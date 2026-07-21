import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


def main():
    from PyQt5.QtWidgets import QApplication, QSystemTrayIcon, QMenu, QAction, QInputDialog
    from PyQt5.QtGui import QFont, QIcon
    from PyQt5.QtCore import Qt

    # 必须在导入任何 qfluentwidgets 模块之前创建 QApplication
    app = QApplication(sys.argv)
    app.setApplicationName("PhoneHub")
    app.setOrganizationName("PhoneHub")
    app.setFont(QFont("Segoe UI Variable", 9))

    # 设置主题（首次触发 qfluentwidgets 导入，此时 QApplication 已存在）
    from styles import get_theme, _c
    get_theme()

    # 导入主窗口（触发所有页面模块的 qfluentwidgets 导入）
    from main_window import MainWindow

    window = MainWindow()
    # 启动后不显示主窗口，最小化到托盘
    window.hide()

    # ===== 系统托盘图标 =====
    icon_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "icon.ico")
    if os.path.exists(icon_path):
        tray_icon_icon = QIcon(icon_path)
    else:
        tray_icon_icon = window.windowIcon()
    tray_icon = QSystemTrayIcon(tray_icon_icon, app)
    tray_icon.setToolTip("PhoneHub")
    tray_icon.activated.connect(lambda reason: (
        window.showNormal(), window.activateWindow(), window.raise_()
    ) if reason in (QSystemTrayIcon.DoubleClick, QSystemTrayIcon.Trigger) else None)

    # 深色主题右键菜单
    c = _c()
    tray_menu = QMenu()
    tray_menu.setStyleSheet(f"""
        QMenu {{
            background-color: {c['surface']};
            color: {c['text']};
            border: 1px solid {c['border']};
            border-radius: 4px;
            padding: 4px;
        }}
        QMenu::item {{
            padding: 6px 24px 6px 12px;
            border-radius: 3px;
        }}
        QMenu::item:selected {{
            background-color: {c['accent']};
            color: #ffffff;
        }}
        QMenu::separator {{
            height: 1px;
            background: {c['border']};
            margin: 4px 8px;
        }}
    """)

    def _show_window():
        window.showNormal()
        window.activateWindow()
        window.raise_()

    action_show = QAction("显示主窗口", app)
    action_show.triggered.connect(_show_window)
    tray_menu.addAction(action_show)

    tray_menu.addSeparator()

    action_send_text = QAction("发送文字", app)
    action_send_text.triggered.connect(lambda: _tray_send_text(window))
    tray_menu.addAction(action_send_text)

    action_send_clip = QAction("推送剪贴板", app)
    action_send_clip.triggered.connect(lambda: _tray_send_clipboard(window))
    tray_menu.addAction(action_send_clip)

    tray_menu.addSeparator()

    action_quit = QAction("退出", app)
    action_quit.triggered.connect(lambda: (window.manager.stop(), app.quit()))
    tray_menu.addAction(action_quit)

    tray_icon.setContextMenu(tray_menu)
    tray_icon.show()

    sys.exit(app.exec_())


def _tray_send_text(window):
    """托盘快捷发送文字"""
    from PyQt5.QtWidgets import QInputDialog, QLineEdit
    text, ok = QInputDialog.getText(None, "发送文字到手机", "输入内容:", QLineEdit.Normal)
    if ok and text:
        window.manager.send_text(text)
        window.text_page.recv_text.setPlainText(text)


def _tray_send_clipboard(window):
    """托盘推送剪贴板"""
    import pyperclip
    try:
        text = pyperclip.paste()
        if text:
            window.manager.send_clipboard(text)
    except Exception:
        pass


if __name__ == "__main__":
    main()
