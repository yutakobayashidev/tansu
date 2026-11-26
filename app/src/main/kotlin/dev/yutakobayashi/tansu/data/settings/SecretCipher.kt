package dev.yutakobayashi.tansu.data.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 秘密の文字列を、保存してよい形式へ暗号化・復号する。 */
interface SecretCipher {
  fun encrypt(plaintext: String): String

  /** 復号できない場合は例外を投げる。 */
  fun decrypt(ciphertext: String): String
}

/**
 * Android Keystore に置いた AES-256 鍵で、AES-GCM により暗号化する。
 *
 * 鍵素材は Keystore の外へ出ない。保存形式は `v1:<IV>:<暗号文>`（各 Base64）。
 */
class KeystoreSecretCipher(private val alias: String = DEFAULT_ALIAS) : SecretCipher {

  override fun encrypt(plaintext: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey())
    val body = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
    val encoder = Base64.getEncoder()
    return listOf(VERSION, encoder.encodeToString(cipher.iv), encoder.encodeToString(body))
      .joinToString(SEPARATOR)
  }

  override fun decrypt(ciphertext: String): String {
    val parts = ciphertext.split(SEPARATOR)
    require(parts.size == 3 && parts[0] == VERSION) { "Unsupported ciphertext format" }
    val decoder = Base64.getDecoder()
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, decoder.decode(parts[1])))
    return String(cipher.doFinal(decoder.decode(parts[2])), Charsets.UTF_8)
  }

  private fun secretKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    (keyStore.getKey(alias, null) as? SecretKey)?.let {
      return it
    }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(KEY_BITS)
        .build()
    )
    return generator.generateKey()
  }

  private companion object {
    const val DEFAULT_ALIAS = "tansu.byo.api_key"
    const val KEYSTORE = "AndroidKeyStore"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val VERSION = "v1"
    const val SEPARATOR = ":"
    const val TAG_BITS = 128
    const val KEY_BITS = 256
  }
}
