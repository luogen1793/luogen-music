package com.luogen.music.ui.mine

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luogen.music.LuogenApp
import com.luogen.music.data.db.DownloadEntity
import com.luogen.music.data.local.FavoriteStore
import com.luogen.music.domain.model.Song
import com.luogen.music.service.ListenTogetherService
import com.luogen.music.ui.components.AmbientBackground
import com.luogen.music.ui.components.CoverArt
import com.luogen.music.ui.components.GlassCard
import com.luogen.music.ui.components.PressScale
import com.luogen.music.ui.components.RoundInputField
import com.luogen.music.ui.components.SongRow
import com.luogen.music.ui.components.songListKey
import com.luogen.music.ui.theme.LgPink
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.MainGradient
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** 我的页 ViewModel：下载列表、我的歌单、最近听过 */
class MineViewModel : ViewModel() {
    data class UiState(
        val downloads: List<DownloadEntity> = emptyList(),
        val playlists: List<com.luogen.music.data.db.MyPlaylistEntity> = emptyList(),
        val history: List<Song> = emptyList(),
        val loading: Boolean = true,
        val importedCount: Int = 0,
    )
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            // 整体兜底：即便历史/下载数据异常也不允许闪退，页面退化为空列表
            runCatching {
                val dao = LuogenApp.instance.database.dao()
                val d = withContext(Dispatchers.IO) { dao.downloads() }
                val p = withContext(Dispatchers.IO) { dao.playlists() }
                val h = withContext(Dispatchers.IO) { dao.history() }
                val json = Json { ignoreUnknownKeys = true }
                val history = h.mapNotNull { e ->
                    runCatching { json.decodeFromString(Song.serializer(), e.songJson) }.getOrNull()
                }
                _ui.value = UiState(d, p, history, false, importedCount = d.size)
            }.onFailure {
                _ui.value = UiState(loading = false)
            }
        }
    }

    fun deleteDownload(entity: DownloadEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 清理 MediaStore 记录/公共文件 + 旧版私有缓存
                com.luogen.music.util.MediaActions.deleteDownloadFiles(LuogenApp.instance, entity)
                LuogenApp.instance.database.dao().deleteDownload(entity.songId)
            }
            refresh()
        }
    }
}

