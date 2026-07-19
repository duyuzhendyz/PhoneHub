package com.phonehub

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.camera.video.AudioStats
import androidx.constraintlayout.widget.ConstraintLayout
import com.phonehub.ConnectionManager
import java.util.Arrays
import java.util.HashMap
import kotlin.KotlinNothingValueException
import kotlin.Pair
import kotlin.ResultKt
import kotlin.TuplesKt
import kotlin.Unit
import kotlin.collections.ArrayDeque
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.Boxing
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlin.ranges.RangesKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import okhttp3.internal.ws.RealWebSocket

class MainActivity {
    var label: Int? = null
    final  MainActivity this$0

    public MainActivity$setupFlows$10(MainActivity mainActivity, Continuation<? super MainActivity$setupFlows$10> continuation) {
        super(2, continuation)
        this.this$0 = mainActivity
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new MainActivity$setupFlows$10(this.this$0, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((MainActivity$setupFlows$10) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            val speedSamples: ArrayDeque = new ArrayDeque()
            final Ref.ObjectRef lastTransferDir = new Ref.ObjectRef()
            lastTransferDir.element = ""
            StateFlow<ConnectionManager.TransferProgress> fileTransferProgress = ConnectionManager.INSTANCE.getFileTransferProgress()
            val mainActivity: MainActivity = this.this$0
            this.label = 1
            if (fileTransferProgress.collect(FlowCollector() { // from class: com.phonehub.MainActivity$setupFlows$10.1
                override
                public   Object emit(Object value, Continuation $completion) {
                    return emit((ConnectionManager.TransferProgress) value, (Continuation<? super Unit>) $completion)
                    }

                fun emit(/* ConnectionManager.TransferProgress progress */, continuation: Continuation<? super Unit>): Any {
                    var hashMap: HashMap? = null
                    var z: Boolean? = null
                    var z2: Boolean? = null
                    var format: String? = null
                    hashMap = MainActivity.this.pageCache
                    val v: View = (View) hashMap.get(Boxing.boxInt(1))
                    if (v == null) {
                        return Unit.INSTANCE
                        }
                    val str: String = ""
                    if (progress != null) {
                        val linearLayout: LinearLayout = (LinearLayout) v.findViewById(R.id.fileProgressContainer)
                        if (linearLayout != null) {
                            linearLayout.setVisibility(0)
                            }
                        val linearLayout2: LinearLayout = (LinearLayout) v.findViewById(R.id.fileTransferBtnContainer)
                        if (linearLayout2 != null) {
                            linearLayout2.setVisibility(0)
                            }
                        val textView: TextView = (TextView) v.findViewById(R.id.fileNameText)
                        if (textView != null) {
                            textView.setText(progress.getFileName())
                            }
                        val pct: Int = progress.getTotal() > 0 ? (int) ((progress.getSent() * 100) / progress.getTotal()) : 0
                        val progressBar: ProgressBar = (ProgressBar) v.findViewById(R.id.fileProgress)
                        if (progressBar != null) {
                            progressBar.setProgress(pct)
                            }
                        val textView2: TextView = (TextView) v.findViewById(R.id.fileProgressText)
                        if (textView2 != null) {
                            textView2.setText(emit$fmtSize(progress.getSent()) + " / " + emit$fmtSize(progress.getTotal()))
                            }
                        val button: Button = (Button) v.findViewById(R.id.cancelFileBtn)
                        if (button != null) {
                            button.setEnabled(true)
                            }
                        val button2: Button = (Button) v.findViewById(R.id.pauseFileBtn)
                        if (button2 != null) {
                            button2.setEnabled(true)
                            }
                        val button3: Button = (Button) v.findViewById(R.id.pauseFileBtn)
                        if (button3 != null) {
                            button3.setText("暂停")
                            }
                        val button4: Button = (Button) v.findViewById(R.id.selectFileBtn)
                        if (button4 != null) {
                            button4.setEnabled(false)
                            }
                        val button5: Button = (Button) v.findViewById(R.id.doneFileBtn)
                        if (button5 != null) {
                            button5.setVisibility(8)
                            }
                        val button6: Button = (Button) v.findViewById(R.id.doneFileBtn)
                        if (button6 != null) {
                            button6.setEnabled(false)
                            }
                        if (progress.getReceiving()) {
                            lastTransferDir.element = "接收"
                            } else {
                            lastTransferDir.element = "发送"
                            }
                        if (progress.getSent() > 0) {
                            val now: Long = System.currentTimeMillis()
                            speedSamples.addLast(TuplesKt.to(Boxing.boxLong(now), Boxing.boxLong(progress.getSent())))
                            while ((!speedSamples.isEmpty()) && now - speedSamples.first().getFirst().longValue() > 4000) {
                                speedSamples.removeFirst()
                                }
                            if (speedSamples.size() >= 2) {
                                val first: Pair = speedSamples.first()
                                val last: Pair = speedSamples.last()
                                val dt: Long = RangesKt.coerceAtLeast(last.getFirst().longValue() - first.getFirst().longValue(), 100L)
                                val speedBps: Double = ((last.getSecond().longValue() - first.getSecond().longValue()) * 1000) / dt
                                if (ConnectionManager.INSTANCE.getTransferPausedFromPc().getValue().booleanValue()) {
                                    val textView3: TextView = (TextView) v.findViewById(R.id.fileSpeedText)
                                    if (textView3 != null) {
                                        textView3.setText("已暂停")
                                        }
                                    } else if (speedBps > AudioStats.AUDIO_AMPLITUDE_NONE) {
                                    if (speedBps >= 1048576.0d) {
                                        format = String.format("%.2f MB/s", Arrays.copyOf(new Object[]{Boxing.boxDouble(speedBps / 1048576.0d)}, 1))
                                        Intrinsics.checkNotNullExpressionValue(format, "format(...)")
                                        } else {
                                        format = String.format("%.0f KB/s", Arrays.copyOf(new Object[]{Boxing.boxDouble(speedBps / 1024.0d)}, 1))
                                        Intrinsics.checkNotNullExpressionValue(format, "format(...)")
                                        }
                                    val speedText: String = format
                                    val remaining: Long = progress.getTotal() - progress.getSent()
                                    val etaSec: Double = speedBps > AudioStats.AUDIO_AMPLITUDE_NONE ? remaining / speedBps : 0.0d
                                    if (etaSec > AudioStats.AUDIO_AMPLITUDE_NONE && remaining > 0) {
                                        val sec: Int = (int) etaSec
                                        if (sec < 60) {
                                            str = sec + "秒"
                                            } else {
                                            str = sec < 3600 ? (sec / 60) + "分" + (sec % 60) + "秒" : (sec / 3600) + "时" + ((sec % 3600) / 60) + "分"
                                            }
                                        }
                                    val etaStr: String = str
                                    val textView4: TextView = (TextView) v.findViewById(R.id.fileSpeedText)
                                    if (textView4 != null) {
                                        textView4.setText(etaStr.length() > 0 ? speedText + " · 剩余 " + etaStr : speedText)
                                        }
                                    }
                                }
                            }
                        } else {
                        speedSamples.clear()
                        if (ConnectionManager.INSTANCE.getTransferCompleted().getValue().booleanValue()) {
                            val progressBar2: ProgressBar = (ProgressBar) v.findViewById(R.id.fileProgress)
                            if (progressBar2 != null) {
                                progressBar2.setProgress(100)
                                }
                            val button7: Button = (Button) v.findViewById(R.id.cancelFileBtn)
                            if (button7 != null) {
                                z2 = false
                                button7.setEnabled(false)
                                } else {
                                z2 = false
                                }
                            val button8: Button = (Button) v.findViewById(R.id.pauseFileBtn)
                            if (button8 != null) {
                                button8.setEnabled(z2)
                                }
                            val button9: Button = (Button) v.findViewById(R.id.pauseFileBtn)
                            if (button9 != null) {
                                button9.setText("暂停")
                                }
                            val button10: Button = (Button) v.findViewById(R.id.selectFileBtn)
                            if (button10 != null) {
                                button10.setEnabled(true)
                                }
                            val button11: Button = (Button) v.findViewById(R.id.doneFileBtn)
                            if (button11 != null) {
                                button11.setVisibility(0)
                                }
                            val button12: Button = (Button) v.findViewById(R.id.doneFileBtn)
                            if (button12 != null) {
                                button12.setEnabled(true)
                                }
                            val nameView: TextView = (TextView) v.findViewById(R.id.fileNameText)
                            if (nameView != null) {
                                val str2: String = lastTransferDir.element
                                val text: String = nameView.getText()
                                if (text == null) {
                                    }
                                nameView.setText(((Object) str2) + "完成: " + ((Object) text))
                                }
                            } else {
                            val progressBar3: ProgressBar = (ProgressBar) v.findViewById(R.id.fileProgress)
                            if (progressBar3 != null) {
                                progressBar3.setProgress(0)
                                }
                            val textView5: TextView = (TextView) v.findViewById(R.id.fileProgressText)
                            if (textView5 != null) {
                                textView5.setText("")
                                }
                            val textView6: TextView = (TextView) v.findViewById(R.id.fileSpeedText)
                            if (textView6 != null) {
                                textView6.setText("")
                                }
                            val button13: Button = (Button) v.findViewById(R.id.cancelFileBtn)
                            if (button13 != null) {
                                z = false
                                button13.setEnabled(false)
                                } else {
                                z = false
                                }
                            val button14: Button = (Button) v.findViewById(R.id.pauseFileBtn)
                            if (button14 != null) {
                                button14.setEnabled(z)
                                }
                            val button15: Button = (Button) v.findViewById(R.id.pauseFileBtn)
                            if (button15 != null) {
                                button15.setText("暂停")
                                }
                            val button16: Button = (Button) v.findViewById(R.id.selectFileBtn)
                            if (button16 != null) {
                                button16.setEnabled(true)
                                }
                            val button17: Button = (Button) v.findViewById(R.id.doneFileBtn)
                            if (button17 != null) {
                                button17.setVisibility(8)
                                }
                            val button18: Button = (Button) v.findViewById(R.id.doneFileBtn)
                            if (button18 != null) {
                                button18.setEnabled(false)
                                }
                            val linearLayout3: LinearLayout = (LinearLayout) v.findViewById(R.id.fileProgressContainer)
                            if (linearLayout3 != null) {
                                linearLayout3.setVisibility(8)
                                }
                            val linearLayout4: LinearLayout = (LinearLayout) v.findViewById(R.id.fileTransferBtnContainer)
                            if (linearLayout4 != null) {
                                linearLayout4.setVisibility(8)
                                }
                            }
                        }
                    return Unit.INSTANCE
                    }

                private static final String emit$fmtSize(long b) {
                    if (b >= 1073741824) {
                        val format: String = String.format("%.2f GB", Arrays.copyOf(new Object[]{Double.valueOf(b / 1.073741824E9d)}, 1))
                        Intrinsics.checkNotNullExpressionValue(format, "format(...)")
                        var format: return? = null
                        }
                    if (b >= 1048576) {
                        val format2: String = String.format("%.1f MB", Arrays.copyOf(new Object[]{Double.valueOf(b / 1048576.0d)}, 1))
                        Intrinsics.checkNotNullExpressionValue(format2, "format(...)")
                        var format2: return? = null
                        }
                    if (b >= RealWebSocket.DEFAULT_MINIMUM_DEFLATE_SIZE) {
                        val format3: String = String.format("%.0f KB", Arrays.copyOf(new Object[]{Double.valueOf(b / 1024.0d)}, 1))
                        Intrinsics.checkNotNullExpressionValue(format3, "format(...)")
                        var format3: return? = null
                        }
                    return b + " B"
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
