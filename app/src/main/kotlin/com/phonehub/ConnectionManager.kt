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
import android.location.Location
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
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpMessagePropertiesKt
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
import okhttp3.internal.ws.RealWebSocket
import org.json.JSONArray
import org.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants
import org.osmdroid.tileprovider.modules.DatabaseFileArchive

class ConnectionManager {
    val ACK_TIMEOUT_MS: private static final long = 10000
    val CHUNK_SIZE: private static final int = 524288
    val CLIPBOARD_FAVORITE_MAX: private static final int = 50
    val CLIPBOARD_HISTORY_MAX: private static final int = 500
    val DEFAULT_IP: private static final String = "192.168.3.9"
    val FILE_TRANSFER_CHANNEL_ID: private static final String = "phonehub_file_transfer"
    val FILE_TRANSFER_NOTIF_ID: private static final int = 88881
    val KEY_CACHED_IP: private static final String = "cached_pc_ip"
    val PREF_NAME: private static final String = "phonehub_prefs"
    val RECONNECT_FAIL_THRESHOLD: private static final int = 3
    val TAG: private static final String = "PhoneHub"
    var activeProjection: private static volatile MediaProjection? = null
    var adbWatchdogJob: private static Job? = null
    var cachedProjectionData: private static volatile Intent? = null
    var cachedProjectionResultCode: private static volatile int? = null
    var client: private static HttpClient? = null
    var context: private static Context? = null
    var currentConn: private static volatile HttpURLConnection? = null
    var fileTransferCancel: private static volatile boolean? = null
    var lastConnectFailReason: private static volatile String? = null
    var lastNotifUpdateMs: private static volatile long? = null
    var lastPcHeartbeatAt: private static volatile long? = null
    var lastReceivedText: private static volatile Pair<String, String>? = null
    var locationStoreDir: private static File? = null
    var msgPollingJob: private static Job? = null
    var pawPollingJob: private static Job? = null
    var pcAudioJob: private static Job? = null
    var pcAudioTrack: private static AudioTrack? = null
    var pcCameraJob: private static Job? = null
    var pcFrameControlMode: private static volatile boolean? = null
    var pcFrameJob: private static Job? = null
    var pcIp: private static String? = null
    var pendingFileTransfer: private static volatile PendingFileTransfer? = null
    var pendingSend: private static volatile PendingSendInfo? = null
    var prevIdle: private static long? = null
    var prevTotal: private static long? = null
    var projectionManager: private static MediaProjectionManager? = null
    var receiveDir: private static File? = null
    var receiveJob: private static Job? = null
    var reconnectFailCount: private static int? = null
    var resumeInfo: private static volatile ResumeInfo? = null
    var sendJob: private static Job? = null
    var statusJob: private static Job? = null
    var statusReportJob: private static Job? = null
    var transferInProgress: private static volatile boolean? = null
    var transferPaused: private static volatile boolean? = null
    var userConnectedIntent: private static volatile boolean? = null
    val INSTANCE: public static final ConnectionManager = new ConnectionManager()
    val DEFAULT_SECRET_TOKEN: private static final String = "541881452418845"
    val secretToken: private static String = DEFAULT_SECRET_TOKEN
    val scope: private static final CoroutineScope = CoroutineScopeKt.CoroutineScope(Dispatchers.getIO().plus(SupervisorKt.SupervisorJob$default((Job) null, 1, (Object) null)))
    val mainHandler: private static final Handler = new Handler(Looper.getMainLooper())
    val _connectionState: private static final MutableStateFlow<ConnectionState> = StateFlowKt.MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: private static final StateFlow<ConnectionState> = _connectionState
    val _currentChannel: private static final MutableStateFlow<ChannelType> = StateFlowKt.MutableStateFlow(ChannelType.NONE)
    val currentChannel: private static final StateFlow<ChannelType> = _currentChannel
    val _phoneMemUsage: private static final MutableStateFlow<Float> = StateFlowKt.MutableStateFlow(Float.valueOf(0.0f))
    val phoneMemUsage: private static final StateFlow<Float> = _phoneMemUsage
    val _connectionMessage: private static final MutableStateFlow<String> = StateFlowKt.MutableStateFlow("未连接")
    val connectionMessage: private static final StateFlow<String> = _connectionMessage
    val _connectionLatency: private static final MutableStateFlow<Long> = StateFlowKt.MutableStateFlow(0L)
    val connectionLatency: private static final StateFlow<Long> = _connectionLatency
    val _receivedText: private static final MutableSharedFlow<Pair<String, String>> = SharedFlowKt.MutableSharedFlow$default(1, 16, null, 4, null)
    val receivedText: private static final SharedFlow<Pair<String, String>> = _receivedText
    val _receivedUrl: private static final MutableSharedFlow<String> = SharedFlowKt.MutableSharedFlow$default(0, 16, null, 5, null)
    val receivedUrl: private static final SharedFlow<String> = _receivedUrl
    val _urlHistorySync: private static final MutableSharedFlow<List<Map<String, Object>>> = SharedFlowKt.MutableSharedFlow$default(0, 4, null, 5, null)
    val urlHistorySync: private static final SharedFlow<List<Map<String, Object>>> = _urlHistorySync
    val _cameraSwitchRequest: private static final MutableSharedFlow<Unit> = SharedFlowKt.MutableSharedFlow$default(0, 4, null, 5, null)
    val cameraSwitchRequest: private static final SharedFlow<Unit> = _cameraSwitchRequest
    val _receivedClipboard: private static final MutableStateFlow<String> = StateFlowKt.MutableStateFlow(null)
    val receivedClipboard: private static final StateFlow<String> = _receivedClipboard
    val _mediaInfo: private static final MutableStateFlow<String> = StateFlowKt.MutableStateFlow("未检测到媒体播放")
    val mediaInfo: private static final StateFlow<String> = _mediaInfo
    val _mediaThumbnail: private static final MutableStateFlow<Array<Byte>> = StateFlowKt.MutableStateFlow(null)
    val mediaThumbnail: private static final StateFlow<Array<Byte>> = _mediaThumbnail
    val _screenshotResult: private static final MutableSharedFlow<String> = SharedFlowKt.MutableSharedFlow$default(0, 4, null, 5, null)
    val screenshotResult: private static final SharedFlow<String> = _screenshotResult
    val _fileTransferProgress: private static final MutableStateFlow<TransferProgress> = StateFlowKt.MutableStateFlow(null)
    val fileTransferProgress: private static final StateFlow<TransferProgress> = _fileTransferProgress
    val _transferCompleted: private static final MutableStateFlow<Boolean> = StateFlowKt.MutableStateFlow(false)
    val transferCompleted: private static final StateFlow<Boolean> = _transferCompleted
    val _completedTransfer: private static final MutableSharedFlow<CompletedTransfer> = SharedFlowKt.MutableSharedFlow$default(0, 4, null, 5, null)
    val completedTransfer: private static final SharedFlow<CompletedTransfer> = _completedTransfer
    val _transferPausedFromPc: private static final MutableStateFlow<Boolean> = StateFlowKt.MutableStateFlow(false)
    val transferPausedFromPc: private static final StateFlow<Boolean> = _transferPausedFromPc
    val _transferCancelledFromPc: private static final MutableSharedFlow<String> = SharedFlowKt.MutableSharedFlow$default(0, 4, null, 5, null)
    val transferCancelledFromPc: private static final SharedFlow<String> = _transferCancelledFromPc
    val _notifications: private static final MutableSharedFlow<NotificationItem> = SharedFlowKt.MutableSharedFlow$default(100, 32, null, 4, null)
    val notifications: private static final SharedFlow<NotificationItem> = _notifications
    val _locationPoints: private static final MutableStateFlow<List<LocationPoint>> = StateFlowKt.MutableStateFlow(CollectionsKt.emptyList())
    val locationPoints: private static final StateFlow<List<LocationPoint>> = _locationPoints
    val _clipboardHistory: private static final MutableStateFlow<List<ClipboardItem>> = StateFlowKt.MutableStateFlow(CollectionsKt.emptyList())
    val clipboardHistory: private static final StateFlow<List<ClipboardItem>> = _clipboardHistory
    val _clipboardFavorites: private static final MutableStateFlow<List<ClipboardItem>> = StateFlowKt.MutableStateFlow(CollectionsKt.emptyList())
    val clipboardFavorites: private static final StateFlow<List<ClipboardItem>> = _clipboardFavorites
    val _pcFrame: private static final MutableSharedFlow<Array<Byte>> = SharedFlowKt.MutableSharedFlow$default(0, 4, null, 5, null)
    val pcFrame: private static final SharedFlow<Array<Byte>> = _pcFrame
    val _pcCursorPos: private static final MutableSharedFlow<Pair<Float, Float>> = SharedFlowKt.MutableSharedFlow$default(0, 4, null, 5, null)
    val pcCursorPos: private static final SharedFlow<Pair<Float, Float>> = _pcCursorPos
    val _pcCameraFrame: private static final MutableSharedFlow<Array<Byte>> = SharedFlowKt.MutableSharedFlow$default(0, 4, null, 5, null)
    val pcCameraFrame: private static final SharedFlow<Array<Byte>> = _pcCameraFrame
    val _lastTouchDownX: private static volatile float = -1.0f
    val _lastTouchDownY: private static volatile float = -1.0f
    val DEFAULT_PORT: private static final int = 58627
    val connectPort: private static int = DEFAULT_PORT
    val lastClipboardContent: private static volatile String = ""
    val fileReceiveState: private static final ConcurrentHashMap<String, FileReceiveState> = new ConcurrentHashMap<>()
    val ackTracker: private static final ConcurrentHashMap<String, Long> = new ConcurrentHashMap<>()
    val pcAudioSampleRate: private static final int = 44100

    public  class WhenMappings {
        public static final  int[] $EnumSwitchMapping$0

        static {
            val iArr: Array<Int> = new int[ChannelType.values().length]
            try {
                iArr[ChannelType.ADB.ordinal()] = 1
                } catch (NoSuchFieldError e) {
                }
            try {
                iArr[ChannelType.WIFI.ordinal()] = 2
                } catch (NoSuchFieldError e2) {
                }
            try {
                iArr[ChannelType.NONE.ordinal()] = 3
                } catch (NoSuchFieldError e3) {
                }
            $EnumSwitchMapping$0 = iArr
            }
        }

    constructor() {
        }

    fun getSecretToken(): String {
        var secretToken: return? = null
        }

    fun loadPawConfig(): Unit {
        secretToken = DEFAULT_SECRET_TOKEN
        val ctx: Context = context
        if (ctx == null) {
            return
            }
        val prefs: SharedPreferences = ctx.getSharedPreferences(PREF_NAME, 0)
        SharedPreferences.Editor editor = prefs.edit()
        if (prefs.contains("paw_token")) {
            editor.remove("paw_token")
            Log.i(TAG, "已清理旧的 paw_token 缓存")
            }
        val cachedToken: String = prefs.getString("cached_token", "")
        val str: String = cachedToken
        if (!(str == null || str.length() == 0) && !Intrinsics.areEqual(cachedToken, DEFAULT_SECRET_TOKEN)) {
            editor.remove("cached_token")
            Log.i(TAG, "已清理旧的 cached_token 缓存: " + cachedToken)
            }
        editor.apply()
        }

    fun getConnectionState(): StateFlow<ConnectionState> {
        var connectionState: return? = null
        }

    fun getCurrentChannel(): StateFlow<ChannelType> {
        var currentChannel: return? = null
        }

    fun getPhoneMemUsage(): StateFlow<Float> {
        var phoneMemUsage: return? = null
        }

    fun getConnectionMessage(): StateFlow<String> {
        var connectionMessage: return? = null
        }

    fun getLastConnectFailReason(): String {
        var lastConnectFailReason: return? = null
        }

    fun getConnectionLatency(): StateFlow<Long> {
        var connectionLatency: return? = null
        }

    fun getReceivedText(): SharedFlow<Pair<String, String>> {
        var receivedText: return? = null
        }

    fun getLastReceivedText(): Pair<String, String> {
        var lastReceivedText: return? = null
        }

    fun getReceivedUrl(): SharedFlow<String> {
        var receivedUrl: return? = null
        }

    fun getUrlHistorySync(): SharedFlow<List<Map<String, Object>>> {
        var urlHistorySync: return? = null
        }

    fun getCameraSwitchRequest(): SharedFlow<Unit> {
        var cameraSwitchRequest: return? = null
        }

    fun getReceivedClipboard(): StateFlow<String> {
        var receivedClipboard: return? = null
        }

    fun getMediaInfo(): StateFlow<String> {
        var mediaInfo: return? = null
        }

    fun getMediaThumbnail(): StateFlow<Array<Byte>> {
        var mediaThumbnail: return? = null
        }

    fun getScreenshotResult(): SharedFlow<String> {
        var screenshotResult: return? = null
        }

    fun getFileTransferProgress(): StateFlow<TransferProgress> {
        var fileTransferProgress: return? = null
        }

    fun getTransferCompleted(): StateFlow<Boolean> {
        var transferCompleted: return? = null
        }

    public static final  class CompletedTransfer {
        var fileName: private final String? = null
        var sending: private final boolean? = null

        public static  CompletedTransfer copy$default(CompletedTransfer completedTransfer, String str, boolean z, int i, Object obj) {
            if ((i & 1) != 0) {
                str = completedTransfer.fileName
                }
            if ((i & 2) != 0) {
                z = completedTransfer.sending
                }
            return completedTransfer.copy(str, z)
            }

        fun getFileName(): String {
            return this.fileName
            }

        fun getSending(): Boolean {
            return this.sending
            }

        fun copy(fileName: String, sending: Boolean): CompletedTransfer {
            Intrinsics.checkNotNullParameter(fileName, "fileName")
            return CompletedTransfer(fileName, sending)
            }

        fun equals(other: Any): Boolean {
            if (this == other) {
                var true: return? = null
                }
            if (!(other is CompletedTransfer)) {
                var false: return? = null
                }
            val completedTransfer: CompletedTransfer = (CompletedTransfer) other
            return Intrinsics.areEqual(this.fileName, completedTransfer.fileName) && this.sending == completedTransfer.sending
            }

        fun hashCode(): Int {
            return (this.fileName.hashCode() * 31) + Boolean.hashCode(this.sending)
            }

        fun toString(): String {
            return "CompletedTransfer(fileName=" + this.fileName + ", sending=" + this.sending + ")"
            }

        fun CompletedTransfer(fileName: String, sending: Boolean): public {
            Intrinsics.checkNotNullParameter(fileName, "fileName")
            this.fileName = fileName
            this.sending = sending
            }

        fun getFileName(): String {
            return this.fileName
            }

        fun getSending(): Boolean {
            return this.sending
            }
        }

    fun getCompletedTransfer(): SharedFlow<CompletedTransfer> {
        var completedTransfer: return? = null
        }

    fun getTransferPausedFromPc(): StateFlow<Boolean> {
        var transferPausedFromPc: return? = null
        }

    fun getTransferCancelledFromPc(): SharedFlow<String> {
        var transferCancelledFromPc: return? = null
        }

    fun getNotifications(): SharedFlow<NotificationItem> {
        var notifications: return? = null
        }

    fun getLocationPoints(): StateFlow<List<LocationPoint>> {
        var locationPoints: return? = null
        }

    fun getClipboardHistory(): StateFlow<List<ClipboardItem>> {
        var clipboardHistory: return? = null
        }

    fun getClipboardFavorites(): StateFlow<List<ClipboardItem>> {
        var clipboardFavorites: return? = null
        }

    fun getPcFrame(): SharedFlow<Array<Byte>> {
        var pcFrame: return? = null
        }

    fun getPcCursorPos(): SharedFlow<Pair<Float, Float>> {
        var pcCursorPos: return? = null
        }

    fun getPcFrameControlMode(): Boolean {
        var pcFrameControlMode: return? = null
        }

    fun setPcFrameControlMode(z: Boolean): Unit {
        pcFrameControlMode = z
        }

    fun getPcCameraFrame(): SharedFlow<Array<Byte>> {
        var pcCameraFrame: return? = null
        }

    fun getUserConnectedIntent(): Boolean {
        var userConnectedIntent: return? = null
        }

    fun setUserConnectedIntent(z: Boolean): Unit {
        userConnectedIntent = z
        }

    fun getLastPcHeartbeatAt(): Long {
        var lastPcHeartbeatAt: return? = null
        }

    fun getTransferInProgress(): Boolean {
        var transferInProgress: return? = null
        }

    fun getPcIp(): String {
        var pcIp: return? = null
        }

    public static final  class PendingSendInfo {
        var deferred: private final CompletableDeferred<Boolean>? = null
        var file: private final File? = null
        var fileId: private final String? = null
        var fileName: private final String? = null
        var fileSize: private final long? = null
        var resolvedName: private final String? = null
        var uri: private final Uri? = null

        fun getFileId(): String {
            return this.fileId
            }

        fun getFileName(): String {
            return this.fileName
            }

        fun getFileSize(): Long {
            return this.fileSize
            }

        fun getFile(): File {
            return this.file
            }

        fun getUri(): Uri {
            return this.uri
            }

        fun getResolvedName(): String {
            return this.resolvedName
            }

        fun component7(): CompletableDeferred<Boolean> {
            return this.deferred
            }

        fun copy(fileId: String, fileName: String, fileSize: Long, file: File, uri: Uri, resolvedName: String, deferred: CompletableDeferred<Boolean>): PendingSendInfo {
            Intrinsics.checkNotNullParameter(fileId, "fileId")
            Intrinsics.checkNotNullParameter(fileName, "fileName")
            Intrinsics.checkNotNullParameter(resolvedName, "resolvedName")
            Intrinsics.checkNotNullParameter(deferred, "deferred")
            return PendingSendInfo(fileId, fileName, fileSize, file, uri, resolvedName, deferred)
            }

        fun equals(other: Any): Boolean {
            if (this == other) {
                var true: return? = null
                }
            if (!(other is PendingSendInfo)) {
                var false: return? = null
                }
            val pendingSendInfo: PendingSendInfo = (PendingSendInfo) other
            return Intrinsics.areEqual(this.fileId, pendingSendInfo.fileId) && Intrinsics.areEqual(this.fileName, pendingSendInfo.fileName) && this.fileSize == pendingSendInfo.fileSize && Intrinsics.areEqual(this.file, pendingSendInfo.file) && Intrinsics.areEqual(this.uri, pendingSendInfo.uri) && Intrinsics.areEqual(this.resolvedName, pendingSendInfo.resolvedName) && Intrinsics.areEqual(this.deferred, pendingSendInfo.deferred)
            }

        fun hashCode(): Int {
            return (((((((((((this.fileId.hashCode() * 31) + this.fileName.hashCode()) * 31) + Long.hashCode(this.fileSize)) * 31) + (this.file == null ? 0 : this.file.hashCode())) * 31) + (this.uri != null ? this.uri.hashCode() : 0)) * 31) + this.resolvedName.hashCode()) * 31) + this.deferred.hashCode()
            }

        fun toString(): String {
            return "PendingSendInfo(fileId=" + this.fileId + ", fileName=" + this.fileName + ", fileSize=" + this.fileSize + ", file=" + this.file + ", uri=" + this.uri + ", resolvedName=" + this.resolvedName + ", deferred=" + this.deferred + ")"
            }

        fun PendingSendInfo(fileId: String, fileName: String, fileSize: Long, file: File, uri: Uri, resolvedName: String, deferred: CompletableDeferred<Boolean>): public {
            Intrinsics.checkNotNullParameter(fileId, "fileId")
            Intrinsics.checkNotNullParameter(fileName, "fileName")
            Intrinsics.checkNotNullParameter(resolvedName, "resolvedName")
            Intrinsics.checkNotNullParameter(deferred, "deferred")
            this.fileId = fileId
            this.fileName = fileName
            this.fileSize = fileSize
            this.file = file
            this.uri = uri
            this.resolvedName = resolvedName
            this.deferred = deferred
            }

        public  PendingSendInfo(String str, String str2, long j, File file, Uri uri, String str3, CompletableDeferred completableDeferred, int i, DefaultConstructorMarker defaultConstructorMarker) {
            this(str, str2, j, (i & 8) != 0 ? null : file, (i & 16) != 0 ? null : uri, (i & 32) != 0 ? "" : str3, completableDeferred)
            }

        fun getFileId(): String {
            return this.fileId
            }

        fun getFileName(): String {
            return this.fileName
            }

        fun getFileSize(): Long {
            return this.fileSize
            }

        fun getFile(): File {
            return this.file
            }

        fun getUri(): Uri {
            return this.uri
            }

        fun getResolvedName(): String {
            return this.resolvedName
            }

        fun getDeferred(): CompletableDeferred<Boolean> {
            return this.deferred
            }
        }

    public static final  class ResumeInfo {
        var file: private final File? = null
        var fileId: private final String? = null
        var fileName: private final String? = null
        var fileSize: private final long? = null
        var resumeOffset: private volatile long? = null
        var uri: private final Uri? = null

        fun getFileId(): String {
            return this.fileId
            }

        fun getFileName(): String {
            return this.fileName
            }

        fun getFileSize(): Long {
            return this.fileSize
            }

        fun getFile(): File {
            return this.file
            }

        fun getUri(): Uri {
            return this.uri
            }

        fun getResumeOffset(): Long {
            return this.resumeOffset
            }

        fun copy(fileId: String, fileName: String, fileSize: Long, file: File, uri: Uri, resumeOffset: Long): ResumeInfo {
            Intrinsics.checkNotNullParameter(fileId, "fileId")
            Intrinsics.checkNotNullParameter(fileName, "fileName")
            return ResumeInfo(fileId, fileName, fileSize, file, uri, resumeOffset)
            }

        fun equals(other: Any): Boolean {
            if (this == other) {
                var true: return? = null
                }
            if (!(other is ResumeInfo)) {
                var false: return? = null
                }
            val resumeInfo: ResumeInfo = (ResumeInfo) other
            return Intrinsics.areEqual(this.fileId, resumeInfo.fileId) && Intrinsics.areEqual(this.fileName, resumeInfo.fileName) && this.fileSize == resumeInfo.fileSize && Intrinsics.areEqual(this.file, resumeInfo.file) && Intrinsics.areEqual(this.uri, resumeInfo.uri) && this.resumeOffset == resumeInfo.resumeOffset
            }

        fun hashCode(): Int {
            return (((((((((this.fileId.hashCode() * 31) + this.fileName.hashCode()) * 31) + Long.hashCode(this.fileSize)) * 31) + (this.file == null ? 0 : this.file.hashCode())) * 31) + (this.uri != null ? this.uri.hashCode() : 0)) * 31) + Long.hashCode(this.resumeOffset)
            }

        fun toString(): String {
            return "ResumeInfo(fileId=" + this.fileId + ", fileName=" + this.fileName + ", fileSize=" + this.fileSize + ", file=" + this.file + ", uri=" + this.uri + ", resumeOffset=" + this.resumeOffset + ")"
            }

        fun ResumeInfo(fileId: String, fileName: String, fileSize: Long, file: File, uri: Uri, resumeOffset: Long): public {
            Intrinsics.checkNotNullParameter(fileId, "fileId")
            Intrinsics.checkNotNullParameter(fileName, "fileName")
            this.fileId = fileId
            this.fileName = fileName
            this.fileSize = fileSize
            this.file = file
            this.uri = uri
            this.resumeOffset = resumeOffset
            }

        public  ResumeInfo(String str, String str2, long j, File file, Uri uri, long j2, int i, DefaultConstructorMarker defaultConstructorMarker) {
            this(str, str2, j, (i & 8) != 0 ? null : file, (i & 16) != 0 ? null : uri, (i & 32) != 0 ? 0L : j2)
            }

        fun getFileId(): String {
            return this.fileId
            }

        fun getFileName(): String {
            return this.fileName
            }

        fun getFileSize(): Long {
            return this.fileSize
            }

        fun getFile(): File {
            return this.file
            }

        fun getUri(): Uri {
            return this.uri
            }

        fun getResumeOffset(): Long {
            return this.resumeOffset
            }

        fun setResumeOffset(j: Long): Unit {
            this.resumeOffset = j
            }
        }

    public static final  class FileReceiveState {
        var fileId: private final String? = null
        var fileName: private final String? = null
        var fileSize: private final long? = null
        var partNum: private volatile int? = null
        var received: private volatile long? = null

        public static  FileReceiveState copy$default(FileReceiveState fileReceiveState, String str, String str2, long j, long j2, int i, int i2, Object obj) {
            if ((i2 & 1) != 0) {
                str = fileReceiveState.fileId
                }
            if ((i2 & 2) != 0) {
                str2 = fileReceiveState.fileName
                }
            val str3: String = str2
            if ((i2 & 4) != 0) {
                j = fileReceiveState.fileSize
                }
            val j3: Long = j
            if ((i2 & 8) != 0) {
                j2 = fileReceiveState.received
                }
            val j4: Long = j2
            if ((i2 & 16) != 0) {
                i = fileReceiveState.partNum
                }
            return fileReceiveState.copy(str, str3, j3, j4, i)
            }

        fun getFileId(): String {
            return this.fileId
            }

        fun getFileName(): String {
            return this.fileName
            }

        fun getFileSize(): Long {
            return this.fileSize
            }

        fun getReceived(): Long {
            return this.received
            }

        fun getPartNum(): Int {
            return this.partNum
            }

        fun copy(fileId: String, fileName: String, fileSize: Long, received: Long, partNum: Int): FileReceiveState {
            Intrinsics.checkNotNullParameter(fileId, "fileId")
            Intrinsics.checkNotNullParameter(fileName, "fileName")
            return FileReceiveState(fileId, fileName, fileSize, received, partNum)
            }

        fun equals(other: Any): Boolean {
            if (this == other) {
                var true: return? = null
                }
            if (!(other is FileReceiveState)) {
                var false: return? = null
                }
            val fileReceiveState: FileReceiveState = (FileReceiveState) other
            return Intrinsics.areEqual(this.fileId, fileReceiveState.fileId) && Intrinsics.areEqual(this.fileName, fileReceiveState.fileName) && this.fileSize == fileReceiveState.fileSize && this.received == fileReceiveState.received && this.partNum == fileReceiveState.partNum
            }

        fun hashCode(): Int {
            return (((((((this.fileId.hashCode() * 31) + this.fileName.hashCode()) * 31) + Long.hashCode(this.fileSize)) * 31) + Long.hashCode(this.received)) * 31) + Integer.hashCode(this.partNum)
            }

        fun toString(): String {
            return "FileReceiveState(fileId=" + this.fileId + ", fileName=" + this.fileName + ", fileSize=" + this.fileSize + ", received=" + this.received + ", partNum=" + this.partNum + ")"
            }

        fun FileReceiveState(fileId: String, fileName: String, fileSize: Long, received: Long, partNum: Int): public {
            Intrinsics.checkNotNullParameter(fileId, "fileId")
            Intrinsics.checkNotNullParameter(fileName, "fileName")
            this.fileId = fileId
            this.fileName = fileName
            this.fileSize = fileSize
            this.received = received
            this.partNum = partNum
            }

        /*
        Code decompiled incorrectly, please refer to instructions dump.
        */
        public  FileReceiveState(String str, String str2, long j, long j2, int i, int i2, DefaultConstructorMarker defaultConstructorMarker) {
            this(str, str2, j, r7, r9)
            var j3: Long? = null
            var i3: Int? = null
            if ((i2 & 8) == 0) {
                j3 = j2
                } else {
                j3 = 0
                }
            if ((i2 & 16) == 0) {
                i3 = i
                } else {
                i3 = 0
                }
            }

        fun getFileId(): String {
            return this.fileId
            }

        fun getFileName(): String {
            return this.fileName
            }

        fun getFileSize(): Long {
            return this.fileSize
            }

        fun getReceived(): Long {
            return this.received
            }

        fun setReceived(j: Long): Unit {
            this.received = j
            }

        fun getPartNum(): Int {
            return this.partNum
            }

        fun setPartNum(i: Int): Unit {
            this.partNum = i
            }
        }

    public static final  class TransferProgress {
        var fileId: private final String? = null
        var fileName: private final String? = null
        var receiving: private final boolean? = null
        var sent: private final long? = null
        var total: private final long? = null

        public static  TransferProgress copy$default(TransferProgress transferProgress, String str, String str2, long j, long j2, boolean z, int i, Object obj) {
            if ((i & 1) != 0) {
                str = transferProgress.fileId
                }
            if ((i & 2) != 0) {
                str2 = transferProgress.fileName
                }
            val str3: String = str2
            if ((i & 4) != 0) {
                j = transferProgress.sent
                }
            val j3: Long = j
            if ((i & 8) != 0) {
                j2 = transferProgress.total
                }
            val j4: Long = j2
            if ((i & 16) != 0) {
                z = transferProgress.receiving
                }
            return transferProgress.copy(str, str3, j3, j4, z)
            }

        fun getFileId(): String {
            return this.fileId
            }

        fun getFileName(): String {
            return this.fileName
            }

        fun getSent(): Long {
            return this.sent
            }

        fun getTotal(): Long {
            return this.total
            }

        fun getReceiving(): Boolean {
            return this.receiving
            }

        fun copy(fileId: String, fileName: String, sent: Long, total: Long, receiving: Boolean): TransferProgress {
            Intrinsics.checkNotNullParameter(fileId, "fileId")
            Intrinsics.checkNotNullParameter(fileName, "fileName")
            return TransferProgress(fileId, fileName, sent, total, receiving)
            }

        fun equals(other: Any): Boolean {
            if (this == other) {
                var true: return? = null
                }
            if (!(other is TransferProgress)) {
                var false: return? = null
                }
            val transferProgress: TransferProgress = (TransferProgress) other
            return Intrinsics.areEqual(this.fileId, transferProgress.fileId) && Intrinsics.areEqual(this.fileName, transferProgress.fileName) && this.sent == transferProgress.sent && this.total == transferProgress.total && this.receiving == transferProgress.receiving
            }

        fun hashCode(): Int {
            return (((((((this.fileId.hashCode() * 31) + this.fileName.hashCode()) * 31) + Long.hashCode(this.sent)) * 31) + Long.hashCode(this.total)) * 31) + Boolean.hashCode(this.receiving)
            }

        fun toString(): String {
            return "TransferProgress(fileId=" + this.fileId + ", fileName=" + this.fileName + ", sent=" + this.sent + ", total=" + this.total + ", receiving=" + this.receiving + ")"
            }

        fun TransferProgress(fileId: String, fileName: String, sent: Long, total: Long, receiving: Boolean): public {
            Intrinsics.checkNotNullParameter(fileId, "fileId")
            Intrinsics.checkNotNullParameter(fileName, "fileName")
            this.fileId = fileId
            this.fileName = fileName
            this.sent = sent
            this.total = total
            this.receiving = receiving
            }

        fun getFileId(): String {
            return this.fileId
            }

        fun getFileName(): String {
            return this.fileName
            }

