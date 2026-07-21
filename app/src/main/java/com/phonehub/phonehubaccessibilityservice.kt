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
        Log.i(TAG, "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 当前不需要响应事件，仅用于执行全局手势
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    /** 媒体键 */
    fun performMediaKey(keyCode: Int) {
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
        } catch (e: Exception) {
            Log.e(TAG, "performMediaKey failed", e)
        }
    }

    private fun dispatchMediaKeyViaAudio(keyCode: Int) {
        try {
            val am = getSystemService(AUDIO_SERVICE) as? android.media.AudioManager
            @Suppress("DEPRECATION")
            am?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            @Suppress("DEPRECATION")
            am?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (e: Exception) {
            Log.e(TAG, "dispatchMediaKey failed", e)
        }
    }

    /** 锁屏 */
    fun performGlobalLock() {
        try {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } catch (e: Exception) {
            Log.e(TAG, "performGlobalLock failed", e)
        }
    }

    /** 回到桌面 */
    fun performHome() {
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (e: Exception) {}
    }

    /** 最近任务 */
    fun performRecents() {
        try {
            performGlobalAction(GLOBAL_ACTION_RECENTS)
        } catch (e: Exception) {}
    }

    /** 通知栏 */
    fun openNotifications() {
        try {
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        } catch (e: Exception) {}
    }

    /** 控制中心（快速设置面板） */
    fun openQuickSettings() {
        try {
            performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        } catch (e: Exception) {}
    }

    /** 模拟点击坐标 */
    fun performTap(x: Float, y: Float) {
        try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 100)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "performTap failed", e)
        }
    }

    /** 模拟滑动 */
    fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 300) {
        try {
            val path = Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "performSwipe failed", e)
        }
    }

    /** 返回键 */
    fun performBack() {
        try {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } catch (e: Exception) {
            Log.e(TAG, "performBack failed", e)
        }
    }

    /** 键盘输入（电脑遥控手机键盘） */
    @Suppress("UNUSED_PARAMETER")
    fun performKeyInput(key: String, mods: List<String>) {
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
                sendKeyEvent(keyCode)
            } else if (k.length == 1) {
                // 普通字符：尝试直接设置到焦点输入框
                val root = rootInActiveWindow
                val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focused != null && focused.isEditable) {
                    val oldText = focused.text?.toString() ?: ""
                    val newText = oldText + k
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                    }
                    focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                } else {
                    // 没有可编辑焦点时，尝试发送字符键码
                    val char = k[0]
                    val vk = KeyEvent.keyCodeFromString("KEYCODE_$char")
                    if (vk != KeyEvent.KEYCODE_UNKNOWN) {
                        sendKeyEvent(vk)
                    }
                }
            } else {
                // 尝试解析功能键 F1-F12
                if (k.startsWith("f") && k.length > 1) {
                    val num = k.substring(1).toIntOrNull()
                    val fKeyCode = when (num) {
                        1 -> KeyEvent.KEYCODE_F1; 2 -> KeyEvent.KEYCODE_F2; 3 -> KeyEvent.KEYCODE_F3
                        4 -> KeyEvent.KEYCODE_F4; 5 -> KeyEvent.KEYCODE_F5; 6 -> KeyEvent.KEYCODE_F6
                        7 -> KeyEvent.KEYCODE_F7; 8 -> KeyEvent.KEYCODE_F8; 9 -> KeyEvent.KEYCODE_F9
                        10 -> KeyEvent.KEYCODE_F10; 11 -> KeyEvent.KEYCODE_F11; 12 -> KeyEvent.KEYCODE_F12
                        else -> null
                    }
                    fKeyCode?.let { sendKeyEvent(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "performKeyInput failed", e)
        }
    }

    private fun sendKeyEvent(keyCode: Int) {
        try {
            // 通过 InputManager 注入需要系统签名或 root；
            // 无障碍服务只能分发媒体键等部分 KeyEvent，普通按键仅作尝试
            val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            val am = getSystemService(AUDIO_SERVICE) as? android.media.AudioManager
            am?.dispatchMediaKeyEvent(down)
            am?.dispatchMediaKeyEvent(up)
        } catch (e: Exception) {
            Log.e(TAG, "sendKeyEvent failed", e)
        }
    }
}
