package com.phonehub

import android.util.Log
import java.io.File
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope

class ConnectionManager_handleFileRename_1(
    val oldPath: String,
    val newPath: String,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    override fun create(obj: Any?, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_handleFileRename_1(this.oldPath, this.newPath, continuation as Continuation<Unit>)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_handleFileRename_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(obj: Any): Any {
        IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(obj)
                try {
                    File(this.oldPath).renameTo(File(this.newPath))
                } catch (e: Exception) {
                    Log.e("PhoneHub", "File rename failed", e)
                }
                return Unit
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
    }
}
