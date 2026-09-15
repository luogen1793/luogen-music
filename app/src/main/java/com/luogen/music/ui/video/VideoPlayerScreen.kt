package com.luogen.music.ui.video

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.luogen.music.LuogenApp
import com.luogen.music.domain.model.Song
import com.luogen.music.ui.components.GlassIconButton
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.TextMain

/**
 * 视频播放页：MV / 本地视频 真实播放（ExoPlayer）
 * video.song.isVideo=true；本地视频 localUri 非空；在线 MV 由 url 传入
 */
@Composable
fun VideoPlayerScreen(song: Song, onBack: () -> Unit) {
    val context = LocalContext.current
    // 网易云 CDN 视频流同样需要 Referer/UA，否则返回非媒体内容导致“地址获取失败”
    val player = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(12000)
            .setReadTimeoutMs(18000)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://music.163.com/",
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36",
                )
            )
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
        ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory).build()
    }
    var error by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(song.id, song.localUri, song.coverUrl, retryTick) {
        error = null
        val url = if (song.localUri != null) {
            song.localUri
        } else {
            resolveMvUrl(song.id)
        }
        if (url != null) {
            try {
                player.setMediaItem(MediaItem.fromUri(url.replace(" ", "%20")))
                player.prepare()
                player.playWhenReady = true
            } catch (e: Exception) {
                error = "视频加载失败：${e.message ?: "未知错误"}"
            }
        } else {
            error = if (song.id == 0L) {
                "MV 信息异常，无法播放"
            } else {
                "该 MV 暂不可播放（需 VIP 权限或已下架），试试其他视频"
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }.also { it.player = player }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // 顶部返回
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(8.dp)) {
            GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, size = 40.dp, onClick = onBack)
        }
        // 加载错误提示 + 点击重试
        error?.let { msg ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { retryTick++ }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Text(msg, color = TextMain, fontSize = 14.sp)
                    Text("点击屏幕重试", color = LgSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        // 标题
        Box(Modifier.fillMaxSize().padding(top = 52.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                song.name, color = TextMain, fontSize = 14.sp,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 解析 MV 直链，通道优先级：
 * 1) 网易官方 MV 详情接口（data.brs 码率直链，不依赖镜像权限，实测可播）
 * 2) 主镜像 /mv/url
 * 3) 设置里配置的其余备用服务器逐一尝试
 */
private suspend fun resolveMvUrl(id: Long): String? {
    val repo = LuogenApp.instance.repository
    // 官方接口：最高命中率的主通道
    repo.mvUrlOfficial(id)?.takeIf { it.isNotBlank() }?.let { return it }
    // 备用：主镜像
    val primary = runCatching { repo.mvUrl(id) }
    primary.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
    // 备用：设置里配置的其余服务器
    val servers = LuogenApp.instance.settings.getApiServersSync()
    for (srv in servers) {
        val v = runCatching { repo.mvUrlForServer(srv, id) }
        v.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        v.exceptionOrNull()?.let { android.util.Log.w("VideoPlayer", "MV url 备用服务器 $srv 失败: ${it.message}") }
    }
    primary.exceptionOrNull()?.let { android.util.Log.w("VideoPlayer", "MV url 主服务器失败: ${it.message}") }
    return null
}