# PhoneHub 修复计划 (基于 E.yaml)

## 一、总体概述

根据对 E.yaml 的完整审查，共发现 **15** 个未完全修复的项目索引（含12个M/S问题和3个W警告），涉及手机端文件传输、投屏、声音传输、截图、摄像头、剪贴板同步及UI细节等方面。本计划按优先级排序。

---

## 二、优先级划分

### P0 - 核心功能阻塞（必须修复）
| 索引 | 问题简述 | 关联文件 |
|------|----------|----------|
| M13/S12 | 远程文件管理无法获取手机文件列表（权限/SAF缺失） | connectionmanager.kt, file_manager.py |
| M6/S10 | 投屏初始化失败（MediaProjection前台服务声明缺失） | screencaptureservice.kt, MainActivity.kt, AndroidManifest.xml |
| M8/S11 | 声音传输无权限申请导致功能不可用 | connectionmanager.kt |

### P1 - 主要功能缺陷（建议尽快修复）
| 索引 | 问题简述 | 关联文件 |
|------|----------|----------|
| M11/S7c | 截图权限每次点击都重新申请，用户体验差 | screenshotactivity.kt, connectionmanager.kt, remote_control.py |
| M12/S8a/b/c | 共享摄像头逻辑不一致且UI有冗余按钮 | camera.py, connectionmanager.kt |
| M9/S6 | 声音传输自动流程未实现完整 | screen_mirror.py, connectionmanager.kt |
| M14/S9 | 推送网页逻辑未检测手机前台状态 | push_web.py, connectionmanager.kt |

### P2 - 体验改进与UI优化（按计划修复）
| 索引 | 问题简述 | 关联文件 |
|------|----------|----------|
| M3/S3 | 重名文件处理缺少手机端弹窗选项 | connectionmanager.kt, filetransferreceiver.kt |
| M10/S7a/b | 音量调整无状态显示且有反馈循环 | screen_mirror.py, connectionmanager.kt |
| M2/S1 | 剪贴板历史记录重复记录需完整解决 | clipboard_sync.py, connectionmanager.kt |
| M7/S5 | 投屏界面多余按钮未删除 | MainActivity.kt, screen_mirror.py |
| M4/S4 | 手机端文件接收保存位置不对 + 通知缺少"开始下载"按钮 | connectionmanager.kt, filetransferreceiver.kt |
| W1 | 远程文件管理缺少排序按钮和多选操作 | file_manager.py |
| W2 | APK安装界面"选择APK安装"按钮未与拖入合并 | dashboard.py / apk_install.py |
| W3 | 手机端设置"(未知)"状态显示需处理 | settings.py |

---

## 三、详细修复方案

### P0: 核心功能阻塞问题

#### M13 / S12：远程文件管理无法获取手机文件列表

**问题分析：**
当前 `handleFileListRequest()`（connectionmanager.kt 第3188行）仅使用简单 `File(path).listFiles()`，未处理以下情况：
- Android 11+ Scoped Storage 限制，直接使用 `/sdcard/` 等路径可能返回空
- 未检查 `MANAGE_EXTERNAL_STORAGE` 权限是否在运行时已授予
- 不支持 SAF（Storage Access Framework）URI 访问

**修复方案：**

1. **connectionmanager.kt 修改：**
   - 在 `handleFileListRequest()` 中添加权限检查逻辑，使用 `ContextCompat.checkSelfPermission()` 验证 `MANAGE_EXTERNAL_STORAGE`
   - 如果权限不足，引导用户前往系统设置授予"所有文件访问权限"
   - 对于 Android 11+，优先使用 SAF URI 或公共媒体目录（如 Environment.DIRECTORY_DOWNLOADS）代替硬编码路径
   - 增加日志记录便于调试

2. **file_manager.py 修改：**
   - 当收到"权限不足"响应时，向用户提示："请先在手机端授权全部文件访问权限"
   - 对于 ADB 通道，保持现有 `adb shell ls` 方式（不受存储权限限制）

3. **AndroidManifest.xml 确认：**
   - 确保已声明 `<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" tools:ignore="ScopedStorage" />`（已存在）
   - targetSdkVersion ≥ 31 时需额外声明 `requestLegacyExternalStorage="false"`（若需兼容旧路径）

#### M6 / S10：投屏初始化失败（MediaProjection前台服务声明缺失）

**问题分析：**
L2指出"Media projections require a foreground service of type Servicelnfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION"，需要：
- 在 AndroidManifest.xml 中为投屏 Service 添加 `android:foregroundServiceType="mediaProjection"`
- 启动服务时调用 `Service.startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)`

**修复方案：**

