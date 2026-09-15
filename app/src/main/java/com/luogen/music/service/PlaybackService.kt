package com.luogen.music.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.ServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.luogen.music.LuogenApp
import com.luogen.music.R
import com.luogen.music.data.api.ApiHttp
import com.luogen.music.domain.model.PlayMode
import com.luogen.music.domain.model.Song
import com.luogen.music.player.PlayerHub
import com.luogen.music.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

/**
 * 发现音乐播放服务（Media3 MediaSessionService）：
 * 负责在线/本地统一播放、播放队列、系统媒体通知、蓝牙 AVRCP、耳机线控、
 * 后台播放控制与播放失败自动换源（点哪首播哪首，绝不偷换歌曲）。
 *
 * 播放策略：队列由 PlayerHub 持有，播放器每次只装载当前一首（单曲模式），
 * 上一曲/下一曲/循环/随机全部由本服务驱动。在线歌曲必须先异步解析出
 * 真实可播放 URL 再交给播放器，避免无 scheme 的占位 URI 触发
 * ExoPlayer 同步异常导致全局闪退。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true }

    /** 已解析的真实播放地址缓存（mediaId -> url），避免连播时重复解析 */
    private val urlCache = HashMap<String, String>()

    /** 当前曲错误重试标记：同曲只允许重试一次，防止失败-重试死循环 */
    private var errorRetryMediaId: String? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // 注意：单曲模式下 setMediaItem 也会触发本回调。
            // 不能在这里清 errorRetryMediaId——否则 onPlayerError 刚标记的同曲重试
            // 立刻被 setMediaItem 触发的 transition 清掉，导致"重试判定永远失效"，
            // 变成同一首歌无限重试或无限跳歌。重试标记只在真正播起来/主动切歌时清。
            updateNowPlaying()
            notifyStateChanged()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            PlayerHub.update { it.copy(isPlaying = isPlaying, isPreparing = false) }
            if (isPlaying) errorRetryMediaId = null
            updateNowPlaying()
            notifyStateChanged()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> PlayerHub.update { it.copy(isPreparing = true) }
                Player.STATE_READY -> {
                    PlayerHub.update { it.copy(isPreparing = false) }
                    // 立即刷新时长/进度，避免界面上一直显示 00:00
                    PlayerHub.durationMs.value = player?.duration?.takeIf { it >= 0 } ?: 0L
                    PlayerHub.positionMs.value = player?.currentPosition ?: 0L
                    // 真正播起来了：清重试标记
                    errorRetryMediaId = null
                }
                Player.STATE_ENDED -> {
                    PlayerHub.update { it.copy(isPreparing = false, isPlaying = false) }
                    PlayerHub.positionMs.value = 0L
                    if (PlayerHub.state.value.mode == PlayMode.REPEAT_ONE) {
                        // 单曲循环：从头重播当前曲
                        player?.apply { seekTo(0); prepare(); play() }
                        PlayerHub.update { it.copy(isPlaying = true) }
                    } else {
                        gotoNext(auto = true)
                    }
                    notifyStateChanged()
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            // 播放失败策略（点哪首播哪首，绝不偷换歌曲）：
            // 1) 首败：重新解析当前歌曲地址，装载重试一次；
            // 2) 同曲再败或解析不到地址：停止并明确提示原因，不再自动跳下一首。
            // 用户手动切歌 / 播完自然连播由 playCurrent / gotoNext 正常驱动。
            PlayerHub.update { it.copy(isPreparing = false, error = "播放出错，正在尝试…") }
            PlayerHub.currentUrl.value = null
            scope.launch {
                val song = PlayerHub.state.value.current ?: return@launch
                val mediaId = mediaIdOf(song)
                // 同一首已重试过一次仍失败：停住并明示，不跳过
                if (errorRetryMediaId == mediaId) {
                    errorRetryMediaId = null
                    failCurrent(song, "「${song.name}」播放失败（网络或音源受限），已停止，可切下一曲")
                    return@launch
                }
                errorRetryMediaId = mediaId
                val retried = if (song.localUri != null) song.localUri else resolvePlayableUrl(song)
                if (PlayerHub.state.value.current?.id != song.id) return@launch
                if (retried != null) {
                    urlCache[mediaIdOf(song)] = retried
                    PlayerHub.currentUrl.value = retried
                    player?.apply {
                        setMediaItem(toMediaItem(song, retried))
                        prepare(); play()
                    }
                    PlayerHub.update { it.copy(error = null, isPreparing = false) }
                    updateNowPlaying()
                } else {
                    failCurrent(song, "「${song.name}」暂无可用音源（VIP/版权受限或音乐服务器不可用）")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // 网易云 CDN 流媒体播放必须带 Referer/UA，否则 403 -> onPlayerError -> 无限跳歌
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://music.163.com/",
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36",
                )
            )
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)
        val mixed = runCatching { kotlinx.coroutines.runBlocking { LuogenApp.instance.settings.audioFocusMixed.first() } }.getOrDefault(false)
        val aa = if (mixed) {
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .setFlags(androidx.media3.common.C.FLAG_AUDIBILITY_ENFORCED)
                .build()
        } else androidx.media3.common.AudioAttributes.DEFAULT
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(aa, true)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(listener)
            }
        player = p

        val sessionIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, p)
            .setSessionActivity(sessionIntent)
            .build()

        tickJob = scope.launch {
            while (isActive) {
                PlayerHub.positionMs.value = p.currentPosition
                PlayerHub.durationMs.value = p.duration.takeIf { it >= 0 } ?: 0L
                delay(if (p.isPlaying) 300L else 800L)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 后台播放开关：关闭时清理即停；开启时保持前台播放（系统回收由 START_STICKY 兜底）
        LuogenApp.instance.appScope.launch(Dispatchers.IO) {
            val enabled = LuogenApp.instance.settings.backgroundPlay.first()
            if (!enabled) {
                stopSelf()
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != null) handleCommand(intent)
        return START_STICKY
    }

    // ---------------- 命令处理 ----------------
    private fun handleCommand(intent: Intent) {
        when (intent.action) {
            CMD_PLAY_QUEUE -> {
                val songsJson = intent.getStringExtra(EXTRA_SONGS) ?: return
                val index = intent.getIntExtra(EXTRA_INDEX, 0)
                val songs = runCatching {
                    json.decodeFromString(ListSerializer(Song.serializer()), songsJson)
                }.getOrDefault(emptyList())
                if (songs.isEmpty()) return
                PlayerHub.replaceQueue(songs, index.coerceIn(0, songs.size - 1))
                urlCache.clear()
                startForegroundSafe()
                playCurrent(play = true)
            }
            CMD_PLAY_INDEX -> {
                val idx = intent.getIntExtra(EXTRA_INDEX, 0)
                if (idx in PlayerHub.state.value.songs.indices) {
                    PlayerHub.update { it.copy(index = idx, error = null) }
                    startForegroundSafe()
                    playCurrent(play = true)
                }
            }
            CMD_REMOVE_AT -> {
                val pos = intent.getIntExtra(EXTRA_INDEX, -1)
                val wasCurrent = pos == PlayerHub.state.value.index
                val wasPlaying = PlayerHub.state.value.isPlaying
                if (pos >= 0) PlayerHub.removeAt(pos)
                if (wasCurrent && PlayerHub.state.value.songs.isNotEmpty()) {
                    // 删的是当前曲：自动跳到新的当前位置继续
                    playCurrent(play = wasPlaying)
                } else if (PlayerHub.state.value.songs.isEmpty()) {
                    player?.stop(); player?.clearMediaItems()
                    PlayerHub.update { it.copy(isPlaying = false, index = -1) }
                }
            }
            CMD_INSERT_NEXT -> {
                val songJson = intent.getStringExtra(EXTRA_SONG) ?: return
                val song = runCatching { json.decodeFromString(Song.serializer(), songJson) }.getOrNull()
                if (song != null) PlayerHub.insertNext(song)
            }
            CMD_PLAY_PAUSE -> player?.let { p ->
                if (p.mediaItemCount == 0) {
                    // 从未加载过歌曲：直接按当前索引开始播
                    startForegroundSafe()
                    playCurrent(play = true)
                } else if (p.isPlaying) {
                    p.pause()
                } else {
                    startForegroundSafe()
                    p.play()
                }
            }
            CMD_NEXT -> gotoNext(auto = false)
            CMD_PREV -> gotoPrev()
            CMD_SEEK_TO -> player?.seekTo(intent.getLongExtra(EXTRA_POS, 0))
            CMD_SET_MODE -> {
                // 没有显式模式参数（通知栏/小组件按钮）时循环切换：顺序→列表→单曲→随机→顺序
                val mode = intent.getStringExtra(EXTRA_MODE)
                val current = PlayerHub.state.value.mode
                val m = when {
                    mode != null -> runCatching { PlayMode.valueOf(mode) }.getOrDefault(PlayMode.REPEAT_ALL)
                    else -> when (current) {
                        PlayMode.SEQUENCE -> PlayMode.REPEAT_ALL
                        PlayMode.REPEAT_ALL -> PlayMode.REPEAT_ONE
                        PlayMode.REPEAT_ONE -> PlayMode.SHUFFLE
                        PlayMode.SHUFFLE -> PlayMode.SEQUENCE
                    }
                }
                PlayerHub.update { it.copy(mode = m) }
                player?.repeatMode = Player.REPEAT_MODE_OFF
                // 通知栏 + 小组件同步刷新模式图标
                startForegroundSafe()
                notifyStateChanged()
            }
        }
    }

    // ---------------- 播放调度（单曲模式，自行驱动队列） ----------------

    /** 加载并播放 PlayerHub 当前索引对应的歌曲 */
    private fun playCurrent(play: Boolean) {
        val st = PlayerHub.state.value
        val song = st.current ?: return
        val p = player ?: return

        // 本地歌曲：直接播放
        if (song.localUri != null) {
            p.setMediaItem(toMediaItem(song, song.localUri))
            p.prepare()
            if (play) p.play()
            PlayerHub.update { it.copy(isPreparing = false, error = null) }
            updateNowPlaying()
            notifyStateChanged()
            recordHistory(song)
            return
        }

        // 在线歌曲：只使用与当前歌曲 mediaId 绑定的已解析地址。
        // 特别注意：不能复用 PlayerHub.currentUrl（它可能是上一首歌/已过期音源的残留地址），
        // 否则会“播错歌或拿过期 URL 播放失败 -> 自动跳下一首 -> 一直跳”。
        val cached = urlCache[mediaIdOf(song)]
        if (cached != null && (cached.startsWith("http") || cached.startsWith("https"))) {
            p.setMediaItem(toMediaItem(song, cached))
            p.prepare()
            if (play) p.play()
            PlayerHub.update { it.copy(isPreparing = false, error = null) }
            updateNowPlaying()
            notifyStateChanged()
            recordHistory(song)
            return
        }

        // 尚无地址：异步解析成功后再装载播放（解析期间显示加载态，不闪退）
        PlayerHub.update { it.copy(isPreparing = true, error = null) }
        scope.launch {
            val u = resolvePlayableUrl(song)
            // 解析期间用户可能已切歌
            if (PlayerHub.state.value.current?.id != song.id) return@launch
            if (u != null) {
                urlCache[mediaIdOf(song)] = u
                PlayerHub.currentUrl.value = u
                p.setMediaItem(toMediaItem(song, u))
                p.prepare()
                if (play) p.play()
                PlayerHub.update { it.copy(isPreparing = false) }
                updateNowPlaying()
                notifyStateChanged()
                recordHistory(song)
            } else {
                // 解析不出地址：停在当前曲并明示原因，绝不偷换歌曲/无限跳下一首
                PlayerHub.update { it.copy(isPreparing = false) }
                failCurrent(song, "「${song.name}」暂无可用音源（VIP/版权受限或音乐服务器不可用），已停止，可切下一曲")
            }
        }
    }

    /** 下一曲：auto=true 表示自然播完驱动，SEQUENCE 播完最后一首时停止 */
    private fun gotoNext(auto: Boolean) {
        val st = PlayerHub.state.value
        val songs = st.songs
        if (songs.isEmpty()) return
        val mode = st.mode
        val ni: Int = when (mode) {
            PlayMode.REPEAT_ONE -> st.index
            PlayMode.SHUFFLE -> if (songs.size > 1) randomIndex(songs.size, st.index) else st.index
            PlayMode.SEQUENCE -> {
                if (auto && st.index >= songs.size - 1) {
                    // 顺序播放结束：停止
                    player?.apply { pause(); seekTo(0) }
                    PlayerHub.update { it.copy(isPlaying = false, isPreparing = false) }
                    return
                }
                (st.index + 1) % songs.size
            }
            PlayMode.REPEAT_ALL -> (st.index + 1) % songs.size
        }
        PlayerHub.update { it.copy(index = ni, error = null) }
        playCurrent(play = true)
    }

    /** 上一曲：播放超过 3 秒回到开头，否则切上一曲 */
    private fun gotoPrev() {
        val p = player ?: return
        val st = PlayerHub.state.value
        val songs = st.songs
        if (songs.isEmpty()) return
        if (p.currentPosition > 3000) {
            p.seekTo(0)
            return
        }
        val ni = when (st.mode) {
            PlayMode.SHUFFLE -> if (songs.size > 1) randomIndex(songs.size, st.index) else st.index
            else -> ((st.index - 1) + songs.size) % songs.size
        }
        PlayerHub.update { it.copy(index = ni, error = null) }
        playCurrent(play = true)
    }

    /**
     * 当前曲无法装载/播放：停在当前歌曲（保留歌曲与索引显示），
     * 播放器复位，明示原因，等待用户手动切歌或重试（点哪首播哪首，绝不自己跳）。
     */
    private fun failCurrent(song: Song, reason: String) {
        player?.stop()
        player?.clearMediaItems()
        PlayerHub.update {
            it.copy(
                isPlaying = false,
                isPreparing = false,
                error = reason,
            )
        }
        PlayerHub.positionMs.value = 0L
        PlayerHub.durationMs.value = 0L
        PlayerHub.currentUrl.value = null
        urlCache.remove(mediaIdOf(song))
        notifyStateChanged()
    }

    private fun randomIndex(size: Int, current: Int): Int {
        var r = Random.nextInt(size)
        if (r == current) r = (r + 1) % size
        return r
    }

    /** 解析真实可播放地址：
     *  1) 优先使用用户在设置页配置的服务器列表（保持其优先级）；
     *  2) 全部失败时兜底内置默认服务器（实测稳定可用），保证"点哪首播哪首"；
     *  3) 并发尝试所有服务器 + 单服务器 4s 超时，失效服务器不拖慢切歌。 */
    private suspend fun resolvePlayableUrl(song: Song): String? {
        val plain = song.localUri
        if (plain != null) return plain
        return try {
            val repo = LuogenApp.instance.repository
            // 用户配置 + 兜底默认服务器（去重、保序），实测默认服务器解析成功率接近 100%
            val configured = LuogenApp.instance.settings.getApiServersSync()
            val servers = (configured + ApiHttp.DEFAULT_SERVER).distinct()
            coroutineScope {
                // 并发发起所有服务器的解析请求（各自带 4s 超时），
                // 逐个取结果：单个服务器超时/失败不影响其他服务器命中（快速失败优先成功）
                val jobs = servers.map { srv ->
                    val d: kotlinx.coroutines.Deferred<String?> = async(Dispatchers.IO) {
                        withTimeout(4000L) {
                            runCatching { repo.songUrlForServer(srv, song.id) }.getOrNull()
                        }
                    }
                    d
                }
                var found: String? = null
                for (job in jobs) {
                    val u = try { job.await() } catch (e: Exception) { null }
                    if (!u.isNullOrBlank()) { found = u; break }
                }
                found
            }
        } catch (e: Exception) { null }
    }

    /**
     * 播放地址安全清洗：网易云 CDN 直链常含未编码空格（如 `vuutv=0+xxxx`），
     * OkHttp/ExoPlayer 遇到非法字符会解析失败 -> onPlayerError -> 无限跳下一首。
     * 这里作为最后一道保险（仓库层已清洗，缓存/旧队列残留值在此兜底）。
     */
    private fun safeUrl(url: String): String = url.replace(" ", "%20")

    private fun toMediaItem(song: Song, url: String): MediaItem {
        val artwork = song.coverUrl.takeIf { it.startsWith("http") }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.name)
            .setArtist(song.artistNames.joinToString(" / "))
            .setAlbumTitle(song.albumName)
            .setArtworkUri(if (artwork != null) android.net.Uri.parse(artwork) else null)
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaIdOf(song))
            .setUri(safeUrl(url))
            .setMediaMetadata(metadata)
            .build()
    }

    private fun mediaIdOf(song: Song) = "${song.source}:${song.id}"

    private fun updateNowPlaying() {
        PlayerHub.update { it.copy(isPlaying = player?.isPlaying == true) }
        startForegroundSafe()
    }

    private fun startForegroundSafe() {
        runCatching {
            ServiceCompat.startForeground(this, 1, buildNotice(), 0)
        }
    }

    private fun notifyStateChanged() {
        // 刷新桌面小组件 + 一起听房间同步
        com.luogen.music.widget.MusicWidgetProvider.refresh(this)
        if (PlayerHub.togetherMode.value) {
            runCatching {
                val i = Intent(this, ListenTogetherService::class.java)
                i.setAction(ListenTogetherService.ACTION_FLAG)
                startService(i)
            }
        }
    }

    /** 通知栏控制按钮：统一复用服务命令（内部命令不经过 Binder） */
    private fun noticeAction(action: String, reqCode: Int, iconResId: Int, title: String): androidx.core.app.NotificationCompat.Action {
        val pi = PendingIntent.getService(
            this, reqCode,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // IconCompat.createWithResource 直接让通知系统渲染矢量图标：
        // 避免了手动 bitmap 化在某些 ROM 上产生的空白/透明问题。
        val icon = androidx.core.graphics.drawable.IconCompat.createWithResource(this, iconResId)
        return androidx.core.app.NotificationCompat.Action.Builder(icon, title, pi).build()
    }

    /**
     * 把通知按钮图标渲染成位图：部分系统（尤其部分国产 ROM）对通知栏 vector drawable
     * 的 Action 图标渲染为空白，位图化后明确自适应系统深浅色主题。
     * 图标统一用白色，系统在浅色主题下自动反色处理。
     */
    private fun renderNoticeIcon(resId: Int): android.graphics.Bitmap {
        val size = (24 * resources.displayMetrics.density).toInt().coerceAtLeast(48)
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val d = androidx.core.content.ContextCompat.getDrawable(this, resId)
        if (d != null) {
            // 白色单色图标：BitAMP 白色 —— 系统通知栏自带 tint，实际显示色由系统决定
            d.setTint(android.graphics.Color.WHITE)
            d.setBounds(0, 0, size, size)
            d.draw(canvas)
        }
        return bmp
    }

    /**
     * 构建前台媒体通知：歌曲信息 + 自定义封面大图标 + 上一曲/播放暂停/下一曲 三个控制按钮。
     * 与桌面小组件共享 PlayerHub 状态与命令通道，状态天然同步。
     */
    private fun buildNotice(): android.app.Notification {
        val st = PlayerHub.state.value
        val song = st.current
        val b = LuogenApp.instance.notificationCompatBuilder()
        b.setContentTitle(song?.name ?: "发现音乐")
        b.setContentText(song?.artistNames?.joinToString(" / ")?.ifBlank { "正在播放…" } ?: "正在播放…")
        // 点击通知打开播放页（直接跳转，而非仅恢复当前页面）
        val deepLinkIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("luogen://open/player"))
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        b.setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                deepLinkIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        // 封面大图标：自定义封面 > 在线封面缓存（采样解码，杜绝 OOM）
        loadNoticeArtwork(song)?.let { b.setLargeIcon(it) }
        // 通知栏控制按钮一律使用系统内置媒体图标（android.R.drawable.ic_media_*）：
        // 这些图标由 ROM 自带且被通知系统完整支持，绝不会出现“下一曲按钮空白”的问题。
        val playing = player?.isPlaying == true
        val actions = listOf(
            noticeAction(CMD_PREV, 1, android.R.drawable.ic_media_previous, "上一曲"),
            noticeAction(
                CMD_PLAY_PAUSE, 2,
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "暂停" else "播放",
            ),
            noticeAction(CMD_NEXT, 3, android.R.drawable.ic_media_next, "下一曲"),
        )
        actions.forEach { b.addAction(it) }
        b.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession?.sessionCompatToken)
                .setShowActionsInCompactView(0, 1, 2)
        )
        b.setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
        b.setOngoing(true)
        b.setShowWhen(false)
        return b.build()
    }

    /** 通知栏封面：自定义封面优先，其次在线封面（有 http 封面则异步下载后刷新通知） */
    private fun loadNoticeArtwork(song: Song?): android.graphics.Bitmap? {
        if (song == null) return null
        // 1. 自定义封面（本地上传）
        com.luogen.music.data.local.CoverStore.get(song.id)?.let { p ->
            decodeSampledBitmap(p)?.let { return it }
        }
        // 2. 已缓存的在线封面（与小组件同一缓存文件）
        val cacheFile = File(cacheDir, "widget_cover_${song.id}.jpg")
        if (cacheFile.exists()) {
            decodeSampledBitmap(cacheFile.absolutePath)?.let { return it }
        }
        // 3. 在线封面：后台下载（与小组件相同的缩略 URL + Referer），成功后刷新通知
        if (song.coverUrl.startsWith("http")) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val thumbUrl = if (!song.coverUrl.contains("param=")) {
                        song.coverUrl + (if (song.coverUrl.contains("?")) "&" else "?") + "param=480y480"
                    } else song.coverUrl
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    client.newCall(
                        okhttp3.Request.Builder().url(thumbUrl)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                            .header("Referer", "https://music.163.com/")
                            .get().build()
                    ).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val data = resp.body?.bytes() ?: return@use
                            cacheFile.outputStream().use { it.write(data) }
                            // 下载完成且仍是这首歌时刷新通知
                            if (PlayerHub.state.value.current?.id == song.id) startForegroundSafe()
                        }
                    }
                }
            }
        }
        return null
    }

    /** 采样解码（≤480px）：直接 decodeFile 大图会 OOM 杀进程，这里先探尺寸再采样 */
    private fun decodeSampledBitmap(path: String): android.graphics.Bitmap? = runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 480) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        android.graphics.BitmapFactory.decodeFile(path, opts)
    }.getOrNull()

    private fun recordHistory(song: Song?) {
        if (song == null || song.id == 0L) return
        LuogenApp.instance.appScope.launch(Dispatchers.IO) {
            val dao = LuogenApp.instance.database.dao()
            val jsonText = Json { ignoreUnknownKeys = true }.encodeToString(Song.serializer(), song)
            val old = dao.history().firstOrNull { it.songId == song.id }
            dao.upsertHistory(
                com.luogen.music.data.db.PlayHistoryEntity(
                    songId = song.id, songJson = jsonText,
                    playCount = (old?.playCount ?: 0) + 1,
                    lastPlayedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    override fun onDestroy() {
        tickJob?.cancel()
        mediaSession?.release()
        player?.release()
        player = null
        mediaSession = null
        super.onDestroy()
    }

    // ---------------- 对外命令入口 ----------------
    companion object {
        const val CMD_PLAY_QUEUE = "cmd_play_queue"
        const val CMD_PLAY_INDEX = "cmd_play_index"
        const val CMD_REMOVE_AT = "cmd_remove_at"
        const val CMD_INSERT_NEXT = "cmd_insert_next"
        const val CMD_PLAY_PAUSE = "cmd_play_pause"
        const val CMD_NEXT = "cmd_next"
        const val CMD_PREV = "cmd_prev"
        const val CMD_SEEK_TO = "cmd_seek"
        const val CMD_SET_MODE = "cmd_mode"
        const val EXTRA_SONGS = "extra_songs"
        const val EXTRA_SONG = "extra_song"
        const val EXTRA_INDEX = "extra_index"
        const val EXTRA_POS = "extra_pos"
        const val EXTRA_MODE = "extra_mode"

        private val json = Json { ignoreUnknownKeys = true }

        private fun start(context: Context, command: String, fill: Intent.() -> Unit = {}) {
            val i = Intent(context, PlaybackService::class.java).setAction(command)
            i.fill()
            context.startForegroundService(i)
        }

        fun playQueue(context: Context, songs: List<Song>, index: Int = 0) {
            // 队列可能很大（搜索页分页累积数百首），JSON 经 Intent 序列化超过 Binder 1MB
            // 会抛 TransactionTooLargeException 直接杀掉进程（回桌面）。改为同进程内
            // 先写 PlayerHub 队列，再发只携带 index 的轻量命令触发播放。
            PlayerHub.replaceQueue(songs, index)
            start(context, CMD_PLAY_INDEX) { putExtra(EXTRA_INDEX, index) }
        }

        fun playIndex(context: Context, index: Int) = start(context, CMD_PLAY_INDEX) { putExtra(EXTRA_INDEX, index) }
        fun playPause(context: Context) = start(context, CMD_PLAY_PAUSE)
        fun next(context: Context) = start(context, CMD_NEXT)
        fun prev(context: Context) = start(context, CMD_PREV)
        fun seekTo(context: Context, posMs: Long) = start(context, CMD_SEEK_TO) { putExtra(EXTRA_POS, posMs) }
        fun removeAt(context: Context, index: Int) = start(context, CMD_REMOVE_AT) { putExtra(EXTRA_INDEX, index) }
        fun insertNext(context: Context, song: Song) = start(context, CMD_INSERT_NEXT) {
            putExtra(EXTRA_SONG, json.encodeToString(Song.serializer(), song))
        }
        fun setMode(context: Context, mode: PlayMode) = start(context, CMD_SET_MODE) { putExtra(EXTRA_MODE, mode.name) }

        /** 供 MediaButtonReceiver 分发的媒体按钮回调（耳机线控/蓝牙） */
        fun onMediaButton(context: Context, action: String) {
            when (action) {
                "android.intent.action.MEDIA_BUTTON" -> Unit // 由 Media3 默认处理
                else -> start(context, action)
            }
        }

        /** 小组件请求刷新播放状态 */
        fun onWidgetChanged(context: Context) {
            com.luogen.music.widget.MusicWidgetProvider.refresh(context)
        }

        /** 摇一摇/线控等外部控制入口（非 Activity 上下文可用） */
        fun externalCommand(context: Context, action: String) {
            when (action) {
                "toggle" -> playPause(context)
                "next" -> next(context)
                "prev" -> prev(context)
                "like" -> Unit
            }
            if (PlayerHub.togetherMode.value) {
                runCatching {
                    val i = Intent(context, ListenTogetherService::class.java)
                    i.setAction(ListenTogetherService.ACTION_FLAG)
                    context.startService(i)
                }
            }
        }
    }
}