# HISTORY.md — PhoneHub

## 2026-09-12：屏幕镜像（Screen Mirroring）并入主工程

将 `cs_apps/Screen_mirroring/` 验证过的投屏能力移植进主工程 PhoneHub（原则：**换引擎、留管道**）。
该测试工程保留为参考，不再双写。

### 改动范围
- **Android（`app/src/main/java/com/phonehub/`）**
  - `connectionmanager.kt`：新增镜像配置数据类与 `MirrorConfig` StateFlow、`MIRROR_*` 常量、音画模式、画质/音质档位、`screen_touch`/`screen_swipe2`/`mirror_config` 解析、`execRemoteCmds`（响应体下行反向控制命令）。
  - `phonehubaccessibilityservice.kt`：修复 EMUI 零长路径手势 bug（`moveTo`+`lineTo`）。
  - `mainactivity.kt`：重写采集引擎——队列 + 3 上传线程 + keep-alive + 音质档位 + 双锁 + 模式切换 + `rebuildMirrorDisplay`（切画质重建虚拟屏）+ `restartMirrorAudio` + `mirrorConfig` 收集器。新增 `showTapMarker` 悬浮窗点击标记。
- **Desktop（Python）**
  - 新增 `desktop/mirror_recorder.py`：`MirrorRecorder`（节拍器铺帧、音画锚点校正、切画质分段重录、补静音、输出 `~/PhoneHub/data/recordings/`）。
  - `connection_manager.py`：挂载录制 `feed_frame`/`feed_audio`；`_play_audio_data` 改为按实际上传采样率/声道动态重建 pyaudio 流；新增 `start/stop_mirror_recording`、`is_mirror_recording`、`mirror_recording_status` 信号、`_perform_screen_swipe2`、`adjust_mirror_offset`。
  - `pages/screen_mirror.py`：`MirrorCanvas` 改用 PIL LANCZOS 高质量缩放；`MirrorWindow` 支持两点滑动（右键 / 「两点滑动」开关）与方向键校准；页面新增「● 录制」按钮（默认不录制）、「两点滑动」开关、录制状态提示；投屏默认开启手机→电脑音频。

### 构建
- `gradle assembleDebug --no-daemon` 通过（BUILD SUCCESSFUL），产物 `app/build/outputs/apk/debug/app-debug.apk`。
- 注意：`C:\PhoneHub` 是指向 `F:\PhoneHub` 的装入点（junction）；从 `F:\PhoneHub` 运行 gradle 可避免增量缓存的根路径不匹配（C: vs F:）。

### 后续
- 未做真机测试安装（按需求只编译 APK）。
- 多 PC 扇出（fan-out）按计划 CUT，主工程为 1:1。

---

## 2026-09-12（下午）：投屏初始化失败加固 —— 「mediaProjection 前台服务未生效」

用户复报 `初始化投屏失败: Media projections require a foreground service of type
ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`。**先取真机事实，再动手**：

- 机型荣耀 9X（HLK-AL10）/ Android 10 (API 29)，应用 targetSdk=36。
- 用 `adb pull` 取出手机上已安装的 base.apk，**md5 与本地 13:52 那次构建完全一致**，
  `aapt2` 确认包里 `ScreenCaptureService` 有 `foregroundServiceType=mediaProjection`，
  `FOREGROUND_SERVICE` 等权限已授予 —— 即「修复包」确实已经在机器上。
- 手机内 `/sdcard/Android/data/com.phonehub/files/log/PHlog.txt`（`writeLog` 每行 flush）
  **自 13:01 起没有任何 SCR 行**，说明这段时间从未真正跑过投屏初始化，
  所以那条 toast 不可能由当前安装的包产生 —— 等于「修复包已装，但从未被验证过」。

### 根因（框架侧事实）
Android 的 `MediaProjectionManagerService.MediaProjection.start()` 放行条件里，
`mActivityManagerInternal.hasRunningForegroundService(uid, FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)`
查的是 **AMS 里的 ServiceRecord**，不是应用自己的标志位。原实现有两个缺口：
1. `ScreenCaptureService.start()` 见到 `isRunning` 直接 return；
2. `startForeground()` 只在 `onCreate()` 调过一次。

服务一旦被系统降级（EMUI 省电策略、用户从状态栏撤销投屏、内存回收等），本地
`isRunning/foregroundStarted` 仍是 true，于是既不重申前台状态也不再拉起服务，
`getMediaProjection()` 就被系统以那条 SecurityException 拒掉。

