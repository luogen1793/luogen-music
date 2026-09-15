package com.luogen.music

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.luogen.music.data.api.ApiHttp
import com.luogen.music.data.api.MusicApiClient
import com.luogen.music.data.api.NetEaseRepository
import com.luogen.music.data.db.LuogenDatabase
import com.luogen.music.data.hot.HotApi
import com.luogen.music.data.prefs.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 发现音乐 —— Application 入口与全局服务容器。
 * 作者：罗根
 */
class LuogenApp : Application() {

    lateinit var settings: SettingsRepo
        private set
    lateinit var repository: NetEaseRepository
        private set
    lateinit var database: LuogenDatabase
        private set
    val searchHistory: com.luogen.music.data.prefs.SearchHistoryStore by lazy { com.luogen.music.data.prefs.SearchHistoryStore(this) }
    val hotApi = HotApi()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepo(this)
        // 初始化服务器列表（从设置读取，默认公共网易云镜像）
        val http = ApiHttp(listOf(ApiHttp.DEFAULT_SERVER))
        appScope.launch {
            settings.apiServers.collect { servers ->
                http.updateServers(servers.toList())
            }
        }
        // 启动预热：App 启动即对高频端点建连，首页/搜索首屏数据近乎秒出
        appScope.launch(Dispatchers.IO) { http.prewarm() }
        repository = NetEaseRepository(MusicApiClient("__BASE__", http))
        database = LuogenDatabase.get(this)
        // 加载本地自定义封面（上传后重启仍生效）
        com.luogen.music.data.local.CoverStore.load()
        // 加载我喜欢的歌曲（播放页爱心 ⇄ 我的页列表实时同步）
        com.luogen.music.data.local.FavoriteStore.load()
        // 按设置恢复摇一摇服务：开关默认开启，启动即拉起，避免重启后失效
        ensureGestureServices()
        ensureNotificationChannels()
        // 全局 Coil 图片加载器：磁盘+内存缓存、淡入，解决封面加载慢/不显示/重复下载
        runCatching {
            val loader = coil.ImageLoader.Builder(this)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .memoryCache {
                    coil.memory.MemoryCache.Builder(this)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    coil.disk.DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(300L * 1024 * 1024)
                        .build()
                }
                .respectCacheHeaders(false)
                .crossfade(true)
                .build()
            coil.Coil.setImageLoader(loader)
        }
    }

    /** 按用户设置恢复摇一摇服务（开关默认开启；启动即拉起，避免重启后失效）。 */
    private fun ensureGestureServices() {
        appScope.launch {
            val shakeOn = settings.shakeEnabled.first()
            if (shakeOn) {
                kotlinx.coroutines.delay(1200) // 等 Activity 起来并拥有前台状态再拉起前台服务
                runCatching { com.luogen.music.service.ShakeGestureService.start(this@LuogenApp) }
            }
        }
    }

    private fun ensureNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PLAYBACK, "播放控制", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "后台播放与桌面小组件的播放状态" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICES, "识别与联动服务", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "摇一摇 / 一起听等后台服务" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOAD, "下载", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "歌曲下载进度通知" }
        )
    }

    fun notificationCompatBuilder(): androidx.core.app.NotificationCompat.Builder =
        androidx.core.app.NotificationCompat.Builder(this, CHANNEL_PLAYBACK)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("发现音乐")
            .setContentText("正在播放…")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

    companion object {
        const val CHANNEL_PLAYBACK = "playback"
        const val CHANNEL_SERVICES = "services"
        const val CHANNEL_DOWNLOAD = "download"

        @Volatile
        lateinit var instance: LuogenApp
            private set
    }
}