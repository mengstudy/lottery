package com.lottery.analyzer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lottery.analyzer.data.model.*
import com.lottery.analyzer.ui.theme.*
import com.lottery.analyzer.ui.viewmodel.LotteryUiState

/**
 * 逐期开奖复盘全屏对话框
 * 涵盖：选期、开奖对照、收集号码/本地候选/AI精选/用户最终保存、改号前后差异与命中影响
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IssueReviewDialog(
    state: LotteryUiState,
    onSelectIssue: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 20.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFF8FAFC)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${state.currentType.title} · 逐期开奖复盘",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "沿用归档与结算数据，对照开奖、收集、候选、AI与最终保存",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                HorizontalDivider(color = BorderLight)

                // 期号选择栏
                if (state.reviewOptions.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.reviewOptions) { option ->
                            val selected = option.issue == state.selectedReviewIssue
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectIssue(option.issue) },
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "第${option.issue}期",
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (option.hasDraw) GreenPrimary else Color(0xFFCBD5E1))
                                        )
                                    }
                                }
                            )
                        }
                    }
                    HorizontalDivider(color = BorderLight)
                }

                // 主体内容区域
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.isReviewLoading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        state.issueReviewError != null -> {
                            Text(
                                text = state.issueReviewError,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                modifier = Modifier.align(Alignment.Center).padding(16.dp)
                            )
                        }
                        state.reviewOptions.isEmpty() -> {
                            Text(
                                text = "暂无可复盘的期号记录。请先进行号码收集、AI 推演或保存预测。",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.align(Alignment.Center).padding(24.dp)
                            )
                        }
                        state.issueReviewDetail != null -> {
                            ReviewDetailContent(detail = state.issueReviewDetail)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewDetailContent(detail: IssueReviewDetail) {
    var expandLocal by remember(detail.issue) { mutableStateOf(false) }
    var expandRejectedAi by remember(detail.issue) { mutableStateOf(false) }

    val acceptedAi = remember(detail.aiOriginalItems) { detail.aiOriginalItems.filter { !it.isRejected } }
    val rejectedAi = remember(detail.aiOriginalItems) { detail.aiOriginalItems.filter { it.isRejected } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 实际开奖号码置顶卡
        item {
            DrawHeaderCard(detail = detail)
        }

        // 口径说明卡片：明确区分“实际判奖”与“对照统计”
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFEFF6FF),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE))
            ) {
                Text(
                    text = "口径说明：标注「单注」的方案按官方规则核验奖级；标注「参考池 / 复式 / 旧记录」的条目仅作对照统计命中数，不作为单注中奖依据。",
                    fontSize = 11.sp,
                    color = Color(0xFF1E40AF),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }

        // 2. 改号前后差异与命中影响（若有改号）
        if (detail.editComparisons.isNotEmpty()) {
            item {
                ReviewSectionHeader(
                    title = "改号前后差异与影响 (${detail.editComparisons.size})",
                    subtitle = "对比用户修改确认版本与改号前 AI 方案的号码及命中变化",
                    accentColor = OrangePrimary
                )
            }
            items(detail.editComparisons) { comp ->
                EditComparisonCard(comparison = comp, hasDraw = detail.drawRecord != null)
            }
        }

        // 3. 用户最终保存号码
        item {
            ReviewSectionHeader(
                title = "用户最终保存号码 (${detail.userFinalItems.size})",
                subtitle = "单注按规则核验奖级；复式与参考池仅统计命中数",
                accentColor = PurplePrimary
            )
        }
        if (detail.userFinalItems.isEmpty()) {
            item { EmptySectionHint("本期暂无用户最终保存记录") }
        } else {
            items(detail.userFinalItems) { item ->
                ReviewItemCard(item = item, hasDraw = detail.drawRecord != null)
            }
        }

        // 4. AI 精选方案
        item {
            ReviewSectionHeader(
                title = "AI 精选方案 (${acceptedAi.size})",
                subtitle = "大模型通过规则校验并保留的方案",
                accentColor = BluePrimary
            )
        }
        if (acceptedAi.isEmpty()) {
            item { EmptySectionHint("本期暂无 AI 精选记录") }
        } else {
            items(acceptedAi) { item ->
                ReviewItemCard(item = item, hasDraw = detail.drawRecord != null)
            }
        }

        // 5. 收集号码（参考池）
        if (detail.collectedItems.isNotEmpty()) {
            item {
                ReviewSectionHeader(
                    title = "收集号码对照 (${detail.collectedItems.size})",
                    subtitle = "外部或手工收集批次，作为参考池统计命中，不作单注判奖",
                    accentColor = GreenPrimary
                )
            }
            items(detail.collectedItems) { item ->
                ReviewItemCard(item = item, hasDraw = detail.drawRecord != null)
            }
        }

        // 6. 本地候选池（默认折叠，避免刷屏）
        if (detail.localCandidates.isNotEmpty()) {
            item {
                CollapsibleSectionHeader(
                    title = "本地候选池 (${detail.localCandidates.size})",
                    expanded = expandLocal,
                    onToggle = { expandLocal = !expandLocal }
                )
            }
            if (expandLocal) {
                items(detail.localCandidates) { item ->
                    ReviewItemCard(item = item, hasDraw = detail.drawRecord != null)
                }
            }
        }

        // 7. 未采纳的 AI 原始方案（默认折叠）
        if (rejectedAi.isNotEmpty()) {
            item {
                CollapsibleSectionHeader(
                    title = "AI 未通过校验 / 未采纳方案 (${rejectedAi.size})",
                    expanded = expandRejectedAi,
                    onToggle = { expandRejectedAi = !expandRejectedAi }
                )
            }
            if (expandRejectedAi) {
                items(rejectedAi) { item ->
                    ReviewItemCard(item = item, hasDraw = detail.drawRecord != null)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrawHeaderCard(detail: IssueReviewDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "第 ${detail.issue} 期 · 官方开奖号码",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (detail.drawRecord != null) {
                    Text(
                        text = detail.drawRecord.drawDate,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
            if (detail.drawRecord == null) {
                Text(
                    text = "本期暂未同步到本地开奖结果，待同步后将自动核对红蓝球命中。",
                    fontSize = 12.sp,
                    color = OrangePrimary
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    detail.drawRecord.redBalls.forEach { num ->
                        ReviewBall(number = num, isBlue = false, isHit = true)
                    }
                    detail.drawRecord.blueBalls.forEach { num ->
                        ReviewBall(number = num, isBlue = true, isHit = true)
                    }
                }
                if (detail.drawRevision != null) {
                    Text(
                        text = "已按当前活跃开奖修订版 (${detail.drawRevision.take(8)}) 完成结算对齐",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewItemCard(item: IssueReviewItem, hasDraw: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isWon) Color(0xFFFFFBEB) else Color.White
        ),
        border = BorderStroke(1.dp, if (item.isWon) Color(0xFFFCD34D) else BorderLight)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val (badgeText, badgeBg, badgeFg) = when {
                        item.isRejected -> Triple("未采纳", Color(0xFFFEE2E2), Color(0xFFB91C1C))
                        item.kind == PredictionKind.SINGLE -> Triple("单注", Color(0xFFEDE9FE), PurplePrimary)
                        item.kind == PredictionKind.COMPOUND -> Triple("复式·仅统计", Color(0xFFE0F2FE), Color(0xFF0369A1))
                        item.kind == PredictionKind.REFERENCE -> Triple("参考池·对照", Color(0xFFECFDF5), Color(0xFF047857))
                        else -> Triple("旧记录·仅命中", Color(0xFFF1F5F9), TextSecondary)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(text = badgeText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = badgeFg)
                    }
                    Text(
                        text = item.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                if (hasDraw && !item.isRejected && item.redHits != null) {
                    val hitText = if (item.blues.isNotEmpty() || item.blueHits != null && item.blueHits > 0) {
                        "中 ${item.redHits}红+${item.blueHits ?: 0}蓝"
                    } else {
                        "中 ${item.redHits} 码"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (item.isWon) Color(0xFFFEF3C7) else Color(0xFFF1F5F9))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = hitText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isWon) Color(0xFFB45309) else TextPrimary
                        )
                    }
                }
            }

            if (item.reds.isNotEmpty() || item.blues.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    item.reds.forEach { num ->
                        ReviewBall(
                            number = num,
                            isBlue = false,
                            isHit = !hasDraw || num in item.hitReds,
                            muted = hasDraw && num !in item.hitReds
                        )
                    }
                    item.blues.forEach { num ->
                        ReviewBall(
                            number = num,
                            isBlue = true,
                            isHit = !hasDraw || num in item.hitBlues,
                            muted = hasDraw && num !in item.hitBlues
                        )
                    }
                }
            }

            // 展示奖级 / 口径说明 / 拒绝原因
            val footerText = when {
                item.isRejected -> "未采纳原因：${item.rejectionReason ?: "规则校验未通过"}"
                item.prizeSummary != null -> item.prizeSummary
                else -> null
            }
            if (!footerText.isNullOrBlank()) {
                Text(
                    text = footerText,
                    fontSize = 11.sp,
                    color = if (item.isWon) Color(0xFFB45309) else TextSecondary,
                    fontWeight = if (item.isWon) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun EditComparisonCard(comparison: IssueEditComparison, hasDraw: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
        border = BorderStroke(1.dp, Color(0xFFFED7AA))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comparison.finalItem.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (hasDraw && comparison.redHitDelta != null) {
                    val rDelta = comparison.redHitDelta
                    val bDelta = comparison.blueHitDelta ?: 0
                    val deltaSummary = buildString {
                        append("改号影响：红球")
                        append(if (rDelta > 0) "+$rDelta" else "$rDelta")
                        if (comparison.finalItem.blues.isNotEmpty() || comparison.originalItem.blues.isNotEmpty()) {
                            append(" / 蓝球")
                            append(if (bDelta > 0) "+$bDelta" else "$bDelta")
                        }
                    }
                    Text(
                        text = deltaSummary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            rDelta + bDelta > 0 -> GreenPrimary
                            rDelta + bDelta < 0 -> RedPrimary
                            else -> TextSecondary
                        }
                    )
                }
            }

            Text(
                text = "改号前：${comparison.originalItem.reds.joinToString(" ") { String.format("%02d", it) }}" +
                    if (comparison.originalItem.blues.isNotEmpty()) " + ${comparison.originalItem.blues.joinToString(" ") { String.format("%02d", it) }}" else "",
                fontSize = 11.sp,
                color = TextSecondary
            )
            Text(
                text = "改号后：${comparison.finalItem.reds.joinToString(" ") { String.format("%02d", it) }}" +
                    if (comparison.finalItem.blues.isNotEmpty()) " + ${comparison.finalItem.blues.joinToString(" ") { String.format("%02d", it) }}" else "",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            val diffParts = mutableListOf<String>()
            if (comparison.addedReds.isNotEmpty()) {
                diffParts.add("新增红球[${comparison.addedReds.joinToString(",") { String.format("%02d", it) }}]")
            }
            if (comparison.removedReds.isNotEmpty()) {
                diffParts.add("删去红球[${comparison.removedReds.joinToString(",") { String.format("%02d", it) }}]")
            }
            if (comparison.addedBlues.isNotEmpty()) {
                diffParts.add("新增蓝球[${comparison.addedBlues.joinToString(",") { String.format("%02d", it) }}]")
            }
            if (comparison.removedBlues.isNotEmpty()) {
                diffParts.add("删去蓝球[${comparison.removedBlues.joinToString(",") { String.format("%02d", it) }}]")
            }
            if (diffParts.isNotEmpty()) {
                Text(
                    text = diffParts.joinToString(" · "),
                    fontSize = 11.sp,
                    color = OrangePrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ReviewBall(
    number: Int,
    isBlue: Boolean,
    isHit: Boolean,
    muted: Boolean = false
) {
    val bgColor = when {
        muted -> Color(0xFFF1F5F9)
        isBlue -> BluePrimary
        else -> RedPrimary
    }
    val textColor = if (muted) Color(0xFF94A3B8) else Color.White
    val borderColor = when {
        !muted && isHit -> Color(0xFFF59E0B)
        muted -> Color(0xFFE2E8F0)
        else -> bgColor
    }
    Surface(
        modifier = Modifier.size(26.dp),
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(if (!muted && isHit) 1.5.dp else 1.dp, borderColor)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = String.format("%02d", number),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
private fun ReviewSectionHeader(title: String, subtitle: String, accentColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accentColor)
        Text(text = subtitle, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun CollapsibleSectionHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE2E8F0).copy(alpha = 0.5f))
            .clickable { onToggle() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = TextSecondary
        )
    }
}

@Composable
private fun EmptySectionHint(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = TextSecondary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

/**
 * 分析看板内嵌的「当期逐期开奖复盘」卡片
 * 随看板期号切换自动更新，展示当期归档对照摘要、改号影响与一键展开完整复盘
 */
