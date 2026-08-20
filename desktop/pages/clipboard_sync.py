import os
import json
import time
import pyperclip
from PyQt5.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout,
                               QTabWidget, QListWidgetItem,
                               QMessageBox, QMenu)
from PyQt5.QtCore import Qt
from PyQt5.QtGui import QFont
from qfluentwidgets import (CardWidget, TitleLabel, BodyLabel,
                            PushButton, PrimaryPushButton,
                            LineEdit, CheckBox, ListWidget, TextEdit,
                            TabBar,
                            setFont, FluentIcon as FIF)
from styles import get_theme, _c, set_item_text_color, dark_msg_box

DATA_DIR = os.path.join(os.path.expanduser("~"), "PhoneHub", "data")
HISTORY_FILE = os.path.join(DATA_DIR, "clipboard_history.json")
FAVORITE_FILE = os.path.join(DATA_DIR, "clipboard_favorites.json")
HISTORY_LIMIT = 500
FAVORITE_LIMIT = 50


class ClipboardSyncPage(QWidget):
    def __init__(self, manager):
        super().__init__()
        self.manager = manager
        self.clipboard_history = []
        self.clipboard_favorites = []
        self._search_keyword = ""
        self._fav_filter = False
        os.makedirs(DATA_DIR, exist_ok=True)
        self._load_history()
        self._load_favorites()
        self._setup_ui()
        self._connect_signals()
        self._refresh_history_view()
        self._refresh_favorites_view()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 20, 24, 20)
        layout.setSpacing(16)

        title = TitleLabel("剪贴板同步")
        setFont(title, 28, QFont.Bold)
        layout.addWidget(title)

        actions_frame = CardWidget()
        actions_layout = QHBoxLayout(actions_frame)
        actions_layout.setContentsMargins(16, 12, 16, 12)
        actions_layout.setSpacing(10)

        self.sync_btn = PrimaryPushButton("立即同步剪贴板")
        actions_layout.addWidget(self.sync_btn)

        self.send_fav_btn = PushButton("推送收藏到手机")
        actions_layout.addWidget(self.send_fav_btn)

        self.clear_btn = PushButton("清空历史")
        actions_layout.addWidget(self.clear_btn)

        self.search_input = LineEdit()
        self.search_input.setPlaceholderText("搜索剪贴板历史...")
        self.search_input.setMaximumWidth(300)
        actions_layout.addWidget(self.search_input)

        actions_layout.addStretch()
        layout.addWidget(actions_frame)

        content_frame = CardWidget()
        content_layout = QVBoxLayout(content_frame)
        content_layout.setContentsMargins(16, 14, 16, 14)
        content_layout.setSpacing(10)

        self.current_clip_label = BodyLabel("当前剪贴板:")
        content_layout.addWidget(self.current_clip_label)

        self.clip_content = TextEdit()
        self.clip_content.setReadOnly(True)
        self.clip_content.setMaximumHeight(140)
        content_layout.addWidget(self.clip_content)

        btn_row = QHBoxLayout()
        self.copy_btn = PushButton("复制到剪贴板")
        btn_row.addWidget(self.copy_btn)

        self.fav_btn = PushButton("★ 收藏当前")
        btn_row.addWidget(self.fav_btn)

        btn_row.addStretch()
        content_layout.addLayout(btn_row)

        layout.addWidget(content_frame)

        # Tab bar (Fluent Design)
        self.tab_bar = TabBar(self)
        self.tab_bar.addTab("history_tab", "历史记录")
        self.tab_bar.addTab("fav_tab", "收藏")
        layout.addWidget(self.tab_bar)

        # Tab content (stacked, visibility controlled by tab bar)
        history_frame = CardWidget()
        history_layout = QVBoxLayout(history_frame)
        history_layout.setContentsMargins(16, 12, 16, 12)
        history_layout.setSpacing(8)
        filter_row = QHBoxLayout()
        self.fav_filter_cb = CheckBox("仅显示收藏")
        filter_row.addWidget(self.fav_filter_cb)
        filter_row.addStretch()
        history_layout.addLayout(filter_row)
        self.history_list = ListWidget()
        self.history_list.setContextMenuPolicy(Qt.CustomContextMenu)
        history_layout.addWidget(self.history_list)
        self._tab_history_widget = history_frame

        fav_frame = CardWidget()
        fav_layout = QVBoxLayout(fav_frame)
        fav_layout.setContentsMargins(16, 12, 16, 12)
        fav_layout.setSpacing(8)
        self.favorites_list = ListWidget()
        self.favorites_list.setContextMenuPolicy(Qt.CustomContextMenu)
        fav_layout.addWidget(self.favorites_list)
        self._tab_fav_widget = fav_frame
        self._tab_fav_widget.hide()

        layout.addWidget(self._tab_history_widget)
        layout.addWidget(self._tab_fav_widget)

    def _connect_signals(self):
        self.tab_bar.currentChanged.connect(self._on_tab_changed)
        self.sync_btn.clicked.connect(self._sync_now)
        self.copy_btn.clicked.connect(self._copy_current)
        self.clear_btn.clicked.connect(self._clear_history)
        self.fav_btn.clicked.connect(self._favorite_current)
        self.send_fav_btn.clicked.connect(self._send_favorites)
        self.search_input.textChanged.connect(self._on_search_changed)
        self.fav_filter_cb.toggled.connect(self._on_fav_filter_toggled)
        self.manager.clipboard_received.connect(self._on_clipboard_received)
        self.manager.clipboard_sent.connect(self._on_clipboard_sent)
        # save.md 功能23：接收手机端收藏同步
        try:
            self.manager.clipboard_favorite_received.connect(self._on_favorite_synced)
        except Exception:
            pass
        # 接收手机端剪贴板历史同步
        try:
            self.manager.clipboard_history_received.connect(self._on_clipboard_history_received)
        except Exception:
            pass
        self.history_list.itemClicked.connect(self._on_history_clicked)
        self.history_list.itemDoubleClicked.connect(self._on_history_double_clicked)
        self.history_list.customContextMenuRequested.connect(self._on_history_menu)
        self.favorites_list.itemClicked.connect(self._on_favorite_clicked)
        self.favorites_list.itemDoubleClicked.connect(self._on_favorite_double_clicked)
        self.favorites_list.customContextMenuRequested.connect(self._on_favorite_menu)

    def _on_tab_changed(self, index):
        is_history = (index == 0)
        self._tab_history_widget.setVisible(is_history)
        self._tab_fav_widget.setVisible(not is_history)

    def _sync_now(self):
        try:
            text = pyperclip.paste()
            if text:
                self.manager.send_clipboard(text)
                self.clip_content.setPlainText(text)
        except Exception:
            pass

    def _copy_current(self):
        text = self.clip_content.toPlainText()
        if text:
            try:
                pyperclip.copy(text)
            except Exception:
                pass

    def _favorite_current(self):
        text = self.clip_content.toPlainText()
        if not text:
            return
        self._add_favorite(text, "本机")
        dark_msg_box(self, QMessageBox.Information, "收藏", "已加入收藏")

    def _send_favorites(self):
        if not self.clipboard_favorites:
            dark_msg_box(self, QMessageBox.Information, "提示", "暂无收藏内容")
            return
        for entry in self.clipboard_favorites:
            self.manager.send_clipboard(entry.get('text', ''))
        dark_msg_box(self, QMessageBox.Information, "已推送", f"已推送 {len(self.clipboard_favorites)} 条收藏到手机")

    def _clear_history(self):
        if dark_msg_box(self, QMessageBox.Question, "确认", "确定清空历史记录?", QMessageBox.Yes | QMessageBox.No) != QMessageBox.Yes:
            return
        self.clipboard_history.clear()
        self._save_history()
        self._refresh_history_view()

    def _on_search_changed(self, text):
        self._search_keyword = text.strip().lower()
        self._refresh_history_view()

    def _on_fav_filter_toggled(self, checked):
        self._fav_filter = checked
        self._refresh_history_view()

    def _on_clipboard_received(self, text, source):
        if not text:
            return
        self.clip_content.setPlainText(text)
        if source == "phone":
            try:
                self.manager._suppress_clipboard = True
                pyperclip.copy(text)
            except Exception:
                pass
            self._add_history(text, "手机")
        else:
            self._add_history(text, "本机")

    def _on_clipboard_sent(self):
        try:
            text = pyperclip.paste()
            if text:
                self._add_history(text, "本机")
        except Exception:
            pass

    def _on_favorite_synced(self, text, favorite):
        """save.md 功能23：手机端收藏变更同步到电脑端"""
        if not text:
            return
        if favorite:
            self._add_favorite(text, "手机")
        else:
            self._remove_favorite(text)

    def _on_clipboard_history_received(self, items):
        """接收手机端同步过来的剪贴板历史"""
        if not items:
            return
        for item in items:
            content = item.get('content', '')
            source = item.get('source', '手机')
            timestamp = item.get('timestamp', 0)
            favorite = item.get('favorite', False)
            if not content:
                continue
            # 避免重复添加
            existing_texts = {e.get('text') for e in self.clipboard_history}
            if content in existing_texts:
                continue
            entry = {
                'text': content,
                'source': source,
                'time': timestamp / 1000.0 if timestamp > 1e10 else timestamp,
                'fav': favorite
            }
            self.clipboard_history.insert(0, entry)
        # 限制总数
        if len(self.clipboard_history) > HISTORY_LIMIT:
            self.clipboard_history = self.clipboard_history[:HISTORY_LIMIT]
        self._save_history()
        self._refresh_history_view()

    def _add_history(self, text, source):
        entry = {'text': text, 'source': source, 'time': time.time(), 'fav': False}
        self.clipboard_history.insert(0, entry)
        if len(self.clipboard_history) > HISTORY_LIMIT:
            self.clipboard_history = self.clipboard_history[:HISTORY_LIMIT]
        self._save_history()
        self._refresh_history_view()

    def _add_favorite(self, text, source):
        for entry in self.clipboard_favorites:
            if entry.get('text') == text:
                return False
        entry = {'text': text, 'source': source, 'time': time.time()}
        self.clipboard_favorites.insert(0, entry)
        if len(self.clipboard_favorites) > FAVORITE_LIMIT:
            self.clipboard_favorites = self.clipboard_favorites[:FAVORITE_LIMIT]
        self._save_favorites()
        self._refresh_favorites_view()
        self._refresh_history_view()
        # save.md 功能23：收藏同步到对端（仅本机用户操作触发，避免与同步回环）
        if source != "手机":
            self._sync_favorite_to_phone(text, True)
        return True

    def _remove_favorite(self, text):
        self.clipboard_favorites = [e for e in self.clipboard_favorites if e.get('text') != text]
        self._save_favorites()
        self._refresh_favorites_view()
        self._refresh_history_view()
        # save.md 功能23：取消收藏同步到对端
        self._sync_favorite_to_phone(text, False)

    def _sync_favorite_to_phone(self, text, favorite):
        """推送收藏变更到手机端（save.md 功能23 双向同步）"""
        try:
            self.manager.send_action("clipboard_favorite", {
                "content": text,
                "favorite": favorite
            })
        except Exception:
            pass

    def _is_favorited(self, text):
        return any(e.get('text') == text for e in self.clipboard_favorites)

    def _format_entry(self, entry):
        text = entry.get('text', '')
        source = entry.get('source', '本机')
        star = "★" if self._is_favorited(text) else "☆"
        ts = time.strftime("%m-%d %H:%M", time.localtime(entry.get('time', time.time())))
        preview = text[:60].replace('\n', ' ')
        if len(text) > 60:
            preview += "..."
        return f"{star} [{source}] {ts}  {preview}"

    def _refresh_history_view(self):
        self.history_list.clear()
        for entry in self.clipboard_history:
            text = entry.get('text', '')
            if self._search_keyword and self._search_keyword not in text.lower():
                continue
            if self._fav_filter and not self._is_favorited(text):
                continue
            item = QListWidgetItem(self._format_entry(entry))
            set_item_text_color(item)
            item.setData(Qt.UserRole, entry)
            self.history_list.addItem(item)

    def _refresh_favorites_view(self):
        self.favorites_list.clear()
        for entry in self.clipboard_favorites:
            ts = time.strftime("%m-%d %H:%M", time.localtime(entry.get('time', time.time())))
            preview = entry.get('text', '')[:60].replace('\n', ' ')
            if len(entry.get('text', '')) > 60:
                preview += "..."
            item = QListWidgetItem(f"★ [{entry.get('source','本机')}] {ts}  {preview}")
            set_item_text_color(item)
            item.setData(Qt.UserRole, entry)
            self.favorites_list.addItem(item)

    def _on_history_clicked(self, item):
        entry = item.data(Qt.UserRole)
        if entry:
            self.clip_content.setPlainText(entry.get('text', ''))

    def _on_history_double_clicked(self, item):
        entry = item.data(Qt.UserRole)
        if entry:
            text = entry.get('text', '')
            self.clip_content.setPlainText(text)
            try:
                pyperclip.copy(text)
            except Exception:
                pass

    def _on_favorite_clicked(self, item):
        entry = item.data(Qt.UserRole)
        if entry:
            self.clip_content.setPlainText(entry.get('text', ''))

    def _on_favorite_double_clicked(self, item):
        entry = item.data(Qt.UserRole)
        if entry:
            text = entry.get('text', '')
            self.clip_content.setPlainText(text)
            try:
                pyperclip.copy(text)
            except Exception:
                pass

    def _on_history_menu(self, pos):
        item = self.history_list.itemAt(pos)
        if not item:
            return
        entry = item.data(Qt.UserRole)
        if not entry:
            return
        menu = QMenu(self)
        act_copy = menu.addAction("复制到剪贴板")
        text = entry.get('text', '')
        if self._is_favorited(text):
            act_fav = menu.addAction("取消收藏")
        else:
            act_fav = menu.addAction("★ 加入收藏")
        act_send = menu.addAction("推送到手机")
        act_del = menu.addAction("删除")
        action = menu.exec_(self.history_list.mapToGlobal(pos))
        if action == act_copy:
            try:
                pyperclip.copy(text)
            except Exception:
                pass
        elif action == act_fav:
            if self._is_favorited(text):
                self._remove_favorite(text)
            else:
                self._add_favorite(text, entry.get('source', '本机'))
        elif action == act_send:
            self.manager.send_clipboard(text)
        elif action == act_del:
            self.clipboard_history = [e for e in self.clipboard_history if e is not entry]
            self._save_history()
            self._refresh_history_view()

    def _on_favorite_menu(self, pos):
        item = self.favorites_list.itemAt(pos)
        if not item:
            return
        entry = item.data(Qt.UserRole)
        if not entry:
            return
        menu = QMenu(self)
        act_copy = menu.addAction("复制到剪贴板")
        act_send = menu.addAction("推送到手机")
        act_del = menu.addAction("移除收藏")
        action = menu.exec_(self.favorites_list.mapToGlobal(pos))
        text = entry.get('text', '')
        if action == act_copy:
            try:
                pyperclip.copy(text)
            except Exception:
                pass
        elif action == act_send:
            self.manager.send_clipboard(text)
        elif action == act_del:
            self._remove_favorite(text)

    def _load_history(self):
        try:
            if os.path.exists(HISTORY_FILE):
                with open(HISTORY_FILE, 'r', encoding='utf-8') as f:
                    self.clipboard_history = json.load(f)
        except Exception:
            self.clipboard_history = []

    def _save_history(self):
        try:
            with open(HISTORY_FILE, 'w', encoding='utf-8') as f:
                json.dump(self.clipboard_history, f, ensure_ascii=False, indent=2)
        except Exception:
            pass

    def _load_favorites(self):
        try:
            if os.path.exists(FAVORITE_FILE):
                with open(FAVORITE_FILE, 'r', encoding='utf-8') as f:
                    self.clipboard_favorites = json.load(f)
        except Exception:
            self.clipboard_favorites = []

    def _save_favorites(self):
        try:
            with open(FAVORITE_FILE, 'w', encoding='utf-8') as f:
                json.dump(self.clipboard_favorites, f, ensure_ascii=False, indent=2)
        except Exception:
            pass
