# PhoneHub 问题修复计划

## 概述
按照用户要求，逐一修复 13 个问题。每个问题修复后标记完成，最后一个问题修复后仅验证电脑端 .py 文件语法正确性。手机端打包和验证由用户自行完成。

## 当前状态分析

### 已知代码现状
- **手机端 ConnectionManager.kt**: CPU 使用 dumpsys→proc/stat→top 优先级；消息轮询 200ms；文件传输有 10 次重试限制；剪贴板用 OnPrimaryClipChangedListener 监听；`screenshot_saved` 处理器为空
- **电脑端 connection_manager.py**: CPU 轮询 1s；文件传输有 30s watchdog；剪贴板 3s 请求间隔；媒体信息用 PowerShell 子进程；键盘用 keybd_event；PC 推流 30fps；音频用 pyaudio
- **电脑端页面**: screen_mirror.py 30fps 显示；camera.py 4:3 比例；file_manager.py 上级按钮用字符串分割；apk_install.py 支持 WiFi 模式；push_web.py 有历史同步

---

## 问题 1: CPU 占用始终为 0

**根因**: 手机端 `getPhoneCpuUsage()` 已改为 dumpsys 优先，但可能仍有问题。需验证 dumpsys 解析逻辑。

**修复步骤**:
1. 文件: `app/src/main/java/com/phonehub/ConnectionManager.kt`
2. 检查 `getCpuFromDumpsys()` (L1334) 的解析逻辑：
   - 确认搜索 `XX% TOTAL` 行的逻辑正确
   - 添加更详细的日志输出实际 dumpsys 内容
3. 检查 `sendStatusReport()` (L1237) 是否正确调用 `getPhoneCpuUsage()` 并放入 `cpu` 字段发送
4. 确认状态报告循环 `startStatusReportLoop()` (L1217) 的 5s 间隔正常工作

---

## 问题 2: 文件互传失败

**根因**: 
- PC→Phone: PC 发送文件后手机端无反应，PC 立即显示"已取消" — 可能是 `file_transfer_active` 状态管理问题或手机端未收到 `send_file_head`
- Phone→PC: 手机发送文件后手机端无变化，PC 显示"正在接收"但进度始终 0 — 可能是 chunk 上传路由或状态阻塞问题

**修复步骤**:
1. 文件: `desktop/connection_manager.py`
   - `send_file()` (L1032): 确认先检查通道条件再设置 `file_transfer_active=True`
   - `_send_file_wifi()` (L1088): 确认 except 块中重置 `file_transfer_active=False`
   - `_file_transfer_watchdog()` (L1078): 确认 30s 超时强制重置
   - `_write_file_chunk()` (L894): 确认放宽条件，只要 file_id 匹配就写入
   - `/api/upload_chunk` 路由 (L400): 确认正确接收 chunk 数据
   - `_complete_file_receive()` (L912): 确认完成后发送 ack 并重置状态

2. 文件: `app/src/main/java/com/phonehub/ConnectionManager.kt`
   - `startReceiveFile()` (L1910): 确认 10 次重试限制逻辑正确
   - `downloadChunk()` (L1968): 确认 HTTP 请求正确，支持 ADB 和 WiFi
   - 确认 `sendFile()` 方法正确发送 `send_file_head` 并上传 chunks

---

## 问题 3: 剪贴板同步失败

**根因**:
- 手机端在其他软件复制后无法自动发送给 PC — 可能是 `OnPrimaryClipChangedListener` 未正确触发或发送逻辑有问题
- PC 端旧的复制内容覆盖手机端和 PC 端新的正确内容 — 可能是时间戳对比逻辑或 `_suppress_clipboard` 标志问题

**修复步骤**:
1. 文件: `app/src/main/java/com/phonehub/ConnectionManager.kt`
   - `onClipboardChanged()` (L1488): 确认 dedup 逻辑 `if (text == lastClipboardContent) return`
   - 确认 `OnPrimaryClipChangedListener` 正确注册 (L1461)
   - 确认剪贴板变化时主动发送给 PC，而不是等 PC 请求

