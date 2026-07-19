package com.phonehub

import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.phonehub.ConnectionManager
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

    public MainActivity$setupFlows$5(MainActivity mainActivity, Continuation<? super MainActivity$setupFlows$5> continuation) {
        super(2, continuation)
        this.this$0 = mainActivity
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new MainActivity$setupFlows$5(this.this$0, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((MainActivity$setupFlows$5) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            StateFlow<ConnectionManager.ChannelType> currentChannel = ConnectionManager.INSTANCE.getCurrentChannel()
            val mainActivity: MainActivity = this.this$0
            this.label = 1
            if (currentChannel.collect(FlowCollector() { // from class: com.phonehub.MainActivity$setupFlows$5.1

                public  class WhenMappings {
                    public static final  int[] $EnumSwitchMapping$0

                    static {
                        val iArr: Array<Int> = new int[ConnectionManager.ChannelType.values().length]
                        try {
                            iArr[ConnectionManager.ChannelType.WIFI.ordinal()] = 1
                            } catch (NoSuchFieldError e) {
                            }
                        try {
                            iArr[ConnectionManager.ChannelType.ADB.ordinal()] = 2
                            } catch (NoSuchFieldError e2) {
                            }
                        $EnumSwitchMapping$0 = iArr
                        }
                    }

                override
                public   Object emit(Object value, Continuation $completion) {
                    return emit((ConnectionManager.ChannelType) value, (Continuation<? super Unit>) $completion)
                    }

                fun emit(/* ConnectionManager.ChannelType channel */, continuation: Continuation<? super Unit>): Any {
                    var channelName: String? = null
                    var hashMap: HashMap? = null
                    var hashMap2: HashMap? = null
                    var textView: TextView? = null
                    var textView2: TextView? = null
                    switch (WhenMappings.$EnumSwitchMapping$0[channel.ordinal()]) {
                        case 1:
                        channelName = "WiFi 直连"
                        break
                        case 2:
                        channelName = "USB 数据线"
                        break
                        default:
                        channelName = "无"
                        break
                        }
                    hashMap = MainActivity.this.pageCache
                    val view: View = (View) hashMap.get(Boxing.boxInt(0))
                    if (view != null && (textView2 = (TextView) view.findViewById(R.id.channelHome)) != null) {
                        textView2.setText("通道: " + channelName)
                        }
                    hashMap2 = MainActivity.this.pageCache
                    val view2: View = (View) hashMap2.get(Boxing.boxInt(16))
                    if (view2 != null && (textView = (TextView) view2.findViewById(R.id.infoChannel)) != null) {
                        textView.setText("通道: " + channelName)
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
