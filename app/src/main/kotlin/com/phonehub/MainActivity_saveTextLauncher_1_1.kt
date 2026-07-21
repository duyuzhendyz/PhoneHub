package com.phonehub

import android.net.Uri
import android.util.Log
import android.widget.Toast
import java.io.OutputStream
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.io.CloseableKt
import kotlin.text.Charsets
import kotlinx.coroutines.BuildersKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class MainActivity_saveTextLauncher_1_1(
    private val mainActivity: MainActivity,
    private val uri: Uri,
    private val textToSave: String,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return MainActivity_saveTextLauncher_1_1(this.mainActivity, this.uri, this.textToSave, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as MainActivity_saveTextLauncher_1_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        try {
        } catch (e: Exception) {
            Log.e("MainActivity", "保存文字失败", e)
            this.label = 2
            val withContext = BuildersKt.withContext(Dispatchers.getMain(), MainActivity_saveTextLauncher_1_1_AnonymousClass3(this.mainActivity, e, null), this)
            if (withContext == coroutine_suspended) {
                return coroutine_suspended
            }
        }
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val openOutputStream: OutputStream? = this.mainActivity.contentResolver.openOutputStream(this.uri, "w")
                if (openOutputStream != null) {
                    val outputStream: OutputStream = openOutputStream
                    try {
                        val bytes = this.textToSave.toByteArray(Charsets.UTF_8)
                        outputStream.write(bytes)
                        outputStream.flush()
                        CloseableKt.closeFinally(outputStream, null)
                    } finally {
                    }
                }
                this.label = 1
                val withContext = BuildersKt.withContext(Dispatchers.getMain(), MainActivity_saveTextLauncher_1_1_AnonymousClass2(this.mainActivity, null), this)
                if (withContext == coroutine_suspended) {
                    return coroutine_suspended
                }
                return Unit
            }
            1 -> {
                ResultKt.throwOnFailure(result)
                return Unit
            }
            2 -> {
                ResultKt.throwOnFailure(result)
                return Unit
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
        return Unit
    }
}

class MainActivity_saveTextLauncher_1_1_AnonymousClass2(
    private val mainActivity: MainActivity,
    continuation: Continuation<*>?
) : SuspendLambda(2, continuation) {

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return MainActivity_saveTextLauncher_1_1_AnonymousClass2(this.mainActivity, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as MainActivity_saveTextLauncher_1_1_AnonymousClass2).invokeSuspend(Unit)
    }

    override fun invokeSuspend(obj: Any): Any {
        IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(obj)
                Toast.makeText(this.mainActivity, "已保存", 0).show()
                return Unit
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
    }
}

class MainActivity_saveTextLauncher_1_1_AnonymousClass3(
    private val mainActivity: MainActivity,
    private val e: Exception,
    continuation: Continuation<*>?
) : SuspendLambda(2, continuation) {

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return MainActivity_saveTextLauncher_1_1_AnonymousClass3(this.mainActivity, this.e, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as MainActivity_saveTextLauncher_1_1_AnonymousClass3).invokeSuspend(Unit)
    }

    override fun invokeSuspend(obj: Any): Any {
        IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(obj)
                Toast.makeText(this.mainActivity, "保存失败: " + this.e.message, 0).show()
                return Unit
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
    }
}
