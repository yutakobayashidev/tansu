package dev.yutakobayashi.tansu.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.yutakobayashi.tansu.data.card.CardRepository
import dev.yutakobayashi.tansu.data.card.SharedImageFiles
import dev.yutakobayashi.tansu.data.card.SourceImageFiles
import dev.yutakobayashi.tansu.data.card.TansuDatabase
import dev.yutakobayashi.tansu.data.folder.FakeDocument
import dev.yutakobayashi.tansu.data.folder.FakeFolderAccess
import dev.yutakobayashi.tansu.data.folder.FolderRepository
import dev.yutakobayashi.tansu.data.folder.InMemoryFolderStore
import dev.yutakobayashi.tansu.data.settings.ByoSettingsSummary
import dev.yutakobayashi.tansu.domain.ImageProgress
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val main = UnconfinedTestDispatcher()
  private val io = Dispatchers.IO
  private val access = FakeFolderAccess()

  private lateinit var database: TansuDatabase
  private lateinit var viewModel: HomeViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(main)
    context.deleteDatabase(DATABASE)
    database = TansuDatabase.build(context, DATABASE)
    val cards = CardRepository(
      database,
      SourceImageFiles(File(context.filesDir, "source-images")),
      SharedImageFiles(File(context.cacheDir, "exports/images")),
      io,
    )
    val folders = FolderRepository(InMemoryFolderStore(), access, database, cards, io)
    viewModel = HomeViewModel(MutableStateFlow(ByoSettingsSummary(baseUrl = "", model = "", hasApiKey = false)), folders)
  }

  @After
  fun tearDown() {
    database.close()
    context.deleteDatabase(DATABASE)
    Dispatchers.resetMain()
  }

  @Test
  fun notSelectedBeforeChoosingFolder() = runBlocking {
    viewModel.refresh()

    assertEquals(FolderUiState.NotSelected, awaitFolder { it != FolderUiState.Loading })
  }

  @Test
  fun resumingRightAfterSelectionStillListsSelectedFolder() = runBlocking {
    // フォルダ選択画面から戻ると、選択結果のすぐ後に画面の再開で refresh が呼ばれる。
    access.documents += FakeDocument.of(TREE, "a.jpg", 1, ByteArray(16) { it.toByte() })

    viewModel.onFolderSelected(TREE)
    viewModel.refresh()

    val loaded = awaitFolder { it is FolderUiState.Loaded && it.images.all { image -> image.progress != null } }
      as FolderUiState.Loaded
    assertEquals(listOf("a.jpg" to ImageProgress.Unprocessed), loaded.images.map { it.displayName to it.progress })
    assertEquals(setOf(TREE), access.persisted)
  }

  @Test
  fun lostPermissionAsksToReselect() = runBlocking {
    viewModel.onFolderSelected(TREE)
    awaitFolder { it is FolderUiState.Loaded }
    access.persisted.clear()

    viewModel.refresh()

    assertEquals(FolderUiState.Unavailable, awaitFolder { it !is FolderUiState.Loaded })
  }

  /** 実スレッドで進む保存と列挙を、実時間で待つ。 */
  private suspend fun awaitFolder(predicate: (FolderUiState) -> Boolean): FolderUiState =
    withContext(Dispatchers.Default) { withTimeout(5_000) { viewModel.folder.first(predicate) } }

  private companion object {
    const val DATABASE = "home-view-model-test.db"
    const val TREE = "content://provider/tree/cards"
  }
}
