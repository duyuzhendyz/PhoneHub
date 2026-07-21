package com.phonehub

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

class NotificationListener : NotificationListenerService() {

    companion object {
        private const val KEY_WHITELIST = "notification_blacklist"
        private const val PREF_NAME = "phonehub_prefs"
        private const val TAG = "PHNotificationListener"

        @Volatile
        private var instance: NotificationListener? = null

        fun getInstance(): NotificationListener? = instance

        fun getBlacklist(ctx: Context): Set<String> {
            val stringSet = ctx.getSharedPreferences(PREF_NAME, 0)
                .getStringSet(KEY_WHITELIST, emptySet())
            return stringSet ?: emptySet()
        }

        fun setBlacklist(ctx: Context, pkgs: Set<String>) {
            ctx.getSharedPreferences(PREF_NAME, 0).edit()
                .putStringSet(KEY_WHITELIST, pkgs).apply()
        }

        fun hasWriteSecureSettings(ctx: Context): Boolean {
            return ctx.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == 0
        }

        fun toggleNotificationAccess(ctx: Context): Boolean {
            return try {
                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                intent.addFlags(0x10000000)
                ctx.startActivity(intent)
                Log.i(TAG, "已引导用户到通知监听设置页")
                true
            } catch (e: Exception) {
                Log.e(TAG, "跳转通知监听设置页失败", e)
                false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "NotificationListener onCreate")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "NotificationListener connected - 开始监听通知")
        try {
            val active = getActiveNotifications()
            Log.i(TAG, "当前活动通知数量: ${active?.size ?: 0}")
            if (active != null && active.isNotEmpty()) {
                for (sbn in active) {
                    processAndReport(sbn)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onListenerConnected getActiveNotifications failed", e)
        }
    }

    override fun onListenerDisconnected() {
        instance = null
        Log.i(TAG, "NotificationListener disconnected - 停止监听通知")
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn != null) processAndReport(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Log.d(TAG, "通知移除: ${sbn?.packageName}")
    }

    fun processAndReport(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (!isNotBlacklisted(pkg)) return
        val n = sbn.notification ?: return
        val extras = n.extras
        var title = extras?.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString() ?: ""
        var text = extras?.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString() ?: ""
        if (text.isEmpty()) {
            text = extras?.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString() ?: ""
        }
        if (text.isEmpty()) {
            text = extras?.getCharSequence(NotificationCompat.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
        }
        if (text.isEmpty()) {
            val lines = extras?.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES)
            if (lines != null && lines.isNotEmpty()) {
                text = lines.joinToString("\n")
            }
        }
        if (title.isEmpty() && text.isEmpty()) return
        Log.d(TAG, "收到通知 [$pkg] title=$title text=$text")

        val actions = mutableListOf<ConnectionManager.NotificationAction>()
        val arr = n.actions
        if (arr != null) {
            for (a in arr) {
                val actTitle = a.title?.toString() ?: continue
                val pendingIntent: PendingIntent = a.actionIntent
                val tag = sbn.tag?.toString() ?: ""
                actions.add(ConnectionManager.NotificationAction(actTitle, pendingIntent, pkg, tag, sbn.id))
            }
        }

        val currentTimeMillis = System.currentTimeMillis()
        val id = sbn.id
        val tag2 = sbn.tag?.toString() ?: ""
        val key = sbn.key
        val item = ConnectionManager.NotificationItem(
            pkg, title, text, currentTimeMillis, actions, id, tag2, key
        )
        ConnectionManager.INSTANCE.reportNotification(item)
        persistNotification(item)
    }

    fun isNotBlacklisted(pkg: String): Boolean {
        val set = getBlacklist(applicationContext)
        return set.isEmpty() || !set.contains(pkg)
    }

    fun reportAllActiveNotifications() {
        try {
            val active = getActiveNotifications() ?: return
            Log.i(TAG, "上报当前所有活动通知: ${active.size} 条")
            for (sbn in active) {
                processAndReport(sbn)
            }
        } catch (e: Exception) {
            Log.e(TAG, "reportAllActiveNotifications failed", e)
        }
    }

    fun cancelNotificationByKey(key: String) {
        try {
            cancelNotification(key)
        } catch (e: Exception) {
            try {
                val parts = key.split("|")
                if (parts.size >= 3) {
                    val pkg = parts[0]
                    val tag = parts[1]
                    val id = parts[2].toIntOrNull() ?: return
                    cancelNotification(pkg, tag, id)
                }
            } catch (e2: Exception) {
                Log.e(TAG, "cancelNotification failed", e2)
            }
        }
    }

    fun persistNotification(item: ConnectionManager.NotificationItem) {
        try {
            val dir = File(getExternalFilesDir(null), "NotificationCache")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val cutoff = System.currentTimeMillis() - 604800000L
            val listFiles = dir.listFiles()
            if (listFiles != null) {
                for (file in listFiles) {
                    if (file.lastModified() < cutoff) {
                        file.delete()
                    }
                }
            }
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
                .format(Date(item.timestamp))
            val file2 = File(dir, "${ts}_${item.packageName.replace('.', '_')}.json")
            val json = JSONObject()
            json.put("package", item.packageName)
            json.put("title", item.title)
            json.put("text", item.text)
            json.put("timestamp", item.timestamp)
            val fileOutputStream = FileOutputStream(file2)
            try {
                val str = json.toString()
                val bytes = str.toByteArray(Charsets.UTF_8)
                fileOutputStream.write(bytes)
            } finally {
                fileOutputStream.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "persistNotification failed", e)
        }
    }
}
