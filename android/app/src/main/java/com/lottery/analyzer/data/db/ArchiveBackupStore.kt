package com.lottery.analyzer.data.db

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lottery.analyzer.data.model.ArchiveBackupRestoreResult
import com.lottery.analyzer.data.model.ArchiveBackupSummary

/**
 * 业务档案导出与迁移恢复存储层
 *
 * 安全与兼容边界：
 * 1. 仅导出与恢复预测记录及记忆归档业务表，严格排除任何 AI 密钥、凭证偏好和本地日志。
 * 2. 导入前强制校验格式、版本与数据结构；写入全程置于单一 SQLite 事务中，异常时自动回滚。
 */
class ArchiveBackupStore(private val sql: ArchiveSql) {
    companion object {
        const val FORMAT_ID = "lottery-archive-backup"
        const val BACKUP_VERSION = 1

        // 严格白名单：仅允许备份业务表，绝不涉及凭证配置
        private val TABLE_COLUMNS = linkedMapOf(
            "collected_batches" to listOf(
                "id", "lottery_type", "target_issue", "origin", "source_label", "created_at_epoch",
                "snapshot_hash", "parser_version", "raw_text", "groups_json", "declared_kind",
                "play_type", "timing_status", "timing_evidence", "baseline_seed", "evaluation_protocol"
            ),
            "ai_request_archive" to listOf(
                "id", "session_id", "lottery_type", "target_issue", "origin", "status",
                "timing_status", "timing_evidence", "created_at_epoch", "response_at_epoch", "updated_at_epoch",
                "provider_name", "endpoint_host", "model_name", "temperature", "requested_count",
                "parsed_count", "accepted_count", "collection_batch_id", "facts_json", "prompt_version",
                "rule_version", "rejection_json", "finish_reason", "failure_category", "baseline_seed", "evaluation_protocol"
            ),
            "ai_archive_payload" to listOf(
                "run_id", "raw_prompt", "raw_response", "prompt_truncated", "response_truncated"
            ),
            "recommendation_entries" to listOf(
                "id", "run_id", "parent_entry_id", "collection_batch_id", "lottery_type", "target_issue",
                "stage", "origin", "ordinal", "accepted_index", "red_json", "blue_json", "kind",
                "play_type", "blue_source", "rejection_reason", "raw_line", "timing_status", "timing_evidence",
                "created_at_epoch", "response_at_epoch", "edited_at_epoch", "confirmed_at_epoch",
                "prediction_id", "strategy_id", "candidate_id", "baseline_order", "baseline_seed",
                "evaluation_protocol", "policy_key"
            ),
            "user_predictions" to listOf(
                "id", "lottery_type", "target_issue", "red_balls", "blue_balls", "strategy", "created_at",
                "note", "prediction_kind", "kl8_play_type", "blue_source", "created_at_epoch",
                "quarantined", "archive_entry_id"
            ),
            "archive_draw_heads" to listOf(
                "lottery_type", "issue", "draw_revision", "rule_version", "source_digest", "updated_at_epoch"
            ),
            "archive_evaluations" to listOf(
                "subject_id", "lottery_type", "issue", "draw_revision", "rule_version", "track", "kind",
                "play_type", "red_count", "blue_count", "strategy_id", "policy_key", "run_id",
                "timing_status", "origin", "red_hits", "blue_hits", "expected_red", "expected_blue",
                "random_red", "random_blue", "constrained_red", "constrained_blue", "constrained_samples",
                "pool_red", "pool_blue", "prospective", "accepted", "numbers_hash", "evaluated_at_epoch"
            ),
            "archive_source_versions" to listOf(
                "lottery_type", "issue", "version"
            ),
            "archive_policy_state" to listOf(
                "lottery_type", "policy_key", "history_periods", "as_of_issue", "weights_json",
                "evidence_digest", "status", "policy_version"
            )
        )

        private val INTEGER_COLUMNS = setOf(
            "created_at_epoch", "response_at_epoch", "updated_at_epoch", "edited_at_epoch",
            "confirmed_at_epoch", "evaluated_at_epoch", "baseline_seed", "requested_count",
            "parsed_count", "accepted_count", "prompt_truncated", "response_truncated",
            "ordinal", "accepted_index", "prediction_id", "baseline_order", "quarantined",
            "red_count", "blue_count", "red_hits", "blue_hits", "constrained_samples",
            "prospective", "accepted", "version", "history_periods"
        )

        private fun isIntegerColumn(tableName: String, col: String): Boolean =
            col in INTEGER_COLUMNS || (tableName == "user_predictions" && col == "id")

        private val REAL_COLUMNS = setOf(
            "temperature", "expected_red", "expected_blue", "random_red", "random_blue",
            "constrained_red", "constrained_blue", "pool_red", "pool_blue"
        )
    }

