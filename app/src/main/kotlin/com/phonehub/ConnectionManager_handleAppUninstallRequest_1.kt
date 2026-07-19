package com.phonehub

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.Pair
import kotlin.ResultKt
import kotlin.TuplesKt
import kotlin.Unit
import kotlin.collections.MapsKt
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.Boxing
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope

class ConnectionManager {
    final  String $pkg
    var label: Int? = null

    public ConnectionManager$handleAppUninstallRequest$1(String str, Continuation<? super ConnectionManager$handleAppUninstallRequest$1> continuation) {
        super(2, continuation)
        this.$pkg = str
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$handleAppUninstallRequest$1(this.$pkg, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$handleAppUninstallRequest$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(obj: Any): Any {
        var context: Context? = null
        IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure(obj)
            try {
                val intent: Intent = new Intent("android.intent.action.DELETE", Uri.parse("package:" + this.$pkg))
                intent.addFlags(268435456)
                context = ConnectionManager.context
                if (context != null) {
                    context.startActivity(intent)
                    }
                ConnectionManager.INSTANCE.sendAction("app_uninstall_result", MapsKt.mapOf(TuplesKt.to("package", this.$pkg), TuplesKt.to("success", Boxing.boxBoolean(true))))
                } catch (Exception e) {
                val connectionManager: ConnectionManager = ConnectionManager.INSTANCE
                val pairArr: Array<Pair> = new Pair[3]
                pairArr[0] = TuplesKt.to("package", this.$pkg)
                pairArr[1] = TuplesKt.to("success", Boxing.boxBoolean(false))
                val message: String = e.getMessage()
                if (message == null) {
                    message = ""
                    }
                pairArr[2] = TuplesKt.to("error", message)
                connectionManager.sendAction("app_uninstall_result", MapsKt.mapOf(pairArr))
                }
            return Unit.INSTANCE
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }
    }
