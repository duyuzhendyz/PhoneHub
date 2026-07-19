import os
import json
import time
from PyQt5.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout,
                               QFrame, QLabel, QListWidgetItem,
                               QMessageBox, QDialog,
                               QMenu, QTabWidget)
from PyQt5.QtCore import Qt
from PyQt5.QtGui import QFont
from styles import get_theme, _c, set_item_text_color, apply_dark_title_bar, dark_dialog_style, dark_msg_box
from qfluentwidgets import (CardWidget, TitleLabel, BodyLabel, SubtitleLabel,
                            PushButton, PrimaryPushButton, ToolButton, ToggleButton,
                            LineEdit, CheckBox, ComboBox, setFont, FluentIcon as FIF,
                            InfoBar, InfoBarPosition, ListWidget)

DATA_DIR = os.path.join(os.path.expanduser("~"), "PhoneHub", "data")
NOTIFICATION_FILE = os.path.join(DATA_DIR, "notifications_history.json")
BLACKLIST_FILE = os.path.join(DATA_DIR, "notifications_blacklist.json")
HISTORY_LIMIT = 200


def TAB_STYLE():
    c = _c()
    return f"""
QTabWidget::pane {{
    border: 1px solid {c['border']};
    border-radius: 6px;
    background-color: {c['surface']};
    top: -1px;
}}
QTabBar::tab {{
    background-color: {c['bg']};
    color: {c['text_secondary']};
    padding: 8px 20px;
    border: 1px solid {c['border']};
    border-bottom: none;
    border-top-left-radius: 6px;
    border-top-right-radius: 6px;
    margin-right: 2px;
    font-size: 13px;
}}
QTabBar::tab:selected {{
    background-color: {c['surface']};
    color: {c['text']};
    border-bottom: 2px solid {c['accent']};
}}
QTabBar::tab:hover {{
    color: {c['text']};
}}
"""


class NotificationPopupDialog(QDialog):
    def __init__(self, notif, parent=None):
        super().__init__(parent)
        self.setWindowTitle("通知详情")
        self.notif = notif
        self._action_result = None
        self._open_app = False
        self._delete = False
        self._manager = None
        if parent and hasattr(parent, 'manager'):
            self._manager = parent.manager
        self.setStyleSheet(dark_dialog_style())
        self._setup_ui()

    def showEvent(self, event):
        super().showEvent(event)
        try:
            apply_dark_title_bar(self)
        except Exception:
            pass

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(10)

        app_label = BodyLabel(f"应用: {self.notif.get('package', '')}")
        layout.addWidget(app_label)

        title = SubtitleLabel(self.notif.get('title', '(无标题)'))
        title.setWordWrap(True)
        layout.addWidget(title)

        text = BodyLabel(self.notif.get('text', ''))
        text.setWordWrap(True)
        layout.addWidget(text)

        actions = self.notif.get('actions', [])
        if actions:
            btn_row = QHBoxLayout()
            for act in actions:
                name = act.get('title', '') if isinstance(act, dict) else str(act)
                act_btn = PushButton(name)
                act_btn.clicked.connect(lambda _, a=act: self._do_action(a))
                btn_row.addWidget(act_btn)
            btn_row.addStretch()
            layout.addLayout(btn_row)

        bottom_row = QHBoxLayout()
        self.open_app_btn = PrimaryPushButton("打开应用并投屏")
        self.open_app_btn.clicked.connect(self._open_app_and_mirror)
        bottom_row.addWidget(self.open_app_btn)

        self.delete_btn = PushButton("删除手机端通知")
        self.delete_btn.clicked.connect(self._delete_notification)
        bottom_row.addWidget(self.delete_btn)

        self.dismiss_btn = PushButton("关闭")
        self.dismiss_btn.clicked.connect(self.reject)
        bottom_row.addWidget(self.dismiss_btn)
        layout.addLayout(bottom_row)

    def _do_action(self, action):
        """点击快捷操作按钮：直接发送命令，不关闭弹窗"""
        if self._manager:
            try:
                action_title = action.get('title', '') if isinstance(action, dict) else str(action)
                self._manager.send_action("notification_action", {
                    "package": self.notif.get('package', ''),
                    "action_title": action_title,
                    "pkg": action.get('pkg', '') if isinstance(action, dict) else '',
                    "tag": action.get('tag', '') if isinstance(action, dict) else '',
                    "id": action.get('id', 0) if isinstance(action, dict) else 0
                })
            except Exception:
                pass

    def _delete_notification(self):
        self._delete = True
        self.accept()

    def _open_app_and_mirror(self):
        self._open_app = True
        self.accept()

    def get_result(self):
        return self._action_result, self._open_app, self._delete


