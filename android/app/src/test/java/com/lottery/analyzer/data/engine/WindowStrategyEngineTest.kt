package com.lottery.analyzer.data.engine

import com.lottery.analyzer.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class WindowStrategyEngineTest {
    private fun record(issue: String, reds: List<Int> = listOf(1, 2, 3, 29, 32, 33), blues: List<Int> = listOf(1)) =
        LotteryRecord(issue, "2026-01-01", reds, blues)

    @Test fun windowUsesLatestNumericIssuesDeduplicatesAndIncludesZeroFrequency() {
        val old = record("99999")
        val latest = record("100001", listOf(4, 5, 6, 7, 8, 9), listOf(2))
        val invalid = record("100002", listOf(0, 1, 2, 3, 4, 5))
        val stats = WindowStrategyEngine.analyze(LotteryType.SSQ, listOf(old, invalid, latest, latest), 1)
        assertEquals(1, stats.sampleCount)
        assertEquals("100001", stats.lastIssue)
        assertEquals(2, stats.excludedCount)
        assertEquals(33, stats.redFrequency.size)
        assertEquals(0, stats.redFrequency.getValue(33))
        assertEquals(6, stats.redFrequency.values.sum())
        assertEquals(1, stats.blueFrequency.values.sum())
        assertEquals(1, stats.ranked(cold = true).first())
    }

    @Test fun shortWindowAndBoundaryHistogramAreAccurate() {
        val stats = WindowStrategyEngine.analyze(LotteryType.SSQ, listOf(record("2026001")), 50)
        assertEquals(50, stats.requestedPeriods)
        assertEquals(1, stats.sampleCount)
        assertEquals(mapOf(81 to 1), stats.sumHistogram)
        assertEquals(100.0, stats.sumMean, 0.0)
        assertEquals(100.0, stats.sumMedian, 0.0)
        assertEquals(0.0, stats.sumStdDev, 0.0)
        assertEquals(mapOf(4 to 1), stats.oddHistogram)
        assertEquals(mapOf(3 to 1), stats.consecutiveHistogram)
    }

    @Test fun knownSumSampleStatistics() {
        val stats = WindowStrategyEngine.analyze(LotteryType.SSQ,
            listOf(record("2026001", (1..6).toList()), record("2026002", (2..7).toList())), 2)
        assertEquals(24.0, stats.sumMean, 0.0)
        assertEquals(24.0, stats.sumMedian, 0.0)
        assertEquals(kotlin.math.sqrt(18.0), stats.sumStdDev, 0.000001)
    }

    @Test fun everyStrategyHonorsLotteryAndRequestedFormatAndProducesUniqueGroups() {
        for (type in listOf(LotteryType.SSQ, LotteryType.DLT)) {
            val stats = WindowStrategyEngine.analyze(type,
                listOf(record("2026001", (1..type.redCount).toList(), (1..type.blueCount).toList())), 30)
            for (strategy in WindowStrategy.values()) {
                for ((reds, blues) in listOf(type.redCount to type.blueCount, 7 to 2, 6 to 2, 16 to 6)) {
                    val output = WindowStrategyEngine.generate(stats, strategy, reds, blues, 10, 123L)
                    assertEquals(10, output.bets.size)
                    assertEquals(10, output.bets.distinct().size)
                    assertSame(stats, output.stats)
                    output.bets.forEach { bet ->
                        assertEquals(reds, bet.redBalls.size)
                        assertEquals(blues, bet.blueBalls.size)
                        PredictionRules.validate(PredictionDraft(type, bet.redBalls, bet.blueBalls, "test",
                            PredictionRules.classify(type, bet.redBalls, bet.blueBalls), null, BlueSource.GENERATED_CONFIRMED))
                        if (strategy == WindowStrategy.CONSECUTIVE) assertTrue(bet.redBalls.zipWithNext().any { (a, b) -> b == a + 1 })
                        if (strategy == WindowStrategy.SEGMENT) {
                            val counts = (0..2).map { zone -> bet.redBalls.count { (it - 1) * 3 / type.maxRedNumber == zone } }
                            assertTrue(counts.maxOrNull()!! - counts.minOrNull()!! <= 1)
                        }
                    }
                    assertEquals(output, WindowStrategyEngine.generate(stats, strategy, reds, blues, 10, 123L))
                }
            }
        }
    }

    @Test fun generationKeepsWindowAndProvenance() {
        val stats = WindowStrategyEngine.analyze(LotteryType.SSQ,
            (1..12).map { record("20260${it.toString().padStart(2, '0')}") }, 3)
        val output = WindowStrategyEngine.generate(stats, WindowStrategy.HOT, 6, 1, 1, -99)
        assertEquals(3, output.stats.sampleCount)
        assertEquals(18, output.stats.redFrequency.values.sum())
        assertTrue(output.description(0).contains("2026010-2026012"))
        assertTrue(output.description(0).contains("seed=-99"))
        assertEquals(1L, output.combinationsPerGroup)
    }

    @Test fun compoundCountUsesBothZonesAndDoesNotEnumerate() {
        assertEquals(14L, WindowStrategyEngine.combinationCount(LotteryType.SSQ, 7, 2))
        assertEquals(2L, WindowStrategyEngine.combinationCount(LotteryType.SSQ, 6, 2))
        assertEquals(21L, WindowStrategyEngine.combinationCount(LotteryType.DLT, 7, 2))
        assertEquals(6L, WindowStrategyEngine.combinationCount(LotteryType.DLT, 6, 2))
        assertEquals(36L, WindowStrategyEngine.combinationCount(LotteryType.DLT, 6, 4))
    }

    @Test fun conflictingIssueFailsRatherThanSilentlyChoosingOne() {
        expectIllegal { WindowStrategyEngine.analyze(LotteryType.SSQ,
            listOf(record("2026001"), record("2026001", (1..6).toList())), 30) }
    }

    @Test fun emptyInvalidUnsupportedAndOutOfBoundsInputsFailClearly() {
        expectIllegal { WindowStrategyEngine.analyze(LotteryType.SSQ, emptyList(), 30) }
        expectIllegal { WindowStrategyEngine.analyze(LotteryType.SSQ, listOf(record("2026001")), 0) }
        expectIllegal { WindowStrategyEngine.analyze(LotteryType.KL8, emptyList(), 30) }
        expectIllegal { WindowStrategyEngine.analyze(LotteryType.SSQ,
            listOf(record("2026001", listOf(1, 1, 2, 3, 4, 5)), record("bad")), 30) }
        val stats = WindowStrategyEngine.analyze(LotteryType.SSQ, listOf(record("2026001")), 30)
        expectIllegal { WindowStrategyEngine.generate(stats, WindowStrategy.HOT, 5, 1, 1, 1) }
        expectIllegal { WindowStrategyEngine.generate(stats, WindowStrategy.HOT, 6, 1, 21, 1) }
        expectIllegal { WindowStrategyEngine.combinationCount(LotteryType.SSQ, 6, 0) }
    }

    @Test fun statisticsAndGenerationCooperateWithCancellation() {
        try {
            WindowStrategyEngine.analyze(LotteryType.SSQ, listOf(record("2026001")), 30) { throw CancellationException() }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        val stats = WindowStrategyEngine.analyze(LotteryType.SSQ, listOf(record("2026001")), 30)
        try {
            WindowStrategyEngine.generate(stats, WindowStrategy.HOT, 6, 1, 5, 1) { throw CancellationException() }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
    }

    private fun expectIllegal(block: () -> Unit) {
        try { block(); fail("Expected invalid input") } catch (_: IllegalArgumentException) { }
    }
}
