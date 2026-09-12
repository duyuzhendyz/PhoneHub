"""
PC 端屏幕接收 + 录屏服务

本文件是从 `cs_apps/Screen_mirroring/pc/server.py` **原样搬进桌面端**的：
端口（5423，含 PHONEHUB_MIRROR_PORT 覆盖）、全部路由、录制逻辑、"自动弹手机屏幕窗口"
的行为都保持参考工程不变。仅在文件末尾追加了 start_server_thread() / stop_server() /
is_server_running() / open_live_window() 四个接口，供桌面 app 在自己进程里以线程方式启停；
直接用 `python mirror_server.py` 跑时行为与原来完全一致。

端口：5423（可用环境变量 PHONEHUB_MIRROR_PORT 覆盖，便于本机测第二个实例）
功能：接收手机 JPEG 帧，录制成固定帧率 mp4，直到手机调用 /stop 或长时间无帧

核心设计：录制端"节拍器"
  手机在画面静止时不会产帧（MediaProjection 只在画面变化时输出）。
  如果按"来一帧写一帧"的方式写 mp4，时间轴上就会出现空洞，播放时闪烁。
  所以这里开一个节拍器线程，按固定间隔（1/OUTPUT_FPS）把"当前最新一帧"
  写进视频，没有新帧就重复上一帧 —— 时间轴永远被填满，播放连续不闪。
  输出帧率固定 60fps（OUTPUT_FPS），与手机实际产帧速度解耦。

  实测输入帧率只作诊断用，显示在 /status 的 measured_fps 里。

录制结束条件：手机 POST /stop（点"停止投屏"）才收尾。
不限制时长 —— 长时间无帧（画面静止）或录很久都不会自动结束，
音频边收边写临时文件，不会把内存撑爆。

历史修复：
  1. 死锁：upload_frame 持锁后再调 _start_recording 会二次加锁，
     threading.Lock 不可重入 → 第一帧就卡死。改用 RLock + _xxx_locked 拆分。
  2. 文件名丢失：原来在 _video_writer 置 None 之后才读它的 fileName。
     改为单独保存 _out_path，release 之前取名字。
  3. 缺 /output/<file> 路由，首页下载链接 404。
  4. 空闲阈值曾是 5s，会在画面静止时把录制掐断 —— 画面静止本来就不产帧，
     已提到 60s。
"""

import os
import subprocess
import sys
import time
import threading
import uuid
from collections import deque
from datetime import datetime
from io import BytesIO
import queue

from flask import Flask, request, jsonify, send_from_directory, Response

import cv2
import numpy as np
from PIL import Image

# ── 配置 ──────────────────────────────────────────────
PORT = int(os.environ.get("PHONEHUB_MIRROR_PORT", "5423"))

# 输出帧率：固定 60，与手机产帧速度解耦，静止时用重复帧铺满
OUTPUT_FPS = 60

# 画面静止时手机不产帧是正常现象，绝不能因此把录制停掉。
# 用户要求「录屏/录音都不限制时长」—— 这两个值都设为 0 表示关闭自动结束，
# 录制只在手机主动调用 /stop（点"停止投屏"）时才收尾。
# （若以后想恢复兜底，把下面改成正数即可，watchdog 仅在 >0 时生效。）
IDLE_STOP_SEC = 0.0
MAX_RECORD_SEC = 0.0

# 节拍器单次最多补写多少帧（600 帧 = 10 秒），只在极端落后时生效
MAX_CATCHUP_BURST = 600

# 待写帧队列上限。手机是成串发帧的，如果只保留"最新一帧"，
# 一串里的中间帧会被整串丢掉，时间轴上的运动会变得一蹦一蹦。
# 这里按到达时间戳排队，逐个按真实时刻铺到时间轴上。
FRAME_QUEUE_MAX = 20

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "output")
os.makedirs(OUTPUT_DIR, exist_ok=True)

# ── Flask ─────────────────────────────────────────────
app = Flask(__name__)

# ── 录制状态 ──────────────────────────────────────────
# 必须可重入：upload_frame 持锁后还要调 _start_recording_locked
_lock = threading.RLock()

_recording = False
_recording_id = None
_video_writer = None
_out_path = None
_out_size = None           # (w, h)，后续帧尺寸不一致时统一缩放
_rec_start = 0.0           # 录制开始（monotonic）
_last_frame_ts = 0.0       # 最后一帧到达时间（monotonic）

_arrivals = deque(maxlen=120)    # 帧到达时间戳，仅用于诊断实测帧率
_frame_queue = deque()           # (时间轴时刻, bgr 帧)，按顺序等待铺进时间轴
_current_frame = None            # 时间轴上"当前这一时刻"应该显示的帧
_queue_dropped = 0               # 因队列满而丢掉的帧数（诊断用）
_popped_count = 0                # 已从队列取出并铺进时间轴的帧数（诊断用）
_ts_origin_phone = None          # 手机端 X-Capture-Ts 的基准（第一帧）
_ts_origin_pc = 0.0              # 上面那个基准对应的本机时刻
_last_enqueued_ts = -1.0         # 已入队帧的最大时间轴时刻，保证单调递增

_frame_count = 0           # 收到的帧数
_written_count = 0         # 实际写入视频的帧数
_measured_fps = 0.0        # 实测输入帧率（诊断用）
_active_client = None

_recorded_files = []
_size_change_count = 0     # 连续收到与当前 mp4 不同尺寸的帧数（手机端切画质档→分段重录）

# ── 反向控制：PC→手机命令队列（搭 /upload、/audio 响应的顺风车下发，延迟≤一帧）──
_ctrl_lock = threading.Lock()
_remote_cmds = []          # 待下发命令
_remote_cmd_id = 0
_ctrl_ready = False        # 手机端无障碍+悬浮窗就绪（来自上传请求头 X-Ctrl）


def _drain_remote_cmds():
    global _remote_cmds
    with _ctrl_lock:
        cmds = _remote_cmds
        _remote_cmds = []
    return cmds

# ── 实时画面（给电脑上"看手机屏幕"用的）──────────────────
# 直接把收到的原始 JPEG 字节存下来推给浏览器：零解码、零重编码，
# 显示实时画面几乎不增加任何开销。
LIVE_FPS = 60             # /stream 检查新帧的频率上限（只是循环频率，没新帧不会真推）
MAX_LIVE_CLIENTS = 4      # 同时在看的浏览器上限，防止把 CPU 拖垮
LIVE_KEEPALIVE_SEC = 3.0  # 超过这么久没有新帧就重发一次上一帧，防止流被中间层掐断

# 投屏开始时自动弹出"手机屏幕"窗口（要关掉就设 PHONEHUB_LIVE_WINDOW=0）
LIVE_AUTO_OPEN = os.environ.get("PHONEHUB_LIVE_WINDOW", "1") != "0"
_live_window_proc = None


def _open_live_window(force=False):
    """弹出独立的"手机屏幕"窗口。

    自动路径（投屏开始时）每个服务进程只弹一次；force=True 是用户在桌面 app 里
    显式点「打开手机屏幕」时用的，忽略 PHONEHUB_LIVE_WINDOW 开关。
    """
    global _live_window_proc
    if _live_window_proc is not None and _live_window_proc.poll() is None:
        return                      # 窗口已经开着，不要重复弹
    if not LIVE_AUTO_OPEN and not force:
        return

    script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "live_window.py")
    if not os.path.exists(script):
        return

    exe = sys.executable
    # 优先用 pythonw：不闪一个黑色控制台窗口
    if exe.lower().endswith("python.exe"):
        pw = exe[:-len("python.exe")] + "pythonw.exe"
        if os.path.exists(pw):
            exe = pw

    try:
        flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        _live_window_proc = subprocess.Popen([exe, script, str(PORT)], creationflags=flags)
        print('[实时] 已自动打开"手机屏幕"窗口')
    except Exception as e:
        print(f"[实时] 自动打开窗口失败: {e}")

