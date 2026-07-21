package com.phonehub

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.hardware.display.VirtualDisplay
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.Window
import android.webkit.MimeTypeMap
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.internal.view.SupportMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.LifecycleOwnerKt
import coil.disk.DiskLruCache
import com.google.common.util.concurrent.ListenableFuture
import io.ktor.http.ContentDisposition
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileFilter
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.Pair
import kotlin.Triple
import kotlin.Unit
import kotlin.collections.ArrayList
import kotlin.coroutines.Continuation
import kotlin.io.ByteStreamsKt
import kotlin.io.CloseableKt
import kotlin.io.FilesKt
import kotlin.ranges.RangesKt
import kotlin.text.Charsets
import kotlinx.coroutines.BuildersKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineScopeKt
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.internal.AbstractJsonLexerKt
import okhttp3.internal.ws.RealWebSocket
import org.slf4j.Marker

class MainActivity : AppCompatActivity() {

    var setupScreen: LinearLayout? = null
    var mainContainer: LinearLayout? = null
    var ipInput: EditText? = null
    var portInput: EditText? = null
    var tokenInput: EditText? = null
    var connectBtn: Button? = null
    var connectStatus: TextView? = null
    var statusText: TextView? = null
    var titleText: TextView? = null
    var pageContainer: FrameLayout? = null
    var currentTab: Int = 0
    val pageCache: HashMap<Int, View> = HashMap()
    var mirrorImageView: ImageView? = null
    var cameraImageView: ImageView? = null
    val frameTimeoutHandler: Handler = Handler(Looper.getMainLooper())
    var mirrorFrameTimeoutRunnable: Runnable? = null
    var cameraFrameTimeoutRunnable: Runnable? = null
    var mediaProjection: MediaProjection? = null
    var screenCaptureRunning: Boolean = false
    var screenCaptureThread: Thread? = null
    var virtualDisplay: VirtualDisplay? = null
    var screenWidth: Int = 0
    var screenHeight: Int = 0
    var screenDensity: Int = 0
    var imageReader: ImageReader? = null
    var isMirrorFullscreen: Boolean = false
    var mirrorFullscreenComponents: List<View>? = null
    var mirrorOriginalLp: ViewGroup.LayoutParams? = null
    var mirrorOriginalPageContainerLp: LinearLayout.LayoutParams? = null
    var cameraProvider: ProcessCameraProvider? = null
    var cameraInstance: Camera? = null
    var cameraLensFacing: Int = 1
    @Volatile var cameraPreviewRunning: Boolean = false
    var cameraExecutor: ExecutorService? = null
    var cameraPreviewView: PreviewView? = null
    var audioRecord: AudioRecord? = null
    var audioCaptureRunning: Boolean = false
    var audioCaptureThread: Thread? = null
    val SELECT_FILE_CODE: Int = 1001
    val SELECT_APK_CODE: Int = 1002
    val urlHistory: MutableList<UrlHistoryItem> = ArrayList()
    val handledTextContents: MutableMap<String, Long> = LinkedHashMap()
    var activeNotifItems: MutableList<ConnectionManager.NotificationItem> = ArrayList()
    var notifHistoryItems: MutableList<ConnectionManager.NotificationItem> = ArrayList()
    var clipViewMode: String = "history"
    val allFuncList: List<FuncInfo> = listOf(
        FuncInfo("文件传输", "📁", 1),
        FuncInfo("剪贴板", "📋", 4),
        FuncInfo("文字互传", "💬", -1),
        FuncInfo("远程控制", "🎮", 2),
        FuncInfo("投屏", "🖥️", 8),
        FuncInfo("摄像头", "📷", 9),
        FuncInfo("通知", "🔔", 10),
        FuncInfo("路线图", "🗺️", 6),
        FuncInfo("文件管理", "📂", 11),
        FuncInfo("电源管理", "⚡", 14),
        FuncInfo("推送网页", "🌐", 15),
        FuncInfo("设置", "⚙️", 16)
    )
    var fileHistory: MutableList<FileHistoryItem> = ArrayList()
    var fmMode: String = "phone"
    var pcCurPath: String = "C:\\"
    var pcInDrives: Boolean = true
    var pendingSaveText: String = ""

    lateinit var notifPermissionLauncher: ActivityResultLauncher<String>
    lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    lateinit var allPermissionsLauncher: ActivityResultLauncher<Array<String>>
    lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>
    lateinit var saveTextLauncher: ActivityResultLauncher<String>

    data class UrlHistoryItem(val url: String, val direction: String, val timestamp: Long)

    data class FuncInfo(val name: String, val icon: String, val tabIndex: Int)

    data class FileHistoryItem(val time: Long, val text: String, val direction: String)

