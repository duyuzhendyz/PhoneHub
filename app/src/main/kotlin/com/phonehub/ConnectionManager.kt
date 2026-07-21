package com.phonehub

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.location.Location
import android.media.Image
import android.media.ImageReader
import android.view.KeyEvent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.constraintlayout.core.motion.utils.TypedValues
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.core.os.EnvironmentCompat
import androidx.lifecycle.CoroutineLiveDataKt
import com.google.android.material.card.MaterialCardViewHelper
import com.phonehub.ConnectionManager
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.HttpClientKt
import io.ktor.client.engine.android.Android
import io.ktor.client.engine.android.AndroidEngineConfig
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.DefaultRequestKt
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutKt
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestKt
import io.ktor.client.request.UtilsKt
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpMessagePropertiesKt
import io.ktor.http.contentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.LinkHeader
import io.ktor.http.content.NullBody
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.JsonSupportKt
import io.ktor.util.reflect.TypeInfoJvmKt
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.Reader
import java.lang.reflect.Type
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLConnection
import java.net.URLEncoder
import java.util.ArrayList
import java.util.Arrays
import java.util.Collection
import java.util.Iterator
import java.util.List
import java.util.ListIterator
import java.util.Locale
import java.util.Map
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.NoWhenBranchMatchedException
import kotlin.Pair
import kotlin.ResultKt
import kotlin.Triple
import kotlin.TuplesKt
import kotlin.Unit
import kotlin.collections.CollectionsKt
import kotlin.collections.MapsKt
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.Boxing
import kotlin.enums.EnumEntries
import kotlin.enums.EnumEntriesKt
import kotlin.io.CloseableKt
import kotlin.io.TextStreamsKt
import kotlin.jvm.functions.Function0
import kotlin.jvm.functions.Function1
import kotlin.jvm.functions.Function2
import kotlin.ranges.RangesKt
import kotlin.reflect.KType
import kotlin.reflect.TypesJVMKt
import kotlin.text.Charsets
import kotlin.text.MatchResult
import kotlin.text.Regex
import kotlin.text.RegexOption
import kotlin.text.StringsKt
import kotlinx.coroutines.BuildersKt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineScopeKt
import kotlinx.coroutines.DelayKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorKt
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharedFlowKt
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.StateFlowKt
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonElementBuildersKt
import kotlinx.serialization.json.JsonElementKt
import kotlinx.serialization.json.JsonKt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.internal.ws.RealWebSocket
import org.json.JSONArray

class ConnectionManager {
    companion object {
        private const val ACK_TIMEOUT_MS: Long = 10000L
        private const val CHUNK_SIZE: Int = 524288
        private const val CLIPBOARD_FAVORITE_MAX: Int = 50
        private const val CLIPBOARD_HISTORY_MAX: Int = 500
        const val DEFAULT_IP: String = "192.168.3.9"
        private const val FILE_TRANSFER_CHANNEL_ID: String = "phonehub_file_transfer"
        private const val FILE_TRANSFER_NOTIF_ID: Int = 88881
        const val KEY_CACHED_IP: String = "cached_pc_ip"
        private const val PREF_NAME: String = "phonehub_prefs"
        private const val RECONNECT_FAIL_THRESHOLD: Int = 3
        private const val TAG: String = "PhoneHub"
        @Volatile var activeProjection: MediaProjection? = null
        var adbWatchdogJob: Job? = null
        @Volatile var cachedProjectionData: Intent? = null
        @Volatile var cachedProjectionResultCode: Int? = null
        var client: HttpClient? = null
        var context: Context? = null
        @Volatile var currentConn: HttpURLConnection? = null
        @Volatile var fileTransferCancel: Boolean? = null
        @Volatile var lastConnectFailReason: String? = null
        @Volatile var lastNotifUpdateMs: Long? = null
        @Volatile var lastPcHeartbeatAt: Long? = null
        @Volatile var lastReceivedText: Pair<String, String>? = null
        var locationStoreDir: File? = null
        var msgPollingJob: Job? = null
        var pawPollingJob: Job? = null
        var pcAudioJob: Job? = null
        var pcAudioTrack: AudioTrack? = null
        var pcCameraJob: Job? = null
        @Volatile var pcFrameControlMode: Boolean? = null
        var pcFrameJob: Job? = null
        var pcIp: String? = null
        @Volatile var pendingFileTransfer: PendingFileTransfer? = null
        @Volatile var pendingSend: PendingSendInfo? = null
        var prevIdle: Long? = null
        var prevTotal: Long? = null
        var projectionManager: MediaProjectionManager? = null
        var receiveDir: File? = null
        var receiveJob: Job? = null
        var reconnectFailCount: Int? = null
        @Volatile var resumeInfo: ResumeInfo? = null
        var sendJob: Job? = null
        var statusJob: Job? = null
        var statusReportJob: Job? = null
        @Volatile var transferInProgress: Boolean? = null
        @Volatile var transferPaused: Boolean? = null
        @Volatile var userConnectedIntent: Boolean? = null
        val INSTANCE: ConnectionManager = ConnectionManager()
        private const val DEFAULT_SECRET_TOKEN: String = "541881452418845"
        var secretToken: String = DEFAULT_SECRET_TOKEN
        val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorKt.SupervisorJob())
        val mainHandler: Handler = Handler(Looper.getMainLooper())
        private val _connectionState: MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.DISCONNECTED)
        val connectionState: StateFlow<ConnectionState> = _connectionState
        private val _currentChannel: MutableStateFlow<ChannelType> = MutableStateFlow(ChannelType.NONE)
        val currentChannel: StateFlow<ChannelType> = _currentChannel
        private val _phoneMemUsage: MutableStateFlow<Float> = MutableStateFlow(0.0f)
        val phoneMemUsage: StateFlow<Float> = _phoneMemUsage
        private val _connectionMessage: MutableStateFlow<String> = MutableStateFlow("未连接")
        val connectionMessage: StateFlow<String> = _connectionMessage
        private val _connectionLatency: MutableStateFlow<Long> = MutableStateFlow(0L)
        val connectionLatency: StateFlow<Long> = _connectionLatency
        private val _receivedText: MutableSharedFlow<Pair<String, String>> = MutableSharedFlow(replay = 1, extraBufferCapacity = 16)
        val receivedText: SharedFlow<Pair<String, String>> = _receivedText
        private val _receivedUrl: MutableSharedFlow<String> = MutableSharedFlow(replay = 0, extraBufferCapacity = 16)
        val receivedUrl: SharedFlow<String> = _receivedUrl
        private val _urlHistorySync: MutableSharedFlow<List<Map<String, Any?>>> = MutableSharedFlow(replay = 0, extraBufferCapacity = 4)
        val urlHistorySync: SharedFlow<List<Map<String, Any?>>> = _urlHistorySync
        private val _cameraSwitchRequest: MutableSharedFlow<Unit> = MutableSharedFlow(replay = 0, extraBufferCapacity = 4)
        val cameraSwitchRequest: SharedFlow<Unit> = _cameraSwitchRequest
        private val _receivedClipboard: MutableStateFlow<String?> = MutableStateFlow(null)
        val receivedClipboard: StateFlow<String?> = _receivedClipboard
        private val _mediaInfo: MutableStateFlow<String> = MutableStateFlow("未检测到媒体播放")
        val mediaInfo: StateFlow<String> = _mediaInfo
        private val _mediaThumbnail: MutableStateFlow<ByteArray?> = MutableStateFlow(null)
        val mediaThumbnail: StateFlow<ByteArray?> = _mediaThumbnail
        private val _screenshotResult: MutableSharedFlow<String> = MutableSharedFlow(replay = 0, extraBufferCapacity = 4)
        val screenshotResult: SharedFlow<String> = _screenshotResult
        private val _fileTransferProgress: MutableStateFlow<TransferProgress?> = MutableStateFlow(null)
        val fileTransferProgress: StateFlow<TransferProgress?> = _fileTransferProgress
        private val _transferCompleted: MutableStateFlow<Boolean> = MutableStateFlow(false)
        val transferCompleted: StateFlow<Boolean> = _transferCompleted
        private val _completedTransfer: MutableSharedFlow<CompletedTransfer> = MutableSharedFlow(replay = 0, extraBufferCapacity = 4)
        val completedTransfer: SharedFlow<CompletedTransfer> = _completedTransfer
        private val _transferPausedFromPc: MutableStateFlow<Boolean> = MutableStateFlow(false)
        val transferPausedFromPc: StateFlow<Boolean> = _transferPausedFromPc
        private val _transferCancelledFromPc: MutableSharedFlow<String> = MutableSharedFlow(replay = 0, extraBufferCapacity = 4)
        val transferCancelledFromPc: SharedFlow<String> = _transferCancelledFromPc
        private val _notifications: MutableSharedFlow<NotificationItem> = MutableSharedFlow(replay = 100, extraBufferCapacity = 32)
        val notifications: SharedFlow<NotificationItem> = _notifications
        private val _locationPoints: MutableStateFlow<List<LocationPoint>> = MutableStateFlow(emptyList())
        val locationPoints: StateFlow<List<LocationPoint>> = _locationPoints
        private val _clipboardHistory: MutableStateFlow<List<ClipboardItem>> = MutableStateFlow(emptyList())
        val clipboardHistory: StateFlow<List<ClipboardItem>> = _clipboardHistory
        private val _clipboardFavorites: MutableStateFlow<List<ClipboardItem>> = MutableStateFlow(emptyList())
        val clipboardFavorites: StateFlow<List<ClipboardItem>> = _clipboardFavorites
        private val _pcFrame: MutableSharedFlow<ByteArray> = MutableSharedFlow(replay = 0, extraBufferCapacity = 4)
        val pcFrame: SharedFlow<ByteArray> = _pcFrame
        private val _pcCursorPos: MutableSharedFlow<Pair<Float, Float>> = MutableSharedFlow(replay = 0, extraBufferCapacity = 4)
        val pcCursorPos: SharedFlow<Pair<Float, Float>> = _pcCursorPos
        private val _pcCameraFrame: MutableSharedFlow<ByteArray> = MutableSharedFlow(replay = 0, extraBufferCapacity = 4)
        val pcCameraFrame: SharedFlow<ByteArray> = _pcCameraFrame
        @Volatile var _lastTouchDownX: Float = -1.0f
        @Volatile var _lastTouchDownY: Float = -1.0f
        const val DEFAULT_PORT: Int = 58627
        var connectPort: Int = DEFAULT_PORT
        @Volatile var lastClipboardContent: String = ""
        val fileReceiveState: ConcurrentHashMap<String, FileReceiveState> = ConcurrentHashMap()
        val ackTracker: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
        const val pcAudioSampleRate: Int = 44100
    }

    class WhenMappings {
        val $EnumSwitchMapping$0: IntArray = IntArray(ChannelType.values().size).also { iArr ->
            try { iArr[ChannelType.ADB.ordinal()] = 1 } catch (_: NoSuchFieldError) {}
            try { iArr[ChannelType.WIFI.ordinal()] = 2 } catch (_: NoSuchFieldError) {}
            try { iArr[ChannelType.NONE.ordinal()] = 3 } catch (_: NoSuchFieldError) {}
        }
    }

    constructor()

    fun getSecretToken(): String = secretToken

    fun loadPawConfig() {
        secretToken = DEFAULT_SECRET_TOKEN
        val ctx = context ?: return
        val prefs: SharedPreferences = ctx.getSharedPreferences(PREF_NAME, 0)
        val editor = prefs.edit()
        if (prefs.contains("paw_token")) {
            editor.remove("paw_token")
            Log.i(TAG, "已清理旧的 paw_token 缓存")
        }
        val cachedToken = prefs.getString("cached_token", "") ?: ""
        if (cachedToken.isNotEmpty()) {
            editor.remove("cached_token")
            Log.i(TAG, "已清理旧的 cached_token 缓存: $cachedToken")
        }
        editor.apply()
    }

    fun getConnectionState(): StateFlow<ConnectionState> = connectionState
    fun getCurrentChannel(): StateFlow<ChannelType> = currentChannel
    fun getPhoneMemUsage(): StateFlow<Float> = phoneMemUsage
    fun getConnectionMessage(): StateFlow<String> = connectionMessage
    fun getLastConnectFailReason(): String? = lastConnectFailReason
    fun getConnectionLatency(): StateFlow<Long> = connectionLatency
    fun getReceivedText(): SharedFlow<Pair<String, String>> = receivedText
    fun getLastReceivedText(): Pair<String, String>? = lastReceivedText
    fun getReceivedUrl(): SharedFlow<String> = receivedUrl
    fun getUrlHistorySync(): SharedFlow<List<Map<String, Any?>>> = urlHistorySync
    fun getCameraSwitchRequest(): SharedFlow<Unit> = cameraSwitchRequest
    fun getReceivedClipboard(): StateFlow<String?> = receivedClipboard
    fun getMediaInfo(): StateFlow<String> = mediaInfo
    fun getMediaThumbnail(): StateFlow<ByteArray?> = mediaThumbnail
    fun getScreenshotResult(): SharedFlow<String> = screenshotResult
    fun getFileTransferProgress(): StateFlow<TransferProgress?> = fileTransferProgress
    fun getTransferCompleted(): StateFlow<Boolean> = transferCompleted

    data class CompletedTransfer(val fileName: String?, val sending: Boolean?)

    fun getCompletedTransfer(): SharedFlow<CompletedTransfer> = completedTransfer
    fun getTransferPausedFromPc(): StateFlow<Boolean> = transferPausedFromPc
    fun getTransferCancelledFromPc(): SharedFlow<String> = transferCancelledFromPc
    fun getNotifications(): SharedFlow<NotificationItem> = notifications
    fun getLocationPoints(): StateFlow<List<LocationPoint>> = locationPoints
    fun getClipboardHistory(): StateFlow<List<ClipboardItem>> = clipboardHistory
    fun getClipboardFavorites(): StateFlow<List<ClipboardItem>> = clipboardFavorites

    fun getPcFrame(): SharedFlow<ByteArray> = pcFrame
    fun getPcCursorPos(): SharedFlow<Pair<Float, Float>> = pcCursorPos
    fun getPcFrameControlMode(): Boolean? = pcFrameControlMode
    fun setPcFrameControlMode(z: Boolean) { pcFrameControlMode = z }
    fun getPcCameraFrame(): SharedFlow<ByteArray> = pcCameraFrame
    fun getUserConnectedIntent(): Boolean? = userConnectedIntent
    fun setUserConnectedIntent(z: Boolean) { userConnectedIntent = z }
    fun getLastPcHeartbeatAt(): Long? = lastPcHeartbeatAt
    fun getTransferInProgress(): Boolean? = transferInProgress
    fun getPcIp(): String? = pcIp

    data class PendingSendInfo(
        val fileId: String,
        val fileName: String,
        val fileSize: Long,
        val file: File?,
        val uri: Uri?,
        val resolvedName: String,
        val deferred: CompletableDeferred<Boolean>?
    )

    data class ResumeInfo(
        val fileId: String,
        val fileName: String,
        val fileSize: Long,
        val file: File?,
        val uri: Uri?,
        @Volatile var resumeOffset: Long
    )

    data class FileReceiveState(
        val fileId: String,
        val fileName: String,
        val fileSize: Long,
        @Volatile var received: Long,
        @Volatile var partNum: Int
    )

    data class PendingFileTransfer(
        val fileId: String,
        val fileName: String,
        val fileSize: Long,
        val file: File?,
        val uri: Uri?,
        val resolvedName: String,
        val deferred: CompletableDeferred<Boolean>?
    )

    data class TransferProgress(
        val fileId: String,
        val fileName: String,
        val sent: Long,
        val total: Long,
        val receiving: Boolean
    )

    data class NotificationItem(
        val packageName: String,
        val title: String,
        val text: String,
        val timestamp: Long,
        val actions: List<NotificationAction> = emptyList(),
        val sbnId: Int = 0,
        val sbnTag: String = "",
        val key: String = ""
    )

    data class NotificationAction(
        val title: String,
        val actionIntent: PendingIntent?,
        val pkg: String,
        val tag: String,
        val id: Int
    )

    data class PcDriveInfo(
        val name: String,
        val label: String,
        val total: Long,
        val used: Long,
        val free: Long
    )

    data class LocationPoint(
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long,
        val accuracy: Float
    )

    data class ClipboardItem(
        val content: String,
        val source: String,
        val timestamp: Long,
        val isFavorite: Boolean = false
    )

    data class PcFileInfo(
        val name: String,
        val isDir: Boolean,
        val size: Long,
        val modified: Long
    )

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED;

    @Suppress("unused")
    private val $ENTRIES: EnumEntries<ConnectionState> = EnumEntriesKt.enumEntries($VALUES)
    fun getEntries(): EnumEntries<ConnectionState> = $ENTRIES
}

