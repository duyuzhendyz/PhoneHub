package com.phonehub

import androidx.constraintlayout.widget.ConstraintLayout
import com.phonehub.ConnectionManager
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope

class ConnectionManager {
    var label: Int? = null

    public ConnectionManager$downgradeFromAdb$1(Continuation<? super ConnectionManager$downgradeFromAdb$1> continuation) {
        super(2, continuation)
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$downgradeFromAdb$1(continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$downgradeFromAdb$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        var str: String? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            str = ConnectionManager.pcIp
            if (str == null) {
                str = "192.168.3.9"
                }
            val ip: String = str
            this.label = 1
            Object testConnection$default = ConnectionManager.testConnection$default(ConnectionManager.INSTANCE, ip, 0, this, 2, null)
            if (testConnection$default != coroutine_suspended) {
                $result = testConnection$default
                break
                } else {
                var coroutine_suspended: return? = null
                }
            case 1:
            ResultKt.throwOnFailure($result)
            break
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        if (((Boolean) $result).booleanValue()) {
            ConnectionManager.INSTANCE.switchChannelImmediate(ConnectionManager.ChannelType.WIFI)
            }
        return Unit.INSTANCE
        }
    }
