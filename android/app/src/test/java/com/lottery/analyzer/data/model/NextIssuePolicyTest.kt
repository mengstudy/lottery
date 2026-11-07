package com.lottery.analyzer.data.model

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class NextIssuePolicyTest {
    @Test fun incrementPreservesWidth(){assertEquals("0000124",NextIssuePolicy.increment("0000123"))}
    @Test fun malformedAndBlankIssuesHaveNoSuggestion(){assertEquals("",NextIssuePolicy.increment("x"));assertEquals("",NextIssuePolicy.increment(""))}
    @Test fun yearBoundaryAndStaleDataRequireOfficialConfirmation(){
        val draw=LotteryRecord("2025153","2025-12-30",listOf(1))
        assertEquals("",NextIssuePolicy.suggest(draw,LocalDate.of(2026,1,1)))
        assertEquals("",NextIssuePolicy.suggest(draw,LocalDate.of(2025,12,31)))
        assertEquals("",NextIssuePolicy.suggest(draw.copy(drawDate="2025-01-01"),LocalDate.of(2025,11,1)))
    }
    @Test fun recentNonBoundaryDrawOffersProvisionalIncrement(){
        assertEquals("2026124",NextIssuePolicy.suggest(LotteryRecord("2026123","2026-11-01",listOf(1)),LocalDate.of(2026,11,2)))
    }
}
