package com.phonehub

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
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
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
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
import android.view.KeyEvent
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
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.core.motion.utils.TypedValues
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.internal.view.SupportMenu
import androidx.core.view.PointerIconCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwnerKt
import coil.disk.DiskLruCache
import com.google.common.util.concurrent.ListenableFuture
import com.phonehub.ConnectionManager
import com.phonehub.MainActivity
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
import java.util.ArrayList
import java.util.Arrays
import java.util.Collection
import java.util.Comparator
import java.util.Date
import java.util.HashMap
import java.util.Iterator
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.List
import java.util.Locale
import java.util.Map
import java.util.Set
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.Pair
import kotlin.Triple
import kotlin.TuplesKt
import kotlin.Unit
import kotlin.collections.ArraysKt
import kotlin.collections.CollectionsKt
import kotlin.collections.MapsKt
import kotlin.comparisons.ComparisonsKt
import kotlin.io.ByteStreamsKt
import kotlin.io.CloseableKt
import kotlin.io.FilesKt
import kotlin.jvm.functions.Function1
import kotlin.jvm.functions.Function2
import kotlin.ranges.RangesKt
import kotlin.text.Charsets
import kotlin.text.StringsKt
import kotlinx.coroutines.BuildersKt
import kotlinx.coroutines.CoroutineScopeKt
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.internal.AbstractJsonLexerKt
import okhttp3.internal.ws.RealWebSocket
import org.slf4j.Marker

class MainActivity : AppCompatActivity {
    private final List<ConnectionManager.NotificationItem> activeNotifItems
    var allFuncList: private final List<FuncInfo>? = null
    var allPermissionsLauncher: private final ActivityResultLauncher<Array<String>>? = null
    var audioCaptureRunning: private boolean? = null
    var audioCaptureThread: private Thread? = null
    var audioRecord: private AudioRecord? = null
    var cameraExecutor: private ExecutorService? = null
    var cameraFrameTimeoutRunnable: private Runnable? = null
    var cameraImageView: private ImageView? = null
    var cameraInstance: private Camera? = null
    var cameraPermissionLauncher: private final ActivityResultLauncher<String>? = null
    var cameraPreviewRunning: private volatile boolean? = null
    var cameraPreviewView: private PreviewView? = null
    var cameraProvider: private ProcessCameraProvider? = null
    var clipViewMode: private String? = null
    var connectBtn: private Button? = null
    var connectStatus: private TextView? = null
    var currentTab: private int? = null
    var fileHistory: private final List<FileHistoryItem>? = null
    var fmMode: private String? = null
    var imageReader: private ImageReader? = null
    var ipInput: private EditText? = null
    var isMirrorFullscreen: private boolean? = null
    var locationPermissionLauncher: private final ActivityResultLauncher<Array<String>>? = null
    var mainContainer: private LinearLayout? = null
    var mediaProjection: private MediaProjection? = null
    var mirrorFrameTimeoutRunnable: private Runnable? = null
    var mirrorFullscreenComponents: private List<? extends View>? = null
    var mirrorImageView: private ImageView? = null
    private ViewGroup.LayoutParams mirrorOriginalLp
    private LinearLayout.LayoutParams mirrorOriginalPageContainerLp
    private final List<ConnectionManager.NotificationItem> notifHistoryItems
    var notifPermissionLauncher: private final ActivityResultLauncher<String>? = null
    var pageContainer: private FrameLayout? = null
    var pcCurPath: private String? = null
    var pcInDrives: private boolean? = null
    var pendingSaveText: private String? = null
    var portInput: private EditText? = null
    var saveTextLauncher: private final ActivityResultLauncher<String>? = null
    var screenCaptureLauncher: private final ActivityResultLauncher<Intent>? = null
    var screenCaptureRunning: private boolean? = null
    var screenCaptureThread: private Thread? = null
    var screenDensity: private int? = null
    var screenHeight: private int? = null
    var screenWidth: private int? = null
    var setupScreen: private LinearLayout? = null
    var statusText: private TextView? = null
    var titleText: private TextView? = null
    var tokenInput: private EditText? = null
    var virtualDisplay: private VirtualDisplay? = null
    val pageCache: private final HashMap<Integer, View> = new HashMap<>()
    val frameTimeoutHandler: private final Handler = new Handler(Looper.getMainLooper())
    val cameraLensFacing: private int = 1
    val SELECT_FILE_CODE: private final int = 1001
    val SELECT_APK_CODE: private final int = PointerIconCompat.TYPE_HAND
    val urlHistory: private final List<UrlHistoryItem> = new ArrayList()
    val handledTextContents: private final Map<String, Long> = new LinkedHashMap()

    public  class WhenMappings {
        public static final  int[] $EnumSwitchMapping$0
        public static final  int[] $EnumSwitchMapping$1

        static {
            val iArr: Array<Int> = new int[ConnectionManager.ConnectionState.values().length]
            try {
                iArr[ConnectionManager.ConnectionState.CONNECTED.ordinal()] = 1
                } catch (NoSuchFieldError e) {
                }
            try {
                iArr[ConnectionManager.ConnectionState.CONNECTING.ordinal()] = 2
                } catch (NoSuchFieldError e2) {
                }
            $EnumSwitchMapping$0 = iArr
            val iArr2: Array<Int> = new int[ConnectionManager.ChannelType.values().length]
            try {
                iArr2[ConnectionManager.ChannelType.WIFI.ordinal()] = 1
                } catch (NoSuchFieldError e3) {
                }
            try {
                iArr2[ConnectionManager.ChannelType.ADB.ordinal()] = 2
                } catch (NoSuchFieldError e4) {
                }
            $EnumSwitchMapping$1 = iArr2
            }
        }

    constructor() {
        val registerForActivityResult: ActivityResultLauncher<String> = registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda42
        override
        fun onActivityResult(obj: Any): Unit {
            MainActivity.notifPermissionLauncher$lambda$2((Boolean) obj)
            }
        })
    Intrinsics.checkNotNullExpressionValue(registerForActivityResult, "registerForActivityResult(...)")
    this.notifPermissionLauncher = registerForActivityResult
    val registerForActivityResult2: ActivityResultLauncher<String> = registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda43
    override
    fun onActivityResult(obj: Any): Unit {
        MainActivity.cameraPermissionLauncher$lambda$3(MainActivity.this, (Boolean) obj)
        }
    })
Intrinsics.checkNotNullExpressionValue(registerForActivityResult2, "registerForActivityResult(...)")
this.cameraPermissionLauncher = registerForActivityResult2
val registerForActivityResult3: ActivityResultLauncher<Array<String>> = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda44
override
fun onActivityResult(obj: Any): Unit {
    MainActivity.locationPermissionLauncher$lambda$4((Map) obj)
    }
})
Intrinsics.checkNotNullExpressionValue(registerForActivityResult3, "registerForActivityResult(...)")
this.locationPermissionLauncher = registerForActivityResult3
val registerForActivityResult4: ActivityResultLauncher<Array<String>> = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda45
override
fun onActivityResult(obj: Any): Unit {
    MainActivity.allPermissionsLauncher$lambda$5((Map) obj)
    }
})
Intrinsics.checkNotNullExpressionValue(registerForActivityResult4, "registerForActivityResult(...)")
this.allPermissionsLauncher = registerForActivityResult4
val registerForActivityResult5: ActivityResultLauncher<Intent> = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda46
override
fun onActivityResult(obj: Any): Unit {
    MainActivity.screenCaptureLauncher$lambda$6(MainActivity.this, (ActivityResult) obj)
    }
})
Intrinsics.checkNotNullExpressionValue(registerForActivityResult5, "registerForActivityResult(...)")
this.screenCaptureLauncher = registerForActivityResult5
this.pendingSaveText = ""
val registerForActivityResult6: ActivityResultLauncher<String> = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"), new ActivityResultCallback() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda47
override
fun onActivityResult(obj: Any): Unit {
    MainActivity.saveTextLauncher$lambda$7(MainActivity.this, (Uri) obj)
    }
})
Intrinsics.checkNotNullExpressionValue(registerForActivityResult6, "registerForActivityResult(...)")
this.saveTextLauncher = registerForActivityResult6
this.allFuncList = CollectionsKt.listOf((Object[]) new FuncInfo[]{FuncInfo("文件传输", "📁", 1), FuncInfo("剪贴板", "📋", 4), FuncInfo("文字互传", "💬", -1), FuncInfo("远程控制", "🎮", 2), FuncInfo("投屏", "🖥️", 8), FuncInfo("摄像头", "📷", 9), FuncInfo("通知", "🔔", 10), FuncInfo("路线图", "🗺️", 6), FuncInfo("文件管理", "📂", 11), FuncInfo("电源管理", "⚡", 14), FuncInfo("推送网页", "🌐", 15), FuncInfo("设置", "⚙️", 16)})
this.fileHistory = ArrayList()
this.clipViewMode = "history"
this.activeNotifItems = ArrayList()
this.notifHistoryItems = ArrayList()
this.fmMode = "phone"
this.pcCurPath = "C:\\"
this.pcInDrives = true
}

public static final  class UrlHistoryItem {
    var direction: private final String? = null
    var timestamp: private final long? = null
    var url: private final String? = null

    public static  UrlHistoryItem copy$default(UrlHistoryItem urlHistoryItem, String str, String str2, long j, int i, Object obj) {
        if ((i & 1) != 0) {
            str = urlHistoryItem.url
            }
        if ((i & 2) != 0) {
            str2 = urlHistoryItem.direction
            }
        if ((i & 4) != 0) {
            j = urlHistoryItem.timestamp
            }
        return urlHistoryItem.copy(str, str2, j)
        }

    fun getUrl(): String {
        return this.url
        }

    fun getDirection(): String {
        return this.direction
        }

    fun getTimestamp(): Long {
        return this.timestamp
        }

    fun copy(url: String, direction: String, timestamp: Long): UrlHistoryItem {
        Intrinsics.checkNotNullParameter(url, "url")
        Intrinsics.checkNotNullParameter(direction, "direction")
        return UrlHistoryItem(url, direction, timestamp)
        }

    fun equals(other: Any): Boolean {
        if (this == other) {
            var true: return? = null
            }
        if (!(other is UrlHistoryItem)) {
            var false: return? = null
            }
        val urlHistoryItem: UrlHistoryItem = (UrlHistoryItem) other
        return Intrinsics.areEqual(this.url, urlHistoryItem.url) && Intrinsics.areEqual(this.direction, urlHistoryItem.direction) && this.timestamp == urlHistoryItem.timestamp
        }

    fun hashCode(): Int {
        return (((this.url.hashCode() * 31) + this.direction.hashCode()) * 31) + Long.hashCode(this.timestamp)
        }

    fun toString(): String {
        return "UrlHistoryItem(url=" + this.url + ", direction=" + this.direction + ", timestamp=" + this.timestamp + ")"
        }

    fun UrlHistoryItem(url: String, direction: String, timestamp: Long): public {
        Intrinsics.checkNotNullParameter(url, "url")
        Intrinsics.checkNotNullParameter(direction, "direction")
        this.url = url
        this.direction = direction
        this.timestamp = timestamp
        }

    fun getDirection(): String {
        return this.direction
        }

    fun getTimestamp(): Long {
        return this.timestamp
        }

    fun getUrl(): String {
        return this.url
        }
    }

fun loadUrlHistory(): Unit {
    var longOrNull: Long? = null
    this.urlHistory.clear()
    val prefs: SharedPreferences = getSharedPreferences("phonehub_prefs", 0)
    val string: String = prefs.getString("push_url_history", "")
    val raw: String = string != null ? string : ""
    if (raw.length() == 0) {
        return
        }
    for (String line : StringsKt.split$default((CharSequence) raw, new String[]{"\n"}, false, 0, 6, (Object) null)) {
        if (!StringsKt.isBlank(line)) {
            val parts: List = StringsKt.split$default((CharSequence) line, new String[]{"\t"}, false, 3, 2, (Object) null)
            if (parts.size() == 3 && (longOrNull = StringsKt.toLongOrNull(parts.get(0))) != null) {
                val ts: Long = longOrNull.longValue()
                this.urlHistory.add(UrlHistoryItem(parts.get(2), parts.get(1), ts))
                }
            }
        }
    }

fun saveUrlHistory(): Unit {
    val prefs: SharedPreferences = getSharedPreferences("phonehub_prefs", 0)
    val raw: String = CollectionsKt.joinToString$default(this.urlHistory, "\n", null, null, 0, null, new Function1() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda62
    override
    fun invoke(obj: Any): Any {
        CharSequence saveUrlHistory$lambda$0
        saveUrlHistory$lambda$0 = MainActivity.saveUrlHistory$lambda$0((MainActivity.UrlHistoryItem) obj)
        return saveUrlHistory$lambda$0
        }
    }, 30, null)
prefs.edit().putString("push_url_history", raw).apply()
}

public static final CharSequence saveUrlHistory$lambda$0(UrlHistoryItem it) {
    Intrinsics.checkNotNullParameter(it, "it")
    return it.getTimestamp() + "\t" + it.getDirection() + "\t" + it.getUrl()
    }

fun addUrlHistory(url: String, direction: String): Unit {
    CollectionsKt.removeAll((List) this.urlHistory, Function1() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda57
        override
        fun invoke(obj: Any): Any {
            boolean addUrlHistory$lambda$1
            addUrlHistory$lambda$1 = MainActivity.addUrlHistory$lambda$1(url, direction, (MainActivity.UrlHistoryItem) obj)
            return Boolean.valueOf(addUrlHistory$lambda$1)
            }
        })
    this.urlHistory.add(0, UrlHistoryItem(url, direction, System.currentTimeMillis()))
    if (this.urlHistory.size() > 50) {
        this.urlHistory.subList(50, this.urlHistory.size()).clear()
        }
    saveUrlHistory()
    }

public static final boolean addUrlHistory$lambda$1(String $url, String $direction, UrlHistoryItem it) {
    Intrinsics.checkNotNullParameter(it, "it")
    return Intrinsics.areEqual(it.getUrl(), $url) && Intrinsics.areEqual(it.getDirection(), $direction)
    }

public static final Unit notifPermissionLauncher$lambda$2(Boolean it) {
    }

public static final Unit cameraPermissionLauncher$lambda$3(MainActivity this$0, Boolean granted) {
    if (granted.booleanValue()) {
        Toast.makeText(this$0, "摄像头权限已授予", 0).show()
        }
    }

public static final Unit locationPermissionLauncher$lambda$4(Map it) {
    }

public static final Unit allPermissionsLauncher$lambda$5(Map it) {
    }

public static final Unit screenCaptureLauncher$lambda$6(final MainActivity this$0, ActivityResult result) {
    try {
        if (result.getResultCode() == -1 && result.getData() != null) {
            val systemService: Any = this$0.getSystemService("media_projection")
            Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.media.projection.MediaProjectionManager")
            val mpManager: MediaProjectionManager = (MediaProjectionManager) systemService
            val connectionManager: ConnectionManager = ConnectionManager.INSTANCE
            val resultCode: Int = result.getResultCode()
            val data: Intent = result.getData()
            Intrinsics.checkNotNull(data)
            connectionManager.cacheMediaProjectionToken(resultCode, data)
            val resultCode2: Int = result.getResultCode()
            val data2: Intent = result.getData()
            Intrinsics.checkNotNull(data2)
            this$0.mediaProjection = mpManager.getMediaProjection(resultCode2, data2)
            val mediaProjection: MediaProjection = this$0.mediaProjection
            if (mediaProjection != null) {
                mediaProjection.registerCallback(new MediaProjection.Callback() { // from class: com.phonehub.MainActivity$screenCaptureLauncher$1$1
                    override
                    fun onStop(): Unit {
                        MainActivity.this.stopPhoneScreenCapture()
                        }
                    }, Handler(Looper.getMainLooper()))
                }
            this$0.startScreenCaptureLoop()
            return
            }
        Toast.makeText(this$0, "屏幕录制权限被拒绝", 0).show()
        } catch (Exception e) {
        Toast.makeText(this$0, "初始化投屏失败: " + e.getMessage(), 1).show()
        }
    }

public static final Unit saveTextLauncher$lambda$7(MainActivity this$0, Uri uri) {
    if (uri != null) {
        val textToSave: String = this$0.pendingSaveText
        BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getIO()), null, null, new MainActivity$saveTextLauncher$1$1(this$0, uri, textToSave, null), 3, null)
        }
    }

override
fun onCreate(savedInstanceState: Bundle): Unit {
    var str: String? = null
    var editText: EditText? = null
    var str2: String? = null
    var button: Button? = null
    super.onCreate(savedInstanceState)
    getWindow().addFlags(128)
    setContentView(R.layout.activity_main)
    this.setupScreen = (LinearLayout) findViewById(R.id.setupScreen)
    this.mainContainer = (LinearLayout) findViewById(R.id.mainContainer)
    this.ipInput = (EditText) findViewById(R.id.ipInput)
    this.portInput = (EditText) findViewById(R.id.portInput)
    this.tokenInput = (EditText) findViewById(R.id.tokenInput)
    this.connectBtn = (Button) findViewById(R.id.connectBtn)
    this.connectStatus = (TextView) findViewById(R.id.connectStatus)
    this.statusText = (TextView) findViewById(R.id.statusText)
    this.titleText = (TextView) findViewById(R.id.titleText)
    this.pageContainer = (FrameLayout) findViewById(R.id.pageContainer)
    val prefs: SharedPreferences = getSharedPreferences("phonehub_prefs", 0)
    val it: String = ConnectionManager.INSTANCE.getCachedIp()
    val button2: Button = null
    if (it != null) {
        val editText2: EditText = this.ipInput
        if (editText2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("ipInput")
            editText2 = null
            }
        editText2.setText(it)
        }
    val editText3: EditText = this.ipInput
    if (editText3 == null) {
        Intrinsics.throwUninitializedPropertyAccessException("ipInput")
        editText3 = null
        }
    val text: Editable = editText3.getText()
    Intrinsics.checkNotNullExpressionValue(text, "getText(...)")
    if (StringsKt.isBlank(text)) {
        val editText4: EditText = this.ipInput
        if (editText4 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("ipInput")
            editText4 = null
            }
        editText4.setText("192.168.3.9")
        }
    val cachedPort: Int = prefs.getInt("cached_port", 0)
    val editText5: EditText = this.portInput
    if (cachedPort > 0) {
        if (editText5 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("portInput")
            editText5 = null
            }
        str = String.valueOf(cachedPort)
        } else {
        if (editText5 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("portInput")
            editText5 = null
            }
        str = "58627"
        }
    editText5.setText(str)
    val cachedToken: String = prefs.getString("cached_token", "")
    val str3: String = cachedToken
    if (str3 == null || str3.length() == 0) {
        editText = this.tokenInput
        if (editText == null) {
            Intrinsics.throwUninitializedPropertyAccessException("tokenInput")
            editText = null
            }
        } else {
        editText = this.tokenInput
        if (editText == null) {
            Intrinsics.throwUninitializedPropertyAccessException("tokenInput")
            editText = null
            }
        str2 = cachedToken
        }
    editText.setText(str2)
    val toggleBtn: TextView = (TextView) findViewById(R.id.toggleTokenVisibility)
    toggleBtn.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda48
        override
        fun onClick(view: View): Unit {
            MainActivity.onCreate$lambda$9(MainActivity.this, toggleBtn, view)
            }
        })
    val button3: Button = this.connectBtn
    if (button3 == null) {
        Intrinsics.throwUninitializedPropertyAccessException("connectBtn")
        button = null
        } else {
        button = button3
        }
    NativeButtonKt.applyDarkTheme$default(button, 0, 0, true, 3, null)
    val button4: Button = this.connectBtn
    if (button4 == null) {
        Intrinsics.throwUninitializedPropertyAccessException("connectBtn")
        } else {
        button2 = button4
        }
    button2.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda49
        override
        fun onClick(view: View): Unit {
            MainActivity.this.attemptConnect()
            }
        })
    requestAllPermissions()
    setupTabs()
    val lastReceivedText: Pair<String, String> = ConnectionManager.INSTANCE.getLastReceivedText()
    if (lastReceivedText != null) {
        val fn: String = lastReceivedText.component1()
        val txt: String = lastReceivedText.component2()
        this.handledTextContents.put(fn + "|" + txt, Long.valueOf(System.currentTimeMillis()))
        }
    setupFlows()
    updateSetupVisibility()
    handleTextNotificationIntent(getIntent())
    handleFileTransferNotificationIntent(getIntent())
    getOnBackPressedDispatcher().addCallback(this, OnBackPressedCallback() { // from class: com.phonehub.MainActivity$onCreate$5
        {
            super(true)
            }

        override
        fun handleOnBackPressed(): Unit {
            var z: Boolean? = null
            var i: Int? = null
            z = MainActivity.this.isMirrorFullscreen
            if (z) {
                MainActivity.this.exitMirrorFullscreen()
                return
                }
            i = MainActivity.this.currentTab
            if (i != 0) {
                MainActivity.this.switchTab(0, false)
                } else {
                setEnabled(false)
                MainActivity.this.getOnBackPressedDispatcher().onBackPressed()
                }
            }
        })
    }

public static final Unit onCreate$lambda$9(MainActivity this$0, TextView $toggleBtn, View it) {
    val editText: EditText = this$0.tokenInput
    val editText2: EditText = null
    if (editText == null) {
        Intrinsics.throwUninitializedPropertyAccessException("tokenInput")
        editText = null
        }
    if (editText.getInputType() == 129) {
        val editText3: EditText = this$0.tokenInput
        if (editText3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("tokenInput")
            editText3 = null
            }
        editText3.setInputType(145)
        $toggleBtn.setText("隐藏")
        } else {
        val editText4: EditText = this$0.tokenInput
        if (editText4 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("tokenInput")
            editText4 = null
            }
        editText4.setInputType(129)
        $toggleBtn.setText("显示")
        }
    val editText5: EditText = this$0.tokenInput
    if (editText5 == null) {
        Intrinsics.throwUninitializedPropertyAccessException("tokenInput")
        editText5 = null
        }
    val editText6: EditText = this$0.tokenInput
    if (editText6 == null) {
        Intrinsics.throwUninitializedPropertyAccessException("tokenInput")
        } else {
        editText2 = editText6
        }
    editText5.setSelection(editText2.getText().length())
    }

fun enterMirrorFullscreen(mirrorFrame: FrameLayout, allUiComponents: List<? extends View>): Unit {
    var button: Button? = null
    if (this.isMirrorFullscreen) {
        return
        }
    setRequestedOrientation(0)
    val win: Window = getWindow()
    WindowInsetsControllerCompat $this$enterMirrorFullscreen_u24lambda_u2412 = WindowInsetsControllerCompat(win, win.getDecorView())
    $this$enterMirrorFullscreen_u24lambda_u2412.hide(WindowInsetsCompat.Type.systemBars())
    $this$enterMirrorFullscreen_u24lambda_u2412.setSystemBarsBehavior(2)
    val supportActionBar: ActionBar = getSupportActionBar()
    if (supportActionBar != null) {
        supportActionBar.hide()
        }
    val mainContainer: LinearLayout = (LinearLayout) findViewById(R.id.mainContainer)
    val childAt: View = mainContainer != null ? mainContainer.getChildAt(0) : null
    val titleBar: LinearLayout = childAt instanceof LinearLayout ? (LinearLayout) childAt : null
    val childAt2: View = mainContainer != null ? mainContainer.getChildAt(1) : null
    val tabScroll: HorizontalScrollView = childAt2 instanceof HorizontalScrollView ? (HorizontalScrollView) childAt2 : null
    if (titleBar != null) {
        titleBar.setVisibility(8)
        }
    if (tabScroll != null) {
        tabScroll.setVisibility(8)
        }
    List<? extends View> $this$forEach$iv = allUiComponents
    for (Object element$iv : $this$forEach$iv) {
        val it: View = (View) element$iv
        if (it != null) {
            it.setVisibility(8)
            }
        }
    ViewGroup.LayoutParams layoutParams = ((FrameLayout) findViewById(R.id.pageContainer)).getLayoutParams()
    this.mirrorOriginalPageContainerLp = layoutParams is LinearLayout.LayoutParams ? (LinearLayout.LayoutParams) layoutParams : null
    LinearLayout.LayoutParams it2 = this.mirrorOriginalPageContainerLp
    if (it2 != null) {
        LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(it2.width, 0)
        newLp.weight = 1.0f
        ((FrameLayout) findViewById(R.id.pageContainer)).setLayoutParams(newLp)
        }
    this.mirrorOriginalLp = mirrorFrame != null ? mirrorFrame.getLayoutParams() : null
    ViewGroup.LayoutParams layoutParams2 = mirrorFrame != null ? mirrorFrame.getLayoutParams() : null
    LinearLayout.LayoutParams lp = layoutParams2 is LinearLayout.LayoutParams ? (LinearLayout.LayoutParams) layoutParams2 : null
    if (lp != null) {
        LinearLayout.LayoutParams it3 = lp
        it3.weight = 1.0f
        it3.height = 0
        mirrorFrame.setLayoutParams(it3)
        }
    this.isMirrorFullscreen = true
    this.mirrorFullscreenComponents = allUiComponents
    val view: View = this.pageCache.get(8)
    if (view != null && (button = (Button) view.findViewById(R.id.btnMirrorFullscreenExit)) != null) {
        button.setVisibility(0)
        }
    }

fun exitMirrorFullscreen(): Unit {
    var view: View? = null
    var view2: View? = null
    var button: Button? = null
    var textView: TextView? = null
    var view3: View? = null
    var frame: FrameLayout? = null
    if (this.isMirrorFullscreen) {
        this.isMirrorFullscreen = false
        setRequestedOrientation(1)
        val win: Window = getWindow()
        WindowInsetsControllerCompat $this$exitMirrorFullscreen_u24lambda_u2416 = WindowInsetsControllerCompat(win, win.getDecorView())
        $this$exitMirrorFullscreen_u24lambda_u2416.show(WindowInsetsCompat.Type.systemBars())
        val supportActionBar: ActionBar = getSupportActionBar()
        if (supportActionBar != null) {
            supportActionBar.show()
            }
        val mainContainer: LinearLayout = (LinearLayout) findViewById(R.id.mainContainer)
        if (mainContainer != null) {
            view = mainContainer.getChildAt(0)
            } else {
            view = null
            }
        val titleBar: LinearLayout = view instanceof LinearLayout ? (LinearLayout) view : null
        if (mainContainer != null) {
            view2 = mainContainer.getChildAt(1)
            } else {
            view2 = null
            }
        val tabScroll: HorizontalScrollView = view2 instanceof HorizontalScrollView ? (HorizontalScrollView) view2 : null
        if (titleBar != null) {
            titleBar.setVisibility(0)
            }
        if (tabScroll != null) {
            tabScroll.setVisibility(0)
            }
        val iterable: Iterable = this.mirrorFullscreenComponents
        if (iterable != null) {
            Iterable $this$forEach$iv = iterable
            for (Object element$iv : $this$forEach$iv) {
                val it: View = (View) element$iv
                if (it != null) {
                    it.setVisibility(0)
                    }
                }
            }
        LinearLayout.LayoutParams it2 = this.mirrorOriginalPageContainerLp
        if (it2 != null) {
            ((FrameLayout) findViewById(R.id.pageContainer)).setLayoutParams(it2)
            }
        ViewGroup.LayoutParams lp = this.mirrorOriginalLp
        if (lp != null && (view3 = this.pageCache.get(8)) != null && (frame = (FrameLayout) view3.findViewById(R.id.mirrorFrame)) != null) {
            frame.setLayoutParams(lp)
            frame.setVisibility(8)
            }
        this.mirrorOriginalLp = null
        this.mirrorOriginalPageContainerLp = null
        ConnectionManager.INSTANCE.sendMediaCommand("pc_stream_stop")
        ConnectionManager.INSTANCE.stopPcFramePolling()
        val imageView: ImageView = this.mirrorImageView
        if (imageView != null) {
            imageView.setImageBitmap(null)
            }
        val view4: View = this.pageCache.get(8)
        if (view4 != null && (textView = (TextView) view4.findViewById(R.id.mirrorStatus)) != null) {
            textView.setText("未启动")
            }
        val view5: View = this.pageCache.get(8)
        if (view5 == null || (button = (Button) view5.findViewById(R.id.btnMirrorFullscreenExit)) == null) {
            return
            }
        button.setVisibility(8)
        }
    }

fun requestAllPermissions(): Unit {
    val prefs: SharedPreferences = getSharedPreferences("phonehub_prefs", 0)
    if (!prefs.getBoolean("permissions_requested", false) || Build.VERSION.SDK_INT >= 33) {
        val runtimePermissions: List = new ArrayList()
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
            List $this$toTypedArray$iv = runtimePermissions
            ActivityCompat.requestPermissions(this, (String[]) $this$toTypedArray$iv.toArray(new String[0]), 1001)
            } catch (Exception e) {
            }
        val handler: Handler = new Handler(Looper.getMainLooper())
        handler.postDelayed(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda99
            override
            fun run(): Unit {
                MainActivity.requestAllPermissions$lambda$21(MainActivity.this)
                }
            }, 500L)
        handler.postDelayed(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda100
            override
            fun run(): Unit {
                MainActivity.requestAllPermissions$lambda$22(MainActivity.this)
                }
            }, 750L)
        handler.postDelayed(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda101
            override
            fun run(): Unit {
                MainActivity.requestAllPermissions$lambda$23(MainActivity.this)
                }
            }, 1500L)
        handler.postDelayed(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda102
            override
            fun run(): Unit {
                MainActivity.requestAllPermissions$lambda$24(MainActivity.this, prefs)
                }
            }, 2000L)
        }
    }

public static final Unit requestAllPermissions$lambda$21(MainActivity this$0) {
    try {
        if (!Settings.canDrawOverlays(this$0)) {
            val intent: Intent = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + this$0.getPackageName()))
            intent.addFlags(268435456)
            this$0.startActivity(intent)
            }
        } catch (Exception e) {
        }
    }

public static final Unit requestAllPermissions$lambda$22(MainActivity this$0) {
    try {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            val intent: Intent = new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION")
            intent.setData(Uri.parse("package:" + this$0.getPackageName()))
            intent.addFlags(268435456)
            this$0.startActivity(intent)
            }
        } catch (Exception e) {
        }
    }

public static final Unit requestAllPermissions$lambda$23(MainActivity this$0) {
    try {
        val systemService: Any = this$0.getSystemService("power")
        val pm: PowerManager = systemService instanceof PowerManager ? (PowerManager) systemService : null
        if (pm != null && !pm.isIgnoringBatteryOptimizations(this$0.getPackageName())) {
            val intent: Intent = new Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
            intent.setData(Uri.parse("package:" + this$0.getPackageName()))
            intent.addFlags(268435456)
            this$0.startActivity(intent)
            }
        } catch (Exception e) {
        }
    }

public static final Unit requestAllPermissions$lambda$24(MainActivity this$0, SharedPreferences $prefs) {
    try {
        if (!this$0.getPackageManager().canRequestPackageInstalls()) {
            val intent: Intent = new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES")
            intent.setData(Uri.parse("package:" + this$0.getPackageName()))
            intent.addFlags(268435456)
            this$0.startActivity(intent)
            }
        } catch (Exception e) {
        }
    $prefs.edit().putBoolean("permissions_requested", true).apply()
    }

fun setupTabs(): Unit {
    }

static  Unit switchTab$default(MainActivity mainActivity, int i, boolean z, int i2, Object obj) {
    if ((i2 & 2) != 0) {
        z = true
        }
    mainActivity.switchTab(i, z)
    }

