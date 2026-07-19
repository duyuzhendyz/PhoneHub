package com.phonehub

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
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function1
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineScopeKt
import kotlinx.coroutines.DelayKt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class ConnectionManager {
    private  Object L$0
    Object L$1
    var label: Int? = null

    public ConnectionManager$startPcCameraPolling$1(Continuation<? super ConnectionManager$startPcCameraPolling$1> continuation) {
        super(2, continuation)
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        ConnectionManager$startPcCameraPolling$1 connectionManager$startPcCameraPolling$1 = new ConnectionManager$startPcCameraPolling$1(continuation)
        connectionManager$startPcCameraPolling$1.L$0 = obj
        return connectionManager$startPcCameraPolling$1
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$startPcCameraPolling$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun invokeSuspend(/* Object $result */): Any {
        CoroutineScope $this$launch
        var baseUrl: String? = null
        Object $result2
        Object $result3
        var baseUrl2: String? = null
        CoroutineScope $this$launch2
        var baseUrl3: String? = null
        Object $result4
        var baseUrl4: String? = null
        Object $result5
        Object $result6
        var bytes: Array<Byte>? = null
        var mutableSharedFlow: MutableSharedFlow? = null
        var httpResponse: HttpResponse? = null
        var resp: HttpResponse? = null
        var status: HttpStatusCode? = null
        CoroutineScope $this$launch3
        HttpClient $this$request$iv$iv$iv$iv
        var ip: String? = null
        var i: Int? = null
        var mutableStateFlow: MutableStateFlow? = null
        var i2: Int? = null
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
                            $result3 = $result2
                            baseUrl2 = baseUrl
                            $this$launch2 = $this$launch
                            this.L$0 = $this$launch2
                            this.L$1 = baseUrl2
                            this.label = 4
                            if (DelayKt.delay(100L, this) != coroutine_suspended) {
                                }
                            }
                        $this$request$iv$iv$iv$iv = ConnectionManager.client
                        if ($this$request$iv$iv$iv$iv != null) {
                            String urlString$iv = baseUrl + "/api/camera_frame"
                            HttpRequestBuilder $this$get_u24lambda_u244$iv = HttpRequestBuilder()
                            HttpRequestKt.url($this$get_u24lambda_u244$iv, urlString$iv)
                            HttpTimeoutKt.timeout($this$get_u24lambda_u244$iv, Function1() { // from class: com.phonehub.ConnectionManager$startPcCameraPolling$1$$ExternalSyntheticLambda0
                                override
                                fun invoke(obj: Any): Any {
                                    Unit invokeSuspend$lambda$1$lambda$0
                                    invokeSuspend$lambda$1$lambda$0 = ConnectionManager$startPcCameraPolling$1.invokeSuspend$lambda$1$lambda$0((HttpTimeout.HttpTimeoutCapabilityConfiguration) obj)
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
                            $this$launch3 = $this$launch
                            baseUrl4 = baseUrl
                            $result5 = $result2
                            $result2 = execute
                            try {
                                } catch (Exception e2) {
                                $result2 = $result5
                                $this$launch = $this$launch3
                                baseUrl = baseUrl4
                                $result3 = $result2
                                baseUrl2 = baseUrl
                                $this$launch2 = $this$launch
                                this.L$0 = $this$launch2
                                this.L$1 = baseUrl2
                                this.label = 4
                                if (DelayKt.delay(100L, this) != coroutine_suspended) {
                                    }
                                }
                            httpResponse = (HttpResponse) $result2
                            $this$launch = $this$launch3
                            resp = httpResponse
                            if (resp != null) {
                                try {
                                    } catch (Exception e3) {
                                    $result2 = $result5
                                    baseUrl = baseUrl4
                                    $result3 = $result2
                                    baseUrl2 = baseUrl
                                    $this$launch2 = $this$launch
                                    this.L$0 = $this$launch2
                                    this.L$1 = baseUrl2
                                    this.label = 4
                                    if (DelayKt.delay(100L, this) != coroutine_suspended) {
                                        }
                                    }
                                status = resp.getStatus()
                                } else {
                                status = null
                                }
                            if (Intrinsics.areEqual(status, HttpStatusCode.INSTANCE.getOK())) {
                                this.L$0 = $this$launch
                                this.L$1 = baseUrl4
                                this.label = 2
                                $result6 = ReadersKt.readBytes(resp, this)
                                if ($result6 == coroutine_suspended) {
                                    var coroutine_suspended: return? = null
                                    }
                                bytes = (byte[]) $result6
                                if (!(bytes.length != 0)) {
                                    $result3 = $result5
                                    $this$launch2 = $this$launch
                                    baseUrl2 = baseUrl4
                                    this.L$0 = $this$launch2
                                    this.L$1 = baseUrl2
                                    this.label = 4
                                    if (DelayKt.delay(100L, this) != coroutine_suspended) {
                                        }
                                    } else {
                                    mutableSharedFlow = ConnectionManager._pcCameraFrame
                                    this.L$0 = $this$launch
                                    this.L$1 = baseUrl4
                                    this.label = 3
                                    if (mutableSharedFlow.emit(bytes, this) == coroutine_suspended) {
                                        var coroutine_suspended: return? = null
                                        }
                                    $result4 = $result5
                                    baseUrl3 = baseUrl4
                                    $result3 = $result4
                                    baseUrl2 = baseUrl3
                                    $this$launch2 = $this$launch
                                    this.L$0 = $this$launch2
                                    this.L$1 = baseUrl2
                                    this.label = 4
                                    if (DelayKt.delay(100L, this) != coroutine_suspended) {
                                        var coroutine_suspended: return? = null
                                        }
                                    $this$launch = $this$launch2
                                    baseUrl = baseUrl2
                                    $result2 = $result3
                                    if (!CoroutineScopeKt.isActive($this$launch)) {
                                        return Unit.INSTANCE
                                        }
                                    }
                                } else {
                                $result3 = $result5
                                $this$launch2 = $this$launch
                                baseUrl2 = baseUrl4
                                this.L$0 = $this$launch2
                                this.L$1 = baseUrl2
                                this.label = 4
                                if (DelayKt.delay(100L, this) != coroutine_suspended) {
                                    }
                                }
                            } else {
                            baseUrl4 = baseUrl
                            $result5 = $result2
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
            baseUrl4 = this.L$1
            CoroutineScope $this$launch5 = (CoroutineScope) this.L$0
            try {
                ResultKt.throwOnFailure($result2)
                $this$launch3 = $this$launch5
                $result5 = $result2
                } catch (Exception e4) {
                baseUrl = baseUrl4
                $this$launch = $this$launch5
                $result3 = $result2
                baseUrl2 = baseUrl
                $this$launch2 = $this$launch
                this.L$0 = $this$launch2
                this.L$1 = baseUrl2
                this.label = 4
                if (DelayKt.delay(100L, this) != coroutine_suspended) {
                    }
                }
            httpResponse = (HttpResponse) $result2
            $this$launch = $this$launch3
            resp = httpResponse
            if (resp != null) {
                }
            if (Intrinsics.areEqual(status, HttpStatusCode.INSTANCE.getOK())) {
                }
            break
            case 2:
            $result6 = $result
            val baseUrl5: String = (String) this.L$1
            $this$launch = (CoroutineScope) this.L$0
            ResultKt.throwOnFailure($result6)
            baseUrl4 = baseUrl5
            $result5 = $result6
            bytes = (byte[]) $result6
            if (!(bytes.length != 0)) {
                }
            break
            case 3:
            $result4 = $result
            baseUrl3 = this.L$1
            $this$launch = (CoroutineScope) this.L$0
            ResultKt.throwOnFailure($result4)
            $result3 = $result4
            baseUrl2 = baseUrl3
            $this$launch2 = $this$launch
            this.L$0 = $this$launch2
            this.L$1 = baseUrl2
            this.label = 4
            if (DelayKt.delay(100L, this) != coroutine_suspended) {
                }
            break
            case 4:
            val baseUrl6: String = (String) this.L$1
            CoroutineScope $this$launch6 = (CoroutineScope) this.L$0
            ResultKt.throwOnFailure($result)
            $this$launch = $this$launch6
            baseUrl = baseUrl6
            $result2 = $result
            if (!CoroutineScopeKt.isActive($this$launch)) {
                }
            break
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }

    public static final Unit invokeSuspend$lambda$1$lambda$0(HttpTimeout.HttpTimeoutCapabilityConfiguration $this$timeout) {
        $this$timeout.setRequestTimeoutMillis(3000L)
        return Unit.INSTANCE
        }
    }
