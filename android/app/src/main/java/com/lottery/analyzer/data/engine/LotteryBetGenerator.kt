package com.lottery.analyzer.data.engine

import com.lottery.analyzer.data.model.*

object LotteryBetGenerator {
    fun generate(type: LotteryType, analysis: IssueAnalysis?): GeneratedLotteryBet {
        val matching = analysis?.takeIf { it.lotteryType == type }
        val hotPool = (matching?.hotNumbers ?: emptyList()).shuffled()
        val warmPool = (matching?.warmNumbers ?: emptyList()).shuffled()
        val coldPool = (matching?.coldNumbers ?: emptyList()).shuffled()
        val pickedReds = mutableListOf<Int>()
        when (type) {
            LotteryType.SSQ -> {
                // 双色球6红：3热 + 2温 + 1冷
                pickedReds.addAll(hotPool.take(3))
                pickedReds.addAll(warmPool.take(2))
                pickedReds.addAll(coldPool.take(1))
            }
            LotteryType.DLT -> {
                // 大乐透5红：2热 + 2温 + 1冷
                pickedReds.addAll(hotPool.take(2))
                pickedReds.addAll(warmPool.take(2))
                pickedReds.addAll(coldPool.take(1))
            }
            LotteryType.KL8 -> {
                // 快乐8选十：5热 + 3温 + 2冷
                pickedReds.addAll(hotPool.take(5))
                pickedReds.addAll(warmPool.take(3))
                pickedReds.addAll(coldPool.take(2))
            }
        }

        val unique = pickedReds.filter { it in 1..type.maxRedNumber }.distinct()
        pickedReds.clear()
        pickedReds.addAll(unique)
        // 补足所需红球个数
        val targetRedCount = if (type == LotteryType.KL8) 10 else type.redCount
        while (pickedReds.size < targetRedCount) {
            val remain = (1..type.maxRedNumber).filter { it !in pickedReds }
            if (remain.isNotEmpty()) {
                pickedReds.add(remain.random())
            } else break
        }

        // 蓝球随机生成
        val pickedBlues = mutableListOf<Int>()
        when (type) {
            LotteryType.SSQ -> {
                // 双色球 1 个蓝球 (1-16)
                pickedBlues.add((1..16).random())
            }
            LotteryType.DLT -> {
                // 大乐透 2 个蓝球 (1-12，不重复)
                val bluePool = (1..12).shuffled()
                pickedBlues.addAll(bluePool.take(2))
            }
            LotteryType.KL8 -> {
                // 快乐8无蓝球
            }
        }

        return GeneratedLotteryBet(
            redBalls = pickedReds.sorted(),
            blueBalls = pickedBlues.sorted()
        )
    }
}
