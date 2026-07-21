package com.phonehub

import android.content.Intent
import android.net.Uri
import kotlin.ResultKt
import kotlin.TuplesKt
import kotlin.Unit
import kotlin.collections.MapsKt
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.Boxing
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope

class `ConnectionManager$handleAppUninstallRequest$1`(private val `$pkg`: String, continuation: Continuation<*>?) : SuspendLambda(2, continuation) {
    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return `ConnectionManager$handleAppUninstallRequest$1`(`$pkg`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<*>): Any {
        return (create(coroutineScope, continuation) as `ConnectionManager$handleAppUninstallRequest$1`).invokeSuspend(Unit)
    }

    override fun invokeSuspend(obj: Any): Any {
        IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(obj)
                try {
                    val intent = Intent("android.intent.action.DELETE", Uri.parse("package:" + this.`$pkg`))
                    intent.addFlags(268435456)
                    val context = ConnectionManager.context
                    if (context != null) {
                        context.startActivity(intent)
                    }
                    ConnectionManager.sendAction("app_uninstall_result", MapsKt.mapOf(TuplesKt.to("package", this.`$pkg`), TuplesKt.to("success", Boxing.boxBoolean(true))))
                } catch (e: Exception) {
                    val message = e.message ?: ""
                    ConnectionManager.sendAction("app_uninstall_result", MapsKt.mapOf(TuplesKt.to("package", this.`$pkg`), TuplesKt.to("success", Boxing.boxBoolean(false)), TuplesKt.to("error", message)))
                }
                return Unit
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
    }
}