        fun getSent(): Long {
            return this.sent
            }

        fun getTotal(): Long {
            return this.total
            }

        fun getReceiving(): Boolean {
            return this.receiving
            }
        }

    public static final  class NotificationItem {
        var actions: private final List<NotificationAction>? = null
        var key: private final String? = null
        var packageName: private final String? = null
        var sbnId: private final int? = null
        var sbnTag: private final String? = null
        var text: private final String? = null
        var timestamp: private final long? = null
        var title: private final String? = null

        fun getPackageName(): String {
            return this.packageName
            }

        fun getTitle(): String {
            return this.title
            }

        fun getText(): String {
            return this.text
            }

        fun getTimestamp(): Long {
            return this.timestamp
            }

        fun component5(): List<NotificationAction> {
            return this.actions
            }

        fun getSbnId(): Int {
            return this.sbnId
            }

        fun getSbnTag(): String {
            return this.sbnTag
            }

        fun getKey(): String {
            return this.key
            }

        fun copy(packageName: String, title: String, text: String, timestamp: Long, actions: List<NotificationAction>, sbnId: Int, sbnTag: String, key: String): NotificationItem {
            Intrinsics.checkNotNullParameter(packageName, "packageName")
            Intrinsics.checkNotNullParameter(title, "title")
            Intrinsics.checkNotNullParameter(text, "text")
            Intrinsics.checkNotNullParameter(actions, "actions")
            Intrinsics.checkNotNullParameter(sbnTag, "sbnTag")
            Intrinsics.checkNotNullParameter(key, "key")
            return NotificationItem(packageName, title, text, timestamp, actions, sbnId, sbnTag, key)
            }

        fun equals(other: Any): Boolean {
            if (this == other) {
                var true: return? = null
                }
            if (!(other is NotificationItem)) {
                var false: return? = null
                }
            val notificationItem: NotificationItem = (NotificationItem) other
            return Intrinsics.areEqual(this.packageName, notificationItem.packageName) && Intrinsics.areEqual(this.title, notificationItem.title) && Intrinsics.areEqual(this.text, notificationItem.text) && this.timestamp == notificationItem.timestamp && Intrinsics.areEqual(this.actions, notificationItem.actions) && this.sbnId == notificationItem.sbnId && Intrinsics.areEqual(this.sbnTag, notificationItem.sbnTag) && Intrinsics.areEqual(this.key, notificationItem.key)
            }

        fun hashCode(): Int {
            return (((((((((((((this.packageName.hashCode() * 31) + this.title.hashCode()) * 31) + this.text.hashCode()) * 31) + Long.hashCode(this.timestamp)) * 31) + this.actions.hashCode()) * 31) + Integer.hashCode(this.sbnId)) * 31) + this.sbnTag.hashCode()) * 31) + this.key.hashCode()
            }

        fun toString(): String {
            return "NotificationItem(packageName=" + this.packageName + ", title=" + this.title + ", text=" + this.text + ", timestamp=" + this.timestamp + ", actions=" + this.actions + ", sbnId=" + this.sbnId + ", sbnTag=" + this.sbnTag + ", key=" + this.key + ")"
            }

        fun NotificationItem(packageName: String, title: String, text: String, timestamp: Long, actions: List<NotificationAction>, sbnId: Int, sbnTag: String, key: String): public {
            Intrinsics.checkNotNullParameter(packageName, "packageName")
            Intrinsics.checkNotNullParameter(title, "title")
            Intrinsics.checkNotNullParameter(text, "text")
            Intrinsics.checkNotNullParameter(actions, "actions")
            Intrinsics.checkNotNullParameter(sbnTag, "sbnTag")
            Intrinsics.checkNotNullParameter(key, "key")
            this.packageName = packageName
            this.title = title
            this.text = text
            this.timestamp = timestamp
            this.actions = actions
            this.sbnId = sbnId
            this.sbnTag = sbnTag
            this.key = key
            }

        public  NotificationItem(String str, String str2, String str3, long j, List list, int i, String str4, String str5, int i2, DefaultConstructorMarker defaultConstructorMarker) {
            this(str, str2, str3, j, (i2 & 16) != 0 ? CollectionsKt.emptyList() : list, (i2 & 32) != 0 ? 0 : i, (i2 & 64) != 0 ? "" : str4, (i2 & 128) != 0 ? "" : str5)
            }

        fun getPackageName(): String {
            return this.packageName
            }

        fun getTitle(): String {
            return this.title
            }

        fun getText(): String {
            return this.text
            }

        fun getTimestamp(): Long {
            return this.timestamp
            }

        fun getActions(): List<NotificationAction> {
            return this.actions
            }

        fun getSbnId(): Int {
            return this.sbnId
            }

        fun getSbnTag(): String {
            return this.sbnTag
            }

        fun getKey(): String {
            return this.key
            }
        }

    public static final  class NotificationAction {
        var actionIntent: private final PendingIntent? = null
        var id: private final int? = null
        var pkg: private final String? = null
        var tag: private final String? = null
        var title: private final String? = null

        public static  NotificationAction copy$default(NotificationAction notificationAction, String str, PendingIntent pendingIntent, String str2, String str3, int i, int i2, Object obj) {
            if ((i2 & 1) != 0) {
                str = notificationAction.title
                }
            if ((i2 & 2) != 0) {
                pendingIntent = notificationAction.actionIntent
                }
            val pendingIntent2: PendingIntent = pendingIntent
            if ((i2 & 4) != 0) {
                str2 = notificationAction.pkg
                }
            val str4: String = str2
            if ((i2 & 8) != 0) {
                str3 = notificationAction.tag
                }
            val str5: String = str3
            if ((i2 & 16) != 0) {
                i = notificationAction.id
                }
            return notificationAction.copy(str, pendingIntent2, str4, str5, i)
            }

        fun getTitle(): String {
            return this.title
            }

        fun getActionIntent(): PendingIntent {
            return this.actionIntent
            }

        fun getPkg(): String {
            return this.pkg
            }

        fun getTag(): String {
            return this.tag
            }

        fun getId(): Int {
            return this.id
            }

        fun copy(title: String, actionIntent: PendingIntent, pkg: String, tag: String, id: Int): NotificationAction {
            Intrinsics.checkNotNullParameter(title, "title")
            Intrinsics.checkNotNullParameter(pkg, "pkg")
            Intrinsics.checkNotNullParameter(tag, "tag")
            return NotificationAction(title, actionIntent, pkg, tag, id)
            }

        fun equals(other: Any): Boolean {
            if (this == other) {
                var true: return? = null
                }
            if (!(other is NotificationAction)) {
                var false: return? = null
                }
            val notificationAction: NotificationAction = (NotificationAction) other
            return Intrinsics.areEqual(this.title, notificationAction.title) && Intrinsics.areEqual(this.actionIntent, notificationAction.actionIntent) && Intrinsics.areEqual(this.pkg, notificationAction.pkg) && Intrinsics.areEqual(this.tag, notificationAction.tag) && this.id == notificationAction.id
            }

        fun hashCode(): Int {
            return (((((((this.title.hashCode() * 31) + (this.actionIntent == null ? 0 : this.actionIntent.hashCode())) * 31) + this.pkg.hashCode()) * 31) + this.tag.hashCode()) * 31) + Integer.hashCode(this.id)
            }

        fun toString(): String {
            return "NotificationAction(title=" + this.title + ", actionIntent=" + this.actionIntent + ", pkg=" + this.pkg + ", tag=" + this.tag + ", id=" + this.id + ")"
            }

        fun NotificationAction(title: String, actionIntent: PendingIntent, pkg: String, tag: String, id: Int): public {
            Intrinsics.checkNotNullParameter(title, "title")
            Intrinsics.checkNotNullParameter(pkg, "pkg")
            Intrinsics.checkNotNullParameter(tag, "tag")
            this.title = title
            this.actionIntent = actionIntent
            this.pkg = pkg
            this.tag = tag
            this.id = id
            }

        public  NotificationAction(String str, PendingIntent pendingIntent, String str2, String str3, int i, int i2, DefaultConstructorMarker defaultConstructorMarker) {
            this(str, (i2 & 2) != 0 ? null : pendingIntent, (i2 & 4) != 0 ? "" : str2, (i2 & 8) != 0 ? "" : str3, (i2 & 16) != 0 ? 0 : i)
            }

        fun getTitle(): String {
            return this.title
            }

        fun getActionIntent(): PendingIntent {
            return this.actionIntent
            }

        fun getPkg(): String {
            return this.pkg
            }

        fun getTag(): String {
            return this.tag
            }

        fun getId(): Int {
            return this.id
            }
        }

    public static final  class LocationPoint {
        var lat: private final double? = null
        var lon: private final double? = null
        var timestamp: private final long? = null
        var uploaded: private final boolean? = null

        fun getLat(): Double {
            return this.lat
            }

        fun getLon(): Double {
            return this.lon
            }

        fun getTimestamp(): Long {
            return this.timestamp
            }

        fun getUploaded(): Boolean {
            return this.uploaded
            }

        fun copy(lat: Double, lon: Double, timestamp: Long, uploaded: Boolean): LocationPoint {
            return LocationPoint(lat, lon, timestamp, uploaded)
            }

        fun equals(other: Any): Boolean {
            if (this == other) {
                var true: return? = null
                }
            if (!(other is LocationPoint)) {
                var false: return? = null
                }
            val locationPoint: LocationPoint = (LocationPoint) other
            return Double.compare(this.lat, locationPoint.lat) == 0 && Double.compare(this.lon, locationPoint.lon) == 0 && this.timestamp == locationPoint.timestamp && this.uploaded == locationPoint.uploaded
            }

        fun hashCode(): Int {
            return (((((Double.hashCode(this.lat) * 31) + Double.hashCode(this.lon)) * 31) + Long.hashCode(this.timestamp)) * 31) + Boolean.hashCode(this.uploaded)
            }

        fun toString(): String {
            return "LocationPoint(lat=" + this.lat + ", lon=" + this.lon + ", timestamp=" + this.timestamp + ", uploaded=" + this.uploaded + ")"
            }

        fun LocationPoint(lat: Double, lon: Double, timestamp: Long, uploaded: Boolean): public {
            this.lat = lat
            this.lon = lon
            this.timestamp = timestamp
            this.uploaded = uploaded
            }

        /*
        Code decompiled incorrectly, please refer to instructions dump.
        */
        public  LocationPoint(double d, double d2, long j, boolean z, int i, DefaultConstructorMarker defaultConstructorMarker) {
            this(d, d2, j, r8)
            var z2: Boolean? = null
            if ((i & 8) == 0) {
                z2 = z
                } else {
                z2 = false
                }
            }

        fun getLat(): Double {
            return this.lat
            }

        fun getLon(): Double {
            return this.lon
            }

        fun getTimestamp(): Long {
            return this.timestamp
            }

        fun getUploaded(): Boolean {
            return this.uploaded
            }
        }

    public static final  class ClipboardItem {
        var content: private final String? = null
        var favorite: private final boolean? = null
        var source: private final String? = null
        var timestamp: private final long? = null

        public static  ClipboardItem copy$default(ClipboardItem clipboardItem, String str, String str2, long j, boolean z, int i, Object obj) {
            if ((i & 1) != 0) {
                str = clipboardItem.content
                }
            if ((i & 2) != 0) {
                str2 = clipboardItem.source
                }
            val str3: String = str2
            if ((i & 4) != 0) {
                j = clipboardItem.timestamp
                }
            val j2: Long = j
            if ((i & 8) != 0) {
                z = clipboardItem.favorite
                }
            return clipboardItem.copy(str, str3, j2, z)
            }

        fun getContent(): String {
            return this.content
            }

        fun getSource(): String {
            return this.source
            }

        fun getTimestamp(): Long {
            return this.timestamp
            }

        fun getFavorite(): Boolean {
            return this.favorite
            }

        fun copy(content: String, source: String, timestamp: Long, favorite: Boolean): ClipboardItem {
            Intrinsics.checkNotNullParameter(content, "content")
            Intrinsics.checkNotNullParameter(source, "source")
            return ClipboardItem(content, source, timestamp, favorite)
            }

        fun equals(other: Any): Boolean {
            if (this == other) {
                var true: return? = null
                }
            if (!(other is ClipboardItem)) {
                var false: return? = null
                }
            val clipboardItem: ClipboardItem = (ClipboardItem) other
            return Intrinsics.areEqual(this.content, clipboardItem.content) && Intrinsics.areEqual(this.source, clipboardItem.source) && this.timestamp == clipboardItem.timestamp && this.favorite == clipboardItem.favorite
            }

        fun hashCode(): Int {
            return (((((this.content.hashCode() * 31) + this.source.hashCode()) * 31) + Long.hashCode(this.timestamp)) * 31) + Boolean.hashCode(this.favorite)
            }

        fun toString(): String {
            return "ClipboardItem(content=" + this.content + ", source=" + this.source + ", timestamp=" + this.timestamp + ", favorite=" + this.favorite + ")"
            }

        fun ClipboardItem(content: String, source: String, timestamp: Long, favorite: Boolean): public {
            Intrinsics.checkNotNullParameter(content, "content")
            Intrinsics.checkNotNullParameter(source, "source")
            this.content = content
            this.source = source
            this.timestamp = timestamp
            this.favorite = favorite
            }

        /*
        Code decompiled incorrectly, please refer to instructions dump.
        */
        public  ClipboardItem(String str, String str2, long j, boolean z, int i, DefaultConstructorMarker defaultConstructorMarker) {
            this(str, str2, j, r5)
            var z2: Boolean? = null
            if ((i & 8) == 0) {
                z2 = z
                } else {
                z2 = false
                }
            }

        fun getContent(): String {
            return this.content
            }

        fun getSource(): String {
            return this.source
            }

        fun getTimestamp(): Long {
            return this.timestamp
            }

        fun getFavorite(): Boolean {
            return this.favorite
            }
        }

enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED

        private static final  EnumEntries $ENTRIES = EnumEntriesKt.enumEntries($VALUES)

        fun getEntries(): EnumEntries<ConnectionState> {
            return $ENTRIES
            }
        }

enum class ChannelType {
        NONE(0),
        ADB(30),
        WIFI(20)

        var priority: private final int? = null
        private static final  EnumEntries $ENTRIES = EnumEntriesKt.enumEntries($VALUES)

        val INSTANCE: public static final Companion = new Companion(null)

        ChannelType(int priority) {
            this.priority = priority
            }

        fun getPriority(): Int {
            return this.priority
            }

        public static final class Companion {
            public  Companion(DefaultConstructorMarker defaultConstructorMarker) {
                this()
                }

            fun Companion(): private {
                }

            fun fromName(s: String): ChannelType {
                var str: String? = null
                if (s != null) {
                    str = s.toLowerCase(Locale.ROOT)
                    Intrinsics.checkNotNullExpressionValue(str, "toLowerCase(...)")
                    } else {
                    str = null
                    }
                return Intrinsics.areEqual(str, "adb") ? ChannelType.ADB : Intrinsics.areEqual(str, "wifi") ? ChannelType.WIFI : ChannelType.NONE
                }
            }