2. 文件: `desktop/connection_manager.py`
   - `/api/cmd` 剪贴板处理 (L271-287): 确认时间戳对比 `remote_ts >= self.last_local_clipboard_time`
   - `_clipboard_monitor()` (L736): 确认 `_suppress_clipboard` 标志仅在内容不同时设置
   - 剪贴板请求间隔 (L763): 确认 3s 间隔

---

## 问题 4: 文字传输慢 + 移除拖拽保存按钮

**根因**: 手机端收到文字后卡很久才提示；电脑端有拖拽保存按钮需移除。

**修复步骤**:
1. 文件: `app/src/main/java/com/phonehub/ConnectionManager.kt`
   - 消息轮询 `startMsgPolling()` (L611): 确认 `delay(200)` 间隔（已经是 200ms，比 1s 快）
   - 如果用户坚持 1s，改为 `delay(1000)`（但 200ms 更优，建议保留并解释）
   - 确认 `handlePcMessage()` 正确处理文字消息并发出 SharedFlow 事件

2. 文件: `desktop/pages/text_share.py`
   - 确认已移除 `drag_recv_btn` 按钮和相关 `_on_drag_btn_press` 方法（根据上下文已移除）

---

## 问题 5: 手机截图无提示 + 查询间隔 1s

**根因**: 电脑端远程控制里点击手机截图后，手机端要卡很久才提示。`screenshot_saved` 处理器为空。

**修复步骤**:
1. 文件: `app/src/main/java/com/phonehub/ConnectionManager.kt`
   - 在 SharedFlow 声明区 (约 L145) 添加 `_screenshotSavedEvent = MutableSharedFlow<String>(extraBufferCapacity = 4)`
   - 修改 `screenshot_saved` 处理器 (L840): 发出 `_screenshotSavedEvent.emit(path)`
   - 添加 Toast 提示"截图已保存"

2. 文件: `app/src/main/java/com/phonehub/MainActivity.kt`
   - 添加 `_screenshotSavedEvent` 收集器：显示 Toast 并刷新截图列表

3. 查询间隔：如果有截图相关的轮询，改为 1s（当前无轮询，截图是事件驱动）

---

## 问题 6: 媒体控制获取信息 + 修饰键

**根因**: 
- 媒体信息获取用 PowerShell 子进程，不稳定且无法获取封面
- 修饰键用 `keybd_event` 和通用 VK 代码，可能不生效

**修复步骤**:
1. 文件: `desktop/connection_manager.py`
   - `_send_media_info()` (L1404): 改用 `winsdk` Python 库（用户提供的参考代码）
   - 添加 `winsdk` 导入：`import winsdk.windows.media.control as wmc` 和 `from winsdk.windows.storage.streams import DataReader`
   - 实现异步获取：标题、艺术家、封面 (thumbnail bytes)
   - 封面以 base64 编码发送给手机
   - 刷新间隔改为 5s

2. 文件: `desktop/connection_manager.py`
   - `_send_keys()` (L1369): 改用 `ctypes.windll.user32.SendInput` 替代 `keybd_event`
   - 修饰键改用左右特定 VK 代码：`VK_LCONTROL=0xA2, VK_LSHIFT=0xA0, VK_LMENU=0xA4`
   - `_send_media_key()` (L1358): 同样改用 SendInput

---

## 问题 7: 投屏/远程控制/音频/60fps

**根因**:
- 投屏卡顿、黑屏、PC 端点击闪退
- 音频无法传输
- 鼠标光标一闪一闪
- 需要 60fps PC→手机推流

