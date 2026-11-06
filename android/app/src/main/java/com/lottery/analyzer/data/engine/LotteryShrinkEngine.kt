package com.lottery.analyzer.data.engine

import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.data.model.ShrinkFilterConfig
import com.lottery.analyzer.data.model.ShrinkResult
import com.lottery.analyzer.data.model.WheelGuaranteeMode

/**
 * 专业复式缩水过滤与旋转矩阵 (Covering Wheel) 算法引擎
 */
object LotteryShrinkEngine {

    /**
     * 生成基础组合 C(n, k)
     */
    fun generateCombinations(elements: List<Int>, k: Int): List<List<Int>> {
        val results = mutableListOf<List<Int>>()
        val sorted = elements.sorted()

        fun backtrack(start: Int, current: MutableList<Int>) {
            if (current.size == k) {
                results.add(ArrayList(current))
                return
            }
            for (i in start until sorted.size) {
                current.add(sorted[i])
                backtrack(i + 1, current)
                current.removeAt(current.size - 1)
            }
        }

        backtrack(0, mutableListOf())
        return results
    }

    /**
     * 核心缩水计算方法
     * @param selectedBalls 用户自选的红球池 (如 7~16 码)
     * @param type 彩种类型
     * @param config 过滤与保奖配置
     * @param lastDrawReds 上期开奖红球 (用于重号计算)
     * @param historyRedSets 历史上所有开奖红球集合 (用于排重历史一等奖)
     */
    fun executeShrink(
        selectedBalls: List<Int>,
        type: LotteryType,
        config: ShrinkFilterConfig,
        lastDrawReds: Set<Int> = emptySet(),
        historyRedSets: Set<Set<Int>> = emptySet()
    ): ShrinkResult {
        val startTime = System.currentTimeMillis()
        val distinctPool = selectedBalls.distinct().sorted()
        val k = type.redCount // 双色球6, 大乐透5, 快乐8取10

        if (distinctPool.size < k) {
            return ShrinkResult(
                selectedBalls = distinctPool,
                originalTotalBets = 0,
                filteredBets = emptyList(),
                filteredCount = 0,
                shrinkRatio = 0f,
                originalCost = 0,
                filteredCost = 0,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        // 1. 生成所有初始全复式注数组合
        val allCombinations = generateCombinations(distinctPool, k)
        val originalTotal = allCombinations.size

        // 2. 多维度指标条件过滤
        val conditionFiltered = allCombinations.filter { bet ->
            passesConditions(bet, config, lastDrawReds, historyRedSets)
        }

        // 3. 旋转矩阵保奖缩水 (Wheeling System)
        val finalBets = when (config.wheelMode) {
            WheelGuaranteeMode.NONE -> conditionFiltered
            WheelGuaranteeMode.WHEEL_MATCH_MINUS_1 -> {
                // 中 k 保 k-1 (如双色球中6保5，大乐透中5保4)
                val guaranteeM = maxOf(1, k - 1)
                applyGreedyCoveringWheel(
                    candidateBets = if (conditionFiltered.isNotEmpty()) conditionFiltered else allCombinations,
                    pool = distinctPool,
                    k = k,
                    guaranteeM = guaranteeM
                )
            }
            WheelGuaranteeMode.WHEEL_MATCH_MINUS_2 -> {
                // 中 k 保 k-2 (如双色球中6保4，大乐透中5保3)
                val guaranteeM = maxOf(1, k - 2)
                applyGreedyCoveringWheel(
                    candidateBets = if (conditionFiltered.isNotEmpty()) conditionFiltered else allCombinations,
                    pool = distinctPool,
                    k = k,
                    guaranteeM = guaranteeM
                )
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
            executionTimeMs = System.currentTimeMillis() - startTime
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

        // 3. 跨度过滤
        if (config.enableSpanFilter) {
            val span = if (bet.isNotEmpty()) bet.last() - bet.first() else 0
            if (span < config.spanMin || span > config.spanMax) return false
        }

        // 4. 连号过滤
        if (config.enableConsecutiveFilter) {
            var consecutiveCount = 0
            for (i in 0 until bet.size - 1) {
                if (bet[i + 1] - bet[i] == 1) {
                    consecutiveCount++
                }
            }
            if (consecutiveCount !in config.allowedConsecutiveCounts) return false
        }

        // 5. 同尾号过滤
        if (config.enableSameTailFilter) {
            val hasSameTail = bet.groupBy { it % 10 }.any { it.value.size >= 2 }
            if (config.requireSameTail && !hasSameTail) return false
            if (!config.requireSameTail && hasSameTail) return false
        }

        // 6. 上期重号过滤
        if (config.enableRepeatFilter && lastDrawReds.isNotEmpty()) {
            val repeatCount = bet.count { it in lastDrawReds }
            if (repeatCount !in config.allowedRepeatCounts) return false
        }

        // 7. 历史一等奖号码排除
        if (config.enableExcludeHistory && historyRedSets.isNotEmpty()) {
            if (bet.toSet() in historyRedSets) return false
        }

        return true
    }

    /**
     * 快速贪心旋转矩阵子集覆盖算法 (Greedy Set Cover Covering Design)
     * 目标：从候选注中选出最小集合，覆盖所有大小为 m 的子集组合。
     * 数学保证：只要开奖红球命中用户大底，选出的注数中必有至少一注与开奖球重合数 >= guaranteeM。
     */
    private fun applyGreedyCoveringWheel(
        candidateBets: List<List<Int>>,
        pool: List<Int>,
        k: Int,
        guaranteeM: Int
    ): List<List<Int>> {
        if (candidateBets.isEmpty()) return emptyList()
        if (candidateBets.size <= 2) return candidateBets

        // 1. 生成大底中所有大小为 guaranteeM 的子集集合
        val allSubsetsM = generateCombinations(pool, guaranteeM)
        if (allSubsetsM.isEmpty()) return candidateBets

        // 编号子集以提高哈希与按位计算效率
        val uncoveredSubsets = (0 until allSubsetsM.size).toMutableSet()

        // 2. 预计算每个候选注所覆盖的 m-元子集索引
        val subsetToIndex = allSubsetsM.mapIndexed { index, list -> list to index }.toMap()
        val betCoverageMap = ArrayList<Set<Int>>(candidateBets.size)

        for (bet in candidateBets) {
            val subOfBet = generateCombinations(bet, guaranteeM)
            val coveredIndices = subOfBet.mapNotNull { subsetToIndex[it] }.toSet()
            betCoverageMap.add(coveredIndices)
        }

        val selectedBets = mutableListOf<List<Int>>()
        val availableBetIndices = (0 until candidateBets.size).toMutableSet()

        // 3. 贪心迭代：每次选取能够覆盖未覆盖子集最多的注
        while (uncoveredSubsets.isNotEmpty() && availableBetIndices.isNotEmpty()) {
            var bestBetIndex = -1
            var maxGain = 0

            for (betIdx in availableBetIndices) {
                val coverage = betCoverageMap[betIdx]
                // 计算该注能新覆盖的子集数量 (交集大小)
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
                // 剩余子集无法被当前可用候选注覆盖，退出
                break
            }

            selectedBets.add(candidateBets[bestBetIndex])
            availableBetIndices.remove(bestBetIndex)
            // 移除已覆盖的子集
            uncoveredSubsets.removeAll(betCoverageMap[bestBetIndex])
        }

        return selectedBets
    }
}
