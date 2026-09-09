# PhoneHub — 手机-电脑互联管理套件

PhoneHub 是一套手机与电脑之间的互联管理工具，包含一个 Android 客户端和一个 Windows 桌面端。通过 HTTP 协议实现手机与电脑的双向通信，支持文件传输、剪贴板同步、屏幕投屏、远程控制等多种功能。

---

## 项目组成

| 组件 | 目录 | 技术栈 |
|------|------|--------|
| **Android 客户端** | `app/` | Kotlin，AGP 8.5.2，compile/target SDK 36，min SDK 24，JDK 17 |
| **桌面客户端** | `desktop/` | Python 3.8+，PyQt5 + qfluentwidgets（Fluent Design GUI），Flask HTTP 后端（端口 58627） |

项目自带完整的工具链，无需额外安装环境：
- **JDK 17** — `jdk17/`
- **Android SDK** — `android-sdk/`
- **Gradle 8.9** — `gradle-dist/`

---

## 功能特性

### 桌面端页面（共 12 个功能模块）

| 页面 | 说明 |
|------|------|
| 仪表盘 | 连接状态总览，设备信息展示 |
| 文件传输 | 手机与电脑之间双向文件传输 |
| 剪贴板同步 | 跨设备剪贴板内容实时同步 |
| 文字互传 | 快速发送/接收文本内容 |
| 共享摄像头 | 将手机摄像头画面共享到电脑 |
| 通知读取 | 在电脑端查看手机通知 |
| 移动路线图 | 基于 GPS 记录手机移动轨迹 |
| 远程文件管理 | 浏览和管理手机上的文件 |
| APK 安装 | 从电脑端安装 APK 到手机 |
| 应用管理 | 管理手机上的应用（启动/卸载等） |
| 推送网页 | 将网页链接推送到手机端打开 |
| 设置 | 连接配置、主题切换等 |

> 投屏与反向控制功能已迁移到独立的网页程序 `desktop/web_pcscreen.py`（端口 1845）。

### Android 端核心服务

- **PhoneHubService** — 前台保活服务（dataSync 类型），维持与电脑的持久连接
- **ScreenCaptureService** — 屏幕截图/投屏服务（MediaProjection）
- **LocationService** — GPS 定位服务
- **NotificationListener** — 通知监听服务，将手机通知转发到电脑
- **PhoneHubAccessibilityService** — 无障碍服务，支持反向控制操作
- **BootReceiver** — 开机自启接收器
- **RestartServiceReceiver** — 进程被杀后通过 AlarmManager 自动重启

### 连接方式（按优先级）

1. **ADB（USB 数据线）** — 最高优先级，通过 `adb forward` 转发端口
2. **WiFi 直连** — 局域网直接连接
3. **Cloudflare 隧道** — 通过 Cloudflare Tunnel 实现远程连接

---

## 快速开始

### 构建并运行 Android 客户端

```batch
:: 使用自带的 Gradle + JDK 构建（离线模式）
run_gradlew.bat

:: 或直接调用打包的 Gradle
call "c:\PhoneHub\gradle-dist\gradle-8.9\bin\gradle.bat" assembleDebug --no-daemon --console=plain --offline
```

构建产物输出到：`app\build\outputs\apk\debug\app-debug.apk`

APK 使用 `phonehub.keystore`（密码：`phonehub123`）签名。

### 运行桌面客户端

```batch
:: 安装依赖（建议在虚拟环境中）
pip install -r desktop\requirements.txt

:: 启动桌面应用
python desktop\main.py
```

> **注意：** `requirements.txt` 中列出的 PySide6 已过时，实际代码使用的是 PyQt5 + qfluentwidgets。请确保安装了 PyQt5 相关依赖。

---

## 通信架构

```
┌──────────────┐         HTTP (Flask :58627)        ┌──────────────┐
│  Android App │  ◄──────────────────────────────►  │  Desktop App │
│  (Kotlin)    │    /api/poll, 文件上传/下载,        │  (Python)    │
│              │    剪贴板同步, 截屏流, 远程命令       │              │
└──────────────┘                                    └──────────────┘
```

