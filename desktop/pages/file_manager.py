import os
import time
from PyQt5.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout,
                               QListWidgetItem,
                               QMessageBox, QMenu, QInputDialog,
                               QTreeWidgetItem, QHeaderView,
                               QFileDialog)
from PyQt5.QtCore import Qt, QTimer
from PyQt5.QtGui import QFont
from qfluentwidgets import (CardWidget, TitleLabel, BodyLabel,
                            PushButton, PrimaryPushButton,
                            LineEdit, TreeWidget,
                            setFont, FluentIcon as FIF)
from styles import get_theme, _c, dark_dialog_style

# 可直接打开的文本文件扩展名
TEXT_FILE_EXTS = {
    'txt', 'log', 'md', 'py', 'pyw', 'js', 'ts', 'jsx', 'tsx', 'java', 'kt',
    'cpp', 'c', 'h', 'hpp', 'cs', 'go', 'rs', 'rb', 'php', 'swift', 'm',
    'xml', 'json', 'yaml', 'yml', 'toml', 'ini', 'cfg', 'conf', 'csv',
    'html', 'htm', 'css', 'scss', 'less', 'sql', 'sh', 'bat', 'ps1',
    'r', 'lua', 'pl', 'dart', 'scala', 'groovy', 'vue', 'svelte',
}


