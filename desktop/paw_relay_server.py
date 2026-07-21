"""
PhoneHub PAW (PythonAnywhere) Relay Server
============================================
中转代理服务：当 PC 和手机不在同一局域网时，通过此服务器进行消息中转。

部署到 PythonAnywhere:
1. 登录 https://www.pythonanywhere.com
2. 创建 Web App (Flask, Python 3.10+)
3. 将本文件放到 /home/<username>/phonehub_paw/app.py
4. WSGI 配置指向 /home/<username>/phonehub_paw/venv/bin/activate
5. 设置环境变量 PHONEHUB_SECRET_TOKEN 为默认 token

协议规范参考 save.md 第三节
"""

import os
import time
import json
import uuid
import threading
import shutil
import logging
from flask import Flask, request, jsonify, Response, stream_with_context
from flask_cors import CORS

# ==================== 配置 ====================

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "paw_data")
BLOCKS_DIR = os.path.join(DATA_DIR, "blocks")
FILES_DIR = os.path.join(DATA_DIR, "files")
SECRET_TOKEN = os.environ.get("PHONEHUB_SECRET_TOKEN", "541881452418845")
HEARTBEAT_TIMEOUT = 30    # 秒，心跳超时视为离线
MSG_QUEUE_MAX = 50        # 消息队列最大长度
BLOCK_CLEANUP_INTERVAL = 600  # 秒，文件块清理间隔（10分钟）
DEVICE_CLEANUP_INTERVAL = 60    # 秒，设备清理间隔
FILE_BLOCK_SIZE = 64 * 1024     # 64KB 分块大小
FLOW_CONTROL_HIGH = 300 * 1024 * 1024  # 300MB 流量控制上限
FLOW_CONTROL_LOW = 250 * 1024 * 1024   # 250MB 流量控制下限

logging.basicConfig(level=logging.INFO, format="%(asctime)s [PAW] %(levelname)s %(message)s")
logger = logging.getLogger("phonehub-paw")

# 确保数据目录存在
os.makedirs(BLOCKS_DIR, exist_ok=True)
os.makedirs(FILES_DIR, exist_ok=True)


# ==================== 数据存储 ====================

# 设备注册表: {device_id: {"last_heartbeat": timestamp, "type": "pc"|"phone"}}
devices = {}
devices_lock = threading.Lock()

# 消息队列: {device_id: [messages]}
msg_queues = {}
msg_queues_lock = threading.Lock()

# 文件块存储: {file_id: {part_num: {"path": str, "size": int, "timestamp": float}}}
file_blocks = {}
file_blocks_lock = threading.Lock()

# 流量控制: {device_id: total_bytes}
flow_control = {}
flow_control_lock = threading.Lock()

# 传输暂停状态: {device_id: bool}
transfer_paused = {}
transfer_paused_lock = threading.Lock()


# ==================== Flask App ====================

app = Flask(__name__)
CORS(app)


def check_auth(req):
    """验证 Bearer Token"""
    auth = req.headers.get("Authorization", "")
    if auth.startswith("Bearer "):
        return auth[7:] == SECRET_TOKEN
    return False


def get_sender_device_id(req):
    """从请求头或 body 获取发送者 device_id"""
    # 优先从请求头获取（PAW 协议标准）
    device_id = req.headers.get("X-Device-Id", "")
    if device_id:
        return device_id
    # 回退到 body
    data = req.get_json(silent=True) or {}
    return data.get("device_id", "")


@app.errorhandler(Exception)
def handle_error(e):
    """全局异常处理"""
    logger.error(f"Error: {e}")
    return jsonify({"error": str(e)}), 500


# ==================== 心跳与注册 ====================

@app.route("/api/register", methods=["POST"])
def register_device():
    """设备注册：手机/PC 首次连接时注册"""
    if not check_auth(request):
        return jsonify({"error": "unauthorized"}), 403

    data = request.get_json()
    if not data:
        return jsonify({"error": "missing json body"}), 400

    device_id = data.get("device_id", "")
    device_type = data.get("type", "")  # "pc" or "phone"
    paired_id = data.get("paired_id", "")

    if not device_id or not device_type:
        return jsonify({"error": "device_id and type required"}), 400

    if device_type not in ("pc", "phone"):
        return jsonify({"error": "type must be 'pc' or 'phone'"}), 400

    with devices_lock:
        devices[device_id] = {
            "last_heartbeat": time.time(),
            "type": device_type,
            "registered_at": time.time(),
        }

    if paired_id:
        with flow_control_lock:
            if device_type == "pc":
                flow_control[device_id] = 0
            else:
                flow_control[paired_id] = flow_control.get(paired_id, 0)

    logger.info(f"Registered: {device_id} ({device_type}), paired={paired_id}")
    return jsonify({"status": "ok", "device_id": device_id})


