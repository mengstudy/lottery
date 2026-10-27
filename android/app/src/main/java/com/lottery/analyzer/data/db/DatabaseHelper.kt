package com.lottery.analyzer.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object DatabaseHelper {

    private const val TAG = "DatabaseHelper"
    private val dbCache = mutableMapOf<String, SQLiteDatabase>()

    /**
     * 获取指定彩种的 SQLite 数据库实例。如果数据库文件在本地不存在，则从 assets/databases 中拷贝。
     */
    @Synchronized
    fun getDatabase(context: Context, dbFileName: String): SQLiteDatabase {
        dbCache[dbFileName]?.let {
            if (it.isOpen) return it
        }

        val dbFile = context.getDatabasePath(dbFileName)
        if (!dbFile.exists()) {
            copyDatabaseFromAssets(context, dbFileName, dbFile)
        }

        val database = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        dbCache[dbFileName] = database
        return database
    }

    private fun copyDatabaseFromAssets(context: Context, dbFileName: String, targetFile: File) {
        try {
            targetFile.parentFile?.mkdirs()
            val assetPath = "databases/$dbFileName"
            context.assets.open(assetPath).use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var length: Int
                    while (input.read(buffer).also { length = it } > 0) {
                        output.write(buffer, 0, length)
                    }
                    output.flush()
                }
            }
            Log.i(TAG, "已成功从 assets 复制数据库: $dbFileName 到 ${targetFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "从 assets 复制数据库失败: $dbFileName", e)
            // 复制失败时创建空数据库作为降级容错
            SQLiteDatabase.openOrCreateDatabase(targetFile, null).close()
        }
    }
}
