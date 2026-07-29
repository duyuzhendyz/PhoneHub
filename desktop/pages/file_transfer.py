import os
import time
import subprocess
from PyQt5.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout,
                               QListWidgetItem,
                               QFileDialog, QDialog)
from PyQt5.QtCore import Qt, QTimer
from PyQt5.QtGui import QFont
from qfluentwidgets import (CardWidget, TitleLabel, BodyLabel,
                            PushButton, PrimaryPushButton,
                            ListWidget, LineEdit, ProgressBar,
                            InfoBar, InfoBarPosition,
                            setFont, FluentIcon as FIF)
from styles import get_theme, _c, set_item_text_color, dark_dialog_style, dark_msg_box


class ConflictDialog(QDialog):
    """同名冲突弹窗：覆盖/重命名/跳过（兼容深色模式）"""
    OVERWRITE = 1
    RENAME = 2
    SKIP = 3

    def __init__(self, filename, parent=None):
        super().__init__(parent)
        self.setWindowTitle("文件冲突")
        self.setWindowFlags(self.windowFlags() & ~Qt.WindowContextHelpButtonHint)
        self.choice = ConflictDialog.SKIP
        self._setup_ui(filename)
        # 应用深色模式样式
        self.setStyleSheet(dark_dialog_style())
        try:
            from styles import apply_dark_title_bar
            apply_dark_title_bar(self)
        except Exception:
            pass

    def _setup_ui(self, filename):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(12)

        label = BodyLabel(f"接收目录已存在同名文件:\n{filename}\n\n请选择处理方式:")
        label.setWordWrap(True)
        layout.addWidget(label)

        btn_row = QHBoxLayout()
        btn_overwrite = PrimaryPushButton("覆盖")
        btn_overwrite.clicked.connect(self._choose_overwrite)
        btn_rename = PushButton("重命名")
        btn_rename.clicked.connect(self._choose_rename)
        btn_skip = PushButton("跳过")
        btn_skip.clicked.connect(self._choose_skip)
        btn_row.addWidget(btn_overwrite)
        btn_row.addWidget(btn_rename)
        btn_row.addWidget(btn_skip)
        layout.addLayout(btn_row)


    def _choose_overwrite(self):
        self.choice = ConflictDialog.OVERWRITE
        self.accept()

    def _choose_rename(self):
        self.choice = ConflictDialog.RENAME
        self.accept()

    def _choose_skip(self):
        self.choice = ConflictDialog.SKIP
        self.reject()


