package dev.yutakobayashi.tansu.domain

/**
 * 名刺画像 1 枚に対する解析と、その状態。
 *
 * 確認待ちの候補はすべて [sourceImage] に由来する。
 */
data class Analysis(
  val sourceImage: ContentHash,
  val status: AnalysisStatus,
) {
  init {
    if (status is AnalysisStatus.AwaitingReview) {
      require(status.candidates.all { it.sourceImage == sourceImage }) {
        "Candidates must come from the analyzed source image"
      }
    }
  }
}

sealed interface AnalysisStatus {
  /** 未解析 */
  data object NotAnalyzed : AnalysisStatus

  /** 解析中 */
  data object Analyzing : AnalysisStatus

  /**
   * 確認待ち。候補が 0 件なら該当なしであり、失敗ではない。
   */
  data class AwaitingReview(val candidates: List<Candidate>) : AnalysisStatus {
    val isNoCardsFound: Boolean get() = candidates.isEmpty()
  }

  /** 完了 */
  data object Completed : AnalysisStatus

  /** 失敗 */
  data object Failed : AnalysisStatus
}
