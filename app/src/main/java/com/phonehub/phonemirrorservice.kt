package com.phonehub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.Manifest
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 投屏前台服务：把系统产出的每一帧 HTTP POST 到 PC
 *
 * ── 架构 ────────────────────────────────────────────────
 *
 * 1) 采集与上传**彻底解耦**
 *
 *      采集线程（HandlerThread）            发送线程 x2
 *      onImageAvailable                      poll
 *        → 转换/压缩（不碰网络）                → POST /upload（带 X-Capture-Ts）
 *        → 放入发送队列（满了丢最旧的）          → 统计成功/失败
 *
 *    早期版本在采集回调里同步上传，网络一个来回多久采集就被卡住多久
 *    （帧率上限 = 1/(编解码 + 网络往返)）。拆开后采集只受编解码成本限制，
 *    网络慢只会让队列堆积并丢掉**最旧**的帧，画面始终是"当下"的。
 *
 * 2) **虚拟屏直接建在小尺寸上**（唯一路径，2026-09-10 定稿）
 *
 *    VirtualDisplay / ImageReader 都建在 400 宽（按真机比例算高，dpi 同比缩放）。
 *    AUTO_MIRROR 会把主屏内容缩放输出到 surface，应用布局不受影响，
 *    每帧数据量降到 1/7，CPU 缩放整步省掉。实测比"真机分辨率 + CPU 缩小"快得多。
 *
 *    注意：这条路径会改动显示配置，**必须**配合 AndroidManifest 里 MainActivity 的
 *    `android:configChanges="...|density|screenLayout|smallestScreenSize|..."`，
 *    否则 Activity 会被系统重建，界面状态被清空、看起来像"卡死"。
 *
 * 3) 每帧带 X-Capture-Ts（相对投屏起点的毫秒数），PC 端按这个时间戳铺时间轴，
 *    网络抖动和两个发送线程的乱序都不会让画面忽快忽慢。
 *
 * ── 历史修复 ────────────────────────────────────────────
 *  · imageToBitmap 忽略 rowStride → 荣耀机型斜条纹（现在按 paddedWidth 拷贝再裁剪）。
 *  · 上传失败静默、帧数照加 → 界面"已发 N 帧"是假象（现在统计成功/失败）。
 *  · 停止指令不能用 startForegroundService 发（前台服务契约会违约，App 会崩）。
 *  · stopMirror 里不能有 `if (!isRunning) return`，否则服务卡在非运行态时停不掉。
 */
class PhoneHubMirrorService : Service() {

