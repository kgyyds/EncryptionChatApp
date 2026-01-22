package com.kgapp.encryptionchat.data.api

import com.kgapp.encryptionchat.BuildConfig
import com.kgapp.encryptionchat.data.crypto.CryptoManager
import com.kgapp.encryptionchat.data.crypto.ProtocolCanonicalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import java.net.UnknownServiceException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.atomic.AtomicLong
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
        private const val TAG = "Api4Client"
    }

    private val serverTimeOffsetSec = AtomicLong(0L)

    suspend fun postJsonEnvelope(data: Map<String, Any>): ApiResult<JSONObject> = withContext(Dispatchers.IO) {
        val signed = buildSignedRequest(data)
            ?: return@withContext ApiResult.Failure("本地密钥缺失")
        val request = Request.Builder()
            .url(resolveApiUrl())
            .post(signed.body)
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json; charset=utf-8")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                updateServerTimeOffsetFromResponseHeader(response.header("Date"))
                val responseText = response.body?.string()
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "API response code=${response.code} body=${responseText?.take(300)}"
                    )
                }
                if (!response.isSuccessful) {
                    return@withContext ApiResult.Failure("网络请求失败: ${response.code}")
                }
                val text = responseText ?: return@withContext ApiResult.Failure("网络响应为空")
                return@withContext ApiResult.Success(JSONObject(text))
            }
        } catch (ex: UnknownServiceException) {
            return@withContext ApiResult.Failure("不允许明文连接，请检查网络配置")
        } catch (ex: Exception) {
            return@withContext ApiResult.Failure("网络请求失败: ${ex.message ?: "未知错误"}")
        }
    }

    fun openSseStream(data: Map<String, Any>): Call? {
        val signed = buildSignedRequest(data) ?: return null
        val request = Request.Builder()
            .url(resolveApiUrl())
            .post(signed.body)
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json; charset=utf-8")
            .build()
        return sseClient.newCall(request)
    }

    suspend fun sendMsg(
        recipient: String,
        msg: String,
        key: String
    ): ApiResult<JSONObject> {
        val payload = buildDataMap(
            type = "SendMsg",
            fields = mapOf(
                "recipient" to recipient,
                "msg" to msg,
                "key" to key
            )
        )
        return postJsonEnvelope(payload)
    }

    suspend fun getMsg(from: String, lastTs: Long): ApiResult<JSONObject> {
        val payload = buildDataMap(
            type = "GetMsg",
            fields = mapOf(
                "from" to from,
                "last_ts" to lastTs
            )
        )
        return postJsonEnvelope(payload)
    }

    fun openSseMsg(from: String, lastTs: Long): Call? {
        val payload = buildDataMap(
            type = "SseMsg",
            fields = mapOf(
                "from" to from,
                "last_ts" to lastTs
            )
        )
        return openSseStream(payload)
    }

    fun openSseAllMsg(contacts: List<Map<String, Any>>): Call? {
        val payload = buildDataMap(
            type = "SseAllMsg",
            fields = mapOf("contacts" to contacts)
        )
        return openSseStream(payload)
    }

    private fun buildSignedRequest(data: Map<String, Any>): SignedRequest? {
        val pub = crypto.computePemBase64() ?: return null
        val payload = data.toMutableMap()
        if (!payload.containsKey("type")) return null
        payload["pub"] = pub
        payload["ts"] = currentServerUnixSeconds()
        val dataJson = ProtocolCanonicalizer.canonicalStringForSigning(payload)
        val uid = crypto.computeUidFromPubBase64(pub)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Request type=${payload["type"]} ts=${payload["ts"]} uid=$uid")
            Log.d(TAG, "Canonical dataJson=${dataJson.take(200)}")
        }
        val sig = crypto.signDataJson(dataJson)
        if (sig.isBlank()) return null
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Signature (base64)=${sig.take(40)}")
        }
        val envelope = JSONObject()
        envelope.put("sig", sig)
        envelope.put("data", toJsonValue(payload))
        return SignedRequest(
            body = envelope.toString().toRequestBody(JSON_MEDIA_TYPE),
            dataJson = dataJson,
            signature = sig
        )
    }

    private fun buildDataMap(type: String, fields: Map<String, Any?>): Map<String, Any> {
        val payload = mutableMapOf<String, Any>("type" to type)
        fields.forEach { (key, value) ->
            if (value != null) {
                payload[key] = value
            }
        }
        return payload
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

    private data class SignedRequest(
        val body: okhttp3.RequestBody,
        val dataJson: String,
        val signature: String
    )

    private fun currentServerUnixSeconds(): Long {
        val offset = serverTimeOffsetSec.get()
        return (System.currentTimeMillis() / 1000L) + offset
    }

    fun updateServerTimeOffsetFromResponseHeader(dateHeader: String?) {
        if (dateHeader.isNullOrBlank()) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Server Date header missing, using local time")
            }
            return
        }
        try {
            val parsed = ZonedDateTime.parse(dateHeader, DateTimeFormatter.RFC_1123_DATE_TIME)
            val serverEpoch = parsed.toInstant().epochSecond
            val localEpoch = System.currentTimeMillis() / 1000L
            serverTimeOffsetSec.set(serverEpoch - localEpoch)
        } catch (ex: DateTimeParseException) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Failed to parse server Date header: $dateHeader")
            }
        }
    }
}
