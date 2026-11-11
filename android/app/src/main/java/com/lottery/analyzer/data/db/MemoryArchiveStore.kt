package com.lottery.analyzer.data.db

import com.google.gson.Gson
import com.lottery.analyzer.data.model.*
import java.util.UUID

interface ArchiveSql {
    fun execute(sql: String, args: List<Any?> = emptyList())
    fun query(sql: String, args: List<Any?> = emptyList()): List<Map<String, String?>>
    fun <T> transaction(block: () -> T): T
}

interface MemoryArchiveStore {
    fun begin(start: ArchiveRunStart)
    fun complete(id: String, result: AiRecommendationResult, at: Long): List<String>
    fun finish(id: String, status: ArchiveRunStatus, at: Long, category: String)
    fun saveCollection(snapshot: CollectionSnapshot, at: Long): String
    fun recoverInterrupted(sessionId: String, at: Long)
    fun overview(type: LotteryType): MemoryArchiveOverview
    fun detail(id: String): ArchiveRunDetail?
    fun collection(id: String): ArchiveCollectionDetail?
}

/** SQL shared by Android and real SQLite JVM tests. All writes use stable IDs, never REPLACE. */
class SqlMemoryArchiveStore(
    private val sql: ArchiveSql,
    private val knownDraw: (LotteryType, String) -> Boolean = { _, _ -> false }
) : MemoryArchiveStore {
    private val gson = Gson()
    private fun insert(table: String, fields: Map<String, Any?>) = sql.execute(
        "INSERT INTO $table (${fields.keys.joinToString()}) VALUES (${fields.keys.joinToString { "?" }})", fields.values.toList())
    private fun timing(type: LotteryType, issue: String) = ArchiveTimingPolicy.classify(issue.isNotBlank() && knownDraw(type, issue))
    private fun evidence(value: ArchiveTiming) = if (value == ArchiveTiming.POST_DRAW) "LOCAL_DRAW_PRESENT" else "NO_TRUSTED_DRAW_TIME"
    private fun acceptedIds(id: String) = sql.query("SELECT id FROM accepted_ai_entries WHERE run_id=? ORDER BY accepted_index", listOf(id)).map { it.getValue("id")!! }

    override fun saveCollection(snapshot: CollectionSnapshot, at: Long): String = sql.transaction {
        require(CollectionInputPolicy.error(snapshot.rawText) == null) { CollectionInputPolicy.error(snapshot.rawText)!! }
        require(snapshot.sourceLabel.length <= 100)
        if (snapshot.targetIssue.isNotBlank()) PredictionRules.validateTarget(snapshot.targetIssue)
        val json = ArchivePayloadCodec.encodeGroups(snapshot.groups)
        val hash = ArchivePayloadCodec.collectionHash(snapshot)
        val legacyHash = ArchivePayloadCodec.legacyCollectionHash(snapshot)
        val existing = sql.query("SELECT id FROM collected_batches WHERE lottery_type=? AND target_issue=? AND origin=? AND snapshot_hash IN (?,?)",
            listOf(snapshot.lotteryType.name, snapshot.targetIssue, snapshot.origin.name, hash, legacyHash)).firstOrNull()
        if (existing != null) return@transaction existing.getValue("id")!!
        val id = UUID.randomUUID().toString()
        val eligibility = timing(snapshot.lotteryType, snapshot.targetIssue)
        insert("collected_batches", linkedMapOf("id" to id, "lottery_type" to snapshot.lotteryType.name,
            "target_issue" to snapshot.targetIssue, "origin" to snapshot.origin.name, "source_label" to snapshot.sourceLabel,
            "created_at_epoch" to at, "snapshot_hash" to hash, "parser_version" to snapshot.parserVersion,
            "raw_text" to snapshot.rawText, "groups_json" to json, "declared_kind" to snapshot.declaredKind.name,
            "play_type" to snapshot.kl8PlayType?.name, "timing_status" to eligibility.name, "timing_evidence" to evidence(eligibility)))
        id
    }

    override fun begin(start: ArchiveRunStart) = sql.transaction {
        require(start.id.isNotBlank() && start.sessionId.isNotBlank() && start.facts.requestedCount in 1..20)
        require(start.facts.lotteryType == start.lotteryType && start.facts.targetIssue == start.targetIssue)
        require(start.collection == null || (start.collection.lotteryType == start.lotteryType && start.collection.targetIssue == start.targetIssue))
        val factsJson = ArchivePayloadCodec.encodeFacts(start.facts)
        require(factsJson.length <= 512000) { "归档事实快照过大" }
        val batch = start.collection?.let { saveCollection(it, start.createdAt) }
        val eligibility = timing(start.lotteryType, start.targetIssue)
        insert("ai_request_archive", linkedMapOf("id" to start.id, "session_id" to start.sessionId,
            "lottery_type" to start.lotteryType.name, "target_issue" to start.targetIssue, "origin" to start.origin.name,
            "status" to ArchiveRunStatus.PENDING.name, "timing_status" to eligibility.name, "timing_evidence" to evidence(eligibility),
            "created_at_epoch" to start.createdAt, "updated_at_epoch" to start.createdAt,
            "provider_name" to start.provider, "endpoint_host" to start.endpointHost, "model_name" to start.model,
            "temperature" to start.temperature, "requested_count" to start.facts.requestedCount,
            "collection_batch_id" to batch, "facts_json" to factsJson, "prompt_version" to "AIv2", "rule_version" to "guardrails-v1"))
        val prompt = ArchivePayloadCodec.encodePrompt(start.prompt)
        insert("ai_archive_payload", linkedMapOf("run_id" to start.id, "raw_prompt" to prompt.take(65536), "prompt_truncated" to if (prompt.length > 65536) 1 else 0))
    }

    override fun complete(id: String, result: AiRecommendationResult, at: Long): List<String> = sql.transaction {
        val run = requireNotNull(sql.query("SELECT * FROM ai_request_archive WHERE id=?", listOf(id)).firstOrNull()) { "请求档案不存在" }
        if (run["status"] != ArchiveRunStatus.PENDING.name) return@transaction acceptedIds(id)
        val type = LotteryType.valueOf(run.getValue("lottery_type")!!)
        require(type == result.lotteryType && run["target_issue"] == result.targetIssue) { "返回结果与归档请求不一致" }
        val eligibility = timing(type, result.targetIssue)
        val audits = result.candidateAudit.ifEmpty {
            // Compatibility for injected/alternate clients that return already validated groups.
            result.recommendedBets.mapIndexed { index, bet -> ArchivedCandidate(index + 1, "", bet.first, bet.second, acceptedIndex = index) }
        }
        val validated = audits.filter { it.acceptedIndex != null && it.rejection == null }.sortedBy { it.acceptedIndex }
        require(validated.map { it.acceptedIndex } == validated.indices.toList()) { "保留方案编号不连续" }
        require(validated.map { it.reds to it.blues } == result.recommendedBets) { "原始方案与保留方案关联不完整" }
        validated.forEach {
            PredictionRules.validate(PredictionDraft(type, requireNotNull(it.reds), requireNotNull(it.blues), "AI归档",
                PredictionKind.SINGLE, result.kl8PlayType, BlueSource.PROVIDED))
        }
        for (candidate in audits) {
            val entryId = UUID.randomUUID().toString()
            insert("recommendation_entries", linkedMapOf("id" to entryId, "run_id" to id,
                "collection_batch_id" to run["collection_batch_id"], "lottery_type" to type.name,
                "target_issue" to result.targetIssue, "stage" to "AI_ORIGINAL", "origin" to run["origin"],
                "ordinal" to candidate.ordinal, "accepted_index" to candidate.acceptedIndex,
                "red_json" to candidate.reds?.let(gson::toJson), "blue_json" to candidate.blues?.let(gson::toJson),
                "kind" to PredictionKind.SINGLE.name, "play_type" to result.kl8PlayType?.name,
                "blue_source" to BlueSource.PROVIDED.name, "rejection_reason" to candidate.rejection,
                "raw_line" to candidate.rawLine.take(2048), "timing_status" to eligibility.name,
                "timing_evidence" to evidence(eligibility), "created_at_epoch" to run["created_at_epoch"]!!.toLong(), "response_at_epoch" to at))
        }
        val status = if (result.recommendedBets.size < run["requested_count"]!!.toInt() || result.finishReason == "length") ArchiveRunStatus.PARTIAL else ArchiveRunStatus.COMPLETED
        sql.execute("UPDATE ai_request_archive SET status=?,response_at_epoch=?,updated_at_epoch=?,parsed_count=?,accepted_count=?,rejection_json=?,finish_reason=?,timing_status=?,timing_evidence=? WHERE id=? AND status='PENDING'",
            listOf(status.name, at, at, result.parsedCount, result.recommendedBets.size, gson.toJson(result.rejectionReasons), result.finishReason, eligibility.name, evidence(eligibility), id))
        sql.execute("UPDATE ai_archive_payload SET raw_response=?,response_truncated=? WHERE run_id=?",
            listOf(result.rawResponse.take(65536), if (result.rawResponse.length > 65536) 1 else 0, id))
        acceptedIds(id)
    }

    override fun finish(id: String, status: ArchiveRunStatus, at: Long, category: String) {
        require(status in listOf(ArchiveRunStatus.FAILED, ArchiveRunStatus.CANCELLED, ArchiveRunStatus.INTERRUPTED))
        sql.execute("UPDATE ai_request_archive SET status=?,updated_at_epoch=?,failure_category=? WHERE id=? AND status='PENDING'", listOf(status.name, at, category, id))
    }

    override fun recoverInterrupted(sessionId: String, at: Long) {
        sql.execute("UPDATE ai_request_archive SET status='INTERRUPTED',updated_at_epoch=?,failure_category='PROCESS_INTERRUPTED_REMOTE_UNKNOWN' WHERE status='PENDING' AND session_id<>?", listOf(at, sessionId))
    }

    /** Called in the same transaction as prediction insertion. Original entries are never edited. */
    fun confirm(record: PredictionRecord): String {
        require(record.collectionSnapshot == null || (record.collectionSnapshot.lotteryType == record.lotteryType && record.collectionSnapshot.targetIssue == record.targetIssue))
        val parent = record.archiveParentEntryId?.let { parentId ->
            requireNotNull(sql.query("SELECT * FROM accepted_ai_entries WHERE id=?", listOf(parentId)).firstOrNull()) { "找不到AI原始方案档案" }
        }
        require(parent == null || parent["lottery_type"] == record.lotteryType.name) { "归档来源彩种不一致" }
        val eligibility = timing(record.lotteryType, record.targetIssue)
        val collection = parent?.get("collection_batch_id") ?: record.collectionSnapshot?.let { saveCollection(it, record.createdAtEpoch) }
        val id = UUID.randomUUID().toString()
        val changed = parent != null && (parent["red_json"] != gson.toJson(record.redBalls) || parent["blue_json"] != gson.toJson(record.blueBalls) || parent["target_issue"] != record.targetIssue)
        insert("recommendation_entries", linkedMapOf("id" to id, "run_id" to parent?.get("run_id"),
            "parent_entry_id" to record.archiveParentEntryId, "collection_batch_id" to collection,
            "lottery_type" to record.lotteryType.name, "target_issue" to record.targetIssue,
            "stage" to "USER_FINAL", "origin" to (parent?.get("origin") ?: ArchiveOrigin.REALTIME_RECOMMEND.name), "ordinal" to 0,
            "red_json" to gson.toJson(record.redBalls), "blue_json" to gson.toJson(record.blueBalls),
            "kind" to record.kind.name, "play_type" to record.kl8PlayType?.name, "blue_source" to record.blueSource.name,
            "timing_status" to eligibility.name, "timing_evidence" to evidence(eligibility),
            "created_at_epoch" to record.createdAtEpoch, "edited_at_epoch" to (record.archiveEditedAtEpoch ?: record.createdAtEpoch.takeIf { changed }),
            "confirmed_at_epoch" to record.createdAtEpoch))
        return id
    }

    override fun overview(type: LotteryType): MemoryArchiveOverview {
        fun count(table: String, extra: String = "") = sql.query("SELECT COUNT(*) AS n FROM $table WHERE lottery_type=? $extra", listOf(type.name)).single().getValue("n")!!.toInt()
        val rows = sql.query("SELECT id,target_issue,status,created_at_epoch,requested_count,accepted_count,timing_status FROM ai_request_archive WHERE lottery_type=? ORDER BY created_at_epoch DESC,id DESC LIMIT 10", listOf(type.name))
        val batches = sql.query("SELECT id,target_issue,source_label,groups_json FROM collected_batches WHERE lottery_type=? ORDER BY created_at_epoch DESC,id DESC LIMIT 5", listOf(type.name))
        return MemoryArchiveOverview(count("collected_batches"), count("ai_request_archive"),
            count("recommendation_entries", "AND stage='AI_ORIGINAL'"), count("recommendation_entries", "AND stage='USER_FINAL'"),
            rows.map { ArchiveRunSummary(it["id"]!!, it["target_issue"]!!, it["status"]!!, it["created_at_epoch"]!!.toLong(), it["requested_count"]!!.toInt(), it["accepted_count"]!!.toInt(), it["timing_status"]!!) },
            batches.map { ArchiveCollectionSummary(it["id"]!!,it["target_issue"]!!,it["source_label"]!!,ArchivePayloadCodec.decodeGroups(it["groups_json"]!!).size) })
    }

    override fun collection(id: String): ArchiveCollectionDetail? {
        val batch = sql.query("SELECT * FROM collected_batches WHERE id=?", listOf(id)).firstOrNull() ?: return null
        val groups = ArchivePayloadCodec.decodeGroups(batch["groups_json"]!!)
        return ArchiveCollectionDetail(ArchiveCollectionSummary(id,batch["target_issue"]!!,batch["source_label"]!!,groups.size),batch["raw_text"],groups)
    }

    override fun detail(id: String): ArchiveRunDetail? {
        val run = sql.query("SELECT id,target_issue,provider_name,model_name,status,finish_reason,rejection_json FROM ai_request_archive WHERE id=?", listOf(id)).firstOrNull() ?: return null
        val entries = sql.query("SELECT stage,red_json,blue_json,rejection_reason,timing_status,edited_at_epoch FROM recommendation_entries WHERE run_id=? ORDER BY created_at_epoch,ordinal LIMIT 200", listOf(id))
        fun numbers(raw: String?) = raw?.let { gson.fromJson(it, Array<Int>::class.java).toList() }
        return ArchiveRunDetail(id, run["target_issue"]!!, run["provider_name"]!!, run["model_name"]!!, run["status"]!!,
            run["finish_reason"], run["rejection_json"]!!, entries.map { ArchiveEntrySummary(it["stage"]!!,
                numbers(it["red_json"]), numbers(it["blue_json"]), it["rejection_reason"], it["timing_status"]!!, it["edited_at_epoch"] != null) })
    }

    /** Only optional payloads of old terminal runs are reclaimed. Structured facts/entries remain. */
    fun prunePayloads(keep: Int = 100) {
        require(keep >= 0)
        sql.execute("""DELETE FROM ai_archive_payload WHERE run_id IN (
            SELECT id FROM ai_request_archive WHERE status IN ('FAILED','CANCELLED','INTERRUPTED')
            ORDER BY created_at_epoch DESC LIMIT -1 OFFSET ?)""", listOf(keep))
        // Successful unsettled plans are protected; Phase 2 will add settled-plan reclamation.
    }
}