    private val gson: Gson = GsonBuilder().serializeNulls().create()

    /**
     * 导出全部预测与记忆归档为 JSON 字符串（不包含任何 API Key 或凭证）
     */
    fun exportJson(nowEpoch: Long = System.currentTimeMillis()): String {
        val root = JsonObject().apply {
            addProperty("format", FORMAT_ID)
            addProperty("backup_version", BACKUP_VERSION)
            addProperty("db_schema_version", PredictionSchema.VERSION)
            addProperty("exported_at_epoch", nowEpoch)
            addProperty("excludes_credentials", true)
        }
        val tablesObj = JsonObject()
        for ((table, columns) in TABLE_COLUMNS) {
            val rows = sql.query("SELECT ${columns.joinToString()} FROM $table")
            val arr = JsonArray()
            for (row in rows) {
                val obj = JsonObject()
                for (col in columns) {
                    val raw = row[col]
                    if (raw == null) {
                        obj.add(col, JsonNull.INSTANCE)
                    } else if (isIntegerColumn(table, col)) {
                        obj.addProperty(col, raw.toLongOrNull() ?: 0L)
                    } else if (col in REAL_COLUMNS) {
                        obj.addProperty(col, raw.toDoubleOrNull() ?: 0.0)
                    } else {
                        obj.addProperty(col, raw)
                    }
                }
                arr.add(obj)
            }
            tablesObj.add(table, arr)
        }
        root.add("tables", tablesObj)
        return gson.toJson(root)
    }

    /**
     * 解析并校验备份文件摘要，供 UI 在覆盖恢复前展示确认信息
     */
    fun inspectBackup(jsonText: String): ArchiveBackupSummary {
        val root = parseAndValidateRoot(jsonText)
        val tables = root.getAsJsonObject("tables")
        return ArchiveBackupSummary(
            format = root.get("format").asString,
            backupVersion = root.get("backup_version").asInt,
            dbSchemaVersion = root.get("db_schema_version").asInt,
            exportedAtEpoch = root.get("exported_at_epoch").asLong,
            predictionCount = tables.getAsJsonArray("user_predictions")?.size() ?: 0,
            collectionBatchCount = tables.getAsJsonArray("collected_batches")?.size() ?: 0,
            aiRunCount = tables.getAsJsonArray("ai_request_archive")?.size() ?: 0,
            entryCount = tables.getAsJsonArray("recommendation_entries")?.size() ?: 0,
            excludesCredentials = true
        )
    }

    /**
     * 事务原子恢复备份数据：按外键依赖顺序清理并重建，任何错误自动回滚
     */
    fun restoreFromJson(jsonText: String): ArchiveBackupRestoreResult {
        val root = parseAndValidateRoot(jsonText)
        val tables = root.getAsJsonObject("tables")

        return sql.transaction {
            // 1. 按子表 -> 父表顺序安全清空现有数据
            sql.execute("DELETE FROM user_predictions")
            sql.execute("DELETE FROM archive_evaluations")
            sql.execute("DELETE FROM archive_draw_heads")
            sql.execute("DELETE FROM archive_policy_state")
            sql.execute("DELETE FROM ai_archive_payload")
            sql.execute("UPDATE recommendation_entries SET parent_entry_id = NULL")
            sql.execute("DELETE FROM recommendation_entries")
            sql.execute("DELETE FROM ai_request_archive")
            sql.execute("DELETE FROM collected_batches")
            sql.execute("DELETE FROM archive_source_versions")

            // 2. 先插入无自引用/外键上游父表
            val batchCount = insertTableRows(tables, "collected_batches")
            val runCount = insertTableRows(tables, "ai_request_archive")
            insertTableRows(tables, "ai_archive_payload")

            // 3. 按父级优先顺序插入 recommendation_entries（满足 parent_entry_id 自引用外键）
            val entryCount = insertRecommendationEntriesTopologically(tables.getAsJsonArray("recommendation_entries"))

            // 4. 插入预测记录及关联结算表
            val predCount = insertTableRows(tables, "user_predictions")
            insertTableRows(tables, "archive_draw_heads")
            insertTableRows(tables, "archive_evaluations")
            insertTableRows(tables, "archive_policy_state")

            // 5. 最后覆盖恢复 archive_source_versions，避免触发器改变原始版本号
            sql.execute("DELETE FROM archive_source_versions")
            insertTableRows(tables, "archive_source_versions")

            ArchiveBackupRestoreResult(
                predictionCount = predCount,
                collectionBatchCount = batchCount,
                aiRunCount = runCount,
                entryCount = entryCount
            )
        }
    }

