package com.phonehub

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 日志工具类
 * - 同时输出到 Logcat 和应用私有目录文件（Android/data/com.phonehub/files/log/PHlog.txt）
 * - 带详细时间戳，格式: yyyy-MM-dd HH:mm:ss.SSS
 * - 使用单线程写入器避免IO阻塞，超 5MB 自动轮转
 */
object LogUtil {
    private const val TAG = "LogUtil"
    private const val LOG_FILE_NAME = "PHlog.txt"
    private const val LOG_MAX_BYTES = 5L * 1024 * 1024  // 5MB 后轮转，防止无限增长
    private var logFile: File? = null
    private var writer: BufferedWriter? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    // 各模块日志开关
    private var enableAccessibility = true
    private var enableConnectionManager = true
    private var enableScreenCapture = true
    private var enableInput = true
    
    @Volatile
    var isEnabled = false

    fun init(context: Context) {
        try {
            // 使用应用私有外部目录存储日志（含点击坐标、按键等敏感行为数据），
            // 避免写入公共存储根目录被其他应用读取
            val dir = context.getExternalFilesDir("log")
                ?: File(context.filesDir as File, "log")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            logFile = File(dir, LOG_FILE_NAME)
            
            // 追加模式写入
            writer = BufferedWriter(FileWriter(logFile, true)).apply {
                write("\n${"=".repeat(80)}\n")
                write("PhoneHub 日志开始 - ${mainDateFormat.format(Date())}\n")
                write("Android版本: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
                write("设备: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
                write("${"=".repeat(80)}\n\n")
                flush()
            }
            isEnabled = true
            Log.i(TAG, "LogUtil 初始化成功: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "LogUtil 初始化失败", e)
            isEnabled = false
        }
    }

    fun close() {
        try {
            writer?.apply {
                write("\n${"=".repeat(80)}\n")
                write("PhoneHub 日志结束 - ${mainDateFormat.format(Date())}\n")
                write("${"=".repeat(80)}\n")
                flush()
                close()
            }
            writer = null
            executor.shutdown()
            isEnabled = false
        } catch (e: Exception) {
            Log.e(TAG, "LogUtil 关闭失败", e)
        }
    }

    /**
     * 清除所有日志文件
     */
    fun clearLogs() {
        try {
            logFile?.delete()
            Log.i(TAG, "日志文件已清除")
        } catch (e: Exception) {
            Log.e(TAG, "清除日志失败", e)
        }
    }

    /**
     * 获取日志文件大小（字节）
     */
    fun getLogFileSize(): Long {
        return logFile?.length() ?: 0L
    }

    /**
     * 设置各模块日志开关
     */
    fun setModuleEnabled(module: String, enabled: Boolean) {
        when (module.lowercase()) {
            "accessibility" -> enableAccessibility = enabled
            "connection" -> enableConnectionManager = enabled
            "screen" -> enableScreenCapture = enabled
            "input" -> enableInput = enabled
        }
    }

    // ==================== 通用日志 ====================
    
    fun d(tag: String, msg: String, module: String = "general") {
        if (module != "general" && !isModuleEnabled(module)) return
        writeLog(Log.DEBUG, tag, msg, module)
    }

    fun i(tag: String, msg: String, module: String = "general") {
        if (module != "general" && !isModuleEnabled(module)) return
        writeLog(Log.INFO, tag, msg, module)
    }

    fun w(tag: String, msg: String, module: String = "general") {
        if (module != "general" && !isModuleEnabled(module)) return
        writeLog(Log.WARN, tag, msg, module)
    }

    fun e(tag: String, msg: String, module: String = "general") {
        if (module != "general" && !isModuleEnabled(module)) return
        writeLog(Log.ERROR, tag, msg, module)
    }

    fun e(tag: String, msg: String, throwable: Throwable, module: String = "general") {
        if (module != "general" && !isModuleEnabled(module)) return
        writeLog(Log.ERROR, tag, "$msg: ${throwable.message}", module)
        writeLog(Log.ERROR, tag, Log.getStackTraceString(throwable), module)
    }

    /** 判断模块日志是否启用 */
    private fun isModuleEnabled(module: String): Boolean = when (module.lowercase()) {
        "accessibility" -> enableAccessibility
        "connection" -> enableConnectionManager
        "screen" -> enableScreenCapture
        "input" -> enableInput
        else -> true
    }

    // ==================== 模块专用日志方法 ====================

    fun accD(msg: String) { if (enableAccessibility) writeLog(Log.DEBUG, "ACC", msg, "accessibility") }
    fun accI(msg: String) { if (enableAccessibility) writeLog(Log.INFO, "ACC", msg, "accessibility") }
    fun accW(msg: String) { if (enableAccessibility) writeLog(Log.WARN, "ACC", msg, "accessibility") }
    fun accE(msg: String) { if (enableAccessibility) writeLog(Log.ERROR, "ACC", msg, "accessibility") }
    fun accE(msg: String, throwable: Throwable) { if (enableAccessibility) { writeLog(Log.ERROR, "ACC", msg, "accessibility"); writeLog(Log.ERROR, "ACC", Log.getStackTraceString(throwable), "accessibility") } }

    fun connD(msg: String) { if (enableConnectionManager) writeLog(Log.DEBUG, "CONN", msg, "connection") }
    fun connI(msg: String) { if (enableConnectionManager) writeLog(Log.INFO, "CONN", msg, "connection") }
    fun connW(msg: String) { if (enableConnectionManager) writeLog(Log.WARN, "CONN", msg, "connection") }
    fun connE(msg: String) { if (enableConnectionManager) writeLog(Log.ERROR, "CONN", msg, "connection") }
    fun connE(msg: String, throwable: Throwable) { if (enableConnectionManager) { writeLog(Log.ERROR, "CONN", msg, "connection"); writeLog(Log.ERROR, "CONN", Log.getStackTraceString(throwable), "connection") } }

    fun scrD(msg: String) { if (enableScreenCapture) writeLog(Log.DEBUG, "SCR", msg, "screen") }
    fun scrI(msg: String) { if (enableScreenCapture) writeLog(Log.INFO, "SCR", msg, "screen") }
    fun scrW(msg: String) { if (enableScreenCapture) writeLog(Log.WARN, "SCR", msg, "screen") }
    fun scrE(msg: String) { if (enableScreenCapture) writeLog(Log.ERROR, "SCR", msg, "screen") }
    fun scrE(msg: String, throwable: Throwable) { if (enableScreenCapture) { writeLog(Log.ERROR, "SCR", msg, "screen"); writeLog(Log.ERROR, "SCR", Log.getStackTraceString(throwable), "screen") } }

    fun inpD(msg: String) { if (enableInput) writeLog(Log.DEBUG, "INP", msg, "input") }
    fun inpI(msg: String) { if (enableInput) writeLog(Log.INFO, "INP", msg, "input") }
    fun inpW(msg: String) { if (enableInput) writeLog(Log.WARN, "INP", msg, "input") }
    fun inpE(msg: String) { if (enableInput) writeLog(Log.ERROR, "INP", msg, "input") }
    fun inpE(msg: String, throwable: Throwable) { if (enableInput) { writeLog(Log.ERROR, "INP", msg, "input"); writeLog(Log.ERROR, "INP", Log.getStackTraceString(throwable), "input") } }

    // ==================== 内部方法 ====================

    private fun writeLog(level: Int, tag: String, msg: String, module: String) {
        // 输出到Logcat
        android.util.Log.println(level, tag, msg)
        
        // 输出到文件
        if (logFile != null && writer != null) {
            val timestamp = mainDateFormat.format(Date())
            val levelStr = when (level) {
                Log.DEBUG -> "D"
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                else -> "V"
            }
            val logLine = "[$timestamp] [$levelStr/$tag] ($module) $msg"
            
            executor.execute {
                try {
                    // 大小轮转：超过上限时把旧日志改名为 .old 后重开新文件
                    if ((logFile?.length() ?: 0L) > LOG_MAX_BYTES) {
                        try {
                            writer?.flush()
                            writer?.close()
                            val old = File(logFile?.parentFile, "${LOG_FILE_NAME}.old")
                            logFile?.renameTo(old)
                            logFile?.let { writer = BufferedWriter(FileWriter(it, true)) }
                        } catch (_: Exception) {}
                    }
                    writer?.write(logLine)
                    writer?.newLine()
                    writer?.flush()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "LogUtil 写入失败", e)
                }
            }
        }
    }
}
