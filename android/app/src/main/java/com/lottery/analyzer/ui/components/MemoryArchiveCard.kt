package com.lottery.analyzer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottery.analyzer.data.model.*
import com.lottery.analyzer.ui.viewmodel.LotteryUiState
import com.lottery.analyzer.ui.viewmodel.LotteryViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryArchiveCard(viewModel: LotteryViewModel, state: LotteryUiState) {
    var collecting by rememberSaveable(state.currentType) { mutableStateOf(false) }
    var target by rememberSaveable(state.currentType, state.nextIssueTarget) { mutableStateOf(state.nextIssueTarget) }
    var source by rememberSaveable(state.currentType) { mutableStateOf("") }
    var originName by rememberSaveable(state.currentType) { mutableStateOf(ArchiveOrigin.REALTIME_RECOMMEND.name) }
    val selectedOrigin = runCatching { ArchiveOrigin.valueOf(originName) }.getOrDefault(ArchiveOrigin.REALTIME_RECOMMEND)
    LaunchedEffect(state.currentType) { viewModel.refreshMemoryArchive() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("逐期记忆档案", fontWeight = FontWeight.Bold)
            Text("收集批次 ${state.memoryArchive.batchCount} · AI请求 ${state.memoryArchive.runCount} · 原始方案 ${state.memoryArchive.originalCount} · 确认版本 ${state.memoryArchive.finalCount}", fontSize = 12.sp)
            Text("第一阶段仅归档；自动开奖结算和动态调整将在后续接入。缺少可信时间依据的记录不进入正式成绩。", fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { collecting = true }, enabled = CollectionInputPolicy.error(state.collectedBallsText) == null && !state.isParsingCollectedText && state.frequencyResult.parsedCompleteBets.isNotEmpty()) { Text("记录收集批次") }
                TextButton(onClick = viewModel::refreshMemoryArchive) { Text("刷新") }
            }
            state.memoryArchiveError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            state.memoryArchive.recentBatches.forEach { batch ->
                TextButton(onClick = { viewModel.showMemoryCollection(batch.id) }) { Text("收集 ${batch.targetIssue.ifBlank { "目标待确认" }} · ${batch.source} · ${batch.count}组", fontSize = 12.sp) }
            }
            state.memoryArchive.recentRuns.forEach { run ->
                TextButton(onClick = { viewModel.showMemoryArchiveRun(run.id) }) {
                    Text("${run.targetIssue.ifBlank { "目标待确认" }} · ${statusTitle(run.status)} · 保留${run.accepted}/${run.requested}注", fontSize = 12.sp)
                }
            }
        }
    }
    if (collecting) AlertDialog(
        onDismissRequest = { collecting = false }, title = { Text("固化收集批次") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(target, { if (it.length <= 12) target = it }, label = { Text("已核对的目标期号") }, singleLine = true)
                OutlinedTextField(source, { source = it.take(100) }, label = { Text("来源备注（可选）") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ArchiveOrigin.values().forEach { origin ->
                        FilterChip(selected = selectedOrigin == origin, onClick = { originName = origin.name }, label = {
                            Text(when (origin) { ArchiveOrigin.REALTIME_RECOMMEND -> "实时收集"; ArchiveOrigin.HISTORY_REVIEW -> "历史复盘"; ArchiveOrigin.MANUAL_BACKFILL -> "人工补录" })
                        })
                    }
                }
                Text("保存当前原文与解析快照，不随后续编辑改变。实时收集标签不等于已证明开奖前。", fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(enabled = target.length in 5..12 && target.all { it in '0'..'9' }, onClick = {
            viewModel.saveCollectedBatch(target, selectedOrigin, source); collecting = false
        }) { Text("确认归档") } },
        dismissButton = { TextButton(onClick = { collecting = false }) { Text("取消") } }
    )
    state.memoryArchiveDetail?.let { detail ->
        AlertDialog(onDismissRequest = { viewModel.showMemoryArchiveRun(null) }, title = { Text("${detail.targetIssue.ifBlank { "目标待确认" }} · ${statusTitle(detail.status)}") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${detail.provider} / ${detail.model}", fontSize = 12.sp)
                    if (detail.finishReason == "length") Text("响应达到长度上限，保留了通过核验的完整方案。", fontSize = 12.sp)
                    Text("原始方案与确认版本分别留档；最多展示200条。", fontSize = 11.sp)
                    detail.entries.forEachIndexed { index, entry ->
                        Text("${index + 1}. ${if (entry.stage == "AI_ORIGINAL") "AI原始" else if (entry.edited) "用户修改确认" else "用户确认"} · ${entry.rejection ?: "已通过校验"}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text((entry.reds?.joinToString(" ") ?: "无法解析") + entry.blues?.takeIf { it.isNotEmpty() }?.joinToString(" ", prefix = " + ").orEmpty(), fontSize = 12.sp)
                        Text(if (entry.timing == ArchiveTiming.POST_DRAW.name) "时间资格：开奖后记录" else "时间资格：证据不足，不计正式成绩", fontSize = 11.sp)
                    }
                }
            }, confirmButton = { TextButton(onClick = { viewModel.showMemoryArchiveRun(null) }) { Text("关闭") } })
    }
    state.memoryCollectionDetail?.let { detail ->
        AlertDialog(onDismissRequest = { viewModel.showMemoryArchiveRun(null) }, title = { Text("收集 ${detail.summary.targetIssue.ifBlank { "目标待确认" }}") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${detail.summary.source} · ${detail.summary.count}组 · 未声明玩法时按参考池留档", fontSize = 12.sp)
                    detail.groups.take(100).forEachIndexed { index, group -> Text("${index + 1}. ${group.first.joinToString(" ")}" + group.second.takeIf { it.isNotEmpty() }?.joinToString(" ", prefix = " + ").orEmpty(), fontSize = 12.sp) }
                    if (detail.groups.size > 100) Text("仅预览前100组，完整结构化数据已保留。", fontSize = 11.sp)
                    Text("原文预览（最多3000字符）", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(detail.rawText.orEmpty().take(3000), fontSize = 11.sp)
                }
            }, confirmButton = { TextButton(onClick = { viewModel.showMemoryArchiveRun(null) }) { Text("关闭") } })
    }
}

private fun statusTitle(status: String): String = when (status) {
    "PENDING" -> "进行中"; "COMPLETED" -> "完成"; "PARTIAL" -> "部分保留"
    "FAILED" -> "失败"; "CANCELLED" -> "已取消"; "INTERRUPTED" -> "进程中断"; else -> "未知状态"
}
