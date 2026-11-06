package com.lottery.analyzer.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.data.model.ShrinkFilterConfig
import com.lottery.analyzer.data.model.ShrinkResult
import com.lottery.analyzer.data.model.WheelGuaranteeMode
import com.lottery.analyzer.ui.theme.BorderLight
import com.lottery.analyzer.ui.theme.GreenLight
import com.lottery.analyzer.ui.theme.GreenPrimary
import com.lottery.analyzer.ui.theme.OrangeLight
import com.lottery.analyzer.ui.theme.OrangePrimary
import com.lottery.analyzer.ui.theme.PurpleLight
import com.lottery.analyzer.ui.theme.PurplePrimary
import com.lottery.analyzer.ui.theme.RedDark
import com.lottery.analyzer.ui.theme.RedLight
import com.lottery.analyzer.ui.theme.RedPrimary
import com.lottery.analyzer.ui.theme.SurfaceLight
import com.lottery.analyzer.ui.theme.TextPrimary
import com.lottery.analyzer.ui.theme.TextSecondary

/**
 * 专业复式缩水过滤与旋转矩阵工具卡片
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShrinkFilterCard(
    lotteryType: LotteryType,
    selectedBalls: Set<Int>,
    heartBalls: Set<Int>,
    config: ShrinkFilterConfig,
    isShrinking: Boolean,
    shrinkResult: ShrinkResult?,
    onToggleBall: (Int) -> Unit,
    onSetBalls: (Set<Int>) -> Unit,
    onClearBalls: () -> Unit,
    onImportHeartBalls: () -> Unit,
    onUpdateConfig: (ShrinkFilterConfig) -> Unit,
    onExecuteShrink: () -> Unit,
    onSaveBetToPrediction: (List<Int>) -> Unit,
    onApplyBetToHeart: (List<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isFilterExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. 顶部标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0F2FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterAlt,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "复式缩水与旋转矩阵",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "组合数学保奖 + 和值/奇偶/连号多维过滤",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Text(
                    text = "选号底池: ${selectedBalls.size} 码",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedBalls.size >= lotteryType.redCount) Color(0xFF0284C7) else RedPrimary
                )
            }

            // 2. 号码球选择大底面板
            val maxNumber = lotteryType.maxRedNumber
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (1..maxNumber).forEach { num ->
                    val isSelected = num in selectedBalls
                    val isHeart = num in heartBalls
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color(0xFF0284C7) else Color(0xFFF8FAFC))
                            .border(
                                width = if (isHeart) 1.5.dp else 1.dp,
                                color = if (isHeart) Color(0xFFFFD700) else if (isSelected) Color(0xFF0284C7) else Color(0xFFCBD5E1),
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

            // 快捷底池操作按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onClearBalls,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清空底池", fontSize = 11.sp)
                }

                if (heartBalls.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onImportHeartBalls,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(imageVector = Icons.Default.Favorite, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("导入心水(${heartBalls.size}码)", fontSize = 11.sp, color = RedPrimary)
                    }
                }

                Button(
                    onClick = {
                        val pickCount = when (lotteryType) {
                            LotteryType.SSQ -> 10
                            LotteryType.DLT -> 9
                            LotteryType.KL8 -> 14
                        }
                        val pool = (1..maxNumber).shuffled().take(pickCount).toSet()
                        onSetBalls(pool)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("机选大底", fontSize = 11.sp)
                }
            }

            Divider(color = BorderLight, thickness = 0.8.dp)

            // 3. 旋转矩阵保奖模式选择
            Text(
                text = "保奖旋转矩阵模式：",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WheelGuaranteeMode.values().forEach { mode ->
                    val isModeSelected = mode == config.wheelMode
                    val borderColor = if (isModeSelected) Color(0xFF0284C7) else BorderLight
                    val bgColor = if (isModeSelected) Color(0xFFF0F9FF) else Color.Transparent

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .clickable { onUpdateConfig(config.copy(wheelMode = mode)) }
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, if (isModeSelected) Color(0xFF0284C7) else BorderLight, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isModeSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0284C7))
                                    )
                                }
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = mode.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isModeSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isModeSelected) Color(0xFF0284C7) else TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "[${mode.subtitle}]",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    text = mode.desc,
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // 4. 多维条件过滤折叠配置面板
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF8FAFC))
                    .clickable { isFilterExpanded = !isFilterExpanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                    Text(
                        text = if (isFilterExpanded) "收起过滤参数设置" else "展开多维形态指标过滤配置 (和值/奇偶/连号/跨度)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0284C7)
                    )
                }
                Icon(
                    imageVector = if (isFilterExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF0284C7),
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = isFilterExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, BorderLight, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 和值范围过滤
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "1. 和值范围过滤: ${config.sumMin} ~ ${config.sumMax}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Switch(
                                checked = config.enableSumFilter,
                                onCheckedChange = { onUpdateConfig(config.copy(enableSumFilter = it)) },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        if (config.enableSumFilter) {
                            RangeSlider(
                                value = config.sumMin.toFloat()..config.sumMax.toFloat(),
                                onValueChange = { range ->
                                    onUpdateConfig(config.copy(sumMin = range.start.toInt(), sumMax = range.endInclusive.toInt()))
                                },
                                valueRange = 40f..180f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Divider(color = BorderLight, thickness = 0.5.dp)

                    // 奇偶比过滤
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2. 奇偶比允许保留 (奇数个数)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Switch(
                                checked = config.enableOddEvenFilter,
                                onCheckedChange = { onUpdateConfig(config.copy(enableOddEvenFilter = it)) },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        if (config.enableOddEvenFilter) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(2 to "2奇", 3 to "3奇", 4 to "4奇", 1 to "1奇/5奇").forEach { (count, label) ->
                                    val isSelected = count in config.allowedOddCounts
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) Color(0xFF0284C7) else Color.White)
                                            .border(1.dp, if (isSelected) Color(0xFF0284C7) else BorderLight, RoundedCornerShape(6.dp))
                                            .clickable {
                                                val next = config.allowedOddCounts.toMutableSet()
                                                if (isSelected) next.remove(count) else next.add(count)
                                                onUpdateConfig(config.copy(allowedOddCounts = next))
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = label, fontSize = 10.sp, color = if (isSelected) Color.White else TextPrimary)
                                    }
                                }
                            }
                        }
                    }

                    Divider(color = BorderLight, thickness = 0.5.dp)

                    // 连号过滤
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "3. 连号组数过滤", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = "默认保留无连号(0对)或单组连号(1对)", fontSize = 10.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = config.enableConsecutiveFilter,
                            onCheckedChange = { onUpdateConfig(config.copy(enableConsecutiveFilter = it)) },
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Divider(color = BorderLight, thickness = 0.5.dp)

                    // 历史一等奖开奖排除
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "4. 排除历史一等奖全同号码", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = "排除历史上 2000+ 期曾完全开出过的组合", fontSize = 10.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = config.enableExcludeHistory,
                            onCheckedChange = { onUpdateConfig(config.copy(enableExcludeHistory = it)) },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 5. 开始缩水按钮
            Button(
                onClick = onExecuteShrink,
                enabled = !isShrinking && selectedBalls.size >= lotteryType.redCount,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                if (isShrinking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在进行保奖矩阵与多维组合计算...", fontSize = 13.sp)
                } else {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("开始旋转矩阵与多维缩水", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 6. 缩水结果展示区域
            if (shrinkResult != null && !isShrinking) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF0FDF4))
                        .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 战报指标统计栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "🎉 缩水完成！注数从 ${shrinkResult.originalTotalBets} 注 降至 ${shrinkResult.filteredCount} 注",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )
                            Text(
                                text = "投注成本从 ${shrinkResult.originalCost} 元 精简至 ${shrinkResult.filteredCost} 元 (耗时 ${shrinkResult.executionTimeMs}ms)",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GreenPrimary)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "缩水率 ${String.format("%.1f", shrinkResult.shrinkRatio)}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // 批量复制按钮
                    if (shrinkResult.filteredBets.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val text = shrinkResult.filteredBets.joinToString("\n") { bet ->
                                        bet.joinToString(" ") { String.format("%02d", it) }
                                    }
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("ShrinkBets", text)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "已复制全部 ${shrinkResult.filteredCount} 注方案", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("复制全部方案", fontSize = 11.sp)
                            }
                        }

                        // 方案列表 (前 15 注预览)
                        shrinkResult.filteredBets.take(15).forEachIndexed { index, bet ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "#${index + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        bet.forEach { num ->
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(RedPrimary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = String.format("%02d", num),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // 设为心水
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(RedLight)
                                            .clickable { onApplyBetToHeart(bet) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = "设为心水", fontSize = 10.sp, color = RedPrimary)
                                    }

                                    // 存为预测
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(GreenLight)
                                            .clickable { onSaveBetToPrediction(bet) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = "记录预测", fontSize = 10.sp, color = GreenPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (shrinkResult.filteredCount > 15) {
                            Text(
                                text = "更多 ${shrinkResult.filteredCount - 15} 注已生成，可点击右上角【复制全部方案】导出",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        }
    }
}
