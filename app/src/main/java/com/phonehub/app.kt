package com.phonehub

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex

class App : Application() {
    companion object {
        @Volatile
        lateinit var context: android.content.Context
            private set
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        // 初始化日志工具（写入应用私有目录）
        LogUtil.init(this)
        LogUtil.accI("App 启动")
        // 初始化连接管理器（建立 HttpClient、接收目录、剪贴板监控）
        ConnectionManager.init(this)
        // 启动前台保活服务
        PhoneHubService.start(this)
    }
}
