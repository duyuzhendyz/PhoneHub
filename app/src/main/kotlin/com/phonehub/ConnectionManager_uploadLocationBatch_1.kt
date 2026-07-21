package com.phonehub

import android.util.Log
import java.util.ArrayList
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonElementBuildersKt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import org.json.JSONArray

class ConnectionManager_uploadLocationBatch_1(
    private val arr: JSONArray,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_uploadLocationBatch_1(this.arr, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_uploadLocationBatch_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        var msg: JsonObject? = null
        var sendRaw: Any? = null
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                try {
                    val elements = ArrayList<JsonElement>()
                    val length = this.arr.length()
                    for (i in 0 until length) {
                        val o = this.arr.getJSONObject(i)
                        val builderIv = JsonObjectBuilder()
                        JsonElementBuildersKt.put(builderIv, "lat", o.getDouble("lat"))
                        JsonElementBuildersKt.put(builderIv, "lon", o.getDouble("lon"))
                        JsonElementBuildersKt.put(builderIv, "timestamp", o.getLong("timestamp"))
                        JsonElementBuildersKt.put(builderIv, "uploaded", o.optBoolean("uploaded", true))
                        elements.add(builderIv.build())
                    }
                    msg = ConnectionManager.buildJsonMessage { obj ->
                        invokeSuspendLambda2(elements, obj as JsonObjectBuilder)
                    }
                    this.label = 1
                    sendRaw = ConnectionManager.sendRaw(msg.toString(), this)
                } catch (e: Exception) {
                    Log.e("PhoneHub", "uploadLocationBatch failed", e)
                    return Unit
                }
                if (sendRaw == coroutine_suspended) {
                    return coroutine_suspended
                }
                return Unit
            }
            1 -> {
                try {
                    ResultKt.throwOnFailure(result)
                } catch (e: Exception) {
                    Log.e("PhoneHub", "uploadLocationBatch failed", e)
                    return Unit
                }
                return Unit
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
    }

    fun invokeSuspendLambda2(elements: List<JsonElement>, builder: JsonObjectBuilder): Unit {
        JsonElementBuildersKt.put(builder, "source", "phone")
        JsonElementBuildersKt.putJsonObject(builder, "data") { obj ->
            invokeSuspendLambda2Lambda1(elements, obj as JsonObjectBuilder)
        }
        return Unit
    }

    fun invokeSuspendLambda2Lambda1(elements: List<JsonElement>, builder: JsonObjectBuilder): Unit {
        JsonElementBuildersKt.put(builder, "action", "location_batch")
        builder.put("points", JsonArray(elements))
        return Unit
    }
}
