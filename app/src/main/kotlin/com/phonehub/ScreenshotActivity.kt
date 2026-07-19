package com.phonehub

import android.content.ContentResolver
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
import androidx.constraintlayout.widget.ConstraintLayout
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.io.CloseableKt

class ScreenshotActivity : AppCompatActivity {
    val REQ_MEDIA_PROJECTION: private static final int = 5001
    val TAG: private static final String = "PHScreenshot"
    var container: private LinearLayout? = null
    var hint: private TextView? = null
    var progress: private ProgressBar? = null
    var projection: private MediaProjection? = null
    var projectionManager: private MediaProjectionManager? = null

    override
    fun onCreate(savedInstanceState: Bundle): Unit {
        super.onCreate(savedInstanceState)
        LinearLayout $this$onCreate_u24lambda_u240 = LinearLayout(this)
        $this$onCreate_u24lambda_u240.setOrientation(1)
        $this$onCreate_u24lambda_u240.setGravity(17)
        $this$onCreate_u24lambda_u240.setPadding(48, 48, 48, 48)
        $this$onCreate_u24lambda_u240.setBackgroundColor(Color.parseColor("#cc000000"))
        this.container = $this$onCreate_u24lambda_u240
        TextView $this$onCreate_u24lambda_u241 = TextView(this)
        $this$onCreate_u24lambda_u241.setText("正在请求截屏权限...")
        $this$onCreate_u24lambda_u241.setTextColor(-1)
        $this$onCreate_u24lambda_u241.setTextSize(14.0f)
        $this$onCreate_u24lambda_u241.setPadding(0, 0, 0, 24)
        this.hint = $this$onCreate_u24lambda_u241
        ProgressBar $this$onCreate_u24lambda_u242 = ProgressBar(this)
        $this$onCreate_u24lambda_u242.setIndeterminate(true)
        this.progress = $this$onCreate_u24lambda_u242
        val linearLayout: LinearLayout = this.container
        val progressBar: ProgressBar = null
        if (linearLayout == null) {
            Intrinsics.throwUninitializedPropertyAccessException("container")
            linearLayout = null
            }
        val textView: TextView = this.hint
        if (textView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("hint")
            textView = null
            }
        linearLayout.addView(textView)
        val linearLayout2: LinearLayout = this.container
        if (linearLayout2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("container")
            linearLayout2 = null
            }
        val progressBar2: ProgressBar = this.progress
        if (progressBar2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("progress")
            progressBar2 = null
            }
        linearLayout2.addView(progressBar2)
        val linearLayout3: LinearLayout = this.container
        if (linearLayout3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("container")
            linearLayout3 = null
            }
        setContentView(linearLayout3)
        val systemService: Any = getSystemService("media_projection")
        this.projectionManager = systemService is MediaProjectionManager ? (MediaProjectionManager) systemService : null
        if (this.projectionManager == null) {
            val textView2: TextView = this.hint
            if (textView2 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("hint")
                textView2 = null
                }
            textView2.setText("当前设备不支持截屏")
            val progressBar3: ProgressBar = this.progress
            if (progressBar3 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("progress")
                } else {
                progressBar = progressBar3
                }
            progressBar.setVisibility(8)
            Handler(Looper.getMainLooper()).postDelayed(Runnable() { // from class: com.phonehub.ScreenshotActivity$$ExternalSyntheticLambda1
                override
                fun run(): Unit {
                    ScreenshotActivity.this.finish()
                    }
                }, 1500L)
            return
            }
        try {
            val mediaProjectionManager: MediaProjectionManager = this.projectionManager
            Intrinsics.checkNotNull(mediaProjectionManager)
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQ_MEDIA_PROJECTION)
            } catch (Exception e) {
            Log.e(TAG, "createScreenCaptureIntent failed", e)
            finish()
            }
        }

    override
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_MEDIA_PROJECTION) {
            if (resultCode != -1 || data == null) {
                finish()
                return
                }
            try {
                val mediaProjectionManager: MediaProjectionManager = this.projectionManager
                this.projection = mediaProjectionManager != null ? mediaProjectionManager.getMediaProjection(resultCode, data) : null
                ConnectionManager.INSTANCE.cacheMediaProjectionToken(resultCode, data)
                doScreenshot()
                return
                } catch (SecurityException e) {
                Log.e(TAG, "getMediaProjection failed", e)
                finish()
                return
                }
            }
        finish()
        }

    fun doScreenshot(): Unit {
        try {
            val metrics: DisplayMetrics = new DisplayMetrics()
            getWindowManager().getDefaultDisplay().getRealMetrics(metrics)
            val w: Int = metrics.widthPixels
            val h: Int = metrics.heightPixels
            val dpi: Int = metrics.densityDpi
            val imageReader: ImageReader = ImageReader.newInstance(w, h, 1, 2)
            Intrinsics.checkNotNullExpressionValue(imageReader, "newInstance(...)")
            val mediaProjection: MediaProjection = this.projection
            if (mediaProjection != null) {
                mediaProjection.registerCallback(new MediaProjection.Callback() { // from class: com.phonehub.ScreenshotActivity$doScreenshot$1
                    }, Handler(Looper.getMainLooper()))
                }
            val mediaProjection2: MediaProjection = this.projection
            val virtualDisplay: VirtualDisplay = mediaProjection2 != null ? mediaProjection2.createVirtualDisplay("PhoneHubScreenshot", w, h, dpi, 16, imageReader.getSurface(), null, null) : null
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() { // from class: com.phonehub.ScreenshotActivity$$ExternalSyntheticLambda2
                override
                fun onImageAvailable(imageReader2: ImageReader): Unit {
                    ScreenshotActivity.doScreenshot$lambda$4(w, h, this, virtualDisplay, imageReader, imageReader2)
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (Exception e) {
            Log.e(TAG, "doScreenshot failed", e)
            finish()
            }
        }

    /* JADX WARN: Code restructure failed: missing block: B:11:0x004f, code lost:

    if (r0 != null) goto L22
     */
    /* JADX WARN: Code restructure failed: missing block: B:12:0x006f, code lost:

    r14.close()
     */
    /* JADX WARN: Code restructure failed: missing block: B:13:0x0073, code lost:

    return
     */
    /* JADX WARN: Code restructure failed: missing block: B:14:0x006c, code lost:

    r0.stop()
     */
    /* JADX WARN: Code restructure failed: missing block: B:31:0x006a, code lost:

    if (r0 == null) goto L23
     */
    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    public static final Unit doScreenshot$lambda$4(int $w, int $h, ScreenshotActivity this$0, VirtualDisplay $virtualDisplay, ImageReader $imageReader, ImageReader reader) {
        var mediaProjection: MediaProjection? = null
        val image: Image = reader.acquireLatestImage()
        if (image == null) {
            return
            }
        try {
            try {
                Image.Plane[] planes = image.getPlanes()
                val buffer: ByteBuffer = planes[0].getBuffer()
                val pixelStride: Int = planes[0].getPixelStride()
                val rowStride: Int = planes[0].getRowStride()
                val rowPadding: Int = rowStride - (pixelStride * $w)
                val bmp: Bitmap = Bitmap.createBitmap((rowPadding / pixelStride) + $w, $h, Bitmap.Config.ARGB_8888)
                Intrinsics.checkNotNullExpressionValue(bmp, "createBitmap(...)")
                buffer.rewind()
                bmp.copyPixelsFromBuffer(buffer)
                val cropped: Bitmap = Bitmap.createBitmap(bmp, 0, 0, $w, $h)
                Intrinsics.checkNotNullExpressionValue(cropped, "createBitmap(...)")
                this$0.onScreenshotCaptured(cropped)
                image.close()
                if ($virtualDisplay != null) {
                    $virtualDisplay.release()
                    }
                mediaProjection = this$0.projection
                } catch (Exception e) {
                Log.e(TAG, "Image available handle failed", e)
                image.close()
                if ($virtualDisplay != null) {
                    $virtualDisplay.release()
                    }
                mediaProjection = this$0.projection
                }
            } catch (Throwable th) {
            image.close()
            if ($virtualDisplay != null) {
                $virtualDisplay.release()
                }
            val mediaProjection2: MediaProjection = this$0.projection
            if (mediaProjection2 != null) {
                mediaProjection2.stop()
                }
            $imageReader.close()
            var th: throw? = null
            }
        }

    fun onScreenshotCaptured(bmp: Bitmap): Unit {
        var handler: Handler? = null
        var runnable: Runnable? = null
        try {
            try {
                val ts: String = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date())
                val fileName: String = "screenshot_" + ts + ".png"
                saveToGallery(bmp, fileName)
                val outFile: File = new File(getExternalFilesDir(null), fileName)
                val fileOutputStream: FileOutputStream = new FileOutputStream(outFile)
                try {
                    val out: FileOutputStream = fileOutputStream
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    CloseableKt.closeFinally(fileOutputStream, null)
                    ConnectionManager.INSTANCE.sendFile(outFile)
                    handler = Handler(Looper.getMainLooper())
                    runnable = Runnable() { // from class: com.phonehub.ScreenshotActivity$$ExternalSyntheticLambda0
                        override
                        fun run(): Unit {
                            ScreenshotActivity.this.finish()
                            }
                        }
                    } finally {
                    }
                } catch (Throwable th) {
                Handler(Looper.getMainLooper()).postDelayed(Runnable() { // from class: com.phonehub.ScreenshotActivity$$ExternalSyntheticLambda0
                    override
                    fun run(): Unit {
                        ScreenshotActivity.this.finish()
                        }
                    }, 100L)
                var th: throw? = null
                }
            } catch (Exception e) {
            Log.e(TAG, "onScreenshotCaptured failed", e)
            handler = Handler(Looper.getMainLooper())
            runnable = Runnable() { // from class: com.phonehub.ScreenshotActivity$$ExternalSyntheticLambda0
                override
                fun run(): Unit {
                    ScreenshotActivity.this.finish()
                    }
                }
            }
        handler.postDelayed(runnable, 100L)
        }

    fun saveToGallery(bmp: Bitmap, fileName: String): Unit {
        var openOutputStream: OutputStream? = null
        try {
            val resolver: ContentResolver = getContentResolver()
            val values: ContentValues = new ContentValues()
            values.put("_display_name", fileName)
            values.put("mime_type", "image/png")
            values.put("relative_path", Environment.DIRECTORY_PICTURES + "/PhoneHub")
            val uri: Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null && (openOutputStream = resolver.openOutputStream(uri)) != null) {
                val outputStream: OutputStream = openOutputStream
                try {
                    val os: OutputStream = outputStream
                    Boolean.valueOf(bmp.compress(Bitmap.CompressFormat.PNG, 100, os))
                    CloseableKt.closeFinally(outputStream, null)
                    } finally {
                    }
                }
            } catch (Exception e) {
            Log.e(TAG, "saveToGallery failed", e)
            }
        }

    override
    fun onDestroy(): Unit {
        try {
            val mediaProjection: MediaProjection = this.projection
            if (mediaProjection != null) {
                mediaProjection.stop()
                }
            } catch (Exception e) {
            }
        super.onDestroy()
        }
    }
