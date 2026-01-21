package com.kgapp.encryptionchat.sdk.api

import com.kgapp.encryptionchat.data.crypto.CryptoManager
import com.kgapp.encryptionchat.sdk.crypto.CanonicalJson
import com.kgapp.encryptionchat.sdk.crypto.RsaUtil
import com.kgapp.encryptionchat.sdk.model.RequestData
import org.json.JSONArray
import org.json.JSONObject

class RequestBuilder(private val crypto: CryptoManager) {
    private val rsaUtil = RsaUtil(crypto)

    fun buildSignedRequest(data: RequestData): String? {
        return buildSignedRequest(data.toMap())
    }

    fun buildSignedRequest(data: Map<String, Any?>): String? {
        val pub = crypto.computePemBase64() ?: return null
        val payload = data.toMutableMap()
        if (!payload.containsKey("type")) return null
        payload["pub"] = pub
        if (!payload.containsKey("ts")) {
            payload["ts"] = System.currentTimeMillis() / 1000L
        }
        val canonical = CanonicalJson.canonicalize(payload)
        val sig = rsaUtil.sign(canonical)
        if (sig.isBlank()) return null
        val envelope = JSONObject()
        envelope.put("sig", sig)
        envelope.put("data", toJsonValue(payload))
        return envelope.toString()
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
