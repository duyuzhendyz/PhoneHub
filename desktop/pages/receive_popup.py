"""
PhoneHub — 接收文件 / 接收文字 独立弹窗
- 在收到手机端发来的文件或文字时，于主窗口外弹出提示
- 使用 Qt.Tool 类型：独立于主窗口，可随主窗口之外浮动、置顶显示
- 完全不替换/移除页面内部的现有显示区域，仅作额外的弹出提示
"""

import os
import time
import pyperclip
from PyQt5.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout,
                             QLabel, QPushButton, QApplication)
from PyQt5.QtCore import Qt, QTimer
from PyQt5.QtGui import QFont
from qfluentwidgets import (CardWidget, SubtitleLabel, BodyLabel,
                            PushButton, PrimaryPushButton,
                            TextEdit, setFont, FluentIcon as FIF)
from styles import _c, dark_dialog_style, apply_dark_title_bar


class ReceivePopupWindow(QWidget):
    """接收文件/文字的独立弹窗（Qt.Tool：独立于主窗口、浮动置顶，不占任务栏）"""

    def __init__(self, title="接收", parent=None):
        super().__init__(parent, Qt.Tool | Qt.WindowStaysOnTopHint)
        self._kind = "none"          # "file" / "text"
        self._file_path = None       # 文件弹窗：接收文件完整路径
        self._fallback_dir = ""      # 无完整路径时打开该目录
        self._text_content = ""      # 文字弹窗：收到的文本
        self._text_filename = None   # 文字弹窗：来源文件名
        self.setWindowTitle(title)
        self.setFixedWidth(460)
        self.setStyleSheet(dark_dialog_style())
        self._setup_ui()
        self._autoclose = QTimer(self)
        self._autoclose.setSingleShot(True)
        self._autoclose.timeout.connect(self._auto_close_if_stale)
        try:
            apply_dark_title_bar(self)
        except Exception:
            pass

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(12)

        self.title_label = SubtitleLabel()
        setFont(self.title_label, 16, QFont.Bold)
        layout.addWidget(self.title_label)

        self.body_label = BodyLabel()
        self.body_label.setWordWrap(True)
        self.body_label.setStyleSheet("color: #b0b0b0;")
        layout.addWidget(self.body_label)

        # 文字内容预览（仅文字弹窗显示）
        self.text_preview = TextEdit()
        self.text_preview.setReadOnly(True)
        self.text_preview.setMinimumHeight(140)
        self.text_preview.setPlaceholderText("")
        layout.addWidget(self.text_preview)

        btn_row = QHBoxLayout()
        self.btn_open = PushButton("打开")
        btn_row.addWidget(self.btn_open)
        self.btn_copy = PushButton("复制")
        btn_row.addWidget(self.btn_copy)
        btn_row.addStretch()
        self.btn_close = PrimaryPushButton("关闭")
        btn_row.addWidget(self.btn_close)
        layout.addLayout(btn_row)

        self.btn_open.clicked.connect(self._on_open)
        self.btn_copy.clicked.connect(self._on_copy)
        self.btn_close.clicked.connect(self._on_close)

    # ==================== 公共方法 ====================

    def show_file(self, file_name, file_size, file_path, fallback_dir=""):
        """显示收到的文件提示"""
        self._kind = "file"
        self._file_path = file_path if file_path and os.path.exists(file_path) else None
        self._fallback_dir = fallback_dir or ""
        self._text_content = ""
        self.title_label.setText("📁 收到文件")
        size_str = self._fmt_size(file_size)
        now = time.strftime("%H:%M:%S")
        self.body_label.setText(f"来自手机 · {now}")
        self.text_preview.setPlainText(f"文件名：{file_name}\n大小：{size_str}\n\n已保存到：{file_path or '接收目录'}")
        self.text_preview.setVisible(True)
        self.btn_open.setVisible(True)
        self.btn_copy.setVisible(False)
        self.setWindowTitle("收到文件")
        self._restart_autoclose(30)
        self._show_pop()

    def show_text(self, text, filename=None):
        """显示收到的文字提示"""
        self._kind = "text"
        self._file_path = None
        self._text_content = text or ""
        self._text_filename = filename
        self.title_label.setText("✉️ 收到文字")
        now = time.strftime("%H:%M:%S")
        self.body_label.setText(f"来自手机 · {now}" + (f" · {filename}" if filename else ""))
        self.text_preview.setPlainText(self._text_content)
        self.text_preview.setVisible(True)
        self.btn_open.setVisible(False)
        self.btn_copy.setVisible(True)
        self.setWindowTitle("收到文字")
        self._restart_autoclose(30)
        self._show_pop()

    # ==================== 内部控制 ====================

    def _show_pop(self):
        self.show()
        self.raise_()
        self.activateWindow()
        # 让窗口自适应高度
        self.adjustSize()
        self.setFixedWidth(460)

    def _restart_autoclose(self, seconds):
        self._autoclose.start(seconds * 1000)

    def _auto_close_if_stale(self):
        # 倒计时结束自动隐藏，避免常驻遮挡
        if self.isVisible():
            self.hide()

    def _on_open(self):
        # 打开接收文件所在位置（仅文件弹窗）
        path = self._file_path or self._fallback_dir
        if path:
            try:
                import subprocess
                subprocess.Popen(f'explorer /select,"{path}"')
            except Exception:
                try:
                    os.startfile(os.path.dirname(path))
                except Exception:
                    pass

    def _on_copy(self):
        if self._text_content:
            try:
                pyperclip.copy(self._text_content)
            except Exception:
                pass

    def _on_close(self):
        self.hide()

    def _fmt_size(self, b):
        try:
            b = int(b)
            if b >= 1024 * 1024 * 1024:
                return f"{b / (1024 * 1024 * 1024):.2f} GB"
            if b >= 1024 * 1024:
                return f"{b / (1024 * 1024):.1f} MB"
            if b >= 1024:
                return f"{b / 1024:.0f} KB"
            return f"{b} B"
        except Exception:
            return "unknown"