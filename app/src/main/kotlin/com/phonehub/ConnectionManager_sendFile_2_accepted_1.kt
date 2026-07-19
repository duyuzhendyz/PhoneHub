package com.phonehub

import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope

class ConnectionManager {
    final  CompletableDeferred<Boolean> $deferred
    var label: Int? = null

    public ConnectionManager$sendFile$2$accepted$1(CompletableDeferred<Boolean> completableDeferred, Continuation<? super ConnectionManager$sendFile$2$accepted$1> continuation) {
        super(2, continuation)
        this.$deferred = completableDeferred
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$sendFile$2$accepted$1(this.$deferred, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Boolean>): Any {
        return ((ConnectionManager$sendFile$2$accepted$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            this.label = 1
            val await: Any = this.$deferred.await(this)
            val await: return = = coroutine_suspended ? coroutine_suspended : await
            case 1:
            ResultKt.throwOnFailure($result)
            return $result
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }
    }
