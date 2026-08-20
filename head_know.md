# PhoneHub 项目完整代码逻辑文档

## 一、项目概述

PhoneHub 是一个手机-电脑管理套件，包含两个核心组件：
- **Android App** (`app/`): Kotlin 源码，Package `com.phonehub`，AGP 8.5.2，compileSDK 36，minSDK 24，JDK 17
- **Desktop App** (`desktop/`): Python 3.8+ 桌面应用，PyQt5 + qfluentwidgets 构建 GUI，Flask 作为 HTTP 后端（端口 58627）

两端通过 HTTP 协议通信，支持三种连接通道（按优先级）：
1. **ADB (USB)** — 最高优先级，使用 `adb forward` 端口转发
2. **WiFi (局域网直连)** — 中等优先级
3. **PAW (PythonAnywhere 中转)** — 远程连接，通过云服务器中转

---

## 二、Desktop 端（Python）文件结构与功能

### 2.1 核心文件

#### `desktop/main.py` — 程序入口
**功能**: 启动桌面应用，管理系统托盘

**核心逻辑**:
- 创建 `QApplication`，设置应用名 "PhoneHub"、字体 "Segoe UI Variable 9pt"
- 安装 Qt 消息过滤器（屏蔽 `QWindowsFontEngine` + `GetTextMetrics` 警告）
- 在导入 qfluentwidgets 之前必须先创建 QApplication
- 调用 `styles.get_theme()` 初始化主题
- 创建 `MainWindow` 后默认隐藏（最小化到系统托盘）
- 系统托盘右键菜单：
  - 显示主窗口
  - 快捷发送文字（弹出 `QInputDialog`）
  - 推送剪贴板（`pyperclip.paste()` → `manager.send_clipboard()`）
  - 退出（`manager.stop()` + `app.quit()`）
- 托盘图标双击/单击均弹出主窗口

---

#### `desktop/main_window.py` — 主窗口管理
**功能**: UI 布局、导航注册、页面初始化、状态栏、远程关机/重启弹窗

**核心逻辑**:
- 继承 `FluentWindow`（qfluentwidgets 的 Fluent Design 窗口）
- 构造顺序：`_setup_smooth_transition()` → `_create_pages()` → `_create_status_widget()` → `_connect_signals()` → `apply_dark_title_bar()` → `manager.start_server()`
- 页面切换使用 `EntranceTransitionStackedWidget` 滑入滑出过渡动画（替换默认弹出动画）
- 窗口尺寸 1000×680，最小 800×600
- 关闭事件：最小化到托盘而非退出（`event.ignore()` + `self.hide()`）

**导航项注册（13 个页面）**:
```python
nav_items = [
    (dashboard_page,       FIF.HOME,     "仪表盘"),
    (file_transfer_page,   FIF.SHARE,    "文件传输"),
    (clipboard_page,       FIF.COPY,     "剪贴板同步"),
    (text_page,            FIF.CHAT,     "文字互传"),
    (screen_mirror_page,   FIF.VIDEO,    "投屏与反向控制"),
    (camera_page,          FIF.CAMERA,   "共享摄像头"),
    (notifications_page,   FIF.MESSAGE,  "通知读取"),
    (location_map_page,    FIF.DATE_TIME,"移动路线图"),
    (file_manager_page,    FIF.FOLDER,   "远程文件管理"),
    (apk_install_page,     FIF.DOWNLOAD, "APK安装"),
    (app_manager_page,     FIF.ALBUM,    "应用管理"),
    (push_web_page,        FIF.LINK,     "推送网页"),
    (settings_page,        FIF.SETTING,  "设置", NavigationItemPosition.BOTTOM),
]
```

**状态栏**: 底部显示连接状态（绿色=已连接+通道名，红色=未连接，黄色=连接中消息）和 IP:端口

**远程关机/重启**: 收到 `power_action_received` 信号后弹出 30 秒倒计时弹窗，用户可取消（执行 `shutdown /a`）

**F3 热键（已禁用）**: 长按 F3 两秒后弹出粘贴截图窗口（Ctrl+V 粘贴图片→发送到手机）

---

#### `desktop/connection_manager.py` — 核心连接管理器
**功能**: Flask HTTP 服务器、设备连接管理、文件传输引擎、远程命令、信号分发

**类常量**:
| 常量 | 值 | 说明 |
|------|-----|------|
| `DEFAULT_SECRET_TOKEN` | `"541881452418845"` | 默认连接令牌 |
| `DEFAULT_PORT` | `58627` | Flask 监听端口 |
| `CHUNK_SIZE` | `524288` (512KB) | 文件传输分块大小 |
| `receive_dir` | `F:\desk\手机上传` | 文件接收目录 |

