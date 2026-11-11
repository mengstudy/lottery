package com.lottery.analyzer.data.ai

import com.lottery.analyzer.data.engine.LotteryAnalysisEngine
import com.lottery.analyzer.data.engine.WindowStrategyEngine
import com.lottery.analyzer.data.model.*

data class AiAnalysisFacts(val analysis: IssueAnalysis, val windowContext: AiWindowContext?)

/** All facts come from one retained local history. Optional cutoffs support offline historical tests. */
object AiAnalysisFactsBuilder {
    fun prepare(
        type: LotteryType, records: List<LotteryRecord>, targetIssue: String, options: AiWindowOptions,
        cutoffIssue: String? = null, checkCancelled: () -> Unit = {}
    ): AiAnalysisFacts {
        fun issueValue(issue: String) = issue.takeIf { it.length in 5..12 && it.all { c -> c in '0'..'9' } }?.toBigInteger()
        val cutoff = cutoffIssue?.let { requireNotNull(issueValue(it)) { "分析截止期号无效" } }
        val filtered = records.filter {
            checkCancelled()
            val issue = issueValue(it.issue)
            issue == null || cutoff == null || issue <= cutoff
        }
        val valid = filtered.filter { record ->
            issueValue(record.issue) != null && record.redBalls.size == type.redCount && record.redBalls.distinct().size == type.redCount &&
                record.redBalls.all { it in 1..type.maxRedNumber } && record.blueBalls.size == type.blueCount &&
                record.blueBalls.distinct().size == type.blueCount && record.blueBalls.all { it in 1..type.maxBlueNumber }
        }
        val byIssue = valid.groupBy { it.issue.toBigInteger() }
        byIssue.values.forEach { group ->
            checkCancelled()
            require(group.all { it.redBalls.toSet() == group.first().redBalls.toSet() && it.blueBalls.toSet() == group.first().blueBalls.toSet() }) { "同一期号存在不同开奖号码" }
        }
        val asc = byIssue.entries.sortedBy { it.key }.map { (_, group) ->
            group.first().let { it.copy(redBalls = it.redBalls.toList(), blueBalls = it.blueBalls.toList()) }
        }
        val latest = requireNotNull(asc.lastOrNull()) { "没有有效开奖记录，请先同步数据" }
        require(cutoff == null || latest.issue.toBigInteger() == cutoff) { "找不到指定分析截止期" }
        if (targetIssue.isNotBlank()) {
            val target = requireNotNull(issueValue(targetIssue)) { "目标期号无效" }
            require(latest.issue.toBigInteger() < target) { "统计截止期必须早于目标期，不能使用目标期或之后的开奖数据" }
        }
        checkCancelled()
        // Only the latest pre-draw omission state is needed; avoid retaining a map per historical draw.
        val redMissing = IntArray(type.maxRedNumber + 1)
        val blueMissing = IntArray(type.maxBlueNumber + 1)
        for (index in 0 until asc.lastIndex) {
            checkCancelled()
            val redSet = asc[index].redBalls.toSet()
            val blueSet = asc[index].blueBalls.toSet()
            for (number in 1..type.maxRedNumber) redMissing[number] = if (number in redSet) 0 else redMissing[number] + 1
            for (number in 1..type.maxBlueNumber) blueMissing[number] = if (number in blueSet) 0 else blueMissing[number] + 1
        }
        val missing = MissingStat(latest.issue,
            (1..type.maxRedNumber).associateWith { redMissing[it] },
            (1..type.maxBlueNumber).associateWith { blueMissing[it] })
        checkCancelled()
        val analysis = LotteryAnalysisEngine.analyze(latest, missing, type)
        val window = if (options.enabled && type != LotteryType.KL8) {
            AiWindowContext(WindowStrategyEngine.analyze(type, filtered, options.periods, checkCancelled), options.strategy)
        } else null
        checkCancelled()
        return AiAnalysisFacts(analysis, window)
    }
}
