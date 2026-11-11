package com.lottery.analyzer.data.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lottery.analyzer.data.model.AiAnalysisMode
import com.lottery.analyzer.data.model.AiConfig
import com.lottery.analyzer.data.model.AiRecommendationResult
import com.lottery.analyzer.data.model.AiRecommendationRequest
import com.lottery.analyzer.data.model.AiRecommendationClient
import com.lottery.analyzer.data.model.AiWindowContext
import com.lottery.analyzer.data.model.IssueAnalysis
import com.lottery.analyzer.data.model.Kl8PlayType
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.util.AppLogger
import com.lottery.analyzer.util.executeAndReadString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * AI 大模型服务层，支持所有兼容 OpenAI /v1/chat/completions 规范的平台
 * (包括 DeepSeek, SiliconFlow, 阿里通义千问, OpenAI 以及自定义中转)
 */
class AiLotteryService : AiRecommendationClient {
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

            val (code, body) = client.newCall(request).executeAndReadString()
            val elapsed = System.currentTimeMillis() - startTime
            AppLogger.i(TAG, "收到测试响应: HTTP $code, 耗时: ${elapsed}ms")

            if (code !in 200..299) {
                val errMsg = try {
                    val json = gson.fromJson(body, JsonObject::class.java)
                    json.getAsJsonObject("error")?.get("message")?.asString ?: body
                } catch (_: Exception) {
                    body
                }
                AppLogger.e(TAG, "测试连接失败: HTTP $code, 错误: $errMsg")
                return@withContext Result.failure(AiHttpFailure(code, "HTTP $code: $errMsg"))
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
        } catch (e: CancellationException) {
            AppLogger.i(TAG, "测试连接被取消")
            throw e
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
    override suspend fun generateRecommendation(request: AiRecommendationRequest): Result<AiRecommendationResult> = withContext(Dispatchers.IO) {
        val input = request
        val config = input.config
        val lotteryType = input.lotteryType
        val analysis = input.analysis
        val targetNextIssue = input.targetNextIssue
        val mode = input.mode
        val betCount = input.betCount
        val kl8PlayType = input.kl8PlayType
        val startTime = System.currentTimeMillis()
        val endpoint = normalizeEndpointUrl(config.baseUrl)
        AppLogger.i(
            TAG,
            "发起 AI 推演: 彩种=[${lotteryType.title}], 目标期=[$targetNextIssue], 模式=[${mode.title}], 注数=[$betCount], 平台=[${config.provider.title}], 模型=[${config.modelName}]"
        )

        try {
            require(betCount in 1..20) { "推荐注数须为1~20" }
            require(analysis.lotteryType == lotteryType) { "分析彩种不匹配" }
            input.windowContext?.let {
                require(it.stats.type == lotteryType && it.stats.lastIssue == analysis.issue) { "频次与遗漏分析截止期不一致" }
            }
            if (config.apiKey.isBlank()) {
                AppLogger.w(TAG, "AI推演终止: 未配置 API Key")
                return@withContext Result.failure(IllegalStateException("请先配置并保存 AI API Key"))
            }

            val prompt = input.preparedPrompt ?: AiPromptBuilder.build(input)
            val systemPrompt = prompt.system

            val requestBodyMap = mapOf(
                "model" to config.modelName.trim(),
                "messages" to listOf(
                    mapOf("role" to "system", "content" to systemPrompt),
                    mapOf("role" to "user", "content" to prompt.user)
                ),
                "temperature" to config.temperature,
                "max_tokens" to 1200 // 限制生成长度，避免模型无限思考冗长文本导致超时
            )

            val jsonString = gson.toJson(requestBodyMap)
            val httpRequest = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
                .addHeader("Content-Type", "application/json")
                .post(jsonString.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            AppLogger.i(TAG, "请求正在发送至云端，等待模型推理...")
            ensureActive()
            val (code, body) = client.newCall(httpRequest).executeAndReadString()
            val responseReceivedAt = System.currentTimeMillis()
            val elapsed = System.currentTimeMillis() - startTime
            AppLogger.i(TAG, "收到云端推演响应: HTTP $code, 耗时: ${elapsed}ms")

            if (code !in 200..299) {
                val errMsg = try {
                    val json = gson.fromJson(body, JsonObject::class.java)
                    json.getAsJsonObject("error")?.get("message")?.asString ?: body
                } catch (_: Exception) {
                    body
                }
                AppLogger.e(TAG, "推演请求返回错误: HTTP $code, 内容: $errMsg")
                return@withContext Result.failure(AiHttpFailure(code, "HTTP $code: $errMsg"))
            }

            val content = try {
                val json = gson.fromJson(body, JsonObject::class.java)
                val choices = json.getAsJsonArray("choices")
                choices[0].asJsonObject.getAsJsonObject("message").get("content").asString
            } catch (e: Exception) {
                AppLogger.e(TAG, "解析大模型响应 JSON 失败", e)
                return@withContext Result.failure(Exception("解析大模型响应失败: ${e.message}\n原始响应: $body"))
            }

            val selectionResult = if (input.selectionContext == null) null else withContext(Dispatchers.Default) {
                val context = coroutineContext
                AiCandidateSelectionParser.parse(content,input){context.ensureActive()}
            }
            val validation = selectionResult?.validation ?: withContext(Dispatchers.Default) {
                val context = coroutineContext
                AiBetGuardrails.parse(content,input){context.ensureActive()}
            }
            val validBets = validation.bets
            val strategySummary = selectionResult?.summary ?: parseStrategySummary(content)
            AppLogger.i(TAG, "AI核验完成：请求${betCount}注，识别${validation.parsedCount}组，保留${validBets.size}注，剔除原因=${validation.rejectionReasons}")

            Result.success(
                AiRecommendationResult(
                    mode = mode,
                    targetIssue = targetNextIssue,
                    rawResponse = content,
                    analysisText = selectionResult?.explanation ?: content,
                    recommendedBets = validBets,
                    strategySummary = strategySummary,
                    lotteryType = lotteryType,
                    kl8PlayType = if (lotteryType == LotteryType.KL8) kl8PlayType else null,
                    requestedCount = betCount,
                    parsedCount = validation.parsedCount,
                    rejectionReasons = validation.rejectionReasons,
                    windowContext = input.windowContext,
                    baseIssue = analysis.issue,
                    providerName = config.provider.title,
                    modelName = config.modelName.trim(),
                    candidateAudit = validation.entries,
                    finishReason = runCatching { gson.fromJson(body, JsonObject::class.java).getAsJsonArray("choices")[0].asJsonObject.get("finish_reason")?.takeUnless { it.isJsonNull }?.asString }.getOrNull(),
                    responseReceivedAtEpoch = responseReceivedAt,
                    memoryContext = input.memoryContext,
                    selectionContext = input.selectionContext
                )
            )
        } catch (e: CancellationException) {
            AppLogger.i(TAG, "AI 推演任务被取消")
            throw e
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

}
