from PyQt5.QtWidgets import QWidget, QVBoxLayout, QHBoxLayout, QLabel, QDialog, QProgressBar, QFrame, QApplication
from PyQt5.QtCore import Qt, QTimer, QSize
from PyQt5.QtGui import QFont, QPixmap, QImage, QClipboard

from qfluentwidgets import (FluentWindow, NavigationItemPosition, FluentIcon as FIF,
                            PushButton, BodyLabel, TitleLabel, SubtitleLabel,
                            setFont, InfoBar, InfoBarPosition)
from qfluentwidgets.components.widgets.stacked_widget import EntranceTransitionStackedWidget

from styles import apply_dark_title_bar, _c, get_theme
from connection_manager import ConnectionManager

# 页面导入
from pages.dashboard import DashboardPage
from pages.file_transfer import FileTransferPage
from pages.clipboard_sync import ClipboardSyncPage
from pages.text_share import TextSharePage
from pages.screen_mirror import ScreenMirrorPage
from pages.camera import CameraPage
from pages.notifications import NotificationsPage
from pages.location_map import LocationMapPage
from pages.file_manager import FileManagerPage
from pages.apk_install import ApkInstallPage
from pages.app_manager import AppManagerPage
from pages.push_web import PushWebPage
from pages.settings import SettingsPage


