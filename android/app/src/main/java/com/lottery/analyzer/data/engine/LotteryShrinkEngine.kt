package com.lottery.analyzer.data.engine

import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.data.model.ShrinkFilterConfig
import com.lottery.analyzer.data.model.ShrinkResult
import com.lottery.analyzer.data.model.WheelGuaranteeMode
import java.util.Locale

/**
 * 旋转矩阵计算内部结果
 */
private data class WheelCoverageResult(
    val bets: List<List<Int>>,
    val isComplete: Boolean,
    val coverageRatio: Float
)

/**
 * 复式缩水与旋转矩阵保奖计算引擎
 */
object LotteryShrinkEngine {
    /**
     * 执行全量复式展开、条件过滤与旋转矩阵保奖缩水
     */
    fun executeShrink(
        selectedBalls: List<Int>,
        type: LotteryType,
        config: ShrinkFilterConfig,
        lastDrawReds: Set<Int> = emptySet(),
        historyRedSets: Set<Set<Int>> = emptySet(),
        checkCancellation: (() -> Unit)? = null
    ): ShrinkResult {
        checkCancellation?.invoke()
        val startTime = System.currentTimeMillis()
        val distinctPool = selectedBalls.distinct().sorted()

        // 快乐8根据玩法配置动态取码(选一至选十)，双色球取 6 码，大乐透取 5 码
        val k = if (type == LotteryType.KL8) config.kl8PlayType.pickCount else type.redCount
        val titleText = if (type == LotteryType.KL8) "快乐8[${config.kl8PlayType.title}]" else type.title

        // 1. 底池最少选号数校验
        if (distinctPool.size < k) {
            return ShrinkResult(
                selectedBalls = distinctPool,
                originalTotalBets = 0,
                filteredBets = emptyList(),
                filteredCount = 0,
                shrinkRatio = 0f,
                originalCost = 0,
                filteredCost = 0,
                executionTimeMs = System.currentTimeMillis() - startTime,
                isStrictGuaranteed = false,
                coverageRatio = 0f,
                warningMessage = "选号池号码不足：$titleText 至少需要选择 $k 个红球"
            )
        }

        // 2. 底池规模上限限制 (防 OOM 内存溢出安全熔断)
        val maxPoolSize = when (type) {
            LotteryType.SSQ -> 16 // C(16, 6) = 8,008
            LotteryType.DLT -> 16 // C(16, 5) = 4,368
            LotteryType.KL8 -> 14 // C(14, 10) = 1,001 (安全控制贪心保奖计算在秒级内完成)
        }
        if (distinctPool.size > maxPoolSize) {
            return ShrinkResult(
                selectedBalls = distinctPool,
                originalTotalBets = 0,
                filteredBets = emptyList(),
                filteredCount = 0,
                shrinkRatio = 0f,
                originalCost = 0,
                filteredCost = 0,
                executionTimeMs = System.currentTimeMillis() - startTime,
                isStrictGuaranteed = false,
                coverageRatio = 0f,
                warningMessage = "选号池超出安全上限：$titleText 最多支持选 $maxPoolSize 码，以防止组合数过大造成内存耗尽！"
            )
        }

        // 3. 生成全复式注数组合 (支持协同取消)
        val allCombinations = generateCombinations(distinctPool, k, checkCancellation)
        val originalTotal = allCombinations.size

        // 4. 多维度形态条件过滤
        val conditionFiltered = allCombinations.filter { bet ->
            passesConditions(bet, config, lastDrawReds, historyRedSets)
        }

        // 核心修复：若条件过滤结果为空，直接返回无符合条件方案，严禁私自退回到未过滤集合！
        if (conditionFiltered.isEmpty()) {
            return ShrinkResult(
                selectedBalls = distinctPool,
                originalTotalBets = originalTotal,
                filteredBets = emptyList(),
                filteredCount = 0,
                shrinkRatio = 100f,
                originalCost = originalTotal * 2,
                filteredCost = 0,
                executionTimeMs = System.currentTimeMillis() - startTime,
                isStrictGuaranteed = false,
                coverageRatio = 0f,
                warningMessage = "所选过滤条件过于苛刻，无任何组合满足条件"
            )
        }

        // 5. 旋转矩阵保奖缩水 (Wheeling System)
        var isStrictGuaranteed = true
        var coverageRatio = 100f
        var warningMsg: String? = null
        val finalBets = when (config.wheelMode) {
            WheelGuaranteeMode.NONE -> conditionFiltered
            WheelGuaranteeMode.WHEEL_MATCH_MINUS_1 -> {
                val guaranteeM = maxOf(1, k - 1)
                val wheelResult = applyGreedyCoveringWheel(
                    candidateBets = conditionFiltered,
                    pool = distinctPool,
                    guaranteeM = guaranteeM,
                    checkCancellation = checkCancellation
                )
                isStrictGuaranteed = wheelResult.isComplete
                coverageRatio = wheelResult.coverageRatio
                if (!wheelResult.isComplete) {
                    warningMsg = "⚠️ 因过滤条件剔除了部分组合，旋转矩阵已破保 (实际保奖覆盖率: ${String.format(Locale.US, "%.1f", coverageRatio)}%)"
                }
                wheelResult.bets
            }
            WheelGuaranteeMode.WHEEL_MATCH_MINUS_2 -> {
                val guaranteeM = maxOf(1, k - 2)
                val wheelResult = applyGreedyCoveringWheel(
                    candidateBets = conditionFiltered,
                    pool = distinctPool,
                    guaranteeM = guaranteeM,
                    checkCancellation = checkCancellation
                )
                isStrictGuaranteed = wheelResult.isComplete
                coverageRatio = wheelResult.coverageRatio
                if (!wheelResult.isComplete) {
                    warningMsg = "⚠️ 因过滤条件剔除了部分组合，旋转矩阵已破保 (实际保奖覆盖率: ${String.format(Locale.US, "%.1f", coverageRatio)}%)"
                }
                wheelResult.bets
            }
        }

        val filteredCount = finalBets.size
        val shrinkRatio = if (originalTotal > 0) {
            ((originalTotal - filteredCount).toFloat() / originalTotal) * 100f
        } else 0f

        return ShrinkResult(
            selectedBalls = distinctPool,
            originalTotalBets = originalTotal,
            filteredBets = finalBets,
            filteredCount = filteredCount,
            shrinkRatio = shrinkRatio,
            originalCost = originalTotal * 2,
            filteredCost = filteredCount * 2,
            executionTimeMs = System.currentTimeMillis() - startTime,
            isStrictGuaranteed = isStrictGuaranteed,
            coverageRatio = coverageRatio,
            warningMessage = warningMsg
        )
    }

