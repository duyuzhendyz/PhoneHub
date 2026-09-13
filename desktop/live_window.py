"""
电脑端的"手机屏幕"窗口 —— 投屏过程中实时显示手机画面 + 本地实时试听手机内部声音。

用法：
    python live_window.py [端口]        默认端口 5423
    （推荐用 pythonw.exe 启动，不会带一个黑色控制台窗口）

特性：
  · 默认不置顶（按 T 切换置顶），可以拖动到屏幕任意位置
  · 自适应窗口大小（画面按比例缩放，不变形）
  · 顶部状态条：分辨率 / 实际显示帧率 / 帧大小 / 连接状态
  · 底部「🔊 声音」开关：默认开启，本地声卡直接试听手机内部声音
  · 底部「● 录制 / ■ 停止」：**默认不录制**，点按钮才开始写文件
    （手机只管推流；"仅音频"模式下开录只出 mp3）
  · 反向控制：左键=点击、按住拖动=滑动；右键点两个位置 = 两点滑动（无需按按钮）；
    「两点滑动」按钮 = 左键依次点两个位置；方向键微调注入偏移、Home 归零
  · Esc 关闭；F 切换全屏；T 切换置顶
  · 帧是直接从 PC 端服务的 /stream 拿的原始 JPEG，不解码重编码，延迟很低
"""

import io
import json
import os
import re
import sys
import threading
import time

import tkinter as tk

import numpy as np
import requests
from PIL import Image, ImageTk

try:
    import sounddevice as sd
except Exception:
    sd = None

HERE = os.path.dirname(os.path.abspath(__file__))
CALIB_PATH = os.path.join(HERE, "calib.json")   # 注入偏移校准（相对工程，可移植）

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else int(
    os.environ.get("PHONEHUB_MIRROR_PORT", "5423"))
BASE = "http://127.0.0.1:%d" % PORT


def _secret_token():
    """与主服务/5423 同一份令牌。

    优先用父进程（mirror_server）通过环境变量 PHONEHUB_MIRROR_TOKEN 递过来的值 ——
    两边都从 desktop/settings.json 读时，一旦令牌被改过而某一侧缓存没刷新，
    窗口就会因为 /stream 被 401 拒而一直显示"PC 服务未连接"。
    """
    env_tok = (os.environ.get("PHONEHUB_MIRROR_TOKEN") or "").strip()
    if env_tok:
        return env_tok
    try:
        with open(os.path.join(HERE, "settings.json"), "r", encoding="utf-8") as f:
            return (json.load(f) or {}).get("secret_token") or "541881452418845"
    except Exception:
        return "541881452418845"


AUTH_HDR = {"Authorization": "Bearer " + _secret_token()}

BG = "#0d0d0d"
BG_BAR = "#161616"
FG = "#d0d0d0"
FG_DIM = "#8a8a8a"
FG_OK = "#00e676"
FG_BAD = "#ff5252"

# 采集线程和界面线程共享的状态
LOCK = threading.Lock()
SHARED = {
    "jpeg": None,        # 最新一帧的原始 JPEG 字节
    "seq": 0,            # 帧序号，用来判断有没有新帧
    "connected": False,
    "error": "",
}
RUNNING = True


def set_shared(**kw):
    with LOCK:
        SHARED.update(kw)


def snapshot():
    with LOCK:
        return dict(SHARED)


# ── 后台线程：从 /stream 拉 MJPEG ──────────────────────────
def reader_thread():
    global RUNNING
    session = requests.Session()
    while RUNNING:
        try:
            with session.get(BASE + "/stream", stream=True, timeout=(5, 15),
                             headers=AUTH_HDR) as r:
                if r.status_code != 200:
                    set_shared(connected=False, error="HTTP %d" % r.status_code)
                    time.sleep(1.5)
                    continue
                set_shared(connected=True, error="")
                buf = b""
                for chunk in r.iter_content(65536):
                    if not RUNNING:
                        return
                    buf = parse_mpjpeg(buf + chunk)
                    if len(buf) > 2_000_000:
                        buf = buf[-200_000:]
        except Exception as e:
            set_shared(connected=False, error="%s: %s" % (type(e).__name__, e))
        if RUNNING:
            time.sleep(1.0)


