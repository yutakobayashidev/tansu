package dev.yutakobayashi.tansu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yutakobayashi.tansu.R
import dev.yutakobayashi.tansu.appContainer
import dev.yutakobayashi.tansu.data.settings.ByoSettingsError
import dev.yutakobayashi.tansu.data.settings.ConnectionCheckResult
import dev.yutakobayashi.tansu.ui.theme.TansuTheme

@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
  val container = LocalContext.current.appContainer
  val viewModel: SettingsViewModel = viewModel {
    SettingsViewModel(container.byoSettingsRepository, container.connectionChecker)
  }
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  SettingsScreen(
    uiState = uiState,
    onBack = onBack,
    onBaseUrlChange = viewModel::onBaseUrlChange,
    onModelChange = viewModel::onModelChange,
    onApiKeyChange = viewModel::onApiKeyChange,
    onSave = viewModel::save,
    onCheckConnection = viewModel::checkConnection,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
  uiState: SettingsUiState,
  onBack: () -> Unit,
  onBaseUrlChange: (String) -> Unit,
  onModelChange: (String) -> Unit,
  onApiKeyChange: (String) -> Unit,
  onSave: () -> Unit,
  onCheckConnection: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.padding(innerPadding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(stringResource(R.string.settings_description), style = MaterialTheme.typography.bodyMedium)
      OutlinedTextField(
        value = uiState.baseUrl,
        onValueChange = onBaseUrlChange,
        label = { Text(stringResource(R.string.settings_base_url_label)) },
        placeholder = { Text(stringResource(R.string.settings_base_url_placeholder)) },
        supportingText = {
          Text(
            stringResource(
              if (uiState.error == ByoSettingsError.InvalidBaseUrl) R.string.settings_error_base_url
              else R.string.settings_base_url_help
            )
          )
        },
        isError = uiState.error == ByoSettingsError.InvalidBaseUrl,
        singleLine = true,
        keyboardOptions =
          KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().testTag(SettingsTestTags.BASE_URL),
      )
      OutlinedTextField(
        value = uiState.model,
        onValueChange = onModelChange,
        label = { Text(stringResource(R.string.settings_model_label)) },
        supportingText = {
          Text(
            stringResource(
              if (uiState.error == ByoSettingsError.MissingModel) R.string.settings_error_model
              else R.string.settings_model_help
            )
          )
        },
        isError = uiState.error == ByoSettingsError.MissingModel,
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().testTag(SettingsTestTags.MODEL),
      )
      OutlinedTextField(
        value = uiState.apiKey,
        onValueChange = onApiKeyChange,
        label = { Text(stringResource(R.string.settings_api_key_label)) },
        supportingText = {
          Text(
            stringResource(
              when {
                uiState.error == ByoSettingsError.MissingApiKey -> R.string.settings_error_api_key
                uiState.hasSavedApiKey -> R.string.settings_api_key_saved_help
                else -> R.string.settings_api_key_help
              }
            )
          )
        },
        isError = uiState.error == ByoSettingsError.MissingApiKey,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions =
          KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().testTag(SettingsTestTags.API_KEY),
      )
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onSave, enabled = uiState.canSave, modifier = Modifier.testTag(SettingsTestTags.SAVE)) {
          Text(stringResource(R.string.settings_save))
        }
        if (uiState.isSaved) {
          Text(stringResource(R.string.settings_saved), style = MaterialTheme.typography.bodyMedium)
        }
      }

      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

      Text(stringResource(R.string.settings_check_title), style = MaterialTheme.typography.titleMedium)
      Text(stringResource(R.string.settings_check_description), style = MaterialTheme.typography.bodyMedium)
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
          onClick = onCheckConnection,
          enabled = uiState.canCheckConnection,
          modifier = Modifier.testTag(SettingsTestTags.CHECK_CONNECTION),
        ) {
          Text(stringResource(R.string.settings_check_action))
        }
        if (uiState.connectionCheck == ConnectionCheckState.Running) {
          CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
      }
      if (!uiState.isSavedSettingsComplete) {
        Text(stringResource(R.string.settings_check_requires_saved), style = MaterialTheme.typography.bodySmall)
      }
      (uiState.connectionCheck as? ConnectionCheckState.Done)?.let { done ->
        ConnectionCheckMessage(done.result)
      }
    }
  }
}

@Composable
private fun ConnectionCheckMessage(result: ConnectionCheckResult) {
  val (text, isError) =
    when (result) {
      ConnectionCheckResult.Success -> stringResource(R.string.settings_check_success) to false
      ConnectionCheckResult.NotConfigured -> stringResource(R.string.settings_check_requires_saved) to true
      is ConnectionCheckResult.Unauthorized ->
        stringResource(R.string.settings_check_unauthorized, result.statusCode) to true
      is ConnectionCheckResult.HttpError -> stringResource(R.string.settings_check_http_error, result.statusCode) to true
      ConnectionCheckResult.NetworkError -> stringResource(R.string.settings_check_network_error) to true
    }
  Text(
    text = text,
    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.testTag(SettingsTestTags.CHECK_RESULT),
  )
}

object SettingsTestTags {
  const val BASE_URL = "settings_base_url"
  const val MODEL = "settings_model"
  const val API_KEY = "settings_api_key"
  const val SAVE = "settings_save"
  const val CHECK_CONNECTION = "settings_check_connection"
  const val CHECK_RESULT = "settings_check_result"
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenEmptyPreview() {
  TansuTheme { SettingsScreen(SettingsUiState(), {}, {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenSavedPreview() {
  TansuTheme {
    SettingsScreen(
      SettingsUiState(
        baseUrl = "https://api.example.com/v1",
        model = "example-model",
        hasSavedApiKey = true,
        isSavedSettingsComplete = true,
        connectionCheck = ConnectionCheckState.Done(ConnectionCheckResult.Success),
      ),
      {},
      {},
      {},
      {},
      {},
      {},
    )
  }
}
