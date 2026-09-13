package com.phonehub

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.webkit.JavascriptInterface
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.util.Log
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.Editable
import android.content.res.Configuration
import java.net.HttpURLConnection
import java.net.URL
import android.text.InputType
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var setupScreen: LinearLayout
    private lateinit var mainContainer: LinearLayout
    private lateinit var ipInput: EditText
    private lateinit var portInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var pawUrlInput: EditText
    private lateinit var pawTokenInput: EditText
    private lateinit var connectBtn: Button
    private lateinit var pawConnectBtn: Button
    private lateinit var pawLoadingOverlay: FrameLayout
    private lateinit var connectStatus: TextView
    private lateinit var statusText: TextView
    private lateinit var titleText: TextView
    private lateinit var pageContainer: FrameLayout

    // 截图 Toast 防抖：同一消息 4 秒内只展示一次，避免“电脑截图已保存并发送”连续闪烁
    private var lastScreenshotToastAtMs = 0L
    private var lastScreenshotToastMsg = ""
    private var isKeyboardFullscreen = false
    private var savedTab = 0   // Remember previous tab before entering keyboard fullscreen
    private var currentTab = 0
    private var volumeReceiver: BroadcastReceiver? = null  // 音量变化广播接收器（onDestroy 注销防泄漏）
    private val pageCache = HashMap<Int, View>()

    // 电脑声音：常驻 WebView（退出界面后仍在后台播放），以及其后台容器
    private var pcAudioWebView: WebView? = null
    private var pcAudioHost: FrameLayout? = null
    private var rootFrame: FrameLayout? = null

    companion object {
        // 供电脑声音媒体通知的「播放/暂停」按钮回调到当前 Activity
        var instance: MainActivity? = null
    }
    private var mirrorImageView: android.widget.ImageView? = null  // save.md 功能7 电脑画面显示
    private var cameraImageView: android.widget.ImageView? = null  // save.md 功能8 电脑摄像头显示

    // 摄像头画面超时清理：2秒无新帧则清空画面
    private val frameTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var mirrorFrameTimeoutRunnable: Runnable? = null
    private var cameraFrameTimeoutRunnable: Runnable? = null

    // ───────────── 手机→电脑投屏（引擎整段来自 cs_apps/Screen_mirroring，端口 5423）─────────────
    // 采集/上传/内录/反向控制全在 PhoneHubMirrorService 里；这里只留界面用的状态。
    @Volatile private var mirrorRunning = false
    private var mirrorPendingIp = ""            // 授权回来后要用的目标地址
    private var mirrorPendingPort = 5423
    private var mirrorUiStatus: TextView? = null
    private var mirrorUiFps: TextView? = null

    private var isMirrorFullscreen = false
    private var mirrorFullscreenComponents: List<View?>? = null
    private var mirrorOriginalLp: android.view.ViewGroup.LayoutParams? = null
    private var mirrorOriginalPageContainerLp: android.widget.LinearLayout.LayoutParams? = null

    // CameraX 实时摄像头预览
    private var cameraProvider: androidx.camera.lifecycle.ProcessCameraProvider? = null
    private var cameraInstance: androidx.camera.core.Camera? = null
    private var cameraLensFacing = androidx.camera.core.CameraSelector.LENS_FACING_BACK
    @Volatile private var cameraPreviewRunning = false
    private var cameraExecutor: java.util.concurrent.ExecutorService? = null
    private var cameraPreviewView: androidx.camera.view.PreviewView? = null

    private val SELECT_FILE_CODE = 1001
    private val SELECT_APK_CODE = 1002

    // 推送网页 URL 历史条目（url + 方向标签 + 时间戳），持久化到 SharedPreferences
    private data class UrlHistoryItem(val url: String, val direction: String, val timestamp: Long)
    private val urlHistory = mutableListOf<UrlHistoryItem>()
    // 文字消息去重：已处理内容 → 处理时间戳，5秒内相同内容不再弹窗
    private val handledTextContents = mutableMapOf<String, Long>()

    /** 从 SharedPreferences 加载 URL 历史 */
    private fun loadUrlHistory() {
        urlHistory.clear()
        val prefs = getSharedPreferences("phonehub_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("push_url_history", "") ?: ""
        if (raw.isEmpty()) return
        for (line in raw.split("\n")) {
            if (line.isBlank()) continue
            val parts = line.split("\t", limit = 3)
            if (parts.size == 3) {
                val ts = parts[0].toLongOrNull() ?: continue
                urlHistory.add(UrlHistoryItem(parts[2], parts[1], ts))
            }
        }
    }

    /** 保存 URL 历史到 SharedPreferences */
    private fun saveUrlHistory() {
        val prefs = getSharedPreferences("phonehub_prefs", Context.MODE_PRIVATE)
        val raw = urlHistory.joinToString("\n") { "${it.timestamp}\t${it.direction}\t${it.url}" }
        prefs.edit().putString("push_url_history", raw).apply()
    }

    /** 添加一条 URL 历史并去重、限制数量、持久化 */
    private fun addUrlHistory(url: String, direction: String) {
        // 去重：移除同 URL 同方向的旧记录
        urlHistory.removeAll { it.url == url && it.direction == direction }
        urlHistory.add(0, UrlHistoryItem(url, direction, System.currentTimeMillis()))
        if (urlHistory.size > 50) urlHistory.subList(50, urlHistory.size).clear()
        saveUrlHistory()
    }

    // 权限请求
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 结果忽略 */ }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) Toast.makeText(this, "摄像头权限已授予", Toast.LENGTH_SHORT).show()
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "录音权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "录音权限被拒绝：投屏只能传画面，声音不可用", Toast.LENGTH_SHORT).show()
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 结果忽略 */ }

    private val allPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 结果忽略 */ }

    // ============================== 手机 → 电脑投屏（参考工程引擎）==============================
    // 引擎整段来自 cs_apps/Screen_mirroring：手机把画面/内录推到电脑 5423 端口
    // （POST /upload、/audio_start、/audio、/stop），协议与端口与参考工程完全一致。
    // 这里只做四件事：连接探测、档位设置、屏幕录制授权、启停服务与状态显示。

    /** 屏幕录制授权回调：拿到 token 后交给 PhoneHubMirrorService 起投屏 */
    private val mirrorConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            try {
                ConnectionManager.cacheMediaProjectionToken(result.resultCode, result.data!!)
                PhoneHubMirrorService.setPendingResult(result.resultCode, result.data!!)
                PhoneHubMirrorService.saveProjectionResult(this, result.resultCode, result.data!!)
                launchMirrorService(mirrorPendingIp, mirrorPendingPort)
            } catch (e: RuntimeException) {
                LogUtil.scrE("[投屏] 授权结果处理失败", e)
                setMirrorStatus("授权结果处理失败: ${e.message}")
            }
        } else {
            setMirrorStatus("屏幕录制授权被拒绝，可再点「开始投屏」重试")
        }
    }

    /** 状态栏文案（页面可能还没创建，判空处理） */
    private fun setMirrorStatus(msg: String) {
        mirrorUiStatus?.text = msg
        LogUtil.scrI("[投屏][UI] $msg")
    }

    /** 目标电脑 IP 默认值：优先用主工程已知的电脑地址，其次参考工程的默认值 */
    private fun defaultMirrorIp(): String {
        val fromConn = try { ConnectionManager.getPcHostForMirror() } catch (_: Exception) { null }
        return if (!fromConn.isNullOrBlank()) fromConn else "192.168.3.9"
    }

    /** 手机端点「开始投屏」和电脑端下发 mirror_start 都汇到这里 */
    private fun startMirrorFlow(ip: String?, port: Int?) {
        val useIp = if (ip.isNullOrBlank()) defaultMirrorIp() else ip
        val usePort = port ?: 5423
        mirrorPendingIp = useIp
        mirrorPendingPort = usePort
        // Android 13+ 前台服务通知需要通知权限（没有就退化成没有通知，不阻塞投屏）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            try { notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) } catch (_: Exception) {}
        }
        // 内录需要 RECORD_AUDIO（没有就静默地只投画面）
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
        ) {
            try { audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO) } catch (_: Exception) {}
        }
        setMirrorStatus("正在启动投屏 → $useIp:$usePort ...")
        if (PhoneHubMirrorService.pendingResultData != null || PhoneHubMirrorService.hasProjection()) {
            launchMirrorService(useIp, usePort)
            return
        }
        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as android.media.projection.MediaProjectionManager
            mirrorConsentLauncher.launch(mpm.createScreenCaptureIntent())
        } catch (e: RuntimeException) {
            LogUtil.scrE("[投屏] 请求屏幕录制授权失败", e)
            setMirrorStatus("请求屏幕录制授权失败: ${e.message}")
        }
    }

    private fun launchMirrorService(ip: String, port: Int) {
        val svc = Intent(this, PhoneHubMirrorService::class.java).apply {
            action = PhoneHubMirrorService.ACTION_START_MIRROR
            putExtra(PhoneHubMirrorService.EXTRA_PC_IP, ip)
            putExtra(PhoneHubMirrorService.EXTRA_PC_PORT, port)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc)
            } else {
                startService(svc)
            }
            mirrorRunning = true
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setMirrorStatus("投屏中 → $ip:$port（电脑上会弹出「手机屏幕」窗口）")
        } catch (e: RuntimeException) {
            LogUtil.scrE("[投屏] 启动投屏服务失败", e)
            mirrorRunning = false
            setMirrorStatus("启动投屏服务失败: ${e.message}")
        }
    }

    private fun stopMirrorFlow() {
        val svc = Intent(this, PhoneHubMirrorService::class.java).apply {
            action = PhoneHubMirrorService.ACTION_STOP_MIRROR
        }
        // 停止指令优先用 startService：Activity 在前台时它合法，也不会触发
        // startForegroundService 那条"5 秒内必须 startForeground"的约束
        var sent = false
        try {
            startService(svc)
            sent = true
        } catch (_: Exception) {
        }
        if (!sent) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svc)
                } else {
                    startService(svc)
                }
            } catch (e: RuntimeException) {
                LogUtil.scrE("[投屏] 停止投屏服务失败", e)
            }
        }
        mirrorRunning = false
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setMirrorStatus("已发送停止指令")
    }

    /**
     * 投屏过程中切换了 清晰度 / 音质 / 模式：通知正在运行的投屏服务按新设置"重新开始投屏"，
     * 让电脑端用新的正确方式重新接收（服务没在跑就什么都不做，下次开始自然生效）。
     * 以服务自身的运行状态为准（界面里的 mirrorRunning 可能不准，例如 Activity 重建后）。
     */
    private fun applyMirrorSettingsIfRunning() {
        val running = PhoneHubMirrorService.instance?.isMirrorRunning() == true
        if (!running) return
        LogUtil.scrI("[投屏] 设置变更，通知服务按新设置重新投屏")
        val svc = Intent(this, PhoneHubMirrorService::class.java).apply {
            action = PhoneHubMirrorService.ACTION_APPLY_SETTINGS
        }
        try {
            startService(svc)
        } catch (e: Exception) {
            LogUtil.scrE("[投屏] 应用新设置失败", e)
        }
        setMirrorStatus("正在按新设置重新投屏…")
    }

    // 文字保存：用系统文件选择器（ACTION_CREATE_DOCUMENT）选择保存路径
    private var pendingSaveText: String = ""
    private val saveTextLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val textToSave = pendingSaveText
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(uri, "w")?.use { os ->
                        os.write(textToSave.toByteArray(Charsets.UTF_8))
                        os.flush()
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "已保存", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "保存文字失败", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 不再无条件常亮：屏幕常亮只在投屏/观看电脑画面期间按需设置，避免正常使用时长亮耗电
        setContentView(R.layout.activity_main)

        setupScreen = findViewById(R.id.setupScreen)
        mainContainer = findViewById(R.id.mainContainer)
        ipInput = findViewById(R.id.ipInput)
        portInput = findViewById(R.id.portInput)
        tokenInput = findViewById(R.id.tokenInput)
        pawUrlInput = findViewById(R.id.pawUrlInput)
        pawTokenInput = findViewById(R.id.pawTokenInput)
        connectBtn = findViewById(R.id.connectBtn)
        connectStatus = findViewById(R.id.connectStatus)
        statusText = findViewById(R.id.statusText)
        titleText = findViewById(R.id.titleText)
        pageContainer = findViewById(R.id.pageContainer)
        rootFrame = findViewById(R.id.rootFrame)
        instance = this

        // IP/端口/Token 缓存
        val prefs = getSharedPreferences("phonehub_prefs", Context.MODE_PRIVATE)
        ConnectionManager.getCachedIp()?.let { ipInput.setText(it) }
        if (ipInput.text.isBlank()) ipInput.setText("192.168.3.9")
        val cachedPort = prefs.getInt("cached_port", 0)
        if (cachedPort > 0) portInput.setText(cachedPort.toString()) else portInput.setText("58627")
        val cachedToken = prefs.getString("cached_token", "")
        if (!cachedToken.isNullOrEmpty()) tokenInput.setText(cachedToken) else tokenInput.setText("541881452418845")

        // Cloudflare 隧道配置（临时隧道地址每次会变，交给用户手动填写）
        val cachedPawUrl = prefs.getString("cached_paw_url", "")
        if (!cachedPawUrl.isNullOrEmpty()) pawUrlInput.setText(cachedPawUrl)
        if (pawUrlInput.text.isBlank()) {
            pawUrlInput.setText("")
            pawUrlInput.hint = "例如 https://xxx.trycloudflare.com"
        }
        val cachedPawToken = prefs.getString("cached_paw_token", "")
        if (!cachedPawToken.isNullOrEmpty()) pawTokenInput.setText(cachedPawToken)
        if (pawTokenInput.text.isBlank()) pawTokenInput.setText("541881452418845")

        // Cloudflare 配置保存按钮
        findViewById<Button>(R.id.savePawBtn)?.setOnClickListener {
            val url = pawUrlInput.text.toString().trim()
            val token = pawTokenInput.text.toString().trim()
            if (url.isNotEmpty() && token.isNotEmpty()) {
                ConnectionManager.setPawConfig(url, token)
                prefs.edit()
                    .putString("cached_paw_url", url)
                    .putString("cached_paw_token", token)
                    .apply()
                Toast.makeText(this, "Cloudflare 隧道设置已保存", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "隧道地址和令牌不能为空", Toast.LENGTH_SHORT).show()
            }
        }

        // Cloudflare 隧道连接按钮
        pawConnectBtn = findViewById(R.id.pawConnectBtn)
        pawLoadingOverlay = findViewById(R.id.pawLoadingOverlay)
        pawConnectBtn.setOnClickListener {
            val url = pawUrlInput.text.toString().trim()
            val token = pawTokenInput.text.toString().trim()
            if (url.isEmpty() || token.isEmpty()) {
                Toast.makeText(this, "请先填写隧道地址和 Token 并保存", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ConnectionManager.setPawConfig(url, token)
            prefs.edit()
                .putString("cached_paw_url", url)
                .putString("cached_paw_token", token)
                .apply()
            // 显示加载遮罩，隐藏表单
            pawLoadingOverlay.visibility = View.VISIBLE
            findViewById<TextView>(R.id.pawLoadingText).text = "正在通过 Cloudflare 隧道连接..."
            ConnectionManager.connectPaw()
        }

        // Token 显示/隐藏切换
        val toggleBtn = findViewById<TextView>(R.id.toggleTokenVisibility)
        toggleBtn.setOnClickListener {
            if (tokenInput.inputType == (android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                tokenInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                toggleBtn.text = "隐藏"
            } else {
                tokenInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                toggleBtn.text = "显示"
            }
            tokenInput.setSelection(tokenInput.text.length)
        }

        connectBtn.applyDarkTheme(primary = true)
        connectBtn.setOnClickListener { attemptConnect() }

        // 启动时自动请求所有权限（先弹说明弹窗，用户点"确定"后才发起授权跳转）
        promptAndRequestAllPermissions()

        setupTabs()
        // 防止 SharedFlow replay 导致重启后重复弹窗：把上次收到的文字标记为已处理
        ConnectionManager.lastReceivedText?.let { (fn, txt) ->
            handledTextContents["$fn|$txt"] = System.currentTimeMillis()
        }
        setupFlows()
        updateSetupVisibility()

        // 处理从通知启动的情况（应用被杀后重启）
        handleTextNotificationIntent(intent)
        handleFileTransferNotificationIntent(intent)

        // 实时同步手机媒体音量变化到电脑端
        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                try {
                    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val vol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    ConnectionManager.sendVolumeChanged(vol)
                } catch (_: Exception) {}
            }
        }
        // 防止 Activity 重建时残留旧注册导致重复注册：先尝试注销再注册
        try {
            unregisterReceiver(volumeReceiver)
        } catch (_: Exception) {}
        // targetSdk 34+ 动态注册广播必须指定 RECEIVER_EXPORTED/NOT_EXPORTED，否则抛 SecurityException
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(volumeReceiver, filter)
        }

        // 使用 OnBackPressedDispatcher 替代已废弃的 onBackPressed()
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isKeyboardFullscreen) {
                    exitKeyboardFullscreen()
                    return
                }
                if (isMirrorFullscreen) {
                    exitMirrorFullscreen()
                    return
                }
                if (currentTab != 0) {
                    switchTab(0, forward = false)
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    override fun onDestroy() {
        // 投屏（手机→电脑）跑在 PhoneHubMirrorService 前台服务里，退出界面不停它；
        // 需要停的时候点页面上的「停止投屏」。
        // 注销音量广播接收器，防止 Activity 内存泄漏
        try {
            volumeReceiver?.let { unregisterReceiver(it) }
        } catch (_: IllegalArgumentException) {}
        volumeReceiver = null
        // 电脑声音 WebView 随 Activity 销毁；销毁前取消媒体通知，避免残留「正在播放」
        try { pcAudioWebView?.destroy() } catch (_: Exception) {}
        pcAudioWebView = null
        ConnectionManager.cancelPcMediaNotification()
        ConnectionManager.setPcAudioKeepAlive(false)
        instance = null
        super.onDestroy()
    }

    private fun enterMirrorFullscreen(mirrorFrame: FrameLayout?, allUiComponents: List<View?>) {
        if (isMirrorFullscreen) return
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val win = window
        androidx.core.view.WindowInsetsControllerCompat(win, win.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        supportActionBar?.hide()
        val mainContainer = findViewById<LinearLayout>(R.id.mainContainer)
        val titleBar = mainContainer?.getChildAt(0) as? android.widget.LinearLayout
        val tabScroll = mainContainer?.getChildAt(1) as? android.widget.HorizontalScrollView
        titleBar?.visibility = View.GONE
        tabScroll?.visibility = View.GONE
        allUiComponents.forEach { it?.visibility = View.GONE }
        mirrorOriginalPageContainerLp = findViewById<FrameLayout>(R.id.pageContainer).layoutParams as? android.widget.LinearLayout.LayoutParams
        mirrorOriginalPageContainerLp?.let {
            val newLp = android.widget.LinearLayout.LayoutParams(it.width, 0)
            newLp.weight = 1f
            findViewById<FrameLayout>(R.id.pageContainer).layoutParams = newLp
        }
        mirrorOriginalLp = mirrorFrame?.layoutParams
        val lp = mirrorFrame?.layoutParams as? LinearLayout.LayoutParams
        lp?.let {
            it.weight = 1f
            it.height = 0
            mirrorFrame.layoutParams = it
        }
        isMirrorFullscreen = true
        mirrorFullscreenComponents = allUiComponents
        // Task 18.5：全屏时显示角落半透明退出按钮
        pageCache[8]?.findViewById<Button>(R.id.btnMirrorFullscreenExit)?.visibility = View.VISIBLE
    }

    private fun exitMirrorFullscreen() {
        if (!isMirrorFullscreen) return
        isMirrorFullscreen = false
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val win = window
        androidx.core.view.WindowInsetsControllerCompat(win, win.decorView).apply {
            show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        supportActionBar?.show()
        val mainContainer = findViewById<LinearLayout>(R.id.mainContainer)
        val titleBar = mainContainer?.getChildAt(0) as? android.widget.LinearLayout
        val tabScroll = mainContainer?.getChildAt(1) as? android.widget.HorizontalScrollView
        titleBar?.visibility = View.VISIBLE
        tabScroll?.visibility = View.VISIBLE
        mirrorFullscreenComponents?.forEach { it?.visibility = View.VISIBLE }
        // 恢复原始布局
        mirrorOriginalPageContainerLp?.let {
            findViewById<FrameLayout>(R.id.pageContainer).layoutParams = it
        }
        mirrorOriginalLp?.let { lp ->
            // 恢复 mirrorFrame 布局，退出全屏后保留竖屏画面区（不停止投屏）
            pageCache[8]?.findViewById<FrameLayout>(R.id.mirrorFrame)?.let { frame ->
                frame.layoutParams = lp
                frame.visibility = View.VISIBLE
            }
        }
        mirrorOriginalLp = null
        mirrorOriginalPageContainerLp = null
        // 保留电脑投屏：退出全屏不停止投屏、不清空画面，仅切换回竖屏布局
        pageCache[8]?.findViewById<TextView>(R.id.mirrorStatus)?.text = "已连接 - 正在查看电脑画面..."
        // 隐藏全屏退出按钮
        pageCache[8]?.findViewById<Button>(R.id.btnMirrorFullscreenExit)?.visibility = View.GONE
    }

    /**
     * 一进入软件就请求所有运行时权限，并引导用户到设置页面开启特殊权限。
     * 运行时权限用 ActivityCompat.requestPermissions 一次性请求；
     * 特殊权限（悬浮窗、修改系统设置、电池优化忽略、安装未知应用）用 Intent 跳转设置页。
     * 每个 Intent 跳转都用 try-catch 包裹，避免个别页面不存在导致崩溃。
     */
    private fun requestAllPermissions() {
        // 仅在首次运行时请求权限，后续启动跳过
        val prefs = getSharedPreferences("phonehub_prefs", Context.MODE_PRIVATE)
        // Android 13+ 需要重新请求媒体权限，重置标志位
        if (prefs.getBoolean("permissions_requested", false)
            && android.os.Build.VERSION.SDK_INT < 33) return

        // 1. 运行时可申请权限
        val runtimePermissions = mutableListOf<String>()
        // 存储权限
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            // Android 13+ 使用细粒度媒体权限
            runtimePermissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            runtimePermissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            runtimePermissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            runtimePermissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            runtimePermissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        // 位置信息
        runtimePermissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        runtimePermissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        // 相机、麦克风
        runtimePermissions.add(android.Manifest.permission.CAMERA)
        runtimePermissions.add(android.Manifest.permission.RECORD_AUDIO)
        // 电话
        runtimePermissions.add(android.Manifest.permission.READ_PHONE_STATE)
        runtimePermissions.add(android.Manifest.permission.CALL_PHONE)
        // 活动识别
        runtimePermissions.add(android.Manifest.permission.ACTIVITY_RECOGNITION)
        // 一次性请求所有运行时权限
        try {
            ActivityCompat.requestPermissions(
                this,
                runtimePermissions.toTypedArray(),
                1001
            )
        } catch (e: Exception) {
            // 部分权限未在 manifest 声明会抛异常，忽略
        }

        // 2. 特殊权限：通过 Intent 引导用户到设置页面开启
        //    使用 Handler 延迟启动，避免同时弹出多个设置页
        val handler = Handler(Looper.getMainLooper())

        // 2.1 悬浮窗 / 显示在其他应用上层（SYSTEM_ALERT_WINDOW）
        handler.postDelayed({
            try {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
            } catch (e: Exception) {
            }
        }, 500)

        // 2.1b 所有文件访问权限（Android 11+，文件管理器需要）
        handler.postDelayed({
            try {
                if (android.os.Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
            } catch (e: Exception) {
            }
        }, 750)

        // 2.2 电池优化忽略（REQUEST_IGNORE_BATTERY_OPTIMIZATIONS）
        handler.postDelayed({
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
            } catch (e: Exception) {
            }
        }, 1500)

        // 2.3 安装未知应用 / 应用内安装其他程序（REQUEST_INSTALL_PACKAGES）
        handler.postDelayed({
            try {
                if (!packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    intent.data = Uri.parse("package:$packageName")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
            } catch (e: Exception) {
            }
            // 所有权限请求完成后，标记为已请求
            prefs.edit().putBoolean("permissions_requested", true).apply()
        }, 2000)
    }

    /**
     * 先弹窗说明当前需要申请的权限，用户点"确定"后才真正发起授权跳转。
     * 避免"一进入就蹦到权限授权界面"。
     */
    private fun promptAndRequestAllPermissions() {
        val prefs = getSharedPreferences("phonehub_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("permissions_requested", false) && Build.VERSION.SDK_INT < 33) return

        // 收集尚未开启的特殊权限，用于向用户说明
        // 各检测独立 try-catch：部分 API（如 canRequestPackageInstalls）在 Manifest 未声明对应权限时会抛 SecurityException
        val pending = mutableListOf<String>()
        try {
            if (!Settings.canDrawOverlays(this)) {
                pending.add("悬浮窗（在其他应用上层显示）")
            }
        } catch (_: Exception) {}
        try {
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                pending.add("所有文件访问权限（文件管理）")
            }
        } catch (_: Exception) {}
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                pending.add("忽略电池优化（保持后台连接）")
            }
        } catch (_: Exception) {}
        try {
            if (!packageManager.canRequestPackageInstalls()) {
                pending.add("安装未知应用（自动安装APK）")
            }
        } catch (_: Exception) {}

        // 顶部：实时显示电脑状态
        val statusTitle = TextView(this)
        statusTitle.text = "电脑状态"
        statusTitle.textSize = 16f
        statusTitle.setTextColor(0xFFFFFFFF.toInt())
        statusTitle.setTypeface(null, android.graphics.Typeface.BOLD)
        statusTitle.setPadding(0, dp(6), 0, dp(4))

        val statusView = TextView(this)
        statusView.text = buildPcStatusText()
        statusView.textSize = 14f
        statusView.setTextColor(0xFFD0D0D0.toInt())
        statusView.setPadding(0, 0, 0, dp(6))

        // 分隔线
        val divider = View(this)
        divider.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        divider.setBackgroundColor(0x1FFFFFFF.toInt())

        // 权限说明
        val msgView = TextView(this)
        msgView.text = buildString {
            append("需要以下权限才能正常工作：\n\n")
            append("• 存储：读写手机文件\n")
            append("• 位置：位置同步\n")
            append("• 相机/麦克风：摄像头共享 / 语音\n")
            append("• 电话：来电等基础信息\n")
            if (pending.isNotEmpty()) {
                append("\n以下还需在系统设置中手动开启：\n")
                pending.forEach { append("• $it\n") }
            }
            append("\n点击「确定」后将依次引导你开启。")
        }
        msgView.textSize = 14f
        msgView.setTextColor(0xFFB0B0B0.toInt())
        msgView.setPadding(0, dp(8), 0, 0)
        msgView.setLineSpacing(0f, 1.2f)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(24), dp(12), dp(24), dp(4))
        content.addView(statusTitle)
        content.addView(statusView)
        content.addView(divider)
        content.addView(msgView)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(content)
            .setCancelable(false)
            .setPositiveButton("确定") { _, _ -> requestAllPermissions() }
            .setNegativeButton("暂不", null)
            .create()
        dialog.show()

        // 实时刷新电脑状态：连接状态/通道/IP 任一变化即更新
        val statusJob = lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                ConnectionManager.connectionState,
                ConnectionManager.currentChannel,
                ConnectionManager.connectionMessage
            ) { _, _, _ -> Unit }
                .collect { statusView.text = buildPcStatusText() }
        }
        dialog.setOnDismissListener { statusJob.cancel() }
    }

    /** 组装电脑状态的实时展示文本 */
    private fun buildPcStatusText(): String {
        val stateName = when (ConnectionManager.connectionState.value) {
            ConnectionManager.ConnectionState.CONNECTED -> "已连接"
            ConnectionManager.ConnectionState.CONNECTING -> "连接中"
            else -> {
                val m = ConnectionManager.connectionMessage.value
                if (!m.isNullOrBlank() && m != "未连接") m else "未连接"
            }
        }
        val channelName = when (ConnectionManager.currentChannel.value) {
            ConnectionManager.ChannelType.WIFI -> "WiFi 直连"
            ConnectionManager.ChannelType.PAW -> "Cloudflare 隧道"
            ConnectionManager.ChannelType.ADB -> "USB 数据线"
            else -> ""
        }
        val ip = ConnectionManager.getPcIp()
        return buildString {
            append("状态：$stateName")
            if (channelName.isNotEmpty()) append(" · $channelName")
            if (!ip.isNullOrBlank()) append("\n电脑IP：$ip")
        }
    }

    // ============================== Tab 栏 ==============================

    private fun setupTabs() {
        // Tab 栏已移除，导航改为首页功能网格 + 返回键回首页
    }

    private fun switchTab(index: Int, forward: Boolean = true) {
        if (currentTab == index && pageContainer.childCount > 0) return
        val oldView = pageContainer.getChildAt(0)
        val newView = getPageView(index)
        // 离开旧页面时清理后台资源
        if (currentTab != index) {
            cleanupPageResources(currentTab)
        }
        currentTab = index

        // 进入「电脑声音」页：把常驻 WebView 从后台容器移回页面（连接保持不变，后台继续播放）
        if (index == 17) {
            reparentAudioWebViewToPage()
        }

        // 进入投屏页时检测无障碍服务是否开启
        if (index == 8 && PhoneHubAccessibilityService.instance == null) {
            android.os.Handler(mainLooper).postDelayed({
                Toast.makeText(this@MainActivity, "无障碍服务未开启，无法操控手机。请在设置→无障碍中开启 PhoneHub", Toast.LENGTH_LONG).show()
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: Exception) {}
            }, 500)
        }

        if (forward) {
            // 进入：新页面从右向左覆盖滑入，旧页面停留在下层被覆盖
            if (oldView != null && oldView !== newView) {
                newView.translationX = newView.width.toFloat()
                pageContainer.addView(newView)
                newView.animate()
                    .translationX(0f)
                    .setDuration(280)
                    .withEndAction {
                        if (oldView.parent != null && oldView !== newView) {
                            pageContainer.removeView(oldView)
                            oldView.translationX = 0f
                        }
                    }
                    .start()
            } else if (oldView != null) {
                pageContainer.removeView(oldView)
                pageContainer.addView(newView)
            } else {
                pageContainer.addView(newView)
            }
        } else {
            // 退出：当前页面从左向右滑出，露出下层页面
            if (oldView != null && oldView !== newView) {
                pageContainer.removeAllViews()
                pageContainer.addView(newView)
                pageContainer.addView(oldView)
                oldView.animate()
                    .translationX(oldView.width.toFloat())
                    .setDuration(280)
                    .withEndAction {
                        pageContainer.removeView(oldView)
                        oldView.translationX = 0f
                    }
                    .start()
            } else if (oldView != null) {
                pageContainer.removeView(oldView)
                pageContainer.addView(newView)
            } else {
                pageContainer.addView(newView)
            }
        }
    }

    private fun cleanupPageResources(oldIndex: Int) {
        when (oldIndex) {
            8 -> { // 投屏页
                // 手机→电脑的投屏由 PhoneHubMirrorService 前台服务负责，离开页面不停它
                // （要停请点页面上的「停止投屏」）；这里只收掉「电脑→手机」那路。
                ConnectionManager.sendMediaCommand("pc_stream_stop")
                ConnectionManager.stopPcFramePolling()
                ConnectionManager.stopPcAudioPolling()
                // 退出全屏并隐藏画面区域
                if (isMirrorFullscreen) exitMirrorFullscreen()
                pageCache[8]?.findViewById<FrameLayout>(R.id.mirrorFrame)?.visibility = View.GONE
            }
            9 -> { // 摄像头页
                ConnectionManager.sendAction("camera_stop", emptyMap())
            }
            10 -> { // 通知页
                // 停止通知刷新（不在通知页时不需要刷新）
            }
            11 -> { // 文件管理页
                // 按需加载，离开时无需特殊处理
            }
            17 -> { // 电脑声音页（WebView 收听）：退出界面后仍在后台播放，不停止、不取消通知
                reparentAudioWebViewToHost()
            }
        }
    }

    private fun getPageView(index: Int): View {
        pageCache[index]?.let { return it }
        val view = when (index) {
            0 -> getHomeView()
            1 -> getFilesView()
            2 -> getRemoteView()
            3 -> getFullKeyboardView()
            4 -> getClipboardView()
            // 5 剪贴板历史已合并到剪贴板页（index 4）
            5 -> getClipboardView()
            6 -> getLocationView()
            7 -> getScreenshotView()
            8 -> getMirrorView()
            9 -> getCameraView()
            10 -> getNotificationsView()
            11 -> getFileManagerView()
            12 -> {
                // APK安装已改为自动安装，不再显示此页面
                val v = LinearLayout(this)
                v.orientation = LinearLayout.VERTICAL
                v.setBackgroundColor(0xFF1e1e1e.toInt())
                v.setPadding(dp(32), dp(32), dp(32), dp(32))
                val tv = TextView(this)
                tv.text = "APK 安装已改为自动安装\n\n电脑端发送 APK 后，手机将自动安装"
                tv.setTextColor(0xFFb0b0b0.toInt())
                tv.textSize = 14f
                v.addView(tv)
                v
            }
            13 -> getAppManagerView()
            14 -> getPowerView()
            15 -> getPushWebView()
            16 -> getSettingsView()
            17 -> getPcAudioWebView()
            else -> getHomeView()
        }
        pageCache[index] = view
        return view
    }

    // ============================== 首页 ==============================

    // 首页功能项定义：name=显示名，icon=颜文字图标，tabIndex=对应tab索引（-1=文字弹窗）
    private data class FuncInfo(val name: String, val icon: String, val tabIndex: Int)

    // 全部功能列表（每行3个，颜文字作为图标）
    private val allFuncList: List<FuncInfo> = listOf(
        FuncInfo("文件传输", "📁", 1),
        FuncInfo("剪贴板", "📋", 4),
        FuncInfo("文字互传", "💬", -1),
        FuncInfo("远程控制", "🎮", 2),
        // 投屏页：手机→电脑(5423) 与 电脑→手机(58627) 双向都在本页；电脑→手机进页自动拉取显示
        FuncInfo("投屏", "🖥️", 8),
        FuncInfo("摄像头", "📷", 9),
        FuncInfo("通知", "🔔", 10),
        FuncInfo("路线图", "🗺️", 6),
        FuncInfo("文件管理", "📂", 11),
        FuncInfo("电源管理", "⚡", 14),
        FuncInfo("推送网页", "🌐", 15),
        FuncInfo("电脑声音", "♪", 17),
        FuncInfo("设置", "⚙️", 16)
    )

    /** dp 转 px */
    private fun dpToPx(v: Float): Float = v * resources.displayMetrics.density

    /** dp 转 px（Int） */
    private fun dp(v: Int): Int = dpToPx(v.toFloat()).toInt()

    /** 首页：连接状态 + CPU/内存 + 最近操作 + 所有功能大图标网格 */
    private fun getHomeView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1e1e1e.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // ===== 连接状态框 =====
        val statusBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF2d2d2d.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val connLabel = TextView(this).apply {
            text = "连接状态"
            setTextColor(0xFFb0b0b0.toInt())
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        }
        val connStatusHome = TextView(this).apply {
            id = R.id.connStatusHome
            text = "未连接"
            setTextColor(0xFFd13438.toInt())
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val channelHome = TextView(this).apply {
            id = R.id.channelHome
            text = "通道: 无"
            setTextColor(0xFFb0b0b0.toInt())
            textSize = 12f
            setPadding(0, dp(4), 0, 0)
        }
        statusBox.addView(connLabel)
        statusBox.addView(connStatusHome)
        statusBox.addView(channelHome)
        // 初始化为当前连接状态/通道
        initHomeStatus(connStatusHome, channelHome)
        root.addView(statusBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        

        // ===== 最近操作（最多 6 个，按使用次数/时间排序）=====
        val recentLabel = TextView(this).apply {
            text = "最近操作"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(recentLabel)
        val recentFuncs = getRecentFunctions(6)
        if (recentFuncs.isEmpty()) {
            val empty = TextView(this).apply {
                text = "暂无最近操作，点击下方功能开始使用"
                setTextColor(0xFFb0b0b0.toInt())
                textSize = 12f
                setPadding(0, 0, 0, dp(12))
            }
            root.addView(empty)
        } else {
            val recentScroll = HorizontalScrollView(this)
            val recentRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(12))
            }
            for (func in recentFuncs) {
                val btn = buildFuncButton(func, small = true)
                btn.setOnClickListener { onFuncClicked(func) }
                val lp = LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.setMargins(dp(4), 0, dp(4), 0)
                recentRow.addView(btn, lp)
            }
            recentScroll.addView(recentRow)
            root.addView(recentScroll, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        // ===== 所有功能大图标网格（每行 3 个）=====
        val allLabel = TextView(this).apply {
            text = "所有功能"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(allLabel)

        var row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        var count = 0
        for (func in allFuncList) {
            val btn = buildFuncButton(func, small = false)
            btn.setOnClickListener { onFuncClicked(func) }
            row.addView(btn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            count++
            if (count % 3 == 0) {
                root.addView(row, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) })
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            }
        }
        if (count % 3 != 0) {
            root.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) })
        }

        // 用 ScrollView 包裹，避免内容超出屏幕
        return ScrollView(this).apply { addView(root) }
    }

    /** 初始化首页连接状态/通道显示（避免 flow 未触发时显示默认值） */
    private fun initHomeStatus(connStatusHome: TextView, channelHome: TextView) {
        val state = ConnectionManager.connectionState.value
        when (state) {
            ConnectionManager.ConnectionState.CONNECTED -> {
                connStatusHome.text = "已连接"
                connStatusHome.setTextColor(0xFF107c10.toInt())
            }
            ConnectionManager.ConnectionState.CONNECTING -> {
                connStatusHome.text = "连接中..."
                connStatusHome.setTextColor(0xFFffb900.toInt())
            }
            else -> {
                connStatusHome.text = "未连接"
                connStatusHome.setTextColor(0xFFd13438.toInt())
            }
        }
        val ch = ConnectionManager.currentChannel.value
        val chName = when (ch) {
            ConnectionManager.ChannelType.WIFI -> "WiFi 直连"
            ConnectionManager.ChannelType.ADB -> "USB 数据线"
            else -> "无"
        }
        channelHome.text = "通道: $chName"
    }

    /** 构建单个功能图标按钮（颜文字 + 文字，居中，>=80dp） */
    private fun buildFuncButton(func: FuncInfo, small: Boolean): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF2d2d2d.toInt())
            setPadding(dp(8), dp(12), dp(8), dp(12))
            minimumHeight = dp(80)
        }
        val iconSize = if (small) 22f else 28f
        val textSize = if (small) 10f else 11f
        val icon = TextView(this).apply {
            text = func.icon
            this.textSize = iconSize
            gravity = Gravity.CENTER
        }
        val label = TextView(this).apply {
            text = func.name
            setTextColor(0xFFFFFFFF.toInt())
            this.textSize = textSize
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }
        container.addView(icon)
        container.addView(label)
        return container
    }

    /** 点击功能：记录使用次数 + 跳转对应 tab 或弹窗 */
    private fun onFuncClicked(func: FuncInfo) {
        recordFunctionUse(func.name)
        // 清除首页缓存，下次返回时重新渲染最近操作
        pageCache.remove(0)
        if (func.tabIndex == -1) {
            // 文字互传 → 打开发送文字弹窗
            showSendTextDialog()
        } else {
            switchTab(func.tabIndex)
        }
    }

    /** 记录功能使用次数和最后使用时间到 SharedPreferences */
    private fun recordFunctionUse(name: String) {
        val prefs = getSharedPreferences("phonehub_func_usage", Context.MODE_PRIVATE)
        val count = prefs.getInt("${name}_count", 0) + 1
        prefs.edit()
            .putInt("${name}_count", count)
            .putLong("${name}_time", System.currentTimeMillis())
            .apply()
    }

    /** 读取最近使用的功能（按次数降序，次数相同按时间降序，最多 max 个） */
    private fun getRecentFunctions(max: Int): List<FuncInfo> {
        val prefs = getSharedPreferences("phonehub_func_usage", Context.MODE_PRIVATE)
        return allFuncList.map { f ->
            Triple(f, prefs.getInt("${f.name}_count", 0), prefs.getLong("${f.name}_time", 0L))
        }.filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Triple<FuncInfo, Int, Long>> { it.second }
                    .thenByDescending { it.third }
            )
            .take(max)
            .map { it.first }
    }

    // ============================== 文件传输 ==============================

    // 文件传输历史条目（time + text + direction），持久化到 SharedPreferences
    private data class FileHistoryItem(val time: Long, val text: String, val direction: String)
    private val fileHistory = mutableListOf<FileHistoryItem>()

    /** 从 SharedPreferences 加载文件传输历史 */
    private fun loadFileHistory() {
        fileHistory.clear()
        val prefs = getSharedPreferences("phonehub_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("file_transfer_history", "") ?: ""
        if (raw.isEmpty()) return
        for (line in raw.split("\n")) {
            if (line.isBlank()) continue
            val parts = line.split("\t", limit = 3)
            if (parts.size == 3) {
                val ts = parts[0].toLongOrNull() ?: continue
                fileHistory.add(FileHistoryItem(ts, parts[2], parts[1]))
            }
        }
    }

    /** 保存文件传输历史到 SharedPreferences */
    private fun saveFileHistory() {
        val prefs = getSharedPreferences("phonehub_prefs", Context.MODE_PRIVATE)
        val raw = fileHistory.joinToString("\n") { "${it.time}\t${it.direction}\t${it.text}" }
        prefs.edit().putString("file_transfer_history", raw).apply()
    }

    /** 追加一条文件传输历史并保存 */
    private fun addFileHistory(text: String, direction: String) {
        // 避免重复记录：检查整个列表中是否已存在相同条目（S1）
        val existing = fileHistory.indexOfFirst { it.text == text && it.direction == direction }
        if (existing >= 0) {
            // 已存在相同记录：移到顶部（最近使用），不重复添加
            val item = fileHistory.removeAt(existing)
            fileHistory.add(0, item.copy(time = System.currentTimeMillis()))
            saveFileHistory()
            refreshFileHistoryList()
            return
        }
        fileHistory.add(0, FileHistoryItem(System.currentTimeMillis(), text, direction))
        if (fileHistory.size > 500) fileHistory.subList(500, fileHistory.size).clear()
        saveFileHistory()
        refreshFileHistoryList()
    }

    /** 刷新文件传输历史 ListView 显示 */
    private fun refreshFileHistoryList() {
        val v = pageCache[1] ?: return
        val list = v.findViewById<ListView>(R.id.fileHistoryList) ?: return
        if (fileHistory.isEmpty()) {
            list.adapter = null
            return
        }
        val displays = fileHistory.map { item ->
            val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.time))
            "[${item.direction}] ${item.text}\n$time"
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displays)
        // 单击传输历史：检查文件是否存在，存在则用系统默认方式打开
        list.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, pos, _ ->
            val item = fileHistory[pos]
            val dir = ConnectionManager.getReceiveDir()
            val file = if (dir != null) File(dir, item.text) else null
            if (file != null && file.exists()) {
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this, "$packageName.fileprovider", file
                    )
                    val ext = item.text.substringAfterLast('.', "").lowercase()
                    val mime = android.webkit.MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(ext) ?: "*/*"
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 重置文件传输界面到初始空闲状态 */
    private fun resetFileTransferUi(v: View) {
        v.findViewById<TextView>(R.id.fileNameText)?.text = "等待传输..."
        v.findViewById<ProgressBar>(R.id.fileProgress)?.progress = 0
        v.findViewById<TextView>(R.id.fileProgressText)?.text = ""
        v.findViewById<TextView>(R.id.fileSpeedText)?.text = ""
        v.findViewById<Button>(R.id.cancelFileBtn)?.isEnabled = false
        v.findViewById<Button>(R.id.pauseFileBtn)?.isEnabled = false
        v.findViewById<Button>(R.id.pauseFileBtn)?.text = "暂停"
        v.findViewById<Button>(R.id.selectFileBtn)?.isEnabled = true
        v.findViewById<Button>(R.id.doneFileBtn)?.isEnabled = false
        v.findViewById<Button>(R.id.doneFileBtn)?.visibility = View.GONE
        v.findViewById<Button>(R.id.doneFileBtn)?.text = "完成"
        v.findViewById<LinearLayout>(R.id.fileProgressContainer)?.visibility = View.GONE
        v.findViewById<LinearLayout>(R.id.fileTransferBtnContainer)?.visibility = View.GONE
    }

    @Suppress("DEPRECATION")
    private fun getFilesView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_files, null)
        v.findViewById<Button>(R.id.selectFileBtn)?.applyDarkTheme(primary = true)
        v.findViewById<Button>(R.id.pauseFileBtn)?.applyDarkTheme()
        v.findViewById<Button>(R.id.cancelFileBtn)?.applyDarkTheme()
        v.findViewById<Button>(R.id.doneFileBtn)?.applyDarkTheme()

        // 首次进入时从 SharedPreferences 加载历史
        if (fileHistory.isEmpty()) loadFileHistory()
        v.post { refreshFileHistoryList() }

        v.findViewById<Button>(R.id.selectFileBtn)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            startActivityForResult(Intent.createChooser(intent, "选择文件"), SELECT_FILE_CODE)
        }

        v.findViewById<Button>(R.id.pauseFileBtn)?.setOnClickListener {
            val btn = v.findViewById<Button>(R.id.pauseFileBtn) ?: return@setOnClickListener
            if (btn.text.toString() == "暂停") {
                // 暂停：中断当前传输并通知 PC
                ConnectionManager.pauseTransfer()
                btn.text = "继续"
                v.findViewById<TextView>(R.id.fileSpeedText)?.text = "已暂停"
            } else {
                // 继续：通知 PC resume 并重新发起传输（从头重发）
                ConnectionManager.resumeTransfer()
                btn.text = "暂停"
                v.findViewById<TextView>(R.id.fileSpeedText)?.text = "继续传输..."
            }
        }

        v.findViewById<Button>(R.id.cancelFileBtn)?.setOnClickListener {
            ConnectionManager.cancelTransfer()
            // Toast 提示用户
            android.widget.Toast.makeText(this@MainActivity, "文件传输已取消", android.widget.Toast.LENGTH_SHORT).show()
            resetFileTransferUi(v)
            v.findViewById<TextView>(R.id.fileNameText)?.text = "已取消"
        }

        v.findViewById<Button>(R.id.doneFileBtn)?.setOnClickListener {
            // "完成" 按钮：将当前传输记录追加到历史并重置界面
            val v2 = pageCache[1] ?: return@setOnClickListener
            val name = v2.findViewById<TextView>(R.id.fileNameText)?.text?.toString() ?: ""
            if (name.isNotEmpty() && name != "等待传输..." && name != "已取消") {
                val direction = if (name.startsWith("发送") || name.startsWith("已发送")) "发送" else "接收"
                val record = name.removePrefix("发送中: ").removePrefix("接收中: ")
                addFileHistory(record, direction)
            }
            resetFileTransferUi(v2)
        }

        // 页面创建时恢复当前传输状态（用户可能在其他页面时传输已开始）
        val currentProgress = ConnectionManager.fileTransferProgress.value
        if (currentProgress != null) {
            v.findViewById<LinearLayout>(R.id.fileProgressContainer)?.visibility = View.VISIBLE
            v.findViewById<LinearLayout>(R.id.fileTransferBtnContainer)?.visibility = View.VISIBLE
            v.findViewById<TextView>(R.id.fileNameText)?.text = currentProgress.fileName
            val pct = if (currentProgress.total > 0) ((currentProgress.sent * 100) / currentProgress.total).toInt() else 0
            v.findViewById<ProgressBar>(R.id.fileProgress)?.progress = pct
            v.findViewById<Button>(R.id.cancelFileBtn)?.isEnabled = true
            v.findViewById<Button>(R.id.pauseFileBtn)?.isEnabled = true
            v.findViewById<Button>(R.id.selectFileBtn)?.isEnabled = false
            v.findViewById<Button>(R.id.doneFileBtn)?.visibility = View.GONE
            v.findViewById<Button>(R.id.doneFileBtn)?.isEnabled = false
            val dirText = if (currentProgress.receiving) "接收中" else "发送中"
            v.findViewById<TextView>(R.id.fileNameText)?.text = "$dirText: ${currentProgress.fileName}"
            fun fmtSize(b: Long): String = when {
                b >= 1024L * 1024 * 1024 -> "%.2f GB".format(b / (1024.0 * 1024 * 1024))
                b >= 1024L * 1024 -> "%.1f MB".format(b / (1024.0 * 1024))
                b >= 1024L -> "%.0f KB".format(b / 1024.0)
                else -> "$b B"
            }
            v.findViewById<TextView>(R.id.fileProgressText)?.text = "${fmtSize(currentProgress.sent)} / ${fmtSize(currentProgress.total)}"
        }

        // 自动记录传输完成的历史（不需要手动点"完成"按钮）
        lifecycleScope.launch {
            ConnectionManager.completedTransfer.collect { transfer ->
                val direction = if (transfer.sending) "发送" else "接收"
                addFileHistory(transfer.fileName, direction)
            }
        }

        // S4: 监听待用户确认接收的文件，显示"开始下载"按钮
        lifecycleScope.launch {
            ConnectionManager.pendingFileReceive.collect { pending ->
                val v2 = pageCache[1] ?: return@collect
                if (pending == null) {
                    // 待确认状态被清除（已开始下载/已取消/已断开）：恢复默认界面
                    resetFileTransferUi(v2)
                    return@collect
                }
                renderPendingFileReceive(v2, pending)
            }
        }
        // 页面创建时同步恢复：进程被杀后从持久化/内存恢复待确认文件，无需等待 SharedFlow replay
        ConnectionManager.getPendingFileTransfer()?.let { renderPendingFileReceive(v, it) }

        return v
    }

    /**
     * 渲染"待确认接收"界面：显示文件名 + "开始下载"/"取消"按钮
     */
    private fun renderPendingFileReceive(v2: View, pending: ConnectionManager.PendingFileTransfer) {
        v2.findViewById<TextView>(R.id.fileNameText)?.text = pending.fileName
        v2.findViewById<LinearLayout>(R.id.fileProgressContainer)?.visibility = View.VISIBLE
        v2.findViewById<LinearLayout>(R.id.fileTransferBtnContainer)?.visibility = View.VISIBLE
        v2.findViewById<ProgressBar>(R.id.fileProgress)?.progress = 0
        v2.findViewById<TextView>(R.id.fileProgressText)?.text = "待确认下载"
        v2.findViewById<TextView>(R.id.fileSpeedText)?.text = ""
        v2.findViewById<Button>(R.id.selectFileBtn)?.isEnabled = false
        v2.findViewById<Button>(R.id.pauseFileBtn)?.isEnabled = false
        v2.findViewById<Button>(R.id.cancelFileBtn)?.isEnabled = true
        v2.findViewById<Button>(R.id.cancelFileBtn)?.text = "取消"
        // 将"完成"按钮改为"开始下载"
        v2.findViewById<Button>(R.id.doneFileBtn)?.text = "开始下载"
        v2.findViewById<Button>(R.id.doneFileBtn)?.visibility = View.VISIBLE
        v2.findViewById<Button>(R.id.doneFileBtn)?.isEnabled = true
        // 重新绑定"开始下载"按钮点击事件
        v2.findViewById<Button>(R.id.doneFileBtn)?.setOnClickListener {
            ConnectionManager.startFileDownloadFromNotification(pending.fileId, pending.fileName, pending.fileSize)
            v2.findViewById<Button>(R.id.doneFileBtn)?.visibility = View.GONE
            v2.findViewById<Button>(R.id.doneFileBtn)?.isEnabled = false
            v2.findViewById<TextView>(R.id.fileProgressText)?.text = "下载中..."
        }
        // "取消"按钮点击：取消待接收
        v2.findViewById<Button>(R.id.cancelFileBtn)?.setOnClickListener {
            ConnectionManager.cancelFileTransferNotification()
            resetFileTransferUi(v2)
        }
    }

    // ============================== 遥控 ==============================

    private fun getRemoteView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_remote, null)
        // 媒体/音量/锁屏按钮（截图按钮单独处理，改为请求电脑截图）
        val buttons = listOf(
            R.id.btnPrev to "media_prev",
            R.id.btnPlayPause to "media_play_pause",
            R.id.btnNext to "media_next",
            R.id.btnVolDown to "vol_down",
            R.id.btnVolUp to "vol_up",
            R.id.btnLock to "lock"
        )
        for ((id, cmd) in buttons) {
            v.findViewById<Button>(id)?.applyDarkTheme(primary = (cmd == "media_play_pause"))
            v.findViewById<Button>(id)?.setOnClickListener {
                ConnectionManager.sendMediaCommand(cmd)
            }
        }

        // 截图按钮：请求电脑端截图当前界面并传回手机（静默，无提示）
        v.findViewById<Button>(R.id.btnScreenshot)?.applyDarkTheme()
        v.findViewById<Button>(R.id.btnScreenshot)?.setOnClickListener {
            try {
                ConnectionManager.requestPcScreenshot()
            } catch (e: Exception) {
                // 静默处理，不弹 Toast
            }
        }
        // 键盘按钮：进入全屏键盘页面
        v.findViewById<Button>(R.id.btnKeyboard)?.applyDarkTheme()
        v.findViewById<Button>(R.id.btnKeyboard)?.setOnClickListener {
            enterKeyboardFullscreen()
        }
        // 媒体信息由电脑端主动推送，手机端被动接收
        val mediaInfoText = v.findViewById<TextView>(R.id.mediaInfoText)
        val mediaCoverImg = v.findViewById<android.widget.ImageView>(R.id.mediaCoverImg)
        // 收集媒体信息文本
        lifecycleScope.launch {
            ConnectionManager.mediaInfo.collect { info ->
                mediaInfoText?.text = info
            }
        }
        // 收集媒体封面图
        lifecycleScope.launch {
            ConnectionManager.mediaThumbnail.collect { bytes ->
                if (bytes != null && bytes.isNotEmpty() && mediaCoverImg != null) {
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        mediaCoverImg.setImageBitmap(bmp)
                        mediaCoverImg.visibility = android.view.View.VISIBLE
                    } else {
                        mediaCoverImg.visibility = android.view.View.GONE
                    }
                } else {
                    mediaCoverImg?.visibility = android.view.View.GONE
                }
            }
        }
        return v
    }

    // ============================== 完整键盘 ==============================

    /**
     * 为包含 page_full_keyboard.xml 布局的根视图绑定完整键盘逻辑。
     * 修饰键 Ctrl/Alt/Shift：点击切换锁定/解锁（高亮），只有再次点击才取消，不受其他按键影响。
     * 复用于：完整键盘页、远程控制页底部键盘。
     */
    private fun wireFullKeyboard(rootView: View) {
        val modifierStatus = rootView.findViewById<TextView>(R.id.modifierStatus)
        val lockedModifiers = mutableSetOf<String>()   // 锁定的修饰键（点击切换）

        fun updateModifierStatus() {
            val locked = lockedModifiers.joinToString("+") { "$it(锁)" }
            modifierStatus?.text = "修饰键: ${locked.ifEmpty { "无" }}"
        }

        fun sendKey(key: String) {
            val modStr = if (lockedModifiers.isEmpty()) "" else lockedModifiers.joinToString("+") + "+"
            ConnectionManager.sendMediaCommand("key_$modStr$key")
        }

        // 字母 / 数字 / 标点 / 功能键 / 方向键
        val keyMap = mapOf(
            R.id.k_1 to "1", R.id.k_2 to "2", R.id.k_3 to "3", R.id.k_4 to "4", R.id.k_5 to "5",
            R.id.k_6 to "6", R.id.k_7 to "7", R.id.k_8 to "8", R.id.k_9 to "9", R.id.k_0 to "0",
            R.id.k_q to "q", R.id.k_w to "w", R.id.k_e to "e", R.id.k_r to "r", R.id.k_t to "t",
            R.id.k_y to "y", R.id.k_u to "u", R.id.k_i to "i", R.id.k_o to "o", R.id.k_p to "p",
            R.id.k_a to "a", R.id.k_s to "s", R.id.k_d to "d", R.id.k_f to "f", R.id.k_g to "g",
            R.id.k_h to "h", R.id.k_j to "j", R.id.k_k to "k", R.id.k_l to "l",
            R.id.k_z to "z", R.id.k_x to "x", R.id.k_c to "c", R.id.k_v to "v", R.id.k_b to "b",
            R.id.k_n to "n", R.id.k_m to "m", R.id.k_comma to ",", R.id.k_dot to ".",
            R.id.k_space to "space", R.id.k_enter to "enter", R.id.k_bksp to "backspace",
            R.id.k_up to "up", R.id.k_down to "down", R.id.k_left to "left", R.id.k_right to "right",
            R.id.k_f1 to "f1", R.id.k_f2 to "f2", R.id.k_f3 to "f3", R.id.k_f4 to "f4",
            R.id.k_f5 to "f5", R.id.k_f6 to "f6", R.id.k_f7 to "f7", R.id.k_f8 to "f8",
            R.id.k_f9 to "f9", R.id.k_f10 to "f10", R.id.k_f11 to "f11", R.id.k_f12 to "f12",
            // 新增按键：Escape, Tab, Home, End, PgUp, PgDown
            R.id.k_escape to "escape", R.id.k_tab to "tab", R.id.k_home to "home",
            R.id.k_end to "end", R.id.k_pgup to "page_up", R.id.k_pgdn to "page_down"
        )
        for ((id, key) in keyMap) {
            rootView.findViewById<Button>(id)?.setOnClickListener { sendKey(key) }
        }

        // 修饰键：点击切换锁定/解锁，锁定态高亮
        val modMap = mapOf(
            R.id.k_shift to "shift",
            R.id.k_ctrl to "ctrl",
            R.id.k_alt to "alt",
            R.id.k_win to "win"
        )
        for ((id, mod) in modMap) {
            val btn = rootView.findViewById<Button>(id)
            btn?.setOnClickListener {
                if (lockedModifiers.contains(mod)) {
                    lockedModifiers.remove(mod)
                    btn.setBackgroundResource(R.drawable.kb_modifier_bg)
                } else {
                    lockedModifiers.add(mod)
                    btn.setBackgroundColor(0xFF3a6fc5.toInt())
                }
                updateModifierStatus()
            }
            btn?.setOnLongClickListener(null)
        }
    }

    private fun enterKeyboardFullscreen() {
        if (isKeyboardFullscreen) return
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        val win = window
        androidx.core.view.WindowInsetsControllerCompat(win, win.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        supportActionBar?.hide()

        // 隐藏 setupScreen，显示 pageContainer 作为键盘容器
         val setupScreen = findViewById<LinearLayout>(R.id.setupScreen)
         val pageContainer = findViewById<FrameLayout>(R.id.pageContainer)
         
         setupScreen.visibility = View.GONE
         pageContainer.visibility = View.VISIBLE
         pageContainer.removeAllViews() // Clear current page
         
         // 创建全屏键盘视图并添加到 pageContainer（使用横屏布局）
         val keyboardView = getFullKeyboardView(landscapeMode = true)
        pageContainer.addView(keyboardView)

        isKeyboardFullscreen = true
    }

    private fun exitKeyboardFullscreen() {
        if (!isKeyboardFullscreen) return
        isKeyboardFullscreen = false

        val win = window
        androidx.core.view.WindowInsetsControllerCompat(win, win.decorView).apply {
            show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        supportActionBar?.show()

        val pageContainer = findViewById<FrameLayout>(R.id.pageContainer)
        
        // Restore tab UI and setup screen visibility
        setupScreen.visibility = View.VISIBLE
        pageContainer.visibility = View.GONE
        
        // 恢复首页内容到 pageContainer（与 enterKeyboardFullscreen 对应）
        restoreHomeScreen()
    }

    private fun getFullKeyboardView(@Suppress("UNUSED_PARAMETER") landscapeMode: Boolean = false): View {
        val v: View
        val layoutResId: Int
        // Simple: always use page_full_keyboard (works in both orientations)
        // For true landscape support, consider using layout-land resources properly
        layoutResId = R.layout.page_full_keyboard
        v = LayoutInflater.from(this).inflate(layoutResId, null)
        wireFullKeyboard(v)
        return v
    }

    private fun restoreHomeScreen() {
        // 退出键盘全屏后，恢复首页导航网格
        val pageContainer = findViewById<FrameLayout>(R.id.pageContainer)
        
        // 清理当前页面容器
        pageContainer.removeAllViews()
        
        // 展示首页 Tab 0（实际显示功能网格）
        val homeView = getPageView(0)
        pageContainer.addView(homeView)
        
        // 设置当前标签为首页
        currentTab = 0
        
        // setupScreen 已可见，pageContainer 隐藏，保持默认状态
    }

    // ============================== 剪贴板（已合并历史/收藏）==============================

    // 剪贴板页内 历史/收藏 切换状态
    private var clipViewMode = "history"  // history / favorite

    /**
     * 合并后的剪贴板页：
     * - 上方：当前剪贴板内容显示 + 复制/发送按钮
     * - 下方：剪贴板历史列表（带搜索、来源标注、收藏标记），点击复制，长按收藏
     */
    private fun getClipboardView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1e1e1e.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // ===== 标题 =====
        val title = TextView(this).apply {
            text = "剪贴板"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(title)

        // ===== 上方：当前剪贴板内容 =====
        val topBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF2d2d2d.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val curLabel = TextView(this).apply {
            text = "当前剪贴板:"
            setTextColor(0xFFb0b0b0.toInt())
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        }
        // 复用 R.id.currentClipText，便于 receivedClipboard flow / onResume 更新
        val currentClipText = TextView(this).apply {
            id = R.id.currentClipText
            text = ""
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            minHeight = dp(60)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(0xFF1e1e1e.toInt())
        }
        // 初始化为系统剪贴板当前内容
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
            if (t.isNotEmpty()) currentClipText.text = t
        } catch (e: Exception) {
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        val copyClipBtn = Button(this).apply {
            text = "复制内容"
            applyDarkTheme()
        }
        val sendClipBtn = Button(this).apply {
            text = "推送到电脑"
            applyDarkTheme(primary = true)
        }
        btnRow.addView(copyClipBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
        btnRow.addView(sendClipBtn, LinearLayout.LayoutParams(0, dp(48), 1f))

        copyClipBtn.setOnClickListener {
            val text = currentClipText.text?.toString() ?: ""
            if (text.isNotEmpty()) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("PhoneHub", text))
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
            }
        }
        sendClipBtn.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
            if (text.isNotEmpty()) {
                ConnectionManager.sendClipboard(text)
                Toast.makeText(this, "已推送", Toast.LENGTH_SHORT).show()
            }
        }

        topBox.addView(curLabel)
        topBox.addView(currentClipText)
        topBox.addView(btnRow)
        root.addView(topBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        // ===== 下方：剪贴板历史 / 收藏 =====
        val histTitle = TextView(this).apply {
            text = "剪贴板历史 / 收藏"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(histTitle)

        // 搜索 + 历史/收藏切换行
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF2d2d2d.toInt())
            setPadding(dp(8), dp(8), dp(8), dp(8)
            )
        }
        val search = EditText(this).apply {
            hint = "搜索..."
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF666666.toInt())
            setBackgroundColor(0xFF1e1e1e.toInt())
            setPadding(dp(8), 0, dp(8), 0)
            textSize = 12f
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val btnHist = Button(this).apply { text = "历史" }
        val btnFav = Button(this).apply { text = "收藏" }
        btnHist.applyDarkTheme(primary = true)
        btnFav.applyDarkTheme()
        searchRow.addView(search, LinearLayout.LayoutParams(0, dp(40), 1f))
        searchRow.addView(btnHist, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)
        ).apply { leftMargin = dp(4) })
        searchRow.addView(btnFav, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)
        ).apply { leftMargin = dp(4) })
        root.addView(searchRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        // 历史列表
        val list = ListView(this)
        val empty = TextView(this).apply {
            text = "暂无记录"
            setTextColor(0xFF666666.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        root.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0
        ).apply { weight = 1f })
        root.addView(empty)

        // 列表刷新逻辑
        fun refresh() {
            val q = search.text?.toString() ?: ""
            val items = if (clipViewMode == "history") {
                if (q.isEmpty()) ConnectionManager.clipboardHistory.value
                else ConnectionManager.searchClipboardHistory(q)
            } else {
                if (q.isEmpty()) ConnectionManager.clipboardFavorites.value
                else ConnectionManager.searchClipboardFavorites(q)
            }
            if (items.isEmpty()) {
                empty.visibility = View.VISIBLE
                empty.text = if (clipViewMode == "history") "暂无历史" else "暂无收藏"
                list.adapter = null
            } else {
                empty.visibility = View.GONE
                // 显示内容 + 来源标注 + 收藏标记
                val displays = items.map {
                    val favMark = if (it.favorite) " ★" else ""
                    "${it.content.take(80)}\n[${it.source}] ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.timestamp))}$favMark"
                }
                list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displays)
                // 点击历史条目直接复制到系统剪贴板
                list.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, pos, _ ->
                    val item = items[pos]
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("PhoneHub", item.content))
                    Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
                }
                // 长按切换收藏
                list.onItemLongClickListener = android.widget.AdapterView.OnItemLongClickListener { _, _, pos, _ ->
                    val item = items[pos]
                    ConnectionManager.toggleFavorite(item)
                    Toast.makeText(this, if (!item.favorite) "已收藏" else "取消收藏", Toast.LENGTH_SHORT).show()
                    refresh()
                    true
                }
            }
        }

        btnHist.setOnClickListener {
            clipViewMode = "history"
            btnHist.applyDarkTheme(primary = true)
            btnFav.applyDarkTheme()
            refresh()
        }
        btnFav.setOnClickListener {
            clipViewMode = "favorite"
            btnFav.applyDarkTheme(primary = true)
            btnHist.applyDarkTheme()
            refresh()
        }
        search.setOnEditorActionListener { _, _, _ -> refresh(); true }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refresh()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 初次进入刷新
        list.post { refresh() }
        return root
    }

    // ============================== 路线图 ==============================

    private fun getLocationView(): View {
        // 路线图功能暂未开放，显示占位页面（避免白屏/闪退）
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF1e1e1e.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        val title = TextView(this).apply {
            text = "移动路线图"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            setPadding(0, 0, 0, dp(32))
        }
        val hint = TextView(this).apply {
            text = "该功能暂未开放，敬请期待"
            setTextColor(0xFFb0b0b0.toInt())
            textSize = 16f
        }
        container.addView(title)
        container.addView(hint)
        return container
    }

    // ============================== 截图 ==============================

    private fun getScreenshotView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_screenshot, null)
        v.findViewById<Button>(R.id.btnTakeScreenshot)?.applyDarkTheme(primary = true)

        v.findViewById<Button>(R.id.btnTakeScreenshot)?.setOnClickListener {
            ConnectionManager.triggerScreenshot()
        }
        refreshScreenshotList(v)
        return v
    }

    private fun refreshScreenshotList(v: View) {
        val list = v.findViewById<ListView>(R.id.screenshotList)
        val empty = v.findViewById<TextView>(R.id.screenshotEmpty)
        // 扫描应用内部目录（PC截图和手机截图临时文件）
        val localDir = File(getExternalFilesDir(null), "Received")
        val files = mutableListOf<File>()
        // 手机截图回传临时文件：screenshot_*.png
        localDir.listFiles { f -> f.name.endsWith(".png") && f.name.startsWith("screenshot_") }
            ?.let { files.addAll(it) }
        val sortedFiles = files.sortedByDescending { it.lastModified() }
        if (sortedFiles.isEmpty()) {
            empty.visibility = View.VISIBLE
            list.adapter = null
        } else {
            empty.visibility = View.GONE
            val names = sortedFiles.map { "${it.name}\n${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.lastModified()))}" }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
            list.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, pos, _ ->
                showScreenshotViewer(sortedFiles[pos])
            }
        }
    }

    // ============================== 截图批注 ==============================

    // 绘图操作数据类（顶层定义，Kotlin 1.8 内部类不支持 data class）
    private class BrushOp(val path: android.graphics.Path, val color: Int, val width: Float)
    private class ArrowOp(val sx: Float, val sy: Float, var ex: Float, var ey: Float, val color: Int, val width: Float)
    private class RectOp(val rect: android.graphics.RectF, val color: Int, val width: Float)
    private class TextOp(val text: String, val x: Float, val y: Float, val color: Int, val size: Float)
    private class MosaicOp(val points: MutableList<Pair<Float, Float>>)

    inner class AnnotationOverlayView(ctx: Context, val srcBitmap: android.graphics.Bitmap) : View(ctx) {
        private val operations = mutableListOf<Any>()
        private var currentOp: Any? = null
        var currentTool = "brush"
        private var scale = 1f
        private var offsetX = 0f
        private var offsetY = 0f
        private var mosaicBmp: android.graphics.Bitmap? = null

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0 && srcBitmap.width > 0) {
                scale = minOf(w.toFloat() / srcBitmap.width, h.toFloat() / srcBitmap.height)
                offsetX = (w - srcBitmap.width * scale) / 2f
                offsetY = (h - srcBitmap.height * scale) / 2f
                // 生成马赛克用的像素化位图
                val smallW = (srcBitmap.width / 15).coerceAtLeast(1)
                val smallH = (srcBitmap.height / 15).coerceAtLeast(1)
                val small = android.graphics.Bitmap.createScaledBitmap(srcBitmap, smallW, smallH, false)
                mosaicBmp = android.graphics.Bitmap.createScaledBitmap(small, srcBitmap.width, srcBitmap.height, false)
            }
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            canvas.save()
            canvas.translate(offsetX, offsetY)
            canvas.scale(scale, scale)
            canvas.drawBitmap(srcBitmap, 0f, 0f, null)
            for (op in operations) drawOp(canvas, op)
            currentOp?.let { drawOp(canvas, it) }
            canvas.restore()
        }

        private fun drawOp(canvas: android.graphics.Canvas, op: Any) {
            when (op) {
                is BrushOp -> {
                    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    p.color = op.color; p.style = android.graphics.Paint.Style.STROKE
                    p.strokeWidth = op.width; p.strokeCap = android.graphics.Paint.Cap.ROUND
                    p.strokeJoin = android.graphics.Paint.Join.ROUND
                    canvas.drawPath(op.path, p)
                }
                is ArrowOp -> {
                    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    p.color = op.color; p.style = android.graphics.Paint.Style.STROKE
                    p.strokeWidth = op.width; p.strokeCap = android.graphics.Paint.Cap.ROUND
                    canvas.drawLine(op.sx, op.sy, op.ex, op.ey, p)
                    val angle = Math.atan2((op.ey - op.sy).toDouble(), (op.ex - op.sx).toDouble())
                    val len = 40f
                    canvas.drawLine(op.ex, op.ey,
                        (op.ex - len * Math.cos(angle - Math.PI / 6)).toFloat(),
                        (op.ey - len * Math.sin(angle - Math.PI / 6)).toFloat(), p)
                    canvas.drawLine(op.ex, op.ey,
                        (op.ex - len * Math.cos(angle + Math.PI / 6)).toFloat(),
                        (op.ey - len * Math.sin(angle + Math.PI / 6)).toFloat(), p)
                }
                is RectOp -> {
                    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    p.color = op.color; p.style = android.graphics.Paint.Style.STROKE
                    p.strokeWidth = op.width
                    canvas.drawRect(op.rect, p)
                }
                is TextOp -> {
                    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    p.color = op.color; p.textSize = op.size
                    p.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    canvas.drawText(op.text, op.x, op.y, p)
                }
                is MosaicOp -> {
                    mosaicBmp?.let { mb ->
                        val radius = 25f
                        for ((x, y) in op.points) {
                            val srcLeft = (x - radius).toInt().coerceAtLeast(0)
                            val srcTop = (y - radius).toInt().coerceAtLeast(0)
                            val srcRight = (x + radius).toInt().coerceAtMost(srcBitmap.width)
                            val srcBottom = (y + radius).toInt().coerceAtMost(srcBitmap.height)
                            if (srcRight > srcLeft && srcBottom > srcTop) {
                                val srcRect = android.graphics.Rect(srcLeft, srcTop, srcRight, srcBottom)
                                val dstRect = android.graphics.RectF(srcLeft.toFloat(), srcTop.toFloat(), srcRight.toFloat(), srcBottom.toFloat())
                                canvas.drawBitmap(mb, srcRect, dstRect, null)
                            }
                        }
                    }
                }
            }
        }

        fun toBitmapX(viewX: Float) = (viewX - offsetX) / scale
        fun toBitmapY(viewY: Float) = (viewY - offsetY) / scale

        fun undo() { if (operations.isNotEmpty()) { operations.removeAt(operations.lastIndex); invalidate() } }
        fun hasOperations() = operations.isNotEmpty()

        fun exportBitmap(): android.graphics.Bitmap {
            val result = android.graphics.Bitmap.createBitmap(srcBitmap.width, srcBitmap.height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(result)
            canvas.drawBitmap(srcBitmap, 0f, 0f, null)
            for (op in operations) drawOp(canvas, op)
            return result
        }

        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            val bx = toBitmapX(event.x)
            val by = toBitmapY(event.y)
            val sw = 6f / scale  // 统一笔宽（适应缩放）
            when (currentTool) {
                "brush" -> when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> { currentOp = BrushOp(android.graphics.Path().apply { moveTo(bx, by) }, 0xFFFF0000.toInt(), sw) }
                    android.view.MotionEvent.ACTION_MOVE -> { (currentOp as? BrushOp)?.path?.lineTo(bx, by); invalidate() }
                    android.view.MotionEvent.ACTION_UP -> { (currentOp as? BrushOp)?.path?.lineTo(bx, by); currentOp?.let { operations.add(it) }; currentOp = null; invalidate() }
                }
                "arrow" -> when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> { currentOp = ArrowOp(bx, by, bx, by, 0xFFFF0000.toInt(), sw) }
                    android.view.MotionEvent.ACTION_MOVE -> { (currentOp as? ArrowOp)?.let { it.ex = bx; it.ey = by }; invalidate() }
                    android.view.MotionEvent.ACTION_UP -> { (currentOp as? ArrowOp)?.let { it.ex = bx; it.ey = by }; currentOp?.let { operations.add(it) }; currentOp = null; invalidate() }
                }
                "rect" -> when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> { currentOp = RectOp(android.graphics.RectF(bx, by, bx, by), 0xFFFF0000.toInt(), sw) }
                    android.view.MotionEvent.ACTION_MOVE -> { (currentOp as? RectOp)?.rect?.apply { left = minOf(left, bx); top = minOf(top, by); right = maxOf(right, bx); bottom = maxOf(bottom, by) }; invalidate() }
                    android.view.MotionEvent.ACTION_UP -> { currentOp?.let { operations.add(it) }; currentOp = null; invalidate() }
                }
                "mosaic" -> when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> { currentOp = MosaicOp(mutableListOf(bx to by)) }
                    android.view.MotionEvent.ACTION_MOVE -> { (currentOp as? MosaicOp)?.points?.add(bx to by); invalidate() }
                    android.view.MotionEvent.ACTION_UP -> { (currentOp as? MosaicOp)?.points?.add(bx to by); currentOp?.let { operations.add(it) }; currentOp = null; invalidate() }
                }
                "text" -> if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    val input = android.widget.EditText(this@MainActivity)
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("输入文字").setView(input)
                        .setPositiveButton("确定") { _, _ ->
                            val text = input.text.toString()
                            if (text.isNotEmpty()) { operations.add(TextOp(text, bx, by, 0xFFFF0000.toInt(), 40f)); invalidate() }
                        }.setNegativeButton("取消", null).show()
                }
            }
            return true
        }
    }

    private fun showScreenshotViewer(file: File) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_screenshot_viewer, null)
        v.findViewById<Button>(R.id.btnAnnotate).applyDarkTheme()
        v.findViewById<Button>(R.id.btnUndo).applyDarkTheme()
        v.findViewById<Button>(R.id.btnSavePng).applyDarkTheme(primary = true)
        v.findViewById<Button>(R.id.btnSendToPc).applyDarkTheme()
        v.findViewById<Button>(R.id.btnClose).applyDarkTheme()
        v.findViewById<Button>(R.id.toolBrush).applyDarkTheme()
        v.findViewById<Button>(R.id.toolText).applyDarkTheme()
        v.findViewById<Button>(R.id.toolArrow).applyDarkTheme()
        v.findViewById<Button>(R.id.toolRect).applyDarkTheme()
        v.findViewById<Button>(R.id.toolMosaic).applyDarkTheme()

        val img = v.findViewById<android.widget.ImageView>(R.id.screenshotImage)
        val originalBmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        if (originalBmp != null) img.setImageBitmap(originalBmp)

        val overlayPlaceholder = v.findViewById<View>(R.id.annotationOverlay)
        val toolbar = v.findViewById<android.widget.LinearLayout>(R.id.annotationToolbar)
        val frameParent = overlayPlaceholder.parent as android.widget.FrameLayout

        // 创建批注视图（替换占位 View）
        var annotationView: AnnotationOverlayView? = null
        var annotationMode = false
        val toolButtons = mapOf(
            "brush" to v.findViewById<Button>(R.id.toolBrush),
            "text" to v.findViewById<Button>(R.id.toolText),
            "arrow" to v.findViewById<Button>(R.id.toolArrow),
            "rect" to v.findViewById<Button>(R.id.toolRect),
            "mosaic" to v.findViewById<Button>(R.id.toolMosaic)
        )

        fun selectTool(tool: String) {
            toolButtons.forEach { (name, btn) ->
                if (name == tool) btn.setBackgroundColor(0xFF3a6fc5.toInt())
                else btn.setBackgroundResource(android.R.drawable.btn_default)
            }
            annotationView?.currentTool = tool
        }

        fun setAnnotationMode(on: Boolean) {
            annotationMode = on
            toolbar.visibility = if (on) View.VISIBLE else View.GONE
            if (on && annotationView == null && originalBmp != null) {
                frameParent.removeView(overlayPlaceholder)
                annotationView = AnnotationOverlayView(this, originalBmp).apply {
                    visibility = View.VISIBLE
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                frameParent.addView(annotationView)
                selectTool("brush")
            }
            annotationView?.visibility = if (on) View.VISIBLE else View.GONE
            v.findViewById<Button>(R.id.btnAnnotate).text = if (on) "退出批注" else "批注"
        }

        v.findViewById<Button>(R.id.btnAnnotate).setOnClickListener { setAnnotationMode(!annotationMode) }
        toolButtons.forEach { (tool, btn) -> btn.setOnClickListener { selectTool(tool) } }

        v.findViewById<Button>(R.id.btnUndo).setOnClickListener {
            annotationView?.undo()
            if (annotationView?.hasOperations() != true) {
                Toast.makeText(this, "无可撤销操作", Toast.LENGTH_SHORT).show()
            }
        }

        v.findViewById<Button>(R.id.btnSavePng).setOnClickListener {
            val bmpToSave = if (annotationView != null && annotationView!!.hasOperations()) {
                annotationView!!.exportBitmap()
            } else {
                originalBmp
            }
            if (bmpToSave != null) {
                try {
                    val saveName = if (annotationView?.hasOperations() == true) {
                        file.nameWithoutExtension + "_annotated.png"
                    } else { file.name }
                    val saveDir = File(getExternalFilesDir(null), "Received")
                    val saveFile = File(saveDir, saveName)
                    val fos = java.io.FileOutputStream(saveFile)
                    bmpToSave.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
                    fos.close()
                    Toast.makeText(this, "已保存: ${saveFile.absolutePath}", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        v.findViewById<Button>(R.id.btnSendToPc).setOnClickListener {
            val sendFile = if (annotationView != null && annotationView!!.hasOperations()) {
                try {
                    val tmpFile = File(getExternalFilesDir(null), "annotated_tmp.png")
                    val fos = java.io.FileOutputStream(tmpFile)
                    annotationView!!.exportBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
                    fos.close()
                    tmpFile
                } catch (e: Exception) { file }
            } else { file }
            ConnectionManager.sendFile(sendFile)
            Toast.makeText(this, "已开始回传", Toast.LENGTH_SHORT).show()
        }

        val dialog = AlertDialog.Builder(this).setView(v).setCancelable(true).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        v.findViewById<Button>(R.id.btnClose).setOnClickListener { dialog.dismiss() }
    }

    // ============================== 投屏 ==============================

    private fun getMirrorView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_screen_mirror, null)

        // ===== 手机 → 电脑投屏（参考工程那套引擎，端口 5423）=====
        val mirStatus = v.findViewById<TextView>(R.id.mirStatus)
        val mirFps = v.findViewById<TextView>(R.id.mirFps)
        val ipEdit = v.findViewById<android.widget.EditText>(R.id.mirPcIp)
        val portEdit = v.findViewById<android.widget.EditText>(R.id.mirPort)
        mirrorUiStatus = mirStatus
        mirrorUiFps = mirFps
        ipEdit.setText(defaultMirrorIp())
        portEdit.setText("5423")
        setupMirrorSpinners(v)

        // 服务回传的状态/帧率（参考工程是 LocalBroadcastManager 广播，这里换成 StateFlow）
        lifecycleScope.launch {
            PhoneHubMirrorService.resultFlow.collect { msg ->
                if (msg.isNotEmpty()) mirStatus.text = msg
            }
        }
        lifecycleScope.launch {
            PhoneHubMirrorService.fpsFlow.collect { txt ->
                if (txt.isNotEmpty()) mirFps.text = txt
            }
        }

        v.findViewById<Button>(R.id.mirBtnConnect)?.setOnClickListener {
            connectMirrorPc(ipEdit.text.toString().trim(), portEdit.text.toString().toIntOrNull() ?: 5423)
        }
        v.findViewById<Button>(R.id.mirBtnStart)?.setOnClickListener {
            startMirrorFlow(ipEdit.text.toString().trim(), portEdit.text.toString().toIntOrNull())
        }
        v.findViewById<Button>(R.id.mirBtnStop)?.setOnClickListener { stopMirrorFlow() }
        v.findViewById<Button>(R.id.mirBtnClearCache)?.setOnClickListener {
            PhoneHubMirrorService.clearProjectionResult(this)
            Toast.makeText(this, "已清除授权缓存，下次投屏需要重新授权", Toast.LENGTH_SHORT).show()
            setMirrorStatus("授权缓存已清除")
        }
        v.findViewById<Button>(R.id.mirBtnAccessibility)?.setOnClickListener {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(this, "找到「PhoneHub」并开启，反向控制才能注入点击", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
            }
        }
        v.findViewById<Button>(R.id.mirBtnDisconnectAll)?.setOnClickListener {
            PhoneHubMirrorService.clearSavedPcEndpoints(this)
            setMirrorStatus("已清空电脑列表（下次投屏前请重新连接）")
        }

        // Find UI components
        val fullscreenBtn = v.findViewById<Button>(R.id.btnFullscreen)
        // 全屏按钮：恢复显示，点击进入全屏投屏（Task 18.5）
        fullscreenBtn?.visibility = View.VISIBLE

        val status = v.findViewById<TextView>(R.id.mirrorStatus)
        val mirrorFrame = v.findViewById<FrameLayout>(R.id.mirrorFrame)

        // ===== 电脑→手机投屏：自动启动（S7）=====
        // Auto-start PC screen mirroring when page is loaded, removing redundant btnFullscreen button per S7
        status.text = "正在连接电脑画面..."
        ConnectionManager.startPcFramePolling(controlMode = true)
        mirrorFrame.visibility = View.VISIBLE
        status.text = "已连接 - 正在查看电脑画面..."

        // 全屏：点击最大化投屏区域，隐藏页面其他组件
        fullscreenBtn?.setOnClickListener {
            val hideViews = ArrayList<View>()
            (v as? android.view.ViewGroup)?.let { rg ->
                for (i in 0 until rg.childCount) {
                    val child = rg.getChildAt(i)
                    if (child !== mirrorFrame) hideViews.add(child)
                }
            }
            enterMirrorFullscreen(mirrorFrame, hideViews)
        }

        // 全屏退出按钮（Task 18.5：角落半透明退出按钮）
        val fullscreenExitBtn = v.findViewById<Button>(R.id.btnMirrorFullscreenExit)
        fullscreenExitBtn?.setOnClickListener {
            exitMirrorFullscreen()
        }

        // 停止接收电脑投屏
        v.findViewById<Button>(R.id.btnStopPcStream)?.setOnClickListener {
            ConnectionManager.sendMediaCommand("pc_stream_stop")
            ConnectionManager.stopPcFramePolling()
            mirrorImageView?.setImageBitmap(null)
            mirrorFrame.visibility = View.GONE
            status.text = "已停止接收电脑画面"
        }

        // 电脑画面显示区域（动态创建 ImageView）
        val frameImg = android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER  // 保持原始比例，居中显示
            setBackgroundColor(0xFF000000.toInt())
            minimumHeight = 400
        }
        mirrorImageView = frameImg

        // 操控模式：触摸事件转发给电脑（模拟触摸屏 + 鼠标指针跟随）
        var touchStartTime = 0L
        var longPressDone = false
        var lastMoveSendTime = 0L  // Task 18.3：节流，降低操控延迟
        val moveThrottleMs = 16L   // ~60fps 上限，避免洪泛发送
        frameImg.setOnTouchListener { _, event ->
            if (!ConnectionManager.pcFrameControlMode) return@setOnTouchListener false
            // 精确计算归一化坐标（考虑 FIT_CENTER 的 letterbox）
            val drawable = frameImg.drawable
            var normX = 0.5f
            var normY = 0.5f
            if (drawable != null && drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                val imgW = drawable.intrinsicWidth.toFloat()
                val imgH = drawable.intrinsicHeight.toFloat()
                val viewW = frameImg.width.toFloat()
                val viewH = frameImg.height.toFloat()
                val scale = minOf(viewW / imgW, viewH / imgH)
                val realImgW = imgW * scale
                val realImgH = imgH * scale
                val offsetX = (viewW - realImgW) / 2f
                val offsetY = (viewH - realImgH) / 2f
                normX = ((event.x - offsetX) / realImgW).coerceIn(0f, 1f)
                normY = ((event.y - offsetY) / realImgH).coerceIn(0f, 1f)
            } else if (frameImg.width > 0 && frameImg.height > 0) {
                normX = (event.x / frameImg.width).coerceIn(0f, 1f)
                normY = (event.y / frameImg.height).coerceIn(0f, 1f)
            }
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    touchStartTime = System.currentTimeMillis()
                    longPressDone = false
                    ConnectionManager.sendAction("screen_click", mapOf(
                        "x" to normX, "y" to normY, "op" to "down"
                    ))
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (!longPressDone) {
                        val now = System.currentTimeMillis()
                        // 长按超过2秒触发右键
                        if (now - touchStartTime > 2000) {
                            longPressDone = true
                            ConnectionManager.sendAction("screen_click", mapOf(
                                "x" to normX, "y" to normY, "op" to "up"
                            ))
                            ConnectionManager.sendAction("screen_click", mapOf(
                                "x" to normX, "y" to normY, "op" to "right"
                            ))
                        } else if (now - lastMoveSendTime >= moveThrottleMs) {
                            // Task 18.3：节流发送 move 事件，减少不必要的 delay
                            lastMoveSendTime = now
                            ConnectionManager.sendAction("screen_click", mapOf(
                                "x" to normX, "y" to normY, "op" to "move"
                            ))
                        }
                    }
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (!longPressDone) {
                        ConnectionManager.sendAction("screen_click", mapOf(
                            "x" to normX, "y" to normY, "op" to "up"
                        ))
                    }
                }
            }
            true
        }
        // 将 ImageView 添加到页面（插入到状态文本后面）
        val parent = status.parent as? android.view.ViewGroup
        val statusIndex = parent?.indexOfChild(status) ?: -1
        if (parent != null && statusIndex >= 0) {
            parent.addView(frameImg, statusIndex + 1)
        }

        return v
    }

    // ============================== 摄像头 ==============================

    private fun getCameraView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_camera, null)
        val btnStart = v.findViewById<Button>(R.id.btnCameraStart)
        val btnSwitch = v.findViewById<Button>(R.id.btnCameraSwitch)
        // btnPcCam was removed per M12/S8c
        btnStart?.applyDarkTheme()
        btnSwitch?.applyDarkTheme()

        val status = v.findViewById<TextView>(R.id.cameraStatus)
        val previewView = v.findViewById<androidx.camera.view.PreviewView>(R.id.cameraPreview)
        cameraPreviewView = previewView
        
        // 设置按钮初始文本 per S8c
        btnStart?.text = "启动推流"

        // S8b: 初始化摄像头ImageView用于显示电脑摄像头画面
        val pcCameraImg = android.widget.ImageView(this)
        pcCameraImg.id = View.generateViewId()
        pcCameraImg.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        pcCameraImg.visibility = android.view.View.GONE
        // 添加到根布局
        if (v is android.view.ViewGroup) {
            v.addView(pcCameraImg)
        }
        cameraImageView = pcCameraImg

        btnStart?.setOnClickListener {
            if (cameraPreviewRunning) {
                stopCameraPush()
            } else {
                startCameraPush()
            }
        }

        // S8c: 长按按钮弹出菜单，包含电脑摄像头控制选项
        btnStart?.setOnLongClickListener {
            showCameraControlPopup()
            true
        }

        // S8a: 收集摄像头推送命令（启动/停止推流），用于处理电脑端请求
        lifecycleScope.launch {
            ConnectionManager.cameraPushCommand.collect { command ->
                when (command.action) {
                    "start" -> startCameraPush()
                    "stop" -> stopCameraPush()
                    else -> {}
                }
            }
        }

        // 本地切换摄像头
        fun doSwitchCamera() {
            cameraLensFacing = when (cameraLensFacing) {
                androidx.camera.core.CameraSelector.LENS_FACING_BACK ->
                    androidx.camera.core.CameraSelector.LENS_FACING_FRONT
                else -> androidx.camera.core.CameraSelector.LENS_FACING_BACK
            }
            // Restart preview to apply new lens facing
            if (cameraPreviewRunning && cameraInstance != null) {
                stopCameraPreview()
                startCameraPreview(1920, 1080, previewView)
            }
            status.text = "镜头已切换: ${if (cameraLensFacing == androidx.camera.core.CameraSelector.LENS_FACING_BACK) "后置" else "前置"}"
        }

        btnSwitch?.setOnClickListener { doSwitchCamera() }

        // Removed btnCameraStop (分享相机画面 button) per M12/S8c

        // S8b: 收集电脑摄像头推流帧（收到PC发送的画面后显示）
        lifecycleScope.launch {
            ConnectionManager.pcCameraFrame.collect { frameData ->
                if (frameData.isNotEmpty() && cameraImageView != null) {
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(frameData, 0, frameData.size)
                    if (bmp != null) {
                        cameraImageView?.setImageBitmap(bmp)
                        cameraImageView?.visibility = View.VISIBLE
                    } else {
                        cameraImageView?.visibility = View.GONE
                    }
                } else {
                    cameraImageView?.visibility = View.GONE
                }
            }
        }

        return v

    }

    // ============================== S8c: 摄像头控制弹窗 ==============================

    /**
     * S8c: 弹出摄像头控制对话框，包含电脑摄像头启动/停止选项
     */
    private fun showCameraControlPopup() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("摄像头选项")
        builder.setNegativeButton("取消", null)

        // 动态创建列表项：电脑摄像头推流 启动/停止（按当前状态显示）
        val pcCamRunning = ConnectionManager.isPcCameraPolling()
        val options = arrayOf(
            if (pcCamRunning) "🛑 停止电脑摄像头推流" else "📷 启动电脑摄像头推流"
        )
        
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> if (pcCamRunning) stopPcCameraPush() else startPcCameraPush()
            }
        }

        val dialog = builder.create()
        // 设置深色主题
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF2d2d2d.toInt()))
        dialog.show()
    }

    /**
     * 启动CameraX摄像头预览并开始推流
     */
    private fun startCameraPreview(width: Int, height: Int, previewView: androidx.camera.view.PreviewView) {
        if (cameraPreviewRunning) {
            stopCameraPreview()
        }

        val futureProvider = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(this)
        // 必须使用主线程Executor，bindToLifecycle 标注了 @MainThread
        futureProvider.addListener({
            cameraProvider = futureProvider.get()

            val resolutionSelector = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                .setResolutionStrategy(androidx.camera.core.resolutionselector.ResolutionStrategy(
                    android.util.Size(width, height),
                    androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                ))
                .build()

            val preview = androidx.camera.core.Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()

            val imageAnalyzer = androidx.camera.core.ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setOutputImageFormat(androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            cameraExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
            imageAnalyzer.setAnalyzer(cameraExecutor!!,
                object : androidx.camera.core.ImageAnalysis.Analyzer {
                    private var frameCount = 0L
                    private var lastFrameTime = 0L
                    private var localFrameTimeoutRunnable: Runnable? = null
                    override fun analyze(image: androidx.camera.core.ImageProxy) {
                        frameCount++
                        // 跳帧：每3帧处理1帧，减少CPU负载避免卡顿
                        if (frameCount % 3 != 0L) {
                            image.close()
                            return
                        }
                        try {
                            val plane = image.planes[0]
                            val buffer = plane.buffer
                            val pixelStride = plane.pixelStride
                            val rowStride = plane.rowStride
                            val rowPadding = rowStride - pixelStride * image.width

                            val rawBitmap = if (rowPadding == 0) {
                                android.graphics.Bitmap.createBitmap(
                                    image.width, image.height,
                                    android.graphics.Bitmap.Config.ARGB_8888
                                ).also {
                                    buffer.rewind()
                                    it.copyPixelsFromBuffer(buffer)
                                }
                            } else {
                                val paddedWidth = image.width + rowPadding / pixelStride
                                val padded = android.graphics.Bitmap.createBitmap(
                                    paddedWidth, image.height,
                                    android.graphics.Bitmap.Config.ARGB_8888
                                )
                                buffer.rewind()
                                padded.copyPixelsFromBuffer(buffer)
                                android.graphics.Bitmap.createBitmap(padded, 0, 0, image.width, image.height).also {
                                    padded.recycle()
                                }
                            }

                            val rotation = image.imageInfo.rotationDegrees
                            val needMirror = cameraLensFacing == androidx.camera.core.CameraSelector.LENS_FACING_FRONT
                            val orientedBitmap = if (rotation != 0 || needMirror) {
                                val matrix = android.graphics.Matrix()
                                if (rotation != 0) matrix.postRotate(rotation.toFloat())
                                if (needMirror) matrix.postScale(-1f, 1f)
                                android.graphics.Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true).also {
                                    if (it !== rawBitmap) rawBitmap.recycle()
                                }
                            } else {
                                rawBitmap
                            }

                            val baos = java.io.ByteArrayOutputStream()
                            orientedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, baos)
                            val jpegData = baos.toByteArray()
                            // 异步发送，避免阻塞分析线程
                            lifecycleScope.launch(Dispatchers.IO) {
                                ConnectionManager.sendFrameToPc(jpegData, type = "camera")
                            }
                            orientedBitmap.recycle()
                            // 本地摄像头帧超时检测：2秒无新帧则停止推流
                            lastFrameTime = System.currentTimeMillis()
                            localFrameTimeoutRunnable?.let { frameTimeoutHandler.removeCallbacks(it) }
                            localFrameTimeoutRunnable = Runnable {
                                if (cameraPreviewRunning) {
                                    stopCameraPreview()
                                    ConnectionManager.sendMediaCommand("mirror_stop")
                                    runOnUiThread {
                                        pageCache[9]?.findViewById<Button>(R.id.btnCameraStart)?.text = "启动推流"
                                        pageCache[9]?.findViewById<TextView>(R.id.cameraStatus)?.text = "摄像头已断开"
                                    }
                                }
                            }.also { frameTimeoutHandler.postDelayed(it, 2000) }
                        } catch (e: Exception) {
                            // ignore
                        } finally {
                            image.close()
                        }
                    }
                }
            )

            val cameraSelector = androidx.camera.core.CameraSelector.Builder()
                .requireLensFacing(cameraLensFacing)
                .build()

            preview.setSurfaceProvider(previewView.surfaceProvider)

            cameraInstance = cameraProvider?.bindToLifecycle(
                this@MainActivity,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            cameraPreviewRunning = true
        }, androidx.core.content.ContextCompat.getMainExecutor(this))
    }

    /**
     * 停止摄像头预览和推流
     */
    private fun stopCameraPreview() {
        cameraPreviewRunning = false
        cameraProvider?.unbindAll()
        cameraProvider = null
        cameraInstance = null
        cameraExecutor?.shutdown()
        cameraExecutor = null
        // 清理超时回调
        frameTimeoutHandler.removeCallbacksAndMessages(null)
        mirrorFrameTimeoutRunnable = null
        cameraFrameTimeoutRunnable = null
    }

    /**
     * 切换前后摄像头
     */
    private fun switchCameraLens() {
        cameraLensFacing = if (cameraLensFacing == androidx.camera.core.CameraSelector.LENS_FACING_BACK) {
            androidx.camera.core.CameraSelector.LENS_FACING_FRONT
        } else {
            androidx.camera.core.CameraSelector.LENS_FACING_BACK
        }
        // 重新启动会自动切换
    }

    /**
     * S8a: 启动手机摄像头推流（持续发送画面给电脑）
     */
    private fun startCameraPush() {
        if (cameraPreviewRunning) return
        // CameraX 预览视图存在于页面中
        cameraPreviewView?.let { startCameraPreview(1920, 1080, it) }
        runOnUiThread { 
            pageCache[9]?.findViewById<Button>(R.id.btnCameraStart)?.text = "停止推流"
            pageCache[9]?.findViewById<TextView>(R.id.cameraStatus)?.text = "摄像头推流中..."
        }
    }

    /**
     * S8a: 停止摄像头推流
     */
    private fun stopCameraPush() {
        if (!cameraPreviewRunning) return
        stopCameraPreview()
        runOnUiThread { 
            pageCache[9]?.findViewById<Button>(R.id.btnCameraStart)?.text = "启动推流"
            pageCache[9]?.findViewById<TextView>(R.id.cameraStatus)?.text = "摄像头已停止"
        }
        ConnectionManager.sendMediaCommand("camera_stop")
    }

    /**
     * S8b: 启动电脑摄像头推流（请求PC开始推送摄像头画面到手机）
     */
    private fun startPcCameraPush() {
        ConnectionManager.startPcCameraPush()
        ConnectionManager.startPcCameraPolling()
        runOnUiThread { 
            pageCache[9]?.findViewById<TextView>(R.id.cameraStatus)?.text = "电脑摄像头连接中..."
        }
    }

    /**
     * S8b: 停止电脑摄像头推流
     */
    private fun stopPcCameraPush() {
        ConnectionManager.stopPcCameraPush()
        ConnectionManager.stopPcCameraPolling()
        runOnUiThread { 
            pageCache[9]?.findViewById<TextView>(R.id.cameraStatus)?.text = "电脑摄像头已停止"
            cameraImageView?.visibility = View.GONE
        }
    }

    /**
     * 执行摄像头切换（类级方法，可供全局 cameraSwitchRequest 收集器调用）
     * @param isStreaming 当前是否正在推流
     */
    private fun performCameraSwitch(@Suppress("UNUSED_PARAMETER") isStreaming: Boolean) {
        // 无论当前是否在推流，都切换镜头方向
        switchCameraLens()
        if (cameraPreviewRunning) {
            // 正在推流：停止当前预览，用新镜头重新启动
            stopCameraPreview()
            cameraPreviewView?.let { startCameraPreview(1920, 1080, it) }
        }
        val facing = if (cameraLensFacing == androidx.camera.core.CameraSelector.LENS_FACING_BACK) "back" else "front"
        ConnectionManager.sendCameraSwitch(facing)
        Toast.makeText(this, "Switched to $facing camera", Toast.LENGTH_SHORT).show()
    }

    // ============================== 通知 ==============================

    private val activeNotifItems = mutableListOf<ConnectionManager.NotificationItem>()
    private val notifHistoryItems = mutableListOf<ConnectionManager.NotificationItem>()

    private fun notifKey(item: ConnectionManager.NotificationItem): String {
        return item.key.ifEmpty { "${item.packageName}|${item.sbnTag}|${item.sbnId}" }
    }

    /**
     * 通知黑名单选择弹窗：显示所有已安装应用，勾选不转发的应用
     */
    private fun showNotificationBlacklistDialog() {
        val pm = packageManager
        val installed = pm.getInstalledApplications(0)
            .filter { it.packageName != "com.phonehub" }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val blacklist = NotificationListener.getBlacklist(this).toMutableSet()
        val appNames = installed.map { it.loadLabel(pm).toString() }
        val pkgNames = installed.map { it.packageName }
        val checked = pkgNames.map { it in blacklist }.toBooleanArray()

        val dialog = android.app.AlertDialog.Builder(this)
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
        // 深色主题弹窗
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF2d2d2d.toInt()))
        dialog.listView?.setBackgroundColor(0xFF2d2d2d.toInt())
        dialog.listView?.divider = android.graphics.drawable.ColorDrawable(0xFF404040.toInt())
        dialog.listView?.dividerHeight = 1
        for (i in 0 until dialog.listView.count) {
            val view = dialog.listView.getChildAt(i) ?: continue
            if (view is android.widget.CheckedTextView) {
                view.setTextColor(0xFFFFFFFF.toInt())
            }
        }
        // 通过 post 方式延迟设置文字颜色（确保已创建完成）
        dialog.listView.post {
            for (i in 0 until dialog.listView.count) {
                val view = dialog.listView.getChildAt(i)
                if (view is android.widget.CheckedTextView) {
                    view.setTextColor(0xFFFFFFFF.toInt())
                }
            }
        }
    }

    private fun getNotificationsView(): View {
        // 通知权限完全由用户手动点击"开启通知使用权"按钮触发，不在进入页面时自动检查或跳转
        val v = LayoutInflater.from(this).inflate(R.layout.page_notifications, null)
        v.findViewById<Button>(R.id.btnNotifWhitelist)?.applyDarkTheme()
        v.findViewById<Button>(R.id.btnNotifPermission)?.applyDarkTheme()
        v.findViewById<Button>(R.id.btnNotifRefresh)?.applyDarkTheme()
        v.findViewById<Button>(R.id.btnNotifActiveTab)?.applyDarkTheme()
        v.findViewById<Button>(R.id.btnNotifHistoryTab)?.applyDarkTheme()
        val activeList = v.findViewById<ListView>(R.id.notifActiveList)
        val historyList = v.findViewById<ListView>(R.id.notifHistoryList)
        val empty = v.findViewById<TextView>(R.id.notifEmpty)
        val filterEdit = v.findViewById<EditText>(R.id.notifFilter)
        val permissionBar = v.findViewById<LinearLayout>(R.id.notifPermissionBar)
        val permissionText = v.findViewById<TextView>(R.id.notifPermissionText)
        val btnPermission = v.findViewById<Button>(R.id.btnNotifPermission)
        val btnRefresh = v.findViewById<Button>(R.id.btnNotifRefresh)
        val btnActiveTab = v.findViewById<Button>(R.id.btnNotifActiveTab)
        val btnHistoryTab = v.findViewById<Button>(R.id.btnNotifHistoryTab)

        // 当前显示的 Tab："active" 或 "history"
        var currentTab = "active"

        v.findViewById<Button>(R.id.btnNotifWhitelist)?.setOnClickListener {
            showNotificationBlacklistDialog()
        }

        fun checkPermissionAndTrigger() {
            // 仅更新 UI 显示权限状态，不自动触发跳转或重绑
            val enabled = ConnectionManager.isNotificationListenerEnabled()
            if (enabled) {
                if (NotificationListener.instance == null) {
                    permissionBar.visibility = View.VISIBLE
                    permissionText.text = "权限已开启但服务未连接，请关闭再重新开启「PhoneHub」开关"
                    btnPermission.text = "去重新开启"
                } else {
                    permissionBar.visibility = View.GONE
                }
            } else {
                permissionBar.visibility = View.VISIBLE
                permissionText.text = "通知监听权限未开启，无法获取通知"
                btnPermission.text = "去开启"
            }
        }

        fun refresh() {
            val q = filterEdit?.text?.toString()?.trim()?.lowercase() ?: ""
            val sourceList = if (currentTab == "active") activeNotifItems else notifHistoryItems
            val filtered = if (q.isEmpty()) sourceList else {
                sourceList.filter {
                    it.packageName.lowercase().contains(q) ||
                    it.title.lowercase().contains(q) ||
                    it.text.lowercase().contains(q)
                }
            }
            val targetList = if (currentTab == "active") activeList else historyList
            if (filtered.isEmpty()) {
                empty.visibility = View.VISIBLE
                empty.text = if (sourceList.isEmpty()) "暂无通知" else "无匹配结果"
                targetList.adapter = null
            } else {
                empty.visibility = View.GONE
                val displays = filtered.map {
                    "${it.title}\n${it.text}\n[${it.packageName}] ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.timestamp))}"
                }
                targetList.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displays)
            }
        }

        // Tab 切换
        btnActiveTab?.setOnClickListener {
            currentTab = "active"
            activeList.visibility = View.VISIBLE
            historyList.visibility = View.GONE
            btnActiveTab.setTextColor(0xFFFFFFFF.toInt())
            btnHistoryTab.setTextColor(0xFF888888.toInt())
            refresh()
        }
        btnHistoryTab?.setOnClickListener {
            currentTab = "history"
            activeList.visibility = View.GONE
            historyList.visibility = View.VISIBLE
            btnHistoryTab.setTextColor(0xFFFFFFFF.toInt())
            btnActiveTab.setTextColor(0xFF888888.toInt())
            refresh()
        }
        // 默认选中"当前通知"Tab
        btnActiveTab.callOnClick()

        btnPermission?.setOnClickListener {
            try {
                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
            }
        }

        btnRefresh?.setOnClickListener {
            // 刷新时清空当前通知列表，手机端会重新发送全部活动通知
            activeNotifItems.clear()
            checkPermissionAndTrigger()
            NotificationListener.instance?.reportAllActiveNotifications()
            refresh()
            val enabled = ConnectionManager.isNotificationListenerEnabled()
            val connected = NotificationListener.instance != null
            when {
                !enabled -> Toast.makeText(this, "通知权限未开启，请先开启", Toast.LENGTH_SHORT).show()
                !connected -> Toast.makeText(this, "服务未连接，请关闭再重新开启权限开关", Toast.LENGTH_LONG).show()
                else -> Toast.makeText(this, "已刷新", Toast.LENGTH_SHORT).show()
            }
        }

        filterEdit?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refresh() }
            override fun afterTextChanged(s: Editable?) {}
        })

        activeList.post {
            checkPermissionAndTrigger()
            refresh()
        }

        lifecycleScope.launch {
            ConnectionManager.notifications.collect { item ->
                val key = notifKey(item)
                // 更新当前通知列表（替换同 key 的旧通知，新通知插入最前面）
                val existingIdx = activeNotifItems.indexOfFirst { notifKey(it) == key }
                if (existingIdx >= 0) {
                    activeNotifItems[existingIdx] = item
                } else {
                    activeNotifItems.add(0, item)
                }
                // 历史记录：只追加不重复的新通知
                if (notifHistoryItems.none { notifKey(it) == key }) {
                    notifHistoryItems.add(0, item)
                    if (notifHistoryItems.size > 200) {
                        notifHistoryItems.removeAt(notifHistoryItems.size - 1)
                    }
                }
                refresh()
            }
        }
        return v
    }

    // ============================== 文件管理 ==============================

    private var pcCurPath = "C:\\"
    private var pcInDrives = true
    private var currentPcDrives = listOf<ConnectionManager.PcDriveInfo>()

    private fun showConfirmDialog(title: String, message: String, onConfirm: () -> Unit) {
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定") { _, _ -> onConfirm() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showInputDialog(title: String, hint: String, initial: String, onConfirm: (String) -> Unit) {
        val input = EditText(this)
        input.setText(initial)
        input.hint = hint
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("确定") { _, _ -> onConfirm(input.text.toString()) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showInfoDialog(title: String, message: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun getFileManagerView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1e1e1e.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // 标题
        val title = TextView(this).apply {
            text = "远程文件管理"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(title)

        // 汇总当前文件列表供操作菜单使用（磁盘视图时不适用）
        var currentPcFiles = mutableListOf<ConnectionManager.PcFileInfo>()

        // 路径栏：上级 / 刷新 / 排序 / 操作（默认禁用）
        val pathRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF2d2d2d.toInt())
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val btnUp = Button(this).apply {
            text = "上级"
            applyDarkTheme()
        }
        val btnRefresh = Button(this).apply {
            text = "刷新"
            applyDarkTheme()
        }
        val btnSort = Button(this).apply {
            text = "排序"
            applyDarkTheme()
        }
        val btnOp = Button(this).apply {
            text = "操作"
            applyDarkTheme(primary = true)
            isEnabled = false
        }
        val pathTv = TextView(this).apply {
            id = R.id.fmPath
            text = pcCurPath
            setTextColor(0xFFb0b0b0.toInt())
            textSize = 12f
            setPadding(dp(8), 0, dp(8), 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        pathRow.addView(btnUp, LinearLayout.LayoutParams(dp(60), dp(40)))
        pathRow.addView(btnRefresh, LinearLayout.LayoutParams(dp(60), dp(40)))
        pathRow.addView(btnSort, LinearLayout.LayoutParams(dp(60), dp(40)))
        pathRow.addView(btnOp, LinearLayout.LayoutParams(dp(60), dp(40)))
        pathRow.addView(pathTv, LinearLayout.LayoutParams(0, dp(40), 1f))
        root.addView(pathRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        // 文件列表（使用可多选的 ListView）
        val list = ListView(this)
        list.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        val empty = TextView(this).apply {
            id = R.id.fmEmpty
            text = "暂无内容"
            setTextColor(0xFF666666.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        root.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0
        ).apply { weight = 1f })
        root.addView(empty)

        val selectedIds = linkedSetOf<Int>() // 多选索引集合
        var sortMode = 0 // 0=名称, 1=大小, 2=修改时间

        fun applySort(): List<ConnectionManager.PcFileInfo> {
            val cmp = when (sortMode) {
                1 -> compareBy<ConnectionManager.PcFileInfo> { !it.isDir }.thenByDescending { it.size }
                2 -> compareBy<ConnectionManager.PcFileInfo> { !it.isDir }.thenByDescending { it.modified }
                else -> compareBy<ConnectionManager.PcFileInfo> { !it.isDir }.thenBy { it.name.lowercase() }
            }
            return currentPcFiles.sortedWith(cmp)
        }

        fun refreshOpBtn() {
            btnOp.isEnabled = selectedIds.isNotEmpty()
            btnOp.text = if (selectedIds.isNotEmpty()) "操作(${selectedIds.size})" else "操作"
        }

        fun renderPcList(files: List<ConnectionManager.PcFileInfo>) {
            selectedIds.clear()
            refreshOpBtn()
            if (files.isEmpty()) {
                empty.visibility = View.VISIBLE
                empty.text = "空目录"
                list.adapter = null
                return
            }
            empty.visibility = View.GONE
            val displays = files.map { f ->
                val icon = if (f.isDir) "\uD83D\uDCC1 " else fileIcon(f.name)
                val sz = if (f.isDir) "[目录]" else formatSize(f.size)
                val name = if (f.isDir) "${f.name}/" else f.name
                "${icon}${name}\n$sz"
            }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, displays)
            refreshOpBtn()
        }

        // 电脑文件浏览逻辑
        fun refreshPcFiles() {
            pcInDrives = false
            pathTv.text = pcCurPath
            empty.visibility = View.VISIBLE
            empty.text = "正在加载..."
            list.adapter = null
            ConnectionManager.fetchPcFiles(pcCurPath) { files, _ ->
                currentPcFiles = files.toMutableList()
                renderPcList(applySort())
            }
        }

        fun refreshPcDrives() {
            pcInDrives = true
            pathTv.text = "我的电脑"
            currentPcFiles = mutableListOf<ConnectionManager.PcFileInfo>()
            empty.visibility = View.VISIBLE
            empty.text = "正在加载磁盘列表..."
            list.adapter = null
            ConnectionManager.fetchPcDrives { drives ->
                currentPcDrives = drives
                if (drives.isEmpty()) {
                    empty.visibility = View.VISIBLE
                    empty.text = "未获取到磁盘信息"
                    list.adapter = null
                } else {
                    empty.visibility = View.GONE
                    val displays = drives.map {
                        val totalGb = it.total / (1024.0 * 1024.0 * 1024.0)
                        val freeGb = it.free / (1024.0 * 1024.0 * 1024.0)
                        "${it.name}  [磁盘]\n总计: %.1f GB  可用: %.1f GB".format(totalGb, freeGb)
                    }
                    list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displays)
                    list.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, pos, _ ->
                        pcCurPath = drives[pos].name
                        pcInDrives = false
                        refreshPcFiles()
                    }
                }
            }
        }

        // 列表点击 / 长按多选
        list.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, pos, _ ->
            if (pcInDrives) {
                if (currentPcDrives.isNotEmpty() && pos < currentPcDrives.size) {
                    pcCurPath = currentPcDrives[pos].name
                    pcInDrives = false
                    refreshPcFiles()
                }
                return@OnItemClickListener
            }
            if (selectedIds.isNotEmpty()) {
                // 已进入多选模式：点击切换选中状态
                if (!selectedIds.add(pos)) selectedIds.remove(pos)
                refreshOpBtn()
            } else {
                // 普通模式：进入目录
                val sorted = applySort()
                if (pos < sorted.size) {
                    val f = sorted[pos]
                    if (f.isDir) {
                        pcCurPath = pcCurPath.trimEnd('\\') + "\\" + f.name
                        refreshPcFiles()
                    }
                }
            }
        }
        list.onItemLongClickListener = android.widget.AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            if (pcInDrives) return@OnItemLongClickListener true
            if (!selectedIds.add(pos)) selectedIds.remove(pos)
            refreshOpBtn()
            true
        }

        // 排序菜单
        btnSort.setOnClickListener {
            val sorted = applySort()
            renderPcList(sorted)
            sortMode = (sortMode + 1) % 3
            val label = when (sortMode) { 1 -> "按大小" 2 -> "按修改时间" else -> "按名称" }
            Toast.makeText(this, "排序: $label", Toast.LENGTH_SHORT).show()
        }

        // 操作菜单
        btnOp.setOnClickListener {
            if (selectedIds.isEmpty()) return@setOnClickListener
            val sorted = applySort()
            val sel = selectedIds.sorted().mapNotNull { if (it < sorted.size) sorted[it] else null }
            if (sel.isEmpty()) return@setOnClickListener
            val destDir = pcCurPath

            val popup = android.widget.PopupMenu(this, btnOp)
            popup.menu.add("打开（下载后自动打开）")
            popup.menu.add("下载")
            popup.menu.add("删除")
            popup.menu.add("重命名")
            popup.menu.add("属性")
            popup.menu.add("复制到")
            popup.setOnMenuItemClickListener { item ->
                val label = item.title.toString()
                when (label) {
                    "打开（下载后自动打开）" -> {
                        val f = sel.firstOrNull() ?: return@setOnMenuItemClickListener true
                        val filePath = pcCurPath.trimEnd('\\') + "\\" + f.name
                        downloadPcFile(filePath, f.name, openAfter = true)
                        selectedIds.clear(); refreshOpBtn()
                    }
                    "下载" -> {
                        sel.forEach { downloadPcFile(pcCurPath.trimEnd('\\') + "\\" + it.name, it.name) }
                        selectedIds.clear(); refreshOpBtn()
                    }
                    "删除" -> {
                        val names = sel.joinToString("、") { it.name }
                        showConfirmDialog("删除", "确定删除选中的 ${sel.size} 项？\n$names") {
                            sel.forEach {
                                ConnectionManager.pcFileDelete(pcCurPath.trimEnd('\\') + "\\" + it.name) { ok, msg ->
                                    runOnUiThread {
                                        if (!ok) Toast.makeText(this@MainActivity, "删除失败: $msg", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            refreshPcFiles()
                            selectedIds.clear(); refreshOpBtn()
                        }
                    }
                    "重命名" -> {
                        val f = sel.firstOrNull() ?: return@setOnMenuItemClickListener true
                        showInputDialog("重命名", "输入新名称", f.name) { newName ->
                            if (newName.isNotBlank() && newName != f.name) {
                                ConnectionManager.pcFileRename(pcCurPath.trimEnd('\\') + "\\" + f.name, newName) { ok, msg ->
                                    runOnUiThread {
                                        Toast.makeText(this@MainActivity, if (ok) "重命名成功" else "重命名失败: $msg", Toast.LENGTH_SHORT).show()
                                        refreshPcFiles()
                                    }
                                }
                            }
                        }
                        selectedIds.clear(); refreshOpBtn()
                    }
                    "属性" -> {
                        val f = sel.firstOrNull() ?: return@setOnMenuItemClickListener true
                        ConnectionManager.pcFileInfo(pcCurPath.trimEnd('\\') + "\\" + f.name) { info ->
                            runOnUiThread {
                                if (info == null) {
                                    Toast.makeText(this@MainActivity, "获取属性失败", Toast.LENGTH_SHORT).show()
                                } else {
                                    val type = if (info["is_dir"] == "true") "文件夹" else (if (info["ext"].isNullOrEmpty()) "文件" else info["ext"] + " 文件")
                                    val sizeStr = if (info["is_dir"] == "true") "-" else formatSize(info["size"]?.toLongOrNull() ?: 0L)
                                    val ctime = info["created"]?.toLongOrNull()?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it * 1000)) } ?: "-"
                                    val mtime = info["modified"]?.toLongOrNull()?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it * 1000)) } ?: "-"
                                    val content = "名称: ${info["name"]}\n类型: $type\n大小: $sizeStr\n创建时间: $ctime\n修改时间: $mtime"
                                    showInfoDialog("属性", content)
                                }
                            }
                        }
                        selectedIds.clear(); refreshOpBtn()
                    }
                    "复制到" -> {
                        // 弹出目录输入（以当前目录为默认目标）
                        showInputDialog("复制到", "输入目标目录", destDir) { input ->
                            val target = input.ifBlank { destDir }
                            sel.forEach {
                                ConnectionManager.pcFileCopy(pcCurPath.trimEnd('\\') + "\\" + it.name, target) { ok, msg ->
                                    runOnUiThread {
                                        if (!ok) Toast.makeText(this@MainActivity, "复制失败: $msg", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            selectedIds.clear(); refreshOpBtn()
                        }
                    }
                }
                true
            }
            popup.show()
        }

        // 上级
        btnUp.setOnClickListener {
            if (pcInDrives) return@setOnClickListener
            val trimmed = pcCurPath.trimEnd('\\', '/')
            if (trimmed.length <= 3 && trimmed.isNotEmpty() && trimmed[1] == ':') {
                refreshPcDrives()
            } else {
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

        // 刷新
        btnRefresh.setOnClickListener {
            if (pcInDrives) refreshPcDrives() else refreshPcFiles()
        }

        // 初始加载
        list.post { refreshPcDrives() }
        return root
    }

    private fun fileIcon(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "apk", "zip", "rar", "7z" -> "\uD83D\uDCE6 "
            "jpg", "jpeg", "png", "gif", "bmp", "webp" -> "\uD83D\uDDBC "
            "mp4", "avi", "mkv", "mov" -> "\uD83C\uDFAC "
            "mp3", "wav", "flac", "aac", "ogg" -> "\uD83C\uDFB5 "
            "pdf", "doc", "docx", "xls", "xlsx" -> "\uD83D\uDCC4 "
            "txt", "log", "md" -> "\uD83D\uDCDD "
            else -> "\uD83D\uDCCE "
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "%d KB".format(bytes / 1024)
            else -> "$bytes B"
        }
    }

    /**
     * 下载电脑文件到手机 Download 目录（使用 MediaStore 兼容 Android 10+ Scoped Storage）
     */
    private fun downloadPcFile(filePath: String, fileName: String, openAfter: Boolean = false) {
        Thread {
            try {
                val baseUrl = ConnectionManager.getBaseUrlPublic()
                val url = URL("$baseUrl/api/pc_file_download")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer ${ConnectionManager.getSecretToken()}")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 60000
                val jsonBody = "{\"path\":\"${filePath.replace("\\", "\\\\")}\"}"
                conn.outputStream.write(jsonBody.toByteArray())
                conn.outputStream.flush()
                conn.outputStream.close()

                if (conn.responseCode == 200) {
                    // 使用 MediaStore 写入 Download 目录（兼容 Android 10+）
                    val resolver = contentResolver
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                        put(android.provider.MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { output ->
                            conn.inputStream.use { input ->
                                input.copyTo(output)
                            }
                        }
                        runOnUiThread {
                            Toast.makeText(this, "已下载到 Download: $fileName", Toast.LENGTH_SHORT).show()
                        }
                        if (openAfter) {
                            runOnUiThread {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, resolver.getType(uri))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "下载失败: 无法创建文件", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val errorMsg = when (conn.responseCode) {
                        403 -> "无权限下载（系统保护文件或文件被锁定）"
                        404 -> "文件不存在"
                        else -> "HTTP ${conn.responseCode}"
                    }
                    runOnUiThread {
                        Toast.makeText(this, "下载失败: $errorMsg", Toast.LENGTH_SHORT).show()
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // ============================== APK 安装 ==============================

    @Suppress("DEPRECATION")
    private fun getApkInstallView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_apk_install, null)
        v.findViewById<Button>(R.id.btnApkPick)?.applyDarkTheme(primary = true)
        v.findViewById<Button>(R.id.btnApkInstallLast)?.applyDarkTheme()
        val list = v.findViewById<ListView>(R.id.apkList)
        val empty = v.findViewById<TextView>(R.id.apkEmpty)

        v.findViewById<Button>(R.id.btnApkPick)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "application/vnd.android.package-archive"
            startActivityForResult(Intent.createChooser(intent, "选择 APK"), SELECT_APK_CODE)
        }
        v.findViewById<Button>(R.id.btnApkInstallLast)?.setOnClickListener {
            val dir = File(getExternalFilesDir(null), "Received")
            val apk = dir.listFiles { f -> f.name.endsWith(".apk") }
                ?.sortedByDescending { it.lastModified() }?.firstOrNull()
            if (apk != null) {
                installApk(apk)
            } else {
                Toast.makeText(this, "暂无接收的 APK", Toast.LENGTH_SHORT).show()
            }
        }
        // 列出已接收的 APK
        val dir = File(getExternalFilesDir(null), "Received")
        val apks = dir.listFiles { f -> f.name.endsWith(".apk") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (apks.isEmpty()) {
            empty.visibility = View.VISIBLE
            list.adapter = null
        } else {
            empty.visibility = View.GONE
            val names = apks.map { "${it.name}\n${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it.lastModified()))}" }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
            list.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, pos, _ ->
                installApk(apks[pos])
            }
        }
        return v
    }

    private fun installApk(file: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法安装 APK: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================== 应用管理 ==============================

    private fun getAppManagerView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_app_manager, null)
        v.findViewById<Button>(R.id.btnAppRefresh)?.applyDarkTheme(primary = true)
        val list = v.findViewById<ListView>(R.id.appList)
        val empty = v.findViewById<TextView>(R.id.appEmpty)
        val filter = v.findViewById<EditText>(R.id.appFilter)

        fun refresh() {
            val pm = packageManager
            val infos = pm.getInstalledApplications(0).sortedBy {
                pm.getApplicationLabel(it).toString().lowercase()
            }
            val q = filter.text?.toString()?.lowercase() ?: ""
            val filtered = infos.filter {
                q.isEmpty() ||
                pm.getApplicationLabel(it).toString().lowercase().contains(q) ||
                it.packageName.lowercase().contains(q)
            }
            if (filtered.isEmpty()) {
                empty.visibility = View.VISIBLE
                list.adapter = null
            } else {
                empty.visibility = View.GONE
                val displays = filtered.map {
                    val label = pm.getApplicationLabel(it).toString()
                    val sys = if ((it.flags and ApplicationInfo.FLAG_SYSTEM) != 0) "[系统]" else ""
                    "$label $sys\n${it.packageName}"
                }
                list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displays)
                list.onItemLongClickListener = android.widget.AdapterView.OnItemLongClickListener { _, _, pos, _ ->
                    val pkg = filtered[pos].packageName
                    val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
                    startActivity(intent)
                    true
                }
            }
        }
        v.findViewById<Button>(R.id.btnAppRefresh)?.setOnClickListener { refresh() }
        filter.setOnEditorActionListener { _, _, _ -> refresh(); true }
        list.post { refresh() }
        return v
    }

    // ============================== 电源管理 ==============================

    private fun getPowerView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_power, null)
        v.findViewById<Button>(R.id.btnPowerShutdown)?.applyDarkTheme(primary = true)
        v.findViewById<Button>(R.id.btnPowerReboot)?.applyDarkTheme(primary = true)
        v.findViewById<Button>(R.id.btnPowerHibernate)?.applyDarkTheme()
        v.findViewById<Button>(R.id.btnPowerLock)?.applyDarkTheme()
        v.findViewById<Button>(R.id.btnPowerCancel)?.applyDarkTheme()

        v.findViewById<Button>(R.id.btnPowerShutdown)?.setOnClickListener {
            showPowerCountdown("关机", "shutdown")
        }
        v.findViewById<Button>(R.id.btnPowerReboot)?.setOnClickListener {
            showPowerCountdown("重启", "reboot")
        }
        v.findViewById<Button>(R.id.btnPowerHibernate)?.setOnClickListener {
            ConnectionManager.sendPowerCommand("hibernate", 0L)
            Toast.makeText(this, "已发送休眠指令", Toast.LENGTH_SHORT).show()
        }
        v.findViewById<Button>(R.id.btnPowerLock)?.setOnClickListener {
            ConnectionManager.sendPowerCommand("lock", 0L)
            Toast.makeText(this, "已发送锁定指令", Toast.LENGTH_SHORT).show()
        }
        v.findViewById<Button>(R.id.btnPowerCancel)?.setOnClickListener {
            ConnectionManager.sendPowerCommand("cancel", 0L)
            Toast.makeText(this, "已发送取消指令", Toast.LENGTH_SHORT).show()
        }
        return v
    }

    private fun showPowerCountdown(label: String, cmd: String) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_power_countdown, null)
        v.findViewById<Button>(R.id.btnPowerCancel).applyDarkTheme(primary = true)
        val title = v.findViewById<TextView>(R.id.powerTitle)
        val countdown = v.findViewById<TextView>(R.id.powerCountdownText)
        val progress = v.findViewById<ProgressBar>(R.id.powerProgressBar)
        title.text = "电脑即将$label"
        progress.max = 30
        progress.progress = 30

        val dialog = AlertDialog.Builder(this).setView(v).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        ConnectionManager.sendPowerCommand(cmd, 30_000L)

        val timer = object : CountDownTimer(30_000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = (millisUntilFinished / 1000).toInt()
                countdown.text = sec.toString()
                progress.progress = sec
            }
            override fun onFinish() {
                countdown.text = "0"
                dialog.dismiss()
                Toast.makeText(this@MainActivity, "$label 指令已发送", Toast.LENGTH_SHORT).show()
            }
        }.start()

        v.findViewById<Button>(R.id.btnPowerCancel).setOnClickListener {
            timer.cancel()
            ConnectionManager.sendPowerCommand("cancel", 0L)
            dialog.dismiss()
            Toast.makeText(this, "已取消$label", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================== 推送网页 ==============================

    private fun getPushWebView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_push_web, null)
        v.findViewById<Button>(R.id.btnPushUrlPc)?.applyDarkTheme(primary = true)
        val urlInput = v.findViewById<EditText>(R.id.urlInput)
        val list = v.findViewById<ListView>(R.id.urlHistoryList)

        // 首次进入时从 SharedPreferences 加载历史
        if (urlHistory.isEmpty()) loadUrlHistory()

        fun refreshHistory() {
            if (urlHistory.isEmpty()) {
                list.adapter = null
            } else {
                // 显示方向标签 + URL + 时间
                val displays = urlHistory.map {
                    val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.timestamp))
                    "[${it.direction}] ${it.url}\n$time"
                }
                list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displays)
                list.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, pos, _ ->
                    urlInput.setText(urlHistory[pos].url)
                }
            }
        }
        list.post { refreshHistory() }

        v.findViewById<Button>(R.id.btnPushUrlPc)?.setOnClickListener {
            var url = urlInput.text?.toString()?.trim() ?: ""
            if (url.isEmpty()) {
                Toast.makeText(this, "请输入 URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            // 按 project_memory 规定使用 Via 浏览器，不再指定 Edge；useEdge=false 让电脑用默认浏览器打开
            ConnectionManager.pushUrlToPc(url, false)
            // 手机 -> 电脑，方向标签 "电脑 <- 手机"
            addUrlHistory(url, "电脑 <- 手机")
            refreshHistory()
            Toast.makeText(this, "已发送到电脑", Toast.LENGTH_SHORT).show()
        }

        // 收集电脑推送过来的 URL，加入历史并标注方向 "电脑 -> 手机"
        lifecycleScope.launch {
            ConnectionManager.receivedUrl.collect { url ->
                if (url.isNotEmpty()) {
                    addUrlHistory(url, "电脑 -> 手机")
                    refreshUrlHistoryList()
                    Toast.makeText(this@MainActivity, "收到电脑推送 URL: $url", Toast.LENGTH_LONG).show()
                }
            }
        }

        // 收集电脑端发来的 URL 历史同步数据，合并到本地
        lifecycleScope.launch {
            ConnectionManager.urlHistorySync.collect { remoteHistory ->
                var changed = false
                for (item in remoteHistory) {
                    val url = item["url"] as? String ?: ""
                    val direction = item["direction"] as? String ?: ""
                    val timestamp = item["timestamp"] as? Long ?: 0L
                    if (url.isEmpty()) continue
                    // 去重：同 URL 同方向则跳过
                    val exists = urlHistory.any { it.url == url && it.direction == direction }
                    if (!exists) {
                        urlHistory.add(UrlHistoryItem(url, direction, timestamp))
                        changed = true
                    }
                }
                if (changed) {
                    // 按时间降序排序
                    urlHistory.sortByDescending { it.timestamp }
                    if (urlHistory.size > 50) urlHistory.subList(50, urlHistory.size).clear()
                    saveUrlHistory()
                    refreshUrlHistoryList()
                }
            }
        }
        return v
    }

    // ============================== 电脑声音（WebView 自动收听） ==============================

    /**
     * 电脑声音页：用 WebView 承载桌面端音频网页（http://<电脑>:4598/），
     * 关闭「需用户手势才能播放媒体」限制，实现打开即自动收听（无需手动点「开始」）。
     */
    private fun getPcAudioWebView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_pc_audio, null)
        val web = v.findViewById<WebView>(R.id.pcAudioWebView)
        configurePcAudioWebView(web)
        pcAudioWebView = web
        // 进入页即显示「电脑声音」媒体通知（收听期间常驻，后台也保留）
        ConnectionManager.showPcMediaNotification()
        return v
    }

    private fun configurePcAudioWebView(web: WebView) {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            try {
                // API 17+：允许免用户手势自动播放音频/视频
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    mediaPlaybackRequiresUserGesture = false
                }
            } catch (_: Exception) {}
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        web.webViewClient = WebViewClient()
        // JS 桥：网页里的播放/暂停/上一曲/下一曲按钮调用它来控制手机端与电脑端
        web.addJavascriptInterface(PcAudioBridge(), "PcAudioBridge")
        web.setBackgroundColor(0xFF1e1e1e.toInt())
        // 打开即加载电脑声音网页（网页内 WebSocket 自动连、AudioContext 自动播放）
        web.loadUrl(ConnectionManager.getPcAudioWebUrl())
    }

    /** 创建常驻后台容器（置于 mainContainer 之下被遮挡，但 WebView 仍挂载在窗口上继续出声） */
    private fun ensurePcAudioHost(): FrameLayout {
        if (pcAudioHost != null) return pcAudioHost!!
        val host = FrameLayout(this)
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply { topMargin = dp(56) }
        rootFrame?.addView(host, 0, lp)
        pcAudioHost = host
        return host
    }

    /** 离开电脑声音页：把 WebView 移入后台容器，连接与播放均保持 */
    private fun reparentAudioWebViewToHost() {
        val web = pcAudioWebView ?: return
        val host = ensurePcAudioHost()
        (web.parent as? ViewGroup)?.removeView(web)
        host.addView(web, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    }

    /** 进入电脑声音页：把 WebView 从后台容器移回页面（连接保持不变） */
    private fun reparentAudioWebViewToPage() {
        val web = pcAudioWebView ?: return
        val page = pageCache[17] as? FrameLayout ?: return
        (web.parent as? ViewGroup)?.removeView(web)
        page.addView(web, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    }

    /** 通知栏「停止播放」→ 手机端停止收听（网页 AudioContext 静音/挂起） */
    fun stopPcAudioListening() {
        pcAudioWebView?.evaluateJavascript("if(window.pcAudioStop)pcAudioStop();") { _ -> }
    }

    /** 通知栏「开启播放」→ 手机端恢复收听 */
    fun startPcAudioListening() {
        pcAudioWebView?.evaluateJavascript("if(window.pcAudioStart)pcAudioStart();") { _ -> }
    }

    /** 网页 ↔ 原生 桥：网页按钮调用，用于同步播放状态与控制电脑媒体键 */
    private class PcAudioBridge : Any() {
        @JavascriptInterface
        fun setPlaying(playing: Boolean) {
            // 手机端是否在收听 → 播放期间持有唤醒/WiFi 锁，保证切后台/息屏后仍在收听
            ConnectionManager.setPcAudioKeepAlive(playing)
        }

        @JavascriptInterface
        fun setPcPlaying(playing: Boolean) {
            // 电脑端是否在放声音 → 通知栏「播放/暂停」按钮图标
            ConnectionManager.pcMediaPlaying = playing
            ConnectionManager.updatePcMediaNotification()
        }

        @JavascriptInterface
        fun setMedia(title: String, artist: String, album: String, cover: String, status: String) {
            ConnectionManager.updatePcMediaFromWeb(title, artist, album, cover, status)
        }

        @JavascriptInterface
        fun prev() {
            ConnectionManager.sendMediaCommand("media_prev")
        }

        @JavascriptInterface
        fun next() {
            ConnectionManager.sendMediaCommand("media_next")
        }
    }

    /** 刷新推送网页页面的历史列表显示 */
    private fun refreshUrlHistoryList() {
        pageCache[15]?.let { cv ->
            cv.findViewById<ListView>(R.id.urlHistoryList)?.let { lv ->
                if (urlHistory.isEmpty()) {
                    lv.adapter = null
                } else {
                    val displays = urlHistory.map {
                        val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.timestamp))
                        "[${it.direction}] ${it.url}\n$time"
                    }
                    lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displays)
                }
            }
        }
    }

    // ============================== 设置 ==============================

    private fun getSettingsView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.page_settings, null)

        val etPawUrl = v.findViewById<EditText>(R.id.etPawUrl)
        val etPawToken = v.findViewById<EditText>(R.id.etPawToken)
        val btnSavePaw = v.findViewById<Button>(R.id.btnSavePaw)

        // 加载已保存的隧道配置
        val prefs = getSharedPreferences("phonehub_prefs", Context.MODE_PRIVATE)
        prefs.getString("cached_paw_url", "")?.let { etPawUrl.setText(it) }
        prefs.getString("cached_paw_token", "")?.let { etPawToken.setText(it) }
        if (etPawUrl.text.isBlank()) etPawUrl.setText("")
        if (etPawToken.text.isBlank()) etPawToken.setText("541881452418845")

        btnSavePaw?.applyDarkTheme(primary = true)
        btnSavePaw?.setOnClickListener {
            val url = etPawUrl.text.toString().trim()
            val token = etPawToken.text.toString().trim()
            if (url.isNotEmpty() && token.isNotEmpty()) {
                ConnectionManager.setPawConfig(url, token)
                prefs.edit()
                    .putString("cached_paw_url", url)
                    .putString("cached_paw_token", token)
                    .apply()
                Toast.makeText(this, "Cloudflare 隧道设置已保存", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "隧道地址和令牌不能为空", Toast.LENGTH_SHORT).show()
            }
        }

        v.findViewById<Button>(R.id.disconnectBtn2)?.applyDarkTheme()
        v.findViewById<Button>(R.id.disconnectBtn2)?.setOnClickListener {
            ConnectionManager.disconnect()
            updateSetupVisibility()
        }

        // 初始化连接信息为当前状态
        val state = ConnectionManager.connectionState.value
        val infoStatus = v.findViewById<TextView>(R.id.infoStatus)
        val infoChannel = v.findViewById<TextView>(R.id.infoChannel)
        val infoIp = v.findViewById<TextView>(R.id.infoIp)

        when (state) {
            ConnectionManager.ConnectionState.CONNECTED -> {
                infoStatus?.text = "状态: 已连接"
                infoStatus?.setTextColor(0xFF107c10.toInt())
            }
            ConnectionManager.ConnectionState.CONNECTING -> {
                infoStatus?.text = "状态: 连接中..."
                infoStatus?.setTextColor(0xFFffb900.toInt())
            }
            else -> {
                infoStatus?.text = "状态: 未连接"
                infoStatus?.setTextColor(0xFFd13438.toInt())
            }
        }

        val channel = ConnectionManager.currentChannel.value
        val channelName = when (channel) {
            ConnectionManager.ChannelType.WIFI -> "WiFi 直连"
            ConnectionManager.ChannelType.PAW -> "Cloudflare 隧道"
            ConnectionManager.ChannelType.ADB -> "USB 数据线"
            else -> "无"
        }
        infoChannel?.text = "通道: $channelName"
        
        infoIp?.text = "IP: ${ConnectionManager.getPcIp() ?: "未知"}"

        return v
    }

    // ============================== 文字发送弹窗 ==============================

    private fun showSendTextDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_send_text, null)
        val filenameInput = dialogView.findViewById<EditText>(R.id.filenameInput)
        val textContentInput = dialogView.findViewById<EditText>(R.id.textContentInput)
        val cancelBtn = dialogView.findViewById<Button>(R.id.cancelTextBtn)
        val sendBtn = dialogView.findViewById<Button>(R.id.sendTextBtn)

        cancelBtn.applyDarkTheme()
        sendBtn.applyDarkTheme(primary = true)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        cancelBtn.setOnClickListener { dialog.dismiss() }
        sendBtn.setOnClickListener {
            val text = textContentInput.text.toString()
            val filename = filenameInput.text.toString().let { if (it.isBlank()) null else it }
            if (text.isNotEmpty()) {
                ConnectionManager.sendText(text, filename)
                Toast.makeText(this, "已发送", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ============================== 连接 ==============================

    private fun attemptConnect() {
        val ip = ipInput.text.toString().trim()
        if (ip.isEmpty()) {
            Toast.makeText(this, "请输入IP地址", Toast.LENGTH_SHORT).show()
            return
        }
        val portStr = portInput.text.toString().trim()
        val port = if (portStr.isNotEmpty()) portStr.toIntOrNull() ?: 58627 else 58627
        val token = tokenInput.text.toString().trim().ifEmpty { "541881452418845" }

        // 保存到 SharedPreferences
        val prefs = getSharedPreferences("phonehub_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("cached_port", port)
            .putString("cached_token", token)
            .apply()

        connectBtn.isEnabled = false
        connectStatus.text = "正在连接..."
        ConnectionManager.connect(ip, port, token)
    }

    // ============================== Flow 收集 ==============================

    private fun setupFlows() {
        lifecycleScope.launch {
            ConnectionManager.connectionState.collect { state ->
                when (state) {
                    ConnectionManager.ConnectionState.DISCONNECTED -> {
                        connectBtn.isEnabled = true
                        pawLoadingOverlay.visibility = View.GONE
                        val msg = ConnectionManager.connectionMessage.value
                        connectStatus.text = if (msg.startsWith("WiFi 连接失败") || msg.startsWith("ADB 连接失败")) msg else "未连接"
                        statusText.text = "未连接"
                        statusText.setTextColor(0xFFd13438.toInt())
                        // 清除首页缓存，下次进入时重建
                        pageCache.remove(0)
                        // 更新设置页连接信息
                        pageCache[16]?.findViewById<TextView>(R.id.infoStatus)?.text = "状态: 未连接"
                        pageCache[16]?.findViewById<TextView>(R.id.infoStatus)?.setTextColor(0xFFd13438.toInt())
                        pageCache[16]?.findViewById<TextView>(R.id.infoChannel)?.text = "通道: 无"
                        pageCache[16]?.findViewById<TextView>(R.id.infoIp)?.text = "IP: 未知"
                        updateSetupVisibility()
                    }
                    ConnectionManager.ConnectionState.CONNECTING -> {
                        connectBtn.isEnabled = false
                        val msg = ConnectionManager.connectionMessage.value
                        connectStatus.text = if (msg.startsWith("检测到 ADB 通道") || msg.startsWith("ADB 连接失败")) msg else "连接中..."
                        statusText.text = "连接中..."
                        statusText.setTextColor(0xFFffb900.toInt())
                        pageCache.remove(0)
                        pageCache[16]?.findViewById<TextView>(R.id.infoStatus)?.text = "状态: 连接中..."
                        pageCache[16]?.findViewById<TextView>(R.id.infoStatus)?.setTextColor(0xFFffb900.toInt())
                    }
                    ConnectionManager.ConnectionState.CONNECTED -> {
                        connectBtn.isEnabled = true
                        pawLoadingOverlay.visibility = View.GONE
                        connectStatus.text = "已连接"
                        statusText.text = "已连接"
                        statusText.setTextColor(0xFF107c10.toInt())
                        // 清除首页缓存，让首页重建时显示最新连接状态/通道
                        pageCache.remove(0)
                        // 更新设置页连接信息
                        pageCache[16]?.findViewById<TextView>(R.id.infoStatus)?.text = "状态: 已连接"
                        pageCache[16]?.findViewById<TextView>(R.id.infoStatus)?.setTextColor(0xFF107c10.toInt())
                        pageCache[16]?.findViewById<TextView>(R.id.infoIp)?.text = "IP: ${ConnectionManager.getPcIp() ?: "未知"}"
                        updateSetupVisibility()
                        // 连接成功后发送本地 URL 历史给电脑端同步
                        if (urlHistory.isEmpty()) loadUrlHistory()
                        ConnectionManager.sendUrlHistorySync(
                            urlHistory.map { Triple(it.url, it.direction, it.timestamp) }
                        )
                        try { ConnectionManager.sendAction("get_volume") } catch (_: Exception) {}
                    }
                }
            }
        }

        lifecycleScope.launch {
            ConnectionManager.connectionMessage.collect { msg ->
                val state = ConnectionManager.connectionState.value
                if (state == ConnectionManager.ConnectionState.DISCONNECTED && msg == "未连接") {
                    connectStatus.text = "未连接"
                } else {
                    connectStatus.text = msg
                }
            }
        }

        // 收集截图结果事件，显示 Toast 提示
        lifecycleScope.launch {
            ConnectionManager.screenshotResult.collect { msg ->
                val now = System.currentTimeMillis()
                if (msg == lastScreenshotToastMsg && now - lastScreenshotToastAtMs < 4000) return@collect
                lastScreenshotToastMsg = msg
                lastScreenshotToastAtMs = now
                android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // 全局监听电脑端发来的摄像头切换请求
        lifecycleScope.launch {
            ConnectionManager.cameraSwitchRequest.collect {
                performCameraSwitch(cameraPreviewRunning)
            }
        }

        // 全局监听电脑端发来的投屏命令（S5）
        lifecycleScope.launch {
            ConnectionManager.mirrorCommand.collect { cmd ->
                when (cmd.action) {
                    "start" -> {
                        // 电脑端请求开始投屏：和手机端点「开始投屏」走同一条路
                        // （没有授权时手机会弹系统授权框，地址由电脑端随命令带下来）
                        if (!mirrorRunning) {
                            if (PhoneHubAccessibilityService.instance == null) {
                                Toast.makeText(this@MainActivity, "无障碍服务未开启，正在引导开启...", Toast.LENGTH_LONG).show()
                                try {
                                    startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                } catch (_: Exception) {}
                            }
                            startMirrorFlow(cmd.ip, cmd.port)
                        }
                    }
                    "stop" -> {
                        if (mirrorRunning) stopMirrorFlow()
                    }
                }
            }
        }

        // 画质/音质/模式不再由电脑端下发：档位存在 PhoneHubMirrorService 里，
        // 由本页的三个下拉框直接写入（参考工程的做法）。

        lifecycleScope.launch {
            ConnectionManager.currentChannel.collect { channel ->
                val channelName = when (channel) {
                    ConnectionManager.ChannelType.WIFI -> "WiFi 直连"
                    // ConnectionManager.ChannelType.PAW -> "Cloudflare 隧道"  // 【禁止删除】PAW 通道显示
                    ConnectionManager.ChannelType.ADB -> "USB 数据线"
                    else -> "无"
                }
                pageCache[0]?.findViewById<TextView>(R.id.channelHome)?.text = "通道: $channelName"
                // 设置页是 index 17（非 16），修正后才能正常更新设置页连接信息
                pageCache[16]?.findViewById<TextView>(R.id.infoChannel)?.text = "通道: $channelName"
            }
        }

        // S6: 全局监听电脑端发来的声音传输控制指令（开始/停止「电脑声音→手机」）
        lifecycleScope.launch {
            ConnectionManager.audioControl.collect { cmd ->
                when (cmd.action) {
                    "start" -> {
                        ConnectionManager.startPcAudioPolling()
                        ConnectionManager.sendMediaCommand("audio_start")
                    }
                    "stop" -> {
                        ConnectionManager.stopPcAudioPolling()
                        ConnectionManager.sendMediaCommand("audio_stop")
                    }
                }
            }
        }

        lifecycleScope.launch {
            ConnectionManager.phoneMemUsage.collect { _ ->
                // 手机内存数据可用于其他页面
            }
        }

        lifecycleScope.launch {
            ConnectionManager.receivedClipboard.collect { text ->
                if (text != null) {
                    pageCache[4]?.findViewById<TextView>(R.id.currentClipText)?.text = text
                }
            }
        }

        // 电脑端媒体信息更新
        lifecycleScope.launch {
            ConnectionManager.mediaInfo.collect { info ->
                pageCache[2]?.findViewById<TextView>(R.id.mediaInfoText)?.text = info
            }
        }

        // collect receivedText 流，显示接收到的文字（基于内容去重，5秒内相同内容不重复弹窗）
        lifecycleScope.launch {
            ConnectionManager.receivedText.collect { (filename, text) ->
                val key = "$filename|$text"
                val lastHandled = handledTextContents[key]
                if (lastHandled != null && System.currentTimeMillis() - lastHandled < 5000) {
                    return@collect  // 5秒内已处理过，跳过
                }
                showReceivedTextDialog(filename, text)
            }
        }

        // 文件传输进度收集：包含速度采样（最近 4 秒）、UI 状态切换、完成按钮
        lifecycleScope.launch {
            // 手机端速度采样：(ts_ms, sent_bytes)，用于平滑速度显示
            val speedSamples = ArrayDeque<Pair<Long, Long>>()
            var lastTransferDir = "" // 记录"发送中"/"接收中"用于完成时区分
            ConnectionManager.fileTransferProgress.collect { progress ->
                val v = pageCache[1] ?: return@collect
                if (progress != null) {
                    // 传输开始：显示进度条容器和按钮容器
                    v.findViewById<LinearLayout>(R.id.fileProgressContainer)?.visibility = View.VISIBLE
                    v.findViewById<LinearLayout>(R.id.fileTransferBtnContainer)?.visibility = View.VISIBLE
                    v.findViewById<TextView>(R.id.fileNameText)?.text = progress.fileName
                    val pct = if (progress.total > 0) ((progress.sent * 100) / progress.total).toInt() else 0
                    v.findViewById<ProgressBar>(R.id.fileProgress)?.progress = pct
                    // 智能格式化大小
                    fun fmtSize(b: Long): String {
                        return when {
                            b >= 1024L * 1024 * 1024 -> "%.2f GB".format(b / (1024.0 * 1024 * 1024))
                            b >= 1024L * 1024 -> "%.1f MB".format(b / (1024.0 * 1024))
                            b >= 1024L -> "%.0f KB".format(b / 1024.0)
                            else -> "$b B"
                        }
                    }
                    v.findViewById<TextView>(R.id.fileProgressText)?.text = "${fmtSize(progress.sent)} / ${fmtSize(progress.total)}"
                    v.findViewById<Button>(R.id.cancelFileBtn)?.isEnabled = true
                    v.findViewById<Button>(R.id.pauseFileBtn)?.isEnabled = true
                    v.findViewById<Button>(R.id.pauseFileBtn)?.text = "暂停"
                    v.findViewById<Button>(R.id.selectFileBtn)?.isEnabled = false
                    v.findViewById<Button>(R.id.doneFileBtn)?.visibility = View.GONE
                    v.findViewById<Button>(R.id.doneFileBtn)?.isEnabled = false
                    // 记录方向标记以便完成时区分
                    if (progress.receiving) {
                        lastTransferDir = "接收"
                    } else {
                        lastTransferDir = "发送"
                    }
                    // 采样（仅有效进度）
                    if (progress.sent > 0) {
                        val now = System.currentTimeMillis()
                        speedSamples.addLast(now to progress.sent)
                        // 仅保留 4 秒内的样本
                        while (speedSamples.isNotEmpty() && now - speedSamples.first().first > 4000) {
                            speedSamples.removeFirst()
                        }
                        // 计算速度（需要至少 2 个样本）
                        if (speedSamples.size >= 2) {
                            val first = speedSamples.first()
                            val last = speedSamples.last()
                            val dt = (last.first - first.first).coerceAtLeast(100L)
                            val speedBps = ((last.second - first.second) * 1000L / dt).toDouble()
                            // 暂停期间不计算速度和剩余时间
                            if (ConnectionManager.transferPausedFromPc.value) {
                                v.findViewById<TextView>(R.id.fileSpeedText)?.text = "已暂停"
                            } else if (speedBps > 0) {
                                val speedText = if (speedBps >= 1024.0 * 1024) {
                                    "%.2f MB/s".format(speedBps / (1024.0 * 1024.0))
                                } else {
                                    "%.0f KB/s".format(speedBps / 1024.0)
                                }
                                // 计算剩余时间
                                val remaining = progress.total - progress.sent
                                val etaSec: Double = if (speedBps > 0) remaining / speedBps else 0.0
                                val etaStr = if (etaSec > 0 && remaining > 0) {
                                    val sec = etaSec.toInt()
                                    if (sec < 60) "${sec}秒"
                                    else if (sec < 3600) "${sec / 60}分${sec % 60}秒"
                                    else "${sec / 3600}时${(sec % 3600) / 60}分"
                                } else ""
                                v.findViewById<TextView>(R.id.fileSpeedText)?.text = if (etaStr.isNotEmpty()) "$speedText · 剩余 $etaStr" else speedText
                            }
                        }
                    }
                } else {
                    // 进度被清空 - 区分完成/取消/中断
                    speedSamples.clear()
                    if (ConnectionManager.transferCompleted.value) {
                        // 真正完成：显示"完成"按钮
                        v.findViewById<ProgressBar>(R.id.fileProgress)?.progress = 100
                        v.findViewById<Button>(R.id.cancelFileBtn)?.isEnabled = false
                        v.findViewById<Button>(R.id.pauseFileBtn)?.isEnabled = false
                        v.findViewById<Button>(R.id.pauseFileBtn)?.text = "暂停"
                        v.findViewById<Button>(R.id.selectFileBtn)?.isEnabled = true
                        v.findViewById<Button>(R.id.doneFileBtn)?.visibility = View.VISIBLE
                        v.findViewById<Button>(R.id.doneFileBtn)?.isEnabled = true
                        val nameView = v.findViewById<TextView>(R.id.fileNameText)
                        nameView?.text = "${lastTransferDir}完成: ${nameView?.text ?: ""}"
                    } else {
                        // 取消/中断：重置界面到空闲状态，允许选择新文件
                        v.findViewById<ProgressBar>(R.id.fileProgress)?.progress = 0
                        v.findViewById<TextView>(R.id.fileProgressText)?.text = ""
                        v.findViewById<TextView>(R.id.fileSpeedText)?.text = ""
                        v.findViewById<Button>(R.id.cancelFileBtn)?.isEnabled = false
                        v.findViewById<Button>(R.id.pauseFileBtn)?.isEnabled = false
                        v.findViewById<Button>(R.id.pauseFileBtn)?.text = "暂停"
                        v.findViewById<Button>(R.id.selectFileBtn)?.isEnabled = true
                        v.findViewById<Button>(R.id.doneFileBtn)?.visibility = View.GONE
                        v.findViewById<Button>(R.id.doneFileBtn)?.isEnabled = false
                        v.findViewById<LinearLayout>(R.id.fileProgressContainer)?.visibility = View.GONE
                        v.findViewById<LinearLayout>(R.id.fileTransferBtnContainer)?.visibility = View.GONE
                    }
                }
            }
        }

        // 订阅 PC 发来的暂停事件，同步手机端按钮文字
        lifecycleScope.launch {
            ConnectionManager.transferPausedFromPc.collect { paused ->
                val v = pageCache[1] ?: return@collect
                val btn = v.findViewById<Button>(R.id.pauseFileBtn) ?: return@collect
                if (paused) {
                    btn.text = "继续"
                    v.findViewById<TextView>(R.id.fileSpeedText)?.text = "已暂停(对端)"
                } else {
                    btn.text = "暂停"
                    v.findViewById<TextView>(R.id.fileSpeedText)?.text = "继续传输..."
                }
            }
        }

        // 订阅 PC 发来的取消事件，重置手机端界面并提示用户
        lifecycleScope.launch {
            ConnectionManager.transferCancelledFromPc.collect { _ ->
                val v = pageCache[1] ?: return@collect
                v.findViewById<TextView>(R.id.fileNameText)?.text = "对端已取消"
                v.findViewById<ProgressBar>(R.id.fileProgress)?.progress = 0
                v.findViewById<TextView>(R.id.fileProgressText)?.text = ""
                v.findViewById<TextView>(R.id.fileSpeedText)?.text = ""
                v.findViewById<Button>(R.id.cancelFileBtn)?.isEnabled = false
                v.findViewById<Button>(R.id.pauseFileBtn)?.isEnabled = false
                v.findViewById<Button>(R.id.pauseFileBtn)?.text = "暂停"
                v.findViewById<Button>(R.id.selectFileBtn)?.isEnabled = true
                v.findViewById<Button>(R.id.doneFileBtn)?.visibility = View.GONE
                v.findViewById<Button>(R.id.doneFileBtn)?.isEnabled = false
                v.findViewById<LinearLayout>(R.id.fileProgressContainer)?.visibility = View.GONE
                v.findViewById<LinearLayout>(R.id.fileTransferBtnContainer)?.visibility = View.GONE
                // Toast 提示用户
                android.widget.Toast.makeText(this@MainActivity, "电脑端已取消文件传输", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        lifecycleScope.launch {
            ConnectionManager.clipboardHistory.collect {
                // 剪贴板历史已合并到剪贴板页（index 4），清缓存让下次重建
                pageCache.remove(4)
            }
        }
        lifecycleScope.launch {
            ConnectionManager.clipboardFavorites.collect {
                pageCache.remove(4)
            }
        }
        // save.md 功能7：收集电脑画面帧并显示（2秒无新帧则清空）
        lifecycleScope.launch(Dispatchers.IO) {
            ConnectionManager.pcFrame.collect { jpegBytes ->
                val bmp = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                if (bmp != null) {
                    withContext(Dispatchers.Main) {
                        mirrorImageView?.setImageBitmap(bmp)
                        // 重置超时计时
                        mirrorFrameTimeoutRunnable?.let { frameTimeoutHandler.removeCallbacks(it) }
                        mirrorFrameTimeoutRunnable = Runnable {
                            if (isMirrorFullscreen) {
                                exitMirrorFullscreen()
                            }
                            mirrorImageView?.setImageBitmap(null)
                            ConnectionManager.sendMediaCommand("pc_stream_stop")
                            ConnectionManager.stopPcFramePolling()
                        }.also { frameTimeoutHandler.postDelayed(it, 2000) }
                    }
                }
            }
        }
        // save.md 功能8：收集电脑摄像头帧并显示（2秒无新帧则清空）
        lifecycleScope.launch(Dispatchers.IO) {
            ConnectionManager.pcCameraFrame.collect { jpegBytes ->
                val bmp = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                if (bmp != null) {
                    withContext(Dispatchers.Main) {
                        cameraImageView?.setImageBitmap(bmp)
                        // 重置超时计时
                        cameraFrameTimeoutRunnable?.let { frameTimeoutHandler.removeCallbacks(it) }
                        cameraFrameTimeoutRunnable = Runnable {
                            cameraImageView?.setImageBitmap(null)
                            cameraImageView?.visibility = android.view.View.GONE
                            ConnectionManager.stopPcCameraPush()
                            pageCache[9]?.findViewById<TextView>(R.id.cameraStatus)?.text = "电脑摄像头已断开"
                        }.also { frameTimeoutHandler.postDelayed(it, 2000) }
                    }
                }
            }
        }
    }

    /**
     * 收到电脑端发送的文字后的弹窗：
     * - 显示标题和内容（深色模式）
     * - "复制"按钮：复制内容到系统剪贴板
     * - "保存"按钮：先输入文件后缀（默认留空=txt），再用系统文件选择器选择保存路径写入文件
     * - "关闭"按钮
     */
    private fun showReceivedTextDialog(filename: String, textContent: String) {
        if (isFinishing) return
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
            setBackgroundColor(0xFF1e1e1e.toInt())
        }
        val title = TextView(this).apply {
            text = "收到文字: $filename"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setPadding(0, 0, 0, 24)
        }
        val content = TextView(this).apply {
            this.text = textContent
            setTextColor(0xFFffffff.toInt())
            textSize = 14f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF2d2d2d.toInt())
            minLines = 3
        }
        // 复制按钮
        val copyBtn = Button(this).apply { text = "复制" }
        copyBtn.applyDarkTheme()
        // 保存按钮（原"保存为 .txt"改为"保存"）
        val saveBtn = Button(this).apply { text = "保存" }
        saveBtn.applyDarkTheme(primary = true)
        val closeBtn = Button(this).apply { text = "关闭" }
        closeBtn.applyDarkTheme()
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

        // 复制内容到系统剪贴板
        copyBtn.setOnClickListener {
            try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("PhoneHub", textContent))
                Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                val key = "$filename|$textContent"
                handledTextContents[key] = System.currentTimeMillis()
            } catch (e: Exception) {
                Toast.makeText(this, "复制失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // 保存：自动检测文件名中的后缀，有则直接用，无则弹输入框
        saveBtn.setOnClickListener {
            // 如果 filename 已包含后缀，直接使用
            val hasExtension = filename.contains('.') && !filename.endsWith('.') && filename.substringAfterLast('.', "").isNotEmpty()
            if (hasExtension) {
                pendingSaveText = textContent
                saveTextLauncher.launch(filename)
            } else {
                val extInput = EditText(this).apply {
                    hint = "文件后缀（留空为 txt）"
                    setTextColor(0xFFFFFFFF.toInt())
                    setHintTextColor(0xFF666666.toInt())
                    inputType = InputType.TYPE_CLASS_TEXT
                }
                AlertDialog.Builder(this)
                    .setTitle("输入文件后缀")
                    .setView(extInput)
                    .setPositiveButton("下一步") { _, _ ->
                        var ext = extInput.text.toString().trim().trimStart('.')
                        if (ext.isEmpty()) ext = "txt"
                        pendingSaveText = textContent
                        val baseName = if (filename.isBlank()) {
                            "received_${System.currentTimeMillis()}"
                        } else {
                            filename.substringBeforeLast('.', filename)
                        }
                        saveTextLauncher.launch("$baseName.$ext")
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    private fun updateSetupVisibility() {
        Handler(Looper.getMainLooper()).post {
            val isConnected = ConnectionManager.connectionState.value == ConnectionManager.ConnectionState.CONNECTED
            if (isConnected || ConnectionManager.hasReceivedPcCpu()) {
                setupScreen.visibility = View.GONE
                mainContainer.visibility = View.VISIBLE
                if (pageContainer.childCount == 0) switchTab(0)
            } else {
                setupScreen.visibility = View.VISIBLE
                mainContainer.visibility = View.GONE
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTextNotificationIntent(intent)
        handleFileTransferNotificationIntent(intent)
    }

    /**
     * 处理通知点击 Intent：若带有 show_text_dialog 标记，显示最近一次收到的文字对话框
     */
    private fun handleTextNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("show_text_dialog", false) == true) {
            ConnectionManager.lastReceivedText?.let { (filename, txt) ->
                showReceivedTextDialog(filename, txt)
            }
            intent.removeExtra("show_text_dialog")
        }
    }

    /**
     * 处理文件接收通知点击 Intent：跳转到文件传输页（tab index 1）
     */
    private fun handleFileTransferNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("show_file_transfer", false) == true) {
            switchTab(1)
            intent.removeExtra("show_file_transfer")
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        when (requestCode) {
            SELECT_FILE_CODE -> {
                data?.data?.let { uri ->
                    ConnectionManager.sendFile(uri)
                    // 尝试获取文件名用于提示
                    val cr = contentResolver
                    var name = "file"
                    try {
                        cr.query(uri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (idx >= 0) cursor.getString(idx)?.let { name = it }
                            }
                        }
                    } catch (_: Exception) {}
                    Toast.makeText(this, "开始发送: $name", Toast.LENGTH_SHORT).show()
                }
            }
            SELECT_APK_CODE -> {
                data?.data?.let { uri ->
                    // 复制整个 APK 到缓存目录移入 IO 线程，避免大文件主线程复制导致 ANR
                    lifecycleScope.launch {
                        val file = withContext(Dispatchers.IO) { uriToFile(uri) }
                        if (file != null && file.exists()) {
                            installApk(file)
                        }
                    }
                }
            }
        }
    }

    private fun uriToFile(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(cacheDir, "temp_send_${System.currentTimeMillis()}")
            tempFile.outputStream().use { output -> inputStream.copyTo(output) }
            inputStream.close()
            tempFile
        } catch (e: Exception) {
            Log.e("MainActivity", "uriToFile failed", e)
            null
        }
    }

    override fun onResume() {
        super.onResume()
        updateSetupVisibility()
        // 更新剪贴板页面当前内容
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
        pageCache[4]?.findViewById<TextView>(R.id.currentClipText)?.text = text
    }

    // ============================== 投屏页的档位与连接（UI 逻辑来自参考工程）==============================

    /** 三个下拉框：清晰度 / 音质 / 模式。值与参考工程一致，存在 PhoneHubMirrorService 的 prefs 里。 */
    private fun setupMirrorSpinners(v: View) {
        val quality = v.findViewById<android.widget.Spinner>(R.id.mirQuality)
        val audio = v.findViewById<android.widget.Spinner>(R.id.mirAudio)
        val mode = v.findViewById<android.widget.Spinner>(R.id.mirMode)

        val qualityOptions = arrayOf("流畅 · 400宽", "标清 · 720宽", "高清 · 1080宽", "原生 · 2340宽")
        val qAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, qualityOptions)
        qAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        quality.adapter = qAdapter
        quality.setSelection(PhoneHubMirrorService.getQualityLevel(this))
        quality.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == PhoneHubMirrorService.getQualityLevel(this@MainActivity)) return
                PhoneHubMirrorService.setQualityLevel(this@MainActivity, position)
                applyMirrorSettingsIfRunning()      // 切清晰度 → 重新投屏
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val audioOptions = arrayOf("标准 · 48k", "高 · 96k", "很高 · 192k")
        val aAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, audioOptions)
        aAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        audio.adapter = aAdapter
        audio.setSelection(PhoneHubMirrorService.getAudioLevel(this))
        audio.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == PhoneHubMirrorService.getAudioLevel(this@MainActivity)) return
                PhoneHubMirrorService.setAudioLevel(this@MainActivity, position)
                applyMirrorSettingsIfRunning()      // 切音质 → 重新投屏
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val modeOptions = arrayOf("音视频", "仅音频", "仅画面")
        val mAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, modeOptions)
        mAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mode.adapter = mAdapter
        mode.setSelection(PhoneHubMirrorService.getMirrorMode(this))
        mode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == PhoneHubMirrorService.getMirrorMode(this@MainActivity)) return
                PhoneHubMirrorService.setMirrorMode(this@MainActivity, position)
                applyMirrorSettingsIfRunning()      // 切模式（如仅音频）→ 重新投屏
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    /** 「连接」按钮：探一下电脑上的投屏服务（参考工程同款，支持连多台，投屏时同时推流） */
    private fun connectMirrorPc(ip: String, port: Int) {
        if (ip.isBlank()) {
            Toast.makeText(this, "请输入电脑 IP 地址", Toast.LENGTH_SHORT).show()
            return
        }
        // 华为/荣耀：没豁免电池优化时先申请，否则后台投屏会被冻结（表现为周期性卡 ~1s）
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(
                        Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:$packageName")
                        )
                    )
                    setMirrorStatus("请在弹窗中点「允许」，然后重新点「连接」")
                    return
                } catch (_: Exception) {
                    // 个别 ROM 没有这个入口，直接连
                }
            }
        } catch (_: Exception) {
        }

        setMirrorStatus("正在连接 $ip:$port ...")
        Thread {
            val err = probeMirrorServer(ip, port)
            runOnUiThread {
                if (err == null) {
                    val isNew = !PhoneHubMirrorService.getSavedPcEndpoints(this)
                        .any { it.first == ip && it.second == port }
                    PhoneHubMirrorService.addSavedPcEndpoint(this, ip, port)
                    val n = PhoneHubMirrorService.savedPcCount(this)
                    val multi = if (n > 1) "（共 $n 台，投屏时同时推流）" else ""
                    val mfr = Build.MANUFACTURER ?: ""
                    val hint = if (mfr.contains("HUAWEI", true) || mfr.contains("HONOR", true))
                        " ｜建议:手机管家→应用启动管理→本应用→手动管理(开关全开)" else ""
                    val added = if (isNew) "已添加" else "已在列表"
                    setMirrorStatus("已连接 $ip:$port ✓ $added$multi 可以点「开始投屏」$hint")
                } else {
                    setMirrorStatus("连接失败: $err")
                    Toast.makeText(this, "连接失败", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /** 探活：GET http://ip:port/status；返回 null 表示通，否则是错误描述 */
    private fun probeMirrorServer(ip: String, port: Int): String? {
        return try {
            val conn = java.net.URL("http://$ip:$port/status").openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            val code = conn.responseCode
            conn.disconnect()
            if (code == 200) null else "HTTP $code"
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message}"
        }
    }
}
