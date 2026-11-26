package dev.yutakobayashi.tansu.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yutakobayashi.tansu.data.settings.ByoSettingsSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(settings: StateFlow<ByoSettingsSummary>) : ViewModel() {
  /** 解析に必要な設定が揃っているか。揃っていなければ解析へ進めない（AC13）。 */
  val isSettingsComplete: StateFlow<Boolean> =
    settings
      .map { it.isComplete }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.value.isComplete)
}
