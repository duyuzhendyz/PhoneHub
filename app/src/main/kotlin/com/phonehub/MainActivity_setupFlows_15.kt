package com.phonehub

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.widget.ImageView
import com.phonehub.MainActivity
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.BuildersKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow

class MainActivity_setupFlows_15(
    private val mainActivity: MainActivity,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return MainActivity_setupFlows_15(this.mainActivity, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as MainActivity_setupFlows_15).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val pcFrame: SharedFlow<ByteArray> = ConnectionManager.INSTANCE.pcFrame
                val mainActivity = this.mainActivity
                this.label = 1
                val collect = pcFrame.collect(object : FlowCollector<ByteArray> {
                    override suspend fun emit(value: ByteArray) {
                        val jpegBytes = value
                        val bmp: Bitmap? = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                        if (bmp == null) {
                            return
                        }
                        val withContext = BuildersKt.withContext(Dispatchers.getMain(), MainActivity_setupFlows_15_C00091(mainActivity, bmp, null), this)
                        if (withContext == IntrinsicsKt.getCOROUTINE_SUSPENDED()) {
                            return withContext
                        }
                    }
                }, this)
                if (collect == coroutine_suspended) {
                    return coroutine_suspended
                }
            }
            1 -> {
                ResultKt.throwOnFailure(result)
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
        return Unit
    }
}

class MainActivity_setupFlows_15_C00091(
    private val mainActivity: MainActivity,
    private val bmp: Bitmap,
    continuation: Continuation<*>?
) : SuspendLambda(2, continuation) {

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return MainActivity_setupFlows_15_C00091(this.mainActivity, this.bmp, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as MainActivity_setupFlows_15_C00091).invokeSuspend(Unit)
    }

    override fun invokeSuspend(obj: Any): Any {
        var imageView: ImageView? = null
        var it: Runnable? = null
        var handler: Handler? = null
        var handler2: Handler? = null
        IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(obj)
                imageView = this.mainActivity.mirrorImageView
                if (imageView != null) {
                    imageView.setImageBitmap(this.bmp)
                }
                it = this.mainActivity.mirrorFrameTimeoutRunnable
                if (it != null) {
                    handler2 = this.mainActivity.frameTimeoutHandler
                    handler2?.removeCallbacks(it)
                }
                val mainActivity = this.mainActivity
                val it2 = Runnable {
                    MainActivity_setupFlows_15_C00091.`invokeSuspend$lambda$1`(this.mainActivity)
                }
                handler = this.mainActivity.frameTimeoutHandler
                handler?.postDelayed(it2, 2000L)
                mainActivity.mirrorFrameTimeoutRunnable = it2
                return Unit
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
    }

    companion object {
        fun `invokeSuspend$lambda$1`(mainActivity: MainActivity) {
            val z = mainActivity.isMirrorFullscreen
            if (z) {
                mainActivity.exitMirrorFullscreen()
            }
            val imageView = mainActivity.mirrorImageView
            if (imageView != null) {
                imageView.setImageBitmap(null)
            }
            ConnectionManager.INSTANCE.sendMediaCommand("pc_stream_stop")
            ConnectionManager.INSTANCE.stopPcFramePolling()
        }
    }
}
