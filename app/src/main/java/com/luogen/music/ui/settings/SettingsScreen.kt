package com.luogen.music.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.music.LuogenApp
import com.luogen.music.data.prefs.SettingsRepo
import com.luogen.music.service.ShakeGestureService
import com.luogen.music.ui.components.AmbientBackground
import com.luogen.music.ui.components.GlassCard
import com.luogen.music.ui.components.GlassChip
import com.luogen.music.ui.components.GlassIconButton
import com.luogen.music.ui.components.PressScale
import com.luogen.music.ui.components.SectionTitle
import com.luogen.music.ui.theme.LgPrimary
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.MainGradient
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain
import kotlinx.coroutines.launch

/** 设置页：音乐服务器 / 后台播放 / 摇一摇 / 封面样式 / 一起听 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = LuogenApp.instance.settings
    val scope = rememberCoroutineScope()

    val bgPlay by s.backgroundPlay.collectAsState(initial = true)
    val shakeEnabled by s.shakeEnabled.collectAsState(initial = true)
    val coverStyle by s.coverStyle.collectAsState(initial = "round")
    val servers by s.apiServers.collectAsState(initial = emptySet())
    val shakeLeft by s.shakeLeftAction.collectAsState(initial = SettingsRepo.ACTION_PREV)
    val shakeRight by s.shakeRightAction.collectAsState(initial = SettingsRepo.ACTION_NEXT)

    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    var serverText by remember { mutableStateOf("") }
    LaunchedEffect(servers) {
        if (serverText.isBlank()) serverText = servers.joinToString("\n")
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(extraGlow = LgSecondary)
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, size = 40.dp, onClick = onBack)
                    Spacer(Modifier.padding(4.dp))
                    Text("设置", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }

            item { SectionTitle("音乐服务", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            item {
                GlassCard(Modifier.padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("音乐服务器（每行一个，自动故障切换）", color = TextMain, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        TextField(
                            value = serverText,
                            onValueChange = { serverText = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextMain),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.06f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                                focusedIndicatorColor = LgPrimary,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = LgPrimary,
                            ),
                        )
                        Spacer(Modifier.height(8.dp))
                        PressScale(onClick = {
                            val list = serverText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                            if (list.isEmpty()) { toast("至少保留一个服务器地址"); return@PressScale }
                            scope.launch { s.setApiServers(list); toast("已保存，重启 App 生效") }
                        }) {
                            Box(
                                Modifier.clip(RoundedCornerShape(20.dp)).background(MainGradient).padding(horizontal = 18.dp, vertical = 9.dp)
                            ) { Text("保存服务器", color = Color.White, fontSize = 13.sp) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("默认：http://iwenwiki.com:3000（网易云协议，免费全曲）", color = TextDim, fontSize = 11.sp)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
            item { SectionTitle("播放与后台", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            item {
                GlassCard(Modifier.padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                    Column {
                        SwitchRow("后台播放", "开启后清理后台仍可继续播放（系统级前台服务）", bgPlay) {
                            scope.launch { s.setBackgroundPlay(it) }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("封面样式", color = TextDim, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("round" to "圆形", "square" to "方形", "glass" to "玻璃拟态", "immersive" to "沉浸式").forEach { (k, label) ->
                                GlassChip(label, selected = coverStyle == k, onClick = { scope.launch { s.setCoverStyle(k) } })
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
            item { SectionTitle("摇一摇切歌", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            item {
                GlassCard(Modifier.padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                    Column {
                        SwitchRow("启用摇一摇", "防误触版：左摇两下=上一曲 · 右摇两下=下一曲 · 单次晃动/左右各一下不触发任何动作", shakeEnabled) {
                            scope.launch {
                                s.setShakeEnabled(it)
                                if (it) ShakeGestureService.start(context) else ShakeGestureService.stop(context)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("加速度传感器无需任何运行时权限（设置里没有“传感器权限”属正常）。开启后通知栏会出现「摇一摇」常驻通知，即表示监听已生效；触发成功时手机有震动反馈。防误触机制：必须同方向连续快速摇动 2 次才切歌，轻微晃动、单次摇晃、左右各一下都不会触发，切歌后 1.5 秒内再次晃动也会被忽略。若通知未出现，请重启 App 或重新拨动开关。", color = TextDim, fontSize = 11.sp, lineHeight = 16.sp)
                        ActionPicker("左摇两下", listOf(
                            SettingsRepo.ACTION_PREV to "上一曲",
                            SettingsRepo.ACTION_NEXT to "下一曲",
                            SettingsRepo.ACTION_PLAY_PAUSE to "播放/暂停",
                            SettingsRepo.ACTION_NONE to "无",
                        ), shakeLeft) { v -> scope.launch { s.setShakeAction(v, shakeRight) } }
                        Spacer(Modifier.height(8.dp))
                        ActionPicker("右摇两下", listOf(
                            SettingsRepo.ACTION_NEXT to "下一曲",
                            SettingsRepo.ACTION_PREV to "上一曲",
                            SettingsRepo.ACTION_PLAY_PAUSE to "播放/暂停",
                            SettingsRepo.ACTION_NONE to "无",
                        ), shakeRight) { v -> scope.launch { s.setShakeAction(shakeLeft, v) } }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
            item { SectionTitle("一起听", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            item {
                GlassCard(Modifier.padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                    Column {
                        Text("一起听服务器（WebSocket 地址，默认公共房间服务器）", color = TextMain, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        TogetherServerInput()
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
            item { SectionTitle("听歌识曲", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            item {
                GlassCard(Modifier.padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                    Column {
                        Text("听歌识曲服务（HTTP 地址，留空使用内置默认）", color = TextMain, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        RecognizeServerInput()
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "协议：POST multipart 上传 file=env.wav → 返回 {\"title\":\"歌名\",\"artist\":\"歌手\"}。可用 Audd.io / ShazamIO 自建，也可使用本应用识曲服务页提供的公共实例。",
                            color = TextDim, fontSize = 11.sp,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("发现音乐 · 作者 罗根", color = TextDim, fontSize = 12.sp)
                    Text("v1.0.0 · 全部功能真实可用", color = TextDim, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun TogetherServerInput() {
    val context = LocalContext.current
    val s = LuogenApp.instance.settings
    val scope = rememberCoroutineScope()
    val server by s.togetherServer.collectAsState(initial = "")
    var text by remember { mutableStateOf("") }
    LaunchedEffect(server) { if (text.isBlank()) text = server }
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(10.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextMain),
        )
        Spacer(Modifier.width(8.dp))
        PressScale(onClick = { scope.launch { s.setTogetherServer(text.trim()); Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show() } }) {
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(MainGradient).padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text("保存", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RecognizeServerInput() {
    val context = LocalContext.current
    val s = LuogenApp.instance.settings
    val scope = rememberCoroutineScope()
    val server by s.recognizeServer.collectAsState(initial = "")
    var text by remember { mutableStateOf("") }
    LaunchedEffect(server) { if (text.isBlank()) text = server }
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(10.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextMain),
            singleLine = true,
        )
        Spacer(Modifier.width(8.dp))
        PressScale(onClick = { scope.launch { s.setRecognizeServer(text.trim()); Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show() } }) {
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(MainGradient).padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text("保存", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 14.sp)
            Text(desc, color = TextDim, fontSize = 11.sp)
        }
        Switch(checked = value, onCheckedChange = onChange, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = LgSecondary))
    }
}

@Composable
private fun ActionPicker(title: String, options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextMain, fontSize = 13.sp, modifier = Modifier.width(90.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            options.forEach { (k, label) ->
                GlassChip(label, selected = current == k, onClick = { onPick(k) })
            }
        }
    }
}