"""
PhoneHub 电脑屏幕远程网页
====================================
独立 Flask 程序，端口 1845。浏览器打开后实时显示电脑屏幕，并支持点击/移动操控电脑。

特点：
- 进入网页才开始截屏推流（无浏览器连接时自动停止，节省 CPU）
- 60fps 推送，JPEG 中等清晰度，追求流畅
- 点击/拖拽通过 ctypes SendInput 操控电脑鼠标，无蓝框/绿框（网页内不做任何框选）

运行：python desktop/web_pcscreen.py
"""

import io
import time
import threading
import logging

from flask import Flask, Response, request, jsonify

# ==================== 配置 ====================

HOST = "0.0.0.0"
PORT = 1845
FPS = 60
FRAME_DELAY = 1.0 / FPS
JPEG_QUALITY = 70            # 画质够用，放弃高清晰度换取流畅
MAX_WIDTH = 1024             # 降采样宽，编码更快更流畅
SCREEN_W = 0                 # 屏幕逻辑宽，点击映射用
SCREEN_H = 0

logging.basicConfig(level=logging.INFO, format="%(asctime)s [PCScreen] %(levelname)s %(message)s")
logger = logging.getLogger("phonehub-pcscreen")

# 共享最新帧
latest_frame = None
frame_lock = threading.Lock()
screen_winsize = []          # [w, h] 被缩放后的画面逻辑尺寸（点击归一化映射用）

# ==================== 截屏线程 ====================

def screen_size():
    """返回主屏逻辑宽高"""
    try:
        import ctypes
        u = ctypes.windll.user32
        return u.GetSystemMetrics(0), u.GetSystemMetrics(1)
    except Exception:
        return 1920, 1080


def capture_loop():
    """有客户端连接时截屏；无连接时进入低频待机"""
    global latest_frame, SCREEN_W, SCREEN_H
    try:
        import mss
        from PIL import Image
    except ImportError:
        logger.error("需要 mss 和 Pillow: pip install mss Pillow")
        return
    SCREEN_W, SCREEN_H = screen_size()
    sct = mss.mss()
    monitor = sct.monitors[1]
    while True:
        try:
            if not clients_connected():
                time.sleep(0.5)
                continue
            t0 = time.time()
            shot = sct.grab(monitor)
            img = Image.frombytes("RGB", shot.size, shot.bgra, "raw", "BGRX")
            w, h = img.size
            if w > MAX_WIDTH:
                r = MAX_WIDTH / w
                img = img.resize((MAX_WIDTH, int(h * r)), Image.BILINEAR)
            buf = io.BytesIO()
            # 不用 optimize（会二次扫描拖慢编码），质量优先
            img.save(buf, format="JPEG", quality=JPEG_QUALITY)
            global latest_frame, screen_winsize
            with frame_lock:
                latest_frame = buf.getvalue()
                screen_winsize = (img.width, img.height)
            # 帧间隔补偿仅在提前完成时启用，避免固定 sleep 压制帧率
            dt = time.time() - t0
            if dt < FRAME_DELAY:
                time.sleep(FRAME_DELAY - dt)
        except Exception as e:
            logger.warning(f"截屏失败: {e}")
            time.sleep(0.05)


# 活跃客户端计数
_active_clients = 0
_clients_lock = threading.Lock()
_keyboard_opened = False  # 屏幕键盘是否已自动打开（本次会话）
cursor_nx = 0.5   # 指示图案归一化位置 x
cursor_ny = 0.5   # 指示图案归一化位置 y


def clients_connected():
    with _clients_lock:
        return _active_clients > 0


# ==================== Flask 路由 ====================

app = Flask(__name__)


def gen_frames():
    """MJPEG 流生成器：连接存在期间不断吐帧"""
    global _active_clients
    with _clients_lock:
        _active_clients += 1
        if _active_clients == 1:
            # 检测到有用户访问，自动打开 Windows 屏幕键盘
            open_screen_keyboard()
    try:
        while True:
            with frame_lock:
                frame = latest_frame
            if frame:
                yield (b"--frame\r\n"
                       b"Content-Type: image/jpeg\r\n\r\n" + frame + b"\r\n")
            time.sleep(FRAME_DELAY)
    finally:
        with _clients_lock:
            _active_clients -= 1
            if _active_clients <= 0:
                _active_clients = 0
                _keyboard_opened = False  # 所有用户断开，复位，下次访问再自动打开


