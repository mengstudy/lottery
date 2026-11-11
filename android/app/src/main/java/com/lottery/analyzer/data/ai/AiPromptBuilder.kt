package com.lottery.analyzer.data.ai

import com.lottery.analyzer.data.model.*

/** Single prompt builder used both for the sent request and its credential-free archive. */
object AiPromptBuilder {
    fun build(r: AiRecommendationRequest): AiPromptContent {
        var prompt=userPrompt(r.lotteryType,r.analysis,r.targetNextIssue,r.mode,r.userSelection,r.topOtherBalls,
            r.topOtherBlues,r.otherBets,r.otherCompleteBets,r.betCount,r.kl8PlayType,r.windowContext)
        if(r.selectionContext!=null)prompt=prompt.substringBefore("\n请在 200 字内简述推演思路")
        r.memoryContext?.let { memory ->
            prompt+="\n【逐期记忆摘要：仅截止${memory.asOfIssue}，窗口${memory.historyPeriods}期】\n"+
                "匹配当前规则的正式样本${memory.formalIssues}期；观察记录${memory.observationIssues}期不作为成绩证据。\n"+
                "历史请求${memory.requestedGroups}组，校验保留${memory.retainedGroups}组；这仅表示合规率。\n"+
                memory.formalSummary+"\n策略状态=${memory.policyStatus}；配额=${memory.weights}。不得把历史命中描述为下期中奖概率。\n"
        }
        val selection=r.selectionContext?:return AiPromptContent(system(),prompt)
        prompt=prompt+"\n【本地候选：只能选编号，不能改写号码】\n"+selection.candidates.joinToString("\n") {
            "[${it.id}] ${it.reds.joinToString(" ")}"+if(it.blues.isNotEmpty())" + ${it.blues.joinToString(" ")}" else ""
        }+"\n"+selection.candidates.joinToString("\n") {
            "[${it.id}] ${it.strategy} 和${it.sum} 奇${it.odd}:${it.reds.size-it.odd} 区${it.zones.joinToString(":")} 连${it.consecutive} 心水重${it.heartOverlap} 他人重${it.minOtherOverlap}~${it.maxOtherOverlap}"
        }+"\n在符合心水等软偏好的候选之间，优先降低已选各组的号码重叠，说明多样性取舍；不把多样性说成提高中奖概率。"+
            "\n请选择${r.betCount}个不同编号，仅返回JSON：{\"candidate_ids\":[\"实际候选编号\"],\"explanation\":\"简短依据\",\"strategy_summary\":\"偏好总结\"}。数组必须使用上方真实编号。"
        return AiPromptContent("你是候选方案比较助手。只能从给定候选中选择编号，不得生成新号码或修改候选。遵守用户硬约束；只能引用提供的统计。诊断模式也只能选择已有候选并作简短解释。只输出要求的JSON，不使用Markdown，不声称提高中奖概率。",prompt)
    }
    private fun system(): String = """
                你是帮助整理彩票开奖统计和选号偏好的分析助手。
                请基于用户提供的历史开奖数据、形态指标、0~9+各层遗漏号码池以及他人偏好，进行严谨客观的推演。
                【执行要求】：
                1. 声明彩票开奖属于随机事件，推演分析仅基于统计学形态与离散数学偏好，请理性购彩。
                2. 论述逻辑请务必【简明扼要，直奔主题】（建议控制在 200 字以内），重点说明奇偶比、和值及 0~9+ 遗漏层级的动态平衡。
                3. 融合模式按本次任务列明的他人单注交集范围执行；它是用户的差异化偏好，不代表改变中奖概率。
                4. 随后严格输出标准结构的推荐号码，确保格式严格对齐如下：
                   【推荐第1注】红球: 02 08 15 21 26 30 + 蓝球: 06
                   【推荐第2注】红球: 05 12 18 23 27 33 + 蓝球: 14
                   若是快乐8（无蓝球）：
                   【推荐第1注】红球: 03 08 15 22 29 35 48 57 66 79
                5. 最后附带一句话【策略总结】概括本次选号的核心偏好（如：心水为胆+防撞号微重合2码+温冷回补等）。
                6. 历史开奖频次、当前遗漏、用户心水、他人选号频次是不同维度，不可互相替代。历史出现率不等于下期中奖概率，不得声称提高胜率。
                7. 每注必须是当前彩种合法单注，无越界、无重复；本次分区或连号硬约束必须满足。若约束无法兼容，请说明原因，不得伪造统计或修改约束。
            """.trimIndent()

