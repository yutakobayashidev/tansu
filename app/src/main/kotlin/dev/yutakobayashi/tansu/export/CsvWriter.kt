package dev.yutakobayashi.tansu.export

import dev.yutakobayashi.tansu.domain.PropertyValue

/**
 * 名刺を CSV（RFC 4180）として書き出す。1 行 1 名刺で、先頭に固定のヘッダ行を置く。
 *
 * - 文字コードは UTF-8（BOM なし）、行末は CRLF
 * - `,`・`"`・CR・LF を含む値は `"` で囲み、`"` は `""` にする
 * - 複数値の `TEL` と `EMAIL` は `;` で区切って 1 セルにまとめる（ADR 0004）
 * - 値の無い項目は空のセルにする
 */
internal object CsvWriter {
  /** 列名と並び順は公開契約として固定する（ADR 0004）。 */
  val HEADER = listOf("uid", "fn", "n", "org", "title", "tel", "email", "adr", "url", "note", "photo_sha256")

  private const val CRLF = "\r\n"
  private const val MULTI_VALUE_SEPARATOR = ";"

  fun write(cards: List<ExportCard>): ByteArray =
    buildString {
        appendRecord(HEADER)
        cards.forEach { appendRecord(fields(it)) }
      }
      .toByteArray(Charsets.UTF_8)

  private fun fields(export: ExportCard): List<String> {
    val card = export.card
    val details = card.details
    return listOf(
      export.uidValue(),
      card.fn.value,
      details.n.orEmpty(),
      details.org.orEmpty(),
      details.title.orEmpty(),
      details.tel.joinToString(MULTI_VALUE_SEPARATOR) { it.value },
      details.email.joinToString(MULTI_VALUE_SEPARATOR) { it.value },
      details.adr.orEmpty(),
      details.url.orEmpty(),
      details.note.orEmpty(),
      export.sourceImage.value,
    )
  }

  private fun StringBuilder.appendRecord(fields: List<String>) {
    fields.joinTo(this, ",", transform = ::quote)
    append(CRLF)
  }

  private fun quote(field: String): String =
    if (field.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
      "\"" + field.replace("\"", "\"\"") + "\""
    } else {
      field
    }

  private fun PropertyValue?.orEmpty(): String = this?.value.orEmpty()
}
