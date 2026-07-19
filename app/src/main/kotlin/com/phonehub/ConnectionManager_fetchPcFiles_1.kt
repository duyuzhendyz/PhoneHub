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
import io.ktor.http.ContentType
import io.ktor.http.HttpMessagePropertiesKt
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.NullBody
import io.ktor.http.content.OutgoingContent
import io.ktor.util.reflect.TypeInfoJvmKt
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
import kotlin.jvm.functions.Function2
import kotlin.reflect.KType
import kotlin.reflect.TypesJVMKt
import kotlin.text.StringsKt
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
    final  Function2<List<ConnectionManager.PcFileInfo>, String, Unit> $callback
    final  String $path
    var label: Int? = null

    public ConnectionManager$fetchPcFiles$1(String str, Function2<? super List<ConnectionManager.PcFileInfo>, ? super String, Unit> function2, Continuation<? super ConnectionManager$fetchPcFiles$1> continuation) {
        super(2, continuation)
        this.$path = str
        this.$callback = function2
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$fetchPcFiles$1(this.$path, this.$callback, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$fetchPcFiles$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
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
        Function2<List<ConnectionManager.PcFileInfo>, String, Unit> function2
        var obj6: Any? = null
        var str: String? = null
        var jsonPrimitive: JsonPrimitive? = null
        var longOrNull: Long? = null
        var jsonPrimitive2: JsonPrimitive? = null
        var longOrNull2: Long? = null
        var jsonPrimitive3: JsonPrimitive? = null
        var booleanOrNull: Boolean? = null
        var jsonPrimitive4: JsonPrimitive? = null
        var jsonArray: JsonArray? = null
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
            baseUrl = ConnectionManager.INSTANCE.getBaseUrl()
            httpClient = ConnectionManager.client
            if (httpClient == null) {
                obj3 = obj
                httpResponse = null
                if (Intrinsics.areEqual(httpResponse != null ? httpResponse.getStatus() : null, HttpStatusCode.INSTANCE.getOK())) {
                    }
                return Unit.INSTANCE
                }
            val str2: String = this.$path
            val httpRequestBuilder: HttpRequestBuilder = new HttpRequestBuilder()
            HttpRequestKt.url(httpRequestBuilder, baseUrl + "/api/pc_files")
            HttpMessagePropertiesKt.contentType(httpRequestBuilder, ContentType.Application.INSTANCE.getJson())
            val str3: String = "{\"path\":\"" + StringsKt.replace$default(str2, "\\", "\\\\", false, 4, (Object) null) + "\"}"
            if (str3 == null) {
                httpRequestBuilder.setBody(NullBody.INSTANCE)
                val typeOf: KType = Reflection.typeOf(String.class)
                httpRequestBuilder.setBodyType(TypeInfoJvmKt.typeInfoImpl(TypesJVMKt.getJavaType(typeOf), Reflection.getOrCreateKotlinClass(String.class), typeOf))
                } else if (str3 is OutgoingContent) {
                httpRequestBuilder.setBody(str3)
                httpRequestBuilder.setBodyType(null)
                } else {
                httpRequestBuilder.setBody(str3)
                val typeOf2: KType = Reflection.typeOf(String.class)
                httpRequestBuilder.setBodyType(TypeInfoJvmKt.typeInfoImpl(TypesJVMKt.getJavaType(typeOf2), Reflection.getOrCreateKotlinClass(String.class), typeOf2))
                }
            httpRequestBuilder.setMethod(HttpMethod.INSTANCE.getPost())
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
                    val jsonElement: JsonElement = (JsonElement) JsonElementKt.getJsonObject(Json.INSTANCE.parseToJsonElement((String) obj5)).get((Object) "files")
                    val emptyList: List = (jsonElement != null || (jsonArray = JsonElementKt.getJsonArray(jsonElement)) == null) ? CollectionsKt.emptyList() : jsonArray
                    arrayList = ArrayList(CollectionsKt.collectionSizeOrDefault(emptyList, 10))
                    it = emptyList.iterator()
                    while (it.hasNext()) {
                        val jsonObject: JsonObject = JsonElementKt.getJsonObject((JsonElement) it.next())
                        val jsonElement2: JsonElement = (JsonElement) jsonObject.get((Object) ContentDisposition.Parameters.Name)
                        if (jsonElement2 == null || (jsonPrimitive4 = JsonElementKt.getJsonPrimitive(jsonElement2)) == null || (str = JsonElementKt.getContentOrNull(jsonPrimitive4)) == null) {
                            str = ""
                            }
                        val str4: String = str
                        val jsonElement3: JsonElement = (JsonElement) jsonObject.get((Object) "is_dir")
                        val booleanValue: Boolean = (jsonElement3 == null || (jsonPrimitive3 = JsonElementKt.getJsonPrimitive(jsonElement3)) == null || (booleanOrNull = JsonElementKt.getBooleanOrNull(jsonPrimitive3)) == null) ? false : booleanOrNull.booleanValue()
                        val jsonElement4: JsonElement = (JsonElement) jsonObject.get((Object) ContentDisposition.Parameters.Size)
                        val longValue: Long = (jsonElement4 == null || (jsonPrimitive2 = JsonElementKt.getJsonPrimitive(jsonElement4)) == null || (longOrNull2 = JsonElementKt.getLongOrNull(jsonPrimitive2)) == null) ? 0L : longOrNull2.longValue()
                        val jsonElement5: JsonElement = (JsonElement) jsonObject.get((Object) "modified")
                        arrayList.add(new ConnectionManager.PcFileInfo(str4, booleanValue, longValue, (jsonElement5 == null || (jsonPrimitive = JsonElementKt.getJsonPrimitive(jsonElement5)) == null || (longOrNull = JsonElementKt.getLongOrNull(jsonPrimitive)) == null) ? 0L : longOrNull.longValue()))
                        }
                    main = Dispatchers.getMain()
                    function2 = this.$callback
                    this.label = 3
                    if (BuildersKt.withContext(main, AnonymousClass1(function2, arrayList, this.$path, null), this) != coroutine_suspended) {
                        var coroutine_suspended: return? = null
                        }
                    obj6 = obj3
                    }
                } catch (Exception e2) {
                e = e2
                obj2 = obj3
                Log.e("PhoneHub", "fetchPcFiles failed", e)
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
            val jsonElement6: JsonElement = (JsonElement) JsonElementKt.getJsonObject(Json.INSTANCE.parseToJsonElement((String) obj5)).get((Object) "files")
            if (jsonElement6 != null) {
                break
                }
            val emptyList2: List = (jsonElement6 != null || (jsonArray = JsonElementKt.getJsonArray(jsonElement6)) == null) ? CollectionsKt.emptyList() : jsonArray
            arrayList = ArrayList(CollectionsKt.collectionSizeOrDefault(emptyList2, 10))
            it = emptyList2.iterator()
            while (it.hasNext()) {
                }
            main = Dispatchers.getMain()
            function2 = this.$callback
            this.label = 3
            if (BuildersKt.withContext(main, AnonymousClass1(function2, arrayList, this.$path, null), this) != coroutine_suspended) {
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
        final  Function2<List<ConnectionManager.PcFileInfo>, String, Unit> $callback
        final  List<ConnectionManager.PcFileInfo> $files
        final  String $path
        var label: Int? = null

        AnonymousClass1(Function2<? super List<ConnectionManager.PcFileInfo>, ? super String, Unit> function2, List<ConnectionManager.PcFileInfo> list, String str, Continuation<? super AnonymousClass1> continuation) {
            super(2, continuation)
            this.$callback = function2
            this.$files = list
            this.$path = str
            }

        override
        fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
            return AnonymousClass1(this.$callback, this.$files, this.$path, continuation)
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
                this.$callback.invoke(this.$files, this.$path)
                return Unit.INSTANCE
                default:
                throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
                }
            }
        }
    }
