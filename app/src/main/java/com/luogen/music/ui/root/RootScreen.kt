package com.luogen.music.ui.root

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalActivity
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.luogen.music.domain.model.RankList
import com.luogen.music.domain.model.Song
import com.luogen.music.domain.model.Video
import com.luogen.music.service.ListenTogetherService
import com.luogen.music.ui.AppNavigator
import com.luogen.music.ui.bluetooth.BluetoothScreen
import com.luogen.music.ui.components.AmbientBackground
import com.luogen.music.ui.content.AlbumScreen
import com.luogen.music.ui.content.AlbumViewModel
import com.luogen.music.ui.content.ArtistScreen
import com.luogen.music.ui.content.ArtistViewModel
import com.luogen.music.ui.content.QueueScreen
import com.luogen.music.ui.content.RankListScreen
import com.luogen.music.ui.content.RankListViewModel
import com.luogen.music.ui.content.SongInfoScreen
import com.luogen.music.ui.home.HomeScreen
import com.luogen.music.ui.home.HomeViewModel
import com.luogen.music.ui.local.LocalScreen
import com.luogen.music.ui.local.LocalViewModel
import com.luogen.music.ui.mine.MineScreen
import com.luogen.music.ui.mine.MyListScreen
import com.luogen.music.ui.mine.PlaylistDetailScreen
import com.luogen.music.ui.player.PlayerScreen
import com.luogen.music.ui.player.PlayerViewModel
import com.luogen.music.ui.radio.RadioScreen
import com.luogen.music.ui.scan.SongScanScreen
import com.luogen.music.ui.search.SearchScreen
import com.luogen.music.ui.search.SearchViewModel
import com.luogen.music.ui.settings.SettingsScreen
import com.luogen.music.ui.theme.LgPrimary
import com.luogen.music.ui.theme.MainGradient
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain
import com.luogen.music.ui.together.TogetherScreen
import com.luogen.music.ui.video.VideoPlayerScreen
import com.luogen.music.util.explodeSong
import com.luogen.music.util.implodeSong
import kotlinx.serialization.json.Json

/**
 * 发现音乐主框架：底部导航（发现/搜索/本地/我的）+ 全应用路由
 */
