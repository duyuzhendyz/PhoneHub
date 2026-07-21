package com.phonehub

import kotlin.KotlinNothingValueException
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow

class `MainActivity$getFilesView$6`(private val `this$0`: MainActivity, continuation: Continuation<*>?) : SuspendLambda(2, continuation) {
    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return `MainActivity$getFilesView$6`(`this$0`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<*>): Any {
        return (create(coroutineScope, continuation) as `MainActivity$getFilesView$6`).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val completedTransfer: SharedFlow<ConnectionManager.CompletedTransfer> = ConnectionManager.getCompletedTransfer()
                val mainActivity = `this$0`
                label = 1
                val collectResult = completedTransfer.collect(object : FlowCollector<ConnectionManager.CompletedTransfer> {
                    override suspend fun emit(transfer: ConnectionManager.CompletedTransfer) {
                        val direction = if (transfer.getSending()) "发送" else "接收"
                        mainActivity.addFileHistory(transfer.getFileName(), direction)
                    }
                })
                if (collectResult == coroutine_suspended) {
                    return coroutine_suspended
                }
            }
            1 -> {
                ResultKt.throwOnFailure(result)
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
        throw KotlinNothingValueException()
    }
}
