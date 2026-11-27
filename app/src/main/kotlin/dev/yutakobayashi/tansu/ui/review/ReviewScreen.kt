package dev.yutakobayashi.tansu.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yutakobayashi.tansu.R
import dev.yutakobayashi.tansu.appContainer
import dev.yutakobayashi.tansu.data.analysis.AnalysisFailure
import dev.yutakobayashi.tansu.domain.Attention
import dev.yutakobayashi.tansu.domain.Candidate
import dev.yutakobayashi.tansu.domain.CandidateReview
import dev.yutakobayashi.tansu.domain.CardDetails
import dev.yutakobayashi.tansu.domain.ContentHash
import dev.yutakobayashi.tansu.domain.FolderImage
import dev.yutakobayashi.tansu.domain.PropertyValue
import dev.yutakobayashi.tansu.domain.ReadResultReview
import dev.yutakobayashi.tansu.ui.home.FolderThumbnails
import dev.yutakobayashi.tansu.ui.home.SettingsRequiredNotice
import dev.yutakobayashi.tansu.ui.theme.TansuTheme
import kotlinx.coroutines.Dispatchers

/** 表示用の画像を読み込む関数。読めなければ `null` を返す。 */
typealias ImageLoader = suspend (sizePx: Int) -> ImageBitmap?

@Composable
fun ReviewScreen(
  documentUri: String,
  displayName: String,
  onBack: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenDetail: (ContentHash) -> Unit,
  modifier: Modifier = Modifier,
) {
  val container = LocalContext.current.appContainer
  val viewModel: ReviewViewModel = viewModel {
    ReviewViewModel(
      documentUri = documentUri,
      settings = container.byoSettingsRepository.summary,
      folders = container.folderRepository,
      cards = container.cardRepository,
      runner = container.analysisRunner,
    )
  }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val isSettingsComplete by viewModel.isSettingsComplete.collectAsStateWithLifecycle()
  val images = remember(container) { FolderThumbnails(container.folderAccess, Dispatchers.IO) }
  LaunchedEffect(state) {
    when (val current = state) {
      ReviewUiState.Done -> onBack()
      // レビューを終えた画像を開いたら、保存済みの名刺を操作できる画像詳細へ移る。
      is ReviewUiState.AlreadyCompleted -> onOpenDetail(current.sourceImage)
      else -> Unit
    }
  }
  ReviewScreen(
    title = displayName,
    state = state,
    isSettingsComplete = isSettingsComplete,
    onBack = onBack,
    onOpenSettings = onOpenSettings,
    onAnalyze = viewModel::analyze,
    onSelect = viewModel::select,
    onSave = viewModel::save,
    loadImage = { sizePx -> images.load(FolderImage(documentUri, displayName, null, null), sizePx) },
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewScreen(
  title: String,
  state: ReviewUiState,
  isSettingsComplete: Boolean,
  onBack: () -> Unit,
  onOpenSettings: () -> Unit,
  onAnalyze: () -> Unit,
  onSelect: (index: Int, include: Boolean) -> Unit,
  onSave: () -> Unit,
  modifier: Modifier = Modifier,
  loadImage: ImageLoader = { null },
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
          TextButton(onClick = onBack, modifier = Modifier.testTag(ReviewTestTags.BACK)) {
            Text(stringResource(R.string.action_back))
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier.padding(innerPadding).padding(horizontal = 16.dp).padding(bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // 候補を切り替えても画像は動かさない。
      FixedImage(loadImage)
      Box(Modifier.weight(1f).fillMaxWidth()) {
        when (state) {
          ReviewUiState.Loading,
          ReviewUiState.Done -> Centered { CircularProgressIndicator() }
          ReviewUiState.Unavailable -> Message(stringResource(R.string.review_unavailable))
          is ReviewUiState.AlreadyCompleted -> Message(stringResource(R.string.review_already_completed))
          ReviewUiState.Analyzing ->
            Centered {
              Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.testTag(ReviewTestTags.ANALYZING))
                Text(stringResource(R.string.review_analyzing))
              }
            }
          is ReviewUiState.NotAnalyzed ->
            BeforeAnalysis(state.failure, isSettingsComplete, onOpenSettings, onAnalyze)
          is ReviewUiState.Reviewing ->
            if (state.review.isNoCardsFound) {
              NoCardsFound(enabled = !state.isSaving, onClose = onSave)
            } else {
              Candidates(state.review, enabled = !state.isSaving, onSelect = onSelect, onSave = onSave)
            }
        }
      }
    }
  }
}

