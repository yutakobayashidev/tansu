package dev.yutakobayashi.tansu.data.settings

import java.net.URI

/**
 * 画面に出してよい BYO 設定の状態。API キーの値は含めず、保存済みかどうかだけを持つ。
 */
data class ByoSettingsSummary(val baseUrl: String, val model: String, val hasApiKey: Boolean) {
  /** 解析に必要な 3 項目がすべて揃っているか。 */
  val isComplete: Boolean
    get() = BaseUrl.normalize(baseUrl) != null && model.isNotBlank() && hasApiKey

  companion object {
    val Empty = ByoSettingsSummary(baseUrl = "", model = "", hasApiKey = false)
  }
}

/** 保存操作の入力値の検査結果。 */
enum class ByoSettingsError {
  InvalidBaseUrl,
  MissingModel,
  MissingApiKey,
}

object BaseUrl {
  /**
   * 入力された base URL を検査し、前後の空白と末尾の `/` を除いた値を返す。
   *
   * API キーを送るので `https` だけを受け付ける。クエリやフラグメントを含む値は
   * パスの連結先として扱えないため拒否する。
   */
  fun normalize(input: String): String? {
    val trimmed = input.trim().trimEnd('/')
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.host.isNullOrEmpty()) return null
    if (uri.rawQuery != null || uri.rawFragment != null || uri.rawUserInfo != null) return null
    return trimmed
  }
}
