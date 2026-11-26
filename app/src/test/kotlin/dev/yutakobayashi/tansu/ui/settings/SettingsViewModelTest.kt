package dev.yutakobayashi.tansu.ui.settings

import dev.yutakobayashi.tansu.data.settings.ByoSettingsError
import dev.yutakobayashi.tansu.data.settings.ByoSettingsRepository
import dev.yutakobayashi.tansu.data.settings.ConnectionCheckResult
import dev.yutakobayashi.tansu.data.settings.ConnectionChecker
import dev.yutakobayashi.tansu.data.settings.FakeSecretCipher
import dev.yutakobayashi.tansu.data.settings.InMemoryByoSettingsStore
import dev.yutakobayashi.tansu.data.settings.StoredByoSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
  private val dispatcher = UnconfinedTestDispatcher()
  private val store = InMemoryByoSettingsStore()

  private class CountingChecker : ConnectionChecker {
    var calls = 0
    var pending = CompletableDeferred<ConnectionCheckResult>()

    override suspend fun check(): ConnectionCheckResult {
      calls++
      return pending.await()
    }
  }

  private val checker = CountingChecker()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel() =
    SettingsViewModel(ByoSettingsRepository(store, FakeSecretCipher(), dispatcher), checker)

  @Test
  fun emptySettings_cannotSaveOrCheck() = runTest(dispatcher) {
    val state = viewModel().uiState.value
    assertFalse(state.canSave)
    assertFalse(state.canCheckConnection)
  }

  @Test
  fun modelIsRequiredWithoutDefault() = runTest(dispatcher) {
    val viewModel = viewModel()
    viewModel.onBaseUrlChange("https://api.example.com/v1")
    viewModel.onApiKeyChange("sk-secret")
    assertEquals("", viewModel.uiState.value.model)
    assertFalse(viewModel.uiState.value.canSave)

    viewModel.onModelChange("model-a")
    assertTrue(viewModel.uiState.value.canSave)
  }

  @Test
  fun save_clearsApiKeyInputAndEnablesCheck() = runTest(dispatcher) {
    val viewModel = viewModel()
    viewModel.onBaseUrlChange("https://api.example.com/v1/")
    viewModel.onModelChange("model-a")
    viewModel.onApiKeyChange("sk-secret")

    viewModel.save()

    val state = viewModel.uiState.value
    assertTrue(state.isSaved)
    assertEquals("", state.apiKey)
    assertEquals("https://api.example.com/v1", state.baseUrl)
    assertTrue(state.hasSavedApiKey)
    assertTrue(state.canCheckConnection)
  }

  @Test
  fun save_showsValidationError() = runTest(dispatcher) {
    val viewModel = viewModel()
    viewModel.onBaseUrlChange("http://api.example.com/v1")
    viewModel.onModelChange("model-a")
    viewModel.onApiKeyChange("sk-secret")

    viewModel.save()

    assertEquals(ByoSettingsError.InvalidBaseUrl, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.canCheckConnection)
  }

  @Test
  fun savedSettings_arePrefilledWithoutApiKey() = runTest(dispatcher) {
    store.stored = StoredByoSettings("https://api.example.com/v1", "model-a", "enc:terces")

    val state = viewModel().uiState.value

    assertEquals("https://api.example.com/v1", state.baseUrl)
    assertEquals("model-a", state.model)
    assertEquals("", state.apiKey)
    assertTrue(state.hasSavedApiKey)
    assertTrue(state.canSave)
    assertTrue(state.canCheckConnection)
  }

  @Test
  fun checkConnection_runsOnceWhileInFlight() = runTest(dispatcher) {
    store.stored = StoredByoSettings("https://api.example.com/v1", "model-a", "enc:terces")
    val viewModel = viewModel()

    viewModel.checkConnection()
    viewModel.checkConnection()
    assertEquals(ConnectionCheckState.Running, viewModel.uiState.value.connectionCheck)
    checker.pending.complete(ConnectionCheckResult.Unauthorized(401))

    assertEquals(1, checker.calls)
    assertEquals(
      ConnectionCheckState.Done(ConnectionCheckResult.Unauthorized(401)),
      viewModel.uiState.value.connectionCheck,
    )
  }
}
