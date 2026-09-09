# PhoneHub · 电脑投屏 Web 服务（web_pcscreen.py）

在浏览器里实时查看并远程操控**电脑画面**的独立 Flask 服务。

> 替代原 PhoneHub 桌面的投屏界面，专做「电脑 → 手机/浏览器」方向的画面查看 + 鼠标操控。

---

## 特点

- **轻量独立**：单独一个 Python 文件，端口 `1845`，不依赖 PhoneHub 主程序。
- **实时推帧**：浏览器打开页面即推送电脑屏幕，进入页面才开始截屏，无人观看时自动休眠，不耗 CPU。
- **流畅优先**：画面 1024 宽 + JPEG 低画质，追求低延迟高流畅度（可自行调参）。
- **双栏操控**：左侧画面 / 右侧控制面板，操作按钮不遮挡画面。
- **准星指示**：金属绿准星实时显示电脑鼠标位置，避免「看不到指针在哪」。
- **绝对定位 + 精细移动**：点画面任意处可让指针绝对跳到该点；右侧触摸板**超低灵敏度**精细移动。
- **左右键分离**：左键 / 右键按钮在准星位置执行点击，互不干扰。
- **自动开屏显键盘**：检测到首个用户访问时自动弹出 Windows 屏幕键盘（`osk.exe`）。

---

## 快速开始

```bash
# 前台运行（调试，有日志输出）
python desktop/web_pcscreen.py
```

运行后访问：

| 场景 | 地址 |
|------|------|
| 本机 | http://localhost:1845 |
| 局域网（电脑 IP 为 192.168.3.9） | http://192.168.3.9:1845 |

手机/平板浏览器直接打开即可。

---

## 开机自启

注册表 Run 键已加入（`HKCU\...\CurrentVersion\Run\PhoneHubWebScreen`），用 `pythonw.exe` 无窗口后台运行：

```
"F:\Program Files\python38\pythonw.exe" "c:\PhoneHub\desktop\web_pcscreen.py"
```

移除自启：

```powershell
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v PhoneHubWebScreen /f
```

---

## 操作说明

1. **定位准星**：点击画面任意处，或按住右侧触摸板拖动。
2. **单击**：在准星当前位置按「左键」按钮。
3. **右键**：在准星当前位置按「右键」按钮。
4. **精细移动**：触摸板灵敏度极低，便于精确点到小控件。

---

## 主要参数（文件顶部可调）

| 常量 | 默认 | 说明 |
|------|------|------|
| `PORT` | `1845` | 服务端口 |
| `MAX_WIDTH` | `1024` | 画面降采样宽度（越小越流畅，越糊） |
| `JPEG_QUALITY` | `70` | JPEG 质量（越低越流畅） |
| `FPS` | `60` | 目标帧率上限 |
| 触摸板 `scale` | `0.4` | 移动灵敏度（越小幅移越慢） |

---

## API

| 路由 | 方法 | 说明 |
|------|------|------|
| `/` | GET | 投屏网页（含操控面板） |
| `/stream` | GET | 视频流（multipart JPEG），进入即触发开屏显键盘 |
| `/api/cursor` | GET | 返回真实鼠标位置（归一化 0-1） |
| `/api/screen` | GET | 返回屏幕物理分辨率 |
| `/api/mouse` | POST | 鼠标动作（点击/绝对定位/相对移动） |

---

## 环境要求

- Windows（屏幕捕获用 `mss`，操控用 `ctypes SendInput`）
- Python 3.8+
- 依赖：`flask`、`mss`（`pip install flask mss`）