package com.luogen.music.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import com.luogen.music.LuogenApp
import com.luogen.music.data.db.DownloadEntity
import com.luogen.music.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File

/**
 * 媒体操作工具箱：真实下载歌曲（公共音乐库可见）、设为铃声、导入自定义封面
 */
object MediaActions {

    private val json = Json { ignoreUnknownKeys = true }

    /** 旧版本下载缓存目录（仅用于删除时的兼容清理，新下载不再写入） */
    fun legacyDownloadDir(context: Context): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir, "downloads").apply { mkdirs() }

    fun coverDir(context: Context): File =
        File(context.filesDir, "covers").apply { mkdirs() }

    fun isDownloaded(context: Context, songId: Long): Boolean =
        kotlinx.coroutines.runBlocking { LuogenApp.instance.database.dao().download(songId) != null }

    /**
     * 真实下载歌曲到系统公共音乐库（文件管理器可见）：
     * - API 29+：MediaStore RELATIVE_PATH = Music/发现音乐，字节流写入 content URI，返回 content:// 地址
     * - API 28-：直接写公共目录 /Music/发现音乐，DATA 登记媒体库，返回绝对路径
     *
     * 返回落盘位置并记录 Room（已下载列表据此离线播放）。
     */
    suspend fun downloadSong(context: Context, song: Song): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = song.localUri?.takeIf { it.startsWith("http") }
                ?: LuogenApp.instance.repository.songUrl(song.id)
                ?: throw IllegalStateException("暂无可用音源")
            // API 28- 需要存储写权限（API 29+ 走 MediaStore 无需运行时权限）
            if (Build.VERSION.SDK_INT < 29 &&
                context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                throw SecurityException("需要“存储”权限才能下载，请在系统设置中开启")
            }
            val location = if (Build.VERSION.SDK_INT >= 29) {
                downloadToMediaStore(context, song, url)
            } else {
                downloadToPublicFile(context, song, url)
            }
            LuogenApp.instance.database.dao().upsertDownload(
                DownloadEntity(
                    songId = song.id, filePath = location,
                    songJson = json.encodeToString(Song.serializer(), song),
                    downloadedAt = System.currentTimeMillis(),
                )
            )
            location
        }
    }

    /** 删除一条已下载：清理 MediaStore 记录/公共文件 + 旧版私有缓存，Room 记录由调用方删除 */
    fun deleteDownloadFiles(context: Context, e: DownloadEntity) {
        if (e.filePath.startsWith("content://")) {
            // API 29+：直接删除 MediaStore 记录（同时删除磁盘文件）
            runCatching { context.contentResolver.delete(Uri.parse(e.filePath), null, null) }
        } else {
            // 公共目录真实文件
            runCatching { File(e.filePath).delete() }
            // API 28- 媒体库里登记的记录一并清理
            if (Build.VERSION.SDK_INT < 29) {
                runCatching {
                    context.contentResolver.delete(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Audio.Media.DATA}=?",
                        arrayOf(e.filePath),
                    )
                }
            }
        }
        // 兼容旧版本下载在私有目录的缓存文件
        runCatching { File(legacyDownloadDir(context), "song_${e.songId}.mp3").delete() }
    }

    /** API 29+（Android 10+）：MediaStore 插入占位 → 流式写入 → IS_PENDING=0，返回 content uri */
    private fun downloadToMediaStore(context: Context, song: Song, url: String): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "song_${song.id}.mp3")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.TITLE, song.name)
            put(MediaStore.Audio.Media.ARTIST, song.artistNames.joinToString(" / "))
            put(MediaStore.Audio.Media.ALBUM, song.albumName)
            put(MediaStore.Audio.Media.DURATION, song.durationMs)
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/发现音乐")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IllegalStateException("写入系统媒体库失败")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                val client = LuogenApp.instance.repositoryClient()
                client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("下载失败 HTTP ${resp.code}")
                    resp.body?.byteStream()?.use { input ->
                        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                        }
                    } ?: throw IllegalStateException("下载内容为空")
                }
            } ?: throw IllegalStateException("无法写入系统媒体库")
            val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri.toString()
        } catch (e: Exception) {
            // 失败时清理占位记录，避免媒体库残留空壳
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    /** API 28-（Android 9 及以下）：直接写公共 Music 目录并用 DATA 登记媒体库，返回绝对路径 */
    private fun downloadToPublicFile(context: Context, song: Song, url: String): String {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "发现音乐",
        ).apply { mkdirs() }
        val file = File(dir, "song_${song.id}.mp3")
        val client = LuogenApp.instance.repositoryClient()
        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("下载失败 HTTP ${resp.code}")
            resp.body?.byteStream()?.use { input ->
                file.outputStream().use { out -> input.copyTo(out) }
            } ?: throw IllegalStateException("下载内容为空")
        }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.TITLE, song.name)
            put(MediaStore.Audio.Media.ARTIST, song.artistNames.joinToString(" / "))
            put(MediaStore.Audio.Media.ALBUM, song.albumName)
            put(MediaStore.Audio.Media.DATA, file.absolutePath)
            put(MediaStore.Audio.Media.DURATION, song.durationMs)
        }
        runCatching { context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) }
        return file.absolutePath
    }

    /**
     * 设为手机铃声：下载/本地音频 → 写入系统铃声库（API 29+ 走 MediaStore 流式写入，
     * API 28- 复制到公共 Ringtones 目录）→ 设为默认来电铃声。
     *
     * 需要 WRITE_SETTINGS 权限（未授权时返回原因，可跳转授权页）。
     * 注意：MediaStore 插入后必须把音频字节真实写入（openOutputStream + IS_PENDING 流程），
     * 否则生成的铃声条目为空壳，系统仍播放默认铃声。
     */
    suspend fun setAsRingtone(context: Context, song: Song): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(context)) {
                throw SecurityException("需要“修改系统设置”权限，请先授权")
            }
            // 1) 准备音频字节：本地歌曲直接用本地文件流，在线歌曲先下载缓存
            val cache = File(context.filesDir, "ringtone_${song.id}.mp3")
            if (song.localUri != null && !song.localUri.startsWith("http")) {
                // 本地 content:// 或绝对路径直接读取
                val local = runCatching { context.contentResolver.openInputStream(Uri.parse(song.localUri)) }
                    .getOrNull() ?: runCatching { File(song.localUri).inputStream() }.getOrNull()
                    ?: throw IllegalStateException("本地文件不可读")
                local.use { input -> cache.outputStream().use { out -> input.copyTo(out) } }
            } else if (!cache.exists() || cache.length() < 1000) {
                val url = song.localUri?.takeIf { it.startsWith("http") }
                    ?: LuogenApp.instance.repository.songUrl(song.id)
                    ?: throw IllegalStateException("暂无可用音源")
                val client = LuogenApp.instance.repositoryClient()
                client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("下载失败")
                    resp.body?.byteStream()?.use { input ->
                        cache.outputStream().use { out -> input.copyTo(out) }
                    }
                }
            }
            // 2) 写入系统铃声库并设为默认铃声
            val uri = if (Build.VERSION.SDK_INT >= 29) {
                ringtoneToMediaStore(context, song, cache)
            } else {
                ringtoneToPublicFile(context, song, cache)
            }
            RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
            true
        }
    }

    /** API 29+：MediaStore 占位(IS_PENDING=1) → 流式写入音频字节 → IS_PENDING=0，返回 content uri */
    private fun ringtoneToMediaStore(context: Context, song: Song, src: File): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "发现音乐_${sanitizeFileName(song.name)}.mp3")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.TITLE, song.name)
            put(MediaStore.Audio.Media.ARTIST, song.artistNames.joinToString(" / "))
            put(MediaStore.Audio.Media.IS_RINGTONE, 1)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, 0)
            put(MediaStore.Audio.Media.IS_ALARM, 0)
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES + "/发现音乐")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IllegalStateException("写入系统铃声库失败")
        try {
            resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { input -> input.copyTo(out) } }
                ?: throw IllegalStateException("写入系统铃声库失败")
            val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    /** API 28-：复制到公共 Ringtones 目录并以 DATA 登记媒体库，返回 file uri */
    private fun ringtoneToPublicFile(context: Context, song: Song, src: File): Uri {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES),
            "发现音乐",
        ).apply { mkdirs() }
        val dest = File(dir, "发现音乐_${sanitizeFileName(song.name)}.mp3")
        src.inputStream().use { input -> dest.outputStream().use { out -> input.copyTo(out) } }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, dest.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.TITLE, song.name)
            put(MediaStore.Audio.Media.ARTIST, song.artistNames.joinToString(" / "))
            put(MediaStore.Audio.Media.IS_RINGTONE, 1)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, 0)
            put(MediaStore.Audio.Media.IS_ALARM, 0)
            put(MediaStore.Audio.Media.DATA, dest.absolutePath)
        }
        val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("写入系统铃声库失败")
        return uri
    }

    /** 文件名安全化：去掉路径分隔符与非法字符 */
    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").ifBlank { "铃声" }

    /** 导入自定义封面图片：复制到私有目录并返回绝对路径（落盘前压缩重采样，防止大图 OOM） */
    suspend fun importCover(context: Context, songId: Long, src: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dest = File(coverDir(context), "cover_$songId.jpg")
            importScaledImage(context, src, dest, maxSide = 1024)
            dest.absolutePath
        }
    }

    /** 导入用户头像：压缩重采样到 ≤512px 后写入私有目录，返回绝对路径 */
    suspend fun importAvatar(context: Context, src: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dest = File(coverDir(context), "avatar.jpg")
            importScaledImage(context, src, dest, maxSide = 512)
            dest.absolutePath
        }
    }

    /** 通用图片缩放落盘：探测尺寸 → 按 maxSide 采样 → JPEG 92 压缩写入 */
    private fun importScaledImage(context: Context, src: Uri, dest: File, maxSide: Int) {
        context.contentResolver.openInputStream(src)?.use { input ->
            // 先探测原图尺寸，按比例采样，避免直存几千万像素原图（解码 OOM 直接杀进程）
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IllegalStateException("无法解析图片")
            }
            val maxSideOfImage = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (maxSideOfImage / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            // 探界后流已到 EOF，必须重新打开再解码
            val bmp = context.contentResolver.openInputStream(src)?.use { input2 ->
                BitmapFactory.decodeStream(input2, null, opts)
            } ?: throw IllegalStateException("图片解码失败")
            dest.outputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (!bmp.isRecycled) bmp.recycle()
        } ?: throw IllegalStateException("无法读取图片")
    }

    /** 校验自定义封面文件是否存在 */
    fun coverFileExists(context: Context, path: String): Boolean = File(path).exists()
}

/** 给 Application 扩展一个携带当前服务器配置的 OkHttpClient（供下载等复用） */
fun com.luogen.music.LuogenApp.repositoryClient(): okhttp3.OkHttpClient {
    return okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}