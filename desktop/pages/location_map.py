# -*- coding: utf-8 -*-
"""移动路线图页面（暂未开放）"""
from PyQt5.QtWidgets import QWidget, QVBoxLayout
from PyQt5.QtCore import Qt
from PyQt5.QtGui import QFont
from qfluentwidgets import (TitleLabel, BodyLabel, setFont, FluentIcon as FIF)
from styles import get_theme, _c


class LocationMapPage(QWidget):
    """移动路线图（暂未开放，仅显示提示页面，避免闪退/白屏）"""

    def __init__(self, manager):
        super().__init__()
        self.manager = manager
        self._setup_ui()

    def _setup_ui(self):
        """构建简单的提示页面"""
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 20, 24, 20)
        layout.setSpacing(12)

        # 标题
        title = TitleLabel("移动路线图")
        setFont(title, 28, QFont.Bold)
        layout.addWidget(title)

        # 居中提示
        hint = BodyLabel("该功能暂未开放，敬请期待")
        hint.setAlignment(Qt.AlignCenter)
        c = _c()
        hint.setStyleSheet(
            f"font-size: 18px; color: {c['text_secondary']}; "
            f"padding: 60px; background-color: transparent;"
        )
        layout.addWidget(hint, 1)