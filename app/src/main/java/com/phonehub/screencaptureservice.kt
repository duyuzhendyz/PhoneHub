package com.phonehub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

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
        startForeground(NOTIFICATION_ID, buildNotification("屏幕截图服务运行中"))
        Log.i(TAG, "ScreenCaptureService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        isRunning = false
        stopProjection()
        super.onDestroy()
        Log.i(TAG, "ScreenCaptureService destroyed")
    }

    /**
     * 启动 MediaProjection（由 ScreenshotActivity 授权后调用）
     */
    fun startProjection(resultCode: Int, data: Intent) {
        try {
            val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            projection = pm?.getMediaProjection(resultCode, data)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped")
                    stopProjection()
                }
            }, mainHandler)
            Log.i(TAG, "MediaProjection started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaProjection", e)
        }
    }

    /**
     * 停止 MediaProjection
     */
    fun stopProjection() {
        try {
            projection?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping projection", e)
        }
        projection = null
    }

    /**
     * 获取当前 MediaProjection 实例（供 ConnectionManager 后台截图复用）
     */
    fun getProjection(): MediaProjection? = projection

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "屏幕截图服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "屏幕截图和投屏功能需要此服务"
                setShowBadge(false)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhoneHub")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
