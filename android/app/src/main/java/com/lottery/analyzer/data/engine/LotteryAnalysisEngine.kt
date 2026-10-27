package com.lottery.analyzer.data.engine

import com.lottery.analyzer.data.model.IssueAnalysis
import com.lottery.analyzer.data.model.LotteryRecord
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.data.model.MissingGroupLevel
import com.lottery.analyzer.data.model.MissingStat

object LotteryAnalysisEngine {

    /**
     * 计算所有历史期次的遗漏值
     * @param ascRecords 必须按开奖日期和期号升序排列的记录
     */
    fun calculateAllMissingStats(
        ascRecords: List<LotteryRecord>,
        type: LotteryType
    ): Map<String, MissingStat> {
        if (ascRecords.isEmpty()) return emptyMap()

        val redMissingCounter = (1..type.maxRedNumber).associateWith { 0 }.toMutableMap()
        val blueMissingCounter = if (type.maxBlueNumber > 0) {
            (1..type.maxBlueNumber).associateWith { 0 }.toMutableMap()
        } else {
            mutableMapOf()
        }

        val resultMap = HashMap<String, MissingStat>(ascRecords.size)

        for (record in ascRecords) {
            // 开奖前的遗漏快照
            val stat = MissingStat(
                issue = record.issue,
                redMissing = redMissingCounter.toMap(),
                blueMissing = blueMissingCounter.toMap()
            )
            resultMap[record.issue] = stat

            // 更新红球遗漏：开出的号码重置为0，未开出的号码 +1
            val drawnRedSet = record.redBalls.toSet()
            for (num in 1..type.maxRedNumber) {
                if (num in drawnRedSet) {
                    redMissingCounter[num] = 0
                } else {
                    redMissingCounter[num] = (redMissingCounter[num] ?: 0) + 1
                }
            }

            // 更新蓝球遗漏
            if (type.maxBlueNumber > 0) {
                val drawnBlueSet = record.blueBalls.toSet()
                for (num in 1..type.maxBlueNumber) {
                    if (num in drawnBlueSet) {
                        blueMissingCounter[num] = 0
                    } else {
                        blueMissingCounter[num] = (blueMissingCounter[num] ?: 0) + 1
                    }
                }
            }
        }

        return resultMap
    }

