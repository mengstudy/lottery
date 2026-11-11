package com.lottery.analyzer.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.lottery.analyzer.data.model.*
import java.util.UUID

object ArchiveProcessSession { val id: String = UUID.randomUUID().toString() }

class AndroidArchiveSql(private val db: SQLiteDatabase) : ArchiveSql {
    override fun execute(sql: String, args: List<Any?>) {
        if (args.isEmpty()) db.execSQL(sql) else db.execSQL(sql, args.toTypedArray())
    }
    override fun query(sql: String, args: List<Any?>): List<Map<String, String?>> =
        db.rawQuery(sql, args.map { it?.toString() }.toTypedArray()).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.columnNames.associateWith { name ->
                val index = cursor.getColumnIndexOrThrow(name)
                if (cursor.isNull(index)) null else cursor.getString(index)
            }) }
        }
    override fun <T> transaction(block: () -> T): T {
        if (db.inTransaction()) return block()
        db.beginTransaction()
        return try { block().also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
    }
}

class AndroidMemoryArchiveStore(context: Context) : MemoryArchiveStore {
    private val app = context.applicationContext
    private fun store(): SqlMemoryArchiveStore = forDatabase(DatabaseHelper.getDatabase(app, "user_predictions.db"))
    fun forDatabase(db: SQLiteDatabase) = SqlMemoryArchiveStore(AndroidArchiveSql(db)) { type, issue ->
        val draws = DatabaseHelper.getDatabase(app, type.dbName)
        draws.rawQuery("SELECT 1 FROM lottery_results WHERE issue=? LIMIT 1", arrayOf(issue)).use { it.moveToFirst() }
    }
    override fun begin(start: ArchiveRunStart) = store().begin(start)
    override fun complete(id: String, result: AiRecommendationResult, at: Long) = store().complete(id, result, at)
    override fun finish(id: String, status: ArchiveRunStatus, at: Long, category: String) = store().finish(id, status, at, category)
    override fun saveCollection(snapshot: CollectionSnapshot, at: Long) = store().saveCollection(snapshot, at)
    override fun recoverInterrupted(sessionId: String, at: Long) = store().recoverInterrupted(sessionId, at)
    override fun overview(type: LotteryType) = store().overview(type)
    override fun detail(id: String) = store().detail(id)
    override fun collection(id: String) = store().collection(id)
    fun prunePayloads() = store().prunePayloads()
}
