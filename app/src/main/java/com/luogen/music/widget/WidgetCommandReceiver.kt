package com.luogen.music.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.luogen.music.domain.model.PlayMode
import com.luogen.music.player.PlayerHub
import com.luogen.music.service.PlaybackService
import com.luogen.music.service.ListenTogetherService

/**
 * 小组件命令接收器：上一曲/下一曲/播放暂停/随机/循环/顺序 真实执行。
 */
class WidgetCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PlaybackService.CMD_PREV -> PlaybackService.prev(context)
            PlaybackService.CMD_NEXT -> PlaybackService.next(context)
            PlaybackService.CMD_PLAY_PAUSE -> {
                PlaybackService.playPause(context)
                // 一起听同步
                if (PlayerHub.togetherMode.value) {
                    val svc = Intent(context, ListenTogetherService::class.java)
                    context.startService(svc)
                }
            }
            PlaybackService.CMD_SET_MODE -> {
                val st = PlayerHub.state.value
                PlaybackService.setMode(context, when (st.mode) {
                    PlayMode.SEQUENCE -> PlayMode.REPEAT_ALL
                    PlayMode.REPEAT_ALL -> PlayMode.REPEAT_ONE
                    PlayMode.REPEAT_ONE -> PlayMode.SHUFFLE
                    PlayMode.SHUFFLE -> PlayMode.SEQUENCE
                })
            }
        }
        PlaybackService.onWidgetChanged(context)
    }
}