package com.phonehub

import com.phonehub.ConnectionManager
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelayKt

class ConnectionManager_resumeTransfer_1(
    private val `$info`: ConnectionManager.ResumeInfo,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_resumeTransfer_1(this.`$info`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_resumeTransfer_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        var sendFileWifiFromUri: Any? = null
        var sendFileWifi: Any? = null
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                this.label = 1
                if (DelayKt.delay(500L, this) == coroutine_suspended) {
                    return coroutine_suspended
                }
                if (this.`$info`.file == null) {
                    this.label = 2
                    sendFileWifi = ConnectionManager.INSTANCE.sendFileWifi(this.`$info`.fileId, this.`$info`.file, this.`$info`.fileSize, this)
                    if (sendFileWifi == coroutine_suspended) {
                        return coroutine_suspended
                    }
                } else if (this.`$info`.uri != null) {
                    this.label = 3
                    sendFileWifiFromUri = ConnectionManager.INSTANCE.sendFileWifiFromUri(this.`$info`.fileId, this.`$info`.uri, this.`$info`.fileName, this.`$info`.fileSize, this)
                    if (sendFileWifiFromUri == coroutine_suspended) {
                        return coroutine_suspended
                    }
                } else {
                    ConnectionManager.INSTANCE.startReceiveFile(this.`$info`.fileId, this.`$info`.fileName, this.`$info`.fileSize)
                }
                return Unit
            }
            1 -> {
                ResultKt.throwOnFailure(result)
                if (this.`$info`.file == null) {
                }
                return Unit
            }
            2 -> {
                ResultKt.throwOnFailure(result)
                return Unit
            }
            3 -> {
                ResultKt.throwOnFailure(result)
                return Unit
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
    }
}
