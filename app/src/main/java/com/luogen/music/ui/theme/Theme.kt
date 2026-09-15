package com.luogen.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val Color_OnPrimary = androidx.compose.ui.graphics.Color.White
private val Color_OnSecondary = androidx.compose.ui.graphics.Color(0xFF00363A)

private val LuogenColorScheme = darkColorScheme(
    primary = LgPrimary,
    onPrimary = Color_OnPrimary,
    secondary = LgSecondary,
    onSecondary = Color_OnSecondary,
    tertiary = LgPink,
    background = BgDeep,
    onBackground = TextMain,
    surface = BgPanel,
    onSurface = TextMain,
    surfaceVariant = BgPanelSoft,
    onSurfaceVariant = TextDim,
    error = LgPink,
)

/** 发现音乐主题：深色玻璃拟态（无论系统深浅均保持品牌暗夜风格） */
@Composable
fun LuogenTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LuogenColorScheme,
        typography = LuogenTypography,
        content = content,
    )
}