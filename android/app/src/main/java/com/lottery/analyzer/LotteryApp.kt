package com.lottery.analyzer

import android.app.Application
import android.content.Context

class LotteryApp : Application() {

    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }
}
