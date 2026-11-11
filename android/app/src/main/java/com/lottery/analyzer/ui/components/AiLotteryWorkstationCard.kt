package com.lottery.analyzer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottery.analyzer.data.model.AiAnalysisMode
import com.lottery.analyzer.data.model.AiConfig
import com.lottery.analyzer.data.model.AiRecommendationResult
import com.lottery.analyzer.data.model.AiWindowOptions
import com.lottery.analyzer.data.engine.WindowStrategy
import com.lottery.analyzer.data.model.Kl8PlayType
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.ui.theme.BorderLight
import com.lottery.analyzer.ui.theme.GreenPrimary
import com.lottery.analyzer.ui.theme.OrangeLight
import com.lottery.analyzer.ui.theme.OrangePrimary
import com.lottery.analyzer.ui.theme.PurpleLight
import com.lottery.analyzer.ui.theme.PurplePrimary
import com.lottery.analyzer.ui.theme.RedLight
import com.lottery.analyzer.ui.theme.RedPrimary
import com.lottery.analyzer.ui.theme.SurfaceLight
import com.lottery.analyzer.ui.theme.TextPrimary
import com.lottery.analyzer.ui.theme.TextSecondary

/**
 * AI 大模型推演工作台卡片
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiLotteryWorkstationCard(
    lotteryType: LotteryType,
    targetIssue: String,
    aiConfig: AiConfig,
    isLoading: Boolean,
    aiResult: AiRecommendationResult?,
    aiError: String?,
    heartBallCount: Int,
    collectionError: String?,
    memoryOptions: com.lottery.analyzer.data.model.AiMemoryOptions,
    onMemoryOptionsChange: (com.lottery.analyzer.data.model.AiMemoryOptions) -> Unit,
    otherBallSet: Set<Int> = emptySet(),
    otherBets: List<List<Int>> = emptyList(),
    kl8PlayType: Kl8PlayType = Kl8PlayType.XUAN_TEN,
    windowOptions: AiWindowOptions = AiWindowOptions(),
    onWindowOptionsChange: (AiWindowOptions) -> Unit = {},
    onKl8PlayTypeChange: ((Kl8PlayType) -> Unit)? = null,
    onOpenSettings: () -> Unit,
    onRunAnalysis: (mode: AiAnalysisMode, count: Int) -> Unit,
    onSaveBetToPrediction: (index: Int) -> Unit,
    onApplyBetToHeart: (index: Int) -> Unit,
    onClearResult: () -> Unit,
    onViewLogs: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedMode by rememberSaveable { mutableStateOf(AiAnalysisMode.FULL_RECOMMEND) }
    var selectedBetCount by rememberSaveable { mutableIntStateOf(3) }
    var isThoughtExpanded by remember { mutableStateOf(false) }
    var windowText by rememberSaveable(windowOptions.periods, lotteryType) { mutableStateOf(windowOptions.periods.toString()) }
    val validWindow = windowText.toIntOrNull()?.takeIf { it in 1..1000 }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. 顶部标头
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(PurpleLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = PurplePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "AI 大模型推演工作台",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(PurpleLight)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "下一期 $targetIssue",
                                    fontSize = 11.sp,
                                    color = PurplePrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(
                            text = "当前引擎: ${aiConfig.provider.title} (${aiConfig.modelName})",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "AI平台配置",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 若未配置 API Key 则醒目提示
            if (aiConfig.apiKey.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(OrangeLight)
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = OrangePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "未配置 API Key，点击此处快速配置各平台大模型",
                            fontSize = 12.sp,
                            color = OrangePrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 快乐8官方10种玩法选择切换
            if (lotteryType == LotteryType.KL8) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "快乐8玩法选择 (官方10种玩法)：",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${kl8PlayType.title} (${kl8PlayType.pickCount}码)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrangePrimary
                        )
                    }

                    // 10种玩法单选 Chip (选十 至 选一)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Kl8PlayType.values().forEach { play ->
                            val isSelected = play == kl8PlayType
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) OrangePrimary else Color(0xFFF1F5F9))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) OrangePrimary else Color(0xFFE2E8F0),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onKl8PlayTypeChange?.invoke(play) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = play.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else TextPrimary
                                    )
                                }
                            }
                        }
                    }

                    // 特色玩法奖级说明
                    Text(
                        text = "💡 ${kl8PlayType.title}：${kl8PlayType.desc}",
                        fontSize = 11.sp,
                        color = Color(0xFFD97706),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFFFBEB))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Divider(color = BorderLight, thickness = 0.6.dp)
            }

            // 2. 推演模式选择
            Text(
                text = "推演模式选择：",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiAnalysisMode.values().forEach { mode ->
                    val isSelected = mode == selectedMode
                    val borderColor = if (isSelected) PurplePrimary else BorderLight
                    val bgColor = if (isSelected) PurpleLight.copy(alpha = 0.4f) else Color.Transparent

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .clickable { selectedMode = mode }
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, if (isSelected) PurplePrimary else BorderLight, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(PurplePrimary)
                                    )
                                }
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = mode.title,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) PurplePrimary else TextPrimary
                                    )
                                    if (mode == AiAnalysisMode.HEART_FUSION || mode == AiAnalysisMode.DIAGNOSE_SELECTION) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(已选心水: ${heartBallCount}码)",
                                            fontSize = 11.sp,
                                            color = if (heartBallCount > 0) RedPrimary else TextSecondary
                                        )
                                    }
                                }
                                Text(
                                    text = mode.desc,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            if (lotteryType != LotteryType.KL8) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Switch(
                        checked = windowOptions.enabled,
                        onCheckedChange = { onWindowOptionsChange(windowOptions.copy(enabled = it)) }
                    )
                    Text("结合历史开奖频次", fontSize = 13.sp)
                }
                if (windowOptions.enabled) {
                    Text("使用本地最新有效开奖作为截止期；频次与遗漏、他人选号分别计算。", fontSize = 11.sp, color = TextSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(30, 50, 100).forEach { periods ->
                            androidx.compose.material3.FilterChip(selected = validWindow == periods,
                                onClick = { windowText = periods.toString(); onWindowOptionsChange(windowOptions.copy(periods = periods)) },
                                label = { Text("${periods}期") })
                        }
                    }
                    androidx.compose.material3.OutlinedTextField(
                        value = windowText,
                        onValueChange = { value ->
                            if (value.length <= 4 && value.all { it in '0'..'9' }) {
                                windowText = value
                                val periods = value.toIntOrNull()
                                // Cancel an in-flight request even when the new draft is incomplete.
                                onWindowOptionsChange(windowOptions.copy(periods = periods?.takeIf { it in 1..1000 } ?: windowOptions.periods))
                            }
                        },
                        label = { Text("统计窗口（1~1000期）") },
                        isError = validWindow == null,
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WindowStrategy.values().forEach { strategy ->
                            androidx.compose.material3.FilterChip(selected = windowOptions.strategy == strategy,
                                onClick = { onWindowOptionsChange(windowOptions.copy(strategy = strategy)) },
                                label = { Text(strategy.title) })
                        }
                    }
                    Text("冷热为软偏好；分区均衡、包含连号由本地严格核验。本轮只生成单注。", fontSize = 11.sp, color = TextSecondary)
                }
            }

            AiMemoryOptionsCard(memoryOptions,onMemoryOptionsChange)

            // 3. 注数与执行栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 注数选择 (1注 / 3注 / 5注)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "生成注数:", fontSize = 12.sp, color = TextSecondary)
                    listOf(1, 3, 5).forEach { count ->
                        val isCountSelected = count == selectedBetCount
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isCountSelected) PurplePrimary else Color(0xFFF1F5F9))
                                .clickable { selectedBetCount = count }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${count}注",
                                fontSize = 11.sp,
                                color = if (isCountSelected) Color.White else TextPrimary,
                                fontWeight = if (isCountSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // 发起推演按钮
                Button(
                    onClick = { onRunAnalysis(selectedMode, selectedBetCount) },
                    enabled = !isLoading && aiConfig.apiKey.isNotBlank() && collectionError == null &&
                        (lotteryType == LotteryType.KL8 || !windowOptions.enabled || validWindow != null),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("推演中...", fontSize = 13.sp)
                    } else {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (selectedMode == AiAnalysisMode.DIAGNOSE_SELECTION) "开始诊断" else "AI 智能推演",
                            fontSize = 13.sp
                        )
                    }
                }
            }

            collectionError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }

            // 4. 正在加载的提示
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PurpleLight.copy(alpha = 0.5f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🧠 AI 正在结合 0~9+ 遗漏层级与统计指标进行深度分析...",
                            fontSize = 12.sp,
                            color = PurplePrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "耗时取决于模型与网络，请稍候",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // 5. 错误提示
            if (!aiError.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(RedLight)
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = RedPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = aiError,
                            fontSize = 12.sp,
                            color = RedPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (onViewLogs != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(RedPrimary)
                                    .clickable { onViewLogs() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(text = "排查日志", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(onClick = onClearResult, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "清除", tint = RedPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // 6. AI 结果展示区
            if (aiResult != null && !isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, PurpleLight, RoundedCornerShape(12.dp))
                        .background(Color(0xFFFAF5FF))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 策略摘要
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(16.dp))
                            Text(
                                text = "【核心策略】" + aiResult.strategySummary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PurplePrimary
                            )
                        }

                        IconButton(onClick = onClearResult, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "清除", tint = TextSecondary, modifier = Modifier.size(14.dp))
                        }
                    }

                    Text("请求 ${aiResult.requestedCount} 注，识别 ${aiResult.parsedCount} 组，本地核验保留 ${aiResult.recommendedBets.size} 注。", fontSize = 12.sp, color = PurplePrimary)
                    if (aiResult.recommendedBets.isEmpty()) Text("没有通过核验的推荐，可调整参数后手动重试。", fontSize = 12.sp, color = RedPrimary)
                    if (aiResult.rejectionReasons.isNotEmpty()) Text(
                        "剔除：" + aiResult.rejectionReasons.entries.joinToString("；") { "${it.key} ${it.value}组" }, fontSize = 11.sp, color = TextSecondary)
                    Text("请求模型：${aiResult.providerName}/${aiResult.modelName} · 分析截止 ${aiResult.baseIssue}", fontSize = 11.sp, color = TextSecondary)
                    aiResult.memoryContext?.let { memory ->
                        Text("记忆截止${memory.asOfIssue} · 正式${memory.formalIssues}期 · 观察${memory.observationIssues}期\n${memoryPolicyTitle(memory.policyStatus)}",fontSize=11.sp,color=TextSecondary)
                        Text("候选配额："+memory.weights.entries.joinToString(" / "){"${it.key} ${"%.0f".format(it.value*100)}%"},fontSize=10.sp,color=TextSecondary)
                    }
                    aiResult.selectionContext?.let { selection -> Text("本地${selection.candidates.size}组合法候选 → AI选择${aiResult.recommendedBets.size}组；同池随机对照已固定。",fontSize=11.sp,color=TextSecondary) }
                    aiResult.windowContext?.let { context ->
                        Text("${context.strategy.title} · 请求${context.stats.requestedPeriods}期/实取${context.stats.sampleCount}期 · ${context.stats.firstIssue}—${context.stats.lastIssue}", fontSize = 11.sp, color = TextSecondary)
                        if (context.stats.excludedCount > 0) Text("统计排除无效或重复记录 ${context.stats.excludedCount} 条", fontSize = 11.sp, color = TextSecondary)
                    }

                    // 思考与推演逻辑折叠区
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White)
                            .clickable { isThoughtExpanded = !isThoughtExpanded }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isThoughtExpanded) "收起 AI 分析原文" else "展开 AI 分析原文（未经规则校验）",
                            fontSize = 12.sp,
                            color = PurplePrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = if (isThoughtExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = PurplePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = isThoughtExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White)
                                .border(1.dp, BorderLight, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = aiResult.analysisText,
                                fontSize = 11.sp,
                                color = TextPrimary,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    // 推荐号码列表展示
                    if (aiResult.recommendedBets.isNotEmpty()) {
                        Text(
                            text = "AI 推荐注数方案：",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        aiResult.recommendedBets.forEachIndexed { index, bet ->
                            val (redBalls, blueBalls) = bet
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "第 ${index + 1} 注",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PurplePrimary
                                            )
                                            if (aiResult.mode == AiAnalysisMode.HEART_FUSION) {
                                                if (otherBets.isNotEmpty()) {
                                                    val maxOverlap = otherBets.maxOfOrNull { oBet -> redBalls.count { it in oBet } } ?: 0
                                                    Text(
                                                        text = "他人单注重合:${maxOverlap}码",
                                                        fontSize = 10.sp,
                                                        color = if (maxOverlap in 1..4) GreenPrimary else OrangePrimary,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                } else if (otherBallSet.isNotEmpty()) {
                                                    val overlap = redBalls.count { it in otherBallSet }
                                                    Text(
                                                        text = "他人重合:${overlap}码",
                                                        fontSize = 10.sp,
                                                        color = if (overlap in 1..4) GreenPrimary else OrangePrimary,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            // 设为心水
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(RedLight)
                                                    .clickable { onApplyBetToHeart(index) }
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Favorite, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(12.dp))
                                                    Text(text = "设为心水", fontSize = 11.sp, color = RedPrimary)
                                                }
                                            }

                                            // 记录为下期预测
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(PurpleLight)
                                                    .clickable { onSaveBetToPrediction(index) }
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(12.dp))
                                                    Text(text = "记录预测", fontSize = 11.sp, color = PurplePrimary, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    }

                                    // 号码球展示
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        redBalls.forEach { num ->
                                            LotteryBall(
                                                number = num,
                                                style = BallStyle.RED,
                                                size = 30.dp,
                                                fontSize = 13
                                            )
                                        }
                                        if (blueBalls.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            blueBalls.forEach { num ->
                                                LotteryBall(
                                                    number = num,
                                                    style = BallStyle.BLUE,
                                                    size = 30.dp,
                                                    fontSize = 13
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
