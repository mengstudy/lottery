package com.lottery.analyzer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.data.model.TrendCell
import com.lottery.analyzer.data.model.TrendMatrixData
import com.lottery.analyzer.data.model.TrendRow
import com.lottery.analyzer.ui.components.LotteryTypeSelectorBar
import com.lottery.analyzer.ui.theme.BluePrimary
import com.lottery.analyzer.ui.theme.BorderLight
import com.lottery.analyzer.ui.theme.RedPrimary
import com.lottery.analyzer.ui.theme.TextPrimary
import com.lottery.analyzer.ui.theme.TextSecondary
import com.lottery.analyzer.ui.viewmodel.LotteryUiState
import com.lottery.analyzer.ui.viewmodel.LotteryViewModel

private val ISSUE_COL_WIDTH = 74.dp
private val CELL_SIZE = 32.dp
private val CELL_HEIGHT = 30.dp

// 红球各分位折线调色板 (红1..红6及备用)
private val RED_LINE_PALETTE = listOf(
    Color(0xFFDC2626), // 红1：深红/龙头
    Color(0xFFEA580C), // 红2：鲜橙
    Color(0xFF16A34A), // 红3：翡翠绿
    Color(0xFF0D9488), // 红4：青绿
    Color(0xFF9333EA), // 红5：亮紫
    Color(0xFFE11D48), // 红6：洋红/凤尾
    Color(0xFFD97706), // 备用7
    Color(0xFF4F46E5), // 备用8
    Color(0xFF059669), // 备用9
    Color(0xFF0284C7)  // 备用10
)

// 蓝球各分位折线调色板 (蓝1..蓝2)
private val BLUE_LINE_PALETTE = listOf(
    Color(0xFF2563EB), // 蓝1：湛蓝
    Color(0xFF0284C7)  // 蓝2：蔚蓝
)

/**
 * 交互式全景网格走势图界面
 */
