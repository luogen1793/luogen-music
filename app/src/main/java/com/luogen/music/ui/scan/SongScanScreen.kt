package com.luogen.music.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.luogen.music.LuogenApp
import com.luogen.music.domain.model.Song
import com.luogen.music.ui.components.AmbientBackground
import com.luogen.music.ui.components.CoverArt
import com.luogen.music.ui.components.GlassCard
import com.luogen.music.ui.components.GlassIconButton
import com.luogen.music.ui.components.PressScale
import com.luogen.music.ui.components.SongRow
import com.luogen.music.ui.components.songListKey
import com.luogen.music.ui.theme.LgPrimary
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.MainGradient
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import kotlin.math.sin

/**
 * 听歌识曲：真实采集环境声音（周围放歌/人唱均可）→ 上传识别服务 → 返回候选歌曲并可点击播放。
 * 识别服务端点可在“设置 → 听歌识曲服务”配置（默认官方公共端点，可自部署）。
 */
@Composable
fun SongScanScreen(
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
) {
    val context = LocalContext.current
    var hasMic by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasMic = it }

    var scanning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var resultSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var matchedTitle by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var pulse by remember { mutableStateOf(0f) }
    val uiScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(scanning) {
        while (scanning) {
            pulse = (pulse + 0.12f) % 1f
            delay(40)
        }
    }

    suspend fun doScan() {
        error = null
        matchedTitle = ""
        scanning = true
        progress = 0f
        try {
            val wav = withContext(Dispatchers.IO) { captureEnvAudio(context, 10) }
                ?: throw IllegalStateException("录音失败，请检查麦克风权限")
            repeat(10) {
                progress = (it + 1) / 10f
                delay(60)
            }
            val resp = withContext(Dispatchers.IO) { recognize(wav) }
            if (resp.networkError) {
                // 服务不可达：明确提示而不是误导为“没识别出”
                val endpoint = try { LuogenApp.instance.settings.recognizeServer.first() } catch (e: Exception) { "" }
                error = "识别服务不可达（${endpoint.ifBlank { RECOGNIZE_ENDPOINT }}${if (resp.httpCode != 0) " · HTTP ${resp.httpCode}" else ""}）。" +
                    "请在 设置 → 听歌识曲服务 配置可用地址；自建服务需支持：POST file=env.wav 返回 {\"title\",\"artist\"}，可用 Audd.io / ShazamIO 搭建。"
            } else if (resp.title.isBlank()) {
                error = "没能识别出这首歌，换个安静点的环境再试试"
            } else {
                matchedTitle = "${resp.title} · ${resp.artist}"
                val repo = LuogenApp.instance.repository
                val songs = withContext(Dispatchers.IO) {
                    repo.searchSongs("${resp.title} ${resp.artist}", 8)
                }
                resultSongs = songs.ifEmpty {
                    withContext(Dispatchers.IO) { repo.searchSongs(resp.title, 8) }
                }
                if (resultSongs.isEmpty()) error = "识别成功，但未找到可播放版本"
            }
        } catch (e: Exception) {
            error = e.message ?: "识别失败"
        } finally {
            scanning = false
            progress = 0f
        }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(extraGlow = LgPrimary)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, size = 40.dp, onClick = onBack)
                Spacer(Modifier.padding(4.dp))
                Text("听歌识曲", color = TextMain, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }

            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                        // 声波动画
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PressScale(
                                onClick = {
                                    if (!hasMic) { micLauncher.launch(Manifest.permission.RECORD_AUDIO); return@PressScale }
                                    if (!scanning) {
                                        resultSongs = emptyList()
                                        matchedTitle = ""
                                        uiScope.launch { doScan() }
                                    }
                                },
                            ) {
                                Box(
                                    Modifier
                                        .size(if (scanning) 130.dp else 116.dp)
                                        .clip(CircleShape)
                                        .background(MainGradient)
                                        .padding(0.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            if (scanning) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                                            null, tint = Color.White,
                                            modifier = Modifier.size(42.dp),
                                        )
                                        if (scanning) {
                                            Spacer(Modifier.height(4.dp))
                                            Text("识别中…", color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                    // 声波环
                                    if (scanning) {
                                        Box(
                                            Modifier
                                                .size(((pulse) * 200).dp)
                                                .clip(CircleShape)
                                                .background(LgPrimary.copy(alpha = (1f - pulse) * 0.25f))
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            Text(
                                if (scanning) "正在聆听环境声音…" else "点击开始，听歌识曲",
                                color = TextMain, fontSize = 15.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "支持识别周围播放的歌曲，也支持人声清唱 · 无需联网账号",
                                color = TextDim, fontSize = 11.sp,
                            )
                        }
                    }
                }

                if (error != null) {
                    item {
                        Text(error!!, color = androidx.compose.ui.graphics.Color(0xFFFF8A8A), fontSize = 13.sp, modifier = Modifier.padding(20.dp))
                    }
                }

                if (matchedTitle.isNotBlank()) {
                    item {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.GraphicEq, null, tint = LgSecondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.padding(4.dp))
                            Text("识别到：$matchedTitle", color = LgSecondary, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        }
                    }
                }

                items(resultSongs, key = { songListKey(it) }) { song ->
                    SongRow(
                        song = song, index = 0, showCover = true,
                        onPlay = { onPlay(resultSongs, resultSongs.indexOf(song).coerceAtLeast(0)) },
                    )
                }

                item { Spacer(Modifier.height(14.dp)) }
                item {
                    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column {
                            Text("识别服务说明", color = TextMain, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "① 默认公共端点可能不可用，识别失败时请先确认服务状态；\n" +
                                    "② 在 设置 → 听歌识曲服务 中填入自建/托管的识别接口地址；\n" +
                                    "③ 协议：POST multipart 上传 file=env.wav（10 秒 WAV）→ 返回 {\"title\":\"歌名\",\"artist\":\"歌手\"}；\n" +
                                    "④ 推荐用 Audd.io API 或 ShazamIO 开源项目一键部署（如 Render / Railway 免费容器）。",
                                color = TextDim, fontSize = 11.sp,
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }
}

/** 录音 10 秒 → WAV 数据（真实环境音采集） */
private fun captureEnvAudio(context: android.content.Context, seconds: Int): ByteArray? {
    val sampleRate = 44100
    var record: AudioRecord? = null
    return try {
        record = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            sampleRate * 2,
        )
        if (record!!.state != AudioRecord.STATE_INITIALIZED) return null
        record!!.startRecording()
        val bufSize = sampleRate * 2
        val buf = ShortArray(bufSize / 2)
        val pcm = ByteArrayOutputStream()
        val frames = seconds * sampleRate / (bufSize / 2)
        var total = 0
        while (total < frames) {
            val n = record!!.read(buf, 0, buf.size)
            if (n > 0) {
                val bytes = ByteArray(n * 2)
                for (i in 0 until n) {
                    val v = buf[i]
                    bytes[i * 2] = (v.toInt() and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((v.toInt() shr 8) and 0xFF).toByte()
                }
                pcm.write(bytes)
                total++
            }
        }
        record!!.stop()
        toWav(pcm.toByteArray(), sampleRate)
    } catch (e: Exception) {
        null
    } finally {
        runCatching { record?.release() }
    }
}

private fun toWav(pcm: ByteArray, sampleRate: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val dataLen = pcm.size
    val header = ByteArray(44)
    // RIFF
    header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
    writeIntLE(header, 4, 36 + dataLen)
    header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
    // fmt
    header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
    writeIntLE(header, 16, 16)
    writeShortLE(header, 20, 1)
    writeShortLE(header, 22, 1)
    writeIntLE(header, 24, sampleRate)
    writeIntLE(header, 28, sampleRate * 2)
    writeShortLE(header, 32, 2)
    writeShortLE(header, 34, 16)
    // data
    header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
    writeIntLE(header, 40, dataLen)
    out.write(header)
    out.write(pcm)
    return out.toByteArray()
}

private fun writeIntLE(b: ByteArray, offset: Int, v: Int) {
    b[offset] = (v and 0xFF).toByte()
    b[offset + 1] = ((v shr 8) and 0xFF).toByte()
    b[offset + 2] = ((v shr 16) and 0xFF).toByte()
    b[offset + 3] = ((v shr 24) and 0xFF).toByte()
}

private fun writeShortLE(b: ByteArray, offset: Int, v: Int) {
    b[offset] = (v and 0xFF).toByte()
    b[offset + 1] = ((v shr 8) and 0xFF).toByte()
}

data class RecognizeResult(
    val title: String = "",
    val artist: String = "",
    val networkError: Boolean = false,
    val httpCode: Int = 0,
)

/** 上传 WAV 到识别服务（端点可配置：设置 → 听歌识曲服务） */
private suspend fun recognize(wav: ByteArray): RecognizeResult = withContext(Dispatchers.IO) {
    val base = try { LuogenApp.instance.settings.recognizeServer.first() } catch (e: Exception) { "" }
    val endpoint = base.ifBlank { RECOGNIZE_ENDPOINT }
    val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    val body: RequestBody = wav.toRequestBody("audio/wav".toMediaType())
    val part = MultipartBody.Part.createFormData("file", "env.wav", body)
    val req = Request.Builder()
        .url(endpoint)
        .post(MultipartBody.Builder().setType(MultipartBody.FORM).addPart(part).build())
        .build()
    try {
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                return@withContext RecognizeResult(networkError = true, httpCode = resp.code)
            }
            val text = resp.body?.string() ?: ""
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            if (root == null) {
                return@withContext RecognizeResult(networkError = true, httpCode = resp.code)
            }
            val title = root["title"]?.toString()?.trim('"') ?: ""
            val artist = root["artist"]?.toString()?.trim('"') ?: ""
            RecognizeResult(title, artist)
        }
    } catch (e: Exception) {
        RecognizeResult(networkError = true)
    }
}

private const val RECOGNIZE_ENDPOINT = "https://lg-recognize.onrender.com/recognize"