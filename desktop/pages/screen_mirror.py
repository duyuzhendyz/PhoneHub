import os
import time
import threading
from concurrent.futures import ThreadPoolExecutor
from PyQt5.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout,
                               QFrame, QMessageBox, QSizePolicy, QApplication,
                               QSlider, QGridLayout, QSpacerItem)
from PyQt5.QtCore import Qt, pyqtSignal, QTimer
from PyQt5.QtGui import QImage, QMouseEvent, QPainter, QPixmap, QFont
from styles import get_theme, _c, apply_dark_title_bar, dark_msg_box
from qfluentwidgets import (CardWidget, TitleLabel, BodyLabel, SubtitleLabel,
                            PushButton, PrimaryPushButton, ToolButton, ToggleButton,
                            CheckBox, ComboBox, setFont, FluentIcon as FIF,
                            InfoBar, InfoBarPosition)


class MirrorCanvas(QFrame):
    """投屏画布：显示手机画面帧，支持点击/拖拽/长按操控（操控始终开启）"""

    def __init__(self, parent=None):
        super().__init__(parent)
        self._pixmap = None
        self._reused_pix = QPixmap()      # 复用对象，避免每帧创建销毁 QPixmap
        self._cached_scaled = None        # 缓存缩放后的 pixmap
        self._cached_size = None          # 缓存对应的画布尺寸
        self._click_handler = None
        self._move_handler = None
        self._long_click_handler = None
        self._press_start = 0
        self._press_pos = None
        self._is_dragging = False
        self.setMinimumSize(480, 360)
        self.setMouseTracking(True)
        self.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        c = _c()
        self.setStyleSheet(f"background-color: #000; border: 1px solid {c['border']};")

    def set_handlers(self, click_handler, move_handler, long_click_handler):
        self._click_handler = click_handler
        self._move_handler = move_handler
        self._long_click_handler = long_click_handler

    def clear(self):
        """清空画布，显示等待文字"""
        self._pixmap = None
        self._cached_scaled = None
        self.update()

    def load_frame(self, frame_data):
        """直接解码 JPEG 数据到复用的 QPixmap"""
        if self._reused_pix.loadFromData(frame_data, 'JPEG') and not self._reused_pix.isNull():
            self._pixmap = self._reused_pix
            self._cached_scaled = None
            self.update()

    def _norm_coords(self, pos):
        """将画布坐标转换为归一化坐标 (0-1)"""
        if not self._pixmap:
            return None, None
        if self._cached_scaled is None or self._cached_size != self.size():
            self._cached_scaled = self._pixmap.scaled(
                self.size(), Qt.KeepAspectRatio, Qt.SmoothTransformation)
            self._cached_size = self.size()
        scaled = self._cached_scaled
        x_off = (self.width() - scaled.width()) // 2
        y_off = (self.height() - scaled.height()) // 2
        x = pos.x() - x_off
        y = pos.y() - y_off
        if x < 0 or y < 0 or x > scaled.width() or y > scaled.height():
            return None, None
        return x / scaled.width(), y / scaled.height()

    def paintEvent(self, event):
        painter = QPainter(self)
        if self._pixmap:
            if self._cached_scaled is None or self._cached_size != self.size():
                self._cached_scaled = self._pixmap.scaled(
                    self.size(), Qt.KeepAspectRatio, Qt.SmoothTransformation)
                self._cached_size = self.size()
            scaled = self._cached_scaled
            x = (self.width() - scaled.width()) // 2
            y = (self.height() - scaled.height()) // 2
            painter.drawPixmap(x, y, scaled)
        else:
            painter.setPen(Qt.white)
            painter.drawText(self.rect(), Qt.AlignCenter, "等待手机投屏画面...")
        painter.end()

    def mousePressEvent(self, event):
        try:
            if event.button() == Qt.LeftButton:
                self._press_start = time.time()
                self._press_pos = event.position()
                self._is_dragging = False
                nx, ny = self._norm_coords(event.position())
                if nx is not None and self._click_handler:
                    self._click_handler(nx, ny, "down")
        except Exception as e:
            print(f"mousePressEvent error: {e}")

    def mouseMoveEvent(self, event):
        try:
            if self._press_pos is not None and event.buttons() & Qt.LeftButton:
                dx = abs(event.position().x() - self._press_pos.x())
                dy = abs(event.position().y() - self._press_pos.y())
                if dx > 5 or dy > 5:
                    self._is_dragging = True
                if self._is_dragging:
                    nx, ny = self._norm_coords(event.position())
                    if nx is not None and self._move_handler:
                        self._move_handler(nx, ny)
        except Exception as e:
            print(f"mouseMoveEvent error: {e}")

    def mouseReleaseEvent(self, event):
        try:
            if event.button() != Qt.LeftButton:
                return
            if self._press_pos is None:
                return
            nx, ny = self._norm_coords(event.position())
            duration = time.time() - self._press_start
            if nx is not None:
                if duration >= 1.0 and not self._is_dragging:
                    # 长按 = 返回键
                    if self._long_click_handler:
                        self._long_click_handler(nx, ny)
                elif self._is_dragging:
                    # 拖拽结束
                    if self._click_handler:
                        self._click_handler(nx, ny, "up")
                else:
                    # 单击
                    if self._click_handler:
                        self._click_handler(nx, ny, "click")
            self._press_pos = None
            self._is_dragging = False
        except Exception as e:
            print(f"mouseReleaseEvent error: {e}")
            self._press_pos = None
            self._is_dragging = False


