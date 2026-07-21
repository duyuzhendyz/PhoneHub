package com.phonehub

import android.widget.TextView
import kotlin.KotlinNothingValueException
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

class `MainActivity$getRemoteView$4`(private val `$mediaInfoText`: TextView, continuation: Continuation<*>?) : SuspendLambda(2, continuation) {
    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return `MainActivity$getRemoteView$4`(`$mediaInfoText`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<*>): Any {
        return (create(coroutineScope, continuation) as `MainActivity$getRemoteView$4`).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val mediaInfo: StateFlow<String> = ConnectionManager.getMediaInfo()
                val textView = `$mediaInfoText`
                label = 1
                val collectResult = mediaInfo.collect(object : FlowCollector<String> {
                    override suspend fun emit(info: String) {
                        val textView2 = textView
                        if (textView2 != null) {
                            textView2.text = info
                        }
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