_live_lock = threading.Lock()
_live_jpeg = None         # 最新一帧的原始 JPEG 字节
_live_w = 0
_live_h = 0
_live_bytes = 0
_live_ts = 0.0
_live_clients = 0
_live_recv_count = 0
_live_count_snapshot = 0
_live_last_count_ts = 0.0
_live_fps = 0.0


def _update_live(jpeg_bytes, size):
    """记下最新一帧，供 /stream 和 /frame.jpg 使用（零解码）"""
    global _live_jpeg, _live_w, _live_h, _live_bytes, _live_ts
    global _live_recv_count, _live_count_snapshot, _live_last_count_ts, _live_fps

    now = time.monotonic()
    with _live_lock:
        _live_jpeg = jpeg_bytes
        _live_w, _live_h = size
        _live_bytes = len(jpeg_bytes)
        _live_ts = now
        _live_recv_count += 1
        dt = now - _live_last_count_ts
        if dt >= 1.0:
            _live_fps = round((_live_recv_count - _live_count_snapshot) / dt, 1)
            _live_count_snapshot = _live_recv_count
            _live_last_count_ts = now


# ── 内部：均需调用方持锁 ────────────────────────────────
def _start_recording_locked(client_ip: str):
    global _recording, _recording_id, _video_writer, _out_path, _out_size
    global _rec_start, _last_frame_ts
    global _frame_count, _written_count, _measured_fps, _active_client
    global _current_frame, _queue_dropped, _popped_count

    if _video_writer is not None:
        _finish_recording_locked()

    _recording = True
    _recording_id = str(uuid.uuid4())[:8]
    _frame_count = 0
    _written_count = 0
    _measured_fps = 0.0
    _active_client = client_ip
    _current_frame = None
    _queue_dropped = 0
    _popped_count = 0
    _ts_origin_phone = None
    _ts_origin_pc = 0.0
    _last_enqueued_ts = -1.0

    _video_writer = None
    _out_path = None
    _out_size = None
    _arrivals.clear()
    _frame_queue.clear()

    _rec_start = time.monotonic()
    _last_frame_ts = _rec_start

    print(f"[录制] 开始 #{_recording_id}  ← 客户端 {client_ip}（等 /stop 结束）")

    # 投屏一开始就把"手机屏幕"窗口弹出来（每个服务进程只弹一次）
    _open_live_window()


def _create_writer_locked(frame):
    """第一帧到达时按固定 OUTPUT_FPS 建 VideoWriter（调用方持锁）"""
    global _video_writer, _out_path, _out_size

    if _video_writer is not None:
        return

    h, w = frame.shape[:2]
    _out_size = (w, h)

    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    out_path = os.path.join(OUTPUT_DIR, f"mirror_{ts}.mp4")
    writer = cv2.VideoWriter(
        out_path,
        cv2.VideoWriter_fourcc(*"mp4v"),
        OUTPUT_FPS,
        (w, h)
    )
    if not writer.isOpened():
        print("[录制] VideoWriter 打开失败")
        return

    _video_writer = writer
    _out_path = out_path
    print(f"[录制] #{_recording_id} → {out_path}  {w}x{h}@{OUTPUT_FPS}fps（定帧率输出）")


def _finish_recording_locked():
    """收尾并落盘（调用方持锁）。
    【顺序极重要】必须先毫秒级释放视频，再做耗时的音频收尾（wav+RMS+ffmpeg 转 mp3
    要 1~3 秒）。若先做音频收尾，节拍器会被 _lock 挡住写不出槽位，
    视频比真实墙钟时长短 → 播放快放 → 音画渐进不同步（实测一次丢 2.15s）。"""
    global _recording, _recording_id, _video_writer, _out_path, _out_size
    global _frame_count, _written_count, _current_frame, _audio_last_wav

    if not _recording and _video_writer is None:
        return

    # 1) 先停视频（毫秒级）：节拍器看到 _recording=False 就收工，
    #    之后耗时的音频收尾/合并不会再丢视频槽位。
    _recording = False
    _current_frame = None
    _frame_queue.clear()
    real_dur = time.monotonic() - _rec_start   # 必须在音频收尾前取，否则被 ffmpeg 耗时污染

    # 取出本次录音的 wav 文件名与音频锚点后立即清零，避免泄漏到下一次录制。
    awav = None
    audio_start_ts = 0.0
    with _audio_lock:
        _finish_audio_locked()
        awav = _audio_last_wav
        audio_start_ts = _audio_start_ts
        _audio_last_wav = None

    if _video_writer is None:
        # 一帧都没收到就结束，不留垃圾文件（音频已单独保存为 mp3）
        _recording_id = None
        _out_path = None
        _out_size = None
        return

    # 先取名再释放，顺序反了就永远拿不到文件名
    out_path = _out_path
    filename = os.path.basename(out_path) if out_path else "unknown.mp4"
    frames = _written_count
    got = _frame_count
    dur = real_dur

    try:
        _video_writer.release()
    except Exception:
        pass
    _video_writer = None
    _out_path = None
    _out_size = None
    _recording_id = None

    # 把内部录音合并进 mp4：视频流直接 copy，音频转 aac。
    # 这样最终 mp4 既能看画面、也能听到手机内部声音（另一份纯 mp3 已单独保存）。
    if awav:
        awav_full = os.path.join(OUTPUT_DIR, awav)
        if os.path.exists(awav_full):
            v_only = out_path[:-4] + "_v.mp4"

            # 音画对齐（关键）。视频时间轴锚点 = 首帧到达时刻 _rec_start；
            # 音频时间轴锚点 = /audio_start 到达时刻 _audio_start_ts。
            # 手机先建音频再出首帧，两者相差一个"首帧+音频启动"延迟。
            # ffmpeg 默认把两条流都对齐到 0，这段差就成了音频整体滞后。
            # 按错位方向修正：音频开始得早 → 裁掉音频开头；开始得晚 → 整体推迟音频。
            av_off = audio_start_ts - _rec_start
            if av_off < -0.03:
                audio_in = ["-ss", f"{-av_off:.3f}", "-i", awav_full]      # 裁头对齐
            elif av_off > 0.03:
                audio_in = ["-itsoffset", f"{av_off:.3f}", "-i", awav_full]  # 推迟对齐
            else:
                audio_in = ["-i", awav_full]

            try:
                os.replace(out_path, v_only)
                r = subprocess.run(
                    [FFMPEG, "-y", "-loglevel", "error",
                     "-i", v_only] + audio_in + [
                     "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", out_path],
                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=600)
                if r.returncode == 0 and os.path.exists(out_path):
                    try:
                        os.remove(v_only)
                    except Exception:
                        pass
                    print(f"[录制] 已把内部录音合并进 {filename}"
                          f"（音画错位校正 {av_off:+.3f}s）")
                else:
                    # 合并失败则回退成纯视频，不丢画面
                    try:
                        os.replace(v_only, out_path)
                    except Exception:
                        pass
                    print(f"[录制] 音轨合并失败，保留纯视频: "
                          f"{r.stderr.decode('utf-8', 'ignore')[:300]}")
            except Exception as e:
                print(f"[录制] 音轨合并异常: {e}")

    _out_path = None
    _out_size = None
    _recording_id = None

    _recorded_files.insert(0, {
        "filename": filename,
        "time": datetime.now().isoformat(),
        "frames": frames,
        "received": got,
        "duration": round(dur, 1),
        "fps": OUTPUT_FPS,
        "measured_fps": _measured_fps,
        "popped": _popped_count,
        "qdrop": _queue_dropped,
    })
    print(f"[录制] 结束 {filename}：写入 {frames} 帧 / 收到 {got} 帧 / "
          f"铺进时间轴 {_popped_count} 帧 / 队列丢弃 {_queue_dropped} 帧 / "
          f"时长 {dur:.1f}s / {OUTPUT_FPS}fps")


