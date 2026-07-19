package com.phonehub

import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.phonehub.ConnectionManager
import com.phonehub.MainActivity
import java.util.ArrayList
import java.util.Collection
import java.util.HashMap
import java.util.List
import kotlin.KotlinNothingValueException
import kotlin.NoWhenBranchMatchedException
import kotlin.ResultKt
import kotlin.Triple
import kotlin.Unit
import kotlin.collections.CollectionsKt
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.Boxing
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlin.text.StringsKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

class MainActivity {
    var label: Int? = null
    final  MainActivity this$0

    public MainActivity$setupFlows$1(MainActivity mainActivity, Continuation<? super MainActivity$setupFlows$1> continuation) {
        super(2, continuation)
        this.this$0 = mainActivity
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new MainActivity$setupFlows$1(this.this$0, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((MainActivity$setupFlows$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            StateFlow<ConnectionManager.ConnectionState> connectionState = ConnectionManager.INSTANCE.getConnectionState()
            val mainActivity: MainActivity = this.this$0
            this.label = 1
            if (connectionState.collect(FlowCollector() { // from class: com.phonehub.MainActivity$setupFlows$1.1

                public  class WhenMappings {
                    public static final  int[] $EnumSwitchMapping$0

                    static {
                        val iArr: Array<Int> = new int[ConnectionManager.ConnectionState.values().length]
                        try {
                            iArr[ConnectionManager.ConnectionState.DISCONNECTED.ordinal()] = 1
                            } catch (NoSuchFieldError e) {
                            }
                        try {
                            iArr[ConnectionManager.ConnectionState.CONNECTING.ordinal()] = 2
                            } catch (NoSuchFieldError e2) {
                            }
                        try {
                            iArr[ConnectionManager.ConnectionState.CONNECTED.ordinal()] = 3
                            } catch (NoSuchFieldError e3) {
                            }
                        $EnumSwitchMapping$0 = iArr
                        }
                    }

                override
                public   Object emit(Object value, Continuation $completion) {
                    return emit((ConnectionManager.ConnectionState) value, (Continuation<? super Unit>) $completion)
                    }

                fun emit(/* ConnectionManager.ConnectionState state */, continuation: Continuation<? super Unit>): Any {
                    var button: Button? = null
                    var textView: TextView? = null
                    var textView2: TextView? = null
                    var textView3: TextView? = null
                    var hashMap: HashMap? = null
                    var hashMap2: HashMap? = null
                    var hashMap3: HashMap? = null
                    var hashMap4: HashMap? = null
                    var hashMap5: HashMap? = null
                    var textView4: TextView? = null
                    var textView5: TextView? = null
                    var textView6: TextView? = null
                    var textView7: TextView? = null
                    var button2: Button? = null
                    var textView8: TextView? = null
                    var textView9: TextView? = null
                    var textView10: TextView? = null
                    var hashMap6: HashMap? = null
                    var hashMap7: HashMap? = null
                    var hashMap8: HashMap? = null
                    var textView11: TextView? = null
                    var textView12: TextView? = null
                    var button3: Button? = null
                    var textView13: TextView? = null
                    var textView14: TextView? = null
                    var textView15: TextView? = null
                    var hashMap9: HashMap? = null
                    var hashMap10: HashMap? = null
                    var hashMap11: HashMap? = null
                    var hashMap12: HashMap? = null
                    var list: List? = null
                    var iterable: Iterable? = null
                    var textView16: TextView? = null
                    var textView17: TextView? = null
                    var textView18: TextView? = null
                    val textView19: TextView = null
                    switch (WhenMappings.$EnumSwitchMapping$0[state.ordinal()]) {
                        case 1:
                        button = MainActivity.this.connectBtn
                        if (button == null) {
                            Intrinsics.throwUninitializedPropertyAccessException("connectBtn")
                            button = null
                            }
                        button.setEnabled(true)
                        val msg: String = ConnectionManager.INSTANCE.getConnectionMessage().getValue()
                        textView = MainActivity.this.connectStatus
                        if (textView == null) {
                            Intrinsics.throwUninitializedPropertyAccessException("connectStatus")
                            textView = null
                            }
                        textView.setText((StringsKt.startsWith$default(msg, "WiFi 连接失败", false, 2, (Object) null) || StringsKt.startsWith$default(msg, "ADB 连接失败", false, 2, (Object) null)) ? msg : "未连接")
                        textView2 = MainActivity.this.statusText
                        if (textView2 == null) {
                            Intrinsics.throwUninitializedPropertyAccessException("statusText")
                            textView2 = null
                            }
                        textView2.setText("未连接")
                        textView3 = MainActivity.this.statusText
                        if (textView3 == null) {
                            Intrinsics.throwUninitializedPropertyAccessException("statusText")
                            } else {
                            textView19 = textView3
                            }
                        textView19.setTextColor(-3066824)
                        hashMap = MainActivity.this.pageCache
                        hashMap.remove(Boxing.boxInt(0))
                        hashMap2 = MainActivity.this.pageCache
                        val view: View = (View) hashMap2.get(Boxing.boxInt(16))
                        if (view != null && (textView7 = (TextView) view.findViewById(R.id.infoStatus)) != null) {
                            textView7.setText("状态: 未连接")
                            }
                        hashMap3 = MainActivity.this.pageCache
                        val view2: View = (View) hashMap3.get(Boxing.boxInt(16))
                        if (view2 != null && (textView6 = (TextView) view2.findViewById(R.id.infoStatus)) != null) {
                            textView6.setTextColor(-3066824)
                            }
                        hashMap4 = MainActivity.this.pageCache
                        val view3: View = (View) hashMap4.get(Boxing.boxInt(16))
                        if (view3 != null && (textView5 = (TextView) view3.findViewById(R.id.infoChannel)) != null) {
                            textView5.setText("通道: 无")
                            }
                        hashMap5 = MainActivity.this.pageCache
                        val view4: View = (View) hashMap5.get(Boxing.boxInt(16))
                        if (view4 != null && (textView4 = (TextView) view4.findViewById(R.id.infoIp)) != null) {
                            textView4.setText("IP: 未知")
                            }
                        MainActivity.this.updateSetupVisibility()
                        break
                        case 2:
                        button2 = MainActivity.this.connectBtn
                        if (button2 == null) {
                            Intrinsics.throwUninitializedPropertyAccessException("connectBtn")
                            button2 = null
                            }
                        button2.setEnabled(false)
                        val msg2: String = ConnectionManager.INSTANCE.getConnectionMessage().getValue()
                        textView8 = MainActivity.this.connectStatus
                        if (textView8 == null) {
                            Intrinsics.throwUninitializedPropertyAccessException("connectStatus")
                            textView8 = null
                            }
                        textView8.setText((StringsKt.startsWith$default(msg2, "检测到 ADB 通道", false, 2, (Object) null) || StringsKt.startsWith$default(msg2, "ADB 连接失败", false, 2, (Object) null)) ? msg2 : "连接中...")
                        textView9 = MainActivity.this.statusText
                        if (textView9 == null) {
                            Intrinsics.throwUninitializedPropertyAccessException("statusText")
                            textView9 = null
                            }
                        textView9.setText("连接中...")
                        textView10 = MainActivity.this.statusText
                        if (textView10 == null) {
                            Intrinsics.throwUninitializedPropertyAccessException("statusText")
                            } else {
                            textView19 = textView10
                            }
                        textView19.setTextColor(-18176)
                        hashMap6 = MainActivity.this.pageCache
                        hashMap6.remove(Boxing.boxInt(0))
                        hashMap7 = MainActivity.this.pageCache
                        val view5: View = (View) hashMap7.get(Boxing.boxInt(16))
                        if (view5 != null && (textView12 = (TextView) view5.findViewById(R.id.infoStatus)) != null) {
                            textView12.setText("状态: 连接中...")
                            }
                        hashMap8 = MainActivity.this.pageCache
                        val view6: View = (View) hashMap8.get(Boxing.boxInt(16))
                        if (view6 != null && (textView11 = (TextView) view6.findViewById(R.id.infoStatus)) != null) {
                            textView11.setTextColor(-18176)
                            break
                            }
                        break
                        case 3:
                        button3 = MainActivity.this.connectBtn
                        if (button3 == null) {
                            Intrinsics.throwUninitializedPropertyAccessException("connectBtn")
                            button3 = null
                            }
                        button3.setEnabled(true)
                        textView13 = MainActivity.this.connectStatus
                        if (textView13 == null) {
                            Intrinsics.throwUninitializedPropertyAccessException("connectStatus")
                            textView13 = null
                            }
                        textView13.setText("已连接")
                        textView14 = MainActivity.this.statusText
                        if (textView14 == null) {
                            Intrinsics.throwUninitializedPropertyAccessException("statusText")
                            textView14 = null
                            }
                        textView14.setText("已连接")
                        textView15 = MainActivity.this.statusText
                        if (textView15 == null) {
                            Intrinsics.throwUninitializedPropertyAccessException("statusText")
                            } else {
                            textView19 = textView15
                            }
                        textView19.setTextColor(-15696880)
                        hashMap9 = MainActivity.this.pageCache
                        hashMap9.remove(Boxing.boxInt(0))
                        hashMap10 = MainActivity.this.pageCache
                        val view7: View = (View) hashMap10.get(Boxing.boxInt(16))
                        if (view7 != null && (textView18 = (TextView) view7.findViewById(R.id.infoStatus)) != null) {
                            textView18.setText("状态: 已连接")
                            }
                        hashMap11 = MainActivity.this.pageCache
                        val view8: View = (View) hashMap11.get(Boxing.boxInt(16))
                        if (view8 != null && (textView17 = (TextView) view8.findViewById(R.id.infoStatus)) != null) {
                            textView17.setTextColor(-15696880)
                            }
                        hashMap12 = MainActivity.this.pageCache
                        val view9: View = (View) hashMap12.get(Boxing.boxInt(16))
                        if (view9 != null && (textView16 = (TextView) view9.findViewById(R.id.infoIp)) != null) {
                            val pcIp: String = ConnectionManager.INSTANCE.getPcIp()
                            if (pcIp == null) {
                                pcIp = "未知"
                                }
                            textView16.setText("IP: " + pcIp)
                            }
                        MainActivity.this.updateSetupVisibility()
                        list = MainActivity.this.urlHistory
                        if (list.isEmpty()) {
                            MainActivity.this.loadUrlHistory()
                            }
                        val connectionManager: ConnectionManager = ConnectionManager.INSTANCE
                        iterable = MainActivity.this.urlHistory
                        Iterable $this$map$iv = iterable
                        Collection destination$iv$iv = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv, 10))
                        for (Object item$iv$iv : $this$map$iv) {
                            MainActivity.UrlHistoryItem it = (MainActivity.UrlHistoryItem) item$iv$iv
                            destination$iv$iv.add(Triple(it.getUrl(), it.getDirection(), Boxing.boxLong(it.getTimestamp())))
                            }
                        connectionManager.sendUrlHistorySync((List) destination$iv$iv)
                        break
                        default:
                        throw NoWhenBranchMatchedException()
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
