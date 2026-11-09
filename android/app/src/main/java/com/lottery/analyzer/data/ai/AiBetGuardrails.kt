package com.lottery.analyzer.data.ai

import com.lottery.analyzer.data.engine.WindowStrategy
import com.lottery.analyzer.data.model.*

data class AiBetValidation(
    val bets: List<Pair<List<Int>, List<Int>>>,
    val parsedCount: Int,
    val rejectionReasons: Map<String, Int>
)

/** Only recommendation lines are parsed. Never repair an invalid group or mine prose for numbers. */
object AiBetGuardrails {
    private val tagged = Regex("""^【推荐第\s*\d+\s*注】\s*红球\s*[:：]\s*(.*)$""")
    private val numberList = Regex("""[0-9]+(?:[\s,，]+[0-9]+)*""")
    private val plainBet = Regex("""[0-9\s,，]+(?:\s*\+\s*[0-9\s,，]+)?""")

    fun parse(text: String, request: AiRecommendationRequest, checkCancelled: () -> Unit = {}): AiBetValidation {
        require(request.betCount in 1..20) { "推荐注数须为1~20" }
        require(request.windowContext == null || (request.lotteryType != LotteryType.KL8 && request.windowContext.stats.type == request.lotteryType)) { "频次策略与彩种不匹配" }
        val type = request.lotteryType
        val expectedRed = if (type == LotteryType.KL8) request.kl8PlayType.pickCount else type.redCount
        val accepted = linkedSetOf<Pair<List<Int>, List<Int>>>()
        val rejected = linkedMapOf<String, Int>()
        val hasTaggedLines = text.lineSequence().any { it.trim().startsWith("【推荐") }
        var parsed = 0
        fun reject(reason: String) { rejected[reason] = (rejected[reason] ?: 0) + 1 }
        fun parseNumbers(part: String): List<Int>? {
            val value = part.trim()
            if (!numberList.matches(value)) return null
            return value.split(Regex("""[\s,，]+""")).map { it.toIntOrNull() ?: return null }
        }
        for (rawLine in text.lineSequence()) {
            checkCancelled()
            val line = rawLine.trim()
            val match = tagged.matchEntire(line)
            val body = when {
                match != null -> match.groupValues[1]
                line.startsWith("【推荐") -> { parsed++; reject("推荐格式错误"); continue }
                !hasTaggedLines && plainBet.matches(line) && (type == LotteryType.KL8 || line.contains('+')) -> line
                else -> continue
            }
            parsed++
            val parts = body.split('+')
            if (parts.size != if (type.blueCount == 0) 1 else 2) { reject("红蓝球分隔格式错误"); continue }
            val reds = parseNumbers(parts[0])
            val blues = if (type.blueCount == 0) emptyList() else parseNumbers(parts[1].trim().replaceFirst(Regex("""^蓝球\s*[:：]\s*"""), ""))
            if (reds == null || blues == null) { reject("包含非整数或多余文本"); continue }
            if (reds.any { it !in 1..type.maxRedNumber } || blues.any { it !in 1..type.maxBlueNumber }) { reject("号码越界"); continue }
            if (reds.distinct().size != reds.size || blues.distinct().size != blues.size) { reject("注内号码重复"); continue }
            if (reds.size != expectedRed || blues.size != type.blueCount) { reject("单注号码数量不符"); continue }
            val sorted = reds.sorted() to blues.sorted()
            when (request.windowContext?.strategy) {
                WindowStrategy.SEGMENT -> {
                    val counts = (0..2).map { zone -> reds.count { (it - 1) * 3 / type.maxRedNumber == zone } }
                    if (counts.maxOrNull()!! - counts.minOrNull()!! > 1) { reject("不满足分区均衡"); continue }
                }
                WindowStrategy.CONSECUTIVE -> if (sorted.first.zipWithNext().none { (a, b) -> b == a + 1 }) { reject("缺少连号"); continue }
                else -> Unit
            }
            if (request.mode == AiAnalysisMode.HEART_FUSION) {
                val maxOverlap = if (type == LotteryType.KL8) minOf(4, maxOf(1, expectedRed - 1)) else 4
                val otherPools = request.otherBets.filter { it.isNotEmpty() }.ifEmpty {
                    if (request.topOtherBalls.isEmpty()) emptyList() else listOf(request.topOtherBalls.map { it.first })
                }
                if (otherPools.any { other -> reds.count { it in other } !in 1..maxOverlap }) {
                    reject("不满足他人号码重叠约束"); continue
                }
            }
            if (sorted in accepted) { reject("推荐组重复"); continue }
            if (accepted.size >= request.betCount) { reject("超过请求注数"); continue }
            accepted += sorted
        }
        return AiBetValidation(accepted.toList(), parsed, rejected.toMap())
    }
}
