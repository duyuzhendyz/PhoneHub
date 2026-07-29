package com.phonehub

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务：
 * - 媒体键注入（播放/暂停/上一首/下一首）
 * - 全局键（锁屏/最近任务/通知栏）
 * - 提供给 ConnectionManager 调用执行反向控制
 *
 * Manifest 已声明该服务，需用户在系统设置中开启。
 */
class PhoneHubAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "PHAccessibility"
        @Volatile
        var instance: PhoneHubAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LogUtil.accI("无障碍服务已连接")
        val sdkInt = android.os.Build.VERSION.SDK_INT
        LogUtil.accI("设备信息: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, API $sdkInt")
        LogUtil.accI("服务配置: ${packageName}")
        
        // 检查全局操作支持情况
        LogUtil.accI("支持的全局操作:")
        LogUtil.accI("  GLOBAL_ACTION_BACK: $GLOBAL_ACTION_BACK")
        LogUtil.accI("  GLOBAL_ACTION_HOME: $GLOBAL_ACTION_HOME")
        LogUtil.accI("  GLOBAL_ACTION_RECENTS: $GLOBAL_ACTION_RECENTS")
        LogUtil.accI("  GLOBAL_ACTION_NOTIFICATIONS: $GLOBAL_ACTION_NOTIFICATIONS")
        LogUtil.accI("  GLOBAL_ACTION_QUICK_SETTINGS: $GLOBAL_ACTION_QUICK_SETTINGS")
        LogUtil.accI("  GLOBAL_ACTION_LOCK_SCREEN: $GLOBAL_ACTION_LOCK_SCREEN")
        
        // 检查dispatchGesture支持
        LogUtil.accI("dispatchGesture 可用性: ${if (sdkInt >= 26) "支持(API26+)" else "可能不支持(API<26)"}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 当前不需要响应事件，仅用于执行全局手势
    }

    override fun onInterrupt() {
        LogUtil.accW("无障碍服务被中断")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        LogUtil.accW("无障碍服务断开")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        LogUtil.accI("无障碍服务已销毁")
    }

    /** 媒体键 */
    fun performMediaKey(keyCode: Int) {
        val keyName = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "PLAY_PAUSE"
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "PREVIOUS"
            KeyEvent.KEYCODE_MEDIA_NEXT -> "NEXT"
            else -> "UNKNOWN($keyCode)"
        }
        LogUtil.accI("执行媒体键: $keyName")
        // 模拟媒体键：通过 AudioManager dispatchMediaKeyEvent（部分 ROM 需系统签名）
        // 这里使用无障碍全局快捷键 fallback
        try {
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    // 使用 GestureDescription 暂无媒体键映射，改用 AudioManager 派发
                    dispatchMediaKeyViaAudio(keyCode)
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> dispatchMediaKeyViaAudio(keyCode)
                KeyEvent.KEYCODE_MEDIA_NEXT -> dispatchMediaKeyViaAudio(keyCode)
            }
            LogUtil.accI("媒体键发送成功: $keyName")
        } catch (e: Exception) {
            LogUtil.accE("performMediaKey 失败: $keyName", e)
        }
    }

    private fun dispatchMediaKeyViaAudio(keyCode: Int) {
        try {
            val am = getSystemService(AUDIO_SERVICE) as? android.media.AudioManager
            if (am == null) {
                LogUtil.accE("无法获取 AUDIO_SERVICE")
                return
            }
            @Suppress("DEPRECATION")
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            @Suppress("DEPRECATION")
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            LogUtil.accD("AudioManager 媒体键派发成功: $keyCode")
        } catch (e: Exception) {
            LogUtil.accE("dispatchMediaKey 失败", e)
        }
    }

    /**
     * 执行全局操作公共方法
     */
    private fun performGlobalActionSafe(action: Int, actionName: String) {
        LogUtil.accI("执行全局操作: $actionName")
        try {
            val success = performGlobalAction(action)
            LogUtil.accI("${actionName}操作结果: $success")
            if (!success) {
                LogUtil.accW("${actionName}操作不被支持")
            }
        } catch (e: Exception) {
            LogUtil.accE("performGlobalAction [$actionName] 失败", e)
        }
    }

    /** 锁屏 */
    fun performGlobalLock() {
        performGlobalActionSafe(GLOBAL_ACTION_LOCK_SCREEN, "锁屏")
    }

    /** 回到桌面 */
    fun performHome() {
        performGlobalActionSafe(GLOBAL_ACTION_HOME, "Home键")
    }

    /** 最近任务 */
    fun performRecents() {
        performGlobalActionSafe(GLOBAL_ACTION_RECENTS, "最近任务")
    }

    /** 通知栏 */
    fun openNotifications() {
        performGlobalActionSafe(GLOBAL_ACTION_NOTIFICATIONS, "通知栏")
    }

    /** 控制中心（快速设置面板） */
    fun openQuickSettings() {
        performGlobalActionSafe(GLOBAL_ACTION_QUICK_SETTINGS, "控制中心")
    }

    /** 模拟点击坐标 */
    fun performTap(x: Float, y: Float) {
        LogUtil.inpI("执行点击: ($x, $y)")
        try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 100)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            LogUtil.inpD("GestureDescription 构建成功, 持续时间: 100ms")
            
            // dispatchGesture 同步返回是否发送成功
            val success = dispatchGesture(gesture, null, null)
            LogUtil.inpI("点击手势发送结果: $success")
            if (!success) {
                LogUtil.inpE("dispatchGesture 返回false，手势未发送")
            } else {
                LogUtil.inpI("点击手势发送成功")
            }
        } catch (e: Exception) {
            LogUtil.inpE("performTap 异常", e)
        }
    }

    /** 模拟滑动 */
    fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 300) {
        LogUtil.inpI("执行滑动: ($x1,$y1) -> ($x2,$y2), 持续时间: ${duration}ms")
        try {
            val path = Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
            }
            LogUtil.inpD("滑动路径构建成功, 起点: ($x1,$y1), 终点: ($x2,$y2)")
            
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            
            // dispatchGesture 同步返回是否发送成功
            val success = dispatchGesture(gesture, null, null)
            LogUtil.inpI("滑动手势发送结果: $success")
            
            // 计算滑动距离
            val dx = Math.abs(x2 - x1)
            val dy = Math.abs(y2 - y1)
            val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
            LogUtil.inpD("滑动距离: $distance 像素")
            
            if (!success) {
                LogUtil.inpE("dispatchGesture 返回false，滑动未发送")
            } else {
                LogUtil.inpI("滑动手势发送成功")
            }
        } catch (e: Exception) {
            LogUtil.inpE("performSwipe 异常", e)
        }
    }

    /** 返回键 */
    fun performBack() {
        performGlobalActionSafe(GLOBAL_ACTION_BACK, "返回键")
    }

    /** 键盘输入（电脑遥控手机键盘） */
    @Suppress("UNUSED_PARAMETER")
    fun performKeyInput(key: String, mods: List<String>) {
        LogUtil.inpI("键盘输入: key='$key', mods=$mods")
        try {
            val special = mapOf(
                "enter" to KeyEvent.KEYCODE_ENTER,
                "backspace" to KeyEvent.KEYCODE_DEL,
                "esc" to KeyEvent.KEYCODE_ESCAPE,
                "tab" to KeyEvent.KEYCODE_TAB,
                "space" to KeyEvent.KEYCODE_SPACE,
                "up" to KeyEvent.KEYCODE_DPAD_UP,
                "down" to KeyEvent.KEYCODE_DPAD_DOWN,
                "left" to KeyEvent.KEYCODE_DPAD_LEFT,
                "right" to KeyEvent.KEYCODE_DPAD_RIGHT,
                "delete" to KeyEvent.KEYCODE_FORWARD_DEL,
                "home" to KeyEvent.KEYCODE_HOME,
                "end" to KeyEvent.KEYCODE_MOVE_END
            )
            val k = key.lowercase()
            val keyCode = special[k]
            if (keyCode != null) {
                LogUtil.inpI("发送特殊键: $key -> keyCode=$keyCode")
                sendKeyEvent(keyCode)
            } else if (k.length == 1) {
                // 普通字符：尝试直接设置到焦点输入框
                LogUtil.inpD("普通字符输入: '$k'")
                val root = rootInActiveWindow
                if (root == null) {
                    LogUtil.inpW("rootInActiveWindow 为空，无法获取焦点")
                    sendKeyEvent(KeyEvent.keyCodeFromString("KEYCODE_$k")?.takeIf { it != KeyEvent.KEYCODE_UNKNOWN } ?: -1)
                    return
                }
                val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focused != null && focused.isEditable) {
                    LogUtil.inpI("找到可编辑焦点: ${focused.className}")
                    val oldText = focused.text?.toString() ?: ""
                    LogUtil.inpD("旧文本长度: ${oldText.length}, 新文本: ${oldText + k}")
                    val newText = oldText + k
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                    }
                    val result = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    LogUtil.inpI("SET_TEXT 结果: $result")
                } else {
                    // 没有可编辑焦点时，尝试发送字符键码
                    LogUtil.inpW("无可编辑焦点或不可编辑，尝试发送键码")
                    val char = k[0]
                    val vk = KeyEvent.keyCodeFromString("KEYCODE_$char")
                    if (vk != KeyEvent.KEYCODE_UNKNOWN) {
                        LogUtil.inpI("找到键码: KEYCODE_$char -> $vk")
                        sendKeyEvent(vk)
                    } else {
                        LogUtil.inpW("未找到 '$char' 对应的键码")
                    }
                }
            } else {
                // 尝试解析功能键 F1-F12
                LogUtil.inpD("尝试解析功能键: $key")
                if (k.startsWith("f") && k.length > 1) {
                    val num = k.substring(1).toIntOrNull()
                    val fKeyCode = when (num) {
                        1 -> KeyEvent.KEYCODE_F1; 2 -> KeyEvent.KEYCODE_F2; 3 -> KeyEvent.KEYCODE_F3
                        4 -> KeyEvent.KEYCODE_F4; 5 -> KeyEvent.KEYCODE_F5; 6 -> KeyEvent.KEYCODE_F6
                        7 -> KeyEvent.KEYCODE_F7; 8 -> KeyEvent.KEYCODE_F8; 9 -> KeyEvent.KEYCODE_F9
                        10 -> KeyEvent.KEYCODE_F10; 11 -> KeyEvent.KEYCODE_F11; 12 -> KeyEvent.KEYCODE_F12
                        else -> null
                    }
                    if (fKeyCode != null) {
                        LogUtil.inpI("发送功能键: F$num -> $fKeyCode")
                        sendKeyEvent(fKeyCode)
                    } else {
                        LogUtil.inpW("无效的功能键编号: $num")
                    }
                } else {
                    LogUtil.inpW("无法识别的输入: '$key'")
                }
            }
            LogUtil.inpI("键盘输入完成: '$key'")
        } catch (e: Exception) {
            LogUtil.inpE("performKeyInput 异常", e)
        }
    }

    private fun sendKeyEvent(keyCode: Int) {
        try {
            LogUtil.inpD("尝试发送键码: $keyCode")
            // 通过 InputManager 注入需要系统签名或 root；
            // 无障碍服务只能分发媒体键等部分 KeyEvent，普通按键仅作尝试
            val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            val am = getSystemService(AUDIO_SERVICE) as? android.media.AudioManager
            if (am == null) {
                LogUtil.inpE("无法获取 AUDIO_SERVICE，键码注入失败")
                return
            }
            @Suppress("DEPRECATION")
            am.dispatchMediaKeyEvent(down)
            @Suppress("DEPRECATION")
            am.dispatchMediaKeyEvent(up)
            LogUtil.inpI("键码通过 AudioManager 派发成功: $keyCode")
        } catch (e: Exception) {
            LogUtil.inpE("sendKeyEvent 失败 (keyCode=$keyCode)", e)
        }
    }
}
