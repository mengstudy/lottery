package com.lottery.analyzer.data.db

import android.content.Context
import com.lottery.analyzer.data.model.AiConfig
import com.lottery.analyzer.data.model.AiProvider
import com.lottery.analyzer.util.AppLogger

interface AiSettingsStore {
    fun getAiConfig(): AiConfig
    fun saveAiConfig(config: AiConfig)
    fun selectProviderPreset(provider: AiProvider): AiConfig
}

class AiConfigPreferences(context: Context) : AiSettingsStore {
    private val prefs = context.applicationContext.getSharedPreferences("ai_lottery_preferences", Context.MODE_PRIVATE)
    private val secrets = context.applicationContext.getSharedPreferences("ai_lottery_credentials", Context.MODE_PRIVATE)
    private val vault = CredentialVault(object : SecretStorage {
        override fun get(key: String) = secrets.getString(key, null)
        override fun put(key: String, value: String) { check(secrets.edit().putString(key, value).commit()) { "密钥保存失败" } }
        override fun remove(key: String) { check(secrets.edit().remove(key).commit()) { "密钥清理失败" } }
    }, KeystoreSecretCipher())

    @Synchronized override fun getAiConfig(): AiConfig {
        val provider = runCatching { AiProvider.valueOf(prefs.getString("ai_provider", AiProvider.DEEPSEEK.name)!!) }.getOrDefault(AiProvider.DEEPSEEK)
        val url = prefs.getString("ai_base_url", provider.defaultBaseUrl) ?: provider.defaultBaseUrl
        val scope = CredentialScope.id(provider.name, url)
        // Encrypt legacy credentials before deleting their plaintext copy. Never log secret material.
        if (prefs.contains("ai_api_key")) {
            val legacy = prefs.getString("ai_api_key", "").orEmpty()
            if (legacy.isNotBlank()) vault.write(scope, legacy)
            check(prefs.edit().remove("ai_api_key").commit()) { "旧密钥迁移失败" }
        }
        val key = vault.read(scope)
        if (vault.recoveryRequired) AppLogger.w("AiConfigPreferences", "密钥无法解密，已清理损坏密文，请重新配置")
        return AiConfig(provider, url, key,
            prefs.getString("ai_model_name", provider.defaultModel) ?: provider.defaultModel,
            prefs.getFloat("ai_temperature", 0.7f))
    }

    @Synchronized override fun saveAiConfig(config: AiConfig) {
        CredentialScope.requireEndpoint(config.baseUrl)
        require(config.modelName.isNotBlank()) { "模型名称不能为空" }
        vault.write(CredentialScope.id(config.provider.name, config.baseUrl), config.apiKey.trim())
        check(prefs.edit().putString("ai_provider", config.provider.name)
            .putString("ai_base_url", config.baseUrl.trim()).putString("ai_model_name", config.modelName.trim())
            .putFloat("ai_temperature", config.temperature).remove("ai_api_key").commit()) { "AI配置保存失败" }
    }

    @Synchronized override fun selectProviderPreset(provider: AiProvider): AiConfig {
        val current = getAiConfig()
        val config = AiConfig(provider, provider.defaultBaseUrl,
            vault.read(CredentialScope.id(provider.name, provider.defaultBaseUrl)), provider.defaultModel, current.temperature)
        saveAiConfig(config)
        return config
    }
}
