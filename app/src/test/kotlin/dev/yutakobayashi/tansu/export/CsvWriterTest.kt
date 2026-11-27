package dev.yutakobayashi.tansu.export

import dev.yutakobayashi.tansu.export.ExportFixtures.card
import dev.yutakobayashi.tansu.export.ExportFixtures.fnOnly
import dev.yutakobayashi.tansu.export.ExportFixtures.hash
import dev.yutakobayashi.tansu.testing.AnalysisFixture
import dev.yutakobayashi.tansu.testing.Golden
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvWriterTest {
  private val cards =
    listOf(
      ExportCard(card(1, AnalysisFixture.JapaneseSingle.cards.single()), hash('a')),
      ExportCard(card(2, AnalysisFixture.EnglishSingle.cards.single()), hash('b')),
      ExportCard(card(3, AnalysisFixture.SpecialCharacters.cards.single()), hash('c'), photo = byteArrayOf(1)),
    )

  /** AC10・AC23: カンマ・引用符・改行を含む値、`uid` と `photo_sha256` 列をゴールデンで固定する。 */
  @Test
  fun matchesGolden() {
    Golden.assertMatches("export/cards.csv", ExportFormat.Csv.write(cards))
  }

  @Test
  fun quotesFieldsWithCommaQuoteOrLineBreak() {
    val text = String(ExportFormat.Csv.write(cards), Charsets.UTF_8)

    assertEquals(
      "urn:uuid:00000000-0000-4000-8000-000000000003,高橋 次郎,高橋;次郎;;;," +
        "\"合同会社 \"\"テスト, 研究所\"\"\n第二開発部\",\"主任研究員, \"\"R&D\"\"\"," +
        "045-000-0000,jiro.takahashi@example.org,,,\"1行目\r\n2行目\"," + "c".repeat(64) + "\r\n",
      text.substring(text.indexOf("urn:uuid:00000000-0000-4000-8000-000000000003")),
    )
  }

  @Test
  fun joinsMultipleValuesWithSemicolon() {
    val text = String(ExportFormat.Csv.write(cards.take(1)), Charsets.UTF_8)

    assertEquals(
      "uid,fn,n,org,title,tel,email,adr,url,note,photo_sha256\r\n" +
        "urn:uuid:00000000-0000-4000-8000-000000000001,山田 太郎,山田;太郎;;;,株式会社サンプル商事,営業部 部長," +
        "03-0000-0000;090-0000-0000,taro.yamada@example.co.jp," +
        "〒100-0000 東京都千代田区架空町1-2-3 サンプルビル5F,https://www.example.co.jp/,," + "a".repeat(64) + "\r\n",
      text,
    )
  }

  /** AC11: 値の無い項目は空のセルになり、他の値で埋めない。ヘッダは常に同じ。 */
  @Test
  fun leavesEmptyPropertiesBlank() {
    val text = String(ExportFormat.Csv.write(listOf(ExportCard(fnOnly(1), hash('a')))), Charsets.UTF_8)

    assertEquals(
      "uid,fn,n,org,title,tel,email,adr,url,note,photo_sha256\r\n" +
        "urn:uuid:00000000-0000-4000-8000-000000000001,見本 花子,,,,,,,,," + "a".repeat(64) + "\r\n",
      text,
    )
  }

  @Test
  fun writesOnlyHeaderForNoCards() {
    assertEquals(
      "uid,fn,n,org,title,tel,email,adr,url,note,photo_sha256\r\n",
      String(ExportFormat.Csv.write(emptyList()), Charsets.UTF_8),
    )
  }

  /** AC12: 同じ名刺を書き出し直すと同じバイト列になる。 */
  @Test
  fun rewritingProducesIdenticalBytes() {
    assertArrayEquals(ExportFormat.Csv.write(cards), ExportFormat.Csv.write(cards))
  }
}
