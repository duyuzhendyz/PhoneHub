package com.phonehub

import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.coroutines.Continuation
import kotlin.coroutines.jvm.internal.ContinuationImpl
import kotlin.coroutines.jvm.internal.DebugMetadata

class ConnectionManager {
    var label: Int? = null
    /* synthetic */ Object result;
    final  ConnectionManager this$0

    public ConnectionManager$sendRaw$1(ConnectionManager connectionManager, Continuation<? super ConnectionManager$sendRaw$1> continuation) {
        super(continuation)
        this.this$0 = connectionManager
        }

    override
    fun invokeSuspend(obj: Any): Any {
        var sendRaw: Any? = null
        this.result = obj
        this.label |= Integer.MIN_VALUE
        sendRaw = this.this$0.sendRaw(null, this)
        var sendRaw: return? = null
        }
    }
