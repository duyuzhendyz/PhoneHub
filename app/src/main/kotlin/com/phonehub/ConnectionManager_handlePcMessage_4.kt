package com.phonehub

import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow

class ConnectionManager {
    final  String $msg
    var label: Int? = null

    public ConnectionManager$handlePcMessage$4(String str, Continuation<? super ConnectionManager$handlePcMessage$4> continuation) {
        super(2, continuation)
        this.$msg = str
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$handlePcMessage$4(this.$msg, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$handlePcMessage$4) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        var mutableSharedFlow: MutableSharedFlow? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            mutableSharedFlow = ConnectionManager._screenshotResult
            this.label = 1
            if (mutableSharedFlow.emit(this.$msg, this) == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            break
            case 1:
            ResultKt.throwOnFailure($result)
            break
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        return Unit.INSTANCE
        }
    }
