package com.phonehub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通知监听服务（功能 10）
 *
 * - 监听所有应用通知
 * - 推送到电脑（通过 ConnectionManager.reportNotification）
 * - 黑名单过滤：可配置不转发指定应用通知
 *
 * 黑名单配置存于 SharedPreferences "phonehub_prefs" 的 "notification_blacklist"（Set<String>），
 * 为空表示全部转发。
 */
class NotificationListener : NotificationListenerService() {
    companion object {
        private const val TAG = "PHNotificationListener"
        private const val PREF_NAME = "phonehub_prefs"
        private const val KEY_WHITELIST = "notification_blacklist"

        @Volatile
        var instance: NotificationListener? = null
            private set

        /**
         * 请求当前系统通知黑名单（仅查询，便于 UI 显示）
         */
        fun getBlacklist(ctx: Context): Set<String> {
            return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
        }

        fun setBlacklist(ctx: Context, pkgs: Set<String>) {
            ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_WHITELIST, pkgs).apply()
        }

        /**
         * 检查应用是否拥有 WRITE_SECURE_SETTINGS 权限（通常需要 ADB 或系统签名）
         */
        private fun hasWriteSecureSettings(ctx: Context): Boolean {
            return ctx.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        /**
         * Android 14+ 已移除隐藏 API setNotificationListenerAccessGranted 的反射访问。
         * 此方法现在仅引导用户手动到设置页开启通知访问权限。
         * 返回值：true = 已跳转设置页，false = 跳转失败
         */
        fun toggleNotificationAccess(ctx: Context): Boolean {
            return try {
                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
        // 连接成功后立即上报当前所有活动通知
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
        processAndReport(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Log.d(TAG, "通知移除: ${sbn?.packageName}")
    }

    /**
     * 提取并上报通知内容（兼容不同 ROM 的 extras key）
     */
    private fun processAndReport(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        // 过滤掉自己
        if (pkg == "com.phonehub") return
        // 黑名单内的应用通知不转发
        if (!isNotBlacklisted(pkg)) return

        val n = sbn.notification ?: return
        val extras = n.extras
        var title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        var text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // 兼容不同 ROM 的文本字段
        if (text.isEmpty()) {
            text = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        }
        if (text.isEmpty()) {
            text = extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
        }
        if (text.isEmpty()) {
            val lines = extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (lines != null && lines.isNotEmpty()) {
                text = lines.joinToString("\n")
            }
        }
        if (title.isEmpty() && text.isEmpty()) return

        // 打印详细日志便于调试
        Log.d(TAG, "收到通知 [$pkg] title=$title text=$text")

        // save.md 功能10：提取通知的所有功能按钮（RemoteAction / Notification.Action）
        val actions = mutableListOf<ConnectionManager.NotificationAction>()
        try {
            // Android 4.0+ Notification.Action
            n.actions?.let { arr ->
                for (a in arr) {
                    val actTitle = a.title?.toString() ?: continue
                    actions.add(ConnectionManager.NotificationAction(
                        title = actTitle,
                        actionIntent = a.actionIntent,
                        pkg = pkg,
                        tag = sbn.tag?.toString() ?: "",
                        id = sbn.id
                    ))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "extract actions failed", e)
        }

        val item = ConnectionManager.NotificationItem(
            packageName = pkg,
            title = title,
            text = text,
            timestamp = System.currentTimeMillis(),
            actions = actions,
            sbnId = sbn.id,
            sbnTag = sbn.tag?.toString() ?: "",
            key = sbn.key
        )
        // 推送给电脑
        ConnectionManager.reportNotification(item)
        // 本地持久化（最近 7 天）
        persistNotification(item)
    }

    private fun isNotBlacklisted(pkg: String): Boolean {
        val set = getBlacklist(applicationContext)
        // 黑名单为空表示全部允许
        return set.isEmpty() || !set.contains(pkg)
    }

    /**
     * 上报当前所有活动通知（功能10：电脑端请求获取所有当前通知）
     */
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

    /**
     * 取消指定通知（功能10：电脑端删除手机通知）
     */
    fun cancelNotificationByKey(key: String) {
        try {
            cancelNotification(key)
        } catch (e: Exception) {
            // 回退到 tag+id 方式
            try {
                val parts = key.split("|")
                if (parts.size >= 3) {
                    val pkg = parts[0]
                    val tag = parts[1]
                    val id = parts[2].toIntOrNull() ?: return
                    @Suppress("DEPRECATION")
                    cancelNotification(pkg, tag, id)
                }
            } catch (e2: Exception) {
                Log.e(TAG, "cancelNotification failed", e2)
            }
        }
    }

    /**
     * 通知本地持久化（7 天保留）。当手机无网络或离线时仍可后续补传。
     */
    private fun persistNotification(item: ConnectionManager.NotificationItem) {
        try {
            val dir = File(getExternalFilesDir(null), "NotificationCache")
            if (!dir.exists()) dir.mkdirs()
            // 清理 7 天前的文件
            val cutoff = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
            dir.listFiles()?.forEach { f ->
                if (f.lastModified() < cutoff) f.delete()
            }
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
                .format(Date(item.timestamp))
            val file = File(dir, "${ts}_${item.packageName.replace('.', '_')}.json")
            val json = JSONObject().apply {
                put("package", item.packageName)
                put("title", item.title)
                put("text", item.text)
                put("timestamp", item.timestamp)
            }
            FileOutputStream(file).use { it.write(json.toString().toByteArray()) }
        } catch (e: Exception) {
            Log.e(TAG, "persistNotification failed", e)
        }
    }
}
