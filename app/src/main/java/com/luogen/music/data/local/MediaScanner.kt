package com.luogen.music.data.local

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.luogen.music.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/** 本地媒体库 */
data class LocalLibrary(
    val music: List<Song> = emptyList(),
    val videos: List<Song> = emptyList(),
    val scannedAt: Long = 0,
)

/**
 * 本地音乐 / 视频自动识别：
 * 通过 MediaStore 扫描系统媒体库，真实读取设备上的音频与视频文件；
 * 并为本地产物生成真实封面（音乐内嵌封面 / 视频首帧缩略图），
 * 缓存到应用 cache 目录，保证列表与播放页显示真实图片封面。
 */
object MediaScanner {

    /** 封面缓存目录名（应用 cache 下） */
    private const val THUMB_DIR = "local_thumbs"

    /** 扫描结果缓存：同一进程重复扫描时复用已生成的封面路径 */
    private val coverCache = ConcurrentHashMap<String, String>()

    suspend fun scan(context: Context): LocalLibrary = withContext(Dispatchers.IO) {
        val music = scanMusic(context)
        val videos = scanVideos(context)
        LocalLibrary(music, videos, System.currentTimeMillis())
    }

    private fun thumbDir(context: Context): File =
        File(context.cacheDir, THUMB_DIR).apply { mkdirs() }

    private fun scanMusic(context: Context): List<Song> {
        val out = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val data = c.getString(dataCol) ?: ""
                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon()
                    .appendPath(id.toString()).build()
                out += Song(
                    id = id,
                    name = c.getString(titleCol) ?: File(data).name,
                    artistNames = listOfNotNull(c.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }),
                    albumName = c.getString(albumCol) ?: "",
                    albumId = c.getLong(albumIdCol),
                    coverUrl = resolveAudioCover(context, id, data, uri),
                    durationMs = c.getLong(durCol),
                    source = "local",
                    localUri = uri.toString(),
                )
            }
        }
        return out
    }

    /**
     * 音频封面解析：
     * 1. 应用缓存中已有生成封面 -> 直接复用；
     * 2. 优先用 MediaMetadataRetriever 读取文件内嵌封面（真实专辑封面，全版本可用）；
     * 3. 兜底尝试 MediaStore Albums 表 ALBUM_ART 列（部分设备仍有效）。
     */
    private fun resolveAudioCover(context: Context, id: Long, data: String, uri: Uri): String {
        val cacheKey = "audio_$id"
        coverCache[cacheKey]?.let { return it }
        val thumb = File(thumbDir(context), "$cacheKey.jpg")
        if (thumb.exists() && thumb.length() > 0) {
            return thumb.toURI().toString().also { coverCache[cacheKey] = it }
        }
        val embedded = runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                if (data.isNotBlank() && File(data).exists()) mmr.setDataSource(data)
                else mmr.setDataSource(context, uri)
                mmr.embeddedPicture
            } finally {
                runCatching { mmr.release() }
            }
        }.getOrNull()
        if (embedded != null && embedded.isNotEmpty()) {
            thumb.parentFile?.mkdirs()
            runCatching { thumb.writeBytes(embedded) }
            if (thumb.exists() && thumb.length() > 0) {
                return thumb.toURI().toString().also { coverCache[cacheKey] = it }
            }
        }
        val legacy = legacyAlbumArt(context, id)
        if (legacy != null) {
            return legacy.also { coverCache[cacheKey] = it }
        }
        return ""
    }

    /** Android 9 及以下设备的专辑封面列（高版本已废弃返回 null） */
    private fun legacyAlbumArt(context: Context, albumId: Long): String? {
        if (Build.VERSION.SDK_INT >= 29 || albumId <= 0L) return null
        return try {
            val projection = arrayOf(MediaStore.Audio.Albums.ALBUM_ART)
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, projection,
                "${MediaStore.Audio.Albums._ID} = ?", arrayOf(albumId.toString()), null
            ) ?: return null
            cursor.use { c ->
                if (c.moveToFirst()) {
                    c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART))
                } else null
            }
        } catch (e: Exception) { null }
    }

    private fun scanVideos(context: Context): List<Song> {
        val out = mutableListOf<Song>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
        )
        context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val data = c.getString(dataCol) ?: ""
                val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon()
                    .appendPath(id.toString()).build()
                out += Song(
                    id = id,
                    name = c.getString(titleCol) ?: File(data).name,
                    durationMs = c.getLong(durCol),
                    source = "local",
                    localUri = uri.toString(),
                    coverUrl = resolveVideoCover(context, id, uri),
                    isVideo = true,
                )
            }
        }
        return out
    }

    /** 视频封面：用系统视频缩略图（不额外解码完整视频帧，速度快、真实可靠） */
    private fun resolveVideoCover(context: Context, id: Long, uri: Uri): String {
        val cacheKey = "video_$id"
        coverCache[cacheKey]?.let { return it }
        val thumb = File(thumbDir(context), "$cacheKey.jpg")
        if (thumb.exists() && thumb.length() > 0) {
            return thumb.toURI().toString().also { coverCache[cacheKey] = it }
        }
        val bmp = runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.loadThumbnail(uri, android.util.Size(720, 480), null)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null
                )
            }
        }.getOrNull()
        if (bmp != null) {
            thumb.parentFile?.mkdirs()
            runCatching {
                FileOutputStream(thumb).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                bmp.recycle()
            }
            if (thumb.exists() && thumb.length() > 0) {
                return thumb.toURI().toString().also { coverCache[cacheKey] = it }
            }
        }
        return ""
    }
}