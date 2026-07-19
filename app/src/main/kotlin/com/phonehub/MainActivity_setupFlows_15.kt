package com.phonehub

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.phonehub.MainActivity$setupFlows$15
import kotlin.KotlinNothingValueException
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.BuildersKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow

class MainActivity {
    var label: Int? = null
    final  MainActivity this$0

    public MainActivity$setupFlows$15(MainActivity mainActivity, Continuation<? super MainActivity$setupFlows$15> continuation) {
        super(2, continuation)
        this.this$0 = mainActivity
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new MainActivity$setupFlows$15(this.this$0, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((MainActivity$setupFlows$15) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            val pcFrame: SharedFlow<Array<Byte>> = ConnectionManager.INSTANCE.getPcFrame()
            val mainActivity: MainActivity = this.this$0
            this.label = 1
            if (pcFrame.collect(FlowCollector() { // from class: com.phonehub.MainActivity$setupFlows$15.1
                override
                public   Object emit(Object value, Continuation $completion) {
                    return emit((byte[]) value, (Continuation<? super Unit>) $completion)
                    }

                fun emit(jpegBytes: Array<Byte>, continuation: Continuation<? super Unit>): Any {
                    var withContext: Any? = null
                    val bmp: Bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length)
                    return (bmp == null || (withContext = BuildersKt.withContext(Dispatchers.getMain(), C00091(MainActivity.this, bmp, null), continuation)) != IntrinsicsKt.getCOROUTINE_SUSPENDED()) ? Unit.INSTANCE : withContext
                    }

                public static final class C00091 extends SuspendLambda implements Function2<CoroutineScope, Continuation<? super Unit>, Object> {
                    final  Bitmap $bmp
                    var label: Int? = null
                    final  MainActivity this$0

                    C00091(MainActivity mainActivity, Bitmap bitmap, Continuation<? super C00091> continuation) {
                        super(2, continuation)
                        this.this$0 = mainActivity
                        this.$bmp = bitmap
                        }

                    override
                    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
                        return C00091(this.this$0, this.$bmp, continuation)
                        }

                    override
                    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
                        return ((C00091) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
                        }

                    override
                    fun invokeSuspend(obj: Any): Any {
                        var imageView: ImageView? = null
                        var it: Runnable? = null
                        var handler: Handler? = null
                        var handler2: Handler? = null
                        IntrinsicsKt.getCOROUTINE_SUSPENDED()
                        switch (this.label) {
                            case 0:
                            ResultKt.throwOnFailure(obj)
                            imageView = this.this$0.mirrorImageView
                            if (imageView != null) {
                                imageView.setImageBitmap(this.$bmp)
                                }
                            it = this.this$0.mirrorFrameTimeoutRunnable
                            if (it != null) {
                                handler2 = this.this$0.frameTimeoutHandler
                                handler2.removeCallbacks(it)
                                }
                            val mainActivity: MainActivity = this.this$0
                            val mainActivity2: MainActivity = this.this$0
                            val it2: Runnable = new Runnable() { // from class: com.phonehub.MainActivity$setupFlows$15$1$1$$ExternalSyntheticLambda0
                            override
                            fun run(): Unit {
                                MainActivity$setupFlows$15.AnonymousClass1.C00091.invokeSuspend$lambda$1(MainActivity.this)
                                }
                            }
                        handler = this.this$0.frameTimeoutHandler
                        handler.postDelayed(it2, 2000L)
                        mainActivity.mirrorFrameTimeoutRunnable = it2
                        return Unit.INSTANCE
                        default:
                        throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
                        }
                    }

                public static final Unit invokeSuspend$lambda$1(MainActivity this$0) {
                    var z: Boolean? = null
                    var imageView: ImageView? = null
                    z = this$0.isMirrorFullscreen
                    if (z) {
                        this$0.exitMirrorFullscreen()
                        }
                    imageView = this$0.mirrorImageView
                    if (imageView != null) {
                        imageView.setImageBitmap(null)
                        }
                    ConnectionManager.INSTANCE.sendMediaCommand("pc_stream_stop")
                    ConnectionManager.INSTANCE.stopPcFramePolling()
                    }
                }
            }, this) == coroutine_suspended) {
            var coroutine_suspended: return? = null
            }
        break
        case 1:
        ResultKt.throwOnFailure($result)
        break
        default:
        throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
    throw KotlinNothingValueException()
    }
}
