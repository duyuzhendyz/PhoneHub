package com.phonehub

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * 屏幕截图/投屏前台服务（Android 10+ 的 MediaProjection 需要此类型前台服务）。
 *
 * 先以 mediaProjection 类型进入前台，再创建投影；持有实例供投屏、内录和截图复用。
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "phonehub_screen_capture"
        private const val NOTIFICATION_ID = 3001

        @Volatile
        var instance: ScreenCaptureService? = null
            private set

        @Volatile
        var isRunning: Boolean = false
            private set

        /** 只有 startForeground 成功后才允许创建 MediaProjection，实例存在不代表前台服务就绪。 */
        @Volatile
        var foregroundStarted: Boolean = false
            private set

        /** 将真实启动错误传给授权页面，避免吞掉异常后只显示超时。 */
        @Volatile
        var startupFailure: String? = null
            private set

        /**
         * 启动或复前台服务。
         *
         * 关键：服务已经存在时**不能直接返回**。EMUI 省电策略、用户从状态栏撤销投屏、
         * 系统内存回收等都会让服务掉出前台，而 `getMediaProjection()` 放行的唯一依据是
         * AMS 里这条 ServiceRecord 是否为 mediaProjection 类型的前台服务——我们的
         * `isRunning/foregroundStarted` 标志位在那种情况下是过期的。
         * 所以这里先重申一次前台状态；重申失败就彻底销毁后重新拉起。
         */
        fun start(context: Context) {
            startupFailure = null
            val existing = instance
            if (existing != null && isRunning) {
                if (existing.ensureForeground()) return
                try {
                    LogUtil.scrW("前台状态重申失败，销毁服务后重新拉起")
                    context.stopService(Intent(context, ScreenCaptureService::class.java))
                } catch (e: RuntimeException) {
                    LogUtil.scrE("stopService 失败", e)
                }
            }
            try {
                val intent = Intent(context, ScreenCaptureService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: RuntimeException) {
                startupFailure = e.message ?: "无法启动屏幕采集前台服务"
                LogUtil.scrE("ScreenCaptureService.start 失败", e)
            }
        }

        fun stop(context: Context) {
            try {
                if (!isRunning) return
                context.stopService(Intent(context, ScreenCaptureService::class.java))
            } catch (e: RuntimeException) {
                Log.e(TAG, "stop failed", e)
            }
        }
    }

    @Volatile
    private var projection: MediaProjection? = null
    // 投屏/内录与截图分别登记使用者，最后一个使用者退出时才停止共享投影。
    private val projectionOwners = mutableSetOf<Any>()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true
        foregroundStarted = false
        startupFailure = null
        if (ensureForeground()) {
            LogUtil.scrI("ScreenCaptureService 前台服务就绪 (API ${Build.VERSION.SDK_INT})")
        } else {
            // 不能让启动失败的服务继续存活并触发前台服务启动超时。
            isRunning = false
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtil.scrD("onStartCommand: startId=$startId")
        // 系统可能在我们不知情的情况下把服务降级成普通服务（EMUI 省电、撤销投屏等），
        // 每次收到指令都重申一次前台状态，代价只是一次 binder 调用。
        if (!ensureForeground()) {
            isRunning = false
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /**
     * 重申 mediaProjection 类型前台状态（幂等，可反复调用）。
     *
     * 不做「已就绪就跳过」的短路：标志位可能是过期的，而 AMS 的 ServiceRecord 状态才是
     * `getMediaProjection()` 放行的唯一依据。返回 false 表示确实进不了前台。
     */
    @Synchronized
    private fun ensureForeground(): Boolean {
        return try {
            createNotificationChannel()
            val notification = buildNotification("屏幕截图和投屏服务运行中")
            // 三参数重载和 mediaProjection 服务类型均从 API 29 才提供。
            // API 26-28 只能使用两参数重载，不能用魔法数字调用不存在的方法。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            if (!foregroundStarted) LogUtil.scrI("重申前台状态成功 (API ${Build.VERSION.SDK_INT})")
            foregroundStarted = true
            true
        } catch (e: RuntimeException) {
            startupFailure = e.message ?: "无法进入 mediaProjection 类型前台服务"
            foregroundStarted = false
            LogUtil.scrE("startForeground(mediaProjection) 失败", e)
            false
        }
    }

    override fun onDestroy() {
        instance = null
        isRunning = false
        foregroundStarted = false
        stopProjection()
        super.onDestroy()
        LogUtil.scrI("ScreenCaptureService 销毁")
    }

    /**
     * 诊断用：打印本进程重要性。
     *
     * `importance <= IMPORTANCE_FOREGROUND_SERVICE(125)` 说明系统确实把本进程当成前台服务；
     * 若取投影前它仍是 IMPORTANCE_FOREGROUND，说明这台 ROM 没接受前台服务提升
     * （典型是华为/荣耀的「应用启动管理」把后台活动掐了），问题不在代码而在系统设置。
     */
    private fun logProcessImportance(stage: String) {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return
            val me = am.runningAppProcesses?.firstOrNull { it.pid == android.os.Process.myPid() }
            LogUtil.scrI(
                "$stage 进程重要性=${me?.importance ?: -1}" +
                    "（前台服务阈值=${android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE}）"
            )
        } catch (e: RuntimeException) {
            LogUtil.scrD("$stage 读取进程重要性失败: ${e.message}")
        }
    }

    /**
     * 授权完成且前台服务就绪后调用；失败向调用者抛出，不重复消费同一授权。
     *
     * 这里务必在 `getMediaProjection()` 之前重申一次前台状态：系统那一侧的
     * `hasRunningForegroundService(uid, FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)`
     * 是放行的唯一依据，服务被降级时本地标志位是过期的。
     */
    @Synchronized
    fun startProjection(resultCode: Int, data: Intent) {
        check(isRunning) { "屏幕采集前台服务尚未启动" }
        if (!ensureForeground()) {
            throw IllegalStateException(startupFailure ?: "无法进入 mediaProjection 类型前台服务")
        }
        if (projection != null) return
        LogUtil.scrI("启动 MediaProjection: resultCode=$resultCode")
        logProcessImportance("取投影前")
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            ?: throw IllegalStateException("无法获取 MEDIA_PROJECTION_SERVICE")
        val newProjection = try {
            manager.getMediaProjection(resultCode, data)
                ?: throw IllegalStateException("getMediaProjection 返回 null")
        } catch (e: SecurityException) {
            // 前台服务类型没被系统认下来：清掉状态，下次调用会重新走一遍重申流程。
            foregroundStarted = false
            LogUtil.scrE("getMediaProjection 被系统拒绝（mediaProjection 前台服务未生效）", e)
            throw IllegalStateException(
                "${e.message ?: "前台服务类型未生效"}（已重申前台服务仍被拒，请重启 App 后再试）", e
            )
        }
        newProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                // 系统已经停止投影，只清理同一实例，不递归 stop 或误清理后来的会话。
                synchronized(this@ScreenCaptureService) {
                    if (projection === newProjection) {
                        projection = null
                        projectionOwners.clear()
                        LogUtil.scrW("MediaProjection 被停止")
                    }
                }
            }
        }, mainHandler)
        projection = newProjection
        LogUtil.scrI("MediaProjection 启动成功，注册回调")
    }

    /** 登记使用者。同一个使用者重复获取不会重复计数。 */
    @Synchronized
    fun acquireProjection(owner: Any): MediaProjection? {
        if (!isRunning || !foregroundStarted) return null
        val current = projection ?: return null
        projectionOwners.add(owner)
        return current
    }

    /** 仅释放自己的使用权，不能停止仍被投屏、内录或截图使用的实例。 */
    @Synchronized
    fun releaseProjection(owner: Any) {
        if (projectionOwners.remove(owner) && projectionOwners.isEmpty()) {
            stopProjection()
        }
    }

    /** 授权页面在服务就绪回调前被销毁时，清掉尚未被任何消费者领取的投影。 */
    @Synchronized
    fun stopProjectionIfUnused() {
        if (projectionOwners.isEmpty()) stopProjection()
    }

    @Synchronized
    fun stopProjection() {
        val stoppedProjection = projection
        projection = null
        projectionOwners.clear()
        try {
            stoppedProjection?.stop()
        } catch (e: RuntimeException) {
            LogUtil.scrE("Error stopping projection", e)
        }
    }

    /** 获取服务持有的实例；调用方不能在前台服务未就绪时用 token 另建实例。 */
    fun getProjection(): MediaProjection? {
        val current = projection
        LogUtil.scrD("getProjection: ${if (current != null) "有实例" else "无实例"}")
        return current
    }

    private fun createNotificationChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        SharedNotificationHelper.createChannel(
            mgr, CHANNEL_ID,
            "屏幕截图服务",
            "屏幕截图和投屏功能需要此服务",
            NotificationManager.IMPORTANCE_LOW
        )
    }

    private fun buildNotification(text: String): Notification {
        return SharedNotificationHelper.buildNotification(
            this, CHANNEL_ID, text,
            androidx.core.app.NotificationCompat.PRIORITY_LOW
        )
    }
}
