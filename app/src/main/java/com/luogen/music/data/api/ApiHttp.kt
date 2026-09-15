package com.luogen.music.data.api

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * ApiHttp：对 OkHttp 的轻封装，具备多服务器自动故障切换能力。
 * 所有服务器默认使用网络上的 NeteaseCloudMusicApi 协议实现，
 * 用户亦可在设置中自填自部署的服务器地址。
 *
 * 并发策略：不同 URL 的请求完全并行（互不阻塞），相同 URL 的并发请求
 * 自动合并复用第一个进行中的请求（in-flight 去重），避免重复风暴。
 *
 * 内存缓存：数据接口结果缓存 90 秒（返回再进/重复搜索直接命中，秒出；
 * 实测镜像对已有 URL 有上游缓存但我们自己有缓存更稳）。
 * 播放地址类接口（/song/url、/mv/url）不缓存——URL 会过期。
 */
class ApiHttp(
    initialServers: List<String>,
) {
    @Volatile
    var servers: List<String> = initialServers.filter { it.isNotBlank() }.ifEmpty { listOf(DEFAULT_SERVER) }
        private set

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    private var cursor = 0

    /** 进行中的请求（按完整 URL 去重）：同 URL 并发调用共享同一个请求结果 */
    private val inFlight = ConcurrentHashMap<String, Deferred<String?>>()

    /** 90 秒 TTL 缓存：URL -> (时间戳, 响应体) */
    private val cache = ConcurrentHashMap<String, Pair<Long, String>>()
    private val cacheTtlMs = 90_000L

    fun updateServers(list: List<String>) {
        val cleaned = list.filter { it.isNotBlank() }
        if (cleaned.isNotEmpty()) {
            servers = cleaned
        }
    }

    /** 启动预热：提前对高频端点建立 TCP/DNS 连接并填充缓存，首屏首次请求近乎秒出 */
    fun prewarm() {
        try {
            val s = servers.firstOrNull() ?: DEFAULT_SERVER
            // 首页/榜单高频端点：小接口 + 4 大榜单歌曲（track/all 体积小、速度快）
            val paths = mutableListOf("/toplist", "/search/hot", "/personalized/newsong?limit=3")
            listOf(19723756L, 3778678L, 3779629L, 2884035L).forEach { id ->
                paths += "/playlist/track/all?id=$id&limit=10"
            }
            paths.forEach { path ->
                runCatching {
                    val req = Request.Builder().url(s.trimEnd('/') + path)
                        .header("User-Agent", UA)
                        .header("Referer", "https://music.163.com/")
                        .get().build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string() ?: ""
                            // key 与 get() 一致（占位符形式），预热缓存可被首屏直接命中
                            if (body.isNotBlank()) cache["__BASE__" + path] = System.currentTimeMillis() to body
                        }
                    }
                }
            }
        } catch (e: Exception) { /* 预热失败不影响主流程 */ }
    }

    /** 依次尝试服务器；全部失败抛出异常。同 URL 去重，不同 URL 并行 */
    suspend fun get(url: String): String? {
        // 播放地址类接口不缓存（URL 短期有效，缓存会播不出）
        if (!url.contains("/song/url") && !url.contains("/mv/url")) {
            cache[url]?.let { (ts, body) ->
                if (System.currentTimeMillis() - ts < cacheTtlMs) return body
                cache.remove(url)
            }
        }
        inFlight[url]?.let { return it.await() }
        return coroutineScope {
            val d = async(start = CoroutineStart.LAZY) { requestWithFailover(url) }
            inFlight[url] = d
            try {
                val body = d.await()
                if (body != null && !url.contains("/song/url") && !url.contains("/mv/url")) {
                    cache[url] = System.currentTimeMillis() to body
                }
                body
            } finally {
                inFlight.remove(url, d)
            }
        }
    }

    private suspend fun requestWithFailover(url: String): String? {
        val n = servers.size
        var last: Exception? = null
        for (i in 0 until n) {
            val idx = (cursor + i) % n
            val server = servers[idx]
            val full = url.replaceFirst("__BASE__", server.trimEnd('/'))
            try {
                val req = Request.Builder().url(full)
                    .header("User-Agent", UA)
                    .header("Referer", "https://music.163.com/")
                    .get().build()
                client.newCall(req).execute().use { resp ->
                    val code = resp.code
                    val body = resp.body?.string() ?: ""
                    if (code in 200..299 && !body.startsWith("<")) {
                        // 命中：游标前移，优先该服务器
                        cursor = idx
                        return body
                    }
                    last = ApiException("服务器 $server 返回 $code")
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: ApiException("所有服务器不可用")
    }

    companion object {
        const val DEFAULT_SERVER = "http://iwenwiki.com:3000"
        const val UA =
            "Mozilla/5.0 (Linux; Android 14; LuogenMusic) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        /** requestOnce 共享客户端：复用连接池，避免高频调用重复建连 */
        private val onceClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        /** 轻量单次 HTTP 请求（不参与服务器轮询，适用于已指定完整 URL 的调用），失败返回 null */
        fun requestOnce(fullUrl: String, timeoutMs: Long = 9000): String? = runCatching {
            val req = Request.Builder().url(fullUrl)
                .header("User-Agent", UA)
                .header("Referer", "https://music.163.com/")
                .get().build()
            onceClient.newCall(req).execute().use { resp ->
                if (resp.code in 200..299) resp.body?.string()
                else null
            }
        }.getOrNull()
    }
}