class BlacklistDialog(QDialog):
    def __init__(self, blacklist, parent=None):
        super().__init__(parent)
        self.setWindowTitle("黑名单管理")
        self.blacklist = list(blacklist)
        self.setStyleSheet(dark_dialog_style())
        self._setup_ui()

    def showEvent(self, event):
        super().showEvent(event)
        try:
            apply_dark_title_bar(self)
        except Exception:
            pass

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(10)
        layout.addWidget(QLabel("以下应用的通知将被过滤(留空表示接收全部):"))

        row = QHBoxLayout()
        self.input = LineEdit()
        self.input.setPlaceholderText("应用包名, 如 com.tencent.mm")
        row.addWidget(self.input)
        add_btn = PushButton("添加")
        add_btn.clicked.connect(self._add)
        row.addWidget(add_btn)
        layout.addLayout(row)

        self.list_widget = ListWidget()
        self.list_widget.setContextMenuPolicy(Qt.CustomContextMenu)
        self.list_widget.customContextMenuRequested.connect(self._on_menu)
        for app in self.blacklist:
            item = QListWidgetItem(app)
            set_item_text_color(item)
            self.list_widget.addItem(item)
        layout.addWidget(self.list_widget)

        btn_row = QHBoxLayout()
        save_btn = PrimaryPushButton("保存")
        save_btn.clicked.connect(self.accept)
        btn_row.addStretch()
        btn_row.addWidget(save_btn)
        layout.addLayout(btn_row)

    def _add(self):
        text = self.input.text().strip()
        if text and text not in self.blacklist:
            self.blacklist.append(text)
            item = QListWidgetItem(text)
            set_item_text_color(item)
            self.list_widget.addItem(item)
            self.input.clear()

    def _on_menu(self, pos):
        item = self.list_widget.itemAt(pos)
        if not item:
            return
        menu = QMenu(self)
        act_del = menu.addAction("删除")
        if menu.exec_(self.list_widget.mapToGlobal(pos)) == act_del:
            row = self.list_widget.row(item)
            self.list_widget.takeItem(row)
            self.blacklist.pop(row)


