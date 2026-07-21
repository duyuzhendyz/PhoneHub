package com.phonehub

import android.util.Log
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
import kotlin.ResultKt
import kotlin.Unit
import kotlin.collections.CollectionsKt
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.jvm.functions.Function2
import kotlin.reflect.TypesJVMKt
import kotlin.reflect.typeOf
import kotlinx.coroutines.BuildersKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class ConnectionManager_fetchPcFiles_1(
    private val path: String,
    private val callback: Function2<List<ConnectionManager.PcFileInfo>, String, Unit>,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_fetchPcFiles_1(this.path, this.callback, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_fetchPcFiles_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        try {
            when (label) {
                0 -> {
                    ResultKt.throwOnFailure(result)
                    val baseUrl = ConnectionManager.INSTANCE.baseUrl
                    val httpClient: HttpClient? = ConnectionManager.client
                    if (httpClient == null) {
                        return Unit
                    }
                    val str2 = this.path
                    val httpRequestBuilder = HttpRequestBuilder()
                    HttpRequestKt.url(httpRequestBuilder, baseUrl + "/api/pc_files")
                    HttpMessagePropertiesKt.contentType(httpRequestBuilder, ContentType.Application.Json)
                    val str3 = "{\"path\":\"" + str2.replace("\\", "\\\\") + "\"}"
                    if (str3 == null) {
                        httpRequestBuilder.setBody(NullBody)
                        val typeOf = typeOf<String>()
                        httpRequestBuilder.setBodyType(
                            TypeInfoJvmKt.typeInfoImpl(
                                TypesJVMKt.getJavaType(typeOf),
                                String::class,
                                typeOf
                            )
                        )
                    } else if (str3 is OutgoingContent) {
                        httpRequestBuilder.setBody(str3)
                        httpRequestBuilder.setBodyType(null)
                    } else {
                        httpRequestBuilder.setBody(str3)
                        val typeOf2 = typeOf<String>()
                        httpRequestBuilder.setBodyType(
                            TypeInfoJvmKt.typeInfoImpl(
                                TypesJVMKt.getJavaType(typeOf2),
                                String::class,
                                typeOf2
                            )
                        )
                    }
                    httpRequestBuilder.method = HttpMethod.Post
                    label = 1
                    val execute = HttpStatement(httpRequestBuilder, httpClient).execute(this)
                    if (execute == coroutine_suspended) {
                        return coroutine_suspended
                    }
                    val httpResponse0 = execute as HttpResponse
                    if (httpResponse0.status != HttpStatusCode.OK) {
                        return Unit
                    }
                    label = 2
                    val bodyText0 = HttpResponseKt.`bodyAsText$default`(httpResponse0, null, this, 1, null)
                    if (bodyText0 == coroutine_suspended) {
                        return coroutine_suspended
                    }
                    val arrayList0 = parseFiles(bodyText0 as String)
                    val main0 = Dispatchers.Main
                    val function20 = this.callback
                    label = 3
                    val withContextResult0 = BuildersKt.withContext(
                        main0,
                        AnonymousClass1(function20, arrayList0, this.path, null),
                        this
                    )
                    if (withContextResult0 == coroutine_suspended) {
                        return coroutine_suspended
                    }
                    return Unit
                }
                1 -> {
                    ResultKt.throwOnFailure(result)
                    val httpResponse = result as HttpResponse
                    if (httpResponse.status != HttpStatusCode.OK) {
                        return Unit
                    }
                    label = 2
                    val bodyText = HttpResponseKt.`bodyAsText$default`(httpResponse, null, this, 1, null)
                    if (bodyText == coroutine_suspended) {
                        return coroutine_suspended
                    }
                    val arrayList = parseFiles(bodyText as String)
                    val main = Dispatchers.Main
                    val function2 = this.callback
                    label = 3
                    val withContextResult = BuildersKt.withContext(
                        main,
                        AnonymousClass1(function2, arrayList, this.path, null),
                        this
                    )
                    if (withContextResult == coroutine_suspended) {
                        return coroutine_suspended
                    }
                    return Unit
                }
                2 -> {
                    ResultKt.throwOnFailure(result)
                    val arrayList2 = parseFiles(result as String)
                    val main2 = Dispatchers.Main
                    val function22 = this.callback
                    label = 3
                    val withContextResult2 = BuildersKt.withContext(
                        main2,
                        AnonymousClass1(function22, arrayList2, this.path, null),
                        this
                    )
                    if (withContextResult2 == coroutine_suspended) {
                        return coroutine_suspended
                    }
                    return Unit
                }
                3 -> {
                    ResultKt.throwOnFailure(result)
                    return Unit
                }
                else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        } catch (e: Exception) {
            Log.e("PhoneHub", "fetchPcFiles failed", e)
            return Unit
        }
        return Unit
    }

    private fun parseFiles(bodyText: String): ArrayList<ConnectionManager.PcFileInfo> {
        val filesElement: JsonElement? = Json.Default.parseToJsonElement(bodyText).jsonObject["files"]
        val emptyList: List<JsonElement> = (filesElement as? JsonArray) ?: CollectionsKt.emptyList()
        val arrayList = ArrayList<ConnectionManager.PcFileInfo>(
            CollectionsKt.collectionSizeOrDefault(emptyList, 10)
        )
        for (item in emptyList) {
            val jsonObject: JsonObject = item.jsonObject
            val jsonElement2: JsonElement? = jsonObject[ContentDisposition.Parameters.Name]
            var str: String? = null
            if (jsonElement2 != null) {
                str = jsonElement2.jsonPrimitive.contentOrNull
            }
            if (str == null) str = ""
            val str4 = str
            val jsonElement3: JsonElement? = jsonObject["is_dir"]
            var booleanValue = false
            if (jsonElement3 != null) {
                val booleanOrNull = jsonElement3.jsonPrimitive.booleanOrNull
                if (booleanOrNull != null) {
                    booleanValue = booleanOrNull
                }
            }
            val jsonElement4: JsonElement? = jsonObject[ContentDisposition.Parameters.Size]
            var longValue: Long = 0L
            if (jsonElement4 != null) {
                val longOrNull2 = jsonElement4.jsonPrimitive.longOrNull
                if (longOrNull2 != null) {
                    longValue = longOrNull2
                }
            }
            val jsonElement5: JsonElement? = jsonObject["modified"]
            var longValue2: Long = 0L
            if (jsonElement5 != null) {
                val longOrNull = jsonElement5.jsonPrimitive.longOrNull
                if (longOrNull != null) {
                    longValue2 = longOrNull
                }
            }
            arrayList.add(ConnectionManager.PcFileInfo(str4, booleanValue, longValue, longValue2))
        }
        return arrayList
    }

    private class AnonymousClass1(
        private val callback: Function2<List<ConnectionManager.PcFileInfo>, String, Unit>,
        private val files: List<ConnectionManager.PcFileInfo>,
        private val path: String,
        continuation: Continuation<*>?
    ) : SuspendLambda(2, continuation) {

        var label: Int = 0

        override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
            return AnonymousClass1(this.callback, this.files, this.path, continuation)
        }

        override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
            return (create(coroutineScope, continuation) as AnonymousClass1).invokeSuspend(Unit)
        }

        override fun invokeSuspend(result: Any): Any {
            IntrinsicsKt.getCOROUTINE_SUSPENDED()
            when (label) {
                0 -> {
                    ResultKt.throwOnFailure(result)
                    this.callback.invoke(this.files, this.path)
                    return Unit
                }
                else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }
    }
}
