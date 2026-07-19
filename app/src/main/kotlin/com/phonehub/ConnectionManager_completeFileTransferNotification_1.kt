package com.phonehub

import android.app.NotificationManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.CoroutineLiveDataKt
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelayKt

class ConnectionManager {
    final  NotificationManager $mgr
    var label: Int? = null

    public ConnectionManager$completeFileTransferNotification$1(NotificationManager notificationManager, Continuation<? super ConnectionManager$completeFileTransferNotification$1> continuation) {
        super(2, continuation)
        this.$mgr = notificationManager
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$completeFileTransferNotification$1(this.$mgr, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$completeFileTransferNotification$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            this.label = 1
            if (DelayKt.delay(CoroutineLiveDataKt.DEFAULT_TIMEOUT, this) == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            break
            case 1:
            ResultKt.throwOnFailure($result)
            break
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        try {
            this.$mgr.cancel(88881)
            } catch (Exception e) {
            }
        return Unit.INSTANCE
        }
    }