**信号定义（pyqtSignal）**:
| 信号 | 参数 | 用途 |
|------|------|------|
| `connection_status_changed` | `(bool, str)` | 连接状态变化 |
| `connection_message_changed` | `(str)` | 连接过程消息 |
| `clipboard_received` | `(str, str)` | 收到剪贴板(text, source) |
| `text_received` | `(str, str)` | 收到文字(text, filename) |
| `file_transfer_progress` | `(str, int, int, float)` | 传输进度(file_id, sent, total, ts) |
| `file_transfer_complete` | `(str, str)` | 传输完成(file_id, path) |
| `file_receive_started` | `(str, int, str)` | 开始接收文件(name, size, id) |
| `file_transfer_cancelled` | `(str)` | 对端取消传输 |
| `file_transfer_paused` | `(bool)` | 对端暂停/继续 |
| `notification_received` | `(dict)` | 收到通知 |
| `phone_frame_received` | `(bytes)` | 手机投屏帧(JPEG) |
| `camera_frame_received` | `(bytes)` | 手机摄像头帧(JPEG) |
| `phone_audio_received` | `(bytes)` | 手机端音频数据 |
| `phone_volume_received` | `(int)` | 手机端音量变化 |
| `phone_mute_received` | `(bool)` | 手机静音状态 |
| `screenshot_received` | `(str)` | 截图完成 |
| `app_list_received` | `(list)` | 应用列表 |
| `file_list_received` | `(str, list)` | 文件列表(path, files) |
| `location_received` | `(list)` | GPS 位置 |
| `clipboard_favorite_received` | `(str, bool)` | 收藏同步 |
| `clipboard_history_received` | `(list)` | 剪贴板历史同步 |
| `url_history_sync_received` | `(list)` | URL 历史同步 |
| `power_action_received` | `(str)` | 关机/重启指令 |
| `cpu_usage_received` | `(float)` | 电脑 CPU 使用率 |
| `phone_cpu_received` | `(float)` | 手机 CPU 使用率 |
| `phone_status_received` | `(dict)` | 手机状态(内存/磁盘/电量/温度) |
| `message_received` | `(dict)` | 通用消息 |
| `command_received` | `(dict)` | 指令消息 |
| `file_sent` | `(str)` | 文件发送完成 |
| `clipboard_sent` | `()` | 剪贴板发送完成 |

**Flask 路由**:
| 端点 | 方法 | 功能 |
|------|------|------|
| `/api/status` | GET | 返回电脑系统状态（CPU/内存/磁盘/网络） |
| `/api/poll` | GET | 合并轮询：返回状态 + 消息队列（减少请求数） |
| `/api/cmd` | POST | 手机上报指令（剪贴板/文字/文件头/传输控制/通知等） |
| `/api/upload_chunk` | POST | 手机端上传文件块到电脑 |
| `/api/download_chunk/<file_id>/<part_num>` | GET | 手机端下载文件块 |
| `/api/send_frame` | POST | 手机端上传投屏/摄像头帧 |
| `/api/send_audio` | POST | 手机端上传音频数据 |

**认证**: 所有请求需 `Authorization: Bearer <token>` 头，403 拒绝

**连接通道管理**:
- 通道优先级: ADB(3) > WiFi(2) > PAW(1) > None(0)
- 升降级机制: 连续 3 次确认升级，连续 3 次失败降级
- 传输期间暂停通道切换（`transfer_in_progress`）
- ADB 通道: 检测 USB → `adb forward` 端口转发
- WiFi 通道: 监听局域网连接，验证 token
- PAW 通道: 通过 PythonAnywhere 中转（长轮询 35s 超时）

**文件传输引擎**:
- 分块大小 512KB（`CHUNK_SIZE = 524288`）
- 支持暂停/继续/取消（`transfer_control` 消息）
- 冲突处理: 覆盖/重命名/跳过
- 进度采样: 最近 4 秒 8 个样本，指数平滑（0.7×旧 + 0.3×新）

**其他功能线程**:
- PC→手机推流: 截屏 → JPEG → 手机轮询拉取（`_pc_stream_running`）
- PC 摄像头→手机推流: OpenCV 采集 → JPEG（`_pc_camera_running`）
- 手机→电脑投屏: 接收 JPEG 帧（`_phone_mirror_running`）
- 声音传输: 双向（`_phone_audio_running` / `_pc_audio_running`）
- 媒体信息监测: 1s 周期检测电脑播放的媒体标题/艺术家（`_media_monitor_running`）
- 剪贴板监控: 0.5s 周期检测本地剪贴板变化（`CLIPBOARD_MONITOR_INTERVAL`）

**日志方法**:
- `log(msg)`: 统一日志（logger + print）
- `log_phone_request(action, detail)`: 记录手机请求
- `log_pc_send(action, detail)`: 记录 PC 发送

---

#### `desktop/styles.py` — 主题管理
**功能**: 深色/浅色主题切换、颜色常量、UI 辅助函数

**核心逻辑**:
- `_ThemeColor` 类: 惰性求值（支持 f-string 占位）
- `_c()` 函数: 返回当前主题颜色字典
- 主题持久化: `~/PhoneHub/data/theme.json`
- 同步到 qfluentwidgets 的 `qconfig.theme`

**颜色字典**:
```python
{
    "bg": "#1C1C1C" / "#F3F3F3",        # 背景
    "surface": "#2C2C2C" / "#FFFFFF",    # 卡片/面板
    "text": "#FFFFFF" / "#1A1A1A",       # 主文字
    "text_secondary": "rgba(255,255,255,0.45)" / "rgba(0,0,0,0.45)",  # 次要文字
    "accent": "#60CDFF" / "#0078D4",     # 强调色
    "success": "#6CCB5F" / "#0F7B0F",    # 成功
    "error": "#FF99A4" / "#C42B1C",      # 错误
    "border": "rgba(255,255,255,0.08)" / "rgba(0,0,0,0.08)",
    "card": "#2C2C2C" / "#FFFFFF",
    "card_border": "rgba(255,255,255,0.06)" / "rgba(0,0,0,0.06)",
    "flyout": "#323232" / "#F6F6F6",
    "surface_hover": "#383838" / "#F0F0F0",
}
```

**辅助函数**:
- `dark_dialog_style()`: 深色弹窗 QSS 样式
- `dark_msg_box(parent, icon, title, text, buttons)`: 深色消息框
- `apply_dark_title_bar(window)`: Windows 深色标题栏（DWM API `DwmSetWindowAttribute`）
- `set_item_text_color(item)`: 列表项文字颜色适配主题

