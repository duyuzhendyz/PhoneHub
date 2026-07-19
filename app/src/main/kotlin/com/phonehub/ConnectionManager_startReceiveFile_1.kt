package com.phonehub

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.ItemTouchHelper
import com.phonehub.ConnectionManager
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.ConcurrentHashMap
import kotlin.ResultKt
import kotlin.Unit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import kotlin.coroutines.jvm.internal.Boxing
import kotlin.coroutines.jvm.internal.DebugMetadata
import kotlin.coroutines.jvm.internal.SuspendLambda
import kotlin.io.CloseableKt
import kotlin.io.FilesKt
import kotlin.jvm.functions.Function2
import kotlin.text.StringsKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class ConnectionManager {
    final  String $fileId
    final  String $fileName
    final  long $fileSize
    var label: Int? = null

    public ConnectionManager$startReceiveFile$1(String str, String str2, long j, Continuation<? super ConnectionManager$startReceiveFile$1> continuation) {
        super(2, continuation)
        this.$fileId = str
        this.$fileName = str2
        this.$fileSize = j
        }

    override
    fun create(obj: Any, continuation: Continuation<?>): Continuation<Unit> {
        return new ConnectionManager$startReceiveFile$1(this.$fileId, this.$fileName, this.$fileSize, continuation)
        }

    override
    fun invoke(coroutineScope: CoroutineScope, continuation: Continuation<? super Unit>): Any {
        return ((ConnectionManager$startReceiveFile$1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE)
        }

    /* JADX WARN: Code restructure failed: missing block: B:15:0x008d, code lost:

    if (r8.mkdirs() != false) goto L21
     */
    /* JADX WARN: Code restructure failed: missing block: B:272:0x07a7, code lost:

    r0 = move-exception
     */
    /* JADX WARN: Code restructure failed: missing block: B:273:0x07a8, code lost:

    r9 = "PhoneHub"
    r2 = r7
    r3 = r0
     */
    /* JADX WARN: Code restructure failed: missing block: B:274:0x07a2, code lost:

    r0 = move-exception
     */
    /* JADX WARN: Code restructure failed: missing block: B:275:0x07a3, code lost:

    r2 = r7
    r3 = r0
     */
    override
    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun invokeSuspend(obj: Any): Any {
        Ref.ObjectRef objectRef
        var th: Throwable? = null
        var str: String? = null
        var exc: Exception? = null
        var concurrentHashMap: ConcurrentHashMap? = null
        var httpURLConnection: HttpURLConnection? = null
        var z: Boolean? = null
        var httpURLConnection2: HttpURLConnection? = null
        var z2: Boolean? = null
        var mutableStateFlow: MutableStateFlow? = null
        var concurrentHashMap2: ConcurrentHashMap? = null
        var r21: ??? = null
        var file: File? = null
        var file2: File? = null
        var context: Context? = null
        var context2: Context? = null
        var filesDir: File? = null
        var file3: File? = null
        var j: Long? = null
        var str2: String? = null
        var mutableStateFlow2: MutableStateFlow? = null
        var i: Int? = null
        var str3: String? = null
        var str4: String? = null
        var responseCode: Int? = null
        var mutableStateFlow3: MutableStateFlow? = null
        var concurrentHashMap3: ConcurrentHashMap? = null
        ConnectionManager.FileReceiveState fileReceiveState
        var str5: String? = null
        var str6: String? = null
        var str7: String? = null
        Ref.ObjectRef objectRef2
        var r4: ??? = null
        var file4: File? = null
        var r2: ??? = null
        var exc2: Exception? = null
        var z3: Boolean? = null
        var z4: Boolean? = null
        var z5: Boolean? = null
        var str8: String? = null
        var str9: String? = null
        var str10: String? = null
        var z6: Boolean? = null
        var z7: Boolean? = null
        var z8: Boolean? = null
        var z9: Boolean? = null
        ConnectionManager.ResumeInfo resumeInfo
        var mutableStateFlow4: MutableStateFlow? = null
        var mutableStateFlow5: MutableStateFlow? = null
        var mutableSharedFlow: MutableSharedFlow? = null
        var mutableStateFlow6: MutableStateFlow? = null
        ConnectionManager.ResumeInfo resumeInfo2
        var mutableStateFlow7: MutableStateFlow? = null
        var z10: Boolean? = null
        var mutableStateFlow8: MutableStateFlow? = null
        var fileOutputStream: FileOutputStream? = null
        var str11: String? = null
        var str12: String? = null
        var th2: Throwable? = null
        var th3: Throwable? = null
        var str13: String? = null
        var z11: Boolean? = null
        var mutableStateFlow9: MutableStateFlow? = null
        var mutableStateFlow10: MutableStateFlow? = null
        var concurrentHashMap4: ConcurrentHashMap? = null
        var httpURLConnection3: HttpURLConnection? = null
        var mutableStateFlow11: MutableStateFlow? = null
        var i2: Int? = null
        var file5: File? = null
        var file6: File? = null
        ConnectionManager$startReceiveFile$1 connectionManager$startReceiveFile$1 = this
        val str14: String = ", paused="
        val str15: String = ", cancel="
        val str16: String = "/"
        val str17: String = "PhoneHub"
        IntrinsicsKt.getCOROUTINE_SUSPENDED()
        switch (connectionManager$startReceiveFile$1.label) {
            case 0:
            ResultKt.throwOnFailure(obj)
            Ref.ObjectRef objectRef3 = new Ref.ObjectRef()
            try {
                val connectionManager: ConnectionManager = ConnectionManager.INSTANCE
                ConnectionManager.transferInProgress = true
                val connectionManager2: ConnectionManager = ConnectionManager.INSTANCE
                ConnectionManager.fileTransferCancel = false
                val connectionManager3: ConnectionManager = ConnectionManager.INSTANCE
                ConnectionManager.transferPaused = false
                val connectionManager4: ConnectionManager = ConnectionManager.INSTANCE
                r21 = 56
                ConnectionManager.resumeInfo = new ConnectionManager.ResumeInfo(connectionManager$startReceiveFile$1.$fileId, connectionManager$startReceiveFile$1.$fileName, connectionManager$startReceiveFile$1.$fileSize, null, null, 0L, 56, null)
                file = ConnectionManager.receiveDir
                if (file != null) {
                    try {
                        try {
                            Boxing.boxBoolean(file.mkdirs())
                            } catch (Throwable th4) {
                            th = th4
                            objectRef = objectRef3
                            try {
                                httpURLConnection = (HttpURLConnection) objectRef.element
                                if (httpURLConnection != null) {
                                    httpURLConnection.disconnect()
                                    val unit: Unit = Unit.INSTANCE
                                    }
                                } catch (Exception e) {
                                }
                            val connectionManager5: ConnectionManager = ConnectionManager.INSTANCE
                            ConnectionManager.currentConn = null
                            val connectionManager6: ConnectionManager = ConnectionManager.INSTANCE
                            ConnectionManager.transferInProgress = false
                            concurrentHashMap = ConnectionManager.fileReceiveState
                            concurrentHashMap.remove(connectionManager$startReceiveFile$1.$fileId)
                            var th: throw? = null
                            }
                        } catch (Exception e2) {
                        exc = e2
                        str = "PhoneHub"
                        objectRef = objectRef3
                        try {
                            z = ConnectionManager.transferPaused
                            if (!z) {
                                }
                            Boxing.boxInt(Log.w(str, "File receive interrupted (paused/cancelled): " + exc.getMessage()))
                            try {
                                httpURLConnection2 = (HttpURLConnection) objectRef.element
                                if (httpURLConnection2 != null) {
                                    }
                                } catch (Exception e3) {
                                }
                            val connectionManager7: ConnectionManager = ConnectionManager.INSTANCE
                            ConnectionManager.currentConn = null
                            val connectionManager8: ConnectionManager = ConnectionManager.INSTANCE
                            ConnectionManager.transferInProgress = false
                            concurrentHashMap2 = ConnectionManager.fileReceiveState
                            concurrentHashMap2.remove(connectionManager$startReceiveFile$1.$fileId)
                            return Unit.INSTANCE
                            } catch (Throwable th5) {
                            th = th5
                            httpURLConnection = (HttpURLConnection) objectRef.element
                            if (httpURLConnection != null) {
                                }
                            val connectionManager52: ConnectionManager = ConnectionManager.INSTANCE
                            ConnectionManager.currentConn = null
                            val connectionManager62: ConnectionManager = ConnectionManager.INSTANCE
                            ConnectionManager.transferInProgress = false
                            concurrentHashMap = ConnectionManager.fileReceiveState
                            concurrentHashMap.remove(connectionManager$startReceiveFile$1.$fileId)
                            var th: throw? = null
                            }
                        }
                    }
                file2 = ConnectionManager.receiveDir
                } catch (Exception e4) {
                str = "PhoneHub"
                objectRef = objectRef3
                exc = e4
                } catch (Throwable th6) {
                objectRef = objectRef3
                th = th6
                }
            if (file2 != null) {
                file5 = ConnectionManager.receiveDir
                Intrinsics.checkNotNull(file5)
                if (!file5.exists()) {
                    file6 = ConnectionManager.receiveDir
                    Intrinsics.checkNotNull(file6)
                    break
                    }
                filesDir = ConnectionManager.receiveDir
                val file7: File = new File(filesDir, connectionManager$startReceiveFile$1.$fileName)
                file3 = File(filesDir, connectionManager$startReceiveFile$1.$fileName + ".progress")
                Log.i("PhoneHub", "startReceiveFile: outFile=" + file7.getAbsolutePath() + ", progressFile=" + file3.getAbsolutePath())
                j = 0
                if (file3.exists()) {
                    if (file7.exists()) {
                        file7.delete()
                        }
                    val unit2: Unit = Unit.INSTANCE
                    } else {
                    try {
                        j = Long.parseLong(StringsKt.trim((CharSequence) FilesKt.readText$default(file3, null, 1, null)).toString())
                        if (j <= 0 || j >= connectionManager$startReceiveFile$1.$fileSize || !file7.exists() || file7.length() < j) {
                            j = 0
                            file3.delete()
                            if (file7.exists()) {
                                file7.delete()
                                }
                            val unit3: Unit = Unit.INSTANCE
                            } else {
                            Boxing.boxInt(Log.i("PhoneHub", "startReceiveFile: 断点续传 offset=" + j))
                            }
                        } catch (Exception e5) {
                        j = 0
                        file3.delete()
                        if (file7.exists()) {
                            file7.delete()
                            }
                        val unit4: Unit = Unit.INSTANCE
                        }
                    }
                str2 = ConnectionManager.pcIp
                if (str2 == null) {
                    str2 = "192.168.3.9"
                    }
                mutableStateFlow2 = ConnectionManager._currentChannel
                if (mutableStateFlow2.getValue() != ConnectionManager.ChannelType.ADB) {
                    i2 = ConnectionManager.connectPort
                    str3 = "http://127.0.0.1:" + i2
                    } else {
                    i = ConnectionManager.connectPort
                    str3 = "http://" + str2 + ":" + i
                    }
                val url: URL = new URL(str3 + "/api/download_file/" + connectionManager$startReceiveFile$1.$fileId)
                Log.i("PhoneHub", "startReceiveFile: 开始下载 " + url + ", offset=" + j)
                val openConnection: URLConnection = url.openConnection()
                Intrinsics.checkNotNull(openConnection, "null cannot be cast to non-null type java.net.HttpURLConnection")
                objectRef3.element = (HttpURLConnection) openConnection
                val connectionManager9: ConnectionManager = ConnectionManager.INSTANCE
                ConnectionManager.currentConn = (HttpURLConnection) objectRef3.element
                ((HttpURLConnection) objectRef3.element).setConnectTimeout(5000)
                ((HttpURLConnection) objectRef3.element).setReadTimeout(300000)
                val httpURLConnection4: HttpURLConnection = (HttpURLConnection) objectRef3.element
                str4 = ConnectionManager.secretToken
                httpURLConnection4.setRequestProperty("Authorization", "Bearer " + str4)
                ((HttpURLConnection) objectRef3.element).setRequestProperty("Connection", "close")
                if (j > 0) {
                    try {
                        ((HttpURLConnection) objectRef3.element).setRequestProperty("Range", "bytes=" + j + "-")
                        } catch (Exception e6) {
                        exc = e6
                        str = "PhoneHub"
                        objectRef = objectRef3
                        z = ConnectionManager.transferPaused
                        if (!z) {
                            }
                        Boxing.boxInt(Log.w(str, "File receive interrupted (paused/cancelled): " + exc.getMessage()))
                        httpURLConnection2 = (HttpURLConnection) objectRef.element
                        if (httpURLConnection2 != null) {
                            }
                        val connectionManager72: ConnectionManager = ConnectionManager.INSTANCE
                        ConnectionManager.currentConn = null
                        val connectionManager82: ConnectionManager = ConnectionManager.INSTANCE
                        ConnectionManager.transferInProgress = false
                        concurrentHashMap2 = ConnectionManager.fileReceiveState
                        concurrentHashMap2.remove(connectionManager$startReceiveFile$1.$fileId)
                        return Unit.INSTANCE
                        } catch (Throwable th7) {
                        th = th7
                        objectRef = objectRef3
                        httpURLConnection = (HttpURLConnection) objectRef.element
                        if (httpURLConnection != null) {
                            }
                        val connectionManager522: ConnectionManager = ConnectionManager.INSTANCE
                        ConnectionManager.currentConn = null
                        val connectionManager622: ConnectionManager = ConnectionManager.INSTANCE
                        ConnectionManager.transferInProgress = false
                        concurrentHashMap = ConnectionManager.fileReceiveState
                        concurrentHashMap.remove(connectionManager$startReceiveFile$1.$fileId)
                        var th: throw? = null
                        }
                    }
                responseCode = ((HttpURLConnection) objectRef3.element).getResponseCode()
                mutableStateFlow3 = ConnectionManager._currentChannel
                Log.i("PhoneHub", "startReceiveFile: responseCode=" + responseCode + ", channel=" + mutableStateFlow3.getValue() + ", contentLength=" + ((HttpURLConnection) objectRef3.element).getContentLength())
                switch (responseCode) {
                    case ItemTouchHelper.Callback.DEFAULT_DRAG_ANIMATION_DURATION :
                    case 206:
                    try {
                        try {
                            concurrentHashMap3 = ConnectionManager.fileReceiveState
                            fileReceiveState = (ConnectionManager.FileReceiveState) concurrentHashMap3.get(connectionManager$startReceiveFile$1.$fileId)
                            } catch (Throwable th8) {
                            th = th8
                            objectRef = objectRef3
                            }
                        } catch (Exception e7) {
                        str = "PhoneHub"
                        exc = e7
                        objectRef = objectRef3
                        }
                    if (fileReceiveState == null) {
                        Log.e("PhoneHub", "startReceiveFile: fileReceiveState[" + connectionManager$startReceiveFile$1.$fileId + "] 为 null，退出")
                        ConnectionManager.INSTANCE.showToast("接收文件失败: 内部状态错误")
                        mutableStateFlow10 = ConnectionManager._fileTransferProgress
                        mutableStateFlow10.setValue(null)
                        val unit5: Unit = Unit.INSTANCE
                        try {
                            val httpURLConnection5: HttpURLConnection = (HttpURLConnection) objectRef3.element
                            if (httpURLConnection5 != null) {
                                httpURLConnection5.disconnect()
                                val unit6: Unit = Unit.INSTANCE
                                }
                            } catch (Exception e8) {
                            }
                        val connectionManager10: ConnectionManager = ConnectionManager.INSTANCE
                        ConnectionManager.currentConn = null
                        val connectionManager11: ConnectionManager = ConnectionManager.INSTANCE
                        ConnectionManager.transferInProgress = false
                        concurrentHashMap4 = ConnectionManager.fileReceiveState
                        concurrentHashMap4.remove(connectionManager$startReceiveFile$1.$fileId)
                        var unit5: return? = null
                        }
                    Log.i("PhoneHub", "startReceiveFile: 开始读取数据流")
                    val longRef: ?? = new Ref.LongRef()
                    if (responseCode != 206) {
                        j = 0
                        }
                    longRef.element = j
                    try {
                        try {
                            fileOutputStream = FileOutputStream(file7, (responseCode == 206 ? 1 : 0) != 0)
                            str11 = connectionManager$startReceiveFile$1.$fileId
                            str12 = connectionManager$startReceiveFile$1.$fileName
                            str5 = longRef
                            } catch (Throwable th9) {
                            th = th9
                            th = th
                            objectRef = objectRef2
                            httpURLConnection = (HttpURLConnection) objectRef.element
                            if (httpURLConnection != null) {
                                }
                            val connectionManager5222: ConnectionManager = ConnectionManager.INSTANCE
                            ConnectionManager.currentConn = null
                            val connectionManager6222: ConnectionManager = ConnectionManager.INSTANCE
                            ConnectionManager.transferInProgress = false
                            concurrentHashMap = ConnectionManager.fileReceiveState
                            concurrentHashMap.remove(connectionManager$startReceiveFile$1.$fileId)
                            var th: throw? = null
                            }
                        } catch (Exception e9) {
                        r21 = ", paused="
                        str5 = ", cancel="
                        str6 = "/"
                        str7 = "PhoneHub"
                        objectRef2 = objectRef3
                        r4 = file3
                        file4 = file7
                        r2 = longRef
                        exc2 = e9
                        }
                    try {
                        try {
                            val j2: Long = connectionManager$startReceiveFile$1.$fileSize
                            try {
                                try {
                                    val fileOutputStream2: FileOutputStream = fileOutputStream
                                    val obj2: Any = objectRef3.element
                                    Intrinsics.checkNotNull(obj2)
                                    val inputStream: InputStream = ((HttpURLConnection) obj2).getInputStream()
                                    try {
                                        val inputStream2: InputStream = inputStream
                                        objectRef2 = objectRef3
                                        try {
                                            val bArr: Array<Byte> = new byte[65536]
                                            val str18: String = str14
                                            val str19: String = str16
                                            while (true) {
                                                file4 = file7
                                                val inputStream3: InputStream = inputStream2
                                                str13 = str17
                                                try {
                                                    val read: Int = inputStream3.read(bArr)
                                                    if (read != -1) {
                                                        z11 = ConnectionManager.fileTransferCancel
                                                        if (!z11) {
                                                            val fileOutputStream3: FileOutputStream = fileOutputStream2
                                                            val str20: String = str18
                                                            try {
                                                                fileOutputStream3.write(bArr, 0, read)
                                                                val str21: String = str19
                                                                val r22: ?? = str5
                                                                val str22: String = str15
                                                                try {
                                                                    val bArr2: Array<Byte> = bArr
                                                                    val file8: File = file3
                                                                    try {
                                                                        r22.element += read
                                                                        fileReceiveState.setReceived(r22.element)
                                                                        mutableStateFlow9 = ConnectionManager._fileTransferProgress
                                                                        mutableStateFlow9.setValue(new ConnectionManager.TransferProgress(str11, str12, r22.element, j2, true))
                                                                        try {
                                                                            FilesKt.writeText$default(file8, String.valueOf(r22.element), null, 2, null)
                                                                            ConnectionManager.updateFileTransferNotification$default(ConnectionManager.INSTANCE, str12, r22.element, j2, false, 8, null)
                                                                            file3 = file8
                                                                            str15 = str22
                                                                            file7 = file4
                                                                            str19 = str21
                                                                            bArr = bArr2
                                                                            str5 = r22
                                                                            str18 = str20
                                                                            fileOutputStream2 = fileOutputStream3
                                                                            str17 = str13
                                                                            inputStream2 = inputStream3
                                                                            } catch (Throwable th10) {
                                                                            th3 = th10
                                                                            try {
                                                                                var th3: throw? = null
                                                                                } catch (Throwable th11) {
                                                                                CloseableKt.closeFinally(inputStream, th3)
                                                                                var th11: throw? = null
                                                                                }
                                                                            }
                                                                        } catch (Throwable th12) {
                                                                        th3 = th12
                                                                        var th3: throw? = null
                                                                        }
                                                                    } catch (Throwable th13) {
                                                                    th3 = th13
                                                                    }
                                                                } catch (Throwable th14) {
                                                                th = th14
                                                                th3 = th
                                                                var th3: throw? = null
                                                                }
                                                            }
                                                        }
                                                    } catch (Throwable th15) {
                                                    th = th15
                                                    }
                                                }
                                            val str23: String = str18
                                            val str24: String = str19
                                            r4 = file3
                                            r2 = str5
                                            val str25: String = str15
                                            val unit7: Unit = Unit.INSTANCE
                                            CloseableKt.closeFinally(inputStream, null)
                                            val unit8: Unit = Unit.INSTANCE
                                            CloseableKt.closeFinally(fileOutputStream, null)
                                            str9 = str25
                                            str10 = str23
                                            z3 = false
                                            str = str13
                                            str8 = str24
                                            } catch (Throwable th16) {
                                            th3 = th16
                                            }
                                        } catch (Throwable th17) {
                                        th3 = th17
                                        }
                                    } catch (Throwable th18) {
                                    th2 = th18
                                    try {
                                        var th2: throw? = null
                                        } catch (Throwable th19) {
                                        CloseableKt.closeFinally(fileOutputStream, th2)
                                        var th19: throw? = null
                                        }
                                    }
                                } catch (Throwable th20) {
                                th2 = th20
                                var th2: throw? = null
                                }
                            } catch (Exception e10) {
                            r21 = ", paused="
                            str6 = "/"
                            str7 = "PhoneHub"
                            objectRef2 = objectRef3
                            r4 = file3
                            file4 = file7
                            r2 = str5
                            str5 = ", cancel="
                            exc2 = e10
                            z3 = true
                            try {
                                val j3: Long = r2.element
                                val j4: Long = connectionManager$startReceiveFile$1.$fileSize
                                z4 = ConnectionManager.fileTransferCancel
                                z5 = ConnectionManager.transferPaused
                                val message: String = exc2.getMessage()
                                str8 = str6
                                val append: StringBuilder = new StringBuilder().append("startReceiveFile: 流中断 received=").append(j3).append(str8).append(j4)
                                str9 = str5
                                str10 = r21
                                val sb: String = append.append(str9).append(z4).append(str10).append(z5).append(", err=").append(message).toString()
                                str = str7
                                } catch (Exception e11) {
                                e = e11
                                str = str7
                                exc = e
                                objectRef = objectRef2
                                z = ConnectionManager.transferPaused
                                if (!z) {
                                    z2 = ConnectionManager.fileTransferCancel
                                    if (!z2) {
                                        Log.e(str, "File receive failed", exc)
                                        ConnectionManager.INSTANCE.showToast("接收文件异常: " + exc.getMessage())
                                        mutableStateFlow = ConnectionManager._fileTransferProgress
                                        mutableStateFlow.setValue(null)
                                        ConnectionManager.INSTANCE.cancelFileTransferNotification()
                                        val unit9: Unit = Unit.INSTANCE
                                        httpURLConnection2 = (HttpURLConnection) objectRef.element
                                        if (httpURLConnection2 != null) {
                                            httpURLConnection2.disconnect()
                                            val unit10: Unit = Unit.INSTANCE
                                            }
                                        val connectionManager722: ConnectionManager = ConnectionManager.INSTANCE
                                        ConnectionManager.currentConn = null
                                        val connectionManager822: ConnectionManager = ConnectionManager.INSTANCE
                                        ConnectionManager.transferInProgress = false
                                        concurrentHashMap2 = ConnectionManager.fileReceiveState
                                        concurrentHashMap2.remove(connectionManager$startReceiveFile$1.$fileId)
                                        return Unit.INSTANCE
                                        }
                                    }
                                Boxing.boxInt(Log.w(str, "File receive interrupted (paused/cancelled): " + exc.getMessage()))
                                httpURLConnection2 = (HttpURLConnection) objectRef.element
                                if (httpURLConnection2 != null) {
                                    }
                                val connectionManager7222: ConnectionManager = ConnectionManager.INSTANCE
                                ConnectionManager.currentConn = null
                                val connectionManager8222: ConnectionManager = ConnectionManager.INSTANCE
                                ConnectionManager.transferInProgress = false
                                concurrentHashMap2 = ConnectionManager.fileReceiveState
                                concurrentHashMap2.remove(connectionManager$startReceiveFile$1.$fileId)
                                return Unit.INSTANCE
                                }
                            try {
                                Log.w(str, sb)
                                try {
                                    FilesKt.writeText$default(r4, String.valueOf(r2.element), null, 2, null)
                                    } catch (Exception e12) {
                                    }
                                val j5: Long = r2.element
                                val j6: Long = connectionManager$startReceiveFile$1.$fileSize
                                z6 = ConnectionManager.fileTransferCancel
                                z7 = ConnectionManager.transferPaused
                                } catch (Exception e13) {
                                e = e13
                                exc = e
                                objectRef = objectRef2
                                z = ConnectionManager.transferPaused
                                if (!z) {
                                    }
                                Boxing.boxInt(Log.w(str, "File receive interrupted (paused/cancelled): " + exc.getMessage()))
                                httpURLConnection2 = (HttpURLConnection) objectRef.element
                                if (httpURLConnection2 != null) {
                                    }
                                val connectionManager72222: ConnectionManager = ConnectionManager.INSTANCE
                                ConnectionManager.currentConn = null
                                val connectionManager82222: ConnectionManager = ConnectionManager.INSTANCE
                                ConnectionManager.transferInProgress = false
                                concurrentHashMap2 = ConnectionManager.fileReceiveState
                                concurrentHashMap2.remove(connectionManager$startReceiveFile$1.$fileId)
                                return Unit.INSTANCE
                                }
                            try {
                                Log.i(str, "startReceiveFile: 数据流结束, received=" + j5 + str8 + j6 + str9 + z6 + str10 + z7 + ", streamBroken=" + (z3))
                                z8 = ConnectionManager.fileTransferCancel
                                if (z8) {
                                    }
                                z9 = ConnectionManager.transferPaused
                                if (z9) {
                                    }
                                val unit11: Unit = Unit.INSTANCE
                                try {
                                    httpURLConnection3 = (HttpURLConnection) objectRef2.element
                                    if (httpURLConnection3 != null) {
                                        }
                                    } catch (Exception e14) {
                                    }
                                } catch (Exception e15) {
                                e = e15
                                connectionManager$startReceiveFile$1 = this
                                exc = e
                                objectRef = objectRef2
                                z = ConnectionManager.transferPaused
                                if (!z) {
                                    }
                                Boxing.boxInt(Log.w(str, "File receive interrupted (paused/cancelled): " + exc.getMessage()))
                                httpURLConnection2 = (HttpURLConnection) objectRef.element
                                if (httpURLConnection2 != null) {
                                    }
                                val connectionManager722222: ConnectionManager = ConnectionManager.INSTANCE
                                ConnectionManager.currentConn = null
                                val connectionManager822222: ConnectionManager = ConnectionManager.INSTANCE
                                ConnectionManager.transferInProgress = false
                                concurrentHashMap2 = ConnectionManager.fileReceiveState
                                concurrentHashMap2.remove(connectionManager$startReceiveFile$1.$fileId)
                                return Unit.INSTANCE
                                }
                            val connectionManager7222222: ConnectionManager = ConnectionManager.INSTANCE
                            ConnectionManager.currentConn = null
                            val connectionManager8222222: ConnectionManager = ConnectionManager.INSTANCE
                            ConnectionManager.transferInProgress = false
                            concurrentHashMap2 = ConnectionManager.fileReceiveState
                            concurrentHashMap2.remove(connectionManager$startReceiveFile$1.$fileId)
                            return Unit.INSTANCE
                            }
                        } catch (Exception e16) {
                        exc2 = e16
                        r2 = str14
                        r4 = str16
                        z3 = true
                        val j32: Long = r2.element
                        val j42: Long = connectionManager$startReceiveFile$1.$fileSize
                        z4 = ConnectionManager.fileTransferCancel
                        z5 = ConnectionManager.transferPaused
                        val message2: String = exc2.getMessage()
                        str8 = str6
                        val append2: StringBuilder = new StringBuilder().append("startReceiveFile: 流中断 received=").append(j32).append(str8).append(j42)
                        str9 = str5
                        str10 = r21
                        val sb2: String = append2.append(str9).append(z4).append(str10).append(z5).append(", err=").append(message2).toString()
                        str = str7
                        Log.w(str, sb2)
                        FilesKt.writeText$default(r4, String.valueOf(r2.element), null, 2, null)
                        val j52: Long = r2.element
                        val j62: Long = connectionManager$startReceiveFile$1.$fileSize
                        z6 = ConnectionManager.fileTransferCancel
                        z7 = ConnectionManager.transferPaused
                        Log.i(str, "startReceiveFile: 数据流结束, received=" + j52 + str8 + j62 + str9 + z6 + str10 + z7 + ", streamBroken=" + (z3))
                        z8 = ConnectionManager.fileTransferCancel
                        if (z8) {
                            }
                        z9 = ConnectionManager.transferPaused
                        if (z9) {
                            }
                        val unit112: Unit = Unit.INSTANCE
                        httpURLConnection3 = (HttpURLConnection) objectRef2.element
                        if (httpURLConnection3 != null) {
                            }
                        val connectionManager72222222: ConnectionManager = ConnectionManager.INSTANCE
                        ConnectionManager.currentConn = null
                        val connectionManager82222222: ConnectionManager = ConnectionManager.INSTANCE
                        ConnectionManager.transferInProgress = false
                        concurrentHashMap2 = ConnectionManager.fileReceiveState
                        concurrentHashMap2.remove(connectionManager$startReceiveFile$1.$fileId)
                        return Unit.INSTANCE
                        }
                    val j522: Long = r2.element
                    val j622: Long = connectionManager$startReceiveFile$1.$fileSize
                    z6 = ConnectionManager.fileTransferCancel
                    z7 = ConnectionManager.transferPaused
                    try {
                        Log.i(str, "startReceiveFile: 数据流结束, received=" + j522 + str8 + j622 + str9 + z6 + str10 + z7 + ", streamBroken=" + (z3))
                        z8 = ConnectionManager.fileTransferCancel
                        if (z8) {
                            z10 = ConnectionManager.transferPaused
                            if (!z10) {
                                Log.w(str, "startReceiveFile: 传输被取消，删除不完整文件")
                                try {
                                    if (file4.exists()) {
                                        file4.delete()
                                        }
                                    if (r4.exists()) {
                                        r4.delete()
                                        }
                                    } catch (Exception e17) {
                                    Log.w(str, "删除不完整文件失败: " + e17.getMessage())
                                    }
                                mutableStateFlow8 = ConnectionManager._fileTransferProgress
                                mutableStateFlow8.setValue(null)
                                ConnectionManager.INSTANCE.cancelFileTransferNotification()
                                connectionManager$startReceiveFile$1 = this
                                val unit1122: Unit = Unit.INSTANCE
                                httpURLConnection3 = (HttpURLConnection) objectRef2.element
                                if (httpURLConnection3 != null) {
                                    httpURLConnection3.disconnect()
                                    val unit12: Unit = Unit.INSTANCE
                                    }
                                val connectionManager722222222: ConnectionManager = ConnectionManager.INSTANCE
                                ConnectionManager.currentConn = null
                                val connectionManager822222222: ConnectionManager = ConnectionManager.INSTANCE
                                ConnectionManager.transferInProgress = false
                                concurrentHashMap2 = ConnectionManager.fileReceiveState
                                concurrentHashMap2.remove(connectionManager$startReceiveFile$1.$fileId)
                                return Unit.INSTANCE
                                }
                            }
                        z9 = ConnectionManager.transferPaused
                        if (z9) {
                            connectionManager$startReceiveFile$1 = this
                            Log.i(str, "startReceiveFile: 传输已暂停，保留进度 received=" + r2.element + str8 + connectionManager$startReceiveFile$1.$fileSize)
                            resumeInfo2 = ConnectionManager.resumeInfo
                            if (resumeInfo2 != null) {
                                resumeInfo2.setResumeOffset(r2.element)
                                val unit13: Unit = Unit.INSTANCE
                                }
                            mutableStateFlow7 = ConnectionManager._fileTransferProgress
                            mutableStateFlow7.setValue(new ConnectionManager.TransferProgress(connectionManager$startReceiveFile$1.$fileId, connectionManager$startReceiveFile$1.$fileName, r2.element, connectionManager$startReceiveFile$1.$fileSize, true))
                            ConnectionManager.INSTANCE.updateFileTransferNotification(connectionManager$startReceiveFile$1.$fileName, r2.element, connectionManager$startReceiveFile$1.$fileSize, true)
                            } else {
                            connectionManager$startReceiveFile$1 = this
                            if (r2.element >= connectionManager$startReceiveFile$1.$fileSize) {
                                r4.delete()
                                val connectionManager12: ConnectionManager = ConnectionManager.INSTANCE
                                ConnectionManager.resumeInfo = null
                                ConnectionManager.INSTANCE.sendAck(connectionManager$startReceiveFile$1.$fileId)
                                Log.i(str, "startReceiveFile: 下载完成, received=" + r2.element)
                                ConnectionManager.INSTANCE.showToast("文件接收完成: " + connectionManager$startReceiveFile$1.$fileName)
                                mutableStateFlow5 = ConnectionManager._transferCompleted
                                mutableStateFlow5.setValue(Boxing.boxBoolean(true))
                                mutableSharedFlow = ConnectionManager._completedTransfer
                                mutableSharedFlow.tryEmit(new ConnectionManager.CompletedTransfer(connectionManager$startReceiveFile$1.$fileName, false))
                                mutableStateFlow6 = ConnectionManager._fileTransferProgress
                                mutableStateFlow6.setValue(null)
                                ConnectionManager.INSTANCE.completeFileTransferNotification(connectionManager$startReceiveFile$1.$fileName)
                                } else {
                                Log.w(str, "startReceiveFile: 流中断未取消/暂停，视为暂停 received=" + r2.element + str8 + connectionManager$startReceiveFile$1.$fileSize)
                                resumeInfo = ConnectionManager.resumeInfo
                                if (resumeInfo != null) {
                                    resumeInfo.setResumeOffset(r2.element)
                                    val unit14: Unit = Unit.INSTANCE
                                    }
                                mutableStateFlow4 = ConnectionManager._fileTransferProgress
                                mutableStateFlow4.setValue(new ConnectionManager.TransferProgress(connectionManager$startReceiveFile$1.$fileId, connectionManager$startReceiveFile$1.$fileName, r2.element, connectionManager$startReceiveFile$1.$fileSize, true))
                                ConnectionManager.INSTANCE.updateFileTransferNotification(connectionManager$startReceiveFile$1.$fileName, r2.element, connectionManager$startReceiveFile$1.$fileSize, true)
                                }
                            }
                        val unit11222: Unit = Unit.INSTANCE
                        httpURLConnection3 = (HttpURLConnection) objectRef2.element
                        if (httpURLConnection3 != null) {
                            }
                        val connectionManager7222222222: ConnectionManager = ConnectionManager.INSTANCE
                        ConnectionManager.currentConn = null
                        val connectionManager8222222222: ConnectionManager = ConnectionManager.INSTANCE
                        ConnectionManager.transferInProgress = false
                        concurrentHashMap2 = ConnectionManager.fileReceiveState
                        concurrentHashMap2.remove(connectionManager$startReceiveFile$1.$fileId)
                        return Unit.INSTANCE
                        } catch (Throwable th21) {
                        th = th21
                        connectionManager$startReceiveFile$1 = this
                        th = th
                        objectRef = objectRef2
                        httpURLConnection = (HttpURLConnection) objectRef.element
                        if (httpURLConnection != null) {
                            }
                        val connectionManager52222: ConnectionManager = ConnectionManager.INSTANCE
                        ConnectionManager.currentConn = null
                        val connectionManager62222: ConnectionManager = ConnectionManager.INSTANCE
                        ConnectionManager.transferInProgress = false
                        concurrentHashMap = ConnectionManager.fileReceiveState
                        concurrentHashMap.remove(connectionManager$startReceiveFile$1.$fileId)
                        var th: throw? = null
                        }
                    default:
                    objectRef2 = objectRef3
                    str = "PhoneHub"
                    try {
                        Log.e(str, "startReceiveFile: 下载失败 responseCode=" + responseCode)
                        ConnectionManager.INSTANCE.showToast("接收文件失败: HTTP " + responseCode)
                        mutableStateFlow11 = ConnectionManager._fileTransferProgress
                        mutableStateFlow11.setValue(null)
                        ConnectionManager.INSTANCE.cancelFileTransferNotification()
                        val unit15: Unit = Unit.INSTANCE
                        httpURLConnection3 = (HttpURLConnection) objectRef2.element
                        if (httpURLConnection3 != null) {
                            }
                        } catch (Exception e18) {
                        objectRef = objectRef2
                        exc = e18
                        z = ConnectionManager.transferPaused
                        if (!z) {
                            }
                        Boxing.boxInt(Log.w(str, "File receive interrupted (paused/cancelled): " + exc.getMessage()))
                        httpURLConnection2 = (HttpURLConnection) objectRef.element
                        if (httpURLConnection2 != null) {
                            }
                        val connectionManager72222222222: ConnectionManager = ConnectionManager.INSTANCE
                        ConnectionManager.currentConn = null
                        val connectionManager82222222222: ConnectionManager = ConnectionManager.INSTANCE
                        ConnectionManager.transferInProgress = false
                        concurrentHashMap2 = ConnectionManager.fileReceiveState
                        concurrentHashMap2.remove(connectionManager$startReceiveFile$1.$fileId)
                        return Unit.INSTANCE
                        } catch (Throwable th22) {
                        objectRef = objectRef2
                        th = th22
                        httpURLConnection = (HttpURLConnection) objectRef.element
                        if (httpURLConnection != null) {
                            }
                        val connectionManager522222: ConnectionManager = ConnectionManager.INSTANCE
                        ConnectionManager.currentConn = null
                        val connectionManager622222: ConnectionManager = ConnectionManager.INSTANCE
                        ConnectionManager.transferInProgress = false
                        concurrentHashMap = ConnectionManager.fileReceiveState
                        concurrentHashMap.remove(connectionManager$startReceiveFile$1.$fileId)
                        var th: throw? = null
                        }
                    val connectionManager722222222222: ConnectionManager = ConnectionManager.INSTANCE
                    ConnectionManager.currentConn = null
                    val connectionManager822222222222: ConnectionManager = ConnectionManager.INSTANCE
                    ConnectionManager.transferInProgress = false
                    concurrentHashMap2 = ConnectionManager.fileReceiveState
                    concurrentHashMap2.remove(connectionManager$startReceiveFile$1.$fileId)
                    return Unit.INSTANCE
                    }
                }
            context = ConnectionManager.context
            if (context == null || (filesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)) == null) {
                context2 = ConnectionManager.context
                filesDir = context2 != null ? context2.getFilesDir() : null
                }
            if (filesDir != null) {
                Boxing.boxBoolean(filesDir.mkdirs())
                }
            Log.w("PhoneHub", "startReceiveFile: 外部存储不可用，回退到 " + filesDir)
            val file72: File = new File(filesDir, connectionManager$startReceiveFile$1.$fileName)
            file3 = File(filesDir, connectionManager$startReceiveFile$1.$fileName + ".progress")
            Log.i("PhoneHub", "startReceiveFile: outFile=" + file72.getAbsolutePath() + ", progressFile=" + file3.getAbsolutePath())
            j = 0
            if (file3.exists()) {
                }
            str2 = ConnectionManager.pcIp
            if (str2 == null) {
                }
            mutableStateFlow2 = ConnectionManager._currentChannel
            if (mutableStateFlow2.getValue() != ConnectionManager.ChannelType.ADB) {
                }
            val url2: URL = new URL(str3 + "/api/download_file/" + connectionManager$startReceiveFile$1.$fileId)
            Log.i("PhoneHub", "startReceiveFile: 开始下载 " + url2 + ", offset=" + j)
            val openConnection2: URLConnection = url2.openConnection()
            Intrinsics.checkNotNull(openConnection2, "null cannot be cast to non-null type java.net.HttpURLConnection")
            objectRef3.element = (HttpURLConnection) openConnection2
            val connectionManager92: ConnectionManager = ConnectionManager.INSTANCE
            ConnectionManager.currentConn = (HttpURLConnection) objectRef3.element
            ((HttpURLConnection) objectRef3.element).setConnectTimeout(5000)
            ((HttpURLConnection) objectRef3.element).setReadTimeout(300000)
            val httpURLConnection42: HttpURLConnection = (HttpURLConnection) objectRef3.element
            str4 = ConnectionManager.secretToken
            httpURLConnection42.setRequestProperty("Authorization", "Bearer " + str4)
            ((HttpURLConnection) objectRef3.element).setRequestProperty("Connection", "close")
            if (j > 0) {
                }
            responseCode = ((HttpURLConnection) objectRef3.element).getResponseCode()
            mutableStateFlow3 = ConnectionManager._currentChannel
            Log.i("PhoneHub", "startReceiveFile: responseCode=" + responseCode + ", channel=" + mutableStateFlow3.getValue() + ", contentLength=" + ((HttpURLConnection) objectRef3.element).getContentLength())
            switch (responseCode) {
                case ItemTouchHelper.Callback.DEFAULT_DRAG_ANIMATION_DURATION :
                case 206:
                break
                }
            default:
            throw IllegalStateException("call to 'resume' before 'invoke' with coroutine")
            }
        }
    }
