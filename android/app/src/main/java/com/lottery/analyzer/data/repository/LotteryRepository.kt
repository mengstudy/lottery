package com.lottery.analyzer.data.repository

import android.content.Context
import com.lottery.analyzer.data.db.LotteryDao
import com.lottery.analyzer.data.engine.LotteryAnalysisEngine
import com.lottery.analyzer.data.model.IssueAnalysis
import com.lottery.analyzer.data.model.LotteryRecord
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.data.model.MissingStat
import com.lottery.analyzer.data.sync.LotterySyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LotteryRepository(context: Context) {

    private val dao = LotteryDao(context)
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
     * 联网同步最新开奖数据并刷新内存缓存
     */
    suspend fun syncLatestData(type: LotteryType): Result<Int> {
        val result = syncService.syncLatest(type)
        if (result.isSuccess && (result.getOrNull() ?: 0) > 0) {
            loadLotteryData(type, forceReload = true)
        }
        return result
    }
}