---

#### `desktop/paw_relay_server.py` — PAW 中转服务器
**功能**: 部署到 PythonAnywhere 的 Flask 中转服务

**核心逻辑**:
- 设备注册: `/api/register`
- 心跳保活: `/api/heartbeat`（60s 超时清理）
- 消息队列: `/api/send`, `/api/get_cmd`, `/api/get_msg`
- 文件中转: `/api/upload_chunk`, `/api/download_chunk`
- 流量控制: 300MB 上限
- 定时清理: 10 分钟清理超时文件块，60 秒清理离线设备
- 认证: `PHONEHUB_SECRET_TOKEN` 环境变量

---

### 2.2 功能页面文件 (`desktop/pages/`)

#### `desktop/pages/dashboard.py` — 仪表盘
**功能**: 显示连接状态和系统统计

**核心组件**:
- `StatProgressBar`: 带进度条的统计卡片（颜色随百分比变化：<70% 蓝色, 70-90% 橙色, >90% 红色）
- `ConnectionDot`: 连接状态圆点指示器（绿色=已连接，红色=未连接，带外发光效果）

**UI 布局**:
- 标题栏 + 连接状态圆点 + 状态文字
- 连接信息卡片（通道/本机IP/端口/网络速率）
- 统计网格（2×3）: 电脑内存/手机内存/电脑磁盘/手机磁盘/手机电量/手机温度
- 快捷操作栏: 推送剪贴板/发送文字/打开接收文件夹

**定时器**: 1s 周期刷新本地统计（`psutil` 获取内存/磁盘/网络速率）

---

#### `desktop/pages/file_transfer.py` — 文件传输
**功能**: 管理文件上传/下载，进度显示，冲突处理

**核心组件**:
- `ConflictDialog`: 同名冲突弹窗（覆盖/重命名/跳过）
- `FileTransferPage`: 主页面

**状态机**: `idle` → `sending`/`receiving` → `done` → `idle`

**速度计算**:
- 采样窗口: 最近 4 秒，最多 8 个样本
- 指数平滑: `smooth_speed = 0.7×旧 + 0.3×瞬时`
- 500ms 定时器刷新 UI（避免每个分块都重算导致闪动）
- ETA 计算: `剩余字节 / 速度`

**关键逻辑**:
- 暂停/继续: 通过 `send_transfer_control("pause"/"resume")` 通知对端
- 取消: 删除未完成的 `.progress` 文件和接收文件
- 完成确认: 传输完成后显示"完成"按钮，点击后将记录写入历史
- 双击历史条目可重新发送

---

#### `desktop/pages/screen_mirror.py` — 投屏与反向控制
**功能**: 手机投屏到电脑、电脑画面推流到手机、声音传输、快捷控制

**核心组件**:
- `MirrorCanvas(QFrame)`: 投屏画布
  - 复用 `QPixmap` 对象避免每帧创建销毁
  - 缓存缩放后的 pixmap（`_cached_scaled`）
  - 鼠标事件: 点击(down/click/up)、拖拽(move)、长按(≥1s=返回键)
  - 坐标归一化: 画布坐标 → 0-1 归一化坐标
- `MirrorWindow(QWidget)`: 独立投屏窗口
  - 60fps 显示（16ms 定时器）
  - 跳帧防卡顿: 仅缓存最新帧，定时器统一解码
  - 超过 2 秒无帧清空画面
  - 触摸操作通过 `ThreadPoolExecutor(max_workers=2)` 执行
- `ScreenMirrorPage(QWidget)`: 主页面（仅提供控制按钮）

**快捷控制按钮**:
- 音量滑块（0-15）+ 静音按钮
- 锁屏/返回/主屏/最近任务/通知栏/控制中心/手机截图

**触摸操作类型**: `click`(单击) / `down`+`up`(拖拽) / `move`(移动) / `right`(长按=返回)

---

#### `desktop/pages/clipboard_sync.py` — 剪贴板同步
**功能**: 双向剪贴板同步、历史记录、收藏管理

**数据存储**:
- `~/PhoneHub/data/clipboard_history.json`（最多 500 条）
- `~/PhoneHub/data/clipboard_favorites.json`（最多 50 条）

**核心功能**:
- 立即同步剪贴板（电脑→手机）
- 历史记录搜索和过滤（"仅显示收藏"）
- 收藏功能（★ 收藏当前）
- 推送收藏到手机
- 双向同步: 手机端收藏变更同步到电脑（`clipboard_favorite_received`）
- 手机端剪贴板历史同步（`clipboard_history_received`）
- 防回环: `_suppress_clipboard` 标志避免无限循环

**右键菜单**: 复制/收藏/删除

---

#### `desktop/pages/text_share.py` — 文字互传
**功能**: 发送/接收文字，历史记录管理

**数据存储**: `~/PhoneHub/data/text_history.json`（最多 100 条）

**核心功能**:
- 发送文字（可指定文件名，留空用时间戳命名）
- 接收文字显示
- 保存为文件（保留原后缀，无后缀才补 `.txt`）
- 历史记录: 搜索/复制/保存/重新发送/删除
- 每条记录包含: text/filename/source/time

---

#### `desktop/pages/notifications.py` — 通知读取
**功能**: 读取手机通知、远程操作、黑名单管理

**数据存储**:
- `~/PhoneHub/data/notifications_history.json`（最多 200 条）
- `~/PhoneHub/data/notifications_blacklist.json`

