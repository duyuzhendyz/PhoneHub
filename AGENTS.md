# AGENTS.md — PhoneHub

## Project Overview

PhoneHub is a phone-PC management suite with two components:
- **Android App** (`app/`): Kotlin source in `app/src/main/java/com/phonehub/`. Package `com.phonehub`. AGP 8.5.2, compile/target SDK 36, min SDK 24, JDK 17.
- **Desktop App** (`desktop/`): Python 3.8+ with PyQt5 + qfluentwidgets GUI and a Flask HTTP backend (port 58627) for phone-PC communication.

The project bundles its own JDK 17 (`jdk17/`), Android SDK (`android-sdk/`), and Gradle 8.9 (`gradle-dist/`). No external toolchain needed.

---

## Build & Run Commands

### Android App

```batch
:: Full build (uses bundled gradle + JDK, offline mode)
run_gradlew.bat
:: or equivalently:
call "c:\PhoneHub\gradle-dist\gradle-8.9\bin\gradle.bat" assembleDebug --no-daemon --console=plain --offline

:: Build output: app\build\outputs\apk\debug\app-debug.apk

:: Open APK output directory
start.bat
```

**Note:** `gradlew.bat` is a wrapper stub. Always use `run_gradlew.bat` or the bundled Gradle directly. Standard AGP compile/kotlin tasks are available. APK is signed with `jarsigner` using `phonehub.keystore` (password: `phonehub123`).

### Desktop App (Python)

```batch
:: Install dependencies (venv recommended)
pip install -r desktop\requirements.txt

:: Run the desktop app
python desktop\main.py
```

### Tests

There are NO tests in this project. No test framework is configured. If you add tests:
- Python: Use `pytest` — run all tests with `pytest desktop/tests/`, a single test with `pytest desktop/tests/test_file.py::test_function`
- Android: Not applicable (no standard test harness configured)

### Linting / Formatting

No linting or formatting tools are configured (no `.editorconfig`, `ruff`, `black`, `ktlint`, or `detekt`). Follow the style guidelines below manually.

---

## Code Style Guidelines

### Python Desktop (`desktop/`)

- **Framework:** PyQt5 + qfluentwidgets (FluentWindow, CardWidget, etc.). Flask HTTP backend for phone communication.
- **Naming:** `snake_case` for functions/methods/variables, `PascalCase` for classes, `UPPER_SNAKE_CASE` for constants.
- **Imports:** Standard library first, then third-party (PyQt5, flask, requests, psutil, PIL, etc.), then local modules (`styles`, `connection_manager`, `pages.*`). No enforced import sorter. Group imports with blank lines between sections.
- **Threading:** Use `threading.Thread(daemon=True)` for background tasks. Use PyQt5 `pyqtSignal` + `QObject` for cross-thread UI updates.
- **Error handling:** Prefer specific exception catches (e.g., `except FileNotFoundError:`). Bare `except Exception:` blocks exist in legacy code — avoid adding new ones.
- **Logging:** Use the project's `log()` function from `connection_manager.py`. For new modules, use the `logging` module with the `"phonehub"` logger.
- **Comments/docstrings:** Written in Chinese (中文). Follow this convention. Module-level docstrings use the `"""..."""` format.
- **Encoding:** UTF-8 throughout. Entry point uses `.pyw` extension (windowless), but `main.py` is the actual runner.
- **Data persistence:** JSON files in `~/PhoneHub/data/` (settings, theme).
- **UI styling:** Colors via `_c()` from `styles.py`. Themes: dark/light managed by qfluentwidgets `qconfig.theme`.

### Kotlin Android (`app/src/main/java/com/phonehub/`)

