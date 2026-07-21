package com.phonehub

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.util.Locale

class PhoneHubAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PHAccessibility"
        @Volatile private var instance: PhoneHubAccessibilityService? = null

        fun getInstance(): PhoneHubAccessibilityService? {
            return instance
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
    }

    override fun onInterrupt() {
    }

    override fun onUnbind(intent: Intent): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    fun performMediaKey(keyCode: Int) {
        try {
            when (keyCode) {
                85 -> dispatchMediaKeyViaAudio(keyCode)
                87 -> dispatchMediaKeyViaAudio(keyCode)
                88 -> dispatchMediaKeyViaAudio(keyCode)
                else -> return
            }
        } catch (e: Exception) {
            Log.e(TAG, "performMediaKey failed", e)
        }
    }

    fun dispatchMediaKeyViaAudio(keyCode: Int) {
        try {
            val systemService = getSystemService("audio")
            val am = systemService as? AudioManager
            if (am != null) {
                am.dispatchMediaKeyEvent(KeyEvent(0, keyCode))
            }
            if (am != null) {
                am.dispatchMediaKeyEvent(KeyEvent(1, keyCode))
            }
        } catch (e: Exception) {
            Log.e(TAG, "dispatchMediaKey failed", e)
        }
    }

    fun performGlobalLock() {
        try {
            performGlobalAction(8)
        } catch (e: Exception) {
            Log.e(TAG, "performGlobalLock failed", e)
        }
    }

    fun performHome() {
        try {
            performGlobalAction(2)
        } catch (e: Exception) {
        }
    }

    fun performRecents() {
        try {
            performGlobalAction(3)
        } catch (e: Exception) {
        }
    }

    fun openNotifications() {
        try {
            performGlobalAction(4)
        } catch (e: Exception) {
        }
    }

    fun openQuickSettings() {
        try {
            performGlobalAction(5)
        } catch (e: Exception) {
        }
    }

    fun performTap(x: Float, y: Float) {
        try {
            val path = Path()
            path.moveTo(x, y)
            val stroke = GestureDescription.StrokeDescription(path, 0L, 100L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "performTap failed", e)
        }
    }

    fun clickAt(x: Float, y: Float) {
        performTap(x, y)
    }

    fun longClickAt(x: Float, y: Float) {
        try {
            val path = Path()
            path.moveTo(x, y)
            val stroke = GestureDescription.StrokeDescription(path, 0L, 800L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "longClickAt failed", e)
        }
    }

    fun inputText(text: String) {
        try {
            val rootInActiveWindow: AccessibilityNodeInfo? = getRootInActiveWindow()
            val findFocus = rootInActiveWindow?.findFocus(1)
            if (findFocus != null && findFocus.isEditable) {
                val bundle = Bundle()
                bundle.putCharSequence(
                    AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
                findFocus.performAction(2097152, bundle)
            } else {
                for (ch in text) {
                    performKeyInput(ch.toString(), emptyList())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "inputText failed", e)
        }
    }

    fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        try {
            val path = Path()
            path.moveTo(x1, y1)
            path.lineTo(x2, y2)
            val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "performSwipe failed", e)
        }
    }

    fun performBack() {
        try {
            performGlobalAction(1)
        } catch (e: Exception) {
            Log.e(TAG, "performBack failed", e)
        }
    }

    fun performKeyInput(key: String, mods: List<String>) {
        var str: String? = null
        try {
            val mapOf = mapOf(
                "enter" to 66, "backspace" to 67, "esc" to 111, "tab" to 61,
                "space" to 62, "up" to 19, "down" to 20, "left" to 21,
                "right" to 22, "delete" to 112, "home" to 3, "end" to 123
            )
            val lowerCase = key.lowercase(Locale.ROOT)
            val num = mapOf[lowerCase]
            if (num != null) {
                sendKeyEvent(num)
                return
            }
            if (lowerCase.length == 1) {
                val rootInActiveWindow: AccessibilityNodeInfo? = getRootInActiveWindow()
                val findFocus = rootInActiveWindow?.findFocus(1)
                if (findFocus != null && findFocus.isEditable) {
                    val text = findFocus.text
                    if (text == null) {
                        str = ""
                    } else {
                        str = text.toString()
                    }
                    val bundle = Bundle()
                    bundle.putCharSequence(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, str + lowerCase)
                    findFocus.performAction(2097152, bundle)
                }
                val keyCodeFromString = KeyEvent.keyCodeFromString("KEYCODE_" + lowerCase[0])
                if (keyCodeFromString != 0) {
                    sendKeyEvent(keyCodeFromString)
                }
                return
            }
            if (lowerCase.startsWith("f") && lowerCase.length > 1) {
                val substring = lowerCase.substring(1)
                val intOrNull = substring.toIntOrNull()
                val num2: Int? = when (intOrNull) {
                    1 -> 131
                    2 -> 132
                    3 -> 133
                    4 -> 134
                    5 -> 135
                    6 -> 136
                    7 -> 137
                    8 -> 138
                    9 -> 139
                    10 -> 140
                    11 -> 141
                    12 -> 142
                    else -> null
                }
                if (num2 != null) {
                    sendKeyEvent(num2)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "performKeyInput failed", e)
        }
    }

    fun sendKeyEvent(keyCode: Int) {
        try {
            val down = KeyEvent(0, keyCode)
            val up = KeyEvent(1, keyCode)
            val systemService = getSystemService("audio")
            val am = systemService as? AudioManager
            if (am != null) {
                am.dispatchMediaKeyEvent(down)
            }
            if (am != null) {
                am.dispatchMediaKeyEvent(up)
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendKeyEvent failed", e)
        }
    }
}