enum class ChannelType(val priority: Int) {
    NONE(0),
    ADB(30),
    WIFI(20);

    @Suppress("unused")
    private val $ENTRIES: EnumEntries<ChannelType> = EnumEntriesKt.enumEntries($VALUES)
    fun getEntries(): EnumEntries<ChannelType> = $ENTRIES

    companion object {
        fun fromName(s: String?): ChannelType {
            return when (s?.lowercase(Locale.ROOT)) {
                "adb" -> ChannelType.ADB
                "wifi" -> ChannelType.WIFI
                else -> ChannelType.NONE
            }
        }
    }
}

    fun init(ctx: Context) {
        context = ctx.applicationContext
        loadPawConfig()
        client = HttpClient(Android) {
            engine {
                connectTimeout = 10000
                socketTimeout = 30000
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30000L
                connectTimeoutMillis = ACK_TIMEOUT_MS
            }
            defaultRequest {
                header("Authorization", "Bearer $secretToken")
            }
        }
        var externalFilesDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (externalFilesDir == null) {
            externalFilesDir = ctx.filesDir
        }
        receiveDir = File(externalFilesDir, TAG)
        receiveDir?.mkdirs()
        locationStoreDir = File(ctx.getExternalFilesDir(null), "LocationCache")
        locationStoreDir?.mkdirs()
        loadClipboardStore()
        startStatusReportLoop()
        startAdbWatchdog()
        scope.launch { /* 初始化后续异步任务 */ }
    }

    fun hasReceivedPcCpu(): Boolean = userConnectedIntent == true && (lastPcHeartbeatAt ?: 0L) > 0L

    fun isNotificationListenerEnabled(): Boolean {
        val ctx = context ?: return false
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        if (flat.isEmpty()) return false
        val target = ComponentName(ctx, NotificationListener::class.java).flattenToString()
        return flat.split(":").any { it == target }
    }

    fun requestNotificationListenerRebind() {
        val ctx = context ?: return
        try {
            if (!isNotificationListenerEnabled()) return
            val instance = NotificationListener.getInstance()
            if (instance == null) {
                Log.i(TAG, "通知权限已开启但服务未运行，引导用户到设置页")
                NotificationListener.toggleNotificationAccess(ctx)
            } else {
                instance.reportAllActiveNotifications()
            }
        } catch (e: Exception) {
            Log.e(TAG, "requestNotificationListenerRebind failed", e)
        }
    }

    fun openNotificationSettings(ctx: Context) {
        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            intent.addFlags(268435456)
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openNotificationSettings failed", e)
        }
    }

    fun connect(ip: String = DEFAULT_IP, port: Int = DEFAULT_PORT, token: String = DEFAULT_SECRET_TOKEN) {
        userConnectedIntent = true
        lastConnectFailReason = null
        secretToken = token
        _connectionState.value = ConnectionState.CONNECTING
        _connectionMessage.value = "正在连接..."
        Log.i(TAG, "connect() called with ip=$ip, port=$port, adbAvailable=${isAdbAvailable()}")
        pcIp = ip
        connectPort = port
        scope.launch {
            // 连接任务实现由独立文件处理
        }
    }

    suspend fun testConnection(ip: String, port: Int = DEFAULT_PORT): Boolean {
        val httpClient = client ?: return false
        return try {
            val response: HttpResponse = httpClient.request {
                url("http://$ip:$port/api/status")
                method = HttpMethod.Get
                timeout { requestTimeoutMillis = 5000L }
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            Log.e(TAG, "Test connection failed to $ip:$port: ${e.javaClass.simpleName}: ${e.message}", e)
            lastConnectFailReason = e.message ?: e.javaClass.simpleName
            false
        }
    }

    fun isAdbAvailable(): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", 5037), 1000)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun startAdbWatchdog() {
        adbWatchdogJob?.cancel()
        adbWatchdogJob = scope.launch {
            // ADB 监控逻辑由独立文件处理
        }
    }

    fun downgradeFromAdb() {
        scope.launch {
            // ADB 降级逻辑由独立文件处理
        }
    }

    fun startChannel(channel: ChannelType) {
        when (channel) {
            ChannelType.ADB -> {
                _currentChannel.value = ChannelType.ADB
                _connectionState.value = ConnectionState.CONNECTED
                _connectionMessage.value = "已连接 - USB 数据线"
                startStatusPolling(ChannelType.ADB)
            }
            ChannelType.WIFI -> {
                _currentChannel.value = ChannelType.WIFI
                _connectionState.value = ConnectionState.CONNECTED
                _connectionMessage.value = "已连接 - WiFi 直连"
                startStatusPolling(ChannelType.WIFI)
            }
            ChannelType.NONE -> {
                // 不启动轮询
            }
        }
    }

    fun switchChannelImmediate(target: ChannelType) {
        if (transferInProgress == true) {
            Log.i(TAG, "传输中，暂缓通道切换到 $target")
            return
        }
        val cur = _currentChannel.value
        if (cur == target) return
        Log.i(TAG, "通道切换: $cur -> $target")
        statusJob?.cancel()
        pawPollingJob?.cancel()
        _currentChannel.value = target
        startChannel(target)
    }

    fun startStatusPolling(channel: ChannelType) {
        statusJob?.cancel()
        msgPollingJob?.cancel()
        statusJob = scope.launch {
            // 状态轮询逻辑由独立文件处理
        }
    }

    fun startMsgPolling(channel: ChannelType) {
        msgPollingJob?.cancel()
    }

    suspend fun handlePollFailure(channel: ChannelType, scope2: CoroutineScope) {
        reconnectFailCount = (reconnectFailCount ?: 0) + 1
        if ((reconnectFailCount ?: 0) >= RECONNECT_FAIL_THRESHOLD) {
            reconnectFailCount = 0
            when (channel) {
                ChannelType.ADB -> downgradeFromAdb()
                ChannelType.WIFI -> _connectionMessage.value = "WiFi 重连失败，请检查网络"
                ChannelType.NONE -> {}
            }
            return
        }
        _connectionMessage.value = "通道 $channel 失败，重试 $reconnectFailCount/$RECONNECT_FAIL_THRESHOLD..."
        delay(2000L)
        if (scope2.isActive) {
            startStatusPolling(channel)
        }
    }

    fun handlePcMessage(msg: JsonObject) {
        val data = msg["data"]?.let { it as? JsonObject } ?: return
        val action = (data["action"] as? JsonPrimitive)?.contentOrNull ?: return
        val source = (msg["source"] as? JsonPrimitive)?.contentOrNull ?: ""

        when (action) {
            "clipboard" -> {
                val txt = (data["txt"] as? JsonPrimitive)?.contentOrNull ?: ""
                if (txt.isNotEmpty()) {
                    setClipboardContent(txt)
                    _receivedClipboard.value = txt
                    addClipboardHistory(txt, "pc")
                }
            }
            "clipboard_favorite" -> {
                val content = (data["content"] as? JsonPrimitive)?.contentOrNull ?: ""
                val favorite = (data["favorite"] as? JsonPrimitive)?.booleanOrNull ?: false
                if (content.isNotEmpty()) applySyncedFavorite(content, favorite)
            }
            "screen_touch" -> {
                val x = (data["x"] as? JsonPrimitive)?.floatOrNull ?: return
                val y = (data["y"] as? JsonPrimitive)?.floatOrNull ?: return
                val op = (data["op"] as? JsonPrimitive)?.contentOrNull ?: "click"
                performScreenTouch(x, y, op)
            }
            "file_copy" -> {
                val src = (data["src"] as? JsonPrimitive)?.contentOrNull ?: return
                val dst = (data["dst"] as? JsonPrimitive)?.contentOrNull ?: return
                val isDir = (data["is_dir"] as? JsonPrimitive)?.booleanOrNull ?: false
                handleFileCopy(src, dst, isDir)
            }
            "camera_switch" -> { scope.launch { } }
            "screenshot_request" -> triggerScreenshot()
            "open_url" -> {
                val url = (data["url"] as? JsonPrimitive)?.contentOrNull ?: ""
                val openInVia = (data["open_in_via"] as? JsonPrimitive)?.booleanOrNull ?: false
                if (url.isNotEmpty()) {
                    scope.launch { }
                    openUrlOnDevice(url, openInVia)
                }
            }
            "cmd" -> {
                val cmd = (data["cmd"] as? JsonPrimitive)?.contentOrNull ?: ""
                handleCommand(cmd, data)
            }
            "txt" -> {
                val txt = (data["txt"] as? JsonPrimitive)?.contentOrNull ?: ""
                val filename = (data["filename"] as? JsonPrimitive)?.contentOrNull ?: ""
                if (txt.isNotEmpty()) {
                    lastReceivedText = Pair(filename, txt)
                    scope.launch { }
                    val ctx = context
                    if (ctx != null && !isAppInForeground(ctx)) {
                        showTextReceivedNotification(ctx, filename, txt)
                    }
                }
            }
            "send_file_request" -> {
                val path = (data["path"] as? JsonPrimitive)?.contentOrNull ?: return
                handleSendFileRequest(path)
            }
            "app_apk_request" -> {
                val pkg = (data["package"] as? JsonPrimitive)?.contentOrNull ?: return
                handleAppApkRequest(pkg)
            }
            "app_list_request" -> handleAppListRequest()
            "notification_action" -> {
                val pkg = (data["package"] as? JsonPrimitive)?.contentOrNull ?: return
                val actionTitle = (data["action_title"] as? JsonPrimitive)?.contentOrNull ?: ""
                handleNotificationAction(pkg, actionTitle)
            }
            "app_uninstall_request" -> {
                val pkg = (data["package"] as? JsonPrimitive)?.contentOrNull ?: return
                handleAppUninstallRequest(pkg)
            }
            "file_list_request" -> {
                val path = (data["path"] as? JsonPrimitive)?.contentOrNull ?: "/"
                handleFileListRequest(path)
            }
            "transfer_control" -> {
                val ctrl = (data["ctrl"] as? JsonPrimitive)?.contentOrNull ?: ""
                val fileId = (data["file_id"] as? JsonPrimitive)?.contentOrNull ?: ""
                Log.i(TAG, "收到 transfer_control: ctrl=$ctrl, fileId=$fileId")
                when (ctrl) {
                    "cancel" -> {
                        fileTransferCancel = true
                        transferPaused = false
                        _transferPausedFromPc.value = false
                        try { currentConn?.disconnect() } catch (_: Exception) {}
                        sendJob?.cancel()
                        receiveJob?.cancel()
                        transferInProgress = false
                        resumeInfo = null
                        pendingSend = null
                        _fileTransferProgress.value = null
                        cancelFileTransferNotification()
                        scope.launch { _transferCancelledFromPc.emit(fileId) }
                    }
                    "resume" -> {
                        transferPaused = false
                        fileTransferCancel = false
                        val info = resumeInfo
                        if (info != null) {
                            Log.i(TAG, "transfer_control resume: 断点续传 offset=${info.resumeOffset}")
                            scope.launch { }
                        } else {
                            Log.w(TAG, "transfer_control resume: 没有 resumeInfo，无法恢复")
                        }
                        _transferPausedFromPc.value = false
                    }
                    "pause" -> {
                        fileTransferCancel = true
                        transferPaused = true
                        try { currentConn?.disconnect() } catch (_: Exception) {}
                        _transferPausedFromPc.value = true
                    }
                }
            }
            "file_complete" -> {
                val fileId = (data["file_id"] as? JsonPrimitive)?.contentOrNull ?: ""
                ackTracker.remove(fileId)
                completeFileReceive(fileId)
            }
            "get_active_notifications" -> {
                NotificationListener.getInstance()?.reportAllActiveNotifications()
            }
            "cancel_notification" -> {
                val key = (data["key"] as? JsonPrimitive)?.contentOrNull ?: ""
                val pkg = (data["pkg"] as? JsonPrimitive)?.contentOrNull ?: ""
                val tag = (data["tag"] as? JsonPrimitive)?.contentOrNull ?: ""
                val id = (data["id"] as? JsonPrimitive)?.intOrNull ?: 0
                val listener = NotificationListener.getInstance()
                if (key.isNotEmpty()) {
                    listener?.cancelNotificationByKey(key)
                } else if (pkg.isNotEmpty()) {
                    listener?.cancelNotificationByKey("$pkg|$tag|$id")
                }
            }
            "file_accept" -> {
                val fileId = (data["file_id"] as? JsonPrimitive)?.contentOrNull ?: ""
                val resolvedName = (data["resolved_name"] as? JsonPrimitive)?.contentOrNull ?: ""
                Log.i(TAG, "收到 file_accept: fileId=$fileId, resolvedName=$resolvedName")
                val pending = pendingSend
                if (pending != null && pending.fileId == fileId) {
                    pending.deferred?.complete(true)
                } else {
                    Log.w(TAG, "file_accept: 没有匹配的 pendingSend (fileId=$fileId)")
                }
            }
            "file_delete" -> {
                val path = (data["path"] as? JsonPrimitive)?.contentOrNull ?: return
                val isDir = (data["is_dir"] as? JsonPrimitive)?.booleanOrNull ?: false
                handleFileDelete(path, isDir)
            }
            "send_file_head" -> {
                val fileName = (data["file_name"] as? JsonPrimitive)?.contentOrNull ?: "unknown"
                val fileSize = (data["file_size"] as? JsonPrimitive)?.longOrNull ?: 0L
                val fileId = (data["file_id"] as? JsonPrimitive)?.contentOrNull ?: ""
                Log.i(TAG, "收到send_file_head: name=$fileName, size=$fileSize, id=$fileId, channel=${_currentChannel.value}")
                startReceiveFile(fileId, fileName, fileSize)
            }
            "screenshot_saved" -> {
                val message = (data["message"] as? JsonPrimitive)?.contentOrNull ?: "截图已保存到电脑"
                scope.launch { _screenshotResult.emit(message) }
            }
            "url_history_sync" -> {
                val historyArr = data["history"] as? JsonArray
                if (historyArr != null) {
                    val historyList = mutableListOf<Map<String, Any?>>()
                    for (item in historyArr) {
                        val obj = item as? JsonObject ?: continue
                        val url = (obj["url"] as? JsonPrimitive)?.contentOrNull ?: ""
                        val direction = (obj["direction"] as? JsonPrimitive)?.contentOrNull ?: ""
                        val timestamp = (obj["timestamp"] as? JsonPrimitive)?.longOrNull ?: 0L
                        if (url.isNotEmpty()) {
                            historyList.add(mapOf("url" to url, "direction" to direction, "timestamp" to timestamp))
                        }
                    }
                    if (historyList.isNotEmpty()) {
                        scope.launch { _urlHistorySync.emit(historyList) }
                    }
                }
            }
            "media_info" -> {
                val title = (data["title"] as? JsonPrimitive)?.contentOrNull ?: ""
                val artist = (data["artist"] as? JsonPrimitive)?.contentOrNull ?: ""
                val thumbnail = (data["thumbnail"] as? JsonPrimitive)?.contentOrNull ?: ""
                val info = if (artist.isNotEmpty()) "$title - $artist" else if (title.isEmpty()) "未检测到媒体播放" else title
                _mediaInfo.value = info
                if (thumbnail.isEmpty()) {
                    _mediaThumbnail.value = null
                } else {
                    try { _mediaThumbnail.value = Base64.decode(thumbnail, 0) } catch (_: Exception) { _mediaThumbnail.value = null }
                }
            }
            "file_reject" -> {
                val fileId = (data["file_id"] as? JsonPrimitive)?.contentOrNull ?: ""
                val reason = (data["reason"] as? JsonPrimitive)?.contentOrNull ?: ""
                Log.i(TAG, "收到 file_reject: fileId=$fileId, reason=$reason")
                val pending = pendingSend
                if (pending != null && pending.fileId == fileId) {
                    pending.deferred?.complete(false)
                }
                transferInProgress = false
                _fileTransferProgress.value = null
            }
            "file_rename" -> {
                val oldPath = (data["old_path"] as? JsonPrimitive)?.contentOrNull ?: return
                val newPath = (data["new_path"] as? JsonPrimitive)?.contentOrNull ?: return
                handleFileRename(oldPath, newPath)
            }
            "file_mkdir" -> {
                val path = (data["path"] as? JsonPrimitive)?.contentOrNull ?: return
                handleFileMkdir(path)
            }
            "install_apk" -> {
                val path = (data["path"] as? JsonPrimitive)?.contentOrNull ?: ""
                if (path.isNotEmpty()) autoInstallApk(path)
            }
            "request_notif_permission" -> {
                Log.i(TAG, "收到 request_notif_permission，忽略（需用户手动开启）")
            }
        }
    }

    fun channelName(c: ChannelType): String = when (c) {
        ChannelType.ADB -> "USB 数据线"
        ChannelType.WIFI -> "WiFi 直连"
        ChannelType.NONE -> "无"
    }

    fun handleCommand(cmd: String, data: JsonObject) {
        when (cmd) {
            "vol_up" -> {
                val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }
            "vol_down" -> {
                val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            }
            "back" -> {
                PhoneHubAccessibilityService.getInstance()?.performGlobalAction(1)
            }
            "home" -> {
                PhoneHubAccessibilityService.getInstance()?.performGlobalAction(2)
            }
            "recents" -> {
                PhoneHubAccessibilityService.getInstance()?.performGlobalAction(3)
            }
            "notifications" -> {
                PhoneHubAccessibilityService.getInstance()?.performGlobalAction(4)
            }
            "quick_settings" -> {
                PhoneHubAccessibilityService.getInstance()?.performGlobalAction(5)
            }
            "power_dialog" -> {
                PhoneHubAccessibilityService.getInstance()?.performGlobalAction(6)
            }
            "take_screenshot" -> {
                PhoneHubAccessibilityService.getInstance()?.performGlobalAction(9)
            }
            "lock_screen" -> {
                PhoneHubAccessibilityService.getInstance()?.performGlobalAction(8)
            }
            "split_screen" -> {
                PhoneHubAccessibilityService.getInstance()?.performGlobalAction(7)
            }
            "input_text" -> {
                val txt = (data["text"] as? JsonPrimitive)?.contentOrNull ?: ""
                if (txt.isNotEmpty()) {
                    PhoneHubAccessibilityService.getInstance()?.inputText(txt)
                }
            }
            "click" -> {
                val x = (data["x"] as? JsonPrimitive)?.floatOrNull ?: return
                val y = (data["y"] as? JsonPrimitive)?.floatOrNull ?: return
                PhoneHubAccessibilityService.getInstance()?.performTap(x, y)
            }
            "swipe" -> {
                val x1 = (data["x1"] as? JsonPrimitive)?.floatOrNull ?: return
                val y1 = (data["y1"] as? JsonPrimitive)?.floatOrNull ?: return
                val x2 = (data["x2"] as? JsonPrimitive)?.floatOrNull ?: return
                val y2 = (data["y2"] as? JsonPrimitive)?.floatOrNull ?: return
                val duration = (data["duration"] as? JsonPrimitive)?.longOrNull ?: 300L
                PhoneHubAccessibilityService.getInstance()?.performSwipe(x1, y1, x2, y2, duration)
            }
            "launch_app" -> {
                val pkg = (data["package"] as? JsonPrimitive)?.contentOrNull ?: return
                val ctx = context ?: return
                val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                }
            }
            "wake_up" -> {
                val pm = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = pm?.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "PhoneHub:WakeUp")
                wakeLock?.acquire(3000L)
                wakeLock?.release()
            }
            "keep_awake" -> {
                val duration = (data["duration"] as? JsonPrimitive)?.longOrNull ?: 60000L
                val pm = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = pm?.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "PhoneHub:KeepAwake")
                wakeLock?.acquire(duration)
                wakeLock?.release()
            }
            "open_notifications" -> {
                val intent = Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context?.startActivity(intent)
            }
            "open_settings" -> {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context?.startActivity(intent)
            }
            "switch_camera" -> {
                scope.launch { _cameraSwitchRequest.emit(Unit) }
            }
            "play_pause" -> {
                val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val keycode = if (audio?.isMusicActive == true) 86 else 85
                audio?.dispatchMediaKeyEvent(KeyEvent(keycode, 0))
                audio?.dispatchMediaKeyEvent(KeyEvent(keycode, 1))
            }
            "next_track" -> {
                val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audio?.dispatchMediaKeyEvent(KeyEvent(87, 0))
                audio?.dispatchMediaKeyEvent(KeyEvent(87, 1))
            }
            "prev_track" -> {
                val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audio?.dispatchMediaKeyEvent(KeyEvent(88, 0))
                audio?.dispatchMediaKeyEvent(KeyEvent(88, 1))
            }
            else -> {
                Log.w(TAG, "Unknown command: $cmd")
            }
        }
    }

    fun handleNotificationAction(pkg: String, actionTitle: String) {
        try {
            val listener = NotificationListener.getInstance() ?: return
            val active = listener.getActiveNotifications() ?: return
            for (sbn in active) {
                if (sbn.packageName != pkg) continue
                val n = sbn.notification ?: continue
                val actions = n.actions ?: continue
                for (action in actions) {
                    val title = action.title?.toString() ?: continue
                    if (title == actionTitle) {
                        action.actionIntent?.send()
                        Log.i(TAG, "已触发通知动作: $pkg / $actionTitle")
                        return
                    }
                }
            }
            Log.w(TAG, "未找到匹配的通知动作: $pkg / $actionTitle")
        } catch (e: Exception) {
            Log.e(TAG, "handleNotificationAction failed", e)
        }
    }

    fun isAppInForeground(ctx: Context): Boolean {
        return try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
            val processes = am.runningAppProcesses ?: return false
            val pkg = ctx.packageName
            processes.any { it.processName == pkg && it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
        } catch (e: Exception) {
            false
        }
    }

    fun showTextReceivedNotification(ctx: Context, filename: String, txt: String) {
        try {
            val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel("phonehub_text", "文字消息", NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "接收电脑端发送的文字消息"
            mgr.createNotificationChannel(channel)
            val intent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("show_text_dialog", true)
            }
            val pi = PendingIntent.getActivity(ctx, System.currentTimeMillis().toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val copyIntent = Intent(ctx, TextNotificationReceiver::class.java).apply {
                action = TextNotificationReceiver.ACTION_COPY
                putExtra(TextNotificationReceiver.EXTRA_TEXT, txt)
            }
            val copyPi = PendingIntent.getBroadcast(ctx, (System.currentTimeMillis() + 1).toInt(), copyIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val saveIntent = Intent(ctx, TextNotificationReceiver::class.java).apply {
                action = TextNotificationReceiver.ACTION_SAVE
                putExtra(TextNotificationReceiver.EXTRA_TEXT, txt)
            }
            val savePi = PendingIntent.getBroadcast(ctx, (System.currentTimeMillis() + 2).toInt(), saveIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val preview = if (txt.length > 100) txt.substring(0, 100) + "..." else txt
            val title = if (filename.isNotEmpty()) "收到文字: $filename" else "收到文字"
            val notification = NotificationCompat.Builder(ctx, "phonehub_text")
                .setContentTitle(title)
                .setContentText(preview)
                .setStyle(NotificationCompat.BigTextStyle().bigText(txt))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .addAction(android.R.drawable.ic_menu_send, "复制", copyPi)
                .addAction(android.R.drawable.ic_menu_save, "保存", savePi)
                .build()
            mgr.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "显示文字接收通知失败", e)
        }
    }

    fun showFileReceiveNotification(fileId: String, fileName: String, fileSize: Long) {
        val ctx = context ?: run {
            Log.w(TAG, "showFileReceiveNotification: context 为 null，跳过")
            return
        }
        pendingFileTransfer = PendingFileTransfer(fileId, fileName, fileSize, null, null, fileName, null)
        try {
            val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(FILE_TRANSFER_CHANNEL_ID, "文件接收", NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "接收电脑端发送的文件"
            mgr.createNotificationChannel(channel)
            val openIntent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("show_file_transfer", true)
            }
            val openPi = PendingIntent.getActivity(ctx, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val startIntent = Intent(ctx, FileTransferReceiver::class.java).apply {
                action = FileTransferReceiver.ACTION_START_DOWNLOAD
                putExtra("file_id", fileId)
                putExtra(FileTransferReceiver.EXTRA_FILE_NAME, fileName)
                putExtra(FileTransferReceiver.EXTRA_FILE_SIZE, fileSize)
            }
            val startPi = PendingIntent.getBroadcast(ctx, 1, startIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val cancelIntent = Intent(ctx, FileTransferReceiver::class.java).apply {
                action = FileTransferReceiver.ACTION_CANCEL_DOWNLOAD
                putExtra("file_id", fileId)
            }
            val cancelPi = PendingIntent.getBroadcast(ctx, 2, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val sizeText = formatFileSize(fileSize)
            val notification = NotificationCompat.Builder(ctx, FILE_TRANSFER_CHANNEL_ID)
                .setContentTitle("收到文件: $fileName")
                .setContentText("大小: $sizeText — 点击「开始下载」接收")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPi)
                .addAction(android.R.drawable.stat_sys_download, "开始下载", startPi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancelPi)
                .setProgress(0, 0, false)
                .build()
            mgr.notify(FILE_TRANSFER_NOTIF_ID, notification)
            Log.i(TAG, "已显示文件接收通知: $fileName ($sizeText)")
        } catch (e: Exception) {
            Log.e(TAG, "显示文件接收通知失败", e)
        }
    }

    fun startFileDownloadFromNotification(fileId: String, fileName: String, fileSize: Long) {
        Log.i(TAG, "用户点击通知开始下载: $fileName")
        showToast("开始下载: $fileName")
        startReceiveFile(fileId, fileName, fileSize)
    }

    fun updateFileTransferNotification(fileName: String, received: Long, total: Long, paused: Boolean = false) {
        val now = System.currentTimeMillis()
        val lastMs = lastNotifUpdateMs ?: 0L
        if (now - lastMs < 400 && !paused) return
        lastNotifUpdateMs = now
        val ctx = context ?: return
        try {
            val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val pct = if (total > 0) ((100 * received) / total).toInt() else 0
            val sizeText = "${formatFileSize(received)} / ${formatFileSize(total)}"
            val builder = NotificationCompat.Builder(ctx, FILE_TRANSFER_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, pct, false)
                .setContentTitle(if (paused) "已暂停: $fileName" else "下载中: $fileName")
                .setContentText(if (paused) sizeText else "$sizeText ($pct%)")
            val pendingFileId = pendingFileTransfer?.fileId ?: ""
            val cancelIntent = Intent(ctx, FileTransferReceiver::class.java).apply {
                action = FileTransferReceiver.ACTION_CANCEL_DOWNLOAD
                putExtra("file_id", pendingFileId)
            }
            val cancelPi = PendingIntent.getBroadcast(ctx, 2, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancelPi)
            val openIntent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("show_file_transfer", true)
            }
            val openPi = PendingIntent.getActivity(ctx, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.setContentIntent(openPi)
            mgr.notify(FILE_TRANSFER_NOTIF_ID, builder.build())
        } catch (e: Exception) {
            Log.w(TAG, "更新文件传输通知失败: ${e.message}")
        }
    }

    fun completeFileTransferNotification(fileName: String) {
        val ctx = context ?: return
        try {
            val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val openIntent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("show_file_transfer", true)
            }
            val openPi = PendingIntent.getActivity(ctx, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(ctx, FILE_TRANSFER_CHANNEL_ID)
                .setContentTitle("下载完成: $fileName")
                .setContentText("文件已保存到接收目录")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setOngoing(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPi)
                .build()
            mgr.notify(FILE_TRANSFER_NOTIF_ID, notification)
            pendingFileTransfer = null
        } catch (e: Exception) {
            Log.w(TAG, "显示完成通知失败: ${e.message}")
        }
    }

    fun cancelFileTransferNotification() {
        val ctx = context ?: return
        try {
            val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            mgr?.cancel(FILE_TRANSFER_NOTIF_ID)
        } catch (e: Exception) {
            Log.w(TAG, "取消文件传输通知失败: ${e.message}")
        }
        pendingFileTransfer = null
    }

    fun formatFileSize(b: Long): String {
        if (b >= 1073741824L) return String.format(Locale.ROOT, "%.2f GB", b / 1.073741824E9)
        if (b >= 1048576L) return String.format(Locale.ROOT, "%.1f MB", b / 1048576.0)
        if (b >= 1024L) return String.format(Locale.ROOT, "%.0f KB", b / 1024.0)
        return "$b B"
    }

    fun startStatusReportLoop() {
        statusReportJob?.cancel()
        statusReportJob = scope.launch {
            while (isActive) {
                try {
                    sendStatusReport()
                } catch (e: Exception) {
                    Log.e(TAG, "状态上报失败", e)
                }
                delay(5000L)
            }
        }
    }

    fun sendStatusReport() {
        val ctx = context ?: return
        try {
            val battery = getBatteryStatus(ctx)
            val temp = getBatteryTemperature(ctx)
            val net = getNetworkType(ctx)
            val storage = getStorageInfo()
            val mem = getMemUsage()
            val cpu = getPhoneCpuUsage()
            _phoneMemUsage.value = mem
            val msg = buildJsonMessage {
                put("source", "phone")
                putJsonObject("data") {
                    put("action", "status")
                    put("cpu", cpu)
                    put("battery", battery)
                    put("temperature", temp)
                    put("network", net)
                    put("storage_total", storage.first)
                    put("storage_free", storage.second)
                    put("memory_usage", mem)
                    put("device_model", Build.MODEL)
                    put("android_version", Build.VERSION.RELEASE)
                }
            }
            scope.launch { sendRaw(msg.toString()) }
        } catch (e: Exception) {
            Log.e(TAG, "Status report failed", e)
        }
    }

    fun getPhoneCpuUsage(): Float {
        val fromDumpsys = getCpuFromDumpsys()
        if (fromDumpsys >= 0.0f) {
            Log.d(TAG, "getPhoneCpuUsage: dumpsys=$fromDumpsys")
            return fromDumpsys
        }
        val fromTop = getCpuFromTop()
        if (fromTop >= 0.0f) {
            Log.d(TAG, "getPhoneCpuUsage: top=$fromTop")
            return fromTop
        }
        val fromProcStat = getCpuFromProcStat()
        if (fromProcStat >= 0.0f) {
            Log.d(TAG, "getPhoneCpuUsage: procstat=$fromProcStat")
            return fromProcStat
        }
        Log.w(TAG, "getPhoneCpuUsage: 所有方案均失败，返回 0")
        return 0.0f
    }

    fun getCpuFromProcStat(): Float {
        return try {
            BufferedReader(FileReader("/proc/stat")).use { reader ->
                val line = reader.readLine() ?: return -1.0f
                if (!line.startsWith("cpu")) return -1.0f
                val parts = line.split(Regex("\\s+")).drop(1).map { it.toLongOrNull() ?: 0L }
                if (parts.size < 4) return -1.0f
                val idle = parts[3]
                val total = parts.sum()
                val pt = prevTotal ?: 0L
                val pi = prevIdle ?: 0L
                if (pt > 0) {
                    val dTotal = total - pt
                    val dIdle = idle - pi
                    prevTotal = total
                    prevIdle = idle
                    if (dTotal <= 0) return 0.0f
                    val pct = (((dTotal - dIdle).toFloat() / dTotal.toFloat()) * 100.0f).coerceIn(0.0f, 100.0f)
                    Log.d(TAG, "getCpuFromProcStat: $pct%")
                    return pct
                }
                prevTotal = total
                prevIdle = idle
                -1.0f
            }
        } catch (e: Exception) {
            -1.0f
        }
    }

    fun getCpuFromTop(): Float {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "top -b -n 1 2>&1"))
            val finished = process.waitFor(5L, TimeUnit.SECONDS)
            if (finished) {
                val output = process.inputStream.bufferedReader().use { it.readText() }
                return parseTopCpuUsage(output)
            }
            process.destroy()
            -1.0f
        } catch (e: Exception) {
            -1.0f
        }
    }

    fun getCpuFromDumpsys(): Float {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/dumpsys", "cpuinfo"))
            val finished = process.waitFor(5L, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                Log.w(TAG, "getCpuFromDumpsys: timeout")
                return -1.0f
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (output.isBlank()) {
                Log.w(TAG, "getCpuFromDumpsys: empty output")
                return -1.0f
            }
            for (line in output.lines().reversed()) {
                val trimmed = line.trim()
                if (trimmed.contains("TOTAL", ignoreCase = true)) {
                    val match1 = Regex("(\\d+\\.?\\d*)%\\s+TOTAL", RegexOption.IGNORE_CASE).find(trimmed)
                    if (match1 != null) {
                        val v = match1.groupValues[1].toFloatOrNull()
                        if (v != null) return v.coerceIn(0.0f, 100.0f)
                    }
                    val match2 = Regex("TOTAL:?(?:\\s+)(\\d+\\.?\\d*)%", RegexOption.IGNORE_CASE).find(trimmed)
                    if (match2 != null) {
                        val v = match2.groupValues[1].toFloatOrNull()
                        if (v != null) return v.coerceIn(0.0f, 100.0f)
                    }
                    val m = Regex("(\\d+\\.?\\d*)%").find(trimmed)
                    if (m != null) {
                        val v = m.groupValues[1].toFloatOrNull()
                        if (v != null && v <= 100.0f) return v
                    }
                }
            }
            Log.w(TAG, "getCpuFromDumpsys: no TOTAL line, output=${output.take(200)}")
            -1.0f
        } catch (e: Exception) {
            Log.e(TAG, "getCpuFromDumpsys failed", e)
            -1.0f
        }
    }

    fun parseTopCpuUsage(output: String): Float {
        for (rawLine in output.lineSequence()) {
            val line = rawLine.trim()
            if (line.startsWith("%Cpu", ignoreCase = true)) {
                val idleMatch = Regex("(\\d+\\.?\\d*)\\s+id").find(line)
                if (idleMatch != null) {
                    val idle = idleMatch.groupValues[1].toFloatOrNull()
                    if (idle != null) return (100.0f - idle).coerceIn(0.0f, 100.0f)
                }
            }
            if (line.startsWith("CPU:", ignoreCase = true)) {
                val idleMatch = Regex("(\\d+)%\\s+idle").find(line)
                if (idleMatch != null) {
                    val idle = idleMatch.groupValues[1].toFloatOrNull()
                    if (idle != null) return (100.0f - idle).coerceIn(0.0f, 100.0f)
                }
            }
            if (line.startsWith("User", ignoreCase = true) && line.contains("%") && line.contains("System", ignoreCase = true)) {
                val userMatch = Regex("User\\s+(\\d+)%", RegexOption.IGNORE_CASE).find(line)
                val sysMatch = Regex("System\\s+(\\d+)%", RegexOption.IGNORE_CASE).find(line)
                val iowMatch = Regex("IOW\\s+(\\d+)%", RegexOption.IGNORE_CASE).find(line)
                val irqMatch = Regex("IRQ\\s+(\\d+)%", RegexOption.IGNORE_CASE).find(line)
                val user = userMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0.0f
                val sys = sysMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0.0f
                val iow = iowMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0.0f
                val irq = irqMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0.0f
                return (user + sys + iow + irq).coerceIn(0.0f, 100.0f)
            }
        }
        Log.w(TAG, "parseTopCpuUsage: no CPU summary found, output=${output.take(200)}")
        return -1.0f
    }

    fun getBatteryStatus(ctx: Context): Int {
        return try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    fun getBatteryTemperature(ctx: Context): Float {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = if (Build.VERSION.SDK_INT >= 34) {
                ctx.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                ctx.registerReceiver(null, filter)
            }
            if (intent != null) {
                intent.getIntExtra("temperature", -1) / 10.0f
            } else -1.0f
        } catch (e: Exception) {
            -1.0f
        }
    }

    fun getNetworkType(ctx: Context): String {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "none"
            val nw = cm.activeNetwork ?: return "none"
            val cap = cm.getNetworkCapabilities(nw) ?: return "none"
            when {
                cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }

    fun getStorageInfo(): Pair<Long, Long> {
        return try {
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            val total = stat.totalBytes
            val free = stat.availableBytes
            Pair(total, free)
        } catch (e: Exception) {
            Pair(0L, 0L)
        }
    }

    fun getMemUsage(): Float {
        return try {
            val reader = BufferedReader(FileReader("/proc/meminfo"))
            var total = 0L
            var free = 0L
            var line = reader.readLine()
            while (line != null) {
                if (line.startsWith("MemTotal:")) {
                    total = line.split(Regex("\\s+"))[1].toLongOrNull() ?: 0L
                } else if (line.startsWith("MemAvailable:")) {
                    free = line.split(Regex("\\s+"))[1].toLongOrNull() ?: 0L
                }
                if (total > 0 && free > 0) break
                line = reader.readLine()
            }
            reader.close()
            if (total <= 0) return 0.0f
            ((total - free).toFloat() / total.toFloat() * 100.0f).coerceIn(0.0f, 100.0f)
        } catch (e: Exception) {
            0.0f
        }
    }

    fun setClipboardContent(text: String) {
        try {
            val ctx = context ?: return
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            cm.setPrimaryClip(ClipData.newPlainText("PhoneHub", text))
        } catch (e: Exception) {
            Log.e(TAG, "setClipboardContent failed", e)
        }
    }

    fun sendClipboard(text: String) {
        val ts = System.currentTimeMillis()
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "clipboard")
                put("text", text)
                put("timestamp", ts)
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    fun sendText(text: String, filename: String?) {
        val actualName = if (filename.isNullOrEmpty()) "未命名.txt" else filename
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "txt")
                put("filename", actualName)
                put("content", text)
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    fun sendMediaCommand(cmd: String) {
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "cmd")
                put("cmd", cmd)
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    fun requestPcScreenshot() {
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "pc_screenshot_request")
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    fun sendCameraSwitch(facing: String) {
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "camera_switch")
                put("facing", facing)
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    fun sendMediaKey(cmd: String) {
        try {
            val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val keycode = when (cmd) {
                "play_pause" -> if (audio.isMusicActive) 86 else 85
                "next_track" -> 87
                "prev_track" -> 88
                "stop" -> 89
                else -> return
            }
            audio.dispatchMediaKeyEvent(KeyEvent(keycode, KeyEvent.ACTION_DOWN))
            audio.dispatchMediaKeyEvent(KeyEvent(keycode, KeyEvent.ACTION_UP))
        } catch (e: Exception) {
            Log.e(TAG, "sendMediaKey failed", e)
        }
    }

    fun adjustVolume(up: Boolean) {
        try {
            val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {
            Log.e(TAG, "adjustVolume failed", e)
        }
    }

    fun toggleMute() {
        try {
            val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {
            Log.e(TAG, "toggleMute failed", e)
        }
    }

    fun setVolume(vol: Int) {
        try {
            val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, vol.coerceIn(0, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {
            Log.e(TAG, "setVolume failed", e)
        }
    }

    fun sendFile(file: File) {
        scope.launch {
            try {
                val fileId = System.currentTimeMillis().toString()
                sendFileWifi(fileId, file, file.length())
            } catch (e: Exception) {
                Log.e(TAG, "sendFile failed", e)
            }
        }
    }

    fun sendFile(uri: Uri, displayName: String?) {
        scope.launch {
            try {
                val ctx = context ?: return@launch
                val fileId = System.currentTimeMillis().toString()
                val actualName = displayName ?: "file"
                val fileSize = getFileSizeFromUri(ctx, uri)
                sendFileWifiFromUri(fileId, uri, actualName, fileSize)
            } catch (e: Exception) {
                Log.e(TAG, "sendFile uri failed", e)
            }
        }
    }

    private fun getFileSizeFromUri(ctx: Context, uri: Uri): Long {
        return try {
            val cr = ctx.contentResolver
            val cursor = cr.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (idx >= 0) return it.getLong(idx)
                }
            }
            0L
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun uploadStreamInternal(
        url: String,
        fileName: String,
        fileSize: Long,
        inputStream: InputStream,
        chunkSize: Long
    ) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $secretToken")
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setRequestProperty("X-File-Name", URLEncoder.encode(fileName, "UTF-8"))
            conn.setRequestProperty("X-File-Size", fileSize.toString())
            conn.doOutput = true
            conn.connectTimeout = ACK_TIMEOUT_MS.toInt()
            conn.readTimeout = 60000
            conn.useCaches = false
            conn.chunkLength = 0
            inputStream.use { input ->
                conn.outputStream.use { output ->
                    val buffer = ByteArray(chunkSize.toInt().coerceAtMost(CHUNK_SIZE))
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        output.flush()
                    }
                }
            }
            conn.responseCode
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "uploadStreamInternal failed", e)
        }
    }

    suspend fun sendFileWifi(fileId: String, file: File, fileSize: Long) {
        try {
            val url = "${getBaseUrl()}/api/upload?file_id=$fileId&file_name=${URLEncoder.encode(file.name, "UTF-8")}&file_size=$fileSize"
            FileInputStream(file).use { input ->
                uploadStreamInternal(url, file.name, fileSize, input, CHUNK_SIZE.toLong())
            }
            sendFileComplete(fileId)
            Log.i(TAG, "sendFileWifi 成功: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "sendFileWifi failed", e)
        }
    }

    suspend fun sendFileWifiFromUri(fileId: String, uri: Uri, fileName: String, fileSize: Long) {
        try {
            val ctx = context ?: return
            val url = "${getBaseUrl()}/api/upload?file_id=$fileId&file_name=${URLEncoder.encode(fileName, "UTF-8")}&file_size=$fileSize"
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                uploadStreamInternal(url, fileName, fileSize, input, CHUNK_SIZE.toLong())
            }
            sendFileComplete(fileId)
            Log.i(TAG, "sendFileWifiFromUri 成功: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "sendFileWifiFromUri failed", e)
        }
    }

    fun sendFileComplete(fileId: String) {
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "file_complete")
                put("file_id", fileId)
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    fun startAckWait(fileId: String) {
        scope.launch {
            try {
                delay(ACK_TIMEOUT_MS)
                Log.w(TAG, "ACK 超时: $fileId")
            } catch (e: Exception) {
                Log.e(TAG, "startAckWait failed", e)
            }
        }
    }

    fun startReceiveFile(fileId: String, fileName: String, fileSize: Long) {
        scope.launch {
            try {
                val ctx = context ?: return@launch
                val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PhoneHub")
                if (!downloadDir.exists()) downloadDir.mkdirs()
                val targetFile = File(downloadDir, fileName)
                val url = "${getBaseUrl()}/api/download?file_id=$fileId&file_name=${URLEncoder.encode(fileName, "UTF-8")}&file_size=$fileSize"
                Log.i(TAG, "startReceiveFile: fileId=$fileId fileName=$fileName size=$fileSize")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $secretToken")
                conn.connectTimeout = ACK_TIMEOUT_MS.toInt()
                conn.readTimeout = 60000
                conn.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(CHUNK_SIZE)
                        var received = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            output.write(buffer, 0, n)
                            received += n
                            updateFileTransferNotification(fileName, received, fileSize, false)
                            _fileTransferProgress.value = TransferProgress(fileId, fileName, received, fileSize, true)
                        }
                    }
                }
                conn.disconnect()
                _fileTransferProgress.value = TransferProgress(fileId, fileName, fileSize, fileSize, true)
                _transferCompleted.value = true
                _completedTransfer.emit(CompletedTransfer(fileName, false))
                completeFileTransferNotification(fileName)
                Log.i(TAG, "startReceiveFile 完成: ${targetFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "startReceiveFile failed", e)
                _fileTransferProgress.value = null
                scope.launch { _transferCancelledFromPc.emit("接收失败: ${e.message ?: "未知错误"}") }
            }
        }
    }

    fun showToast(msg: String) {
        val ctx = context ?: return
        mainHandler.post {
            try {
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "showToast failed", e)
            }
        }
    }

    suspend fun downloadChunk(fileId: String, partNum: Int): ByteArray {
        return try {
            val url = "${getBaseUrl()}/api/file_chunk?file_id=$fileId&part=$partNum"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $secretToken")
            conn.connectTimeout = ACK_TIMEOUT_MS.toInt()
            conn.readTimeout = 30000
            val data = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            data
        } catch (e: Exception) {
            Log.e(TAG, "downloadChunk failed", e)
            ByteArray(0)
        }
    }

    fun completeFileReceive(fileId: String) {
        Log.i(TAG, "completeFileReceive: $fileId")
        fileReceiveState.remove(fileId)
    }

    fun sendAck(fileId: String) {
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "file_complete")
                put("file_id", fileId)
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    fun sendTransferControl(ctrl: String, fileId: String = "") {
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "transfer_control")
                put("ctrl", ctrl)
                put("file_id", fileId)
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    fun cancelTransfer() {
        fileTransferCancel = true
        sendTransferControl("cancel")
        _fileTransferProgress.value = null
    }

    fun pauseTransfer() {
        sendTransferControl("pause")
        _transferPausedFromPc.value = true
    }

    fun resumeTransfer() {
        sendTransferControl("resume")
        _transferPausedFromPc.value = false
    }

    fun isTransferPaused(): Boolean = transferPausedFromPc.value

    fun resetTransferCancel() {
        fileTransferCancel = false
    }

    fun autoInstallApk(path: String) {
        try {
            val file = File(path)
            if (!file.exists()) {
                Log.w(TAG, "autoInstallApk: 文件不存在 $path")
                return
            }
            doInstallApk(file)
        } catch (e: Exception) {
            Log.e(TAG, "autoInstallApk failed", e)
        }
    }

    fun doInstallApk(file: File) {
        val ctx = context ?: return
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (Build.VERSION.SDK_INT >= 24) {
                val apkUri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "doInstallApk failed", e)
        }
    }

    fun sendAction(action: String, extra: Map<String, Any?> = emptyMap()) {
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", action)
                extra.forEach { (k, v) -> put(k, v.toString()) }
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    fun startPcFramePolling(controlMode: Boolean) {
        pcFrameControlMode = controlMode
        pcFrameJob?.cancel()
        pcFrameJob = scope.launch {
            while (isActive) {
                try {
                    val url = "${getBaseUrl()}/api/pc_frame"
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Authorization", "Bearer $secretToken")
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    val data = conn.inputStream.use { it.readBytes() }
                    conn.disconnect()
                    if (data.isNotEmpty()) {
                        _pcFrame.emit(data)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "pc_frame 轮询失败: ${e.message}")
                }
                delay(33L)
            }
        }
    }

    fun stopPcFramePolling() {
        pcFrameJob?.cancel()
        pcFrameJob = null
    }

    fun isPcFramePolling(): Boolean = pcFrameJob?.isActive == true

    fun startPcAudioPolling() {
        pcAudioJob?.cancel()
        pcAudioJob = scope.launch {
            while (isActive) {
                try {
                    val url = "${getBaseUrl()}/api/pc_audio"
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Authorization", "Bearer $secretToken")
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    val data = conn.inputStream.use { it.readBytes() }
                    conn.disconnect()
                    if (data.isNotEmpty()) {
                        playPcAudioChunk(data)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "pc_audio 轮询失败: ${e.message}")
                }
                delay(20L)
            }
        }
    }

    private fun playPcAudioChunk(data: ByteArray) {
        try {
            if (pcAudioTrack == null) {
                val minBuf = AudioTrack.getMinBufferSize(
                    pcAudioSampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                pcAudioTrack = AudioTrack(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                    AudioFormat.Builder().setSampleRate(pcAudioSampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
                    minBuf.coerceAtLeast(data.size * 2),
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                pcAudioTrack?.play()
            }
            pcAudioTrack?.write(data, 0, data.size)
        } catch (e: Exception) {
            Log.e(TAG, "playPcAudioChunk failed", e)
        }
    }

    fun stopPcAudioPolling() {
        pcAudioJob?.cancel()
        pcAudioJob = null
        try {
            pcAudioTrack?.stop()
            pcAudioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "stopPcAudioPolling release failed: ${e.message}")
        }
        pcAudioTrack = null
    }

    fun isPcAudioPolling(): Boolean = pcAudioJob?.isActive == true

    fun startPcCameraPolling() {
        pcCameraJob?.cancel()
        pcCameraJob = scope.launch {
            while (isActive) {
                try {
                    val url = "${getBaseUrl()}/api/pc_camera"
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Authorization", "Bearer $secretToken")
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    val data = conn.inputStream.use { it.readBytes() }
                    conn.disconnect()
                    if (data.isNotEmpty()) {
                        _pcCameraFrame.emit(data)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "pc_camera 轮询失败: ${e.message}")
                }
                delay(66L)
            }
        }
    }

    fun stopPcCameraPolling() {
        pcCameraJob?.cancel()
        pcCameraJob = null
    }

    fun isPcCameraPolling(): Boolean = pcCameraJob?.isActive == true

    fun handleAppListRequest() {
        scope.launch {
            try {
                val ctx = context ?: return@launch
                val pm = ctx.packageManager
                val apps = pm.getInstalledApplications(0)
                val msg = buildJsonMessage {
                    put("source", "phone")
                    putJsonObject("data") {
                        put("action", "app_list_response")
                        putJsonArray("apps") {
                            apps.filter { pm.getLaunchIntentForPackage(it.packageName) != null }.forEach { app ->
                                addJsonObject {
                                    put("package", app.packageName)
                                    put("name", pm.getApplicationLabel(app).toString())
                                }
                            }
                        }
                    }
                }
                sendRaw(msg.toString())
            } catch (e: Exception) {
                Log.e(TAG, "handleAppListRequest failed", e)
            }
        }
    }

    fun handleAppUninstallRequest(pkg: String) {
        try {
            val ctx = context ?: return
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "handleAppUninstallRequest failed", e)
        }
    }

    fun handleAppApkRequest(pkg: String) {
        scope.launch {
            try {
                val ctx = context ?: return@launch
                val pm = ctx.packageManager
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val srcFile = File(appInfo.sourceDir)
                val targetFile = File(ctx.externalCacheDir, "$pkg.apk")
                srcFile.copyTo(targetFile, overwrite = true)
                sendFile(targetFile)
            } catch (e: Exception) {
                Log.e(TAG, "handleAppApkRequest failed", e)
            }
        }
    }

    fun handleFileListRequest(path: String) {
        scope.launch {
            try {
                val dir = File(path)
                val files = dir.listFiles()?.toList() ?: emptyList()
                val msg = buildJsonMessage {
                    put("source", "phone")
                    putJsonObject("data") {
                        put("action", "file_list_response")
                        put("path", path)
                        putJsonArray("files") {
                            files.forEach { f ->
                                addJsonObject {
                                    put("name", f.name)
                                    put("is_dir", f.isDirectory)
                                    put("size", f.length())
                                    put("modified", f.lastModified())
                                }
                            }
                        }
                    }
                }
                sendRaw(msg.toString())
            } catch (e: Exception) {
                Log.e(TAG, "handleFileListRequest failed", e)
            }
        }
    }

    fun handleFileDelete(path: String, isDir: Boolean) {
        try {
            val file = File(path)
            if (isDir) file.deleteRecursively() else file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "handleFileDelete failed", e)
        }
    }

    fun handleFileRename(oldPath: String, newPath: String) {
        try {
            File(oldPath).renameTo(File(newPath))
        } catch (e: Exception) {
            Log.e(TAG, "handleFileRename failed", e)
        }
    }

    fun handleFileMkdir(path: String) {
        try {
            File(path).mkdirs()
        } catch (e: Exception) {
            Log.e(TAG, "handleFileMkdir failed", e)
        }
    }

    fun handleFileCopy(src: String, dst: String, isDir: Boolean) {
        try {
            val srcFile = File(src)
            val dstFile = File(dst)
            if (isDir) srcFile.copyRecursively(dstFile, overwrite = true)
            else srcFile.copyTo(dstFile, overwrite = true)
        } catch (e: Exception) {
            Log.e(TAG, "handleFileCopy failed", e)
        }
    }

    fun handleSendFileRequest(path: String) {
        scope.launch {
            try {
                val file = File(path)
                if (!file.exists()) {
                    Log.w(TAG, "handleSendFileRequest: 文件不存在 $path")
                    return@launch
                }
                sendFile(file)
            } catch (e: Exception) {
                Log.e(TAG, "handleSendFileRequest failed", e)
            }
        }
    }

    fun cacheMediaProjectionToken(resultCode: Int, data: Intent) {
        cachedProjectionResultCode = resultCode
        cachedProjectionData = data
        Log.i(TAG, "已缓存 MediaProjection token")
    }

    fun hasCachedProjectionToken(): Boolean = cachedProjectionData != null

    fun getCachedMediaProjection(): MediaProjection? {
        val ctx = context ?: return null
        val code = cachedProjectionResultCode ?: return null
        val data = cachedProjectionData ?: return null
        return try {
            val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            mpm?.getMediaProjection(code, data)
        } catch (e: Exception) {
            Log.e(TAG, "getCachedMediaProjection failed", e)
            null
        }
    }

    fun triggerScreenshot() {
        scope.launch {
            try {
                performBackgroundScreenshot()
            } catch (e: Exception) {
                Log.e(TAG, "triggerScreenshot failed", e)
                _screenshotResult.emit("截图失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    fun launchScreenshotActivity(ctx: Context) {
        try {
            val intent = Intent(ctx, ScreenshotActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "launchScreenshotActivity failed", e)
        }
    }

    suspend fun performBackgroundScreenshot(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        val ctx = context ?: return false
        val service = ScreenCaptureService.INSTANCE
        if (service?.isRunning != true) {
            service?.start(ctx)
            delay(1500L)
        }
        if (service?.isRunning != true) {
            Log.w("PhoneHub", "ScreenCaptureService 未启动，无法截图")
            return false
        }
        return performScreenshot(ctx)
    }

    private fun performScreenshot(context: Context): Boolean {
        var imageReader: ImageReader? = null
        var virtualDisplay: VirtualDisplay? = null
        var mediaProjection: MediaProjection? = null
        try {
            val cachedMediaProjection = getCachedMediaProjection() ?: return false
            mediaProjection = cachedMediaProjection
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            val w = metrics.widthPixels
            val h = metrics.heightPixels
            val dpi = metrics.densityDpi
            imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            virtualDisplay = cachedMediaProjection.createVirtualDisplay(
                "PhoneHubBgScreenshot", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface, null, null
            )
            val latch = java.util.concurrent.CountDownLatch(1)
            var bitmap: Bitmap? = null
            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * w
                        val bmp = Bitmap.createBitmap(rowPadding / pixelStride + w, h, Bitmap.Config.ARGB_8888)
                        buffer.rewind()
                        bmp.copyPixelsFromBuffer(buffer)
                        bitmap = Bitmap.createBitmap(bmp, 0, 0, w, h)
                    } catch (e: Exception) {
                        Log.e("PhoneHub", "后台截图 Image 处理失败", e)
                    }
                    image.close()
                    latch.countDown()
                }
            }, Handler(Looper.getMainLooper()))
            latch.await(3L, TimeUnit.SECONDS)
            if (bitmap == null) return false
            val fileName = "screenshot_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".png"
            saveBitmapToGallery(context, bitmap!!, fileName)
            val file = File(context.getExternalFilesDir(null), fileName)
            FileOutputStream(file).use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
            sendFile(file)
            Log.d("PhoneHub", "后台静默截图成功: $fileName")
            scope.launch { _screenshotResult.emit("截图已保存到手机相册: $fileName") }
            return true
        } catch (e: Exception) {
            Log.e("PhoneHub", "performBackgroundScreenshot failed", e)
            scope.launch { _screenshotResult.emit("截图失败: ${e.message ?: "未知错误"}") }
            return false
        } finally {
            try { virtualDisplay?.release() } catch (_: Exception) {}
            try { imageReader?.close() } catch (_: Exception) {}
            try { mediaProjection?.stop() } catch (_: Exception) {}
        }
    }

    fun saveBitmapToGallery(ctx: Context, bmp: Bitmap, fileName: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhoneHub")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    ctx.contentResolver.update(uri, values, null, null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveBitmapToGallery failed", e)
        }
    }

    fun openUrlOnDevice(url: String, forceVia: Boolean = false) {
        try {
            val ctx = context ?: return
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openUrlOnDevice failed", e)
        }
    }

    fun pushUrlToPc(url: String, useEdge: Boolean = false) {
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "open_url")
                put("url", url)
                put("use_edge", useEdge)
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    fun sendUrlHistorySync(history: List<Triple<String, String, Long>>) {
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "url_history_sync")
                putJsonArray("history") {
                    history.forEach { item ->
                        addJsonObject {
                            put("url", item.first)
                            put("title", item.second)
                            put("timestamp", item.third)
                        }
                    }
                }
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    fun sendPowerCommand(cmd: String, delay: Long = 0L) {
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "power")
                put("cmd", cmd)
                put("delay", delay)
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    fun reportLocation(loc: Location) {
        scope.launch {
            try {
                val msg = buildJsonMessage {
                    put("source", "phone")
                    putJsonObject("data") {
                        put("action", "location")
                        put("lat", loc.latitude)
                        put("lon", loc.longitude)
                        put("accuracy", loc.accuracy)
                        put("timestamp", System.currentTimeMillis())
                    }
                }
                sendRaw(msg.toString())
            } catch (e: Exception) {
                Log.e(TAG, "reportLocation failed", e)
            }
        }
    }

    fun uploadLocationBatch(arr: JSONArray) {
        scope.launch {
            try {
                val msg = buildJsonMessage {
                    put("source", "phone")
                    putJsonObject("data") {
                        put("action", "location_batch")
                        put("batch", arr.toString())
                    }
                }
                sendRaw(msg.toString())
            } catch (e: Exception) {
                Log.e(TAG, "uploadLocationBatch failed", e)
            }
        }
    }

    fun addLocationPoint(p: LocationPoint) {
        val list = _locationPoints.value.toMutableList()
        list.add(p)
        if (list.size > 1000) {
            list.removeAt(0)
        }
        _locationPoints.value = list
    }

    fun performScreenTouch(normX: Float, normY: Float, op: String) {
        try {
            val ctx = context ?: return
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            val x = normX * metrics.widthPixels
            val y = normY * metrics.heightPixels
            when (op) {
                "click", "down", "up" -> {
                    PhoneHubAccessibilityService.getInstance()?.clickAt(x, y)
                }
                "long_press" -> {
                    PhoneHubAccessibilityService.getInstance()?.longClickAt(x, y)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "performScreenTouch failed", e)
        }
    }

    fun reportNotification(item: NotificationItem) {
        scope.launch {
            try {
                _notifications.emit(item)
            } catch (e: Exception) {
                Log.e(TAG, "reportNotification failed", e)
            }
        }
    }

    fun loadClipboardStore() {
        try {
            val ctx = context ?: return
            val file = File(ctx.filesDir, "clipboard_store.json")
            if (!file.exists()) return
            val text = file.readText()
            val json = Json.parseToJsonElement(text) as? JsonObject ?: return
            val historyArr = json["history"] as? JsonArray
            if (historyArr != null) {
                val historyList = historyArr.mapNotNull { (it as? JsonObject)?.toClipboardItem() }
                _clipboardHistory.value = historyList
            }
            val favoritesArr = json["favorites"] as? JsonArray
            if (favoritesArr != null) {
                val favoritesList = favoritesArr.mapNotNull { (it as? JsonObject)?.toClipboardItem() }
                _clipboardFavorites.value = favoritesList
            }
            Log.i(TAG, "已加载剪贴板历史: ${_clipboardHistory.value.size} 条")
        } catch (e: Exception) {
            Log.e(TAG, "loadClipboardStore failed", e)
        }
    }

    fun saveClipboardStore() {
        try {
            val ctx = context ?: return
            val file = File(ctx.filesDir, "clipboard_store.json")
            val obj = buildJsonObject {
                putJsonArray("history") {
                    _clipboardHistory.value.forEach { add(it.toJsonObject()) }
                }
                putJsonArray("favorites") {
                    _clipboardFavorites.value.forEach { add(it.toJsonObject()) }
                }
            }
            file.writeText(obj.toString())
        } catch (e: Exception) {
            Log.e(TAG, "saveClipboardStore failed", e)
        }
    }

    fun addClipboardHistory(text: String, source: String) {
        if (text.isBlank()) return
        val item = ClipboardItem(text, source, System.currentTimeMillis(), false)
        val list = _clipboardHistory.value.toMutableList()
        list.removeAll { it.content == text }
        list.add(0, item)
        if (list.size > CLIPBOARD_HISTORY_MAX) list.removeLast()
        _clipboardHistory.value = list
        saveClipboardStore()
    }

    fun toggleFavorite(item: ClipboardItem) {
        val newItem = item.copy(isFavorite = !item.isFavorite)
        val historyList = _clipboardHistory.value.toMutableList()
        val idx = historyList.indexOfFirst { it.content == item.content && it.timestamp == item.timestamp }
        if (idx >= 0) {
            historyList[idx] = newItem
            _clipboardHistory.value = historyList
        }
        val favoritesList = _clipboardFavorites.value.toMutableList()
        if (newItem.isFavorite) {
            favoritesList.add(newItem)
        } else {
            favoritesList.removeAll { it.content == item.content && it.timestamp == item.timestamp }
        }
        _clipboardFavorites.value = favoritesList
        saveClipboardStore()
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "clipboard_favorite")
                put("content", newItem.content)
                put("favorite", newItem.isFavorite)
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    fun applySyncedFavorite(content: String, favorite: Boolean) {
        val historyList = _clipboardHistory.value.toMutableList()
        val idx = historyList.indexOfFirst { it.content == content }
        if (idx >= 0) {
            historyList[idx] = historyList[idx].copy(isFavorite = favorite)
            _clipboardHistory.value = historyList
        }
        val favoritesList = _clipboardFavorites.value.toMutableList()
        if (favorite) {
            val item = historyList.getOrNull(idx) ?: ClipboardItem(content, "synced", System.currentTimeMillis(), true)
            if (favoritesList.none { it.content == content }) {
                favoritesList.add(item)
            }
        } else {
            favoritesList.removeAll { it.content == content }
        }
        _clipboardFavorites.value = favoritesList
        saveClipboardStore()
    }

    fun searchClipboardHistory(query: String): List<ClipboardItem> {
        val q = query.lowercase(Locale.ROOT)
        return _clipboardHistory.value.filter { it.content.lowercase(Locale.ROOT).contains(q) }
    }

    fun searchClipboardFavorites(query: String): List<ClipboardItem> {
        val q = query.lowercase(Locale.ROOT)
        return _clipboardFavorites.value.filter { it.content.lowercase(Locale.ROOT).contains(q) }
    }

    fun sendClipboardHistoryToPc() {
        scope.launch {
            try {
                val msg = buildJsonMessage {
                    put("source", "phone")
                    putJsonObject("data") {
                        put("action", "clipboard_history_sync")
                        putJsonArray("history") {
                            _clipboardHistory.value.forEach { add(it.toJsonObject()) }
                        }
                    }
                }
                sendRaw(msg.toString())
            } catch (e: Exception) {
                Log.e(TAG, "sendClipboardHistoryToPc failed", e)
            }
        }
    }

    fun ClipboardItem.toJsonObject(): JsonObject = buildJsonObject {
        put("content", content)
        put("source", source)
        put("timestamp", timestamp)
        put("favorite", isFavorite)
    }

    fun JsonObject.toClipboardItem(): ClipboardItem {
        val content = this["content"]?.jsonPrimitive?.contentOrNull ?: ""
        val source = this["source"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val timestamp = this["timestamp"]?.jsonPrimitive?.longOrNull ?: 0L
        val favorite = this["favorite"]?.jsonPrimitive?.booleanOrNull ?: false
        return ClipboardItem(content, source, timestamp, favorite)
    }

    fun getReceiveDir(): File {
        val ctx = context
        val dir = if (ctx != null) {
            File(ctx.getExternalFilesDir(null), "received")
        } else {
            File(Environment.getExternalStorageDirectory(), "PhoneHub/received")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun cacheIp(ip: String) {
        val ctx = context ?: return
        ctx.getSharedPreferences(PREF_NAME, 0).edit().putString(KEY_CACHED_IP, ip).apply()
    }

    fun getCachedIp(): String? {
        val ctx = context ?: return null
        return ctx.getSharedPreferences(PREF_NAME, 0).getString(KEY_CACHED_IP, null)
    }

    suspend fun sendRaw(payload: String) {
        try {
            val clientRef = client ?: return
            val urlStr = if (_currentChannel.value == ChannelType.ADB) {
                "http://127.0.0.1:$connectPort/api/cmd"
            } else {
                "${getBaseUrl()}/api/cmd"
            }
            clientRef.post {
                url(urlStr)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendRaw failed", e)
        }
    }

    fun buildJsonMessage(block: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject {
        put("token", secretToken)
        put("source", "phone")
        block()
    }

    fun getBaseUrl(): String {
        var ip = pcIp
        if (ip == null) ip = DEFAULT_IP
        return if (_currentChannel.value == ChannelType.ADB) {
            "http://127.0.0.1:$connectPort"
        } else {
            "http://$ip:$connectPort"
        }
    }

    fun getBaseUrlPublic(): String = getBaseUrl()

    fun sendFrameToPc(frameData: ByteArray, type: String = "mirror") {
        try {
            val url = URL("${getBaseUrl()}/api/phone_frame?type=$type")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $secretToken")
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.doOutput = true
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.useCaches = false
            conn.setFixedLengthStreamingMode(frameData.size)
            conn.outputStream.use { it.write(frameData); it.flush() }
            conn.responseCode
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "sendFrameToPc failed: ${e.message}")
        }
    }

    fun sendAudioToPc(audioData: ByteArray) {
        try {
            val url = URL("${getBaseUrl()}/api/phone_audio")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $secretToken")
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.doOutput = true
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.useCaches = false
            conn.setFixedLengthStreamingMode(audioData.size)
            conn.outputStream.use { it.write(audioData); it.flush() }
            conn.responseCode
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "sendAudioToPc failed: ${e.message}")
        }
    }

    fun fetchPcDrives(callback: (List<PcDriveInfo>) -> Unit) {
        scope.launch {
            try {
                val clientRef = client ?: run { callback(emptyList()); return@launch }
                val resp = clientRef.get("${getBaseUrl()}/api/pc_drives")
                if (resp.status.value !in 200..299) {
                    callback(emptyList())
                    return@launch
                }
                val body = resp.bodyAsText()
                val drivesList = mutableListOf<PcDriveInfo>()
                try {
                    val jsonEl = Json.parseToJsonElement(body)
                    val drivesArr = jsonEl.jsonObject["drives"]?.jsonArray ?: JsonArray(emptyList())
                    for (driveElem in drivesArr) {
                        val driveObj = driveElem.jsonObject
                        val name = (driveObj["name"] as? JsonPrimitive)?.content ?: ""
                        val label = (driveObj["label"] as? JsonPrimitive)?.content ?: ""
                        val total = (driveObj["total"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
                        val used = (driveObj["used"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
                        val free = (driveObj["free"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
                        drivesList.add(PcDriveInfo(name, label, total, used, free))
                    }
                } catch (e: Exception) {
                    Log.e("PhoneHub", "fetchPcDrives parse failed", e)
                }
                callback(drivesList)
            } catch (e: Exception) {
                Log.e(TAG, "fetchPcDrives failed", e)
                callback(emptyList())
            }
        }
    }

    fun fetchPcFiles(path: String, callback: (List<PcFileInfo>, String) -> Unit) {
        scope.launch {
            try {
                val clientRef = client ?: run { callback(emptyList(), path); return@launch }
                val encodedPath = URLEncoder.encode(path, "UTF-8")
                val resp = clientRef.get("${getBaseUrl()}/api/pc_files?path=$encodedPath")
                if (resp.status.value !in 200..299) {
                    callback(emptyList(), path)
                    return@launch
                }
                val body = resp.bodyAsText()
                val filesList = mutableListOf<PcFileInfo>()
                try {
                    val jsonEl = Json.parseToJsonElement(body)
                    val filesArr = jsonEl.jsonObject["files"]?.jsonArray ?: JsonArray(emptyList())
                    for (fileElem in filesArr) {
                        val fileObj = fileElem.jsonObject
                        val name = (fileObj["name"] as? JsonPrimitive)?.content ?: ""
                        val isDir = (fileObj["is_dir"] as? JsonPrimitive)?.booleanOrNull ?: false
                        val size = (fileObj["size"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
                        val modified = (fileObj["modified"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
                        filesList.add(PcFileInfo(name, isDir, size, modified))
                    }
                } catch (e: Exception) {
                    Log.e("PhoneHub", "fetchPcFiles parse failed", e)
                }
                callback(filesList, path)
            } catch (e: Exception) {
                Log.e(TAG, "fetchPcFiles failed", e)
                callback(emptyList(), path)
            }
        }
    }

    fun disconnect() {
        userConnectedIntent = false
        lastPcHeartbeatAt = 0L
        statusJob?.cancel()
        msgPollingJob?.cancel()
        statusReportJob?.cancel()
        sendJob?.cancel()
        receiveJob?.cancel()
        transferInProgress = false
        ackTracker.clear()
        fileReceiveState.clear()
        _connectionState.value = ConnectionState.DISCONNECTED
        _currentChannel.value = ChannelType.NONE
        _connectionMessage.value = "未连接"
        _fileTransferProgress.value = null
    }

    fun runOnUiThread(block: () -> Unit) {
        mainHandler.post { block() }
    }
}