**修复步骤**:
1. 文件: `desktop/connection_manager.py`
   - `_pc_stream_loop()` (L1500): 将 `time.sleep(0.033)` 改为 `time.sleep(0.016)` (60fps)
   - 确认 `start_pc_stream()` 正确启动线程
   - `/api/frame` 路由 (L426): 确认返回最新帧
   - `_perform_pc_click()` (L1712): 检查 SendInput 调用是否有异常导致闪退，添加 try-except 保护
   - `_pc_audio_loop()` (L1642): 检查音频捕获逻辑，确认设备查找正确
   - 鼠标光标：在投屏窗口激活时隐藏系统光标，使用 `setCursor(Qt.BlankCursor)` 或自定义透明光标

2. 文件: `desktop/pages/screen_mirror.py`
   - `MirrorCanvas` (L51): 将光标改为 `Qt.BlankCursor` 或仅在悬停时显示
   - `MirrorWindow._display_timer` (L172): 确认 33ms 间隔（30fps 显示足够）
   - 添加异常保护防止闪退

3. 文件: `app/src/main/java/com/phonehub/ConnectionManager.kt`
   - `startPcFramePolling()` (L2147): 将 `delay(33)` 改为 `delay(16)` (60fps)
   - `startPcAudioPolling()` (L2203): 检查音频播放逻辑

---

## 问题 8: 摄像头比例 + 切换按钮

**根因**: 手机摄像头画面和电脑端比例不一样；切换摄像头按钮无效果。

**修复步骤**:
1. 文件: `desktop/pages/camera.py`
   - `CameraWindow` (L98): 确认 `_aspect_ratio` 从帧尺寸更新（L162-165）
   - `_switch_camera()` (L274): 确认发送 `camera_switch` action 到手机

2. 文件: `app/src/main/java/com/phonehub/ConnectionManager.kt`
   - `camera_switch` 处理器 (L843): 确认发出 `_cameraSwitchRequest.emit(Unit)`
   - 检查手机端 CameraActivity 是否正确切换前后摄

3. 检查手机端摄像头预览的 aspect ratio 设置，确保与 PC 端一致（16:9 或 4:3）

---

## 问题 9: 通知控制无效

**根因**: 电脑端远程操控手机通知时，手机端无效果（如点击暂停不暂停），弹窗前卡了一阵。

**修复步骤**:
1. 文件: `app/src/main/java/com/phonehub/ConnectionManager.kt`
   - `notification_action` 处理器 (L1104): 检查通知查找逻辑
   - 确认 `_notifications.replayCache` 中能找到匹配的通知
   - 确认 `action.actionIntent.send()` 正确执行
   - 添加日志输出查找和执行结果

2. 文件: `app/src/main/java/com/phonehub/NotificationListener.kt`
   - 确认通知缓存格式正确：package + action_title 能匹配
   - 确认 `NotificationListener.instance` 不为 null

3. 文件: `desktop/pages/notifications.py`
   - `_exec_notif_action()` (L459): 确认发送的 `action_title` 与手机端缓存一致

---

## 问题 10: 文件管理上级/下载/查看

**根因**: 
- 手机端查看电脑文件时"上级"按钮跳到最上级
- 双端无法下载对方文件
- APK 安装里的文件也无法发送
- 手机端下载应保存到 /Download/
- 电脑端无法查看手机端文件

**修复步骤**:
1. 文件: `desktop/pages/file_manager.py`
   - `_go_up()` (L292): 检查路径导航逻辑，确认正确计算父目录
   - `_download_selected()` (L368): 
     - ADB 模式: 确认 `adb_pull` 返回值检查
     - WiFi 模式: 确认 `send_file_request` 正确发送
   - `_refresh()` (L207): 确认 ADB 和 WiFi 模式都能正确获取文件列表

2. 文件: `app/src/main/java/com/phonehub/MainActivity.kt` (手机端文件管理 UI)
   - 查找手机端浏览 PC 文件的"上级"按钮逻辑
   - 修复路径导航：正确计算父目录而非跳到根目录
   - 确认下载的文件保存到 `/sdcard/Download/`

