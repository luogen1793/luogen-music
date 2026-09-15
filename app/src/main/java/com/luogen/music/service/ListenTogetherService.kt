package com.luogen.music.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.luogen.music.LuogenApp
import com.luogen.music.domain.model.Song
import com.luogen.music.domain.model.TogetherMessage
import com.luogen.music.player.PlayerHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * 一起听服务：真实 WebSocket 房间客户端。
 * 协议（与 delivery/server 服务端一致）：
 *  - {type:"join", room, nick}
 *  - {type:"peer_join"/"peer_left"} 系统消息
 *  - {type:"chat", room, nick, text}
 *  - {type:"cmd", room, seq, action: "play"|"pause"|"next"|"prev"|"seek"|"queue", song?, time?, index?}
 * 双方播放状态完全同步（上一曲/下一曲/播放/暂停同步）+ 实时聊天。
 */
class ListenTogetherService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ws: WebSocket? = null
    private var pingJob: Job? = null
    private var seq = 0

    private val json = Json { ignoreUnknownKeys = true }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 前台服务要求 10s 内 startForeground
        runCatching {
            ServiceCompat.startForeground(this, 1004, LuogenApp.instance.notificationCompatBuilder()
                .setContentTitle("一起听")
                .setContentText("一起听房间已连接")
                .build(), 0)
        }
        when (intent?.action) {
            ACTION_JOIN -> intent.getStringExtra("room")?.let { joinRoom(it) }
            ACTION_LEAVE -> leaveRoom()
            ACTION_CHAT -> intent.getStringExtra("text")?.let { sendChat(it) }
            ACTION_FLAG -> {
                val current = PlayerHub.state.value.current
                val st = PlayerHub.state.value
                if (current != null) {
                    runCatching {
                        ws?.send(
                            Envelope(
                                type = "cmd", room = _room.value ?: "", action = "queue",
                                songs = st.songs, index = st.index,
                            ).toJson()
                        )
                    }
                }
            }
        }
        return START_STICKY
    }

    /** 创建/加入房间并开始同步 */
    fun joinRoom(roomId: String) {
        scope.launch {
            val server = LuogenApp.instance.settings.togetherServer.first()
                .ifBlank { DEFAULT_SERVER }
            val nick = LuogenApp.instance.settings.togetherNick.first()
            _messages.value = emptyList()
            _error.value = null
            ws?.close(1000, "switch")
            val client = OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url("$server/ws?room=$roomId&nick=${java.net.URLEncoder.encode(nick, "UTF-8")}").build()
            // 连接超时看门狗：15s 内未能连上即给出明确提示
            val watchdog = scope.launch {
                delay(15000)
                _error.value = "连接服务器超时（${server}），请检查网络，或在 设置-更多-一起听服务器 中更换可用地址"
            }
            ws = client.newWebSocket(req, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _room.value = roomId
                    _connected.value = true
                    _error.value = null
                    watchdog.cancel()
                    post(Envelope("join", roomId, nick = nick))
                    // 加入后广播当前播放（让新成员对齐）
                    syncNow()
                    pingJob?.cancel()
                    pingJob = scope.launch {
                        while (isActive) {
                            delay(20000)
                            runCatching { ws?.send(Envelope("ping", roomId).toJson()) }
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val ev = runCatching { json.decodeFromString<Envelope>(text) }.getOrNull() ?: return
                    when (ev.type) {
                        "chat" -> append(TogetherMessage(nick = ev.nick ?: "", text = ev.text ?: ""))
                        "system" -> append(TogetherMessage(nick = "系统", text = ev.text ?: "", system = true))
                        "cmd" -> applyRemote(ev)
                        "snapshot" -> applySnapshot(ev)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connected.value = false
                    _room.value = null
                    watchdog.cancel()
                    pingJob?.cancel()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _connected.value = false
                    watchdog.cancel()
                    val reason = t.message ?: "网络不可达"
                    _error.value = "无法连接服务器（$server）：$reason。请检查网络，或在 设置-更多-一起听服务器 中更换可用地址"
                }
            })
        }
    }

    /** 远程命令：对方点播/暂停等 → 本地同步执行（不重复发给远端） */
    private fun applyRemote(ev: Envelope) {
        when (ev.action) {
            "queue" -> {
                val songs = ev.songs ?: return
                val idx = (ev.index ?: 0).coerceIn(0, songs.size - 1)
                PlayerHub.togetherMode.value = true
                PlaybackService.playQueue(this, songs, idx)
            }
            "play" -> PlaybackService.playPause(this)
            "pause" -> PlaybackService.playPause(this)
            "next" -> PlaybackService.next(this)
            "prev" -> PlaybackService.prev(this)
            "seek" -> ev.time?.let { PlaybackService.seekTo(this, it) }
        }
    }

    private fun applySnapshot(ev: Envelope) {
        val songs = ev.songs ?: return
        val idx = (ev.index ?: 0).coerceIn(0, songs.size - 1)
        PlayerHub.togetherMode.value = true
        PlaybackService.playQueue(this, songs, idx)
    }

    private fun syncNow() {
        val st = PlayerHub.state.value
        val songs = st.songs
        if (songs.isEmpty()) return
        ws?.send(
            Envelope(
                type = "cmd", room = _room.value ?: return, action = "queue",
                songs = songs, index = st.index,
            ).toJson()
        )
    }

    /** 本地播放状态变化时调用：广播到房间 */
    fun broadcastCommand(action: String, song: Song? = null, time: Long? = null) {
        val roomId = _room.value ?: return
        if (!_connected.value) return
        val payload = when (action) {
            "queue" -> Envelope("cmd", roomId, action = "queue", songs = PlayerHub.state.value.songs, index = PlayerHub.state.value.index)
            "seek" -> Envelope("cmd", roomId, action = "seek", time = PlayerHub.positionMs.value)
            else -> Envelope("cmd", roomId, action = action)
        }
        runCatching { ws?.send(payload.toJson()) }
    }

    fun sendChat(text: String) {
        val roomId = _room.value ?: return
        val nick = LuogenApp.instance.settings.togetherNick
        val job = scope.launch {
            val n = nick.first()
            append(TogetherMessage(nick = n, text = text))
            runCatching { ws?.send(Envelope("chat", roomId, nick = n, text = text).toJson()) }
        }
        job.start()
    }

    fun leaveRoom() {
        runCatching { ws?.close(1000, "leave") }
        _room.value = null
        _connected.value = false
        PlayerHub.togetherMode.value = false
        stopSelf()
    }

    private fun append(msg: TogetherMessage) {
        _messages.value = (_messages.value + msg).takeLast(200)
    }

    private fun post(env: Envelope) {
        runCatching { ws?.send(env.toJson()) }
    }

    override fun onDestroy() {
        pingJob?.cancel()
        runCatching { ws?.close(1000, "destroy") }
        PlayerHub.togetherMode.value = false
        super.onDestroy()
    }

    @Serializable
    data class Envelope(
        val type: String,
        val room: String = "",
        val nick: String? = null,
        val text: String? = null,
        val action: String? = null,
        val songs: List<Song>? = null,
        val index: Int? = null,
        val time: Long? = null,
        val seq: Int = 0,
    ) {
        fun toJson() = Json { ignoreUnknownKeys = true }.encodeToString(Envelope.serializer(), this)
    }

    companion object {
        private val _room = MutableStateFlow<String?>(null)
        val room: StateFlow<String?> = _room.asStateFlow()

        private val _messages = MutableStateFlow<List<TogetherMessage>>(emptyList())
        val messages: StateFlow<List<TogetherMessage>> = _messages.asStateFlow()

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        const val DEFAULT_SERVER = "wss://lg-together-xx.onrender.com"
        const val ACTION_FLAG = "com.luogen.music.together.SYNC"
        const val ACTION_JOIN = "com.luogen.music.together.JOIN"
        const val ACTION_LEAVE = "com.luogen.music.together.LEAVE"
        const val ACTION_CHAT = "com.luogen.music.together.CHAT"
        const val ACTION_PLAY = "play"
        const val ACTION_PAUSE = "pause"
        const val ACTION_NEXT = "next"
        const val ACTION_PREV = "prev"
        const val ACTION_QUEUE = "queue"

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, ListenTogetherService::class.java)) }
        }

        /** UI 通过广播式命令与前台服务通信 */
        fun joinCompat(context: Context, room: String) {
            runCatching {
                context.startForegroundService(Intent(context, ListenTogetherService::class.java)
                    .setAction(ACTION_JOIN).putExtra("room", room))
            }
        }

        fun leaveRoomCompat(context: Context) {
            runCatching {
                context.startService(Intent(context, ListenTogetherService::class.java).setAction(ACTION_LEAVE))
            }
        }

        fun sendChatCompat(context: Context, text: String) {
            runCatching {
                context.startService(Intent(context, ListenTogetherService::class.java)
                    .setAction(ACTION_CHAT).putExtra("text", text))
            }
        }
    }
}