@Composable
fun TrendChartScreen(
    viewModel: LotteryViewModel,
    uiState: LotteryUiState,
    modifier: Modifier = Modifier
) {
    val hScrollState = rememberScrollState()
    val vListState = rememberLazyListState()

    var showConnectLines by rememberSaveable { mutableStateOf(false) } // 连线走势开关

    // 监听彩种或期数变更时触发数据加载
    LaunchedEffect(uiState.currentType) {
        viewModel.loadTrendMatrixData()
    }

    val trendData = uiState.trendMatrixData
    val maxRed = uiState.currentType.maxRedNumber
    val maxBlue = uiState.currentType.maxBlueNumber

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // 1. 顶栏彩种选择
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            LotteryTypeSelectorBar(
                currentType = uiState.currentType,
                onSelectType = { viewModel.switchLotteryType(it) }
            )
        }

        // 2. 控制条 (期数选择 + 排序切换 + 走势连线)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 期数切换胶囊
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(30, 50, 100).forEach { limit ->
                    val isSelected = limit == uiState.trendMatrixLimit
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE2E8F0))
                            .clickable { viewModel.setTrendLimit(limit) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${limit}期",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else TextPrimary
                        )
                    }
                }
            }

            // 功能开关区
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 走势折线开关
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (showConnectLines) Color(0xFFE0E7FF) else Color(0xFFF1F5F9))
                        .border(1.dp, if (showConnectLines) Color(0xFF6366F1) else BorderLight, RoundedCornerShape(6.dp))
                        .clickable { showConnectLines = !showConnectLines }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = if (showConnectLines) Color(0xFF4F46E5) else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "折线",
                            fontSize = 11.sp,
                            color = if (showConnectLines) Color(0xFF4F46E5) else TextSecondary,
                            fontWeight = if (showConnectLines) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                // 排序切换 (最新在顶 / 最新在底)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF1F5F9))
                        .clickable { viewModel.toggleTrendOrder() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.trendDescOrder) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = if (uiState.trendDescOrder) "最新在上" else "最新在下",
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // 分位折线图例指示条 (折线开启时呈现)
        if (showConnectLines) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEEF2FF))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("分位折线:", fontSize = 10.sp, color = Color(0xFF4F46E5), fontWeight = FontWeight.Bold)
                val redCount = when (uiState.currentType) {
                    LotteryType.SSQ -> 6
                    LotteryType.DLT -> 5
                    LotteryType.KL8 -> 10
                }
                for (k in 0 until minOf(redCount, RED_LINE_PALETTE.size)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(RED_LINE_PALETTE[k]))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("红${k + 1}", fontSize = 9.sp, color = RED_LINE_PALETTE[k], fontWeight = FontWeight.Bold)
                    }
                }
                if (maxBlue > 0) {
                    for (b in 0 until minOf(uiState.currentType.blueCount, BLUE_LINE_PALETTE.size)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(BLUE_LINE_PALETTE[b]))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("蓝${b + 1}", fontSize = 9.sp, color = BLUE_LINE_PALETTE[b], fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 3. 核心走势图网格主体 (左侧固定期号 + 右侧横向滚动号码)
        if (uiState.isTrendLoading || trendData == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
            ) {
                // 表头 (期号 + 各球号 01..33/35/80)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(Color(0xFFF1F5F9)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧固定表头：期号
                    Box(
                        modifier = Modifier
                            .width(ISSUE_COL_WIDTH)
                            .fillMaxHeight()
                            .border(0.5.dp, BorderLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "期号",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    // 右侧滚动表头：红球与蓝球号
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(hScrollState)
                    ) {
                        (1..maxRed).forEach { num ->
                            Box(
                                modifier = Modifier
                                    .width(CELL_SIZE)
                                    .fillMaxHeight()
                                    .border(0.5.dp, BorderLight)
                                    .background(Color(0xFFFEF2F2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = String.format("%02d", num),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RedPrimary
                                )
                            }
                        }

                        if (maxBlue > 0) {
                            (1..maxBlue).forEach { num ->
                                Box(
                                    modifier = Modifier
                                        .width(CELL_SIZE)
                                        .fillMaxHeight()
                                    .border(0.5.dp, BorderLight)
                                    .background(Color(0xFFF0F9FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = String.format("%02d", num),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BluePrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // 表体行列表 (LazyColumn)
                LazyColumn(
                    state = vListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    itemsIndexed(trendData.rows, key = { _, row -> row.issue }) { index, row ->
                        val isEvenRow = index % 2 == 0
                        val rowBg = if (isEvenRow) Color.White else Color(0xFFFAFAFA)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(CELL_HEIGHT)
                                .background(rowBg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 固定列：期号
                            Box(
                                modifier = Modifier
                                    .width(ISSUE_COL_WIDTH)
                                    .fillMaxHeight()
                                    .border(0.5.dp, BorderLight)
                                    .background(rowBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = row.issue.takeLast(4) + "期",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                            }

                            // 滚动列：单元格集合与分位折线层
                            val prevRow = if (index > 0) trendData.rows[index - 1] else null
                            val totalGridWidth = CELL_SIZE * (maxRed + maxBlue)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .horizontalScroll(hScrollState)
                            ) {
                                // 1. 底层绘制跨期红蓝球分位折线
                                if (showConnectLines && prevRow != null) {
                                    Canvas(
                                        modifier = Modifier
                                            .width(totalGridWidth)
                                            .fillMaxHeight()
                                    ) {
                                        val cellWidthPx = CELL_SIZE.toPx()
                                        val cellHeightPx = CELL_HEIGHT.toPx()
                                        val startY = cellHeightPx / 2f
                                        val endY = -cellHeightPx / 2f // 上一行中心 Y 坐标

                                        // 绘制红球分位折线 (第 1 位..第 K 位)
                                        val minRedCount = minOf(row.hitRedNumbers.size, prevRow.hitRedNumbers.size)
                                        for (k in 0 until minRedCount) {
                                            val currBall = row.hitRedNumbers[k]
                                            val prevBall = prevRow.hitRedNumbers[k]
                                            val startX = (currBall - 1) * cellWidthPx + cellWidthPx / 2f
                                            val endX = (prevBall - 1) * cellWidthPx + cellWidthPx / 2f
                                            val lineColor = RED_LINE_PALETTE[k % RED_LINE_PALETTE.size]

                                            drawLine(
                                                color = lineColor,
                                                start = Offset(startX, startY),
                                                end = Offset(endX, endY),
                                                strokeWidth = 2.dp.toPx(),
                                                cap = StrokeCap.Round
                                            )
                                        }

                                        // 绘制蓝球分位折线 (第 1 位..第 B 位)
                                        val minBlueCount = minOf(row.hitBlueNumbers.size, prevRow.hitBlueNumbers.size)
                                        val redOffsetPx = maxRed * cellWidthPx
                                        for (b in 0 until minBlueCount) {
                                            val currBall = row.hitBlueNumbers[b]
                                            val prevBall = prevRow.hitBlueNumbers[b]
                                            val startX = redOffsetPx + (currBall - 1) * cellWidthPx + cellWidthPx / 2f
                                            val endX = redOffsetPx + (prevBall - 1) * cellWidthPx + cellWidthPx / 2f
                                            val lineColor = BLUE_LINE_PALETTE[b % BLUE_LINE_PALETTE.size]

                                            drawLine(
                                                color = lineColor,
                                                start = Offset(startX, startY),
                                                end = Offset(endX, endY),
                                                strokeWidth = 2.dp.toPx(),
                                                cap = StrokeCap.Round
                                            )
                                        }
                                    }
                                }

                                // 2. 上层实心彩球与遗漏数字网格
                                Row(modifier = Modifier.fillMaxHeight()) {
                                    // 红球网格
                                    row.redCells.forEach { cell ->
                                        TrendCellItem(cell = cell, isBlue = false)
                                    }
                                    // 蓝球网格
                                    row.blueCells.forEach { cell ->
                                        TrendCellItem(cell = cell, isBlue = true)
                                    }
                                }
                            }
                        }
                    }
                }

                Divider(color = BorderLight, thickness = 1.dp)

                // 4. 底部固定指标统计栏 (出现次数 / 平均遗漏 / 最大遗漏 / 当前遗漏)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC))
                ) {
                    listOf(
                        "出现次数" to { s: com.lottery.analyzer.data.model.NumberTrendSummary -> s.hitCount.toString() },
                        "平均遗漏" to { s: com.lottery.analyzer.data.model.NumberTrendSummary -> s.avgMissing.toString() },
                        "最大遗漏" to { s: com.lottery.analyzer.data.model.NumberTrendSummary -> s.maxMissing.toString() },
                        "当前遗漏" to { s: com.lottery.analyzer.data.model.NumberTrendSummary -> s.currentMissing.toString() }
                    ).forEachIndexed { sIndex, (label, valueExtractor) ->
                        val isSummaryEven = sIndex % 2 == 0
                        val sBg = if (isSummaryEven) Color(0xFFF1F5F9) else Color(0xFFF8FAFC)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .background(sBg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 左侧标签
                            Box(
                                modifier = Modifier
                                    .width(ISSUE_COL_WIDTH)
                                    .fillMaxHeight()
                                    .border(0.5.dp, BorderLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            // 右侧数值
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .horizontalScroll(hScrollState)
                            ) {
                                trendData.redSummaries.forEach { summary ->
                                    Box(
                                        modifier = Modifier
                                            .width(CELL_SIZE)
                                            .fillMaxHeight()
                                            .border(0.5.dp, BorderLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = valueExtractor(summary),
                                            fontSize = 9.sp,
                                            fontWeight = if (label == "出现次数" && summary.hitCount > 5) FontWeight.Bold else FontWeight.Normal,
                                            color = if (label == "出现次数" && summary.hitCount > 5) RedPrimary else TextSecondary
                                        )
                                    }
                                }

                                if (maxBlue > 0) {
                                    trendData.blueSummaries.forEach { summary ->
                                        Box(
                                            modifier = Modifier
                                                .width(CELL_SIZE)
                                                .fillMaxHeight()
                                                .border(0.5.dp, BorderLight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = valueExtractor(summary),
                                                fontSize = 9.sp,
                                                fontWeight = if (label == "出现次数" && summary.hitCount > 3) FontWeight.Bold else FontWeight.Normal,
                                                color = if (label == "出现次数" && summary.hitCount > 3) BluePrimary else TextSecondary
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

/**
 * 走势图单网格单元
 */
@Composable
private fun TrendCellItem(
    cell: TrendCell,
    isBlue: Boolean
) {
    Box(
        modifier = Modifier
            .width(CELL_SIZE)
            .fillMaxHeight()
            .border(0.5.dp, BorderLight),
        contentAlignment = Alignment.Center
    ) {
        if (cell.isHit) {
            // 开出号码：实心拟物圆球
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isBlue) BluePrimary else RedPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format("%02d", cell.number),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        } else {
            // 未开出：显示遗漏数
            val isDeepCold = cell.missing >= 10
            Text(
                text = cell.missing.toString(),
                fontSize = 10.sp,
                fontWeight = if (isDeepCold) FontWeight.Bold else FontWeight.Normal,
                color = if (isDeepCold) Color(0xFF64748B) else Color(0xFFCBD5E1)
            )
        }
    }
}
