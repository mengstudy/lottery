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
    otherBallSet: Set<Int> = emptySet(),
    otherBets: List<List<Int>> = emptyList(),
    onOpenSettings: () -> Unit,
    onRunAnalysis: (mode: AiAnalysisMode, count: Int) -> Unit,
    onSaveBetToPrediction: (index: Int) -> Unit,
    onApplyBetToHeart: (index: Int) -> Unit,
    onClearResult: () -> Unit,
    onViewLogs: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedMode by remember { mutableStateOf(AiAnalysisMode.FULL_RECOMMEND) }
    var selectedBetCount by remember { mutableIntStateOf(3) }
    var isThoughtExpanded by remember { mutableStateOf(false) }

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
                    enabled = !isLoading && aiConfig.apiKey.isNotBlank(),
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
                            text = "推演耗时约 5~15 秒，请稍候",
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
                            text = if (isThoughtExpanded) "收起 AI 深度推演论述" else "展开查看 AI 概率推演思路与形态依据",
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
