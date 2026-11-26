package dev.yutakobayashi.tansu.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yutakobayashi.tansu.data.folder.FolderListing
import dev.yutakobayashi.tansu.data.folder.FolderRepository
import dev.yutakobayashi.tansu.data.settings.ByoSettingsSummary
import dev.yutakobayashi.tansu.domain.FolderImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** ホーム画面に出すフォルダの状態。 */
sealed interface FolderUiState {
  /** 初回の列挙中。 */
  data object Loading : FolderUiState

  data object NotSelected : FolderUiState

  /** フォルダが消えたか権限を失った。選び直しを促す。 */
  data object Unavailable : FolderUiState

  /** [images] は更新日時の新しい順。 */
  data class Loaded(val name: String, val images: List<FolderImage>) : FolderUiState
}

class HomeViewModel(
  settings: StateFlow<ByoSettingsSummary>,
  private val folders: FolderRepository,
) : ViewModel() {
  /** 解析に必要な設定が揃っているか。揃っていなければ解析へ進めない（AC13）。 */
  val isSettingsComplete: StateFlow<Boolean> =
    settings
      .map { it.isComplete }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.value.isComplete)

  private val _folder = MutableStateFlow<FolderUiState>(FolderUiState.Loading)
  val folder: StateFlow<FolderUiState> = _folder.asStateFlow()

  /** フォルダ選択画面の初期位置に渡す、選択中のフォルダ。 */
  val selectedFolder: StateFlow<String?> = folders.selected

  private var loading: Job? = null
  private var selecting: Job? = null

  /** フォルダを列挙し直す。画面に戻るたびに呼び、フォルダ側の変更と解析の進み具合を反映する。 */
  fun refresh() {
    loading?.cancel()
    val pending = selecting
    loading =
      viewModelScope.launch {
        // 選択の保存を待つ。フォルダ選択画面から戻ると、選択結果の直後に画面の再開で呼ばれるため。
        pending?.join()
        load()
      }
  }

  /** フォルダ選択画面で選ばれた tree URI を保存して列挙する。 */
  fun onFolderSelected(treeUri: String) {
    val previous = selecting
    // 保存は refresh で取り消さない。途中で止めると、権限だけ取って保存しない状態が残る。
    selecting =
      viewModelScope.launch {
        previous?.join()
        try {
          folders.select(treeUri)
        } catch (_: SecurityException) {
          // provider が永続化できる権限を渡さなかった。選び直してもらう。
        }
      }
    _folder.value = FolderUiState.Loading
    refresh()
  }

  private suspend fun load() {
    when (val listing = folders.list()) {
      FolderListing.NotSelected -> _folder.value = FolderUiState.NotSelected
      is FolderListing.Unavailable -> _folder.value = FolderUiState.Unavailable
      is FolderListing.Listed -> {
        // 前回の進み具合を引き継ぎ、ハッシュを求め終えるまでバッジが消えないようにする。
        val previous = (_folder.value as? FolderUiState.Loaded)?.images.orEmpty().associateBy { it.uri }
        val listed =
          listing.images.map { image ->
            val known = previous[image.uri]
            if (known != null && known.lastModified == image.lastModified && known.size == image.size) {
              image.copy(progress = known.progress)
            } else {
              image
            }
          }
        _folder.value = FolderUiState.Loaded(listing.name, listed)
        _folder.value = FolderUiState.Loaded(listing.name, folders.withProgress(listing.images))
      }
    }
  }
}
