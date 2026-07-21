package com.phonehub

import android.util.Log
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ConnectionManager_startStatusPolling_1(
    private val channel: ConnectionManager.ChannelType,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    private var L$0: Any? = null
    private var I$0: Int = 0
    private var J$0: Long = 0L

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        val instance = ConnectionManager_startStatusPolling_1(this.channel, continuation)
        instance.L$0 = obj
        return instance
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_startStatusPolling_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val scope = this.L$0 as CoroutineScope
                ConnectionManager._currentChannel.value = this.channel
                this.L$0 = scope
                this.I$0 = 0
                this.label = 1
                val pollResult = pollLoop(scope, 0, this)
                if (pollResult == coroutine_suspended) {
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

    private fun pollLoop(scope: CoroutineScope, failCount: Int, cont: Continuation<Unit>): Any {
        // Simplified single-step poll representation
        // Real implementation loops with ktor suspend calls, parses JSON response,
        // updates connection state, and handles fail count with backoff
        return try {
            if (!scope.isActive) return Unit
            val client = ConnectionManager.client
            if (client != null) {
                // Status polling logic would be expanded here by the compiler
            }
            Unit
        } catch (e: Exception) {
            Log.w("PhoneHub", "轮询异常: ${e.message}, failCount=$failCount")
            Unit
        }
    }
}
