package com.lottery.analyzer.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottery.analyzer.ui.theme.ColdBlue
import com.lottery.analyzer.ui.theme.HotRed
import com.lottery.analyzer.ui.theme.TextPrimary
import com.lottery.analyzer.ui.theme.TextSecondary
import com.lottery.analyzer.ui.theme.WarmOrange

@Composable
fun ColdHotCard(
    hotNumbers: List<Int>,
    warmNumbers: List<Int>,
    coldNumbers: List<Int>,
    modifier: Modifier = Modifier
) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp, 16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(HotRed)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "全盘冷热温号分布",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Text(
                text = "热号 (遗漏≤4期) / 温号 (遗漏5-9期) / 冷号 (遗漏≥10期)",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 12.dp)
            )

            // 热号区
            ColdHotSection(
                label = "热号 (${hotNumbers.size}个)",
                numbers = hotNumbers,
                themeColor = HotRed,
                bgColor = Color(0xFFFEF2F2)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 温号区
            ColdHotSection(
                label = "温号 (${warmNumbers.size}个)",
                numbers = warmNumbers,
                themeColor = WarmOrange,
                bgColor = Color(0xFFFFF7ED)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 冷号区
            ColdHotSection(
                label = "冷号 (${coldNumbers.size}个)",
                numbers = coldNumbers,
                themeColor = ColdBlue,
                bgColor = Color(0xFFF0F9FF)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColdHotSection(
    label: String,
    numbers: List<Int>,
    themeColor: Color,
    bgColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .padding(10.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = themeColor
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (numbers.isEmpty()) {
            Text(
                text = "无",
                fontSize = 12.sp,
                color = TextSecondary
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                numbers.forEach { num ->
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format("%02d", num),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = themeColor
                        )
                    }
                }
            }
        }
    }
}
