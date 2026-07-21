package com.phonehub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class RestartServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("PhoneHub", "RestartServiceReceiver: 触发重启 PhoneHubService")
        PhoneHubService.Companion.start(context)
    }
}
