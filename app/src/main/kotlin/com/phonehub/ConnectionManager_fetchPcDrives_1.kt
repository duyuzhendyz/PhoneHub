package com.phonehub

import android.util.Log
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class ConnectionManager_fetchPcDrives_1(
    private val callback: (List<ConnectionManager.PcDriveInfo>) -> Unit,
    continuation: Continuation<Unit>
) : SuspendLambda(2, continuation) {

    var label: Int = 0

    override fun create(obj: Any, continuation: Continuation<*>): Continuation<Unit> {
        return ConnectionManager_fetchPcDrives_1(this.callback, continuation)
    }

    override fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<Unit>): Any {
        return (create(coroutineScope, continuation) as ConnectionManager_fetchPcDrives_1).invokeSuspend(Unit)
    }

    override fun invokeSuspend(result: Any): Any {
        val coroutine_suspended = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        when (label) {
            0 -> {
                ResultKt.throwOnFailure(result)
                val baseUrl = ConnectionManager.INSTANCE.getBaseUrl()
                val httpClient = ConnectionManager.client
                if (httpClient == null) {
                    callback(emptyList())
                    return Unit
                }
                val httpRequestBuilder = HttpRequestBuilder()
                httpRequestBuilder.url("$baseUrl/api/pc_drives")
                httpRequestBuilder.method = HttpMethod.Get
                this.label = 1
                val execute = httpClient.execute(httpRequestBuilder, this)
                if (execute == coroutine_suspended) {
                    return coroutine_suspended
                }
            }
            1 -> {
                ResultKt.throwOnFailure(result)
                val httpResponse = result as io.ktor.client.statement.HttpResponse
                if (httpResponse.status.value !in 200..299) {
                    callback(emptyList())
                    return Unit
                }
                this.label = 2
                val bodyAsText = httpResponse.bodyAsText(this)
                if (bodyAsText == coroutine_suspended) {
                    return coroutine_suspended
                }
            }
            2 -> {
                ResultKt.throwOnFailure(result)
                val bodyText = result as String
                val drivesList = mutableListOf<ConnectionManager.PcDriveInfo>()
                try {
                    val jsonElement = Json.parseToJsonElement(bodyText)
                    val drivesArray: JsonArray = jsonElement.jsonObject["drives"]?.jsonArray ?: JsonArray(emptyList())
                    for (driveElem in drivesArray) {
                        val driveObj = driveElem.jsonObject
                        val name = (driveObj["name"] as? JsonPrimitive)?.content ?: ""
                        val driveLabel = (driveObj["label"] as? JsonPrimitive)?.content ?: ""
                        val total = (driveObj["total"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
                        val used = (driveObj["used"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
                        val free = (driveObj["free"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
                        drivesList.add(ConnectionManager.PcDriveInfo(name, driveLabel, total, used, free))
                    }
                } catch (e: Exception) {
                    Log.e("PhoneHub", "fetchPcDrives parse failed", e)
                }
                callback(drivesList)
            }
            else -> throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
        }
        return Unit
    }
}
