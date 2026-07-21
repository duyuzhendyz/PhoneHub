package com.phonehub

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.phonehub.ConnectionManager
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

class MainActivity_setupFlows_16(
    private val mainActivity: MainActivity,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return MainActivity_setupFlows_16(this.mainActivity, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as MainActivity_setupFlows_16).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val pcCameraFrame: SharedFlow<ByteArray> = ConnectionManager.pcCameraFrame
                val mainActivity: MainActivity = this.mainActivity
                this.label = 1
                val collectResult = pcCameraFrame.collect(object : FlowCollector<ByteArray> {
                    override suspend fun emit(value: ByteArray) {
                        val bmp: Bitmap? = BitmapFactory.decodeByteArray(value, 0, value.size)
                        if (bmp != null) {
                            BuildersKt.withContext(Dispatchers.Main, C00101(mainActivity, bmp, null), null)
                        }
                    }
                }, this)
                if (collectResult == coroutine_suspended) {
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

    private inner class C00101(
        private val activity: MainActivity,
        private val bmp: Bitmap,
        continuation: Continuation<Unit>?
    ) : SuspendLambda(2, continuation) {

        var label: Int = 0

        override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
            return C00101(this.activity, this.bmp, continuation)
        }

        override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
            return (create(coroutineScope, continuation) as C00101).invokeSuspend(Unit)
        }

        override fun invokeSuspend(result: Any): Any {
            when (this.label) {
                0 -> {
                    ResultKt.throwOnFailure(result)
                    val imageView: ImageView? = this.activity.cameraImageView
                    if (imageView != null) {
                        imageView.setImageBitmap(this.bmp)
                    }
                    val it: Runnable? = this.activity.cameraFrameTimeoutRunnable
                    if (it != null) {
                        val handler2: Handler = this.activity.frameTimeoutHandler
                        handler2.removeCallbacks(it)
                    }
                    val handler: Handler = this.activity.frameTimeoutHandler
                    handler.postDelayed(Runnable {
                        invokeSuspendLambda1(this.activity)
                    }, 2000L)
                    return Unit
                }
                else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }
    }

    fun invokeSuspendLambda1(activity: MainActivity) {
        val imageView: ImageView? = activity.cameraImageView
        if (imageView != null) {
            imageView.setImageBitmap(null)
        }
        val imageView2: ImageView? = activity.cameraImageView
        if (imageView2 != null) {
            imageView2.visibility = 8
        }
        ConnectionManager.sendMediaCommand("pc_camera_stop")
        ConnectionManager.stopPcCameraPolling()
        val hashMap = activity.pageCache
        val view = hashMap[9] as? View
        val button = view?.findViewById<Button>(R.id.btnCameraStop)
        if (button != null) {
            button.text = "查看电脑摄像头"
        }
        val hashMap2 = activity.pageCache
        val view2 = hashMap2[9] as? View
        val textView = view2?.findViewById<TextView>(R.id.cameraStatus)
        if (textView != null) {
            textView.text = "电脑摄像头已断开"
        }
    }
}