**核心组件**:
- `NotificationPopupDialog`: 通知详情弹窗（远程操作按钮/删除/打开应用并投屏）
- `BlacklistDialog`: 黑名单管理弹窗
- `NotificationsPage`: 主页面（双 Tab: 当前活动通知/历史记录）

**核心逻辑**:
- 1s 周期拉取活动通知（`get_active_notifications`）
- 批量上报去重: 150ms 窗口合并批量通知，签名比对避免重复刷新
- 通知签名: `key|package|title|text|summary|actions`
- 黑名单过滤: 包名匹配
- 远程操作: 发送 `notification_action` 到手机
- 打开应用并投屏: `send_command("open_app", {package, mirror: True})`

---

#### `desktop/pages/camera.py` — 共享摄像头
**功能**: 查看手机摄像头、电脑摄像头推流到手机

**核心组件**:
- `CameraCanvas(QFrame)`: 摄像头画面画布（复用 QPixmap）
- `CameraWindow(QWidget)`: 独立摄像头窗口
  - 支持两种模式: `mode="phone"`(手机摄像头) / `mode="pc"`(电脑摄像头预览)
  - 60fps 显示，2 秒无帧清空
  - 保持宽高比缩放（`resizeEvent` 中强制比例）
- `CameraPage(QWidget)`: 主页面（仅提供控制按钮）

**核心功能**:
- 查看手机摄像头（独立窗口，`camera_frame_received` 信号）
- 电脑摄像头→手机推流（OpenCV `VideoCapture(0)` → JPEG）
- 依赖: `opencv-python`（`cv2`）

---

#### `desktop/pages/remote_control.py` — 远程控制
**功能**: 手机截图

**截图保存目录**: `F:\desk\手机上传\截图`

**核心逻辑**:
- ADB 模式: `adb exec-out screencap -p` → 保存本地 → 通知手机
- WiFi 模式: 发送 `screenshot_request` → 手机截图后通过 `sendFile` 回传

---

#### `desktop/pages/file_manager.py` — 远程文件管理
**功能**: 浏览和操控手机文件系统

**核心组件**:
- `FileManagerPage(QWidget)`: 主页面
- 使用 `TreeWidget` 显示文件列表（4 列: 名称/大小/权限/修改时间）
- 支持多选（`ExtendedSelection`）

**文件操作**:
- 新建文件夹: ADB `mkdir -p` / WiFi `file_mkdir`
- 上传: ADB `adb push` / WiFi `send_file`
- 下载: ADB `adb pull` / WiFi `send_file_request`
- 删除: ADB `rm` / WiFi `file_delete`
- 重命名: ADB `mv` / WiFi `file_rename`
- 复制: ADB `cp` / WiFi `file_copy`

**文本文件直接打开**: 支持 50+ 种扩展名（`TEXT_FILE_EXTS`）

**排序**: 点击表头排序（名称/大小/修改时间），目录始终优先

**文件图标**: 根据扩展名显示 emoji 图标（📁📦🖼🎬🎵📄📝🐍📜📎）

---

#### `desktop/pages/app_manager.py` — 应用管理
**功能**: 加载应用列表、卸载/清除数据/导出 APK

**APK 备份目录**: `~/PhoneHub/AppBackups/`

**核心组件**:
- `AppManagerPage(QWidget)`: 主页面
- 使用 `TableWidget` 显示应用列表（5 列: 应用名/包名/版本/大小/安装时间）

**加载应用列表**:
- ADB 模式: `pm list packages -3 -f` → 解析包名和 APK 路径 → `dumpsys package` 查版本/安装时间 → `wc -c` 查大小（限制 200 个应用）
- WiFi 模式: 发送 `app_list_request` → 接收 `app_list_received` 信号

**操作**:
- 卸载(含备份): ADB `pm path` → `adb pull` 备份 → `adb uninstall`
- 清除数据: 仅 ADB 模式（`adb shell pm clear`）
- 导出 APK: ADB `adb pull` / WiFi `app_apk_request`

**信号**: `action_progress(int, str)` / `action_done(bool, str)` / `apps_loaded()` 用于跨线程 UI 更新

---

#### `desktop/pages/apk_install.py` — APK 安装
**功能**: 选择 APK 并安装到手机

**核心逻辑**:
- 支持拖拽 `.apk` 文件到页面
- 点击拖放区域选择 APK
- 统一走文件传输模块（`manager.send_file()`）
- 手机端接收完成后自动安装（`send_action("install_apk", {path})`）
- 仅 ADB 通道可用（WiFi 模式提示不支持）
- 安装为原子操作，无法中途取消

---

#### `desktop/pages/push_web.py` — 推送网页
**功能**: 推送 URL 到手机、接收手机推送、URL 历史同步

**数据存储**:
- `~/PhoneHub/data/push_web_history.json`（最多 100 条）
- `~/PhoneHub/data/push_web_last.json`（上次发送记录）

**核心逻辑**:
- URL 检测: `https?://` 或 `www.` 前缀，或简单域名匹配
- 推送 URL: ADB/WiFi 均通过 `send_action("open_url", {url})`
- 推送文字: 通过 `send_text()` 走文字互传通道
- 接收处理: URL → Edge 打开 / 文字 → Bing 搜索
- URL 历史同步: 连接成功时发送本地历史给手机（`send_url_history()`）
- 合并远端历史: 去重（同 URL 同方向）→ 按时间降序

---

#### `desktop/pages/location_map.py` — 移动路线图（已禁用）
**功能**: 原计划使用 Leaflet.js 显示 GPS 轨迹地图

**当前状态**: 因闪退/白屏问题暂时禁用，仅显示 "该功能暂未开放，敬请期待"

