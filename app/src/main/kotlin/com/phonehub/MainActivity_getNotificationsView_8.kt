package com.phonehub

import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.phonehub.ConnectionManager
import java.util.Collection
import java.util.Iterator
import java.util.List
import kotlin.KotlinNothingValueException
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow

class MainActivity {
    final  ListView $activeList
    final  Ref.ObjectRef<String> $currentTab
    final  TextView $empty
    final  EditText $filterEdit
    final  ListView $historyList
    var label: Int? = null
    final  MainActivity this$0

    public MainActivity$getNotificationsView$8(MainActivity mainActivity, EditText editText, Ref.ObjectRef<String> objectRef, ListView listView, ListView listView2, TextView textView, Continuation<? super MainActivity$getNotificationsView$8> continuation) {
        super(2, continuation)
        this.this$0 = mainActivity
        this.$filterEdit = editText
        this.$currentTab = objectRef
        this.$activeList = listView
        this.$historyList = listView2
        this.$empty = textView
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new MainActivity$getNotificationsView$8(this.this$0, this.$filterEdit, this.$currentTab, this.$activeList, this.$historyList, this.$empty, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((MainActivity$getNotificationsView$8) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            SharedFlow<ConnectionManager.NotificationItem> notifications = ConnectionManager.INSTANCE.getNotifications()
            val mainActivity: MainActivity = this.this$0
            val editText: EditText = this.$filterEdit
            final Ref.ObjectRef<String> objectRef = this.$currentTab
            val listView: ListView = this.$activeList
            val listView2: ListView = this.$historyList
            val textView: TextView = this.$empty
            this.label = 1
            if (notifications.collect(FlowCollector() { // from class: com.phonehub.MainActivity$getNotificationsView$8.1
                override
                public   Object emit(Object value, Continuation $completion) {
                    return emit((ConnectionManager.NotificationItem) value, (Continuation<? super Unit>) $completion)
                    }

                fun emit(/* ConnectionManager.NotificationItem item */, continuation: Continuation<? super Unit>): Any {
                    var key: String? = null
                    List $this$indexOfFirst$iv
                    var list: List? = null
                    var iterable: Iterable? = null
                    Iterable $this$none$iv
                    var notifKey: String? = null
                    var list2: List? = null
                    var list3: List? = null
                    var list4: List? = null
                    var list5: List? = null
                    var list6: List? = null
                    var notifKey2: String? = null
                    key = MainActivity.this.notifKey(item)
                    $this$indexOfFirst$iv = MainActivity.this.activeNotifItems
                    val mainActivity2: MainActivity = MainActivity.this
                    int index$iv = 0
                    val it: Iterator = $this$indexOfFirst$iv.iterator()
                    while (true) {
                        if (it.hasNext()) {
                            Object item$iv = it.next()
                            ConnectionManager.NotificationItem it2 = (ConnectionManager.NotificationItem) item$iv
                            notifKey2 = mainActivity2.notifKey(it2)
                            if (Intrinsics.areEqual(notifKey2, key)) {
                                break
                                }
                            index$iv++
                            } else {
                            index$iv = -1
                            break
                            }
                        }
                    val existingIdx: Int = index$iv
                    if (existingIdx >= 0) {
                        list6 = MainActivity.this.activeNotifItems
                        list6.set(existingIdx, item)
                        } else {
                        list = MainActivity.this.activeNotifItems
                        list.add(0, item)
                        }
                    iterable = MainActivity.this.notifHistoryItems
                    Iterable $this$none$iv2 = iterable
                    val mainActivity3: MainActivity = MainActivity.this
                    if (!($this$none$iv2 is Collection) || !((Collection) $this$none$iv2).isEmpty()) {
                        val it3: Iterator<T> = $this$none$iv2.iterator()
                        while (true) {
                            if (it3.hasNext()) {
                                Object element$iv = it3.next()
                                ConnectionManager.NotificationItem it4 = (ConnectionManager.NotificationItem) element$iv
                                notifKey = mainActivity3.notifKey(it4)
                                if (Intrinsics.areEqual(notifKey, key)) {
                                    $this$none$iv = null
                                    break
                                    }
                                } else {
                                $this$none$iv = 1
                                break
                                }
                            }
                        } else {
                        $this$none$iv = 1
                        }
                    if ($this$none$iv != null) {
                        list2 = MainActivity.this.notifHistoryItems
                        list2.add(0, item)
                        list3 = MainActivity.this.notifHistoryItems
                        if (list3.size() > 200) {
                            list4 = MainActivity.this.notifHistoryItems
                            list5 = MainActivity.this.notifHistoryItems
                            list4.remove(list5.size() - 1)
                            }
                        }
                    MainActivity.getNotificationsView$refresh$143(editText, objectRef, MainActivity.this, listView, listView2, textView)
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
