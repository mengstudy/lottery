package com.lottery.analyzer.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottery.analyzer.data.model.AiConfig
import com.lottery.analyzer.data.model.AiProvider
import com.lottery.analyzer.ui.theme.BorderLight
import com.lottery.analyzer.ui.theme.GreenLight
import com.lottery.analyzer.ui.theme.GreenPrimary
import com.lottery.analyzer.ui.theme.RedLight
import com.lottery.analyzer.ui.theme.RedPrimary
import com.lottery.analyzer.ui.theme.TextPrimary
import com.lottery.analyzer.ui.theme.TextSecondary

/**
 * AI 大模型平台配置弹窗
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsDialog(
    config: AiConfig,
    isLoading: Boolean,
    testMessage: String?,
    onDismiss: () -> Unit,
    onSaveConfig: (AiConfig) -> Unit,
    onTestConnection: (AiConfig) -> Unit,
    onViewLogs: (() -> Unit)? = null
) {
    var selectedProvider by remember(config) { mutableStateOf(config.provider) }
    var baseUrl by remember(config) { mutableStateOf(config.baseUrl) }
    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var modelName by remember(config) { mutableStateOf(config.modelName) }
    var isKeyVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = "AI 大模型配置",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. 平台快速预设选择
                Text(
                    text = "选择大模型平台预设：",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AiProvider.values().forEach { provider ->
                        val isSelected = provider == selectedProvider
                        val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFF1F5F9)
                        val textColor = if (isSelected) Color.White else TextPrimary
                        val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else BorderLight

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(bgColor)
                                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedProvider = provider
                                    baseUrl = provider.defaultBaseUrl
                                    modelName = provider.defaultModel
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = provider.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = textColor
                            )
                        }
                    }
                }

                Text(
                    text = selectedProvider.note,
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(2.dp))

                // 2. API Key 输入框
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key (密钥)") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isKeyVisible) "隐藏密码" else "显示密码",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )

                // 3. API 终端地址 Base URL
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("接口 Base URL") },
                    placeholder = { Text("https://api.deepseek.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 4. 模型名称
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("模型名称 (Model)") },
                    placeholder = { Text("deepseek-chat / qwen-plus") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 5. 连通性测试按钮与反馈展示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val cur = AiConfig(
                                provider = selectedProvider,
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                modelName = modelName
                            )
                            onTestConnection(cur)
                        },
                        enabled = !isLoading && apiKey.isNotBlank() && baseUrl.isNotBlank()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("测试中...", fontSize = 12.sp)
                        } else {
                            Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("测试连通性", fontSize = 12.sp)
                        }
                    }

                    Text(
                        text = "OpenAI 兼容协议",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                // 测试结果反馈条
                if (!testMessage.isNullOrBlank()) {
                    val isSuccess = testMessage.startsWith("✅")
                    val bg = if (isSuccess) GreenLight else RedLight
                    val textColor = if (isSuccess) GreenPrimary else RedPrimary

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = testMessage,
                            fontSize = 11.sp,
                            color = textColor,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newConfig = AiConfig(
                        provider = selectedProvider,
                        baseUrl = baseUrl.trim(),
                        apiKey = apiKey.trim(),
                        modelName = modelName.trim()
                    )
                    onSaveConfig(newConfig)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("保存配置")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onViewLogs != null) {
                    TextButton(onClick = onViewLogs) {
                        Text("查看日志", fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("取消")
                }
            }
        }
    )
}
