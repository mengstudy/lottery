package com.lottery.analyzer.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.lottery.analyzer.data.model.LotteryRecord
import com.lottery.analyzer.data.model.LotteryType

class LotteryDao(private val context: Context) {

    /**
     * 获取指定彩种的所有开奖历史记录
     * @param asc 是否按开奖日期/期号升序排列（计算遗漏时需要升序）
     */
    fun getAllRecords(type: LotteryType, asc: Boolean = false): List<LotteryRecord> {
        val db = DatabaseHelper.getDatabase(context, type.dbName)
        val order = if (asc) "draw_date ASC, issue ASC" else "draw_date DESC, issue DESC"
        val cursor = db.query("lottery_results", null, null, null, null, null, order)
        val records = mutableListOf<LotteryRecord>()

        cursor.use {
            while (it.moveToNext()) {
                records.add(parseRecord(type, it))
            }
        }
        return records
    }

    /**
     * 根据期号查询某条记录
     */
    fun getRecordByIssue(type: LotteryType, issue: String): LotteryRecord? {
        val db = DatabaseHelper.getDatabase(context, type.dbName)
        val cursor = db.query(
            "lottery_results",
            null,
            "issue = ?",
            arrayOf(issue),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) parseRecord(type, it) else null
        }
    }

    /**
     * 插入或更新单条开奖记录（用于网络更新同步）
     */
    fun insertOrUpdateRecord(type: LotteryType, record: LotteryRecord): Boolean {
        val db = DatabaseHelper.getDatabase(context, type.dbName)
        val cv = ContentValues().apply {
            put("issue", record.issue)
            put("draw_date", record.drawDate)
            put("region", record.region)
            when (type) {
                LotteryType.SSQ -> {
                    for (i in 0 until 6) {
                        put("red_${i + 1}", record.redBalls.getOrElse(i) { 0 })
                    }
                    put("blue", record.blueBalls.firstOrNull() ?: 0)
                }
                LotteryType.DLT -> {
                    for (i in 0 until 5) {
                        put("red_${i + 1}", record.redBalls.getOrElse(i) { 0 })
                    }
                    put("blue_1", record.blueBalls.getOrElse(0) { 0 })
                    put("blue_2", record.blueBalls.getOrElse(1) { 0 })
                }
                LotteryType.KL8 -> {
                    for (i in 0 until 20) {
                        put("red_${i + 1}", record.redBalls.getOrElse(i) { 0 })
                    }
                }
            }
        }

        val rows = db.update("lottery_results", cv, "issue = ?", arrayOf(record.issue))
        return if (rows <= 0) {
            db.insert("lottery_results", null, cv) > 0
        } else {
            true
        }
    }

    private fun parseRecord(type: LotteryType, cursor: Cursor): LotteryRecord {
        val issue = cursor.getString(cursor.getColumnIndexOrThrow("issue"))
        val drawDate = cursor.getString(cursor.getColumnIndexOrThrow("draw_date"))
        val region = cursor.getColumnIndex("region").takeIf { it >= 0 }?.let { cursor.getString(it) } ?: ""

        val redBalls = mutableListOf<Int>()
        val blueBalls = mutableListOf<Int>()

        when (type) {
            LotteryType.SSQ -> {
                for (i in 1..6) {
                    redBalls.add(cursor.getInt(cursor.getColumnIndexOrThrow("red_$i")))
                }
                blueBalls.add(cursor.getInt(cursor.getColumnIndexOrThrow("blue")))
            }
            LotteryType.DLT -> {
                for (i in 1..5) {
                    redBalls.add(cursor.getInt(cursor.getColumnIndexOrThrow("red_$i")))
                }
                blueBalls.add(cursor.getInt(cursor.getColumnIndexOrThrow("blue_1")))
                blueBalls.add(cursor.getInt(cursor.getColumnIndexOrThrow("blue_2")))
            }
            LotteryType.KL8 -> {
                for (i in 1..20) {
                    redBalls.add(cursor.getInt(cursor.getColumnIndexOrThrow("red_$i")))
                }
            }
        }

        return LotteryRecord(
            issue = issue,
            drawDate = drawDate,
            redBalls = redBalls.sorted(),
            blueBalls = blueBalls.sorted(),
            region = region
        )
    }
}
