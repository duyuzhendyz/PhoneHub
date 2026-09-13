package com.phonehub.livemap

import android.Manifest
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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * LiveMap · 手机端实时位置推送（Kotlin 参考实现）
 * ================================================
 * 前台服务（foregroundServiceType="location"），每 2 秒（或位移 > 10 米）取一次最新位置，
 * POST 给电脑端 cs_apps/LiveMap/pc/map_server.py：
 *
 *     POST http://<PC_IP>:5678/push
 *     {"lat":..,"lon":..,"spd":..,"bat":..,"acc":..,"ts":..}
 *
 * 断网时写入本地缓存文件，网络恢复后逐条补发（不丢轨迹）。
 *
 * 并入主 app 时：
 *  - AndroidManifest 加 <service android:name=".LiveLocationPusher"
 *        android:foregroundServiceType="location" android:exported="false"/>
 *    以及 FOREGROUND_SERVICE_LOCATION 权限（ACCESS_FINE_LOCATION 已有）。
 *  - 路线图页加一个开关：startForegroundService(Intent(ctx, LiveLocationPusher::class.java)
 *        .putExtra("pc_ip", ip).putExtra("pc_port", 5678))。
 */
class LiveLocationPusher : Service() {

    companion object {
        const val TAG = "LiveMap"
        const val CHANNEL_ID = "livemap_push"
        const val NOTIF_ID = 9101
        const val INTERVAL_MS = 2000L      // 推点间隔
        const val MIN_MOVE_M = 10.0        // 位移小于该值不推（省电省流量）
    }

    private var pcIp = "192.168.3.9"
    private var pcPort = 5678
    private var lastPushed: Location? = null
    private var lastTs = 0L
    private val main = Handler(Looper.getMainLooper())
    private lateinit var lm: LocationManager

    private val ticker = object : Runnable {
        override fun run() {
            pushLatest()
            main.postDelayed(this, INTERVAL_MS)
        }
    }

    /** GPS/网络双源，谁新用谁 */
    private val listener = LocationListener { loc -> lastFix = loc }
    private var lastFix: Location? = null

    override fun onCreate() {
        super.onCreate()
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        startForegroundCompat()
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener, main.looper)
        } catch (_: SecurityException) {}
        try {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 5f, listener, main.looper)
        } catch (_: Exception) {}
        main.post(ticker)
        Log.i(TAG, "LiveLocationPusher started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("pc_ip")?.let { pcIp = it }
        intent?.getIntExtra("pc_port", pcPort)?.let { pcPort = it }
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        main.removeCallbacks(ticker)
        try { lm.removeUpdates(listener) } catch (_: Exception) {}
        try { lm.removeUpdates(listener) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun pushLatest() {
        val loc = lastFix ?: bestKnown() ?: return
        val now = System.currentTimeMillis()
        val moved = lastPushed?.distanceTo(loc)?.toDouble() ?: Double.MAX_VALUE
        if (lastPushed != null && moved < MIN_MOVE_M && now - lastTs < 10_000) return
        lastPushed = loc
        lastTs = now

        val body = "{\"lat\":${loc.latitude},\"lon\":${loc.longitude}," +
                "\"spd\":${loc.speed.toDouble()},\"bat\":${batteryPct()}," +
                "\"acc\":${if (loc.hasAccuracy()) loc.accuracy else 0.0},\"ts\":${now / 1000.0}}"
        Thread {
            if (!post(body)) {
                savePending(body)                       // 断网缓存
            } else {
                flushPending()                          // 恢复后补发
            }
        }.start()
    }

    private fun bestKnown(): Location? = try {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { p ->
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                ) lm.getLastKnownLocation(p) else null
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
        Log.w(TAG, "push failed: ${e.message}")
        false
    }

    private fun cacheFile(): File =
        File(getExternalFilesDir(null), "livemap_pending.txt")

    @Synchronized
    private fun savePending(body: String) {
        try {
            cacheFile().appendText(body + "\n")
        } catch (_: Exception) {}
    }

    @Synchronized
    private fun flushPending() {
        val f = cacheFile()
        if (!f.exists() || f.length() == 0L) return
        val lines = try { f.readLines() } catch (_: Exception) { return }
        val rest = lines.toMutableList()
        val it2 = rest.iterator()
        while (it2.hasNext()) {
            val b = it2.next()
            if (post(b)) it2.remove() else break       // 顺序补发，失败即停，保序
        }
        try { f.writeText(if (rest.isEmpty()) "" else rest.joinToString("\n") + "\n") } catch (_: Exception) {}
    }

    private fun batteryPct(): Float = try {
        val i = registerReceiver(null, android.content.IntentFilter(
            android.content.Intent.ACTION_BATTERY_CHANGED))
        val lvl = i?.getIntExtra("level", -1) ?: -1
        val scl = i?.getIntExtra("scale", -1) ?: -1
        if (lvl >= 0 && scl > 0) lvl * 100f / scl else -1f
    } catch (_: Exception) { -1f }

    private fun startForegroundCompat() {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "LiveMap 位置共享",
                NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
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
