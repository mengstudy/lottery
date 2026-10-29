package com.lottery.analyzer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.data.model.PredictionVerificationResult
import com.lottery.analyzer.ui.theme.BluePrimary
import com.lottery.analyzer.ui.theme.RedDark
import com.lottery.analyzer.ui.theme.RedPrimary
import com.lottery.analyzer.ui.theme.TextPrimary
import com.lottery.analyzer.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PredictionRecordsCard(
    lotteryType: LotteryType,
    records: List<PredictionVerificationResult>,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    initialExpanded: Boolean = true
) {
    var isExpanded by remember { mutableStateOf(initialExpanded) }
    var recordToDelete by remember { mutableStateOf<Long?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // 顶栏标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp, 16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF059669))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "📋 预测选号记录与开奖比对 (${records.size}条)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (records.isNotEmpty() && isExpanded) {
                        Text(
                            text = "清空",
                            fontSize = 11.sp,
                            color = Color(0xFFEF4444),
                            modifier = Modifier
                                .clickable { showClearConfirm = true }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                }
            }

            Text(
                text = "记录推荐选号及策略，下一期开奖数据更新时将自动比对命中情况与中奖等级",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 8.dp)
            )

            AnimatedVisibility(visible = isExpanded) {
                if (records.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无预测记录。在上方自选心水或智能选号后，点击「记录为下期预测」即可在此比对！",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        records.forEach { result ->
                            SinglePredictionItem(
                                result = result,
                                onDeleteClick = { recordToDelete = result.record.id }
                            )
                        }
                    }
                }
            }
        }
    }

    // 单条删除确认
    if (recordToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("确认删除记录？", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text("删除后不可恢复。", fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        recordToDelete?.let { onDelete(it) }
                        recordToDelete = null
                    }
                ) {
                    Text("删除", color = RedPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 清空确认
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空全部记录？", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text("将清空当前彩种所有的预测与比对记录。", fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showClearConfirm = false
                    }
                ) {
                    Text("清空", color = RedPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SinglePredictionItem(
    result: PredictionVerificationResult,
    onDeleteClick: () -> Unit
) {
    val record = result.record
    val isDrawn = result.isDrawn
    val hitRedSet = result.hitRedBalls.toSet()
    val hitBlueSet = result.hitBlueBalls.toSet()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (result.isWon) Color(0xFFFEF2F2) else Color(0xFFF8FAFC))
            .padding(10.dp)
    ) {
        // 第一行：目标期号、策略标签、删除按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "目标第 ${record.targetIssue} 期",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = RedDark
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = record.strategy,
                    fontSize = 10.sp,
                    color = BluePrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFEFF6FF))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.createdAt,
                    fontSize = 10.sp,
                    color = TextSecondary
                )
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "删除",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 第二行：预测号码展示（命中的球带金色高亮边框和星号标识）
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            record.redBalls.forEach { ball ->
                val isHit = isDrawn && ball in hitRedSet
                LotteryBall(
                    number = ball,
                    style = BallStyle.RED,
                    size = 30.dp,
                    fontSize = 12,
                    isHeartSelected = isHit, // 命中球金边高亮
                    badgeText = if (isHit) "✓" else null,
                    badgeBgColor = Color(0xFF16A34A)
                )
            }

            if (record.blueBalls.isNotEmpty()) {
                Text(
                    text = "+",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
                )
                record.blueBalls.forEach { blue ->
                    val isHit = isDrawn && blue in hitBlueSet
                    LotteryBall(
                        number = blue,
                        style = BallStyle.BLUE,
                        size = 30.dp,
                        fontSize = 12,
                        isHeartSelected = isHit,
                        badgeText = if (isHit) "✓" else null,
                        badgeBgColor = Color(0xFF16A34A)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 第三行：核验比对结果状态
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = result.resultSummary,
                fontSize = 12.sp,
                fontWeight = if (result.isWon) FontWeight.Bold else FontWeight.Medium,
                color = if (result.isWon) RedPrimary else if (!isDrawn) Color(0xFFD97706) else TextSecondary
            )

            if (isDrawn && result.actualRecord != null) {
                Text(
                    text = "开奖: ${result.actualRecord.redBalls.joinToString(" ")}" +
                            if (result.actualRecord.blueBalls.isNotEmpty()) " + ${result.actualRecord.blueBalls.joinToString(" ")}" else "",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }
    }
}