@Composable
fun RootScreen(initialIntent: Intent?) {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val deepLink = remember { mutableStateOf(initialIntent?.data?.toString()) }
    var rankTarget by remember { mutableStateOf<RankList?>(null) }

    LaunchedEffect(Unit) {
        if (initialIntent?.data != null) handleDeepLink(initialIntent.data!!, nav)
    }

    // 监听全局导航事件（如通知栏点击 → 播放页）
    LaunchedEffect(Unit) {
        AppNavigator.events.collect { route -> nav.navigate(route) }
    }

    // 底部导航显示的页面（发现/搜索/本地/我的）；播放页等全屏路由隐藏
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val baseRoute = route?.substringBefore("?") ?: "home"
    val showBar = baseRoute in listOf("home", "search", "local", "mine")

    val homeVm: HomeViewModel = viewModel()
    val searchVm: SearchViewModel = viewModel()
    val localVm: LocalViewModel = viewModel()
    val playerVm: PlayerViewModel = viewModel()

    // 用 Column 流式布局而非 Box 悬浮：NavHost 占满 BottomBar 以上空间，
    // 页面内嵌的迷你播放条 align(BottomCenter) 自然落在 BottomBar 之上，
    // 不会被底部导航遮挡，且可正常点击。
    Column(Modifier.fillMaxSize()) {
        NavHost(
            nav,
            startDestination = "home",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = homeVm,
                    toSearch = { kw, videoOnly ->
                        nav.navigate("search?keyword=${Uri.encode(kw)}&video=$videoOnly")
                    },
                    toScan = { nav.navigate("scan") },
                    toOpenPlayer = { nav.navigate("player") },
                    toArtist = { id -> nav.navigate("artist/$id") },
                    toAlbum = { id -> nav.navigate("album/$id") },
                    toRankList = { tl -> rankTarget = tl; nav.navigate("rank/${tl.id}") },
                    toRadio = { p -> nav.navigate("radio/${p.name}") },
                )
            }
            composable(
                route = "search?keyword={keyword}&video={video}",
                arguments = listOf(
                    androidx.navigation.navArgument("keyword") { type = androidx.navigation.NavType.StringType; defaultValue = "" },
                    androidx.navigation.navArgument("video") { type = androidx.navigation.NavType.BoolType; defaultValue = false },
                ),
            ) { back ->
                val kw = back.arguments?.getString("keyword").orEmpty()
                val video = back.arguments?.getBoolean("video") == true
                SearchScreen(
                    viewModel = searchVm,
                    initialKeyword = kw,
                    initialVideoOnly = video,
                    toArtist = { id -> nav.navigate("artist/$id") },
                    toAlbum = { id -> nav.navigate("album/$id") },
                    toPlayVideo = { v -> nav.navigate("video/${Uri.encode(implodeSong(Song(id = v.id, name = v.name, coverUrl = v.coverUrl, mvid = v.id, isVideo = true)))}") },
                    toOpenPlayer = { nav.navigate("player") },
                )
            }
            composable("local") {
                LocalScreen(
                    viewModel = localVm,
                    onPlayVideo = { s -> nav.navigate("video/${Uri.encode(implodeSong(s))}") },
                    onUploadCover = { s -> nav.navigate("info/${Uri.encode(implodeSong(s))}") },
                    onOpenPlayer = { nav.navigate("player") },
                )
            }
            composable("mine") {
                MineScreen(
                    toSettings = { nav.navigate("settings") },
                    toTogether = { nav.navigate("together") },
                    toBluetooth = { nav.navigate("bluetooth") },
                    toScan = { nav.navigate("scan") },
                    toPlayer = { nav.navigate("player") },
                    toPlay = { songs, idx ->
                        com.luogen.music.service.PlaybackService.playQueue(ctx, songs, idx)
                        nav.navigate("player")
                    },
                    toFav = { nav.navigate("mine-fav") },
                    toHistory = { nav.navigate("mine-history") },
                    toPlaylist = { id -> nav.navigate("playlist/$id") },
                )
            }
            composable("mine-fav") {
                MyListScreen(kind = 0, title = "我的喜欢", onBack = { nav.popBackStack() }, toPlay = { songs, idx ->
                    com.luogen.music.service.PlaybackService.playQueue(ctx, songs, idx)
                    nav.navigate("player")
                })
            }
            composable("mine-history") {
                MyListScreen(kind = 1, title = "最近听过", onBack = { nav.popBackStack() }, toPlay = { songs, idx ->
                    com.luogen.music.service.PlaybackService.playQueue(ctx, songs, idx)
                    nav.navigate("player")
                })
            }
            composable("playlist/{id}") { back ->
                val id = back.arguments?.getString("id")?.toLongOrNull() ?: 0L
                PlaylistDetailScreen(playlistId = id, onBack = { nav.popBackStack() }, toPlay = { songs, idx ->
                    com.luogen.music.service.PlaybackService.playQueue(ctx, songs, idx)
                    nav.navigate("player")
                })
            }
            composable("player") { PlayerScreen(viewModel = playerVm, onBack = { nav.popBackStack() }, onShowQueue = { nav.navigate("queue") }, onShowInfo = { s -> nav.navigate("info/${Uri.encode(implodeSong(s))}") }, toArtist = { nav.navigate("artist/$it") }) }
            composable("queue") { QueueScreen(onBack = { nav.popBackStack() }) }
            composable("scan") { SongScanScreen(onBack = { nav.popBackStack() }, onPlay = { songs, i -> com.luogen.music.service.PlaybackService.playQueue(ctx, songs, i); nav.navigate("player") }) }
            composable(
                route = "radio/{persona}",
                arguments = listOf(androidx.navigation.navArgument("persona") { defaultValue = "HOT" }),
            ) { back ->
                val persona = runCatching { com.luogen.music.domain.model.RaType.valueOf(back.arguments?.getString("persona") ?: "HOT") }.getOrDefault(com.luogen.music.domain.model.RaType.HOT)
                RadioScreen(initialPersona = persona, onBack = { nav.popBackStack() }, onPlay = { songs, i -> com.luogen.music.service.PlaybackService.playQueue(ctx, songs, i); nav.navigate("player") }, toArtist = { nav.navigate("artist/$it") })
            }
            composable("together") { TogetherScreen(onBack = { nav.popBackStack() }) }
            composable("bluetooth") { BluetoothScreen(onBack = { nav.popBackStack() }) }
            composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable("artist/{id}") { back ->
                val id = back.arguments?.getString("id")?.toLongOrNull() ?: 0L
                val vm: ArtistViewModel = viewModel(
                    key = "artist_$id",
                    factory = viewModelFactory { initializer { ArtistViewModel(id) } },
                )
                ArtistScreen(vm, onBack = { nav.popBackStack() }, toAlbum = { nav.navigate("album/$it") })
            }
            composable("album/{id}") { back ->
                val id = back.arguments?.getString("id")?.toLongOrNull() ?: 0L
                val vm: AlbumViewModel = viewModel(
                    key = "album_$id",
                    factory = viewModelFactory { initializer { AlbumViewModel(id) } },
                )
                AlbumScreen(vm, onBack = { nav.popBackStack() }, toOpenPlayer = { nav.navigate("player") })
            }
            composable("rank/{id}") { back ->
                val id = back.arguments?.getString("id")?.toLongOrNull() ?: 0L
                val vm: RankListViewModel = viewModel(
                    key = "rank_$id",
                    factory = viewModelFactory { initializer { RankListViewModel(id) } },
                )
                RankListScreen(vm, onBack = { nav.popBackStack() }, toOpenPlayer = { nav.navigate("player") })
            }
            composable("info/{song}") { back ->
                val song = back.arguments?.getString("song")?.let { Uri.decode(it) }?.let { explodeSong(it) }
                if (song != null) {
                    SongInfoScreen(song, onBack = { nav.popBackStack() }, toArtist = { nav.navigate("artist/$it") })
                }
            }
            composable("video/{song}") { back ->
                val song = back.arguments?.getString("song")?.let { Uri.decode(it) }?.let { explodeSong(it) }
                if (song != null) VideoPlayerScreen(song, onBack = { nav.popBackStack() })
            }
        }
        // 底部导航（隐藏于播放页等全屏场景）
        if (showBar) {
            BottomBar(
                route = baseRoute,
                onSelect = { r ->
                    val target = if (r == "search") "search?keyword=&video=false" else r
                    nav.navigate(target) { popUpTo("home"); launchSingleTop = true }
                },
            )
        }
    }
}

