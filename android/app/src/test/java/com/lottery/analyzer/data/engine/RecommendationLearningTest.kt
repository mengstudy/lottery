package com.lottery.analyzer.data.engine

import com.lottery.analyzer.data.ai.*
import com.lottery.analyzer.data.db.*
import com.lottery.analyzer.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class RecommendationLearningTest {
    private fun request(type:LotteryType=LotteryType.SSQ,play:Kl8PlayType=Kl8PlayType.XUAN_TEN,strategy:WindowStrategy?=null):AiRecommendationRequest {
        val draw=LotteryRecord("2026001","2026-01-01",(1..type.redCount).toList(),(1..type.blueCount).toList())
        val facts=AiAnalysisFactsBuilder.prepare(type,listOf(draw),"2026002",AiWindowOptions(enabled=strategy!=null,strategy=strategy?:WindowStrategy.BALANCED))
        return AiRecommendationRequest(AiConfig(),type,facts.analysis,"2026002",AiAnalysisMode.FULL_RECOMMEND,setOf(1,2),betCount=3,kl8PlayType=play,windowContext=facts.windowContext)
    }

    @Test fun candidateReplayAndFeaturesAcrossEveryLotteryAndKl8Play() {
        val inputs=listOf(request(),request(LotteryType.DLT))+Kl8PlayType.values().map{request(LotteryType.KL8,it)}
        for(r in inputs) {
            val pool=CandidatePoolEngine.generate(r,16,42)
            assertEquals(pool,CandidatePoolEngine.generate(r,16,42))
            assertEquals(16,pool.candidates.size)
            assertEquals(pool.candidates.map{it.id}.toSet(),pool.baselineOrder.toSet())
            assertEquals(16,pool.candidates.map{it.reds to it.blues}.toSet().size)
            pool.candidates.forEach { c->
                assertNull(CandidateRules.from(r).rejection(c.reds,c.blues))
                assertEquals(c.reds.sum(),c.sum);assertEquals(c.reds.count{it%2==1},c.odd)
                assertEquals(c.reds.size,c.zones.sum())
                assertEquals(c.reds.zipWithNext().count{(a,b)->b==a+1},c.consecutive)
                assertEquals(c.reds.count{it in r.userSelection},c.heartOverlap)
            }
        }
    }

    @Test fun sharedHardRulesApplyToAllLocalCandidates() {
        for(type in listOf(LotteryType.SSQ,LotteryType.DLT))for(strategy in listOf(WindowStrategy.SEGMENT,WindowStrategy.CONSECUTIVE)) {
            val r=request(type,strategy=strategy).copy(mode=AiAnalysisMode.HEART_FUSION,otherBets=listOf((1..10).toList()))
            CandidatePoolEngine.generate(r,16,21).candidates.forEach{assertNull(CandidateRules.from(r).rejection(it.reds,it.blues))}
        }
    }

    @Test fun impossibleFusionStopsAndCancellationPropagates() {
        val r=request(LotteryType.KL8,Kl8PlayType.XUAN_ONE).copy(mode=AiAnalysisMode.HEART_FUSION,otherBets=listOf(listOf(1),listOf(2)))
        try {CandidatePoolEngine.generate(r,16,1);fail()}catch(_:IllegalArgumentException){}
        try {CandidatePoolEngine.generate(request(),16,1){throw CancellationException()};fail()}catch(_:CancellationException){}
    }

    @Test fun selectionRejectsUnknownDuplicateAndNonStringIdsAndNeverInventsNumbers() {
        val base=request();val pool=CandidatePoolEngine.generate(base,16,10);val r=base.copy(selectionContext=pool)
        val result=AiCandidateSelectionParser.parse("""{"candidate_ids":["C001","madeup","C001",12,"C002"],"explanation":"reason"}""",r)
        assertEquals(5,result.validation.parsedCount);assertEquals(2,result.validation.bets.size)
        assertEquals(3,result.validation.rejectionReasons.values.sum())
        assertEquals(pool.candidates.take(2).map{it.reds to it.blues},result.validation.bets)
        assertEquals("reason",result.explanation)
        assertTrue(AiBetGuardrails.parse("【推荐第1注】红球: 01 02 03 04 05 06 + 蓝球: 01",r).bets.isEmpty())
    }

    @Test fun selectionCapsAndHandlesCodeFencesAndTruncatedJson() {
        val r=request().let{it.copy(selectionContext=CandidatePoolEngine.generate(it,16,10))}
        val json="""{"candidate_ids":["C001","C002","C003","C004"]}"""
        assertEquals(3,AiCandidateSelectionParser.parse("```json\n$json\n```",r).validation.bets.size)
        assertTrue(AiCandidateSelectionParser.parse(json.dropLast(3),r).validation.bets.isEmpty())
        assertTrue(AiCandidateSelectionParser.parse("{\"candidate_ids\":[null,{}]}",r).validation.bets.isEmpty())
    }

    @Test fun archiveCodecRetainsCandidateMetricsAndIndependentControlOrder() {
        val r=request().let{it.copy(selectionContext=CandidatePoolEngine.generate(it,16,10))}
        val json=ArchivePayloadCodec.encodeFacts(SafeAiArchiveFacts.from(r))
        assertEquals(r.selectionContext,ArchivePayloadCodec.selectionFromFacts(json))
        val prompt=AiPromptBuilder.build(r)
        assertTrue(prompt.user.contains("candidate_ids"));assertTrue(prompt.user.contains("C001"))
    }

    @Test fun baselineIsReproducibleAndImpossibleConstraintsHaveNoSubstitutedResult() {
        val r=request();val draw=LotteryRecord("2026002","2026-01-02",(1..6).toList(),listOf(1))
        val rules=CandidateRules.from(r)
        assertEquals(ArchiveEvaluationEngine.baseline(rules,15,draw,false),ArchiveEvaluationEngine.baseline(rules,15,draw,false))
        val impossible=rules.copy(fusion=true,otherPools=listOf(emptyList()))
        val result=ArchiveEvaluationEngine.baseline(impossible,15,draw,true)
        assertEquals(0,result.samples);assertNull(result.red);assertNull(result.blue)
        val complement=draw.copy(redBalls=(7..33).toList(),blueBalls=(2..16).toList())
        val a=ArchiveEvaluationEngine.baseline(rules,15,draw,false);val b=ArchiveEvaluationEngine.baseline(rules,15,complement,false)
        assertEquals(6.0,a.red!!+b.red!!,1e-9);assertEquals(1.0,a.blue!!+b.blue!!,1e-9)
    }

    @Test fun controlledWeightsRequirePeriodsAndPreserveFloorAndStepBound() {
        val names=WindowStrategy.values().map{it.name};val equal=AdaptivePolicyEngine.uniform(names)
        assertEquals(equal,AdaptivePolicyEngine.proposed(mapOf(names[0] to List(29){2.0}),names))
        val target=AdaptivePolicyEngine.proposed(names.associateWith{n->List(100){if(n==names[0])2.0 else -1.0}},names)
        val next=AdaptivePolicyEngine.bounded(equal,target)
        assertEquals(1.0,next.values.sum(),1e-9)
        next.forEach{(name,value)->assertTrue(value>=.1);assertTrue(kotlin.math.abs(value-equal.getValue(name))<=.05000001)}
        assertTrue(next.getValue(names[0])>equal.getValue(names[0]))
    }

    @Test fun collectorRejectsAmbiguousRowsWithoutSilentlyRepairingThem() {
        for(text in listOf("0102030 + 01","01 01 02 + 01","01 99 + 01","01 -2 + 01","01 2.5 + 01","01 + 01 + 02")) {
            val result=CollectedNumbersParser.parse(text,LotteryType.SSQ)
            assertEquals(text,1,result.rejectedRows);assertTrue(result.parsedCompleteBets.isEmpty())
            assertNotNull(CollectionInputPolicy.error(text,result))
        }
        assertEquals(1,CollectedNumbersParser.parse("01 02 + 03",LotteryType.KL8).rejectedRows)
        val valid=CollectedNumbersParser.parse("010203040506 + 01\n红球: 07 08 09 10 11 12 蓝球: 02",LotteryType.SSQ)
        assertEquals(0,valid.rejectedRows);assertEquals(2,valid.validGroupsCount)
    }
}
