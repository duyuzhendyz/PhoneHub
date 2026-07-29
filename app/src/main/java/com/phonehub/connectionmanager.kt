package com.phonehub

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.content.pm.ServiceInfo
import android.os.PowerManager
import android.os.StatFs
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import android.media.AudioManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.media.MediaScannerConnection
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConnectionManager {
    private const val TAG = "PhoneHub"
    private const val DEFAULT_SECRET_TOKEN = "541881452418845"
    private const val DEFAULT_PORT = 58627
    private const val CHUNK_SIZE = 524288  // 512KB，与PC端保持一致，减少HTTP请求数量
    private const val DEFAULT_PAW_URL = ""
    private const val DEFAULT_IP = "192.168.3.9"

    // 重连参数
    private const val RECONNECT_FAIL_THRESHOLD = 3
    private const val ACK_TIMEOUT_MS = 10000L

    // 剪贴板历史与收藏上限
    private const val CLIPBOARD_HISTORY_MAX = 500
    private const val CLIPBOARD_FAVORITE_MAX = 50

    private const val PREF_NAME = "phonehub_prefs"
    private const val KEY_CACHED_IP = "cached_pc_ip"
    private const val KEY_PAW_URL = "paw_url"
    private const val KEY_PAW_TOKEN = "paw_token"
    private var pawDeviceId: String? = null

    private var secretToken: String = DEFAULT_SECRET_TOKEN
    private var pawUrl: String = DEFAULT_PAW_URL

    fun getPawUrl(): String = pawUrl
    fun getSecretToken(): String = secretToken

    fun setPawConfig(url: String, token: String) {
        pawUrl = url
        secretToken = token
        val ctx = context ?: return
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_PAW_URL, url)
            .putString(KEY_PAW_TOKEN, token)
            .apply()
    }

    private fun loadPawConfig() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // 从 SharedPreferences 加载 secretToken，无缓存时使用默认值
        secretToken = prefs.getString(KEY_PAW_TOKEN, DEFAULT_SECRET_TOKEN) ?: DEFAULT_SECRET_TOKEN
        // 清理旧的 paw_token 和 cached_token 缓存
        val editor = prefs.edit()
        if (prefs.contains("paw_token")) {
            editor.remove("paw_token")
            Log.i(TAG, "已清理旧的 paw_token 缓存")
        }
        // 清理旧的 cached_token（用户之前手动输入的值可能与新默认 token 不匹配）
        val cachedToken = prefs.getString("cached_token", "")
        if (!cachedToken.isNullOrEmpty() && cachedToken != DEFAULT_SECRET_TOKEN) {
            editor.remove("cached_token")
            Log.i(TAG, "已清理旧的 cached_token 缓存: $cachedToken")
        }
        editor.apply()
    }

    private var context: Context? = null
    private var client: HttpClient? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _currentChannel = MutableStateFlow(ChannelType.NONE)
    val currentChannel: StateFlow<ChannelType> = _currentChannel

    private val _phoneMemUsage = MutableStateFlow(0f)
    val phoneMemUsage: StateFlow<Float> = _phoneMemUsage

    private val _connectionMessage = MutableStateFlow("未连接")
    val connectionMessage: StateFlow<String> = _connectionMessage

    @Volatile
    var lastConnectFailReason: String? = null
        private set

    private val _connectionLatency = MutableStateFlow(0L)
    val connectionLatency: StateFlow<Long> = _connectionLatency

    private val _receivedText = MutableSharedFlow<Pair<String, String>>(replay = 1, extraBufferCapacity = 16)
    val receivedText: SharedFlow<Pair<String, String>> = _receivedText
    // 缓存最近一次收到的文字（filename, txt），供通知点击后重新显示 dialog
    @Volatile
    var lastReceivedText: Pair<String, String>? = null
        private set

    // 电脑推送给手机的 URL（功能：推送网页历史方向标注）
    private val _receivedUrl = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val receivedUrl: SharedFlow<String> = _receivedUrl

    // 电脑端发来的 URL 历史同步数据（list of {url, direction, timestamp}）
    private val _urlHistorySync = MutableSharedFlow<List<Map<String, Any>>>(extraBufferCapacity = 4)
    val urlHistorySync: SharedFlow<List<Map<String, Any>>> = _urlHistorySync

    // 电脑端请求切换手机摄像头事件
    private val _cameraSwitchRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val cameraSwitchRequest: SharedFlow<Unit> = _cameraSwitchRequest

    private val _receivedClipboard = MutableStateFlow<String?>(null)
    val receivedClipboard: StateFlow<String?> = _receivedClipboard

    // 电脑端媒体信息
    private val _mediaInfo = MutableStateFlow("未检测到媒体播放")
    val mediaInfo: StateFlow<String> = _mediaInfo

    // 电脑端媒体封面图（Base64 解码后的字节，null 表示无封面）
    private val _mediaThumbnail = MutableStateFlow<ByteArray?>(null)
    val mediaThumbnail: StateFlow<ByteArray?> = _mediaThumbnail

    // 截图结果事件（成功/失败消息，供 UI 显示 Toast）
    private val _screenshotResult = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val screenshotResult: SharedFlow<String> = _screenshotResult

    private val _fileTransferProgress = MutableStateFlow<TransferProgress?>(null)
    val fileTransferProgress: StateFlow<TransferProgress?> = _fileTransferProgress

    // 传输真正完成标志（区别于取消/异常导致的进度清空）
    private val _transferCompleted = MutableStateFlow(false)
    val transferCompleted: StateFlow<Boolean> = _transferCompleted

    // 传输完成详情（文件名+方向），供 MainActivity 自动记录历史
    data class CompletedTransfer(val fileName: String, val sending: Boolean)
    private val _completedTransfer = MutableSharedFlow<CompletedTransfer>(extraBufferCapacity = 4)
    val completedTransfer: SharedFlow<CompletedTransfer> = _completedTransfer

    // PC 发来的传输控制事件（暂停/取消），供 UI 订阅同步按钮状态
    private val _transferPausedFromPc = MutableStateFlow(false)
    val transferPausedFromPc: StateFlow<Boolean> = _transferPausedFromPc
    private val _transferCancelledFromPc = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val transferCancelledFromPc: SharedFlow<String> = _transferCancelledFromPc

    private val _notifications = MutableSharedFlow<NotificationItem>(replay = 100, extraBufferCapacity = 32)
    val notifications: SharedFlow<NotificationItem> = _notifications

    private val _locationPoints = MutableStateFlow<List<LocationPoint>>(emptyList())
    val locationPoints: StateFlow<List<LocationPoint>> = _locationPoints

    private val _clipboardHistory = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val clipboardHistory: StateFlow<List<ClipboardItem>> = _clipboardHistory

    private val _clipboardFavorites = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val clipboardFavorites: StateFlow<List<ClipboardItem>> = _clipboardFavorites

    // save.md 功能7：电脑→手机推流帧
    private val _pcFrame = MutableSharedFlow<ByteArray>(extraBufferCapacity = 4)
    val pcFrame: SharedFlow<ByteArray> = _pcFrame
    private val _pcCursorPos = MutableSharedFlow<Pair<Float, Float>>(extraBufferCapacity = 4)
    val pcCursorPos: SharedFlow<Pair<Float, Float>> = _pcCursorPos
    private var pcFrameJob: Job? = null
    @Volatile
    var pcFrameControlMode = false

    // save.md 功能8：电脑摄像头→手机推流帧
    private val _pcCameraFrame = MutableSharedFlow<ByteArray>(extraBufferCapacity = 4)
    val pcCameraFrame: SharedFlow<ByteArray> = _pcCameraFrame
    private var pcCameraJob: Job? = null

    @Volatile
    var userConnectedIntent = false

    @Volatile
    var lastPcHeartbeatAt = 0L
        private set

    // 远程控制：记录上次触摸按下位置（归一化坐标→像素坐标后缓存）
    @Volatile
    private var _lastTouchDownX = -1f
    @Volatile
    private var _lastTouchDownY = -1f

    private var pcIp: String? = null
    private var connectPort: Int = DEFAULT_PORT

    fun getPcIp(): String? = pcIp
    private var pawPollingJob: Job? = null
    private var pawStatusReportJob: Job? = null
    private var statusJob: Job? = null
    private var msgPollingJob: Job? = null
    private var statusReportJob: Job? = null
    private var adbWatchdogJob: Job? = null
    @Volatile
    private var lastClipboardContent = ""

    // 后台截图：缓存 MediaProjection token (resultCode + Intent data)
    // Android 14+ 同一 token 只能关联一个 MediaProjection 实例，不可复用
    @Volatile
    private var cachedProjectionResultCode: Int = 0
    @Volatile
    private var cachedProjectionData: Intent? = null
    private var projectionManager: MediaProjectionManager? = null
    // 使用 WeakReference 避免持有 MediaProjection 强引用导致内存泄漏
    @Volatile
    private var activeProjectionRef: java.lang.ref.WeakReference<MediaProjection>? = null

    // 分离发送和接收 Job，避免互相 cancel 导致闪退
    private var sendJob: Job? = null
    private var receiveJob: Job? = null
    @Volatile
    private var fileTransferCancel = false
    private var receiveDir: File? = null
    private var locationStoreDir: File? = null

    private var reconnectFailCount = 0
    private var userVerifiedConnection = false
    private var lastPcCpuAt = 0L

    // 文件接收状态（断点续传）
    private val fileReceiveState = ConcurrentHashMap<String, FileReceiveState>()
    private val ackTracker = ConcurrentHashMap<String, Long>()

    // 待发送文件信息：发送 send_file_head 后等待 PC 的 file_accept 才开始上传
    private data class PendingSendInfo(
        val fileId: String,
        val fileName: String,
        val fileSize: Long,
        val file: File? = null,
        val uri: Uri? = null,
        val resolvedName: String = "",
        val deferred: CompletableDeferred<Boolean>
    )
    @Volatile
    private var pendingSend: PendingSendInfo? = null
    @Volatile
    private var transferPaused = false

    // 暂停后保留的恢复信息（继续时断点续传）
    private data class ResumeInfo(
        val fileId: String,
        val fileName: String,
        val fileSize: Long,
        val file: File? = null,
        val uri: Uri? = null,
        @Volatile var resumeOffset: Long = 0
    )
    @Volatile
    private var resumeInfo: ResumeInfo? = null
    @Volatile
    private var currentConn: HttpURLConnection? = null

    data class FileReceiveState(
        val fileId: String,
        val fileName: String,
        val fileSize: Long,
        @Volatile var received: Long = 0,
        @Volatile var partNum: Int = 0
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
        val actionIntent: android.app.PendingIntent? = null,
        val pkg: String = "",
        val tag: String = "",
        val id: Int = 0
    )

    data class LocationPoint(
        val lat: Double,
        val lon: Double,
        val timestamp: Long,
        val uploaded: Boolean = false
    )

    data class ClipboardItem(
        val content: String,
        val source: String,        // phone / pc / unknown
        val timestamp: Long,
        val favorite: Boolean = false
    )

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED
    }

    enum class ChannelType(val priority: Int) {
        NONE(0), ADB(30), WIFI(20), PAW(10);

        companion object {
            fun fromName(s: String?): ChannelType =
                when (s?.lowercase()) {
                    "adb" -> ADB
                    "wifi" -> WIFI
                    "paw" -> PAW
                    else -> NONE
                }
        }
    }

    fun init(ctx: Context) {
        context = ctx.applicationContext
        loadPawConfig()
        client = HttpClient(Android) {
            engine {
                // Android 16: 确保使用应用的网络安全配置
                connectTimeout = 10_000
                socketTimeout = 30_000
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 10000
            }
            defaultRequest {
                header("Authorization", "Bearer ${this@ConnectionManager.secretToken}")
            }
        }
        receiveDir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: ctx.filesDir, "PhoneHub")
        receiveDir?.mkdirs()
        locationStoreDir = File(ctx.getExternalFilesDir(null), "LocationCache")
        locationStoreDir?.mkdirs()
        loadClipboardStore()
        startStatusReportLoop()
        startAdbWatchdog()
        // 启动媒体信息监控（定期轮询，尽快反映播放状态变化）
        startMediaMonitoring()
        // 通知监听权限由用户在通知页手动开启，不在启动时自动检查或跳转设置页
        // 启动时自动连接（使用缓存的 IP/端口/Token，无缓存则用默认值）
        scope.launch {
            val prefs = ctx.getSharedPreferences("phonehub_prefs", Context.MODE_PRIVATE)
            val cachedIp = getCachedIp() ?: DEFAULT_IP
            val cachedPort = if (prefs.contains("cached_port")) prefs.getInt("cached_port", DEFAULT_PORT) else DEFAULT_PORT
            val cachedToken = prefs.getString("cached_token", DEFAULT_SECRET_TOKEN) ?: DEFAULT_SECRET_TOKEN
            // 优先使用 cached_token，其次使用 paw_token
            val effectiveToken = if (cachedToken != DEFAULT_SECRET_TOKEN) cachedToken else secretToken
            delay(300)  // 等待 client 初始化完成
            connect(cachedIp, cachedPort, effectiveToken)
        }
    }

    fun hasReceivedPcCpu(): Boolean {
        return userConnectedIntent && lastPcHeartbeatAt > 0L
    }

    /**
     * 检查通知监听权限是否已开启
     */
    fun isNotificationListenerEnabled(): Boolean {
        val ctx = context ?: return false
        val pkgName = ctx.packageName
        val flat = android.provider.Settings.Secure.getString(
            ctx.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        if (flat.isEmpty()) return false
        val target = android.content.ComponentName(ctx, NotificationListener::class.java).flattenToString()
        return flat.split(":").any { it == target }
    }

    /**
     * 尝试触发 NotificationListener 重新连接（不自动跳转设置页）
     * - 权限未开启：直接返回，由用户在通知页手动点击按钮开启
     * - 权限已开启但服务未连接：尝试反射自动重绑
     * - 服务已连接：请求当前所有活动通知
     */
    fun requestNotificationListenerRebind() {
        val ctx = context ?: return
        try {
            if (!isNotificationListenerEnabled()) {
                // 权限未开启：不自动跳转设置页，等用户手动开启
                return
            }
            // 权限已开启但服务可能未连接：引导用户到设置页重新开关
            if (NotificationListener.instance == null) {
                Log.i(TAG, "通知权限已开启但服务未运行，引导用户到设置页")
                NotificationListener.toggleNotificationAccess(ctx)
            }
            // 服务已连接：请求当前所有活动通知
            NotificationListener.instance?.reportAllActiveNotifications()
        } catch (e: Exception) {
            Log.e(TAG, "requestNotificationListenerRebind failed", e)
        }
    }

    private fun openNotificationSettings(ctx: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openNotificationSettings failed", e)
        }
    }

    // ============================== 连接 ==============================

    fun connect(ip: String = DEFAULT_IP, port: Int = DEFAULT_PORT, token: String = DEFAULT_SECRET_TOKEN) {
        userConnectedIntent = true
        lastConnectFailReason = null
        secretToken = token
        _connectionState.value = ConnectionState.CONNECTING
        _connectionMessage.value = "正在连接..."
        Log.i(TAG, "connect() called with ip=$ip, port=$port, adbAvailable=${isAdbAvailable()}")
        pcIp = ip
        connectPort = port
        // save.md 规则：本地缓存的电脑IP仅在直连成功时更新，失败不缓存
        // 因此此处不预先 cacheIp，等 testConnection 成功后再缓存

        scope.launch {
            if (isAdbAvailable()) {
                _connectionMessage.value = "检测到 ADB 通道，尝试通过 ADB 连接..."
                val adbSuccess = testConnection("127.0.0.1", port)
                if (adbSuccess) {
                    _connectionMessage.value = "ADB 连接成功"
                    // ADB 直连成功，缓存局域网IP（便于 ADB 丢失后降级 WiFi）
                    cacheIp(ip)
                    startChannel(ChannelType.ADB)
                } else {
                    _connectionMessage.value = "ADB 连接失败，尝试 WiFi 直连..."
                    val wifiSuccess = testConnection(ip, port)
                    if (wifiSuccess) {
                        _connectionMessage.value = "WiFi 直连成功"
                        // WiFi 直连成功才缓存 IP（save.md：仅在直连成功时更新）
                        cacheIp(ip)
                        startChannel(ChannelType.WIFI)
                    } else {
                        val reason = lastConnectFailReason ?: "未知错误"
                        _connectionMessage.value = "WiFi 连接失败: $reason"
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                }
            } else {
                val success = testConnection(ip, port)
                if (success) {
                    _connectionMessage.value = "WiFi 直连成功"
                    // WiFi 直连成功才缓存 IP
                    cacheIp(ip)
                    startChannel(ChannelType.WIFI)
                } else {
                    val reason = lastConnectFailReason ?: "未知错误"
                    _connectionMessage.value = "WiFi 连接失败: $reason"
                    // 尝试 PAW 中转
                    _connectionMessage.value = "直连失败，尝试 PAW 中转..."
                    val pawSuccess = testPawConnection()
                    if (pawSuccess) {
                        _connectionMessage.value = "PAW 中转连接成功"
                        startChannel(ChannelType.PAW)
                    } else {
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                }
            }
        }
    }

    private suspend fun testConnection(ip: String, port: Int = DEFAULT_PORT): Boolean {
        return try {
            val response = client?.get("http://$ip:$port/api/status") {
                timeout { requestTimeoutMillis = 5000 }
            }
            response?.status == HttpStatusCode.OK
        } catch (e: Exception) {
            Log.e(TAG, "Test connection failed to $ip:$port: ${e.javaClass.simpleName}: ${e.message}", e)
            lastConnectFailReason = e.message ?: e.javaClass.simpleName
            false
        }
    }

    // ============================== ADB 检测 ==============================

    fun isAdbAvailable(): Boolean {
        // 先检查 ADB server 端口 5037（PC 端检测用）
        val serverAvailable = try {
            val s = java.net.Socket()
            s.connect(java.net.InetSocketAddress("127.0.0.1", 5037), 300)
            s.close()
            true
        } catch (e: Exception) {
            false
        }
        if (serverAvailable) return true
        // 手机端：通过实际 HTTP 请求判断 ADB reverse 转发是否可用
        return try {
            val url = java.net.URL("http://127.0.0.1:$connectPort/api/status")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.setRequestProperty("Authorization", "Bearer $secretToken")
            val ok = conn.responseCode == 200
            conn.disconnect()
            ok
        } catch (e: Exception) {
            false
        }
    }

    private fun startAdbWatchdog() {
        adbWatchdogJob?.cancel()
        adbWatchdogJob = scope.launch {
            while (isActive) {
                delay(5000)
                if (!userConnectedIntent) continue
                val adb = isAdbAvailable()
                val cur = _currentChannel.value
                if (adb && cur != ChannelType.ADB) {
                    switchChannelImmediate(ChannelType.ADB)
                } else if (!adb && cur == ChannelType.ADB) {
                    downgradeFromAdb()
                }
            }
        }
    }

    private fun downgradeFromAdb() {
        scope.launch {
            val ip = pcIp ?: DEFAULT_IP
            if (testConnection(ip)) {
                switchChannelImmediate(ChannelType.WIFI)
            } else {
                // switchChannelImmediate(ChannelType.PAW)  // 【禁止删除】PAW 降级
            }
        }
    }

    // ============================== 通道管理 ==============================

    private fun startChannel(channel: ChannelType) {
        // 连接已通过 testConnection 验证成功，立即置为 CONNECTED 并设置对应通道
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
            ChannelType.PAW -> {
                _currentChannel.value = ChannelType.PAW
                _connectionState.value = ConnectionState.CONNECTED
                _connectionMessage.value = "已连接 - PAW 中转"
                startPawPolling()
            }
            else -> {}
        }
        // 连接成功后发送剪贴板历史
        scope.launch {
            delay(500)
            sendClipboardHistoryToPc()
        }
    }

    /**
     * 立即切换通道（降级用）。升级请走 watchdog 累计确认。
     */
    private fun switchChannelImmediate(target: ChannelType) {
        if (sendJob?.isActive == true || receiveJob?.isActive == true) {
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

    private fun startStatusPolling(channel: ChannelType) {
        statusJob?.cancel()
        msgPollingJob?.cancel()
        statusJob = scope.launch {
            _currentChannel.value = channel
            var failCount = 0
            while (isActive) {
                try {
                    val ip = pcIp ?: DEFAULT_IP
                    val baseUrl = if (channel == ChannelType.ADB) "http://127.0.0.1:$connectPort" else "http://$ip:$connectPort"
                    // 合并轮询：一次请求同时获取状态和消息，减轻网络负担
                    val t0 = System.currentTimeMillis()
                    val response = client?.get("$baseUrl/api/poll") {
                        timeout { requestTimeoutMillis = 8000 }
                    }
                    val rtt = System.currentTimeMillis() - t0
                    if (response?.status == HttpStatusCode.OK) {
                        val body = response.bodyAsText()
                        val json = Json.parseToJsonElement(body).jsonObject
                        // 处理状态信息
                        val statusInfo = json["status_info"]?.jsonObject
                        if (statusInfo != null) {
                            lastPcHeartbeatAt = System.currentTimeMillis()
                            _connectionState.value = ConnectionState.CONNECTED
                            _connectionLatency.value = rtt
                            _connectionMessage.value = "已连接 - ${channelName(channel)} (${rtt}ms)"
                            sendStatusReport()
                            reconnectFailCount = 0
                        }
                        // 处理消息（兼容旧版 msg 单条和新版 msgs 数组）
                        val msgsArray = json["msgs"]?.jsonArray
                        if (msgsArray != null && msgsArray.isNotEmpty()) {
                            for (m in msgsArray) {
                                val msgObj = m.jsonObject
                                if (msgObj["activate"]?.jsonPrimitive?.contentOrNull != "ping") {
                                    handlePcMessage(msgObj)
                                }
                            }
                            // 有消息时立即再次轮询，快速处理堆积
                            continue
                        } else {
                            // 兼容旧版单条 msg
                            val msg = json["msg"]?.jsonObject
                            if (msg != null && msg["activate"]?.jsonPrimitive?.contentOrNull != "ping") {
                                handlePcMessage(msg)
                                continue
                            }
                        }
                        failCount = 0
                    } else {
                        failCount++
                        Log.w(TAG, "轮询失败 HTTP ${response?.status}, failCount=$failCount")
                    }
                } catch (e: Exception) {
                    failCount++
                    Log.w(TAG, "轮询异常: ${e.message}, failCount=$failCount")
                }
                // 锁屏/Doze 模式下网络受限，失败时退避等待再重试，而不是直接退出
                if (failCount >= 5) {
                    Log.w(TAG, "连续失败 $failCount 次，退避 5 秒后重试")
                    failCount = 0
                    delay(5000)
                } else if (failCount > 0) {
                    delay(1000L * failCount)  // 递增退避: 1s, 2s, 3s, 4s, 5s
                } else {
                    delay(500)
                }
            }
        }
    }

    private fun startMsgPolling(channel: ChannelType) {
        // 已合并到 startStatusPolling 的 /api/poll 轮询中，不再单独启动
        msgPollingJob?.cancel()
    }

    private suspend fun handlePollFailure(channel: ChannelType, scope: CoroutineScope) {
        reconnectFailCount++
        if (reconnectFailCount >= RECONNECT_FAIL_THRESHOLD) {
            reconnectFailCount = 0
            when (channel) {
                ChannelType.ADB -> downgradeFromAdb()
                ChannelType.WIFI -> {
                    _connectionMessage.value = "WiFi 重连失败，尝试 PAW 中转..."
                    startPawPolling()
                }
                else -> {}
            }
        } else {
            _connectionMessage.value = "通道 $channel 失败，重试 $reconnectFailCount/$RECONNECT_FAIL_THRESHOLD..."
            delay(2000)
            // 重新启动当前通道轮询
            if (scope.isActive) startStatusPolling(channel)
        }
    }

    private fun handlePcMessage(msg: JsonObject) {
        val data = msg["data"]?.jsonObject ?: return
        val action = data["action"]?.jsonPrimitive?.contentOrNull ?: return
        val source = msg["source"]?.jsonPrimitive?.contentOrNull ?: ""

        when (action) {
            "clipboard" -> {
                if (source == "phone") {
                    Log.i(TAG, "忽略本机回环剪贴板")
                    return
                }
                val txt = data["txt"]?.jsonPrimitive?.contentOrNull ?: ""
                if (txt.isNotEmpty()) {
                    // 防止电脑旧内容覆盖手机新复制的内容：
                    // 读取当前手机剪贴板，如果与 lastClipboardContent 不同，说明用户在手机上复制了新内容
                    val currentPhoneClip = try {
                        val cm = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                    } catch (e: Exception) { null }
                    if (currentPhoneClip != null && currentPhoneClip != lastClipboardContent) {
                        // 用户在手机上复制了新内容，不覆盖；同步追踪值避免后续推送全部被拒
                        Log.d(TAG, "手机剪贴板有新内容，忽略电脑推送: lastKnown=$lastClipboardContent, current=$currentPhoneClip")
                        lastClipboardContent = currentPhoneClip
                        return
                    }
                    if (txt != lastClipboardContent) {
                        setClipboardContent(txt)
                        _receivedClipboard.value = txt
                        addClipboardHistory(txt, "pc")
                    }
                }
            }
            "clipboard_favorite" -> {
                // save.md 功能23：电脑端收藏变更同步到手机
                val content = data["content"]?.jsonPrimitive?.contentOrNull ?: ""
                val favorite = data["favorite"]?.jsonPrimitive?.booleanOrNull ?: false
                if (content.isNotEmpty()) {
                    applySyncedFavorite(content, favorite)
                }
            }
            "txt" -> {
                val txt = data["txt"]?.jsonPrimitive?.contentOrNull ?: ""
                val filename = data["filename"]?.jsonPrimitive?.contentOrNull ?: ""
                if (txt.isNotEmpty()) {
                    lastReceivedText = Pair(filename, txt)
                    scope.launch { _receivedText.emit(Pair(filename, txt)) }
                    // Task 12: 如果应用不在前台，发送系统通知提示用户
                    val ctx = context
                    if (ctx != null && !isAppInForeground(ctx)) {
                        showTextReceivedNotification(ctx, filename, txt)
                    }
                }
            }
            "cmd" -> {
                val cmd = data["cmd"]?.jsonPrimitive?.contentOrNull ?: ""
                handleCommand(cmd, data)
            }
            "send_file_head" -> {
                val fileName = data["file_name"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                val fileSize = data["file_size"]?.jsonPrimitive?.longOrNull ?: 0L
                val fileId = data["file_id"]?.jsonPrimitive?.contentOrNull ?: ""
                Log.i(TAG, "收到send_file_head: name=$fileName, size=$fileSize, id=$fileId, channel=${_currentChannel.value}")
                // 自动开始接收（不弹常驻通知），双端界面立即更新
                startReceiveFile(fileId, fileName, fileSize)
            }
            "file_complete" -> {
                val fileId = data["file_id"]?.jsonPrimitive?.contentOrNull ?: ""
                ackTracker.remove(fileId)
                completeFileReceive(fileId)
            }
            "app_list_request" -> {
                handleAppListRequest()
            }
            "file_list_request" -> {
                val path = data["path"]?.jsonPrimitive?.contentOrNull ?: "/"
                handleFileListRequest(path)
            }
            "file_delete" -> {
                val path = data["path"]?.jsonPrimitive?.contentOrNull ?: return
                val isDir = data["is_dir"]?.jsonPrimitive?.booleanOrNull ?: false
                handleFileDelete(path, isDir)
            }
            "file_rename" -> {
                val oldPath = data["old_path"]?.jsonPrimitive?.contentOrNull ?: return
                val newPath = data["new_path"]?.jsonPrimitive?.contentOrNull ?: return
                handleFileRename(oldPath, newPath)
            }
            "file_mkdir" -> {
                val path = data["path"]?.jsonPrimitive?.contentOrNull ?: return
                handleFileMkdir(path)
            }
            "file_copy" -> {
                val src = data["src"]?.jsonPrimitive?.contentOrNull ?: return
                val dst = data["dst"]?.jsonPrimitive?.contentOrNull ?: return
                val isDir = data["is_dir"]?.jsonPrimitive?.booleanOrNull ?: false
                handleFileCopy(src, dst, isDir)
            }
            "send_file_request" -> {
                val path = data["path"]?.jsonPrimitive?.contentOrNull ?: return
                handleSendFileRequest(path)
            }
            "screenshot_request" -> {
                triggerScreenshot()
            }
            "app_uninstall_request" -> {
                val pkg = data["package"]?.jsonPrimitive?.contentOrNull ?: return
                handleAppUninstallRequest(pkg)
            }
            "app_apk_request" -> {
                val pkg = data["package"]?.jsonPrimitive?.contentOrNull ?: return
                handleAppApkRequest(pkg)
            }
            "open_url" -> {
                val url = data["url"]?.jsonPrimitive?.contentOrNull ?: ""
                val openInVia = data["open_in_via"]?.jsonPrimitive?.booleanOrNull ?: false
                // Task 4.3：收到电脑推送的 URL 时，发射到 receivedUrl 流，供 UI 加入历史记录
                if (url.isNotEmpty()) {
                    scope.launch { _receivedUrl.emit(url) }
                }
                openUrlOnDevice(url, openInVia)
            }
            "url_history_sync" -> {
                // 电脑端发来 URL 历史用于同步
                val historyArr = data["history"]?.jsonArray
                if (historyArr != null) {
                    val historyList = mutableListOf<Map<String, Any>>()
                    for (item in historyArr) {
                        val obj = item.jsonObject
                        val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: ""
                        val direction = obj["direction"]?.jsonPrimitive?.contentOrNull ?: ""
                        val timestamp = obj["timestamp"]?.jsonPrimitive?.longOrNull ?: 0L
                        if (url.isNotEmpty()) {
                            historyList.add(mapOf("url" to url, "direction" to direction, "timestamp" to timestamp))
                        }
                    }
                    if (historyList.isNotEmpty()) {
                        scope.launch { _urlHistorySync.emit(historyList) }
                    }
                }
            }
            "get_active_notifications" -> {
                // 功能10：电脑端请求获取手机当前所有活动通知
                NotificationListener.instance?.reportAllActiveNotifications()
            }
            "request_notif_permission" -> {
                // 不再自动处理：通知权限由用户在手机端通知页手动点击按钮开启
                Log.i(TAG, "收到 request_notif_permission，忽略（需用户手动开启）")
            }
            "cancel_notification" -> {
                // 功能10：电脑端删除手机上的通知
                val key = data["key"]?.jsonPrimitive?.contentOrNull ?: ""
                val pkg = data["pkg"]?.jsonPrimitive?.contentOrNull ?: ""
                val tag = data["tag"]?.jsonPrimitive?.contentOrNull ?: ""
                val id = data["id"]?.jsonPrimitive?.intOrNull ?: 0
                if (key.isNotEmpty()) {
                    NotificationListener.instance?.cancelNotificationByKey(key)
                } else if (pkg.isNotEmpty()) {
                    // 回退：用 pkg|tag|id 组合 key
                    NotificationListener.instance?.cancelNotificationByKey("$pkg|$tag|$id")
                }
            }
            "screenshot_saved" -> {
                val msg = data["message"]?.jsonPrimitive?.contentOrNull ?: "截图已保存到电脑"
                scope.launch { _screenshotResult.emit(msg) }
            }
            "camera_switch" -> {
                // 电脑端请求切换手机前后摄像头
                scope.launch { _cameraSwitchRequest.emit(Unit) }
            }
            "screen_touch" -> {
                // 电脑端远程控制手机屏幕（归一化坐标）
                val x = data["x"]?.jsonPrimitive?.floatOrNull
                if (x == null) {
                    LogUtil.connE("screen_touch 缺少 x 坐标，跳过处理")
                } else {
                    val y = data["y"]?.jsonPrimitive?.floatOrNull
                    if (y == null) {
                        LogUtil.connE("screen_touch 缺少 y 坐标，跳过处理")
                    } else {
                        val op = data["op"]?.jsonPrimitive?.contentOrNull ?: "click"
                        LogUtil.connI("收到screen_touch命令: x=$x, y=$y, op=$op, 通道=$currentChannel")
                        performScreenTouch(x, y, op)
                    }
                }
            }
            "media_info" -> {
                val title = data["title"]?.jsonPrimitive?.contentOrNull ?: ""
                val artist = data["artist"]?.jsonPrimitive?.contentOrNull ?: ""
                val thumbnailB64 = data["thumbnail"]?.jsonPrimitive?.contentOrNull ?: ""
                _mediaInfo.value = if (artist.isNotEmpty()) "$title - $artist" else title.ifEmpty { "未检测到媒体播放" }
                // 解析封面图 Base64
                if (thumbnailB64.isNotEmpty()) {
                    try {
                        _mediaThumbnail.value = android.util.Base64.decode(thumbnailB64, android.util.Base64.DEFAULT)
                    } catch (e: Exception) {
                        _mediaThumbnail.value = null
                    }
                } else {
                    _mediaThumbnail.value = null
                }
            }
            "install_apk" -> {
                // 电脑端发送APK安装命令，手机自动安装
                val path = data["path"]?.jsonPrimitive?.contentOrNull ?: ""
                if (path.isNotEmpty()) {
                    autoInstallApk(path)
                }
            }
            "notification_action" -> {
                // 执行通知功能按钮（通过PendingIntent发送）
                val pkg = data["package"]?.jsonPrimitive?.contentOrNull ?: return
                val actionTitle = data["action_title"]?.jsonPrimitive?.contentOrNull ?: ""
                // 查找缓存的通知actions，找到匹配的PendingIntent并send
                val notif = _notifications.replayCache.findLast {
                    it.packageName == pkg && it.actions.any { a -> a.title == actionTitle }
                }
                val action = notif?.actions?.find { it.title == actionTitle }
                if (action?.actionIntent != null) {
                    try {
                        action.actionIntent.send()
                        Log.i(TAG, "通知快捷操作已执行: pkg=$pkg action=$actionTitle")
                    } catch (e: Exception) {
                        Log.e(TAG, "notification action send failed", e)
                        // 回退：打开应用
                        val launchIntent = context?.packageManager?.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context?.startActivity(launchIntent)
                        }
                    }
                } else {
                    Log.w(TAG, "未找到通知快捷操作: pkg=$pkg action=$actionTitle, cache size=${_notifications.replayCache.size}")
                    // 回退到打开应用
                    val launchIntent = context?.packageManager?.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context?.startActivity(launchIntent)
                    }
                }
            }
            "file_accept" -> {
                // PC 确认接收文件，触发待发送文件实际上传
                val fileId = data["file_id"]?.jsonPrimitive?.contentOrNull ?: ""
                val resolvedName = data["resolved_name"]?.jsonPrimitive?.contentOrNull ?: ""
                Log.i(TAG, "收到 file_accept: fileId=$fileId, resolvedName=$resolvedName")
                val pending = pendingSend
                if (pending != null && pending.fileId == fileId) {
                    pending.deferred.complete(true)
                } else {
                    Log.w(TAG, "file_accept: 没有匹配的 pendingSend (fileId=$fileId)")
                }
            }
            "file_reject" -> {
                // PC 拒绝接收文件（重名跳过等），取消待发送
                val fileId = data["file_id"]?.jsonPrimitive?.contentOrNull ?: ""
                val reason = data["reason"]?.jsonPrimitive?.contentOrNull ?: ""
                Log.i(TAG, "收到 file_reject: fileId=$fileId, reason=$reason")
                val pending = pendingSend
                if (pending != null && pending.fileId == fileId) {
                    pending.deferred.complete(false)
                }
                _fileTransferProgress.value = null
            }
            "transfer_control" -> {
                // PC 发来的传输控制（pause/resume/cancel）
                val ctrl = data["ctrl"]?.jsonPrimitive?.contentOrNull ?: ""
                val fileId = data["file_id"]?.jsonPrimitive?.contentOrNull ?: ""
                Log.i(TAG, "收到 transfer_control: ctrl=$ctrl, fileId=$fileId")
                when (ctrl) {
                    "pause" -> {
                        fileTransferCancel = true
                        transferPaused = true
                        try { currentConn?.disconnect() } catch (e: Exception) {}
                        _transferPausedFromPc.value = true
                    }
                    "resume" -> {
                        transferPaused = false
                        fileTransferCancel = false
                        // 断点续传：用 resumeInfo 中保存的信息和 offset 继续传输
                        val info = resumeInfo
                        if (info != null) {
                            Log.i(TAG, "transfer_control resume: 断点续传 offset=${info.resumeOffset}")
                            scope.launch {
                                when {
                                    info.file != null -> sendFileWifi(info.fileId, info.file, info.fileSize)
                                    info.uri != null -> sendFileWifiFromUri(info.fileId, info.uri, info.fileName, info.fileSize)
                                    else -> {
                                        // PC→手机方向：重新触发下载（会从 .progress 读取 offset）
                                        startReceiveFile(info.fileId, info.fileName, info.fileSize)
                                    }
                                }
                            }
                        } else {
                            Log.w(TAG, "transfer_control resume: 没有 resumeInfo，无法恢复")
                        }
                        _transferPausedFromPc.value = false
                    }
                    "cancel" -> {
                        fileTransferCancel = true
                        transferPaused = false
                        _transferPausedFromPc.value = false
                        try { currentConn?.disconnect() } catch (e: Exception) {}
                        sendJob?.cancel()
                        receiveJob?.cancel()
                        resumeInfo = null
                        pendingSend = null
                        _fileTransferProgress.value = null
                        cancelFileTransferNotification()
                        scope.launch { _transferCancelledFromPc.emit(fileId) }
                    }
                }
            }
        }
    }

    private fun startPawPolling() {
        pawPollingJob?.cancel()
        statusJob?.cancel()
        _currentChannel.value = ChannelType.PAW
        
        // PAW 通道需要独立的状态上报任务（替代 ADB/WiFi 的轮询机制）
        startPawStatusReport()
        
        // 注册 PAW 设备
        scope.launch {
            if (pawDeviceId == null) {
                try {
                    val phoneId = android.provider.Settings.Secure.getString(context?.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "phone_${System.currentTimeMillis()}"
                    val conn = URL("$pawUrl/api/register").openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Authorization", "Bearer $secretToken")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    conn.outputStream.use { os ->
                        os.write("""{"device_id":"$phoneId","type":"phone","paired_id":""}""".toByteArray(Charsets.UTF_8))
                    }
                    if (conn.responseCode == 200) {
                        val resp = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                        val json = Json.parseToJsonElement(resp).jsonObject
                        pawDeviceId = json["device_id"]?.jsonPrimitive?.contentOrNull ?: phoneId
                        Log.i(TAG, "PAW registered: ${pawDeviceId}")
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.e(TAG, "PAW register failed: ${e.message}", e)
                    pawDeviceId = "phone_${System.currentTimeMillis()}"
                }
            }
        }
        pawPollingJob = scope.launch {
            while (isActive) {
                try {
                    val url = "$pawUrl/api/get_msg?device_id=${pawDeviceId}"
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Authorization", "Bearer $secretToken")
                    conn.readTimeout = 35000
                    conn.connectTimeout = 10000

                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val lineStr = line ?: continue
                        if (lineStr.startsWith("data: ")) {
                            try {
                                val jsonStr = lineStr.substring(6)
                                if (jsonStr.contains("\"activate\":\"ping\"")) continue
                                val msg = Json.parseToJsonElement(jsonStr).jsonObject
                                handlePawMessage(msg)
                            } catch (e: Exception) {
                                Log.e(TAG, "Parse PAW message failed", e)
                            }
                        } else if (lineStr.startsWith(": ")) {
                            continue  // SSE 心跳
                        }
                    }
                    reader.close()
                    conn.disconnect()
                    _connectionState.value = ConnectionState.CONNECTED
                    _connectionMessage.value = "已连接 - PAW 中转"
                    userVerifiedConnection = true
                    lastPcCpuAt = System.currentTimeMillis()
                    reconnectFailCount = 0
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    /**
     * PAW 通道状态上报：每5秒向PC发送一次手机状态
     */
    private fun startPawStatusReport() {
        pawStatusReportJob?.cancel()
        pawStatusReportJob = scope.launch {
            while (isActive) {
                try {
                    delay(5000)  // 每5秒上报一次
                    sendPawStatusReport()
                } catch (e: Exception) {
                    Log.e(TAG, "PAW status report failed", e)
                }
            }
        }
    }

    /**
     * 通过 PAW 发送手机状态到 PC
     */
    private suspend fun sendPawStatusReport() {
        val ctx = context ?: return
        try {
            val battery = getBatteryStatus(ctx)
            val temp = getBatteryTemperature(ctx)
            val net = getNetworkType(ctx)
            val storage = getStorageInfo()
            val mem = getMemUsage()
            val cpu = getPhoneCpuUsage()

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
                    put("device_model", android.os.Build.MODEL)
                    put("android_version", android.os.Build.VERSION.RELEASE)
                        put("volume", getCurrentMusicVolume())
                }
            }

            // 通过 PAW 发送
            val conn = URL("$pawUrl/api/send").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $secretToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { os ->
                os.write(msg.toString().toByteArray(Charsets.UTF_8))
            }
            val responseCode = conn.responseCode
            conn.disconnect()

            if (responseCode == 200) {
                lastPcHeartbeatAt = System.currentTimeMillis()
                lastPcCpuAt = System.currentTimeMillis()
                _phoneMemUsage.value = mem
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendPawStatusReport failed", e)
        }
    }

    private fun handlePawMessage(msg: JsonObject) {
        // PAW 通道消息通过 handlePcMessage 处理
        handlePcMessage(msg)
    }

    private suspend fun testPawConnection(): Boolean {
        return try {
            val conn = URL("$pawUrl/api/status").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $secretToken")
            conn.readTimeout = 5000
            conn.connectTimeout = 5000
            val ok = conn.responseCode == 200
            conn.disconnect()
            ok
        } catch (e: Exception) {
            Log.e(TAG, "PAW connection test failed: ${e.message}", e)
            false
        }
    }

    private fun channelName(c: ChannelType): String = when (c) {
        ChannelType.WIFI -> "WiFi 直连"
        ChannelType.PAW -> "PAW 中转"
        ChannelType.ADB -> "USB 数据线"
        ChannelType.NONE -> "无"
    }

    private fun handleCommand(cmd: String, data: JsonObject) {
        when (cmd) {
            "media_play_pause", "media_prev", "media_next",
            "vol_up", "vol_down", "vol_mute" -> {
                sendMediaKey(cmd)
            }
            "set_volume" -> {
                val vol = data["volume"]?.jsonPrimitive?.intOrNull ?: return
                setVolume(vol)
            }
            "back" -> {
                PhoneHubAccessibilityService.instance?.performBack()
            }
            "home" -> {
                PhoneHubAccessibilityService.instance?.performHome()
            }
            "recents" -> {
                PhoneHubAccessibilityService.instance?.performRecents()
            }
            "open_notifications_panel" -> {
                PhoneHubAccessibilityService.instance?.openNotifications()
            }
            "control_center" -> {
                PhoneHubAccessibilityService.instance?.openQuickSettings()
            }
            "lock" -> {
                PhoneHubAccessibilityService.instance?.performGlobalLock()
            }
            "screenshot" -> triggerScreenshot()
            "get_volume" -> sendCurrentVolume()
            "key" -> {
                val key = data["key"]?.jsonPrimitive?.contentOrNull ?: return
                val mods = data["mods"]?.jsonArray?.map { it.jsonPrimitive.contentOrNull ?: "" } ?: emptyList()
                PhoneHubAccessibilityService.instance?.performKeyInput(key, mods)
            }
            "camera_start" -> {
                // 摄像头通过Intent启动系统相机
                val intent = Intent("android.media.action.STILL_IMAGE_CAMERA")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context?.startActivity(intent)
            }
            "camera_stop" -> {
                // 无操作，系统相机由用户关闭
            }
            "screen_click" -> {
                val x = data["x"]?.jsonPrimitive?.floatOrNull ?: return
                val y = data["y"]?.jsonPrimitive?.floatOrNull ?: return
                val op = data["op"]?.jsonPrimitive?.contentOrNull ?: "click"
                performScreenTouch(x, y, op)
            }
            "open_app" -> {
                val pkg = data["package"]?.jsonPrimitive?.contentOrNull ?: return
                val launchIntent = context?.packageManager?.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context?.startActivity(launchIntent)
                }
            }
            "notification_delete" -> {
                // save.md 功能10：电脑端可删除手机上的通知
                val key = data["key"]?.jsonPrimitive?.contentOrNull ?: ""
                if (key.isNotEmpty()) {
                    try {
                        NotificationListener.instance?.cancelNotification(key)
                    } catch (e: Exception) {
                        Log.e(TAG, "cancelNotification failed", e)
                    }
                } else {
                    // 兼容：无 key 时取消所有通知
                    try {
                        NotificationListener.instance?.cancelAllNotifications()
                    } catch (e: Exception) {
                        Log.e(TAG, "cancelAllNotifications failed", e)
                    }
                }
            }
            "never_sleep" -> {
                val enabled = data["enabled"]?.jsonPrimitive?.booleanOrNull ?: false
                if (enabled) {
                    @Suppress("DEPRECATION")
                    context?.let {
                        val pm = it.getSystemService(Context.POWER_SERVICE) as? PowerManager
                        // 使用 FULL_WAKE_LOCK 替代已弃用的 SCREEN_BRIGHT_WAKE_LOCK
                        pm?.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                            "PhoneHub::NeverSleep"
                        )?.acquire()
                    }
                }
            }
        }
    }

    // ============================== Task 12: 文字接收通知 ==============================

    /**
     * 检查应用是否在前台
     */
    private fun isAppInForeground(ctx: Context): Boolean {
        return try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val runningAppProcesses = am?.runningAppProcesses ?: return false
            val packageName = ctx.packageName
            // runningAppProcesses 在 Android 5.0+ 仅返回自己的进程，但足以判断前台状态
            runningAppProcesses.any {
                it.processName == packageName &&
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 发送系统通知提示用户收到文字（应用不在前台时调用）
     * 点击通知打开 MainActivity
     */
    private fun showTextReceivedNotification(ctx: Context, filename: String, txt: String) {
        try {
            val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // 创建通知渠道
            val channel = NotificationChannel(
                "phonehub_text",
                "文字消息",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "接收电脑端发送的文字消息"
            }
            mgr.createNotificationChannel(channel)
            // 点击通知打开 MainActivity，并通过 extra 标记触发显示最近一次收到的文字
            val intent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("show_text_dialog", true)
            }
            val pi = PendingIntent.getActivity(
                ctx, System.currentTimeMillis().toInt(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            // 复制按钮
            val copyIntent = Intent(ctx, TextNotificationReceiver::class.java).apply {
                action = TextNotificationReceiver.ACTION_COPY
                putExtra(TextNotificationReceiver.EXTRA_TEXT, txt)
            }
            val copyPi = PendingIntent.getBroadcast(
                ctx, System.currentTimeMillis().toInt() + 1, copyIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            // 保存按钮
            val saveIntent = Intent(ctx, TextNotificationReceiver::class.java).apply {
                action = TextNotificationReceiver.ACTION_SAVE
                putExtra(TextNotificationReceiver.EXTRA_TEXT, txt)
            }
            val savePi = PendingIntent.getBroadcast(
                ctx, System.currentTimeMillis().toInt() + 2, saveIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            // 通知内容截断预览
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

    // ============================== Task: 文件接收通知（PC→手机） ==============================

    private const val FILE_TRANSFER_CHANNEL_ID = "phonehub_file_transfer"
    private const val FILE_TRANSFER_NOTIF_ID = 88881

    // 暂存待下载的文件信息（用户点击"开始下载"后用于触发下载）
    private data class PendingFileTransfer(
        val fileId: String,
        val fileName: String,
        val fileSize: Long
    )
    @Volatile
    private var pendingFileTransfer: PendingFileTransfer? = null

    // 通知进度更新节流（避免高频更新通知导致卡顿）
    @Volatile
    private var lastNotifUpdateMs: Long = 0L

    /**
     * 显示文件接收通知（不自动下载，等待用户点击"开始下载"）
     * - 通知为 ongoing，切换到其他应用时持续显示
     * - 通知主体点击 → 打开 MainActivity 并跳到文件传输页
     * - 通知"开始下载"按钮 → 触发 FileTransferReceiver 开始下载
     * - 通知"取消"按钮 → 取消传输并移除通知
     */
    fun showFileReceiveNotification(fileId: String, fileName: String, fileSize: Long) {
        val ctx = context ?: run {
            Log.w(TAG, "showFileReceiveNotification: context 为 null，跳过")
            return
        }
        // 暂存文件信息，供点击"开始下载"时使用
        pendingFileTransfer = PendingFileTransfer(fileId, fileName, fileSize)
        try {
            val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // 创建通知渠道
            val channel = NotificationChannel(
                FILE_TRANSFER_CHANNEL_ID,
                "文件接收",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "接收电脑端发送的文件"
            }
            mgr.createNotificationChannel(channel)

            // 点击通知主体 → 打开 MainActivity 并跳到文件传输页
            val openIntent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("show_file_transfer", true)
            }
            val openPi = PendingIntent.getActivity(
                ctx, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // "开始下载"按钮 → FileTransferReceiver
            val startIntent = Intent(ctx, FileTransferReceiver::class.java).apply {
                action = FileTransferReceiver.ACTION_START_DOWNLOAD
                putExtra(FileTransferReceiver.EXTRA_FILE_ID, fileId)
                putExtra(FileTransferReceiver.EXTRA_FILE_NAME, fileName)
                putExtra(FileTransferReceiver.EXTRA_FILE_SIZE, fileSize)
            }
            val startPi = PendingIntent.getBroadcast(
                ctx, 1, startIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // "取消"按钮 → FileTransferReceiver
            val cancelIntent = Intent(ctx, FileTransferReceiver::class.java).apply {
                action = FileTransferReceiver.ACTION_CANCEL_DOWNLOAD
                putExtra(FileTransferReceiver.EXTRA_FILE_ID, fileId)
            }
            val cancelPi = PendingIntent.getBroadcast(
                ctx, 2, cancelIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val sizeText = formatFileSize(fileSize)
            val notification = NotificationCompat.Builder(ctx, FILE_TRANSFER_CHANNEL_ID)
                .setContentTitle("收到文件: $fileName")
                .setContentText("大小: $sizeText — 点击「开始下载」接收")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)  // 持续显示，不可滑动清除
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

    /**
     * 用户在通知中点击"开始下载"后触发（由 FileTransferReceiver 调用）
     */
    fun startFileDownloadFromNotification(fileId: String, fileName: String, fileSize: Long) {
        Log.i(TAG, "用户点击通知开始下载: $fileName")
        showToast("开始下载: $fileName")
        startReceiveFile(fileId, fileName, fileSize)
    }

    /**
     * 更新通知为下载中（带进度条）
     * 节流：间隔 ≥ 400ms 才更新一次，避免拖慢传输
     */
    fun updateFileTransferNotification(fileName: String, received: Long, total: Long, paused: Boolean = false) {
        val now = System.currentTimeMillis()
        if (now - lastNotifUpdateMs < 400 && !paused) return
        lastNotifUpdateMs = now
        val ctx = context ?: return
        try {
            val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val pct = if (total > 0) ((received * 100) / total).toInt() else 0
            val sizeText = "${formatFileSize(received)} / ${formatFileSize(total)}"

            val builder = NotificationCompat.Builder(ctx, FILE_TRANSFER_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, pct, false)
                .setContentTitle(if (paused) "已暂停: $fileName" else "下载中: $fileName")
                .setContentText(if (paused) sizeText else "$sizeText ($pct%)")

            // 下载中提供"取消"按钮
            val pendingFileId = pendingFileTransfer?.fileId ?: ""
            val cancelIntent = Intent(ctx, FileTransferReceiver::class.java).apply {
                action = FileTransferReceiver.ACTION_CANCEL_DOWNLOAD
                putExtra(FileTransferReceiver.EXTRA_FILE_ID, pendingFileId)
            }
            val cancelPi = PendingIntent.getBroadcast(
                ctx, 2, cancelIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancelPi)

            // 点击主体仍可打开文件传输页
            val openIntent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("show_file_transfer", true)
            }
            val openPi = PendingIntent.getActivity(
                ctx, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.setContentIntent(openPi)

            mgr.notify(FILE_TRANSFER_NOTIF_ID, builder.build())
        } catch (e: Exception) {
            Log.w(TAG, "更新文件传输通知失败: ${e.message}")
        }
    }

    /**
     * 下载完成：通知变为可清除，显示"完成"
     */
    fun completeFileTransferNotification(fileName: String) {
        val ctx = context ?: return
        try {
            val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val openIntent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("show_file_transfer", true)
            }
            val openPi = PendingIntent.getActivity(
                ctx, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(ctx, FILE_TRANSFER_CHANNEL_ID)
                .setContentTitle("下载完成: $fileName")
                .setContentText("文件已保存到接收目录")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)  // 点击后自动消失
                .setOngoing(false)    // 不再持续显示
                .setOnlyAlertOnce(true)
                .setContentIntent(openPi)
                .build()
            mgr.notify(FILE_TRANSFER_NOTIF_ID, notification)
            // 5 秒后自动移除完成通知
            scope.launch {
                delay(5000)
                try { mgr.cancel(FILE_TRANSFER_NOTIF_ID) } catch (_: Exception) {}
            }
            pendingFileTransfer = null
        } catch (e: Exception) {
            Log.w(TAG, "显示完成通知失败: ${e.message}")
        }
    }

    /**
     * 取消下载：移除通知
     */
    fun cancelFileTransferNotification() {
        val ctx = context ?: return
        try {
            val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.cancel(FILE_TRANSFER_NOTIF_ID)
        } catch (_: Exception) {}
        pendingFileTransfer = null
    }

    /** 格式化文件大小（用于通知显示） */
    private fun formatFileSize(b: Long): String {
        return when {
            b >= 1024L * 1024 * 1024 -> "%.2f GB".format(b / (1024.0 * 1024 * 1024))
            b >= 1024L * 1024 -> "%.1f MB".format(b / (1024.0 * 1024))
            b >= 1024L -> "%.0f KB".format(b / 1024.0)
            else -> "$b B"
        }
    }

    // ============================== 状态上报 ==============================

    private fun startStatusReportLoop() {
        statusReportJob?.cancel()
        statusReportJob = scope.launch {
            while (isActive) {
                if (userConnectedIntent && _connectionState.value == ConnectionState.CONNECTED) {
                    sendStatusReport()
                }
                delay(5000)
            }
        }
    }

    private fun sendStatusReport() {
        val ctx = context ?: return
        try {
            val battery = getBatteryStatus(ctx)
            val temp = getBatteryTemperature(ctx)
            val net = getNetworkType(ctx)
            val storage = getStorageInfo()
            val mem = getMemUsage()
            val cpu = getPhoneCpuUsage()
            _phoneMemUsage.value = mem
            // 手机 CPU 显示已移除

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
                    put("device_model", android.os.Build.MODEL)
                    put("android_version", android.os.Build.VERSION.RELEASE)
                        put("volume", getCurrentMusicVolume())
                }
            }
            scope.launch { sendRaw(msg.toString()) }
        } catch (e: Exception) {
            Log.e(TAG, "Status report failed", e)
        }
    }

    // 上次 /proc/stat 采样（用于两次差值计算 CPU 占用率）
    private var prevTotal = 0L
    private var prevIdle = 0L

    @Synchronized
    private fun getPhoneCpuUsage(): Float {
        // 方案1: dumpsys cpuinfo（最可靠，所有 Android 设备可用，格式固定）
        val fromDumpsys = getCpuFromDumpsys()
        if (fromDumpsys >= 0f) {
            Log.d(TAG, "getPhoneCpuUsage: dumpsys=$fromDumpsys")
            return fromDumpsys
        }

        // 方案2: top 命令（解析多种格式）
        val fromTop = getCpuFromTop()
        if (fromTop >= 0f) {
            Log.d(TAG, "getPhoneCpuUsage: top=$fromTop")
            return fromTop
        }

        // 方案3: 读 /proc/stat（部分设备可用）
        val fromProcStat = getCpuFromProcStat()
        if (fromProcStat >= 0f) {
            Log.d(TAG, "getPhoneCpuUsage: procstat=$fromProcStat")
            return fromProcStat
        }

        Log.w(TAG, "getPhoneCpuUsage: 所有方案均失败，返回 0")
        return 0f
    }

    /** 方案1: 读 /proc/stat 计算两次采样差值 */
    private fun getCpuFromProcStat(): Float {
        return try {
            val reader = java.io.BufferedReader(java.io.FileReader("/proc/stat"))
            val line = reader.readLine()  // 第一行 "cpu  user nice system idle ..."
            reader.close()
            if (!line.startsWith("cpu")) return -1f
            val parts = line.split(Regex("\\s+")).drop(1).map { it.toLong() }
            if (parts.size < 4) return -1f
            val idle = parts[3]
            val total = parts.sum()
            if (prevTotal > 0) {
                val dTotal = total - prevTotal
                val dIdle = idle - prevIdle
                prevTotal = total
                prevIdle = idle
                if (dTotal > 0) {
                    val pct = ((dTotal - dIdle).toFloat() / dTotal * 100f).coerceIn(0f, 100f)
                    Log.d(TAG, "getCpuFromProcStat: $pct%")
                    pct
                } else 0f
            } else {
                // 首次采样，记录基准值，下次再算
                prevTotal = total
                prevIdle = idle
                -1f
            }
        } catch (e: Exception) {
            -1f  // Android 8+ SELinux 阻止读取 /proc/stat
        }
    }

    /** 方案2: top 命令兜底 */
    private fun getCpuFromTop(): Float {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "top -b -n 1 2>&1"))
            val finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            val output = if (finished) {
                process.inputStream.bufferedReader().readText()
            } else {
                process.destroy()
                return -1f
            }
            parseTopCpuUsage(output)
        } catch (e: Exception) {
            -1f
        }
    }

    /** dumpsys cpuinfo 解析（找 "XX% TOTAL" 或 "TOTAL: XX%" 行） */
    private fun getCpuFromDumpsys(): Float {
        return try {
            // 直接用 /system/bin/dumpsys，避免 PATH 查找问题
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/dumpsys", "cpuinfo"))
            val finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                Log.w(TAG, "getCpuFromDumpsys: timeout")
                return -1f
            }
            val output = process.inputStream.bufferedReader().readText()
            if (output.isBlank()) {
                Log.w(TAG, "getCpuFromDumpsys: empty output")
                return -1f
            }
            // 从后往前找 "TOTAL" 行（多种格式）
            for (line in output.lines().reversed()) {
                val trimmed = line.trim()
                if (!trimmed.contains("TOTAL", ignoreCase = true)) continue
                // 格式1: "18% TOTAL: ..."
                val match1 = Regex("(\\d+\\.?\\d*)%\\s+TOTAL", RegexOption.IGNORE_CASE).find(trimmed)
                if (match1 != null) {
                    val v = match1.groupValues[1].toFloatOrNull()
                    if (v != null) return v.coerceIn(0f, 100f)
                }
                // 格式2: "TOTAL: 18% ..." 或 "TOTAL 18% ..."
                val match2 = Regex("TOTAL:?(?:\\s+)(\\d+\\.?\\d*)%", RegexOption.IGNORE_CASE).find(trimmed)
                if (match2 != null) {
                    val v = match2.groupValues[1].toFloatOrNull()
                    if (v != null) return v.coerceIn(0f, 100f)
                }
                // 格式3: 行中任何百分比
                val m = Regex("(\\d+\\.?\\d*)%").find(trimmed)
                val v = m?.groupValues?.get(1)?.toFloatOrNull()
                if (v != null && v <= 100f) return v
            }
            Log.w(TAG, "getCpuFromDumpsys: no TOTAL line, output=${output.take(300)}")
            -1f
        } catch (e: Exception) {
            Log.e(TAG, "getCpuFromDumpsys failed", e)
            -1f
        }
    }

    /**
     * 解析 top 命令输出的整机 CPU 占用率，兼容多种 Android 版本格式：
     * - 新版 toybox: "%Cpu(s):  15.2 us,  3.8 sy,  0.0 ni, 80.5 id,  0.5 wa, ..."
     * - 旧版 toolbox: "CPU: 15% user, 5% kernel, 80% idle"
     * - Android 10: "User 15%, System 5%, IOW 1%, IRQ 0%"
     */
    private fun parseTopCpuUsage(output: String): Float {
        for (rawLine in output.lineSequence()) {
            val line = rawLine.trim()
            // 格式1: "%Cpu(s): 15.2 us, 3.8 sy, 0.0 ni, 80.5 id"
            if (line.startsWith("%Cpu", ignoreCase = true)) {
                val idleMatch = Regex("(\\d+\\.?\\d*)\\s+id").find(line)
                if (idleMatch != null) {
                    val idle = idleMatch.groupValues[1].toFloatOrNull()
                    if (idle != null) return (100f - idle).coerceIn(0f, 100f)
                }
            }
            // 格式2: "CPU: 15% user, 5% kernel, 80% idle"
            if (line.startsWith("CPU:", ignoreCase = true)) {
                val idleMatch = Regex("(\\d+)%\\s+idle").find(line)
                if (idleMatch != null) {
                    val idle = idleMatch.groupValues[1].toFloatOrNull()
                    if (idle != null) return (100f - idle).coerceIn(0f, 100f)
                }
            }
            // 格式3: "User 15%, System 5%, IOW 1%, IRQ 0%" (Android 10 toolbox)
            if (line.startsWith("User", ignoreCase = true) && line.contains("%") && line.contains("System", ignoreCase = true)) {
                val userMatch = Regex("User\\s+(\\d+)%", RegexOption.IGNORE_CASE).find(line)
                val sysMatch = Regex("System\\s+(\\d+)%", RegexOption.IGNORE_CASE).find(line)
                val iowMatch = Regex("IOW\\s+(\\d+)%", RegexOption.IGNORE_CASE).find(line)
                val irqMatch = Regex("IRQ\\s+(\\d+)%", RegexOption.IGNORE_CASE).find(line)
                val user = userMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                val sys = sysMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                val iow = iowMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                val irq = irqMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                return (user + sys + iow + irq).coerceIn(0f, 100f)
            }
        }
        Log.w(TAG, "parseTopCpuUsage: no CPU summary found, output=${output.take(300)}")
        return -1f
    }

    private fun getBatteryStatus(ctx: Context): Int {
        return try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        } catch (e: Exception) { -1 }
    }

    private fun getBatteryTemperature(ctx: Context): Float {
        return try {
            val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val intent = if (android.os.Build.VERSION.SDK_INT >= 34) {
                ctx.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                ctx.registerReceiver(null, filter)
            }
            intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1)?.div(10f) ?: -1f
        } catch (e: Exception) { -1f }
    }

    private fun getNetworkType(ctx: Context): String {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val nw = cm?.activeNetwork ?: return "none"
            val cap = cm.getNetworkCapabilities(nw) ?: return "none"
            when {
                cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
        } catch (e: Exception) { "unknown" }
    }

    private fun getStorageInfo(): Pair<Long, Long> {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            Pair(total, free)
        } catch (e: Exception) { Pair(0L, 0L) }
    }

    /**
     * 真正的内存使用率（堆 / max），原 getCpuUsage 实为内存，故改名 getMemUsage。
     */
    private fun getMemUsage(): Float {
        return try {
            val r = Runtime.getRuntime()
            val used = (r.totalMemory() - r.freeMemory()).toFloat()
            val total = r.maxMemory().toFloat()
            (used / total * 100).coerceIn(0f, 100f)
        } catch (e: Exception) { 0f }
    }

    // ============================== 剪贴板 ==============================

    fun setClipboardContent(text: String) {
        try {
            val cm = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("PhoneHub", text))
            lastClipboardContent = text
        } catch (e: Exception) {
            Log.e(TAG, "Set clipboard failed", e)
        }
    }

    fun sendClipboard(text: String) {
        val ts = System.currentTimeMillis()
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "clipboard")
                put("txt", text)
                put("timestamp", ts)
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    fun sendText(text: String, filename: String? = null) {
        val actualName = filename ?: "text_${System.currentTimeMillis()}.txt"
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "txt")
                put("txt", text)
                put("filename", actualName)
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

    /**
     * 请求电脑端截图当前界面并传回手机（Task 15.3）。
     * 电脑端收到后应截取当前屏幕并以 JPEG 帧推送回手机显示。
     */
    fun requestPcScreenshot() {
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "pc_screenshot_request")
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    /**
     * 通知电脑端手机摄像头已切换（前后置）（Task 20.3）。
     * @param facing 当前镜头方向，"back" 或 "front"
     */
    fun sendCameraSwitch(facing: String = "back") {
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "camera_switch")
                put("facing", facing)
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    private fun sendMediaKey(cmd: String) {
        // 优先用无障碍服务执行媒体键
        val acc = PhoneHubAccessibilityService.instance
        when (cmd) {
            "media_play_pause" -> acc?.performMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "media_prev" -> acc?.performMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "media_next" -> acc?.performMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
            "vol_up" -> adjustVolume(true)
            "vol_down" -> adjustVolume(false)
            "vol_mute" -> toggleMute()
        }
    }

    private fun adjustVolume(up: Boolean) {
        try {
            val am = context?.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            val dir = if (up) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER
            am?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, dir, 0)
        } catch (e: Exception) {}
    }

    private fun toggleMute() {
        try {
            val am = context?.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            am?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_TOGGLE_MUTE, 0)
        } catch (e: Exception) {}
    }

    private fun setVolume(vol: Int) {
        try {
            val am = context?.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            val maxVol = am?.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) ?: 15
            val clamped = vol.coerceIn(0, maxVol)
            am?.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, clamped, 0)
            Log.i(TAG, "setVolume: $clamped (max=$maxVol)")
        } catch (e: Exception) {
            Log.e(TAG, "setVolume failed", e)
        }
    }


    private fun getCurrentMusicVolume(): Int {
        return try {
            val am = context?.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return 7
            am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        } catch (_: Exception) { 7 }
    }

    private fun sendCurrentVolume() {
        sendAction("volume_changed", mapOf("volume" to getCurrentMusicVolume()))
    }

    // ============================== 媒体信息监控 ==============================
    private var mediaMonitorJob: Job? = null

    /**
     * 启动媒体信息周期性监控：每 2 秒查询当前播放媒体的标题，如有变化则上报给 PC
     */
    fun startMediaMonitoring() {
        mediaMonitorJob?.cancel()
        mediaMonitorJob = scope.launch {
            while (isActive) {
                delay(2000) // 2 秒间隔
                val currentTitle = getCurrentMediaTitle()
                if (!currentTitle.isNullOrBlank() && currentTitle != _mediaInfo.value.split(" - ").first()) {
                    _mediaInfo.value = currentTitle
                    sendMediaCommand("media_update")
                }
            }
        }
    }

    /**
     * 停止媒体信息监控
     */
    fun stopMediaMonitoring() {
        mediaMonitorJob?.cancel()
        mediaMonitorJob = null
    }

    /**
     * 尝试从 AudioManager 获取当前正在播放媒体的标题（通过 RemoteControlClient 元数据）
     * 简化实现：目前仅作为框架预留，实际开发中需适配不同 Android 版本的媒体会话 API
     */
    private fun getCurrentMediaTitle(): String? {
        // 简化占位：暂时不实现复杂的媒体标题获取逻辑，返回 null 由上层决定
        // 实际完整方案可使用 MediaSessionCompat.Callback 或广播监听
        return null
    }

    // ============================== 文件传输 ==============================

    // Task 10: 使用 sendJob 替代 fileTransferJob，不再 cancel receiveJob
    fun sendFile(file: File) {
        if (!file.exists()) return
        // 自动重置卡死状态：如果 sendJob 不再活跃但仍有传输在进行
        if (sendJob?.isActive == true) {
            // 检查 ackTracker 中是否有卡住超过 60 秒的任务，如果有则取消
            val now = System.currentTimeMillis()
            val stuckFileId = ackTracker.entries.firstOrNull { now - it.value > 60000 }?.key
            if (stuckFileId != null) {
                Log.w(TAG, "sendFile: 检测到卡死任务 $stuckFileId，强制取消 sendJob")
                sendJob?.cancel()
                ackTracker.remove(stuckFileId)
            } else {
                Log.w(TAG, "sendFile: 上次发送任务仍在进行，忽略新请求")
                return
            }
        }
        sendJob = scope.launch {
            try {
                val fileId = UUID.randomUUID().toString()
                val fileSize = file.length()
                fileTransferCancel = false
                transferPaused = false

                val headMsg = buildJsonMessage {
                    put("source", "phone")
                    putJsonObject("data") {
                        put("action", "send_file_head")
                        put("file_name", file.name)
                        put("file_size", fileSize)
                        put("file_id", fileId)
                    }
                }
                sendRaw(headMsg.toString())

                // 两阶段握手：等待 PC 的 file_accept 后才开始上传
                val deferred = CompletableDeferred<Boolean>()
                pendingSend = PendingSendInfo(
                    fileId = fileId,
                    fileName = file.name,
                    fileSize = fileSize,
                    file = file,
                    deferred = deferred
                )
                Log.i(TAG, "sendFile: 已发送 head, 等待 PC 确认 fileId=$fileId")

                val accepted = withTimeoutOrNull(120000) { deferred.await() }
                pendingSend = null
                if (accepted != true) {
                    Log.w(TAG, "sendFile: PC 未确认或拒绝，取消发送 fileId=$fileId")
                    return@launch
                }

                when (_currentChannel.value) {
                    ChannelType.ADB -> sendFileWifi(fileId, file, fileSize)  // ADB 反向转发走相同 HTTP 接口
                    ChannelType.WIFI -> sendFileWifi(fileId, file, fileSize)
                    // ChannelType.PAW -> sendFilePaw(fileId, file, fileSize)  // 【禁止删除】PAW 文件发送
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send file failed", e)
            } finally {
                pendingSend = null
            }
        }
    }

    /**
     * Task 10: 通过 Uri 发送文件（用于文件选择器 SAF 返回的 content:// Uri）
     * 使用 ContentResolver.openInputStream(uri) 而非直接 File(path)，防止闪退
     */
    fun sendFile(uri: android.net.Uri, displayName: String? = null) {
        if (sendJob?.isActive == true) {
            val now = System.currentTimeMillis()
            val stuckFileId = ackTracker.entries.firstOrNull { now - it.value > 60000 }?.key
            if (stuckFileId != null) {
                Log.w(TAG, "sendFile(Uri): 检测到卡死任务 $stuckFileId，强制取消 sendJob")
                sendJob?.cancel()
                ackTracker.remove(stuckFileId)
            } else {
                Log.w(TAG, "sendFile(Uri): 上次发送任务仍在进行，忽略新请求")
                return
            }
        }
        val ctx = context ?: return
        sendJob = scope.launch {
            try {
                val fileId = UUID.randomUUID().toString()
                fileTransferCancel = false
                transferPaused = false

                // 通过 ContentResolver 查询文件名和大小
                val cr = ctx.contentResolver
                var fileName = displayName ?: "file_${System.currentTimeMillis()}"
                var fileSize = 0L
                try {
                    cr.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0) {
                                cursor.getString(nameIdx)?.takeIf { it.isNotEmpty() }?.let { fileName = it }
                            }
                            val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (sizeIdx >= 0) {
                                fileSize = cursor.getLong(sizeIdx)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "查询 Uri 元数据失败", e)
                }

                val headMsg = buildJsonMessage {
                    put("source", "phone")
                    putJsonObject("data") {
                        put("action", "send_file_head")
                        put("file_name", fileName)
                        put("file_size", fileSize)
                        put("file_id", fileId)
                    }
                }
                sendRaw(headMsg.toString())

                // 立即显示初始进度，让用户知道正在等待电脑确认
                _fileTransferProgress.value = TransferProgress(fileId, fileName, 0, fileSize, false)
                _transferCompleted.value = false

                // 两阶段握手：等待 PC 的 file_accept 后才开始上传
                val deferred = CompletableDeferred<Boolean>()
                pendingSend = PendingSendInfo(
                    fileId = fileId,
                    fileName = fileName,
                    fileSize = fileSize,
                    uri = uri,
                    deferred = deferred
                )
                Log.i(TAG, "sendFile(Uri): 已发送 head, 等待 PC 确认 fileId=$fileId")

                val accepted = withTimeoutOrNull(120000) { deferred.await() }
                pendingSend = null
                if (accepted != true) {
                    Log.w(TAG, "sendFile(Uri): PC 未确认或拒绝，取消发送 fileId=$fileId")
                    showToast("发送失败: 电脑未确认")
                    _fileTransferProgress.value = null
                    return@launch
                }

                when (_currentChannel.value) {
                    ChannelType.ADB -> sendFileWifiFromUri(fileId, uri, fileName, fileSize)
                    ChannelType.WIFI -> sendFileWifiFromUri(fileId, uri, fileName, fileSize)
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send file (Uri) failed", e)
                showToast("发送异常: ${e.message}")
                _fileTransferProgress.value = null
            } finally {
                pendingSend = null
            }
        }
    }

    /**
     * 通用流式上传核心逻辑（sendFileWifi / sendFileWifiFromUri 共用）
     * @param fileId 文件ID
     * @param displayName 显示用文件名
     * @param fileSize 文件总大小
     * @param inputStream 输入流（已 skip 到 resumeOffset）
     * @param resumeOffset 断点续传偏移量
     */
    private suspend fun uploadStreamInternal(
        fileId: String,
        displayName: String,
        fileSize: Long,
        inputStream: java.io.InputStream,
        resumeOffset: Long
    ) {
        val ip = pcIp ?: DEFAULT_IP
        val base = if (_currentChannel.value == ChannelType.ADB)
            "http://127.0.0.1:$connectPort" else "http://$ip:$connectPort"
        Log.i(TAG, "uploadStreamInternal: 开始上传 $fileId, size=$fileSize, base=$base, offset=$resumeOffset")
        _transferCompleted.value = false
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$base/api/upload_file")
            conn = url.openConnection() as HttpURLConnection
            currentConn = conn
            conn.connectTimeout = 5000
            conn.readTimeout = 120000
            conn.doOutput = true
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $secretToken")
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setRequestProperty("X-File-Id", fileId)
            conn.setRequestProperty("X-File-Name", java.net.URLEncoder.encode(displayName, "UTF-8"))
            conn.setRequestProperty("X-File-Size", fileSize.toString())
            if (resumeOffset > 0) {
                conn.setRequestProperty("X-Resume-Offset", resumeOffset.toString())
            }
            val remaining = fileSize - resumeOffset
            if (remaining > 0) conn.setFixedLengthStreamingMode(remaining)
            ackTracker[fileId] = System.currentTimeMillis()

            var uploadInterrupted = false
            try {
                inputStream.use { fis ->
                    conn!!.outputStream.use { os ->
                        val buffer = ByteArray(65536)
                        var sent: Long = resumeOffset
                        var bytes: Int
                        while (fis.read(buffer).also { bytes = it } != -1 && !fileTransferCancel) {
                            os.write(buffer, 0, bytes)
                            sent += bytes
                            resumeInfo?.resumeOffset = sent
                            _fileTransferProgress.value = TransferProgress(fileId, displayName, sent, fileSize, false)
                            ackTracker[fileId] = System.currentTimeMillis()
                        }
                        os.flush()
                    }
                }
            } catch (e: Exception) {
                uploadInterrupted = true
                Log.w(TAG, "uploadStreamInternal: 上传流中断 sent=${resumeInfo?.resumeOffset}/$fileSize, cancel=$fileTransferCancel, paused=$transferPaused, err=${e.message}")
            }

            if (transferPaused) {
                Log.i(TAG, "uploadStreamInternal: 上传已暂停，保留进度")
                _fileTransferProgress.value = TransferProgress(fileId, displayName, resumeInfo?.resumeOffset ?: resumeOffset, fileSize, false)
            } else if (fileTransferCancel) {
                Log.w(TAG, "uploadStreamInternal: 上传被取消")
                _fileTransferProgress.value = null
            } else if (!uploadInterrupted) {
                val respCode = conn.responseCode
                if (respCode == 200) {
                    sendFileComplete(fileId)
                    startAckWait(fileId)
                    resumeInfo = null
                    Log.i(TAG, "uploadStreamInternal: 上传完成, respCode=$respCode")
                    _transferCompleted.value = true
                    _completedTransfer.tryEmit(CompletedTransfer(displayName, true))
                    _fileTransferProgress.value = null
                } else {
                    Log.e(TAG, "uploadStreamInternal: 上传失败 respCode=$respCode")
                    showToast("上传失败: HTTP $respCode")
                    _fileTransferProgress.value = null
                }
            } else {
                showToast("上传中断: $displayName")
                _fileTransferProgress.value = null
            }
        } catch (e: Exception) {
            if (transferPaused || fileTransferCancel) {
                Log.w(TAG, "uploadStreamInternal interrupted (paused/cancelled): ${e.message}")
            } else {
                Log.e(TAG, "uploadStreamInternal failed", e)
                showToast("上传异常: ${e.message}")
                _fileTransferProgress.value = null
            }
        } finally {
            try { conn?.disconnect() } catch (e: Exception) {}
            currentConn = null
        }
    }

    // 改为流式上传：单次 HTTP POST 请求上传整个文件，支持断点续传
    private suspend fun sendFileWifi(fileId: String, file: File, fileSize: Long) {
        val resumeOffset = resumeInfo?.resumeOffset ?: 0L
        if (resumeInfo == null) {
            resumeInfo = ResumeInfo(fileId, file.name, fileSize, file = file)
        }
        val fis = FileInputStream(file)
        if (resumeOffset > 0) {
            val skipped = fis.skip(resumeOffset)
            Log.i(TAG, "sendFileWifi: 跳过 $skipped 字节 (offset=$resumeOffset)")
        }
        uploadStreamInternal(fileId, file.name, fileSize, fis, resumeOffset)
    }

    /**
     * 改为流式上传（通过 Uri）：单次 HTTP POST 请求上传整个文件
     */
    private suspend fun sendFileWifiFromUri(fileId: String, uri: android.net.Uri, fileName: String, fileSize: Long) {
        val ctx = context ?: return
        val resumeOffset = resumeInfo?.resumeOffset ?: 0L
        if (resumeInfo == null) {
            resumeInfo = ResumeInfo(fileId, fileName, fileSize, uri = uri)
        }
        val fis = ctx.contentResolver.openInputStream(uri) ?: return
        if (resumeOffset > 0) {
            val skipped = fis.skip(resumeOffset)
            Log.i(TAG, "sendFileWifiFromUri: 跳过 $skipped 字节 (offset=$resumeOffset)")
        }
        uploadStreamInternal(fileId, fileName, fileSize, fis, resumeOffset)
    }

    // private suspend fun sendFilePaw(fileId: String, file: File, fileSize: Long) {  // 【禁止删除】PAW 文件发送
    //     // PAW 通道下用 upload_chunk 二进制接口（PAW 同样提供 /api/upload_chunk 路由）
    //     try {
    //         FileInputStream(file).use { fis ->
    //             var partNum = 0
    //             var sent: Long = 0
    //             val buffer = ByteArray(CHUNK_SIZE)
    //             var bytes: Int
    //             ackTracker[fileId] = System.currentTimeMillis()
    //
    //             while (fis.read(buffer).also { bytes = it } != -1 && !fileTransferCancel) {
    //                 val chunk = if (bytes < CHUNK_SIZE) buffer.copyOf(bytes) else buffer
    //                 val url = URL("$pawUrl/api/upload_chunk/$fileId/$partNum")
    //                 val conn = url.openConnection() as HttpURLConnection
    //                 conn.doOutput = true
    //                 conn.requestMethod = "POST"
    //                 conn.setRequestProperty("Authorization", "Bearer $secretToken")
    //                 conn.setRequestProperty("Content-Type", "application/octet-stream")
    //                 conn.outputStream.use { os -> os.write(chunk) }
    //                 conn.responseCode
    //                 conn.disconnect()
    //
    //                 sent += bytes
    //                 _fileTransferProgress.value = TransferProgress(fileId, file.name, sent, fileSize, false)
    //                 ackTracker[fileId] = System.currentTimeMillis()
    //                 partNum++
    //             }
    //         }
    //
    //         if (!fileTransferCancel) {
    //             sendFileComplete(fileId)
    //             startAckWait(fileId)
    //         }
    //     } catch (e: Exception) {
    //         Log.e(TAG, "PAW file send failed", e)
    //     }
    // }

    private fun sendFileComplete(fileId: String) {
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "file_complete")
                put("file_id", fileId)
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    /**
     * 10 秒未收到 ack 则重发 file_complete。
     */
    private fun startAckWait(fileId: String) {
        scope.launch {
            var retries = 0
            while (isActive && retries < 3) {
                delay(ACK_TIMEOUT_MS)
                if (!ackTracker.containsKey(fileId)) return@launch  // 已收到 ack
                retries++
                Log.i(TAG, "ACK 超时重发 file_complete ($retries) for $fileId")
                sendFileComplete(fileId)
            }
            // 仍然失败：放弃
            ackTracker.remove(fileId)
        }
    }

    // Task 10: 使用 receiveJob，不再 cancel sendJob，避免发送和接收互相 cancel
    // 改为流式下载：单次 HTTP GET 请求下载整个文件，支持断点续传
    private fun startReceiveFile(fileId: String, fileName: String, fileSize: Long) {
        receiveJob?.cancel()
        fileReceiveState[fileId] = FileReceiveState(fileId, fileName, fileSize)
        Log.i(TAG, "startReceiveFile(流式): fileId=$fileId, name=$fileName, size=$fileSize, receiveDir=$receiveDir")
        // 立即设置初始进度(0%)，让UI马上显示"接收中"状态（不依赖pageCache是否已创建）
        _fileTransferProgress.value = TransferProgress(fileId, fileName, 0, fileSize, true)
        _transferCompleted.value = false
        receiveJob = scope.launch {
            var conn: HttpURLConnection? = null
            try {
                fileTransferCancel = false
                transferPaused = false
                // 保存恢复信息（PC→手机方向暂停后继续时断点续传）
                resumeInfo = ResumeInfo(fileId, fileName, fileSize)
                // 确保接收目录存在（防止 ENOENT）
                receiveDir?.mkdirs()
                val dir = if (receiveDir != null && (receiveDir!!.exists() || receiveDir!!.mkdirs())) {
                    receiveDir
                } else {
                    // 外部存储不可用时，回退到应用内部存储
                    val fallback = context?.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: context?.filesDir
                    fallback?.mkdirs()
                    Log.w(TAG, "startReceiveFile: 外部存储不可用，回退到 $fallback")
                    fallback
                }
                val outFile = File(dir, fileName)
                val progressFile = File(dir, "$fileName.progress")
                Log.i(TAG, "startReceiveFile: outFile=${outFile.absolutePath}, progressFile=${progressFile.absolutePath}")
                // 读取已接收的字节数（断点续传）
                var resumeOffset = 0L
                if (progressFile.exists()) {
                    try {
                        resumeOffset = progressFile.readText().trim().toLong()
                        if (resumeOffset > 0 && resumeOffset < fileSize && outFile.exists() && outFile.length() >= resumeOffset) {
                            Log.i(TAG, "startReceiveFile: 断点续传 offset=$resumeOffset")
                        } else {
                            resumeOffset = 0
                            progressFile.delete()
                            if (outFile.exists()) outFile.delete()
                        }
                    } catch (e: Exception) {
                        resumeOffset = 0
                        progressFile.delete()
                        if (outFile.exists()) outFile.delete()
                    }
                } else {
                    if (outFile.exists()) outFile.delete()
                }

                val ip = pcIp ?: DEFAULT_IP
                val base = if (_currentChannel.value == ChannelType.ADB)
                    "http://127.0.0.1:$connectPort" else "http://$ip:$connectPort"
                val url = URL("$base/api/download_file/$fileId")
                Log.i(TAG, "startReceiveFile: 开始下载 $url, offset=$resumeOffset")
                conn = url.openConnection() as HttpURLConnection
                currentConn = conn
                conn.connectTimeout = 5000
                conn.readTimeout = 300000  // 5分钟，大文件需要长超时
                conn.setRequestProperty("Authorization", "Bearer $secretToken")
                conn.setRequestProperty("Connection", "close")  // 禁用keep-alive，避免流式传输卡死
                if (resumeOffset > 0) {
                    conn.setRequestProperty("Range", "bytes=$resumeOffset-")
                }

                val code = conn.responseCode
                Log.i(TAG, "startReceiveFile: responseCode=$code, channel=${_currentChannel.value}, contentLength=${conn.contentLength}")
                if (code == 200 || code == 206) {
                    val state = fileReceiveState[fileId]
                    if (state == null) {
                        Log.e(TAG, "startReceiveFile: fileReceiveState[$fileId] 为 null，退出")
                        showToast("接收文件失败: 内部状态错误")
                        _fileTransferProgress.value = null
                        return@launch
                    }
                    Log.i(TAG, "startReceiveFile: 开始读取数据流")
                    var received = if (code == 206) resumeOffset else 0L
                    // 206 时追加写入，200 时覆盖写入
                    val fosMode = if (code == 206) true else false
                    var streamBroken = false
                    try {
                        FileOutputStream(outFile, fosMode).use { fos ->
                            conn!!.inputStream.use { ins ->
                                val buffer = ByteArray(65536)
                                var bytes: Int
                                while (ins.read(buffer).also { bytes = it } != -1 && !fileTransferCancel) {
                                    fos.write(buffer, 0, bytes)
                                    received += bytes
                                    state.received = received
                                    _fileTransferProgress.value = TransferProgress(fileId, fileName, received, fileSize, true)
                                    progressFile.writeText(received.toString())
                                    // 同步更新通知中的进度条（节流在 updateFileTransferNotification 内部）
                                    updateFileTransferNotification(fileName, received, fileSize)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // 流中断（暂停/取消/网络异常）：记录状态，不当作错误
                        streamBroken = true
                        Log.w(TAG, "startReceiveFile: 流中断 received=$received/$fileSize, cancel=$fileTransferCancel, paused=$transferPaused, err=${e.message}")
                        // 确保已接收字节写入 .progress 供断点续传
                        try { progressFile.writeText(received.toString()) } catch (_: Exception) {}
                    }
                    Log.i(TAG, "startReceiveFile: 数据流结束, received=$received/$fileSize, cancel=$fileTransferCancel, paused=$transferPaused, streamBroken=$streamBroken")

                    if (fileTransferCancel && !transferPaused) {
                        // 真正取消（非暂停）：清空进度，删除不完整的文件
                        Log.w(TAG, "startReceiveFile: 传输被取消，删除不完整文件")
                        try {
                            if (outFile.exists()) outFile.delete()
                            if (progressFile.exists()) progressFile.delete()
                        } catch (e: Exception) {
                            Log.w(TAG, "删除不完整文件失败: ${e.message}")
                        }
                        _fileTransferProgress.value = null
                        cancelFileTransferNotification()
                    } else if (transferPaused) {
                        // 暂停：保留进度状态和 resumeInfo，UI 显示"已暂停"，不清空进度
                        Log.i(TAG, "startReceiveFile: 传输已暂停，保留进度 received=$received/$fileSize")
                        // 更新 resumeInfo 的 offset 供继续时使用
                        resumeInfo?.resumeOffset = received
                        // 确保进度显示当前已接收量
                        _fileTransferProgress.value = TransferProgress(fileId, fileName, received, fileSize, true)
                        // 通知显示"已暂停"状态（仍 ongoing）
                        updateFileTransferNotification(fileName, received, fileSize, paused = true)
                    } else if (received >= fileSize) {
                        // 正常完成
                        progressFile.delete()
                        resumeInfo = null
                        sendAck(fileId)
                        Log.i(TAG, "startReceiveFile: 下载完成, received=$received")
                        showToast("文件接收完成: $fileName")
                        _transferCompleted.value = true
                        _completedTransfer.tryEmit(CompletedTransfer(fileName, false))
                        _fileTransferProgress.value = null
                        completeFileTransferNotification(fileName)
                    } else {
                        // 流中断但未取消/暂停（可能是对端暂停但消息未到达，或网络异常）
                        // 视为暂停处理：保留进度和 resumeInfo，等待对端的 resume/cancel 消息
                        // 不弹"中断"Toast，避免暂停被误显示为中断
                        Log.w(TAG, "startReceiveFile: 流中断未取消/暂停，视为暂停 received=$received/$fileSize")
                        resumeInfo?.resumeOffset = received
                        _fileTransferProgress.value = TransferProgress(fileId, fileName, received, fileSize, true)
                        updateFileTransferNotification(fileName, received, fileSize, paused = true)
                    }
                } else {
                    Log.e(TAG, "startReceiveFile: 下载失败 responseCode=$code")
                    showToast("接收文件失败: HTTP $code")
                    _fileTransferProgress.value = null
                    cancelFileTransferNotification()
                }
            } catch (e: Exception) {
                // 暂停/取消导致的 Socket closed 等异常不算错误
                if (transferPaused || fileTransferCancel) {
                    Log.w(TAG, "File receive interrupted (paused/cancelled): ${e.message}")
                } else {
                    Log.e(TAG, "File receive failed", e)
                    showToast("接收文件异常: ${e.message}")
                    _fileTransferProgress.value = null
                    cancelFileTransferNotification()
                }
            } finally {
                try { conn?.disconnect() } catch (e: Exception) {}
                currentConn = null
                fileReceiveState.remove(fileId)
            }
        }
    }

    /** 在主线程显示Toast */
    private fun showToast(msg: String) {
        val ctx = context ?: return
        mainHandler.post {
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // downloadChunk 已废弃（改为流式下载），保留空函数避免编译错误
    private suspend fun downloadChunk(fileId: String, partNum: Int): ByteArray? {
        return null
    }

    private fun completeFileReceive(fileId: String) {
        _fileTransferProgress.value = null
        fileReceiveState.remove(fileId)
    }

    /**
     * sendAck 改为发送 file_complete（与协议一致）。
     */
    private fun sendAck(fileId: String) {
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "file_complete")
                put("file_id", fileId)
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    /** 向 PC 发送传输控制消息（pause/resume/cancel） */
    private fun sendTransferControl(ctrl: String) {
        val fileId = resumeInfo?.fileId ?: pendingSend?.fileId ?: ""
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "transfer_control")
                put("ctrl", ctrl)
                put("file_id", fileId)
            }
        }
        scope.launch { sendRaw(msg.toString()) }
        Log.i(TAG, "发送 transfer_control: ctrl=$ctrl, fileId=$fileId")
    }

    fun cancelTransfer() {
        fileTransferCancel = true
        transferPaused = false
        _transferPausedFromPc.value = false
        _transferCompleted.value = false
        // 强制中断当前 HTTP 连接
        try { currentConn?.disconnect() } catch (e: Exception) {}
        // 通知 PC 取消
        sendTransferControl("cancel")
        // Task 10: 分别 cancel 发送和接收 Job
        sendJob?.cancel()
        receiveJob?.cancel()
        resumeInfo = null
        pendingSend = null
        _fileTransferProgress.value = null
        // 同步移除文件接收通知
        cancelFileTransferNotification()
    }

    /** 暂停当前传输（中断HTTP连接，流式循环会检测 fileTransferCancel 并退出） */
    fun pauseTransfer() {
        fileTransferCancel = true
        transferPaused = true
        _transferPausedFromPc.value = true
        // 强制中断当前 HTTP 连接，避免缓冲区继续传输导致速度还在变
        try { currentConn?.disconnect() } catch (e: Exception) {}
        // 通知 PC 暂停
        sendTransferControl("pause")
    }

    /** 继续暂停的传输：通知 PC resume 后重新发起上传/下载（从头重发） */
    fun resumeTransfer() {
        transferPaused = false
        fileTransferCancel = false
        _transferPausedFromPc.value = false
        // 通知 PC resume（PC 端会重置 file_transfer_cancel）
        sendTransferControl("resume")
        // 用 resumeInfo 重新发起传输
        val info = resumeInfo
        if (info != null) {
            scope.launch {
                // 延迟 500ms，确保 PC 端先收到 resume 消息并重置 file_transfer_cancel
                delay(500)
                when {
                    info.file != null -> sendFileWifi(info.fileId, info.file, info.fileSize)
                    info.uri != null -> sendFileWifiFromUri(info.fileId, info.uri, info.fileName, info.fileSize)
                    else -> {
                        // PC→手机方向：重新触发下载
                        startReceiveFile(info.fileId, info.fileName, info.fileSize)
                    }
                }
            }
        } else {
            Log.w(TAG, "resumeTransfer: 没有 resumeInfo，无法恢复")
        }
    }

    /** 当前是否处于暂停状态（供 UI 查询） */
    fun isTransferPaused(): Boolean = transferPaused

    /** 重置取消标志，供继续传输前调用 */
    fun resetTransferCancel() {
        fileTransferCancel = false
    }

    // ============================== 自动安装APK ==============================

    private fun autoInstallApk(path: String) {
        scope.launch {
            try {
                val file = java.io.File(path)
                if (!file.exists()) {
                    // 尝试在 Received 目录查找
                    val receivedFile = java.io.File(receiveDir, path.substringAfterLast("/"))
                    if (receivedFile.exists()) {
                        doInstallApk(receivedFile)
                    } else {
                        Log.e(TAG, "APK file not found: $path")
                    }
                } else {
                    doInstallApk(file)
                }
            } catch (e: Exception) {
                Log.e(TAG, "autoInstallApk failed", e)
            }
        }
    }

    private fun doInstallApk(file: java.io.File) {
        val ctx = context ?: return
        try {
            // 先尝试 ADB 静默安装
            if (isAdbAvailable()) {
                val result = Runtime.getRuntime().exec(arrayOf("sh", "-c", "pm install -r -t ${file.absolutePath}")).waitFor()
                if (result == 0) {
                    Log.i(TAG, "APK installed via pm: ${file.name}")
                    mainHandler.post {
                        android.widget.Toast.makeText(ctx, "已安装: ${file.name}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "pm install failed, fallback to intent", e)
        }
        // 回退到 Intent 安装（需要用户确认一次）
        mainHandler.post {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    ctx, "${ctx.packageName}.fileprovider", file
                )
                intent.setDataAndType(uri, "application/vnd.android.package-archive")
                intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "APK install intent failed", e)
            }
        }
    }

    /**
     * 通用 action 发送方法（save.md 功能18 等）
     * @param action action 名称，如 "run_as_admin"
     * @param extra 额外字段
     */
    fun sendAction(action: String, extra: Map<String, Any> = emptyMap()) {
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", action)
                for ((k, v) in extra) {
                    when (v) {
                        is String -> put(k, v)
                        is Number -> put(k, v)
                        is Boolean -> put(k, v)
                    }
                }
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    // ============================== 电脑画面拉取（save.md 功能7 电脑→手机）==============================

    /**
     * 启动轮询拉取电脑画面 JPEG 帧
     * @param controlMode true=操控模式（点击发送指令），false=预览模式
     */
    fun startPcFramePolling(controlMode: Boolean) {
        pcFrameControlMode = controlMode
        pcFrameJob?.cancel()
        pcFrameJob = scope.launch {
            val ip = pcIp ?: DEFAULT_IP
            val baseUrl = if (isAdbAvailable() && _currentChannel.value == ChannelType.ADB) {
                "http://127.0.0.1:$connectPort"
            } else {
                "http://$ip:$connectPort"
            }
            while (isActive) {
                try {
                    val resp = client?.get("$baseUrl/api/frame") {
                        timeout { requestTimeoutMillis = 3000 }
                    }
                    if (resp?.status == HttpStatusCode.OK) {
                        val bytes = resp.readBytes()
                        if (bytes.isNotEmpty()) {
                            _pcFrame.emit(bytes)
                        }
                        // 解析电脑鼠标归一化坐标
                        try {
                            val cx = resp.headers["X-Cursor-X"]?.toFloatOrNull()
                            val cy = resp.headers["X-Cursor-Y"]?.toFloatOrNull()
                            if (cx != null && cy != null) {
                                _pcCursorPos.emit(Pair(cx, cy))
                            }
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    // 超时或错误，继续轮询
                }
                // 60fps轮询间隔
                delay(16)
            }
        }
    }

    fun stopPcFramePolling() {
        pcFrameJob?.cancel()
        pcFrameJob = null
    }

    /** 判断电脑画面拉取是否正在进行 */
    fun isPcFramePolling(): Boolean = pcFrameJob?.isActive == true

    // ============================== 电脑音频拉取与播放 ===============================

    private var pcAudioJob: kotlinx.coroutines.Job? = null
    private var pcAudioTrack: android.media.AudioTrack? = null
    private val pcAudioSampleRate = 44100

    /**
     * 启动轮询拉取电脑音频 PCM 数据并播放
     */
    fun startPcAudioPolling() {
        stopPcAudioPolling()
        // 初始化 AudioTrack 用于播放
        val bufSize = android.media.AudioTrack.getMinBufferSize(
            pcAudioSampleRate, android.media.AudioFormat.CHANNEL_OUT_STEREO,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        )
        try {
            pcAudioTrack = android.media.AudioTrack.Builder()
                .setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(android.media.AudioFormat.Builder()
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(pcAudioSampleRate)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO)
                    .build())
                .setBufferSizeInBytes(maxOf(bufSize, 4096))
                .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                .build()
            pcAudioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack init failed", e)
        }
        pcAudioJob = scope.launch {
            val ip = pcIp ?: DEFAULT_IP
            val baseUrl = if (isAdbAvailable() && _currentChannel.value == ChannelType.ADB) {
                "http://127.0.0.1:$connectPort"
            } else {
                "http://$ip:$connectPort"
            }
            while (isActive) {
                try {
                    val resp = client?.get("$baseUrl/api/audio") {
                        timeout { requestTimeoutMillis = 2000 }
                    }
                    if (resp?.status == HttpStatusCode.OK) {
                        val bytes = resp.readBytes()
                        if (bytes.isNotEmpty()) {
                            // 单声道 PCM → 双声道（复制左声道到右声道）
                            val stereo = ByteArray(bytes.size * 2)
                            for (i in bytes.indices step 2) {
                                stereo[i * 2] = bytes[i]
                                stereo[i * 2 + 1] = bytes[i + 1]
                                stereo[i * 2 + 2] = bytes[i]
                                stereo[i * 2 + 3] = bytes[i + 1]
                            }
                            pcAudioTrack?.write(stereo, 0, stereo.size, android.media.AudioTrack.WRITE_NON_BLOCKING)
                        }
                    }
                } catch (e: Exception) {
                    // 超时或错误，继续轮询
                }
                delay(30)
            }
        }
    }

    fun stopPcAudioPolling() {
        pcAudioJob?.cancel()
        pcAudioJob = null
        try {
            pcAudioTrack?.stop()
            pcAudioTrack?.release()
        } catch (_: Exception) {}
        pcAudioTrack = null
    }

    fun isPcAudioPolling(): Boolean = pcAudioJob?.isActive == true

    // ============================== 电脑摄像头画面拉取（save.md 功能8）==============================

    /**
     * 启动轮询拉取电脑摄像头 JPEG 帧
     */
    fun startPcCameraPolling() {
        pcCameraJob?.cancel()
        pcCameraJob = scope.launch {
            val ip = pcIp ?: DEFAULT_IP
            val baseUrl = if (isAdbAvailable() && _currentChannel.value == ChannelType.ADB) {
                "http://127.0.0.1:$connectPort"
            } else {
                "http://$ip:$connectPort"
            }
            while (isActive) {
                try {
                    val resp = client?.get("$baseUrl/api/camera_frame") {
                        timeout { requestTimeoutMillis = 3000 }
                    }
                    if (resp?.status == HttpStatusCode.OK) {
                        val bytes = resp.readBytes()
                        if (bytes.isNotEmpty()) {
                            _pcCameraFrame.emit(bytes)
                        }
                    }
                } catch (e: Exception) {
                    // 超时或错误，继续轮询
                }
                // 10fps 轮询间隔
                delay(100)
            }
        }
    }

    fun stopPcCameraPolling() {
        pcCameraJob?.cancel()
        pcCameraJob = null
    }

    /** 判断电脑摄像头拉取是否正在进行 */
    fun isPcCameraPolling(): Boolean = pcCameraJob?.isActive == true

    // ============================== 应用列表 ==============================

    private fun handleAppListRequest() {
        scope.launch {
            try {
                val pm = context?.packageManager ?: return@launch
                val infos = pm.getInstalledApplications(0)
                val arr = buildJsonArray {
                    for (info in infos) {
                        addJsonObject {
                            put("name", pm.getApplicationLabel(info).toString())
                            put("package", info.packageName)
                            put("system", (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                            put("version", try {
                                pm.getPackageInfo(info.packageName, 0).versionName ?: ""
                            } catch (e: Exception) { "" })
                            // APK 大小（字节）
                            put("size", try {
                                java.io.File(info.sourceDir).length()
                            } catch (e: Exception) { 0L })
                            // 安装时间（毫秒时间戳）
                            put("install_time", try {
                                pm.getPackageInfo(info.packageName, 0).firstInstallTime
                            } catch (e: Exception) { 0L })
                        }
                    }
                }
                val msg = buildJsonMessage {
                    put("source", "phone")
                    putJsonObject("data") {
                        put("action", "app_list")
                        put("apps", arr)
                    }
                }
                sendRaw(msg.toString())
            } catch (e: Exception) {
                Log.e(TAG, "App list failed", e)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun handleAppUninstallRequest(pkg: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_DELETE,
                    android.net.Uri.parse("package:$pkg"))
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context?.startActivity(intent)
                sendAction("app_uninstall_result", mapOf("package" to pkg, "success" to true))
            } catch (e: Exception) {
                sendAction("app_uninstall_result", mapOf("package" to pkg, "success" to false, "error" to (e.message ?: "")))
            }
        }
    }

    private fun handleAppApkRequest(pkg: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val pm = context?.packageManager ?: return@launch
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val apkPath = appInfo.sourceDir
                if (apkPath != null && File(apkPath).exists()) {
                    sendFile(File(apkPath))
                } else {
                    sendAction("app_apk_result", mapOf("package" to pkg, "success" to false, "error" to "APK not found"))
                }
            } catch (e: Exception) {
                sendAction("app_apk_result", mapOf("package" to pkg, "success" to false, "error" to (e.message ?: "")))
            }
        }
    }

    // ============================== 文件管理 ==============================

    private fun handleFileListRequest(path: String) {
        scope.launch {
            try {
                val dir = File(path)
                if (!dir.exists() || !dir.isDirectory) {
                    // 路径无效时返回空列表（携带 path 供 PC 校验），避免 PC 端永久卡在"正在请求..."
                    val msg = buildJsonMessage {
                        put("source", "phone")
                        putJsonObject("data") {
                            put("action", "file_list")
                            put("path", path)
                            put("files", buildJsonArray {})
                        }
                    }
                    sendRaw(msg.toString())
                    return@launch
                }
                val files = dir.listFiles() ?: emptyArray()
                val arr = buildJsonArray {
                    for (f in files) {
                        addJsonObject {
                            put("name", f.name)
                            put("path", f.absolutePath)
                            put("size", f.length())
                            put("is_dir", f.isDirectory)
                            put("modified", f.lastModified())
                        }
                    }
                }
                val msg = buildJsonMessage {
                    put("source", "phone")
                    putJsonObject("data") {
                        put("action", "file_list")
                        put("path", path)
                        put("files", arr)
                    }
                }
                sendRaw(msg.toString())
            } catch (e: Exception) {
                Log.e(TAG, "File list failed", e)
            }
        }
    }

    private fun handleFileDelete(path: String, isDir: Boolean) {
        scope.launch {
            try {
                val f = File(path)
                if (isDir) f.deleteRecursively() else f.delete()
            } catch (e: Exception) {
                Log.e(TAG, "File delete failed", e)
            }
        }
    }

    private fun handleFileRename(oldPath: String, newPath: String) {
        scope.launch {
            try {
                File(oldPath).renameTo(File(newPath))
            } catch (e: Exception) {
                Log.e(TAG, "File rename failed", e)
            }
        }
    }

    private fun handleFileMkdir(path: String) {
        scope.launch {
            try {
                File(path).mkdirs()
            } catch (e: Exception) {
                Log.e(TAG, "File mkdir failed", e)
            }
        }
    }

    private fun handleFileCopy(src: String, dst: String, isDir: Boolean) {
        scope.launch {
            try {
                val srcFile = File(src)
                val dstFile = File(dst)
                if (isDir) {
                    srcFile.copyRecursively(dstFile, overwrite = true)
                } else {
                    srcFile.copyTo(dstFile, overwrite = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "File copy failed", e)
            }
        }
    }

    private fun handleSendFileRequest(path: String) {
        scope.launch {
            try {
                sendFile(File(path))
            } catch (e: Exception) {
                Log.e(TAG, "Send file request failed", e)
            }
        }
    }

    // ============================== 截图 ==============================

    /**
     * 缓存 MediaProjection token，供后台静默截图复用
     * 在 MainActivity/ScreenshotActivity 获取到授权后调用
     */
    fun cacheMediaProjectionToken(resultCode: Int, data: Intent) {
        cachedProjectionResultCode = resultCode
        // Intent 可安全持有，不会因 Activity 销毁而失效
        cachedProjectionData = data
        val ctx = context ?: return
        if (projectionManager == null) {
            projectionManager = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        }
        Log.d(TAG, "MediaProjection token 已缓存，可后台静默截图")
    }

    /**
     * 是否已缓存可用的 MediaProjection token
     */
    fun hasCachedProjectionToken(): Boolean {
        return cachedProjectionData != null && cachedProjectionResultCode != 0
    }

    /**
     * 从缓存 token 创建 MediaProjection 实例
     * Android 14+ 同一 token 只能创建一个实例，若已有活跃实例则先释放
     */
    fun getCachedMediaProjection(): MediaProjection? {
        val ctx = context ?: return null

        // Android 14+ 优先使用 ScreenCaptureService 中的 MediaProjection
        if (android.os.Build.VERSION.SDK_INT >= 34 && ScreenCaptureService.isRunning) {
            return ScreenCaptureService.instance?.getProjection()
        }

        val pm = projectionManager
            ?: (ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager)?.also { projectionManager = it }
            ?: return null
        val rc = cachedProjectionResultCode
        val data = cachedProjectionData ?: return null
        return try {
            // Android 14+: 先检查已有的 WeakReference 是否还有效
            val existing = activeProjectionRef?.get()
            if (existing != null) {
                Log.d(TAG, "复用已有的 MediaProjection 实例")
                existing
            } else {
                // Android 14+: 先释放已有的活跃实例（如果 WeakReference 指向的对象已被 GC 回收）
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    try { activeProjectionRef?.get()?.stop() } catch (_: Exception) {}
                    activeProjectionRef = null
                }
                val projection = pm.getMediaProjection(rc, data)
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    activeProjectionRef = java.lang.ref.WeakReference(projection)
                    projection?.registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() {
                            activeProjectionRef = null
                        }
                    }, android.os.Handler(android.os.Looper.getMainLooper()))
                }
                projection
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCachedMediaProjection failed", e)
            null
        }
    }

    fun triggerScreenshot() {
        val ctx = context ?: return

        // Android 14+: 通过 ScreenCaptureService 执行截图（需要前台服务）
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            if (hasCachedProjectionToken() && ScreenCaptureService.isRunning) {
                scope.launch(Dispatchers.IO) {
                    val success = performBackgroundScreenshot()
                    if (!success) {
                        Log.w(TAG, "后台截图失败，启动 ScreenCaptureService 后重试")
                        ScreenCaptureService.start(ctx)
                        // 等待服务启动后重试
                        kotlinx.coroutines.delay(1000)
                        val retrySuccess = performBackgroundScreenshot()
                        if (!retrySuccess) {
                            launchScreenshotActivity(ctx)
                        }
                    }
                }
            } else if (hasCachedProjectionToken()) {
                // 启动 ScreenCaptureService
                ScreenCaptureService.start(ctx)
                scope.launch(Dispatchers.IO) {
                    // 等待服务启动
                    kotlinx.coroutines.delay(1000)
                    val success = performBackgroundScreenshot()
                    if (!success) {
                        launchScreenshotActivity(ctx)
                    }
                }
            } else {
                // 没有缓存 token，需要 Activity 引导授权
                launchScreenshotActivity(ctx)
            }
        } else {
            // Android 13 及以下：原有逻辑
            if (hasCachedProjectionToken()) {
                scope.launch(Dispatchers.IO) {
                    val success = performBackgroundScreenshot()
                    if (!success) {
                        Log.w(TAG, "后台截图失败，回退到 Activity 授权截图")
                        launchScreenshotActivity(ctx)
                    }
                }
            } else {
                launchScreenshotActivity(ctx)
            }
        }
    }

    private fun launchScreenshotActivity(ctx: Context) {
        val intent = Intent(ctx, ScreenshotActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        ctx.startActivity(intent)
    }

    /**
     * 后台静默截图：复用缓存 token 创建 MediaProjection，截取一帧
     * 保存到相册 + 临时文件 + 发送给电脑
     * Android 14+ 必须在前台服务中执行
     * @return true 成功，false 失败（token 失效等）
     */
    private suspend fun performBackgroundScreenshot(): Boolean {
        return withContext(Dispatchers.IO) {
            // Android 14+: 确保 ScreenCaptureService 正在运行
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                val ctx = context ?: return@withContext false
                if (!ScreenCaptureService.isRunning) {
                    ScreenCaptureService.start(ctx)
                    kotlinx.coroutines.delay(1500) // 等待服务启动
                }
                if (!ScreenCaptureService.isRunning) {
                    Log.w(TAG, "ScreenCaptureService 未启动，无法截图")
                    return@withContext false
                }
            }

            var projection: MediaProjection? = null
            var imageReader: ImageReader? = null
            var virtualDisplay: android.hardware.display.VirtualDisplay? = null
            try {
                val ctx = context ?: return@withContext false
                projection = getCachedMediaProjection() ?: return@withContext false

                val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val metrics = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(metrics)
                val w = metrics.widthPixels
                val h = metrics.heightPixels
                val dpi = metrics.densityDpi

                imageReader = ImageReader.newInstance(w, h, android.graphics.PixelFormat.RGBA_8888, 2)
                virtualDisplay = projection.createVirtualDisplay(
                    "PhoneHubBgScreenshot", w, h, dpi,
                    android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.surface, null, null
                )

                var captured: Bitmap? = null
                val latch = CountDownLatch(1)

                imageReader.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        try {
                            val planes = image.planes
                            val buffer = planes[0].buffer
                            val pixelStride = planes[0].pixelStride
                            val rowStride = planes[0].rowStride
                            val rowPadding = rowStride - pixelStride * w
                            val bmp = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888)
                            buffer.rewind()
                            bmp.copyPixelsFromBuffer(buffer)
                            captured = Bitmap.createBitmap(bmp, 0, 0, w, h)
                        } catch (e: Exception) {
                            Log.e(TAG, "后台截图 Image 处理失败", e)
                        } finally {
                            image.close()
                        }
                        latch.countDown()
                    }
                }, Handler(Looper.getMainLooper()))

                latch.await(3, TimeUnit.SECONDS)

                val bmp = captured ?: return@withContext false

                // 保存到相册 + 临时文件 + 发送给电脑
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "screenshot_${ts}.png"

                saveBitmapToGallery(ctx, bmp, fileName)

                val outFile = File(ctx.getExternalFilesDir(null), fileName)
                FileOutputStream(outFile).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
                sendFile(outFile)

                Log.d(TAG, "后台静默截图成功: $fileName")
                scope.launch { _screenshotResult.emit("截图已保存到手机相册: $fileName") }
                true
            } catch (e: Exception) {
                Log.e(TAG, "performBackgroundScreenshot failed", e)
                scope.launch { _screenshotResult.emit("截图失败: ${e.message ?: "未知错误"}") }
                false
            } finally {
                try { virtualDisplay?.release() } catch (e: Exception) {}
                try { imageReader?.close() } catch (e: Exception) {}
                try { projection?.stop() } catch (e: Exception) {}
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    activeProjectionRef = null
                }
            }
        }
    }

    /**
     * 保存 Bitmap 到系统相册的 PhoneHub 子目录
     */
    private fun saveBitmapToGallery(ctx: Context, bmp: Bitmap, fileName: String) {
        try {
            val resolver = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Computer")
            }
            val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                resolver.openOutputStream(it)?.use { os -> bmp.compress(Bitmap.CompressFormat.PNG, 100, os) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveBitmapToGallery failed", e)
        }
    }

    // ============================== 网页推送 ==============================

    fun openUrlOnDevice(url: String, forceVia: Boolean = false) {
        // save.md 功能J：电脑端发给手机
        // ADB连接下：复制内容到剪贴板 + 用Via浏览器打开
        // 非ADB连接下：仅复制到剪贴板，用户自行操作
        try {
            // 先复制到剪贴板
            setClipboardContent(url)
            val isAdb = _currentChannel.value == ChannelType.ADB
            if (isAdb || forceVia) {
                // 尝试用Via浏览器打开
                val viaPackages = listOf("mark.via", "mark.via.gp")
                var opened = false
                for (pkg in viaPackages) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                            setPackage(pkg)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context?.startActivity(intent)
                        opened = true
                        break
                    } catch (e: Exception) {
                        // Via未安装，继续尝试下一个包名
                    }
                }
                if (!opened) {
                    // Via未安装，回退到默认浏览器
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context?.startActivity(intent)
                }
            }
            // 非ADB模式仅复制到剪贴板，不自动打开
        } catch (e: Exception) {
            Log.e(TAG, "Open URL failed", e)
        }
    }

    /**
     * 推送 URL 到电脑打开。
     * @param useEdge 是否用 Edge 自动打开（否则由电脑端默认浏览器打开）
     */
    fun pushUrlToPc(url: String, useEdge: Boolean = true) {
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

    /**
     * 发送 URL 历史给电脑端同步
     * @param history 历史列表，每个元素为 {url, direction, timestamp}
     */
    fun sendUrlHistorySync(history: List<Triple<String, String, Long>>) {
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "url_history_sync")
                putJsonArray("history") {
                    for (item in history) {
                        addJsonObject {
                            put("url", item.first)
                            put("direction", item.second)
                            put("timestamp", item.third)
                        }
                    }
                }
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    // ============================== 电源指令 ==============================

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

    // ============================== 位置数据 ==============================

    fun reportLocation(loc: Location) {
        scope.launch {
            try {
                val msg = buildJsonMessage {
                    put("source", "phone")
                    putJsonObject("data") {
                        put("action", "location")
                        put("lat", loc.latitude)
                        put("lon", loc.longitude)
                        put("timestamp", System.currentTimeMillis())
                        put("speed", loc.speed)
                        put("accuracy", loc.accuracy)
                    }
                }
                sendRaw(msg.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Report location failed", e)
            }
        }
    }

    /**
     * 批量补传本地缓存的位置点（用于离线期间产生的位置，由 LocationService 上传按钮触发）。
     * @param arr JSONArray，元素为 {lat,lon,timestamp,uploaded}
     */
    fun uploadLocationBatch(arr: org.json.JSONArray) {
        scope.launch {
            try {
                val elements = mutableListOf<JsonElement>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    elements.add(buildJsonObject {
                        put("lat", o.getDouble("lat"))
                        put("lon", o.getDouble("lon"))
                        put("timestamp", o.getLong("timestamp"))
                        put("uploaded", o.optBoolean("uploaded", true))
                    })
                }
                val msg = buildJsonMessage {
                    put("source", "phone")
                    putJsonObject("data") {
                        put("action", "location_batch")
                        put("points", JsonArray(elements))
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
        if (list.size > 5000) list.removeAt(0)
        _locationPoints.value = list
    }

    // ============================== 远程控制（归一化坐标→像素坐标）==============================

    /**
     * 将归一化坐标 (0-1) 转为像素坐标并执行触摸操作
     */
    private fun performScreenTouch(normX: Float, normY: Float, op: String) {
        LogUtil.connI("收到屏幕操控指令: op=$op, norm=($normX, $normY)")
        
        val ctx = context ?: run {
            LogUtil.connE("context 为空，无法执行操控")
            return
        }
        
        val metrics = android.util.DisplayMetrics()
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealMetrics(metrics)
        
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        LogUtil.connD("屏幕尺寸: ${screenWidth}x${screenHeight}")
        
        val px = normX * screenWidth
        val py = normY * screenHeight
        LogUtil.connI("归一化坐标 (${normX}, ${normY}) -> 像素坐标 ($px, $py)")
        
        val acc = PhoneHubAccessibilityService.instance
        if (acc == null) {
            LogUtil.connE("无障碍服务未连接！用户需要在 设置→无障碍 中开启 PhoneHub")
            showToast("无障碍服务未开启，无法操控。请在设置→无障碍中开启 PhoneHub")
            return
        }
        LogUtil.connI("无障碍服务已连接，准备执行操作")
        
        when (op) {
            "click" -> {
                LogUtil.connD("执行点击操作")
                acc.performTap(px, py)
            }
            "down" -> {
                _lastTouchDownX = px
                _lastTouchDownY = py
                LogUtil.connD("触摸按下: ($px, $py)")
            }
            "move" -> {
                val lastX = _lastTouchDownX
                val lastY = _lastTouchDownY
                LogUtil.connD("触摸移动: ($lastX,$lastY) -> ($px,$py)")
                if (lastX >= 0 && lastY >= 0) {
                    acc.performSwipe(lastX, lastY, px, py, 50)
                } else {
                    LogUtil.connW("没有有效的按下位置，跳过移动")
                }
                _lastTouchDownX = px
                _lastTouchDownY = py
            }
            "up" -> {
                val lastX = _lastTouchDownX
                val lastY = _lastTouchDownY
                LogUtil.connD("触摸抬起: ($lastX,$lastY) -> ($px,$py)")
                if (lastX >= 0 && lastY >= 0) {
                    val dx = Math.abs(px - lastX)
                    val dy = Math.abs(py - lastY)
                    LogUtil.connD("移动距离: dx=$dx, dy=$dy")
                    if (dx < 10 && dy < 10) {
                        LogUtil.connI("移动距离小于10像素，执行点击")
                        acc.performTap(px, py)
                    } else {
                        LogUtil.connI("移动距离大于10像素，执行滑动")
                        acc.performSwipe(lastX, lastY, px, py, 100)
                    }
                } else {
                    LogUtil.connW("没有有效的按下位置")
                }
                _lastTouchDownX = -1f
                _lastTouchDownY = -1f
            }
            "right" -> {
                LogUtil.connI("执行右键（返回键）操作")
                acc.performBack()
            }
            else -> {
                LogUtil.connW("未知操作类型: $op，默认执行点击")
                acc.performTap(px, py)
            }
        }
        LogUtil.connI("屏幕操控指令执行完毕: $op ($px, $py)")
    }

    // ============================== 通知监听 ===============================

    fun reportNotification(item: NotificationItem) {
        scope.launch {
            _notifications.emit(item)
            val msg = buildJsonMessage {
                put("source", "phone")
                putJsonObject("data") {
                    put("action", "notification")
                    put("package", item.packageName)
                    put("title", item.title)
                    put("text", item.text)
                    put("timestamp", item.timestamp)
                    // save.md 功能10：发送通知的所有功能按钮标题
                    if (item.actions.isNotEmpty()) {
                        putJsonArray("actions") {
                            for (a in item.actions) {
                                addJsonObject {
                                    put("title", a.title)
                                }
                            }
                        }
                    }
                    // 用于通知删除
                    put("sbn_id", item.sbnId)
                    put("sbn_tag", item.sbnTag)
                    put("key", item.key)
                }
            }
            sendRaw(msg.toString())
        }
    }

    // ============================== 剪贴板历史 / 收藏 ==============================

    private fun loadClipboardStore() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val histStr = prefs.getString("clipboard_history", null)
        val favStr = prefs.getString("clipboard_favorites", null)
        try {
            if (histStr != null) {
                val arr = Json.parseToJsonElement(histStr).jsonArray
                _clipboardHistory.value = arr.map { it.jsonObject.toClipboardItem() }
            }
            if (favStr != null) {
                val arr = Json.parseToJsonElement(favStr).jsonArray
                _clipboardFavorites.value = arr.map { it.jsonObject.toClipboardItem() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Load clipboard store failed", e)
        }
    }

    private fun saveClipboardStore() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("clipboard_history", Json.encodeToString(JsonArray.serializer(),
                buildJsonArray { _clipboardHistory.value.forEach { add(it.toJsonObject()) } }))
            putString("clipboard_favorites", Json.encodeToString(JsonArray.serializer(),
                buildJsonArray { _clipboardFavorites.value.forEach { add(it.toJsonObject()) } }))
            apply()
        }
    }

    fun addClipboardHistory(text: String, source: String) {
        val item = ClipboardItem(text, source, System.currentTimeMillis(), false)
        val list = _clipboardHistory.value.toMutableList()
        list.add(0, item)
        while (list.size > CLIPBOARD_HISTORY_MAX) list.removeAt(list.size - 1)
        _clipboardHistory.value = list
        saveClipboardStore()
    }

    fun toggleFavorite(item: ClipboardItem) {
        val item2 = item.copy(favorite = !item.favorite)
        val hist = _clipboardHistory.value.toMutableList()
        val idx = hist.indexOfFirst { it.content == item.content && it.timestamp == item.timestamp }
        if (idx >= 0) {
            hist[idx] = item2
            _clipboardHistory.value = hist
        }
        val fav = _clipboardFavorites.value.toMutableList()
        if (item2.favorite) {
            fav.add(0, item2)
            while (fav.size > CLIPBOARD_FAVORITE_MAX) fav.removeAt(fav.size - 1)
        } else {
            fav.removeAll { it.content == item.content && it.timestamp == item.timestamp }
        }
        _clipboardFavorites.value = fav
        saveClipboardStore()
        // 同步给对端（save.md 功能23）
        val msg = buildJsonMessage {
            put("source", "phone")
            putJsonObject("data") {
                put("action", "clipboard_favorite")
                put("content", item2.content)
                put("favorite", item2.favorite)
            }
        }
        scope.launch { sendRaw(msg.toString()) }
    }

    /**
     * 应用电脑端同步过来的收藏变更（save.md 功能23 双向同步）。
     * 不再回发，避免回环。
     */
    private fun applySyncedFavorite(content: String, favorite: Boolean) {
        val fav = _clipboardFavorites.value.toMutableList()
        if (favorite) {
            if (fav.none { it.content == content }) {
                fav.add(0, ClipboardItem(content, "pc", System.currentTimeMillis(), true))
                while (fav.size > CLIPBOARD_FAVORITE_MAX) fav.removeAt(fav.size - 1)
                _clipboardFavorites.value = fav
                saveClipboardStore()
            }
        } else {
            _clipboardFavorites.value = fav.filterNot { it.content == content }
            saveClipboardStore()
        }
    }

    fun searchClipboardHistory(query: String): List<ClipboardItem> {
        val q = query.lowercase()
        return _clipboardHistory.value.filter { it.content.lowercase().contains(q) }
    }

    fun searchClipboardFavorites(query: String): List<ClipboardItem> {
        val q = query.lowercase()
        return _clipboardFavorites.value.filter { it.content.lowercase().contains(q) }
    }

    private fun sendClipboardHistoryToPc() {
        scope.launch {
            // 增量同步：仅发送最近 50 条，减少网络开销
            val recentItems = _clipboardHistory.value.take(50)
            val arr = buildJsonArray {
                for (item in recentItems) {
                    addJsonObject {
                        put("content", item.content)
                        put("source", item.source)
                        put("timestamp", item.timestamp)
                        put("favorite", item.favorite)
                    }
                }
            }
            val msg = buildJsonMessage {
                put("source", "phone")
                putJsonObject("data") {
                    put("action", "clipboard_history")
                    put("items", arr)
                }
            }
            sendRaw(msg.toString())
        }
    }

    private fun ClipboardItem.toJsonObject(): JsonObject = buildJsonObject {
        put("content", content)
        put("source", source)
        put("timestamp", timestamp)
        put("favorite", favorite)
    }

    private fun JsonObject.toClipboardItem(): ClipboardItem = ClipboardItem(
        content = this["content"]?.jsonPrimitive?.contentOrNull ?: "",
        source = this["source"]?.jsonPrimitive?.contentOrNull ?: "unknown",
        timestamp = this["timestamp"]?.jsonPrimitive?.longOrNull ?: 0L,
        favorite = this["favorite"]?.jsonPrimitive?.booleanOrNull ?: false
    )

    // ============================== IP 缓存 ==============================

    /** 获取文件接收目录（供 MainActivity 检查文件是否存在） */
    fun getReceiveDir(): File? = receiveDir

    private fun cacheIp(ip: String) {
        val ctx = context ?: return
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CACHED_IP, ip).apply()
    }

    fun getCachedIp(): String? {
        val ctx = context ?: return null
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CACHED_IP, null)
    }

    // ============================== 底层发送 ==============================

    private suspend fun sendRaw(payload: String) {
        try {
            when (_currentChannel.value) {
                ChannelType.ADB -> {
                    client?.post("http://127.0.0.1:$connectPort/api/cmd") {
                        contentType(ContentType.Application.Json)
                        setBody(payload)
                    }
                }
                ChannelType.WIFI -> {
                    val ip = pcIp ?: DEFAULT_IP
                    client?.post("http://$ip:$connectPort/api/cmd") {
                        contentType(ContentType.Application.Json)
                        setBody(payload)
                    }
                }
                // ChannelType.PAW -> {  // 【禁止删除】PAW 发送
                //     client?.post("$pawUrl/api/send") {
                //         contentType(ContentType.Application.Json)
                //         setBody(payload)
                //     }
                // }
                else -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendRaw failed", e)
        }
    }

    private fun buildJsonMessage(block: JsonObjectBuilder.() -> Unit): JsonObject {
        return buildJsonObject {
            put("token", secretToken)
            put("activate", "send")
            block()
        }
    }

    // ============================== 自研投屏/音频传输 ==============================

    private fun getBaseUrl(): String {
        val ip = pcIp ?: DEFAULT_IP
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
            conn.outputStream.write(frameData)
            conn.outputStream.flush()
            conn.outputStream.close()
            conn.responseCode  // trigger request
            conn.disconnect()
        } catch (e: Exception) {
            // ignore
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
            conn.outputStream.write(audioData)
            conn.outputStream.flush()
            conn.outputStream.close()
            conn.responseCode
            conn.disconnect()
        } catch (e: Exception) {
            // ignore
        }
    }

    // ============================== PC 文件浏览 ==============================

    fun fetchPcDrives(callback: (List<PcDriveInfo>) -> Unit) {
        scope.launch {
            try {
                val ip = pcIp ?: DEFAULT_IP
                val baseUrl = getBaseUrl()
                val response = client?.get("$baseUrl/api/pc_drives") {
                }
                if (response?.status == HttpStatusCode.OK) {
                    val body = response.bodyAsText()
                    val json = Json.parseToJsonElement(body).jsonObject
                    val drivesArr = json["drives"]?.jsonArray ?: emptyList()
                    val drives = drivesArr.map { d ->
                        val obj = d.jsonObject
                        PcDriveInfo(
                            name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                            label = obj["label"]?.jsonPrimitive?.contentOrNull ?: "",
                            total = obj["total"]?.jsonPrimitive?.longOrNull ?: 0L,
                            used = obj["used"]?.jsonPrimitive?.longOrNull ?: 0L,
                            free = obj["free"]?.jsonPrimitive?.longOrNull ?: 0L
                        )
                    }
                    withContext(Dispatchers.Main) { callback(drives) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchPcDrives failed", e)
            }
        }
    }

    fun fetchPcFiles(path: String, callback: (List<PcFileInfo>, String) -> Unit) {
        scope.launch {
            try {
                val baseUrl = getBaseUrl()
                val response = client?.post("$baseUrl/api/pc_files") {
                    contentType(ContentType.Application.Json)
                    setBody("{\"path\":\"${path.replace("\\", "\\\\")}\"}")
                }
                if (response?.status == HttpStatusCode.OK) {
                    val body = response.bodyAsText()
                    val json = Json.parseToJsonElement(body).jsonObject
                    val filesArr = json["files"]?.jsonArray ?: emptyList()
                    val files = filesArr.map { f ->
                        val obj = f.jsonObject
                        PcFileInfo(
                            name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                            isDir = obj["is_dir"]?.jsonPrimitive?.booleanOrNull ?: false,
                            size = obj["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                            modified = obj["modified"]?.jsonPrimitive?.longOrNull ?: 0L
                        )
                    }
                    withContext(Dispatchers.Main) { callback(files, path) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchPcFiles failed", e)
            }
        }
    }

    data class PcDriveInfo(
        val name: String,
        val label: String,
        val total: Long,
        val used: Long,
        val free: Long
    )

    data class PcFileInfo(
        val name: String,
        val isDir: Boolean,
        val size: Long,
        val modified: Long
    )

    // ============================== 生命周期 ==============================

    fun disconnect() {
        userConnectedIntent = false
        lastPcHeartbeatAt = 0L
        statusJob?.cancel()
        msgPollingJob?.cancel()
        // pawPollingJob?.cancel()  // 【禁止删除】PAW 轮询停止
        statusReportJob?.cancel()
        pawStatusReportJob?.cancel()  // PAW 状态上报
        // Task 10: 分别 cancel 发送和接收 Job
        sendJob?.cancel()
        receiveJob?.cancel()
        ackTracker.clear()
        fileReceiveState.clear()
        _connectionState.value = ConnectionState.DISCONNECTED
        _currentChannel.value = ChannelType.NONE
        _connectionMessage.value = "未连接"
        _fileTransferProgress.value = null
    }

    /**
     * 通过 PAW 中转服务器连接
     */
    fun connectPaw() {
        userConnectedIntent = true
        lastConnectFailReason = null
        _connectionState.value = ConnectionState.CONNECTING
        _connectionMessage.value = "正在通过 PAW 连接..."
        Log.i(TAG, "connectPaw() called")
        // 启动 PAW 通道
        startChannel(ChannelType.PAW)
    }

    fun runOnUiThread(block: () -> Unit) {
        mainHandler.post(block)
    }
}
