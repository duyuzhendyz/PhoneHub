package com.phonehub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

/**
 * 文字消息通知的按钮点击接收器
 *
 * 处理通知中的两种操作：
 *  - 复制：将文字复制到系统剪贴板
 *  - 保存：将文字保存为文件到接收目录
 */
class TextNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val txt = intent.getStringExtra(EXTRA_TEXT) ?: ""
        Log.i("PhoneHub", "TextNotificationReceiver: action=$action, length=${txt.length}")

        when (action) {
            ACTION_COPY -> {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("PhoneHub", txt))
            }
            ACTION_SAVE -> {
                try {
                    val dir = ConnectionManager.getReceiveDir() ?: context.filesDir
                    dir.mkdirs()
                    val file = File(dir, "text_${System.currentTimeMillis()}.txt")
                    file.writeText(txt)
                    Log.i("PhoneHub", "文字已保存: ${file.absolutePath}")
                } catch (e: Exception) {
                    Log.e("PhoneHub", "保存文字失败", e)
                }
            }
        }
    }

    companion object {
        const val ACTION_COPY = "com.phonehub.action.COPY_TEXT"
        const val ACTION_SAVE = "com.phonehub.action.SAVE_TEXT"
        const val EXTRA_TEXT = "text"
    }
}
