package com.luogen.music.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.music.ui.components.AmbientBackground
import com.luogen.music.ui.theme.GlassBorder
import com.luogen.music.ui.theme.LgPrimary
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.MainGradient
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain

/**
 * 品牌启动页：毛玻璃徽标 + 作者罗根标注
 */
@Composable
fun SplashScreen(onFinished: () -> Unit = {}) {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        ready = true
        kotlinx.coroutines.delay(500)
        onFinished()
    }
    val scale by animateFloatAsState(if (ready) 1f else 0.72f, spring(dampingRatio = 0.5f, stiffness = 380f), label = "logo")
    val alpha by animateFloatAsState(if (ready) 1f else 0f, tween(600), label = "alpha")

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(extraGlow = LgPrimary)
        Column(
            modifier = Modifier.align(Alignment.Center).alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(30.dp))
                    .background(MainGradient)
                    .then(Modifier.padding(0.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(30.dp))
                        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent)))
                        .padding(0.dp)
                )
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(52.dp))
                Box(
                    Modifier
                        .size(108.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color.Transparent)
                )
            }
            Spacer(Modifier.height(26.dp))
            Text("发现音乐", color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "极致质感 · 在线音乐播放器",
                color = TextDim, fontSize = 13.sp, letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "作者 · 罗根",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 42.dp).alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("玻璃拟态 · 一键听歌 · 听歌识曲 · 一起听", color = TextDim, fontSize = 11.sp)
        }
    }
}