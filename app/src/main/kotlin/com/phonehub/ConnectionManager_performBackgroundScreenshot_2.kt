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
import androidx.constraintlayout.widget.ConstraintLayout
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
import kotlin.coroutines.jvm.internal.Boxing
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.io.CloseableKt
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.BuildersKt__Builders_commonKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelayKt
import kotlinx.coroutines.flow.MutableSharedFlow

class ConnectionManager {
    var label: Int? = null

    public ConnectionManager$performBackgroundScreenshot$2(Continuation<? super ConnectionManager$performBackgroundScreenshot$2> continuation) {
        super(2, continuation)
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$performBackgroundScreenshot$2(continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Boolean>): Any {
        return ((ConnectionManager$performBackgroundScreenshot$2) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun invokeSuspend(obj: Any): Any {
        var context: Context? = null
        var obj2: Any? = null
        var imageReader: ImageReader? = null
        var virtualDisplay: VirtualDisplay? = null
        var th: Throwable? = null
        var z: Boolean? = null
        var coroutineScope: CoroutineScope? = null
        var context2: Context? = null
        var cachedMediaProjection: MediaProjection? = null
        var r4: ??? = null
        var bitmap: Bitmap? = null
        var coroutineScope2: CoroutineScope? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure(obj)
            if (Build.VERSION.SDK_INT >= 34) {
                context = ConnectionManager.context
                if (context == null) {
                    return Boxing.boxBoolean(false)
                    }
                if (!ScreenCaptureService.INSTANCE.isRunning()) {
                    ScreenCaptureService.INSTANCE.start(context)
                    this.label = 1
                    if (DelayKt.delay(1500L, this) == coroutine_suspended) {
                        var coroutine_suspended: return? = null
                        }
                    obj2 = obj
                    }
                if (!ScreenCaptureService.INSTANCE.isRunning()) {
                    Log.w("PhoneHub", "ScreenCaptureService 未启动，无法截图")
                    return Boxing.boxBoolean(false)
                    }
                }
            val mediaProjection: MediaProjection = null
            imageReader = null
            virtualDisplay = null
            try {
                try {
                    context2 = ConnectionManager.context
                    } catch (Exception e) {
                    e = e
                    z = false
                    }
                if (context2 != null && (cachedMediaProjection = ConnectionManager.INSTANCE.getCachedMediaProjection()) != null) {
                    try {
                        try {
                            val systemService: Any = context2.getSystemService("window")
                            Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.view.WindowManager")
                            val displayMetrics: DisplayMetrics = new DisplayMetrics()
                            ((WindowManager) systemService).getDefaultDisplay().getRealMetrics(displayMetrics)
                            val i: Int = displayMetrics.widthPixels
                            val i2: Int = displayMetrics.heightPixels
                            val i3: Int = displayMetrics.densityDpi
                            imageReader = ImageReader.newInstance(i, i2, 1, 2)
                            virtualDisplay = cachedMediaProjection.createVirtualDisplay("PhoneHubBgScreenshot", i, i2, i3, 16, imageReader.getSurface(), null, null)
                            final Ref.ObjectRef objectRef = new Ref.ObjectRef()
                            val countDownLatch: CountDownLatch = new CountDownLatch(1)
                            try {
                                imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() { // from class: com.phonehub.ConnectionManager$performBackgroundScreenshot$2$$ExternalSyntheticLambda0
                                    override
                                    fun onImageAvailable(imageReader2: ImageReader): Unit {
                                        ConnectionManager$performBackgroundScreenshot$2.invokeSuspend$lambda$0(i, i2, objectRef, countDownLatch, imageReader2)
                                        }
                                    }, Handler(Looper.getMainLooper()))
                                r4 = 3
                                countDownLatch.await(3L, TimeUnit.SECONDS)
                                bitmap = (Bitmap) objectRef.element
                                } catch (Exception e2) {
                                e = e2
                                r4 = 0
                                }
                            try {
                                } catch (Exception e3) {
                                e = e3
                                mediaProjection = cachedMediaProjection
                                z = r4
                                Log.e("PhoneHub", "performBackgroundScreenshot failed", e)
                                coroutineScope = ConnectionManager.scope
                                BuildersKt__Builders_commonKt.launch$default(coroutineScope, null, null, AnonymousClass4(e, null), 3, null)
                                if (virtualDisplay != null) {
                                    try {
                                        virtualDisplay.release()
                                        } catch (Exception e4) {
                                        }
                                    }
                                if (imageReader != null) {
                                    try {
                                        imageReader.close()
                                        } catch (Exception e5) {
                                        }
                                    }
                                if (mediaProjection != null) {
                                    try {
                                        mediaProjection.stop()
                                        } catch (Exception e6) {
                                        }
                                    }
                                return Boxing.boxBoolean(z)
                                }
                            } catch (Exception e7) {
                            e = e7
                            r4 = 0
                            }
                        if (bitmap != null) {
                            val boxBoolean: Boolean = Boxing.boxBoolean(false)
                            if (virtualDisplay != null) {
                                try {
                                    virtualDisplay.release()
                                    } catch (Exception e8) {
                                    }
                                }
                            if (imageReader != null) {
                                try {
                                    imageReader.close()
                                    } catch (Exception e9) {
                                    }
                                }
                            try {
                                cachedMediaProjection.stop()
                                } catch (Exception e10) {
                                }
                            var boxBoolean: return? = null
                            }
                        val str: String = "screenshot_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".png"
                        ConnectionManager.INSTANCE.saveBitmapToGallery(context2, bitmap, str)
                        val file: File = new File(context2.getExternalFilesDir(null), str)
                        val fileOutputStream: FileOutputStream = new FileOutputStream(file)
                        try {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
                            CloseableKt.closeFinally(fileOutputStream, null)
                            ConnectionManager.INSTANCE.sendFile(file)
                            Log.d("PhoneHub", "后台静默截图成功: " + str)
                            coroutineScope2 = ConnectionManager.scope
                            BuildersKt__Builders_commonKt.launch$default(coroutineScope2, null, null, AnonymousClass3(str, null), 3, null)
                            if (virtualDisplay != null) {
                                try {
                                    virtualDisplay.release()
                                    } catch (Exception e11) {
                                    }
                                }
                            if (imageReader != null) {
                                try {
                                    imageReader.close()
                                    } catch (Exception e12) {
                                    }
                                }
                            try {
                                cachedMediaProjection.stop()
                                } catch (Exception e13) {
                                }
                            z = true
                            return Boxing.boxBoolean(z)
                            } catch (Throwable th2) {
                            try {
                                var th2: throw? = null
                                } catch (Throwable th3) {
                                CloseableKt.closeFinally(fileOutputStream, th2)
                                var th3: throw? = null
                                }
                            }
                        } catch (Throwable th4) {
                        th = th4
                        mediaProjection = cachedMediaProjection
                        if (0 != 0) {
                            try {
                                virtualDisplay.release()
                                } catch (Exception e14) {
                                }
                            }
                        if (0 != 0) {
                            try {
                                imageReader.close()
                                } catch (Exception e15) {
                                }
                            }
                        if (mediaProjection == null) {
                            var th: throw? = null
                            }
                        try {
                            mediaProjection.stop()
                            var th: throw? = null
                            } catch (Exception e16) {
                            var th: throw? = null
                            }
                        }
                    }
                return Boxing.boxBoolean(false)
                } catch (Throwable th5) {
                th = th5
                }
            break
            case 1:
            obj2 = obj
            ResultKt.throwOnFailure(obj2)
            if (!ScreenCaptureService.INSTANCE.isRunning()) {
                }
            val mediaProjection2: MediaProjection = null
            imageReader = null
            virtualDisplay = null
            context2 = ConnectionManager.context
            if (context2 != null) {
                val systemService2: Any = context2.getSystemService("window")
                Intrinsics.checkNotNull(systemService2, "null cannot be cast to non-null type android.view.WindowManager")
                val displayMetrics2: DisplayMetrics = new DisplayMetrics()
                ((WindowManager) systemService2).getDefaultDisplay().getRealMetrics(displayMetrics2)
                val i4: Int = displayMetrics2.widthPixels
                val i22: Int = displayMetrics2.heightPixels
                val i32: Int = displayMetrics2.densityDpi
                imageReader = ImageReader.newInstance(i4, i22, 1, 2)
                virtualDisplay = cachedMediaProjection.createVirtualDisplay("PhoneHubBgScreenshot", i4, i22, i32, 16, imageReader.getSurface(), null, null)
                final Ref.ObjectRef objectRef2 = new Ref.ObjectRef()
                val countDownLatch2: CountDownLatch = new CountDownLatch(1)
                imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() { // from class: com.phonehub.ConnectionManager$performBackgroundScreenshot$2$$ExternalSyntheticLambda0
                    override
                    fun onImageAvailable(imageReader2: ImageReader): Unit {
                        ConnectionManager$performBackgroundScreenshot$2.invokeSuspend$lambda$0(i4, i22, objectRef2, countDownLatch2, imageReader2)
                        }
                    }, Handler(Looper.getMainLooper()))
                r4 = 3
                countDownLatch2.await(3L, TimeUnit.SECONDS)
                bitmap = (Bitmap) objectRef2.element
                if (bitmap != null) {
                    }
                break
                } else {
                return Boxing.boxBoolean(false)
                }
            break
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }

    public static final Unit invokeSuspend$lambda$0(int $w, int $h, Ref.ObjectRef $captured, CountDownLatch $latch, ImageReader reader) {
        val image: Image = reader.acquireLatestImage()
        try {
            if (image != null) {
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
                    $captured.element = Bitmap.createBitmap(bmp, 0, 0, $w, $h)
                    } catch (Exception e) {
                    Log.e("PhoneHub", "后台截图 Image 处理失败", e)
                    }
                image.close()
                $latch.countDown()
                }
            } catch (Throwable th) {
            image.close()
            var th: throw? = null
            }
        }

