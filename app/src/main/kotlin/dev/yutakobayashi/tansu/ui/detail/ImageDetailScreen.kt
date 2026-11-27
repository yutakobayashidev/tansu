package dev.yutakobayashi.tansu.ui.detail

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yutakobayashi.tansu.R
import dev.yutakobayashi.tansu.appContainer
import dev.yutakobayashi.tansu.data.card.SharedImage
import dev.yutakobayashi.tansu.domain.Card
import dev.yutakobayashi.tansu.domain.CardDetails
import dev.yutakobayashi.tansu.domain.ContentHash
import dev.yutakobayashi.tansu.domain.PropertyValue
import dev.yutakobayashi.tansu.domain.Uid
import dev.yutakobayashi.tansu.ui.review.PropertyRow
import dev.yutakobayashi.tansu.ui.theme.TansuTheme
import java.io.File
import java.util.UUID

@Composable
fun ImageDetailScreen(sourceImage: ContentHash, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val container = context.appContainer
  val viewModel: ImageDetailViewModel = viewModel {
    ImageDetailViewModel(sourceImage, container.cardRepository)
  }
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(state) {
    if (state == ImageDetailUiState.Discarded) {
      Toast.makeText(context, R.string.detail_discarded, Toast.LENGTH_SHORT).show()
      onBack()
    }
  }
  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is ImageDetailEvent.Share -> context.share(event.image)
        ImageDetailEvent.ShareFailed ->
          Toast.makeText(context, R.string.detail_share_failed, Toast.LENGTH_SHORT).show()
        ImageDetailEvent.DeleteFailed ->
          Toast.makeText(context, R.string.detail_delete_failed, Toast.LENGTH_SHORT).show()
      }
    }
  }
  val images = container.copiedImages
  ImageDetailScreen(
    state = state,
    onBack = onBack,
    onShare = viewModel::share,
    onDelete = viewModel::delete,
    loadImage = { file, sizePx -> images.load(file, sizePx) },
    modifier = modifier,
  )
}

/** 表示用に縮小した複製を読み込む関数。読めなければ `null` を返す。 */
typealias CopiedImageLoader = suspend (file: File, sizePx: Int) -> ImageBitmap?

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageDetailScreen(
  state: ImageDetailUiState,
  onBack: () -> Unit,
  onShare: () -> Unit,
  onDelete: (Uid) -> Unit,
  modifier: Modifier = Modifier,
  loadImage: CopiedImageLoader = { _, _ -> null },
) {
  val loaded = state as? ImageDetailUiState.Loaded
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.detail_title)) },
        navigationIcon = {
          TextButton(onClick = onBack, modifier = Modifier.testTag(ImageDetailTestTags.BACK)) {
            Text(stringResource(R.string.action_back))
          }
        },
        actions = {
          if (loaded != null) {
            TextButton(
              onClick = onShare,
              enabled = !loaded.isDeleting,
              modifier = Modifier.testTag(ImageDetailTestTags.SHARE),
            ) {
              Text(stringResource(R.string.detail_share))
            }
          }
        },
      )
    },
  ) { innerPadding ->
    Box(Modifier.padding(innerPadding).fillMaxSize()) {
      when (state) {
        ImageDetailUiState.Loading,
        ImageDetailUiState.Discarded ->
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        ImageDetailUiState.Missing ->
          Text(
            stringResource(R.string.detail_missing),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp).testTag(ImageDetailTestTags.MISSING),
          )
        is ImageDetailUiState.Loaded -> Loaded(state, onDelete, loadImage)
      }
    }
  }
}

@Composable
private fun Loaded(state: ImageDetailUiState.Loaded, onDelete: (Uid) -> Unit, loadImage: CopiedImageLoader) {
  var isFullScreen by rememberSaveable { mutableStateOf(false) }
  // 削除を確かめている名刺。画面を回しても確認を続けられるよう、UID の文字列で覚える。
  var confirming by rememberSaveable { mutableStateOf<String?>(null) }
  Column(
    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    CopiedImage(
      state.file,
      loadImage,
      maxHeight = IMAGE_MAX_HEIGHT,
      modifier =
        Modifier.clip(RoundedCornerShape(8.dp))
          .clickable(onClickLabel = stringResource(R.string.detail_view_full)) { isFullScreen = true }
          .testTag(ImageDetailTestTags.IMAGE),
    )
    Text(
      stringResource(R.string.detail_cards, state.cards.size),
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.testTag(ImageDetailTestTags.CARD_COUNT),
    )
    state.cards.forEachIndexed { index, card ->
      SavedCardItem(
        index = index,
        card = card,
        enabled = !state.isDeleting,
        onDelete = { confirming = card.uid.toString() },
      )
    }
  }
  if (isFullScreen) FullScreenImage(state.file, loadImage, onDismiss = { isFullScreen = false })
  val target = state.cards.firstOrNull { it.uid.toString() == confirming }
  if (target != null) {
    DeleteDialog(
      card = target,
      isLast = state.cards.size == 1,
      onConfirm = {
        confirming = null
        onDelete(target.uid)
      },
      onDismiss = { confirming = null },
    )
  }
}