3. 文件: `app/src/main/java/com/phonehub/ConnectionManager.kt`
   - `handleFileListRequest()` (L2472): 确认正确返回文件列表
   - `handleSendFileRequest()` (L2535): 确认正确发送文件

4. 文件: `desktop/connection_manager.py`
   - `/api/pc_files` 路由 (L522): 确认正确返回 PC 文件列表
   - `/api/pc_file_download` 路由 (L557): 确认正确处理文件下载

---

## 问题 11: APK 安装禁用 WiFi 模式

**根因**: WiFi 模式下两个按钮都无法使用，用户要求禁止 WiFi 端执行，只允许 ADB。

**修复步骤**:
1. 文件: `desktop/pages/apk_install.py`
   - `_update_button_states()` (L158): WiFi 模式下禁用 `select_btn`，仅 ADB 模式启用
   - `_install_apk()` (L194): WiFi 模式直接返回提示"仅支持 ADB 模式安装"
   - 修改通道标签：WiFi 模式显示"仅支持 ADB 模式安装"
   - 拖拽事件 `dropEvent()` (L182): WiFi 模式下拒绝并提示

---

## 问题 12: 网页历史同步

**根因**: 
- 历史应同步所有电脑↔手机的发送记录
- 电脑端网页历史里有文字传输的历史（不应有）
- 手机端无法接收电脑端网页推送，或电脑无法接收

**修复步骤**:
1. 文件: `desktop/pages/push_web.py`
   - `_on_command_received()` (L241): 确认仅处理 `open_url` action，过滤掉文字传输（`txt`/`text`/`input_text`）
   - `_send_history_sync()` (L258): 确认发送完整历史到手机
   - `_on_url_history_sync()` (L276): 确认正确合并远端历史

2. 文件: `app/src/main/java/com/phonehub/ConnectionManager.kt`
   - `open_url` 处理器 (L795): 确认发出 `_receivedUrl.emit(url)` 用于 UI 记录
   - `url_history_sync` 处理器 (L804): 确认正确接收并发出 `_urlHistorySync.emit(historyList)`
   - 确认手机端发送 URL 时同时记录到本地历史

3. 文件: `app/src/main/java/com/phonehub/MainActivity.kt`
   - 确认手机端网页历史页面正确收集 `_receivedUrl` 和 `_urlHistorySync` 事件
   - 确认手机端发送 URL 时记录到本地历史并发送给 PC

---

## 问题 13: 最终验证

**修复步骤**:
1. 对所有修改过的 .py 文件执行语法检查：
   ```
   python -m py_compile desktop/connection_manager.py
   python -m py_compile desktop/pages/text_share.py
   python -m py_compile desktop/pages/screen_mirror.py
   python -m py_compile desktop/pages/camera.py
   python -m py_compile desktop/pages/file_manager.py
   python -m py_compile desktop/pages/notifications.py
   python -m py_compile desktop/pages/apk_install.py
   python -m py_compile desktop/pages/push_web.py
   python -m py_compile desktop/pages/remote_control.py
   ```
2. 修复发现的任何语法错误
3. 手机端打包和验证由用户自行完成

---

## 假设与决策

1. **消息轮询间隔**: 当前 200ms 已经比用户要求的 1s 快，建议保留 200ms 并向用户解释。如果用户坚持，改为 1s。
2. **winsdk 依赖**: 假设用户已安装或愿意安装 `winsdk` Python 包。如未安装，需 `pip install winsdk`。
3. **60fps 推流**: 将 PC→手机推流改为 60fps，可能增加 CPU 和带宽占用。如性能不足，可降回 30fps。
4. **手机端代码**: 部分问题（如文件管理上级按钮、摄像头比例）需要修改手机端 Kotlin 代码，用户会自行打包验证。
5. **顺序修复**: 严格按问题 1→13 顺序修复，每个问题修复后标记完成。

## 验证步骤

1. 每个问题修复后，检查相关代码逻辑是否正确
2. 问题 13 执行 Python 语法检查
3. 手机端打包验证由用户完成
