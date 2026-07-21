package com.phonehub

import com.phonehub.MainActivity
import java.util.Comparator
import kotlin.ResultKt
import kotlin.Unit
import kotlin.collections.CollectionsKt
import kotlin.comparisons.ComparisonsKt
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow

class MainActivity_getPushWebView_4(
    private val mainActivity: MainActivity,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return MainActivity_getPushWebView_4(this.mainActivity, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as MainActivity_getPushWebView_4).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (this.label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val urlHistorySync: SharedFlow<List<Map<String, Any?>>> = ConnectionManager.INSTANCE.urlHistorySync
                val mainActivity = this.mainActivity
                this.label = 1
                val collect = urlHistorySync.collect(object : FlowCollector<List<Map<String, Any?>>> {
                    override suspend fun emit(value: List<Map<String, Any?>>) {
                        val list = value
                        var changed = false
                        val it = list.iterator()
                        while (true) {
                            var exists = true
                            if (!it.hasNext()) {
                                break
                            }
                            val item = it.next() as Map<String, Any?>
                            val obj = item["url"]
                            val url: String = (obj as? String) ?: ""
                            val obj2 = item["direction"]
                            val direction: String = (obj2 as? String) ?: ""
                            val obj3 = item["timestamp"]
                            val l = obj3 as? Long
                            val timestamp: Long = l ?: 0L
                            if (url.isNotEmpty()) {
                                val iterable = mainActivity.urlHistory
                                val anyIv: Iterable<*> = iterable
                                if (anyIv !is Collection || !(anyIv as Collection).isEmpty()) {
                                    val it2 = anyIv.iterator()
                                    while (true) {
                                        if (it2.hasNext()) {
                                            val elementIv = it2.next()
                                            val it3 = elementIv as MainActivity.UrlHistoryItem
                                            if (it3.url == url && it3.direction == direction) {
                                                break
                                            }
                                        } else {
                                            exists = false
                                            break
                                        }
                                    }
                                } else {
                                    exists = false
                                }
                                if (!exists) {
                                    val list5 = mainActivity.urlHistory
                                    list5.add(MainActivity.UrlHistoryItem(url, direction, timestamp))
                                    changed = true
                                }
                            }
                        }
                        if (changed) {
                            val sortByDescendingIv = mainActivity.urlHistory
                            if (sortByDescendingIv.size > 1) {
                                CollectionsKt.sortWith(sortByDescendingIv, Comparator { t, t2 ->
                                    val it4 = t2 as MainActivity.UrlHistoryItem
                                    val it5 = t as MainActivity.UrlHistoryItem
                                    ComparisonsKt.compareValues(it4.timestamp, it5.timestamp)
                                })
                            }
                            if (sortByDescendingIv.size > 50) {
                                val list3 = mainActivity.urlHistory
                                val list4 = mainActivity.urlHistory
                                list3.subList(50, list4.size).clear()
                            }
                            mainActivity.saveUrlHistory()
                            mainActivity.refreshUrlHistoryList()
                        }
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
