package com.kgapp.encryptionchat.data.crypto

import com.kgapp.encryptionchat.data.storage.FileStorage
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

class CryptoManager(private val storage: FileStorage) {
    companion object {
        private const val PRIVATE_HEADER = "-----BEGIN PRIVATE KEY-----"
        private const val PRIVATE_FOOTER = "-----END PRIVATE KEY-----"
        private const val PUBLIC_HEADER = "-----BEGIN PUBLIC KEY-----"
        private const val PUBLIC_FOOTER = "-----END PUBLIC KEY-----"
    }

    fun hasPrivateKey(): Boolean = storage.privateKeyFile().exists()

    fun hasPublicKey(): Boolean = storage.publicKeyFile().exists()

    fun hasKeyPair(): Boolean = hasPrivateKey() && hasPublicKey()

    fun generateKeyPair(): Boolean {
        storage.ensureKeyDirs()
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val keyPair = generator.generateKeyPair()
        val privatePem = pemEncode(PRIVATE_HEADER, PRIVATE_FOOTER, keyPair.private.encoded)
        val publicPem = pemEncode(PUBLIC_HEADER, PUBLIC_FOOTER, keyPair.public.encoded)
        storage.privateKeyFile().writeText(privatePem, Charsets.UTF_8)
        storage.publicKeyFile().writeText(publicPem, Charsets.UTF_8)
        return true
    }

    private fun pemEncode(header: String, footer: String, derBytes: ByteArray): String {
        val base64 = java.util.Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(derBytes)
        return buildString {
            append(header)
            append("\n")
            append(base64)
            append("\n")
            append(footer)
            append("\n")
        }
    }

    fun readPublicPemText(): String? = storage.readPublicPemText()

    fun importPrivatePem(pemText: String): Boolean {
        return try {
            val text = pemText.trim()
            if (!text.contains(PRIVATE_HEADER) || !text.contains(PRIVATE_FOOTER)) {
                return false
            }
            storage.ensureKeyDirs()
            storage.privateKeyFile().writeText(text + if (text.endsWith("\n")) "" else "\n", Charsets.UTF_8)
            true
        } catch (ex: Exception) {
            false
        }
    }

    fun importPublicPem(pemText: String): Boolean {
        return try {
            val text = pemText.trim()
            if (!text.contains(PUBLIC_HEADER) || !text.contains(PUBLIC_FOOTER)) {
                return false
            }
            storage.ensureKeyDirs()
            storage.publicKeyFile().writeText(text + if (text.endsWith("\n")) "" else "\n", Charsets.UTF_8)
            true
        } catch (ex: Exception) {
            false
        }
    }

    fun computePemBase64(): String? {
        val pem = readPublicPemText() ?: return null
        val trimmed = pem.trimEnd('\n')
        return java.util.Base64.getEncoder().encodeToString(trimmed.toByteArray(Charsets.UTF_8))
    }

    fun computeSelfName(): String? {
        val pemB64 = computePemBase64() ?: return null
        return md5Hex(pemB64.toByteArray(Charsets.UTF_8))
    }

    fun md5Hex(input: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(input)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun encryptWithPublicPemBase64(pubPemBase64: String, plain: String): String {
        return try {
            val pemBytes = java.util.Base64.getDecoder().decode(pubPemBase64)
            val publicKey = loadPublicKeyFromPemBytes(pemBytes)
            val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            val spec = OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
            )
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, spec)
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            java.util.Base64.getEncoder().encodeToString(encrypted)
        } catch (ex: Exception) {
            ""
        }
    }

    fun encryptBytesWithPublicPemBase64(pubPemBase64: String, plainBytes: ByteArray): String {
        return try {
            val pemBytes = java.util.Base64.getDecoder().decode(pubPemBase64)
            val publicKey = loadPublicKeyFromPemBytes(pemBytes)
            val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            val spec = OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
            )
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, spec)
            val encrypted = cipher.doFinal(plainBytes)
            java.util.Base64.getEncoder().encodeToString(encrypted)
        } catch (ex: Exception) {
            ""
        }
    }

    fun decryptBytesFromBase64(ciphertextBase64: String): ByteArray? {
        return try {
            val privateKey = loadPrivateKey() ?: return null
            val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            val spec = OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
            )
            cipher.init(Cipher.DECRYPT_MODE, privateKey, spec)
            val cipherBytes = java.util.Base64.getDecoder().decode(ciphertextBase64)
            cipher.doFinal(cipherBytes)
        } catch (ex: Exception) {
            null
        }
    }

    fun decryptText(ciphertextBase64: String): String {
        return try {
            val privateKey = loadPrivateKey() ?: return "解密失败"
            val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            val spec = OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
            )
            cipher.init(Cipher.DECRYPT_MODE, privateKey, spec)
            val cipherBytes = java.util.Base64.getDecoder().decode(ciphertextBase64)
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (ex: Exception) {
            "解密失败"
        }
    }

    fun signDataJson(dataJson: String): String {
        val privateKey = loadPrivateKey() ?: return ""
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(dataJson.toByteArray(Charsets.UTF_8))
        val sigBytes = signature.sign()
        return java.util.Base64.getEncoder().encodeToString(sigBytes)
    }

    fun buildCanonicalDataJson(data: Any?): String = ProtocolCanonicalizer.buildCanonicalDataJson(data)

    private fun loadPrivateKey(): PrivateKey? {
        val pemBytes = storage.readPrivatePemBytes() ?: return null
        return try {
            val derBytes = parsePem(pemBytes, PRIVATE_HEADER, PRIVATE_FOOTER)
            val keySpec = PKCS8EncodedKeySpec(derBytes)
            KeyFactory.getInstance("RSA").generatePrivate(keySpec)
        } catch (ex: Exception) {
            null
        }
    }

    private fun loadPublicKeyFromPemBytes(pemBytes: ByteArray): PublicKey {
        val derBytes = parsePem(pemBytes, PUBLIC_HEADER, PUBLIC_FOOTER)
        val keySpec = X509EncodedKeySpec(derBytes)
        return KeyFactory.getInstance("RSA").generatePublic(keySpec)
    }

    private fun parsePem(pemBytes: ByteArray, header: String, footer: String): ByteArray {
        val text = String(pemBytes, StandardCharsets.UTF_8)
        val normalized = text
            .replace(header, "")
            .replace(footer, "")
            .replace("\r", "")
            .replace("\n", "")
            .trim()
        return java.util.Base64.getDecoder().decode(normalized)
    }

}
