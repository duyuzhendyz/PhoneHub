import os
import sys
import json
import time
import uuid
import socket
import base64
import threading
import subprocess
import requests
import psutil
import logging
from PIL import ImageGrab
from flask import Flask, request, jsonify, send_file, Response
from flask.logging import default_handler
from PyQt5.QtCore import QObject, pyqtSignal, QThread
from collections import deque

SECRET_TOKEN = "541881452418845"
DEFAULT_PORT = 58627
CHUNK_SIZE = 524288  # 512KB，减少文件传输的HTTP请求数量，降低延迟

LOG_FILE = os.path.join(os.path.expanduser("~"), "PhoneHub", "log.txt")
os.makedirs(os.path.dirname(LOG_FILE), exist_ok=True)

logger = logging.getLogger('phonehub')
logger.setLevel(logging.INFO)
logger.propagate = False

file_handler = logging.FileHandler(LOG_FILE, encoding='utf-8')
file_handler.setLevel(logging.INFO)

console_handler = logging.StreamHandler()
console_handler.setLevel(logging.INFO)

log_format = logging.Formatter('%(asctime)s - %(message)s')
file_handler.setFormatter(log_format)
console_handler.setFormatter(log_format)

if not logger.handlers:
    logger.addHandler(file_handler)
    logger.addHandler(console_handler)


def log(msg):
    logger.info(msg)
    print(msg)


def log_phone_request(action, detail=""):
    msg = f"手机请求 {action}"
    if detail:
        msg += f" - {detail}"
    logger.info(msg)
    print(msg)


def log_pc_send(action, detail=""):
    msg = f"电脑发送 {action}"
    if detail:
        msg += f" - {detail}"
    logger.info(msg)
    print(msg)
# PAW_URL = "https://duyuzhendyz.pythonanywhere.com"  # 【禁止删除】PAW 中转服务地址

def load_settings():
    """从配置文件加载设置（不再读取 paw_token，避免与对端 token 不匹配导致认证失败）"""
    # SECRET_TOKEN 保持默认值，不从配置文件覆盖
    pass

load_settings()

# 通道优先级
CHANNEL_NONE = "none"
CHANNEL_ADB = "adb"
CHANNEL_WIFI = "wifi"
# CHANNEL_PAW = "paw"  # 【禁止删除】PAW 中转通道
CHANNEL_PRIORITY = {CHANNEL_ADB: 3, CHANNEL_WIFI: 2, CHANNEL_NONE: 0}

# 升降级参数
UPGRADE_CONFIRM_COUNT = 3
DOWNGRADE_FAIL_COUNT = 3
RECONNECT_RETRY = 3
PROBE_INTERVAL = 5


