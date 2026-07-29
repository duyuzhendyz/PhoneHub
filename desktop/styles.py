"""
PhoneHub — qfluentwidgets 主题管理
保留向后兼容的颜色常量和辅助函数
"""

import json
import os
import ctypes

_SETTINGS_DIR = os.path.join(os.path.expanduser("~"), "PhoneHub", "data")
_THEME_FILE = os.path.join(_SETTINGS_DIR, "theme.json")
_current_theme = "dark"


def get_theme():
    global _current_theme
    try:
        if os.path.exists(_THEME_FILE):
            with open(_THEME_FILE, "r", encoding="utf-8") as f:
                _current_theme = json.load(f).get("theme", "dark")
        # 同步到 qfluentwidgets（懒加载）
        from qfluentwidgets import Theme, setTheme, qconfig
        qconfig.theme = Theme.DARK if _current_theme == "dark" else Theme.LIGHT
        setTheme(qconfig.theme)
    except Exception:
        pass
    return _current_theme


def set_theme(theme: str):
    global _current_theme
    _current_theme = theme
    try:
        os.makedirs(_SETTINGS_DIR, exist_ok=True)
        with open(_THEME_FILE, "w", encoding="utf-8") as f:
            json.dump({"theme": theme}, f)
        from qfluentwidgets import Theme, setTheme, qconfig
        qconfig.theme = Theme.DARK if theme == "dark" else Theme.LIGHT
        setTheme(qconfig.theme)
    except Exception:
        pass


# 向后兼容的颜色常量（惰性求值，用于 f-string 占位）
class _ThemeColor:
    def __init__(self, key):
        self._key = key
    def __str__(self):
        return _c()[self._key]
    def __repr__(self):
        return _c()[self._key]


def _c():
    """获取当前主题色板（兼容旧代码）"""
    from qfluentwidgets import Theme, qconfig
    is_dark = qconfig.theme == Theme.DARK
    return {
        "bg": "#1C1C1C" if is_dark else "#F3F3F3",
        "surface": "#2C2C2C" if is_dark else "#FFFFFF",
        "surface_hover": "#353535" if is_dark else "#F5F5F5",
        "text": "#FFFFFF" if is_dark else "#1A1A1A",
        "text_secondary": "#9E9E9E" if is_dark else "#616161",
        "text_disabled": "#5A5A5A" if is_dark else "#BDBDBD",
        "accent": "#60CDFF" if is_dark else "#0078D4",
        "accent_hover": "#78D5FF" if is_dark else "#1A86D8",
        "border": "#4D4D4D" if is_dark else "#D6D6D6",
        "border_subtle": "#424242" if is_dark else "#E8E8E8",
        "success": "#6CCB5F" if is_dark else "#0F7B0F",
        "warning": "#FCE100" if is_dark else "#9D5D00",
        "error": "#FF99A4" if is_dark else "#C42B1C",
        "card": "#2C2C2C" if is_dark else "#FFFFFF",
        "card_border": "#3A3A3A" if is_dark else "#E8E8E8",
        "flyout": "#323232" if is_dark else "#F7F7F7",
    }


BG_DARK = _ThemeColor("bg")
BG_ELEVATED = _ThemeColor("surface")
BG_HOVER = _ThemeColor("surface_hover")
TEXT_PRIMARY = _ThemeColor("text")
TEXT_SECONDARY = _ThemeColor("text_secondary")
TEXT_DISABLED = _ThemeColor("text_disabled")
PRIMARY = _ThemeColor("accent")
PRIMARY_HOVER = _ThemeColor("accent_hover")
BORDER_COLOR = _ThemeColor("border")
SUCCESS = _ThemeColor("success")
WARNING = _ThemeColor("warning")
ERROR = _ThemeColor("error")


# 兼容旧代码：样式函数（qfluentwidgets 已接管，返回空/简化样式）
def WIN10_BUTTON_STYLE():
    return ""

def WIN10_PRIMARY_BUTTON_STYLE():
    return ""

def SIDEBAR_BUTTON_STYLE():
    return ""

def MAIN_WINDOW_STYLE():
    return ""