def open_screen_keyboard():
    """打开 Windows 屏幕键盘（osk.exe）。仅启动一次，不重复。"""
    global _keyboard_opened
    if _keyboard_opened:
        return
    _keyboard_opened = True
    try:
        import subprocess
        import ctypes
        # 用 ShellExecute，避免因进程句柄持有导致窗口被关闭
        result = ctypes.windll.shell32.ShellExecuteW(
            None, "open", r"C:\Windows\System32\osk.exe", None, None, 1)
        if result <= 32:
            logger.warning(f"打开屏幕键盘失败，ShellExecute 返回: {result}")
    except Exception as e:
        logger.warning(f"打开屏幕键盘异常: {e}")


@app.route("/")
def index():
    return HTML_PAGE


@app.route("/stream")
def stream():
    return Response(gen_frames(), mimetype="multipart/x-mixed-replace; boundary=frame")


@app.route("/api/cursor")
def cursor_pos():
    """返回真实鼠标位置（归一化 0-1），网页准星据此显示，杜绝漂移"""
    try:
        import ctypes
        class POINT(ctypes.Structure):
            _fields_ = [("x", ctypes.c_long), ("y", ctypes.c_long)]
        pt = POINT()
        if ctypes.windll.user32.GetCursorPos(ctypes.byref(pt)):
            nx = pt.x / SCREEN_W if SCREEN_W else pt.x
            ny = pt.y / SCREEN_H if SCREEN_H else pt.y
            return jsonify({"x": max(0.0, min(1.0, nx)), "y": max(0.0, min(1.0, ny))})
    except Exception:
        pass
    return jsonify({"x": cursor_nx, "y": cursor_ny})


@app.route("/api/screen")
def screen_info():
    """返回屏幕物理分辨率，供前端做 CSS→物理像素换算"""
    return jsonify({"w": SCREEN_W, "h": SCREEN_H})


@app.route("/api/mouse", methods=["POST"])
def mouse():
    """接收网页上报的鼠标操作：move/click/down/up/right/relmove"""
    global cursor_nx, cursor_ny
    try:
        data = request.get_json(force=True, silent=True) or {}
        nx = float(data.get("x", 0.5))
        ny = float(data.get("y", 0.5))
        op = data.get("op", "click")
        dx = float(data.get("dx", 0))
        dy = float(data.get("dy", 0))
        _perform_mouse(nx, ny, op, dx, dy)
        # 更新指示图案位置（相对移动需换算，由前端同时上报新位置）
        if "nx" in data and "ny" in data:
            cursor_nx = float(data["nx"])
            cursor_ny = float(data["ny"])
        elif op not in ("relmove",):
            cursor_nx = nx
            cursor_ny = ny
        return jsonify({"status": "ok"})
    except Exception as e:
        logger.warning(f"鼠标操作失败: {e}")
        return jsonify({"status": "error", "msg": str(e)}), 400


def _perform_mouse(nx, ny, op, dx=0, dy=0):
    """ctypes SendInput 模拟鼠标（归一化坐标 0-1）或相对移动（dx/dy 像素）"""
    import ctypes
    from ctypes import wintypes

    u = ctypes.windll.user32
    sw = SCREEN_W if SCREEN_W else u.GetSystemMetrics(0)
    sh = SCREEN_H if SCREEN_H else u.GetSystemMetrics(1)

    MOUSEEVENTF_MOVE = 0x0001
    MOUSEEVENTF_LEFTDOWN = 0x0002
    MOUSEEVENTF_LEFTUP = 0x0004
    MOUSEEVENTF_RIGHTDOWN = 0x0008
    MOUSEEVENTF_RIGHTUP = 0x0010
    MOUSEEVENTF_ABSOLUTE = 0x8000

    abs_x = int(max(0.0, min(1.0, nx)) * 65535)
    abs_y = int(max(0.0, min(1.0, ny)) * 65535)

    class MOUSEINPUT(ctypes.Structure):
        _fields_ = [
            ("dx", wintypes.LONG), ("dy", wintypes.LONG),
            ("mouseData", wintypes.DWORD), ("dwFlags", wintypes.DWORD),
            ("time", wintypes.DWORD), ("dwExtraInfo", ctypes.POINTER(wintypes.ULONG)),
        ]

    class INPUT(ctypes.Structure):
        class _U(ctypes.Union):
            _fields_ = [("mi", MOUSEINPUT)]
        _anonymous_ = ("_u",)
        _fields_ = [("type", wintypes.DWORD), ("_u", _U)]

    def send(flags, mx=abs_x, my=abs_y):
        i = INPUT(); i.type = 0
        i.mi = MOUSEINPUT(dx=mx, dy=my, mouseData=0, dwFlags=flags, time=0,
                          dwExtraInfo=ctypes.pointer(wintypes.ULONG(0)))
        u.SendInput(1, ctypes.byref(i), ctypes.sizeof(INPUT))

    # 相对移动（触摸板）：不带 ABSOLUTE 标志，dx/dy 为像素增量
    if op == "relmove":
        send(MOUSEEVENTF_MOVE, int(dx), int(dy))
        return

    if op in ("down", "click"):
        send(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_LEFTDOWN)
        if op == "click":
            send(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_LEFTUP)
    elif op == "up":
        send(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_LEFTUP)
    elif op == "move":
        send(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE)
    elif op == "right":
        send(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_RIGHTDOWN)
        send(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_RIGHTUP)
    elif op in ("leftdown", "leftup"):
        flags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | (MOUSEEVENTF_LEFTDOWN if op == "leftdown" else MOUSEEVENTF_LEFTUP)
        send(flags)
    else:
        send(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_LEFTDOWN)
        send(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_LEFTUP)