    private fun parseAndValidateRoot(jsonText: String): JsonObject {
        require(jsonText.isNotBlank()) { "备份文件内容为空" }
        val element = runCatching { JsonParser.parseString(jsonText) }.getOrNull()
            ?: throw IllegalArgumentException("备份文件不是有效的 JSON 格式")
        require(element.isJsonObject) { "备份文件格式无效" }
        val root = element.asJsonObject
        val format = root.get("format")?.takeIf { it.isJsonPrimitive }?.asString
        require(format == FORMAT_ID) { "不支持的备份文件类型，请选择本应用导出的 JSON 备份" }
        val backupVer = root.get("backup_version")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
            ?: throw IllegalArgumentException("备份版本号缺失")
        require(backupVer in 1..BACKUP_VERSION) { "备份版本($backupVer)高于当前应用支持范围，请先升级应用" }
        val schemaVer = root.get("db_schema_version")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
            ?: throw IllegalArgumentException("数据库版本信息缺失")
        require(schemaVer <= PredictionSchema.VERSION) { "备份数据库版本($schemaVer)高于当前应用，无法降级恢复" }
        val tables = root.get("tables")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("备份缺少数据表内容")
        require(tables.has("user_predictions") && tables.has("collected_batches") && tables.has("ai_request_archive")) {
            "备份关键业务表不完整，已中止恢复"
        }
        return root
    }

    private fun insertTableRows(tables: JsonObject, tableName: String): Int {
        val arr = tables.get(tableName)?.takeIf { it.isJsonArray }?.asJsonArray ?: return 0
        val allowedCols = TABLE_COLUMNS.getValue(tableName)
        var count = 0
        for (el in arr) {
            require(el.isJsonObject) { "表 $tableName 包含无效记录行" }
            insertSingleRow(tableName, allowedCols, el.asJsonObject)
            count++
        }
        return count
    }

    private fun insertRecommendationEntriesTopologically(arr: JsonArray?): Int {
        if (arr == null || arr.size() == 0) return 0
        val allowedCols = TABLE_COLUMNS.getValue("recommendation_entries")
        val remaining = arr.map {
            require(it.isJsonObject) { "recommendation_entries 包含无效记录行" }
            it.asJsonObject
        }.toMutableList()
        val insertedIds = mutableSetOf<String>()
        var count = 0

        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { obj ->
                val parentId = obj.get("parent_entry_id")?.takeIf { !it.isJsonNull }?.asString
                parentId == null || parentId in insertedIds
            }
            require(ready.isNotEmpty()) { "推荐条目存在断链或循环父级引用，已回滚恢复" }
            for (obj in ready) {
                insertSingleRow("recommendation_entries", allowedCols, obj)
                obj.get("id")?.takeIf { !it.isJsonNull }?.asString?.let { insertedIds.add(it) }
                count++
            }
            remaining.removeAll(ready.toSet())
        }
        return count
    }

    private fun insertSingleRow(tableName: String, allowedCols: List<String>, obj: JsonObject) {
        val colsToInsert = allowedCols.filter { obj.has(it) }
        if (colsToInsert.isEmpty()) return
        val values = colsToInsert.map { col -> jsonValueToSqlArg(tableName, col, obj.get(col)) }
        val sqlText = "INSERT INTO $tableName (${colsToInsert.joinToString()}) VALUES (${colsToInsert.joinToString { "?" }})"
        sql.execute(sqlText, values)
    }

    private fun jsonValueToSqlArg(tableName: String, col: String, element: JsonElement?): Any? {
        if (element == null || element.isJsonNull) return null
        val prim = element.asJsonPrimitive
        return when {
            isIntegerColumn(tableName, col) -> prim.asLong
            col in REAL_COLUMNS -> prim.asDouble
            prim.isBoolean -> if (prim.asBoolean) 1 else 0
            else -> prim.asString
        }
    }
}
