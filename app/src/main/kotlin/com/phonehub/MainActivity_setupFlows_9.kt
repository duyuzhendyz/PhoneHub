package com.phonehub

import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.CoroutineLiveDataKt
import java.util.Map
import kotlin.KotlinNothingValueException
import kotlin.Pair
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

    public MainActivity$setupFlows$9(MainActivity mainActivity, Continuation<? super MainActivity$setupFlows$9> continuation) {
        super(2, continuation)
        this.this$0 = mainActivity
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new MainActivity$setupFlows$9(this.this$0, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((MainActivity$setupFlows$9) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            val receivedText: SharedFlow<Pair<String, String>> = ConnectionManager.INSTANCE.getReceivedText()
            val mainActivity: MainActivity = this.this$0
            this.label = 1
            if (receivedText.collect(FlowCollector() { // from class: com.phonehub.MainActivity$setupFlows$9.1
                override
                public   Object emit(Object value, Continuation $completion) {
                    return emit((Pair<String, String>) value, (Continuation<? super Unit>) $completion)
                    }

                fun emit(pair: Pair<String, String>, continuation: Continuation<? super Unit>): Any {
                    var map: Map? = null
                    val filename: String = pair.component1()
                    val text: String = pair.component2()
                    val key: String = filename + "|" + text
                    map = MainActivity.this.handledTextContents
                    val lastHandled: Long = (Long) map.get(key)
                    if (lastHandled == null || System.currentTimeMillis() - lastHandled.longValue() >= CoroutineLiveDataKt.DEFAULT_TIMEOUT) {
                        MainActivity.this.showReceivedTextDialog(filename, text)
                        return Unit.INSTANCE
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