@Composable
private fun CopiedImage(file: File, loadImage: CopiedImageLoader, maxHeight: Dp, modifier: Modifier = Modifier) {
  val sizePx = with(LocalDensity.current) { IMAGE_SIZE.roundToPx() }
  val image by produceState<ImageBitmap?>(null, file, sizePx) { value = loadImage(file, sizePx) }
  Box(
    modifier.fillMaxWidth().heightIn(max = maxHeight).background(MaterialTheme.colorScheme.surfaceVariant),
    contentAlignment = Alignment.Center,
  ) {
    image?.let {
      // 名刺の文字が切れないよう、切り抜かずに全体を収める。
      Image(
        it,
        contentDescription = stringResource(R.string.review_image_description),
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth(),
      )
    } ?: Box(Modifier.fillMaxWidth().heightIn(min = maxHeight.coerceAtMost(IMAGE_MAX_HEIGHT)))
  }
}

/** 画像を画面いっぱいに出す。どこを押しても閉じる。 */
@Composable
private fun FullScreenImage(file: File, loadImage: CopiedImageLoader, onDismiss: () -> Unit) {
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Box(
      Modifier.fillMaxSize()
        .background(Color.Black)
        .clickable(onClickLabel = stringResource(R.string.detail_close_full), onClick = onDismiss)
        .testTag(ImageDetailTestTags.FULL_SCREEN),
      contentAlignment = Alignment.Center,
    ) {
      CopiedImage(file, loadImage, maxHeight = FULL_SCREEN_MAX_HEIGHT, modifier = Modifier.background(Color.Black))
    }
  }
}

@Composable
private fun SavedCardItem(index: Int, card: Card, enabled: Boolean, onDelete: () -> Unit) {
  Card(Modifier.fillMaxWidth().testTag(ImageDetailTestTags.card(index))) {
    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      PropertyRow(stringResource(R.string.property_fn), card.fn.value)
      val details = card.details
      details.n?.let { PropertyRow(stringResource(R.string.property_n), it.value) }
      details.org?.let { PropertyRow(stringResource(R.string.property_org), it.value) }
      details.title?.let { PropertyRow(stringResource(R.string.property_title), it.value) }
      details.tel.forEach { PropertyRow(stringResource(R.string.property_tel), it.value) }
      details.email.forEach { PropertyRow(stringResource(R.string.property_email), it.value) }
      details.adr?.let { PropertyRow(stringResource(R.string.property_adr), it.value) }
      details.url?.let { PropertyRow(stringResource(R.string.property_url), it.value) }
      details.note?.let { PropertyRow(stringResource(R.string.property_note), it.value) }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDelete, enabled = enabled, modifier = Modifier.testTag(ImageDetailTestTags.delete(index))) {
          Text(stringResource(R.string.detail_delete), color = if (enabled) MaterialTheme.colorScheme.error else Color.Unspecified)
        }
      }
    }
  }
}

/** 削除の確認。最後の 1 件なら、アプリ内の複製も消えることを伝える。どちらでも原本は残る。 */
@Composable
private fun DeleteDialog(card: Card, isLast: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.detail_delete_title, card.fn.value)) },
    text = {
      Text(stringResource(if (isLast) R.string.detail_delete_body_last else R.string.detail_delete_body))
    },
    confirmButton = {
      TextButton(onClick = onConfirm, modifier = Modifier.testTag(ImageDetailTestTags.DELETE_CONFIRM)) {
        Text(stringResource(R.string.detail_delete_confirm), color = MaterialTheme.colorScheme.error)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, modifier = Modifier.testTag(ImageDetailTestTags.DELETE_CANCEL)) {
        Text(stringResource(R.string.detail_delete_cancel))
      }
    },
    modifier = Modifier.testTag(ImageDetailTestTags.DELETE_DIALOG),
  )
}

/** 名刺画像の複製を `ACTION_SEND` で渡す。受け取り側には読み取りだけを一時的に許す。 */
private fun Context.share(image: SharedImage) {
  val uri = FileProvider.getUriForFile(this, "$packageName.exports", image.file)
  val send =
    Intent(Intent.ACTION_SEND).apply {
      type = image.mimeType
      putExtra(Intent.EXTRA_STREAM, uri)
      clipData = ClipData.newRawUri(image.file.name, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  startActivity(Intent.createChooser(send, getString(R.string.detail_share_chooser_title)))
}

private val IMAGE_SIZE = 1080.dp
private val IMAGE_MAX_HEIGHT = 240.dp
private val FULL_SCREEN_MAX_HEIGHT = 2000.dp

object ImageDetailTestTags {
  const val BACK = "detail_back"
  const val SHARE = "detail_share"
  const val IMAGE = "detail_image"
  const val FULL_SCREEN = "detail_full_screen"
  const val CARD_COUNT = "detail_card_count"
  const val MISSING = "detail_missing"
  const val DELETE_DIALOG = "detail_delete_dialog"
  const val DELETE_CONFIRM = "detail_delete_confirm"
  const val DELETE_CANCEL = "detail_delete_cancel"

  fun card(index: Int) = "detail_card_$index"

  fun delete(index: Int) = "detail_delete_$index"
}

private val PREVIEW_CARDS =
  listOf(
    Card.of(
      Uid(UUID(0, 1)),
      PropertyValue.of("山田 太郎")!!,
      CardDetails.fromRaw(org = "株式会社サンプル商事", title = "営業部 部長", email = listOf("taro@example.com")),
    ),
    Card.of(Uid(UUID(0, 2)), PropertyValue.of("佐藤 花子")!!, CardDetails.fromRaw(org = "架空大学")),
  )

@Preview(showBackground = true)
@Composable
private fun ImageDetailScreenPreview() {
  TansuTheme {
    ImageDetailScreen(
      state = ImageDetailUiState.Loaded(File("preview"), PREVIEW_CARDS),
      onBack = {},
      onShare = {},
      onDelete = {},
    )
  }
}
