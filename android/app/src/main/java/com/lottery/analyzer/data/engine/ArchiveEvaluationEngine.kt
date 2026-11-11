package com.lottery.analyzer.data.engine

import com.lottery.analyzer.data.model.*
import kotlin.random.Random
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.exp

data class BaselineResult(val red: Double?, val blue: Double?, val samples: Int)

object ArchiveEvaluationEngine {
    const val VERSION = "eval-v1"
    fun revision(type: LotteryType, draw: LotteryRecord) = StableSeed.hash("${type.name}|${draw.issue}|${draw.drawDate}|${draw.redBalls.sorted()}|${draw.blueBalls.sorted()}")
    fun hits(numbers: List<Int>, actual: List<Int>) = numbers.count { it in actual }

    /** Samples depend only on frozen shape/rules/seed. The actual draw is used solely for scoring. */
    fun baseline(rules: CandidateRules, seed: Long, draw: LotteryRecord, constrained: Boolean, check: () -> Unit = {}): BaselineResult {
        val random = Random(seed); var samples=0; var red=0.0; var blue=0.0
        val limit=if(constrained)4096 else 64
        for(attempt in 0 until limit) {
            check()
            if(samples==64)break
            val reds=(1..rules.type.maxRedNumber).shuffled(random).take(rules.redCount)
            val blues=(1..rules.type.maxBlueNumber).shuffled(random).take(rules.blueCount)
            if(constrained && rules.rejection(reds,blues)!=null)continue
            red+=hits(reds,draw.redBalls);blue+=hits(blues,draw.blueBalls);samples++
        }
        return BaselineResult(if(samples==0)null else red/samples,if(samples==0)null else blue/samples,samples)
    }
}

object AdaptivePolicyEngine {
    fun uniform(families: List<String>) = families.associateWith { 1.0/families.size }
    /** Scores are per-draw normalized excess hits in [-2,2], never independent ticket samples. */
    fun proposed(scores: Map<String,List<Double>>, families: List<String>): Map<String,Double> {
        val evidence=families.associateWith { name ->
            val values=scores[name].orEmpty()
            if(values.size<30)0.0 else maxOf(0.0,values.average()-4.0*sqrt(ln(2.0*families.size/0.05)/(2.0*values.size)))
        }
        val transformed=evidence.mapValues{ exp(minOf(2.0,it.value)) }
        val total=transformed.values.sum()
        return transformed.mapValues{0.1+(1.0-0.1*families.size)*it.value/total}
    }
    fun bounded(previous: Map<String,Double>, target: Map<String,Double>): Map<String,Double> {
        // A convex step preserves normalization/exploration and caps every change at five points.
        val maxDelta=target.keys.maxOf { kotlin.math.abs(target.getValue(it)-previous.getValue(it)) }
        val step=if(maxDelta<=0.05)1.0 else 0.05/maxDelta
        return target.mapValues{(name,value)->previous.getValue(name)+(value-previous.getValue(name))*step}
    }
}
