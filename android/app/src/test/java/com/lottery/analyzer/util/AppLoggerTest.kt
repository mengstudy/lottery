package com.lottery.analyzer.util

import android.content.ContextWrapper
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppLoggerTest {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun clearSurvivesQueueOverflowAndOldWritesCannotReappear()=runBlocking {
        val directory=temporary.newFolder()
        val context=object:ContextWrapper(null){override fun getFilesDir():File=directory}
        AppLogger.init(context)
        repeat(1200){AppLogger.i("test","old-marker-$it")}
        AppLogger.clearLogs()
        repeat(50){AppLogger.i("test","new-marker-$it")}
        val file=File(directory,"logs/app_runtime.log")
        withTimeout(10000){while(!runCatching{file.readText().contains("new-marker-49")}.getOrDefault(false))delay(20)}
        assertFalse(file.readText().contains("old-marker-"))
        assertTrue(AppLogger.logsFlow.value.size<=500)
    }
}
