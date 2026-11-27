package dev.yutakobayashi.tansu.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yutakobayashi.tansu.data.card.CardDeletionOutcome
import dev.yutakobayashi.tansu.data.card.CardRepository
import dev.yutakobayashi.tansu.domain.Card
import dev.yutakobayashi.tansu.domain.ContentHash
import dev.yutakobayashi.tansu.domain.Uid
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 画像詳細の状態。 */
sealed interface ImageDetailUiState {
  data object Loading : ImageDetailUiState

  /** 名刺画像がもう保存されていない。別の画面で最後の名刺を削除した場合など。 */
  data object Missing : ImageDetailUiState

  /**
   * @property file アプリ内の複製。フォルダの原本ではない。
   * @property cards この名刺画像から保存した名刺。保存した順。
   * @property isDeleting 削除の途中。終わるまで他の削除と共有を受け付けない。
   */
  data class Loaded(val file: File, val cards: List<Card>, val isDeleting: Boolean = false) : ImageDetailUiState

  /** 最後の名刺を削除し、名刺画像の複製も削除した。画面を閉じる。 */
  data object Discarded : ImageDetailUiState
}

/** 画像詳細が 1 回だけ行うこと。 */
sealed interface ImageDetailEvent {
  data class Share(val image: SharedImage) : ImageDetailEvent

  data object ShareFailed : ImageDetailEvent
}

/**
 * 名刺画像 1 枚から保存した名刺を一覧し、名刺の削除と画像の共有を行う。
 *
 * 画像はアプリ内の複製から読むので、フォルダの原本を消しても開ける（AC21）。
 * 名刺を削除して 0 件になると、複製も削除される。原本には触れない（AC26、ADR 0005）。
 */
class ImageDetailViewModel(
  private val sourceImage: ContentHash,
  private val cards: CardRepository,
  private val sharedImages: SharedImageFiles,
) : ViewModel() {
  private val _state = MutableStateFlow<ImageDetailUiState>(ImageDetailUiState.Loading)
  val state: StateFlow<ImageDetailUiState> = _state.asStateFlow()

  private val _events = Channel<ImageDetailEvent>(Channel.BUFFERED)
  val events: Flow<ImageDetailEvent> = _events.receiveAsFlow()

  init {
    viewModelScope.launch { _state.value = load() }
  }

  /** 名刺 [uid] を削除する。削除の途中なら何もしない。 */
  fun delete(uid: Uid) {
    val current = _state.value as? ImageDetailUiState.Loaded ?: return
    if (current.isDeleting || current.cards.none { it.uid == uid }) return
    _state.value = current.copy(isDeleting = true)
    viewModelScope.launch {
      _state.value =
        when (cards.deleteCard(sourceImage, uid)) {
          CardDeletionOutcome.CardsRemain -> load()
          CardDeletionOutcome.SourceImageDiscarded -> ImageDetailUiState.Discarded
        }
    }
  }

  /** 名刺画像の複製を共有シートへ渡す。 */
  fun share() {
    val current = _state.value as? ImageDetailUiState.Loaded ?: return
    if (current.isDeleting) return
    viewModelScope.launch {
      val event =
        try {
          sharedImages.prepare(current.file)?.let(ImageDetailEvent::Share) ?: ImageDetailEvent.ShareFailed
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          ImageDetailEvent.ShareFailed
        }
      _events.send(event)
    }
  }

  private suspend fun load(): ImageDetailUiState {
    val image = cards.sourceImage(sourceImage) ?: return ImageDetailUiState.Missing
    val saved = cards.cards(sourceImage)
    if (saved.isEmpty()) return ImageDetailUiState.Missing
    return ImageDetailUiState.Loaded(cards.file(image), saved)
  }
}