        fun getEntries(): EnumEntries<ChannelType> {
            return $ENTRIES
            }
        }

    fun init(ctx: Context): Unit {
        Intrinsics.checkNotNullParameter(ctx, "ctx")
        context = ctx.getApplicationContext()
        loadPawConfig()
        client = HttpClientKt.HttpClient(Android.INSTANCE, Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda34
            override
            fun invoke(obj: Any): Any {
                Unit init$lambda$5
                init$lambda$5 = ConnectionManager.init$lambda$5((HttpClientConfig) obj)
                return init$lambda$5
                }
            })
        val externalFilesDir: File = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (externalFilesDir == null) {
            externalFilesDir = ctx.getFilesDir()
            }
        receiveDir = File(externalFilesDir, TAG)
        val file: File = receiveDir
        if (file != null) {
            file.mkdirs()
            }
        locationStoreDir = File(ctx.getExternalFilesDir(null), "LocationCache")
        val file2: File = locationStoreDir
        if (file2 != null) {
            file2.mkdirs()
            }
        loadClipboardStore()
        startStatusReportLoop()
        startAdbWatchdog()
        BuildersKt.launch$default(scope, null, null, new ConnectionManager$init$2(ctx, null), 3, null)
        }

    public static final Unit init$lambda$5(HttpClientConfig HttpClient) {
        Intrinsics.checkNotNullParameter(HttpClient, "$this$HttpClient")
        HttpClient.engine(Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda38
            override
            fun invoke(obj: Any): Any {
                Unit init$lambda$5$lambda$0
                init$lambda$5$lambda$0 = ConnectionManager.init$lambda$5$lambda$0((AndroidEngineConfig) obj)
                return init$lambda$5$lambda$0
                }
            })
        HttpClient.install(ContentNegotiation.INSTANCE, Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda39
            override
            fun invoke(obj: Any): Any {
                Unit init$lambda$5$lambda$2
                init$lambda$5$lambda$2 = ConnectionManager.init$lambda$5$lambda$2((ContentNegotiation.Config) obj)
                return init$lambda$5$lambda$2
                }
            })
        HttpClient.install(HttpTimeout.INSTANCE, Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda40
            override
            fun invoke(obj: Any): Any {
                Unit init$lambda$5$lambda$3
                init$lambda$5$lambda$3 = ConnectionManager.init$lambda$5$lambda$3((HttpTimeout.HttpTimeoutCapabilityConfiguration) obj)
                return init$lambda$5$lambda$3
                }
            })
        DefaultRequestKt.defaultRequest(HttpClient, Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda41
            override
            fun invoke(obj: Any): Any {
                Unit init$lambda$5$lambda$4
                init$lambda$5$lambda$4 = ConnectionManager.init$lambda$5$lambda$4((DefaultRequest.DefaultRequestBuilder) obj)
                return init$lambda$5$lambda$4
                }
            })
        return Unit.INSTANCE
        }

    public static final Unit init$lambda$5$lambda$0(AndroidEngineConfig engine) {
        Intrinsics.checkNotNullParameter(engine, "$this$engine")
        engine.setConnectTimeout(10000)
        engine.setSocketTimeout(30000)
        return Unit.INSTANCE
        }

    public static final Unit init$lambda$5$lambda$2(ContentNegotiation.Config install) {
        Intrinsics.checkNotNullParameter(install, "$this$install")
        JsonSupportKt.json$default(install, JsonKt.Json$default(null, Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda4
            override
            fun invoke(obj: Any): Any {
                Unit init$lambda$5$lambda$2$lambda$1
                init$lambda$5$lambda$2$lambda$1 = ConnectionManager.init$lambda$5$lambda$2$lambda$1((JsonBuilder) obj)
                return init$lambda$5$lambda$2$lambda$1
                }
            }, 1, null), null, 2, null)
        return Unit.INSTANCE
        }

    public static final Unit init$lambda$5$lambda$2$lambda$1(JsonBuilder Json) {
        Intrinsics.checkNotNullParameter(Json, "$this$Json")
        Json.setIgnoreUnknownKeys(true)
        Json.setLenient(true)
        Json.setEncodeDefaults(true)
        return Unit.INSTANCE
        }

    public static final Unit init$lambda$5$lambda$3(HttpTimeout.HttpTimeoutCapabilityConfiguration install) {
        Intrinsics.checkNotNullParameter(install, "$this$install")
        install.setRequestTimeoutMillis(30000L)
        install.setConnectTimeoutMillis(Long.valueOf(ACK_TIMEOUT_MS))
        return Unit.INSTANCE
        }

    public static final Unit init$lambda$5$lambda$4(DefaultRequest.DefaultRequestBuilder defaultRequest) {
        Intrinsics.checkNotNullParameter(defaultRequest, "$this$defaultRequest")
        UtilsKt.header(defaultRequest, "Authorization", "Bearer " + secretToken)
        return Unit.INSTANCE
        }

    fun hasReceivedPcCpu(): Boolean {
        return userConnectedIntent && lastPcHeartbeatAt > 0
        }

    fun isNotificationListenerEnabled(): Boolean {
        val ctx: Context = context
        if (ctx == null) {
            var false: return? = null
            }
        ctx.getPackageName()
        val flat: String = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners")
        if (flat == null) {
            var false: return? = null
            }
        if (flat.length() == 0) {
            var false: return? = null
            }
        val target: String = new ComponentName(ctx, (Class<?>) NotificationListener.class).flattenToString()
        Intrinsics.checkNotNullExpressionValue(target, "flattenToString(...)")
        Iterable $this$any$iv = StringsKt.split$default((CharSequence) flat, new String[]{":"}, false, 0, 6, (Object) null)
        if (($this$any$iv is Collection) && ((Collection) $this$any$iv).isEmpty()) {
            var false: return? = null
            }
        for (Object element$iv : $this$any$iv) {
            val it: String = (String) element$iv
            if (Intrinsics.areEqual(it, target)) {
                var true: return? = null
                }
            }
        var false: return? = null
        }

    fun requestNotificationListenerRebind(): Unit {
        val ctx: Context = context
        if (ctx == null) {
            return
            }
        try {
            if (!isNotificationListenerEnabled()) {
                return
                }
            if (NotificationListener.INSTANCE.getInstance() == null) {
                Log.i(TAG, "通知权限已开启但服务未运行，引导用户到设置页")
                NotificationListener.INSTANCE.toggleNotificationAccess(ctx)
                }
            val companion: NotificationListener = NotificationListener.INSTANCE.getInstance()
            if (companion != null) {
                companion.reportAllActiveNotifications()
                }
            } catch (Exception e) {
            Log.e(TAG, "requestNotificationListenerRebind failed", e)
            }
        }

    fun openNotificationSettings(ctx: Context): Unit {
        try {
            val intent: Intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            intent.addFlags(268435456)
            ctx.startActivity(intent)
            } catch (Exception e) {
            Log.e(TAG, "openNotificationSettings failed", e)
            }
        }

    public static  Unit connect$default(ConnectionManager connectionManager, String str, int i, String str2, int i2, Object obj) {
        if ((i2 & 1) != 0) {
            str = DEFAULT_IP
            }
        if ((i2 & 2) != 0) {
            i = DEFAULT_PORT
            }
        if ((i2 & 4) != 0) {
            str2 = DEFAULT_SECRET_TOKEN
            }
        connectionManager.connect(str, i, str2)
        }

    fun connect(ip: String, port: Int, token: String): Unit {
        Intrinsics.checkNotNullParameter(ip, "ip")
        Intrinsics.checkNotNullParameter(token, "token")
        userConnectedIntent = true
        lastConnectFailReason = null
        secretToken = token
        _connectionState.setValue(ConnectionState.CONNECTING)
        _connectionMessage.setValue("正在连接...")
        Log.i(TAG, "connect() called with ip=" + ip + ", port=" + port + ", adbAvailable=" + isAdbAvailable())
        pcIp = ip
        connectPort = port
        BuildersKt.launch$default(scope, null, null, new ConnectionManager$connect$1(port, ip, null), 3, null)
        }

    /* JADX WARN: Code restructure failed: missing block: B:32:0x00d4, code lost:

    r0 = move-exception
     */
    /* JADX WARN: Code restructure failed: missing block: B:33:0x00d5, code lost:

    android.util.Log.e(com.phonehub.ConnectionManager.TAG, "Test connection failed to " + r10 + ":" + r9 + ": " + r0.getClass().getSimpleName() + ": " + r0.getMessage(), r0)
    r2 = r0.getMessage()
     */
    /* JADX WARN: Code restructure failed: missing block: B:34:0x011a, code lost:

    if (r2 == null) goto L33
     */
    /* JADX WARN: Code restructure failed: missing block: B:35:0x011c, code lost:

    r2 = r0.getClass().getSimpleName()
     */
    /* JADX WARN: Code restructure failed: missing block: B:36:0x0124, code lost:

    com.phonehub.ConnectionManager.lastConnectFailReason = r2
    r0 = false
     */
    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun testConnection(str: String, i: Int, continuation: Continuation<? super Boolean>): Any {
        ConnectionManager$testConnection$1 connectionManager$testConnection$1
        ConnectionManager$testConnection$1 connectionManager$testConnection$12
        var port: Int? = null
        var ip: String? = null
        var httpResponse: HttpResponse? = null
        var execute: Any? = null
        if (continuation is ConnectionManager$testConnection$1) {
            connectionManager$testConnection$1 = (ConnectionManager$testConnection$1) continuation
            if ((connectionManager$testConnection$1.label & Integer.MIN_VALUE) != 0) {
                connectionManager$testConnection$1.label -= Integer.MIN_VALUE
                connectionManager$testConnection$12 = connectionManager$testConnection$1
                Object $result = connectionManager$testConnection$12.result
                val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
                switch (connectionManager$testConnection$12.label) {
                    case 0:
                    ResultKt.throwOnFailure($result)
                    port = i
                    ip = str
                    HttpClient $this$request$iv$iv$iv$iv = client
                    if ($this$request$iv$iv$iv$iv == null) {
                        httpResponse = null
                        val response: HttpResponse = httpResponse
                        val z: Boolean = Intrinsics.areEqual(response != null ? response.getStatus() : null, HttpStatusCode.INSTANCE.getOK())
                        return Boxing.boxBoolean(z)
                        }
                    String urlString$iv = "http://" + ip + ":" + port + "/api/status"
                    HttpRequestBuilder $this$get_u24lambda_u244$iv = HttpRequestBuilder()
                    HttpRequestKt.url($this$get_u24lambda_u244$iv, urlString$iv)
                    HttpTimeoutKt.timeout($this$get_u24lambda_u244$iv, Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda35
                        override
                        fun invoke(obj: Any): Any {
                            Unit testConnection$lambda$8$lambda$7
                            testConnection$lambda$8$lambda$7 = ConnectionManager.testConnection$lambda$8$lambda$7((HttpTimeout.HttpTimeoutCapabilityConfiguration) obj)
                            return testConnection$lambda$8$lambda$7
                            }
                        })
                    $this$get_u24lambda_u244$iv.setMethod(HttpMethod.INSTANCE.getGet())
                    val httpStatement: HttpStatement = new HttpStatement($this$get_u24lambda_u244$iv, $this$request$iv$iv$iv$iv)
                    connectionManager$testConnection$12.L$0 = this
                    connectionManager$testConnection$12.L$1 = ip
                    connectionManager$testConnection$12.I$0 = port
                    connectionManager$testConnection$12.label = 1
                    execute = httpStatement.execute(connectionManager$testConnection$12)
                    if (execute == coroutine_suspended) {
                        var coroutine_suspended: return? = null
                        }
                    httpResponse = (HttpResponse) execute
                    val response2: HttpResponse = httpResponse
                    val z2: Boolean = Intrinsics.areEqual(response2 != null ? response2.getStatus() : null, HttpStatusCode.INSTANCE.getOK())
                    return Boxing.boxBoolean(z2)
                    case 1:
                    port = connectionManager$testConnection$12.I$0
                    ip = connectionManager$testConnection$12.L$1
                    ResultKt.throwOnFailure($result)
                    execute = $result
                    httpResponse = (HttpResponse) execute
                    val response22: HttpResponse = httpResponse
                    val z22: Boolean = Intrinsics.areEqual(response22 != null ? response22.getStatus() : null, HttpStatusCode.INSTANCE.getOK())
                    return Boxing.boxBoolean(z22)
                    default:
                    throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
                    }
                }
            }
        connectionManager$testConnection$1 = new ConnectionManager$testConnection$1(this, continuation)
        connectionManager$testConnection$12 = connectionManager$testConnection$1
        Object $result2 = connectionManager$testConnection$12.result
        val coroutine_suspended2: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (connectionManager$testConnection$12.label) {
            }
        }

    public static  Object testConnection$default(ConnectionManager connectionManager, String str, int i, Continuation continuation, int i2, Object obj) {
        if ((i2 & 2) != 0) {
            i = DEFAULT_PORT
            }
        return connectionManager.testConnection(str, i, continuation)
        }

    public static final Unit testConnection$lambda$8$lambda$7(HttpTimeout.HttpTimeoutCapabilityConfiguration timeout) {
        Intrinsics.checkNotNullParameter(timeout, "$this$timeout")
        timeout.setRequestTimeoutMillis(Long.valueOf(CoroutineLiveDataKt.DEFAULT_TIMEOUT))
        return Unit.INSTANCE
        }

    fun isAdbAvailable(): Boolean {
        var e: Exception? = null
        val ok: Boolean = true
        try {
            val s: Socket = new Socket()
            s.connect(InetSocketAddress("127.0.0.1", 5037), MaterialCardViewHelper.DEFAULT_FADE_ANIM_DURATION)
            s.close()
            e = 1
            } catch (Exception e2) {
            e = null
            }
        if (e != null) {
            var true: return? = null
            }
        try {
            val url: URL = new URL("http://127.0.0.1:" + connectPort + "/api/status")
            val openConnection: URLConnection = url.openConnection()
            Intrinsics.checkNotNull(openConnection, "null cannot be cast to non-null type java.net.HttpURLConnection")
            val conn: HttpURLConnection = (HttpURLConnection) openConnection
            conn.setConnectTimeout(1000)
            conn.setReadTimeout(1000)
            conn.setRequestProperty("Authorization", "Bearer " + secretToken)
            if (conn.getResponseCode() != 200) {
                ok = false
                }
            conn.disconnect()
            var ok: return? = null
            } catch (Exception e3) {
            var false: return? = null
            }
        }

    fun startAdbWatchdog(): Unit {
        val job: Job = adbWatchdogJob
        if (job != null) {
            Job.DefaultImpls.cancel$default(job, (CancellationException) null, 1, (Object) null)
            }
        adbWatchdogJob = BuildersKt.launch$default(scope, null, null, new ConnectionManager$startAdbWatchdog$1(null), 3, null)
        }

    fun downgradeFromAdb(): Unit {
        BuildersKt.launch$default(scope, null, null, new ConnectionManager$downgradeFromAdb$1(null), 3, null)
        }

    fun startChannel(channel: ChannelType): Unit {
        switch (WhenMappings.$EnumSwitchMapping$0[channel.ordinal()]) {
            case 1:
            _currentChannel.setValue(ChannelType.ADB)
            _connectionState.setValue(ConnectionState.CONNECTED)
            _connectionMessage.setValue("已连接 - USB 数据线")
            startStatusPolling(ChannelType.ADB)
            return
            case 2:
            _currentChannel.setValue(ChannelType.WIFI)
            _connectionState.setValue(ConnectionState.CONNECTED)
            _connectionMessage.setValue("已连接 - WiFi 直连")
            startStatusPolling(ChannelType.WIFI)
            return
            default:
            return
            }
        }

    fun switchChannelImmediate(target: ChannelType): Unit {
        if (transferInProgress) {
            Log.i(TAG, "传输中，暂缓通道切换到 " + target)
            return
            }
        val cur: ChannelType = _currentChannel.getValue()
        if (cur == target) {
            return
            }
        Log.i(TAG, "通道切换: " + cur + " -> " + target)
        val job: Job = statusJob
        if (job != null) {
            Job.DefaultImpls.cancel$default(job, (CancellationException) null, 1, (Object) null)
            }
        val job2: Job = pawPollingJob
        if (job2 != null) {
            Job.DefaultImpls.cancel$default(job2, (CancellationException) null, 1, (Object) null)
            }
        _currentChannel.setValue(target)
        startChannel(target)
        }

    fun startStatusPolling(channel: ChannelType): Unit {
        val job: Job = statusJob
        if (job != null) {
            Job.DefaultImpls.cancel$default(job, (CancellationException) null, 1, (Object) null)
            }
        val job2: Job = msgPollingJob
        if (job2 != null) {
            Job.DefaultImpls.cancel$default(job2, (CancellationException) null, 1, (Object) null)
            }
        statusJob = BuildersKt.launch$default(scope, null, null, new ConnectionManager$startStatusPolling$1(channel, null), 3, null)
        }

    fun startMsgPolling(channel: ChannelType): Unit {
        val job: Job = msgPollingJob
        if (job != null) {
            Job.DefaultImpls.cancel$default(job, (CancellationException) null, 1, (Object) null)
            }
        }

    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun handlePollFailure(channel: ChannelType, scope2: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        ConnectionManager$handlePollFailure$1 connectionManager$handlePollFailure$1
        var connectionManager: ConnectionManager? = null
        var channel2: ChannelType? = null
        var scope3: CoroutineScope? = null
        if (continuation is ConnectionManager$handlePollFailure$1) {
            connectionManager$handlePollFailure$1 = (ConnectionManager$handlePollFailure$1) continuation
            if ((connectionManager$handlePollFailure$1.label & Integer.MIN_VALUE) != 0) {
                connectionManager$handlePollFailure$1.label -= Integer.MIN_VALUE
                Object $result = connectionManager$handlePollFailure$1.result
                val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
                switch (connectionManager$handlePollFailure$1.label) {
                    case 0:
                    ResultKt.throwOnFailure($result)
                    reconnectFailCount++
                    if (reconnectFailCount >= 3) {
                        reconnectFailCount = 0
                        switch (WhenMappings.$EnumSwitchMapping$0[channel.ordinal()]) {
                            case 1:
                            downgradeFromAdb()
                            break
                            case 2:
                            _connectionMessage.setValue("WiFi 重连失败，请检查网络")
                            break
                            }
                        return Unit.INSTANCE
                        }
                    _connectionMessage.setValue("通道 " + channel + " 失败，重试 " + reconnectFailCount + "/3...")
                    connectionManager$handlePollFailure$1.L$0 = this
                    connectionManager$handlePollFailure$1.L$1 = channel
                    connectionManager$handlePollFailure$1.L$2 = scope2
                    connectionManager$handlePollFailure$1.label = 1
                    if (DelayKt.delay(2000L, connectionManager$handlePollFailure$1) == coroutine_suspended) {
                        var coroutine_suspended: return? = null
                        }
                    connectionManager = this
                    channel2 = channel
                    scope3 = scope2
                    if (CoroutineScopeKt.isActive(scope3)) {
                        connectionManager.startStatusPolling(channel2)
                        }
                    return Unit.INSTANCE
                    case 1:
                    scope3 = (CoroutineScope) connectionManager$handlePollFailure$1.L$2
                    channel2 = (ChannelType) connectionManager$handlePollFailure$1.L$1
                    connectionManager = (ConnectionManager) connectionManager$handlePollFailure$1.L$0
                    ResultKt.throwOnFailure($result)
                    if (CoroutineScopeKt.isActive(scope3)) {
                        }
                    return Unit.INSTANCE
                    default:
                    throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
                    }
                }
            }
        connectionManager$handlePollFailure$1 = new ConnectionManager$handlePollFailure$1(this, continuation)
        Object $result2 = connectionManager$handlePollFailure$1.result
        val coroutine_suspended2: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (connectionManager$handlePollFailure$1.label) {
            }
        }

    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun handlePcMessage(msg: JsonObject): Unit {
        var data: JsonObject? = null
        var jsonElement: JsonElement? = null
        var jsonPrimitive: JsonPrimitive? = null
        var action: String? = null
        var str: String? = null
        var primaryClip: ClipData? = null
        ClipData.Item itemAt
        var coerceToText: CharSequence? = null
        var jsonPrimitive2: JsonPrimitive? = null
        var contentOrNull: String? = null
        var jsonPrimitive3: JsonPrimitive? = null
        var booleanOrNull: Boolean? = null
        var jsonPrimitive4: JsonPrimitive? = null
        var contentOrNull2: String? = null
        var jsonElement2: JsonElement? = null
        var jsonPrimitive5: JsonPrimitive? = null
        var floatOrNull: Float? = null
        var jsonPrimitive6: JsonPrimitive? = null
        var floatOrNull2: Float? = null
        var op: String? = null
        var jsonPrimitive7: JsonPrimitive? = null
        var jsonElement3: JsonElement? = null
        var jsonPrimitive8: JsonPrimitive? = null
        var src: String? = null
        var jsonElement4: JsonElement? = null
        var jsonPrimitive9: JsonPrimitive? = null
        var dst: String? = null
        var jsonPrimitive10: JsonPrimitive? = null
        var booleanOrNull2: Boolean? = null
        var jsonPrimitive11: JsonPrimitive? = null
        var booleanOrNull3: Boolean? = null
        var jsonPrimitive12: JsonPrimitive? = null
        var contentOrNull3: String? = null
        var jsonPrimitive13: JsonPrimitive? = null
        var contentOrNull4: String? = null
        var txt: String? = null
        var jsonPrimitive14: JsonPrimitive? = null
        var contentOrNull5: String? = null
        var jsonPrimitive15: JsonPrimitive? = null
        var jsonElement5: JsonElement? = null
        var jsonPrimitive16: JsonPrimitive? = null
        var path: String? = null
        var jsonElement6: JsonElement? = null
        var jsonPrimitive17: JsonPrimitive? = null
        var pkg: String? = null
        var jsonElement7: JsonElement? = null
        var jsonPrimitive18: JsonPrimitive? = null
        var pkg2: String? = null
        var notificationItem: NotificationItem? = null
        var notificationAction: NotificationAction? = null
        var packageManager: PackageManager? = null
        var packageManager2: PackageManager? = null
        var actions: List<NotificationAction>? = null
        var notificationAction2: NotificationAction? = null
        var z: Boolean? = null
        var z2: Boolean? = null
        var jsonPrimitive19: JsonPrimitive? = null
        var contentOrNull6: String? = null
        var jsonElement8: JsonElement? = null
        var jsonPrimitive20: JsonPrimitive? = null
        var pkg3: String? = null
        var path2: String? = null
        var jsonPrimitive21: JsonPrimitive? = null
        var str2: String? = null
        var i: Int? = null
        var r9: ??? = null
        var jsonPrimitive22: JsonPrimitive? = null
        var contentOrNull7: String? = null
        var jsonPrimitive23: JsonPrimitive? = null
        var jsonPrimitive24: JsonPrimitive? = null
        var contentOrNull8: String? = null
        var companion: NotificationListener? = null
        var key: String? = null
        var pkg4: String? = null
        var companion2: NotificationListener? = null
        var jsonPrimitive25: JsonPrimitive? = null
        var intOrNull: Integer? = null
        var jsonPrimitive26: JsonPrimitive? = null
        var contentOrNull9: String? = null
        var jsonPrimitive27: JsonPrimitive? = null
        var jsonPrimitive28: JsonPrimitive? = null
        var fileId: String? = null
        var jsonPrimitive29: JsonPrimitive? = null
        var contentOrNull10: String? = null
        var jsonPrimitive30: JsonPrimitive? = null
        var jsonElement9: JsonElement? = null
        var jsonPrimitive31: JsonPrimitive? = null
        var path3: String? = null
        var jsonPrimitive32: JsonPrimitive? = null
        var booleanOrNull4: Boolean? = null
        var fileName: String? = null
        var jsonPrimitive33: JsonPrimitive? = null
        var contentOrNull11: String? = null
        var jsonPrimitive34: JsonPrimitive? = null
        var longOrNull: Long? = null
        var jsonPrimitive35: JsonPrimitive? = null
        var msg2: String? = null
        var jsonPrimitive36: JsonPrimitive? = null
        var url: String? = null
        var str3: String? = null
        var jsonPrimitive37: JsonPrimitive? = null
        var longOrNull2: Long? = null
        var jsonPrimitive38: JsonPrimitive? = null
        var jsonPrimitive39: JsonPrimitive? = null
        var str4: String? = null
        var str5: String? = null
        var str6: String? = null
        var jsonPrimitive40: JsonPrimitive? = null
        var contentOrNull12: String? = null
        var jsonPrimitive41: JsonPrimitive? = null
        var jsonPrimitive42: JsonPrimitive? = null
        var fileId2: String? = null
        var jsonPrimitive43: JsonPrimitive? = null
        var contentOrNull13: String? = null
        var jsonPrimitive44: JsonPrimitive? = null
        var jsonElement10: JsonElement? = null
        var jsonPrimitive45: JsonPrimitive? = null
        var oldPath: String? = null
        var jsonElement11: JsonElement? = null
        var jsonPrimitive46: JsonPrimitive? = null
        var newPath: String? = null
        var jsonElement12: JsonElement? = null
        var jsonPrimitive47: JsonPrimitive? = null
        var path4: String? = null
        var jsonPrimitive48: JsonPrimitive? = null
        var contentOrNull14: String? = null
        var jsonPrimitive49: JsonPrimitive? = null
        val jsonElement13: JsonElement = (JsonElement) msg.get("data")
        if (jsonElement13 == null || (data = JsonElementKt.getJsonObject(jsonElement13)) == null || (jsonElement = (JsonElement) data.get("action")) == null || (jsonPrimitive = JsonElementKt.getJsonPrimitive(jsonElement)) == null || (action = JsonElementKt.getContentOrNull(jsonPrimitive)) == null) {
            return
            }
        val jsonElement14: JsonElement = (JsonElement) msg.get("source")
        val reason: String = ""
        if (jsonElement14 == null || (jsonPrimitive49 = JsonElementKt.getJsonPrimitive(jsonElement14)) == null || (str = JsonElementKt.getContentOrNull(jsonPrimitive49)) == null) {
            str = ""
            }
        val source: String = str
        val str7: String = null
        switch (action.hashCode()) {
            case -1600397930:
            if (action.equals("clipboard")) {
                if (Intrinsics.areEqual(source, "phone")) {
                    Log.i(TAG, "忽略本机回环剪贴板")
                    return
                    }
                val jsonElement15: JsonElement = (JsonElement) data.get("txt")
                if (jsonElement15 != null && (jsonPrimitive2 = JsonElementKt.getJsonPrimitive(jsonElement15)) != null && (contentOrNull = JsonElementKt.getContentOrNull(jsonPrimitive2)) != null) {
                    reason = contentOrNull
                    }
                if (reason.length() > 0) {
                    try {
                        val context2: Context = context
                        val systemService: Any = context2 != null ? context2.getSystemService("clipboard") : null
                        val cm: ClipboardManager = systemService instanceof ClipboardManager ? (ClipboardManager) systemService : null
                        if (cm != null && (primaryClip = cm.getPrimaryClip()) != null && (itemAt = primaryClip.getItemAt(0)) != null && (coerceToText = itemAt.coerceToText(context)) != null) {
                            str7 = coerceToText.toString()
                            }
                        } catch (Exception e) {
                        }
                    val currentPhoneClip: String = str7
                    if (currentPhoneClip != null && !Intrinsics.areEqual(currentPhoneClip, lastClipboardContent)) {
                        Log.d(TAG, "手机剪贴板有新内容，忽略电脑推送: lastKnown=" + lastClipboardContent + ", current=" + currentPhoneClip)
                        lastClipboardContent = currentPhoneClip
                        return
                        } else {
                        if (Intrinsics.areEqual(reason, lastClipboardContent)) {
                            return
                            }
                        setClipboardContent(reason)
                        _receivedClipboard.setValue(reason)
                        addClipboardHistory(reason, "pc")
                        return
                        }
                    }
                return
                }
            return
            case -1433178642:
            if (action.equals("request_notif_permission")) {
                Log.i(TAG, "收到 request_notif_permission，忽略（需用户手动开启）")
                return
                }
            return
            case -1417935323:
            if (action.equals("clipboard_favorite")) {
                val jsonElement16: JsonElement = (JsonElement) data.get("content")
                if (jsonElement16 != null && (jsonPrimitive4 = JsonElementKt.getJsonPrimitive(jsonElement16)) != null && (contentOrNull2 = JsonElementKt.getContentOrNull(jsonPrimitive4)) != null) {
                    reason = contentOrNull2
                    }
                val content: String = reason
                val jsonElement17: JsonElement = (JsonElement) data.get("favorite")
                val favorite: Boolean = (jsonElement17 == null || (jsonPrimitive3 = JsonElementKt.getJsonPrimitive(jsonElement17)) == null || (booleanOrNull = JsonElementKt.getBooleanOrNull(jsonPrimitive3)) == null) ? false : booleanOrNull.booleanValue()
                if (content.length() > 0) {
                    applySyncedFavorite(content, favorite)
                    return
                    }
                return
                }
            return
            case -1335230036:
            if (!action.equals("screen_touch") || (jsonElement2 = (JsonElement) data.get("x")) == null || (jsonPrimitive5 = JsonElementKt.getJsonPrimitive(jsonElement2)) == null || (floatOrNull = JsonElementKt.getFloatOrNull(jsonPrimitive5)) == null) {
                return
                }
            val x: Float = floatOrNull.floatValue()
            val jsonElement18: JsonElement = (JsonElement) data.get("y")
            if (jsonElement18 == null || (jsonPrimitive6 = JsonElementKt.getJsonPrimitive(jsonElement18)) == null || (floatOrNull2 = JsonElementKt.getFloatOrNull(jsonPrimitive6)) == null) {
                return
                }
            val y: Float = floatOrNull2.floatValue()
            val jsonElement19: JsonElement = (JsonElement) data.get("op")
            if (jsonElement19 == null || (jsonPrimitive7 = JsonElementKt.getJsonPrimitive(jsonElement19)) == null || (op = JsonElementKt.getContentOrNull(jsonPrimitive7)) == null) {
                op = "click"
                }
            performScreenTouch(x, y, op)
            return
            case -1316781992:
            if (!action.equals("file_copy") || (jsonElement3 = (JsonElement) data.get("src")) == null || (jsonPrimitive8 = JsonElementKt.getJsonPrimitive(jsonElement3)) == null || (src = JsonElementKt.getContentOrNull(jsonPrimitive8)) == null || (jsonElement4 = (JsonElement) data.get("dst")) == null || (jsonPrimitive9 = JsonElementKt.getJsonPrimitive(jsonElement4)) == null || (dst = JsonElementKt.getContentOrNull(jsonPrimitive9)) == null) {
                return
                }
            val jsonElement20: JsonElement = (JsonElement) data.get("is_dir")
            val isDir: Boolean = (jsonElement20 == null || (jsonPrimitive10 = JsonElementKt.getJsonPrimitive(jsonElement20)) == null || (booleanOrNull2 = JsonElementKt.getBooleanOrNull(jsonPrimitive10)) == null) ? false : booleanOrNull2.booleanValue()
            handleFileCopy(src, dst, isDir)
            return
            case -1131672178:
            if (action.equals("camera_switch")) {
                BuildersKt.launch$default(scope, null, null, new ConnectionManager$handlePcMessage$5(null), 3, null)
                return
                }
            return
            case -654616394:
            if (action.equals("screenshot_request")) {
                triggerScreenshot()
                return
                }
            return
            case -504306182:
            if (action.equals("open_url")) {
                val jsonElement21: JsonElement = (JsonElement) data.get("url")
                if (jsonElement21 != null && (jsonPrimitive12 = JsonElementKt.getJsonPrimitive(jsonElement21)) != null && (contentOrNull3 = JsonElementKt.getContentOrNull(jsonPrimitive12)) != null) {
                    reason = contentOrNull3
                    }
                val url2: String = reason
                val jsonElement22: JsonElement = (JsonElement) data.get("open_in_via")
                val openInVia: Boolean = (jsonElement22 == null || (jsonPrimitive11 = JsonElementKt.getJsonPrimitive(jsonElement22)) == null || (booleanOrNull3 = JsonElementKt.getBooleanOrNull(jsonPrimitive11)) == null) ? false : booleanOrNull3.booleanValue()
                if (url2.length() > 0) {
                    BuildersKt.launch$default(scope, null, null, new ConnectionManager$handlePcMessage$2(url2, null), 3, null)
                    }
                openUrlOnDevice(url2, openInVia)
                return
                }
            return
            case 98618:
            if (action.equals("cmd")) {
                val jsonElement23: JsonElement = (JsonElement) data.get("cmd")
                if (jsonElement23 != null && (jsonPrimitive13 = JsonElementKt.getJsonPrimitive(jsonElement23)) != null && (contentOrNull4 = JsonElementKt.getContentOrNull(jsonPrimitive13)) != null) {
                    reason = contentOrNull4
                    }
                val cmd: String = reason
                handleCommand(cmd, data)
                return
                }
            return
            case 115312:
            if (action.equals("txt")) {
                val jsonElement24: JsonElement = (JsonElement) data.get("txt")
                if (jsonElement24 == null || (jsonPrimitive15 = JsonElementKt.getJsonPrimitive(jsonElement24)) == null || (txt = JsonElementKt.getContentOrNull(jsonPrimitive15)) == null) {
                    txt = ""
                    }
                val jsonElement25: JsonElement = (JsonElement) data.get(ContentDisposition.Parameters.FileName)
                if (jsonElement25 != null && (jsonPrimitive14 = JsonElementKt.getJsonPrimitive(jsonElement25)) != null && (contentOrNull5 = JsonElementKt.getContentOrNull(jsonPrimitive14)) != null) {
                    reason = contentOrNull5
                    }
                if (txt.length() > 0) {
                    lastReceivedText = new Pair<>(reason, txt)
                    BuildersKt.launch$default(scope, null, null, new ConnectionManager$handlePcMessage$1(reason, txt, null), 3, null)
                    val ctx: Context = context
                    if (ctx == null || isAppInForeground(ctx)) {
                        return
                        }
                    showTextReceivedNotification(ctx, reason, txt)
                    return
                    }
                return
                }
            return
            case 315910691:
            if (!action.equals("send_file_request") || (jsonElement5 = (JsonElement) data.get("path")) == null || (jsonPrimitive16 = JsonElementKt.getJsonPrimitive(jsonElement5)) == null || (path = JsonElementKt.getContentOrNull(jsonPrimitive16)) == null) {
                return
                }
            handleSendFileRequest(path)
            return
            case 581756494:
            if (!action.equals("app_apk_request") || (jsonElement6 = (JsonElement) data.get("package")) == null || (jsonPrimitive17 = JsonElementKt.getJsonPrimitive(jsonElement6)) == null || (pkg = JsonElementKt.getContentOrNull(jsonPrimitive17)) == null) {
                return
                }
            handleAppApkRequest(pkg)
            return
            case 646510924:
            if (action.equals("app_list_request")) {
                handleAppListRequest()
                return
                }
            return
            case 661660682:
            if (!action.equals("notification_action") || (jsonElement7 = (JsonElement) data.get("package")) == null || (jsonPrimitive18 = JsonElementKt.getJsonPrimitive(jsonElement7)) == null || (pkg2 = JsonElementKt.getContentOrNull(jsonPrimitive18)) == null) {
                return
                }
            val jsonElement26: JsonElement = (JsonElement) data.get("action_title")
            if (jsonElement26 != null && (jsonPrimitive19 = JsonElementKt.getJsonPrimitive(jsonElement26)) != null && (contentOrNull6 = JsonElementKt.getContentOrNull(jsonPrimitive19)) != null) {
                reason = contentOrNull6
                }
            val replayCache: List<NotificationItem> = _notifications.getReplayCache()
            val listIterator: ListIterator<NotificationItem> = replayCache.listIterator(replayCache.size())
            while (true) {
                if (listIterator.hasPrevious()) {
                    notificationItem = listIterator.previous()
                    val it: NotificationItem = notificationItem
                    if (Intrinsics.areEqual(it.getPackageName(), pkg2)) {
                        Iterable $this$any$iv = it.getActions()
                        if (($this$any$iv is Collection) && ((Collection) $this$any$iv).isEmpty()) {
                            z2 = false
                            } else {
                            val it2: Iterator = $this$any$iv.iterator()
                            while (true) {
                                if (it2.hasNext()) {
                                    Object element$iv = it2.next()
                                    val a: NotificationAction = (NotificationAction) element$iv
                                    if (Intrinsics.areEqual(a.getTitle(), reason)) {
                                        z2 = true
                                        }
                                    } else {
                                    z2 = false
                                    }
                                }
                            }
                        if (z2) {
                            z = true
                            if (!z) {
                                }
                            }
                        }
                    z = false
                    if (!z) {
                        }
                    } else {
                    notificationItem = null
                    }
                }
            val notif: NotificationItem = notificationItem
            if (notif == null || (actions = notif.getActions()) == null) {
                notificationAction = null
                } else {
                val it3: Iterator = actions.iterator()
                while (true) {
                    if (it3.hasNext()) {
                        notificationAction2 = it3.next()
                        if (Intrinsics.areEqual(((NotificationAction) notificationAction2).getTitle(), reason)) {
                            }
                        } else {
                        notificationAction2 = 0
                        }
                    }
                notificationAction = notificationAction2
                }
            val action2: NotificationAction = notificationAction
            if ((action2 != null ? action2.getActionIntent() : null) == null) {
                Log.w(TAG, "未找到通知快捷操作: pkg=" + pkg2 + " action=" + reason + ", cache size=" + _notifications.getReplayCache().size())
                val context3: Context = context
                val launchIntent: Intent = (context3 == null || (packageManager2 = context3.getPackageManager()) == null) ? null : packageManager2.getLaunchIntentForPackage(pkg2)
                if (launchIntent != null) {
                    launchIntent.addFlags(268435456)
                    val context4: Context = context
                    if (context4 != null) {
                        context4.startActivity(launchIntent)
                        val unit: Unit = Unit.INSTANCE
                        }
                    }
                val unit2: Unit = Unit.INSTANCE
                return
                }
            try {
                action2.getActionIntent().send()
                Integer.valueOf(Log.i(TAG, "通知快捷操作已执行: pkg=" + pkg2 + " action=" + reason))
                return
                } catch (Exception e2) {
                Log.e(TAG, "notification action send failed", e2)
                val context5: Context = context
                val launchIntent2: Intent = (context5 == null || (packageManager = context5.getPackageManager()) == null) ? null : packageManager.getLaunchIntentForPackage(pkg2)
                if (launchIntent2 != null) {
                    launchIntent2.addFlags(268435456)
                    val context6: Context = context
                    if (context6 != null) {
                        context6.startActivity(launchIntent2)
                        val unit3: Unit = Unit.INSTANCE
                        }
                    }
                val unit4: Unit = Unit.INSTANCE
                return
                }
            case 835141012:
            if (!action.equals("app_uninstall_request") || (jsonElement8 = (JsonElement) data.get("package")) == null || (jsonPrimitive20 = JsonElementKt.getJsonPrimitive(jsonElement8)) == null || (pkg3 = JsonElementKt.getContentOrNull(jsonPrimitive20)) == null) {
                return
                }
            handleAppUninstallRequest(pkg3)
            return
            case 986778065:
            if (action.equals("file_list_request")) {
                val jsonElement27: JsonElement = (JsonElement) data.get("path")
                if (jsonElement27 == null || (jsonPrimitive21 = JsonElementKt.getJsonPrimitive(jsonElement27)) == null || (path2 = JsonElementKt.getContentOrNull(jsonPrimitive21)) == null) {
                    path2 = "/"
                    }
                handleFileListRequest(path2)
                return
                }
            return
            case 1070246153:
            if (action.equals("transfer_control")) {
                val jsonElement28: JsonElement = (JsonElement) data.get("ctrl")
                if (jsonElement28 == null || (jsonPrimitive23 = JsonElementKt.getJsonPrimitive(jsonElement28)) == null || (str2 = JsonElementKt.getContentOrNull(jsonPrimitive23)) == null) {
                    str2 = ""
                    }
                val ctrl: String = str2
                val jsonElement29: JsonElement = (JsonElement) data.get("file_id")
                if (jsonElement29 != null && (jsonPrimitive22 = JsonElementKt.getJsonPrimitive(jsonElement29)) != null && (contentOrNull7 = JsonElementKt.getContentOrNull(jsonPrimitive22)) != null) {
                    reason = contentOrNull7
                    }
                Log.i(TAG, "收到 transfer_control: ctrl=" + ctrl + ", fileId=" + reason)
                switch (ctrl.hashCode()) {
                    case -1367724422:
                    if (ctrl.equals("cancel")) {
                        fileTransferCancel = true
                        transferPaused = false
                        _transferPausedFromPc.setValue(false)
                        try {
                            val httpURLConnection: HttpURLConnection = currentConn
                            if (httpURLConnection != null) {
                                httpURLConnection.disconnect()
                                val unit5: Unit = Unit.INSTANCE
                                }
                            } catch (Exception e3) {
                            }
                        val job: Job = sendJob
                        if (job != null) {
                            i = 1
                            r9 = 0
                            Job.DefaultImpls.cancel$default(job, (CancellationException) null, 1, (Object) null)
                            val unit6: Unit = Unit.INSTANCE
                            } else {
                            i = 1
                            r9 = 0
                            }
                        val job2: Job = receiveJob
                        if (job2 != null) {
                            Job.DefaultImpls.cancel$default(job2, (CancellationException) r9, i, (Object) r9)
                            val unit7: Unit = Unit.INSTANCE
                            }
                        transferInProgress = false
                        resumeInfo = r9
                        pendingSend = r9
                        _fileTransferProgress.setValue(r9)
                        cancelFileTransferNotification()
                        BuildersKt.launch$default(scope, null, null, new ConnectionManager$handlePcMessage$8(reason, r9), 3, null)
                        return
                        }
                    return
                    case -934426579:
                    if (ctrl.equals("resume")) {
                        transferPaused = false
                        fileTransferCancel = false
                        val info: ResumeInfo = resumeInfo
                        if (info != null) {
                            Log.i(TAG, "transfer_control resume: 断点续传 offset=" + info.getResumeOffset())
                            BuildersKt.launch$default(scope, null, null, new ConnectionManager$handlePcMessage$7(info, null), 3, null)
                            } else {
                            Integer.valueOf(Log.w(TAG, "transfer_control resume: 没有 resumeInfo，无法恢复"))
                            }
                        _transferPausedFromPc.setValue(false)
                        return
                        }
                    return
                    case 106440182:
                    if (ctrl.equals("pause")) {
                        fileTransferCancel = true
                        transferPaused = true
                        try {
                            val httpURLConnection2: HttpURLConnection = currentConn
                            if (httpURLConnection2 != null) {
                                httpURLConnection2.disconnect()
                                val unit8: Unit = Unit.INSTANCE
                                }
                            } catch (Exception e4) {
                            }
                        _transferPausedFromPc.setValue(true)
                        return
                        }
                    return
                    default:
                    return
                    }
                }
            return
            case 1127274652:
            if (action.equals("file_complete")) {
                val jsonElement30: JsonElement = (JsonElement) data.get("file_id")
                if (jsonElement30 != null && (jsonPrimitive24 = JsonElementKt.getJsonPrimitive(jsonElement30)) != null && (contentOrNull8 = JsonElementKt.getContentOrNull(jsonPrimitive24)) != null) {
                    reason = contentOrNull8
                    }
                val fileId3: String = reason
                ackTracker.remove(fileId3)
                completeFileReceive(fileId3)
                return
                }
            return
            case 1249624248:
            if (action.equals("get_active_notifications") && (companion = NotificationListener.INSTANCE.getInstance()) != null) {
                companion.reportAllActiveNotifications()
                val unit9: Unit = Unit.INSTANCE
                return
                }
            return
            case 1271700112:
            if (action.equals("cancel_notification")) {
                val jsonElement31: JsonElement = (JsonElement) data.get(DatabaseFileArchive.COLUMN_KEY)
                if (jsonElement31 == null || (jsonPrimitive28 = JsonElementKt.getJsonPrimitive(jsonElement31)) == null || (key = JsonElementKt.getContentOrNull(jsonPrimitive28)) == null) {
                    key = ""
                    }
                val jsonElement32: JsonElement = (JsonElement) data.get("pkg")
                if (jsonElement32 == null || (jsonPrimitive27 = JsonElementKt.getJsonPrimitive(jsonElement32)) == null || (pkg4 = JsonElementKt.getContentOrNull(jsonPrimitive27)) == null) {
                    pkg4 = ""
                    }
                val jsonElement33: JsonElement = (JsonElement) data.get("tag")
                if (jsonElement33 != null && (jsonPrimitive26 = JsonElementKt.getJsonPrimitive(jsonElement33)) != null && (contentOrNull9 = JsonElementKt.getContentOrNull(jsonPrimitive26)) != null) {
                    reason = contentOrNull9
                    }
                val jsonElement34: JsonElement = (JsonElement) data.get("id")
                val id: Int = (jsonElement34 == null || (jsonPrimitive25 = JsonElementKt.getJsonPrimitive(jsonElement34)) == null || (intOrNull = JsonElementKt.getIntOrNull(jsonPrimitive25)) == null) ? 0 : intOrNull.intValue()
                if (key.length() > 0) {
                    val companion3: NotificationListener = NotificationListener.INSTANCE.getInstance()
                    if (companion3 != null) {
                        companion3.cancelNotificationByKey(key)
                        val unit10: Unit = Unit.INSTANCE
                        return
                        }
                    return
                    }
                if (!(pkg4.length() > 0) || (companion2 = NotificationListener.INSTANCE.getInstance()) == null) {
                    return
                    }
                companion2.cancelNotificationByKey(pkg4 + "|" + reason + "|" + id)
                val unit11: Unit = Unit.INSTANCE
                return
                }
            return
            case 1519114539:
            if (action.equals("file_accept")) {
                val jsonElement35: JsonElement = (JsonElement) data.get("file_id")
                if (jsonElement35 == null || (jsonPrimitive30 = JsonElementKt.getJsonPrimitive(jsonElement35)) == null || (fileId = JsonElementKt.getContentOrNull(jsonPrimitive30)) == null) {
                    fileId = ""
                    }
                val jsonElement36: JsonElement = (JsonElement) data.get("resolved_name")
                if (jsonElement36 != null && (jsonPrimitive29 = JsonElementKt.getJsonPrimitive(jsonElement36)) != null && (contentOrNull10 = JsonElementKt.getContentOrNull(jsonPrimitive29)) != null) {
                    reason = contentOrNull10
                    }
                Log.i(TAG, "收到 file_accept: fileId=" + fileId + ", resolvedName=" + reason)
                val pending: PendingSendInfo = pendingSend
                if (pending == null || !Intrinsics.areEqual(pending.getFileId(), fileId)) {
                    Integer.valueOf(Log.w(TAG, "file_accept: 没有匹配的 pendingSend (fileId=" + fileId + ")"))
                    return
                    } else {
                    Boolean.valueOf(pending.getDeferred().complete(true))
                    return
                    }
                }
            return
            case 1607117262:
            if (!action.equals("file_delete") || (jsonElement9 = (JsonElement) data.get("path")) == null || (jsonPrimitive31 = JsonElementKt.getJsonPrimitive(jsonElement9)) == null || (path3 = JsonElementKt.getContentOrNull(jsonPrimitive31)) == null) {
                return
                }
            val jsonElement37: JsonElement = (JsonElement) data.get("is_dir")
            val isDir2: Boolean = (jsonElement37 == null || (jsonPrimitive32 = JsonElementKt.getJsonPrimitive(jsonElement37)) == null || (booleanOrNull4 = JsonElementKt.getBooleanOrNull(jsonPrimitive32)) == null) ? false : booleanOrNull4.booleanValue()
            handleFileDelete(path3, isDir2)
            return
            case 1623498444:
            if (action.equals("send_file_head")) {
                val jsonElement38: JsonElement = (JsonElement) data.get(FileTransferReceiver.EXTRA_FILE_NAME)
                if (jsonElement38 == null || (jsonPrimitive35 = JsonElementKt.getJsonPrimitive(jsonElement38)) == null || (fileName = JsonElementKt.getContentOrNull(jsonPrimitive35)) == null) {
                    fileName = EnvironmentCompat.MEDIA_UNKNOWN
                    }
                val jsonElement39: JsonElement = (JsonElement) data.get(FileTransferReceiver.EXTRA_FILE_SIZE)
                val fileSize: Long = (jsonElement39 == null || (jsonPrimitive34 = JsonElementKt.getJsonPrimitive(jsonElement39)) == null || (longOrNull = JsonElementKt.getLongOrNull(jsonPrimitive34)) == null) ? 0L : longOrNull.longValue()
                val jsonElement40: JsonElement = (JsonElement) data.get("file_id")
                if (jsonElement40 != null && (jsonPrimitive33 = JsonElementKt.getJsonPrimitive(jsonElement40)) != null && (contentOrNull11 = JsonElementKt.getContentOrNull(jsonPrimitive33)) != null) {
                    reason = contentOrNull11
                    }
                Log.i(TAG, "收到send_file_head: name=" + fileName + ", size=" + fileSize + ", id=" + reason + ", channel=" + _currentChannel.getValue())
                startReceiveFile(reason, fileName, fileSize)
                return
                }
            return
            case 1787834990:
            if (action.equals("screenshot_saved")) {
                val jsonElement41: JsonElement = (JsonElement) data.get("message")
                if (jsonElement41 == null || (jsonPrimitive36 = JsonElementKt.getJsonPrimitive(jsonElement41)) == null || (msg2 = JsonElementKt.getContentOrNull(jsonPrimitive36)) == null) {
                    msg2 = "截图已保存到电脑"
                    }
                BuildersKt.launch$default(scope, null, null, new ConnectionManager$handlePcMessage$4(msg2, null), 3, null)
                return
                }
            return
            case 1922882614:
            if (action.equals("url_history_sync")) {
                val jsonElement42: JsonElement = (JsonElement) data.get("history")
                val historyArr: JsonArray = jsonElement42 != null ? JsonElementKt.getJsonArray(jsonElement42) : null
                if (historyArr != null) {
                    val historyList: List = new ArrayList()
                    val it4: Iterator<JsonElement> = historyArr.iterator()
                    while (it4.hasNext()) {
                        val item: JsonElement = it4.next()
                        val obj: JsonObject = JsonElementKt.getJsonObject(item)
                        val jsonElement43: JsonElement = (JsonElement) obj.get((Object) "url")
                        if (jsonElement43 == null || (jsonPrimitive39 = JsonElementKt.getJsonPrimitive(jsonElement43)) == null || (url = JsonElementKt.getContentOrNull(jsonPrimitive39)) == null) {
                            url = ""
                            }
                        val jsonElement44: JsonElement = (JsonElement) obj.get((Object) "direction")
                        if (jsonElement44 == null || (jsonPrimitive38 = JsonElementKt.getJsonPrimitive(jsonElement44)) == null || (str3 = JsonElementKt.getContentOrNull(jsonPrimitive38)) == null) {
                            str3 = ""
                            }
                        val direction: String = str3
                        val jsonElement45: JsonElement = (JsonElement) obj.get((Object) "timestamp")
                        val timestamp: Long = (jsonElement45 == null || (jsonPrimitive37 = JsonElementKt.getJsonPrimitive(jsonElement45)) == null || (longOrNull2 = JsonElementKt.getLongOrNull(jsonPrimitive37)) == null) ? 0L : longOrNull2.longValue()
                        if (url.length() > 0 ? true : r7) {
                            historyList.add(MapsKt.mapOf(TuplesKt.to("url", url), TuplesKt.to("direction", direction), TuplesKt.to("timestamp", Long.valueOf(timestamp))))
                            historyArr = historyArr
                            r7 = false
                            } else {
                            historyArr = historyArr
                            r7 = false
                            }
                        }
                    if (!historyList.isEmpty()) {
                        BuildersKt.launch$default(scope, null, null, new ConnectionManager$handlePcMessage$3(historyList, null), 3, null)
                        return
                        }
                    return
                    }
                return
                }
            return
            case 1939536937:
            if (action.equals("media_info")) {
                val jsonElement46: JsonElement = (JsonElement) data.get(LinkHeader.Parameters.Title)
                if (jsonElement46 == null || (jsonPrimitive42 = JsonElementKt.getJsonPrimitive(jsonElement46)) == null || (str4 = JsonElementKt.getContentOrNull(jsonPrimitive42)) == null) {
                    str4 = ""
                    }
                val title: String = str4
                val jsonElement47: JsonElement = (JsonElement) data.get("artist")
                if (jsonElement47 == null || (jsonPrimitive41 = JsonElementKt.getJsonPrimitive(jsonElement47)) == null || (str5 = JsonElementKt.getContentOrNull(jsonPrimitive41)) == null) {
                    str5 = ""
                    }
                val artist: String = str5
                val jsonElement48: JsonElement = (JsonElement) data.get("thumbnail")
                if (jsonElement48 != null && (jsonPrimitive40 = JsonElementKt.getJsonPrimitive(jsonElement48)) != null && (contentOrNull12 = JsonElementKt.getContentOrNull(jsonPrimitive40)) != null) {
                    reason = contentOrNull12
                    }
                val mutableStateFlow: MutableStateFlow<String> = _mediaInfo
                if (artist.length() > 0) {
                    str6 = title + " - " + artist
                    } else {
                    val str8: String = title
                    if (str8.length() == 0) {
                        str8 = "未检测到媒体播放"
                        }
                    str6 = str8
                    }
                mutableStateFlow.setValue(str6)
                if (!(reason.length() > 0)) {
                    _mediaThumbnail.setValue(null)
                    return
                    }
                try {
                    _mediaThumbnail.setValue(Base64.decode(reason, 0))
                    return
                    } catch (Exception e5) {
                    _mediaThumbnail.setValue(null)
                    return
                    }
                }
            return
            case 2007865282:
            if (action.equals("file_reject")) {
                val jsonElement49: JsonElement = (JsonElement) data.get("file_id")
                if (jsonElement49 == null || (jsonPrimitive44 = JsonElementKt.getJsonPrimitive(jsonElement49)) == null || (fileId2 = JsonElementKt.getContentOrNull(jsonPrimitive44)) == null) {
                    fileId2 = ""
                    }
                val jsonElement50: JsonElement = (JsonElement) data.get("reason")
                if (jsonElement50 != null && (jsonPrimitive43 = JsonElementKt.getJsonPrimitive(jsonElement50)) != null && (contentOrNull13 = JsonElementKt.getContentOrNull(jsonPrimitive43)) != null) {
                    reason = contentOrNull13
                    }
                Log.i(TAG, "收到 file_reject: fileId=" + fileId2 + ", reason=" + reason)
                val pending2: PendingSendInfo = pendingSend
                if (pending2 != null && Intrinsics.areEqual(pending2.getFileId(), fileId2)) {
                    pending2.getDeferred().complete(false)
                    }
                transferInProgress = false
                _fileTransferProgress.setValue(null)
                return
                }
            return
            case 2007980897:
            if (!action.equals("file_rename") || (jsonElement10 = (JsonElement) data.get("old_path")) == null || (jsonPrimitive45 = JsonElementKt.getJsonPrimitive(jsonElement10)) == null || (oldPath = JsonElementKt.getContentOrNull(jsonPrimitive45)) == null || (jsonElement11 = (JsonElement) data.get("new_path")) == null || (jsonPrimitive46 = JsonElementKt.getJsonPrimitive(jsonElement11)) == null || (newPath = JsonElementKt.getContentOrNull(jsonPrimitive46)) == null) {
                return
                }
            handleFileRename(oldPath, newPath)
            return
            case 2138535340:
            if (!action.equals("file_mkdir") || (jsonElement12 = (JsonElement) data.get("path")) == null || (jsonPrimitive47 = JsonElementKt.getJsonPrimitive(jsonElement12)) == null || (path4 = JsonElementKt.getContentOrNull(jsonPrimitive47)) == null) {
                return
                }
            handleFileMkdir(path4)
            return
            case 2143848824:
            if (action.equals("install_apk")) {
                val jsonElement51: JsonElement = (JsonElement) data.get("path")
                if (jsonElement51 != null && (jsonPrimitive48 = JsonElementKt.getJsonPrimitive(jsonElement51)) != null && (contentOrNull14 = JsonElementKt.getContentOrNull(jsonPrimitive48)) != null) {
                    reason = contentOrNull14
                    }
                val path5: String = reason
                if (path5.length() > 0) {
                    autoInstallApk(path5)
                    return
                    }
                return
                }
            return
            default:
            return
            }
        }

    fun channelName(c: ChannelType): String {
        switch (WhenMappings.$EnumSwitchMapping$0[c.ordinal()]) {
            case 1:
            return "USB 数据线"
            case 2:
            return "WiFi 直连"
            case 3:
            return "无"
            default:
            throw NoWhenBranchMatchedException()
            }
        }

    fun handleCommand(cmd: String, data: JsonObject): Unit {
        var jsonElement: JsonElement? = null
        var jsonPrimitive: JsonPrimitive? = null
        var floatOrNull: Float? = null
        var jsonPrimitive2: JsonPrimitive? = null
        var floatOrNull2: Float? = null
        var str: String? = null
        var jsonPrimitive3: JsonPrimitive? = null
        var companion: PhoneHubAccessibilityService? = null
        var jsonElement2: JsonElement? = null
        var jsonPrimitive4: JsonPrimitive? = null
        var intOrNull: Integer? = null
        var jsonElement3: JsonElement? = null
        var jsonPrimitive5: JsonPrimitive? = null
        var contentOrNull: String? = null
        var packageManager: PackageManager? = null
        var context2: Context? = null
        PowerManager.WakeLock newWakeLock
        var jsonPrimitive6: JsonPrimitive? = null
        var booleanOrNull: Boolean? = null
        var jsonElement4: JsonElement? = null
        var jsonPrimitive7: JsonPrimitive? = null
        var contentOrNull2: String? = null
        var emptyList: ArrayList? = null
        var jsonArray: JsonArray? = null
        var companion2: PhoneHubAccessibilityService? = null
        var companion3: PhoneHubAccessibilityService? = null
        var companion4: PhoneHubAccessibilityService? = null
        var jsonPrimitive8: JsonPrimitive? = null
        var contentOrNull3: String? = null
        var companion5: PhoneHubAccessibilityService? = null
        var companion6: PhoneHubAccessibilityService? = null
        val intent: Intent = null
        intent = null
        val str2: String = ""
        switch (cmd.hashCode()) {
            case -1890344818:
            if (!cmd.equals("vol_down")) {
                return
                }
            break
            case -1890071035:
            if (!cmd.equals("vol_mute")) {
                return
                }
            break
            case -1351030795:
            if (!cmd.equals("screen_click") || (jsonElement = (JsonElement) data.get("x")) == null || (jsonPrimitive = JsonElementKt.getJsonPrimitive(jsonElement)) == null || (floatOrNull = JsonElementKt.getFloatOrNull(jsonPrimitive)) == null) {
                return
                }
            val floatValue: Float = floatOrNull.floatValue()
            val jsonElement5: JsonElement = (JsonElement) data.get("y")
            if (jsonElement5 == null || (jsonPrimitive2 = JsonElementKt.getJsonPrimitive(jsonElement5)) == null || (floatOrNull2 = JsonElementKt.getFloatOrNull(jsonPrimitive2)) == null) {
                return
                }
            val floatValue2: Float = floatOrNull2.floatValue()
            val jsonElement6: JsonElement = (JsonElement) data.get("op")
            if (jsonElement6 == null || (jsonPrimitive3 = JsonElementKt.getJsonPrimitive(jsonElement6)) == null || (str = JsonElementKt.getContentOrNull(jsonPrimitive3)) == null) {
                str = "click"
                }
            performScreenTouch(floatValue, floatValue2, str)
            return
            case -1089662601:
            if (cmd.equals("control_center") && (companion = PhoneHubAccessibilityService.INSTANCE.getInstance()) != null) {
                companion.openQuickSettings()
                return
                }
            return
            case -1088367209:
            if (!cmd.equals("set_volume") || (jsonElement2 = (JsonElement) data.get("volume")) == null || (jsonPrimitive4 = JsonElementKt.getJsonPrimitive(jsonElement2)) == null || (intOrNull = JsonElementKt.getIntOrNull(jsonPrimitive4)) == null) {
                return
                }
            setVolume(intOrNull.intValue())
            return
            case -810904185:
            if (!cmd.equals("vol_up")) {
                return
                }
            break
            case -590791992:
            if (cmd.equals("camera_start")) {
                val intent2: Intent = new Intent("android.media.action.STILL_IMAGE_CAMERA")
                intent2.setFlags(268435456)
                val context3: Context = context
                if (context3 != null) {
                    context3.startActivity(intent2)
                    return
                    }
                return
                }
            return
            case -504325460:
            if (!cmd.equals("open_app") || (jsonElement3 = (JsonElement) data.get("package")) == null || (jsonPrimitive5 = JsonElementKt.getJsonPrimitive(jsonElement3)) == null || (contentOrNull = JsonElementKt.getContentOrNull(jsonPrimitive5)) == null) {
                return
                }
            val context4: Context = context
            if (context4 != null && (packageManager = context4.getPackageManager()) != null) {
                intent = packageManager.getLaunchIntentForPackage(contentOrNull)
                }
            if (intent != null) {
                intent.addFlags(268435456)
                val context5: Context = context
                if (context5 != null) {
                    context5.startActivity(intent)
                    return
                    }
                return
                }
            return
            case -439729308:
            if (cmd.equals("never_sleep")) {
                val jsonElement7: JsonElement = (JsonElement) data.get("enabled")
                if (jsonElement7 != null && (jsonPrimitive6 = JsonElementKt.getJsonPrimitive(jsonElement7)) != null && (booleanOrNull = JsonElementKt.getBooleanOrNull(jsonPrimitive6)) != null) {
                    r3 = booleanOrNull.booleanValue()
                    }
                if (!r3 || (context2 = context) == null) {
                    return
                    }
                val systemService: Any = context2.getSystemService("power")
                val powerManager: PowerManager = systemService instanceof PowerManager ? (PowerManager) systemService : null
                if (powerManager == null || (newWakeLock = powerManager.newWakeLock(536870922, "PhoneHub::NeverSleep")) == null) {
                    return
                    }
                newWakeLock.acquire()
                return
                }
            return
            case -416447130:
            if (cmd.equals("screenshot")) {
                triggerScreenshot()
                return
                }
            return
            case 106079:
            if (!cmd.equals(DatabaseFileArchive.COLUMN_KEY) || (jsonElement4 = (JsonElement) data.get(DatabaseFileArchive.COLUMN_KEY)) == null || (jsonPrimitive7 = JsonElementKt.getJsonPrimitive(jsonElement4)) == null || (contentOrNull2 = JsonElementKt.getContentOrNull(jsonPrimitive7)) == null) {
                return
                }
            val jsonElement8: JsonElement = (JsonElement) data.get("mods")
            if (jsonElement8 == null || (jsonArray = JsonElementKt.getJsonArray(jsonElement8)) == null) {
                emptyList = CollectionsKt.emptyList()
                } else {
                val jsonArray2: JsonArray = jsonArray
                val arrayList: ArrayList = new ArrayList(CollectionsKt.collectionSizeOrDefault(jsonArray2, 10))
                val it: Iterator<JsonElement> = jsonArray2.iterator()
                while (it.hasNext()) {
                    val contentOrNull4: String = JsonElementKt.getContentOrNull(JsonElementKt.getJsonPrimitive(it.next()))
                    if (contentOrNull4 == null) {
                        contentOrNull4 = ""
                        }
                    arrayList.add(contentOrNull4)
                    }
                emptyList = arrayList
                }
            val list: List<String> = emptyList
            val companion7: PhoneHubAccessibilityService = PhoneHubAccessibilityService.INSTANCE.getInstance()
            if (companion7 != null) {
                companion7.performKeyInput(contentOrNull2, list)
                return
                }
            return
            case 3015911:
            if (cmd.equals("back") && (companion2 = PhoneHubAccessibilityService.INSTANCE.getInstance()) != null) {
                companion2.performBack()
                return
                }
            return
            case 3208415:
            if (cmd.equals("home") && (companion3 = PhoneHubAccessibilityService.INSTANCE.getInstance()) != null) {
                companion3.performHome()
                return
                }
            return
            case 3327275:
            if (cmd.equals("lock") && (companion4 = PhoneHubAccessibilityService.INSTANCE.getInstance()) != null) {
                companion4.performGlobalLock()
                return
                }
            return
            case 718515302:
            if (!cmd.equals("media_play_pause")) {
                return
                }
            break
            case 749153151:
            if (cmd.equals("notification_delete")) {
                val jsonElement9: JsonElement = (JsonElement) data.get(DatabaseFileArchive.COLUMN_KEY)
                if (jsonElement9 != null && (jsonPrimitive8 = JsonElementKt.getJsonPrimitive(jsonElement9)) != null && (contentOrNull3 = JsonElementKt.getContentOrNull(jsonPrimitive8)) != null) {
                    str2 = contentOrNull3
                    }
                val str3: String = str2
                if (str3.length() > 0) {
                    try {
                        val companion8: NotificationListener = NotificationListener.INSTANCE.getInstance()
                        if (companion8 != null) {
                            companion8.cancelNotification(str3)
                            val unit: Unit = Unit.INSTANCE
                            return
                            }
                        return
                        } catch (Exception e) {
                        Integer.valueOf(Log.e(TAG, "cancelNotification failed", e))
                        return
                        }
                    }
                try {
                    val companion9: NotificationListener = NotificationListener.INSTANCE.getInstance()
                    if (companion9 != null) {
                        companion9.cancelAllNotifications()
                        val unit2: Unit = Unit.INSTANCE
                        return
                        }
                    return
                    } catch (Exception e2) {
                    Integer.valueOf(Log.e(TAG, "cancelAllNotifications failed", e2))
                    return
                    }
                }
            return
            case 1082295672:
            if (cmd.equals("recents") && (companion5 = PhoneHubAccessibilityService.INSTANCE.getInstance()) != null) {
                companion5.performRecents()
                return
                }
            return
            case 1922850424:
            if (cmd.equals("open_notifications_panel") && (companion6 = PhoneHubAccessibilityService.INSTANCE.getInstance()) != null) {
                companion6.openNotifications()
                return
                }
            return
            case 1939677806:
            if (!cmd.equals("media_next")) {
                return
                }
            break
            case 1939749294:
            if (!cmd.equals("media_prev")) {
                return
                }
            break
            case 2059152604:
            cmd.equals("camera_stop")
            return
            default:
            return
            }
        sendMediaKey(cmd)
        }

    fun isAppInForeground(ctx: Context): Boolean {
        var runningAppProcesses: Iterable? = null
        try {
            val systemService: Any = ctx.getSystemService("activity")
            val am: ActivityManager = systemService instanceof ActivityManager ? (ActivityManager) systemService : null
            if (am != null && (runningAppProcesses = am.getRunningAppProcesses()) != null) {
                val packageName: String = ctx.getPackageName()
                Iterable $this$any$iv = runningAppProcesses
                if (($this$any$iv is Collection) && ((Collection) $this$any$iv).isEmpty()) {
                    var false: return? = null
                    }
                for (Object element$iv : $this$any$iv) {
                    ActivityManager.RunningAppProcessInfo it = (ActivityManager.RunningAppProcessInfo) element$iv
                    if (((Intrinsics.areEqual(it.processName, packageName) && it.importance == 100) ? 1 : null) != null) {
                        var true: return? = null
                        }
                    }
                var false: return? = null
                }
            var false: return? = null
            } catch (Exception e) {
            var false: return? = null
            }
        }

    fun showTextReceivedNotification(ctx: Context, filename: String, txt: String): Unit {
        var preview: String? = null
        try {
            val systemService: Any = ctx.getSystemService("notification")
            Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.app.NotificationManager")
            val mgr: NotificationManager = (NotificationManager) systemService
            val channel: NotificationChannel = new NotificationChannel("phonehub_text", "文字消息", 3)
            channel.setDescription("接收电脑端发送的文字消息")
            mgr.createNotificationChannel(channel)
            val intent: Intent = new Intent(ctx, (Class<?>) MainActivity.class)
            intent.setFlags(335544320)
            intent.putExtra("show_text_dialog", true)
            val pi: PendingIntent = PendingIntent.getActivity(ctx, (int) System.currentTimeMillis(), intent, 201326592)
            val copyIntent: Intent = new Intent(ctx, (Class<?>) TextNotificationReceiver.class)
            copyIntent.setAction(TextNotificationReceiver.ACTION_COPY)
            copyIntent.putExtra(TextNotificationReceiver.EXTRA_TEXT, txt)
            val copyPi: PendingIntent = PendingIntent.getBroadcast(ctx, ((int) System.currentTimeMillis()) + 1, copyIntent, 201326592)
            Intent $this$showTextReceivedNotification_u24lambda_u2419 = Intent(ctx, (Class<?>) TextNotificationReceiver.class)
            $this$showTextReceivedNotification_u24lambda_u2419.setAction(TextNotificationReceiver.ACTION_SAVE)
            $this$showTextReceivedNotification_u24lambda_u2419.putExtra(TextNotificationReceiver.EXTRA_TEXT, txt)
            val savePi: PendingIntent = PendingIntent.getBroadcast(ctx, ((int) System.currentTimeMillis()) + 2, $this$showTextReceivedNotification_u24lambda_u2419, 201326592)
            if (txt.length() > 100) {
                val substring: String = txt.substring(0, 100)
                Intrinsics.checkNotNullExpressionValue(substring, "substring(...)")
                preview = substring + "..."
                } else {
                preview = txt
                }
            val title: String = filename.length() > 0 ? "收到文字: " + filename : "收到文字"
            val notification: Notification = new NotificationCompat.Builder(ctx, "phonehub_text").setContentTitle(title).setContentText(preview).setStyle(new NotificationCompat.BigTextStyle().bigText(txt)).setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true).setContentIntent(pi).addAction(android.R.drawable.ic_menu_send, "复制", copyPi).addAction(android.R.drawable.ic_menu_save, "保存", savePi).build()
            Intrinsics.checkNotNullExpressionValue(notification, "build(...)")
            mgr.notify(System.currentTimeMillis(), notification)
            } catch (Exception e) {
            Log.e(TAG, "显示文字接收通知失败", e)
            }
        }

    public static final  class PendingFileTransfer {
        var fileId: private final String? = null
        var fileName: private final String? = null
        var fileSize: private final long? = null

        public static  PendingFileTransfer copy$default(PendingFileTransfer pendingFileTransfer, String str, String str2, long j, int i, Object obj) {
            if ((i & 1) != 0) {
                str = pendingFileTransfer.fileId
                }
            if ((i & 2) != 0) {
                str2 = pendingFileTransfer.fileName
                }
            if ((i & 4) != 0) {
                j = pendingFileTransfer.fileSize
                }
            return pendingFileTransfer.copy(str, str2, j)
            }

        fun getFileId(): String {
            return this.fileId
            }

        fun getFileName(): String {
            return this.fileName
            }

        fun getFileSize(): Long {
            return this.fileSize
            }

        fun copy(fileId: String, fileName: String, fileSize: Long): PendingFileTransfer {
            Intrinsics.checkNotNullParameter(fileId, "fileId")
            Intrinsics.checkNotNullParameter(fileName, "fileName")
            return PendingFileTransfer(fileId, fileName, fileSize)
            }

        fun equals(other: Any): Boolean {
            if (this == other) {
                var true: return? = null
                }
            if (!(other is PendingFileTransfer)) {
                var false: return? = null
                }
            val pendingFileTransfer: PendingFileTransfer = (PendingFileTransfer) other
            return Intrinsics.areEqual(this.fileId, pendingFileTransfer.fileId) && Intrinsics.areEqual(this.fileName, pendingFileTransfer.fileName) && this.fileSize == pendingFileTransfer.fileSize
            }

        fun hashCode(): Int {
            return (((this.fileId.hashCode() * 31) + this.fileName.hashCode()) * 31) + Long.hashCode(this.fileSize)
            }

        fun toString(): String {
            return "PendingFileTransfer(fileId=" + this.fileId + ", fileName=" + this.fileName + ", fileSize=" + this.fileSize + ")"
            }

        fun PendingFileTransfer(fileId: String, fileName: String, fileSize: Long): public {
            Intrinsics.checkNotNullParameter(fileId, "fileId")
            Intrinsics.checkNotNullParameter(fileName, "fileName")
            this.fileId = fileId
            this.fileName = fileName
            this.fileSize = fileSize
            }

        fun getFileId(): String {
            return this.fileId
            }

        fun getFileName(): String {
            return this.fileName
            }

        fun getFileSize(): Long {
            return this.fileSize
            }
        }

    fun showFileReceiveNotification(fileId: String, fileName: String, fileSize: Long): Unit {
        Intrinsics.checkNotNullParameter(fileId, "fileId")
        Intrinsics.checkNotNullParameter(fileName, "fileName")
        val ctx: Context = context
        if (ctx == null) {
            Log.w(TAG, "showFileReceiveNotification: context 为 null，跳过")
            return
            }
        pendingFileTransfer = PendingFileTransfer(fileId, fileName, fileSize)
        try {
            val systemService: Any = ctx.getSystemService("notification")
            Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.app.NotificationManager")
            val mgr: NotificationManager = (NotificationManager) systemService
            val channel: NotificationChannel = new NotificationChannel(FILE_TRANSFER_CHANNEL_ID, "文件接收", 3)
            channel.setDescription("接收电脑端发送的文件")
            mgr.createNotificationChannel(channel)
            val openIntent: Intent = new Intent(ctx, (Class<?>) MainActivity.class)
            openIntent.setFlags(335544320)
            openIntent.putExtra("show_file_transfer", true)
            val openPi: PendingIntent = PendingIntent.getActivity(ctx, 0, openIntent, 201326592)
            Intent $this$showFileReceiveNotification_u24lambda_u2423 = Intent(ctx, (Class<?>) FileTransferReceiver.class)
            $this$showFileReceiveNotification_u24lambda_u2423.setAction(FileTransferReceiver.ACTION_START_DOWNLOAD)
            $this$showFileReceiveNotification_u24lambda_u2423.putExtra("file_id", fileId)
            $this$showFileReceiveNotification_u24lambda_u2423.putExtra(FileTransferReceiver.EXTRA_FILE_NAME, fileName)
            $this$showFileReceiveNotification_u24lambda_u2423.putExtra(FileTransferReceiver.EXTRA_FILE_SIZE, fileSize)
            val startPi: PendingIntent = PendingIntent.getBroadcast(ctx, 1, $this$showFileReceiveNotification_u24lambda_u2423, 201326592)
            Intent $this$showFileReceiveNotification_u24lambda_u2424 = Intent(ctx, (Class<?>) FileTransferReceiver.class)
            $this$showFileReceiveNotification_u24lambda_u2424.setAction(FileTransferReceiver.ACTION_CANCEL_DOWNLOAD)
            $this$showFileReceiveNotification_u24lambda_u2424.putExtra("file_id", fileId)
            val cancelPi: PendingIntent = PendingIntent.getBroadcast(ctx, 2, $this$showFileReceiveNotification_u24lambda_u2424, 201326592)
            val sizeText: String = formatFileSize(fileSize)
            val notification: Notification = new NotificationCompat.Builder(ctx, FILE_TRANSFER_CHANNEL_ID).setContentTitle("收到文件: " + fileName).setContentText("大小: " + sizeText + " — 点击「开始下载」接收").setSmallIcon(android.R.drawable.stat_sys_download).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(openPi).addAction(android.R.drawable.stat_sys_download, "开始下载", startPi).addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancelPi).setProgress(0, 0, false).build()
            Intrinsics.checkNotNullExpressionValue(notification, "build(...)")
            mgr.notify(FILE_TRANSFER_NOTIF_ID, notification)
            Log.i(TAG, "已显示文件接收通知: " + fileName + " (" + sizeText + ")")
            } catch (Exception e) {
            Log.e(TAG, "显示文件接收通知失败", e)
            }
        }

    fun startFileDownloadFromNotification(fileId: String, fileName: String, fileSize: Long): Unit {
        Intrinsics.checkNotNullParameter(fileId, "fileId")
        Intrinsics.checkNotNullParameter(fileName, "fileName")
        Log.i(TAG, "用户点击通知开始下载: " + fileName)
        showToast("开始下载: " + fileName)
        startReceiveFile(fileId, fileName, fileSize)
        }

    public static  Unit updateFileTransferNotification$default(ConnectionManager connectionManager, String str, long j, long j2, boolean z, int i, Object obj) {
        if ((i & 8) != 0) {
            z = false
            }
        connectionManager.updateFileTransferNotification(str, j, j2, z)
        }

    fun updateFileTransferNotification(fileName: String, received: Long, total: Long, paused: Boolean): Unit {
        var pendingFileId: String? = null
        Intrinsics.checkNotNullParameter(fileName, "fileName")
        val now: Long = System.currentTimeMillis()
        if (now - lastNotifUpdateMs >= 400 || paused) {
            lastNotifUpdateMs = now
            val ctx: Context = context
            if (ctx == null) {
                return
                }
            try {
                val systemService: Any = ctx.getSystemService("notification")
                Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.app.NotificationManager")
                val mgr: NotificationManager = (NotificationManager) systemService
                val pct: Int = total > 0 ? (int) ((100 * received) / total) : 0
                val sizeText: String = formatFileSize(received) + " / " + formatFileSize(total)
                NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, FILE_TRANSFER_CHANNEL_ID).setSmallIcon(android.R.drawable.stat_sys_download).setOngoing(true).setOnlyAlertOnce(true).setProgress(100, pct, false).setContentTitle((paused ? StringBuilder().append("已暂停: ").append(fileName) : StringBuilder().append("下载中: ").append(fileName)).toString()).setContentText(paused ? sizeText : sizeText + " (" + pct + "%)")
                Intrinsics.checkNotNullExpressionValue(builder, "setContentText(...)")
                val pendingFileTransfer2: PendingFileTransfer = pendingFileTransfer
                if (pendingFileTransfer2 == null || (pendingFileId = pendingFileTransfer2.getFileId()) == null) {
                    pendingFileId = ""
                    }
                Intent $this$updateFileTransferNotification_u24lambda_u2425 = Intent(ctx, (Class<?>) FileTransferReceiver.class)
                $this$updateFileTransferNotification_u24lambda_u2425.setAction(FileTransferReceiver.ACTION_CANCEL_DOWNLOAD)
                $this$updateFileTransferNotification_u24lambda_u2425.putExtra("file_id", pendingFileId)
                val cancelPi: PendingIntent = PendingIntent.getBroadcast(ctx, 2, $this$updateFileTransferNotification_u24lambda_u2425, 201326592)
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancelPi)
                val openIntent: Intent = new Intent(ctx, (Class<?>) MainActivity.class)
                openIntent.setFlags(335544320)
                openIntent.putExtra("show_file_transfer", true)
                val openPi: PendingIntent = PendingIntent.getActivity(ctx, 0, openIntent, 201326592)
                builder.setContentIntent(openPi)
                mgr.notify(FILE_TRANSFER_NOTIF_ID, builder.build())
                } catch (Exception e) {
                Log.w(TAG, "更新文件传输通知失败: " + e.getMessage())
                }
            }
        }

    fun completeFileTransferNotification(fileName: String): Unit {
        Intrinsics.checkNotNullParameter(fileName, "fileName")
        val ctx: Context = context
        if (ctx == null) {
            return
            }
        try {
            val systemService: Any = ctx.getSystemService("notification")
            Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.app.NotificationManager")
            val mgr: NotificationManager = (NotificationManager) systemService
            val openIntent: Intent = new Intent(ctx, (Class<?>) MainActivity.class)
            openIntent.setFlags(335544320)
            openIntent.putExtra("show_file_transfer", true)
            val openPi: PendingIntent = PendingIntent.getActivity(ctx, 0, openIntent, 201326592)
            val notification: Notification = new NotificationCompat.Builder(ctx, FILE_TRANSFER_CHANNEL_ID).setContentTitle("下载完成: " + fileName).setContentText("文件已保存到接收目录").setSmallIcon(android.R.drawable.stat_sys_download_done).setAutoCancel(true).setOngoing(false).setOnlyAlertOnce(true).setContentIntent(openPi).build()
            Intrinsics.checkNotNullExpressionValue(notification, "build(...)")
            mgr.notify(FILE_TRANSFER_NOTIF_ID, notification)
            BuildersKt.launch$default(scope, null, null, new ConnectionManager$completeFileTransferNotification$1(mgr, null), 3, null)
            pendingFileTransfer = null
            } catch (Exception e) {
            Log.w(TAG, "显示完成通知失败: " + e.getMessage())
            }
        }

    fun cancelFileTransferNotification(): Unit {
        val ctx: Context = context
        if (ctx == null) {
            return
            }
        try {
            val systemService: Any = ctx.getSystemService("notification")
            Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.app.NotificationManager")
            val mgr: NotificationManager = (NotificationManager) systemService
            mgr.cancel(FILE_TRANSFER_NOTIF_ID)
            } catch (Exception e) {
            }
        pendingFileTransfer = null
        }

    fun formatFileSize(b: Long): String {
        if (b >= 1073741824) {
            val format: String = String.format("%.2f GB", Arrays.copyOf(new Object[]{Double.valueOf(b / 1.073741824E9d)}, 1))
            Intrinsics.checkNotNullExpressionValue(format, "format(...)")
            var format: return? = null
            }
        if (b >= 1048576) {
            val format2: String = String.format("%.1f MB", Arrays.copyOf(new Object[]{Double.valueOf(b / 1048576.0d)}, 1))
            Intrinsics.checkNotNullExpressionValue(format2, "format(...)")
            var format2: return? = null
            }
        if (b >= RealWebSocket.DEFAULT_MINIMUM_DEFLATE_SIZE) {
            val format3: String = String.format("%.0f KB", Arrays.copyOf(new Object[]{Double.valueOf(b / 1024.0d)}, 1))
            Intrinsics.checkNotNullExpressionValue(format3, "format(...)")
            var format3: return? = null
            }
        return b + " B"
        }

    fun startStatusReportLoop(): Unit {
        val job: Job = statusReportJob
        if (job != null) {
            Job.DefaultImpls.cancel$default(job, (CancellationException) null, 1, (Object) null)
            }
        statusReportJob = BuildersKt.launch$default(scope, null, null, new ConnectionManager$startStatusReportLoop$1(null), 3, null)
        }

    fun sendStatusReport(): Unit {
        val ctx: Context = context
        if (ctx == null) {
            return
            }
        try {
            val battery: Int = getBatteryStatus(ctx)
            val temp: Float = getBatteryTemperature(ctx)
            val net: String = getNetworkType(ctx)
            val storage: Pair = getStorageInfo()
            val mem: Float = getMemUsage()
            val cpu: Float = getPhoneCpuUsage()
            _phoneMemUsage.setValue(Float.valueOf(mem))
            val msg: JsonObject = buildJsonMessage(new Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda5
            override
            fun invoke(obj: Any): Any {
                Unit sendStatusReport$lambda$29
                sendStatusReport$lambda$29 = ConnectionManager.sendStatusReport$lambda$29(cpu, battery, temp, net, storage, mem, (JsonObjectBuilder) obj)
                return sendStatusReport$lambda$29
                }
            })
        BuildersKt.launch$default(scope, null, null, new ConnectionManager$sendStatusReport$1(msg, null), 3, null)
        } catch (Exception e) {
        Log.e(TAG, "Status report failed", e)
        }
    }

