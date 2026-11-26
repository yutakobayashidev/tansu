package dev.yutakobayashi.tansu.domain

/**
 * `FN` 以外の任意項目の集合。候補と名刺で共有する。
 *
 * 各項目は [PropertyValue] なので空の値を持たない。`TEL` と `EMAIL` は複数保持できる。
 */
data class CardDetails(
  val n: PropertyValue? = null,
  val org: PropertyValue? = null,
  val title: PropertyValue? = null,
  val tel: List<PropertyValue> = emptyList(),
  val email: List<PropertyValue> = emptyList(),
  val adr: PropertyValue? = null,
  val url: PropertyValue? = null,
  val note: PropertyValue? = null,
) {
  companion object {
    val EMPTY = CardDetails()

    /** 解析結果などの生の文字列から作る。空の値は落とし、値の整形や補完はしない。 */
    fun fromRaw(
      n: String? = null,
      org: String? = null,
      title: String? = null,
      tel: List<String?> = emptyList(),
      email: List<String?> = emptyList(),
      adr: String? = null,
      url: String? = null,
      note: String? = null,
    ): CardDetails = CardDetails(
      n = PropertyValue.of(n),
      org = PropertyValue.of(org),
      title = PropertyValue.of(title),
      tel = PropertyValue.listOf(tel),
      email = PropertyValue.listOf(email),
      adr = PropertyValue.of(adr),
      url = PropertyValue.of(url),
      note = PropertyValue.of(note),
    )
  }
}
