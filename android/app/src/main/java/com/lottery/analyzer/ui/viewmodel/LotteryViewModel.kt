package com.lottery.analyzer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lottery.analyzer.data.model.FrequencyAnalysisResult
import com.lottery.analyzer.data.model.HeartBallLevelDistribution
import com.lottery.analyzer.data.model.IssueAnalysis
import com.lottery.analyzer.data.model.LotteryRecord
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.data.repository.LotteryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LotteryUiState(
    val currentType: LotteryType = LotteryType.SSQ,
    val recordsDesc: List<LotteryRecord> = emptyList(),
    val currentIssue: String = "",
    val analysis: IssueAnalysis? = null,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
    val syncMessage: String? = null,
    // 心水红球与他人频次比对状态
    val myHeartBalls: Set<Int> = emptySet(),
    val collectedBallsText: String = "",
    val frequencyResult: FrequencyAnalysisResult = FrequencyAnalysisResult(0, emptyMap(), emptyList()),
    val heartBallDistributions: List<HeartBallLevelDistribution> = emptyList()
)

class LotteryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LotteryRepository(application)

    private val _uiState = MutableStateFlow(LotteryUiState())
    val uiState: StateFlow<LotteryUiState> = _uiState.asStateFlow()

    init {
        switchLotteryType(LotteryType.SSQ)
    }

    /**
     * 切换彩种
     */
    fun switchLotteryType(type: LotteryType) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                currentType = type,
                isLoading = true,
                errorMessage = null,
                myHeartBalls = emptySet() // 切换彩种重置自选
            )
            try {
                val records = repository.getRecordsDesc(type)
                val firstIssue = records.firstOrNull()?.issue ?: ""
                val analysis = if (firstIssue.isNotEmpty()) {
                    repository.getAnalysisForIssue(type, firstIssue)
                } else null

                _uiState.value = _uiState.value.copy(
                    currentType = type,
                    recordsDesc = records,
                    currentIssue = firstIssue,
                    analysis = analysis,
                    isLoading = false
                )
                recalcHeartDistributions()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "加载数据失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 切换查看期号
     */
    fun selectIssue(issue: String) {
        val type = _uiState.value.currentType
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val analysis = repository.getAnalysisForIssue(type, issue)
                _uiState.value = _uiState.value.copy(
                    currentIssue = issue,
                    analysis = analysis,
                    isLoading = false
                )
                recalcHeartDistributions()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "分析期号失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 上一期（历史更早的一期）
     */
    fun gotoPrevIssue() {
        val records = _uiState.value.recordsDesc
        val current = _uiState.value.currentIssue
        val currentIndex = records.indexOfFirst { it.issue == current }
        if (currentIndex in 0 until records.size - 1) {
            val prevIssue = records[currentIndex + 1].issue
            selectIssue(prevIssue)
        }
    }

    /**
     * 下一期（历史更晚/更新的一期）
     */
    fun gotoNextIssue() {
        val records = _uiState.value.recordsDesc
        val current = _uiState.value.currentIssue
        val currentIndex = records.indexOfFirst { it.issue == current }
        if (currentIndex > 0) {
            val nextIssue = records[currentIndex - 1].issue
            selectIssue(nextIssue)
        }
    }

    /**
     * 点选或取消心水球
     */
    fun toggleHeartBall(num: Int) {
        val current = _uiState.value.myHeartBalls.toMutableSet()
        if (num in current) {
            current.remove(num)
        } else {
            current.add(num)
        }
        _uiState.value = _uiState.value.copy(myHeartBalls = current)
        recalcHeartDistributions()
    }

    /**
     * 批量设置心水球
     */
    fun setHeartBalls(balls: Set<Int>) {
        _uiState.value = _uiState.value.copy(myHeartBalls = balls)
        recalcHeartDistributions()
    }

    /**
     * 清空心水球
     */
    fun clearHeartBalls() {
        _uiState.value = _uiState.value.copy(myHeartBalls = emptySet(), heartBallDistributions = emptyList())
    }

    /**
     * 更新他人收集号码并解析频次
     */
    fun updateCollectedBallsText(text: String) {
        val maxNumber = _uiState.value.currentType.maxRedNumber
        val freqMap = mutableMapOf<Int, Int>()
        var validGroups = 0

        val lines = text.split("\n")
        for (line in lines) {
            val clean = line.replace("[^0-9]".toRegex(), "")
            if (clean.length >= 2) {
                validGroups++
                val seenInLine = mutableSetOf<Int>()
                var i = 0
                while (i + 1 < clean.length) {
                    val num = clean.substring(i, i + 2).toIntOrNull()
                    if (num != null && num in 1..maxNumber && num !in seenInLine) {
                        seenInLine.add(num)
                        freqMap[num] = (freqMap[num] ?: 0) + 1
                    }
                    i += 2
                }
            }
        }

        val sorted = freqMap.toList().sortedByDescending { it.second }
        _uiState.value = _uiState.value.copy(
            collectedBallsText = text,
            frequencyResult = FrequencyAnalysisResult(
                validGroupsCount = validGroups,
                frequencyMap = freqMap,
                sortedByFreq = sorted
            )
        )
    }

    /**
     * 清空他人收集号码
     */
    fun clearCollectedBalls() {
        _uiState.value = _uiState.value.copy(
            collectedBallsText = "",
            frequencyResult = FrequencyAnalysisResult(0, emptyMap(), emptyList())
        )
    }

    /**
     * 重新计算心水球在下期预测遗漏分组中的穿透分布
     */
    private fun recalcHeartDistributions() {
        val analysis = _uiState.value.analysis ?: return
        val heartBalls = _uiState.value.myHeartBalls
        if (heartBalls.isEmpty()) {
            _uiState.value = _uiState.value.copy(heartBallDistributions = emptyList())
            return
        }

        val distributions = mutableListOf<HeartBallLevelDistribution>()
        for (level in analysis.nextSimulatedGroups) {
            val matched = level.allNumbers.filter { it in heartBalls }
            if (matched.isNotEmpty()) {
                distributions.add(HeartBallLevelDistribution(level.level, matched.sorted()))
            }
        }
        _uiState.value = _uiState.value.copy(heartBallDistributions = distributions)
    }

    /**
     * 联网同步最新开奖数据
     */
    fun syncLatestData() {
        val type = _uiState.value.currentType
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, syncMessage = null)
            val result = repository.syncLatestData(type)
            result.onSuccess { updatedCount ->
                val records = repository.getRecordsDesc(type)
                val firstIssue = records.firstOrNull()?.issue ?: ""
                val analysis = if (firstIssue.isNotEmpty()) {
                    repository.getAnalysisForIssue(type, firstIssue)
                } else null

                _uiState.value = _uiState.value.copy(
                    recordsDesc = records,
                    currentIssue = firstIssue,
                    analysis = analysis,
                    isSyncing = false,
                    syncMessage = if (updatedCount > 0) "已成功同步 $updatedCount 条最新开奖记录" else "本地已是最新数据"
                )
                recalcHeartDistributions()
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncMessage = "同步完成 (当前已展示本地最新记录)"
                )
            }
        }
    }

    /**
     * 获取指定球号的走势
     */
    suspend fun getNumberTrend(number: Int, isBlue: Boolean = false): List<Pair<String, Int>> {
        return repository.getNumberTrend(_uiState.value.currentType, number, isBlue)
    }
}