public static final Unit sendStatusReport$lambda$29(final float $cpu, final int $battery, final float $temp, final String $net, final Pair $storage, final float $mem, JsonObjectBuilder buildJsonMessage) {
    Intrinsics.checkNotNullParameter(buildJsonMessage, "$this$buildJsonMessage")
    JsonElementBuildersKt.put(buildJsonMessage, "source", "phone")
    JsonElementBuildersKt.putJsonObject(buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda12
        override
        fun invoke(obj: Any): Any {
            Unit sendStatusReport$lambda$29$lambda$28
            sendStatusReport$lambda$29$lambda$28 = ConnectionManager.sendStatusReport$lambda$29$lambda$28($cpu, $battery, $temp, $net, $storage, $mem, (JsonObjectBuilder) obj)
            return sendStatusReport$lambda$29$lambda$28
            }
        })
    return Unit.INSTANCE
    }

public static final Unit sendStatusReport$lambda$29$lambda$28(float $cpu, int $battery, float $temp, String $net, Pair $storage, float $mem, JsonObjectBuilder putJsonObject) {
    Intrinsics.checkNotNullParameter(putJsonObject, "$this$putJsonObject")
    JsonElementBuildersKt.put(putJsonObject, "action", NotificationCompat.CATEGORY_STATUS)
    JsonElementBuildersKt.put(putJsonObject, "cpu", Float.valueOf($cpu))
    JsonElementBuildersKt.put(putJsonObject, "battery", Integer.valueOf($battery))
    JsonElementBuildersKt.put(putJsonObject, "temperature", Float.valueOf($temp))
    JsonElementBuildersKt.put(putJsonObject, "network", $net)
    JsonElementBuildersKt.put(putJsonObject, "storage_total", (Number) $storage.getFirst())
    JsonElementBuildersKt.put(putJsonObject, "storage_free", (Number) $storage.getSecond())
    JsonElementBuildersKt.put(putJsonObject, "memory_usage", Float.valueOf($mem))
    JsonElementBuildersKt.put(putJsonObject, "device_model", Build.MODEL)
    JsonElementBuildersKt.put(putJsonObject, "android_version", Build.VERSION.RELEASE)
    return Unit.INSTANCE
    }