fun switchTab(index: Int, forward: Boolean): Unit {
    val frameLayout: FrameLayout = null
    if (this.currentTab == index) {
        val frameLayout2: FrameLayout = this.pageContainer
        if (frameLayout2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("pageContainer")
            frameLayout2 = null
            }
        if (frameLayout2.getChildCount() > 0) {
            return
            }
        }
    val frameLayout3: FrameLayout = this.pageContainer
    if (frameLayout3 == null) {
        Intrinsics.throwUninitializedPropertyAccessException("pageContainer")
        frameLayout3 = null
        }
    val oldView: View = frameLayout3.getChildAt(0)
    val newView: View = getPageView(index)
    if (this.currentTab != index) {
        cleanupPageResources(this.currentTab)
        }
    this.currentTab = index
    if (index == 8 && PhoneHubAccessibilityService.INSTANCE.getInstance() == null) {
        Handler(getMainLooper()).postDelayed(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda75
            override
            fun run(): Unit {
                MainActivity.switchTab$lambda$25(MainActivity.this)
                }
            }, 500L)
        }
    if (forward) {
        if (oldView != null && oldView != newView) {
            newView.setTranslationX(newView.getWidth())
            val frameLayout4: FrameLayout = this.pageContainer
            if (frameLayout4 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("pageContainer")
                } else {
                frameLayout = frameLayout4
                }
            frameLayout.addView(newView)
            newView.animate().translationX(0.0f).setDuration(280L).withEndAction(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda76
                override
                fun run(): Unit {
                    MainActivity.switchTab$lambda$26(oldView, newView, this)
                    }
                }).start()
            return
            }
        if (oldView != null) {
            val frameLayout5: FrameLayout = this.pageContainer
            if (frameLayout5 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("pageContainer")
                frameLayout5 = null
                }
            frameLayout5.removeView(oldView)
            val frameLayout6: FrameLayout = this.pageContainer
            if (frameLayout6 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("pageContainer")
                } else {
                frameLayout = frameLayout6
                }
            frameLayout.addView(newView)
            return
            }
        val frameLayout7: FrameLayout = this.pageContainer
        if (frameLayout7 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("pageContainer")
            } else {
            frameLayout = frameLayout7
            }
        frameLayout.addView(newView)
        return
        }
    if (oldView != null && oldView != newView) {
        val frameLayout8: FrameLayout = this.pageContainer
        if (frameLayout8 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("pageContainer")
            frameLayout8 = null
            }
        frameLayout8.removeAllViews()
        val frameLayout9: FrameLayout = this.pageContainer
        if (frameLayout9 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("pageContainer")
            frameLayout9 = null
            }
        frameLayout9.addView(newView)
        val frameLayout10: FrameLayout = this.pageContainer
        if (frameLayout10 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("pageContainer")
            } else {
            frameLayout = frameLayout10
            }
        frameLayout.addView(oldView)
        oldView.animate().translationX(oldView.getWidth()).setDuration(280L).withEndAction(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda77
            override
            fun run(): Unit {
                MainActivity.switchTab$lambda$27(MainActivity.this, oldView)
                }
            }).start()
        return
        }
    if (oldView != null) {
        val frameLayout11: FrameLayout = this.pageContainer
        if (frameLayout11 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("pageContainer")
            frameLayout11 = null
            }
        frameLayout11.removeView(oldView)
        val frameLayout12: FrameLayout = this.pageContainer
        if (frameLayout12 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("pageContainer")
            } else {
            frameLayout = frameLayout12
            }
        frameLayout.addView(newView)
        return
        }
    val frameLayout13: FrameLayout = this.pageContainer
    if (frameLayout13 == null) {
        Intrinsics.throwUninitializedPropertyAccessException("pageContainer")
        } else {
        frameLayout = frameLayout13
        }
    frameLayout.addView(newView)
    }

public static final Unit switchTab$lambda$25(MainActivity this$0) {
    Toast.makeText(this$0, "无障碍服务未开启，无法操控手机。请在设置→无障碍中开启 PhoneHub", 1).show()
    try {
        this$0.startActivity(Intent("android.settings.ACCESSIBILITY_SETTINGS"))
        } catch (Exception e) {
        }
    }

public static final Unit switchTab$lambda$26(View $oldView, View $newView, MainActivity this$0) {
    if ($oldView.getParent() != null && $oldView != $newView) {
        val frameLayout: FrameLayout = this$0.pageContainer
        if (frameLayout == null) {
            Intrinsics.throwUninitializedPropertyAccessException("pageContainer")
            frameLayout = null
            }
        frameLayout.removeView($oldView)
        $oldView.setTranslationX(0.0f)
        }
    }

public static final Unit switchTab$lambda$27(MainActivity this$0, View $oldView) {
    val frameLayout: FrameLayout = this$0.pageContainer
    if (frameLayout == null) {
        Intrinsics.throwUninitializedPropertyAccessException("pageContainer")
        frameLayout = null
        }
    frameLayout.removeView($oldView)
    $oldView.setTranslationX(0.0f)
    }

fun cleanupPageResources(oldIndex: Int): Unit {
    var frameLayout: FrameLayout? = null
    switch (oldIndex) {
        case 8:
        ConnectionManager.INSTANCE.sendMediaCommand("mirror_stop")
        ConnectionManager.INSTANCE.sendMediaCommand("pc_stream_stop")
        ConnectionManager.INSTANCE.stopPcFramePolling()
        ConnectionManager.INSTANCE.stopPcAudioPolling()
        stopPhoneScreenCapture()
        if (this.isMirrorFullscreen) {
            exitMirrorFullscreen()
            }
        val view: View = this.pageCache.get(8)
        if (view == null || (frameLayout = (FrameLayout) view.findViewById(R.id.mirrorFrame)) == null) {
            return
            }
        frameLayout.setVisibility(8)
        return
        case 9:
        ConnectionManager.INSTANCE.sendAction("camera_stop", MapsKt.emptyMap())
        return
        default:
        return
        }
    }

fun getPageView(index: Int): View {
    var view: LinearLayout? = null
    val it: View = this.pageCache.get(Integer.valueOf(index))
    if (it != null) {
        var it: return? = null
        }
    switch (index) {
        case 0:
        view = getHomeView()
        break
        case 1:
        view = getFilesView()
        break
        case 2:
        view = getRemoteView()
        break
        case 3:
        view = getFullKeyboardView()
        break
        case 4:
        view = getClipboardView()
        break
        case 5:
        view = getClipboardView()
        break
        case 6:
        view = getLocationView()
        break
        case 7:
        view = getScreenshotView()
        break
        case 8:
        view = getMirrorView()
        break
        case 9:
        view = getCameraView()
        break
        case 10:
        view = getNotificationsView()
        break
        case 11:
        view = getFileManagerView()
        break
        case 12:
        val v: LinearLayout = new LinearLayout(this)
        v.setOrientation(1)
        v.setBackgroundColor(-14803426)
        v.setPadding(dp(32), dp(32), dp(32), dp(32))
        val tv: TextView = new TextView(this)
        tv.setText("APK 安装已改为自动安装\n\n电脑端发送 APK 后，手机将自动安装")
        tv.setTextColor(-5197648)
        tv.setTextSize(14.0f)
        v.addView(tv)
        view = v
        break
        case 13:
        view = getAppManagerView()
        break
        case 14:
        view = getPowerView()
        break
        case 15:
        view = getPushWebView()
        break
        case 16:
        view = getSettingsView()
        break
        default:
        view = getHomeView()
        break
        }
    this.pageCache.put(Integer.valueOf(index), view)
    var view: return? = null
    }

public static final  class FuncInfo {
    var icon: private final String? = null
    var name: private final String? = null
    var tabIndex: private final int? = null

    public static  FuncInfo copy$default(FuncInfo funcInfo, String str, String str2, int i, int i2, Object obj) {
        if ((i2 & 1) != 0) {
            str = funcInfo.name
            }
        if ((i2 & 2) != 0) {
            str2 = funcInfo.icon
            }
        if ((i2 & 4) != 0) {
            i = funcInfo.tabIndex
            }
        return funcInfo.copy(str, str2, i)
        }

    fun getName(): String {
        return this.name
        }

    fun getIcon(): String {
        return this.icon
        }

    fun getTabIndex(): Int {
        return this.tabIndex
        }

    fun copy(name: String, icon: String, tabIndex: Int): FuncInfo {
        Intrinsics.checkNotNullParameter(name, "name")
        Intrinsics.checkNotNullParameter(icon, "icon")
        return FuncInfo(name, icon, tabIndex)
        }

    fun equals(other: Any): Boolean {
        if (this == other) {
            var true: return? = null
            }
        if (!(other is FuncInfo)) {
            var false: return? = null
            }
        val funcInfo: FuncInfo = (FuncInfo) other
        return Intrinsics.areEqual(this.name, funcInfo.name) && Intrinsics.areEqual(this.icon, funcInfo.icon) && this.tabIndex == funcInfo.tabIndex
        }

    fun hashCode(): Int {
        return (((this.name.hashCode() * 31) + this.icon.hashCode()) * 31) + Integer.hashCode(this.tabIndex)
        }

    fun toString(): String {
        return "FuncInfo(name=" + this.name + ", icon=" + this.icon + ", tabIndex=" + this.tabIndex + ")"
        }

    fun FuncInfo(name: String, icon: String, tabIndex: Int): public {
        Intrinsics.checkNotNullParameter(name, "name")
        Intrinsics.checkNotNullParameter(icon, "icon")
        this.name = name
        this.icon = icon
        this.tabIndex = tabIndex
        }

    fun getIcon(): String {
        return this.icon
        }

    fun getName(): String {
        return this.name
        }

    fun getTabIndex(): Int {
        return this.tabIndex
        }
    }

fun dp(v: Int): Int {
    return NativeButtonKt.dpToPx(v)
    }

fun getHomeView(): View {
    val root: LinearLayout = new LinearLayout(this)
    root.setOrientation(1)
    root.setBackgroundColor(-14803426)
    root.setPadding(dp(16), dp(16), dp(16), dp(16))
    val statusBox: LinearLayout = new LinearLayout(this)
    statusBox.setOrientation(1)
    statusBox.setBackgroundColor(-13816531)
    statusBox.setPadding(dp(16), dp(16), dp(16), dp(16))
    val connLabel: TextView = new TextView(this)
    connLabel.setText("连接状态")
    connLabel.setTextColor(-5197648)
    connLabel.setTextSize(12.0f)
    connLabel.setPadding(0, 0, 0, dp(8))
    val connStatusHome: TextView = new TextView(this)
    connStatusHome.setId(R.id.connStatusHome)
    connStatusHome.setText("未连接")
    connStatusHome.setTextColor(-3066824)
    connStatusHome.setTextSize(18.0f)
    connStatusHome.setTypeface(connStatusHome.getTypeface(), 1)
    val channelHome: TextView = new TextView(this)
    channelHome.setId(R.id.channelHome)
    channelHome.setText("通道: 无")
    channelHome.setTextColor(-5197648)
    channelHome.setTextSize(12.0f)
    channelHome.setPadding(0, dp(4), 0, 0)
    statusBox.addView(connLabel)
    statusBox.addView(connStatusHome)
    statusBox.addView(channelHome)
    initHomeStatus(connStatusHome, channelHome)
    LinearLayout.LayoutParams $this$getHomeView_u24lambda_u2434 = new LinearLayout.LayoutParams(-1, -2)
    $this$getHomeView_u24lambda_u2434.bottomMargin = dp(12)
    val unit: Unit = Unit.INSTANCE
    root.addView(statusBox, $this$getHomeView_u24lambda_u2434)
    val recentLabel: TextView = new TextView(this)
    recentLabel.setText("最近操作")
    recentLabel.setTextColor(-1)
    recentLabel.setTextSize(14.0f)
    recentLabel.setPadding(0, 0, 0, dp(8))
    root.addView(recentLabel)
    val recentFuncs: List = getRecentFunctions(6)
    if (recentFuncs.isEmpty()) {
        TextView $this$getHomeView_u24lambda_u2436 = TextView(this)
        $this$getHomeView_u24lambda_u2436.setText("暂无最近操作，点击下方功能开始使用")
        $this$getHomeView_u24lambda_u2436.setTextColor(-5197648)
        $this$getHomeView_u24lambda_u2436.setTextSize(12.0f)
        $this$getHomeView_u24lambda_u2436.setPadding(0, 0, 0, dp(12))
        root.addView($this$getHomeView_u24lambda_u2436)
        } else {
        val recentScroll: HorizontalScrollView = new HorizontalScrollView(this)
        val recentRow: LinearLayout = new LinearLayout(this)
        recentRow.setOrientation(0)
        recentRow.setPadding(0, 0, 0, dp(12))
        for (final FuncInfo func : recentFuncs) {
            val btn: View = buildFuncButton(func, true)
            btn.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda116
                override
                fun onClick(view: View): Unit {
                    MainActivity.this.onFuncClicked(func)
                    }
                })
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(84), -2)
            lp.setMargins(dp(4), 0, dp(4), 0)
            recentRow.addView(btn, lp)
            statusBox = statusBox
            }
        recentScroll.addView(recentRow)
        root.addView(recentScroll, new LinearLayout.LayoutParams(-1, -2))
        }
    val allLabel: TextView = new TextView(this)
    allLabel.setText("所有功能")
    allLabel.setTextColor(-1)
    allLabel.setTextSize(14.0f)
    allLabel.setPadding(0, 0, 0, dp(8))
    root.addView(allLabel)
    val row: LinearLayout = new LinearLayout(this)
    row.setOrientation(0)
    val count: Int = 0
    for (final FuncInfo func2 : this.allFuncList) {
        val btn2: View = buildFuncButton(func2, false)
        btn2.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda1
            override
            fun onClick(view: View): Unit {
                MainActivity.this.onFuncClicked(func2)
                }
            })
        val allLabel2: TextView = allLabel
        row.addView(btn2, new LinearLayout.LayoutParams(0, -2, 1.0f))
        count++
        if (count % 3 == 0) {
            LinearLayout.LayoutParams $this$getHomeView_u24lambda_u2442 = new LinearLayout.LayoutParams(-1, -2)
            val connLabel2: TextView = connLabel
            $this$getHomeView_u24lambda_u2442.bottomMargin = dp(8)
            val unit2: Unit = Unit.INSTANCE
            root.addView(row, $this$getHomeView_u24lambda_u2442)
            LinearLayout $this$getHomeView_u24lambda_u2443 = LinearLayout(this)
            $this$getHomeView_u24lambda_u2443.setOrientation(0)
            row = $this$getHomeView_u24lambda_u2443
            allLabel = allLabel2
            connLabel = connLabel2
            recentLabel = recentLabel
            } else {
            allLabel = allLabel2
            }
        }
    if (count % 3 != 0) {
        LinearLayout.LayoutParams $this$getHomeView_u24lambda_u2444 = new LinearLayout.LayoutParams(-1, -2)
        $this$getHomeView_u24lambda_u2444.bottomMargin = dp(8)
        val unit3: Unit = Unit.INSTANCE
        root.addView(row, $this$getHomeView_u24lambda_u2444)
        }
    ScrollView $this$getHomeView_u24lambda_u2445 = ScrollView(this)
    $this$getHomeView_u24lambda_u2445.addView(root)
    return $this$getHomeView_u24lambda_u2445
    }

fun initHomeStatus(connStatusHome: TextView, channelHome: TextView): Unit {
    var chName: String? = null
    ConnectionManager.ConnectionState state = ConnectionManager.INSTANCE.getConnectionState().getValue()
    switch (WhenMappings.$EnumSwitchMapping$0[state.ordinal()]) {
        case 1:
        connStatusHome.setText("已连接")
        connStatusHome.setTextColor(-15696880)
        break
        case 2:
        connStatusHome.setText("连接中...")
        connStatusHome.setTextColor(-18176)
        break
        default:
        connStatusHome.setText("未连接")
        connStatusHome.setTextColor(-3066824)
        break
        }
    ConnectionManager.ChannelType ch = ConnectionManager.INSTANCE.getCurrentChannel().getValue()
    switch (WhenMappings.$EnumSwitchMapping$1[ch.ordinal()]) {
        case 1:
        chName = "WiFi 直连"
        break
        case 2:
        chName = "USB 数据线"
        break
        default:
        chName = "无"
        break
        }
    channelHome.setText("通道: " + chName)
    }

fun buildFuncButton(func: FuncInfo, small: Boolean): View {
    val container: LinearLayout = new LinearLayout(this)
    container.setOrientation(1)
    container.setGravity(17)
    container.setBackgroundColor(-13816531)
    container.setPadding(dp(8), dp(12), dp(8), dp(12))
    container.setMinimumHeight(dp(80))
    val iconSize: Float = small ? 22.0f : 28.0f
    val textSize: Float = small ? 10.0f : 11.0f
    TextView $this$buildFuncButton_u24lambda_u2447 = TextView(this)
    $this$buildFuncButton_u24lambda_u2447.setText(func.getIcon())
    $this$buildFuncButton_u24lambda_u2447.setTextSize(iconSize)
    $this$buildFuncButton_u24lambda_u2447.setGravity(17)
    TextView $this$buildFuncButton_u24lambda_u2448 = TextView(this)
    $this$buildFuncButton_u24lambda_u2448.setText(func.getName())
    $this$buildFuncButton_u24lambda_u2448.setTextColor(-1)
    $this$buildFuncButton_u24lambda_u2448.setTextSize(textSize)
    $this$buildFuncButton_u24lambda_u2448.setGravity(17)
    $this$buildFuncButton_u24lambda_u2448.setPadding(0, dp(6), 0, 0)
    container.addView($this$buildFuncButton_u24lambda_u2447)
    container.addView($this$buildFuncButton_u24lambda_u2448)
    var container: return? = null
    }

fun onFuncClicked(func: FuncInfo): Unit {
    recordFunctionUse(func.getName())
    this.pageCache.remove(0)
    if (func.getTabIndex() == -1) {
        showSendTextDialog()
        } else {
        switchTab$default(this, func.getTabIndex(), false, 2, null)
        }
    }

fun recordFunctionUse(name: String): Unit {
    val prefs: SharedPreferences = getSharedPreferences("phonehub_func_usage", 0)
    val count: Int = prefs.getInt(name + "_count", 0) + 1
    prefs.edit().putInt(name + "_count", count).putLong(name + "_time", System.currentTimeMillis()).apply()
    }

fun getRecentFunctions(max: Int): List<FuncInfo> {
    val i: Int = 0
    val prefs: SharedPreferences = getSharedPreferences("phonehub_func_usage", 0)
    Iterable $this$map$iv = this.allFuncList
    Collection destination$iv$iv = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv, 10))
    Iterable $this$mapTo$iv$iv = $this$map$iv
    for (Object item$iv$iv : $this$mapTo$iv$iv) {
        val f: FuncInfo = (FuncInfo) item$iv$iv
        destination$iv$iv.add(Triple(f, Integer.valueOf(prefs.getInt(f.getName() + "_count", i)), Long.valueOf(prefs.getLong(f.getName() + "_time", 0L))))
        $this$mapTo$iv$iv = $this$mapTo$iv$iv
        i = 0
        }
    Iterable $this$filter$iv = (List) destination$iv$iv
    Collection destination$iv$iv2 = ArrayList()
    for (Object element$iv$iv : $this$filter$iv) {
        val it: Triple = (Triple) element$iv$iv
        if (((Number) it.getSecond()).intValue() > 0) {
            destination$iv$iv2.add(element$iv$iv)
            }
        }
    val comparator: Comparator = new Comparator() { // from class: com.phonehub.MainActivity$getRecentFunctions$$inlined$compareByDescending$1
    override
    fun compare(t: T, t2: T): Int {
        val it2: Triple = (Triple) t2
        val comparable: Comparable = (Comparable) it2.getSecond()
        val it3: Triple = (Triple) t
        return ComparisonsKt.compareValues(comparable, (Comparable) it3.getSecond())
        }
    }
Iterable $this$map$iv2 = CollectionsKt.take(CollectionsKt.sortedWith((List) destination$iv$iv2, Comparator() { // from class: com.phonehub.MainActivity$getRecentFunctions$$inlined$thenByDescending$1
    override
    fun compare(t: T, t2: T): Int {
        val previousCompare: Int = comparator.compare(t, t2)
        if (previousCompare != 0) {
            var previousCompare: return? = null
            }
        val it2: Triple = (Triple) t2
        val comparable: Comparable = (Comparable) it2.getThird()
        val it3: Triple = (Triple) t
        return ComparisonsKt.compareValues(comparable, (Comparable) it3.getThird())
        }
    }), max)
Collection destination$iv$iv3 = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv2, 10))
for (Object item$iv$iv2 : $this$map$iv2) {
    val it2: Triple = (Triple) item$iv$iv2
    destination$iv$iv3.add((FuncInfo) it2.getFirst())
    }
return (List) destination$iv$iv3
}

public static final  class FileHistoryItem {
    var direction: private final String? = null
    var text: private final String? = null
    var time: private final long? = null

    public static  FileHistoryItem copy$default(FileHistoryItem fileHistoryItem, long j, String str, String str2, int i, Object obj) {
        if ((i & 1) != 0) {
            j = fileHistoryItem.time
            }
        if ((i & 2) != 0) {
            str = fileHistoryItem.text
            }
        if ((i & 4) != 0) {
            str2 = fileHistoryItem.direction
            }
        return fileHistoryItem.copy(j, str, str2)
        }

    fun getTime(): Long {
        return this.time
        }

    fun getText(): String {
        return this.text
        }

    fun getDirection(): String {
        return this.direction
        }

    fun copy(time: Long, text: String, direction: String): FileHistoryItem {
        Intrinsics.checkNotNullParameter(text, "text")
        Intrinsics.checkNotNullParameter(direction, "direction")
        return FileHistoryItem(time, text, direction)
        }

    fun equals(other: Any): Boolean {
        if (this == other) {
            var true: return? = null
            }
        if (!(other is FileHistoryItem)) {
            var false: return? = null
            }
        val fileHistoryItem: FileHistoryItem = (FileHistoryItem) other
        return this.time == fileHistoryItem.time && Intrinsics.areEqual(this.text, fileHistoryItem.text) && Intrinsics.areEqual(this.direction, fileHistoryItem.direction)
        }

    fun hashCode(): Int {
        return (((Long.hashCode(this.time) * 31) + this.text.hashCode()) * 31) + this.direction.hashCode()
        }

    fun toString(): String {
        return "FileHistoryItem(time=" + this.time + ", text=" + this.text + ", direction=" + this.direction + ")"
        }

    fun FileHistoryItem(time: Long, text: String, direction: String): public {
        Intrinsics.checkNotNullParameter(text, "text")
        Intrinsics.checkNotNullParameter(direction, "direction")
        this.time = time
        this.text = text
        this.direction = direction
        }

    fun getDirection(): String {
        return this.direction
        }

    fun getText(): String {
        return this.text
        }

    fun getTime(): Long {
        return this.time
        }
    }

fun loadFileHistory(): Unit {
    var longOrNull: Long? = null
    this.fileHistory.clear()
    val prefs: SharedPreferences = getSharedPreferences("phonehub_prefs", 0)
    val string: String = prefs.getString("file_transfer_history", "")
    val raw: String = string != null ? string : ""
    if (raw.length() == 0) {
        return
        }
    for (String line : StringsKt.split$default((CharSequence) raw, new String[]{"\n"}, false, 0, 6, (Object) null)) {
        if (!StringsKt.isBlank(line)) {
            val parts: List = StringsKt.split$default((CharSequence) line, new String[]{"\t"}, false, 3, 2, (Object) null)
            if (parts.size() == 3 && (longOrNull = StringsKt.toLongOrNull(parts.get(0))) != null) {
                val ts: Long = longOrNull.longValue()
                this.fileHistory.add(FileHistoryItem(ts, parts.get(2), parts.get(1)))
                }
            }
        }
    }

fun saveFileHistory(): Unit {
    val prefs: SharedPreferences = getSharedPreferences("phonehub_prefs", 0)
    val raw: String = CollectionsKt.joinToString$default(this.fileHistory, "\n", null, null, 0, null, new Function1() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda82
    override
    fun invoke(obj: Any): Any {
        CharSequence saveFileHistory$lambda$54
        saveFileHistory$lambda$54 = MainActivity.saveFileHistory$lambda$54((MainActivity.FileHistoryItem) obj)
        return saveFileHistory$lambda$54
        }
    }, 30, null)
prefs.edit().putString("file_transfer_history", raw).apply()
}

public static final CharSequence saveFileHistory$lambda$54(FileHistoryItem it) {
    Intrinsics.checkNotNullParameter(it, "it")
    return it.getTime() + "\t" + it.getDirection() + "\t" + it.getText()
    }

fun addFileHistory(text: String, direction: String): Unit {
    this.fileHistory.add(0, FileHistoryItem(System.currentTimeMillis(), text, direction))
    if (this.fileHistory.size() > 500) {
        this.fileHistory.subList(500, this.fileHistory.size()).clear()
        }
    saveFileHistory()
    refreshFileHistoryList()
    }

fun refreshFileHistoryList(): Unit {
    var list: ListView? = null
    val v: View = this.pageCache.get(1)
    if (v == null || (list = (ListView) v.findViewById(R.id.fileHistoryList)) == null) {
        return
        }
    if (this.fileHistory.isEmpty()) {
        list.setAdapter((ListAdapter) null)
        return
        }
    Iterable $this$map$iv = this.fileHistory
    Collection destination$iv$iv = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv, 10))
    for (Object item$iv$iv : $this$map$iv) {
        val item: FileHistoryItem = (FileHistoryItem) item$iv$iv
        val time: String = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(item.getTime()))
        destination$iv$iv.add("[" + item.getDirection() + "] " + item.getText() + "\n" + time)
        v = v
        }
    val displays: List = (List) destination$iv$iv
    list.setAdapter((ListAdapter) ArrayAdapter(this, android.R.layout.simple_list_item_1, displays))
    list.setOnItemClickListener(new AdapterView.OnItemClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda11
        override
        fun onItemClick(adapterView: AdapterView, view: View, i: Int, j: Long): Unit {
            MainActivity.refreshFileHistoryList$lambda$57(MainActivity.this, adapterView, view, i, j)
            }
        })
    }

public static final Unit refreshFileHistoryList$lambda$57(MainActivity this$0, AdapterView adapterView, View view, int pos, long j) {
    val item: FileHistoryItem = this$0.fileHistory.get(pos)
    val dir: File = ConnectionManager.INSTANCE.getReceiveDir()
    val file: File = dir != null ? new File(dir, item.getText()) : null
    if (file == null || !file.exists()) {
        Toast.makeText(this$0, "文件不存在", 0).show()
        return
        }
    try {
        val uri: Uri = FileProvider.getUriForFile(this$0, this$0.getPackageName() + ".fileprovider", file)
        val ext: String = StringsKt.substringAfterLast(item.getText(), '.', "").toLowerCase(Locale.ROOT)
        Intrinsics.checkNotNullExpressionValue(ext, "toLowerCase(...)")
        val mime: String = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (mime == null) {
            mime = "*/*"
            }
        val intent: Intent = new Intent("android.intent.action.VIEW")
        intent.setDataAndType(uri, mime)
        intent.addFlags(1)
        intent.addFlags(268435456)
        this$0.startActivity(intent)
        } catch (Exception e) {
        Toast.makeText(this$0, "无法打开文件: " + e.getMessage(), 0).show()
        }
    }

fun getFilesView(): View {
    val v: View = LayoutInflater.from(this).inflate(R.layout.page_files, (ViewGroup) null)
    val button: Button = (Button) v.findViewById(R.id.selectFileBtn)
    if (button != null) {
        NativeButtonKt.applyDarkTheme$default(button, 0, 0, true, 3, null)
        }
    val button2: Button = (Button) v.findViewById(R.id.pauseFileBtn)
    if (button2 != null) {
        NativeButtonKt.applyDarkTheme$default(button2, 0, 0, false, 7, null)
        }
    val button3: Button = (Button) v.findViewById(R.id.cancelFileBtn)
    if (button3 != null) {
        NativeButtonKt.applyDarkTheme$default(button3, 0, 0, false, 7, null)
        }
    val button4: Button = (Button) v.findViewById(R.id.doneFileBtn)
    if (button4 != null) {
        NativeButtonKt.applyDarkTheme$default(button4, 0, 0, false, 7, null)
        }
    if (this.fileHistory.isEmpty()) {
        loadFileHistory()
        }
    v.post(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda93
        override
        fun run(): Unit {
            MainActivity.this.refreshFileHistoryList()
            }
        })
    val button5: Button = (Button) v.findViewById(R.id.selectFileBtn)
    if (button5 != null) {
        button5.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda94
            override
            fun onClick(view: View): Unit {
                MainActivity.getFilesView$lambda$59(MainActivity.this, view)
                }
            })
        }
    val button6: Button = (Button) v.findViewById(R.id.pauseFileBtn)
    if (button6 != null) {
        button6.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda96
            override
            fun onClick(view: View): Unit {
                MainActivity.getFilesView$lambda$60(v, view)
                }
            })
        }
    val button7: Button = (Button) v.findViewById(R.id.cancelFileBtn)
    if (button7 != null) {
        button7.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda97
            override
            fun onClick(view: View): Unit {
                MainActivity.getFilesView$lambda$61(MainActivity.this, v, view)
                }
            })
        }
    val button8: Button = (Button) v.findViewById(R.id.doneFileBtn)
    if (button8 != null) {
        button8.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda98
            override
            fun onClick(view: View): Unit {
                MainActivity.getFilesView$lambda$62(MainActivity.this, view)
                }
            })
        }
    ConnectionManager.TransferProgress currentProgress = ConnectionManager.INSTANCE.getFileTransferProgress().getValue()
    if (currentProgress != null) {
        val linearLayout: LinearLayout = (LinearLayout) v.findViewById(R.id.fileProgressContainer)
        if (linearLayout != null) {
            linearLayout.setVisibility(0)
            }
        val linearLayout2: LinearLayout = (LinearLayout) v.findViewById(R.id.fileTransferBtnContainer)
        if (linearLayout2 != null) {
            linearLayout2.setVisibility(0)
            }
        val textView: TextView = (TextView) v.findViewById(R.id.fileNameText)
        if (textView != null) {
            textView.setText(currentProgress.getFileName())
            }
        val pct: Int = currentProgress.getTotal() > 0 ? (int) ((currentProgress.getSent() * 100) / currentProgress.getTotal()) : 0
        val progressBar: ProgressBar = (ProgressBar) v.findViewById(R.id.fileProgress)
        if (progressBar != null) {
            progressBar.setProgress(pct)
            }
        val button9: Button = (Button) v.findViewById(R.id.cancelFileBtn)
        if (button9 != null) {
            button9.setEnabled(true)
            }
        val button10: Button = (Button) v.findViewById(R.id.pauseFileBtn)
        if (button10 != null) {
            button10.setEnabled(true)
            }
        val button11: Button = (Button) v.findViewById(R.id.selectFileBtn)
        if (button11 != null) {
            button11.setEnabled(false)
            }
        val button12: Button = (Button) v.findViewById(R.id.doneFileBtn)
        if (button12 != null) {
            button12.setVisibility(8)
            }
        val button13: Button = (Button) v.findViewById(R.id.doneFileBtn)
        if (button13 != null) {
            button13.setEnabled(false)
            }
        val dirText: String = currentProgress.getReceiving() ? "接收中" : "发送中"
        val textView2: TextView = (TextView) v.findViewById(R.id.fileNameText)
        if (textView2 != null) {
            textView2.setText(dirText + ": " + currentProgress.getFileName())
            }
        val textView3: TextView = (TextView) v.findViewById(R.id.fileProgressText)
        if (textView3 != null) {
            textView3.setText(getFilesView$fmtSize(currentProgress.getSent()) + " / " + getFilesView$fmtSize(currentProgress.getTotal()))
            }
        }
    BuildersKt.launch$default(LifecycleOwnerKt.getLifecycleScope(this), null, null, new MainActivity$getFilesView$6(this, null), 3, null)
    Intrinsics.checkNotNull(v)
    var v: return? = null
    }

public static final Unit getFilesView$lambda$59(MainActivity this$0, View it) {
    val intent: Intent = new Intent("android.intent.action.GET_CONTENT")
    intent.setType("*/*")
    this$0.startActivityForResult(Intent.createChooser(intent, "选择文件"), this$0.SELECT_FILE_CODE)
    }

public static final Unit getFilesView$lambda$60(View $v, View it) {
    val btn: Button = (Button) $v.findViewById(R.id.pauseFileBtn)
    if (btn == null) {
        return
        }
    if (Intrinsics.areEqual(btn.getText().toString(), "暂停")) {
        ConnectionManager.INSTANCE.pauseTransfer()
        btn.setText("继续")
        val textView: TextView = (TextView) $v.findViewById(R.id.fileSpeedText)
        if (textView != null) {
            textView.setText("已暂停")
            return
            }
        return
        }
    ConnectionManager.INSTANCE.resumeTransfer()
    btn.setText("暂停")
    val textView2: TextView = (TextView) $v.findViewById(R.id.fileSpeedText)
    if (textView2 != null) {
        textView2.setText("继续传输...")
        }
    }

public static final Unit getFilesView$lambda$61(MainActivity this$0, View $v, View it) {
    ConnectionManager.INSTANCE.cancelTransfer()
    Toast.makeText(this$0, "文件传输已取消", 0).show()
    val textView: TextView = (TextView) $v.findViewById(R.id.fileNameText)
    if (textView != null) {
        textView.setText("已取消")
        }
    val progressBar: ProgressBar = (ProgressBar) $v.findViewById(R.id.fileProgress)
    if (progressBar != null) {
        progressBar.setProgress(0)
        }
    val textView2: TextView = (TextView) $v.findViewById(R.id.fileProgressText)
    if (textView2 != null) {
        textView2.setText("")
        }
    val textView3: TextView = (TextView) $v.findViewById(R.id.fileSpeedText)
    if (textView3 != null) {
        textView3.setText("")
        }
    val button: Button = (Button) $v.findViewById(R.id.cancelFileBtn)
    if (button != null) {
        button.setEnabled(false)
        }
    val button2: Button = (Button) $v.findViewById(R.id.pauseFileBtn)
    if (button2 != null) {
        button2.setEnabled(false)
        }
    val button3: Button = (Button) $v.findViewById(R.id.pauseFileBtn)
    if (button3 != null) {
        button3.setText("暂停")
        }
    val button4: Button = (Button) $v.findViewById(R.id.selectFileBtn)
    if (button4 != null) {
        button4.setEnabled(true)
        }
    val button5: Button = (Button) $v.findViewById(R.id.doneFileBtn)
    if (button5 != null) {
        button5.setVisibility(8)
        }
    val linearLayout: LinearLayout = (LinearLayout) $v.findViewById(R.id.fileProgressContainer)
    if (linearLayout != null) {
        linearLayout.setVisibility(8)
        }
    val linearLayout2: LinearLayout = (LinearLayout) $v.findViewById(R.id.fileTransferBtnContainer)
    if (linearLayout2 != null) {
        linearLayout2.setVisibility(8)
        }
    }

