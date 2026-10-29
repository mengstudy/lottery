package com.lottery.analyzer.data.db

import android.content.Context
import android.content.SharedPreferences
import com.lottery.analyzer.data.model.AiConfig
import com.lottery.analyzer.data.model.AiProvider

/**
 * AI 参数配置持久化管理类 (基于 SharedPreferences)
 */
class AiConfigPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "ai_lottery_preferences"
        private const val KEY_PROVIDER = "ai_provider"
        private const val KEY_BASE_URL = "ai_base_url"
        private const val KEY_API_KEY = "ai_api_key"
        private const val KEY_MODEL_NAME = "ai_model_name"
        private const val KEY_TEMPERATURE = "ai_temperature"
    }

    /**
     * 读取 AI 配置，若无则使用默认 DeepSeek 预设
     */
    fun getAiConfig(): AiConfig {
        val providerStr = prefs.getString(KEY_PROVIDER, AiProvider.DEEPSEEK.name) ?: AiProvider.DEEPSEEK.name
        val provider = try {
            AiProvider.valueOf(providerStr)
        } catch (_: Exception) {
            AiProvider.DEEPSEEK
        }

        val baseUrl = prefs.getString(KEY_BASE_URL, provider.defaultBaseUrl) ?: provider.defaultBaseUrl
        val apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        val modelName = prefs.getString(KEY_MODEL_NAME, provider.defaultModel) ?: provider.defaultModel
        val temperature = prefs.getFloat(KEY_TEMPERATURE, 0.7f)

        return AiConfig(
            provider = provider,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelName = modelName,
            temperature = temperature
        )
    }

    /**
     * 保存 AI 配置
     */
    fun saveAiConfig(config: AiConfig) {
        prefs.edit()
            .putString(KEY_PROVIDER, config.provider.name)
            .putString(KEY_BASE_URL, config.baseUrl)
            .putString(KEY_API_KEY, config.apiKey)
            .putString(KEY_MODEL_NAME, config.modelName)
            .putFloat(KEY_TEMPERATURE, config.temperature)
            .apply()
    }

    /**
     * 快捷切换平台预设并保存
     */
    fun selectProviderPreset(provider: AiProvider): AiConfig {
        val currentKey = prefs.getString(KEY_API_KEY, "") ?: ""
        val newConfig = AiConfig(
            provider = provider,
            baseUrl = provider.defaultBaseUrl,
            apiKey = currentKey,
            modelName = provider.defaultModel,
            temperature = 0.7f
        )
        saveAiConfig(newConfig)
        return newConfig
    }
}