    private fun userPrompt(
        lotteryType: LotteryType,
        analysis: IssueAnalysis,
        targetNextIssue: String,
        mode: AiAnalysisMode,
        userSelection: Set<Int>,
        topOtherBalls: List<Pair<Int, Int>>,
        topOtherBlues: List<Pair<Int, Int>> = emptyList(),
        otherBets: List<List<Int>> = emptyList(),
        otherCompleteBets: List<Pair<List<Int>, List<Int>>> = emptyList(),
        betCount: Int,
        kl8PlayType: Kl8PlayType = Kl8PlayType.XUAN_TEN,
        windowContext: AiWindowContext? = null
    ): String {
        val sb = StringBuilder()
        val maxOverlap = if (lotteryType == LotteryType.KL8) minOf(4, maxOf(1, kl8PlayType.pickCount - 1)) else 4
        if (lotteryType == LotteryType.KL8) {
            sb.appendLine("【彩种与玩法】: 快乐8 - ${kl8PlayType.title} (单注选 ${kl8PlayType.pickCount} 码，${kl8PlayType.desc})")
        } else {
            sb.appendLine("【彩种】: ${lotteryType.title} (${lotteryType.subtitle})")
        }
        sb.appendLine("【分析基准期】: 第 ${analysis.issue} 期 (开奖日: ${analysis.drawDate})")
        sb.appendLine("【下期目标期号】: ${targetNextIssue.ifBlank { "待用户核对官方期号，不能自行编造" }}")
        sb.appendLine("【最新开出号码】:")
        sb.append("  红球: ").appendLine(analysis.redBalls.joinToString(" ") { String.format("%02d", it) })
        if (analysis.blueBalls.isNotEmpty()) {
            sb.append("  蓝球: ").appendLine(analysis.blueBalls.joinToString(" ") { String.format("%02d", it) })
        }
        sb.appendLine("【形态统计指标】:")
        sb.appendLine("  和值: ${analysis.sumValue} | 跨度: ${analysis.spanValue} | 奇偶比: ${analysis.oddEvenRatio} | 区间比: ${analysis.zoneRatio}")
        if (analysis.consecutivePairs.isNotEmpty()) {
            sb.appendLine("  连号: ${analysis.consecutivePairs.joinToString(", ")}")
        }
        sb.appendLine("  冷温热分布: 热号(${analysis.hotNumbers.size}个), 温号(${analysis.warmNumbers.size}个), 冷号(${analysis.coldNumbers.size}个)")

        sb.appendLine("\n【下期目标期 0~9+ 遗漏层级模拟分布】:")
        for (group in analysis.nextSimulatedGroups) {
            val balls = group.allNumbers.joinToString(" ") { String.format("%02d", it) }
            val label = when (group.level) {
                "0" -> "0层 (重号候选)"
                "1" -> "1层 (活跃号)"
                "9+" -> "9+层 (深冷号)"
                else -> "${group.level}层"
            }
            sb.appendLine("  $label: [${group.allNumbers.size}码] $balls")
        }

        if (userSelection.isNotEmpty()) {
            sb.appendLine("\n【用户当前自选心水红球】:")
            sb.appendLine("  " + userSelection.sorted().joinToString(" ") { String.format("%02d", it) } + " (共${userSelection.size}个)")
        }

        val hasCompleteBets = otherCompleteBets.isNotEmpty()
        if (hasCompleteBets || otherBets.isNotEmpty()) {
            val totalBetsCount = if (hasCompleteBets) otherCompleteBets.size else otherBets.size
            sb.appendLine("\n【工具箱他人收集号码池 (共${totalBetsCount}注，1行1注)】:")
            if (hasCompleteBets) {
                otherCompleteBets.forEachIndexed { idx, (reds, blues) ->
                    val redStr = reds.joinToString(" ") { String.format("%02d", it) }
                    val blueStr = if (blues.isNotEmpty()) " + 蓝球: " + blues.joinToString(" ") { String.format("%02d", it) } else ""
                    sb.appendLine("  他人第${idx + 1}注: 红球: $redStr$blueStr")
                }
            } else {
                otherBets.forEachIndexed { idx, bet ->
                    val lineStr = bet.joinToString(" ") { String.format("%02d", it) }
                    sb.appendLine("  他人第${idx + 1}注: $lineStr")
                }
            }
            if (topOtherBalls.isNotEmpty()) {
                val rankList = topOtherBalls.take(10).joinToString(", ") { "${String.format("%02d", it.first)}(${it.second}次)" }
                sb.appendLine("  他人红球高频热度榜 Top 10: $rankList")
            }
            if (topOtherBlues.isNotEmpty()) {
                val blueRankList = topOtherBlues.take(6).joinToString(", ") { "${String.format("%02d", it.first)}(${it.second}次)" }
                sb.appendLine("  他人蓝球高频热度榜: $blueRankList")
            }
        } else if (topOtherBalls.isNotEmpty()) {
            val otherBallsList = topOtherBalls.map { it.first }
            val rankList = topOtherBalls.take(10).joinToString(", ") { "${String.format("%02d", it.first)}(${it.second}次)" }
            val allOtherPoolStr = otherBallsList.sorted().joinToString(" ") { String.format("%02d", it) }
            sb.appendLine("\n【工具箱他人收集号码池 (频次排行榜)】:")
            sb.appendLine("  高频红球排行 Top 10: $rankList")
            sb.appendLine("  他人红球全集池 (${otherBallsList.size}码): $allOtherPoolStr")
            if (topOtherBlues.isNotEmpty()) {
                val blueRankList = topOtherBlues.take(6).joinToString(", ") { "${String.format("%02d", it.first)}(${it.second}次)" }
                sb.appendLine("  高频蓝球排行: $blueRankList")
            }
        }

        windowContext?.let { sb.appendLine(AiWindowPrompt.build(it)) }

        sb.appendLine("\n【本次任务模式】: ${mode.title}")
        when (mode) {
            AiAnalysisMode.FULL_RECOMMEND -> {
                sb.appendLine("请综合历史形态走势、冷温热平衡与 0~9+ 遗漏层级分布，精简分析后为下期生成 $betCount 注推荐号码。")
            }
            AiAnalysisMode.HEART_FUSION -> {
                sb.appendLine("请执行【心水与他人频次融合推演】，严格遵循以下规则生成 $betCount 注方案：")
                sb.appendLine("1. 【心水胆码核心】：以用户自选的心水红球作为核心骨架与优先保留号码。")
                if (otherBets.isNotEmpty()) {
                    sb.appendLine("2. 【严格防撞号与重复度约束 (1-$maxOverlap 码)】：")
                    sb.appendLine("   - 重点避开上面列出的他人每一注号码（1行1注），严禁整注雷同或高度撞车！")
                    sb.appendLine("   - 所推荐的任意一注号码，与上述他人已购的【任意单注】之间的重复号码个数，必须严格控制在 1 ~ $maxOverlap 个之间（即每注与他人的重复度为 1-$maxOverlap 码，最多不超过 $maxOverlap 码）！")
                    sb.appendLine("3. 【其余号码补充】：除 1~$maxOverlap 码适度借鉴他人号码以及用户心水球外，其余空缺号码必须从他人未涉足的优质 0~9+ 遗漏层级（如活跃温号、深冷回补号）中精选补齐，确保组合兼顾热度与独特性！")
                } else if (topOtherBalls.isNotEmpty()) {
                    sb.appendLine("2. 【严格防撞号约束 (1-$maxOverlap 码)】：所推荐的每注号码与他人号码的重叠数必须严格控制在 1 ~ $maxOverlap 码之间，严禁超过 $maxOverlap 码！")
                    sb.appendLine("3. 其余号码从他人未选的优质 0~9+ 遗漏层级中补充。")
                } else {
                    sb.appendLine("2. 结合下期 0~9+ 遗漏层级与冷热号轮动，补齐其余红蓝球。")
                }
                if (lotteryType.maxBlueNumber > 0 && topOtherBlues.isNotEmpty()) {
                    sb.appendLine("4. 【他人蓝球参考指导】：参考他人收集的蓝球热度榜，分析他人扎堆热选与被忽视的冷门蓝球，结合蓝球历史走势推演最具博弈性价比的蓝球推荐方案！")
                }
            }
            AiAnalysisMode.DIAGNOSE_SELECTION -> {
                sb.appendLine("请对用户当前自选的心水号码进行全方位健康度诊断（分析和值、奇偶比、连号及遗漏层级结构是否合理），随后给出 $betCount 注微调优化方案。")
            }
        }

        sb.appendLine("\n请在 200 字内简述推演思路，随后立即输出标准格式推荐号码与一句话【策略总结】。")
        return sb.toString()
    }

}
