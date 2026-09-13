#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PhoneHub · 电脑声音 → 手机浏览器 实时收听（测试模块）
==================================================
思路：彻底绕开安卓端 AudioTrack/轮询那套（之前一直卡顿）。
      PC 端直接抓「系统正在播放的声音」(WASAPI loopback)，
      通过 WebSocket 把原始 PCM 推给手机浏览器，浏览器用 Web Audio 播放。

  - 网页  : http://<本机局域网IP>:4598/   → 手机浏览器打开这个网址，点「开始」
  - 音频WS: ws://<本机局域网IP>:4599/audio → 网页内部自动连接（无需手动）

运行（在装了 PhoneHub 桌面端的那台 Windows 上）：
    F:/Program Files/python38/python.exe server.py

然后手机连同一 WiFi，浏览器打开上面的网址，点「开始」即可收听。
防火墙需放行 4598(网页) 与 4599(音频WS)（与 58627/5435 同网段）。
"""
import os
import sys
import json
import time
import socket
import queue
import base64
import asyncio
import threading
import websockets
import http.server

HTTP_PORT = 4598
WS_PORT = 4599
FRAME_MS = 20          # 每片时长，约 20ms 一片

HERE = os.path.dirname(os.path.abspath(__file__))


# ----------------------------------------------------------------------------
# 1) 采集 PC 系统声（WASAPI loopback，pyaudiowpatch，写法与桌面端一致）
# ----------------------------------------------------------------------------
class Capture:
    def __init__(self):
        self.p = None
        self.stream = None
        self.rate = 48000
        self.channels = 2
        self.frames_per_buffer = int(self.rate * FRAME_MS / 1000)
        self._open()

    def _open(self):
        try:
            import pyaudiowpatch as pyaudio
            self.p = pyaudio.PyAudio()
            try:
                dev = self.p.get_default_wasapi_loopback()
            except Exception:
                dev = self._scan_loopback(self.p)
            if dev is None:
                raise RuntimeError("找不到 WASAPI loopback 设备")

            want_rate, want_ch = 48000, 2
            try:
                self.stream = self.p.open(
                    format=pyaudio.paInt16, channels=want_ch, rate=want_rate,
                    input=True, input_device_index=dev["index"],
                    frames_per_buffer=self.frames_per_buffer)
                self.rate, self.channels = want_rate, want_ch
            except Exception:
                self.rate = int(dev["defaultSampleRate"])
                self.channels = int(dev["maxInputChannels"]) or 2
                self.frames_per_buffer = int(self.rate * FRAME_MS / 1000)
                self.stream = self.p.open(
                    format=pyaudio.paInt16, channels=self.channels, rate=self.rate,
                    input=True, input_device_index=dev["index"],
                    frames_per_buffer=self.frames_per_buffer)
            print(f"[capture] loopback: {self.rate}Hz / {self.channels}ch  ({dev.get('name')})")
        except Exception as e:
            print(f"[capture] 采集不可用: {e}（网页仍可用，但不会出声）")
            self.stream = None

    @staticmethod
    def _scan_loopback(p):
        try:
            wasapi = p.get_host_api_info_by_type(pyaudio.paWASAPI)
            for i in range(wasapi["deviceCount"]):
                d = p.get_device_info_by_index(i)
                if int(d.get("maxInputChannels", 0)) > 0 and \
                   "loopback" in str(d.get("name", "")).lower():
                    return d
        except Exception:
            pass
        return None

    def read(self):
        """返回一片原始 PCM(int16) 字节，无数据返回 b''。"""
        if self.stream is None:
            return b""
        try:
            data = self.stream.read(self.frames_per_buffer,
                                    exception_on_overflow=False)
            return data if isinstance(data, (bytes, bytearray)) else b""
        except Exception as e:
            print(f"[capture] read 异常: {e}")
            return b""

    def close(self):
        try:
            if self.stream:
                self.stream.stop_stream()
                self.stream.close()
        except Exception:
            pass
        try:
            if self.p:
                self.p.terminate()
        except Exception:
            pass


# ----------------------------------------------------------------------------
# 2) 广播：采集线程 → 线程安全队列 → asyncio 分发给各 WS 订阅者
# ----------------------------------------------------------------------------
audio_q = queue.Queue(maxsize=40)     # 采集线程写入（封顶≈0.8s，避免延迟累积）
subscribers = set()                    # 每个 WS 客户端一个 asyncio.Queue
running = True
capture = None


def capture_thread():
    while running:
        data = capture.read()
        if not data:
            time.sleep(0.01)
            continue
        try:
            audio_q.put_nowait(data)
        except queue.Full:
            pass


async def pump():
    loop = asyncio.get_event_loop()
    while running:
        data = await loop.run_in_executor(None, audio_q.get)
        for q in list(subscribers):
            try:
                q.put_nowait(data)
            except asyncio.QueueFull:
                pass


# ----------------------------------------------------------------------------
# 2.5) 当前播放媒体信息（winsdk，写法与桌面端 connection_manager 一致）
# ----------------------------------------------------------------------------
latest_media = {}
media_dirty = False
_media_lock = threading.Lock()


def _img_mime(b):
    if b[:3] == b"\xff\xd8\xff":
        return "image/jpeg"
    if b[:8] == b"\x89PNG\r\n\x1a\n":
        return "image/png"
    if b[:4] == b"RIFF" and b[8:12] == b"WEBP":
        return "image/webp"
    return "image/jpeg"


def get_media_info():
    try:
        import asyncio
        import winsdk.windows.media.control as wmc
        from winsdk.windows.storage.streams import DataReader

        async def _get():
            sessions = await wmc.GlobalSystemMediaTransportControlsSessionManager.request_async()
            session = sessions.get_current_session()
            if not session:
                return None
            props = await session.try_get_media_properties_async()
            thumb_url = ""
            if props.thumbnail:
                try:
                    stream = await props.thumbnail.open_read_async()
                    if stream.size > 0:
                        reader = DataReader(stream)
                        await reader.load_async(stream.size)
                        # 用 unconsumed_buffer_length 取实际可读字节，避免 size 误报导致读空
                        data = bytearray(reader.unconsumed_buffer_length)
                        reader.read_bytes(data)
                        reader.detach_stream()
                        reader.close()
                        stream.close()
                        data = bytes(data)
                        mime = _img_mime(data)
                        thumb_url = "data:%s;base64,%s" % (
                            mime, base64.b64encode(data).decode("ascii"))
                except Exception:
                    pass
            status = ("playing"
                      if session.get_playback_info().playback_status
                      == wmc.GlobalSystemMediaTransportControlsSessionPlaybackStatus.PLAYING
                      else "paused")
            return {
                "title": props.title or "",
                "artist": props.artist or "",
                "album": props.album_title or "",
                "thumbnail": thumb_url,
                "status": status,
            }
        return asyncio.run(_get())
    except Exception as e:
        print(f"[media] 获取失败: {e}")
        return None


def media_thread():
    last = None
    import concurrent.futures as _cf
    while running:
        info = None
        try:
            with _cf.ThreadPoolExecutor(max_workers=1) as _ex:
                info = _ex.submit(get_media_info).result(timeout=3)
        except Exception:
            info = None
        if info is None:
            info = {"title": "未检测到媒体播放", "artist": "", "album": "",
                    "thumbnail": "", "status": "stopped"}
        with _media_lock:
            if info != last:
                latest_media.clear()
                latest_media.update(info)
                global media_dirty
                media_dirty = True
                last = info
        time.sleep(2)


async def media_pump():
    global media_dirty
    last_sent = 0.0
    while running:
        now = time.time()
        with _media_lock:
            dirty = media_dirty
            media_dirty = False
            snap = dict(latest_media) if latest_media else None
        # 内容变化即时推；否则每 2s 兜底重推，保证任何时刻连上的客户端都能拿到当前状态
        if snap and (dirty or now - last_sent >= 2):
            try:
                payload = json.dumps({"type": "media_info", **snap}, ensure_ascii=False)
            except Exception:
                payload = None
            if payload:
                for q in list(subscribers):
                    try:
                        q.put_nowait(payload)
                    except asyncio.QueueFull:
                        pass
                last_sent = now
        await asyncio.sleep(0.5)


# ----------------------------------------------------------------------------
# 3) WebSocket 推流（端口 4599）
# ----------------------------------------------------------------------------
async def ws_handler(websocket, path=None):
    cfg = json.dumps({
        "type": "config",
        "rate": capture.rate,
        "channels": capture.channels,
        "fmt": "int16",
    })
    await websocket.send(cfg)
    q = asyncio.Queue(maxsize=60)
    subscribers.add(q)
    # 新客户端连上立即补发当前已知媒体信息（避免错过首推 / 曲目未变时看不到）
    with _media_lock:
        if latest_media:
            try:
                await websocket.send(
                    json.dumps({"type": "media_info", **latest_media}, ensure_ascii=False))
            except Exception:
                pass
    print(f"[ws] 客户端接入（当前 {len(subscribers)}）")
    try:
        while True:
            data = await q.get()
            await websocket.send(data)        # 二进制 PCM
    except websockets.ConnectionClosed:
        pass
    finally:
        subscribers.discard(q)
        print(f"[ws] 客户端断开（剩余 {len(subscribers)}）")


# ----------------------------------------------------------------------------
# 4) HTTP 提供网页（端口 4598）
# ----------------------------------------------------------------------------
class HttpHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path in ("/", "/index.html"):
            try:
                with open(os.path.join(HERE, "index.html"), "rb") as f:
                    body = f.read()
            except Exception:
                self.send_error(404, "index.html not found")
                return
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_error(404)

    def log_message(self, *args):
        pass


# ----------------------------------------------------------------------------
# 5) 启动
# ----------------------------------------------------------------------------
def lan_ips():
    ips = []
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ips.append(s.getsockname()[0])
        s.close()
    except Exception:
        pass
    return ips


async def main():
    global capture
    capture = Capture()
    threading.Thread(target=capture_thread, daemon=True).start()
    asyncio.ensure_future(pump())
    threading.Thread(target=media_thread, daemon=True).start()
    asyncio.ensure_future(media_pump())

    # 网页服务（线程）
    httpd = http.server.ThreadingHTTPServer(("0.0.0.0", HTTP_PORT), HttpHandler)
    threading.Thread(target=httpd.serve_forever, daemon=True).start()

    print("=" * 50)
    print("电脑声音 → 手机浏览器 测试服务已启动")
    for ip in lan_ips():
        print(f"  手机浏览器打开: http://{ip}:{HTTP_PORT}/")
    print(f"  本机调试      : http://127.0.0.1:{HTTP_PORT}/")
    print(f"  (音频 WS 在端口 {WS_PORT})")
    print("=" * 50)

    async with websockets.serve(ws_handler, "0.0.0.0", WS_PORT):
        await asyncio.Future()      # 永久运行


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        running = False
        if capture:
            capture.close()
        print("\n[server] 已停止")
