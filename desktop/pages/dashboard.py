import os
import time
import psutil
from PyQt5.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout, QGridLayout,
                               QFrame, QSizePolicy, QTreeWidgetItem,
                               QHeaderView, QMenu, QMessageBox, QProgressBar)
from PyQt5.QtCore import Qt, QTimer, QPropertyAnimation, QEasingCurve, QSize
from PyQt5.QtGui import QFont, QColor, QPainter, QLinearGradient, QPen
from styles import get_theme, set_theme, _c, apply_dark_title_bar, set_item_text_color
from qfluentwidgets import (CardWidget, TitleLabel, BodyLabel, SubtitleLabel,
                            PushButton, PrimaryPushButton,
                            setFont, FluentIcon as FIF,
                            InfoBar, InfoBarPosition, LineEdit, TreeWidget)


class ProcessManagerWidget(QWidget):
    """手机进程管理"""

    def __init__(self, manager, parent=None):
        super().__init__(parent)
        self.manager = manager
        self._processes = []
        self._setup_ui()
        self._refresh_timer = QTimer(self)
        self._refresh_timer.timeout.connect(self._refresh)
        self._refresh_timer.start(10000)
        try:
            self.manager.process_list_received.connect(self._on_process_list_received)
        except Exception:
            pass
        self._refresh()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(8)

        toolbar = QHBoxLayout()
        self.search_input = LineEdit()
        self.search_input.setPlaceholderText("搜索手机进程 (按名称或PID)...")
        self.search_input.textChanged.connect(self._on_search_changed)
        toolbar.addWidget(self.search_input)
        self.refresh_btn = PushButton("刷新手机进程")
        self.refresh_btn.clicked.connect(self._refresh)
        toolbar.addWidget(self.refresh_btn)
        self.kill_btn = PrimaryPushButton("结束手机进程")
        self.kill_btn.clicked.connect(self._kill_selected)
        toolbar.addWidget(self.kill_btn)
        layout.addLayout(toolbar)

        self.tree = TreeWidget()
        self.tree.setHeaderLabels(["PID", "名称", "CPU%", "内存(MB)", "包名"])
        self.tree.setRootIsDecorated(False)
        self.tree.setContextMenuPolicy(Qt.CustomContextMenu)
        self.tree.header().setSectionResizeMode(0, QHeaderView.ResizeToContents)
        self.tree.header().setSectionResizeMode(1, QHeaderView.Stretch)
        self.tree.header().setSectionResizeMode(2, QHeaderView.ResizeToContents)
        self.tree.header().setSectionResizeMode(3, QHeaderView.ResizeToContents)
        self.tree.header().setSectionResizeMode(4, QHeaderView.Stretch)
        self.tree.itemDoubleClicked.connect(self._kill_item)
        self.tree.customContextMenuRequested.connect(self._on_menu)
        layout.addWidget(self.tree)

    def _refresh(self):
        if self.manager.current_channel == "adb":
            self._refresh_adb()
        elif self.manager.current_channel == "wifi":
            self.manager.send_action("process_list_request")
        else:
            self.tree.clear()
            item = QTreeWidgetItem(["", "未连接手机", "", "", ""])
            set_item_text_color(item)
            self.tree.addTopLevelItem(item)

    def _refresh_adb(self):
        def on_result(output):
            self._processes = []
            if not output:
                return
            try:
                lines = output.strip().split('\n')[1:]
                for line in lines:
                    parts = line.split()
                    if len(parts) >= 5:
                        pid = parts[0]
                        name = parts[1]
                        cpu = parts[2]
                        rss = int(parts[3]) / 1024
                        pkg = " ".join(parts[4:]) if len(parts) > 4 else ""
                        self._processes.append({
                            'pid': pid,
                            'name': name,
                            'cpu': cpu,
                            'mem': rss,
                            'package': pkg
                        })
                self._populate()
            except Exception:
                pass
        self.manager.adb_command('shell', 'ps', '-A', '-o', 'PID,NAME,%CPU,RSS,ARGS', callback=on_result)

    def _on_search_changed(self, text):
        self._populate()

    def _populate(self):
        kw = self.search_input.text().strip().lower()
        self.tree.clear()
        for p in self._processes:
            name = p['name']
            pid = str(p['pid'])
            pkg = p.get('package', '')
            if kw and kw not in name.lower() and kw not in pid and kw not in pkg.lower():
                continue
            item = QTreeWidgetItem([pid, name, f"{p['cpu']}", f"{p['mem']:.1f}", pkg])
            set_item_text_color(item)
            item.setData(0, Qt.UserRole, p)
            self.tree.addTopLevelItem(item)

    def _on_process_list_received(self, processes):
        self._processes = []
        for p in processes:
            self._processes.append({
                'pid': str(p.get('pid', '')),
                'name': p.get('name', ''),
                'cpu': str(p.get('cpu', 0)),
                'mem': float(p.get('mem', 0)),
                'package': p.get('user', '') or ''
            })
        self._populate()

    def _kill_selected(self):
        item = self.tree.currentItem()
        if item:
            self._kill_item(item)

    def _kill_item(self, item):
        p = item.data(0, Qt.UserRole)
        if not p:
            return
        pid = p['pid']
        name = p['name']
        if QMessageBox.question(self, "确认", f"结束手机进程 {name} (PID={pid})?") != QMessageBox.Yes:
            return
        def on_kill_done(output):
            if output is not None or self.manager.current_channel != "adb":
                QMessageBox.information(self, "成功", f"已结束 {name}")
                self._refresh()
        try:
            if self.manager.current_channel == "adb":
                self.manager.adb_command('shell', 'kill', '-9', pid, callback=on_kill_done)
            else:
                self.manager.send_action("kill_process", {"pid": pid})
                on_kill_done(None)
        except Exception as e:
            QMessageBox.warning(self, "失败", str(e))

    def _on_menu(self, pos):
        item = self.tree.itemAt(pos)
        if not item:
            return
        menu = QMenu(self)
        act_kill = menu.addAction("结束进程")
        act_refresh = menu.addAction("刷新")
        action = menu.exec_(self.tree.mapToGlobal(pos))
        if action == act_kill:
            self._kill_item(item)
        elif action == act_refresh:
            self._refresh()