public static final Unit getFilesView$lambda$62(MainActivity this$0, View it) {
    var name: String? = null
    var text: CharSequence? = null
    val v2: View = this$0.pageCache.get(1)
    if (v2 == null) {
        return
        }
    val textView: TextView = (TextView) v2.findViewById(R.id.fileNameText)
    if (textView == null || (text = textView.getText()) == null || (name = text.toString()) == null) {
        name = ""
        }
    if ((name.length() > 0) && !Intrinsics.areEqual(name, "等待传输...") && !Intrinsics.areEqual(name, "已取消")) {
        val direction: String = "发送"
        if (!StringsKt.startsWith$default(name, "发送", false, 2, (Object) null) && !StringsKt.startsWith$default(name, "已发送", false, 2, (Object) null)) {
            direction = "接收"
            }
        val record: String = StringsKt.removePrefix(StringsKt.removePrefix(name, (CharSequence) "发送中: "), (CharSequence) "接收中: ")
        this$0.addFileHistory(record, direction)
        }
    val textView2: TextView = (TextView) v2.findViewById(R.id.fileNameText)
    if (textView2 != null) {
        textView2.setText("等待传输...")
        }
    val progressBar: ProgressBar = (ProgressBar) v2.findViewById(R.id.fileProgress)
    if (progressBar != null) {
        progressBar.setProgress(0)
        }
    val textView3: TextView = (TextView) v2.findViewById(R.id.fileProgressText)
    if (textView3 != null) {
        textView3.setText("")
        }
    val textView4: TextView = (TextView) v2.findViewById(R.id.fileSpeedText)
    if (textView4 != null) {
        textView4.setText("")
        }
    val button: Button = (Button) v2.findViewById(R.id.cancelFileBtn)
    if (button != null) {
        button.setEnabled(false)
        }
    val button2: Button = (Button) v2.findViewById(R.id.pauseFileBtn)
    if (button2 != null) {
        button2.setEnabled(false)
        }
    val button3: Button = (Button) v2.findViewById(R.id.pauseFileBtn)
    if (button3 != null) {
        button3.setText("暂停")
        }
    val button4: Button = (Button) v2.findViewById(R.id.selectFileBtn)
    if (button4 != null) {
        button4.setEnabled(true)
        }
    val button5: Button = (Button) v2.findViewById(R.id.doneFileBtn)
    if (button5 != null) {
        button5.setEnabled(false)
        }
    val button6: Button = (Button) v2.findViewById(R.id.doneFileBtn)
    if (button6 != null) {
        button6.setVisibility(8)
        }
    val linearLayout: LinearLayout = (LinearLayout) v2.findViewById(R.id.fileProgressContainer)
    if (linearLayout != null) {
        linearLayout.setVisibility(8)
        }
    val linearLayout2: LinearLayout = (LinearLayout) v2.findViewById(R.id.fileTransferBtnContainer)
    if (linearLayout2 != null) {
        linearLayout2.setVisibility(8)
        }
    }

private static final String getFilesView$fmtSize(long b) {
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
    if (b < RealWebSocket.DEFAULT_MINIMUM_DEFLATE_SIZE) {
        return b + " B"
        }
    val format3: String = String.format("%.0f KB", Arrays.copyOf(new Object[]{Double.valueOf(b / 1024.0d)}, 1))
    Intrinsics.checkNotNullExpressionValue(format3, "format(...)")
    var format3: return? = null
    }

fun getRemoteView(): View {
    val v: View = LayoutInflater.from(this).inflate(R.layout.page_remote, (ViewGroup) null)
    val buttons: List<Pair> = CollectionsKt.listOf((Object[]) new Pair[]{TuplesKt.to(Integer.valueOf(R.id.btnPrev), "media_prev"), TuplesKt.to(Integer.valueOf(R.id.btnPlayPause), "media_play_pause"), TuplesKt.to(Integer.valueOf(R.id.btnNext), "media_next"), TuplesKt.to(Integer.valueOf(R.id.btnVolDown), "vol_down"), TuplesKt.to(Integer.valueOf(R.id.btnMute), "vol_mute"), TuplesKt.to(Integer.valueOf(R.id.btnVolUp), "vol_up"), TuplesKt.to(Integer.valueOf(R.id.btnLock), "lock")})
    for (Pair pair : buttons) {
        val id: Int = ((Number) pair.component1()).intValue()
        val cmd: String = (String) pair.component2()
        val button: Button = (Button) v.findViewById(id)
        if (button != null) {
            NativeButtonKt.applyDarkTheme$default(button, 0, 0, Intrinsics.areEqual(cmd, "media_play_pause"), 3, null)
            }
        val button2: Button = (Button) v.findViewById(id)
        if (button2 != null) {
            button2.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda103
                override
                fun onClick(view: View): Unit {
                    MainActivity.getRemoteView$lambda$63(cmd, view)
                    }
                })
            }
        }
    val button3: Button = (Button) v.findViewById(R.id.btnScreenshot)
    if (button3 != null) {
        NativeButtonKt.applyDarkTheme$default(button3, 0, 0, false, 7, null)
        }
    val button4: Button = (Button) v.findViewById(R.id.btnScreenshot)
    if (button4 != null) {
        button4.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda104
            override
            fun onClick(view: View): Unit {
                MainActivity.getRemoteView$lambda$64(view)
                }
            })
        }
    val button5: Button = (Button) v.findViewById(R.id.btnKeyboard)
    if (button5 != null) {
        NativeButtonKt.applyDarkTheme$default(button5, 0, 0, false, 7, null)
        }
    val button6: Button = (Button) v.findViewById(R.id.btnKeyboard)
    if (button6 != null) {
        button6.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda105
            override
            fun onClick(view: View): Unit {
                MainActivity.getRemoteView$lambda$66(MainActivity.this, view)
                }
            })
        }
    val mediaInfoText: TextView = (TextView) v.findViewById(R.id.mediaInfoText)
    val mediaCoverImg: ImageView = (ImageView) v.findViewById(R.id.mediaCoverImg)
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$getRemoteView$4(mediaInfoText, null), 3, null)
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$getRemoteView$5(mediaCoverImg, null), 3, null)
    Intrinsics.checkNotNull(v)
    var v: return? = null
    }

public static final Unit getRemoteView$lambda$63(String $cmd, View it) {
    ConnectionManager.INSTANCE.sendMediaCommand($cmd)
    }

public static final Unit getRemoteView$lambda$64(View it) {
    try {
        ConnectionManager.INSTANCE.requestPcScreenshot()
        } catch (Exception e) {
        }
    }

public static final Unit getRemoteView$lambda$66(MainActivity this$0, View it) {
    val keyboardView: View = this$0.getFullKeyboardView()
    val dialog: AlertDialog = new AlertDialog.Builder(this$0).setView(keyboardView).setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda90
    override
    fun onDismiss(dialogInterface: DialogInterface): Unit {
        MainActivity.getRemoteView$lambda$66$lambda$65(dialogInterface)
        }
    }).create()
val window: Window = dialog.getWindow()
if (window != null) {
    window.setBackgroundDrawableResource(android.R.color.black)
    }
val window2: Window = dialog.getWindow()
if (window2 != null) {
    window2.setLayout(-1, -1)
    }
dialog.show()
}

public static final Unit getRemoteView$lambda$66$lambda$65(DialogInterface it) {
    }

fun wireFullKeyboard(rootView: View): Unit {
    val modifierStatus: TextView = (TextView) rootView.findViewById(R.id.modifierStatus)
    val lockedModifiers: Set = new LinkedHashSet()
    val keyMap: Map = MapsKt.mapOf(TuplesKt.to(Integer.valueOf(R.id.k_1), DiskLruCache.VERSION), TuplesKt.to(Integer.valueOf(R.id.k_2), ExifInterface.GPS_MEASUREMENT_2D), TuplesKt.to(Integer.valueOf(R.id.k_3), ExifInterface.GPS_MEASUREMENT_3D), TuplesKt.to(Integer.valueOf(R.id.k_4), "4"), TuplesKt.to(Integer.valueOf(R.id.k_5), "5"), TuplesKt.to(Integer.valueOf(R.id.k_6), "6"), TuplesKt.to(Integer.valueOf(R.id.k_7), "7"), TuplesKt.to(Integer.valueOf(R.id.k_8), "8"), TuplesKt.to(Integer.valueOf(R.id.k_9), "9"), TuplesKt.to(Integer.valueOf(R.id.k_0), "0"), TuplesKt.to(Integer.valueOf(R.id.k_q), "q"), TuplesKt.to(Integer.valueOf(R.id.k_w), "w"), TuplesKt.to(Integer.valueOf(R.id.k_e), "e"), TuplesKt.to(Integer.valueOf(R.id.k_r), "r"), TuplesKt.to(Integer.valueOf(R.id.k_t), "t"), TuplesKt.to(Integer.valueOf(R.id.k_y), "y"), TuplesKt.to(Integer.valueOf(R.id.k_u), "u"), TuplesKt.to(Integer.valueOf(R.id.k_i), "i"), TuplesKt.to(Integer.valueOf(R.id.k_o), "o"), TuplesKt.to(Integer.valueOf(R.id.k_p), "p"), TuplesKt.to(Integer.valueOf(R.id.k_a), "a"), TuplesKt.to(Integer.valueOf(R.id.k_s), "s"), TuplesKt.to(Integer.valueOf(R.id.k_d), "d"), TuplesKt.to(Integer.valueOf(R.id.k_f), "f"), TuplesKt.to(Integer.valueOf(R.id.k_g), "g"), TuplesKt.to(Integer.valueOf(R.id.k_h), "h"), TuplesKt.to(Integer.valueOf(R.id.k_j), "j"), TuplesKt.to(Integer.valueOf(R.id.k_k), "k"), TuplesKt.to(Integer.valueOf(R.id.k_l), "l"), TuplesKt.to(Integer.valueOf(R.id.k_z), "z"), TuplesKt.to(Integer.valueOf(R.id.k_x), "x"), TuplesKt.to(Integer.valueOf(R.id.k_c), "c"), TuplesKt.to(Integer.valueOf(R.id.k_v), "v"), TuplesKt.to(Integer.valueOf(R.id.k_b), "b"), TuplesKt.to(Integer.valueOf(R.id.k_n), "n"), TuplesKt.to(Integer.valueOf(R.id.k_m), "m"), TuplesKt.to(Integer.valueOf(R.id.k_comma), ","), TuplesKt.to(Integer.valueOf(R.id.k_dot), "."), TuplesKt.to(Integer.valueOf(R.id.k_space), "space"), TuplesKt.to(Integer.valueOf(R.id.k_enter), "enter"), TuplesKt.to(Integer.valueOf(R.id.k_bksp), "backspace"), TuplesKt.to(Integer.valueOf(R.id.k_up), "up"), TuplesKt.to(Integer.valueOf(R.id.k_down), "down"), TuplesKt.to(Integer.valueOf(R.id.k_left), "left"), TuplesKt.to(Integer.valueOf(R.id.k_right), "right"), TuplesKt.to(Integer.valueOf(R.id.k_f1), "f1"), TuplesKt.to(Integer.valueOf(R.id.k_f2), "f2"), TuplesKt.to(Integer.valueOf(R.id.k_f3), "f3"), TuplesKt.to(Integer.valueOf(R.id.k_f4), "f4"), TuplesKt.to(Integer.valueOf(R.id.k_f5), "f5"), TuplesKt.to(Integer.valueOf(R.id.k_f6), "f6"), TuplesKt.to(Integer.valueOf(R.id.k_f7), "f7"), TuplesKt.to(Integer.valueOf(R.id.k_f8), "f8"), TuplesKt.to(Integer.valueOf(R.id.k_f9), "f9"), TuplesKt.to(Integer.valueOf(R.id.k_f10), "f10"), TuplesKt.to(Integer.valueOf(R.id.k_f11), "f11"), TuplesKt.to(Integer.valueOf(R.id.k_f12), "f12"))
    for (Map.Entry entry : keyMap.entrySet()) {
        val id: Int = ((Number) entry.getKey()).intValue()
        val key: String = (String) entry.getValue()
        val button: Button = (Button) rootView.findViewById(id)
        if (button != null) {
            button.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda54
                override
                fun onClick(view: View): Unit {
                    MainActivity.wireFullKeyboard$sendKey(lockedModifiers, key)
                    }
                })
            }
        }
    val modMap: Map = MapsKt.mapOf(TuplesKt.to(Integer.valueOf(R.id.k_shift), "shift"), TuplesKt.to(Integer.valueOf(R.id.k_ctrl), "ctrl"), TuplesKt.to(Integer.valueOf(R.id.k_alt), "alt"), TuplesKt.to(Integer.valueOf(R.id.k_win), "win"))
    for (Map.Entry entry2 : modMap.entrySet()) {
        val id2: Int = ((Number) entry2.getKey()).intValue()
        val mod: String = (String) entry2.getValue()
        val btn: Button = (Button) rootView.findViewById(id2)
        if (btn != null) {
            btn.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda55
                override
                fun onClick(view: View): Unit {
                    MainActivity.wireFullKeyboard$lambda$70(lockedModifiers, mod, btn, modifierStatus, view)
                    }
                })
            }
        if (btn != null) {
            btn.setOnLongClickListener(null)
            }
        }
    }

private static final Unit wireFullKeyboard$updateModifierStatus(Set<String> set, TextView modifierStatus) {
    val locked: String = CollectionsKt.joinToString$default(set, Marker.ANY_NON_NULL_MARKER, null, null, 0, null, new Function1() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda0
    override
    fun invoke(obj: Any): Any {
        CharSequence wireFullKeyboard$updateModifierStatus$lambda$67
        wireFullKeyboard$updateModifierStatus$lambda$67 = MainActivity.wireFullKeyboard$updateModifierStatus$lambda$67(obj)
        return wireFullKeyboard$updateModifierStatus$lambda$67
        }
    }, 30, null)
if (modifierStatus != null) {
    val str: String = locked
    if (str.length() == 0) {
        str = "无"
        }
    modifierStatus.setText("修饰键: " + ((Object) str))
    }
}

public static final CharSequence wireFullKeyboard$updateModifierStatus$lambda$67(String it) {
    Intrinsics.checkNotNullParameter(it, "it")
    return it + "(锁)"
    }

public static final Unit wireFullKeyboard$sendKey(Set<String> set, String key) {
    var modStr: String? = null
    if (set.isEmpty()) {
        modStr = ""
        } else {
        modStr = CollectionsKt.joinToString$default(set, Marker.ANY_NON_NULL_MARKER, null, null, 0, null, null, 62, null) + Marker.ANY_NON_NULL_MARKER
        }
    ConnectionManager.INSTANCE.sendMediaCommand("key_" + modStr + key)
    }

public static final Unit wireFullKeyboard$lambda$70(Set $lockedModifiers, String $mod, Button $btn, TextView $modifierStatus, View it) {
    if ($lockedModifiers.contains($mod)) {
        $lockedModifiers.remove($mod)
        $btn.setBackgroundResource(R.drawable.kb_modifier_bg)
        } else {
        $lockedModifiers.add($mod)
        $btn.setBackgroundColor(-12947515)
        }
    wireFullKeyboard$updateModifierStatus($lockedModifiers, $modifierStatus)
    }

fun getFullKeyboardView(): View {
    val v: View = LayoutInflater.from(this).inflate(R.layout.page_full_keyboard, (ViewGroup) null)
    Intrinsics.checkNotNull(v)
    wireFullKeyboard(v)
    var v: return? = null
    }

fun getClipboardView(): View {
    ClipData.Item itemAt
    var coerceToText: CharSequence? = null
    var obj: String? = null
    LinearLayout $this$getClipboardView_u24lambda_u2471 = LinearLayout(this)
    $this$getClipboardView_u24lambda_u2471.setOrientation(1)
    $this$getClipboardView_u24lambda_u2471.setBackgroundColor(-14803426)
    $this$getClipboardView_u24lambda_u2471.setPadding(dp(16), dp(16), dp(16), dp(16))
    TextView $this$getClipboardView_u24lambda_u2472 = TextView(this)
    $this$getClipboardView_u24lambda_u2472.setText("剪贴板")
    $this$getClipboardView_u24lambda_u2472.setTextColor(-1)
    $this$getClipboardView_u24lambda_u2472.setTextSize(20.0f)
    $this$getClipboardView_u24lambda_u2472.setTypeface($this$getClipboardView_u24lambda_u2472.getTypeface(), 1)
    $this$getClipboardView_u24lambda_u2472.setPadding(0, 0, 0, dp(16))
    $this$getClipboardView_u24lambda_u2471.addView($this$getClipboardView_u24lambda_u2472)
    LinearLayout $this$getClipboardView_u24lambda_u2473 = LinearLayout(this)
    $this$getClipboardView_u24lambda_u2473.setOrientation(1)
    $this$getClipboardView_u24lambda_u2473.setBackgroundColor(-13816531)
    $this$getClipboardView_u24lambda_u2473.setPadding(dp(16), dp(16), dp(16), dp(16))
    TextView $this$getClipboardView_u24lambda_u2474 = TextView(this)
    $this$getClipboardView_u24lambda_u2474.setText("当前剪贴板:")
    $this$getClipboardView_u24lambda_u2474.setTextColor(-5197648)
    $this$getClipboardView_u24lambda_u2474.setTextSize(12.0f)
    $this$getClipboardView_u24lambda_u2474.setPadding(0, 0, 0, dp(8))
    final TextView $this$getClipboardView_u24lambda_u2475 = TextView(this)
    $this$getClipboardView_u24lambda_u2475.setId(R.id.currentClipText)
    val str: String = ""
    $this$getClipboardView_u24lambda_u2475.setText("")
    $this$getClipboardView_u24lambda_u2475.setTextColor(-1)
    $this$getClipboardView_u24lambda_u2475.setTextSize(14.0f)
    $this$getClipboardView_u24lambda_u2475.setMinHeight(dp(60))
    $this$getClipboardView_u24lambda_u2475.setPadding(dp(8), dp(8), dp(8), dp(8))
    $this$getClipboardView_u24lambda_u2475.setBackgroundColor(-14803426)
    try {
        val systemService: Any = getSystemService("clipboard")
        Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.content.ClipboardManager")
        val cm: ClipboardManager = (ClipboardManager) systemService
        val primaryClip: ClipData = cm.getPrimaryClip()
        if (primaryClip != null && (itemAt = primaryClip.getItemAt(0)) != null && (coerceToText = itemAt.coerceToText(this)) != null && (obj = coerceToText.toString()) != null) {
            str = obj
            }
        val t: String = str
        if (t.length() > 0) {
            $this$getClipboardView_u24lambda_u2475.setText(t)
            }
        } catch (Exception e) {
        }
    val btnRow: LinearLayout = new LinearLayout(this)
    btnRow.setOrientation(0)
    btnRow.setPadding(0, dp(8), 0, 0)
    Button $this$getClipboardView_u24lambda_u2477 = Button(this)
    $this$getClipboardView_u24lambda_u2477.setText("复制内容")
    NativeButtonKt.applyDarkTheme$default($this$getClipboardView_u24lambda_u2477, 0, 0, false, 7, null)
    Button $this$getClipboardView_u24lambda_u2478 = Button(this)
    $this$getClipboardView_u24lambda_u2478.setText("推送到电脑")
    NativeButtonKt.applyDarkTheme$default($this$getClipboardView_u24lambda_u2478, 0, 0, true, 3, null)
    LinearLayout.LayoutParams $this$getClipboardView_u24lambda_u2479 = new LinearLayout.LayoutParams(0, dp(48), 1.0f)
    $this$getClipboardView_u24lambda_u2479.rightMargin = dp(8)
    val unit: Unit = Unit.INSTANCE
    btnRow.addView($this$getClipboardView_u24lambda_u2477, $this$getClipboardView_u24lambda_u2479)
    btnRow.addView($this$getClipboardView_u24lambda_u2478, new LinearLayout.LayoutParams(0, dp(48), 1.0f))
    $this$getClipboardView_u24lambda_u2477.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda109
        override
        fun onClick(view: View): Unit {
            MainActivity.getClipboardView$lambda$80($this$getClipboardView_u24lambda_u2475, this, view)
            }
        })
    $this$getClipboardView_u24lambda_u2478.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda110
        override
        fun onClick(view: View): Unit {
            MainActivity.getClipboardView$lambda$81(MainActivity.this, view)
            }
        })
    $this$getClipboardView_u24lambda_u2473.addView($this$getClipboardView_u24lambda_u2474)
    $this$getClipboardView_u24lambda_u2473.addView($this$getClipboardView_u24lambda_u2475)
    $this$getClipboardView_u24lambda_u2473.addView(btnRow)
    LinearLayout.LayoutParams $this$getClipboardView_u24lambda_u2482 = new LinearLayout.LayoutParams(-1, -2)
    $this$getClipboardView_u24lambda_u2482.bottomMargin = dp(12)
    val unit2: Unit = Unit.INSTANCE
    $this$getClipboardView_u24lambda_u2471.addView($this$getClipboardView_u24lambda_u2473, $this$getClipboardView_u24lambda_u2482)
    TextView $this$getClipboardView_u24lambda_u2483 = TextView(this)
    $this$getClipboardView_u24lambda_u2483.setText("剪贴板历史 / 收藏")
    $this$getClipboardView_u24lambda_u2483.setTextColor(-1)
    $this$getClipboardView_u24lambda_u2483.setTextSize(16.0f)
    $this$getClipboardView_u24lambda_u2483.setTypeface($this$getClipboardView_u24lambda_u2483.getTypeface(), 1)
    $this$getClipboardView_u24lambda_u2483.setPadding(0, 0, 0, dp(8))
    $this$getClipboardView_u24lambda_u2471.addView($this$getClipboardView_u24lambda_u2483)
    LinearLayout $this$getClipboardView_u24lambda_u2484 = LinearLayout(this)
    $this$getClipboardView_u24lambda_u2484.setOrientation(0)
    $this$getClipboardView_u24lambda_u2484.setBackgroundColor(-13816531)
    $this$getClipboardView_u24lambda_u2484.setPadding(dp(8), dp(8), dp(8), dp(8))
    final EditText $this$getClipboardView_u24lambda_u2485 = EditText(this)
    $this$getClipboardView_u24lambda_u2485.setHint("搜索...")
    $this$getClipboardView_u24lambda_u2485.setTextColor(-1)
    $this$getClipboardView_u24lambda_u2485.setHintTextColor(-10066330)
    $this$getClipboardView_u24lambda_u2485.setBackgroundColor(-14803426)
    $this$getClipboardView_u24lambda_u2485.setPadding(dp(8), 0, dp(8), 0)
    $this$getClipboardView_u24lambda_u2485.setTextSize(12.0f)
    $this$getClipboardView_u24lambda_u2485.setInputType(1)
    final Button $this$getClipboardView_u24lambda_u2486 = Button(this)
    $this$getClipboardView_u24lambda_u2486.setText("历史")
    final Button $this$getClipboardView_u24lambda_u2487 = Button(this)
    $this$getClipboardView_u24lambda_u2487.setText("收藏")
    NativeButtonKt.applyDarkTheme$default($this$getClipboardView_u24lambda_u2486, 0, 0, true, 3, null)
    NativeButtonKt.applyDarkTheme$default($this$getClipboardView_u24lambda_u2487, 0, 0, false, 7, null)
    $this$getClipboardView_u24lambda_u2484.addView($this$getClipboardView_u24lambda_u2485, new LinearLayout.LayoutParams(0, dp(40), 1.0f))
    LinearLayout.LayoutParams $this$getClipboardView_u24lambda_u2488 = new LinearLayout.LayoutParams(-2, dp(40))
    $this$getClipboardView_u24lambda_u2488.leftMargin = dp(4)
    val unit3: Unit = Unit.INSTANCE
    $this$getClipboardView_u24lambda_u2484.addView($this$getClipboardView_u24lambda_u2486, $this$getClipboardView_u24lambda_u2488)
    LinearLayout.LayoutParams $this$getClipboardView_u24lambda_u2489 = new LinearLayout.LayoutParams(-2, dp(40))
    $this$getClipboardView_u24lambda_u2489.leftMargin = dp(4)
    val unit4: Unit = Unit.INSTANCE
    $this$getClipboardView_u24lambda_u2484.addView($this$getClipboardView_u24lambda_u2487, $this$getClipboardView_u24lambda_u2489)
    LinearLayout.LayoutParams $this$getClipboardView_u24lambda_u2490 = new LinearLayout.LayoutParams(-1, -2)
    $this$getClipboardView_u24lambda_u2490.bottomMargin = dp(8)
    val unit5: Unit = Unit.INSTANCE
    $this$getClipboardView_u24lambda_u2471.addView($this$getClipboardView_u24lambda_u2484, $this$getClipboardView_u24lambda_u2490)
    val list: ListView = new ListView(this)
    final TextView $this$getClipboardView_u24lambda_u2491 = TextView(this)
    $this$getClipboardView_u24lambda_u2491.setText("暂无记录")
    $this$getClipboardView_u24lambda_u2491.setTextColor(-10066330)
    $this$getClipboardView_u24lambda_u2491.setTextSize(13.0f)
    $this$getClipboardView_u24lambda_u2491.setGravity(17)
    $this$getClipboardView_u24lambda_u2491.setVisibility(8)
    LinearLayout.LayoutParams $this$getClipboardView_u24lambda_u2492 = new LinearLayout.LayoutParams(-1, 0)
    $this$getClipboardView_u24lambda_u2492.weight = 1.0f
    val unit6: Unit = Unit.INSTANCE
    $this$getClipboardView_u24lambda_u2471.addView(list, $this$getClipboardView_u24lambda_u2492)
    $this$getClipboardView_u24lambda_u2471.addView($this$getClipboardView_u24lambda_u2491)
    $this$getClipboardView_u24lambda_u2486.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda111
        override
        fun onClick(view: View): Unit {
            MainActivity.getClipboardView$lambda$96(MainActivity.this, $this$getClipboardView_u24lambda_u2486, $this$getClipboardView_u24lambda_u2487, $this$getClipboardView_u24lambda_u2485, $this$getClipboardView_u24lambda_u2491, list, view)
            }
        })
    $this$getClipboardView_u24lambda_u2487.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda112
        override
        fun onClick(view: View): Unit {
            MainActivity.getClipboardView$lambda$97(MainActivity.this, $this$getClipboardView_u24lambda_u2487, $this$getClipboardView_u24lambda_u2486, $this$getClipboardView_u24lambda_u2485, $this$getClipboardView_u24lambda_u2491, list, view)
            }
        })
    $this$getClipboardView_u24lambda_u2485.setOnEditorActionListener(new TextView.OnEditorActionListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda113
        override
        fun onEditorAction(textView: TextView, i: Int, keyEvent: KeyEvent): Boolean {
            boolean clipboardView$lambda$98
            clipboardView$lambda$98 = MainActivity.getClipboardView$lambda$98($this$getClipboardView_u24lambda_u2485, this, $this$getClipboardView_u24lambda_u2491, list, textView, i, keyEvent)
            return clipboardView$lambda$98
            }
        })
    $this$getClipboardView_u24lambda_u2485.addTextChangedListener(TextWatcher() { // from class: com.phonehub.MainActivity$getClipboardView$12
        override
        fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int): Unit {
            }

        override
        fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int): Unit {
            MainActivity.getClipboardView$refresh($this$getClipboardView_u24lambda_u2485, this, $this$getClipboardView_u24lambda_u2491, list)
            }

        override
        fun afterTextChanged(s: Editable): Unit {
            }
        })
    list.post(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda114
        override
        fun run(): Unit {
            MainActivity.getClipboardView$refresh($this$getClipboardView_u24lambda_u2485, this, $this$getClipboardView_u24lambda_u2491, list)
            }
        })
    return $this$getClipboardView_u24lambda_u2471
    }

public static final Unit getClipboardView$lambda$80(TextView $currentClipText, MainActivity this$0, View it) {
    var text: String? = null
    val text2: CharSequence = $currentClipText.getText()
    if (text2 == null || (text = text2.toString()) == null) {
        text = ""
        }
    if (text.length() > 0) {
        val systemService: Any = this$0.getSystemService("clipboard")
        Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.content.ClipboardManager")
        val cm: ClipboardManager = (ClipboardManager) systemService
        cm.setPrimaryClip(ClipData.newPlainText("PhoneHub", text))
        Toast.makeText(this$0, "已复制", 0).show()
        }
    }

public static final Unit getClipboardView$lambda$81(MainActivity this$0, View it) {
    var text: String? = null
    ClipData.Item itemAt
    var coerceToText: CharSequence? = null
    val systemService: Any = this$0.getSystemService("clipboard")
    Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.content.ClipboardManager")
    val cm: ClipboardManager = (ClipboardManager) systemService
    val primaryClip: ClipData = cm.getPrimaryClip()
    if (primaryClip == null || (itemAt = primaryClip.getItemAt(0)) == null || (coerceToText = itemAt.coerceToText(this$0)) == null || (text = coerceToText.toString()) == null) {
        text = ""
        }
    if (text.length() > 0) {
        ConnectionManager.INSTANCE.sendClipboard(text)
        Toast.makeText(this$0, "已推送", 0).show()
        }
    }

public static final Unit getClipboardView$refresh(final EditText search, final MainActivity this$0, final TextView empty, final ListView list) {
    var str: String? = null
    var value: List? = null
    val text: Editable = search.getText()
    val str2: String = ""
    if (text == null || (str = text.toString()) == null) {
        str = ""
        }
    val q: String = str
    if (Intrinsics.areEqual(this$0.clipViewMode, "history")) {
        value = q.length() == 0 ? ConnectionManager.INSTANCE.getClipboardHistory().getValue() : ConnectionManager.INSTANCE.searchClipboardHistory(q)
        } else {
        value = q.length() == 0 ? ConnectionManager.INSTANCE.getClipboardFavorites().getValue() : ConnectionManager.INSTANCE.searchClipboardFavorites(q)
        }
    val items: List = value
    if (items.isEmpty()) {
        empty.setVisibility(0)
        empty.setText(Intrinsics.areEqual(this$0.clipViewMode, "history") ? "暂无历史" : "暂无收藏")
        list.setAdapter((ListAdapter) null)
        return
        }
    empty.setVisibility(8)
    List $this$map$iv = items
    int $i$f$map = 0
    Collection destination$iv$iv = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv, 10))
    Iterable $this$mapTo$iv$iv = $this$map$iv
    int $i$f$mapTo = 0
    val it: Iterator = $this$mapTo$iv$iv.iterator()
    while (it.hasNext()) {
        Object item$iv$iv = it.next()
        ConnectionManager.ClipboardItem it2 = (ConnectionManager.ClipboardItem) item$iv$iv
        val favMark: String = it2.getFavorite() ? " ★" : str2
        destination$iv$iv.add(StringsKt.take(it2.getContent(), 80) + "\n[" + it2.getSource() + "] " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it2.getTimestamp())) + favMark)
        it = it
        $this$map$iv = $this$map$iv
        str2 = str2
        $i$f$map = $i$f$map
        $this$mapTo$iv$iv = $this$mapTo$iv$iv
        $i$f$mapTo = $i$f$mapTo
        }
    val displays: List = (List) destination$iv$iv
    list.setAdapter((ListAdapter) ArrayAdapter(this$0, android.R.layout.simple_list_item_1, displays))
    list.setOnItemClickListener(new AdapterView.OnItemClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda107
        override
        fun onItemClick(adapterView: AdapterView, view: View, i: Int, j: Long): Unit {
            MainActivity.getClipboardView$refresh$lambda$94(items, this$0, adapterView, view, i, j)
            }
        })
    list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda108
        override
        fun onItemLongClick(adapterView: AdapterView, view: View, i: Int, j: Long): Boolean {
            boolean clipboardView$refresh$lambda$95
            clipboardView$refresh$lambda$95 = MainActivity.getClipboardView$refresh$lambda$95(items, this$0, search, empty, list, adapterView, view, i, j)
            return clipboardView$refresh$lambda$95
            }
        })
    }

public static final Unit getClipboardView$refresh$lambda$94(List $items, MainActivity this$0, AdapterView adapterView, View view, int pos, long j) {
    ConnectionManager.ClipboardItem item = (ConnectionManager.ClipboardItem) $items.get(pos)
    val systemService: Any = this$0.getSystemService("clipboard")
    Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.content.ClipboardManager")
    val cm: ClipboardManager = (ClipboardManager) systemService
    cm.setPrimaryClip(ClipData.newPlainText("PhoneHub", item.getContent()))
    Toast.makeText(this$0, "已复制", 0).show()
    }

