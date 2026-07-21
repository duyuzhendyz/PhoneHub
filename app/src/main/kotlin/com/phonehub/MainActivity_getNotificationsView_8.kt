package com.phonehub

import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import com.phonehub.ConnectionManager
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.internal.Ref
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow

class MainActivity_getNotificationsView_8(
    private val mainActivity: MainActivity,
    private val filterEdit: EditText,
    private val currentTab: Ref.ObjectRef<String>,
    private val activeList: ListView,
    private val historyList: ListView,
    private val empty: TextView,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return MainActivity_getNotificationsView_8(this.mainActivity, this.filterEdit, this.currentTab, this.activeList, this.historyList, this.empty, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as MainActivity_getNotificationsView_8).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val notifications: SharedFlow<ConnectionManager.NotificationItem> = ConnectionManager.INSTANCE.notifications
                val mainActivity = this.mainActivity
                val editText = this.filterEdit
                val objectRef = this.currentTab
                val listView = this.activeList
                val listView2 = this.historyList
                val textView = this.empty
                this.label = 1
                val collect = notifications.collect(object : FlowCollector<ConnectionManager.NotificationItem> {
                    override suspend fun emit(value: ConnectionManager.NotificationItem) {
                        val item = value
                        val key = mainActivity.notifKey(item)
                        val indexOfFirstIv = mainActivity.activeNotifItems
                        val mainActivity2 = mainActivity
                        var indexIv = 0
                        val it = indexOfFirstIv.iterator()
                        while (true) {
                            if (it.hasNext()) {
                                val itemIv = it.next()
                                val it2 = itemIv as ConnectionManager.NotificationItem
                                val notifKey2 = mainActivity2.notifKey(it2)
                                if (notifKey2 == key) {
                                    break
                                }
                                indexIv++
                            } else {
                                indexIv = -1
                                break
                            }
                        }
                        val existingIdx: Int = indexIv
                        if (existingIdx >= 0) {
                            val list6 = mainActivity.activeNotifItems
                            list6[existingIdx] = item
                        } else {
                            val list = mainActivity.activeNotifItems
                            list.add(0, item)
                        }
                        val iterable = mainActivity.notifHistoryItems
                        val noneIv2: Iterable<*> = iterable
                        val mainActivity3 = mainActivity
                        var noneIv: Any? = null
                        if (noneIv2 !is Collection || !(noneIv2 as Collection).isEmpty()) {
                            val it3 = noneIv2.iterator()
                            while (true) {
                                if (it3.hasNext()) {
                                    val elementIv = it3.next()
                                    val it4 = elementIv as ConnectionManager.NotificationItem
                                    val notifKey = mainActivity3.notifKey(it4)
                                    if (notifKey == key) {
                                        noneIv = null
                                        break
                                    }
                                } else {
                                    noneIv = 1
                                    break
                                }
                            }
                        } else {
                            noneIv = 1
                        }
                        if (noneIv != null) {
                            val list2 = mainActivity.notifHistoryItems
                            list2.add(0, item)
                            val list3 = mainActivity.notifHistoryItems
                            if (list3.size > 200) {
                                val list4 = mainActivity.notifHistoryItems
                                val list5 = mainActivity.notifHistoryItems
                                list4.removeAt(list5.size - 1)
                            }
                        }
                        MainActivity.`getNotificationsView$refresh$143`(editText, objectRef, mainActivity, listView, listView2, textView)
                    }
                }, this)
                if (collect == coroutine_suspended) {
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
