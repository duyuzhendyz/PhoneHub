package com.phonehub

import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import io.ktor.http.ContentDisposition
import java.io.File
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function1
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElementBuildersKt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder

class ConnectionManager {
    final  String $path
    var label: Int? = null

    public ConnectionManager$handleFileListRequest$1(String str, Continuation<? super ConnectionManager$handleFileListRequest$1> continuation) {
        super(2, continuation)
        this.$path = str
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$handleFileListRequest$1(this.$path, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$handleFileListRequest$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        var msg: JsonObject? = null
        var sendRaw: Any? = null
        var msg2: JsonObject? = null
        var sendRaw2: Any? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        try {
            } catch (Exception e) {
            Log.e("PhoneHub", "File list failed", e)
            }
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            val dir: File = new File(this.$path)
            if (dir.exists() && dir.isDirectory()) {
                val files: Array<File> = dir.listFiles()
                if (files == null) {
                    files = new File[0]
                    }
                JsonArrayBuilder builder$iv = JsonArrayBuilder()
                for (final File f : files) {
                    JsonElementBuildersKt.addJsonObject(builder$iv, Function1() { // from class: com.phonehub.ConnectionManager$handleFileListRequest$1$$ExternalSyntheticLambda3
                        override
                        fun invoke(obj: Any): Any {
                            Unit invokeSuspend$lambda$4$lambda$3
                            invokeSuspend$lambda$4$lambda$3 = ConnectionManager$handleFileListRequest$1.invokeSuspend$lambda$4$lambda$3(f, (JsonObjectBuilder) obj)
                            return invokeSuspend$lambda$4$lambda$3
                            }
                        })
                    }
                val arr: JsonArray = builder$iv.build()
                val connectionManager: ConnectionManager = ConnectionManager.INSTANCE
                val str: String = this.$path
                msg2 = connectionManager.buildJsonMessage(Function1() { // from class: com.phonehub.ConnectionManager$handleFileListRequest$1$$ExternalSyntheticLambda4
                    override
                    fun invoke(obj: Any): Any {
                        Unit invokeSuspend$lambda$6
                        invokeSuspend$lambda$6 = ConnectionManager$handleFileListRequest$1.invokeSuspend$lambda$6(str, arr, (JsonObjectBuilder) obj)
                        return invokeSuspend$lambda$6
                        }
                    })
                this.label = 2
                sendRaw2 = ConnectionManager.INSTANCE.sendRaw(msg2.toString(), this)
                if (sendRaw2 == coroutine_suspended) {
                    var coroutine_suspended: return? = null
                    }
                return Unit.INSTANCE
                }
            val connectionManager2: ConnectionManager = ConnectionManager.INSTANCE
            val str2: String = this.$path
            msg = connectionManager2.buildJsonMessage(Function1() { // from class: com.phonehub.ConnectionManager$handleFileListRequest$1$$ExternalSyntheticLambda2
                override
                fun invoke(obj: Any): Any {
                    Unit invokeSuspend$lambda$2
                    invokeSuspend$lambda$2 = ConnectionManager$handleFileListRequest$1.invokeSuspend$lambda$2(str2, (JsonObjectBuilder) obj)
                    return invokeSuspend$lambda$2
                    }
                })
            this.label = 1
            sendRaw = ConnectionManager.INSTANCE.sendRaw(msg.toString(), this)
            if (sendRaw == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            return Unit.INSTANCE
            case 1:
            ResultKt.throwOnFailure($result)
            return Unit.INSTANCE
            case 2:
            ResultKt.throwOnFailure($result)
            return Unit.INSTANCE
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }

    public static final Unit invokeSuspend$lambda$2(final String $path, JsonObjectBuilder $this$buildJsonMessage) {
        JsonElementBuildersKt.put($this$buildJsonMessage, "source", "phone")
        JsonElementBuildersKt.putJsonObject($this$buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$handleFileListRequest$1$$ExternalSyntheticLambda1
            override
            fun invoke(obj: Any): Any {
                Unit invokeSuspend$lambda$2$lambda$1
                invokeSuspend$lambda$2$lambda$1 = ConnectionManager$handleFileListRequest$1.invokeSuspend$lambda$2$lambda$1($path, (JsonObjectBuilder) obj)
                return invokeSuspend$lambda$2$lambda$1
                }
            })
        return Unit.INSTANCE
        }

    public static final Unit invokeSuspend$lambda$2$lambda$1(String $path, JsonObjectBuilder $this$putJsonObject) {
        JsonElementBuildersKt.put($this$putJsonObject, "action", "file_list")
        JsonElementBuildersKt.put($this$putJsonObject, "path", $path)
        JsonArrayBuilder builder$iv = JsonArrayBuilder()
        val unit: Unit = Unit.INSTANCE
        $this$putJsonObject.put("files", builder$iv.build())
        return Unit.INSTANCE
        }

    public static final Unit invokeSuspend$lambda$4$lambda$3(File $f, JsonObjectBuilder $this$addJsonObject) {
        JsonElementBuildersKt.put($this$addJsonObject, ContentDisposition.Parameters.Name, $f.getName())
        JsonElementBuildersKt.put($this$addJsonObject, "path", $f.getAbsolutePath())
        JsonElementBuildersKt.put($this$addJsonObject, ContentDisposition.Parameters.Size, Long.valueOf($f.length()))
        JsonElementBuildersKt.put($this$addJsonObject, "is_dir", Boolean.valueOf($f.isDirectory()))
        JsonElementBuildersKt.put($this$addJsonObject, "modified", Long.valueOf($f.lastModified()))
        return Unit.INSTANCE
        }

    public static final Unit invokeSuspend$lambda$6(final String $path, final JsonArray $arr, JsonObjectBuilder $this$buildJsonMessage) {
        JsonElementBuildersKt.put($this$buildJsonMessage, "source", "phone")
        JsonElementBuildersKt.putJsonObject($this$buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$handleFileListRequest$1$$ExternalSyntheticLambda0
            override
            fun invoke(obj: Any): Any {
                Unit invokeSuspend$lambda$6$lambda$5
                invokeSuspend$lambda$6$lambda$5 = ConnectionManager$handleFileListRequest$1.invokeSuspend$lambda$6$lambda$5($path, $arr, (JsonObjectBuilder) obj)
                return invokeSuspend$lambda$6$lambda$5
                }
            })
        return Unit.INSTANCE
        }

    public static final Unit invokeSuspend$lambda$6$lambda$5(String $path, JsonArray $arr, JsonObjectBuilder $this$putJsonObject) {
        JsonElementBuildersKt.put($this$putJsonObject, "action", "file_list")
        JsonElementBuildersKt.put($this$putJsonObject, "path", $path)
        $this$putJsonObject.put("files", $arr)
        return Unit.INSTANCE
        }
    }
