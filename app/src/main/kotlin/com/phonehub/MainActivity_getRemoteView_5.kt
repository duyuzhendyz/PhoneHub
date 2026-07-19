package com.phonehub

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
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
import kotlinx.coroutines.flow.StateFlow

class MainActivity {
    final  ImageView $mediaCoverImg
    var label: Int? = null

    public MainActivity$getRemoteView$5(ImageView imageView, Continuation<? super MainActivity$getRemoteView$5> continuation) {
        super(2, continuation)
        this.$mediaCoverImg = imageView
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new MainActivity$getRemoteView$5(this.$mediaCoverImg, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((MainActivity$getRemoteView$5) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    fun invokeSuspend(/* Object $result */): Any {
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            val mediaThumbnail: StateFlow<Array<Byte>> = ConnectionManager.INSTANCE.getMediaThumbnail()
            val imageView: ImageView = this.$mediaCoverImg
            this.label = 1
            if (mediaThumbnail.collect(FlowCollector() { // from class: com.phonehub.MainActivity$getRemoteView$5.1
                override
                public   Object emit(Object value, Continuation $completion) {
                    return emit((byte[]) value, (Continuation<? super Unit>) $completion)
                    }

                fun emit(bytes: Array<Byte>, continuation: Continuation<? super Unit>): Any {
                    if (bytes != null) {
                        if ((!(bytes.length == 0)) && imageView != null) {
                            val bmp: Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length)
                            if (bmp == null) {
                                imageView.setVisibility(8)
                                } else {
                                imageView.setImageBitmap(bmp)
                                imageView.setVisibility(0)
                                }
                            return Unit.INSTANCE
                            }
                        }
                    val imageView2: ImageView = imageView
                    if (imageView2 != null) {
                        imageView2.setVisibility(8)
                        }
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
