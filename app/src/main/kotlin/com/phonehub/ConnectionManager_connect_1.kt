package com.phonehub

import androidx.constraintlayout.core.motion.utils.TypedValues
import androidx.constraintlayout.widget.ConstraintLayout
import com.phonehub.ConnectionManager
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

class ConnectionManager {
    final  String $ip
    final  int $port
    var label: Int? = null

    public ConnectionManager$connect$1(int i, String str, Continuation<? super ConnectionManager$connect$1> continuation) {
        super(2, continuation)
        this.$port = i
        this.$ip = str
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$connect$1(this.$port, this.$ip, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$connect$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun invokeSuspend(/* Object $result */): Any {
        var testConnection: Any? = null
        Object $result2
        var mutableStateFlow: MutableStateFlow? = null
        var testConnection2: Any? = null
        var adbSuccess: Boolean? = null
        var mutableStateFlow2: MutableStateFlow? = null
        var mutableStateFlow3: MutableStateFlow? = null
        var wifiSuccess: Boolean? = null
        var mutableStateFlow4: MutableStateFlow? = null
        var mutableStateFlow5: MutableStateFlow? = null
        var mutableStateFlow6: MutableStateFlow? = null
        var success: Boolean? = null
        var mutableStateFlow7: MutableStateFlow? = null
        var mutableStateFlow8: MutableStateFlow? = null
        var mutableStateFlow9: MutableStateFlow? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            if (!ConnectionManager.INSTANCE.isAdbAvailable()) {
                this.label = 3
                testConnection = ConnectionManager.INSTANCE.testConnection(this.$ip, this.$port, this)
                if (testConnection == coroutine_suspended) {
                    var coroutine_suspended: return? = null
                    }
                $result2 = $result
                $result = testConnection
                success = ((Boolean) $result).booleanValue()
                if (success) {
                    val lastConnectFailReason: String = ConnectionManager.INSTANCE.getLastConnectFailReason()
                    val reason: String = lastConnectFailReason != null ? lastConnectFailReason : "未知错误"
                    mutableStateFlow7 = ConnectionManager._connectionMessage
                    mutableStateFlow7.setValue("WiFi 连接失败: " + reason)
                    mutableStateFlow8 = ConnectionManager._connectionState
                    mutableStateFlow8.setValue(ConnectionManager.ConnectionState.DISCONNECTED)
                    } else {
                    mutableStateFlow9 = ConnectionManager._connectionMessage
                    mutableStateFlow9.setValue("WiFi 直连成功")
                    ConnectionManager.INSTANCE.cacheIp(this.$ip)
                    ConnectionManager.INSTANCE.startChannel(ConnectionManager.ChannelType.WIFI)
                    }
                return Unit.INSTANCE
                }
            mutableStateFlow = ConnectionManager._connectionMessage
            mutableStateFlow.setValue("检测到 ADB 通道，尝试通过 ADB 连接...")
            this.label = 1
            testConnection2 = ConnectionManager.INSTANCE.testConnection("127.0.0.1", this.$port, this)
            if (testConnection2 == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            $result = testConnection2
            adbSuccess = ((Boolean) $result).booleanValue()
            if (!adbSuccess) {
                mutableStateFlow3 = ConnectionManager._connectionMessage
                mutableStateFlow3.setValue("ADB 连接成功")
                ConnectionManager.INSTANCE.cacheIp(this.$ip)
                ConnectionManager.INSTANCE.startChannel(ConnectionManager.ChannelType.ADB)
                return Unit.INSTANCE
                }
            mutableStateFlow2 = ConnectionManager._connectionMessage
            mutableStateFlow2.setValue("ADB 连接失败，尝试 WiFi 直连...")
            this.label = 2
            $result = ConnectionManager.INSTANCE.testConnection(this.$ip, this.$port, this)
            if ($result == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            wifiSuccess = ((Boolean) $result).booleanValue()
            if (wifiSuccess) {
                val lastConnectFailReason2: String = ConnectionManager.INSTANCE.getLastConnectFailReason()
                val reason2: String = lastConnectFailReason2 != null ? lastConnectFailReason2 : "未知错误"
                mutableStateFlow4 = ConnectionManager._connectionMessage
                mutableStateFlow4.setValue("WiFi 连接失败: " + reason2)
                mutableStateFlow5 = ConnectionManager._connectionState
                mutableStateFlow5.setValue(ConnectionManager.ConnectionState.DISCONNECTED)
                } else {
                mutableStateFlow6 = ConnectionManager._connectionMessage
                mutableStateFlow6.setValue("WiFi 直连成功")
                ConnectionManager.INSTANCE.cacheIp(this.$ip)
                ConnectionManager.INSTANCE.startChannel(ConnectionManager.ChannelType.WIFI)
                }
            return Unit.INSTANCE
            case 1:
            ResultKt.throwOnFailure($result)
            adbSuccess = ((Boolean) $result).booleanValue()
            if (!adbSuccess) {
                }
            break
            case 2:
            ResultKt.throwOnFailure($result)
            wifiSuccess = ((Boolean) $result).booleanValue()
            if (wifiSuccess) {
                }
            return Unit.INSTANCE
            case 3:
            ResultKt.throwOnFailure($result)
            $result2 = $result
            success = ((Boolean) $result).booleanValue()
            if (success) {
                }
            return Unit.INSTANCE
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }
    }
