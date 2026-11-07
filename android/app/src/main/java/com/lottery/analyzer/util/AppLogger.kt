package com.lottery.analyzer.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
 * 内部后台落盘命令队列动作
 */
private sealed class LogDiskAction {
    data class Write(val content: String) : LogDiskAction()
    object Clear : LogDiskAction()
}

/**
 * 应用全局日志中心：
 * 1. 采用不可变、完全线程安全的 DateTimeFormatter 格式化时间，杜绝 SimpleDateFormat 多线程竞态异常
 * 2. 采用有界队列 (Channel + DROP_OLDEST) 与后台单协程串行落盘，主线程零磁盘 I/O 阻塞
 * 3. 内存环形队列实时更新 StateFlow
 * 4. 运行期间实时控制文件体积不超过上限
 * 5. 清空与写入保序，保留崩溃时同步可靠落盘通道
 */
object AppLogger {
    private const val MAX_MEMORY_LOGS = 500
    private const val MAX_LOG_FILE_SIZE = 2 * 1024 * 1024L // 2MB
    private const val CHANNEL_CAPACITY = 1000 // 有界队列上限，队满时丢弃最旧日志防内存膨胀
    private var logFile: File? = null
    private var idCounter = 0L

    // 不可变且线程安全的时间格式化器 (java.time 原生 API 26+)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val memoryLogs = ArrayDeque<LogEntry>(MAX_MEMORY_LOGS)
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    // 后台串行落盘通道
    private val diskChannel = Channel<LogDiskAction>(
        capacity = CHANNEL_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 启动后台串行落盘协程
        logScope.launch {
            for (action in diskChannel) {
                try {
                    when (action) {
                        is LogDiskAction.Write -> {
                            val file = logFile ?: continue
                            // 运行时文件大小超标检查与自动滚动
                            if (file.exists() && file.length() > MAX_LOG_FILE_SIZE) {
                                file.delete()
                            }
                            FileWriter(file, true).use { fw ->
                                PrintWriter(fw).use { pw ->
                                    pw.print(action.content)
                                }
                            }
                        }
                        is LogDiskAction.Clear -> {
                            val file = logFile
                            if (file != null && file.exists()) {
                                file.delete()
                            }
                        }
                    }
                } catch (_: Throwable) {
                    // 内部吞并文件落盘异常，防止影响业务主流程
                }
            }
        }
    }

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

            if (logFile != null && logFile!!.exists() && logFile!!.length() > MAX_LOG_FILE_SIZE) {
                logFile!!.delete()
            }
            i("AppLogger", "=== 日志系统初始化完成，存储路径: ${logFile?.absolutePath} ===")
        } catch (e: Exception) {
            Log.e("AppLogger", "初始化日志文件失败: ${e.message}")
        }
    }

    private fun log(level: LogLevel, tag: String, message: String, tr: Throwable? = null) {
        val nowMillis = System.currentTimeMillis()
        val nowDateTime = LocalDateTime.now()
        val timeStr = nowDateTime.format(timeFormatter)
        val fullTimeStr = nowDateTime.format(fullDateFormatter)
        val errStr = tr?.stackTraceToString()

        // 1. Android 原生 Logcat
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, tr)
            LogLevel.INFO -> Log.i(tag, message, tr)
            LogLevel.WARN -> Log.w(tag, message, tr)
            LogLevel.ERROR -> Log.e(tag, message, tr)
        }

        // 2. 内存环形队列快速更新 (加轻量锁仅保护内存集合，微秒级操作)
        synchronized(this) {
            idCounter++
            val e = LogEntry(
                id = idCounter,
                timestamp = nowMillis,
                timeFormatted = timeStr,
                level = level,
                tag = tag,
                message = message,
                errorDetail = errStr
            )
            if (memoryLogs.size >= MAX_MEMORY_LOGS) {
                memoryLogs.removeFirst()
            }
            memoryLogs.addLast(e)
            _logsFlow.value = memoryLogs.toList()
        }

        // 3. 非阻塞投递后台落盘通道 (主线程 0 磁盘 I/O 阻塞)
        val contentBuilder = java.lang.StringBuilder()
        contentBuilder.append("[$fullTimeStr] [${level.label}] [$tag]: $message\n")
        if (!errStr.isNullOrBlank()) {
            contentBuilder.append(errStr).append("\n")
        }
        diskChannel.trySend(LogDiskAction.Write(contentBuilder.toString()))
    }

    /**
     * 崩溃异常安全通道：进程消亡前同步写入磁盘
     */
    @Synchronized
    fun writeSyncImmediately(level: LogLevel, tag: String, message: String, tr: Throwable? = null) {
        val fullTimeStr = LocalDateTime.now().format(fullDateFormatter)
        val errStr = tr?.stackTraceToString()
        try {
            val file = logFile ?: return
            FileWriter(file, true).use { fw ->
                PrintWriter(fw).use { pw ->
                    pw.println("[$fullTimeStr] [CRASH-${level.label}] [$tag]: $message")
                    errStr?.let { pw.println(it) }
                }
            }
        } catch (_: Throwable) {}
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
        sb.appendLine("导出时间: ${LocalDateTime.now().format(fullDateFormatter)}")
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
     * 清空内存与本地日志 (与落盘保序投递)
     */
    @Synchronized
    fun clearLogs() {
        memoryLogs.clear()
        _logsFlow.value = emptyList()
        diskChannel.trySend(LogDiskAction.Clear)
        i("AppLogger", "已清空运行日志")
    }
}