**原功能（注释代码中）**:
- `QWebEngineView` 加载 Leaflet.js 地图
- 轨迹点渲染（有信号=蓝色实线，无信号=灰色虚线）
- 时间范围筛选（今天/近7天/自定义）
- 轨迹点管理（删除/清空）
- 数据缓存: `~/PhoneHub/data/location_cache.json`

---

#### `desktop/pages/settings.py` — 设置
**功能**: 应用配置管理

**数据存储**: `~/PhoneHub/data/settings.json`

**设置项**:
- **外观**: 深色/浅色主题切换（立即生效）
- **连接设置**: 监听端口/连接令牌/PAW 服务器地址/开机自启
- **手机端设置**: 永不休眠（通过 `send_command("never_sleep")` 下发）
- **剪贴板设置**: 自动同步/历史记录上限
- **接收设置**: 接收文件夹/接收完成后自动打开
- **关于**: 版本/IP/设备 ID

**手机状态显示**: 监听 `phone_status_received` 信号，显示"永不休眠(已启用)"/"默认休眠策略"/"(未知)"

---

## 三、Android 端（Kotlin）文件结构与功能

### 3.1 核心文件

#### `app/src/main/java/com/phonehub/App.kt` — Application
**功能**: 应用启动时初始化

**核心逻辑**:
- 初始化全局 Context（`companion object` 持有 `appContext`）
- 安装 MultiDex（`MultiDex.install()`）
- 初始化日志工具（`LogUtil.init()`）
- 初始化连接管理器（`ConnectionManager.init()`）
- 启动前台保活服务（`PhoneHubService.start()`）

---

#### `app/src/main/java/com/phonehub/MainActivity.kt` — 主 Activity
**功能**: 用户界面入口

**核心逻辑**:
- 请求必要权限（存储/位置/通知/无障碍等）
- 检查无障碍服务是否开启
- 显示连接状态和操作按钮
- 处理手机端用户操作（截图/通知权限引导等）

---

#### `app/src/main/java/com/phonehub/ConnectionManager.kt` — 连接管理器（手机端）
**功能**: 与电脑端通信的核心管理器

**核心逻辑**:
- 初始化 Ktor HttpClient（OkHttp 引擎）
- 管理接收目录: `/sdcard/Download/PhoneHub/`
- 剪贴板监控: `ContentObserver` 监听系统剪贴板变化
- 文件传输: 分块上传/下载（与 PC 端 CHUNK_SIZE 对应）
- 指令处理: 接收电脑命令并执行（截图/音量/锁屏等）
- 状态上报: 内存/磁盘/电量/温度等系统信息
- MediaProjection token 缓存: 供后台静默截图复用

**关键方法**:
```kotlin
fun sendFile(file: File)                    // 发送文件到电脑
fun sendClipboard(text: String)             // 发送剪贴板
fun sendText(text: String, filename: String?) // 发送文字
fun cacheMediaProjectionToken(resultCode, data) // 缓存截图权限
```

---

#### `app/src/main/java/com/phonehub/PhoneHubService.kt` — 前台保活服务
**功能**: 确保应用持续运行

**核心逻辑**:
- 创建前台通知（低优先级，静默）
- 获取 WakeLock（`PARTIAL_WAKE_LOCK`，防止 CPU 休眠）
- 三重重启机制（用户清理后台时 `onTaskRemoved`）:
  1. `AlarmManager`（3 秒后）
  2. `JobScheduler`（5-15 秒后）
  3. 直接拉起服务
  4. 二次兜底（20 秒后）

**关键代码**:
```kotlin
override fun onTaskRemoved(rootIntent: Intent?) {
    scheduleRestart(this, 3_000)    // AlarmManager
    scheduleJobRestart(this)          // JobScheduler
    start(this)                       // 直接拉起
    scheduleRestart(this, 20_000)     // 二次兜底
}
```

---

#### `app/src/main/java/com/phonehub/ScreenCaptureService.kt` — 屏幕捕获服务
**功能**: 使用 MediaProjection API 捕获屏幕

**核心逻辑**:
- 请求 MediaProjection 权限（需用户授权）
- 创建 VirtualDisplay
- 使用 ImageReader 捕获屏幕帧
- 编码为 JPEG 并发送到电脑
- 支持 60fps 帧率

**关键流程**:
```kotlin
fun startCapture(resultCode: Int, data: Intent) {
    projection = projectionManager.getMediaProjection(resultCode, data)
    virtualDisplay = projection.createVirtualDisplay(...)
    imageReader.setOnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        // Bitmap → JPEG → POST 到电脑
    }
}
```

---

#### `app/src/main/java/com/phonehub/NotificationListener.kt` — 通知监听服务
**功能**: 读取手机通知并上报到电脑

**核心逻辑**:
- 继承 `NotificationListenerService`
- `onNotificationPosted`: 提取标题/内容/包名/操作按钮 → 发送到电脑
- `onNotificationRemoved`: 通知移除回调
- 支持获取活动通知列表（`getActiveNotifications()`）
- 支持远程取消通知（`cancelNotification(key)`）
- 支持远程执行通知操作按钮

---

#### `app/src/main/java/com/phonehub/PhoneHubAccessibilityService.kt` — 无障碍服务
**功能**: 执行全局操作和手势模拟

**核心功能**:
- 媒体键注入: 播放/暂停/上一首/下一首（`KeyEvent`）
- 全局键: 锁屏/最近任务/通知栏（`performGlobalAction`）
- 模拟点击: `dispatchGesture` + `GestureDescription`
- 模拟滑动: `Path` + `lineTo` + `dispatchGesture`
- 键盘输入: 特殊键/普通字符/功能键

