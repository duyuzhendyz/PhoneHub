package com.phonehub

import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.PointerIconCompat
import com.phonehub.ConnectionManager
import kotlin.KotlinNothingValueException
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow

class MainActivity {
    var label: Int? = null
    final  MainActivity this$0

    public MainActivity$getFilesView$6(MainActivity mainActivity, Continuation<? super MainActivity$getFilesView$6> continuation) {
        super(2, continuation)
        this.this$0 = mainActivity
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new MainActivity$getFilesView$6(this.this$0, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((MainActivity$getFilesView$6) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            SharedFlow<ConnectionManager.CompletedTransfer> completedTransfer = ConnectionManager.INSTANCE.getCompletedTransfer()
            val mainActivity: MainActivity = this.this$0
            this.label = 1
            if (completedTransfer.collect(FlowCollector() { // from class: com.phonehub.MainActivity$getFilesView$6.1
                override
                public   Object emit(Object value, Continuation $completion) {
                    return emit((ConnectionManager.CompletedTransfer) value, (Continuation<? super Unit>) $completion)
                    }

                fun emit(/* ConnectionManager.CompletedTransfer transfer */, continuation: Continuation<? super Unit>): Any {
                    val direction: String = transfer.getSending() ? "发送" : "接收"
                    MainActivity.this.addFileHistory(transfer.getFileName(), direction)
                    return Unit.INSTANCE
                    }
                }, this) == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            break
            case 1:
            ResultKt.throwOnFailure($result)
            break
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        throw KotlinNothingValueException()
        }
    }
