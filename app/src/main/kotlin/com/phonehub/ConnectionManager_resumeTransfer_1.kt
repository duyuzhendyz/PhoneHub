package com.phonehub

import androidx.constraintlayout.widget.ConstraintLayout
import com.phonehub.ConnectionManager
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelayKt

class ConnectionManager {
    final  ConnectionManager.ResumeInfo $info
    var label: Int? = null

    public ConnectionManager$resumeTransfer$1(ConnectionManager.ResumeInfo resumeInfo, Continuation<? super ConnectionManager$resumeTransfer$1> continuation) {
        super(2, continuation)
        this.$info = resumeInfo
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$resumeTransfer$1(this.$info, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$resumeTransfer$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun invokeSuspend(/* Object $result */): Any {
        var sendFileWifiFromUri: Any? = null
        var sendFileWifi: Any? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            this.label = 1
            if (DelayKt.delay(500L, this) == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            if (this.$info.getFile() == null) {
                this.label = 2
                sendFileWifi = ConnectionManager.INSTANCE.sendFileWifi(this.$info.getFileId(), this.$info.getFile(), this.$info.getFileSize(), this)
                if (sendFileWifi == coroutine_suspended) {
                    var coroutine_suspended: return? = null
                    }
                } else if (this.$info.getUri() != null) {
                this.label = 3
                sendFileWifiFromUri = ConnectionManager.INSTANCE.sendFileWifiFromUri(this.$info.getFileId(), this.$info.getUri(), this.$info.getFileName(), this.$info.getFileSize(), this)
                if (sendFileWifiFromUri == coroutine_suspended) {
                    var coroutine_suspended: return? = null
                    }
                } else {
                ConnectionManager.INSTANCE.startReceiveFile(this.$info.getFileId(), this.$info.getFileName(), this.$info.getFileSize())
                }
            return Unit.INSTANCE
            case 1:
            ResultKt.throwOnFailure($result)
            if (this.$info.getFile() == null) {
                }
            return Unit.INSTANCE
            case 2:
            ResultKt.throwOnFailure($result)
            return Unit.INSTANCE
            case 3:
            ResultKt.throwOnFailure($result)
            return Unit.INSTANCE
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }
    }
