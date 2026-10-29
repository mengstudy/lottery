package com.lottery.analyzer.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottery.analyzer.ui.theme.BorderLight
import com.lottery.analyzer.ui.theme.GreenLight
import com.lottery.analyzer.ui.theme.GreenPrimary
import com.lottery.analyzer.ui.theme.OrangeLight
import com.lottery.analyzer.ui.theme.OrangePrimary
import com.lottery.analyzer.ui.theme.RedLight
import com.lottery.analyzer.ui.theme.RedPrimary
import com.lottery.analyzer.ui.theme.TextPrimary
import com.lottery.analyzer.ui.theme.TextSecondary
import com.lottery.analyzer.util.AppLogger
import com.lottery.analyzer.util.LogEntry
import com.lottery.analyzer.util.LogLevel

/**
 * 手机端运行与调试日志查看器弹窗
 */
@Composable
fun LogViewerDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val allLogs by AppLogger.logsFlow.collectAsState()
    var selectedFilter by remember { mutableStateOf("全部") } // 全部, 仅错误, AI相关, 数据同步

    val filteredLogs = remember(allLogs, selectedFilter) {
        when (selectedFilter) {
            "仅错误" -> allLogs.filter { it.level == LogLevel.ERROR }
            "AI相关" -> allLogs.filter { it.tag.contains("Ai", ignoreCase = true) }
            "数据同步" -> allLogs.filter { it.tag.contains("Sync", ignoreCase = true) }
            else -> allLogs
        }.reversed() // 最新的日志显示在最顶部
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "程序运行日志",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = "${allLogs.size}条",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. 过滤标签栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("全部", "仅错误", "AI相关", "数据同步").forEach { filterName ->
                        val isSelected = filterName == selectedFilter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFF1F5F9))
                                .clickable { selectedFilter = filterName }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = filterName,
                                fontSize = 11.sp,
                                color = if (isSelected) Color.White else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // 2. 日志列表滚动区
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (allLogs.isEmpty()) "暂无运行日志" else "没有符合当前筛选条件的日志",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A)) // 深色控制台背景
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { logEntry ->
                            LogItemView(entry = logEntry)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 复制日志到剪贴板
                OutlinedButton(
                    onClick = {
                        val fullLogText = AppLogger.getLogsAsText()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("LotteryLogs", fullLogText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "已复制全部日志到剪贴板", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("复制日志", fontSize = 12.sp)
                }

                // 清空日志
                OutlinedButton(
                    onClick = {
                        AppLogger.clearLogs()
                    }
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("清空", fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("关闭")
            }
        }
    )
}

/**
 * 单条日志展示项
 */
@Composable
private fun LogItemView(entry: LogEntry) {
    var isExpanded by remember { mutableStateOf(false) }

    val levelColor = when (entry.level) {
        LogLevel.ERROR -> Color(0xFFEF4444)
        LogLevel.WARN -> Color(0xFFF59E0B)
        LogLevel.INFO -> Color(0xFF38BDF8)
        LogLevel.DEBUG -> Color(0xFF94A3B8)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.6f))
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.timeFormatted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "[${entry.level.label}]",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = levelColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = entry.tag,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFA5B4FC)
                )
            }

            if (!entry.errorDetail.isNullOrBlank()) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { isExpanded = !isExpanded }
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = entry.message,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFE2E8F0),
            lineHeight = 15.sp
        )

        // 异常调用栈折叠展开
        if (!entry.errorDetail.isNullOrBlank()) {
            AnimatedVisibility(visible = isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF334155))
                        .padding(6.dp)
                ) {
                    Text(
                        text = entry.errorDetail,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFCA5A5),
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
