package com.phonehub

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.util.Log

class App : Application() {
    companion object {
        @Volatile
        private var context: Context? = null

        fun getContext(): Context {
            val ctx = context
            if (ctx != null) {
                return ctx
            }
            throw UninitializedPropertyAccessException("context")
        }
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        ConnectionManager.init(this)
        PhoneHubService.start(this)
        requestBatteryOptimization()
    }

    fun requestBatteryOptimization() {
        try {
            val systemService = getSystemService("power")
            val pm = systemService as? PowerManager
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
                intent.data = Uri.parse("package:" + packageName)
                intent.flags = 268435456
                startActivity(intent)
                Log.i("PhoneHub", "已请求忽略电池优化")
            }
        } catch (e: Exception) {
            Log.w("PhoneHub", "请求电池优化白名单失败: " + e.message)
        }
    }
}
