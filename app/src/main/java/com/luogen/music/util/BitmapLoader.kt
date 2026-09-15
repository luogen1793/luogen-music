package com.luogen.music.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 位图采样解码工具：所有「从本地文件解码图片」的统一入口。
 * 直接 decodeFile 会把几千万像素的照片一次性载入内存导致 OOM 杀进程，
 * 这里先探尺寸、按目标边长采样后再解码，杜绝大图闪退。
 */
object BitmapLoader {

    /** 采样解码本地图片（默认目标 480px；myNick/头像类用 256px） */
    fun decodeSampled(path: String, maxSide: Int = 480): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, opts)
    }.getOrNull()
}