package dev.yutakobayashi.tansu.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yutakobayashi.tansu.R
import dev.yutakobayashi.tansu.appContainer
import dev.yutakobayashi.tansu.domain.FolderImage
import dev.yutakobayashi.tansu.domain.ImageProgress
import dev.yutakobayashi.tansu.ui.export.ExportAction
import dev.yutakobayashi.tansu.ui.theme.TansuTheme
import kotlinx.coroutines.Dispatchers

/** 縮小画像を読み込む関数。読めなければ `null` を返す。 */
typealias ThumbnailLoader = suspend (image: FolderImage, sizePx: Int) -> ImageBitmap?

@Composable
fun HomeScreen(
  onOpenSettings: () -> Unit,
  onOpenImage: (FolderImage) -> Unit,
  onOpenSavedImages: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val container = LocalContext.current.appContainer
  val viewModel: HomeViewModel = viewModel {
    HomeViewModel(container.byoSettingsRepository.summary, container.folderRepository)
  }
  val isSettingsComplete by viewModel.isSettingsComplete.collectAsStateWithLifecycle()
  val folder by viewModel.folder.collectAsStateWithLifecycle()
  val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
  val pickFolder =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      uri?.let { viewModel.onFolderSelected(it.toString()) }
    }
  // フォルダ選択や他の画面から戻ったときにも、フォルダの変更と解析の進み具合を反映する。
  LifecycleResumeEffect(viewModel) {
    viewModel.refresh()
    onPauseOrDispose {}
  }
  val thumbnails = remember(container) { FolderThumbnails(container.folderAccess, Dispatchers.IO) }
  HomeScreen(
    isSettingsComplete = isSettingsComplete,
    folder = folder,
    onOpenSettings = onOpenSettings,
    onOpenImage = onOpenImage,
    onOpenSavedImages = onOpenSavedImages,
    onPickFolder = { pickFolder.launch(selectedFolder?.toUri()) },
    loadThumbnail = thumbnails::load,
    exportAction = { ExportAction() },
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
  isSettingsComplete: Boolean,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
  onOpenImage: (FolderImage) -> Unit = {},
  onOpenSavedImages: () -> Unit = {},
  folder: FolderUiState = FolderUiState.NotSelected,
  onPickFolder: () -> Unit = {},
  loadThumbnail: ThumbnailLoader = { _, _ -> null },
  exportAction: @Composable () -> Unit = {},
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
          TextButton(onClick = onOpenSavedImages, modifier = Modifier.testTag(HomeTestTags.SAVED_IMAGES_ACTION)) {
            Text(stringResource(R.string.saved_images_action))
          }
          exportAction()
          TextButton(onClick = onOpenSettings, modifier = Modifier.testTag(HomeTestTags.SETTINGS_ACTION)) {
            Text(stringResource(R.string.settings_title))
          }
        },
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier.padding(innerPadding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      if (!isSettingsComplete) {
        SettingsRequiredNotice(onOpenSettings = onOpenSettings)
      }
      FolderSection(
        folder = folder,
        onPickFolder = onPickFolder,
        onOpenImage = onOpenImage,
        loadThumbnail = loadThumbnail,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

/** 設定が未完了のときに、解析できない理由と設定画面への入口を示す。 */
@Composable
fun SettingsRequiredNotice(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier.fillMaxWidth().testTag(HomeTestTags.SETTINGS_REQUIRED_NOTICE),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stringResource(R.string.settings_required_title), style = MaterialTheme.typography.titleMedium)
      Text(stringResource(R.string.settings_required_body), style = MaterialTheme.typography.bodyMedium)
      OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings_required_action)) }
    }
  }
}

@Composable
private fun FolderSection(
  folder: FolderUiState,
  onPickFolder: () -> Unit,
  onOpenImage: (FolderImage) -> Unit,
  loadThumbnail: ThumbnailLoader,
  modifier: Modifier = Modifier,
) {
  when (folder) {
    FolderUiState.Loading ->
      Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    FolderUiState.NotSelected ->
      FolderNotice(
        title = stringResource(R.string.folder_not_selected_title),
        body = stringResource(R.string.folder_not_selected_body),
        action = stringResource(R.string.folder_pick),
        onAction = onPickFolder,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.testTag(HomeTestTags.FOLDER_NOT_SELECTED),
      )
    FolderUiState.Unavailable ->
      FolderNotice(
        title = stringResource(R.string.folder_unavailable_title),
        body = stringResource(R.string.folder_unavailable_body),
        action = stringResource(R.string.folder_repick),
        onAction = onPickFolder,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.testTag(HomeTestTags.FOLDER_UNAVAILABLE),
      )
    is FolderUiState.Loaded ->
      Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            folder.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag(HomeTestTags.FOLDER_NAME),
          )
          TextButton(onClick = onPickFolder, modifier = Modifier.testTag(HomeTestTags.FOLDER_CHANGE)) {
            Text(stringResource(R.string.folder_change))
          }
        }
        if (folder.images.isEmpty()) {
          Text(stringResource(R.string.folder_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
          FolderImageGrid(folder.images, onOpenImage, loadThumbnail)
        }
      }
  }
}

