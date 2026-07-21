package com.phonehub

import android.view.View
import android.widget.Button
import android.widget.TextView
import kotlin.KotlinNothingValueException
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

class MainActivity_setupFlows_11(
    private val `this$0`: MainActivity,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return MainActivity_setupFlows_11(this.`this$0`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as MainActivity_setupFlows_11).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val transferPausedFromPc: StateFlow<Boolean> = ConnectionManager.INSTANCE.transferPausedFromPc
                val mainActivity: MainActivity = this.`this$0`
                label = 1
                val collectResult = transferPausedFromPc.collect(object : FlowCollector<Boolean> {
                    override suspend fun emit(paused: Boolean) {
                        val hashMap = mainActivity.pageCache
                        val v = hashMap.get(1) as View?
                        if (v != null) {
                            val btn = v.findViewById<Button>(R.id.pauseFileBtn)
                            if (btn != null) {
                                if (paused) {
                                    btn.setText("继续")
                                    val textView = v.findViewById<TextView>(R.id.fileSpeedText)
                                    if (textView != null) {
                                        textView.setText("已暂停(对端)")
                                    }
                                } else {
                                    btn.setText("暂停")
                                    val textView2 = v.findViewById<TextView>(R.id.fileSpeedText)
                                    if (textView2 != null) {
                                        textView2.setText("继续传输...")
                                    }
                                }
                            }
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
