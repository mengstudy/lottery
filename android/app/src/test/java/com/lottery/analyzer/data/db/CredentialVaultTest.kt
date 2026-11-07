package com.lottery.analyzer.data.db

import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class CredentialVaultTest {
    private class Storage : SecretStorage {
        val values=mutableMapOf<String,String>()
        override fun get(key:String)=values[key]
        override fun put(key:String,value:String){values[key]=value}
        override fun remove(key:String){values.remove(key)}
    }
    private fun cipher():SecretCipher {
        val key=KeyGenerator.getInstance("AES").apply{init(128)}.generateKey()
        return AesGcmSecretCipher {key}
    }
    @Test fun providerAndEndpointCredentialsAreIsolatedAndEncrypted(){
        val storage=Storage();val vault=CredentialVault(storage,cipher())
        val a=CredentialScope.id("A","https://example.com/v1")
        val b=CredentialScope.id("B","https://example.com/v1")
        val c=CredentialScope.id("A","https://elsewhere.com/v1")
        vault.write(a,"test-secret")
        assertEquals("test-secret",vault.read(a));assertEquals("",vault.read(b));assertEquals("",vault.read(c))
        assertFalse(storage.values.values.any{it.contains("test-secret")})
        assertEquals(a,CredentialScope.id("A","https://EXAMPLE.com/v1/"))
    }
    @Test fun gcmUsesFreshIvAndRejectsWrongKey(){
        val cipher=cipher();val first=cipher.encrypt("secret");val second=cipher.encrypt("secret")
        assertNotEquals(first,second);assertEquals("secret",cipher.decrypt(first))
        assertTrue(runCatching{cipher().decrypt(first)}.isFailure)
    }
    @Test fun damagedOrRestoredCiphertextIsRemovedWithoutReturningWrongSecret(){
        val storage=Storage();storage.values["scope"]="broken"
        val vault=CredentialVault(storage,cipher())
        assertEquals("",vault.read("scope"));assertTrue(vault.recoveryRequired);assertFalse(storage.values.containsKey("scope"))
    }
    @Test fun blankKeyClearsOnlyItsOwnScope(){
        val storage=Storage();val vault=CredentialVault(storage,cipher())
        vault.write("a","first");vault.write("b","second");vault.write("a","")
        assertEquals("",vault.read("a"));assertEquals("second",vault.read("b"))
    }
}
