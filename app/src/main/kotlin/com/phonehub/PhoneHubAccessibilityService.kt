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
import androidx.constraintlayout.core.motion.utils.TypedValues
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.util.List
import java.util.Locale
import java.util.Map
import kotlin.TuplesKt
import kotlin.collections.MapsKt
import kotlin.text.StringsKt
import org.osmdroid.tileprovider.modules.DatabaseFileArchive

class PhoneHubAccessibilityService : AccessibilityService {

    val INSTANCE: public static final Companion = new Companion(null)
    val TAG: private static final String = "PHAccessibility"
    var instance: private static volatile PhoneHubAccessibilityService? = null

    public static final class Companion {
        public  Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this()
            }

        fun Companion(): private {
            }

        fun getInstance(): PhoneHubAccessibilityService {
            return PhoneHubAccessibilityService.instance
            }
        }

    override
    fun onServiceConnected(): Unit {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "AccessibilityService connected")
        }

    override
    fun onAccessibilityEvent(event: AccessibilityEvent): Unit {
        }

    override
    fun onInterrupt(): Unit {
        }

    override
    fun onUnbind(intent: Intent): Boolean {
        instance = null
        return super.onUnbind(intent)
        }

    fun performMediaKey(keyCode: Int): Unit {
        try {
            switch (keyCode) {
                case 85:
                dispatchMediaKeyViaAudio(keyCode)
                break
                case 86:
                default:
                return
                case 87:
                dispatchMediaKeyViaAudio(keyCode)
                break
                case 88:
                dispatchMediaKeyViaAudio(keyCode)
                break
                }
            } catch (Exception e) {
            Log.e(TAG, "performMediaKey failed", e)
            }
        }

    fun dispatchMediaKeyViaAudio(keyCode: Int): Unit {
        try {
            val systemService: Any = getSystemService("audio")
            val am: AudioManager = systemService instanceof AudioManager ? (AudioManager) systemService : null
            if (am != null) {
                am.dispatchMediaKeyEvent(KeyEvent(0, keyCode))
                }
            if (am != null) {
                am.dispatchMediaKeyEvent(KeyEvent(1, keyCode))
                }
            } catch (Exception e) {
            Log.e(TAG, "dispatchMediaKey failed", e)
            }
        }

    fun performGlobalLock(): Unit {
        try {
            performGlobalAction(8)
            } catch (Exception e) {
            Log.e(TAG, "performGlobalLock failed", e)
            }
        }

    fun performHome(): Unit {
        try {
            performGlobalAction(2)
            } catch (Exception e) {
            }
        }

    fun performRecents(): Unit {
        try {
            performGlobalAction(3)
            } catch (Exception e) {
            }
        }

    fun openNotifications(): Unit {
        try {
            performGlobalAction(4)
            } catch (Exception e) {
            }
        }

    fun openQuickSettings(): Unit {
        try {
            performGlobalAction(5)
            } catch (Exception e) {
            }
        }

    fun performTap(x: Float, y: Float): Unit {
        try {
            Path $this$performTap_u24lambda_u240 = Path()
            $this$performTap_u24lambda_u240.moveTo(x, y)
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription($this$performTap_u24lambda_u240, 0L, 100L)
            val gesture: GestureDescription = new GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
            } catch (Exception e) {
            Log.e(TAG, "performTap failed", e)
            }
        }

    public static  Unit performSwipe$default(PhoneHubAccessibilityService phoneHubAccessibilityService, float f, float f2, float f3, float f4, long j, int i, Object obj) {
        if ((i & 16) != 0) {
            j = 300
            }
        phoneHubAccessibilityService.performSwipe(f, f2, f3, f4, j)
        }

    fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Unit {
        try {
            Path $this$performSwipe_u24lambda_u241 = Path()
            $this$performSwipe_u24lambda_u241.moveTo(x1, y1)
            $this$performSwipe_u24lambda_u241.lineTo(x2, y2)
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription($this$performSwipe_u24lambda_u241, 0L, duration)
            val gesture: GestureDescription = new GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
            } catch (Exception e) {
            Log.e(TAG, "performSwipe failed", e)
            }
        }

    fun performBack(): Unit {
        try {
            performGlobalAction(1)
            } catch (Exception e) {
            Log.e(TAG, "performBack failed", e)
            }
        }

    fun performKeyInput(key: String, mods: List<String>): Unit {
        var str: String? = null
        Intrinsics.checkNotNullParameter(key, "key")
        Intrinsics.checkNotNullParameter(mods, "mods")
        try {
            val mapOf: Map = MapsKt.mapOf(TuplesKt.to("enter", 66), TuplesKt.to("backspace", 67), TuplesKt.to("esc", 111), TuplesKt.to("tab", 61), TuplesKt.to("space", 62), TuplesKt.to("up", 19), TuplesKt.to("down", 20), TuplesKt.to("left", 21), TuplesKt.to("right", 22), TuplesKt.to("delete", 112), TuplesKt.to("home", 3), TuplesKt.to("end", 123))
            val lowerCase: String = key.toLowerCase(Locale.ROOT)
            Intrinsics.checkNotNullExpressionValue(lowerCase, "toLowerCase(...)")
            val num: Integer = (Integer) mapOf.get(lowerCase)
            if (num != null) {
                sendKeyEvent(num.intValue())
                return
                }
            val num2: Integer = null
            if (lowerCase.length() == 1) {
                val rootInActiveWindow: AccessibilityNodeInfo = getRootInActiveWindow()
                val findFocus: AccessibilityNodeInfo = rootInActiveWindow != null ? rootInActiveWindow.findFocus(1) : null
                if (findFocus != null && findFocus.isEditable()) {
                    val text: CharSequence = findFocus.getText()
                    if (text == null || (str = text.toString()) == null) {
                        str = ""
                        }
                    val bundle: Bundle = new Bundle()
                    bundle.putCharSequence(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, str + lowerCase)
                    findFocus.performAction(2097152, bundle)
                    return
                    }
                val keyCodeFromString: Int = KeyEvent.keyCodeFromString("KEYCODE_" + lowerCase.charAt(0))
                if (keyCodeFromString != 0) {
                    sendKeyEvent(keyCodeFromString)
                    return
                    }
                return
                }
            if (StringsKt.startsWith$default(lowerCase, "f", false, 2, (Object) null) && lowerCase.length() > 1) {
                val substring: String = lowerCase.substring(1)
                Intrinsics.checkNotNullExpressionValue(substring, "substring(...)")
                val intOrNull: Integer = StringsKt.toIntOrNull(substring)
                if (intOrNull != null && intOrNull.intValue() == 1) {
                    num2 = 131
                    } else if (intOrNull != null && intOrNull.intValue() == 2) {
                    num2 = 132
                    } else if (intOrNull != null && intOrNull.intValue() == 3) {
                    num2 = 133
                    } else if (intOrNull != null && intOrNull.intValue() == 4) {
                    num2 = 134
                    } else if (intOrNull != null && intOrNull.intValue() == 5) {
                    num2 = 135
                    } else if (intOrNull != null && intOrNull.intValue() == 6) {
                    num2 = 136
                    } else if (intOrNull != null && intOrNull.intValue() == 7) {
                    num2 = 137
                    } else if (intOrNull != null && intOrNull.intValue() == 8) {
                    num2 = 138
                    } else if (intOrNull != null && intOrNull.intValue() == 9) {
                    num2 = 139
                    } else if (intOrNull != null && intOrNull.intValue() == 10) {
                    num2 = 140
                    } else if (intOrNull != null && intOrNull.intValue() == 11) {
                    num2 = 141
                    } else if (intOrNull != null && intOrNull.intValue() == 12) {
                    num2 = 142
                    }
                val num3: Integer = num2
                if (num3 != null) {
                    sendKeyEvent(num3.intValue())
                    }
                }
            } catch (Exception e) {
            Log.e(TAG, "performKeyInput failed", e)
            }
        }

    fun sendKeyEvent(keyCode: Int): Unit {
        try {
            val down: KeyEvent = new KeyEvent(0, keyCode)
            val up: KeyEvent = new KeyEvent(1, keyCode)
            val systemService: Any = getSystemService("audio")
            val am: AudioManager = systemService instanceof AudioManager ? (AudioManager) systemService : null
            if (am != null) {
                am.dispatchMediaKeyEvent(down)
                }
            if (am != null) {
                am.dispatchMediaKeyEvent(up)
                }
            } catch (Exception e) {
            Log.e(TAG, "sendKeyEvent failed", e)
            }
        }
    }
