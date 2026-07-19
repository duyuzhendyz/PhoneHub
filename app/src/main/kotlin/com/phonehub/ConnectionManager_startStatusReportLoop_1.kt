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

    public ConnectionManager$startStatusReportLoop$1(Continuation<? super ConnectionManager$startStatusReportLoop$1> continuation) {
        super(2, continuation)
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        ConnectionManager$startStatusReportLoop$1 connectionManager$startStatusReportLoop$1 = new ConnectionManager$startStatusReportLoop$1(continuation)
        connectionManager$startStatusReportLoop$1.L$0 = obj
        return connectionManager$startStatusReportLoop$1
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$startStatusReportLoop$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        CoroutineScope $this$launch
        var obj: Any? = null
        ConnectionManager$startStatusReportLoop$1 connectionManager$startStatusReportLoop$1
        var mutableStateFlow: MutableStateFlow? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            $this$launch = (CoroutineScope) this.L$0
            obj = coroutine_suspended
            connectionManager$startStatusReportLoop$1 = this
            break
            case 1:
            CoroutineScope $this$launch2 = (CoroutineScope) this.L$0
            ResultKt.throwOnFailure($result)
            $this$launch = $this$launch2
            obj = coroutine_suspended
            connectionManager$startStatusReportLoop$1 = this
            break
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        while (CoroutineScopeKt.isActive($this$launch)) {
            if (ConnectionManager.INSTANCE.getUserConnectedIntent()) {
                mutableStateFlow = ConnectionManager._connectionState
                if (mutableStateFlow.getValue() == ConnectionManager.ConnectionState.CONNECTED) {
                    ConnectionManager.INSTANCE.sendStatusReport()
                    }
                }
            connectionManager$startStatusReportLoop$1.L$0 = $this$launch
            connectionManager$startStatusReportLoop$1.label = 1
            if (DelayKt.delay(CoroutineLiveDataKt.DEFAULT_TIMEOUT, connectionManager$startStatusReportLoop$1) == obj) {
                var obj: return? = null
                }
            }
        return Unit.INSTANCE
        }
    }