- 桌面端 Flask 服务器监听端口 **58627**
- 手机端通过 HTTP 轮询 `/api/poll` 获取指令
- 支持文件上传/下载、剪贴板同步、屏幕截图流、远程命令执行
- 连接令牌（Token）默认为 `541881452418845`，可从 `settings.json` 加载

---

## 项目目录结构

```
PhoneHub/
├── app/                          # Android 客户端
│   ├── src/main/java/com/phonehub/  # Kotlin 源码
│   ├── src/main/res/             # 资源文件（多语言、布局、图标等）
│   └── build.gradle.kts          # 模块构建配置
├── desktop/                      # 桌面客户端
│   ├── main.py                   # 入口（系统托盘 + QApplication）
│   ├── main_window.py            # 主窗口（FluentWindow 导航 + 页面）
│   ├── connection_manager.py     # Flask 服务器 + 通道管理 + 文件/剪贴板/截屏操作
│   ├── styles.py                 # 主题管理（深色/浅色）+ 颜色常量
│   ├── web_pcscreen.py           # 独立投屏网页程序（端口 1845）
│   ├── pages/                    # 14 个功能页面
│   │   ├── dashboard.py          # 仪表盘
│   │   ├── file_transfer.py      # 文件传输
│   │   ├── clipboard_sync.py     # 剪贴板同步
│   │   ├── text_share.py         # 文字互传
│   │   ├── camera.py             # 共享摄像头
│   │   ├── notifications.py      # 通知读取
│   │   ├── location_map.py       # 移动路线图
│   │   ├── file_manager.py       # 远程文件管理
│   │   ├── apk_install.py        # APK 安装
│   │   ├── app_manager.py        # 应用管理
│   │   ├── push_web.py           # 推送网页
│   │   └── settings.py           # 设置
│   └── requirements.txt          # Python 依赖
├── jdk17/                        # 自带 JDK 17
├── android-sdk/                  # 自带 Android SDK
├── gradle-dist/                  # 自带 Gradle 8.9
├── build.gradle.kts             # 根构建配置
├── settings.gradle.kts          # Gradle 设置
├── run_gradlew.bat              # 构建脚本
├── start.bat                    # 打开 APK 输出目录
├── phonehub.keystore            # APK 签名密钥
└── AGENTS.md                    # 项目开发指南
```

---

## 技术依赖

### Android 端

| 库 | 版本 | 用途 |
|----|------|------|
| Kotlin | 1.9.23 | 编程语言 |
| Kotlinx Coroutines | 1.7.3 | 异步编程 |
| Kotlinx Serialization | 1.6.2 | JSON 序列化 |
| Ktor | 2.3.7 | HTTP 客户端 |
| OkHttp | 4.12.0 | HTTP 引擎 |
| Coil | 2.5.0 | 图片加载 |
| CameraX | 1.3.1 | 摄像头 |
| Material Components | 1.12.0 | UI 组件 |

### 桌面端

| 库 | 用途 |
|----|------|
| PyQt5 | GUI 框架 |
| qfluentwidgets | Fluent Design UI 组件库 |
| Flask | HTTP 后端服务器 |
| psutil | 系统进程信息 |
| requests | HTTP 请求 |
| Pillow | 图像处理 |
| mss | 屏幕截图 |

---

## 开发说明

- 项目**未使用版本控制**，无 CI/CD 流水线
- 项目中**没有测试**，未配置测试框架
- 代码注释和 UI 字符串使用中文
- 桌面端数据持久化使用 JSON 文件，存储在 `~/PhoneHub/data/`
- 主题支持深色/浅色模式切换
- Python 代码遵循 `snake_case` 命名，Kotlin 代码遵循 `PascalCase`/`camelCase` 命名

详细开发规范请参考 [AGENTS.md](AGENTS.md)。
