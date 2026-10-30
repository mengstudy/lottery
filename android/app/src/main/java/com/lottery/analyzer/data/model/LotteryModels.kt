package com.lottery.analyzer.data.model

enum class LotteryType(
    val code: String,
    val title: String,
    val subtitle: String,
    val maxRedNumber: Int,
    val redCount: Int,
    val maxBlueNumber: Int,
    val blueCount: Int,
    val dbName: String
) {
    SSQ(
        code = "ssq",
        title = "双色球",
        subtitle = "6红球 + 1蓝球",
        maxRedNumber = 33,
        redCount = 6,
        maxBlueNumber = 16,
        blueCount = 1,
        dbName = "lottery.db"
    ),
    DLT(
        code = "dlt",
        title = "大乐透",
        subtitle = "5前区 + 2后区",
        maxRedNumber = 35,
        redCount = 5,
        maxBlueNumber = 12,
        blueCount = 2,
        dbName = "dlt_lottery.db"
    ),
    KL8(
        code = "kl8",
        title = "快乐8",
        subtitle = "80选20号码",
        maxRedNumber = 80,
        redCount = 20,
        maxBlueNumber = 0,
        blueCount = 0,
        dbName = "kl8_lottery.db"
    )
}

/**
 * 通用开奖记录实体
 */
data class LotteryRecord(
    val issue: String,
    val drawDate: String,
    val redBalls: List<Int>,
    val blueBalls: List<Int> = emptyList(),
    val region: String = ""
)

/**
 * 某一期的遗漏统计对象（开奖前状态）
 */
data class MissingStat(
    val issue: String,
    val redMissing: Map<Int, Int>, // 号码 -> 遗漏期数
    val blueMissing: Map<Int, Int> = emptyMap()
)

/**
 * 遗漏层级分组数据（如 0层、1层...9+层）
 */
data class MissingGroupLevel(
    val level: String,             // "0", "1", ... "9+"
    val allNumbers: List<Int>,     // 处于该遗漏层级的所有号码
    val hitNumbers: List<Int>      // 本期命中/开出的号码
)

/**
 * 单期深度分析结果对象
 */
data class IssueAnalysis(
    val lotteryType: LotteryType,
    val issue: String,
    val drawDate: String,
    val redBalls: List<Int>,
    val blueBalls: List<Int>,
    // 统计指标
    val oddEvenRatio: String,       // 奇偶比，如 "3:3"
    val zoneRatio: String,          // 区间比，如 "2:2:2" 或 "5:5:5:5"
    val sumValue: Int,              // 和值
    val spanValue: Int,             // 跨度 (最大红球 - 最小红球)
    val consecutivePairs: List<String>, // 连号，如 ["07-08", "21-22"]
    val sameTailPairs: Map<Int, List<Int>>, // 同尾号，如 3 -> [03, 13, 23]
    // 遗漏情况
    val redMissingValues: List<Int>,// 本期红球开出时各自遗漏
    val blueMissingValues: List<Int>,// 本期蓝球开出时各自遗漏
    val maxRedMissing: Int,         // 最大红球遗漏
    val missingSum: Int,            // 遗漏总和
    // 遗漏层级分组
    val currentDrawnGroups: Map<String, List<Int>>, // 开出号码归属的层级
    val allMissingGroups: List<MissingGroupLevel>,   // 本期所有号码的层级分布及命中情况
    val nextSimulatedGroups: List<MissingGroupLevel>,// 下一期遗漏层级预测分布
    // 冷热号
    val hotNumbers: List<Int>,      // 热号 (<=4)
    val warmNumbers: List<Int>,     // 温号 (5-9)
    val coldNumbers: List<Int>      // 冷号 (>=10)
)

/**
 * 他人号码频次统计结果
 */
data class FrequencyAnalysisResult(
    val validGroupsCount: Int,           // 解析出的有效组数
    val frequencyMap: Map<Int, Int>,      // 号码 -> 出现次数
    val sortedByFreq: List<Pair<Int, Int>>, // 按频次降序
    val parsedBets: List<List<Int>> = emptyList() // 每行解析出的具体注数号码列表 (1行1注)
)

/**
 * 心水号码在遗漏层级的穿透分布
 */
data class HeartBallLevelDistribution(
    val level: String,
    val matchedBalls: List<Int>
)

/**
 * 用户保存的预测与选号策略记录实体
 */
data class PredictionRecord(
    val id: Long = 0,
    val lotteryType: LotteryType,
    val targetIssue: String,
    val redBalls: List<Int>,
    val blueBalls: List<Int> = emptyList(),
    val strategy: String,
    val createdAt: String,
    val note: String = ""
)

/**
 * 预测记录与实际开奖的比对核验结果
 */
data class PredictionVerificationResult(
    val record: PredictionRecord,
    val isDrawn: Boolean,
    val actualRecord: LotteryRecord? = null,
    val hitRedBalls: List<Int> = emptyList(),
    val hitBlueBalls: List<Int> = emptyList(),
    val resultSummary: String = "",
    val isWon: Boolean = false
)


