package com.phonehub

import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

class ConnectionManager_connect_1(
    private val port: Int,
    private val ip: String,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_connect_1(this.port, this.ip, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_connect_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        var testConnection: Any? = null
        var testConnection2: Any? = null
        var mutableStateFlow: MutableStateFlow<String>? = null
        var adbSuccess: Boolean? = null
        var mutableStateFlow2: MutableStateFlow<String>? = null
        var mutableStateFlow3: MutableStateFlow<String>? = null
        var wifiSuccess: Boolean? = null
        var mutableStateFlow4: MutableStateFlow<String>? = null
        var mutableStateFlow5: MutableStateFlow<ConnectionManager.ConnectionState>? = null
        var mutableStateFlow6: MutableStateFlow<String>? = null
        var success: Boolean? = null
        var mutableStateFlow7: MutableStateFlow<String>? = null
        var mutableStateFlow8: MutableStateFlow<ConnectionManager.ConnectionState>? = null
        var mutableStateFlow9: MutableStateFlow<String>? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                if (!ConnectionManager.INSTANCE.isAdbAvailable()) {
                    this.label = 3
                    testConnection = ConnectionManager.INSTANCE.testConnection(this.ip, this.port, this)
                    if (testConnection == coroutine_suspended) {
                        return coroutine_suspended
                    }
                    success = testConnection as Boolean
                    if (success == true) {
                        val lastConnectFailReason: String? = ConnectionManager.INSTANCE.lastConnectFailReason
                        val reason: String = lastConnectFailReason ?: "未知错误"
                        mutableStateFlow7 = ConnectionManager._connectionMessage
                        mutableStateFlow7?.value = "WiFi 连接失败: " + reason
                        mutableStateFlow8 = ConnectionManager._connectionState
                        mutableStateFlow8?.value = ConnectionManager.ConnectionState.DISCONNECTED
                    } else {
                        mutableStateFlow9 = ConnectionManager._connectionMessage
                        mutableStateFlow9?.value = "WiFi 直连成功"
                        ConnectionManager.INSTANCE.cacheIp(this.ip)
                        ConnectionManager.INSTANCE.startChannel(ConnectionManager.ChannelType.WIFI)
                    }
                    return Unit
                }
                mutableStateFlow = ConnectionManager._connectionMessage
                mutableStateFlow?.value = "检测到 ADB 通道，尝试通过 ADB 连接..."
                this.label = 1
                testConnection2 = ConnectionManager.INSTANCE.testConnection("127.0.0.1", this.port, this)
                if (testConnection2 == coroutine_suspended) {
                    return coroutine_suspended
                }
                adbSuccess = testConnection2 as Boolean
                if (adbSuccess != true) {
                    mutableStateFlow3 = ConnectionManager._connectionMessage
                    mutableStateFlow3?.value = "ADB 连接成功"
                    ConnectionManager.INSTANCE.cacheIp(this.ip)
                    ConnectionManager.INSTANCE.startChannel(ConnectionManager.ChannelType.ADB)
                    return Unit
                }
                mutableStateFlow2 = ConnectionManager._connectionMessage
                mutableStateFlow2?.value = "ADB 连接失败，尝试 WiFi 直连..."
                this.label = 2
                testConnection = ConnectionManager.INSTANCE.testConnection(this.ip, this.port, this)
                if (testConnection == coroutine_suspended) {
                    return coroutine_suspended
                }
                wifiSuccess = testConnection as Boolean
                if (wifiSuccess == true) {
                    val lastConnectFailReason2: String? = ConnectionManager.INSTANCE.lastConnectFailReason
                    val reason2: String = lastConnectFailReason2 ?: "未知错误"
                    mutableStateFlow4 = ConnectionManager._connectionMessage
                    mutableStateFlow4?.value = "WiFi 连接失败: " + reason2
                    mutableStateFlow5 = ConnectionManager._connectionState
                    mutableStateFlow5?.value = ConnectionManager.ConnectionState.DISCONNECTED
                } else {
                    mutableStateFlow6 = ConnectionManager._connectionMessage
                    mutableStateFlow6?.value = "WiFi 直连成功"
                    ConnectionManager.INSTANCE.cacheIp(this.ip)
                    ConnectionManager.INSTANCE.startChannel(ConnectionManager.ChannelType.WIFI)
                }
                return Unit
            }
            1 -> {
                ResultKt.throwOnFailure(result)
                adbSuccess = result as Boolean
            }
            2 -> {
                ResultKt.throwOnFailure(result)
                wifiSuccess = result as Boolean
                return Unit
            }
            3 -> {
                ResultKt.throwOnFailure(result)
                success = result as Boolean
                return Unit
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
        return Unit
    }
}
