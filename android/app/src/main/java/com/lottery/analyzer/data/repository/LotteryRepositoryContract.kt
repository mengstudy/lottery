package com.lottery.analyzer.data.repository

import com.lottery.analyzer.data.model.*
import com.lottery.analyzer.data.sync.SyncReport

interface LotteryRepositoryContract {
    suspend fun getRecordsDesc(type: LotteryType): List<LotteryRecord>
    suspend fun getAnalysisForIssue(type: LotteryType, issue: String): IssueAnalysis?
    suspend fun getNumberTrend(type: LotteryType, number: Int, isBlue: Boolean = false, limit: Int = 30): List<Pair<String, Int>>
    suspend fun syncLatestData(type: LotteryType): Result<SyncReport>
    suspend fun syncAllHistoryData(type: LotteryType, onProgress: (suspend (Int, Int) -> Unit)? = null): Result<SyncReport>
    suspend fun savePredictions(records: List<PredictionRecord>): Int
    suspend fun deletePrediction(id: Long): Boolean
    suspend fun clearPredictions(type: LotteryType?): Boolean
    suspend fun getVerifiedPredictionsPaged(type: LotteryType?, page: Int = 0, pageSize: Int = 10): PagedPredictionsResult
    suspend fun executeShrink(type: LotteryType, selectedBalls: List<Int>, config: ShrinkFilterConfig): ShrinkResult
    suspend fun getTrendMatrixData(type: LotteryType, limit: Int = 30, descOrder: Boolean = true): TrendMatrixData
}