1. **AndroidManifest.xml 修改：**
   ```xml
   <service
       android:name=".ScreenMirrorService"
       android:foregroundServiceType="mediaProjection"
       android:exported="false" />
   ```
   （注：需确认具体 Service 类名，可能是 `PhoneHubService` 或其他）

2. **screencaptureservice.kt / phonehubservice.kt 修改：**
   - 在服务 onCreate() 或 start() 方法中创建通知频道，类型重要性为 `NotificationManager.IMPORTANCE_LOW` 或 `IMPORTANCE_DEFAULT`
   - 调用 `startForeground(nid, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)`
   - 确保 notification 包含持续文字描述

3. **MainActivity.kt 检查：**
   - 移除任何可能导致服务被系统杀死的代码（如不正确的后台行为）

#### M8 / S11：声音传输权限申请缺失

**问题分析：**
M8指出"手机端当前点击声音传输，没有获取任何权限来获取媒体声音"，对应 L3 解决方案 S7a+S7b。

**修复方案：**

1. **connectionmanager.kt 修改：**
   - 在开始声音传输前，动态请求 `RECORD_AUDIO` 权限（使用 `ActivityCompat.requestPermissions`）
   - 如需要捕获系统内部音频（声韵传输），同时请求 MediaProjection 权限
   - 创建 `AudioPlaybackCaptureConfiguration` 用于捕获播放内容

2. **AndroidManifest.xml 确认：**
   - 已声明 `<uses-permission android:name="android.permission.RECORD_AUDIO />`（存在）
   - 如需后台录音，考虑添加 `android:foregroundServiceType="microphone"`

3. **添加权限检查结果回调：**
   - 权限授予后自动开始声音传输，否则提示用户并取消操作

---

### P1: 主要功能缺陷

#### M11 / S7c：手机截图权限反复申请

**问题分析：**
L4指出"单击'手机截图'按钮后，手机端出现了'是否允许...录制/投射您的屏幕'的请求，并且每次单击都需要重新授予权限"。S7c要求修复此逻辑，使授权只进行一次。

**修复方案：**

1. **connectionmanager.kt 修改：**
   - 缓存 MediaProjection token（resultCode + Intent），使用 `WeakReference<MediaProjection>` 避免内存泄漏
   - 检查是否有活跃的 projection，如有则直接使用；若无则启动 `MediaProjectionManager.createScreenCaptureIntent()`
   - Android 14+: 同一 token 只能关联一个 MediaProjection 实例，需注意生命周期

2. **screenshotactivity.kt 修改：**
   - 确保 onActivityResult 正确处理 token 并保存至 ConnectionManager.companion object

3. **remote_control.py 优化：**
   - 增加本地状态跟踪，显示当前截图权限状态（已授权/未授权）
   - 增加"授权管理"按钮引导用户查看权限设置

#### M12 / S8a+b+c：共享摄像头逻辑不完善

**问题分析：**
M12要求修改手机摄像头推送给电脑、电脑摄像头推送给手机的逻辑，并优化 UI（S8a/S8b/S8c）。当前界面可能有冗余按钮或状态不一致。

**修复方案：**

1. **camera.py 修改（S8c UI优化）：**
   - 去掉"切换手机摄像头"、"查看电脑摄像头"按钮
   - 只保留"电脑摄像头→手机"与"查看手机摄像头"两个主按钮
   - 预览手机摄像头弹窗新增"切换手机摄像头"与"停止查看手机摄像头"
   - 预览电脑摄像头弹窗新增"停止获取摄像头画面"按钮
   - 确保只有一个"停止获取摄像头画面"按钮（移除重复）

2. **connectionmanager.kt 修改（S8a/S8b逻辑）：**
   - 手机摄像头推流：更新编码策略和质量参数
   - 电脑摄像头推流：实现反向推送，增加画质自适应

#### M9 / S6：声音传输自动流程未实现

**问题分析：**
S5、S6要求点击"开始声音传输"后手机自动获取权限并开始传输，电脑自动接收播放。当前流程可能需要手动干预。

**修复方案：**

1. **connectionmanager.kt：**
   - 在接收到 PC 发送的 "start_sound_transfer" 命令时，自动触发权限请求
   - 权限授予后立即启动音频采集和传输 coroutine

2. **screen_mirror.py：**
   - 移除"开始声音传输"前的等待提示
   - 增加播放音量控制和静音状态显示（S7a）

#### M14 / S9：推送网页逻辑错误

**问题分析：**
S9要求：检测到手机端在桌面或 Phonehub 软件时，直接通过 via 浏览器打开，否则只保存历史并放剪贴板。

**修复方案：**

