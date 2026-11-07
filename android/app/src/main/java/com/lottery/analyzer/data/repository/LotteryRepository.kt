package com.lottery.analyzer.data.repository

import android.content.Context
import com.lottery.analyzer.data.db.LotteryDao
import com.lottery.analyzer.data.db.PredictionDao
import com.lottery.analyzer.data.engine.LotteryAnalysisEngine
import com.lottery.analyzer.data.model.IssueAnalysis
import com.lottery.analyzer.data.model.LotteryRecord
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.data.model.MissingStat
import com.lottery.analyzer.data.model.PredictionRecord
import com.lottery.analyzer.data.model.PredictionVerificationResult
import com.lottery.analyzer.data.engine.LotteryShrinkEngine
import com.lottery.analyzer.data.engine.TrendMatrixEngine
import com.lottery.analyzer.data.model.ShrinkFilterConfig
import com.lottery.analyzer.data.model.ShrinkResult
import com.lottery.analyzer.data.model.TrendMatrixData
import com.lottery.analyzer.data.sync.LotterySyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class PagedPredictionsResult(
    val totalCount: Int,
    val validPage: Int,
    val records: List<PredictionVerificationResult>
)

class LotteryRepository(context: Context) {
    private val dao = LotteryDao(context)
    private val predictionDao = PredictionDao(context)
    private val syncService = LotterySyncService(dao)

    // 内存缓存：彩种 -> 升序开奖记录
    private val recordsAscCache = mutableMapOf<LotteryType, List<LotteryRecord>>()

    // 内存缓存：彩种 -> (期号 -> 遗漏统计)
    private val missingStatsCache = mutableMapOf<LotteryType, Map<String, MissingStat>>()

    /**
     * 预加载并初始化计算指定彩种的所有数据
     */
    suspend fun loadLotteryData(type: LotteryType, forceReload: Boolean = false): List<LotteryRecord> =
        withContext(Dispatchers.IO) {
            if (!forceReload && recordsAscCache.containsKey(type)) {
                return@withContext recordsAscCache[type] ?: emptyList()
}

            val ascList = dao.getAllRecords(type, asc = true)

            recordsAscCache[type] = ascList

            val missingMap = LotteryAnalysisEngine.calculateAllMissingStats(ascList, type)

            missingStatsCache[type] = missingMap

            ascList
}

    /**
     * 获取降序排序的开奖记录列表（适合手机端列表自顶向下显示最新期数）
     */
    suspend fun getRecordsDesc(type: LotteryType): List<LotteryRecord> = withContext(Dispatchers.IO) {
        val asc = loadLotteryData(type)

        asc.asReversed()
}

    /**
     * 获取指定期号的深度分析数据
     */
    suspend fun getAnalysisForIssue(type: LotteryType, issue: String): IssueAnalysis? =
        withContext(Dispatchers.Default) {
            val ascList = loadLotteryData(type)
            val record = ascList.firstOrNull { it.issue == issue } ?: return@withContext null
            val missingMap = missingStatsCache[type] ?: emptyMap()
            val stat = missingMap[issue]

            LotteryAnalysisEngine.analyze(record, stat, type)
}

    /**
     * 获取某个球号的最近历史遗漏走势
     */
    suspend fun getNumberTrend(
        type: LotteryType,
        number: Int,
        isBlue: Boolean = false,
        limit: Int = 30
    ): List<Pair<String, Int>> = withContext(Dispatchers.Default) {
        val ascList = loadLotteryData(type)
        val missingMap = missingStatsCache[type] ?: emptyMap()
        val recentList = ascList.takeLast(limit)

        recentList.map { rec ->

            val stat = missingMap[rec.issue]
            val missVal = if (isBlue) {
                stat?.blueMissing?.get(number) ?: 0
} else {
                stat?.redMissing?.get(number) ?: 0
}

            rec.issue to missVal
}
}

    /**
     * 1. 联网同步最新开奖数据并刷新内存缓存
     */
    suspend fun syncLatestData(type: LotteryType): Result<Int> {
        try {
            return syncService.syncLatest(type)
} finally {
            // 无论正常返回、网络异常还是协程被取消(如切彩种)，均在 NonCancellable 上下文中强制刷新内存缓存

            withContext(NonCancellable) {
                loadLotteryData(type, forceReload = true)
}
}
}

