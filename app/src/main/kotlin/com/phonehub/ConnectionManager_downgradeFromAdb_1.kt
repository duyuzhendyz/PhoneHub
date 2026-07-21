package com.phonehub

import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope

class `ConnectionManager$downgradeFromAdb$1`(continuation: Continuation<*>?) : SuspendLambda(2, continuation) {
    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return `ConnectionManager$downgradeFromAdb$1`(continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<*>): Any {
        return (create(coroutineScope, continuation) as `ConnectionManager$downgradeFromAdb$1`).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        var result = result
        var str: String? = null
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                str = ConnectionManager.pcIp
                if (str == null) {
                    str = "192.168.3.9"
                }
                val ip = str
                label = 1
                val testConnectionResult = ConnectionManager.testConnection(ip)
                if (testConnectionResult != coroutine_suspended) {
                    result = testConnectionResult
                } else {
                    return coroutine_suspended
                }
            }
            1 -> {
                ResultKt.throwOnFailure(result)
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
        if ((result as Boolean)) {
            ConnectionManager.switchChannelImmediate(ConnectionManager.ChannelType.WIFI)
        }
        return Unit
    }
}
