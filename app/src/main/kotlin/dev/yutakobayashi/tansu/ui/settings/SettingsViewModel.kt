package dev.yutakobayashi.tansu.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yutakobayashi.tansu.data.settings.ByoSettingsError
import dev.yutakobayashi.tansu.data.settings.ByoSettingsRepository
import dev.yutakobayashi.tansu.data.settings.ConnectionCheckResult
import dev.yutakobayashi.tansu.data.settings.ConnectionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ConnectionCheckState {
  data object Idle : ConnectionCheckState

  data object Running : ConnectionCheckState

  data class Done(val result: ConnectionCheckResult) : ConnectionCheckState
}

data class SettingsUiState(
  val baseUrl: String = "",
  val model: String = "",
  /** 新しく入力された API キー。保存済みの値は画面へ戻さない。 */
  val apiKey: String = "",
  val hasSavedApiKey: Boolean = false,
  /** 保存済みの設定が解析に使える状態か。 */
  val isSavedSettingsComplete: Boolean = false,
  val isSaving: Boolean = false,
  val error: ByoSettingsError? = null,
  val isSaved: Boolean = false,
  val connectionCheck: ConnectionCheckState = ConnectionCheckState.Idle,
) {
  val canSave: Boolean
    get() =
      !isSaving && baseUrl.isNotBlank() && model.isNotBlank() && (apiKey.isNotBlank() || hasSavedApiKey)

  val canCheckConnection: Boolean
    get() = isSavedSettingsComplete && !isSaving && connectionCheck != ConnectionCheckState.Running
}

class SettingsViewModel(
  private val repository: ByoSettingsRepository,
  private val connectionChecker: ConnectionChecker,
) : ViewModel() {

  private val _uiState: MutableStateFlow<SettingsUiState>

  init {
    val saved = repository.summary.value
    _uiState =
      MutableStateFlow(
        SettingsUiState(
          baseUrl = saved.baseUrl,
          model = saved.model,
          hasSavedApiKey = saved.hasApiKey,
          isSavedSettingsComplete = saved.isComplete,
        )
      )
    viewModelScope.launch {
      repository.summary.collect { summary ->
        _uiState.update {
          it.copy(hasSavedApiKey = summary.hasApiKey, isSavedSettingsComplete = summary.isComplete)
        }
      }
    }
  }

  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  fun onBaseUrlChange(value: String) = _uiState.update { it.copy(baseUrl = value, error = null, isSaved = false) }

  fun onModelChange(value: String) = _uiState.update { it.copy(model = value, error = null, isSaved = false) }

  fun onApiKeyChange(value: String) = _uiState.update { it.copy(apiKey = value, error = null, isSaved = false) }

  fun save() {
    val current = _uiState.value
    if (!current.canSave) return
    _uiState.update { it.copy(isSaving = true, error = null, isSaved = false) }
    viewModelScope.launch {
      val error = repository.save(current.baseUrl, current.model, current.apiKey)
      val summary = repository.summary.value
      _uiState.update {
        if (error == null) {
          it.copy(
            baseUrl = summary.baseUrl,
            model = summary.model,
            apiKey = "",
            isSaving = false,
            isSaved = true,
            // 以前の確認結果は保存前の設定に対するものなので消す。
            connectionCheck = ConnectionCheckState.Idle,
          )
        } else {
          it.copy(isSaving = false, error = error)
        }
      }
    }
  }

  /** 保存済みの設定で接続確認を 1 回だけ行う。実行中の再要求は無視する。 */
  fun checkConnection() {
    if (!_uiState.value.canCheckConnection) return
    _uiState.update { it.copy(connectionCheck = ConnectionCheckState.Running) }
    viewModelScope.launch {
      val result = connectionChecker.check()
      _uiState.update { it.copy(connectionCheck = ConnectionCheckState.Done(result)) }
    }
  }
}
