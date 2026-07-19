package com.phonehub

import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.coroutines.Continuation
import kotlin.coroutines.jvm.internal.ContinuationImpl
import kotlin.coroutines.jvm.internal.DebugMetadata

class ConnectionManager {
    int I$0
    Object L$0
    Object L$1
    var label: Int? = null
    /* synthetic */ Object result;
    final  ConnectionManager this$0

    public ConnectionManager$testConnection$1(ConnectionManager connectionManager, Continuation<? super ConnectionManager$testConnection$1> continuation) {
        super(continuation)
        this.this$0 = connectionManager
        }

    override
    fun invokeSuspend(obj: Any): Any {
        var testConnection: Any? = null
        this.result = obj
        this.label |= Integer.MIN_VALUE
        testConnection = this.this$0.testConnection(null, 0, this)
        var testConnection: return? = null
        }
    }
