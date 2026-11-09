package com.lottery.analyzer.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottery.analyzer.data.model.GeneratedLotteryBet
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.ui.components.AiLotteryWorkstationCard
import com.lottery.analyzer.ui.components.BallStyle
import com.lottery.analyzer.ui.components.LotteryBall
import com.lottery.analyzer.ui.components.LotteryTypeSelectorBar
import com.lottery.analyzer.ui.components.PredictionRecordsCard
import com.lottery.analyzer.ui.components.ShrinkFilterCard
import com.lottery.analyzer.ui.theme.BluePrimary
import com.lottery.analyzer.ui.theme.RedDark
import com.lottery.analyzer.ui.theme.RedPrimary
import com.lottery.analyzer.ui.theme.TextPrimary
import com.lottery.analyzer.ui.theme.TextSecondary
import com.lottery.analyzer.ui.viewmodel.LotteryUiState
import com.lottery.analyzer.ui.viewmodel.LotteryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsScreen(
    viewModel: LotteryViewModel,
    uiState: LotteryUiState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedNumber by rememberSaveable { mutableIntStateOf(1) }
    var trendData by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }

    // 智能选号生成的多注列表
    var generatedBets by remember { mutableStateOf<List<GeneratedLotteryBet>>(emptyList()) }
    // 工具箱子功能选项卡 (0: 选号推演, 1: 旋转矩阵与复式缩水, 2: 全景网格走势图)
    var currentToolTab by rememberSaveable { mutableIntStateOf(0) }

    // 重新获取走势
    LaunchedEffect(selectedNumber, uiState.currentType) {
        trendData = viewModel.getNumberTrend(selectedNumber)
    }

    LaunchedEffect(uiState.predictionFeedbackId) {
        uiState.predictionFeedback?.let {
            viewModel.consumePredictionFeedback(uiState.predictionFeedbackId)
            snackbarHostState.showSnackbar(it)
        }
    }

    fun generateSingleBet(type: LotteryType) = com.lottery.analyzer.data.engine.LotteryBetGenerator.generate(type, uiState.analysis)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部三段式功能切换导航栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val toolTabs = listOf("🎯 选号推演", "⚙️ 旋转矩阵与缩水", "📈 全景网格走势")
                toolTabs.forEachIndexed { index, title ->
                    val selected = currentToolTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) RedPrimary else Color(0xFFF1F5F9))
                            .clickable { currentToolTab = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else TextPrimary
                        )
                    }
                }
            }

            when (currentToolTab) {
                0 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(14.dp)
                    ) {
            // 1. 彩种选择器
            LotteryTypeSelectorBar(
                currentType = uiState.currentType,
                onSelectType = {
                    viewModel.switchLotteryType(it)
                    generatedBets = emptyList() // 切换彩种重置
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. 智能冷温热过滤组号模拟器（支持红球+蓝球完整随机）
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
                                text = "🎲 遗漏冷热过滤组号模拟",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Text(
                            text = when (uiState.currentType) {
                                LotteryType.SSQ -> "含 1 码蓝球"
                                LotteryType.DLT -> "含 2 码后区蓝球"
                                LotteryType.KL8 -> "选十玩法推荐"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BluePrimary
                        )
                    }

                    Text(
                        text = "依据下期遗漏分组算法，按黄金比例 (热+温+冷) 推荐红球并随机蓝球，支持记录到下期进行开奖比对",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 12.dp)
                    )

                    // 动作按钮栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                generatedBets = listOf(generateSingleBet(uiState.currentType))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("生成 1 注", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                val list = mutableListOf<GeneratedLotteryBet>()
                                repeat(5) {
                                    list.add(generateSingleBet(uiState.currentType))
                                }
                                generatedBets = list
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("生成 5 注", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // 批量保存全部按钮
                    if (generatedBets.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                generatedBets.forEachIndexed { i, b ->
                                    viewModel.savePredictionRecord(
                                        redBalls = b.redBalls,
                                        blueBalls = b.blueBalls,
                                        strategy = "黄金比例智能模拟(第${i + 1}注)",
                                        blueSource = com.lottery.analyzer.data.model.BlueSource.GENERATED_CONFIRMED
                                    )
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("💾 一键记录全部 ${generatedBets.size} 注为下期预测", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 展示生成的注数列表
                    if (generatedBets.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))

                        generatedBets.forEachIndexed { index, bet ->
                            SingleBetResultCard(
                                index = index + 1,
                                bet = bet,
                                lotteryType = uiState.currentType,
                                onSyncToHeartBalls = {
                                    viewModel.setHeartBalls(bet.redBalls.toSet())
                                    scope.launch {
                                        snackbarHostState.showSnackbar("已将第 ${index + 1} 注红球设为您的心水红球！")
                                    }
                                },
                                onSavePrediction = {
                                    viewModel.savePredictionRecord(
                                        redBalls = bet.redBalls,
                                        blueBalls = bet.blueBalls,
                                        strategy = "遗漏黄金比例智能模拟",
                                        blueSource = com.lottery.analyzer.data.model.BlueSource.GENERATED_CONFIRMED
                                    )
                                }
                            )
                            if (index < generatedBets.size - 1) {
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            com.lottery.analyzer.ui.components.FrequencyStrategyCard(
                type = uiState.currentType,
                records = uiState.recordsDesc,
                dataLoading = uiState.isLoading,
                onSave = { output, index ->
                    if (output.stats.type == uiState.currentType) {
                        val bet = output.bets[index]
                        viewModel.savePredictionRecord(
                            redBalls = bet.redBalls,
                            blueBalls = bet.blueBalls,
                            strategy = output.description(index),
                            blueSource = com.lottery.analyzer.data.model.BlueSource.GENERATED_CONFIRMED
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.height(14.dp))

            // 3. 🤖 AI 大模型智能推演工作台
            AiLotteryWorkstationCard(
                lotteryType = uiState.currentType,
                targetIssue = uiState.nextIssueTarget,
                aiConfig = uiState.aiConfig,
                isLoading = uiState.isAiLoading,
                aiResult = uiState.aiResult,
                aiError = uiState.aiError,
                heartBallCount = uiState.myHeartBalls.size,
                otherBallSet = uiState.frequencyResult.frequencyMap.keys,
                otherBets = uiState.frequencyResult.parsedBets,
                kl8PlayType = uiState.kl8PlayType,
                onKl8PlayTypeChange = { viewModel.setKl8PlayType(it) },
                onOpenSettings = { viewModel.setAiSettingsDialogVisible(true) },
                onRunAnalysis = { mode, count -> viewModel.runAiAnalysis(mode, count) },
                onSaveBetToPrediction = { index -> viewModel.saveAiBetToPrediction(index) },
                onApplyBetToHeart = { index -> viewModel.applyAiBetToHeartBalls(index) },
                onClearResult = { viewModel.clearAiResult() },
                onViewLogs = { viewModel.setLogDialogVisible(true) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4. 预测选号记录与开奖自动比对卡片 (含删除和清空)
            PredictionRecordsCard(
                lotteryType = uiState.currentType,
                records = uiState.predictionRecords,
                totalCount = uiState.predictionTotalCount,
                currentPage = uiState.predictionCurrentPage,
                pageSize = uiState.predictionPageSize,
                onPageChange = { page -> viewModel.loadPredictions(page = page) },
                onDelete = { viewModel.deletePredictionRecord(it) },
                onClearAll = { viewModel.clearPredictionRecords() }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 5. 号码遗漏走势图卡片
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
                                .background(BluePrimary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "📈 单号遗漏走势分析",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "点击下方号码，查看其最近 20 期遗漏值起伏曲线",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 10.dp)
                    )

                    // 号码选择横向球
                    val maxNumber = if (uiState.currentType == LotteryType.KL8) 40 else uiState.currentType.maxRedNumber
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (1..minOf(maxNumber, 35)).forEach { num ->
                            val isSelected = num == selectedNumber
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) RedPrimary else Color(0xFFF1F5F9))
                                    .clickable { selectedNumber = num },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = String.format("%02d", num),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else TextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 折线图绘制
                    if (trendData.isNotEmpty()) {
                        TrendLineChart(
                            data = trendData.takeLast(16),
                            number = selectedNumber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                1 -> {
                    // 旋转矩阵与复式缩水
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    ) {
                        LotteryTypeSelectorBar(
                            currentType = uiState.currentType,
                            onSelectType = { viewModel.switchLotteryType(it) }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        ShrinkFilterCard(
                            lotteryType = uiState.currentType,
                            selectedBalls = uiState.shrinkSelectedBalls,
                            heartBalls = uiState.myHeartBalls,
                            config = uiState.shrinkConfig,
                            isShrinking = uiState.isShrinking,
                            shrinkResult = uiState.shrinkResult,
                            onToggleBall = { viewModel.toggleShrinkBall(it) },
                            onSetBalls = { viewModel.setShrinkBalls(it) },
                            onClearBalls = { viewModel.clearShrinkBalls() },
                            onImportHeartBalls = { viewModel.importHeartToShrink() },
                            onUpdateConfig = { viewModel.updateShrinkConfig(it) },
                            onExecuteShrink = { viewModel.runShrinkCalculation() },
                            onSaveBetToPrediction = { viewModel.saveShrinkBetToPrediction(it) },
                            onApplyBetToHeart = { viewModel.setHeartBalls(it.toSet()) }
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                2 -> {
                    // 交互式全景网格走势图
                    TrendChartScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * 单注生成结果展示组件
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SingleBetResultCard(
    index: Int,
    bet: GeneratedLotteryBet,
    lotteryType: LotteryType,
    onSyncToHeartBalls: () -> Unit,
    onSavePrediction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8FAFC))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "第 $index 注推荐",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = RedDark
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                val sum = bet.redBalls.sum()
                val odd = bet.redBalls.count { it % 2 != 0 }
                val even = bet.redBalls.size - odd
                Text(
                    text = "和值:$sum | 奇偶:$odd:$even",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "设为心水",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = RedPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFEF2F2))
                        .clickable { onSyncToHeartBalls() }
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "记录预测",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF059669),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFECFDF5))
                        .clickable { onSavePrediction() }
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 彩球排布 (红球 + 蓝球)
        when (lotteryType) {
            LotteryType.SSQ -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bet.redBalls.forEach { ball ->
                        LotteryBall(
                            number = ball,
                            style = BallStyle.RED,
                            size = 34.dp,
                            fontSize = 13
                        )
                    }
                    Text(
                        text = "+",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    bet.blueBalls.firstOrNull()?.let { blue ->
                        LotteryBall(
                            number = blue,
                            style = BallStyle.BLUE,
                            size = 34.dp,
                            fontSize = 13
                        )
                    }
                }
            }
            LotteryType.DLT -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bet.redBalls.forEach { ball ->
                        LotteryBall(
                            number = ball,
                            style = BallStyle.RED,
                            size = 34.dp,
                            fontSize = 13
                        )
                    }
                    Text(
                        text = "+",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    bet.blueBalls.forEach { blue ->
                        LotteryBall(
                            number = blue,
                            style = BallStyle.BLUE,
                            size = 34.dp,
                            fontSize = 13
                        )
                    }
                }
            }
            LotteryType.KL8 -> {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    bet.redBalls.forEach { ball ->
                        LotteryBall(
                            number = ball,
                            style = BallStyle.ORANGE,
                            size = 28.dp,
                            fontSize = 11
                        )
                    }
                }
            }
        }
    }
}

/**
 * 手机端轻量级遗漏走势折线图
 */
@Composable
private fun TrendLineChart(
    data: List<Pair<String, Int>>,
    number: Int
) {
    val maxVal = maxOf(data.maxOfOrNull { it.second } ?: 1, 10)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8FAFC))
            .padding(12.dp)
    ) {
        Text(
            text = "号码 ${String.format("%02d", number)} 近期各期遗漏期数 (值越小说明开出越频繁):",
            fontSize = 12.sp,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val width = size.width
            val height = size.height
            val stepX = width / (data.size - 1).coerceAtLeast(1)
            val path = Path()
            val points = mutableListOf<Offset>()

            data.forEachIndexed { i, pair ->
                val x = i * stepX
                val y = height - (pair.second.toFloat() / maxVal * (height - 30f)) - 15f
                points.add(Offset(x, y))
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            // 绘制平滑线条
            drawPath(
                path = path,
                color = RedPrimary,
                style = Stroke(width = 3.dp.toPx())
            )

            // 绘制关键节点圆圈
            points.forEach { pt ->
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = RedDark,
                    radius = 3.dp.toPx(),
                    center = pt
                )
            }
        }

        // 底部期号标签（简版展示首尾）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "第 ${data.firstOrNull()?.first ?: ""} 期", fontSize = 10.sp, color = TextSecondary)
            Text(text = "第 ${data.lastOrNull()?.first ?: ""} 期 (最新)", fontSize = 10.sp, color = RedPrimary, fontWeight = FontWeight.Bold)
        }
    }
}
