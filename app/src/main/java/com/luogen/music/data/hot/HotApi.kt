package com.luogen.music.data.hot

import com.luogen.music.domain.model.HotWord
import com.luogen.music.data.api.arr
import com.luogen.music.data.api.asArr
import com.luogen.music.data.api.asObj
import com.luogen.music.data.api.str
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 平台热搜聚合（真实实时数据）：
 *  - 今日头条热榜（真实热度值）
 *  - B站热搜搜索榜（真实热搜词）
 * 接口均为各平台公开接口，直接抓取真实榜单。
 */
class HotApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchAll(): List<HotWord> {
        val t = runCatching { toutiao() }.getOrDefault(emptyList())
        val b = runCatching { bilibili() }.getOrDefault(emptyList())
        return t + b
    }

    private suspend fun toutiao(): List<HotWord> = request(
        "https://www.toutiao.com/hot-event/hot-board/?origin=toutiao_pc"
    )?.let { text ->
        val root = json.parseToJsonElement(text).asObj()
        root.arr("data").mapIndexedNotNull { idx, e ->
            val o = e.asObj()
            val title = o.str("Title")
            if (title.isBlank()) null
            else HotWord(title, o.str("HotValue").toLongOrNull() ?: 0L, idx + 1, "头条热榜")
        }
    } ?: emptyList()

    private suspend fun bilibili(): List<HotWord> = request(
        "https://api.bilibili.com/x/web-interface/search/square?limit=20"
    )?.let { text ->
        val root = json.parseToJsonElement(text).asObj()
        root.asObj().let { r -> r["data"]?.asObj()?.get("trending")?.asObj()?.get("list")?.asArr() }
            ?.mapIndexedNotNull { idx, e ->
                val o = e.asObj()
                val kw = o.str("keyword")
                if (kw.isBlank()) null else HotWord(kw, 0L, idx + 1, "B站热搜")
            }
    } ?: emptyList()

    private fun request(url: String): String? {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
            .get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }
}