package com.phonehub

import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda

class ConnectionManager_handlePollFailure_1(
    private val outer: ConnectionManager,
    continuation: Continuation<*>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_handlePollFailure_1(this.outer, continuation)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        this.label |= Int.MIN_VALUE
        val handlePollFailure = outer.handlePollFailure(null, null, this)
        return if (handlePollFailure == coroutine_suspended) coroutine_suspended else Unit
    }
}
