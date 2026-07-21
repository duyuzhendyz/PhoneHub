package com.phonehub

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class PhoneHubService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var watchdogFuture: ScheduledFuture<*>? = null
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    companion object {
        private const val CHANNEL_ID = "phonehub_foreground"
        private const val JOB_RESTART_ID = 7777
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "PhoneHubService"
        private const val WATCHDOG_INTERVAL_SEC: Long = 15

        @Volatile
        private var instance: PhoneHubService? = null

        fun getInstance(): PhoneHubService? = instance

        fun start(context: Context) {
            try {
                val intent = Intent(context, PhoneHubService::class.java)
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "start failed", e)
            }
        }

        fun scheduleRestart(context: Context, delayMs: Long = 5000L) {
            try {
                val intent = Intent(context, RestartServiceReceiver::class.java)
                val pi = PendingIntent.getBroadcast(context, 9999, intent, 201326592)
                val systemService = context.getSystemService(NotificationCompat.CATEGORY_ALARM)
                val am = systemService as AlarmManager
                am.setExactAndAllowWhileIdle(2, System.currentTimeMillis() + delayMs, pi)
                Log.i(TAG, "AlarmManager: " + (delayMs / 1000) + "s 后重启")
            } catch (e: Exception) {
                Log.e(TAG, "scheduleRestart failed", e)
            }
        }

        fun scheduleJobRestart(context: Context) {
            try {
                val systemService = context.getSystemService("jobscheduler")
                val jm = if (systemService is JobScheduler) systemService else null
                val comp = ComponentName(context, RestartJobService::class.java)
                val job = JobInfo.Builder(JOB_RESTART_ID, comp)
                    .setMinimumLatency(5000L)
                    .setOverrideDeadline(15000L)
                    .setRequiredNetworkType(1)
                    .build()
                jm?.schedule(job)
                Log.i(TAG, "JobScheduler: 5-15s 后重启")
            } catch (e: Exception) {
                Log.e(TAG, "scheduleJobRestart failed", e)
            }
        }

        fun `startWatchdog$lambda$0`(this$0: PhoneHubService) {
            try {
                if (instance == null) {
                    Log.w(TAG, "watchdog: instance 为 null，重启")
                    start(this$0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "watchdog error", e)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("PhoneHub 保活中"))
        acquireWakeLock()
        startWatchdog()
        Log.i(TAG, "PhoneHubService created")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "onTaskRemoved: 用户清理后台，三路重启")
        scheduleRestart(this, 3000L)
        scheduleJobRestart(this)
        start(this)
        scheduleRestart(this, 20000L)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWatchdog()
        releaseWakeLock()
        instance = null
        Log.w(TAG, "PhoneHubService destroyed，尝试重启")
        scheduleRestart(this, 3000L)
        scheduleJobRestart(this)
    }

    fun startWatchdog() {
        watchdogFuture?.cancel(false)
        watchdogFuture = scheduler.scheduleAtFixedRate(
            Runnable { `startWatchdog$lambda$0`(this@PhoneHubService) },
            WATCHDOG_INTERVAL_SEC,
            WATCHDOG_INTERVAL_SEC,
            TimeUnit.SECONDS
        )
    }

    fun stopWatchdog() {
        watchdogFuture?.cancel(false)
        watchdogFuture = null
    }

    fun createNotificationChannel() {
        val mgr = getSystemService(NotificationManager::class.java) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "PhoneHub 保活", 2)
        channel.description = "保持 PhoneHub 与电脑持续连接"
        channel.setShowBadge(false)
        channel.lockscreenVisibility = 1
        mgr.createNotificationChannel(channel)
    }

    fun buildNotification(text: String): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        mainIntent.flags = 335544320
        val pi = PendingIntent.getActivity(this, 0, mainIntent, 201326592)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhoneHub")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pi)
            .setPriority(-2)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun acquireWakeLock() {
        try {
            val systemService = getSystemService("power")
                ?: throw NullPointerException("null cannot be cast to non-null type android.os.PowerManager")
            val pm = systemService as PowerManager
            this.wakeLock = pm.newWakeLock(1, "PhoneHub:KeepAlive")
            this.wakeLock?.setReferenceCounted(false)
            this.wakeLock?.acquire()
        } catch (e: Exception) {
            Log.e(TAG, "Acquire wakelock failed", e)
        }
    }

    fun releaseWakeLock() {
        try {
            val wakeLock = this.wakeLock
            if (wakeLock != null && wakeLock.isHeld) {
                wakeLock.release()
            }
        } catch (e: Exception) {
        }
        this.wakeLock = null
    }
}
