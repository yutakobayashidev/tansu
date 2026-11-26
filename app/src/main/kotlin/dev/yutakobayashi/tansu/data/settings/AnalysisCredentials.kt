package dev.yutakobayashi.tansu.data.settings

/**
 * 解析リクエストに使う接続先と資格情報。
 *
 * 解析側はこの型だけを受け取り、BYO と SIWC を区別しない（ADR 0001）。
 */
class AnalysisCredentials(val baseUrl: String, val model: String, val apiKey: String) {
  override fun toString(): String = "AnalysisCredentials(baseUrl=$baseUrl, model=$model, apiKey=***)"
}

/** 現在の設定から [AnalysisCredentials] を返す。設定が未完了なら `null` を返す。 */
fun interface AnalysisCredentialsProvider {
  suspend fun credentials(): AnalysisCredentials?
}
