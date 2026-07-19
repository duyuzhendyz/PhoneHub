package com.phonehub

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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
import kotlinx.coroutines.flow.SharedFlow

class MainActivity {
    var label: Int? = null
    final  MainActivity this$0

    public MainActivity$setupFlows$12(MainActivity mainActivity, Continuation<? super MainActivity$setupFlows$12> continuation) {
        super(2, continuation)
        this.this$0 = mainActivity
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new MainActivity$setupFlows$12(this.this$0, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((MainActivity$setupFlows$12) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            val transferCancelledFromPc: SharedFlow<String> = ConnectionManager.INSTANCE.getTransferCancelledFromPc()
            val mainActivity: MainActivity = this.this$0
            this.label = 1
            if (transferCancelledFromPc.collect(FlowCollector() { // from class: com.phonehub.MainActivity$setupFlows$12.1
                override
                public   Object emit(Object value, Continuation $completion) {
                    return emit(value, (Continuation<? super Unit>) $completion)
                    }

                fun emit(fileId: String, continuation: Continuation<? super Unit>): Any {
                    var hashMap: HashMap? = null
                    hashMap = MainActivity.this.pageCache
                    val v: View = (View) hashMap.get(Boxing.boxInt(1))
                    if (v == null) {
                        return Unit.INSTANCE
                        }
                    val textView: TextView = (TextView) v.findViewById(R.id.fileNameText)
                    if (textView != null) {
                        textView.setText("对端已取消")
                        }
                    val progressBar: ProgressBar = (ProgressBar) v.findViewById(R.id.fileProgress)
                    if (progressBar != null) {
                        progressBar.setProgress(0)
                        }
                    val textView2: TextView = (TextView) v.findViewById(R.id.fileProgressText)
                    if (textView2 != null) {
                        textView2.setText("")
                        }
                    val textView3: TextView = (TextView) v.findViewById(R.id.fileSpeedText)
                    if (textView3 != null) {
                        textView3.setText("")
                        }
                    val button: Button = (Button) v.findViewById(R.id.cancelFileBtn)
                    if (button != null) {
                        button.setEnabled(false)
                        }
                    val button2: Button = (Button) v.findViewById(R.id.pauseFileBtn)
                    if (button2 != null) {
                        button2.setEnabled(false)
                        }
                    val button3: Button = (Button) v.findViewById(R.id.pauseFileBtn)
                    if (button3 != null) {
                        button3.setText("暂停")
                        }
                    val button4: Button = (Button) v.findViewById(R.id.selectFileBtn)
                    if (button4 != null) {
                        button4.setEnabled(true)
                        }
                    val button5: Button = (Button) v.findViewById(R.id.doneFileBtn)
                    if (button5 != null) {
                        button5.setVisibility(8)
                        }
                    val button6: Button = (Button) v.findViewById(R.id.doneFileBtn)
                    if (button6 != null) {
                        button6.setEnabled(false)
                        }
                    val linearLayout: LinearLayout = (LinearLayout) v.findViewById(R.id.fileProgressContainer)
                    if (linearLayout != null) {
                        linearLayout.setVisibility(8)
                        }
                    val linearLayout2: LinearLayout = (LinearLayout) v.findViewById(R.id.fileTransferBtnContainer)
                    if (linearLayout2 != null) {
                        linearLayout2.setVisibility(8)
                        }
                    Toast.makeText(MainActivity.this, "电脑端已取消文件传输", 0).show()
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