public static final boolean getClipboardView$refresh$lambda$95(List $items, MainActivity this$0, EditText $search, TextView $empty, ListView $list, AdapterView adapterView, View view, int pos, long j) {
    ConnectionManager.ClipboardItem item = (ConnectionManager.ClipboardItem) $items.get(pos)
    ConnectionManager.INSTANCE.toggleFavorite(item)
    Toast.makeText(this$0, !item.getFavorite() ? "已收藏" : "取消收藏", 0).show()
    getClipboardView$refresh($search, this$0, $empty, $list)
    var true: return? = null
    }

public static final Unit getClipboardView$lambda$96(MainActivity this$0, Button $btnHist, Button $btnFav, EditText $search, TextView $empty, ListView $list, View it) {
    this$0.clipViewMode = "history"
    NativeButtonKt.applyDarkTheme$default($btnHist, 0, 0, true, 3, null)
    NativeButtonKt.applyDarkTheme$default($btnFav, 0, 0, false, 7, null)
    getClipboardView$refresh($search, this$0, $empty, $list)
    }

public static final Unit getClipboardView$lambda$97(MainActivity this$0, Button $btnFav, Button $btnHist, EditText $search, TextView $empty, ListView $list, View it) {
    this$0.clipViewMode = "favorite"
    NativeButtonKt.applyDarkTheme$default($btnFav, 0, 0, true, 3, null)
    NativeButtonKt.applyDarkTheme$default($btnHist, 0, 0, false, 7, null)
    getClipboardView$refresh($search, this$0, $empty, $list)
    }

public static final boolean getClipboardView$lambda$98(EditText $search, MainActivity this$0, TextView $empty, ListView $list, TextView textView, int i, KeyEvent keyEvent) {
    getClipboardView$refresh($search, this$0, $empty, $list)
    var true: return? = null
    }

fun getLocationView(): View {
    val container: LinearLayout = new LinearLayout(this)
    container.setOrientation(1)
    container.setGravity(17)
    container.setBackgroundColor(-14803426)
    container.setLayoutParams(new LinearLayout.LayoutParams(-1, -1))
    TextView $this$getLocationView_u24lambda_u24101 = TextView(this)
    $this$getLocationView_u24lambda_u24101.setText("移动路线图")
    $this$getLocationView_u24lambda_u24101.setTextColor(-1)
    $this$getLocationView_u24lambda_u24101.setTextSize(22.0f)
    $this$getLocationView_u24lambda_u24101.setPadding(0, 0, 0, dp(32))
    TextView $this$getLocationView_u24lambda_u24102 = TextView(this)
    $this$getLocationView_u24lambda_u24102.setText("该功能暂未开放，敬请期待")
    $this$getLocationView_u24lambda_u24102.setTextColor(-5197648)
    $this$getLocationView_u24lambda_u24102.setTextSize(16.0f)
    container.addView($this$getLocationView_u24lambda_u24101)
    container.addView($this$getLocationView_u24lambda_u24102)
    var container: return? = null
    }

fun getScreenshotView(): View {
    val v: View = LayoutInflater.from(this).inflate(R.layout.page_screenshot, (ViewGroup) null)
    val button: Button = (Button) v.findViewById(R.id.btnTakeScreenshot)
    if (button != null) {
        NativeButtonKt.applyDarkTheme$default(button, 0, 0, true, 3, null)
        }
    val button2: Button = (Button) v.findViewById(R.id.btnTakeScreenshot)
    if (button2 != null) {
        button2.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda56
            override
            fun onClick(view: View): Unit {
                MainActivity.getScreenshotView$lambda$103(view)
                }
            })
        }
    Intrinsics.checkNotNull(v)
    refreshScreenshotList(v)
    var v: return? = null
    }

public static final Unit getScreenshotView$lambda$103(View it) {
    ConnectionManager.INSTANCE.triggerScreenshot()
    }

fun refreshScreenshotList(v: View): Unit {
    val list: ListView = (ListView) v.findViewById(R.id.screenshotList)
    val empty: TextView = (TextView) v.findViewById(R.id.screenshotEmpty)
    val localDir: File = new File(getExternalFilesDir(null), "Received")
    val files: List = new ArrayList()
    val it: Array<File> = localDir.listFiles(new FileFilter() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda39
    override
    fun accept(file: File): Boolean {
        boolean refreshScreenshotList$lambda$104
        refreshScreenshotList$lambda$104 = MainActivity.refreshScreenshotList$lambda$104(file)
        return refreshScreenshotList$lambda$104
        }
    })
if (it != null) {
    CollectionsKt.addAll(files, it)
    }
List $this$sortedByDescending$iv = files
val sortedFiles: List = CollectionsKt.sortedWith($this$sortedByDescending$iv, new Comparator() { // from class: com.phonehub.MainActivity$refreshScreenshotList$$inlined$sortedByDescending$1
override
fun compare(t: T, t2: T): Int {
    val it2: File = (File) t2
    val it3: File = (File) t
    return ComparisonsKt.compareValues(Long.valueOf(it2.lastModified()), Long.valueOf(it3.lastModified()))
    }
})
if (sortedFiles.isEmpty()) {
    empty.setVisibility(0)
    list.setAdapter((ListAdapter) null)
    return
    }
empty.setVisibility(8)
List $this$map$iv = sortedFiles
Collection destination$iv$iv = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv, 10))
for (Object item$iv$iv : $this$map$iv) {
    val it2: File = (File) item$iv$iv
    destination$iv$iv.add(it2.getName() + "\n" + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it2.lastModified())))
    empty = empty
    localDir = localDir
    $this$map$iv = $this$map$iv
    files = files
    }
val names: List = (List) destination$iv$iv
list.setAdapter((ListAdapter) ArrayAdapter(this, android.R.layout.simple_list_item_1, names))
list.setOnItemClickListener(new AdapterView.OnItemClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda41
    override
    fun onItemClick(adapterView: AdapterView, view: View, i: Int, j: Long): Unit {
        MainActivity.refreshScreenshotList$lambda$108(MainActivity.this, sortedFiles, adapterView, view, i, j)
        }
    })
}

public static final boolean refreshScreenshotList$lambda$104(File f) {
    val name: String = f.getName()
    Intrinsics.checkNotNullExpressionValue(name, "getName(...)")
    if (!StringsKt.endsWith$default(name, ".png", false, 2, (Object) null)) {
        var false: return? = null
        }
    val name2: String = f.getName()
    Intrinsics.checkNotNullExpressionValue(name2, "getName(...)")
    return StringsKt.startsWith$default(name2, "screenshot_", false, 2, (Object) null)
    }

public static final Unit refreshScreenshotList$lambda$108(MainActivity this$0, List $sortedFiles, AdapterView adapterView, View view, int pos, long j) {
    this$0.showScreenshotViewer((File) $sortedFiles.get(pos))
    }

public static final class BrushOp {
    var color: private final int? = null
    var path: private final Path? = null
    var width: private final float? = null

    fun BrushOp(path: Path, color: Int, width: Float): public {
        Intrinsics.checkNotNullParameter(path, "path")
        this.path = path
        this.color = color
        this.width = width
        }

    fun getColor(): Int {
        return this.color
        }

    fun getPath(): Path {
        return this.path
        }

    fun getWidth(): Float {
        return this.width
        }
    }

public static final class ArrowOp {
    var color: private final int? = null
    var ex: private float? = null
    var ey: private float? = null
    var sx: private final float? = null
    var sy: private final float? = null
    var width: private final float? = null

    fun ArrowOp(sx: Float, sy: Float, ex: Float, ey: Float, color: Int, width: Float): public {
        this.sx = sx
        this.sy = sy
        this.ex = ex
        this.ey = ey
        this.color = color
        this.width = width
        }

    fun getColor(): Int {
        return this.color
        }

    fun getEx(): Float {
        return this.ex
        }

    fun getEy(): Float {
        return this.ey
        }

    fun getSx(): Float {
        return this.sx
        }

    fun getSy(): Float {
        return this.sy
        }

    fun getWidth(): Float {
        return this.width
        }

    fun setEx(f: Float): Unit {
        this.ex = f
        }

    fun setEy(f: Float): Unit {
        this.ey = f
        }
    }

public static final class RectOp {
    var color: private final int? = null
    var rect: private final RectF? = null
    var width: private final float? = null

    fun RectOp(rect: RectF, color: Int, width: Float): public {
        Intrinsics.checkNotNullParameter(rect, "rect")
        this.rect = rect
        this.color = color
        this.width = width
        }

    fun getColor(): Int {
        return this.color
        }

    fun getRect(): RectF {
        return this.rect
        }

    fun getWidth(): Float {
        return this.width
        }
    }

public static final class TextOp {
    var color: private final int? = null
    var size: private final float? = null
    var text: private final String? = null
    var x: private final float? = null
    var y: private final float? = null

    fun TextOp(text: String, x: Float, y: Float, color: Int, size: Float): public {
        Intrinsics.checkNotNullParameter(text, "text")
        this.text = text
        this.x = x
        this.y = y
        this.color = color
        this.size = size
        }

    fun getColor(): Int {
        return this.color
        }

    fun getSize(): Float {
        return this.size
        }

    fun getText(): String {
        return this.text
        }

    fun getX(): Float {
        return this.x
        }

    fun getY(): Float {
        return this.y
        }
    }

public static final class MosaicOp {
    var points: private final List<Pair<Float, Float>>? = null

    fun MosaicOp(points: List<Pair<Float, Float>>): public {
        Intrinsics.checkNotNullParameter(points, "points")
        this.points = points
        }

    fun getPoints(): List<Pair<Float, Float>> {
        return this.points
        }
    }

class AnnotationOverlayView : View {
    var currentOp: private Object? = null
    var currentTool: private String? = null
    var mosaicBmp: private Bitmap? = null
    var offsetX: private float? = null
    var offsetY: private float? = null
    var operations: private final List<Any>? = null
    var scale: private float? = null
    var srcBitmap: private final Bitmap? = null
    final  MainActivity this$0

    fun AnnotationOverlayView(this: MainActivity, ctx: Context, srcBitmap: Bitmap): public {
        super(ctx)
        Intrinsics.checkNotNullParameter(ctx, "ctx")
        Intrinsics.checkNotNullParameter(srcBitmap, "srcBitmap")
        this.this$0 = this$0
        this.srcBitmap = srcBitmap
        this.operations = ArrayList()
        this.currentTool = "brush"
        this.scale = 1.0f
        }

    fun getSrcBitmap(): Bitmap {
        return this.srcBitmap
        }

    fun getCurrentTool(): String {
        return this.currentTool
        }

    fun setCurrentTool(str: String): Unit {
        Intrinsics.checkNotNullParameter(str, "<set-?>")
        this.currentTool = str
        }

    override
    fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int): Unit {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && this.srcBitmap.getWidth() > 0) {
            this.scale = Math.min(w / this.srcBitmap.getWidth(), h / this.srcBitmap.getHeight())
            this.offsetX = (w - (this.srcBitmap.getWidth() * this.scale)) / 2.0f
            this.offsetY = (h - (this.srcBitmap.getHeight() * this.scale)) / 2.0f
            val smallW: Int = RangesKt.coerceAtLeast(this.srcBitmap.getWidth() / 15, 1)
            val smallH: Int = RangesKt.coerceAtLeast(this.srcBitmap.getHeight() / 15, 1)
            val small: Bitmap = Bitmap.createScaledBitmap(this.srcBitmap, smallW, smallH, false)
            Intrinsics.checkNotNullExpressionValue(small, "createScaledBitmap(...)")
            this.mosaicBmp = Bitmap.createScaledBitmap(small, this.srcBitmap.getWidth(), this.srcBitmap.getHeight(), false)
            }
        }

    override
    fun onDraw(canvas: Canvas): Unit {
        Intrinsics.checkNotNullParameter(canvas, "canvas")
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(this.offsetX, this.offsetY)
        canvas.scale(this.scale, this.scale)
        canvas.drawBitmap(this.srcBitmap, 0.0f, 0.0f, (Paint) null)
        for (Object op : this.operations) {
            drawOp(canvas, op)
            }
        val it: Any = this.currentOp
        if (it != null) {
            drawOp(canvas, it)
            }
        canvas.restore()
        }

    fun drawOp(canvas: Canvas, op: Any): Unit {
        var mb: Bitmap? = null
        val annotationOverlayView: AnnotationOverlayView = this
        if (op is BrushOp) {
            val p: Paint = new Paint(1)
            p.setColor(((BrushOp) op).getColor())
            p.setStyle(Paint.Style.STROKE)
            p.setStrokeWidth(((BrushOp) op).getWidth())
            p.setStrokeCap(Paint.Cap.ROUND)
            p.setStrokeJoin(Paint.Join.ROUND)
            canvas.drawPath(((BrushOp) op).getPath(), p)
            return
            }
        if (op is ArrowOp) {
            val p2: Paint = new Paint(1)
            p2.setColor(((ArrowOp) op).getColor())
            p2.setStyle(Paint.Style.STROKE)
            p2.setStrokeWidth(((ArrowOp) op).getWidth())
            p2.setStrokeCap(Paint.Cap.ROUND)
            canvas.drawLine(((ArrowOp) op).getSx(), ((ArrowOp) op).getSy(), ((ArrowOp) op).getEx(), ((ArrowOp) op).getEy(), p2)
            val angle: Double = Math.atan2(((ArrowOp) op).getEy() - ((ArrowOp) op).getSy(), ((ArrowOp) op).getEx() - ((ArrowOp) op).getSx())
            canvas.drawLine(((ArrowOp) op).getEx(), ((ArrowOp) op).getEy(), (((ArrowOp) op).getEx() - (40.0f * Math.cos(angle - 0.5235987755982988d))), (((ArrowOp) op).getEy() - (40.0f * Math.sin(angle - 0.5235987755982988d))), p2)
            canvas.drawLine(((ArrowOp) op).getEx(), ((ArrowOp) op).getEy(), (((ArrowOp) op).getEx() - (40.0f * Math.cos(angle + 0.5235987755982988d))), (((ArrowOp) op).getEy() - (40.0f * Math.sin(0.5235987755982988d + angle))), p2)
            return
            }
        if (op is RectOp) {
            val p3: Paint = new Paint(1)
            p3.setColor(((RectOp) op).getColor())
            p3.setStyle(Paint.Style.STROKE)
            p3.setStrokeWidth(((RectOp) op).getWidth())
            canvas.drawRect(((RectOp) op).getRect(), p3)
            return
            }
        if (op is TextOp) {
            val p4: Paint = new Paint(1)
            p4.setColor(((TextOp) op).getColor())
            p4.setTextSize(((TextOp) op).getSize())
            p4.setTypeface(Typeface.DEFAULT_BOLD)
            canvas.drawText(((TextOp) op).getText(), ((TextOp) op).getX(), ((TextOp) op).getY(), p4)
            return
            }
        if ((op is MosaicOp) && (mb = annotationOverlayView.mosaicBmp) != null) {
            val i: Int = 0
            val radius: Float = 25.0f
            for (Pair<Float, Float> pair : ((MosaicOp) op).getPoints()) {
                val x: Float = pair.component1().floatValue()
                val y: Float = pair.component2().floatValue()
                val srcLeft: Int = RangesKt.coerceAtLeast((int) (x - radius), 0)
                val srcTop: Int = RangesKt.coerceAtLeast((int) (y - radius), 0)
                val srcRight: Int = RangesKt.coerceAtMost((int) (x + radius), annotationOverlayView.srcBitmap.getWidth())
                val srcBottom: Int = RangesKt.coerceAtMost((int) (y + radius), annotationOverlayView.srcBitmap.getHeight())
                if (srcRight <= srcLeft || srcBottom <= srcTop) {
                    annotationOverlayView = this
                    } else {
                    val srcRect: Rect = new Rect(srcLeft, srcTop, srcRight, srcBottom)
                    val radius2: Float = radius
                    val radius3: Float = srcBottom
                    val dstRect: RectF = new RectF(srcLeft, srcTop, srcRight, radius3)
                    canvas.drawBitmap(mb, srcRect, dstRect, (Paint) null)
                    annotationOverlayView = this
                    i = i
                    radius = radius2
                    }
                }
            }
        }

    fun toBitmapX(viewX: Float): Float {
        return (viewX - this.offsetX) / this.scale
        }

    fun toBitmapY(viewY: Float): Float {
        return (viewY - this.offsetY) / this.scale
        }

    fun undo(): Unit {
        if (!this.operations.isEmpty()) {
            this.operations.remove(CollectionsKt.getLastIndex(this.operations))
            invalidate()
            }
        }

    fun hasOperations(): Boolean {
        return !this.operations.isEmpty()
        }

    fun exportBitmap(): Bitmap {
        val result: Bitmap = Bitmap.createBitmap(this.srcBitmap.getWidth(), this.srcBitmap.getHeight(), Bitmap.Config.ARGB_8888)
        Intrinsics.checkNotNullExpressionValue(result, "createBitmap(...)")
        val canvas: Canvas = new Canvas(result)
        canvas.drawBitmap(this.srcBitmap, 0.0f, 0.0f, (Paint) null)
        for (Object op : this.operations) {
            drawOp(canvas, op)
            }
        var result: return? = null
        }

        /* JADX WARN: Code restructure failed: missing block: B:96:0x0227, code lost:

    var true: return? = null
         */
    override
        /*
    Code decompiled incorrectly, please refer to instructions dump.
        */
    fun onTouchEvent(event: MotionEvent): Boolean {
        var points: List<Pair<Float, Float>>? = null
        var points2: List<Pair<Float, Float>>? = null
        var rect: RectF? = null
        var path: Path? = null
        var path2: Path? = null
        Intrinsics.checkNotNullParameter(event, "event")
        val bitmapX: Float = toBitmapX(event.getX())
        val bitmapY: Float = toBitmapY(event.getY())
        val f: Float = 6.0f / this.scale
        val str: String = this.currentTool
        switch (str.hashCode()) {
            case -1068356470:
            if (str.equals("mosaic")) {
                switch (event.getAction()) {
                    case 0:
                    this.currentOp = MosaicOp(CollectionsKt.mutableListOf(TuplesKt.to(Float.valueOf(bitmapX), Float.valueOf(bitmapY))))
                    break
                    case 1:
                    val obj: Any = this.currentOp
                    val mosaicOp: MosaicOp = obj instanceof MosaicOp ? (MosaicOp) obj : null
                    if (mosaicOp != null && (points = mosaicOp.getPoints()) != null) {
                        points.add(TuplesKt.to(Float.valueOf(bitmapX), Float.valueOf(bitmapY)))
                        }
                    val obj2: Any = this.currentOp
                    if (obj2 != null) {
                        this.operations.add(obj2)
                        }
                    this.currentOp = null
                    invalidate()
                    break
                    case 2:
                    val obj3: Any = this.currentOp
                    val mosaicOp2: MosaicOp = obj3 instanceof MosaicOp ? (MosaicOp) obj3 : null
                    if (mosaicOp2 != null && (points2 = mosaicOp2.getPoints()) != null) {
                        points2.add(TuplesKt.to(Float.valueOf(bitmapX), Float.valueOf(bitmapY)))
                        }
                    invalidate()
                    break
                    }
                }
            break
            case 3496420:
            if (str.equals("rect")) {
                switch (event.getAction()) {
                    case 0:
                    this.currentOp = RectOp(RectF(bitmapX, bitmapY, bitmapX, bitmapY), SupportMenu.CATEGORY_MASK, f)
                    break
                    case 1:
                    val obj4: Any = this.currentOp
                    if (obj4 != null) {
                        this.operations.add(obj4)
                        }
                    this.currentOp = null
                    invalidate()
                    break
                    case 2:
                    val obj5: Any = this.currentOp
                    val rectOp: RectOp = obj5 instanceof RectOp ? (RectOp) obj5 : null
                    if (rectOp != null && (rect = rectOp.getRect()) != null) {
                        rect.left = Math.min(rect.left, bitmapX)
                        rect.top = Math.min(rect.top, bitmapY)
                        rect.right = Math.max(rect.right, bitmapX)
                        rect.bottom = Math.max(rect.bottom, bitmapY)
                        }
                    invalidate()
                    break
                    }
                }
            break
            case 3556653:
            if (str.equals(TextNotificationReceiver.EXTRA_TEXT) && event.getAction() == 0) {
                val editText: EditText = new EditText(this.this$0)
                new AlertDialog.Builder(this.this$0).setTitle("输入文字").setView(editText).setPositiveButton("确定", new DialogInterface.OnClickListener() { // from class: com.phonehub.MainActivity$AnnotationOverlayView$$ExternalSyntheticLambda0
                    override
                    fun onClick(dialogInterface: DialogInterface, i: Int): Unit {
                        MainActivity.AnnotationOverlayView.onTouchEvent$lambda$10(editText, this, bitmapX, bitmapY, dialogInterface, i)
                        }
                    }).setNegativeButton("取消", (DialogInterface.OnClickListener) null).show()
                break
                }
            break
            case 93090825:
            if (str.equals("arrow")) {
                switch (event.getAction()) {
                    case 0:
                    this.currentOp = ArrowOp(bitmapX, bitmapY, bitmapX, bitmapY, SupportMenu.CATEGORY_MASK, f)
                    break
                    case 1:
                    val obj6: Any = this.currentOp
                    val arrowOp: ArrowOp = obj6 instanceof ArrowOp ? (ArrowOp) obj6 : null
                    if (arrowOp != null) {
                        arrowOp.setEx(bitmapX)
                        arrowOp.setEy(bitmapY)
                        }
                    val obj7: Any = this.currentOp
                    if (obj7 != null) {
                        this.operations.add(obj7)
                        }
                    this.currentOp = null
                    invalidate()
                    break
                    case 2:
                    val obj8: Any = this.currentOp
                    val arrowOp2: ArrowOp = obj8 instanceof ArrowOp ? (ArrowOp) obj8 : null
                    if (arrowOp2 != null) {
                        val arrowOp3: ArrowOp = arrowOp2
                        arrowOp3.setEx(bitmapX)
                        arrowOp3.setEy(bitmapY)
                        }
                    invalidate()
                    break
                    }
                }
            break
            case 94017338:
            if (str.equals("brush")) {
                switch (event.getAction()) {
                    case 0:
                    val path3: Path = new Path()
                    path3.moveTo(bitmapX, bitmapY)
                    this.currentOp = BrushOp(path3, SupportMenu.CATEGORY_MASK, f)
                    break
                    case 1:
                    val obj9: Any = this.currentOp
                    val brushOp: BrushOp = obj9 instanceof BrushOp ? (BrushOp) obj9 : null
                    if (brushOp != null && (path = brushOp.getPath()) != null) {
                        path.lineTo(bitmapX, bitmapY)
                        }
                    val obj10: Any = this.currentOp
                    if (obj10 != null) {
                        this.operations.add(obj10)
                        }
                    this.currentOp = null
                    invalidate()
                    break
                    case 2:
                    val obj11: Any = this.currentOp
                    val brushOp2: BrushOp = obj11 instanceof BrushOp ? (BrushOp) obj11 : null
                    if (brushOp2 != null && (path2 = brushOp2.getPath()) != null) {
                        path2.lineTo(bitmapX, bitmapY)
                        }
                    invalidate()
                    break
                    }
                }
            break
            }
        }

    public static final Unit onTouchEvent$lambda$10(EditText $input, AnnotationOverlayView this$0, float $bx, float $by, DialogInterface dialogInterface, int i) {
        val text: String = $input.getText().toString()
        if (text.length() > 0) {
            this$0.operations.add(TextOp(text, $bx, $by, SupportMenu.CATEGORY_MASK, 40.0f))
            this$0.invalidate()
            }
        }
    }

fun showScreenshotViewer(file: File): Unit {
    val v: View = LayoutInflater.from(this).inflate(R.layout.dialog_screenshot_viewer, (ViewGroup) null)
    val findViewById: View = v.findViewById(R.id.btnAnnotate)
    Intrinsics.checkNotNullExpressionValue(findViewById, "findViewById(...)")
    NativeButtonKt.applyDarkTheme$default((Button) findViewById, 0, 0, false, 7, null)
    val findViewById2: View = v.findViewById(R.id.btnUndo)
    Intrinsics.checkNotNullExpressionValue(findViewById2, "findViewById(...)")
    NativeButtonKt.applyDarkTheme$default((Button) findViewById2, 0, 0, false, 7, null)
    val findViewById3: View = v.findViewById(R.id.btnSavePng)
    Intrinsics.checkNotNullExpressionValue(findViewById3, "findViewById(...)")
    NativeButtonKt.applyDarkTheme$default((Button) findViewById3, 0, 0, true, 3, null)
    val findViewById4: View = v.findViewById(R.id.btnSendToPc)
    Intrinsics.checkNotNullExpressionValue(findViewById4, "findViewById(...)")
    NativeButtonKt.applyDarkTheme$default((Button) findViewById4, 0, 0, false, 7, null)
    val findViewById5: View = v.findViewById(R.id.btnClose)
    Intrinsics.checkNotNullExpressionValue(findViewById5, "findViewById(...)")
    NativeButtonKt.applyDarkTheme$default((Button) findViewById5, 0, 0, false, 7, null)
    val findViewById6: View = v.findViewById(R.id.toolBrush)
    Intrinsics.checkNotNullExpressionValue(findViewById6, "findViewById(...)")
    NativeButtonKt.applyDarkTheme$default((Button) findViewById6, 0, 0, false, 7, null)
    val findViewById7: View = v.findViewById(R.id.toolText)
    Intrinsics.checkNotNullExpressionValue(findViewById7, "findViewById(...)")
    NativeButtonKt.applyDarkTheme$default((Button) findViewById7, 0, 0, false, 7, null)
    val findViewById8: View = v.findViewById(R.id.toolArrow)
    Intrinsics.checkNotNullExpressionValue(findViewById8, "findViewById(...)")
    NativeButtonKt.applyDarkTheme$default((Button) findViewById8, 0, 0, false, 7, null)
    val findViewById9: View = v.findViewById(R.id.toolRect)
    Intrinsics.checkNotNullExpressionValue(findViewById9, "findViewById(...)")
    NativeButtonKt.applyDarkTheme$default((Button) findViewById9, 0, 0, false, 7, null)
    val findViewById10: View = v.findViewById(R.id.toolMosaic)
    Intrinsics.checkNotNullExpressionValue(findViewById10, "findViewById(...)")
    NativeButtonKt.applyDarkTheme$default((Button) findViewById10, 0, 0, false, 7, null)
    val img: ImageView = (ImageView) v.findViewById(R.id.screenshotImage)
    val originalBmp: Bitmap = BitmapFactory.decodeFile(file.getAbsolutePath())
    if (originalBmp != null) {
        img.setImageBitmap(originalBmp)
        }
    val overlayPlaceholder: View = v.findViewById(R.id.annotationOverlay)
    val toolbar: LinearLayout = (LinearLayout) v.findViewById(R.id.annotationToolbar)
    val parent: ViewParent = overlayPlaceholder.getParent()
    Intrinsics.checkNotNull(parent, "null cannot be cast to non-null type android.widget.FrameLayout")
    val frameParent: FrameLayout = (FrameLayout) parent
    final Ref.ObjectRef annotationView = new Ref.ObjectRef()
    final Ref.BooleanRef annotationMode = new Ref.BooleanRef()
    val toolButtons: Map = MapsKt.mapOf(TuplesKt.to("brush", v.findViewById(R.id.toolBrush)), TuplesKt.to(TextNotificationReceiver.EXTRA_TEXT, v.findViewById(R.id.toolText)), TuplesKt.to("arrow", v.findViewById(R.id.toolArrow)), TuplesKt.to("rect", v.findViewById(R.id.toolRect)), TuplesKt.to("mosaic", v.findViewById(R.id.toolMosaic)))
    ((Button) v.findViewById(R.id.btnAnnotate)).setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda3
        override
        fun onClick(view: View): Unit {
            MainActivity.showScreenshotViewer$lambda$111(Ref.BooleanRef.this, toolbar, annotationView, originalBmp, frameParent, overlayPlaceholder, this, v, toolButtons, view)
            }
        })
    for (Map.Entry element$iv : toolButtons.entrySet()) {
        val tool: String = (String) element$iv.getKey()
        val btn: Button = (Button) element$iv.getValue()
        btn.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda4
            override
            fun onClick(view: View): Unit {
                MainActivity.showScreenshotViewer$selectTool(toolButtons, annotationView, tool)
                }
            })
        }
    ((Button) v.findViewById(R.id.btnUndo)).setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda5
        override
        fun onClick(view: View): Unit {
            MainActivity.showScreenshotViewer$lambda$114(Ref.ObjectRef.this, this, view)
            }
        })
    ((Button) v.findViewById(R.id.btnSavePng)).setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda6
        override
        fun onClick(view: View): Unit {
            MainActivity.showScreenshotViewer$lambda$115(Ref.ObjectRef.this, originalBmp, file, this, view)
            }
        })
    ((Button) v.findViewById(R.id.btnSendToPc)).setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda7
        override
        fun onClick(view: View): Unit {
            MainActivity.showScreenshotViewer$lambda$116(Ref.ObjectRef.this, this, file, view)
            }
        })
    final androidx.appcompat.app.AlertDialog dialog = new AlertDialog.Builder(this).setView(v).setCancelable(true).create()
    Intrinsics.checkNotNullExpressionValue(dialog, "create(...)")
    val window: Window = dialog.getWindow()
    if (window != null) {
        window.setBackgroundDrawableResource(android.R.color.transparent)
        }
    dialog.show()
    ((Button) v.findViewById(R.id.btnClose)).setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda8
        override
        fun onClick(view: View): Unit {
            androidx.appcompat.app.AlertDialog.this.dismiss()
            }
        })
    }

public static final Unit showScreenshotViewer$selectTool(Map<String, ? extends Button> map, Ref.ObjectRef<AnnotationOverlayView> objectRef, String tool) {
    for (Map.Entry element$iv : map.entrySet()) {
        val name: String = element$iv.getKey()
        val btn: Button = element$iv.getValue()
        if (Intrinsics.areEqual(name, tool)) {
            btn.setBackgroundColor(-12947515)
            } else {
            btn.setBackgroundResource(android.R.drawable.btn_default)
            }
        }
    val annotationOverlayView: AnnotationOverlayView = objectRef.element
    if (annotationOverlayView != null) {
        annotationOverlayView.setCurrentTool(tool)
        }
    }

private static final Unit showScreenshotViewer$setAnnotationMode(Ref.BooleanRef annotationMode, LinearLayout toolbar, Ref.ObjectRef<AnnotationOverlayView> objectRef, Bitmap originalBmp, FrameLayout frameParent, View overlayPlaceholder, MainActivity this$0, View v, Map<String, ? extends Button> map, boolean on) {
    annotationMode.element = on
    toolbar.setVisibility(on ? 0 : 8)
    if (on && objectRef.element == null && originalBmp != null) {
        frameParent.removeView(overlayPlaceholder)
        val annotationOverlayView: ?? = new AnnotationOverlayView(this$0, this$0, originalBmp)
        annotationOverlayView.setVisibility(0)
        annotationOverlayView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1))
        objectRef.element = annotationOverlayView
        frameParent.addView(objectRef.element)
        showScreenshotViewer$selectTool(map, objectRef, "brush")
        }
    val annotationOverlayView2: AnnotationOverlayView = objectRef.element
    if (annotationOverlayView2 != null) {
        annotationOverlayView2.setVisibility(on ? 0 : 8)
        }
    ((Button) v.findViewById(R.id.btnAnnotate)).setText(on ? "退出批注" : "批注")
    }

public static final Unit showScreenshotViewer$lambda$111(Ref.BooleanRef $annotationMode, LinearLayout $toolbar, Ref.ObjectRef $annotationView, Bitmap $originalBmp, FrameLayout $frameParent, View $overlayPlaceholder, MainActivity this$0, View $v, Map $toolButtons, View it) {
    showScreenshotViewer$setAnnotationMode($annotationMode, $toolbar, $annotationView, $originalBmp, $frameParent, $overlayPlaceholder, this$0, $v, $toolButtons, !$annotationMode.element)
    }

    /* JADX WARN: Code restructure failed: missing block: B:7:0x0015, code lost:

if (r0.hasOperations() == true) goto L11
     */
    /*
Code decompiled incorrectly, please refer to instructions dump.
    */
public static final Unit showScreenshotViewer$lambda$114(Ref.ObjectRef $annotationView, MainActivity this$0, View it) {
    var z: Boolean? = null
    val annotationOverlayView: AnnotationOverlayView = (AnnotationOverlayView) $annotationView.element
    if (annotationOverlayView != null) {
        annotationOverlayView.undo()
        }
    val annotationOverlayView2: AnnotationOverlayView = (AnnotationOverlayView) $annotationView.element
    if (annotationOverlayView2 != null) {
        z = true
        }
    z = false
    if (!z) {
        Toast.makeText(this$0, "无可撤销操作", 0).show()
        }
    }

    /*
Code decompiled incorrectly, please refer to instructions dump.
    */
