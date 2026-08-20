# PhoneHub 代码库完整知识文档

## 📁 项目结构概览

```
c:\PhoneHub\
├── app/                  # Android 手机端源码 (Kotlin)
│   └── src/main/java/com/phonehub/
├── desktop/             # 桌面端源码 (Python)
│   ├── pages/           # 14个功能页面组件
│   ├── connection_manager.py  # Flask后端核心
│   ├── main.py          # 入口点
│   └── main_window.py   # 主窗口
├── E.yaml               # 外部配置文件
└── old/                 # 临时归档的无用文件
```

---

## 📱 Android手机端 (Kotlin)

### 核心文件列表及功能

#### 1. `app/src/main/java/com/phonehub/connectionmanager.kt`
**核心作用：** 整个App的心脏，处理所有连接、通信和数据同步

- **单例对象模式：** `object ConnectionManager`，全局唯一实例
- **StateFlow响应式架构：** `_connectionState`, `_currentChannel`, `_phoneMemUsage`等
- **多通道支持：** ADB (优先级30)、WIFI (优先级20)、PAW (优先级10)、NONE (0)
- **文件传输接收路径：** `receiveDir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir, "PhoneHub")` → `/Android/data/<包名>/files/Downloads/PhoneHub/`
- **断点续传机制：** `fileReceiveState`, `ackTracker`, `ResumeInfo`等数据结构
- **通知监听上报：** `_notifications` SharedFlow，携带 NotificationItem 数据
- **剪贴板同步：** `_clipboardHistory`, `_clipboardFavorites`持久化到SharedPreferences
- **电脑推流帧接收：** `_pcFrame`, `_pcCursorPos` (功能7：电脑→手机推流)
- **电脑摄像头帧接收：** `_pcCameraFrame` (功能8：电脑摄像头→手机推流)
- **截屏Token缓存：** `cachedProjectionResultCode`, `cachedProjectionData`, `activeProjectionRef`
- **媒体信息推送：** `_mediaInfo`, `_mediaThumbnail`
- **URL历史同步：** `_urlHistorySync`
- **键盘/遥控器命令发送：** `_performScreenTouch`通过ADB或WiFi下发指令
- **云端文件上传辅助：** `sendFrameToPc()`, `sendAudioToPc()`
- **PC文件浏览器：** `fetchPcDrives()`, `fetchPcFiles()`获取电脑磁盘和文件列表
- **clipboard历史同步方法：** `sendClipboardHistoryToPc()`, `searchClipboardHistory()`, `searchClipboardFavorites()`
- **剪贴板收藏同步：** `toggleFavorite()`, `applySyncedFavorite()`
- **IP缓存函数：** `cacheIp()`, `getCachedIp()`
- **底层发送方法：** `sendRaw()`根据当前通道(ADB/WiFi/PAW)选择发送目标
- **通知监听管理：** `isNotificationListenerEnabled()`, `requestNotificationListenerRebind()`, `openNotificationSettings()`
- **初始化方法：** `init(ctx: Context)`设置HttpClient、创建接收目录、自动连接缓存IP
- **断开连接：** `disconnect()`取消所有Job、清空状态

#### 2. `app/src/main/java/com/phonehub/screenshotactivity.kt`
**作用：** 屏幕截图授权界面

- 使用 `MediaProjectionManager.createScreenCaptureIntent()` 请求截屏权限
- 成功后通过 `ImageReader` 捕获一帧画面
- 保存到相册 `Environment.DIRECTORY_PICTURES + "/Computer/"`
- 保存为临时文件并通过 `ConnectionManager.sendFile()` 发送给PC
- 静默截图后不弹Toast，直接finish

#### 3. `app/src/main/java/com/phonehub/screencaptureservice.kt`
**作用：** 屏幕截图前台服务（Android 14+强制要求）

- `companion object instance`单例，`isRunning`标志
- `startProjection(resultCode, data, MediaProjection)`初始化投影实例
- `getProjection()`供ConnectionManager复用已有的MediaProjection实例
- `stopProjection()`清理资源
- 前台服务类型：`mediaProjection` (API 29+) 或普通前台通知
- 与 `SharedNotificationHelper` 共用通知渠道

#### 4. `app/src/main/java/com/phonehub/phonehubservice.kt`
**作用：** 保活守护服务

