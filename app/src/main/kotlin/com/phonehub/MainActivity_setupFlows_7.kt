package com.phonehub

import android.view.View
import android.widget.TextView
import kotlin.KotlinNothingValueException
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.Boxing
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

class `MainActivity$setupFlows$7`(private val `this$0`: MainActivity, continuation: Continuation<*>?) : SuspendLambda(2, continuation) {
    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return `MainActivity$setupFlows$7`(`this$0`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<*>): Any {
        return (create(coroutineScope, continuation) as `MainActivity$setupFlows$7`).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val receivedClipboard: StateFlow<String> = ConnectionManager.getReceivedClipboard()
                val mainActivity = `this$0`
                label = 1
                val collectResult = receivedClipboard.collect(object : FlowCollector<String> {
                    override suspend fun emit(text: String) {
                        if (text != null) {
                            val hashMap = mainActivity.pageCache
                            val view = hashMap.get(Boxing.boxInt(4)) as View?
                            if (view != null) {
                                val textView = view.findViewById<TextView>(R.id.currentClipText)
                                if (textView != null) {
                                    textView.setText(text)
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
