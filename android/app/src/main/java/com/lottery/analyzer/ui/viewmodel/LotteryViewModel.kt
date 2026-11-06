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
import com.lottery.analyzer.data.model.Kl8PlayType
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.data.model.PredictionRecord
import com.lottery.analyzer.data.model.PredictionVerificationResult
import com.lottery.analyzer.data.model.ShrinkFilterConfig
import com.lottery.analyzer.data.model.ShrinkResult
import com.lottery.analyzer.data.model.TrendMatrixData
import com.lottery.analyzer.data.model.WheelGuaranteeMode
import com.lottery.analyzer.data.repository.LotteryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val isSyncingAll: Boolean = false,
    val syncAllProgress: String? = null,
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
    val showLogDialog: Boolean = false,
    // 缩水与旋转矩阵状态
    val kl8PlayType: Kl8PlayType = Kl8PlayType.XUAN_TEN,
    val shrinkSelectedBalls: Set<Int> = emptySet(),
    val shrinkConfig: ShrinkFilterConfig = ShrinkFilterConfig(),
    val shrinkResult: ShrinkResult? = null,
    val isShrinking: Boolean = false,
    // 全景网格走势图状态
    val trendMatrixLimit: Int = 30,
    val trendDescOrder: Boolean = true,
    val trendMatrixData: TrendMatrixData? = null,
    val isTrendLoading: Boolean = false
)

class LotteryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LotteryRepository(application)
    private val aiConfigPreferences = AiConfigPreferences(application)
    private val aiService = AiLotteryService()

    private val _uiState = MutableStateFlow(LotteryUiState())
    val uiState: StateFlow<LotteryUiState> = _uiState.asStateFlow()

    private var switchJob: Job? = null
    private var syncJob: Job? = null
    private var shrinkJob: Job? = null
    private var aiJob: Job? = null
    private var trendJob: Job? = null
    private var issueJob: Job? = null
    private var predictionJob: Job? = null

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
        switchJob?.cancel()
        syncJob?.cancel()
        shrinkJob?.cancel()
        aiJob?.cancel()
        trendJob?.cancel()
        issueJob?.cancel()
        predictionJob?.cancel()
        switchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                currentType = type,
                isLoading = true,
                errorMessage = null,
                myHeartBalls = emptySet(),
                predictionRecords = emptyList(),
                shrinkSelectedBalls = emptySet(),
                shrinkResult = null,
                isShrinking = false,
                aiResult = null,
                aiError = null,
                isAiLoading = false,
                isSyncing = false,
                isSyncingAll = false,
                syncAllProgress = null,
                syncMessage = null,
                trendMatrixData = null,
                isTrendLoading = false
            )
            try {
                val records = repository.getRecordsDesc(type)
                // 彩种异步一致性守卫：若彩种已被再次切换，终止过时赋值
                if (_uiState.value.currentType != type) return@launch

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

                // 若初次启动检测到本地记录为空，自动拉取网络最新数据
                if (records.isEmpty()) {
                    syncLatestData()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_uiState.value.currentType != type) return@launch
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
        issueJob?.cancel()
        issueJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val analysis = repository.getAnalysisForIssue(type, issue)
                if (_uiState.value.currentType != type) return@launch
                _uiState.value = _uiState.value.copy(
                    currentIssue = issue,
                    analysis = analysis,
                    isLoading = false,
                    errorMessage = null
                )
                recalcHeartDistributions()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_uiState.value.currentType != type) return@launch
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
        val targetType = _uiState.value.currentType
        predictionJob?.cancel()
        predictionJob = viewModelScope.launch {
            try {
                val list = repository.getVerifiedPredictions(targetType)
                if (_uiState.value.currentType != targetType) return@launch
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
        val targetType = _uiState.value.currentType
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, syncMessage = null)
            val result = repository.syncLatestData(targetType)
            if (_uiState.value.currentType != targetType) return@launch

            result.onSuccess { updatedCount ->
                val records = repository.getRecordsDesc(targetType)
                if (_uiState.value.currentType != targetType) return@launch
                val firstIssue = records.firstOrNull()?.issue ?: ""
                val analysis = if (firstIssue.isNotEmpty()) {
                    repository.getAnalysisForIssue(targetType, firstIssue)
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
                loadPredictions()
            }.onFailure { err ->
                if (_uiState.value.currentType != targetType) return@launch
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncMessage = "同步失败: ${err.message ?: "网络连接异常"}"
                )
            }
        }
    }

    /**
     * 联网全量同步所有历史开奖数据 (抓取近 2000~3500 期全量历史大底)
     */
    fun syncAllHistoryData() {
        val targetType = _uiState.value.currentType
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSyncingAll = true,
                syncAllProgress = "正在连接官方数据源...",
                syncMessage = null
            )
            val result = repository.syncAllHistoryData(targetType) { page, totalRecords ->
                if (_uiState.value.currentType == targetType) {
                    _uiState.value = _uiState.value.copy(
                        syncAllProgress = "正在拉取第 " + page + " 页，累计 " + totalRecords + " 期..."
                    )
                }
            }
            if (_uiState.value.currentType != targetType) return@launch

            result.onSuccess { updatedCount ->
                val records = repository.getRecordsDesc(targetType)
                if (_uiState.value.currentType != targetType) return@launch
                val firstIssue = records.firstOrNull()?.issue ?: ""
                val analysis = if (firstIssue.isNotEmpty()) {
                    repository.getAnalysisForIssue(targetType, firstIssue)
                } else null

                val nextIssue = computeNextIssue(firstIssue)

                _uiState.value = _uiState.value.copy(
                    recordsDesc = records,
                    currentIssue = firstIssue,
                    analysis = analysis,
                    nextIssueTarget = nextIssue,
                    isSyncingAll = false,
                    syncAllProgress = null,
                    syncMessage = "全量更新完成！当前数据库共有 " + records.size + " 期历史数据"
                )
                recalcHeartDistributions()
                loadPredictions()
                loadTrendMatrixData()
            }.onFailure { err ->
                if (_uiState.value.currentType != targetType) return@launch
                val records = repository.getRecordsDesc(targetType)
                val firstIssue = records.firstOrNull()?.issue ?: ""
                val analysis = if (firstIssue.isNotEmpty()) {
                    repository.getAnalysisForIssue(targetType, firstIssue)
                } else null
                val nextIssue = computeNextIssue(firstIssue)

                _uiState.value = _uiState.value.copy(
                    recordsDesc = records,
                    currentIssue = firstIssue,
                    analysis = analysis,
                    nextIssueTarget = nextIssue,
                    isSyncingAll = false,
                    syncAllProgress = null,
                    syncMessage = "全量同步中断: ${err.message ?: "网络连接异常"}"
                )
                recalcHeartDistributions()
                loadPredictions()
                loadTrendMatrixData()
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
    /**
     * 设置快乐8所选玩法 (选一 至 选十)
     */
    fun setKl8PlayType(playType: Kl8PlayType) {
        _uiState.value = _uiState.value.copy(
            kl8PlayType = playType,
            shrinkConfig = _uiState.value.shrinkConfig.copy(kl8PlayType = playType)
        )
    }

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

        val targetType = type
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAiLoading = true,
                aiError = null,
                aiResult = null
            )
            val result = aiService.generateRecommendation(
                config = config,
                lotteryType = targetType,
                analysis = analysis,
                targetNextIssue = targetIssue,
                mode = mode,
                userSelection = _uiState.value.myHeartBalls,
                topOtherBalls = _uiState.value.frequencyResult.sortedByFreq,
                otherBets = _uiState.value.frequencyResult.parsedBets,
                betCount = betCount,
                kl8PlayType = _uiState.value.kl8PlayType
            )
            if (_uiState.value.currentType != targetType) return@launch

            result.onSuccess { recommendation ->
                if (_uiState.value.currentType != targetType) return@launch
                _uiState.value = _uiState.value.copy(
                    isAiLoading = false,
                    aiResult = recommendation
                )
            }.onFailure { err ->
                if (_uiState.value.currentType != targetType) return@launch
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
        if (result.lotteryType != _uiState.value.currentType) return
        if (betIndex in result.recommendedBets.indices) {
            val (reds, blues) = result.recommendedBets[betIndex]
            val playSuffix = if (result.lotteryType == LotteryType.KL8 && result.kl8PlayType != null) "-${result.kl8PlayType.title}" else ""
            val strategy = "AI大模型[${_uiState.value.aiConfig.provider.title}]-${result.mode.title}${playSuffix}#${betIndex + 1}"
            val target = result.targetIssue.ifBlank { _uiState.value.nextIssueTarget }
            savePredictionRecord(
                redBalls = reds,
                blueBalls = blues,
                strategy = strategy,
                targetIssue = target
            )
        }
    }

    /**
     * 将 AI 推荐的红球一键设置为心水红球
     */
    fun applyAiBetToHeartBalls(betIndex: Int) {
        val result = _uiState.value.aiResult ?: return
        if (result.lotteryType != _uiState.value.currentType) return
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

    // ==================== 复式缩水与旋转矩阵控制 ====================

    fun toggleShrinkBall(ball: Int) {
        val current = _uiState.value.shrinkSelectedBalls.toMutableSet()
        if (ball in current) current.remove(ball) else current.add(ball)
        _uiState.value = _uiState.value.copy(shrinkSelectedBalls = current)
    }

    fun setShrinkBalls(balls: Set<Int>) {
        _uiState.value = _uiState.value.copy(shrinkSelectedBalls = balls)
    }

    fun clearShrinkBalls() {
        _uiState.value = _uiState.value.copy(
            shrinkSelectedBalls = emptySet(),
            shrinkResult = null
        )
    }

    fun importHeartToShrink() {
        val hearts = _uiState.value.myHeartBalls
        _uiState.value = _uiState.value.copy(shrinkSelectedBalls = hearts)
    }

    fun updateShrinkConfig(config: ShrinkFilterConfig) {
        _uiState.value = _uiState.value.copy(shrinkConfig = config)
    }

    fun runShrinkCalculation() {
        val selected = _uiState.value.shrinkSelectedBalls.toList()
        val type = _uiState.value.currentType
        val config = _uiState.value.shrinkConfig

        val minCount = if (type == LotteryType.KL8) config.kl8PlayType.pickCount else type.redCount
        val titleText = if (type == LotteryType.KL8) "快乐8[${config.kl8PlayType.title}]" else type.title
        val maxCount = if (type == LotteryType.KL8) 14 else 16

        if (selected.size < minCount) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "选号池号码不足：" + titleText + "至少需要选择 " + minCount + " 个红球"
            )
            return
        }
        if (selected.size > maxCount) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "选号池超出上限：" + titleText + "最多支持选择 " + maxCount + " 个红球，以防内存溢出"
            )
            return
        }

        val targetType = type
        shrinkJob?.cancel()
        shrinkJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isShrinking = true, errorMessage = null)
            try {
                val result = repository.executeShrink(targetType, selected, config)
                if (_uiState.value.currentType != targetType) return@launch

                _uiState.value = _uiState.value.copy(
                    shrinkResult = result
                )
            } finally {
                if (_uiState.value.currentType == targetType) {
                    _uiState.value = _uiState.value.copy(isShrinking = false)
                }
            }
        }
    }

    fun saveShrinkBetToPrediction(reds: List<Int>) {
        val type = _uiState.value.currentType
        val blueList = when (type) {
            LotteryType.SSQ -> listOf((1..16).random())
            LotteryType.DLT -> (1..12).shuffled().take(2).sorted()
            LotteryType.KL8 -> emptyList()
        }
        val playSuffix = if (type == LotteryType.KL8) "[${_uiState.value.shrinkConfig.kl8PlayType.title}]" else ""
        val strategy = "复式缩水$playSuffix[" + _uiState.value.shrinkConfig.wheelMode.title + "]"
        savePredictionRecord(redBalls = reds, blueBalls = blueList, strategy = strategy)
    }

    // ==================== 全景网格走势图控制 ====================

    fun loadTrendMatrixData() {
        val type = _uiState.value.currentType
        val limit = _uiState.value.trendMatrixLimit
        val desc = _uiState.value.trendDescOrder

        trendJob?.cancel()
        trendJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTrendLoading = true)
            val data = repository.getTrendMatrixData(type, limit, desc)
            if (_uiState.value.currentType != type) return@launch
            _uiState.value = _uiState.value.copy(
                isTrendLoading = false,
                trendMatrixData = data
            )
        }
    }

    fun setTrendLimit(limit: Int) {
        if (_uiState.value.trendMatrixLimit == limit) return
        _uiState.value = _uiState.value.copy(trendMatrixLimit = limit)
        loadTrendMatrixData()
    }

    fun toggleTrendOrder() {
        val newDesc = !_uiState.value.trendDescOrder
        _uiState.value = _uiState.value.copy(trendDescOrder = newDesc)
        loadTrendMatrixData()
    }
}
