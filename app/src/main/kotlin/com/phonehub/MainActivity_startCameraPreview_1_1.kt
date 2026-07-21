package com.phonehub

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.HashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineScopeKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity_startCameraPreview_1_1(
    private val mainActivity: MainActivity
) : ImageAnalysis.Analyzer {

    private var frameCount: Long = 0
    private var lastFrameTime: Long = 0
    private var localFrameTimeoutRunnable: Runnable? = null

    override fun analyze(image: ImageProxy) {
        var it: Bitmap? = null
        var rotation: Int = 0
        var i: Int = 0
        var needMirror: Boolean = false
        var orientedBitmap: Bitmap? = null
        var it2: Runnable? = null
        var handler: Handler? = null
        var handler2: Handler? = null
        this.frameCount++
        if (this.frameCount % 3 != 0L) {
            image.close()
            return
        }
        try {
            val plane: ImageProxy.PlaneProxy = image.planes[0]
            val buffer: ByteBuffer = plane.buffer
            val pixelStride: Int = plane.pixelStride
            val rowStride: Int = plane.rowStride
            val rowPadding: Int = rowStride - (image.width * pixelStride)
            if (rowPadding == 0) {
                it = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                buffer.rewind()
                it.copyPixelsFromBuffer(buffer)
            } else {
                val paddedWidth: Int = image.width + (rowPadding / pixelStride)
                val padded: Bitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                buffer.rewind()
                padded.copyPixelsFromBuffer(buffer)
                val createBitmap: Bitmap = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                padded.recycle()
                it = createBitmap
            }
            rotation = image.imageInfo.rotationDegrees
            i = this.mainActivity.cameraLensFacing
            needMirror = i == 0
        } catch (e: Exception) {
        } catch (th: Throwable) {
            image.close()
            throw th
        }
        val srcBitmap: Bitmap = it ?: run { image.close(); return }
        if (rotation == 0 && !needMirror) {
            orientedBitmap = srcBitmap
            val baos = ByteArrayOutputStream()
            orientedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            val jpegData: ByteArray = baos.toByteArray()
            CoroutineScopeKt.CoroutineScope(Dispatchers.IO).launch {
                ConnectionManager.INSTANCE.sendFrameToPc(jpegData, "camera")
            }
            orientedBitmap.recycle()
            this.lastFrameTime = System.currentTimeMillis()
            it2 = this.localFrameTimeoutRunnable
            if (it2 != null) {
                handler2 = this.mainActivity.frameTimeoutHandler
                handler2.removeCallbacks(it2)
            }
            val it3 = Runnable {
                `analyze$lambda$5`(this.mainActivity)
            }
            handler = this.mainActivity.frameTimeoutHandler
            handler.postDelayed(it3, 2000L)
            this.localFrameTimeoutRunnable = it3
            image.close()
            return
        }
        val matrix = Matrix()
        if (rotation != 0) {
            matrix.postRotate(rotation.toFloat())
        }
        if (needMirror) {
            matrix.postScale(-1.0f, 1.0f)
        }
        orientedBitmap = Bitmap.createBitmap(srcBitmap, 0, 0, srcBitmap.width, srcBitmap.height, matrix, true)
        if (orientedBitmap != srcBitmap) {
            srcBitmap.recycle()
        }
        val baos2 = ByteArrayOutputStream()
        orientedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos2)
        val jpegData2: ByteArray = baos2.toByteArray()
        CoroutineScopeKt.CoroutineScope(Dispatchers.IO).launch {
            ConnectionManager.INSTANCE.sendFrameToPc(jpegData2, "camera")
        }
        orientedBitmap.recycle()
        this.lastFrameTime = System.currentTimeMillis()
        it2 = this.localFrameTimeoutRunnable
        if (it2 != null) {
            handler2 = this.mainActivity.frameTimeoutHandler
            handler2.removeCallbacks(it2)
        }
        val it32 = Runnable {
            `analyze$lambda$5`(this.mainActivity)
        }
        handler = this.mainActivity.frameTimeoutHandler
        handler.postDelayed(it32, 2000L)
        this.localFrameTimeoutRunnable = it32
        image.close()
    }

    companion object {
        fun `analyze$lambda$5`(mainActivity: MainActivity) {
            val z: Boolean = mainActivity.cameraPreviewRunning
            if (z) {
                mainActivity.stopCameraPreview()
                ConnectionManager.INSTANCE.sendMediaCommand("mirror_stop")
                mainActivity.runOnUiThread {
                    `analyze$lambda$5$lambda$4`(mainActivity)
                }
            }
        }

        fun `analyze$lambda$5$lambda$4`(mainActivity: MainActivity) {
            val hashMap: HashMap<Int, View> = mainActivity.pageCache
            val view: View? = hashMap.get(9) as? View
            if (view != null) {
                val button: Button? = view.findViewById(R.id.btnCameraStart) as? Button
                if (button != null) {
                    button.text = "启动推流"
                }
            }
            val hashMap2: HashMap<Int, View> = mainActivity.pageCache
            val view2: View? = hashMap2.get(9) as? View
            if (view2 == null) {
                return
            }
            val textView: TextView? = view2.findViewById(R.id.cameraStatus) as? TextView
            if (textView == null) {
                return
            }
            textView.text = "摄像头已断开"
        }
    }
}
