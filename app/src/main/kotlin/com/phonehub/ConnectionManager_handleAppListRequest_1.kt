package com.phonehub

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import io.ktor.http.ContentDisposition
import java.io.File
import java.util.List
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
    var label: Int? = null

    public ConnectionManager$handleAppListRequest$1(Continuation<? super ConnectionManager$handleAppListRequest$1> continuation) {
        super(2, continuation)
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$handleAppListRequest$1(continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$handleAppListRequest$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        var context: Context? = null
        var pm: PackageManager? = null
        var msg: JsonObject? = null
        var sendRaw: Any? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        try {
            switch (this.label) {
                case 0:
                ResultKt.throwOnFailure($result)
                context = ConnectionManager.context
                if (context != null && (pm = context.getPackageManager()) != null) {
                    val infos: List = pm.getInstalledApplications(0)
                    Intrinsics.checkNotNullExpressionValue(infos, "getInstalledApplications(...)")
                    JsonArrayBuilder builder$iv = JsonArrayBuilder()
                    for (final ApplicationInfo info : infos) {
                        JsonElementBuildersKt.addJsonObject(builder$iv, Function1() { // from class: com.phonehub.ConnectionManager$handleAppListRequest$1$$ExternalSyntheticLambda1
                            override
                            fun invoke(obj: Any): Any {
                                Unit invokeSuspend$lambda$1$lambda$0
                                invokeSuspend$lambda$1$lambda$0 = ConnectionManager$handleAppListRequest$1.invokeSuspend$lambda$1$lambda$0(pm, info, (JsonObjectBuilder) obj)
                                return invokeSuspend$lambda$1$lambda$0
                                }
                            })
                        }
                    val arr: JsonArray = builder$iv.build()
                    msg = ConnectionManager.INSTANCE.buildJsonMessage(Function1() { // from class: com.phonehub.ConnectionManager$handleAppListRequest$1$$ExternalSyntheticLambda2
                        override
                        fun invoke(obj: Any): Any {
                            Unit invokeSuspend$lambda$3
                            invokeSuspend$lambda$3 = ConnectionManager$handleAppListRequest$1.invokeSuspend$lambda$3(JsonArray.this, (JsonObjectBuilder) obj)
                            return invokeSuspend$lambda$3
                            }
                        })
                    this.label = 1
                    sendRaw = ConnectionManager.INSTANCE.sendRaw(msg.toString(), this)
                    if (sendRaw == coroutine_suspended) {
                        var coroutine_suspended: return? = null
                        }
                    }
                return Unit.INSTANCE
                case 1:
                ResultKt.throwOnFailure($result)
                break
                default:
                throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
                }
            } catch (Exception e) {
            Log.e("PhoneHub", "App list failed", e)
            }
        return Unit.INSTANCE
        }

    public static final Unit invokeSuspend$lambda$1$lambda$0(PackageManager $pm, ApplicationInfo $info, JsonObjectBuilder $this$addJsonObject) {
        var j: Long? = null
        val str: String = ""
        JsonElementBuildersKt.put($this$addJsonObject, ContentDisposition.Parameters.Name, $pm.getApplicationLabel($info).toString())
        JsonElementBuildersKt.put($this$addJsonObject, "package", $info.packageName)
        JsonElementBuildersKt.put($this$addJsonObject, "system", Boolean.valueOf(($info.flags & 1) != 0))
        try {
            val str2: String = $pm.getPackageInfo($info.packageName, 0).versionName
            if (str2 != null) {
                str = str2
                }
            } catch (Exception e) {
            }
        JsonElementBuildersKt.put($this$addJsonObject, "version", str)
        val j2: Long = 0
        try {
            j = File($info.sourceDir).length()
            } catch (Exception e2) {
            j = 0
            }
        JsonElementBuildersKt.put($this$addJsonObject, ContentDisposition.Parameters.Size, Long.valueOf(j))
        try {
            j2 = $pm.getPackageInfo($info.packageName, 0).firstInstallTime
            } catch (Exception e3) {
            }
        JsonElementBuildersKt.put($this$addJsonObject, "install_time", Long.valueOf(j2))
        return Unit.INSTANCE
        }

    public static final Unit invokeSuspend$lambda$3(final JsonArray $arr, JsonObjectBuilder $this$buildJsonMessage) {
        JsonElementBuildersKt.put($this$buildJsonMessage, "source", "phone")
        JsonElementBuildersKt.putJsonObject($this$buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$handleAppListRequest$1$$ExternalSyntheticLambda0
            override
            fun invoke(obj: Any): Any {
                Unit invokeSuspend$lambda$3$lambda$2
                invokeSuspend$lambda$3$lambda$2 = ConnectionManager$handleAppListRequest$1.invokeSuspend$lambda$3$lambda$2(JsonArray.this, (JsonObjectBuilder) obj)
                return invokeSuspend$lambda$3$lambda$2
                }
            })
        return Unit.INSTANCE
        }

    public static final Unit invokeSuspend$lambda$3$lambda$2(JsonArray $arr, JsonObjectBuilder $this$putJsonObject) {
        JsonElementBuildersKt.put($this$putJsonObject, "action", "app_list")
        $this$putJsonObject.put("apps", $arr)
        return Unit.INSTANCE
        }
    }
