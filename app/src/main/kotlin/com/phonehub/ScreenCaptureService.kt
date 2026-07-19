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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer
import kotlin.Unit
import kotlin.jvm.functions.Function1

class ScreenCaptureService : Service {
    val CHANNEL_ID: private static final String = "phonehub_screen_capture"

    val INSTANCE: public static final Companion = new Companion(null)
    val NOTIFICATION_ID: private static final int = 3001
    val TAG: private static final String = "ScreenCaptureService"
    var instance: private static volatile ScreenCaptureService? = null
    var isRunning: private static volatile boolean? = null
    var imageReader: private ImageReader? = null
    val mainHandler: private final Handler = new Handler(Looper.getMainLooper())
    var projection: private MediaProjection? = null
    var virtualDisplay: private VirtualDisplay? = null

    public static final class Companion {
        public  Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this()
            }

        fun Companion(): private {
            }

        fun getInstance(): ScreenCaptureService {
            return ScreenCaptureService.instance
            }

        fun isRunning(): Boolean {
            return ScreenCaptureService.isRunning
            }

        fun start(context: Context): Unit {
            Intrinsics.checkNotNullParameter(context, "context")
            if (!isRunning()) {
                val intent: Intent = new Intent(context, (Class<?>) ScreenCaptureService.class)
                context.startForegroundService(intent)
                }
            }

        fun stop(context: Context): Unit {
            Intrinsics.checkNotNullParameter(context, "context")
            if (isRunning()) {
                val intent: Intent = new Intent(context, (Class<?>) ScreenCaptureService.class)
                context.stopService(intent)
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
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("屏幕截图服务运行中"))
        Log.i(TAG, "ScreenCaptureService created")
        }

