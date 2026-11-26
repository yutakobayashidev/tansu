package dev.yutakobayashi.tansu.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit

/** ディスクに置く BYO 設定。API キーは [SecretCipher] で暗号化した値だけを持つ。 */
data class StoredByoSettings(val baseUrl: String, val model: String, val encryptedApiKey: String?)

interface ByoSettingsStore {
  fun read(): StoredByoSettings?

  fun write(settings: StoredByoSettings)

  fun clearApiKey()
}

class SharedPreferencesByoSettingsStore(private val preferences: SharedPreferences) : ByoSettingsStore {

  override fun read(): StoredByoSettings? {
    val baseUrl = preferences.getString(KEY_BASE_URL, null) ?: return null
    return StoredByoSettings(
      baseUrl = baseUrl,
      model = preferences.getString(KEY_MODEL, null).orEmpty(),
      encryptedApiKey = preferences.getString(KEY_ENCRYPTED_API_KEY, null),
    )
  }

  override fun write(settings: StoredByoSettings) {
    preferences.edit(commit = true) {
      putString(KEY_BASE_URL, settings.baseUrl)
      putString(KEY_MODEL, settings.model)
      putString(KEY_ENCRYPTED_API_KEY, settings.encryptedApiKey)
    }
  }

  override fun clearApiKey() {
    preferences.edit(commit = true) { remove(KEY_ENCRYPTED_API_KEY) }
  }

  companion object {
    const val FILE_NAME = "byo_settings"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_MODEL = "model"
    private const val KEY_ENCRYPTED_API_KEY = "api_key_encrypted"
  }
}
