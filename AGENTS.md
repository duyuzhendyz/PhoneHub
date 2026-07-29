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
| `pages/*.py` | 14 feature page widgets (dashboard, file_transfer, screen_mirror, etc.) |

---

## Common Pitfalls

1. **Use bundled toolchains.** Always reference `jdk17/`, `android-sdk/`, `gradle-dist/` paths directly — do not rely on system-installed JDK/SDK.
2. **`requirements.txt` is outdated.** It lists PySide6 but the code uses PyQt5 + qfluentwidgets. Do not add PySide6 imports.
3. **Flask runs on port 58627.** This is hardcoded in `connection_manager.py`.
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