class StatProgressBar(QWidget):
    """带进度条的统计卡片（内存/磁盘/电量等）"""
    def __init__(self, title, parent=None):
        super().__init__(parent)
        self._title = title
        self.setMinimumSize(160, 80)
        self.setSizePolicy(QSizePolicy.Preferred, QSizePolicy.Preferred)
        self._value = 0
        self._setup_ui()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 10, 12, 10)
        layout.setSpacing(6)

        # 标题 + 数值行
        top_row = QHBoxLayout()
        t = BodyLabel(self._title)
        setFont(t, 11)
        top_row.addWidget(t)
        top_row.addStretch()
        self.value_label = BodyLabel("--")
        setFont(self.value_label, 11, QFont.Bold)
        top_row.addWidget(self.value_label)
        layout.addLayout(top_row)

        # 进度条
        self.progress = QProgressBar()
        self.progress.setRange(0, 100)
        self.progress.setValue(0)
        self.progress.setFixedHeight(6)
        self.progress.setTextVisible(False)
        self.progress.setStyleSheet("""
            QProgressBar {
                background-color: rgba(255,255,255,0.06);
                border: none;
                border-radius: 3px;
            }
            QProgressBar::chunk {
                border-radius: 3px;
                background: qlineargradient(x1:0, y1:0, x2:1, y2:0,
                    stop:0 #60CDFF, stop:1 #4FC3F7);
            }
        """)
        layout.addWidget(self.progress)

    def setValue(self, pct):
        """设置百分比值（0-100），自动更新颜色"""
        self._value = pct
        self.value_label.setText(f"{pct:.0f}%")
        self.progress.setValue(int(pct))
        # 根据值动态着色
        if pct >= 90:
            color = "#FF6B6B"
            gradient = "#FF6B6B, stop:1 #FF8A80"
        elif pct >= 70:
            color = "#FFB74D"
            gradient = "#FFB74D, stop:1 #FFCC80"
        else:
            color = "#60CDFF"
            gradient = "#60CDFF, stop:1 #4FC3F7"
        self.progress.setStyleSheet(f"""
            QProgressBar {{
                background-color: rgba(255,255,255,0.06);
                border: none;
                border-radius: 3px;
            }}
            QProgressBar::chunk {{
                border-radius: 3px;
                background: qlineargradient(x1:0, y1:0, x2:1, y2:0,
                    stop:0 {gradient});
            }}
        """)