@Composable
private fun FixedImage(loadImage: ImageLoader) {
  val sizePx = with(LocalDensity.current) { IMAGE_SIZE.roundToPx() }
  val image by produceState<ImageBitmap?>(null, sizePx) { value = loadImage(sizePx) }
  Box(
    Modifier.fillMaxWidth()
      .heightIn(max = IMAGE_MAX_HEIGHT)
      .clip(RoundedCornerShape(8.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .testTag(ReviewTestTags.IMAGE),
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
    } ?: Box(Modifier.fillMaxWidth().heightIn(min = IMAGE_MAX_HEIGHT))
  }
}

@Composable
private fun BeforeAnalysis(
  failure: Failure?,
  isSettingsComplete: Boolean,
  onOpenSettings: () -> Unit,
  onAnalyze: () -> Unit,
) {
  Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (!isSettingsComplete) SettingsRequiredNotice(onOpenSettings = onOpenSettings)
    if (failure != null) {
      Card(
        modifier = Modifier.fillMaxWidth().testTag(ReviewTestTags.FAILURE),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
      ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(stringResource(R.string.review_failed_title), style = MaterialTheme.typography.titleMedium)
          Text(stringResource(failure.message()), style = MaterialTheme.typography.bodyMedium)
        }
      }
    } else {
      Text(stringResource(R.string.review_analyze_hint), style = MaterialTheme.typography.bodyMedium)
    }
    Button(
      onClick = onAnalyze,
      enabled = isSettingsComplete,
      modifier = Modifier.fillMaxWidth().testTag(ReviewTestTags.ANALYZE_BUTTON),
    ) {
      Text(stringResource(if (failure == null) R.string.review_analyze else R.string.review_retry))
    }
  }
}

private fun Failure.message(): Int =
  when (this) {
    is Failure.Analysis ->
      when (reason) {
        AnalysisFailure.Authentication -> R.string.review_failed_authentication
        AnalysisFailure.Network -> R.string.review_failed_network
        AnalysisFailure.RateLimited -> R.string.review_failed_rate_limited
        AnalysisFailure.InvalidResponse -> R.string.review_failed_invalid_response
        AnalysisFailure.UnreadableImage -> R.string.review_failed_unreadable_image
      }
    Failure.NotConfigured -> R.string.review_failed_not_configured
    Failure.Unknown -> R.string.review_failed_unknown
  }

/** 該当なし（AC18）。失敗とは色も文言も分け、名刺が得られなかった理由を示す。 */
@Composable
private fun NoCardsFound(enabled: Boolean, onClose: () -> Unit) {
  Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Card(
      modifier = Modifier.fillMaxWidth().testTag(ReviewTestTags.NO_CARDS_FOUND),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.review_no_cards_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.review_no_cards_body), style = MaterialTheme.typography.bodyMedium)
      }
    }
    Button(
      onClick = onClose,
      enabled = enabled,
      modifier = Modifier.fillMaxWidth().testTag(ReviewTestTags.SAVE_BUTTON),
    ) {
      Text(stringResource(R.string.review_no_cards_close))
    }
  }
}

@Composable
private fun Candidates(
  review: ReadResultReview,
  enabled: Boolean,
  onSelect: (index: Int, include: Boolean) -> Unit,
  onSave: () -> Unit,
) {
  val pagerState = rememberPagerState { review.candidates.size }
  Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      stringResource(R.string.review_page, pagerState.currentPage + 1, review.candidates.size),
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier.testTag(ReviewTestTags.PAGE_INDICATOR),
    )
    HorizontalPager(
      state = pagerState,
      pageSpacing = 16.dp,
      verticalAlignment = Alignment.Top,
      modifier = Modifier.weight(1f).fillMaxWidth().testTag(ReviewTestTags.PAGER),
    ) { index ->
      CandidatePage(
        index = index,
        review = review.candidates[index],
        selected = review.isSelected(index),
        enabled = enabled,
        onSelect = { onSelect(index, it) },
      )
    }
    val count = review.selected.size
    Button(
      onClick = onSave,
      enabled = enabled,
      modifier = Modifier.fillMaxWidth().testTag(ReviewTestTags.SAVE_BUTTON),
    ) {
      Text(if (count == 0) stringResource(R.string.review_save_none) else stringResource(R.string.review_save, count))
    }
  }
}

