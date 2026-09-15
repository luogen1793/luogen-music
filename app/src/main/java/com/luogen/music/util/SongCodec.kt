package com.luogen.music.util

import com.luogen.music.domain.model.Song
import kotlinx.serialization.json.Json

/** Song 编解码（导航路由传参） */
private val songCodec = Json { ignoreUnknownKeys = true }

fun implodeSong(song: Song): String = songCodec.encodeToString(Song.serializer(), song)

fun explodeSong(json: String): Song? = runCatching { songCodec.decodeFromString(Song.serializer(), json) }.getOrNull()