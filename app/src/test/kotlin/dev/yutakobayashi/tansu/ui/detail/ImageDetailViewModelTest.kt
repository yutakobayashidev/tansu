package dev.yutakobayashi.tansu.ui.detail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.yutakobayashi.tansu.data.analysis.pngBytes
import dev.yutakobayashi.tansu.data.card.CardRepository
import dev.yutakobayashi.tansu.data.card.SharedImageFiles
import dev.yutakobayashi.tansu.data.card.SourceImageFiles
import dev.yutakobayashi.tansu.data.card.TansuDatabase
import dev.yutakobayashi.tansu.data.folder.FakeDocument
import dev.yutakobayashi.tansu.data.folder.FakeFolderAccess
import dev.yutakobayashi.tansu.data.folder.FolderListing
import dev.yutakobayashi.tansu.data.folder.FolderRepository
import dev.yutakobayashi.tansu.data.folder.InMemoryFolderStore
import dev.yutakobayashi.tansu.domain.Candidate
import dev.yutakobayashi.tansu.domain.ConfirmationResult
import dev.yutakobayashi.tansu.domain.ContentHash
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
import org.robolectric.annotation.GraphicsMode

/**
 * 画像詳細の流れを、実際の DB と、フォルダの原本を模した [FakeFolderAccess] で確かめる。
 *
 * 原本が残るかは、フォルダの一覧とバイト列が削除の前後で変わらないことで確かめる。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageDetailViewModelTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val imageDirectory = File(context.filesDir, "source-images")
  private val sharedDirectory = File(context.cacheDir, "exports/images")
  private val access = FakeFolderAccess()
  private val store = InMemoryFolderStore()

  private lateinit var database: TansuDatabase
  private lateinit var cards: CardRepository
  private lateinit var folders: FolderRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    context.deleteDatabase(DATABASE)
    imageDirectory.deleteRecursively()
    sharedDirectory.deleteRecursively()
    database = TansuDatabase.build(context, DATABASE)
    cards = CardRepository(
      database,
      SourceImageFiles(imageDirectory),
      SharedImageFiles(sharedDirectory),
      Dispatchers.IO,
    )
    folders = FolderRepository(store, access, database, cards, Dispatchers.IO)
    access.documents += FakeDocument.of(TREE, NAME, 1, IMAGE)
    runBlocking { folders.select(TREE) }
  }

  @After
  fun tearDown() {
    database.close()
    context.deleteDatabase(DATABASE)
    imageDirectory.deleteRecursively()
    sharedDirectory.deleteRecursively()
    Dispatchers.resetMain()
  }

  @Test
  fun showsSavedCardsAndTheCopyInOrder() = runBlocking {
    val hash = saveFromFolder("山田 太郎", "佐藤 花子")

    val state = viewModel(hash).awaitLoaded()

    assertEquals(listOf("山田 太郎", "佐藤 花子"), state.cards.map { it.fn.value })
    assertEquals(imageDirectory, state.file.parentFile)
    assertArrayEquals(IMAGE, state.file.readBytes())
  }

  @Test
  fun opensTheCopyAfterTheOriginalIsRemovedFromTheFolder() = runBlocking {
    // AC21
    val hash = saveFromFolder("山田 太郎")
    access.documents.clear()
    assertTrue((folders.list() as FolderListing.Listed).images.isEmpty())

    val state = viewModel(hash).awaitLoaded()

    assertEquals(listOf("山田 太郎"), state.cards.map { it.fn.value })
    assertArrayEquals(IMAGE, state.file.readBytes())
  }

  @Test
  fun deletingOneOfTwoCardsKeepsTheCopyAndTheOriginal() = runBlocking {
    val hash = saveFromFolder("山田 太郎", "佐藤 花子")
    val viewModel = viewModel(hash)
    val before = viewModel.awaitLoaded()

    viewModel.delete(before.cards[0].uid)
    val after = viewModel.await { it is ImageDetailUiState.Loaded && it.cards.size == 1 } as ImageDetailUiState.Loaded

    assertEquals(listOf("佐藤 花子"), after.cards.map { it.fn.value })
    assertTrue(after.file.exists())
    assertOriginalIsKept()
  }

  @Test
  fun deletingTheLastCardDeletesTheCopyButKeepsTheOriginal() = runBlocking {
    // AC26
    val hash = saveFromFolder("山田 太郎")
    val viewModel = viewModel(hash)
    val loaded = viewModel.awaitLoaded()

    viewModel.delete(loaded.cards[0].uid)
    viewModel.await { it == ImageDetailUiState.Discarded }

    assertTrue(cards.cards(hash).isEmpty())
    assertNull(cards.sourceImage(hash))
    assertNull(cards.analysis(hash))
    assertFalse(loaded.file.exists())
    assertOriginalIsKept()
  }

  @Test
  fun anImageWithoutSavedCardsIsMissing() = runBlocking {
    val state = viewModel(ContentHash("f".repeat(64))).await { it != ImageDetailUiState.Loading }

    assertEquals(ImageDetailUiState.Missing, state)
  }

  @Test
  fun sharingWritesAnExtensionedCopyAndLeavesTheCopyUntouched() = runBlocking {
    val hash = saveFromFolder("山田 太郎")
    val viewModel = viewModel(hash)
    val loaded = viewModel.awaitLoaded()

    viewModel.share()
    val event = withContext(Dispatchers.Default) { withTimeout(5_000) { viewModel.events.first() } }

    val shared = (event as ImageDetailEvent.Share).image
    assertEquals("image/png", shared.mimeType)
    assertEquals("${hash.value}.png", shared.file.name)
    assertEquals(sharedDirectory, shared.file.parentFile)
    assertArrayEquals(IMAGE, shared.file.readBytes())
    assertArrayEquals(IMAGE, loaded.file.readBytes())
  }

  @Test
  fun deletingTheLastCardAfterSharingLeavesNoSharedCopy() = runBlocking {
    // AC26・ADR 0005: 共有用に作った写しも、複製と一緒に消える。
    val hash = saveFromFolder("山田 太郎")
    val viewModel = viewModel(hash)
    val loaded = viewModel.awaitLoaded()
    viewModel.share()
    val event = withContext(Dispatchers.Default) { withTimeout(5_000) { viewModel.events.first() } }
    assertTrue((event as ImageDetailEvent.Share).image.file.exists())

    viewModel.delete(loaded.cards[0].uid)
    viewModel.await { it == ImageDetailUiState.Discarded }

    assertTrue(sharedDirectory.listFiles().orEmpty().isEmpty())
    assertFalse(loaded.file.exists())
    assertOriginalIsKept()
  }

  @Test
  fun aFailedDeletionIsReportedAndTheListIsReloaded() = runBlocking {
    val hash = saveFromFolder("山田 太郎", "佐藤 花子")
    val viewModel = viewModel(hash)
    val loaded = viewModel.awaitLoaded()
    // 別の経路で先に消され、この画面の削除は失敗する。
    cards.deleteCard(hash, loaded.cards[0].uid)

    viewModel.delete(loaded.cards[0].uid)
    val event = withContext(Dispatchers.Default) { withTimeout(5_000) { viewModel.events.first() } }
    val after =
      viewModel.await { it is ImageDetailUiState.Loaded && !it.isDeleting && it.cards.size == 1 }
        as ImageDetailUiState.Loaded

    assertEquals(ImageDetailEvent.DeleteFailed, event)
    assertEquals(listOf("佐藤 花子"), after.cards.map { it.fn.value })
  }

  /** フォルダの画像を取り込み、[names] の候補をすべて確定して保存する。 */
  private suspend fun saveFromFolder(vararg names: String): ContentHash {
    val hash = folders.import(DOCUMENT).sourceImage
    cards.startAnalysis(hash)
    val candidates = names.map { Candidate.fromRaw(hash, fn = it) }
    cards.recordReadResult(hash, candidates)
    cards.completeReview(hash, candidates.map { (it.confirm() as ConfirmationResult.Confirmed).card })
    return hash
  }

  private suspend fun assertOriginalIsKept() {
    val listed = folders.list() as FolderListing.Listed
    assertEquals(listOf(DOCUMENT), listed.images.map { it.uri })
    assertArrayEquals(IMAGE, access.open(DOCUMENT).use { it.readBytes() })
  }

  private fun viewModel(hash: ContentHash) =
    ImageDetailViewModel(hash, cards)

  private suspend fun ImageDetailViewModel.awaitLoaded(): ImageDetailUiState.Loaded =
    await { it is ImageDetailUiState.Loaded } as ImageDetailUiState.Loaded

  /** 実スレッドで進む DB を、実時間で待つ。 */
  private suspend fun ImageDetailViewModel.await(predicate: (ImageDetailUiState) -> Boolean): ImageDetailUiState =
    withContext(Dispatchers.Default) { withTimeout(5_000) { state.first(predicate) } }

  private companion object {
    const val DATABASE = "image-detail-view-model-test.db"
    const val TREE = "content://provider/tree/cards"
    const val NAME = "card.png"
    const val DOCUMENT = "$TREE/document/$NAME"
    val IMAGE = pngBytes(40, 25)
  }
}