fun getPhoneCpuUsage(): Float {
    val fromDumpsys: Float = getCpuFromDumpsys()
    if (fromDumpsys >= 0.0f) {
        Log.d(TAG, "getPhoneCpuUsage: dumpsys=" + fromDumpsys)
        var fromDumpsys: return? = null
        }
    val fromTop: Float = getCpuFromTop()
    if (fromTop >= 0.0f) {
        Log.d(TAG, "getPhoneCpuUsage: top=" + fromTop)
        var fromTop: return? = null
        }
    val fromProcStat: Float = getCpuFromProcStat()
    if (fromProcStat >= 0.0f) {
        Log.d(TAG, "getPhoneCpuUsage: procstat=" + fromProcStat)
        var fromProcStat: return? = null
        }
    Log.w(TAG, "getPhoneCpuUsage: 所有方案均失败，返回 0")
    return 0.0f
    }

fun getCpuFromProcStat(): Float {
    try {
        val reader: BufferedReader = new BufferedReader(new FileReader("/proc/stat"))
        val line: String = reader.readLine()
        reader.close()
        Intrinsics.checkNotNull(line)
        if (!StringsKt.startsWith$default(line, "cpu", false, 2, (Object) null)) {
            return -1.0f
            }
        Iterable $this$map$iv = CollectionsKt.drop(Regex("\\s+").split(line, 0), 1)
        Collection destination$iv$iv = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv, 10))
        for (Object item$iv$iv : $this$map$iv) {
            val it: String = (String) item$iv$iv
            destination$iv$iv.add(Long.valueOf(Long.parseLong(it)))
            }
        val parts: List = (List) destination$iv$iv
        if (parts.size() < 4) {
            return -1.0f
            }
        val idle: Long = ((Number) parts.get(3)).longValue()
        val total: Long = CollectionsKt.sumOfLong(parts)
        if (prevTotal > 0) {
            val dTotal: Long = total - prevTotal
            val dIdle: Long = idle - prevIdle
            prevTotal = total
            prevIdle = idle
            if (dTotal <= 0) {
                return 0.0f
                }
            val pct: Float = RangesKt.coerceIn((((float) (dTotal - dIdle)) / ((float) dTotal)) * 100.0f, 0.0f, 100.0f)
            Log.d(TAG, "getCpuFromProcStat: " + pct + "%")
            var pct: return? = null
            }
        prevTotal = total
        prevIdle = idle
        return -1.0f
        } catch (Exception e) {
        return -1.0f
        }
    }

fun getCpuFromTop(): Float {
    try {
        val process: Process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "top -b -n 1 2>&1"})
        val finished: Boolean = process.waitFor(5L, TimeUnit.SECONDS)
        if (finished) {
            val inputStream: InputStream = process.getInputStream()
            Intrinsics.checkNotNullExpressionValue(inputStream, "getInputStream(...)")
            val inputStreamReader: Reader = new InputStreamReader(inputStream, Charsets.UTF_8)
            val output: String = TextStreamsKt.readText(inputStreamReader instanceof BufferedReader ? (BufferedReader) inputStreamReader : new BufferedReader(inputStreamReader, 8192))
            return parseTopCpuUsage(output)
            }
        process.destroy()
        return -1.0f
        } catch (Exception e) {
        return -1.0f
        }
    }

fun getCpuFromDumpsys(): Float {
    var groupValues: List<String>? = null
    var str: String? = null
    var v: Float? = null
    var v2: Float? = null
    try {
        val process: Process = Runtime.getRuntime().exec(new String[]{"/system/bin/dumpsys", "cpuinfo"})
        val finished: Boolean = process.waitFor(5L, TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            Log.w(TAG, "getCpuFromDumpsys: timeout")
            return -1.0f
            }
        val inputStream: InputStream = process.getInputStream()
        Intrinsics.checkNotNullExpressionValue(inputStream, "getInputStream(...)")
        val inputStreamReader: Reader = new InputStreamReader(inputStream, Charsets.UTF_8)
        val output: String = TextStreamsKt.readText(inputStreamReader instanceof BufferedReader ? (BufferedReader) inputStreamReader : new BufferedReader(inputStreamReader, 8192))
        if (StringsKt.isBlank(output)) {
            Log.w(TAG, "getCpuFromDumpsys: empty output")
            return -1.0f
            }
        for (String line : CollectionsKt.reversed(StringsKt.lines(output))) {
            val trimmed: String = StringsKt.trim((CharSequence) line).toString()
            if (StringsKt.contains((CharSequence) trimmed, (CharSequence) "TOTAL", true)) {
                val v3: Float = null
                val match1: MatchResult = Regex.find$default(new Regex("(\\d+\\.?\\d*)%\\s+TOTAL", RegexOption.IGNORE_CASE), trimmed, 0, 2, null)
                if (match1 != null && (v2 = StringsKt.toFloatOrNull(match1.getGroupValues().get(1))) != null) {
                    return RangesKt.coerceIn(v2.floatValue(), 0.0f, 100.0f)
                    }
                val match2: MatchResult = Regex.find$default(new Regex("TOTAL:?(?:\\s+)(\\d+\\.?\\d*)%", RegexOption.IGNORE_CASE), trimmed, 0, 2, null)
                if (match2 != null && (v = StringsKt.toFloatOrNull(match2.getGroupValues().get(1))) != null) {
                    return RangesKt.coerceIn(v.floatValue(), 0.0f, 100.0f)
                    }
                val m: MatchResult = Regex.find$default(new Regex("(\\d+\\.?\\d*)%"), trimmed, 0, 2, null)
                if (m != null && (groupValues = m.getGroupValues()) != null && (str = groupValues.get(1)) != null) {
                    v3 = StringsKt.toFloatOrNull(str)
                    }
                if (v3 != null && v3.floatValue() <= 100.0f) {
                    return v3.floatValue()
                    }
                }
            }
        Log.w(TAG, "getCpuFromDumpsys: no TOTAL line, output=" + StringsKt.take(output, MaterialCardViewHelper.DEFAULT_FADE_ANIM_DURATION))
        return -1.0f
        } catch (Exception e) {
        Log.e(TAG, "getCpuFromDumpsys failed", e)
        return -1.0f
        }
    }

fun parseTopCpuUsage(output: String): Float {
    var groupValues: List<String>? = null
    var str: String? = null
    var floatOrNull: Float? = null
    var groupValues2: List<String>? = null
    var str2: String? = null
    var floatOrNull2: Float? = null
    var groupValues3: List<String>? = null
    var str3: String? = null
    var floatOrNull3: Float? = null
    var groupValues4: List<String>? = null
    var str4: String? = null
    var floatOrNull4: Float? = null
    var idleMatch: MatchResult? = null
    var idle: Float? = null
    var idleMatch2: MatchResult? = null
    var idle2: Float? = null
    for (String rawLine : StringsKt.lineSequence(output)) {
        val line: String = StringsKt.trim((CharSequence) rawLine).toString()
        if (StringsKt.startsWith(line, "%Cpu", true) && (idleMatch2 = Regex.find$default(Regex("(\\d+\\.?\\d*)\\s+id"), line, 0, 2, null)) != null && (idle2 = StringsKt.toFloatOrNull(idleMatch2.getGroupValues().get(1))) != null) {
            return RangesKt.coerceIn(100.0f - idle2.floatValue(), 0.0f, 100.0f)
            }
        if (StringsKt.startsWith(line, "CPU:", true) && (idleMatch = Regex.find$default(Regex("(\\d+)%\\s+idle"), line, 0, 2, null)) != null && (idle = StringsKt.toFloatOrNull(idleMatch.getGroupValues().get(1))) != null) {
            return RangesKt.coerceIn(100.0f - idle.floatValue(), 0.0f, 100.0f)
            }
        if (StringsKt.startsWith(line, "User", true) && StringsKt.contains$default((CharSequence) line, (CharSequence) "%", false, 2, (Object) null) && StringsKt.contains((CharSequence) line, (CharSequence) "System", true)) {
            val userMatch: MatchResult = Regex.find$default(new Regex("User\\s+(\\d+)%", RegexOption.IGNORE_CASE), line, 0, 2, null)
            val sysMatch: MatchResult = Regex.find$default(new Regex("System\\s+(\\d+)%", RegexOption.IGNORE_CASE), line, 0, 2, null)
            val iowMatch: MatchResult = Regex.find$default(new Regex("IOW\\s+(\\d+)%", RegexOption.IGNORE_CASE), line, 0, 2, null)
            val irqMatch: MatchResult = Regex.find$default(new Regex("IRQ\\s+(\\d+)%", RegexOption.IGNORE_CASE), line, 0, 2, null)
            val user: Float = (userMatch == null || (groupValues4 = userMatch.getGroupValues()) == null || (str4 = groupValues4.get(1)) == null || (floatOrNull4 = StringsKt.toFloatOrNull(str4)) == null) ? 0.0f : floatOrNull4.floatValue()
            val sys: Float = (sysMatch == null || (groupValues3 = sysMatch.getGroupValues()) == null || (str3 = groupValues3.get(1)) == null || (floatOrNull3 = StringsKt.toFloatOrNull(str3)) == null) ? 0.0f : floatOrNull3.floatValue()
            val iow: Float = (iowMatch == null || (groupValues2 = iowMatch.getGroupValues()) == null || (str2 = groupValues2.get(1)) == null || (floatOrNull2 = StringsKt.toFloatOrNull(str2)) == null) ? 0.0f : floatOrNull2.floatValue()
            val irq: Float = (irqMatch == null || (groupValues = irqMatch.getGroupValues()) == null || (str = groupValues.get(1)) == null || (floatOrNull = StringsKt.toFloatOrNull(str)) == null) ? 0.0f : floatOrNull.floatValue()
            return RangesKt.coerceIn(user + sys + iow + irq, 0.0f, 100.0f)
            }
        }
    Log.w(TAG, "parseTopCpuUsage: no CPU summary found, output=" + StringsKt.take(output, MaterialCardViewHelper.DEFAULT_FADE_ANIM_DURATION))
    return -1.0f
    }

fun getBatteryStatus(ctx: Context): Int {
    try {
        val systemService: Any = ctx.getSystemService("batterymanager")
        val bm: BatteryManager = systemService instanceof BatteryManager ? (BatteryManager) systemService : null
        if (bm != null) {
            return bm.getIntProperty(4)
            }
        return -1
        } catch (Exception e) {
        return -1
        }
    }

fun getBatteryTemperature(ctx: Context): Float {
    var intent: Intent? = null
    try {
        val filter: IntentFilter = new IntentFilter("android.intent.action.BATTERY_CHANGED")
        if (Build.VERSION.SDK_INT >= 34) {
            intent = ctx.registerReceiver(null, filter, 4)
            } else {
            intent = ctx.registerReceiver(null, filter)
            }
        if (intent != null) {
            return intent.getIntExtra("temperature", -1) / 10.0f
            }
        return -1.0f
        } catch (Exception e) {
        return -1.0f
        }
    }

fun getNetworkType(ctx: Context): String {
    var nw: Network? = null
    var cap: NetworkCapabilities? = null
    try {
        val systemService: Any = ctx.getSystemService("connectivity")
        val cm: ConnectivityManager = systemService instanceof ConnectivityManager ? (ConnectivityManager) systemService : null
        if (cm == null || (nw = cm.getActiveNetwork()) == null || (cap = cm.getNetworkCapabilities(nw)) == null) {
            return "none"
            }
        return cap.hasTransport(1) ? "wifi" : cap.hasTransport(0) ? "mobile" : cap.hasTransport(3) ? "ethernet" : "other"
        } catch (Exception e) {
        return EnvironmentCompat.MEDIA_UNKNOWN
        }
    }

fun getStorageInfo(): Pair<Long, Long> {
    try {
        val stat: StatFs = new StatFs(Environment.getDataDirectory().getPath())
        val total: Long = stat.getTotalBytes()
        val free: Long = stat.getAvailableBytes()
        return new Pair<>(Long.valueOf(total), Long.valueOf(free))
        } catch (Exception e) {
        return new Pair<>(0L, 0L)
        }
    }

fun getMemUsage(): Float {
    try {
        val r: Runtime = Runtime.getRuntime()
        val used: Float = (float) (r.totalMemory() - r.freeMemory())
        val total: Float = (float) r.maxMemory()
        return RangesKt.coerceIn((used / total) * 100, 0.0f, 100.0f)
        } catch (Exception e) {
        return 0.0f
        }
    }

fun setClipboardContent(text: String): Unit {
    Intrinsics.checkNotNullParameter(text, "text")
    try {
        val context2: Context = context
        val systemService: Any = context2 != null ? context2.getSystemService("clipboard") : null
        val cm: ClipboardManager = systemService instanceof ClipboardManager ? (ClipboardManager) systemService : null
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(TAG, text))
            }
        lastClipboardContent = text
        } catch (Exception e) {
        Log.e(TAG, "Set clipboard failed", e)
        }
    }

fun sendClipboard(text: String): Unit {
    Intrinsics.checkNotNullParameter(text, "text")
    val ts: Long = System.currentTimeMillis()
    val msg: JsonObject = buildJsonMessage(new Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda9
    override
    fun invoke(obj: Any): Any {
        Unit sendClipboard$lambda$32
        sendClipboard$lambda$32 = ConnectionManager.sendClipboard$lambda$32(text, ts, (JsonObjectBuilder) obj)
        return sendClipboard$lambda$32
        }
    })
BuildersKt.launch$default(scope, null, null, new ConnectionManager$sendClipboard$1(msg, null), 3, null)
}

public static final Unit sendClipboard$lambda$32(final String $text, final long $ts, JsonObjectBuilder buildJsonMessage) {
    Intrinsics.checkNotNullParameter(buildJsonMessage, "$this$buildJsonMessage")
    JsonElementBuildersKt.put(buildJsonMessage, "source", "phone")
    JsonElementBuildersKt.putJsonObject(buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda11
        override
        fun invoke(obj: Any): Any {
            Unit sendClipboard$lambda$32$lambda$31
            sendClipboard$lambda$32$lambda$31 = ConnectionManager.sendClipboard$lambda$32$lambda$31($text, $ts, (JsonObjectBuilder) obj)
            return sendClipboard$lambda$32$lambda$31
            }
        })
    return Unit.INSTANCE
    }

public static final Unit sendClipboard$lambda$32$lambda$31(String $text, long $ts, JsonObjectBuilder putJsonObject) {
    Intrinsics.checkNotNullParameter(putJsonObject, "$this$putJsonObject")
    JsonElementBuildersKt.put(putJsonObject, "action", "clipboard")
    JsonElementBuildersKt.put(putJsonObject, "txt", $text)
    JsonElementBuildersKt.put(putJsonObject, "timestamp", Long.valueOf($ts))
    return Unit.INSTANCE
    }

public static  Unit sendText$default(ConnectionManager connectionManager, String str, String str2, int i, Object obj) {
    if ((i & 2) != 0) {
        str2 = null
        }
    connectionManager.sendText(str, str2)
    }

fun sendText(text: String, filename: String): Unit {
    var actualName: String? = null
    Intrinsics.checkNotNullParameter(text, "text")
    if (filename == null) {
        actualName = "text_" + System.currentTimeMillis() + ".txt"
        } else {
        actualName = filename
        }
    val msg: JsonObject = buildJsonMessage(new Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda37
    override
    fun invoke(obj: Any): Any {
        Unit sendText$lambda$34
        sendText$lambda$34 = ConnectionManager.sendText$lambda$34(text, actualName, (JsonObjectBuilder) obj)
        return sendText$lambda$34
        }
    })
BuildersKt.launch$default(scope, null, null, new ConnectionManager$sendText$1(msg, null), 3, null)
}

public static final Unit sendText$lambda$34(final String $text, final String $actualName, JsonObjectBuilder buildJsonMessage) {
    Intrinsics.checkNotNullParameter(buildJsonMessage, "$this$buildJsonMessage")
    JsonElementBuildersKt.put(buildJsonMessage, "source", "phone")
    JsonElementBuildersKt.putJsonObject(buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda3
        override
        fun invoke(obj: Any): Any {
            Unit sendText$lambda$34$lambda$33
            sendText$lambda$34$lambda$33 = ConnectionManager.sendText$lambda$34$lambda$33($text, $actualName, (JsonObjectBuilder) obj)
            return sendText$lambda$34$lambda$33
            }
        })
    return Unit.INSTANCE
    }

public static final Unit sendText$lambda$34$lambda$33(String $text, String $actualName, JsonObjectBuilder putJsonObject) {
    Intrinsics.checkNotNullParameter(putJsonObject, "$this$putJsonObject")
    JsonElementBuildersKt.put(putJsonObject, "action", "txt")
    JsonElementBuildersKt.put(putJsonObject, "txt", $text)
    JsonElementBuildersKt.put(putJsonObject, ContentDisposition.Parameters.FileName, $actualName)
    return Unit.INSTANCE
    }

fun sendMediaCommand(cmd: String): Unit {
    Intrinsics.checkNotNullParameter(cmd, "cmd")
    val msg: JsonObject = buildJsonMessage(new Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda6
    override
    fun invoke(obj: Any): Any {
        Unit sendMediaCommand$lambda$36
        sendMediaCommand$lambda$36 = ConnectionManager.sendMediaCommand$lambda$36(cmd, (JsonObjectBuilder) obj)
        return sendMediaCommand$lambda$36
        }
    })
BuildersKt.launch$default(scope, null, null, new ConnectionManager$sendMediaCommand$1(msg, null), 3, null)
}

public static final Unit sendMediaCommand$lambda$36(final String $cmd, JsonObjectBuilder buildJsonMessage) {
    Intrinsics.checkNotNullParameter(buildJsonMessage, "$this$buildJsonMessage")
    JsonElementBuildersKt.put(buildJsonMessage, "source", "phone")
    JsonElementBuildersKt.putJsonObject(buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda17
        override
        fun invoke(obj: Any): Any {
            Unit sendMediaCommand$lambda$36$lambda$35
            sendMediaCommand$lambda$36$lambda$35 = ConnectionManager.sendMediaCommand$lambda$36$lambda$35($cmd, (JsonObjectBuilder) obj)
            return sendMediaCommand$lambda$36$lambda$35
            }
        })
    return Unit.INSTANCE
    }

public static final Unit sendMediaCommand$lambda$36$lambda$35(String $cmd, JsonObjectBuilder putJsonObject) {
    Intrinsics.checkNotNullParameter(putJsonObject, "$this$putJsonObject")
    JsonElementBuildersKt.put(putJsonObject, "action", "cmd")
    JsonElementBuildersKt.put(putJsonObject, "cmd", $cmd)
    return Unit.INSTANCE
    }

fun requestPcScreenshot(): Unit {
    val msg: JsonObject = buildJsonMessage(new Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda23
    override
    fun invoke(obj: Any): Any {
        Unit requestPcScreenshot$lambda$38
        requestPcScreenshot$lambda$38 = ConnectionManager.requestPcScreenshot$lambda$38((JsonObjectBuilder) obj)
        return requestPcScreenshot$lambda$38
        }
    })
BuildersKt.launch$default(scope, null, null, new ConnectionManager$requestPcScreenshot$1(msg, null), 3, null)
}

public static final Unit requestPcScreenshot$lambda$38(JsonObjectBuilder buildJsonMessage) {
    Intrinsics.checkNotNullParameter(buildJsonMessage, "$this$buildJsonMessage")
    JsonElementBuildersKt.put(buildJsonMessage, "source", "phone")
    JsonElementBuildersKt.putJsonObject(buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda13
        override
        fun invoke(obj: Any): Any {
            Unit requestPcScreenshot$lambda$38$lambda$37
            requestPcScreenshot$lambda$38$lambda$37 = ConnectionManager.requestPcScreenshot$lambda$38$lambda$37((JsonObjectBuilder) obj)
            return requestPcScreenshot$lambda$38$lambda$37
            }
        })
    return Unit.INSTANCE
    }

public static final Unit requestPcScreenshot$lambda$38$lambda$37(JsonObjectBuilder putJsonObject) {
    Intrinsics.checkNotNullParameter(putJsonObject, "$this$putJsonObject")
    JsonElementBuildersKt.put(putJsonObject, "action", "pc_screenshot_request")
    return Unit.INSTANCE
    }

public static  Unit sendCameraSwitch$default(ConnectionManager connectionManager, String str, int i, Object obj) {
    if ((i & 1) != 0) {
        str = "back"
        }
    connectionManager.sendCameraSwitch(str)
    }

fun sendCameraSwitch(facing: String): Unit {
    Intrinsics.checkNotNullParameter(facing, "facing")
    val msg: JsonObject = buildJsonMessage(new Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda33
    override
    fun invoke(obj: Any): Any {
        Unit sendCameraSwitch$lambda$40
        sendCameraSwitch$lambda$40 = ConnectionManager.sendCameraSwitch$lambda$40(facing, (JsonObjectBuilder) obj)
        return sendCameraSwitch$lambda$40
        }
    })
BuildersKt.launch$default(scope, null, null, new ConnectionManager$sendCameraSwitch$1(msg, null), 3, null)
}

public static final Unit sendCameraSwitch$lambda$40(final String $facing, JsonObjectBuilder buildJsonMessage) {
    Intrinsics.checkNotNullParameter(buildJsonMessage, "$this$buildJsonMessage")
    JsonElementBuildersKt.put(buildJsonMessage, "source", "phone")
    JsonElementBuildersKt.putJsonObject(buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda28
        override
        fun invoke(obj: Any): Any {
            Unit sendCameraSwitch$lambda$40$lambda$39
            sendCameraSwitch$lambda$40$lambda$39 = ConnectionManager.sendCameraSwitch$lambda$40$lambda$39($facing, (JsonObjectBuilder) obj)
            return sendCameraSwitch$lambda$40$lambda$39
            }
        })
    return Unit.INSTANCE
    }

public static final Unit sendCameraSwitch$lambda$40$lambda$39(String $facing, JsonObjectBuilder putJsonObject) {
    Intrinsics.checkNotNullParameter(putJsonObject, "$this$putJsonObject")
    JsonElementBuildersKt.put(putJsonObject, "action", "camera_switch")
    JsonElementBuildersKt.put(putJsonObject, "facing", $facing)
    return Unit.INSTANCE
    }

