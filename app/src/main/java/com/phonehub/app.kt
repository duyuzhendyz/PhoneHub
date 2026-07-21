package com.phonehub

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log

class App : Application() {
    companion object {
        @Volatile
        lateinit var context: android.content.Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        // 初始化连接管理器（建立 HttpClient、接收目录、剪贴板监控）
        ConnectionManager.init(this)
        // 启动前台保活服务
        PhoneHubService.start(this)
        // 请求忽略电池优化（加入白名单，防止后台被杀）
        requestBatteryOptimization()
    }

    private fun requestBatteryOptimization() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                Log.i("PhoneHub", "已请求忽略电池优化")
            }
        } catch (e: Exception) {
            Log.w("PhoneHub", "请求电池优化白名单失败: ${e.message}")
        }
    }
}
