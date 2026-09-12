"""投屏与反向控制（PhoneHub 桌面端）

投屏这一套是从 `cs_apps/Screen_mirroring/` **原样搬进来**的，端口与协议一律不变：
  · `desktop/mirror_server.py`  监听 **5423**（`PHONEHUB_MIRROR_PORT` 可覆盖），路由
    `/upload /audio /audio_start /audio_stop /audio_stream /remote_tap /remote_swipe
     /record/start /record/stop /start /stop /status /stream /frame.jpg /live /output/<f>`
  · `desktop/live_window.py`   「手机屏幕」窗口（tkinter）：画面、声音、录制开关、
    两点滑动、方向键校准都在它里面（与参考工程一致，服务开始录制时自动拉起）

本页只负责四件事：启停这个服务、打开窗口/`/live` 网页、显示 `/status`、控制录制。
其余保留原有与投屏引擎无关的能力：手机遥控（锁屏/返回/主屏/最近/通知栏/控制中心）、
音量与静音、手机截图、「推流电脑画面到手机」、「电脑声音传到手机」。
"""

import os
import threading
import time
import webbrowser

from PyQt5.QtCore import Qt, QTimer, pyqtSignal
from PyQt5.QtGui import QFont
from PyQt5.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout, QMessageBox, QSlider, QSizePolicy)

from styles import dark_msg_box
from qfluentwidgets import (CardWidget, TitleLabel, BodyLabel, SubtitleLabel,
                            PushButton, PrimaryPushButton, setFont,
                            InfoBar, InfoBarPosition)

try:
    import requests
except Exception:                                    # 理论上必有（requirements 里就有）
    requests = None

# 服务没起来时先用参考工程的默认端口显示，起来后再以服务实际端口为准
DEFAULT_MIRROR_PORT = 5423