**关键方法**:
```kotlin
fun performTap(x: Float, y: Float)       // 模拟点击
fun performSwipe(x1, y1, x2, y2, duration) // 模拟滑动
```

---

#### `app/src/main/java/com/phonehub/ScreenshotActivity.kt` — 截图 Activity
**功能**: 使用 MediaProjection 截图

**核心逻辑**:
- 请求 MediaProjection 权限（透明引导界面）
- 捕获屏幕帧（ImageReader）
- 保存到相册: `Pictures/Computer/`
- 回传到电脑: `ConnectionManager.sendFile()`
- 缓存 token 供后台静默截图复用

---

#### `app/src/main/java/com/phonehub/LocationService.kt` — GPS 定位服务
**功能**: 前台服务持续上报位置

**核心逻辑**:
- 优先使用 GPS，其次 NETWORK
- 最小间隔 30 秒，最小距离 100 米
- 离线缓存位置，恢复连接后批量上传
- 位置数据格式: `{lat, lng, ts, signal}`

---

### 3.2 辅助文件

#### `app/src/main/java/com/phonehub/LogUtil.kt` — 日志工具
**功能**: 双输出日志（Logcat + 文件）

**核心功能**:
- 输出到 Logcat 和文件: `/storage/emulated/0/_/PHlog.txt`
- 详细时间戳: `yyyy-MM-dd HH:mm:ss.SSS`
- 单线程写入器: `Executors.newSingleThreadExecutor()`
- 模块日志开关: accessibility / connection / screen / input

---

#### `app/src/main/java/com/phonehub/SharedNotificationHelper.kt` — 通知构建器
**功能**: 共享通知构建工具

**核心功能**:
- 创建通知渠道
- 构建基础通知（点击打开 MainActivity）
- 供 `PhoneHubService` 和 `ScreenCaptureService` 共用

---

#### `app/src/main/java/com/phonehub/NativeButtonKt.kt` — Button 样式扩展
**功能**: Button 深色主题样式

**核心功能**:
- `applyDarkTheme(primary: Boolean)`: 应用深色主题样式
- `primary=true`: 蓝色实心按钮
- `primary=false`: 深色描边按钮

---

#### `app/src/main/java/com/phonehub/ProcessTextActivity.kt` — 文字处理 Activity
**功能**: 系统文本选择菜单中的「同步复制」

**核心逻辑**:
- 通过 `ACTION_PROCESS_TEXT` 注册
- 长按文字后的悬浮菜单显示此选项
- 将选中文字同步复制到电脑

---

### 3.3 BroadcastReceiver 文件

#### `app/src/main/java/com/phonehub/TextNotificationReceiver.kt` — 文字通知接收器
**功能**: 文字消息通知的按钮点击处理

**处理操作**:
- `ACTION_COPY`: 复制文字到系统剪贴板
- `ACTION_SAVE`: 保存文字到接收目录

---

#### `app/src/main/java/com/phonehub/FileTransferReceiver.kt` — 文件传输接收器
**功能**: 文件传输通知的按钮点击处理

**处理操作**:
- `ACTION_START_DOWNLOAD`: 开始下载文件
- `ACTION_CANCEL_DOWNLOAD`: 取消传输

---

#### `app/src/main/java/com/phonehub/BootReceiver.kt` — 开机自启接收器
**功能**: 手机重启后自动启动

**核心逻辑**:
- 监听 `ACTION_BOOT_COMPLETED`
- 自动启动 `PhoneHubService`

---

#### `app/src/main/java/com/phonehub/RestartServiceReceiver.kt` — AlarmManager 重启接收器
**功能**: PhoneHubService 被杀后通过 AlarmManager 定时拉起

---

#### `app/src/main/java/com/phonehub/RestartJobService.kt` — JobScheduler 重启服务
**功能**: 当 AlarmManager 被系统限制时（Doze 深度休眠），JobScheduler 作为保活备用通道

---

## 四、通信协议与数据流

### 4.1 HTTP API 端点

**PC 端 Flask 服务器（端口 58627）**:

| 端点 | 方法 | 功能 | 方向 |
|------|------|------|------|
| `/api/status` | GET | 获取电脑系统状态 | PC←Phone |
| `/api/poll` | GET | 合并轮询（状态+消息队列） | PC←Phone |
| `/api/cmd` | POST | 手机上报指令 | PC←Phone |
| `/api/upload_chunk` | POST | 上传文件块到电脑 | PC←Phone |
| `/api/download_chunk/<id>/<num>` | GET | 从电脑下载文件块 | PC→Phone |
| `/api/send_frame` | POST | 上传投屏/摄像头帧 | PC←Phone |
| `/api/send_audio` | POST | 上传音频数据 | PC←Phone |

**PAW 中转服务器（PythonAnywhere）**:

| 端点 | 方法 | 功能 |
|------|------|------|
| `/api/register` | POST | 设备注册 |
| `/api/heartbeat` | POST | 心跳保活 |
| `/api/send` | POST | 发送消息 |
| `/api/get_cmd` | GET | 获取指令（长轮询） |
| `/api/get_msg` | GET | 获取消息（长轮询） |
| `/api/upload_chunk` | POST | 文件中转上传 |
| `/api/download_chunk` | GET | 文件中转下载 |

### 4.2 主要数据流

#### 文件传输流程（PC→Phone）:
```
1. PC 端选择文件 → connection_manager.send_file()
2. 生成 file_id，分块读取文件（512KB/块）
3. 手机端通过 /api/download_chunk 逐块拉取
4. 每块写入临时文件（.progress 后缀）
5. 全部块接收完成后合并
6. 移动到最终目录（/sdcard/Download/PhoneHub/）
7. 发送 file_complete 通知到 PC
```

