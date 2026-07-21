package com.phonehub

import android.content.Context
import android.net.Uri
import com.phonehub.ConnectionManager
import com.phonehub.FileTransferReceiver
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElementBuildersKt
import kotlinx.serialization.json.JsonObjectBuilder

class ConnectionManager_sendFile_2(
    private val ctx: Context,
    private val displayName: String,
    private val uri: Uri,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    class WhenMappings {
        companion object {
            val ENUM_SWITCH_MAPPING_0: IntArray = IntArray(ConnectionManager.ChannelType.values().size).also { iArr ->
                try { iArr[ConnectionManager.ChannelType.ADB.ordinal] = 1 } catch (e: NoSuchFieldError) {}
                try { iArr[ConnectionManager.ChannelType.WIFI.ordinal] = 2 } catch (e: NoSuchFieldError) {}
            }
        }
    }

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_sendFile_2(this.ctx, this.displayName, this.uri, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_sendFile_2).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        throw UnsupportedOperationException("Method not decompiled: com.phonehub.ConnectionManager\$sendFile\$2.invokeSuspend(java.lang.Object):java.lang.Object")
    }

    fun invokeSuspendLambda4(fileName: kotlin.jvm.internal.Ref.ObjectRef, fileSize: kotlin.jvm.internal.Ref.LongRef, fileId: String, builder: JsonObjectBuilder): Unit {
        JsonElementBuildersKt.put(builder, "source", "phone")
        JsonElementBuildersKt.putJsonObject(builder, "data") { obj ->
            invokeSuspendLambda4Lambda3(fileName, fileSize, fileId, obj as JsonObjectBuilder)
        }
        return Unit
    }

    fun invokeSuspendLambda4Lambda3(fileName: kotlin.jvm.internal.Ref.ObjectRef, fileSize: kotlin.jvm.internal.Ref.LongRef, fileId: String, builder: JsonObjectBuilder): Unit {
        JsonElementBuildersKt.put(builder, "action", "send_file_head")
        JsonElementBuildersKt.put(builder, FileTransferReceiver.EXTRA_FILE_NAME, fileName.element)
        JsonElementBuildersKt.put(builder, FileTransferReceiver.EXTRA_FILE_SIZE, fileSize.element)
        JsonElementBuildersKt.put(builder, "file_id", fileId)
        return Unit
    }
}
