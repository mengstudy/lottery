package com.lottery.analyzer.data.model

import com.lottery.analyzer.data.engine.LotteryAnalysisEngine
import org.junit.Assert.*
import org.junit.Test

class PredictionRulesTest {
    private fun draft(kind:PredictionKind,reds:List<Int>,blues:List<Int>,type:LotteryType=LotteryType.SSQ,play:Kl8PlayType?=null)=
        PredictionDraft(type,reds,blues,"test",kind,play,BlueSource.PROVIDED)
    @Test fun duplicateOutOfRangeAndWrongSingleCountAreRejected(){
        for(d in listOf(draft(PredictionKind.SINGLE,listOf(1,1,2,3,4,5),listOf(1)),draft(PredictionKind.SINGLE,(1..6).toList(),listOf(17)),draft(PredictionKind.SINGLE,(1..7).toList(),listOf(1))))
            assertTrue(runCatching{PredictionRules.validate(d)}.isFailure)
    }
    @Test fun kl8SingleRequiresExplicitMatchingPlay(){
        assertTrue(runCatching{PredictionRules.validate(draft(PredictionKind.SINGLE,listOf(1,2,3),emptyList(),LotteryType.KL8,Kl8PlayType.XUAN_TEN))}.isFailure)
        PredictionRules.validate(draft(PredictionKind.SINGLE,listOf(1,2,3),emptyList(),LotteryType.KL8,Kl8PlayType.XUAN_THREE))
    }
    @Test fun referenceAndLegacyRecordsNeverPretendToBeWinningSingles(){
        val draw=LotteryRecord("2026001","2026-11-01",(1..6).toList(),listOf(1))
        for(kind in listOf(PredictionKind.REFERENCE,PredictionKind.COMPOUND,PredictionKind.LEGACY_UNCONFIRMED)) {
            val pred=PredictionRecord(lotteryType=LotteryType.SSQ,targetIssue="2026001",redBalls=(1..7).toList(),blueBalls=listOf(1),strategy="test",createdAt="",kind=kind)
            val result=LotteryAnalysisEngine.verifyPrediction(pred,draw)
            assertFalse(result.isWon);assertFalse(result.resultSummary.contains("未中奖"));assertEquals(6,result.hitRedBalls.size)
        }
    }
    @Test fun validSingleStillReceivesPrizeVerification(){
        val pred=PredictionRecord(lotteryType=LotteryType.SSQ,targetIssue="2026001",redBalls=(1..6).toList(),blueBalls=listOf(1),strategy="test",createdAt="",kind=PredictionKind.SINGLE)
        val result=LotteryAnalysisEngine.verifyPrediction(pred,LotteryRecord("2026001","2026-11-01",(1..6).toList(),listOf(1)))
        assertTrue(result.isWon);assertTrue(result.resultSummary.contains("一等奖"))
    }
}