    override
    fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        var 1: return? = null
        }

    override
    fun onDestroy(): Unit {
        instance = null
        isRunning = false
        stopProjection()
        super.onDestroy()
        Log.i(TAG, "ScreenCaptureService destroyed")
        }

    fun createNotificationChannel(): Unit {
        val mgr: NotificationManager = (NotificationManager) getSystemService(NotificationManager.class)
        val channel: NotificationChannel = new NotificationChannel(CHANNEL_ID, "屏幕截图服务", 2)
        channel.setDescription("屏幕截图和投屏功能需要此服务")
        mgr.createNotificationChannel(channel)
        }

    fun buildNotification(text: String): Notification {
        val mainIntent: Intent = new Intent(this, (Class<?>) MainActivity.class)
        mainIntent.setFlags(335544320)
        val pi: PendingIntent = PendingIntent.getActivity(this, 0, mainIntent, 201326592)
        val build: Notification = new NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("PhoneHub").setContentText(text).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setOngoing(true).setContentIntent(pi).setPriority(-1).build()
        Intrinsics.checkNotNullExpressionValue(build, "build(...)")
        var build: return? = null
        }

    fun startProjection(resultCode: Int, data: Intent): Unit {
        Intrinsics.checkNotNullParameter(data, "data")
        try {
            val systemService: Any = getSystemService("media_projection")
            val projectionManager: MediaProjectionManager = systemService instanceof MediaProjectionManager ? (MediaProjectionManager) systemService : null
            this.projection = projectionManager != null ? projectionManager.getMediaProjection(resultCode, data) : null
            val mediaProjection: MediaProjection = this.projection
            if (mediaProjection != null) {
                mediaProjection.registerCallback(new MediaProjection.Callback() { // from class: com.phonehub.ScreenCaptureService$startProjection$1
                    override
                    fun onStop(): Unit {
                        Log.i("ScreenCaptureService", "MediaProjection stopped")
                        ScreenCaptureService.this.stopProjection()
                        }
                    }, this.mainHandler)
                }
            Log.i(TAG, "MediaProjection started")
            } catch (Exception e) {
            Log.e(TAG, "Failed to start MediaProjection", e)
            }
        }

    fun stopProjection(): Unit {
        try {
            val virtualDisplay: VirtualDisplay = this.virtualDisplay
            if (virtualDisplay != null) {
                virtualDisplay.release()
                }
            this.virtualDisplay = null
            val imageReader: ImageReader = this.imageReader
            if (imageReader != null) {
                imageReader.close()
                }
            this.imageReader = null
            val mediaProjection: MediaProjection = this.projection
            if (mediaProjection != null) {
                mediaProjection.stop()
                }
            this.projection = null
            } catch (Exception e) {
            Log.e(TAG, "Error stopping projection", e)
            }
        }

    fun getProjection(): MediaProjection {
        return this.projection
        }

    fun captureScreenshot(callback: Function1<? super Bitmap, Unit>): Unit {
        Intrinsics.checkNotNullParameter(callback, "callback")
        val proj: MediaProjection = this.projection
        if (proj == null) {
            Log.w(TAG, "No active MediaProjection")
            callback.invoke(null)
            } else {
            this.mainHandler.post(Runnable() { // from class: com.phonehub.ScreenCaptureService$$ExternalSyntheticLambda1
                override
                fun run(): Unit {
                    ScreenCaptureService.captureScreenshot$lambda$3(ScreenCaptureService.this, proj, callback)
                    }
                })
            }
        }

    public static final Unit captureScreenshot$lambda$3(final ScreenCaptureService this$0, MediaProjection $proj, final Function1 $callback) {
        try {
            val systemService: Any = this$0.getSystemService("window")
            Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.view.WindowManager")
            val wm: WindowManager = (WindowManager) systemService
            val metrics: DisplayMetrics = new DisplayMetrics()
            wm.getDefaultDisplay().getRealMetrics(metrics)
            val width: Int = metrics.widthPixels
            val height: Int = metrics.heightPixels
            val density: Int = metrics.densityDpi
            this$0.imageReader = ImageReader.newInstance(width, height, 1, 2)
            val imageReader: ImageReader = this$0.imageReader
            this$0.virtualDisplay = $proj.createVirtualDisplay("PhoneHubScreenshot", width, height, density, 16, imageReader != null ? imageReader.getSurface() : null, null, this$0.mainHandler)
            val imageReader2: ImageReader = this$0.imageReader
            if (imageReader2 != null) {
                imageReader2.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() { // from class: com.phonehub.ScreenCaptureService$$ExternalSyntheticLambda0
                    override
                    fun onImageAvailable(imageReader3: ImageReader): Unit {
                        ScreenCaptureService.captureScreenshot$lambda$3$lambda$2(ScreenCaptureService.this, width, height, $callback, imageReader3)
                        }
                    }, this$0.mainHandler)
                }
            } catch (Exception e) {
            Log.e(TAG, "Screenshot capture failed", e)
            $callback.invoke(null)
            }
        }

    public static final Unit captureScreenshot$lambda$3$lambda$2(ScreenCaptureService this$0, int $width, int $height, Function1 $callback, ImageReader reader) {
        val image: Image = reader.acquireLatestImage()
        try {
            if (image != null) {
                try {
                    val bitmap: Bitmap = this$0.imageToBitmap(image, $width, $height)
                    $callback.invoke(bitmap)
                    } catch (Exception e) {
                    Log.e(TAG, "Failed to convert image to bitmap", e)
                    $callback.invoke(null)
                    }
                }
            } finally {
            image.close()
            }
        }

    fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        try {
            Image.Plane plane = image.getPlanes()[0]
            val buffer: ByteBuffer = plane.getBuffer()
            val pixelStride: Int = plane.getPixelStride()
            val rowStride: Int = plane.getRowStride()
            val rowPadding: Int = rowStride - (pixelStride * width)
            val bitmap: Bitmap = Bitmap.createBitmap((rowPadding / pixelStride) + width, height, Bitmap.Config.ARGB_8888)
            Intrinsics.checkNotNullExpressionValue(bitmap, "createBitmap(...)")
            bitmap.copyPixelsFromBuffer(buffer)
            if (rowPadding <= 0) {
                var bitmap: return? = null
                }
            return Bitmap.createBitmap(bitmap, 0, 0, width, height)
            } catch (Exception e) {
            Log.e(TAG, "Image to bitmap conversion failed", e)
            var null: return? = null
            }
        }
    }
