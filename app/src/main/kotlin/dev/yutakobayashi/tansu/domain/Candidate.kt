package dev.yutakobayashi.tansu.domain

/**
 * 1 人分の未確定の項目集合。レビューの単位。
 *
 * 解析が `FN` を読み取れなかった場合も候補として保持するが、その候補は確定できない。
 */
data class Candidate(
  val sourceImage: ContentHash,
  val fn: PropertyValue?,
  val details: CardDetails = CardDetails.EMPTY,
) {
  val isConfirmable: Boolean get() = fn != null

  /** 候補を名刺として確定する。`FN` が空なら [ConfirmationResult.MissingFn] を返す。 */
  fun confirm(uid: Uid = Uid.random()): ConfirmationResult =
    fn?.let { ConfirmationResult.Confirmed(Card.of(uid, it, details)) }
      ?: ConfirmationResult.MissingFn

  companion object {
    /** 解析結果などの生の文字列から作る。空の値は落とし、値の整形や補完はしない。 */
    fun fromRaw(
      sourceImage: ContentHash,
      fn: String?,
      details: CardDetails = CardDetails.EMPTY,
    ): Candidate = Candidate(sourceImage, PropertyValue.of(fn), details)
  }
}

sealed interface ConfirmationResult {
  data class Confirmed(val card: Card) : ConfirmationResult

  /** `FN` が空のため確定できなかった。 */
  data object MissingFn : ConfirmationResult
}