# 辅助函数（简化为空操作或调用 qfluentwidgets 的等效功能）
def set_card_style(frame):
    """qfluentwidgets 通过 CardWidget 处理，此函数保留仅用于旧代码兼容"""
    pass

def set_card_style_no_shadow(frame):
    pass

def set_surface_style(frame):
    pass

def add_shadow(widget, **kwargs):
    pass


# 文本样式函数（保留供旧代码直接调用，但建议使用 qfluentwidgets 的 TitleLabel/BodyLabel）
def page_title_style():
    return "font-size: 28px; font-weight: 600;"

def section_title_style():
    return "font-size: 16px; font-weight: 600;"

def subtitle_style():
    return "font-size: 13px; color: #9E9E9E;"

def body_style():
    return "font-size: 13px;"

def caption_style():
    return "font-size: 12px; color: #757575;"

def accent_text_style():
    return "font-size: 13px; color: #60CDFF; font-weight: 500;"

def success_text_style():
    return "font-size: 13px; color: #6CCB5F;"

def error_text_style():
    return "font-size: 13px; color: #FF99A4;"

def stat_value_style():
    return "font-size: 24px; font-weight: 600; color: #60CDFF;"


def dark_dialog_style():
    """深色模式弹窗样式（QDialog/QMessageBox 通用）"""
    c = _c()
    return f"""
QDialog, QMessageBox {{
    background-color: {c['bg']};
    color: {c['text']};
}}
QDialog QLabel, QMessageBox QLabel {{
    color: {c['text']};
    background-color: transparent;
}}
QMessageBox QPushButton, QDialog QPushButton {{
    background-color: {c['surface']};
    color: {c['text']};
    border: 1px solid {c['border']};
    border-radius: 2px;
    padding: 6px 16px;
    min-width: 60px;
    font-family: "Segoe UI", "Microsoft YaHei";
    font-size: 13px;
}}
QMessageBox QPushButton:hover, QDialog QPushButton:hover {{
    background-color: {c['accent']};
    border-color: {c['accent']};
}}
QMessageBox QPushButton:pressed, QDialog QPushButton:pressed {{
    background-color: #005a9e;
}}
"""


def dark_msg_box(parent, icon, title, text, buttons=None):
    """创建兼容深色模式的 QMessageBox 并执行，返回用户点击结果"""
    from PyQt5.QtWidgets import QMessageBox
    if buttons is None:
        buttons = QMessageBox.Ok
    msg = QMessageBox(parent)
    msg.setIcon(icon)
    msg.setWindowTitle(title)
    msg.setText(text)
    msg.setStandardButtons(buttons)
    msg.setStyleSheet(dark_dialog_style())
    try:
        apply_dark_title_bar(msg)
    except Exception:
        pass
    return msg.exec_()


def set_item_text_color(item, column=-1):
    """为 QTreeWidgetItem 或 QListWidgetItem 设置正确的主题文字颜色"""
    from PyQt5.QtGui import QColor
    c = _c()
    color = QColor(c['text'])
    # QTreeWidgetItem.setForeground 需要 column 参数
    from PyQt5.QtWidgets import QTreeWidgetItem
    if isinstance(item, QTreeWidgetItem):
        if column >= 0:
            item.setForeground(column, color)
        else:
            for i in range(item.columnCount()):
                item.setForeground(i, color)
    else:
        item.setForeground(color)


# ─── Windows 标题栏 ──────────────────────────────────────────
def _get_hwnd(widget):
    try:
        return int(widget.winId())
    except Exception:
        return None


def apply_dark_title_bar(widget):
    hwnd = _get_hwnd(widget)
    if hwnd is None:
        return
    is_dark = 1 if get_theme() == "dark" else 0
    try:
        DWMWA_USE_IMMERSIVE_DARK_MODE = 20
        ctypes.windll.dwmapi.DwmSetWindowAttribute(
            hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE,
            ctypes.byref(ctypes.c_int(is_dark)), ctypes.sizeof(ctypes.c_int))
    except Exception:
        try:
            DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19
            ctypes.windll.dwmapi.DwmSetWindowAttribute(
                hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD,
                ctypes.byref(ctypes.c_int(is_dark)), ctypes.sizeof(ctypes.c_int))
        except Exception:
            pass
