package com.lottery.analyzer.data.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lottery.analyzer.data.model.AiAnalysisMode
import com.lottery.analyzer.data.model.AiConfig
import com.lottery.analyzer.data.model.AiRecommendationResult
import com.lottery.analyzer.data.model.IssueAnalysis
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * AI 大模型服务层，支持所有兼容 OpenAI /v1/chat/completions 规范的平台
 * (包括 DeepSeek, SiliconFlow, 阿里通义千问, OpenAI 以及自定义中转)
 */
class AiLotteryService {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)   // 云端深度推演预留充裕时间，避免高并发时 timeout
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        private const val TAG = "AiLotteryService"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * 规范化 API 终端地址
     */
    fun normalizeEndpointUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().removeSuffix("/")
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            else -> "$trimmed/v1/chat/completions"
        }
    }

    /**
     * 测试大模型 API 连通性
     */
    suspend fun testConnection(config: AiConfig): Result<String> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val endpoint = normalizeEndpointUrl(config.baseUrl)
        AppLogger.i(TAG, "开始测试连接: 平台=[${config.provider.title}], 接口=[$endpoint], 模型=[${config.modelName}]")

        try {
            if (config.baseUrl.isBlank()) {
                AppLogger.w(TAG, "测试中断: API 接口地址为空")
                return@withContext Result.failure(IllegalArgumentException("API 接口地址不能为空"))
            }
            if (config.apiKey.isBlank()) {
                AppLogger.w(TAG, "测试中断: API Key 为空")
                return@withContext Result.failure(IllegalArgumentException("API Key 不能为空，请先填写"))
            }

            val requestBodyMap = mapOf(
                "model" to config.modelName.trim(),
                "messages" to listOf(
                    mapOf("role" to "system", "content" to "You are a helpful assistant."),
                    mapOf("role" to "user", "content" to "请直接回复：OK")
                ),
                "temperature" to 0.3,
                "max_tokens" to 30
            )

            val jsonString = gson.toJson(requestBodyMap)
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
                .addHeader("Content-Type", "application/json")
                .post(jsonString.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - startTime
                val body = response.body?.string().orEmpty()
                AppLogger.i(TAG, "收到测试响应: HTTP ${response.code}, 耗时: ${elapsed}ms")

                if (!response.isSuccessful) {
                    val errMsg = try {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        json.getAsJsonObject("error")?.get("message")?.asString ?: body
                    } catch (_: Exception) {
                        body
                    }
                    AppLogger.e(TAG, "测试连接失败: HTTP ${response.code}, 错误: $errMsg")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errMsg"))
                }

                val reply = try {
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val choices = json.getAsJsonArray("choices")
                    if (choices != null && choices.size() > 0) {
                        choices[0].asJsonObject.getAsJsonObject("message").get("content").asString
                    } else {
                        "响应成功但内容为空"
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "解析测试响应JSON失败", e)
                    "连接成功，但解析响应失败: ${e.message}"
                }

                AppLogger.i(TAG, "测试连接成功! 耗时: ${elapsed}ms, 模型回复: ${reply.trim()}")
                Result.success(reply.trim())
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            AppLogger.e(TAG, "测试连接异常 (耗时 ${elapsed}ms): ${e.message}", e)
            val userFriendlyMsg = when (e) {
                is SocketTimeoutException -> "测试超时：无法在规定时间内连通接口，请检查网络或更换 Base URL"
                is UnknownHostException -> "网络解析失败：无法找到域名主机，请检查接口地址"
                else -> e.message ?: "连接异常"
            }
            Result.failure(Exception(userFriendlyMsg, e))
        }
    }

    /**
     * 生成 AI 模拟选号与分析
     */
    suspend fun generateRecommendation(
        config: AiConfig,
        lotteryType: LotteryType,
        analysis: IssueAnalysis,
        targetNextIssue: String,
        mode: AiAnalysisMode,
        userSelection: Set<Int>,
        topOtherBalls: List<Pair<Int, Int>> = emptyList(),
        betCount: Int = 3
    ): Result<AiRecommendationResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val endpoint = normalizeEndpointUrl(config.baseUrl)
        AppLogger.i(
            TAG,
            "发起 AI 推演: 彩种=[${lotteryType.title}], 目标期=[$targetNextIssue], 模式=[${mode.title}], 注数=[$betCount], 平台=[${config.provider.title}], 模型=[${config.modelName}]"
        )

        try {
            if (config.apiKey.isBlank()) {
                AppLogger.w(TAG, "AI推演终止: 未配置 API Key")
                return@withContext Result.failure(IllegalStateException("请先配置并保存 AI API Key"))
            }

            val prompt = buildAnalysisPrompt(
                lotteryType = lotteryType,
                analysis = analysis,
                targetNextIssue = targetNextIssue,
                mode = mode,
                userSelection = userSelection,
                topOtherBalls = topOtherBalls,
                betCount = betCount
            )
            AppLogger.d(TAG, "已构建提示词，长度: ${prompt.length} 字符")

            val systemPrompt = """
                你是一位深耕国家级概率统计学、离散组合数学与彩票数字规律的资深分析专家。
                请基于用户提供的历史开奖数据、形态指标、0~9+各层遗漏号码池以及他人偏好，进行严谨客观的推演。
                【执行要求】：
                1. 声明彩票开奖属于随机事件，推演分析仅基于统计学形态与离散数学偏好，请理性购彩。
                2. 论述逻辑请务必【简明扼要，直奔主题】（建议控制在 200 字以内），重点说明奇偶比、和值及 0~9+ 遗漏层级的动态平衡。
                3. 随后严格输出标准结构的推荐号码，确保格式严格对齐如下：
                   【推荐第1注】红球: 02 08 15 21 26 30 + 蓝球: 06
                   【推荐第2注】红球: 05 12 18 23 27 33 + 蓝球: 14
                   若是快乐8（无蓝球）：
                   【推荐第1注】红球: 03 08 15 22 29 35 48 57 66 79
                4. 最后附带一句话【策略总结】概括本次选号的核心偏好（如：偏热码回补、奇偶3:3中轴形态等）。
            """.trimIndent()

            val requestBodyMap = mapOf(
                "model" to config.modelName.trim(),
                "messages" to listOf(
                    mapOf("role" to "system", "content" to systemPrompt),
                    mapOf("role" to "user", "content" to prompt)
                ),
                "temperature" to config.temperature,
                "max_tokens" to 1200 // 限制生成长度，避免模型无限思考冗长文本导致超时
            )

            val jsonString = gson.toJson(requestBodyMap)
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
                .addHeader("Content-Type", "application/json")
                .post(jsonString.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            AppLogger.i(TAG, "请求正在发送至云端，等待模型推理...")
            client.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - startTime
                val body = response.body?.string().orEmpty()
                AppLogger.i(TAG, "收到云端推演响应: HTTP ${response.code}, 耗时: ${elapsed}ms")

                if (!response.isSuccessful) {
                    val errMsg = try {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        json.getAsJsonObject("error")?.get("message")?.asString ?: body
                    } catch (_: Exception) {
                        body
                    }
                    AppLogger.e(TAG, "推演请求返回错误: HTTP ${response.code}, 内容: $errMsg")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errMsg"))
                }

                val content = try {
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val choices = json.getAsJsonArray("choices")
                    choices[0].asJsonObject.getAsJsonObject("message").get("content").asString
                } catch (e: Exception) {
                    AppLogger.e(TAG, "解析大模型响应 JSON 失败", e)
                    return@withContext Result.failure(Exception("解析大模型响应失败: ${e.message}\n原始响应: $body"))
                }

                // 从生成文本中抽取推荐号码
                val extractedBets = parseRecommendedBets(content, lotteryType)
                val strategySummary = parseStrategySummary(content)
                AppLogger.i(TAG, "推演成功完成! 耗时: ${elapsed}ms, 抽取出 ${extractedBets.size} 注推荐, 策略: $strategySummary")

                Result.success(
                    AiRecommendationResult(
                        mode = mode,
                        rawResponse = content,
                        analysisText = content,
                        recommendedBets = extractedBets,
                        strategySummary = strategySummary
                    )
                )
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            AppLogger.e(TAG, "推演过程发生异常 (耗时 ${elapsed}ms): ${e.message}", e)
            val userFriendlyMsg = when (e) {
                is SocketTimeoutException -> "推演超时：云端大模型响应耗时较长（超过等待上限），可能该平台节点繁忙，建议稍后重试或切换轻量模型"
                is UnknownHostException -> "网络连接异常：无法访问 API 域名，请检查手机网络设置"
                else -> e.message ?: "AI 生成异常"
            }
            Result.failure(Exception(userFriendlyMsg, e))
        }
    }

    /**
     * 构建结构化的选号与诊断提示词
     */
    private fun buildAnalysisPrompt(
        lotteryType: LotteryType,
        analysis: IssueAnalysis,
        targetNextIssue: String,
        mode: AiAnalysisMode,
        userSelection: Set<Int>,
        topOtherBalls: List<Pair<Int, Int>>,
        betCount: Int
    ): String {
        val sb = StringBuilder()
        sb.appendLine("【彩种】: ${lotteryType.title} (${lotteryType.subtitle})")
        sb.appendLine("【分析基准期】: 第 ${analysis.issue} 期 (开奖日: ${analysis.drawDate})")
        sb.appendLine("【下期目标期号】: 第 $targetNextIssue 期")
        sb.appendLine("【最新开出号码】:")
        sb.append("  红球: ").appendLine(analysis.redBalls.joinToString(" ") { String.format("%02d", it) })
        if (analysis.blueBalls.isNotEmpty()) {
            sb.append("  蓝球: ").appendLine(analysis.blueBalls.joinToString(" ") { String.format("%02d", it) })
        }
        sb.appendLine("【形态统计指标】:")
        sb.appendLine("  和值: ${analysis.sumValue} | 跨度: ${analysis.spanValue} | 奇偶比: ${analysis.oddEvenRatio} | 区间比: ${analysis.zoneRatio}")
        if (analysis.consecutivePairs.isNotEmpty()) {
            sb.appendLine("  连号: ${analysis.consecutivePairs.joinToString(", ")}")
        }
        sb.appendLine("  冷温热分布: 热号(${analysis.hotNumbers.size}个), 温号(${analysis.warmNumbers.size}个), 冷号(${analysis.coldNumbers.size}个)")

        sb.appendLine("\n【下期目标期 0~9+ 遗漏层级模拟分布】:")
        for (group in analysis.nextSimulatedGroups) {
            val balls = group.allNumbers.joinToString(" ") { String.format("%02d", it) }
            val label = when (group.level) {
                "0" -> "0层 (重号候选)"
                "1" -> "1层 (活跃号)"
                "9+" -> "9+层 (深冷号)"
                else -> "${group.level}层"
            }
            sb.appendLine("  $label: [${group.allNumbers.size}码] $balls")
        }

        if (userSelection.isNotEmpty()) {
            sb.appendLine("\n【用户当前自选心水红球】:")
            sb.appendLine("  " + userSelection.sorted().joinToString(" ") { String.format("%02d", it) } + " (共${userSelection.size}个)")
        }

        if (topOtherBalls.isNotEmpty()) {
            sb.appendLine("\n【工具箱他人关注购买频次 Top 10】:")
            val rankList = topOtherBalls.take(10).joinToString(", ") { "${String.format("%02d", it.first)}(${it.second}次)" }
            sb.appendLine("  $rankList")
        }

        sb.appendLine("\n【本次任务模式】: ${mode.title}")
        when (mode) {
            AiAnalysisMode.FULL_RECOMMEND -> {
                sb.appendLine("请综合历史形态走势、冷温热平衡与 0~9+ 遗漏层级分布，精简分析后为下期生成 $betCount 注推荐号码。")
            }
            AiAnalysisMode.HEART_FUSION -> {
                sb.appendLine("请重点以用户选择的心水红球作为核心或胆码，结合他人高频球号及遗漏轮动补充其余红蓝球，精简分析后生成 $betCount 注方案。")
            }
            AiAnalysisMode.DIAGNOSE_SELECTION -> {
                sb.appendLine("请对用户当前自选的心水号码进行全方位健康度诊断（分析和值、奇偶比、连号及遗漏层级结构是否合理），随后给出 1~2 注微调优化方案。")
            }
        }

        sb.appendLine("\n请在 200 字内简述推演思路，随后立即输出标准格式推荐号码与一句话【策略总结】。")
        return sb.toString()
    }

    /**
     * 解析推荐号码
     */
    private fun parseRecommendedBets(text: String, lotteryType: LotteryType): List<Pair<List<Int>, List<Int>>> {
        val result = mutableListOf<Pair<List<Int>, List<Int>>>()
        val lines = text.lines()

        val structuredRegex = Pattern.compile(
            """【推荐第\s*\d+\s*注】.*?红球[:：]\s*([\d\s,，]+?)(?:\s*\+\s*蓝球[:：]\s*([\d\s,，]+))?${'$'}""",
            Pattern.CASE_INSENSITIVE
        )

        for (line in lines) {
            val matcher = structuredRegex.matcher(line.trim())
            if (matcher.find()) {
                val redPart = matcher.group(1).orEmpty()
                val bluePart = matcher.group(2).orEmpty()

                val redBalls = extractNumbers(redPart, 1, lotteryType.maxRedNumber)
                val blueBalls = if (lotteryType.maxBlueNumber > 0) {
                    extractNumbers(bluePart, 1, lotteryType.maxBlueNumber)
                } else emptyList()

                if (redBalls.isNotEmpty()) {
                    result.add(Pair(redBalls.sorted(), blueBalls.sorted()))
                }
            }
        }

        // 备用兜底：如果模型没有按【推荐第X注】格式输出，按行通用提取类似 "02 08 15 21 26 30 + 06" 的行
        if (result.isEmpty()) {
            for (line in lines) {
                if (line.contains("+")) {
                    val parts = line.split("+")
                    val reds = extractNumbers(parts[0], 1, lotteryType.maxRedNumber)
                    val blues = if (parts.size > 1 && lotteryType.maxBlueNumber > 0) {
                        extractNumbers(parts[1], 1, lotteryType.maxBlueNumber)
                    } else emptyList()

                    if (reds.size >= (if (lotteryType == LotteryType.KL8) 10 else 5)) {
                        result.add(Pair(reds.sorted(), blues.sorted()))
                    }
                } else if (lotteryType == LotteryType.KL8) {
                    val reds = extractNumbers(line, 1, lotteryType.maxRedNumber)
                    if (reds.size == 10) {
                        result.add(Pair(reds.sorted(), emptyList()))
                    }
                }
            }
        }

        return result
    }

    /**
     * 提取策略总结
     */
    private fun parseStrategySummary(text: String): String {
        val regex = Pattern.compile("""【策略总结】[:：]?\s*(.*)""")
        for (line in text.lines()) {
            val matcher = regex.matcher(line.trim())
            if (matcher.find()) {
                val summary = matcher.group(1)?.trim()
                if (!summary.isNullOrBlank()) return summary
            }
        }
        return "AI 大模型多维统计综合推演"
    }

    private fun extractNumbers(text: String, min: Int, max: Int): List<Int> {
        val numPattern = Pattern.compile("""\b\d{1,2}\b""")
        val matcher = numPattern.matcher(text)
        val nums = mutableSetOf<Int>()
        while (matcher.find()) {
            val n = matcher.group().toIntOrNull()
            if (n != null && n in min..max) {
                nums.add(n)
            }
        }
        return nums.toList()
    }
}
