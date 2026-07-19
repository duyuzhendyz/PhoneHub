package com.phonehub

import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import androidx.lifecycle.CoroutineLiveDataKt
import com.phonehub.ConnectionManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutKt
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestKt
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpResponseKt
import io.ktor.client.statement.HttpStatement
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import java.util.Collection
import java.util.Iterator
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.Boxing
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function1
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineScopeKt
import kotlinx.coroutines.DelayKt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonElementKt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ConnectionManager {
    final  ConnectionManager.ChannelType $channel
    int I$0
    long J$0
    private  Object L$0
    var label: Int? = null

    public ConnectionManager$startStatusPolling$1(ConnectionManager.ChannelType channelType, Continuation<? super ConnectionManager$startStatusPolling$1> continuation) {
        super(2, continuation)
        this.$channel = channelType
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        ConnectionManager$startStatusPolling$1 connectionManager$startStatusPolling$1 = new ConnectionManager$startStatusPolling$1(this.$channel, continuation)
        connectionManager$startStatusPolling$1.L$0 = obj
        return connectionManager$startStatusPolling$1
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$startStatusPolling$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    /* JADX WARN: Code restructure failed: missing block: B:108:0x02a9, code lost:

    r0 = e
     */
    /* JADX WARN: Code restructure failed: missing block: B:109:0x02aa, code lost:

    r7 = r5
     */
    /* JADX WARN: Code restructure failed: missing block: B:123:0x02de, code lost:

    r0 = e
     */
    override
    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun invokeSuspend(obj: Any): Any {
        var mutableStateFlow: MutableStateFlow? = null
        var coroutineScope: CoroutineScope? = null
        var i: Int? = null
        var obj2: Any? = null
        var i2: Int? = null
        var obj3: Any? = null
        var j: Long? = null
        var j2: Long? = null
        var jsonObject: JsonObject? = null
        var obj4: Any? = null
        var i3: Int? = null
        var coroutineScope2: CoroutineScope? = null
        var jsonElement: JsonElement? = null
        var jsonArray: Collection? = null
        var jsonObject2: JsonObject? = null
        var jsonPrimitive: JsonPrimitive? = null
        var jsonPrimitive2: JsonPrimitive? = null
        var mutableStateFlow2: MutableStateFlow? = null
        var mutableStateFlow3: MutableStateFlow? = null
        var mutableStateFlow4: MutableStateFlow? = null
        var channelName: String? = null
        var j3: Long? = null
        var httpResponse: HttpResponse? = null
        var httpResponse2: HttpResponse? = null
        var status: HttpStatusCode? = null
        var str: String? = null
        var i4: Int? = null
        var append: StringBuilder? = null
        var httpClient: HttpClient? = null
        var i5: Int? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (this.label) {
            case 0:
            ResultKt.throwOnFailure(obj)
            val coroutineScope3: CoroutineScope = (CoroutineScope) this.L$0
            mutableStateFlow = ConnectionManager._currentChannel
            mutableStateFlow.setValue(this.$channel)
            coroutineScope = coroutineScope3
            i = 0
            obj2 = obj
            if (CoroutineScopeKt.isActive(coroutineScope)) {
                try {
                    } catch (Exception e) {
                    e = e
                    val i6: Int = i + 1
                    Boxing.boxInt(Log.w("PhoneHub", "轮询异常: " + e.getMessage() + ", failCount=" + i6))
                    obj4 = obj2
                    i3 = i6
                    coroutineScope2 = coroutineScope
                    if (i3 >= 5) {
                        }
                    if (CoroutineScopeKt.isActive(coroutineScope)) {
                        }
                    }
                str = ConnectionManager.pcIp
                if (str == null) {
                    str = "192.168.3.9"
                    }
                if (this.$channel == ConnectionManager.ChannelType.ADB) {
                    i5 = ConnectionManager.connectPort
                    append = StringBuilder().append("http://127.0.0.1:").append(i5)
                    } else {
                    i4 = ConnectionManager.connectPort
                    append = StringBuilder().append("http://").append(str).append(":").append(i4)
                    }
                val sb: String = append.toString()
                j3 = System.currentTimeMillis()
                httpClient = ConnectionManager.client
                if (httpClient != null) {
                    val httpRequestBuilder: HttpRequestBuilder = new HttpRequestBuilder()
                    HttpRequestKt.url(httpRequestBuilder, sb + "/api/poll")
                    HttpTimeoutKt.timeout(httpRequestBuilder, Function1() { // from class: com.phonehub.ConnectionManager$startStatusPolling$1$$ExternalSyntheticLambda0
                        override
                        fun invoke(obj5: Any): Any {
                            Unit invokeSuspend$lambda$1$lambda$0
                            invokeSuspend$lambda$1$lambda$0 = ConnectionManager$startStatusPolling$1.invokeSuspend$lambda$1$lambda$0((HttpTimeout.HttpTimeoutCapabilityConfiguration) obj5)
                            return invokeSuspend$lambda$1$lambda$0
                            }
                        })
                    httpRequestBuilder.setMethod(HttpMethod.INSTANCE.getGet())
                    this.L$0 = coroutineScope
                    this.I$0 = i
                    this.J$0 = j3
                    this.label = 1
                    val execute: Any = new HttpStatement(httpRequestBuilder, httpClient).execute(this)
                    if (execute == coroutine_suspended) {
                        var coroutine_suspended: return? = null
                        }
                    obj3 = obj2
                    obj2 = execute
                    i2 = i
                    j = j3
                    try {
                        } catch (Exception e2) {
                        e = e2
                        obj2 = obj3
                        i = i2
                        val i62: Int = i + 1
                        Boxing.boxInt(Log.w("PhoneHub", "轮询异常: " + e.getMessage() + ", failCount=" + i62))
                        obj4 = obj2
                        i3 = i62
                        coroutineScope2 = coroutineScope
                        if (i3 >= 5) {
                            }
                        if (CoroutineScopeKt.isActive(coroutineScope)) {
                            }
                        }
                    httpResponse = (HttpResponse) obj2
                    i = i2
                    j3 = j
                    httpResponse2 = httpResponse
                    j2 = System.currentTimeMillis() - j3
                    if (httpResponse2 != null) {
                        try {
                            } catch (Exception e3) {
                            e = e3
                            obj2 = obj3
                            val i622: Int = i + 1
                            Boxing.boxInt(Log.w("PhoneHub", "轮询异常: " + e.getMessage() + ", failCount=" + i622))
                            obj4 = obj2
                            i3 = i622
                            coroutineScope2 = coroutineScope
                            if (i3 >= 5) {
                                }
                            if (CoroutineScopeKt.isActive(coroutineScope)) {
                                }
                            }
                        status = httpResponse2.getStatus()
                        } else {
                        status = null
                        }
                    if (Intrinsics.areEqual(status, HttpStatusCode.INSTANCE.getOK())) {
                        ConnectionManager$startStatusPolling$1 connectionManager$startStatusPolling$1 = this
                        this.L$0 = coroutineScope
                        this.I$0 = i
                        this.J$0 = j2
                        this.label = 2
                        jsonObject = null
                        Object bodyAsText$default = HttpResponseKt.bodyAsText$default(httpResponse2, null, connectionManager$startStatusPolling$1, 1, null)
                        if (bodyAsText$default == coroutine_suspended) {
                            var coroutine_suspended: return? = null
                            }
                        obj2 = bodyAsText$default
                        val jsonObject3: JsonObject = JsonElementKt.getJsonObject(Json.INSTANCE.parseToJsonElement((String) obj2))
                        jsonElement = (JsonElement) jsonObject3.get((Object) "status_info")
                        if ((jsonElement == null ? JsonElementKt.getJsonObject(jsonElement) : jsonObject) != null) {
                            val connectionManager: ConnectionManager = ConnectionManager.INSTANCE
                            ConnectionManager.lastPcHeartbeatAt = System.currentTimeMillis()
                            mutableStateFlow2 = ConnectionManager._connectionState
                            mutableStateFlow2.setValue(ConnectionManager.ConnectionState.CONNECTED)
                            mutableStateFlow3 = ConnectionManager._connectionLatency
                            mutableStateFlow3.setValue(Boxing.boxLong(j2))
                            mutableStateFlow4 = ConnectionManager._connectionMessage
                            channelName = ConnectionManager.INSTANCE.channelName(this.$channel)
                            mutableStateFlow4.setValue("已连接 - " + channelName + " (" + j2 + "ms)")
                            ConnectionManager.INSTANCE.sendStatusReport()
                            val connectionManager2: ConnectionManager = ConnectionManager.INSTANCE
                            ConnectionManager.reconnectFailCount = 0
                            }
                        val jsonElement2: JsonElement = (JsonElement) jsonObject3.get((Object) "msgs")
                        jsonArray = jsonElement2 == null ? JsonElementKt.getJsonArray(jsonElement2) : jsonObject
                        if (jsonArray == 0 && (!jsonArray.isEmpty())) {
                            val it: Iterator<JsonElement> = jsonArray.iterator()
                            while (it.hasNext()) {
                                val jsonObject4: JsonObject = JsonElementKt.getJsonObject(it.next())
                                val jsonElement3: JsonElement = (JsonElement) jsonObject4.get((Object) "activate")
                                if (!Intrinsics.areEqual((jsonElement3 == null || (jsonPrimitive2 = JsonElementKt.getJsonPrimitive(jsonElement3)) == null) ? jsonObject : JsonElementKt.getContentOrNull(jsonPrimitive2), "ping")) {
                                    ConnectionManager.INSTANCE.handlePcMessage(jsonObject4)
                                    }
                                }
                            obj2 = obj3
                            } else {
                            val jsonElement4: JsonElement = (JsonElement) jsonObject3.get((Object) NotificationCompat.CATEGORY_MESSAGE)
                            jsonObject2 = jsonElement4 == null ? JsonElementKt.getJsonObject(jsonElement4) : jsonObject
                            if (jsonObject2 != null) {
                                val jsonElement5: JsonElement = (JsonElement) jsonObject2.get((Object) "activate")
                                if (!Intrinsics.areEqual((jsonElement5 == null || (jsonPrimitive = JsonElementKt.getJsonPrimitive(jsonElement5)) == null) ? jsonObject : JsonElementKt.getContentOrNull(jsonPrimitive), "ping")) {
                                    ConnectionManager.INSTANCE.handlePcMessage(jsonObject2)
                                    obj2 = obj3
                                    }
                                }
                            i3 = 0
                            obj4 = obj3
                            coroutineScope2 = coroutineScope
                            if (i3 >= 5) {
                                Log.w("PhoneHub", "连续失败 " + i3 + " 次，退避 5 秒后重试")
                                this.L$0 = coroutineScope2
                                this.I$0 = 0
                                this.label = 3
                                if (DelayKt.delay(CoroutineLiveDataKt.DEFAULT_TIMEOUT, this) == coroutine_suspended) {
                                    var coroutine_suspended: return? = null
                                    }
                                i = 0
                                coroutineScope = coroutineScope2
                                obj2 = obj4
                                } else if (i3 > 0) {
                                this.L$0 = coroutineScope2
                                this.I$0 = i3
                                this.label = 4
                                if (DelayKt.delay(i3 * 1000, this) == coroutine_suspended) {
                                    var coroutine_suspended: return? = null
                                    }
                                i = i3
                                coroutineScope = coroutineScope2
                                obj2 = obj4
                                } else {
                                this.L$0 = coroutineScope2
                                this.I$0 = i3
                                this.label = 5
                                if (DelayKt.delay(100L, this) == coroutine_suspended) {
                                    var coroutine_suspended: return? = null
                                    }
                                i = i3
                                coroutineScope = coroutineScope2
                                obj2 = obj4
                                }
                            }
                        if (CoroutineScopeKt.isActive(coroutineScope)) {
                            return Unit.INSTANCE
                            }
                        } else {
                        val i7: Int = i + 1
                        Boxing.boxInt(Log.w("PhoneHub", "轮询失败 HTTP " + (httpResponse2 != null ? httpResponse2.getStatus() : null) + ", failCount=" + i7))
                        obj4 = obj3
                        i3 = i7
                        coroutineScope2 = coroutineScope
                        if (i3 >= 5) {
                            }
                        if (CoroutineScopeKt.isActive(coroutineScope)) {
                            }
                        }
                    } else {
                    obj3 = obj2
                    httpResponse = null
                    httpResponse2 = httpResponse
                    j2 = System.currentTimeMillis() - j3
                    if (httpResponse2 != null) {
                        }
                    if (Intrinsics.areEqual(status, HttpStatusCode.INSTANCE.getOK())) {
                        }
                    }
                }
            break
            case 1:
            obj2 = obj
            val j4: Long = this.J$0
            i2 = this.I$0
            val coroutineScope4: CoroutineScope = (CoroutineScope) this.L$0
            try {
                ResultKt.throwOnFailure(obj2)
                obj3 = obj2
                coroutineScope = coroutineScope4
                j = j4
                } catch (Exception e4) {
                e = e4
                i = i2
                coroutineScope = coroutineScope4
                val i6222: Int = i + 1
                Boxing.boxInt(Log.w("PhoneHub", "轮询异常: " + e.getMessage() + ", failCount=" + i6222))
                obj4 = obj2
                i3 = i6222
                coroutineScope2 = coroutineScope
                if (i3 >= 5) {
                    }
                if (CoroutineScopeKt.isActive(coroutineScope)) {
                    }
                }
            httpResponse = (HttpResponse) obj2
            i = i2
            j3 = j
            httpResponse2 = httpResponse
            j2 = System.currentTimeMillis() - j3
            if (httpResponse2 != null) {
                }
            if (Intrinsics.areEqual(status, HttpStatusCode.INSTANCE.getOK())) {
                }
            break
            case 2:
            obj2 = obj
            j2 = this.J$0
            i = this.I$0
            coroutineScope = (CoroutineScope) this.L$0
            try {
                ResultKt.throwOnFailure(obj2)
                obj3 = obj2
                jsonObject = null
                } catch (Exception e5) {
                e = e5
                val i62222: Int = i + 1
                Boxing.boxInt(Log.w("PhoneHub", "轮询异常: " + e.getMessage() + ", failCount=" + i62222))
                obj4 = obj2
                i3 = i62222
                coroutineScope2 = coroutineScope
                if (i3 >= 5) {
                    }
                if (CoroutineScopeKt.isActive(coroutineScope)) {
                    }
                }
            val jsonObject32: JsonObject = JsonElementKt.getJsonObject(Json.INSTANCE.parseToJsonElement((String) obj2))
            jsonElement = (JsonElement) jsonObject32.get((Object) "status_info")
            if ((jsonElement == null ? JsonElementKt.getJsonObject(jsonElement) : jsonObject) != null) {
                }
            val jsonElement22: JsonElement = (JsonElement) jsonObject32.get((Object) "msgs")
            if (jsonElement22 == null) {
                }
            if (jsonArray == 0) {
                break
                }
            val jsonElement42: JsonElement = (JsonElement) jsonObject32.get((Object) NotificationCompat.CATEGORY_MESSAGE)
            if (jsonElement42 == null) {
                }
            if (jsonObject2 != null) {
                }
            i3 = 0
            obj4 = obj3
            coroutineScope2 = coroutineScope
            if (i3 >= 5) {
                }
            if (CoroutineScopeKt.isActive(coroutineScope)) {
                }
            break
            case 3:
            val i8: Int = this.I$0
            val coroutineScope5: CoroutineScope = (CoroutineScope) this.L$0
            ResultKt.throwOnFailure(obj)
            i = i8
            coroutineScope = coroutineScope5
            obj2 = obj
            if (CoroutineScopeKt.isActive(coroutineScope)) {
                }
            break
            case 4:
            val i9: Int = this.I$0
            val coroutineScope6: CoroutineScope = (CoroutineScope) this.L$0
            ResultKt.throwOnFailure(obj)
            i = i9
            coroutineScope = coroutineScope6
            obj2 = obj
            if (CoroutineScopeKt.isActive(coroutineScope)) {
                }
            break
            case 5:
            val i10: Int = this.I$0
            val coroutineScope7: CoroutineScope = (CoroutineScope) this.L$0
            ResultKt.throwOnFailure(obj)
            i = i10
            coroutineScope = coroutineScope7
            obj2 = obj
            if (CoroutineScopeKt.isActive(coroutineScope)) {
                }
            break
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }

    public static final Unit invokeSuspend$lambda$1$lambda$0(HttpTimeout.HttpTimeoutCapabilityConfiguration $this$timeout) {
        $this$timeout.setRequestTimeoutMillis(8000L)
        return Unit.INSTANCE
        }
    }