### 改动（`app/src/main/java/com/phonehub/`）
- **`screencaptureservice.kt`**
  - 新增幂等的 `ensureForeground()`：每次都真调
    `startForeground(id, notif, FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)`，
    故意不做「已就绪就跳过」的短路（标志位可能是过期的）。
  - `onCreate()` / `onStartCommand()` 都走它；`startProjection()` 在
    `getMediaProjection()` 之前**必定重申一次**。
  - `start()` 不再因 `isRunning` 直接返回：先重申前台状态，重申失败才销毁重拉。
  - `getMediaProjection()` 抛 SecurityException 时清 `foregroundStarted`、
    把完整栈写进 PHlog，并抛出带「请重启 App 后再试」提示的错误。
  - 新增诊断 `logProcessImportance()`：打印进程重要性与 `IMPORTANCE_FOREGROUND_SERVICE(125)`
    阈值，用来区分「代码问题」还是「ROM 没接受前台服务提升」。
- **`connectionmanager.kt`**：`attachScreenCaptureService` 不再以本地 `foregroundStarted`
  作放行条件（等 `instance != null && isRunning` 即调用，重申交给服务内部）；
  失败路径改用 `LogUtil.connE` 落 PHlog（原来只 `Log.e` 进 logcat，而本机 logcat 环形缓冲
  只有 64 KiB，十几分钟就滚没了，根本倒查不到）。
- **`mainactivity.kt`**：投屏初始化失败 / 无法取到投影的几条路径补 `LogUtil.scrE`，
  让 toast 文案也进 PHlog。

### 验证
- `assembleDebug` BUILD SUCCESSFUL，产物 `app/build/outputs/apk/debug/app-debug.apk`。
- 已 `adb install -r` 覆盖安装到 192.168.3.60（荣耀 9X），待用户点一次投屏验收。
  若仍失败，PHlog.txt 里会同时出现 `取投影前 进程重要性=…` 与
  `getMediaProjection 被系统拒绝（mediaProjection 前台服务未生效）` 的完整栈 ——
  前者低于阈值即可判定是 ROM 侧没放行（需去「手机管家 → 应用启动管理 → 手动管理」全开）。

### 工具链备注
沙箱 shell 缺 coreutils 的解法：把 PortableGit 的 usr/bin 放到 PATH 最前
（`/c/Users/Administrator/.workbuddy/binaries/PortableGit/versions/1.2.0/usr/bin`）。
`adb` 的 daemon 会随每次 Bash 调用被回收，所以每个命令都要先
`adb connect 192.168.3.60:5555`；抓真机数据要「一次调用里连+抓+落文件」。

---

## 2026-09-12（晚）：投屏**按原样重新移植**（放弃"换引擎、留管道"）

用户更正了移植要求：**除了"手机功能嵌进手机 app 一个页面 / 电脑程序嵌进电脑 app 一个界面"，
其余一律不变——包括端口**。原来的实现把投屏协议嫁接到了 58627（`/api/phone_frame?type=mirror`、
`/api/phone_audio`、`mirror_start/mirror_config/screen_touch/screen_swipe2`），不符合要求。
用户明确表示允许电脑端开两个端口。

### 现状（重移后）
| | 文件 | 与参考工程的关系 |
|---|---|---|
| 手机端引擎 | `app/src/main/java/com/phonehub/phonemirrorservice.kt` | ≈ `MirrorService.kt` 逐行搬入，协议/档位/采集/内录/反控/`mirror_prefs` 持久化全保留 |
| 手机端界面 | `res/layout/page_screen_mirror.xml` + `MainActivity`（页面 8） | 参考工程 `activity_main.xml` 的控件搬进来 |
| PC 端程序 | `desktop/mirror_server.py`、`desktop/live_window.py` | ≈ `pc/server.py`、`pc/live_window.py` 逐行搬入 |
| PC 端界面 | `desktop/pages/screen_mirror.py` | 5423 服务的控制台（启停服务/开窗口/轮询 `/status`/录制） |

### 端口与协议（与参考工程一致）
- 投屏走 **5423**（`PHONEHUB_MIRROR_PORT` 可覆盖）：`POST /upload`（头 `X-Capture-Ts/X-Seq/X-Ctrl`）、
  `POST /audio_start?rate&ch&bits`、`POST /audio`（头 `X-Audio-Seq`）、`POST /stop`；
  PC 侧路由 `/start /stop /audio_start /audio /audio_stop /audio_stream /remote_tap /remote_swipe
  /record/start /record/stop /upload /status /output/<f> /stream /frame.jpg /live`。
