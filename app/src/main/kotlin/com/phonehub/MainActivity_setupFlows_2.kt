package com.phonehub

import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.phonehub.ConnectionManager
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
    var label: Int? = null
    final  MainActivity this$0

    public MainActivity$setupFlows$2(MainActivity mainActivity, Continuation<? super MainActivity$setupFlows$2> continuation) {
        super(2, continuation)
        this.this$0 = mainActivity
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new MainActivity$setupFlows$2(this.this$0, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((MainActivity$setupFlows$2) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            val connectionMessage: StateFlow<String> = ConnectionManager.INSTANCE.getConnectionMessage()
            val mainActivity: MainActivity = this.this$0
            this.label = 1
            if (connectionMessage.collect(FlowCollector() { // from class: com.phonehub.MainActivity$setupFlows$2.1
                override
                public   Object emit(Object value, Continuation $completion) {
                    return emit(value, (Continuation<? super Unit>) $completion)
                    }

                fun emit(msg: String, continuation: Continuation<? super Unit>): Any {
                    var textView: TextView? = null
                    var textView2: TextView? = null
                    ConnectionManager.ConnectionState state = ConnectionManager.INSTANCE.getConnectionState().getValue()
                    val textView3: TextView = null
                    if (state != ConnectionManager.ConnectionState.DISCONNECTED || !Intrinsics.areEqual(msg, "未连接")) {
                        textView = MainActivity.this.connectStatus
                        if (textView == null) {
                            Intrinsics.throwUninitializedPropertyAccessException("connectStatus")
                            } else {
                            textView3 = textView
                            }
                        textView3.setText(msg)
                        } else {
                        textView2 = MainActivity.this.connectStatus
                        if (textView2 == null) {
                            Intrinsics.throwUninitializedPropertyAccessException("connectStatus")
                            } else {
                            textView3 = textView2
                            }
                        textView3.setText("未连接")
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
