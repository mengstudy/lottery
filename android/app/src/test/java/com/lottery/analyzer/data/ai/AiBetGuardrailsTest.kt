package com.lottery.analyzer.data.ai

import com.lottery.analyzer.data.engine.WindowStrategy
import com.lottery.analyzer.data.engine.WindowStrategyEngine
import com.lottery.analyzer.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class AiBetGuardrailsTest {
    private fun request(type: LotteryType = LotteryType.SSQ, strategy: WindowStrategy? = null, count: Int = 5,
                        mode: AiAnalysisMode = AiAnalysisMode.FULL_RECOMMEND, others: List<List<Int>> = emptyList(),
                        play: Kl8PlayType = Kl8PlayType.XUAN_TEN): AiRecommendationRequest {
        val draw = LotteryRecord("2026001", "2026-01-01", (1..type.redCount).toList(), (1..type.blueCount).toList())
        val facts = AiAnalysisFactsBuilder.prepare(type, listOf(draw), "2026002", AiWindowOptions(enabled = strategy != null, strategy = strategy ?: WindowStrategy.BALANCED))
        return AiRecommendationRequest(AiConfig(), type, facts.analysis, "2026002", mode, emptySet(),
            otherBets = others, betCount = count, kl8PlayType = play, windowContext = facts.windowContext)
    }

    @Test fun invalidTokensAreRejectedRatherThanRemovedOrDeduplicated() {
        val text = """
            【推荐第1注】红球: 01 02 03 04 05 06 99 + 蓝球: 01
            【推荐第2注】红球: 01 02 03 04 05 06 06 + 蓝球: 01
            【推荐第3注】红球: 01 02 03 04 05 06 + 蓝球: 01 01
            【推荐第4注】红球: 01 02 03 04 05 06 -1 + 蓝球: 01
            【推荐第5注】红球: 01 02 03 04 05 06.5 + 蓝球: 01
            【推荐第6注】红球: 01 02 03 04 05 06 + 蓝球: 01
        """.trimIndent()
        val result = AiBetGuardrails.parse(text, request())
        assertEquals(6, result.parsedCount)
        assertEquals(1, result.bets.size)
        assertEquals(1, result.rejectionReasons["号码越界"])
        assertEquals(2, result.rejectionReasons["注内号码重复"])
        assertEquals(2, result.rejectionReasons["包含非整数或多余文本"])
    }

    @Test fun deduplicatesGroupsAndCapsRequestedCountWithVisibleReasons() {
        val result = AiBetGuardrails.parse("""
            【推荐第1注】红球: 01 02 03 04 05 06 + 蓝球: 01
            【推荐第2注】红球: 06 05 04 03 02 01 + 蓝球: 01
            【推荐第3注】红球: 02 03 04 05 06 07 + 蓝球: 02
        """.trimIndent(), request(count = 1))
        assertEquals(3, result.parsedCount)
        assertEquals(1, result.bets.size)
        assertEquals(mapOf("推荐组重复" to 1, "超过请求注数" to 1), result.rejectionReasons)
    }

    @Test fun plainFallbackDoesNotRescueMalformedTaggedOutputOrMineProse() {
        val malformed = "【推荐第1注】红球: 01 02 03 04 05 06 99 + 蓝球: 01\n01 02 03 04 05 06 + 01"
        assertTrue(AiBetGuardrails.parse(malformed, request()).bets.isEmpty())
        assertTrue(AiBetGuardrails.parse("和值建议 01 02 03 04 05 06 + 01", request()).bets.isEmpty())
        assertEquals(1, AiBetGuardrails.parse("01,02,03,04,05,06 + 01", request()).bets.size)
        assertTrue(AiBetGuardrails.parse("01 02 03 04 05 06 + 01 + 02", request()).bets.isEmpty())
    }

    @Test fun segmentAndConsecutiveConstraintsAreHardButHotColdArePreferences() {
        val unbalanced = "【推荐第1注】红球: 01 03 05 07 09 11 + 蓝球: 01"
        assertEquals(1, AiBetGuardrails.parse(unbalanced, request(strategy = WindowStrategy.HOT)).bets.size)
        assertEquals(1, AiBetGuardrails.parse(unbalanced, request(strategy = WindowStrategy.COLD)).bets.size)
        assertTrue(AiBetGuardrails.parse(unbalanced, request(strategy = WindowStrategy.SEGMENT)).bets.isEmpty())
        assertTrue(AiBetGuardrails.parse(unbalanced, request(strategy = WindowStrategy.CONSECUTIVE)).bets.isEmpty())
        val balanced = "【推荐第1注】红球: 01 02 12 13 23 24 + 蓝球: 01"
        assertEquals(1, AiBetGuardrails.parse(balanced, request(strategy = WindowStrategy.SEGMENT)).bets.size)
        assertEquals(1, AiBetGuardrails.parse(balanced, request(strategy = WindowStrategy.CONSECUTIVE)).bets.size)
        val dlt = "【推荐第1注】红球: 01 02 13 14 25 + 蓝球: 01 02"
        assertEquals(1, AiBetGuardrails.parse(dlt, request(LotteryType.DLT, WindowStrategy.SEGMENT)).bets.size)
    }

    @Test fun heartFusionOverlapStillAppliesAlongsideWindowStrategy() {
        val input = request(strategy = WindowStrategy.CONSECUTIVE, mode = AiAnalysisMode.HEART_FUSION, others = listOf((1..6).toList()))
        val result = AiBetGuardrails.parse("""
            【推荐第1注】红球: 01 02 03 04 05 06 + 蓝球: 01
            【推荐第2注】红球: 01 02 12 13 23 24 + 蓝球: 01
        """.trimIndent(), input)
        assertEquals(1, result.bets.size)
        assertEquals(1, result.rejectionReasons["不满足他人号码重叠约束"])
    }

    @Test fun kl8RequiresSelectedPlayAndRejectsDuplicateAndBlueBalls() {
        val input = request(LotteryType.KL8, play = Kl8PlayType.XUAN_TWO)
        val result = AiBetGuardrails.parse("""
            【推荐第1注】红球: 01 80
            【推荐第2注】红球: 01 01 80
            【推荐第3注】红球: 01 80 + 蓝球: 01
            【推荐第4注】红球: 01 02 03
        """.trimIndent(), input)
        assertEquals(listOf(listOf(1, 80) to emptyList<Int>()), result.bets)
        assertEquals(3, result.rejectionReasons.values.sum())
    }

    @Test fun allInvalidOutputRetainsDiagnosticsAndCancellationPropagates() {
        val input = request()
        val result = AiBetGuardrails.parse("【推荐第1注】红球: 99 + 蓝球: 01", input)
        assertTrue(result.bets.isEmpty()); assertEquals(1, result.parsedCount)
        try { AiBetGuardrails.parse("anything", input) { throw CancellationException() }; fail("Expected cancellation") }
        catch (_: CancellationException) { }
    }
}
