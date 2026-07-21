package com.phonehub

import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope

class ConnectionManager_sendFile_2_accepted_1(
    private val deferred: CompletableDeferred<Boolean>,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_sendFile_2_accepted_1(this.deferred, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Boolean>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_sendFile_2_accepted_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                this.label = 1
                val await = this.deferred.await(this)
                return if (await == coroutine_suspended) coroutine_suspended else await
            }
            1 -> {
                ResultKt.throwOnFailure(result)
                return result
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
    }
}
