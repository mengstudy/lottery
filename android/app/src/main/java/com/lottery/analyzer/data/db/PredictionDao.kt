package com.lottery.analyzer.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.data.model.PredictionRecord

/**
 * 用户预测与实战选号数据访问对象
 * 统一接入全局应用级共享 DatabaseHelper 单例池，懒加载初始化，杜绝在主线程冷启动建库
 */
class PredictionDao(private val context: Context) {
    private fun getDb(): SQLiteDatabase {
        return DatabaseHelper.getDatabase(context.applicationContext, "user_predictions.db")
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
        return getDb().insert("user_predictions", null, cv)
    }

    /**
     * 删除指定单条记录
     */
    fun delete(id: Long): Boolean {
        return getDb().delete("user_predictions", "id = ?", arrayOf(id.toString())) > 0
    }

    /**
     * 清空指定彩种的所有记录，若 type 为 null 则清空全部
     */
    fun clear(type: LotteryType? = null): Boolean {
        return if (type != null) {
            getDb().delete("user_predictions", "lottery_type = ?", arrayOf(type.name)) >= 0
        } else {
            getDb().delete("user_predictions", null, null) >= 0
        }
    }

    /**
     * 获取指定彩种的记录总数
     */
    fun getCount(type: LotteryType? = null): Int {
        val selection = if (type != null) "lottery_type = ?" else null
        val selectionArgs = if (type != null) arrayOf(type.name) else null
        val cursor = getDb().rawQuery(
            "SELECT COUNT(*) FROM user_predictions" + (if (selection != null) " WHERE $selection" else ""),
            selectionArgs
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }
        return 0
    }

    /**
     * 分页查询预测记录（按 id 倒序），用于有限预览与懒加载
     */
    fun getPaged(type: LotteryType? = null, limit: Int = 20, offset: Int = 0): List<PredictionRecord> {
        val selection = if (type != null) "lottery_type = ?" else null
        val selectionArgs = if (type != null) arrayOf(type.name) else null
        val cursor = getDb().query(
            "user_predictions",
            null,
            selection,
            selectionArgs,
            null,
            null,
            "id DESC",
            "$offset, $limit"
        )
        val list = mutableListOf<PredictionRecord>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(parseRecord(it))
            }
        }
        return list
    }

    /**
     * 查询记录（按 id 倒序）
     */
    fun getAll(type: LotteryType? = null): List<PredictionRecord> {
        val selection = if (type != null) "lottery_type = ?" else null
        val selectionArgs = if (type != null) arrayOf(type.name) else null
        val cursor = getDb().query(
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
