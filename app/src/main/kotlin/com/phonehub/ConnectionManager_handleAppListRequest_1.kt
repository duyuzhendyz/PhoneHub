package com.phonehub

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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

class ConnectionManager_handleAppListRequest_1(
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_handleAppListRequest_1(continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_handleAppListRequest_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        var sendRaw: Any? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        try {
            when (this.label) {
                0 -> {
                    ResultKt.throwOnFailure(result)
                    val context: Context? = ConnectionManager.context
                    if (context != null) {
                        val pm: PackageManager = context.packageManager
                        if (pm != null) {
                            val infos: List<ApplicationInfo> = pm.getInstalledApplications(0)
                            val builderIv = JsonArrayBuilder()
                            for (info in infos) {
                                JsonElementBuildersKt.addJsonObject(builderIv) {
                                    `invokeSuspend$lambda$1$lambda$0`(pm, info, this)
                                }
                            }
                            val arr: JsonArray = builderIv.build()
                            val msg: JsonObject = ConnectionManager.INSTANCE.buildJsonMessage {
                                `invokeSuspend$lambda$3`(arr, this)
                            }
                            this.label = 1
                            sendRaw = ConnectionManager.INSTANCE.sendRaw(msg.toString(), this)
                            if (sendRaw == coroutine_suspended) {
                                return coroutine_suspended
                            }
                        }
                    }
                    return Unit
                }
                1 -> {
                    ResultKt.throwOnFailure(result)
                }
                else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        } catch (e: Exception) {
            Log.e("PhoneHub", "App list failed", e)
        }
        return Unit
    }

    companion object {
        fun `invokeSuspend$lambda$1$lambda$0`(
            pm: PackageManager,
            info: ApplicationInfo,
            addJsonObjectBuilder: JsonObjectBuilder
        ): Unit {
            var str: String = ""
            JsonElementBuildersKt.put(addJsonObjectBuilder, ContentDisposition.Parameters.Name, pm.getApplicationLabel(info).toString())
            JsonElementBuildersKt.put(addJsonObjectBuilder, "package", info.packageName)
            JsonElementBuildersKt.put(addJsonObjectBuilder, "system", (info.flags and 1) != 0)
            try {
                val str2: String? = pm.getPackageInfo(info.packageName, 0).versionName
                if (str2 != null) {
                    str = str2
                }
            } catch (e: Exception) {
            }
            JsonElementBuildersKt.put(addJsonObjectBuilder, "version", str)
            var j: Long = 0
            try {
                j = File(info.sourceDir).length()
            } catch (e2: Exception) {
                j = 0
            }
            JsonElementBuildersKt.put(addJsonObjectBuilder, ContentDisposition.Parameters.Size, j)
            var j2: Long = 0
            try {
                j2 = pm.getPackageInfo(info.packageName, 0).firstInstallTime
            } catch (e3: Exception) {
            }
            JsonElementBuildersKt.put(addJsonObjectBuilder, "install_time", j2)
            return Unit
        }

        fun `invokeSuspend$lambda$3`(
            arr: JsonArray,
            buildJsonMessageBuilder: JsonObjectBuilder
        ): Unit {
            JsonElementBuildersKt.put(buildJsonMessageBuilder, "source", "phone")
            JsonElementBuildersKt.putJsonObject(buildJsonMessageBuilder, "data") {
                `invokeSuspend$lambda$3$lambda$2`(arr, this)
            }
            return Unit
        }

        fun `invokeSuspend$lambda$3$lambda$2`(
            arr: JsonArray,
            putJsonObjectBuilder: JsonObjectBuilder
        ): Unit {
            JsonElementBuildersKt.put(putJsonObjectBuilder, "action", "app_list")
            putJsonObjectBuilder.put("apps", arr)
            return Unit
        }
    }
}
