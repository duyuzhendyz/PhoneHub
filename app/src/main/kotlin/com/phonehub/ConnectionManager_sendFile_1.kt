package com.phonehub

import android.util.Log
import java.io.File
import java.util.UUID
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutKt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class ConnectionManager_sendFile_1(
    private val file: File,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    private var L$0: Any? = null
    private var J$0: Long = 0L

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_sendFile_1(this.file, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_sendFile_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val fileId = UUID.randomUUID().toString()
                val fileSize = this.file.length()
                ConnectionManager.fileTransferCancel = false
                ConnectionManager.transferPaused = false
                ConnectionManager.transferInProgress = true
                val headMsg: JsonObject = ConnectionManager.INSTANCE.buildJsonMessage {
                    put("source", "phone")
                    putJsonObject("data") {
                        put("action", "send_file_head")
                        put(FileTransferReceiver.EXTRA_FILE_NAME, file.name)
                        put(FileTransferReceiver.EXTRA_FILE_SIZE, fileSize)
                        put("file_id", fileId)
                    }
                }
                this.L$0 = fileId
                this.J$0 = fileSize
                this.label = 1
                val sendRaw = ConnectionManager.INSTANCE.sendRaw(headMsg.toString(), this)
                if (sendRaw == coroutine_suspended) {
                    return coroutine_suspended
                }
            }
            1 -> {
                ResultKt.throwOnFailure(result)
                val fileId = this.L$0 as String
                val fileSize = this.J$0
                val deferred = CompletableDeferred<Boolean>()
                ConnectionManager.pendingSend = ConnectionManager.PendingSendInfo(
                    fileId, file.name, fileSize, file, null, null, deferred, 48, null
                )
                Log.i("PhoneHub", "sendFile: 已发送 head, 等待 PC 确认 fileId=$fileId")
                this.L$0 = fileId
                this.J$0 = fileSize
                this.label = 2
                val withTimeoutOrNull = TimeoutKt.withTimeoutOrNull(120000L, ConnectionManager_sendFile_1_accepted_1(deferred), this)
                if (withTimeoutOrNull == coroutine_suspended) {
                    return coroutine_suspended
                }
            }
            2 -> {
                ResultKt.throwOnFailure(result)
                val fileId = this.L$0 as String
                val fileSize = this.J$0
                val accepted = result as? Boolean ?: false
                ConnectionManager.pendingSend = null
                if (!accepted) {
                    Log.w("PhoneHub", "sendFile: PC 未确认或拒绝，取消发送 fileId=$fileId")
                    ConnectionManager.transferInProgress = false
                    ConnectionManager.pendingSend = null
                    return Unit
                }
                val channel = ConnectionManager._currentChannel.value
                this.L$0 = null
                when (channel) {
                    ConnectionManager.ChannelType.ADB, ConnectionManager.ChannelType.WIFI -> {
                        this.label = 3
                        val sendFileWifi = ConnectionManager.INSTANCE.sendFileWifi(fileId, this.file, fileSize, this)
                        if (sendFileWifi == coroutine_suspended) {
                            return coroutine_suspended
                        }
                    }
                    else -> {
                        ConnectionManager.transferInProgress = false
                        ConnectionManager.pendingSend = null
                        return Unit
                    }
                }
            }
            3 -> {
                ResultKt.throwOnFailure(result)
                ConnectionManager.transferInProgress = false
                ConnectionManager.pendingSend = null
                return Unit
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
        return Unit
    }
}
