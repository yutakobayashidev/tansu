package dev.yutakobayashi.tansu.domain

/** 無加工で保持した名刺画像の SHA-256。小文字 16 進 64 桁だけを受け付ける。 */
@JvmInline
value class ContentHash(val value: String) {
  init {
    require(SHA256_HEX.matches(value)) { "ContentHash must be 64 lowercase hex characters" }
  }

  override fun toString(): String = value

  private companion object {
    val SHA256_HEX = Regex("^[0-9a-f]{64}$")
  }
}

/** アプリ内へ複製した名刺画像ファイルへの参照。空の参照は表現できない。 */
@JvmInline
value class CopiedFileRef(val value: String) {
  init {
    require(value.isNotBlank()) { "CopiedFileRef must not be blank" }
  }

  override fun toString(): String = value
}

/** フォルダから取り込み、アプリ内へ無加工で複製した名刺画像。 */
data class SourceImage(
  val contentHash: ContentHash,
  val copiedFile: CopiedFileRef,
)
