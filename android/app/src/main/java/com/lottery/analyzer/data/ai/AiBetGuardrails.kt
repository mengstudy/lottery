package com.lottery.analyzer.data.ai

import com.lottery.analyzer.data.engine.CandidateRules
import com.lottery.analyzer.data.model.*

data class AiBetValidation(
    val bets: List<Pair<List<Int>, List<Int>>>,
    val parsedCount: Int,
    val rejectionReasons: Map<String, Int>,
    val entries: List<ArchivedCandidate> = emptyList()
)

/** Only recommendation lines are parsed. Never repair an invalid group or mine prose for numbers. */
object AiBetGuardrails {
    private val tagged = Regex("""^【推荐第\s*\d+\s*注】\s*红球\s*[:：]\s*(.*)$""")
    private val numberList = Regex("""[0-9]+(?:[\s,，]+[0-9]+)*""")

    fun parse(text: String, request: AiRecommendationRequest, checkCancelled: () -> Unit = {}): AiBetValidation {
        if (request.selectionContext != null) return AiCandidateSelectionParser.parse(text, request, checkCancelled).validation
        require(request.betCount in 1..20) { "推荐注数须为1~20" }
        require(request.windowContext == null || (request.lotteryType != LotteryType.KL8 && request.windowContext.stats.type == request.lotteryType)) { "频次策略与彩种不匹配" }
        val type = request.lotteryType
        val rules = CandidateRules.from(request)
        val accepted = linkedSetOf<Pair<List<Int>, List<Int>>>()
        val rejected = linkedMapOf<String, Int>()
        var parsed = 0
        val entries = mutableListOf<ArchivedCandidate>()
        var auditLine = ""
        var auditReds: List<Int>? = null
        var auditBlues: List<Int>? = null
        fun reject(reason: String) {
            rejected[reason] = (rejected[reason] ?: 0) + 1
            entries += ArchivedCandidate(parsed, auditLine, auditReds, auditBlues, reason)
        }
        fun parseNumbers(part: String): List<Int>? {
            val value = part.trim()
            if (!numberList.matches(value)) return null
            return value.split(Regex("""[\s,，]+""")).map { it.toIntOrNull() ?: return null }
        }
        for (rawLine in text.lineSequence()) {
            checkCancelled()
            val line = rawLine.trim()
            auditLine = line.take(2048)
            auditReds = null
            auditBlues = null
            val match = tagged.matchEntire(line)
            val body = when {
                match != null -> match.groupValues[1]
                line.startsWith("【推荐") -> { parsed++; reject("推荐格式错误"); continue }
                else -> continue
            }
            parsed++
            val parts = body.split('+')
            if (parts.size != if (type.blueCount == 0) 1 else 2) { reject("红蓝球分隔格式错误"); continue }
            val reds = parseNumbers(parts[0])
            val blues = if (type.blueCount == 0) emptyList() else parseNumbers(parts[1].trim().replaceFirst(Regex("""^蓝球\s*[:：]\s*"""), ""))
            auditReds = reds
            auditBlues = blues
            if (reds == null || blues == null) { reject("包含非整数或多余文本"); continue }
            val problem = rules.rejection(reds, blues)
            if (problem != null) { reject(problem); continue }
            val sorted = reds.sorted() to blues.sorted()
            if (sorted in accepted) { reject("推荐组重复"); continue }
            if (accepted.size >= request.betCount) { reject("超过请求注数"); continue }
            entries += ArchivedCandidate(parsed, auditLine, sorted.first, sorted.second, acceptedIndex = accepted.size)
            accepted += sorted
        }
        return AiBetValidation(accepted.toList(), parsed, rejected.toMap(), entries.toList())
    }
}
