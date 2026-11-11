package com.lottery.analyzer.data.engine

import com.lottery.analyzer.data.model.*

/** The same hard rules govern generation, both AI parsers and matched random controls. */
data class CandidateRules(
    val type: LotteryType, val redCount: Int, val blueCount: Int, val hardStrategy: String? = null,
    val fusion: Boolean = false, val otherPools: List<List<Int>> = emptyList()
) {
    fun rejection(reds: List<Int>, blues: List<Int>): String? {
        if (reds.any { it !in 1..type.maxRedNumber } || blues.any { it !in 1..type.maxBlueNumber }) return "号码越界"
        if (reds.distinct().size != reds.size || blues.distinct().size != blues.size) return "注内号码重复"
        if (reds.size != redCount || blues.size != blueCount) return "单注号码数量不符"
        if (hardStrategy == "SEGMENT") {
            val zones = (0..2).map { zone -> reds.count { (it - 1) * 3 / type.maxRedNumber == zone } }
            if (zones.maxOrNull()!! - zones.minOrNull()!! > 1) return "不满足分区均衡"
        }
        if (hardStrategy == "CONSECUTIVE" && reds.sorted().zipWithNext().none { (a, b) -> b == a + 1 }) return "缺少连号"
        if (fusion) {
            val max = if (type == LotteryType.KL8) minOf(4, maxOf(1, redCount - 1)) else 4
            if (otherPools.any { other -> reds.count { it in other } !in 1..max }) return "不满足他人号码重叠约束"
        }
        return null
    }

    fun requireFeasible() {
        require(hardStrategy != "CONSECUTIVE" || redCount >= 2) { "当前玩法无法满足连号条件" }
        if (!fusion) return
        val high = if (type == LotteryType.KL8) minOf(4, maxOf(1, redCount - 1)) else 4
        for (pool in otherPools) {
            val size = pool.distinct().count { it in 1..type.maxRedNumber }
            require(minOf(size, redCount) >= 1 && maxOf(0, redCount - (type.maxRedNumber - size)) <= high) {
                "收集号码与重叠限制无法兼容，请调整样本或切换全盘模式"
            }
        }
        if (redCount == 1 && otherPools.isNotEmpty()) require(otherPools.map { it.toSet() }.reduce { a, b -> a intersect b }.isNotEmpty()) {
            "选一玩法与当前他人号码约束无共同候选"
        }
    }

    companion object {
        fun from(r: AiRecommendationRequest) = CandidateRules(r.lotteryType,
            if (r.lotteryType == LotteryType.KL8) r.kl8PlayType.pickCount else r.lotteryType.redCount, r.lotteryType.blueCount,
            r.windowContext?.strategy?.name?.takeIf { it in listOf("SEGMENT", "CONSECUTIVE") },
            r.mode == AiAnalysisMode.HEART_FUSION,
            r.otherBets.filter { it.isNotEmpty() }.ifEmpty { if (r.topOtherBalls.isEmpty()) emptyList() else listOf(r.topOtherBalls.map { it.first }) })
    }
}
