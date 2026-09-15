package com.luogen.music.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

private val Context.searchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "search_history")

/**
 * 搜索历史：DataStore 持久化最近 12 条搜索关键词（去重、新的在前）。
 */
class SearchHistoryStore(private val context: Context) {
    private val key = stringPreferencesKey("history_json")

    suspend fun load(): List<String> {
        val raw = context.searchHistoryDataStore.data.first()[key] ?: return emptyList()
        return runCatching {
            Json.parseToJsonElement(raw).jsonArray.map { it.jsonPrimitive.content }
        }.getOrDefault(emptyList())
    }

    /** 记录一次搜索（去重 + 置顶），返回更新后的历史 */
    suspend fun append(word: String): List<String> {
        val w = word.trim()
        if (w.isBlank()) return load()
        val list = (listOf(w) + load().filterNot { it == w }).take(12)
        context.searchHistoryDataStore.edit { it[key] = Json.encodeToString(list) }
        return list
    }

    /** 清空搜索历史 */
    suspend fun clear(): List<String> {
        context.searchHistoryDataStore.edit { it.remove(key) }
        return emptyList()
    }
}