package com.lottery.analyzer.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log

object DatabaseHelper {
    private const val TAG = "DatabaseHelper"
    private val dbCache = mutableMapOf<String, SQLiteDatabase>()

    /**
     * 获取指定彩种的 SQLite 数据库实例。
     * 本地不存在则自动创建，绝不覆盖已有数据库，打开后自动校验并初始化必要的表结构与索引。
     */
    @Synchronized
    fun getDatabase(context: Context, dbFileName: String): SQLiteDatabase {
        dbCache[dbFileName]?.let {
            if (it.isOpen) return it
        }

        val dbFile = context.getDatabasePath(dbFileName)
        dbFile.parentFile?.mkdirs()

        val database = SQLiteDatabase.openOrCreateDatabase(dbFile, null)

        // 核心兜底：确保各彩种 lottery_results 表结构和索引必须存在
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
                "user_predictions.db" -> {
                    // 用户预测与选号策略记录库
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS user_predictions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            lottery_type TEXT NOT NULL,
                            target_issue TEXT NOT NULL,
                            red_balls TEXT NOT NULL,
                            blue_balls TEXT NOT NULL,
                            strategy TEXT NOT NULL,
                            created_at TEXT NOT NULL,
                            note TEXT
                        );
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_pred_type_issue ON user_predictions(lottery_type, target_issue);")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_pred_id ON user_predictions(id);")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化表结构失败: $dbFileName", e)
        }
    }
}
