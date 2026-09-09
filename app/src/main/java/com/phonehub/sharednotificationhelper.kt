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
    // 直接引用同包 Activity 类（避免 Class.forName 字符串在开启混淆后崩溃）
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
     * @param title 通知标题，可传空字符串以隐藏通知文字（前台服务通知不能删除，只能隐藏文案）
     * @param withContentIntent 是否绑定点击打开 MainActivity；保活通知可设 false 进一步减重量
     */
    fun buildNotification(context: Context, channelId: String, text: String, priority: Int,
                          title: String = "PhoneHub", withContentIntent: Boolean = true): Notification {
        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(priority)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
        if (withContentIntent) {
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, 0, mainIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.setContentIntent(pi)
        }
        return builder.build()
    }
}
