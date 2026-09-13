package com.phonehub.livemap

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * LiveMap · 手机端实时位置推送（独立测试 App）
 * ================================================
 * 前台服务（foregroundServiceType="location"），每 2 秒取一次最新位置，
 * 位移超过 10 米（或距上次推送超过 10 秒）就 POST 给电脑端：
 *
 *     POST http://<PC_IP>:5678/push
 *     {"lat":..,"lon":..,"spd":..,"bat":..,"acc":..,"ts":..}
 *
 * 断网时写入本地缓存文件，网络恢复后**按顺序**逐条补发，不丢轨迹。
 *
 * 状态通过 companion object 的 @Volatile 字段暴露给 MainActivity 轮询显示
 * （同一个进程，不需要广播）。
 */
class LiveLocationPusher : Service() {

    companion object {
        const val TAG = "LiveMap"
        const val CHANNEL_ID = "livemap_push"
        const val NOTIF_ID = 9101
        const val INTERVAL_MS = 2000L      // 取点间隔
        const val MIN_MOVE_M = 10.0        // 位移小于该值不推（省电省流量）
        const val MAX_IDLE_MS = 10_000L    // 但静止时也至少这么发一次心跳

        const val EXTRA_IP = "pc_ip"
        const val EXTRA_PORT = "pc_port"

        // ===== 给界面看的状态（同进程，直接读）=====
        @Volatile var running = false
        @Volatile var pushedOk = 0L
        @Volatile var pushedFail = 0L
        @Volatile var pendingCount = 0
        @Volatile var lastError: String? = null
        @Volatile var lastFixMs = 0L
        @Volatile var lastPushMs = 0L
        @Volatile var lastLat = 0.0
        @Volatile var lastLon = 0.0
        @Volatile var lastSpeedKmh = 0.0
        @Volatile var target = ""
    }

    private var pcIp = "192.168.3.9"
    private var pcPort = 5678
    private var lastPushed: Location? = null
    private var lastTs = 0L
    private var lastFix: Location? = null
    private val main = Handler(Looper.getMainLooper())
    private lateinit var lm: LocationManager

    private val ticker = object : Runnable {
        override fun run() {
            pushLatest()
            main.postDelayed(this, INTERVAL_MS)
        }
    }

    /** GPS / 网络双源，谁新用谁 */
    private val listener = LocationListener { loc ->
        lastFix = loc
        lastFixMs = System.currentTimeMillis()
    }

    override fun onCreate() {
        super.onCreate()
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            lastError = "缺少定位权限（ACCESS_FINE_LOCATION），服务已停止"
            Log.w(TAG, lastError!!)
            stopSelf()
            return
        }

