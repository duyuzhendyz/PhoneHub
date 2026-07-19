package com.phonehub

import androidx.constraintlayout.widget.ConstraintLayout
import com.phonehub.MainActivity
import java.util.Collection
import java.util.Comparator
import java.util.Iterator
import java.util.List
import java.util.Map
import kotlin.KotlinNothingValueException
import kotlin.ResultKt
import kotlin.Unit
import kotlin.collections.CollectionsKt
import kotlin.comparisons.ComparisonsKt
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow

class MainActivity {
    var label: Int? = null
    final  MainActivity this$0

    public MainActivity$getPushWebView$4(MainActivity mainActivity, Continuation<? super MainActivity$getPushWebView$4> continuation) {
        super(2, continuation)
        this.this$0 = mainActivity
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new MainActivity$getPushWebView$4(this.this$0, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((MainActivity$getPushWebView$4) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            val urlHistorySync: SharedFlow<List<Map<String, Object>>> = ConnectionManager.INSTANCE.getUrlHistorySync()
            val mainActivity: MainActivity = this.this$0
            this.label = 1
            if (urlHistorySync.collect(FlowCollector() { // from class: com.phonehub.MainActivity$getPushWebView$4.1
                override
                public   Object emit(Object value, Continuation $completion) {
                    return emit((List<? extends Map<String, ? extends Object>>) value, (Continuation<? super Unit>) $completion)
                    }

                fun emit(list: List<? extends Map<String, ? extends Object>>, continuation: Continuation<? super Unit>): Any {
                    List $this$sortByDescending$iv
                    var list2: List? = null
                    var list3: List? = null
                    var list4: List? = null
                    var iterable: Iterable? = null
                    var list5: List? = null
                    val changed: Boolean = false
                    val it: Iterator<? extends Map<String, ? extends Object>> = list.iterator()
                    while (true) {
                        val exists: Boolean = true
                        if (!it.hasNext()) {
                            break
                            }
                        val item: Map = it.next()
                        val obj: Any = item.get("url")
                        val url: String = obj instanceof String ? (String) obj : null
                        if (url == null) {
                            url = ""
                            }
                        val obj2: Any = item.get("direction")
                        val str: String = obj2 instanceof String ? (String) obj2 : null
                        val direction: String = str != null ? str : ""
                        val obj3: Any = item.get("timestamp")
                        val l: Long = obj3 instanceof Long ? (Long) obj3 : null
                        val timestamp: Long = l != null ? l.longValue() : 0L
                        if (!(url.length() == 0)) {
                            iterable = MainActivity.this.urlHistory
                            Iterable $this$any$iv = iterable
                            if (!($this$any$iv is Collection) || !((Collection) $this$any$iv).isEmpty()) {
                                val it2: Iterator<T> = $this$any$iv.iterator()
                                while (true) {
                                    if (it2.hasNext()) {
                                        Object element$iv = it2.next()
                                        MainActivity.UrlHistoryItem it3 = (MainActivity.UrlHistoryItem) element$iv
                                        if (Intrinsics.areEqual(it3.getUrl(), url) && Intrinsics.areEqual(it3.getDirection(), direction)) {
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
                                list5 = MainActivity.this.urlHistory
                                list5.add(new MainActivity.UrlHistoryItem(url, direction, timestamp))
                                changed = true
                                }
                            }
                        }
                    if (changed) {
                        $this$sortByDescending$iv = MainActivity.this.urlHistory
                        if ($this$sortByDescending$iv.size() > 1) {
                            CollectionsKt.sortWith($this$sortByDescending$iv, Comparator() { // from class: com.phonehub.MainActivity$getPushWebView$4$1$emit$$inlined$sortByDescending$1
                                override
                                fun compare(t: T, t2: T): Int {
                                    MainActivity.UrlHistoryItem it4 = (MainActivity.UrlHistoryItem) t2
                                    MainActivity.UrlHistoryItem it5 = (MainActivity.UrlHistoryItem) t
                                    return ComparisonsKt.compareValues(Long.valueOf(it4.getTimestamp()), Long.valueOf(it5.getTimestamp()))
                                    }
                                })
                            }
                        list2 = MainActivity.this.urlHistory
                        if (list2.size() > 50) {
                            list3 = MainActivity.this.urlHistory
                            list4 = MainActivity.this.urlHistory
                            list3.subList(50, list4.size()).clear()
                            }
                        MainActivity.this.saveUrlHistory()
                        MainActivity.this.refreshUrlHistoryList()
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