- `START_STICKy`：系统内存紧张被杀后自动重启
- **看门狗** (`WATCHDOG_INTERVAL_SEC=15`)：每15秒自检instance是否null
- `onTaskRemoved()`：用户清理后台时三路并行启动：
  1. `scheduleRestart(this, 3_000)` - AlarmManager 3秒后重启
  2. `scheduleJobRestart()` - JobScheduler 5-15秒后重启
  3. `start(this)` - 直接拉起服务
  4. `scheduleRestart(this, 20_000)` - 20秒后兜底重启
- `onDestroy()`：同样触发Alarm和JobScheduler重启
- `WakeLock`防止CPU休眠
- `NotificationHelper`创建低重要性不可滑动移除的通知

#### 5. `app/src/main/java/com/phonehub/notificationlistener.kt`
**作用：** 通知监听服务，获取手机所有应用的通知

- `NotificationListenerService`继承
- `instance`单例，`pollJob`每秒轮检一次通知变化
- **黑名单过滤：** `notification_blacklist` SharedPreferences存储，空则全部转发
- **首次连接时上报：** `onListenerConnected()`获取所有活动通知并上报
- **差量上报机制：** `checkAndReportChanges()`比较lastNotifications集合，仅上报变化的通知
- **通知持久化：** `persistNotification()`写入 `NotificationCache/` JSON文件（保留7天）
- **通知操作按钮提取：** `n.actions`遍历Notification.Action转为ConnectionManager.NotificationAction格式
- **删除通知：** `cancelNotificationByKey()`通过key或直接调用cancel(tag,id)删除指定通知
- `reportAllActiveNotifications()`主动上报所有当前活动通知

#### 6. `app/src/main/java/com/phonehub/phonehubaccessibilityservice.kt`
**作用：** 无障碍服务，提供按键模拟能力

- 通过 `AccessibilityService` 获取windowInfo获取屏幕尺寸
- `connect()`建立连接，`disconnect()`断开
- `performClick(x, y)`, `performSwipe(x1,y1,x2,y2,duration)`, `performBack()`, `performHome()`, `performRecents()`等触摸操作
- `sendKeyEvent(keyCode)`发送虚拟键码（需系统权限）
- `toggleKeyboardFullscreen()`控制全屏键盘的显示隐藏
- `extractWindowInfo()`从当前焦点窗口提取信息用于坐标映射

#### 7. `app/src/main/java/com/phonehub/mainactivity.kt`
**作用：** 主Activity，UI入口

- **全屏键盘模式：** `enterKeyboardFullscreen()` / `exitKeyboardFullscreen()`切换横屏全屏视图
- **全键盘绑定函数：** `wireFullKeyboard(rootView: View)`第1161-119行，将按键ID映射到key值
  - **关键代码行1195：** `rootView.findViewById<Button>(id)?.setOnClickListener { sendKey(key) }`
  - 构建 `keyMap` 包含所有字母数字标点方向键功能键
  - 构建 `modMap` 修饰键（shift/ctrl/alt/win）点击切换锁定态
  - `sendKey(key(key))`调用 `ConnectionManager.sendMediaCommand("key_$modStr$key")`
- **截屏Launcher：** `screenCaptureLauncher` ActivityResultContracts启动截图授权
- **CameraX预览：** `cameraProvider`, `cameraInstance`, `cameraPreviewView`实时摄像头预览
- **投屏相关：** `mediaProjection`, `virtualDisplay`, `screenCaptureThread`, `startScreenCaptureLoop()`
- **通知拦截：** `notifPermissionLauncher`请求通知权限
- **URL历史记录：** `UrlHistoryItem`结构，push_url_history持久化去重保存最多50条
- **文字消息去重：** `handledTextContents`缓存最近5秒内的相同内容避免重复弹窗
- **页面缓存：** `pageCache HashMap<Int, View>`记录已加载过的页面视图

#### 8. `app/src/main/java/com/phonehub/filetransferreceiver.kt`
**作用：** 文件传输广播接收器

- 响应 ACTION_FILE_TRANSFER_* 广播事件
- 启动/停止文件传输进程
- 处理文件冲突逻辑（覆盖/重命名/跳过）

#### 9. `app/src/main/java/com/phonehub/bootreceiver.kt`
**作用：** 开机广播接收器

- 监听 BOOT_COMPLETED 事件
- 启动 PhoneHubService 保活服务

#### 10. `app/src/main/java/com/phonehub/logutil.kt`
**作用：** 日志工具类

- 封装 Log.i/w/e/d/v 输出，带TAG前缀
- 不同模块独立的TAG：conn/scr/input等

#### 11. `app/src/main/java/com/phonehub/sharednotificationhelper.kt`
**作用：** 共享通知辅助类

