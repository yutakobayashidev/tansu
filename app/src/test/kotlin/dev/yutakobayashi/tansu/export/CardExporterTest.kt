package dev.yutakobayashi.tansu.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.yutakobayashi.tansu.data.analysis.ImageCodec
import dev.yutakobayashi.tansu.data.analysis.ImageInfo
import dev.yutakobayashi.tansu.data.analysis.ReadCard
import dev.yutakobayashi.tansu.data.card.CardRepository
import dev.yutakobayashi.tansu.data.card.SourceImageFiles
import dev.yutakobayashi.tansu.data.card.TansuDatabase
import dev.yutakobayashi.tansu.domain.Candidate
import dev.yutakobayashi.tansu.domain.Card
import dev.yutakobayashi.tansu.domain.ConfirmationResult
import dev.yutakobayashi.tansu.domain.ContentHash
import dev.yutakobayashi.tansu.domain.Uid
import dev.yutakobayashi.tansu.testing.AnalysisFixture
import dev.yutakobayashi.tansu.testing.Golden
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CardExporterTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val imageDirectory = File(context.filesDir, "source-images")
  private val exportDirectory = File(context.cacheDir, "exports")

  private lateinit var database: TansuDatabase
  private lateinit var repository: CardRepository
  private lateinit var exporter: CardExporter

  @Before
  fun setUp() {
    context.deleteDatabase(DATABASE)
    imageDirectory.deleteRecursively()
    exportDirectory.deleteRecursively()
    database = TansuDatabase.build(context, DATABASE)
    repository = CardRepository(database, SourceImageFiles(imageDirectory), Dispatchers.Unconfined)
    exporter =
      CardExporter(repository, exportDirectory, ExportPhotoRenderer(FakeCodec), Dispatchers.Unconfined)
  }

  @After
  fun tearDown() {
    database.close()
    context.deleteDatabase(DATABASE)
    imageDirectory.deleteRecursively()
    exportDirectory.deleteRecursively()
  }

  /**
   * AC22・AC23: 候補 1 件の解析から確定した名刺だけが `PHOTO` を持ち、3 件の解析から確定した名刺は持たない。
   * CSV は写真を含まず、`uid` と `photo_sha256` で参照する。
   */
  @Test
  fun embedsPhotoOnlyForSingleCandidateAnalyses() = runTest {
    seedScenario()

    Golden.assertMatches("export/photo-condition.vcf", exportBytes(ExportFormat.VCard))
    Golden.assertMatches("export/photo-condition.csv", exportBytes(ExportFormat.Csv))
  }

  /** AC22: 3 件の解析から確定した名刺を後で 1 件削除しても、残りに `PHOTO` は付かない。 */
  @Test
  fun deletingCardsLaterDoesNotChangePhoto() = runTest {
    val scenario = seedScenario()

    deleteCard(scenario.threePeople[1])
    deleteCard(scenario.threePeople[2])

    Golden.assertMatches("export/photo-condition-after-delete.vcf", exportBytes(ExportFormat.VCard))
    val text = String(exportBytes(ExportFormat.VCard), Charsets.UTF_8)
    assertEquals(1, Regex("^PHOTO:", RegexOption.MULTILINE).findAll(text).count())
    assertTrue(text.contains("UID:urn:uuid:${scenario.threePeople[0]}\r\n"))
    assertFalse(text.contains("UID:urn:uuid:${scenario.threePeople[1]}\r\n"))
  }

  /** 候補 2 件のうち 1 件だけを確定しても、解析の候補は 2 件なので `PHOTO` は付かない。 */
  @Test
  fun countsCandidatesNotConfirmedCards() = runTest {
    val image = seedAnalysis(IMAGE_C, AnalysisFixture.NeedsAttention.cards.map { it.copy(fn = "候補") })
    repository.completeReview(image, listOf(confirm(image, 9)))

    val text = String(exportBytes(ExportFormat.VCard), Charsets.UTF_8)

    assertFalse(text.contains("PHOTO:"))
    assertEquals(2, repository.savedCards().single().readCandidateCount)
  }

  /** AC12・AC23: 書き出し直すと `UID` と `PHOTO` を含め同じバイト列になる。 */
  @Test
  fun reexportProducesIdenticalBytes() = runTest {
    seedScenario()

    ExportFormat.entries.forEach { format ->
      assertArrayEquals(exportBytes(format), exportBytes(format))
    }
  }

  @Test
  fun reportsNoCards() = runTest {
    assertEquals(ExportResult.NoCards, exporter.export(ExportFormat.VCard))
  }

  @Test
  fun writesFixedFileNamePerFormat() = runTest {
    seedScenario()

    val vcard = exporter.export(ExportFormat.VCard) as ExportResult.Exported
    val csv = exporter.export(ExportFormat.Csv) as ExportResult.Exported

    assertEquals(File(exportDirectory, "tansu.vcf"), vcard.file)
    assertEquals(File(exportDirectory, "tansu.csv"), csv.file)
    assertEquals(4, vcard.count)
    assertEquals(listOf("tansu.csv", "tansu.vcf"), exportDirectory.list()!!.sorted())
  }

  private class Scenario(val single: Uid, val threePeople: List<Uid>)

  /** 候補 1 件の画像 A を 1 件、候補 3 件の画像 B を 3 件確定する。 */
  private suspend fun seedScenario(): Scenario {
    val single = seedAnalysis(IMAGE_A, AnalysisFixture.JapaneseSingle.cards)
    val singleCard = confirm(single, 1)
    repository.completeReview(single, listOf(singleCard))

    val three = seedAnalysis(IMAGE_B, AnalysisFixture.ThreePeople.cards)
    val threeCards = (0..2).map { confirm(three, index = it + 2, position = it) }
    repository.completeReview(three, threeCards)
    return Scenario(singleCard.uid, threeCards.map { it.uid })
  }

  private val readCards = mutableMapOf<ContentHash, List<ReadCard>>()

  private suspend fun seedAnalysis(
    image: ByteArray,
    cards: List<ReadCard>,
  ): ContentHash {
    val hash = repository.import(ByteArrayInputStream(image)).sourceImage
    repository.startAnalysis(hash)
    repository.recordReadResult(
      hash,
      cards.map { ExportFixtures.card(0, it) }.map { Candidate(hash, it.fn, it.details) },
    )
    readCards[hash] = cards
    return hash
  }

  private fun confirm(image: ContentHash, index: Int, position: Int = 0): Card {
    val read = readCards.getValue(image)[position]
    val candidate = ExportFixtures.card(index, read).let { Candidate(image, it.fn, it.details) }
    return (candidate.confirm(ExportFixtures.uid(index)) as ConfirmationResult.Confirmed).card
  }

  private suspend fun exportBytes(format: ExportFormat): ByteArray =
    (exporter.export(format) as ExportResult.Exported).file.readBytes()

  /** 保存済みの名刺の削除（#13）の代わりに、行を直接消す。 */
  private fun deleteCard(uid: Uid) {
    database.openHelper.writableDatabase.execSQL("DELETE FROM card WHERE uid = ?", arrayOf(uid.toString()))
  }

  /** 縮小の代わりに、元のバイト列と長辺の上限が分かる固定のバイト列を返す。 */
  private object FakeCodec : ImageCodec {
    override fun probe(bytes: ByteArray) = ImageInfo(1600, 1000, "image/png")

    override fun downscale(bytes: ByteArray, maxLongSide: Int, quality: Int): ByteArray =
      "jpeg:$maxLongSide:$quality:".toByteArray() + bytes
  }

  private companion object {
    const val DATABASE = "card-exporter-test.db"
    val IMAGE_A = "image A: one card".toByteArray()
    val IMAGE_B = "image B: three cards".toByteArray()
    val IMAGE_C = "image C: two candidates".toByteArray()
  }
}
