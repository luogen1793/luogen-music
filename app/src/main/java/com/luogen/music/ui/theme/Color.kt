package com.luogen.music.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** 发现音乐 · 酷狗概念版风格配色 —— 暗夜流光 */
val LgPrimary = Color(0xFFE040FB)      // 流光紫
val LgPrimarySoft = Color(0xFF9D5CFF)
val LgSecondary = Color(0xFF4DD0E1)    // 星云青
val LgPink = Color(0xFFFF5C8D)
val LgAmber = Color(0xFFFFC24B)
val LgGreen = Color(0xFF2EE6A8)

val BgDeep = Color(0xFF0B0B14)         // 深邃底
val BgPanel = Color(0xFF12121F)
val BgPanelSoft = Color(0xFF1A1A2E)

val GlassWhite = Color(0x1CFFFFFF)     // 玻璃半透明白
val GlassWhite2 = Color(0x0DFFFFFF)
val GlassBorder = Color(0x4DFFFFFF)    // 玻璃高光描边
val TextMain = Color(0xFFF5F5FF)
val TextDim = Color(0xFF9AA0B4)
val TextFaint = Color(0xFF5F6678)

/** 页面主渐变（紫 ➜ 青 的暗夜流光） */
val MainGradient = Brush.linearGradient(
    colors = listOf(LgPrimary, LgPrimarySoft, LgSecondary)
)

/** 深色底上的氛围渐变 */
val AmbientGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF25143E), Color(0xFF122A38), BgDeep)
)

val GlassGradient = Brush.linearGradient(
    colors = listOf(GlassWhite2, GlassWhite, Color(0x0AFFFFFF))
)