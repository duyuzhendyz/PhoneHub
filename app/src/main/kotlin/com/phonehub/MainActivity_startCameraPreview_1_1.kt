package com.phonehub

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.constraintlayout.widget.ConstraintLayout
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.HashMap
import kotlinx.coroutines.BuildersKt__Builders_commonKt
import kotlinx.coroutines.CoroutineScopeKt
import kotlinx.coroutines.Dispatchers

class MainActivity {
    var frameCount: private long? = null
    var lastFrameTime: private long? = null
    var localFrameTimeoutRunnable: private Runnable? = null
    final  MainActivity this$0

    public MainActivity$startCameraPreview$1$1(MainActivity $receiver) {
        this.this$0 = $receiver
        }

    override
    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun analyze(image: ImageProxy): Unit {
        var it: Bitmap? = null
        var rotation: Int? = null
        var i: Int? = null
        var needMirror: Boolean? = null
        var orientedBitmap: Bitmap? = null
        var it2: Runnable? = null
        var handler: Handler? = null
        var handler2: Handler? = null
        Intrinsics.checkNotNullParameter(image, "image")
        this.frameCount++
        if (this.frameCount % 3 != 0) {
            image.close()
            return
            }
        try {
            ImageProxy.PlaneProxy plane = image.getPlanes()[0]
            val buffer: ByteBuffer = plane.getBuffer()
            Intrinsics.checkNotNullExpressionValue(buffer, "getBuffer(...)")
            val pixelStride: Int = plane.getPixelStride()
            val rowStride: Int = plane.getRowStride()
            val rowPadding: Int = rowStride - (image.getWidth() * pixelStride)
            if (rowPadding == 0) {
                it = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888)
                buffer.rewind()
                it.copyPixelsFromBuffer(buffer)
                } else {
                val paddedWidth: Int = image.getWidth() + (rowPadding / pixelStride)
                val padded: Bitmap = Bitmap.createBitmap(paddedWidth, image.getHeight(), Bitmap.Config.ARGB_8888)
                Intrinsics.checkNotNullExpressionValue(padded, "createBitmap(...)")
                buffer.rewind()
                padded.copyPixelsFromBuffer(buffer)
                val createBitmap: Bitmap = Bitmap.createBitmap(padded, 0, 0, image.getWidth(), image.getHeight())
                padded.recycle()
                it = createBitmap
                }
            Intrinsics.checkNotNull(it)
            rotation = image.getImageInfo().getRotationDegrees()
            i = this.this$0.cameraLensFacing
            needMirror = i == 0
            } catch (Exception e) {
            } catch (Throwable th) {
            image.close()
            var th: throw? = null
            }
        if (rotation == 0 && !needMirror) {
            orientedBitmap = it
            Intrinsics.checkNotNull(orientedBitmap)
            val baos: ByteArrayOutputStream = new ByteArrayOutputStream()
            orientedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            val jpegData: Array<Byte> = baos.toByteArray()
            BuildersKt__Builders_commonKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getIO()), null, null, new MainActivity$startCameraPreview$1$1$analyze$1(jpegData, null), 3, null)
            orientedBitmap.recycle()
            this.lastFrameTime = System.currentTimeMillis()
            it2 = this.localFrameTimeoutRunnable
            if (it2 != null) {
                handler2 = this.this$0.frameTimeoutHandler
                handler2.removeCallbacks(it2)
                }
            val mainActivity: MainActivity = this.this$0
            val it3: Runnable = new Runnable() { // from class: com.phonehub.MainActivity$startCameraPreview$1$1$$ExternalSyntheticLambda1
            override
            fun run(): Unit {
                MainActivity$startCameraPreview$1$1.analyze$lambda$5(MainActivity.this)
                }
            }
        handler = this.this$0.frameTimeoutHandler
        handler.postDelayed(it3, 2000L)
        this.localFrameTimeoutRunnable = it3
        image.close()
        }
    val matrix: Matrix = new Matrix()
    if (rotation != 0) {
        matrix.postRotate(rotation)
        }
    if (needMirror) {
        matrix.postScale(-1.0f, 1.0f)
        }
    orientedBitmap = Bitmap.createBitmap(it, 0, 0, it.getWidth(), it.getHeight(), matrix, true)
    if (orientedBitmap != it) {
        it.recycle()
        }
    Intrinsics.checkNotNull(orientedBitmap)
    val baos2: ByteArrayOutputStream = new ByteArrayOutputStream()
    orientedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos2)
    val jpegData2: Array<Byte> = baos2.toByteArray()
    BuildersKt__Builders_commonKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getIO()), null, null, new MainActivity$startCameraPreview$1$1$analyze$1(jpegData2, null), 3, null)
    orientedBitmap.recycle()
    this.lastFrameTime = System.currentTimeMillis()
    it2 = this.localFrameTimeoutRunnable
    if (it2 != null) {
        }
    val mainActivity2: MainActivity = this.this$0
    val it32: Runnable = new Runnable() { // from class: com.phonehub.MainActivity$startCameraPreview$1$1$$ExternalSyntheticLambda1
    override
    fun run(): Unit {
        MainActivity$startCameraPreview$1$1.analyze$lambda$5(MainActivity.this)
        }
    }
handler = this.this$0.frameTimeoutHandler
handler.postDelayed(it32, 2000L)
this.localFrameTimeoutRunnable = it32
image.close()
}

public static final Unit analyze$lambda$5(final MainActivity this$0) {
    var z: Boolean? = null
    z = this$0.cameraPreviewRunning
    if (z) {
        this$0.stopCameraPreview()
        ConnectionManager.INSTANCE.sendMediaCommand("mirror_stop")
        this$0.runOnUiThread(Runnable() { // from class: com.phonehub.MainActivity$startCameraPreview$1$1$$ExternalSyntheticLambda0
            override
            fun run(): Unit {
                MainActivity$startCameraPreview$1$1.analyze$lambda$5$lambda$4(MainActivity.this)
                }
            })
        }
    }

public static final Unit analyze$lambda$5$lambda$4(MainActivity this$0) {
    var hashMap: HashMap? = null
    var hashMap2: HashMap? = null
    var textView: TextView? = null
    var button: Button? = null
    hashMap = this$0.pageCache
    val view: View = (View) hashMap.get(9)
    if (view != null && (button = (Button) view.findViewById(R.id.btnCameraStart)) != null) {
        button.setText("启动推流")
        }
    hashMap2 = this$0.pageCache
    val view2: View = (View) hashMap2.get(9)
    if (view2 == null || (textView = (TextView) view2.findViewById(R.id.cameraStatus)) == null) {
        return
        }
    textView.setText("摄像头已断开")
    }
}
