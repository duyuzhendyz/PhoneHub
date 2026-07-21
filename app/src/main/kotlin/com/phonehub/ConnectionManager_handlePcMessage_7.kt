package com.phonehub

import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope

class `ConnectionManager$handlePcMessage$7`(private val `$info`: ConnectionManager.ResumeInfo, continuation: Continuation<*>?) : SuspendLambda(2, continuation) {
    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return `ConnectionManager$handlePcMessage$7`(`$info`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<*>): Any {
        return (create(coroutineScope, continuation) as `ConnectionManager$handlePcMessage$7`).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        var sendFileWifiFromUri: Any? = null
        var sendFileWifi: Any? = null
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                if (this.`$info`.getFile() != null) {
                    this.label = 1
                    sendFileWifi = ConnectionManager.sendFileWifi(this.`$info`.getFileId(), this.`$info`.getFile(), this.`$info`.getFileSize(), this)
                    if (sendFileWifi == coroutine_suspended) {
                        return coroutine_suspended
                    }
                } else if (this.`$info`.getUri() != null) {
                    this.label = 2
                    sendFileWifiFromUri = ConnectionManager.sendFileWifiFromUri(this.`$info`.getFileId(), this.`$info`.getUri(), this.`$info`.getFileName(), this.`$info`.getFileSize(), this)
                    if (sendFileWifiFromUri == coroutine_suspended) {
                        return coroutine_suspended
                    }
                } else {
                    ConnectionManager.startReceiveFile(this.`$info`.getFileId(), this.`$info`.getFileName(), this.`$info`.getFileSize())
                }
            }
            1 -> {
                ResultKt.throwOnFailure(result)
            }
            2 -> {
                ResultKt.throwOnFailure(result)
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
        return Unit
    }
}
