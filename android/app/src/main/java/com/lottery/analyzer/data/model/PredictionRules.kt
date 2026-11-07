package com.lottery.analyzer.data.model

object PredictionRules {
    fun inferPlay(type: LotteryType, reds: List<Int>): Kl8PlayType? =
        if (type == LotteryType.KL8) Kl8PlayType.values().firstOrNull { it.pickCount == reds.size } else null

    fun classify(type: LotteryType, reds: List<Int>, blues: List<Int>): PredictionKind {
        if (type == LotteryType.KL8) return if (reds.size in 1..10) PredictionKind.SINGLE else PredictionKind.REFERENCE
        return when {
            reds.size == type.redCount && blues.size == type.blueCount -> PredictionKind.SINGLE
            reds.size >= type.redCount && blues.size >= type.blueCount -> PredictionKind.COMPOUND
            else -> PredictionKind.REFERENCE
        }
    }

    fun validate(draft: PredictionDraft) {
        val type = draft.lotteryType
        require(draft.redBalls.isNotEmpty()) { "请先选择红球" }
        require(draft.redBalls.distinct().size == draft.redBalls.size && draft.redBalls.all { it in 1..type.maxRedNumber }) { "红球重复或超出范围" }
        require(draft.blueBalls.distinct().size == draft.blueBalls.size && draft.blueBalls.all { it in 1..type.maxBlueNumber }) { "蓝球重复或超出范围" }
        require(type == LotteryType.KL8 || draft.kl8PlayType == null) { "当前彩种不支持快乐8玩法" }
        when (draft.kind) {
            PredictionKind.SINGLE -> {
                val expected = if (type == LotteryType.KL8) requireNotNull(draft.kl8PlayType) { "请选择快乐8玩法" }.pickCount else type.redCount
                require(draft.redBalls.size == expected && draft.blueBalls.size == type.blueCount) { "单注号码数量与玩法不匹配" }
            }
            PredictionKind.COMPOUND -> {
                val pick = if (type == LotteryType.KL8) requireNotNull(draft.kl8PlayType) { "请选择复式玩法" }.pickCount else type.redCount
                require(draft.redBalls.size >= pick && draft.blueBalls.size >= type.blueCount) { "复式池号码不足" }
                require(draft.redBalls.size > pick || draft.blueBalls.size > type.blueCount) { "复式池应包含多于一注的号码" }
            }
            PredictionKind.REFERENCE -> Unit
            PredictionKind.LEGACY_UNCONFIRMED -> error("新记录必须明确注型")
        }
    }

    fun validateTarget(issue: String) {
        require(issue.length in 5..12 && issue.all { it in '0'..'9' }) { "请输入已核对的官方期号（5~12位数字）" }
    }

    fun validate(record: PredictionRecord) {
        validateTarget(record.targetIssue)
        validate(PredictionDraft(record.lotteryType, record.redBalls, record.blueBalls, record.strategy, record.kind, record.kl8PlayType, record.blueSource))
    }
}
