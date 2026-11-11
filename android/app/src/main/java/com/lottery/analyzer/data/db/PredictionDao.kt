package com.lottery.analyzer.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.data.model.*
import com.lottery.analyzer.util.AppLogger

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
        PredictionRules.validate(record)
        val db = getDb()
        return AndroidArchiveSql(db).transaction {
        val archive = AndroidMemoryArchiveStore(context).forDatabase(db)
        val entryId = archive.confirm(record)
        val cv = ContentValues().apply {
            put("lottery_type", record.lotteryType.name)
            put("target_issue", record.targetIssue)
            put("red_balls", record.redBalls.joinToString(","))
            put("blue_balls", record.blueBalls.joinToString(","))
            put("strategy", record.strategy)
            put("created_at", record.createdAt)
            put("note", record.note)
            put("prediction_kind", record.kind.name)
            put("kl8_play_type", record.kl8PlayType?.name)
            put("blue_source", record.blueSource.name)
            put("created_at_epoch", record.createdAtEpoch)
            put("archive_entry_id", entryId)
        }
        val id = db.insertOrThrow("user_predictions", null, cv)
        db.execSQL("UPDATE recommendation_entries SET prediction_id=? WHERE id=?", arrayOf(id, entryId))
        id
        }
    }

    /**
     * 删除指定单条记录
     */
    fun insertAll(records: List<PredictionRecord>): Int {
        require(records.isNotEmpty()) { "没有待保存方案" }
        val db = getDb()
        db.beginTransaction()
        try {
            records.forEach { insert(it) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return records.size
    }

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
        val selection = "quarantined = 0" + if (type != null) " AND lottery_type = ?" else ""
        val selectionArgs = if (type != null) arrayOf(type.name) else null
        val cursor = getDb().rawQuery(
            "SELECT COUNT(*) FROM user_predictions WHERE $selection",
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
        val selection = "quarantined = 0" + if (type != null) " AND lottery_type = ?" else ""
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
                try { list.add(parseRecord(it)) }
                catch (e: Exception) {
                    val id = it.getLong(it.getColumnIndexOrThrow("id"))
                    getDb().update("user_predictions", ContentValues().apply { put("quarantined", 1) }, "id = ?", arrayOf(id.toString()))
                    AppLogger.w("PredictionDao", "已隔离损坏记录 id=$id", e)
                }
            }
        }
        return list
    }

    /**
     * 查询记录（按 id 倒序）
     */
    fun getAll(type: LotteryType? = null): List<PredictionRecord> {
        val selection = "quarantined = 0" + if (type != null) " AND lottery_type = ?" else ""
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
                try { list.add(parseRecord(it)) }
                catch (e: Exception) {
                    val id = it.getLong(it.getColumnIndexOrThrow("id"))
                    getDb().update("user_predictions", ContentValues().apply { put("quarantined", 1) }, "id = ?", arrayOf(id.toString()))
                    AppLogger.w("PredictionDao", "已隔离损坏记录 id=$id", e)
                }
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
            throw IllegalArgumentException("未知彩种：$typeStr", e)
        }
        val targetIssue = cursor.getString(cursor.getColumnIndexOrThrow("target_issue"))
        val redStr = cursor.getString(cursor.getColumnIndexOrThrow("red_balls"))
        val blueStr = cursor.getString(cursor.getColumnIndexOrThrow("blue_balls"))
        val strategy = cursor.getString(cursor.getColumnIndexOrThrow("strategy"))
        val createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at"))
        val note = cursor.getString(cursor.getColumnIndexOrThrow("note")) ?: ""
        val redBalls = redStr.split(",").map { it.trim().toInt() }
        val blueBalls = if (blueStr.isBlank()) emptyList() else blueStr.split(",").map { it.trim().toInt() }
        require(redBalls.isNotEmpty() && redBalls.distinct().size == redBalls.size && redBalls.all { it in 1..lotteryType.maxRedNumber }) { "损坏红球数据" }
        require(blueBalls.distinct().size == blueBalls.size && blueBalls.all { it in 1..lotteryType.maxBlueNumber }) { "损坏蓝球数据" }
        return PredictionRecord(
            id = id,
            lotteryType = lotteryType,
            targetIssue = targetIssue,
            redBalls = redBalls,
            blueBalls = blueBalls,
            strategy = strategy,
            createdAt = createdAt,
            note = note,
            kind = PredictionKind.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("prediction_kind"))),
            kl8PlayType = cursor.getString(cursor.getColumnIndexOrThrow("kl8_play_type"))?.let(Kl8PlayType::valueOf),
            blueSource = BlueSource.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("blue_source"))),
            createdAtEpoch = cursor.getLong(cursor.getColumnIndexOrThrow("created_at_epoch")),
            archiveEntryId = cursor.getString(cursor.getColumnIndexOrThrow("archive_entry_id"))
        )
    }
}