1. **push_web.py 修改：**
   - 输入 URL 后先向手机发送查询命令（check_app_foreground）
   - 收到手机响应后决定操作方式：
     - 若 PhoneHub 在前台 → 通过 direct link 推送，手机直接打开
     - 否则 → 将 URL 放入剪贴板 + 历史记录

2. **connectionmanager.kt 支持：**
   - 添加 `check_app_foreground()` 方法返回布尔值
   - 处理来自 PC 的 URL 推送时，根据此标志选择路径

---

### P2: 体验改进与UI优化

#### M3 / S3：重名文件处理缺少弹窗

**修复方案：**
- connectionmanager.kt 收到重名文件通知时，弹出对话框提供三个选项
- 选项文本严格按 S3 描述：覆盖原有文件、添加编号接收（如 a_1.txt）、取消接收
- 手机端需在通知中同样提供操作按钮

#### M10 / S7a+b：音量调整反馈问题

**修复方案：**
- S7a：静音时电脑端应显示状态图标（如扬声器带斜杠），而非无声无息
- S7b：手机自动调整音量时，传输到电脑的音量设置不应立即回传；需引入延迟和去抖逻辑，避免横跳

#### M2 / S1：剪贴板历史记录重复记录

**修复方案：**
- clipboard_sync.py 中 `_refresh_history_view()` 已过滤非本机源（source != "本机"），但需确认手机端也做了相同过滤
- 检查是否存在"接收完成"这类重复条目的生成源头（connectionmanager.kt 中的消息处理逻辑）

#### M7 / S5：投屏界面多余按钮

**修复方案：**
- MainActivity.kt：删除"投屏+反向控制"内的"启动推流"按钮
- screen_mirror.py：删除"投屏与反向控制"内的"打卡投屏窗口"按钮
- 确保界面简化为单一"投屏"按钮，点击后自动完成权限请求和投屏启动

#### M4 / S4：文件接收保存位置和通知

**修复方案：**
- filetransferreceiver.kt 和 connectionmanager.kt：接收文件默认保存到 `/sdcard/Download/` 而非 `/Android/data/.../Downloads/PhoneHub/`
- 通知中包含明确的"开始下载"和"取消下载"两个按钮，用户点击后才真正开始下载

#### W1：远程文件管理UI不完整

**修复方案：**
- file_manager.py 树形控件表头增加"排序"按钮（可按名称、大小、时间排序）
- 第一行仅显示"电脑文件"选择（隐藏手机文件选项）
- 增加右键长按多选菜单，启用禁用操作按钮（打开、下载、删除、重命名、属性、复制到）

#### W2：APK安装界面按钮合并

**修复方案：**
- dashboard.py 或 apk_install.py 中合并"选择APK安装"按钮与拖放区域
- 创建一个新组件：既能拖入文件也能点击选择文件

#### W3：手机端设置标签问题

**修复方案：**
- settings.py 第163行标题"手机端设置"已符合要求 ✓
- 但需检查 `self.phone_status_label`（第171行）在未连接时显示的"(未知)"：
  - 若有对应逻辑则修复（显示正确状态如"已连接/未连接"）
  - 若无逻辑则删除该标签或置为空字符串

---

## 四、实施顺序建议

1. **第一阶段（P0 - 阻塞性问题）**
   - M13/S12：文件列表权限处理（影响文件管理核心功能）
   - M6/S10：投屏前台服务（投屏功能必须的基础）
   - M8/S11：声音传输权限（声音功能可用性的前提）

2. **第二阶段（P1 - 主要功能）**
   - M11/S7c：截图权限缓存（高频使用功能的体验关键）
   - M12/S8a/b/c：共享摄像头（重要多态功能）
   - M9/S6：声音传输自动流程（完善P0修复的功能链）
   - M14/S9：推送网页业务逻辑（增强智能性）

3. **第三阶段（P2 - 体验优化）**
   - M3/S3：重名文件处理（完整文件传输闭环）
   - M10/S7a+b：音量交互优化（音视频体验）
   - M2/S1：剪贴板去重（数据一致性）
   - M7/S5：投屏界面精简（UI规范）
   - M4/S4：文件下载通知机制（完整S4流程）
   - W1/W2/W3：UI细节修正（产品化收尾）

---

## 五、验证方法

每个修复完成后需执行：
1. **静态检查**：确认相关代码引用 E.yaml 中描述的解决方案特征
2. **功能测试**：双端连接后逐项复现原问题场景
3. **回归测试**：确保修改不影响已有功能运行
4. **编译验证**：Android 端使用 `run_gradlew.bat` 编译无错；Python 端通过 `python -m py_compile` 语法检查