package dev.yutakobayashi.tansu.export

import dev.yutakobayashi.tansu.export.ExportFixtures.card
import dev.yutakobayashi.tansu.export.ExportFixtures.fnOnly
import dev.yutakobayashi.tansu.export.ExportFixtures.hash
import dev.yutakobayashi.tansu.export.ExportFixtures.longNote
import dev.yutakobayashi.tansu.testing.AnalysisFixture
import dev.yutakobayashi.tansu.testing.Golden
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardWriterTest {
  private val cards =
    listOf(
      ExportCard(card(1, AnalysisFixture.JapaneseSingle.cards.single()), hash('a')),
      ExportCard(card(2, AnalysisFixture.EnglishSingle.cards.single()), hash('b')),
      ExportCard(card(3, AnalysisFixture.SpecialCharacters.cards.single()), hash('c')),
      ExportCard(longNote(4), hash('d'), photo = ByteArray(120) { it.toByte() }),
    )

  /** AC9・AC23: CRLF / UTF-8 / 75 オクテット / `VERSION:4.0` / `FN` / `UID` をゴールデンで固定する。 */
  @Test
  fun matchesGolden() {
    Golden.assertMatches("export/cards.vcf", ExportFormat.VCard.write(cards))
  }

  @Test
  fun everyLineEndsWithCrlfAndFitsIn75Octets() {
    val bytes = ExportFormat.VCard.write(cards)

    val lines = split(bytes)
    lines.forEach { line ->
      assertTrue("Line exceeds 75 octets: ${line.size}", line.size <= 75)
      assertFalse("Bare CR or LF in a line", line.any { it == '\r'.code.toByte() || it == '\n'.code.toByte() })
      // 折り返しで UTF-8 の文字を割っていれば、ここで復号に失敗する。
      Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(line))
    }
    assertTrue("Expected folded lines", lines.any { it.firstOrNull() == ' '.code.toByte() })
  }

  @Test
  fun unfoldingRestoresEscapedValues() {
    val text = unfold(ExportFormat.VCard.write(cards))

    assertTrue(text.contains("ORG:合同会社 \"テスト\\, 研究所\"\\n第二開発部\r\n"))
    assertTrue(text.contains("NOTE:1行目\\n2行目\r\n"))
    assertTrue(text.contains("N:高橋;次郎;;;\r\n"))
    assertTrue(text.contains("ADR:;;100 Example Street\\, Suite 200\\, Springfield\\, ZZ 00000\\, USA;;;;\r\n"))
    assertTrue(text.contains("URL:https://example.com/path?a=1,b=2;c=3\r\n"))
    assertTrue(text.contains("NOTE:a" + "名刺の裏面に記載された長いメモ。".repeat(8) + "\r\n"))
  }

  @Test
  fun eachCardHasVersionUidAndFn() {
    val text = unfold(ExportFormat.VCard.write(cards))

    assertEquals(4, Regex("BEGIN:VCARD\r\nVERSION:4.0\r\nUID:urn:uuid:[0-9a-f-]{36}\r\nFN:").findAll(text).count())
    assertTrue(text.contains("UID:urn:uuid:00000000-0000-4000-8000-000000000001\r\n"))
  }

  /** AC11: 値の無い項目はプロパティを 1 行も出さない。 */
  @Test
  fun omitsEmptyProperties() {
    val text = String(ExportFormat.VCard.write(listOf(ExportCard(fnOnly(1), hash('a')))), Charsets.UTF_8)

    assertEquals(
      "BEGIN:VCARD\r\n" +
        "VERSION:4.0\r\n" +
        "UID:urn:uuid:00000000-0000-4000-8000-000000000001\r\n" +
        "FN:見本 花子\r\n" +
        "END:VCARD\r\n",
      text,
    )
  }

  /** N の要素が足りなければ空で補い、多ければ余りを最後の要素の文字として扱う。 */
  @Test
  fun padsAndClampsNComponents() {
    fun n(raw: String): String {
      val card = card(1, AnalysisFixture.JapaneseSingle.cards.single().copy(n = raw))
      return unfold(ExportFormat.VCard.write(listOf(ExportCard(card, hash('a')))))
        .lineSequence()
        .first { it.startsWith("N:") }
        .trimEnd('\r')
    }

    assertEquals("N:山田 太郎;;;;", n("山田 太郎"))
    assertEquals("N:a;b;c;d;e\\;f\\;g", n("a;b;c;d;e;f;g"))
  }

  /** AC12: 同じ名刺を書き出し直すと同じバイト列になる。 */
  @Test
  fun rewritingProducesIdenticalBytes() {
    assertArrayEquals(ExportFormat.VCard.write(cards), ExportFormat.VCard.write(cards))
  }

  private fun split(bytes: ByteArray): List<ByteArray> {
    val text = String(bytes, Charsets.ISO_8859_1)
    assertTrue(text.endsWith("\r\n"))
    return text.removeSuffix("\r\n").split("\r\n").map { it.toByteArray(Charsets.ISO_8859_1) }
  }

  private fun unfold(bytes: ByteArray): String = String(bytes, Charsets.UTF_8).replace("\r\n ", "")
}
