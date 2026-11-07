package com.lottery.analyzer.data.db

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import android.security.keystore.KeyPermanentlyInvalidatedException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class KeystoreSecretCipher : SecretCipher {
    companion object { private const val ALIAS = "lottery_ai_credentials_v1"; private val lock = Any() }

    private fun key(): SecretKey = synchronized(lock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private val cipher = AesGcmSecretCipher(::key)
    private fun resetKey() = synchronized(lock) {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(ALIAS) }
    }

    override fun encrypt(value: String): String = try { cipher.encrypt(value) }
    catch (e: KeyPermanentlyInvalidatedException) { resetKey(); cipher.encrypt(value) }
    catch (e: UnrecoverableKeyException) { resetKey(); cipher.encrypt(value) }

    override fun decrypt(value: String): String = try { cipher.decrypt(value) }
    catch (e: KeyPermanentlyInvalidatedException) { resetKey(); throw e }
    catch (e: UnrecoverableKeyException) { resetKey(); throw e }
}
