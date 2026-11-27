package dev.yutakobayashi.tansu.data.folder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.yutakobayashi.tansu.data.card.CardRepository
import dev.yutakobayashi.tansu.data.card.SharedImageFiles
import dev.yutakobayashi.tansu.data.card.SourceImageFiles
import dev.yutakobayashi.tansu.data.card.TansuDatabase
import dev.yutakobayashi.tansu.domain.Candidate
import dev.yutakobayashi.tansu.domain.ConfirmationResult
import dev.yutakobayashi.tansu.domain.FolderImage
import dev.yutakobayashi.tansu.domain.ImageProgress
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderRepositoryTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val imageDirectory = File(context.filesDir, "source-images")
  private val store = InMemoryFolderStore()
  private val access = FakeFolderAccess()

  private lateinit var database: TansuDatabase
  private lateinit var cards: CardRepository
  private lateinit var repository: FolderRepository

  @Before
  fun setUp() {
    context.deleteDatabase(DATABASE)
    imageDirectory.deleteRecursively()
    database = TansuDatabase.build(context, DATABASE)
    cards = CardRepository(
      database,
      SourceImageFiles(imageDirectory),
      SharedImageFiles(File(context.cacheDir, "exports/images")),
      Dispatchers.Unconfined,
    )
    repository = FolderRepository(store, access, database, cards, Dispatchers.Unconfined)
  }

  @After
  fun tearDown() {
    database.close()
    context.deleteDatabase(DATABASE)
    imageDirectory.deleteRecursively()
  }

  @Test
  fun notSelectedUntilFolderIsChosen() = runTest {
    assertNull(repository.selected.value)
    assertEquals(FolderListing.NotSelected, repository.list())
  }

  @Test
  fun selectingFolderPersistsPermissionAndRemembersIt() = runTest {
    repository.select(TREE)

    assertEquals(setOf(TREE), access.persisted)
    assertEquals(TREE, store.read())
    assertEquals(TREE, repository.selected.value)
  }

  @Test
  fun selectingAnotherFolderReleasesPreviousPermission() = runTest {
    repository.select(TREE)
    repository.select(OTHER_TREE)

    assertEquals(setOf(OTHER_TREE), access.persisted)
    assertEquals(OTHER_TREE, store.read())
  }

  @Test
  fun reselectingSameFolderKeepsPermission() = runTest {
    repository.select(TREE)
    repository.select(TREE)

    assertEquals(setOf(TREE), access.persisted)
  }

  @Test
  fun listsImagesNewestFirst() = runTest {
    // AC1: 更新日時の降順。同時刻は名前順、更新日時の無い画像は末尾。
    access.documents +=
      listOf(
        document("old.jpg", lastModified = 1_000),
        document("unknown.jpg", lastModified = null),
        document("new.jpg", lastModified = 3_000),
        document("b-mid.jpg", lastModified = 2_000),
        document("a-mid.jpg", lastModified = 2_000),
      )
    repository.select(TREE)

    val listing = repository.list() as FolderListing.Listed

    assertEquals("名刺", listing.name)
    assertEquals(
      listOf("new.jpg", "a-mid.jpg", "b-mid.jpg", "old.jpg", "unknown.jpg"),
      listing.images.map { it.displayName },
    )
  }

  @Test
  fun lostPermissionAsksToReselect() = runTest {
    repository.select(TREE)
    access.persisted.clear()

    assertEquals(FolderListing.Unavailable(TREE), repository.list())
  }

  @Test
  fun deletedFolderAsksToReselect() = runTest {
    repository.select(TREE)
    access.folderExists = false

    assertEquals(FolderListing.Unavailable(TREE), repository.list())
  }

  @Test
  fun progressFollowsAnalysisOfSameContent() = runTest {
    // AC1: 保存済み（完了）と確認待ちにバッジが付く。取り込み前の画像は未処理。
    access.documents +=
      listOf(
        document("completed.jpg", lastModified = 3, bytes = COMPLETED),
        document("awaiting.jpg", lastModified = 2, bytes = AWAITING),
        document("new.jpg", lastModified = 1, bytes = NEW),
      )
    repository.select(TREE)
    complete(COMPLETED)
    awaitReview(AWAITING)

    val progress = progressByName()

    assertEquals(
      mapOf(
        "completed.jpg" to ImageProgress.Completed,
        "awaiting.jpg" to ImageProgress.AwaitingReview,
        "new.jpg" to ImageProgress.Unprocessed,
      ),
      progress,
    )
  }

  @Test
  fun copyOfSavedImageIsAlsoCompleted() = runTest {
    // 解析は内容ハッシュ単位なので、同じ内容の別ファイルも完了として扱う（AC5 と整合）。
    access.documents += listOf(document("a.jpg", 2, bytes = COMPLETED), document("a-copy.jpg", 1, bytes = COMPLETED))
    repository.select(TREE)
    complete(COMPLETED)

    assertEquals(
      mapOf("a.jpg" to ImageProgress.Completed, "a-copy.jpg" to ImageProgress.Completed),
      progressByName(),
    )
  }

  @Test
  fun unchangedImagesAreNotReadAgain() = runTest {
    access.documents += listOf(document("a.jpg", 1, bytes = NEW), document("b.jpg", 2, bytes = AWAITING))
    repository.select(TREE)

    progressByName()
    progressByName()

    assertEquals(mapOf("a.jpg" to 1, "b.jpg" to 1), access.openCounts)
  }

  @Test
  fun modifiedImageIsReadAgain() = runTest {
    access.documents += document("a.jpg", 1, bytes = NEW)
    repository.select(TREE)
    progressByName()
    complete(COMPLETED)

    access.documents[0] = document("a.jpg", 2, bytes = COMPLETED)

    assertEquals(mapOf("a.jpg" to ImageProgress.Completed), progressByName())
    assertEquals(mapOf("a.jpg" to 2), access.openCounts)
  }

  @Test
  fun unreadableImageHasNoBadge() = runTest {
    access.documents += document("broken.jpg", 1, bytes = null)
    repository.select(TREE)

    val images = (repository.list() as FolderListing.Listed).images

    assertNull(repository.withProgress(images).single().progress)
  }

  private suspend fun progressByName(): Map<String, ImageProgress?> {
    val images = (repository.list() as FolderListing.Listed).images
    return repository.withProgress(images).associate { it.displayName to it.progress }
  }

  private suspend fun awaitReview(bytes: ByteArray) {
    val analysis = cards.import(ByteArrayInputStream(bytes))
    cards.startAnalysis(analysis.sourceImage)
    cards.recordReadResult(analysis.sourceImage, listOf(Candidate.fromRaw(analysis.sourceImage, "山田 太郎")))
  }

  private suspend fun complete(bytes: ByteArray) {
    awaitReview(bytes)
    val hash = cards.import(ByteArrayInputStream(bytes)).sourceImage
    val card = (Candidate.fromRaw(hash, "山田 太郎").confirm() as ConfirmationResult.Confirmed).card
    cards.completeReview(hash, listOf(card))
  }

  private fun document(name: String, lastModified: Long?, bytes: ByteArray? = NEW) =
    FakeDocument.of(TREE, name, lastModified, bytes)

  private companion object {
    const val DATABASE = "folder-repository-test.db"
    const val TREE = "content://provider/tree/cards"
    const val OTHER_TREE = "content://provider/tree/other"
    val NEW = ByteArray(1024) { (it * 7 % 253).toByte() }
    val AWAITING = ByteArray(1024) { (it * 11 % 251).toByte() }
    val COMPLETED = ByteArray(1024) { (it * 13 % 241).toByte() }
  }
}
