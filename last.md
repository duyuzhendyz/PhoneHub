# PhoneHub - E1.yaml 待完成任务清单 (Last.md)

## 任务概览

以下为在打包 APK 前仍需修复/完善的遗留问题，按优先级排序：

---

## 🔴 P0 — 必须修复才能打包的阻塞项

### M13: 远程文件管理无法获取手机文件列表（已授权）

**问题描述：**
- ADB/WiFi 通道下，FileManagerPage 都无法正常列出手机文件
- 即使授予了权限，依然无响应或显示错误

**涉及文件：**
- desktop/pages/file_manager.py
- desktop/connection_manager.py
- app/src/main/java/com/phonehub/connectionmanager.kt

**需排查点：**
1. ADB 通道：adb_list_files() 调用的 adb_command('shell', '-la', path) 是否正确返回结果？是否因 ADB 设备未识别、路径不存在或权限不足导致空返回？
2. WiFi 通道：发送 file_list_request 后，手机端是否正确响应 file_list action？file_list_received 信号是否正确发射并被 _populate_from_json 接收？
3. 检查 ConnectionManager 中是否有对应 Flask endpoint /api/file_list 处理请求并返回 JSON
4. 检查手机端是否在收到 file_list_request 后正确枚举目录并以 JSON 格式回传

**建议修复方向：**
- 在 _refresh() 中增加调试日志输出当前 channel、adb_device_id、路径等
- 确保 Android 端 Manifest 有读写存储权限（已在项目声明中）
- 若使用 API 29+，检查 scoped storage 限制影响 /sdcard/ 访问
- WiFi 端添加错误捕获并抛错提示给用户

### M7 / S5: 手机端启动推流按钮删除 + 投屏自动弹窗完善
## 任务概览

以下为在打包 APK 前仍需修复/完善的遗留问题，按优先级排序：

---

## 🟠 P1 — 重要功能修正

### M9 / S6: 声音传输自动启动逻辑

**需求（S6）：**
- PC 点击"开始声音传输" → 手机自动获取录音权限 → 自动开始语音上传 → PC 自动播放
- **手机端全程不切换界面**，无需额外操作
- 应删除手机端"投屏+反向控制"内的"声韵传输"按钮

**当前实现缺陷：**
- _toggle_audio() 仅实现 PC→phone 推流（start_pc_audio()），缺少 phone→PC 上行链路触发
- 手机端未处理来自 PC 的音频启动指令（缺少对应的 action 处理）

**涉及文件：**
- desktop/pages/screen_mirror.py（_toggle_audio 方法）
- desktop/connection_manager.py（新增处理 phone audio start 的逻辑）
- app/src/main/java/com/phonehub/connectionmanager.kt（新增接收 PC 音频启动指令并自动请求录音权限）

**需修改内容：**
1. PC 端 _toggle_audio() 改为：
   - 启动时先向手机发送 "audio_up_start" action（自定义指令）
   - 手机收到后自动请求 RECORD_AUDIO 权限并开始录音推流
   - PC 同时启动 _phone_audio_running 接收管道和播放
2. 手机端增加指令处理和权限申请逻辑（使用 ActivityResultContracts 申请录音权限静默启动）
3. 若 S6 明确要求删除手机端"声韵传输"按钮，则在 MainActivity/kotlin 相应位置移除该按钮 UI（若存在）

### M7 / S5: 手机端启动推流按钮删除 + 投屏自动弹窗完善

**需求（S5）：**
- 删除手机端"投屏+反向控制"内的"启动推流"按钮
- PC 端删除"打卡投屏窗口"按钮（即"打开投屏窗口"）→ **已完成 PC 端 UI 修改**
- PC 点击"手机投屏到电脑"后，手机自动申请录屏权限→自动开始推流→PC 自动弹窗显示

**当前状态：**
- ✅ PC 端 screen_mirror.py 已移除 open_window_btn，hint 文字已更新
- ⏳ 手机端尚未确认是否存在同名按钮需删除；自动启动逻辑需在连接层确认

**涉及文件：**
- app/src/main/java/com/phonehub/mainactivity.kt（查找投屏相关按钮 UI）
- desktop/pages/screen_mirror.py（已调整）

**需补充：**
- 检查 MainActivity 中是否有"启动推流"按钮（可能在 XML 布局或动态创建），若有则删除
- 确认 _toggle_phone_mirror() 发送 mirror_start 后，手机端收到指令是否正确进入无障碍/录屏权限申请流程且不跳转其他页面
### M12 / S8a, S8b, S8c: 共享摄像头多模式推送及界面重构
### M12 / S8a, S8b, S8c: 共享摄像头多模式推送及界面重构

## 🟡 P2 — 特性增强与 UI 优化

### M14 / S9: 推送网页智能分发逻辑

**需求（S9）：**
输入网页 URL 后推送给手机：
- 若检测到手机端正在桌面或 PhoneHub 软件中（即前台为 PhoneHub），则不把 URL 复制到剪贴板，而是直接通过 via 浏览器打开，然后保存历史
- 否则（手机在其他应用或后台），只保存历史并把 URL 放到剪贴板内（由用户在手机上自行粘贴或通过其他方式处理）

**当前实现：**
- 没有检测手机前台应用状态的机制
- push_web.py 的 _send_to_phone() 统一走 send_action("open_url")，未做前端应用状态判断
- 没有检测手机前台应用状态的机制
### M7 / S5: 手机端启动推流按钮删除 + 投屏自动弹窗完善

### M12 / S8a, S8b, S8c: 共享摄像头多模式推送及界面重构

## 📝 附：已完成项回顾

以下任务已在本次工作中完成，可在最后验证后打包后验证：
| 模块 | 变更内容 | 验证方式 |
|------|----------|----------|
| screen_mirror.py | 移除 open_window_btn，更新 hint 文字 | Python compile OK，UI 无引用残留 |
| camera.py | 移除 switch_btn & view_pc_cam_btn，修复语法与缩进 | Python compile OK，逻辑结构正确 |
| File Transfer (用户自述) | 待接收状态、Download 目录、去重记录、重名处理逻辑 | 需运行测试文件收发流程验证 |
| Clipboard Sync (用户自述) | 来源过滤，隐藏 TabBar 加号 | 需运行测试复制历史展示验证 |

## 🔧 打包前自查清单 (Pre-build Checklist)
- [ ] M13: ADB/WiFi 文件列表获取正常，错误提示友好
- [ ] M9/S6: 声音传输双向开启，手机端自动申请权限无界面跳变
- [ ] M7/S5: 手机端无启动推流按钮，PC 点投屏自动弹窗
- [ ] M12/S8a/b: 摄像头四种模式 UI 状态正确，无弹窗多余按钮
- [ ] M14/S9: 推送 URL 根据手机前台状态选择直开或放剪贴板
- [ ] 所有 .py 文件通过 python -m py_compile 语法检查
- [ ] Android 端无引用已移除的 Button ID（编译报错检查）
- [ ] 应用能正常启动，双端连接后各页面功能无崩溃
- [ ] APK 签名可用（keystore 密码：phonehub123）

*Last updated: 2026-07-29*
*Generated from E1.yaml remaining issues + codebase analysis