class FileTransferPage(QWidget):
    RECEIVE_DIR = r"F:\desk\手机上传"

    def __init__(self, manager):
        super().__init__()
        self.manager = manager
        # 速度采样窗口：保存最近 N 个 (timestamp, sent_bytes) 样本
        self._speed_samples = []  # [(ts, sent_bytes), ...]
        self._current_file_name = ""
        self._current_file_size = 0
        self._state = "idle"  # idle / sending / receiving / done
        self._completed_records = []  # 未点击"完成"前的传输记录，点击后清空
        self._last_done_file_id = None
        self._progress_emitted = False  # 标记：当前传输是否已发出过真实进度
        # 启动 500ms 刷新定时器（速度/ETA 仅在定时器中更新，避免每次进度信号都重算导致闪动）
        self._ui_timer = QTimer(self)
        self._ui_timer.setInterval(500)
        self._ui_timer.timeout.connect(self._refresh_speed_label)
        self._ui_timer.start()
        self._setup_ui()
        self._connect_signals()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(12)

        title = TitleLabel("文件传输")
        setFont(title, 28, QFont.Bold)
        layout.addWidget(title)

        actions_frame = CardWidget()
        actions_layout = QHBoxLayout(actions_frame)

        self.send_file_btn = PrimaryPushButton("选择文件发送")
        actions_layout.addWidget(self.send_file_btn)

        self.pause_btn = PushButton("暂停")
        self.pause_btn.setEnabled(False)
        actions_layout.addWidget(self.pause_btn)

        self.cancel_btn = PushButton("取消传输")
        self.cancel_btn.setEnabled(False)
        actions_layout.addWidget(self.cancel_btn)

        self.done_btn = PushButton("完成")
        self.done_btn.setEnabled(False)
        self.done_btn.setVisible(False)  # 传输完成前隐藏
        actions_layout.addWidget(self.done_btn)

        self.open_recv_btn = PushButton("打开接收文件夹")
        actions_layout.addWidget(self.open_recv_btn)

        actions_layout.addStretch()
        layout.addWidget(actions_frame)

        self.progress_frame = CardWidget()
        progress_layout = QVBoxLayout(self.progress_frame)

        self.current_file_label = BodyLabel("等待传输...")
        progress_layout.addWidget(self.current_file_label)

        self.progress_bar = ProgressBar()
        self.progress_bar.setRange(0, 100)
        self.progress_bar.setValue(0)
        progress_layout.addWidget(self.progress_bar)

        info_row = QHBoxLayout()
        self.speed_label = BodyLabel("")
        info_row.addWidget(self.speed_label)
        info_row.addStretch()
        self.eta_label = BodyLabel("")
        info_row.addWidget(self.eta_label)
        progress_layout.addLayout(info_row)

        layout.addWidget(self.progress_frame)
        self.progress_frame.setVisible(False)  # 默认隐藏，传输时显示

        history_frame = CardWidget()
        history_layout = QVBoxLayout(history_frame)
        history_layout.addWidget(BodyLabel("传输历史"))
        self.history_list = ListWidget()
        history_layout.addWidget(self.history_list)
        layout.addWidget(history_frame)

    def _connect_signals(self):
        self.send_file_btn.clicked.connect(self._select_file)
        self.pause_btn.clicked.connect(self._toggle_pause)
        self.cancel_btn.clicked.connect(self._cancel_transfer)
        self.done_btn.clicked.connect(self._on_done_clicked)
        self.open_recv_btn.clicked.connect(self._open_recv_folder)
        self.history_list.itemDoubleClicked.connect(self._on_history_double_clicked)
        self.manager.file_transfer_progress.connect(self._on_progress)
        self.manager.file_transfer_complete.connect(self._on_complete)
        self.manager.file_receive_started.connect(self._on_receive_started)
        self.manager.file_sent.connect(self._on_file_sent)
        # 对端取消传输时同步界面
        try:
            self.manager.file_transfer_cancelled.connect(self._on_remote_cancel)
        except Exception:
            pass
        # 对端暂停/继续时同步按钮状态
        try:
            self.manager.file_transfer_paused.connect(self._on_remote_paused)
        except Exception:
            pass

    def _on_remote_cancel(self, file_id):
        """对端取消传输时，本端也停止并提示用户"""
        self._state = "idle"
        self.manager.file_transfer_cancel = True
        self._speed_samples.clear()
        self._smooth_speed = 0.0
        self._progress_emitted = False
        self.progress_bar.setValue(0)
        self.speed_label.setText("对端已取消")
        self.eta_label.setText("")
        self.current_file_label.setText("等待传输...")
        self._update_action_buttons(sending=False)
        self.pause_btn.setText("暂停")
        self.done_btn.setVisible(False)
        self.done_btn.setEnabled(False)
        self.progress_frame.setVisible(False)
        InfoBar.warning("传输已取消", "手机端已取消文件传输。", parent=self,
                         duration=3000, position=InfoBarPosition.TOP)

    def _on_remote_paused(self, paused):
        """对端暂停/继续时同步本端按钮状态"""
        if paused:
            self.pause_btn.setText("继续")
            self.speed_label.setText("已暂停(对端)")
            self.eta_label.setText("")
        else:
            self.pause_btn.setText("暂停")
            self.speed_label.setText("继续传输...")
            self._speed_samples.clear()
            self._smooth_speed = 0.0

    # ==================== 速度/ETA 稳定化 ====================

    def _push_speed_sample(self, ts, sent):
        """记录最近 4 秒内的样本（最多 8 个），用于平滑速度计算"""
        self._speed_samples.append((ts, sent))
        cutoff = ts - 4.0
        self._speed_samples = [(t, s) for (t, s) in self._speed_samples if t >= cutoff]
        if len(self._speed_samples) > 8:
            self._speed_samples = self._speed_samples[-8:]

    def _calc_smooth_speed(self, current_ts, current_sent):
        """基于样本窗口计算平均速度（bytes/sec），并做指数平滑避免闪动"""
        if len(self._speed_samples) < 2:
            return 0.0
        oldest_ts, oldest_sent = self._speed_samples[0]
        dt = current_ts - oldest_ts
        if dt <= 0.1:
            return 0.0
        bytes_delta = current_sent - oldest_sent
        if bytes_delta <= 0:
            return 0.0
        instant_speed = bytes_delta / dt
        # 指数平滑：上一帧 0.7 + 瞬时 0.3
        if not hasattr(self, '_smooth_speed') or self._smooth_speed <= 0:
            self._smooth_speed = instant_speed
        else:
            self._smooth_speed = self._smooth_speed * 0.7 + instant_speed * 0.3
        return self._smooth_speed

    def _refresh_speed_label(self):
        """定时器回调：刷新速度/ETA 显示（避免每个分块都重算）"""
        if self._state not in ("sending", "receiving"):
            return
        # 暂停期间不再计算速度和剩余时间
        if getattr(self.manager, '_transfer_paused', False):
            self.speed_label.setText("已暂停")
            self.eta_label.setText("")
            return
        if self._current_file_size <= 0:
            return
        now = time.time()
        # 取最近一次进度作为 sent
        if not self._speed_samples:
            return
        current_sent = self._speed_samples[-1][1]
        speed = self._calc_smooth_speed(now, current_sent)
        # 进度百分比
        pct = (current_sent * 100) / self._current_file_size if self._current_file_size > 0 else 0
        if speed > 0:
            speed_mb = speed / (1024 * 1024)
            if speed_mb >= 1:
                speed_str = f"{speed_mb:.2f} MB/s"
            else:
                speed_str = f"{speed / 1024:.0f} KB/s"
        else:
            speed_str = "0 KB/s"
        # 已传/总大小（自动选择单位）
        def fmt_size(b):
            if b >= 1024 * 1024 * 1024:
                return f"{b / (1024 * 1024 * 1024):.2f} GB"
            if b >= 1024 * 1024:
                return f"{b / (1024 * 1024):.1f} MB"
            if b >= 1024:
                return f"{b / 1024:.0f} KB"
            return f"{b} B"
        sent_str = fmt_size(current_sent)
        total_str = fmt_size(self._current_file_size)
        speed_text = f"{pct:.0f}%  ·  {sent_str} / {total_str}  ·  {speed_str}"
        remaining = self._current_file_size - current_sent
        if remaining > 0 and speed > 0:
            eta_sec = remaining / speed
            self.eta_label.setText(f"剩余: {self._format_eta(eta_sec)}")
        elif remaining <= 0:
            self.eta_label.setText("")
        self.speed_label.setText(speed_text)

    # ==================== 用户操作 ====================

    def _select_file(self):
        file_path, _ = QFileDialog.getOpenFileName(self, "选择文件")
        if not file_path:
            return
        ok = self.manager.send_file(file_path)
        if not ok:
            self._reset_to_idle()
            InfoBar.error("发送失败", "无法启动传输，请检查连接状态。", parent=self,
                          duration=3000, position=InfoBarPosition.TOP)
            return
        # 重置采样和状态
        self._speed_samples.clear()
        self._smooth_speed = 0.0
        self._current_file_name = os.path.basename(file_path)
        self._current_send_path = file_path  # 保存路径供暂停后继续使用
        self._current_file_size = 0  # 等首个真实进度信号再设置
        self._state = "sending"
        self._last_done_file_id = None
        self._progress_emitted = False
        self._reset_completion_ui()
        self._update_action_buttons(sending=True)
        self.current_file_label.setText(f"发送中: {self._current_file_name}")
        self.progress_bar.setValue(0)
        self.speed_label.setText("等待手机开始接收...")
        self.eta_label.setText("")
        self.progress_frame.setVisible(True)  # 显示进度条区域

    def _cancel_transfer(self):
        was_sending = self._state in ("sending", "receiving")
        self._state = "idle"
        self.manager.cancel_transfer()
        self._speed_samples.clear()
        self._smooth_speed = 0.0
        self._progress_emitted = False
        self.progress_bar.setValue(0)
        self.speed_label.setText("已取消")
        self.eta_label.setText("")
        self.current_file_label.setText("等待传输...")
        self._update_action_buttons(sending=False)
        self.pause_btn.setText("暂停")
        self.done_btn.setVisible(False)
        self.done_btn.setEnabled(False)
        self.progress_frame.setVisible(False)  # 隐藏进度条区域
        if was_sending:
            InfoBar.warning("传输已取消", "文件传输已取消。", parent=self,
                             duration=3000, position=InfoBarPosition.TOP)
        # 弹窗提示是否删除未完成的文件
        if was_sending and hasattr(self, '_current_send_path') and self._current_send_path:
            self._prompt_delete_file(self._current_send_path)

    def _prompt_delete_file(self, file_path):
        """取消传输后直接删除未完成的接收文件（不弹窗询问）"""
        try:
            recv_file = getattr(self.manager, 'current_receive_file', None)
            if recv_file:
                import os as _os
                prog = recv_file + '.progress'
                if _os.path.exists(prog):
                    try:
                        _os.remove(prog)
                    except Exception:
                        pass
                if _os.path.exists(recv_file):
                    try:
                        _os.remove(recv_file)
                    except Exception:
                        pass
        except Exception:
            pass

    def _toggle_pause(self):
        """暂停/继续切换：通过 transfer_control 消息通知对端"""
        if self._state not in ("sending", "receiving"):
            return
        if not getattr(self.manager, '_transfer_paused', False):
            # 暂停：通知对端暂停
            self.manager._transfer_paused = True
            self.manager.file_transfer_cancel = True
            file_id = getattr(self.manager, 'outgoing_file_id', '') or getattr(self.manager, 'current_file_id', '') or ''
            try:
                self.manager.send_transfer_control("pause", file_id)
            except Exception:
                pass
            self.pause_btn.setText("继续")
            self.speed_label.setText("已暂停")
            self.eta_label.setText("")
        else:
            # 继续：通知对端继续，对端收到 resume 后会重新发起传输
            self.manager._transfer_paused = False
            self.manager.file_transfer_cancel = False
            file_id = getattr(self.manager, 'outgoing_file_id', '') or getattr(self.manager, 'current_file_id', '') or ''
            try:
                self.manager.send_transfer_control("resume", file_id)
            except Exception:
                pass
            self.pause_btn.setText("暂停")
            self._speed_samples.clear()
            self._smooth_speed = 0.0
            self._progress_emitted = False
            if self._state == "sending":
                # PC→手机方向：保持 outgoing_file_id 不变，手机端收到 resume 后会重新下载
                # 不调用 send_file（会生成新 fileId 导致不匹配），由手机端主动重新发起 GET 请求
                self.progress_bar.setValue(0)
                self.speed_label.setText("等待手机重新下载...")
                self.progress_frame.setVisible(True)
            elif self._state == "receiving":
                # 手机→PC 方向：等待手机端重新上传
                self.speed_label.setText("等待手机重新上传...")

    def _on_done_clicked(self):
        """传输完成后的"完成"按钮：保存当前所有待确认的记录到历史，清空状态"""
        if not self._completed_records:
            # 没有待确认的记录时仅清空界面
            self._reset_to_idle()
            return
        for record in self._completed_records:
            item = QListWidgetItem(record)
            set_item_text_color(item)
            self.history_list.addItem(item)
        self._completed_records.clear()
        self._reset_to_idle()

    def _reset_to_idle(self):
        """重置界面到初始空闲状态"""
        self._state = "idle"
        self._speed_samples.clear()
        self._smooth_speed = 0.0
        self._current_file_name = ""
        self._current_file_size = 0
        self._last_done_file_id = None
        self._progress_emitted = False
        self.progress_bar.setValue(0)
        self.progress_frame.setVisible(False)  # 隐藏进度条区域
        self.speed_label.setText("")
        self.eta_label.setText("")
        self.current_file_label.setText("等待传输...")
        self._update_action_buttons(sending=False)
        self.pause_btn.setText("暂停")
        self.done_btn.setVisible(False)
        self.done_btn.setEnabled(False)

    def _reset_completion_ui(self):
        """每次新传输开始时，清除完成按钮状态"""
        self._completed_records.clear()
        self.done_btn.setVisible(False)
        self.done_btn.setEnabled(False)

    def _update_action_buttons(self, sending=False):
        """根据是否在传输中切换按钮可用性"""
        self.send_file_btn.setEnabled(not sending)
        self.pause_btn.setEnabled(sending)
        self.cancel_btn.setEnabled(sending)
        self.open_recv_btn.setEnabled(True)

    def _open_recv_folder(self):
        path = self.manager.receive_dir
        os.makedirs(path, exist_ok=True)
        subprocess.Popen(f'explorer "{path}"')

    def _on_history_double_clicked(self, item):
        """双击历史条目重新发送：从 '[时间] 已发送 xxx' 中恢复文件名。"""
        text = item.text().strip()
        marker = "] 已发送 "
        if marker not in text:
            InfoBar.warning("无法识别", "该历史记录不支持重新发送。", parent=self,
                            duration=3000, position=InfoBarPosition.TOP)
            return
        file_name = text.split(marker, 1)[1].strip()
        if not file_name:
            InfoBar.warning("文件缺失", "历史记录中的文件名无效。", parent=self,
                            duration=3000, position=InfoBarPosition.TOP)
            return
        target = os.path.join(self.manager.receive_dir, file_name)
        if not os.path.exists(target):
            InfoBar.warning("文件不存在", "历史文件已被删除或移动，无法再次发送。", parent=self,
                            duration=3000, position=InfoBarPosition.TOP)
            return
        # 如果当前有传输正在进行，先提示用户等待
        if self._state in ("sending", "receiving"):
            InfoBar.info("传输中", "请等待当前传输完成后，再重新发送历史文件。", parent=self,
                         duration=4000, position=InfoBarPosition.TOP)
            return
        self._select_from_path(target)

    def _select_from_path(self, file_path):
        ok = self.manager.send_file(file_path)
        if not ok:
            self._reset_to_idle()
            InfoBar.error("发送失败", "无法启动传输，请检查连接状态。", parent=self,
                          duration=3000, position=InfoBarPosition.TOP)
            return
        self._speed_samples.clear()
        self._smooth_speed = 0.0
        self._current_file_name = os.path.basename(file_path)
        self._current_send_path = file_path
        self._current_file_size = 0
        self._state = "sending"
        self._last_done_file_id = None
        self._progress_emitted = False
        self._reset_completion_ui()
        self._update_action_buttons(sending=True)
        self.current_file_label.setText(f"发送中: {self._current_file_name}")
        self.progress_bar.setValue(0)
        self.speed_label.setText("等待手机开始接收...")
        self.eta_label.setText("")
        self.progress_frame.setVisible(True)

    # ==================== 信号回调 ====================

    def _on_progress(self, file_id, sent, total, timestamp):
        if self._state not in ("sending", "receiving"):
            return
        if total <= 0:
            return
        # 关键修复：忽略初始的 sent==0 进度信号，不让它触发"完成"
        if sent <= 0:
            # 仅当之前已经发出过真实进度时才响应（避免在 0% 时多次重置）
            if self._progress_emitted:
                return
            self.progress_bar.setValue(0)
            return
        # 已收到真实进度
        self._progress_emitted = True
        pct = int((sent / total) * 100)
        self.progress_bar.setValue(pct)
        self._current_file_size = total
        now = timestamp if timestamp > 0 else time.time()
        self._push_speed_sample(now, sent)
        # 进入真实进度后，更新提示文本（去掉"等待手机开始接收..."）
        if "等待" in self.speed_label.text():
            self.speed_label.setText("")

    def _format_eta(self, sec):
        if sec < 0 or sec > 3600 * 24:
            return "--"
        if sec < 60:
            return f"{int(sec)} 秒"
        if sec < 3600:
            return f"{int(sec // 60)} 分 {int(sec % 60)} 秒"
        return f"{int(sec // 3600)} 时 {int((sec % 3600) // 60)} 分"

    def _resolve_conflict(self, filename):
        """兼容调用：立即返回已存在重名时的处理结果；弹窗逻辑走异步版本，避免阻塞主线程。"""
        target = os.path.join(self.manager.receive_dir, filename)
        if not os.path.exists(target):
            return filename
        dlg = ConflictDialog(filename, self)
        dlg.exec_()
        if dlg.choice == ConflictDialog.OVERWRITE:
            try:
                os.remove(target)
            except Exception:
                pass
            return filename
        elif dlg.choice == ConflictDialog.RENAME:
            base, ext = os.path.splitext(filename)
            i = 1
            while True:
                new_name = f"{base}({i}){ext}"
                if not os.path.exists(os.path.join(self.manager.receive_dir, new_name)):
                    return new_name
                i += 1
        else:
            return None

    def _resolve_conflict_async(self, file_name, file_size, file_id):
        """检测到重名时非阻塞弹窗，用户选择后立即决定接受/拒绝，减少卡顿感。"""
        target = os.path.join(self.manager.receive_dir, file_name)
        if not os.path.exists(target):
            self._accept_incoming_file(file_name, file_size, file_id)
            return
        dlg = ConflictDialog(file_name, self)
        dlg.finished.connect(lambda _: self._on_conflict_finished(dlg, file_name, file_size, file_id))
        dlg.show()
        QTimer.singleShot(0, dlg.raise_)

    def _on_conflict_finished(self, dlg, file_name, file_size, file_id):
        if dlg.choice == ConflictDialog.OVERWRITE:
            try:
                os.remove(os.path.join(self.manager.receive_dir, file_name))
            except Exception:
                pass
            self._accept_incoming_file(file_name, file_size, file_id)
        elif dlg.choice == ConflictDialog.RENAME:
            base, ext = os.path.splitext(file_name)
            i = 1
            while True:
                new_name = f"{base}({i}){ext}"
                if not os.path.exists(os.path.join(self.manager.receive_dir, new_name)):
                    break
                i += 1
            self._accept_incoming_file(new_name, file_size, file_id)
        else:
            try:
                self.manager.send_file_reject(file_id, "用户跳过")
                self.manager.cancel_transfer()
            except Exception:
                pass

    def _accept_incoming_file(self, resolved, file_size, file_id):
        if resolved != getattr(self, '_current_file_name', ""):
            try:
                self.manager.current_receive_file = os.path.join(self.manager.receive_dir, resolved)
            except Exception:
                pass
        try:
            self.manager.send_file_accept(file_id, resolved)
        except Exception:
            pass
        self._current_file_name = resolved
        self._current_file_size = file_size
        self._state = "receiving"
        self._last_done_file_id = None
        self._progress_emitted = False
        self._speed_samples.clear()
        self._smooth_speed = 0.0
        self._reset_completion_ui()
        self._update_action_buttons(sending=True)
        self.current_file_label.setText(f"接收中: {resolved}")
        self.progress_bar.setValue(0)
        self.speed_label.setText("等待手机上传...")
        self.eta_label.setText("")
        self.progress_frame.setVisible(True)

    def _on_complete(self, file_id, file_path):
        # 接收完成
        if self._state != "receiving":
            return
        if self._last_done_file_id == file_id:
            return
        self._last_done_file_id = file_id
        self._state = "done"
        self.progress_bar.setValue(100)
        self.speed_label.setText("传输完成")
        self.eta_label.setText("")
        name = os.path.basename(file_path) if file_path else self._current_file_name
        record = f"[{time.strftime('%H:%M:%S')}] 已接收: {name}"
        self._completed_records.append(record)
        self._show_completion_ui()

    def _on_receive_started(self, file_name, file_size, file_id):
        # 手机端发来的文件：弹出重名选择（非阻塞），选择后由回调接受/拒绝
        self._resolve_conflict_async(file_name, file_size, file_id)

    def _on_file_sent(self, file_id):
        # PC端发送完成（手机确认 file_complete）
        if self._state != "sending":
            return
        # 关键修复：从未收到真实进度就触发 file_sent，多半是 cancel/超时/通道错误
        # 不显示"完成"，而是当作传输失败重置
        if not self._progress_emitted:
            self._state = "idle"
            self._speed_samples.clear()
            self.progress_bar.setValue(0)
            self.speed_label.setText("传输未开始，已取消")
            self.eta_label.setText("")
            self.current_file_label.setText("等待传输...")
            self._update_action_buttons(sending=False)
            return
        # 关键修复：进度未到 100% 就不应触发完成
        if self._current_file_size > 0 and self._speed_samples:
            last_sent = self._speed_samples[-1][1]
            if last_sent < self._current_file_size * 0.95:
                # 进度不足 95%，忽略此次完成信号（多半是误触发）
                return
        if self._last_done_file_id == file_id:
            return
        self._last_done_file_id = file_id
        self._state = "done"
        self.progress_bar.setValue(100)
        self.speed_label.setText("发送完成")
        self.eta_label.setText("")
        record = f"[{time.strftime('%H:%M:%S')}] 已发送: {self._current_file_name}"
        self._completed_records.append(record)
        self._show_completion_ui()

    def _show_completion_ui(self):
        """传输完成后：禁用选择/取消按钮，显示"完成"按钮让用户确认"""
        self.send_file_btn.setEnabled(False)
        self.pause_btn.setEnabled(False)
        self.pause_btn.setText("暂停")
        self.cancel_btn.setEnabled(False)
        self.done_btn.setVisible(True)
        self.done_btn.setEnabled(True)