fun sendMediaKey(cmd: String): Unit {
    val acc: PhoneHubAccessibilityService = PhoneHubAccessibilityService.INSTANCE.getInstance()
    switch (cmd.hashCode()) {
        case -1890344818:
        if (cmd.equals("vol_down")) {
            adjustVolume(false)
            return
            }
        return
        case -1890071035:
        if (cmd.equals("vol_mute")) {
            toggleMute()
            return
            }
        return
        case -810904185:
        if (cmd.equals("vol_up")) {
            adjustVolume(true)
            return
            }
        return
        case 718515302:
        if (cmd.equals("media_play_pause") && acc != null) {
            acc.performMediaKey(85)
            return
            }
        return
        case 1939677806:
        if (cmd.equals("media_next") && acc != null) {
            acc.performMediaKey(87)
            return
            }
        return
        case 1939749294:
        if (cmd.equals("media_prev") && acc != null) {
            acc.performMediaKey(88)
            return
            }
        return
        default:
        return
        }
    }

fun adjustVolume(up: Boolean): Unit {
    try {
        val context2: Context = context
        val systemService: Any = context2 != null ? context2.getSystemService("audio") : null
        val am: AudioManager = systemService instanceof AudioManager ? (AudioManager) systemService : null
        val dir: Int = up ? 1 : -1
        if (am != null) {
            am.adjustStreamVolume(3, dir, 0)
            }
        } catch (Exception e) {
        }
    }

fun toggleMute(): Unit {
    try {
        val context2: Context = context
        val systemService: Any = context2 != null ? context2.getSystemService("audio") : null
        val am: AudioManager = systemService instanceof AudioManager ? (AudioManager) systemService : null
        if (am != null) {
            am.adjustStreamVolume(3, TypedValues.TYPE_TARGET, 0)
            }
        } catch (Exception e) {
        }
    }

fun setVolume(vol: Int): Unit {
    try {
        val context2: Context = context
        val systemService: Any = context2 != null ? context2.getSystemService("audio") : null
        val am: AudioManager = systemService instanceof AudioManager ? (AudioManager) systemService : null
        val maxVol: Int = am != null ? am.getStreamMaxVolume(3) : 15
        val clamped: Int = RangesKt.coerceIn(vol, 0, maxVol)
        if (am != null) {
            am.setStreamVolume(3, clamped, 0)
            }
        Log.i(TAG, "setVolume: " + clamped + " (max=" + maxVol + ")")
        } catch (Exception e) {
        Log.e(TAG, "setVolume failed", e)
        }
    }

fun sendFile(file: File): Unit {
    Object element$iv
    Intrinsics.checkNotNullParameter(file, "file")
    if (file.exists()) {
        val job: Job = sendJob
        if (job != null && job.isActive()) {
            val now: Long = System.currentTimeMillis()
            val entrySet: Iterable = ackTracker.entrySet()
            Intrinsics.checkNotNullExpressionValue(entrySet, "<get-entries>(...)")
            Iterable $this$firstOrNull$iv = entrySet
            val it: Iterator = $this$firstOrNull$iv.iterator()
            while (true) {
                if (it.hasNext()) {
                    element$iv = it.next()
                    Map.Entry it2 = (Map.Entry) element$iv
                    val value: Any = it2.getValue()
                    Intrinsics.checkNotNullExpressionValue(value, "<get-value>(...)")
                    Map.Entry it3 = now - ((Number) value).longValue() > OpenStreetMapTileProviderConstants.ONE_MINUTE ? 1 : null
                    if (it3 != null) {
                        break
                        }
                    } else {
                    element$iv = null
                    break
                    }
                }
            Map.Entry entry = (Map.Entry) element$iv
            val stuckFileId: String = entry != null ? (String) entry.getKey() : null
            if (stuckFileId != null) {
                Log.w(TAG, "sendFile: 检测到卡死任务 " + stuckFileId + "，强制取消 sendJob")
                val job2: Job = sendJob
                if (job2 != null) {
                    Job.DefaultImpls.cancel$default(job2, (CancellationException) null, 1, (Object) null)
                    }
                ackTracker.remove(stuckFileId)
                transferInProgress = false
                } else {
                Log.w(TAG, "sendFile: 上次发送任务仍在进行，忽略新请求")
                return
                }
            }
        if (transferInProgress) {
            Log.w(TAG, "sendFile: transferInProgress 卡死，自动重置")
            transferInProgress = false
            }
        sendJob = BuildersKt.launch$default(scope, null, null, new ConnectionManager$sendFile$1(file, null), 3, null)
        }
    }

public static  Unit sendFile$default(ConnectionManager connectionManager, Uri uri, String str, int i, Object obj) {
    if ((i & 2) != 0) {
        str = null
        }
    connectionManager.sendFile(uri, str)
    }

fun sendFile(uri: Uri, displayName: String): Unit {
    Object element$iv
    Intrinsics.checkNotNullParameter(uri, "uri")
    val job: Job = sendJob
    if (job != null && job.isActive()) {
        val now: Long = System.currentTimeMillis()
        val entrySet: Iterable = ackTracker.entrySet()
        Intrinsics.checkNotNullExpressionValue(entrySet, "<get-entries>(...)")
        Iterable $this$firstOrNull$iv = entrySet
        val it: Iterator = $this$firstOrNull$iv.iterator()
        while (true) {
            if (it.hasNext()) {
                element$iv = it.next()
                Map.Entry it2 = (Map.Entry) element$iv
                val value: Any = it2.getValue()
                Intrinsics.checkNotNullExpressionValue(value, "<get-value>(...)")
                Map.Entry it3 = now - ((Number) value).longValue() > OpenStreetMapTileProviderConstants.ONE_MINUTE ? 1 : null
                if (it3 != null) {
                    break
                    }
                } else {
                element$iv = null
                break
                }
            }
        Map.Entry entry = (Map.Entry) element$iv
        val stuckFileId: String = entry != null ? (String) entry.getKey() : null
        if (stuckFileId != null) {
            Log.w(TAG, "sendFile(Uri): 检测到卡死任务 " + stuckFileId + "，强制取消 sendJob")
            val job2: Job = sendJob
            if (job2 != null) {
                Job.DefaultImpls.cancel$default(job2, (CancellationException) null, 1, (Object) null)
                }
            ackTracker.remove(stuckFileId)
            transferInProgress = false
            } else {
            Log.w(TAG, "sendFile(Uri): 上次发送任务仍在进行，忽略新请求")
            return
            }
        }
    if (transferInProgress) {
        Log.w(TAG, "sendFile(Uri): transferInProgress 卡死，自动重置")
        transferInProgress = false
        }
    val ctx: Context = context
    if (ctx == null) {
        return
        }
    sendJob = BuildersKt.launch$default(scope, null, null, new ConnectionManager$sendFile$2(ctx, displayName, uri, null), 3, null)
    }

    /*
Code decompiled incorrectly, please refer to instructions dump.
    */
fun uploadStreamInternal(str: String, str2: String, j: Long, inputStream: InputStream, j2: Long, continuation: Continuation<? super Unit>): Any {
    var i: Int? = null
    var append: StringBuilder? = null
    var str3: String? = null
    var httpURLConnection: HttpURLConnection? = null
    var th: Throwable? = null
    var connectionManager: ConnectionManager? = null
    var str4: String? = null
    var httpURLConnection2: HttpURLConnection? = null
    var httpURLConnection3: HttpURLConnection? = null
    var httpURLConnection4: HttpURLConnection? = null
    var httpURLConnection5: HttpURLConnection? = null
    var httpURLConnection6: HttpURLConnection? = null
    var httpURLConnection7: HttpURLConnection? = null
    var z: Boolean? = null
    var connectionManager2: ConnectionManager? = null
    var inputStream2: InputStream? = null
    var th2: Throwable? = null
    var outputStream: OutputStream? = null
    var th3: Throwable? = null
    var th4: Throwable? = null
    var outputStream2: OutputStream? = null
    var inputStream3: InputStream? = null
    var read: ??? = null
    var inputStream4: InputStream? = null
    val r1: ?? = this
    val r14: ?? = j2
    val str5: String = pcIp
    if (str5 == null) {
        str5 = DEFAULT_IP
        }
    val str6: String = str5
    if (_currentChannel.getValue() == ChannelType.ADB) {
        i = connectPort
        append = StringBuilder()
        str3 = "http://127.0.0.1:"
        } else {
        i = connectPort
        append = StringBuilder().append("http://").append(str6)
        str3 = ":"
        }
    val sb: String = append.append(str3).append(i).toString()
    val str7: String = TAG
    Log.i(TAG, "uploadStreamInternal: 开始上传 " + str + ", size=" + j + ", base=" + sb + ", offset=" + r14)
    _transferCompleted.setValue(Boxing.boxBoolean(false))
    val obj: Any = null
    try {
        val openConnection: URLConnection = new URL(sb + "/api/upload_file").openConnection()
        Intrinsics.checkNotNull(openConnection, "null cannot be cast to non-null type java.net.HttpURLConnection")
        val httpURLConnection8: HttpURLConnection = (HttpURLConnection) openConnection
        try {
            currentConn = httpURLConnection8
            httpURLConnection8.setConnectTimeout(5000)
            httpURLConnection8.setReadTimeout(120000)
            httpURLConnection8.setDoOutput(true)
            httpURLConnection8.setRequestMethod("POST")
            httpURLConnection8.setRequestProperty("Authorization", "Bearer " + secretToken)
            httpURLConnection8.setRequestProperty("Content-Type", "application/octet-stream")
            httpURLConnection8.setRequestProperty("X-File-Id", str)
            httpURLConnection8.setRequestProperty("X-File-Name", URLEncoder.encode(str2, "UTF-8"))
            httpURLConnection8.setRequestProperty("X-File-Size", String.valueOf(j))
            if (r14 > 0) {
                try {
                    httpURLConnection8.setRequestProperty("X-Resume-Offset", String.valueOf(j2))
                    } catch (Exception e) {
                    e = e
                    connectionManager = r1
                    obj = httpURLConnection8
                    str4 = TAG
                    httpURLConnection2 = null
                    try {
                        if (!transferPaused) {
                            }
                        Boxing.boxInt(Log.w(str4, "uploadStreamInternal interrupted (paused/cancelled): " + e.getMessage()))
                        try {
                            httpURLConnection4 = (HttpURLConnection) obj
                            if (httpURLConnection4 != null) {
                                }
                            } catch (Exception e2) {
                            }
                        currentConn = httpURLConnection2
                        return Unit.INSTANCE
                        } catch (Throwable th5) {
                        th = th5
                        httpURLConnection = httpURLConnection2
                        try {
                            httpURLConnection3 = (HttpURLConnection) obj
                            if (httpURLConnection3 != null) {
                                }
                            } catch (Exception e3) {
                            }
                        currentConn = httpURLConnection
                        var th: throw? = null
                        }
                    } catch (Throwable th6) {
                    obj = httpURLConnection8
                    httpURLConnection = null
                    th = th6
                    httpURLConnection3 = (HttpURLConnection) obj
                    if (httpURLConnection3 != null) {
                        }
                    currentConn = httpURLConnection
                    var th: throw? = null
                    }
                }
            val j3: Long = j - r14
            if (j3 > 0) {
                httpURLConnection8.setFixedLengthStreamingMode(j3)
                }
            ackTracker.put(str, Boxing.boxLong(System.currentTimeMillis()))
            val z2: Boolean = false
            try {
                try {
                    try {
                        val inputStream5: InputStream = inputStream
                        try {
                            val inputStream6: InputStream = inputStream5
                            try {
                                val outputStream3: OutputStream = httpURLConnection8.getOutputStream()
                                try {
                                    outputStream2 = outputStream3
                                    val bArr: Array<Byte> = new byte[65536]
                                    val j4: Long = j2
                                    while (true) {
                                        inputStream3 = inputStream5
                                        try {
                                            read = inputStream6.read(bArr)
                                            val inputStream7: InputStream = inputStream6
                                            if (read == -1) {
                                                break
                                                }
                                            try {
                                                if (fileTransferCancel) {
                                                    break
                                                    }
                                                val r3: ?? = outputStream2
                                                val j5: Long = j3
                                                try {
                                                    r3.write(bArr, 0, read)
                                                    val j6: Long = j4 + ((long) read)
                                                    val bArr2: Array<Byte> = bArr
                                                    val resumeInfo2: ResumeInfo = resumeInfo
                                                    if (resumeInfo2 != null) {
                                                        try {
                                                            resumeInfo2.setResumeOffset(j6)
                                                            } catch (Throwable th7) {
                                                            th3 = th7
                                                            outputStream = outputStream3
                                                            inputStream2 = inputStream3
                                                            try {
                                                                var th3: throw? = null
                                                                } catch (Throwable th8) {
                                                                try {
                                                                    CloseableKt.closeFinally(outputStream, th3)
                                                                    var th8: throw? = null
                                                                    } catch (Throwable th9) {
                                                                    th4 = th9
                                                                    th2 = th4
                                                                    try {
                                                                        var th2: throw? = null
                                                                        } catch (Throwable th10) {
                                                                        CloseableKt.closeFinally(inputStream2, th2)
                                                                        var th10: throw? = null
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    val outputStream4: OutputStream = outputStream3
                                                    try {
                                                        val httpURLConnection9: HttpURLConnection = httpURLConnection8
                                                        val str8: String = sb
                                                        val str9: String = str7
                                                        outputStream = outputStream4
                                                        try {
                                                            _fileTransferProgress.setValue(TransferProgress(str, str2, j6, j, false))
                                                            ackTracker.put(str, Boxing.boxLong(System.currentTimeMillis()))
                                                            str7 = str9
                                                            outputStream3 = outputStream
                                                            j3 = j5
                                                            inputStream6 = inputStream7
                                                            sb = str8
                                                            bArr = bArr2
                                                            inputStream5 = inputStream3
                                                            httpURLConnection8 = httpURLConnection9
                                                            j4 = j6
                                                            outputStream2 = r3
                                                            } catch (Throwable th11) {
                                                            th3 = th11
                                                            inputStream2 = inputStream3
                                                            var th3: throw? = null
                                                            }
                                                        } catch (Throwable th12) {
                                                        th = th12
                                                        inputStream4 = inputStream3
                                                        outputStream = outputStream4
                                                        th3 = th
                                                        inputStream2 = inputStream4
                                                        var th3: throw? = null
                                                        }
                                                    } catch (Throwable th13) {
                                                    th = th13
                                                    outputStream = outputStream3
                                                    inputStream4 = inputStream3
                                                    }
                                                } catch (Throwable th14) {
                                                outputStream = outputStream3
                                                th3 = th14
                                                inputStream2 = inputStream3
                                                }
                                            } catch (Throwable th15) {
                                            th = th15
                                            outputStream = outputStream3
                                            inputStream2 = inputStream3
                                            th3 = th
                                            var th3: throw? = null
                                            }
                                        }
                                    httpURLConnection7 = httpURLConnection8
                                    r14 = str7
                                    outputStream = outputStream3
                                    z = true
                                    } catch (Throwable th16) {
                                    th = th16
                                    inputStream2 = inputStream5
                                    outputStream = outputStream3
                                    }
                                try {
                                    outputStream2.flush()
                                    val unit: Unit = Unit.INSTANCE
                                    r1 = 0
                                    try {
                                        CloseableKt.closeFinally(outputStream, null)
                                        val unit2: Unit = Unit.INSTANCE
                                        CloseableKt.closeFinally(inputStream3, null)
                                        connectionManager2 = read
                                        } catch (Throwable th17) {
                                        th4 = th17
                                        inputStream2 = inputStream3
                                        th2 = th4
                                        var th2: throw? = null
                                        }
                                    } catch (Throwable th18) {
                                    inputStream2 = inputStream3
                                    th3 = th18
                                    var th3: throw? = null
                                    }
                                } catch (Throwable th19) {
                                inputStream2 = inputStream5
                                th2 = th19
                                }
                            } catch (Throwable th20) {
                            inputStream2 = inputStream5
                            th2 = th20
                            }
                        } catch (Throwable th21) {
                        th = th21
                        th = th
                        obj = httpURLConnection7
                        httpURLConnection = r1
                        httpURLConnection3 = (HttpURLConnection) obj
                        if (httpURLConnection3 != null) {
                            httpURLConnection3.disconnect()
                            }
                        currentConn = httpURLConnection
                        var th: throw? = null
                        }
                    } catch (Exception e4) {
                    e = e4
                    z2 = true
                    try {
                        val resumeInfo3: ResumeInfo = resumeInfo
                        val boxLong: Long = resumeInfo3 == null ? Boxing.boxLong(resumeInfo3.getResumeOffset()) : r1
                        Log.w(r14, "uploadStreamInternal: 上传流中断 sent=" + boxLong + "/" + j + ", cancel=" + fileTransferCancel + ", paused=" + transferPaused + ", err=" + e.getMessage())
                        r1 = r1
                        connectionManager2 = ", err="
                        r14 = r14
                        if (transferPaused) {
                            }
                        httpURLConnection7.disconnect()
                        currentConn = r1
                        } catch (Exception e5) {
                        e = e5
                        connectionManager2 = this
                        obj = httpURLConnection7
                        httpURLConnection2 = r1
                        connectionManager = connectionManager2
                        str4 = r14
                        if (!transferPaused && !fileTransferCancel) {
                            Log.e(str4, "uploadStreamInternal failed", e)
                            connectionManager.showToast("上传异常: " + e.getMessage())
                            _fileTransferProgress.setValue(httpURLConnection2)
                            httpURLConnection4 = (HttpURLConnection) obj
                            if (httpURLConnection4 != null) {
                                httpURLConnection4.disconnect()
                                }
                            currentConn = httpURLConnection2
                            return Unit.INSTANCE
                            }
                        Boxing.boxInt(Log.w(str4, "uploadStreamInternal interrupted (paused/cancelled): " + e.getMessage()))
                        httpURLConnection4 = (HttpURLConnection) obj
                        if (httpURLConnection4 != null) {
                            }
                        currentConn = httpURLConnection2
                        return Unit.INSTANCE
                        }
                    return Unit.INSTANCE
                    }
                } catch (Exception e6) {
                e = e6
                httpURLConnection7 = httpURLConnection8
                r14 = TAG
                r1 = 0
                z = true
                z2 = true
                val resumeInfo32: ResumeInfo = resumeInfo
                if (resumeInfo32 == null) {
                    }
                Log.w(r14, "uploadStreamInternal: 上传流中断 sent=" + boxLong + "/" + j + ", cancel=" + fileTransferCancel + ", paused=" + transferPaused + ", err=" + e.getMessage())
                r1 = r1
                connectionManager2 = ", err="
                r14 = r14
                if (transferPaused) {
                    }
                httpURLConnection7.disconnect()
                currentConn = r1
                return Unit.INSTANCE
                } catch (Throwable th22) {
                th = th22
                httpURLConnection5 = httpURLConnection8
                httpURLConnection6 = null
                th = th
                obj = httpURLConnection5
                httpURLConnection = httpURLConnection6
                httpURLConnection3 = (HttpURLConnection) obj
                if (httpURLConnection3 != null) {
                    }
                currentConn = httpURLConnection
                var th: throw? = null
                }
            if (transferPaused) {
                Log.i(r14, "uploadStreamInternal: 上传已暂停，保留进度")
                val mutableStateFlow: MutableStateFlow<TransferProgress> = _fileTransferProgress
                val resumeInfo4: ResumeInfo = resumeInfo
                mutableStateFlow.setValue(TransferProgress(str, str2, resumeInfo4 != null ? resumeInfo4.getResumeOffset() : j2, j, false))
                connectionManager2 = this
                } else if (fileTransferCancel) {
                Log.w(r14, "uploadStreamInternal: 上传被取消")
                _fileTransferProgress.setValue(r1)
                connectionManager2 = this
                } else {
                try {
                    if (z2) {
                        val connectionManager3: ConnectionManager = this
                        connectionManager3.showToast("上传中断: " + str2)
                        _fileTransferProgress.setValue(r1)
                        connectionManager2 = connectionManager3
                        } else {
                        val responseCode: Int = httpURLConnection7.getResponseCode()
                        if (responseCode == 200) {
                            sendFileComplete(str)
                            startAckWait(str)
                            resumeInfo = r1
                            Log.i(r14, "uploadStreamInternal: 上传完成, respCode=" + responseCode)
                            _transferCompleted.setValue(Boxing.boxBoolean(z))
                            _completedTransfer.tryEmit(CompletedTransfer(str2, z))
                            _fileTransferProgress.setValue(r1)
                            connectionManager2 = this
                            } else {
                            Log.e(r14, "uploadStreamInternal: 上传失败 respCode=" + responseCode)
                            val connectionManager4: ConnectionManager = this
                            connectionManager4.showToast("上传失败: HTTP " + responseCode)
                            _fileTransferProgress.setValue(r1)
                            connectionManager2 = connectionManager4
                            }
                        }
                    } catch (Exception e7) {
                    e = e7
                    obj = httpURLConnection7
                    httpURLConnection2 = r1
                    connectionManager = connectionManager2
                    str4 = r14
                    if (!transferPaused) {
                        Log.e(str4, "uploadStreamInternal failed", e)
                        connectionManager.showToast("上传异常: " + e.getMessage())
                        _fileTransferProgress.setValue(httpURLConnection2)
                        httpURLConnection4 = (HttpURLConnection) obj
                        if (httpURLConnection4 != null) {
                            }
                        currentConn = httpURLConnection2
                        return Unit.INSTANCE
                        }
                    Boxing.boxInt(Log.w(str4, "uploadStreamInternal interrupted (paused/cancelled): " + e.getMessage()))
                    httpURLConnection4 = (HttpURLConnection) obj
                    if (httpURLConnection4 != null) {
                        }
                    currentConn = httpURLConnection2
                    return Unit.INSTANCE
                    } catch (Throwable th23) {
                    th = th23
                    th = th
                    obj = httpURLConnection7
                    httpURLConnection = r1
                    httpURLConnection3 = (HttpURLConnection) obj
                    if (httpURLConnection3 != null) {
                        }
                    currentConn = httpURLConnection
                    var th: throw? = null
                    }
                }
            httpURLConnection7.disconnect()
            currentConn = r1
            } catch (Exception e8) {
            e = e8
            connectionManager = r1
            str4 = TAG
            httpURLConnection2 = null
            obj = httpURLConnection8
            } catch (Throwable th24) {
            th = th24
            httpURLConnection5 = httpURLConnection8
            httpURLConnection6 = null
            }
        } catch (Exception e9) {
        e = e9
        connectionManager = r1
        str4 = TAG
        httpURLConnection2 = null
        } catch (Throwable th25) {
        httpURLConnection = null
        th = th25
        }
    return Unit.INSTANCE
    }

fun sendFileWifi(fileId: String, file: File, fileSize: Long, continuation: Continuation<? super Unit>): Any {
    val resumeInfo2: ResumeInfo = resumeInfo
    val resumeOffset: Long = resumeInfo2 != null ? resumeInfo2.getResumeOffset() : 0L
    if (resumeInfo == null) {
        val name: String = file.getName()
        Intrinsics.checkNotNullExpressionValue(name, "getName(...)")
        resumeInfo = ResumeInfo(fileId, name, fileSize, file, null, 0L, 48, null)
        }
    val fis: FileInputStream = new FileInputStream(file)
    if (resumeOffset > 0) {
        val skipped: Long = fis.skip(resumeOffset)
        Log.i(TAG, "sendFileWifi: 跳过 " + skipped + " 字节 (offset=" + resumeOffset + ")")
        }
    val name2: String = file.getName()
    Intrinsics.checkNotNullExpressionValue(name2, "getName(...)")
    val uploadStreamInternal: Any = uploadStreamInternal(fileId, name2, fileSize, fis, resumeOffset, continuation)
    val uploadStreamInternal: return = = IntrinsicsKt.getCOROUTINE_SUSPENDED() ? uploadStreamInternal : Unit.INSTANCE
    }

fun sendFileWifiFromUri(fileId: String, uri: Uri, fileName: String, fileSize: Long, continuation: Continuation<? super Unit>): Any {
    val ctx: Context = context
    if (ctx == null) {
        return Unit.INSTANCE
        }
    val resumeInfo2: ResumeInfo = resumeInfo
    val resumeOffset: Long = resumeInfo2 != null ? resumeInfo2.getResumeOffset() : 0L
    if (resumeInfo == null) {
        resumeInfo = ResumeInfo(fileId, fileName, fileSize, null, uri, 0L, 40, null)
        }
    val fis: InputStream = ctx.getContentResolver().openInputStream(uri)
    if (fis == null) {
        return Unit.INSTANCE
        }
    if (resumeOffset > 0) {
        val skipped: Long = fis.skip(resumeOffset)
        Log.i(TAG, "sendFileWifiFromUri: 跳过 " + skipped + " 字节 (offset=" + resumeOffset + ")")
        }
    val uploadStreamInternal: Any = uploadStreamInternal(fileId, fileName, fileSize, fis, resumeOffset, continuation)
    val uploadStreamInternal: return = = IntrinsicsKt.getCOROUTINE_SUSPENDED() ? uploadStreamInternal : Unit.INSTANCE
    }

fun sendFileComplete(fileId: String): Unit {
    val msg: JsonObject = buildJsonMessage(new Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda24
    override
    fun invoke(obj: Any): Any {
        Unit sendFileComplete$lambda$47
        sendFileComplete$lambda$47 = ConnectionManager.sendFileComplete$lambda$47(fileId, (JsonObjectBuilder) obj)
        return sendFileComplete$lambda$47
        }
    })
BuildersKt.launch$default(scope, null, null, new ConnectionManager$sendFileComplete$1(msg, null), 3, null)
}

public static final Unit sendFileComplete$lambda$47(final String $fileId, JsonObjectBuilder buildJsonMessage) {
    Intrinsics.checkNotNullParameter(buildJsonMessage, "$this$buildJsonMessage")
    JsonElementBuildersKt.put(buildJsonMessage, "source", "phone")
    JsonElementBuildersKt.putJsonObject(buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda30
        override
        fun invoke(obj: Any): Any {
            Unit sendFileComplete$lambda$47$lambda$46
            sendFileComplete$lambda$47$lambda$46 = ConnectionManager.sendFileComplete$lambda$47$lambda$46($fileId, (JsonObjectBuilder) obj)
            return sendFileComplete$lambda$47$lambda$46
            }
        })
    return Unit.INSTANCE
    }

public static final Unit sendFileComplete$lambda$47$lambda$46(String $fileId, JsonObjectBuilder putJsonObject) {
    Intrinsics.checkNotNullParameter(putJsonObject, "$this$putJsonObject")
    JsonElementBuildersKt.put(putJsonObject, "action", "file_complete")
    JsonElementBuildersKt.put(putJsonObject, "file_id", $fileId)
    return Unit.INSTANCE
    }

fun startAckWait(fileId: String): Unit {
    BuildersKt.launch$default(scope, null, null, new ConnectionManager$startAckWait$1(fileId, null), 3, null)
    }

fun startReceiveFile(fileId: String, fileName: String, fileSize: Long): Unit {
    val job: Job = receiveJob
    if (job != null) {
        Job.DefaultImpls.cancel$default(job, (CancellationException) null, 1, (Object) null)
        }
    fileReceiveState.put(fileId, FileReceiveState(fileId, fileName, fileSize, 0L, 0, 24, null))
    Log.i(TAG, "startReceiveFile(流式): fileId=" + fileId + ", name=" + fileName + ", size=" + fileSize + ", receiveDir=" + receiveDir)
    _fileTransferProgress.setValue(TransferProgress(fileId, fileName, 0L, fileSize, true))
    _transferCompleted.setValue(false)
    receiveJob = BuildersKt.launch$default(scope, null, null, new ConnectionManager$startReceiveFile$1(fileId, fileName, fileSize, null), 3, null)
    }

fun showToast(msg: String): Unit {
    val ctx: Context = context
    if (ctx == null) {
        return
        }
    mainHandler.post(Runnable() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda1
        override
        fun run(): Unit {
            ConnectionManager.showToast$lambda$48(ctx, msg)
            }
        })
    }

public static final Unit showToast$lambda$48(Context $ctx, String $msg) {
    Toast.makeText($ctx, $msg, 0).show()
    }

fun downloadChunk(fileId: String, partNum: Int, continuation: Continuation<Array<? super byte>>): Any {
    var null: return? = null
    }

fun completeFileReceive(fileId: String): Unit {
    _fileTransferProgress.setValue(null)
    fileReceiveState.remove(fileId)
    }

fun sendAck(fileId: String): Unit {
    val msg: JsonObject = buildJsonMessage(new Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda31
    override
    fun invoke(obj: Any): Any {
        Unit sendAck$lambda$50
        sendAck$lambda$50 = ConnectionManager.sendAck$lambda$50(fileId, (JsonObjectBuilder) obj)
        return sendAck$lambda$50
        }
    })
BuildersKt.launch$default(scope, null, null, new ConnectionManager$sendAck$1(msg, null), 3, null)
}

public static final Unit sendAck$lambda$50(final String $fileId, JsonObjectBuilder buildJsonMessage) {
    Intrinsics.checkNotNullParameter(buildJsonMessage, "$this$buildJsonMessage")
    JsonElementBuildersKt.put(buildJsonMessage, "source", "phone")
    JsonElementBuildersKt.putJsonObject(buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda10
        override
        fun invoke(obj: Any): Any {
            Unit sendAck$lambda$50$lambda$49
            sendAck$lambda$50$lambda$49 = ConnectionManager.sendAck$lambda$50$lambda$49($fileId, (JsonObjectBuilder) obj)
            return sendAck$lambda$50$lambda$49
            }
        })
    return Unit.INSTANCE
    }

public static final Unit sendAck$lambda$50$lambda$49(String $fileId, JsonObjectBuilder putJsonObject) {
    Intrinsics.checkNotNullParameter(putJsonObject, "$this$putJsonObject")
    JsonElementBuildersKt.put(putJsonObject, "action", "file_complete")
    JsonElementBuildersKt.put(putJsonObject, "file_id", $fileId)
    return Unit.INSTANCE
    }

fun sendTransferControl(ctrl: String): Unit {
    var fileId: String? = null
    val resumeInfo2: ResumeInfo = resumeInfo
    if (resumeInfo2 == null || (fileId = resumeInfo2.getFileId()) == null) {
        val pendingSendInfo: PendingSendInfo = pendingSend
        fileId = pendingSendInfo != null ? pendingSendInfo.getFileId() : ""
        }
    val msg: JsonObject = buildJsonMessage(new Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda21
    override
    fun invoke(obj: Any): Any {
        Unit sendTransferControl$lambda$52
        sendTransferControl$lambda$52 = ConnectionManager.sendTransferControl$lambda$52(ctrl, fileId, (JsonObjectBuilder) obj)
        return sendTransferControl$lambda$52
        }
    })
BuildersKt.launch$default(scope, null, null, new ConnectionManager$sendTransferControl$1(msg, null), 3, null)
Log.i(TAG, "发送 transfer_control: ctrl=" + ctrl + ", fileId=" + fileId)
}

