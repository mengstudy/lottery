package com.lottery.analyzer.util

import java.io.File

object DiagnosticRetention {
    fun prune(directory: File, maxFiles: Int = 20, maxBytes: Long = 5L * 1024 * 1024, protectedFile: File? = null) {
        val files = directory.listFiles()?.filter { it.isFile && it.name.startsWith("crash_") && it.extension == "log" }
            ?.sortedByDescending { it.lastModified() } ?: return
        var count = 0
        var bytes = 0L
        for (file in files) {
            val keep = file == protectedFile || (count < maxFiles && bytes + file.length() <= maxBytes)
            if (keep) { count++; bytes += file.length() } else file.delete()
        }
    }
}
