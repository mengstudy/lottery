package com.lottery.analyzer.data.model

/**
 * 支持的 AI 平台预设
 */
enum class AiProvider(
    val title: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val note: String
) {
    DEEPSEEK(
        title = "DeepSeek (深度求索)",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat",
        note = "性价比与推理能力极强，推荐使用"
    ),
    SILICONFLOW(
        title = "硅基流动 (SiliconFlow)",
        defaultBaseUrl = "https://api.siliconflow.cn/v1",
        defaultModel = "deepseek-ai/DeepSeek-V3",
        note = "支持主流高并发开箱即用"
    ),
    QWEN(
        title = "阿里通义千问 (DashScope)",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultModel = "qwen-plus",
        note = "阿里云通义千问兼容接口"
    ),
    OPENAI(
        title = "OpenAI 官方",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        note = "官方 OpenAI 接口 (需要外网环境)"
    ),
    CUSTOM(
        title = "自定义 (OpenAI 协议)",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-3.5-turbo",
        note = "任意兼容 OpenAI 规范的第三方中转 API"
    )
}

/**
 * AI 参数配置
 */
data class AiConfig(
    val provider: AiProvider = AiProvider.DEEPSEEK,
    val baseUrl: String = AiProvider.DEEPSEEK.defaultBaseUrl,
    val apiKey: String = "",
    val modelName: String = AiProvider.DEEPSEEK.defaultModel,
    val temperature: Float = 0.7f
)

/**
 * AI 分析推荐模式
 */
enum class AiAnalysisMode(val title: String, val desc: String) {
    FULL_RECOMMEND(
        title = "全盘遗漏综合推演",
        desc = "结合最新形态指标、0~9+遗漏层级与冷热号，由AI自主推演出推荐方案"
    ),
    HEART_FUSION(
        title = "心水与他人频次融合推演",
        desc = "将您的心水红球作为偏好核心，结合他人高频热号与遗漏分布融合生成方案"
    ),
    DIAGNOSE_SELECTION(
        title = "自选方案 AI 诊断与评估",
        desc = "对您当前自选的心水号码进行和值、奇偶及遗漏层级健康度诊断，并给出微调优化建议"
    )
}

/**
 * AI 返回的推荐结果
 */
data class AiRecommendationResult(
    val mode: AiAnalysisMode,
    val rawResponse: String,
    val analysisText: String,
    val recommendedBets: List<Pair<List<Int>, List<Int>>>, // (红球, 蓝球)
    val strategySummary: String
)
