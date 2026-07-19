package com.phonehub

import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import com.phonehub.ConnectionManager
import java.io.File
import java.util.UUID
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.Boxing
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function1
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableDeferredKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutKt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonElementBuildersKt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder

class ConnectionManager {
    final  File $file
    long J$0
    Object L$0
    var label: Int? = null

    public  class WhenMappings {
        public static final  int[] $EnumSwitchMapping$0

        static {
            val iArr: Array<Int> = new int[ConnectionManager.ChannelType.values().length]
            try {
                iArr[ConnectionManager.ChannelType.ADB.ordinal()] = 1
                } catch (NoSuchFieldError e) {
                }
            try {
                iArr[ConnectionManager.ChannelType.WIFI.ordinal()] = 2
                } catch (NoSuchFieldError e2) {
                }
            $EnumSwitchMapping$0 = iArr
            }
        }

    public ConnectionManager$sendFile$1(File file, Continuation<? super ConnectionManager$sendFile$1> continuation) {
        super(2, continuation)
        this.$file = file
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$sendFile$1(this.$file, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$sendFile$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun invokeSuspend(obj: Any): Any {
        Object $result
        var headMsg: JsonObject? = null
        var sendRaw: Any? = null
        var fileId: String? = null
        var fileSize: Long? = null
        var withTimeoutOrNull: Any? = null
        var fileId2: String? = null
        var fileSize2: Long? = null
        Object $result2
        Object $result3
        var accepted: Boolean? = null
        var mutableStateFlow: MutableStateFlow? = null
        var sendFileWifi: Any? = null
        Object $result4
        var sendFileWifi2: Any? = null
        Object $result5
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        val r2: ?? = this.label
        try {
            try {
                } catch (Throwable th) {
                th = th
                }
            } catch (Exception e) {
            e = e
            }
        switch (r2) {
            case 0:
            ResultKt.throwOnFailure(obj)
            $result = obj
            val fileId3: String = UUID.randomUUID().toString()
            Intrinsics.checkNotNullExpressionValue(fileId3, "toString(...)")
            val fileSize3: Long = this.$file.length()
            val connectionManager: ConnectionManager = ConnectionManager.INSTANCE
            ConnectionManager.fileTransferCancel = false
            val connectionManager2: ConnectionManager = ConnectionManager.INSTANCE
            ConnectionManager.transferPaused = false
            val connectionManager3: ConnectionManager = ConnectionManager.INSTANCE
            ConnectionManager.transferInProgress = true
            val connectionManager4: ConnectionManager = ConnectionManager.INSTANCE
            val file: File = this.$file
            headMsg = connectionManager4.buildJsonMessage(Function1() { // from class: com.phonehub.ConnectionManager$sendFile$1$$ExternalSyntheticLambda0
                override
                fun invoke(obj2: Any): Any {
                    Unit invokeSuspend$lambda$1
                    invokeSuspend$lambda$1 = ConnectionManager$sendFile$1.invokeSuspend$lambda$1(file, fileSize3, fileId3, (JsonObjectBuilder) obj2)
                    return invokeSuspend$lambda$1
                    }
                })
            this.L$0 = fileId3
            this.J$0 = fileSize3
            this.label = 1
            sendRaw = ConnectionManager.INSTANCE.sendRaw(headMsg.toString(), this)
            if (sendRaw == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            fileId = fileId3
            fileSize = fileSize3
            val deferred: CompletableDeferred = CompletableDeferredKt.CompletableDeferred$default(null, 1, null)
            val connectionManager5: ConnectionManager = ConnectionManager.INSTANCE
            val name: String = this.$file.getName()
            Intrinsics.checkNotNullExpressionValue(name, "getName(...)")
            val fileId4: String = fileId
            val fileSize4: Long = fileSize
            ConnectionManager.pendingSend = new ConnectionManager.PendingSendInfo(fileId, name, fileSize, this.$file, null, null, deferred, 48, null)
            Log.i("PhoneHub", "sendFile: 已发送 head, 等待 PC 确认 fileId=" + fileId4)
            this.L$0 = fileId4
            this.J$0 = fileSize4
            this.label = 2
            withTimeoutOrNull = TimeoutKt.withTimeoutOrNull(120000L, new ConnectionManager$sendFile$1$accepted$1(deferred, null), this)
            if (withTimeoutOrNull != coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            fileId2 = fileId4
            fileSize2 = fileSize4
            $result2 = $result
            $result3 = withTimeoutOrNull
            try {
                accepted = (Boolean) $result3
                val connectionManager6: ConnectionManager = ConnectionManager.INSTANCE
                ConnectionManager.pendingSend = null
                } catch (Exception e2) {
                e = e2
                r2 = $result2
                Log.e("PhoneHub", "Send file failed", e)
                val connectionManager7: ConnectionManager = ConnectionManager.INSTANCE
                ConnectionManager.transferInProgress = false
                val connectionManager8: ConnectionManager = ConnectionManager.INSTANCE
                ConnectionManager.pendingSend = null
                return Unit.INSTANCE
                } catch (Throwable th2) {
                th = th2
                val connectionManager9: ConnectionManager = ConnectionManager.INSTANCE
                ConnectionManager.transferInProgress = false
                val connectionManager10: ConnectionManager = ConnectionManager.INSTANCE
                ConnectionManager.pendingSend = null
                var th: throw? = null
                }
            if (Intrinsics.areEqual(accepted, Boxing.boxBoolean(true))) {
                Log.w("PhoneHub", "sendFile: PC 未确认或拒绝，取消发送 fileId=" + fileId2)
                val unit: Unit = Unit.INSTANCE
                val connectionManager11: ConnectionManager = ConnectionManager.INSTANCE
                ConnectionManager.transferInProgress = false
                val connectionManager12: ConnectionManager = ConnectionManager.INSTANCE
                ConnectionManager.pendingSend = null
                var unit: return? = null
                }
            mutableStateFlow = ConnectionManager._currentChannel
            switch (WhenMappings.$EnumSwitchMapping$0[((ConnectionManager.ChannelType) mutableStateFlow.getValue()).ordinal()]) {
                case 1:
                this.L$0 = null
                this.label = 3
                sendFileWifi = ConnectionManager.INSTANCE.sendFileWifi(fileId2, this.$file, fileSize2, this)
                if (sendFileWifi == coroutine_suspended) {
                    var coroutine_suspended: return? = null
                    }
                $result4 = $result2
                val connectionManager13: ConnectionManager = ConnectionManager.INSTANCE
                ConnectionManager.transferInProgress = false
                val connectionManager14: ConnectionManager = ConnectionManager.INSTANCE
                ConnectionManager.pendingSend = null
                return Unit.INSTANCE
                case 2:
                this.L$0 = null
                this.label = 4
                sendFileWifi2 = ConnectionManager.INSTANCE.sendFileWifi(fileId2, this.$file, fileSize2, this)
                if (sendFileWifi2 == coroutine_suspended) {
                    var coroutine_suspended: return? = null
                    }
                $result5 = $result2
                val connectionManager132: ConnectionManager = ConnectionManager.INSTANCE
                ConnectionManager.transferInProgress = false
                val connectionManager142: ConnectionManager = ConnectionManager.INSTANCE
                ConnectionManager.pendingSend = null
                return Unit.INSTANCE
                default:
                val connectionManager1322: ConnectionManager = ConnectionManager.INSTANCE
                ConnectionManager.transferInProgress = false
                val connectionManager1422: ConnectionManager = ConnectionManager.INSTANCE
                ConnectionManager.pendingSend = null
                return Unit.INSTANCE
                }
            case 1:
            $result = obj
            val fileSize5: Long = this.J$0
            val fileId5: String = (String) this.L$0
            ResultKt.throwOnFailure($result)
            fileSize = fileSize5
            fileId = fileId5
            val deferred2: CompletableDeferred = CompletableDeferredKt.CompletableDeferred$default(null, 1, null)
            val connectionManager52: ConnectionManager = ConnectionManager.INSTANCE
            val name2: String = this.$file.getName()
            Intrinsics.checkNotNullExpressionValue(name2, "getName(...)")
            val fileId42: String = fileId
            val fileSize42: Long = fileSize
            ConnectionManager.pendingSend = new ConnectionManager.PendingSendInfo(fileId, name2, fileSize, this.$file, null, null, deferred2, 48, null)
            Log.i("PhoneHub", "sendFile: 已发送 head, 等待 PC 确认 fileId=" + fileId42)
            this.L$0 = fileId42
            this.J$0 = fileSize42
            this.label = 2
            withTimeoutOrNull = TimeoutKt.withTimeoutOrNull(120000L, new ConnectionManager$sendFile$1$accepted$1(deferred2, null), this)
            if (withTimeoutOrNull != coroutine_suspended) {
                }
            break
            case 2:
            $result3 = obj
            val fileSize6: Long = this.J$0
            val fileId6: String = (String) this.L$0
            ResultKt.throwOnFailure($result3)
            $result2 = $result3
            fileSize2 = fileSize6
            fileId2 = fileId6
            accepted = (Boolean) $result3
            val connectionManager62: ConnectionManager = ConnectionManager.INSTANCE
            ConnectionManager.pendingSend = null
            if (Intrinsics.areEqual(accepted, Boxing.boxBoolean(true))) {
                }
            break
            case 3:
            $result4 = obj
            ResultKt.throwOnFailure($result4)
            val connectionManager13222: ConnectionManager = ConnectionManager.INSTANCE
            ConnectionManager.transferInProgress = false
            val connectionManager14222: ConnectionManager = ConnectionManager.INSTANCE
            ConnectionManager.pendingSend = null
            return Unit.INSTANCE
            case 4:
            $result5 = obj
            ResultKt.throwOnFailure($result5)
            val connectionManager132222: ConnectionManager = ConnectionManager.INSTANCE
            ConnectionManager.transferInProgress = false
            val connectionManager142222: ConnectionManager = ConnectionManager.INSTANCE
            ConnectionManager.pendingSend = null
            return Unit.INSTANCE
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }

    public static final Unit invokeSuspend$lambda$1(final File $file, final long $fileSize, final String $fileId, JsonObjectBuilder $this$buildJsonMessage) {
        JsonElementBuildersKt.put($this$buildJsonMessage, "source", "phone")
        JsonElementBuildersKt.putJsonObject($this$buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$sendFile$1$$ExternalSyntheticLambda1
            override
            fun invoke(obj: Any): Any {
                Unit invokeSuspend$lambda$1$lambda$0
                invokeSuspend$lambda$1$lambda$0 = ConnectionManager$sendFile$1.invokeSuspend$lambda$1$lambda$0($file, $fileSize, $fileId, (JsonObjectBuilder) obj)
                return invokeSuspend$lambda$1$lambda$0
                }
            })
        return Unit.INSTANCE
        }

    public static final Unit invokeSuspend$lambda$1$lambda$0(File $file, long $fileSize, String $fileId, JsonObjectBuilder $this$putJsonObject) {
        JsonElementBuildersKt.put($this$putJsonObject, "action", "send_file_head")
        JsonElementBuildersKt.put($this$putJsonObject, FileTransferReceiver.EXTRA_FILE_NAME, $file.getName())
        JsonElementBuildersKt.put($this$putJsonObject, FileTransferReceiver.EXTRA_FILE_SIZE, Long.valueOf($fileSize))
        JsonElementBuildersKt.put($this$putJsonObject, "file_id", $fileId)
        return Unit.INSTANCE
        }
    }
