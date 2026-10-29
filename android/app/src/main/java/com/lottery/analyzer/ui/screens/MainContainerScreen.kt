package com.lottery.analyzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.ui.components.AiSettingsDialog
import com.lottery.analyzer.ui.components.LogViewerDialog
import com.lottery.analyzer.ui.theme.BluePrimary
import com.lottery.analyzer.ui.theme.OrangePrimary
import com.lottery.analyzer.ui.theme.RedDark
import com.lottery.analyzer.ui.theme.RedPrimary
import com.lottery.analyzer.ui.theme.TextPrimary
import com.lottery.analyzer.ui.viewmodel.LotteryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainerScreen(viewModel: LotteryViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 看板, 1: 历史, 2: 心水比对, 3: 工具
    var showTypeMenu by remember { mutableStateOf(false) }

    val themeColors = when (uiState.currentType) {
        LotteryType.SSQ -> listOf(RedDark, RedPrimary)
        LotteryType.DLT -> listOf(Color(0xFF1565C0), BluePrimary)
        LotteryType.KL8 -> listOf(Color(0xFFD84315), OrangePrimary)
    }
    val activeColor = themeColors.last()

    val tabs = listOf(
        TabItem("分析看板", Icons.Default.Dashboard, RedPrimary),
        TabItem("历史列表", Icons.Default.History, Color(0xFF1E88E5)),
        TabItem("心水比对", Icons.Default.AutoAwesome, Color(0xFF7C3AED)),
        TabItem("工具箱", Icons.Default.ShowChart, Color(0xFFFB8C00))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showTypeMenu = true }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            val titleSuffix = when (selectedTab) {
                                0 -> "深度走势"
                                1 -> "历史开奖"
                                2 -> "预测比对"
                                else -> "走势工具"
                            }
                            Text(
                                text = "${uiState.currentType.title} · $titleSuffix",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "切换彩种",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 顶栏点击快速切换彩种菜单
                        DropdownMenu(
                            expanded = showTypeMenu,
                            onDismissRequest = { showTypeMenu = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            LotteryType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        when (type) {
                                                            LotteryType.SSQ -> RedPrimary
                                                            LotteryType.DLT -> BluePrimary
                                                            LotteryType.KL8 -> OrangePrimary
                                                        }
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${type.title} (${type.subtitle})",
                                                fontWeight = if (type == uiState.currentType) FontWeight.Bold else FontWeight.Normal,
                                                color = if (type == uiState.currentType) RedPrimary else TextPrimary
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.switchLotteryType(type)
                                        showTypeMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.setLogDialogVisible(true) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "运行日志",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = { viewModel.setAiSettingsDialogVisible(true) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "AI平台配置",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = { viewModel.syncLatestData() },
                        enabled = !uiState.isSyncing
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "同步最新",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.background(
                    Brush.horizontalGradient(themeColors)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) tab.color else Color(0xFF94A3B8),
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) tab.color else Color(0xFF64748B)
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = tab.color.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC))
        ) {
            when (selectedTab) {
                0 -> LotteryDashboardScreen(viewModel = viewModel, uiState = uiState)
                1 -> HistoryScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onNavigateToDetail = { selectedTab = 0 } // 点击历史期次跳转至分析看板
                )
                2 -> PredictCompareScreen(viewModel = viewModel, uiState = uiState)
                3 -> ToolsScreen(viewModel = viewModel, uiState = uiState)
            }
        }
    }

    // 全局 AI 大模型配置弹窗
    if (uiState.showAiSettingsDialog) {
        AiSettingsDialog(
            config = uiState.aiConfig,
            isLoading = uiState.isAiLoading,
            testMessage = uiState.aiTestMessage,
            onDismiss = { viewModel.setAiSettingsDialogVisible(false) },
            onSaveConfig = { viewModel.updateAiConfig(it) },
            onTestConnection = { viewModel.testAiConnection(it) },
            onViewLogs = { viewModel.setLogDialogVisible(true) }
        )
    }

    // 全局运行日志查看弹窗
    if (uiState.showLogDialog) {
        LogViewerDialog(
            onDismiss = { viewModel.setLogDialogVisible(false) }
        )
    }
}

private data class TabItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)
