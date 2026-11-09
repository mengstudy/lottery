package com.lottery.analyzer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottery.analyzer.data.engine.*
import com.lottery.analyzer.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

/** Keyed state discards results immediately when data, lottery, or generation settings change. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FrequencyStrategyCard(
    type: LotteryType,
    records: List<LotteryRecord>,
    dataLoading: Boolean,
    onSave: (WindowGeneration, Int) -> Unit
) {
    if (type == LotteryType.KL8) return
    var windowText by rememberSaveable(type.name) { mutableStateOf("50") }
    var strategyName by rememberSaveable(type.name) { mutableStateOf(WindowStrategy.BALANCED.name) }
    var redCount by rememberSaveable(type.name) { mutableIntStateOf(type.redCount) }
    var blueCount by rememberSaveable(type.name) { mutableIntStateOf(type.blueCount) }
    val window = windowText.toIntOrNull()?.takeIf { it in 1..1000 }
    val strategy = WindowStrategy.valueOf(strategyName)

    var stats by remember(type, records, window, dataLoading) { mutableStateOf<WindowFrequencyStats?>(null) }
    var analysisError by remember(type, records, window, dataLoading) { mutableStateOf<String?>(null) }
    LaunchedEffect(type, records, window, dataLoading) {
        if (window == null || dataLoading) return@LaunchedEffect
        try {
            stats = withContext(Dispatchers.Default) {
                val context = coroutineContext
                WindowStrategyEngine.analyze(type, records, window) { context.ensureActive() }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { analysisError = e.message ?: "统计失败" }
    }

    // A generation request is consumed by LaunchedEffect, so leaving the screen or changing
    // parameters cancels work. Configuration survives recreation; generated results are transient.
    var request by remember(stats, strategy, redCount, blueCount) { mutableStateOf<Pair<Long, Int>?>(null) }
    var result by remember(stats, strategy, redCount, blueCount) { mutableStateOf<WindowGeneration?>(null) }
    var generationError by remember(stats, strategy, redCount, blueCount) { mutableStateOf<String?>(null) }
    var generating by remember(stats, strategy, redCount, blueCount) { mutableStateOf(false) }
    LaunchedEffect(stats, strategy, redCount, blueCount, request) {
        val source = stats ?: return@LaunchedEffect
        val action = request ?: return@LaunchedEffect
        generating = true
        result = null
        generationError = null
        try {
            result = withContext(Dispatchers.Default) {
                val context = coroutineContext
                WindowStrategyEngine.generate(source, strategy, redCount, blueCount, action.second, action.first) { context.ensureActive() }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { generationError = e.message ?: "生成失败" }
        finally { generating = false }
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("窗口频次与策略选号", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("按窗口出现次数统计，与遗漏冷热分别计算。历史频次不代表下期中奖概率。", fontSize = 11.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30, 50, 100).forEach { n ->
                    FilterChip(selected = windowText == n.toString(), onClick = { windowText = n.toString() }, label = { Text("${n}期") })
                }
            }
            OutlinedTextField(
                value = windowText,
                onValueChange = { if (it.length <= 4 && it.all { c -> c in '0'..'9' }) windowText = it },
                label = { Text("自定义窗口（1~1000期）") },
                isError = window == null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
            val source = stats
            if (source != null) {
                Text("实取 ${source.sampleCount} / 请求 ${source.requestedPeriods} 期 · ${source.firstIssue}—${source.lastIssue}", fontSize = 12.sp)
                if (source.excludedCount > 0) Text("输入中排除无效或重复记录 ${source.excludedCount} 条", fontSize = 11.sp)
                Text("频次排名（括号为出现期数；同频按号码排序）", fontSize = 11.sp)
                fun ranking(blue: Boolean, cold: Boolean): String {
                    val counts = if (blue) source.blueFrequency else source.redFrequency
                    return source.ranked(blue, cold).take(if (blue) 5 else 8).joinToString(" ") { "$it(${counts.getValue(it)})" }
                }
                Text("红/前区高频：${ranking(false, false)}\n红/前区低频：${ranking(false, true)}", fontSize = 12.sp)
                Text("蓝/后区高频：${ranking(true, false)}\n蓝/后区低频：${ranking(true, true)}", fontSize = 12.sp)
                Text(String.format(Locale.CHINA, "红/前区和值：均值 %.1f · 中位数 %.1f · 样本标准差 %.1f", source.sumMean, source.sumMedian, source.sumStdDev), fontSize = 11.sp)
                Text("和值分布：" + source.sumHistogram.entries.joinToString(" / ") { "${it.key}~${it.key + 19}:${it.value}期" }, fontSize = 11.sp)
                Text("奇偶分布：" + source.oddHistogram.entries.joinToString(" / ") { "${it.key}:${type.redCount - it.key}=${it.value}期" }, fontSize = 11.sp)
                Text("连号相邻对数：" + source.consecutiveHistogram.entries.joinToString(" / ") { "${it.key}对=${it.value}期" }, fontSize = 11.sp)
            } else Text(analysisError ?: if (window == null) "请输入有效窗口" else "正在加载统计…", fontSize = 12.sp)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WindowStrategy.values().forEach { item ->
                    FilterChip(selected = strategy == item, onClick = { strategyName = item.name }, label = { Text(item.title) })
                }
            }
            Text(when (strategy) {
                WindowStrategy.BALANCED -> "高低频各取约一半；同频按号码排序分组。"
                WindowStrategy.HOT -> "按频次加权抽样，所有合法号码均保留被选机会。"
                WindowStrategy.COLD -> "按低频权重抽样，包含窗口内未出现号码。"
                WindowStrategy.SEGMENT -> "全号码范围分成三区，尽量平均分配选号数量。"
                WindowStrategy.CONSECUTIVE -> "红/前区至少包含一对连号，其余随机；蓝/后区随机。"
            }, fontSize = 11.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("单注" to (type.redCount to type.blueCount), "7+2" to (7 to 2), "6+2" to (6 to 2)).forEach { (title, counts) ->
                    FilterChip(selected = redCount == counts.first && blueCount == counts.second,
                        onClick = { redCount = counts.first; blueCount = counts.second }, label = { Text(title) })
                }
            }
            CountSelector("红/前区", redCount, type.redCount, 16) { redCount = it }
            CountSelector("蓝/后区", blueCount, type.blueCount, 6) { blueCount = it }
            val combinations = WindowStrategyEngine.combinationCount(type, redCount, blueCount)
            Text("每组 $redCount+$blueCount · $combinations 注组合" + if (combinations > 1) "（复式池）" else "（单注）", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            if (combinations > 1) Text("仅计算组合注数；保存后展示命中，尚不提供复式奖级明细。不同组可能包含相同的单注组合。", fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5).forEach { count ->
                    Button(enabled = source != null && !generating && !dataLoading,
                        onClick = { request = Random.nextLong() to count }) { Text("生成 $count 组") }
                }
            }
            if (generating) Text("正在生成…", fontSize = 12.sp)
            generationError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            result?.let { output ->
                Text("${output.strategy.title} · ${output.bets.size} 组 · 每组 ${output.combinationsPerGroup} 注组合", fontSize = 12.sp)
                output.bets.forEachIndexed { index, bet ->
                    Text("第 ${index + 1} 组", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        bet.redBalls.forEach { LotteryBall(it, size = 30.dp, fontSize = 12) }
                        bet.blueBalls.forEach { LotteryBall(it, style = BallStyle.BLUE, size = 30.dp, fontSize = 12) }
                    }
                    OutlinedButton(onClick = { onSave(output, index) }) { Text("核对并记录第 ${index + 1} 组") }
                }
                if (output.bets.size > 1) OutlinedButton(onClick = { output.bets.indices.forEach { onSave(output, it) } }) { Text("核对并记录全部 ${output.bets.size} 组") }
            }
        }
    }
}

@Composable
private fun CountSelector(title: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = value > min, onClick = { onChange(value - 1) }) { Text("−") }
        Text("$title $value 码", modifier = Modifier.padding(top = 12.dp), fontSize = 12.sp)
        OutlinedButton(enabled = value < max, onClick = { onChange(value + 1) }) { Text("＋") }
    }
}