class FileManagerPage(QWidget):
    """远程文件管理"""

    def __init__(self, manager):
        super().__init__()
        self.manager = manager
        self.current_path = "/sdcard/"
        self._setup_ui()
        self._connect_signals()
        # 页面初始化时立即显示内容
        self._refresh()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 16, 16, 16)
        layout.setSpacing(10)

        title = TitleLabel("远程文件管理")
        setFont(title, 28, QFont.Bold)
        layout.addWidget(title)

        self.channel_label = BodyLabel("当前通道: --")
        layout.addWidget(self.channel_label)

        path_frame = CardWidget()
        path_layout = QHBoxLayout(path_frame)
        self.up_btn = PushButton("上级")
        path_layout.addWidget(self.up_btn)
        self.path_input = LineEdit()
        self.path_input.setText(self.current_path)
        path_layout.addWidget(self.path_input, 1)
        self.go_btn = PrimaryPushButton("前往")
        path_layout.addWidget(self.go_btn)
        self.refresh_btn = PushButton("刷新")
        path_layout.addWidget(self.refresh_btn)
        layout.addWidget(path_frame)

        actions_frame = CardWidget()
        actions_layout = QHBoxLayout(actions_frame)
        self.mkdir_btn = PushButton("新建文件夹")
        actions_layout.addWidget(self.mkdir_btn)
        self.upload_btn = PrimaryPushButton("上传文件")
        actions_layout.addWidget(self.upload_btn)
        self.download_btn = PushButton("下载选中")
        actions_layout.addWidget(self.download_btn)
        self.delete_btn = PushButton("删除选中")
        actions_layout.addWidget(self.delete_btn)
        self.rename_btn = PushButton("重命名")
        actions_layout.addWidget(self.rename_btn)
        actions_layout.addStretch()
        layout.addWidget(actions_frame)

        # 文件列表
        list_frame = CardWidget()
        list_layout = QVBoxLayout(list_frame)
        list_layout.setContentsMargins(2, 2, 2, 2)
        self.tree = TreeWidget()
        self.tree.setHeaderLabels(["名称", "大小", "权限", "修改时间"])
        self.tree.setRootIsDecorated(False)
        self.tree.setContextMenuPolicy(Qt.CustomContextMenu)
        self.tree.header().setSectionResizeMode(0, QHeaderView.Stretch)
        self.tree.header().setSectionResizeMode(1, QHeaderView.ResizeToContents)
        self.tree.header().setSectionResizeMode(2, QHeaderView.ResizeToContents)
        self.tree.header().setSectionResizeMode(3, QHeaderView.ResizeToContents)
        self.tree.itemDoubleClicked.connect(self._on_item_double_clicked)
        list_layout.addWidget(self.tree)
        layout.addWidget(list_frame, 1)

        # 延迟刷新确保页面完全就绪后再加载文件列表
        QTimer.singleShot(800, self._refresh)

    def _connect_signals(self):
        self.up_btn.clicked.connect(self._go_up)
        self.go_btn.clicked.connect(self._go_path)
        self.refresh_btn.clicked.connect(self._refresh)
        self.mkdir_btn.clicked.connect(self._mkdir)
        self.upload_btn.clicked.connect(self._upload)
        self.download_btn.clicked.connect(self._download_selected)
        self.delete_btn.clicked.connect(self._delete_selected)
        self.rename_btn.clicked.connect(self._rename_selected)
        self.tree.itemSelectionChanged.connect(self._on_selection_changed)
        self.tree.customContextMenuRequested.connect(self._on_tree_menu)
        try:
            self.manager.connection_status_changed.connect(lambda c, ch: self._update_channel_label())
            self.manager.file_list_received.connect(self._populate_from_json)
        except Exception:
            pass
        self._update_channel_label()

    def _update_channel_label(self):
        """更新通道标签，并根据通道禁用/启用操作按钮"""
        try:
            ch = self.manager.current_channel
            if ch == "adb":
                self.channel_label.setText(f"当前通道: {ch} (ADB 直接操作)")
            elif ch == "wifi":
                self.channel_label.setText(f"当前通道: {ch} (通过指令远程操作)")
            else:
                self.channel_label.setText(f"当前通道: {ch} (未连接)")
        except Exception:
            pass
        # 通道变化时自动刷新文件列表
        self._refresh()

    # ==================== 深色弹窗辅助方法 ====================

    def _show_message(self, icon, title, text):
        """显示深色模式兼容的消息弹窗"""
        msg = QMessageBox(self)
        msg.setIcon(icon)
        msg.setWindowTitle(title)
        msg.setText(text)
        msg.setStyleSheet(dark_dialog_style())
        return msg.exec_()

    def _show_question(self, title, text):
        """显示深色模式的确认弹窗，返回是否点击 Yes"""
        msg = QMessageBox(self)
        msg.setIcon(QMessageBox.Question)
        msg.setWindowTitle(title)
        msg.setText(text)
        msg.setStandardButtons(QMessageBox.Yes | QMessageBox.No)
        msg.setDefaultButton(QMessageBox.No)
        msg.setStyleSheet(dark_dialog_style())
        return msg.exec_() == QMessageBox.Yes

    def _show_input(self, title, label, text=""):
        """显示深色模式的输入弹窗，返回 (text, ok)"""
        dlg = QInputDialog(self)
        dlg.setWindowTitle(title)
        dlg.setLabelText(label)
        dlg.setTextValue(text)
        dlg.setStyleSheet(dark_dialog_style())
        ok = dlg.exec_() == QInputDialog.Accepted
        return dlg.textValue(), ok

    # ==================== 文件列表刷新 ====================

    def _show_placeholder(self, message):
        """在树中显示单条占位提示"""
        from PyQt5.QtGui import QColor
        self.tree.clear()
        item = QTreeWidgetItem([message, "", "", ""])
        item.setFlags(Qt.NoItemFlags)  # 不可选中
        # 使用次要颜色区分提示文字
        item.setForeground(0, QColor(_c()['text_secondary']))
        self.tree.addTopLevelItem(item)

    def _refresh(self):
        """刷新当前目录的文件列表"""
        ch = getattr(self.manager, "current_channel", "none")
        if ch == "adb":
            # ADB 通道：直接调用 adb_list_files 列出目录
            try:
                output = self.manager.adb_list_files(self.current_path)
                self._populate_from_ls(output)
            except Exception as e:
                self._show_placeholder(f"读取目录失败: {e}")
        elif ch == "wifi":
            # WiFi 通道：发送 file_list_request 给手机，等待 file_list_received 信号
            self._show_placeholder("正在请求目录列表...")
            try:
                self.manager.send_action("file_list_request", {"path": self.current_path})
            except Exception:
                pass
        else:
            # 未连接：显示提示
            self._show_placeholder("未连接手机，请先连接设备")

    def _parse_ls_la(self, output):
        items = []
        for line in output.splitlines():
            line = line.strip()
            if not line or line.startswith("total"):
                continue
            parts = line.split()
            if len(parts) < 7:
                continue
            perms = parts[0]
            size = parts[3]
            try:
                date_str = " ".join(parts[4:7])
            except Exception:
                date_str = ""
            name = " ".join(parts[7:])
            if name in (".", ".."):
                continue
            is_dir = perms.startswith("d")
            items.append({
                'name': name,
                'perms': perms,
                'size': size,
                'date': date_str,
                'is_dir': is_dir,
            })
        # 排序：目录优先，然后按名称字母排序
        items.sort(key=lambda x: (not x['is_dir'], x['name'].lower()))
        return items

    def _file_icon(self, name, is_dir):
        """根据文件名返回文件类型图标"""
        if is_dir:
            return "\U0001F4C1 "  # 📁
        ext = name.rsplit('.', 1)[-1].lower() if '.' in name else ''
        icons = {
            'apk': '\U0001F4E6 ', 'zip': '\U0001F4E6 ', 'rar': '\U0001F4E6 ', '7z': '\U0001F4E6 ',
            'jpg': '\U0001F5BC ', 'jpeg': '\U0001F5BC ', 'png': '\U0001F5BC ', 'gif': '\U0001F5BC ', 'bmp': '\U0001F5BC ', 'webp': '\U0001F5BC ',
            'mp4': '\U0001F3AC ', 'avi': '\U0001F3AC ', 'mkv': '\U0001F3AC ', 'mov': '\U0001F3AC ',
            'mp3': '\U0001F3B5 ', 'wav': '\U0001F3B5 ', 'flac': '\U0001F3B5 ', 'aac': '\U0001F3B5 ', 'ogg': '\U0001F3B5 ',
            'pdf': '\U0001F4C4 ', 'doc': '\U0001F4C4 ', 'docx': '\U0001F4C4 ', 'xls': '\U0001F4C4 ', 'xlsx': '\U0001F4C4 ',
            'txt': '\U0001F4DD ', 'log': '\U0001F4DD ', 'md': '\U0001F4DD ',
            'py': '\U0001F40D ', 'js': '\U0001F4DC ', 'java': '\U0001F4DC ', 'kt': '\U0001F4DC ',
        }
        return icons.get(ext, '\U0001F4CE ')  # 📎 default

    def _populate_from_ls(self, output):
        """解析 adb ls -la 输出并填充列表"""
        self.tree.clear()
        if not output:
            self._show_placeholder("空目录或无法读取")
            return
        items = self._parse_ls_la(output)
        if not items:
            self._show_placeholder("空目录")
            return
        for it in items:
            icon = self._file_icon(it['name'], it['is_dir'])
            name = it['name'] + ("/" if it['is_dir'] else "")
            display = [icon + name, it['size'], it['perms'], it['date']]
            tree_item = QTreeWidgetItem(display)
            tree_item.setData(0, Qt.UserRole, it)
            self.tree.addTopLevelItem(tree_item)

    def _populate_from_json(self, resp_path, files):
        """处理 WiFi 通道返回的文件列表（JSON 格式）
        resp_path: 手机端响应中携带的路径，用于校验是否匹配当前请求路径
        """
        # 校验响应路径是否匹配当前路径（防止旧响应覆盖新视图）
        if resp_path and resp_path != self.current_path:
            return
        self.tree.clear()
        if not files:
            self._show_placeholder("空目录")
            return
        import time
        # 排序：目录优先，按名称字母排序
        files.sort(key=lambda x: (not x.get('is_dir', False), x.get('name', '').lower()))
        for f in files:
            name = f.get('name', '')
            is_dir = f.get('is_dir', False)
            icon = self._file_icon(name, is_dir)
            display_name = icon + name + ("/" if is_dir else "")
            size = f"{f.get('size', 0)}" if not is_dir else "-"
            try:
                date_str = time.strftime("%Y-%m-%d %H:%M", time.localtime(f.get('modified', 0) / 1000))
            except Exception:
                date_str = ""
            display = [display_name, size, "d" if is_dir else "-", date_str]
            tree_item = QTreeWidgetItem(display)
            tree_item.setData(0, Qt.UserRole, f)
            self.tree.addTopLevelItem(tree_item)

    def _go_up(self):
        if self.current_path == "/" or self.current_path == "/sdcard/":
            return
        parts = self.current_path.rstrip("/").split("/")
        parts.pop()
        new_path = "/".join(parts) + "/"
        if not new_path.startswith("/"):
            new_path = "/" + new_path
        self.current_path = new_path
        self.path_input.setText(self.current_path)
        self._refresh()

    def _go_path(self):
        new_path = self.path_input.text().strip()
        if new_path:
            if not new_path.endswith("/"):
                new_path += "/"
            self.current_path = new_path
            self._refresh()

    def _on_item_double_clicked(self, item, column):
        data = item.data(0, Qt.UserRole)
        if not data:
            return
        if data.get('is_dir'):
            self.current_path = self.current_path.rstrip("/") + "/" + data['name'] + "/"
            self.path_input.setText(self.current_path)
            self._refresh()

    def _on_selection_changed(self):
        """选中项变化时，根据选中项类型（文件/文件夹）更新按钮状态"""
        item = self.tree.currentItem()
        if not item:
            # 无选中：启用按钮
            self.download_btn.setEnabled(True)
            self.upload_btn.setEnabled(True)
            return
        data = item.data(0, Qt.UserRole)
        if data and data.get('is_dir'):
            # 选中文件夹：禁用下载和上传按钮
            self.download_btn.setEnabled(False)
            self.upload_btn.setEnabled(False)
        else:
            # 选中文件：启用按钮
            self.download_btn.setEnabled(True)
            self.upload_btn.setEnabled(True)

    def _mkdir(self):
        name, ok = self._show_input("新建文件夹", "名称:")
        if not ok or not name:
            return
        new_path = self.current_path.rstrip("/") + "/" + name
        if self.manager.current_channel == "adb":
            self.manager.adb_command('shell', 'mkdir', '-p', new_path)
            self._refresh()
        else:
            self.manager.send_action("file_mkdir", {"path": new_path})
            QTimer.singleShot(1500, self._refresh)

    def _upload(self):
        file_path, _ = QFileDialog.getOpenFileName(self, "选择要上传的文件")
        if not file_path:
            return
        remote = self.current_path.rstrip("/") + "/" + os.path.basename(file_path)
        if self.manager.current_channel == "adb":
            try:
                self.manager.adb_push(file_path, remote)
                self._show_message(QMessageBox.Information, "上传成功", f"已上传到:\n{remote}")
                self._refresh()
            except Exception as e:
                self._show_message(QMessageBox.Warning, "上传失败", str(e))
        else:
            # WiFi/传输引擎发送文件
            self.manager.send_file(file_path)
            self._show_message(QMessageBox.Information, "已发送", "文件已通过传输引擎发送，接收后存放在手机接收目录")

    def _download_selected(self):
        item = self.tree.currentItem()
        if not item:
            self._show_message(QMessageBox.Information, "提示", "请先选择文件")
            return
        data = item.data(0, Qt.UserRole)
        if not data or data.get('is_dir'):
            self._show_message(QMessageBox.Information, "提示", "请选择文件而非文件夹")
            return
        remote = self.current_path.rstrip("/") + "/" + data['name']
        if self.manager.current_channel == "adb":
            # ADB 模式：让用户选择保存目录
            target_dir = QFileDialog.getExistingDirectory(self, "选择保存目录")
            if not target_dir:
                return
            local = os.path.join(target_dir, data['name'])
            try:
                self.manager.adb_pull(remote, local)
                self._show_message(QMessageBox.Information, "下载成功", f"已保存到:\n{local}")
            except Exception as e:
                self._show_message(QMessageBox.Warning, "下载失败", str(e))
        else:
            # WiFi 模式：手机端发送文件
            if self.manager.file_transfer_active and not self.manager.outgoing_file_path and not self.manager.current_file_id:
                self.manager.file_transfer_active = False
                self.manager.transfer_in_progress = False
            if self.manager.transfer_in_progress or self.manager.file_transfer_active:
                self._show_message(QMessageBox.Warning, "忙碌", "当前有文件正在传输，请等待完成后再下载。")
                return
            try:
                self.manager.send_action("send_file_request", {"path": remote})
                # 自动切换到文件传输页面
                self._switch_to_transfer_page()
            except Exception as e:
                self._show_message(QMessageBox.Warning, "下载失败", str(e))

    def _switch_to_transfer_page(self):
        """切换到文件传输页面"""
        try:
            main_win = self.window()
            if hasattr(main_win, 'switchTo') and hasattr(main_win, 'file_transfer_page'):
                main_win.switchTo(main_win.file_transfer_page)
        except Exception:
            pass

    def _delete_selected(self):
        item = self.tree.currentItem()
        if not item:
            return
        data = item.data(0, Qt.UserRole)
        if not data:
            return
        if not self._show_question("确认", f"删除 {data['name']}?"):
            return
        target = self.current_path.rstrip("/") + "/" + data['name']
        if self.manager.current_channel == "adb":
            if data.get('is_dir'):
                self.manager.adb_command('shell', 'rm', '-rf', target)
            else:
                self.manager.adb_command('shell', 'rm', target)
            self._refresh()
        else:
            self.manager.send_action("file_delete", {"path": target, "is_dir": data.get('is_dir', False)})
            QTimer.singleShot(1500, self._refresh)

    def _rename_selected(self):
        item = self.tree.currentItem()
        if not item:
            return
        data = item.data(0, Qt.UserRole)
        if not data:
            return
        new_name, ok = self._show_input("重命名", "新名称:", text=data['name'])
        if not ok or not new_name or new_name == data['name']:
            return
        old_path = self.current_path.rstrip("/") + "/" + data['name']
        new_path = self.current_path.rstrip("/") + "/" + new_name
        if self.manager.current_channel == "adb":
            self.manager.adb_command('shell', 'mv', old_path, new_path)
            self._refresh()
        else:
            self.manager.send_action("file_rename", {"old_path": old_path, "new_path": new_path})
            QTimer.singleShot(1500, self._refresh)

    def _copy_selected(self):
        """复制选中文件/文件夹到指定目录"""
        item = self.tree.currentItem()
        if not item:
            return
        data = item.data(0, Qt.UserRole)
        if not data:
            return
        src = self.current_path.rstrip("/") + "/" + data['name']
        # 弹窗输入目标目录
        target, ok = self._show_input("复制到", "目标路径:", text=self.current_path)
        if not ok or not target:
            return
        target = target.rstrip("/") + "/" + data['name']
        if self.manager.current_channel == "adb":
            try:
                if data.get('is_dir'):
                    self.manager.adb_command('shell', 'cp', '-r', src, target)
                else:
                    self.manager.adb_command('shell', 'cp', src, target)
                self._show_message(QMessageBox.Information, "复制成功", f"已复制到:\n{target}")
                self._refresh()
            except Exception as e:
                self._show_message(QMessageBox.Warning, "复制失败", str(e))
        else:
            self.manager.send_action("file_copy", {"src": src, "dst": target, "is_dir": data.get('is_dir', False)})
            QTimer.singleShot(1500, self._refresh)

    def _on_tree_menu(self, pos):
        item = self.tree.itemAt(pos)
        if not item:
            return
        self.tree.setCurrentItem(item)
        data = item.data(0, Qt.UserRole)
        if not data:
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

        is_dir = data.get('is_dir', False)
        ext = data['name'].rsplit('.', 1)[-1].lower() if '.' in data['name'] else ''

        # "打开(进入)"：目录→进入，文本文件→打开，其他→不显示
        act_open = None
        if is_dir:
            act_open = menu.addAction("打开(进入)")
        elif ext in TEXT_FILE_EXTS:
            act_open = menu.addAction("打开")

        act_download = menu.addAction("下载")
        act_copy = menu.addAction("复制到...")
        menu.addSeparator()
        act_rename = menu.addAction("重命名")
        act_delete = menu.addAction("删除")

        action = menu.exec_(self.tree.mapToGlobal(pos))
        if action is None:
            return
        if action == act_open:
            if is_dir:
                self._on_item_double_clicked(item, 0)
            else:
                self._open_text_file(item)
        elif action == act_download:
            self._download_selected()
        elif action == act_copy:
            self._copy_selected()
        elif action == act_rename:
            self._rename_selected()
        elif action == act_delete:
            self._delete_selected()

    def _open_text_file(self, item):
        """下载文本文件并在电脑上打开"""
        data = item.data(0, Qt.UserRole)
        if not data:
            return
        remote = self.current_path.rstrip("/") + "/" + data['name']
        if self.manager.current_channel == "adb":
            import tempfile
            local = os.path.join(tempfile.gettempdir(), data['name'])
            try:
                self.manager.adb_pull(remote, local)
                os.startfile(local)
            except Exception as e:
                self._show_message(QMessageBox.Warning, "打开失败", str(e))
        else:
            # WiFi 模式：请求手机发送文件，接收后打开
            if self.manager.file_transfer_active and not self.manager.outgoing_file_path and not self.manager.current_file_id:
                self.manager.file_transfer_active = False
                self.manager.transfer_in_progress = False
            if self.manager.transfer_in_progress or self.manager.file_transfer_active:
                self._show_message(QMessageBox.Warning, "忙碌", "当前有文件正在传输，请等待完成后再打开。")
                return
            self._open_after_download_name = data['name']
            try:
                self.manager.send_action("send_file_request", {"path": remote})
                self._switch_to_transfer_page()
            except Exception as e:
                self._show_message(QMessageBox.Warning, "打开失败", str(e))
