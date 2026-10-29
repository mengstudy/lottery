package com.lottery.analyzer.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel(val label: String) {
    DEBUG("DEBUG"),
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR")
}

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val timeFormatted: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val errorDetail: String? = null
)

/**
 * 应用全局日志中心，支持内存环形缓冲实时监听与本地文件持久化
 */
object AppLogger {

    private const val MAX_MEMORY_LOGS = 500
    private const val MAX_LOG_FILE_SIZE = 2 * 1024 * 1024 // 2MB
    private var logFile: File? = null
    private var idCounter = 0L

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private val memoryLogs = ArrayDeque<LogEntry>(MAX_MEMORY_LOGS)
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    /**
     * 初始化日志目录
     */
    fun init(context: Context) {
        try {
            val logDir = File(context.filesDir, "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            logFile = File(logDir, "app_runtime.log")

            // 检查文件大小超标则归档清理
            if (logFile != null && logFile!!.exists() && logFile!!.length() > MAX_LOG_FILE_SIZE) {
                logFile!!.delete()
            }
            i("AppLogger", "=== 日志系统初始化完成，存储路径: ${logFile?.absolutePath} ===")
        } catch (e: Exception) {
            Log.e("AppLogger", "初始化日志文件失败: ${e.message}")
        }
    }

    @Synchronized
    private fun log(level: LogLevel, tag: String, message: String, tr: Throwable? = null) {
        val now = System.currentTimeMillis()
        val timeStr = timeFormat.format(Date(now))
        val fullTimeStr = fullDateFormat.format(Date(now))
        val errStr = tr?.stackTraceToString()

        // 1. Android 原生 Logcat
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, tr)
            LogLevel.INFO -> Log.i(tag, message, tr)
            LogLevel.WARN -> Log.w(tag, message, tr)
            LogLevel.ERROR -> Log.e(tag, message, tr)
        }

        // 2. 内存环形队列更新
        idCounter++
        val entry = LogEntry(
            id = idCounter,
            timestamp = now,
            timeFormatted = timeStr,
            level = level,
            tag = tag,
            message = message,
            errorDetail = errStr
        )

        if (memoryLogs.size >= MAX_MEMORY_LOGS) {
            memoryLogs.removeFirst()
        }
        memoryLogs.addLast(entry)
        _logsFlow.value = memoryLogs.toList()

        // 3. 异步/追加写入文件
        try {
            val file = logFile
            if (file != null) {
                FileWriter(file, true).use { fw ->
                    PrintWriter(fw).use { pw ->
                        pw.println("[$fullTimeStr] [${level.label}] [$tag]: $message")
                        errStr?.let { pw.println(it) }
                    }
                }
            }
        } catch (_: Exception) {
            // 忽略写入日志本身的异常
        }
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String, tr: Throwable? = null) = log(LogLevel.WARN, tag, message, tr)
    fun e(tag: String, message: String, tr: Throwable? = null) = log(LogLevel.ERROR, tag, message, tr)

    /**
     * 获取全部日志的格式化纯文本
     */
    @Synchronized
    fun getLogsAsText(): String {
        val sb = StringBuilder()
        sb.appendLine("=== 彩票分析助手运行日志 ===")
        sb.appendLine("导出时间: ${fullDateFormat.format(Date())}")
        sb.appendLine("总日志数: ${memoryLogs.size}")
        sb.appendLine("----------------------------------------")
        for (entry in memoryLogs) {
            sb.append("[${entry.timeFormatted}] [${entry.level.label}] [${entry.tag}]: ${entry.message}\n")
            if (!entry.errorDetail.isNullOrBlank()) {
                sb.append("${entry.errorDetail}\n")
            }
        }
        return sb.toString()
    }

    /**
     * 清空内存与本地日志
     */
    @Synchronized
    fun clearLogs() {
        memoryLogs.clear()
        _logsFlow.value = emptyList()
        try {
            logFile?.let {
                if (it.exists()) it.delete()
            }
            i("AppLogger", "已清空运行日志")
        } catch (_: Exception) {}
    }
}
