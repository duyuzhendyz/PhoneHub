package com.phonehub

import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import java.util.concurrent.ConcurrentHashMap
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

class ConnectionManager {
    final  String $fileId
    int I$0
    private  Object L$0
    var label: Int? = null

    public ConnectionManager$startAckWait$1(String str, Continuation<? super ConnectionManager$startAckWait$1> continuation) {
        super(2, continuation)
        this.$fileId = str
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        ConnectionManager$startAckWait$1 connectionManager$startAckWait$1 = new ConnectionManager$startAckWait$1(this.$fileId, continuation)
        connectionManager$startAckWait$1.L$0 = obj
        return connectionManager$startAckWait$1
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$startAckWait$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun invokeSuspend(/* Object $result */): Any {
        CoroutineScope $this$launch
        ConnectionManager$startAckWait$1 connectionManager$startAckWait$1
        var concurrentHashMap: ConcurrentHashMap? = null
        var obj: Any? = null
        var retries: Int? = null
        var concurrentHashMap2: ConcurrentHashMap? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            CoroutineScope $this$launch2 = (CoroutineScope) this.L$0
            val retries2: Int = 0
            $this$launch = $this$launch2
            val obj2: Any = coroutine_suspended
            connectionManager$startAckWait$1 = this
            if (CoroutineScopeKt.isActive($this$launch) || retries2 >= 3) {
                concurrentHashMap = ConnectionManager.ackTracker
                concurrentHashMap.remove(connectionManager$startAckWait$1.$fileId)
                return Unit.INSTANCE
                }
            connectionManager$startAckWait$1.L$0 = $this$launch
            connectionManager$startAckWait$1.I$0 = retries2
            connectionManager$startAckWait$1.label = 1
            if (DelayKt.delay(10000L, connectionManager$startAckWait$1) == obj2) {
                var obj2: return? = null
                }
            val i: Int = retries2
            obj = obj2
            retries = i
            concurrentHashMap2 = ConnectionManager.ackTracker
            if (concurrentHashMap2.containsKey(connectionManager$startAckWait$1.$fileId)) {
                return Unit.INSTANCE
                }
            val retries3: Int = retries + 1
            Log.i("PhoneHub", "ACK 超时重发 file_complete (" + retries3 + ") for " + connectionManager$startAckWait$1.$fileId)
            ConnectionManager.INSTANCE.sendFileComplete(connectionManager$startAckWait$1.$fileId)
            val obj3: Any = obj
            retries2 = retries3
            obj2 = obj3
            if (CoroutineScopeKt.isActive($this$launch)) {
                }
            concurrentHashMap = ConnectionManager.ackTracker
            concurrentHashMap.remove(connectionManager$startAckWait$1.$fileId)
            return Unit.INSTANCE
            case 1:
            retries = this.I$0
            CoroutineScope $this$launch3 = (CoroutineScope) this.L$0
            ResultKt.throwOnFailure($result)
            $this$launch = $this$launch3
            obj = coroutine_suspended
            connectionManager$startAckWait$1 = this
            concurrentHashMap2 = ConnectionManager.ackTracker
            if (concurrentHashMap2.containsKey(connectionManager$startAckWait$1.$fileId)) {
                }
            break
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }
    }