class MainWindow(FluentWindow):
    def __init__(self):
        self.manager = ConnectionManager()
        super().__init__()
        self.setWindowTitle("PhoneHub")
        self.resize(1000, 680)
        self.setMinimumSize(960, 640)

        # 替换默认的弹出动画为更流畅的滑入滑出过渡动画
        # 注意：必须在 _create_pages 之前调用，这样 addSubInterface 会使用新的 view
        self._setup_smooth_transition()

        # 创建页面
        self._create_pages()

        # 状态栏（导航底部）
        self._create_status_widget()

        # 连接信号
        self._connect_signals()

        # 深色标题栏
        apply_dark_title_bar(self)

        # 启动服务器
        self.manager.start_server()

        # F3 截图热键监听（已禁用，保留代码不做删除）
        # self._f3_thread = None
        # self._f3_running = False
        # self._start_f3_listener()

    def _setup_smooth_transition(self):
        """设置平滑的页面切换过渡动画"""
        # 获取当前的 StackedWidget
        stacked = self.stackedWidget

        # 保存当前的 view 引用
        old_view = stacked.view

        # 创建新的入场过渡动画组件
        new_view = EntranceTransitionStackedWidget(stacked)

        # 替换布局中的组件
        stacked.hBoxLayout.removeWidget(old_view)
        stacked.hBoxLayout.addWidget(new_view)

        # 更新引用
        stacked.view = new_view

        # 连接新 view 的信号到 stacked 的 currentChanged
        new_view.currentChanged.connect(stacked.currentChanged)

        # 断开旧 view 的信号连接（在连接新信号之后）
        try:
            old_view.currentChanged.disconnect(stacked.currentChanged)
        except:
            pass

        # 旧组件删除
        old_view.deleteLater()

    def _create_pages(self):
        """创建所有页面并添加到导航"""
        self.dashboard_page = DashboardPage(self.manager)
        self.file_transfer_page = FileTransferPage(self.manager)
        self.clipboard_page = ClipboardSyncPage(self.manager)
        self.text_page = TextSharePage(self.manager)
        self.screen_mirror_page = ScreenMirrorPage(self.manager)
        self.camera_page = CameraPage(self.manager)
        self.notifications_page = NotificationsPage(self.manager)
        self.location_map_page = LocationMapPage(self.manager)
        self.file_manager_page = FileManagerPage(self.manager)
        self.apk_install_page = ApkInstallPage(self.manager)
        self.app_manager_page = AppManagerPage(self.manager)
        self.push_web_page = PushWebPage(self.manager)
        self.settings_page = SettingsPage(self.manager)

        # 导航项列表：(页面实例, 图标, 标题, 位置)
        nav_items = [
            (self.dashboard_page,     FIF.HOME,      "仪表盘"),
            (self.file_transfer_page,  FIF.SHARE,     "文件传输"),
            (self.clipboard_page,     FIF.COPY,      "剪贴板同步"),
            (self.text_page,          FIF.CHAT,      "文字互传"),
            (self.screen_mirror_page,  FIF.VIDEO,     "投屏与反向控制"),
            (self.camera_page,        FIF.CAMERA,    "共享摄像头"),
            (self.notifications_page,  FIF.MESSAGE,   "通知读取"),
            (self.location_map_page,   FIF.DATE_TIME, "移动路线图"),
            (self.file_manager_page,   FIF.FOLDER,    "远程文件管理"),
            (self.apk_install_page,    FIF.DOWNLOAD,  "APK安装"),
            (self.app_manager_page,    FIF.ALBUM,     "应用管理"),
            (self.push_web_page,       FIF.LINK,      "推送网页"),
            (self.settings_page,       FIF.SETTING,   "设置", NavigationItemPosition.BOTTOM),
        ]

        for item in nav_items:
            page = item[0]
            icon = item[1]
            title = item[2]
            position = item[3] if len(item) > 3 else NavigationItemPosition.SCROLL
            page.setObjectName(title)
            self.addSubInterface(page, icon, title, position)

        # 默认选中仪表盘
        self.switchTo(self.dashboard_page)

    def _create_status_widget(self):
        """创建导航底部状态栏"""
        self.status_label = BodyLabel("等待连接...")
        setFont(self.status_label, 11)
        self.ip_label = BodyLabel(f"IP: {self.manager.local_ip}:{self.manager.port}")
        setFont(self.ip_label, 10)

    def _connect_signals(self):
        self.manager.connection_status_changed.connect(self._on_connection_changed)
        self.manager.connection_message_changed.connect(self._on_connection_message)
        self.manager.power_action_received.connect(self._on_power_action)

    def _on_connection_changed(self, connected, channel):
        c = _c()
        if connected:
            channel_names = {"wifi": "WiFi直连", "paw": "PAW中转", "adb": "USB数据线"}
            self.status_label.setText(f"已连接 — {channel_names.get(channel, channel)}")
            self.status_label.setStyleSheet(f"color:{c['success']}; font-size: 12px; font-weight: 500;")
        else:
            self.status_label.setText("未连接")
            self.status_label.setStyleSheet(f"color:{c['error']}; font-size: 12px;")

    def _on_connection_message(self, message):
        c = _c()
        if not self.manager.phone_connected:
            self.status_label.setText(message)
            self.status_label.setStyleSheet(f"color:{c['warning']}; font-size: 12px;")

    def _on_power_action(self, action_type):
        if action_type in ('shutdown', 'reboot'):
            label_text = "关机" if action_type == "shutdown" else "重启"
            dlg = QDialog(self)
            dlg.setWindowTitle(f"远程{label_text}")
            dlg.setWindowFlags(dlg.windowFlags() | Qt.WindowStaysOnTopHint)
            dlg.setFixedSize(320, 160)
            layout = QVBoxLayout(dlg)

            title = TitleLabel(f"电脑即将{label_text}")
            title.setAlignment(Qt.AlignCenter)
            layout.addWidget(title)

            countdown_label = TitleLabel("30")
            countdown_label.setAlignment(Qt.AlignCenter)
            countdown_label.setStyleSheet("color: #0078D4; font-size: 36px;")
            layout.addWidget(countdown_label)

            progress = QProgressBar()
            progress.setRange(0, 30)
            progress.setValue(30)
            layout.addWidget(progress)

            cancel_btn = PushButton("取消")
            layout.addWidget(cancel_btn)

            remaining = [30]

            def on_tick():
                remaining[0] -= 1
                if remaining[0] <= 0:
                    timer.stop()
                    dlg.accept()
                    self._release_power_dlg(dlg)
                else:
                    countdown_label.setText(str(remaining[0]))
                    progress.setValue(remaining[0])

            timer = QTimer(dlg)
            timer.timeout.connect(on_tick)
            timer.start(1000)

            def on_cancel():
                timer.stop()
                try:
                    import subprocess
                    subprocess.Popen('shutdown /a', shell=True)
                except Exception:
                    pass
                dlg.reject()
                self._release_power_dlg(dlg)

            cancel_btn.clicked.connect(on_cancel)
            # 非阻塞显示，避免 exec_() 阻塞主事件循环（30s 倒计时期间 UI 仍可响应）
            self._power_dlg = dlg
            dlg.setWindowModality(Qt.ApplicationModal)
            dlg.show()

    def _release_power_dlg(self, dlg):
        """关闭后释放电源弹窗，避免 self._power_dlg 持有引用造成内存泄漏"""
        if getattr(self, '_power_dlg', None) is dlg:
            self._power_dlg = None
            dlg.deleteLater()

    def _start_f3_listener(self):
        """启动 F3 全局热键监听线程"""
        import threading
        import ctypes
        import time

        VK_F3 = 0x72
        self._f3_running = True

        def f3_monitor():
            was_pressed = False
            while self._f3_running:
                try:
                    state = ctypes.windll.user32.GetAsyncKeyState(VK_F3)
                    if state & 0x8000:
                        if not was_pressed:
                            was_pressed = True
                            # 检测到 F3 按下，等 2 秒后弹窗
                            time.sleep(2)
                            if self._f3_running:
                                # 在主线程弹出粘贴窗口
                                QTimer.singleShot(0, self._show_paste_screenshot_dialog)
                    else:
                        was_pressed = False
                except Exception:
                    pass
                time.sleep(0.05)

        self._f3_thread = threading.Thread(target=f3_monitor, daemon=True)
        self._f3_thread.start()

    def _show_paste_screenshot_dialog(self):
        """弹出粘贴截图窗口，接收用户 Ctrl+V 粘贴的图片"""
        dlg = QDialog(self)
        dlg.setWindowTitle("粘贴截图 — 按 Ctrl+V 粘贴图片")
        dlg.setWindowFlags(dlg.windowFlags() | Qt.WindowStaysOnTopHint)
        dlg.setFixedSize(500, 420)
        dlg.setStyleSheet("background-color: #1e1e1e;")

        layout = QVBoxLayout(dlg)
        layout.setContentsMargins(16, 16, 16, 16)
        layout.setSpacing(10)

        title = SubtitleLabel("粘贴截图")
        title.setStyleSheet("color: #ffffff;")
        layout.addWidget(title)

        hint = BodyLabel("按 Ctrl+V 粘贴你刚截的图片，粘贴后会自动发送到手机")
        hint.setStyleSheet("color: #b0b0b0;")
        hint.setWordWrap(True)
        layout.addWidget(hint)

        # 图片预览区域
        from PyQt5.QtWidgets import QLabel
        preview_label = QLabel()
        preview_label.setAlignment(Qt.AlignCenter)
        preview_label.setMinimumHeight(250)
        preview_label.setStyleSheet("background-color: #2d2d2d; border-radius: 6px; color: #606060;")
        preview_label.setText("等待粘贴图片...")
        layout.addWidget(preview_label, 1)

        btn_row = QHBoxLayout()
        send_btn = PushButton("发送到手机")
        send_btn.setEnabled(False)
        cancel_btn = PushButton("关闭")
        btn_row.addStretch()
        btn_row.addWidget(send_btn)
        btn_row.addWidget(cancel_btn)
        layout.addLayout(btn_row)

        # 保存的图片路径
        saved_path = [None]

        def handle_paste():
            clipboard = QApplication.clipboard()
            mime = clipboard.mimeData()
            if mime.hasImage():
                img = clipboard.image()
                if img and not img.isNull():
                    # 显示预览
                    pixmap = QPixmap.fromImage(img)
                    scaled = pixmap.scaled(460, 240, Qt.KeepAspectRatio, Qt.SmoothTransformation)
                    preview_label.setPixmap(scaled)
                    preview_label.setStyleSheet("background-color: #2d2d2d; border-radius: 6px;")
                    # 保存到临时文件
                    import os
                    import time as _time
                    path = os.path.join(self.manager.receive_dir, f"pc_screenshot_{int(_time.time())}.png")
                    img.save(path, "PNG")
                    saved_path[0] = path
                    send_btn.setEnabled(True)
            elif mime.hasUrls():
                urls = mime.urls()
                if urls:
                    file_path = urls[0].toLocalFile()
                    if file_path.lower().endswith(('.png', '.jpg', '.jpeg', '.bmp')):
                        pixmap = QPixmap(file_path)
                        if not pixmap.isNull():
                            scaled = pixmap.scaled(460, 240, Qt.KeepAspectRatio, Qt.SmoothTransformation)
                            preview_label.setPixmap(scaled)
                            preview_label.setStyleSheet("background-color: #2d2d2d; border-radius: 6px;")
                            saved_path[0] = file_path
                            send_btn.setEnabled(True)

        def on_send():
            if saved_path[0]:
                self.manager.send_file(saved_path[0])
                self.manager.send_action("screenshot_saved", extra={"message": "电脑截图已发送"})
                from qfluentwidgets import InfoBar
                InfoBar.success("已发送", "截图已发送到手机", parent=self,
                                duration=2000, position=InfoBarPosition.TOP)
            dlg.accept()

        send_btn.clicked.connect(on_send)
        cancel_btn.clicked.connect(dlg.reject)

        # 安装事件过滤器监听 Ctrl+V
        dlg.installEventFilter(dlg)
        dlg._handle_paste = handle_paste

        # 重写 keyPressEvent
        def key_press_event(event):
            if event.key() == Qt.Key_V and event.modifiers() == Qt.ControlModifier:
                handle_paste()
            else:
                QDialog.keyPressEvent(dlg, event)

        dlg.keyPressEvent = key_press_event

        # 弹窗后自动聚焦，方便直接粘贴
        dlg.show()
        dlg.raise_()
        dlg.activateWindow()
        dlg.setFocus()

    def closeEvent(self, event):
        # 最小化到托盘而不是退出
        event.ignore()
        self.hide()
