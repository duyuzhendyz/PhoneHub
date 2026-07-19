package com.phonehub

import android.location.Location
import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function1
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElementBuildersKt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder

class ConnectionManager {
    final  Location $loc
    var label: Int? = null

    public ConnectionManager$reportLocation$1(Location location, Continuation<? super ConnectionManager$reportLocation$1> continuation) {
        super(2, continuation)
        this.$loc = location
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$reportLocation$1(this.$loc, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$reportLocation$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        var msg: JsonObject? = null
        var sendRaw: Any? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        try {
            switch (this.label) {
                case 0:
                ResultKt.throwOnFailure($result)
                val connectionManager: ConnectionManager = ConnectionManager.INSTANCE
                val location: Location = this.$loc
                msg = connectionManager.buildJsonMessage(Function1() { // from class: com.phonehub.ConnectionManager$reportLocation$1$$ExternalSyntheticLambda1
                    override
                    fun invoke(obj: Any): Any {
                        Unit invokeSuspend$lambda$1
                        invokeSuspend$lambda$1 = ConnectionManager$reportLocation$1.invokeSuspend$lambda$1(location, (JsonObjectBuilder) obj)
                        return invokeSuspend$lambda$1
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
            } catch (Exception e) {
            Log.e("PhoneHub", "Report location failed", e)
            }
        return Unit.INSTANCE
        }

    public static final Unit invokeSuspend$lambda$1(final Location $loc, JsonObjectBuilder $this$buildJsonMessage) {
        JsonElementBuildersKt.put($this$buildJsonMessage, "source", "phone")
        JsonElementBuildersKt.putJsonObject($this$buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$reportLocation$1$$ExternalSyntheticLambda0
            override
            fun invoke(obj: Any): Any {
                Unit invokeSuspend$lambda$1$lambda$0
                invokeSuspend$lambda$1$lambda$0 = ConnectionManager$reportLocation$1.invokeSuspend$lambda$1$lambda$0($loc, (JsonObjectBuilder) obj)
                return invokeSuspend$lambda$1$lambda$0
                }
            })
        return Unit.INSTANCE
        }

    public static final Unit invokeSuspend$lambda$1$lambda$0(Location $loc, JsonObjectBuilder $this$putJsonObject) {
        JsonElementBuildersKt.put($this$putJsonObject, "action", "location")
        JsonElementBuildersKt.put($this$putJsonObject, "lat", Double.valueOf($loc.getLatitude()))
        JsonElementBuildersKt.put($this$putJsonObject, "lon", Double.valueOf($loc.getLongitude()))
        JsonElementBuildersKt.put($this$putJsonObject, "timestamp", Long.valueOf(System.currentTimeMillis()))
        JsonElementBuildersKt.put($this$putJsonObject, "speed", Float.valueOf($loc.getSpeed()))
        JsonElementBuildersKt.put($this$putJsonObject, "accuracy", Float.valueOf($loc.getAccuracy()))
        return Unit.INSTANCE
        }
    }
