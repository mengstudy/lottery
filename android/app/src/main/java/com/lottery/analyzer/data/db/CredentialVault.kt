package com.lottery.analyzer.data.db

import java.net.URI
import java.security.MessageDigest
import java.util.Locale

interface SecretCipher { fun encrypt(value: String): String; fun decrypt(value: String): String }
interface SecretStorage { fun get(key: String): String?; fun put(key: String, value: String); fun remove(key: String) }

object CredentialScope {
    fun requireEndpoint(baseUrl: String) {
        val uri = URI(baseUrl.trim())
        require(uri.scheme?.lowercase(Locale.ROOT) in setOf("https", "http") && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.query == null && uri.fragment == null) { "请输入合法的 HTTP(S) 接口地址，不包含账号、查询参数或片段" }
    }

    fun id(provider: String, baseUrl: String): String {
        val normalized = runCatching {
            val uri = URI(baseUrl.trim()).normalize()
            require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null)
            "${uri.scheme.lowercase(Locale.ROOT)}://${uri.host.lowercase(Locale.ROOT)}:${uri.port}${uri.path.orEmpty().trimEnd('/')}"
        }.getOrElse { baseUrl.trim().trimEnd('/') }
        return MessageDigest.getInstance("SHA-256").digest("$provider|$normalized".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 255) }
    }
}

class CredentialVault(private val storage: SecretStorage, private val cipher: SecretCipher) {
    var recoveryRequired: Boolean = false
        private set

    @Synchronized fun read(scope: String): String {
        val value = storage.get(scope) ?: return ""
        return try { cipher.decrypt(value) }
        catch (_: Exception) {
            storage.remove(scope)
            recoveryRequired = true
            ""
        }
    }

    @Synchronized fun write(scope: String, secret: String) {
        if (secret.isBlank()) storage.remove(scope)
        else storage.put(scope, cipher.encrypt(secret))
    }
}

/** AES-GCM implementation shared by Android Keystore and JVM verification. */
class AesGcmSecretCipher(private val key: () -> javax.crypto.SecretKey) : SecretCipher {
    override fun encrypt(value: String): String {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply { init(javax.crypto.Cipher.ENCRYPT_MODE, key()) }
        val encoder = java.util.Base64.getEncoder()
        return "v1:" + encoder.encodeToString(cipher.iv) + ":" + encoder.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
    }
    override fun decrypt(value: String): String {
        val parts = value.split(':')
        require(parts.size == 3 && parts[0] == "v1") { "Unsupported credential format" }
        val decoder = java.util.Base64.getDecoder()
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(javax.crypto.Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, decoder.decode(parts[1])))
        }
        return String(cipher.doFinal(decoder.decode(parts[2])), Charsets.UTF_8)
    }
}
