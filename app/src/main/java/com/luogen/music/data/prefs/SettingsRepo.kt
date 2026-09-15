package com.luogen.music.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.luogen.music.data.api.ApiHttp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 设置仓库：全部用户可配置项（DataStore 持久化）
 */
class SettingsRepo(private val context: Context) {

    // ---------- 音乐服务器 ----------
    val apiServers: Flow<Set<String>> = context.dataStore.data.map { p ->
        p[stringSetPreferencesKey(KEY_API_SERVERS)] ?: setOf(ApiHttp.DEFAULT_SERVER)
    }

    suspend fun getApiServersSync(): List<String> =
        apiServers.first().toList().ifEmpty { listOf(ApiHttp.DEFAULT_SERVER) }

    suspend fun setApiServers(list: List<String>) {
        context.dataStore.edit { it[stringSetPreferencesKey(KEY_API_SERVERS)] = list.filter { s -> s.isNotBlank() }.toSet() }
    }

    // ---------- 后台播放 ----------
    val backgroundPlay: Flow<Boolean> = context.dataStore.data.map { it[booleanPreferencesKey(KEY_BG_PLAY)] ?: true }
    suspend fun setBackgroundPlay(b: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey(KEY_BG_PLAY)] = b }
    }

    // ---------- 摇一摇 ----------
    val shakeEnabled: Flow<Boolean> = context.dataStore.data.map { it[booleanPreferencesKey(KEY_SHAKE_EN)] ?: true }
    val shakeLeftAction: Flow<String> = context.dataStore.data.map { it[stringPreferencesKey(KEY_SHAKE_L)] ?: "prev" }
    val shakeRightAction: Flow<String> = context.dataStore.data.map { it[stringPreferencesKey(KEY_SHAKE_R)] ?: "next" }

    suspend fun setShakeEnabled(b: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey(KEY_SHAKE_EN)] = b }
    }

    val shakeVibrate: Flow<Boolean> = context.dataStore.data.map { it[booleanPreferencesKey(KEY_SHAKE_VIB)] ?: true }

    /** 一次性写入「左/右」摇一摇动作（已取消「左右各一下」模式） */
    suspend fun setShakeAction(left: String, right: String) {
        context.dataStore.edit {
            it[stringPreferencesKey(KEY_SHAKE_L)] = left
            it[stringPreferencesKey(KEY_SHAKE_R)] = right
        }
    }

    // ---------- 封面样式 ----------
    // round 圆角 / square 方形 / immersive 沉浸 / glass 玻璃
    val coverStyle: Flow<String> = context.dataStore.data.map { it[stringPreferencesKey(KEY_COVER_STYLE)] ?: "round" }
    suspend fun setCoverStyle(style: String) {
        context.dataStore.edit { it[stringPreferencesKey(KEY_COVER_STYLE)] = style }
    }

    // ---------- 一起听 ----------
    val togetherServer: Flow<String> = context.dataStore.data.map { it[stringPreferencesKey(KEY_TG_SERVER)] ?: "" }
    suspend fun setTogetherServer(addr: String) {
        context.dataStore.edit { it[stringPreferencesKey(KEY_TG_SERVER)] = addr }
    }
    val togetherNick: Flow<String> = context.dataStore.data.map { it[stringPreferencesKey(KEY_TG_NICK)] ?: "罗根乐友" }
    suspend fun setTogetherNick(nick: String) {
        context.dataStore.edit { it[stringPreferencesKey(KEY_TG_NICK)] = nick }
    }

    // ---------- AI电台 ----------
    val radioPersona: Flow<String> = context.dataStore.data.map { it[stringPreferencesKey(KEY_RADIO_P)] ?: "HOT" }
    suspend fun setRadioPersona(p: String) {
        context.dataStore.edit { it[stringPreferencesKey(KEY_RADIO_P)] = p }
    }

    // ---------- 听歌识曲服务 ----------
    // 协议：POST multipart/file=env.wav → {"title","artist"}；留空使用内置默认地址
    val recognizeServer: Flow<String> = context.dataStore.data.map { it[stringPreferencesKey(KEY_RECOGNIZE_SERVER)] ?: "" }
    suspend fun setRecognizeServer(addr: String) {
        context.dataStore.edit { it[stringPreferencesKey(KEY_RECOGNIZE_SERVER)] = addr }
    }

    // ---------- 歌词 ----------
    val lyricSync: Flow<Boolean> = context.dataStore.data.map { it[booleanPreferencesKey(KEY_LYRIC)] ?: true }
    suspend fun setLyricSync(b: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey(KEY_LYRIC)] = b }
    }

    /** 「点击歌词退出全屏」引导提示是否已展示过（仅首次全屏显示一次） */
    val lyricHintSeen: Flow<Boolean> = context.dataStore.data.map { it[booleanPreferencesKey(KEY_LYRIC_HINT)] ?: false }
    suspend fun markLyricHintSeen() {
        context.dataStore.edit { it[booleanPreferencesKey(KEY_LYRIC_HINT)] = true }
    }

    // ---------- 我的资料 ----------
    /** 昵称（空 = 显示默认“罗根乐友”） */
    val myNick: Flow<String> = context.dataStore.data.map { p -> p[stringPreferencesKey(KEY_MY_NICK)] ?: "" }
    suspend fun setMyNick(nick: String) {
        context.dataStore.edit { it[stringPreferencesKey(KEY_MY_NICK)] = nick.trim() }
    }
    /** 自定义头像绝对路径（空 = 默认占位头像） */
    val myAvatar: Flow<String> = context.dataStore.data.map { p -> p[stringPreferencesKey(KEY_MY_AVATAR)] ?: "" }
    suspend fun setMyAvatar(path: String) {
        context.dataStore.edit { it[stringPreferencesKey(KEY_MY_AVATAR)] = path }
    }

    // ---------- 多应用同时播放（音频焦点策略） ----------
    val audioFocusMixed: Flow<Boolean> = context.dataStore.data.map { it[booleanPreferencesKey(KEY_MIXED_PLAY)] ?: false }
    suspend fun setAudioFocusMixed(b: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey(KEY_MIXED_PLAY)] = b }
    }

    companion object {
        private const val KEY_API_SERVERS = "api_servers"
        private const val KEY_BG_PLAY = "bg_play"
        private const val KEY_SHAKE_EN = "shake_en"
        private const val KEY_SHAKE_L = "shake_l"
        private const val KEY_SHAKE_R = "shake_r"
                private const val KEY_SHAKE_VIB = "shake_vib"
        private const val KEY_COVER_STYLE = "cover_style"
        private const val KEY_TG_SERVER = "tg_server"
        private const val KEY_TG_NICK = "tg_nick"
        private const val KEY_RADIO_P = "radio_p"
        private const val KEY_LYRIC = "lyric"
        private const val KEY_LYRIC_HINT = "lyric_hint_seen"
        private const val KEY_MY_NICK = "my_nick"
        private const val KEY_MY_AVATAR = "my_avatar"
        private const val KEY_MIXED_PLAY = "mixed_play"
        private const val KEY_RECOGNIZE_SERVER = "recognize_server"

        const val ACTION_PREV = "prev"
        const val ACTION_NEXT = "next"
        const val ACTION_PLAY_PAUSE = "play_pause"
        const val ACTION_NONE = "none"
    }
}