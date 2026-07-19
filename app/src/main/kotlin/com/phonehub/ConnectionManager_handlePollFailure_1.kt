package com.phonehub

import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.coroutines.Continuation
import kotlin.coroutines.jvm.internal.ContinuationImpl
import kotlin.coroutines.jvm.internal.DebugMetadata

class ConnectionManager {
    Object L$0
    Object L$1
    Object L$2
    var label: Int? = null
    /* synthetic */ Object result;
    final  ConnectionManager this$0

    public ConnectionManager$handlePollFailure$1(ConnectionManager connectionManager, Continuation<? super ConnectionManager$handlePollFailure$1> continuation) {
        super(continuation)
        this.this$0 = connectionManager
        }

    override
    fun invokeSuspend(obj: Any): Any {
        var handlePollFailure: Any? = null
        this.result = obj
        this.label |= Integer.MIN_VALUE
        handlePollFailure = this.this$0.handlePollFailure(null, null, this)
        var handlePollFailure: return? = null
        }
    }
