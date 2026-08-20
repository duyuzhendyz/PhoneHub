package com.phonehub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 共享通知构建器：供 PhoneHubService 和 ScreenCaptureService 共用
 */
object SharedNotificationHelper {
    private const val MAIN_ACTIVITY_CLASS = "com.phonehub.MainActivity"

    /**
     * 创建通知渠道
     */
    fun createChannel(mgr: NotificationManager, channelId: String, name: String, description: String, importance: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, name, importance).apply {
                this.description = description
                setShowBadge(false)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    /**
     * 构建基础通知（点击打开 MainActivity）
     */
    @Suppress("UNCHECKED_CAST")
    fun buildNotification(context: Context, channelId: String, text: String, priority: Int): Notification {
        val mainIntent = Intent(context, Class.forName(MAIN_ACTIVITY_CLASS) as Class<android.app.Activity>).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setContentTitle("PhoneHub")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pi)
            .setPriority(priority)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
