package com.lottery.analyzer.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object DatabaseHelper {

    private const val TAG = "DatabaseHelper"
    private val dbCache = mutableMapOf<String, SQLiteDatabase>()

    /**
     * 获取指定彩种的 SQLite 数据库实例。如果数据库文件在本地不存在，则从 assets/databases 中拷贝。
     * 打开后自动校验并自愈初始化必要的表结构。
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

        val database = try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            )
        } catch (e: Exception) {
            Log.w(TAG, "打开数据库失败，尝试 openOrCreateDatabase: ${e.message}")
            SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        }

        // 核心兜底自愈：确保各彩种 lottery_results 表结构和索引必须存在
        ensureTablesExist(database, dbFileName)

        dbCache[dbFileName] = database
        return database
    }

    private fun ensureTablesExist(db: SQLiteDatabase, dbFileName: String) {
        try {
            when (dbFileName) {
                "lottery.db" -> {
                    // 双色球 SSQ
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS lottery_results (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            issue TEXT UNIQUE NOT NULL,
                            red_1 INTEGER NOT NULL,
                            red_2 INTEGER NOT NULL,
                            red_3 INTEGER NOT NULL,
                            red_4 INTEGER NOT NULL,
                            red_5 INTEGER NOT NULL,
                            red_6 INTEGER NOT NULL,
                            blue INTEGER NOT NULL,
                            draw_date DATE NOT NULL,
                            region TEXT
                        );
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_ssq_issue ON lottery_results(issue);")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_ssq_draw_date ON lottery_results(draw_date);")
                }
                "dlt_lottery.db" -> {
                    // 大乐透 DLT
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS lottery_results (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            issue TEXT UNIQUE NOT NULL,
                            red_1 INTEGER NOT NULL,
                            red_2 INTEGER NOT NULL,
                            red_3 INTEGER NOT NULL,
                            red_4 INTEGER NOT NULL,
                            red_5 INTEGER NOT NULL,
                            blue_1 INTEGER NOT NULL,
                            blue_2 INTEGER NOT NULL,
                            draw_date DATE NOT NULL,
                            region TEXT
                        );
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_dlt_issue ON lottery_results(issue);")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_dlt_draw_date ON lottery_results(draw_date);")
                }
                "kl8_lottery.db" -> {
                    // 快乐8 KL8
                    val redCols = (1..20).joinToString(", ") { "red_$it INTEGER NOT NULL" }
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS lottery_results (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            issue TEXT UNIQUE NOT NULL,
                            $redCols,
                            draw_date DATE NOT NULL,
                            region TEXT
                        );
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_kl8_issue ON lottery_results(issue);")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_kl8_draw_date ON lottery_results(draw_date);")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化表结构失败: $dbFileName", e)
        }
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
            Log.e(TAG, "从 assets 复制数据库失败（将执行自愈建表）: $dbFileName", e)
            SQLiteDatabase.openOrCreateDatabase(targetFile, null).close()
        }
    }
}
