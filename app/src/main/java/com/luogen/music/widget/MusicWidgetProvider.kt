package com.luogen.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.luogen.music.R
import com.luogen.music.data.local.CoverStore
import com.luogen.music.domain.model.PlayMode
import com.luogen.music.player.PlayerHub
import com.luogen.music.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File

/**
 * 桌面小组件：上一曲/下一曲/播放暂停/随机/循环切换 + 当前歌曲信息（真实状态）。
 * 封面按「歌曲 ID」独立缓存并支持自定义封面：切歌后不再误显示上一首歌的封面。
 */
class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, manager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            "com.luogen.music.widget.REFRESH",
            Intent.ACTION_BOOT_COMPLETED,
            "android.appwidget.action.APPWIDGET_UPDATE" -> {
                val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                    ComponentName(context, MusicWidgetProvider::class.java)
                )
                ids.forEach { updateWidget(context, AppWidgetManager.getInstance(context), it) }
            }
        }
    }

    companion object {
        /** 状态变化时由 PlaybackService/PlayerHub 调用刷新 */
        fun refresh(context: Context) {
            val intent = Intent(context, MusicWidgetProvider::class.java)
                .setAction("com.luogen.music.widget.REFRESH")
            context.sendBroadcast(intent)
        }

        /** 按歌曲 ID 的封面缓存文件（本地与在线歌曲共用） */
        private fun coverCacheFile(context: Context, songId: Long): File =
            File(context.cacheDir, "widget_cover_${songId}.jpg")
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_music)
        val st = PlayerHub.state.value
        val song = st.current

        views.setTextViewText(R.id.widget_title, song?.name ?: "发现音乐")
        views.setTextViewText(R.id.widget_subtitle, song?.artistNames?.joinToString(" / ") ?: "作者 · 罗根")

        // 封面：自定义封面 > 按歌曲缓存的位图 > 占位图（异步下载后按歌曲 ID 落盘再刷新，
        // setImageViewBitmap 跨进程最可靠，不要用 file:// Uri —— Android 7+ 限制跨应用读取）
        val songId = song?.id ?: 0L
        val coverBmp = localCoverBitmap(context, songId, song?.coverUrl.orEmpty())
        if (coverBmp != null) {
            views.setImageViewBitmap(R.id.widget_cover, coverBmp)
        } else {
            views.setImageViewResource(R.id.widget_cover, R.drawable.widget_cover_placeholder)
            if (!song?.coverUrl.isNullOrBlank() && songId != 0L) {
                val url = song!!.coverUrl
                CoroutineScope(Dispatchers.IO).launch {
                    fetchCover(context, url, songId, widgetId)
                }
            }
        }

        views.setImageViewResource(
            R.id.widget_play,
            if (st.isPlaying) R.drawable.widget_ic_pause else R.drawable.widget_ic_play
        )
        views.setImageViewResource(
            R.id.widget_mode,
            when (st.mode) {
                PlayMode.SEQUENCE -> R.drawable.widget_ic_mode_sequence
                PlayMode.REPEAT_ALL -> R.drawable.widget_ic_mode_repeat_all
                PlayMode.REPEAT_ONE -> R.drawable.widget_ic_mode_repeat_one
                PlayMode.SHUFFLE -> R.drawable.widget_ic_mode_shuffle
            }
        )

        views.setOnClickPendingIntent(R.id.widget_prev, cmd(context, PlaybackService.CMD_PREV, "prev"))
        views.setOnClickPendingIntent(R.id.widget_next, cmd(context, PlaybackService.CMD_NEXT, "next"))
        views.setOnClickPendingIntent(R.id.widget_play, cmd(context, PlaybackService.CMD_PLAY_PAUSE, "play_pause"))
        views.setOnClickPendingIntent(R.id.widget_mode, cmd(context, PlaybackService.CMD_SET_MODE, "mode"))

        // 轻触标题打开 APP
        val openApp = PendingIntent.getActivity(
            context, 0,
            context.packageManager.getLaunchIntentForPackage(context.packageName)!!,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_title, openApp)
        views.setOnClickPendingIntent(R.id.widget_cover, openApp)

        manager.updateAppWidget(widgetId, views)
    }

    private fun cmd(context: Context, action: String, tag: String): PendingIntent {
        val it = Intent(context, WidgetCommandReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, tag.hashCode(), it,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** 读取封面位图：用户自定义封面优先，其次按歌曲 ID 缓存的在线封面 */
    private fun localCoverBitmap(context: Context, songId: Long, url: String): Bitmap? {
        // 1. 用户自定义封面（本地上传，file 路径）
        val custom = CoverStore.get(songId)
        if (custom != null && File(custom).exists()) {
            return runCatching { decodeSampled(custom) }.getOrNull()
        }
        // 2. 在线封面缓存（1 小时内有效）
        if (songId != 0L) {
            val f = coverCacheFile(context, songId)
            if (f.exists() && f.lastModified() > System.currentTimeMillis() - 3600_000) {
                return runCatching { decodeSampled(f.absolutePath) }.getOrNull()
            }
        }
        return null
    }

    /**
     * 采样解码封面：先读尺寸、按目标 ≤480px 计算 inSampleSize 再解码。
     * 直接 decodeFile 整图会把几千万像素的封面一次性载入内存导致 OOM，
     * 进程被杀 → 上传自定义封面后点歌播放瞬间闪退回桌面。
     */
    private fun decodeSampled(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 480) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun fetchCover(context: Context, url: String, songId: Long, widgetId: Int) {
        runCatching {
            val thumbUrl = if (!url.contains("param=")) {
                url + (if (url.contains("?")) "&" else "?") + "param=480y480"
            } else url
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            client.newCall(
                Request.Builder().url(thumbUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                    .header("Referer", "https://music.163.com/")
                    .get().build()
            ).execute().use { resp ->
                if (resp.isSuccessful) {
                    val data = resp.body?.bytes() ?: return
                    BitmapFactory.decodeByteArray(data, 0, data.size)?.let { bmp ->
                        val f = coverCacheFile(context, songId)
                        f.outputStream().use { it.buffered().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
                        // 校验：下载完成时仍是这首歌才更新 UI，避免把旧封面贴到新歌名上
                        if (PlayerHub.state.value.current?.id == songId) {
                            val views = RemoteViews(context.packageName, R.layout.widget_music)
                            views.setImageViewBitmap(R.id.widget_cover, bmp)
                            AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views)
                        }
                        bmp.recycle()
                    }
                }
            }
        }
    }
}