@Composable
private fun BottomBar(route: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(Color(0xD91A1A2E))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomItem("home", "发现", Icons.Rounded.Home, route, Modifier.weight(1f), onSelect)
        BottomItem("search", "搜索", Icons.Rounded.Search, route, Modifier.weight(1f), onSelect)
        BottomItem("local", "本地", Icons.Rounded.LocalActivity, route, Modifier.weight(1f), onSelect)
        BottomItem("mine", "我的", Icons.Rounded.Person, route, Modifier.weight(1f), onSelect)
    }
}

@Composable
private fun BottomItem(route: String, label: String, icon: ImageVector, current: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val selected = current == route
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect(route) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) MainGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = if (selected) Color.White else TextDim, modifier = Modifier.size(20.dp))
        }
        Text(label, color = if (selected) LgPrimary else TextDim, fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private fun handleDeepLink(uri: Uri, nav: NavHostController) {
    val scheme = uri.scheme
    if (scheme == "luogen") {
        when (uri.host) {
            "song" -> uri.lastPathSegment?.toLongOrNull()?.let { id ->
                nav.navigate("player")
                com.luogen.music.ui.root.DeepPlay.request(id)
            }
            "room" -> nav.navigate("together")
            "open" -> {
                // open/player → 直接跳转到歌曲播放页；其他 → 首页
                if (uri.lastPathSegment == "player") nav.navigate("player")
                else nav.navigate("home")
            }
        }
    }
}

/** 深链播放请求（简化内存通道） */
object DeepPlay {
    private var pending = 0L
    fun request(songId: Long) { pending = songId }
    fun consume(): Long = pending.also { pending = 0L }
}