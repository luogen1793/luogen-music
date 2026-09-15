package com.luogen.music.ui.content

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.luogen.music.LuogenApp
import com.luogen.music.data.local.CoverStore
import com.luogen.music.domain.model.PlayMode
import com.luogen.music.domain.model.Song
import com.luogen.music.domain.model.SongComment
import com.luogen.music.player.PlayerHub
import com.luogen.music.service.PlaybackService
import com.luogen.music.ui.components.AmbientBackground
import com.luogen.music.ui.components.CoverArt
import com.luogen.music.ui.components.GlassGradientButton
import com.luogen.music.ui.components.GlassIconButton
import com.luogen.music.ui.components.SectionTitle
import com.luogen.music.ui.components.SongRow
import com.luogen.music.ui.components.songListKey
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain
import com.luogen.music.util.MediaActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------- 专辑 ----------------
class AlbumViewModel(private val albumId: Long) : ViewModel() {
    data class UiState(
        val name: String = "", val pic: String = "", val artist: String = "",
        val publishTime: Long = 0, val size: Int = 0, val desc: String = "",
        val songs: List<Song> = emptyList(), val loading: Boolean = true, val error: String? = null,
    )
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                val (album, songs) = withContext(Dispatchers.IO) {
                    kotlinx.coroutines.withTimeout(9000) { LuogenApp.instance.repository.albumDetail(albumId) }
                }
                _ui.value = UiState(album.name, album.picUrl, album.artistNames.joinToString(" / "),
                    album.publishTime, album.size, album.description, songs, false)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message)
            }
        }
    }
}

@Composable
fun AlbumScreen(viewModel: AlbumViewModel, onBack: () -> Unit, toOpenPlayer: () -> Unit = {}) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding()) {
            item {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, size = 40.dp, onClick = onBack)
                    Spacer(Modifier.width(8.dp))
                    Text(ui.name, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(Song(name = ui.name, coverUrl = ui.pic), 120.dp, style = "square")
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(ui.name, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Text(ui.artist, color = LgSecondary, fontSize = 13.sp)
                        Text(pubTime(ui.publishTime) + if (ui.size > 0) " · ${ui.size}首" else "", color = TextDim, fontSize = 12.sp)
                    }
                }
            }
            item {
                if (ui.songs.isNotEmpty()) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        GlassGradientButton("播放全部", icon = Icons.Rounded.PlayArrow, onClick = {
                            PlaybackService.playQueue(context, ui.songs, 0)
                            toOpenPlayer()
                        })
                    }
                }
            }
            items(ui.songs, key = { songListKey(it) }) { song ->
                SongRow(song = song, index = 0, onPlay = {
                    PlaybackService.playQueue(context, ui.songs, ui.songs.indexOf(song).coerceAtLeast(0))
                    toOpenPlayer()
                })
            }
        }
    }
}

// ---------------- 榜单 ----------------
class RankListViewModel(private val rankId: Long) : ViewModel() {
    data class UiState(val name: String = "", val cover: String = "", val songs: List<Song> = emptyList(), val loading: Boolean = true)
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                // 榜单歌曲与榜单信息并行加载，各自超时兜底，任一失败都快速结束 loading
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        val t = async {
                            runCatching {
                                kotlinx.coroutines.withTimeout(7000) { LuogenApp.instance.repository.topListTracks(rankId, 100) }
                            }.getOrDefault(emptyList())
                        }
                        val l = async {
                            runCatching {
                                kotlinx.coroutines.withTimeout(5000) { LuogenApp.instance.repository.topLists() }
                            }.getOrDefault(emptyList())
                        }
                        val tl = l.await().firstOrNull { it.id == rankId }
                        _ui.value = UiState(tl?.name ?: "榜单", tl?.coverUrl ?: "", t.await(), false)
                    }
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false)
            }
        }
    }
}

@Composable
fun RankListScreen(viewModel: RankListViewModel, onBack: () -> Unit, toOpenPlayer: () -> Unit = {}) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        AmbientBackground(extraGlow = LgSecondary)
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding()) {
            item {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, size = 40.dp, onClick = onBack)
                    Spacer(Modifier.width(8.dp))
                    Text(ui.name, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("实时更新", color = TextDim, fontSize = 11.sp)
                }
            }
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(Song(name = ui.name, coverUrl = ui.cover), 110.dp, style = "square")
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(ui.name, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("榜单实时更新 · 共${ui.songs.size}首", color = TextDim, fontSize = 12.sp)
                    }
                }
            }
            item {
                if (ui.songs.isNotEmpty()) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        GlassGradientButton("播放全部", icon = Icons.Rounded.PlayArrow, onClick = {
                            PlaybackService.playQueue(context, ui.songs, 0)
                            toOpenPlayer()
                        })
                    }
                }
            }
            items(ui.songs, key = { songListKey(it) }) { song ->
                SongRow(song = song, index = 0, onPlay = {
                    PlaybackService.playQueue(context, ui.songs, ui.songs.indexOf(song).coerceAtLeast(0))
                    toOpenPlayer()
                })
            }
        }
    }
}

