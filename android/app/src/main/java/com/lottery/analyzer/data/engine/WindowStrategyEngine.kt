package com.lottery.analyzer.data.engine

import com.lottery.analyzer.data.model.*
import java.math.BigInteger
import kotlin.math.sqrt
import kotlin.random.Random

enum class WindowStrategy(val title: String) {
    BALANCED("冷热均衡"), HOT("偏重高频"), COLD("偏重低频"),
    SEGMENT("分区均衡"), CONSECUTIVE("包含连号")
}

data class WindowFrequencyStats(
    val type: LotteryType,
    val requestedPeriods: Int,
    val sampleCount: Int,
    val firstIssue: String,
    val lastIssue: String,
    val excludedCount: Int,
    val redFrequency: Map<Int, Int>,
    val blueFrequency: Map<Int, Int>,
    val sumMean: Double,
    val sumMedian: Double,
    val sumStdDev: Double,
    val sumHistogram: Map<Int, Int>,
    val oddHistogram: Map<Int, Int>,
    val consecutiveHistogram: Map<Int, Int>
) {
    fun ranked(blue: Boolean = false, cold: Boolean = false): List<Int> {
        val frequency = if (blue) blueFrequency else redFrequency
        return frequency.keys.sortedWith(compareBy<Int> {
            if (cold) frequency.getValue(it) else -frequency.getValue(it)
        }.thenBy { it })
    }
}

data class WindowGeneration(
    val stats: WindowFrequencyStats,
    val strategy: WindowStrategy,
    val redCount: Int,
    val blueCount: Int,
    val seed: Long,
    val bets: List<GeneratedLotteryBet>
) {
    val combinationsPerGroup: Long get() = WindowStrategyEngine.combinationCount(stats.type, redCount, blueCount)
    // Existing strategy text is persisted with the draft; no database migration is needed.
    fun description(index: Int): String =
        "频次v1/${strategy.title}/${stats.requestedPeriods}期(实${stats.sampleCount})/${stats.firstIssue}-${stats.lastIssue}/${redCount}+${blueCount}/seed=$seed/组${index + 1}"
}

/** Local descriptive statistics and reproducible heuristics; no network or prediction model. */
object WindowStrategyEngine {
    private fun supported(type: LotteryType) = require(type != LotteryType.KL8) { "频次策略暂支持双色球和大乐透" }

    fun analyze(
        type: LotteryType, records: List<LotteryRecord>, periods: Int,
        checkCancelled: () -> Unit = {}
    ): WindowFrequencyStats {
        supported(type)
        require(periods in 1..1000) { "统计窗口须为1~1000期" }
        var excluded = 0
        val unique = linkedMapOf<BigInteger, LotteryRecord>()
        for (record in records) {
            checkCancelled()
            val validIssue = record.issue.length in 5..12 && record.issue.all { it in '0'..'9' }
            val validBalls = record.redBalls.size == type.redCount && record.blueBalls.size == type.blueCount &&
                record.redBalls.distinct().size == type.redCount && record.redBalls.all { it in 1..type.maxRedNumber } &&
                record.blueBalls.distinct().size == type.blueCount && record.blueBalls.all { it in 1..type.maxBlueNumber }
            if (!validIssue || !validBalls) { excluded++; continue }
            val issue = record.issue.toBigInteger()
            val old = unique[issue]
            if (old != null) {
                require(old.redBalls.toSet() == record.redBalls.toSet() && old.blueBalls.toSet() == record.blueBalls.toSet()) {
                    "同一期号存在不同开奖号码：${record.issue}"
                }
                excluded++
            } else unique[issue] = record
        }
        checkCancelled()
        val selected = unique.entries.sortedByDescending { it.key }.take(periods).map { it.value }
        require(selected.isNotEmpty()) { "没有可分析的有效开奖记录，请先同步数据" }
        val reds = (1..type.maxRedNumber).associateWith { 0 }.toMutableMap()
        val blues = (1..type.maxBlueNumber).associateWith { 0 }.toMutableMap()
        val sums = mutableListOf<Int>()
        val bins = sortedMapOf<Int, Int>()
        val odds = sortedMapOf<Int, Int>()
        val consecutive = sortedMapOf<Int, Int>()
        for (record in selected) {
            checkCancelled()
            record.redBalls.forEach { reds[it] = reds.getValue(it) + 1 }
            record.blueBalls.forEach { blues[it] = blues.getValue(it) + 1 }
            val sum = record.redBalls.sum()
            sums += sum
            val bin = ((sum - 1) / 20) * 20 + 1
            bins[bin] = (bins[bin] ?: 0) + 1
            val odd = record.redBalls.count { it % 2 != 0 }
            odds[odd] = (odds[odd] ?: 0) + 1
            val pairs = record.redBalls.sorted().zipWithNext().count { (a, b) -> b == a + 1 }
            consecutive[pairs] = (consecutive[pairs] ?: 0) + 1
        }
        val mean = sums.average()
        val orderedSums = sums.sorted()
        val median = (orderedSums[(sums.size - 1) / 2].toDouble() + orderedSums[sums.size / 2]) / 2
        val deviation = if (sums.size < 2) 0.0 else sqrt(sums.sumOf { (it - mean) * (it - mean) } / (sums.size - 1))
        return WindowFrequencyStats(type, periods, selected.size, selected.last().issue, selected.first().issue,
            excluded, reds.toMap(), blues.toMap(), mean, median, deviation, bins.toMap(), odds.toMap(), consecutive.toMap())
    }