class ScreenMirrorPage(QWidget):
    """投屏与反向控制：承载 cs_apps/Screen_mirroring 的 PC 端程序"""

    # 后台线程 → UI 线程
    _svc_result = pyqtSignal(bool, str)      # 启动服务结果
    _svc_stop_result = pyqtSignal(str)       # 停止服务结果
    _status_result = pyqtSignal(dict)        # /status 结果（空 dict 表示没拿到）
    _record_result = pyqtSignal(bool, str)   # (是否成功, 说明)

    def __init__(self, manager):
        super().__init__()
        self.manager = manager
        self._is_phone_muted = False
        self._svc_module = None              # 延迟 import 的 mirror_server（避免把 cv2 拖进启动路径）
        self._svc_starting = False
        self._svc_ok = False
        self._status_busy = False
        self._auto_started = False
        self._live_window_opened = False
        self._mirror_on = False
        self._recording = False

        self._setup_ui()
        self._connect_signals()
        self._update_button_states()

        # 每秒拉一次 /status（网络请求放后台线程，别卡 UI）
        self._poll_timer = QTimer(self)
        self._poll_timer.setInterval(1000)
        self._poll_timer.timeout.connect(self._poll_status)
        self._poll_timer.start()
        self._poll_status()

    # ==================== 界面 ====================

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 20, 24, 20)
        layout.setSpacing(14)

        title = TitleLabel("投屏与反向控制")
        title.setObjectName("titleLabel")
        setFont(title, 28, QFont.Bold)
        layout.addWidget(title)

        self.channel_label = SubtitleLabel("当前通道: --")
        layout.addWidget(self.channel_label)

        # ---------- 卡片 1：投屏服务（5423） ----------
        svc_frame = CardWidget()
        svc_layout = QVBoxLayout(svc_frame)
        svc_layout.setContentsMargins(16, 12, 16, 12)
        svc_layout.setSpacing(8)

        svc_layout.addWidget(SubtitleLabel("投屏服务（PC 端 5423 · 手机→电脑）"))

        self.svc_state_label = BodyLabel("服务状态：未启动")
        self.svc_state_label.setWordWrap(True)
        svc_layout.addWidget(self.svc_state_label)

        self.svc_stat_label = BodyLabel("等待画面…")
        self.svc_stat_label.setWordWrap(True)
        svc_layout.addWidget(self.svc_stat_label)

        svc_btn_row = QHBoxLayout()
        svc_btn_row.setSpacing(10)
        self.btn_svc_start = PrimaryPushButton("启动投屏服务")
        self.btn_svc_stop = PushButton("停止投屏服务")
        self.btn_live_window = PushButton("打开手机屏幕窗口")
        self.btn_live_web = PushButton("浏览器打开 /live")
        for b in (self.btn_svc_start, self.btn_svc_stop, self.btn_live_window, self.btn_live_web):
            svc_btn_row.addWidget(b)
        svc_btn_row.addStretch()
        svc_layout.addLayout(svc_btn_row)
        layout.addWidget(svc_frame)

        # ---------- 卡片 2：本次投屏 ----------
        run_frame = CardWidget()
        run_layout = QVBoxLayout(run_frame)
        run_layout.setContentsMargins(16, 12, 16, 12)
        run_layout.setSpacing(8)

        run_layout.addWidget(SubtitleLabel("本次投屏"))

        run_btn_row = QHBoxLayout()
        run_btn_row.setSpacing(10)
        self.phone_to_pc_btn = PrimaryPushButton("手机投屏到电脑")
        run_btn_row.addWidget(self.phone_to_pc_btn)
        self.rec_btn = PushButton("● 开始录制")
        self.rec_btn.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
        run_btn_row.addWidget(self.rec_btn)
        self.btn_open_output = PushButton("打开录制目录")
        self.btn_open_output.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
        run_btn_row.addWidget(self.btn_open_output)
        run_layout.addLayout(run_btn_row)

        self.rec_status_label = BodyLabel("录制：未开始（默认不录制，点「开始录制」或窗口上的录制键）")
        self.rec_status_label.setWordWrap(True)
        run_layout.addWidget(self.rec_status_label)
        layout.addWidget(run_frame)

        # ---------- 卡片 3：快捷控制（与投屏引擎无关，保留原能力）----------
        control_frame = CardWidget()
        control_layout = QVBoxLayout(control_frame)
        control_layout.setContentsMargins(16, 12, 16, 12)
        control_layout.setSpacing(8)
        control_layout.addWidget(SubtitleLabel("快捷控制"))

        vol_row = QHBoxLayout()
        vol_row.addWidget(BodyLabel("音量"))
        self.vol_slider = QSlider(Qt.Horizontal)
        self.vol_slider.setRange(0, 15)
        self.vol_slider.setValue(7)
        self.vol_slider.setFixedWidth(200)
        self._vol_sync_enabled = True
        vol_row.addWidget(self.vol_slider)
        self.vol_value_label = BodyLabel("7")
        self.vol_value_label.setFixedWidth(30)
        vol_row.addWidget(self.vol_value_label)
        self.vol_mute_btn = PushButton("静音")
        vol_row.addWidget(self.vol_mute_btn)
        self.mute_indicator = BodyLabel("未静音")
        self.mute_indicator.setObjectName("muteIndicator")
        self.mute_indicator.setStyleSheet("color: #FF6B6B; font-weight: bold;")
        vol_row.addWidget(self.mute_indicator)
        vol_row.addStretch()
        control_layout.addLayout(vol_row)

        btn_row = QHBoxLayout()
        self.btn_lock = PushButton("锁屏")
        self.btn_back = PushButton("返回")
        self.btn_home = PushButton("主屏")
        self.btn_recents = PushButton("最近任务")
        self.btn_notif_panel = PushButton("通知栏")
        self.btn_control_center = PushButton("控制中心")
        self.btn_phone_screenshot = PushButton("手机截图")
        for b in (self.btn_lock, self.btn_back, self.btn_home, self.btn_recents,
                  self.btn_notif_panel, self.btn_control_center, self.btn_phone_screenshot):
            btn_row.addWidget(b)
        btn_row.addStretch()
        control_layout.addLayout(btn_row)
        layout.addWidget(control_frame)

        # ---------- 卡片 4：反方向（电脑→手机）----------
        pc_frame = CardWidget()
        pc_layout = QHBoxLayout(pc_frame)
        pc_layout.setContentsMargins(16, 12, 16, 12)
        pc_layout.setSpacing(10)
        self.pc_label = SubtitleLabel("电脑→手机")
        self.pc_label.setSizePolicy(QSizePolicy.Fixed, QSizePolicy.Fixed)
        pc_layout.addWidget(self.pc_label)
        self.pc_stream_btn = PushButton("推流电脑画面到手机")
        self.pc_stream_btn.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
        pc_layout.addWidget(self.pc_stream_btn)
        self.audio_btn = PushButton("开始声音传输")
        self.audio_btn.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
        pc_layout.addWidget(self.audio_btn)
        layout.addWidget(pc_frame)

        self.hint_label = BodyLabel(
            "投屏链路：手机端「投屏」页点「开始投屏」（或本页点「手机投屏到电脑」）→ 手机把画面/内部声音推到本机 5423。\n"
            "画面与反向控制都在「手机屏幕」窗口里：左键点击/拖拽，长按=返回，右键=两点滑动，方向键微调坐标偏移。\n"
            "本页首次打开会自动启动 5423 服务；也可用浏览器看 http://127.0.0.1:5423/live 。"
        )
        self.hint_label.setWordWrap(True)
        layout.addWidget(self.hint_label)
        layout.addStretch()

    # ==================== 信号 ====================

    def _connect_signals(self):
        self.btn_svc_start.clicked.connect(lambda: self._ensure_service())
        self.btn_svc_stop.clicked.connect(self._stop_service)
        self.btn_live_window.clicked.connect(self._open_live_window)
        self.btn_live_web.clicked.connect(self._open_live_web)
        self.phone_to_pc_btn.clicked.connect(self._toggle_phone_mirror)
        self.rec_btn.clicked.connect(self._toggle_record)
        self.btn_open_output.clicked.connect(self._open_output_dir)

        self.pc_stream_btn.clicked.connect(self._toggle_pc_stream)
        self.audio_btn.clicked.connect(self._toggle_audio)

        self.vol_slider.valueChanged.connect(lambda v: self.vol_value_label.setText(str(v)))
        self.vol_slider.sliderPressed.connect(self._on_vol_pressed)
        self.vol_slider.valueChanged.connect(self._on_vol_changed)
        self.vol_slider.sliderReleased.connect(self._on_vol_released)
        self.vol_mute_btn.clicked.connect(self._toggle_phone_mute)
        self.btn_lock.clicked.connect(lambda: self.manager.send_command("lock"))
        self.btn_back.clicked.connect(lambda: self.manager.send_command("back"))
        self.btn_home.clicked.connect(lambda: self.manager.send_command("home"))
        self.btn_recents.clicked.connect(lambda: self.manager.send_command("recents"))
        self.btn_notif_panel.clicked.connect(lambda: self.manager.send_command("open_notifications_panel"))
        self.btn_control_center.clicked.connect(lambda: self.manager.send_command("control_center"))
        self.btn_phone_screenshot.clicked.connect(self._phone_screenshot)

        try:
            self.manager.connection_status_changed.connect(
                lambda c, ch: (self._update_button_states(), self._request_phone_volume()))
            self.manager.phone_volume_received.connect(self._on_phone_volume_changed)
            self.manager.phone_mute_received.connect(self._on_phone_mute_changed)
        except Exception:
            pass

        # 后台线程回调
        self._svc_result.connect(self._on_svc_result)
        self._svc_stop_result.connect(self._on_svc_stopped)
        self._status_result.connect(self._on_status)
        self._record_result.connect(self._on_record_result)

        self._request_phone_volume()

    def showEvent(self, event):
        super().showEvent(event)
        # 首次进页面把服务拉起来：手机上先开始投屏也不会因为服务没起而丢帧
        if not self._auto_started:
            self._auto_started = True
            self._ensure_service()

    def _update_button_states(self):
        try:
            ch = self.manager.current_channel
            self.channel_label.setText(f"当前通道: {ch}")
        except Exception:
            pass

    # ==================== 投屏服务（5423）====================

    @property
    def _port(self):
        return getattr(self._svc_module, "PORT", DEFAULT_MIRROR_PORT)

    def _ensure_service(self):
        """启动 5423 服务。延迟 import mirror_server：cv2 只在真要用时才加载。"""
        if self._svc_ok or self._svc_starting:
            return
        self._svc_starting = True
        self.svc_state_label.setText("服务状态：启动中…")

        def work():
            try:
                if self._svc_module is None:
                    import mirror_server
                    self._svc_module = mirror_server
                ok = bool(self._svc_module.start_server_thread())
                msg = f"端口 {self._svc_module.PORT}"
            except Exception as e:
                ok, msg = False, f"{type(e).__name__}: {e}"
            self._svc_result.emit(ok, msg)

        threading.Thread(target=work, daemon=True, name="mirror-svc-start").start()

    def _on_svc_result(self, ok, msg):
        self._svc_starting = False
        self._svc_ok = bool(ok)
        if ok:
            out_dir = getattr(self._svc_module, "OUTPUT_DIR", "") if self._svc_module else ""
            self.svc_state_label.setText(f"服务状态：运行中 · {msg} · 连接地址 0.0.0.0:{self._port}")
            self.rec_status_label.setText(f"录制：未开始（输出目录 {out_dir}）")
        else:
            self.svc_state_label.setText(f"服务状态：启动失败 — {msg}")
            InfoBar.error("投屏服务启动失败",
                          f"{msg}\n若 5423 被占用，可在启动前设置环境变量 PHONEHUB_MIRROR_PORT 换端口。",
                          parent=self, duration=6000, position=InfoBarPosition.TOP)

    def _stop_service(self):
        if self._svc_module is None:
            self._svc_ok = False
            self.svc_state_label.setText("服务状态：未启动")
            return

        def work():
            try:
                self._svc_module.stop_server()
                msg = "已停止"
            except Exception as e:
                msg = f"停止出错: {e}"
            self._svc_stop_result.emit(msg)

        self.svc_state_label.setText("服务状态：停止中…")
        threading.Thread(target=work, daemon=True, name="mirror-svc-stop").start()

    def _on_svc_stopped(self, msg):
        self._svc_ok = False
        self._recording = False
        self.rec_btn.setText("● 开始录制")
        self.svc_state_label.setText(f"服务状态：未启动（{msg}）")
        self.svc_stat_label.setText("等待画面…")

    def _open_live_window(self):
        """打开参考工程那个「手机屏幕」窗口（tkinter，独立进程）"""
        try:
            if self._svc_module is None:
                InfoBar.warning("服务未启动", "先点「启动投屏服务」，再打开窗口。",
                                parent=self, duration=3000, position=InfoBarPosition.TOP)
                return
            self._svc_module.open_live_window()
            self._live_window_opened = True
        except Exception as e:
            dark_msg_box(self, QMessageBox.Warning, "打开失败", f"打开手机屏幕窗口出错: {e}")

    def _open_live_web(self):
        try:
            webbrowser.open(f"http://127.0.0.1:{self._port}/live")
        except Exception as e:
            dark_msg_box(self, QMessageBox.Warning, "打开失败", f"打开浏览器失败: {e}")

    def _open_output_dir(self):
        out_dir = getattr(self._svc_module, "OUTPUT_DIR", "")
        if not out_dir:
            out_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "output")
        out_dir = os.path.abspath(out_dir)
        try:
            os.makedirs(out_dir, exist_ok=True)
            os.startfile(out_dir)                     # Windows
        except Exception as e:
            dark_msg_box(self, QMessageBox.Information, "录制目录", f"{out_dir}\n\n（打开失败: {e}）")

    def _poll_status(self):
        """每秒拉一次 /status；服务没在跑就直接标未运行。"""
        if self._status_busy:
            return
        if not self._svc_ok:
            self._on_status({})
            return
        if requests is None:
            return
        self._status_busy = True
        port = self._port

        def work():
            data = {}
            try:
                r = requests.get(f"http://127.0.0.1:{port}/status", timeout=1.5)
                if r.status_code == 200:
                    data = r.json() or {}
            except Exception:
                data = {}
            self._status_result.emit(data)

        threading.Thread(target=work, daemon=True, name="mirror-status").start()

    def _on_status(self, d):
        self._status_busy = False
        try:
            if not d:
                if not self._svc_ok:
                    self.svc_state_label.setText("服务状态：未启动（首次进本页会自动启动）")
                else:
                    self.svc_state_label.setText(f"服务状态：已启动但无响应（端口 {self._port}）")
                self.svc_stat_label.setText("等待画面…")
                return

            client = d.get("active_client") or "无"
            live = d.get("live") or {}
            audio = d.get("audio") or {}
            self.svc_state_label.setText(
                f"服务状态：运行中 · 端口 {self._port} · 投屏手机 {client}")
            self.svc_stat_label.setText(
                f"收到帧 {d.get('received', 0)} · 写入 {d.get('written', 0)} · "
                f"输出 {d.get('out_fps', 0)}fps（实测输入 {d.get('measured_fps') or '-'}） · "
                f"画面 {live.get('w', 0)}x{live.get('h', 0)}（观看端 {live.get('clients', 0)}） · "
                f"音频 {'录' if audio.get('recording') else '待机'}"
                f"{'/' + str(audio.get('rate')) + 'Hz' if audio.get('rate') else ''}")

            # 录制态与窗口联动
            rec = bool(d.get("recording"))
            self._recording = rec
            self.rec_btn.setText("■ 停止录制" if rec else "● 开始录制")
            if rec:
                self.rec_status_label.setText(
                    f"录制：进行中 #{d.get('recording_id') or ''} · {d.get('elapsed', 0)}s / "
                    f"{d.get('written', 0)} 帧")
            else:
                files = d.get("last_files") or []
                if files:
                    f0 = files[0]
                    self.rec_status_label.setText(
                        f"录制：已停止 → {f0.get('filename', '?')} "
                        f"（{f0.get('duration', 0)}s / {f0.get('frames', 0)} 帧 / {f0.get('fps', 0)}fps）")
                else:
                    self.rec_status_label.setText("录制：未开始（默认不录制）")

            # 手机开始推流后，自动把「手机屏幕」窗口弹出来（用户关掉后不再自动弹）
            if client != "无" and not self._live_window_opened:
                self._live_window_opened = True
                QTimer.singleShot(200, self._open_live_window)

            # 手机端停了投屏（连续 3 秒没有客户端）→ 「手机投屏到电脑」按钮状态回归
            if client == "无" and self._mirror_on:
                self._no_client_polls = getattr(self, "_no_client_polls", 0) + 1
                if self._no_client_polls >= 3:
                    self._no_client_polls = 0
                    self._mirror_on = False
                    self.phone_to_pc_btn.setText("手机投屏到电脑")
            else:
                self._no_client_polls = 0
        except Exception:
            pass

    # ==================== 投屏启停（电脑端发起）====================

    def _toggle_phone_mirror(self):
        """手机投屏到电脑：让手机端启动参考工程那套 MirrorService（推到本机 5423）"""
        try:
            if self._mirror_on:
                self.manager.send_action("mirror_stop")
                self._mirror_on = False
                self.phone_to_pc_btn.setText("手机投屏到电脑")
                return

            # 先把服务拉起来，否则手机开始推帧时没人收
            self._ensure_service()

            extra = {"mirror_port": self._port}
            ip = None
            try:
                self.manager._get_local_ip()
                ip = getattr(self.manager, "local_ip", None)
            except Exception:
                ip = None
            if ip and ip != "127.0.0.1":
                extra["mirror_ip"] = ip

            self.manager.send_action("mirror_start", extra)
            self._mirror_on = True
            self.phone_to_pc_btn.setText("停止手机投屏")
            InfoBar.info("已通知手机开始投屏",
                         f"目标 {extra.get('mirror_ip', '（手机端已配置的地址）')}:{self._port}"
                         "；首次会弹一次屏幕录制授权。",
                         parent=self, duration=4000, position=InfoBarPosition.TOP)
        except Exception as e:
            dark_msg_box(self, QMessageBox.Warning, "操作失败", f"手机投屏操作出错: {e}")

    def _toggle_record(self):
        """录制开关：等价于「手机屏幕」窗口上的录制键（都打 5423 的 /record/*）"""
        if requests is None:
            dark_msg_box(self, QMessageBox.Warning, "缺少依赖", "当前环境没有 requests，无法控制录制。")
            return
        if not self._svc_ok:
            InfoBar.warning("服务未启动", "先点「启动投屏服务」。",
                            parent=self, duration=3000, position=InfoBarPosition.TOP)
            return
        want = not self._recording
        url = f"http://127.0.0.1:{self._port}/" + ("record/start" if want else "record/stop")

        def work():
            try:
                r = requests.post(url, timeout=3)
                j = r.json() if r.status_code == 200 else {}
                ok = bool(j.get("ok"))
                msg = "开始录制" if want else "停止录制"
                if not ok:
                    msg += f"（服务返回 {r.status_code}）"
            except Exception as e:
                ok, msg = False, f"{type(e).__name__}: {e}"
            self._record_result.emit(ok, msg)

        threading.Thread(target=work, daemon=True, name="mirror-record").start()

    def _on_record_result(self, ok, msg):
        self.rec_status_label.setText(f"录制：{msg}" if ok else f"录制控制失败：{msg}")
        if not ok:
            InfoBar.warning("录制控制失败", msg, parent=self,
                            duration=4000, position=InfoBarPosition.TOP)

    # ==================== 手机遥控（原有能力，走 58627 通道）====================

    def _request_phone_volume(self):
        """向手机请求当前媒体音量，作为滑块初始值。"""
        try:
            self.manager.send_command("get_volume")
        except Exception:
            pass

    def _on_phone_volume_changed(self, volume):
        """手机端媒体音量变化同步到滑块（电脑拖动期间不覆盖用户操作，且不在静音状态下覆盖）"""
        # 如果手机处于静音状态，收到非零音量时先取消静音
        if volume > 0 and getattr(self, '_is_phone_muted', False):
            self._is_phone_muted = False
            self.mute_indicator.setText("未静音")
            self.vol_mute_btn.setText("静音")

        if not getattr(self, '_vol_sync_enabled', True):
            return
        try:
            clamped = int(max(0, min(15, volume)))
            # 只在当前不是静音操作时更新滑块（避免用户手动调节后被手机覆盖）
            if not getattr(self, '_vol_during_drag', False):
                # Set flag to ignore valueChanged signal from this programmatic update
                self._ignore_volume_update = True
                self.vol_slider.setValue(clamped)
                self.vol_value_label.setText(str(clamped))
                self._ignore_volume_update = False
        except Exception:
            # Ensure flag is cleared even on error
            self._ignore_volume_update = False
            pass

    def _on_vol_pressed(self):
        """首次触碰滑块时立即发送当前值并短暂关闭来自手机的同步，避免回弹。"""
        self._vol_sync_enabled = False
        self._vol_during_drag = True
        value = self.vol_slider.value()
        self.manager.send_command("set_volume", extra={"volume": value})
        self._last_vol_send = time.time()
        QTimer.singleShot(300, lambda: setattr(self, '_vol_sync_enabled', True))
        QTimer.singleShot(100, lambda: setattr(self, '_vol_during_drag', False))

    def _on_vol_changed(self, value):
        """拖动中 50ms 节流发送"""
        # Skip if this update is from phone volume sync (avoid oscillation loop)
        if getattr(self, '_ignore_volume_update', False):
            return
        now = time.time()
        if now - getattr(self, '_last_vol_send', 0) >= 0.05:
            self.manager.send_command("set_volume", extra={"volume": value})
            self._last_vol_send = now

    def _on_vol_released(self):
        """滑块释放时发送最终值兜底"""
        value = self.vol_slider.value()
        self.manager.send_command("set_volume", extra={"volume": value})
        self._last_vol_send = time.time()

    def _on_phone_mute_changed(self, muted: bool):
        """手机静音状态变化，更新UI指示器"""
        try:
            self._is_phone_muted = muted
            if muted:
                self.mute_indicator.setText("🔇 静音")
                self.vol_mute_btn.setText("取消静音")
            else:
                self.mute_indicator.setText("未静音")
                self.vol_mute_btn.setText("静音")
        except Exception:
            pass

    def _toggle_phone_mute(self):
        """切换手机静音状态"""
        try:
            if getattr(self, '_is_phone_muted', False):
                current_vol = self.vol_slider.value() if self.vol_slider.value() > 0 else 7
                self.manager.send_command("vol_mute")
                QTimer.singleShot(100, lambda: self.manager.send_command(
                    "set_volume", extra={"volume": current_vol}))
                self._is_phone_muted = False
            else:
                self.manager.send_command("vol_mute")
                self._is_phone_muted = True
                self.vol_slider.setValue(0)
                self.vol_value_label.setText("0")
        except Exception:
            pass

    def _phone_screenshot(self):
        """手机截图：ADB模式直接截图，WiFi模式发送截图请求"""
        # 截图目录跟随接收目录（自动创建，具备不可用回退），避免硬编码盘符
        screenshot_dir = os.path.join(self.manager.receive_dir, "screenshots")
        try:
            os.makedirs(screenshot_dir, exist_ok=True)
        except Exception as e:
            dark_msg_box(self, QMessageBox.Warning, "创建目录失败",
                         f"无法创建截图保存目录:\n{screenshot_dir}\n错误: {e}")
            return

        if self.manager.adb_device_id:
            timestamp = time.strftime("%Y%m%d_%H%M%S")
            local_path = os.path.join(screenshot_dir, f"phone_{timestamp}.png")
            try:
                self.manager.adb_screenshot(local_path)
            except Exception as e:
                dark_msg_box(self, QMessageBox.Warning, "截图失败", f"ADB 截图失败: {e}")
                return
            if not os.path.exists(local_path) or os.path.getsize(local_path) == 0:
                dark_msg_box(self, QMessageBox.Warning, "截图失败", "截图文件未生成，请检查 ADB 连接。")
                return
            try:
                self.manager.send_action("screenshot_saved",
                                         extra={"message": "截图已保存到电脑", "path": local_path})
            except Exception:
                pass
            dark_msg_box(self, QMessageBox.Information, "截图成功", f"截图已保存到:\n{local_path}")
        else:
            try:
                self.manager.send_action("screenshot_request")
                dark_msg_box(self, QMessageBox.Information, "已发送截图请求",
                             "已请求手机截图，截图完成后将自动回传至电脑。")
            except Exception as e:
                dark_msg_box(self, QMessageBox.Warning, "请求失败", f"发送截图请求失败: {e}")

    # ==================== 电脑 → 手机 ====================

    def _toggle_pc_stream(self):
        """电脑画面推流到手机 启停切换

        电脑端只负责把帧缓存到 _latest_frame；必须发 pc_stream_start 通知手机端
        启动轮询拉取（startPcFramePolling），否则手机端永远不会来拉 → 看似"没效果"。
        """
        try:
            if self.manager._pc_stream_running:
                self.manager.stop_pc_stream()
                self.manager.send_action("pc_stream_stop")
                self.pc_stream_btn.setText("推流电脑画面到手机")
            else:
                self.manager.start_pc_stream()
                self.manager.send_action("pc_stream_start")
                self.pc_stream_btn.setText("停止推流")
        except Exception as e:
            dark_msg_box(self, QMessageBox.Warning, "操作失败", f"推流操作出错: {e}")

    def _toggle_audio(self):
        """电脑声音传到手机 启停切换"""
        try:
            if self.manager._pc_audio_running:
                self.manager.stop_pc_audio()
                self.manager.send_action("audio_stop")
                self.audio_btn.setText("开始声音传输")
            else:
                self.manager.start_pc_audio()
                self.manager.send_action("audio_start")
                self.audio_btn.setText("停止声音传输")
                # 启动后稍等，检查采集是否真的在跑、用的是哪种音源，给用户明确反馈
                QTimer.singleShot(1500, self._check_audio_source)
        except RuntimeError as e:
            dark_msg_box(self, QMessageBox.Warning, "音频启动失败",
                         f"{str(e)}\n\n请检查:\n1. 是否已安装pyaudio (pip install pyaudio)\n"
                         "2. 音频设备是否被其他程序占用\n3. 是否需要以管理员身份运行")
        except Exception as e:
            dark_msg_box(self, QMessageBox.Warning, "操作失败", f"声音传输操作出错: {e}")

    def _check_audio_source(self):
        """声音传输启动后检查采集状态，反馈回环/麦克风/失败，便于排查无声"""
        try:
            if not self.manager._pc_audio_running:
                dark_msg_box(self, QMessageBox.Warning, "声音传输未启动",
                             "电脑端音频采集未能开始（可能 pyaudio 未安装、设备被占用或无输入设备）。\n"
                             "请查看电脑端日志。")
                self.audio_btn.setText("开始声音传输")
                return
            src = getattr(self.manager, "_pc_audio_source", "unknown")
            if src == "mic":
                dark_msg_box(self, QMessageBox.Warning, "声音传输：当前抓的是麦克风",
                             "未找到系统回环(loopback)设备，现在捕获的是麦克风输入。\n"
                             "想传电脑正在播放的声音，请开启 Windows『立体声混音』，或安装虚拟音频线(VB-Cable)。")
            elif src == "none":
                dark_msg_box(self, QMessageBox.Warning, "声音传输失败",
                             "电脑端没有任何可用音频输入设备。")
                self.audio_btn.setText("开始声音传输")
        except Exception:
            pass
