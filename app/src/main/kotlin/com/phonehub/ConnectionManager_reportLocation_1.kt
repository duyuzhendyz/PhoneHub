package com.phonehub

import android.location.Location
import android.util.Log
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElementBuildersKt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder

class ConnectionManager_reportLocation_1(
    private val loc: Location,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_reportLocation_1(this.loc, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_reportLocation_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        var msg: JsonObject? = null
        var sendRaw: Any? = null
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        try {
            when (this.label) {
                0 -> {
                    ResultKt.throwOnFailure(result)
                    val connectionManager = ConnectionManager
                    val location = this.loc
                    msg = connectionManager.buildJsonMessage { obj ->
                        invokeSuspendLambda1(location, obj as JsonObjectBuilder)
                    }
                    this.label = 1
                    sendRaw = ConnectionManager.sendRaw(msg.toString(), this)
                    if (sendRaw == coroutine_suspended) {
                        return coroutine_suspended
                    }
                }
                1 -> {
                    ResultKt.throwOnFailure(result)
                }
                else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        } catch (e: Exception) {
            Log.e("PhoneHub", "Report location failed", e)
        }
        return Unit
    }

    fun invokeSuspendLambda1(loc: Location, builder: JsonObjectBuilder): Unit {
        JsonElementBuildersKt.put(builder, "source", "phone")
        JsonElementBuildersKt.putJsonObject(builder, "data") { obj ->
            invokeSuspendLambda1Lambda0(loc, obj as JsonObjectBuilder)
        }
        return Unit
    }

    fun invokeSuspendLambda1Lambda0(loc: Location, builder: JsonObjectBuilder): Unit {
        JsonElementBuildersKt.put(builder, "action", "location")
        JsonElementBuildersKt.put(builder, "lat", loc.latitude)
        JsonElementBuildersKt.put(builder, "lon", loc.longitude)
        JsonElementBuildersKt.put(builder, "timestamp", System.currentTimeMillis())
        JsonElementBuildersKt.put(builder, "speed", loc.speed)
        JsonElementBuildersKt.put(builder, "accuracy", loc.accuracy)
        return Unit
    }
}
