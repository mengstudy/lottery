package com.lottery.analyzer.util

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局未捕获异常处理器
 */
class CrashHandler private constructor(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    companion object {
        fun init(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        AppLogger.e("CrashHandler", "FATAL EXCEPTION in thread '${thread.name}': ${throwable.message}", throwable)

        try {
            val crashDir = File(context.filesDir, "crash")
            if (!crashDir.exists()) crashDir.mkdirs()
            val crashFile = File(crashDir, "crash_${System.currentTimeMillis()}.log")
            FileWriter(crashFile).use { fw ->
                PrintWriter(fw).use { pw ->
                    pw.println("Time: $timeStr")
                    pw.println("Thread: ${thread.name}")
                    pw.println("Message: ${throwable.message}")
                    throwable.printStackTrace(pw)
                }
            }
        } catch (_: Exception) {}

        defaultHandler?.uncaughtException(thread, throwable)
    }
}
