package com.phonehub

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

class TextNotificationReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_COPY = "com.phonehub.action.COPY_TEXT"
        const val ACTION_SAVE = "com.phonehub.action.SAVE_TEXT"
        const val EXTRA_TEXT = "text"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        var txt = intent.getStringExtra(EXTRA_TEXT) ?: ""
        Log.i("PhoneHub", "TextNotificationReceiver: action=" + action + ", length=" + txt.length)
        if (action == ACTION_COPY) {
            val systemService = context.getSystemService("clipboard")
            val cm = systemService as? ClipboardManager
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("PhoneHub", txt))
            }
            return
        }
        if (action == ACTION_SAVE) {
            try {
                var dir = ConnectionManager.receiveDir
                if (dir == null) {
                    dir = context.filesDir
                }
                dir.mkdirs()
                val file = File(dir, "text_" + System.currentTimeMillis() + ".txt")
                file.writeText(txt)
                Log.i("PhoneHub", "文字已保存: " + file.absolutePath)
            } catch (e: Exception) {
                Log.e("PhoneHub", "保存文字失败", e)
            }
        }
    }
}