@Composable
private fun FolderNotice(
  title: String,
  body: String,
  action: String,
  onAction: () -> Unit,
  containerColor: Color,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onAction, modifier = Modifier.testTag(HomeTestTags.FOLDER_PICK)) { Text(action) }
      }
    }
  }
}

@Composable
private fun FolderImageGrid(
  images: List<FolderImage>,
  onOpenImage: (FolderImage) -> Unit,
  loadThumbnail: ThumbnailLoader,
) {
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 104.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxSize().testTag(HomeTestTags.FOLDER_GRID),
  ) {
    items(images, key = { it.uri }) { image -> FolderImageTile(image, { onOpenImage(image) }, loadThumbnail) }
  }
}

@Composable
private fun FolderImageTile(image: FolderImage, onClick: () -> Unit, loadThumbnail: ThumbnailLoader) {
  val sizePx = with(LocalDensity.current) { THUMBNAIL_SIZE.roundToPx() }
  val thumbnail by
    produceState<ImageBitmap?>(null, image.uri, image.lastModified, image.size) {
      value = loadThumbnail(image, sizePx)
    }
  val badge = image.progress?.let { progressLabel(it) }
  val description = listOfNotNull(image.displayName, badge).joinToString("、")
  Column(
    // 画像を開くとレビュー画面へ進む。解析は開いた先で利用者が明示的に始める（AC2）。
    modifier =
      Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).semantics(mergeDescendants = true) {
        contentDescription = description
      },
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Box(
      Modifier.fillMaxWidth()
        .aspectRatio(4f / 3f)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
      thumbnail?.let {
        // 名刺の文字が切れないよう、切り抜かずに全体を収める。
        Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
      }
      image.progress?.let { ProgressBadge(it, Modifier.align(Alignment.TopStart).padding(4.dp)) }
    }
    Text(
      image.displayName,
      style = MaterialTheme.typography.bodySmall,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun ProgressBadge(progress: ImageProgress, modifier: Modifier = Modifier) {
  val (container, content) =
    when (progress) {
      ImageProgress.Unprocessed -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
      ImageProgress.AwaitingReview ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
      ImageProgress.Completed -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    }
  Surface(
    color = container,
    contentColor = content,
    shape = RoundedCornerShape(4.dp),
    modifier = modifier.testTag(HomeTestTags.progressBadge(progress)),
  ) {
    Text(
      progressLabel(progress),
      style = MaterialTheme.typography.labelSmall,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}

@Composable
private fun progressLabel(progress: ImageProgress): String =
  stringResource(
    when (progress) {
      ImageProgress.Unprocessed -> R.string.progress_unprocessed
      ImageProgress.AwaitingReview -> R.string.progress_awaiting_review
      ImageProgress.Completed -> R.string.progress_completed
    }
  )

private val THUMBNAIL_SIZE = 160.dp

object HomeTestTags {
  const val SETTINGS_ACTION = "home_settings_action"
  const val SAVED_IMAGES_ACTION = "home_saved_images_action"
  const val SETTINGS_REQUIRED_NOTICE = "home_settings_required_notice"
  const val FOLDER_NOT_SELECTED = "home_folder_not_selected"
  const val FOLDER_UNAVAILABLE = "home_folder_unavailable"
  const val FOLDER_PICK = "home_folder_pick"
  const val FOLDER_CHANGE = "home_folder_change"
  const val FOLDER_NAME = "home_folder_name"
  const val FOLDER_GRID = "home_folder_grid"

  fun progressBadge(progress: ImageProgress) = "home_progress_badge_${progress.name}"
}

private val PREVIEW_IMAGES =
  listOf(
    FolderImage("c", "card-c.jpg", 3, 1, ImageProgress.AwaitingReview),
    FolderImage("b", "card-b.jpg", 2, 1, ImageProgress.Completed),
    FolderImage("a", "card-a.jpg", 1, 1, ImageProgress.Unprocessed),
    FolderImage("d", "card-d.jpg", 0, 1, null),
  )

@Preview(showBackground = true)
@Composable
private fun HomeScreenIncompletePreview() {
  TansuTheme { HomeScreen(isSettingsComplete = false, onOpenSettings = {}) }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenCompletePreview() {
  TansuTheme { HomeScreen(isSettingsComplete = true, onOpenSettings = {}) }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenFolderPreview() {
  TansuTheme {
    HomeScreen(
      isSettingsComplete = true,
      onOpenSettings = {},
      folder = FolderUiState.Loaded("名刺", PREVIEW_IMAGES),
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenFolderUnavailablePreview() {
  TansuTheme {
    HomeScreen(isSettingsComplete = true, onOpenSettings = {}, folder = FolderUiState.Unavailable)
  }
}