- **Package:** `com.phonehub`
- **Naming:** `PascalCase` classes, `camelCase` methods/properties, `SCREAMING_SNAKE_CASE` constants.
- **Architecture:** Singleton via `companion object` (e.g., `ConnectionManager.INSTANCE`). `AppCompatActivity` for UI activities.
- **State:** Kotlin `StateFlow`/`MutableStateFlow` for reactive state. `CoroutineScope` + `launch`/`async` for async work.
- **Coroutines:** Heavy use of `suspend` functions and coroutine builders (`launch`, `async`, `runBlocking`).
- **Networking:** Ktor client (`io.ktor`) with JSON serialization via `kotlinx-serialization`. OkHttp as engine.
- **Camera/Media:** CameraX (`camera-core`, `camera-view`, `camera-camera2`). MediaProjection for screen capture.
- **Error handling:** `try/catch` with minimal logging. `Log.e()` for error output. Empty catch blocks exist in legacy code — avoid adding new ones.
- **Strings:** Chinese (中文) for UI strings.
- **Dependencies:** Kotlin 1.9.23, Coroutines 1.7.3, Ktor 2.3.7, Coil 2.5.0, Material 1.12.0.

---

## Architecture Notes

### Communication Protocol

Phone connects to PC via HTTP (Flask server). Connection channels by priority:
1. **ADB** (USB) — highest priority, uses `adb forward`
2. **WiFi** (direct LAN connection)
3. **None** (disconnected)

API endpoints: `/api/poll`, file upload/download, clipboard sync, remote command execution, screen capture stream.

### Key Python Files

| File | Purpose |
|------|---------|
| `main.py` | Entry point, system tray, QApplication setup |
| `main_window.py` | `MainWindow(FluentWindow)` — navigation, pages, status bar |
| `connection_manager.py` | Flask server, channel mgmt, file/clipboard/screen operations, `log()` utility |
| `styles.py` | Theme management (dark/light), color constants `_c()`, Windows dark title bar |
| `pages/screen_mirror.py` | 投屏与反向控制页面：5423 投屏服务的控制台（启停服务/打开「手机屏幕」窗口/轮询 `/status`/录制开关）+ 手机遥控与「推流电脑画面到手机」 |
| `mirror_server.py` | **照搬自 `cs_apps/Screen_mirroring/pc/server.py`**：监听 5423，收手机 JPEG/PCM、录制 mp4+mp3、反控命令下行、`/live` 网页；桌面 app 用 `start_server_thread()`/`stop_server()` 启停 |
| `live_window.py` | **照搬自参考工程**：tkinter「手机屏幕」窗口（画面/声音/录制/两点滑动/方向键校准），由上面那个服务拉起 |
| `pages/*.py` | 14 feature page widgets (dashboard, file_transfer, screen_mirror, etc.) |

---

## Screen Mirroring Module（投屏与反向控制）

**整段照搬自 `cs_apps/Screen_mirroring/`**：除了"嵌进两端的界面"，**端口与协议一律不变**。
手机端引擎 = `app/src/main/java/com/phonehub/phonemirrorservice.kt`（≈参考工程 `MirrorService.kt`）；
PC 端程序 = `desktop/mirror_server.py` + `desktop/live_window.py`（≈参考工程 `pc/server.py`、`pc/live_window.py`）。

### 端口与进程模型
- 手机→电脑投屏走 **5423**（`PHONEHUB_MIRROR_PORT` 可覆盖），由 `mirror_server.py` 在桌面 app 进程内以
  线程方式启停（`start_server_thread()` / `stop_server()` / `open_live_window()`）。
- 桌面 app 自有的手机连接通道仍是 **58627**（`connection_manager.py`；另有 `web_pcscreen.py` 的 1845）。
  **投屏不再经过 58627** 的帧/音频接口。
- `live_window.py` 是独立 tkinter 进程（"手机屏幕"窗口）：画面 `/stream`、声音 `/audio_stream`、录制开关、
  两点滑动、方向键校准都在它里面；服务开始录制时会自动拉起，投屏页也能手动打开。
- 手机端 `PhoneHubMirrorService` 自带 MediaProjection 管理（`mirror_prefs` 存授权/档位/电脑列表），
  与截图用的 `ScreenCaptureService` 并存互不干扰。