#### 文件传输流程（Phone→PC）:
```
1. 手机端发送 send_file_head（文件名/大小/ID）
2. PC 端检查冲突 → 通知手机接受/重命名/跳过
3. 手机端分块 POST 到 /api/upload_chunk
4. PC 端接收并写入临时文件
5. 全部块接收完成后合并到 receive_dir
6. 触发 file_transfer_complete 信号
7. UI 更新进度
```

#### 剪贴板同步流程:
```
PC→Phone:
1. PC 端读取系统剪贴板（pyperclip）
2. POST 到 /api/cmd (action="clipboard")
3. 手机端接收并写入系统剪贴板

Phone→PC:
1. 手机端 ContentObserver 监听剪贴板变化
2. POST 到 PC 的 /api/cmd (action="clipboard")
3. PC 端接收 → clipboard_received 信号
4. 写入系统剪贴板（pyperclip）
5. 防回环: _suppress_clipboard 标志
```

#### 投屏流程:
```
Phone→PC:
1. PC 端发送 mirror_start 指令
2. 手机端启动 ScreenCaptureService
3. MediaProjection 捕获屏幕帧
4. 编码为 JPEG → POST /api/send_frame
5. PC 端接收 → phone_frame_received 信号
6. MirrorWindow 60fps 解码显示（跳帧防卡顿）

PC→Phone（远程控制）:
1. MirrorCanvas 捕获鼠标事件
2. 转换为归一化坐标（0-1）
3. 通过 ThreadPoolExecutor 后台执行
4. POST 到 /api/cmd (action="touch")
5. 手机端 PhoneHubAccessibilityService 执行手势
6. dispatchGesture 模拟点击/滑动/长按
```

---

## 五、关键设计模式与技术点

### 5.1 多线程与异步

**Desktop 端**:
- Flask 服务器: `threading.Thread(daemon=True)`
- 文件传输: 后台线程
- UI 更新: `pyqtSignal` 跨线程
- 触摸操作: `ThreadPoolExecutor(max_workers=2)`
- 速度计算: 500ms `QTimer` 定时刷新

**Android 端**:
- Kotlin Coroutines: `CoroutineScope` + `launch`/`async`
- `StateFlow`/`MutableStateFlow`: 响应式状态
- 单线程日志: `Executors.newSingleThreadExecutor()`

### 5.2 前台服务与保活

**Android 端保活机制**:
1. **前台服务**: `PhoneHubService` 创建持久通知
2. **WakeLock**: `PARTIAL_WAKE_LOCK` 防止 CPU 休眠
3. **三重重启**:
   - AlarmManager（3 秒后）
   - JobScheduler（5-15 秒后）
   - 直接拉起 + 二次兜底（20 秒后）

### 5.3 文件分块传输

**分块策略**:
- 块大小: 512KB（`CHUNK_SIZE = 524288`）
- 流量控制: 300MB 上限（PAW 中转）
- 暂停/继续: `transfer_control` 消息
- 冲突处理: 覆盖/重命名/跳过

### 5.4 权限管理

**Android 端必要权限**:
- `FOREGROUND_SERVICE`: 前台服务
- `SYSTEM_ALERT_WINDOW`: 悬浮窗
- `BIND_ACCESSIBILITY_SERVICE`: 无障碍服务
- `BIND_NOTIFICATION_LISTENER_SERVICE`: 通知监听
- `MEDIA_PROJECTION`: 屏幕捕获
- `ACCESS_FINE_LOCATION`: GPS 定位
- `READ/WRITE_EXTERNAL_STORAGE`: 存储

### 5.5 UI 框架

**Desktop 端**:
- PyQt5 + qfluentwidgets
- Fluent Design 风格
- 深色/浅色主题（`_c()` 颜色字典）
- 系统托盘图标
- `EntranceTransitionStackedWidget` 页面过渡动画

**Android 端**:
- Material Design
- Activity + Service 架构
- 透明引导界面（权限请求）

---

## 六、数据持久化

### 6.1 Desktop 端

**存储位置**: `~/PhoneHub/data/`

| 文件 | 内容 |
|------|------|
| `settings.json` | 设置（端口/令牌/主题等） |
| `theme.json` | 主题配置 |
| `clipboard_history.json` | 剪贴板历史（最多 500 条） |
| `clipboard_favorites.json` | 剪贴板收藏（最多 50 条） |
| `text_history.json` | 文字互传历史（最多 100 条） |
| `notifications_history.json` | 通知历史（最多 200 条） |
| `notifications_blacklist.json` | 通知黑名单 |
| `push_web_history.json` | 推送网页历史（最多 100 条） |
| `push_web_last.json` | 上次推送记录 |
| `location_cache.json` | GPS 轨迹缓存 |

**其他存储**:
- `F:\desk\手机上传`: 文件接收目录
- `F:\desk\手机上传\截图`: 截图保存目录
- `~/PhoneHub/AppBackups/`: APK 备份目录
- `~/PhoneHub/log.txt`: 运行日志
- `~/.phonehub_ip_cache`: 手机 IP 缓存

### 6.2 Android 端

**存储位置**:
- `/sdcard/Download/PhoneHub/`: 接收文件
- `/storage/emulated/0/_/PHlog.txt`: 日志文件
- `Pictures/Computer/`: 截图相册

---

## 七、文件功能速查表

### Desktop 端文件清单

