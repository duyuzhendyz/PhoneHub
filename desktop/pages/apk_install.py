import os
import threading
import time
import subprocess
from PyQt5.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout,
                               QFrame, QLabel, QListWidgetItem,
                               QMessageBox, QFileDialog,
                               QDialog)
from PyQt5.QtCore import Qt, pyqtSignal, pyqtSlot, QMimeData
from PyQt5.QtGui import QDragEnterEvent, QDropEvent, QFont
from styles import get_theme, _c, set_item_text_color, dark_dialog_style
from qfluentwidgets import (CardWidget, TitleLabel, BodyLabel, SubtitleLabel,
                            PushButton, PrimaryPushButton, ToolButton, ToggleButton,
                            LineEdit, CheckBox, ComboBox, setFont, FluentIcon as FIF,
                            InfoBar, InfoBarPosition, ListWidget, ProgressBar)

# APK 远程路径（与 connection_manager.py 中 ADB 推送路径保持一致）
APK_REMOTE_DIR = "/sdcard/Download/PhoneHub/"


class ApkInstallPage(QWidget):
    """APK 安装"""

    install_progress = pyqtSignal(int, str)  # percent, message
    install_done = pyqtSignal(bool, str)  # success, message

    def __init__(self, manager):
        super().__init__()
        self.manager = manager
        self._installing = False
        self._wifi_transfer_dialog = None  # WiFi 传输进度弹窗
        self._setup_ui()
        self._connect_signals()
        self.setAcceptDrops(True)
        self._update_button_states()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 20, 24, 20)
        layout.setSpacing(16)

        title = TitleLabel("APK 安装")
        title.setObjectName("titleLabel")
        setFont(title, 28, QFont.Bold)
        layout.addWidget(title)

        self.channel_label = SubtitleLabel("当前通道: --")
        layout.addWidget(self.channel_label)

        drop_frame = QFrame()
        drop_frame.setFixedHeight(120)
        c = _c()
        drop_frame.setStyleSheet(
            f"QFrame {{ background-color: {c['surface']}; "
            f"border: 2px dashed {c['accent']}; border-radius: 8px; cursor: pointer; }}"
        )
        drop_layout = QVBoxLayout(drop_frame)
        drop_layout.setContentsMargins(20, 20, 20, 20)
        drop_layout.setSpacing(6)
        drop_layout.setAlignment(Qt.AlignCenter)

        hint = QLabel("拖入 .apk 文件到此处或点击此处选择")
        hint.setAlignment(Qt.AlignCenter)
        c = _c()
        hint.setStyleSheet(f"color: {c['text']}; font-size: 16px; background-color: transparent; border: none;")
        drop_layout.addWidget(hint)

        hint2 = BodyLabel(".apk 格式，仅支持 ADB 通道")
        hint2.setAlignment(Qt.AlignCenter)
        hint2.setStyleSheet(f"color: {c['text_secondary']}; font-size: 14px; background-color: transparent; border: none;")
        drop_layout.addWidget(hint2)
        
        drop_frame.mousePressEvent = lambda event: self._on_drop_frame_click()  # 添加点击事件
        layout.addWidget(drop_frame)

        actions_frame = CardWidget()
        actions_layout = QHBoxLayout(actions_frame)

        # 合并后，选择按钮不再需要，因为点击拖放区域即可
        # self.select_btn = PrimaryPushButton("选择 APK 安装")
        # actions_layout.addWidget(self.select_btn)

        self.cancel_btn = PushButton("取消")
        self.cancel_btn.setEnabled(False)
        actions_layout.addWidget(self.cancel_btn)

        actions_layout.addStretch()
        layout.addWidget(actions_frame)

        progress_frame = CardWidget()
        progress_layout = QVBoxLayout(progress_frame)
        self.status_label = SubtitleLabel("等待中...")
        progress_layout.addWidget(self.status_label)
        self.progress_bar = ProgressBar()
        self.progress_bar.setRange(0, 100)
        progress_layout.addWidget(self.progress_bar)
        layout.addWidget(progress_frame)

        history_frame = CardWidget()
        history_layout = QVBoxLayout(history_frame)
        history_label = SubtitleLabel("安装历史")
        history_layout.addWidget(history_label)
        self.history_list = ListWidget()
        history_layout.addWidget(self.history_list)
        layout.addWidget(history_frame)

    def _connect_signals(self):
        # self.select_btn.clicked.connect(self._select_apk)  # 合并后不再需要
        self.cancel_btn.clicked.connect(self._cancel)
        self.install_progress.connect(self._on_progress)
        self.install_done.connect(self._on_done)
        try:
            self.manager.connection_status_changed.connect(lambda c, ch: self._update_button_states())
            # WiFi 文件传输信号
            self.manager.file_transfer_progress.connect(self._on_wifi_transfer_progress)
            self.manager.file_transfer_complete.connect(self._on_wifi_transfer_complete)
            # 传输被取消/失败时必须复位安装状态，否则页面永久锁死
            self.manager.file_transfer_cancelled.connect(self._on_transfer_cancelled)
        except Exception:
            pass

    def _on_transfer_cancelled(self, file_id):
        """对端取消传输：复位安装状态，避免 _installing 永久卡死"""
        if getattr(self, '_installing', False):
            self._installing = False
            self._close_wifi_dialog()
            self._pending_apk_path = None
            self._pending_apk_file_id = None
            self._update_button_states()
            self.cancel_btn.setEnabled(False)
            self.progress_bar.setRange(0, 0)
            self.status_label.setText("传输已取消")

    def _update_button_states(self):
        """根据通道更新按钮状态和标签：仅 ADB 通道可用"""
        try:
            ch = self.manager.current_channel
            if ch == "adb":
                self.channel_label.setText(f"当前通道: {ch} (ADB 静默安装)")
            elif ch == "wifi":
                self.channel_label.setText(f"当前通道: {ch} (WiFi 模式不支持 APK 安装，请使用 ADB)")
            else:
                self.channel_label.setText(f"当前通道: {ch} (未连接)")
            # 仅 ADB 通道且未在安装中时启用拖放区域（视觉上通过样式变化提示）
            # 由于 select_btn 已合并到拖放区，不再需要单独控制其状态
        except Exception:
            pass

    def _on_drop_frame_click(self):
        """点击拖放区域等同于点击选择按钮"""
        if not self._installing and self.manager.current_channel == "adb":
            self._select_apk()

    def dragEnterEvent(self, event: QDragEnterEvent):
        if event.mimeData().hasUrls():
            urls = event.mimeData().urls()
            for url in urls:
                if url.toLocalFile().lower().endswith('.apk'):
                    event.acceptProposedAction()
                    return
        event.ignore()

    def dropEvent(self, event: QDropEvent):
        for url in event.mimeData().urls():
            path = url.toLocalFile()
            if path.lower().endswith('.apk'):
                self._install_apk(path)
                break

    def _select_apk(self):
        file_path, _ = QFileDialog.getOpenFileName(self, "选择 APK", "", "APK 文件 (*.apk)")
        if file_path:
            self._install_apk(file_path)

    def _install_apk(self, apk_path):
        """安装 APK：统一走文件传输模块，手机端收到完整 APK 后自动触发安装。"""
        if self._installing:
            return
        ch = self.manager.current_channel
        if ch not in ("adb", "wifi"):
            self._show_message(QMessageBox.Warning, "通道不可用", "请先通过 ADB 或 WiFi 连接手机。")
            return
        if not os.path.exists(apk_path):
            self._show_message(QMessageBox.Warning, "文件不存在", apk_path)
            return
        self._installing = True
        self.cancel_btn.setEnabled(True)
        self.progress_bar.setValue(0)
        self._pending_apk_path = apk_path
        ok = self.manager.send_file(apk_path)
        if not ok:
            self._installing = False
            self._update_button_states()
            self._show_message(QMessageBox.Warning, "发送失败", "无法启动文件传输，请检查连接状态。")
            return
        # 记录本次传输的 file_id，完成回调据此精确匹配，避免其他无关传输误触发 install_apk
        self._pending_apk_file_id = getattr(self.manager, 'outgoing_file_id', None)
        self.status_label.setText("已交给文件传输，请在文件传输页面查看进度")
        self.progress_bar.setRange(0, 0)
        self.progress_bar.setValue(0)
        InfoBar.info("APK 推送到文件传输", "已将 {} 交给文件传输模块，手机端接收完成后自动安装。".format(os.path.basename(apk_path)),
                     parent=self, duration=3000, position=InfoBarPosition.TOP)

    def _install_wifi(self, apk_path):
        """WiFi 模式：通过 send_file 传输 APK，传输成功后手机端自动安装"""
        file_name = os.path.basename(apk_path)
        file_size = os.path.getsize(apk_path)

        # 创建深色模式传输进度弹窗
        self._wifi_transfer_dialog = QDialog(self)
        self._wifi_transfer_dialog.setWindowTitle("正在传输 APK")
        self._wifi_transfer_dialog.setModal(True)
        self._wifi_transfer_dialog.setFixedSize(400, 150)
        self._wifi_transfer_dialog.setStyleSheet(dark_dialog_style())
        dlg_layout = QVBoxLayout(self._wifi_transfer_dialog)
        dlg_layout.setContentsMargins(20, 20, 20, 20)
        dlg_layout.setSpacing(10)

        self._wifi_transfer_label = BodyLabel(f"正在传输 {file_name} ({file_size/1024/1024:.1f} MB)...")
        dlg_layout.addWidget(self._wifi_transfer_label)

        self._wifi_transfer_bar = ProgressBar()
        self._wifi_transfer_bar.setRange(0, 100)
        self._wifi_transfer_bar.setValue(0)
        dlg_layout.addWidget(self._wifi_transfer_bar)

        self._wifi_transfer_status = SubtitleLabel("等待手机接收...")
        dlg_layout.addWidget(self._wifi_transfer_status)

        self._wifi_transfer_apk_path = apk_path
        self._wifi_transfer_dialog.show()

        # 调用 send_file 发送 APK
        try:
            result = self.manager.send_file(apk_path)
            if not result:
                self._close_wifi_dialog()
                self._installing = False
                self._update_button_states()
                self._show_message(QMessageBox.Warning, "发送失败", "无法启动文件传输，请检查连接。")
        except Exception as e:
            self._close_wifi_dialog()
            self._installing = False
            self._update_button_states()
            self._show_message(QMessageBox.Warning, "发送失败", str(e))

    def _close_wifi_dialog(self):
        """关闭 WiFi 传输弹窗"""
        if self._wifi_transfer_dialog:
            self._wifi_transfer_dialog.close()
            self._wifi_transfer_dialog = None

    @pyqtSlot(str, int, int, float)
    def _on_wifi_transfer_progress(self, file_id, sent, total, ts):
        """WiFi 文件传输进度回调"""
        if not self._wifi_transfer_dialog:
            return
        if total > 0:
            pct = int(sent * 100 / total)
            self._wifi_transfer_bar.setValue(pct)
            sent_mb = sent / 1024 / 1024
            total_mb = total / 1024 / 1024
            self._wifi_transfer_status.setText(f"已传输 {sent_mb:.1f} / {total_mb:.1f} MB")

    @pyqtSlot(str, str)
    def _on_wifi_transfer_complete(self, file_id, file_path):
        """文件传输完成回调：仅当本次完成任务确为 APK 传输时才通知手机端自动安装。"""
        self._close_wifi_dialog()
        pending = getattr(self, '_pending_apk_path', None)
        if not pending:
            return
        # 精确匹配：file_id 不一致说明完成的是其他传输任务，不触发安装
        expected_id = getattr(self, '_pending_apk_file_id', None)
        if expected_id is not None and expected_id != file_id:
            return
        file_name = os.path.basename(pending)
        try:
            # 使用常量统一远程安装目录
            self.manager.send_action("install_apk", {"path": APK_REMOTE_DIR + file_name})
        except Exception:
            pass
        self._pending_apk_path = None
        self._pending_apk_file_id = None
        self.install_done.emit(True, f"APK 已传输到手机: {file_name}\n手机端将自动安装。")

    @pyqtSlot(int, str)
    def _on_progress(self, percent, message):
        self.progress_bar.setValue(percent)
        self.status_label.setText(message)

    @pyqtSlot(bool, str)
    def _on_done(self, success, message):
        self._installing = False
        self._update_button_states()
        self.cancel_btn.setEnabled(False)
        self.progress_bar.setValue(100 if success else self.progress_bar.value())
        self.status_label.setText(message)
        prefix = "[成功]" if success else "[失败]"
        item = QListWidgetItem(f"[{time.strftime('%H:%M:%S')}] {prefix} {message}")
        set_item_text_color(item)
        self.history_list.addItem(item)

    def _cancel(self):
        # APK 安装是一次性 push + install, 无法中途取消, 只能提示
        self._show_message(QMessageBox.Information, "提示", "APK 安装为原子操作, 无法中途取消。请等待完成。")

    # ==================== 深色弹窗辅助方法 ====================

    def _show_message(self, icon, title, text):
        """显示深色模式兼容的消息弹窗"""
        msg = QMessageBox(self)
        msg.setIcon(icon)
        msg.setWindowTitle(title)
        msg.setText(text)
        msg.setStyleSheet(dark_dialog_style())
        return msg.exec_()
