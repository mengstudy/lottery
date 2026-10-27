package com.lottery.analyzer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottery.analyzer.data.model.IssueAnalysis
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.ui.components.BallStyle
import com.lottery.analyzer.ui.components.ColdHotCard
import com.lottery.analyzer.ui.components.IssueSelectorBar
import com.lottery.analyzer.ui.components.LotteryBall
import com.lottery.analyzer.ui.components.LotteryTypeSelectorBar
import com.lottery.analyzer.ui.components.MissingLevelCard
import com.lottery.analyzer.ui.components.StatChip
import com.lottery.analyzer.ui.theme.BluePrimary
import com.lottery.analyzer.ui.theme.ColdBlue
import com.lottery.analyzer.ui.theme.HotRed
import com.lottery.analyzer.ui.theme.OrangePrimary
import com.lottery.analyzer.ui.theme.RedDark
import com.lottery.analyzer.ui.theme.RedPrimary
import com.lottery.analyzer.ui.theme.TextPrimary
import com.lottery.analyzer.ui.theme.TextSecondary
import com.lottery.analyzer.ui.theme.WarmOrange
import com.lottery.analyzer.ui.viewmodel.LotteryUiState
import com.lottery.analyzer.ui.viewmodel.LotteryViewModel

@Composable
fun LotteryDashboardScreen(
    viewModel: LotteryViewModel,
    uiState: LotteryUiState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.syncMessage) {
        uiState.syncMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    val records = uiState.recordsDesc
    val currentIssue = uiState.currentIssue
    val currentIndex = records.indexOfFirst { it.issue == currentIssue }
    val hasPrev = currentIndex in 0 until records.size - 1
    val hasNext = currentIndex > 0

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // 0. 彩种切换栏 (双色球 / 大乐透 / 快乐8)
            LotteryTypeSelectorBar(
                currentType = uiState.currentType,
                onSelectType = { viewModel.switchLotteryType(it) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 1. 手机顶栏：期号控制栏
            IssueSelectorBar(
                currentIssue = currentIssue,
                allIssues = records.map { it.issue },
                hasPrev = hasPrev,
                hasNext = hasNext,
                isSyncing = uiState.isSyncing,
                onPrevClick = { viewModel.gotoPrevIssue() },
                onNextClick = { viewModel.gotoNextIssue() },
                onIssueSelect = { viewModel.selectIssue(it) },
                onSyncClick = { viewModel.syncLatestData() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RedPrimary)
                }
            } else if (uiState.analysis != null) {
                val analysis = uiState.analysis

                // 2. 开奖号码展示卡片
                LotteryBallsCard(analysis = analysis)

                Spacer(modifier = Modifier.height(12.dp))

                // 3. 核心统计指标网格
                LotteryMetricsCard(analysis = analysis)

                Spacer(modifier = Modifier.height(12.dp))

                // 4. 下期遗漏分组预测（最重要功能，联动心水红球与他人频次）
                MissingLevelCard(
                    title = "⚡ 下期遗漏分组预测 (参考池)",
                    subtitle = if (uiState.myHeartBalls.isNotEmpty()) "已高亮展示心水号码 (${uiState.myHeartBalls.size}码)" else "模拟下期各遗漏层级的球号分布，提前预判冷热分流",
                    levels = analysis.nextSimulatedGroups,
                    isPrediction = true,
                    initialExpanded = true,
                    heartBalls = uiState.myHeartBalls,
                    freqMap = uiState.frequencyResult.frequencyMap,
                    onBallClick = { viewModel.toggleHeartBall(it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 5. 本期开出号码遗漏卡片
                DrawnBallsMissingCard(analysis = analysis)

                Spacer(modifier = Modifier.height(12.dp))

                // 6. 本期全盘 0~9+ 遗漏层级卡片
                MissingLevelCard(
                    title = "🎯 本期开出号码遗漏分组",
                    subtitle = "开出前全盘号码在 0~9+ 层的分布，红色高亮为本期命中球",
                    levels = analysis.allMissingGroups,
                    isPrediction = false,
                    initialExpanded = false
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 7. 冷热温号全盘分布
                ColdHotCard(
                    hotNumbers = analysis.hotNumbers,
                    warmNumbers = analysis.warmNumbers,
                    coldNumbers = analysis.coldNumbers
                )

                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Text(
                    text = uiState.errorMessage ?: "暂无开奖数据",
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * 开奖号码卡片
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LotteryBallsCard(analysis: IssueAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "第 ${analysis.issue} 期",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RedDark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = analysis.lotteryType.title,
                        fontSize = 12.sp,
                        color = RedPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFEF2F2))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "开奖: ${analysis.drawDate}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 号码球展示
            when (analysis.lotteryType) {
                LotteryType.SSQ -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        analysis.redBalls.forEachIndexed { idx, ball ->
                            val missing = analysis.redMissingValues.getOrElse(idx) { 0 }
                            LotteryBall(
                                number = ball,
                                style = BallStyle.RED,
                                size = 40.dp,
                                fontSize = 16,
                                badgeText = "$missing"
                            )
                        }
                        // 蓝球
                        analysis.blueBalls.firstOrNull()?.let { blue ->
                            val missing = analysis.blueMissingValues.firstOrNull() ?: 0
                            LotteryBall(
                                number = blue,
                                style = BallStyle.BLUE,
                                size = 40.dp,
                                fontSize = 16,
                                badgeText = "$missing"
                            )
                        }
                    }
                }
                LotteryType.DLT -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 5个前区红球
                        analysis.redBalls.forEachIndexed { idx, ball ->
                            val missing = analysis.redMissingValues.getOrElse(idx) { 0 }
                            LotteryBall(
                                number = ball,
                                style = BallStyle.RED,
                                size = 40.dp,
                                fontSize = 16,
                                badgeText = "$missing"
                            )
                        }
                        // 2个后区蓝球
                        analysis.blueBalls.forEachIndexed { idx, blue ->
                            val missing = analysis.blueMissingValues.getOrElse(idx) { 0 }
                            LotteryBall(
                                number = blue,
                                style = BallStyle.BLUE,
                                size = 40.dp,
                                fontSize = 16,
                                badgeText = "$missing"
                            )
                        }
                    }
                }
                LotteryType.KL8 -> {
                    // 快乐8有20个号码，以 10 列网格排布
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        analysis.redBalls.forEachIndexed { idx, ball ->
                            val missing = analysis.redMissingValues.getOrElse(idx) { 0 }
                            LotteryBall(
                                number = ball,
                                style = BallStyle.ORANGE,
                                size = 30.dp,
                                fontSize = 12,
                                badgeText = if (missing > 0) "$missing" else null
                            )
                        }
                    }
                }
            }

            Text(
                text = "注：球右上角数字为该号码开奖时的遗漏期数",
                fontSize = 10.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

/**
 * 核心指标网格卡片
 */
@Composable
private fun LotteryMetricsCard(analysis: IssueAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = "📊 本期核心形态指标",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            // 第一行指标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    title = "和值",
                    value = "${analysis.sumValue}",
                    modifier = Modifier.weight(1f),
                    backgroundColor = Color(0xFFFEF2F2),
                    valueColor = RedPrimary
                )
                StatChip(
                    title = "跨度",
                    value = "${analysis.spanValue}",
                    modifier = Modifier.weight(1f),
                    backgroundColor = Color(0xFFF0FDF4),
                    valueColor = Color(0xFF16A34A)
                )
                StatChip(
                    title = "奇偶比",
                    value = analysis.oddEvenRatio,
                    modifier = Modifier.weight(1f),
                    backgroundColor = Color(0xFFEFF6FF),
                    valueColor = BluePrimary
                )
                StatChip(
                    title = if (analysis.lotteryType == LotteryType.KL8) "四区比" else "三区比",
                    value = analysis.zoneRatio,
                    modifier = Modifier.weight(1f),
                    backgroundColor = Color(0xFFFFF7ED),
                    valueColor = OrangePrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 第二行指标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    title = "最大遗漏",
                    value = "${analysis.maxRedMissing}期",
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    title = "遗漏总和",
                    value = "${analysis.missingSum}",
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    title = "连号",
                    value = if (analysis.consecutivePairs.isEmpty()) "无" else analysis.consecutivePairs.joinToString(" "),
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    title = "同尾号",
                    value = if (analysis.sameTailPairs.isEmpty()) "无" else analysis.sameTailPairs.keys.joinToString("尾", postfix = "尾"),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 开出号码遗漏详情卡片
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrawnBallsMissingCard(analysis: IssueAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp, 16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(RedPrimary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "🎯 开出号码遗漏详情与冷热判定",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                analysis.redBalls.forEachIndexed { idx, ball ->
                    val missing = analysis.redMissingValues.getOrElse(idx) { 0 }
                    val (tag, tagColor, bgColor) = when {
                        missing <= 4 -> Triple("热", HotRed, Color(0xFFFEF2F2))
                        missing in 5..9 -> Triple("温", WarmOrange, Color(0xFFFFF7ED))
                        else -> Triple("冷", ColdBlue, Color(0xFFF0F9FF))
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format("%02d", ball),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = RedPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "遗漏${missing}期",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(tagColor)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 9.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