@Composable
fun MineScreen(
    toSettings: () -> Unit,
    toTogether: () -> Unit,
    toBluetooth: () -> Unit,
    toScan: () -> Unit,
    toPlayer: () -> Unit,
    toPlay: (List<Song>, Int) -> Unit,
    toFav: () -> Unit,
    toHistory: () -> Unit,
    toPlaylist: (Long) -> Unit,
    vm: MineViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }
    val settings = LuogenApp.instance.settings

    // 每次进入「我的」页刷新最近听过/下载/歌单（播放记录在后台服务中写入）
    LaunchedEffect(Unit) { vm.refresh() }

    // 我喜欢的歌曲：全局收藏状态（播放页爱心 ⇄ 本列表实时同步）数量展示
    val favSongs by FavoriteStore.songs.collectAsState()

    // 个人资料：昵称 + 头像（DataStore 持久化）
    val myNick by settings.myNick.collectAsState(initial = "")
    val myAvatar by settings.myAvatar.collectAsState(initial = "")
    var showNickDialog by remember { mutableStateOf(false) }
    val avatarPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = com.luogen.music.util.MediaActions.importAvatar(context, uri).getOrNull()
                if (path != null) {
                    settings.setMyAvatar(path)
                    Toast.makeText(context, "头像已更新", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "头像导入失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ===== 我的歌单：创建弹窗（名称 + 可选自定义头像）+ 各歌单歌曲数缓存 =====
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistCover by remember { mutableStateOf("") }
    var plCounts by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    LaunchedEffect(ui.playlists) {
        plCounts = runCatching {
            ui.playlists.associate { p -> p.id to com.luogen.music.data.local.PlaylistStore.songs(p).size }
        }.getOrDefault(emptyMap())
    }
    val createCoverPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = com.luogen.music.util.MediaActions.importAvatar(context, uri).getOrNull()
                if (path != null) newPlaylistCover = path
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(extraGlow = com.luogen.music.ui.theme.LgPink)
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    // 头像：自定义头像优先（本地上传），否则默认占位
                    PressScale(onClick = { avatarPicker.launch("image/*") }) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(MainGradient), contentAlignment = Alignment.Center) {
                            if (myAvatar.isNotBlank() && java.io.File(myAvatar).exists()) {
                                androidx.compose.foundation.Image(
                                    bitmap = (com.luogen.music.util.BitmapLoader.decodeSampled(myAvatar, 256)
                                        ?: return@Box).asImageBitmap(),
                                    contentDescription = "头像",
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                )
                            } else {
                                Icon(Icons.Rounded.Person, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (myNick.isNotBlank()) myNick else "罗根乐友", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("点击头像上传 / 点击昵称修改", color = TextDim, fontSize = 12.sp, modifier = Modifier.clickable { showNickDialog = true })
                    }
                    Spacer(Modifier.weight(1f))
                    // 编辑昵称按钮
                    PressScale(onClick = { showNickDialog = true }) {
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Edit, "修改昵称", tint = TextDim, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FunCard(Icons.Rounded.GraphicEq, "听歌识曲", Modifier.weight(1f)) { toScan() }
                    FunCard(Icons.Rounded.Group, "一起听", Modifier.weight(1f)) { toTogether() }
                    FunCard(Icons.Rounded.Bluetooth, "蓝牙设备", Modifier.weight(1f)) { toBluetooth() }
                    FunCard(Icons.Rounded.Settings, "设置", Modifier.weight(1f)) { toSettings() }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }

            // ===== 我的喜欢 / 最近听过：模块化入口（点击进入独立详情页）=====
            item { ModuleEntryCard(Icons.Rounded.Favorite, "我的喜欢", "${favSongs.size} 首", "收藏的歌曲一键播放 · 支持搜索", LgPink, toFav) }
            item { Spacer(Modifier.height(10.dp)) }
            item { ModuleEntryCard(Icons.Rounded.History, "最近听过", "${ui.history.size} 首", "自动记录播放过的每首歌 · 支持搜索", LgSecondary, toHistory) }

            item { Spacer(Modifier.height(20.dp)) }

            // ===== 我的歌单：多歌单模块卡片（新建 / 点击进入详情）=====
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.QueueMusic, null, tint = LgSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("我的歌单（${ui.playlists.size}）", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    PressScale(onClick = { showCreatePlaylist = true }) {
                        Row(
                            Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = LgSecondary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("新建歌单", color = LgSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (ui.playlists.isEmpty()) {
                item {
                    Text("还没有歌单：点右上角「新建歌单」创建；在任意歌曲列表点「三点 → 添加到歌单」即可加入", color = TextDim, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
            } else {
                items(ui.playlists, key = { "pl:${it.id}" }) { p ->
                    PlaylistCard(p, count = plCounts[p.id] ?: 0, onClick = { toPlaylist(p.id) })
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
            item {
                Text("已下载（${ui.downloads.size}）", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
            }
            if (ui.downloads.isEmpty()) {
                item { Text("还没有已下载的歌曲，播放页点击「下载」即可离线收听", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) }
            } else {
                items(ui.downloads, key = { "dl:${it.songId}" }) { d ->
                    val song = runCatching { json.decodeFromString(Song.serializer(), d.songJson) }.getOrNull()
                        ?.copy(localUri = d.filePath)
                        ?: Song(id = d.songId, name = "已下载歌曲", source = "download", localUri = d.filePath)
                    SongRow(
                        song = song, index = 0, showCover = true,
                        onPlay = {
                            runCatching {
                                val list = ui.downloads.mapNotNull {
                                    runCatching { json.decodeFromString(Song.serializer(), it.songJson) }.getOrNull()
                                        ?.copy(localUri = it.filePath)
                                }
                                PlayLocalSongs(toPlay, list.ifEmpty { listOf(song) })
                            }
                        },
                        menuExtra = {
                            DropdownMenuItem(
                                text = { Text("删除下载") },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                                onClick = { vm.deleteDownload(d) },
                            )
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
            item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column {
                        Text("本地媒体", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("本地音乐自动识别、上传自定义封面、圆形/方形/沉浸式/玻璃封面样式、设为铃声、下载歌曲——全部在「本地」与「播放页」可一键完成。", color = TextDim, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("发现音乐 · 作者：罗根", color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("酷狗概念版风格 · 极致质感 · 全部功能真实可用", color = TextDim, fontSize = 11.sp)
                }
            }
        }

        // 昵称编辑对话框
        if (showNickDialog) {
            var nickInput by remember { mutableStateOf(myNick) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showNickDialog = false },
                containerColor = androidx.compose.ui.graphics.Color(0xF2242036),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                title = {
                    Column {
                        Text("修改昵称", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text("最多 20 个字符", color = TextDim, fontSize = 11.sp)
                    }
                },
                text = {
                    com.luogen.music.ui.components.RoundInputField(
                        value = nickInput,
                        onValueChange = { nickInput = it },
                        placeholder = "输入新昵称",
                        maxLength = 20,
                    )
                },
                confirmButton = {
                    Text(
                        "保存",
                        color = LgSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(8.dp)
                            .clickable {
                                if (nickInput.isNotBlank()) {
                                    scope.launch { settings.setMyNick(nickInput.trim()) }
                                }
                                showNickDialog = false
                            },
                    )
                },
                dismissButton = {
                    Text("取消", color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(8.dp).clickable { showNickDialog = false })
                },
            )
        }

        // 新建歌单对话框：名称 + 可选自定义头像（不选则用默认渐变头像）
        if (showCreatePlaylist) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showCreatePlaylist = false; newPlaylistName = ""; newPlaylistCover = "" },
                shape = RoundedCornerShape(24.dp),
                containerColor = androidx.compose.ui.graphics.Color(0xF2242036),
                title = { Text("新建歌单", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                text = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PressScale(onClick = { createCoverPicker.launch("image/*") }) {
                                Box(Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(MainGradient), contentAlignment = Alignment.Center) {
                                    if (newPlaylistCover.isNotBlank() && java.io.File(newPlaylistCover).exists()) {
                                        androidx.compose.foundation.Image(
                                            bitmap = (com.luogen.music.util.BitmapLoader.decodeSampled(newPlaylistCover, 256) ?: return@Box).asImageBitmap(),
                                            contentDescription = "歌单头像",
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        )
                                    } else {
                                        Icon(Icons.Rounded.QueueMusic, null, tint = Color.White, modifier = Modifier.size(30.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("点击选择歌单头像", color = TextDim, fontSize = 12.sp)
                                Spacer(Modifier.height(3.dp))
                                Text("不选则使用默认头像", color = TextDim, fontSize = 10.sp)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        RoundInputField(value = newPlaylistName, onValueChange = { newPlaylistName = it }, placeholder = "歌单名称", maxLength = 30)
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        enabled = newPlaylistName.isNotBlank(),
                        onClick = {
                            scope.launch {
                                val id = runCatching { com.luogen.music.data.local.PlaylistStore.create(newPlaylistName, newPlaylistCover) }.getOrDefault(0L)
                                if (id > 0L) Toast.makeText(context, "歌单「${newPlaylistName.trim()}」创建成功", Toast.LENGTH_SHORT).show()
                                else Toast.makeText(context, "创建失败，请重试", Toast.LENGTH_SHORT).show()
                                newPlaylistName = ""; newPlaylistCover = ""; showCreatePlaylist = false
                                vm.refresh()
                            }
                        },
                    ) { Text("创建", color = if (newPlaylistName.isNotBlank()) LgSecondary else TextDim, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showCreatePlaylist = false; newPlaylistName = ""; newPlaylistCover = "" }) { Text("取消", color = TextDim) }
                },
            )
        }
    }
}

@Composable
private fun FunCard(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PressScale(modifier = modifier, onClick = onClick) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = LgSecondary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, color = TextMain, fontSize = 12.sp)
        }
    }
}

private fun PlayLocalSongs(onPlay: (List<Song>, Int) -> Unit, songs: List<Song>) {
    if (songs.isNotEmpty()) onPlay(songs, 0)
}

/** 模块化入口卡片：图标 + 标题 + 数量 + 描述，点击进入独立详情页 */
@Composable
private fun ModuleEntryCard(icon: ImageVector, title: String, count: String, desc: String, tint: Color, onClick: () -> Unit) {
    PressScale(onClick = onClick) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(count, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(3.dp))
                Text(desc, color = TextDim, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = TextDim, modifier = Modifier.size(20.dp))
        }
    }
}

/** 歌单模块卡片：封面（自定义头像或默认渐变图标）+ 名称 + 歌曲数，点击进入歌单详情 */
@Composable
private fun PlaylistCard(p: com.luogen.music.data.db.MyPlaylistEntity, count: Int, onClick: () -> Unit) {
    PressScale(onClick = onClick) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MainGradient), contentAlignment = Alignment.Center) {
                if (p.coverPath.isNotBlank() && java.io.File(p.coverPath).exists()) {
                    androidx.compose.foundation.Image(
                        bitmap = (com.luogen.music.util.BitmapLoader.decodeSampled(p.coverPath, 160) ?: return@Box).asImageBitmap(),
                        contentDescription = p.name,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    Icon(Icons.Rounded.QueueMusic, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text("$count 首歌曲", color = TextDim, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = TextDim, modifier = Modifier.size(20.dp))
        }
    }
}