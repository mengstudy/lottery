package com.lottery.analyzer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottery.analyzer.data.model.AiMemoryOptions

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiMemoryOptionsCard(options:AiMemoryOptions,onChange:(AiMemoryOptions)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { Switch(options.twoStage,{onChange(options.copy(twoStage=it))});Text("本地候选 → AI选择编号",fontSize=13.sp) }
        Row(verticalAlignment=Alignment.CenterVertically) { Switch(options.enabled,{onChange(options.copy(enabled=it))});Text("使用逐期记忆摘要",fontSize=13.sp) }
        Row(verticalAlignment=Alignment.CenterVertically) { Switch(options.adaptive,{onChange(options.copy(adaptive=it))},enabled=options.enabled);Text("受控策略配额调整",fontSize=13.sp) }
        Text("只有具备开奖前证据、匹配玩法与规则的正式数据才用于调权。少于30期维持均衡；历史命中不代表下期概率。",fontSize=11.sp)
        if(options.enabled)FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            listOf(30,60,100).forEach{n->FilterChip(options.historyPeriods==n,{onChange(options.copy(historyPeriods=n))},label={Text("记忆${n}期")})}
        }
        if(options.twoStage)FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            listOf(12,16,20).forEach{n->FilterChip(options.candidateCount==n,{onChange(options.copy(candidateCount=n))},label={Text("候选${n}组")})}
        }
    }
}

fun memoryPolicyTitle(status:String)=when(status) {
    "INSUFFICIENT_DATA"->"正式数据不足，维持均衡"
    "NO_ADVANTAGE_EVIDENCE"->"未发现足够优势证据"
    "CONTROLLED"->"受控调整，每期最多5个百分点"
    "CORRECTION_FROZEN"->"历史数据修订，冻结至下一正式期"
    "DISABLED"->"自动调整已关闭"
    "HISTORICAL_SCOPED"->"历史截止期隔离"
    else->"固定配额"
}
