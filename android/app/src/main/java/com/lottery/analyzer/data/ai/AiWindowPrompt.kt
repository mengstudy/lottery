package com.lottery.analyzer.data.ai

import com.lottery.analyzer.data.engine.WindowStrategy
import com.lottery.analyzer.data.model.AiWindowContext
import java.util.Locale

object AiWindowPrompt {
    fun build(context: AiWindowContext): String = buildString {
        val stats = context.stats
        appendLine("【客观开奖频次：本地统计，非他人购买频次，非当前遗漏】")
        appendLine("彩种=${stats.type.title}；请求=${stats.requestedPeriods}期；有效=${stats.sampleCount}期；起止=${stats.firstIssue}~${stats.lastIssue}；排除无效/重复=${stats.excludedCount}条。")
        appendLine("红/前区号码:出现期数=" + stats.redFrequency.entries.joinToString(",") { "${it.key}:${it.value}" })
        appendLine("蓝/后区号码:出现期数=" + stats.blueFrequency.entries.joinToString(",") { "${it.key}:${it.value}" })
        appendLine("红/前区高频前8=" + stats.ranked().take(8).joinToString(","))
        appendLine("红/前区低频前8=" + stats.ranked(cold = true).take(8).joinToString(","))
        appendLine("同频按号码升序排列，不代表同频号码存在优劣。")
        appendLine(String.format(Locale.ROOT, "红/前区和值：均值=%.2f，中位数=%.2f，样本标准差=%.2f", stats.sumMean, stats.sumMedian, stats.sumStdDev))
        appendLine("和值区间:期数=" + stats.sumHistogram.entries.joinToString(",") { "${it.key}~${it.key + 19}:${it.value}" })
        appendLine("红/前区奇数个数:期数=" + stats.oddHistogram.entries.joinToString(",") { "${it.key}:${it.value}" })
        appendLine("红/前区连号相邻对数:期数=" + stats.consecutiveHistogram.entries.joinToString(",") { "${it.key}:${it.value}" })
        appendLine("以上是样本描述，不能当作下一期概率或声称提高胜率。不要自行重算或虚构统计。")
        appendLine("【用户历史频次策略】${context.strategy.title}")
        appendLine(when (context.strategy) {
            WindowStrategy.BALANCED -> "软偏好：参考高低频搭配，并兼顾遗漏和用户意图，不强制固定比例。"
            WindowStrategy.HOT -> "软偏好：适当偏重窗口出现次数较高的号码，不强制从前几名中选。"
            WindowStrategy.COLD -> "软偏好：适当偏重低频或零频号码，不把低频解释为即将补出。"
            WindowStrategy.CONSECUTIVE -> "硬约束：每注红/前区至少包含一对相邻号码；蓝/后区不要求连号。"
            WindowStrategy.SEGMENT -> {
                val zones = (1..stats.type.maxRedNumber).groupBy { (it - 1) * 3 / stats.type.maxRedNumber }.values
                "硬约束：红/前区分三区=" + zones.joinToString("、") { "${it.first()}~${it.last()}" } +
                    "，每注三区选号数量最大值与最小值相差不超过1；蓝/后区无此配额。"
            }
        })
        appendLine("仅生成合法单注，不得输出复式池；范围、数量、去重和上述硬约束将由本地核验。")
    }
}