class ConnectionDot(QWidget):
    """连接状态圆点指示器"""
    def __init__(self, parent=None):
        super().__init__(parent)
        self._connected = False
        self.setFixedSize(12, 12)

    def setConnected(self, connected):
        self._connected = connected
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)
        color = QColor("#6CCB5F") if self._connected else QColor("#FF6B6B")
        # 外圈发光
        painter.setPen(Qt.NoPen)
        glow = QColor(color)
        glow.setAlpha(60)
        painter.setBrush(glow)
        painter.drawEllipse(0, 0, 12, 12)
        # 内圈实心
        painter.setBrush(color)
        painter.drawEllipse(2, 2, 8, 8)


class DashboardPage(QWidget):
    def __init__(self, manager):
        super().__init__()
        self.manager = manager
        self._setup_ui()
        self._connect_signals()
        if self.manager.phone_connected:
            self._on_connection_changed(True, self.manager.current_channel)
        else:
            self._on_connection_changed(False, self.manager.current_channel)
        self._refresh_timer = QTimer(self)
        self._refresh_timer.timeout.connect(self._refresh_pc_stats)
        self._refresh_timer.start(1000)
        self._refresh_pc_stats()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 20, 24, 20)
        layout.setSpacing(16)

        # ===== 标题栏 =====
        header = QHBoxLayout()
        header.setSpacing(12)
        title = TitleLabel("仪表盘")
        setFont(title, 26, QFont.Bold)
        header.addWidget(title)
        header.addStretch()
        # 连接状态指示
        self.conn_dot = ConnectionDot()
        header.addWidget(self.conn_dot)
        self.conn_status_label = BodyLabel("未连接")
        setFont(self.conn_status_label, 12)
        header.addWidget(self.conn_status_label)
        layout.addLayout(header)

        # ===== 上半部分：状态卡片 + 统计网格 =====
        top_row = QHBoxLayout()
        top_row.setSpacing(12)

        # --- 连接信息卡片 ---
        status_card = CardWidget()
        status_card.setFixedWidth(240)
        status_layout = QVBoxLayout(status_card)
        status_layout.setContentsMargins(16, 16, 16, 16)
        status_layout.setSpacing(10)

        status_title = SubtitleLabel("连接信息")
        setFont(status_title, 14)
        status_layout.addWidget(status_title)

        # 键值对行
        for key_text, attr_name, default_val in [
            ("通    道", "channel_label", "无"),
            ("本机 IP", "pc_ip_label", self.manager.local_ip),
            ("监听端口", "pc_port_label", str(self.manager.port)),
            ("网络速率", "net_label", "--"),
        ]:
            row = QHBoxLayout()
            row.setSpacing(0)
            k = BodyLabel(key_text)
            setFont(k, 11)
            k.setStyleSheet("color: rgba(255,255,255,0.45);")
            row.addWidget(k)
            row.addSpacing(16)
            v = BodyLabel(default_val)
            setFont(v, 11)
            row.addWidget(v)
            row.addStretch()
            status_layout.addLayout(row)
            setattr(self, attr_name, v)

        status_layout.addStretch()
        top_row.addWidget(status_card)

        # --- 统计网格卡片 ---
        stats_card = CardWidget()
        stats_card.setMinimumWidth(380)
        stats_grid = QGridLayout(stats_card)
        stats_grid.setSpacing(4)
        stats_grid.setContentsMargins(8, 8, 8, 8)

        # 6 个统计项：2列 x 3行
        self.pc_mem_stat = StatProgressBar("电脑内存")
        stats_grid.addWidget(self.pc_mem_stat, 0, 0)

        self.phone_mem_stat = StatProgressBar("手机内存")
        stats_grid.addWidget(self.phone_mem_stat, 0, 1)

        self.pc_disk_stat = StatProgressBar("电脑磁盘")
        stats_grid.addWidget(self.pc_disk_stat, 1, 0)

        self.phone_disk_stat = StatProgressBar("手机磁盘")
        stats_grid.addWidget(self.phone_disk_stat, 1, 1)

        self.phone_battery_stat = StatProgressBar("手机电量")
        stats_grid.addWidget(self.phone_battery_stat, 2, 0)

        self.phone_temp_stat = StatProgressBar("手机温度")
        stats_grid.addWidget(self.phone_temp_stat, 2, 1)

        top_row.addWidget(stats_card, 1)
        layout.addLayout(top_row)

        # ===== 快捷操作栏 =====
        actions_card = CardWidget()
        actions_layout = QHBoxLayout(actions_card)
        actions_layout.setContentsMargins(12, 8, 12, 8)
        actions_layout.setSpacing(8)

        self.send_clip_btn = PushButton("  推送剪贴板")
        self.send_clip_btn.setIcon(FIF.COPY)
        actions_layout.addWidget(self.send_clip_btn)

        self.send_text_btn = PushButton("  发送文字")
        self.send_text_btn.setIcon(FIF.CHAT)
        actions_layout.addWidget(self.send_text_btn)

        self.open_recv_btn = PrimaryPushButton("  打开接收文件夹")
        self.open_recv_btn.setIcon(FIF.FOLDER)
        actions_layout.addWidget(self.open_recv_btn)

        actions_layout.addStretch()

        # 手机网络状态
        self.phone_net_label = BodyLabel("--")
        setFont(self.phone_net_label, 11)
        self.phone_net_label.setStyleSheet("color: rgba(255,255,255,0.45);")
        actions_layout.addWidget(self.phone_net_label)

        layout.addWidget(actions_card)

        # ===== 手机进程管理 =====
        proc_card = CardWidget()
        proc_layout = QVBoxLayout(proc_card)
        proc_layout.setContentsMargins(16, 16, 16, 16)
        proc_layout.setSpacing(10)
        proc_title = SubtitleLabel("手机进程管理")
        setFont(proc_title, 14)
        proc_layout.addWidget(proc_title)
        self.proc_widget = ProcessManagerWidget(self.manager)
        proc_layout.addWidget(self.proc_widget, 1)
        layout.addWidget(proc_card, 3)

    def _connect_signals(self):
        self.manager.connection_status_changed.connect(self._on_connection_changed)
        self.manager.phone_status_received.connect(self._on_phone_status)
        self.open_recv_btn.clicked.connect(self._open_recv_folder)
        self.send_clip_btn.clicked.connect(self._send_current_clipboard)
        self.send_text_btn.clicked.connect(self._send_text)

    def _on_connection_changed(self, connected, channel):
        print(f"[Dashboard] _on_connection_changed: connected={connected}, channel={channel}")
        if connected:
            self.conn_status_label.setText("已连接")
            self.conn_status_label.setStyleSheet("color: #6CCB5F; font-weight: 500;")
            self.conn_dot.setConnected(True)
            channel_names = {"wifi": "WiFi 直连", "paw": "PAW 中转", "adb": "USB 数据线"}
            self.channel_label.setText(channel_names.get(channel, channel))
            self.proc_widget._refresh()
        else:
            self.conn_status_label.setText("未连接")
            self.conn_status_label.setStyleSheet("color: #FF6B6B;")
            self.conn_dot.setConnected(False)
            self.channel_label.setText("无")
            self.phone_mem_stat.setValue(0)
            self.phone_disk_stat.setValue(0)
            self.phone_battery_stat.setValue(0)
            self.phone_temp_stat.value_label.setText("--")
            self.phone_net_label.setText("--")
            self.proc_widget._refresh()

    def _refresh_pc_stats(self):
        try:
            mem = psutil.virtual_memory()
            self.pc_mem_stat.setValue(mem.percent)
            try:
                disk = psutil.disk_usage(os.path.expanduser('~'))
            except Exception:
                disk = psutil.disk_usage('C:\\')
            disk_pct = int((disk.used / disk.total) * 100)
            self.pc_disk_stat.setValue(disk_pct)
            # 网络速率
            net = psutil.net_io_counters()
            now_sent = net.bytes_sent
            now_recv = net.bytes_recv
            if not hasattr(self, '_last_bytes_sent'):
                self._last_bytes_sent = now_sent
                self._last_bytes_recv = now_recv
                self._last_net_time = time.time()
            else:
                t = time.time()
                dt = t - self._last_net_time
                if dt > 0:
                    up = (now_sent - self._last_bytes_sent) / dt / 1024
                    down = (now_recv - self._last_bytes_recv) / dt / 1024
                    self.net_label.setText(f"↑{up:.1f}  ↓{down:.1f} KB/s")
                self._last_bytes_sent = now_sent
                self._last_bytes_recv = now_recv
                self._last_net_time = t
        except Exception:
            pass

    def _on_phone_status(self, status):
        try:
            mem = status.get('memory_usage')
            if mem is not None:
                self.phone_mem_stat.setValue(float(mem))
            storage_total = status.get('storage_total', 0)
            storage_free = status.get('storage_free', 0)
            if storage_total and storage_total > 0:
                disk_pct = int((1 - storage_free / storage_total) * 100)
                self.phone_disk_stat.setValue(disk_pct)
            battery = status.get('battery')
            if battery is not None:
                self.phone_battery_stat.setValue(int(battery))
            temp = status.get('temperature')
            if temp is not None:
                self.phone_temp_stat.value_label.setText(f"{float(temp):.1f}°C")
                # 温度映射到 0-100 范围（0°C~50°C）
                temp_pct = min(max(float(temp) / 50 * 100, 0), 100)
                self.phone_temp_stat.progress.setValue(int(temp_pct))
            net = status.get('network')
            if net:
                net_map = {"wifi": "WiFi", "mobile": "移动数据", "ethernet": "以太网", "none": "无网络", "unknown": "未知"}
                self.phone_net_label.setText(f"手机网络: {net_map.get(net, net)}")
        except Exception:
            pass

    def _open_recv_folder(self):
        import subprocess
        path = self.manager.receive_dir
        os.makedirs(path, exist_ok=True)
        subprocess.Popen(f'explorer "{path}"')

    def _send_current_clipboard(self):
        try:
            import pyperclip
            text = pyperclip.paste()
            if text:
                self.manager.send_clipboard(text)
                InfoBar.success("已推送", "剪贴板内容已发送到手机", parent=self,
                                duration=2000, position=InfoBarPosition.TOP)
        except Exception:
            pass

    def _send_text(self):
        from PyQt5.QtWidgets import QInputDialog, QLineEdit
        text, ok = QInputDialog.getText(self, "发送文字", "请输入要发送到手机的文字:", QLineEdit.Normal)
        if ok and text:
            self.manager.send_text(text)
            InfoBar.success("已发送", "文字已发送到手机", parent=self,
                            duration=2000, position=InfoBarPosition.TOP)
