package com.phonehub

import android.content.Context
import android.util.Log
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelayKt

class ConnectionManager_triggerScreenshot_1(
    private val `$ctx`: Context,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_triggerScreenshot_1(this.`$ctx`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_triggerScreenshot_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        var performBackgroundScreenshot: Any? = null
        var performBackgroundScreenshot2: Any? = null
        var success: Boolean = false
        var retrySuccess: Boolean = false
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                this.label = 1
                performBackgroundScreenshot = ConnectionManager.INSTANCE.performBackgroundScreenshot(this)
                if (performBackgroundScreenshot == coroutine_suspended) {
                    return coroutine_suspended
                }
                success = performBackgroundScreenshot as Boolean
                if (!success) {
                    Log.w("PhoneHub", "后台截图失败，启动 ScreenCaptureService 后重试")
                    ScreenCaptureService.INSTANCE.start(this.`$ctx`)
                    this.label = 2
                    if (DelayKt.delay(1000L, this) == coroutine_suspended) {
                        return coroutine_suspended
                    }
                    this.label = 3
                    performBackgroundScreenshot2 = ConnectionManager.INSTANCE.performBackgroundScreenshot(this)
                    if (performBackgroundScreenshot2 == coroutine_suspended) {
                        return coroutine_suspended
                    }
                    retrySuccess = performBackgroundScreenshot2 as Boolean
                    if (!retrySuccess) {
                        ConnectionManager.INSTANCE.launchScreenshotActivity(this.`$ctx`)
                    }
                }
                return Unit
            }
            1 -> {
                ResultKt.throwOnFailure(result)
                success = result as Boolean
                if (!success) {
                }
                return Unit
            }
            2 -> {
                ResultKt.throwOnFailure(result)
                this.label = 3
                performBackgroundScreenshot2 = ConnectionManager.INSTANCE.performBackgroundScreenshot(this)
                if (performBackgroundScreenshot2 == coroutine_suspended) {
                    return coroutine_suspended
                }
                retrySuccess = performBackgroundScreenshot2 as Boolean
                if (!retrySuccess) {
                    ConnectionManager.INSTANCE.launchScreenshotActivity(this.`$ctx`)
                }
                return Unit
            }
            3 -> {
                ResultKt.throwOnFailure(result)
                retrySuccess = result as Boolean
                if (!retrySuccess) {
                }
                return Unit
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
    }
}