    /**
     * 单注条件校验
     */
    private fun passesConditions(
        bet: List<Int>,
        config: ShrinkFilterConfig,
        lastDrawReds: Set<Int>,
        historyRedSets: Set<Set<Int>>
    ): Boolean {
        // 1. 和值过滤
        if (config.enableSumFilter) {
            val sum = bet.sum()
            if (sum < config.sumMin || sum > config.sumMax) return false
        }

        // 2. 奇偶比过滤
        if (config.enableOddEvenFilter) {
            val oddCount = bet.count { it % 2 != 0 }
            if (oddCount !in config.allowedOddCounts) return false
        }

        // 3. 连号对数过滤
        if (config.enableConsecutiveFilter) {
            var consecutivePairs = 0
            for (i in 0 until bet.size - 1) {
                if (bet[i + 1] - bet[i] == 1) {
                    consecutivePairs++
                }
            }
            if (consecutivePairs !in config.allowedConsecutiveCounts) return false
        }

        // 4. 同尾号组数过滤
        if (config.enableSameTailFilter) {
            val tailMap = bet.groupBy { it % 10 }
            val hasSameTail = tailMap.values.any { it.size >= 2 }
            if (config.requireSameTail && !hasSameTail) return false
            if (!config.requireSameTail && hasSameTail) return false
        }

        // 5. 跨度过滤
        if (config.enableSpanFilter && bet.size >= 2) {
            val span = bet.last() - bet.first()
            if (span < config.spanMin || span > config.spanMax) return false
        }

        // 6. 上期重号个数过滤
        if (config.enableRepeatFilter && lastDrawReds.isNotEmpty()) {
            val repeatCount = bet.count { it in lastDrawReds }
            if (repeatCount !in config.allowedRepeatCounts) return false
        }

        // 7. 排除历史一等奖开出组合
        if (config.enableExcludeHistory && historyRedSets.isNotEmpty()) {
            if (bet.toSet() in historyRedSets) return false
        }

        return true
    }

