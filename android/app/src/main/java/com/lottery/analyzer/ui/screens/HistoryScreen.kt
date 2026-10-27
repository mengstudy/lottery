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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.lottery.analyzer.data.model.LotteryRecord
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.ui.components.BallStyle
import com.lottery.analyzer.ui.components.LotteryBall
import com.lottery.analyzer.ui.components.LotteryTypeSelectorBar
import com.lottery.analyzer.ui.theme.BluePrimary
import com.lottery.analyzer.ui.theme.RedDark
import com.lottery.analyzer.ui.theme.RedPrimary
import com.lottery.analyzer.ui.theme.TextPrimary
import com.lottery.analyzer.ui.theme.TextSecondary
import com.lottery.analyzer.ui.viewmodel.LotteryUiState
import com.lottery.analyzer.ui.viewmodel.LotteryViewModel

@Composable
fun HistoryScreen(
    viewModel: LotteryViewModel,
    uiState: LotteryUiState,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchKeyword by remember { mutableStateOf("") }

    val filteredRecords = remember(searchKeyword, uiState.recordsDesc) {
        if (searchKeyword.isBlank()) {
            uiState.recordsDesc
        } else {
            uiState.recordsDesc.filter { it.issue.contains(searchKeyword.trim()) || it.drawDate.contains(searchKeyword.trim()) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // 1. 彩种选择栏
        LotteryTypeSelectorBar(
            currentType = uiState.currentType,
            onSelectType = { viewModel.switchLotteryType(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. 搜索框与统计数量
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchKeyword,
                onValueChange = { searchKeyword = it },
                placeholder = { Text("搜索期号或日期 (如 2024...)", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "共 ${filteredRecords.size} 期",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. 历史记录卡片流
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = RedPrimary)
            }
        } else if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "未找到匹配的历史记录",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredRecords, key = { it.issue }) { record ->
                    HistoryItemCard(
                        type = uiState.currentType,
                        record = record,
                        onClick = {
                            viewModel.selectIssue(record.issue)
                            onNavigateToDetail(record.issue)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryItemCard(
    type: LotteryType,
    record: LotteryRecord,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 顶栏：期号、日期、查看分析指示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "第 ${record.issue} 期",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = RedDark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = record.drawDate,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "深度分析",
                        fontSize = 11.sp,
                        color = BluePrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = BluePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 号码球展示
            when (type) {
                LotteryType.SSQ -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        record.redBalls.forEach { ball ->
                            LotteryBall(
                                number = ball,
                                style = BallStyle.RED,
                                size = 32.dp,
                                fontSize = 13
                            )
                        }
                        record.blueBalls.firstOrNull()?.let { blue ->
                            LotteryBall(
                                number = blue,
                                style = BallStyle.BLUE,
                                size = 32.dp,
                                fontSize = 13
                            )
                        }
                    }
                }
                LotteryType.DLT -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        record.redBalls.forEach { ball ->
                            LotteryBall(
                                number = ball,
                                style = BallStyle.RED,
                                size = 32.dp,
                                fontSize = 13
                            )
                        }
                        record.blueBalls.forEach { blue ->
                            LotteryBall(
                                number = blue,
                                style = BallStyle.BLUE,
                                size = 32.dp,
                                fontSize = 13
                            )
                        }
                    }
                }
                LotteryType.KL8 -> {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        record.redBalls.forEach { ball ->
                            LotteryBall(
                                number = ball,
                                style = BallStyle.ORANGE,
                                size = 26.dp,
                                fontSize = 11
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 底部轻量指标
            val sum = record.redBalls.sum()
            val odd = record.redBalls.count { it % 2 != 0 }
            val even = record.redBalls.size - odd

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "和值: $sum",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Text(
                    text = "奇偶: $odd:$even",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                if (record.region.isNotBlank()) {
                    Text(
                        text = "出奖: ${record.region}",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
