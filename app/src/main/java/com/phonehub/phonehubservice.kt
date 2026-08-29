package com.phonehub

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * 前台保活服务：
 * - 前台通知（CATEGORY_SERVICE，不可滑动移除）让系统优先保留
 * - START_STICKY 让系统内存紧张被杀后自动重启
 * - onTaskRemoved：用户清理所有后台时触发三路重启
 * - onDestroy：销毁时通过 AlarmManager + JobScheduler 双重重启
 */
class PhoneHubService : Service() {
    companion object {
        private const val TAG = "PhoneHubService"
        private const val CHANNEL_ID = "phonehub_foreground"
        private const val NOTIFICATION_ID = 1001
        private const val JOB_RESTART_ID = 7777

        @Volatile
        var instance: PhoneHubService? = null
            private set

        fun start(context: Context) {
            try {
                val intent = Intent(context, PhoneHubService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "start failed", e)
            }
        }

        fun scheduleRestart(context: Context, delayMs: Long = 5_000) {
            try {
                val intent = Intent(context, RestartServiceReceiver::class.java)
                val pi = PendingIntent.getBroadcast(
                    context, 9999, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + delayMs, pi
                )
                Log.i(TAG, "AlarmManager: ${delayMs / 1000}s 后重启")
            } catch (e: Exception) {
                Log.e(TAG, "scheduleRestart failed", e)
            }
        }

        fun scheduleJobRestart(context: Context) {
            try {
                val jm = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? android.app.job.JobScheduler
                val comp = android.content.ComponentName(context, RestartJobService::class.java)
                val job = android.app.job.JobInfo.Builder(JOB_RESTART_ID, comp)
                    .setMinimumLatency(5000)
                    .setOverrideDeadline(15000)
                    .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_ANY)
                    .build()
                jm?.schedule(job)
                Log.i(TAG, "JobScheduler: 5-15s 后重启")
            } catch (e: Exception) {
                Log.e(TAG, "scheduleJobRestart failed", e)
            }
        }
    }

    /**
     * 通知构建辅助类：负责创建通知渠道和构建前台通知
     */
    private object NotificationHelper {
        private const val CHANNEL_ID = "phonehub_foreground"
        const val NOTIFICATION_ID = 1001

        fun createChannel(mgr: NotificationManager) {
            SharedNotificationHelper.createChannel(
                mgr, CHANNEL_ID,
                "PhoneHub 保活",
                "保持 PhoneHub 与电脑持续连接",
                NotificationManager.IMPORTANCE_LOW
            )
        }

        fun buildNotification(context: Context): Notification {
            return SharedNotificationHelper.buildNotification(
                context, CHANNEL_ID, "PhoneHub 保活中",
                androidx.core.app.NotificationCompat.PRIORITY_MIN
            )
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val mgr = getSystemService(NotificationManager::class.java)
        NotificationHelper.createChannel(mgr)
        startForeground(NotificationHelper.NOTIFICATION_ID, NotificationHelper.buildNotification(this))
        acquireWakeLock()
        Log.i(TAG, "PhoneHubService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 服务被重启时重新获取（超时已释放）的 WakeLock，保证后台连接可用
        if (wakeLock?.isHeld != true) {
            acquireWakeLock()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "onTaskRemoved: 用户清理后台，三路重启")
        // 三路并行重启，确保至少一路成功
        scheduleRestart(this, 3_000)    // AlarmManager: 3秒后
        scheduleJobRestart(this)          // JobScheduler: 5-15秒
        start(this)                       // 直接拉起
        // 二次兜底
        scheduleRestart(this, 20_000)
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        instance = null
        Log.w(TAG, "PhoneHubService destroyed，尝试重启")
        scheduleRestart(this, 3_000)
        scheduleJobRestart(this)
    }

    // ==================== WakeLock ====================

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhoneHub:KeepAlive")
            wakeLock?.setReferenceCounted(false)
            // 带超时获取：10 分钟后自动释放，避免无限期持有导致 CPU 永久无法深睡；
            // 活跃传输/投屏的持续保活由各功能在需要时单独持锁
            wakeLock?.acquire(10 * 60 * 1000L)
        } catch (e: Exception) {
            Log.e(TAG, "Acquire wakelock failed", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {}
        wakeLock = null
    }
}
