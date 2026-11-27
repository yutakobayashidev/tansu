package dev.yutakobayashi.tansu.ui.review

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.yutakobayashi.tansu.data.analysis.AnalysisFailure
import dev.yutakobayashi.tansu.data.analysis.AnalysisRunner
import dev.yutakobayashi.tansu.data.analysis.ChatCompletionsAnalysisClient
import dev.yutakobayashi.tansu.data.analysis.pngBytes
import dev.yutakobayashi.tansu.data.card.CardRepository
import dev.yutakobayashi.tansu.data.card.SharedImageFiles
import dev.yutakobayashi.tansu.data.card.SourceImageFiles
import dev.yutakobayashi.tansu.data.card.TansuDatabase
import dev.yutakobayashi.tansu.data.folder.FakeDocument
import dev.yutakobayashi.tansu.data.folder.FakeFolderAccess
import dev.yutakobayashi.tansu.data.folder.FolderRepository
import dev.yutakobayashi.tansu.data.folder.InMemoryFolderStore
import dev.yutakobayashi.tansu.data.settings.AnalysisCredentials
import dev.yutakobayashi.tansu.data.settings.ByoSettingsSummary
import dev.yutakobayashi.tansu.domain.AnalysisStatus
import dev.yutakobayashi.tansu.domain.Attention
import dev.yutakobayashi.tansu.domain.ContentHash
import dev.yutakobayashi.tansu.testing.AnalysisFixture
import dev.yutakobayashi.tansu.testing.MockAnalysisServer
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * レビュー画面の流れを、実際の DB・解析クライアント・MockWebServer で確かめる。
 *
 * HTTP リクエスト数は [MockAnalysisServer.requestCount] で数える。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReviewViewModelTest {
  @get:Rule val api = MockAnalysisServer()

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val imageDirectory = File(context.filesDir, "source-images")
  private val access = FakeFolderAccess()
  private val settings = MutableStateFlow(COMPLETE)
  private var credentials: AnalysisCredentials? = null

  private lateinit var database: TansuDatabase
  private lateinit var cards: CardRepository
  private lateinit var folders: FolderRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    context.deleteDatabase(DATABASE)
    imageDirectory.deleteRecursively()
    database = TansuDatabase.build(context, DATABASE)
    cards = CardRepository(
      database,
      SourceImageFiles(imageDirectory),
      SharedImageFiles(File(context.cacheDir, "exports/images")),
      Dispatchers.IO,
    )
    folders = FolderRepository(InMemoryFolderStore(), access, database, cards, Dispatchers.IO)
    access.documents += FakeDocument.of(TREE, NAME, 1, IMAGE)
    credentials = api.credentials
  }

  @After
  fun tearDown() {
    database.close()
    context.deleteDatabase(DATABASE)
    imageDirectory.deleteRecursively()
    Dispatchers.resetMain()
  }

  private fun viewModel(): ReviewViewModel {
    val runner = AnalysisRunner(cards, { credentials }, ChatCompletionsAnalysisClient(), ioDispatcher = Dispatchers.IO)
    return ReviewViewModel(DOCUMENT, settings, folders, cards, runner)
  }

  @Test
  fun openingSendsNothingAndAnalyzeSendsExactlyOneRequest() = runBlocking {
    // AC2
    val viewModel = viewModel()
    assertEquals(ReviewUiState.NotAnalyzed(), viewModel.await { it !is ReviewUiState.Loading })
    assertEquals(0, api.requestCount)
    // 開いただけでは取り込まない。
    assertNull(cards.analysis(HASH))

    api.enqueue(AnalysisFixture.JapaneseSingle)
    viewModel.analyze()
    viewModel.await { it is ReviewUiState.Reviewing }

    assertEquals(1, api.requestCount)
  }

  @Test
  fun japaneseAndEnglishCardsHaveFnAndSaveOneCardEach() = runBlocking {
    // AC3
    for (fixture in listOf(AnalysisFixture.JapaneseSingle, AnalysisFixture.EnglishSingle)) {
      reset()
      val viewModel = viewModel().also { it.await { state -> state is ReviewUiState.NotAnalyzed } }
      api.enqueue(fixture)

      val reviewing = viewModel.analyzeAndReview()

      assertTrue(fixture.name, reviewing.review.candidates.single().candidate.fn!!.value.isNotBlank())
      viewModel.saveAndAwait()
      assertEquals(fixture.name, 1, cards.cards(HASH).size)
    }
  }

  @Test
  fun threePeopleNeedOneRequestAndAreAllSavedByDefault() = runBlocking {
    // AC15・AC19: 編集せずに「解析 → 保存」だけで 3 件保存される。
    val viewModel = viewModel().also { it.await { state -> state is ReviewUiState.NotAnalyzed } }
    api.enqueue(AnalysisFixture.ThreePeople)

    val reviewing = viewModel.analyzeAndReview()
    assertEquals(3, reviewing.review.candidates.size)
    assertEquals(setOf(0, 1, 2), reviewing.review.selected)
    viewModel.saveAndAwait()

    assertEquals(1, api.requestCount)
    assertEquals(listOf("山田 太郎", "Jane Doe", "佐藤 花子"), cards.cards(HASH).map { it.fn.value })
    assertEquals(AnalysisStatus.Completed, cards.analysis(HASH)!!.status)
  }

  @Test
  fun deselectingSecondCandidateSavesTwoAndKeepsTheImage() = runBlocking {
    // AC16
    val viewModel = viewModel().also { it.await { state -> state is ReviewUiState.NotAnalyzed } }
    api.enqueue(AnalysisFixture.ThreePeople)
    viewModel.analyzeAndReview()

    viewModel.select(1, include = false)
    viewModel.saveAndAwait()

    assertEquals(listOf("山田 太郎", "佐藤 花子"), cards.cards(HASH).map { it.fn.value })
    val image = cards.sourceImage(HASH)!!
    assertTrue(cards.file(image).exists())
  }

  @Test
  fun deselectingEveryCandidateSavesNothingAndDeletesTheCopy() = runBlocking {
    // AC17
    val viewModel = viewModel().also { it.await { state -> state is ReviewUiState.NotAnalyzed } }
    api.enqueue(AnalysisFixture.ThreePeople)
    viewModel.analyzeAndReview()
    val copied = cards.file(cards.sourceImage(HASH)!!)

    (0..2).forEach { viewModel.select(it, include = false) }
    viewModel.saveAndAwait()

    assertTrue(cards.cards(HASH).isEmpty())
    assertNull(cards.sourceImage(HASH))
    assertFalse(copied.exists())
  }

  @Test
  fun noCardsFoundIsNotAFailureAndSavesNothing() = runBlocking {
    // AC18
    val viewModel = viewModel().also { it.await { state -> state is ReviewUiState.NotAnalyzed } }
    api.enqueue(AnalysisFixture.NoCard)

    val reviewing = viewModel.analyzeAndReview()
    assertTrue(reviewing.review.isNoCardsFound)
    assertEquals(AnalysisStatus.AwaitingReview(emptyList()), cards.analysis(HASH)!!.status)
    viewModel.saveAndAwait()

    assertTrue(cards.cards(HASH).isEmpty())
  }

  @Test
  fun yellowCandidateIsSavedAndRedIsLeftOut() = runBlocking {
    // AC4・AC20
    val viewModel = viewModel().also { it.await { state -> state is ReviewUiState.NotAnalyzed } }
    api.enqueue(AnalysisFixture.NeedsAttention)

    val reviewing = viewModel.analyzeAndReview()
    assertEquals(listOf(Attention.Blocking, Attention.Warning), reviewing.review.candidates.map { it.attention })
    viewModel.saveAndAwait()

    assertEquals(listOf("鈴木 一郎"), cards.cards(HASH).map { it.fn.value })
  }

  @Test
  fun incompleteSettingsBlockAnalysis() = runBlocking {
    // AC13
    settings.value = ByoSettingsSummary.Empty
    val viewModel = viewModel().also { it.await { state -> state is ReviewUiState.NotAnalyzed } }

    viewModel.analyze()

    assertEquals(ReviewUiState.NotAnalyzed(), viewModel.state.value)
    assertEquals(0, api.requestCount)
    assertNull(cards.analysis(HASH))
  }

  @Test
  fun failureShowsReasonAndRetrySendsOneMoreRequest() = runBlocking {
    // AC7
    val viewModel = viewModel().also { it.await { state -> state is ReviewUiState.NotAnalyzed } }
    api.enqueueStatus(401)
    viewModel.analyze()

    assertEquals(
      ReviewUiState.NotAnalyzed(Failure.Analysis(AnalysisFailure.Authentication)),
      viewModel.await { it is ReviewUiState.NotAnalyzed },
    )
    assertEquals(1, api.requestCount)

    api.enqueue(AnalysisFixture.JapaneseSingle)
    viewModel.analyzeAndReview()
    assertEquals(2, api.requestCount)
  }

  @Test
  fun reopeningShowsTheStoredReadResultWithoutSendingAgain() = runBlocking {
    val first = viewModel().also { it.await { state -> state is ReviewUiState.NotAnalyzed } }
    api.enqueue(AnalysisFixture.ThreePeople)
    first.analyzeAndReview()

    val reopened = viewModel().await { it !is ReviewUiState.Loading }

    assertEquals(3, (reopened as ReviewUiState.Reviewing).review.candidates.size)
    assertEquals(1, api.requestCount)
  }

  @Test
  fun reopeningACompletedImageLeadsToItsImageDetail() = runBlocking {
    val first = viewModel().also { it.await { state -> state is ReviewUiState.NotAnalyzed } }
    api.enqueue(AnalysisFixture.ThreePeople)
    first.analyzeAndReview()
    first.saveAndAwait()

    val reopened = viewModel().await { it !is ReviewUiState.Loading }

    assertEquals(ReviewUiState.AlreadyCompleted(HASH), reopened)
    assertEquals(1, api.requestCount)
  }

  private suspend fun ReviewViewModel.analyzeAndReview(): ReviewUiState.Reviewing {
    analyze()
    return await { it is ReviewUiState.Reviewing || it is ReviewUiState.NotAnalyzed } as ReviewUiState.Reviewing
  }

  private suspend fun ReviewViewModel.saveAndAwait() {
    save()
    await { it == ReviewUiState.Done }
  }

  /** 実スレッドで進む DB と HTTP を、実時間で待つ。 */
  private suspend fun ReviewViewModel.await(predicate: (ReviewUiState) -> Boolean): ReviewUiState =
    withContext(Dispatchers.Default) { withTimeout(5_000) { state.first(predicate) } }

  private suspend fun reset() {
    withContext(Dispatchers.IO) { database.clearAllTables() }
    imageDirectory.deleteRecursively()
  }

  private companion object {
    const val DATABASE = "review-view-model-test.db"
    const val TREE = "content://provider/tree/cards"
    const val NAME = "card.png"
    const val DOCUMENT = "$TREE/document/$NAME"
    val IMAGE = pngBytes(40, 25)
    val HASH =
      ContentHash(MessageDigest.getInstance("SHA-256").digest(IMAGE).joinToString("") { "%02x".format(it) })
    val COMPLETE = ByoSettingsSummary(baseUrl = "https://api.example.com/v1", model = "m", hasApiKey = true)
  }
}
