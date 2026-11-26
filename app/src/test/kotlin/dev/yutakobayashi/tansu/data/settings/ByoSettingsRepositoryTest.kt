package dev.yutakobayashi.tansu.data.settings

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ByoSettingsRepositoryTest {
  private val dispatcher = StandardTestDispatcher()
  private val store = InMemoryByoSettingsStore()
  private val cipher = FakeSecretCipher()

  private fun repository() = ByoSettingsRepository(store, cipher, dispatcher)

  private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

  @Test
  fun initialSummary_isIncompleteWithoutStoredSettings() = test {
    assertEquals(ByoSettingsSummary.Empty, repository().summary.value)
    assertNull(repository().credentials())
  }

  @Test
  fun save_storesOnlyEncryptedApiKey() = test {
    val repository = repository()

    val error = repository.save(" https://api.example.com/v1/ ", " model-a ", " sk-secret ")

    assertNull(error)
    val stored = store.stored
    assertNotNull(stored)
    stored!!
    assertEquals("https://api.example.com/v1", stored.baseUrl)
    assertEquals("model-a", stored.model)
    assertFalse(stored.encryptedApiKey!!.contains("sk-secret"))
    assertTrue(repository.summary.value.isComplete)
    val credentials = repository.credentials()!!
    assertEquals("sk-secret", credentials.apiKey)
    assertEquals("https://api.example.com/v1", credentials.baseUrl)
    assertEquals("model-a", credentials.model)
  }

  @Test
  fun save_withBlankApiKeyKeepsSavedKey() = test {
    val repository = repository()
    repository.save("https://api.example.com/v1", "model-a", "sk-secret")

    val error = repository.save("https://other.example.com/v1", "model-b", "")

    assertNull(error)
    val credentials = repository.credentials()!!
    assertEquals("sk-secret", credentials.apiKey)
    assertEquals("https://other.example.com/v1", credentials.baseUrl)
    assertEquals("model-b", credentials.model)
  }

  @Test
  fun save_rejectsInvalidInputWithoutWriting() = test {
    val repository = repository()

    assertEquals(ByoSettingsError.InvalidBaseUrl, repository.save("http://api.example.com", "m", "k"))
    assertEquals(ByoSettingsError.MissingModel, repository.save("https://api.example.com", "  ", "k"))
    assertEquals(ByoSettingsError.MissingApiKey, repository.save("https://api.example.com", "m", " "))
    assertNull(store.stored)
    assertFalse(repository.summary.value.isComplete)
  }

  @Test
  fun credentials_clearsKeyThatCannotBeDecrypted() = test {
    val repository = repository()
    repository.save("https://api.example.com/v1", "model-a", "sk-secret")
    cipher.brokenKey = true

    assertNull(repository.credentials())

    assertNull(store.stored!!.encryptedApiKey)
    assertFalse(repository.summary.value.hasApiKey)
    assertFalse(repository.summary.value.isComplete)
  }

  @Test
  fun credentials_toStringDoesNotExposeApiKey() {
    val credentials = AnalysisCredentials("https://api.example.com/v1", "model-a", "sk-secret")
    assertFalse(credentials.toString().contains("sk-secret"))
  }
}
