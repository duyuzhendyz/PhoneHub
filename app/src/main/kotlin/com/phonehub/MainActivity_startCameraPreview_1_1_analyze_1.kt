package com.phonehub

import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope

class MainActivity_startCameraPreview_1_1_analyze_1(
    val jpegData: ByteArray,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    override fun create(obj: Any?, continuation: Continuation<*>): Continuation<Unit> {
        return MainActivity_startCameraPreview_1_1_analyze_1(this.jpegData, continuation as Continuation<Unit>)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as MainActivity_startCameraPreview_1_1_analyze_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(obj: Any): Any {
        IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(obj)
                val connectionManager: ConnectionManager = ConnectionManager.INSTANCE
                val bArr: ByteArray = this.jpegData
                connectionManager.sendFrameToPc(bArr, "camera")
                return Unit
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
    }
}