def parse_mpjpeg(buf):
    """从 MJPEG 字节流里切出一帧帧 JPEG（按 Content-Length 切，最稳）"""
    while True:
        head_end = buf.find(b"\r\n\r\n")
        if head_end < 0:
            # 还没凑齐头部；别让缓冲区无限增长
            return buf[-4096:] if len(buf) > 4096 else buf

        head = buf[:head_end]
        m = re.search(rb"Content-Length:\s*(\d+)", head)
        if not m:
            buf = buf[head_end + 4:]
            continue

        n = int(m.group(1))
        body_start = head_end + 4
        if len(buf) < body_start + n:
            return buf          # 这一帧还没收完

        jpg = buf[body_start:body_start + n]
        buf = buf[body_start + n:]
        if jpg[:3] == b"\xff\xd8\xff":
            with LOCK:
                SHARED["jpeg"] = jpg
                SHARED["seq"] += 1


# ── 实时试听引擎：/audio_stream → 环形缓冲 → 本地声卡 ──────────
# 与浏览器版同一套防爆音设计：
#   字节余数跨块携带（4 字节帧对齐）、180ms 预填充、断流淡入/将断淡出、
#   落后 0.8s 强制重对齐。区别是走本地声卡（PortAudio），老电脑负担极小。
ARATE = 48000
ACAP = ARATE * 2                    # 环形缓冲 2s（每声道样本数）
APREFILL = int(ARATE * 0.18)        # 预填充 180ms 后开播
AMAXBUF = int(ARATE * 0.8)          # 缓冲超 0.8s → 丢到 0.5s 重对齐