    companion object {
        const val TAG = "MirrorSvc"
        const val ACTION_START_MIRROR = "com.phonehub.mirror.ACTION_START_MIRROR"
        const val ACTION_STOP_MIRROR = "com.phonehub.mirror.ACTION_STOP_MIRROR"

        // 参考工程用 LocalBroadcastManager 把状态广播给界面；搬进主工程后改成
        // StateFlow（主工程本来就用协程流，也省掉一个 androidx 依赖）。
        private val _fpsFlow = MutableStateFlow("")
        val fpsFlow: StateFlow<String> = _fpsFlow
        private val _resultFlow = MutableStateFlow("")
        val resultFlow: StateFlow<String> = _resultFlow

        const val EXTRA_PC_IP = "pc_ip"
        const val EXTRA_PC_PORT = "pc_port"

        private const val NOTIFICATION_ID = 9001
        private const val CHANNEL_ID = "mirror_service"
        private const val PREFS_NAME = "mirror_prefs"

        // 画质设置：4个级别（JPEG质量 + 分辨率宽度 + 单帧大小上限）
        // 帧上限必须跟画质一起升：否则 40/60/80 档的每一帧都会超过 40KB
        // 被强行缩到 70% 再压回 25，画质档位等于没用
        private val QUALITY_LEVELS = intArrayOf(25, 40, 60, 80)  // 从低到高
        private val RESOLUTION_WIDTHS = intArrayOf(400, 720, 1080, 2340)  // 对应宽度（超过真机宽=原生）
        private val FRAME_SIZE_CAPS = intArrayOf(40_000, 160_000, 500_000, 1_500_000)

        private const val UPLOAD_THREADS = 3          // 每台 PC 的上传线程数
        private const val SEND_QUEUE_CAP = 8
        private const val AUDIO_QUEUE_CAP = 8         // 每台 PC 的音频队列（块）
        private const val MAX_CONSEC_FAIL = 60

        // 启动后这么久还没出帧就在状态栏提示一下，避免"无声地卡住"
        private const val NO_FRAME_NOTICE_MS = 3000L

        // ── 内部录音（AudioPlaybackCapture，Android 10+）──
        // 音频质量设置：3个级别（采样率 + 块大小）
        // 注意：Android AudioFormat 采集路径最高可靠支持 16bit PCM，
        //       24bit 需要用 packed/float 且 AudioPlaybackCapture 不支持，已按用户要求去掉极高档
        private val AUDIO_SAMPLE_RATES = intArrayOf(48000, 96000, 192000)  // 48k, 96k, 192k
        private val AUDIO_CHUNK_MS_LIST = intArrayOf(60, 40, 30)  // 60ms, 40ms, 30ms
        private const val AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_STEREO  // 固定2声道
        private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT  // 固定16bit编码
        private const val AUDIO_UPLOAD_TIMEOUT_MS = 8000

        // ── 投屏模式：运行中可随时无缝切换 ──
        const val MODE_BOTH = 0        // 音视频
        const val MODE_AUDIO_ONLY = 1  // 仅音频：手机端不发送画面
        const val MODE_VIDEO_ONLY = 2  // 仅画面：手机端不发送声音

        @Volatile
        var mirrorMode: Int = MODE_BOTH

        // 运行中的服务实例：设置项改动实时下发（画质/模式），不必重启投屏
        @Volatile
        var instance: PhoneHubMirrorService? = null

        @Volatile
        var pendingResultCode: Int = -1
        @Volatile
        var pendingResultData: Intent? = null

        @Volatile
        var heldProjection: MediaProjection? = null

        fun hasProjection(): Boolean = heldProjection != null

        fun setPendingResult(code: Int, data: Intent) {
            pendingResultCode = code
            pendingResultData = data
        }

        /** 持久化授权结果到 SharedPreferences，避免重复弹窗 */
        fun saveProjectionResult(context: Context, code: Int, data: Intent?) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putInt("proj_code", code)
                data?.let {
                    // 将 Intent 序列化存储（简化处理，只存储必要字段）
                    putString("proj_data_uri", it.data?.toString())
                    putString("proj_data_type", it.type)
                    putInt("proj_data_flags", it.flags)
                }
                apply()
            }
            Log.i(TAG, "已保存 MediaProjection 授权结果（永不超时）")
        }

        /** 从 SharedPreferences 恢复授权结果 */
        fun loadProjectionResult(context: Context): Pair<Int, Intent?>? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val code = prefs.getInt("proj_code", -1)
            if (code == -1) return null

            val dataUri = prefs.getString("proj_data_uri", null)
            val dataType = prefs.getString("proj_data_type", null)
            val dataFlags = prefs.getInt("proj_data_flags", 0)

            // 授权结果永不超时，只要用户不清除缓存就一直有效
            return try {
                val data = if (dataUri != null) {
                    Intent().apply {
                        data = android.net.Uri.parse(dataUri)
                        type = dataType
                        flags = dataFlags
                    }
                } else null
                Pair(code, data)
            } catch (e: Exception) {
                Log.e(TAG, "恢复授权结果失败", e)
                null
            }
        }

        /** 清除缓存的授权结果 */
        fun clearProjectionResult(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            Log.i(TAG, "已清除缓存的授权结果")
        }

        /** 获取画质级别（0-3） */
        fun getQualityLevel(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt("quality_level", 0)  // 默认最低画质
        }

        /** 设置画质级别（运行中即时生效：改 JPEG 质量与帧上限，分辨率变了重建虚拟屏） */
        fun setQualityLevel(context: Context, level: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lv = level.coerceIn(0, QUALITY_LEVELS.size - 1)
            prefs.edit().putInt("quality_level", lv).apply()
            instance?.let { svc ->
                svc.currentJpegQuality = QUALITY_LEVELS[lv]
                svc.currentMaxFrameSize = FRAME_SIZE_CAPS[lv]
                if (svc.isRunning && RESOLUTION_WIDTHS[lv] != svc.appliedTargetWidth) {
                    svc.requestRebuildDisplay()
                }
            }
        }

        /** 获取音频质量级别（0-2） */
        fun getAudioLevel(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt("audio_level", 0).coerceIn(0, AUDIO_SAMPLE_RATES.size - 1)
        }

        /** 设置音频质量级别（采样率/块大小在下次开始投屏时生效） */
        fun setAudioLevel(context: Context, level: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt("audio_level", level.coerceIn(0, AUDIO_SAMPLE_RATES.size - 1)).apply()
        }

        /** 获取投屏模式（0=音视频 1=仅音频 2=仅画面） */
        fun getMirrorMode(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt("mirror_mode", MODE_BOTH).coerceIn(0, 2)
        }

        /** 设置投屏模式（运行中即时生效） */
        fun setMirrorMode(context: Context, mode: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val m = mode.coerceIn(0, 2)
            prefs.edit().putInt("mirror_mode", m).apply()
            mirrorMode = m   // @Volatile，采集/音频循环每帧每块都会读到
        }

        // ── 多 PC 目标列表（"ip:port" 集合，随连接保存）──
        fun getSavedPcEndpoints(context: Context): List<Pair<String, Int>> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return (prefs.getStringSet("pc_endpoints", emptySet()) ?: emptySet()).mapNotNull { e ->
                val i = e.lastIndexOf(':')
                if (i <= 0) null else try {
                    Pair(e.substring(0, i), e.substring(i + 1).toInt())
                } catch (_: Exception) {
                    null
                }
            }
        }

        fun addSavedPcEndpoint(context: Context, ip: String, port: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val set = (prefs.getStringSet("pc_endpoints", emptySet()) ?: emptySet()).toMutableSet()
            set.add("$ip:$port")
            prefs.edit().putStringSet("pc_endpoints", set).apply()
        }

        fun clearSavedPcEndpoints(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove("pc_endpoints").apply()
        }

        fun savedPcCount(context: Context): Int = getSavedPcEndpoints(context).size

        /** 获取当前画质的JPEG质量 */
        fun getCurrentJpegQuality(context: Context): Int {
            val level = getQualityLevel(context)
            return QUALITY_LEVELS[level]
        }

        /** 获取当前画质的分辨率宽度 */
        fun getCurrentResolutionWidth(context: Context): Int {
            val level = getQualityLevel(context)
            return RESOLUTION_WIDTHS[level]
        }

        /** 获取当前档位的单帧大小上限 */
        fun getCurrentMaxFrameSize(context: Context): Int {
            val level = getQualityLevel(context)
            return FRAME_SIZE_CAPS[level]
        }

        /** 获取当前音频的采样率 */
        fun getCurrentAudioSampleRate(context: Context): Int {
            val level = getAudioLevel(context)
            return AUDIO_SAMPLE_RATES[level]
        }

        /** 获取当前音频的块大小（毫秒） */
        fun getCurrentAudioChunkMs(context: Context): Int {
            val level = getAudioLevel(context)
            return AUDIO_CHUNK_MS_LIST[level]
        }
    }

    private class FramePacket(val seq: Long, val tsMs: Long, val data: ByteArray)

    // ── 采集侧 ──
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var paddedBitmap: Bitmap? = null
    private var mirrorStartMs = 0L
    private val seqGen = AtomicLong(0)

    // ── 内部录音 ──
    private var audioRecord: AudioRecord? = null

    // 后台防卡顿双锁：App 退后台/灭屏后，系统会降频、Wi-Fi 进省电（攒包发送），
    // 表现为音乐和画面一起周期性卡 ~1s。PARTIAL_WAKE_LOCK 保 CPU 不睡，
    // LOW_LATENCY WifiLock 让 Wi-Fi 灭屏后仍以低延迟收发包。
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var audioThread: Thread? = null
    @Volatile private var audioRunning = false
    @Volatile private var audioOkCount = 0
    @Volatile private var audioFailCount = 0
    @Volatile private var audioBytes = 0L
    @Volatile private var lastAudioErr: String? = null

    // 当前采集尺寸（虚拟屏 = ImageReader 尺寸）
    @Volatile private var capW = 0
    @Volatile private var capH = 0
    @Volatile private var appliedTargetWidth = 0   // 当前虚拟屏应用的目标宽度（画质档位）

    // 当前画质和音频设置
    @Volatile private var currentJpegQuality = 25
    @Volatile private var currentMaxFrameSize = 40_000
    @Volatile private var currentAudioSampleRate = 48000
    @Volatile private var currentAudioChunkMs = 60

    // ── 发送侧：支持同时向多台 PC 推流（采集一份，每台 PC 独立队列/独立线程）──
    private class PcSender(val ip: String, val port: Int) {
        val frameQ = LinkedBlockingDeque<FramePacket>(SEND_QUEUE_CAP * 2)
        val audioQ = LinkedBlockingDeque<AudioChunk>(AUDIO_QUEUE_CAP)
        @Volatile var alive = true
        @Volatile var ok = 0L
        @Volatile var fail = 0
        @Volatile var consecFail = 0
        @Volatile var lastErr: String? = null
        override fun toString() = "$ip:$port"
    }

    private class AudioChunk(val seq: Long, val data: ByteArray)

    private val senders = mutableListOf<PcSender>()
    private val uploaders = mutableListOf<Thread>()

    @Volatile
    private var uploadersRunning = false

    @Volatile
    private var isRunning = false

    // 已按哪个方向建过虚拟屏（用于检测手机旋转后重建，让电脑端画面跟随横竖屏）
    @Volatile
    private var lastAppliedRotation = -1

    // ── 统计（给界面看，用来定位瓶颈） ──
    private var okCount = 0
    private var failCount = 0
    private var consecFail = 0
    private var capturedCount = 0
    private var droppedCount = 0
    private var lastOkSnapshot = 0
    private var lastOkSnapshotTs = 0L

    @Volatile private var msConvert = 0L
    @Volatile private var msCompress = 0L
    @Volatile private var msUpload = 0L
    @Volatile private var lastFrameBytes = 0

    private var fpsWindowStart = 0L
    private var windowFrames = 0

    private var pcIp = "192.168.3.9"
    private var pcPort = 5423

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForegroundCompat()
        LogUtil.scrI("[投屏][MIR] 投屏服务已创建")
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 不管什么指令都先履行前台服务契约，否则 Android 8+ 会抛
        // ForegroundServiceDidNotStartInTimeException（停止指令走的就是这条路）
        startForegroundCompat()

        when (intent?.action) {
            ACTION_START_MIRROR -> {
                pcIp = intent.getStringExtra(EXTRA_PC_IP) ?: "192.168.3.9"
                pcPort = intent.getIntExtra(EXTRA_PC_PORT, 5423)
                LogUtil.scrI("[投屏][MIR] 开始投屏 → $pcIp:$pcPort")
                startForegroundCompat()
                startMirror()
            }
            ACTION_STOP_MIRROR -> {
                LogUtil.scrI("[投屏][MIR] 收到停止投屏指令")
                stopMirror()
            }
            else -> {
                Log.w(TAG, "收到空指令，收尾退出")
                finishLoop()
            }
        }
        return START_NOT_STICKY
    }

    private fun startMirror() {
        if (isRunning) return

        val resultCode = pendingResultCode
        val resultData = pendingResultData
        if (resultData == null) {
            LogUtil.scrE("[投屏][MIR] 没有 MediaProjection 授权，无法开始投屏")
            sendResult("请先授权屏幕录制（点击'开始投屏录制'）")
            return
        }

        isRunning = true
        okCount = 0
        failCount = 0
        consecFail = 0
        capturedCount = 0
        droppedCount = 0
        lastOkSnapshot = 0
        lastOkSnapshotTs = SystemClock.elapsedRealtime()
        windowFrames = 0
        fpsWindowStart = SystemClock.elapsedRealtime()
        seqGen.set(0)

        // 从设置中加载画质和音频参数
        currentJpegQuality = PhoneHubMirrorService.getCurrentJpegQuality(this)
        currentMaxFrameSize = PhoneHubMirrorService.getCurrentMaxFrameSize(this)
        currentAudioSampleRate = PhoneHubMirrorService.getCurrentAudioSampleRate(this)
        currentAudioChunkMs = PhoneHubMirrorService.getCurrentAudioChunkMs(this)
        mirrorMode = PhoneHubMirrorService.getMirrorMode(this)

        // 目标 PC 列表：连接过的所有电脑（多台同时推流）；为空则回退 intent 单地址
        senders.clear()
        val eps = PhoneHubMirrorService.getSavedPcEndpoints(this)
        if (eps.isNotEmpty()) {
            for ((ip, port) in eps) senders.add(PcSender(ip, port))
        } else {
            senders.add(PcSender(pcIp, pcPort))
        }
        Log.i(TAG, "目标 PC: $senders")

        Log.i(TAG, "当前设置: 画质级别=${PhoneHubMirrorService.getQualityLevel(this)} (JPEG质量=$currentJpegQuality, 分辨率宽度=${PhoneHubMirrorService.getCurrentResolutionWidth(this)}), 音频级别=${PhoneHubMirrorService.getAudioLevel(this)} (采样率=$currentAudioSampleRate, 块大小=${currentAudioChunkMs}ms)")

        try {
            val mpMgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            var mp = heldProjection
            if (mp == null) {
                mp = mpMgr.getMediaProjection(resultCode, resultData)
                heldProjection = mp
                mp?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        heldProjection = null
                        isRunning = false
                        sendResult("系统已终止投屏，需重新授权")
                    }
                }, mainHandler)
            }
            mediaProjection = mp
            if (mp == null) {
                sendResult("投屏异常: 获取 MediaProjection 失败")
                finishLoop()
                return
            }

            mirrorStartMs = SystemClock.elapsedRealtime()
            sendResult("正在启动投屏...")

            if (!setupDisplay()) {
                sendResult("投屏异常: 创建虚拟屏失败")
                finishLoop()
                return
            }

            acquireKeepAwake()             // 退后台/灭屏防卡顿：CPU + Wi-Fi 双锁
            startRotationWatch()           // 横竖屏切换时自动重建虚拟屏

            startUploaders()
            startAudioCapture(mp)          // 内部录音：复用同一个 MediaProjection，无需二次授权
            sendResult("投屏中 · ${capW}x$capH（采集/发送已解耦）")

            mainHandler.postDelayed({
                if (isRunning && capturedCount == 0) {
                    sendResult("虚拟屏 ${capW}x$capH 已 3 秒没出帧，可能这个机型不支持该尺寸")
                }
            }, NO_FRAME_NOTICE_MS)
        } catch (e: Throwable) {
            Log.e(TAG, "启动投屏失败", e)
            sendResult("投屏异常: ${e.javaClass.simpleName}: ${e.message}")
            finishLoop()
        }
    }

    /**
     * 建虚拟屏。尺寸从设置中读取（按真机比例算高，dpi 同比缩放），
     * AUTO_MIRROR 会负责把主屏内容缩放到这个 surface 上。
     */
    private fun setupDisplay(): Boolean {
        val mp = mediaProjection ?: return false

        val dm = resources.displayMetrics
        var realW = dm.widthPixels
        var realH = dm.heightPixels
        val realDpi = dm.densityDpi

        // 横竖屏：以物理旋转为准（服务里的 displayMetrics 可能滞后），保证采集方向跟随手机
        val rot = currentRotation()
        val landscape = (rot == android.view.Surface.ROTATION_90 ||
                         rot == android.view.Surface.ROTATION_270)
        if (landscape != (realW > realH)) {
            val t = realW; realW = realH; realH = t
        }
        lastAppliedRotation = rot

        // 从设置中读取目标分辨率宽度
        val targetWidth = PhoneHubMirrorService.getCurrentResolutionWidth(this)

        if (realW <= targetWidth) {
            capW = realW
            capH = realH
        } else {
            capW = targetWidth
            capH = (realH.toLong() * targetWidth / realW).toInt().coerceAtLeast(1)
        }
        appliedTargetWidth = targetWidth
        // dpi 同比缩小，保持逻辑尺寸不变，避免内容被当成另一块屏幕重新布局
        val capDpi = if (realW > targetWidth) {
            (realDpi.toLong() * targetWidth / realW).toInt().coerceAtLeast(1)
        } else {
            realDpi
        }

        return try {
            val reader = ImageReader.newInstance(capW, capH, android.graphics.PixelFormat.RGBA_8888, 3)
            imageReader = reader

            captureThread = HandlerThread("mirror-capture").apply { start() }
            captureHandler = Handler(captureThread!!.looper)

            // 采集回调里只做"转换+压缩+入队"，绝不碰网络
            reader.setOnImageAvailableListener({ r ->
                if (!isRunning) return@setOnImageAvailableListener
                if (mirrorMode == MODE_AUDIO_ONLY) return@setOnImageAvailableListener  // 仅音频：不采集画面
                val capturedAt = SystemClock.elapsedRealtime() - mirrorStartMs
                val img: Image? = try {
                    r.acquireLatestImage()
                } catch (e: Exception) {
                    null
                }
                if (img == null) return@setOnImageAvailableListener
                try {
                    encodeAndEnqueue(img, capturedAt)
                } catch (e: Throwable) {
                    Log.e(TAG, "处理帧失败", e)
                } finally {
                    img.close()
                }
            }, captureHandler)

            virtualDisplay = mp.createVirtualDisplay(
                "mirror-display",
                capW, capH, capDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null, null
            )
            if (virtualDisplay == null) {
                teardownDisplay()
                return false
            }
            Log.i(TAG, "虚拟屏 ${capW}x$capH @${capDpi}dpi（真机 ${realW}x$realH @${realDpi}dpi）")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "创建虚拟屏失败", e)
            teardownDisplay()
            false
        }
    }

    // ───────────────────────── 采集 ─────────────────────────

    /** 转换 → 压缩 → 入队。全部在采集线程完成，不碰网络。 */
    private fun encodeAndEnqueue(img: Image, capturedAt: Long) {
        val t0 = SystemClock.elapsedRealtime()

        val src = imageToSendBitmap(img)
        val t1 = SystemClock.elapsedRealtime()

        var data = compressJpeg(src, currentJpegQuality)
        if (data.size > currentMaxFrameSize) {
            val smaller = Bitmap.createScaledBitmap(
                src,
                (src.width * 7 / 10).coerceAtLeast(1),
                (src.height * 7 / 10).coerceAtLeast(1),
                false
            )
            data = compressJpeg(smaller, 25)
            smaller.recycle()
        }
        // 无 padding 的常见路径下 imageToSendBitmap 返回的就是复用的 padded 位图，
        // **不能 recycle**（下一帧还要往里拷）；只有它是新分配出来的才需要回收
        if (src !== paddedBitmap) src.recycle()
        val t2 = SystemClock.elapsedRealtime()

        msConvert = t1 - t0
        msCompress = t2 - t1
        lastFrameBytes = data.size
        capturedCount++

        // 队列满了丢最旧的：宁可掉帧也要保证画面是"当下"的（每台 PC 独立队列）
        val seq = seqGen.incrementAndGet()
        for (s in senders) {
            if (!s.alive) continue
            if (s.frameQ.size >= SEND_QUEUE_CAP) {
                s.frameQ.pollFirst()
                droppedCount++
            }
            s.frameQ.offerLast(FramePacket(seq, capturedAt, data))
        }

        reportStats(capturedAt)
    }

    /**
     * Image → 发送尺寸的 Bitmap（新建，调用方负责 recycle）。
     *
     * 虚拟屏已经建在目标尺寸，所以这里**不需要缩放**，只要处理 rowStride padding：
     * 按 paddedWidth 建一张复用的位图直接拷贝，再裁掉右侧多出来的几列。
     * （1080 宽时 rowStride 常按 64/128 字节对齐，不处理就会出斜条纹。）
     */
    private fun imageToSendBitmap(image: Image): Bitmap {
        val width = image.width
        val height = image.height
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val paddedWidth = if (pixelStride > 0) plane.rowStride / pixelStride else width

        buffer.rewind()

        // 无 padding（400 宽时 400*4=1600 正好是 64 的倍数，通常就是这种情况）：
        // 直接拷进**复用的** padded 位图并返回它。调用方通过 `!== paddedBitmap`
        // 判断不复用，所以这里零新分配、零多余拷贝。
        if (paddedWidth == width && buffer.remaining() >= width * height * 4) {
            val padded = obtainPaddedBitmap(width, height)
            padded.copyPixelsFromBuffer(buffer)
            return padded
        }

        // rowStride 比 width 还小（异常情况）：老老实实新建一张
        if (paddedWidth < width || buffer.remaining() < paddedWidth * height * 4) {
            val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            buffer.rewind()
            b.copyPixelsFromBuffer(buffer)
            return b
        }

        // 有 padding：先拷进复用的 padded 位图，再裁掉右侧多余列（这次必须新分配）
        val padded = obtainPaddedBitmap(paddedWidth, height)
        padded.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(padded, 0, 0, width, height)
    }

    private fun obtainPaddedBitmap(w: Int, h: Int): Bitmap {
        val cur = paddedBitmap
        if (cur != null && !cur.isRecycled && cur.width == w && cur.height == h) return cur
        cur?.recycle()
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        paddedBitmap = b
        return b
    }

    private fun releasePaddedBitmap() {
        try { paddedBitmap?.recycle() } catch (_: Throwable) {}
        paddedBitmap = null
    }

    // ───────────────────────── 发送 ─────────────────────────

    private fun startUploaders() {
        uploadersRunning = true
        for (s in senders) {
            for (i in 0 until UPLOAD_THREADS) {
                val t = Thread({ uploadLoop(s) }, "mirror-upload-${s.ip}-$i")
                t.isDaemon = true
                t.start()
                uploaders.add(t)
            }
        }
    }

    private fun uploadLoop(s: PcSender) {
        while (uploadersRunning || s.frameQ.isNotEmpty()) {
            val p = try {
                s.frameQ.pollFirst(200, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                break
            } ?: continue

            val t0 = SystemClock.elapsedRealtime()
            val err = uploadFrame(s, p.data, p.tsMs, p.seq)
            val dt = SystemClock.elapsedRealtime() - t0
            if (err == null) {
                msUpload = dt
                okCount++
                s.ok++
                s.consecFail = 0
            } else {
                failCount++
                s.fail++
                s.consecFail++
                s.lastErr = err
                if (s.consecFail == 1 || s.consecFail % 30 == 0) {
                    sendResult("[$s] 上传失败(${s.consecFail} 次): $err")
                }
                if (s.consecFail >= MAX_CONSEC_FAIL) {
                    s.alive = false
                    sendResult("[$s] 连续失败 ${s.consecFail} 次，已停止向它发送")
                    if (senders.all { !it.alive }) {
                        sendResult("所有电脑都失联，停止投屏。最后错误: $err")
                        mainHandler.post { stopMirror() }
                    }
                    return   // 该 PC 的发送线程退出，其余 PC 不受影响
                }
            }
        }
    }

    /**
     * POST 一帧到指定 PC。返回 null 表示成功。
     *
     * 不调用 disconnect()：把响应体读完再关流，让 socket 回到 keep-alive
     * 连接池被下一帧复用，省掉每帧一次 TCP 三次握手。
     */
    private fun uploadFrame(s: PcSender, data: ByteArray, tsMs: Long, seq: Long): String? {
        return try {
            val conn = URL("http://${s.ip}:${s.port}/upload").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("Content-Type", "image/jpeg")
            conn.setRequestProperty("Connection", "keep-alive")
            conn.setRequestProperty("X-Capture-Ts", tsMs.toString())
            conn.setRequestProperty("X-Seq", seq.toString())
            conn.setRequestProperty("X-Ctrl", if (ctrlReady()) "1" else "0")
            conn.setFixedLengthStreamingMode(data.size)
            conn.outputStream.use { it.write(data) }

            val code = conn.responseCode
            var resp: ByteArray? = null
            try {
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                resp = stream?.use { it.readBytes() }
            } catch (_: Exception) {
            }
            if (code in 200..299) {
                execRemoteCmds(resp)   // 反向控制命令搭响应的顺风车
                null
            } else "HTTP $code"
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message}"
        }
    }

    // ───────────────────────── 统计上报 ─────────────────────────

    private fun reportStats(now: Long) {
        windowFrames++
        val span = now - fpsWindowStart
        if (span < 1000) return

        val captureFps = windowFrames * 1000.0 / span
        windowFrames = 0
        fpsWindowStart = now

        val dt = now - lastOkSnapshotTs
        val deliverFps = if (dt > 0) (okCount - lastOkSnapshot) * 1000.0 / dt else 0.0
        lastOkSnapshot = okCount
        lastOkSnapshotTs = now

        // 状态回传界面（替代参考工程的 LocalBroadcastManager 广播）
        _fpsFlow.value = String.format(
            "采集 %.1f | 送达 %.1f | ok %d / 失败 %d / 丢 %d | 队列 %d | %dx%d | 转换 %d 压缩 %d 上传 %d ms | %.0fKB",
            captureFps, deliverFps, okCount, failCount, droppedCount,
            senders.sumOf { it.frameQ.size }, capW, capH,
            msConvert, msCompress, msUpload, lastFrameBytes / 1024.0
        )
    }

    // ───────────────────────── 停止 / 释放 ─────────────────────────

    /** 画质档位的分辨率变化时重建虚拟屏（JPEG 质量/帧上限已即时生效，无需重建） */
    fun requestRebuildDisplay() {
        mainHandler.post {
            if (!isRunning) return@post
            sendResult("切换分辨率：重建虚拟屏...")
            teardownDisplay()
            if (setupDisplay()) {
                sendResult("投屏中 · ${capW}x$capH（已切换分辨率）")
            } else {
                sendResult("虚拟屏重建失败，停止投屏")
                finishLoop()
            }
        }
    }

    /** 当前物理旋转（服务里拿不到 Activity 的 configChanges，直接读 Display） */
    @Suppress("DEPRECATION")
    private fun currentRotation(): Int = try {
        (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
            .defaultDisplay.rotation
    } catch (_: Throwable) { android.view.Surface.ROTATION_0 }

    /** 轮询物理旋转：手机横竖屏切换时重建虚拟屏（电脑端画面/窗口随之转成横屏或竖屏） */
    private val rotationWatch = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val r = currentRotation()
            if (r != lastAppliedRotation) {
                lastAppliedRotation = r
                sendResult("屏幕方向变化，正在重建虚拟屏…")
                requestRebuildDisplay()
            }
            mainHandler.postDelayed(this, 1000)
        }
    }

    private fun startRotationWatch() {
        mainHandler.removeCallbacks(rotationWatch)
        mainHandler.postDelayed(rotationWatch, 1500)
    }

    private fun stopRotationWatch() {
        mainHandler.removeCallbacks(rotationWatch)
    }

    private fun teardownDisplay() {
        try { imageReader?.setOnImageAvailableListener(null, null) } catch (_: Throwable) {}
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        virtualDisplay = null
        try { imageReader?.close() } catch (_: Throwable) {}
        imageReader = null
        captureHandler = null
        try { captureThread?.quitSafely() } catch (_: Throwable) {}
        captureThread = null
    }

    private fun releaseCapture() {
        stopAudioCapture()
        teardownDisplay()
        mediaProjection = null
        releasePaddedBitmap()
        releaseKeepAwake()
    }

    /** 投屏期间持有 CPU 唤醒锁 + 低延迟 Wi-Fi 锁，防退后台/灭屏后周期性卡顿 */
    private fun acquireKeepAwake() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhoneHub:MirrorCpu")
                    .apply { setReferenceCounted(false); acquire(4 * 60 * 60 * 1000L) } // 4h 兜底
            }
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (wifiLock == null) {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY      // API29+：灭屏低延迟
                else
                    @Suppress("DEPRECATION") WifiManager.WIFI_MODE_FULL_HIGH_PERF
                wifiLock = wm.createWifiLock(mode, "PhoneHub:MirrorWifi")
                    .apply { setReferenceCounted(false); acquire() }
            }
            Log.i(TAG, "防卡顿双锁已持有 (CPU + Wi-Fi LowLatency)")
        } catch (e: Throwable) {
            Log.w(TAG, "持有双锁失败: ${e.message}")
        }
    }

    private fun releaseKeepAwake() {
        try { wakeLock?.release() } catch (_: Throwable) {}
        wakeLock = null
        try { wifiLock?.release() } catch (_: Throwable) {}
        wifiLock = null
    }

    private fun stopUploaders() {
        uploadersRunning = false
        for (s in senders) {
            s.frameQ.clear()
            s.audioQ.clear()
        }
        for (t in uploaders) {
            try { t.interrupt() } catch (_: Throwable) {}
        }
        uploaders.clear()
    }

    // ══════════════ 内部录音（AudioPlaybackCapture，Android 10+）══════════════

    /**
     * 启动内部录音。
     *
     * 原理（逆向华为系统录屏器 confirmed）：内部录音**不是录"扬声器发出的声音"** —— 那是声波，
     * 只能靠麦克风收。正确做法是 Android 10 的 `AudioPlaybackCapture`：从 AudioFlinger 混音链路
     * 里，把各 App 的音频流在送到扬声器之前"分一路"出来。
     *
     * 关键点：**复用投屏授权拿到的那个 MediaProjection，不需要二次授权。**
     * 参数照抄系统录屏器：48kHz / 立体声 / 16bit。
     */
    private fun startAudioCapture(mp: MediaProjection) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "Android ${Build.VERSION.SDK_INT} < 29，不支持 AudioPlaybackCapture")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "缺少 RECORD_AUDIO 权限，跳过内部录音")
            sendResult("未授予录音权限，本次只录画面")
            return
        }
        try {
            // 要捕获哪些类型的音频流（usage 白名单）。USAGE_MEDIA 覆盖音乐/视频，
            // GAME 覆盖游戏音效，UNKNOWN 兜住不声明 usage 的 App。
            val cfg = AudioPlaybackCaptureConfiguration.Builder(mp)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .addMatchingUsage(AudioAttributes.USAGE_ALARM)
                .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION)
                .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .build()

            // 采样率回退：部分设备的高采样率采集不可用（getMinBufferSize 报错或
            // AudioRecord 初始化失败），自动回退 48000Hz，保证录音功能不失效
            var rate = currentAudioSampleRate
            var chunkBytesActual = 0          // 回退后与原率不同的块字节数
            var minBuf = try {
                AudioRecord.getMinBufferSize(rate, AUDIO_CHANNEL, AUDIO_ENCODING)
            } catch (e: Exception) {
                -1
            }
            if (minBuf <= 0 && rate != 48000) {
                Log.w(TAG, "设备不支持 ${rate}Hz 采集，内部录音回退 48000Hz")
                sendResult("设备不支持 ${rate}Hz，录音回退 48kHz")
                rate = 48000
                currentAudioSampleRate = 48000   // 同步给 postAudioStart，PC 端按实际率处理
                minBuf = AudioRecord.getMinBufferSize(rate, AUDIO_CHANNEL, AUDIO_ENCODING)
            }

            val format = AudioFormat.Builder()
                .setSampleRate(rate)
                .setChannelMask(AUDIO_CHANNEL)
                .setEncoding(AUDIO_ENCODING)
                .build()

            val frames = rate * currentAudioChunkMs / 1000
            val bytesPerFrame = 4                       // 16bit × 2 声道
            val chunkBytes = frames * bytesPerFrame
            // 缓冲给足 8 块：上传线程偶尔被 Wi-Fi 卡一下时，采样在缓冲里排队
            // 等待而不是被覆盖丢失 —— 丢样本会让录音时长变短、音画渐进不同步。
            // 缓冲大小不增加延迟：只要上传跟得上，队列始终接近空。
            val bufSize = maxOf(minBuf, chunkBytes * 8)

            var rec = try {
                AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufSize)
                    .setAudioPlaybackCaptureConfig(cfg)
                    .build()
            } catch (e: Throwable) {
                Log.w(TAG, "${rate}Hz AudioRecord 建立失败: ${e.message}")
                null
            }
            if (rec != null && rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                rec = null
            }
            if (rec == null && rate != 48000) {
                // 高采样率初始化失败，最后回退 48000Hz 再试一次
                Log.w(TAG, "${rate}Hz 初始化失败，内部录音回退 48000Hz")
                sendResult("设备不支持 ${rate}Hz，录音回退 48kHz")
                rate = 48000
                currentAudioSampleRate = 48000
                val fbFrames = rate * currentAudioChunkMs / 1000
                val fbChunk = fbFrames * 4
                val fbBuf = maxOf(
                    AudioRecord.getMinBufferSize(rate, AUDIO_CHANNEL, AUDIO_ENCODING),
                    fbChunk * 8
                )
                rec = try {
                    AudioRecord.Builder()
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setSampleRate(rate)
                                .setChannelMask(AUDIO_CHANNEL)
                                .setEncoding(AUDIO_ENCODING)
                                .build()
                        )
                        .setBufferSizeInBytes(fbBuf)
                        .setAudioPlaybackCaptureConfig(cfg)
                        .build()
                } catch (e: Throwable) {
                    Log.w(TAG, "48000Hz 回退也失败: ${e.message}")
                    null
                }
                if (rec != null) chunkBytesActual = fbChunk
            }
            if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                rec?.release()
                Log.w(TAG, "AudioRecord 初始化失败（设备不支持或被占用）")
                sendResult("内部录音不可用（本次只录画面）")
                return
            }
            val effectiveChunk = if (chunkBytesActual > 0) chunkBytesActual else chunkBytes

            audioRecord = rec
            audioBytes = 0L
            audioOkCount = 0
            audioFailCount = 0

            // 整个"通知 PC + 开始采集 + 循环推流"都放子线程：
            // postAudioStart 是网络请求，放主线程会抛 NetworkOnMainThreadException
            // （它的 message 是 null，日志里只看到"失败: null"，极难排查）。
            audioThread = Thread({
                try {
                    if (!postAudioStart()) Log.w(TAG, "通知 PC 开始录音失败，仍继续采集")
                    rec.startRecording()
                    audioRunning = true
                    Log.i(TAG, "内部录音已启动 ${currentAudioSampleRate}Hz 立体声 16bit (块 $effectiveChunk 字节)")
                    for (s in senders) {
                        Thread({ audioSenderLoop(s) }, "audio-send-${s.ip}").start()
                    }
                    audioLoop(rec, effectiveChunk)
                } catch (e: Throwable) {
                    Log.e(TAG, "录音线程异常: ${e.javaClass.name}: ${e.message}", e)
                }
            }, "audio-capture").also { it.start() }
        } catch (e: Throwable) {
            Log.e(TAG, "启动内部录音失败: ${e.javaClass.name}: ${e.message}", e)
            sendResult("内部录音启动失败: ${e.javaClass.simpleName}")
        }
    }

    private fun audioLoop(rec: AudioRecord, chunkBytes: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buf = ByteArray(chunkBytes)
        var seq = 0L
        while (audioRunning && !Thread.currentThread().isInterrupted) {
            val n = try {
                rec.read(buf, 0, chunkBytes, AudioRecord.READ_BLOCKING)
            } catch (e: Throwable) {
                Log.w(TAG, "录音读取异常: ${e.message}")
                -1
            }
            if (n <= 0) {
                if (!audioRunning) break
                continue
            }
            // 仅画面模式：继续读取排空缓冲，但不发送、序号不前进——
            // PC 端会按时间间隔补静音，保证录制文件里音画时间轴对齐
            if (mirrorMode == MODE_VIDEO_ONLY) continue
            // 分发到每台 PC 的音频队列（各自线程上传，单台卡顿不拖累其他）
            val chunk = AudioChunk(seq++, buf.copyOf(n))
            var queued = false
            for (s in senders) {
                if (!s.alive) continue
                if (s.audioQ.size >= AUDIO_QUEUE_CAP) s.audioQ.pollFirst()
                if (s.audioQ.offerLast(chunk)) queued = true
            }
            if (queued) {
                audioOkCount++
                audioBytes += n
                if (audioOkCount == 1 || audioOkCount % 25 == 0) {
                    Log.i(TAG, "录音已推 $audioOkCount 块 / ${audioBytes / 1024} KB")
                }
            }
        }
        Log.i(TAG, "录音线程结束 ok=$audioOkCount fail=$audioFailCount bytes=$audioBytes")
    }

    /** 每台 PC 一个音频发送线程：从自己的队列取块上传，失败只影响自己 */
    private fun audioSenderLoop(s: PcSender) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        while (audioRunning || s.audioQ.isNotEmpty()) {
            val c = try {
                s.audioQ.pollFirst(200, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                break
            } ?: continue
            if (!s.alive) break
            if (postAudioChunk(s, c.data, c.data.size, c.seq)) {
                s.consecFail = 0
            } else {
                s.consecFail++
                s.fail++
                s.lastErr = lastAudioErr
                Log.w(TAG, "[$s] 音频上传失败(${s.consecFail}): $lastAudioErr")
                if (s.consecFail >= MAX_CONSEC_FAIL) {
                    s.alive = false
                    sendResult("[$s] 音频连续失败，已停止向它发送")
                    if (senders.all { !it.alive }) {
                        mainHandler.post { stopMirror() }
                    }
                    break
                }
            }
        }
    }

    private fun stopAudioCapture() {
        audioRunning = false
        try { audioRecord?.stop() } catch (_: Throwable) {}
        try { audioThread?.interrupt() } catch (_: Throwable) {}
        audioThread = null
        try { audioRecord?.release() } catch (_: Throwable) {}
        audioRecord = null
    }

    // ───────────────────────── 反向控制（PC 鼠标 → 手机点击）─────────────────────

    private fun ctrlReady(): Boolean {
        return PhoneHubAccessibilityService.instance != null &&
            android.provider.Settings.canDrawOverlays(this)
    }

    /**
     * 解析 PC 响应里捎带的命令并执行。
     * PC 把待执行命令排在 /upload、/audio 的响应里带回（延迟 ≤ 一帧间隔）。
     */
    private fun execRemoteCmds(body: ByteArray?) {
        if (body == null || body.size < 8) return
        try {
            val s = String(body, Charsets.UTF_8)
            if (!s.contains("\"cmds\"")) return
            val arr = org.json.JSONObject(s).optJSONArray("cmds") ?: return
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val offx = o.optInt("offx", 0)
                val offy = o.optInt("offy", 0)
                when (o.optString("type")) {
                    "tap" -> handleRemoteTap(
                        o.optDouble("fx", -1.0).toFloat(),
                        o.optDouble("fy", -1.0).toFloat(),
                        offx, offy
                    )
                    "swipe" -> handleRemoteSwipe(
                        o.optDouble("fx1", -1.0).toFloat(),
                        o.optDouble("fy1", -1.0).toFloat(),
                        o.optDouble("fx2", -1.0).toFloat(),
                        o.optDouble("fy2", -1.0).toFloat(),
                        o.optInt("ms", 300), offx, offy
                    )
                }
            }
        } catch (_: Throwable) {
        }
    }

    /** 真实物理屏尺寸。不能用 resources.displayMetrics —— 建了 400 宽虚拟屏后，
     * 部分 ROM 的 displayMetrics 会被配置变更污染成虚拟屏尺寸，坐标被缩到角落。 */
    private fun realScreenSize(): Pair<Int, Int> {
        val dm = android.util.DisplayMetrics()
        (applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.getRealMetrics(dm)
        return Pair(dm.widthPixels, dm.heightPixels)
    }

    /** 归一化坐标 (0..1，相对镜像画面) → 真实屏幕坐标：悬浮标记 + 无障碍注入点击 */
    private fun handleRemoteTap(fx: Float, fy: Float, offx: Int = 0, offy: Int = 0) {
        if (fx.isNaN() || fy.isNaN()) return
        val dm = realScreenSize()
        val x = (fx.coerceIn(0f, 1f) * dm.first).toInt() + offx
        val y = (fy.coerceIn(0f, 1f) * dm.second).toInt() + offy
        showTapMarker(x.toFloat(), y.toFloat())
        // 主工程的无障碍服务由 PhoneHubAccessibilityService 提供（原参考工程是自带的
        // MirrorAccessibilityService，已废掉，全 app 只保留一个无障碍服务）
        val acc = PhoneHubAccessibilityService.instance
        if (acc == null) {
            Log.w(TAG, "点击注入失败：无障碍服务未开启（设置→辅助功能→PhoneHub）")
            sendResult("点击注入失败：请开启 PhoneHub 无障碍服务")
        } else {
            acc.performTap(x.toFloat(), y.toFloat())
        }
    }

    /** 归一化起止坐标 → 真实屏幕坐标：标记起止点 + 无障碍注入直线滑动 */
    private fun handleRemoteSwipe(fx1: Float, fy1: Float, fx2: Float, fy2: Float,
                                  ms: Int, offx: Int = 0, offy: Int = 0) {
        if (fx1.isNaN() || fy1.isNaN() || fx2.isNaN() || fy2.isNaN()) return
        val dm = realScreenSize()
        val x1 = (fx1.coerceIn(0f, 1f) * dm.first).toInt() + offx
        val y1 = (fy1.coerceIn(0f, 1f) * dm.second).toInt() + offy
        val x2 = (fx2.coerceIn(0f, 1f) * dm.first).toInt() + offx
        val y2 = (fy2.coerceIn(0f, 1f) * dm.second).toInt() + offy
        showTapMarker(x1.toFloat(), y1.toFloat())
        mainHandler.postDelayed({ showTapMarker(x2.toFloat(), y2.toFloat()) }, ms.toLong().coerceAtMost(3000))
        val acc = PhoneHubAccessibilityService.instance
        if (acc == null) {
            Log.w(TAG, "滑动注入失败：无障碍服务未开启（设置→辅助功能→PhoneHub）")
            sendResult("滑动注入失败：请开启 PhoneHub 无障碍服务")
        } else {
            acc.performSwipe(
                x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(),
                ms.toLong().coerceIn(150, 3000))
        }
    }

    /** 悬浮窗标记点击位置：绿色圆圈闪现 ~450ms（需悬浮窗权限） */
    private fun showTapMarker(x: Float, y: Float) {
        mainHandler.post {
            try {
                val wm = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val size = 56
                val v = android.view.View(this).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(0x3300E676)
                        setStroke(4, 0xFF00E676.toInt())
                    }
                }
                val lp = android.view.WindowManager.LayoutParams(
                    size, size,
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    android.graphics.PixelFormat.TRANSLUCENT
                )
                lp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
                lp.x = (x - size / 2).toInt().coerceAtLeast(0)
                lp.y = (y - size / 2).toInt().coerceAtLeast(0)
                wm.addView(v, lp)
                mainHandler.postDelayed({
                    try { wm.removeView(v) } catch (_: Throwable) {}
                }, 450)
            } catch (e: Throwable) {
                Log.w(TAG, "悬浮标记失败: ${e.message}")
            }
        }
    }

    private fun postAudioStart(): Boolean {
        var anyOk = false
        for (s in senders) {
            val ok = try {
                val conn = URL("http://${s.ip}:${s.port}/audio_start?rate=$currentAudioSampleRate&ch=2&bits=16&mode=$mirrorMode")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.setFixedLengthStreamingMode(0)
                val r = conn.responseCode in 200..299
                conn.disconnect()
                r
            } catch (e: Exception) {
                lastAudioErr = "${e.javaClass.name}: ${e.message}"
                Log.w(TAG, "[${s.ip}] audio_start 失败: $lastAudioErr")
                false
            }
            anyOk = anyOk || ok
        }
        return anyOk
    }

    private fun postAudioChunk(s: PcSender, data: ByteArray, len: Int, seq: Long): Boolean {
        return try {
            val conn = URL("http://${s.ip}:${s.port}/audio").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = AUDIO_UPLOAD_TIMEOUT_MS
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setRequestProperty("Connection", "keep-alive")
            conn.setRequestProperty("X-Audio-Seq", seq.toString())
            conn.setFixedLengthStreamingMode(len)
            conn.outputStream.use { it.write(data, 0, len) }
            val ok = conn.responseCode in 200..299
            // 不调 disconnect()：读完响应体让 socket 回到连接池被下一块复用。
            // 每块一次 TCP 握手在 Wi-Fi 上耗时抖动大，是试听卡顿/爆音的来源之一。
            var resp: ByteArray? = null
            try {
                val stream = if (ok) conn.inputStream else conn.errorStream
                resp = stream?.use { it.readBytes() }
            } catch (_: Exception) {
            }
            if (ok) execRemoteCmds(resp)   // 仅音频模式下反向控制也走这里
            ok
        } catch (e: Exception) {
            lastAudioErr = "${e.javaClass.name}: ${e.message}"
            false
        }
    }

    private fun finishLoop() {
        isRunning = false
        stopRotationWatch()
        releaseCapture()
        stopUploaders()
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }

    /**
     * 停止推帧，并通知 PC 收尾录制文件。
     * 没有 `if (!isRunning) return` 之类的提前返回：收尾逻辑本身幂等，
     * 服务可能因为系统撤销授权等原因已经不在推帧，但通知还挂着，必须照样能收工。
     */
    private fun stopMirror() {
        Log.i(TAG, "stopMirror: isRunning=$isRunning, ok=$okCount, fail=$failCount")
        isRunning = false
        stopRotationWatch()
        notifyPcStop()
        releaseCapture()
        stopUploaders()
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()

        val tail = if (failCount > 0) "，失败 $failCount 帧" else ""
        sendResult("投屏已停止，共发送 $okCount 帧$tail；下次开始无需再授权")
    }

    private fun notifyPcStop() {
        Thread {
            for (s in senders) {
                try {
                    val conn = URL("http://${s.ip}:${s.port}/stop").openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    conn.setFixedLengthStreamingMode(0)
                    conn.responseCode
                    conn.disconnect()
                    Log.i(TAG, "已通知 $s 停止录制")
                } catch (e: Exception) {
                    Log.w(TAG, "通知 $s 停止失败: ${e.message}")
                }
            }
        }.start()
    }

    private fun compressJpeg(bmp: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }

    private fun sendResult(msg: String) {
        _resultFlow.value = msg
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "PhoneHub 投屏", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhoneHub 投屏运行中")
            .setContentText(
                if (senders.isEmpty()) "等待开始..."
                else "正在向 ${senders.size} 台电脑发送画面帧: ${senders.joinToString("、")}"
            )
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        releaseCapture()
        stopUploaders()
        if (okCount > 0) {
            notifyPcStop()
        }
        if (instance === this) instance = null
        super.onDestroy()
    }
}
