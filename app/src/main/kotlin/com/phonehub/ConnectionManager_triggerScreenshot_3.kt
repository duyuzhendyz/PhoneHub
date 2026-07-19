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

class ConnectionManager {
    final  Context $ctx
    var label: Int? = null

    public ConnectionManager$triggerScreenshot$3(Context context, Continuation<? super ConnectionManager$triggerScreenshot$3> continuation) {
        super(2, continuation)
        this.$ctx = context
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$triggerScreenshot$3(this.$ctx, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$triggerScreenshot$3) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        var performBackgroundScreenshot: Any? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            this.label = 1
            performBackgroundScreenshot = ConnectionManager.INSTANCE.performBackgroundScreenshot(this)
            if (performBackgroundScreenshot != coroutine_suspended) {
                $result = performBackgroundScreenshot
                break
                } else {
                var coroutine_suspended: return? = null
                }
            case 1:
            ResultKt.throwOnFailure($result)
            break
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        val success: Boolean = ((Boolean) $result).booleanValue()
        if (!success) {
            Log.w("PhoneHub", "后台截图失败，回退到 Activity 授权截图")
            ConnectionManager.INSTANCE.launchScreenshotActivity(this.$ctx)
            }
        return Unit.INSTANCE
        }
    }
