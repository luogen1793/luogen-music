package com.luogen.music.ui.bluetooth

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.luogen.music.ui.components.AmbientBackground
import com.luogen.music.ui.components.GlassCard
import com.luogen.music.ui.components.GlassIconButton
import com.luogen.music.ui.components.PressScale
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.MainGradient
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain

/**
 * 蓝牙设备：真实调用系统蓝牙扫描附近设备，列出可配对的音频设备并连接。
 * 连接后通过系统 A2DP 播放，且由 Media3 MediaSession 自动接管蓝牙控制（上一曲/下一曲/播放暂停）。
 */
@Composable
fun BluetoothScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var scanning by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var bonded by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var connected by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var btEnabled by remember { mutableStateOf(false) }
    // A2DP 音频设备连接代理（异步获取，用于检测“当前已连接”的耳机/音箱）
    val a2dpProxy = remember { mutableStateOf<BluetoothA2dp?>(null) }
    // 标记“点去开启时发现权限未授予”，权限回调后再补发系统开启请求
    val pendingEnable = remember { mutableStateOf(false) }

    /** 从 A2DP 代理读取已连接设备列表（需要 BLUETOOTH_CONNECT 权限） */
    fun refreshConnected() {
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            connected = emptyList(); return
        }
        runCatching { connected = a2dpProxy.value?.connectedDevices?.toList() ?: emptyList() }
    }

    val profileListener = remember {
        object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    a2dpProxy.value = proxy as BluetoothA2dp
                    refreshConnected()
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.A2DP) {
                    a2dpProxy.value = null
                    connected = emptyList()
                }
            }
        }
    }

    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshBonded(context) { bonded = it }
        btEnabled = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true
        if (btEnabled) {
            devices = listOf()
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // 权限结果回来：若是为了开启蓝牙而来，则继续发送系统开启请求；否则直接扫描
        if (pendingEnable.value) {
            pendingEnable.value = false
            runCatching {
                val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = mgr?.adapter
                if (adapter != null && !adapter.isEnabled) {
                    enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
            }
        } else if (it.values.any { v -> v }) {
            startScan(context) { scanning = it }
        }
        refreshBonded(context) { bonded = it }
        devices = listOf()
        // 授权后补注册 A2DP 代理，刷新已连接设备
        if (a2dpProxy.value == null &&
            (Build.VERSION.SDK_INT < 31 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
        ) {
            runCatching {
                (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                    ?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
            }
        }
        refreshConnected()
    }

    fun ensureBt() {
        runCatching {
            val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = mgr?.adapter
            if (adapter == null) {
                Toast.makeText(context, "当前设备不支持蓝牙", Toast.LENGTH_SHORT).show()
                return
            }
            if (!adapter.isEnabled) {
                // Android 12+ 启动系统蓝牙开启对话框需要 BLUETOOTH_CONNECT 权限
                if (Build.VERSION.SDK_INT >= 31 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingEnable.value = true
                    permLauncher.launch(btPerms())
                    return
                }
                try {
                    enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                } catch (e: Exception) {
                    // 个别 ROM 不支持 ACTION_REQUEST_ENABLE：降级跳系统蓝牙设置
                    context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                return
            }
            btEnabled = true
            refreshBonded(context) { bonded = it }
            if (!hasBtPerm(context)) {
                permLauncher.launch(btPerms())
            } else if (!scanning) {
                scanning = true
                startScan(context) { scanning = it }
            }
        }.onFailure {
            Toast.makeText(context, "蓝牙操作失败：${it.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
        }
    }

    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val d = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        if (d != null && !devices.any { it.address == d.address }) {
                            devices = devices + listOf(d)
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> scanning = false
                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                        // 耳机/音箱连接状态变化（连接/断开）→ 实时刷新“已连接”区
                        refreshConnected()
                        refreshBonded(context) { bonded = it }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        // 配对完成/解除 → 实时刷新已配对列表
                        refreshBonded(context) { bonded = it }
                    }
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        // 蓝牙开关变化实时刷新（系统设置里开/关也会同步到这里）
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        val on = state == BluetoothAdapter.STATE_ON
                        btEnabled = on
                        if (on) {
                            refreshBonded(context) { bonded = it }
                            refreshConnected()
                        } else {
                            bonded = emptyList()
                            devices = emptyList()
                            connected = emptyList()
                        }
                    }
                }
            }
        }
    }
    DisposableEffect(Unit) {
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        runCatching { ContextCompat.registerReceiver(context, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED) }
        // 获取 A2DP 代理以检测已连接设备（异步回调）
        val mgr0 = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter0 = mgr0?.adapter
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            // 无权限时不注册代理，等授权回调后补注册
        } else {
            runCatching { adapter0?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP) }
        }
        // 进入页面立即检测当前蓝牙开关状态
        btEnabled = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true
        if (btEnabled && !hasBtPerm(context)) {
            // 蓝牙已开但无权限：主动请求一次，授权后刷新已配对设备
            permLauncher.launch(btPerms())
        } else {
            refreshBonded(context) { bonded = it }
            refreshConnected()
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.cancelDiscovery() }
            runCatching { (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.closeProfileProxy(BluetoothProfile.A2DP, a2dpProxy.value) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(extraGlow = LgSecondary)
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, size = 40.dp, onClick = onBack)
                    Spacer(Modifier.padding(4.dp))
                    Text("蓝牙设备", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (!btEnabled) {
                item {
                    Box(
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x2EFF6B6B))
                            .padding(14.dp),
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Bluetooth, null, tint = Color(0xFFFF9A9A), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.padding(4.dp))
                                Text("蓝牙未开启", color = Color(0xFFFFD0D0), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.weight(1f))
                                PressScale(onClick = { ensureBt() }) {
                                    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFFF7A7A)).padding(horizontal = 14.dp, vertical = 7.dp)) {
                                        Text("去开启", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("开启蓝牙后即可扫描并连接耳机 / 音箱，实现无线播放与媒体控制", color = Color(0xFFC8B8D8), fontSize = 11.sp)
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (scanning) "正在扫描附近蓝牙设备…" else "点击开始扫描 · 支持蓝牙耳机/音箱播放控制",
                        color = TextDim, fontSize = 12.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    PressScale(onClick = { if (!btEnabled) ensureBt() else if (!hasBtPerm(context)) permLauncher.launch(btPerms()) else { scanning = true; startScan(context) { scanning = it } } }) {
                        Box(Modifier.clip(RoundedCornerShape(18.dp)).background(MainGradient).padding(horizontal = 16.dp, vertical = 9.dp)) {
                            Text(if (scanning) "扫描中…" else "扫描", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
            // 已连接设备：通过 A2DP 代理实时检测，连接/断开立即刷新
            item {
                Text("已连接设备", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
            }
            val connectedAddrs = connected.map { it.address }.toSet()
            if (connected.isEmpty()) {
                item { Text("当前没有已连接的蓝牙音频设备", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            } else {
                items(connected, key = { it.address }) { d ->
                    DeviceRow(d, "已连接", connected = true) { openBtSettings(context) }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                Text("已配对设备", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
            }
            // 已连接的设备已在“已连接设备”区展示，这里只列未在使用的配对设备
            val pairedOnly = bonded.filter { it.address !in connectedAddrs }
            items(pairedOnly, key = { it.address }) { d ->
                DeviceRow(d, "已配对", connected = false) { connect(context, d) }
            }
            if (pairedOnly.isEmpty()) {
                item { Text("无其他已配对设备", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                Text("附近设备", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
            }
            items(devices, key = { it.address }) { d ->
                DeviceRow(d, "附近") { connect(context, d) }
            }
            if (devices.isEmpty()) {
                item { Text("扫描结果会显示在这里", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            }
            item { Spacer(Modifier.height(20.dp)) }
            item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column {
                        Text("说明", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("连接蓝牙耳机/音箱后：发现音乐自动支持蓝牙媒体控制（上一曲 / 下一曲 / 播放暂停），并可通过系统媒体按钮与 AVRCP 协议控制。", color = TextDim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(d: BluetoothDevice, tag: String, connected: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (connected) Color(0x2E3DDC84) else Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Bluetooth, null,
                tint = if (connected) Color(0xFF4CD97B) else LgSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.padding(4.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name ?: d.address, color = TextMain, fontSize = 14.sp)
            Text(tag, color = if (connected) Color(0xFF4CD97B) else TextDim, fontSize = 11.sp)
        }
        Text(if (connected) "管理 ›" else "连接 ›", color = LgSecondary, fontSize = 12.sp)
    }
}

private fun hasBtPerm(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 31) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
    }

private fun btPerms(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
}

private fun startScan(context: Context, onDone: (Boolean) -> Unit) {
    val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter = mgr.adapter
    if (adapter == null || !adapter.isEnabled) { onDone(false); return }
    runCatching {
        if (adapter.isDiscovering) adapter.cancelDiscovery()
        adapter.startDiscovery()
    }
    onDone(true)
}

private fun refreshBonded(context: Context, onResult: (List<BluetoothDevice>) -> Unit) {
    runCatching {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = mgr?.adapter
        if (adapter == null) { onResult(emptyList()); return }
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            // API 31+ 读取已配对设备同样需要 BLUETOOTH_CONNECT；未授权时静默返回空
            onResult(emptyList()); return
        }
        onResult(adapter.bondedDevices.toList())
    }
}

private fun openBtSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun connect(context: Context, d: BluetoothDevice) {
    runCatching {
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "请先授予蓝牙权限", Toast.LENGTH_SHORT).show(); return
        }
        if (d.bondState != BluetoothDevice.BOND_BONDED) {
            d.createBond()
            Toast.makeText(context, "正在配对：${d.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "已配对 ${d.name}，请在系统蓝牙设置中连接音频", Toast.LENGTH_SHORT).show()
            // 引导打开系统蓝牙设置，用户确认音频输出（A2DP）
            openBtSettings(context)
        }
    }
}