- `createChannel()`创建通知渠道
- `buildNotification()`构建标准通知对象
- 供ScreenCaptureService、PhoneHubService等共享使用

#### 12. `app/src/main/java/com/phonehub/textnotificationreceiver.kt`
**作用：** 文本通知广播接收器

- 处理TEXT_SHARE动作，将文本内容推送到连接管理器

#### 13. `app/src/main/java/com/phonehub/locationservice.kt`
**作用：** 位置追踪服务

- 定时上报地理位置坐标给PC
- LocationPoint数据结构，latitude/longitude/timestamp/uploaded状态

#### 14. `app/src/main/java/com/phonehub/restartjobservice.kt`
**作用：** JobScheduler重启服务

- 配合PhoneHubService实现系统级的重启兜底机制

#### 15. `app/src/main/java/com/phonehub/restartservicereceiver.kt`
**作用：** 重启服务广播接收器

- 监听AlarmManager触发的广播，调用PhoneHubService.start()重启

---

## 💻 桌面端 (Python)

### 核心文件列表及功能

#### 1. `desktop/connection_manager.py`
**作用：** Flask HTTP后端（核心中的核心），运行在58627端口

**常数定义：**
- `DEFAULT_SECRET_TOKEN = "541881452418845"`
- `DEFAULT_PORT = 58627`
- `CHUNK_SIZE = 524288` (512KB)
- `receive_dir = r"F:\desk\手机上传"`（失败回退到 `~/PhoneHub/Received`）

**信号定义 (pyqtSignal)：**
- `connection_status_changed`, `cpu_usage_received`, `phone_status_received`
- `message_received`, `clipboard_received`, `text_received`, `clipboard_favorite_received`
- `file_transfer_progress`, `file_transfer_complete`, `file_receive_started`
- `file_transfer_cancelled`, `file_transfer_paused`
- `notification_received`, `location_received`, `power_action_received`
- `file_sent`, `screenshot_received`, `app_list_received`
- `phone_frame_received`, `camera_frame_received`, `phone_audio_received`
- `clipboard_history_received`, `url_history_sync_received`

**Flask路由 (@self.app.route)：**
- `/api/status` - 获取电脑状态（CPU/内存/磁盘/网络）
- `/api/poll` - 合并轮询：status + message queue
- `/api/cmd` (POST) - 接收手机命令，handle各种action：
  - clipboard/clipboard_set/clippboard_favorite
  - txt/text/input_text
  - send_file_head/file_send_head → _start_file_receive
  - transfer_control (pause/cancel/resume)
  - file_complete/file_send_complete
  - cpu/phone_status/status_report
  - notification/location/location_batch
  - screenshot_request/pc_screenshot → _take_screenshot_and_send
  - run_as_admin/power_command/media_play_pause/vol_up/vol_down/...
  - screen_touch/mirror_start/mirror_stop/pc_stream_start/pc_stream_stop
  - camera_switch/volume_changed/url_history_sync/clipboard_history
  
- `/api/upload_file` (POST) - 手机→PC文件上传（流式，支持断点续传）
- `/api/download_file/<file_id>` (GET) - PC→手机文件下载（支持Range头断点续传）
- `/api/upload_chunk/<file_id>/<part_num>` (POST) - 分块上传
- `/api/download_chunk/<file_id>/<part_num>` (GET) - 分块下载
- `/api/msg` (GET) - 获取消息队列中一条消息
- `/api/frame` (GET) - PC画面帧推流（返回JPEG，附带鼠标归一化坐标X-Cursor-X/Y）
- `/api/camera_frame` (GET) - 电脑摄像头帧推流（返回JPEG）
- `/api/phone_frame` (POST) - 手机投屏帧接收（带type参数区分mirror/camera）
- `/api/phone_audio` (POST) - 手机音频接收
- `/api/audio` (GET) - PC音频推流（供手机拉取PCM）
- `/api/pc_drives` (GET) - 获取电脑驱动器列表
- `/api/pc_files` (POST) - 列出电脑指定目录文件
- `/api/pc_file_download` (POST) - 手机端下载电脑文件

