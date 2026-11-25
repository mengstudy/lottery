package com.lottery.analyzer.data.model

/**
 * 逐期复盘可供选择的期号概要
 */
data class IssueReviewOption(
    val issue: String,
    val hasDraw: Boolean,
    val drawDate: String? = null,
    val hasCollection: Boolean = false,
    val hasAiRun: Boolean = false,
    val hasUserFinal: Boolean = false
)

/**
 * 复盘对照中的单条号码方案
 */
data class IssueReviewItem(
    val id: String,
    val stage: String, // COLLECTED, LOCAL_CANDIDATE, AI_ORIGINAL, USER_FINAL, USER_PREDICTION
    val label: String,
    val reds: List<Int>,
    val blues: List<Int>,
    val kind: PredictionKind,
    val playType: Kl8PlayType?,
    val redHits: Int?,
    val blueHits: Int?,
    val hitReds: Set<Int>,
    val hitBlues: Set<Int>,
    val isRejected: Boolean = false,
    val rejectionReason: String? = null,
    val timingStatus: String = ArchiveTiming.INSUFFICIENT_TIMING_EVIDENCE.name,
    val parentEntryId: String? = null,
    val edited: Boolean = false,
    val prizeSummary: String? = null,
    val isWon: Boolean = false,
    val strategyId: String? = null,
    val createdAtEpoch: Long = 0L
)

/**
 * 改号前后差异与命中影响对比
 */
data class IssueEditComparison(
    val finalItem: IssueReviewItem,
    val originalItem: IssueReviewItem,
    val addedReds: List<Int>,
    val removedReds: List<Int>,
    val addedBlues: List<Int>,
    val removedBlues: List<Int>,
    val redHitDelta: Int?,
    val blueHitDelta: Int?
)

/**
 * 单期开奖复盘完整聚合数据
 */
data class IssueReviewDetail(
    val lotteryType: LotteryType,
    val issue: String,
    val drawRecord: LotteryRecord?,
    val drawRevision: String?,
    val collectedItems: List<IssueReviewItem>,
    val localCandidates: List<IssueReviewItem>,
    val aiOriginalItems: List<IssueReviewItem>,
    val userFinalItems: List<IssueReviewItem>,
    val editComparisons: List<IssueEditComparison>
)
