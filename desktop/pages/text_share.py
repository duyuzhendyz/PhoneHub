import os
import json
import time
import pyperclip
from PyQt5.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout,
                               QListWidgetItem,
                               QFileDialog, QMessageBox,
                               QMenu)
from PyQt5.QtCore import Qt, QMimeData, QUrl
from PyQt5.QtGui import QDrag, QIcon, QFont
from qfluentwidgets import (CardWidget, TitleLabel, BodyLabel,
                            PushButton, PrimaryPushButton,
                            LineEdit, ListWidget, TextEdit,
                            setFont, FluentIcon as FIF)
from styles import get_theme, _c, set_item_text_color, dark_dialog_style, dark_msg_box

DATA_DIR = os.path.join(os.path.expanduser("~"), "PhoneHub", "data")
HISTORY_FILE = os.path.join(DATA_DIR, "text_history.json")
HISTORY_LIMIT = 100


class TextSharePage(QWidget):
    def __init__(self, manager):
        super().__init__()
        self.manager = manager
        self.text_history = []
        self._search_keyword = ""
        self._last_recv_filename = None  # 最近一次接收到的文件名
        os.makedirs(DATA_DIR, exist_ok=True)
        self._load_history()
        self._setup_ui()
        self._connect_signals()
        self._refresh_history_view()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 16, 16, 16)
        layout.setSpacing(12)

        title = TitleLabel("文字互传")
        setFont(title, 28, QFont.Bold)
        layout.addWidget(title)

        send_frame = CardWidget()
        send_layout = QVBoxLayout(send_frame)
        send_layout.setContentsMargins(14, 14, 14, 14)
        send_layout.setSpacing(10)

        name_row = QHBoxLayout()
        name_label = BodyLabel("文件名(可选):")
        name_row.addWidget(name_label, alignment=Qt.AlignVCenter)
        self.filename_input = LineEdit()
        self.filename_input.setPlaceholderText("留空则用时间戳命名")
        name_row.addWidget(self.filename_input)
        send_layout.addLayout(name_row)

        input_label = BodyLabel("输入要发送的文字:")
        send_layout.addWidget(input_label)
        self.text_input = TextEdit()
        self.text_input.setPlaceholderText("在此输入文字...")
        self.text_input.setMinimumHeight(120)
        self.text_input.document().setDocumentMargin(8)
        send_layout.addWidget(self.text_input)

        btn_row = QHBoxLayout()
        self.send_btn = PrimaryPushButton("发送文字")
        btn_row.addWidget(self.send_btn)
        self.clear_input_btn = PushButton("清空输入")
        btn_row.addWidget(self.clear_input_btn)
        btn_row.addStretch()
        send_layout.addLayout(btn_row)

        layout.addWidget(send_frame)

        recv_frame = CardWidget()
        recv_layout = QVBoxLayout(recv_frame)
        recv_layout.setContentsMargins(14, 14, 14, 14)
        recv_layout.setSpacing(10)

        recv_label = BodyLabel("收到的文字:")
        recv_layout.addWidget(recv_label)

        self.recv_text = TextEdit()
        self.recv_text.setReadOnly(True)
        self.recv_text.setMinimumHeight(120)
        self.recv_text.document().setDocumentMargin(8)
        recv_layout.addWidget(self.recv_text)

        recv_btn_row = QHBoxLayout()
        self.copy_recv_btn = PushButton("复制内容")
        recv_btn_row.addWidget(self.copy_recv_btn)
        self.save_recv_btn = PrimaryPushButton("保存为TXT")
        recv_btn_row.addWidget(self.save_recv_btn)
        recv_btn_row.addStretch()
        recv_layout.addLayout(recv_btn_row)

        layout.addWidget(recv_frame)

        history_frame = CardWidget()
        history_layout = QVBoxLayout(history_frame)
        history_layout.setContentsMargins(14, 14, 14, 14)
        history_layout.setSpacing(10)
        hist_header = QHBoxLayout()
        hist_header.addWidget(BodyLabel("历史记录"))
        self.search_input = LineEdit()
        self.search_input.setPlaceholderText("搜索历史...")
        self.search_input.setMaximumWidth(250)
        hist_header.addStretch()
        hist_header.addWidget(self.search_input)
        history_layout.addLayout(hist_header)
        self.history_list = ListWidget()
        self.history_list.setContextMenuPolicy(Qt.CustomContextMenu)
        history_layout.addWidget(self.history_list)
        layout.addWidget(history_frame)

    def _connect_signals(self):
        self.send_btn.clicked.connect(self._send_text)
        self.clear_input_btn.clicked.connect(lambda: self.text_input.clear())
        self.copy_recv_btn.clicked.connect(self._copy_received)
        self.save_recv_btn.clicked.connect(self._save_received)
        self.manager.text_received.connect(self._on_text_received)
        self.history_list.itemClicked.connect(self._on_history_clicked)
        self.history_list.customContextMenuRequested.connect(self._on_history_menu)
        self.search_input.textChanged.connect(self._on_search_changed)

    def _send_text(self):
        text = self.text_input.toPlainText()
        if text:
            filename = self.filename_input.text().strip() or None
            self.manager.send_text(text, filename)
            self._add_history(text, filename, "本机")
            self.text_input.clear()
            self.filename_input.clear()

    def _copy_received(self):
        text = self.recv_text.toPlainText()
        if text:
            try:
                pyperclip.copy(text)
            except Exception:
                pass

    def _ensure_txt_suffix(self, name):
        """保留原文件名后缀，无后缀时才补 .txt"""
        if not name:
            return f"text_{int(time.time())}.txt"
        # 如果已有后缀（如 .md .json），直接使用；否则补 .txt
        if '.' in name and not name.endswith('.') and name.rsplit('.', 1)[-1].isalnum():
            return name
        return name + '.txt'

    def _save_received(self):
        text = self.recv_text.toPlainText()
        if not text:
            return
        # 使用接收到的文件名作为默认名，保留原后缀（手机端发什么就存什么）
        default_name = self._ensure_txt_suffix(self._last_recv_filename)
        # 过滤器支持所有文件，避免强制改成 .txt
        file_path, _ = QFileDialog.getSaveFileName(self, "保存文件", default_name, "所有文件 (*);;文本文件 (*.txt)")
        if file_path:
            try:
                with open(file_path, 'w', encoding='utf-8') as f:
                    f.write(text)
            except Exception as e:
                dark_msg_box(self, QMessageBox.Warning, "保存失败", str(e))

    def _on_text_received(self, text, filename):
        self.recv_text.setPlainText(text)
        # 记录接收到的文件名，供保存为TXT使用
        self._last_recv_filename = filename
        # filename 随历史记录一起保存
        self._add_history(text, filename, "手机")

    def _add_history(self, text, filename, source):
        entry = {'text': text, 'filename': filename, 'source': source, 'time': time.time()}
        self.text_history.insert(0, entry)
        if len(self.text_history) > HISTORY_LIMIT:
            self.text_history = self.text_history[:HISTORY_LIMIT]
        self._save_history()
        self._refresh_history_view()

    def _format_entry(self, entry):
        ts = time.strftime("%m-%d %H:%M", time.localtime(entry.get('time', time.time())))
        preview = entry.get('text', '')[:40].replace('\n', ' ')
        if len(entry.get('text', '')) > 40:
            preview += "..."
        return f"[{entry.get('source','本机')}] {ts}  {entry.get('filename') or '(无标题)'} - {preview}"

    def _refresh_history_view(self):
        self.history_list.clear()
        for entry in self.text_history:
            text = entry.get('text', '')
            if self._search_keyword and self._search_keyword not in text.lower():
                continue
            item = QListWidgetItem(self._format_entry(entry))
            set_item_text_color(item)
            item.setData(Qt.UserRole, entry)
            self.history_list.addItem(item)

    def _on_search_changed(self, text):
        self._search_keyword = text.strip().lower()
        self._refresh_history_view()

    def _on_history_clicked(self, item):
        entry = item.data(Qt.UserRole)
        if entry:
            self.recv_text.setPlainText(entry.get('text', ''))

    def _on_history_menu(self, pos):
        item = self.history_list.itemAt(pos)
        if not item:
            return
        entry = item.data(Qt.UserRole)
        if not entry:
            return
        menu = QMenu(self)
        act_copy = menu.addAction("复制")
        act_save = menu.addAction("保存为TXT")
        act_resend = menu.addAction("重新发送")
        act_del = menu.addAction("删除")
        action = menu.exec_(self.history_list.mapToGlobal(pos))
        text = entry.get('text', '')
        if action == act_copy:
            try:
                pyperclip.copy(text)
            except Exception:
                pass
        elif action == act_save:
            # 使用历史记录中的文件名，保留原后缀
            default_name = self._ensure_txt_suffix(entry.get('filename'))
            file_path, _ = QFileDialog.getSaveFileName(self, "保存文件", default_name, "所有文件 (*);;文本文件 (*.txt)")
            if file_path:
                try:
                    with open(file_path, 'w', encoding='utf-8') as f:
                        f.write(text)
                except Exception:
                    pass
        elif action == act_resend:
            self.manager.send_text(text, entry.get('filename'))
        elif action == act_del:
            self.text_history = [e for e in self.text_history if e is not entry]
            self._save_history()
            self._refresh_history_view()

    def _load_history(self):
        try:
            if os.path.exists(HISTORY_FILE):
                with open(HISTORY_FILE, 'r', encoding='utf-8') as f:
                    self.text_history = json.load(f)
        except Exception:
            self.text_history = []

    def _save_history(self):
        try:
            with open(HISTORY_FILE, 'w', encoding='utf-8') as f:
                json.dump(self.text_history, f, ensure_ascii=False, indent=2)
        except Exception:
            pass