**文件收发核心方法：**
- `_start_file_receive(file_id, file_name, file_size)` - 开始接收文件，设置current_receive_file路径
- `_write_file_chunk(file_id, part_num, data)` - 写入分块（乱序到达支持seek定位）
- `_complete_file_verify(file_id)` - 完成校验，对比期望大小与实际大小
- `_read_file_chunk(file_id, part_num)` - 读取PC要发送的分块
- `send_file_accept(file_id, resolved_name)` - PC确认接受文件（发送给手机）
- `send_file_reject(file_id, reason)` - PC拒绝接受文件
- `send_transfer_control(ctrl, file_id)` - 发送暂停/继续/取消控制消息
- `send_file(file_path)` - PC主动发送文件给手机（通过wifi/adbs/paw通道）
- `cancel_transfer()` - 取消当前传输

**PC→手机推流 (功能7)：**
- `start_pc_stream()` / `stop_pc_stream()` - mss截屏循环，60fps，JPEG质量75%，最大宽度1280px
- `_pc_stream_loop()` - 核心循环，每次截取全屏后缩放编码为JPEG放入`_latest_frame`
- `_frame_lock`保护最新帧数据

**电脑摄像头推流 (功能8)：**
- `start_pc_camera()` / `stop_pc_camera()` - OpenCV采集，10fps，1280x720，JPEG质量70%
- `_pc_camera_loop()` - 摄像头循环，翻转画面后编码为JPEG放入`_latest_camera_frame`
- `_camera_lock`保护最新帧数据

**声音传输：**
- `start_phone_mirror()` / `stop_phone_mirror()` - 接收手机投屏帧
- `start_phone_audio()` / `stop_phone_audio()` - 接收手机音频（pyaudio播放）
- `start_pc_audio()` / `stop_pc_audio()` - 捕获电脑音频（WASAPI loopback或立体声混音）→ `_latest_pc_audio`
- `_pc_audio_loop()` - 查找loopback设备，以4096采样率捕获PCM数据
- `_play_audio_data(audio_data)` - pyaudio播放手机端传来的PCM

**远程控制命令：**
- `_perform_screen_touch(norm_x, norm_y, op)` - ADB通道用input tap/swipe/WiFi通道下发touch指令
- `_perform_pc_click(norm_x, norm_y, op)` - 通过ctypes SendInput直接在电脑上执行点击/拖拽/右键
- `_send_media_key(vk_code)` - 发送媒体键（播放/上一曲/音量等）
- `_send_keys(key_name)` - 发送组合键（如ctrl+shift+esc）
- `_get_media_info()` - 使用winsdk获取当前媒体播放信息（歌曲名/作者/封面）
- `_check_and_send_media_info()` - 定期推送媒体信息变化
- `_open_url_on_pc(url, use_edge)` - 在电脑打开URL（用Edge或默认浏览器）

**进程管理：**
- `_kill_process(pid)` - taskkill /PID /F
- `_set_pc_process_priority(pid, adjustment)` - psutil调整优先级

**电源管理：**
- `_handle_power_action(action_type)` - lock/sleep/shutdown/reboot/cancel shutdown

**媒体监测：**
- `_start_media_monitor()` / `_media_monitor_loop()` - 定期检查媒体播放状态变化
- `_delayed_check_media_info()` - 按键后延迟检查媒体信息

**其他：**
- `_take_screenshot_and_save_to_gallery()` - PC端截图并保存到receive_dir
- `_run_as_admin(program)` - 以管理员身份运行程序
- `_verify_server()` - socket自检Flask端口是否正常
- `_probe_channels()` - 线程检测各通道可用性，自动升降级

#### 2. `desktop/pages/screen_mirror.py`
**作用：** 投屏与远程控制页面

- **MirrorCanvas(QFrame)** - 画布类，显示手机投屏画面，支持点击/拖拽/长按操控
  - `_norm_coords(pos)` 画布坐标→归一化坐标(0-1)
  - `load_frame(frame_data)` 解码JPEG到复用的QPixmap
  - `mousePressEvent/mouseMoveEvent/mouseReleaseEvent` 处理触摸事件
  
- **MirrorWindow(QWidget)** - 独立投屏查看窗口
  - 60fps定时器刷新显示最新帧
  - 收到手机投屏帧时自动打开窗口
  - 关闭窗口时通知手机停止投屏采集

- **ScreenMirrorPage(QWidget)** - 投屏功能页面
  - `pc_stream_btn` / `phone_to_pc_btn` / `open_window_btn` / `audio_btn`
  - 快捷控制面板：音量滑块、锁屏/返回/主页/最近任务/通知栏/控制中心按钮
  - `phone_screenshot()` - ADB模式直接截图，WiFi模式发送截图请求
  - `_toggle_pc_stream()` / `_toggle_phone_mirror()` / `_toggle_audio()` 启停切换

#### 3. `desktop/pages/file_transfer.py`
**作用：** 文件传输页面

