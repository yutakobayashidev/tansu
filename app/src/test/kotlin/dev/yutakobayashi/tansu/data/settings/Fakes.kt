package dev.yutakobayashi.tansu.data.settings

class InMemoryByoSettingsStore(var stored: StoredByoSettings? = null) : ByoSettingsStore {
  override fun read(): StoredByoSettings? = stored

  override fun write(settings: StoredByoSettings) {
    stored = settings
  }

  override fun clearApiKey() {
    stored = stored?.copy(encryptedApiKey = null)
  }
}

/** 平文を含まない値へ可逆に変換する偽の暗号。`brokenKey` が真なら復号に失敗する。 */
class FakeSecretCipher(var brokenKey: Boolean = false) : SecretCipher {
  override fun encrypt(plaintext: String): String = "enc:" + plaintext.reversed()

  override fun decrypt(ciphertext: String): String {
    check(!brokenKey) { "key lost" }
    require(ciphertext.startsWith("enc:"))
    return ciphertext.removePrefix("enc:").reversed()
  }
}
