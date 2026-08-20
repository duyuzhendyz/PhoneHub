import time
import threading
from PyQt5.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout,
                               QFrame, QLabel, QMessageBox, QSizePolicy, QFileDialog)
from PyQt5.QtCore import Qt, QTimer
from PyQt5.QtGui import QPainter, QPixmap, QImage, QFont
from styles import get_theme, _c, apply_dark_title_bar, dark_dialog_style, dark_msg_box
from qfluentwidgets import (CardWidget, TitleLabel, BodyLabel, SubtitleLabel,
                            PushButton, PrimaryPushButton, ComboBox, setFont,
                            FluentIcon as FIF, InfoBar, InfoBarPosition)


class CameraCanvas(QFrame):
    """摄像头画面画布：保持宽高比，尽可能放大填充区域"""

    def __init__(self, parent=None):
        super().__init__(parent)
        self._pixmap = None
        self._reused_pix = QPixmap()
        self._cached_scaled = None
        self._cached_size = None
        self.setMinimumSize(320, 240)
        self.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        c = _c()
        self.setStyleSheet(f"background-color: #000; border: 1px solid {c['border']};")

    def load_frame(self, frame_data):
        """直接解码 JPEG 数据到复用的 QPixmap"""
        if self._reused_pix.loadFromData(frame_data, 'JPEG') and not self._reused_pix.isNull():
            self._pixmap = self._reused_pix
            self._cached_scaled = None
            self.update()

    def set_pixmap(self, pix):
        self._pixmap = pix
        self._cached_scaled = None
        self.update()

    def clear(self):
        self._pixmap = None
        self._cached_scaled = None
        self.update()

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
            painter.drawText(self.rect(), Qt.AlignCenter, "等待摄像头画面...")
        painter.end()


class CameraWindow(QWidget):
    """独立摄像头查看窗口：等比缩放，最高60fps显示
    支持手机摄像头画面（来自 camera_frame_received 信号）
    和电脑摄像头预览（从 manager._latest_camera_frame 拉取）
    """

    def __init__(self, manager, mode="phone", parent=None):
        """mode: 'phone' = 查看手机摄像头, 'pc' = 电脑摄像头本地预览"""
        super().__init__(parent, Qt.Window)
        self.manager = manager
        self.mode = mode
        if mode == "phone":
            self.setWindowTitle("手机摄像头")
        else:
            self.setWindowTitle("电脑摄像头预览")
        self._pix = QPixmap()
        self._latest_frame = None
        self._last_frame_time = 0  # 上次收到帧的时间
        self._aspect_ratio = 16.0 / 9.0
        self._resizing = False
        self._setup_ui()
        self._display_timer = QTimer(self)
        self._display_timer.setInterval(16)
        self._display_timer.timeout.connect(self._flush_frame)
        self.resize(640, 480)

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)
        self.canvas = CameraCanvas()
        layout.addWidget(self.canvas, 1)

    def showEvent(self, event):
        super().showEvent(event)
        try:
            apply_dark_title_bar(self)
        except Exception:
            pass
        if self.mode == "phone":
            try:
                self.manager.camera_frame_received.connect(self._on_frame_received)
            except Exception:
                pass
        self._display_timer.start()

    def closeEvent(self, event):
        if self.mode == "phone":
            try:
                self.manager.camera_frame_received.disconnect(self._on_frame_received)
            except Exception:
                pass
            try:
                self.manager.send_action("camera_stop")
            except Exception:
                pass
        self._display_timer.stop()
        self._latest_frame = None
        self.canvas.clear()
        super().closeEvent(event)

    def _on_frame_received(self, frame_data):
        self._latest_frame = frame_data
        self._last_frame_time = time.time()

    def _flush_frame(self):
        now = time.time()
        if self.mode == "phone":
            if not self._latest_frame:
                # 超过2秒没收到帧，清空画面
                if self._last_frame_time > 0 and now - self._last_frame_time > 2.0:
                    self._last_frame_time = 0
                    self.canvas.clear()
                return
            data = self._latest_frame
            self._latest_frame = None
            if self._pix.loadFromData(data) and not self._pix.isNull():
                w = self._pix.width()
                h = self._pix.height()
                if h > 0:
                    self._aspect_ratio = w / h
                self.canvas.set_pixmap(self._pix)
        else:
            # 电脑摄像头预览：从 manager 拉取最新帧
            try:
                frame = self.manager._latest_camera_frame
                if frame:
                    self.canvas.load_frame(frame)
            except Exception:
                pass

    def resizeEvent(self, event):
        super().resizeEvent(event)
        if self._resizing:
            return
        new_h = event.size().height()
        target_w = int(new_h * self._aspect_ratio)
        if abs(target_w - event.size().width()) > 2:
            self._resizing = True
            self.resize(target_w, new_h)
            self._resizing = False