    public static final class AnonymousClass3 extends SuspendLambda implements Function2<CoroutineScope, Continuation<? super Unit>, Object> {
        final  String $fileName
        var label: Int? = null

        AnonymousClass3(String str, Continuation<? super AnonymousClass3> continuation) {
            super(2, continuation)
            this.$fileName = str
            }

        override
        fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
            return AnonymousClass3(this.$fileName, continuation)
            }

        override
        fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
            return ((AnonymousClass3) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
            }

        override
        fun invokeSuspend(/* Object $result */): Any {
            var mutableSharedFlow: MutableSharedFlow? = null
            val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
            switch (this.label) {
                case 0:
                ResultKt.throwOnFailure($result)
                mutableSharedFlow = ConnectionManager._screenshotResult
                val str: String = this.$fileName
                this.label = 1
                if (mutableSharedFlow.emit("截图已保存到手机相册: " + str, this) == coroutine_suspended) {
                    var coroutine_suspended: return? = null
                    }
                break
                case 1:
                ResultKt.throwOnFailure($result)
                break
                default:
                throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
                }
            return Unit.INSTANCE
            }
        }

    public static final class AnonymousClass4 extends SuspendLambda implements Function2<CoroutineScope, Continuation<? super Unit>, Object> {
        final  Exception $e
        var label: Int? = null

        AnonymousClass4(Exception exc, Continuation<? super AnonymousClass4> continuation) {
            super(2, continuation)
            this.$e = exc
            }

        override
        fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
            return AnonymousClass4(this.$e, continuation)
            }

        override
        fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
            return ((AnonymousClass4) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
            }

        override
        fun invokeSuspend(/* Object $result */): Any {
            var mutableSharedFlow: MutableSharedFlow? = null
            val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
            switch (this.label) {
                case 0:
                ResultKt.throwOnFailure($result)
                mutableSharedFlow = ConnectionManager._screenshotResult
                val message: String = this.$e.getMessage()
                if (message == null) {
                    message = "未知错误"
                    }
                this.label = 1
                if (mutableSharedFlow.emit("截图失败: " + message, this) == coroutine_suspended) {
                    var coroutine_suspended: return? = null
                    }
                break
                case 1:
                ResultKt.throwOnFailure($result)
                break
                default:
                throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
                }
            return Unit.INSTANCE
            }
        }
    }
