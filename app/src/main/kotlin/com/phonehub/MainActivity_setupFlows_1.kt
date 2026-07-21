package com.phonehub

import android.view.View
import android.widget.Button
import android.widget.TextView
import kotlin.KotlinNothingValueException
import kotlin.NoWhenBranchMatchedException
import kotlin.ResultKt
import kotlin.Triple
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

class MainActivity_setupFlows_1(
    private val `this$0`: MainActivity,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return MainActivity_setupFlows_1(this.`this$0`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as MainActivity_setupFlows_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val connectionState: StateFlow<ConnectionManager.ConnectionState> =
                    ConnectionManager.INSTANCE.connectionState
                val mainActivity: MainActivity = this.`this$0`
                label = 1
                val collectResult = connectionState.collect(object : FlowCollector<ConnectionManager.ConnectionState> {
                    override suspend fun emit(state: ConnectionManager.ConnectionState) {
                        when (state) {
                            ConnectionManager.ConnectionState.DISCONNECTED -> {
                                val button: Button? = mainActivity.connectBtn
                                button?.isEnabled = true
                                val msg: String = ConnectionManager.INSTANCE.connectionMessage.value
                                val textView: TextView? = mainActivity.connectStatus
                                textView?.text = if (
                                    msg.startsWith("WiFi 连接失败") || msg.startsWith("ADB 连接失败")
                                ) msg else "未连接"
                                val textView2: TextView? = mainActivity.statusText
                                textView2?.text = "未连接"
                                val textView3: TextView? = mainActivity.statusText
                                textView3?.setTextColor(-3066824)
                                val hashMap: java.util.HashMap<Int, View> = mainActivity.pageCache
                                hashMap.remove(0)
                                val view: View? = hashMap[16]
                                if (view != null) {
                                    view.findViewById<TextView?>(R.id.infoStatus)?.text = "状态: 未连接"
                                }
                                val hashMap2: java.util.HashMap<Int, View> = mainActivity.pageCache
                                val view2: View? = hashMap2[16]
                                if (view2 != null) {
                                    view2.findViewById<TextView?>(R.id.infoStatus)?.setTextColor(-3066824)
                                }
                                val hashMap3: java.util.HashMap<Int, View> = mainActivity.pageCache
                                val view3: View? = hashMap3[16]
                                if (view3 != null) {
                                    view3.findViewById<TextView?>(R.id.infoChannel)?.text = "通道: 无"
                                }
                                val hashMap4: java.util.HashMap<Int, View> = mainActivity.pageCache
                                val view4: View? = hashMap4[16]
                                if (view4 != null) {
                                    view4.findViewById<TextView?>(R.id.infoIp)?.text = "IP: 未知"
                                }
                                mainActivity.updateSetupVisibility()
                            }
                            ConnectionManager.ConnectionState.CONNECTING -> {
                                val button2: Button? = mainActivity.connectBtn
                                button2?.isEnabled = false
                                val msg2: String = ConnectionManager.INSTANCE.connectionMessage.value
                                val textView8: TextView? = mainActivity.connectStatus
                                textView8?.text = if (
                                    msg2.startsWith("检测到 ADB 通道") || msg2.startsWith("ADB 连接失败")
                                ) msg2 else "连接中..."
                                val textView9: TextView? = mainActivity.statusText
                                textView9?.text = "连接中..."
                                val textView10: TextView? = mainActivity.statusText
                                textView10?.setTextColor(-18176)
                                val hashMap6: java.util.HashMap<Int, View> = mainActivity.pageCache
                                hashMap6.remove(0)
                                val hashMap7: java.util.HashMap<Int, View> = mainActivity.pageCache
                                val view5: View? = hashMap7[16]
                                if (view5 != null) {
                                    view5.findViewById<TextView?>(R.id.infoStatus)?.text = "状态: 连接中..."
                                }
                                val hashMap8: java.util.HashMap<Int, View> = mainActivity.pageCache
                                val view6: View? = hashMap8[16]
                                if (view6 != null) {
                                    view6.findViewById<TextView?>(R.id.infoStatus)?.setTextColor(-18176)
                                }
                            }
                            ConnectionManager.ConnectionState.CONNECTED -> {
                                val button3: Button? = mainActivity.connectBtn
                                button3?.isEnabled = true
                                val textView13: TextView? = mainActivity.connectStatus
                                textView13?.text = "已连接"
                                val textView14: TextView? = mainActivity.statusText
                                textView14?.text = "已连接"
                                val textView15: TextView? = mainActivity.statusText
                                textView15?.setTextColor(-15696880)
                                val hashMap9: java.util.HashMap<Int, View> = mainActivity.pageCache
                                hashMap9.remove(0)
                                val hashMap10: java.util.HashMap<Int, View> = mainActivity.pageCache
                                val view7: View? = hashMap10[16]
                                if (view7 != null) {
                                    view7.findViewById<TextView?>(R.id.infoStatus)?.text = "状态: 已连接"
                                }
                                val hashMap11: java.util.HashMap<Int, View> = mainActivity.pageCache
                                val view8: View? = hashMap11[16]
                                if (view8 != null) {
                                    view8.findViewById<TextView?>(R.id.infoStatus)?.setTextColor(-15696880)
                                }
                                val hashMap12: java.util.HashMap<Int, View> = mainActivity.pageCache
                                val view9: View? = hashMap12[16]
                                if (view9 != null) {
                                    var pcIp: String? = ConnectionManager.INSTANCE.pcIp
                                    if (pcIp == null) {
                                        pcIp = "未知"
                                    }
                                    view9.findViewById<TextView?>(R.id.infoIp)?.text = "IP: " + pcIp
                                }
                                mainActivity.updateSetupVisibility()
                                val list: List<MainActivity.UrlHistoryItem> = mainActivity.urlHistory
                                if (list.isEmpty()) {
                                    mainActivity.loadUrlHistory()
                                }
                                val connectionManager: ConnectionManager = ConnectionManager.INSTANCE
                                val iterable: List<MainActivity.UrlHistoryItem> = mainActivity.urlHistory
                                val destination = ArrayList<Triple<String, String, Long>>(
                                    iterable.size
                                )
                                for (item in iterable) {
                                    val it: MainActivity.UrlHistoryItem = item
                                    destination.add(Triple(it.url, it.direction, it.timestamp))
                                }
                                connectionManager.sendUrlHistorySync(destination)
                            }
                            else -> throw NoWhenBranchMatchedException()
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
