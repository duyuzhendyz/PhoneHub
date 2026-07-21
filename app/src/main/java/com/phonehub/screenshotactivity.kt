package com.phonehub

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.media.MediaScannerConnection
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
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 截图 Activity（功能 5 双向截图）
 *
 * - 使用 MediaProjection 截图（无障碍/Root 不需要）
 * - 相册存一份
 * - 回传电脑（通过 ConnectionManager.sendFile）
 *
 * 注：MediaProjection 必须由用户授权，故本 Activity 为透明引导界面。
 */
class ScreenshotActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "PHScreenshot"
        private const val REQ_MEDIA_PROJECTION = 5001
    }

    private var projectionManager: MediaProjectionManager? = null
    private var projection: MediaProjection? = null
    private lateinit var container: LinearLayout
    private lateinit var hint: TextView
    private lateinit var progress: ProgressBar

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#cc000000"))
        }
        hint = TextView(this).apply {
            text = "正在请求截屏权限..."
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 0, 0, 24)
        }
        progress = ProgressBar(this).apply {
            isIndeterminate = true
        }
        container.addView(hint)
        container.addView(progress)
        setContentView(container)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (projectionManager == null) {
            hint.text = "当前设备不支持截屏"
            progress.visibility = View.GONE
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1500)
            return
        }
        try {
            startActivityForResult(projectionManager!!.createScreenCaptureIntent(), REQ_MEDIA_PROJECTION)
        } catch (e: Exception) {
            Log.e(TAG, "createScreenCaptureIntent failed", e)
            finish()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK || data == null) {
                // 静默处理：权限被拒绝时不弹 Toast，直接退出
                finish()
                return
            }
            try {
                projection = projectionManager?.getMediaProjection(resultCode, data)
                // 缓存 token 供后台静默截图复用（下次无需再弹授权界面）
                ConnectionManager.cacheMediaProjectionToken(resultCode, data)
                doScreenshot()
            } catch (e: SecurityException) {
                Log.e(TAG, "getMediaProjection failed", e)
                finish()
            }
        } else {
            finish()
        }
    }

    private fun doScreenshot() {
        try {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val w = metrics.widthPixels
            val h = metrics.heightPixels
            val dpi = metrics.densityDpi

            // 通过 ImageReader 拿到一帧
            val imageReader = android.media.ImageReader.newInstance(w, h, android.graphics.PixelFormat.RGBA_8888, 2)
            projection?.registerCallback(object : MediaProjection.Callback() {}, Handler(Looper.getMainLooper()))
            val virtualDisplay = projection?.createVirtualDisplay(
                "PhoneHubScreenshot", w, h, dpi,
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface, null, null
            )

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * w
                    val bmp = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888)
                    buffer.rewind()
                    bmp.copyPixelsFromBuffer(buffer)
                    val cropped = Bitmap.createBitmap(bmp, 0, 0, w, h)
                    onScreenshotCaptured(cropped)
                } catch (e: Exception) {
                    Log.e(TAG, "Image available handle failed", e)
                } finally {
                    image.close()
                    virtualDisplay?.release()
                    projection?.stop()
                    imageReader.close()
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            Log.e(TAG, "doScreenshot failed", e)
            finish()
        }
    }

    private fun onScreenshotCaptured(bmp: Bitmap) {
        try {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "screenshot_${ts}.png"

            // 1. 相册存一份
            saveToGallery(bmp, fileName)

            // 2. 写入临时文件，回传电脑
            val outFile = File(getExternalFilesDir(null), fileName)
            FileOutputStream(outFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            ConnectionManager.sendFile(outFile)
            // 静默截图：不弹 Toast，避免 Activity finish 后 Toast 崩溃
        } catch (e: Exception) {
            Log.e(TAG, "onScreenshotCaptured failed", e)
        } finally {
            // 延迟 finish，确保 ImageReader 回调中的资源全部释放完毕
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 100)
        }
    }

    private fun saveToGallery(bmp: Bitmap, fileName: String) {
        try {
            val resolver = contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhoneHub")
            }
            val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                resolver.openOutputStream(it)?.use { os ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, os)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveToGallery failed", e)
        }
    }

    override fun onDestroy() {
        try { projection?.stop() } catch (e: Exception) {}
        super.onDestroy()
    }
}
