import os
import time
import psutil
from PyQt5.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout, QGridLayout,
                               QFrame, QSizePolicy, QProgressBar)
from PyQt5.QtCore import Qt, QTimer, QSize
from PyQt5.QtGui import QFont, QColor, QPainter
from styles import _c
from qfluentwidgets import (CardWidget, TitleLabel, BodyLabel, SubtitleLabel,
                            PushButton, PrimaryPushButton,
                            setFont, FluentIcon as FIF,
                            InfoBar, InfoBarPosition)


class StatProgressBar(QWidget):
    """带进度条的统计卡片"""
    def __init__(self, title, parent=None):
        super().__init__(parent)
        self._title = title
        self.setMinimumSize(160, 80)
        self.setSizePolicy(QSizePolicy.Preferred, QSizePolicy.Preferred)
        self._setup_ui()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 10, 12, 10)
        layout.setSpacing(6)
        top_row = QHBoxLayout()
        t = BodyLabel(self._title)
        setFont(t, 11)
        top_row.addWidget(t)
        top_row.addStretch()
        self.value_label = BodyLabel("--")
        setFont(self.value_label, 11, QFont.Bold)
        top_row.addWidget(self.value_label)
        layout.addLayout(top_row)
        self.progress = QProgressBar()
        self.progress.setRange(0, 100)
        self.progress.setValue(0)
        self.progress.setFixedHeight(6)
        self.progress.setTextVisible(False)
        self._apply_color("#60CDFF", "#4FC3F7")
        layout.addWidget(self.progress)

    def _apply_color(self, c1, c2):
        self.progress.setStyleSheet(
            f"QProgressBar {{background:rgba(255,255,255,0.06);border:none;border-radius:3px;}}"
            f"QProgressBar::chunk {{border-radius:3px;background:qlineargradient(x1:0,y1:0,x2:1,y2:0,stop:0 {c1},stop:1 {c2});}}"
        )

    def setValue(self, pct):
        self.value_label.setText(f"{pct:.0f}%")
        self.progress.setValue(int(pct))
        # 仅在档位跨越阈值时切换样式，避免每秒 setStyleSheet 重解析 QSS
        tier = 0 if pct >= 90 else (1 if pct >= 70 else 2)
        if getattr(self, '_color_tier', None) != tier:
            self._color_tier = tier
            if tier == 0:
                self._apply_color("#FF6B6B", "#FF8A80")
            elif tier == 1:
                self._apply_color("#FFB74D", "#FFCC80")
            else:
                self._apply_color("#60CDFF", "#4FC3F7")


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
        painter.setPen(Qt.NoPen)
        glow = QColor(color); glow.setAlpha(60)
        painter.setBrush(glow)
        painter.drawEllipse(0, 0, 12, 12)
        painter.setBrush(color)
        painter.drawEllipse(2, 2, 8, 8)


