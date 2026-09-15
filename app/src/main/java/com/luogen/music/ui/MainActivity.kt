package com.luogen.music.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.luogen.music.ui.root.RootScreen
import com.luogen.music.ui.splash.SplashScreen
import com.luogen.music.ui.theme.LuogenTheme

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * 全局导航事件通道：跨组件/服务传递页面跳转请求（如通知栏点击→播放页）
 */
object AppNavigator {
    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    fun navigateTo(route: String) = _events.trySend(route)
}

/**
 * 发现音乐主入口 Activity：启动页 -> 应用主框架（底部导航 + 全局路由）
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            LuogenTheme {
                var showRoot by remember { mutableStateOf(false) }
                val intent = remember { intent }
                // Android 13+ 通知栏需要运行时权限：首次进入静默请求
                val appContext = applicationContext
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        runCatching {
                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001)
                        }
                    }
                }
                if (!showRoot) {
                    SplashScreen(onFinished = { showRoot = true })
                } else {
                    RootScreen(intent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 通知栏点击 deep link → 播放页
        intent.data?.let { uri ->
            if (uri.scheme == "luogen" && uri.host == "open" && uri.lastPathSegment == "player") {
                AppNavigator.navigateTo("player")
            }
        }
    }
}