package com.phonehub

import androidx.constraintlayout.widget.ConstraintLayout
import com.phonehub.ConnectionManager
import io.ktor.http.LinkHeader
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function1
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElementBuildersKt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import org.osmdroid.tileprovider.modules.DatabaseFileArchive

class ConnectionManager {
    final  ConnectionManager.NotificationItem $item
    var label: Int? = null

    public ConnectionManager$reportNotification$1(ConnectionManager.NotificationItem notificationItem, Continuation<? super ConnectionManager$reportNotification$1> continuation) {
        super(2, continuation)
        this.$item = notificationItem
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$reportNotification$1(this.$item, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$reportNotification$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun invokeSuspend(/* Object $result */): Any {
        var mutableSharedFlow: MutableSharedFlow? = null
        var msg: JsonObject? = null
        var sendRaw: Any? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            mutableSharedFlow = ConnectionManager._notifications
            this.label = 1
            if (mutableSharedFlow.emit(this.$item, this) == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            val connectionManager: ConnectionManager = ConnectionManager.INSTANCE
            final ConnectionManager.NotificationItem notificationItem = this.$item
            msg = connectionManager.buildJsonMessage(Function1() { // from class: com.phonehub.ConnectionManager$reportNotification$1$$ExternalSyntheticLambda2
                override
                fun invoke(obj: Any): Any {
                    Unit invokeSuspend$lambda$3
                    invokeSuspend$lambda$3 = ConnectionManager$reportNotification$1.invokeSuspend$lambda$3(ConnectionManager.NotificationItem.this, (JsonObjectBuilder) obj)
                    return invokeSuspend$lambda$3
                    }
                })
            this.label = 2
            sendRaw = ConnectionManager.INSTANCE.sendRaw(msg.toString(), this)
            if (sendRaw == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            return Unit.INSTANCE
            case 1:
            ResultKt.throwOnFailure($result)
            val connectionManager2: ConnectionManager = ConnectionManager.INSTANCE
            final ConnectionManager.NotificationItem notificationItem2 = this.$item
            msg = connectionManager2.buildJsonMessage(Function1() { // from class: com.phonehub.ConnectionManager$reportNotification$1$$ExternalSyntheticLambda2
                override
                fun invoke(obj: Any): Any {
                    Unit invokeSuspend$lambda$3
                    invokeSuspend$lambda$3 = ConnectionManager$reportNotification$1.invokeSuspend$lambda$3(ConnectionManager.NotificationItem.this, (JsonObjectBuilder) obj)
                    return invokeSuspend$lambda$3
                    }
                })
            this.label = 2
            sendRaw = ConnectionManager.INSTANCE.sendRaw(msg.toString(), this)
            if (sendRaw == coroutine_suspended) {
                }
            return Unit.INSTANCE
            case 2:
            ResultKt.throwOnFailure($result)
            return Unit.INSTANCE
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }

    public static final Unit invokeSuspend$lambda$3(final ConnectionManager.NotificationItem $item, JsonObjectBuilder $this$buildJsonMessage) {
        JsonElementBuildersKt.put($this$buildJsonMessage, "source", "phone")
        JsonElementBuildersKt.putJsonObject($this$buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$reportNotification$1$$ExternalSyntheticLambda0
            override
            fun invoke(obj: Any): Any {
                Unit invokeSuspend$lambda$3$lambda$2
                invokeSuspend$lambda$3$lambda$2 = ConnectionManager$reportNotification$1.invokeSuspend$lambda$3$lambda$2(ConnectionManager.NotificationItem.this, (JsonObjectBuilder) obj)
                return invokeSuspend$lambda$3$lambda$2
                }
            })
        return Unit.INSTANCE
        }

    public static final Unit invokeSuspend$lambda$3$lambda$2(final ConnectionManager.NotificationItem $item, JsonObjectBuilder $this$putJsonObject) {
        JsonElementBuildersKt.put($this$putJsonObject, "action", "notification")
        JsonElementBuildersKt.put($this$putJsonObject, "package", $item.getPackageName())
        JsonElementBuildersKt.put($this$putJsonObject, LinkHeader.Parameters.Title, $item.getTitle())
        JsonElementBuildersKt.put($this$putJsonObject, TextNotificationReceiver.EXTRA_TEXT, $item.getText())
        JsonElementBuildersKt.put($this$putJsonObject, "timestamp", Long.valueOf($item.getTimestamp()))
        if (!$item.getActions().isEmpty()) {
            JsonElementBuildersKt.putJsonArray($this$putJsonObject, "actions", Function1() { // from class: com.phonehub.ConnectionManager$reportNotification$1$$ExternalSyntheticLambda1
                override
                fun invoke(obj: Any): Any {
                    Unit invokeSuspend$lambda$3$lambda$2$lambda$1
                    invokeSuspend$lambda$3$lambda$2$lambda$1 = ConnectionManager$reportNotification$1.invokeSuspend$lambda$3$lambda$2$lambda$1(ConnectionManager.NotificationItem.this, (JsonArrayBuilder) obj)
                    return invokeSuspend$lambda$3$lambda$2$lambda$1
                    }
                })
            }
        JsonElementBuildersKt.put($this$putJsonObject, "sbn_id", Integer.valueOf($item.getSbnId()))
        JsonElementBuildersKt.put($this$putJsonObject, "sbn_tag", $item.getSbnTag())
        JsonElementBuildersKt.put($this$putJsonObject, DatabaseFileArchive.COLUMN_KEY, $item.getKey())
        return Unit.INSTANCE
        }

    public static final Unit invokeSuspend$lambda$3$lambda$2$lambda$1(ConnectionManager.NotificationItem $item, JsonArrayBuilder $this$putJsonArray) {
        for (final ConnectionManager.NotificationAction a : $item.getActions()) {
            JsonElementBuildersKt.addJsonObject($this$putJsonArray, Function1() { // from class: com.phonehub.ConnectionManager$reportNotification$1$$ExternalSyntheticLambda3
                override
                fun invoke(obj: Any): Any {
                    Unit invokeSuspend$lambda$3$lambda$2$lambda$1$lambda$0
                    invokeSuspend$lambda$3$lambda$2$lambda$1$lambda$0 = ConnectionManager$reportNotification$1.invokeSuspend$lambda$3$lambda$2$lambda$1$lambda$0(ConnectionManager.NotificationAction.this, (JsonObjectBuilder) obj)
                    return invokeSuspend$lambda$3$lambda$2$lambda$1$lambda$0
                    }
                })
            }
        return Unit.INSTANCE
        }

    public static final Unit invokeSuspend$lambda$3$lambda$2$lambda$1$lambda$0(ConnectionManager.NotificationAction $a, JsonObjectBuilder $this$addJsonObject) {
        JsonElementBuildersKt.put($this$addJsonObject, LinkHeader.Parameters.Title, $a.getTitle())
        return Unit.INSTANCE
        }
    }