public static final Unit sendTransferControl$lambda$52(final String $ctrl, final String $fileId, JsonObjectBuilder buildJsonMessage) {
    Intrinsics.checkNotNullParameter(buildJsonMessage, "$this$buildJsonMessage")
    JsonElementBuildersKt.put(buildJsonMessage, "source", "phone")
    JsonElementBuildersKt.putJsonObject(buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda29
        override
        fun invoke(obj: Any): Any {
            Unit sendTransferControl$lambda$52$lambda$51
            sendTransferControl$lambda$52$lambda$51 = ConnectionManager.sendTransferControl$lambda$52$lambda$51($ctrl, $fileId, (JsonObjectBuilder) obj)
            return sendTransferControl$lambda$52$lambda$51
            }
        })
    return Unit.INSTANCE
    }

public static final Unit sendTransferControl$lambda$52$lambda$51(String $ctrl, String $fileId, JsonObjectBuilder putJsonObject) {
    Intrinsics.checkNotNullParameter(putJsonObject, "$this$putJsonObject")
    JsonElementBuildersKt.put(putJsonObject, "action", "transfer_control")
    JsonElementBuildersKt.put(putJsonObject, "ctrl", $ctrl)
    JsonElementBuildersKt.put(putJsonObject, "file_id", $fileId)
    return Unit.INSTANCE
    }

fun cancelTransfer(): Unit {
    fileTransferCancel = true
    transferPaused = false
    _transferPausedFromPc.setValue(false)
    _transferCompleted.setValue(false)
    try {
        val httpURLConnection: HttpURLConnection = currentConn
        if (httpURLConnection != null) {
            httpURLConnection.disconnect()
            }
        } catch (Exception e) {
        }
    sendTransferControl("cancel")
    val job: Job = sendJob
    if (job != null) {
        Job.DefaultImpls.cancel$default(job, (CancellationException) null, 1, (Object) null)
        }
    val job2: Job = receiveJob
    if (job2 != null) {
        Job.DefaultImpls.cancel$default(job2, (CancellationException) null, 1, (Object) null)
        }
    transferInProgress = false
    resumeInfo = null
    pendingSend = null
    _fileTransferProgress.setValue(null)
    cancelFileTransferNotification()
    }

fun pauseTransfer(): Unit {
    fileTransferCancel = true
    transferPaused = true
    _transferPausedFromPc.setValue(true)
    try {
        val httpURLConnection: HttpURLConnection = currentConn
        if (httpURLConnection != null) {
            httpURLConnection.disconnect()
            }
        } catch (Exception e) {
        }
    sendTransferControl("pause")
    }

fun resumeTransfer(): Unit {
    transferPaused = false
    fileTransferCancel = false
    _transferPausedFromPc.setValue(false)
    sendTransferControl("resume")
    val info: ResumeInfo = resumeInfo
    if (info != null) {
        BuildersKt.launch$default(scope, null, null, new ConnectionManager$resumeTransfer$1(info, null), 3, null)
        } else {
        Integer.valueOf(Log.w(TAG, "resumeTransfer: 没有 resumeInfo，无法恢复"))
        }
    }

fun isTransferPaused(): Boolean {
    var transferPaused: return? = null
    }

fun resetTransferCancel(): Unit {
    fileTransferCancel = false
    }

fun autoInstallApk(path: String): Unit {
    BuildersKt.launch$default(scope, null, null, new ConnectionManager$autoInstallApk$1(path, null), 3, null)
    }

fun doInstallApk(file: File): Unit {
    val ctx: Context = context
    if (ctx == null) {
        return
        }
    try {
        if (isAdbAvailable()) {
            val result: Int = Runtime.getRuntime().exec(new String[]{"sh", "-c", "pm install -r -t " + file.getAbsolutePath()}).waitFor()
            if (result == 0) {
                Log.i(TAG, "APK installed via pm: " + file.getName())
                mainHandler.post(Runnable() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda15
                    override
                    fun run(): Unit {
                        ConnectionManager.doInstallApk$lambda$53(ctx, file)
                        }
                    })
                return
                }
            }
        } catch (Exception e) {
        Log.e(TAG, "pm install failed, fallback to intent", e)
        }
    mainHandler.post(Runnable() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda16
        override
        fun run(): Unit {
            ConnectionManager.doInstallApk$lambda$54(ctx, file)
            }
        })
    }

public static final Unit doInstallApk$lambda$53(Context $ctx, File $file) {
    Toast.makeText($ctx, "已安装: " + $file.getName(), 0).show()
    }

public static final Unit doInstallApk$lambda$54(Context $ctx, File $file) {
    try {
        val intent: Intent = new Intent("android.intent.action.VIEW")
        val uri: Uri = FileProvider.getUriForFile($ctx, $ctx.getPackageName() + ".fileprovider", $file)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(1)
        intent.addFlags(268435456)
        $ctx.startActivity(intent)
        } catch (Exception e) {
        Log.e(TAG, "APK install intent failed", e)
        }
    }

public static  Unit sendAction$default(ConnectionManager connectionManager, String str, Map map, int i, Object obj) {
    if ((i & 2) != 0) {
        map = MapsKt.emptyMap()
        }
    connectionManager.sendAction(str, map)
    }

fun sendAction(action: String, extra: Map<String, ? extends Object>): Unit {
    Intrinsics.checkNotNullParameter(action, "action")
    Intrinsics.checkNotNullParameter(extra, "extra")
    val msg: JsonObject = buildJsonMessage(new Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda14
    override
    fun invoke(obj: Any): Any {
        Unit sendAction$lambda$56
        sendAction$lambda$56 = ConnectionManager.sendAction$lambda$56(action, extra, (JsonObjectBuilder) obj)
        return sendAction$lambda$56
        }
    })
BuildersKt.launch$default(scope, null, null, new ConnectionManager$sendAction$1(msg, null), 3, null)
}

public static final Unit sendAction$lambda$56(final String $action, final Map $extra, JsonObjectBuilder buildJsonMessage) {
    Intrinsics.checkNotNullParameter(buildJsonMessage, "$this$buildJsonMessage")
    JsonElementBuildersKt.put(buildJsonMessage, "source", "phone")
    JsonElementBuildersKt.putJsonObject(buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda7
        override
        fun invoke(obj: Any): Any {
            Unit sendAction$lambda$56$lambda$55
            sendAction$lambda$56$lambda$55 = ConnectionManager.sendAction$lambda$56$lambda$55($action, $extra, (JsonObjectBuilder) obj)
            return sendAction$lambda$56$lambda$55
            }
        })
    return Unit.INSTANCE
    }

public static final Unit sendAction$lambda$56$lambda$55(String $action, Map $extra, JsonObjectBuilder putJsonObject) {
    Intrinsics.checkNotNullParameter(putJsonObject, "$this$putJsonObject")
    JsonElementBuildersKt.put(putJsonObject, "action", $action)
    for (Map.Entry entry : $extra.entrySet()) {
        val k: String = (String) entry.getKey()
        val v: Any = entry.getValue()
        if (v is String) {
            JsonElementBuildersKt.put(putJsonObject, k, v)
            } else if (v is Number) {
            JsonElementBuildersKt.put(putJsonObject, k, (Number) v)
            } else if (v is Boolean) {
            JsonElementBuildersKt.put(putJsonObject, k, (Boolean) v)
            }
        }
    return Unit.INSTANCE
    }

fun startPcFramePolling(controlMode: Boolean): Unit {
    pcFrameControlMode = controlMode
    val job: Job = pcFrameJob
    if (job != null) {
        Job.DefaultImpls.cancel$default(job, (CancellationException) null, 1, (Object) null)
        }
    pcFrameJob = BuildersKt.launch$default(scope, null, null, new ConnectionManager$startPcFramePolling$1(null), 3, null)
    }

fun stopPcFramePolling(): Unit {
    val job: Job = pcFrameJob
    if (job != null) {
        Job.DefaultImpls.cancel$default(job, (CancellationException) null, 1, (Object) null)
        }
    pcFrameJob = null
    }

fun isPcFramePolling(): Boolean {
    val job: Job = pcFrameJob
    return job != null && job.isActive()
    }

fun startPcAudioPolling(): Unit {
    stopPcAudioPolling()
    val bufSize: Int = AudioTrack.getMinBufferSize(pcAudioSampleRate, 12, 2)
    try {
        pcAudioTrack = new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(1).setContentType(2).build()).setAudioFormat(new AudioFormat.Builder().setEncoding(2).setSampleRate(pcAudioSampleRate).setChannelMask(12).build()).setBufferSizeInBytes(Math.max(bufSize, 4096)).setTransferMode(1).build()
        val audioTrack: AudioTrack = pcAudioTrack
        if (audioTrack != null) {
            audioTrack.play()
            }
        } catch (Exception e) {
        Log.e(TAG, "AudioTrack init failed", e)
        }
    pcAudioJob = BuildersKt.launch$default(scope, null, null, new ConnectionManager$startPcAudioPolling$1(null), 3, null)
    }

fun stopPcAudioPolling(): Unit {
    val job: Job = pcAudioJob
    if (job != null) {
        Job.DefaultImpls.cancel$default(job, (CancellationException) null, 1, (Object) null)
        }
    pcAudioJob = null
    try {
        val audioTrack: AudioTrack = pcAudioTrack
        if (audioTrack != null) {
            audioTrack.stop()
            }
        val audioTrack2: AudioTrack = pcAudioTrack
        if (audioTrack2 != null) {
            audioTrack2.release()
            }
        } catch (Exception e) {
        }
    pcAudioTrack = null
    }

fun isPcAudioPolling(): Boolean {
    val job: Job = pcAudioJob
    return job != null && job.isActive()
    }

fun startPcCameraPolling(): Unit {
    val job: Job = pcCameraJob
    if (job != null) {
        Job.DefaultImpls.cancel$default(job, (CancellationException) null, 1, (Object) null)
        }
    pcCameraJob = BuildersKt.launch$default(scope, null, null, new ConnectionManager$startPcCameraPolling$1(null), 3, null)
    }

fun stopPcCameraPolling(): Unit {
    val job: Job = pcCameraJob
    if (job != null) {
        Job.DefaultImpls.cancel$default(job, (CancellationException) null, 1, (Object) null)
        }
    pcCameraJob = null
    }

fun isPcCameraPolling(): Boolean {
    val job: Job = pcCameraJob
    return job != null && job.isActive()
    }

fun handleAppListRequest(): Unit {
    BuildersKt.launch$default(scope, null, null, new ConnectionManager$handleAppListRequest$1(null), 3, null)
    }

fun handleAppUninstallRequest(pkg: String): Unit {
    BuildersKt.launch$default(scope, Dispatchers.getIO(), null, new ConnectionManager$handleAppUninstallRequest$1(pkg, null), 2, null)
    }

fun handleAppApkRequest(pkg: String): Unit {
    BuildersKt.launch$default(scope, Dispatchers.getIO(), null, new ConnectionManager$handleAppApkRequest$1(pkg, null), 2, null)
    }

fun handleFileListRequest(path: String): Unit {
    BuildersKt.launch$default(scope, null, null, new ConnectionManager$handleFileListRequest$1(path, null), 3, null)
    }

fun handleFileDelete(path: String, isDir: Boolean): Unit {
    BuildersKt.launch$default(scope, null, null, new ConnectionManager$handleFileDelete$1(path, isDir, null), 3, null)
    }

fun handleFileRename(oldPath: String, newPath: String): Unit {
    BuildersKt.launch$default(scope, null, null, new ConnectionManager$handleFileRename$1(oldPath, newPath, null), 3, null)
    }

fun handleFileMkdir(path: String): Unit {
    BuildersKt.launch$default(scope, null, null, new ConnectionManager$handleFileMkdir$1(path, null), 3, null)
    }

fun handleFileCopy(src: String, dst: String, isDir: Boolean): Unit {
    BuildersKt.launch$default(scope, null, null, new ConnectionManager$handleFileCopy$1(src, dst, isDir, null), 3, null)
    }

fun handleSendFileRequest(path: String): Unit {
    BuildersKt.launch$default(scope, null, null, new ConnectionManager$handleSendFileRequest$1(path, null), 3, null)
    }

fun cacheMediaProjectionToken(resultCode: Int, data: Intent): Unit {
    Intrinsics.checkNotNullParameter(data, "data")
    cachedProjectionResultCode = resultCode
    cachedProjectionData = data
    val ctx: Context = context
    if (ctx == null) {
        return
        }
    if (projectionManager == null) {
        val systemService: Any = ctx.getSystemService("media_projection")
        projectionManager = systemService is MediaProjectionManager ? (MediaProjectionManager) systemService : null
        }
    Log.d(TAG, "MediaProjection token 已缓存，可后台静默截图")
    }

fun hasCachedProjectionToken(): Boolean {
    return (cachedProjectionData == null || cachedProjectionResultCode == 0) ? false : true
    }

fun getCachedMediaProjection(): MediaProjection {
    val ctx: Context = context
    if (ctx == null) {
        var null: return? = null
        }
    if (Build.VERSION.SDK_INT >= 34 && ScreenCaptureService.INSTANCE.isRunning()) {
        val companion: ScreenCaptureService = ScreenCaptureService.INSTANCE.getInstance()
        if (companion != null) {
            return companion.getProjection()
            }
        var null: return? = null
        }
    val pm: MediaProjectionManager = projectionManager
    if (pm == null) {
        val systemService: Any = ctx.getSystemService("media_projection")
        pm = systemService is MediaProjectionManager ? (MediaProjectionManager) systemService : null
        if (pm != null) {
            val it: MediaProjectionManager = pm
            projectionManager = it
            } else {
            pm = null
            }
        if (pm == null) {
            var null: return? = null
            }
        }
    val rc: Int = cachedProjectionResultCode
    val data: Intent = cachedProjectionData
    if (data == null) {
        var null: return? = null
        }
    try {
        if (Build.VERSION.SDK_INT >= 34 && activeProjection != null) {
            try {
                val mediaProjection: MediaProjection = activeProjection
                if (mediaProjection != null) {
                    mediaProjection.stop()
                    }
                } catch (Exception e) {
                }
            activeProjection = null
            }
        val projection: MediaProjection = pm.getMediaProjection(rc, data)
        if (Build.VERSION.SDK_INT >= 34) {
            activeProjection = projection
            if (projection != null) {
                projection.registerCallback(new MediaProjection.Callback() { // from class: com.phonehub.ConnectionManager$getCachedMediaProjection$1
                    override
                    fun onStop(): Unit {
                        val connectionManager: ConnectionManager = ConnectionManager.INSTANCE
                        ConnectionManager.activeProjection = null
                        }
                    }, Handler(Looper.getMainLooper()))
                }
            }
        var projection: return? = null
        } catch (Exception e2) {
        Log.e(TAG, "getCachedMediaProjection failed", e2)
        var null: return? = null
        }
    }

fun triggerScreenshot(): Unit {
    val ctx: Context = context
    if (ctx == null) {
        return
        }
    if (Build.VERSION.SDK_INT >= 34) {
        if (hasCachedProjectionToken() && ScreenCaptureService.INSTANCE.isRunning()) {
            BuildersKt.launch$default(scope, Dispatchers.getIO(), null, new ConnectionManager$triggerScreenshot$1(ctx, null), 2, null)
            return
            } else if (hasCachedProjectionToken()) {
            ScreenCaptureService.INSTANCE.start(ctx)
            BuildersKt.launch$default(scope, Dispatchers.getIO(), null, new ConnectionManager$triggerScreenshot$2(ctx, null), 2, null)
            return
            } else {
            launchScreenshotActivity(ctx)
            val unit: Unit = Unit.INSTANCE
            return
            }
        }
    if (hasCachedProjectionToken()) {
        BuildersKt.launch$default(scope, Dispatchers.getIO(), null, new ConnectionManager$triggerScreenshot$3(ctx, null), 2, null)
        } else {
        launchScreenshotActivity(ctx)
        val unit2: Unit = Unit.INSTANCE
        }
    }

fun launchScreenshotActivity(ctx: Context): Unit {
    val intent: Intent = new Intent(ctx, (Class<?>) ScreenshotActivity.class)
    intent.setFlags(268435456)
    ctx.startActivity(intent)
    }

fun performBackgroundScreenshot(continuation: Continuation<? super Boolean>): Any {
    return BuildersKt.withContext(Dispatchers.getIO(), new ConnectionManager$performBackgroundScreenshot$2(null), continuation)
    }

fun saveBitmapToGallery(ctx: Context, bmp: Bitmap, fileName: String): Unit {
    var openOutputStream: OutputStream? = null
    try {
        val resolver: ContentResolver = ctx.getContentResolver()
        val values: ContentValues = new ContentValues()
        values.put("_display_name", fileName)
        values.put("mime_type", "image/png")
        values.put("relative_path", Environment.DIRECTORY_PICTURES + "/PhoneHub")
        val uri: Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null && (openOutputStream = resolver.openOutputStream(uri)) != null) {
            val outputStream: OutputStream = openOutputStream
            try {
                val os: OutputStream = outputStream
                Boolean.valueOf(bmp.compress(Bitmap.CompressFormat.PNG, 100, os))
                CloseableKt.closeFinally(outputStream, null)
                } finally {
                }
            }
        } catch (Exception e) {
        Log.e(TAG, "saveBitmapToGallery failed", e)
        }
    }

public static  Unit openUrlOnDevice$default(ConnectionManager connectionManager, String str, boolean z, int i, Object obj) {
    if ((i & 2) != 0) {
        z = false
        }
    connectionManager.openUrlOnDevice(str, z)
    }

fun openUrlOnDevice(url: String, forceVia: Boolean): Unit {
    Intrinsics.checkNotNullParameter(url, "url")
    try {
        setClipboardContent(url)
        val isAdb: Boolean = _currentChannel.getValue() == ChannelType.ADB
        if (isAdb || forceVia) {
            val viaPackages: List<String> = CollectionsKt.listOf((Object[]) new String[]{"mark.via", "mark.via.gp"})
            val opened: Boolean = false
            for (String pkg : viaPackages) {
                try {
                    val intent: Intent = new Intent("android.intent.action.VIEW", Uri.parse(url))
                    intent.setPackage(pkg)
                    intent.setFlags(268435456)
                    val context2: Context = context
                    if (context2 != null) {
                        context2.startActivity(intent)
                        }
                    opened = true
                    break
                    } catch (Exception e) {
                    }
                }
            if (!opened) {
                val intent2: Intent = new Intent("android.intent.action.VIEW", Uri.parse(url))
                intent2.setFlags(268435456)
                val context3: Context = context
                if (context3 != null) {
                    context3.startActivity(intent2)
                    }
                }
            }
        } catch (Exception e2) {
        Log.e(TAG, "Open URL failed", e2)
        }
    }

public static  Unit pushUrlToPc$default(ConnectionManager connectionManager, String str, boolean z, int i, Object obj) {
    if ((i & 2) != 0) {
        z = true
        }
    connectionManager.pushUrlToPc(str, z)
    }

fun pushUrlToPc(url: String, useEdge: Boolean): Unit {
    Intrinsics.checkNotNullParameter(url, "url")
    val msg: JsonObject = buildJsonMessage(new Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda2
    override
    fun invoke(obj: Any): Any {
        Unit pushUrlToPc$lambda$65
        pushUrlToPc$lambda$65 = ConnectionManager.pushUrlToPc$lambda$65(url, useEdge, (JsonObjectBuilder) obj)
        return pushUrlToPc$lambda$65
        }
    })
BuildersKt.launch$default(scope, null, null, new ConnectionManager$pushUrlToPc$1(msg, null), 3, null)
}

public static final Unit pushUrlToPc$lambda$65(final String $url, final boolean $useEdge, JsonObjectBuilder buildJsonMessage) {
    Intrinsics.checkNotNullParameter(buildJsonMessage, "$this$buildJsonMessage")
    JsonElementBuildersKt.put(buildJsonMessage, "source", "phone")
    JsonElementBuildersKt.putJsonObject(buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda22
        override
        fun invoke(obj: Any): Any {
            Unit pushUrlToPc$lambda$65$lambda$64
            pushUrlToPc$lambda$65$lambda$64 = ConnectionManager.pushUrlToPc$lambda$65$lambda$64($url, $useEdge, (JsonObjectBuilder) obj)
            return pushUrlToPc$lambda$65$lambda$64
            }
        })
    return Unit.INSTANCE
    }

public static final Unit pushUrlToPc$lambda$65$lambda$64(String $url, boolean $useEdge, JsonObjectBuilder putJsonObject) {
    Intrinsics.checkNotNullParameter(putJsonObject, "$this$putJsonObject")
    JsonElementBuildersKt.put(putJsonObject, "action", "open_url")
    JsonElementBuildersKt.put(putJsonObject, "url", $url)
    JsonElementBuildersKt.put(putJsonObject, "use_edge", Boolean.valueOf($useEdge))
    return Unit.INSTANCE
    }

fun sendUrlHistorySync(history: List<Triple<String, String, Long>>): Unit {
    Intrinsics.checkNotNullParameter(history, "history")
    val msg: JsonObject = buildJsonMessage(new Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda8
    override
    fun invoke(obj: Any): Any {
        Unit sendUrlHistorySync$lambda$69
        sendUrlHistorySync$lambda$69 = ConnectionManager.sendUrlHistorySync$lambda$69(history, (JsonObjectBuilder) obj)
        return sendUrlHistorySync$lambda$69
        }
    })
BuildersKt.launch$default(scope, null, null, new ConnectionManager$sendUrlHistorySync$1(msg, null), 3, null)
}

public static final Unit sendUrlHistorySync$lambda$69(final List $history, JsonObjectBuilder buildJsonMessage) {
    Intrinsics.checkNotNullParameter(buildJsonMessage, "$this$buildJsonMessage")
    JsonElementBuildersKt.put(buildJsonMessage, "source", "phone")
    JsonElementBuildersKt.putJsonObject(buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda36
        override
        fun invoke(obj: Any): Any {
            Unit sendUrlHistorySync$lambda$69$lambda$68
            sendUrlHistorySync$lambda$69$lambda$68 = ConnectionManager.sendUrlHistorySync$lambda$69$lambda$68($history, (JsonObjectBuilder) obj)
            return sendUrlHistorySync$lambda$69$lambda$68
            }
        })
    return Unit.INSTANCE
    }

public static final Unit sendUrlHistorySync$lambda$69$lambda$68(final List $history, JsonObjectBuilder putJsonObject) {
    Intrinsics.checkNotNullParameter(putJsonObject, "$this$putJsonObject")
    JsonElementBuildersKt.put(putJsonObject, "action", "url_history_sync")
    JsonElementBuildersKt.putJsonArray(putJsonObject, "history", Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda25
        override
        fun invoke(obj: Any): Any {
            Unit sendUrlHistorySync$lambda$69$lambda$68$lambda$67
            sendUrlHistorySync$lambda$69$lambda$68$lambda$67 = ConnectionManager.sendUrlHistorySync$lambda$69$lambda$68$lambda$67($history, (JsonArrayBuilder) obj)
            return sendUrlHistorySync$lambda$69$lambda$68$lambda$67
            }
        })
    return Unit.INSTANCE
    }

public static final Unit sendUrlHistorySync$lambda$69$lambda$68$lambda$67(List $history, JsonArrayBuilder putJsonArray) {
    Intrinsics.checkNotNullParameter(putJsonArray, "$this$putJsonArray")
    val it: Iterator = $history.iterator()
    while (it.hasNext()) {
        val item: Triple = (Triple) it.next()
        JsonElementBuildersKt.addJsonObject(putJsonArray, Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda20
            override
            fun invoke(obj: Any): Any {
                Unit sendUrlHistorySync$lambda$69$lambda$68$lambda$67$lambda$66
                sendUrlHistorySync$lambda$69$lambda$68$lambda$67$lambda$66 = ConnectionManager.sendUrlHistorySync$lambda$69$lambda$68$lambda$67$lambda$66(Triple.this, (JsonObjectBuilder) obj)
                return sendUrlHistorySync$lambda$69$lambda$68$lambda$67$lambda$66
                }
            })
        }
    return Unit.INSTANCE
    }

public static final Unit sendUrlHistorySync$lambda$69$lambda$68$lambda$67$lambda$66(Triple $item, JsonObjectBuilder addJsonObject) {
    Intrinsics.checkNotNullParameter(addJsonObject, "$this$addJsonObject")
    JsonElementBuildersKt.put(addJsonObject, "url", $item.getFirst())
    JsonElementBuildersKt.put(addJsonObject, "direction", $item.getSecond())
    JsonElementBuildersKt.put(addJsonObject, "timestamp", (Number) $item.getThird())
    return Unit.INSTANCE
    }

public static  Unit sendPowerCommand$default(ConnectionManager connectionManager, String str, long j, int i, Object obj) {
    if ((i & 2) != 0) {
        j = 0
        }
    connectionManager.sendPowerCommand(str, j)
    }

fun sendPowerCommand(cmd: String, delay: Long): Unit {
    Intrinsics.checkNotNullParameter(cmd, "cmd")
    val msg: JsonObject = buildJsonMessage(new Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda27
    override
    fun invoke(obj: Any): Any {
        Unit sendPowerCommand$lambda$71
        sendPowerCommand$lambda$71 = ConnectionManager.sendPowerCommand$lambda$71(cmd, delay, (JsonObjectBuilder) obj)
        return sendPowerCommand$lambda$71
        }
    })
BuildersKt.launch$default(scope, null, null, new ConnectionManager$sendPowerCommand$1(msg, null), 3, null)
}

public static final Unit sendPowerCommand$lambda$71(final String $cmd, final long $delay, JsonObjectBuilder buildJsonMessage) {
    Intrinsics.checkNotNullParameter(buildJsonMessage, "$this$buildJsonMessage")
    JsonElementBuildersKt.put(buildJsonMessage, "source", "phone")
    JsonElementBuildersKt.putJsonObject(buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda0
        override
        fun invoke(obj: Any): Any {
            Unit sendPowerCommand$lambda$71$lambda$70
            sendPowerCommand$lambda$71$lambda$70 = ConnectionManager.sendPowerCommand$lambda$71$lambda$70($cmd, $delay, (JsonObjectBuilder) obj)
            return sendPowerCommand$lambda$71$lambda$70
            }
        })
    return Unit.INSTANCE
    }

public static final Unit sendPowerCommand$lambda$71$lambda$70(String $cmd, long $delay, JsonObjectBuilder putJsonObject) {
    Intrinsics.checkNotNullParameter(putJsonObject, "$this$putJsonObject")
    JsonElementBuildersKt.put(putJsonObject, "action", "power")
    JsonElementBuildersKt.put(putJsonObject, "cmd", $cmd)
    JsonElementBuildersKt.put(putJsonObject, "delay", Long.valueOf($delay))
    return Unit.INSTANCE
    }

fun reportLocation(loc: Location): Unit {
    Intrinsics.checkNotNullParameter(loc, "loc")
    BuildersKt.launch$default(scope, null, null, new ConnectionManager$reportLocation$1(loc, null), 3, null)
    }

fun uploadLocationBatch(arr: JSONArray): Unit {
    Intrinsics.checkNotNullParameter(arr, "arr")
    BuildersKt.launch$default(scope, null, null, new ConnectionManager$uploadLocationBatch$1(arr, null), 3, null)
    }

fun addLocationPoint(p: LocationPoint): Unit {
    Intrinsics.checkNotNullParameter(p, "p")
    val list: List = CollectionsKt.toMutableList((Collection) _locationPoints.getValue())
    list.add(p)
    if (list.size() > 5000) {
        list.remove(0)
        }
    _locationPoints.setValue(list)
    }

fun performScreenTouch(normX: Float, normY: Float, op: String): Unit {
    var defaultDisplay: Display? = null
    val ctx: Context = context
    if (ctx == null) {
        return
        }
    val metrics: DisplayMetrics = new DisplayMetrics()
    val systemService: Any = ctx.getSystemService("window")
    val wm: WindowManager = systemService instanceof WindowManager ? (WindowManager) systemService : null
    if (wm != null && (defaultDisplay = wm.getDefaultDisplay()) != null) {
        defaultDisplay.getRealMetrics(metrics)
        }
    val px: Float = metrics.widthPixels * normX
    val py: Float = normY * metrics.heightPixels
    val acc: PhoneHubAccessibilityService = PhoneHubAccessibilityService.INSTANCE.getInstance()
    if (acc == null) {
        Log.w(TAG, "performScreenTouch: 无障碍服务未连接，无法执行操控。请在设置→无障碍中开启 PhoneHub 服务")
        showToast("无障碍服务未开启，无法操控。请在设置→无障碍中开启 PhoneHub")
        return
        }
    switch (op.hashCode()) {
        case 3739:
        if (op.equals("up")) {
            val lastX: Float = _lastTouchDownX
            val lastY: Float = _lastTouchDownY
            if (lastX >= 0.0f && lastY >= 0.0f) {
                val dx: Float = Math.abs(px - lastX)
                val dy: Float = Math.abs(py - lastY)
                if (dx >= 10.0f || dy >= 10.0f) {
                    acc.performSwipe(lastX, lastY, px, py, 100L)
                    } else {
                    acc.performTap(px, py)
                    }
                }
            _lastTouchDownX = -1.0f
            _lastTouchDownY = -1.0f
            return
            }
        break
        case 3089570:
        if (op.equals("down")) {
            _lastTouchDownX = px
            _lastTouchDownY = py
            return
            }
        break
        case 3357649:
        if (op.equals("move")) {
            val lastX2: Float = _lastTouchDownX
            val lastY2: Float = _lastTouchDownY
            if (lastX2 >= 0.0f && lastY2 >= 0.0f) {
                acc.performSwipe(lastX2, lastY2, px, py, 50L)
                }
            _lastTouchDownX = px
            _lastTouchDownY = py
            return
            }
        break
        case 94750088:
        if (op.equals("click")) {
            acc.performTap(px, py)
            return
            }
        break
        case 108511772:
        if (op.equals("right")) {
            acc.performBack()
            return
            }
        break
        }
    acc.performTap(px, py)
    }

fun reportNotification(item: NotificationItem): Unit {
    Intrinsics.checkNotNullParameter(item, "item")
    BuildersKt.launch$default(scope, null, null, new ConnectionManager$reportNotification$1(item, null), 3, null)
    }