@app.route("/api/heartbeat", methods=["POST"])
def heartbeat():
    """心跳保活"""
    if not check_auth(request):
        return jsonify({"error": "unauthorized"}), 403

    data = request.get_json()
    device_id = data.get("device_id", "")
    if not device_id:
        return jsonify({"error": "device_id required"}), 400

    with devices_lock:
        if device_id in devices:
            devices[device_id]["last_heartbeat"] = time.time()
            return jsonify({"status": "ok"})
        else:
            return jsonify({"error": "device not registered"}), 404


@app.route("/api/disconnect", methods=["POST"])
def disconnect_device():
    """设备主动断开"""
    if not check_auth(request):
        return jsonify({"error": "unauthorized"}), 403

    data = request.get_json()
    device_id = data.get("device_id", "")
    if not device_id:
        return jsonify({"error": "device_id required"}), 400

    with devices_lock:
        if device_id in devices:
            del devices[device_id]
            logger.info(f"Disconnected: {device_id}")

    with msg_queues_lock:
        if device_id in msg_queues:
            del msg_queues[device_id]

    with flow_control_lock:
        if device_id in flow_control:
            del flow_control[device_id]

    with transfer_paused_lock:
        if device_id in transfer_paused:
            del transfer_paused[device_id]

    return jsonify({"status": "ok"})


# ==================== 消息发送（save.md 标准协议） ====================

@app.route("/api/send", methods=["POST"])
def send_message():
    """发送消息到 PAW 服务器（双向）"""
    if not check_auth(request):
        return jsonify({"error": "unauthorized"}), 403

    data = request.get_json()
    if not data:
        return jsonify({"error": "missing json body"}), 400

    activate = data.get("activate", "")
    if activate != "send":
        return jsonify({"error": "activate must be 'send'"}), 400

    message = data.get("data", {})
    if not message:
        return jsonify({"error": "missing data"}), 400

    # 获取发送者和目标设备 ID
    sender_id = data.get("sender_id", "") or get_sender_device_id(request)
    target_id = data.get("target_id", "")

    if not sender_id or not target_id:
        # 尝试从设备类型推断
        with devices_lock:
            sender_info = devices.get(sender_id)
            if sender_info and sender_info["type"] == "pc":
                # PC 发送 → 找配对的手机
                target_id = None
                for dev_id, info in devices.items():
                    if info["type"] == "phone":
                        target_id = dev_id
                        break
            elif sender_info and sender_info["type"] == "phone":
                # 手机发送 → 找配对的 PC
                target_id = None
                for dev_id, info in devices.items():
                    if info["type"] == "pc":
                        target_id = dev_id
                        break

    if not target_id:
        return jsonify({"error": "target_id required"}), 400

    # 自动填充元数据
    message.setdefault("sender_id", sender_id)
    message.setdefault("timestamp", int(time.time() * 1000))

    with msg_queues_lock:
        if target_id not in msg_queues:
            msg_queues[target_id] = []
        msg_queues[target_id].append(data)

        if len(msg_queues[target_id]) > MSG_QUEUE_MAX:
            msg_queues[target_id] = msg_queues[target_id][-MSG_QUEUE_MAX:]

    logger.info(f"Message queued: {sender_id} -> {target_id} (action={message.get('action', '?')})")
    return jsonify({"status": "ok"})


@app.route("/api/get_cmd", methods=["GET"])
def get_cmd():
    """PC 端长轮询获取指令（save.md 标准端点）"""
    if not check_auth(request):
        return jsonify({"error": "unauthorized"}), 403

    device_id = request.args.get("device_id", "")
    if not device_id:
        return jsonify({"error": "device_id required"}), 400

    with devices_lock:
        if device_id not in devices:
            return jsonify({"error": "device not registered"}), 404

    def generate():
        while True:
            with msg_queues_lock:
                if device_id in msg_queues and msg_queues[device_id]:
                    msg = msg_queues[device_id].pop(0)
                    yield f"data: {json.dumps(msg, ensure_ascii=False)}\n\n"
                    if msg_queues[device_id]:
                        for m in msg_queues[device_id]:
                            yield f"data: {json.dumps(m, ensure_ascii=False)}\n\n"
                        msg_queues[device_id] = []
                    break

            yield ": heartbeat\n\n"
            time.sleep(1)

    return Response(stream_with_context(generate()), mimetype="text/event-stream")


