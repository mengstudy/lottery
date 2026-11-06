package com.lottery.analyzer.data.engine

import com.lottery.analyzer.data.model.LotteryRecord
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.data.model.MissingStat
import com.lottery.analyzer.data.model.NumberTrendSummary
import com.lottery.analyzer.data.model.TrendCell
import com.lottery.analyzer.data.model.TrendMatrixData
import com.lottery.analyzer.data.model.TrendRow

/**
 * 全景网格走势图与统计指标计算引擎
 */
object TrendMatrixEngine {

    /**
     * 生成全景网格走势图数据
     * @param ascRecords 升序排列的历史记录 (日期从旧到新)
     * @param missingMap 每一期开奖前的遗漏快照
     * @param limit 期数限制 (如 30、50、100 期)
     * @param descOrder 行是否倒序呈现 (最新期在最上面)
     */
    fun buildTrendMatrix(
        ascRecords: List<LotteryRecord>,
        missingMap: Map<String, MissingStat>,
        type: LotteryType,
        limit: Int = 30,
        descOrder: Boolean = true
    ): TrendMatrixData {
        if (ascRecords.isEmpty()) {
            return TrendMatrixData(type, emptyList(), emptyList(), emptyList())
        }

        val targetAsc = ascRecords.takeLast(limit)
        val maxRed = type.maxRedNumber
        val maxBlue = type.maxBlueNumber

        // 1. 生成单期行数据
        val rowsAsc = targetAsc.map { record ->
            val redSet = record.redBalls.toSet()
            val blueSet = record.blueBalls.toSet()
            val stat = missingMap[record.issue]

            val redCells = (1..maxRed).map { num ->
                val isHit = num in redSet
                val miss = if (isHit) 0 else (stat?.redMissing?.get(num) ?: 0)
                TrendCell(number = num, isHit = isHit, missing = miss)
            }

            val blueCells = if (maxBlue > 0) {
                (1..maxBlue).map { num ->
                    val isHit = num in blueSet
                    val miss = if (isHit) 0 else (stat?.blueMissing?.get(num) ?: 0)
                    TrendCell(number = num, isHit = isHit, missing = miss)
                }
            } else emptyList()

            val oddCount = record.redBalls.count { it % 2 != 0 }
            val evenCount = record.redBalls.size - oddCount

            TrendRow(
                issue = record.issue,
                drawDate = record.drawDate,
                redCells = redCells,
                blueCells = blueCells,
                sumValue = record.redBalls.sum(),
                oddEvenRatio = "$oddCount:$evenCount"
            )
        }

        // 2. 统计指标计算 (出现次数、平均遗漏、最大遗漏、最大连出、当前遗漏)
        val redSummaries = (1..maxRed).map { num ->
            calculateSummary(
                number = num,
                isBlue = false,
                ascRecords = targetAsc,
                missingMap = missingMap
            )
        }

        val blueSummaries = if (maxBlue > 0) {
            (1..maxBlue).map { num ->
                calculateSummary(
                    number = num,
                    isBlue = true,
                    ascRecords = targetAsc,
                    missingMap = missingMap
                )
            }
        } else emptyList()

        val finalRows = if (descOrder) rowsAsc.reversed() else rowsAsc

        return TrendMatrixData(
            lotteryType = type,
            rows = finalRows,
            redSummaries = redSummaries,
            blueSummaries = blueSummaries
        )
    }

    private fun calculateSummary(
        number: Int,
        isBlue: Boolean,
        ascRecords: List<LotteryRecord>,
        missingMap: Map<String, MissingStat>
    ): NumberTrendSummary {
        var hitCount = 0
        var maxMissing = 0
        var maxContinuous = 0
        var currentContinuous = 0
        var currentMissing = 0

        val totalPeriods = ascRecords.size
        for (i in ascRecords.indices) {
            val record = ascRecords[i]
            val isHit = if (isBlue) {
                number in record.blueBalls
            } else {
                number in record.redBalls
            }

            if (isHit) {
                hitCount++
                currentContinuous++
                if (currentContinuous > maxContinuous) {
                    maxContinuous = currentContinuous
                }
            } else {
                currentContinuous = 0
            }

            val stat = missingMap[record.issue]
            val miss = if (isBlue) {
                stat?.blueMissing?.get(number) ?: 0
            } else {
                stat?.redMissing?.get(number) ?: 0
            }
            if (miss > maxMissing) {
                maxMissing = miss
            }

            if (i == ascRecords.size - 1) {
                currentMissing = if (isHit) 0 else miss + 1
            }
        }

        val avgMissing = if (hitCount > 0) {
            (totalPeriods - hitCount).toFloat() / (hitCount + 1)
        } else {
            totalPeriods.toFloat()
        }

        return NumberTrendSummary(
            number = number,
            isBlue = isBlue,
            hitCount = hitCount,
            avgMissing = String.format("%.1f", avgMissing).toFloat(),
            maxMissing = maxMissing,
            maxContinuous = maxContinuous,
            currentMissing = currentMissing
        )
    }
}
