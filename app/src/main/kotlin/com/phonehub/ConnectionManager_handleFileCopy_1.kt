package com.phonehub

import android.util.Log
import java.io.File
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.Boxing
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.io.FilesKt
import kotlinx.coroutines.CoroutineScope

class ConnectionManager_handleFileCopy_1(
    private val src: String,
    private val dst: String,
    private val isDir: Boolean,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_handleFileCopy_1(this.src, this.dst, this.isDir, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_handleFileCopy_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                try {
                    val srcFile: File = File(this.src)
                    val dstFile: File = File(this.dst)
                    if (this.isDir) {
                        Boxing.boxBoolean(FilesKt.copyRecursively$default(srcFile, dstFile, true, null, 4, null))
                    } else {
                        FilesKt.copyTo$default(srcFile, dstFile, true, 0, 4, null)
                    }
                } catch (e: Exception) {
                    Boxing.boxInt(Log.e("PhoneHub", "File copy failed", e))
                }
                return Unit
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
    }
}
