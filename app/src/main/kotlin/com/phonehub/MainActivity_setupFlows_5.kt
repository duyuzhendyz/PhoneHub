package com.phonehub

import android.view.View
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

class MainActivity_setupFlows_5(
    private val `this$0`: MainActivity,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return MainActivity_setupFlows_5(this.`this$0`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as MainActivity_setupFlows_5).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val currentChannel: StateFlow<ConnectionManager.ChannelType> = ConnectionManager.INSTANCE.currentChannel
                val mainActivity: MainActivity = this.`this$0`
                label = 1
                val collectResult = currentChannel.collect(object : FlowCollector<ConnectionManager.ChannelType> {
                    override suspend fun emit(channel: ConnectionManager.ChannelType) {
                        val channelName: String = when (channel) {
                            ConnectionManager.ChannelType.WIFI -> "WiFi 直连"
                            ConnectionManager.ChannelType.ADB -> "USB 数据线"
                            else -> "无"
                        }
                        val hashMap = mainActivity.pageCache
                        val view = hashMap.get(0) as View?
                        if (view != null) {
                            val textView2 = view.findViewById<TextView>(R.id.channelHome)
                            if (textView2 != null) {
                                textView2.setText("通道: " + channelName)
                            }
                        }
                        val hashMap2 = mainActivity.pageCache
                        val view2 = hashMap2.get(16) as View?
                        if (view2 != null) {
                            val textView = view2.findViewById<TextView>(R.id.infoChannel)
                            if (textView != null) {
                                textView.setText("通道: " + channelName)
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
