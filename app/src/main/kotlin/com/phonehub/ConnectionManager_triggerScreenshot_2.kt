package com.phonehub

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
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
    final  Context $ctx
    var label: Int? = null

    public ConnectionManager$triggerScreenshot$2(Context context, Continuation<? super ConnectionManager$triggerScreenshot$2> continuation) {
        super(2, continuation)
        this.$ctx = context
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$triggerScreenshot$2(this.$ctx, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$triggerScreenshot$2) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun invokeSuspend(/* Object $result */): Any {
        var performBackgroundScreenshot: Any? = null
        var success: Boolean? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            this.label = 1
            if (DelayKt.delay(1000L, this) == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            this.label = 2
            performBackgroundScreenshot = ConnectionManager.INSTANCE.performBackgroundScreenshot(this)
            if (performBackgroundScreenshot != coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            $result = performBackgroundScreenshot
            success = ((Boolean) $result).booleanValue()
            if (!success) {
                ConnectionManager.INSTANCE.launchScreenshotActivity(this.$ctx)
                }
            return Unit.INSTANCE
            case 1:
            ResultKt.throwOnFailure($result)
            this.label = 2
            performBackgroundScreenshot = ConnectionManager.INSTANCE.performBackgroundScreenshot(this)
            if (performBackgroundScreenshot != coroutine_suspended) {
                }
            break
            case 2:
            ResultKt.throwOnFailure($result)
            success = ((Boolean) $result).booleanValue()
            if (!success) {
                }
            return Unit.INSTANCE
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }
    }
