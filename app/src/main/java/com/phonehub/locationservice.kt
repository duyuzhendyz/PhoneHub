package com.phonehub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * GPS 定位前台服务
 *
 * - 持续上报位置到电脑端
 * - 前台服务类型：location
 * - 离线期间位置缓存到本地文件，恢复连接后批量上传
 */
class LocationService : Service() {

    companion object {
        private const val TAG = "PHLocation"
        private const val CHANNEL_ID = "phonehub_location"
        private const val NOTIFICATION_ID = 2002
        private const val MIN_INTERVAL_MS = 30_000L
        private const val MIN_DISTANCE_M = 100.0f

        @Volatile
        var instance: LocationService? = null
            private set

        @Volatile
        var lastLocation: Location? = null
            private set

        fun start(context: Context) {
            try {
                val intent = Intent(context, LocationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "start failed", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, LocationService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "stop failed", e)
            }
        }

        /**
         * 请求一次定位更新（短按触发）
         */
        fun requestSingleUpdate() {
            instance?.requestSingleLocation()
        }
    }

    private var locationManager: LocationManager? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleLocation(location)
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("定位服务运行中"))
        startLocationUpdates()
        Log.i(TAG, "LocationService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            Log.e(TAG, "removeUpdates failed", e)
        }
        instance = null
        super.onDestroy()
        Log.i(TAG, "LocationService destroyed")
    }

    private fun startLocationUpdates() {
        try {
            locationManager = (getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.also { lm ->
                val providers = lm.getProviders(true)
                // 优先 GPS，其次 NETWORK
                val provider = when {
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    else -> providers.firstOrNull() ?: return
                }
                lm.requestLocationUpdates(provider, MIN_INTERVAL_MS, MIN_DISTANCE_M, locationListener)
                Log.i(TAG, "Location updates started with provider=$provider")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "No location permission", e)
        } catch (e: Exception) {
            Log.e(TAG, "startLocationUpdates failed", e)
        }
    }

    /**
     * 请求单次定位
     */
    fun requestSingleLocation() {
        try {
            val lm = locationManager ?: return
            val providers = lm.getProviders(true)
            for (provider in providers) {
                @Suppress("MissingPermission")
                val location = lm.getLastKnownLocation(provider)
                if (location != null) {
                    handleLocation(location)
                    return
                }
            }
            // 没有缓存位置，请求一次新位置
            val provider = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                LocationManager.GPS_PROVIDER
            } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                LocationManager.NETWORK_PROVIDER
            } else return
            lm.requestSingleUpdate(provider, locationListener, android.os.Looper.getMainLooper())
        } catch (e: Exception) {
            Log.e(TAG, "requestSingleLocation failed", e)
        }
    }

    private fun handleLocation(location: Location) {
        lastLocation = location
        try {
            // 通过 ConnectionManager 上报位置到电脑
            // 这里仅保存到 lastLocation，由 ConnectionManager 主动获取或批量上传
            Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")
        } catch (e: Exception) {
            Log.e(TAG, "handleLocation failed", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "定位服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "GPS 位置上报功能需要此服务"
                setShowBadge(false)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhoneHub")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
