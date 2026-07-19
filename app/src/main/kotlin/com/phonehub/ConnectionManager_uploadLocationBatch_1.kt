package com.phonehub

import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import java.util.ArrayList
import java.util.List
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.Boxing
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function1
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElementBuildersKt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import org.json.JSONArray
import org.json.JSONObject

class ConnectionManager {
    final  JSONArray $arr
    var label: Int? = null

    public ConnectionManager$uploadLocationBatch$1(JSONArray jSONArray, Continuation<? super ConnectionManager$uploadLocationBatch$1> continuation) {
        super(2, continuation)
        this.$arr = jSONArray
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$uploadLocationBatch$1(this.$arr, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$uploadLocationBatch$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        var msg: JsonObject? = null
        var sendRaw: Any? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            try {
                val elements: List = new ArrayList()
                val length: Int = this.$arr.length()
                for (int i = 0; i < length; i++) {
                    val o: JSONObject = this.$arr.getJSONObject(i)
                    JsonObjectBuilder builder$iv = JsonObjectBuilder()
                    JsonElementBuildersKt.put(builder$iv, "lat", Boxing.boxDouble(o.getDouble("lat")))
                    JsonElementBuildersKt.put(builder$iv, "lon", Boxing.boxDouble(o.getDouble("lon")))
                    JsonElementBuildersKt.put(builder$iv, "timestamp", Boxing.boxLong(o.getLong("timestamp")))
                    JsonElementBuildersKt.put(builder$iv, "uploaded", Boxing.boxBoolean(o.optBoolean("uploaded", true)))
                    elements.add(builder$iv.build())
                    }
                msg = ConnectionManager.INSTANCE.buildJsonMessage(Function1() { // from class: com.phonehub.ConnectionManager$uploadLocationBatch$1$$ExternalSyntheticLambda1
                    override
                    fun invoke(obj: Any): Any {
                        Unit invokeSuspend$lambda$2
                        invokeSuspend$lambda$2 = ConnectionManager$uploadLocationBatch$1.invokeSuspend$lambda$2(elements, (JsonObjectBuilder) obj)
                        return invokeSuspend$lambda$2
                        }
                    })
                this.label = 1
                sendRaw = ConnectionManager.INSTANCE.sendRaw(msg.toString(), this)
                } catch (Exception e) {
                e = e
                Log.e("PhoneHub", "uploadLocationBatch failed", e)
                return Unit.INSTANCE
                }
            if (sendRaw == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            return Unit.INSTANCE
            case 1:
            try {
                ResultKt.throwOnFailure($result)
                } catch (Exception e2) {
                e = e2
                Log.e("PhoneHub", "uploadLocationBatch failed", e)
                return Unit.INSTANCE
                }
            return Unit.INSTANCE
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }

    public static final Unit invokeSuspend$lambda$2(final List $elements, JsonObjectBuilder $this$buildJsonMessage) {
        JsonElementBuildersKt.put($this$buildJsonMessage, "source", "phone")
        JsonElementBuildersKt.putJsonObject($this$buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$uploadLocationBatch$1$$ExternalSyntheticLambda0
            override
            fun invoke(obj: Any): Any {
                Unit invokeSuspend$lambda$2$lambda$1
                invokeSuspend$lambda$2$lambda$1 = ConnectionManager$uploadLocationBatch$1.invokeSuspend$lambda$2$lambda$1($elements, (JsonObjectBuilder) obj)
                return invokeSuspend$lambda$2$lambda$1
                }
            })
        return Unit.INSTANCE
        }

    public static final Unit invokeSuspend$lambda$2$lambda$1(List $elements, JsonObjectBuilder $this$putJsonObject) {
        JsonElementBuildersKt.put($this$putJsonObject, "action", "location_batch")
        $this$putJsonObject.put("points", JsonArray($elements))
        return Unit.INSTANCE
        }
    }
