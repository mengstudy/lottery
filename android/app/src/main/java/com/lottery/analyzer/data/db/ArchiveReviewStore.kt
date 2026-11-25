package com.lottery.analyzer.data.db

import com.google.gson.Gson
import com.lottery.analyzer.data.engine.LotteryAnalysisEngine
import com.lottery.analyzer.data.model.*
import java.math.BigInteger

/**
 * 逐期开奖复盘数据聚合查询
 * 沿用现有归档记录与结算数据（archive_evaluations / archive_draw_heads），不重复建设底座
 */
class ArchiveReviewStore(
    private val sql: ArchiveSql,
    private val drawLookup: (LotteryType, List<String>) -> Map<String, LotteryRecord>
) {
    private val gson = Gson()

    private fun parseNumbers(raw: String?): List<Int> = runCatching {
        raw?.let { gson.fromJson(it, Array<Int>::class.java).toList() } ?: emptyList()
    }.getOrDefault(emptyList())

    /**
     * 获取可供复盘的期号列表（汇总收集批次、AI推演、用户保存及本地预测记录）
     */
    fun reviewOptions(type: LotteryType, limit: Int = 50): List<IssueReviewOption> {
        val collectionIssues = sql.query(
            "SELECT DISTINCT target_issue FROM collected_batches WHERE lottery_type=?",
            listOf(type.name)
        ).mapNotNull { it["target_issue"] }.toSet()

        val aiRunIssues = sql.query(
            "SELECT DISTINCT target_issue FROM ai_request_archive WHERE lottery_type=?",
            listOf(type.name)
        ).mapNotNull { it["target_issue"] }.toSet()

        val entryIssues = sql.query(
            "SELECT DISTINCT target_issue, stage FROM recommendation_entries WHERE lottery_type=?",
            listOf(type.name)
        )
        val userFinalIssues = entryIssues
            .filter { it["stage"] == "USER_FINAL" }
            .mapNotNull { it["target_issue"] }
            .toMutableSet()

        val allRawIssues = mutableSetOf<String>()
        allRawIssues.addAll(collectionIssues)
        allRawIssues.addAll(aiRunIssues)
        allRawIssues.addAll(entryIssues.mapNotNull { it["target_issue"] })

        // 兼容未写入 recommendation_entries 或独立存在的 user_predictions 记录
        runCatching {
            val predIssues = sql.query(
                "SELECT DISTINCT target_issue FROM user_predictions WHERE quarantined=0 AND lottery_type=?",
                listOf(type.name)
            ).mapNotNull { it["target_issue"] }
            allRawIssues.addAll(predIssues)
            userFinalIssues.addAll(predIssues)
        }

        val sortedIssues = allRawIssues
            .filter { it.isNotBlank() && it.all { c -> c in '0'..'9' } }
            .sortedWith(compareByDescending<String> { it.toBigIntegerOrNull() ?: BigInteger.ZERO }.thenByDescending { it })
            .take(limit)

        if (sortedIssues.isEmpty()) return emptyList()
        val draws = drawLookup(type, sortedIssues)

        return sortedIssues.map { issue ->
            val draw = draws[issue]
            IssueReviewOption(
                issue = issue,
                hasDraw = draw != null,
                drawDate = draw?.drawDate,
                hasCollection = issue in collectionIssues,
                hasAiRun = issue in aiRunIssues,
                hasUserFinal = issue in userFinalIssues
            )
        }
    }

    /**
     * 获取指定期号的逐期复盘完整对照数据
     */
    fun issueReview(type: LotteryType, issue: String): IssueReviewDetail {
        val drawRecord = drawLookup(type, listOf(issue))[issue]
        val activeHead = sql.query(
            "SELECT draw_revision, rule_version FROM archive_draw_heads WHERE lottery_type=? AND issue=?",
            listOf(type.name, issue)
        ).firstOrNull()

        val drawRevision = activeHead?.get("draw_revision")
        val ruleVersion = activeHead?.get("rule_version")

        val evalMap: Map<String, Pair<Int, Int>> = if (drawRevision != null && ruleVersion != null) {
            sql.query(
                "SELECT subject_id, red_hits, blue_hits FROM archive_evaluations WHERE lottery_type=? AND issue=? AND draw_revision=? AND rule_version=?",
                listOf(type.name, issue, drawRevision, ruleVersion)
            ).associate { row ->
                row.getValue("subject_id")!! to (
                    (row["red_hits"]?.toIntOrNull() ?: 0) to (row["blue_hits"]?.toIntOrNull() ?: 0)
                )
            }
        } else {
            emptyMap()
        }

        // 1. 收集号码批次
        val collectedItems = mutableListOf<IssueReviewItem>()
        val batches = sql.query(
            "SELECT * FROM collected_batches WHERE lottery_type=? AND target_issue=? ORDER BY created_at_epoch DESC",
            listOf(type.name, issue)
        )
        for (batch in batches) {
            val batchId = batch.getValue("id")!!
            val sourceLabel = batch["source_label"].orEmpty().ifBlank { "手工收集" }
            val kind = runCatching { PredictionKind.valueOf(batch["declared_kind"] ?: "REFERENCE") }
                .getOrDefault(PredictionKind.REFERENCE)
            val playType = batch["play_type"]?.let { runCatching { Kl8PlayType.valueOf(it) }.getOrNull() }
            val timingStatus = batch["timing_status"] ?: ArchiveTiming.INSUFFICIENT_TIMING_EVIDENCE.name
            val createdAt = batch["created_at_epoch"]?.toLongOrNull() ?: 0L
            val groups = runCatching { ArchivePayloadCodec.decodeGroups(batch["groups_json"]!!) }.getOrDefault(emptyList())

            groups.forEachIndexed { idx, (reds, blues) ->
                val subjectId = "B:$batchId:$idx"
                collectedItems.add(
                    buildItem(
                        id = subjectId,
                        stage = "COLLECTED",
                        label = "$sourceLabel (#${idx + 1})",
                        type = type,
                        issue = issue,
                        reds = reds.sorted(),
                        blues = blues.sorted(),
                        kind = kind,
                        playType = playType,
                        timingStatus = timingStatus,
                        drawRecord = drawRecord,
                        settledHits = evalMap[subjectId],
                        createdAtEpoch = createdAt
                    )
                )
            }
        }

        // 读取 AI 请求元信息，用于丰富展示标签
        val runMetaMap = sql.query(
            "SELECT id, provider_name, model_name FROM ai_request_archive WHERE lottery_type=? AND target_issue=?",
            listOf(type.name, issue)
        ).associate { it.getValue("id")!! to "${it["provider_name"]}/${it["model_name"]}" }

        // 2. 推荐条目（本地候选、AI精选/原始、用户确认）
        val entries = sql.query(
            "SELECT * FROM recommendation_entries WHERE lottery_type=? AND target_issue=? ORDER BY created_at_epoch ASC, ordinal ASC",
            listOf(type.name, issue)
        )

        val localCandidates = mutableListOf<IssueReviewItem>()
        val aiOriginalItems = mutableListOf<IssueReviewItem>()
        val userFinalItems = mutableListOf<IssueReviewItem>()
        val entryById = mutableMapOf<String, IssueReviewItem>()

        for (row in entries) {
            val id = row.getValue("id")!!
            val stage = row.getValue("stage")!!
            val reds = parseNumbers(row["red_json"]).sorted()
            val blues = parseNumbers(row["blue_json"]).sorted()
            val kind = runCatching { PredictionKind.valueOf(row["kind"] ?: "SINGLE") }.getOrDefault(PredictionKind.SINGLE)
            val playType = row["play_type"]?.let { runCatching { Kl8PlayType.valueOf(it) }.getOrNull() }
            val timingStatus = row["timing_status"] ?: ArchiveTiming.INSUFFICIENT_TIMING_EVIDENCE.name
            val rejection = row["rejection_reason"]
            val acceptedIdx = row["accepted_index"]?.toIntOrNull()
            val isRejected = stage == "AI_ORIGINAL" && (acceptedIdx == null || rejection != null)
            val runId = row["run_id"]
            val modelInfo = runId?.let { runMetaMap[it] }
            val strategyId = row["strategy_id"]
            val candidateId = row["candidate_id"]
            val parentEntryId = row["parent_entry_id"]
            val edited = row["edited_at_epoch"] != null
            val createdAt = row["created_at_epoch"]?.toLongOrNull() ?: 0L
            val ordinal = row["ordinal"]?.toIntOrNull() ?: 0

            val label = when (stage) {
                "LOCAL_CANDIDATE" -> buildString {
                    append("本地候选 ")
                    append(candidateId ?: "#$ordinal")
                    if (!strategyId.isNullOrBlank()) append(" · $strategyId")
                }
                "AI_ORIGINAL" -> buildString {
                    append(if (isRejected) "AI 原始(未采纳)" else "AI 精选 #${(acceptedIdx ?: ordinal) + 1}")
                    if (!modelInfo.isNullOrBlank()) append(" · $modelInfo")
                }
                "USER_FINAL" -> buildString {
                    append(if (edited) "用户改号确认" else "用户确认保存")
                    if (!modelInfo.isNullOrBlank()) append(" · 来源:$modelInfo")
                }
                else -> stage
            }

            val item = buildItem(
                id = id,
                stage = stage,
                label = label,
                type = type,
                issue = issue,
                reds = reds,
                blues = blues,
                kind = kind,
                playType = playType,
                timingStatus = timingStatus,
                drawRecord = drawRecord,
                settledHits = evalMap[id],
                isRejected = isRejected,
                rejectionReason = rejection,
                parentEntryId = parentEntryId,
                edited = edited,
                strategyId = strategyId,
                createdAtEpoch = createdAt
            )
            entryById[id] = item
            when (stage) {
                "LOCAL_CANDIDATE" -> localCandidates.add(item)
                "AI_ORIGINAL" -> aiOriginalItems.add(item)
                "USER_FINAL" -> userFinalItems.add(item)
            }
        }

        // 补充 user_predictions 中未关联 archive_entry_id 的独立/旧预测记录
        runCatching {
            val linkedArchiveIds = entries.mapNotNull { it["prediction_id"]?.toLongOrNull() }.toSet()
            val predRows = sql.query(
                "SELECT * FROM user_predictions WHERE quarantined=0 AND lottery_type=? AND target_issue=? ORDER BY id DESC",
                listOf(type.name, issue)
            )
            for (pred in predRows) {
                val predId = pred["id"]?.toLongOrNull() ?: continue
                val archiveEntryId = pred["archive_entry_id"]
                if (predId in linkedArchiveIds || (archiveEntryId != null && archiveEntryId in entryById)) {
                    continue
                }
                val reds = pred["red_balls"].orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }.sorted()
                val blues = pred["blue_balls"].orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }.sorted()
                val kind = runCatching { PredictionKind.valueOf(pred["prediction_kind"] ?: "LEGACY_UNCONFIRMED") }
                    .getOrDefault(PredictionKind.LEGACY_UNCONFIRMED)
                val playType = pred["kl8_play_type"]?.let { runCatching { Kl8PlayType.valueOf(it) }.getOrNull() }
                val strategy = pred["strategy"].orEmpty().ifBlank { "预测保存" }
                val createdAt = pred["created_at_epoch"]?.toLongOrNull() ?: 0L

                userFinalItems.add(
                    buildItem(
                        id = "P:$predId",
                        stage = "USER_PREDICTION",
                        label = strategy,
                        type = type,
                        issue = issue,
                        reds = reds,
                        blues = blues,
                        kind = kind,
                        playType = playType,
                        timingStatus = ArchiveTiming.INSUFFICIENT_TIMING_EVIDENCE.name,
                        drawRecord = drawRecord,
                        settledHits = null,
                        createdAtEpoch = createdAt
                    )
                )
            }
        }

        // 3. 构建改号前后差异与命中变化对照
        val editComparisons = userFinalItems.mapNotNull { finalItem ->
            val parentId = finalItem.parentEntryId ?: return@mapNotNull null
            val originalItem = entryById[parentId] ?: return@mapNotNull null
            val redChanged = finalItem.reds != originalItem.reds
            val blueChanged = finalItem.blues != originalItem.blues
            if (!redChanged && !blueChanged && !finalItem.edited) return@mapNotNull null

            val origRedSet = originalItem.reds.toSet()
            val finalRedSet = finalItem.reds.toSet()
            val origBlueSet = originalItem.blues.toSet()
            val finalBlueSet = finalItem.blues.toSet()

            IssueEditComparison(
                finalItem = finalItem,
                originalItem = originalItem,
                addedReds = finalItem.reds.filter { it !in origRedSet },
                removedReds = originalItem.reds.filter { it !in finalRedSet },
                addedBlues = finalItem.blues.filter { it !in origBlueSet },
                removedBlues = originalItem.blues.filter { it !in finalBlueSet },
                redHitDelta = if (finalItem.redHits != null && originalItem.redHits != null) {
                    finalItem.redHits - originalItem.redHits
                } else null,
                blueHitDelta = if (finalItem.blueHits != null && originalItem.blueHits != null) {
                    finalItem.blueHits - originalItem.blueHits
                } else null
            )
        }

        return IssueReviewDetail(
            lotteryType = type,
            issue = issue,
            drawRecord = drawRecord,
            drawRevision = drawRevision,
            collectedItems = collectedItems,
            localCandidates = localCandidates,
            aiOriginalItems = aiOriginalItems,
            userFinalItems = userFinalItems.sortedByDescending { it.createdAtEpoch },
            editComparisons = editComparisons
        )
    }

    private fun buildItem(
        id: String,
        stage: String,
        label: String,
        type: LotteryType,
        issue: String,
        reds: List<Int>,
        blues: List<Int>,
        kind: PredictionKind,
        playType: Kl8PlayType?,
        timingStatus: String,
        drawRecord: LotteryRecord?,
        settledHits: Pair<Int, Int>?,
        isRejected: Boolean = false,
        rejectionReason: String? = null,
        parentEntryId: String? = null,
        edited: Boolean = false,
        strategyId: String? = null,
        createdAtEpoch: Long = 0L
    ): IssueReviewItem {
        val hitReds = if (drawRecord != null) reds.filter { it in drawRecord.redBalls }.toSet() else emptySet()
        val hitBlues = if (drawRecord != null) blues.filter { it in drawRecord.blueBalls }.toSet() else emptySet()

        val redHits = if (drawRecord != null) (settledHits?.first ?: hitReds.size) else null
        val blueHits = if (drawRecord != null) (settledHits?.second ?: hitBlues.size) else null

        var prizeSummary: String? = null
        var isWon = false
        if (drawRecord != null && !isRejected) {
            val verifyRecord = PredictionRecord(
                lotteryType = type,
                targetIssue = issue,
                redBalls = reds,
                blueBalls = blues,
                strategy = label,
                createdAt = "",
                kind = kind,
                kl8PlayType = playType
            )
            val verification = LotteryAnalysisEngine.verifyPrediction(verifyRecord, drawRecord)
            prizeSummary = verification.resultSummary
            isWon = verification.isWon
        }

        return IssueReviewItem(
            id = id,
            stage = stage,
            label = label,
            reds = reds,
            blues = blues,
            kind = kind,
            playType = playType,
            redHits = redHits,
            blueHits = blueHits,
            hitReds = hitReds,
            hitBlues = hitBlues,
            isRejected = isRejected,
            rejectionReason = rejectionReason,
            timingStatus = timingStatus,
            parentEntryId = parentEntryId,
            edited = edited,
            prizeSummary = prizeSummary,
            isWon = isWon,
            strategyId = strategyId,
            createdAtEpoch = createdAtEpoch
        )
    }
}