@app.route("/api/get_msg", methods=["GET"])
def get_msg():
    """手机端长轮询获取消息（save.md 标准端点）"""
    if not check_auth(request):
        return jsonify({"error": "unauthorized"}), 403

    device_id = request.args.get("device_id", "")
    if not device_id:
        return jsonify({"error": "device_id required"}), 400

    with devices_lock:
        if device_id not in devices:
            return jsonify({"error": "device not registered"}), 404

    def generate():
        while True:
            with msg_queues_lock:
                if device_id in msg_queues and msg_queues[device_id]:
                    msg = msg_queues[device_id].pop(0)
                    yield f"data: {json.dumps(msg, ensure_ascii=False)}\n\n"
                    if msg_queues[device_id]:
                        for m in msg_queues[device_id]:
                            yield f"data: {json.dumps(m, ensure_ascii=False)}\n\n"
                        msg_queues[device_id] = []
                    break

            yield ": heartbeat\n\n"
            time.sleep(1)

    return Response(stream_with_context(generate()), mimetype="text/event-stream")


# ==================== 文件块传输 ====================

@app.route("/api/upload_chunk", methods=["POST"])
def upload_chunk():
    """上传文件块（手机端→PAW→PC 下载）"""
    if not check_auth(request):
        return jsonify({"error": "unauthorized"}), 403

    file_id = request.args.get("file_id", "")
    part_num = int(request.args.get("part_num", 0))
    sender_id = get_sender_device_id(request)

    if not file_id or not sender_id:
        return jsonify({"error": "file_id and device_id required"}), 400

    # 流量控制检查
    with flow_control_lock:
        total = flow_control.get(sender_id, 0)
        if total >= FLOW_CONTROL_HIGH:
            return jsonify({"error": "flow_control_exceeded", "current": total}), 429

    # 保存文件块
    with file_blocks_lock:
        if file_id not in file_blocks:
            file_blocks[file_id] = {}

        block_path = os.path.join(BLOCKS_DIR, f"{file_id}_part_{part_num}")
        with open(block_path, "wb") as f:
            while True:
                chunk = request.stream.read(8192)
                if not chunk:
                    break
                f.write(chunk)

        block_size = os.path.getsize(block_path)
        file_blocks[file_id][part_num] = {
            "path": block_path,
            "size": block_size,
            "timestamp": time.time(),
        }

    # 更新流量控制计数器
    with flow_control_lock:
        flow_control[sender_id] = flow_control.get(sender_id, 0) + block_size

    return jsonify({"status": "ok", "part_num": part_num, "size": block_size})


@app.route("/api/download_chunk/<file_id>/<int:part_num>", methods=["GET"])
def download_chunk(file_id, part_num):
    """下载文件块（PC 端从 PAW 下载）"""
    if not check_auth(request):
        return jsonify({"error": "unauthorized"}), 403

    with file_blocks_lock:
        if file_id not in file_blocks or part_num not in file_blocks[file_id]:
            return jsonify({"error": "block not found"}), 404

        block_info = file_blocks[file_id][part_num]
        block_path = block_info["path"]

    if not os.path.exists(block_path):
        return jsonify({"error": "block file missing"}), 404

    return send_file_static(block_path, f"{file_id}_part_{part_num}")


