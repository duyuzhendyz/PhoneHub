package com.phonehub

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import kotlin.KotlinNothingValueException
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

class MainActivity_getRemoteView_5(
    private val `$mediaCoverImg`: ImageView?,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return MainActivity_getRemoteView_5(this.`$mediaCoverImg`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as MainActivity_getRemoteView_5).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val mediaThumbnail: StateFlow<ByteArray?> = ConnectionManager.INSTANCE.mediaThumbnail
                val imageView: ImageView? = this.`$mediaCoverImg`
                label = 1
                val collectResult = mediaThumbnail.collect(object : FlowCollector<ByteArray?> {
                    override suspend fun emit(bytes: ByteArray?) {
                        if (bytes != null) {
                            if (bytes.isNotEmpty() && imageView != null) {
                                val bmp: Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bmp == null) {
                                    imageView.visibility = 8
                                } else {
                                    imageView.setImageBitmap(bmp)
                                    imageView.visibility = 0
                                }
                                return
                            }
                        }
                        val imageView2: ImageView? = imageView
                        imageView2?.visibility = 8
                    }
                })
                if (collectResult == coroutine_suspended) {
                    return coroutine_suspended
                }
            }
            1 -> {
                ResultKt.throwOnFailure(result)
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
        throw KotlinNothingValueException()
    }
}
