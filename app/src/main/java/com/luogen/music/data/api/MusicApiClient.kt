package com.luogen.music.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * 网易云音乐 API 客户端（NeteaseCloudMusicApi 协议）。
 * 所有请求为公开接口，无需登录；服务器可配置并自动故障切换。
 */
class MusicApiClient(private val baseUrl: String, private val http: ApiHttp) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(path: String, params: Map<String, String> = emptyMap()): JsonObject {
        val url = buildUrl(path, params)
        val body = http.get(url) ?: throw ApiException("网络无响应: $path")
        return json.parseToJsonElement(body).jsonObject
    }

    private fun buildUrl(path: String, params: Map<String, String>): String {
        val sb = StringBuilder(baseUrl.trimEnd('/')).append(path)
        if (params.isNotEmpty()) {
            val qs = params.entries.joinToString("&") { (k, v) ->
                k + "=" + java.net.URLEncoder.encode(v, "UTF-8")
            }
            sb.append('?').append(qs)
        }
        return sb.toString()
    }
}

class ApiException(message: String) : Exception(message)