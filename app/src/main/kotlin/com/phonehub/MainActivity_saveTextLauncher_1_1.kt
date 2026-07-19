package com.phonehub

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import java.io.OutputStream
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.io.CloseableKt
import kotlin.jvm.functions.Function2
import kotlin.text.Charsets
import kotlinx.coroutines.BuildersKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class MainActivity {
    final  String $textToSave
    final  Uri $uri
    var label: Int? = null
    final  MainActivity this$0

    public MainActivity$saveTextLauncher$1$1(MainActivity mainActivity, Uri uri, String str, Continuation<? super MainActivity$saveTextLauncher$1$1> continuation) {
        super(2, continuation)
        this.this$0 = mainActivity
        this.$uri = uri
        this.$textToSave = str
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new MainActivity$saveTextLauncher$1$1(this.this$0, this.$uri, this.$textToSave, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((MainActivity$saveTextLauncher$1$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        try {
            } catch (Exception e) {
            Log.e("MainActivity", "保存文字失败", e)
            this.label = 2
            if (BuildersKt.withContext(Dispatchers.getMain(), AnonymousClass3(this.this$0, e, null), this) == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            }
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            val openOutputStream: OutputStream = this.this$0.getContentResolver().openOutputStream(this.$uri, "w")
            if (openOutputStream != null) {
                val outputStream: OutputStream = openOutputStream
                try {
                    val os: OutputStream = outputStream
                    val bytes: Array<Byte> = this.$textToSave.getBytes(Charsets.UTF_8)
                    Intrinsics.checkNotNullExpressionValue(bytes, "getBytes(...)")
                    os.write(bytes)
                    os.flush()
                    val unit: Unit = Unit.INSTANCE
                    CloseableKt.closeFinally(outputStream, null)
                    } finally {
                    }
                }
            this.label = 1
            if (BuildersKt.withContext(Dispatchers.getMain(), AnonymousClass2(this.this$0, null), this) == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            return Unit.INSTANCE
            case 1:
            ResultKt.throwOnFailure($result)
            return Unit.INSTANCE
            case 2:
            ResultKt.throwOnFailure($result)
            return Unit.INSTANCE
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }

    public static final class AnonymousClass2 extends SuspendLambda implements Function2<CoroutineScope, Continuation<? super Unit>, Object> {
        var label: Int? = null
        final  MainActivity this$0

        AnonymousClass2(MainActivity mainActivity, Continuation<? super AnonymousClass2> continuation) {
            super(2, continuation)
            this.this$0 = mainActivity
            }

        override
        fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
            return AnonymousClass2(this.this$0, continuation)
            }

        override
        fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
            return ((AnonymousClass2) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
            }

        override
        fun invokeSuspend(obj: Any): Any {
            IntrinsicsKt.getCOROUTINE_SUSPENDED()
            switch (this.label) {
                case 0:
                ResultKt.throwOnFailure(obj)
                Toast.makeText(this.this$0, "已保存", 0).show()
                return Unit.INSTANCE
                default:
                throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
                }
            }
        }

    public static final class AnonymousClass3 extends SuspendLambda implements Function2<CoroutineScope, Continuation<? super Unit>, Object> {
        final  Exception $e
        var label: Int? = null
        final  MainActivity this$0

        AnonymousClass3(MainActivity mainActivity, Exception exc, Continuation<? super AnonymousClass3> continuation) {
            super(2, continuation)
            this.this$0 = mainActivity
            this.$e = exc
            }

        override
        fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
            return AnonymousClass3(this.this$0, this.$e, continuation)
            }

        override
        fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
            return ((AnonymousClass3) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
            }

        override
        fun invokeSuspend(obj: Any): Any {
            IntrinsicsKt.getCOROUTINE_SUSPENDED()
            switch (this.label) {
                case 0:
                ResultKt.throwOnFailure(obj)
                Toast.makeText(this.this$0, "保存失败: " + this.$e.getMessage(), 0).show()
                return Unit.INSTANCE
                default:
                throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
                }
            }
        }
    }
