package com.phonehub

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import java.io.File
import kotlin.io.FilesKt

class TextNotificationReceiver : BroadcastReceiver {
    val ACTION_COPY: public static final String = "com.phonehub.action.COPY_TEXT"
    val ACTION_SAVE: public static final String = "com.phonehub.action.SAVE_TEXT"
    val EXTRA_TEXT: public static final String = "text"

    override
    fun onReceive(context: Context, intent: Intent): Unit {
        var action: String? = null
        Intrinsics.checkNotNullParameter(context, "context")
        if (intent == null || (action = intent.getAction()) == null) {
            return
            }
        val txt: String = intent.getStringExtra(EXTRA_TEXT)
        if (txt == null) {
            txt = ""
            }
        Log.i("PhoneHub", "TextNotificationReceiver: action=" + action + ", length=" + txt.length())
        if (Intrinsics.areEqual(action, ACTION_COPY)) {
            val systemService: Any = context.getSystemService("clipboard")
            val cm: ClipboardManager = systemService instanceof ClipboardManager ? (ClipboardManager) systemService : null
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("PhoneHub", txt))
                return
                }
            return
            }
        if (Intrinsics.areEqual(action, ACTION_SAVE)) {
            try {
                val dir: File = ConnectionManager.INSTANCE.getReceiveDir()
                if (dir == null) {
                    dir = context.getFilesDir()
                    }
                dir.mkdirs()
                val file: File = new File(dir, "text_" + System.currentTimeMillis() + ".txt")
                FilesKt.writeText$default(file, txt, null, 2, null)
                Log.i("PhoneHub", "文字已保存: " + file.getAbsolutePath())
                } catch (Exception e) {
                Log.e("PhoneHub", "保存文字失败", e)
                }
            }
        }
    }
