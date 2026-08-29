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
        # 通知页面本窗口被用户关闭（用于抑制后续在途帧重新拉起窗口）
        if self._closed_cb:
            try:
                self._closed_cb()
            except Exception:
                pass
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
        self._is_phone_muted = False  # 初始状态：未静音
        self._setup_ui()
        self._connect_signals()
        self._update_button_states()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 20, 24, 20)
        layout.setSpacing(16)

        title = TitleLabel("投屏与反向控制")
        title.setObjectName("titleLabel")
        setFont(title, 28, QFont.Bold)
        layout.addWidget(title)

        self.channel_label = SubtitleLabel("当前通道: --")
        layout.addWidget(self.channel_label)

        ctrl_frame = CardWidget()
        ctrl_layout = QHBoxLayout(ctrl_frame)
        ctrl_layout.setContentsMargins(16, 12, 16, 12)
        ctrl_layout.setSpacing(12)

        # 电脑画面推流到手机
        self.pc_stream_btn = PrimaryPushButton("推流电脑画面到手机")
        ctrl_layout.addWidget(self.pc_stream_btn)

        # 手机投屏到电脑
        self.phone_to_pc_btn = PrimaryPushButton("手机投屏到电脑")
        ctrl_layout.addWidget(self.phone_to_pc_btn)



        # 声音传输
        self.audio_btn = PushButton("开始声音传输")
        ctrl_layout.addWidget(self.audio_btn)

        ctrl_layout.addStretch()
        layout.addWidget(ctrl_frame)

        # ==================== 快捷控制面板 ====================
        control_frame = CardWidget()
        control_layout = QVBoxLayout(control_frame)
        control_layout.setContentsMargins(16, 12, 16, 12)
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
        self._vol_sync_enabled = True
        vol_row.addWidget(self.vol_slider)
        self.vol_value_label = BodyLabel("7")
        self.vol_value_label.setFixedWidth(30)
        vol_row.addWidget(self.vol_value_label)
        self.vol_mute_btn = PushButton("静音")
        vol_row.addWidget(self.vol_mute_btn)
        # 添加静音状态指示器
        self.mute_indicator = BodyLabel("未静音")
        self.mute_indicator.setObjectName("muteIndicator")
        self.mute_indicator.setStyleSheet("color: #FF6B6B; font-weight: bold;")
        vol_row.addWidget(self.mute_indicator)
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
        self.btn_phone_screenshot = PushButton("手机截图")
        btn_row.addWidget(self.btn_phone_screenshot)
        btn_row.addStretch()
        control_layout.addLayout(btn_row)

        layout.addWidget(control_frame)

        # 提示文本
        self.hint_label = BodyLabel("手机投屏开始后，投屏画面将在独立窗口中自动显示。")
        self.hint_label.setWordWrap(True)
        layout.addWidget(self.hint_label)
        layout.addStretch()

    def _connect_signals(self):
        self.pc_stream_btn.clicked.connect(self._toggle_pc_stream)
        self.phone_to_pc_btn.clicked.connect(self._toggle_phone_mirror)
        self.audio_btn.clicked.connect(self._toggle_audio)

        # 快捷控制面板信号
        # 音量：首次触碰立即发送，拖动中 50ms 节流，释放时兜底
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
            self.manager.connection_status_changed.connect(lambda c, ch: (self._update_button_states(), self._request_phone_volume()))
            self.manager.phone_volume_received.connect(self._on_phone_volume_changed)
            self.manager.phone_mute_received.connect(self._on_phone_mute_changed)
        except Exception:
            pass
        try:
            # 收到手机投屏帧时自动打开窗口
            self.manager.phone_frame_received.connect(self._on_phone_frame_received)
        except Exception:
            pass
        # 请求初始音量
        self._request_phone_volume()

    def _update_button_states(self):
        try:
            ch = self.manager.current_channel
            self.channel_label.setText(f"当前通道: {ch}")
        except Exception:
            pass

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
                # 音量滑块置为0但显示特殊状态
            else:
                self.mute_indicator.setText("未静音")
                self.vol_mute_btn.setText("静音")
        except Exception:
            pass

    def _toggle_phone_mute(self):
        """切换手机静音状态"""
        try:
            # 如果当前处于静音状态，先取消静音再发送；否则直接发送静音命令
            if getattr(self, '_is_phone_muted', False):
                # 先恢复音量（如果之前有记录的音量）
                current_vol = self.vol_slider.value() if self.vol_slider.value() > 0 else 7
                self.manager.send_command("vol_mute")
                # 稍后恢复音量（需要两次操作：先取消静音，然后恢复原音量）
                QTimer.singleShot(100, lambda: self.manager.send_command("set_volume", extra={"volume": current_vol}))
                self._is_phone_muted = False
            else:
                self.manager.send_command("vol_mute")
                self._is_phone_muted = True
                # 立即将本地滑块置为0以反映静音状态
                self.vol_slider.setValue(0)
                self.vol_value_label.setText("0")
        except Exception as e:
            pass

    def _phone_screenshot(self):
        """手机截图：ADB模式直接截图，WiFi模式发送截图请求"""
        # 截图目录跟随接收目录（自动创建，具备不可用回退），避免硬编码盘符
        screenshot_dir = os.path.join(self.manager.receive_dir, "screenshots")
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
        # 用户手动关闭窗口后 3 秒内不再自动拉起（手机端停止采集有延迟，在途帧会触发重建）
        if time.time() - getattr(self, '_mirror_closed_at', 0) < 3.0:
            return
        if self._mirror_window is None or not self._mirror_window.isVisible():
            self._open_mirror_window()

    def _on_mirror_closed(self):
        """投屏窗口被用户关闭：记录时间用于抑制在途帧重新拉起"""
        self._mirror_closed_at = time.time()

    def _open_mirror_window(self):
        """打开独立投屏查看窗口"""
        try:
            if self._mirror_window is None or not self._mirror_window.isVisible():
                self._mirror_window = MirrorWindow(self.manager, closed_cb=self._on_mirror_closed)
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
            else:
                self.manager.start_pc_stream()
                self.pc_stream_btn.setText("停止推流")
        except Exception as e:
            dark_msg_box(self, QMessageBox.Warning, "操作失败", f"推流操作出错: {e}")

    def _toggle_phone_mirror(self):
        """手机投屏到电脑：自动发起权限请求→自动开始投屏→自动开窗口（S5）"""
        try:
            if self.manager._phone_mirror_running:
                # 停止投屏
                self.manager.send_action("mirror_stop")
                self.manager.stop_phone_mirror()
                self.phone_to_pc_btn.setText("手机投屏到电脑")
            else:
                # S5：直接发起投屏请求，手机端会自动处理权限并启动
                # 手机端收到 mirror_start 后会进入权限授予界面（如已授权则直接开始）
                self.manager.send_action("mirror_start")
                self.phone_to_pc_btn.setText("停止手机投屏")
                # 自动打开投屏窗口（无论是否已授权，先打开，有帧再显示）
                self._open_mirror_window()
        except Exception as e:
            dark_msg_box(self, QMessageBox.Warning, "操作失败", f"手机投屏操作出错: {e}")

    def _toggle_audio(self):
        """声音传输 启停切换（双向：手机→电脑上传播放 + 电脑音频→手机播放）"""
        try:
            if self.manager._pc_audio_running:
                self.manager.stop_pc_audio()
                self.manager.send_action("audio_stop")
                self.audio_btn.setText("开始声音传输")
            else:
                self.manager.start_pc_audio()
                self.manager.send_action("audio_start")
                self.audio_btn.setText("停止声音传输")
        except RuntimeError as e:
            # 显示具体的音频错误信息
            dark_msg_box(self, QMessageBox.Warning, "音频启动失败", f"{str(e)}\n\n请检查:\n1. 是否已安装pyaudio (pip install pyaudio)\n2. 音频设备是否被其他程序占用\n3. 是否需要以管理员身份运行")
        except Exception as e:
            dark_msg_box(self, QMessageBox.Warning, "操作失败", f"声音传输操作出错: {e}")

