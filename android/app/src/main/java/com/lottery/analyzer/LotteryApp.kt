package com.lottery.analyzer

import android.app.Application
import android.content.Context
import com.lottery.analyzer.util.AppLogger
import com.lottery.analyzer.util.CrashHandler
import com.lottery.analyzer.util.DiagnosticRetention
import com.lottery.analyzer.data.db.AndroidMemoryArchiveStore
import com.lottery.analyzer.data.db.ArchiveProcessSession
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LotteryApp : Application() {

    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        // 初始化日志系统与崩溃捕获
        CrashHandler.init(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            AppLogger.init(this@LotteryApp)
            DiagnosticRetention.prune(File(filesDir, "crash"))
            try {
                val archive = AndroidMemoryArchiveStore(this@LotteryApp)
                archive.recoverInterrupted(ArchiveProcessSession.id, System.currentTimeMillis())
                archive.prunePayloads()
            } catch (e: Exception) {
                AppLogger.w("MemoryArchive", "归档恢复未完成，后续请求会再次校验存储", e)
            }
            AppLogger.i("LotteryApp", "彩票数据分析助手启动，系统初始化就绪")
        }
    }
}