fun loadClipboardStore(): Unit {
    val context2: Context = context
    if (context2 == null) {
        return
        }
    val ctx: Context = context2
    val prefs: SharedPreferences = ctx.getSharedPreferences(PREF_NAME, 0)
    val histStr: String = prefs.getString("clipboard_history", null)
    val favStr: String = prefs.getString("clipboard_favorites", null)
    if (histStr != null) {
        try {
            val arr: Iterable = JsonElementKt.getJsonArray(Json.INSTANCE.parseToJsonElement(histStr))
            val mutableStateFlow: MutableStateFlow<List<ClipboardItem>> = _clipboardHistory
            Iterable $this$map$iv = arr
            Collection destination$iv$iv = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv, 10))
            for (Object item$iv$iv : $this$map$iv) {
                val it: JsonElement = (JsonElement) item$iv$iv
                val ctx2: Context = ctx
                try {
                    destination$iv$iv.add(INSTANCE.toClipboardItem(JsonElementKt.getJsonObject(it)))
                    ctx = ctx2
                    } catch (Exception e) {
                    e = e
                    Log.e(TAG, "Load clipboard store failed", e)
                    return
                    }
                }
            mutableStateFlow.setValue((List) destination$iv$iv)
            } catch (Exception e2) {
            e = e2
            Log.e(TAG, "Load clipboard store failed", e)
            return
            }
        }
    if (favStr != null) {
        val arr2: Iterable = JsonElementKt.getJsonArray(Json.INSTANCE.parseToJsonElement(favStr))
        val mutableStateFlow2: MutableStateFlow<List<ClipboardItem>> = _clipboardFavorites
        Iterable $this$map$iv2 = arr2
        Collection destination$iv$iv2 = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv2, 10))
        for (Object item$iv$iv2 : $this$map$iv2) {
            val it2: JsonElement = (JsonElement) item$iv$iv2
            destination$iv$iv2.add(INSTANCE.toClipboardItem(JsonElementKt.getJsonObject(it2)))
            }
        mutableStateFlow2.setValue((List) destination$iv$iv2)
        }
    }

fun saveClipboardStore(): Unit {
    val ctx: Context = context
    if (ctx == null) {
        return
        }
    val prefs: SharedPreferences = ctx.getSharedPreferences(PREF_NAME, 0)
    SharedPreferences.Editor $this$saveClipboardStore_u24lambda_u2478 = prefs.edit()
    Json.Companion companion = Json.INSTANCE
    val serializer: KSerializer<JsonArray> = JsonArray.INSTANCE.serializer()
    JsonArrayBuilder builder$iv = JsonArrayBuilder()
    Iterable $this$forEach$iv = _clipboardHistory.getValue()
    for (Object element$iv : $this$forEach$iv) {
        val it: ClipboardItem = (ClipboardItem) element$iv
        builder$iv.add(INSTANCE.toJsonObject(it))
        ctx = ctx
        }
    val unit: Unit = Unit.INSTANCE
    $this$saveClipboardStore_u24lambda_u2478.putString("clipboard_history", companion.encodeToString(serializer, builder$iv.build()))
    Json.Companion companion2 = Json.INSTANCE
    val serializer2: KSerializer<JsonArray> = JsonArray.INSTANCE.serializer()
    JsonArrayBuilder builder$iv2 = JsonArrayBuilder()
    Iterable $this$forEach$iv2 = _clipboardFavorites.getValue()
    for (Object element$iv2 : $this$forEach$iv2) {
        val it2: ClipboardItem = (ClipboardItem) element$iv2
        builder$iv2.add(INSTANCE.toJsonObject(it2))
        }
    val unit2: Unit = Unit.INSTANCE
    $this$saveClipboardStore_u24lambda_u2478.putString("clipboard_favorites", companion2.encodeToString(serializer2, builder$iv2.build()))
    $this$saveClipboardStore_u24lambda_u2478.apply()
    }

fun addClipboardHistory(text: String, source: String): Unit {
    Intrinsics.checkNotNullParameter(text, "text")
    Intrinsics.checkNotNullParameter(source, "source")
    val item: ClipboardItem = new ClipboardItem(text, source, System.currentTimeMillis(), false)
    val list: List = CollectionsKt.toMutableList((Collection) _clipboardHistory.getValue())
    list.add(0, item)
    while (list.size() > CLIPBOARD_HISTORY_MAX) {
        list.remove(list.size() - 1)
        }
    _clipboardHistory.setValue(list)
    saveClipboardStore()
    }

fun toggleFavorite(item: ClipboardItem): Unit {
    Intrinsics.checkNotNullParameter(item, "item")
    val item2: ClipboardItem = ClipboardItem.copy$default(item, null, null, 0L, !item.getFavorite(), 7, null)
    val hist: List = CollectionsKt.toMutableList((Collection) _clipboardHistory.getValue())
    int index$iv = 0
    val it: Iterator<ClipboardItem> = hist.iterator()
    while (true) {
        if (it.hasNext()) {
            Object item$iv = it.next()
            val it2: ClipboardItem = (ClipboardItem) item$iv
            if (((Intrinsics.areEqual(it2.getContent(), item.getContent()) && it2.getTimestamp() == item.getTimestamp()) ? 1 : null) != null) {
                break
                } else {
                index$iv++
                }
            } else {
            index$iv = -1
            break
            }
        }
    val idx: Int = index$iv
    if (idx >= 0) {
        hist.set(idx, item2)
        _clipboardHistory.setValue(hist)
        }
    val fav: List = CollectionsKt.toMutableList((Collection) _clipboardFavorites.getValue())
    if (item2.getFavorite()) {
        fav.add(0, item2)
        while (fav.size() > 50) {
            fav.remove(fav.size() - 1)
            }
        } else {
        CollectionsKt.removeAll(fav, Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda18
            override
            fun invoke(obj: Any): Any {
                var z: Boolean? = null
                z = ConnectionManager.toggleFavorite$lambda$80(ConnectionManager.ClipboardItem.this, (ConnectionManager.ClipboardItem) obj)
                return Boolean.valueOf(z)
                }
            })
        }
    _clipboardFavorites.setValue(fav)
    saveClipboardStore()
    val msg: JsonObject = buildJsonMessage(new Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda19
    override
    fun invoke(obj: Any): Any {
        var unit: Unit? = null
        unit = ConnectionManager.toggleFavorite$lambda$82(ConnectionManager.ClipboardItem.this, (JsonObjectBuilder) obj)
        var unit: return? = null
        }
    })
BuildersKt.launch$default(scope, null, null, new ConnectionManager$toggleFavorite$2(msg, null), 3, null)
}

public static final boolean toggleFavorite$lambda$80(ClipboardItem $item, ClipboardItem it) {
    Intrinsics.checkNotNullParameter(it, "it")
    return Intrinsics.areEqual(it.getContent(), $item.getContent()) && it.getTimestamp() == $item.getTimestamp()
    }

public static final Unit toggleFavorite$lambda$82(final ClipboardItem $item2, JsonObjectBuilder buildJsonMessage) {
    Intrinsics.checkNotNullParameter(buildJsonMessage, "$this$buildJsonMessage")
    JsonElementBuildersKt.put(buildJsonMessage, "source", "phone")
    JsonElementBuildersKt.putJsonObject(buildJsonMessage, "data", Function1() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda32
        override
        fun invoke(obj: Any): Any {
            var unit: Unit? = null
            unit = ConnectionManager.toggleFavorite$lambda$82$lambda$81(ConnectionManager.ClipboardItem.this, (JsonObjectBuilder) obj)
            var unit: return? = null
            }
        })
    return Unit.INSTANCE
    }

public static final Unit toggleFavorite$lambda$82$lambda$81(ClipboardItem $item2, JsonObjectBuilder putJsonObject) {
    Intrinsics.checkNotNullParameter(putJsonObject, "$this$putJsonObject")
    JsonElementBuildersKt.put(putJsonObject, "action", "clipboard_favorite")
    JsonElementBuildersKt.put(putJsonObject, "content", $item2.getContent())
    JsonElementBuildersKt.put(putJsonObject, "favorite", Boolean.valueOf($item2.getFavorite()))
    return Unit.INSTANCE
    }

fun applySyncedFavorite(content: String, favorite: Boolean): Unit {
    Iterable $this$none$iv
    val fav: List = CollectionsKt.toMutableList((Collection) _clipboardFavorites.getValue())
    if (favorite) {
        List $this$none$iv2 = fav
        if (!($this$none$iv2 is Collection) || !$this$none$iv2.isEmpty()) {
            val it: Iterator = $this$none$iv2.iterator()
            while (true) {
                if (it.hasNext()) {
                    Object element$iv = it.next()
                    val it2: ClipboardItem = (ClipboardItem) element$iv
                    if (Intrinsics.areEqual(it2.getContent(), content)) {
                        $this$none$iv = null
                        break
                        }
                    } else {
                    $this$none$iv = 1
                    break
                    }
                }
            } else {
            $this$none$iv = 1
            }
        if ($this$none$iv != null) {
            fav.add(0, ClipboardItem(content, "pc", System.currentTimeMillis(), true))
            while (fav.size() > 50) {
                fav.remove(fav.size() - 1)
                }
            _clipboardFavorites.setValue(fav)
            saveClipboardStore()
            return
            }
        return
        }
    val mutableStateFlow: MutableStateFlow<List<ClipboardItem>> = _clipboardFavorites
    List $this$filterNot$iv = fav
    Collection destination$iv$iv = ArrayList()
    for (Object element$iv$iv : $this$filterNot$iv) {
        val it3: ClipboardItem = (ClipboardItem) element$iv$iv
        if (!Intrinsics.areEqual(it3.getContent(), content)) {
            destination$iv$iv.add(element$iv$iv)
            }
        }
    mutableStateFlow.setValue((List) destination$iv$iv)
    saveClipboardStore()
    }

fun searchClipboardHistory(query: String): List<ClipboardItem> {
    Intrinsics.checkNotNullParameter(query, "query")
    val q: String = query.toLowerCase(Locale.ROOT)
    Intrinsics.checkNotNullExpressionValue(q, "toLowerCase(...)")
    Iterable $this$filter$iv = _clipboardHistory.getValue()
    Collection destination$iv$iv = ArrayList()
    for (Object element$iv$iv : $this$filter$iv) {
        val it: ClipboardItem = (ClipboardItem) element$iv$iv
        val lowerCase: String = it.getContent().toLowerCase(Locale.ROOT)
        Intrinsics.checkNotNullExpressionValue(lowerCase, "toLowerCase(...)")
        if (StringsKt.contains$default((CharSequence) lowerCase, (CharSequence) q, false, 2, (Object) null)) {
            destination$iv$iv.add(element$iv$iv)
            }
        }
    return (List) destination$iv$iv
    }

fun searchClipboardFavorites(query: String): List<ClipboardItem> {
    Intrinsics.checkNotNullParameter(query, "query")
    val q: String = query.toLowerCase(Locale.ROOT)
    Intrinsics.checkNotNullExpressionValue(q, "toLowerCase(...)")
    Iterable $this$filter$iv = _clipboardFavorites.getValue()
    Collection destination$iv$iv = ArrayList()
    for (Object element$iv$iv : $this$filter$iv) {
        val it: ClipboardItem = (ClipboardItem) element$iv$iv
        val lowerCase: String = it.getContent().toLowerCase(Locale.ROOT)
        Intrinsics.checkNotNullExpressionValue(lowerCase, "toLowerCase(...)")
        if (StringsKt.contains$default((CharSequence) lowerCase, (CharSequence) q, false, 2, (Object) null)) {
            destination$iv$iv.add(element$iv$iv)
            }
        }
    return (List) destination$iv$iv
    }

fun sendClipboardHistoryToPc(): Unit {
    BuildersKt.launch$default(scope, null, null, new ConnectionManager$sendClipboardHistoryToPc$1(null), 3, null)
    }

fun toJsonObject(/* ClipboardItem $this$toJsonObject */): JsonObject {
    JsonObjectBuilder builder$iv = JsonObjectBuilder()
    JsonElementBuildersKt.put(builder$iv, "content", $this$toJsonObject.getContent())
    JsonElementBuildersKt.put(builder$iv, "source", $this$toJsonObject.getSource())
    JsonElementBuildersKt.put(builder$iv, "timestamp", Long.valueOf($this$toJsonObject.getTimestamp()))
    JsonElementBuildersKt.put(builder$iv, "favorite", Boolean.valueOf($this$toJsonObject.getFavorite()))
    return builder$iv.build()
    }

fun toClipboardItem(/* JsonObject $this$toClipboardItem */): ClipboardItem {
    var str: String? = null
    var str2: String? = null
    var jsonPrimitive: JsonPrimitive? = null
    var booleanOrNull: Boolean? = null
    var jsonPrimitive2: JsonPrimitive? = null
    var longOrNull: Long? = null
    var jsonPrimitive3: JsonPrimitive? = null
    var jsonPrimitive4: JsonPrimitive? = null
    val jsonElement: JsonElement = (JsonElement) $this$toClipboardItem.get("content")
    if (jsonElement == null || (jsonPrimitive4 = JsonElementKt.getJsonPrimitive(jsonElement)) == null || (str = JsonElementKt.getContentOrNull(jsonPrimitive4)) == null) {
        str = ""
        }
    val str3: String = str
    val jsonElement2: JsonElement = (JsonElement) $this$toClipboardItem.get("source")
    if (jsonElement2 == null || (jsonPrimitive3 = JsonElementKt.getJsonPrimitive(jsonElement2)) == null || (str2 = JsonElementKt.getContentOrNull(jsonPrimitive3)) == null) {
        str2 = EnvironmentCompat.MEDIA_UNKNOWN
        }
    val str4: String = str2
    val jsonElement3: JsonElement = (JsonElement) $this$toClipboardItem.get("timestamp")
    val longValue: Long = (jsonElement3 == null || (jsonPrimitive2 = JsonElementKt.getJsonPrimitive(jsonElement3)) == null || (longOrNull = JsonElementKt.getLongOrNull(jsonPrimitive2)) == null) ? 0L : longOrNull.longValue()
    val jsonElement4: JsonElement = (JsonElement) $this$toClipboardItem.get("favorite")
    return ClipboardItem(str3, str4, longValue, (jsonElement4 == null || (jsonPrimitive = JsonElementKt.getJsonPrimitive(jsonElement4)) == null || (booleanOrNull = JsonElementKt.getBooleanOrNull(jsonPrimitive)) == null) ? false : booleanOrNull.booleanValue())
    }

fun getReceiveDir(): File {
    var receiveDir: return? = null
    }

fun cacheIp(ip: String): Unit {
    val ctx: Context = context
    if (ctx == null) {
        return
        }
    ctx.getSharedPreferences(PREF_NAME, 0).edit().putString(KEY_CACHED_IP, ip).apply()
    }

fun getCachedIp(): String {
    val ctx: Context = context
    if (ctx == null) {
        var null: return? = null
        }
    return ctx.getSharedPreferences(PREF_NAME, 0).getString(KEY_CACHED_IP, null)
    }

    /* JADX WARN: Code restructure failed: missing block: B:54:0x01d6, code lost:

r0 = move-exception
     */
    /* JADX WARN: Code restructure failed: missing block: B:55:0x01d7, code lost:

android.util.Log.e(com.phonehub.ConnectionManager.TAG, "sendRaw failed", r0)
     */
    /*
Code decompiled incorrectly, please refer to instructions dump.
    */
fun sendRaw(payload: String, continuation: Continuation<? super Unit>): Any {
    ConnectionManager$sendRaw$1 connectionManager$sendRaw$1
    ConnectionManager$sendRaw$1 connectionManager$sendRaw$12
    var execute: Any? = null
    var execute2: Any? = null
    if (continuation is ConnectionManager$sendRaw$1) {
        connectionManager$sendRaw$1 = (ConnectionManager$sendRaw$1) continuation
        if ((connectionManager$sendRaw$1.label & Integer.MIN_VALUE) != 0) {
            connectionManager$sendRaw$1.label -= Integer.MIN_VALUE
            connectionManager$sendRaw$12 = connectionManager$sendRaw$1
            Object $result = connectionManager$sendRaw$12.result
            val coroutine_suspended: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
            switch (connectionManager$sendRaw$12.label) {
                case 0:
                ResultKt.throwOnFailure($result)
                switch (WhenMappings.$EnumSwitchMapping$0[_currentChannel.getValue().ordinal()]) {
                    case 1:
                    HttpClient $this$request$iv$iv$iv$iv = client
                    if ($this$request$iv$iv$iv$iv != null) {
                        String urlString$iv = "http://127.0.0.1:" + connectPort + "/api/cmd"
                        HttpRequestBuilder $this$post_u24lambda_u245$iv = HttpRequestBuilder()
                        HttpRequestKt.url($this$post_u24lambda_u245$iv, urlString$iv)
                        HttpMessagePropertiesKt.contentType($this$post_u24lambda_u245$iv, ContentType.Application.INSTANCE.getJson())
                        if (payload == null) {
                            $this$post_u24lambda_u245$iv.setBody(NullBody.INSTANCE)
                            KType kType$iv$iv = Reflection.typeOf(String.class)
                            Type reifiedType$iv$iv = TypesJVMKt.getJavaType(kType$iv$iv)
                            $this$post_u24lambda_u245$iv.setBodyType(TypeInfoJvmKt.typeInfoImpl(reifiedType$iv$iv, Reflection.getOrCreateKotlinClass(String.class), kType$iv$iv))
                            } else if (payload is OutgoingContent) {
                            $this$post_u24lambda_u245$iv.setBody(payload)
                            $this$post_u24lambda_u245$iv.setBodyType(null)
                            } else {
                            $this$post_u24lambda_u245$iv.setBody(payload)
                            KType kType$iv$iv2 = Reflection.typeOf(String.class)
                            Type reifiedType$iv$iv2 = TypesJVMKt.getJavaType(kType$iv$iv2)
                            $this$post_u24lambda_u245$iv.setBodyType(TypeInfoJvmKt.typeInfoImpl(reifiedType$iv$iv2, Reflection.getOrCreateKotlinClass(String.class), kType$iv$iv2))
                            }
                        $this$post_u24lambda_u245$iv.setMethod(HttpMethod.INSTANCE.getPost())
                        val httpStatement: HttpStatement = new HttpStatement($this$post_u24lambda_u245$iv, $this$request$iv$iv$iv$iv)
                        connectionManager$sendRaw$12.label = 1
                        execute = httpStatement.execute(connectionManager$sendRaw$12)
                        if (execute == coroutine_suspended) {
                            var coroutine_suspended: return? = null
                            }
                        }
                    return Unit.INSTANCE
                    case 2:
                    val ip: String = pcIp
                    if (ip == null) {
                        ip = DEFAULT_IP
                        }
                    HttpClient $this$request$iv$iv$iv$iv2 = client
                    if ($this$request$iv$iv$iv$iv2 == null) {
                        return Unit.INSTANCE
                        }
                    String urlString$iv2 = "http://" + ip + ":" + connectPort + "/api/cmd"
                    HttpRequestBuilder $this$post_u24lambda_u245$iv2 = HttpRequestBuilder()
                    HttpRequestKt.url($this$post_u24lambda_u245$iv2, urlString$iv2)
                    HttpMessagePropertiesKt.contentType($this$post_u24lambda_u245$iv2, ContentType.Application.INSTANCE.getJson())
                    if (payload == null) {
                        $this$post_u24lambda_u245$iv2.setBody(NullBody.INSTANCE)
                        KType kType$iv$iv3 = Reflection.typeOf(String.class)
                        Type reifiedType$iv$iv3 = TypesJVMKt.getJavaType(kType$iv$iv3)
                        $this$post_u24lambda_u245$iv2.setBodyType(TypeInfoJvmKt.typeInfoImpl(reifiedType$iv$iv3, Reflection.getOrCreateKotlinClass(String.class), kType$iv$iv3))
                        } else if (payload is OutgoingContent) {
                        $this$post_u24lambda_u245$iv2.setBody(payload)
                        $this$post_u24lambda_u245$iv2.setBodyType(null)
                        } else {
                        $this$post_u24lambda_u245$iv2.setBody(payload)
                        KType kType$iv$iv4 = Reflection.typeOf(String.class)
                        Type reifiedType$iv$iv4 = TypesJVMKt.getJavaType(kType$iv$iv4)
                        $this$post_u24lambda_u245$iv2.setBodyType(TypeInfoJvmKt.typeInfoImpl(reifiedType$iv$iv4, Reflection.getOrCreateKotlinClass(String.class), kType$iv$iv4))
                        }
                    $this$post_u24lambda_u245$iv2.setMethod(HttpMethod.INSTANCE.getPost())
                    val httpStatement2: HttpStatement = new HttpStatement($this$post_u24lambda_u245$iv2, $this$request$iv$iv$iv$iv2)
                    connectionManager$sendRaw$12.label = 2
                    execute2 = httpStatement2.execute(connectionManager$sendRaw$12)
                    if (execute2 == coroutine_suspended) {
                        var coroutine_suspended: return? = null
                        }
                    return Unit.INSTANCE
                    default:
                    return Unit.INSTANCE
                    }
                case 1:
                ResultKt.throwOnFailure($result)
                execute = $result
                return Unit.INSTANCE
                case 2:
                ResultKt.throwOnFailure($result)
                execute2 = $result
                return Unit.INSTANCE
                default:
                throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
                }
            }
        }
    connectionManager$sendRaw$1 = new ConnectionManager$sendRaw$1(this, continuation)
    connectionManager$sendRaw$12 = connectionManager$sendRaw$1
    Object $result2 = connectionManager$sendRaw$12.result
    val coroutine_suspended2: Any = IntrinsicsKt.getCOROUTINE_SUSPENDED()
    switch (connectionManager$sendRaw$12.label) {
        }
    }

fun buildJsonMessage(block: Function1<? super JsonObjectBuilder, Unit>): JsonObject {
    JsonObjectBuilder builder$iv = JsonObjectBuilder()
    JsonElementBuildersKt.put(builder$iv, "token", secretToken)
    JsonElementBuildersKt.put(builder$iv, "activate", "send")
    block.invoke(builder$iv)
    return builder$iv.build()
    }

fun getBaseUrl(): String {
    val ip: String = pcIp
    if (ip == null) {
        ip = DEFAULT_IP
        }
    if (_currentChannel.getValue() == ChannelType.ADB) {
        return "http://127.0.0.1:" + connectPort
        }
    return "http://" + ip + ":" + connectPort
    }

fun getBaseUrlPublic(): String {
    return getBaseUrl()
    }

public static  Unit sendFrameToPc$default(ConnectionManager connectionManager, byte[] bArr, String str, int i, Object obj) {
    if ((i & 2) != 0) {
        str = "mirror"
        }
    connectionManager.sendFrameToPc(bArr, str)
    }

fun sendFrameToPc(frameData: Array<Byte>, type: String): Unit {
    Intrinsics.checkNotNullParameter(frameData, "frameData")
    Intrinsics.checkNotNullParameter(type, "type")
    try {
        val url: URL = new URL(getBaseUrl() + "/api/phone_frame?type=" + type)
        val openConnection: URLConnection = url.openConnection()
        Intrinsics.checkNotNull(openConnection, "null cannot be cast to non-null type java.net.HttpURLConnection")
        val conn: HttpURLConnection = (HttpURLConnection) openConnection
        conn.setRequestMethod("POST")
        conn.setRequestProperty("Authorization", "Bearer " + secretToken)
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setDoOutput(true)
        conn.setConnectTimeout(1000)
        conn.setReadTimeout(1000)
        conn.setUseCaches(false)
        conn.setFixedLengthStreamingMode(frameData.length)
        conn.getOutputStream().write(frameData)
        conn.getOutputStream().flush()
        conn.getOutputStream().close()
        conn.getResponseCode()
        conn.disconnect()
        } catch (Exception e) {
        }
    }

fun sendAudioToPc(audioData: Array<Byte>): Unit {
    Intrinsics.checkNotNullParameter(audioData, "audioData")
    try {
        val url: URL = new URL(getBaseUrl() + "/api/phone_audio")
        val openConnection: URLConnection = url.openConnection()
        Intrinsics.checkNotNull(openConnection, "null cannot be cast to non-null type java.net.HttpURLConnection")
        val conn: HttpURLConnection = (HttpURLConnection) openConnection
        conn.setRequestMethod("POST")
        conn.setRequestProperty("Authorization", "Bearer " + secretToken)
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setDoOutput(true)
        conn.setConnectTimeout(1000)
        conn.setReadTimeout(1000)
        conn.setUseCaches(false)
        conn.setFixedLengthStreamingMode(audioData.length)
        conn.getOutputStream().write(audioData)
        conn.getOutputStream().flush()
        conn.getOutputStream().close()
        conn.getResponseCode()
        conn.disconnect()
        } catch (Exception e) {
        }
    }

fun fetchPcDrives(callback: Function1<? super List<PcDriveInfo>, Uni>>): Unit {
    Intrinsics.checkNotNullParameter(callback, "callback")
    BuildersKt.launch$default(scope, null, null, new ConnectionManager$fetchPcDrives$1(callback, null), 3, null)
    }

fun fetchPcFiles(path: String, callback: Function2<? super List<PcFileInfo>, ? super String, Uni>>): Unit {
    Intrinsics.checkNotNullParameter(path, "path")
    Intrinsics.checkNotNullParameter(callback, "callback")
    BuildersKt.launch$default(scope, null, null, new ConnectionManager$fetchPcFiles$1(path, callback, null), 3, null)
    }

public static final  class PcDriveInfo {
    var free: private final long? = null
    var label: private final String? = null
    var name: private final String? = null
    var total: private final long? = null
    var used: private final long? = null

    fun getName(): String {
        return this.name
        }

    fun getLabel(): String {
        return this.label
        }

    fun getTotal(): Long {
        return this.total
        }

    fun getUsed(): Long {
        return this.used
        }

    fun getFree(): Long {
        return this.free
        }

    fun copy(name: String, label: String, total: Long, used: Long, free: Long): PcDriveInfo {
        Intrinsics.checkNotNullParameter(name, "name")
        Intrinsics.checkNotNullParameter(label, "label")
        return PcDriveInfo(name, label, total, used, free)
        }

    fun equals(other: Any): Boolean {
        if (this == other) {
            var true: return? = null
            }
        if (!(other is PcDriveInfo)) {
            var false: return? = null
            }
        val pcDriveInfo: PcDriveInfo = (PcDriveInfo) other
        return Intrinsics.areEqual(this.name, pcDriveInfo.name) && Intrinsics.areEqual(this.label, pcDriveInfo.label) && this.total == pcDriveInfo.total && this.used == pcDriveInfo.used && this.free == pcDriveInfo.free
        }

    fun hashCode(): Int {
        return (((((((this.name.hashCode() * 31) + this.label.hashCode()) * 31) + Long.hashCode(this.total)) * 31) + Long.hashCode(this.used)) * 31) + Long.hashCode(this.free)
        }

    fun toString(): String {
        return "PcDriveInfo(name=" + this.name + ", label=" + this.label + ", total=" + this.total + ", used=" + this.used + ", free=" + this.free + ")"
        }

    fun PcDriveInfo(name: String, label: String, total: Long, used: Long, free: Long): public {
        Intrinsics.checkNotNullParameter(name, "name")
        Intrinsics.checkNotNullParameter(label, "label")
        this.name = name
        this.label = label
        this.total = total
        this.used = used
        this.free = free
        }

    fun getName(): String {
        return this.name
        }

    fun getLabel(): String {
        return this.label
        }

    fun getTotal(): Long {
        return this.total
        }

    fun getUsed(): Long {
        return this.used
        }

    fun getFree(): Long {
        return this.free
        }
    }

public static final  class PcFileInfo {
    var isDir: private final boolean? = null
    var modified: private final long? = null
    var name: private final String? = null
    var size: private final long? = null

    public static  PcFileInfo copy$default(PcFileInfo pcFileInfo, String str, boolean z, long j, long j2, int i, Object obj) {
        if ((i & 1) != 0) {
            str = pcFileInfo.name
            }
        if ((i & 2) != 0) {
            z = pcFileInfo.isDir
            }
        val z2: Boolean = z
        if ((i & 4) != 0) {
            j = pcFileInfo.size
            }
        val j3: Long = j
        if ((i & 8) != 0) {
            j2 = pcFileInfo.modified
            }
        return pcFileInfo.copy(str, z2, j3, j2)
        }

    fun getName(): String {
        return this.name
        }

    fun getIsDir(): Boolean {
        return this.isDir
        }

    fun getSize(): Long {
        return this.size
        }

    fun getModified(): Long {
        return this.modified
        }

    fun copy(name: String, isDir: Boolean, size: Long, modified: Long): PcFileInfo {
        Intrinsics.checkNotNullParameter(name, "name")
        return PcFileInfo(name, isDir, size, modified)
        }

    fun equals(other: Any): Boolean {
        if (this == other) {
            var true: return? = null
            }
        if (!(other is PcFileInfo)) {
            var false: return? = null
            }
        val pcFileInfo: PcFileInfo = (PcFileInfo) other
        return Intrinsics.areEqual(this.name, pcFileInfo.name) && this.isDir == pcFileInfo.isDir && this.size == pcFileInfo.size && this.modified == pcFileInfo.modified
        }

    fun hashCode(): Int {
        return (((((this.name.hashCode() * 31) + Boolean.hashCode(this.isDir)) * 31) + Long.hashCode(this.size)) * 31) + Long.hashCode(this.modified)
        }

    fun toString(): String {
        return "PcFileInfo(name=" + this.name + ", isDir=" + this.isDir + ", size=" + this.size + ", modified=" + this.modified + ")"
        }

    fun PcFileInfo(name: String, isDir: Boolean, size: Long, modified: Long): public {
        Intrinsics.checkNotNullParameter(name, "name")
        this.name = name
        this.isDir = isDir
        this.size = size
        this.modified = modified
        }

    fun getName(): String {
        return this.name
        }

    fun isDir(): Boolean {
        return this.isDir
        }

    fun getSize(): Long {
        return this.size
        }

    fun getModified(): Long {
        return this.modified
        }
    }

fun disconnect(): Unit {
    userConnectedIntent = false
    lastPcHeartbeatAt = 0L
    val job: Job = statusJob
    if (job != null) {
        Job.DefaultImpls.cancel$default(job, (CancellationException) null, 1, (Object) null)
        }
    val job2: Job = msgPollingJob
    if (job2 != null) {
        Job.DefaultImpls.cancel$default(job2, (CancellationException) null, 1, (Object) null)
        }
    val job3: Job = statusReportJob
    if (job3 != null) {
        Job.DefaultImpls.cancel$default(job3, (CancellationException) null, 1, (Object) null)
        }
    val job4: Job = sendJob
    if (job4 != null) {
        Job.DefaultImpls.cancel$default(job4, (CancellationException) null, 1, (Object) null)
        }
    val job5: Job = receiveJob
    if (job5 != null) {
        Job.DefaultImpls.cancel$default(job5, (CancellationException) null, 1, (Object) null)
        }
    transferInProgress = false
    ackTracker.clear()
    fileReceiveState.clear()
    _connectionState.setValue(ConnectionState.DISCONNECTED)
    _currentChannel.setValue(ChannelType.NONE)
    _connectionMessage.setValue("未连接")
    _fileTransferProgress.setValue(null)
    }

fun runOnUiThread(block: Function0<Unit>): Unit {
    Intrinsics.checkNotNullParameter(block, "block")
    mainHandler.post(Runnable() { // from class: com.phonehub.ConnectionManager$$ExternalSyntheticLambda26
        override
        fun run(): Unit {
            Function0.this.invoke()
            }
        })
    }
}
