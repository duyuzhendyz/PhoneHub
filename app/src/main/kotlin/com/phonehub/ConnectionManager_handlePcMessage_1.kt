package com.phonehub

import kotlin.Pair
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow

class ConnectionManager_handlePcMessage_1(
    private val filename: String,
    private val txt: String,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_handlePcMessage_1(this.filename, this.txt, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_handlePcMessage_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        var mutableSharedFlow: MutableSharedFlow<Pair<String, String>>? = null
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                mutableSharedFlow = ConnectionManager._receivedText
                this.label = 1
                if (mutableSharedFlow!!.emit(Pair(this.filename, this.txt), this) == coroutine_suspended) {
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
