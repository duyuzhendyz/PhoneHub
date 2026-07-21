# PhoneHub 问题修复计划（问题 4-12）

## 概述
按用户要求，从头开始顺序修复问题 4-12，每个问题修复后立即进入下一个，不回头检查。最终只验证电脑端 .py 文件语法，手机端打包/验证由用户自行完成。

---

## 问题 4：文字传输慢 + 移除拖拽按钮

### 现状分析
- 手机端 `startMsgPolling()` (ConnectionManager.kt L641) 轮询间隔为 `delay(200)` = 200ms，已经比用户要求的 1s 快。
- `handlePcMessage` 的 `"txt"` 分支 (L696-708) 正确发射 SharedFlow 事件。
- PC 端 `text_share.py` 中 **不存在** `drag_recv_btn` 或 `_on_drag_btn_press`，拖拽按钮已被移除。

### 修复方案
- 保留 200ms 轮询（比 1s 更优，向用户说明）。
- 无需代码修改。如果用户坚持 1s，改 `delay(200)` → `delay(1000)`。

### 验证
- 无需语法验证（无修改）。

---

## 问题 5：截图无手机端提示

### 现状分析
- ADB 模式：PC 截图后发 `screenshot_saved` action，但手机端 ConnectionManager.kt L837-839 **静默处理**，不弹 Toast。
- `performBackgroundScreenshot()` (L2668-2740) 截图成功后保存相册+发文件，**无 SharedFlow 事件**。
- `requestPcScreenshot()` (L1633-1641) 请求 PC 截图，PC 端 `_take_screenshot_and_send()` (connection_manager.py L1454-1462) **只发文件不发成功 action**。

### 修复方案
**手机端 ConnectionManager.kt：**
1. 新增 `_screenshotResult: MutableSharedFlow<String>` (成功/失败消息)
2. `screenshot_saved` handler (L837-839)：改为发射 `_screenshotResult.emit("截图已保存到电脑")`
3. `performBackgroundScreenshot()` 成功后发射 `_screenshotResult.emit("截图已保存到手机相册")`，失败发射错误信息
4. `requestPcScreenshot()` 后等待文件接收完成时发射提示

**PC 端 connection_manager.py：**
5. `_take_screenshot_and_send()` (L1454-1462)：截图后除发文件外，额外发 `screenshot_saved` action 通知手机

**手机端 MainActivity.kt：**
6. 在截图按钮点击处收集 `_screenshotResult` 并显示 Toast

### 验证
- `python -m py_compile connection_manager.py`

---

## 问题 6：媒体控制 - 封面/信息获取 + 修饰键无效

### 现状分析
- PC 端 `_send_media_info()` (connection_manager.py L1405-1452) 用 PowerShell 调 WinRT，**只取 title/artist/album，不取 thumbnail**。
- 手机端 `_mediaInfo` 是 `StateFlow<String>` (L155-156)，只存文本无法承载图片。handler (L851-854) 忽略 album。
- `remote_control.py` 的 `_send_key()` (L84-85) 发 `"key"` 命令时 **不传 mods 字段**，修饰键丢失。
- 无媒体信息刷新定时器，手机端仅页面创建时请求一次。

### 修复方案
**PC 端 connection_manager.py：**
1. 改用用户提供的 `winsdk` Python 库重写 `_send_media_info()`：
   - `import winsdk.windows.media.control as wmc`
   - 获取 title/artist + **thumbnail 字节**
   - thumbnail base64 编码后随消息发送
2. 新增媒体键处理分支：`media_play_pause`(0xB3), `media_prev`(0xB1), `media_next`(0xB0) 走 `_send_media_key()`
3. 修饰键支持：`_send_keys()` 已支持，需确保路由正确

**手机端 ConnectionManager.kt：**
4. 新增 `_mediaThumbnail: MutableStateFlow<ByteArray?>` 存储封面
5. `media_info` handler 解析 `thumbnail` base64 字段
6. `sendMediaKey` 支持 media_play_pause/media_prev/media_next 命令

**手机端 MainActivity.kt：**
7. 媒体信息区域增加封面 ImageView，收集 `_mediaThumbnail`
8. 增加定时器每 5 秒请求 `get_media_info`

### 验证
- `python -m py_compile connection_manager.py`
- 确认 `winsdk` 库可用（`pip show winsdk`）

---

## 问题 7：投屏卡顿/黑屏/远程控制闪退/音频/鼠标闪烁/60fps

### 现状分析（基于预存分析）
- 手机投屏到 PC：帧率低导致卡顿
- PC 点击投屏界面远程控制时闪退：mouse 事件处理可能有异常
- 鼠标闪烁：可能 cursor shape 频繁切换
- 音频传输不工作：手机端采集或 PC 端播放链路断裂
- PC→手机推流要求 60fps

### 修复方案
**PC 端 screen_mirror.py：**
1. 修复鼠标点击闪退：try-catch 包裹鼠标事件处理，防止异常崩溃
2. 鼠标不闪烁：设置 `setCursor(Qt.BlankCursor)` 或固定 cursor
3. 投屏画面全屏保持原比例：`setAspectRatioMode(Qt.KeepAspectRatio)`

**PC 端 connection_manager.py：**
4. PC→手机推流帧率提升至 60fps（捕获间隔 ~16ms）
5. 手机音频接收后正确播放（检查 `_phone_audio_buffer` 处理）

