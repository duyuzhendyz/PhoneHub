import os
import time
from PyQt5.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout,
                               QGridLayout, QGroupBox, QMessageBox)
from PyQt5.QtGui import QFont
from styles import get_theme, _c, dark_msg_box
from qfluentwidgets import (CardWidget, TitleLabel, BodyLabel, SubtitleLabel,
                            PushButton, PrimaryPushButton, ToolButton, ToggleButton,
                            LineEdit, CheckBox, ComboBox, setFont, FluentIcon as FIF,
                            InfoBar, InfoBarPosition, ListWidget)


# 截图保存目录（电脑端，自动创建）
SCREENSHOT_SAVE_DIR = r"F:\desk\手机上传\截图"


# ==================== 主控制页 ====================
class RemoteControlPage(QWidget):
    """远程控制页：手机截图"""

    def __init__(self, manager):
        super().__init__()
        self.manager = manager
        self._setup_ui()
        self._connect_signals()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 20, 24, 20)
        layout.setSpacing(16)

        title = TitleLabel("远程控制")
        title.setObjectName("titleLabel")
        setFont(title, 28, QFont.Bold)
        layout.addWidget(title)

        # ===== 截图 =====
        func_group = QGroupBox("截图")
        func_group.setStyleSheet(self._group_style())
        ss_layout = QHBoxLayout(func_group)

        self.ss_phone_btn = PrimaryPushButton("手机截图")
        ss_layout.addWidget(self.ss_phone_btn)

        ss_layout.addStretch()
        layout.addWidget(func_group)

    def _send_key(self, key):
        self.manager.send_command("key", extra={"key": key})

    def _group_style(self):
        c = _c()
        return f"""
            QGroupBox {{
                color: {c['text_secondary']};
                background-color: {c['card']};
                border: 1px solid {c['card_border']};
                border-radius: 8px;
                margin-top: 14px;
                font-weight: 600;
                padding-top: 8px;
            }}
            QGroupBox::title {{
                subcontrol-origin: margin;
                subcontrol-position: top left;
                left: 12px;
                padding: 0 6px;
                color: {c['text_secondary']};
                background-color: transparent;
            }}
        """

    def _connect_signals(self):
        self.ss_phone_btn.clicked.connect(self._phone_screenshot)

    # ====== 手机截图 ======
    def _phone_screenshot(self):
        """手机截图：支持 WiFi 和 ADB 两种方式，保存到电脑并回传手机"""
        # 自动创建保存目录
        try:
            os.makedirs(SCREENSHOT_SAVE_DIR, exist_ok=True)
        except Exception as e:
            dark_msg_box(self, QMessageBox.Critical, "创建目录失败",
                         f"无法创建截图保存目录:\n{SCREENSHOT_SAVE_DIR}\n错误: {e}")
            return

        timestamp = time.strftime("%Y%m%d_%H%M%S")
        local_path = os.path.join(SCREENSHOT_SAVE_DIR, f"phone_{timestamp}.png")

        if self.manager.adb_device_id:
            # ADB 模式：直接 ADB 截图
            try:
                self.manager.adb_screenshot(local_path)
            except Exception as e:
                dark_msg_box(self, QMessageBox.Warning, "截图失败", f"ADB 截图失败: {e}")
                return

            if not os.path.exists(local_path) or os.path.getsize(local_path) == 0:
                dark_msg_box(self, QMessageBox.Warning, "截图失败",
                             "截图文件未生成，请检查 ADB 连接。")
                return

            # 通知手机截图已完成
            try:
                self.manager.send_action(
                    "screenshot_saved",
                    extra={"message": "截图已保存到电脑", "path": local_path})
            except Exception as e:
                print(f"通知手机失败: {e}")

            tip = f"截图已保存到:\n{local_path}"
            dark_msg_box(self, QMessageBox.Information, "截图成功", tip)
        else:
            # WiFi 模式：发送截图请求到手机，手机截图后通过 sendFile 回传
            try:
                self.manager.send_action("screenshot_request")
                dark_msg_box(self, QMessageBox.Information, "已发送截图请求",
                             "已请求手机截图，截图完成后将自动回传至电脑。")
            except Exception as e:
                dark_msg_box(self, QMessageBox.Warning, "请求失败", f"发送截图请求失败: {e}")
