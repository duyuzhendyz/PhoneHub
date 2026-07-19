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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import com.phonehub.ConnectionManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Comparator
import java.util.Date
import java.util.List
import java.util.Locale
import kotlin.Deprecated
import kotlin.Unit
import kotlin.collections.ArraysKt
import kotlin.comparisons.ComparisonsKt
import kotlin.io.CloseableKt
import kotlin.io.FilesKt
import kotlin.ranges.RangesKt
import kotlin.text.Charsets
import org.json.JSONArray
import org.json.JSONObject

class LocationService : Service {
    val CHANNEL_ID: private static final String = "phonehub_location"

    val INSTANCE: public static final Companion = new Companion(null)
    val MAX_SPEED_KMH: private static final float = 30000.0f
    val MIN_DISTANCE_M: private static final float = 100.0f
    val MIN_INTERVAL_MS: private static final long = 30000
    val NOTIF_ID: private static final int = 2002
    val RETAIN_MS: private static final long = 604800000
    val TAG: private static final String = "PHLocation"
    var instance: private static volatile LocationService? = null
    var lastLocation: private static volatile Location? = null
    private final LocationService$listener$1 listener = LocationListener() { // from class: com.phonehub.LocationService$listener$1
        override
        fun onLocationChanged(location: Location): Unit {
            Intrinsics.checkNotNullParameter(location, "location")
            LocationService.handleLocation$default(LocationService.this, location, false, 2, null)
            }

        override
        fun onProviderEnabled(provider: String): Unit {
            Intrinsics.checkNotNullParameter(provider, "provider")
            }

        override
        fun onProviderDisabled(provider: String): Unit {
            Intrinsics.checkNotNullParameter(provider, "provider")
            }

        override
        fun onStatusChanged(provider: String, status: Int, extras: Bundle): Unit {
            }
        }
    var locationManager: private LocationManager? = null

    public static final class Companion {
        public  Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this()
            }

        fun Companion(): private {
            }

        fun getInstance(): LocationService {
            return LocationService.instance
            }