// ---------------- 播放队列 ----------------
@Composable
fun QueueScreen(onBack: () -> Unit) {
    val state by PlayerHub.state.collectAsState()
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, size = 40.dp, onClick = onBack)
                Spacer(Modifier.width(8.dp))
                Text("播放队列", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    when (state.mode) {
                        PlayMode.SEQUENCE -> "顺序播放"
                        PlayMode.REPEAT_ALL -> "列表循环"
                        PlayMode.REPEAT_ONE -> "单曲循环"
                        PlayMode.SHUFFLE -> "随机播放"
                    },
                    color = LgSecondary, fontSize = 12.sp,
                )
            }
            if (state.songs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("队列为空，去搜索一首歌吧", color = TextDim, fontSize = 13.sp)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(vertical = 6.dp)) {
                    items(state.songs.size) { i ->
                        val song = state.songs[i]
                        SongRow(
                            song = song, index = i, showCover = true, isCurrent = i == state.index,
                            showRemove = true,
                            onPlay = { PlaybackService.playIndex(context, i) },
                            onRemove = { PlaybackService.removeAt(context, i) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------- 歌曲信息 ----------------
@Composable
fun SongInfoScreen(song: Song, onBack: () -> Unit, toArtist: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var customCover by remember { mutableStateOf(CoverStore.get(song.id)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = MediaActions.importCover(context, song.id, uri).getOrNull()
                if (path != null) {
                    CoverStore.set(song.id, path)
                    customCover = path
                    Toast.makeText(context, "封面已更新", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "封面导入失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val customValue = customCover
    val displaySong = if (customValue != null) song.copy(coverUrl = customValue) else song

    // ---------- 评论 ----------
    var comments by remember { mutableStateOf<List<SongComment>>(emptyList()) }
    var commentsLoading by remember { mutableStateOf(false) }
    var activeComment by remember { mutableStateOf<SongComment?>(null) }   // 点选某条评论（操作菜单）
    var replyTarget by remember { mutableStateOf<SongComment?>(null) }     // 回复弹窗目标
    var replyText by remember { mutableStateOf("") }
    val isLocal = song.source == "local" || song.source == "download"
    LaunchedEffect(song.id) {
        if (isLocal || song.id <= 0L) return@LaunchedEffect
        commentsLoading = true
        comments = withContext(Dispatchers.IO) {
            runCatching { LuogenApp.instance.repository.comments(song.id) }.getOrDefault(emptyList())
        }
        commentsLoading = false
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(extraGlow = LgSecondary)
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 30.dp)) {
            item {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, size = 40.dp, onClick = onBack)
                    Spacer(Modifier.width(8.dp))
                    Text("歌曲信息", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        CoverArt(displaySong, 160.dp, style = "glass")
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable { picker.launch("image/*") },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.CameraAlt, "上传封面", tint = TextMain, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(song.name, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(song.artistNames.joinToString(" / "), color = TextDim, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(if (customCover != null) "已使用自定义封面（点击右上角图标可更换）" else "点击右下角图标可上传自定义封面",
                        color = TextDim, fontSize = 11.sp)
                }
            }
            item { Spacer(Modifier.height(10.dp)) }
            item { SectionTitle("详细信息", modifier = Modifier.padding(horizontal = 20.dp)) }
            item {
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 10.dp)) {
                    InfoRow("歌曲", song.name)
                    InfoRow("歌手", song.artistNames.joinToString(" / "))
                    InfoRow("专辑", song.albumName.ifBlank { "单曲/未知" })
                    if (song.durationMs > 0) InfoRow("时长", "${song.durationMs / 60000}:${String.format("%02d", (song.durationMs % 60000) / 1000)}")
                    InfoRow("音质", "320Kbps 无损流媒体" + if (song.fee > 0) " · 会员歌曲" else " · 免费")
                    InfoRow("来源", when (song.source) {
                        "local" -> "本地设备"
                        "download" -> "已下载"
                        else -> "网易云音乐在线"
                    })
                }
            }
            if (song.artistIds.isNotEmpty()) {
                item { Spacer(Modifier.height(18.dp)) }
                item { SectionTitle("歌手", modifier = Modifier.padding(horizontal = 20.dp)) }
                item {
                    Row(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)) {
                        song.artistIds.forEachIndexed { i, aid ->
                            Text(
                                song.artistNames.getOrElse(i) { "歌手" },
                                color = LgSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.07f))
                                    .clickable { toArtist(aid) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
            item { SectionTitle(
                "评论",
                modifier = Modifier.padding(horizontal = 20.dp),
                subtitle = if (comments.isNotEmpty()) "${comments.size} 条 · ${comments.sumOf { it.replies.size }} 回复" else null,
            ) }
            item {
                Text(
                    when {
                        isLocal -> "本地歌曲暂无在线评论"
                        commentsLoading -> "加载评论中…"
                        comments.isEmpty() -> "暂无评论，快来抢沙发"
                        else -> "点击评论可复制或回复"
                    },
                    color = TextDim, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
                )
            }
            if (comments.isNotEmpty()) {
                items(comments, key = { it.commentId }) { c ->
                    CommentItem(c, onActivate = { activeComment = it })
                }
            }
        }
    }
    // 评论操作菜单：复制 / 回复（毛玻璃圆角卡片）
    activeComment?.let { c ->
        AlertDialog(
            onDismissRequest = { activeComment = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color(0xF2242036),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))) {
                        if (c.avatarUrl.isNotBlank()) {
                            AsyncImage(model = c.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Rounded.Person, null, tint = TextDim, modifier = Modifier.fillMaxSize().padding(7.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(c.nickname, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(commentTime(c.time), color = TextDim, fontSize = 11.sp)
                    }
                }
            },
            text = {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f)).padding(12.dp),
                ) {
                    Text(c.content, color = TextMain, fontSize = 14.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { copyText(context, c.content); activeComment = null }) {
                    Text("复制", color = LgSecondary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { replyTarget = c; replyText = ""; activeComment = null }) {
                    Text("回复", color = LgSecondary, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
    // 回复弹窗：输入回复内容，点「回复」复制到剪贴板并提示（无平台登录态，可粘贴到任意平台发送）
    replyTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { replyTarget = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color(0xF2242036),
            title = {
                Column {
                    Text("回复 @${c.nickname}", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(c.content, color = TextDim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            },
            text = {
                com.luogen.music.ui.components.RoundInputField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = "写下你的回复…",
                    maxLength = 300,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = replyText.isNotBlank(),
                    onClick = {
                        copyText(context, "回复 @${c.nickname}：${replyText}", "访客模式无法直接发布：已复制回复内容，打开网易云 App 找到该评论点「回复」粘贴即可生效")
                        replyTarget = null
                        replyText = ""
                    },
                ) { Text("回复", color = if (replyText.isNotBlank()) LgSecondary else TextDim, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { replyTarget = null }) { Text("取消", color = TextDim) }
            },
        )
    }
}

/** 复制文本到剪贴板并提示（可选自定义提示语） */
private fun copyText(context: Context, text: String, hint: String = "已复制") {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("comment", text))
    Toast.makeText(context, hint, Toast.LENGTH_SHORT).show()
}

@Composable
private fun CommentItem(c: SongComment, onActivate: (SongComment) -> Unit) {
    var expandReplies by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onActivate(c) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))) {
            if (c.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = c.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = TextDim,
                    modifier = Modifier.fillMaxSize().padding(9.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.nickname, color = LgSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(commentTime(c.time), color = TextDim, fontSize = 11.sp)
            }
            if (c.replyToUser.isNotBlank()) {
                Text(
                    "回复 @${c.replyToUser}：${c.replyTo}",
                    color = TextDim, fontSize = 12.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(c.content, color = TextMain, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Rounded.ThumbUp, contentDescription = null, tint = TextDim, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(if (c.likedCount > 0) c.likedCount.toString() else "赞", color = TextDim, fontSize = 11.sp)
            }
            // ===== 楼中楼：该评论下的其他用户回复，可点击继续回复楼层 =====
            if (c.replies.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    val showing = if (expandReplies) c.replies else c.replies.take(5)
                    showing.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onActivate(r) }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(r.nickname, color = LgSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (r.replyToUser.isNotBlank()) {
                                Text(" 回复 ${r.replyToUser}", color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                r.content, color = TextMain.copy(alpha = 0.85f), fontSize = 12.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(start = 4.dp),
                            )
                        }
                    }
                    if (c.replies.size > 5) {
                        Text(
                            if (expandReplies) "收起回复" else "查看全部 ${c.replies.size} 条回复",
                            color = TextDim, fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 3.dp).clickable { expandReplies = !expandReplies },
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Color.White.copy(alpha = 0.05f))
}

/** 评论时间：5 分钟内"刚刚"，更早按相对时间，超 1 天显示日期 */
private fun commentTime(t: Long): String {
    if (t <= 0L) return ""
    val diff = System.currentTimeMillis() - t
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        else -> {
            val f = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            f.format(java.util.Date(t))
        }
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(k, color = TextDim, fontSize = 13.sp, modifier = Modifier.width(64.dp))
        Text(v, color = TextMain, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

private fun pubTime(t: Long): String = if (t <= 0) "发行时间未知" else {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = t }
    "${c.get(java.util.Calendar.YEAR)}-${c.get(java.util.Calendar.MONTH) + 1}-${c.get(java.util.Calendar.DAY_OF_MONTH)} 发行"
}