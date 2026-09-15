package com.luogen.music.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.ServiceCompat
import com.luogen.music.LuogenApp
import com.luogen.music.data.prefs.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 摇一摇切歌服务：加速度传感器持续监听。
 *
 * 防误触规则（针对「手机稍微一晃就播放暂停/切歌」的问题彻底重构）：
 *  - 只有【同方向快速连续摇动 2 次】才触发动作：左摇两下 = 上一曲，右摇两下 = 下一曲；
 *  - 单次摇晃、左右各一下、日常轻微晃动（阈值提高至 3.5f）一律不触发任何动作；
 *  - 触发一次动作后进入 1500ms 冷却期，期间一切摇动直接忽略，
 *    避免「刚切完歌手还没放稳又触发一次」的连续误触；
 *  - 已取消「左右各一下=播放/暂停」映射，播放/暂停只能通过按钮或耳机控制。
 */
class ShakeGestureService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 摇动状态机
    private var lastEventAt = 0L   // 事件节流（60ms）
    private var lastStreakAt = 0L  // 上一次有效摇动时间
    private var streakDir = 0      // 当前连续方向：+1 右 / -1 左 / 0 无
    private var streakCount = 0    // 当前方向连续有效摇动次数
    private var lastActionAt = 0L  // 上次触发动作时间（冷却用）
    private var lastAx = 0f
    /** 动态基线（低通滤波）：跟随持机姿态，摇动方向的左右以它为准 */
    private var axBaseline = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fgOk = runCatching {
            ServiceCompat.startForeground(this, 1002, LuogenApp.instance.notificationCompatBuilder()
                .setContentTitle("摇一摇")
                .setContentText("已开启：左摇两下上一曲 / 右摇两下下一曲")
                .build(), 0)
        }.isSuccess
        if (!fgOk) {
            // Android 14 前台服务类型异常等情况下 startForeground 会抛异常；
            // 直接返回以免服务在崩溃循环中反复重启，功能缺失但 App 本身稳定。
            stopSelf()
            return START_NOT_STICKY
        }
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // 关键：必须注册监听，否则 onSensorChanged 永远不会回调
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val now = System.currentTimeMillis()
        val ax = event.values[0]
        val deltaAx = Math.abs(ax - lastAx)
        lastAx = ax
        axBaseline = 0.92f * axBaseline + 0.08f * ax // 低通滤波基线

        if (now - lastEventAt < 60) return
        lastEventAt = now

        // 触发冷却：切完歌后 1.5 秒内忽略一切摇动，防止惯性与余震带来的二次触发
        if (now - lastActionAt < 1500L) {
            streakDir = 0
            streakCount = 0
            return
        }

        // 严格阈值：低于 3.5f 的动作视为日常晃动/持机调整，不计入有效摇动
        if (deltaAx < 3.5f) return
        val dir = if (ax > axBaseline) 1 else -1

        // 同方向窗口：600ms 内同向叠加；方向变了（左右各一下）或超时则重新计数
        if (dir != streakDir || now - lastStreakAt > 600L) {
            streakDir = dir
            streakCount = 0
        }
        streakCount++
        lastStreakAt = now

        // 同方向连续 2 次 → 触发对应方向动作；单次晃动永远不触发
        if (streakCount >= 2) {
            val firedDir = streakDir
            streakDir = 0
            streakCount = 0
            lastActionAt = now
            vibrate()
            scope.launch {
                val s = LuogenApp.instance.settings
                if (!s.shakeEnabled.first()) return@launch
                val action = if (firedDir == 1) s.shakeRightAction.first() else s.shakeLeftAction.first()
                sendAction(action)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private suspend fun sendAction(action: String) {
        when (action) {
            SettingsRepo.ACTION_PREV -> PlaybackService.prev(this)
            SettingsRepo.ACTION_NEXT -> PlaybackService.next(this)
            SettingsRepo.ACTION_PLAY_PAUSE -> PlaybackService.playPause(this)
        }
    }

    private fun vibrate() {
        runCatching {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (v?.hasVibrator() == true) {
                v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    override fun onDestroy() {
        accelerometer?.let { runCatching { sensorManager.unregisterListener(this, it) } }
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, ShakeGestureService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ShakeGestureService::class.java)) }
        }
    }
}