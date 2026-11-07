package com.lottery.analyzer.data.engine

import com.lottery.analyzer.data.model.*
import org.junit.Assert.*
import org.junit.Test

class LotteryBetGeneratorTest {
    @Test fun generatedSingleBetsAreLegalForEachTypeEvenWithoutAnalysis(){
        for(type in LotteryType.values())repeat(100){
            val bet=LotteryBetGenerator.generate(type,null)
            assertEquals(if(type==LotteryType.KL8)10 else type.redCount,bet.redBalls.size)
            assertEquals(bet.redBalls.size,bet.redBalls.distinct().size)
            assertTrue(bet.redBalls.all{it in 1..type.maxRedNumber})
            assertEquals(type.blueCount,bet.blueBalls.size);assertEquals(bet.blueBalls.size,bet.blueBalls.distinct().size)
        }
    }
    @Test fun mismatchedAnalysisCannotLeakNumbersIntoAnotherLottery(){
        val analysis=LotteryAnalysisEngine.analyze(LotteryRecord("1","2026-01-01",(61..80).toList()),null,LotteryType.KL8)
        repeat(50){assertTrue(LotteryBetGenerator.generate(LotteryType.SSQ,analysis).redBalls.all{it in 1..33})}
    }
}
