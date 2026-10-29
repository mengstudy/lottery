package com.lottery.analyzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.lottery.analyzer.data.model.HeartBallLevelDistribution
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.ui.components.FrequencyCompareCard
import com.lottery.analyzer.ui.components.HeartBallSelectorCard
import com.lottery.analyzer.ui.components.LotteryTypeSelectorBar
import com.lottery.analyzer.ui.components.MissingLevelCard
import com.lottery.analyzer.ui.components.PredictionRecordsCard
import com.lottery.analyzer.ui.theme.RedDark
import com.lottery.analyzer.ui.theme.RedPrimary
import com.lottery.analyzer.ui.theme.TextPrimary
import com.lottery.analyzer.ui.theme.TextSecondary
import com.lottery.analyzer.ui.viewmodel.LotteryUiState
import com.lottery.analyzer.ui.viewmodel.LotteryViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PredictCompareScreen(
    viewModel: LotteryViewModel,
    uiState: LotteryUiState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val analysis = uiState.analysis

    LaunchedEffect(uiState.predictionFeedback) {
        uiState.predictionFeedback?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // 1. 彩种选择器
            LotteryTypeSelectorBar(
                currentType = uiState.currentType,
                onSelectType = { viewModel.switchLotteryType(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. 心水红球自选面板 (支持一键保存为下期预测)
            HeartBallSelectorCard(
                lotteryType = uiState.currentType,
                selectedBalls = uiState.myHeartBalls,
                onToggleBall = { viewModel.toggleHeartBall(it) },
                onSetBalls = { viewModel.setHeartBalls(it) },
                onClear = { viewModel.clearHeartBalls() },
                onSaveAsPrediction = {
                    val redList = uiState.myHeartBalls.toList()
                    val blueList = when (uiState.currentType) {
                        LotteryType.SSQ -> listOf((1..16).random())
                        LotteryType.DLT -> (1..12).shuffled().take(2)
                        LotteryType.KL8 -> emptyList()
                    }
                    viewModel.savePredictionRecord(
                        redBalls = redList,
                        blueBalls = blueList,
                        strategy = "心水自选(层级透视)"
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. 他人购买红球频次收集比对面板
            FrequencyCompareCard(
                inputText = uiState.collectedBallsText,
                onInputChange = { viewModel.updateCollectedBallsText(it) },
                analysisResult = uiState.frequencyResult,
                onClear = { viewModel.clearCollectedBalls() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. 心水红球在各遗漏层级的穿透分布卡片
            if (uiState.myHeartBalls.isNotEmpty() && analysis != null) {
                HeartBallStatsCard(
                    distributions = uiState.heartBallDistributions,
                    totalSelected = uiState.myHeartBalls.size
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 5. 核心联动比对卡片：下期遗漏分组预测 (心水标红 + 频次角标)
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RedPrimary)
                }
            } else if (analysis != null) {
                MissingLevelCard(
                    title = "⚡ 预测分组与心水比对透视",
                    subtitle = "🔴 金边红球为您的心水号码 | 🟣 紫色角标为他人高频出现号码",
                    levels = analysis.nextSimulatedGroups,
                    isPrediction = true,
                    initialExpanded = true,
                    heartBalls = uiState.myHeartBalls,
                    freqMap = uiState.frequencyResult.frequencyMap,
                    onBallClick = { viewModel.toggleHeartBall(it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 6. 预测选号记录与开奖自动比对卡片 (含删除和清空)
            PredictionRecordsCard(
                lotteryType = uiState.currentType,
                records = uiState.predictionRecords,
                onDelete = { viewModel.deletePredictionRecord(it) },
                onClearAll = { viewModel.clearPredictionRecords() }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * 心水球层级分布透视卡片
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeartBallStatsCard(
    distributions: List<HeartBallLevelDistribution>,
    totalSelected: Int
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
                        text = "🎯 心水号码层级穿透统计",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = "共 $totalSelected 码",
                    fontSize = 12.sp,
                    color = RedDark,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "评估选号结构是否科学：建议覆盖重号(0层)、热号(1-2层)、温号(3-8层)与冷号(9+层)",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 10.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                distributions.forEach { dist ->
                    val desc = when (dist.level) {
                        "0" -> "0层 (重号)"
                        "1" -> "1层"
                        "9+" -> "9+ (冷号)"
                        else -> "${dist.level}层"
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFEF2F2))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = desc,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = RedDark
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${dist.matchedBalls.size}码: " + dist.matchedBalls.joinToString(" ") { String.format("%02d", it) },
                            fontSize = 12.sp,
                            color = RedPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
