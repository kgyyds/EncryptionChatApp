package com.kgapp.encryptionchat.data.api

import com.kgapp.encryptionchat.data.crypto.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.net.UnknownServiceException
import java.util.concurrent.TimeUnit

class Api4Client(
    private val crypto: CryptoManager,
    private val baseUrlProvider: () -> String,
    private val client: OkHttpClient = OkHttpClient(),
    private val sseClient: OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) {
    companion object {
        private const val USER_AGENT = "my-bot/1.0"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    suspend fun postJsonEnvelope(data: Map<String, Any>): ApiResult<JSONObject> = withContext(Dispatchers.IO) {
        val envelope = buildSignedEnvelope(data)
            ?: return@withContext ApiResult.Failure("本地密钥缺失")
        val request = Request.Builder()
            .url(resolveApiUrl())
            .post(envelope.toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json; charset=utf-8")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ApiResult.Failure("网络请求失败: ${response.code}")
                }
                val text = response.body?.string() ?: return@withContext ApiResult.Failure("网络响应为空")
                return@withContext ApiResult.Success(JSONObject(text))
            }
        } catch (ex: UnknownServiceException) {
            return@withContext ApiResult.Failure("不允许明文连接，请检查网络配置")
        } catch (ex: Exception) {
            return@withContext ApiResult.Failure("网络请求失败: ${ex.message ?: "未知错误"}")
        }
    }

    fun openSseStream(data: Map<String, Any>): Call? {
        val envelope = buildSignedEnvelope(data) ?: return null
        val request = Request.Builder()
            .url(resolveApiUrl())
            .post(envelope.toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json; charset=utf-8")
            .build()
        return sseClient.newCall(request)
    }

    private fun buildSignedEnvelope(data: Map<String, Any>): String? {
        val pub = crypto.computePemBase64() ?: return null
        val payload = data.toMutableMap()

        if (!payload.containsKey("ts")) {
            payload["ts"] = (System.currentTimeMillis() / 1000L)
        }
        val dataJson = crypto.canonicalizeDataForSigning(payload)
        val sig = crypto.signDataJson(dataJson)
        if (sig.isBlank()) return null
        val envelope = JSONObject()
        envelope.put("sig", sig)
        envelope.put("pub", pub)
        envelope.put("data", toJsonValue(payload))
        return envelope.toString()
    }

    private fun resolveApiUrl(): String {
        val base = baseUrlProvider().trim().ifBlank { "" }
        if (base.endsWith("/api/api5.php")) return base
        val normalized = if (base.endsWith("/")) base.dropLast(1) else base
        return when {
            normalized.endsWith("/api/api2.php") -> normalized.removeSuffix("/api/api2.php") + "/api/api5.php"
            normalized.endsWith("/api/api3.php") -> normalized.removeSuffix("/api/api3.php") + "/api/api5.php"
            normalized.endsWith("/api/api4.php") -> normalized.removeSuffix("/api/api4.php") + "/api/api5.php"
            normalized.contains("/api/") -> normalized.substringBefore("/api/") + "/api/api5.php"
            else -> "$normalized/api/api5.php"
        }
    }

    private fun toJsonValue(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> {
                val obj = JSONObject()
                value.forEach { (key, entryValue) ->
                    if (key != null) {
                        obj.put(key.toString(), toJsonValue(entryValue))
                    }
                }
                obj
            }
            is Iterable<*> -> {
                val array = JSONArray()
                value.forEach { array.put(toJsonValue(it)) }
                array
            }
            is Array<*> -> {
                val array = JSONArray()
                value.forEach { array.put(toJsonValue(it)) }
                array
            }
            is JSONObject -> value
            is JSONArray -> value
            is Boolean, is Number, is String -> value
            else -> value.toString()
        }
    }
}
