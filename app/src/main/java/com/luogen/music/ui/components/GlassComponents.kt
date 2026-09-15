package com.luogen.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.music.ui.theme.GlassBorder
import com.luogen.music.ui.theme.GlassGradient
import com.luogen.music.ui.theme.LgPrimary
import com.luogen.music.ui.theme.MainGradient
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain

/**
 * 发现音乐 · 毛玻璃玻璃拟态组件库
 * 全部组件自带：玻璃质感（半透渐变+高光描边）、按压弹性反馈（scale 弹簧动画）
 */

/** 通用按压弹性容器：按压时缩小并带涟漪，松手回弹 —— 大厂质感触感 */
@Composable
fun PressScale(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scaleDown: Float = 0.94f,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "press"
    )
    Box(
        modifier = modifier
            .scale(scale)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .pointerInput(enabled) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        pressed = ev.changes.any { it.pressed }
                    }
                }
            },
        content = content,
    )
}

/** 玻璃卡片：圆角 + 半透白渐变 + 高光描边 + 阴影 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val styled: @Composable BoxScope.() -> Unit = {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(GlassGradient)
                .border(1.dp, GlassBorder.copy(alpha = 0.6f), shape)
                .padding(contentPadding),
            content = content,
        )
    }
    if (onClick != null) {
        PressScale(modifier = modifier, onClick = onClick) { styled() }
    } else {
        Box(modifier = modifier) { styled() }
    }
}

/** 渐变胶囊按钮（主操作） */
@Composable
fun GlassGradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    PressScale(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MainGradient)
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 玻璃小标签（Chip） */
@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    val bg = if (selected) MainGradient else GlassGradient
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) Color.White.copy(alpha = 0.4f) else GlassBorder.copy(alpha = 0.45f), shape)
            .clickable(enabled = onClick != null, onClick = onClick ?: {})
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            color = if (selected) Color.White else TextMain,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** 圆形玻璃图标按钮（按压弹性） */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = TextMain,
    bg: Color = Color.White.copy(alpha = 0.08f),
    onClick: () -> Unit,
) {
    PressScale(modifier = modifier.size(size), onClick = onClick) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/** 区块标题（左侧高亮竖线 + 标题 + 可选副标题） */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            Modifier
                .width(4.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MainGradient)
        )
        Spacer(Modifier.width(8.dp))
        Text(title, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.width(8.dp))
            Text(subtitle, color = TextDim, fontSize = 12.sp)
        }
    }
}

/** 全屏氛围渐变背景 */
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    extraGlow: Color? = null,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A0F2E), Color(0xFF0E2230), Color(0xFF0B0B14))))
    ) {
        if (extraGlow != null) {
            Box(
                Modifier
                    .size(320.dp)
                    .background(Brush.radialGradient(listOf(extraGlow.copy(alpha = 0.28f), Color.Transparent)))
                    .align(Alignment.TopEnd)
            )
            Box(
                Modifier
                    .size(260.dp)
                    .background(Brush.radialGradient(listOf(LgPrimary.copy(alpha = 0.16f), Color.Transparent)))
                    .align(Alignment.BottomStart)
            )
        }
    }
}

/** 空态占位 */
@Composable
fun EmptyHint(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = TextDim.copy(alpha = 0.6f), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(text, color = TextDim, fontSize = 13.sp)
    }
}