package com.kgapp.encryptionchat.data.api

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import java.util.concurrent.TimeUnit

class SseChatApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) {
    companion object {
        private const val USER_AGENT = "my-bot/1.0"
    }

    fun openStream(fields: Map<String, String>): Call {
        val bodyBuilder = FormBody.Builder()
        fields.forEach { (key, value) -> bodyBuilder.add(key, value) }
        val request = Request.Builder()
            .url(ChatApi.SERVER_API)
            .post(bodyBuilder.build())
            .header("User-Agent", USER_AGENT)
            .build()
        return client.newCall(request)
    }
}
