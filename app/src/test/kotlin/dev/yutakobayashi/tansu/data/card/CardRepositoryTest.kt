package dev.yutakobayashi.tansu.data.card

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.yutakobayashi.tansu.domain.AnalysisStatus
import dev.yutakobayashi.tansu.domain.Candidate
import dev.yutakobayashi.tansu.domain.Card
import dev.yutakobayashi.tansu.domain.CardDetails
import dev.yutakobayashi.tansu.domain.ConfirmationResult
import dev.yutakobayashi.tansu.domain.ContentHash
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CardRepositoryTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val imageDirectory = File(context.filesDir, "source-images")
  private val openDatabases = mutableListOf<TansuDatabase>()

  private lateinit var repository: CardRepository

  @Before
  fun setUp() {
    context.deleteDatabase(DATABASE)
    imageDirectory.deleteRecursively()
    repository = openRepository()
  }

  @After
  fun tearDown() {
    openDatabases.forEach { it.close() }
    context.deleteDatabase(DATABASE)
    imageDirectory.deleteRecursively()
  }

  @Test
  fun importCopiesImageUnmodifiedUnderContentHashName() = runTest {
    val analysis = repository.import(ByteArrayInputStream(IMAGE))

    assertEquals(sha256(IMAGE), analysis.sourceImage.value)
    assertEquals(AnalysisStatus.NotAnalyzed, analysis.status)
    val image = repository.sourceImage(analysis.sourceImage)!!
    val file = repository.file(image)
    assertEquals(analysis.sourceImage.value, file.name)
    assertEquals(imageDirectory, file.parentFile)
    assertArrayEquals(IMAGE, file.readBytes())
  }

  @Test
  fun importingCopyOfSameImageReusesAnalysis() = runTest {
    // AC5
    val first = repository.import(ByteArrayInputStream(IMAGE))
    repository.startAnalysis(first.sourceImage)

    val second = repository.import(ByteArrayInputStream(IMAGE.copyOf()))

    assertEquals(first.sourceImage, second.sourceImage)
    assertEquals(AnalysisStatus.Analyzing, second.status)
    assertEquals(listOf(first.sourceImage.value), imageDirectory.list()!!.toList())
  }

  @Test
  fun differentImagesGetSeparateAnalyses() = runTest {
    val first = repository.import(ByteArrayInputStream(IMAGE))
    val second = repository.import(ByteArrayInputStream(OTHER_IMAGE))

    assertFalse(first.sourceImage == second.sourceImage)
    assertEquals(2, imageDirectory.list()!!.size)
  }

  @Test
  fun analysisInterruptedByProcessDeathFailsAfterRestart() = runTest {
    // AC8
    val hash = repository.import(ByteArrayInputStream(IMAGE)).sourceImage
    repository.startAnalysis(hash)
    restartProcess()

    val restored = repository.analysis(hash)!!

    assertEquals(AnalysisStatus.Failed, restored.status)
    assertTrue(repository.cards(hash).isEmpty())
    assertTrue(repository.file(repository.sourceImage(hash)!!).exists())
  }

  @Test
  fun restartKeepsAnalysesThatWereNotRunning() = runTest {
    val awaiting = repository.import(ByteArrayInputStream(IMAGE)).sourceImage
    repository.startAnalysis(awaiting)
    repository.recordReadResult(awaiting, listOf(candidate(awaiting, "山田 太郎")))
    val notAnalyzed = repository.import(ByteArrayInputStream(OTHER_IMAGE)).sourceImage
    restartProcess()

    assertEquals(
      AnalysisStatus.AwaitingReview(listOf(candidate(awaiting, "山田 太郎"))),
      repository.analysis(awaiting)!!.status,
    )
    assertEquals(AnalysisStatus.NotAnalyzed, repository.analysis(notAnalyzed)!!.status)
  }

  @Test
  fun failedAnalysisCanBeRetried() = runTest {
    val hash = repository.import(ByteArrayInputStream(IMAGE)).sourceImage
    repository.startAnalysis(hash)
    restartProcess()

    repository.startAnalysis(hash)

    assertEquals(AnalysisStatus.Analyzing, repository.analysis(hash)!!.status)
  }

  @Test
  fun readResultKeepsCandidatesInOrderWithAllProperties() = runTest {
    val hash = repository.import(ByteArrayInputStream(IMAGE)).sourceImage
    val candidates = listOf(
      Candidate.fromRaw(
        hash,
        fn = " Taro Yamada ",
        details = CardDetails.fromRaw(
          n = "Yamada;Taro",
          org = "Example, Inc.",
          tel = listOf("03-0000-0000", "", "090-0000-0000"),
          email = listOf("taro@example.com", "taro@example.com"),
          note = "line1\nline2",
        ),
      ),
      Candidate.fromRaw(hash, fn = null),
      candidate(hash, "佐藤 花子"),
    )
    repository.startAnalysis(hash)

    repository.recordReadResult(hash, candidates)

    assertEquals(AnalysisStatus.AwaitingReview(candidates), repository.analysis(hash)!!.status)
  }

  @Test
  fun readResultWithoutCandidatesIsNoCardsFound() = runTest {
    val hash = repository.import(ByteArrayInputStream(IMAGE)).sourceImage
    repository.startAnalysis(hash)

    repository.recordReadResult(hash, emptyList())

    val status = repository.analysis(hash)!!.status as AnalysisStatus.AwaitingReview
    assertTrue(status.isNoCardsFound)
  }

  @Test
  fun confirmingSomeCandidatesSavesCardsAndKeepsImage() = runTest {
    val hash = repository.import(ByteArrayInputStream(IMAGE)).sourceImage
    repository.startAnalysis(hash)
    val candidates = listOf(candidate(hash, "山田 太郎"), candidate(hash, "佐藤 花子"))
    repository.recordReadResult(hash, candidates)
    val card = (candidates[0].confirm() as ConfirmationResult.Confirmed).card

    val outcome = repository.completeReview(hash, listOf(card))

    assertEquals(ReviewOutcome.CardsSaved, outcome)
    assertEquals(listOf(card), repository.cards(hash))
    assertEquals(AnalysisStatus.Completed, repository.analysis(hash)!!.status)
    assertTrue(repository.file(repository.sourceImage(hash)!!).exists())
  }

  @Test
  fun rejectingAllCandidatesDeletesCopiedImage() = runTest {
    // AC17
    val hash = repository.import(ByteArrayInputStream(IMAGE)).sourceImage
    repository.startAnalysis(hash)
    repository.recordReadResult(hash, listOf(candidate(hash, "山田 太郎"), candidate(hash, "佐藤 花子")))
    val file = repository.file(repository.sourceImage(hash)!!)

    val outcome = repository.completeReview(hash, emptyList())

    assertEquals(ReviewOutcome.SourceImageDiscarded, outcome)
    assertTrue(repository.cards(hash).isEmpty())
    assertNull(repository.sourceImage(hash))
    assertNull(repository.analysis(hash))
    assertFalse(file.exists())
    assertTrue(imageDirectory.list()!!.isEmpty())
  }

  @Test
  fun discardedImageCanBeImportedAgain() = runTest {
    val hash = repository.import(ByteArrayInputStream(IMAGE)).sourceImage
    repository.startAnalysis(hash)
    repository.recordReadResult(hash, emptyList())
    repository.completeReview(hash, emptyList())

    val reimported = repository.import(ByteArrayInputStream(IMAGE))

    assertEquals(AnalysisStatus.NotAnalyzed, reimported.status)
    assertArrayEquals(IMAGE, repository.file(repository.sourceImage(hash)!!).readBytes())
  }

  @Test
  fun transitionsOutOfOrderAreRejected() = runTest {
    val hash = repository.import(ByteArrayInputStream(IMAGE)).sourceImage

    val readResult = runCatching { repository.recordReadResult(hash, emptyList()) }
    val review = runCatching { repository.completeReview(hash, emptyList()) }

    assertTrue(readResult.exceptionOrNull() is IllegalStateException)
    assertTrue(review.exceptionOrNull() is IllegalStateException)
    assertEquals(AnalysisStatus.NotAnalyzed, repository.analysis(hash)!!.status)
    assertTrue(repository.file(repository.sourceImage(hash)!!).exists())
  }

  @Test
  fun deletingOneOfSeveralCardsKeepsTheCopy() = runTest {
    val (hash, saved) = saveCards(IMAGE, "山田 太郎", "佐藤 花子")
    val file = repository.file(repository.sourceImage(hash)!!)

    val outcome = repository.deleteCard(hash, saved[0].uid)

    assertEquals(CardDeletionOutcome.CardsRemain, outcome)
    assertEquals(listOf(saved[1]), repository.cards(hash))
    assertEquals(AnalysisStatus.Completed, repository.analysis(hash)!!.status)
    assertArrayEquals(IMAGE, file.readBytes())
  }

  @Test
  fun deletingTheLastCardDeletesTheCopy() = runTest {
    // AC26: 名刺 0 件で複製も削除する。原本はフォルダ側にあり、このクラスは触れない。
    val (hash, saved) = saveCards(IMAGE, "山田 太郎")
    val file = repository.file(repository.sourceImage(hash)!!)

    val outcome = repository.deleteCard(hash, saved[0].uid)

    assertEquals(CardDeletionOutcome.SourceImageDiscarded, outcome)
    assertTrue(repository.cards(hash).isEmpty())
    assertNull(repository.sourceImage(hash))
    assertNull(repository.analysis(hash))
    assertFalse(file.exists())
    assertTrue(repository.savedCards().isEmpty())
  }

  @Test
  fun deletingACardOfAnotherImageIsRejected() = runTest {
    val (hash, _) = saveCards(IMAGE, "山田 太郎")
    val (_, other) = saveCards(OTHER_IMAGE, "佐藤 花子")

    val result = runCatching { repository.deleteCard(hash, other[0].uid) }

    assertTrue(result.exceptionOrNull() is IllegalStateException)
    assertEquals(1, repository.cards(hash).size)
    assertEquals(2, repository.savedCards().size)
  }

  @Test
  fun savedImagesListImagesWithCardsNewestFirst() = runTest {
    val (first, firstCards) = saveCards(IMAGE, "山田 太郎", "佐藤 花子")
    val (second, secondCards) = saveCards(OTHER_IMAGE, "Jane Doe")
    val pending = repository.import(ByteArrayInputStream(ByteArray(16) { it.toByte() })).sourceImage

    val images = repository.savedImages()

    assertEquals(listOf(second, first), images.map { it.image.contentHash })
    assertEquals(listOf(secondCards, firstCards), images.map { it.cards })
    assertFalse(images.any { it.image.contentHash == pending })
  }

  /** [bytes] を取り込み、[names] の候補をすべて確定して保存する。 */
  private suspend fun saveCards(bytes: ByteArray, vararg names: String): Pair<ContentHash, List<Card>> {
    val hash = repository.import(ByteArrayInputStream(bytes)).sourceImage
    repository.startAnalysis(hash)
    val candidates = names.map { candidate(hash, it) }
    repository.recordReadResult(hash, candidates)
    val cards = candidates.map { (it.confirm() as ConfirmationResult.Confirmed).card }
    repository.completeReview(hash, cards)
    return hash to cards
  }

  /** データベースを閉じて開き直す。プロセスが落ちて再起動したときと同じ経路を通る。 */
  private fun restartProcess() {
    openDatabases.forEach { it.close() }
    openDatabases.clear()
    repository = openRepository()
  }

  private fun openRepository(): CardRepository {
    val database = TansuDatabase.build(context, DATABASE)
    openDatabases += database
    return CardRepository(database, SourceImageFiles(imageDirectory), Dispatchers.Unconfined)
  }

  private fun candidate(hash: ContentHash, fn: String) = Candidate.fromRaw(hash, fn = fn)

  private companion object {
    const val DATABASE = "card-repository-test.db"
    val IMAGE = ByteArray(4096) { (it * 31 % 251).toByte() }
    val OTHER_IMAGE = ByteArray(2048) { (it * 17 % 241).toByte() }

    fun sha256(bytes: ByteArray): String =
      MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
  }
}
