package com.lottery.analyzer.data.ai

import com.lottery.analyzer.data.engine.LotteryAnalysisEngine
import com.lottery.analyzer.data.engine.WindowStrategy
import com.lottery.analyzer.data.model.*
import org.junit.Assert.*
import org.junit.Test

class AiAnalysisFactsTest {
    private val records = listOf(
        LotteryRecord("2026001", "2026-01-01", (1..6).toList(), listOf(1)),
        LotteryRecord("2026002", "2026-01-03", (7..12).toList(), listOf(2)),
        LotteryRecord("2026003", "2026-01-05", (20..25).toList(), listOf(3))
    )

    @Test fun historicalCutoffExcludesAllFutureDrawsAndMatchesOmissionSnapshot() {
        val facts = AiAnalysisFactsBuilder.prepare(LotteryType.SSQ, records.reversed(), "2026003", AiWindowOptions(periods = 50), "2026002")
        val stats = facts.windowContext!!.stats
        assertEquals("2026002", stats.lastIssue)
        assertEquals(2, stats.sampleCount)
        assertEquals(0, stats.redFrequency.getValue(20))
        assertEquals(0, stats.blueFrequency.getValue(3))
        val expected = LotteryAnalysisEngine.analyze(records[1], LotteryAnalysisEngine.calculateAllMissingStats(records.take(2), LotteryType.SSQ)["2026002"], LotteryType.SSQ)
        assertEquals(expected, facts.analysis)
    }

    @Test fun targetCannotBeAtOrBeforeStatisticalCutoff() {
        for (target in listOf("2026001", "2026003")) {
            try { AiAnalysisFactsBuilder.prepare(LotteryType.SSQ, records, target, AiWindowOptions()); fail("Expected future-data guard") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun latestAnalysisAndWindowAlwaysShareSameCutoff() {
        val facts = AiAnalysisFactsBuilder.prepare(LotteryType.SSQ, records, "2026004", AiWindowOptions(periods = 1))
        val stats = facts.windowContext!!.stats
        assertEquals(facts.analysis.issue, stats.lastIssue)
        assertEquals(1, stats.sampleCount)
        assertEquals(6, stats.redFrequency.values.sum())
    }

    @Test fun disabledWindowAndKl8NeverInvokeUnsupportedWindowEngine() {
        assertNull(AiAnalysisFactsBuilder.prepare(LotteryType.SSQ, records, "2026004", AiWindowOptions(enabled = false)).windowContext)
        val draw = LotteryRecord("2026001", "2026-01-01", (1..20).toList())
        assertNull(AiAnalysisFactsBuilder.prepare(LotteryType.KL8, listOf(draw), "2026002", AiWindowOptions()).windowContext)
    }

    @Test fun compactPromptIncludesZeroCountsSourcesAndExactHardConstraint() {
        val facts = AiAnalysisFactsBuilder.prepare(LotteryType.SSQ, records, "2026004", AiWindowOptions(strategy = WindowStrategy.SEGMENT))
        val prompt = AiWindowPrompt.build(facts.windowContext!!)
        assertTrue(prompt.contains("非他人购买频次，非当前遗漏"))
        assertTrue(prompt.contains("33:0"))
        assertTrue(prompt.contains("1~11、12~22、23~33"))
        assertTrue(prompt.contains("有效=3期"))
        assertTrue(prompt.length < 2000)
    }
}
