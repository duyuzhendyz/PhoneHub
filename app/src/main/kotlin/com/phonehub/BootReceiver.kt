package com.phonehub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout

class BootReceiver : BroadcastReceiver {
    override
    fun onReceive(context: Context, intent: Intent): Unit {
        Intrinsics.checkNotNullParameter(context, "context")
        if (!Intrinsics.areEqual(intent != null ? intent.getAction() : null, "android.intent.action.BOOT_COMPLETED")) {
            if (!Intrinsics.areEqual(intent != null ? intent.getAction() : null, "android.intent.action.QUICKBOOT_POWERON")) {
                return
                }
            }
        Log.i("PhoneHub", "BootReceiver: 开机完成，启动 PhoneHubService")
        PhoneHubService.INSTANCE.start(context)
        }
    }