@Composable
private fun CandidatePage(
  index: Int,
  review: CandidateReview,
  selected: Boolean,
  enabled: Boolean,
  onSelect: (Boolean) -> Unit,
) {
  val attention = review.attention
  val colors = attentionColors(attention)
  Card(
    modifier = Modifier.fillMaxWidth().testTag(ReviewTestTags.candidate(index, attention)),
    border = colors?.let { BorderStroke(2.dp, it.content) },
  ) {
    Column(
      Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      val confirmable = review.candidate.isConfirmable
      Row(
        Modifier.fillMaxWidth()
          .toggleable(
            value = selected,
            enabled = enabled && confirmable,
            role = Role.Switch,
            onValueChange = onSelect,
          )
          .testTag(ReviewTestTags.toggle(index)),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(stringResource(R.string.review_include), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Switch(checked = selected, onCheckedChange = null, enabled = enabled && confirmable)
      }
      if (attention == Attention.Blocking) {
        Banner(stringResource(R.string.review_blocking), Attention.Blocking, ReviewTestTags.blockingNotice(index))
      }
      if (review.malformedEmails.isNotEmpty()) {
        Banner(stringResource(R.string.review_warning), Attention.Warning, ReviewTestTags.warningNotice(index))
      }
      if (review.sharesFn) {
        Banner(stringResource(R.string.review_same_fn), null, ReviewTestTags.sameFnNotice(index))
      }
      Properties(index, review)
    }
  }
}

@Composable
private fun Properties(index: Int, review: CandidateReview) {
  val candidate = review.candidate
  val details = candidate.details
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    PropertyRow(
      label = stringResource(R.string.property_fn),
      value = candidate.fn?.value ?: stringResource(R.string.review_empty_value),
      attention = if (candidate.fn == null) Attention.Blocking else Attention.None,
      tag = ReviewTestTags.property(index, "FN"),
    )
    details.n?.let { PropertyRow(stringResource(R.string.property_n), it.value) }
    details.org?.let { PropertyRow(stringResource(R.string.property_org), it.value) }
    details.title?.let { PropertyRow(stringResource(R.string.property_title), it.value) }
    details.tel.forEach { PropertyRow(stringResource(R.string.property_tel), it.value) }
    details.email.forEachIndexed { emailIndex, email ->
      PropertyRow(
        label = stringResource(R.string.property_email),
        value = email.value,
        attention = if (emailIndex in review.malformedEmails) Attention.Warning else Attention.None,
        tag = ReviewTestTags.property(index, "EMAIL", emailIndex),
      )
    }
    details.adr?.let { PropertyRow(stringResource(R.string.property_adr), it.value) }
    details.url?.let { PropertyRow(stringResource(R.string.property_url), it.value) }
    details.note?.let { PropertyRow(stringResource(R.string.property_note), it.value) }
  }
}

@Composable
internal fun PropertyRow(label: String, value: String, attention: Attention = Attention.None, tag: String? = null) {
  val colors = attentionColors(attention)
  Surface(
    color = colors?.container ?: Color.Transparent,
    contentColor = colors?.onContainer ?: MaterialTheme.colorScheme.onSurface,
    shape = RoundedCornerShape(4.dp),
    // 項目名と値を 1 つの読み上げ単位にする。
    modifier =
      Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.let { if (tag != null) it.testTag(tag) else it },
  ) {
    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(PROPERTY_LABEL_WIDTH))
      Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
  }
}

/** [attention] が `null` なら色を付けない中立の注意（同名の警告）として出す。 */
@Composable
private fun Banner(text: String, attention: Attention?, tag: String) {
  val colors = attention?.let { attentionColors(it) }
  Surface(
    color = colors?.container ?: MaterialTheme.colorScheme.secondaryContainer,
    contentColor = colors?.onContainer ?: MaterialTheme.colorScheme.onSecondaryContainer,
    shape = RoundedCornerShape(4.dp),
    modifier = Modifier.fillMaxWidth().testTag(tag),
  ) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
  }
}

