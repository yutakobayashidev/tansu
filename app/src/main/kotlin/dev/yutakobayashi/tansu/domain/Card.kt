package dev.yutakobayashi.tansu.domain

/**
 * 確定した名刺。`FN` を必ず持つ。
 *
 * `copy` を公開しないので、作成後に [uid] を差し替える手段はない。
 */
@ConsistentCopyVisibility
data class Card private constructor(
  val uid: Uid,
  val fn: PropertyValue,
  val details: CardDetails,
) {
  companion object {
    /** 保存済みの名刺を復元するときにも使う。 */
    fun of(uid: Uid, fn: PropertyValue, details: CardDetails = CardDetails.EMPTY): Card =
      Card(uid, fn, details)
  }
}
