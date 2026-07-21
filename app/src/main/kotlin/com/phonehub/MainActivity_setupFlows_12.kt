package com.phonehub

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow

class MainActivity_setupFlows_12(
    private val mainActivity: MainActivity,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return MainActivity_setupFlows_12(this.mainActivity, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as MainActivity_setupFlows_12).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val transferCancelledFromPc: SharedFlow<String> = ConnectionManager.INSTANCE.transferCancelledFromPc
                this.label = 1
                val collectResult = transferCancelledFromPc.collect(object : FlowCollector<String> {
                    override suspend fun emit(fileId: String) {
                        val hashMap = mainActivity.pageCache
                        val v: View? = hashMap[1] as? View
                        if (v == null) return
                        v.findViewById<TextView?>(R.id.fileNameText)?.text = "对端已取消"
                        v.findViewById<ProgressBar?>(R.id.fileProgress)?.progress = 0
                        v.findViewById<TextView?>(R.id.fileProgressText)?.text = ""
                        v.findViewById<TextView?>(R.id.fileSpeedText)?.text = ""
                        v.findViewById<Button?>(R.id.cancelFileBtn)?.isEnabled = false
                        v.findViewById<Button?>(R.id.pauseFileBtn)?.isEnabled = false
                        v.findViewById<Button?>(R.id.pauseFileBtn)?.text = "暂停"
                        v.findViewById<Button?>(R.id.selectFileBtn)?.isEnabled = true
                        v.findViewById<Button?>(R.id.doneFileBtn)?.visibility = 8
                        v.findViewById<Button?>(R.id.doneFileBtn)?.isEnabled = false
                        v.findViewById<LinearLayout?>(R.id.fileProgressContainer)?.visibility = 8
                        v.findViewById<LinearLayout?>(R.id.fileTransferBtnContainer)?.visibility = 8
                        Toast.makeText(mainActivity, "电脑端已取消文件传输", 0).show()
                    }
                }, this)
                if (collectResult == coroutine_suspended) {
                    return coroutine_suspended
                }
            }
            1 -> {
                ResultKt.throwOnFailure(result)
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
        return Unit
    }
}