@app.route("/api/delete", methods=["POST"])
def delete_block():
    """删除文件块（用户取消传输时调用）"""
    if not check_auth(request):
        return jsonify({"error": "unauthorized"}), 403

    data = request.get_json()
    file_id = data.get("file_id", "")
    part_num = data.get("part_num")

    if not file_id:
        return jsonify({"error": "file_id required"}), 400

    deleted_size = 0

    with file_blocks_lock:
        if file_id in file_blocks:
            if part_num is not None:
                # 删除指定块
                if part_num in file_blocks[file_id]:
                    block_info = file_blocks[file_id][part_num]
                    if os.path.exists(block_info["path"]):
                        os.remove(block_info["path"])
                        deleted_size = block_info["size"]
                    del file_blocks[file_id][part_num]
            else:
                # 删除整个文件的所有块
                for pn, block_info in file_blocks[file_id].items():
                    if os.path.exists(block_info["path"]):
                        deleted_size += block_info["size"]
                        os.remove(block_info["path"])
                del file_blocks[file_id]

    # 更新流量控制计数器
    with flow_control_lock:
        sender_id = data.get("device_id", "")
        if sender_id and sender_id in flow_control:
            flow_control[sender_id] = max(0, flow_control[sender_id] - deleted_size)

    logger.info(f"Deleted block: file_id={file_id}, part_num={part_num}, size={deleted_size}")
    return jsonify({"status": "ok", "deleted_size": deleted_size})


def send_file_static(filepath, download_filename):
    """发送静态文件"""
    from flask import send_file as flask_send_file
    return flask_send_file(
        filepath,
        as_attachment=True,
        download_name=download_filename
    )


# ==================== 状态查询 ====================

@app.route("/api/status", methods=["GET"])
def get_status():
    """获取服务器状态"""
    if not check_auth(request):
        return jsonify({"error": "unauthorized"}), 403

    with devices_lock:
        online_devices = sum(
            1 for d in devices.values()
            if time.time() - d["last_heartbeat"] < HEARTBEAT_TIMEOUT
        )

    with file_blocks_lock:
        total_blocks = sum(len(blocks) for blocks in file_blocks.values())

    return jsonify({
        "status": "ok",
        "online_devices": online_devices,
        "total_registered": len(devices),
        "total_blocks": total_blocks,
        "server_time": int(time.time()),
    })


@app.route("/api/peers", methods=["GET"])
def get_peers():
    """获取当前在线设备列表"""
    if not check_auth(request):
        return jsonify({"error": "unauthorized"}), 403

    with devices_lock:
        peers = []
        for dev_id, info in devices.items():
            is_online = time.time() - info["last_heartbeat"] < HEARTBEAT_TIMEOUT
            peers.append({
                "device_id": dev_id,
                "type": info["type"],
                "online": is_online,
                "last_seen": info["last_heartbeat"],
            })

    return jsonify({"peers": peers})


# ==================== 定时清理 ====================

def cleanup_file_blocks():
    """清理超时的文件块（10分钟）"""
    while True:
        time.sleep(BLOCK_CLEANUP_INTERVAL)
        now = time.time()
        expired_ids = []

        with file_blocks_lock:
            for file_id, parts in file_blocks.items():
                for part_num, info in list(parts.items()):
                    if now - info["timestamp"] > BLOCK_CLEANUP_INTERVAL:
                        if os.path.exists(info["path"]):
                            os.remove(info["path"])
                        del parts[part_num]
                        logger.info(f"Expired block: {file_id}_part_{part_num}")
                if not parts:
                    expired_ids.append(file_id)

            for file_id in expired_ids:
                del file_blocks[file_id]


def cleanup_stale_devices():
    """定期清理超时的设备"""
    while True:
        time.sleep(DEVICE_CLEANUP_INTERVAL)
        now = time.time()
        stale_ids = []

        with devices_lock:
            for dev_id, info in devices.items():
                if now - info["last_heartbeat"] > HEARTBEAT_TIMEOUT:
                    stale_ids.append(dev_id)

        for dev_id in stale_ids:
            with devices_lock:
                if dev_id in devices:
                    del devices[dev_id]
                    logger.info(f"Cleaned up stale device: {dev_id}")

            with msg_queues_lock:
                if dev_id in msg_queues:
                    del msg_queues[dev_id]

            with flow_control_lock:
                if dev_id in flow_control:
                    del flow_control[dev_id]

            with transfer_paused_lock:
                if dev_id in transfer_paused:
                    del transfer_paused[dev_id]


# ==================== 启动 ====================

if __name__ == "__main__":
    # 启动清理线程
    threading.Thread(target=cleanup_file_blocks, daemon=True).start()
    threading.Thread(target=cleanup_stale_devices, daemon=True).start()

    logger.info(f"Starting PhoneHub PAW server on 0.0.0.0:5000")
    logger.info(f"SECRET_TOKEN: {'*' * 8}{SECRET_TOKEN[-4:]}")

    app.run(host="0.0.0.0", port=5000, debug=False)
