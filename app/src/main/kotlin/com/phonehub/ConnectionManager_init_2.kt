package com.phonehub

import android.content.Context
import android.content.SharedPreferences
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
    int I$0
    Object L$0
    Object L$1
    var label: Int? = null

    public ConnectionManager$init$2(Context context, Continuation<? super ConnectionManager$init$2> continuation) {
        super(2, continuation)
        this.$ctx = context
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$init$2(this.$ctx, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$init$2) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        var cachedIp: String? = null
        var cachedToken: String? = null
        var cachedPort: Int? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            val prefs: SharedPreferences = this.$ctx.getSharedPreferences("phonehub_prefs", 0)
            cachedIp = ConnectionManager.INSTANCE.getCachedIp()
            if (cachedIp == null) {
                cachedIp = "192.168.3.9"
                }
            val cachedPort2: Int = prefs.contains("cached_port") ? prefs.getInt("cached_port", 58627) : 58627
            val string: String = prefs.getString("cached_token", "541881452418845")
            cachedToken = string != null ? string : "541881452418845"
            this.L$0 = cachedIp
            this.L$1 = cachedToken
            this.I$0 = cachedPort2
            this.label = 1
            if (DelayKt.delay(300L, this) == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            cachedPort = cachedPort2
            break
            case 1:
            cachedPort = this.I$0
            cachedToken = this.L$1
            cachedIp = this.L$0
            ResultKt.throwOnFailure($result)
            break
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        ConnectionManager.INSTANCE.connect(cachedIp, cachedPort, cachedToken)
        return Unit.INSTANCE
        }
    }