class AudioMonitor:

    def __init__(self, base_url, report):
        self.base = base_url
        self.report = report            # 状态回调（可能在子线程被调）
        self.running = False
        self.stream = None
        self._resp = None               # 当前打开的流响应（stop 时用来掐断阻塞读）
        self.lock = threading.Lock()
        self.ringL = np.zeros(ACAP, dtype=np.float32)
        self.ringR = np.zeros(ACAP, dtype=np.float32)
        self.wpos = 0
        self.filled = 0
        self.primed = False
        self._in_gap = True
        self._fade = 0
        self._carry = b""
        self.got_bytes = 0
        self.stream_rate = 48000        # 手机端当前音频采样率（音质分档后可能是 96k/192k）

    # ── 对外 ──
    def start(self):
        if self.running or sd is None:
            return
        self.stream_rate = self._query_rate()
        with self.lock:
            self.wpos = 0; self.filled = 0
            self.primed = False; self._in_gap = True; self._fade = 0
            self._carry = b""; self.got_bytes = 0
        self.running = True
        try:
            self.stream = sd.OutputStream(
                samplerate=ARATE, channels=2, dtype="float32",
                blocksize=0, callback=self._callback)
            self.stream.start()
        except Exception as e:
            self.running = False
            self.report("声卡打开失败: %s" % e)
            return
        threading.Thread(target=self._reader, daemon=True).start()
        threading.Thread(target=self._rate_poller, daemon=True).start()
        self.report("连接中…")

    def _query_rate(self):
        """查手机端当前音频采样率（音质分档后可能是 96k/192k）"""
        try:
            st = requests.get(self.base + "/status", timeout=3, headers=AUTH_HDR).json()
            r = int((st.get("audio") or {}).get("rate") or 48000)
            return max(8000, min(192000, r))
        except Exception:
            return 48000

    def _rate_poller(self):
        """每 5s 同步一次手机端采样率（用户中途改音质档位时能跟上）"""
        while self.running:
            for _ in range(50):
                if not self.running:
                    return
                time.sleep(0.1)
            if not self.running:
                return
            try:
                st = requests.get(self.base + "/status", timeout=3, headers=AUTH_HDR).json()
                r = int((st.get("audio") or {}).get("rate") or 48000)
                if 8000 <= r <= 192000:
                    self.stream_rate = r
            except Exception:
                pass

    def stop(self):
        if not self.running:
            return
        self.running = False
        try:
            if self._resp is not None:
                self._resp.close()      # 掐断阻塞中的网络读
        except Exception:
            pass
        self._resp = None
        try:
            if self.stream is not None:
                self.stream.stop(); self.stream.close()
        except Exception:
            pass
        self.stream = None
        with self.lock:
            self.filled = 0; self.primed = False
        self.report("关")

    # ── 网络读线程 ──
    def _reader(self):
        while self.running:
            try:
                with requests.get(self.base + "/audio_stream", stream=True,
                                  timeout=(5, None), headers=AUTH_HDR) as r:
                    if r.status_code != 200:
                        self.report("连接失败 HTTP %d" % r.status_code)
                        time.sleep(1.5)
                        continue
                    self._resp = r
                    carry = b""
                    for chunk in r.iter_content(11520):
                        if not self.running:
                            return
                        if not chunk:
                            continue
                        buf = carry + chunk
                        usable = len(buf) - (len(buf) % 4)
                        carry = buf[usable:]
                        if usable <= 0:
                            continue
                        i16 = np.frombuffer(buf[:usable], dtype="<i2")
                        f = i16.astype(np.float32) / 32768.0
                        self._push(f[0::2].copy(), f[1::2].copy())
                        self.got_bytes += usable
                # 服务端关了流 → 若仍在试听就重连
            except Exception as e:
                if self.running:
                    self.report("音频流中断: %s" % type(e).__name__)
            if self.running:
                self.report("重连中…")
                time.sleep(1.0)

    def _push(self, l, r):
        # 手机端可能是 96k/192k 采样率，而播放设备固定 48k：
        # 整数倍率用均值滤波降采样（2:1 / 4:1），等效低通，无混叠
        sr = self.stream_rate
        if sr != ARATE and sr > 0:
            if sr % ARATE == 0:
                k = sr // ARATE
                if k > 1:
                    n2 = (len(l) // k) * k
                    if n2 == 0:
                        return
                    l = l[:n2].reshape(-1, k).mean(axis=1)
                    r = r[:n2].reshape(-1, k).mean(axis=1)
            else:
                # 非整数倍率（理论上不出现）：线性插值兜底
                n2 = max(1, int(len(l) * ARATE / sr))
                idx = np.linspace(0.0, len(l) - 1.0, n2)
                l = np.interp(idx, np.arange(len(l)), l)
                r = np.interp(idx, np.arange(len(r)), r)
        n = len(l)
        if n == 0:
            return
        with self.lock:
            end = self.wpos + n
            if end <= ACAP:
                self.ringL[self.wpos:end] = l
                self.ringR[self.wpos:end] = r
            else:
                k = ACAP - self.wpos
                self.ringL[self.wpos:] = l[:k]
                self.ringL[:n - k] = l[k:]
                self.ringR[self.wpos:] = r[:k]
                self.ringR[:n - k] = r[k:]
            self.wpos = end % ACAP
            self.filled = min(ACAP, self.filled + n)

    # ── 声卡回调（PortAudio 线程）──
    # 注意：纯输出流的回调签名是 (outdata, frames, time, status)，没有 indata
    def _callback(self, outdata, frames, _time, _status):
        primed_now = False
        with self.lock:
            if not self.primed:
                if self.filled >= APREFILL:
                    self.primed = True
                    self._in_gap = True
                    self._fade = 0
                    primed_now = True        # 锁外上报，避免在锁内做 UI 调度
                else:
                    outdata.fill(0)
                    return
            if self.filled > AMAXBUF:                 # 落后太多，重对齐
                self.filled = int(ARATE * 0.5)
                self._in_gap = True
                self._fade = 0
            avail = min(frames, self.filled)
            if avail > 0:
                start = (self.wpos - self.filled + ACAP) % ACAP
                end = start + avail
                if end <= ACAP:
                    outdata[:avail, 0] = self.ringL[start:end]
                    outdata[:avail, 1] = self.ringR[start:end]
                else:
                    k = ACAP - start
                    outdata[:k, 0] = self.ringL[start:]
                    outdata[k:avail, 0] = self.ringL[:avail - k]
                    outdata[:k, 1] = self.ringR[start:]
                    outdata[k:avail, 1] = self.ringR[:avail - k]
                self.filled -= avail
            if avail < frames:                        # 断流段
                self._in_gap = True
                self._fade = 0
                outdata[avail:] = 0
            if avail > 0:
                if self._in_gap:                      # 恢复：淡入防爆音
                    nfade = min(64, avail)
                    g = np.linspace(0.0, 1.0, nfade, endpoint=False, dtype=np.float32)
                    outdata[:nfade, 0] *= g
                    outdata[:nfade, 1] *= g
                    self._fade += nfade
                    if self._fade >= 64:
                        self._in_gap = False
                elif avail < 64:                      # 将断：淡出防爆音
                    g = np.linspace(1.0, 0.0, avail, dtype=np.float32)
                    outdata[:avail, 0] *= g
                    outdata[:avail, 1] *= g
        if primed_now:
            self.report("试听中")


# ── 界面 ─────────────────────────────────────────────────
class LiveWindow:

    def __init__(self):
        self.root = tk.Tk()
        self.root.title("手机屏幕 · 实时")
        self.root.configure(bg=BG)
        self.root.attributes("-topmost", False)   # 默认不置顶（按 T 可切换置顶）
        self.root.minsize(240, 360)
        self.root.geometry("420x920")
        self._place_right()

        self.root.bind("<Escape>", lambda e: self.close())
        self.root.bind("f", lambda e: self.toggle_fullscreen())
        self.root.bind("t", lambda e: self.toggle_topmost())
        self.root.protocol("WM_DELETE_WINDOW", self.close)

        self.bar = tk.Label(
            self.root, text="正在连接...", bg=BG_BAR, fg=FG_DIM,
            anchor="w", padx=10, pady=4, font=("Consolas", 9))
        self.bar.pack(side="top", fill="x")

        # 底部两行：上行=试听开关 + 录制开关；下行=两点滑动
        self.audio = AudioMonitor(BASE, lambda t: self.root.after(0, self._audio_state, t))
        bottom = tk.Frame(self.root, bg=BG)
        bottom.pack(side="bottom", fill="x")
        row1 = tk.Frame(bottom, bg=BG)
        row1.pack(side="top", fill="x")
        row2 = tk.Frame(bottom, bg=BG)
        row2.pack(side="top", fill="x")
        self.audio_btn = tk.Button(
            row1,
            text="🔊 声音:关" if sd is not None else "声音不可用(缺 sounddevice)",
            command=self.toggle_audio, bg="#2196f3", fg="white",
            activebackground="#1976d2", activeforeground="white",
            relief="flat", font=("Microsoft YaHei UI", 10), pady=4)
        self.audio_btn.pack(side="left", fill="x", expand=True)
        self.rec_btn = tk.Button(
            row1, text="● 开始录制", command=self.toggle_record,
            bg="#2e7d32", fg="white", activebackground="#1b5e20",
            activeforeground="white", relief="flat",
            font=("Microsoft YaHei UI", 10), pady=4, width=10)
        self.rec_btn.pack(side="right", fill="y")
        self.swipe_btn = tk.Button(
            row2, text="⟺ 两点滑动（按钮 或 右键点两个位置）", command=self.toggle_swipe_mode,
            bg="#607d8b", fg="white", activebackground="#455a64",
            activeforeground="white", relief="flat",
            font=("Microsoft YaHei UI", 9), pady=3)
        self.swipe_btn.pack(side="top", fill="x")
        self._shown_rec_state = None
        self.rclick_p1 = None      # 右键两点滑动的起点
        self._toast = ("", 0.0)    # (文本, 到期时间)：临时提示，避免被状态栏刷新冲掉

        # 注入偏移校准：绿圈落点不对时用方向键微调（手机像素），Home 归零
        self.off_x = 0
        self.off_y = 0
        try:
            with open(CALIB_PATH, encoding="utf-8") as f:
                c = json.load(f)
            self.off_x = int(c.get("off_x", 0))
            self.off_y = int(c.get("off_y", 0))
        except Exception:
            pass
        self.swipe_arm = 0     # 0=未启用 1=等起点 2=等终点
        self.swipe_p1 = None
        self.root.bind("<Left>", lambda e: self._nudge(-15, 0))
        self.root.bind("<Right>", lambda e: self._nudge(15, 0))
        self.root.bind("<Up>", lambda e: self._nudge(0, -15))
        self.root.bind("<Down>", lambda e: self._nudge(0, 15))
        self.root.bind("<Home>", lambda e: self._nudge(0, 0, reset=True))

        self.screen = tk.Label(
            self.root,
            text="等待手机画面\n\n请在手机上点「② 开始投屏录制」",
            bg=BG, fg=FG_DIM, font=("Microsoft YaHei UI", 11), justify="center")
        self.screen.pack(side="top", fill="both", expand=True)
        self.screen.bind("<ButtonPress-1>", self._on_press)
        self.screen.bind("<ButtonRelease-1>", self._on_release)
        self.screen.bind("<ButtonPress-3>", self._on_right_press)   # 右键两点滑动

        self.last_seq = -1
        self.photo = None
        self.shown = 0
        self.fps_win_start = time.time()
        self.fps = 0.0
        self.frame_size = (0, 0)
        self.frame_kb = 0.0
        self.first_frame = True
        self.blank_since = time.time()
        self.ctrl_ready = False     # 手机端反向控制就绪（无障碍+悬浮窗）
        self.rec_state = False
        self._last_status_poll = 0.0
        self._press = None          # (x, y, t) 鼠标按下状态，用于判定点击 vs 拖动

    def _poll_status(self):
        try:
            d = requests.get(BASE + "/status", timeout=3, headers=AUTH_HDR).json()
            self.ctrl_ready = bool((d.get("live") or {}).get("ctrl"))
            self.rec_state = bool(d.get("recording"))
        except Exception:
            pass

    def _norm(self, x, y):
        """窗口坐标 → 镜像画面的归一化坐标 (0..1)；不在画面上返回 None"""
        if self.photo is None:
            return None
        lw = max(self.screen.winfo_width(), 1)
        lh = max(self.screen.winfo_height(), 1)
        iw, ih = self.photo.width(), self.photo.height()
        if iw <= 0 or ih <= 0:
            return None
        fx = (x - (lw - iw) / 2.0) / iw
        fy = (y - (lh - ih) / 2.0) / ih
        if fx < 0 or fx > 1 or fy < 0 or fy > 1:
            return None
        return fx, fy

    def _on_press(self, ev):
        self._press = (ev.x, ev.y, time.time())

    def _on_release(self, ev):
        p = self._press
        self._press = None
        end = self._norm(ev.x, ev.y)
        if p is None or end is None:
            return
        if self.swipe_arm > 0:
            self._two_point_click(end)
            return
        dist = ((ev.x - p[0]) ** 2 + (ev.y - p[1]) ** 2) ** 0.5
        if dist < 12:
            # 位移很小 → 当作点击（异步发送，别冻住界面）
            self._post_async("/remote_tap",
                             {"fx": round(end[0], 4), "fy": round(end[1], 4),
                              "offx": self.off_x, "offy": self.off_y})
        else:
            # 拖动 → 滑动手势。时长截到 200~800ms 的自然滑动范围：
            # 之前直接映射实际拖动时长，慢慢拖 2 秒就变成 2 秒的"慢爬"
            # 手势，系统当成按住不放而不是滑动
            start = self._norm(p[0], p[1])
            if start is None:
                return
            ms = max(200, min(800, int((time.time() - p[2]) * 1000)))
            self._post_async("/remote_swipe",
                             {"fx1": round(start[0], 4), "fy1": round(start[1], 4),
                              "fx2": round(end[0], 4), "fy2": round(end[1], 4),
                              "ms": ms, "offx": self.off_x, "offy": self.off_y})

    # ── 两点滑动模式：点按钮激活，依次点两个位置，自动从第1点滑到第2点 ──
    #    也可以不按按钮：右键点第一个位置、再右键点第二个位置，直接完成滑动
    SWIPE_IDLE = "⟺ 两点滑动（按钮 或 右键点两个位置）"

    def toggle_swipe_mode(self):
        if self.swipe_arm == 0:
            self.swipe_arm = 1
            self.swipe_btn.configure(text="⟺ 点起点（左键）", bg="#ff9800")
            self._show_toast("两点滑动：请点第一个位置（起点）")
        else:
            self.swipe_arm = 0
            self.rclick_p1 = None
            self.swipe_btn.configure(text=self.SWIPE_IDLE, bg="#607d8b")

    def _two_point_click(self, pt):
        if self.swipe_arm == 1:
            self.swipe_p1 = pt
            self.swipe_arm = 2
            self.swipe_btn.configure(text="⟺ 点终点（左键）", bg="#ff9800")
            self._show_toast("两点滑动：起点已记下，请点终点")
        else:
            self._post_async("/remote_swipe",
                             {"fx1": round(self.swipe_p1[0], 4), "fy1": round(self.swipe_p1[1], 4),
                              "fx2": round(pt[0], 4), "fy2": round(pt[1], 4),
                              "ms": 500, "offx": self.off_x, "offy": self.off_y})
            self.swipe_arm = 0
            self.swipe_btn.configure(text=self.SWIPE_IDLE, bg="#607d8b")
            self._show_toast("两点滑动已发送")

    def _on_right_press(self, ev):
        """右键：第一次记起点，第二次直接滑动（等于按钮+两次左键，但不用按按钮）"""
        pt = self._norm(ev.x, ev.y)
        if pt is None:
            return
        if self.rclick_p1 is None:
            self.rclick_p1 = pt
            self._show_toast("右键起点已记下 —— 再右键点第二个位置即完成滑动")
        else:
            p1 = self.rclick_p1
            self.rclick_p1 = None
            self._post_async("/remote_swipe",
                             {"fx1": round(p1[0], 4), "fy1": round(p1[1], 4),
                              "fx2": round(pt[0], 4), "fy2": round(pt[1], 4),
                              "ms": 500, "offx": self.off_x, "offy": self.off_y})
            self._show_toast("右键两点滑动已发送")

    # ── 录制开关（控制 PC 端开始/停止录制，与手机推流解耦）──
    def toggle_record(self):
        if self.rec_state:
            self._post_async("/record/stop", {})
            self._show_toast("已请求停止录制（正在收尾合并...）")
        else:
            self._post_async("/record/start", {})
            self._show_toast("已请求开始录制")

    def _show_toast(self, msg, secs=3.0):
        """状态栏临时提示：update_bar 每 20ms 重写状态栏，提示要单独存一份带过期时间"""
        self._toast = (msg, time.time() + secs)

    # ── 注入偏移校准 ──
    def _nudge(self, dx, dy, reset=False):
        if reset:
            self.off_x = 0
            self.off_y = 0
        else:
            self.off_x += dx
            self.off_y += dy
        self._save_calib()
        self._show_toast(f"注入偏移: ({self.off_x:+d}, {self.off_y:+d}) 手机像素（Home 归零）", 2.5)

    def _save_calib(self):
        try:
            with open(CALIB_PATH, "w", encoding="utf-8") as f:
                json.dump({"off_x": self.off_x, "off_y": self.off_y}, f)
        except Exception:
            pass

    def _post_async(self, path, params):
        def _send():
            try:
                requests.post(BASE + path, params=params, timeout=2, headers=AUTH_HDR)
            except Exception:
                pass
        threading.Thread(target=_send, daemon=True).start()

    def _place_right(self):
        """默认贴在屏幕右侧，像个立在旁边的手机"""
        self.root.update_idletasks()
        sw = self.root.winfo_screenwidth()
        sh = self.root.winfo_screenheight()
        w, h = 420, min(920, int(sh * 0.88))
        self.root.geometry("%dx%d+%d+%d" % (w, h, max(sw - w - 40, 0), max((sh - h) // 2, 0)))

    def toggle_topmost(self):
        cur = bool(self.root.attributes("-topmost"))
        self.root.attributes("-topmost", not cur)

    def toggle_fullscreen(self):
        self.root.attributes("-fullscreen", not bool(self.root.attributes("-fullscreen")))

    def toggle_audio(self):
        if sd is None:
            self._audio_state("不可用")
            return
        if self.audio.running:
            self.audio.stop()
        else:
            self.audio.start()

    def _audio_state(self, txt):
        try:
            self.audio_btn.configure(text="🔊 声音:" + txt)
        except Exception:
            pass

    def close(self):
        global RUNNING
        RUNNING = False
        try:
            self.audio.stop()
        except Exception:
            pass
        try:
            self.root.destroy()
        except Exception:
            pass

    def tick(self):
        if not RUNNING:
            return
        snap = snapshot()

        if snap["jpeg"] is not None and snap["seq"] != self.last_seq:
            self.last_seq = snap["seq"]
            self.render(snap["jpeg"])

        if time.time() - self._last_status_poll > 1.0:
            self._last_status_poll = time.time()
            threading.Thread(target=self._poll_status, daemon=True).start()

        # 录制按钮状态跟随服务端（在主线程改控件）
        if self.rec_state != self._shown_rec_state:
            self._shown_rec_state = self.rec_state
            if self.rec_state:
                self.rec_btn.configure(text="■ 停止录制", bg="#d32f2f")
            else:
                self.rec_btn.configure(text="● 开始录制", bg="#2e7d32")

        self.update_bar(snap)
        self.root.after(20, self.tick)

    def render(self, jpeg):
        try:
            img = Image.open(io.BytesIO(jpeg))
        except Exception:
            return

        if self.first_frame or (img.width, img.height) != self.frame_size:
            first = self.first_frame
            self.first_frame = False
            self._fit_window_to(img.width, img.height)
            # 打一条到 stdout，方便自动化验证（pythonw 下无处输出，无影响）
            if first:
                print("[窗口] 已显示第一帧 %dx%d" % (img.width, img.height), flush=True)
            else:
                print("[窗口] 画面尺寸变化 %dx%d → %dx%d（手机横竖屏切换）" % (
                    self.frame_size[0], self.frame_size[1], img.width, img.height), flush=True)

        avail_w = max(self.screen.winfo_width(), 1)
        avail_h = max(self.screen.winfo_height(), 1)
        ratio = min(avail_w / float(img.width), avail_h / float(img.height))
        tw = max(int(img.width * ratio), 1)
        th = max(int(img.height * ratio), 1)

        # 尺寸刚好一致就跳过缩放（解码本身也要几毫秒，能省一步是一步）
        if (tw, th) == img.size:
            self.photo = ImageTk.PhotoImage(img)
        else:
            # LANCZOS：高分辨率源缩到窗口大小时细节保留最好，
            # BILINEAR 大比例缩放是预览发糊的主要原因之一
            self.photo = ImageTk.PhotoImage(img.resize((tw, th), Image.LANCZOS))
        self.screen.configure(image=self.photo, text="")
        self.screen.image = self.photo       # 必须留引用，否则会被回收

        self.frame_size = img.size
        self.frame_kb = len(jpeg) / 1024.0
        self.shown += 1
        self.blank_since = time.time()

        span = time.time() - self.fps_win_start
        if span >= 1.0:
            self.fps = self.shown / span
            self.shown = 0
            self.fps_win_start = time.time()

    def _fit_window_to(self, fw, fh):
        """按画面比例调整窗口：竖屏→窄高窗，横屏→宽窗（并贴合屏幕范围）"""
        sh = self.root.winfo_screenheight()
        sw = self.root.winfo_screenwidth()
        if fw <= 0 or fh <= 0:
            return
        max_h = min(int(sh * 0.88), 1000)
        max_w = int(sw * 0.6)
        target_h = max_h
        target_w = int(target_h * fw / float(fh)) + 2
        if target_w > max_w:                 # 横屏：受屏幕宽度限制，按宽度反算高度
            target_w = max_w
            target_h = max(int(target_w * fh / float(fw)), 200)
        target_w = max(target_w, 240)
        x = max(sw - target_w - 40, 0)
        y = max((sh - target_h - 30) // 2, 0)
        self.root.geometry("%dx%d+%d+%d" % (target_w, target_h + 24, x, y))

    def update_bar(self, snap):
        parts = []
        if self.frame_size[0]:
            parts.append("%dx%d" % self.frame_size)
            parts.append("%.1f fps" % self.fps)
            parts.append("%.1f KB" % self.frame_kb)

        age = time.time() - self.blank_since if self.frame_size[0] else None
        if not snap["connected"]:
            # 带上具体原因：以前只写"PC 服务未连接"，看不出是 401 还是连接被拒
            err = (snap.get("error") or "").strip()
            status = f"PC 服务未连接（{err}）" if err else "PC 服务未连接"
            color = FG_BAD
        elif self.frame_size[0] == 0:
            status, color = "等待手机画面...", FG_DIM
        elif age is not None and age > 2.0:
            status, color = "画面静止", FG_DIM
        else:
            status, color = "接收中", FG_OK

        text = "  ".join(parts + [status])
        if self.rec_state and not self.ctrl_ready and self.frame_size[0]:
            text += "  ｜反向控制未就绪(手机开无障碍)"
        if time.time() < self._toast[1]:          # 临时提示优先显示
            text = self._toast[0]
        self.bar.configure(text=text, fg=color)
        self.root.title("手机屏幕 · 实时" + ("  " + " ".join(parts) if parts else ""))

    def run(self):
        t = threading.Thread(target=reader_thread, daemon=True)
        t.start()
        # 默认开启实时试听（连上就听；手机还没推流时安静待着，不报错）
        try:
            self.audio.start()
        except Exception:
            pass
        self.root.after(20, self.tick)
        self.root.mainloop()


if __name__ == "__main__":
    LiveWindow().run()
