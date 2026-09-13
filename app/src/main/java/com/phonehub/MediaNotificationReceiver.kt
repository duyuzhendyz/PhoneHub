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
            // 播放/暂停：与「远程控制」页同一个逻辑 —— 直接发 media_play_pause 控制电脑端媒体
            ACTION_TOGGLE -> ConnectionManager.sendMediaCommand("media_play_pause")
            // 上一曲 / 下一曲：发送媒体键给电脑端
            ACTION_PREV -> ConnectionManager.sendMediaCommand("media_prev")
            ACTION_NEXT -> ConnectionManager.sendMediaCommand("media_next")
            // 停止/开启播放：控制手机端是否收听（网页 AudioContext 静音/恢复），电脑端不受影响
            ACTION_STOP_LISTEN -> MainActivity.instance?.stopPcAudioListening()
            ACTION_START_LISTEN -> MainActivity.instance?.startPcAudioListening()
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.phonehub.action.PC_MEDIA_TOGGLE"
        const val ACTION_PREV = "com.phonehub.action.PC_MEDIA_PREV"
        const val ACTION_NEXT = "com.phonehub.action.PC_MEDIA_NEXT"
        const val ACTION_STOP_LISTEN = "com.phonehub.action.PC_MEDIA_STOP_LISTEN"
        const val ACTION_START_LISTEN = "com.phonehub.action.PC_MEDIA_START_LISTEN"
    }
}