@Composable
fun DashboardIssueReviewCard(
    issue: String,
    reviewDetail: IssueReviewDetail?,
    onOpenFullReview: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val detail = reviewDetail?.takeIf { it.issue == issue }
    val acceptedAi = remember(detail) { detail?.aiOriginalItems?.filter { !it.isRejected }.orEmpty() }
    val userFinals = detail?.userFinalItems.orEmpty()
    val collected = detail?.collectedItems.orEmpty()
    val localCandidates = detail?.localCandidates.orEmpty()
    val editComps = detail?.editComparisons.orEmpty()

    val hasAnyData = userFinals.isNotEmpty() || acceptedAi.isNotEmpty() || collected.isNotEmpty() || localCandidates.isNotEmpty()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔍 第 $issue 期 · 开奖复盘对照",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = if (hasAnyData) "查看完整复盘 >" else "复盘中心 >",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PurplePrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(PurpleLight)
                        .clickable { onOpenFullReview(if (hasAnyData) issue else null) }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            if (!hasAnyData) {
                Text(
                    text = "本期暂无收集号码、AI 推演或保存预测记录，点击右侧可进入复盘中心查看其它已归档期号。",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            } else {
                Text(
                    text = "用户保存 ${userFinals.size} · AI精选 ${acceptedAi.size} · 收集参考 ${collected.size} · 本地候选 ${localCandidates.size}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                // 优先展示改号前后对比摘要（若有）
                editComps.firstOrNull()?.let { comp ->
                    EditComparisonCard(comparison = comp, hasDraw = detail?.drawRecord != null)
                }

                // 预览最多 2 条核心记录（优先用户最终保存，其次 AI 精选）
                val previewItems = (userFinals + acceptedAi).take(2)
                previewItems.forEach { item ->
                    ReviewItemCard(item = item, hasDraw = detail?.drawRecord != null)
                }
            }
        }
    }
}
