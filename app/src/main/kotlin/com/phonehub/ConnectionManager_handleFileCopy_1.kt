package com.phonehub

import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import java.io.File
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.Boxing
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.io.FilesKt
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope

class ConnectionManager {
    final  String $dst
    final  boolean $isDir
    final  String $src
    var label: Int? = null

    public ConnectionManager$handleFileCopy$1(String str, String str2, boolean z, Continuation<? super ConnectionManager$handleFileCopy$1> continuation) {
        super(2, continuation)
        this.$src = str
        this.$dst = str2
        this.$isDir = z
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$handleFileCopy$1(this.$src, this.$dst, this.$isDir, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$handleFileCopy$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(obj: Any): Any {
        IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure(obj)
            try {
                val srcFile: File = new File(this.$src)
                val dstFile: File = new File(this.$dst)
                if (this.$isDir) {
                    Boxing.boxBoolean(FilesKt.copyRecursively$default(srcFile, dstFile, true, null, 4, null))
                    } else {
                    FilesKt.copyTo$default(srcFile, dstFile, true, 0, 4, null)
                    }
                } catch (Exception e) {
                Boxing.boxInt(Log.e("PhoneHub", "File copy failed", e))
                }
            return Unit.INSTANCE
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }
    }