def _start_recording(client_ip: str):
    with _lock:
        _start_recording_locked(client_ip)


def _finish_recording():
    with _lock:
        _finish_recording_locked()


def _update_measured_fps_locked():
    """用最近 1 秒的到达时间戳算实测输入帧率（仅诊断）"""
    global _measured_fps
    if len(_arrivals) < 2:
        return
    now = time.monotonic()
    recent = [t for t in _arrivals if now - t <= 1.0]
    if len(recent) >= 2:
        _measured_fps = round(len(recent) / (recent[-1] - recent[0]) if recent[-1] > recent[0] else 0.0, 1)


def _pacer():
    """
    节拍器：把每一帧铺到它**真实到达的时刻**上，两帧之间用上一帧重复填满。

    两个要点：

    1. 时间轴用**墙钟锚定**：目标写入帧数 = (now - 录制起点) × OUTPUT_FPS。
       落后了就一次性把欠的帧补齐，**绝不跳帧**。
       （早期版本"落后超过 0.25s 就把时间基准对齐到当前"，等于把欠的帧丢掉，
       录得越久丢得越多，视频时长比真实录制时间短 → 播放时明显快放。）

    2. 帧按**到达时间戳排队**消费，而不是只取"最新一帧"。
       手机是成串发帧的，只取最新会把一串里的中间帧整串丢掉，
       时间轴上的运动就变得一蹦一蹦（卡顿）。
    """
    global _written_count, _current_frame, _popped_count

    while True:
        time.sleep(0.001)
        try:
            with _lock:
                if not _recording or _video_writer is None:
                    continue

                now = time.monotonic()
                if _out_size is None:
                    continue

                wrote = False
                # 逐帧槽推进：第 k 帧代表时刻 _rec_start + k/OUTPUT_FPS。
                while True:
                    slot_time = _rec_start + _written_count / OUTPUT_FPS
                    if slot_time > now:
                        break

                    # 【关键】每个时间槽最多消费**一帧**，且只在帧"已经到达"时消费。
                    # 之前这里写的是 while 循环，把"所有已到达的帧"一次全吸进来、
                    # 只留最后一帧 —— 于是成串到达的帧会被折叠成一张，中间帧全丢
                    # （实测 88 帧只活了 58 帧）。改成一次一帧后，
                    # 每一帧都能轮到属于自己的槽位，一帧都不会少。
                    # 注意：判据用"帧到达时刻 <= 当前真实时间 now"，而不是 <= slot_time。
                    # 否则当首帧时间戳比 _rec_start 略晚时，帧永远不满足
                    # frame_time <= slot_time（slot_time 卡在 0 不前进），
                    # 导致 _current_frame 始终为 None、一帧都写不出（0 帧死锁）。
                    if _frame_queue and _frame_queue[0][0] <= now:
                        _current_frame = _frame_queue.popleft()[1]
                        _popped_count += 1

                    if _current_frame is None:
                        break

                    frame = _current_frame
                    if (frame.shape[1], frame.shape[0]) != _out_size:
                        frame = cv2.resize(frame, _out_size, interpolation=cv2.INTER_AREA)
                        _current_frame = frame   # 只缩一次，后面复用

                    _video_writer.write(frame)
                    _written_count += 1
                    wrote = True

                    if _written_count > 0 and (_written_count % MAX_CATCHUP_BURST) == 0:
                        break   # 极端落后时让出锁，下一轮继续补

                if not wrote:
                    continue
        except Exception as e:
            print(f"[节拍器] 异常: {e}")


def _watchdog():
    """收尾守卫：长时间无帧 / 超过上限时兜底结束"""
    while True:
        time.sleep(0.5)
        try:
            with _lock:
                if not _recording:
                    continue
                now = time.monotonic()
                if IDLE_STOP_SEC > 0 and now - _last_frame_ts >= IDLE_STOP_SEC:
                    print(f"[录制] {IDLE_STOP_SEC}s 无新帧，自动结束")
                    _finish_recording_locked()
                elif MAX_RECORD_SEC > 0 and now - _rec_start >= MAX_RECORD_SEC:
                    print(f"[录制] 达到上限 {MAX_RECORD_SEC}s，自动结束")
                    _finish_recording_locked()
        except Exception as e:
            print(f"[守卫] 异常: {e}")


_started = False


def _ensure_threads():
    global _started
    if _started:
        return
    _started = True
    threading.Thread(target=_pacer, daemon=True).start()
    threading.Thread(target=_watchdog, daemon=True).start()


# ── 内部录音状态 ────────────────────────────────────
# 手机端用 AudioPlaybackCapture 采 PCM 直传，这里**边收边写临时 .pcm 文件**，
# 收尾时再包 wav 头 + ffmpeg 转 mp3。
# 改成落盘而不是攒内存：录很久（用户要求不限时长）也不会把 RAM 撑爆。
# 直传 PCM 而不是 AAC：避免 ADTS 头处理的坑，且无损，最利于验证"声音对不对"。
_audio_lock = threading.Lock()
_audio_f = None              # 打开中的临时 PCM 文件句柄（边收边写）
_audio_pcm_path = ""         # 临时 PCM 文件路径
_audio_active = False
_audio_rate = 48000
_audio_ch = 2
_audio_bits = 16
_audio_chunks = 0
_audio_bytes = 0
_audio_start_ts = 0.0
_audio_files = []
_audio_last_wav = None       # 本次录音落盘的 wav 文件名（供合并进 mp4 用）
_audio_arrivals = []         # (到达时刻, 字节数) 账本：收尾时按时间轴补静音对齐音画

# 实时试听：收到的 PCM 也往这个有界队列里放一份，/audio_stream 边收边推给浏览器。
# 缓冲必须小（8 块×200ms≈1.6s）：太大时浏览器一旦读慢，播的就是越来越旧的
# 音频，试听延迟会越拖越大。配合 put 端"满则丢最旧保最新"，把延迟钉在小窗口内。
_audio_stream_q = queue.Queue(maxsize=8)
_audio_stream_clients = 0    # 正在挂着的试听连接数（上限 4，防线程堆积）

# ffmpeg 查找：优先工程内相对路径（随工程走，可移植），再退 PATH，
# 最后才是本机绝对路径兜底（不破坏现有环境）
_HERE = os.path.dirname(os.path.abspath(__file__))
_FFMPEG_CANDIDATES = [
    os.path.join(_HERE, "ffmpeg", "bin", "ffmpeg.exe"),
    os.path.join(_HERE, "..", "ffmpeg", "bin", "ffmpeg.exe"),
    os.path.join(_HERE, "..", "..", "ffmpeg", "bin", "ffmpeg.exe"),
    "ffmpeg",
    r"D:\Program Files\ffmpeg-master-latest-win64-gpl-shared\bin\ffmpeg.exe",
    r"C:\Program Files\ffmpeg\bin\ffmpeg.exe",
]


def _find_ffmpeg():
    for c in _FFMPEG_CANDIDATES:
        if c == "ffmpeg":
            return c
        if os.path.exists(c):
            return c
    return "ffmpeg"


FFMPEG = _find_ffmpeg()


