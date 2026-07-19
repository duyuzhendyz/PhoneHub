package com.phonehub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout

class RestartServiceReceiver : BroadcastReceiver {
    override
    fun onReceive(context: Context, intent: Intent): Unit {
        Intrinsics.checkNotNullParameter(context, "context")
        Log.i("PhoneHub", "RestartServiceReceiver: 触发重启 PhoneHubService")
        PhoneHubService.INSTANCE.start(context)
        }
    }
