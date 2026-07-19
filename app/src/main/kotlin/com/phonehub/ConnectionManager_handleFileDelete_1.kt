package com.phonehub

import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import java.io.File
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.io.FilesKt
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope

class ConnectionManager {
    final  boolean $isDir
    final  String $path
    var label: Int? = null

    public ConnectionManager$handleFileDelete$1(String str, boolean z, Continuation<? super ConnectionManager$handleFileDelete$1> continuation) {
        super(2, continuation)
        this.$path = str
        this.$isDir = z
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$handleFileDelete$1(this.$path, this.$isDir, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$handleFileDelete$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(obj: Any): Any {
        IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure(obj)
            try {
                val f: File = new File(this.$path)
                if (this.$isDir) {
                    FilesKt.deleteRecursively(f)
                    } else {
                    f.delete()
                    }
                } catch (Exception e) {
                Log.e("PhoneHub", "File delete failed", e)
                }
            return Unit.INSTANCE
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }
    }
