package com.phonehub

import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.CoroutineLiveDataKt
import com.phonehub.ConnectionManager
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineScopeKt
import kotlinx.coroutines.DelayKt
import kotlinx.coroutines.flow.MutableStateFlow

class ConnectionManager {
    private  Object L$0
    var label: Int? = null

    public ConnectionManager$startAdbWatchdog$1(Continuation<? super ConnectionManager$startAdbWatchdog$1> continuation) {
        super(2, continuation)
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        ConnectionManager$startAdbWatchdog$1 connectionManager$startAdbWatchdog$1 = new ConnectionManager$startAdbWatchdog$1(continuation)
        connectionManager$startAdbWatchdog$1.L$0 = obj
        return connectionManager$startAdbWatchdog$1
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$startAdbWatchdog$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun invokeSuspend(/* Object $result */): Any {
        CoroutineScope $this$launch
        var obj: Any? = null
        ConnectionManager$startAdbWatchdog$1 connectionManager$startAdbWatchdog$1
        var mutableStateFlow: MutableStateFlow? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            $this$launch = (CoroutineScope) this.L$0
            obj = coroutine_suspended
            connectionManager$startAdbWatchdog$1 = this
            if (CoroutineScopeKt.isActive($this$launch)) {
                connectionManager$startAdbWatchdog$1.L$0 = $this$launch
                connectionManager$startAdbWatchdog$1.label = 1
                if (DelayKt.delay(CoroutineLiveDataKt.DEFAULT_TIMEOUT, connectionManager$startAdbWatchdog$1) == obj) {
                    var obj: return? = null
                    }
                if (ConnectionManager.INSTANCE.getUserConnectedIntent()) {
                    val adb: Boolean = ConnectionManager.INSTANCE.isAdbAvailable()
                    mutableStateFlow = ConnectionManager._currentChannel
                    ConnectionManager.ChannelType cur = (ConnectionManager.ChannelType) mutableStateFlow.getValue()
                    if (adb && cur != ConnectionManager.ChannelType.ADB) {
                        ConnectionManager.INSTANCE.switchChannelImmediate(ConnectionManager.ChannelType.ADB)
                        } else if (!adb && cur == ConnectionManager.ChannelType.ADB) {
                        ConnectionManager.INSTANCE.downgradeFromAdb()
                        }
                    }
                if (CoroutineScopeKt.isActive($this$launch)) {
                    return Unit.INSTANCE
                    }
                }
            break
            case 1:
            CoroutineScope $this$launch2 = (CoroutineScope) this.L$0
            ResultKt.throwOnFailure($result)
            $this$launch = $this$launch2
            obj = coroutine_suspended
            connectionManager$startAdbWatchdog$1 = this
            if (ConnectionManager.INSTANCE.getUserConnectedIntent()) {
                }
            if (CoroutineScopeKt.isActive($this$launch)) {
                }
            break
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }
    }
