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
    final  String $fileId
    var label: Int? = null

    public ConnectionManager$handlePcMessage$8(String str, Continuation<? super ConnectionManager$handlePcMessage$8> continuation) {
        super(2, continuation)
        this.$fileId = str
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$handlePcMessage$8(this.$fileId, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$handlePcMessage$8) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        var mutableSharedFlow: MutableSharedFlow? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            mutableSharedFlow = ConnectionManager._transferCancelledFromPc
            this.label = 1
            if (mutableSharedFlow.emit(this.$fileId, this) == coroutine_suspended) {
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
