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
import kotlin.jvm.functions.Function2
import kotlin.text.StringsKt
import kotlinx.coroutines.CoroutineScope

class ConnectionManager {
    final  String $path
    var label: Int? = null

    public ConnectionManager$autoInstallApk$1(String str, Continuation<? super ConnectionManager$autoInstallApk$1> continuation) {
        super(2, continuation)
        this.$path = str
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$autoInstallApk$1(this.$path, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$autoInstallApk$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(obj: Any): Any {
        var file: File? = null
        IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure(obj)
            try {
                val file2: File = new File(this.$path)
                if (!file2.exists()) {
                    file = ConnectionManager.receiveDir
                    val receivedFile: File = new File(file, StringsKt.substringAfterLast$default(this.$path, "/", (String) null, 2, (Object) null))
                    if (receivedFile.exists()) {
                        ConnectionManager.INSTANCE.doInstallApk(receivedFile)
                        } else {
                        Boxing.boxInt(Log.e("PhoneHub", "APK file not found: " + this.$path))
                        }
                    } else {
                    ConnectionManager.INSTANCE.doInstallApk(file2)
                    }
                } catch (Exception e) {
                Boxing.boxInt(Log.e("PhoneHub", "autoInstallApk failed", e))
                }
            return Unit.INSTANCE
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }
    }