- **ConflictDialog(QDialog)** - 同名冲突弹窗：覆盖/重命名/跳过
  - `OVERWRITE`/`RENAME`/`SKIP`三个选项

- **FileTransferPage(QWidget)** - 文件传输界面
  - `RECEIVE_DIR = r"F:\desk\手机上传"` （与connection_manager.py一致）
  - `_speed_samples` 速度采样窗口，支持指数平滑算法
  - `send_file_btn` / `pause_btn` / `cancel_btn` / `done_btn` / `open_recv_btn`
  - `_select_file()` 选择文件发送
  - `_cancel_transfer()` 取消传输，删除未完成文件
  - `_toggle_pause()` 暂停/继续，通过transfer_control消息通知对端
  - `_on_remote_cancel()` / `_on_remote_paused()` 对端取消/暂停时的同步
  - `_on_progress()` 进度更新，带防抖和速度计算
  - `_resolve_conflict_async()` 异步弹出冲突对话框
  - `_accept_incoming_file()` 接受传入文件，发送file_accept
  - `_on_complete()` / `_on_receive_started()` / `_on_file_sent()` 完成信号回调
  - `_show_completion_ui()` 完成后显示done按钮让用户确认

#### 4. `desktop/pages/camera.py`
**作用：** 共享摄像头页面

- **CameraCanvas(QFrame)** - 摄像头画面画布（与MirrorCanvas类似但不同文件）
  - `load_frame()` / `set_pixmap()` / `clear()` / `paintEvent()`
  
- **CameraWindow(QWidget)** - 独立摄像头查看窗口
  - mode="phone" (手机摄像头) 或 mode="pc" (电脑摄像头本地预览)
  - 60fps定时器显示最新帧
  
- **CameraPage(QWidget)** - 摄像头功能页面
  - `switch_btn` 切换手机前后置摄像头
  - `pc_camera_btn` 电脑摄像头→手机推流
  - `view_phone_cam_btn` / `view_pc_cam_btn` 查看各自摄像头画面
  - `_toggle_pc_camera()` 启停电脑摄像头推流（依赖opencv-python）

#### 5. `desktop/pages/dashboard.py`
**作用：** 仪表盘页面（展示CPU/内存/网络等系统监控数据）

#### 6. `desktop/pages/app_manager.py`
**作用：** 应用管理页面（安装APK、卸载应用、查看应用列表）

#### 7. `desktop/pages/clipboard_sync.py`
**作用：** 剪贴板双向同步页面，显示剪贴板历史

#### 8. `desktop/pages/location_map.py`
**作用：** 地图位置页面，显示手机上报的地理坐标轨迹

#### 9. `desktop/pages/notifications.py`
**作用：** 通知管理页面（修复过 `_poll_active_notifications` 缺失方法错误）

#### 10. `desktop/pages/text_share.py`
**作用：** 文本分享页面，双向文本同步

#### 11. `desktop/pages/apk_install.py`
**作用：** APK安装页面

#### 12. `desktop/pages/push_web.py`
**作用：** Web推送页面

#### 13. `desktop/pages/settings.py`
**作用：** 设置页面，配置PC连接信息、Token、PAW服务器等

#### 14. `desktop/pages/__init__.py`
**作用：** 包初始化

#### 15. `desktop/paw_relay_server.py`
**作用：** PAW中转服务器代理（部分代码被注释保留，当前未启用）

#### 16. `desktop/styles.py`
**作用：** 样式辅助函数
- `_c()` - 主题颜色字典
- `get_theme()` - 获取当前主题对象
- `apply_dark_title_bar(window)` - 应用深色标题栏（Windows专用）
- `dark_dialog_style()` - 深色对话框样式
- `dark_msg_box()` - 深色消息框
- `set_item_text_color(item)` - 设置列表项文本颜色

#### 17. `desktop/main.py`
**作用：** 程序入口点

- QApplication初始化，设置字体和高DPI支持
- 创建ConnectionManager实例，启动Flask服务端线程
- 创建MainWindow实例，显示系统托盘图标
- **Qt消息警告过滤器：** 添加message handler抑制 `QWindowsFontEngine::GetTextMetrics failed` 警告
- 注册各个页面到导航界面

#### 18. `desktop/main_window.py`
**作用：** 主窗口界面（FluentWindow）

- qfluentwidgets的导航界面 + 多个子页面容器
- 顶部搜索框、主题切换器
- 状态栏显示连接状态和网络延迟
- 所有功能页面的容器容器

