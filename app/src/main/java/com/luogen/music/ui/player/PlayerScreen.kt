package com.luogen.music.ui.player

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.luogen.music.domain.model.PlayMode
import com.luogen.music.domain.model.Song
import com.luogen.music.data.local.CoverStore
import com.luogen.music.data.local.FavoriteStore
import com.luogen.music.service.PlaybackService
import com.luogen.music.ui.components.CoverArt
import com.luogen.music.ui.components.GlassIconButton
import com.luogen.music.ui.components.PressScale
import com.luogen.music.ui.theme.LgPrimary
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.LgPink
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain
import kotlinx.coroutines.launch

/** 播放页：毛玻璃沉浸式 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onShowQueue: () -> Unit,
    onShowInfo: (Song) -> Unit,
    toArtist: (Long) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val lyr by viewModel.lyr.collectAsState()
    val style by viewModel.coverStyle.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val song = state.current

    if (song == null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0B14)), contentAlignment = Alignment.Center) {
            Text("暂无正在播放的歌曲", color = TextDim)
        }
        return
    }

    // 首次进入加载歌词
    LaunchedEffect(song.id) { viewModel.loadLyric() }

    // 设为铃声：未授权“修改系统设置”时先跳系统授权页，返回后自动重试设置
    val pendingRingtone = remember { mutableStateOf<Song?>(null) }
    val ringtoneLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        pendingRingtone.value?.let { s ->
            viewModel.setRingtone(context, s)
            pendingRingtone.value = null
        }
    }

    fun requestRingtone(s: Song) {
        if (android.provider.Settings.System.canWrite(context)) {
            viewModel.setRingtone(context, s)
        } else {
            pendingRingtone.value = s
            runCatching {
                ringtoneLauncher.launch(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}"),
                    )
                )
            }.onFailure {
                pendingRingtone.value = null
                viewModel.showToast("无法打开系统授权页，请手动前往 设置→应用→发现音乐 授予“修改系统设置”")
            }
        }
    }

    val displayCover = remember(song.id, CoverStore.custom.value) {
        CoverStore.effectiveCover(song.id, song.coverUrl)
    }
    val displaySong = song.copy(coverUrl = displayCover)

    // 自定义封面是本地绝对路径（无 scheme），直接作为字符串 model 会让 Coil 抛异常导致崩溃，
    // 统一转成 File 对象后才可加载；http(s) 封面保持字符串。
    val coverModel: Any = remember(displayCover) {
        when {
            displayCover.startsWith("http") -> displayCover
            displayCover.startsWith("file:") -> java.io.File(displayCover.removePrefix("file://"))
            displayCover.startsWith("/") -> java.io.File(displayCover)
            else -> displayCover
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 沉浸模糊背景
        if (displayCover.isNotBlank()) {
            AsyncImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(60.dp).background(Color.Black.copy(alpha = 0.35f)),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF241040), Color(0xFF0B0B14)))))
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // 顶栏：当前歌曲名 + 歌手（点击歌手可跳歌手详情页）
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, size = 40.dp, onClick = onBack)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        song.name,
                        color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    if (song.artistNames.isNotEmpty()) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            song.artistNames.forEachIndexed { index, name ->
                                Text(
                                    name,
                                    color = LgSecondary, fontSize = 10.sp,
                                    modifier = Modifier.clickable { song.artistIds.getOrNull(index)?.let { toArtist(it) } },
                                )
                                if (index < song.artistNames.size - 1) {
                                    Text(" / ", color = LgSecondary, fontSize = 10.sp)
                                }
                            }
                        }
                    } else {
                        Text("未知歌手", color = TextDim, fontSize = 10.sp)
                    }
                }
                // 喜欢/收藏：与「我的喜欢」列表实时同步
                val favSongs by FavoriteStore.songs.collectAsState()
                val isFav = favSongs.any { it.id == song.id }
                GlassIconButton(
                    if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    size = 40.dp,
                    tint = if (isFav) LgPink else TextMain,
                    onClick = {
                        FavoriteStore.toggle(song)
                        viewModel.showToast(if (isFav) "已取消喜欢" else "已收藏到「我的喜欢」")
                    },
                )
                GlassIconButton(Icons.Rounded.Share, size = 40.dp, onClick = {
                    // 生成分享卡片需下载封面，先给即时反馈，避免“点一下没反应”的错觉
                    viewModel.showToast("正在生成分享卡片…")
                    scope.launch { com.luogen.music.util.ShareHelper.shareSong(context, song) }
                })
            }

            // 封面区 / 歌词区（联动：全屏歌词时隐藏封面、歌词放大占满）
            val lyricOn = lyr.visible && lyr.lyrics.isNotEmpty()
            if (lyricOn && lyr.fullscreen) {
                // 全屏歌词：与封面模糊背景、下方按钮区共用同一底色，整屏连为一体（无分隔线、无突兀色块）。
                // 点击一次回到“封面+歌词”模式。
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    LyricView(lyr.lyrics, viewModel.positionMs.collectAsState().value, fullscreen = true, onClick = { viewModel.toggleLyricFullscreen() }, onSeekToLine = { viewModel.seekToLine(it) })
                    // 「点击歌词退出全屏」引导：仅在第一次进入全屏时短时显示，之后不再出现，不遮挡歌词
                    val app = com.luogen.music.LuogenApp.instance
                    val hintSeen by app.settings.lyricHintSeen.collectAsState(initial = false)
                    var hintVisible by remember { mutableStateOf(false) }
                    val hintAlpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (hintVisible) 1f else 0f,
                        animationSpec = androidx.compose.animation.core.tween(500),
                        label = "lyricHintAlpha",
                    )
                    LaunchedEffect(lyr.fullscreen, hintSeen) {
                        if (lyr.fullscreen && !hintSeen) {
                            hintVisible = true
                            kotlinx.coroutines.delay(2500)
                            hintVisible = false
                            scope.launch { runCatching { app.settings.markLyricHintSeen() } }
                        }
                    }
                    if (hintAlpha > 0.01f) {
                        Text(
                            "点击歌词 退出全屏",
                            color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                                .graphicsLayer { this.alpha = hintAlpha }
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.3f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            } else {
                // 封面区（歌词开启且未全屏时，封面保持显示）
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when (style) {
                        "immersive" -> {
                            if (displayCover.isNotBlank()) {
                                AsyncImage(model = coverModel, contentDescription = null, modifier = Modifier.fillMaxWidth().height(340.dp))
                            } else {
                                CoverArt(displaySong, 320.dp, style = "immersive")
                            }
                        }
                        else -> {
                            val round = style == "round"
                            CoverArt(displaySong, 300.dp, style = style, shape = if (round) RoundedCornerShape(24.dp) else null)
                        }
                    }
                }

                // 歌词（开启但未全屏：封面共存，点击进入全屏）或歌曲信息
                if (lyricOn) {
                    LyricView(lyr.lyrics, viewModel.positionMs.collectAsState().value, onClick = { viewModel.toggleLyricFullscreen() })
                } else {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(song.name, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Text(song.artistNames.joinToString(" / "), color = TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 进度
            ProgressBar(viewModel)

            // 控制
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ModeButton(state.mode, onClick = { viewModel.rotateMode(context) })
                GlassIconButton(Icons.Rounded.SkipPrevious, size = 52.dp, onClick = { viewModel.prev(context) })
                BigPlayButton(state.isPlaying, onClick = { viewModel.playPause(context) })
                GlassIconButton(Icons.Rounded.SkipNext, size = 52.dp, onClick = { viewModel.next(context) })
                GlassIconButton(Icons.Rounded.QueueMusic, size = 44.dp, onClick = onShowQueue)
            }

            // 操作行：歌词/下载/铃声/封面/信息
            var styleDialog by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ActionChip(Icons.Rounded.GraphicEq, if (lyr.visible) "歌词关" else "歌词") { viewModel.toggleLyric() }
                ActionChip(Icons.Rounded.Download, "下载") { viewModel.downloadCurrent(context, song) {} }
                ActionChip(Icons.Rounded.PhoneAndroid, "铃声") { requestRingtone(song) }
                ActionChip(Icons.Rounded.CameraAlt, "封面") { styleDialog = true }
                ActionChip(Icons.Rounded.Info, "信息") { onShowInfo(song) }
            }
            if (styleDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { styleDialog = false },
                    containerColor = Color(0xF21A1A2E),
                    title = { Text("封面样式", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            listOf("round" to "圆角玻璃", "square" to "方形", "glass" to "玻璃拟态", "immersive" to "沉浸式全屏").forEach { (k, v) ->
                                TextButton(onClick = { viewModel.setCoverStyle(k); styleDialog = false }) {
                                    Text(
                                        v,
                                        color = if (k == style) LgSecondary else TextMain,
                                        fontSize = 14.sp, fontWeight = if (k == style) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    if (k == style) Spacer(Modifier.width(4.dp))
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { styleDialog = false }) { Text("完成", color = LgSecondary) } },
                )
            }
            Spacer(Modifier.height(14.dp))
        }
    }

    toast?.let { t ->
        LaunchedEffect(t) {
            kotlinx.coroutines.delay(2200)
            viewModel.consumeToast()
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Text(
                t, color = Color.White, fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 90.dp).clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun ProgressBar(viewModel: PlayerViewModel) {
    val pos by viewModel.positionMs.collectAsState()
    val dur by viewModel.durationMs.collectAsState()
    val context = LocalContext.current
    val progress = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    // 拖动中显示悬停位置，松手才真正 seek。
    // 修复跳动：seek 后不立即释放拖动态，否则 positionMs 尚未更新到目标时会先闪回旧进度；
    // 等播放位置真正追上目标（或兜底超时）再恢复实时显示。
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    var seekTargetMs by remember { mutableStateOf(-1L) } // -1=当前无待确认的 seek
    val shown = if (dragging) dragValue else progress
    // pos 追上目标（±600ms，或已到歌曲末尾）即释放
    LaunchedEffect(pos, seekTargetMs) {
        if (dragging && seekTargetMs >= 0L && dur > 0L) {
            val reached = kotlin.math.abs(pos - seekTargetMs) <= 600L || pos >= dur - 200L
            if (reached) {
                dragging = false
                seekTargetMs = -1L
            }
        }
    }
    // 兜底：若 seek 后长时间无响应（如音源缓冲/切换），2 秒后仍释放拖动态
    LaunchedEffect(seekTargetMs) {
        if (seekTargetMs >= 0L) {
            kotlinx.coroutines.delay(2000)
            dragging = false
            seekTargetMs = -1L
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Slider(
            value = shown,
            onValueChange = { v ->
                dragging = true
                dragValue = v
            },
            onValueChangeFinished = {
                if (dragging) {
                    val target = (dragValue * dur).toLong()
                    seekTargetMs = target
                    PlaybackService.seekTo(context, target)
                }
            },
            colors = SliderDefaults.colors(
                thumbColor = LgPrimary,
                activeTrackColor = LgPrimary,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    // 点击进度条任意位置直接跳到对应进度（滑块拖动由 Slider 原生处理）
                    detectTapGestures { offset ->
                        val ratio = offset.x / size.width
                        PlaybackService.seekTo(context, (ratio * dur).toLong())
                    }
                },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            Text(fmt(if (dragging) (dragValue * dur).toLong() else pos), color = TextDim, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(fmt(dur), color = TextDim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BigPlayButton(isPlaying: Boolean, onClick: () -> Unit) {
    PressScale(modifier = Modifier.size(72.dp), scaleDown = 0.88f, onClick = onClick) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(36.dp))
                .background(Brush.linearGradient(listOf(LgPrimary, Color(0xFF8B5CF6))))
                .then(Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                null, tint = Color.White, modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun ModeButton(mode: PlayMode, onClick: () -> Unit) {
    val (icon, label) = when (mode) {
        PlayMode.REPEAT_ALL -> Icons.Rounded.Repeat to "列表循环"
        PlayMode.REPEAT_ONE -> Icons.Rounded.RepeatOne to "单曲循环"
        PlayMode.SEQUENCE -> Icons.Rounded.ArrowDownward to "顺序播放"
        PlayMode.SHUFFLE -> Icons.Rounded.Shuffle to "随机播放"
    }
    GlassIconButton(icon, size = 44.dp, tint = LgSecondary, onClick = onClick)
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    PressScale(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
            Icon(icon, null, tint = TextDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(2.dp))
            Text(label, color = TextDim, fontSize = 10.sp)
        }
    }
}

@Composable
private fun LyricView(lines: List<com.luogen.music.util.LrcLine>, positionMs: Long, onClick: () -> Unit, fullscreen: Boolean = false, onSeekToLine: ((com.luogen.music.util.LrcLine) -> Unit)? = null) {
    val listState = rememberLazyListState()
    val currentIdx = remember(positionMs, lines) {
        var idx = 0
        for ((i, l) in lines.withIndex()) { if (l.timeMs <= positionMs) idx = i else break }
        idx
    }
    LaunchedEffect(currentIdx, lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(currentIdx.coerceIn(0, lines.size - 1))
    }
    val boxModifier = if (fullscreen) {
        Modifier.fillMaxSize()
    } else {
        Modifier.fillMaxWidth().height(140.dp).pointerInput(Unit) { detectTapGestures { onClick() } }
    }
    Box(boxModifier) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (fullscreen) item { Spacer(Modifier.height(120.dp)) }
            items(lines.size) { i ->
                val line = lines[i]
                val active = i == currentIdx
                Text(
                    line.text,
                    color = when {
                        active -> Color.White
                        fullscreen -> Color.White.copy(alpha = 0.45f)
                        else -> TextDim.copy(alpha = 0.85f)
                    },
                    fontSize = if (fullscreen) { if (active) 24.sp else 16.sp } else { if (active) 15.sp else 13.sp },
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = if (active) 0.55f else 0.5f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 1.5f),
                            blurRadius = if (active) 6f else 5f,
                        ),
                    ),
                    modifier = Modifier
                        .padding(vertical = if (fullscreen) 10.dp else 5.dp, horizontal = 28.dp)
                        .then(
                            if (fullscreen && onSeekToLine != null && !active) Modifier.clickable { onSeekToLine(line) }
                            else if (fullscreen) Modifier.clickable { onClick() }
                            else Modifier
                        ),
                )
            }
            if (fullscreen) item { Spacer(Modifier.height(120.dp)) }
        }
    }
}


private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}