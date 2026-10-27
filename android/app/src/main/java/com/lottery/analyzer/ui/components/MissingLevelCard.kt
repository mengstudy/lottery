package com.lottery.analyzer.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.lottery.analyzer.data.model.MissingGroupLevel
import com.lottery.analyzer.ui.theme.RedPrimary
import com.lottery.analyzer.ui.theme.TextPrimary
import com.lottery.analyzer.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MissingLevelCard(
    title: String,
    subtitle: String,
    levels: List<MissingGroupLevel>,
    modifier: Modifier = Modifier,
    initialExpanded: Boolean = true,
    isPrediction: Boolean = false,
    heartBalls: Set<Int> = emptySet(),
    freqMap: Map<Int, Int> = emptyMap(),
    onBallClick: ((Int) -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(initialExpanded) }

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
            // 卡片标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp, 16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isPrediction) Color(0xFF2563EB) else RedPrimary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 14.dp, top = 2.dp)
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    levels.forEach { levelData ->
                        SingleLevelRow(
                            levelData = levelData,
                            isPrediction = isPrediction,
                            heartBalls = heartBalls,
                            freqMap = freqMap,
                            onBallClick = onBallClick
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SingleLevelRow(
    levelData: MissingGroupLevel,
    isPrediction: Boolean,
    heartBalls: Set<Int>,
    freqMap: Map<Int, Int>,
    onBallClick: ((Int) -> Unit)?
) {
    val levelDesc = when (levelData.level) {
        "0" -> "0层 (重号)"
        "1" -> "1层 (斜连)"
        "9+" -> "9+ (冷号)"
        else -> "${levelData.level}层"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8FAFC))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 层级标签
        Column(
            modifier = Modifier.width(72.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = levelDesc,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "共 ${levelData.allNumbers.size} 个",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }

        // 号码流
        if (levelData.allNumbers.isEmpty()) {
            Text(
                text = "暂无号码",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(start = 8.dp)
            )
        } else {
            FlowRow(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val hitSet = levelData.hitNumbers.toSet()
                levelData.allNumbers.forEach { num ->
                    val isHit = !isPrediction && num in hitSet
                    val isHeart = num in heartBalls
                    val freq = freqMap[num] ?: 0

                    val ballBg = when {
                        isHeart -> RedPrimary
                        isHit -> RedPrimary
                        else -> Color.White
                    }
                    val ballText = when {
                        isHeart || isHit -> Color.White
                        else -> Color(0xFF334155)
                    }

                    Box(
                        modifier = Modifier
                            .clickable(enabled = onBallClick != null) { onBallClick?.invoke(num) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(ballBg)
                                .border(
                                    width = if (isHeart) 2.dp else 1.dp,
                                    color = if (isHeart) Color(0xFFFFD700) else if (isHit) RedPrimary else Color(0xFFCBD5E1),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = String.format("%02d", num),
                                fontSize = 11.sp,
                                fontWeight = if (isHit || isHeart) FontWeight.Bold else FontWeight.Medium,
                                color = ballText
                            )
                        }

                        // 他人购买频次角标
                        if (freq > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-4).dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF7C3AED))
                                    .padding(horizontal = 3.dp, vertical = 0.5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "x$freq",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
