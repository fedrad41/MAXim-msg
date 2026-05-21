package com.example.data

import android.util.Base64
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptographyHelper {
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256
    private val SALT = "MAXIM_SECURE_SALT_2026".toByteArray(Charsets.UTF_8)
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val PREFIX = "ENC:"

    @Volatile
    private var activeSecretKeySpec: SecretKeySpec? = null

    fun deriveKey(password: String) {
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec: KeySpec = PBEKeySpec(password.toCharArray(), SALT, ITERATIONS, KEY_LENGTH)
            val tmp = factory.generateSecret(spec)
            activeSecretKeySpec = SecretKeySpec(tmp.encoded, ALGORITHM)
        } catch (e: Exception) {
            try {
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                val spec: KeySpec = PBEKeySpec(password.toCharArray(), SALT, ITERATIONS, KEY_LENGTH)
                val tmp = factory.generateSecret(spec)
                activeSecretKeySpec = SecretKeySpec(tmp.encoded, ALGORITHM)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun clearKey() {
        activeSecretKeySpec = null
    }

    fun isKeyAvailable(): Boolean = activeSecretKeySpec != null

    fun encrypt(plainText: String): String {
        val keySpec = activeSecretKeySpec ?: return plainText
        if (plainText.startsWith(PREFIX)) return plainText
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secureRandom = SecureRandom()
            val iv = ByteArray(16)
            secureRandom.nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combinedBytes = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combinedBytes, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combinedBytes, iv.size, encryptedBytes.size)

            val base64Cipher = Base64.encodeToString(combinedBytes, Base64.NO_WRAP)
            "$PREFIX$base64Cipher"
        } catch (e: Exception) {
            e.printStackTrace()
            plainText
        }
    }

    fun decrypt(cipherText: String): String {
        val keySpec = activeSecretKeySpec ?: return cipherText
        if (!cipherText.startsWith(PREFIX)) return cipherText
        return try {
            val base64Payload = cipherText.substring(PREFIX.length)
            val combinedBytes = Base64.decode(base64Payload, Base64.NO_WRAP)
            
            if (combinedBytes.size < 16) return cipherText
            
            val iv = ByteArray(16)
            System.arraycopy(combinedBytes, 0, iv, 0, 16)
            
            val encryptedBytes = ByteArray(combinedBytes.size - 16)
            System.arraycopy(combinedBytes, 16, encryptedBytes, 0, encryptedBytes.size)
            
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            
            val decodedBytes = cipher.doFinal(encryptedBytes)
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "[DECRYPTION_FAILED]"
        }
    }
}