**手机端 ConnectionManager.kt / MainActivity.kt：**
6. 手机投屏帧率提升（已是 30fps，按之前调整）
7. 音频采集：检查 MediaRecordr/AudioRecord 初始化和发送
8. 远程控制输入注入：确保 MotionEvent 正确注入

### 验证
- `python -m py_compile connection_manager.py pages/screen_mirror.py`

---

## 问题 8：摄像头比例 + 切换按钮无效

### 现状分析（基于预存分析）
- PC 端 camera.py 预览比例与手机不一致
- 切换摄像头按钮点击无响应

### 修复方案
**PC 端 camera.py：**
1. 预览 QLabel/pixmap 设置 `Qt.KeepAspectRatio` 保持比例
2. 切换按钮 handler：调用 `send_action("switch_camera")` 发给手机

**手机端 ConnectionManager.kt / MainActivity.kt：**
3. `switch_camera` action handler：切换 `CameraManager` 前后摄

### 验证
- `python -m py_compile pages/camera.py`

---

## 问题 9：通知控制无效（如暂停按钮）

### 现状分析（基于预存分析）
- PC 端 notifications.py 发送通知 action，手机端无效果
- 弹窗前有卡顿

### 修复方案
**PC 端 notifications.py / connection_manager.py：**
1. 确认通知 action 发送格式正确（key 格式：`notification_action`，body 含 `key`/`packageName`）

**手机端 NotificationListener.kt / ConnectionManager.kt：**
2. `notification_action` handler：正确解析 key，调用 `sbn.notification.actions[]` 执行
3. 处理 `instance` 为 null 的情况，提示用户开启通知权限

### 验证
- `python -m py_compile pages/notifications.py connection_manager.py`

---

## 问题 10：文件管理 - 上级按钮/下载/查看手机文件

### 现状分析（基于预存分析）
- 上级按钮从 `/a/b/` 跳到根目录 `/`，应跳到 `/a/`
- 双向下载失败
- PC 无法查看手机文件

### 修复方案
**PC 端 file_manager.py：**
1. 上级按钮：`os.path.dirname(current_path)` 而非跳根目录；处理已是根时禁用
2. 下载手机文件：通过 HTTP chunk 下载 (`/api/download_chunk`)
3. 查看手机文件列表：正确请求和显示

**PC 端 connection_manager.py：**
4. 文件列表请求路由正确处理
5. 下载 chunk 路由正确响应

**手机端 ConnectionManager.kt：**
6. 文件列表请求：正确遍历目录返回
7. 下载 chunk：正确读取文件分块发送

### 验证
- `python -m py_compile pages/file_manager.py connection_manager.py`

---

## 问题 11：APK 安装禁用 WiFi 模式

### 现状分析（基于预存分析）
- WiFi 模式下两个按钮都无法使用
- 用户要求 WiFi 禁用，仅 ADB 可用

### 修复方案
**PC 端 apk_install.py：**
1. 检测当前通道：若非 ADB，禁用发送/安装按钮并提示"仅 ADB 模式可用"
2. `_update_button_states()` 中根据 `manager.current_channel` 控制

### 验证
- `python -m py_compile pages/apk_install.py`

---

## 问题 12：URL 历史同步 + 文字历史混入 + 手机接收网页推送

### 现状分析（基于预存分析）
- 历史应包含 PC↔Phone 所有传输记录并同步
- PC 端网页历史中出现文字传输记录（分类错误）
- 手机无法接收 PC 网页推送

### 修复方案
**PC 端 push_web.py / connection_manager.py：**
1. URL 历史存储时正确分类：URL 推送 vs 文字传输分开
2. 发送 URL 推送时同步历史给手机

**手机端 ConnectionManager.kt / MainActivity.kt：**
3. `url_push` action handler：正确接收并打开/提示 URL
4. URL 历史同步接收并存储

### 验证
- `python -m py_compile pages/push_web.py connection_manager.py`

---

## 问题 13：最终 Python 语法验证

对所有修改的 .py 文件执行：
```
python -m py_compile desktop/connection_manager.py
python -m py_compile desktop/pages/text_share.py
python -m py_compile desktop/pages/remote_control.py
python -m py_compile desktop/pages/screen_mirror.py
python -m py_compile desktop/pages/camera.py
python -m py_compile desktop/pages/notifications.py
python -m py_compile desktop/pages/file_manager.py
python -m py_compile desktop/pages/apk_install.py
python -m py_compile desktop/pages/push_web.py
```

---

## 假设与决策
1. 手机端编译/打包由用户自行完成，助手只改代码
2. 修复顺序：4→5→6→7→8→9→10→11→12→13，不回头
3. 问题 4 无需代码修改（200ms 已优于 1s），向用户说明
4. 媒体封面使用 base64 编码传输（避免 HTTP multipart 复杂性）
5. winsdk 库需确认已安装，若未安装则保留 PowerShell 方案但增加 thumbnail 读取
6. 投屏 60fps 指的是 PC→手机推流方向

## 验证步骤
- 每个问题修复后立即进入下一个，不回头检查
- 最终统一执行 `python -m py_compile` 验证所有 .py 文件语法
- 手机端 .kt 文件由用户自行编译验证
