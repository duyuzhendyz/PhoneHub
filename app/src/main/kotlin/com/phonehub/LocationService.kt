package com.phonehub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class LocationService : Service() {

    companion object {
        private const val CHANNEL_ID = "phonehub_location"
        private const val MAX_SPEED_KMH = 30000.0f
        private const val MIN_DISTANCE_M = 100.0f
        private const val MIN_INTERVAL_MS = 30000L
        private const val NOTIF_ID = 2002
        private const val RETAIN_MS = 604800000L
        private const val TAG = "PHLocation"

        @Volatile
        private var instance: LocationService? = null

        @Volatile
        private var lastLocation: Location? = null

        fun getInstance(): LocationService? = instance

        fun requestSingleUpdate() {
            getInstance()?.requestSingle()
        }
    }

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleLocation(location, false)
        }

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
    }

    private var locationManager: LocationManager? = null

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        startForeground(NOTIF_ID, buildNotification("PhoneHub"))
        val systemService = getSystemService("location")
        this.locationManager = systemService as? LocationManager
        try {
            val locationManager = this.locationManager
            var z = true
            if (locationManager != null && locationManager.isProviderEnabled("gps")) {
                this.locationManager?.requestLocationUpdates("gps", MIN_INTERVAL_MS, MIN_DISTANCE_M, this.listener)
                Log.i(TAG, "GPS_PROVIDER 已注册")
                return
            }
            val locationManager3 = this.locationManager
            if (locationManager3 == null || !locationManager3.isProviderEnabled("network")) {
                z = false
            }
            if (z) {
                this.locationManager?.requestLocationUpdates("network", MIN_INTERVAL_MS, MIN_DISTANCE_M, this.listener)
                Log.i(TAG, "NETWORK_PROVIDER 已注册（GPS 不可用）")
                return
            }
            Log.w(TAG, "无可用定位 Provider")
        } catch (e: SecurityException) {
            Log.e(TAG, "无定位权限", e)
        } catch (e: Exception) {
            Log.e(TAG, "注册定位失败", e)
        }
    }

    fun requestSingle() {
        try {
            val locationManager = this.locationManager
            if (locationManager != null) {
                val it = locationManager.getLastKnownLocation("gps")
                if (it != null) {
                    handleLocation(it, true)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "单次定位无权限", e)
        }
    }

    fun handleLocation(loc: Location, force: Boolean) {
        val last = lastLocation
        if (last != null) {
            val dtMs = maxOf(loc.time - last.time, 1L)
            val dtMin = dtMs / 60000.0
            val distM = last.distanceTo(loc)
            val speedKmh = (distM / 1000.0 / dtMin) * 60.0
            if (speedKmh > MAX_SPEED_KMH) {
                Log.w(TAG, "丢弃 GPS 漂移: $speedKmh km/h dist=$distM m")
                return
            } else if (!force && distM < MIN_DISTANCE_M) {
                return
            }
        }
        lastLocation = loc
        val point = ConnectionManager.LocationPoint(loc.latitude, loc.longitude, loc.time, false)
        ConnectionManager.INSTANCE.addLocationPoint(point)
        ConnectionManager.INSTANCE.reportLocation(point)
        cacheLocally(point)
    }

    fun cacheLocally(p: ConnectionManager.LocationPoint) {
        try {
            val dir = File(getExternalFilesDir(null), "LocationCache")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val cutoff = System.currentTimeMillis() - RETAIN_MS
            val listFiles = dir.listFiles()
            if (listFiles != null) {
                for (file in listFiles) {
                    if (file.lastModified() < cutoff) {
                        file.delete()
                    }
                }
            }
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date(p.timestamp))
            val file2 = File(dir, "loc_$ts.json")
            val json = JSONObject()
            json.put("lat", p.lat)
            json.put("lon", p.lon)
            json.put("timestamp", p.timestamp)
            json.put("uploaded", p.uploaded)
            val fileOutputStream = FileOutputStream(file2)
            try {
                val str = json.toString()
                val bytes = str.toByteArray(Charsets.UTF_8)
                fileOutputStream.write(bytes)
            } finally {
                fileOutputStream.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "cacheLocally failed", e)
        }
    }

    fun uploadCachedToPaw() {
        try {
            val dir = File(getExternalFilesDir(null), "LocationCache")
            if (dir.exists()) {
                val listFiles = dir.listFiles()
                if (listFiles != null) {
                    val files = listFiles.sortedWith(compareBy { it.name })
                    val arr = JSONArray()
                    for (f in files) {
                        try {
                            val obj = JSONObject(f.readText())
                            obj.put("uploaded", true)
                            arr.put(obj)
                        } catch (e: Exception) {
                        }
                    }
                    ConnectionManager.INSTANCE.uploadLocationBatch(arr)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadCachedToPaw failed", e)
        }
    }

    fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL_ID, "PhoneHub 定位", 1)
        mgr.createNotificationChannel(ch)
    }

    fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhoneHub")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(-2)
            .build()
    }

    override fun onDestroy() {
        try {
            val locationManager = this.locationManager
            if (locationManager != null) {
                locationManager.removeUpdates(this.listener)
            }
        } catch (e: Exception) {
        }
        instance = null
        super.onDestroy()
    }
}