    fun combinationCount(type: LotteryType, reds: Int, blues: Int): Long {
        supported(type)
        require(reds in type.redCount..type.maxRedNumber && blues in type.blueCount..type.maxBlueNumber) { "选号数量不合法" }
        fun choose(n: Int, k: Int): BigInteger {
            var value = BigInteger.ONE
            for (i in 1..minOf(k, n - k)) value = value * (n - i + 1).toBigInteger() / i.toBigInteger()
            return value
        }
        val total = choose(reds, type.redCount) * choose(blues, type.blueCount)
        require(total.bitLength() <= 63) { "组合注数超出可表示范围" }
        return total.toLong()
    }

    fun generate(
        stats: WindowFrequencyStats, strategy: WindowStrategy, redCount: Int, blueCount: Int,
        count: Int, seed: Long, checkCancelled: () -> Unit = {}
    ): WindowGeneration {
        supported(stats.type)
        // Bound interactive pools and attempts; never enumerate compound tickets.
        require(redCount in stats.type.redCount..16 && blueCount in stats.type.blueCount..6) { "红球最多16码，蓝球最多6码" }
        require(count in 1..20) { "每次生成1~20组" }
        val random = Random(seed)
        fun weighted(pool: List<Int>, n: Int, freq: Map<Int, Int>, cold: Boolean): List<Int> {
            val available = pool.toMutableList()
            val picked = mutableListOf<Int>()
            val max = freq.values.maxOrNull() ?: 0
            repeat(n) {
                checkCancelled()
                val weights = available.map { if (cold) max - freq.getValue(it) + 1 else freq.getValue(it) + 1 }
                var ticket = random.nextInt(weights.sum())
                val index = weights.indices.first { ticket -= weights[it]; ticket < 0 }
                picked += available.removeAt(index)
            }
            return picked
        }
        fun pick(freq: Map<Int, Int>, n: Int, red: Boolean): List<Int> {
            val pool = freq.keys.sorted()
            return when (strategy) {
                WindowStrategy.HOT -> weighted(pool, n, freq, false)
                WindowStrategy.COLD -> weighted(pool, n, freq, true)
                WindowStrategy.BALANCED -> {
                    val ranked = pool.sortedWith(compareByDescending<Int> { freq.getValue(it) }.thenBy { it })
                    val upper = ranked.take(ranked.size / 2)
                    val lower = ranked.drop(upper.size)
                    weighted(upper, (n + 1) / 2, freq, false) + weighted(lower, n / 2, freq, true)
                }
                WindowStrategy.SEGMENT -> {
                    val zones = pool.groupBy { (it - 1) * 3 / pool.size }.values.toList()
                    val extraZones = (0..2).shuffled(random).take(n % 3)
                    zones.flatMapIndexed { i, zone -> zone.shuffled(random).take(n / 3 + if (i in extraZones) 1 else 0) }
                }
                WindowStrategy.CONSECUTIVE -> if (red) {
                    val first = random.nextInt(1, pool.size)
                    listOf(first, first + 1) + pool.filter { it != first && it != first + 1 }.shuffled(random).take(n - 2)
                } else pool.shuffled(random).take(n)
            }.sorted()
        }
        val bets = linkedMapOf<Pair<List<Int>, List<Int>>, GeneratedLotteryBet>()
        repeat(count * 100) {
            checkCancelled()
            if (bets.size == count) return WindowGeneration(stats, strategy, redCount, blueCount, seed, bets.values.toList())
            val reds = pick(stats.redFrequency, redCount, true)
            val blues = pick(stats.blueFrequency, blueCount, false)
            val draft = PredictionDraft(stats.type, reds, blues, "频次策略", PredictionRules.classify(stats.type, reds, blues), null, BlueSource.GENERATED_CONFIRMED)
            PredictionRules.validate(draft)
            bets[reds to blues] = GeneratedLotteryBet(reds, blues)
        }
        require(bets.size == count) { "无法生成足够的不重复方案，请减少组数或调整参数" }
        return WindowGeneration(stats, strategy, redCount, blueCount, seed, bets.values.toList())
    }
}
