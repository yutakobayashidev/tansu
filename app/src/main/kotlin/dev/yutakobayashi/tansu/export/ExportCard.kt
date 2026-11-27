package dev.yutakobayashi.tansu.export

import dev.yutakobayashi.tansu.domain.Card
import dev.yutakobayashi.tansu.domain.ContentHash

/**
 * 書き出す名刺 1 件。
 *
 * @property sourceImage 取り込み元の名刺画像の内容ハッシュ。CSV の `photo_sha256` 列になる。
 * @property photo vCard の `PHOTO` に埋め込む JPEG。埋め込まないなら `null`。
 */
class ExportCard(
  val card: Card,
  val sourceImage: ContentHash,
  val photo: ByteArray? = null,
)

/** 書き出し形式。 */
enum class ExportFormat(val extension: String, val mimeType: String) {
  VCard("vcf", "text/vcard"),
  Csv("csv", "text/csv"),
  ;

  /** [cards] をこの形式で書き出したバイト列。同じ入力からは常に同じバイト列になる。 */
  fun write(cards: List<ExportCard>): ByteArray =
    when (this) {
      VCard -> VCardWriter.write(cards)
      Csv -> CsvWriter.write(cards)
    }
}

/** vCard と CSV で共通の `UID` の値。RFC 6350 が推奨する `urn:uuid:` 形式にする。 */
internal fun ExportCard.uidValue(): String = "urn:uuid:${card.uid}"
