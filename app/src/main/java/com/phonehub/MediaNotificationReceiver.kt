package com.phonehub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 电脑声音播放媒体通知的按钮点击接收器
 *
 * 处理通知中的三种操作，转发给电脑端执行（与远程控制页同款命令）：
 *  - 播放/暂停：media_play_pause
 *  - 上一曲：media_prev
 *  - 下一曲：media_next
 */
class MediaNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i("PhoneHub", "MediaNotificationReceiver: action=$action")
        when (action) {
            // 播放/暂停：切换手机端收听状态（由网页里的 AudioContext 控制，不暂停电脑端媒体）
            ACTION_TOGGLE -> MainActivity.instance?.togglePcAudioPlayback()
            // 上一曲 / 下一曲：直接发送媒体键给电脑端
            ACTION_PREV -> ConnectionManager.sendMediaCommand("media_prev")
            ACTION_NEXT -> ConnectionManager.sendMediaCommand("media_next")
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.phonehub.action.PC_MEDIA_TOGGLE"
        const val ACTION_PREV = "com.phonehub.action.PC_MEDIA_PREV"
        const val ACTION_NEXT = "com.phonehub.action.PC_MEDIA_NEXT"
    }
}
