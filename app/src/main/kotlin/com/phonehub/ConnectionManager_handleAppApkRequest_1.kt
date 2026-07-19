package com.phonehub

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.constraintlayout.widget.ConstraintLayout
import java.io.File
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

    public ConnectionManager$handleAppApkRequest$1(String str, Continuation<? super ConnectionManager$handleAppApkRequest$1> continuation) {
        super(2, continuation)
        this.$pkg = str
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$handleAppApkRequest$1(this.$pkg, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$handleAppApkRequest$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(obj: Any): Any {
        var context: Context? = null
        var pm: PackageManager? = null
        IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure(obj)
            try {
                context = ConnectionManager.context
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
                connectionManager.sendAction("app_apk_result", MapsKt.mapOf(pairArr))
                }
            if (context != null && (pm = context.getPackageManager()) != null) {
                val appInfo: ApplicationInfo = pm.getApplicationInfo(this.$pkg, 0)
                Intrinsics.checkNotNullExpressionValue(appInfo, "getApplicationInfo(...)")
                val apkPath: String = appInfo.sourceDir
                if (apkPath != null && File(apkPath).exists()) {
                    ConnectionManager.INSTANCE.sendFile(File(apkPath))
                    } else {
                    ConnectionManager.INSTANCE.sendAction("app_apk_result", MapsKt.mapOf(TuplesKt.to("package", this.$pkg), TuplesKt.to("success", Boxing.boxBoolean(false)), TuplesKt.to("error", "APK not found")))
                    }
                return Unit.INSTANCE
                }
            return Unit.INSTANCE
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }
    }
