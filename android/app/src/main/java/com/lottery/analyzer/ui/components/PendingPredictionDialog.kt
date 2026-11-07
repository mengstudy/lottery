package com.lottery.analyzer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lottery.analyzer.data.model.PredictionKind
import com.lottery.analyzer.ui.viewmodel.LotteryUiState

@Composable
fun PendingPredictionDialog(
    state: LotteryUiState,
    onDismiss: () -> Unit,
    onConfirm: (String, List<Int>?) -> Unit
) {
    val drafts = state.pendingPredictions
    if (drafts.isEmpty()) return
    var issue by rememberSaveable(state.pendingTargetIssue) { mutableStateOf(state.pendingTargetIssue) }
    var acknowledged by rememberSaveable(drafts.size, state.pendingTargetIssue) { mutableStateOf(false) }
    var blues by remember(drafts) { mutableStateOf(drafts.singleOrNull()?.blueBalls?.joinToString(" ").orEmpty()) }
    var inputError by remember { mutableStateOf<String?>(null) }
    val editableBlues = drafts.size == 1 && drafts[0].lotteryType.maxBlueNumber > 0 && drafts[0].kind != PredictionKind.REFERENCE
    AlertDialog(
        onDismissRequest = { if (!state.isSavingPrediction) onDismiss() },
        title = { Text("确认保存 ${drafts.size} 条方案") },
        text = {
            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = issue, onValueChange = { issue = it; acknowledged = false },
                    label = { Text("官方目标期号") }, placeholder = { Text("请核对并填写期号") }, singleLine = true,
                    enabled = !state.isSavingPrediction)
                Text("期号仅为本地递增建议；跨年、休市或数据较旧时请按官方公告填写。")
                drafts.forEachIndexed { index, draft ->
                    Text("${index + 1}. ${draft.kind.title}${draft.kl8PlayType?.let { " · ${it.title}" }.orEmpty()}")
                    Text("红球：${draft.redBalls.joinToString(" ")}")
                    if (draft.blueBalls.isNotEmpty()) Text("蓝球：${draft.blueBalls.joinToString(" ")}（${draft.blueSource.title}）")
                    if (draft.kind == PredictionKind.REFERENCE) Text("参考池只比对命中，不作为单注判断中奖。")
                    if (draft.kind == PredictionKind.COMPOUND) Text("复式池需展开为单注核验，此处仅记录命中。")
                }
                if (editableBlues) OutlinedTextField(value = blues, onValueChange = { blues = it; acknowledged = false },
                    label = { Text("蓝球，可修改；多个号码用空格分隔") }, enabled = !state.isSavingPrediction, singleLine = true)
                Row {
                    Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it }, enabled = !state.isSavingPrediction)
                    Text("已核对官方期号和完整号码，包括机选补全的蓝球", modifier = Modifier.padding(top = 10.dp))
                }
                (inputError ?: state.predictionSaveError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = acknowledged && issue.isNotBlank() && !state.isSavingPrediction, onClick = {
                val override = if (editableBlues) {
                    val tokens = blues.trim().split(Regex("\\s+|,|，")).filter { it.isNotBlank() }
                    if (tokens.any { it.toIntOrNull() == null }) { inputError = "蓝球请输入数字"; return@TextButton }
                    tokens.map { it.toInt() }
                } else null
                inputError = null
                onConfirm(issue, override)
            }) { Text(if (state.isSavingPrediction) "正在保存…" else "确认保存") }
        },
        dismissButton = { TextButton(enabled = !state.isSavingPrediction, onClick = onDismiss) { Text("取消") } }
    )
}
