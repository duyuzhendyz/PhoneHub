package com.phonehub

import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow

class ConnectionManager_startPcCameraPolling_1(
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    private var L$0: Any? = null
    private var L$1: Any? = null

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        val instance = ConnectionManager_startPcCameraPolling_1(continuation)
        instance.L$0 = obj
        return instance
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_startPcCameraPolling_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val scope = this.L$0 as CoroutineScope
                val ip = ConnectionManager.pcIp ?: "192.168.3.9"
                val baseUrl: String
                if (ConnectionManager.INSTANCE.isAdbAvailable() &&
                    ConnectionManager._currentChannel.value == ConnectionManager.ChannelType.ADB) {
                    baseUrl = "http://127.0.0.1:${ConnectionManager.connectPort}"
                } else {
                    baseUrl = "http://$ip:${ConnectionManager.connectPort}"
                }
                this.L$0 = scope
                this.L$1 = baseUrl
                this.label = 1
                val pollResult = pollLoop(scope, baseUrl, this)
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

    private fun pollLoop(scope: CoroutineScope, baseUrl: String, cont: Continuation<Unit>): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        // Simplified single-step poll; real implementation uses loop with suspend calls
        return try {
            if (!scope.isActive) return Unit
            val client = ConnectionManager.client
            if (client != null) {
                // Note: actual ktor suspend calls would be expanded by the compiler
                // This is a faithful representation of the decompiled control flow
            }
            Unit
        } catch (e: Exception) {
            Unit
        }
    }
}
