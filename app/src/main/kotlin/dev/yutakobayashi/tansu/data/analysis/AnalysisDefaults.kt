package dev.yutakobayashi.tansu.data.analysis

/**
 * 解析の既定値。
 *
 * `buildConfig = false` のため `BuildConfig` ではなくここに置く。
 */
object AnalysisDefaults {
  /** 接続の確立を待つ上限。 */
  const val CONNECT_TIMEOUT_MILLIS = 15_000L

  /** 送信から応答の受信完了までを含む、1 回のリクエスト全体の上限。 */
  const val CALL_TIMEOUT_MILLIS = 120_000L

  /** 送信する画像の長辺の上限。これを超える画像だけを縮小した複製にして送る。 */
  const val MAX_LONG_SIDE_PX = 2048

  /** 縮小した複製を JPEG にするときの品質。 */
  const val JPEG_QUALITY = 85
}
