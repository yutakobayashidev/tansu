package dev.yutakobayashi.tansu.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yutakobayashi.tansu.export.CardExporter
import dev.yutakobayashi.tansu.export.ExportFormat
import dev.yutakobayashi.tansu.export.ExportResult
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 書き出しの後に画面が 1 回だけ行うこと。 */
sealed interface ExportEvent {
  /** 書き出した [file] を共有シートで渡す。 */
  data class Share(val file: File, val format: ExportFormat) : ExportEvent

  data object NoCards : ExportEvent

  data object Failed : ExportEvent
}

class ExportViewModel(private val exporter: CardExporter) : ViewModel() {
  private val _isExporting = MutableStateFlow(false)
  val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

  private val _events = Channel<ExportEvent>(Channel.BUFFERED)
  val events: Flow<ExportEvent> = _events.receiveAsFlow()

  /** 書き出し中なら何もしない。同じファイルを 2 つの書き出しが同時に置き換えないようにする。 */
  fun export(format: ExportFormat) {
    if (_isExporting.value) return
    _isExporting.value = true
    viewModelScope.launch {
      val event =
        try {
          when (val result = exporter.export(format)) {
            is ExportResult.Exported -> ExportEvent.Share(result.file, result.format)
            ExportResult.NoCards -> ExportEvent.NoCards
          }
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          // 入出力の失敗に加えて、壊れた画像の縮小や DB の読み取りの失敗でも、書き出し中のまま固まらず失敗を知らせる。
          ExportEvent.Failed
        } finally {
          _isExporting.value = false
        }
      _events.send(event)
    }
  }
}
