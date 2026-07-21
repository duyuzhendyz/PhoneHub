package com.phonehub

import androidx.lifecycle.CoroutineLiveDataKt
import com.phonehub.ConnectionManager
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineScopeKt
import kotlinx.coroutines.DelayKt

class ConnectionManager_startAdbWatchdog_1(
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0
    var L$0: Any? = null

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        val instance = ConnectionManager_startAdbWatchdog_1(continuation)
        instance.L$0 = obj
        return instance
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_startAdbWatchdog_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        val $this$launch: CoroutineScope = when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                this.L$0 as CoroutineScope
            }
            1 -> {
                ResultKt.throwOnFailure(result)
                this.L$0 as CoroutineScope
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
        while (CoroutineScopeKt.isActive($this$launch)) {
            this.L$0 = $this$launch
            this.label = 1
            if (DelayKt.delay(CoroutineLiveDataKt.DEFAULT_TIMEOUT, this) == coroutine_suspended) {
                return coroutine_suspended
            }
            if (ConnectionManager.INSTANCE.getUserConnectedIntent()) {
                val adb = ConnectionManager.INSTANCE.isAdbAvailable()
                val mutableStateFlow = ConnectionManager.INSTANCE.currentChannel
                val cur = mutableStateFlow.value
                if (adb && cur != ConnectionManager.ChannelType.ADB) {
                    ConnectionManager.INSTANCE.switchChannelImmediate(ConnectionManager.ChannelType.ADB)
                } else if (!adb && cur == ConnectionManager.ChannelType.ADB) {
                    ConnectionManager.INSTANCE.downgradeFromAdb()
                }
            }
        }
        return Unit
    }
}
