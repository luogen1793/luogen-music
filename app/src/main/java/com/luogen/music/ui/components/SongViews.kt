package com.luogen.music.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.luogen.music.domain.model.Song
import com.luogen.music.ui.theme.GlassBorder
import com.luogen.music.ui.theme.GlassGradient
import com.luogen.music.ui.theme.LgPrimary
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.MainGradient
import com.luogen.music.ui.theme.TextDim

/**
 * 封面组件：支持 圆形 / 圆角方形 / 沉浸式 / 玻璃拟态 四种样式；
 * 无封面时自动生成渐变默认封面（默认封面规则：品牌渐变 + 音符 + 歌名首字）。
 */
@Composable
fun CoverArt(
    song: Song,
    size: Dp,
    style: String = "round",   // round | square | immersive | glass
    modifier: Modifier = Modifier,
    shape: Shape? = null,
) {
    // 自定义封面优先（用户上传即全局生效，本地/在线列表都实时刷新）
    val customMap by com.luogen.music.data.local.CoverStore.custom.collectAsState()
    val custom = if (song.id != 0L) customMap[song.id]?.takeIf { java.io.File(it).exists() } else null
    val display = custom ?: song.coverUrl
    val hasArt = display.isNotBlank()
    val overrideShape = when (style) {
        "square" -> RoundedCornerShape(4.dp)
        "immersive" -> RoundedCornerShape(0.dp)
        "glass" -> RoundedCornerShape(20.dp)
        else -> shape ?: RoundedCornerShape(12.dp)
    }
    Box(
        modifier = modifier.size(size).clip(overrideShape),
        contentAlignment = Alignment.Center,
    ) {
        if (hasArt) {
            // 自定义封面为本地绝对路径/文件：Coil 需 File 对象；在线封面/封面 URL 用原字符串。
            // 加载中：底层渐变占位（图片淡入后覆盖）；加载失败：完整默认封面。绝不出现空白块。
            var failed by remember(custom, display) { mutableStateOf(false) }
            if (!failed) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(LgPrimary.copy(alpha = 0.85f), LgSecondary.copy(alpha = 0.55f))))
                )
                AsyncImage(
                    model = if (custom != null) java.io.File(custom) else display,
                    contentDescription = song.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = { failed = true },
                )
            } else {
                DefaultCover(song, size)
            }
            if (style == "glass") {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(GlassGradient)
                        .border(1.dp, GlassBorder.copy(alpha = 0.5f), overrideShape)
                )
            }
        } else {
            DefaultCover(song, size)
            if (style == "glass") {
                Box(Modifier.fillMaxSize().background(GlassGradient).border(1.dp, GlassBorder.copy(alpha = 0.5f), overrideShape))
            }
        }
    }
}

/** 默认封面：品牌紫青渐变 + 音符 + 歌名首字（无封面/封面加载中/加载失败时使用） */
@Composable
private fun DefaultCover(song: Song, size: Dp) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(LgPrimary.copy(alpha = 0.85f), LgSecondary.copy(alpha = 0.55f))))
    ) {
        Icon(
            Icons.Rounded.MusicNote, null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.align(Alignment.Center).size(size * 0.45f),
        )
        if (song.name.isNotBlank()) {
            Text(
                song.name.take(1),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = (size.value * 0.28f).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** 圆形默认/普通封面 */
@Composable
fun RoundCover(song: Song, size: Dp, modifier: Modifier = Modifier, style: String = "round") =
    CoverArt(song, size, style, modifier, shape = CircleShape)

/** 正在播放的跳动频谱装饰 */
@Composable
fun BoxScope.NowPlayingBadge() {
    Box(
        Modifier
            .align(Alignment.Center)
            .size(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.GraphicEq, null, tint = LgSecondary, modifier = Modifier.size(18.dp))
    }
}