public static final Unit showScreenshotViewer$lambda$115(Ref.ObjectRef $annotationView, Bitmap $originalBmp, File $file, MainActivity this$0, View it) {
    var bmpToSave: Bitmap? = null
    if ($annotationView.element != 0) {
        val t: T = $annotationView.element
        Intrinsics.checkNotNull(t)
        if (((AnnotationOverlayView) t).hasOperations()) {
            val t2: T = $annotationView.element
            Intrinsics.checkNotNull(t2)
            bmpToSave = ((AnnotationOverlayView) t2).exportBitmap()
            if (bmpToSave == null) {
                try {
                    val annotationOverlayView: AnnotationOverlayView = (AnnotationOverlayView) $annotationView.element
                    val saveName: String = annotationOverlayView != null && annotationOverlayView.hasOperations() ? FilesKt.getNameWithoutExtension($file) + "_annotated.png" : $file.getName()
                    val saveDir: File = new File(this$0.getExternalFilesDir(null), "Received")
                    val saveFile: File = new File(saveDir, saveName)
                    val fos: FileOutputStream = new FileOutputStream(saveFile)
                    bmpToSave.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    fos.close()
                    Toast.makeText(this$0, "已保存: " + saveFile.getAbsolutePath(), 1).show()
                    return
                    } catch (Exception e) {
                    Toast.makeText(this$0, "保存失败: " + e.getMessage(), 0).show()
                    return
                    }
                }
            return
            }
        }
    bmpToSave = $originalBmp
    if (bmpToSave == null) {
        }
    }

public static final Unit showScreenshotViewer$lambda$116(Ref.ObjectRef $annotationView, MainActivity this$0, File $file, View it) {
    var tmpFile: File? = null
    if ($annotationView.element != 0) {
        val t: T = $annotationView.element
        Intrinsics.checkNotNull(t)
        if (((AnnotationOverlayView) t).hasOperations()) {
            try {
                tmpFile = File(this$0.getExternalFilesDir(null), "annotated_tmp.png")
                val fos: FileOutputStream = new FileOutputStream(tmpFile)
                val t2: T = $annotationView.element
                Intrinsics.checkNotNull(t2)
                ((AnnotationOverlayView) t2).exportBitmap().compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.close()
                } catch (Exception e) {
                tmpFile = $file
                }
            ConnectionManager.INSTANCE.sendFile(tmpFile)
            Toast.makeText(this$0, "已开始回传", 0).show()
            }
        }
    tmpFile = $file
    ConnectionManager.INSTANCE.sendFile(tmpFile)
    Toast.makeText(this$0, "已开始回传", 0).show()
    }

fun getMirrorView(): View {
    val v: View = LayoutInflater.from(this).inflate(R.layout.page_screen_mirror, (ViewGroup) null)
    val btnMirrorToggle: Button = (Button) v.findViewById(R.id.btnMirrorToggle)
    if (btnMirrorToggle != null) {
        NativeButtonKt.applyDarkTheme$default(btnMirrorToggle, 0, 0, true, 3, null)
        }
    val button: Button = (Button) v.findViewById(R.id.btnAudio)
    if (button != null) {
        NativeButtonKt.applyDarkTheme$default(button, 0, 0, false, 7, null)
        }
    val fullscreenBtn: Button = (Button) v.findViewById(R.id.btnFullscreen)
    if (fullscreenBtn != null) {
        NativeButtonKt.applyDarkTheme$default(fullscreenBtn, 0, 0, false, 7, null)
        }
    val status: TextView = (TextView) v.findViewById(R.id.mirrorStatus)
    val mirrorFrame: FrameLayout = (FrameLayout) v.findViewById(R.id.mirrorFrame)
    final Ref.BooleanRef mirrorRunning = new Ref.BooleanRef()
    if (btnMirrorToggle != null) {
        btnMirrorToggle.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda83
            override
            fun onClick(view: View): Unit {
                MainActivity.getMirrorView$lambda$118(Ref.BooleanRef.this, status, this, btnMirrorToggle, view)
                }
            })
        }
    val btnAudio: Button = (Button) v.findViewById(R.id.btnAudio)
    final Ref.BooleanRef audioRunning = new Ref.BooleanRef()
    if (btnAudio != null) {
        btnAudio.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda85
            override
            fun onClick(view: View): Unit {
                MainActivity.getMirrorView$lambda$119(Ref.BooleanRef.this, this, btnAudio, view)
                }
            })
        }
    val titleText: TextView = (TextView) v.findViewById(R.id.mirrorTitle)
    val topButtons: LinearLayout = (LinearLayout) v.findViewById(R.id.topButtons)
    val btnStopPcStream: Button = (Button) v.findViewById(R.id.btnStopPcStream)
    if (btnStopPcStream != null) {
        NativeButtonKt.applyDarkTheme$default(btnStopPcStream, 0, 0, false, 7, null)
        }
    val allUiComponents: List = CollectionsKt.listOf((Object[]) new View[]{titleText, topButtons, status, fullscreenBtn, btnStopPcStream})
    if (fullscreenBtn != null) {
        fullscreenBtn.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda86
            override
            fun onClick(view: View): Unit {
                MainActivity.getMirrorView$lambda$120(status, mirrorFrame, this, allUiComponents, view)
                }
            })
        }
    val fullscreenExitBtn: Button = (Button) v.findViewById(R.id.btnMirrorFullscreenExit)
    if (fullscreenExitBtn != null) {
        fullscreenExitBtn.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda87
            override
            fun onClick(view: View): Unit {
                MainActivity.this.exitMirrorFullscreen()
                }
            })
        }
    val button2: Button = (Button) v.findViewById(R.id.btnStopPcStream)
    if (button2 != null) {
        button2.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda88
            override
            fun onClick(view: View): Unit {
                MainActivity.getMirrorView$lambda$122(MainActivity.this, mirrorFrame, status, view)
                }
            })
        }
    final ImageView $this$getMirrorView_u24lambda_u24123 = ImageView(this)
    $this$getMirrorView_u24lambda_u24123.setScaleType(ImageView.ScaleType.FIT_CENTER)
    $this$getMirrorView_u24lambda_u24123.setBackgroundColor(ViewCompat.MEASURED_STATE_MASK)
    $this$getMirrorView_u24lambda_u24123.setMinimumHeight(400)
    this.mirrorImageView = $this$getMirrorView_u24lambda_u24123
    final Ref.LongRef touchStartTime = new Ref.LongRef()
    final Ref.BooleanRef longPressDone = new Ref.BooleanRef()
    final Ref.LongRef lastMoveSendTime = new Ref.LongRef()
    val moveThrottleMs: Long = 16
    $this$getMirrorView_u24lambda_u24123.setOnTouchListener(new View.OnTouchListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda89
        override
        fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
            boolean mirrorView$lambda$124
            mirrorView$lambda$124 = MainActivity.getMirrorView$lambda$124($this$getMirrorView_u24lambda_u24123, touchStartTime, longPressDone, lastMoveSendTime, moveThrottleMs, view, motionEvent)
            return mirrorView$lambda$124
            }
        })
    val parent: ViewParent = status.getParent()
    val parent2: ViewGroup = parent instanceof ViewGroup ? (ViewGroup) parent : null
    val statusIndex: Int = parent2 != null ? parent2.indexOfChild(status) : -1
    if (parent2 != null && statusIndex >= 0) {
        parent2.addView($this$getMirrorView_u24lambda_u24123, statusIndex + 1)
        }
    Intrinsics.checkNotNull(v)
    var v: return? = null
    }

public static final Unit getMirrorView$lambda$118(Ref.BooleanRef $mirrorRunning, TextView $status, MainActivity this$0, Button $btnMirrorToggle, View it) {
    if (!$mirrorRunning.element) {
        $status.setText("正在推流（手机画面推送到电脑）...")
        ConnectionManager.INSTANCE.sendMediaCommand("mirror_start")
        this$0.startPhoneScreenCapture()
        $btnMirrorToggle.setText("停止推流")
        $mirrorRunning.element = true
        return
        }
    $status.setText("已停止")
    ConnectionManager.INSTANCE.sendMediaCommand("mirror_stop")
    ConnectionManager.INSTANCE.sendMediaCommand("pc_stream_stop")
    this$0.stopPhoneScreenCapture()
    ConnectionManager.INSTANCE.stopPcFramePolling()
    val imageView: ImageView = this$0.mirrorImageView
    if (imageView != null) {
        imageView.setImageBitmap(null)
        }
    $btnMirrorToggle.setText("启动推流")
    $mirrorRunning.element = false
    }

public static final Unit getMirrorView$lambda$119(Ref.BooleanRef $audioRunning, MainActivity this$0, Button $btnAudio, View it) {
    if (!$audioRunning.element) {
        ConnectionManager.INSTANCE.sendMediaCommand("audio_start")
        this$0.startPhoneAudioCapture()
        $btnAudio.setText("停止声音")
        $audioRunning.element = true
        return
        }
    this$0.stopPhoneAudioCapture()
    ConnectionManager.INSTANCE.sendMediaCommand("audio_stop")
    $btnAudio.setText("声音传输")
    $audioRunning.element = false
    }

public static final Unit getMirrorView$lambda$120(TextView $status, FrameLayout $mirrorFrame, MainActivity this$0, List $allUiComponents, View it) {
    ConnectionManager.INSTANCE.sendMediaCommand("pc_stream_start")
    ConnectionManager.INSTANCE.startPcFramePolling(true)
    $status.setText("正在查看电脑画面...")
    $mirrorFrame.setVisibility(0)
    this$0.enterMirrorFullscreen($mirrorFrame, $allUiComponents)
    }

public static final Unit getMirrorView$lambda$122(MainActivity this$0, FrameLayout $mirrorFrame, TextView $status, View it) {
    ConnectionManager.INSTANCE.sendMediaCommand("pc_stream_stop")
    ConnectionManager.INSTANCE.stopPcFramePolling()
    val imageView: ImageView = this$0.mirrorImageView
    if (imageView != null) {
        imageView.setImageBitmap(null)
        }
    $mirrorFrame.setVisibility(8)
    $status.setText("已停止接收电脑画面")
    }

public static final boolean getMirrorView$lambda$124(ImageView $frameImg, Ref.LongRef $touchStartTime, Ref.BooleanRef $longPressDone, Ref.LongRef $lastMoveSendTime, long $moveThrottleMs, View view, MotionEvent event) {
    if (!ConnectionManager.INSTANCE.getPcFrameControlMode()) {
        var false: return? = null
        }
    val drawable: Drawable = $frameImg.getDrawable()
    val normX: Float = 0.5f
    val normY: Float = 0.5f
    if (drawable != null && drawable.getIntrinsicWidth() > 0 && drawable.getIntrinsicHeight() > 0) {
        val imgW: Float = drawable.getIntrinsicWidth()
        val imgH: Float = drawable.getIntrinsicHeight()
        val viewW: Float = $frameImg.getWidth()
        val viewH: Float = $frameImg.getHeight()
        val scale: Float = Math.min(viewW / imgW, viewH / imgH)
        val realImgW: Float = imgW * scale
        val realImgH: Float = imgH * scale
        val offsetX: Float = (viewW - realImgW) / 2.0f
        val offsetY: Float = (viewH - realImgH) / 2.0f
        normX = RangesKt.coerceIn((event.getX() - offsetX) / realImgW, 0.0f, 1.0f)
        normY = RangesKt.coerceIn((event.getY() - offsetY) / realImgH, 0.0f, 1.0f)
        } else if ($frameImg.getWidth() > 0 && $frameImg.getHeight() > 0) {
        normX = RangesKt.coerceIn(event.getX() / $frameImg.getWidth(), 0.0f, 1.0f)
        normY = RangesKt.coerceIn(event.getY() / $frameImg.getHeight(), 0.0f, 1.0f)
        }
    switch (event.getAction()) {
        case 0:
        $touchStartTime.element = System.currentTimeMillis()
        $longPressDone.element = false
        ConnectionManager.INSTANCE.sendAction("screen_click", MapsKt.mapOf(TuplesKt.to("x", Float.valueOf(normX)), TuplesKt.to("y", Float.valueOf(normY)), TuplesKt.to("op", "down")))
        var true: return? = null
        case 1:
        val normX2: Float = normX
        if (!$longPressDone.element) {
            ConnectionManager.INSTANCE.sendAction("screen_click", MapsKt.mapOf(TuplesKt.to("x", Float.valueOf(normX2)), TuplesKt.to("y", Float.valueOf(normY)), TuplesKt.to("op", "up")))
            }
        var true: return? = null
        case 2:
        if (!$longPressDone.element) {
            val now: Long = System.currentTimeMillis()
            val normX3: Float = normX
            if (now - $touchStartTime.element > 2000) {
                $longPressDone.element = true
                ConnectionManager.INSTANCE.sendAction("screen_click", MapsKt.mapOf(TuplesKt.to("x", Float.valueOf(normX3)), TuplesKt.to("y", Float.valueOf(normY)), TuplesKt.to("op", "up")))
                ConnectionManager.INSTANCE.sendAction("screen_click", MapsKt.mapOf(TuplesKt.to("x", Float.valueOf(normX3)), TuplesKt.to("y", Float.valueOf(normY)), TuplesKt.to("op", "right")))
                } else if (now - $lastMoveSendTime.element >= $moveThrottleMs) {
                $lastMoveSendTime.element = now
                ConnectionManager.INSTANCE.sendAction("screen_click", MapsKt.mapOf(TuplesKt.to("x", Float.valueOf(normX3)), TuplesKt.to("y", Float.valueOf(normY)), TuplesKt.to("op", "move")))
                }
            }
        var true: return? = null
        default:
        var true: return? = null
        }
    }

fun getCameraView(): View {
    val v: View = LayoutInflater.from(this).inflate(R.layout.page_camera, (ViewGroup) null)
    val btnStart: Button = (Button) v.findViewById(R.id.btnCameraStart)
    val btnSwitch: Button = (Button) v.findViewById(R.id.btnCameraSwitch)
    val btnPcCam: Button = (Button) v.findViewById(R.id.btnCameraStop)
    if (btnStart != null) {
        NativeButtonKt.applyDarkTheme$default(btnStart, 0, 0, false, 7, null)
        }
    if (btnSwitch != null) {
        NativeButtonKt.applyDarkTheme$default(btnSwitch, 0, 0, false, 7, null)
        }
    if (btnPcCam != null) {
        NativeButtonKt.applyDarkTheme$default(btnPcCam, 0, 0, false, 7, null)
        }
    val status: TextView = (TextView) v.findViewById(R.id.cameraStatus)
    val previewView: PreviewView = (PreviewView) v.findViewById(R.id.cameraPreview)
    this.cameraPreviewView = previewView
    final Ref.BooleanRef isStreaming = new Ref.BooleanRef()
    if (btnStart != null) {
        btnStart.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda9
            override
            fun onClick(view: View): Unit {
                MainActivity.getCameraView$lambda$125(Ref.BooleanRef.this, status, this, previewView, btnStart, view)
                }
            })
        }
    if (btnSwitch != null) {
        btnSwitch.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda10
            override
            fun onClick(view: View): Unit {
                MainActivity.getCameraView$doSwitchCamera(MainActivity.this, isStreaming)
                }
            })
        }
    if (btnPcCam != null) {
        btnPcCam.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda12
            override
            fun onClick(view: View): Unit {
                MainActivity.getCameraView$lambda$127(btnPcCam, this, status, isStreaming, view)
                }
            })
        }
    val pcCameraImg: ImageView = new ImageView(this)
    pcCameraImg.setScaleType(ImageView.ScaleType.FIT_CENTER)
    pcCameraImg.setBackgroundColor(ViewCompat.MEASURED_STATE_MASK)
    pcCameraImg.setMinimumHeight(400)
    pcCameraImg.setVisibility(8)
    this.cameraImageView = pcCameraImg
    val parent: ViewParent = status.getParent()
    val parent2: ViewGroup = parent instanceof ViewGroup ? (ViewGroup) parent : null
    val statusIndex: Int = parent2 != null ? parent2.indexOfChild(status) : -1
    if (parent2 != null && statusIndex >= 0) {
        parent2.addView(pcCameraImg, statusIndex + 1)
        }
    Intrinsics.checkNotNull(v)
    var v: return? = null
    }

public static final Unit getCameraView$lambda$125(Ref.BooleanRef $isStreaming, TextView $status, MainActivity this$0, PreviewView $previewView, Button $btnStart, View it) {
    if (!$isStreaming.element) {
        try {
            $status.setText("正在启动摄像头...")
            Intrinsics.checkNotNull($previewView)
            this$0.startCameraPreview(1920, 1080, $previewView)
            ConnectionManager.INSTANCE.sendMediaCommand("mirror_start")
            $status.setText("推流中（本地预览 + 推送给电脑）")
            $btnStart.setText("停止推流")
            $isStreaming.element = true
            return
            } catch (Exception e) {
            Toast.makeText(this$0, "无法启动摄像头: " + e.getMessage(), 1).show()
            $status.setText("启动失败")
            return
            }
        }
    this$0.stopCameraPreview()
    ConnectionManager.INSTANCE.sendMediaCommand("mirror_stop")
    $status.setText("已停止")
    $btnStart.setText("启动推流")
    $isStreaming.element = false
    }

public static final Unit getCameraView$doSwitchCamera(MainActivity this$0, Ref.BooleanRef isStreaming) {
    this$0.performCameraSwitch(isStreaming.element)
    }

public static final Unit getCameraView$lambda$127(Button $btnPcCam, MainActivity this$0, TextView $status, Ref.BooleanRef $isStreaming, View it) {
    if (ConnectionManager.INSTANCE.isPcCameraPolling()) {
        ConnectionManager.INSTANCE.stopPcCameraPolling()
        ConnectionManager.INSTANCE.sendMediaCommand("pc_camera_stop")
        $btnPcCam.setText("查看电脑摄像头")
        val imageView: ImageView = this$0.cameraImageView
        if (imageView != null) {
            imageView.setImageBitmap(null)
            }
        val imageView2: ImageView = this$0.cameraImageView
        if (imageView2 != null) {
            imageView2.setVisibility(8)
            }
        $status.setText($isStreaming.element ? "推流中（本地预览 + 推送给电脑）" : "已停止")
        return
        }
    ConnectionManager.INSTANCE.sendMediaCommand("pc_camera_start")
    ConnectionManager.INSTANCE.startPcCameraPolling()
    $btnPcCam.setText("停止拉取")
    val imageView3: ImageView = this$0.cameraImageView
    if (imageView3 != null) {
        imageView3.setVisibility(0)
        }
    $status.setText("正在拉取电脑摄像头...")
    }

fun startCameraPreview(width: Int, height: Int, previewView: PreviewView): Unit {
    if (this.cameraPreviewRunning) {
        stopCameraPreview()
        }
    val futureProvider: ListenableFuture = ProcessCameraProvider.getInstance(this)
    Intrinsics.checkNotNullExpressionValue(futureProvider, "getInstance(...)")
    futureProvider.addListener(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda58
        override
        fun run(): Unit {
            MainActivity.startCameraPreview$lambda$129(MainActivity.this, futureProvider, width, height, previewView)
            }
        }, ContextCompat.getMainExecutor(this))
    }

public static final Unit startCameraPreview$lambda$129(MainActivity this$0, ListenableFuture $futureProvider, int $width, int $height, PreviewView $previewView) {
    this$0.cameraProvider = (ProcessCameraProvider) $futureProvider.get()
    val resolutionSelector: ResolutionSelector = new ResolutionSelector.Builder().setResolutionStrategy(new ResolutionStrategy(new Size($width, $height), 1)).build()
    Intrinsics.checkNotNullExpressionValue(resolutionSelector, "build(...)")
    val preview: Preview = new Preview.Builder().setResolutionSelector(resolutionSelector).build()
    Intrinsics.checkNotNullExpressionValue(preview, "build(...)")
    val imageAnalyzer: ImageAnalysis = new ImageAnalysis.Builder().setResolutionSelector(resolutionSelector).setOutputImageFormat(2).setBackpressureStrategy(0).build()
    Intrinsics.checkNotNullExpressionValue(imageAnalyzer, "build(...)")
    this$0.cameraExecutor = Executors.newSingleThreadExecutor()
    val executorService: ExecutorService = this$0.cameraExecutor
    Intrinsics.checkNotNull(executorService)
    imageAnalyzer.setAnalyzer(executorService, new MainActivity$startCameraPreview$1$1(this$0))
    val cameraSelector: CameraSelector = new CameraSelector.Builder().requireLensFacing(this$0.cameraLensFacing).build()
    Intrinsics.checkNotNullExpressionValue(cameraSelector, "build(...)")
    preview.setSurfaceProvider($previewView.getSurfaceProvider())
    val processCameraProvider: ProcessCameraProvider = this$0.cameraProvider
    this$0.cameraInstance = processCameraProvider != null ? processCameraProvider.bindToLifecycle(this$0, cameraSelector, preview, imageAnalyzer) : null
    this$0.cameraPreviewRunning = true
    }

fun stopCameraPreview(): Unit {
    this.cameraPreviewRunning = false
    val processCameraProvider: ProcessCameraProvider = this.cameraProvider
    if (processCameraProvider != null) {
        processCameraProvider.unbindAll()
        }
    this.cameraProvider = null
    this.cameraInstance = null
    val executorService: ExecutorService = this.cameraExecutor
    if (executorService != null) {
        executorService.shutdown()
        }
    this.cameraExecutor = null
    this.frameTimeoutHandler.removeCallbacksAndMessages(null)
    this.mirrorFrameTimeoutRunnable = null
    this.cameraFrameTimeoutRunnable = null
    }

fun switchCameraLens(): Unit {
    this.cameraLensFacing = this.cameraLensFacing == 1 ? 0 : 1
    }

fun performCameraSwitch(isStreaming: Boolean): Unit {
    switchCameraLens()
    if (this.cameraPreviewRunning) {
        stopCameraPreview()
        val it: PreviewView = this.cameraPreviewView
        if (it != null) {
            startCameraPreview(1920, 1080, it)
            }
        }
    val facing: String = this.cameraLensFacing == 1 ? "back" : "front"
    ConnectionManager.INSTANCE.sendCameraSwitch(facing)
    Toast.makeText(this, "Switched to " + facing + " camera", 0).show()
    }

fun notifKey(/* ConnectionManager.NotificationItem item */): String {
    val key: String = item.getKey()
    if (key.length() == 0) {
        key = item.getPackageName() + "|" + item.getSbnTag() + "|" + item.getSbnId()
        }
    var key: return? = null
    }

fun showNotificationBlacklistDialog(): Unit {
    val pm: PackageManager = getPackageManager()
    val installedApplications: Iterable = pm.getInstalledApplications(0)
    Intrinsics.checkNotNullExpressionValue(installedApplications, "getInstalledApplications(...)")
    Iterable $this$filter$iv = installedApplications
    Collection destination$iv$iv = ArrayList()
    for (Object element$iv$iv : $this$filter$iv) {
        val it: ApplicationInfo = (ApplicationInfo) element$iv$iv
        if (true ^ Intrinsics.areEqual(it.packageName, "com.phonehub")) {
            destination$iv$iv.add(element$iv$iv)
            }
        }
    Iterable $this$sortedBy$iv = CollectionsKt.sortedWith((List) destination$iv$iv, Comparator() { // from class: com.phonehub.MainActivity$showNotificationBlacklistDialog$$inlined$sortedBy$1
        override
        fun compare(t: T, t2: T): Int {
            val it2: ApplicationInfo = (ApplicationInfo) t
            val lowerCase: String = it2.loadLabel(pm).toString().toLowerCase(Locale.ROOT)
            Intrinsics.checkNotNullExpressionValue(lowerCase, "toLowerCase(...)")
            val it3: ApplicationInfo = (ApplicationInfo) t2
            val lowerCase2: String = it3.loadLabel(pm).toString().toLowerCase(Locale.ROOT)
            Intrinsics.checkNotNullExpressionValue(lowerCase2, "toLowerCase(...)")
            return ComparisonsKt.compareValues(lowerCase, lowerCase2)
            }
        })
    val blacklist: Set = CollectionsKt.toMutableSet(NotificationListener.INSTANCE.getBlacklist(this))
    Iterable $this$map$iv = $this$sortedBy$iv
    Collection destination$iv$iv2 = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv, 10))
    for (Object item$iv$iv : $this$map$iv) {
        val it2: ApplicationInfo = (ApplicationInfo) item$iv$iv
        destination$iv$iv2.add(it2.loadLabel(pm).toString())
        }
    val appNames: Collection = (List) destination$iv$iv2
    Iterable $this$map$iv2 = $this$sortedBy$iv
    Collection destination$iv$iv3 = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv2, 10))
    for (Object item$iv$iv2 : $this$map$iv2) {
        val it3: ApplicationInfo = (ApplicationInfo) item$iv$iv2
        destination$iv$iv3.add(it3.packageName)
        }
    val pkgNames: List = (List) destination$iv$iv3
    List $this$map$iv3 = pkgNames
    Collection destination$iv$iv4 = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv3, 10))
    for (Object item$iv$iv3 : $this$map$iv3) {
        val it4: String = (String) item$iv$iv3
        destination$iv$iv4.add(Boolean.valueOf(blacklist.contains(it4)))
        }
    val checked: Array<Boolean> = CollectionsKt.toBooleanArray((List) destination$iv$iv4)
    Collection $this$toTypedArray$iv = appNames
    final android.app.AlertDialog dialog = new AlertDialog.Builder(this).setTitle("选择不转发的应用").setMultiChoiceItems((CharSequence[]) $this$toTypedArray$iv.toArray(new String[0]), checked, new DialogInterface.OnMultiChoiceClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda32
        override
        fun onClick(dialogInterface: DialogInterface, i: Int, z: Boolean): Unit {
            MainActivity.showNotificationBlacklistDialog$lambda$137(blacklist, pkgNames, dialogInterface, i, z)
            }
        }).setPositiveButton("保存", new DialogInterface.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda33
        override
        fun onClick(dialogInterface: DialogInterface, i: Int): Unit {
            MainActivity.showNotificationBlacklistDialog$lambda$138(MainActivity.this, blacklist, dialogInterface, i)
            }
        }).setNegativeButton("取消", (DialogInterface.OnClickListener) null).create()
    dialog.show()
    val window: Window = dialog.getWindow()
    if (window != null) {
        window.setBackgroundDrawable(ColorDrawable(-13816531))
        }
    val listView: ListView = dialog.getListView()
    if (listView != null) {
        listView.setBackgroundColor(-13816531)
        }
    val listView2: ListView = dialog.getListView()
    if (listView2 != null) {
        listView2.setDivider(ColorDrawable(-12566464))
        }
    val listView3: ListView = dialog.getListView()
    if (listView3 != null) {
        listView3.setDividerHeight(1)
        }
    val count: Int = dialog.getListView().getCount()
    for (int i = 0; i < count; i++) {
        val view: View = dialog.getListView().getChildAt(i)
        if (view != null && (view is CheckedTextView)) {
            ((CheckedTextView) view).setTextColor(-1)
            }
        }
    dialog.getListView().post(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda34
        override
        fun run(): Unit {
            MainActivity.showNotificationBlacklistDialog$lambda$139(dialog)
            }
        })
    }

public static final Unit showNotificationBlacklistDialog$lambda$137(Set $blacklist, List $pkgNames, DialogInterface dialogInterface, int which, boolean isChecked) {
    val obj: Any = $pkgNames.get(which)
    if (isChecked) {
        Intrinsics.checkNotNullExpressionValue(obj, "get(...)")
        $blacklist.add(obj)
        } else {
        $blacklist.remove(obj)
        }
    }

public static final Unit showNotificationBlacklistDialog$lambda$138(MainActivity this$0, Set $blacklist, DialogInterface dialogInterface, int i) {
    NotificationListener.INSTANCE.setBlacklist(this$0, $blacklist)
    Toast.makeText(this$0, "黑名单已更新", 0).show()
    }

public static final Unit showNotificationBlacklistDialog$lambda$139(android.app.AlertDialog $dialog) {
    val count: Int = $dialog.getListView().getCount()
    for (int i = 0; i < count; i++) {
        val view: View = $dialog.getListView().getChildAt(i)
        if (view is CheckedTextView) {
            ((CheckedTextView) view).setTextColor(-1)
            }
        }
    }

fun getNotificationsView(): View {
    Ref.ObjectRef currentTab
    var btnHistoryTab: Button? = null
    var btnRefresh: Button? = null
    var btnPermission: Button? = null
    var v: View? = null
    var btnPermission2: Button? = null
    var btnPermission3: Button? = null
    var btnPermission4: Button? = null
    val v2: View = LayoutInflater.from(this).inflate(R.layout.page_notifications, (ViewGroup) null)
    val button: Button = (Button) v2.findViewById(R.id.btnNotifWhitelist)
    if (button != null) {
        NativeButtonKt.applyDarkTheme$default(button, 0, 0, false, 7, null)
        }
    val button2: Button = (Button) v2.findViewById(R.id.btnNotifPermission)
    if (button2 != null) {
        NativeButtonKt.applyDarkTheme$default(button2, 0, 0, false, 7, null)
        }
    val button3: Button = (Button) v2.findViewById(R.id.btnNotifRefresh)
    if (button3 != null) {
        NativeButtonKt.applyDarkTheme$default(button3, 0, 0, false, 7, null)
        }
    val button4: Button = (Button) v2.findViewById(R.id.btnNotifActiveTab)
    if (button4 != null) {
        NativeButtonKt.applyDarkTheme$default(button4, 0, 0, false, 7, null)
        }
    val button5: Button = (Button) v2.findViewById(R.id.btnNotifHistoryTab)
    if (button5 != null) {
        NativeButtonKt.applyDarkTheme$default(button5, 0, 0, false, 7, null)
        }
    val activeList: ListView = (ListView) v2.findViewById(R.id.notifActiveList)
    val historyList: ListView = (ListView) v2.findViewById(R.id.notifHistoryList)
    val empty: TextView = (TextView) v2.findViewById(R.id.notifEmpty)
    val filterEdit: EditText = (EditText) v2.findViewById(R.id.notifFilter)
    val permissionBar: LinearLayout = (LinearLayout) v2.findViewById(R.id.notifPermissionBar)
    val permissionText: TextView = (TextView) v2.findViewById(R.id.notifPermissionText)
    val btnPermission5: Button = (Button) v2.findViewById(R.id.btnNotifPermission)
    val btnRefresh2: Button = (Button) v2.findViewById(R.id.btnNotifRefresh)
    val btnActiveTab: Button = (Button) v2.findViewById(R.id.btnNotifActiveTab)
    val btnHistoryTab2: Button = (Button) v2.findViewById(R.id.btnNotifHistoryTab)
    final Ref.ObjectRef currentTab2 = new Ref.ObjectRef()
    currentTab2.element = "active"
    val button6: Button = (Button) v2.findViewById(R.id.btnNotifWhitelist)
    if (button6 != null) {
        button6.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda13
            override
            fun onClick(view: View): Unit {
                MainActivity.this.showNotificationBlacklistDialog()
                }
            })
        }
    if (btnActiveTab != null) {
        v = v2
        currentTab = currentTab2
        btnHistoryTab = btnHistoryTab2
        btnPermission = btnPermission5
        btnPermission2 = btnActiveTab
        btnRefresh = btnRefresh2
        btnPermission2.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda14
            override
            fun onClick(view: View): Unit {
                MainActivity.getNotificationsView$lambda$144(Ref.ObjectRef.this, activeList, historyList, btnActiveTab, btnHistoryTab2, filterEdit, this, empty, view)
                }
            })
        } else {
        currentTab = currentTab2
        btnHistoryTab = btnHistoryTab2
        btnRefresh = btnRefresh2
        btnPermission = btnPermission5
        v = v2
        btnPermission2 = btnActiveTab
        }
    if (btnHistoryTab != null) {
        final Ref.ObjectRef objectRef = currentTab
        val button7: Button = btnHistoryTab
        val button8: Button = btnPermission2
        btnHistoryTab.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda15
            override
            fun onClick(view: View): Unit {
                MainActivity.getNotificationsView$lambda$145(Ref.ObjectRef.this, activeList, historyList, button7, button8, filterEdit, this, empty, view)
                }
            })
        }
    btnPermission2.callOnClick()
    if (btnPermission != null) {
        btnPermission3 = btnPermission
        btnPermission3.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda16
            override
            fun onClick(view: View): Unit {
                MainActivity.getNotificationsView$lambda$146(MainActivity.this, view)
                }
            })
        } else {
        btnPermission3 = btnPermission
        }
    val btnRefresh3: Button = btnRefresh
    if (btnRefresh3 != null) {
        val button9: Button = btnPermission3
        final Ref.ObjectRef objectRef2 = currentTab
        btnPermission4 = btnPermission3
        btnRefresh3.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda17
            override
            fun onClick(view: View): Unit {
                MainActivity.getNotificationsView$lambda$147(MainActivity.this, permissionBar, permissionText, button9, filterEdit, objectRef2, activeList, historyList, empty, view)
                }
            })
        } else {
        btnPermission4 = btnPermission3
        }
    if (filterEdit != null) {
        final Ref.ObjectRef objectRef3 = currentTab
        filterEdit.addTextChangedListener(TextWatcher() { // from class: com.phonehub.MainActivity$getNotificationsView$6
            override
            fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int): Unit {
                }

            override
            fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int): Unit {
                MainActivity.getNotificationsView$refresh$143(filterEdit, objectRef3, this, activeList, historyList, empty)
                }

            override
            fun afterTextChanged(s: Editable): Unit {
                }
            })
        }
    val button10: Button = btnPermission4
    final Ref.ObjectRef objectRef4 = currentTab
    activeList.post(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda18
        override
        fun run(): Unit {
            MainActivity.getNotificationsView$lambda$148(permissionBar, permissionText, button10, filterEdit, objectRef4, this, activeList, historyList, empty)
            }
        })
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$getNotificationsView$8(this, filterEdit, currentTab, activeList, historyList, empty, null), 3, null)
    Intrinsics.checkNotNull(v)
    var v: return? = null
    }

