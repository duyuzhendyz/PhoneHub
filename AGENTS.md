# AGENTS.md — PhoneHub

## Project Overview

PhoneHub is a phone-PC management suite with two components:
- **Android App** (`app/`): Built from smali bytecode via apktool (NOT compiled Kotlin). The `.kt` files in `app/src/main/kotlin/` are decompiled reference code.
- **Desktop App** (`desktop/`): Python 3.8+ with PyQt5 + qfluentwidgets GUI and a Flask HTTP backend for phone communication.

The project bundles its own JDK 17, Android SDK, Gradle 8.9, and apktool. No external toolchain needed.

---

## Build & Run Commands

### Android App (Gradle + apktool)

```batch
:: Full debug build (uses bundled gradle + JDK)
gradlew.bat assembleDebug

:: Convenience script (sets JAVA_HOME, uses bundled gradle)
run_gradlew.bat

:: Build output
:: app\build\outputs\apk\debug\app-debug.apk

:: Install to connected device via ADB
install.bat
```

**Important:** Standard AGP compile/kotlin tasks are DISABLED. The build uses custom `apktoolAssembleDebug` and `signDebugApk` tasks defined in `app/build.gradle.kts`.

### Desktop App (Python)

```batch
:: Install dependencies (use a venv)
pip install -r desktop\requirements.txt

:: Run the desktop app
python desktop\main.pyw
```

### Tests

There are NO tests in this project. No test framework is configured for either the Android or Python components. If you add tests:
- Python: Use `pytest` — run with `pytest desktop/tests/` or a single test with `pytest desktop/tests/test_file.py::test_function`
- Android: Not applicable (smali-based build, no compilable source)

### Linting / Formatting

No linting or formatting tools are configured. No `.editorconfig`, `ruff`, `black`, `flake8`, `ktlint`, or `detekt` exists.

---

## Code Style Guidelines

### Python Desktop (`desktop/`)

- **Framework:** PyQt5 + qfluentwidgets (FluentWindow, CardWidget, etc.)
- **Backend:** Flask HTTP server on port 58627 for phone-PC communication
- **Naming:**
  - `snake_case` for functions, methods, variables
  - `PascalCase` for classes
  - `UPPER_SNAKE_CASE` for constants
- **Imports:** Standard library first, then third-party (PyQt5, flask, requests, etc.), then local modules. No enforced import sorter.
- **Threading:** Use `threading.Thread(daemon=True)` for background tasks. Use PyQt5 `pyqtSignal` for cross-thread UI updates.
- **Error handling:** Bare `except Exception:` blocks are common. Prefer catching specific exceptions when adding new code. Use the project's `log()` function (defined in `connection_manager.py`) for logging.
- **Data persistence:** JSON files stored in `~/PhoneHub/data/` (settings, theme preferences).
- **Docstrings/comments:** Written in Chinese (中文). Follow this convention for consistency.
- **Encoding:** UTF-8 throughout. Files use `.pyw` extension for windowless execution.

### Kotlin / Android (`app/`)

- **Context:** The `.kt` files are decompiled code, not original source. They serve as reference only.
- **Package:** `com.phonehub`
- **Naming:** `PascalCase` classes, `camelCase` methods/properties, `SCREAMING_SNAKE_CASE` constants
- **Architecture:** Singleton via `companion object` (e.g., `ConnectionManager.INSTANCE`)
- **State:** Kotlin `StateFlow`/`MutableStateFlow` for reactive state
- **Coroutines:** Heavy use of `suspend` functions and coroutine lambdas
- **UI:** Programmatic view construction (no XML layouts), `AppCompatActivity`
- **Error handling:** `try/catch` with empty catch blocks; `Log.e()` for logging
- **UI strings:** Chinese (中文)

### Smali / APK Decoded (`apk_decoded/`)

- Contains decompiled smali bytecode and resources from the original APK
- Used by apktool during the build process
- Do not edit smali files unless you understand the bytecode format

---

## Architecture Notes

### Communication Protocol

Phone connects to PC via HTTP (Flask server). Connection channels by priority:
1. **ADB** (USB) — highest priority, uses `adb forward`
2. **WiFi** (direct LAN connection)
3. **None** (disconnected)

API endpoints include `/api/poll`, file upload/download, clipboard sync, and remote command execution.

### Key Python Files

| File | Purpose |
|------|---------|
| `main.pyw` | Entry point, system tray, QApplication setup |
| `main_window.py` | `MainWindow(FluentWindow)` — navigation, pages, status bar |
| `connection_manager.py` | Flask server, channel management, file/clipboard/screen operations |
| `styles.py` | Theme management (dark/light), color constants, Windows dark title bar |
| `pages/*.py` | 14 feature page widgets (dashboard, file_transfer, screen_mirror, etc.) |

### Signing

The Android APK is signed with `jarsigner` using `phonehub.keystore` (password: `phonehub123`). This is a debug keystore.

---

## Common Pitfalls

1. **Do NOT try to compile the `.kt` files** — they are decompiled reference code, not compilable source.
2. **The build is offline-first** — Gradle uses `--offline` flag. Dependencies must already be cached.
3. **`requirements.txt` is outdated** — it lists PySide6 but the code uses PyQt5 + qfluentwidgets.
4. **No git repo** — this project has no version control initialized.
5. **Bundled tools** — Always use the bundled JDK (`jdk17/`), SDK (`android-sdk/`), and Gradle (`gradle-dist/`).

---

## Adding New Features

### New Desktop Page

1. Create `desktop/pages/your_page.py` with a class inheriting from a qfluentwidgets widget (e.g., `ScrollArea`)
2. Register it in `main_window.py` by adding to the navigation and `addSubInterface()`
3. Follow existing page patterns (see `pages/dashboard.py` or `pages/settings.py`)

### New Python Dependencies

Add to `desktop/requirements.txt`. Note the PyQt5/qfluentwidgets stack — do not introduce PySide6 imports.
