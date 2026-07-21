package com.phonehub

import android.util.Log
import io.ktor.http.ContentDisposition
import java.io.File
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElementBuildersKt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder

class ConnectionManager_handleFileListRequest_1(
    private val path: String,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_handleFileListRequest_1(this.path, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_handleFileListRequest_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        var msg: JsonObject? = null
        var sendRaw: Any? = null
        var msg2: JsonObject? = null
        var sendRaw2: Any? = null
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        try {
            when (this.label) {
                0 -> {
                    ResultKt.throwOnFailure(result)
                    val dir = File(this.path)
                    if (dir.exists() && dir.isDirectory) {
                        var files = dir.listFiles()
                        if (files == null) {
                            files = emptyArray<File>()
                        }
                        val builder = JsonArrayBuilder()
                        for (f in files) {
                            JsonElementBuildersKt.addJsonObject(builder) { obj ->
                                invokeSuspendLambda4Lambda3(f, obj as JsonObjectBuilder)
                            }
                        }
                        val arr = builder.build()
                        val connectionManager = ConnectionManager
                        val str = this.path
                        msg2 = connectionManager.buildJsonMessage { obj ->
                            invokeSuspendLambda6(str, arr, obj as JsonObjectBuilder)
                        }
                        this.label = 2
                        sendRaw2 = ConnectionManager.sendRaw(msg2.toString(), this)
                        if (sendRaw2 == coroutine_suspended) {
                            return coroutine_suspended
                        }
                        return Unit
                    }
                    val connectionManager2 = ConnectionManager
                    val str2 = this.path
                    msg = connectionManager2.buildJsonMessage { obj ->
                        invokeSuspendLambda2(str2, obj as JsonObjectBuilder)
                    }
                    this.label = 1
                    sendRaw = ConnectionManager.sendRaw(msg.toString(), this)
                    if (sendRaw == coroutine_suspended) {
                        return coroutine_suspended
                    }
                    return Unit
                }
                1 -> {
                    ResultKt.throwOnFailure(result)
                    return Unit
                }
                2 -> {
                    ResultKt.throwOnFailure(result)
                    return Unit
                }
                else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        } catch (e: Exception) {
            Log.e("PhoneHub", "File list failed", e)
        }
        return Unit
    }

    fun invokeSuspendLambda2(path: String, builder: JsonObjectBuilder): Unit {
        JsonElementBuildersKt.put(builder, "source", "phone")
        JsonElementBuildersKt.putJsonObject(builder, "data") { obj ->
            invokeSuspendLambda2Lambda1(path, obj as JsonObjectBuilder)
        }
        return Unit
    }

    fun invokeSuspendLambda2Lambda1(path: String, builder: JsonObjectBuilder): Unit {
        JsonElementBuildersKt.put(builder, "action", "file_list")
        JsonElementBuildersKt.put(builder, "path", path)
        val builderIv = JsonArrayBuilder()
        JsonElementBuildersKt.put(builder, "files", builderIv.build())
        return Unit
    }

    fun invokeSuspendLambda4Lambda3(f: File, builder: JsonObjectBuilder): Unit {
        JsonElementBuildersKt.put(builder, ContentDisposition.Parameters.Name, f.name)
        JsonElementBuildersKt.put(builder, "path", f.absolutePath)
        JsonElementBuildersKt.put(builder, ContentDisposition.Parameters.Size, f.length())
        JsonElementBuildersKt.put(builder, "is_dir", f.isDirectory)
        JsonElementBuildersKt.put(builder, "modified", f.lastModified())
        return Unit
    }

    fun invokeSuspendLambda6(path: String, arr: JsonArray, builder: JsonObjectBuilder): Unit {
        JsonElementBuildersKt.put(builder, "source", "phone")
        JsonElementBuildersKt.putJsonObject(builder, "data") { obj ->
            invokeSuspendLambda6Lambda5(path, arr, obj as JsonObjectBuilder)
        }
        return Unit
    }

    fun invokeSuspendLambda6Lambda5(path: String, arr: JsonArray, builder: JsonObjectBuilder): Unit {
        JsonElementBuildersKt.put(builder, "action", "file_list")
        JsonElementBuildersKt.put(builder, "path", path)
        JsonElementBuildersKt.put(builder, "files", arr)
        return Unit
    }
}
