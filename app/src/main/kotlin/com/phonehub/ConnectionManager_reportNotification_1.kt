package com.phonehub

import com.phonehub.ConnectionManager
import io.ktor.http.LinkHeader
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElementBuildersKt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder

class ConnectionManager_reportNotification_1(
    private val item: ConnectionManager.NotificationItem,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_reportNotification_1(this.item, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_reportNotification_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        var mutableSharedFlow: MutableSharedFlow<ConnectionManager.NotificationItem>? = null
        var msg: JsonObject? = null
        var sendRaw: Any? = null
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                mutableSharedFlow = ConnectionManager._notifications
                this.label = 1
                if (mutableSharedFlow.emit(this.item, this) == coroutine_suspended) {
                    return coroutine_suspended
                }
                val connectionManager = ConnectionManager
                val notificationItem = this.item
                msg = connectionManager.buildJsonMessage { obj ->
                    invokeSuspendLambda3(notificationItem, obj as JsonObjectBuilder)
                }
                this.label = 2
                sendRaw = ConnectionManager.sendRaw(msg.toString(), this)
                if (sendRaw == coroutine_suspended) {
                    return coroutine_suspended
                }
                return Unit
            }
            1 -> {
                ResultKt.throwOnFailure(result)
                val connectionManager2 = ConnectionManager
                val notificationItem2 = this.item
                msg = connectionManager2.buildJsonMessage { obj ->
                    invokeSuspendLambda3(notificationItem2, obj as JsonObjectBuilder)
                }
                this.label = 2
                sendRaw = ConnectionManager.sendRaw(msg.toString(), this)
                if (sendRaw == coroutine_suspended) {
                    return coroutine_suspended
                }
                return Unit
            }
            2 -> {
                ResultKt.throwOnFailure(result)
                return Unit
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
    }

    fun invokeSuspendLambda3(item: ConnectionManager.NotificationItem, builder: JsonObjectBuilder): Unit {
        JsonElementBuildersKt.put(builder, "source", "phone")
        JsonElementBuildersKt.putJsonObject(builder, "data") { obj ->
            invokeSuspendLambda3Lambda2(item, obj as JsonObjectBuilder)
        }
        return Unit
    }

    fun invokeSuspendLambda3Lambda2(item: ConnectionManager.NotificationItem, builder: JsonObjectBuilder): Unit {
        JsonElementBuildersKt.put(builder, "action", "notification")
        JsonElementBuildersKt.put(builder, "package", item.packageName)
        JsonElementBuildersKt.put(builder, LinkHeader.Parameters.Title, item.title)
        JsonElementBuildersKt.put(builder, TextNotificationReceiver.EXTRA_TEXT, item.text)
        JsonElementBuildersKt.put(builder, "timestamp", item.timestamp)
        if (!item.actions.isEmpty()) {
            JsonElementBuildersKt.putJsonArray(builder, "actions") { obj ->
                invokeSuspendLambda3Lambda2Lambda1(item, obj as JsonArrayBuilder)
            }
        }
        JsonElementBuildersKt.put(builder, "sbn_id", item.sbnId)
        JsonElementBuildersKt.put(builder, "sbn_tag", item.sbnTag)
        JsonElementBuildersKt.put(builder, "key", item.key)
        return Unit
    }

    fun invokeSuspendLambda3Lambda2Lambda1(item: ConnectionManager.NotificationItem, builder: JsonArrayBuilder): Unit {
        for (a in item.actions) {
            JsonElementBuildersKt.addJsonObject(builder) { obj ->
                invokeSuspendLambda3Lambda2Lambda1Lambda0(a, obj as JsonObjectBuilder)
            }
        }
        return Unit
    }

    fun invokeSuspendLambda3Lambda2Lambda1Lambda0(a: ConnectionManager.NotificationAction, builder: JsonObjectBuilder): Unit {
        JsonElementBuildersKt.put(builder, LinkHeader.Parameters.Title, a.title)
        return Unit
    }
}