---

## 🔗 关键交互流程

### 文件传输流程
1. **手机→PC上传：**
   - 手机 `sendFile(outFile)` → ConnectionManager触发 `_start_file_receive()`
   - PC收到文件头 `send_file_head` → `_start_file_receive()` 设置 receive_dir 路径
   - 手机分块/流式上传 `/api/upload_file` → `_write_file_chunk()` 写入文件
   - 手机上传完成发 `file_complete` → `_complete_file_verify()` 校验大小
   - PC发射 `file_transfer_complete` 信号通知UI
   
2. **PC→手机下载：**
   - PC `send_file(path)` → 设置 outgoing_file_info 并发送文件头
   - 手机通过 `/api/download_file` Range请求下载分块
   - PC `/api/download_file` 生成器流式返回数据
   - 手机下载完成发 `file_complete` 确认

### 投屏流程 (手机→PC)
1. ScreenshotActivity 用户授权截屏 → MediaProjection实例创建
2. ConnectionManager 缓存MediaProjection token
3. 通过 `/api/phone_frame` POST上传JPEG帧数据
4. PC `receive_phone_frame()` 存入 `_latest_phone_frame` 并发射 `phone_frame_received`
5. MirrorWindow 收到信号缓存帧，定时器解码显示

### 投屏流程 (PC→手机)
1. `start_pc_stream()` → mss截屏循环60fps，缩放到≤1280宽，JPEG编码
2. 存入 `_latest_frame` 并通过 `/api/frame` GET返回
3. 手机通过 `getBaseUrlPublic()` 构造URL定期轮询拉取帧

### 通知监听流程
1. NotificationListener Service启动，每秒轮检通知变化
2. `processAndReport(sbn)` 提取title/text/actions等字段
3. 构建NotificationItem并通过 `ConnectionManager.reportNotification(item)` 上报
4. PC `/api/cmd` 收到 action=notification 后发射 `notification_received`

### 剪贴板同步流程
1. PC端 `clipboard_monitor()` 线程每0.5秒检测剪贴板变化
2. 内容与上次不同时发送 `send_clipboard()` → `/api/cmd` action=clipboard
3. 手机端接收后 `_receivedClipboard` StateFlow更新，UI显示

### 远程控制流程
1. MirrorCanvas 用户触摸事件 → 归一化坐标传递
2. `MirrorWindow._safe_touch()` → `manager._perform_screen_touch()`
3. ADB通道：`adb shell input tap/swipe` 或坐标转换执行
4. WiFi通道：下发 `{action: "screen_touch", x, y, op}` JSON消息

---

## ⚠️ 已知问题和注意事项

1. **notification.py缺失方法：** `_poll_active_notifications()` 此前报错已修复添加
2. **Qt警告过滤：** `QWindowsFontEngine::GetTextMetrics failed` 已在main.py中添加message handler过滤
3. **环境变量问题：** setup-android-env.ps1配置了ANDROID_NDK_HOME等，但NDK版本可能过旧
4. **依赖差异：** requirements.txt列的是PySide6，实际代码使用的是PyQt5 + qfluentwidgets
5. **安全令牌：** 默认token "541881452418845" hardcoded在多处，建议通过settings.json统一管理
6. **文件路径硬编码：** 两端都使用了硬编码路径 `F:\desk\手机上传` 和 `/Android/data/...`，应改为可配置
7. **PAW通道：** 有相关代码但基本被注释，尚未完全启用

---

## 🧩 代码记忆要点速查

| 代码片段 | 文件 | 行号 | 用途 |
|----------|------|------|------|
| `class CameraCanvas(QFrame):` | `desktop/pages/camera.py` | 13 | 摄像头画面画布 |
| `rootView.findViewById<Button>(id)?.setOnClickListener { sendKey(key) }` | `app/src/main/java/com/phonehub/mainactivity.kt` | 1195 | 虚拟键盘按键绑定 |
| `receiveDir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir, "PhoneHub")` | `connectionmanager.kt` | 396-398 | 手机文件接收目录 |
| `self.receive_dir = r"F:\desk\手机上传"` | `connection_manager.py` | 172 | PC文件接收目录 |
| `def wireFullKeyboard(rootView: View)` | `mainactivity.kt` | 1161 | 全键盘初始化的核心函数 |
| `func sendKey(key: String)` | `mainactivity.kt` | 1170 | 虚拟键盘按键发送 |

---

*文档生成时间：基于对 PhoneHub 项目全部核心源代码的阅读和分析*