private data class AttentionColors(val container: Color, val onContainer: Color, val content: Color)

/** 黄と赤の色。注意がなければ `null`。 */
@Composable
private fun attentionColors(attention: Attention): AttentionColors? =
  when (attention) {
    Attention.None -> null
    Attention.Warning ->
      if (isSystemInDarkTheme()) {
        AttentionColors(Color(0xFF4D3D00), Color(0xFFFFE07A), Color(0xFFE3C14A))
      } else {
        AttentionColors(Color(0xFFFFF1B8), Color(0xFF3D3000), Color(0xFFB08A00))
      }
    Attention.Blocking ->
      AttentionColors(
        MaterialTheme.colorScheme.errorContainer,
        MaterialTheme.colorScheme.onErrorContainer,
        MaterialTheme.colorScheme.error,
      )
  }

@Composable
private fun Centered(content: @Composable () -> Unit) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Message(text: String) {
  Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
}

private val IMAGE_SIZE = 720.dp
private val IMAGE_MAX_HEIGHT = 220.dp
private val PROPERTY_LABEL_WIDTH = 72.dp

object ReviewTestTags {
  const val BACK = "review_back"
  const val IMAGE = "review_image"
  const val ANALYZE_BUTTON = "review_analyze_button"
  const val ANALYZING = "review_analyzing"
  const val FAILURE = "review_failure"
  const val NO_CARDS_FOUND = "review_no_cards_found"
  const val PAGER = "review_pager"
  const val PAGE_INDICATOR = "review_page_indicator"
  const val SAVE_BUTTON = "review_save_button"

  /** 候補のカード。注意の段階を含めて、色の段階をテストで確かめられるようにする。 */
  fun candidate(index: Int, attention: Attention) = "review_candidate_${index}_${attention.name}"

  fun toggle(index: Int) = "review_toggle_$index"

  fun blockingNotice(index: Int) = "review_blocking_$index"

  fun warningNotice(index: Int) = "review_warning_$index"

  fun sameFnNotice(index: Int) = "review_same_fn_$index"

  /** 項目の行。`EMAIL` のように複数ある項目は [position] で区別する。 */
  fun property(index: Int, name: String, position: Int = 0) = "review_property_${index}_${name}_$position"
}

private val PREVIEW_HASH = ContentHash("0".repeat(64))

private val PREVIEW_REVIEW =
  ReadResultReview.of(
    listOf(
      Candidate.fromRaw(
        PREVIEW_HASH,
        "山田 太郎",
        CardDetails.fromRaw(org = "株式会社サンプル商事", title = "営業部 部長", email = listOf("taro@@example")),
      ),
      Candidate.fromRaw(PREVIEW_HASH, null, CardDetails.fromRaw(org = "架空大学")),
      Candidate.fromRaw(PREVIEW_HASH, "山田 太郎", CardDetails(org = PropertyValue.of("Example Inc."))),
    )
  )

@Preview(showBackground = true)
@Composable
private fun ReviewScreenCandidatesPreview() {
  TansuTheme {
    ReviewScreen(
      title = "card-a.jpg",
      state = ReviewUiState.Reviewing(PREVIEW_REVIEW),
      isSettingsComplete = true,
      onBack = {},
      onOpenSettings = {},
      onAnalyze = {},
      onSelect = { _, _ -> },
      onSave = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun ReviewScreenNotAnalyzedPreview() {
  TansuTheme {
    ReviewScreen(
      title = "card-a.jpg",
      state = ReviewUiState.NotAnalyzed(),
      isSettingsComplete = false,
      onBack = {},
      onOpenSettings = {},
      onAnalyze = {},
      onSelect = { _, _ -> },
      onSave = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun ReviewScreenNoCardsPreview() {
  TansuTheme {
    ReviewScreen(
      title = "card-a.jpg",
      state = ReviewUiState.Reviewing(ReadResultReview.of(emptyList())),
      isSettingsComplete = true,
      onBack = {},
      onOpenSettings = {},
      onAnalyze = {},
      onSelect = { _, _ -> },
      onSave = {},
    )
  }
}
