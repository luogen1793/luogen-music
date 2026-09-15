package com.luogen.music.domain.model

import kotlinx.serialization.Serializable

/** 统一歌曲模型：在线(网易云) / 本地 / 下载 通用 */
@Serializable
data class Song(
    val id: Long = 0,
    val name: String = "",
    val artistNames: List<String> = emptyList(),
    val artistIds: List<Long> = emptyList(),
    val albumName: String = "",
    val albumId: Long = 0,
    val coverUrl: String = "",
    val durationMs: Long = 0,
    val fee: Int = 0,          // 0 免费 1 VIP 8 会员
    val mvid: Long = 0,
    val source: String = "netease",   // netease | local | download
    val localUri: String? = null,     // content:// 本地地址
    val popularity: Int = 0,          // 流行度 0-100
    val publishTime: Long = 0,        // 发行时间(ms)
    val lyric: String = "",
    val isVideo: Boolean = false,
)

/** 歌手 */
@Serializable
data class Artist(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String = "",
    val alias: List<String> = emptyList(),
    val musicSize: Int = 0,
    val albumSize: Int = 0,
    val briefDesc: String = "",
    val fans: Long = 0,
)

/** 歌曲评论（网易云 hotComments/comments 通用字段，支持楼中楼） */
@Serializable
data class SongComment(
    val commentId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String = "",
    val content: String = "",
    val time: Long = 0,
    val likedCount: Int = 0,
    /** 被回复的父评论 id（0 = 顶层评论），用于楼中楼分组 */
    val parentId: Long = 0,
    /** 被回复的楼层：原文 + 作者（无则空） */
    val replyTo: String = "",
    val replyToUser: String = "",
    /** 该评论下的楼中楼回复（最多保留若干条） */
    val replies: List<SongComment> = emptyList(),
)

/** 专辑 */
@Serializable
data class Album(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String = "",
    val publishTime: Long = 0,
    val size: Int = 0,
    val artistNames: List<String> = emptyList(),
    val description: String = "",
    val company: String = "",
)

/** MV / 视频 */
@Serializable
data class Video(
    val id: Long = 0,
    val name: String = "",
    val coverUrl: String = "",
    val playCount: Long = 0,
    val artistName: String = "",
    val artistId: Long = 0,
    val durationMs: Long = 0,
    val url: String = "",
)

/** 榜单 */
@Serializable
data class RankList(
    val id: Long = 0,
    val name: String = "",
    val coverUrl: String = "",
    val updateFrequency: String = "",
    val playCount: Long = 0,
    val trackCount: Int = 0,
)

/** 热搜词 */
@Serializable
data class HotWord(
    val word: String = "",
    val hotValue: Long = 0,
    val rank: Int = 0,
    val source: String = "",   // 网易云 | 头条 | B站
)

/** 播放队列项 */
@Serializable
data class QueueItem(
    val song: Song,
    val from: String = "",        // 来源页面
    val addedAt: Long = System.currentTimeMillis(),
)

/** 播放模式：顺序 / 单曲循环 / 列表循环 / 随机 */
enum class PlayMode(val modeName: String) {
    SEQUENCE("顺序播放"),
    REPEAT_ALL("列表循环"),
    REPEAT_ONE("单曲循环"),
    SHUFFLE("随机播放"),
}

/** 一起听消息 */
@Serializable
data class TogetherMessage(
    val from: String = "",
    val nick: String = "",
    val text: String = "",
    val at: Long = System.currentTimeMillis(),
    val system: Boolean = false,
)

/** AI 电台人格 */
enum class RaType(val label: String, val slogan: String) {
    HOT("热辣冲榜官", "专推当下最火的热门金曲"),
    SOUL("知心点歌官", "懂你喜好，推荐风格相似的歌"),
    TREASURE("宝藏挖歌官", "为你挖出小众宝藏歌曲"),
}