- 主工程原有的 58627 通道只保留「投屏控制」这一条：电脑端点「手机投屏到电脑」→
  `send_action("mirror_start", {mirror_ip, mirror_port})` → 手机走和手机页按钮同一条路
  （没授权就先弹系统授权框）。反控不再经 58627，由 PC 在 `/upload` 响应里带 `cmds` 下行。
- 桌面 app 现在共 3 个监听端口：58627（主服务）、5423（投屏）、1845（web_pcscreen）。

### 手机端适配点（协议未动，只改"接缝"）
1. 包名/类名/action：`com.ph.mirror.*` → `com.phonehub`、`MirrorService` → `PhoneHubMirrorService`、
   `com.phonehub.mirror.ACTION_*`。
2. 无障碍：参考工程自带的 `MirrorAccessibilityService` **不搬**（全 app 只留一个无障碍服务），
   改用主工程 `PhoneHubAccessibilityService.instance.performTap/performSwipe`。
3. 状态回传：参考工程用 `LocalBroadcastManager` 广播 → 改成 companion 里的
   `resultFlow`/`fpsFlow`（`MutableStateFlow`），省掉一个 androidx 依赖。
4. 日志：关键节点（服务创建/开始投屏/无授权/取投影被拒）额外写 `LogUtil`，落 PHlog.txt 便于真机排查。
5. Manifest：新增 `PhoneHubMirrorService`（`foregroundServiceType="mediaProjection"`，通知渠道
   `mirror_service`/id 9001，与截图用的 3001 不冲突）；补 `POST_NOTIFICATIONS` 权限（Android 13+）。
6. 手机页不再"进页面自动开投屏"：改成点「开始投屏」（参考工程是显式按钮），
   另外电脑端可以远程触发同一条路。

### PC 端适配点
1. `mirror_server.py`：内容与参考 `server.py` 一致，只在末尾追加 `start_server_thread()` /
   `stop_server()` / `is_server_running()` / `open_live_window()`，把 `app.run` 包进 `__main__`
   （直接 `python mirror_server.py` 行为不变）；并给 `_open_live_window` 加了 `force` 参数
   （用户显式点按钮时忽略"只弹一次"和 `PHONEHUB_LIVE_WINDOW=0`）。
2. **顺手修了参考工程一个缺陷**：`_finish_audio_locked()` 里漏了 `global _audio_arrivals`，
   导致收尾永远抛 `UnboundLocalError: local variable '_audio_arrivals' referenced before assignment`
   —— wav/mp3 不产出、mp4 也没有音轨。补上声明后音轨合并恢复正常（冒烟已验证）。
3. `pages/screen_mirror.py` 重写：删掉 PyQt 的 `MirrorCanvas`/`MirrorWindow`（原来那套 58627 画面
   显示 + 反控），换成 5423 服务控制台；保留音量/静音/锁屏/返回/主屏/最近/通知栏/控制中心/截图
   与「推流电脑画面到手机」。
4. `connection_manager.py` 移除投屏专用路径：`/api/phone_frame?type=mirror` 分支、`/api/phone_audio`、
   `start/stop_phone_mirror`、`start/stop_phone_audio`、`_play_audio_data`、
   `start/stop_mirror_recording`、`is_mirror_recording`、`mirror_recording_status` 信号、
   `_perform_screen_touch/_perform_screen_swipe2/adjust_mirror_offset`；`mirror_recorder.py` 移入备份。
   `/api/phone_frame?type=camera`（摄像头预览）与截图链路保持不变。
5. 桌面 app 新增 `mirror_server.py`/`live_window.py` 依赖（flask/cv2/numpy/PIL/requests/sounddevice/
   tkinter/pythonw）——本机 python38 已全部具备。

### 验证
- PC 侧冒烟（合成数据，脚本 `_diag/smoke_mirror_server.py`）：起服务 → `/start` → 推 10 帧 →
  `/remote_tap`+`/remote_swipe` 入队并在 `/upload` 响应里带出 `cmds` → `/audio_start`+`/audio` →
  `/status`、`/frame.jpg`、`/live`、`/stream` → `/stop` 收尾产出
  `mirror_*.mp4`（含音轨）+ `audio_*.wav`/`mp3` → 停服务。**全部通过**。
- 手机端：`assembleDebug` 编译通过（见下），真机验收由用户做。
- 备份：改动前把将删/大改的文件整体备份到 `_backup_20260912_mirror_reimport/`
  （`mirror_recorder.py` 在其中的 `removed_from_tree/`）。
