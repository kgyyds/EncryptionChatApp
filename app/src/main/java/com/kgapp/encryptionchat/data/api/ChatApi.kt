package com.kgapp.encryptionchat.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.UnknownServiceException

class ChatApi(
    private val client: OkHttpClient = OkHttpClient()
) {
    companion object {
        const val SERVER_API = "http://47.113.126.123:8890/api/api.php"
        private const val USER_AGENT = "my-bot/1.0"
    }

    suspend fun postForm(fields: Map<String, String>): ApiResult<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val bodyBuilder = FormBody.Builder()
            fields.forEach { (key, value) -> bodyBuilder.add(key, value) }
            val request = Request.Builder()
                .url(SERVER_API)
                .post(bodyBuilder.build())
                .header("User-Agent", USER_AGENT)
                .build()
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
}