        startForegroundCompat()
        running = true
        target = "$pcIp:$pcPort"
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener, main.looper)
        } catch (e: Exception) {
            Log.w(TAG, "GPS 源不可用: ${e.message}")
        }
        try {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 5f, listener, main.looper)
        } catch (e: Exception) {
            Log.w(TAG, "网络定位源不可用: ${e.message}")
        }
        main.post(ticker)
        Log.i(TAG, "LiveLocationPusher started → $target")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_IP)?.let { if (it.isNotBlank()) pcIp = it.trim() }
        intent?.getIntExtra(EXTRA_PORT, pcPort)?.let { if (it > 0) pcPort = it }
        target = "$pcIp:$pcPort"
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        running = false
        main.removeCallbacks(ticker)
        try {
            lm.removeUpdates(listener)          // 原来这里写了两遍，去掉重复
        } catch (_: Exception) {
        }
        super.onDestroy()
        Log.i(TAG, "LiveLocationPusher stopped")
    }

    // ============================== 推点 ==============================

    private fun pushLatest() {
        val loc = lastFix ?: bestKnown() ?: run {
            if (lastError == null) lastError = "还没拿到定位（室内请开一下 WiFi 定位 / 到窗边）"
            return
        }
        lastFix = loc
        if (lastFixMs == 0L) lastFixMs = System.currentTimeMillis()

        val now = System.currentTimeMillis()
        val moved = lastPushed?.distanceTo(loc)?.toDouble() ?: Double.MAX_VALUE
        if (lastPushed != null && moved < MIN_MOVE_M && now - lastTs < MAX_IDLE_MS) return
        lastPushed = loc
        lastTs = now

        lastLat = loc.latitude
        lastLon = loc.longitude
        lastSpeedKmh = if (loc.hasSpeed()) loc.speed * 3.6 else 0.0

        val body = "{\"lat\":${loc.latitude},\"lon\":${loc.longitude}," +
                "\"spd\":${if (loc.hasSpeed()) loc.speed else 0.0}," +
                "\"bat\":${batteryPct()}," +
                "\"acc\":${if (loc.hasAccuracy()) loc.accuracy else 0.0}," +
                "\"ts\":${now / 1000.0}}"

        Thread {
            if (post(body)) {
                pushedOk++
                lastPushMs = System.currentTimeMillis()
                lastError = null
                flushPending()                  // 网络恢复后把断网期间的点补发
            } else {
                pushedFail++
                savePending(body)               // 断网缓存
            }
            pendingCount = pendingLines()
        }.start()
    }

    private fun bestKnown(): Location? = try {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) null
        else listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { p ->
                try {
                    lm.getLastKnownLocation(p)
                } catch (_: Exception) {
                    null
                }
            }
            .maxByOrNull { it.time }
    } catch (_: Exception) {
        null
    }

    private fun post(body: String): Boolean = try {
        val conn = URL("http://$pcIp:$pcPort/push").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setFixedLengthStreamingMode(body.toByteArray().size)
        conn.outputStream.use { it.write(body.toByteArray()) }
        val ok = conn.responseCode in 200..299
        conn.disconnect()
        ok
    } catch (e: Exception) {
        lastError = "推送失败：${e.message}"
        Log.w(TAG, "push failed: ${e.message}")
        false
    }

    // ============================== 断网缓存 ==============================

    private fun cacheFile(): File = File(getExternalFilesDir(null), "livemap_pending.txt")

    @Synchronized
    private fun savePending(body: String) {
        try {
            cacheFile().appendText(body + "\n")
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun flushPending() {
        val f = cacheFile()
        if (!f.exists() || f.length() == 0L) return
        val lines = try {
            f.readLines()
        } catch (_: Exception) {
            return
        }
        val rest = lines.toMutableList()
        val it2 = rest.iterator()
        while (it2.hasNext()) {
            val b = it2.next()
            if (post(b)) it2.remove() else break   // 顺序补发，失败即停，保序
        }
        try {
            f.writeText(if (rest.isEmpty()) "" else rest.joinToString("\n") + "\n")
        } catch (_: Exception) {
        }
    }

    private fun pendingLines(): Int = try {
        val f = cacheFile()
        if (f.exists()) f.readLines().count { it.isNotBlank() } else 0
    } catch (_: Exception) {
        0
    }

    private fun batteryPct(): Float = try {
        val i = registerReceiver(
            null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        )
        val lvl = i?.getIntExtra("level", -1) ?: -1
        val scl = i?.getIntExtra("scale", -1) ?: -1
        if (lvl >= 0 && scl > 0) lvl * 100f / scl else -1f
    } catch (_: Exception) {
        -1f
    }

    // ============================== 前台通知 ==============================

    private fun startForegroundCompat() {
        val mgr = getSystemService(NotificationManager::class.java)
        // 通知渠道是 API 26 才有的概念；低版本直接发普通通知即可
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID, getString(R.string.notif_channel),
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
        val pi = PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        // 平台 API（不用 NotificationCompat），保持零第三方依赖
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val n = builder
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("LiveMap 位置共享中")
            .setContentText("正在向电脑推送实时位置")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }
}