class NotificationsPage(QWidget):
    """通知读取 - 分当前活动通知和历史记录两个Tab"""

    def __init__(self, manager):
        super().__init__()
        self.manager = manager
        self.history = []           # 全部历史记录（持久化）
        self.active_notifs = {}     # 当前活动通知 key -> notif
        self.blacklist = []         # 黑名单：其中的应用通知将被过滤（留空表示接收全部）
        self._search_keyword = ""
        os.makedirs(DATA_DIR, exist_ok=True)
        self._load_history()
        self._load_blacklist()
        self._setup_ui()
        self._connect_signals()
        self._refresh_history_list()
        self._refresh_active_list()
        # 通知权限完全由手机端用户手动点击按钮开启，PC 端不再发送 request_notif_permission

    def showEvent(self, event):
        """每次显示通知页时刷新活动通知"""
        super().showEvent(event)
        # 用户进入通知页时刷新活动通知（不自动请求权限）
        self._request_active_notifications()

    def _request_active_notifications(self):
        """请求手机端所有活动通知（刷新时先清空旧列表，手机端会重新发送全部活动通知）"""
        try:
            # 清空旧列表，手机端发来的通知会重新填充
            self.active_notifs.clear()
            self._refresh_active_list()
            if getattr(self.manager, 'phone_connected', False):
                self.manager.send_action("get_active_notifications")
        except Exception:
            pass

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 16, 16, 16)
        layout.setSpacing(12)

        title = TitleLabel("通知读取")
        title.setObjectName("titleLabel")
        setFont(title, 28, QFont.Bold)
        layout.addWidget(title)

        # 控制栏
        ctrl_frame = CardWidget()
        ctrl_layout = QHBoxLayout(ctrl_frame)
        ctrl_layout.setContentsMargins(14, 10, 14, 10)
        ctrl_layout.setSpacing(10)

        self.refresh_btn = PrimaryPushButton("刷新活动通知")
        ctrl_layout.addWidget(self.refresh_btn)

        self.blacklist_btn = PushButton("黑名单设置")
        ctrl_layout.addWidget(self.blacklist_btn)

        self.clear_btn = PushButton("清空历史")
        ctrl_layout.addWidget(self.clear_btn)

        ctrl_layout.addStretch()

        self.search_input = LineEdit()
        self.search_input.setPlaceholderText("搜索通知...")
        self.search_input.setMaximumWidth(250)
        ctrl_layout.addWidget(self.search_input)
        layout.addWidget(ctrl_frame)

        # Tab 容器
        self.tab_widget = QTabWidget()
        self.tab_widget.setStyleSheet(TAB_STYLE())

        # Tab1: 当前活动通知
        active_tab = QWidget()
        active_layout = QVBoxLayout(active_tab)
        active_layout.setContentsMargins(10, 10, 10, 10)
        active_layout.setSpacing(8)

        active_hint = BodyLabel("当前手机端正在显示的通知，可远程操作按钮、删除通知")
        active_layout.addWidget(active_hint)

        self.active_list = ListWidget()
        self.active_list.setContextMenuPolicy(Qt.CustomContextMenu)
        active_layout.addWidget(self.active_list)

        self.tab_widget.addTab(active_tab, "当前活动通知")

        # Tab2: 历史记录
        history_tab = QWidget()
        hist_layout = QVBoxLayout(history_tab)
        hist_layout.setContentsMargins(10, 10, 10, 10)
        hist_layout.setSpacing(8)

        hist_hint = BodyLabel("所有收到过的通知历史（仅本地保存）")
        hist_layout.addWidget(hist_hint)

        self.history_list = ListWidget()
        self.history_list.setContextMenuPolicy(Qt.CustomContextMenu)
        hist_layout.addWidget(self.history_list)

        self.tab_widget.addTab(history_tab, "历史记录")

        layout.addWidget(self.tab_widget, 1)

    def _connect_signals(self):
        self.refresh_btn.clicked.connect(self._request_active_notifications)
        self.blacklist_btn.clicked.connect(self._open_blacklist)
        self.clear_btn.clicked.connect(self._clear_history)
        self.search_input.textChanged.connect(self._on_search_changed)

        # 当前活动通知列表
        self.active_list.itemClicked.connect(self._on_active_item_clicked)
        self.active_list.customContextMenuRequested.connect(self._on_active_menu)

        # 历史记录列表
        self.history_list.itemClicked.connect(self._on_history_item_clicked)
        self.history_list.customContextMenuRequested.connect(self._on_history_menu)

        try:
            self.manager.notification_received.connect(self._on_notification_received)
        except Exception:
            pass
        try:
            self.manager.connection_status_changed.connect(self._on_connection_changed)
        except Exception:
            pass

    def _on_connection_changed(self, connected, channel):
        """手机连接状态变化回调，连接后请求所有活动通知"""
        if connected:
            self._request_active_notifications()

    def _is_blacklisted(self, package):
        """判断应用是否在黑名单中（在黑名单中则应过滤掉，返回 True）"""
        return package in self.blacklist

    def _notif_key(self, notif):
        """获取通知唯一标识，用于去重"""
        return notif.get('key') or notif.get('id') or f"{notif.get('package', '')}_{notif.get('sbn_id', 0)}_{notif.get('sbn_tag', '')}"

    def _on_notification_received(self, notif):
        """收到通知（新通知推送或活动通知批量上报）"""
        if not isinstance(notif, dict):
            return
        package = notif.get('package', '')
        # 黑名单内的应用通知不显示、不发送
        if self._is_blacklisted(package):
            return
        # 补时间戳
        if not notif.get('time'):
            notif['time'] = time.time()

        key = self._notif_key(notif)

        # 判断是活动通知批量上报还是单条新通知推送
        is_batch = notif.get('_batch', False)
        if is_batch:
            # 批量上报：替换整个活动通知列表
            # 由调用方在循环外处理
            pass

        # 更新活动通知字典
        is_cleared = notif.get('cleared', False)
        if is_cleared and key in self.active_notifs:
            del self.active_notifs[key]
        else:
            self.active_notifs[key] = notif

        # 加入历史记录（去重）
        self.history = [n for n in self.history if self._notif_key(n) != key]
        self.history.insert(0, notif)
        if len(self.history) > HISTORY_LIMIT:
            self.history = self.history[:HISTORY_LIMIT]
        self._save_history()

        self._refresh_active_list()
        self._refresh_history_list()

    def _cancel_notification(self, notif):
        """删除手机端通知"""
        try:
            self.manager.send_action("cancel_notification", {
                "key": notif.get('key', ''),
                "pkg": notif.get('package', '') or notif.get('packageName', ''),
                "tag": notif.get('tag', '') or notif.get('sbn_tag', ''),
                "id": notif.get('id', 0) or notif.get('sbn_id', 0)
            })
            # 本地也移除
            key = self._notif_key(notif)
            if key in self.active_notifs:
                del self.active_notifs[key]
                self._refresh_active_list()
        except Exception:
            pass

    def _exec_notif_action(self, notif, action, open_app, delete):
        """执行通知弹窗返回的操作"""
        if action:
            try:
                action_title = action.get('title', '') if isinstance(action, dict) else str(action)
                self.manager.send_action("notification_action", {
                    "package": notif.get('package', ''),
                    "action_title": action_title,
                    "pkg": action.get('pkg', '') if isinstance(action, dict) else '',
                    "tag": action.get('tag', '') if isinstance(action, dict) else '',
                    "id": action.get('id', 0) if isinstance(action, dict) else 0
                })
            except Exception:
                pass
        if delete:
            self._cancel_notification(notif)
        if open_app:
            try:
                self.manager.send_command("open_app",
                                          extra={"package": notif.get('package', ''), "mirror": True})
            except Exception:
                pass

    def _on_search_changed(self, text):
        self._search_keyword = text.strip().lower()
        self._refresh_active_list()
        self._refresh_history_list()

    def _format_notif(self, notif):
        ts = time.strftime("%H:%M:%S", time.localtime(notif.get('time', time.time())))
        title = notif.get('title', '') or '(无标题)'
        text = notif.get('text', '') or ''
        package = notif.get('package', '')
        # 取包名最后一段
        pkg_short = package.split('.')[-1] if package else ''
        preview = text[:40].replace('\n', ' ')
        if len(text) > 40:
            preview += "..."
        return f"[{ts}] [{pkg_short}] {title} — {preview}"

    def _match_search(self, notif):
        if not self._search_keyword:
            return True
        text_full = (notif.get('title', '') + ' ' + notif.get('text', '') + ' ' + notif.get('package', '')).lower()
        return self._search_keyword in text_full

    def _refresh_active_list(self):
        self.active_list.clear()
        for key, notif in self.active_notifs.items():
            if not self._match_search(notif):
                continue
            item = QListWidgetItem(self._format_notif(notif))
            set_item_text_color(item)
            item.setData(Qt.UserRole, notif)
            self.active_list.addItem(item)

    def _refresh_history_list(self):
        self.history_list.clear()
        for notif in self.history:
            if not self._match_search(notif):
                continue
            item = QListWidgetItem(self._format_notif(notif))
            set_item_text_color(item)
            item.setData(Qt.UserRole, notif)
            self.history_list.addItem(item)

    def _on_active_item_clicked(self, item):
        """点击当前活动通知 → 弹出详情，可远程操作"""
        notif = item.data(Qt.UserRole)
        if notif:
            dlg = NotificationPopupDialog(notif, self)
            dlg.exec_()
            action, open_app, delete = dlg.get_result()
            # 快捷操作已在弹窗内直接执行，不再重复处理
            if open_app:
                self._exec_notif_action(notif, None, True, False)
            if delete:
                self._cancel_notification(notif)
                self.history = [n for n in self.history if n is not notif]
                self._save_history()
                self._refresh_history_list()

    def _on_history_item_clicked(self, item):
        """点击历史通知 → 仅查看详情，不做远程操作"""
        notif = item.data(Qt.UserRole)
        if notif:
            dlg = NotificationPopupDialog(notif, self)
            # 历史记录禁用删除按钮（只看详情）
            dlg.delete_btn.setEnabled(False)
            dlg.open_app_btn.setEnabled(False)
            dlg.exec_()

    def _on_active_menu(self, pos):
        item = self.active_list.itemAt(pos)
        if not item:
            return
        notif = item.data(Qt.UserRole)
        if not notif:
            return
        menu = QMenu(self)
        c = _c()
        menu.setStyleSheet(f"""
            QMenu {{
                background-color: {c['surface']};
                color: {c['text']};
                border: 1px solid {c['border']};
                padding: 4px;
            }}
            QMenu::item {{
                padding: 8px 20px;
                border-radius: 2px;
            }}
            QMenu::item:selected {{
                background-color: {c['accent']};
                color: {c['text']};
            }}
        """)
        act_del_phone = menu.addAction("删除手机端通知")
        act_del_local = menu.addAction("仅移除本地显示")
        action = menu.exec(self.active_list.mapToGlobal(pos))
        if action == act_del_phone:
            self._cancel_notification(notif)
            self.history = [n for n in self.history if n is not notif]
            self._save_history()
            self._refresh_history_list()
        elif action == act_del_local:
            key = self._notif_key(notif)
            if key in self.active_notifs:
                del self.active_notifs[key]
                self._refresh_active_list()

    def _on_history_menu(self, pos):
        item = self.history_list.itemAt(pos)
        if not item:
            return
        notif = item.data(Qt.UserRole)
        if not notif:
            return
        menu = QMenu(self)
        c = _c()
        menu.setStyleSheet(f"""
            QMenu {{
                background-color: {c['surface']};
                color: {c['text']};
                border: 1px solid {c['border']};
                padding: 4px;
            }}
            QMenu::item {{
                padding: 8px 20px;
                border-radius: 2px;
            }}
            QMenu::item:selected {{
                background-color: {c['accent']};
                color: {c['text']};
            }}
        """)
        act_del = menu.addAction("删除本条历史")
        action = menu.exec_(self.history_list.mapToGlobal(pos))
        if action == act_del:
            self.history = [n for n in self.history if n is not notif]
            self._save_history()
            self._refresh_history_list()

    def _clear_history(self):
        if dark_msg_box(self, QMessageBox.Question, "确认", "清空所有本地通知历史?",
                         QMessageBox.Yes | QMessageBox.No) != QMessageBox.Yes:
            return
        self.history.clear()
        self._save_history()
        self._refresh_history_list()

    def _open_blacklist(self):
        dlg = BlacklistDialog(self.blacklist, self)
        if dlg.exec_() == QDialog.Accepted:
            self.blacklist = dlg.blacklist
            self._save_blacklist()

    def _load_history(self):
        try:
            if os.path.exists(NOTIFICATION_FILE):
                with open(NOTIFICATION_FILE, 'r', encoding='utf-8') as f:
                    self.history = json.load(f)
        except Exception:
            self.history = []

    def _save_history(self):
        try:
            with open(NOTIFICATION_FILE, 'w', encoding='utf-8') as f:
                json.dump(self.history, f, ensure_ascii=False, indent=2)
        except Exception:
            pass

    def _load_blacklist(self):
        try:
            if os.path.exists(BLACKLIST_FILE):
                with open(BLACKLIST_FILE, 'r', encoding='utf-8') as f:
                    self.blacklist = json.load(f)
        except Exception:
            self.blacklist = []

    def _save_blacklist(self):
        try:
            with open(BLACKLIST_FILE, 'w', encoding='utf-8') as f:
                json.dump(self.blacklist, f, ensure_ascii=False, indent=2)
        except Exception:
            pass