class CameraPage(QWidget):
    """共享摄像头 - 手机摄像头由手机端自行管理，电脑端接收显示；
    电脑摄像头可推流给手机查看
    摄像头画面均在独立弹窗中显示，此页面仅提供控制按钮
    """

    def __init__(self, manager):
        super().__init__()
        self.manager = manager
        self._camera_active = False
        self._phone_cam_window = None
        self._pc_cam_window = None
        self._setup_ui()
        self._connect_signals()
        self._update_button_states()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 20, 24, 20)
        layout.setSpacing(16)

        title = TitleLabel("共享摄像头")
        title.setObjectName("titleLabel")
        setFont(title, 28, QFont.Bold)
        layout.addWidget(title)

        self.channel_label = SubtitleLabel("当前通道: --")
        layout.addWidget(self.channel_label)

        ctrl_frame = CardWidget()
        ctrl_layout = QHBoxLayout(ctrl_frame)
        ctrl_layout.setContentsMargins(16, 12, 16, 12)
        ctrl_layout.setSpacing(12)



        # 电脑摄像头推流给手机
        self.pc_camera_btn = PrimaryPushButton("电脑摄像头→手机")
        ctrl_layout.addWidget(self.pc_camera_btn)

        # 查看手机摄像头（独立窗口）
        self.view_phone_cam_btn = PushButton("查看手机摄像头")
        ctrl_layout.addWidget(self.view_phone_cam_btn)

        # 切换手机摄像头镜头（前置/后置）
        self.switch_phone_cam_btn = PushButton("切换手机镜头")
        ctrl_layout.addWidget(self.switch_phone_cam_btn)


        ctrl_layout.addStretch()
        layout.addWidget(ctrl_frame)

        # 提示文本
        self.hint_label = BodyLabel('"摄像头画面将在独立窗口中显示。点击"查看手机摄像头"或"电脑摄像头→手机"开始推送。')
        self.hint_label.setWordWrap(True)
        layout.addWidget(self.hint_label)
        layout.addStretch()

    def _connect_signals(self):

        self.pc_camera_btn.clicked.connect(self._toggle_pc_camera)

        self.view_phone_cam_btn.clicked.connect(self._open_phone_cam_window)

        self.switch_phone_cam_btn.clicked.connect(self._switch_camera)

        try:

            self.manager.connection_status_changed.connect(lambda c, ch: self._update_button_states())

        except Exception:

            pass

    def _update_button_states(self):
        try:
            ch = self.manager.current_channel
            self.channel_label.setText(f"当前通道: {ch}")
        except Exception:
            pass

    def _switch_camera(self):
        """发送切换摄像头指令到手机"""
        try:
            self.manager.send_action("camera_switch", {})
        except Exception:
            pass

    def _open_phone_cam_window(self):
        """打开独立手机摄像头查看窗口"""
        try:
            if self._phone_cam_window is None or not self._phone_cam_window.isVisible():
                self._phone_cam_window = CameraWindow(self.manager, mode="phone")
            self._phone_cam_window.show()
            self._phone_cam_window.raise_()
            self._phone_cam_window.activateWindow()
        except Exception as e:
            dark_msg_box(self, QMessageBox.Warning, "操作失败", f"打开手机摄像头窗口出错: {e}")

    def _open_pc_cam_window(self):
        """打开独立电脑摄像头预览窗口"""
        try:
            if self._pc_cam_window is None or not self._pc_cam_window.isVisible():
                self._pc_cam_window = CameraWindow(self.manager, mode="pc")
            self._pc_cam_window.show()
            self._pc_cam_window.raise_()
            self._pc_cam_window.activateWindow()
        except Exception as e:
            dark_msg_box(self, QMessageBox.Warning, "操作失败", f"打开电脑摄像头窗口出错: {e}")

    def _toggle_pc_camera(self):
        """电脑摄像头推流给手机 启停切换"""
        if self.manager._pc_camera_running:
            self.manager.stop_pc_camera()
            self.pc_camera_btn.setText("电脑摄像头→手机")
            self._camera_active = False
        else:
            try:
                import cv2
                cap = cv2.VideoCapture(0)
                if not cap.isOpened():
                    cap.release()
                    dark_msg_box(self, QMessageBox.Warning, "错误",
                                 "无法打开电脑摄像头，请检查设备。")
                    return
                cap.release()
            except ImportError:
                dark_msg_box(self, QMessageBox.Warning, "依赖缺失",
                             "请安装 opencv-python: pip install opencv-python")
                return
            self.manager.start_pc_camera()
            self.pc_camera_btn.setText("停止电脑摄像头")
            self._camera_active = True
            # 自动打开电脑摄像头预览窗口
            self._open_pc_cam_window()


