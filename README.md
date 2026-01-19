# EncryptionChatApp

## Overview
This is a Kotlin + Jetpack Compose Android implementation that mirrors the Python reference logic (RSA OAEP encryption, RSA-SHA256 signing, and JSON storage layout).

## Local file layout (context.filesDir)
```
config/key/private.pem
config/key/public.pem
contacts/config.json
contacts/chats/<uid>.json
```

## Identity and UID derivation
- Public key PEM text is read from `config/key/public.pem` **as-is** and `rstrip('\n')` is applied.
- `pem_b64 = base64(pem_text_bytes)` (single line).
- `self_name = md5(pem_b64_bytes).hexdigest()`.
- For contacts, `uid = md5(base64(pub_key_pem_text_bytes).hexdigest())`.

## Crypto algorithms
- Key format: PKCS#8 private key (`BEGIN PRIVATE KEY`) and SubjectPublicKeyInfo public key (`BEGIN PUBLIC KEY`).
- Encryption: `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` (OAEP SHA-256).
- Signing: `SHA256withRSA` (PKCS#1 v1.5).

## Build
- Ensure `ANDROID_HOME` (or `sdk.dir` in `local.properties`) points to a valid Android SDK install.
- Install required SDK components (example):
  - `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"`
- `./gradlew assembleDebug`

## Minimum environment
- Android Gradle Plugin 8.2+
- Kotlin 1.9+
- JDK 17
