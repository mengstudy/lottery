package com.lottery.analyzer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.lottery.analyzer.ui.theme.RedDark
import com.lottery.analyzer.ui.theme.RedPrimary
import com.lottery.analyzer.ui.theme.TextPrimary
import com.lottery.analyzer.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueSelectorBar(
    currentIssue: String,
    allIssues: List<String>,
    hasPrev: Boolean,
    hasNext: Boolean,
    isSyncing: Boolean,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    onIssueSelect: (String) -> Unit,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }
    var filterText by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 上一期
        IconButton(
            onClick = onPrevClick,
            enabled = hasPrev,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "上一期",
                tint = if (hasPrev) RedPrimary else Color(0xFFCBD5E1)
            )
        }

        // 当前期号按钮 (可点击弹窗)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFEF2F2))
                .clickable { showSheet = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "第 $currentIssue 期",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = RedDark
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = RedDark,
                modifier = Modifier.size(18.dp)
            )
        }

        // 下一期
        IconButton(
            onClick = onNextClick,
            enabled = hasNext,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "下一期",
                tint = if (hasNext) RedPrimary else Color(0xFFCBD5E1)
            )
        }

        // 联网同步按钮
        IconButton(
            onClick = onSyncClick,
            enabled = !isSyncing,
            modifier = Modifier.size(36.dp)
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = RedPrimary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "同步最新",
                    tint = TextSecondary
                )
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "快速跳转期号",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    placeholder = { Text("输入期号过滤 (如 2024...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                val filtered = if (filterText.isBlank()) {
                    allIssues
                } else {
                    allIssues.filter { it.contains(filterText) }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                ) {
                    items(filtered) { issue ->
                        val isSelected = issue == currentIssue
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFFFEF2F2) else Color.Transparent)
                                .clickable {
                                    onIssueSelect(issue)
                                    showSheet = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "第 $issue 期",
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) RedPrimary else TextPrimary
                            )
                            if (isSelected) {
                                Text(
                                    text = "当前选择",
                                    fontSize = 12.sp,
                                    color = RedPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
