package com.phonehub

import android.view.View
import android.widget.Button
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

    public MainActivity$setupFlows$11(MainActivity mainActivity, Continuation<? super MainActivity$setupFlows$11> continuation) {
        super(2, continuation)
        this.this$0 = mainActivity
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new MainActivity$setupFlows$11(this.this$0, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((MainActivity$setupFlows$11) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            val transferPausedFromPc: StateFlow<Boolean> = ConnectionManager.INSTANCE.getTransferPausedFromPc()
            val mainActivity: MainActivity = this.this$0
            this.label = 1
            if (transferPausedFromPc.collect(FlowCollector() { // from class: com.phonehub.MainActivity$setupFlows$11.1
                override
                public   Object emit(Object value, Continuation $completion) {
                    return emit(((Boolean) value).booleanValue(), (Continuation<? super Unit>) $completion)
                    }

                fun emit(paused: Boolean, continuation: Continuation<? super Unit>): Any {
                    var hashMap: HashMap? = null
                    var btn: Button? = null
                    hashMap = MainActivity.this.pageCache
                    val v: View = (View) hashMap.get(Boxing.boxInt(1))
                    if (v != null && (btn = (Button) v.findViewById(R.id.pauseFileBtn)) != null) {
                        if (paused) {
                            btn.setText("继续")
                            val textView: TextView = (TextView) v.findViewById(R.id.fileSpeedText)
                            if (textView != null) {
                                textView.setText("已暂停(对端)")
                                }
                            } else {
                            btn.setText("暂停")
                            val textView2: TextView = (TextView) v.findViewById(R.id.fileSpeedText)
                            if (textView2 != null) {
                                textView2.setText("继续传输...")
                                }
                            }
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
