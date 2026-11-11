package com.lottery.analyzer.data.engine

import com.lottery.analyzer.data.model.*
import java.security.MessageDigest
import java.nio.ByteBuffer
import kotlin.random.Random

object StableSeed {
    fun of(value: String): Long = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))).long
    fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

object CandidatePoolEngine {
    fun generate(r: AiRecommendationRequest, limit: Int, seed: Long, check: () -> Unit = {}): CandidateSelectionContext {
        require(limit in 1..20 && r.betCount in 1..20)
        val rules = CandidateRules.from(r); rules.requireFeasible()
        val random = Random(seed)
        val families = WindowStrategy.values().filter { it != WindowStrategy.CONSECUTIVE || rules.redCount >= 2 }
        val weights = families.associateWith { r.memoryContext?.weights?.get(it.name)?.takeIf { n -> n.isFinite() && n > 0 } ?: 1.0 }
        val frequency = (1..rules.type.maxRedNumber).associateWith {
            r.windowContext?.stats?.redFrequency?.get(it) ?: when(it) { in r.analysis.hotNumbers -> 2; in r.analysis.warmNumbers -> 1; else -> 0 }
        }
        val blueFrequency = (1..rules.type.maxBlueNumber).associateWith { r.windowContext?.stats?.blueFrequency?.get(it) ?: 0 }
        val pool = linkedMapOf<Pair<List<Int>, List<Int>>, LocalCandidate>()
        fun weighted(values: List<Int>, count: Int, freq: Map<Int,Int>, cold: Boolean): List<Int> {
            val remaining = values.toMutableList(); val out = mutableListOf<Int>(); val max = freq.values.maxOrNull() ?: 0
            repeat(count) {
                check()
                val probabilities = remaining.map { if(cold) max - freq.getValue(it) + 1 else freq.getValue(it) + 1 }
                var ticket = random.nextInt(probabilities.sum())
                val chosen = probabilities.indices.first { ticket -= probabilities[it]; ticket < 0 }
                out += remaining.removeAt(chosen)
            }
            return out
        }
        fun pick(strategy: WindowStrategy): List<Int> {
            val all = frequency.keys.toList(); val n = rules.redCount
            return when(strategy) {
                WindowStrategy.HOT -> weighted(all,n,frequency,false)
                WindowStrategy.COLD -> weighted(all,n,frequency,true)
                WindowStrategy.BALANCED -> {
                    val ranked = all.sortedWith(compareByDescending<Int>{frequency.getValue(it)}.thenBy{it})
                    val half = ranked.size/2
                    weighted(ranked.take(half),(n+1)/2,frequency,false) + weighted(ranked.drop(half),n/2,frequency,true)
                }
                WindowStrategy.SEGMENT -> {
                    val zoneCount = if(r.lotteryType==LotteryType.KL8) 4 else 3
                    val extras=(0 until zoneCount).shuffled(random).take(n%zoneCount)
                    (0 until zoneCount).flatMap { zone -> all.filter{(it-1)*zoneCount/all.size==zone}.shuffled(random).take(n/zoneCount+if(zone in extras)1 else 0) }
                }
                WindowStrategy.CONSECUTIVE -> {
                    val first=random.nextInt(1,rules.type.maxRedNumber)
                    listOf(first,first+1)+all.filter{it!=first&&it!=first+1}.shuffled(random).take(n-2)
                }
            }.sorted()
        }
        val desired = maxOf(limit,r.betCount)
        for (attempt in 0 until 4000) {
            check()
            if(pool.size>=desired) break
            val strategy = if(attempt<families.size) families[attempt] else {
                var ticket=random.nextDouble()*weights.values.sum()
                families.firstOrNull { ticket -= weights.getValue(it); ticket<0 } ?: families.last()
            }
            val reds=pick(strategy)
            val blues=if(rules.blueCount==0) emptyList() else weighted(blueFrequency.keys.toList(),rules.blueCount,blueFrequency,strategy==WindowStrategy.COLD).sorted()
            if(rules.rejection(reds,blues)!=null || (reds to blues) in pool) continue
            val zones=if(r.lotteryType==LotteryType.KL8)4 else 3
            val overlaps=rules.otherPools.map{other->reds.count{it in other}}
            val id="C${(pool.size+1).toString().padStart(3,'0')}"
            pool[reds to blues]=LocalCandidate(id,strategy.name,reds,blues,reds.sum(),reds.count{it%2!=0},
                (0 until zones).map{zone->reds.count{(it-1)*zones/rules.type.maxRedNumber==zone}},
                reds.zipWithNext().count{(a,b)->b==a+1},reds.count{it in r.userSelection},overlaps.minOrNull()?:0,overlaps.maxOrNull()?:0)
        }
        require(pool.size>=r.betCount) { "当前约束只找到${pool.size}组合法候选，不足${r.betCount}组；请调整收集号码、玩法或模式" }
        val candidates=pool.values.toList()
        val order=candidates.map{it.id}.shuffled(Random(StableSeed.of("$seed:pool-baseline")))
        return CandidateSelectionContext(seed,candidates,order,basis=if(r.windowContext!=null)"FREQUENCY" else "OMISSION")
    }
}
