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

class MainActivity {
    final  byte[] $jpegData
    var label: Int? = null

    public MainActivity$startCameraPreview$1$1$analyze$1(byte[] bArr, Continuation<? super MainActivity$startCameraPreview$1$1$analyze$1> continuation) {
        super(2, continuation)
        this.$jpegData = bArr
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new MainActivity$startCameraPreview$1$1$analyze$1(this.$jpegData, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((MainActivity$startCameraPreview$1$1$analyze$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(obj: Any): Any {
        IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure(obj)
            val connectionManager: ConnectionManager = ConnectionManager.INSTANCE
            val bArr: Array<Byte> = this.$jpegData
            Intrinsics.checkNotNull(bArr)
            connectionManager.sendFrameToPc(bArr, "camera")
            return Unit.INSTANCE
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }
    }
