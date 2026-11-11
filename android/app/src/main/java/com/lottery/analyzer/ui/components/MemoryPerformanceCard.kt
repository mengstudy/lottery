package com.lottery.analyzer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottery.analyzer.data.model.*
import com.lottery.analyzer.ui.viewmodel.LotteryViewModel
import com.lottery.analyzer.ui.viewmodel.LotteryUiState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryPerformanceCard(viewModel:LotteryViewModel,state:LotteryUiState) {
    val report=state.performanceReport
    var track by rememberSaveable(state.currentType){mutableStateOf("AI_ACCEPTED")}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("开奖结算与历史对照",fontWeight=FontWeight.Bold,fontSize=14.sp)
        Text(if(state.isSettlingArchive)"正在核验档案…" else "已核验${report.issues}期 · 正式${report.formalIssues}期 · 观察${report.observationIssues}期 · 待开奖/未匹配${report.pendingIssues}期",fontSize=12.sp)
        Text("按同彩种、注型、号码规模和时间资格分开统计；先取每期组均值，再跨期平均。不同轨道不合并为独立样本。",fontSize=11.sp)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf(30,60,100).forEach{n->FilterChip(state.aiMemoryOptions.historyPeriods==n,{viewModel.updateAiMemoryOptions(state.aiMemoryOptions.copy(historyPeriods=n))},label={Text("${n}期")})}
        }
        val tracks=linkedMapOf("COLLECTED" to "收集", "AI_ORIGINAL" to "原始合法", "AI_ACCEPTED" to "AI保留", "USER_FINAL" to "用户确认", "LOCAL_CANDIDATE" to "本地候选")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            tracks.forEach{(id,title)->FilterChip(track==id,{track=id},label={Text(title)})}
        }
        val visible=report.rows.filter{it.track==track}
        if(visible.isEmpty())Text("该轨道暂无已匹配开奖的合法方案。",fontSize=12.sp)
        visible.take(24).forEach { row ->
            val qualification=when(row.eligibility){"FORMAL"->"正式";"POST_DRAW"->"开奖后观察";"REVIEW"->"复盘/补录";else->"时间证据不足"}
            val kind=when(row.kind){"SINGLE"->"单注";"COMPOUND"->"复式池";else->"参考池"}
            Text("$kind ${row.redCount}+${row.blueCount} ${row.play.orEmpty()} · $qualification · ${row.issues}期/${row.plans}组",fontWeight=FontWeight.Bold,fontSize=12.sp)
            fun format(r:Double?,b:Double?)=if(r==null||b==null)"采样不足/不适用" else String.format(Locale.CHINA,"红 %.2f / 蓝 %.2f",r,b)
            Text("实际平均命中：${format(row.redMean,row.blueMean)}\n同规模随机：${format(row.randomRed,row.randomBlue)}\n同约束随机：${format(row.constrainedRed,row.constrainedBlue)}",fontSize=11.sp)
            if(row.track=="AI_ACCEPTED")Text("同池同组数随机：${format(row.poolRed,row.poolBlue)}",fontSize=11.sp)
        }
        if(visible.size>24)Text("仅预览前24个分组。",fontSize=11.sp)
        Text("随机对照使用固定种子和有限采样；采样不足不强行给出比较结果。复式/参考池只比命中，不计算单注奖级或收益。",fontSize=11.sp)
        if(report.requestedGroups>0)Text("该窗口请求${report.requestedGroups}组，保留${report.retainedGroups}组（合规统计，非中奖率）",fontSize=11.sp)
        if(state.settlementResult.skipped>0)Text("${state.settlementResult.skipped}项因异常开奖或数据变化未结算，请刷新重试。",fontSize=11.sp,color=MaterialTheme.colorScheme.error)
    }
}
