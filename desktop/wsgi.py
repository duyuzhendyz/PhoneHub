# -*- coding: utf-8 -*-
"""PhoneHub PAW 中继服务器 — PythonAnywhere WSGI 入口

部署步骤：
1. 将本文件与 paw_relay_server.py、paw_requirements.txt 放到同一目录，
   例如 /home/<你的PythonAnywhere用户名>/phonehub_paw/
2. 在该目录的 virtualenv 里安装依赖：
       pip install -r paw_requirements.txt
3. 在 PythonAnywhere Web 标签页：
   - 编辑 WSGI configuration file，内容指向本文件
     （或在默认 wsgi.py 中把内容替换为下面几行 import）
   - 确认入口文件的绝对路径正确
4. Reload（重启）Web App 生效。

注意：长轮询端点 /api/get_cmd、/api/get_msg 会在 50 秒后自动断开，
由客户端重连，避免单个 SSE 连接永久占用 PythonAnywhere 进程。
后台清理线程由本入口启动（幂等），无需手工处理。
"""
import os
import sys

# 把本脚本所在目录加入 sys.path，保证能 import 到 paw_relay_server.py
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from paw_relay_server import app as application  # noqa: E402
from paw_relay_server import start_background_threads  # noqa: E402

# PythonAnywhere 依赖一个名为 application 的对象；start_background_threads 幂等
start_background_threads()