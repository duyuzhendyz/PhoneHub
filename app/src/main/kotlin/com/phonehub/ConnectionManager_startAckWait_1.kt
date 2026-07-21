package com.phonehub

import android.util.Log
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineScopeKt
import kotlinx.coroutines.DelayKt

class ConnectionManager_startAckWait_1(
    private val fileId: String,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0
    var `L$0`: Any? = null
    var `I$0`: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        val instance = ConnectionManager_startAckWait_1(this.fileId, continuation)
        instance.`L$0` = obj
        return instance
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_startAckWait_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val launchScope = this.`L$0` as CoroutineScope
                val retries2 = 0
                if (CoroutineScopeKt.isActive(launchScope) || retries2 >= 3) {
                    ConnectionManager.ackTracker.remove(this.fileId)
                    return Unit
                }
                this.`L$0` = launchScope
                this.`I$0` = retries2
                this.label = 1
                if (DelayKt.delay(10000L, this) == coroutine_suspended) {
                    return coroutine_suspended
                }
                if (ConnectionManager.ackTracker.containsKey(this.fileId)) {
                    return Unit
                }
                val retries3 = retries2 + 1
                Log.i("PhoneHub", "ACK 超时重发 file_complete (" + retries3 + ") for " + this.fileId)
                ConnectionManager.INSTANCE.sendFileComplete(this.fileId)
                ConnectionManager.ackTracker.remove(this.fileId)
                return Unit
            }
            1 -> {
                val retries = this.`I$0`
                val launchScope3 = this.`L$0` as CoroutineScope
                ResultKt.throwOnFailure(result)
                if (ConnectionManager.ackTracker.containsKey(this.fileId)) {
                    // decompiler incomplete: original likely had return Unit here
                }
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
        return Unit
    }
}
