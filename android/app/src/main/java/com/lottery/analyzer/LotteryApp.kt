package com.lottery.analyzer

import android.app.Application
import android.content.Context
import com.lottery.analyzer.util.AppLogger
import com.lottery.analyzer.util.CrashHandler

class LotteryApp : Application() {

    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        // 初始化日志系统与崩溃捕获
        AppLogger.init(this)
        CrashHandler.init(this)
        AppLogger.i("LotteryApp", "彩票数据分析助手启动，系统初始化就绪")
    }
}
