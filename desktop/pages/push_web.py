import os
import re
import json
import subprocess
import time
import pyperclip
from urllib.parse import quote_plus
from PyQt5.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout,
                               QFrame,
                               QListWidgetItem,
                               QMessageBox, QMenu)
from PyQt5.QtCore import Qt
from PyQt5.QtGui import QFont
from styles import get_theme, _c, set_item_text_color, dark_dialog_style
from qfluentwidgets import (CardWidget, TitleLabel, BodyLabel, SubtitleLabel,
                            PushButton, PrimaryPushButton, ToolButton, ToggleButton,
                            LineEdit, CheckBox, ComboBox, setFont, FluentIcon as FIF,
                            InfoBar, InfoBarPosition, ListWidget, TextEdit)

DATA_DIR = os.path.join(os.path.expanduser("~"), "PhoneHub", "data")
HISTORY_FILE = os.path.join(DATA_DIR, "push_web_history.json")
LAST_SEND_FILE = os.path.join(DATA_DIR, "push_web_last.json")
HISTORY_LIMIT = 100

# URL 正则
URL_REGEX = re.compile(r'^(https?://|www\.)', re.IGNORECASE)
URL_REGEX2 = re.compile(r'^[a-z0-9-]+(\.[a-z0-9-]+)+', re.IGNORECASE)


