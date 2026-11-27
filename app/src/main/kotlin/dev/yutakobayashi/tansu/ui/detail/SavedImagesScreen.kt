package dev.yutakobayashi.tansu.ui.detail

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yutakobayashi.tansu.R
import dev.yutakobayashi.tansu.appContainer
import dev.yutakobayashi.tansu.data.card.CardRepository
import dev.yutakobayashi.tansu.data.card.SavedImage
import dev.yutakobayashi.tansu.domain.Card
import dev.yutakobayashi.tansu.domain.ContentHash
import dev.yutakobayashi.tansu.domain.CopiedFileRef
import dev.yutakobayashi.tansu.domain.PropertyValue
import dev.yutakobayashi.tansu.domain.SourceImage
import dev.yutakobayashi.tansu.domain.Uid
import dev.yutakobayashi.tansu.ui.theme.TansuTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 保存済みの名刺画像の一覧。読み込む前は `null`。 */
class SavedImagesViewModel(private val cards: CardRepository) : ViewModel() {
  private val _images = MutableStateFlow<List<SavedImageItem>?>(null)
  val images: StateFlow<List<SavedImageItem>?> = _images.asStateFlow()

  /** 一覧を読み直す。画像詳細で名刺を削除して戻ったときに反映するため、画面に戻るたびに呼ぶ。 */
  fun refresh() {
    viewModelScope.launch {
      _images.value = cards.savedImages().map { SavedImageItem(it, cards.file(it.image)) }
    }
  }
}

/** 一覧の 1 行。[file] はアプリ内の複製で、フォルダの原本が消えても読める。 */
data class SavedImageItem(val saved: SavedImage, val file: File)

@Composable
fun SavedImagesScreen(onBack: () -> Unit, onOpenImage: (ContentHash) -> Unit, modifier: Modifier = Modifier) {
  val container = LocalContext.current.appContainer
  val viewModel: SavedImagesViewModel = viewModel { SavedImagesViewModel(container.cardRepository) }
  val images by viewModel.images.collectAsStateWithLifecycle()
  LifecycleResumeEffect(viewModel) {
    viewModel.refresh()
    onPauseOrDispose {}
  }
  val loader = container.copiedImages
  SavedImagesScreen(
    images = images,
    onBack = onBack,
    onOpenImage = onOpenImage,
    loadImage = { file, sizePx -> loader.load(file, sizePx) },
    modifier = modifier,
  )
}

/**
 * 名刺を保存した名刺画像を、最後に保存したものから並べる。
 *
 * フォルダの一覧とは別に、アプリ内の複製から作る。原本をフォルダから消した画像にも、ここから辿り着ける（AC21）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedImagesScreen(
  images: List<SavedImageItem>?,
  onBack: () -> Unit,
  onOpenImage: (ContentHash) -> Unit,
  modifier: Modifier = Modifier,
  loadImage: CopiedImageLoader = { _, _ -> null },
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.saved_images_title)) },
        navigationIcon = {
          TextButton(onClick = onBack, modifier = Modifier.testTag(SavedImagesTestTags.BACK)) {
            Text(stringResource(R.string.action_back))
          }
        },
      )
    },
  ) { innerPadding ->
    Box(Modifier.padding(innerPadding).fillMaxSize()) {
      when {
        images == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        images.isEmpty() ->
          Text(
            stringResource(R.string.saved_images_empty),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp).testTag(SavedImagesTestTags.EMPTY),
          )
        else ->
          LazyColumn(
            Modifier.fillMaxSize().testTag(SavedImagesTestTags.LIST),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            itemsIndexed(images, key = { _, item -> item.saved.image.contentHash.value }) { index, item ->
              SavedImageRow(index, item, onClick = { onOpenImage(item.saved.image.contentHash) }, loadImage)
            }
          }
      }
    }
  }
}

@Composable
private fun SavedImageRow(index: Int, item: SavedImageItem, onClick: () -> Unit, loadImage: CopiedImageLoader) {
  val sizePx = with(LocalDensity.current) { THUMBNAIL_WIDTH.roundToPx() }
  val thumbnail by produceState<ImageBitmap?>(null, item.file, sizePx) { value = loadImage(item.file, sizePx) }
  val cards = item.saved.cards
  Row(
    Modifier.fillMaxWidth()
      .clickable(onClick = onClick)
      .semantics(mergeDescendants = true) {}
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .testTag(SavedImagesTestTags.row(index)),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier.width(THUMBNAIL_WIDTH)
        .aspectRatio(4f / 3f)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
      thumbnail?.let {
        Image(
          it,
          contentDescription = stringResource(R.string.review_image_description),
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        cards.joinToString("、") { it.fn.value },
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        stringResource(R.string.saved_images_count, cards.size),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private val THUMBNAIL_WIDTH = 96.dp

object SavedImagesTestTags {
  const val BACK = "saved_images_back"
  const val LIST = "saved_images_list"
  const val EMPTY = "saved_images_empty"

  fun row(index: Int) = "saved_images_row_$index"
}

@Preview(showBackground = true)
@Composable
private fun SavedImagesScreenPreview() {
  fun card(id: Long, fn: String) = Card.of(Uid(UUID(0, id)), PropertyValue.of(fn)!!)
  fun item(hash: Char, vararg cards: Card) =
    SavedImageItem(
      SavedImage(SourceImage(ContentHash(hash.toString().repeat(64)), CopiedFileRef("$hash")), cards.toList()),
      File("$hash"),
    )
  TansuTheme {
    SavedImagesScreen(
      images = listOf(item('a', card(1, "山田 太郎"), card(2, "佐藤 花子")), item('b', card(3, "Jane Doe"))),
      onBack = {},
      onOpenImage = {},
    )
  }
}
