package com.phonehub

import android.widget.TextView
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
import kotlinx.coroutines.flow.StateFlow

class MainActivity {
    final  TextView $mediaInfoText
    var label: Int? = null

    public MainActivity$getRemoteView$4(TextView textView, Continuation<? super MainActivity$getRemoteView$4> continuation) {
        super(2, continuation)
        this.$mediaInfoText = textView
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new MainActivity$getRemoteView$4(this.$mediaInfoText, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((MainActivity$getRemoteView$4) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            val mediaInfo: StateFlow<String> = ConnectionManager.INSTANCE.getMediaInfo()
            val textView: TextView = this.$mediaInfoText
            this.label = 1
            if (mediaInfo.collect(FlowCollector() { // from class: com.phonehub.MainActivity$getRemoteView$4.1
                override
                public   Object emit(Object value, Continuation $completion) {
                    return emit(value, (Continuation<? super Unit>) $completion)
                    }

                fun emit(info: String, continuation: Continuation<? super Unit>): Any {
                    val textView2: TextView = textView
                    if (textView2 != null) {
                        textView2.setText(info)
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
