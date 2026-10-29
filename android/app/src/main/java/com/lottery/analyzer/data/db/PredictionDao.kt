package com.lottery.analyzer.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.data.model.PredictionRecord

class PredictionDao(context: Context) {

    private val db: SQLiteDatabase

    init {
        val dbFile = context.getDatabasePath("user_predictions.db")
        dbFile.parentFile?.mkdirs()
        db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        createTableIfNotExists()
    }

    private fun createTableIfNotExists() {
        val sql = """
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
        db.execSQL(sql)
    }

    /**
     * 插入一条新的预测选号记录
     */
    fun insert(record: PredictionRecord): Long {
        val cv = ContentValues().apply {
            put("lottery_type", record.lotteryType.name)
            put("target_issue", record.targetIssue)
            put("red_balls", record.redBalls.joinToString(","))
            put("blue_balls", record.blueBalls.joinToString(","))
            put("strategy", record.strategy)
            put("created_at", record.createdAt)
            put("note", record.note)
        }
        return db.insert("user_predictions", null, cv)
    }

    /**
     * 删除指定单条记录
     */
    fun delete(id: Long): Boolean {
        return db.delete("user_predictions", "id = ?", arrayOf(id.toString())) > 0
    }

    /**
     * 清空指定彩种的所有记录，若 type 为 null 则清空全部
     */
    fun clear(type: LotteryType? = null): Boolean {
        return if (type != null) {
            db.delete("user_predictions", "lottery_type = ?", arrayOf(type.name)) >= 0
        } else {
            db.delete("user_predictions", null, null) >= 0
        }
    }

    /**
     * 查询记录（按创建时间倒序）
     */
    fun getAll(type: LotteryType? = null): List<PredictionRecord> {
        val selection = if (type != null) "lottery_type = ?" else null
        val selectionArgs = if (type != null) arrayOf(type.name) else null
        val cursor = db.query(
            "user_predictions",
            null,
            selection,
            selectionArgs,
            null,
            null,
            "id DESC"
        )
        val list = mutableListOf<PredictionRecord>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(parseRecord(it))
            }
        }
        return list
    }

    private fun parseRecord(cursor: Cursor): PredictionRecord {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
        val typeStr = cursor.getString(cursor.getColumnIndexOrThrow("lottery_type"))
        val lotteryType = try {
            LotteryType.valueOf(typeStr)
        } catch (e: Exception) {
            LotteryType.SSQ
        }
        val targetIssue = cursor.getString(cursor.getColumnIndexOrThrow("target_issue"))
        val redStr = cursor.getString(cursor.getColumnIndexOrThrow("red_balls"))
        val blueStr = cursor.getString(cursor.getColumnIndexOrThrow("blue_balls"))
        val strategy = cursor.getString(cursor.getColumnIndexOrThrow("strategy"))
        val createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at"))
        val note = cursor.getString(cursor.getColumnIndexOrThrow("note")) ?: ""

        val redBalls = redStr.split(",").mapNotNull { it.trim().toIntOrNull() }
        val blueBalls = if (blueStr.isBlank()) emptyList() else blueStr.split(",").mapNotNull { it.trim().toIntOrNull() }

        return PredictionRecord(
            id = id,
            lotteryType = lotteryType,
            targetIssue = targetIssue,
            redBalls = redBalls,
            blueBalls = blueBalls,
            strategy = strategy,
            createdAt = createdAt,
            note = note
        )
    }
}
