package com.lottery.analyzer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lottery.analyzer.data.ai.AiLotteryService
import com.lottery.analyzer.data.db.AiConfigPreferences
import com.lottery.analyzer.data.model.AiAnalysisMode
import com.lottery.analyzer.data.model.AiConfig
import com.lottery.analyzer.data.model.AiProvider
import com.lottery.analyzer.data.model.AiRecommendationResult
import com.lottery.analyzer.data.model.FrequencyAnalysisResult
import com.lottery.analyzer.data.model.HeartBallLevelDistribution
import com.lottery.analyzer.data.model.IssueAnalysis
import com.lottery.analyzer.data.model.LotteryRecord
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.data.model.PredictionRecord
import com.lottery.analyzer.data.model.PredictionVerificationResult
import com.lottery.analyzer.data.repository.LotteryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val heartBallDistributions: List<HeartBallLevelDistribution> = emptyList(),
    // 预测与选号策略记录状态
    val nextIssueTarget: String = "",
    val predictionRecords: List<PredictionVerificationResult> = emptyList(),
    val predictionFeedback: String? = null,
    // AI 大模型状态
    val aiConfig: AiConfig = AiConfig(),
    val isAiLoading: Boolean = false,
    val aiResult: AiRecommendationResult? = null,
    val aiError: String? = null,
    val aiTestMessage: String? = null,
    val showAiSettingsDialog: Boolean = false,
    val showLogDialog: Boolean = false
)

class LotteryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LotteryRepository(application)
    private val aiConfigPreferences = AiConfigPreferences(application)
    private val aiService = AiLotteryService()

    private val _uiState = MutableStateFlow(LotteryUiState())
    val uiState: StateFlow<LotteryUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            aiConfig = aiConfigPreferences.getAiConfig()
        )
        switchLotteryType(LotteryType.SSQ)
    }

    /**
     * 推算下一期期号（基于当前数据库最新期）
     */
    private fun computeNextIssue(latestIssue: String): String {
        if (latestIssue.isBlank()) return ""
        val num = latestIssue.toLongOrNull()
        return if (num != null) (num + 1).toString() else latestIssue
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
                myHeartBalls = emptySet(), // 切换彩种重置自选
                aiResult = null,
                aiError = null
            )
            try {
                val records = repository.getRecordsDesc(type)
                val firstIssue = records.firstOrNull()?.issue ?: ""
                val analysis = if (firstIssue.isNotEmpty()) {
                    repository.getAnalysisForIssue(type, firstIssue)
                } else null

                val nextIssue = computeNextIssue(firstIssue)

                _uiState.value = _uiState.value.copy(
                    currentType = type,
                    recordsDesc = records,
                    currentIssue = firstIssue,
                    analysis = analysis,
                    nextIssueTarget = nextIssue,
                    isLoading = false
                )
                recalcHeartDistributions()
                loadPredictions()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "加载数据失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 选择指定期号查看分析看板
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
     * 切换心水红球选择状态
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
     * 批量设置心水红球
     */
    fun setHeartBalls(balls: Set<Int>) {
        _uiState.value = _uiState.value.copy(myHeartBalls = balls)
        recalcHeartDistributions()
    }

    /**
     * 清空心水红球
     */
    fun clearHeartBalls() {
        _uiState.value = _uiState.value.copy(myHeartBalls = emptySet(), heartBallDistributions = emptyList())
    }

    /**
     * 更新他人购买号码文本并进行频次解析与注数提取 (支持1行1注)
     */
    fun updateCollectedBallsText(text: String) {
        val maxNumber = _uiState.value.currentType.maxRedNumber
        val freqMap = mutableMapOf<Int, Int>()
        val parsedBets = mutableListOf<List<Int>>()
        var validGroups = 0

        val lines = text.split("\n")
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank()) continue

            // 1. 优先尝试按空格/逗号等分隔符提取独立数字
            val extractedByDelim = Regex("\\b\\d{1,2}\\b")
                .findAll(trimmedLine)
                .mapNotNull { it.value.toIntOrNull() }
                .filter { it in 1..maxNumber }
                .distinct()
                .toList()

            val rowBalls = if (extractedByDelim.size >= 2) {
                extractedByDelim
            } else {
                // 2. 兜底尝试每 2 位切割连续无空格数字 (如 020815212630)
                val clean = trimmedLine.replace("[^0-9]".toRegex(), "")
                val subList = mutableListOf<Int>()
                var i = 0
                while (i + 1 < clean.length) {
                    val num = clean.substring(i, i + 2).toIntOrNull()
                    if (num != null && num in 1..maxNumber && num !in subList) {
                        subList.add(num)
                    }
                    i += 2
                }
                subList
            }

            if (rowBalls.isNotEmpty()) {
                validGroups++
                parsedBets.add(rowBalls.sorted())
                for (b in rowBalls) {
                    freqMap[b] = (freqMap[b] ?: 0) + 1
                }
            }
        }

        val sorted = freqMap.toList().sortedByDescending { it.second }
        _uiState.value = _uiState.value.copy(
            collectedBallsText = text,
            frequencyResult = FrequencyAnalysisResult(
                validGroupsCount = validGroups,
                frequencyMap = freqMap,
                sortedByFreq = sorted,
                parsedBets = parsedBets
            )
        )
    }

    /**
     * 清空他人购买号码
     */
    fun clearCollectedBalls() {
        _uiState.value = _uiState.value.copy(
            collectedBallsText = "",
            frequencyResult = FrequencyAnalysisResult(0, emptyMap(), emptyList())
        )
    }

    /**
     * 重新计算心水红球在下一期遗漏层级上的分布
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
     * 加载当前彩种已保存的预测记录并完成自动比对验证
     */
    fun loadPredictions() {
        viewModelScope.launch {
            try {
                val list = repository.getVerifiedPredictions(_uiState.value.currentType)
                _uiState.value = _uiState.value.copy(predictionRecords = list)
            } catch (e: Exception) {
                // 忽略或记录
            }
        }
    }

    /**
     * 保存一条新的预测与选号策略记录（期号默认为预测下一期）
     */
    fun savePredictionRecord(
        redBalls: List<Int>,
        blueBalls: List<Int> = emptyList(),
        strategy: String,
        targetIssue: String = ""
    ) {
        val target = if (targetIssue.isNotBlank()) targetIssue else _uiState.value.nextIssueTarget
        val timeStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())

        val record = PredictionRecord(
            lotteryType = _uiState.value.currentType,
            targetIssue = target,
            redBalls = redBalls.sorted(),
            blueBalls = blueBalls.sorted(),
            strategy = strategy,
            createdAt = timeStr
        )

        viewModelScope.launch {
            repository.savePrediction(record)
            loadPredictions()
            _uiState.value = _uiState.value.copy(
                predictionFeedback = "已成功记录第 $target 期预测选号！"
            )
        }
    }

    /**
     * 删除单条预测记录
     */
    fun deletePredictionRecord(id: Long) {
        viewModelScope.launch {
            repository.deletePrediction(id)
            loadPredictions()
            _uiState.value = _uiState.value.copy(
                predictionFeedback = "已删除预测记录"
            )
        }
    }

    /**
     * 清空当前彩种的所有预测记录
     */
    fun clearPredictionRecords() {
        viewModelScope.launch {
            repository.clearPredictions(_uiState.value.currentType)
            loadPredictions()
            _uiState.value = _uiState.value.copy(
                predictionFeedback = "已清空预测历史记录"
            )
        }
    }

    /**
     * 联网同步最新开奖数据（同步完成后自动重算预测比对）
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

                val nextIssue = computeNextIssue(firstIssue)

                _uiState.value = _uiState.value.copy(
                    recordsDesc = records,
                    currentIssue = firstIssue,
                    analysis = analysis,
                    nextIssueTarget = nextIssue,
                    isSyncing = false,
                    syncMessage = if (updatedCount > 0) "已成功同步 $updatedCount 条最新开奖记录" else "本地已是最新数据"
                )
                recalcHeartDistributions()
                loadPredictions() // 自动重算比对
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncMessage = "同步完成 (当前已展示本地最新记录)"
                )
                loadPredictions()
            }
        }
    }

    /**
     * 获取指定球号的走势
     */
    suspend fun getNumberTrend(number: Int, isBlue: Boolean = false): List<Pair<String, Int>> {
        return repository.getNumberTrend(_uiState.value.currentType, number, isBlue)
    }

    // ==================== AI 大模型功能控制 ====================

    /**
     * 显示/关闭 AI 配置弹窗
     */
    fun setAiSettingsDialogVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(
            showAiSettingsDialog = visible,
            aiTestMessage = null
        )
    }

    /**
     * 显示/关闭 日志查看弹窗
     */
    fun setLogDialogVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showLogDialog = visible)
    }

    /**
     * 更新 AI 配置
     */
    fun updateAiConfig(config: AiConfig) {
        aiConfigPreferences.saveAiConfig(config)
        _uiState.value = _uiState.value.copy(aiConfig = config)
    }

    /**
     * 切换 AI 平台预设
     */
    fun selectAiProviderPreset(provider: AiProvider) {
        val newConfig = aiConfigPreferences.selectProviderPreset(provider)
        _uiState.value = _uiState.value.copy(aiConfig = newConfig)
    }

    /**
     * 测试 AI API 连通性
     */
    fun testAiConnection(config: AiConfig = _uiState.value.aiConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAiLoading = true,
                aiTestMessage = null,
                aiError = null
            )
            val result = aiService.testConnection(config)
            result.onSuccess { reply ->
                _uiState.value = _uiState.value.copy(
                    isAiLoading = false,
                    aiTestMessage = "✅ 连接成功！模型响应: $reply"
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isAiLoading = false,
                    aiTestMessage = "❌ 连接失败: ${err.message}"
                )
            }
        }
    }

    /**
     * 发起 AI 选号推演或诊断
     */
    fun runAiAnalysis(mode: AiAnalysisMode, betCount: Int = 3) {
        val analysis = _uiState.value.analysis
        if (analysis == null) {
            _uiState.value = _uiState.value.copy(aiError = "尚未加载到当前彩种分析数据")
            return
        }
        val type = _uiState.value.currentType
        val targetIssue = _uiState.value.nextIssueTarget
        val config = _uiState.value.aiConfig

        if (config.apiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                showAiSettingsDialog = true,
                aiError = "请先配置并保存 AI API Key"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAiLoading = true,
                aiError = null,
                aiResult = null
            )
            val result = aiService.generateRecommendation(
                config = config,
                lotteryType = type,
                analysis = analysis,
                targetNextIssue = targetIssue,
                mode = mode,
                userSelection = _uiState.value.myHeartBalls,
                topOtherBalls = _uiState.value.frequencyResult.sortedByFreq,
                otherBets = _uiState.value.frequencyResult.parsedBets,
                betCount = betCount
            )
            result.onSuccess { recommendation ->
                _uiState.value = _uiState.value.copy(
                    isAiLoading = false,
                    aiResult = recommendation
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isAiLoading = false,
                    aiError = err.message ?: "AI 生成失败，请检查配置与网络"
                )
            }
        }
    }

    /**
     * 将 AI 推荐的指定注数号码保存为下期预测记录
     */
    fun saveAiBetToPrediction(betIndex: Int) {
        val result = _uiState.value.aiResult ?: return
        if (betIndex in result.recommendedBets.indices) {
            val (reds, blues) = result.recommendedBets[betIndex]
            val strategy = "AI大模型[${_uiState.value.aiConfig.provider.title}]-${result.mode.title}#${betIndex + 1}"
            savePredictionRecord(
                redBalls = reds,
                blueBalls = blues,
                strategy = strategy
            )
        }
    }

    /**
     * 将 AI 推荐的红球一键设置为心水红球
     */
    fun applyAiBetToHeartBalls(betIndex: Int) {
        val result = _uiState.value.aiResult ?: return
        if (betIndex in result.recommendedBets.indices) {
            val (reds, _) = result.recommendedBets[betIndex]
            setHeartBalls(reds.toSet())
        }
    }

    /**
     * 清除当前 AI 结果
     */
    fun clearAiResult() {
        _uiState.value = _uiState.value.copy(aiResult = null, aiError = null)
    }
}