    /**
     * 2. 联网全量同步所有历史数据并刷新内存缓存
     */
    suspend fun syncAllHistoryData(
        type: LotteryType,
        onProgress: ((page: Int, totalRecords: Int) -> Unit)? = null
    ): Result<Int> {
        try {
            return syncService.syncAllHistory(type, onProgress)
} finally {
            // 核心修复：无论正常完成、异常中断还是被主动取消，均在 NonCancellable 上下文中强制刷新内存缓存，使已入库数据立即可见

            withContext(NonCancellable) {
                loadLotteryData(type, forceReload = true)
}
}
}

    /**
     * 保存单条预测记录
     */
    suspend fun savePrediction(record: PredictionRecord): Long = withContext(Dispatchers.IO) {
        predictionDao.insert(record)
}

    /**
     * 删除单条预测记录
     */
    suspend fun deletePrediction(id: Long): Boolean = withContext(Dispatchers.IO) {
        predictionDao.delete(id)
}

    /**
     * 清空指定彩种的预测记录
     */
    suspend fun clearPredictions(type: LotteryType?): Boolean = withContext(Dispatchers.IO) {
        predictionDao.clear(type)
}

    /**
     * 查询预测记录并与当前最新开奖数据库进行比对核验
     */

    /**
     * 查询预测记录并与当前最新开奖数据库进行比对核验 (批量去重分批核验，彻底解决 N+1 数据库查询)
     */

        /**
     * 数据层惰性分页查询预测记录并批量核验 (接通数据层分页，彻底解决全量查询内存开销)
     */

    /**
     * 数据层惰性分页查询预测记录并批量核验 (接通数据层分页参数、总数与翻页，彻底解决内存开销与20条截断限制)
     */
    suspend fun getVerifiedPredictionsPaged(
        type: LotteryType?,
        page: Int = 0,
        pageSize: Int = 10
    ): PagedPredictionsResult = withContext(Dispatchers.IO) {
        val totalCount = predictionDao.getCount(type)
        if (totalCount <= 0) {
            return@withContext PagedPredictionsResult(0, 0, emptyList())
        }
        val maxPage = (totalCount - 1) / pageSize
        val validPage = page.coerceIn(0, maxPage)
        val offset = validPage * pageSize
        val rawList = predictionDao.getPaged(type, limit = pageSize, offset = offset)
        if (rawList.isEmpty()) {
            return@withContext PagedPredictionsResult(totalCount, validPage, emptyList())
        }

        val groupedByLottery = rawList.groupBy { it.lotteryType }
        val actualRecordsMap = mutableMapOf<Pair<LotteryType, String>, LotteryRecord>()
        for ((lotteryType, records) in groupedByLottery) {
            val issues = records.map { it.targetIssue }.filter { it.isNotBlank() }.distinct()
            val batchMap = dao.getRecordsByIssues(lotteryType, issues)
            for ((issue, rec) in batchMap) {
                actualRecordsMap[lotteryType to issue] = rec
            }
        }

        val verified = rawList.map { pred ->
            val actual = actualRecordsMap[pred.lotteryType to pred.targetIssue]
            LotteryAnalysisEngine.verifyPrediction(pred, actual)
        }
        PagedPredictionsResult(totalCount, validPage, verified)
    }

    /**
     * 查询预测记录 (支持通过指定页码与大小查询，接通完整分页)
     */
    suspend fun getVerifiedPredictions(
        type: LotteryType?,
        page: Int = 0,
        pageSize: Int = 10
    ): List<PredictionVerificationResult> =
        getVerifiedPredictionsPaged(type, page = page, pageSize = pageSize).records

    suspend fun executeShrink(
        type: LotteryType,
        selectedBalls: List<Int>,
        config: ShrinkFilterConfig
    ): ShrinkResult = withContext(Dispatchers.Default) {
        val ascList = loadLotteryData(type)
        val lastReds = ascList.lastOrNull()?.redBalls?.toSet() ?: emptySet()
        val historyRedSets = ascList.map { it.redBalls.toSet() }.toSet()

        ensureActive()

        LotteryShrinkEngine.executeShrink(
            selectedBalls = selectedBalls,
            type = type,
            config = config,
            lastDrawReds = lastReds,
            historyRedSets = historyRedSets,
            checkCancellation = { ensureActive() }
        )
}

    /**
     * 获取全景网格走势图矩阵数据
     */
    suspend fun getTrendMatrixData(
        type: LotteryType,
        limit: Int = 30,
        descOrder: Boolean = true
    ): TrendMatrixData = withContext(Dispatchers.Default) {
        val ascList = loadLotteryData(type)
        val missingMap = missingStatsCache[type] ?: emptyMap()

        TrendMatrixEngine.buildTrendMatrix(
            ascRecords = ascList,
            missingMap = missingMap,
            type = type,
            limit = limit,
            descOrder = descOrder
        )
}
}