    /**
     * 单期深度分析
     */
    fun analyze(
        record: LotteryRecord,
        missingStat: MissingStat?,
        type: LotteryType
    ): IssueAnalysis {
        val redBalls = record.redBalls.sorted()
        val blueBalls = record.blueBalls.sorted()

        // 1. 奇偶比
        val oddCount = redBalls.count { it % 2 != 0 }
        val evenCount = redBalls.size - oddCount
        val oddEvenRatio = "$oddCount:$evenCount"

        // 2. 区间比
        val zoneRatio = when (type) {
            LotteryType.SSQ -> {
                val z1 = redBalls.count { it in 1..11 }
                val z2 = redBalls.count { it in 12..22 }
                val z3 = redBalls.count { it in 23..33 }
                "$z1:$z2:$z3"
            }
            LotteryType.DLT -> {
                val z1 = redBalls.count { it in 1..12 }
                val z2 = redBalls.count { it in 13..24 }
                val z3 = redBalls.count { it in 25..35 }
                "$z1:$z2:$z3"
            }
            LotteryType.KL8 -> {
                val z1 = redBalls.count { it in 1..20 }
                val z2 = redBalls.count { it in 21..40 }
                val z3 = redBalls.count { it in 41..60 }
                val z4 = redBalls.count { it in 61..80 }
                "$z1:$z2:$z3:$z4"
            }
        }

        // 3. 和值与跨度
        val sumValue = redBalls.sum()
        val spanValue = if (redBalls.isNotEmpty()) redBalls.last() - redBalls.first() else 0

        // 4. 连号与同尾号
        val consecutivePairs = mutableListOf<String>()
        for (i in 0 until redBalls.size - 1) {
            if (redBalls[i + 1] - redBalls[i] == 1) {
                consecutivePairs.add(String.format("%02d-%02d", redBalls[i], redBalls[i + 1]))
            }
        }

        val tailMap = redBalls.groupBy { it % 10 }
        val sameTailPairs = tailMap.filter { it.value.size >= 2 }

        // 5. 遗漏值详情
        val redMissingValues = redBalls.map { missingStat?.redMissing?.get(it) ?: 0 }
        val blueMissingValues = blueBalls.map { missingStat?.blueMissing?.get(it) ?: 0 }
        val maxRedMissing = redMissingValues.maxOrNull() ?: 0
        val missingSum = redMissingValues.sum()

        // 6. 开出号码遗漏分组 (0~9+)
        val currentDrawnGroups = mutableMapOf<String, MutableList<Int>>()
        for (i in redBalls.indices) {
            val ball = redBalls[i]
            val missing = redMissingValues.getOrElse(i) { 0 }
            val levelKey = if (missing >= 9) "9+" else missing.toString()
            currentDrawnGroups.computeIfAbsent(levelKey) { mutableListOf() }.add(ball)
        }

        // 7. 本期全盘 0~9+ 遗漏层级分布
        val allMissingGroups = calculateGroupLevels(
            missingMap = missingStat?.redMissing ?: emptyMap(),
            maxNumber = type.maxRedNumber,
            hitNumbers = redBalls
        )

        // 8. 下一期遗漏层级模拟预测 (本期开出置0，未开出+1)
        val simulatedNextMissing = HashMap<Int, Int>()
        val drawnRedSet = redBalls.toSet()
        for (num in 1..type.maxRedNumber) {
            if (num in drawnRedSet) {
                simulatedNextMissing[num] = 0
            } else {
                val currentMiss = missingStat?.redMissing?.get(num) ?: 0
                simulatedNextMissing[num] = currentMiss + 1
            }
        }
        val nextSimulatedGroups = calculateGroupLevels(
            missingMap = simulatedNextMissing,
            maxNumber = type.maxRedNumber,
            hitNumbers = emptyList() // 下期预测无命中球
        )

        // 9. 冷温热分类
        val hotList = mutableListOf<Int>()
        val warmList = mutableListOf<Int>()
        val coldList = mutableListOf<Int>()

        for (num in 1..type.maxRedNumber) {
            val miss = missingStat?.redMissing?.get(num) ?: 0
            when {
                miss <= 4 -> hotList.add(num)
                miss in 5..9 -> warmList.add(num)
                else -> coldList.add(num)
            }
        }

        return IssueAnalysis(
            lotteryType = type,
            issue = record.issue,
            drawDate = record.drawDate,
            redBalls = redBalls,
            blueBalls = blueBalls,
            oddEvenRatio = oddEvenRatio,
            zoneRatio = zoneRatio,
            sumValue = sumValue,
            spanValue = spanValue,
            consecutivePairs = consecutivePairs,
            sameTailPairs = sameTailPairs,
            redMissingValues = redMissingValues,
            blueMissingValues = blueMissingValues,
            maxRedMissing = maxRedMissing,
            missingSum = missingSum,
            currentDrawnGroups = currentDrawnGroups,
            allMissingGroups = allMissingGroups,
            nextSimulatedGroups = nextSimulatedGroups,
            hotNumbers = hotList,
            warmNumbers = warmList,
            coldNumbers = coldList
        )
    }

    private fun calculateGroupLevels(
        missingMap: Map<Int, Int>,
        maxNumber: Int,
        hitNumbers: List<Int>
    ): List<MissingGroupLevel> {
        val hitSet = hitNumbers.toSet()
        val levelKeys = (0..8).map { it.toString() } + listOf("9+")
        val bucketMap = levelKeys.associateWith { mutableListOf<Int>() }

        for (num in 1..maxNumber) {
            val miss = missingMap[num] ?: 0
            val key = if (miss >= 9) "9+" else miss.toString()
            bucketMap[key]?.add(num)
        }

        return levelKeys.map { level ->
            val allInLevel = bucketMap[level] ?: emptyList()
            val hitInLevel = allInLevel.filter { it in hitSet }
            MissingGroupLevel(
                level = level,
                allNumbers = allInLevel.sorted(),
                hitNumbers = hitInLevel.sorted()
            )
        }
    }
}
