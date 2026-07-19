package com.phonehub

import android.media.AudioTrack
import androidx.constraintlayout.widget.ConstraintLayout
import com.phonehub.ConnectionManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutKt
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestKt
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.ReadersKt
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.Boxing
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.internal.ProgressionUtilKt
import kotlin.jvm.functions.Function1
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineScopeKt
import kotlinx.coroutines.DelayKt
import kotlinx.coroutines.flow.MutableStateFlow

class ConnectionManager {
    private  Object L$0
    Object L$1
    var label: Int? = null

    public ConnectionManager$startPcAudioPolling$1(Continuation<? super ConnectionManager$startPcAudioPolling$1> continuation) {
        super(2, continuation)
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        ConnectionManager$startPcAudioPolling$1 connectionManager$startPcAudioPolling$1 = new ConnectionManager$startPcAudioPolling$1(continuation)
        connectionManager$startPcAudioPolling$1.L$0 = obj
        return connectionManager$startPcAudioPolling$1
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$startPcAudioPolling$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun invokeSuspend(/* Object $result */): Any {
        var ip: String? = null
        var i: Int? = null
        var baseUrl: String? = null
        CoroutineScope $this$launch
        Object $result2
        var mutableStateFlow: MutableStateFlow? = null
        var i2: Int? = null
        var baseUrl2: String? = null
        CoroutineScope $this$launch2
        Object $result3
        Object $result4
        var baseUrl3: String? = null
        CoroutineScope $this$launch3
        var bytes: Array<Byte>? = null
        var audioTrack: AudioTrack? = null
        var httpResponse: HttpResponse? = null
        var resp: HttpResponse? = null
        var status: HttpStatusCode? = null
        HttpClient $this$request$iv$iv$iv$iv
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure($result)
            CoroutineScope $this$launch4 = (CoroutineScope) this.L$0
            ip = ConnectionManager.pcIp
            if (ip == null) {
                ip = "192.168.3.9"
                }
            if (ConnectionManager.INSTANCE.isAdbAvailable()) {
                mutableStateFlow = ConnectionManager._currentChannel
                if (mutableStateFlow.getValue() == ConnectionManager.ChannelType.ADB) {
                    i2 = ConnectionManager.connectPort
                    baseUrl = "http://127.0.0.1:" + i2
                    $this$launch = $this$launch4
                    $result2 = $result
                    if (!CoroutineScopeKt.isActive($this$launch)) {
                        try {
                            } catch (Exception e) {
                            $result4 = $result2
                            baseUrl3 = baseUrl
                            $this$launch3 = $this$launch
                            this.L$0 = $this$launch3
                            this.L$1 = baseUrl3
                            this.label = 3
                            if (DelayKt.delay(30L, this) != coroutine_suspended) {
                                }
                            }
                        $this$request$iv$iv$iv$iv = ConnectionManager.client
                        if ($this$request$iv$iv$iv$iv != null) {
                            String urlString$iv = baseUrl + "/api/audio"
                            HttpRequestBuilder $this$get_u24lambda_u244$iv = HttpRequestBuilder()
                            HttpRequestKt.url($this$get_u24lambda_u244$iv, urlString$iv)
                            HttpTimeoutKt.timeout($this$get_u24lambda_u244$iv, Function1() { // from class: com.phonehub.ConnectionManager$startPcAudioPolling$1$$ExternalSyntheticLambda0
                                override
                                fun invoke(obj: Any): Any {
                                    Unit invokeSuspend$lambda$1$lambda$0
                                    invokeSuspend$lambda$1$lambda$0 = ConnectionManager$startPcAudioPolling$1.invokeSuspend$lambda$1$lambda$0((HttpTimeout.HttpTimeoutCapabilityConfiguration) obj)
                                    return invokeSuspend$lambda$1$lambda$0
                                    }
                                })
                            $this$get_u24lambda_u244$iv.setMethod(HttpMethod.INSTANCE.getGet())
                            this.L$0 = $this$launch
                            this.L$1 = baseUrl
                            this.label = 1
                            val execute: Any = new HttpStatement($this$get_u24lambda_u244$iv, $this$request$iv$iv$iv$iv).execute(this)
                            if (execute == coroutine_suspended) {
                                var coroutine_suspended: return? = null
                                }
                            $result3 = $result2
                            $result2 = execute
                            baseUrl2 = baseUrl
                            $this$launch2 = $this$launch
                            try {
                                } catch (Exception e2) {
                                $result2 = $result3
                                baseUrl = baseUrl2
                                $this$launch = $this$launch2
                                $result4 = $result2
                                baseUrl3 = baseUrl
                                $this$launch3 = $this$launch
                                this.L$0 = $this$launch3
                                this.L$1 = baseUrl3
                                this.label = 3
                                if (DelayKt.delay(30L, this) != coroutine_suspended) {
                                    }
                                }
                            httpResponse = (HttpResponse) $result2
                            baseUrl = baseUrl2
                            $this$launch = $this$launch2
                            resp = httpResponse
                            if (resp != null) {
                                try {
                                    } catch (Exception e3) {
                                    $result2 = $result3
                                    $result4 = $result2
                                    baseUrl3 = baseUrl
                                    $this$launch3 = $this$launch
                                    this.L$0 = $this$launch3
                                    this.L$1 = baseUrl3
                                    this.label = 3
                                    if (DelayKt.delay(30L, this) != coroutine_suspended) {
                                        }
                                    }
                                status = resp.getStatus()
                                } else {
                                status = null
                                }
                            if (Intrinsics.areEqual(status, HttpStatusCode.INSTANCE.getOK())) {
                                this.L$0 = $this$launch
                                this.L$1 = baseUrl
                                this.label = 2
                                $result2 = ReadersKt.readBytes(resp, this)
                                if ($result2 == coroutine_suspended) {
                                    var coroutine_suspended: return? = null
                                    }
                                bytes = (byte[]) $result2
                                if (!(bytes.length != 0)) {
                                    val stereo: Array<Byte> = new byte[bytes.length * 2]
                                    val i3: Int = 0
                                    val progressionLastElement: Int = ProgressionUtilKt.getProgressionLastElement(0, bytes.length - 1, 2)
                                    if (0 <= progressionLastElement) {
                                        while (true) {
                                            stereo[i3 * 2] = bytes[i3]
                                            stereo[(i3 * 2) + 1] = bytes[i3 + 1]
                                            stereo[(i3 * 2) + 2] = bytes[i3]
                                            stereo[(i3 * 2) + 3] = bytes[i3 + 1]
                                            if (i3 != progressionLastElement) {
                                                i3 += 2
                                                }
                                            }
                                        }
                                    audioTrack = ConnectionManager.pcAudioTrack
                                    if (audioTrack != null) {
                                        Boxing.boxInt(audioTrack.write(stereo, 0, stereo.length, 1))
                                        }
                                    }
                                $result4 = $result3
                                baseUrl3 = baseUrl
                                $this$launch3 = $this$launch
                                this.L$0 = $this$launch3
                                this.L$1 = baseUrl3
                                this.label = 3
                                if (DelayKt.delay(30L, this) != coroutine_suspended) {
                                    var coroutine_suspended: return? = null
                                    }
                                $this$launch = $this$launch3
                                baseUrl = baseUrl3
                                $result2 = $result4
                                if (!CoroutineScopeKt.isActive($this$launch)) {
                                    return Unit.INSTANCE
                                    }
                                } else {
                                $result4 = $result3
                                baseUrl3 = baseUrl
                                $this$launch3 = $this$launch
                                this.L$0 = $this$launch3
                                this.L$1 = baseUrl3
                                this.label = 3
                                if (DelayKt.delay(30L, this) != coroutine_suspended) {
                                    }
                                }
                            } else {
                            $result3 = $result2
                            httpResponse = null
                            resp = httpResponse
                            if (resp != null) {
                                }
                            if (Intrinsics.areEqual(status, HttpStatusCode.INSTANCE.getOK())) {
                                }
                            }
                        }
                    }
                }
            i = ConnectionManager.connectPort
            baseUrl = "http://" + ip + ":" + i
            $this$launch = $this$launch4
            $result2 = $result
            if (!CoroutineScopeKt.isActive($this$launch)) {
                }
            case 1:
            $result2 = $result
            baseUrl2 = this.L$1
            $this$launch2 = (CoroutineScope) this.L$0
            try {
                ResultKt.throwOnFailure($result2)
                $result3 = $result2
                } catch (Exception e4) {
                baseUrl = baseUrl2
                $this$launch = $this$launch2
                $result4 = $result2
                baseUrl3 = baseUrl
                $this$launch3 = $this$launch
                this.L$0 = $this$launch3
                this.L$1 = baseUrl3
                this.label = 3
                if (DelayKt.delay(30L, this) != coroutine_suspended) {
                    }
                }
            httpResponse = (HttpResponse) $result2
            baseUrl = baseUrl2
            $this$launch = $this$launch2
            resp = httpResponse
            if (resp != null) {
                }
            if (Intrinsics.areEqual(status, HttpStatusCode.INSTANCE.getOK())) {
                }
            break
            case 2:
            $result2 = $result
            baseUrl = this.L$1
            $this$launch = (CoroutineScope) this.L$0
            try {
                ResultKt.throwOnFailure($result2)
                $result3 = $result2
                } catch (Exception e5) {
                $result4 = $result2
                baseUrl3 = baseUrl
                $this$launch3 = $this$launch
                this.L$0 = $this$launch3
                this.L$1 = baseUrl3
                this.label = 3
                if (DelayKt.delay(30L, this) != coroutine_suspended) {
                    }
                }
            bytes = (byte[]) $result2
            if (!(bytes.length != 0)) {
                }
            $result4 = $result3
            baseUrl3 = baseUrl
            $this$launch3 = $this$launch
            this.L$0 = $this$launch3
            this.L$1 = baseUrl3
            this.label = 3
            if (DelayKt.delay(30L, this) != coroutine_suspended) {
                }
            break
            case 3:
            val baseUrl4: String = (String) this.L$1
            CoroutineScope $this$launch5 = (CoroutineScope) this.L$0
            ResultKt.throwOnFailure($result)
            $this$launch = $this$launch5
            baseUrl = baseUrl4
            $result2 = $result
            if (!CoroutineScopeKt.isActive($this$launch)) {
                }
            break
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }

    public static final Unit invokeSuspend$lambda$1$lambda$0(HttpTimeout.HttpTimeoutCapabilityConfiguration $this$timeout) {
        $this$timeout.setRequestTimeoutMillis(2000L)
        return Unit.INSTANCE
        }
    }
