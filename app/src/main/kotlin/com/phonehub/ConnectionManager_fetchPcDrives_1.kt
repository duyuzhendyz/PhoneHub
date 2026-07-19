package com.phonehub

import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import com.phonehub.ConnectionManager
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestKt
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpResponseKt
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import java.util.ArrayList
import java.util.Iterator
import java.util.List
import kotlin.ResultKt
import kotlin.Unit
import kotlin.collections.CollectionsKt
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function1
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.BuildersKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonElementKt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ConnectionManager {
    final  Function1<List<ConnectionManager.PcDriveInfo>, Unit> $callback
    var label: Int? = null

    public ConnectionManager$fetchPcDrives$1(Function1<? super List<ConnectionManager.PcDriveInfo>, Unit> function1, Continuation<? super ConnectionManager$fetchPcDrives$1> continuation) {
        super(2, continuation)
        this.$callback = function1
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$fetchPcDrives$1(this.$callback, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$fetchPcDrives$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    override
    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun invokeSuspend(obj: Any): Any {
        var obj2: Any? = null
        var baseUrl: String? = null
        var httpClient: HttpClient? = null
        var obj3: Any? = null
        var httpResponse: HttpResponse? = null
        var obj4: Any? = null
        var obj5: Any? = null
        var arrayList: ArrayList? = null
        var it: Iterator? = null
        var main: MainCoroutineDispatcher? = null
        Function1<List<ConnectionManager.PcDriveInfo>, Unit> function1
        var obj6: Any? = null
        var str: String? = null
        var jsonElement: JsonElement? = null
        var str2: String? = null
        var jsonPrimitive: JsonPrimitive? = null
        var longOrNull: Long? = null
        var jsonPrimitive2: JsonPrimitive? = null
        var longOrNull2: Long? = null
        var jsonPrimitive3: JsonPrimitive? = null
        var longOrNull3: Long? = null
        var jsonPrimitive4: JsonPrimitive? = null
        var contentOrNull: String? = null
        var jsonPrimitive5: JsonPrimitive? = null
        var contentOrNull2: String? = null
        var jsonArray: JsonArray? = null
        var unused: String? = null
        val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        val i: Int = this.label
        try {
            } catch (Exception e) {
            e = e
            obj2 = i
            }
        switch (i) {
            case 0:
            ResultKt.throwOnFailure(obj)
            unused = ConnectionManager.pcIp
            baseUrl = ConnectionManager.INSTANCE.getBaseUrl()
            httpClient = ConnectionManager.client
            if (httpClient == null) {
                obj3 = obj
                httpResponse = null
                if (Intrinsics.areEqual(httpResponse != null ? httpResponse.getStatus() : null, HttpStatusCode.INSTANCE.getOK())) {
                    }
                return Unit.INSTANCE
                }
            val httpRequestBuilder: HttpRequestBuilder = new HttpRequestBuilder()
            HttpRequestKt.url(httpRequestBuilder, baseUrl + "/api/pc_drives")
            httpRequestBuilder.setMethod(HttpMethod.INSTANCE.getGet())
            this.label = 1
            val execute: Any = new HttpStatement(httpRequestBuilder, httpClient).execute(this)
            if (execute == coroutine_suspended) {
                var coroutine_suspended: return? = null
                }
            obj3 = obj
            obj4 = execute
            try {
                httpResponse = (HttpResponse) obj4
                if (Intrinsics.areEqual(httpResponse != null ? httpResponse.getStatus() : null, HttpStatusCode.INSTANCE.getOK())) {
                    this.label = 2
                    Object bodyAsText$default = HttpResponseKt.bodyAsText$default(httpResponse, null, this, 1, null)
                    if (bodyAsText$default == coroutine_suspended) {
                        var coroutine_suspended: return? = null
                        }
                    obj5 = bodyAsText$default
                    val jsonElement2: JsonElement = (JsonElement) JsonElementKt.getJsonObject(Json.INSTANCE.parseToJsonElement((String) obj5)).get((Object) "drives")
                    val emptyList: List = (jsonElement2 != null || (jsonArray = JsonElementKt.getJsonArray(jsonElement2)) == null) ? CollectionsKt.emptyList() : jsonArray
                    arrayList = ArrayList(CollectionsKt.collectionSizeOrDefault(emptyList, 10))
                    it = emptyList.iterator()
                    while (it.hasNext()) {
                        val jsonObject: JsonObject = JsonElementKt.getJsonObject((JsonElement) it.next())
                        val jsonElement3: JsonElement = (JsonElement) jsonObject.get((Object) ContentDisposition.Parameters.Name)
                        if (jsonElement3 != null && (jsonPrimitive5 = JsonElementKt.getJsonPrimitive(jsonElement3)) != null && (contentOrNull2 = JsonElementKt.getContentOrNull(jsonPrimitive5)) != null) {
                            str = contentOrNull2
                            jsonElement = (JsonElement) jsonObject.get((Object) "label")
                            if (jsonElement != null && (jsonPrimitive4 = JsonElementKt.getJsonPrimitive(jsonElement)) != null && (contentOrNull = JsonElementKt.getContentOrNull(jsonPrimitive4)) != null) {
                                str2 = contentOrNull
                                val jsonElement4: JsonElement = (JsonElement) jsonObject.get((Object) "total")
                                val longValue: Long = (jsonElement4 != null || (jsonPrimitive3 = JsonElementKt.getJsonPrimitive(jsonElement4)) == null || (longOrNull3 = JsonElementKt.getLongOrNull(jsonPrimitive3)) == null) ? 0L : longOrNull3.longValue()
                                val jsonElement5: JsonElement = (JsonElement) jsonObject.get((Object) "used")
                                val longValue2: Long = (jsonElement5 != null || (jsonPrimitive2 = JsonElementKt.getJsonPrimitive(jsonElement5)) == null || (longOrNull2 = JsonElementKt.getLongOrNull(jsonPrimitive2)) == null) ? 0L : longOrNull2.longValue()
                                val jsonElement6: JsonElement = (JsonElement) jsonObject.get((Object) "free")
                                arrayList.add(new ConnectionManager.PcDriveInfo(str, str2, longValue, longValue2, (jsonElement6 != null || (jsonPrimitive = JsonElementKt.getJsonPrimitive(jsonElement6)) == null || (longOrNull = JsonElementKt.getLongOrNull(jsonPrimitive)) == null) ? 0L : longOrNull.longValue()))
                                }
                            str2 = ""
                            val jsonElement42: JsonElement = (JsonElement) jsonObject.get((Object) "total")
                            if (jsonElement42 != null) {
                                }
                            val jsonElement52: JsonElement = (JsonElement) jsonObject.get((Object) "used")
                            if (jsonElement52 != null) {
                                }
                            val jsonElement62: JsonElement = (JsonElement) jsonObject.get((Object) "free")
                            arrayList.add(new ConnectionManager.PcDriveInfo(str, str2, longValue, longValue2, (jsonElement62 != null || (jsonPrimitive = JsonElementKt.getJsonPrimitive(jsonElement62)) == null || (longOrNull = JsonElementKt.getLongOrNull(jsonPrimitive)) == null) ? 0L : longOrNull.longValue()))
                            }
                        str = ""
                        jsonElement = (JsonElement) jsonObject.get((Object) "label")
                        if (jsonElement != null) {
                            str2 = contentOrNull
                            val jsonElement422: JsonElement = (JsonElement) jsonObject.get((Object) "total")
                            if (jsonElement422 != null) {
                                }
                            val jsonElement522: JsonElement = (JsonElement) jsonObject.get((Object) "used")
                            if (jsonElement522 != null) {
                                }
                            val jsonElement622: JsonElement = (JsonElement) jsonObject.get((Object) "free")
                            arrayList.add(new ConnectionManager.PcDriveInfo(str, str2, longValue, longValue2, (jsonElement622 != null || (jsonPrimitive = JsonElementKt.getJsonPrimitive(jsonElement622)) == null || (longOrNull = JsonElementKt.getLongOrNull(jsonPrimitive)) == null) ? 0L : longOrNull.longValue()))
                            }
                        str2 = ""
                        val jsonElement4222: JsonElement = (JsonElement) jsonObject.get((Object) "total")
                        if (jsonElement4222 != null) {
                            }
                        val jsonElement5222: JsonElement = (JsonElement) jsonObject.get((Object) "used")
                        if (jsonElement5222 != null) {
                            }
                        val jsonElement6222: JsonElement = (JsonElement) jsonObject.get((Object) "free")
                        arrayList.add(new ConnectionManager.PcDriveInfo(str, str2, longValue, longValue2, (jsonElement6222 != null || (jsonPrimitive = JsonElementKt.getJsonPrimitive(jsonElement6222)) == null || (longOrNull = JsonElementKt.getLongOrNull(jsonPrimitive)) == null) ? 0L : longOrNull.longValue()))
                        }
                    main = Dispatchers.getMain()
                    function1 = this.$callback
                    this.label = 3
                    if (BuildersKt.withContext(main, AnonymousClass1(function1, arrayList, null), this) != coroutine_suspended) {
                        var coroutine_suspended: return? = null
                        }
                    obj6 = obj3
                    }
                } catch (Exception e2) {
                e = e2
                obj2 = obj3
                Log.e("PhoneHub", "fetchPcDrives failed", e)
                return Unit.INSTANCE
                }
            return Unit.INSTANCE
            case 1:
            obj4 = obj
            ResultKt.throwOnFailure(obj4)
            obj3 = obj4
            httpResponse = (HttpResponse) obj4
            if (Intrinsics.areEqual(httpResponse != null ? httpResponse.getStatus() : null, HttpStatusCode.INSTANCE.getOK())) {
                }
            return Unit.INSTANCE
            case 2:
            obj5 = obj
            ResultKt.throwOnFailure(obj5)
            obj3 = obj5
            val jsonElement22: JsonElement = (JsonElement) JsonElementKt.getJsonObject(Json.INSTANCE.parseToJsonElement((String) obj5)).get((Object) "drives")
            if (jsonElement22 != null) {
                break
                }
            val emptyList2: List = (jsonElement22 != null || (jsonArray = JsonElementKt.getJsonArray(jsonElement22)) == null) ? CollectionsKt.emptyList() : jsonArray
            arrayList = ArrayList(CollectionsKt.collectionSizeOrDefault(emptyList2, 10))
            it = emptyList2.iterator()
            while (it.hasNext()) {
                }
            main = Dispatchers.getMain()
            function1 = this.$callback
            this.label = 3
            if (BuildersKt.withContext(main, AnonymousClass1(function1, arrayList, null), this) != coroutine_suspended) {
                }
            break
            case 3:
            obj6 = obj
            ResultKt.throwOnFailure(obj6)
            return Unit.INSTANCE
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }

    public static final class AnonymousClass1 extends SuspendLambda implements Function2<CoroutineScope, Continuation<? super Unit>, Object> {
        final  Function1<List<ConnectionManager.PcDriveInfo>, Unit> $callback
        final  List<ConnectionManager.PcDriveInfo> $drives
        var label: Int? = null

        AnonymousClass1(Function1<? super List<ConnectionManager.PcDriveInfo>, Unit> function1, List<ConnectionManager.PcDriveInfo> list, Continuation<? super AnonymousClass1> continuation) {
            super(2, continuation)
            this.$callback = function1
            this.$drives = list
            }

        override
        fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
            return AnonymousClass1(this.$callback, this.$drives, continuation)
            }

        override
        fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
            return ((AnonymousClass1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
            }

        override
        fun invokeSuspend(obj: Any): Any {
            IntrinsicsKt.getCOROUTINE_SUSPENDED()
            switch (this.label) {
                case 0:
                ResultKt.throwOnFailure(obj)
                this.$callback.invoke(this.$drives)
                return Unit.INSTANCE
                default:
                throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
                }
            }
        }
    }
