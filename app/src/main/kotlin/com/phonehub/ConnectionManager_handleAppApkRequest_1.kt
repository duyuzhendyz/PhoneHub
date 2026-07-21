package com.phonehub

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File
import kotlin.Pair
import kotlin.ResultKt
import kotlin.TuplesKt
import kotlin.Unit
import kotlin.collections.MapsKt
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope

class ConnectionManager_handleAppApkRequest_1(
    private val `$pkg`: String,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_handleAppApkRequest_1(this.`$pkg`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_handleAppApkRequest_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        var context: Context? = null
        var pm: PackageManager? = null
        IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                try {
                    context = ConnectionManager.context
                } catch (e: Exception) {
                    val connectionManager = ConnectionManager.INSTANCE
                    val message = e.message ?: ""
                    connectionManager.sendAction("app_apk_result", MapsKt.mapOf(
                        TuplesKt.to("package", this.`$pkg`),
                        TuplesKt.to("success", false),
                        TuplesKt.to("error", message)
                    ))
                }
                if (context != null) {
                    pm = context.packageManager
                    if (pm != null) {
                        val appInfo: ApplicationInfo = pm.getApplicationInfo(this.`$pkg`, 0)
                        val apkPath: String? = appInfo.sourceDir
                        if (apkPath != null && File(apkPath).exists()) {
                            ConnectionManager.INSTANCE.sendFile(File(apkPath))
                        } else {
                            ConnectionManager.INSTANCE.sendAction("app_apk_result", MapsKt.mapOf(
                                TuplesKt.to("package", this.`$pkg`),
                                TuplesKt.to("success", false),
                                TuplesKt.to("error", "APK not found")
                            ))
                        }
                        return Unit
                    }
                }
                return Unit
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
    }
}
