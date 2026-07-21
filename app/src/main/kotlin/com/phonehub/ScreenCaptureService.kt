package com.phonehub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {

    companion object {
        private const val CHANNEL_ID = "phonehub_screen_capture"
        private const val NOTIFICATION_ID = 3001
        private const val TAG = "ScreenCaptureService"
        @Volatile private var instance: ScreenCaptureService? = null
        @Volatile private var isRunning: Boolean = false

        fun getInstance(): ScreenCaptureService? {
            return instance
        }

        fun isRunning(): Boolean {
            return isRunning
        }

        fun start(context: Context) {
            if (!isRunning()) {
                val intent = Intent(context, ScreenCaptureService::class.java)
                context.startForegroundService(intent)
            }
        }

        fun stop(context: Context) {
            if (isRunning()) {
                val intent = Intent(context, ScreenCaptureService::class.java)
                context.stopService(intent)
            }
        }
    }

    private var imageReader: ImageReader? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("屏幕截图服务运行中"))
        Log.i(TAG, "ScreenCaptureService created")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return 1
    }

    override fun onDestroy() {
        instance = null
        isRunning = false
        stopProjection()
        super.onDestroy()
        Log.i(TAG, "ScreenCaptureService destroyed")
    }

    fun createNotificationChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "屏幕截图服务", 2)
        channel.description = "屏幕截图和投屏功能需要此服务"
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
            .setContentIntent(pi)
            .setPriority(-1)
            .build()
    }

    fun startProjection(resultCode: Int, data: Intent) {
        try {
            val systemService = getSystemService("media_projection")
            val projectionManager = systemService as? MediaProjectionManager
            this.projection = projectionManager?.getMediaProjection(resultCode, data)
            val mediaProjection = this.projection
            if (mediaProjection != null) {
                mediaProjection.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.i("ScreenCaptureService", "MediaProjection stopped")
                        this@ScreenCaptureService.stopProjection()
                    }
                }, this.mainHandler)
            }
            Log.i(TAG, "MediaProjection started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaProjection", e)
        }
    }

    fun stopProjection() {
        try {
            val virtualDisplay = this.virtualDisplay
            if (virtualDisplay != null) {
                virtualDisplay.release()
            }
            this.virtualDisplay = null
            val imageReader = this.imageReader
            if (imageReader != null) {
                imageReader.close()
            }
            this.imageReader = null
            val mediaProjection = this.projection
            if (mediaProjection != null) {
                mediaProjection.stop()
            }
            this.projection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping projection", e)
        }
    }

    fun getProjection(): MediaProjection? {
        return this.projection
    }

    fun captureScreenshot(callback: (Bitmap?) -> Unit) {
        val proj = this.projection
        if (proj == null) {
            Log.w(TAG, "No active MediaProjection")
            callback(null)
        } else {
            this.mainHandler.post {
                captureScreenshotLambda3(proj, callback)
            }
        }
    }

    fun captureScreenshotLambda3(proj: MediaProjection, callback: (Bitmap?) -> Unit) {
        try {
            val systemService = this.getSystemService("window")
            val wm = systemService as WindowManager
            val metrics = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi
            this.imageReader = ImageReader.newInstance(width, height, 1, 2)
            val imageReader = this.imageReader
            this.virtualDisplay = proj.createVirtualDisplay(
                "PhoneHubScreenshot", width, height, density, 16,
                imageReader?.surface, null, this.mainHandler
            )
            val imageReader2 = this.imageReader
            imageReader2?.setOnImageAvailableListener({ reader ->
                captureScreenshotLambda3Lambda2(width, height, callback, reader)
            }, this.mainHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot capture failed", e)
            callback(null)
        }
    }

    fun captureScreenshotLambda3Lambda2(width: Int, height: Int, callback: (Bitmap?) -> Unit, reader: ImageReader) {
        val image = reader.acquireLatestImage()
        try {
            if (image != null) {
                try {
                    val bitmap = this.imageToBitmap(image, width, height)
                    callback(bitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to convert image to bitmap", e)
                    callback(null)
                }
            }
        } finally {
            image?.close()
        }
    }

    fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap? {
        try {
            val plane = image.planes[0]
            val buffer: ByteBuffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - (pixelStride * width)
            val bitmap = Bitmap.createBitmap((rowPadding / pixelStride) + width, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            if (rowPadding <= 0) {
                return bitmap
            }
            return Bitmap.createBitmap(bitmap, 0, 0, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "Image to bitmap conversion failed", e)
            return null
        }
    }
}