class PushWebPage(QWidget):
    """推送网页"""

    def __init__(self, manager):
        super().__init__()
        self.manager = manager
        self.history = []  # 统一历史记录（发送和接收合并）
        self._last_send = None  # 上次发送记录（含 time 和 content）
        os.makedirs(DATA_DIR, exist_ok=True)
        self._load_history()
        self._load_last_send()
        self._setup_ui()
        self._connect_signals()
        self._refresh_history()
        self._refresh_last_send_label()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 16, 16, 16)
        layout.setSpacing(12)

        title = TitleLabel("推送网页")
        title.setObjectName("titleLabel")
        setFont(title, 28, QFont.Bold)
        layout.addWidget(title)

        # 接收区
        recv_frame = CardWidget()
        recv_layout = QVBoxLayout(recv_frame)
        recv_label = SubtitleLabel("接收手机推送的内容:")
        recv_layout.addWidget(recv_label)
        self.recv_text = TextEdit()
        self.recv_text.setReadOnly(True)
        self.recv_text.setMaximumHeight(80)
        c = _c()
        self.recv_text.setStyleSheet(f"background-color: {c['bg']}; color: {c['text']}; border: 1px solid {c['border']}; border-radius: 4px;")
        recv_layout.addWidget(self.recv_text)
        btn_row = QHBoxLayout()
        self.handle_btn = PrimaryPushButton("处理 (URL→Edge打开 / 文字→搜索)")
        btn_row.addWidget(self.handle_btn)
        self.copy_recv_btn = PushButton("复制")
        btn_row.addWidget(self.copy_recv_btn)
        btn_row.addStretch()
        recv_layout.addLayout(btn_row)
        layout.addWidget(recv_frame)

        # 发送区
        send_frame = CardWidget()
        send_layout = QVBoxLayout(send_frame)
        send_label = SubtitleLabel("向手机推送 (URL自动用浏览器打开, 文字通过文字互传发送):")
        send_layout.addWidget(send_label)
        self.send_input = LineEdit()
        self.send_input.setPlaceholderText("输入URL或文字...")
        send_layout.addWidget(self.send_input)
        btn_row2 = QHBoxLayout()
        self.send_btn = PrimaryPushButton("推送到手机")
        btn_row2.addWidget(self.send_btn)
        # 上次发送信息标签（显示时间和内容预览）
        self.last_send_label = BodyLabel("上次发送: 无")
        btn_row2.addWidget(self.last_send_label)
        btn_row2.addStretch()
        send_layout.addLayout(btn_row2)
        layout.addWidget(send_frame)

        # 统一历史记录列表（发送和接收合并显示）
        history_frame = CardWidget()
        history_layout = QVBoxLayout(history_frame)
        history_label = SubtitleLabel("推送历史 (电脑 -> 手机 表示发送, 电脑 <- 手机 表示接收)")
        history_layout.addWidget(history_label)
        self.history_list = ListWidget()
        self.history_list.setContextMenuPolicy(Qt.CustomContextMenu)
        history_layout.addWidget(self.history_list)
        layout.addWidget(history_frame, 1)

    def _connect_signals(self):
        self.handle_btn.clicked.connect(self._handle_received)
        self.copy_recv_btn.clicked.connect(self._copy_received)
        self.send_btn.clicked.connect(self._send_to_phone)
        # 回车键直接发送
        self.send_input.returnPressed.connect(self._send_to_phone)
        self.history_list.customContextMenuRequested.connect(self._on_history_menu)
        try:
            # 仅监听 command_received 以捕获 open_url 指令（文字/剪贴板历史由各自的页面管理）
            self.manager.command_received.connect(self._on_command_received)
            # 监听 URL 历史同步
            self.manager.url_history_sync_received.connect(self._on_url_history_sync)
            # 连接状态变化时发送自己的历史给手机
            self.manager.connection_status_changed.connect(self._on_connection_changed)
        except Exception:
            pass

    def _is_url(self, text):
        text = text.strip()
        if not text:
            return False
        if URL_REGEX.match(text):
            return True
        # 简单的域名匹配, 但排除带空格的句子
        if ' ' not in text and URL_REGEX2.match(text):
            return True
        return False

    def _normalize_url(self, text):
        text = text.strip()
        if not text.lower().startswith(('http://', 'https://')):
            return 'https://' + text
        return text

    def _open_in_edge(self, url):
        try:
            # 优先使用 Edge, 找不到则使用默认浏览器
            subprocess.Popen(['start', 'msedge', url], shell=True)
        except Exception:
            try:
                os.startfile(url)
            except Exception:
                pass

    def _handle_received(self):
        text = self.recv_text.toPlainText().strip()
        if not text:
            return
        if self._is_url(text):
            url = self._normalize_url(text)
            self._open_in_edge(url)
            self._add_history(text, "URL → Edge打开", direction="in")
        else:
            # 普通文字 → 搜索引擎
            search_url = 'https://www.bing.com/search?q=' + quote_plus(text)
            self._open_in_edge(search_url)
            self._add_history(text, "搜索 → Edge打开", direction="in")

    def _copy_received(self):
        text = self.recv_text.toPlainText()
        if text:
            try:
                pyperclip.copy(text)
            except Exception:
                pass

    def _send_to_phone(self):
        text = self.send_input.text().strip()
        if not text:
            return
        ch = self.manager.current_channel
        is_url = self._is_url(text)
        if ch == "adb":
            if is_url:
                # URL: 控制 Via 浏览器打开
                url = self._normalize_url(text)
                try:
                    self.manager.adb_command('shell', 'am', 'start', '-a', 'android.intent.action.VIEW',
                                             '-d', url, 'mark.via.gp')
                    self._add_history(text, "Via (ADB)", direction="out")
                    self._update_last_send(text)
                except Exception as e:
                    self._show_message(QMessageBox.Warning, "失败", str(e))
            else:
                # 非URL文字: 通过文字传输机制发送，不记入URL历史
                try:
                    self.manager.send_text(text)
                    self._update_last_send(text)
                    self._show_message(QMessageBox.Information, "已发送",
                                       "文字已发送到手机（文字互传），请查看手机的文字互传页面。")
                except Exception as e:
                    self._show_message(QMessageBox.Warning, "失败", str(e))
        elif ch == "wifi":
            if is_url:
                # URL: 通过 open_url action 让手机打开
                url = self._normalize_url(text)
                try:
                    self.manager.send_action("open_url", {"url": url, "open_in_via": True})
                    self._add_history(text, "浏览器 (WiFi)", direction="out")
                    self._update_last_send(text)
                except Exception as e:
                    self._show_message(QMessageBox.Warning, "失败", str(e))
            else:
                # 非URL文字: 通过文字传输机制发送，不记入URL历史
                try:
                    self.manager.send_text(text)
                    self._update_last_send(text)
                    self._show_message(QMessageBox.Information, "已发送",
                                       "文字已发送到手机（文字互传），请查看手机的文字互传页面。")
                except Exception as e:
                    self._show_message(QMessageBox.Warning, "失败", str(e))
        else:
            self._show_message(QMessageBox.Warning, "未连接", "请先连接手机 (ADB 或 WiFi)。")
        self.send_input.clear()

    def _on_command_received(self, msg_data):
        """监听手机发来的指令，捕获 open_url 等"""
        try:
            action = msg_data.get('action', '')
            if action == 'open_url':
                url = msg_data.get('url', '')
                if url:
                    self.recv_text.setPlainText(url)
                    self._add_history(url, "手机URL", direction="in")
        except Exception:
            pass

    def _on_connection_changed(self, connected, channel):
        """连接成功时发送自己的 URL 历史给手机端同步"""
        if connected:
            self._send_history_sync()

    def _send_history_sync(self):
        """发送本地 URL 历史给手机端"""
        try:
            # 转换为同步格式：{url, direction, timestamp}
            sync_data = []
            for entry in self.history:
                # 电脑端 direction: out=电脑发手机, in=手机发电脑
                # 同步给手机时统一用手机端方向标签
                direction = "电脑 -> 手机" if entry.get('direction') == 'out' else "电脑 <- 手机"
                sync_data.append({
                    'url': entry.get('content', ''),
                    'direction': direction,
                    'timestamp': int(entry.get('time', 0) * 1000)
                })
            self.manager.send_url_history(sync_data)
        except Exception:
            pass

    def _on_url_history_sync(self, remote_history):
        """收到手机端的 URL 历史，合并到本地（仅保留URL条目）"""
        try:
            changed = False
            for item in remote_history:
                url = item.get('url', '')
                direction = item.get('direction', '')
                timestamp = item.get('timestamp', 0) / 1000.0  # ms→s
                if not url or not self._is_url(url):
                    continue
                # 手机端方向标签转换为电脑端 direction
                local_dir = 'out' if direction == "电脑 -> 手机" else 'in'
                # 去重：同 URL 同方向且时间戳接近则跳过
                exists = any(
                    e.get('content') == url and e.get('direction') == local_dir
                    for e in self.history
                )
                if not exists:
                    entry = {
                        'content': url,
                        'action': '同步',
                        'direction': local_dir,
                        'time': timestamp
                    }
                    self.history.append(entry)
                    changed = True
            if changed:
                # 按时间降序排序
                self.history.sort(key=lambda e: e.get('time', 0), reverse=True)
                if len(self.history) > HISTORY_LIMIT:
                    self.history = self.history[:HISTORY_LIMIT]
                self._save_history()
                self._refresh_history()
        except Exception:
            pass

    def _add_history(self, content, action, direction="out"):
        """添加历史记录到统一列表
        direction: 'out' 表示发送到手机, 'in' 表示从手机接收
        """
        entry = {
            'content': content,
            'action': action,
            'direction': direction,
            'time': time.time()
        }
        self.history.insert(0, entry)
        if len(self.history) > HISTORY_LIMIT:
            self.history = self.history[:HISTORY_LIMIT]
        self._save_history()
        self._refresh_history()

    def _refresh_history(self):
        """刷新历史列表显示"""
        self.history_list.clear()
        for entry in self.history:
            ts = time.strftime("%m-%d %H:%M:%S", time.localtime(entry.get('time', time.time())))
            direction = entry.get('direction', 'out')
            # 方向标记
            dir_mark = "电脑 -> 手机" if direction == "out" else "电脑 <- 手机"
            # 内容预览（截断过长内容）
            content = entry.get('content', '')
            preview = content[:50].replace('\n', ' ').replace('\r', '')
            if len(content) > 50:
                preview += "..."
            action = entry.get('action', '')
            # 格式: [时间] 方向  动作  内容预览
            item_text = f"[{ts}] {dir_mark}  {action}  |  {preview}"
            item = QListWidgetItem(item_text)
            set_item_text_color(item)
            item.setData(Qt.UserRole, entry)
            # 发送和接收用不同颜色区分
            from PyQt5.QtGui import QColor, QBrush
            if direction == "out":
                c = _c()
                item.setForeground(QBrush(QColor(c['accent'])))
            else:
                c = _c()
                item.setForeground(QBrush(QColor(c['success'])))
            self.history_list.addItem(item)

    def _on_history_menu(self, pos):
        item = self.history_list.itemAt(pos)
        if not item:
            return
        entry = item.data(Qt.UserRole)
        if not entry:
            return
        menu = QMenu(self)
        c = _c()
        menu.setStyleSheet(f"""
            QMenu {{
                background-color: {c['flyout']};
                color: {c['text']};
                border: 1px solid {c['border']};
                padding: 4px;
            }}
            QMenu::item {{
                padding: 8px 20px;
                border-radius: 2px;
            }}
            QMenu::item:selected {{
                background-color: {c['surface_hover']};
                color: {c['text']};
            }}
        """)
        act_copy = menu.addAction("复制内容")
        act_redo = menu.addAction("重新处理")
        act_del = menu.addAction("删除")
        action = menu.exec_(self.history_list.mapToGlobal(pos))
        text = entry.get('content', '')
        if action == act_copy:
            try:
                pyperclip.copy(text)
            except Exception:
                pass
        elif action == act_redo:
            self.recv_text.setPlainText(text)
            self._handle_received()
        elif action == act_del:
            self.history = [e for e in self.history if e is not entry]
            self._save_history()
            self._refresh_history()

    def _load_history(self):
        """从本地JSON文件加载历史记录，仅保留URL相关的条目"""
        try:
            if os.path.exists(HISTORY_FILE):
                with open(HISTORY_FILE, 'r', encoding='utf-8') as f:
                    all_history = json.load(f)
                # 过滤：只保留URL相关的条目，去掉纯文字推送历史
                url_actions = {'URL → Edge打开', '搜索 → Edge打开', 'Via (ADB)',
                               '浏览器 (WiFi)', '手机URL', '同步', 'Via浏览器打开'}
                self.history = [
                    e for e in all_history
                    if e.get('action', '') in url_actions or self._is_url(e.get('content', ''))
                ]
        except Exception:
            self.history = []

    def _save_history(self):
        """保存历史记录到本地JSON文件"""
        try:
            with open(HISTORY_FILE, 'w', encoding='utf-8') as f:
                json.dump(self.history, f, ensure_ascii=False, indent=2)
        except Exception:
            pass

    # ==================== 上次发送记录持久化 ====================

    def _load_last_send(self):
        """从本地JSON文件加载上次发送记录"""
        try:
            if os.path.exists(LAST_SEND_FILE):
                with open(LAST_SEND_FILE, 'r', encoding='utf-8') as f:
                    self._last_send = json.load(f)
        except Exception:
            self._last_send = None

    def _update_last_send(self, content):
        """更新上次发送记录并持久化，同时刷新标签"""
        self._last_send = {'time': time.time(), 'content': content}
        try:
            with open(LAST_SEND_FILE, 'w', encoding='utf-8') as f:
                json.dump(self._last_send, f, ensure_ascii=False, indent=2)
        except Exception:
            pass
        self._refresh_last_send_label()

    def _refresh_last_send_label(self):
        """刷新上次发送标签显示"""
        if not self._last_send:
            self.last_send_label.setText("上次发送: 无")
            return
        ts = time.strftime("%m-%d %H:%M:%S", time.localtime(self._last_send.get('time', time.time())))
        content = self._last_send.get('content', '')
        preview = content[:30].replace('\n', ' ').replace('\r', '')
        if len(content) > 30:
            preview += "..."
        self.last_send_label.setText(f"上次发送: [{ts}] {preview}")

    # ==================== 深色弹窗辅助方法 ====================

    def _show_message(self, icon, title, text):
        """显示深色模式兼容的消息弹窗"""
        msg = QMessageBox(self)
        msg.setIcon(icon)
        msg.setWindowTitle(title)
        msg.setText(text)
        msg.setStyleSheet(dark_dialog_style())
        return msg.exec_()
