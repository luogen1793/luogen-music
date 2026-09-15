package com.luogen.music.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import com.luogen.music.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * 分享工具：生成“发现音乐”品牌歌曲卡片图 + 深链，
 * 调用系统分享面板真实发送到微信/QQ/朋友圈/QQ空间等任意应用。
 */
object ShareHelper {

    /** 分享深链：luogen://song/{id}，配落地页 https://luogen.app/s/{id} */
    const val LANDING_BASE = "https://luogen.app/s/"

    suspend fun shareSong(context: Context, song: Song) {
        val card = withContext(Dispatchers.IO) { makeCard(context, song) }
        val text = "🎵 ${song.name} — ${song.artistNames.joinToString(" / ")}\n来自「发现音乐」APP，点开即听：luogen://song/${song.id}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "发现音乐 · ${song.name}")
            if (card != null) {
                // 必须用 FileProvider 暴露，直接 Uri.fromFile 在 Android 7.0+ 会抛 FileUriExposedException 崩溃
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", card
                )
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "分享「${song.name}」到").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { e ->
            // 兜底：即使分享面板打不开也绝不闪退，退回纯文本分享
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            runCatching { context.startActivity(Intent.createChooser(fallback, "分享「${song.name}」到").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** 生成 1080x1440 的品牌歌曲卡片（真实位图，可发朋友圈/QQ空间/微信） */
    private fun makeCard(context: Context, song: Song): File? {
        return runCatching {
            val out = File(context.cacheDir, "share_${song.id}.jpg")
            val w = 1080
            val h = 1440
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // 紫色氛围渐变
            paint.shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(0xFF2B1055.toInt(), 0xFF1A2B4A.toInt(), 0xFF0E3140.toInt()),
                null, Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null

            // 封面图（圆角方）
            val cover = loadCover(song.coverUrl) ?: defaultCover()
            val coverSize = 660
            val left = (w - coverSize) / 2f
            val top = 260f
            val rect = RectF(left, top, left + coverSize, top + coverSize)
            canvas.drawRoundRect(rect, 56f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            canvas.save()
            val clipPath = android.graphics.Path().apply {
                addRoundRect(rect, 56f, 56f, android.graphics.Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            canvas.drawBitmap(cover, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()

            // 歌名
            paint.color = Color.WHITE
            paint.textSize = 58f
            paint.isFakeBoldText = true
            val title = song.name.take(14)
            canvas.drawText(title, (w - paint.measureText(title)) / 2f, 1060f, paint)
            // 歌手
            paint.color = 0xCCFFFFFF.toInt()
            paint.textSize = 40f
            paint.isFakeBoldText = false
            val artist = song.artistNames.joinToString(" / ").take(20)
            canvas.drawText(artist, (w - paint.measureText(artist)) / 2f, 1130f, paint)
            // 底部来源
            paint.color = 0x99FFFFFF.toInt()
            paint.textSize = 30f
            val brand = "来自「发现音乐」 · 点击下方链接即可收听"
            canvas.drawText(brand, (w - paint.measureText(brand)) / 2f, 1280f, paint)

            check(!out.exists() || out.delete())
            FileOutputStream(out).use { fos -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos) }
            bmp.recycle()
            out
        }.getOrNull()
    }

    private fun loadCover(url: String): Bitmap? {
        if (url.isBlank()) return null
        return runCatching {
            // 封面缩略 + 短超时，避免分享按钮长期无响应
            val thumbUrl = if (url.startsWith("http") && !url.contains("param=")) {
                url + (if (url.contains("?")) "&" else "?") + "param=480y480"
            } else url
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url(thumbUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                .header("Referer", "https://music.163.com/")
                .get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let {
                    Bitmap.createScaledBitmap(it, 660, 660, true)
                }
            }
        }.getOrNull()
    }

    private fun defaultCover(): Bitmap {
        val bmp = Bitmap.createBitmap(660, 660, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFF30145B.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x80FFFFFF.toInt() }
        canvas.drawCircle(330f, 330f, 150f, paint)
        return bmp
    }

    private fun FileOutputStream(path: File) = java.io.FileOutputStream(path)
}