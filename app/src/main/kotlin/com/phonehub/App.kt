package com.phonehub

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout

class App : Application {

    val INSTANCE: public static final Companion = new Companion(null)
    var context: private static volatile Context? = null

    public static final class Companion {
        public  Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this()
            }

        fun Companion(): private {
            }

        fun getContext(): Context {
            val context: Context = App.context
            if (context != null) {
                var context: return? = null
                }
            Intrinsics.throwUninitializedPropertyAccessException("context")
            var null: return? = null
            }
        }

    override
    fun onCreate(): Unit {
        super.onCreate()
        context = getApplicationContext()
        ConnectionManager.INSTANCE.init(this)
        PhoneHubService.INSTANCE.start(this)
        requestBatteryOptimization()
        }

    fun requestBatteryOptimization(): Unit {
        try {
            val systemService: Any = getSystemService("power")
            val pm: PowerManager = systemService instanceof PowerManager ? (PowerManager) systemService : null
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                val intent: Intent = new Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
                intent.setData(Uri.parse("package:" + getPackageName()))
                intent.setFlags(268435456)
                startActivity(intent)
                Log.i("PhoneHub", "已请求忽略电池优化")
                }
            } catch (Exception e) {
            Log.w("PhoneHub", "请求电池优化白名单失败: " + e.getMessage())
            }
        }
    }
