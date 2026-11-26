package dev.yutakobayashi.tansu.domain

/**
 * フォルダの画像 1 枚が、利用者から見てどこまで進んだか。一覧のバッジに使う。
 *
 * 解析の 5 状態を、利用者が次にすべきことで 3 つにまとめる。
 */
enum class ImageProgress {
  /** 取り込み前、または `未解析` / `解析中` / `失敗`。解析を始めるか、やり直す必要がある。 */
  Unprocessed,

  /** `確認待ち`。読み取り結果のレビューが残っている。 */
  AwaitingReview,

  /** `完了`。レビューを終えて名刺を保存した。 */
  Completed;

  companion object {
    /** [status] が `null` なら、その画像はまだ取り込まれていない。 */
    fun of(status: AnalysisStatus?): ImageProgress = when (status) {
      null, AnalysisStatus.NotAnalyzed, AnalysisStatus.Analyzing, AnalysisStatus.Failed -> Unprocessed
      is AnalysisStatus.AwaitingReview -> AwaitingReview
      AnalysisStatus.Completed -> Completed
    }
  }
}
