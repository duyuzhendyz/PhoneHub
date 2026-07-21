package com.phonehub

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.io.CloseableKt

class ScreenshotActivity : AppCompatActivity() {
    private var container: LinearLayout? = null
    private var hint: TextView? = null
    private var progress: ProgressBar? = null
    private var projection: MediaProjection? = null
    private var projectionManager: MediaProjectionManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val linearLayout = LinearLayout(this)
        linearLayout.orientation = 1
        linearLayout.gravity = 17
        linearLayout.setPadding(48, 48, 48, 48)
        linearLayout.setBackgroundColor(Color.parseColor("#cc000000"))
        this.container = linearLayout
        val textView = TextView(this)
        textView.text = "正在请求截屏权限..."
        textView.setTextColor(-1)
        textView.textSize = 14.0f
        textView.setPadding(0, 0, 0, 24)
        this.hint = textView
        val progressBar = ProgressBar(this)
        progressBar.isIndeterminate = true
        this.progress = progressBar
        linearLayout.addView(textView)
        linearLayout.addView(progressBar)
        setContentView(linearLayout)
        val systemService = getSystemService("media_projection")
        this.projectionManager = if (systemService is MediaProjectionManager) systemService else null
        if (this.projectionManager == null) {
            this.hint?.text = "当前设备不支持截屏"
            this.progress?.visibility = 8
            Handler(Looper.getMainLooper()).postDelayed({
                this@ScreenshotActivity.finish()
            }, 1500L)
            return
        }
        try {
            val mediaProjectionManager = this.projectionManager
            if (mediaProjectionManager != null) {
                startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQ_MEDIA_PROJECTION)
            }
        } catch (e: Exception) {
            Log.e(TAG, "createScreenCaptureIntent failed", e)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_MEDIA_PROJECTION) {
            if (resultCode != -1 || data == null) {
                finish()
                return
            }
            try {
                val mediaProjectionManager = this.projectionManager
                this.projection = mediaProjectionManager?.getMediaProjection(resultCode, data)
                ConnectionManager.INSTANCE.cacheMediaProjectionToken(resultCode, data)
                doScreenshot()
                return
            } catch (e: SecurityException) {
                Log.e(TAG, "getMediaProjection failed", e)
                finish()
                return
            }
        }
        finish()
    }

    fun doScreenshot() {
        try {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val w = metrics.widthPixels
            val h = metrics.heightPixels
            val dpi = metrics.densityDpi
            val imageReader = ImageReader.newInstance(w, h, 1, 2)
            val mediaProjection = this.projection
            if (mediaProjection != null) {
                mediaProjection.registerCallback(object : MediaProjection.Callback() {}, Handler(Looper.getMainLooper()))
            }
            val mediaProjection2 = this.projection
            val virtualDisplay = mediaProjection2?.createVirtualDisplay(
                "PhoneHubScreenshot", w, h, dpi, 16, imageReader.surface, null, null
            )
            imageReader.setOnImageAvailableListener(object : ImageReader.OnImageAvailableListener {
                override fun onImageAvailable(reader: ImageReader) {
                    `doScreenshot$lambda$4`(w, h, this@ScreenshotActivity, virtualDisplay, imageReader, reader)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            Log.e(TAG, "doScreenshot failed", e)
            finish()
        }
    }

    private fun onScreenshotCaptured(bmp: Bitmap) {
        var handler: Handler? = null
        var runnable: Runnable? = null
        try {
            try {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "screenshot_" + ts + ".png"
                saveToGallery(bmp, fileName)
                val outFile = File(getExternalFilesDir(null)!!, fileName)
                val fileOutputStream = FileOutputStream(outFile)
                try {
                    val out = fileOutputStream
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    CloseableKt.closeFinally(out, null)
                    ConnectionManager.INSTANCE.sendFile(outFile)
                    handler = Handler(Looper.getMainLooper())
                    runnable = Runnable {
                        this@ScreenshotActivity.finish()
                    }
                } finally {
                }
            } catch (th: Throwable) {
                Handler(Looper.getMainLooper()).postDelayed(Runnable {
                    this@ScreenshotActivity.finish()
                }, 100L)
                throw th
            }
        } catch (e: Exception) {
            Log.e(TAG, "onScreenshotCaptured failed", e)
            handler = Handler(Looper.getMainLooper())
            runnable = Runnable {
                this@ScreenshotActivity.finish()
            }
        }
        val h = handler
        val r = runnable
        if (h != null && r != null) {
            h.postDelayed(r, 100L)
        }
    }

    private fun saveToGallery(bmp: Bitmap, fileName: String) {
        var openOutputStream: OutputStream? = null
        try {
            val resolver = contentResolver
            val values = ContentValues()
            values.put("_display_name", fileName)
            values.put("mime_type", "image/png")
            values.put("relative_path", Environment.DIRECTORY_PICTURES + "/PhoneHub")
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                openOutputStream = resolver.openOutputStream(uri)
                if (openOutputStream != null) {
                    val outputStream = openOutputStream
                    try {
                        val os = outputStream
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, os)
                        CloseableKt.closeFinally(outputStream, null)
                    } finally {
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveToGallery failed", e)
        }
    }

    override fun onDestroy() {
        try {
            val mediaProjection = this.projection
            mediaProjection?.stop()
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    companion object {
        private const val REQ_MEDIA_PROJECTION = 5001
        private const val TAG = "PHScreenshot"

        internal fun `doScreenshot$lambda$4`(
            w: Int, h: Int, activity: ScreenshotActivity,
            virtualDisplay: VirtualDisplay?, imageReader: ImageReader, reader: ImageReader
        ) {
            var mediaProjection: MediaProjection? = null
            val image = reader.acquireLatestImage() ?: return
            try {
                try {
                    val planes = image.planes
                    val buffer: ByteBuffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - (pixelStride * w)
                    val bmp = Bitmap.createBitmap((rowPadding / pixelStride) + w, h, Bitmap.Config.ARGB_8888)
                    buffer.rewind()
                    bmp.copyPixelsFromBuffer(buffer)
                    val cropped = Bitmap.createBitmap(bmp, 0, 0, w, h)
                    activity.onScreenshotCaptured(cropped)
                    image.close()
                    virtualDisplay?.release()
                    mediaProjection = activity.projection
                } catch (e: Exception) {
                    Log.e(TAG, "Image available handle failed", e)
                    image.close()
                    virtualDisplay?.release()
                    mediaProjection = activity.projection
                }
                mediaProjection?.stop()
                imageReader.close()
            } catch (th: Throwable) {
                image.close()
                virtualDisplay?.release()
                val mediaProjection2 = activity.projection
                mediaProjection2?.stop()
                imageReader.close()
                throw th
            }
        }
    }
}
