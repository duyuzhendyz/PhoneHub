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
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope

class ConnectionManager {
    final  String $path
    var label: Int? = null

    public ConnectionManager$handleFileMkdir$1(String str, Continuation<? super ConnectionManager$handleFileMkdir$1> continuation) {
        super(2, continuation)
        this.$path = str
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$handleFileMkdir$1(this.$path, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$handleFileMkdir$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(obj: Any): Any {
        IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure(obj)
            try {
                File(this.$path).mkdirs()
                } catch (Exception e) {
                Log.e("PhoneHub", "File mkdir failed", e)
                }
            return Unit.INSTANCE
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }
    }
