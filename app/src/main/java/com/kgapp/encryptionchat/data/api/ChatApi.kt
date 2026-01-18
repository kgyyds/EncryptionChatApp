package com.kgapp.encryptionchat.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class ChatApi(
    private val client: OkHttpClient = OkHttpClient()
) {
    companion object {
        const val SERVER_API = "http://47.113.126.123:8890/api/api.php"
        private const val USER_AGENT = "my-bot/1.0"
    }

    suspend fun postForm(fields: Map<String, String>): JSONObject? = withContext(Dispatchers.IO) {
        val bodyBuilder = FormBody.Builder()
        fields.forEach { (key, value) -> bodyBuilder.add(key, value) }
        val request = Request.Builder()
            .url(SERVER_API)
            .post(bodyBuilder.build())
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext null
            }
            val text = response.body?.string() ?: return@withContext null
            return@withContext JSONObject(text)
        }
    }
}
