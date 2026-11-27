package dev.yutakobayashi.tansu.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yutakobayashi.tansu.data.analysis.AnalysisFailure
import dev.yutakobayashi.tansu.data.analysis.AnalysisRunResult
import dev.yutakobayashi.tansu.data.analysis.AnalysisRunner
import dev.yutakobayashi.tansu.data.card.CardRepository
import dev.yutakobayashi.tansu.data.folder.FolderRepository
import dev.yutakobayashi.tansu.data.settings.ByoSettingsSummary
import dev.yutakobayashi.tansu.domain.Analysis
import dev.yutakobayashi.tansu.domain.AnalysisStatus
import dev.yutakobayashi.tansu.domain.ContentHash
import dev.yutakobayashi.tansu.domain.ReadResultReview
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** レビュー画面の状態。画像は固定し、解析の進み具合に応じて下半分だけが変わる。 */
sealed interface ReviewUiState {
  /** 画像の内容ハッシュを求め、保存済みの解析を読んでいる。 */
  data object Loading : ReviewUiState

  /** フォルダの画像を読めない。 */
  data object Unavailable : ReviewUiState

  /**
   * 解析の前、または失敗の後。解析ボタンを出す。
   *
   * @property failure 直前の解析が失敗した理由。保存済みの `失敗` を読んだときは理由が分からないので
   *   [AnalysisFailure] を持たない [Failure.Unknown] になる。
   */
  data class NotAnalyzed(val failure: Failure? = null) : ReviewUiState

  data object Analyzing : ReviewUiState

  /** 読み取り結果のレビュー中。候補が 0 件なら該当なし。 */
  data class Reviewing(val review: ReadResultReview, val isSaving: Boolean = false) : ReviewUiState

  /** レビューを終えた。画面を閉じる。 */
  data object Done : ReviewUiState

  /** 既にレビューを終えた画像。保存済みの名刺の操作は画像詳細で扱う。 */
  data object AlreadyCompleted : ReviewUiState
}

/** 解析が失敗した理由。画面の文言を選ぶのに使う。 */
sealed interface Failure {
  data class Analysis(val reason: AnalysisFailure) : Failure

  /** 設定が未完了か、保存した API キーを復号できなかった。 */
  data object NotConfigured : Failure

  data object Unknown : Failure
}

/**
 * フォルダの画像 1 枚を解析し、読み取り結果をレビューして保存する。
 *
 * 画像を開いただけでは取り込みも HTTP リクエストもしない。利用者が解析ボタンを押したときに初めて
 * 取り込み、解析を 1 回だけ実行する（AC2）。保存は既定で確定できる候補すべてを対象にする（AC15）。
 */
class ReviewViewModel(
  private val documentUri: String,
  private val settings: StateFlow<ByoSettingsSummary>,
  private val folders: FolderRepository,
  private val cards: CardRepository,
  private val runner: AnalysisRunner,
) : ViewModel() {
  /** 解析に必要な設定が揃っているか。揃っていなければ解析ボタンを無効にする（AC13）。 */
  val isSettingsComplete: StateFlow<Boolean> =
    settings
      .map { it.isComplete }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.value.isComplete)

  private val _state = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
  val state: StateFlow<ReviewUiState> = _state.asStateFlow()

  /** 取り込み済みなら、その名刺画像の内容ハッシュ。 */
  private var sourceImage: ContentHash? = null

  init {
    viewModelScope.launch { load() }
  }

  private suspend fun load() {
    val hash =
      folders.contentHash(documentUri)
        ?: run {
          _state.value = ReviewUiState.Unavailable
          return
        }
    val analysis = cards.analysis(hash)
    if (analysis != null) sourceImage = hash
    _state.value = analysis?.toUiState() ?: ReviewUiState.NotAnalyzed()
  }

  /** 画像を取り込み、解析を 1 回実行する。解析前か失敗後でなければ何もしない。 */
  fun analyze() {
    if (_state.value !is ReviewUiState.NotAnalyzed || !settings.value.isComplete) return
    _state.value = ReviewUiState.Analyzing
    viewModelScope.launch {
      val analysis =
        try {
          sourceImage?.let { cards.analysis(it) } ?: folders.import(documentUri)
        } catch (_: IOException) {
          _state.value = ReviewUiState.Unavailable
          return@launch
        } catch (_: SecurityException) {
          _state.value = ReviewUiState.Unavailable
          return@launch
        }
      sourceImage = analysis.sourceImage
      // 同じ内容の画像を既に解析していれば、送り直さずにその結果を出す。
      when (analysis.status) {
        AnalysisStatus.NotAnalyzed, AnalysisStatus.Failed -> Unit
        else -> {
          _state.value = analysis.toUiState()
          return@launch
        }
      }
      _state.value =
        when (val result = runner.run(analysis.sourceImage)) {
          is AnalysisRunResult.Read -> ReviewUiState.Reviewing(ReadResultReview.of(result.candidates))
          is AnalysisRunResult.Failed -> ReviewUiState.NotAnalyzed(Failure.Analysis(result.failure))
          AnalysisRunResult.NotConfigured -> ReviewUiState.NotAnalyzed(Failure.NotConfigured)
        }
    }
  }

  /** [index] の候補を保存対象に入れる、または外す。 */
  fun select(index: Int, include: Boolean) {
    val current = _state.value as? ReviewUiState.Reviewing ?: return
    if (current.isSaving) return
    _state.value = current.copy(review = current.review.select(index, include))
  }

  /**
   * 保存対象の候補を名刺として保存し、レビューを終える。
   *
   * 保存対象が 0 件（該当なしを含む）なら名刺は 0 件になり、名刺画像の複製は削除される（AC17・AC18）。
   */
  fun save() {
    val current = _state.value as? ReviewUiState.Reviewing ?: return
    if (current.isSaving) return
    val hash = checkNotNull(sourceImage) { "Reviewing requires an imported source image" }
    _state.value = current.copy(isSaving = true)
    viewModelScope.launch {
      cards.completeReview(hash, current.review.confirm())
      _state.value = ReviewUiState.Done
    }
  }

  private fun Analysis.toUiState(): ReviewUiState =
    when (val status = status) {
      AnalysisStatus.NotAnalyzed -> ReviewUiState.NotAnalyzed()
      // 解析は ViewModel の寿命の中で終わる。中断されれば `失敗` になるので、ここで見えるのは
      // 別の画面から同じ内容の画像を解析している最中に限られる。
      AnalysisStatus.Analyzing -> ReviewUiState.Analyzing
      is AnalysisStatus.AwaitingReview -> ReviewUiState.Reviewing(ReadResultReview.of(status.candidates))
      AnalysisStatus.Completed -> ReviewUiState.AlreadyCompleted
      AnalysisStatus.Failed -> ReviewUiState.NotAnalyzed(Failure.Unknown)
    }
}