def _finish_audio_locked():
    """把临时 PCM 文件包成 wav，再用 ffmpeg 转 mp3（调用方持 _audio_lock）。
    同时把本次 wav 文件名记到 _audio_last_wav，供录制收尾合并进 mp4。"""
    global _audio_active, _audio_bytes, _audio_chunks
    global _audio_f, _audio_pcm_path, _audio_last_wav
    # 【搬过来时修的一个参考工程缺陷】下面要把 _audio_arrivals 置空，
    # 但它没在 global 里声明过 → 整函数里它被当局部变量，第 585 行一读就
    # "local variable referenced before assignment"，导致收尾永远走 except、
    # wav/mp3 都不产出、mp4 也没音轨。补上声明即恢复「音频独立备份 + 合音轨」。
    global _audio_arrivals

    if not _audio_active:
        return
    _audio_active = False

    # 关掉写入中的临时文件
    if _audio_f is not None:
        try:
            _audio_f.close()
        except Exception:
            pass
        _audio_f = None

    pcm = _audio_pcm_path
    _audio_pcm_path = ""
    if not pcm or not os.path.exists(pcm) or _audio_bytes == 0:
        print("[录音] 没有收到任何音频数据，跳过")
        return

    bytes_per_sec = _audio_rate * _audio_ch * (_audio_bits // 8)

    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    wav_path = os.path.join(OUTPUT_DIR, f"audio_{ts}.wav")
    mp3_path = os.path.join(OUTPUT_DIR, f"audio_{ts}.mp3")

    # 1) 临时 PCM → wav（流式；按块到达时间轴补静音）
    #    "仅画面"等模式会暂停发送音频，这里把暂停段按墙钟补成静音，
    #    使音频时间轴与视频一致——切模式前后音画仍然对齐。
    #    每块在墙钟上的位置 ≈ 到达时刻 - 自身时长（块是填满即发，近似实时）。
    dur = 0.0
    try:
        import wave
        arrivals = list(_audio_arrivals)
        _audio_arrivals = []
        if arrivals:
            t0 = arrivals[0][0] - arrivals[0][1] / float(bytes_per_sec)
            last_end = 0.0
            silence_written = 0.0
            with open(pcm, "rb") as fin, wave.open(wav_path, "wb") as w:
                w.setnchannels(_audio_ch)
                w.setsampwidth(_audio_bits // 8)
                w.setframerate(_audio_rate)
                for arrival, nbytes in arrivals:
                    data = fin.read(nbytes)
                    if not data:
                        break
                    start = (arrival - nbytes / float(bytes_per_sec)) - t0
                    if start > last_end + 0.25:
                        # 时间轴缺口（切模式/暂停发送）：按墙钟补静音
                        gap = min(start - last_end, 3600.0)
                        pad = int(gap * bytes_per_sec) // 2 * 2   # 对齐 2 字节样本
                        w.writeframes(b"\x00" * pad)
                        last_end += pad / float(bytes_per_sec)
                        silence_written += gap
                    if start < last_end:
                        start = last_end                        # 网络抖动重叠：顺延拼接
                    w.writeframes(data)
                    last_end = start + nbytes / float(bytes_per_sec)
            dur = last_end
            if silence_written > 0.5:
                print(f"[录音] 检测到发送暂停，已补静音 {silence_written:.1f}s（音画时间轴对齐）")
        else:
            # 无账本（异常情况）：退回流式直拷
            with open(pcm, "rb") as fin, wave.open(wav_path, "wb") as w:
                w.setnchannels(_audio_ch)
                w.setsampwidth(_audio_bits // 8)
                w.setframerate(_audio_rate)
                while True:
                    buf = fin.read(1 << 20)
                    if not buf:
                        break
                    w.writeframes(buf)
            dur = _audio_bytes / float(bytes_per_sec)
    except Exception as e:
        print(f"[录音] 写 wav 失败: {e}")
        return

    # 2) 客观判断"有没有声音"：流式算 RMS 和峰值（同样低内存）
    try:
        rms2, n, peak = 0.0, 0, 0.0
        with open(pcm, "rb") as fin:
            while True:
                buf = fin.read(1 << 20)
                if not buf:
                    break
                arr = np.frombuffer(buf, dtype="<i2").astype(np.float32) / 32768.0
                if arr.size:
                    rms2 += float(np.sum(arr * arr))
                    m = float(np.max(np.abs(arr)))
                    if m > peak:
                        peak = m
                    n += arr.size
        rms = float(np.sqrt(rms2 / n)) if n else 0.0
    except Exception:
        rms, peak = -1.0, -1.0
    verdict = "有声 ✓" if rms > 0.001 else "疑似静音 ✗"

    # 3) wav → mp3（独立保存一份）
    mp3_ok = False
    try:
        r = subprocess.run(
            [FFMPEG, "-y", "-loglevel", "error", "-i", wav_path,
             "-codec:a", "libmp3lame", "-b:a", "192k", mp3_path],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=300,
        )
        mp3_ok = (r.returncode == 0) and os.path.exists(mp3_path)
        if not mp3_ok:
            print(f"[录音] ffmpeg 转 mp3 失败: {r.stderr.decode('utf-8', 'ignore')[:300]}")
    except Exception as e:
        print(f"[录音] 调用 ffmpeg 失败: {e}")

    # 临时 pcm 已转成 wav，删掉省空间（wav 保留，供合并进 mp4）
    try:
        os.remove(pcm)
    except Exception:
        pass

    _audio_last_wav = os.path.basename(wav_path)

    info = {
        "wav": os.path.basename(wav_path),
        "mp3": os.path.basename(mp3_path) if mp3_ok else None,
        "time": datetime.now().isoformat(),
        "duration": round(dur, 2),
        "chunks": _audio_chunks,
        "bytes": _audio_bytes,
        "rms": round(rms, 5),
        "peak": round(peak, 5),
        "verdict": verdict,
    }
    _audio_files.insert(0, info)

    print(f"[录音] 结束 audio_{ts}.wav：时长 {dur:.2f}s / {_audio_rate}Hz "
          f"{_audio_ch}ch / {_audio_bytes} 字节 / {_audio_chunks} 块")
    print(f"[录音] 音量检测：RMS={rms:.5f}  峰值={peak:.5f}  → {verdict}")
    print(f"[录音] mp3: {info['mp3'] or '(转换失败，保留 wav)'}"
          + (f"  {os.path.getsize(mp3_path)} 字节" if mp3_ok else ""))


def _open_audio_segment_locked():
    """（调用方持 _audio_lock）开启一段新音频：录音开始 / mp4 分段重录时用。
    锚点 = 现在，与新的视频段时间轴对齐。"""
    global _audio_f, _audio_pcm_path, _audio_active
    global _audio_chunks, _audio_bytes, _audio_start_ts
    global _audio_arrivals, _audio_last_wav
    if _audio_f is not None:
        try:
            _audio_f.close()
        except Exception:
            pass
        _audio_f = None
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    _audio_pcm_path = os.path.join(OUTPUT_DIR, f"audio_{ts}.pcm")
    try:
        _audio_f = open(_audio_pcm_path, "wb")
    except Exception as e:
        print(f"[录音] 打开临时文件失败: {e}")
        _audio_f = None
    _audio_chunks = 0
    _audio_bytes = 0
    _audio_active = True
    _audio_start_ts = time.monotonic()   # 必须与视频 _rec_start 同钟，才能算音画错位
    _audio_arrivals = []
    _audio_last_wav = None


# ── 路由 ──────────────────────────────────────────────
@app.route("/start", methods=["POST"])
def api_start():
    """手机主动触发录制开始（也可以不发，首帧到达会自动开始）"""
    _ensure_threads()
    _start_recording(request.remote_addr)
    return jsonify({"status": "started", "recording_id": _recording_id})


@app.route("/stop", methods=["POST"])
def api_stop():
    """手机点击"停止投屏"时调用"""
    _ensure_threads()
    with _lock:
        was = _recording
        _finish_recording_locked()
    return jsonify({"stopped": was})


@app.route("/audio_start", methods=["POST"])
def api_audio_start():
    """手机通知：内部录音开始，并告知采样参数"""
    global _audio_rate, _audio_ch, _audio_bits, _audio_active
    global _audio_chunks, _audio_bytes, _audio_start_ts
    global _audio_f, _audio_pcm_path, _audio_last_wav, _audio_arrivals
    rate = int(request.args.get("rate", 48000))
    ch = int(request.args.get("ch", 2))
    bits = int(request.args.get("bits", 16))
    with _audio_lock:
        _audio_rate, _audio_ch, _audio_bits = rate, ch, bits
        _open_audio_segment_locked()
        _audio_arrivals = []
    # 丢弃上一轮的残留试听数据，避免新一次录音开头播到旧声音
    while not _audio_stream_q.empty():
        try:
            _audio_stream_q.get_nowait()
        except Exception:
            break
    print(f"[录音] 开始 {rate}Hz {ch}ch {bits}bit（AudioPlaybackCapture）")
    return jsonify({"ok": True})


@app.route("/audio", methods=["POST"])
def api_audio_chunk():
    """接收一块 PCM（16bit 交错）"""
    global _audio_chunks, _audio_bytes, _audio_f, _audio_arrivals
    data = request.get_data()
    if not data:
        return jsonify({"error": "empty"}), 400
    with _audio_lock:
        if _audio_active and _audio_f is not None:
            _audio_f.write(data)
            _audio_chunks += 1
            _audio_bytes += len(data)
            _audio_arrivals.append((time.monotonic(), len(data)))
            if _audio_chunks % 25 == 0:
                try:
                    _audio_f.flush()
                except Exception:
                    pass
    # 实时试听：往有界队列放一份。队列满就丢"最旧"的一块、保最新，
    # 浏览器读得慢也不会越积越多，把试听延迟钉在小窗口内（不影响保存）。
    try:
        _audio_stream_q.put_nowait(data)
    except queue.Full:
        try:
            _audio_stream_q.get_nowait()
        except Exception:
            pass
        try:
            _audio_stream_q.put_nowait(data)
        except Exception:
            pass
    d = {"ok": True, "bytes": len(data)}
    cmds = _drain_remote_cmds()
    if cmds:
        d["cmds"] = cmds
    return jsonify(d)


@app.route("/audio_stop", methods=["POST"])
def api_audio_stop():
    """单独收尾音频（不等视频停止）"""
    with _audio_lock:
        _finish_audio_locked()
    return jsonify({"ok": True})


@app.route("/audio_stream", methods=["GET"])
def audio_stream():
    """
    实时试听流：把收到的 PCM 边收边推给浏览器，由 /live 页面用 Web Audio 播放。
    直接转发原始 16bit 交错 PCM（48k/立体声），不做任何编码。
    【绝不中途补静音】—— 静音字节会插进连续波形里，就是"刺啦"爆音的来源；
    没数据时安静等待（连接保持），录停了也不主动断，下一次录音无缝续上。
    """
    global _audio_stream_clients
    with _audio_lock:
        if _audio_stream_clients >= 4:
            return Response("too many listeners", status=503)
        _audio_stream_clients += 1

    def gen():
        global _audio_stream_clients
        try:
            while True:
                try:
                    chunk = _audio_stream_q.get(timeout=5)
                except queue.Empty:
                    continue            # 安静等待，绝不掺静音字节
                except Exception:
                    return
                try:
                    yield chunk
                except Exception:
                    return              # 客户端断开（停止试听/关页面）
        finally:
            with _audio_lock:
                _audio_stream_clients -= 1

    return Response(gen(), mimetype="application/octet-stream")


@app.route("/remote_tap", methods=["POST"])
def remote_tap():
    """PC 预览上的鼠标点击（归一化坐标）→ 排队，随下一次上传/音频响应下发"""
    global _remote_cmd_id
    try:
        fx = float(request.args.get("fx", -1))
        fy = float(request.args.get("fy", -1))
        offx = int(float(request.args.get("offx", 0)))
        offy = int(float(request.args.get("offy", 0)))
    except (TypeError, ValueError):
        return jsonify({"error": "bad fx/fy"}), 400
    if not (0.0 <= fx <= 1.0 and 0.0 <= fy <= 1.0):
        return jsonify({"error": "out of range"}), 400
    with _ctrl_lock:
        _remote_cmd_id += 1
        _remote_cmds.append({"id": _remote_cmd_id, "type": "tap",
                             "fx": fx, "fy": fy, "offx": offx, "offy": offy})
        if len(_remote_cmds) > 50:
            del _remote_cmds[:-50]
    return jsonify({"ok": True})


@app.route("/remote_swipe", methods=["POST"])
def remote_swipe():
    """PC 预览上的鼠标拖动 → 滑动手势（起点/终点归一化坐标 + 时长 ms）"""
    global _remote_cmd_id
    try:
        fx1 = float(request.args.get("fx1", -1))
        fy1 = float(request.args.get("fy1", -1))
        fx2 = float(request.args.get("fx2", -1))
        fy2 = float(request.args.get("fy2", -1))
        ms = int(float(request.args.get("ms", 300)))
        offx = int(float(request.args.get("offx", 0)))
        offy = int(float(request.args.get("offy", 0)))
    except (TypeError, ValueError):
        return jsonify({"error": "bad params"}), 400
    if not all(0.0 <= p <= 1.0 for p in (fx1, fy1, fx2, fy2)):
        return jsonify({"error": "out of range"}), 400
    ms = max(150, min(3000, ms))
    with _ctrl_lock:
        _remote_cmd_id += 1
        _remote_cmds.append({"id": _remote_cmd_id, "type": "swipe",
                             "fx1": fx1, "fy1": fy1, "fx2": fx2, "fy2": fy2,
                             "ms": ms, "offx": offx, "offy": offy})
        if len(_remote_cmds) > 50:
            del _remote_cmds[:-50]
    return jsonify({"ok": True})


@app.route("/record/start", methods=["POST"])
def record_start():
    """PC 预览窗口的「开始录制」：开始一段新录制（视频按到达帧写，音频同时开新段）"""
    with _lock:
        if _recording:
            return jsonify({"ok": True, "already": True})
        _start_recording_locked(request.remote_addr)
    with _audio_lock:
        _open_audio_segment_locked()   # 锚点=现在，与视频段对齐
    print("[录制] 由 PC 预览窗口开始录制")
    return jsonify({"ok": True})


@app.route("/record/stop", methods=["POST"])
def record_stop():
    """PC 预览窗口的「停止录制」：收尾当前录制（独立 mp3 + 音轨合并进 mp4）"""
    with _lock:
        if not _recording and _video_writer is None:
            return jsonify({"ok": True, "already": True, "note": "没有进行中的录制"})
        _finish_recording_locked()
    print("[录制] 由 PC 预览窗口停止录制")
    return jsonify({"ok": True})


@app.route("/upload", methods=["POST"])
def upload_frame():
    """接收 JPEG 帧"""
    global _last_frame_ts, _frame_count
    global _ts_origin_phone, _ts_origin_pc, _last_enqueued_ts, _queue_dropped
    global _size_change_count, _ctrl_ready

    _ensure_threads()
    ip = request.remote_addr
    t_recv = time.monotonic()

    data = request.get_data()
    if not data:
        return jsonify({"error": "empty"}), 400

    _ctrl_ready = (request.headers.get("X-Ctrl") == "1")
    # 解码放在锁外面做：PIL 解码 + 色彩转换在高分辨率下要几十毫秒，
    # 拿在锁里会把节拍器挡在门外，导致时间轴出现缺口
    try:
        pil_img = Image.open(BytesIO(data)).convert("RGB")
        bgr = cv2.cvtColor(np.array(pil_img), cv2.COLOR_RGB2BGR)
    except Exception as e:
        return jsonify({"error": f"bad image: {e}"}), 400

    # 实时画面：原样存下 JPEG 字节，不解码也不重编码，显示开销几乎为零
    _update_live(data, pil_img.size)

    # 手机带上来的采集时刻（相对投屏起点的毫秒数）。用它来铺时间轴，
    # 这样网络抖动、两个发送线程的乱序都不会让画面变得忽快忽慢。
    phone_ts = None
    raw_ts = request.headers.get("X-Capture-Ts")
    if raw_ts:
        try:
            phone_ts = int(raw_ts) / 1000.0
        except (TypeError, ValueError):
            phone_ts = None

    with _lock:
        global _size_change_count
        now = time.monotonic()
        _last_frame_ts = now
        _frame_count += 1
        _arrivals.append(now)
        _update_measured_fps_locked()

        # 默认【不录制】：只有 PC 端点了「● 开始录制」才写文件。
        # 未录制时只更新实时画面（上面 _update_live 已做），帧不入队——
        # 否则开录瞬间队列里的旧帧会混进新录制的时间轴。
        if _recording:
            # 第一帧就把 writer 建起来，之后全部交给节拍器
            if _video_writer is None:
                _create_writer_locked(bgr)
                _size_change_count = 0
            elif (bgr.shape[1], bgr.shape[0]) != _out_size:
                # 手机端切画质档会重建虚拟屏，帧尺寸随之变化。连续 5 帧（剔除
                # 队列里的旧尺寸残帧）确认后：把当前 mp4 **分段落盘**（含音轨
                # 合并），再按新尺寸重开一段——否则 mp4 永远停留在首帧尺寸，
                # 切了画质也看不见效果。
                _size_change_count += 1
                if _size_change_count >= 5:
                    _finish_recording_locked()      # 上一段收尾（含音轨合并）
                    _start_recording_locked(ip)     # 新段视频状态
                    with _audio_lock:
                        _open_audio_segment_locked()  # 新段音频（锚点=现在，与新段视频对齐）
                    _create_writer_locked(bgr)      # 按新尺寸建 writer
                    _size_change_count = 0
                    print("[录制] 画质/分辨率变化，已分段重开录制文件")
            else:
                _size_change_count = 0

            # 换算成"本机时间轴上的时刻"
            if phone_ts is not None:
                if _ts_origin_phone is None:
                    _ts_origin_phone = phone_ts
                    _ts_origin_pc = _rec_start
                frame_time = _ts_origin_pc + (phone_ts - _ts_origin_phone)
            else:
                frame_time = t_recv

            # 两个发送线程可能让帧乱序到达；这里保证时间轴时刻单调不减
            if frame_time < _last_enqueued_ts:
                frame_time = _last_enqueued_ts
            _last_enqueued_ts = frame_time

            # 队列满就丢最旧的（同时计数，方便诊断到底丢了多少）
            if len(_frame_queue) >= FRAME_QUEUE_MAX:
                _frame_queue.popleft()
                _queue_dropped += 1
            _frame_queue.append((frame_time, bgr))

    d = {
        "received": _frame_count,
        "written": _written_count,
        "out_fps": OUTPUT_FPS,
        "measured_fps": _measured_fps,
    }
    cmds = _drain_remote_cmds()
    if cmds:
        d["cmds"] = cmds
    return jsonify(d)


@app.route("/status", methods=["GET"])
def api_status():
    # 先在外面读实时数据：两把锁不嵌套，避免锁序颠倒
    with _live_lock:
        live = {
            "w": _live_w,
            "h": _live_h,
            "fps": _live_fps,
            "bytes": _live_bytes,
            "clients": _live_clients,
            "ctrl": _ctrl_ready,
            "age": (round(time.monotonic() - _live_ts, 1) if _live_ts else None),
        }

    with _lock:
        return jsonify({
            "recording": _recording,
            "recording_id": _recording_id,
            "received": _frame_count if _recording else 0,
            "written": _written_count if _recording else 0,
            "out_fps": OUTPUT_FPS,
            "measured_fps": _measured_fps,
            "elapsed": round(time.monotonic() - _rec_start, 1) if _recording else 0,
            "active_client": _active_client,
            "queue": len(_frame_queue),
            "queue_dropped": _queue_dropped,
            "queue_popped": _popped_count,
            "live": live,
            "last_files": _recorded_files[:5],
            "audio": {
                "recording": _audio_active,
                "rate": _audio_rate,
                "ch": _audio_ch,
                "chunks": _audio_chunks,
                "bytes": _audio_bytes,
                "seconds": round(
                    _audio_bytes / float(_audio_rate * _audio_ch * (_audio_bits // 8)), 2
                ) if _audio_active else 0,
                "last_files": _audio_files[:5],
            },
        })


@app.route("/output/<path:filename>", methods=["GET"])
def api_output(filename):
    return send_from_directory(OUTPUT_DIR, filename, as_attachment=False)


@app.route("/stream", methods=["GET"])
def stream():
    """
    MJPEG 实时流：把手机传来的原始 JPEG 一帧帧推给浏览器。

    这里**不做任何解码/重编码** —— 收到的就是 JPEG，直接转推，
    所以"在电脑上看手机屏幕"这个功能几乎不占额外 CPU。
    画面没变化时手机本来就不产帧，这里也不会重复推同一帧（浏览器会保持上一帧）。
    """
    def gen():
        global _live_clients

        with _live_lock:
            if _live_clients >= MAX_LIVE_CLIENTS:
                print(f"[实时] 已有 {_live_clients} 个观看端，拒绝新连接")
                return
            _live_clients += 1
            print(f"[实时] 观看端接入，当前 {_live_clients} 个")

        try:
            last = None
            last_send = 0.0
            while True:
                with _live_lock:
                    jpg = _live_jpeg
                now = time.monotonic()
                # 有新帧就推；长时间没新帧就重发上一帧保活，
                # 否则一条完全安静的连接容易被中间层当成死连接掐掉
                if jpg is not None and (jpg is not last
                                        or now - last_send >= LIVE_KEEPALIVE_SEC):
                    last = jpg
                    last_send = now
                    yield (b"--frame\r\n"
                           b"Content-Type: image/jpeg\r\n"
                           b"Content-Length: " + str(len(jpg)).encode() + b"\r\n\r\n"
                           + jpg + b"\r\n")
                time.sleep(1.0 / LIVE_FPS)
        finally:
            with _live_lock:
                _live_clients -= 1
                print(f"[实时] 观看端断开，剩余 {_live_clients} 个")

    return Response(gen(), mimetype="multipart/x-mixed-replace; boundary=frame")


@app.route("/frame.jpg", methods=["GET"])
def frame_jpg():
    """最新一帧（单张 JPEG）。低带宽/排障用。"""
    with _live_lock:
        jpg = _live_jpeg
    if jpg is None:
        return Response(status=204)
    resp = Response(jpg, mimetype="image/jpeg")
    resp.headers["Cache-Control"] = "no-store, no-cache, must-revalidate"
    return resp


@app.route("/live", methods=["GET"])
def live():
    """电脑上看手机屏幕的实时页面"""
    return """<!DOCTYPE html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>手机屏幕 · 实时</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{background:#0d0d0d;color:#ddd;height:100vh;display:flex;flex-direction:column;
     overflow:hidden;font-family:"Segoe UI",system-ui,sans-serif}
header{display:flex;align-items:center;gap:18px;padding:10px 16px;background:#161616;
       border-bottom:1px solid #2a2a2a;flex:none}
h1{font-size:15px;font-weight:500;color:#00e676;letter-spacing:.5px;white-space:nowrap}
#stats{font-size:12px;color:#8a8a8a;font-family:Consolas,monospace}
#stats b{color:#4fc3f7;font-weight:500}
#stats i{color:#ffb74d;font-style:normal}
main{flex:1;display:flex;align-items:center;justify-content:center;padding:12px;min-height:0}
#screen{max-width:100%;max-height:100%;object-fit:contain;background:#1a1a1a;
        border-radius:6px;box-shadow:0 0 0 1px #2a2a2a}
#empty{font-size:14px;color:#666;text-align:center;line-height:1.8}
#audioBtn{font-size:12px;padding:6px 12px;border:none;border-radius:5px;
       background:#2196f3;color:#fff;cursor:pointer;font-family:inherit;white-space:nowrap}
#audioBtn.on{background:#f4511e}
#audioStat{font-size:12px;color:#8a8a8a;white-space:nowrap}
</style></head><body>
<header>
  <h1>手机屏幕 · 实时</h1>
  <button id="audioBtn">🔊 开启实时试听</button>
  <span id="audioStat">未开启</span>
  <div id="stats">等待画面...</div>
</header>
<main>
  <img id="screen" alt="手机屏幕" style="display:none">
  <div id="empty">还没有收到手机画面<br>请在手机上点「② 开始投屏录制」</div>
</main>
<script>
var img = document.getElementById('screen');
var emptyBox = document.getElementById('empty');
var stats = document.getElementById('stats');
var shown = false;

function connect() {
  img.src = '/stream?t=' + Date.now();
}
img.onload = function () {
  if (!shown) { shown = true; img.style.display = ''; emptyBox.style.display = 'none'; }
};
img.onerror = function () {
  shown = false;
  img.style.display = 'none';
  emptyBox.style.display = '';
  stats.textContent = '连接中断，正在重连...';
  setTimeout(connect, 1500);
};
// ── 实时试听：/audio_stream → 环形缓冲 → ScriptProcessorNode 拉取播放 ──
// 目标：无爆音 + 低延迟。要点：
//  1. 强制 48kHz 上下文，与手机采样率一致，避免浏览器二次重采样伤音质；
//  2. 网络分块边界不保证 4 字节对齐 —— 字节余数跨块携带，绝不错位采样（刺啦声主因）；
//  3. 拉取模型（onaudioprocess 从环形缓冲取数），天然免疫调度漂移；
//  4. 断流处淡入/将断处淡出（64 样本斜坡），波形不跳变，听感平滑；
//  5. 缓冲 180ms 预填充即播，落后超 0.8s 强制重对齐，延迟钉住不涨。
var audioCtx=null, audioNode=null, audioReader=null, audioRunning=false;
var audioStreamRate=48000;   // 手机端实际音频采样率（音质分档后可能是 96k/192k），从 /status 同步
var RATE=48000, CAP=RATE*2, PREFILL=(RATE*0.18)|0, MAXBUF=(RATE*0.8)|0;
var ringL=null, ringR=null, wpos=0, filled=0;
var primed=false, inGap=false, fadePos=64, carry=null;

function startAudio() {
  if (audioRunning) return;
  var btn = document.getElementById('audioBtn');
  var stat = document.getElementById('audioStat');
  try {
    audioCtx = new (window.AudioContext||window.webkitAudioContext)({sampleRate: RATE});
  } catch (e) {
    try { audioCtx = new (window.AudioContext||window.webkitAudioContext)(); }
    catch (e2) { stat.textContent = '浏览器不支持 WebAudio'; return; }
  }
  ringL = new Float32Array(CAP); ringR = new Float32Array(CAP);
  wpos = 0; filled = 0; primed = false; inGap = true; fadePos = 0; carry = null;
  audioCtx.resume();
  audioRunning = true;
  btn.classList.add('on');
  btn.textContent = '🔇 停止试听';
  stat.textContent = '连接中…';
  fetch('/audio_stream').then(function (r) {
    audioReader = r.body.getReader();
    audioNode = audioCtx.createScriptProcessor(2048, 1, 2);
    audioNode.onaudioprocess = onAudio;
    audioNode.connect(audioCtx.destination);
    stat.textContent = '缓冲中…';
    pump();
  }).catch(function (e) {
    stopAudio();
    document.getElementById('audioStat').textContent = '试听连接失败';
  });
}
function pushSample(l, r) {
  ringL[wpos] = l; ringR[wpos] = r;
  wpos = (wpos + 1) % CAP;
  if (filled < CAP) filled++;
}
function popSample() {
  if (filled <= 0) return null;
  var idx = (wpos - filled + CAP) % CAP;
  filled--;
  return [ringL[idx], ringR[idx]];
}
function handleBytes(u8) {
  var buf;
  if (carry && carry.length) {
    buf = new Uint8Array(carry.length + u8.length);
    buf.set(carry); buf.set(u8, carry.length);
  } else if (u8.byteOffset % 2 !== 0) {
    buf = new Uint8Array(u8.length); buf.set(u8);
  } else {
    buf = u8;
  }
  var usable = buf.length - (buf.length % 4);
  if (usable <= 0) { carry = buf; return; }
  carry = buf.slice(usable);
  var i16 = new Int16Array(buf.buffer, buf.byteOffset, usable / 2);
  var frames = usable >> 2;
  // 手机端音质分档后可能是 96k/192k，而播放上下文固定 48k：
  // 整数倍率用均值滤波降采样（2:1 / 4:1），等效低通，无混叠
  var k = 1;
  if (audioStreamRate > 48000 && audioStreamRate % 48000 === 0) k = audioStreamRate / 48000;
  if (k > 1) {
    var outFrames = (frames / k) | 0;
    for (var i = 0; i < outFrames; i++) {
      var sl = 0, sr = 0;
      for (var q = 0; q < k; q++) { sl += i16[j]; sr += i16[j + 1]; j += 2; }
      pushSample(sl / k / 32768, sr / k / 32768);
    }
  } else {
    for (var i = 0, j = 0; i < frames; i++) {
      pushSample(i16[j] / 32768, i16[j + 1] / 32768);
      j += 2;
    }
  }
}
function onAudio(e) {
  var oL = e.outputBuffer.getChannelData(0), oR = e.outputBuffer.getChannelData(1);
  var n = oL.length, i, s, g;
  if (!primed) {
    if (filled >= PREFILL) { primed = true; document.getElementById('audioStat').textContent = '试听中'; }
    else { for (i = 0; i < n; i++) { oL[i] = 0; oR[i] = 0; } return; }
  }
  if (filled > MAXBUF) {                       // 落后太多：丢积压到 0.5s 重对齐
    filled = (RATE * 0.5) | 0; inGap = true; fadePos = 0;
  }
  for (i = 0; i < n; i++) {
    if (filled <= 0) { inGap = true; fadePos = 0; oL[i] = 0; oR[i] = 0; continue; }
    if (inGap) {                               // 断流恢复：淡入
      g = fadePos / 64; if (fadePos < 64) fadePos++; else inGap = false;
      if (filled < 64) g = Math.min(g, filled / 64);
    } else if (filled < 64) {                  // 即将断流：淡出
      g = filled / 64;
    } else { g = 1; }
    s = popSample();
    oL[i] = s[0] * g; oR[i] = s[1] * g;
  }
}
function pump() {
  if (!audioRunning || !audioReader) return;
  audioReader.read().then(function (res) {
    if (!audioRunning) return;
    if (res.done) { reconnect(); return; }
    handleBytes(res.value);
    pump();
  }).catch(function () { if (audioRunning) reconnect(); });
}
function reconnect() {
  try { if (audioReader) { audioReader.cancel(); } } catch (e) {}
  audioReader = null;
  setTimeout(function () {
    if (!audioRunning) return;
    fetch('/audio_stream').then(function (r) {
      if (!audioRunning) return;
      audioReader = r.body.getReader();
      pump();
    }).catch(function () { setTimeout(reconnect, 2000); });
  }, 1000);
}
function stopAudio() {
  audioRunning = false;
  try { if (audioReader) audioReader.cancel(); } catch (e) {}
  try { if (audioNode) { audioNode.onaudioprocess = null; audioNode.disconnect(); } } catch (e) {}
  try { if (audioCtx) audioCtx.close(); } catch (e) {}
  audioCtx = null; audioNode = null; audioReader = null;
  primed = false; filled = 0; carry = null;
  var btn = document.getElementById('audioBtn');
  btn.classList.remove('on');
  btn.textContent = '🔊 开启实时试听';
  document.getElementById('audioStat').textContent = '已停止';
}
document.getElementById('audioBtn').addEventListener('click', function () {
  if (!audioRunning) { startAudio(); } else { stopAudio(); }
});

connect();

function refresh() {
  fetch('/status').then(function (r) { return r.json(); }).then(function (d) {
    var L = d.live || {};
    if (d.audio && d.audio.rate) { audioStreamRate = d.audio.rate; }
    var parts = [];
    if (L.w) { parts.push('<b>' + L.w + 'x' + L.h + '</b>'); }
    if (L.fps) { parts.push('<b>' + L.fps + '</b> fps'); }
    if (L.bytes) { parts.push('<b>' + (L.bytes / 1024).toFixed(1) + '</b> KB'); }
    if (L.clients) { parts.push('观看 <b>' + L.clients + '</b>'); }
    if (L.age !== null && L.age !== undefined) { parts.push(L.age + 's 前'); }
    if (d.recording) { parts.push('<i>[REC]</i> ' + d.elapsed + 's / ' + d.written + ' 帧'); }
    stats.innerHTML = parts.length ? parts.join(' &nbsp;·&nbsp; ') : '等待画面...';
  }).catch(function () {
    stats.textContent = 'PC 端服务无响应';
  });
}
refresh();
setInterval(refresh, 1000);
</script></body></html>"""


@app.route("/", methods=["GET"])
def index():
    banner = "端口 5423 &nbsp;|&nbsp; 不限时（手机点停止才停）&nbsp;|&nbsp; 固定 60fps &nbsp;|&nbsp; 画面+内部声音"
    return """<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>投屏录屏 PC 端</title>
<style>body{font-family:monospace;background:#111;color:#0f0;padding:40px}
h1{color:#0f0}.ok{color:#0f0}.err{color:#f55}
a.live{display:inline-block;margin:12px 0 20px;padding:10px 18px;background:#00e676;
       color:#111;text-decoration:none;border-radius:5px;font-weight:bold}</style>
</head><body>
<h1>PhoneHub 投屏录屏 - PC 端</h1>
<p>""" + banner + """</p>
<a class="live" href="/live">▶ 打开手机屏幕实时画面</a>
<pre id="log"></pre>
<script>
function refresh(){
  fetch('/status').then(r=>r.json()).then(function(d){
    var el=document.getElementById('log');
    var status=(d.recording)?('<span class="ok">[REC] 录制中 #'+d.recording_id+'</span>')
                            :('<span class="err">o 待机</span>');
    el.innerHTML='状态：'+status
      +'<br>收到帧：'+d.received+'  写入帧：'+d.written
      +'<br>输出帧率：'+d.out_fps+' fps（固定）  实测输入：'+(d.measured_fps||'-')+' fps'
      +'<br>已录制：'+d.elapsed+'s  客户端：'+(d.active_client||'-');
    if(d.last_files.length){
      var list='<br><br>最近录制:<br>';
      d.last_files.forEach(function(f){
        list+='<a href="/output/'+f.filename+'" target="_blank" style="color:#0ff">'
              +f.filename+'</a> ('+f.frames+'帧, '+f.duration+'s, '+f.fps+'fps)<br>';
      });
      el.innerHTML+=list;
    }
  });
}
refresh();
setInterval(refresh, 1000);
</script></body></html>"""


# ── 嵌入式启停接口（桌面 app 使用）────────────────────────────────
# 参考工程是"命令行起一个进程"，这里多给一组函数让桌面 app 能在自己进程里以线程方式
# 启停同一套服务。命令行运行（__main__ 分支）依旧走 app.run，行为不变。
_run_lock = threading.Lock()
_http_server = None
_server_thread = None


def start_server_thread(port=None, host="0.0.0.0"):
    """在后台线程里起 HTTP 服务；返回 True 表示已启动（或本来就在跑）。"""
    global _http_server, _server_thread, PORT
    if port:
        PORT = int(port)
    with _run_lock:
        if _server_thread is not None and _server_thread.is_alive():
            return True
        try:
            from werkzeug.serving import make_server
            _http_server = make_server(host, PORT, app, threaded=True)
        except Exception as e:
            print(f"[投屏服务] 启动失败: {e}")
            return False
        _ensure_threads()
        _server_thread = threading.Thread(target=_http_server.serve_forever,
                                          daemon=True, name="mirror-http")
        _server_thread.start()
        print("=" * 50)
        print("  PhoneHub 投屏录屏 PC 端（内嵌于桌面 app）")
        print(f"  监听端口: {PORT}")
        print(f"  录制时长: 不限时，直到手机调用 /stop（点「停止投屏」）")
        print(f"  输出帧率: 固定 {OUTPUT_FPS} fps")
        print(f"  输出目录: {os.path.abspath(OUTPUT_DIR)}")
        print("=" * 50)
        return True


def stop_server():
    """停掉 HTTP 服务。

    刻意不主动给录制收尾：参考工程的设计是"手机点停止投屏才收尾"，
    这里保持一致，避免桌面 app 关服务时产出残缺文件。
    """
    global _http_server, _server_thread
    srv = _http_server
    _http_server = None
    _server_thread = None
    if srv is None:
        return
    try:
        srv.shutdown()
        srv.server_close()
        print("[投屏服务] 已停止")
    except Exception as e:
        print(f"[投屏服务] 停止出错: {e}")


def is_server_running():
    return _server_thread is not None and _server_thread.is_alive()


def open_live_window():
    """"打开手机屏幕"窗口（桌面 app 的按钮用，忽略 PHONEHUB_LIVE_WINDOW 开关）。"""
    _open_live_window(force=True)


if __name__ == "__main__":
    _ensure_threads()
    print("=" * 50)
    print("  PhoneHub 投屏录屏 PC 端")
    print(f"  监听端口: {PORT}")
    print(f"  录制时长: 不限时，直到手机调用 /stop（点「停止投屏」）")
    print(f"  输出帧率: 固定 {OUTPUT_FPS} fps")
    print(f"  输出目录: {os.path.abspath(OUTPUT_DIR)}")
    print("=" * 50)
    app.run(host="0.0.0.0", port=PORT, threaded=True)
