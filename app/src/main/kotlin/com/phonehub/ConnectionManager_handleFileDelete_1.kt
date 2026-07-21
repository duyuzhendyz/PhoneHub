package com.phonehub

import android.util.Log
import java.io.File
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope

class ConnectionManager_handleFileDelete_1(
    val path: String,
    val isDir: Boolean,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    override fun create(obj: Any?, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_handleFileDelete_1(this.path, this.isDir, continuation as Continuation<Unit>)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_handleFileDelete_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(obj: Any): Any {
        IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(obj)
                try {
                    val f: File = File(this.path)
                    if (this.isDir) {
                        f.deleteRecursively()
                    } else {
                        f.delete()
                    }
                } catch (e: Exception) {
                    Log.e("PhoneHub", "File delete failed", e)
                }
                return Unit
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
    }
}
