package com.phonehub

import android.content.Context
import android.util.Log
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope

class `ConnectionManager$triggerScreenshot$3`(private val `$ctx`: Context, continuation: Continuation<*>?) : SuspendLambda(2, continuation) {
    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return `ConnectionManager$triggerScreenshot$3`(`$ctx`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<*>): Any {
        return (create(coroutineScope, continuation) as `ConnectionManager$triggerScreenshot$3`).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        var result = result
        var performBackgroundScreenshot: Any? = null
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                label = 1
                performBackgroundScreenshot = ConnectionManager.performBackgroundScreenshot()
                if (performBackgroundScreenshot != coroutine_suspended) {
                    result = performBackgroundScreenshot!!
                } else {
                    return coroutine_suspended
                }
            }
            1 -> {
                ResultKt.throwOnFailure(result)
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
        val success = result as Boolean
        if (!success) {
            Log.w("PhoneHub", "后台截图失败，回退到 Activity 授权截图")
            ConnectionManager.launchScreenshotActivity(`$ctx`)
        }
        return Unit
    }
}
