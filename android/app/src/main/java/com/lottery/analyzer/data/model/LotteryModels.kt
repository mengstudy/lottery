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
 * 快乐8官方支持的10种单注玩法类型 (选一 至 选十)
 */
enum class Kl8PlayType(
    val title: String,
    val pickCount: Int,
    val desc: String
) {
    XUAN_TEN("选十", 10, "单注选10码，封顶500万，中0也中奖"),
    XUAN_NINE("选九", 9, "单注选9码，最高30万，中0也中奖"),
    XUAN_EIGHT("选八", 8, "单注选8码，最高5万，中0也中奖"),
    XUAN_SEVEN("选七", 7, "单注选7码，最高8500元，中0也中奖"),
    XUAN_SIX("选六", 6, "单注选6码，最高2880元，中3~6码有奖"),
    XUAN_FIVE("选五", 5, "单注选5码，中5得1000元，高返奖首选"),
    XUAN_FOUR("选四", 4, "单注选4码，中2~4码有奖"),
    XUAN_THREE("选三", 3, "单注选3码，中2~3码有奖"),
    XUAN_TWO("选二", 2, "单注选2码，中2得19元"),
    XUAN_ONE("选一", 1, "单注选1码，中1得4.6元")
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