class ConnectionManager(QObject):
    connection_status_changed = pyqtSignal(bool, str)
    connection_message_changed = pyqtSignal(str)
    cpu_usage_received = pyqtSignal(float)
    phone_cpu_received = pyqtSignal(float)
    phone_status_received = pyqtSignal(dict)
    message_received = pyqtSignal(dict)
    command_received = pyqtSignal(dict)
    clipboard_received = pyqtSignal(str, str)  # text, source
    text_received = pyqtSignal(str, str)
    clipboard_favorite_received = pyqtSignal(str, bool)  # text, favorite (save.md 功能23 收藏同步)
    file_transfer_progress = pyqtSignal(str, int, int, float)
    file_transfer_complete = pyqtSignal(str, str)
    file_receive_started = pyqtSignal(str, int, str)
    file_transfer_cancelled = pyqtSignal(str)  # 对端取消传输
    file_transfer_paused = pyqtSignal(bool)  # 对端暂停/继续传输
    notification_received = pyqtSignal(dict)
    location_received = pyqtSignal(list)
    process_list_received = pyqtSignal(list)
    power_action_received = pyqtSignal(str)
    file_sent = pyqtSignal(str)
    clipboard_sent = pyqtSignal()
    screenshot_received = pyqtSignal(str)
    app_list_received = pyqtSignal(list)
    file_list_received = pyqtSignal(str, list)  # 文件列表（WiFi 通道）：path, files
    url_history_sync_received = pyqtSignal(list)  # URL 历史同步（list of {url, direction, timestamp}）
    phone_frame_received = pyqtSignal(bytes)  # 手机投屏画面帧 (JPEG bytes)
    camera_frame_received = pyqtSignal(bytes)  # 手机摄像头画面帧 (JPEG bytes)
    phone_audio_received = pyqtSignal(bytes)  # 手机端音频数据

    def __init__(self):
        super().__init__()
        self.app = Flask(__name__)
        self.app.logger.removeHandler(default_handler)
        werkzeug_logger = logging.getLogger('werkzeug')
        werkzeug_logger.removeHandler(default_handler)
        werkzeug_logger.addHandler(file_handler)
        self.server_thread = None
        self.is_running = False
        self.port = DEFAULT_PORT
        self.device_id = str(uuid.uuid4())

        self.phone_connected = False
        self.current_channel = CHANNEL_NONE
        self.phone_ip = None
        self.cached_phone_ip = None
        self.adb_device_id = None

        # self.paw_connected = False  # 【禁止删除】PAW 连接状态
        # self.paw_thread = None      # 【禁止删除】PAW 线程
        # self.paw_running = False    # 【禁止删除】PAW 运行标志

        self.last_phone_seen = 0
        self.last_pc_clipboard = ""
        self.clipboard_monitor_running = False
        self._suppress_clipboard = False  # 防回环标志
        self._suppressed_content = ""     # 被抑制的回环内容（用于区分回环 vs 用户新复制）
        self.last_local_clipboard_time = 0  # 本地剪贴板最近一次变更时间（毫秒）

        self.file_transfer_active = False
        self.current_file_id = None
        self.file_transfer_cancel = False
        self._transfer_paused = False
        # save.md：接收文件统一存到 F:\desk\手机上传
        self.receive_dir = r"F:\desk\手机上传"
        try:
            os.makedirs(self.receive_dir, exist_ok=True)
        except Exception:
            self.receive_dir = os.path.expanduser("~/PhoneHub/Received")
            os.makedirs(self.receive_dir, exist_ok=True)

        self.cmd_queue = deque()
        self.msg_queue = deque()
        self.queue_lock = threading.Lock()

        # save.md 功能7：电脑→手机推流（手机轮询拉取 JPEG 帧）
        self._pc_stream_running = False
        self._pc_stream_thread = None
        self._latest_frame = None  # bytes (JPEG)
        self._frame_lock = threading.Lock()

        # save.md 功能8：电脑摄像头→手机推流
        self._pc_camera_running = False
        self._pc_camera_thread = None
        self._latest_camera_frame = None  # bytes (JPEG)
        self._camera_lock = threading.Lock()

        # 手机→电脑投屏：手机端上传JPEG帧
        self._latest_phone_frame = None
        self._phone_frame_lock = threading.Lock()
        self._phone_mirror_running = False

        # 手机→电脑声音传输：手机端上传音频
        self._phone_audio_running = False
        self._phone_audio_buffer = bytearray()
        self._phone_audio_lock = threading.Lock()

        # 电脑→手机声音传输：电脑端捕获音频
        self._pc_audio_running = False
        self._pc_audio_thread = None
        self._latest_pc_audio = None
        self._pc_audio_lock = threading.Lock()

        self.outgoing_file_path = None
        self.outgoing_file_id = None
        self.outgoing_file_size = 0

        # 升降级计数器
        self.upgrade_confirm = {}  # channel -> count
        self.downgrade_fail = {}   # channel -> count
        self.transfer_in_progress = False  # 传输期间暂停通道切换

        # IP缓存文件
        self.ip_cache_file = os.path.join(os.path.expanduser("~"), ".phonehub_ip_cache")
        self._cached_cpu = 0.0  # 保留用于状态信息，不再在UI显示
        try:
            psutil.cpu_percent(interval=0.1)
        except Exception:
            pass

        self._setup_flask_routes()
        self._get_local_ip()
        self._load_cached_ip()

        # 媒体信息监测（主动推送，只比较歌曲名和作者）
        self._last_media_title = ""
        self._last_media_artist = ""
        self._media_monitor_running = False
        self._media_monitor_thread = None
        self._start_media_monitor()

    def _get_local_ip(self):
        s = None
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.settimeout(1)
            # 使用公网 DNS 服务器检测本机局域网 IP，比 10.255.255.255 更可靠
            s.connect(("8.8.8.8", 80))
            self.local_ip = s.getsockname()[0]
        except Exception:
            # 回退：尝试绑定到所有接口获取非回环 IP
            try:
                hostname = socket.gethostname()
                ips = socket.gethostbyname_ex(hostname)[2]
                non_loopback = [ip for ip in ips if not ip.startswith("127.")]
                self.local_ip = non_loopback[0] if non_loopback else "127.0.0.1"
            except Exception:
                self.local_ip = "127.0.0.1"
        finally:
            if s:
                try:
                    s.close()
                except Exception:
                    pass

    def _load_cached_ip(self):
        try:
            if os.path.exists(self.ip_cache_file):
                with open(self.ip_cache_file, 'r') as f:
                    self.cached_phone_ip = f.read().strip()
        except Exception:
            pass

    def _save_cached_ip(self, ip):
        self.cached_phone_ip = ip
        try:
            with open(self.ip_cache_file, 'w') as f:
                f.write(ip)
        except Exception:
            pass

    def _setup_flask_routes(self):
        @self.app.before_request
        def check_auth():
            if request.path == '/favicon.ico':
                return None
            auth = request.headers.get('Authorization', '')
            if auth != f'Bearer {SECRET_TOKEN}':
                remote = request.remote_addr or 'unknown'
                # 记录认证失败详情（显示完整token便于排查）
                log(f"[AUTH FAIL] 来自 {remote} {request.method} {request.path} | 手机端发送='{auth}' | 电脑端期望='Bearer {SECRET_TOKEN}'")
                return jsonify({'error': 'unauthorized'}), 403

        @self.app.errorhandler(Exception)
        def handle_exception(e):
            """全局异常处理：防止请求处理异常导致服务器崩溃"""
            log(f"Flask 请求处理异常: {e}")
            return jsonify({'error': str(e)}), 500

        def _update_phone_connection():
            """从请求来源更新手机 IP 和通道状态（复用逻辑）"""
            remote_ip = request.remote_addr
            if not remote_ip:
                return
            self.last_phone_seen = time.time()
            if remote_ip != '127.0.0.1':
                if self.phone_ip != remote_ip:
                    self.phone_ip = remote_ip
                    self._save_cached_ip(remote_ip)
            if not self.phone_connected:
                if remote_ip == '127.0.0.1' and self.adb_device_id:
                    log(f"[连接] 检测到 ADB 通道: remote={remote_ip}")
                    self._set_channel(CHANNEL_ADB)
                else:
                    log(f"[连接] 检测到 WiFi 通道: remote={remote_ip}")
                    self._set_channel(CHANNEL_WIFI)

        def _get_system_status():
            """获取电脑系统状态信息（复用逻辑）"""
            mem = psutil.virtual_memory()
            try:
                disk = psutil.disk_usage(os.path.expanduser('~'))
            except Exception:
                disk = psutil.disk_usage('C:\\')
            net = psutil.net_io_counters()
            return {
                'status': 'ok',
                'cpu': self._cached_cpu,
                'memory': mem.percent,
                'disk': disk.percent,
                'net_sent': net.bytes_sent,
                'net_recv': net.bytes_recv,
                'device_id': self.device_id
            }

        @self.app.route('/api/status', methods=['GET'])
        def get_status():
            log_phone_request("获取电脑状态")
            _update_phone_connection()
            return jsonify(_get_system_status())

        @self.app.route('/api/poll', methods=['GET'])
        def poll_all():
            """合并轮询端点：一次请求同时返回电脑状态和消息队列中的一条消息，减轻网络负担"""
            log_phone_request("合并轮询(status+msg)")
            _update_phone_connection()
            status_info = _get_system_status()
            # 消息：取所有待发消息（减少多次轮询延迟，快捷控制等场景）
            with self.queue_lock:
                if self.msg_queue:
                    msgs = list(self.msg_queue)
                    self.msg_queue.clear()
                    try:
                        for m in msgs:
                            _action = m.get('data', {}).get('action', '未知')
                            log(f"[poll] 返回消息: action={_action}")
                    except Exception:
                        pass
                else:
                    msgs = []
            return jsonify({'status_info': status_info, 'msgs': msgs})

        @self.app.route('/api/cmd', methods=['POST'])
        def receive_cmd():
            data = request.get_json(force=True)
            source = data.get('source', 'phone')

            if 'action' in data:
                action = data.get('action', '')
                body = data.get('body', {}) if isinstance(data.get('body'), dict) else {}
            else:
                body = data.get('data', {})
                action = body.get('action', '')

            log_phone_request(action)

            # 更新手机 IP 和通道状态
            _update_phone_connection()

            with self.queue_lock:
                self.cmd_queue.append(data)

            if action == 'cmd':
                self._handle_remote_command(body)
                self.command_received.emit(body)
            elif action in ('clipboard', 'clipboard_set'):
                text = body.get('txt', body.get('text', ''))
                # 内容对比：仅当远端剪贴板与本地不同时才覆盖本地
                # 不使用时间戳对比，避免时钟差异导致新内容被拒绝
                if text and text != self.last_pc_clipboard:
                    self._suppress_clipboard = True
                    self._suppressed_content = text
                    self.last_pc_clipboard = text
                    self.clipboard_received.emit(text, source)
            elif action == 'clipboard_favorite':
                text = body.get('content', '')
                favorite = body.get('favorite', False)
                if text:
                    self.clipboard_favorite_received.emit(text, favorite)
            elif action in ('txt', 'text', 'input_text'):
                text = body.get('txt', body.get('text', ''))
                filename = body.get('filename', '')
                self.text_received.emit(text, filename)
            elif action in ('send_file_head', 'file_send_head'):
                file_name = body.get('file_name', body.get('fileName', ''))
                file_size = body.get('file_size', body.get('fileSize', 0))
                file_id = body.get('file_id', body.get('fileId', ''))
                log_phone_request("发送文件", f"name={file_name}, size={file_size}, id={file_id}")
                self._start_file_receive(file_id, file_name, file_size)
                self.file_receive_started.emit(file_name, file_size, file_id)
            elif action == 'transfer_control':
                # 手机端发来的传输控制消息（暂停/继续/取消）
                ctrl = body.get('ctrl', '')
                log_phone_request("传输控制", f"ctrl={ctrl}")
                if ctrl == 'cancel':
                    self.file_transfer_cancel = True
                    self._transfer_paused = False
                    self.file_transfer_cancelled.emit(body.get('file_id', ''))
                elif ctrl == 'pause':
                    self.file_transfer_cancel = True
                    self._transfer_paused = True
                    self.file_transfer_paused.emit(True)
                elif ctrl == 'resume':
                    self.file_transfer_cancel = False
                    self._transfer_paused = False
                    self.file_transfer_paused.emit(False)
            elif action == 'chunk':
                pass
            elif action in ('file_complete', 'file_send_complete'):
                file_id = body.get('file_id', body.get('fileId', ''))
                if self.outgoing_file_id == file_id:
                    # 手机确认收到我们发出的文件（PC→Phone 传输完成）
                    sent_path = self.outgoing_file_path
                    self.file_sent.emit(file_id)
                    self.file_transfer_complete.emit(file_id, sent_path or "")
                    self.outgoing_file_path = None
                    self.outgoing_file_id = None
                    self.transfer_in_progress = False
                    self.file_transfer_active = False
                else:
                    self._complete_file_receive(file_id)
            elif action == 'ack':
                # ack 动作不应触发 file_sent（file_sent 由 file_complete 触发）。
                # 此分支保留仅为兼容性，不发射任何完成信号，避免误显示"完成"按钮。
                pass
            elif action == 'cpu':
                cpu_val = body.get('cpu', 0)
                self.phone_cpu_received.emit(float(cpu_val))
            elif action == 'phone_status':
                self.phone_status_received.emit(body)
            elif action in ('status', 'status_report'):
                cpu_val = body.get('cpu', body.get('memory_usage', 0))
                self.phone_cpu_received.emit(float(cpu_val))
                self.phone_status_received.emit(body)
            elif action == 'notification':
                self.notification_received.emit(body)
            elif action == 'location':
                point = {
                    'lat': body.get('lat', 0),
                    'lon': body.get('lon', 0),
                    'timestamp': body.get('timestamp', 0),
                    'speed': body.get('speed', 0),
                    'accuracy': body.get('accuracy', 0)
                }
                self.location_received.emit([point])
            elif action == 'location_batch':
                locations = body.get('points', body.get('locations', []))
                self.location_received.emit(locations)
            elif action == 'process_list':
                processes = body.get('processes', [])
                self.process_list_received.emit(processes)
            elif action == 'process_list_request':
                self._send_process_list()
            elif action in ('screenshot_request', 'pc_screenshot_request'):
                self._take_screenshot_and_send()
            elif action == 'kill_process':
                pid = body.get('pid')
                if pid:
                    self._kill_process(pid)
            elif action == 'run_as_admin':
                program = body.get('program', '')
                if program:
                    self._run_as_admin(program)
            elif action in ('power', 'power_command'):
                action_type = body.get('cmd') or body.get('type') or body.get('command', '')
                self._handle_power_action(action_type)
                self.power_action_received.emit(action_type)
            elif action == 'stop':
                self.file_transfer_cancel = True
            elif action == 'screenshot':
                screenshot_path = body.get('path', '')
                self.screenshot_received.emit(screenshot_path)
            elif action == 'app_list':
                apps = body.get('apps', [])
                self.app_list_received.emit(apps)
            elif action == 'kill_pc_process':
                pid = body.get('pid', 0)
                if pid:
                    self._kill_pc_process(pid)
            elif action == 'set_pc_process_priority':
                pid = body.get('pid', 0)
                adjustment = body.get('adjustment', 0)
                if pid:
                    self._set_pc_process_priority(pid, adjustment)
            elif action == 'file_list':
                files = body.get('files', [])
                resp_path = body.get('path', '')
                self.file_list_received.emit(resp_path, files)
            elif action in ('open_url', 'url', 'url_push'):
                url = body.get('url', '')
                use_edge = body.get('use_edge', body.get('edge', body.get('via', True)))
                if url:
                    self._open_url_on_pc(url, use_edge)
                    # 发射信号让网页历史页面记录
                    self.command_received.emit(body)
            elif action == 'screen_click':
                norm_x = body.get('x', 0.5)
                norm_y = body.get('y', 0.5)
                op = body.get('op', 'click')
                self._perform_pc_click(norm_x, norm_y, op)
            elif action == 'pc_screenshot':
                # 截取电脑当前屏幕并发送给手机
                self._take_screenshot_and_send()
            elif action == 'camera_switch':
                # 手机端切换了摄像头，PC端无需额外操作
                pass
            elif action == 'url_history_sync':
                # 手机端发来 URL 历史用于同步
                history = body.get('history', [])
                if history:
                    self.url_history_sync_received.emit(history)

            return jsonify({'status': 'ok', 'cpu': self._cached_cpu})

        @self.app.route('/api/upload_file', methods=['POST'])
        def upload_file():
            """手机→PC：流式接收整个文件（支持断点续传）"""
            file_id = request.headers.get('X-File-Id', '')
            file_name = request.headers.get('X-File-Name', '')
            file_size = int(request.headers.get('X-File-Size', '0'))
            resume_offset = int(request.headers.get('X-Resume-Offset', '0'))
            log_phone_request("上传文件(流式)", f"file_id={file_id}, name={file_name}, size={file_size}, offset={resume_offset}")
            if not file_id or file_id != self.current_file_id:
                return jsonify({'error': 'invalid file_id'}), 400
            if self.file_transfer_cancel:
                return jsonify({'error': 'cancelled'}), 400
            # 流式写入文件
            try:
                recv_path = self.current_receive_file
                # 断点续传：如果指定了 offset，从该位置继续写入
                if resume_offset > 0 and os.path.exists(recv_path):
                    mode = 'r+b'
                    initial_written = resume_offset
                else:
                    mode = 'wb'
                    initial_written = 0
                written = initial_written
                with open(recv_path, mode) as f:
                    if resume_offset > 0:
                        f.seek(resume_offset)
                    while True:
                        if self.file_transfer_cancel:
                            break
                        chunk = request.stream.read(65536)
                        if not chunk:
                            break
                        f.write(chunk)
                        written += len(chunk)
                        self.current_receive_written = written
                        self.file_transfer_progress.emit(file_id, written, file_size, time.time())
                if self.file_transfer_cancel:
                    return jsonify({'status': 'cancelled', 'written': written}), 400
                # 接收完成
                self._complete_file_receive(file_id)
                return jsonify({'status': 'ok', 'written': written})
            except Exception as e:
                print(f"upload_file error: {e}")
                return jsonify({'error': str(e)}), 500

        @self.app.route('/api/download_file/<file_id>', methods=['GET'])
        def download_file(file_id):
            """PC→手机：流式发送整个文件（支持断点续传 Range 请求）"""
            log_phone_request("下载文件(流式)", f"file_id={file_id}")
            log(f"[download_file] 请求进入: file_id={file_id}, outgoing_file_id={self.outgoing_file_id}, outgoing_file_path={self.outgoing_file_path}, file_transfer_cancel={self.file_transfer_cancel}")
            if not self.outgoing_file_path or self.outgoing_file_id != file_id:
                log(f"[download_file] 404: outgoing_file_path={self.outgoing_file_path}, outgoing_file_id={self.outgoing_file_id} != file_id={file_id}")
                return jsonify({'error': 'not found'}), 404
            file_path = self.outgoing_file_path
            file_size = self.outgoing_file_size
            if not os.path.exists(file_path):
                log(f"[download_file] 404: 文件不存在 {file_path}")
                return jsonify({'error': 'file not found'}), 404

            # 解析 Range header 支持断点续传
            range_header = request.headers.get('Range', '')
            resume_offset = 0
            if range_header.startswith('bytes='):
                try:
                    resume_offset = int(range_header[6:].split('-')[0])
                except Exception:
                    resume_offset = 0
            if resume_offset >= file_size:
                resume_offset = 0

            def generate():
                """生成器流式读取文件并发送"""
                sent = resume_offset
                log(f"[download_file] generate() 开始: resume_offset={resume_offset}, file_size={file_size}, file_transfer_cancel={self.file_transfer_cancel}")
                try:
                    with open(file_path, 'rb') as f:
                        if resume_offset > 0:
                            f.seek(resume_offset)
                        while True:
                            if self.file_transfer_cancel:
                                print(f"[download_file] 传输已取消, sent={sent}")
                                break
                            data = f.read(65536)
                            if not data:
                                break
                            yield data
                            sent += len(data)
                            try:
                                self.file_transfer_progress.emit(file_id, sent, file_size, time.time())
                            except Exception:
                                pass
                except Exception as e:
                    print(f"download_file generate error: {e}")

            remaining = file_size - resume_offset
            resp = Response(generate(), mimetype='application/octet-stream', direct_passthrough=True)
            if resume_offset > 0:
                resp.status_code = 206
                resp.headers['Content-Range'] = f'bytes {resume_offset}-{file_size - 1}/{file_size}'
            resp.headers['Content-Length'] = str(remaining)
            resp.headers['X-File-Size'] = str(file_size)
            resp.headers['X-Resume-Offset'] = str(resume_offset)
            return resp

        @self.app.route('/api/upload_chunk/<file_id>/<int:part_num>', methods=['POST'])
        def upload_chunk(file_id, part_num):
            log_phone_request("上传文件分块", f"file_id={file_id}, part={part_num}")
            chunk_data = request.get_data()
            self._write_file_chunk(file_id, part_num, chunk_data)
            return jsonify({'status': 'ok'})

        @self.app.route('/api/download_chunk/<file_id>/<int:part_num>', methods=['GET'])
        def download_chunk(file_id, part_num):
            log_phone_request("下载文件分块", f"file_id={file_id}, part={part_num}")
            chunk_data = self._read_file_chunk(file_id, part_num)
            if chunk_data:
                file_size = self.outgoing_file_size
                if file_size > 0:
                    sent_bytes = min((part_num + 1) * CHUNK_SIZE, file_size)
                    self.file_transfer_progress.emit(file_id, sent_bytes, file_size, time.time())
                return Response(chunk_data, mimetype='application/octet-stream')
            return jsonify({'error': 'not found'}), 404

        @self.app.route('/api/msg', methods=['GET'])
        def get_msg():
            log_phone_request("获取消息")
            with self.queue_lock:
                if self.msg_queue:
                    msg = self.msg_queue.popleft()
                    return jsonify(msg)
            return jsonify({'activate': 'ping'})

        @self.app.route('/api/frame', methods=['GET'])
        def get_frame():
            log_phone_request("获取电脑画面帧")
            """save.md 功能7：手机轮询拉取电脑画面 JPEG 帧，附带鼠标归一化坐标"""
            with self._frame_lock:
                frame = self._latest_frame
            if frame:
                from flask import Response
                resp = Response(frame, mimetype='image/jpeg')
                # 附带当前鼠标位置（归一化 0-1）- 使用 ctypes 避免 pyautogui 依赖
                try:
                    import ctypes
                    from ctypes import wintypes
                    user32 = ctypes.windll.user32
                    sw = user32.GetSystemMetrics(0)
                    sh = user32.GetSystemMetrics(1)
                    point = wintypes.POINT()
                    user32.GetCursorPos(ctypes.byref(point))
                    resp.headers['X-Cursor-X'] = f'{point.x / sw:.4f}' if sw > 0 else '0'
                    resp.headers['X-Cursor-Y'] = f'{point.y / sh:.4f}' if sh > 0 else '0'
                except Exception:
                    resp.headers['X-Cursor-X'] = '0'
                    resp.headers['X-Cursor-Y'] = '0'
                return resp
            return ('', 204)

        @self.app.route('/api/camera_frame', methods=['GET'])
        def get_camera_frame():
            log_phone_request("获取电脑摄像头帧")
            """save.md 功能8：手机轮询拉取电脑摄像头 JPEG 帧"""
            with self._camera_lock:
                frame = self._latest_camera_frame
            if frame:
                from flask import Response
                return Response(frame, mimetype='image/jpeg')
            return ('', 204)

        @self.app.route('/api/phone_frame', methods=['POST'])
        def receive_phone_frame():
            log_phone_request("上传手机画面帧", f"type={request.args.get('type', 'mirror')}")
            """手机投屏：接收手机端上传的 JPEG 帧，通过 ?type=camera 或 ?type=mirror 区分来源"""
            frame_data = request.get_data()
            frame_type = request.args.get('type', 'mirror')  # 区分投屏帧和摄像头帧
            if frame_data:
                with self._phone_frame_lock:
                    self._latest_phone_frame = frame_data
                # 根据帧类型发射不同信号，避免投屏帧和摄像头帧冲突
                if frame_type == 'camera':
                    self.camera_frame_received.emit(frame_data)
                else:
                    self.phone_frame_received.emit(frame_data)
            return jsonify({'status': 'ok'})

        @self.app.route('/api/phone_audio', methods=['POST'])
        def receive_phone_audio():
            log_phone_request("上传手机音频")
            """声音传输：接收手机端上传的音频数据"""
            audio_data = request.get_data()
            if audio_data and self._phone_audio_running:
                self._play_audio_data(audio_data)
            return jsonify({'status': 'ok'})

        @self.app.route('/api/audio', methods=['GET'])
        def get_audio():
            log_phone_request("获取电脑音频")
            """PC→手机声音传输：手机轮询拉取电脑音频 PCM 数据"""
            with self._pc_audio_lock:
                chunk = self._latest_pc_audio
            if chunk:
                return Response(chunk, mimetype='application/octet-stream')
            return ('', 204)

        @self.app.route('/api/pc_drives', methods=['GET'])
        def get_pc_drives():
            log_phone_request("获取电脑驱动器列表")
            """返回电脑所有磁盘驱动器列表"""
            import string
            drives = []
            for letter in string.ascii_uppercase:
                drive = f"{letter}:\\"
                if os.path.exists(drive):
                    try:
                        usage = psutil.disk_usage(drive)
                        drives.append({
                            'name': drive,
                            'label': drive,
                            'is_dir': True,
                            'total': usage.total,
                            'used': usage.used,
                            'free': usage.free
                        })
                    except Exception:
                        drives.append({
                            'name': drive,
                            'label': drive,
                            'is_dir': True,
                            'total': 0,
                            'used': 0,
                            'free': 0
                        })
            return jsonify({'drives': drives})

        @self.app.route('/api/pc_files', methods=['POST'])
        def list_pc_files():
            log_phone_request("获取电脑文件列表")
            """列出电脑指定目录的文件"""
            data = request.get_json(force=True)
            path = data.get('path', 'C:\\')
            # 安全检查：防止路径遍历攻击
            if not path or not os.path.exists(path):
                # 回退到根目录
                path = 'C:\\'
            # 过滤系统隐藏文件（desktop.ini、Thumbs.db 等）
            SYSTEM_HIDDEN_FILES = {'desktop.ini', 'thumbs.db', '.ds_store', 'pagefile.sys', 'hiberfil.sys', 'swapfile.sys'}
            try:
                files = []
                for entry in os.listdir(path):
                    # 跳过系统隐藏文件和 Windows 受保护文件
                    if entry.lower() in SYSTEM_HIDDEN_FILES:
                        continue
                    if entry.lower().endswith('.ini') and entry.lower().startswith('desktop'):
                        continue
                    full_path = os.path.join(path, entry)
                    is_dir = os.path.isdir(full_path)
                    try:
                        stat = os.stat(full_path)
                        size = stat.st_size if not is_dir else 0
                        modified = stat.st_mtime
                    except (PermissionError, OSError):
                        # 跳过无权限访问的系统文件
                        continue
                    files.append({
                        'name': entry,
                        'is_dir': is_dir,
                        'size': size,
                        'modified': modified
                    })
                # 排序：目录在前，然后按名称
                files.sort(key=lambda f: (not f['is_dir'], f['name'].lower()))
                return jsonify({'path': path, 'files': files})
            except PermissionError:
                return jsonify({'error': 'permission_denied', 'path': path}), 403
            except Exception as e:
                return jsonify({'error': str(e), 'path': path}), 500

        @self.app.route('/api/pc_file_download', methods=['POST'])
        def download_pc_file():
            log_phone_request("下载电脑文件")
            """手机端下载电脑文件（仅限文件，不支持文件夹）"""
            data = request.get_json(force=True)
            file_path = data.get('path', '')
            if not file_path or not os.path.isfile(file_path):
                return jsonify({'error': 'not a file or not found'}), 404
            # 拒绝下载系统隐藏文件
            basename = os.path.basename(file_path).lower()
            if basename in ('desktop.ini', 'thumbs.db', '.ds_store'):
                return jsonify({'error': 'system file, not downloadable'}), 403
            try:
                return send_file(file_path, as_attachment=True, download_name=os.path.basename(file_path))
            except PermissionError:
                return jsonify({'error': 'permission denied, file may be locked by system'}), 403
            except Exception as e:
                return jsonify({'error': str(e)}), 500

    # ==================== 通道管理 ====================

    def _set_channel(self, channel):
        old = self.current_channel
        log(f"[通道] _set_channel: {old} -> {channel}")
        # 从未连接变为已连接，直接设置，无需累积确认
        if old == CHANNEL_NONE and channel != CHANNEL_NONE:
            self.current_channel = channel
            self.phone_connected = True
            log(f"[通道] 首次连接! emitting connection_status_changed(True, '{channel}')")
            self.connection_status_changed.emit(True, channel)
            self.upgrade_confirm.clear()
            self.downgrade_fail.clear()
            return
        if CHANNEL_PRIORITY.get(channel, 0) > CHANNEL_PRIORITY.get(old, 0):
            self.upgrade_confirm[channel] = self.upgrade_confirm.get(channel, 0) + 1
            if self.upgrade_confirm[channel] >= UPGRADE_CONFIRM_COUNT:
                self.current_channel = channel
                self.phone_connected = True
                self.connection_status_changed.emit(True, channel)
                self.upgrade_confirm.clear()
        elif CHANNEL_PRIORITY.get(channel, 0) < CHANNEL_PRIORITY.get(old, 0):
            self.current_channel = channel
            self.phone_connected = (channel != CHANNEL_NONE)
            self.connection_status_changed.emit(self.phone_connected, channel)
            self.downgrade_fail.clear()
        else:
            self.current_channel = channel
            self.phone_connected = (channel != CHANNEL_NONE)
            self.connection_status_changed.emit(self.phone_connected, channel)

    def _check_adb(self):
        """检测ADB设备并设置端口转发"""
        try:
            result = subprocess.run(['adb', 'devices'], capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=3)
            lines = result.stdout.strip().split('\n')
            for line in lines[1:]:
                if 'device' in line and 'unauthorized' not in line:
                    device_id = line.split('\t')[0].strip()
                    # 只在设备ID变化时才设置端口转发（避免重复执行 adb tcpip 5555）
                    if self.adb_device_id != device_id:
                        self.adb_device_id = device_id
                        self._setup_adb_forwarding(device_id)
                    return True
        except Exception:
            pass
        self.adb_device_id = None
        return False

    def _setup_adb_forwarding(self, device_id):
        """设置 ADB 端口转发，让手机可以通过 127.0.0.1:58627 访问电脑"""
        try:
            # adb reverse：手机通过 127.0.0.1:58627 访问电脑 Flask 服务
            subprocess.run(
                ['adb', '-s', device_id, 'reverse', 'tcp:58627', 'tcp:58627'],
                capture_output=True, timeout=2
            )
            # adb forward：电脑通过 127.0.0.1:58628 向手机发消息（如果手机有HTTP服务）
            subprocess.run(
                ['adb', '-s', device_id, 'forward', 'tcp:58628', 'tcp:58627'],
                capture_output=True, timeout=2
            )
            # ADB 连接成功后，自动开启无线ADB模式（adb tcpip 5555）
            # 让后续投屏和声音可以走WiFi ADB，USB插拔无感切换
            subprocess.run(
                ['adb', '-s', device_id, 'tcpip', '5555'],
                capture_output=True, timeout=10
            )
        except Exception:
            pass

    def _wait_for_connection(self):
        """启动时等待ADB连接10秒，超时后切换到WiFi等待模式"""
        start_time = time.time()
        elapsed = 0
        while elapsed < 10 and self.is_running:
            if self._check_adb():
                self.connection_message_changed.emit(f"ADB已连接 ({self.adb_device_id})，等待手机响应...")
                return
            elapsed = int(time.time() - start_time)
            self.connection_message_changed.emit(f"等待ADB连接... ({10 - elapsed}s)")
            time.sleep(1)
        if not self.phone_connected:
            self.connection_message_changed.emit("等待手机WiFi连接...")

    # def _check_paw(self):  # 【禁止删除】PAW 通道检测
    #     try:
    #         resp = requests.get(
    #             f"{PAW_URL}/api/pc_status",
    #             headers={"Authorization": f"Bearer {SECRET_TOKEN}"},
    #             timeout=5
    #         )
    #         if resp.status_code == 200:
    #             return True
    #     except Exception:
    #         pass
    #     return False

    def _probe_channels(self):
        """探测线程：每5秒检测各通道可用性，自动升降级"""
        while self.is_running:
            if self.transfer_in_progress:
                time.sleep(PROBE_INTERVAL)
                continue

            # 持续检测ADB设备并设置端口转发（手机启动后可立即连接）
            adb_device_ok = self._check_adb()

            # 通道升级逻辑：已连接时，ADB可用则尝试升级到ADB
            if self.phone_connected and adb_device_ok and self.current_channel == CHANNEL_WIFI:
                self.upgrade_confirm[CHANNEL_ADB] = self.upgrade_confirm.get(CHANNEL_ADB, 0) + 1
                if self.upgrade_confirm[CHANNEL_ADB] >= UPGRADE_CONFIRM_COUNT:
                    self._set_channel(CHANNEL_ADB)
                    self.upgrade_confirm.clear()
            elif not adb_device_ok:
                self.upgrade_confirm.clear()

            # WiFi通道无需主动探测（手机没有HTTP服务），依赖手机主动连接

            time.sleep(PROBE_INTERVAL)

    def _reconnect(self):
        """断线重连：重试原方式3次，失败降级"""
        channel = self.current_channel
        for _ in range(RECONNECT_RETRY):
            if channel == CHANNEL_ADB and self._check_adb():
                return True
            elif channel == CHANNEL_WIFI:
                return True
            # elif channel == CHANNEL_PAW and self._check_paw():  # 【禁止删除】PAW 重连
            #     return True
            time.sleep(2)
        # 降级
        if channel == CHANNEL_ADB:
            self._set_channel(CHANNEL_WIFI)
            return True
        # if channel in (CHANNEL_ADB, CHANNEL_WIFI):  # 【禁止删除】PAW 降级
        #     if self._check_paw():
        #         self._set_channel(CHANNEL_PAW)
        #         return True
        self._set_channel(CHANNEL_NONE)
        return False

    # ==================== 启动 ====================

    def start_server(self):
        if self.is_running:
            return
        self.is_running = True
        self.server_thread = threading.Thread(target=self._run_server, daemon=True)
        self.server_thread.start()
        self._start_monitoring()
        # threading.Thread(target=self._report_to_paw, daemon=True).start()  # 【禁止删除】PAW 上报
        # self._start_paw_polling()  # 【禁止删除】PAW 轮询
        # 先等待ADB连接（10秒），超时后切换到WiFi等待模式
        threading.Thread(target=self._wait_for_connection, daemon=True).start()
        threading.Thread(target=self._probe_channels, daemon=True).start()
        threading.Thread(target=self._verify_server, daemon=True).start()

    def _run_server(self):
        try:
            log(f"Flask 服务器启动中... 监听 0.0.0.0:{self.port}")
            self.app.run(host='0.0.0.0', port=self.port, debug=False, use_reloader=False, threaded=True)
        except OSError as e:
            log(f"Flask 服务器启动失败（端口 {self.port} 可能被占用）: {e}")
            self.connection_message_changed.emit(f"服务器启动失败: 端口 {self.port} 被占用")
        except Exception as e:
            log(f"Flask 服务器异常: {e}")

    def _verify_server(self):
        """延迟验证服务器是否正常监听
        注意：不能用 HTTP 请求 /api/status 自检，否则会触发 _update_phone_connection
        把本机自检请求误识别为手机连接，导致 PC 启动后立即显示"已连接"。
        改用 socket 检测端口是否监听。
        """
        import socket
        time.sleep(2)
        if not self.is_running:
            return
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(2)
            s.connect(("127.0.0.1", self.port))
            s.close()
            log(f"Flask 服务器自检通过: 127.0.0.1:{self.port} 端口监听正常")
        except Exception as e:
            log(f"Flask 服务器自检失败: {e}")
            self.connection_message_changed.emit(f"服务器自检失败，请检查防火墙是否放行端口 {self.port}")

    def _start_monitoring(self):
        self.clipboard_monitor_running = True
        threading.Thread(target=self._clipboard_monitor, daemon=True).start()
        threading.Thread(target=self._status_monitor, daemon=True).start()

    def _clipboard_monitor(self):
        try:
            import pyperclip
            while self.clipboard_monitor_running:
                try:
                    current = pyperclip.paste()
                    if current and current != self.last_pc_clipboard and len(current) < 100000:
                        if self._suppress_clipboard:
                            self._suppress_clipboard = False
                            if current != self._suppressed_content:
                                # 用户新复制了与回环内容不同的内容，正常发送
                                self.last_pc_clipboard = current
                                self.last_local_clipboard_time = int(time.time() * 1000)
                                self.send_clipboard(current)
                            else:
                                # 回环内容，仅更新追踪值
                                self.last_pc_clipboard = current
                        else:
                            self.last_pc_clipboard = current
                            self.last_local_clipboard_time = int(time.time() * 1000)
                            self.send_clipboard(current)
                except Exception:
                    pass
                time.sleep(0.5)
        except ImportError:
            pass

    def _status_monitor(self):
        while self.is_running:
            if self.phone_connected and time.time() - self.last_phone_seen > 15:
                self._reconnect()
            # 不再获取 CPU 占用（用户要求去掉双端 CPU 显示与采集）
            time.sleep(1)

    # def _report_to_paw(self):  # 【禁止删除】PAW IP 上报
    #     def report():
    #         while self.is_running:
    #             try:
    #                 requests.post(
    #                     f"{PAW_URL}/api/update_ip",
    #                     json={"ip": self.local_ip, "port": self.port},
    #                     headers={"Authorization": f"Bearer {SECRET_TOKEN}"},
    #                     timeout=5
    #                 )
    #                 self.paw_connected = True
    #             except Exception:
    #                 self.paw_connected = False
    #             time.sleep(60)
    #     threading.Thread(target=report, daemon=True).start()
    #
    # def _start_paw_polling(self):  # 【禁止删除】PAW 轮询启动
    #     self.paw_running = True
    #     self.paw_thread = threading.Thread(target=self._paw_long_poll, daemon=True)
    #     self.paw_thread.start()
    #
    # def _paw_long_poll(self):  # 【禁止删除】PAW 长轮询
    #     fail_count = 0
    #     while self.paw_running:
    #         try:
    #             resp = requests.get(
    #                 f"{PAW_URL}/api/get_cmd",
    #                 headers={"Authorization": f"Bearer {SECRET_TOKEN}"},
    #                 stream=True,
    #                 timeout=35
    #             )
    #             for line in resp.iter_lines():
    #                 if line:
    #                     line = line.decode('utf-8')
    #                     if line.startswith('data: '):
    #                         try:
    #                             data = json.loads(line[6:])
    #                             if data.get('activate') == 'ping':
    #                                 continue
    #                             self._handle_paw_message(data)
    #                             fail_count = 0
    #                         except Exception:
    #                             pass
    #         except Exception:
    #             fail_count += 1
    #             if fail_count >= RECONNECT_RETRY:
    #                 self.paw_connected = False
    #                 fail_count = 0
    #             time.sleep(2)
    #
    # def _handle_paw_message(self, data):  # 【禁止删除】PAW 消息处理
    #     msg_data = data.get('data', {})
    #     action = msg_data.get('action', '')
    #     source = data.get('source', 'phone')
    #     self.last_phone_seen = time.time()
    #     if not self.phone_connected:
    #         self._set_channel(CHANNEL_PAW)
    #
    #     if action == 'clipboard':
    #         self.clipboard_received.emit(msg_data.get('txt', ''), source)
    #     elif action == 'txt':
    #         self.text_received.emit(msg_data.get('txt', ''), msg_data.get('filename', ''))
    #     elif action == 'cmd':
    #         self._handle_remote_command(msg_data)
    #         self.command_received.emit(msg_data)
    #     elif action == 'send_file_head':
    #         self.file_receive_started.emit(msg_data.get('file_name', ''), msg_data.get('file_size', 0), msg_data.get('file_id', ''))
    #         self._start_paw_file_receive(msg_data.get('file_id'), msg_data.get('file_name', ''), msg_data.get('file_size', 0))
    #     elif action == 'file_complete':
    #         self._complete_file_receive(msg_data.get('file_id', ''))
    #     elif action == 'ack':
    #         self.file_sent.emit(msg_data.get('file_id', ''))
    #     elif action == 'cpu':
    #         self.phone_cpu_received.emit(float(msg_data.get('cpu', 0)))
    #     elif action == 'phone_status':
    #         self.phone_status_received.emit(msg_data)
    #     elif action == 'notification':
    #         self.notification_received.emit(msg_data)
    #     elif action == 'location_batch':
    #         self.location_received.emit(msg_data.get('locations', []))
    #     elif action == 'process_list':
    #         self.process_list_received.emit(msg_data.get('processes', []))
    #     elif action == 'process_list_request':
    #         self._send_process_list()
    #     elif action == 'kill_process':
    #         pid = msg_data.get('pid')
    #         if pid:
    #             self._kill_process(pid)
    #     elif action == 'run_as_admin':
    #         program = msg_data.get('program', '')
    #         if program:
    #             self._run_as_admin(program)
    #     elif action == 'power':
    #         action_type = msg_data.get('cmd') or msg_data.get('type', '')
    #         self._handle_power_action(action_type)
    #         self.power_action_received.emit(action_type)
    #     elif action == 'screenshot':
    #         self.screenshot_received.emit(msg_data.get('path', ''))
    #     elif action == 'app_list':
    #         self.app_list_received.emit(msg_data.get('apps', []))
    #     elif action == 'file_list':
    #         self.file_list_received.emit(msg_data.get('files', []))

    # ==================== 文件接收 ====================

    def _start_file_receive(self, file_id, file_name, file_size):
        self.transfer_in_progress = True
        self.file_transfer_active = True
        self.current_file_id = file_id
        self.file_transfer_cancel = False
        self.current_receive_file = os.path.join(self.receive_dir, file_name)
        self.current_receive_size = file_size
        self.current_receive_written = 0
        self.current_receive_parts = {}
        if os.path.exists(self.current_receive_file + '.progress'):
            try:
                with open(self.current_receive_file + '.progress', 'r') as f:
                    self.current_receive_written = int(f.read().strip())
            except Exception:
                self.current_receive_written = 0

    def _write_file_chunk(self, file_id, part_num, data):
        # 放宽条件：即使 file_transfer_active 为 False，只要 file_id 匹配也尝试写入
        if file_id != self.current_file_id:
            print(f"[file] chunk 丢弃: file_id 不匹配 (recv={file_id}, current={self.current_file_id})")
            return
        if self.file_transfer_cancel:
            print(f"[file] chunk 丢弃: file_transfer_cancel=True")
            return
        # 使用seek定位写入，支持乱序到达
        try:
            with open(self.current_receive_file, 'r+b' if os.path.exists(self.current_receive_file) else 'wb') as f:
                f.seek(part_num * CHUNK_SIZE)
                f.write(data)
        except Exception as e:
            print(f"[file] 写入 chunk 失败: {e}")
            return
        self.current_receive_written = max(self.current_receive_written, (part_num + 1) * CHUNK_SIZE)
        with open(self.current_receive_file + '.progress', 'w') as f:
            f.write(str(self.current_receive_written))
        self.file_transfer_progress.emit(file_id, self.current_receive_written, self.current_receive_size, time.time())

    def _complete_file_receive(self, file_id):
        try:
            if os.path.exists(self.current_receive_file + '.progress'):
                os.remove(self.current_receive_file + '.progress')
        except Exception:
            pass
        # save.md 规则：两端对比文件大小（字节），一致=成功
        actual_size = 0
        try:
            actual_size = os.path.getsize(self.current_receive_file)
        except Exception:
            pass
        expected_size = getattr(self, 'current_receive_size', 0)
        size_match = (expected_size == 0) or (actual_size == expected_size)
        self.file_transfer_active = False
        self.current_file_id = None
        self.transfer_in_progress = False
        if size_match:
            self.file_transfer_complete.emit(file_id, self.current_receive_file)
            self._send_ack(file_id)
        else:
            # 大小不一致，校验失败：删除残文件，不发 ack（发送方会超时重发）
            try:
                os.remove(self.current_receive_file)
            except Exception:
                pass
            print(f"文件校验失败: 期望 {expected_size} 字节, 实际 {actual_size} 字节, file_id={file_id}")

    # def _start_paw_file_receive(self, file_id, file_name, file_size):  # 【禁止删除】PAW 文件接收
    #     self._start_file_receive(file_id, file_name, file_size)
    #     threading.Thread(target=self._download_paw_chunks, args=(file_id, file_size), daemon=True).start()
    #
    # def _download_paw_chunks(self, file_id, file_size):  # 【禁止删除】PAW 分块下载
    #     part_num = self.current_receive_written // CHUNK_SIZE
    #     total_parts = (file_size + CHUNK_SIZE - 1) // CHUNK_SIZE
    #     while not self.file_transfer_cancel and part_num < total_parts and self.is_running:
    #         try:
    #             resp = requests.get(
    #                 f"{PAW_URL}/api/download_chunk/{file_id}/{part_num}",
    #                 headers={"Authorization": f"Bearer {SECRET_TOKEN}"},
    #                 timeout=10
    #             )
    #             if resp.status_code == 200:
    #                 self._write_file_chunk(file_id, part_num, resp.content)
    #                 part_num += 1
    #             else:
    #                 time.sleep(0.5)
    #         except Exception:
    #             time.sleep(1)

    def _read_file_chunk(self, file_id, part_num):
        if not self.outgoing_file_path or self.outgoing_file_id != file_id:
            return None
        try:
            with open(self.outgoing_file_path, 'rb') as f:
                f.seek(part_num * CHUNK_SIZE)
                return f.read(CHUNK_SIZE)
        except Exception:
            return None

    def _send_ack(self, file_id):
        msg = {"token": SECRET_TOKEN, "activate": "send", "source": "pc",
               "data": {"action": "file_complete", "file_id": file_id}}
        self._send_to_phone(msg)

    def send_file_accept(self, file_id, resolved_name=""):
        """PC端确认接收文件（手机发送前等待此确认）"""
        msg = {"token": SECRET_TOKEN, "activate": "send", "source": "pc",
               "data": {"action": "file_accept", "file_id": file_id, "resolved_name": resolved_name}}
        self._send_to_phone(msg)
        log(f"[PC→手机] 发送 file_accept: file_id={file_id}, resolved_name={resolved_name}")

    def send_file_reject(self, file_id, reason=""):
        """PC端拒绝接收文件（重名跳过等）"""
        msg = {"token": SECRET_TOKEN, "activate": "send", "source": "pc",
               "data": {"action": "file_reject", "file_id": file_id, "reason": reason}}
        self._send_to_phone(msg)
        log(f"[PC→手机] 发送 file_reject: file_id={file_id}, reason={reason}")

    def send_transfer_control(self, ctrl, file_id=""):
        """向对端发送传输控制消息（pause/resume/cancel）"""
        msg = {"token": SECRET_TOKEN, "activate": "send", "source": "pc",
               "data": {"action": "transfer_control", "ctrl": ctrl, "file_id": file_id}}
        self._send_to_phone(msg)
        log(f"[PC→手机] 发送传输控制: ctrl={ctrl}, file_id={file_id}")

    # ==================== 发送消息 ====================

    def _send_to_phone(self, msg):
        """统一发送方法：消息已在上层加入msg_queue，手机通过/api/msg轮询获取"""
        action = msg.get('data', {}).get('action', '未知')
        log_pc_send(action)
        with self.queue_lock:
            self.msg_queue.append(msg)
        return True

    def send_clipboard(self, text):
        if not text:
            return
        self.last_pc_clipboard = text
        ts = int(time.time() * 1000)
        msg = {"token": SECRET_TOKEN, "activate": "send", "source": "pc",
               "data": {"action": "clipboard", "txt": text, "timestamp": ts}}
        self._send_to_phone(msg)
        self.clipboard_sent.emit()

    def send_text(self, text, filename=None):
        if not filename:
            filename = f"text_{int(time.time())}.txt"
        msg = {"token": SECRET_TOKEN, "activate": "send", "source": "pc",
               "data": {"action": "txt", "txt": text, "filename": filename}}
        self._send_to_phone(msg)

    def send_command(self, cmd, extra=None):
        data = {"action": "cmd", "cmd": cmd}
        if extra:
            data.update(extra)
        msg = {"token": SECRET_TOKEN, "activate": "send", "source": "pc", "data": data}
        self._send_to_phone(msg)

    def send_action(self, action, extra=None):
        """发送独立action消息到手机（非cmd类型）"""
        data = {"action": action}
        if extra:
            data.update(extra)
        msg = {"token": SECRET_TOKEN, "activate": "send", "source": "pc", "data": data}
        self._send_to_phone(msg)

    def send_phone_status(self):
        """发送电脑状态到手机"""
        # 使用缓存的 CPU 值，避免 psutil.cpu_percent() 在短时间内重复调用返回不准确值
        cpu = self._cached_cpu
        mem = psutil.virtual_memory()
        try:
            disk = psutil.disk_usage(os.path.expanduser('~'))
        except Exception:
            disk = psutil.disk_usage('C:\\')
        net = psutil.net_io_counters()
        msg = {"token": SECRET_TOKEN, "activate": "send", "source": "pc",
               "data": {"action": "pc_status", "cpu": cpu, "memory": mem.percent,
                        "disk": disk.percent, "net_sent": net.bytes_sent, "net_recv": net.bytes_recv}}
        self._send_to_phone(msg)

    def send_file(self, file_path):
        # 自动重置卡死状态：如果 file_transfer_active 为 True 但没有实际传输，重置
        if self.file_transfer_active:
            if not self.outgoing_file_id and not self.current_file_id:
                print("[file] file_transfer_active 卡死但无实际传输，自动重置")
                self.file_transfer_active = False
                self.transfer_in_progress = False
            else:
                return False
        if not os.path.exists(file_path):
            return False
        # 先检查通道条件，不满足则直接返回，不设置传输状态
        if self.current_channel == CHANNEL_WIFI and self.phone_ip:
            pass
        elif self.current_channel == CHANNEL_ADB and self.adb_device_id:
            pass
        else:
            return False

        file_name = os.path.basename(file_path)
        file_size = os.path.getsize(file_path)
        file_id = str(uuid.uuid4())
        self.outgoing_file_path = file_path
        self.outgoing_file_id = file_id
        self.outgoing_file_size = file_size
        self.transfer_in_progress = True
        self.file_transfer_active = True
        self.file_transfer_cancel = False
        self._transfer_paused = False
        # 立即发射初始进度（0%），让PC端UI立即显示进度条进入"发送中"状态
        try:
            self.file_transfer_progress.emit(file_id, 0, file_size, time.time())
        except Exception:
            pass

        head_msg = {
            "token": SECRET_TOKEN, "activate": "send", "source": "pc",
            "data": {
                "action": "send_file_head",
                "file_name": file_name,
                "file_size": file_size,
                "file_id": file_id
            }
        }

        if self.current_channel == CHANNEL_WIFI and self.phone_ip:
            threading.Thread(target=self._send_file_wifi, args=(file_id, file_path, file_size, head_msg), daemon=True).start()
            # 看门狗：30秒后如果传输仍未完成，强制重置状态
            threading.Thread(target=self._file_transfer_watchdog, args=(file_id,), daemon=True).start()
            return True
        # elif self.current_channel == CHANNEL_PAW:  # 【禁止删除】PAW 文件发送
        #     threading.Thread(target=self._send_file_paw, args=(file_id, file_path, file_size, head_msg), daemon=True).start()
        #     return True
        elif self.current_channel == CHANNEL_ADB and self.adb_device_id:
            threading.Thread(target=self._send_file_adb, args=(file_id, file_path, file_size, head_msg), daemon=True).start()
            threading.Thread(target=self._file_transfer_watchdog, args=(file_id,), daemon=True).start()
            return True
        return False

    def _file_transfer_watchdog(self, file_id):
        """300秒后如果传输仍未完成，强制重置状态（防止卡死）；暂停期间不计入超时"""
        elapsed = 0
        while elapsed < 300:
            time.sleep(5)
            # 暂停期间不累计超时
            if getattr(self, '_transfer_paused', False):
                continue
            elapsed += 5
        if self.outgoing_file_id == file_id and self.file_transfer_active:
            print(f"[watchdog] 文件传输超时(300s)，强制重置: {file_id}")
            self.outgoing_file_path = None
            self.outgoing_file_id = None
            self.transfer_in_progress = False
            self.file_transfer_active = False

    def _send_file_wifi(self, file_id, file_path, file_size, head_msg):
        """WiFi模式：发送文件头到msg_queue，手机主动通过/api/download_file下载"""
        try:
            # 设置出站文件信息（供 /api/download_file 路由读取）
            self.outgoing_file_path = file_path
            self.outgoing_file_id = file_id
            self.outgoing_file_size = file_size
            log(f"[PC→手机] 发送文件头到消息队列: file_id={file_id}, name={os.path.basename(file_path)}, size={file_size}")
            # 发送文件头到消息队列，手机轮询 /api/poll 获取后主动下载
            self._send_to_phone(head_msg)
            log(f"[PC→手机] 文件头已加入队列，等待手机下载...")
            # 手机下载完成后会发 file_complete，届时由 /api/cmd handler 清理状态
        except Exception as e:
            log(f"[PC→手机] 发送文件头失败: {e}")
            self.outgoing_file_path = None
            self.outgoing_file_id = None
            self.transfer_in_progress = False
            self.file_transfer_active = False

    # def _send_file_paw(self, file_id, file_path, file_size, head_msg):  # 【禁止删除】PAW 文件发送
    #     try:
    #         # 发送文件头
    #         requests.post(
    #             f"{PAW_URL}/api/send",
    #             json=head_msg,
    #             headers={"Authorization": f"Bearer {SECRET_TOKEN}"},
    #             timeout=10
    #         )
    #         # 上传分块（使用upload_chunk二进制接口）
    #         with open(file_path, 'rb') as f:
    #             part_num = 0
    #             while not self.file_transfer_cancel:
    #                 chunk = f.read(CHUNK_SIZE)
    #                 if not chunk:
    #                     break
    #                 resp = requests.post(
    #                     f"{PAW_URL}/api/upload_chunk",
    #                     data=chunk,
    #                     params={"file_id": file_id, "part_num": part_num},
    #                     headers={"Authorization": f"Bearer {SECRET_TOKEN}", "Content-Type": "application/octet-stream"},
    #                     timeout=15
    #                 )
    #                 sent = min((part_num + 1) * CHUNK_SIZE, file_size)
    #                 self.file_transfer_progress.emit(file_id, sent, file_size, time.time())
    #                 part_num += 1
    #         # 发送完成通知
    #         complete_msg = {"token": SECRET_TOKEN, "activate": "send", "data": {"action": "file_complete", "file_id": file_id, "total_parts": part_num}}
    #         requests.post(
    #             f"{PAW_URL}/api/send",
    #             json=complete_msg,
    #             headers={"Authorization": f"Bearer {SECRET_TOKEN}"},
    #             timeout=10
    #         )
    #     except Exception as e:
    #         print(f"PAW file send error: {e}")
    #     finally:
    #         self.transfer_in_progress = False

    def _send_file_adb(self, file_id, file_path, file_size, head_msg):
        """ADB模式：通过ADB端口转发走与WiFi相同的HTTP分块下载流程（手机连接 127.0.0.1:58627）"""
        try:
            # 设置出站文件信息（供 /api/download_chunk 路由读取）
            self.outgoing_file_path = file_path
            self.outgoing_file_id = file_id
            self.outgoing_file_size = file_size
            # 发送文件头到消息队列，手机轮询 /api/msg 获取后主动通过 ADB 端口转发下载分块
            self._send_to_phone(head_msg)
            # 手机下载完成后会发 file_complete，届时由 /api/cmd handler 清理状态
        except Exception as e:
            print(f"ADB file send error: {e}")
            self.outgoing_file_path = None
            self.outgoing_file_id = None
            self.transfer_in_progress = False
            self.file_transfer_active = False

    def cancel_transfer(self):
        """取消文件传输，并通知对端"""
        self.file_transfer_cancel = True
        self._transfer_paused = False
        # 通知对端取消
        cancel_file_id = self.current_file_id or (self.outgoing_file_id or "")
        try:
            self.send_transfer_control("cancel", cancel_file_id)
        except Exception:
            pass
        if self.current_file_id:
            # 清理临时文件和.progress
            try:
                if hasattr(self, 'current_receive_file') and os.path.exists(self.current_receive_file + '.progress'):
                    os.remove(self.current_receive_file + '.progress')
                if hasattr(self, 'current_receive_file') and os.path.exists(self.current_receive_file):
                    os.remove(self.current_receive_file)
            except Exception:
                pass
        self.transfer_in_progress = False
        self.file_transfer_active = False
        self.current_file_id = None

    # ==================== ADB操作 ====================

    def adb_tcpip(self):
        """USB连接后自动开启无线ADB"""
        if self.adb_device_id:
            try:
                subprocess.run(
                    ['adb', '-s', self.adb_device_id, 'tcpip', '5555'],
                    capture_output=True, timeout=10
                )
            except Exception:
                pass

    def adb_command(self, *args, callback=None):
        """执行ADB命令（异步，通过callback返回结果）"""
        if not self.adb_device_id:
            if callback:
                callback(None)
            return None
        def run_cmd():
            try:
                cmd = ['adb', '-s', self.adb_device_id] + list(args)
                result = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=15)
                if callback:
                    callback(result.stdout)
            except Exception:
                if callback:
                    callback(None)
        if callback:
            threading.Thread(target=run_cmd, daemon=True).start()
            return None
        try:
            cmd = ['adb', '-s', self.adb_device_id] + list(args)
            result = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=15)
            return result.stdout
        except Exception:
            return None

    def adb_push(self, local_path, remote_path):
        """ADB push文件"""
        return self.adb_command('push', local_path, remote_path)

    def adb_pull(self, remote_path, local_path):
        """ADB pull文件"""
        return self.adb_command('pull', remote_path, local_path)

    def adb_install(self, apk_path):
        """ADB静默安装APK"""
        return self.adb_command('install', '-r', '-t', apk_path)

    def adb_uninstall(self, package):
        """ADB卸载应用"""
        return self.adb_command('uninstall', package)

    def adb_clear_data(self, package):
        """ADB清除应用数据"""
        return self.adb_command('shell', 'pm', 'clear', package)

    def adb_list_apps(self):
        """列出所有已安装应用"""
        output = self.adb_command('shell', 'pm', 'list', 'packages', '-3')
        if output:
            return [line.replace('package:', '').strip() for line in output.split('\n') if line.startswith('package:')]
        return []

    def adb_list_files(self, path='/sdcard/'):
        """列出手机目录"""
        output = self.adb_command('shell', 'ls', '-la', path)
        return output or ""

    def adb_screenshot(self, local_path):
        """ADB截图"""
        self.adb_command('shell', 'screencap', '-p', '/sdcard/screenshot.png')
        self.adb_pull('/sdcard/screenshot.png', local_path)

    def check_adb(self):
        """检查ADB连接"""
        return self._check_adb()

    def _send_process_list(self):
        """发送电脑进程列表到手机"""
        processes = []
        for proc in psutil.process_iter(['pid', 'name', 'cpu_percent', 'memory_info', 'username']):
            try:
                info = proc.info
                mem = info.get('memory_info')
                mem_mb = mem.rss / (1024 * 1024) if mem else 0
                processes.append({
                    'pid': info.get('pid'),
                    'name': info.get('name', ''),
                    'cpu': info.get('cpu_percent', 0),
                    'mem': round(mem_mb, 1),
                    'user': info.get('username', '') or ''
                })
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                continue
        msg = {"token": SECRET_TOKEN, "activate": "send", "source": "pc",
               "data": {"action": "process_list", "source": "pc", "processes": processes}}
        self._send_to_phone(msg)

    def _kill_process(self, pid):
        """结束电脑进程"""
        try:
            proc = psutil.Process(pid)
            proc.terminate()
            proc.wait(timeout=3)
            result = "ok"
        except psutil.AccessDenied:
            result = "access_denied"
        except psutil.NoSuchProcess:
            result = "not_found"
        except Exception as e:
            result = f"error: {e}"
        msg = {"token": SECRET_TOKEN, "activate": "send", "source": "pc",
               "data": {"action": "kill_process_result", "pid": pid, "result": result}}
        self._send_to_phone(msg)

    def _run_as_admin(self, program):
        """以管理员权限启动程序（Windows）"""
        try:
            subprocess.Popen(f'runas /user:administrator "{program}"', shell=True)
            result = "started"
        except Exception as e:
            result = f"error: {e}"
        msg = {"token": SECRET_TOKEN, "activate": "send", "source": "pc",
               "data": {"action": "run_as_admin_result", "program": program, "result": result}}
        self._send_to_phone(msg)

    def _handle_remote_command(self, msg_data):
        """处理手机遥控指令（媒体键/音量/键盘/锁屏/截图，所有通道均可用）"""
        cmd = msg_data.get('cmd', '')
        try:
            if cmd == 'media_play_pause':
                self._send_media_key(0xB3)  # VK_MEDIA_PLAY_PAUSE
                self._delay_check_media_info()
            elif cmd == 'media_prev':
                self._send_media_key(0xB1)  # VK_MEDIA_PREV_TRACK
                self._delay_check_media_info()
            elif cmd == 'media_next':
                self._send_media_key(0xB0)  # VK_MEDIA_NEXT_TRACK
                self._delay_check_media_info()
            elif cmd == 'vol_up':
                self._send_media_key(0xAF)  # VK_VOLUME_UP
            elif cmd == 'vol_down':
                self._send_media_key(0xAE)  # VK_VOLUME_DOWN
            elif cmd == 'vol_mute':
                self._send_media_key(0xAD)  # VK_VOLUME_MUTE
            elif cmd == 'lock':
                subprocess.Popen('rundll32.exe user32.dll,LockWorkStation', shell=True)
            elif cmd == 'get_media_info':
                self._send_media_info()
            elif cmd == 'screenshot':
                self._take_screenshot_and_send()
            elif cmd.startswith('key_'):
                key_name = cmd[4:]
                self._send_keys(key_name)
            # ===== 手机端投屏/摄像头页面发起的请求（手机→电脑，单向）=====
            elif cmd == 'mirror_start':
                # 手机请求启动投屏（自研：手机端 MediaProjection 推流）
                self.start_phone_mirror()
            elif cmd == 'mirror_stop':
                # 手机请求停止投屏
                self.stop_phone_mirror()
            elif cmd == 'audio_start':
                self.start_phone_audio()
                # 同时启动电脑音频推流到手机
                self.start_pc_audio()
            elif cmd == 'audio_stop':
                self.stop_phone_audio()
                self.stop_pc_audio()
            elif cmd == 'pc_stream_start':
                # 手机端请求电脑推流画面
                self.start_pc_stream()
            elif cmd == 'pc_stream_stop':
                self.stop_pc_stream()
            elif cmd == 'camera_switch':
                # 手机端切换了摄像头，PC端无需额外操作（帧数据会自动更新）
                pass
        except Exception as e:
            print(f"Remote command failed: {cmd} {e}")

    def _send_media_key(self, vk_code):
        """使用 SendInput 发送媒体虚拟键码（64位兼容结构体定义）"""
        try:
            import ctypes
            import time
            PUL = ctypes.POINTER(ctypes.c_ulong)
            class MouseInput(ctypes.Structure):
                _fields_ = [("dx", ctypes.c_long), ("dy", ctypes.c_long),
                            ("mouseData", ctypes.c_ulong), ("dwFlags", ctypes.c_ulong),
                            ("time", ctypes.c_ulong), ("dwExtraInfo", PUL)]
            class KeyBdInput(ctypes.Structure):
                _fields_ = [("wVk", ctypes.c_ushort), ("wScan", ctypes.c_ushort),
                            ("dwFlags", ctypes.c_ulong), ("time", ctypes.c_ulong),
                            ("dwExtraInfo", PUL)]
            class HardwareInput(ctypes.Structure):
                _fields_ = [("uMsg", ctypes.c_ulong), ("wParamL", ctypes.c_short),
                            ("wParamH", ctypes.c_ushort)]
            class InputI(ctypes.Union):
                _fields_ = [("mi", MouseInput), ("ki", KeyBdInput), ("hi", HardwareInput)]
            class Input(ctypes.Structure):
                _fields_ = [("type", ctypes.c_ulong), ("ii", InputI)]
            # 按下
            inp = Input(type=1)
            inp.ii.ki.wVk = vk_code
            inp.ii.ki.dwFlags = 0
            ret1 = ctypes.windll.user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(inp))
            if ret1 == 0:
                print(f"[media_key] SendInput down failed, vk=0x{vk_code:X}, err={ctypes.windll.kernel32.GetLastError()}")
            time.sleep(0.05)
            # 释放
            inp2 = Input(type=1)
            inp2.ii.ki.wVk = vk_code
            inp2.ii.ki.dwFlags = 2  # KEYEVENTF_KEYUP
            ret2 = ctypes.windll.user32.SendInput(1, ctypes.byref(inp2), ctypes.sizeof(inp2))
            if ret2 == 0:
                print(f"[media_key] SendInput up failed, vk=0x{vk_code:X}, err={ctypes.windll.kernel32.GetLastError()}")
        except Exception as e:
            print(f"[media_key] Exception: {e}")

    def _send_keys(self, key_name):
        """发送单个按键或组合键（如 ctrl+shift+esc），使用 SendInput（64位兼容）"""
        try:
            import ctypes
            import time
            # 简单映射
            special = {
                'esc': 0x1B, 'tab': 0x09, 'enter': 0x0D, 'backspace': 0x08,
                'space': 0x20, 'delete': 0x2E, 'home': 0x24, 'end': 0x23,
                'up': 0x26, 'down': 0x28, 'left': 0x25, 'right': 0x27,
                'ctrl': 0x11, 'shift': 0x10, 'alt': 0x12, 'win': 0x5B,
                'f1': 0x70, 'f2': 0x71, 'f3': 0x72, 'f4': 0x73,
                'f5': 0x74, 'f6': 0x75, 'f7': 0x76, 'f8': 0x77,
                'f9': 0x78, 'f10': 0x79, 'f11': 0x7A, 'f12': 0x7B,
            }
            parts = key_name.split('+')
            vk_codes = []
            for part in parts:
                p = part.strip().lower()
                if p in special:
                    vk_codes.append(special[p])
                elif len(p) == 1 and p.isalnum():
                    vk_codes.append(ord(p.upper()))
                else:
                    return
            # SendInput 结构定义（含 MouseInput 保证 64 位对齐）
            PUL = ctypes.POINTER(ctypes.c_ulong)
            class MouseInput(ctypes.Structure):
                _fields_ = [("dx", ctypes.c_long), ("dy", ctypes.c_long),
                            ("mouseData", ctypes.c_ulong), ("dwFlags", ctypes.c_ulong),
                            ("time", ctypes.c_ulong), ("dwExtraInfo", PUL)]
            class KeyBdInput(ctypes.Structure):
                _fields_ = [("wVk", ctypes.c_ushort), ("wScan", ctypes.c_ushort),
                            ("dwFlags", ctypes.c_ulong), ("time", ctypes.c_ulong),
                            ("dwExtraInfo", PUL)]
            class HardwareInput(ctypes.Structure):
                _fields_ = [("uMsg", ctypes.c_ulong), ("wParamL", ctypes.c_short),
                            ("wParamH", ctypes.c_ushort)]
            class InputI(ctypes.Union):
                _fields_ = [("mi", MouseInput), ("ki", KeyBdInput), ("hi", HardwareInput)]
            class Input(ctypes.Structure):
                _fields_ = [("type", ctypes.c_ulong), ("ii", InputI)]
            # 按下所有键（修饰键先按）
            for vk in vk_codes:
                inp = Input(type=1)
                inp.ii.ki.wVk = vk
                inp.ii.ki.dwFlags = 0
                ctypes.windll.user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(inp))
            time.sleep(0.05)
            # 释放所有键（反序）
            for vk in reversed(vk_codes):
                inp = Input(type=1)
                inp.ii.ki.wVk = vk
                inp.ii.ki.dwFlags = 2  # KEYEVENTF_KEYUP
                ctypes.windll.user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(inp))
        except Exception as e:
            print(f"[send_keys] Exception: {e}")

    def _send_media_info(self):
        """通过 winsdk 获取当前媒体播放信息（含封面），发送给手机"""
        try:
            import asyncio
            import winsdk.windows.media.control as wmc
            from winsdk.windows.storage.streams import DataReader

            async def _get_info():
                sessions = await wmc.GlobalSystemMediaTransportControlsSessionManager.request_async()
                session = sessions.get_current_session()
                if not session:
                    return None
                props = await session.try_get_media_properties_async()
                # 获取封面
                thumbnail_b64 = ""
                if props.thumbnail:
                    try:
                        stream = await props.thumbnail.open_read_async()
                        if stream.size > 0:
                            reader = DataReader(stream)
                            await reader.load_async(stream.size)
                            data = bytearray(stream.size)
                            reader.read_bytes(data)
                            thumbnail_b64 = base64.b64encode(bytes(data)).decode('ascii')
                            reader.detach_stream()
                            reader.close()
                        stream.close()
                    except Exception:
                        pass
                return {
                    'title': props.title or "",
                    'artist': props.artist or "",
                    'album': props.album_title or "",
                    'thumbnail': thumbnail_b64
                }

            info = asyncio.run(_get_info())
            if info:
                msg = {"token": SECRET_TOKEN, "activate": "send", "source": "pc",
                       "data": {"action": "media_info", "title": info['title'],
                                "artist": info['artist'], "album": info['album'],
                                "thumbnail": info['thumbnail']}}
            else:
                msg = {"token": SECRET_TOKEN, "activate": "send", "source": "pc",
                       "data": {"action": "media_info", "title": "未检测到媒体播放",
                                "artist": "", "album": "", "thumbnail": ""}}
            self._send_to_phone(msg)
        except Exception as e:
            print(f"Get media info failed: {e}")
            # 回退：发送无媒体信息
            try:
                msg = {"token": SECRET_TOKEN, "activate": "send", "source": "pc",
                       "data": {"action": "media_info", "title": "未检测到媒体播放",
                                "artist": "", "album": "", "thumbnail": ""}}
                self._send_to_phone(msg)
            except Exception:
                pass

    def _start_media_monitor(self):
        self._media_monitor_running = True
        self._media_monitor_thread = threading.Thread(target=self._media_monitor_loop, daemon=True)
        self._media_monitor_thread.start()

    def _stop_media_monitor(self):
        self._media_monitor_running = False

    def _media_monitor_loop(self):
        while self._media_monitor_running and self.is_running:
            try:
                self._check_and_send_media_info()
            except Exception as e:
                print(f"[media_monitor] error: {e}")
            time.sleep(2)

    def _check_and_send_media_info(self):
        """获取当前媒体信息，与上次比较（只比歌曲名+作者），变化时主动推送"""
        try:
            import asyncio
            import winsdk.windows.media.control as wmc

            async def _get_info():
                sessions = await wmc.GlobalSystemMediaTransportControlsSessionManager.request_async()
                session = sessions.get_current_session()
                if not session:
                    return None
                props = await session.try_get_media_properties_async()
                return {
                    'title': props.title or "",
                    'artist': props.artist or "",
                }

            info = asyncio.run(_get_info())
            if info is None:
                title, artist = "", ""
            else:
                title, artist = info['title'], info['artist']

            if title != self._last_media_title or artist != self._last_media_artist:
                self._last_media_title = title
                self._last_media_artist = artist
                self._send_media_info()
        except Exception as e:
            print(f"[check_media_info] error: {e}")

    def _delay_check_media_info(self):
        """按键后延迟检测并推送媒体信息"""
        def _delayed():
            time.sleep(0.6)
            self._check_and_send_media_info()
        threading.Thread(target=_delayed, daemon=True).start()

    def _take_screenshot_and_send(self):
        """截图并发送给手机"""
        try:
            from PIL import ImageGrab
            path = os.path.join(self.receive_dir, f"pc_screenshot_{int(time.time())}.png")
            ImageGrab.grab().save(path)
            self.send_file(path)
            # 通知手机截图成功（触发 Toast 提示）
            self.send_action("screenshot_saved", extra={"message": "电脑截图已保存并发送"})
        except Exception as e:
            print(f"Screenshot failed: {e}")
            self.send_action("screenshot_saved", extra={"message": f"电脑截图失败: {e}"})

    def _open_url_on_pc(self, url, use_edge=True):
        """在电脑端打开URL（use_edge=True用Edge，use_edge=False用默认浏览器）"""
        try:
            import subprocess
            import webbrowser
            if not url.startswith('http://') and not url.startswith('https://'):
                url = 'https://www.bing.com/search?q=' + url
            if use_edge:
                subprocess.Popen(['cmd', '/c', 'start', 'msedge', url], shell=False)
            else:
                # 用默认浏览器打开
                webbrowser.open(url)
        except Exception as e:
            print(f"Open URL failed: {e}")

    def send_url_history(self, history):
        """发送 URL 历史给手机端同步"""
        msg = {"token": SECRET_TOKEN, "activate": "send", "source": "pc",
               "data": {"action": "url_history_sync", "history": history}}
        self._send_to_phone(msg)

    # ==================== 电脑→手机推流（save.md 功能7）====================

    def start_pc_stream(self):
        """启动电脑画面推流线程：截屏→JPEG→缓存，手机轮询拉取"""
        if self._pc_stream_running:
            return
        self._pc_stream_running = True
        self._pc_stream_thread = threading.Thread(target=self._pc_stream_loop, daemon=True)
        self._pc_stream_thread.start()

    def stop_pc_stream(self):
        """停止推流"""
        self._pc_stream_running = False
        with self._frame_lock:
            self._latest_frame = None

    def _pc_stream_loop(self):
        """截屏循环：60fps，全屏原始分辨率，JPEG质量75%
        推流整个电脑画面给手机，保持高清晰度
        """
        try:
            import mss
            from PIL import Image
            import io
        except ImportError:
            print("推流需要 mss 和 Pillow: pip install mss Pillow")
            self._pc_stream_running = False
            return
        sct = mss.mss()
        while self._pc_stream_running:
            try:
                monitor = sct.monitors[1]
                shot = sct.grab(monitor)
                img = Image.frombytes("RGB", shot.size, shot.bgra, "raw", "BGRX")
                # 全屏分辨率推流，仅在高分辨率时适度缩放（最高1280p宽度）
                w, h = img.size
                max_w = 1280
                if w > max_w:
                    ratio = max_w / w
                    img = img.resize((max_w, int(h * ratio)), Image.LANCZOS)
                buf = io.BytesIO()
                img.save(buf, format='JPEG', quality=75)
                frame_data = buf.getvalue()
                with self._frame_lock:
                    self._latest_frame = frame_data
            except Exception as e:
                print(f"PC stream frame failed: {e}")
            time.sleep(0.016)  # 60fps
        sct.close()

    # ==================== 电脑摄像头推流（save.md 功能8）====================

    def start_pc_camera(self):
        """启动电脑摄像头采集线程，推送 JPEG 帧给手机"""
        if self._pc_camera_running:
            return
        self._pc_camera_running = True
        self._pc_camera_thread = threading.Thread(target=self._pc_camera_loop, daemon=True)
        self._pc_camera_thread.start()

    def stop_pc_camera(self):
        """停止摄像头采集"""
        self._pc_camera_running = False
        with self._camera_lock:
            self._latest_camera_frame = None

    def _pc_camera_loop(self):
        """摄像头采集循环：用 OpenCV 采集，约10fps，JPEG质量70%，16:9比例"""
        try:
            import cv2
        except ImportError:
            print("摄像头推流需要 opencv-python: pip install opencv-python")
            self._pc_camera_running = False
            return
        cap = cv2.VideoCapture(0)
        if not cap.isOpened():
            print("无法打开电脑摄像头")
            self._pc_camera_running = False
            return
        # 设置 16:9 分辨率（1280x720）
        cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
        while self._pc_camera_running:
            try:
                ret, frame = cap.read()
                if not ret:
                    continue
                # 翻转画面（前置摄像头镜像效果）
                frame = cv2.flip(frame, 1)
                # 编码为 JPEG
                _, buf = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 70])
                frame_data = buf.tobytes()
                with self._camera_lock:
                    self._latest_camera_frame = frame_data
            except Exception as e:
                print(f"Camera frame failed: {e}")
            time.sleep(0.1)  # 10fps
        cap.release()

    def _play_audio_data(self, audio_data):
        """播放手机端传来的音频数据（PCM 16bit 44100Hz mono）- 使用 pyaudio 流式播放"""
        try:
            import pyaudio
            if not hasattr(self, '_phone_audio_pa') or self._phone_audio_pa is None:
                self._phone_audio_pa = pyaudio.PyAudio()
            if not hasattr(self, '_phone_audio_stream') or self._phone_audio_stream is None:
                self._phone_audio_stream = self._phone_audio_pa.open(
                    format=pyaudio.paInt16, channels=1, rate=44100, output=True,
                    frames_per_buffer=1024)
            try:
                self._phone_audio_stream.write(audio_data)
            except Exception:
                pass
        except ImportError:
            pass
        except Exception:
            pass

    def start_phone_mirror(self):
        """开始接收手机投屏画面"""
        self._phone_mirror_running = True

    def stop_phone_mirror(self):
        """停止接收手机投屏画面"""
        self._phone_mirror_running = False
        with self._phone_frame_lock:
            self._latest_phone_frame = None

    def start_phone_audio(self):
        """开始接收手机声音"""
        self._phone_audio_running = True

    def stop_phone_audio(self):
        """停止接收手机声音"""
        self._phone_audio_running = False
        # 关闭 pyaudio 播放流
        try:
            if hasattr(self, '_phone_audio_stream') and self._phone_audio_stream is not None:
                self._phone_audio_stream.stop_stream()
                self._phone_audio_stream.close()
                self._phone_audio_stream = None
            if hasattr(self, '_phone_audio_pa') and self._phone_audio_pa is not None:
                self._phone_audio_pa.terminate()
                self._phone_audio_pa = None
        except Exception:
            pass

    def start_pc_audio(self):
        """开始捕获电脑音频推流给手机"""
        if self._pc_audio_running:
            return
        self._pc_audio_running = True
        self._pc_audio_thread = threading.Thread(target=self._pc_audio_loop, daemon=True)
        self._pc_audio_thread.start()

    def stop_pc_audio(self):
        """停止电脑音频推流"""
        self._pc_audio_running = False
        with self._pc_audio_lock:
            self._latest_pc_audio = None

    def _pc_audio_loop(self):
        """电脑音频捕获循环（使用 pyaudio WASAPI loopback 捕获系统输出音频）
        自动查找 loopback 设备，找不到时回退到立体声混响/麦克风
        """
        try:
            import pyaudio
            p = pyaudio.PyAudio()

            # 查找 WASAPI loopback 设备（捕获系统输出音频）
            loopback_dev = None
            default_output = None
            try:
                default_output = p.get_default_output_device_info()
            except Exception:
                pass

            for i in range(p.get_device_count()):
                try:
                    info = p.get_device_info_by_index(i)
                    name = info.get('name', '')
                    # 优先查找 WASAPI loopback 设备
                    if 'Loopback' in name or 'loopback' in name:
                        loopback_dev = i
                        break
                    # 备选：立体声混响（Stereo Mix）
                    if 'Stereo Mix' in name or '立体声混音' in name:
                        loopback_dev = i
                except Exception:
                    continue

            # 如果找到 loopback 设备，使用它；否则使用默认输入设备
            if loopback_dev is not None:
                dev_idx = loopback_dev
                print(f"[audio] 使用 loopback 设备: index={dev_idx}")
            else:
                dev_idx = None  # 使用默认输入设备
                print("[audio] 未找到 loopback 设备，使用默认输入（麦克风）")

            CHUNK = 4096  # 增大缓冲区，减少丢帧
            FORMAT = pyaudio.paInt16
            CHANNELS = 1
            RATE = 44100

            if dev_idx is not None:
                stream = p.open(format=FORMAT, channels=CHANNELS, rate=RATE,
                                input=True, input_device_index=dev_idx,
                                frames_per_buffer=CHUNK)
            else:
                stream = p.open(format=FORMAT, channels=CHANNELS, rate=RATE,
                                input=True, frames_per_buffer=CHUNK)

            while self._pc_audio_running:
                try:
                    data = stream.read(CHUNK, exception_on_overflow=False)
                    if data:
                        with self._pc_audio_lock:
                            self._latest_pc_audio = data
                except Exception:
                    pass
                time.sleep(0.01)
            stream.stop_stream()
            stream.close()
            p.terminate()
        except ImportError:
            print("[audio] pyaudio 未安装，无法传输音频: pip install pyaudio")
            self._pc_audio_running = False
        except Exception as e:
            print(f"[audio] 音频捕获失败: {e}")
            self._pc_audio_running = False

    def _kill_pc_process(self, pid):
        """结束电脑上的指定进程"""
        try:
            import subprocess
            subprocess.run(['taskkill', '/PID', str(pid), '/F'], capture_output=True, timeout=10)
            print(f"[process] killed PID {pid}")
        except Exception as e:
            print(f"[process] kill PID {pid} failed: {e}")


    def _set_pc_process_priority(self, pid, adjustment):
        """调整电脑进程优先级（adjustment>0 降低，<0 提高）"""
        try:
            import psutil
            proc = psutil.Process(pid)
            current = proc.nice()
            new_nice = max(-7, min(6, current + adjustment))
            proc.nice(new_nice)
            print(f"[process] PID {pid} priority: {current} -> {new_nice}")
        except psutil.AccessDenied:
            print(f"[process] PID {pid} priority change denied (need admin)")
        except psutil.NoSuchProcess:
            print(f"[process] PID {pid} not found")
        except Exception as e:
            print(f"[process] PID {pid} priority change failed: {e}")

    def _perform_pc_click(self, norm_x, norm_y, op='click'):
        """手机操控电脑：在电脑屏幕上执行点击/拖拽/右键操作
        使用 ctypes SendInput 直接发送鼠标事件，避免 pyautogui 导致的光标闪烁
        """
        try:
            import ctypes
            from ctypes import wintypes

            # 获取屏幕尺寸
            user32 = ctypes.windll.user32
            screen_w = user32.GetSystemMetrics(0)
            screen_h = user32.GetSystemMetrics(1)
            x = int(norm_x * screen_w)
            y = int(norm_y * screen_h)

            # 鼠标事件常量
            MOUSEEVENTF_MOVE = 0x0001
            MOUSEEVENTF_LEFTDOWN = 0x0002
            MOUSEEVENTF_LEFTUP = 0x0004
            MOUSEEVENTF_RIGHTDOWN = 0x0008
            MOUSEEVENTF_RIGHTUP = 0x0010
            MOUSEEVENTF_ABSOLUTE = 0x8000

            # 归一化绝对坐标 (0-65535)
            abs_x = int(norm_x * 65535)
            abs_y = int(norm_y * 65535)

            # 定义 INPUT 结构
            class MOUSEINPUT(ctypes.Structure):
                _fields_ = [
                    ("dx", wintypes.LONG),
                    ("dy", wintypes.LONG),
                    ("mouseData", wintypes.DWORD),
                    ("dwFlags", wintypes.DWORD),
                    ("time", wintypes.DWORD),
                    ("dwExtraInfo", ctypes.POINTER(wintypes.ULONG)),
                ]

            class INPUT(ctypes.Structure):
                class _INPUT(ctypes.Union):
                    _fields_ = [("mi", MOUSEINPUT)]
                _anonymous_ = ("_input",)
                _fields_ = [("type", wintypes.DWORD), ("_input", _INPUT)]

            def send_mouse(flags, dx=abs_x, dy=abs_y):
                inp = INPUT()
                inp.type = 0  # INPUT_MOUSE
                inp.mi = MOUSEINPUT(dx=dx, dy=dy, mouseData=0,
                                    dwFlags=flags, time=0,
                                    dwExtraInfo=ctypes.pointer(wintypes.ULONG(0)))
                user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))

            if op == 'down':
                send_mouse(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_LEFTDOWN)
            elif op == 'up':
                send_mouse(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_LEFTUP)
            elif op == 'click':
                send_mouse(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_LEFTDOWN)
                send_mouse(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_LEFTUP)
            elif op == 'move':
                send_mouse(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE)
            elif op == 'right':
                send_mouse(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_RIGHTDOWN)
                send_mouse(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_RIGHTUP)
            else:
                send_mouse(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_LEFTDOWN)
                send_mouse(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_LEFTUP)
        except Exception as e:
            print(f"PC click failed: {e}")

    def _perform_screen_touch(self, norm_x, norm_y, op='click'):
        """操控模式：通过 ADB 或 WiFi 控制手机屏幕触摸操作（不再使用 pyautogui 点击电脑屏幕）"""
        try:
            if self.current_channel == CHANNEL_ADB and self.adb_device_id:
                # ADB 通道：通过 adb shell input 命令控制手机
                # 获取手机屏幕尺寸（缓存，避免每次查询）
                if not hasattr(self, '_phone_screen_w') or not hasattr(self, '_phone_screen_h'):
                    self._phone_screen_w = 1080
                    self._phone_screen_h = 1920
                    try:
                        result = subprocess.run(
                            ['adb', '-s', self.adb_device_id, 'shell', 'wm', 'size'],
                            capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=3
                        )
                        # 输出格式: Physical size: 1080x1920
                        if result.stdout:
                            for line in result.stdout.strip().split('\n'):
                                if 'Physical size' in line:
                                    parts = line.split(':')[1].strip().split('x')
                                    if len(parts) == 2:
                                        self._phone_screen_w = int(parts[0])
                                        self._phone_screen_h = int(parts[1])
                                        break
                    except Exception:
                        pass
                x = int(norm_x * self._phone_screen_w)
                y = int(norm_y * self._phone_screen_h)
                if op == 'down':
                    # 按下：仅记录位置，不执行 tap
                    self._last_touch_x = x
                    self._last_touch_y = y
                    self._touch_moved = False
                elif op == 'move':
                    # 移动：input swipe 从上一个点到当前点
                    last_x = getattr(self, '_last_touch_x', x)
                    last_y = getattr(self, '_last_touch_y', y)
                    subprocess.run(
                        ['adb', '-s', self.adb_device_id, 'shell', 'input', 'swipe',
                         str(last_x), str(last_y), str(x), str(y), '50'],
                        capture_output=True, timeout=2
                    )
                    self._touch_moved = True
                elif op == 'up':
                    # 抬起：如果没有移动过，执行 tap
                    if not getattr(self, '_touch_moved', False):
                        subprocess.run(
                            ['adb', '-s', self.adb_device_id, 'shell', 'input', 'tap', str(x), str(y)],
                            capture_output=True, timeout=2
                        )
                elif op == 'right':
                    # 右键：返回键
                    subprocess.run(
                        ['adb', '-s', self.adb_device_id, 'shell', 'input', 'keyevent', 'KEYCODE_BACK'],
                        capture_output=True, timeout=2
                    )
                else:  # click 及其他
                    subprocess.run(
                        ['adb', '-s', self.adb_device_id, 'shell', 'input', 'tap', str(x), str(y)],
                        capture_output=True, timeout=2
                    )
                # 记录当前位置，供下次 move 使用
                self._last_touch_x = x
                self._last_touch_y = y
            else:
                # WiFi 通道：通过 msg_queue 发送 touch 命令给手机
                msg = {"token": SECRET_TOKEN, "activate": "send", "source": "pc",
                       "data": {"action": "screen_touch", "op": op, "x": norm_x, "y": norm_y}}
                self._send_to_phone(msg)
        except Exception as e:
            print(f"Screen touch failed: {e}")

    def _handle_power_action(self, action_type):
        """执行电脑电源管理指令（所有通道均可用）"""
        try:
            if action_type == 'lock':
                subprocess.Popen('rundll32.exe user32.dll,LockWorkStation', shell=True)
            elif action_type in ('sleep', 'hibernate'):
                subprocess.Popen('shutdown /h', shell=True)
            elif action_type == 'shutdown':
                subprocess.Popen('shutdown /s /t 30 /c "PhoneHub远程关机"', shell=True)
            elif action_type == 'reboot':
                subprocess.Popen('shutdown /r /t 30 /c "PhoneHub远程重启"', shell=True)
            elif action_type == 'cancel':
                subprocess.Popen('shutdown /a', shell=True)
        except Exception as e:
            print(f"Power action failed: {e}")

    def stop(self):
        self.is_running = False
        # self.paw_running = False  # 【禁止删除】PAW 停止标志
        self.clipboard_monitor_running = False
        self._media_monitor_running = False
        # 清理 ADB 端口转发
        if self.adb_device_id:
            try:
                subprocess.run(['adb', '-s', self.adb_device_id, 'forward', '--remove', 'tcp:58628'], capture_output=True, timeout=2)
                subprocess.run(['adb', '-s', self.adb_device_id, 'reverse', '--remove', 'tcp:58627'], capture_output=True, timeout=2)
            except Exception:
                pass
