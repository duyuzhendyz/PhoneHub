package com.phonehub

import android.content.Context
import android.net.Uri
import androidx.constraintlayout.widget.ConstraintLayout
import com.phonehub.ConnectionManager
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function1
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElementBuildersKt
import kotlinx.serialization.json.JsonObjectBuilder

class ConnectionManager {
    final  Context $ctx
    final  String $displayName
    final  Uri $uri
    Object L$0
    Object L$1
    Object L$2
    var label: Int? = null

    public  class WhenMappings {
        public static final  int[] $EnumSwitchMapping$0

        static {
            val iArr: Array<Int> = new int[ConnectionManager.ChannelType.values().length]
            try {
                iArr[ConnectionManager.ChannelType.ADB.ordinal()] = 1
                } catch (NoSuchFieldError e) {
                }
            try {
                iArr[ConnectionManager.ChannelType.WIFI.ordinal()] = 2
                } catch (NoSuchFieldError e2) {
                }
            $EnumSwitchMapping$0 = iArr
            }
        }

    public ConnectionManager$sendFile$2(Context context, String str, Uri uri, Continuation<? super ConnectionManager$sendFile$2> continuation) {
        super(2, continuation)
        this.$ctx = context
        this.$displayName = str
        this.$uri = uri
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$sendFile$2(this.$ctx, this.$displayName, this.$uri, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$sendFile$2) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    /*  JADX ERROR: Types fix failed
    java.lang.NullPointerException: Cannot invoke "jadx.core.dex.instructions.args.InsnArg.getType()" because "changeArg" is null
    at jadx.core.dex.visitors.typeinference.TypeUpdate.moveListener(TypeUpdate.java:439)
    at jadx.core.dex.visitors.typeinference.TypeUpdate.runListeners(TypeUpdate.java:232)
    at jadx.core.dex.visitors.typeinference.TypeUpdate.requestUpdate(TypeUpdate.java:212)
    at jadx.core.dex.visitors.typeinference.TypeUpdate.updateTypeForSsaVar(TypeUpdate.java:183)
    at jadx.core.dex.visitors.typeinference.TypeUpdate.updateTypeChecked(TypeUpdate.java:112)
    at jadx.core.dex.visitors.typeinference.TypeUpdate.apply(TypeUpdate.java:83)
    at jadx.core.dex.visitors.typeinference.TypeUpdate.apply(TypeUpdate.java:56)
    at jadx.core.dex.visitors.typeinference.FixTypesVisitor.tryPossibleTypes(FixTypesVisitor.java:183)
    at jadx.core.dex.visitors.typeinference.FixTypesVisitor.deduceType(FixTypesVisitor.java:242)
    at jadx.core.dex.visitors.typeinference.FixTypesVisitor.tryDeduceTypes(FixTypesVisitor.java:221)
    at jadx.core.dex.visitors.typeinference.FixTypesVisitor.visit(FixTypesVisitor.java:91)
        */
    /* JADX WARN: Failed to apply debug info
    java.lang.NullPointerException: Cannot invoke "jadx.core.dex.instructions.args.InsnArg.getType()" because "changeArg" is null
    at jadx.core.dex.visitors.typeinference.TypeUpdate.moveListener(TypeUpdate.java:439)
    at jadx.core.dex.visitors.typeinference.TypeUpdate.runListeners(TypeUpdate.java:232)
    at jadx.core.dex.visitors.typeinference.TypeUpdate.requestUpdate(TypeUpdate.java:212)
    at jadx.core.dex.visitors.typeinference.TypeUpdate.updateTypeForSsaVar(TypeUpdate.java:183)
    at jadx.core.dex.visitors.typeinference.TypeUpdate.updateTypeChecked(TypeUpdate.java:112)
    at jadx.core.dex.visitors.typeinference.TypeUpdate.apply(TypeUpdate.java:83)
    at jadx.core.dex.visitors.typeinference.TypeUpdate.applyWithWiderIgnoreUnknown(TypeUpdate.java:74)
    at jadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor.applyDebugInfo(DebugInfoApplyVisitor.java:137)
    at jadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor.applyDebugInfo(DebugInfoApplyVisitor.java:133)
    at jadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor.searchAndApplyVarDebugInfo(DebugInfoApplyVisitor.java:75)
    at jadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor.lambda$applyDebugInfo$0(DebugInfoApplyVisitor.java:68)
    at java.base/java.util.ArrayList.forEach(ArrayList.java:1511)
    at jadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor.applyDebugInfo(DebugInfoApplyVisitor.java:68)
    at jadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor.visit(DebugInfoApplyVisitor.java:55)
     */
    override
    public final java.lang.Object invokeSuspend(java.lang.Object r28) {
        /*
        Method dump skipped, instructions count: 730
        To view this dump add '--comments-level debug' option
        */
        throw UnsupportedOperationException("Method not decompiled: com.phonehub.ConnectionManager$sendFile$2.invokeSuspend(java.lang.Object):java.lang.Object")
        }

    public static final Unit invokeSuspend$lambda$4(final Ref.ObjectRef $fileName, final Ref.LongRef $fileSize, final String $fileId, JsonObjectBuilder $this$buildJsonMessage) {
        JsonElementBuildersKt.put($this$buildJsonMessage, "source", "phone")
        JsonElementBuildersKt.putJsonObject($this$buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$sendFile$2$$ExternalSyntheticLambda0
            override
            fun invoke(obj: Any): Any {
                Unit invokeSuspend$lambda$4$lambda$3
                invokeSuspend$lambda$4$lambda$3 = ConnectionManager$sendFile$2.invokeSuspend$lambda$4$lambda$3(Ref.ObjectRef.this, $fileSize, $fileId, (JsonObjectBuilder) obj)
                return invokeSuspend$lambda$4$lambda$3
                }
            })
        return Unit.INSTANCE
        }

    public static final Unit invokeSuspend$lambda$4$lambda$3(Ref.ObjectRef $fileName, Ref.LongRef $fileSize, String $fileId, JsonObjectBuilder $this$putJsonObject) {
        JsonElementBuildersKt.put($this$putJsonObject, "action", "send_file_head")
        JsonElementBuildersKt.put($this$putJsonObject, FileTransferReceiver.EXTRA_FILE_NAME, $fileName.element)
        JsonElementBuildersKt.put($this$putJsonObject, FileTransferReceiver.EXTRA_FILE_SIZE, Long.valueOf($fileSize.element))
        JsonElementBuildersKt.put($this$putJsonObject, "file_id", $fileId)
        return Unit.INSTANCE
        }
    }
