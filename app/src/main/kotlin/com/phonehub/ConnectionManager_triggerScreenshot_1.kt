package com.phonehub

import android.content.Context
import android.util.Log
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

    public ConnectionManager$triggerScreenshot$1(Context context, Continuation<? super ConnectionManager$triggerScreenshot$1> continuation) {
        super(2, continuation)
        this.$ctx = context
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$triggerScreenshot$1(this.$ctx, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$triggerScreenshot$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun invokeSuspend(/* Object $result */): Any {
        var performBackgroundScreenshot: Any? = null
        Object $result2
        var success: Boolean? = null
        var performBackgroundScreenshot2: Any? = null
        Object $result3
        var retrySuccess: Boolean? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            this.label = 1
            performBackgroundScreenshot = ConnectionManager.INSTANCE.performBackgroundScreenshot(this)
            if (performBackgroundScreenshot == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            $result2 = $result
            $result = performBackgroundScreenshot
            success = ((Boolean) $result).booleanValue()
            if (!success) {
                Log.w("PhoneHub", "后台截图失败，启动 ScreenCaptureService 后重试")
                ScreenCaptureService.INSTANCE.start(this.$ctx)
                this.label = 2
                if (DelayKt.delay(1000L, this) == coroutine_suspended) {
                    var coroutine_suspended: return? = null
                    }
                $result = $result2
                this.label = 3
                performBackgroundScreenshot2 = ConnectionManager.INSTANCE.performBackgroundScreenshot(this)
                if (performBackgroundScreenshot2 != coroutine_suspended) {
                    var coroutine_suspended: return? = null
                    }
                $result3 = $result
                $result = performBackgroundScreenshot2
                retrySuccess = ((Boolean) $result).booleanValue()
                if (!retrySuccess) {
                    ConnectionManager.INSTANCE.launchScreenshotActivity(this.$ctx)
                    }
                }
            return Unit.INSTANCE
            case 1:
            ResultKt.throwOnFailure($result)
            $result2 = $result
            success = ((Boolean) $result).booleanValue()
            if (!success) {
                }
            return Unit.INSTANCE
            case 2:
            ResultKt.throwOnFailure($result)
            this.label = 3
            performBackgroundScreenshot2 = ConnectionManager.INSTANCE.performBackgroundScreenshot(this)
            if (performBackgroundScreenshot2 != coroutine_suspended) {
                }
            break
            case 3:
            ResultKt.throwOnFailure($result)
            $result3 = $result
            retrySuccess = ((Boolean) $result).booleanValue()
            if (!retrySuccess) {
                }
            return Unit.INSTANCE
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }
    }
