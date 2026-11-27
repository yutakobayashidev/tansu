package dev.yutakobayashi.tansu.export

import dev.yutakobayashi.tansu.domain.PropertyValue
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * 名刺を vCard 4.0（RFC 6350）として書き出す。
 *
 * - 行末は CRLF、文字コードは UTF-8
 * - 75 オクテットを超える行は折り返す。UTF-8 の 1 文字の途中では切らない
 * - 値の無い項目はプロパティごと出さない
 * - 値は保持している文字列をそのまま使い、RFC 6350 のエスケープだけを施す
 */
internal object VCardWriter {
  private const val CRLF = "\r\n"
  private const val MAX_LINE_OCTETS = 75

  fun write(cards: List<ExportCard>): ByteArray =
    ByteArrayOutputStream().use { output ->
      cards.forEach { card -> lines(card).forEach { output.write(fold(it)) } }
      output.toByteArray()
    }

  private fun lines(export: ExportCard): List<String> = buildList {
    val card = export.card
    val details = card.details
    add("BEGIN:VCARD")
    add("VERSION:4.0")
    add("UID:${export.uidValue()}")
    add("FN:${text(card.fn)}")
    details.n?.let { add("N:${structured(it, N_COMPONENTS)}") }
    details.org?.let { add("ORG:${text(it)}") }
    details.title?.let { add("TITLE:${text(it)}") }
    details.tel.forEach { add("TEL:${text(it)}") }
    details.email.forEach { add("EMAIL:${text(it)}") }
    // 住所は区切りの無い 1 つの文字列で持つため、番地（3 番目の要素）にまとめて入れる。
    details.adr?.let { add("ADR:;;${text(it)};;;;") }
    details.url?.let { add("URL:${uri(it)}") }
    details.note?.let { add("NOTE:${text(it)}") }
    export.photo?.let { add("PHOTO:data:image/jpeg;base64,${Base64.getEncoder().encodeToString(it)}") }
    add("END:VCARD")
  }

  /** テキスト値。`\`・`,`・`;`・改行をエスケープする（RFC 6350 3.4）。 */
  private fun text(value: PropertyValue): String = escapeComponent(value.value).replace(";", "\\;")

  /**
   * 構造化された値（`N`）。解析は `山田;太郎;;;` のように `;` で要素を区切った形で返すため、
   * `;` は区切りとして残し、足りない要素は空で補う。要素数を超える分の `;` は最後の要素の文字として扱う。
   */
  private fun structured(value: PropertyValue, components: Int): String {
    val parts = value.value.split(';').map(::escapeComponent)
    val padded = parts + List(maxOf(0, components - parts.size)) { "" }
    val last = padded.drop(components - 1).joinToString("\\;")
    return (padded.take(components - 1) + last).joinToString(";")
  }

  /** URI 値。エスケープは要らないが、改行が残ると行の区切りと混ざるため `\n` にする。 */
  private fun uri(value: PropertyValue): String = value.value.replace(LINE_BREAK, "\\\\n")

  private fun escapeComponent(value: String): String =
    value.replace("\\", "\\\\").replace(",", "\\,").replace(LINE_BREAK, "\\\\n")

  /** 1 行を 75 オクテットごとに折り返し、CRLF を付けたバイト列にする。続きの行は空白 1 つで始める。 */
  private fun fold(line: String): ByteArray {
    val bytes = line.toByteArray(Charsets.UTF_8)
    val output = ByteArrayOutputStream(bytes.size + bytes.size / MAX_LINE_OCTETS * 3 + 2)
    var start = 0
    var limit = MAX_LINE_OCTETS
    while (bytes.size - start > limit) {
      var end = start + limit
      // UTF-8 の継続バイト（10xxxxxx）の手前では切らない。
      while (bytes[end].toInt() and 0xC0 == 0x80) end--
      output.write(bytes, start, end - start)
      output.write(FOLD)
      start = end
      limit = MAX_LINE_OCTETS - 1
    }
    output.write(bytes, start, bytes.size - start)
    output.write(CRLF.toByteArray(Charsets.US_ASCII))
    return output.toByteArray()
  }

  private const val N_COMPONENTS = 5
  private val LINE_BREAK = Regex("\r\n|\r|\n")
  private val FOLD = "$CRLF ".toByteArray(Charsets.US_ASCII)
}