    /**
     * 递归组合生成
     */
    fun generateCombinations(
        pool: List<Int>,
        k: Int,
        checkCancellation: (() -> Unit)? = null
    ): List<List<Int>> {
        val result = mutableListOf<List<Int>>()
        val current = mutableListOf<Int>()
        var count = 0

        fun backtrack(start: Int) {
            if (current.size == k) {
                result.add(ArrayList(current))
                count++
                if (count % 1000 == 0) {
                    checkCancellation?.invoke()
                }
                return
            }
            for (i in start until pool.size) {
                current.add(pool[i])
                backtrack(i + 1)
                current.removeAt(current.size - 1)
            }
        }

        backtrack(0)
        return result
    }

    /**
     * 基于贪心子集覆盖算法 (Greedy Set Cover) 的旋转矩阵保奖计算
     * 并精确返回是否完全达成保奖及实际覆盖率
     */
    private fun applyGreedyCoveringWheel(
        candidateBets: List<List<Int>>,
        pool: List<Int>,
        guaranteeM: Int,
        checkCancellation: (() -> Unit)? = null
    ): WheelCoverageResult {
        if (candidateBets.isEmpty()) return WheelCoverageResult(emptyList(), false, 0f)

        checkCancellation?.invoke()
        // 1. 生成大底中所有大小为 guaranteeM 的目标子集
        val allSubsetsM = generateCombinations(pool, guaranteeM, checkCancellation)
        if (allSubsetsM.isEmpty()) return WheelCoverageResult(candidateBets, true, 100f)

        val totalSubsets = allSubsetsM.size
        val uncoveredSubsets = (0 until totalSubsets).toMutableSet()

        // 2. 预计算每个候选注所能覆盖的子集索引
        val subsetToIndex = allSubsetsM.mapIndexed { index, list -> list to index }.toMap()
        val betCoverageMap = ArrayList<Set<Int>>(candidateBets.size)
        var betCounter = 0
        for (bet in candidateBets) {
            val subOfBet = generateCombinations(bet, guaranteeM, checkCancellation)
            val coveredIndices = subOfBet.mapNotNull { subsetToIndex[it] }.toSet()
            betCoverageMap.add(coveredIndices)
            betCounter++
            if (betCounter % 500 == 0) {
                checkCancellation?.invoke()
            }
        }

        val selectedBets = mutableListOf<List<Int>>()
        val availableBetIndices = (0 until candidateBets.size).toMutableSet()

        // 3. 贪心迭代：每次选取能够覆盖未覆盖子集最多的注 (每轮循环检查取消探针)
        var outerRound = 0
        while (uncoveredSubsets.isNotEmpty() && availableBetIndices.isNotEmpty()) {
            outerRound++
            if (outerRound % 10 == 0) {
                checkCancellation?.invoke()
            }
            var bestBetIndex = -1
            var maxGain = 0
            var innerCheck = 0
            for (betIdx in availableBetIndices) {
                innerCheck++
                if (innerCheck % 2000 == 0) {
                    checkCancellation?.invoke()
                }
                val coverage = betCoverageMap[betIdx]
                var gain = 0
                for (subIdx in coverage) {
                    if (subIdx in uncoveredSubsets) {
                        gain++
                    }
                }
                if (gain > maxGain) {
                    maxGain = gain
                    bestBetIndex = betIdx
                }
            }

            if (bestBetIndex == -1 || maxGain == 0) {
                // 剩余子集无法被当前候选集合覆盖（发生破保）
                break
            }

            selectedBets.add(candidateBets[bestBetIndex])
            availableBetIndices.remove(bestBetIndex)
            uncoveredSubsets.removeAll(betCoverageMap[bestBetIndex])
        }

        val coveredCount = totalSubsets - uncoveredSubsets.size
        val coverageRatio = if (totalSubsets > 0) (coveredCount.toFloat() / totalSubsets) * 100f else 100f
        val isComplete = uncoveredSubsets.isEmpty()
        return WheelCoverageResult(
            bets = selectedBets,
            isComplete = isComplete,
            coverageRatio = coverageRatio
        )
    }
}
