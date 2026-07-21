package com.phonehub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != "android.intent.action.BOOT_COMPLETED" &&
            action != "android.intent.action.QUICKBOOT_POWERON") {
            return
        }
        Log.i("PhoneHub", "BootReceiver: 开机完成，启动 PhoneHubService")
        PhoneHubService.Companion.start(context)
    }
}
