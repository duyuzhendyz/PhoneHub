package com.phonehub

import android.util.Log
import java.io.File
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope

class `ConnectionManager$autoInstallApk$1`(private val `$path`: String, continuation: Continuation<*>?) : SuspendLambda(2, continuation) {
    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return `ConnectionManager$autoInstallApk$1`(`$path`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<*>): Any {
        return (create(coroutineScope, continuation) as `ConnectionManager$autoInstallApk$1`).invokeSuspend(Unit)
    }

    override fun invokeSuspend(obj: Any): Any {
        var file: File? = null
        IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(obj)
                try {
                    val file2 = File(`$path`)
                    if (!file2.exists()) {
                        file = ConnectionManager.receiveDir
                        val receivedFile = File(file, `$path`.substringAfterLast("/"))
                        if (receivedFile.exists()) {
                            ConnectionManager.doInstallApk(receivedFile)
                        } else {
                            Log.e("PhoneHub", "APK file not found: " + `$path`)
                        }
                    } else {
                        ConnectionManager.doInstallApk(file2)
                    }
                } catch (e: Exception) {
                    Log.e("PhoneHub", "autoInstallApk failed", e)
                }
                return Unit
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
    }
}
