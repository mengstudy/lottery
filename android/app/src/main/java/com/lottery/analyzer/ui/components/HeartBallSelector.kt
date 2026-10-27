package com.lottery.analyzer.ui.components

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
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
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.ui.theme.RedDark
import com.lottery.analyzer.ui.theme.RedPrimary
import com.lottery.analyzer.ui.theme.TextPrimary
import com.lottery.analyzer.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeartBallSelectorCard(
    lotteryType: LotteryType,
    selectedBalls: Set<Int>,
    onToggleBall: (Int) -> Unit,
    onSetBalls: (Set<Int>) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var manualInput by remember { mutableStateOf("") }

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
                        text = "❤️ 心水红球自选面板",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = "已选 ${selectedBalls.size} 码",
                    fontSize = 12.sp,
                    color = RedPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "点击下方号码点选心水球，选中的号码将在下方预测分组中自动高亮标红并统计层级分布",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 10.dp)
            )

            // 号码球网格
            val maxNumber = lotteryType.maxRedNumber
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (1..maxNumber).forEach { num ->
                    val isSelected = num in selectedBalls
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) RedPrimary else Color(0xFFF8FAFC))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFFFFD700) else Color(0xFFCBD5E1),
                                shape = CircleShape
                            )
                            .clickable { onToggleBall(num) },
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

            Spacer(modifier = Modifier.height(10.dp))

            // 手动文本输入 / 快捷操作
            OutlinedTextField(
                value = manualInput,
                onValueChange = {
                    manualInput = it
                    // 解析数字
                    val numbers = Regex("\\d+").findAll(it).mapNotNull { m ->
                        val n = m.value.toIntOrNull()
                        if (n != null && n in 1..maxNumber) n else null
                    }.toSet()
                    if (numbers.isNotEmpty() || it.isBlank()) {
                        onSetBalls(numbers)
                    }
                },
                placeholder = { Text("支持手动输入或粘贴号码，以空格/逗号分隔...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 操作按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        manualInput = ""
                        onClear()
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清空自选", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        // 随机机选注数
                        val pool = (1..maxNumber).shuffled()
                        val randomPick = pool.take(lotteryType.redCount).toSet()
                        onSetBalls(randomPick)
                        manualInput = randomPick.sorted().joinToString(" ") { String.format("%02d", it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("机选一组", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
