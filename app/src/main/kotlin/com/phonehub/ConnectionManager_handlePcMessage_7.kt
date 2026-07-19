package com.phonehub

import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.PointerIconCompat
import com.phonehub.ConnectionManager
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope

class ConnectionManager {
    final  ConnectionManager.ResumeInfo $info
    var label: Int? = null

    public ConnectionManager$handlePcMessage$7(ConnectionManager.ResumeInfo resumeInfo, Continuation<? super ConnectionManager$handlePcMessage$7> continuation) {
        super(2, continuation)
        this.$info = resumeInfo
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$handlePcMessage$7(this.$info, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$handlePcMessage$7) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        var sendFileWifiFromUri: Any? = null
        var sendFileWifi: Any? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            if (this.$info.getFile() != null) {
                this.label = 1
                sendFileWifi = ConnectionManager.INSTANCE.sendFileWifi(this.$info.getFileId(), this.$info.getFile(), this.$info.getFileSize(), this)
                if (sendFileWifi == coroutine_suspended) {
                    var coroutine_suspended: return? = null
                    }
                } else if (this.$info.getUri() != null) {
                this.label = 2
                sendFileWifiFromUri = ConnectionManager.INSTANCE.sendFileWifiFromUri(this.$info.getFileId(), this.$info.getUri(), this.$info.getFileName(), this.$info.getFileSize(), this)
                if (sendFileWifiFromUri == coroutine_suspended) {
                    var coroutine_suspended: return? = null
                    }
                } else {
                ConnectionManager.INSTANCE.startReceiveFile(this.$info.getFileId(), this.$info.getFileName(), this.$info.getFileSize())
                break
                }
            break
            case 1:
            ResultKt.throwOnFailure($result)
            break
            case 2:
            ResultKt.throwOnFailure($result)
            break
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        return Unit.INSTANCE
        }
    }
