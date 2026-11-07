package com.lottery.analyzer.data.engine

import com.lottery.analyzer.data.model.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class EngineRegressionTest {
    @Test fun impossibleFiltersDoNotFallBackToUnfilteredBets(){
        val result=LotteryShrinkEngine.executeShrink((1..7).toList(),LotteryType.SSQ,
            ShrinkFilterConfig(enableSumFilter=true,sumMin=100,sumMax=120,enableExcludeHistory=false,wheelMode=WheelGuaranteeMode.WHEEL_MATCH_MINUS_1))
        assertTrue(result.filteredBets.isEmpty());assertNotNull(result.warningMessage)
    }
    @Test fun incompleteWheelCannotClaimStrictGuarantee(){
        val result=LotteryShrinkEngine.executeShrink((1..8).toList(),LotteryType.SSQ,
            ShrinkFilterConfig(enableSumFilter=true,sumMin=21,sumMax=21,enableExcludeHistory=false,wheelMode=WheelGuaranteeMode.WHEEL_MATCH_MINUS_1))
        assertFalse(result.isStrictGuaranteed);assertNotNull(result.warningMessage)
    }
    @Test fun oversizedPoolIsRejectedBeforeCombinatorialExpansion(){
        val result=LotteryShrinkEngine.executeShrink((1..33).toList(),LotteryType.SSQ,ShrinkFilterConfig())
        assertTrue(result.filteredBets.isEmpty());assertNotNull(result.warningMessage)
    }
    @Test fun kl8TenNumberPoolStillExpandsTo1001LegalBets(){
        val result=LotteryShrinkEngine.executeShrink((1..14).toList(),LotteryType.KL8,ShrinkFilterConfig(enableExcludeHistory=false))
        assertEquals(1001,result.filteredCount);assertTrue(result.filteredBets.all{it.size==10})
    }
    @Test fun cancellationProbeAbortsRatherThanReturningPartialGuaranteedResult(){
        var probes=0
        try {
            LotteryShrinkEngine.executeShrink((1..16).toList(),LotteryType.SSQ,ShrinkFilterConfig(),checkCancellation={if(++probes==2)throw kotlinx.coroutines.CancellationException("stop")})
            fail("must propagate cancellation")
        }catch(_:kotlinx.coroutines.CancellationException){}
    }
    @Test fun trendWorksInCommaDecimalLocaleAndIncludesCurrentMissing(){
        val records=listOf(LotteryRecord("1","2026-01-01",listOf(1,3,4,5,6,7),listOf(1)),LotteryRecord("2","2026-01-02",listOf(2,3,4,5,6,7),listOf(2)))
        val locale=Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val matrix=TrendMatrixEngine.buildTrendMatrix(records,LotteryAnalysisEngine.calculateAllMissingStats(records,LotteryType.SSQ),LotteryType.SSQ)
            val stat=matrix.redSummaries.first{it.number==1}
            assertEquals(1,stat.currentMissing);assertEquals(1,stat.maxMissing)
        }finally{Locale.setDefault(locale)}
    }
}
