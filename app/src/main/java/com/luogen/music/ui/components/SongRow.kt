package com.luogen.music.ui.components

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.music.domain.model.Song
import com.luogen.music.player.PlayerHub
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain

/** 歌曲行：封面/序号 + 歌名歌手 + 时长 + 三点操作菜单 */
@Composable
fun SongRow(
    song: Song,
    index: Int,
    modifier: Modifier = Modifier,
    showCover: Boolean = true,
    showIndex: Int? = null,          // 传序号则在封面位置显示序号
    showDuration: Boolean = true,
    isCurrent: Boolean = false,
    showRemove: Boolean = false,     // 队列上下文显示“移除”
    coverStyle: String = "round",
    onPlay: () -> Unit,
    onRemove: (() -> Unit)? = null,
    menuExtra: (@Composable () -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    val st = PlayerHub.state.value
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = true, onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIndex != null) {
            Text(
                "$showIndex", color = if (isCurrent) LgSecondary else TextDim,
                fontSize = 13.sp, modifier = Modifier.width(22.dp),
            )
            Spacer(Modifier.width(10.dp))
        } else if (showCover) {
            Box {
                CoverArt(song, 46.dp, style = coverStyle)
                if (isCurrent) NowPlayingBadge()
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.name,
                color = if (isCurrent) LgSecondary else TextMain,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(
                    song.artistNames.joinToString(" / ").takeIf { it.isNotBlank() },
                    song.albumName.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                    .ifBlank { "未知歌手" },
                color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (showDuration && song.durationMs > 0) {
            Spacer(Modifier.width(8.dp))
            Text(formatDuration(song.durationMs), color = TextDim, fontSize = 11.sp)
        }
        Box {
            Icon(
                Icons.Rounded.MoreVert, null,
                tint = TextDim,
                modifier = Modifier
                    .padding(4.dp)
                    .size(20.dp)
                    .clickable { menuOpen = true },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("播放") },
                    leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                    onClick = { menuOpen = false; onPlay() },
                )
                // 下一首播放：插入到当前播放曲目之后，按添加顺序依次播放
                DropdownMenuItem(
                    text = { Text("下一首播放") },
                    leadingIcon = { Icon(Icons.Rounded.SkipNext, null) },
                    onClick = {
                        menuOpen = false
                        PlayerHub.insertNext(song)
                        Toast.makeText(context, "已插入下一首播放", Toast.LENGTH_SHORT).show()
                    },
                )
                // 添加到歌单：选择/新建歌单后加入
                DropdownMenuItem(
                    text = { Text("添加到歌单") },
                    leadingIcon = { Icon(Icons.Rounded.Add, null) },
                    onClick = { menuOpen = false; showPlaylistSheet = true },
                )
                if (showRemove && onRemove != null) {
                    DropdownMenuItem(
                        text = { Text("从列表移除") },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                        onClick = { menuOpen = false; onRemove() },
                    )
                }
                menuExtra?.invoke()
            }
            if (showPlaylistSheet) {
                PlaylistSheet(song, onDismiss = { showPlaylistSheet = false })
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}

/** 歌曲在 Lazy 列表中的稳定 key：本地用 uri，在线用 source+id，刷新/换批不再错位 */
fun songListKey(s: Song): String = if (s.localUri != null) "l:${s.localUri}" else "o:${s.source}:${s.id}"

/** 歌曲行通用菜单项（下载/铃声/信息/分享），供各页面复用 */
@Composable
fun commonSongMenuItems(
    song: Song,
    onChange: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onRingtone: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onAddList: (() -> Unit)? = null,
) {
    if (onNext != null) {
        DropdownMenuItem(
            text = { Text("下一首播放") },
            leadingIcon = { Icon(Icons.Rounded.Add, null) },
            onClick = onNext,
        )
    }
    if (onAddList != null) {
        DropdownMenuItem(
            text = { Text("收藏到我的歌单") },
            leadingIcon = { Icon(Icons.Rounded.Add, null) },
            onClick = onAddList,
        )
    }
    if (onDownload != null) {
        DropdownMenuItem(
            text = { Text("下载歌曲") },
            leadingIcon = { Icon(Icons.Rounded.Download, null) },
            onClick = onDownload,
        )
    }
    if (onRingtone != null) {
        DropdownMenuItem(
            text = { Text("设为铃声") },
            leadingIcon = { Icon(Icons.Rounded.PhoneAndroid, null) },
            onClick = onRingtone,
        )
    }
    if (onShare != null) {
        DropdownMenuItem(
            text = { Text("分享") },
            leadingIcon = { Icon(Icons.Rounded.Share, null) },
            onClick = onShare,
        )
    }
    if (onInfo != null) {
        DropdownMenuItem(
            text = { Text("歌曲信息") },
            leadingIcon = { Icon(Icons.Rounded.Info, null) },
            onClick = onInfo,
        )
    }
    onChange?.let {
        DropdownMenuItem(text = { Text("…") }, onClick = it)
    }
}

/** 迷你播放条（首页/本地页底部：旋转唱片 + 播放状态 + 进播放页） */
@Composable
fun NowPlayingBar(
    modifier: Modifier = Modifier,
    onOpenPlayer: () -> Unit,
) {
    // 订阅 PlayerHub 状态：开始播放/切歌/暂停都会驱动重组显示
    val st by PlayerHub.state.collectAsState()
    val song = st.current ?: return
    val context = LocalContext.current

    // 旋转唱片动画：播放中持续旋转，暂停时停在当前角度
    val transition = rememberInfiniteTransition(label = "miniVinyl")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 16000, easing = LinearEasing)),
        label = "miniRotation",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenPlayer() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 旋转唱片封面
        Box(
            modifier = Modifier
                .size(46.dp)
                .graphicsLayer {
                    rotationZ = if (st.isPlaying) rotation else 0f
                },
            contentAlignment = Alignment.Center,
        ) {
            CoverArt(song, 46.dp, style = "round", modifier = Modifier.clickable { onOpenPlayer() })
            // 播放状态小徽章：暂停显示 ▶，播放中显示动态音柱
            if (st.isPlaying) {
                PlayingBadge(Modifier.align(Alignment.BottomEnd))
            } else {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .background(Color(0xE61A1A2E))
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = LgSecondary, modifier = Modifier.size(11.dp))
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable { onOpenPlayer() }) {
            Text(song.name, color = TextMain, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artistNames.joinToString(" / "), color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(6.dp))
        // 上一曲
        PressScale(modifier = Modifier.size(40.dp), onClick = {
            com.luogen.music.service.PlaybackService.prev(context)
        }) {
            Box(Modifier.padding(9.dp)) {
                Icon(Icons.Rounded.SkipPrevious, null, tint = TextMain, modifier = Modifier.size(20.dp))
            }
        }
        // 播放/暂停
        PressScale(modifier = Modifier.size(42.dp), onClick = {
            com.luogen.music.service.PlaybackService.playPause(context)
        }) {
            Box(Modifier.padding(9.dp)) {
                androidx.compose.material3.Icon(
                    if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    null, tint = TextMain,
                )
            }
        }
        // 下一曲
        PressScale(modifier = Modifier.size(42.dp), onClick = {
            com.luogen.music.service.PlaybackService.next(context)
        }) {
            Box(Modifier.padding(9.dp)) {
                androidx.compose.material3.Icon(Icons.Rounded.SkipNext, null, tint = TextMain)
            }
        }
    }
}

/** 播放中动态音柱小徽章 */
@Composable
private fun PlayingBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eq")
    val heights = listOf(
        transition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing)), label = "h1"),
        transition.animateFloat(1f, 0.45f, infiniteRepeatable(tween(320, easing = LinearEasing)), label = "h2"),
        transition.animateFloat(0.5f, 0.95f, infiniteRepeatable(tween(500, easing = LinearEasing)), label = "h3"),
    )
    Row(
        modifier = modifier
            .size(16.dp)
            .padding(3.dp)
            .background(Color(0xE61A1A2E))
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEach { h ->
            Box(
                Modifier
                    .width(2.dp)
                    .height((6 * h.value).dp)
                    .background(LgSecondary),
            )
        }
    }
}