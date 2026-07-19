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
        layout.setContentsMargins(16, 16, 16, 16)
        layout.setSpacing(12)

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
            f"border: 2px dashed {c['accent']}; border-radius: 8px; }}"
        )
        drop_layout = QVBoxLayout(drop_frame)
        drop_layout.setContentsMargins(20, 20, 20, 20)
        drop_layout.setSpacing(6)
        drop_layout.setAlignment(Qt.AlignCenter)

        hint = QLabel("拖入 .apk 文件到此处")
        hint.setAlignment(Qt.AlignCenter)
        c = _c()
        hint.setStyleSheet(f"color: {c['text']}; font-size: 16px; background-color: transparent; border: none;")
        drop_layout.addWidget(hint)

        hint2 = BodyLabel("或点击下方按钮选择文件")
        hint2.setAlignment(Qt.AlignCenter)
        drop_layout.addWidget(hint2)

        hint3 = BodyLabel("仅支持 ADB 通道安装（WiFi 模式不可用）")
        hint3.setAlignment(Qt.AlignCenter)
        drop_layout.addWidget(hint3)
        layout.addWidget(drop_frame)

        actions_frame = CardWidget()
        actions_layout = QHBoxLayout(actions_frame)

        self.select_btn = PrimaryPushButton("选择 APK 安装")
        actions_layout.addWidget(self.select_btn)

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
        self.select_btn.clicked.connect(self._select_apk)
        self.cancel_btn.clicked.connect(self._cancel)
        self.install_progress.connect(self._on_progress)
        self.install_done.connect(self._on_done)
        try:
            self.manager.connection_status_changed.connect(lambda c, ch: self._update_button_states())
            # WiFi 文件传输信号
            self.manager.file_transfer_progress.connect(self._on_wifi_transfer_progress)
            self.manager.file_transfer_complete.connect(self._on_wifi_transfer_complete)
        except Exception:
            pass

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
            # 仅 ADB 通道且未在安装中时启用选择按钮
            self.select_btn.setEnabled(ch == "adb" and not self._installing)
            self.cancel_btn.setEnabled(self._installing)
        except Exception:
            pass

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
        """安装 APK：仅支持 ADB 通道"""
        if self._installing:
            return
        ch = self.manager.current_channel
        if ch != "adb":
            self._show_message(QMessageBox.Warning, "通道不可用",
                               "APK 安装仅支持 ADB 通道。请通过 USB 连接手机并确保 ADB 可用。")
            return
        if not os.path.exists(apk_path):
            self._show_message(QMessageBox.Warning, "文件不存在", apk_path)
            return
        self._installing = True
        self.select_btn.setEnabled(False)
        self.cancel_btn.setEnabled(True)
        self.progress_bar.setValue(0)

        # ADB 模式：push 后 pm install
        threading.Thread(target=self._install_worker_adb, args=(apk_path,), daemon=True).start()

    def _install_worker_adb(self, apk_path):
        """ADB 模式安装线程：push 到手机后用 pm install 静默安装"""
        try:
            file_name = os.path.basename(apk_path)
            file_size = os.path.getsize(apk_path)
            # 第一步：ADB push
            self.install_progress.emit(5, f"正在推送 {file_name} ({file_size/1024/1024:.1f} MB)...")
            remote_path = APK_REMOTE_DIR + file_name
            # 确保远程目录存在
            self.manager.adb_command('shell', 'mkdir', '-p', APK_REMOTE_DIR)
            # 使用 subprocess 直接执行 push 以便实时进度
            cmd = ['adb']
            if self.manager.adb_device_id:
                cmd += ['-s', self.manager.adb_device_id]
            cmd += ['push', apk_path, remote_path]
            proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding='utf-8', errors='replace')
            # 等待推送完成
            while True:
                ret = proc.poll()
                if ret is not None:
                    break
                self.install_progress.emit(40, f"推送中... ({file_name})")
                time.sleep(0.5)
            if proc.returncode != 0:
                self.install_done.emit(False, f"推送失败: {proc.stderr.read()}")
                return
            self.install_progress.emit(60, "推送完成, 正在静默安装...")

            # 第二步：pm install（远程安装，adb install 仅接受本地路径故不能用）
            install_output = self.manager.adb_command('shell', 'pm', 'install', '-r', '-t', remote_path)
            self.install_progress.emit(90, "安装完成, 清理临时文件...")

            # 第三步：清理
            self.manager.adb_command('shell', 'rm', remote_path)

            success_str = "Success"
            if install_output and success_str in install_output:
                self.install_done.emit(True, f"安装成功: {file_name}")
            else:
                msg = (install_output or "").strip().splitlines()[-1] if install_output else "未知结果"
                self.install_done.emit(False, f"安装失败: {msg}")
        except Exception as e:
            self.install_done.emit(False, f"安装异常: {e}")

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
        """WiFi 文件传输完成回调"""
        self._close_wifi_dialog()
        file_name = os.path.basename(self._wifi_transfer_apk_path) if hasattr(self, '_wifi_transfer_apk_path') else "APK"
        try:
            self.manager.send_action("install_apk", {"path": "/sdcard/Download/PhoneHub/" + file_name})
        except Exception:
            pass
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
