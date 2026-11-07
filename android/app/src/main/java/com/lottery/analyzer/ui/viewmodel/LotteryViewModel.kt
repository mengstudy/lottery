package com.lottery.analyzer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.lottery.analyzer.data.model.*
import com.lottery.analyzer.data.db.AiSettingsStore
import com.lottery.analyzer.data.repository.LotteryRepositoryContract
import java.time.Instant
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
import com.lottery.analyzer.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import com.lottery.analyzer.data.model.NextIssuePolicy
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
    val isParsingCollectedText: Boolean = false,
    val frequencyResult: FrequencyAnalysisResult = FrequencyAnalysisResult(0, emptyMap(), emptyList()),
    val heartBallDistributions: List<HeartBallLevelDistribution> = emptyList(),
    // 预测与选号策略记录状态
    val nextIssueTarget: String = "",
    val predictionRecords: List<PredictionVerificationResult> = emptyList(),
    val predictionTotalCount: Int = 0,
    val predictionCurrentPage: Int = 0,
    val predictionPageSize: Int = 10,
    val predictionFeedback: String? = null,
    val predictionFeedbackId: Long = 0,
    val syncMessageId: Long = 0,
    val pendingPredictions: List<PredictionDraft> = emptyList(),
    val pendingTargetIssue: String = "",
    val isSavingPrediction: Boolean = false,
    val predictionSaveError: String? = null,
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

