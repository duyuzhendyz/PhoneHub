package com.phonehub

import android.content.Context
import android.content.SharedPreferences
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelayKt

class `ConnectionManager$init$2`(private val `$ctx`: Context, continuation: Continuation<*>?) : SuspendLambda(2, continuation) {
    var label: Int = 0
    var I$0: Int = 0
    var L$0: Any? = null
    var L$1: Any? = null

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return `ConnectionManager$init$2`(`$ctx`, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<*>): Any {
        return (create(coroutineScope, continuation) as `ConnectionManager$init$2`).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        var cachedIp: String? = null
        var cachedToken: String? = null
        var cachedPort: Int? = null
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val prefs: SharedPreferences = this.`$ctx`.getSharedPreferences("phonehub_prefs", 0)
                cachedIp = ConnectionManager.getCachedIp()
                if (cachedIp == null) {
                    cachedIp = "192.168.3.9"
                }
                val cachedPort2: Int = if (prefs.contains("cached_port")) prefs.getInt("cached_port", 58627) else 58627
                val string: String? = prefs.getString("cached_token", "541881452418845")
                cachedToken = string ?: "541881452418845"
                this.L$0 = cachedIp
                this.L$1 = cachedToken
                this.I$0 = cachedPort2
                this.label = 1
                if (DelayKt.delay(300L, this) == coroutine_suspended) {
                    return coroutine_suspended
                }
                cachedPort = cachedPort2
            }
            1 -> {
                cachedPort = this.I$0
                cachedToken = this.L$1 as String?
                cachedIp = this.L$0 as String?
                ResultKt.throwOnFailure(result)
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
        ConnectionManager.connect(cachedIp!!, cachedPort!!, cachedToken!!)
        return Unit
    }
}
