package dev.yutakobayashi.tansu.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yutakobayashi.tansu.R
import dev.yutakobayashi.tansu.appContainer
import dev.yutakobayashi.tansu.ui.theme.TansuTheme

@Composable
fun HomeScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
  val container = LocalContext.current.appContainer
  val viewModel: HomeViewModel = viewModel { HomeViewModel(container.byoSettingsRepository.summary) }
  val isSettingsComplete by viewModel.isSettingsComplete.collectAsStateWithLifecycle()
  HomeScreen(
    isSettingsComplete = isSettingsComplete,
    onOpenSettings = onOpenSettings,
    // 解析の起動は後続の Issue で実装する。ここでは設定未完了時に押せないことだけを担う。
    onAnalyze = {},
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
  isSettingsComplete: Boolean,
  onOpenSettings: () -> Unit,
  onAnalyze: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
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
      Button(
        onClick = onAnalyze,
        enabled = isSettingsComplete,
        modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.ANALYZE_BUTTON),
      ) {
        Text(stringResource(R.string.home_analyze))
      }
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

object HomeTestTags {
  const val ANALYZE_BUTTON = "home_analyze_button"
  const val SETTINGS_ACTION = "home_settings_action"
  const val SETTINGS_REQUIRED_NOTICE = "home_settings_required_notice"
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenIncompletePreview() {
  TansuTheme { HomeScreen(isSettingsComplete = false, onOpenSettings = {}, onAnalyze = {}) }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenCompletePreview() {
  TansuTheme { HomeScreen(isSettingsComplete = true, onOpenSettings = {}, onAnalyze = {}) }
}
