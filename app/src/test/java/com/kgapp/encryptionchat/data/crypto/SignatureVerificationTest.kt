package com.kgapp.encryptionchat.data.crypto

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureVerificationTest {
    @Test
    fun signatureRoundTrip() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val keyPair = generator.generateKeyPair()

        val data = mapOf(
            "type" to "GetMsg",
            "pub" to "PUB_BASE64",
            "ts" to 1700000000,
            "from" to "uid1",
            "last_ts" to 1L
        )
        val canonical = ProtocolCanonicalizer.canonicalStringForSigning(data)

        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(canonical.toByteArray(Charsets.UTF_8))
        val signature = signer.sign()

        val publicSpec = X509EncodedKeySpec(keyPair.public.encoded)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(publicSpec)
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        verifier.update(canonical.toByteArray(Charsets.UTF_8))

        assertTrue(verifier.verify(signature))
    }
}