    init {
        notifPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { _: Boolean -> }

        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted: Boolean ->
            if (granted) {
                Toast.makeText(this, "摄像头权限已授予", Toast.LENGTH_SHORT).show()
            }
        }

        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _: Map<String, Boolean> -> }

        allPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _: Map<String, Boolean> -> }

        screenCaptureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            try {
                if (result.resultCode == -1 && result.data != null) {
                    val mpManager = getSystemService("media_projection") as MediaProjectionManager
                    val data = result.data!!
                    ConnectionManager.INSTANCE.cacheMediaProjectionToken(result.resultCode, data)
                    mediaProjection = mpManager.getMediaProjection(result.resultCode, data)
                    mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() {
                            this@MainActivity.stopPhoneScreenCapture()
                        }
                    }, Handler(Looper.getMainLooper()))
                    startScreenCaptureLoop()
                    return@registerForActivityResult
                }
                Toast.makeText(this, "屏幕录制权限被拒绝", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "初始化投屏失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        saveTextLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("text/plain")
        ) { uri: Uri? ->
            if (uri != null) {
                val textToSave = pendingSaveText
                BuildersKt.launch$default(
                    CoroutineScopeKt.CoroutineScope(Dispatchers.IO),
                    null, null,
                    MainActivity_saveTextLauncher_1_1(this, uri, textToSave, null),
                    3, null
                )
            }
        }
    }

    fun loadUrlHistory() {
        urlHistory.clear()
        val prefs = getSharedPreferences("phonehub_prefs", 0)
        val raw = prefs.getString("push_url_history", "") ?: ""
        if (raw.isEmpty()) return
        for (line in raw.split("\n")) {
            if (line.isNotBlank()) {
                val parts = line.split("\t")
                if (parts.size == 3) {
                    val ts = parts[0].toLongOrNull()
                    if (ts != null) {
                        urlHistory.add(UrlHistoryItem(parts[2], parts[1], ts))
                    }
                }
            }
        }
    }

    fun saveUrlHistory() {
        val prefs = getSharedPreferences("phonehub_prefs", 0)
        val raw = urlHistory.joinToString("\n") { "${it.timestamp}\t${it.direction}\t${it.url}" }
        prefs.edit().putString("push_url_history", raw).apply()
    }

    fun addUrlHistory(url: String, direction: String) {
        urlHistory.removeAll { it.url == url && it.direction == direction }
        urlHistory.add(0, UrlHistoryItem(url, direction, System.currentTimeMillis()))
        if (urlHistory.size > 50) {
            urlHistory.subList(50, urlHistory.size).clear()
        }
        saveUrlHistory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(128)
        setContentView(R.layout.activity_main)
        setupScreen = findViewById(R.id.setupScreen)
        mainContainer = findViewById(R.id.mainContainer)
        ipInput = findViewById(R.id.ipInput)
        portInput = findViewById(R.id.portInput)
        tokenInput = findViewById(R.id.tokenInput)
        connectBtn = findViewById(R.id.connectBtn)
        connectStatus = findViewById(R.id.connectStatus)
        statusText = findViewById(R.id.statusText)
        titleText = findViewById(R.id.titleText)
        pageContainer = findViewById(R.id.pageContainer)

        val prefs = getSharedPreferences("phonehub_prefs", 0)
        ConnectionManager.INSTANCE.getCachedIp()?.let { ipInput?.setText(it) }
        if (ipInput?.text.isNullOrBlank()) {
            ipInput?.setText("192.168.3.9")
        }
        val cachedPort = prefs.getInt("cached_port", 0)
        portInput?.setText(if (cachedPort > 0) cachedPort.toString() else "58627")
        val cachedToken = prefs.getString("cached_token", "")
        if (!cachedToken.isNullOrEmpty()) {
            tokenInput?.setText(cachedToken)
        }

        val toggleBtn = findViewById<TextView>(R.id.toggleTokenVisibility)
        toggleBtn.setOnClickListener {
            val et = tokenInput!!
            if (et.inputType == 129) {
                et.inputType = 145
                toggleBtn.text = "隐藏"
            } else {
                et.inputType = 129
                toggleBtn.text = "显示"
            }
            et.setSelection(et.text.length)
        }

        connectBtn?.let { btn ->
            btn.applyDarkTheme(primary = true)
            btn.setOnClickListener { attemptConnect() }
        }

        requestAllPermissions()
        setupTabs()

        ConnectionManager.INSTANCE.getLastReceivedText()?.let { lastReceivedText ->
            val fn = lastReceivedText.first
            val txt = lastReceivedText.second
            handledTextContents["$fn|$txt"] = System.currentTimeMillis()
        }

        setupFlows()
        updateSetupVisibility()
        handleTextNotificationIntent(intent)
        handleFileTransferNotificationIntent(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isMirrorFullscreen) {
                    exitMirrorFullscreen()
                    return
                }
                if (currentTab != 0) {
                    switchTab(0, false)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    fun enterMirrorFullscreen(mirrorFrame: FrameLayout?, allUiComponents: List<View>) {
        if (isMirrorFullscreen) return
        requestedOrientation = 0
        val win = window
        val controller = WindowInsetsControllerCompat(win, win.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = 2
        supportActionBar?.hide()

        val mainContainer = findViewById<LinearLayout?>(R.id.mainContainer)
        val titleBar = mainContainer?.getChildAt(0) as? LinearLayout
        val tabScroll = mainContainer?.getChildAt(1) as? HorizontalScrollView
        titleBar?.visibility = View.GONE
        tabScroll?.visibility = View.GONE
        allUiComponents.forEach { it?.visibility = View.GONE }

        val pageContainer = findViewById<FrameLayout>(R.id.pageContainer)
        val layoutParams = pageContainer.layoutParams
        mirrorOriginalPageContainerLp = layoutParams as? LinearLayout.LayoutParams
        mirrorOriginalPageContainerLp?.let { lp ->
            val newLp = LinearLayout.LayoutParams(lp.width, 0)
            newLp.weight = 1.0f
            pageContainer.layoutParams = newLp
        }

        mirrorOriginalLp = mirrorFrame?.layoutParams
        (mirrorFrame?.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.weight = 1.0f
            lp.height = 0
            mirrorFrame.layoutParams = lp
        }

        isMirrorFullscreen = true
        mirrorFullscreenComponents = allUiComponents
        pageCache[8]?.findViewById<Button?>(R.id.btnMirrorFullscreenExit)?.visibility = View.VISIBLE
    }

    fun exitMirrorFullscreen() {
        if (!isMirrorFullscreen) return
        isMirrorFullscreen = false
        requestedOrientation = 1
        val win = window
        val controller = WindowInsetsControllerCompat(win, win.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        supportActionBar?.show()

        val mainContainer = findViewById<LinearLayout?>(R.id.mainContainer)
        val titleBar = mainContainer?.getChildAt(0) as? LinearLayout
        val tabScroll = mainContainer?.getChildAt(1) as? HorizontalScrollView
        titleBar?.visibility = View.VISIBLE
        tabScroll?.visibility = View.VISIBLE

        mirrorFullscreenComponents?.forEach { it?.visibility = View.VISIBLE }

        mirrorOriginalPageContainerLp?.let { lp ->
            findViewById<FrameLayout>(R.id.pageContainer).layoutParams = lp
        }

        val lp = mirrorOriginalLp
        val view8 = pageCache[8]
        if (lp != null && view8 != null) {
            val frame = view8.findViewById<FrameLayout?>(R.id.mirrorFrame)
            if (frame != null) {
                frame.layoutParams = lp
                frame.visibility = View.GONE
            }
        }
        mirrorOriginalLp = null
        mirrorOriginalPageContainerLp = null

        ConnectionManager.INSTANCE.sendMediaCommand("pc_stream_stop")
        ConnectionManager.INSTANCE.stopPcFramePolling()
        mirrorImageView?.setImageBitmap(null)
        pageCache[8]?.findViewById<TextView?>(R.id.mirrorStatus)?.text = "未启动"
        pageCache[8]?.findViewById<Button?>(R.id.btnMirrorFullscreenExit)?.visibility = View.GONE
    }

    private fun requestAllPermissions() {
        val prefs = getSharedPreferences("phonehub_prefs", 0)
        if (!prefs.getBoolean("permissions_requested", false) || Build.VERSION.SDK_INT >= 33) {
            val runtimePermissions = ArrayList<String>()
            if (Build.VERSION.SDK_INT >= 33) {
                runtimePermissions.add("android.permission.READ_MEDIA_IMAGES")
                runtimePermissions.add("android.permission.READ_MEDIA_VIDEO")
                runtimePermissions.add("android.permission.READ_MEDIA_AUDIO")
            } else {
                runtimePermissions.add("android.permission.READ_EXTERNAL_STORAGE")
                runtimePermissions.add("android.permission.WRITE_EXTERNAL_STORAGE")
            }
            runtimePermissions.add("android.permission.ACCESS_FINE_LOCATION")
            runtimePermissions.add("android.permission.ACCESS_COARSE_LOCATION")
            runtimePermissions.add("android.permission.CAMERA")
            runtimePermissions.add("android.permission.RECORD_AUDIO")
            runtimePermissions.add("android.permission.READ_PHONE_STATE")
            runtimePermissions.add("android.permission.CALL_PHONE")
            runtimePermissions.add("android.permission.ACTIVITY_RECOGNITION")
            try {
                ActivityCompat.requestPermissions(this, runtimePermissions.toTypedArray(), 1001)
            } catch (_: Exception) {
            }
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                try {
                    if (!Settings.canDrawOverlays(this)) {
                        val intent = Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:$packageName"))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                } catch (_: Exception) {
                }
            }, 500L)
            handler.postDelayed({
                try {
                    if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                        val intent = Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION")
                        intent.data = Uri.parse("package:$packageName")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                } catch (_: Exception) {
                }
            }, 750L)
            handler.postDelayed({
                try {
                    val pm = getSystemService("power") as? PowerManager
                    if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                        val intent = Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
                        intent.data = Uri.parse("package:$packageName")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                } catch (_: Exception) {
                }
            }, 1500L)
            handler.postDelayed({
                try {
                    if (!packageManager.canRequestPackageInstalls()) {
                        val intent = Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES")
                        intent.data = Uri.parse("package:$packageName")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                } catch (_: Exception) {
                }
                prefs.edit().putBoolean("permissions_requested", true).apply()
            }, 2000L)
        }
    }

    private fun setupTabs() {
    }

    fun switchTab(index: Int, forward: Boolean = true) {
        val pageContainer = pageContainer ?: return
        if (currentTab == index && pageContainer.childCount > 0) return

        val oldView = pageContainer.getChildAt(0)
        val newView = getPageView(index)
        if (currentTab != index) {
            cleanupPageResources(currentTab)
        }
        currentTab = index

        if (index == 8 && PhoneHubAccessibilityService.getInstance() == null) {
            Handler(mainLooper).postDelayed({
                Toast.makeText(this, "无障碍服务未开启，无法操控手机。请在设置→无障碍中开启 PhoneHub", Toast.LENGTH_LONG).show()
                try {
                    startActivity(Intent("android.settings.ACCESSIBILITY_SETTINGS"))
                } catch (_: Exception) {
                }
            }, 500L)
        }

        if (forward) {
            if (oldView != null && oldView !== newView) {
                newView.translationX = newView.width.toFloat()
                pageContainer.addView(newView)
                newView.animate().translationX(0.0f).setDuration(280L).withEndAction {
                    if (oldView.parent != null && oldView !== newView) {
                        pageContainer.removeView(oldView)
                        oldView.translationX = 0.0f
                    }
                }.start()
                return
            }
            if (oldView != null) {
                pageContainer.removeView(oldView)
            }
            pageContainer.addView(newView)
            return
        }

        if (oldView != null && oldView !== newView) {
            pageContainer.removeAllViews()
            pageContainer.addView(newView)
            pageContainer.addView(oldView)
            oldView.animate().translationX(oldView.width.toFloat()).setDuration(280L).withEndAction {
                pageContainer.removeView(oldView)
                oldView.translationX = 0.0f
            }.start()
            return
        }
        if (oldView != null) {
            pageContainer.removeView(oldView)
        }
        pageContainer.addView(newView)
    }

    private fun cleanupPageResources(oldIndex: Int) {
        when (oldIndex) {
            8 -> {
                ConnectionManager.INSTANCE.sendMediaCommand("mirror_stop")
                ConnectionManager.INSTANCE.sendMediaCommand("pc_stream_stop")
                ConnectionManager.INSTANCE.stopPcFramePolling()
                ConnectionManager.INSTANCE.stopPcAudioPolling()
                stopPhoneScreenCapture()
                if (isMirrorFullscreen) {
                    exitMirrorFullscreen()
                }
                val view = pageCache[8]
                view?.findViewById<FrameLayout?>(R.id.mirrorFrame)?.visibility = View.GONE
            }
            9 -> {
                ConnectionManager.INSTANCE.sendAction("camera_stop", emptyMap())
            }
        }
    }

    private fun getPageView(index: Int): View {
        pageCache[index]?.let { return it }
        val view: View = when (index) {
            0 -> getHomeView()
            1 -> getFilesView()
            2 -> getRemoteView()
            3 -> getFullKeyboardView()
            4 -> getClipboardView()
            5 -> getClipboardView()
            6 -> getLocationView()
            7 -> getScreenshotView()
            8 -> getMirrorView()
            9 -> getCameraView()
            10 -> getNotificationsView()
            11 -> getFileManagerView()
            12 -> {
                val v = LinearLayout(this)
                v.orientation = LinearLayout.VERTICAL
                v.setBackgroundColor(-14803426)
                v.setPadding(dp(32), dp(32), dp(32), dp(32))
                val tv = TextView(this)
                tv.text = "APK 安装已改为自动安装\n\n电脑端发送 APK 后，手机将自动安装"
                tv.setTextColor(-5197648)
                tv.textSize = 14.0f
                v.addView(tv)
                v
            }
            13 -> getAppManagerView()
            14 -> getPowerView()
            15 -> getPushWebView()
            16 -> getSettingsView()
            else -> getHomeView()
        }
        pageCache[index] = view
        return view
    }

    private fun dp(v: Int): Int {
        return NativeButtonKt.dpToPx(v.toFloat()).toInt()
    }

    private fun getHomeView(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(-14803426)
        root.setPadding(dp(16), dp(16), dp(16), dp(16))

        val statusBox = LinearLayout(this)
        statusBox.orientation = LinearLayout.VERTICAL
        statusBox.setBackgroundColor(-13816531)
        statusBox.setPadding(dp(16), dp(16), dp(16), dp(16))
        val connLabel = TextView(this)
        connLabel.text = "连接状态"
        connLabel.setTextColor(-5197648)
        connLabel.textSize = 12.0f
        connLabel.setPadding(0, 0, 0, dp(8))
        val connStatusHome = TextView(this)
        connStatusHome.id = R.id.connStatusHome
        connStatusHome.text = "未连接"
        connStatusHome.setTextColor(-3066824)
        connStatusHome.textSize = 18.0f
        connStatusHome.setTypeface(connStatusHome.typeface, Typeface.BOLD)
        val channelHome = TextView(this)
        channelHome.id = R.id.channelHome
        channelHome.text = "通道: 无"
        channelHome.setTextColor(-5197648)
        channelHome.textSize = 12.0f
        channelHome.setPadding(0, dp(4), 0, 0)
        statusBox.addView(connLabel)
        statusBox.addView(connStatusHome)
        statusBox.addView(channelHome)
        initHomeStatus(connStatusHome, channelHome)
        val statusLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        statusLp.bottomMargin = dp(12)
        root.addView(statusBox, statusLp)

        val recentLabel = TextView(this)
        recentLabel.text = "最近操作"
        recentLabel.setTextColor(-1)
        recentLabel.textSize = 14.0f
        recentLabel.setPadding(0, 0, 0, dp(8))
        root.addView(recentLabel)

        val recentFuncs = getRecentFunctions(6)
        if (recentFuncs.isEmpty()) {
            val emptyText = TextView(this)
            emptyText.text = "暂无最近操作，点击下方功能开始使用"
            emptyText.setTextColor(-5197648)
            emptyText.textSize = 12.0f
            emptyText.setPadding(0, 0, 0, dp(12))
            root.addView(emptyText)
        } else {
            val recentScroll = HorizontalScrollView(this)
            val recentRow = LinearLayout(this)
            recentRow.orientation = LinearLayout.HORIZONTAL
            recentRow.setPadding(0, 0, 0, dp(12))
            for (func in recentFuncs) {
                val btn = buildFuncButton(func, true)
                btn.setOnClickListener { onFuncClicked(func) }
                val lp = LinearLayout.LayoutParams(dp(84), LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(dp(4), 0, dp(4), 0)
                recentRow.addView(btn, lp)
            }
            recentScroll.addView(recentRow)
            root.addView(recentScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        val allLabel = TextView(this)
        allLabel.text = "所有功能"
        allLabel.setTextColor(-1)
        allLabel.textSize = 14.0f
        allLabel.setPadding(0, 0, 0, dp(8))
        root.addView(allLabel)

        var row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        var count = 0
        for (func in allFuncList) {
            val btn = buildFuncButton(func, false)
            btn.setOnClickListener { onFuncClicked(func) }
            row.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f))
            count++
            if (count % 3 == 0) {
                val rowLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                rowLp.bottomMargin = dp(8)
                root.addView(row, rowLp)
                row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
            }
        }
        if (count % 3 != 0) {
            val rowLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            rowLp.bottomMargin = dp(8)
            root.addView(row, rowLp)
        }

        val scroll = ScrollView(this)
        scroll.addView(root)
        return scroll
    }

    private fun initHomeStatus(connStatusHome: TextView, channelHome: TextView) {
        val state = ConnectionManager.INSTANCE.connectionState.value
        when (state) {
            ConnectionManager.ConnectionState.CONNECTED -> {
                connStatusHome.text = "已连接"
                connStatusHome.setTextColor(-15696880)
            }
            ConnectionManager.ConnectionState.CONNECTING -> {
                connStatusHome.text = "连接中..."
                connStatusHome.setTextColor(-18176)
            }
            else -> {
                connStatusHome.text = "未连接"
                connStatusHome.setTextColor(-3066824)
            }
        }
        val ch = ConnectionManager.INSTANCE.currentChannel.value
        val chName = when (ch) {
            ConnectionManager.ChannelType.WIFI -> "WiFi 直连"
            ConnectionManager.ChannelType.ADB -> "USB 数据线"
            else -> "无"
        }
        channelHome.text = "通道: $chName"
    }

    private fun buildFuncButton(func: FuncInfo, small: Boolean): View {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.gravity = 17
        container.setBackgroundColor(-13816531)
        container.setPadding(dp(8), dp(12), dp(8), dp(12))
        container.minimumHeight = dp(80)
        val iconSize = if (small) 22.0f else 28.0f
        val textSize = if (small) 10.0f else 11.0f
        val iconTv = TextView(this)
        iconTv.text = func.icon
        iconTv.textSize = iconSize
        iconTv.gravity = 17
        val nameTv = TextView(this)
        nameTv.text = func.name
        nameTv.setTextColor(-1)
        nameTv.textSize = textSize
        nameTv.gravity = 17
        nameTv.setPadding(0, dp(6), 0, 0)
        container.addView(iconTv)
        container.addView(nameTv)
        return container
    }

    fun onFuncClicked(func: FuncInfo) {
        recordFunctionUse(func.name)
        pageCache.remove(0)
        if (func.tabIndex == -1) {
            showSendTextDialog()
        } else {
            switchTab(func.tabIndex, false)
        }
    }

    private fun recordFunctionUse(name: String) {
        val prefs = getSharedPreferences("phonehub_func_usage", 0)
        val count = prefs.getInt("${name}_count", 0) + 1
        prefs.edit().putInt("${name}_count", count).putLong("${name}_time", System.currentTimeMillis()).apply()
    }

    private fun getRecentFunctions(max: Int): List<FuncInfo> {
        val prefs = getSharedPreferences("phonehub_func_usage", 0)
        return allFuncList
            .map { Triple(it, prefs.getInt("${it.name}_count", 0), prefs.getLong("${it.name}_time", 0L)) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Triple<FuncInfo, Int, Long>> { it.second }.thenByDescending { it.third })
            .take(max)
            .map { it.first }
    }

    fun loadFileHistory() {
        fileHistory.clear()
        val prefs = getSharedPreferences("phonehub_prefs", 0)
        val raw = prefs.getString("file_transfer_history", "") ?: ""
        if (raw.isEmpty()) return
        for (line in raw.split("\n")) {
            if (line.isNotBlank()) {
                val parts = line.split("\t")
                if (parts.size == 3) {
                    val ts = parts[0].toLongOrNull()
                    if (ts != null) {
                        fileHistory.add(FileHistoryItem(ts, parts[2], parts[1]))
                    }
                }
            }
        }
    }

    fun saveFileHistory() {
        val prefs = getSharedPreferences("phonehub_prefs", 0)
        val raw = fileHistory.joinToString("\n") { "${it.time}\t${it.direction}\t${it.text}" }
        prefs.edit().putString("file_transfer_history", raw).apply()
    }

    fun addFileHistory(text: String, direction: String) {
        fileHistory.add(0, FileHistoryItem(System.currentTimeMillis(), text, direction))
        if (fileHistory.size > 500) {
            fileHistory.subList(500, fileHistory.size).clear()
        }
        saveFileHistory()
        refreshFileHistoryList()
    }

    fun refreshFileHistoryList() {
        val v = pageCache[1] ?: return
        val list = v.findViewById<ListView?>(R.id.fileHistoryList) ?: return
        if (fileHistory.isEmpty()) {
            list.adapter = null
            return
        }
        val displays = fileHistory.map { item ->
            val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.time))
            "[${item.direction}] ${item.text}\n$time"
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displays)
        list.setOnItemClickListener { _, _, pos, _ ->
            val item = fileHistory[pos]
            val dir = ConnectionManager.INSTANCE.receiveDir
            val file = dir?.let { File(it, item.text) }
            if (file == null || !file.exists()) {
                Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show()
                return@setOnItemClickListener
            }
            try {
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                val ext = item.text.substringAfterLast('.', "").lowercase(Locale.ROOT)
                var mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                if (mime == null) mime = "*/*"
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, mime)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFilesView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_files, null)
        v.findViewById<Button?>(R.id.selectFileBtn)?.let {
            it.applyDarkTheme(primary = true)
        }
        v.findViewById<Button?>(R.id.pauseFileBtn)?.let {
            it.applyDarkTheme(primary = false)
        }
        v.findViewById<Button?>(R.id.cancelFileBtn)?.let {
            it.applyDarkTheme(primary = false)
        }
        v.findViewById<Button?>(R.id.doneFileBtn)?.let {
            it.applyDarkTheme(primary = false)
        }
        if (fileHistory.isEmpty()) {
            loadFileHistory()
        }
        v.post { refreshFileHistoryList() }
        v.findViewById<Button?>(R.id.selectFileBtn)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            startActivityForResult(Intent.createChooser(intent, "选择文件"), SELECT_FILE_CODE)
        }
        v.findViewById<Button?>(R.id.pauseFileBtn)?.setOnClickListener {
            val btn = v.findViewById<Button?>(R.id.pauseFileBtn) ?: return@setOnClickListener
            if (btn.text.toString() == "暂停") {
                ConnectionManager.INSTANCE.pauseTransfer()
                btn.text = "继续"
                v.findViewById<TextView?>(R.id.fileSpeedText)?.text = "已暂停"
            } else {
                ConnectionManager.INSTANCE.resumeTransfer()
                btn.text = "暂停"
                v.findViewById<TextView?>(R.id.fileSpeedText)?.text = "继续传输..."
            }
        }
        v.findViewById<Button?>(R.id.cancelFileBtn)?.setOnClickListener {
            ConnectionManager.INSTANCE.cancelTransfer()
            Toast.makeText(this, "文件传输已取消", Toast.LENGTH_SHORT).show()
            v.findViewById<TextView?>(R.id.fileNameText)?.text = "已取消"
            v.findViewById<ProgressBar?>(R.id.fileProgress)?.progress = 0
            v.findViewById<TextView?>(R.id.fileProgressText)?.text = ""
            v.findViewById<TextView?>(R.id.fileSpeedText)?.text = ""
            v.findViewById<Button?>(R.id.cancelFileBtn)?.isEnabled = false
            v.findViewById<Button?>(R.id.pauseFileBtn)?.isEnabled = false
            v.findViewById<Button?>(R.id.pauseFileBtn)?.text = "暂停"
            v.findViewById<Button?>(R.id.selectFileBtn)?.isEnabled = true
            v.findViewById<Button?>(R.id.doneFileBtn)?.visibility = View.GONE
            v.findViewById<LinearLayout?>(R.id.fileProgressContainer)?.visibility = View.GONE
            v.findViewById<LinearLayout?>(R.id.fileTransferBtnContainer)?.visibility = View.GONE
        }
        v.findViewById<Button?>(R.id.doneFileBtn)?.setOnClickListener {
            val v2 = pageCache[1] ?: return@setOnClickListener
            var name = v2.findViewById<TextView?>(R.id.fileNameText)?.text?.toString() ?: ""
            if (name.isNotEmpty() && name != "等待传输..." && name != "已取消") {
                var direction = "发送"
                if (!name.startsWith("发送") && !name.startsWith("已发送")) {
                    direction = "接收"
                }
                val record = name.removePrefix("发送中: ").removePrefix("接收中: ")
                addFileHistory(record, direction)
            }
            v2.findViewById<TextView?>(R.id.fileNameText)?.text = "等待传输..."
            v2.findViewById<ProgressBar?>(R.id.fileProgress)?.progress = 0
            v2.findViewById<TextView?>(R.id.fileProgressText)?.text = ""
            v2.findViewById<TextView?>(R.id.fileSpeedText)?.text = ""
            v2.findViewById<Button?>(R.id.cancelFileBtn)?.isEnabled = false
            v2.findViewById<Button?>(R.id.pauseFileBtn)?.isEnabled = false
            v2.findViewById<Button?>(R.id.pauseFileBtn)?.text = "暂停"
            v2.findViewById<Button?>(R.id.selectFileBtn)?.isEnabled = true
            v2.findViewById<Button?>(R.id.doneFileBtn)?.isEnabled = false
            v2.findViewById<Button?>(R.id.doneFileBtn)?.visibility = View.GONE
            v2.findViewById<LinearLayout?>(R.id.fileProgressContainer)?.visibility = View.GONE
            v2.findViewById<LinearLayout?>(R.id.fileTransferBtnContainer)?.visibility = View.GONE
        }

        ConnectionManager.INSTANCE.fileTransferProgress.value?.let { currentProgress ->
            v.findViewById<LinearLayout?>(R.id.fileProgressContainer)?.visibility = View.VISIBLE
            v.findViewById<LinearLayout?>(R.id.fileTransferBtnContainer)?.visibility = View.VISIBLE
            v.findViewById<TextView?>(R.id.fileNameText)?.text = currentProgress.fileName
            val pct = if (currentProgress.total > 0) ((currentProgress.sent * 100) / currentProgress.total).toInt() else 0
            v.findViewById<ProgressBar?>(R.id.fileProgress)?.progress = pct
            v.findViewById<Button?>(R.id.cancelFileBtn)?.isEnabled = true
            v.findViewById<Button?>(R.id.pauseFileBtn)?.isEnabled = true
            v.findViewById<Button?>(R.id.selectFileBtn)?.isEnabled = false
            v.findViewById<Button?>(R.id.doneFileBtn)?.visibility = View.GONE
            v.findViewById<Button?>(R.id.doneFileBtn)?.isEnabled = false
            val dirText = if (currentProgress.receiving) "接收中" else "发送中"
            v.findViewById<TextView?>(R.id.fileNameText)?.text = "$dirText: ${currentProgress.fileName}"
            v.findViewById<TextView?>(R.id.fileProgressText)?.text = "${fmtSize(currentProgress.sent)} / ${fmtSize(currentProgress.total)}"
        }

        BuildersKt.launch$default(
            LifecycleOwnerKt.getLifecycleScope(this),
            null, null,
            MainActivity_getFilesView_6(this, null),
            3, null
        )
        return v
    }

    private fun fmtSize(b: Long): String {
        if (b >= 1073741824L) return String.format("%.2f GB", b / 1.073741824E9)
        if (b >= 1048576L) return String.format("%.1f MB", b / 1048576.0)
        if (b < 1024L) return "$b B"
        return String.format("%.0f KB", b / 1024.0)
    }

    private fun getRemoteView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_remote, null)
        val buttons = listOf(
            Pair(R.id.btnPrev, "media_prev"),
            Pair(R.id.btnPlayPause, "media_play_pause"),
            Pair(R.id.btnNext, "media_next"),
            Pair(R.id.btnVolDown, "vol_down"),
            Pair(R.id.btnMute, "vol_mute"),
            Pair(R.id.btnVolUp, "vol_up"),
            Pair(R.id.btnLock, "lock")
        )
        for ((id, cmd) in buttons) {
            v.findViewById<Button?>(id)?.let {
                it.applyDarkTheme(primary = (cmd == "media_play_pause"))
                it.setOnClickListener { ConnectionManager.INSTANCE.sendMediaCommand(cmd) }
            }
        }
        v.findViewById<Button?>(R.id.btnScreenshot)?.let {
            it.applyDarkTheme(primary = false)
            it.setOnClickListener {
                try {
                    ConnectionManager.INSTANCE.requestPcScreenshot()
                } catch (_: Exception) {
                }
            }
        }
        v.findViewById<Button?>(R.id.btnKeyboard)?.let {
            it.applyDarkTheme(primary = false)
            it.setOnClickListener {
                val keyboardView = getFullKeyboardView()
                val dialog = AlertDialog.Builder(this).setView(keyboardView).setOnDismissListener { }.create()
                dialog.window?.setBackgroundDrawableResource(android.R.color.black)
                dialog.window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
                dialog.show()
            }
        }
        val mediaInfoText = v.findViewById<TextView?>(R.id.mediaInfoText)
        val mediaCoverImg = v.findViewById<ImageView?>(R.id.mediaCoverImg)
        BuildersKt.launch$default(
            CoroutineScopeKt.CoroutineScope(Dispatchers.Main),
            null, null,
            MainActivity_getRemoteView_4(mediaInfoText, null),
            3, null
        )
        BuildersKt.launch$default(
            CoroutineScopeKt.CoroutineScope(Dispatchers.Main),
            null, null,
            MainActivity_getRemoteView_5(mediaCoverImg, null),
            3, null
        )
        return v
    }

    private fun wireFullKeyboard(rootView: View) {
        val modifierStatus = rootView.findViewById<TextView?>(R.id.modifierStatus)
        val lockedModifiers = LinkedHashSet<String>()
        val keyMap = mapOf(
            Pair(R.id.k_1, "1"), Pair(R.id.k_2, "2"), Pair(R.id.k_3, "3"), Pair(R.id.k_4, "4"),
            Pair(R.id.k_5, "5"), Pair(R.id.k_6, "6"), Pair(R.id.k_7, "7"), Pair(R.id.k_8, "8"),
            Pair(R.id.k_9, "9"), Pair(R.id.k_0, "0"),
            Pair(R.id.k_q, "q"), Pair(R.id.k_w, "w"), Pair(R.id.k_e, "e"), Pair(R.id.k_r, "r"),
            Pair(R.id.k_t, "t"), Pair(R.id.k_y, "y"), Pair(R.id.k_u, "u"), Pair(R.id.k_i, "i"),
            Pair(R.id.k_o, "o"), Pair(R.id.k_p, "p"),
            Pair(R.id.k_a, "a"), Pair(R.id.k_s, "s"), Pair(R.id.k_d, "d"), Pair(R.id.k_f, "f"),
            Pair(R.id.k_g, "g"), Pair(R.id.k_h, "h"), Pair(R.id.k_j, "j"), Pair(R.id.k_k, "k"),
            Pair(R.id.k_l, "l"),
            Pair(R.id.k_z, "z"), Pair(R.id.k_x, "x"), Pair(R.id.k_c, "c"), Pair(R.id.k_v, "v"),
            Pair(R.id.k_b, "b"), Pair(R.id.k_n, "n"), Pair(R.id.k_m, "m"),
            Pair(R.id.k_comma, ","), Pair(R.id.k_dot, "."),
            Pair(R.id.k_space, "space"), Pair(R.id.k_enter, "enter"), Pair(R.id.k_bksp, "backspace"),
            Pair(R.id.k_up, "up"), Pair(R.id.k_down, "down"), Pair(R.id.k_left, "left"), Pair(R.id.k_right, "right"),
            Pair(R.id.k_f1, "f1"), Pair(R.id.k_f2, "f2"), Pair(R.id.k_f3, "f3"), Pair(R.id.k_f4, "f4"),
            Pair(R.id.k_f5, "f5"), Pair(R.id.k_f6, "f6"), Pair(R.id.k_f7, "f7"), Pair(R.id.k_f8, "f8"),
            Pair(R.id.k_f9, "f9"), Pair(R.id.k_f10, "f10"), Pair(R.id.k_f11, "f11"), Pair(R.id.k_f12, "f12")
        )
        for ((id, key) in keyMap) {
            rootView.findViewById<Button?>(id)?.setOnClickListener {
                val modStr = if (lockedModifiers.isEmpty()) "" else lockedModifiers.joinToString("+") + "+"
                ConnectionManager.INSTANCE.sendMediaCommand("key_$modStr$key")
            }
        }
        val modMap = mapOf(
            Pair(R.id.k_shift, "shift"), Pair(R.id.k_ctrl, "ctrl"),
            Pair(R.id.k_alt, "alt"), Pair(R.id.k_win, "win")
        )
        for ((id, mod) in modMap) {
            val btn = rootView.findViewById<Button?>(id)
            btn?.setOnClickListener {
                if (lockedModifiers.contains(mod)) {
                    lockedModifiers.remove(mod)
                    btn.setBackgroundResource(R.drawable.kb_modifier_bg)
                } else {
                    lockedModifiers.add(mod)
                    btn.setBackgroundColor(-12947515)
                }
                val locked = lockedModifiers.joinToString("+") { "$it(锁)" }
                modifierStatus?.text = "修饰键: ${if (locked.isEmpty()) "无" else locked}"
            }
            btn?.setOnLongClickListener(null)
        }
    }

    private fun getFullKeyboardView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_full_keyboard, null)
        wireFullKeyboard(v)
        return v
    }

    private fun getClipboardView(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(-14803426)
        root.setPadding(dp(16), dp(16), dp(16), dp(16))

        val titleTv = TextView(this)
        titleTv.text = "剪贴板"
        titleTv.setTextColor(-1)
        titleTv.textSize = 20.0f
        titleTv.setTypeface(titleTv.typeface, Typeface.BOLD)
        titleTv.setPadding(0, 0, 0, dp(16))
        root.addView(titleTv)

        val currentBox = LinearLayout(this)
        currentBox.orientation = LinearLayout.VERTICAL
        currentBox.setBackgroundColor(-13816531)
        currentBox.setPadding(dp(16), dp(16), dp(16), dp(16))
        val currentLabel = TextView(this)
        currentLabel.text = "当前剪贴板:"
        currentLabel.setTextColor(-5197648)
        currentLabel.textSize = 12.0f
        currentLabel.setPadding(0, 0, 0, dp(8))
        val currentClipText = TextView(this)
        currentClipText.id = R.id.currentClipText
        currentClipText.text = ""
        currentClipText.setTextColor(-1)
        currentClipText.textSize = 14.0f
        currentClipText.minHeight = dp(60)
        currentClipText.setPadding(dp(8), dp(8), dp(8), dp(8))
        currentClipText.setBackgroundColor(-14803426)
        try {
            val cm = getSystemService("clipboard") as ClipboardManager
            val primaryClip = cm.primaryClip
            if (primaryClip != null && primaryClip.itemCount > 0) {
                val text = primaryClip.getItemAt(0).coerceToText(this).toString()
                if (text.isNotEmpty()) {
                    currentClipText.text = text
                }
            }
        } catch (_: Exception) {
        }

        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.setPadding(0, dp(8), 0, 0)
        val copyBtn = Button(this)
        copyBtn.text = "复制内容"
        copyBtn.applyDarkTheme(primary = false)
        val pushBtn = Button(this)
        pushBtn.text = "推送到电脑"
        pushBtn.applyDarkTheme(primary = true)
        val copyLp = LinearLayout.LayoutParams(0, dp(48), 1.0f)
        copyLp.rightMargin = dp(8)
        btnRow.addView(copyBtn, copyLp)
        btnRow.addView(pushBtn, LinearLayout.LayoutParams(0, dp(48), 1.0f))

        copyBtn.setOnClickListener {
            val text = currentClipText.text.toString()
            if (text.isNotEmpty()) {
                val cm = getSystemService("clipboard") as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("PhoneHub", text))
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
            }
        }
        pushBtn.setOnClickListener {
            var text = ""
            try {
                val cm = getSystemService("clipboard") as ClipboardManager
                val primaryClip = cm.primaryClip
                if (primaryClip != null && primaryClip.itemCount > 0) {
                    text = primaryClip.getItemAt(0).coerceToText(this).toString()
                }
            } catch (_: Exception) {
            }
            if (text.isNotEmpty()) {
                ConnectionManager.INSTANCE.sendClipboard(text)
                Toast.makeText(this, "已推送", Toast.LENGTH_SHORT).show()
            }
        }

        currentBox.addView(currentLabel)
        currentBox.addView(currentClipText)
        currentBox.addView(btnRow)
        val currentLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        currentLp.bottomMargin = dp(12)
        root.addView(currentBox, currentLp)

        val histLabel = TextView(this)
        histLabel.text = "剪贴板历史 / 收藏"
        histLabel.setTextColor(-1)
        histLabel.textSize = 16.0f
        histLabel.setTypeface(histLabel.typeface, Typeface.BOLD)
        histLabel.setPadding(0, 0, 0, dp(8))
        root.addView(histLabel)

        val searchBar = LinearLayout(this)
        searchBar.orientation = LinearLayout.HORIZONTAL
        searchBar.setBackgroundColor(-13816531)
        searchBar.setPadding(dp(8), dp(8), dp(8), dp(8))
        val searchEdit = EditText(this)
        searchEdit.hint = "搜索..."
        searchEdit.setTextColor(-1)
        searchEdit.setHintTextColor(-10066330)
        searchEdit.setBackgroundColor(-14803426)
        searchEdit.setPadding(dp(8), 0, dp(8), 0)
        searchEdit.textSize = 12.0f
        searchEdit.inputType = 1
        val btnHist = Button(this)
        btnHist.text = "历史"
        val btnFav = Button(this)
        btnFav.text = "收藏"
        btnHist.applyDarkTheme(primary = true)
        btnFav.applyDarkTheme(primary = false)
        searchBar.addView(searchEdit, LinearLayout.LayoutParams(0, dp(40), 1.0f))
        val btnHistLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40))
        btnHistLp.leftMargin = dp(4)
        searchBar.addView(btnHist, btnHistLp)
        val btnFavLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40))
        btnFavLp.leftMargin = dp(4)
        searchBar.addView(btnFav, btnFavLp)
        val searchLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        searchLp.bottomMargin = dp(8)
        root.addView(searchBar, searchLp)

        val list = ListView(this)
        val emptyText = TextView(this)
        emptyText.text = "暂无记录"
        emptyText.setTextColor(-10066330)
        emptyText.textSize = 13.0f
        emptyText.gravity = 17
        emptyText.visibility = View.GONE
        val listLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0)
        listLp.weight = 1.0f
        root.addView(list, listLp)
        root.addView(emptyText)

        fun refresh() {
            val q = searchEdit.text.toString().trim().lowercase(Locale.ROOT)
            val items = if (clipViewMode == "history") {
                if (q.isEmpty()) ConnectionManager.INSTANCE.clipboardHistory.value
                else ConnectionManager.INSTANCE.searchClipboardHistory(q)
            } else {
                if (q.isEmpty()) ConnectionManager.INSTANCE.clipboardFavorites.value
                else ConnectionManager.INSTANCE.searchClipboardFavorites(q)
            }
            if (items.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                emptyText.text = if (clipViewMode == "history") "暂无历史" else "暂无收藏"
                list.adapter = null
                return
            }
            emptyText.visibility = View.GONE
            val displays = items.map { it ->
                val favMark = if (it.favorite) " ★" else ""
                "${it.content.take(80)}\n[${it.source}] ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.timestamp))}$favMark"
            }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displays)
            list.setOnItemClickListener { _, _, pos, _ ->
                val item = items[pos]
                val cm = getSystemService("clipboard") as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("PhoneHub", item.content))
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
            }
            list.setOnItemLongClickListener { _, _, pos, _ ->
                val item = items[pos]
                ConnectionManager.INSTANCE.toggleFavorite(item)
                Toast.makeText(this, if (!item.favorite) "已收藏" else "取消收藏", Toast.LENGTH_SHORT).show()
                refresh()
                true
            }
        }

        btnHist.setOnClickListener {
            clipViewMode = "history"
            btnHist.applyDarkTheme(primary = true)
            btnFav.applyDarkTheme(primary = false)
            refresh()
        }
        btnFav.setOnClickListener {
            clipViewMode = "favorite"
            btnFav.applyDarkTheme(primary = true)
            btnHist.applyDarkTheme(primary = false)
            refresh()
        }
        searchEdit.setOnEditorActionListener { _, _, _ ->
            refresh()
            true
        }
        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refresh()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        list.post { refresh() }
        return root
    }

    private fun getLocationView(): View {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.gravity = 17
        container.setBackgroundColor(-14803426)
        container.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        val titleTv = TextView(this)
        titleTv.text = "移动路线图"
        titleTv.textColor = -1
        titleTv.textSize = 22.0f
        titleTv.setPadding(0, 0, 0, dp(32))
        val placeholderTv = TextView(this)
        placeholderTv.text = "该功能暂未开放，敬请期待"
        placeholderTv.textColor = -5197648
        placeholderTv.textSize = 16.0f
        container.addView(titleTv)
        container.addView(placeholderTv)
        return container
    }

    private fun getScreenshotView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_screenshot, null)
        v.findViewById<Button?>(R.id.btnTakeScreenshot)?.let {
            it.applyDarkTheme(primary = true)
            it.setOnClickListener { ConnectionManager.INSTANCE.triggerScreenshot() }
        }
        refreshScreenshotList(v)
        return v
    }

    private fun refreshScreenshotList(v: View) {
        val list = v.findViewById<ListView?>(R.id.screenshotList) ?: return
        val empty = v.findViewById<TextView?>(R.id.screenshotEmpty) ?: return
        val localDir = File(getExternalFilesDir(null), "Received")
        val files = (localDir.listFiles { f ->
            f.name.endsWith(".png") && f.name.startsWith("screenshot_")
        }?.toList() ?: emptyList()).sortedByDescending { it.lastModified() }
        if (files.isEmpty()) {
            empty.visibility = View.VISIBLE
            list.adapter = null
            return
        }
        empty.visibility = View.GONE
        val names = files.map { "${it.name}\n${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.lastModified()))}" }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        list.setOnItemClickListener { _, _, pos, _ ->
            showScreenshotViewer(files[pos])
        }
    }

    class BrushOp(val path: Path, val color: Int, val width: Float)
    class ArrowOp(val sx: Float, val sy: Float, var ex: Float, var ey: Float, val color: Int, val width: Float)
    class RectOp(val rect: RectF, val color: Int, val width: Float)
    class TextOp(val text: String, val x: Float, val y: Float, val color: Int, val size: Float)
    class MosaicOp(val points: List<Pair<Float, Float>>)

    inner class AnnotationOverlayView(ctx: android.content.Context, val srcBitmap: Bitmap) : View(ctx) {
        private val operations: MutableList<Any> = ArrayList()
        var currentTool: String = "brush"
        private var scale: Float = 1.0f
        private var offsetX: Float = 0.0f
        private var offsetY: Float = 0.0f
        private var mosaicBmp: Bitmap? = null
        private var currentOp: Any? = null

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0 && srcBitmap.width > 0) {
                scale = Math.min(w.toFloat() / srcBitmap.width, h.toFloat() / srcBitmap.height)
                offsetX = (w - srcBitmap.width * scale) / 2.0f
                offsetY = (h - srcBitmap.height * scale) / 2.0f
                val smallW = RangesKt.coerceAtLeast(srcBitmap.width / 15, 1)
                val smallH = RangesKt.coerceAtLeast(srcBitmap.height / 15, 1)
                val small = Bitmap.createScaledBitmap(srcBitmap, smallW, smallH, false)
                mosaicBmp = Bitmap.createScaledBitmap(small, srcBitmap.width, srcBitmap.height, false)
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.save()
            canvas.translate(offsetX, offsetY)
            canvas.scale(scale, scale)
            canvas.drawBitmap(srcBitmap, 0.0f, 0.0f, null)
            for (op in operations) {
                drawOp(canvas, op)
            }
            currentOp?.let { drawOp(canvas, it) }
            canvas.restore()
        }

        private fun drawOp(canvas: Canvas, op: Any) {
            when (op) {
                is BrushOp -> {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG)
                    p.color = op.color
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = op.width
                    p.strokeCap = Paint.Cap.ROUND
                    p.strokeJoin = Paint.Join.ROUND
                    canvas.drawPath(op.path, p)
                }
                is ArrowOp -> {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG)
                    p.color = op.color
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = op.width
                    p.strokeCap = Paint.Cap.ROUND
                    canvas.drawLine(op.sx, op.sy, op.ex, op.ey, p)
                    val angle = Math.atan2((op.ey - op.sy).toDouble(), (op.ex - op.sx).toDouble())
                    canvas.drawLine(op.ex, op.ey, (op.ex - 40.0f * Math.cos(angle - 0.5235987755982988)).toFloat(), (op.ey - 40.0f * Math.sin(angle - 0.5235987755982988)).toFloat(), p)
                    canvas.drawLine(op.ex, op.ey, (op.ex - 40.0f * Math.cos(angle + 0.5235987755982988)).toFloat(), (op.ey - 40.0f * Math.sin(0.5235987755982988 + angle)).toFloat(), p)
                }
                is RectOp -> {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG)
                    p.color = op.color
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = op.width
                    canvas.drawRect(op.rect, p)
                }
                is TextOp -> {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG)
                    p.color = op.color
                    p.textSize = op.size
                    p.typeface = Typeface.DEFAULT_BOLD
                    canvas.drawText(op.text, op.x, op.y, p)
                }
                is MosaicOp -> {
                    val mb = mosaicBmp ?: return
                    val radius = 25.0f
                    for (pair in op.points) {
                        val x = pair.first
                        val y = pair.second
                        val srcLeft = RangesKt.coerceAtLeast((x - radius).toInt(), 0)
                        val srcTop = RangesKt.coerceAtLeast((y - radius).toInt(), 0)
                        val srcRight = RangesKt.coerceAtMost((x + radius).toInt(), srcBitmap.width)
                        val srcBottom = RangesKt.coerceAtMost((y + radius).toInt(), srcBitmap.height)
                        if (srcRight > srcLeft && srcBottom > srcTop) {
                            val srcRect = android.graphics.Rect(srcLeft, srcTop, srcRight, srcBottom)
                            val dstRect = RectF(srcLeft.toFloat(), srcTop.toFloat(), srcRight.toFloat(), srcBottom.toFloat())
                            canvas.drawBitmap(mb, srcRect, dstRect, null)
                        }
                    }
                }
            }
        }

        fun toBitmapX(viewX: Float): Float = (viewX - offsetX) / scale
        fun toBitmapY(viewY: Float): Float = (viewY - offsetY) / scale

        fun undo() {
            if (operations.isNotEmpty()) {
                operations.removeAt(operations.lastIndex)
                invalidate()
            }
        }

        fun hasOperations(): Boolean = operations.isNotEmpty()

        fun exportBitmap(): Bitmap {
            val result = Bitmap.createBitmap(srcBitmap.width, srcBitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawBitmap(srcBitmap, 0.0f, 0.0f, null)
            for (op in operations) {
                drawOp(canvas, op)
            }
            return result
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val bitmapX = toBitmapX(event.x)
            val bitmapY = toBitmapY(event.y)
            val f = 6.0f / scale
            when (currentTool) {
                "brush" -> {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            val path = Path()
                            path.moveTo(bitmapX, bitmapY)
                            currentOp = BrushOp(path, SupportMenu.CATEGORY_MASK, f)
                        }
                        MotionEvent.ACTION_UP -> {
                            (currentOp as? BrushOp)?.path?.lineTo(bitmapX, bitmapY)
                            currentOp?.let { operations.add(it) }
                            currentOp = null
                            invalidate()
                        }
                        MotionEvent.ACTION_MOVE -> {
                            (currentOp as? BrushOp)?.path?.lineTo(bitmapX, bitmapY)
                            invalidate()
                        }
                    }
                }
                "arrow" -> {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            currentOp = ArrowOp(bitmapX, bitmapY, bitmapX, bitmapY, SupportMenu.CATEGORY_MASK, f)
                        }
                        MotionEvent.ACTION_UP -> {
                            (currentOp as? ArrowOp)?.let {
                                it.ex = bitmapX
                                it.ey = bitmapY
                            }
                            currentOp?.let { operations.add(it) }
                            currentOp = null
                            invalidate()
                        }
                        MotionEvent.ACTION_MOVE -> {
                            (currentOp as? ArrowOp)?.let {
                                it.ex = bitmapX
                                it.ey = bitmapY
                            }
                            invalidate()
                        }
                    }
                }
                "rect" -> {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            currentOp = RectOp(RectF(bitmapX, bitmapY, bitmapX, bitmapY), SupportMenu.CATEGORY_MASK, f)
                        }
                        MotionEvent.ACTION_UP -> {
                            currentOp?.let { operations.add(it) }
                            currentOp = null
                            invalidate()
                        }
                        MotionEvent.ACTION_MOVE -> {
                            (currentOp as? RectOp)?.rect?.let { rect ->
                                rect.left = Math.min(rect.left, bitmapX)
                                rect.top = Math.min(rect.top, bitmapY)
                                rect.right = Math.max(rect.right, bitmapX)
                                rect.bottom = Math.max(rect.bottom, bitmapY)
                            }
                            invalidate()
                        }
                    }
                }
                "mosaic" -> {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            currentOp = MosaicOp(mutableListOf(Pair(bitmapX, bitmapY)))
                        }
                        MotionEvent.ACTION_UP -> {
                            (currentOp as? MosaicOp)?.points?.toMutableList()?.add(Pair(bitmapX, bitmapY))
                            currentOp?.let { operations.add(it) }
                            currentOp = null
                            invalidate()
                        }
                        MotionEvent.ACTION_MOVE -> {
                            (currentOp as? MosaicOp)?.points?.toMutableList()?.add(Pair(bitmapX, bitmapY))
                            invalidate()
                        }
                    }
                }
                "text" -> {
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        val input = EditText(this@MainActivity)
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("输入文字")
                            .setView(input)
                            .setPositiveButton("确定") { _, _ ->
                                val text = input.text.toString()
                                if (text.isNotEmpty()) {
                                    operations.add(TextOp(text, bitmapX, bitmapY, SupportMenu.CATEGORY_MASK, 40.0f))
                                    invalidate()
                                }
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }
            }
            return true
        }
    }

    private fun showScreenshotViewer(file: File) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_screenshot_viewer, null)
        v.findViewById<Button?>(R.id.btnAnnotate)?.let { it.applyDarkTheme(primary = false) }
        v.findViewById<Button?>(R.id.btnUndo)?.let { it.applyDarkTheme(primary = false) }
        v.findViewById<Button?>(R.id.btnSavePng)?.let { it.applyDarkTheme(primary = true) }
        v.findViewById<Button?>(R.id.btnSendToPc)?.let { it.applyDarkTheme(primary = false) }
        v.findViewById<Button?>(R.id.btnClose)?.let { it.applyDarkTheme(primary = false) }
        v.findViewById<Button?>(R.id.toolBrush)?.let { it.applyDarkTheme(primary = false) }
        v.findViewById<Button?>(R.id.toolText)?.let { it.applyDarkTheme(primary = false) }
        v.findViewById<Button?>(R.id.toolArrow)?.let { it.applyDarkTheme(primary = false) }
        v.findViewById<Button?>(R.id.toolRect)?.let { it.applyDarkTheme(primary = false) }
        v.findViewById<Button?>(R.id.toolMosaic)?.let { it.applyDarkTheme(primary = false) }

        val img = v.findViewById<ImageView?>(R.id.screenshotImage)
        val originalBmp = BitmapFactory.decodeFile(file.absolutePath)
        if (originalBmp != null) {
            img?.setImageBitmap(originalBmp)
        }
        val overlayPlaceholder = v.findViewById<View?>(R.id.annotationOverlay)
        val toolbar = v.findViewById<LinearLayout?>(R.id.annotationToolbar)
        val parent = overlayPlaceholder?.parent as FrameLayout
        var annotationView: AnnotationOverlayView? = null
        var annotationMode = false
        val toolButtons = mapOf(
            "brush" to v.findViewById<Button?>(R.id.toolBrush),
            "text" to v.findViewById<Button?>(R.id.toolText),
            "arrow" to v.findViewById<Button?>(R.id.toolArrow),
            "rect" to v.findViewById<Button?>(R.id.toolRect),
            "mosaic" to v.findViewById<Button?>(R.id.toolMosaic)
        )

        fun selectTool(tool: String) {
            for ((name, btn) in toolButtons) {
                if (name == tool) {
                    btn?.setBackgroundColor(-12947515)
                } else {
                    btn?.setBackgroundResource(android.R.drawable.btn_default)
                }
            }
            annotationView?.currentTool = tool
        }

        fun setAnnotationMode(on: Boolean) {
            annotationMode = on
            toolbar?.visibility = if (on) View.VISIBLE else View.GONE
            if (on && annotationView == null && originalBmp != null) {
                parent.removeView(overlayPlaceholder)
                annotationView = AnnotationOverlayView(this, originalBmp).also {
                    it.visibility = View.VISIBLE
                    it.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                }
                parent.addView(annotationView)
                selectTool("brush")
            }
            annotationView?.visibility = if (on) View.VISIBLE else View.GONE
            v.findViewById<Button?>(R.id.btnAnnotate)?.text = if (on) "退出批注" else "批注"
        }

        v.findViewById<Button?>(R.id.btnAnnotate)?.setOnClickListener {
            setAnnotationMode(!annotationMode)
        }
        for ((tool, btn) in toolButtons) {
            btn?.setOnClickListener { selectTool(tool) }
        }
        v.findViewById<Button?>(R.id.btnUndo)?.setOnClickListener {
            if (annotationView?.hasOperations() == true) {
                annotationView?.undo()
            } else {
                Toast.makeText(this, "无可撤销操作", Toast.LENGTH_SHORT).show()
            }
        }
        v.findViewById<Button?>(R.id.btnSavePng)?.setOnClickListener {
            val bmpToSave = if (annotationView?.hasOperations() == true) annotationView!!.exportBitmap() else originalBmp
            if (bmpToSave != null) {
                try {
                    val saveName = if (annotationView?.hasOperations() == true) "${FilesKt.getNameWithoutExtension(file)}_annotated.png" else file.name
                    val saveDir = File(getExternalFilesDir(null), "Received")
                    val saveFile = File(saveDir, saveName)
                    FileOutputStream(saveFile).use { fos ->
                        bmpToSave.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                    Toast.makeText(this, "已保存: ${saveFile.absolutePath}", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        v.findViewById<Button?>(R.id.btnSendToPc)?.setOnClickListener {
            val tmpFile = if (annotationView?.hasOperations() == true) {
                try {
                    val tmp = File(getExternalFilesDir(null), "annotated_tmp.png")
                    FileOutputStream(tmp).use { fos ->
                        annotationView!!.exportBitmap().compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                    tmp
                } catch (_: Exception) {
                    file
                }
            } else file
            ConnectionManager.INSTANCE.sendFile(tmpFile)
            Toast.makeText(this, "已开始回传", Toast.LENGTH_SHORT).show()
        }
        val dialog = AlertDialog.Builder(this).setView(v).setCancelable(true).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        v.findViewById<Button?>(R.id.btnClose)?.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun getMirrorView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_screen_mirror, null)
        val btnMirrorToggle = v.findViewById<Button?>(R.id.btnMirrorToggle)
        btnMirrorToggle?.let { it.applyDarkTheme(primary = true) }
        v.findViewById<Button?>(R.id.btnAudio)?.let { it.applyDarkTheme(primary = false) }
        val fullscreenBtn = v.findViewById<Button?>(R.id.btnFullscreen)
        fullscreenBtn?.let { it.applyDarkTheme(primary = false) }
        val status = v.findViewById<TextView?>(R.id.mirrorStatus)
        val mirrorFrame = v.findViewById<FrameLayout?>(R.id.mirrorFrame)
        var mirrorRunning = false

        btnMirrorToggle?.setOnClickListener {
            if (!mirrorRunning) {
                status?.text = "正在推流（手机画面推送到电脑）..."
                ConnectionManager.INSTANCE.sendMediaCommand("mirror_start")
                startPhoneScreenCapture()
                btnMirrorToggle.text = "停止推流"
                mirrorRunning = true
            } else {
                status?.text = "已停止"
                ConnectionManager.INSTANCE.sendMediaCommand("mirror_stop")
                ConnectionManager.INSTANCE.sendMediaCommand("pc_stream_stop")
                stopPhoneScreenCapture()
                ConnectionManager.INSTANCE.stopPcFramePolling()
                mirrorImageView?.setImageBitmap(null)
                btnMirrorToggle.text = "启动推流"
                mirrorRunning = false
            }
        }

        val btnAudio = v.findViewById<Button?>(R.id.btnAudio)
        var audioRunning = false
        btnAudio?.setOnClickListener {
            if (!audioRunning) {
                ConnectionManager.INSTANCE.sendMediaCommand("audio_start")
                startPhoneAudioCapture()
                btnAudio.text = "停止声音"
                audioRunning = true
            } else {
                stopPhoneAudioCapture()
                ConnectionManager.INSTANCE.sendMediaCommand("audio_stop")
                btnAudio.text = "声音传输"
                audioRunning = false
            }
        }

        val titleText = v.findViewById<TextView?>(R.id.mirrorTitle)
        val topButtons = v.findViewById<LinearLayout?>(R.id.topButtons)
        val btnStopPcStream = v.findViewById<Button?>(R.id.btnStopPcStream)
        btnStopPcStream?.let { it.applyDarkTheme(primary = false) }
        val allUiComponents = listOf<View?>(titleText, topButtons, status, fullscreenBtn, btnStopPcStream)

        fullscreenBtn?.setOnClickListener {
            ConnectionManager.INSTANCE.sendMediaCommand("pc_stream_start")
            ConnectionManager.INSTANCE.startPcFramePolling(true)
            status?.text = "正在查看电脑画面..."
            mirrorFrame?.visibility = View.VISIBLE
            enterMirrorFullscreen(mirrorFrame, allUiComponents.filterNotNull())
        }

        v.findViewById<Button?>(R.id.btnMirrorFullscreenExit)?.setOnClickListener {
            exitMirrorFullscreen()
        }

        btnStopPcStream?.setOnClickListener {
            ConnectionManager.INSTANCE.sendMediaCommand("pc_stream_stop")
            ConnectionManager.INSTANCE.stopPcFramePolling()
            mirrorImageView?.setImageBitmap(null)
            mirrorFrame?.visibility = View.GONE
            status?.text = "已停止接收电脑画面"
        }

        val mirrorImg = ImageView(this)
        mirrorImg.scaleType = ImageView.ScaleType.FIT_CENTER
        mirrorImg.setBackgroundColor(ViewCompat.MEASURED_STATE_MASK)
        mirrorImg.minimumHeight = 400
        mirrorImageView = mirrorImg
        var touchStartTime = 0L
        var longPressDone = false
        var lastMoveSendTime = 0L
        val moveThrottleMs = 16L
        mirrorImg.setOnTouchListener { _, event ->
            if (ConnectionManager.INSTANCE.pcFrameControlMode != true) {
                return@setOnTouchListener false
            }
            val drawable = mirrorImg.drawable
            var normX = 0.5f
            var normY = 0.5f
            if (drawable != null && drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                val imgW = drawable.intrinsicWidth.toFloat()
                val imgH = drawable.intrinsicHeight.toFloat()
                val viewW = mirrorImg.width.toFloat()
                val viewH = mirrorImg.height.toFloat()
                val scale = Math.min(viewW / imgW, viewH / imgH)
                val realImgW = imgW * scale
                val realImgH = imgH * scale
                val offsetX = (viewW - realImgW) / 2.0f
                val offsetY = (viewH - realImgH) / 2.0f
                normX = RangesKt.coerceIn((event.x - offsetX) / realImgW, 0.0f, 1.0f)
                normY = RangesKt.coerceIn((event.y - offsetY) / realImgH, 0.0f, 1.0f)
            } else if (mirrorImg.width > 0 && mirrorImg.height > 0) {
                normX = RangesKt.coerceIn(event.x / mirrorImg.width, 0.0f, 1.0f)
                normY = RangesKt.coerceIn(event.y / mirrorImg.height, 0.0f, 1.0f)
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartTime = System.currentTimeMillis()
                    longPressDone = false
                    ConnectionManager.INSTANCE.sendAction("screen_click", mapOf("x" to normX, "y" to normY, "op" to "down"))
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!longPressDone) {
                        ConnectionManager.INSTANCE.sendAction("screen_click", mapOf("x" to normX, "y" to normY, "op" to "up"))
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!longPressDone) {
                        val now = System.currentTimeMillis()
                        if (now - touchStartTime > 2000) {
                            longPressDone = true
                            ConnectionManager.INSTANCE.sendAction("screen_click", mapOf("x" to normX, "y" to normY, "op" to "up"))
                            ConnectionManager.INSTANCE.sendAction("screen_click", mapOf("x" to normX, "y" to normY, "op" to "right"))
                        } else if (now - lastMoveSendTime >= moveThrottleMs) {
                            lastMoveSendTime = now
                            ConnectionManager.INSTANCE.sendAction("screen_click", mapOf("x" to normX, "y" to normY, "op" to "move"))
                        }
                    }
                    true
                }
                else -> true
            }
        }
        val parent2 = status?.parent as? ViewGroup
        val statusIndex = parent2?.indexOfChild(status) ?: -1
        if (parent2 != null && statusIndex >= 0) {
            parent2.addView(mirrorImg, statusIndex + 1)
        }
        return v
    }

    private fun getCameraView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_camera, null)
        val btnStart = v.findViewById<Button?>(R.id.btnCameraStart)
        val btnSwitch = v.findViewById<Button?>(R.id.btnCameraSwitch)
        val btnPcCam = v.findViewById<Button?>(R.id.btnCameraStop)
        btnStart?.let { it.applyDarkTheme(primary = false) }
        btnSwitch?.let { it.applyDarkTheme(primary = false) }
        btnPcCam?.let { it.applyDarkTheme(primary = false) }
        val status = v.findViewById<TextView?>(R.id.cameraStatus)
        val previewView = v.findViewById<PreviewView?>(R.id.cameraPreview)
        cameraPreviewView = previewView
        var isStreaming = false

        btnStart?.setOnClickListener {
            if (!isStreaming) {
                try {
                    status?.text = "正在启动摄像头..."
                    startCameraPreview(1920, 1080, previewView!!)
                    ConnectionManager.INSTANCE.sendMediaCommand("mirror_start")
                    status?.text = "推流中（本地预览 + 推送给电脑）"
                    btnStart.text = "停止推流"
                    isStreaming = true
                } catch (e: Exception) {
                    Toast.makeText(this, "无法启动摄像头: ${e.message}", Toast.LENGTH_LONG).show()
                    status?.text = "启动失败"
                }
            } else {
                stopCameraPreview()
                ConnectionManager.INSTANCE.sendMediaCommand("mirror_stop")
                status?.text = "已停止"
                btnStart.text = "启动推流"
                isStreaming = false
            }
        }

        btnSwitch?.setOnClickListener {
            performCameraSwitch(isStreaming)
        }

        btnPcCam?.setOnClickListener {
            if (ConnectionManager.INSTANCE.isPcCameraPolling()) {
                ConnectionManager.INSTANCE.stopPcCameraPolling()
                ConnectionManager.INSTANCE.sendMediaCommand("pc_camera_stop")
                btnPcCam.text = "查看电脑摄像头"
                cameraImageView?.setImageBitmap(null)
                cameraImageView?.visibility = View.GONE
                status?.text = if (isStreaming) "推流中（本地预览 + 推送给电脑）" else "已停止"
            } else {
                ConnectionManager.INSTANCE.sendMediaCommand("pc_camera_start")
                ConnectionManager.INSTANCE.startPcCameraPolling()
                btnPcCam.text = "停止拉取"
                cameraImageView?.visibility = View.VISIBLE
                status?.text = "正在拉取电脑摄像头..."
            }
        }

        val pcCameraImg = ImageView(this)
        pcCameraImg.scaleType = ImageView.ScaleType.FIT_CENTER
        pcCameraImg.setBackgroundColor(ViewCompat.MEASURED_STATE_MASK)
        pcCameraImg.minimumHeight = 400
        pcCameraImg.visibility = View.GONE
        cameraImageView = pcCameraImg
        val parent2 = status?.parent as? ViewGroup
        val statusIndex = parent2?.indexOfChild(status) ?: -1
        if (parent2 != null && statusIndex >= 0) {
            parent2.addView(pcCameraImg, statusIndex + 1)
        }
        return v
    }

    fun startCameraPreview(width: Int, height: Int, previewView: PreviewView) {
        if (cameraPreviewRunning) {
            stopCameraPreview()
        }
        val futureProvider = ProcessCameraProvider.getInstance(this)
        futureProvider.addListener({
            cameraProvider = futureProvider.get()
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(width, height), 1))
                .build()
            val preview = Preview.Builder().setResolutionSelector(resolutionSelector).build()
            val imageAnalyzer = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setOutputImageFormat(2)
                .setBackpressureStrategy(0)
                .build()
            cameraExecutor = Executors.newSingleThreadExecutor()
            imageAnalyzer.setAnalyzer(cameraExecutor!!, MainActivity_startCameraPreview$analyze_1(this))
            val cameraSelector = CameraSelector.Builder().requireLensFacing(cameraLensFacing).build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            cameraProvider?.let {
                cameraInstance = it.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            }
            cameraPreviewRunning = true
        }, ContextCompat.getMainExecutor(this))
    }

    fun stopCameraPreview() {
        cameraPreviewRunning = false
        cameraProvider?.unbindAll()
        cameraProvider = null
        cameraInstance = null
        cameraExecutor?.shutdown()
        cameraExecutor = null
        frameTimeoutHandler.removeCallbacksAndMessages(null)
        mirrorFrameTimeoutRunnable = null
        cameraFrameTimeoutRunnable = null
    }

    private fun switchCameraLens() {
        cameraLensFacing = if (cameraLensFacing == 1) 0 else 1
    }

    fun performCameraSwitch(isStreaming: Boolean) {
        switchCameraLens()
        if (cameraPreviewRunning) {
            stopCameraPreview()
            cameraPreviewView?.let { startCameraPreview(1920, 1080, it) }
        }
        val facing = if (cameraLensFacing == 1) "back" else "front"
        ConnectionManager.INSTANCE.sendCameraSwitch(facing)
        Toast.makeText(this, "Switched to $facing camera", Toast.LENGTH_SHORT).show()
    }

    fun notifKey(item: ConnectionManager.NotificationItem): String {
        var key = item.key
        if (key.isEmpty()) {
            key = "${item.packageName}|${item.sbnTag}|${item.sbnId}"
        }
        return key
    }

    fun showNotificationBlacklistDialog() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0).filter { it.packageName != "com.phonehub" }
            .sortedBy { it.loadLabel(pm).toString().lowercase(Locale.ROOT) }
        val blacklist = NotificationListener.getBlacklist(this).toMutableSet()
        val appNames = apps.map { it.loadLabel(pm).toString() }
        val pkgNames = apps.map { it.packageName }
        val checked = pkgNames.map { it in blacklist }.toBooleanArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle("选择不转发的应用")
            .setMultiChoiceItems(appNames.toTypedArray(), checked) { _, which, isChecked ->
                if (isChecked) blacklist.add(pkgNames[which]) else blacklist.remove(pkgNames[which])
            }
            .setPositiveButton("保存") { _, _ ->
                NotificationListener.setBlacklist(this, blacklist)
                Toast.makeText(this, "黑名单已更新", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(-13816531))
        dialog.listView?.setBackgroundColor(-13816531)
        dialog.listView?.divider = ColorDrawable(-12566464)
        dialog.listView?.dividerHeight = 1
        val count = dialog.listView.count
        for (i in 0 until count) {
            (dialog.listView.getChildAt(i) as? CheckedTextView)?.setTextColor(-1)
        }
        dialog.listView.post {
            val c = dialog.listView.count
            for (i in 0 until c) {
                (dialog.listView.getChildAt(i) as? CheckedTextView)?.setTextColor(-1)
            }
        }
    }

    private fun getNotificationsView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_notifications, null)
        v.findViewById<Button?>(R.id.btnNotifWhitelist)?.let { it.applyDarkTheme(primary = false) }
        v.findViewById<Button?>(R.id.btnNotifPermission)?.let { it.applyDarkTheme(primary = false) }
        v.findViewById<Button?>(R.id.btnNotifRefresh)?.let { it.applyDarkTheme(primary = false) }
        v.findViewById<Button?>(R.id.btnNotifActiveTab)?.let { it.applyDarkTheme(primary = false) }
        v.findViewById<Button?>(R.id.btnNotifHistoryTab)?.let { it.applyDarkTheme(primary = false) }
        val activeList = v.findViewById<ListView?>(R.id.notifActiveList)
        val historyList = v.findViewById<ListView?>(R.id.notifHistoryList)
        val empty = v.findViewById<TextView?>(R.id.notifEmpty)
        val filterEdit = v.findViewById<EditText?>(R.id.notifFilter)
        val permissionBar = v.findViewById<LinearLayout?>(R.id.notifPermissionBar)
        val permissionText = v.findViewById<TextView?>(R.id.notifPermissionText)
        val btnPermission = v.findViewById<Button?>(R.id.btnNotifPermission)
        val btnRefresh = v.findViewById<Button?>(R.id.btnNotifRefresh)
        val btnActiveTab = v.findViewById<Button?>(R.id.btnNotifActiveTab)
        val btnHistoryTab = v.findViewById<Button?>(R.id.btnNotifHistoryTab)
        var currentTab = "active"

        fun checkPermissionAndTrigger() {
            val enabled = ConnectionManager.INSTANCE.isNotificationListenerEnabled()
            if (enabled) {
                if (NotificationListener.getInstance() == null) {
                    permissionBar?.visibility = View.VISIBLE
                    permissionText?.text = "权限已开启但服务未连接，请关闭再重新开启「PhoneHub」开关"
                    btnPermission?.text = "去重新开启"
                } else {
                    permissionBar?.visibility = View.GONE
                }
            } else {
                permissionBar?.visibility = View.VISIBLE
                permissionText?.text = "通知监听权限未开启，无法获取通知"
                btnPermission?.text = "去开启"
            }
        }

        fun refresh() {
            val q = filterEdit?.text?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
            val sourceList = if (currentTab == "active") activeNotifItems else notifHistoryItems
            val filtered = if (q.isEmpty()) sourceList else sourceList.filter {
                it.packageName.lowercase(Locale.ROOT).contains(q) ||
                it.title.lowercase(Locale.ROOT).contains(q) ||
                it.text.lowercase(Locale.ROOT).contains(q)
            }
            val targetList = if (currentTab == "active") activeList else historyList
            if (filtered.isEmpty()) {
                empty?.visibility = View.VISIBLE
                empty?.text = if (sourceList.isEmpty()) "暂无通知" else "无匹配结果"
                targetList?.adapter = null
                return
            }
            empty?.visibility = View.GONE
            val displays = filtered.map {
                "${it.title}\n${it.text}\n[${it.packageName}] ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.timestamp))}"
            }
            targetList?.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displays)
        }

        v.findViewById<Button?>(R.id.btnNotifWhitelist)?.setOnClickListener {
            showNotificationBlacklistDialog()
        }
        btnActiveTab?.setOnClickListener {
            currentTab = "active"
            activeList?.visibility = View.VISIBLE
            historyList?.visibility = View.GONE
            btnActiveTab.setTextColor(-1)
            btnHistoryTab?.setTextColor(-7829368)
            refresh()
        }
        btnHistoryTab?.setOnClickListener {
            currentTab = "history"
            activeList?.visibility = View.GONE
            historyList?.visibility = View.VISIBLE
            btnHistoryTab?.setTextColor(-1)
            btnActiveTab?.setTextColor(-7829368)
            refresh()
        }
        btnActiveTab?.callOnClick()
        btnPermission?.setOnClickListener {
            try {
                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
            }
        }
        btnRefresh?.setOnClickListener {
            activeNotifItems.clear()
            checkPermissionAndTrigger()
            NotificationListener.getInstance()?.reportAllActiveNotifications()
            refresh()
            val enabled = ConnectionManager.INSTANCE.isNotificationListenerEnabled()
            val connected = NotificationListener.getInstance() != null
            if (enabled) {
                if (connected) {
                    Toast.makeText(this, "已刷新", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "服务未连接，请关闭再重新开启权限开关", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "通知权限未开启，请先开启", Toast.LENGTH_SHORT).show()
            }
        }
        filterEdit?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refresh() }
            override fun afterTextChanged(s: Editable?) {}
        })
        activeList?.post {
            checkPermissionAndTrigger()
            refresh()
        }
        BuildersKt.launch$default(
            CoroutineScopeKt.CoroutineScope(Dispatchers.Main),
            null, null,
            MainActivity_getNotificationsView_8(this, filterEdit, null, null, null, null, null),
            3, null
        )
        return v
    }

    private fun getFileManagerView(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(-14803426)
        root.setPadding(dp(16), dp(16), dp(16), dp(16))
        val titleTv = TextView(this)
        titleTv.text = "远程文件管理"
        titleTv.textColor = -1
        titleTv.textSize = 20.0f
        titleTv.setTypeface(titleTv.typeface, Typeface.BOLD)
        titleTv.setPadding(0, 0, 0, dp(12))
        root.addView(titleTv)

        val tabRow = LinearLayout(this)
        tabRow.orientation = LinearLayout.HORIZONTAL
        tabRow.setBackgroundColor(-13816531)
        tabRow.setPadding(dp(4), dp(4), dp(4), dp(4))
        val btnPhone = Button(this)
        btnPhone.text = "手机文件"
        btnPhone.applyDarkTheme(primary = true)
        val btnPc = Button(this)
        btnPc.text = "电脑文件"
        btnPc.applyDarkTheme(primary = false)
        val btnPhoneLp = LinearLayout.LayoutParams(0, dp(44), 1.0f)
        btnPhoneLp.rightMargin = dp(4)
        tabRow.addView(btnPhone, btnPhoneLp)
        tabRow.addView(btnPc, LinearLayout.LayoutParams(0, dp(44), 1.0f))
        val tabLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        tabLp.bottomMargin = dp(8)
        root.addView(tabRow, tabLp)

        val navBar = LinearLayout(this)
        navBar.orientation = LinearLayout.HORIZONTAL
        navBar.setBackgroundColor(-13816531)
        navBar.setPadding(dp(8), dp(8), dp(8), dp(8))
        val btnUp = Button(this)
        btnUp.text = "返回上级"
        btnUp.applyDarkTheme(primary = false)
        val btnRefresh = Button(this)
        btnRefresh.text = "刷新"
        btnRefresh.applyDarkTheme(primary = true)
        val pathTv = TextView(this)
        pathTv.id = R.id.fmPath
        pathTv.textColor = -5197648
        pathTv.textSize = 12.0f
        pathTv.setPadding(dp(8), 0, dp(8), 0)
        pathTv.gravity = 16
        navBar.addView(btnUp, LinearLayout.LayoutParams(dp(60), dp(40)))
        navBar.addView(btnRefresh, LinearLayout.LayoutParams(dp(60), dp(40)))
        navBar.addView(pathTv, LinearLayout.LayoutParams(0, dp(40), 1.0f))
        val navLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        navLp.bottomMargin = dp(8)
        root.addView(navBar, navLp)

        val list = ListView(this)
        val empty = TextView(this)
        empty.id = R.id.fmEmpty
        empty.text = "暂无内容"
        empty.textColor = -10066330
        empty.textSize = 13.0f
        empty.gravity = 17
        empty.visibility = View.GONE
        val listLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0)
        listLp.weight = 1.0f
        root.addView(list, listLp)
        root.addView(empty)

        var phoneCurPath: String = Environment.getExternalStorageDirectory()?.absolutePath ?: "/"

        fun refreshPhoneFiles() {
            pathTv.text = phoneCurPath
            val dir = File(phoneCurPath)
            val files = (dir.listFiles()?.toList() ?: emptyList())
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
            if (files.isEmpty()) {
                empty.visibility = View.VISIBLE
                empty.text = "空目录"
                list.adapter = null
                return
            }
            empty.visibility = View.GONE
            val displays = files.map { f ->
                val icon = if (f.isDirectory) "📁 " else fileIcon(f.name)
                val sz = if (f.isDirectory) "[目录]" else formatSize(f.length())
                "$icon${f.name}\n$sz"
            }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displays)
            list.setOnItemClickListener { _, _, pos, _ ->
                val f = files[pos]
                if (f.isDirectory) {
                    phoneCurPath = f.absolutePath
                    refreshPhoneFiles()
                } else {
                    ConnectionManager.INSTANCE.sendFile(f)
                    Toast.makeText(this, "开始发送: ${f.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun refreshPcFiles() {
            pcInDrives = false
            pathTv.text = pcCurPath
            empty.visibility = View.VISIBLE
            empty.text = "正在加载..."
            list.adapter = null
            ConnectionManager.INSTANCE.fetchPcFiles(pcCurPath) { files, path ->
                val sorted = files.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase(Locale.ROOT) }))
                if (sorted.isEmpty()) {
                    empty.visibility = View.VISIBLE
                    empty.text = "空目录"
                    list.adapter = null
                } else {
                    empty.visibility = View.GONE
                    val displays = sorted.map { f ->
                        val icon = if (f.isDir) "📁 " else fileIcon(f.name)
                        val sz = if (f.isDir) "[目录]" else formatSize(f.size)
                        val name = if (f.isDir) "${f.name}/" else f.name
                        "$icon$name\n$sz"
                    }
                    list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displays)
                    list.setOnItemClickListener { _, _, pos, _ ->
                        val f = sorted[pos]
                        if (f.isDir) {
                            pcCurPath = pcCurPath.trimEnd('\\') + "\\" + f.name
                            refreshPcFiles()
                        } else {
                            val filePath = pcCurPath.trimEnd('\\') + "\\" + f.name
                            downloadPcFile(filePath, f.name)
                        }
                    }
                }
                Unit
            }
        }

        fun refreshPcDrives() {
            pcInDrives = true
            pathTv.text = "我的电脑"
            empty.visibility = View.VISIBLE
            empty.text = "正在加载磁盘列表..."
            list.adapter = null
            ConnectionManager.INSTANCE.fetchPcDrives { drives ->
                if (drives.isEmpty()) {
                    empty.visibility = View.VISIBLE
                    empty.text = "未获取到磁盘信息"
                    list.adapter = null
                } else {
                    empty.visibility = View.GONE
                    val displays = drives.map {
                        val totalGb = it.total / 1.073741824E9
                        val freeGb = it.free / 1.073741824E9
                        String.format("${it.name}  [磁盘]\n总计: %.1f GB  可用: %.1f GB", totalGb, freeGb)
                    }
                    list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displays)
                    list.setOnItemClickListener { _, _, pos, _ ->
                        pcCurPath = drives[pos].name
                        pcInDrives = false
                        refreshPcFiles()
                    }
                }
                Unit
            }
        }

        btnPhone.setOnClickListener {
            fmMode = "phone"
            btnPhone.applyDarkTheme(primary = true)
            btnPc.applyDarkTheme(primary = false)
            refreshPhoneFiles()
        }
        btnPc.setOnClickListener {
            fmMode = "pc"
            btnPc.applyDarkTheme(primary = true)
            btnPhone.applyDarkTheme(primary = false)
            refreshPcDrives()
        }
        btnUp.setOnClickListener {
            if (fmMode == "phone") {
                val cur = File(phoneCurPath)
                val sdRoot = Environment.getExternalStorageDirectory()?.absolutePath ?: "/sdcard"
                if (cur.absolutePath == sdRoot || cur.absolutePath == "/") return@setOnClickListener
                val parent = cur.parentFile
                if (parent != null && parent.canRead()) {
                    phoneCurPath = parent.absolutePath
                    refreshPhoneFiles()
                } else if (parent != null) {
                    phoneCurPath = parent.absolutePath
                    refreshPhoneFiles()
                }
            } else {
                if (pcInDrives) return@setOnClickListener
                val trimmed = pcCurPath.trimEnd('\\', '/')
                if (trimmed.length <= 3 && trimmed.isNotEmpty() && trimmed[1] == ':') {
                    refreshPcDrives()
                    return@setOnClickListener
                }
                val lastSlash = trimmed.lastIndexOf('\\')
                val parent = when {
                    lastSlash > 2 -> trimmed.substring(0, lastSlash)
                    lastSlash == 2 -> trimmed.substring(0, 3)
                    else -> null
                }
                if (parent != null) {
                    pcCurPath = parent
                    refreshPcFiles()
                } else {
                    refreshPcDrives()
                }
            }
        }
        btnRefresh.setOnClickListener {
            if (fmMode == "phone") refreshPhoneFiles()
            else if (pcInDrives) refreshPcDrives()
            else refreshPcFiles()
        }
        list.post { refreshPhoneFiles() }
        return root
    }

    fun fileIcon(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "7z" -> "📦 "
            "md" -> "📝 "
            "aac", "flac", "mp3", "ogg", "wav" -> "🎵 "
            "apk" -> "📦 "
            "avi", "mkv", "mov", "mp4" -> "🎬 "
            "bmp", "gif", "jpeg", "jpg", "png", "webp" -> "🖼 "
            "doc", "docx", "pdf", "txt" -> "📄 "
            "log" -> "📝 "
            "rar", "zip" -> "📦 "
            "xls", "xlsx" -> "📊 "
            else -> "📎 "
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes >= 1073741824L) return String.format("%.1f GB", bytes / 1.073741824E9)
        if (bytes >= 1048576L) return String.format("%.1f MB", bytes / 1048576.0)
        if (bytes >= 1024L) return String.format("%d KB", bytes / 1024)
        return "$bytes B"
    }

    fun downloadPcFile(filePath: String, fileName: String) {
        Thread {
            try {
                val baseUrl = ConnectionManager.INSTANCE.getBaseUrlPublic()
                val url = URL("$baseUrl/api/pc_file_download")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer ${ConnectionManager.INSTANCE.secretToken}")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 60000
                val jsonBody = "{\"path\":\"${filePath.replace("\\", "\\\\")}\"}"
                conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode == 200) {
                    val resolver = contentResolver
                    val values = ContentValues()
                    values.put("_display_name", fileName)
                    values.put("mime_type", "application/octet-stream")
                    values.put("relative_path", Environment.DIRECTORY_DOWNLOADS)
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri == null) {
                        runOnUiThread { Toast.makeText(this, "下载失败: 无法创建文件", Toast.LENGTH_SHORT).show() }
                    } else {
                        resolver.openOutputStream(uri)?.use { output ->
                            conn.inputStream.use { input ->
                                ByteStreamsKt.copyTo(input, output)
                            }
                        }
                        runOnUiThread { Toast.makeText(this, "已下载到 Download: $fileName", Toast.LENGTH_SHORT).show() }
                    }
                } else {
                    val errorMsg = when (conn.responseCode) {
                        403 -> "无权限下载（系统保护文件或文件被锁定）"
                        404 -> "文件不存在"
                        else -> "HTTP ${conn.responseCode}"
                    }
                    runOnUiThread { Toast.makeText(this, "下载失败: $errorMsg", Toast.LENGTH_SHORT).show() }
                }
                conn.disconnect()
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun getApkInstallView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_apk_install, null)
        v.findViewById<Button?>(R.id.btnApkPick)?.let { it.applyDarkTheme(primary = true) }
        v.findViewById<Button?>(R.id.btnApkInstallLast)?.let { it.applyDarkTheme(primary = false) }
        val list = v.findViewById<ListView?>(R.id.apkList) ?: return v
        val empty = v.findViewById<TextView?>(R.id.apkEmpty) ?: return v
        v.findViewById<Button?>(R.id.btnApkPick)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "application/vnd.android.package-archive"
            startActivityForResult(Intent.createChooser(intent, "选择 APK"), SELECT_APK_CODE)
        }
        v.findViewById<Button?>(R.id.btnApkInstallLast)?.setOnClickListener {
            val dir = File(getExternalFilesDir(null), "Received")
            val apks = dir.listFiles { f -> f.name.endsWith(".apk") }?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
            val apk = apks.firstOrNull()
            if (apk != null) {
                installApk(apk)
            } else {
                Toast.makeText(this, "暂无接收的 APK", Toast.LENGTH_SHORT).show()
            }
        }
        val dir = File(getExternalFilesDir(null), "Received")
        val apks = (dir.listFiles { f -> f.name.endsWith(".apk") }?.toList() ?: emptyList())
            .sortedByDescending { it.lastModified() }
        if (apks.isEmpty()) {
            empty.visibility = View.VISIBLE
            list.adapter = null
        } else {
            empty.visibility = View.GONE
            val names = apks.map { "${it.name}\n${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it.lastModified()))}" }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
            list.setOnItemClickListener { _, _, pos, _ ->
                installApk(apks[pos])
            }
        }
        return v
    }

    private fun installApk(file: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法安装 APK: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getAppManagerView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_app_manager, null)
        v.findViewById<Button?>(R.id.btnAppRefresh)?.let { it.applyDarkTheme(primary = true) }
        val list = v.findViewById<ListView?>(R.id.appList) ?: return v
        val empty = v.findViewById<TextView?>(R.id.appEmpty) ?: return v
        val filter = v.findViewById<EditText?>(R.id.appFilter) ?: return v

        fun refresh() {
            val pm = packageManager
            val q = filter.text?.toString()?.lowercase(Locale.ROOT) ?: ""
            val infos = pm.getInstalledApplications(0)
                .sortedBy { pm.getApplicationLabel(it).toString().lowercase(Locale.ROOT) }
                .filter { q.isEmpty() || pm.getApplicationLabel(it).toString().lowercase(Locale.ROOT).contains(q) || it.packageName.lowercase(Locale.ROOT).contains(q) }
            if (infos.isEmpty()) {
                empty.visibility = View.VISIBLE
                list.adapter = null
                return
            }
            empty.visibility = View.GONE
            val displays = infos.map {
                val label = pm.getApplicationLabel(it).toString()
                val sys = if ((it.flags and ApplicationInfo.FLAG_SYSTEM) != 0) "[系统]" else ""
                "$label $sys\n${it.packageName}"
            }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displays)
            list.setOnItemLongClickListener { _, _, pos, _ ->
                val pkg = infos[pos].packageName
                val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
                startActivity(intent)
                true
            }
        }

        v.findViewById<Button?>(R.id.btnAppRefresh)?.setOnClickListener { refresh() }
        filter.setOnEditorActionListener { _, _, _ -> refresh(); true }
        list.post { refresh() }
        return v
    }

    private fun getPowerView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_power, null)
        v.findViewById<Button?>(R.id.btnPowerShutdown)?.let { it.applyDarkTheme(primary = true) }
        v.findViewById<Button?>(R.id.btnPowerReboot)?.let { it.applyDarkTheme(primary = true) }
        v.findViewById<Button?>(R.id.btnPowerHibernate)?.let { it.applyDarkTheme(primary = false) }
        v.findViewById<Button?>(R.id.btnPowerLock)?.let { it.applyDarkTheme(primary = false) }
        v.findViewById<Button?>(R.id.btnPowerCancel)?.let { it.applyDarkTheme(primary = false) }
        v.findViewById<Button?>(R.id.btnPowerShutdown)?.setOnClickListener { showPowerCountdown("关机", "shutdown") }
        v.findViewById<Button?>(R.id.btnPowerReboot)?.setOnClickListener { showPowerCountdown("重启", "reboot") }
        v.findViewById<Button?>(R.id.btnPowerHibernate)?.setOnClickListener {
            ConnectionManager.INSTANCE.sendPowerCommand("hibernate", 0L)
            Toast.makeText(this, "已发送休眠指令", Toast.LENGTH_SHORT).show()
        }
        v.findViewById<Button?>(R.id.btnPowerLock)?.setOnClickListener {
            ConnectionManager.INSTANCE.sendPowerCommand("lock", 0L)
            Toast.makeText(this, "已发送锁定指令", Toast.LENGTH_SHORT).show()
        }
        v.findViewById<Button?>(R.id.btnPowerCancel)?.setOnClickListener {
            ConnectionManager.INSTANCE.sendPowerCommand("cancel", 0L)
            Toast.makeText(this, "已发送取消指令", Toast.LENGTH_SHORT).show()
        }
        return v
    }

    fun showPowerCountdown(label: String, cmd: String) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_power_countdown, null)
        v.findViewById<Button?>(R.id.btnPowerCancel)?.let { it.applyDarkTheme(primary = true) }
        val title = v.findViewById<TextView?>(R.id.powerTitle)
        val countdown = v.findViewById<TextView?>(R.id.powerCountdownText)
        val progress = v.findViewById<ProgressBar?>(R.id.powerProgressBar)
        title?.text = "电脑即将$label"
        progress?.max = 30
        progress?.progress = 30
        val dialog = AlertDialog.Builder(this).setView(v).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        ConnectionManager.INSTANCE.sendPowerCommand(cmd, 30000L)
        val timer = object : CountDownTimer(30000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = (millisUntilFinished / 1000).toInt()
                countdown?.text = sec.toString()
                progress?.progress = sec
            }
            override fun onFinish() {
                countdown?.text = "0"
                dialog.dismiss()
                Toast.makeText(this@MainActivity, "$label 指令已发送", Toast.LENGTH_SHORT).show()
            }
        }.start()
        v.findViewById<Button?>(R.id.btnPowerCancel)?.setOnClickListener {
            timer.cancel()
            ConnectionManager.INSTANCE.sendPowerCommand("cancel", 0L)
            dialog.dismiss()
            Toast.makeText(this, "已取消$label", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getPushWebView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_push_web, null)
        v.findViewById<Button?>(R.id.btnPushUrlPc)?.let { it.applyDarkTheme(primary = true) }
        val urlInput = v.findViewById<EditText?>(R.id.urlInput) ?: return v
        val list = v.findViewById<ListView?>(R.id.urlHistoryList) ?: return v
        if (urlHistory.isEmpty()) {
            loadUrlHistory()
        }
        list.post { refreshUrlHistoryList(list, urlInput) }
        v.findViewById<Button?>(R.id.btnPushUrlPc)?.setOnClickListener {
            var url = urlInput.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "请输入 URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            ConnectionManager.INSTANCE.pushUrlToPc(url, false)
            addUrlHistory(url, "电脑 <- 手机")
            refreshUrlHistoryList(list, urlInput)
            Toast.makeText(this, "已发送到电脑", Toast.LENGTH_SHORT).show()
        }
        BuildersKt.launch$default(
            CoroutineScopeKt.CoroutineScope(Dispatchers.Main),
            null, null,
            MainActivity_getPushWebView_3(this, null),
            3, null
        )
        BuildersKt.launch$default(
            CoroutineScopeKt.CoroutineScope(Dispatchers.Main),
            null, null,
            MainActivity_getPushWebView_4(this, null),
            3, null
        )
        return v
    }

    private fun refreshUrlHistoryList(list: ListView, urlInput: EditText) {
        if (urlHistory.isEmpty()) {
            list.adapter = null
            return
        }
        val displays = urlHistory.map {
            val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.timestamp))
            "[${it.direction}] ${it.url}\n$time"
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displays)
        list.setOnItemClickListener { _, _, pos, _ ->
            urlInput.setText(urlHistory[pos].url)
        }
    }

    fun refreshUrlHistoryList() {
        val cv = pageCache[15] ?: return
        val lv = cv.findViewById<ListView?>(R.id.urlHistoryList) ?: return
        if (urlHistory.isEmpty()) {
            lv.adapter = null
            return
        }
        val displays = urlHistory.map {
            val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.timestamp))
            "[${it.direction}] ${it.url}\n$time"
        }
        lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displays)
    }

    private fun getSettingsView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_settings, null)
        v.findViewById<Button?>(R.id.disconnectBtn2)?.let { it.applyDarkTheme(primary = false) }
        v.findViewById<Button?>(R.id.disconnectBtn2)?.setOnClickListener {
            ConnectionManager.INSTANCE.disconnect()
            updateSetupVisibility()
        }
        val state = ConnectionManager.INSTANCE.connectionState.value
        val infoStatus = v.findViewById<TextView?>(R.id.infoStatus)
        val infoChannel = v.findViewById<TextView?>(R.id.infoChannel)
        val infoIp = v.findViewById<TextView?>(R.id.infoIp)
        when (state) {
            ConnectionManager.ConnectionState.CONNECTED -> {
                infoStatus?.text = "状态: 已连接"
                infoStatus?.setTextColor(-15696880)
            }
            ConnectionManager.ConnectionState.CONNECTING -> {
                infoStatus?.text = "状态: 连接中..."
                infoStatus?.setTextColor(-18176)
            }
            ConnectionManager.ConnectionState.DISCONNECTED -> {
                infoStatus?.text = "状态: 未连接"
                infoStatus?.setTextColor(-3066824)
            }
        }
        val channel = ConnectionManager.INSTANCE.currentChannel.value
        val channelName = when (channel) {
            ConnectionManager.ChannelType.WIFI -> "WiFi 直连"
            ConnectionManager.ChannelType.ADB -> "USB 数据线"
            else -> "无"
        }
        infoChannel?.text = "通道: $channelName"
        infoIp?.text = "IP: ${ConnectionManager.INSTANCE.pcIp ?: "未知"}"
        return v
    }

    fun showSendTextDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_send_text, null)
        val filenameInput = dialogView.findViewById<EditText?>(R.id.filenameInput)
        val textContentInput = dialogView.findViewById<EditText?>(R.id.textContentInput)
        val cancelBtn = dialogView.findViewById<Button?>(R.id.cancelTextBtn)
        val sendBtn = dialogView.findViewById<Button?>(R.id.sendTextBtn)
        cancelBtn?.let { it.applyDarkTheme(primary = false) }
        sendBtn?.let { it.applyDarkTheme(primary = true) }
        val dialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(true).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        cancelBtn?.setOnClickListener { dialog.dismiss() }
        sendBtn?.setOnClickListener {
            val text = textContentInput?.text?.toString() ?: ""
            var name = filenameInput?.text?.toString() ?: ""
            if (name.isBlank()) name = ""
            if (text.isNotEmpty()) {
                ConnectionManager.INSTANCE.sendText(text, if (name.isBlank()) null else name)
                Toast.makeText(this, "已发送", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun attemptConnect() {
        val ip = ipInput?.text?.toString()?.trim() ?: ""
        if (ip.isEmpty()) {
            Toast.makeText(this, "请输入IP地址", Toast.LENGTH_SHORT).show()
            return
        }
        val portStr = portInput?.text?.toString()?.trim() ?: ""
        var port = 58627
        if (portStr.isNotEmpty()) {
            portStr.toIntOrNull()?.let { port = it }
        }
        var token = tokenInput?.text?.toString()?.trim() ?: ""
        if (token.isEmpty()) {
            token = "541881452418845"
        }
        val prefs = getSharedPreferences("phonehub_prefs", 0)
        prefs.edit().putInt("cached_port", port).putString("cached_token", token).apply()
        connectBtn?.isEnabled = false
        connectStatus?.text = "正在连接..."
        ConnectionManager.INSTANCE.connect(ip, port, token)
    }

    private fun setupFlows() {
        BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.Main), null, null, MainActivity_setupFlows_1(this, null), 3, null)
        BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.Main), null, null, MainActivity_setupFlows_2(this, null), 3, null)
        BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.Main), null, null, MainActivity_setupFlows_3(this, null), 3, null)
        BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.Main), null, null, MainActivity_setupFlows_4(this, null), 3, null)
        BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.Main), null, null, MainActivity_setupFlows_5(this, null), 3, null)
        BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.Main), null, null, MainActivity_setupFlows_6(null), 3, null)
        BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.Main), null, null, MainActivity_setupFlows_7(this, null), 3, null)
        BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.Main), null, null, MainActivity_setupFlows_8(this, null), 3, null)
        BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.Main), null, null, MainActivity_setupFlows_9(this, null), 3, null)
        BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.Main), null, null, MainActivity_setupFlows_10(this, null), 3, null)
        BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.Main), null, null, MainActivity_setupFlows_11(this, null), 3, null)
        BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.Main), null, null, MainActivity_setupFlows_12(this, null), 3, null)
        BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.Main), null, null, MainActivity_setupFlows_13(this, null), 3, null)
        BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.Main), null, null, MainActivity_setupFlows_14(this, null), 3, null)
        BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.IO), null, null, MainActivity_setupFlows_15(this, null), 3, null)
        BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.IO), null, null, MainActivity_setupFlows_16(this, null), 3, null)
    }

    fun showReceivedTextDialog(filename: String, textContent: String) {
        if (isFinishing()) return
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
            setBackgroundColor(-14803426)
        }
        val title = TextView(this).apply {
            text = "收到文字: $filename"
            setTextColor(-1)
            textSize = 16.0f
            setPadding(0, 0, 0, 24)
        }
        val content = TextView(this).apply {
            text = textContent
            setTextColor(-1)
            textSize = 14.0f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(-13816531)
            minLines = 3
        }
        val copyBtn = Button(this).apply {
            text = "复制"
            this.applyDarkTheme(primary = false)
        }
        val saveBtn = Button(this).apply {
            text = "保存"
            this.applyDarkTheme(primary = true)
        }
        val closeBtn = Button(this).apply {
            text = "关闭"
            this.applyDarkTheme(primary = false)
        }
        v.addView(title)
        v.addView(content)
        v.addView(copyBtn)
        v.addView(saveBtn)
        v.addView(closeBtn)
        val dialog = AlertDialog.Builder(this).setView(v).setCancelable(true).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnDismissListener {
            val key = "$filename|$textContent"
            handledTextContents[key] = System.currentTimeMillis()
        }
        dialog.show()
        closeBtn.setOnClickListener {
            val key = "$filename|$textContent"
            handledTextContents[key] = System.currentTimeMillis()
            dialog.dismiss()
        }
        copyBtn.setOnClickListener {
            try {
                val cm = getSystemService("clipboard") as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("PhoneHub", textContent))
                Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                val key = "$filename|$textContent"
                handledTextContents[key] = System.currentTimeMillis()
            } catch (e: Exception) {
                Toast.makeText(this, "复制失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        saveBtn.setOnClickListener {
            val hasExtension = filename.contains('.') && !filename.endsWith('.') &&
                filename.substringAfterLast('.', "").isNotEmpty()
            if (hasExtension) {
                pendingSaveText = textContent
                saveTextLauncher.launch(filename)
            } else {
                val extInput = EditText(this).apply {
                    hint = "文件后缀（留空为 txt）"
                    setTextColor(-1)
                    setHintTextColor(-10066330)
                    inputType = 1
                }
                AlertDialog.Builder(this)
                    .setTitle("输入文件后缀")
                    .setView(extInput)
                    .setPositiveButton("下一步") { _, _ ->
                        var ext = extInput.text.toString().trim().trimStart('.')
                        if (ext.isEmpty()) ext = "txt"
                        pendingSaveText = textContent
                        val baseName = if (filename.isBlank()) "received_${System.currentTimeMillis()}" else filename.substringBeforeLast('.', filename)
                        saveTextLauncher.launch("$baseName.$ext")
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    fun updateSetupVisibility() {
        Handler(Looper.getMainLooper()).post {
            val isConnected = ConnectionManager.INSTANCE.connectionState.value == ConnectionManager.ConnectionState.CONNECTED
            if (isConnected || ConnectionManager.INSTANCE.hasReceivedPcCpu()) {
                setupScreen?.visibility = View.GONE
                mainContainer?.visibility = View.VISIBLE
                if (pageContainer?.childCount == 0) {
                    switchTab(0, false)
                }
            } else {
                setupScreen?.visibility = View.VISIBLE
                mainContainer?.visibility = View.GONE
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTextNotificationIntent(intent)
        handleFileTransferNotificationIntent(intent)
    }

    private fun handleTextNotificationIntent(intent: Intent?) {
        if (intent != null && intent.getBooleanExtra("show_text_dialog", false)) {
            ConnectionManager.INSTANCE.lastReceivedText?.let { last ->
                showReceivedTextDialog(last.first, last.second)
            }
            intent.removeExtra("show_text_dialog")
        }
    }

    private fun handleFileTransferNotificationIntent(intent: Intent?) {
        if (intent != null && intent.getBooleanExtra("show_file_transfer", false)) {
            switchTab(1, false)
            intent.removeExtra("show_file_transfer")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != -1) return
        if (requestCode == SELECT_FILE_CODE) {
            val uri = data?.data ?: return
            ConnectionManager.INSTANCE.sendFile(uri, null)
            val cr = contentResolver
            var name: String = "file"
            try {
                cr.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex("_display_name")
                        if (idx >= 0) {
                            cursor.getString(idx)?.let { name = it }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            Toast.makeText(this, "开始发送: $name", Toast.LENGTH_SHORT).show()
            return
        }
        if (requestCode == SELECT_APK_CODE) {
            val uri = data?.data ?: return
            val file = uriToFile(uri)
            if (file != null && file.exists()) {
                installApk(file)
            }
        }
    }

    private fun uriToFile(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(cacheDir, "temp_send_${System.currentTimeMillis()}")
            FileOutputStream(tempFile).use { output ->
                inputStream.use { it.copyTo(output) }
            }
            tempFile
        } catch (_: Exception) {
            null
        }
    }

    override fun onResume() {
        super.onResume()
        updateSetupVisibility()
        val cm = getSystemService("clipboard") as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
        val view = pageCache[4] ?: return
        val textView = view.findViewById<TextView?>(R.id.currentClipText) ?: return
        textView.text = text
    }

    fun startPhoneScreenCapture() {
        if (screenCaptureRunning) return
        try {
            val dm = resources.displayMetrics
            screenWidth = dm.widthPixels
            screenHeight = dm.heightPixels
            screenDensity = dm.densityDpi
            if (screenWidth > 1280 || screenHeight > 1280) {
                val ratio = screenWidth.toFloat() / screenHeight.toFloat()
                if (screenWidth > screenHeight) {
                    screenWidth = 1280
                    screenHeight = (1280 / ratio).toInt()
                } else {
                    screenHeight = 1280
                    screenWidth = (1280 * ratio).toInt()
                }
            }
            val mpManager = getSystemService(MediaProjectionManager::class.java)
            val intent = mpManager.createScreenCaptureIntent()
            screenCaptureLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "启动推流失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startScreenCaptureLoop() {
        if (mediaProjection == null) {
            runOnUiThread {
                Toast.makeText(this, "屏幕录制权限未获取，请重试", Toast.LENGTH_SHORT).show()
            }
            return
        }
        screenCaptureRunning = true
        try {
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, 1, 3)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "PhoneHubMirror",
                screenWidth, screenHeight, screenDensity, 16,
                imageReader?.surface, null, null
            )
            if (virtualDisplay == null) {
                screenCaptureRunning = false
                runOnUiThread {
                    Toast.makeText(this, "创建虚拟显示失败", Toast.LENGTH_LONG).show()
                }
                return
            }
            screenCaptureThread = Thread {
                val conn = ConnectionManager.INSTANCE
                while (screenCaptureRunning) {
                    try {
                        val image = imageReader?.acquireLatestImage()
                        if (image == null) {
                            try { Thread.sleep(16) } catch (_: InterruptedException) { return@Thread }
                        } else {
                            val planes = image.planes
                            if (planes.isEmpty()) {
                                image.close()
                                try { Thread.sleep(16) } catch (_: InterruptedException) { return@Thread }
                            } else {
                                val buffer = planes[0].buffer
                                val pixelStride = planes[0].pixelStride
                                val rowStride = planes[0].rowStride
                                val rowPadding = rowStride - screenWidth * pixelStride
                                val bitmap: Bitmap = if (rowPadding == 0) {
                                    Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888).also {
                                        buffer.rewind()
                                        it.copyPixelsFromBuffer(buffer)
                                    }
                                } else {
                                    val padded = Bitmap.createBitmap(
                                        screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888
                                    )
                                    buffer.rewind()
                                    padded.copyPixelsFromBuffer(buffer)
                                    val cropped = Bitmap.createBitmap(padded, 0, 0, screenWidth, screenHeight)
                                    padded.recycle()
                                    cropped
                                }
                                val baos = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                                val jpegData = baos.toByteArray()
                                conn.sendFrameToPc(jpegData)
                                image.close()
                                bitmap.recycle()
                                try { Thread.sleep(16) } catch (_: InterruptedException) { return@Thread }
                            }
                        }
                    } catch (_: Exception) {
                        if (screenCaptureRunning) {
                            try { Thread.sleep(200) } catch (_: InterruptedException) { return@Thread }
                        }
                    }
                }
            }
            screenCaptureThread?.start()
        } catch (e: Exception) {
            screenCaptureRunning = false
            runOnUiThread {
                Toast.makeText(this, "创建虚拟显示失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun stopPhoneScreenCapture() {
        screenCaptureRunning = false
        screenCaptureThread?.interrupt()
        mirrorFrameTimeoutRunnable?.let { frameTimeoutHandler.removeCallbacks(it) }
        mirrorFrameTimeoutRunnable = null
        try {
            screenCaptureThread?.join(500)
        } catch (_: InterruptedException) {
        }
        screenCaptureThread = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    fun startPhoneAudioCapture() {
        if (audioCaptureRunning) return
        val minBufferSize = AudioRecord.getMinBufferSize(44100, 16, 2)
        if (minBufferSize < 0) {
            runOnUiThread {
                Toast.makeText(this, "音频参数不支持", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val bufferSize = maxOf(minBufferSize, 4096)
        try {
            val mp = mediaProjection
            if (mp == null) {
                Log.w("MainActivity", "MediaProjection 不可用，回退到 MIC 录音")
            } else {
                val config = AudioPlaybackCaptureConfiguration.Builder(mp)
                    .addMatchingUsage(1)
                    .addMatchingUsage(14)
                    .addMatchingUsage(0)
                    .build()
                audioRecord = AudioRecord.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(44100)
                            .setChannelMask(16)
                            .setEncoding(2)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setAudioPlaybackCaptureConfig(config)
                    .build()
                Log.i("MainActivity", "AudioPlaybackCapture 已启动（系统内音）")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "AudioPlaybackCapture 失败，回退到 MIC: ${e.message}")
            audioRecord = null
        }
        if (audioRecord == null) {
            audioRecord = AudioRecord(1, 44100, 16, 2, bufferSize)
            Log.i("MainActivity", "AudioRecord 使用 MIC 源")
        }
        val initialized = audioRecord?.state == 1
        if (!initialized) {
            Log.e("MainActivity", "AudioRecord 初始化失败，state=${audioRecord?.state}")
            audioRecord?.release()
            audioRecord = null
            runOnUiThread {
                Toast.makeText(this, "录音初始化失败，请检查麦克风权限", Toast.LENGTH_SHORT).show()
            }
            return
        }
        audioCaptureRunning = true
        audioRecord?.startRecording()
        audioCaptureThread = Thread {
            val buffer = ByteArray(bufferSize)
            val conn = ConnectionManager.INSTANCE
            val batchBuffers = ArrayList<ByteArray>()
            while (audioCaptureRunning) {
                try {
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    if (read > 0) {
                        batchBuffers.add(buffer.copyOf(read))
                        if (batchBuffers.size >= 5) {
                            var total = 0
                            for (b in batchBuffers) total += b.size
                            val merged = ByteArray(total)
                            var offset = 0
                            for (b in batchBuffers) {
                                System.arraycopy(b, 0, merged, offset, b.size)
                                offset += b.size
                            }
                            batchBuffers.clear()
                            conn.sendAudioToPc(merged)
                        }
                    }
                } catch (_: Exception) {
                    if (audioCaptureRunning) {
                        try { Thread.sleep(100) } catch (_: InterruptedException) { return@Thread }
                    }
                }
            }
            if (batchBuffers.isNotEmpty()) {
                try {
                    var total = 0
                    for (b in batchBuffers) total += b.size
                    val merged = ByteArray(total)
                    var offset = 0
                    for (b in batchBuffers) {
                        System.arraycopy(b, 0, merged, offset, b.size)
                        offset += b.size
                    }
                    conn.sendAudioToPc(merged)
                } catch (_: Exception) {
                }
            }
        }
        audioCaptureThread?.start()
    }

    fun stopPhoneAudioCapture() {
        audioCaptureRunning = false
        audioCaptureThread?.interrupt()
        audioCaptureThread = null
        try { audioRecord?.stop() } catch (_: Exception) {}
        audioRecord?.release()
        audioRecord = null
    }
}
