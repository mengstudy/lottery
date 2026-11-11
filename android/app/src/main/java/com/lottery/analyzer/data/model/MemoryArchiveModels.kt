package com.lottery.analyzer.data.model

enum class ArchiveOrigin { REALTIME_RECOMMEND, HISTORY_REVIEW, MANUAL_BACKFILL }
enum class ArchiveRunStatus { PENDING, COMPLETED, PARTIAL, FAILED, CANCELLED, INTERRUPTED }
enum class ArchiveTiming { CONFIRMED_PRE_DRAW, POST_DRAW, INSUFFICIENT_TIMING_EVIDENCE }

object CollectionInputPolicy {
    const val MAX_CHARS = 20000
    fun error(text: String): String? = if (text.length > MAX_CHARS)
        "收集文本共${text.length}字符，超过${MAX_CHARS}字符上限，请缩短后再推荐或归档" else null
    fun error(text: String, parsed: FrequencyAnalysisResult): String? = error(text) ?: parsed.validationIssues.firstOrNull()?.let {
        "收集号码有${parsed.rejectedRows}行未通过校验：$it；请修正后再推荐或归档"
    }
}

data class CollectionSnapshot(
    val lotteryType: LotteryType,
    val targetIssue: String,
    val rawText: String,
    val groups: List<Pair<List<Int>, List<Int>>>,
    val sourceLabel: String = "手工收集",
    val origin: ArchiveOrigin = ArchiveOrigin.REALTIME_RECOMMEND,
    val parserVersion: String = "collected-v1",
    val declaredKind: PredictionKind = PredictionKind.REFERENCE,
    val kl8PlayType: Kl8PlayType? = null
)

data class ArchivedCandidate(
    val ordinal: Int,
    val rawLine: String,
    val reds: List<Int>?,
    val blues: List<Int>?,
    val rejection: String? = null,
    val acceptedIndex: Int? = null,
    val candidateId: String? = null
)

data class AiPromptContent(val system: String, val user: String)

/** Explicit allowlist: no AiConfig, request object, headers or credentials are serializable here. */
data class SafeAiArchiveFacts(
    val lotteryType: LotteryType,
    val targetIssue: String,
    val mode: AiAnalysisMode,
    val analysis: IssueAnalysis,
    val windowContext: AiWindowContext?,
    val hearts: List<Int>,
    val otherRedFrequency: List<Pair<Int, Int>>,
    val otherBlueFrequency: List<Pair<Int, Int>>,
    val collectedGroups: List<Pair<List<Int>, List<Int>>>,
    val kl8PlayType: Kl8PlayType?,
    val requestedCount: Int,
    val memoryContext: LearningContext? = null,
    val selectionContext: CandidateSelectionContext? = null
) {
    companion object {
        fun from(request: AiRecommendationRequest) = SafeAiArchiveFacts(
            request.lotteryType, request.targetNextIssue, request.mode, request.analysis, request.windowContext,
            request.userSelection.sorted(), request.topOtherBalls.toList(), request.topOtherBlues.toList(),
            request.otherCompleteBets.ifEmpty { request.otherBets.map { it to emptyList() } }.toList(),
            request.kl8PlayType.takeIf { request.lotteryType == LotteryType.KL8 }, request.betCount,
            request.memoryContext, request.selectionContext
        )
    }
}

data class ArchiveRunStart(
    val id: String,
    val sessionId: String,
    val lotteryType: LotteryType,
    val targetIssue: String,
    val createdAt: Long,
    val provider: String,
    val endpointHost: String,
    val model: String,
    val temperature: Float,
    val facts: SafeAiArchiveFacts,
    val prompt: AiPromptContent,
    val collection: CollectionSnapshot?,
    val origin: ArchiveOrigin = ArchiveOrigin.REALTIME_RECOMMEND
)

data class ArchiveRunSummary(
    val id: String, val targetIssue: String, val status: String, val createdAt: Long,
    val requested: Int, val accepted: Int, val timing: String
)
data class MemoryArchiveOverview(
    val batchCount: Int = 0, val runCount: Int = 0, val originalCount: Int = 0,
    val finalCount: Int = 0, val recentRuns: List<ArchiveRunSummary> = emptyList(),
    val recentBatches: List<ArchiveCollectionSummary> = emptyList()
)
data class ArchiveCollectionSummary(val id: String, val targetIssue: String, val source: String, val count: Int)
data class ArchiveCollectionDetail(val summary: ArchiveCollectionSummary, val rawText: String?, val groups: List<Pair<List<Int>, List<Int>>>)

data class ArchiveEntrySummary(val stage: String, val reds: List<Int>?, val blues: List<Int>?,
    val rejection: String?, val timing: String, val edited: Boolean)
data class ArchiveRunDetail(val id: String, val targetIssue: String, val provider: String, val model: String,
    val status: String, val finishReason: String?, val rejectionJson: String, val entries: List<ArchiveEntrySummary>)

/** Phase 1 has no trusted draw-time authority. Local absence never establishes pre-draw eligibility. */
object ArchiveTimingPolicy {
    fun classify(hasKnownDraw: Boolean): ArchiveTiming = if (hasKnownDraw) ArchiveTiming.POST_DRAW else ArchiveTiming.INSUFFICIENT_TIMING_EVIDENCE
}