private static final Unit getNotificationsView$checkPermissionAndTrigger(LinearLayout permissionBar, TextView permissionText, Button btnPermission) {
    val enabled: Boolean = ConnectionManager.INSTANCE.isNotificationListenerEnabled()
    if (enabled) {
        if (NotificationListener.INSTANCE.getInstance() == null) {
            permissionBar.setVisibility(0)
            permissionText.setText("权限已开启但服务未连接，请关闭再重新开启「PhoneHub」开关")
            btnPermission.setText("去重新开启")
            return
            }
        permissionBar.setVisibility(8)
        return
        }
    permissionBar.setVisibility(0)
    permissionText.setText("通知监听权限未开启，无法获取通知")
    btnPermission.setText("去开启")
    }

    /* JADX WARN: Code restructure failed: missing block: B:10:0x002b, code lost:

if (r4 == null) goto L12
     */
    /*
Code decompiled incorrectly, please refer to instructions dump.
    */
public static final Unit getNotificationsView$refresh$143(EditText filterEdit, Ref.ObjectRef<String> objectRef, MainActivity this$0, ListView activeList, ListView historyList, TextView empty) {
    var q: String? = null
    var filtered: List? = null
    var z: Boolean? = null
    var text: Editable? = null
    var obj: String? = null
    var obj2: String? = null
    if (filterEdit != null && (text = filterEdit.getText()) != null && (obj = text.toString()) != null && (obj2 = StringsKt.trim((CharSequence) obj).toString()) != null) {
        q = obj2.toLowerCase(Locale.ROOT)
        Intrinsics.checkNotNullExpressionValue(q, "toLowerCase(...)")
        }
    q = ""
    val sourceList: List = Intrinsics.areEqual(objectRef.element, "active") ? this$0.activeNotifItems : this$0.notifHistoryItems
    if (q.length() == 0) {
        filtered = sourceList
        } else {
        Iterable $this$filter$iv = sourceList
        int $i$f$filter = 0
        Collection destination$iv$iv = ArrayList()
        Iterable $this$filterTo$iv$iv = $this$filter$iv
        for (Object element$iv$iv : $this$filterTo$iv$iv) {
            ConnectionManager.NotificationItem it = (ConnectionManager.NotificationItem) element$iv$iv
            val lowerCase: String = it.getPackageName().toLowerCase(Locale.ROOT)
            Intrinsics.checkNotNullExpressionValue(lowerCase, "toLowerCase(...)")
            Iterable $this$filter$iv2 = $this$filter$iv
            int $i$f$filter2 = $i$f$filter
            Iterable $this$filterTo$iv$iv2 = $this$filterTo$iv$iv
            if (!StringsKt.contains$default((CharSequence) lowerCase, (CharSequence) q, false, 2, (Object) null)) {
                val lowerCase2: String = it.getTitle().toLowerCase(Locale.ROOT)
                Intrinsics.checkNotNullExpressionValue(lowerCase2, "toLowerCase(...)")
                if (!StringsKt.contains$default((CharSequence) lowerCase2, (CharSequence) q, false, 2, (Object) null)) {
                    val lowerCase3: String = it.getText().toLowerCase(Locale.ROOT)
                    Intrinsics.checkNotNullExpressionValue(lowerCase3, "toLowerCase(...)")
                    if (!StringsKt.contains$default((CharSequence) lowerCase3, (CharSequence) q, false, 2, (Object) null)) {
                        z = false
                        if (!z) {
                            destination$iv$iv.add(element$iv$iv)
                            }
                        $this$filter$iv = $this$filter$iv2
                        $i$f$filter = $i$f$filter2
                        $this$filterTo$iv$iv = $this$filterTo$iv$iv2
                        }
                    }
                }
            z = true
            if (!z) {
                }
            $this$filter$iv = $this$filter$iv2
            $i$f$filter = $i$f$filter2
            $this$filterTo$iv$iv = $this$filterTo$iv$iv2
            }
        filtered = (List) destination$iv$iv
        }
    val targetList: ListView = Intrinsics.areEqual(objectRef.element, "active") ? activeList : historyList
    if (filtered.isEmpty()) {
        empty.setVisibility(0)
        empty.setText(sourceList.isEmpty() ? "暂无通知" : "无匹配结果")
        targetList.setAdapter((ListAdapter) null)
        return
        }
    empty.setVisibility(8)
    Iterable $this$map$iv = filtered
    int $i$f$map = 0
    Collection destination$iv$iv2 = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv, 10))
    for (Object item$iv$iv : $this$map$iv) {
        ConnectionManager.NotificationItem it2 = (ConnectionManager.NotificationItem) item$iv$iv
        destination$iv$iv2.add(it2.getTitle() + "\n" + it2.getText() + "\n[" + it2.getPackageName() + "] " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it2.getTimestamp())))
        filtered = filtered
        q = q
        sourceList = sourceList
        $this$map$iv = $this$map$iv
        $i$f$map = $i$f$map
        }
    val displays: List = (List) destination$iv$iv2
    targetList.setAdapter((ListAdapter) ArrayAdapter(this$0, android.R.layout.simple_list_item_1, displays))
    }

public static final Unit getNotificationsView$lambda$144(Ref.ObjectRef $currentTab, ListView $activeList, ListView $historyList, Button $btnActiveTab, Button $btnHistoryTab, EditText $filterEdit, MainActivity this$0, TextView $empty, View it) {
    $currentTab.element = "active"
    $activeList.setVisibility(0)
    $historyList.setVisibility(8)
    $btnActiveTab.setTextColor(-1)
    $btnHistoryTab.setTextColor(-7829368)
    getNotificationsView$refresh$143($filterEdit, $currentTab, this$0, $activeList, $historyList, $empty)
    }

public static final Unit getNotificationsView$lambda$145(Ref.ObjectRef $currentTab, ListView $activeList, ListView $historyList, Button $btnHistoryTab, Button $btnActiveTab, EditText $filterEdit, MainActivity this$0, TextView $empty, View it) {
    $currentTab.element = "history"
    $activeList.setVisibility(8)
    $historyList.setVisibility(0)
    $btnHistoryTab.setTextColor(-1)
    $btnActiveTab.setTextColor(-7829368)
    getNotificationsView$refresh$143($filterEdit, $currentTab, this$0, $activeList, $historyList, $empty)
    }

public static final Unit getNotificationsView$lambda$146(MainActivity this$0, View it) {
    try {
        val intent: Intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        intent.addFlags(268435456)
        this$0.startActivity(intent)
        } catch (Exception e) {
        Toast.makeText(this$0, "无法打开设置", 0).show()
        }
    }

public static final Unit getNotificationsView$lambda$147(MainActivity this$0, LinearLayout $permissionBar, TextView $permissionText, Button $btnPermission, EditText $filterEdit, Ref.ObjectRef $currentTab, ListView $activeList, ListView $historyList, TextView $empty, View it) {
    this$0.activeNotifItems.clear()
    getNotificationsView$checkPermissionAndTrigger($permissionBar, $permissionText, $btnPermission)
    val companion: NotificationListener = NotificationListener.INSTANCE.getInstance()
    if (companion != null) {
        companion.reportAllActiveNotifications()
        }
    getNotificationsView$refresh$143($filterEdit, $currentTab, this$0, $activeList, $historyList, $empty)
    val enabled: Boolean = ConnectionManager.INSTANCE.isNotificationListenerEnabled()
    val connected: Boolean = NotificationListener.INSTANCE.getInstance() != null
    if (enabled) {
        if (connected) {
            Toast.makeText(this$0, "已刷新", 0).show()
            return
            } else {
            Toast.makeText(this$0, "服务未连接，请关闭再重新开启权限开关", 1).show()
            return
            }
        }
    Toast.makeText(this$0, "通知权限未开启，请先开启", 0).show()
    }

public static final Unit getNotificationsView$lambda$148(LinearLayout $permissionBar, TextView $permissionText, Button $btnPermission, EditText $filterEdit, Ref.ObjectRef $currentTab, MainActivity this$0, ListView $activeList, ListView $historyList, TextView $empty) {
    getNotificationsView$checkPermissionAndTrigger($permissionBar, $permissionText, $btnPermission)
    getNotificationsView$refresh$143($filterEdit, $currentTab, this$0, $activeList, $historyList, $empty)
    }

fun getFileManagerView(): View {
    var str: String? = null
    val linearLayout: LinearLayout = new LinearLayout(this)
    linearLayout.setOrientation(1)
    linearLayout.setBackgroundColor(-14803426)
    linearLayout.setPadding(dp(16), dp(16), dp(16), dp(16))
    val textView: TextView = new TextView(this)
    textView.setText("远程文件管理")
    textView.setTextColor(-1)
    textView.setTextSize(20.0f)
    textView.setTypeface(textView.getTypeface(), 1)
    textView.setPadding(0, 0, 0, dp(12))
    linearLayout.addView(textView)
    val linearLayout2: LinearLayout = new LinearLayout(this)
    linearLayout2.setOrientation(0)
    linearLayout2.setBackgroundColor(-13816531)
    linearLayout2.setPadding(dp(4), dp(4), dp(4), dp(4))
    val button: Button = new Button(this)
    button.setText("手机文件")
    NativeButtonKt.applyDarkTheme$default(button, 0, 0, true, 3, null)
    val button2: Button = new Button(this)
    button2.setText("电脑文件")
    NativeButtonKt.applyDarkTheme$default(button2, 0, 0, false, 7, null)
    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, dp(44), 1.0f)
    layoutParams.rightMargin = dp(4)
    val unit: Unit = Unit.INSTANCE
    linearLayout2.addView(button, layoutParams)
    linearLayout2.addView(button2, new LinearLayout.LayoutParams(0, dp(44), 1.0f))
    LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, -2)
    layoutParams2.bottomMargin = dp(8)
    val unit2: Unit = Unit.INSTANCE
    linearLayout.addView(linearLayout2, layoutParams2)
    val linearLayout3: LinearLayout = new LinearLayout(this)
    linearLayout3.setOrientation(0)
    linearLayout3.setBackgroundColor(-13816531)
    linearLayout3.setPadding(dp(8), dp(8), dp(8), dp(8))
    val button3: Button = new Button(this)
    button3.setText("上级")
    NativeButtonKt.applyDarkTheme$default(button3, 0, 0, false, 7, null)
    val button4: Button = new Button(this)
    button4.setText("刷新")
    NativeButtonKt.applyDarkTheme$default(button4, 0, 0, true, 3, null)
    val textView2: TextView = new TextView(this)
    textView2.setId(R.id.fmPath)
    val str2: String = "/"
    if (Intrinsics.areEqual(this.fmMode, "phone")) {
        val externalStorageDirectory: File = Environment.getExternalStorageDirectory()
        if (externalStorageDirectory == null || (str = externalStorageDirectory.getAbsolutePath()) == null) {
            str = "/"
            }
        } else {
        str = this.pcCurPath
        }
    textView2.setText(str)
    textView2.setTextColor(-5197648)
    textView2.setTextSize(12.0f)
    textView2.setPadding(dp(8), 0, dp(8), 0)
    textView2.setGravity(16)
    linearLayout3.addView(button3, new LinearLayout.LayoutParams(dp(60), dp(40)))
    linearLayout3.addView(button4, new LinearLayout.LayoutParams(dp(60), dp(40)))
    linearLayout3.addView(textView2, new LinearLayout.LayoutParams(0, dp(40), 1.0f))
    LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-1, -2)
    layoutParams3.bottomMargin = dp(8)
    val unit3: Unit = Unit.INSTANCE
    linearLayout.addView(linearLayout3, layoutParams3)
    val listView: ListView = new ListView(this)
    val textView3: TextView = new TextView(this)
    textView3.setId(R.id.fmEmpty)
    textView3.setText("暂无内容")
    textView3.setTextColor(-10066330)
    textView3.setTextSize(13.0f)
    textView3.setGravity(17)
    textView3.setVisibility(8)
    LinearLayout.LayoutParams layoutParams4 = new LinearLayout.LayoutParams(-1, 0)
    layoutParams4.weight = 1.0f
    val unit4: Unit = Unit.INSTANCE
    linearLayout.addView(listView, layoutParams4)
    linearLayout.addView(textView3)
    final Ref.ObjectRef objectRef = new Ref.ObjectRef()
    val externalStorageDirectory2: File = Environment.getExternalStorageDirectory()
    val t: T = str2
    if (externalStorageDirectory2 != null) {
        val absolutePath: String = externalStorageDirectory2.getAbsolutePath()
        t = str2
        if (absolutePath != null) {
            t = absolutePath
            }
        }
    objectRef.element = t
    button.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda21
        override
        fun onClick(view: View): Unit {
            MainActivity.getFileManagerView$lambda$175(MainActivity.this, button, button2, textView2, objectRef, textView3, listView, view)
            }
        })
    button2.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda22
        override
        fun onClick(view: View): Unit {
            MainActivity.getFileManagerView$lambda$176(MainActivity.this, button2, button, textView2, textView3, listView, view)
            }
        })
    button3.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda23
        override
        fun onClick(view: View): Unit {
            MainActivity.getFileManagerView$lambda$177(MainActivity.this, objectRef, textView2, textView3, listView, view)
            }
        })
    button4.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda24
        override
        fun onClick(view: View): Unit {
            MainActivity.getFileManagerView$lambda$178(MainActivity.this, textView2, objectRef, textView3, listView, view)
            }
        })
    listView.post(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda25
        override
        fun run(): Unit {
            MainActivity.getFileManagerView$refreshPhoneFiles(textView2, objectRef, textView3, listView, this)
            }
        })
    var linearLayout: return? = null
    }

    /* JADX WARN: Code restructure failed: missing block: B:4:0x0033, code lost:

if (r0 == null) goto L6
     */
    /*
Code decompiled incorrectly, please refer to instructions dump.
    */
public static final Unit getFileManagerView$refreshPhoneFiles(final TextView pathTv, final Ref.ObjectRef<String> objectRef, final TextView empty, final ListView list, final MainActivity this$0) {
    var emptyList: List? = null
    Iterable $this$map$iv
    int $i$f$map
    var icon: String? = null
    Iterable $this$mapTo$iv$iv
    int $i$f$mapTo
    var sz: String? = null
    pathTv.setText(objectRef.element)
    val dir: File = new File(objectRef.element)
    val listFiles: Array<File> = dir.listFiles()
    if (listFiles != null) {
        val comparator: Comparator = new Comparator() { // from class: com.phonehub.MainActivity$getFileManagerView$refreshPhoneFiles$$inlined$compareBy$1
        override
        fun compare(t: T, t2: T): Int {
            val it: File = (File) t
            val valueOf: Boolean = Boolean.valueOf(!it.isDirectory())
            val it2: File = (File) t2
            return ComparisonsKt.compareValues(valueOf, Boolean.valueOf(!it2.isDirectory()))
            }
        }
    emptyList = ArraysKt.sortedWith(listFiles, Comparator() { // from class: com.phonehub.MainActivity$getFileManagerView$refreshPhoneFiles$$inlined$thenBy$1
        override
        fun compare(t: T, t2: T): Int {
            val previousCompare: Int = comparator.compare(t, t2)
            if (previousCompare != 0) {
                var previousCompare: return? = null
                }
            val it: File = (File) t
            val name: String = it.getName()
            Intrinsics.checkNotNullExpressionValue(name, "getName(...)")
            val lowerCase: String = name.toLowerCase(Locale.ROOT)
            Intrinsics.checkNotNullExpressionValue(lowerCase, "toLowerCase(...)")
            val it2: File = (File) t2
            val name2: String = it2.getName()
            Intrinsics.checkNotNullExpressionValue(name2, "getName(...)")
            val lowerCase2: String = name2.toLowerCase(Locale.ROOT)
            Intrinsics.checkNotNullExpressionValue(lowerCase2, "toLowerCase(...)")
            return ComparisonsKt.compareValues(lowerCase, lowerCase2)
            }
        })
    }
emptyList = CollectionsKt.emptyList()
val files: List = emptyList
if (files.isEmpty()) {
    empty.setVisibility(0)
    empty.setText("空目录")
    list.setAdapter((ListAdapter) null)
    return
    }
empty.setVisibility(8)
List $this$map$iv2 = files
int $i$f$map2 = 0
Collection destination$iv$iv = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv2, 10))
Iterable $this$mapTo$iv$iv2 = $this$map$iv2
int $i$f$mapTo2 = 0
for (Object item$iv$iv : $this$mapTo$iv$iv2) {
    val f: File = (File) item$iv$iv
    if (f.isDirectory()) {
        $i$f$map = $i$f$map2
        $this$map$iv = $this$map$iv2
        icon = "📁 "
        } else {
        $this$map$iv = $this$map$iv2
        val name: String = f.getName()
        $i$f$map = $i$f$map2
        Intrinsics.checkNotNullExpressionValue(name, "getName(...)")
        icon = this$0.fileIcon(name)
        }
    if (f.isDirectory()) {
        $i$f$mapTo = $i$f$mapTo2
        val iterable: Iterable = $this$mapTo$iv$iv2
        sz = "[目录]"
        $this$mapTo$iv$iv = iterable
        } else {
        $this$mapTo$iv$iv = $this$mapTo$iv$iv2
        $i$f$mapTo = $i$f$mapTo2
        sz = this$0.formatSize(f.length())
        }
    Iterable $this$mapTo$iv$iv3 = $this$mapTo$iv$iv
    destination$iv$iv.add(icon + f.getName() + "\n" + sz)
    $this$map$iv2 = $this$map$iv
    $i$f$map2 = $i$f$map
    $i$f$mapTo2 = $i$f$mapTo
    $this$mapTo$iv$iv2 = $this$mapTo$iv$iv3
    }
val displays: List = (List) destination$iv$iv
list.setAdapter((ListAdapter) ArrayAdapter(this$0, android.R.layout.simple_list_item_1, displays))
list.setOnItemClickListener(new AdapterView.OnItemClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda20
    override
    fun onItemClick(adapterView: AdapterView, view: View, i: Int, j: Long): Unit {
        MainActivity.getFileManagerView$refreshPhoneFiles$lambda$166(files, objectRef, this$0, pathTv, empty, list, adapterView, view, i, j)
        }
    })
}

public static final Unit getFileManagerView$refreshPhoneFiles$lambda$166(List $files, Ref.ObjectRef $phoneCurPath, MainActivity this$0, TextView $pathTv, TextView $empty, ListView $list, AdapterView adapterView, View view, int pos, long j) {
    val f: File = (File) $files.get(pos)
    if (f.isDirectory()) {
        $phoneCurPath.element = f.getAbsolutePath()
        getFileManagerView$refreshPhoneFiles($pathTv, $phoneCurPath, $empty, $list, this$0)
        return
        }
    val connectionManager: ConnectionManager = ConnectionManager.INSTANCE
    Intrinsics.checkNotNull(f)
    connectionManager.sendFile(f)
    Toast.makeText(this$0, "开始发送: " + f.getName(), 0).show()
    }

private static final Unit getFileManagerView$refreshPcFiles(final MainActivity this$0, final TextView pathTv, final TextView empty, final ListView list) {
    this$0.pcInDrives = false
    pathTv.setText(this$0.pcCurPath)
    empty.setVisibility(0)
    empty.setText("正在加载...")
    list.setAdapter((ListAdapter) null)
    ConnectionManager.INSTANCE.fetchPcFiles(this$0.pcCurPath, Function2() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda19
        override
        fun invoke(obj: Any, obj2: Any): Any {
            Unit fileManagerView$refreshPcFiles$lambda$171
            fileManagerView$refreshPcFiles$lambda$171 = MainActivity.getFileManagerView$refreshPcFiles$lambda$171(empty, list, this$0, pathTv, (List) obj, obj2)
            return fileManagerView$refreshPcFiles$lambda$171
            }
        })
    }

public static final Unit getFileManagerView$refreshPcFiles$lambda$171(final TextView $empty, final ListView $list, final MainActivity this$0, final TextView $pathTv, List files, String path) {
    Iterable $this$map$iv
    int $i$f$map
    var sz: String? = null
    Iterable $this$mapTo$iv$iv
    var name: String? = null
    Intrinsics.checkNotNullParameter(files, "files")
    Intrinsics.checkNotNullParameter(path, "path")
    val comparator: Comparator = new Comparator() { // from class: com.phonehub.MainActivity$getFileManagerView$refreshPcFiles$lambda$171$$inlined$compareBy$1
    override
    fun compare(t: T, t2: T): Int {
        ConnectionManager.PcFileInfo it = (ConnectionManager.PcFileInfo) t
        val valueOf: Boolean = Boolean.valueOf(!it.isDir())
        ConnectionManager.PcFileInfo it2 = (ConnectionManager.PcFileInfo) t2
        return ComparisonsKt.compareValues(valueOf, Boolean.valueOf(!it2.isDir()))
        }
    }
val sorted: List = CollectionsKt.sortedWith(files, new Comparator() { // from class: com.phonehub.MainActivity$getFileManagerView$refreshPcFiles$lambda$171$$inlined$thenBy$1
override
fun compare(t: T, t2: T): Int {
    val previousCompare: Int = comparator.compare(t, t2)
    if (previousCompare != 0) {
        var previousCompare: return? = null
        }
    ConnectionManager.PcFileInfo it = (ConnectionManager.PcFileInfo) t
    val lowerCase: String = it.getName().toLowerCase(Locale.ROOT)
    Intrinsics.checkNotNullExpressionValue(lowerCase, "toLowerCase(...)")
    ConnectionManager.PcFileInfo it2 = (ConnectionManager.PcFileInfo) t2
    val lowerCase2: String = it2.getName().toLowerCase(Locale.ROOT)
    Intrinsics.checkNotNullExpressionValue(lowerCase2, "toLowerCase(...)")
    return ComparisonsKt.compareValues(lowerCase, lowerCase2)
    }
})
if (sorted.isEmpty()) {
    $empty.setVisibility(0)
    $empty.setText("空目录")
    $list.setAdapter((ListAdapter) null)
    } else {
    $empty.setVisibility(8)
    List $this$map$iv2 = sorted
    int $i$f$map2 = 0
    Collection destination$iv$iv = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv2, 10))
    Iterable $this$mapTo$iv$iv2 = $this$map$iv2
    for (Object item$iv$iv : $this$mapTo$iv$iv2) {
        ConnectionManager.PcFileInfo f = (ConnectionManager.PcFileInfo) item$iv$iv
        val icon: String = f.isDir() ? "📁 " : this$0.fileIcon(f.getName())
        if (f.isDir()) {
            $i$f$map = $i$f$map2
            $this$map$iv = $this$map$iv2
            sz = "[目录]"
            } else {
            $this$map$iv = $this$map$iv2
            $i$f$map = $i$f$map2
            sz = this$0.formatSize(f.getSize())
            }
        if (f.isDir()) {
            $this$mapTo$iv$iv = $this$mapTo$iv$iv2
            name = f.getName() + "/"
            } else {
            $this$mapTo$iv$iv = $this$mapTo$iv$iv2
            name = f.getName()
            }
        destination$iv$iv.add(icon + name + "\n" + sz)
        $this$map$iv2 = $this$map$iv
        $i$f$map2 = $i$f$map
        $this$mapTo$iv$iv2 = $this$mapTo$iv$iv
        }
    val displays: List = (List) destination$iv$iv
    $list.setAdapter((ListAdapter) ArrayAdapter(this$0, android.R.layout.simple_list_item_1, displays))
    $list.setOnItemClickListener(new AdapterView.OnItemClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda91
        override
        fun onItemClick(adapterView: AdapterView, view: View, i: Int, j: Long): Unit {
            MainActivity.getFileManagerView$refreshPcFiles$lambda$171$lambda$170(sorted, this$0, $pathTv, $empty, $list, adapterView, view, i, j)
            }
        })
    }
return Unit.INSTANCE
}

public static final Unit getFileManagerView$refreshPcFiles$lambda$171$lambda$170(List $sorted, MainActivity this$0, TextView $pathTv, TextView $empty, ListView $list, AdapterView adapterView, View view, int pos, long j) {
    ConnectionManager.PcFileInfo f = (ConnectionManager.PcFileInfo) $sorted.get(pos)
    if (f.isDir()) {
        this$0.pcCurPath = StringsKt.trimEnd(this$0.pcCurPath, AbstractJsonLexerKt.STRING_ESC) + "\\" + f.getName()
        getFileManagerView$refreshPcFiles(this$0, $pathTv, $empty, $list)
        } else {
        val filePath: String = StringsKt.trimEnd(this$0.pcCurPath, AbstractJsonLexerKt.STRING_ESC) + "\\" + f.getName()
        this$0.downloadPcFile(filePath, f.getName())
        }
    }

private static final Unit getFileManagerView$refreshPcDrives(final MainActivity this$0, final TextView pathTv, final TextView empty, final ListView list) {
    this$0.pcInDrives = true
    pathTv.setText("我的电脑")
    empty.setVisibility(0)
    empty.setText("正在加载磁盘列表...")
    list.setAdapter((ListAdapter) null)
    ConnectionManager.INSTANCE.fetchPcDrives(Function1() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda50
        override
        fun invoke(obj: Any): Any {
            Unit fileManagerView$refreshPcDrives$lambda$174
            fileManagerView$refreshPcDrives$lambda$174 = MainActivity.getFileManagerView$refreshPcDrives$lambda$174(empty, list, this$0, pathTv, (List) obj)
            return fileManagerView$refreshPcDrives$lambda$174
            }
        })
    }

public static final Unit getFileManagerView$refreshPcDrives$lambda$174(final TextView $empty, final ListView $list, final MainActivity this$0, final TextView $pathTv, final List drives) {
    Intrinsics.checkNotNullParameter(drives, "drives")
    if (drives.isEmpty()) {
        $empty.setVisibility(0)
        $empty.setText("未获取到磁盘信息")
        $list.setAdapter((ListAdapter) null)
        } else {
        $empty.setVisibility(8)
        List $this$map$iv = drives
        int $i$f$map = 0
        Collection destination$iv$iv = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv, 10))
        Iterable $this$mapTo$iv$iv = $this$map$iv
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            ConnectionManager.PcDriveInfo it = (ConnectionManager.PcDriveInfo) item$iv$iv
            val totalGb: Double = it.getTotal() / 1.073741824E9d
            Iterable $this$map$iv2 = $this$map$iv
            val freeGb: Double = it.getFree() / 1.073741824E9d
            Iterable $this$mapTo$iv$iv2 = $this$mapTo$iv$iv
            val format: String = String.format(it.getName() + "  [磁盘]\n总计: %.1f GB  可用: %.1f GB", Arrays.copyOf(new Object[]{Double.valueOf(totalGb), Double.valueOf(freeGb)}, 2))
            Intrinsics.checkNotNullExpressionValue(format, "format(...)")
            destination$iv$iv.add(format)
            $this$map$iv = $this$map$iv2
            $i$f$map = $i$f$map
            $this$mapTo$iv$iv = $this$mapTo$iv$iv2
            }
        val displays: List = (List) destination$iv$iv
        $list.setAdapter((ListAdapter) ArrayAdapter(this$0, android.R.layout.simple_list_item_1, displays))
        $list.setOnItemClickListener(new AdapterView.OnItemClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda2
            override
            fun onItemClick(adapterView: AdapterView, view: View, i: Int, j: Long): Unit {
                MainActivity.getFileManagerView$refreshPcDrives$lambda$174$lambda$173(MainActivity.this, drives, $pathTv, $empty, $list, adapterView, view, i, j)
                }
            })
        }
    return Unit.INSTANCE
    }

public static final Unit getFileManagerView$refreshPcDrives$lambda$174$lambda$173(MainActivity this$0, List $drives, TextView $pathTv, TextView $empty, ListView $list, AdapterView adapterView, View view, int pos, long j) {
    this$0.pcCurPath = ((ConnectionManager.PcDriveInfo) $drives.get(pos)).getName()
    this$0.pcInDrives = false
    getFileManagerView$refreshPcFiles(this$0, $pathTv, $empty, $list)
    }

public static final Unit getFileManagerView$lambda$175(MainActivity this$0, Button $btnPhone, Button $btnPc, TextView $pathTv, Ref.ObjectRef $phoneCurPath, TextView $empty, ListView $list, View it) {
    this$0.fmMode = "phone"
    NativeButtonKt.applyDarkTheme$default($btnPhone, 0, 0, true, 3, null)
    NativeButtonKt.applyDarkTheme$default($btnPc, 0, 0, false, 7, null)
    getFileManagerView$refreshPhoneFiles($pathTv, $phoneCurPath, $empty, $list, this$0)
    }

public static final Unit getFileManagerView$lambda$176(MainActivity this$0, Button $btnPc, Button $btnPhone, TextView $pathTv, TextView $empty, ListView $list, View it) {
    this$0.fmMode = "pc"
    NativeButtonKt.applyDarkTheme$default($btnPc, 0, 0, true, 3, null)
    NativeButtonKt.applyDarkTheme$default($btnPhone, 0, 0, false, 7, null)
    getFileManagerView$refreshPcDrives(this$0, $pathTv, $empty, $list)
    }

public static final Unit getFileManagerView$lambda$177(MainActivity this$0, Ref.ObjectRef $phoneCurPath, TextView $pathTv, TextView $empty, ListView $list, View it) {
    var parent: String? = null
    var sdRoot: String? = null
    if (Intrinsics.areEqual(this$0.fmMode, "phone")) {
        val cur: File = new File((String) $phoneCurPath.element)
        val externalStorageDirectory: File = Environment.getExternalStorageDirectory()
        if (externalStorageDirectory == null || (sdRoot = externalStorageDirectory.getAbsolutePath()) == null) {
            sdRoot = "/sdcard"
            }
        if (Intrinsics.areEqual(cur.getAbsolutePath(), sdRoot) || Intrinsics.areEqual(cur.getAbsolutePath(), "/")) {
            return
            }
        val parent2: File = cur.getParentFile()
        if (parent2 != null && parent2.canRead()) {
            $phoneCurPath.element = parent2.getAbsolutePath()
            getFileManagerView$refreshPhoneFiles($pathTv, $phoneCurPath, $empty, $list, this$0)
            return
            } else {
            if (parent2 != null) {
                $phoneCurPath.element = parent2.getAbsolutePath()
                getFileManagerView$refreshPhoneFiles($pathTv, $phoneCurPath, $empty, $list, this$0)
                return
                }
            return
            }
        }
    if (this$0.pcInDrives) {
        return
        }
    val trimmed: String = StringsKt.trimEnd(this$0.pcCurPath, AbstractJsonLexerKt.STRING_ESC, '/')
    if (trimmed.length() <= 3) {
        if ((trimmed.length() > 0) && trimmed.charAt(1) == ':') {
            getFileManagerView$refreshPcDrives(this$0, $pathTv, $empty, $list)
            return
            }
        }
    val lastSlash: Int = StringsKt.lastIndexOf$default((CharSequence) trimmed, AbstractJsonLexerKt.STRING_ESC, 0, false, 6, (Object) null)
    if (lastSlash > 2) {
        parent = trimmed.substring(0, lastSlash)
        Intrinsics.checkNotNullExpressionValue(parent, "substring(...)")
        } else if (lastSlash == 2) {
        parent = trimmed.substring(0, 3)
        Intrinsics.checkNotNullExpressionValue(parent, "substring(...)")
        } else {
        parent = null
        }
    if (parent != null) {
        this$0.pcCurPath = parent
        getFileManagerView$refreshPcFiles(this$0, $pathTv, $empty, $list)
        } else {
        getFileManagerView$refreshPcDrives(this$0, $pathTv, $empty, $list)
        }
    }

public static final Unit getFileManagerView$lambda$178(MainActivity this$0, TextView $pathTv, Ref.ObjectRef $phoneCurPath, TextView $empty, ListView $list, View it) {
    if (Intrinsics.areEqual(this$0.fmMode, "phone")) {
        getFileManagerView$refreshPhoneFiles($pathTv, $phoneCurPath, $empty, $list, this$0)
        } else if (this$0.pcInDrives) {
        getFileManagerView$refreshPcDrives(this$0, $pathTv, $empty, $list)
        } else {
        getFileManagerView$refreshPcFiles(this$0, $pathTv, $empty, $list)
        }
    }

    /*
Code decompiled incorrectly, please refer to instructions dump.
    */
