package com.phonehub

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.content.pm.ServiceInfo
import android.util.Log

/**
 * 屏幕截图/投屏前台服务（Android 14+ 强制要求）
 *
 * - 持有 MediaProjection 实例，供 ConnectionManager 复用
 * - 前台服务类型：mediaProjection
 * - 提供 getProjection() 供后台截图使用
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

        fun start(context: Context) {
            try {
                if (isRunning) return
                val intent = Intent(context, ScreenCaptureService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "start failed", e)
            }
        }

        fun stop(context: Context) {
            try {
                if (!isRunning) return
                context.stopService(Intent(context, ScreenCaptureService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "stop failed", e)
            }
        }
    }

    private var projection: MediaProjection? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true
        createNotificationChannel()
        val notification = buildNotification("屏幕截图服务运行中")
        // Always use three-parameter startForeground on API 26+ to properly declare foreground service type
        // This ensures MediaProjection works correctly across all Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Get the foreground service type: use constant on API 29+, else use literal 64 (1 << 6)
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                // For API 26-28, the integer value for MEDIA_PROJECTION type is 64
                64
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        LogUtil.scrI("ScreenCaptureService 创建")
        LogUtil.scrI("Android版本: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtil.scrD("onStartCommand: startId=$startId")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        isRunning = false
        stopProjection()
        super.onDestroy()
        LogUtil.scrI("ScreenCaptureService 销毁")
    }

    /**
     * 启动 MediaProjection（由 ScreenshotActivity 授权后调用）
     */
    fun startProjection(resultCode: Int, data: Intent) {
        try {
            LogUtil.scrI("启动 MediaProjection: resultCode=$resultCode")
            val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (pm == null) {
                LogUtil.scrE("无法获取 MEDIA_PROJECTION_SERVICE")
                return
            }
            projection = pm.getMediaProjection(resultCode, data)
            if (projection == null) {
                LogUtil.scrE("getMediaProjection 返回null")
                return
            }
            LogUtil.scrI("MediaProjection 实例获取成功")
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    LogUtil.scrW("MediaProjection 被停止")
                    stopProjection()
                }
            }, mainHandler)
            LogUtil.scrI("MediaProjection 启动成功，注册回调")
        } catch (e: Exception) {
            LogUtil.scrE("Failed to start MediaProjection", e)
        }
    }

    /**
     * 停止 MediaProjection
     */
    fun stopProjection() {
        try {
            LogUtil.scrD("停止 MediaProjection")
            projection?.stop()
            LogUtil.scrD("MediaProjection 已停止")
        } catch (e: Exception) {
            LogUtil.scrE("Error stopping projection", e)
        }
        projection = null
    }

    /**
     * 获取当前 MediaProjection 实例（供 ConnectionManager 后台截图复用）
     */
    fun getProjection(): MediaProjection? {
        val hasProj = projection != null
        LogUtil.scrD("getProjection: ${if (hasProj) "有实例" else "无实例"}")
        return projection
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
