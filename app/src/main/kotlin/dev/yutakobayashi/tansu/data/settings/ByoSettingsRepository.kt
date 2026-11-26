package dev.yutakobayashi.tansu.data.settings

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** BYO の接続先・モデル名・API キーを保存し、解析へ [AnalysisCredentials] を渡す。 */
class ByoSettingsRepository(
  private val store: ByoSettingsStore,
  private val cipher: SecretCipher,
  private val ioDispatcher: CoroutineDispatcher,
) : AnalysisCredentialsProvider {

  private val mutex = Mutex()
  private val _summary = MutableStateFlow(store.read().toSummary())

  val summary: StateFlow<ByoSettingsSummary> = _summary.asStateFlow()

  /**
   * 設定を検査して保存する。`apiKey` が空なら保存済みの API キーを残す。
   *
   * @return 保存できなければ最初に見つかった不備を返す。保存できたら `null`。
   */
  suspend fun save(baseUrl: String, model: String, apiKey: String): ByoSettingsError? =
    withContext(ioDispatcher) {
      mutex.withLock {
        val normalizedUrl = BaseUrl.normalize(baseUrl) ?: return@withLock ByoSettingsError.InvalidBaseUrl
        val trimmedModel = model.trim()
        if (trimmedModel.isEmpty()) return@withLock ByoSettingsError.MissingModel
        val trimmedKey = apiKey.trim()
        val encryptedKey =
          if (trimmedKey.isEmpty()) {
            store.read()?.encryptedApiKey ?: return@withLock ByoSettingsError.MissingApiKey
          } else {
            cipher.encrypt(trimmedKey)
          }
        val stored = StoredByoSettings(normalizedUrl, trimmedModel, encryptedKey)
        store.write(stored)
        _summary.value = stored.toSummary()
        null
      }
    }

  override suspend fun credentials(): AnalysisCredentials? =
    withContext(ioDispatcher) {
      mutex.withLock {
        val stored = store.read() ?: return@withLock null
        val baseUrl = BaseUrl.normalize(stored.baseUrl) ?: return@withLock null
        if (stored.model.isBlank()) return@withLock null
        val encryptedKey = stored.encryptedApiKey ?: return@withLock null
        val apiKey =
          runCatching { cipher.decrypt(encryptedKey) }
            .getOrElse {
              // 鍵が失われた（端末の初期化やバックアップ復元など）暗号文は二度と復号できない。
              // 残しておくと設定済みに見えるので消し、再入力へ誘導する。
              store.clearApiKey()
              _summary.value = store.read().toSummary()
              return@withLock null
            }
        AnalysisCredentials(baseUrl = baseUrl, model = stored.model, apiKey = apiKey)
      }
    }

  private fun StoredByoSettings?.toSummary(): ByoSettingsSummary =
    if (this == null) {
      ByoSettingsSummary.Empty
    } else {
      ByoSettingsSummary(baseUrl = baseUrl, model = model, hasApiKey = encryptedApiKey != null)
    }
}
