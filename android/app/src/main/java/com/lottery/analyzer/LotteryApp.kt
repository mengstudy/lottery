package com.lottery.analyzer

import android.app.Application
import android.content.Context
import com.lottery.analyzer.util.AppLogger
import com.lottery.analyzer.util.CrashHandler
import com.lottery.analyzer.util.DiagnosticRetention
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
            AppLogger.i("LotteryApp", "彩票数据分析助手启动，系统初始化就绪")
        }
    }
}
