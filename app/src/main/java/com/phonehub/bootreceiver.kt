package com.phonehub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机自启接收器：
 * 手机重启后自动启动 PhoneHubService
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.i("PhoneHub", "BootReceiver: 开机完成，启动 PhoneHubService")
            PhoneHubService.start(context)
        }
    }
}
