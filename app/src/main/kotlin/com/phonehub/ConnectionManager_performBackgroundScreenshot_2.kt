package com.phonehub

import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class ConnectionManager_performBackgroundScreenshot_2(
    continuation: Continuation<Boolean>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_performBackgroundScreenshot_2(continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Boolean>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_performBackgroundScreenshot_2).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                if (Build.VERSION.SDK_INT < 34) {
                    return false
                }
                val context = ConnectionManager.context
                if (context == null) {
                    return false
                }
                if (!ScreenCaptureService.INSTANCE!!.isRunning) {
                    ScreenCaptureService.INSTANCE!!.start(context)
                    this.label = 1
                    val delayResult = delay(1500L, this)
                    if (delayResult == coroutine_suspended) {
                        return coroutine_suspended
                    }
                }
                if (!ScreenCaptureService.INSTANCE!!.isRunning) {
                    Log.w("PhoneHub", "ScreenCaptureService 未启动，无法截图")
                    return false
                }
                return performScreenshot(context)
            }
            1 -> {
                ResultKt.throwOnFailure(result)
                if (!ScreenCaptureService.INSTANCE!!.isRunning) {
                    Log.w("PhoneHub", "ScreenCaptureService 未启动，无法截图")
                    return false
                }
                val context = ConnectionManager.context
                if (context == null) {
                    return false
                }
                return performScreenshot(context)
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
    }

    private fun performScreenshot(context: Context): Boolean {
        var imageReader: ImageReader? = null
        var virtualDisplay: VirtualDisplay? = null
        var mediaProjection: MediaProjection? = null
        try {
            val cachedMediaProjection = ConnectionManager.INSTANCE.getCachedMediaProjection() ?: return false
            mediaProjection = cachedMediaProjection
            val systemService = context.getSystemService("window")
            val displayMetrics = DisplayMetrics()
            (systemService as WindowManager).defaultDisplay.getRealMetrics(displayMetrics)
            val w = displayMetrics.widthPixels
            val h = displayMetrics.heightPixels
            val dpi = displayMetrics.densityDpi
            imageReader = ImageReader.newInstance(w, h, 1, 2)
            virtualDisplay = cachedMediaProjection.createVirtualDisplay(
                "PhoneHubBgScreenshot", w, h, dpi, 16, imageReader.surface, null, null
            )
            val latch = CountDownLatch(1)
            var bitmap: Bitmap? = null
            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        val planes = image.planes
                        val buffer: ByteBuffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * w
                        val bmp = Bitmap.createBitmap(rowPadding / pixelStride + w, h, Bitmap.Config.ARGB_8888)
                        buffer.rewind()
                        bmp.copyPixelsFromBuffer(buffer)
                        bitmap = Bitmap.createBitmap(bmp, 0, 0, w, h)
                    } catch (e: Exception) {
                        Log.e("PhoneHub", "后台截图 Image 处理失败", e)
                    }
                    image.close()
                    latch.countDown()
                }
            }, Handler(Looper.getMainLooper()))
            latch.await(3L, TimeUnit.SECONDS)
            if (bitmap == null) {
                return false
            }
            val fileName = "screenshot_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".png"
            ConnectionManager.INSTANCE.saveBitmapToGallery(context, bitmap!!, fileName)
            val file = File(context.getExternalFilesDir(null), fileName)
            val fileOutputStream = FileOutputStream(file)
            try {
                bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            } finally {
                fileOutputStream.close()
            }
            ConnectionManager.INSTANCE.sendFile(file)
            Log.d("PhoneHub", "后台静默截图成功: $fileName")
            ConnectionManager.scope.launch {
                ConnectionManager._screenshotResult.emit("截图已保存到手机相册: $fileName")
            }
            return true
        } catch (e: Exception) {
            Log.e("PhoneHub", "performBackgroundScreenshot failed", e)
            ConnectionManager.scope.launch {
                ConnectionManager._screenshotResult.emit("截图失败: ${e.message ?: "未知错误"}")
            }
            return false
        } finally {
            try { virtualDisplay?.release() } catch (_: Exception) {}
            try { imageReader?.close() } catch (_: Exception) {}
            try { mediaProjection?.stop() } catch (_: Exception) {}
        }
    }
}