### 协议（与参考工程一字不改）
- 手机 → PC：`POST /upload`（jpeg，头 `X-Capture-Ts`/`X-Seq`/`X-Ctrl`）、`POST /audio_start?rate=&ch=&bits=`、
  `POST /audio`（PCM，头 `X-Audio-Seq`）、`POST /stop`。
- PC 路由全集：`/start /stop /audio_start /audio /audio_stop /audio_stream /remote_tap /remote_swipe
  /record/start /record/stop /upload /status /output/<f> /stream /frame.jpg /live /`。
- 反向控制：PC 把 `{"cmds":[{id,type:"tap"|"swipe",fx,fy[,fx2,fy2,ms],offx,offy}]}` 搭 `/upload`、`/audio`
  的响应顺风车下发（延迟 ≤ 一帧、无 ACK）；手机端解析后调
  `PhoneHubAccessibilityService.performTap/performSwipe`（EMUI 零长路径已用 moveTo+lineTo 规避）。
- 电脑端发起投屏：`send_action("mirror_start", {mirror_ip, mirror_port})`（58627 通道，只传地址），
  手机端收到后走和手机页按钮同一条路（没有授权就先弹系统授权框）。

### 两端界面挂载点
- **手机**：`res/layout/page_screen_mirror.xml`（MainActivity 页面 index 8）= 参考工程 MainActivity 的控件
  （电脑 IP/端口、清晰度/音质/模式下拉、开始/停止投屏、无障碍设置/清除授权/断开所有、状态、FPS）；
  状态与帧率通过 `PhoneHubMirrorService.resultFlow` / `fpsFlow` 回传（取代参考工程的 LocalBroadcastManager）。
- **电脑**：`pages/screen_mirror.py` = 5423 服务控制台（启停服务、打开窗口/`/live`、轮询 `/status` 显示
  录制/帧率/连接态、录制开关），并保留原有手机遥控（音量/静音/锁屏/返回/主屏/最近/通知栏/控制中心/截图）
  与「推流电脑画面到手机」。原先那套 PyQt 投屏窗口（`MirrorWindow`/`MirrorCanvas`）已删除。

### 录制输出
- 目录：`desktop/output/`（`mirror_server.OUTPUT_DIR`，跟着脚本目录走）。
- 产物：`mirror_YYYYMMDD_HHMMSS.mp4`（定 60fps 节拍器铺帧 + 内录音轨）、`audio_*.pcm/wav/mp3`。
- 默认**不录制**：手机只管推流，录不录由 PC 的 `/record/start|stop`（窗口按钮或投屏页按钮）决定。

---

## Common Pitfalls

1. **Use bundled toolchains.** Always reference `jdk17/`, `android-sdk/`, `gradle-dist/` paths directly — do not rely on system-installed JDK/SDK.
2. **`requirements.txt` is outdated.** It lists PySide6 but the code uses PyQt5 + qfluentwidgets. Do not add PySide6 imports.
3. **端口**：桌面 app 自有的手机通道是 **58627**（`connection_manager.py`，硬编码）；**手机→电脑投屏是 5423**
   （`mirror_server.py`，端口与协议照搬 `cs_apps/Screen_mirroring`，可用 `PHONEHUB_MIRROR_PORT` 覆盖）；
   另有 `web_pcscreen.py` 的 1845（电脑→浏览器）。三者互不相干，别把投屏流量接到 58627 上。
4. **Secret token** is in `connection_manager.py` (default: `"541881452418845"`), loaded from `settings.json` if present.
5. **No version control** is initialized. No CI/CD pipeline.

---

## Adding New Features

### New Desktop Page

1. Create `desktop/pages/your_page.py` with a class inheriting from a qfluentwidgets widget.
2. Import and register in `main_window.py`: add to navigation with `self.navigationInterface.addItem()` and `self.addSubInterface()`.
3. Follow existing page patterns (see `pages/dashboard.py` or `pages/settings.py`).

### New Android Feature

1. Add Kotlin source in `app/src/main/java/com/phonehub/`.
2. Register Activities/Services in `app/src/main/AndroidManifest.xml`.
3. Rebuild with `run_gradlew.bat`.
