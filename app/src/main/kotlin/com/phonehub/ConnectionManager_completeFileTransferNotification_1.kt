package com.phonehub

import android.app.NotificationManager
import androidx.lifecycle.CoroutineLiveDataKt
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelayKt

class ConnectionManager_completeFileTransferNotification_1(
    private val mgr: NotificationManager,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_completeFileTransferNotification_1(this.mgr, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_completeFileTransferNotification_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                this.label = 1
                if (DelayKt.delay(CoroutineLiveDataKt.DEFAULT_TIMEOUT, this) == coroutine_suspended) {
                    return coroutine_suspended
                }
            }
            1 -> {
                ResultKt.throwOnFailure(result)
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
        try {
            this.mgr.cancel(88881)
        } catch (e: Exception) {
        }
        return Unit
    }
}
