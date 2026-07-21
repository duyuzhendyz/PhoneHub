package com.phonehub

import android.widget.Toast
import kotlin.KotlinNothingValueException
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow

class `MainActivity$setupFlows$3`(private val `this$0`: MainActivity, continuation: Continuation<*>?) : SuspendLambda(2, continuation) {
    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return `MainActivity$setupFlows$3`(`this$0`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<*>): Any {
        return (create(coroutineScope, continuation) as `MainActivity$setupFlows$3`).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val screenshotResult: SharedFlow<String> = ConnectionManager.getScreenshotResult()
                val mainActivity = `this$0`
                label = 1
                val collectResult = screenshotResult.collect(object : FlowCollector<String> {
                    override suspend fun emit(msg: String) {
                        Toast.makeText(mainActivity, msg, 0).show()
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
