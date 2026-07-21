package com.phonehub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * AlarmManager 重启接收器：
 * PhoneHubService 被杀后通过 AlarmManager 定时触发此 Receiver，拉起服务
 */
class RestartServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i("PhoneHub", "RestartServiceReceiver: 触发重启 PhoneHubService")
        PhoneHubService.start(context)
    }
}
