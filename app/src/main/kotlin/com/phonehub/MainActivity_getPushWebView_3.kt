package com.phonehub

import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.KotlinNothingValueException
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow

class MainActivity {
    var label: Int? = null
    final  MainActivity this$0

    public MainActivity$getPushWebView$3(MainActivity mainActivity, Continuation<? super MainActivity$getPushWebView$3> continuation) {
        super(2, continuation)
        this.this$0 = mainActivity
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new MainActivity$getPushWebView$3(this.this$0, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((MainActivity$getPushWebView$3) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            val receivedUrl: SharedFlow<String> = ConnectionManager.INSTANCE.getReceivedUrl()
            val mainActivity: MainActivity = this.this$0
            this.label = 1
            if (receivedUrl.collect(FlowCollector() { // from class: com.phonehub.MainActivity$getPushWebView$3.1
                override
                public   Object emit(Object value, Continuation $completion) {
                    return emit(value, (Continuation<? super Unit>) $completion)
                    }

                fun emit(url: String, continuation: Continuation<? super Unit>): Any {
                    if (url.length() > 0) {
                        MainActivity.this.addUrlHistory(url, "电脑 -> 手机")
                        MainActivity.this.refreshUrlHistoryList()
                        Toast.makeText(MainActivity.this, "收到电脑推送 URL: " + url, 0).show()
                        }
                    return Unit.INSTANCE
                    }
                }, this) == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            break
            case 1:
            ResultKt.throwOnFailure($result)
            break
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        throw KotlinNothingValueException()
        }
    }
