package com.phonehub

import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.KotlinNothingValueException
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow

class MainActivity {
    var label: Int? = null
    final  MainActivity this$0

    public MainActivity$setupFlows$4(MainActivity mainActivity, Continuation<? super MainActivity$setupFlows$4> continuation) {
        super(2, continuation)
        this.this$0 = mainActivity
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new MainActivity$setupFlows$4(this.this$0, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((MainActivity$setupFlows$4) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            val cameraSwitchRequest: SharedFlow<Unit> = ConnectionManager.INSTANCE.getCameraSwitchRequest()
            val mainActivity: MainActivity = this.this$0
            this.label = 1
            if (cameraSwitchRequest.collect(FlowCollector() { // from class: com.phonehub.MainActivity$setupFlows$4.1
                override
                public   Object emit(Object value, Continuation $completion) {
                    return emit((Unit) value, (Continuation<? super Unit>) $completion)
                    }

                fun emit(it: Unit, continuation: Continuation<? super Unit>): Any {
                    var z: Boolean? = null
                    val mainActivity2: MainActivity = MainActivity.this
                    z = MainActivity.this.cameraPreviewRunning
                    mainActivity2.performCameraSwitch(z)
                    return Unit.INSTANCE
                    }
                }, this) == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            break
            case 1:
            ResultKt.throwOnFailure($result)
            break
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        throw KotlinNothingValueException()
        }
    }
