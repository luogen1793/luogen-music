package com.luogen.music.ui.together

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Bundle
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.luogen.music.LuogenApp
import com.luogen.music.domain.model.TogetherMessage
import com.luogen.music.service.ListenTogetherService
import com.luogen.music.ui.components.AmbientBackground
import com.luogen.music.ui.components.GlassCard
import com.luogen.music.ui.components.GlassIconButton
import com.luogen.music.ui.components.PressScale
import com.luogen.music.ui.theme.LgPrimary
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.MainGradient
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 一起听：创建/加入房间、扫码/分享二维码、双方播放状态实时同步、房间内聊天。
 * 真实链路：WebSocket 房间服务（设置页可配置服务器，默认公共服务器）。
 */
@Composable
fun TogetherScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var joined by remember { mutableStateOf(false) }
    var roomCode by remember { mutableStateOf("") }
    // 本地生成的房间码：点击创建后立即展示二维码，不等服务器应答
    var creatingCode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { ListenTogetherService.start(context) }

    val room by ListenTogetherService.room.collectAsState()
    val connected by ListenTogetherService.connected.collectAsState()
    val error by ListenTogetherService.error.collectAsState()
    val messages by ListenTogetherService.messages.collectAsState()

    LaunchedEffect(room) { joined = room != null }

    // 房间内展示条件：已连接 或 正在创建本地房间
    val inRoom = joined || creatingCode != null

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(extraGlow = LgSecondary)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, size = 40.dp, onClick = onBack)
                Spacer(Modifier.padding(4.dp))
                Text("一起听", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (joined) {
                    PressScale(onClick = {
                        ListenTogetherService.leaveRoomCompat(context)
                        joined = false
                    }) {
                        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text("退出房间", color = TextDim, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (!inRoom) {
                // 创建 / 加入
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                    item {
                        GlassCard(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("创建房间", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("生成邀请二维码，好友扫码即可一起听", color = TextDim, fontSize = 12.sp)
                                Spacer(Modifier.height(14.dp))
                                PressScale(onClick = {
                                    val code = UUID.randomUUID().toString().take(6)
                                    creatingCode = code
                                    scope.launch { withContext(Dispatchers.IO) { join(context, code) } }
                                }) {
                                    Box(Modifier.clip(RoundedCornerShape(22.dp)).background(MainGradient).padding(horizontal = 26.dp, vertical = 12.dp)) {
                                        Text("创建房间并生成二维码", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                    item {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column {
                                Text("加入房间", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextField(
                                        value = roomCode,
                                        onValueChange = { roomCode = it },
                                        placeholder = { Text("输入 6 位房间号", color = TextDim, fontSize = 13.sp) },
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.padding(4.dp))
                                    PressScale(onClick = {
                                        if (roomCode.length == 6) scope.launch { withContext(Dispatchers.IO) { join(context, roomCode) } }
                                        else Toast.makeText(context, "房间号为 6 位", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Box(Modifier.clip(RoundedCornerShape(20.dp)).background(MainGradient).padding(horizontal = 16.dp, vertical = 10.dp)) {
                                            Text("加入", color = Color.White, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                    if (error != null) {
                        item {
                            Text("⚠ $error", color = Color(0xFFFF7A7A), fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                    item {
                        Text("玩法：进入房间后双方实时同步——上一曲/下一曲/播放暂停同步，可互发消息。分享二维码给好友，他们安装发现音乐后扫码即可加入。", color = TextDim, fontSize = 12.sp)
                    }
                }
            } else {
                // 房间内：二维码 + 聊天
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                    item {
                        GlassCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                // 连接状态行：已连接 / 连接中 / 服务器不可达 + 重试
                                val code = room ?: creatingCode ?: ""
                                Text(
                                    when {
                                        connected -> "● 已连接房间 $code · 邀请好友加入"
                                        error != null -> "⚠ 服务器连接失败"
                                        else -> "正在连接服务器…"
                                    },
                                    color = when {
                                        connected -> LgSecondary
                                        error != null -> Color(0xFFFF7A7A)
                                        else -> TextDim
                                    },
                                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                )
                                if (error != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        error!!,
                                        color = Color(0xFFFF7A7A), fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    PressScale(onClick = {
                                        val retry = creatingCode ?: room ?: return@PressScale
                                        creatingCode = retry
                                        scope.launch { withContext(Dispatchers.IO) { join(context, retry) } }
                                    }) {
                                        Box(Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 18.dp, vertical = 8.dp)) {
                                            Text("重试连接", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                val qrBmp = remember(code) { if (code.length == 6) makeQr("luogen://room/$code") else null }
                                if (qrBmp != null) {
                                    Box(Modifier.size(150.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(8.dp)) {
                                        androidx.compose.foundation.Image(
                                            bitmap = qrBmp.asImageBitmap(),
                                            contentDescription = "房间二维码",
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Text("扫码加入房间 · 房间号 $code", color = TextMain, fontSize = 13.sp)
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SmallBtn(Icons.Rounded.ContentCopy, "复制链接") {
                                            copyText(context, "luogen://room/$code")
                                        }
                                        SmallBtn(Icons.Rounded.Share, "分享") {
                                            val send = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, "来和我一起听歌！发现音乐房间：luogen://room/$code")
                                                putExtra(Intent.EXTRA_SUBJECT, "发现音乐 · 一起听")
                                            }
                                            context.startActivity(Intent.createChooser(send, "分享房间").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        }
                                    }
                                    if (!connected && error == null) {
                                        Spacer(Modifier.height(8.dp))
                                        Text("二维码已生成，正在连接服务器；连上后好友才能扫码加入", color = TextDim, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(14.dp)) }
                    item {
                        Column(Modifier.fillMaxWidth()) {
                            messages.takeLast(30).forEach { msg ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                    horizontalArrangement = if (msg.system) Arrangement.Center else Arrangement.Start,
                                ) {
                                    Text(
                                        if (msg.system) msg.text
                                        else "${msg.nick}：${msg.text}",
                                        color = if (msg.system) TextDim else TextMain, fontSize = 13.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(if (msg.system) 14.dp else 8.dp))
                                            .background(if (msg.system) Color.Transparent else Color.White.copy(alpha = 0.08f))
                                            .padding(horizontal = 12.dp, vertical = 7.dp),
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                    item {
                        ChatInput(onSend = { text ->
                            if (text.isNotBlank()) {
                                ListenTogetherService.sendChatCompat(context, text)
                            }
                        })
                    }
                    item { Spacer(Modifier.height(30.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SmallBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    PressScale(onClick = onClick) {
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = TextMain, modifier = Modifier.size(15.dp))
            Spacer(Modifier.padding(4.dp))
            Text(label, color = TextMain, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChatInput(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("和朋友说点什么…", color = TextDim, fontSize = 13.sp) },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.padding(4.dp))
        PressScale(onClick = { onSend(text); text = "" }) {
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(MainGradient).padding(horizontal = 16.dp, vertical = 11.dp)) {
                Text("发送", color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

private fun join(context: Context, code: String) {
    val i = Intent(context, ListenTogetherService::class.java)
    i.setAction("com.luogen.music.together.JOIN")
    i.putExtra("room", code)
    context.startForegroundService(i)
}

private fun makeQr(content: String): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 320, 320, hints)
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val offset = y * w
        for (x in 0 until w) {
            pixels[offset + x] = if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

private fun copyText(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
        .setPrimaryClip(android.content.ClipData.newPlainText("room", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}