package com.phonehub

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.phonehub.ConnectionManager
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

class MainActivity_setupFlows_10(
    private val `this$0`: MainActivity,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return MainActivity_setupFlows_10(this.`this$0`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as MainActivity_setupFlows_10).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val speedSamples = ArrayDeque<Pair<Long, Long>>()
                var lastTransferDir = ""
                val fileTransferProgress: StateFlow<ConnectionManager.TransferProgress?> =
                    ConnectionManager.INSTANCE.fileTransferProgress
                val mainActivity: MainActivity = this.`this$0`
                label = 1
                val collectResult = fileTransferProgress.collect(object : FlowCollector<ConnectionManager.TransferProgress?> {
                    override suspend fun emit(progress: ConnectionManager.TransferProgress?) {
                        val hashMap = mainActivity.pageCache
                        val v: View? = hashMap[1]
                        if (v == null) return
                        var str = ""
                        if (progress != null) {
                            v.findViewById<LinearLayout?>(R.id.fileProgressContainer)?.visibility = 0
                            v.findViewById<LinearLayout?>(R.id.fileTransferBtnContainer)?.visibility = 0
                            v.findViewById<TextView?>(R.id.fileNameText)?.text = progress.fileName
                            val pct: Int = if (progress.total > 0) ((progress.sent * 100) / progress.total).toInt() else 0
                            v.findViewById<ProgressBar?>(R.id.fileProgress)?.progress = pct
                            v.findViewById<TextView?>(R.id.fileProgressText)?.text = fmtSize(progress.sent) + " / " + fmtSize(progress.total)
                            v.findViewById<Button?>(R.id.cancelFileBtn)?.isEnabled = true
                            v.findViewById<Button?>(R.id.pauseFileBtn)?.isEnabled = true
                            v.findViewById<Button?>(R.id.pauseFileBtn)?.text = "暂停"
                            v.findViewById<Button?>(R.id.selectFileBtn)?.isEnabled = false
                            v.findViewById<Button?>(R.id.doneFileBtn)?.visibility = 8
                            v.findViewById<Button?>(R.id.doneFileBtn)?.isEnabled = false
                            lastTransferDir = if (progress.receiving) "接收" else "发送"
                            if (progress.sent > 0) {
                                val now = System.currentTimeMillis()
                                speedSamples.addLast(Pair(now, progress.sent))
                                while (!speedSamples.isEmpty() && now - speedSamples.first().first > 4000) {
                                    speedSamples.removeFirst()
                                }
                                if (speedSamples.size >= 2) {
                                    val first = speedSamples.first()
                                    val last = speedSamples.last()
                                    val dt = if (last.first - first.first >= 100L) last.first - first.first else 100L
                                    val speedBps = ((last.second - first.second) * 1000).toDouble() / dt
                                    if (ConnectionManager.INSTANCE.transferPausedFromPc.value) {
                                        v.findViewById<TextView?>(R.id.fileSpeedText)?.text = "已暂停"
                                    } else if (speedBps > 0.0) {
                                        val speedText = if (speedBps >= 1048576.0) {
                                            "%.2f MB/s".format(speedBps / 1048576.0)
                                        } else {
                                            "%.0f KB/s".format(speedBps / 1024.0)
                                        }
                                        val remaining = progress.total - progress.sent
                                        val etaSec = if (speedBps > 0.0) remaining / speedBps else 0.0
                                        if (etaSec > 0.0 && remaining > 0) {
                                            val sec = etaSec.toInt()
                                            str = if (sec < 60) {
                                                "$sec 秒"
                                            } else if (sec < 3600) {
                                                "${sec / 60}分${sec % 60}秒"
                                            } else {
                                                "${sec / 3600}时${(sec % 3600) / 60}分"
                                            }
                                        }
                                        val etaStr = str
                                        v.findViewById<TextView?>(R.id.fileSpeedText)?.text = if (etaStr.isNotEmpty()) "$speedText · 剩余 $etaStr" else speedText
                                    }
                                }
                            }
                        } else {
                            speedSamples.clear()
                            if (ConnectionManager.INSTANCE.transferCompleted.value) {
                                v.findViewById<ProgressBar?>(R.id.fileProgress)?.progress = 100
                                v.findViewById<Button?>(R.id.cancelFileBtn)?.isEnabled = false
                                v.findViewById<Button?>(R.id.pauseFileBtn)?.isEnabled = false
                                v.findViewById<Button?>(R.id.pauseFileBtn)?.text = "暂停"
                                v.findViewById<Button?>(R.id.selectFileBtn)?.isEnabled = true
                                v.findViewById<Button?>(R.id.doneFileBtn)?.visibility = 0
                                v.findViewById<Button?>(R.id.doneFileBtn)?.isEnabled = true
                                val nameView = v.findViewById<TextView?>(R.id.fileNameText)
                                if (nameView != null) {
                                    val text = nameView.text?.toString() ?: ""
                                    nameView.text = "$lastTransferDir 完成: $text"
                                }
                            } else {
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
                            }
                        }
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

    companion object {
        private fun fmtSize(b: Long): String {
            if (b >= 1073741824L) {
                return "%.2f GB".format(b / 1.073741824E9)
            }
            if (b >= 1048576L) {
                return "%.1f MB".format(b / 1048576.0)
            }
            if (b >= 1024L) {
                return "%.0f KB".format(b / 1024.0)
            }
            return "$b B"
        }
    }
}