# ==================== 网页 ====================

HTML_PAGE = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>电脑屏幕</title>
<style>
  html, body { margin:0; padding:0; height:100%; background:#000; overflow:hidden; }
  * { -webkit-user-select:none; user-select:none; -webkit-tap-highlight-color:transparent; box-sizing:border-box; }
  #layout { display:flex; height:100vh; width:100vw; }
  /* ---- 画面区 ---- */
  #screendiv { flex:1; position:relative; background:#000; overflow:hidden; }
  #screen {
    position:absolute; left:50%; top:50%; transform:translate(-50%,-50%);
    max-width:100%; max-height:100%;
    -webkit-user-drag:none; border:none;
  }
  /* ---- 指示图案（准星）---- */
  #indicator {
    position:absolute; width:0; height:0; pointer-events:none; z-index:5;
  }
  #indicator::before, #indicator::after {
    content:""; position:absolute; background:#0f0;
  }
  #indicator::before { width:44px; height:2px; left:-22px; top:-1px; }
  #indicator::after  { width:2px; height:44px; left:-1px; top:-22px; }
  #indicator .dot {
    position:absolute; width:10px; height:10px; border-radius:50%;
    background:#0f0; left:-5px; top:-5px;
    box-shadow:0 0 6px #0f0;
  }
  /* ---- 右侧控制面板 ---- */
  #panel {
    width:200px; flex:0 0 200px;
    display:flex; flex-direction:column; gap:12px;
    padding:14px; background:#1a1a1a; color:#eee; overflow-y:auto;
  }
  .btn {
    width:100%; height:64px; font-size:18px; font-weight:bold;
    border:none; border-radius:10px; cursor:pointer; color:#000;
  }
  #btnL { background:#4a9eff; }
  #btnR { background:#ff7a45; }
  .btn:active { filter:brightness(1.2); }
  label { font-size:13px; color:#aaa; margin-bottom:2px; }
  /* ---- 触摸板 ---- */
  #trackpad {
    height:220px; width:100%; background:#222; border-radius:10px;
    border:1px solid #444; position:relative;
    touch-action:none; cursor:grab;
  }
  #trackpad .pad {
    position:absolute; width:44px; height:44px; border-radius:50%;
    background:#3a3a3a; border:1px solid #555; left:50%; top:50%;
    transform:translate(-50%,-50%);
  }
  #trackpad.active { border-color:#0f0; }
  .tip { font-size:11px; color:#666; text-align:center; margin-top:-6px; }
</style>
</head>
<body>
<div id="layout">
  <div id="screendiv">
    <img id="screen" src="/stream" alt="电脑屏幕">
    <div id="indicator"><span class="dot"></span></div>
  </div>
  <div id="panel">
    <button class="btn" id="btnL">左键</button>
    <button class="btn" id="btnR">右键</button>
    <label>触摸板（拖动移动鼠标）</label>
    <div id="trackpad"><div class="pad"></div></div>
    <div class="tip">在画面上点击=绝对移动指针</div>
  </div>
</div>
<script>
  var img = document.getElementById('screen');
  var indicator = document.getElementById('indicator');
  var screenDiv = document.getElementById('screendiv');
  var trackpad = document.getElementById('trackpad');

  // ---- 指示图案：轮询服务端真实鼠标位置，杜绝漂移 ----
  var cursorX = 0.5, cursorY = 0.5;
  function post(obj) {
    fetch('/api/mouse', {
      method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(obj)
    }).catch(function(){});
  }
  function updateIndicator() {
    var r = img.getBoundingClientRect();
    indicator.style.left = (r.left + cursorX * r.width) + 'px';
    indicator.style.top  = (r.top  + cursorY * r.height) + 'px';
  }
  function pollCursor() {
    fetch('/api/cursor', {cache:'no-store'}).then(function(r){return r.json();}).then(function(d){
      if (d && typeof d.x === 'number') {
        var changed = Math.abs(d.x-cursorX)>0.005 || Math.abs(d.y-cursorY)>0.005;
        cursorX = d.x; cursorY = d.y;
        if (changed) updateIndicator();
      }
    }).catch(function(){});
  }
  setInterval(pollCursor, 120);   // 高频率轮询，准星实时贴合真实鼠标
  pollCursor();

  // 屏幕物理分辨率（CSS→物理像素换算用）
  var screenW = 0, screenH = 0;
  fetch('/api/screen').then(function(r){return r.json();}).then(function(d){
    screenW = d.w; screenH = d.h;
  }).catch(function(){});

  // ---- 接入画面后实时校正一次真实鼠标位置 ----
  pollCursor();

  // ---- 绝对定位：点击画面任意处，指针跳到该点（只移动不点击）----
  screenDiv.addEventListener('pointerdown', function(ev){
    if (ev.target === img || ev.target === screenDiv) {
      var r = img.getBoundingClientRect();
      var nx = Math.max(0, Math.min(1, (ev.clientX - r.left)/r.width));
      var ny = Math.max(0, Math.min(1, (ev.clientY - r.top)/r.height));
      cursorX = nx; cursorY = ny; updateIndicator();
      post({ x:nx.toFixed(4), y:ny.toFixed(4), op:'move' });
    }
  });

  // ---- 左右键：在当前指针位置点击 ----
  var btnL = document.getElementById('btnL');
  var btnR = document.getElementById('btnR');
  btnL.addEventListener('pointerdown', function(){ post({ x:cursorX.toFixed(4), y:cursorY.toFixed(4), op:'click' }); });
  btnR.addEventListener('pointerdown', function(){ post({ x:cursorX.toFixed(4), y:cursorY.toFixed(4), op:'right' }); });

  // ---- 触摸板：按住拖动=相对移动鼠标 ----
  var lastX=0, lastY=0, padActive=false;
  trackpad.addEventListener('pointerdown', function(ev){
    ev.preventDefault(); trackpad.setPointerCapture(ev.pointerId);
    lastX = ev.clientX; lastY = ev.clientY; padActive = true;
    trackpad.classList.add('active');
  });
  trackpad.addEventListener('pointermove', function(ev){
    if(!padActive) return;
    ev.preventDefault();
    var dx = ev.clientX - lastX;
    var dy = ev.clientY - lastY;
    if (dx===0 && dy===0) return;
    lastX = ev.clientX; lastY = ev.clientY;
    // 灵敏度（超低速度，便于精细操控）
    var scale = 0.4;
    // CSS 像素 → 物理像素：画面物理宽 / 画面 CSS 显示宽
    var r = img.getBoundingClientRect();
    var ratioX = (screenW > 0 && r.width > 0) ? (screenW / r.width) * scale : scale;
    var ratioY = (screenH > 0 && r.height > 0) ? (screenH / r.height) * scale : scale;
    var physX = dx * ratioX;
    var physY = dy * ratioY;
    // 相对移动上报物理像素增量，由后端换算成归一化（与后端一致，不闪）
    post({ dx: physX.toFixed(0), dy: physY.toFixed(0), op:'relmove' });
  });
  function padEnd(){ padActive=false; trackpad.classList.remove('active'); }
  trackpad.addEventListener('pointerup', padEnd);
  trackpad.addEventListener('pointercancel', padEnd);

  // 窗口尺寸变化时校正指示图案位置
  window.addEventListener('resize', updateIndicator);
</script>
</body>
</html>
"""


# ==================== 启动 ====================

def main():
    # 声明 DPI-aware，让截图与鼠标坐标统一为物理像素，避免缩放导致准星偏移
    try:
        import ctypes
        ctypes.windll.shcore.SetProcessDpiAwareness(2)
    except Exception:
        try:
            ctypes.windll.user32.SetProcessDPIAware()
        except Exception:
            pass
    threading.Thread(target=capture_loop, daemon=True).start()
    logger.info(f"电脑屏幕网页已启动: http://localhost:{PORT}")
    app.run(host=HOST, port=PORT, threaded=True)


if __name__ == "__main__":
    main()