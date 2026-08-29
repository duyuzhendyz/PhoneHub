import os
import json
from PyQt5.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout,
                               QMessageBox, QFileDialog)
from PyQt5.QtCore import Qt
from PyQt5.QtGui import QFont
from styles import get_theme, set_theme, apply_dark_title_bar, _c, dark_msg_box
from qfluentwidgets import (CardWidget, TitleLabel, BodyLabel, SubtitleLabel,
                            PushButton, PrimaryPushButton,
                            LineEdit, CheckBox,
                            setFont, FluentIcon as FIF,
                            InfoBar, InfoBarPosition, setThemeColor,
                            SmoothScrollArea)

SETTINGS_FILE = os.path.join(os.path.expanduser("~"), "PhoneHub", "data", "settings.json")


class SettingsPage(QWidget):
    def __init__(self, manager):
        super().__init__()
        self.manager = manager
        self.settings_data = {}
        self._last_connected = False  # 跟踪连接状态，用于 phone_status_label
        self._load_settings()
        self._setup_ui()

    def _load_settings(self):
        try:
            os.makedirs(os.path.dirname(SETTINGS_FILE), exist_ok=True)
            if os.path.exists(SETTINGS_FILE):
                with open(SETTINGS_FILE, 'r', encoding='utf-8') as f:
                    self.settings_data = json.load(f)
        except Exception:
            self.settings_data = {}

    def _save_settings(self):
        try:
            os.makedirs(os.path.dirname(SETTINGS_FILE), exist_ok=True)
            with open(SETTINGS_FILE, 'w', encoding='utf-8') as f:
                json.dump(self.settings_data, f, ensure_ascii=False, indent=2)
        except Exception:
            pass

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 20, 24, 20)
        layout.setSpacing(16)

        title = TitleLabel("设置")
        setFont(title, 24, QFont.Bold)
        layout.addWidget(title)

        subtitle = BodyLabel("自定义 PhoneHub 的外观和行为")
        layout.addWidget(subtitle)

        scroll_area = SmoothScrollArea()
        scroll_area.setWidgetResizable(True)
        scroll_area.setFrameShape(SmoothScrollArea.NoFrame)
        scroll_area.setStyleSheet("background: transparent;")

        scroll_content = QWidget()
        scroll_layout = QVBoxLayout(scroll_content)
        scroll_layout.setContentsMargins(0, 0, 0, 0)
        scroll_layout.setSpacing(12)

        # ===== 主题设置（Fluent Design）=====
        theme_card = CardWidget()
        theme_layout = QVBoxLayout(theme_card)
        theme_layout.setContentsMargins(16, 16, 16, 16)
        theme_layout.setSpacing(12)

        theme_title = SubtitleLabel("外观")
        theme_layout.addWidget(theme_title)

        theme_desc = BodyLabel("选择 Fluent Design 主题风格，切换后立即生效")
        theme_layout.addWidget(theme_desc)

        theme_btn_layout = QHBoxLayout()
        theme_btn_layout.setSpacing(12)

        self.dark_btn = PushButton(FIF.BRIGHTNESS, "  深色主题")
        self.dark_btn.setFixedHeight(40)
        self.light_btn = PushButton(FIF.BRUSH, "  浅色主题")
        self.light_btn.setFixedHeight(40)

        self.dark_btn.clicked.connect(lambda: self._switch_theme("dark"))
        self.light_btn.clicked.connect(lambda: self._switch_theme("light"))

        theme_btn_layout.addWidget(self.dark_btn)
        theme_btn_layout.addWidget(self.light_btn)
        theme_btn_layout.addStretch()
        theme_layout.addLayout(theme_btn_layout)

        current = get_theme()
        self.theme_status = BodyLabel(f"当前: {'深色' if current == 'dark' else '浅色'}主题")
        theme_layout.addWidget(self.theme_status)

        scroll_layout.addWidget(theme_card)

        # ===== 连接设置 =====
        conn_card = CardWidget()
        conn_layout = QVBoxLayout(conn_card)
        conn_layout.setContentsMargins(16, 16, 16, 16)
        conn_layout.setSpacing(12)

        conn_title = SubtitleLabel("连接设置")
        conn_layout.addWidget(conn_title)

        port_row = QHBoxLayout()
        port_row.addWidget(BodyLabel("监听端口:"))
        self.port_input = LineEdit()
        self.port_input.setText(str(self.manager.port))
        self.port_input.setMinimumWidth(200)
        port_row.addWidget(self.port_input)
        port_row.addStretch()
        conn_layout.addLayout(port_row)

        token_row = QHBoxLayout()
        token_row.addWidget(BodyLabel("连接令牌:"))
        self.paw_token_input = LineEdit()
        # 从 settings.json 加载实际 token，默认值统一取 ConnectionManager 常量（单一数据源）
        _token = self.settings_data.get("paw_token", self.manager.DEFAULT_SECRET_TOKEN)
        self.paw_token_input.setText(_token)
        self.paw_token_input.setEchoMode(LineEdit.Password)
        self.paw_token_input.setMinimumWidth(200)
        token_row.addWidget(self.paw_token_input)
        token_row.addStretch()
        conn_layout.addLayout(token_row)

        paw_url_row = QHBoxLayout()
        paw_url_row.addWidget(BodyLabel("PAW 服务器地址:"))
        self.paw_url_input = LineEdit()
        _paw_url = self.settings_data.get("paw_url", self.manager.DEFAULT_PAW_URL)
        self.paw_url_input.setText(_paw_url)
        self.paw_url_input.setPlaceholderText("https://yourname.pythonanywhere.com")
        self.paw_url_input.setMinimumWidth(300)
        paw_url_row.addWidget(self.paw_url_input)
        paw_url_row.addStretch()
        conn_layout.addLayout(paw_url_row)

        self.auto_start_cb = CheckBox("开机自动启动")
        conn_layout.addWidget(self.auto_start_cb)

        self.save_conn_btn = PrimaryPushButton("保存连接设置")
        self.save_conn_btn.clicked.connect(self._save_conn_settings)
        conn_layout.addWidget(self.save_conn_btn)

        scroll_layout.addWidget(conn_card)

        # ===== 手机端设置 =====
        phone_card = CardWidget()
        phone_layout = QVBoxLayout(phone_card)
        phone_layout.setContentsMargins(16, 16, 16, 16)
        phone_layout.setSpacing(12)

        phone_title = SubtitleLabel("手机端设置")
        phone_layout.addWidget(phone_title)

        self.never_sleep_cb = CheckBox("永不休眠 (发送到手机)")
        self.never_sleep_cb.setChecked(self.settings_data.get("never_sleep", False))
        self.never_sleep_cb.toggled.connect(self._on_never_sleep_toggled)
        phone_layout.addWidget(self.never_sleep_cb)

        self.phone_status_label = BodyLabel("")
        phone_layout.addWidget(self.phone_status_label)

        scroll_layout.addWidget(phone_card)

        # ===== 剪贴板设置 =====
        clip_card = CardWidget()
        clip_layout = QVBoxLayout(clip_card)
        clip_layout.setContentsMargins(16, 16, 16, 16)
        clip_layout.setSpacing(12)

        clip_title = SubtitleLabel("剪贴板设置")
        clip_layout.addWidget(clip_title)

        self.auto_sync_cb = CheckBox("自动同步剪贴板")
        self.auto_sync_cb.setChecked(self.settings_data.get("auto_sync", True))
        clip_layout.addWidget(self.auto_sync_cb)

        history_row = QHBoxLayout()
        history_row.addWidget(BodyLabel("历史记录上限:"))
        self.clip_history_limit = LineEdit()
        self.clip_history_limit.setText(str(self.settings_data.get("clip_history_limit", 500)))
        self.clip_history_limit.setMaximumWidth(120)
        history_row.addWidget(self.clip_history_limit)
        history_row.addStretch()
        clip_layout.addLayout(history_row)

        scroll_layout.addWidget(clip_card)

        # ===== 接收设置 =====
        recv_card = CardWidget()
        recv_layout = QVBoxLayout(recv_card)
        recv_layout.setContentsMargins(16, 16, 16, 16)
        recv_layout.setSpacing(12)

        recv_title = SubtitleLabel("接收设置")
        recv_layout.addWidget(recv_title)

        recv_dir_row = QHBoxLayout()
        recv_dir_row.addWidget(BodyLabel("接收文件夹:"))
        self.recv_dir_label = BodyLabel(self.manager.receive_dir)
        self.recv_dir_label.setWordWrap(True)
        recv_dir_row.addWidget(self.recv_dir_label, 1)
        recv_layout.addLayout(recv_dir_row)

        self.browse_recv_btn = PushButton("选择文件夹")
        self.browse_recv_btn.clicked.connect(self._browse_recv_dir)
        recv_layout.addWidget(self.browse_recv_btn)

        self.auto_open_cb = CheckBox("接收完成后打开文件夹")
        self.auto_open_cb.setChecked(self.settings_data.get("auto_open", False))
        recv_layout.addWidget(self.auto_open_cb)

        scroll_layout.addWidget(recv_card)

        # ===== 关于 =====
        about_card = CardWidget()
        about_layout = QVBoxLayout(about_card)
        about_layout.setContentsMargins(16, 16, 16, 16)
        about_layout.setSpacing(12)

        about_title = SubtitleLabel("关于")
        about_layout.addWidget(about_title)
        about_layout.addWidget(BodyLabel("PhoneHub 电脑端"))
        about_layout.addWidget(BodyLabel("版本: 1.0.0"))
        ip_label = BodyLabel("电脑IP: " + self.manager.local_ip)
        about_layout.addWidget(ip_label)
        dev_label = BodyLabel("设备ID: " + self.manager.device_id[:8] + "...")
        about_layout.addWidget(dev_label)

        scroll_layout.addWidget(about_card)

        scroll_layout.addStretch()
        scroll_area.setWidget(scroll_content)
        layout.addWidget(scroll_area)

        # 连接手机状态信号以更新显示
        try:
            self.manager.phone_status_received.connect(self._on_phone_status)
            self.manager.connection_status_changed.connect(self._on_connection_changed)
        except Exception:
            pass
    
    def _on_connection_changed(self, connected, channel):
        """根据连接状态更新手机状态标签显示"""
        self._last_connected = connected
        try:
            if not connected:
                self.phone_status_label.setText("未连接")
            else:
                # 如果已连接但尚未收到状态，显示等待中
                if self.phone_status_label.text() == "" or self.phone_status_label.text() == "(未知)":
                    self.phone_status_label.setText("等待状态...")
        except Exception:
            pass

    def _switch_theme(self, theme):
        """切换 Fluent Design 主题"""
        if theme == get_theme():
            return

        set_theme(theme)
        self.settings_data["theme"] = theme
        self._save_settings()

        # 重新应用全局样式
        window = self.window()
        if window:
            apply_dark_title_bar(window)

        # 显示提示
        theme_name = "深色" if theme == "dark" else "浅色"
        dark_msg_box(self, QMessageBox.Information, "主题已切换",
                      f"已切换到{theme_name} Fluent Design 主题。\n"
                      f"部分页面可能需要重新打开才能完全应用。")

    def _save_conn_settings(self):
        try:
            port_text = self.port_input.text().strip()
            if port_text:
                port = int(port_text)
                if not (1 <= port <= 65535):
                    dark_msg_box(self, QMessageBox.Warning, "端口无效", "端口必须在 1-65535 之间。")
                    return
                self.manager.port = port
            paw_token = self.paw_token_input.text().strip()
            paw_url = self.paw_url_input.text().strip()
            if paw_token:
                self.manager.secret_token = paw_token
                self.settings_data["paw_token"] = paw_token
            if paw_url:
                self.manager.paw_url = paw_url
                self.settings_data["paw_url"] = paw_url
            # 同步到 ConnectionManager 实际读取的配置缓存，确保 PAW 连接使用新值
            self.manager._save_settings_cache(paw_url=paw_url or None, secret_token=paw_token or None)
            self.settings_data["auto_start"] = self.auto_start_cb.isChecked()
            self.settings_data["auto_sync"] = self.auto_sync_cb.isChecked()
            self.settings_data["auto_open"] = self.auto_open_cb.isChecked()
            try:
                self.settings_data["clip_history_limit"] = int(self.clip_history_limit.text())
            except Exception:
                pass
            self._save_settings()
            dark_msg_box(self, QMessageBox.Information, "已保存", "设置已保存。端口变更需重启生效。")
        except Exception as e:
            dark_msg_box(self, QMessageBox.Warning, "保存失败", str(e))

    def _on_never_sleep_toggled(self, checked):
        self.settings_data["never_sleep"] = checked
        self._save_settings()
        # 通过当前通道下发指令
        try:
            self.manager.send_command("never_sleep", extra={"enabled": checked})
        except Exception:
            pass

    def _on_phone_status(self, status):
        """手机端上报的状态数据"""
        try:
            # 首先检查连接状态，如果未连接则显示未知
            if not getattr(self, '_last_connected', False):
                self.phone_status_label.setText("(未知)")
                return
                
            never = status.get("never_sleep")
            if never is None:
                # 无休眠设置信息，显示默认状态
                self.phone_status_label.setText("(未知)")
            elif never:
                self.phone_status_label.setText("永不休眠 (已启用)")
            else:
                self.phone_status_label.setText("默认休眠策略")
        except Exception:
            pass

    def _browse_recv_dir(self):
        dir_path = QFileDialog.getExistingDirectory(self, "选择接收文件夹")
        if dir_path:
            self.manager.receive_dir = dir_path
            self.recv_dir_label.setText(dir_path)