class MirrorWindow(QWidget):
    """独立投屏查看窗口：显示手机画面帧 + 支持远程控制，60fps 显示"""

    def __init__(self, manager, parent=None):
        super().__init__(parent, Qt.Window)
        self.manager = manager
        self._latest_frame = None
        self._last_frame_time = 0  # 上次收到帧的时间
        self._touch_pool = ThreadPoolExecutor(max_workers=2, thread_name_prefix="touch")
        self._setup_ui()
        # 帧显示定时器：60fps 解码最新帧
        self._display_timer = QTimer(self)
        self._display_timer.setInterval(16)
        self._display_timer.timeout.connect(self._flush_frame)
        self.resize(540, 960)  # 竖屏窗口默认尺寸

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)
        self.canvas = MirrorCanvas()
        self.canvas.set_handlers(self._on_click, self._on_move, self._on_long_click)
        layout.addWidget(self.canvas, 1)

    def showEvent(self, event):
        super().showEvent(event)
        try:
            apply_dark_title_bar(self)
        except Exception:
            pass
        try:
            self.manager.phone_frame_received.connect(self._on_frame_received)
        except Exception:
            pass
        self._display_timer.start()

    def closeEvent(self, event):
        # 关闭窗口时通知手机停止投屏采集
        try:
            self.manager.stop_phone_mirror()
            self.manager.send_action("mirror_stop")
        except Exception:
            pass
        try:
            self.manager.phone_frame_received.disconnect(self._on_frame_received)
        except Exception:
            pass
        self._display_timer.stop()
        self._touch_pool.shutdown(wait=False)
        self._latest_frame = None
        self.canvas.clear()
        super().closeEvent(event)

    def _on_frame_received(self, frame_data):
        """帧到达：仅缓存最新数据，由定时器统一解码（跳帧防卡顿）"""
        self._latest_frame = frame_data
        self._last_frame_time = time.time()

    def _flush_frame(self):
        """定时解码最新帧并刷新画布，超时2秒无帧则清空画面"""
        now = time.time()
        if not self._latest_frame:
            # 超过2秒没收到帧，清空画面
            if self._last_frame_time > 0 and now - self._last_frame_time > 2.0:
                self._last_frame_time = 0
                self.canvas.clear()
            return
        data = self._latest_frame
        self._latest_frame = None
        self.canvas.load_frame(data)

    # ==================== 远程控制（通过线程池执行，避免阻塞UI）====================

    def _on_click(self, norm_x, norm_y, op="click"):
        """点击/触摸操作 - 线程池执行"""
        self._touch_pool.submit(self._safe_touch, norm_x, norm_y, op)

    def _on_move(self, norm_x, norm_y):
        """拖拽移动 - 线程池执行"""
        self._touch_pool.submit(self._safe_touch, norm_x, norm_y, "move")

    def _on_long_click(self, norm_x, norm_y):
        """长按（=返回键）- 线程池执行"""
        self._touch_pool.submit(self._safe_touch, norm_x, norm_y, "right")

    def _safe_touch(self, norm_x, norm_y, op):
        """安全执行触摸操作（后台线程，捕获所有异常防止闪退）"""
        try:
            self.manager._perform_screen_touch(norm_x, norm_y, op)
        except Exception as e:
            print(f"Screen touch failed: {e}")