fun fileIcon(name: String): String {
    val ext: String = StringsKt.substringAfterLast(name, '.', "").toLowerCase(Locale.ROOT)
    Intrinsics.checkNotNullExpressionValue(ext, "toLowerCase(...)")
    switch (ext.hashCode()) {
        case 1827:
        return !ext.equals("7z") ? "📎 " : "📦 "
        case 3479:
        if (ext.equals("md")) {
            return "📝 "
            }
        break
        case 96323:
        if (ext.equals("aac")) {
            return "🎵 "
            }
        break
        case 96796:
        if (!ext.equals("apk")) {
            }
        break
        case 96980:
        if (ext.equals("avi")) {
            return "🎬 "
            }
        break
        case 97669:
        if (ext.equals("bmp")) {
            return "🖼 "
            }
        break
        case 99640:
        if (ext.equals("doc")) {
            return "📄 "
            }
        break
        case 102340:
        if (!ext.equals("gif")) {
            }
        break
        case 105441:
        if (!ext.equals("jpg")) {
            }
        break
        case 107332:
        if (!ext.equals("log")) {
            }
        break
        case 108184:
        if (!ext.equals("mkv")) {
            }
        break
        case 108272:
        if (!ext.equals("mp3")) {
            }
        break
        case 108273:
        if (!ext.equals("mp4")) {
            }
        break
        case 108308:
        if (!ext.equals("mov")) {
            }
        break
        case 109967:
        if (!ext.equals("ogg")) {
            }
        break
        case 110834:
        if (!ext.equals("pdf")) {
            }
        break
        case 111145:
        if (!ext.equals("png")) {
            }
        break
        case 112675:
        if (!ext.equals("rar")) {
            }
        break
        case 115312:
        if (!ext.equals("txt")) {
            }
        break
        case 117484:
        if (!ext.equals("wav")) {
            }
        break
        case 118783:
        if (!ext.equals("xls")) {
            }
        break
        case 120609:
        if (!ext.equals("zip")) {
            }
        break
        case 3088960:
        if (!ext.equals("docx")) {
            }
        break
        case 3145576:
        if (!ext.equals("flac")) {
            }
        break
        case 3268712:
        if (!ext.equals("jpeg")) {
            }
        break
        case 3645340:
        if (!ext.equals("webp")) {
            }
        break
        case 3682393:
        if (!ext.equals("xlsx")) {
            }
        break
        }
    }

fun formatSize(bytes: Long): String {
    if (bytes >= 1073741824) {
        val format: String = String.format("%.1f GB", Arrays.copyOf(new Object[]{Double.valueOf(bytes / 1.073741824E9d)}, 1))
        Intrinsics.checkNotNullExpressionValue(format, "format(...)")
        var format: return? = null
        }
    if (bytes >= 1048576) {
        val format2: String = String.format("%.1f MB", Arrays.copyOf(new Object[]{Double.valueOf(bytes / 1048576.0d)}, 1))
        Intrinsics.checkNotNullExpressionValue(format2, "format(...)")
        var format2: return? = null
        }
    if (bytes >= RealWebSocket.DEFAULT_MINIMUM_DEFLATE_SIZE) {
        val format3: String = String.format("%d KB", Arrays.copyOf(new Object[]{Long.valueOf(bytes / 1024)}, 1))
        Intrinsics.checkNotNullExpressionValue(format3, "format(...)")
        var format3: return? = null
        }
    return bytes + " B"
    }

fun downloadPcFile(filePath: String, fileName: String): Unit {
    Thread(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda92
        override
        fun run(): Unit {
            MainActivity.downloadPcFile$lambda$187(filePath, this, fileName)
            }
        }).start()
    }

public static final Unit downloadPcFile$lambda$187(String $filePath, final MainActivity this$0, final String $fileName) {
    var errorMsg: String? = null
    var th: Throwable? = null
    var th2: Throwable? = null
    try {
        val baseUrl: String = ConnectionManager.INSTANCE.getBaseUrlPublic()
        val url: URL = new URL(baseUrl + "/api/pc_file_download")
        val openConnection: URLConnection = url.openConnection()
        Intrinsics.checkNotNull(openConnection, "null cannot be cast to non-null type java.net.HttpURLConnection")
        val conn: HttpURLConnection = (HttpURLConnection) openConnection
        conn.setRequestMethod("POST")
        conn.setRequestProperty("Authorization", "Bearer " + ConnectionManager.INSTANCE.getSecretToken())
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setDoOutput(true)
        conn.setConnectTimeout(5000)
        conn.setReadTimeout(60000)
        val jsonBody: String = "{\"path\":\"" + StringsKt.replace$default($filePath, "\\", "\\\\", false, 4, (Object) null) + "\"}"
        val outputStream: OutputStream = conn.getOutputStream()
        val bytes: Array<Byte> = jsonBody.getBytes(Charsets.UTF_8)
        Intrinsics.checkNotNullExpressionValue(bytes, "getBytes(...)")
        outputStream.write(bytes)
        conn.getOutputStream().flush()
        conn.getOutputStream().close()
        if (conn.getResponseCode() == 200) {
            val resolver: ContentResolver = this$0.getContentResolver()
            ContentValues $this$downloadPcFile_u24lambda_u24187_u24lambda_u24180 = ContentValues()
            $this$downloadPcFile_u24lambda_u24187_u24lambda_u24180.put("_display_name", $fileName)
            $this$downloadPcFile_u24lambda_u24187_u24lambda_u24180.put("mime_type", "application/octet-stream")
            $this$downloadPcFile_u24lambda_u24187_u24lambda_u24180.put("relative_path", Environment.DIRECTORY_DOWNLOADS)
            val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, $this$downloadPcFile_u24lambda_u24187_u24lambda_u24180)
            if (uri == null) {
                this$0.runOnUiThread(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda79
                    override
                    fun run(): Unit {
                        MainActivity.downloadPcFile$lambda$187$lambda$184(MainActivity.this)
                        }
                    })
                } else {
                val openOutputStream: OutputStream = resolver.openOutputStream(uri)
                if (openOutputStream != null) {
                    val inputStream: InputStream = openOutputStream
                    try {
                        try {
                            val output: OutputStream = inputStream
                            inputStream = conn.getInputStream()
                            try {
                                val input: InputStream = inputStream
                                Intrinsics.checkNotNull(input)
                                try {
                                    long copyTo$default = ByteStreamsKt.copyTo$default(input, output, 0, 2, null)
                                    CloseableKt.closeFinally(inputStream, null)
                                    Long.valueOf(copyTo$default)
                                    CloseableKt.closeFinally(inputStream, null)
                                    } catch (Throwable th3) {
                                    th2 = th3
                                    try {
                                        var th2: throw? = null
                                        } finally {
                                        }
                                    }
                                } catch (Throwable th4) {
                                th2 = th4
                                }
                            } catch (Throwable th5) {
                            th = th5
                            try {
                                var th2: throw? = null
                                } finally {
                                }
                            }
                        } catch (Throwable th6) {
                        th = th6
                        var th2: throw? = null
                        }
                    }
                this$0.runOnUiThread(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda78
                    override
                    fun run(): Unit {
                        MainActivity.downloadPcFile$lambda$187$lambda$183(MainActivity.this, $fileName)
                        }
                    })
                }
            } else {
            switch (conn.getResponseCode()) {
                case TypedValues.CycleType.TYPE_ALPHA :
                errorMsg = "无权限下载（系统保护文件或文件被锁定）"
                break
                case 404:
                errorMsg = "文件不存在"
                break
                default:
                errorMsg = "HTTP " + conn.getResponseCode()
                break
                }
            this$0.runOnUiThread(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda80
                override
                fun run(): Unit {
                    MainActivity.downloadPcFile$lambda$187$lambda$185(MainActivity.this, errorMsg)
                    }
                })
            }
        conn.disconnect()
        } catch (Exception e) {
        this$0.runOnUiThread(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda81
            override
            fun run(): Unit {
                MainActivity.downloadPcFile$lambda$187$lambda$186(MainActivity.this, e)
                }
            })
        }
    }

public static final Unit downloadPcFile$lambda$187$lambda$183(MainActivity this$0, String $fileName) {
    Toast.makeText(this$0, "已下载到 Download: " + $fileName, 0).show()
    }

public static final Unit downloadPcFile$lambda$187$lambda$184(MainActivity this$0) {
    Toast.makeText(this$0, "下载失败: 无法创建文件", 0).show()
    }

public static final Unit downloadPcFile$lambda$187$lambda$185(MainActivity this$0, String $errorMsg) {
    Toast.makeText(this$0, "下载失败: " + $errorMsg, 0).show()
    }

public static final Unit downloadPcFile$lambda$187$lambda$186(MainActivity this$0, Exception $e) {
    Toast.makeText(this$0, "下载失败: " + $e.getMessage(), 0).show()
    }

fun getApkInstallView(): View {
    var apks: List? = null
    val v: View = LayoutInflater.from(this).inflate(R.layout.page_apk_install, (ViewGroup) null)
    val button: Button = (Button) v.findViewById(R.id.btnApkPick)
    if (button != null) {
        NativeButtonKt.applyDarkTheme$default(button, 0, 0, true, 3, null)
        }
    val button2: Button = (Button) v.findViewById(R.id.btnApkInstallLast)
    if (button2 != null) {
        NativeButtonKt.applyDarkTheme$default(button2, 0, 0, false, 7, null)
        }
    val list: ListView = (ListView) v.findViewById(R.id.apkList)
    val empty: TextView = (TextView) v.findViewById(R.id.apkEmpty)
    val button3: Button = (Button) v.findViewById(R.id.btnApkPick)
    if (button3 != null) {
        button3.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda26
            override
            fun onClick(view: View): Unit {
                MainActivity.getApkInstallView$lambda$188(MainActivity.this, view)
                }
            })
        }
    val button4: Button = (Button) v.findViewById(R.id.btnApkInstallLast)
    if (button4 != null) {
        button4.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda27
            override
            fun onClick(view: View): Unit {
                MainActivity.getApkInstallView$lambda$191(MainActivity.this, view)
                }
            })
        }
    val dir: File = new File(getExternalFilesDir(null), "Received")
    Object[] $this$sortedByDescending$iv = dir.listFiles(FileFilter() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda28
        override
        fun accept(file: File): Boolean {
            boolean apkInstallView$lambda$192
            apkInstallView$lambda$192 = MainActivity.getApkInstallView$lambda$192(file)
            return apkInstallView$lambda$192
            }
        })
    if ($this$sortedByDescending$iv == null || (apks = ArraysKt.sortedWith($this$sortedByDescending$iv, Comparator() { // from class: com.phonehub.MainActivity$getApkInstallView$$inlined$sortedByDescending$1
        override
        fun compare(t: T, t2: T): Int {
            val it: File = (File) t2
            val it2: File = (File) t
            return ComparisonsKt.compareValues(Long.valueOf(it.lastModified()), Long.valueOf(it2.lastModified()))
            }
        })) == null) {
        apks = CollectionsKt.emptyList()
        }
    if (apks.isEmpty()) {
        empty.setVisibility(0)
        list.setAdapter((ListAdapter) null)
        } else {
        empty.setVisibility(8)
        Iterable $this$map$iv = apks
        Collection destination$iv$iv = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv, 10))
        Iterable $this$mapTo$iv$iv = $this$map$iv
        int $i$f$mapTo = 0
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            val it: File = (File) item$iv$iv
            destination$iv$iv.add(it.getName() + "\n" + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it.lastModified())))
            $this$mapTo$iv$iv = $this$mapTo$iv$iv
            $this$map$iv = $this$map$iv
            empty = empty
            dir = dir
            $i$f$mapTo = $i$f$mapTo
            }
        val names: List = (List) destination$iv$iv
        list.setAdapter((ListAdapter) ArrayAdapter(this, android.R.layout.simple_list_item_1, names))
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda30
            override
            fun onItemClick(adapterView: AdapterView, view: View, i: Int, j: Long): Unit {
                MainActivity.getApkInstallView$lambda$195(MainActivity.this, apks, adapterView, view, i, j)
                }
            })
        }
    Intrinsics.checkNotNull(v)
    var v: return? = null
    }

public static final Unit getApkInstallView$lambda$188(MainActivity this$0, View it) {
    val intent: Intent = new Intent("android.intent.action.GET_CONTENT")
    intent.setType("application/vnd.android.package-archive")
    this$0.startActivityForResult(Intent.createChooser(intent, "选择 APK"), this$0.SELECT_APK_CODE)
    }

public static final Unit getApkInstallView$lambda$191(MainActivity this$0, View it) {
    var sortedWith: List? = null
    val apk: File = null
    val dir: File = new File(this$0.getExternalFilesDir(null), "Received")
    Object[] $this$sortedByDescending$iv = dir.listFiles(FileFilter() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda52
        override
        fun accept(file: File): Boolean {
            boolean apkInstallView$lambda$191$lambda$189
            apkInstallView$lambda$191$lambda$189 = MainActivity.getApkInstallView$lambda$191$lambda$189(file)
            return apkInstallView$lambda$191$lambda$189
            }
        })
    if ($this$sortedByDescending$iv != null && (sortedWith = ArraysKt.sortedWith($this$sortedByDescending$iv, Comparator() { // from class: com.phonehub.MainActivity$getApkInstallView$lambda$191$$inlined$sortedByDescending$1
        override
        fun compare(t: T, t2: T): Int {
            val it2: File = (File) t2
            val it3: File = (File) t
            return ComparisonsKt.compareValues(Long.valueOf(it2.lastModified()), Long.valueOf(it3.lastModified()))
            }
        })) != null) {
        apk = (File) CollectionsKt.firstOrNull(sortedWith)
        }
    if (apk != null) {
        this$0.installApk(apk)
        } else {
        Toast.makeText(this$0, "暂无接收的 APK", 0).show()
        }
    }

public static final boolean getApkInstallView$lambda$191$lambda$189(File f) {
    val name: String = f.getName()
    Intrinsics.checkNotNullExpressionValue(name, "getName(...)")
    return StringsKt.endsWith$default(name, ".apk", false, 2, (Object) null)
    }

public static final boolean getApkInstallView$lambda$192(File f) {
    val name: String = f.getName()
    Intrinsics.checkNotNullExpressionValue(name, "getName(...)")
    return StringsKt.endsWith$default(name, ".apk", false, 2, (Object) null)
    }

public static final Unit getApkInstallView$lambda$195(MainActivity this$0, List $apks, AdapterView adapterView, View view, int pos, long j) {
    val obj: Any = $apks.get(pos)
    Intrinsics.checkNotNullExpressionValue(obj, "get(...)")
    this$0.installApk((File) obj)
    }

fun installApk(file: File): Unit {
    try {
        val intent: Intent = new Intent("android.intent.action.VIEW")
        val uri: Uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(1)
        intent.addFlags(268435456)
        startActivity(intent)
        } catch (Exception e) {
        Toast.makeText(this, "无法安装 APK: " + e.getMessage(), 0).show()
        }
    }

fun getAppManagerView(): View {
    val v: View = LayoutInflater.from(this).inflate(R.layout.page_app_manager, (ViewGroup) null)
    val button: Button = (Button) v.findViewById(R.id.btnAppRefresh)
    if (button != null) {
        NativeButtonKt.applyDarkTheme$default(button, 0, 0, true, 3, null)
        }
    val list: ListView = (ListView) v.findViewById(R.id.appList)
    val empty: TextView = (TextView) v.findViewById(R.id.appEmpty)
    val filter: EditText = (EditText) v.findViewById(R.id.appFilter)
    val button2: Button = (Button) v.findViewById(R.id.btnAppRefresh)
    if (button2 != null) {
        button2.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda29
            override
            fun onClick(view: View): Unit {
                MainActivity.getAppManagerView$refresh$200(MainActivity.this, filter, empty, list)
                }
            })
        }
    filter.setOnEditorActionListener(new TextView.OnEditorActionListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda40
        override
        fun onEditorAction(textView: TextView, i: Int, keyEvent: KeyEvent): Boolean {
            boolean appManagerView$lambda$202
            appManagerView$lambda$202 = MainActivity.getAppManagerView$lambda$202(MainActivity.this, filter, empty, list, textView, i, keyEvent)
            return appManagerView$lambda$202
            }
        })
    list.post(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda51
        override
        fun run(): Unit {
            MainActivity.getAppManagerView$refresh$200(MainActivity.this, filter, empty, list)
            }
        })
    Intrinsics.checkNotNull(v)
    var v: return? = null
    }

    /* JADX WARN: Code restructure failed: missing block: B:6:0x003c, code lost:

if (r6 == null) goto L8
     */
    /*
Code decompiled incorrectly, please refer to instructions dump.
    */
public static final Unit getAppManagerView$refresh$200(final MainActivity this$0, EditText filter, TextView empty, ListView list) {
    var q: String? = null
    var infos: List? = null
    var str: String? = null
    Iterable $this$filter$iv
    int $i$f$filter
    Iterable $this$filterTo$iv$iv
    var obj: String? = null
    val pm: PackageManager = this$0.getPackageManager()
    val installedApplications: Iterable = pm.getInstalledApplications(0)
    Intrinsics.checkNotNullExpressionValue(installedApplications, "getInstalledApplications(...)")
    Iterable $this$sortedBy$iv = installedApplications
    val infos2: List = CollectionsKt.sortedWith($this$sortedBy$iv, new Comparator() { // from class: com.phonehub.MainActivity$getAppManagerView$refresh$200$$inlined$sortedBy$1
    override
    fun compare(t: T, t2: T): Int {
        val it: ApplicationInfo = (ApplicationInfo) t
        val lowerCase: String = pm.getApplicationLabel(it).toString().toLowerCase(Locale.ROOT)
        Intrinsics.checkNotNullExpressionValue(lowerCase, "toLowerCase(...)")
        val it2: ApplicationInfo = (ApplicationInfo) t2
        val lowerCase2: String = pm.getApplicationLabel(it2).toString().toLowerCase(Locale.ROOT)
        Intrinsics.checkNotNullExpressionValue(lowerCase2, "toLowerCase(...)")
        return ComparisonsKt.compareValues(lowerCase, lowerCase2)
        }
    })
val text: Editable = filter.getText()
val str2: String = ""
if (text != null && (obj = text.toString()) != null) {
    q = obj.toLowerCase(Locale.ROOT)
    Intrinsics.checkNotNullExpressionValue(q, "toLowerCase(...)")
    }
q = ""
List $this$filter$iv2 = infos2
int $i$f$filter2 = 0
Collection destination$iv$iv = ArrayList()
Iterable $this$filterTo$iv$iv2 = $this$filter$iv2
val it: Iterator = $this$filterTo$iv$iv2.iterator()
while (true) {
    val z: Boolean = true
    if (!it.hasNext()) {
        break
        }
    Object element$iv$iv = it.next()
    val it2: ApplicationInfo = (ApplicationInfo) element$iv$iv
    if (q.length() == 0) {
        infos = infos2
        str = str2
        $this$filter$iv = $this$filter$iv2
        $i$f$filter = $i$f$filter2
        $this$filterTo$iv$iv = $this$filterTo$iv$iv2
        } else {
        infos = infos2
        str = str2
        val lowerCase: String = pm.getApplicationLabel(it2).toString().toLowerCase(Locale.ROOT)
        Intrinsics.checkNotNullExpressionValue(lowerCase, "toLowerCase(...)")
        $this$filter$iv = $this$filter$iv2
        $i$f$filter = $i$f$filter2
        $this$filterTo$iv$iv = $this$filterTo$iv$iv2
        if (!StringsKt.contains$default((CharSequence) lowerCase, (CharSequence) q, false, 2, (Object) null)) {
            val packageName: String = it2.packageName
            Intrinsics.checkNotNullExpressionValue(packageName, "packageName")
            val lowerCase2: String = packageName.toLowerCase(Locale.ROOT)
            Intrinsics.checkNotNullExpressionValue(lowerCase2, "toLowerCase(...)")
            if (!StringsKt.contains$default((CharSequence) lowerCase2, (CharSequence) q, false, 2, (Object) null)) {
                z = false
                }
            }
        }
    if (z) {
        destination$iv$iv.add(element$iv$iv)
        }
    str2 = str
    infos2 = infos
    $this$filter$iv2 = $this$filter$iv
    $i$f$filter2 = $i$f$filter
    $this$filterTo$iv$iv2 = $this$filterTo$iv$iv
    }
val str3: String = str2
val filtered: List = (List) destination$iv$iv
if (filtered.isEmpty()) {
    empty.setVisibility(0)
    list.setAdapter((ListAdapter) null)
    return
    }
empty.setVisibility(8)
List $this$map$iv = filtered
Collection destination$iv$iv2 = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv, 10))
for (Object item$iv$iv : $this$map$iv) {
    val it3: ApplicationInfo = (ApplicationInfo) item$iv$iv
    val label: String = pm.getApplicationLabel(it3).toString()
    val sys: String = (it3.flags & 1) != 0 ? "[系统]" : str3
    destination$iv$iv2.add(label + " " + sys + "\n" + it3.packageName)
    pm = pm
    $this$map$iv = $this$map$iv
    q = q
    }
val displays: List = (List) destination$iv$iv2
list.setAdapter((ListAdapter) ArrayAdapter(this$0, android.R.layout.simple_list_item_1, displays))
list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda115
    override
    fun onItemLongClick(adapterView: AdapterView, view: View, i: Int, j: Long): Boolean {
        boolean appManagerView$refresh$200$lambda$199
        appManagerView$refresh$200$lambda$199 = MainActivity.getAppManagerView$refresh$200$lambda$199(filtered, this$0, adapterView, view, i, j)
        return appManagerView$refresh$200$lambda$199
        }
    })
}

public static final boolean getAppManagerView$refresh$200$lambda$199(List $filtered, MainActivity this$0, AdapterView adapterView, View view, int pos, long j) {
    val pkg: String = ((ApplicationInfo) $filtered.get(pos)).packageName
    val intent: Intent = new Intent("android.intent.action.DELETE", Uri.parse("package:" + pkg))
    this$0.startActivity(intent)
    var true: return? = null
    }

public static final boolean getAppManagerView$lambda$202(MainActivity this$0, EditText $filter, TextView $empty, ListView $list, TextView textView, int i, KeyEvent keyEvent) {
    getAppManagerView$refresh$200(this$0, $filter, $empty, $list)
    var true: return? = null
    }

fun getPowerView(): View {
    val v: View = LayoutInflater.from(this).inflate(R.layout.page_power, (ViewGroup) null)
    val button: Button = (Button) v.findViewById(R.id.btnPowerShutdown)
    if (button != null) {
        NativeButtonKt.applyDarkTheme$default(button, 0, 0, true, 3, null)
        }
    val button2: Button = (Button) v.findViewById(R.id.btnPowerReboot)
    if (button2 != null) {
        NativeButtonKt.applyDarkTheme$default(button2, 0, 0, true, 3, null)
        }
    val button3: Button = (Button) v.findViewById(R.id.btnPowerHibernate)
    if (button3 != null) {
        NativeButtonKt.applyDarkTheme$default(button3, 0, 0, false, 7, null)
        }
    val button4: Button = (Button) v.findViewById(R.id.btnPowerLock)
    if (button4 != null) {
        NativeButtonKt.applyDarkTheme$default(button4, 0, 0, false, 7, null)
        }
    val button5: Button = (Button) v.findViewById(R.id.btnPowerCancel)
    if (button5 != null) {
        NativeButtonKt.applyDarkTheme$default(button5, 0, 0, false, 7, null)
        }
    val button6: Button = (Button) v.findViewById(R.id.btnPowerShutdown)
    if (button6 != null) {
        button6.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda68
            override
            fun onClick(view: View): Unit {
                MainActivity.this.showPowerCountdown("关机", "shutdown")
                }
            })
        }
    val button7: Button = (Button) v.findViewById(R.id.btnPowerReboot)
    if (button7 != null) {
        button7.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda69
            override
            fun onClick(view: View): Unit {
                MainActivity.this.showPowerCountdown("重启", "reboot")
                }
            })
        }
    val button8: Button = (Button) v.findViewById(R.id.btnPowerHibernate)
    if (button8 != null) {
        button8.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda70
            override
            fun onClick(view: View): Unit {
                MainActivity.getPowerView$lambda$206(MainActivity.this, view)
                }
            })
        }
    val button9: Button = (Button) v.findViewById(R.id.btnPowerLock)
    if (button9 != null) {
        button9.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda71
            override
            fun onClick(view: View): Unit {
                MainActivity.getPowerView$lambda$207(MainActivity.this, view)
                }
            })
        }
    val button10: Button = (Button) v.findViewById(R.id.btnPowerCancel)
    if (button10 != null) {
        button10.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda72
            override
            fun onClick(view: View): Unit {
                MainActivity.getPowerView$lambda$208(MainActivity.this, view)
                }
            })
        }
    Intrinsics.checkNotNull(v)
    var v: return? = null
    }

public static final Unit getPowerView$lambda$206(MainActivity this$0, View it) {
    ConnectionManager.INSTANCE.sendPowerCommand("hibernate", 0L)
    Toast.makeText(this$0, "已发送休眠指令", 0).show()
    }

public static final Unit getPowerView$lambda$207(MainActivity this$0, View it) {
    ConnectionManager.INSTANCE.sendPowerCommand("lock", 0L)
    Toast.makeText(this$0, "已发送锁定指令", 0).show()
    }

public static final Unit getPowerView$lambda$208(MainActivity this$0, View it) {
    ConnectionManager.INSTANCE.sendPowerCommand("cancel", 0L)
    Toast.makeText(this$0, "已发送取消指令", 0).show()
    }

fun showPowerCountdown(label: String, cmd: String): Unit {
    val v: View = LayoutInflater.from(this).inflate(R.layout.dialog_power_countdown, (ViewGroup) null)
    val findViewById: View = v.findViewById(R.id.btnPowerCancel)
    Intrinsics.checkNotNullExpressionValue(findViewById, "findViewById(...)")
    NativeButtonKt.applyDarkTheme$default((Button) findViewById, 0, 0, true, 3, null)
    val title: TextView = (TextView) v.findViewById(R.id.powerTitle)
    val countdown: TextView = (TextView) v.findViewById(R.id.powerCountdownText)
    val progress: ProgressBar = (ProgressBar) v.findViewById(R.id.powerProgressBar)
    title.setText("电脑即将" + label)
    progress.setMax(30)
    progress.setProgress(30)
    final androidx.appcompat.app.AlertDialog dialog = new AlertDialog.Builder(this).setView(v).setCancelable(false).create()
    Intrinsics.checkNotNullExpressionValue(dialog, "create(...)")
    val window: Window = dialog.getWindow()
    if (window != null) {
        window.setBackgroundDrawableResource(android.R.color.transparent)
        }
    dialog.show()
    ConnectionManager.INSTANCE.sendPowerCommand(cmd, 30000L)
    val timer: CountDownTimer = new CountDownTimer() { // from class: com.phonehub.MainActivity$showPowerCountdown$timer$1
    {
        super(30000L, 1000L)
        }

    override
    fun onTick(millisUntilFinished: Long): Unit {
        val sec: Int = (int) (millisUntilFinished / 1000)
        countdown.setText(String.valueOf(sec))
        progress.setProgress(sec)
        }

    override
    fun onFinish(): Unit {
        countdown.setText("0")
        dialog.dismiss()
        Toast.makeText(this, label + " 指令已发送", 0).show()
        }
    }.start()
((Button) v.findViewById(R.id.btnPowerCancel)).setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda64
    override
    fun onClick(view: View): Unit {
        MainActivity.showPowerCountdown$lambda$209(timer, dialog, this, label, view)
        }
    })
}

public static final Unit showPowerCountdown$lambda$209(CountDownTimer $timer, androidx.appcompat.app.AlertDialog $dialog, MainActivity this$0, String $label, View it) {
    $timer.cancel()
    ConnectionManager.INSTANCE.sendPowerCommand("cancel", 0L)
    $dialog.dismiss()
    Toast.makeText(this$0, "已取消" + $label, 0).show()
    }

fun getPushWebView(): View {
    val v: View = LayoutInflater.from(this).inflate(R.layout.page_push_web, (ViewGroup) null)
    val button: Button = (Button) v.findViewById(R.id.btnPushUrlPc)
    if (button != null) {
        NativeButtonKt.applyDarkTheme$default(button, 0, 0, true, 3, null)
        }
    val urlInput: EditText = (EditText) v.findViewById(R.id.urlInput)
    val list: ListView = (ListView) v.findViewById(R.id.urlHistoryList)
    if (this.urlHistory.isEmpty()) {
        loadUrlHistory()
        }
    list.post(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda73
        override
        fun run(): Unit {
            MainActivity.getPushWebView$refreshHistory(MainActivity.this, list, urlInput)
            }
        })
    val button2: Button = (Button) v.findViewById(R.id.btnPushUrlPc)
    if (button2 != null) {
        button2.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda84
            override
            fun onClick(view: View): Unit {
                MainActivity.getPushWebView$lambda$213(urlInput, this, list, view)
                }
            })
        }
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$getPushWebView$3(this, null), 3, null)
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$getPushWebView$4(this, null), 3, null)
    Intrinsics.checkNotNull(v)
    var v: return? = null
    }

public static final Unit getPushWebView$refreshHistory(final MainActivity this$0, ListView list, final EditText urlInput) {
    if (this$0.urlHistory.isEmpty()) {
        list.setAdapter((ListAdapter) null)
        return
        }
    Iterable $this$map$iv = this$0.urlHistory
    Collection destination$iv$iv = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv, 10))
    for (Object item$iv$iv : $this$map$iv) {
        val it: UrlHistoryItem = (UrlHistoryItem) item$iv$iv
        val time: String = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(it.getTimestamp()))
        destination$iv$iv.add("[" + it.getDirection() + "] " + it.getUrl() + "\n" + time)
        }
    val displays: List = (List) destination$iv$iv
    list.setAdapter((ListAdapter) ArrayAdapter(this$0, android.R.layout.simple_list_item_1, displays))
    list.setOnItemClickListener(new AdapterView.OnItemClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda74
        override
        fun onItemClick(adapterView: AdapterView, view: View, i: Int, j: Long): Unit {
            MainActivity.getPushWebView$refreshHistory$lambda$211(urlInput, this$0, adapterView, view, i, j)
            }
        })
    }

public static final Unit getPushWebView$refreshHistory$lambda$211(EditText $urlInput, MainActivity this$0, AdapterView adapterView, View view, int pos, long j) {
    $urlInput.setText(this$0.urlHistory.get(pos).getUrl())
    }

public static final Unit getPushWebView$lambda$213(EditText $urlInput, MainActivity this$0, ListView $list, View it) {
    var url: String? = null
    var obj: String? = null
    val text: Editable = $urlInput.getText()
    if (text == null || (obj = text.toString()) == null || (url = StringsKt.trim((CharSequence) obj).toString()) == null) {
        url = ""
        }
    if (url.length() == 0) {
        Toast.makeText(this$0, "请输入 URL", 0).show()
        return
        }
    if (!StringsKt.startsWith$default(url, "http://", false, 2, (Object) null) && !StringsKt.startsWith$default(url, "https://", false, 2, (Object) null)) {
        url = "https://" + url
        }
    ConnectionManager.INSTANCE.pushUrlToPc(url, false)
    this$0.addUrlHistory(url, "电脑 <- 手机")
    getPushWebView$refreshHistory(this$0, $list, $urlInput)
    Toast.makeText(this$0, "已发送到电脑", 0).show()
    }

fun refreshUrlHistoryList(): Unit {
    val cv: View = this.pageCache.get(15)
    if (cv != null) {
        val i: Int = 0
        val lv: ListView = (ListView) cv.findViewById(R.id.urlHistoryList)
        if (lv != null) {
            val i2: Int = 0
            if (this.urlHistory.isEmpty()) {
                lv.setAdapter((ListAdapter) null)
                return
                }
            Iterable $this$map$iv = this.urlHistory
            Collection destination$iv$iv = ArrayList(CollectionsKt.collectionSizeOrDefault($this$map$iv, 10))
            for (Object item$iv$iv : $this$map$iv) {
                val it: UrlHistoryItem = (UrlHistoryItem) item$iv$iv
                val cv2: View = cv
                val i3: Int = i2
                val time: String = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(it.getTimestamp()))
                destination$iv$iv.add("[" + it.getDirection() + "] " + it.getUrl() + "\n" + time)
                i2 = i3
                cv = cv2
                $this$map$iv = $this$map$iv
                i = i
                }
            val displays: List = (List) destination$iv$iv
            lv.setAdapter((ListAdapter) ArrayAdapter(this, android.R.layout.simple_list_item_1, displays))
            }
        }
    }

