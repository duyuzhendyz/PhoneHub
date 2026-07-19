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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import androidx.lifecycle.CoroutineLiveDataKt
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class PhoneHubService : Service {
    val CHANNEL_ID: private static final String = "phonehub_foreground"

    val INSTANCE: public static final Companion = new Companion(null)
    val JOB_RESTART_ID: private static final int = 7777
    val NOTIFICATION_ID: private static final int = 1001
    val TAG: private static final String = "PhoneHubService"
    val WATCHDOG_INTERVAL_SEC: private static final long = 15
    var instance: private static volatile PhoneHubService? = null
    val scheduler: private final ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private PowerManager.WakeLock wakeLock
    var watchdogFuture: private ScheduledFuture<?>? = null

    public static final class Companion {
        public  Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this()
            }

        fun Companion(): private {
            }

        fun getInstance(): PhoneHubService {
            return PhoneHubService.instance
            }

        fun start(context: Context): Unit {
            Intrinsics.checkNotNullParameter(context, "context")
            try {
                val intent: Intent = new Intent(context, (Class<?>) PhoneHubService.class)
                context.startForegroundService(intent)
                } catch (Exception e) {
                Log.e(PhoneHubService.TAG, "start failed", e)
                }
            }

        public static  Unit scheduleRestart$default(Companion companion, Context context, long j, int i, Object obj) {
            if ((i & 2) != 0) {
                j = CoroutineLiveDataKt.DEFAULT_TIMEOUT
                }
            companion.scheduleRestart(context, j)
            }

        fun scheduleRestart(context: Context, delayMs: Long): Unit {
            Intrinsics.checkNotNullParameter(context, "context")
            try {
                val intent: Intent = new Intent(context, (Class<?>) RestartServiceReceiver.class)
                val pi: PendingIntent = PendingIntent.getBroadcast(context, 9999, intent, 201326592)
                val systemService: Any = context.getSystemService(NotificationCompat.CATEGORY_ALARM)
                Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.app.AlarmManager")
                val am: AlarmManager = (AlarmManager) systemService
                am.setExactAndAllowWhileIdle(2, System.currentTimeMillis() + delayMs, pi)
                Log.i(PhoneHubService.TAG, "AlarmManager: " + (delayMs / 1000) + "s 后重启")
                } catch (Exception e) {
                Log.e(PhoneHubService.TAG, "scheduleRestart failed", e)
                }
            }

        fun scheduleJobRestart(context: Context): Unit {
            Intrinsics.checkNotNullParameter(context, "context")
            try {
                val systemService: Any = context.getSystemService("jobscheduler")
                val jm: JobScheduler = systemService instanceof JobScheduler ? (JobScheduler) systemService : null
                val comp: ComponentName = new ComponentName(context, (Class<?>) RestartJobService.class)
                val job: JobInfo = new JobInfo.Builder(PhoneHubService.JOB_RESTART_ID, comp).setMinimumLatency(CoroutineLiveDataKt.DEFAULT_TIMEOUT).setOverrideDeadline(15000L).setRequiredNetworkType(1).build()
                if (jm != null) {
                    jm.schedule(job)
                    }
                Log.i(PhoneHubService.TAG, "JobScheduler: 5-15s 后重启")
                } catch (Exception e) {
                Log.e(PhoneHubService.TAG, "scheduleJobRestart failed", e)
                }
            }
        }

    override
    fun onBind(intent: Intent): IBinder {
        var null: return? = null
        }

    override
    fun onCreate(): Unit {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(1001, buildNotification("PhoneHub 保活中"))
        acquireWakeLock()
        startWatchdog()
        Log.i(TAG, "PhoneHubService created")
        }

    override
    fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        var 1: return? = null
        }

    override
    fun onTaskRemoved(rootIntent: Intent): Unit {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "onTaskRemoved: 用户清理后台，三路重启")
        INSTANCE.scheduleRestart(this, 3000L)
        INSTANCE.scheduleJobRestart(this)
        INSTANCE.start(this)
        INSTANCE.scheduleRestart(this, 20000L)
        }

    override
    fun onDestroy(): Unit {
        super.onDestroy()
        stopWatchdog()
        releaseWakeLock()
        instance = null
        Log.w(TAG, "PhoneHubService destroyed，尝试重启")
        INSTANCE.scheduleRestart(this, 3000L)
        INSTANCE.scheduleJobRestart(this)
        }

    fun startWatchdog(): Unit {
        val scheduledFuture: ScheduledFuture<?> = this.watchdogFuture
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false)
            }
        this.watchdogFuture = this.scheduler.scheduleAtFixedRate(Runnable() { // from class: com.phonehub.PhoneHubService$$ExternalSyntheticLambda0
            override
            fun run(): Unit {
                PhoneHubService.startWatchdog$lambda$0(PhoneHubService.this)
                }
            }, WATCHDOG_INTERVAL_SEC, WATCHDOG_INTERVAL_SEC, TimeUnit.SECONDS)
        }

    public static final Unit startWatchdog$lambda$0(PhoneHubService this$0) {
        try {
            if (instance == null) {
                Log.w(TAG, "watchdog: instance 为 null，重启")
                INSTANCE.start(this$0)
                }
            } catch (Exception e) {
            Log.e(TAG, "watchdog error", e)
            }
        }

    fun stopWatchdog(): Unit {
        val scheduledFuture: ScheduledFuture<?> = this.watchdogFuture
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false)
            }
        this.watchdogFuture = null
        }

    fun createNotificationChannel(): Unit {
        val mgr: NotificationManager = (NotificationManager) getSystemService(NotificationManager.class)
        val channel: NotificationChannel = new NotificationChannel(CHANNEL_ID, "PhoneHub 保活", 2)
        channel.setDescription("保持 PhoneHub 与电脑持续连接")
        channel.setShowBadge(false)
        channel.setLockscreenVisibility(1)
        mgr.createNotificationChannel(channel)
        }

    fun buildNotification(text: String): Notification {
        val mainIntent: Intent = new Intent(this, (Class<?>) MainActivity.class)
        mainIntent.setFlags(335544320)
        val pi: PendingIntent = PendingIntent.getActivity(this, 0, mainIntent, 201326592)
        val build: Notification = new NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("PhoneHub").setContentText(text).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setOngoing(true).setSilent(true).setContentIntent(pi).setPriority(-2).setCategory(NotificationCompat.CATEGORY_SERVICE).build()
        Intrinsics.checkNotNullExpressionValue(build, "build(...)")
        var build: return? = null
        }

    fun acquireWakeLock(): Unit {
        try {
            val systemService: Any = getSystemService("power")
            Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.os.PowerManager")
            val pm: PowerManager = (PowerManager) systemService
            this.wakeLock = pm.newWakeLock(1, "PhoneHub:KeepAlive")
            PowerManager.WakeLock wakeLock = this.wakeLock
            if (wakeLock != null) {
                wakeLock.setReferenceCounted(false)
                }
            PowerManager.WakeLock wakeLock2 = this.wakeLock
            if (wakeLock2 != null) {
                wakeLock2.acquire()
                }
            } catch (Exception e) {
            Log.e(TAG, "Acquire wakelock failed", e)
            }
        }

    fun releaseWakeLock(): Unit {
        PowerManager.WakeLock wakeLock
        try {
            PowerManager.WakeLock wakeLock2 = this.wakeLock
            val z: Boolean = false
            if (wakeLock2 != null && wakeLock2.isHeld()) {
                z = true
                }
            if (z && (wakeLock = this.wakeLock) != null) {
                wakeLock.release()
                }
            } catch (Exception e) {
            }
        this.wakeLock = null
        }
    }
