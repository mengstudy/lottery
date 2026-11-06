package com.lottery.analyzer.data.model

/**
 * 走势图单元格数据
 */
data class TrendCell(
    val number: Int,
    val isHit: Boolean,
    val missing: Int // 开出时一般为0(或开出时遗漏)，未开出时为该期开奖前的遗漏值
)

/**
 * 走势图单期行数据
 */
data class TrendRow(
    val issue: String,
    val drawDate: String,
    val redCells: List<TrendCell>,  // 1..maxRedNumber 的所有单元格
    val blueCells: List<TrendCell>, // 1..maxBlueNumber 的所有单元格
    val sumValue: Int,
    val oddEvenRatio: String,
    val hitRedNumbers: List<Int> = redCells.filter { it.isHit }.map { it.number }.sorted(),
    val hitBlueNumbers: List<Int> = blueCells.filter { it.isHit }.map { it.number }.sorted()
)

/**
 * 单个号码的全局统计指标 (用于走势图底部统计栏)
 */
data class NumberTrendSummary(
    val number: Int,
    val isBlue: Boolean = false,
    val hitCount: Int,           // 出现次数
    val avgMissing: Float,       // 平均遗漏期数
    val maxMissing: Int,         // 最大遗漏期数
    val maxContinuous: Int,      // 最大连出期数
    val currentMissing: Int      // 当前遗漏期数
)

/**
 * 走势图完整矩阵数据包
 */
data class TrendMatrixData(
    val lotteryType: LotteryType,
    val rows: List<TrendRow>, // 按期号排列 (支持倒序或升序)
    val redSummaries: List<NumberTrendSummary>,
    val blueSummaries: List<NumberTrendSummary>
)
