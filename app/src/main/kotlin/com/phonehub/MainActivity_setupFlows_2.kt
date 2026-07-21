package com.phonehub

import android.widget.TextView
import com.phonehub.ConnectionManager
import kotlin.KotlinNothingValueException
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

class MainActivity_setupFlows_2(
    private val `this$0`: MainActivity,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return MainActivity_setupFlows_2(this.`this$0`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as MainActivity_setupFlows_2).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val connectionMessage: StateFlow<String> = ConnectionManager.INSTANCE.connectionMessage
                val mainActivity: MainActivity = this.`this$0`
                label = 1
                val collectResult = connectionMessage.collect(object : FlowCollector<String> {
                    override suspend fun emit(msg: String) {
                        val state = ConnectionManager.INSTANCE.connectionState.value
                        if (state != ConnectionManager.ConnectionState.DISCONNECTED || msg != "未连接") {
                            val textView: TextView = mainActivity.connectStatus
                            textView.text = msg
                        } else {
                            val textView2: TextView = mainActivity.connectStatus
                            textView2.text = "未连接"
                        }
                    }
                })
                if (collectResult == coroutine_suspended) {
                    return coroutine_suspended
                }
            }
            1 -> {
                ResultKt.throwOnFailure(result)
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
        throw KotlinNothingValueException()
    }
}
