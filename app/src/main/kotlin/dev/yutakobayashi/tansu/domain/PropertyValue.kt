package dev.yutakobayashi.tansu.domain

/**
 * 名刺の項目 1 つ分の値。空文字・空白のみの値は表現できない。
 *
 * 値は受け取った文字列をそのまま保持し、前後の空白の除去や表記の正規化はしない。
 */
@JvmInline
value class PropertyValue private constructor(val value: String) {
  override fun toString(): String = value

  companion object {
    /** [raw] が `null`・空文字・空白のみなら `null` を返す。それ以外は無加工で包む。 */
    fun of(raw: String?): PropertyValue? = raw?.takeUnless { it.isBlank() }?.let(::PropertyValue)

    /** 空の値を落とし、残りを順序と重複を保ったまま返す。 */
    fun listOf(raws: List<String?>): List<PropertyValue> = raws.mapNotNull(::of)
  }
}
