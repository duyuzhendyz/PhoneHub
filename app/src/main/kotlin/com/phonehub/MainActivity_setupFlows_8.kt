package com.phonehub

import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import java.util.HashMap
import kotlin.KotlinNothingValueException
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.Boxing
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

class MainActivity {
    var label: Int? = null
    final  MainActivity this$0

    public MainActivity$setupFlows$8(MainActivity mainActivity, Continuation<? super MainActivity$setupFlows$8> continuation) {
        super(2, continuation)
        this.this$0 = mainActivity
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new MainActivity$setupFlows$8(this.this$0, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((MainActivity$setupFlows$8) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            val mediaInfo: StateFlow<String> = ConnectionManager.INSTANCE.getMediaInfo()
            val mainActivity: MainActivity = this.this$0
            this.label = 1
            if (mediaInfo.collect(FlowCollector() { // from class: com.phonehub.MainActivity$setupFlows$8.1
                override
                public   Object emit(Object value, Continuation $completion) {
                    return emit(value, (Continuation<? super Unit>) $completion)
                    }

                fun emit(info: String, continuation: Continuation<? super Unit>): Any {
                    var hashMap: HashMap? = null
                    var textView: TextView? = null
                    hashMap = MainActivity.this.pageCache
                    val view: View = (View) hashMap.get(Boxing.boxInt(2))
                    if (view != null && (textView = (TextView) view.findViewById(R.id.mediaInfoText)) != null) {
                        textView.setText(info)
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