class DashboardPage(QWidget):
    def __init__(self, manager):
        super().__init__()
        self.manager = manager
        self._last_connected = False
        self._setup_ui()
        self._connect_signals()
        # 初始化连接状态
        self._on_connection_changed(self.manager.phone_connected, self.manager.current_channel)
        # 1秒定时器刷新本地统计（仅页面可见时运行）
        self._timer = QTimer(self)
        self._timer.timeout.connect(self._refresh_local_stats)
        self._timer.setInterval(1000)
        self._refresh_local_stats()

    def showEvent(self, event):
        super().showEvent(event)
        self._timer.start()

    def hideEvent(self, event):
        super().hideEvent(event)
        self._timer.stop()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 20, 24, 20)
        layout.setSpacing(16)

        # 标题栏
        header = QHBoxLayout()
        header.setSpacing(12)
        title = TitleLabel("仪表盘")
        setFont(title, 26, QFont.Bold)
        header.addWidget(title)
        header.addStretch()
        self.conn_dot = ConnectionDot()
        header.addWidget(self.conn_dot)
        self.conn_status_label = BodyLabel("未连接")
        setFont(self.conn_status_label, 12)
        header.addWidget(self.conn_status_label)
        layout.addLayout(header)

        # 上半部分：状态卡片 + 统计网格
        top_row = QHBoxLayout()
        top_row.setSpacing(12)

        # 连接信息卡片
        status_card = CardWidget()
        status_card.setFixedWidth(240)
        sl = QVBoxLayout(status_card)
        sl.setContentsMargins(16, 16, 16, 16)
        sl.setSpacing(10)
        sl.addWidget(SubtitleLabel("连接信息"))
        for key, attr, default in [
            ("通    道", "channel_label", "无"),
            ("本机 IP", "pc_ip_label", self.manager.local_ip),
            ("监听端口", "pc_port_label", str(self.manager.port)),
            ("网络速率", "net_label", "--"),
        ]:
            row = QHBoxLayout()
            k = BodyLabel(key); setFont(k, 11)
            k.setStyleSheet("color:rgba(255,255,255,0.45);")
            k.setFixedWidth(72)
            row.addWidget(k)
            v = BodyLabel(default); setFont(v, 11)
            row.addWidget(v)
            row.addStretch()
            sl.addLayout(row)
            setattr(self, attr, v)
        sl.addStretch()
        top_row.addWidget(status_card)

        # 统计网格
        stats_card = CardWidget()
        stats_card.setMinimumWidth(380)
        sg = QGridLayout(stats_card)
        sg.setSpacing(4); sg.setContentsMargins(8, 8, 8, 8)
        sg.setColumnStretch(0, 1); sg.setColumnStretch(1, 1)
        self.pc_mem_stat = StatProgressBar("电脑内存"); sg.addWidget(self.pc_mem_stat, 0, 0)
        self.phone_mem_stat = StatProgressBar("手机内存"); sg.addWidget(self.phone_mem_stat, 0, 1)
        self.pc_disk_stat = StatProgressBar("电脑磁盘"); sg.addWidget(self.pc_disk_stat, 1, 0)
        self.phone_disk_stat = StatProgressBar("手机磁盘"); sg.addWidget(self.phone_disk_stat, 1, 1)
        self.phone_battery_stat = StatProgressBar("手机电量"); sg.addWidget(self.phone_battery_stat, 2, 0)
        self.phone_temp_stat = StatProgressBar("手机温度"); sg.addWidget(self.phone_temp_stat, 2, 1)
        top_row.addWidget(stats_card, 1)
        layout.addLayout(top_row)

        # 快捷操作栏
        actions_card = CardWidget()
        al = QHBoxLayout(actions_card)
        al.setContentsMargins(12, 8, 12, 8); al.setSpacing(8)
        self.send_clip_btn = PushButton("推送剪贴板"); self.send_clip_btn.setIcon(FIF.COPY)
        al.addWidget(self.send_clip_btn)
        self.send_text_btn = PushButton("发送文字"); self.send_text_btn.setIcon(FIF.CHAT)
        al.addWidget(self.send_text_btn)
        self.open_recv_btn = PrimaryPushButton("打开接收文件夹"); self.open_recv_btn.setIcon(FIF.FOLDER)
        al.addWidget(self.open_recv_btn)
        al.addStretch()
        self.phone_net_label = BodyLabel("--")
        setFont(self.phone_net_label, 11)
        self.phone_net_label.setStyleSheet("color:rgba(255,255,255,0.45);")
        al.addWidget(self.phone_net_label)
        layout.addWidget(actions_card)
        layout.addStretch()

    def _connect_signals(self):
        self.manager.connection_status_changed.connect(self._on_connection_changed)
        self.manager.phone_status_received.connect(self._on_phone_status)
        self.open_recv_btn.clicked.connect(self._open_recv_folder)
        self.send_clip_btn.clicked.connect(self._send_clipboard)
        self.send_text_btn.clicked.connect(self._send_text)

    def _on_connection_changed(self, connected, channel):
        c = _c()
        if connected:
            self.conn_status_label.setText("已连接")
            self.conn_status_label.setStyleSheet(f"color:{c['success']};font-weight:500;")
            self.conn_dot.setConnected(True)
            names = {"wifi": "WiFi 直连", "paw": "PAW 中转", "adb": "USB 数据线"}
            self.channel_label.setText(names.get(channel, channel))
        else:
            self.conn_status_label.setText("未连接")
            self.conn_status_label.setStyleSheet(f"color:{c['error']};")
            self.conn_dot.setConnected(False)
            self.channel_label.setText("无")
            # 仅在状态变化时重置手机数据，避免闪烁
            if self._last_connected:
                for w in (self.phone_mem_stat, self.phone_disk_stat, self.phone_battery_stat):
                    w.setValue(0)
                self.phone_temp_stat.value_label.setText("--")
                self.phone_temp_stat.progress.setValue(0)
                self.phone_net_label.setText("--")
        self._last_connected = connected

    def _refresh_local_stats(self):
        """每秒刷新本机统计（内存/磁盘/网络速率）"""
        try:
            mem = psutil.virtual_memory()
            self.pc_mem_stat.setValue(mem.percent)
        except Exception:
            pass
        try:
            try:
                disk = psutil.disk_usage(os.path.expanduser('~'))
            except Exception:
                disk = psutil.disk_usage('C:\\')
            self.pc_disk_stat.setValue(int(disk.used * 100 / disk.total))
        except Exception:
            pass
        try:
            net = psutil.net_io_counters()
            now_s, now_r = net.bytes_sent, net.bytes_recv
            if not hasattr(self, '_net_prev_s'):
                self._net_prev_s, self._net_prev_r, self._net_prev_t = now_s, now_r, time.time()
            else:
                dt = time.time() - self._net_prev_t
                if dt > 0:
                    up = (now_s - self._net_prev_s) / dt / 1024
                    down = (now_r - self._net_prev_r) / dt / 1024
                    self.net_label.setText(f"↑{up:.1f}  ↓{down:.1f} KB/s")
                self._net_prev_s, self._net_prev_r, self._net_prev_t = now_s, now_r, time.time()
        except Exception:
            pass

    def _on_phone_status(self, status):
        """手机端上报的状态数据"""
        try:
            mem = status.get('memory_usage')
            if mem is not None:
                self.phone_mem_stat.setValue(float(mem))
            st = status.get('storage_total', 0)
            sf = status.get('storage_free', 0)
            if st and st > 0:
                self.phone_disk_stat.setValue(int((1 - sf / st) * 100))
            bat = status.get('battery')
            if bat is not None:
                self.phone_battery_stat.setValue(int(bat))
            temp = status.get('temperature')
            if temp is not None:
                self.phone_temp_stat.value_label.setText(f"{float(temp):.1f}°C")
                self.phone_temp_stat.progress.setValue(min(int(float(temp) / 50 * 100), 100))
            net = status.get('network')
            if net:
                nm = {"wifi": "WiFi", "mobile": "移动数据", "ethernet": "以太网", "none": "无网络", "unknown": "未知"}
                self.phone_net_label.setText(f"手机网络: {nm.get(net, net)}")
        except Exception:
            pass

    def _open_recv_folder(self):
        import subprocess
        path = self.manager.receive_dir
        os.makedirs(path, exist_ok=True)
        subprocess.Popen(f'explorer "{path}"')

    def _send_clipboard(self):
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
