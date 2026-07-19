package com.phonehub

import androidx.constraintlayout.widget.ConstraintLayout
import com.phonehub.ConnectionManager
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function1
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElementBuildersKt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder

class ConnectionManager {
    var label: Int? = null

    public ConnectionManager$sendClipboardHistoryToPc$1(Continuation<? super ConnectionManager$sendClipboardHistoryToPc$1> continuation) {
        super(2, continuation)
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$sendClipboardHistoryToPc$1(continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$sendClipboardHistoryToPc$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        var mutableStateFlow: MutableStateFlow? = null
        var msg: JsonObject? = null
        var sendRaw: Any? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            JsonArrayBuilder builder$iv = JsonArrayBuilder()
            mutableStateFlow = ConnectionManager._clipboardHistory
            Iterable $this$forEach$iv = (Iterable) mutableStateFlow.getValue()
            for (Object element$iv : $this$forEach$iv) {
                final ConnectionManager.ClipboardItem it = (ConnectionManager.ClipboardItem) element$iv
                JsonElementBuildersKt.addJsonObject(builder$iv, Function1() { // from class: com.phonehub.ConnectionManager$sendClipboardHistoryToPc$1$$ExternalSyntheticLambda0
                    override
                    fun invoke(obj: Any): Any {
                        Unit invokeSuspend$lambda$2$lambda$1$lambda$0
                        invokeSuspend$lambda$2$lambda$1$lambda$0 = ConnectionManager$sendClipboardHistoryToPc$1.invokeSuspend$lambda$2$lambda$1$lambda$0(ConnectionManager.ClipboardItem.this, (JsonObjectBuilder) obj)
                        return invokeSuspend$lambda$2$lambda$1$lambda$0
                        }
                    })
                }
            val arr: JsonArray = builder$iv.build()
            msg = ConnectionManager.INSTANCE.buildJsonMessage(Function1() { // from class: com.phonehub.ConnectionManager$sendClipboardHistoryToPc$1$$ExternalSyntheticLambda1
                override
                fun invoke(obj: Any): Any {
                    Unit invokeSuspend$lambda$4
                    invokeSuspend$lambda$4 = ConnectionManager$sendClipboardHistoryToPc$1.invokeSuspend$lambda$4(JsonArray.this, (JsonObjectBuilder) obj)
                    return invokeSuspend$lambda$4
                    }
                })
            this.label = 1
            sendRaw = ConnectionManager.INSTANCE.sendRaw(msg.toString(), this)
            if (sendRaw == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            break
            case 1:
            ResultKt.throwOnFailure($result)
            break
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        return Unit.INSTANCE
        }

    public static final Unit invokeSuspend$lambda$2$lambda$1$lambda$0(ConnectionManager.ClipboardItem $it, JsonObjectBuilder $this$addJsonObject) {
        JsonElementBuildersKt.put($this$addJsonObject, "content", $it.getContent())
        JsonElementBuildersKt.put($this$addJsonObject, "source", $it.getSource())
        JsonElementBuildersKt.put($this$addJsonObject, "timestamp", Long.valueOf($it.getTimestamp()))
        JsonElementBuildersKt.put($this$addJsonObject, "favorite", Boolean.valueOf($it.getFavorite()))
        return Unit.INSTANCE
        }

    public static final Unit invokeSuspend$lambda$4(final JsonArray $arr, JsonObjectBuilder $this$buildJsonMessage) {
        JsonElementBuildersKt.put($this$buildJsonMessage, "source", "phone")
        JsonElementBuildersKt.putJsonObject($this$buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$sendClipboardHistoryToPc$1$$ExternalSyntheticLambda2
            override
            fun invoke(obj: Any): Any {
                Unit invokeSuspend$lambda$4$lambda$3
                invokeSuspend$lambda$4$lambda$3 = ConnectionManager$sendClipboardHistoryToPc$1.invokeSuspend$lambda$4$lambda$3(JsonArray.this, (JsonObjectBuilder) obj)
                return invokeSuspend$lambda$4$lambda$3
                }
            })
        return Unit.INSTANCE
        }

    public static final Unit invokeSuspend$lambda$4$lambda$3(JsonArray $arr, JsonObjectBuilder $this$putJsonObject) {
        JsonElementBuildersKt.put($this$putJsonObject, "action", "clipboard_history")
        $this$putJsonObject.put("items", $arr)
        return Unit.INSTANCE
        }
    }