class LotteryViewModel @JvmOverloads constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val repository: LotteryRepositoryContract = LotteryRepository(application),
    private val aiConfigPreferences: AiSettingsStore = AiConfigPreferences(application),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : AndroidViewModel(application) {
    companion object {
        private val REGEX_NON_DIGIT = Regex("[^0-9]+")
        private val REGEX_BRACKETED_BET_LABEL = Regex("""[【\[\(（][^】\]\)）]*第?\s*\d+\s*注?[^】\]\)）]*[】\]\)）]""")
        private val REGEX_GENERAL_BRACKETS = Regex("""【[^】]*】|\[[^\]]*\]""")
        private val REGEX_STARTING_BET_PREFIX = Regex("""^(?:他人)?第?\s*\d+\s*注[:：\s]*""")
        private val REGEX_STARTING_BET_NUM = Regex("""^注\s*\d+[:：\s]*""")
        private val REGEX_STARTING_ORDER_PUNCT = Regex("""^\d+[\.\、\)]\s*""")
        private val REGEX_RED_LABEL = Regex("""(?:红球|前区|红)[:：]?""")
        private val REGEX_BLUE_LABEL = Regex("""(?:蓝球|后区|蓝)[:：]?""")
        private val REGEX_BLUE_REPLACE_PLUS = Regex("""(?:蓝球|后区)[:：]?""")
    }

    private val aiService = AiLotteryService()
    private val _uiState = MutableStateFlow(LotteryUiState())
    val uiState: StateFlow<LotteryUiState> = _uiState.asStateFlow()
    private var switchJob: Job? = null
    private var syncJob: Job? = null
    private var shrinkJob: Job? = null
    private var aiJob: Job? = null
    private var aiTestJob: Job? = null
    private var aiGeneration = 0
    private var trendJob: Job? = null
    private var issueJob: Job? = null
    private var predictionJob: Job? = null
    private val configMutex = Mutex()
    private var configGeneration = 0
    private var switchGeneration = 0
    private var feedbackGeneration = 0L
    private var messageGeneration = 0L
    private var syncGeneration = 0
    private var trendGeneration = 0
    private var shrinkGeneration = 0
    private var parseJob: Job? = null
    private var textParsingGeneration = 0

    private data class RestoredInput(val hearts: Set<Int>, val shrink: Set<Int>, val text: String, val play: Kl8PlayType)

    init {
        val restoredType = runCatching { LotteryType.valueOf(savedStateHandle.get<String>("type") ?: "SSQ") }.getOrDefault(LotteryType.SSQ)
        val restoredHearts = savedStateHandle.get<IntArray>("hearts")?.toSet().orEmpty()
        val restoredText = savedStateHandle.get<String>("collected").orEmpty()
        val restoredShrink = savedStateHandle.get<IntArray>("shrink")?.toSet().orEmpty()
        val restoredPlay = runCatching { Kl8PlayType.valueOf(savedStateHandle.get<String>("play") ?: "XUAN_TEN") }.getOrDefault(Kl8PlayType.XUAN_TEN)
        viewModelScope.launch {
            try {
                val config = configMutex.withLock { withContext(ioDispatcher) { aiConfigPreferences.getAiConfig() } }
                if (configGeneration == 0) _uiState.value = _uiState.value.copy(aiConfig = config)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (configGeneration == 0) _uiState.value = _uiState.value.copy(aiError = "AI配置读取失败，请重新配置：${e.message}")
            }
        }
        switchLotteryType(restoredType, RestoredInput(restoredHearts, restoredShrink, restoredText, restoredPlay))
    }

    /**
     * 推算下一期期号（基于当前数据库最新期）
     */
    private fun computeNextIssue(latestIssue: String): String {
        if (latestIssue.isBlank()) return ""
        return NextIssuePolicy.suggest(_uiState.value.recordsDesc.firstOrNull { it.issue == latestIssue })
    }

    /**
     * 切换彩种
     */
    fun switchLotteryType(type: LotteryType) = switchLotteryType(type, null)

    private fun switchLotteryType(type: LotteryType, restored: RestoredInput?) {
        switchGeneration++
        savedStateHandle["type"] = type.name
        savedStateHandle["hearts"] = intArrayOf()
        savedStateHandle["shrink"] = intArrayOf()
        savedStateHandle["collected"] = ""
        aiGeneration++
        aiTestJob?.cancel()
        syncGeneration++
        trendGeneration++
        shrinkGeneration++
        textParsingGeneration++
        parseJob?.cancel()
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
                heartBallDistributions = emptyList(),
                analysis = null,
                recordsDesc = emptyList(),
                currentIssue = "",
                nextIssueTarget = "",
                predictionFeedback = null,
                pendingPredictions = emptyList(), pendingTargetIssue = "", predictionSaveError = null,
                isSavingPrediction = false,
                predictionRecords = emptyList(),
                predictionTotalCount = 0,
                predictionCurrentPage = 0,
                isParsingCollectedText = false,
                collectedBallsText = "",
                frequencyResult = FrequencyAnalysisResult(
                    validGroupsCount = 0,
                    frequencyMap = emptyMap(),
                    sortedByFreq = emptyList(),
                    parsedBets = emptyList(),
                    blueFrequencyMap = emptyMap(),
                    sortedBluesByFreq = emptyList(),
                    parsedCompleteBets = emptyList()
                ),
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
            if (restored != null) {
                setHeartBalls(restored.hearts.filter { it in 1..type.maxRedNumber }.toSet())
                setShrinkBalls(restored.shrink.filter { it in 1..type.maxRedNumber }.toSet())
                setKl8PlayType(restored.play)
                updateCollectedBallsText(restored.text)
            }
            try {
                val records = repository.getRecordsDesc(type)
                // 彩种异步一致性守卫：若彩种已被再次切换，终止过时赋值
                if (_uiState.value.currentType != type) return@launch

                val firstIssue = records.firstOrNull()?.issue ?: ""
                val analysis = if (firstIssue.isNotEmpty()) {
                    repository.getAnalysisForIssue(type, firstIssue)
                } else null

                val nextIssue = NextIssuePolicy.suggest(records.firstOrNull())

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
        savedStateHandle["hearts"] = current.toIntArray()
        _uiState.value = _uiState.value.copy(myHeartBalls = current)
        recalcHeartDistributions()
    }

    /**
     * 批量设置心水红球
     */
    fun setHeartBalls(balls: Set<Int>) {
        savedStateHandle["hearts"] = balls.toIntArray()
        _uiState.value = _uiState.value.copy(myHeartBalls = balls)
        recalcHeartDistributions()
    }

    /**
     * 清空心水红球
     */
    fun clearHeartBalls() {
        savedStateHandle["hearts"] = intArrayOf()
        _uiState.value = _uiState.value.copy(myHeartBalls = emptySet(), heartBallDistributions = emptyList())
    }

    /**
     * 更新他人购买号码文本并进行频次解析与注数提取 (支持1行1注)
     */
    fun updateCollectedBallsText(text: String) {
        savedStateHandle["collected"] = text.takeIf { it.length <= 20000 }.orEmpty()
        val currentType = _uiState.value.currentType
        val currentGen = ++textParsingGeneration
        parseJob?.cancel()

        if (text.isBlank()) {
            _uiState.value = _uiState.value.copy(
                collectedBallsText = "",
                isParsingCollectedText = false,
                frequencyResult = FrequencyAnalysisResult(
                    validGroupsCount = 0,
                    frequencyMap = emptyMap(),
                    sortedByFreq = emptyList(),
                    parsedBets = emptyList(),
                    blueFrequencyMap = emptyMap(),
                    sortedBluesByFreq = emptyList(),
                    parsedCompleteBets = emptyList()
                )
            )
            return
        }

        // 主线程即时回显文本与加载指示器
        _uiState.value = _uiState.value.copy(
            collectedBallsText = text,
            isParsingCollectedText = true
        )

        parseJob = viewModelScope.launch(computeDispatcher) {
            delay(200) // 200ms 防抖
            ensureActive()

            val maxRed = currentType.maxRedNumber
            val maxBlue = currentType.maxBlueNumber
            val freqMap = mutableMapOf<Int, Int>()
            val blueFreqMap = mutableMapOf<Int, Int>()
            val parsedBets = mutableListOf<List<Int>>()
            val parsedCompleteBets = mutableListOf<Pair<List<Int>, List<Int>>>()
            var validGroups = 0

            fun parseSegment(segment: String, maxVal: Int): List<Int> {
                val trimmed = segment.trim()
                if (trimmed.isBlank() || maxVal <= 0) return emptyList()

                val tokens = trimmed.split(REGEX_NON_DIGIT).filter { it.isNotBlank() }
                val result = mutableListOf<Int>()
                for (token in tokens) {
                    if (token.length <= 2) {
                        val num = token.toIntOrNull()
                        if (num != null && num in 1..maxVal && num !in result) {
                            result.add(num)
                        }
                    } else {
                        var i = 0
                        while (i + 1 < token.length) {
                            val twoDigits = token.substring(i, i + 2)
                            val num = twoDigits.toIntOrNull()
                            if (num != null && num in 1..maxVal && num !in result) {
                                result.add(num)
                            }
                            i += 2
                        }
                    }
                }
                return result.sorted()
            }

            val lineList = text.split("\n")
            for ((idx, line) in lineList.withIndex()) {
                if (idx % 50 == 0) {
                    ensureActive()
                }
                val trimmedLine = line.trim()
                if (trimmedLine.isBlank()) continue

                var cleanLine = trimmedLine
                    .replace(REGEX_BRACKETED_BET_LABEL, " ")
                    .replace(REGEX_GENERAL_BRACKETS, " ")
                    .trim()

                cleanLine = cleanLine
                    .replaceFirst(REGEX_STARTING_BET_PREFIX, "")
                    .replaceFirst(REGEX_STARTING_BET_NUM, "")
                    .replaceFirst(REGEX_STARTING_ORDER_PUNCT, "")
                    .trim()

                cleanLine = cleanLine
                    .replace(REGEX_RED_LABEL, "")
                    .replace(REGEX_BLUE_REPLACE_PLUS, "+")
                    .trim()

                val rowReds = mutableListOf<Int>()
                val rowBlues = mutableListOf<Int>()
                if (cleanLine.contains("+")) {
                    val parts = cleanLine.split("+")
                    val redPart = parts.firstOrNull().orEmpty()
                    val bluePart = parts.drop(1).joinToString(" ")
                    rowReds.addAll(parseSegment(redPart, maxRed))
                    rowBlues.addAll(parseSegment(bluePart, maxBlue))
                } else {
                    rowReds.addAll(parseSegment(cleanLine, maxRed))
                }

                if (rowReds.isNotEmpty() || rowBlues.isNotEmpty()) {
                    validGroups++
                    if (rowReds.isNotEmpty()) {
                        parsedBets.add(rowReds)
                    }
                    parsedCompleteBets.add(Pair(rowReds, rowBlues))
                    for (r in rowReds) {
                        freqMap[r] = (freqMap[r] ?: 0) + 1
                    }
                    for (b in rowBlues) {
                        blueFreqMap[b] = (blueFreqMap[b] ?: 0) + 1
                    }
                }
            }

            ensureActive()
            val sortedReds = freqMap.toList().sortedByDescending { it.second }
            val sortedBlues = blueFreqMap.toList().sortedByDescending { it.second }
            val parseResult = FrequencyAnalysisResult(
                validGroupsCount = validGroups,
                frequencyMap = freqMap,
                sortedByFreq = sortedReds,
                parsedBets = parsedBets,
                blueFrequencyMap = blueFreqMap,
                sortedBluesByFreq = sortedBlues,
                parsedCompleteBets = parsedCompleteBets
            )

            // 核心修复 (P2-1)：后台计算完毕后切回主线程校验代数并更新，杜绝竞态覆盖清空
            withContext(Dispatchers.Main.immediate) {
                if (currentGen == textParsingGeneration && _uiState.value.currentType == currentType) {
                    _uiState.value = _uiState.value.copy(
                        isParsingCollectedText = false,
                        frequencyResult = parseResult
                    )
                }
            }
        }
    }

    /**
     * 清空他人购买号码
     */
    fun clearCollectedBalls() {
        savedStateHandle["collected"] = ""
        textParsingGeneration++
        parseJob?.cancel()
        _uiState.value = _uiState.value.copy(
            collectedBallsText = "",
            isParsingCollectedText = false,
            frequencyResult = FrequencyAnalysisResult(
                validGroupsCount = 0,
                frequencyMap = emptyMap(),
                sortedByFreq = emptyList(),
                parsedBets = emptyList(),
                blueFrequencyMap = emptyMap(),
                sortedBluesByFreq = emptyList(),
                parsedCompleteBets = emptyList()
            )
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
    fun loadPredictions(page: Int = _uiState.value.predictionCurrentPage) {
        val targetType = _uiState.value.currentType
        val pageSize = _uiState.value.predictionPageSize
        predictionJob?.cancel()
        predictionJob = viewModelScope.launch {
            try {
                val result = repository.getVerifiedPredictionsPaged(targetType, page, pageSize)
                if (_uiState.value.currentType != targetType) return@launch
                _uiState.value = _uiState.value.copy(
                    predictionRecords = result.records,
                    predictionTotalCount = result.totalCount,
                    predictionCurrentPage = result.validPage
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("LotteryViewModel", "加载预测记录失败: ${e.message}", e)
            }
        }
    }

    /**
     * 保存一条新的预测与选号策略记录（期号默认为预测下一期）
     */
    fun savePredictionRecord(
        redBalls: List<Int>, blueBalls: List<Int> = emptyList(), strategy: String,
        targetIssue: String = "", kind: PredictionKind? = null,
        playType: Kl8PlayType? = null, blueSource: BlueSource = BlueSource.PROVIDED
    ) {
        val type = _uiState.value.currentType
        val resolvedKind = kind ?: PredictionRules.classify(type, redBalls, blueBalls)
        val draft = PredictionDraft(type, redBalls.sorted(), blueBalls.sorted(), strategy,
            resolvedKind,
            playType ?: if (resolvedKind == PredictionKind.SINGLE) PredictionRules.inferPlay(type, redBalls) else null,
            if (blueBalls.isEmpty()) BlueSource.NONE else blueSource)
        try { PredictionRules.validate(draft) }
        catch (e: Exception) { feedback(e.message ?: "号码无效"); return }
        if (_uiState.value.isSavingPrediction) return
        _uiState.value = _uiState.value.copy(
            pendingPredictions = _uiState.value.pendingPredictions + draft,
            pendingTargetIssue = targetIssue.ifBlank { _uiState.value.nextIssueTarget }, predictionSaveError = null
        )
    }

    fun dismissPendingPredictions() {
        if (!_uiState.value.isSavingPrediction) {
            _uiState.value = _uiState.value.copy(pendingPredictions = emptyList(), predictionSaveError = null)
        }
    }

    fun confirmPendingPredictions(targetIssue: String, blueOverride: List<Int>? = null) {
        if (_uiState.value.isSavingPrediction) return
        val pending = _uiState.value.pendingPredictions
        if (pending.isEmpty()) return
        val type = _uiState.value.currentType
        val generation = switchGeneration
        val records = try {
            PredictionRules.validateTarget(targetIssue.trim())
            val time = Instant.now()
            pending.map { original ->
                val draft = if (pending.size == 1 && blueOverride != null) original.copy(blueBalls = blueOverride.sorted(),
                    blueSource = if (blueOverride.sorted() == original.blueBalls) original.blueSource else BlueSource.PROVIDED) else original
                require(draft.lotteryType == type) { "彩种已改变，请重新选择方案" }
                PredictionRules.validate(draft)
                PredictionRecord(lotteryType = type, targetIssue = targetIssue.trim(), redBalls = draft.redBalls,
                    blueBalls = draft.blueBalls, strategy = draft.strategy, createdAt = time.toString(),
                    kind = draft.kind, kl8PlayType = draft.kl8PlayType, blueSource = draft.blueSource,
                    createdAtEpoch = time.toEpochMilli())
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(predictionSaveError = e.message ?: "方案无效")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingPrediction = true, predictionSaveError = null)
            try {
                val count = repository.savePredictions(records)
                check(count == records.size) { "方案未全部保存" }
                if (_uiState.value.currentType == type && generation == switchGeneration) {
                    _uiState.value = _uiState.value.copy(pendingPredictions = emptyList())
                    loadPredictions(0)
                    feedback("已保存 $count 条第 ${targetIssue.trim()} 期方案")
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (_uiState.value.currentType == type && generation == switchGeneration) _uiState.value = _uiState.value.copy(predictionSaveError = "保存失败：${e.message}")
            } finally {
                if (_uiState.value.currentType == type && generation == switchGeneration) _uiState.value = _uiState.value.copy(isSavingPrediction = false)
            }
        }
    }

    fun deletePredictionRecord(id: Long) {
        val type = _uiState.value.currentType
        viewModelScope.launch {
            try {
                check(repository.deletePrediction(id)) { "记录不存在或已被删除" }
                if (_uiState.value.currentType == type) { loadPredictions(); feedback("已删除预测记录") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (_uiState.value.currentType == type) feedback("删除失败：${e.message}") }
        }
    }

    fun clearPredictionRecords() {
        val type = _uiState.value.currentType
        viewModelScope.launch {
            try {
                check(repository.clearPredictions(type)) { "清空失败" }
                if (_uiState.value.currentType == type) { loadPredictions(0); feedback("已清空预测记录") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (_uiState.value.currentType == type) feedback("清空失败：${e.message}") }
        }
    }

    private fun feedback(message: String) {
        _uiState.value = _uiState.value.copy(predictionFeedback = message, predictionFeedbackId = ++feedbackGeneration)
    }

    fun consumePredictionFeedback(id: Long) {
        if (_uiState.value.predictionFeedbackId == id) _uiState.value = _uiState.value.copy(predictionFeedback = null)
    }

    fun consumeSyncMessage(id: Long) {
        if (_uiState.value.syncMessageId == id) _uiState.value = _uiState.value.copy(syncMessage = null)
    }

    /**
     * 联网同步最新开奖数据（同步完成后自动重算预测比对）
     */
    fun syncLatestData() = launchSync(fullHistory = false)

    fun syncAllHistoryData() = launchSync(fullHistory = true)

    private fun launchSync(fullHistory: Boolean) {
        val type = _uiState.value.currentType
        val generation = ++syncGeneration
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSyncing = !fullHistory, isSyncingAll = fullHistory,
                syncAllProgress = if (fullHistory) "正在连接数据源…" else null, syncMessage = null
            )
            try {
                val result = if (fullHistory) repository.syncAllHistoryData(type) { page, count ->
                    withContext(Dispatchers.Main.immediate) {
                        if (generation == syncGeneration && _uiState.value.currentType == type) {
                            _uiState.value = _uiState.value.copy(syncAllProgress = "正在拉取第 $page 页，已入库 $count 条")
                        }
                    }
                } else repository.syncLatestData(type)
                val records = repository.getRecordsDesc(type)
                val first = records.firstOrNull()
                val analysis = first?.let { repository.getAnalysisForIssue(type, it.issue) }
                if (generation != syncGeneration || _uiState.value.currentType != type) return@launch
                val message = result.fold(
                    onSuccess = { report ->
                        "同步结束，更新 ${report.updatedCount} 条" +
                            if (report.invalidCount > 0) "；${report.invalidCount} 条异常记录未导入，请查看日志" else ""
                    },
                    onFailure = { "同步未完成：${it.message ?: "数据源异常"}" }
                )
                _uiState.value = _uiState.value.copy(
                    recordsDesc = records, currentIssue = first?.issue.orEmpty(), analysis = analysis,
                    nextIssueTarget = NextIssuePolicy.suggest(first), syncMessage = message, syncMessageId = ++messageGeneration
                )
                recalcHeartDistributions()
                loadPredictions()
                if (fullHistory) loadTrendMatrixData()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == syncGeneration && _uiState.value.currentType == type) {
                    _uiState.value = _uiState.value.copy(syncMessage = "同步失败：${e.message ?: "数据读取异常"}", syncMessageId = ++messageGeneration)
                }
            } finally {
                if (generation == syncGeneration) {
                    _uiState.value = _uiState.value.copy(isSyncing = false, isSyncingAll = false, syncAllProgress = null)
                }
            }
        }
    }

    /**
     * 获取指定球号的走势
     */
    suspend fun getNumberTrend(number: Int, isBlue: Boolean = false): List<Pair<String, Int>> {
        return try { repository.getNumberTrend(_uiState.value.currentType, number, isBlue) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = "号码走势加载失败：${e.message}")
            emptyList()
        }
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
        val generation = ++configGeneration
        clearAiResult()
        viewModelScope.launch {
            try {
                configMutex.withLock { withContext(ioDispatcher) { aiConfigPreferences.saveAiConfig(config) } }
                if (generation == configGeneration) _uiState.value = _uiState.value.copy(aiConfig = config, aiError = null)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == configGeneration) _uiState.value = _uiState.value.copy(aiError = "配置保存失败：${e.message}", showAiSettingsDialog = true) }
        }
    }

    fun selectAiProviderPreset(provider: AiProvider) {
        val generation = ++configGeneration
        clearAiResult()
        viewModelScope.launch {
            try {
                val config = configMutex.withLock { withContext(ioDispatcher) { aiConfigPreferences.selectProviderPreset(provider) } }
                if (generation == configGeneration) _uiState.value = _uiState.value.copy(aiConfig = config)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == configGeneration) _uiState.value = _uiState.value.copy(aiError = "平台切换失败：${e.message}") }
        }
    }

    /**
     * 测试 AI API 连通性
     */
    fun testAiConnection(config: AiConfig = _uiState.value.aiConfig) {
        val generation = ++aiGeneration
        aiTestJob?.cancel()
        aiJob?.cancel()
        aiTestJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAiLoading = true, aiTestMessage = null, aiError = null)
            try {
                val result = aiService.testConnection(config)
                if (generation == aiGeneration) _uiState.value = _uiState.value.copy(
                    aiTestMessage = result.fold({ "✅ 连接成功：$it" }, { "❌ 连接失败：${it.message}" })
                )
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == aiGeneration) _uiState.value = _uiState.value.copy(aiTestMessage = "❌ 连接失败：${e.message}")
            } finally {
                if (generation == aiGeneration) _uiState.value = _uiState.value.copy(isAiLoading = false)
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
        savedStateHandle["play"] = playType.name
        _uiState.value = _uiState.value.copy(
            kl8PlayType = playType,
            shrinkConfig = _uiState.value.shrinkConfig.copy(kl8PlayType = playType)
        )
    }

    fun runAiAnalysis(mode: AiAnalysisMode, betCount: Int = 3) {
        val state = _uiState.value
        if (state.isParsingCollectedText) {
            _uiState.value = state.copy(aiError = "他人号码正在解析，请稍后再发起推荐")
            return
        }
        val analysis = state.analysis
        if (analysis == null) {
            _uiState.value = state.copy(aiError = "尚未加载到当前彩种分析数据")
            return
        }
        if (state.aiConfig.apiKey.isBlank()) {
            _uiState.value = state.copy(showAiSettingsDialog = true, aiError = "请先配置 AI API Key")
            return
        }
        val generation = ++aiGeneration
        aiTestJob?.cancel()
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAiLoading = true, aiError = null, aiResult = null)
            try {
                val result = aiService.generateRecommendation(
                    config = state.aiConfig, lotteryType = state.currentType, analysis = analysis,
                    targetNextIssue = state.nextIssueTarget, mode = mode, userSelection = state.myHeartBalls,
                    topOtherBalls = state.frequencyResult.sortedByFreq, topOtherBlues = state.frequencyResult.sortedBluesByFreq,
                    otherBets = state.frequencyResult.parsedBets, otherCompleteBets = state.frequencyResult.parsedCompleteBets,
                    betCount = betCount, kl8PlayType = state.kl8PlayType
                )
                if (generation != aiGeneration || _uiState.value.currentType != state.currentType) return@launch
                _uiState.value = _uiState.value.copy(aiResult = result.getOrNull(), aiError = result.exceptionOrNull()?.message)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == aiGeneration) _uiState.value = _uiState.value.copy(aiError = e.message ?: "AI推演失败")
            } finally {
                if (generation == aiGeneration) _uiState.value = _uiState.value.copy(isAiLoading = false)
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
                targetIssue = target, kind = PredictionKind.SINGLE, playType = result.kl8PlayType
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
        aiGeneration++
        aiJob?.cancel()
        aiTestJob?.cancel()
        _uiState.value = _uiState.value.copy(aiResult = null, aiError = null, isAiLoading = false)
    }

    // ==================== 复式缩水与旋转矩阵控制 ====================
    fun toggleShrinkBall(ball: Int) {
        val current = _uiState.value.shrinkSelectedBalls.toMutableSet()
        if (ball in current) current.remove(ball) else current.add(ball)
        savedStateHandle["shrink"] = current.toIntArray()
        _uiState.value = _uiState.value.copy(shrinkSelectedBalls = current)
    }

    fun setShrinkBalls(balls: Set<Int>) {
        savedStateHandle["shrink"] = balls.toIntArray()
        _uiState.value = _uiState.value.copy(shrinkSelectedBalls = balls)
    }

    fun clearShrinkBalls() {
        savedStateHandle["shrink"] = intArrayOf()
        _uiState.value = _uiState.value.copy(
            shrinkSelectedBalls = emptySet(),
            shrinkResult = null
        )
    }

    fun importHeartToShrink() {
        val hearts = _uiState.value.myHeartBalls
        setShrinkBalls(hearts)
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
        val currentGen = ++shrinkGeneration
        shrinkJob?.cancel()
        shrinkJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isShrinking = true, errorMessage = null)
            try {
                val result = repository.executeShrink(targetType, selected, config)
                // 核心修复 (P2-3)：校验当前任务版本与彩种，旧任务结果直接废弃
                if (currentGen != shrinkGeneration || _uiState.value.currentType != targetType) return@launch

                _uiState.value = _uiState.value.copy(
                    shrinkResult = result
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (currentGen == shrinkGeneration && _uiState.value.currentType == targetType) {
                    _uiState.value = _uiState.value.copy(errorMessage = "缩水计算失败: ${e.message}")
                }
            } finally {
                // 核心修复 (P2-3)：严格校验 currentGen == shrinkGeneration，旧任务退出绝不冲掉新任务的 isShrinking
                if (currentGen == shrinkGeneration) {
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
        savePredictionRecord(redBalls = reds, blueBalls = blueList, strategy = strategy,
            kind = PredictionKind.SINGLE, playType = if (type == LotteryType.KL8) _uiState.value.shrinkConfig.kl8PlayType else null,
            blueSource = if (blueList.isEmpty()) BlueSource.NONE else BlueSource.GENERATED_CONFIRMED)
    }

    // ==================== 全景网格走势图控制 ====================
    fun loadTrendMatrixData() {
        val type = _uiState.value.currentType
        val limit = _uiState.value.trendMatrixLimit
        val desc = _uiState.value.trendDescOrder
        val generation = ++trendGeneration
        trendJob?.cancel()
        trendJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTrendLoading = true)
            try {
                val data = repository.getTrendMatrixData(type, limit, desc)
                if (generation == trendGeneration && _uiState.value.currentType == type) {
                    _uiState.value = _uiState.value.copy(trendMatrixData = data)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == trendGeneration && _uiState.value.currentType == type) {
                    _uiState.value = _uiState.value.copy(errorMessage = "走势加载失败：${e.message}")
                }
            } finally {
                if (generation == trendGeneration) _uiState.value = _uiState.value.copy(isTrendLoading = false)
            }
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