class ScreenMirrorPage(QWidget):
    """投屏与反向控制（自研实现，不使用 scrcpy/sndcpy）
    投屏画面在独立弹窗中显示，此页面仅提供控制按钮
    """

    def __init__(self, manager):
        super().__init__()
        self.manager = manager
        self._mirror_window = None
        self._setup_ui()
        self._connect_signals()
        self._update_button_states()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 16, 16, 16)
        layout.setSpacing(12)

        title = TitleLabel("投屏与反向控制")
        title.setObjectName("titleLabel")
        setFont(title, 28, QFont.Bold)
        layout.addWidget(title)

        self.channel_label = SubtitleLabel("当前通道: --")
        layout.addWidget(self.channel_label)

        ctrl_frame = CardWidget()
        ctrl_layout = QHBoxLayout(ctrl_frame)

        # 电脑画面推流到手机
        self.pc_stream_btn = PrimaryPushButton("推流电脑画面到手机")
        ctrl_layout.addWidget(self.pc_stream_btn)

        self.stop_btn = PushButton("停止推流")
        self.stop_btn.setEnabled(False)
        ctrl_layout.addWidget(self.stop_btn)

        # 手机投屏到电脑
        self.phone_to_pc_btn = PrimaryPushButton("手机投屏到电脑")
        ctrl_layout.addWidget(self.phone_to_pc_btn)

        # 打开投屏查看窗口
        self.open_window_btn = PushButton("打开投屏窗口")
        ctrl_layout.addWidget(self.open_window_btn)

        # 声音传输
        self.audio_btn = PushButton("开始声音传输")
        ctrl_layout.addWidget(self.audio_btn)

        ctrl_layout.addStretch()
        layout.addWidget(ctrl_frame)

        # ==================== 快捷控制面板 ====================
        control_frame = CardWidget()
        control_layout = QVBoxLayout(control_frame)
        control_layout.setSpacing(8)

        ctrl_title = SubtitleLabel("快捷控制")
        control_layout.addWidget(ctrl_title)

        # 第一行：音量控制
        vol_row = QHBoxLayout()
        vol_label = BodyLabel("音量")
        vol_row.addWidget(vol_label)
        self.vol_slider = QSlider(Qt.Horizontal)
        self.vol_slider.setRange(0, 15)
        self.vol_slider.setValue(7)
        self.vol_slider.setFixedWidth(200)
        vol_row.addWidget(self.vol_slider)
        self.vol_value_label = BodyLabel("7")
        self.vol_value_label.setFixedWidth(30)
        vol_row.addWidget(self.vol_value_label)
        self.vol_mute_btn = PushButton("静音")
        vol_row.addWidget(self.vol_mute_btn)
        vol_row.addStretch()
        control_layout.addLayout(vol_row)

        # 第三行：快捷按钮
        btn_row = QHBoxLayout()
        self.btn_lock = PushButton("锁屏")
        btn_row.addWidget(self.btn_lock)
        self.btn_back = PushButton("返回")
        btn_row.addWidget(self.btn_back)
        self.btn_home = PushButton("主屏")
        btn_row.addWidget(self.btn_home)
        self.btn_recents = PushButton("最近任务")
        btn_row.addWidget(self.btn_recents)
        self.btn_notif_panel = PushButton("通知栏")
        btn_row.addWidget(self.btn_notif_panel)
        self.btn_control_center = PushButton("控制中心")
        btn_row.addWidget(self.btn_control_center)
        self.btn_screenshot = PushButton("截屏")
        btn_row.addWidget(self.btn_screenshot)
        self.btn_phone_screenshot = PushButton("手机截图")
        btn_row.addWidget(self.btn_phone_screenshot)
        btn_row.addStretch()
        control_layout.addLayout(btn_row)

        layout.addWidget(control_frame)

        # 提示文本
        self.hint_label = BodyLabel("手机投屏开始后，投屏画面将在独立窗口中显示。点击「打开投屏窗口」可手动打开。")
        self.hint_label.setWordWrap(True)
        layout.addWidget(self.hint_label)
        layout.addStretch()

    def _connect_signals(self):
        self.pc_stream_btn.clicked.connect(self._toggle_pc_stream)
        self.stop_btn.clicked.connect(self._stop_stream)
        self.phone_to_pc_btn.clicked.connect(self._toggle_phone_mirror)
        self.open_window_btn.clicked.connect(self._open_mirror_window)
        self.audio_btn.clicked.connect(self._toggle_audio)

        # 快捷控制面板信号
        # 音量：首次触碰立即发送，拖动中 50ms 节流，释放时兜底
        self.vol_slider.valueChanged.connect(lambda v: self.vol_value_label.setText(str(v)))
        self.vol_slider.sliderPressed.connect(self._on_vol_pressed)
        self.vol_slider.valueChanged.connect(self._on_vol_changed)
        self.vol_slider.sliderReleased.connect(self._on_vol_released)
        self.vol_mute_btn.clicked.connect(lambda: self.manager.send_command("vol_mute"))
        self.btn_lock.clicked.connect(lambda: self.manager.send_command("lock"))
        self.btn_back.clicked.connect(lambda: self.manager.send_command("back"))
        self.btn_home.clicked.connect(lambda: self.manager.send_command("home"))
        self.btn_recents.clicked.connect(lambda: self.manager.send_command("recents"))
        self.btn_notif_panel.clicked.connect(lambda: self.manager.send_command("open_notifications_panel"))
        self.btn_control_center.clicked.connect(lambda: self.manager.send_command("control_center"))
        self.btn_screenshot.clicked.connect(lambda: self.manager.send_command("screenshot"))
        self.btn_phone_screenshot.clicked.connect(self._phone_screenshot)

        try:
            self.manager.connection_status_changed.connect(lambda c, ch: self._update_button_states())
        except Exception:
            pass
        try:
            # 收到手机投屏帧时自动打开窗口
            self.manager.phone_frame_received.connect(self._on_phone_frame_received)
        except Exception:
            pass

    def _update_button_states(self):
        try:
            ch = self.manager.current_channel
            self.channel_label.setText(f"当前通道: {ch}")
        except Exception:
            pass

    def _on_vol_pressed(self):
        """首次触碰滑块时立即发送当前值"""
        value = self.vol_slider.value()
        self.manager.send_command("set_volume", extra={"volume": value})
        self._last_vol_send = time.time()

    def _on_vol_changed(self, value):
        """拖动中 50ms 节流发送"""
        now = time.time()
        if now - getattr(self, '_last_vol_send', 0) >= 0.05:
            self.manager.send_command("set_volume", extra={"volume": value})
            self._last_vol_send = now

    def _on_vol_released(self):
        """滑块释放时发送最终值兜底"""
        value = self.vol_slider.value()
        self.manager.send_command("set_volume", extra={"volume": value})
        self._last_vol_send = time.time()

    def _phone_screenshot(self):
        """手机截图：ADB模式直接截图，WiFi模式发送截图请求"""
        screenshot_dir = r"F:\desk\手机上传\截图"
        try:
            os.makedirs(screenshot_dir, exist_ok=True)
        except Exception as e:
            dark_msg_box(self, QMessageBox.Warning, "创建目录失败", f"无法创建截图保存目录:\n{screenshot_dir}\n错误: {e}")
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

    def _on_phone_frame_received(self, frame_data):
        """收到手机投屏帧时自动打开投屏窗口"""
        if self._mirror_window is None or not self._mirror_window.isVisible():
            self._open_mirror_window()

    def _open_mirror_window(self):
        """打开独立投屏查看窗口"""
        try:
            if self._mirror_window is None or not self._mirror_window.isVisible():
                self._mirror_window = MirrorWindow(self.manager, self)
            self._mirror_window.show()
            self._mirror_window.raise_()
            self._mirror_window.activateWindow()
        except Exception as e:
            dark_msg_box(self, QMessageBox.Warning, "操作失败", f"打开投屏窗口出错: {e}")

    # ==================== 按钮操作 ====================

    def _toggle_pc_stream(self):
        """电脑画面推流到手机 启停切换"""
        try:
            if self.manager._pc_stream_running:
                self.manager.stop_pc_stream()
                self.pc_stream_btn.setText("推流电脑画面到手机")
                self.stop_btn.setEnabled(False)
            else:
                self.manager.start_pc_stream()
                self.pc_stream_btn.setText("停止推流")
                self.stop_btn.setEnabled(True)
        except Exception as e:
            dark_msg_box(self, QMessageBox.Warning, "操作失败", f"推流操作出错: {e}")

    def _stop_stream(self):
        """停止电脑画面推流"""
        try:
            self.manager.stop_pc_stream()
        except Exception:
            pass
        self.pc_stream_btn.setText("推流电脑画面到手机")
        self.stop_btn.setEnabled(False)

    def _toggle_phone_mirror(self):
        """手机投屏到电脑：启停切换"""
        try:
            if self.manager._phone_mirror_running:
                self.manager.send_action("mirror_stop")
                self.manager.stop_phone_mirror()
                self.phone_to_pc_btn.setText("手机投屏到电脑")
            else:
                self.manager.send_action("mirror_start")
                self.phone_to_pc_btn.setText("停止手机投屏")
                # 自动打开投屏窗口
                self._open_mirror_window()
        except Exception as e:
            dark_msg_box(self, QMessageBox.Warning, "操作失败", f"手机投屏操作出错: {e}")

    def _toggle_audio(self):
        """声音传输 启停切换（电脑音频 → 手机）"""
        try:
            if self.manager._pc_audio_running:
                self.manager.stop_pc_audio()
                self.audio_btn.setText("开始声音传输")
            else:
                self.manager.start_pc_audio()
                self.audio_btn.setText("停止声音传输")
        except Exception as e:
            dark_msg_box(self, QMessageBox.Warning, "操作失败", f"声音传输操作出错: {e}")