fun getSettingsView(): View {
    var channelName: String? = null
    val v: View = LayoutInflater.from(this).inflate(R.layout.page_settings, (ViewGroup) null)
    val button: Button = (Button) v.findViewById(R.id.disconnectBtn2)
    if (button != null) {
        NativeButtonKt.applyDarkTheme$default(button, 0, 0, false, 7, null)
        }
    val button2: Button = (Button) v.findViewById(R.id.disconnectBtn2)
    if (button2 != null) {
        button2.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda65
            override
            fun onClick(view: View): Unit {
                MainActivity.getSettingsView$lambda$217(MainActivity.this, view)
                }
            })
        }
    ConnectionManager.ConnectionState state = ConnectionManager.INSTANCE.getConnectionState().getValue()
    val infoStatus: TextView = (TextView) v.findViewById(R.id.infoStatus)
    val infoChannel: TextView = (TextView) v.findViewById(R.id.infoChannel)
    val infoIp: TextView = (TextView) v.findViewById(R.id.infoIp)
    switch (WhenMappings.$EnumSwitchMapping$0[state.ordinal()]) {
        case 1:
        if (infoStatus != null) {
            infoStatus.setText("状态: 已连接")
            }
        if (infoStatus != null) {
            infoStatus.setTextColor(-15696880)
            break
            }
        break
        case 2:
        if (infoStatus != null) {
            infoStatus.setText("状态: 连接中...")
            }
        if (infoStatus != null) {
            infoStatus.setTextColor(-18176)
            break
            }
        break
        default:
        if (infoStatus != null) {
            infoStatus.setText("状态: 未连接")
            }
        if (infoStatus != null) {
            infoStatus.setTextColor(-3066824)
            break
            }
        break
        }
    ConnectionManager.ChannelType channel = ConnectionManager.INSTANCE.getCurrentChannel().getValue()
    switch (WhenMappings.$EnumSwitchMapping$1[channel.ordinal()]) {
        case 1:
        channelName = "WiFi 直连"
        break
        case 2:
        channelName = "USB 数据线"
        break
        default:
        channelName = "无"
        break
        }
    if (infoChannel != null) {
        infoChannel.setText("通道: " + channelName)
        }
    if (infoIp != null) {
        val pcIp: String = ConnectionManager.INSTANCE.getPcIp()
        if (pcIp == null) {
            pcIp = "未知"
            }
        infoIp.setText("IP: " + pcIp)
        }
    Intrinsics.checkNotNull(v)
    var v: return? = null
    }

public static final Unit getSettingsView$lambda$217(MainActivity this$0, View it) {
    ConnectionManager.INSTANCE.disconnect()
    this$0.updateSetupVisibility()
    }

fun showSendTextDialog(): Unit {
    val dialogView: View = LayoutInflater.from(this).inflate(R.layout.dialog_send_text, (ViewGroup) null)
    val filenameInput: EditText = (EditText) dialogView.findViewById(R.id.filenameInput)
    val textContentInput: EditText = (EditText) dialogView.findViewById(R.id.textContentInput)
    val cancelBtn: Button = (Button) dialogView.findViewById(R.id.cancelTextBtn)
    val sendBtn: Button = (Button) dialogView.findViewById(R.id.sendTextBtn)
    Intrinsics.checkNotNull(cancelBtn)
    NativeButtonKt.applyDarkTheme$default(cancelBtn, 0, 0, false, 7, null)
    Intrinsics.checkNotNull(sendBtn)
    NativeButtonKt.applyDarkTheme$default(sendBtn, 0, 0, true, 3, null)
    final androidx.appcompat.app.AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).setCancelable(true).create()
    Intrinsics.checkNotNullExpressionValue(dialog, "create(...)")
    val window: Window = dialog.getWindow()
    if (window != null) {
        window.setBackgroundDrawableResource(android.R.color.transparent)
        }
    dialog.show()
    cancelBtn.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda66
        override
        fun onClick(view: View): Unit {
            androidx.appcompat.app.AlertDialog.this.dismiss()
            }
        })
    sendBtn.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda67
        override
        fun onClick(view: View): Unit {
            MainActivity.showSendTextDialog$lambda$220(textContentInput, filenameInput, this, dialog, view)
            }
        })
    }

public static final Unit showSendTextDialog$lambda$220(EditText $textContentInput, EditText $filenameInput, MainActivity this$0, androidx.appcompat.app.AlertDialog $dialog, View it) {
    val text: String = $textContentInput.getText().toString()
    val it2: String = $filenameInput.getText().toString()
    if (StringsKt.isBlank(it2)) {
        it2 = null
        }
    if (text.length() > 0) {
        ConnectionManager.INSTANCE.sendText(text, it2)
        Toast.makeText(this$0, "已发送", 0).show()
        $dialog.dismiss()
        return
        }
    Toast.makeText(this$0, "内容不能为空", 0).show()
    }

fun attemptConnect(): Unit {
    var intOrNull: Integer? = null
    val editText: EditText = this.ipInput
    val textView: TextView = null
    if (editText == null) {
        Intrinsics.throwUninitializedPropertyAccessException("ipInput")
        editText = null
        }
    val ip: String = StringsKt.trim((CharSequence) editText.getText().toString()).toString()
    if (ip.length() == 0) {
        Toast.makeText(this, "请输入IP地址", 0).show()
        return
        }
    val editText2: EditText = this.portInput
    if (editText2 == null) {
        Intrinsics.throwUninitializedPropertyAccessException("portInput")
        editText2 = null
        }
    val portStr: String = StringsKt.trim((CharSequence) editText2.getText().toString()).toString()
    val i: Int = 58627
    if ((portStr.length() > 0) && (intOrNull = StringsKt.toIntOrNull(portStr)) != null) {
        i = intOrNull.intValue()
        }
    val port: Int = i
    val editText3: EditText = this.tokenInput
    if (editText3 == null) {
        Intrinsics.throwUninitializedPropertyAccessException("tokenInput")
        editText3 = null
        }
    val obj: String = StringsKt.trim((CharSequence) editText3.getText().toString()).toString()
    if (obj.length() == 0) {
        obj = "541881452418845"
        }
    val token: String = obj
    val prefs: SharedPreferences = getSharedPreferences("phonehub_prefs", 0)
    prefs.edit().putInt("cached_port", port).putString("cached_token", token).apply()
    val button: Button = this.connectBtn
    if (button == null) {
        Intrinsics.throwUninitializedPropertyAccessException("connectBtn")
        button = null
        }
    button.setEnabled(false)
    val textView2: TextView = this.connectStatus
    if (textView2 == null) {
        Intrinsics.throwUninitializedPropertyAccessException("connectStatus")
        } else {
        textView = textView2
        }
    textView.setText("正在连接...")
    ConnectionManager.INSTANCE.connect(ip, port, token)
    }

fun setupFlows(): Unit {
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$setupFlows$1(this, null), 3, null)
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$setupFlows$2(this, null), 3, null)
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$setupFlows$3(this, null), 3, null)
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$setupFlows$4(this, null), 3, null)
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$setupFlows$5(this, null), 3, null)
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$setupFlows$6(null), 3, null)
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$setupFlows$7(this, null), 3, null)
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$setupFlows$8(this, null), 3, null)
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$setupFlows$9(this, null), 3, null)
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$setupFlows$10(this, null), 3, null)
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$setupFlows$11(this, null), 3, null)
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$setupFlows$12(this, null), 3, null)
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$setupFlows$13(this, null), 3, null)
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new MainActivity$setupFlows$14(this, null), 3, null)
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getIO()), null, null, new MainActivity$setupFlows$15(this, null), 3, null)
    BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getIO()), null, null, new MainActivity$setupFlows$16(this, null), 3, null)
    }

fun showReceivedTextDialog(filename: String, textContent: String): Unit {
    if (isFinishing()) {
        return
        }
    val v: LinearLayout = new LinearLayout(this)
    v.setOrientation(1)
    v.setPadding(48, 32, 48, 32)
    v.setBackgroundColor(-14803426)
    TextView $this$showReceivedTextDialog_u24lambda_u24223 = TextView(this)
    $this$showReceivedTextDialog_u24lambda_u24223.setText("收到文字: " + filename)
    $this$showReceivedTextDialog_u24lambda_u24223.setTextColor(-1)
    $this$showReceivedTextDialog_u24lambda_u24223.setTextSize(16.0f)
    $this$showReceivedTextDialog_u24lambda_u24223.setPadding(0, 0, 0, 24)
    TextView $this$showReceivedTextDialog_u24lambda_u24224 = TextView(this)
    $this$showReceivedTextDialog_u24lambda_u24224.setText(textContent)
    $this$showReceivedTextDialog_u24lambda_u24224.setTextColor(-1)
    $this$showReceivedTextDialog_u24lambda_u24224.setTextSize(14.0f)
    $this$showReceivedTextDialog_u24lambda_u24224.setPadding(24, 24, 24, 24)
    $this$showReceivedTextDialog_u24lambda_u24224.setBackgroundColor(-13816531)
    $this$showReceivedTextDialog_u24lambda_u24224.setMinLines(3)
    val copyBtn: Button = new Button(this)
    copyBtn.setText("复制")
    NativeButtonKt.applyDarkTheme$default(copyBtn, 0, 0, false, 7, null)
    val saveBtn: Button = new Button(this)
    saveBtn.setText("保存")
    NativeButtonKt.applyDarkTheme$default(saveBtn, 0, 0, true, 3, null)
    val closeBtn: Button = new Button(this)
    closeBtn.setText("关闭")
    NativeButtonKt.applyDarkTheme$default(closeBtn, 0, 0, false, 7, null)
    v.addView($this$showReceivedTextDialog_u24lambda_u24223)
    v.addView($this$showReceivedTextDialog_u24lambda_u24224)
    v.addView(copyBtn)
    v.addView(saveBtn)
    v.addView(closeBtn)
    final androidx.appcompat.app.AlertDialog dialog = new AlertDialog.Builder(this).setView(v).setCancelable(true).create()
    Intrinsics.checkNotNullExpressionValue(dialog, "create(...)")
    val window: Window = dialog.getWindow()
    if (window != null) {
        window.setBackgroundDrawableResource(android.R.color.transparent)
        }
    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda35
        override
        fun onDismiss(dialogInterface: DialogInterface): Unit {
            MainActivity.showReceivedTextDialog$lambda$228(filename, textContent, this, dialogInterface)
            }
        })
    dialog.show()
    closeBtn.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda36
        override
        fun onClick(view: View): Unit {
            MainActivity.showReceivedTextDialog$lambda$229(filename, textContent, this, dialog, view)
            }
        })
    copyBtn.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda37
        override
        fun onClick(view: View): Unit {
            MainActivity.showReceivedTextDialog$lambda$230(MainActivity.this, textContent, filename, view)
            }
        })
    saveBtn.setOnClickListener(new View.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda38
        override
        fun onClick(view: View): Unit {
            MainActivity.showReceivedTextDialog$lambda$233(filename, this, textContent, view)
            }
        })
    }

public static final Unit showReceivedTextDialog$lambda$228(String $filename, String $textContent, MainActivity this$0, DialogInterface it) {
    val key: String = $filename + "|" + $textContent
    this$0.handledTextContents.put(key, Long.valueOf(System.currentTimeMillis()))
    }

public static final Unit showReceivedTextDialog$lambda$229(String $filename, String $textContent, MainActivity this$0, androidx.appcompat.app.AlertDialog $dialog, View it) {
    val key: String = $filename + "|" + $textContent
    this$0.handledTextContents.put(key, Long.valueOf(System.currentTimeMillis()))
    $dialog.dismiss()
    }

public static final Unit showReceivedTextDialog$lambda$230(MainActivity this$0, String $textContent, String $filename, View it) {
    try {
        val systemService: Any = this$0.getSystemService("clipboard")
        Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.content.ClipboardManager")
        val cm: ClipboardManager = (ClipboardManager) systemService
        cm.setPrimaryClip(ClipData.newPlainText("PhoneHub", $textContent))
        Toast.makeText(this$0, "已复制到剪贴板", 0).show()
        val key: String = $filename + "|" + $textContent
        this$0.handledTextContents.put(key, Long.valueOf(System.currentTimeMillis()))
        } catch (Exception e) {
        Toast.makeText(this$0, "复制失败: " + e.getMessage(), 0).show()
        }
    }

public static final Unit showReceivedTextDialog$lambda$233(final String $filename, final MainActivity this$0, final String $textContent, View it) {
    val z: Boolean = false
    if (StringsKt.contains$default((CharSequence) $filename, '.', false, 2, (Object) null) && !StringsKt.endsWith$default((CharSequence) $filename, '.', false, 2, (Object) null)) {
        if (StringsKt.substringAfterLast($filename, '.', "").length() > 0) {
            z = true
            }
        }
    val hasExtension: Boolean = z
    if (hasExtension) {
        this$0.pendingSaveText = $textContent
        this$0.saveTextLauncher.launch($filename)
        return
        }
    final EditText $this$showReceivedTextDialog_u24lambda_u24233_u24lambda_u24231 = EditText(this$0)
    $this$showReceivedTextDialog_u24lambda_u24233_u24lambda_u24231.setHint("文件后缀（留空为 txt）")
    $this$showReceivedTextDialog_u24lambda_u24233_u24lambda_u24231.setTextColor(-1)
    $this$showReceivedTextDialog_u24lambda_u24233_u24lambda_u24231.setHintTextColor(-10066330)
    $this$showReceivedTextDialog_u24lambda_u24233_u24lambda_u24231.setInputType(1)
    new AlertDialog.Builder(this$0).setTitle("输入文件后缀").setView($this$showReceivedTextDialog_u24lambda_u24233_u24lambda_u24231).setPositiveButton("下一步", new DialogInterface.OnClickListener() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda53
        override
        fun onClick(dialogInterface: DialogInterface, i: Int): Unit {
            MainActivity.showReceivedTextDialog$lambda$233$lambda$232($this$showReceivedTextDialog_u24lambda_u24233_u24lambda_u24231, this$0, $textContent, $filename, dialogInterface, i)
            }
        }).setNegativeButton("取消", (DialogInterface.OnClickListener) null).show()
    }

public static final Unit showReceivedTextDialog$lambda$233$lambda$232(EditText $extInput, MainActivity this$0, String $textContent, String $filename, DialogInterface dialogInterface, int i) {
    val ext: String = StringsKt.trimStart(StringsKt.trim((CharSequence) $extInput.getText().toString()).toString(), '.')
    if (ext.length() == 0) {
        ext = "txt"
        }
    this$0.pendingSaveText = $textContent
    val baseName: String = StringsKt.isBlank($filename) ? "received_" + System.currentTimeMillis() : StringsKt.substringBeforeLast($filename, '.', $filename)
    this$0.saveTextLauncher.launch(baseName + "." + ext)
    }

fun updateSetupVisibility(): Unit {
    Handler(Looper.getMainLooper()).post(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda31
        override
        fun run(): Unit {
            MainActivity.updateSetupVisibility$lambda$234(MainActivity.this)
            }
        })
    }

public static final Unit updateSetupVisibility$lambda$234(MainActivity this$0) {
    val isConnected: Boolean = ConnectionManager.INSTANCE.getConnectionState().getValue() == ConnectionManager.ConnectionState.CONNECTED
    val linearLayout: LinearLayout = null
    if (isConnected || ConnectionManager.INSTANCE.hasReceivedPcCpu()) {
        val linearLayout2: LinearLayout = this$0.setupScreen
        if (linearLayout2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("setupScreen")
            linearLayout2 = null
            }
        linearLayout2.setVisibility(8)
        val linearLayout3: LinearLayout = this$0.mainContainer
        if (linearLayout3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mainContainer")
            linearLayout3 = null
            }
        linearLayout3.setVisibility(0)
        val frameLayout: FrameLayout = this$0.pageContainer
        if (frameLayout == null) {
            Intrinsics.throwUninitializedPropertyAccessException("pageContainer")
            frameLayout = null
            }
        if (frameLayout.getChildCount() == 0) {
            switchTab$default(this$0, 0, false, 2, null)
            return
            }
        return
        }
    val linearLayout4: LinearLayout = this$0.setupScreen
    if (linearLayout4 == null) {
        Intrinsics.throwUninitializedPropertyAccessException("setupScreen")
        linearLayout4 = null
        }
    linearLayout4.setVisibility(0)
    val linearLayout5: LinearLayout = this$0.mainContainer
    if (linearLayout5 == null) {
        Intrinsics.throwUninitializedPropertyAccessException("mainContainer")
        } else {
        linearLayout = linearLayout5
        }
    linearLayout.setVisibility(8)
    }

override
fun onNewIntent(intent: Intent): Unit {
    super.onNewIntent(intent)
    setIntent(intent)
    handleTextNotificationIntent(intent)
    handleFileTransferNotificationIntent(intent)
    }

fun handleTextNotificationIntent(intent: Intent): Unit {
    val z: Boolean = false
    if (intent != null && intent.getBooleanExtra("show_text_dialog", false)) {
        z = true
        }
    if (z) {
        val lastReceivedText: Pair<String, String> = ConnectionManager.INSTANCE.getLastReceivedText()
        if (lastReceivedText != null) {
            val filename: String = lastReceivedText.component1()
            val txt: String = lastReceivedText.component2()
            showReceivedTextDialog(filename, txt)
            }
        intent.removeExtra("show_text_dialog")
        }
    }

fun handleFileTransferNotificationIntent(intent: Intent): Unit {
    if (intent != null && intent.getBooleanExtra("show_file_transfer", false)) {
        switchTab$default(this, 1, false, 2, null)
        intent.removeExtra("show_file_transfer")
        }
    }

override
fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit {
    var uri: Uri? = null
    var file: File? = null
    var uri2: Uri? = null
    var idx: Int? = null
    var it: Any? = null
    super.onActivityResult(requestCode, resultCode, data)
    if (resultCode != -1) {
        return
        }
    if (requestCode == this.SELECT_FILE_CODE) {
        if (data != null && (uri2 = data.getData()) != null) {
            ConnectionManager.sendFile$default(ConnectionManager.INSTANCE, uri2, null, 2, null)
            val cr: ContentResolver = getContentResolver()
            val name: Any = "file"
            try {
                val query: Cursor = cr.query(uri2, null, null, null, null)
                if (query != null) {
                    val cursor: Cursor = query
                    try {
                        val cursor2: Cursor = cursor
                        if (cursor2.moveToFirst() && (idx = cursor2.getColumnIndex("_display_name")) >= 0 && (it = cursor2.getString(idx)) != null) {
                            name = it
                            }
                        val unit: Unit = Unit.INSTANCE
                        CloseableKt.closeFinally(cursor, null)
                        } finally {
                        }
                    }
                } catch (Exception e) {
                }
            Toast.makeText(this, "开始发送: " + name, 0).show()
            return
            }
        return
        }
    if (requestCode == this.SELECT_APK_CODE && data != null && (uri = data.getData()) != null && (file = uriToFile(uri)) != null && file.exists()) {
        installApk(file)
        }
    }

fun uriToFile(uri: Uri): File {
    try {
        val inputStream: InputStream = getContentResolver().openInputStream(uri)
        if (inputStream == null) {
            var null: return? = null
            }
        val tempFile: File = new File(getCacheDir(), "temp_send_" + System.currentTimeMillis())
        val fileOutputStream: FileOutputStream = new FileOutputStream(tempFile)
        try {
            val output: FileOutputStream = fileOutputStream
            ByteStreamsKt.copyTo$default(inputStream, output, 0, 2, null)
            CloseableKt.closeFinally(fileOutputStream, null)
            inputStream.close()
            var tempFile: return? = null
            } finally {
            }
        } catch (Exception e) {
        var null: return? = null
        }
    }

override
fun onResume(): Unit {
    var text: String? = null
    var textView: TextView? = null
    ClipData.Item itemAt
    var coerceToText: CharSequence? = null
    super.onResume()
    updateSetupVisibility()
    val systemService: Any = getSystemService("clipboard")
    Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.content.ClipboardManager")
    val cm: ClipboardManager = (ClipboardManager) systemService
    val primaryClip: ClipData = cm.getPrimaryClip()
    if (primaryClip == null || (itemAt = primaryClip.getItemAt(0)) == null || (coerceToText = itemAt.coerceToText(this)) == null || (text = coerceToText.toString()) == null) {
        text = ""
        }
    val view: View = this.pageCache.get(4)
    if (view == null || (textView = (TextView) view.findViewById(R.id.currentClipText)) == null) {
        return
        }
    textView.setText(text)
    }

fun startPhoneScreenCapture(): Unit {
    if (this.screenCaptureRunning) {
        return
        }
    try {
        val dm: DisplayMetrics = getResources().getDisplayMetrics()
        this.screenWidth = dm.widthPixels
        this.screenHeight = dm.heightPixels
        this.screenDensity = dm.densityDpi
        if (this.screenWidth > 1280 || this.screenHeight > 1280) {
            val ratio: Float = this.screenWidth / this.screenHeight
            if (this.screenWidth > this.screenHeight) {
                this.screenWidth = 1280
                this.screenHeight = (1280 / ratio)
                } else {
                this.screenHeight = 1280
                this.screenWidth = (1280 * ratio)
                }
            }
        val systemService: Any = getSystemService(MediaProjectionManager.class)
        Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.media.projection.MediaProjectionManager")
        val intent: Intent = ((MediaProjectionManager) systemService).createScreenCaptureIntent()
        Intrinsics.checkNotNullExpressionValue(intent, "createScreenCaptureIntent(...)")
        this.screenCaptureLauncher.launch(intent)
        } catch (Exception e) {
        Toast.makeText(this, "启动推流失败: " + e.getMessage(), 1).show()
        }
    }

fun startScreenCaptureLoop(): Unit {
    if (this.mediaProjection == null) {
        runOnUiThread(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda59
            override
            fun run(): Unit {
                MainActivity.startScreenCaptureLoop$lambda$242(MainActivity.this)
                }
            })
        return
        }
    this.screenCaptureRunning = true
    try {
        this.imageReader = ImageReader.newInstance(this.screenWidth, this.screenHeight, 1, 3)
        val mediaProjection: MediaProjection = this.mediaProjection
        val virtualDisplay: VirtualDisplay = null
        if (mediaProjection != null) {
            val i: Int = this.screenWidth
            val i2: Int = this.screenHeight
            val i3: Int = this.screenDensity
            val imageReader: ImageReader = this.imageReader
            virtualDisplay = mediaProjection.createVirtualDisplay("PhoneHubMirror", i, i2, i3, 16, imageReader != null ? imageReader.getSurface() : null, null, null)
            }
        this.virtualDisplay = virtualDisplay
        if (this.virtualDisplay == null) {
            this.screenCaptureRunning = false
            runOnUiThread(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda60
                override
                fun run(): Unit {
                    MainActivity.startScreenCaptureLoop$lambda$243(MainActivity.this)
                    }
                })
            return
            }
        this.screenCaptureThread = Thread(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda63
            override
            fun run(): Unit {
                MainActivity.startScreenCaptureLoop$lambda$247(MainActivity.this)
                }
            })
        val thread: Thread = this.screenCaptureThread
        if (thread != null) {
            thread.start()
            }
        } catch (Exception e) {
        this.screenCaptureRunning = false
        runOnUiThread(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda61
            override
            fun run(): Unit {
                MainActivity.startScreenCaptureLoop$lambda$244(MainActivity.this, e)
                }
            })
        }
    }

public static final Unit startScreenCaptureLoop$lambda$242(MainActivity this$0) {
    Toast.makeText(this$0, "屏幕录制权限未获取，请重试", 0).show()
    }

public static final Unit startScreenCaptureLoop$lambda$243(MainActivity this$0) {
    Toast.makeText(this$0, "创建虚拟显示失败", 1).show()
    }

public static final Unit startScreenCaptureLoop$lambda$244(MainActivity this$0, Exception $e) {
    Toast.makeText(this$0, "创建虚拟显示失败: " + $e.getMessage(), 1).show()
    }

public static final Unit startScreenCaptureLoop$lambda$247(MainActivity this$0) {
    var it: Bitmap? = null
    val conn: ConnectionManager = ConnectionManager.INSTANCE
    val i: Int = this$0.screenWidth * 4
    while (this$0.screenCaptureRunning) {
        try {
            val imageReader: ImageReader = this$0.imageReader
            val image: Image = imageReader != null ? imageReader.acquireLatestImage() : null
            if (image == null) {
                try {
                    Thread.sleep(16L)
                    } catch (InterruptedException e) {
                    return
                    }
                } else {
                Image.Plane[] planes = image.getPlanes()
                Intrinsics.checkNotNull(planes)
                if (planes.length == 0) {
                    image.close()
                    try {
                        Thread.sleep(16L)
                        } catch (InterruptedException e2) {
                        return
                        }
                    } else {
                    val buffer: ByteBuffer = planes[0].getBuffer()
                    val pixelStride: Int = planes[0].getPixelStride()
                    val rowStride: Int = planes[0].getRowStride()
                    val rowPadding: Int = rowStride - (this$0.screenWidth * pixelStride)
                    if (rowPadding == 0) {
                        it = Bitmap.createBitmap(this$0.screenWidth, this$0.screenHeight, Bitmap.Config.ARGB_8888)
                        buffer.rewind()
                        it.copyPixelsFromBuffer(buffer)
                        } else {
                        val paddedBitmap: Bitmap = Bitmap.createBitmap(this$0.screenWidth + (rowPadding / pixelStride), this$0.screenHeight, Bitmap.Config.ARGB_8888)
                        Intrinsics.checkNotNullExpressionValue(paddedBitmap, "createBitmap(...)")
                        buffer.rewind()
                        paddedBitmap.copyPixelsFromBuffer(buffer)
                        val createBitmap: Bitmap = Bitmap.createBitmap(paddedBitmap, 0, 0, this$0.screenWidth, this$0.screenHeight)
                        paddedBitmap.recycle()
                        it = createBitmap
                        }
                    Intrinsics.checkNotNull(it)
                    val bitmap: Bitmap = it
                    val baos: ByteArrayOutputStream = new ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                    val jpegData: Array<Byte> = baos.toByteArray()
                    Intrinsics.checkNotNull(jpegData)
                    ConnectionManager.sendFrameToPc$default(conn, jpegData, null, 2, null)
                    image.close()
                    bitmap.recycle()
                    try {
                        Thread.sleep(16L)
                        } catch (InterruptedException e3) {
                        return
                        }
                    }
                }
            } catch (Exception e4) {
            if (this$0.screenCaptureRunning) {
                try {
                    Thread.sleep(200L)
                    } catch (InterruptedException e5) {
                    return
                    }
                } else {
                continue
                }
            }
        }
    }

fun stopPhoneScreenCapture(): Unit {
    this.screenCaptureRunning = false
    val thread: Thread = this.screenCaptureThread
    if (thread != null) {
        thread.interrupt()
        }
    val it: Runnable = this.mirrorFrameTimeoutRunnable
    if (it != null) {
        this.frameTimeoutHandler.removeCallbacks(it)
        }
    this.mirrorFrameTimeoutRunnable = null
    try {
        val thread2: Thread = this.screenCaptureThread
        if (thread2 != null) {
            thread2.join(500L)
            }
        } catch (InterruptedException e) {
        }
    this.screenCaptureThread = null
    val virtualDisplay: VirtualDisplay = this.virtualDisplay
    if (virtualDisplay != null) {
        virtualDisplay.release()
        }
    this.virtualDisplay = null
    val imageReader: ImageReader = this.imageReader
    if (imageReader != null) {
        imageReader.close()
        }
    this.imageReader = null
    val mediaProjection: MediaProjection = this.mediaProjection
    if (mediaProjection != null) {
        mediaProjection.stop()
        }
    this.mediaProjection = null
    }

fun startPhoneAudioCapture(): Unit {
    if (this.audioCaptureRunning) {
        return
        }
    val minBufferSize: Int = AudioRecord.getMinBufferSize(44100, 16, 2)
    switch (minBufferSize) {
        case -2:
        case -1:
        runOnUiThread(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda95
            override
            fun run(): Unit {
                MainActivity.startPhoneAudioCapture$lambda$249(MainActivity.this)
                }
            })
        return
        default:
        val bufferSize: Int = Math.max(minBufferSize, 4096)
        val z: Boolean = false
        try {
            val mp: MediaProjection = this.mediaProjection
            if (mp == null) {
                Log.w("MainActivity", "MediaProjection 不可用，回退到 MIC 录音")
                } else {
                val config: AudioPlaybackCaptureConfiguration = new AudioPlaybackCaptureConfiguration.Builder(mp).addMatchingUsage(1).addMatchingUsage(14).addMatchingUsage(0).build()
                Intrinsics.checkNotNullExpressionValue(config, "build(...)")
                this.audioRecord = new AudioRecord.Builder().setAudioFormat(new AudioFormat.Builder().setSampleRate(44100).setChannelMask(16).setEncoding(2).build()).setBufferSizeInBytes(bufferSize).setAudioPlaybackCaptureConfig(config).build()
                Log.i("MainActivity", "AudioPlaybackCapture 已启动（系统内音）")
                }
            } catch (Exception e) {
            Log.e("MainActivity", "AudioPlaybackCapture 失败，回退到 MIC: " + e.getMessage())
            this.audioRecord = null
            }
        if (this.audioRecord == null) {
            this.audioRecord = AudioRecord(1, 44100, 16, 2, bufferSize)
            Log.i("MainActivity", "AudioRecord 使用 MIC 源")
            }
        val audioRecord: AudioRecord = this.audioRecord
        if (audioRecord != null && audioRecord.getState() == 1) {
            z = true
            }
        if (!z) {
            val audioRecord2: AudioRecord = this.audioRecord
            Log.e("MainActivity", "AudioRecord 初始化失败，state=" + (audioRecord2 != null ? Integer.valueOf(audioRecord2.getState()) : null))
            val audioRecord3: AudioRecord = this.audioRecord
            if (audioRecord3 != null) {
                audioRecord3.release()
                }
            this.audioRecord = null
            runOnUiThread(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda106
                override
                fun run(): Unit {
                    MainActivity.startPhoneAudioCapture$lambda$250(MainActivity.this)
                    }
                })
            return
            }
        this.audioCaptureRunning = true
        val audioRecord4: AudioRecord = this.audioRecord
        if (audioRecord4 != null) {
            audioRecord4.startRecording()
            }
        this.audioCaptureThread = Thread(Runnable() { // from class: com.phonehub.MainActivity$$ExternalSyntheticLambda117
            override
            fun run(): Unit {
                MainActivity.startPhoneAudioCapture$lambda$253(bufferSize, this)
                }
            })
        val thread: Thread = this.audioCaptureThread
        if (thread != null) {
            thread.start()
            return
            }
        return
        }
    }

public static final Unit startPhoneAudioCapture$lambda$249(MainActivity this$0) {
    Toast.makeText(this$0, "音频参数不支持", 0).show()
    }

public static final Unit startPhoneAudioCapture$lambda$250(MainActivity this$0) {
    Toast.makeText(this$0, "录音初始化失败，请检查麦克风权限", 0).show()
    }

public static final Unit startPhoneAudioCapture$lambda$253(int $bufferSize, MainActivity this$0) {
    val buffer: Array<Byte> = new byte[$bufferSize]
    val conn: ConnectionManager = ConnectionManager.INSTANCE
    val batchBuffers: List<Array<Byte>> = new ArrayList()
    while (this$0.audioCaptureRunning) {
        try {
            val audioRecord: AudioRecord = this$0.audioRecord
            val read: Int = audioRecord != null ? audioRecord.read(buffer, 0, $bufferSize) : 0
            if (read > 0) {
                val copyOf: Array<Byte> = Arrays.copyOf(buffer, read)
                Intrinsics.checkNotNullExpressionValue(copyOf, "copyOf(...)")
                batchBuffers.add(copyOf)
                if (batchBuffers.size() >= 5) {
                    val i: Int = 0
                    for (byte[] it : batchBuffers) {
                        i += it.length
                        }
                    val merged: Array<Byte> = new byte[i]
                    val offset: Int = 0
                    for (byte[] b : batchBuffers) {
                        System.arraycopy(b, 0, merged, offset, b.length)
                        offset += b.length
                        }
                    batchBuffers.clear()
                    conn.sendAudioToPc(merged)
                    }
                }
            } catch (Exception e) {
            if (this$0.audioCaptureRunning) {
                Thread.sleep(100L)
                }
            }
        }
    if (!batchBuffers.isEmpty()) {
        try {
            val i2: Int = 0
            for (byte[] it2 : batchBuffers) {
                i2 += it2.length
                }
            val merged2: Array<Byte> = new byte[i2]
            val offset2: Int = 0
            for (byte[] b2 : batchBuffers) {
                System.arraycopy(b2, 0, merged2, offset2, b2.length)
                offset2 += b2.length
                }
            conn.sendAudioToPc(merged2)
            } catch (Exception e2) {
            }
        }
    }

fun stopPhoneAudioCapture(): Unit {
    this.audioCaptureRunning = false
    val thread: Thread = this.audioCaptureThread
    if (thread != null) {
        thread.interrupt()
        }
    this.audioCaptureThread = null
    val audioRecord: AudioRecord = this.audioRecord
    if (audioRecord != null) {
        audioRecord.stop()
        }
    val audioRecord2: AudioRecord = this.audioRecord
    if (audioRecord2 != null) {
        audioRecord2.release()
        }
    this.audioRecord = null
    }
}
