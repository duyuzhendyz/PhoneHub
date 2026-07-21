package com.phonehub

import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class ConnectionManager_sendClipboardHistoryToPc_1(
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_sendClipboardHistoryToPc_1(continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_sendClipboardHistoryToPc_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        var sendRaw: Any? = null
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val arr: JsonArray = buildJsonArray {
                    ConnectionManager._clipboardHistory.value.forEach { it ->
                        addJsonObject {
                            put("content", it.content)
                            put("source", it.source)
                            put("timestamp", it.timestamp)
                            put("favorite", it.favorite)
                        }
                    }
                }
                val msg: JsonObject = ConnectionManager.INSTANCE.buildJsonMessage {
                    put("source", "phone")
                    putJsonObject("data") {
                        put("action", "clipboard_history")
                        put("items", arr)
                    }
                }
                this.label = 1
                sendRaw = ConnectionManager.INSTANCE.sendRaw(msg.toString(), this)
                if (sendRaw == coroutine_suspended) {
                    return coroutine_suspended
                }
            }
            1 -> {
                ResultKt.throwOnFailure(result)
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
        return Unit
    }
}
