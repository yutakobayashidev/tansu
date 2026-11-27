package dev.yutakobayashi.tansu.domain

/**
 * 保存済みの名刺と、その取り込み元の名刺画像。
 *
 * @property readCandidateCount 取り込み元の解析が返した候補の件数。確定や却下、後からの削除では変わらない。
 *   記録する前に保存した名刺では `null`。
 */
data class SavedCard(
  val card: Card,
  val sourceImage: ContentHash,
  val readCandidateCount: Int?,
) {
  /**
   * 名刺画像を vCard の `PHOTO` として埋め込むか（ADR 0004）。
   *
   * 解析が返した候補が 1 件だけのときに限る。複数件写る画像では、どの名刺の写真か決められない。
   */
  val embedsPhoto: Boolean get() = readCandidateCount == 1
}
