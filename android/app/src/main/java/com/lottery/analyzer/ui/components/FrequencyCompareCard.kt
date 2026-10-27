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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottery.analyzer.data.model.FrequencyAnalysisResult
import com.lottery.analyzer.ui.theme.TextPrimary
import com.lottery.analyzer.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FrequencyCompareCard(
    inputText: String,
    onInputChange: (String) -> Unit,
    analysisResult: FrequencyAnalysisResult,
    onClear: () -> Unit,
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
                            .background(Color(0xFF7C3AED))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "📋 收集他人红球 (频次比对)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                if (analysisResult.validGroupsCount > 0) {
                    Text(
                        text = "已解析 ${analysisResult.validGroupsCount} 组",
                        fontSize = 12.sp,
                        color = Color(0xFF7C3AED),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "粘贴他人购买的号码（每行连续数字按2位分割，支持多行），系统将统计频次并在预测球旁展示角标",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 10.dp)
            )

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        "粘贴或输入多行号码，如:\n01 05 12 18 25 30\n050812192631",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            )

            if (analysisResult.sortedByFreq.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "他人最高频次号码 TOP 10：",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    analysisResult.sortedByFreq.take(10).forEach { (num, freq) ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF3E8FF))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format("%02d", num),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6B21A8)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "x$freq",
                                fontSize = 10.sp,
                                color = Color(0xFF9333EA),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onClear,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("清空收集号码", fontSize = 12.sp)
            }
        }
    }
}