| 文件路径 | 功能 |
|----------|------|
| `desktop/main.py` | 程序入口，系统托盘 |
| `desktop/main_window.py` | 主窗口，导航注册，状态栏 |
| `desktop/connection_manager.py` | Flask 服务器，连接管理，文件传输，信号 |
| `desktop/styles.py` | 主题管理，颜色常量，UI 辅助 |
| `desktop/paw_relay_server.py` | PAW 中转服务器（PythonAnywhere） |
| `desktop/pages/dashboard.py` | 仪表盘（连接状态/系统统计） |
| `desktop/pages/file_transfer.py` | 文件传输（进度/冲突/暂停） |
| `desktop/pages/screen_mirror.py` | 投屏与反向控制（触摸/音量/快捷） |
| `desktop/pages/clipboard_sync.py` | 剪贴板同步（历史/收藏） |
| `desktop/pages/text_share.py` | 文字互传 |
| `desktop/pages/notifications.py` | 通知读取（活动/历史/黑名单） |
| `desktop/pages/camera.py` | 共享摄像头 |
| `desktop/pages/remote_control.py` | 远程控制（截图） |
| `desktop/pages/file_manager.py` | 远程文件管理 |
| `desktop/pages/app_manager.py` | 应用管理（卸载/清除/导出） |
| `desktop/pages/apk_install.py` | APK 安装 |
| `desktop/pages/push_web.py` | 推送网页（URL 同步） |
| `desktop/pages/location_map.py` | 移动路线图（已禁用） |
| `desktop/pages/settings.py` | 设置 |

### Android 端文件清单

| 文件路径 | 功能 |
|----------|------|
| `app/.../App.kt` | Application 初始化 |
| `app/.../MainActivity.kt` | 主 Activity，权限请求 |
| `app/.../ConnectionManager.kt` | 连接管理器（HTTP/文件/剪贴板） |
| `app/.../PhoneHubService.kt` | 前台保活服务（三重重启） |
| `app/.../ScreenCaptureService.kt` | 屏幕捕获（MediaProjection） |
| `app/.../NotificationListener.kt` | 通知监听（上报/远程操作） |
| `app/.../PhoneHubAccessibilityService.kt` | 无障碍服务（手势/全局操作） |
| `app/.../ScreenshotActivity.kt` | 截图 Activity |
| `app/.../LocationService.kt` | GPS 定位服务 |
| `app/.../LogUtil.kt` | 日志工具 |
| `app/.../SharedNotificationHelper.kt` | 通知构建器 |
| `app/.../NativeButtonKt.kt` | Button 深色样式 |
| `app/.../ProcessTextActivity.kt` | 系统文字选择菜单 |
| `app/.../TextNotificationReceiver.kt` | 文字通知按钮处理 |
| `app/.../FileTransferReceiver.kt` | 文件传输按钮处理 |
| `app/.../BootReceiver.kt` | 开机自启 |
| `app/.../RestartServiceReceiver.kt` | AlarmManager 重启 |
| `app/.../RestartJobService.kt` | JobScheduler 重启 |

---

## 八、常见问题与注意事项

### 8.1 构建与运行

**Desktop 端**:
```bash
pip install -r desktop/requirements.txt  # 注意: requirements.txt 过时
python desktop/main.py
```

**Android 端**:
```bash
call "c:\PhoneHub\gradle-dist\gradle-8.9\bin\gradle.bat" assembleDebug --no-daemon --console=plain --offline
# APK: app\build\outputs\apk\debug\app-debug.apk
```

### 8.2 已知问题

1. **requirements.txt 过时**: 列出 PySide6 但实际使用 PyQt5
2. **无测试框架**: 项目未配置测试
3. **无版本控制**: 未初始化 git
4. **location_map.py 已禁用**: 因闪退/白屏问题

### 8.3 开发规范

**Python 端**:
- 命名: `snake_case`（函数/变量），`PascalCase`（类）
- 注释: 中文
- 编码: UTF-8
- 日志: 使用 `log()` 函数（来自 `connection_manager.py`）

**Kotlin 端**:
- 命名: `camelCase`（方法/属性），`PascalCase`（类）
- 字符串: 中文 UI 文本
- 错误处理: `try/catch` 最小化日志
- 协程: 大量使用 `suspend` 函数

---

## 九、扩展开发指南

### 9.1 添加新的 Desktop 页面

1. 创建 `desktop/pages/your_page.py`，继承 qfluentwidgets 组件
2. 在 `main_window.py` 中导入并注册:
   ```python
   from pages.your_page import YourPage
   self.your_page = YourPage(self.manager)
   nav_items.append((self.your_page, FIF.ICON, "页面标题"))
   ```
3. 参考现有页面（如 `dashboard.py`）实现 UI 和信号连接

### 9.2 添加新的 Android 功能

1. 在 `app/src/main/java/com/phonehub/` 添加 Kotlin 文件
2. 在 `AndroidManifest.xml` 注册 Activity/Service/Receiver
3. 通过 `ConnectionManager` 与 PC 端通信
4. 使用 `LogUtil` 记录日志

---

## 十、总结

PhoneHub 是一个功能丰富的手机-电脑管理工具，核心特点:
- **双端架构**: Android App + Desktop App
- **多通道连接**: ADB/WiFi/PAW 三种方式，自动升降级
- **实时通信**: HTTP + 长轮询
- **文件传输**: 512KB 分块，支持断点续传/暂停/冲突处理
- **远程控制**: 投屏 + 触摸模拟（60fps）
- **保活机制**: 三重重启策略（AlarmManager + JobScheduler + 直接拉起）
- **Fluent Design**: 现代化 UI，深色/浅色主题

项目代码结构清晰，模块化程度高，适合二次开发和功能扩展。
