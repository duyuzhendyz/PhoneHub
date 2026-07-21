package com.phonehub

import android.content.Context
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelayKt

class `ConnectionManager$triggerScreenshot$2`(private val `$ctx`: Context, continuation: Continuation<*>?) : SuspendLambda(2, continuation) {
    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return `ConnectionManager$triggerScreenshot$2`(`$ctx`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<*>): Any {
        return (create(coroutineScope, continuation) as `ConnectionManager$triggerScreenshot$2`).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        var result = result
        var performBackgroundScreenshot: Any? = null
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                label = 1
                if (DelayKt.delay(1000L, this) == coroutine_suspended) {
                    return coroutine_suspended
                }
                label = 2
                performBackgroundScreenshot = ConnectionManager.performBackgroundScreenshot(this)
                if (performBackgroundScreenshot == coroutine_suspended) {
                    return coroutine_suspended
                }
                result = performBackgroundScreenshot!!
            }
            1 -> {
                ResultKt.throwOnFailure(result)
                label = 2
                performBackgroundScreenshot = ConnectionManager.performBackgroundScreenshot(this)
                if (performBackgroundScreenshot == coroutine_suspended) {
                    return coroutine_suspended
                }
                result = performBackgroundScreenshot!!
            }
            2 -> {
                ResultKt.throwOnFailure(result)
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
        val success = result as Boolean
        if (!success) {
            ConnectionManager.launchScreenshotActivity(this.`$ctx`)
        }
        return Unit
    }
}
