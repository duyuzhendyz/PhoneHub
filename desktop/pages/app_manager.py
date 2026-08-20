import os
import time
import threading
from PyQt5.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout,
                               QFrame, QListWidgetItem,
                               QMessageBox, QMenu,
                               QHeaderView, QFileDialog,
                               QTableWidgetItem,
                               QAbstractItemView)
from PyQt5.QtCore import Qt, pyqtSignal, pyqtSlot, QTimer
from PyQt5.QtGui import QFont
from qfluentwidgets import (CardWidget, TitleLabel, BodyLabel, SubtitleLabel,
                            PushButton, PrimaryPushButton,
                            LineEdit, TableWidget, ProgressBar,
                            setFont, FluentIcon as FIF)
from styles import get_theme, _c, dark_dialog_style

APK_BACKUP_DIR = os.path.join(os.path.expanduser("~"), "PhoneHub", "AppBackups")

# 表格深色样式 — 已由全局 QSS 处理，保留占位注释


class AppManagerPage(QWidget):
    """应用管理"""

    action_progress = pyqtSignal(int, str)
    action_done = pyqtSignal(bool, str)
    apps_loaded = pyqtSignal()  # 应用列表加载完成信号（主线程渲染）

    def __init__(self, manager):
        super().__init__()
        self.manager = manager
        self._apps = []
        self._search_keyword = ""
        os.makedirs(APK_BACKUP_DIR, exist_ok=True)
        self._setup_ui()
        self._connect_signals()
        self._update_button_states()
        # 页面加载后自动获取应用列表（如果已连接且列表为空）
        QTimer.singleShot(500, self._auto_load_apps)

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 20, 24, 20)
        layout.setSpacing(16)

        # 标题行：标题在左，同步时间在右
        title_row = QHBoxLayout()
        title = TitleLabel("应用管理")
        setFont(title, 28, QFont.Bold)
        title_row.addWidget(title)
        title_row.addStretch()
        self.sync_time_label = BodyLabel("未同步")
        title_row.addWidget(self.sync_time_label)
        layout.addLayout(title_row)

        self.channel_label = BodyLabel("当前通道: --")
        layout.addWidget(self.channel_label)

        ctrl_frame = CardWidget()
        ctrl_layout = QHBoxLayout(ctrl_frame)
        ctrl_layout.setContentsMargins(16, 12, 16, 12)
        ctrl_layout.setSpacing(10)

        self.refresh_btn = PrimaryPushButton("加载应用列表")
        ctrl_layout.addWidget(self.refresh_btn)

        self.uninstall_btn = PushButton("卸载(含备份)")
        ctrl_layout.addWidget(self.uninstall_btn)

        self.clear_btn = PushButton("清除数据")
        ctrl_layout.addWidget(self.clear_btn)

        self.export_btn = PushButton("导出APK")
        ctrl_layout.addWidget(self.export_btn)

        ctrl_layout.addStretch()

        self.search_input = LineEdit()
        self.search_input.setPlaceholderText("搜索应用名/包名...")
        self.search_input.setMaximumWidth(300)
        ctrl_layout.addWidget(self.search_input)
        layout.addWidget(ctrl_frame)

        progress_frame = CardWidget()
        progress_layout = QVBoxLayout(progress_frame)
        progress_layout.setContentsMargins(16, 12, 16, 12)
        progress_layout.setSpacing(8)
        self.status_label = BodyLabel("等待操作...")
        progress_layout.addWidget(self.status_label)
        self.progress_bar = ProgressBar()
        self.progress_bar.setRange(0, 100)
        progress_layout.addWidget(self.progress_bar)
        layout.addWidget(progress_frame)

        # 使用 TableWidget 显示应用列表
        list_frame = CardWidget()
        list_layout = QVBoxLayout(list_frame)
        list_layout.setContentsMargins(8, 8, 8, 8)
        self.table = TableWidget()
        self.table.setColumnCount(5)
        self.table.setHorizontalHeaderLabels(["应用名", "包名", "版本", "大小", "安装时间"])
        self.table.setSelectionBehavior(QAbstractItemView.SelectRows)
        self.table.setSelectionMode(QAbstractItemView.SingleSelection)
        self.table.setEditTriggers(QAbstractItemView.NoEditTriggers)
        self.table.setContextMenuPolicy(Qt.CustomContextMenu)
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.Stretch)
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.Stretch)
        self.table.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(4, QHeaderView.ResizeToContents)
        self.table.verticalHeader().setVisible(False)
        self.table.customContextMenuRequested.connect(self._on_table_menu)
        list_layout.addWidget(self.table)
        layout.addWidget(list_frame, 1)

        # 初始占位提示
        self._show_placeholder("点击\"加载应用列表\"或等待自动加载")

    def _connect_signals(self):
        self.refresh_btn.clicked.connect(self._load_apps)
        self.uninstall_btn.clicked.connect(self._uninstall_selected)
        self.clear_btn.clicked.connect(self._clear_data_selected)
        self.export_btn.clicked.connect(self._export_selected)
        self.search_input.textChanged.connect(self._on_search_changed)
        # 选中行变化时更新按钮状态
        self.table.itemSelectionChanged.connect(self._update_button_states)
        self.action_progress.connect(self._on_progress)
        self.action_done.connect(self._on_done)
        self.apps_loaded.connect(self._populate)  # 主线程渲染列表
        try:
            self.manager.connection_status_changed.connect(lambda c, ch: self._on_connection_changed(c, ch))
            # WiFi 模式下接收应用列表
            self.manager.app_list_received.connect(self._on_app_list_received)
        except Exception:
            pass

    def _on_connection_changed(self, connected, channel):
        """连接状态变化时自动加载应用列表"""
        self._update_button_states()
        if connected and not self._apps:
            self._load_apps()

    def _update_button_states(self):
        """根据通道和选中行状态更新按钮启用状态"""
        try:
            ch = self.manager.current_channel
            self.channel_label.setText(f"当前通道: {ch}")
            # 刷新按钮在连接时始终启用
            self.refresh_btn.setEnabled(ch in ("adb", "wifi"))
            # 检查是否有选中行且选中行有有效应用数据
            has_selection = False
            row = self.table.currentRow()
            if row >= 0:
                item = self.table.item(row, 0)
                if item is not None and item.data(Qt.UserRole) is not None:
                    has_selection = True
            if ch == "adb":
                # ADB 模式：有选中行时启用卸载、清除数据、导出APK
                if has_selection:
                    self.uninstall_btn.setEnabled(True)
                    self.clear_btn.setEnabled(True)
                    self.export_btn.setEnabled(True)
                else:
                    self.uninstall_btn.setEnabled(False)
                    self.clear_btn.setEnabled(False)
                    self.export_btn.setEnabled(False)
            elif ch == "wifi":
                # WiFi 模式：有选中行时仅启用卸载和导出APK，禁用清除数据
                self.channel_label.setText(f"当前通道: {ch} (WiFi 模式)")
                if has_selection:
                    self.uninstall_btn.setEnabled(True)
                    self.clear_btn.setEnabled(False)
                    self.export_btn.setEnabled(True)
                else:
                    self.uninstall_btn.setEnabled(False)
                    self.clear_btn.setEnabled(False)
                    self.export_btn.setEnabled(False)
            else:
                # 未连接：禁用所有操作按钮
                self.uninstall_btn.setEnabled(False)
                self.clear_btn.setEnabled(False)
                self.export_btn.setEnabled(False)
                self.channel_label.setText(f"当前通道: {ch} (未连接)")
        except Exception:
            pass

    def _auto_load_apps(self):
        """页面加载时自动获取应用列表（如果已连接且列表为空）"""
        if not self._apps:
            ch = getattr(self.manager, "current_channel", "none")
            if ch in ("adb", "wifi"):
                self._load_apps()

    def _show_placeholder(self, message):
        """在表格中显示占位提示"""
        self.table.setRowCount(1)
        self.table.setRowHeight(0, 40)
        item = QTableWidgetItem(message)
        item.setFlags(Qt.NoItemFlags)
        from PyQt5.QtGui import QColor
        item.setForeground(QColor(_c()['text_secondary']))
        self.table.setItem(0, 0, item)
        self.table.setSpan(0, 0, 1, 3)

    def _load_apps(self):
        """加载应用列表"""
        ch = self.manager.current_channel
        if ch == "adb":
            self.status_label.setText("正在加载应用列表...")
            self.progress_bar.setValue(10)
            threading.Thread(target=self._load_apps_worker_adb, daemon=True).start()
        elif ch == "wifi":
            # WiFi 模式：发送 app_list_request 给手机
            self.status_label.setText("正在请求应用列表...")
            self.progress_bar.setValue(30)
            try:
                self.manager.send_action("app_list_request", {})
            except Exception:
                pass
        else:
            self._show_message(QMessageBox.Warning, "未连接", "请先连接手机。")

    def _load_apps_worker_adb(self):
        """ADB 模式加载应用列表（工作线程）"""
        try:
            self.action_progress.emit(30, "获取包列表...")
            # pm list packages -3 -f (第三方应用，含 APK 路径)
            output = self.manager.adb_command('shell', 'pm', 'list', 'packages', '-3', '-f')
            if not output:
                self.action_done.emit(False, "未获取到应用列表")
                return
            pkg_paths = {}  # package -> apk_path
            for line in output.splitlines():
                line = line.strip()
                if line.startswith("package:"):
                    # 格式: package:/data/app/.../base.apk=com.example.app
                    rest = line[len("package:"):]
                    if '=' in rest:
                        apk_path, pkg = rest.rsplit('=', 1)
                        pkg_paths[pkg.strip()] = apk_path.strip()
            self.action_progress.emit(50, f"获取到 {len(pkg_paths)} 个应用, 正在查询详情...")

            apps = []
            packages = list(pkg_paths.keys())
            # 限制最多处理 200 个应用，防止卡死
            max_apps = min(len(packages), 200)
            for i, pkg in enumerate(packages[:max_apps]):
                try:
                    short_name = pkg.split('.')[-1] if pkg else pkg
                    version = ""
                    install_time = 0
                    # 版本信息 + 安装时间 - dumpsys 带 15s 超时
                    try:
                        info = self.manager.adb_command('shell', 'dumpsys', 'package', pkg) or ""
                        for ln in info.splitlines():
                            ln = ln.strip()
                            if ln.startswith("versionName="):
                                version = ln.split("=", 1)[1].split()[0] if "=" in ln else ""
                            elif ln.startswith("firstInstallTime="):
                                # 格式: firstInstallTime=2024-01-15 10:30:00
                                ts_str = ln.split("=", 1)[1].strip() if "=" in ln else ""
                                try:
                                    from datetime import datetime
                                    dt = datetime.strptime(ts_str[:19], "%Y-%m-%d %H:%M:%S")
                                    install_time = int(dt.timestamp() * 1000)
                                except Exception:
                                    pass
                    except Exception:
                        pass
                    # APK 大小
                    size = 0
                    apk_path = pkg_paths.get(pkg, "")
                    if apk_path:
                        try:
                            size_out = self.manager.adb_command('shell', 'wc', '-c', apk_path) or ""
                            # 输出格式: 12345 /data/app/.../base.apk
                            size = int(size_out.strip().split()[0]) if size_out.strip() else 0
                        except Exception:
                            pass
                    apps.append({
                        'name': short_name,
                        'package': pkg,
                        'version': version,
                        'size': size,
                        'install_time': install_time,
                    })
                    if i % 5 == 0:
                        pct = 50 + int(40 * (i + 1) / max(1, max_apps))
                        self.action_progress.emit(pct, f"查询中 {i+1}/{max_apps}")
                except Exception:
                    continue
            self._apps = apps
            self.action_progress.emit(95, "渲染列表...")
            self.apps_loaded.emit()
            self._populate()
            self.action_done.emit(True, f"加载完成: {len(apps)} 个应用")
        except Exception as e:
            self.action_done.emit(False, f"加载失败: {e}")

    def _on_app_list_received(self, apps):
        """WiFi 模式下接收手机端返回的应用列表"""
        self._apps = apps if isinstance(apps, list) else []
        self.progress_bar.setValue(95)
        self._populate()
        self.action_done.emit(True, f"加载完成: {len(self._apps)} 个应用")

    def _populate(self):
        """填充应用列表到表格（主线程调用）"""
        self.table.clearSpans()
        self.table.setRowCount(0)
        count = 0
        for app in self._apps:
            name = app.get('name', '')
            pkg = app.get('package', '')
            version = app.get('version', '')
            size = app.get('size', 0)
            install_time = app.get('install_time', 0)
            # 搜索过滤
            if self._search_keyword:
                if self._search_keyword not in name.lower() and self._search_keyword not in pkg.lower():
                    continue
            # 格式化大小
            size_str = ""
            if size and size > 0:
                if size >= 1024 * 1024:
                    size_str = f"{size / 1024 / 1024:.1f} MB"
                elif size >= 1024:
                    size_str = f"{size / 1024:.0f} KB"
                else:
                    size_str = f"{size} B"
            # 格式化安装时间
            time_str = ""
            if install_time and install_time > 0:
                try:
                    from datetime import datetime
                    time_str = datetime.fromtimestamp(install_time / 1000).strftime("%Y-%m-%d")
                except Exception:
                    time_str = ""
            row = self.table.rowCount()
            self.table.insertRow(row)
            name_item = QTableWidgetItem(name)
            pkg_item = QTableWidgetItem(pkg)
            ver_item = QTableWidgetItem(version)
            size_item = QTableWidgetItem(size_str)
            time_item = QTableWidgetItem(time_str)
            name_item.setData(Qt.UserRole, app)
            pkg_item.setData(Qt.UserRole, app)
            ver_item.setData(Qt.UserRole, app)
            size_item.setData(Qt.UserRole, app)
            time_item.setData(Qt.UserRole, app)
            self.table.setItem(row, 0, name_item)
            self.table.setItem(row, 1, pkg_item)
            self.table.setItem(row, 2, ver_item)
            self.table.setItem(row, 3, size_item)
            self.table.setItem(row, 4, time_item)
            count += 1
        if count == 0:
            if self._search_keyword:
                self._show_placeholder("没有匹配的应用")
            elif not self._apps:
                self._show_placeholder("暂无应用，点击\"加载应用列表\"")
            else:
                self._show_placeholder("没有可显示的应用")

    def _on_search_changed(self, text):
        self._search_keyword = text.strip().lower()
        self._populate()

    def _get_selected_app(self):
        """获取当前选中的应用"""
        row = self.table.currentRow()
        if row < 0:
            self._show_message(QMessageBox.Information, "提示", "请先选择应用")
            return None
        item = self.table.item(row, 0)
        if not item:
            return None
        return item.data(Qt.UserRole)

    def _uninstall_selected(self):
        app = self._get_selected_app()
        if not app:
            return
        pkg = app.get('package', '')
        ch = self.manager.current_channel
        if ch == "adb":
            if not self._show_question("确认", f"卸载 {pkg}?\n卸载前会先备份APK"):
                return
            threading.Thread(target=self._uninstall_worker, args=(app,), daemon=True).start()
        elif ch == "wifi":
            if not self._show_question("确认", f"卸载 {pkg}?"):
                return
            self.status_label.setText(f"正在卸载 {pkg}...")
            try:
                self.manager.send_action("app_uninstall_request", {"package": pkg})
            except Exception as e:
                self._show_message(QMessageBox.Warning, "卸载失败", f"发送卸载请求失败: {e}")

    def _uninstall_worker(self, app):
        try:
            pkg = app.get('package', '')
            name = app.get('name', '')
            version = app.get('version', '1.0')
            self.action_progress.emit(10, "正在查找APK路径...")
            # 1. 查找 APK 路径
            path_output = self.manager.adb_command('shell', 'pm', 'path', pkg) or ""
            apk_paths = [line.replace("package:", "").strip() for line in path_output.splitlines() if line.startswith("package:")]
            if not apk_paths:
                self.action_done.emit(False, f"未找到 {pkg} 的APK路径")
                return
            self.action_progress.emit(30, "正在备份APK (pull)...")

            # 2. ADB pull 备份
            backup_name = f"{name}_{version}.apk"
            # 清理非法字符
            for ch in '\\/:*?"<>|':
                backup_name = backup_name.replace(ch, "_")
            local_path = os.path.join(APK_BACKUP_DIR, backup_name)
            self.manager.adb_pull(apk_paths[0], local_path)
            if not os.path.exists(local_path):
                self.action_done.emit(False, f"备份失败: {local_path} 不存在")
                return
            self.action_progress.emit(70, f"已备份到 {local_path}, 正在卸载...")

            # 3. 卸载
            output = self.manager.adb_uninstall(pkg) or ""
            if "Success" in output:
                self.action_done.emit(True, f"已卸载 {pkg}\n备份: {backup_name}")
            else:
                self.action_done.emit(False, f"卸载失败: {output.strip()}")
        except Exception as e:
            self.action_done.emit(False, f"卸载异常: {e}")

    def _clear_data_selected(self):
        if self.manager.current_channel != "adb":
            # WiFi 模式不支持清除数据，给用户提示
            self._show_message(QMessageBox.Warning, "不支持", "WiFi 模式不支持清除数据")
            return
        app = self._get_selected_app()
        if not app:
            return
        pkg = app.get('package', '')
        if not self._show_question("确认", f"清除 {pkg} 的数据?"):
            return
        threading.Thread(target=self._clear_data_worker, args=(pkg,), daemon=True).start()

    def _clear_data_worker(self, pkg):
        try:
            self.action_progress.emit(20, f"清除 {pkg} 数据...")
            output = self.manager.adb_clear_data(pkg) or ""
            if "Success" in output:
                self.action_done.emit(True, f"已清除 {pkg} 数据")
            else:
                self.action_done.emit(False, f"清除失败: {output.strip()}")
        except Exception as e:
            self.action_done.emit(False, f"清除异常: {e}")

    def _export_selected(self):
        app = self._get_selected_app()
        if not app:
            return
        pkg = app.get('package', '')
        ch = self.manager.current_channel
        if ch == "adb":
            target_dir = QFileDialog.getExistingDirectory(self, "选择保存目录", APK_BACKUP_DIR)
            if not target_dir:
                return
            threading.Thread(target=self._export_worker, args=(app, target_dir), daemon=True).start()
        elif ch == "wifi":
            self.status_label.setText(f"正在导出 {pkg} APK...")
            try:
                self.manager.send_action("app_apk_request", {"package": pkg})
            except Exception as e:
                self._show_message(QMessageBox.Warning, "导出失败", f"发送APK请求失败: {e}")

    def _export_worker(self, app, target_dir):
        try:
            pkg = app.get('package', '')
            name = app.get('name', '')
            version = app.get('version', '1.0')
            self.action_progress.emit(20, f"查找 {pkg} 的 APK 路径...")
            path_output = self.manager.adb_command('shell', 'pm', 'path', pkg) or ""
            apk_paths = [line.replace("package:", "").strip() for line in path_output.splitlines() if line.startswith("package:")]
            if not apk_paths:
                self.action_done.emit(False, f"未找到 {pkg} 的APK路径")
                return
            backup_name = f"{name}_{version}.apk"
            for ch in '\\/:*?"<>|':
                backup_name = backup_name.replace(ch, "_")
            local_path = os.path.join(target_dir, backup_name)
            self.action_progress.emit(60, f"导出 APK 到 {local_path}...")
            self.manager.adb_pull(apk_paths[0], local_path)
            if os.path.exists(local_path):
                self.action_done.emit(True, f"已导出: {backup_name}")
            else:
                self.action_done.emit(False, "导出失败: 文件不存在")
        except Exception as e:
            self.action_done.emit(False, f"导出异常: {e}")

    def _on_table_menu(self, pos):
        """右键菜单：先选中行再显示菜单，避免闪退"""
        try:
            item = self.table.itemAt(pos)
            if not item:
                return
            row = item.row()
            # 先选中该行
            self.table.selectRow(row)
            # 检查是否有有效的应用数据（占位行无数据，不弹菜单）
            app = item.data(Qt.UserRole)
            if not app:
                return
            # 选中后更新按钮状态
            self._update_button_states()
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
            act_uninstall = menu.addAction("卸载(含备份)")
            act_clear = menu.addAction("清除数据")
            act_export = menu.addAction("导出APK")
            action = menu.exec_(self.table.mapToGlobal(pos))
            if action == act_uninstall:
                self._uninstall_selected()
            elif action == act_clear:
                self._clear_data_selected()
            elif action == act_export:
                self._export_selected()
        except Exception as e:
            print(f"右键菜单异常: {e}")

    @pyqtSlot(int, str)
    def _on_progress(self, percent, message):
        self.progress_bar.setValue(percent)
        self.status_label.setText(message)

    @pyqtSlot(bool, str)
    def _on_done(self, success, message):
        self.progress_bar.setValue(100 if success else self.progress_bar.value())
        self.status_label.setText(message)
        if success:
            # 加载应用列表成功时更新同步时间标签
            if "加载完成" in message:
                self.sync_time_label.setText(time.strftime("%Y-%m-%d %H:%M:%S", time.localtime()))
            # 成功时不弹窗，静默处理
        else:
            self._show_message(QMessageBox.Warning, "失败", message)

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