        fun requestSingleUpdate(): Unit {
            val companion: LocationService = getInstance()
            if (companion != null) {
                companion.requestSingle()
                }
            }
        }

    override
    fun onBind(intent: Intent): IBinder {
        var null: return? = null
        }

    override
    fun onCreate(): Unit {
        super.onCreate()
        instance = this
        createChannel()
        startForeground(NOTIF_ID, buildNotification("PhoneHub"))
        val systemService: Any = getSystemService("location")
        this.locationManager = systemService is LocationManager ? (LocationManager) systemService : null
        try {
            val locationManager: LocationManager = this.locationManager
            val z: Boolean = true
            if (locationManager != null && locationManager.isProviderEnabled("gps")) {
                val locationManager2: LocationManager = this.locationManager
                if (locationManager2 != null) {
                    locationManager2.requestLocationUpdates("gps", MIN_INTERVAL_MS, MIN_DISTANCE_M, this.listener)
                    }
                Log.i(TAG, "GPS_PROVIDER 已注册")
                return
                }
            val locationManager3: LocationManager = this.locationManager
            if (locationManager3 == null || !locationManager3.isProviderEnabled("network")) {
                z = false
                }
            if (z) {
                val locationManager4: LocationManager = this.locationManager
                if (locationManager4 != null) {
                    locationManager4.requestLocationUpdates("network", MIN_INTERVAL_MS, MIN_DISTANCE_M, this.listener)
                    }
                Log.i(TAG, "NETWORK_PROVIDER 已注册（GPS 不可用）")
                return
                }
            Log.w(TAG, "无可用定位 Provider")
            } catch (SecurityException e) {
            Log.e(TAG, "无定位权限", e)
            } catch (Exception e2) {
            Log.e(TAG, "注册定位失败", e2)
            }
        }

    fun requestSingle(): Unit {
        var it: Location? = null
        try {
            val locationManager: LocationManager = this.locationManager
            if (locationManager != null && (it = locationManager.getLastKnownLocation("gps")) != null) {
                handleLocation(it, true)
                }
            } catch (SecurityException e) {
            Log.e(TAG, "单次定位无权限", e)
            }
        }

    public static  Unit handleLocation$default(LocationService locationService, Location location, boolean z, int i, Object obj) {
        if ((i & 2) != 0) {
            z = false
            }
        locationService.handleLocation(location, z)
        }

    fun handleLocation(loc: Location, force: Boolean): Unit {
        val last: Location = lastLocation
        if (last != null) {
            val dtMs: Long = RangesKt.coerceAtLeast(loc.getTime() - last.getTime(), 1L)
            val dtMin: Double = dtMs / 60000.0d
            val distM: Float = last.distanceTo(loc)
            val speedKmh: Double = ((distM / 1000.0d) / dtMin) * 60.0d
            if (speedKmh > 30000.0d) {
                Log.w(TAG, "丢弃 GPS 漂移: " + speedKmh + "km/h dist=" + distM + "m")
                return
                } else if (!force && distM < MIN_DISTANCE_M) {
                return
                }
            }
        lastLocation = loc
        ConnectionManager.LocationPoint point = new ConnectionManager.LocationPoint(loc.getLatitude(), loc.getLongitude(), loc.getTime(), false)
        ConnectionManager.INSTANCE.addLocationPoint(point)
        ConnectionManager.INSTANCE.reportLocation(loc)
        cacheLocally(point)
        }

    fun cacheLocally(/* ConnectionManager.LocationPoint p */): Unit {
        try {
            val dir: File = new File(getExternalFilesDir(null), "LocationCache")
            if (!dir.exists()) {
                dir.mkdirs()
                }
            val cutoff: Long = System.currentTimeMillis() - 604800000
            val listFiles: Array<File> = dir.listFiles()
            if (listFiles != null) {
                for (File file : listFiles) {
                    if (file.lastModified() < cutoff) {
                        file.delete()
                        }
                    }
                }
            val ts: String = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(new Date(p.getTimestamp()))
            val file2: File = new File(dir, "loc_" + ts + ".json")
            val json: JSONObject = new JSONObject()
            json.put("lat", p.getLat())
            json.put("lon", p.getLon())
            json.put("timestamp", p.getTimestamp())
            json.put("uploaded", p.getUploaded())
            val fileOutputStream: FileOutputStream = new FileOutputStream(file2)
            try {
                val it: FileOutputStream = fileOutputStream
                val jSONObject: String = json.toString()
                Intrinsics.checkNotNullExpressionValue(jSONObject, "toString(...)")
                val bytes: Array<Byte> = jSONObject.getBytes(Charsets.UTF_8)
                Intrinsics.checkNotNullExpressionValue(bytes, "getBytes(...)")
                it.write(bytes)
                val unit: Unit = Unit.INSTANCE
                CloseableKt.closeFinally(fileOutputStream, null)
                } finally {
                }
            } catch (Exception e) {
            Log.e(TAG, "cacheLocally failed", e)
            }
        }

    fun uploadCachedToPaw(): Unit {
        Object[] $this$sortedBy$iv
        var files: List<File>? = null
        try {
            val dir: File = new File(getExternalFilesDir(null), "LocationCache")
            if (dir.exists() && ($this$sortedBy$iv = dir.listFiles()) != null && (files = ArraysKt.sortedWith($this$sortedBy$iv, Comparator() { // from class: com.phonehub.LocationService$uploadCachedToPaw$$inlined$sortedBy$1
                override
                fun compare(t: T, t2: T): Int {
                    val it: File = (File) t
                    val it2: File = (File) t2
                    return ComparisonsKt.compareValues(it.getName(), it2.getName())
                    }
                })) != null) {
                val arr: JSONArray = new JSONArray()
                for (File f : files) {
                    try {
                        Intrinsics.checkNotNull(f)
                        val obj: JSONObject = new JSONObject(FilesKt.readText$default(f, null, 1, null))
                        obj.put("uploaded", true)
                        arr.put(obj)
                        } catch (Exception e) {
                        }
                    }
                ConnectionManager.INSTANCE.uploadLocationBatch(arr)
                }
            } catch (Exception e2) {
            Log.e(TAG, "uploadCachedToPaw failed", e2)
            }
        }

    fun createChannel(): Unit {
        val mgr: NotificationManager = (NotificationManager) getSystemService(NotificationManager.class)
        val ch: NotificationChannel = new NotificationChannel(CHANNEL_ID, "PhoneHub 定位", 1)
        mgr.createNotificationChannel(ch)
        }

    fun buildNotification(text: String): Notification {
        val build: Notification = new NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("PhoneHub").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).setPriority(-2).build()
        Intrinsics.checkNotNullExpressionValue(build, "build(...)")
        var build: return? = null
        }

    override
    fun onDestroy(): Unit {
        try {
            val locationManager: LocationManager = this.locationManager
            if (locationManager != null) {
                locationManager.removeUpdates(this.listener)
                }
            } catch (Exception e) {
            }
        instance = null
        super.onDestroy()
        }
    }
