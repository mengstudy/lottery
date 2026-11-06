package com.lottery.analyzer.data.model

/**
 * 旋转矩阵保奖模式
 */
enum class WheelGuaranteeMode(val title: String, val subtitle: String, val desc: String) {
    NONE(
        title = "不使用旋转矩阵",
        subtitle = "全复式常规过滤",
        desc = "对所选号码的所有组合进行条件过滤，不做保奖稀疏压缩"
    ),
    WHEEL_MATCH_MINUS_1(
        title = "中 N 保 N-1 (保高阶奖)",
        subtitle = "中6保5 / 中5保4",
        desc = "若选号池中包含全部开奖红球，确保至少有一注命中 5 个红球(双色球)或 4 个前区(大乐透)，注数大幅精简"
    ),
    WHEEL_MATCH_MINUS_2(
        title = "中 N 保 N-2 (经济普及型)",
        subtitle = "中6保4 / 中5保3",
        desc = "以极少注数进行防守型投注，确保只要大底命中全部开奖球，必有注数命中保底奖级"
    )
}

/**
 * 缩水过滤配置参数
 */
data class ShrinkFilterConfig(
    // 1. 和值过滤
    val enableSumFilter: Boolean = false,
    val sumMin: Int = 80,
    val sumMax: Int = 120,

    // 2. 奇偶比过滤 (保留选中的奇数个数，例如双色球选 2, 3, 4 代表保留 2:4, 3:3, 4:2)
    val enableOddEvenFilter: Boolean = false,
    val allowedOddCounts: Set<Int> = setOf(2, 3, 4),

    // 3. 连号对数过滤 (例如 0代表无连号，1代表1对连号，2代表2对连号或三连号)
    val enableConsecutiveFilter: Boolean = false,
    val allowedConsecutiveCounts: Set<Int> = setOf(0, 1),

    // 4. 同尾号组数过滤 (如至少有一组同尾号)
    val enableSameTailFilter: Boolean = false,
    val requireSameTail: Boolean = true,

    // 5. 跨度范围过滤 (最大红球 - 最小红球)
    val enableSpanFilter: Boolean = false,
    val spanMin: Int = 18,
    val spanMax: Int = 30,

    // 6. 上期重号过滤 (与上一期开奖红球重复的个数)
    val enableRepeatFilter: Boolean = false,
    val allowedRepeatCounts: Set<Int> = setOf(1, 2),

    // 7. 历史开奖一等奖排除 (排除历史曾开出过完全一致的 6 码红球)
    val enableExcludeHistory: Boolean = true,

    // 8. 旋转矩阵保奖模式
    val wheelMode: WheelGuaranteeMode = WheelGuaranteeMode.NONE
)

/**
 * 缩水计算结果
 */
data class ShrinkResult(
    val selectedBalls: List<Int>,
    val originalTotalBets: Int,
    val filteredBets: List<List<Int>>,
    val filteredCount: Int,
    val shrinkRatio: Float,       // 缩水率 (0~100%)
    val originalCost: Int,        // 原复式金额 (元)
    val filteredCost: Int,        // 缩水后金额 (元)
    val executionTimeMs: Long,    // 计算耗时 (毫秒)
    val isStrictGuaranteed: Boolean = true, // 是否达成数学意义上的严格保奖
    val coverageRatio: Float = 100f,        // 实际保奖覆盖率 (0~100%)
    val warningMessage: String? = null      // 告警提示 (如已破保警告或规模提示)
)
