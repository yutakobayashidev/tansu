package dev.yutakobayashi.tansu.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BaseUrlTest {
  @Test
  fun normalize_trimsWhitespaceAndTrailingSlash() {
    assertEquals("https://api.example.com/v1", BaseUrl.normalize("  https://api.example.com/v1/ "))
  }

  @Test
  fun normalize_acceptsHostWithPort() {
    assertEquals("https://llm.example.com:8443/v1", BaseUrl.normalize("https://llm.example.com:8443/v1"))
  }

  @Test
  fun normalize_rejectsNonHttps() {
    assertNull(BaseUrl.normalize("http://api.example.com/v1"))
    assertNull(BaseUrl.normalize("ftp://api.example.com"))
    assertNull(BaseUrl.normalize("api.example.com/v1"))
  }

  @Test
  fun normalize_rejectsBlankOrHostless() {
    assertNull(BaseUrl.normalize(""))
    assertNull(BaseUrl.normalize("https://"))
    assertNull(BaseUrl.normalize("not a url"))
  }

  @Test
  fun normalize_rejectsQueryFragmentAndUserInfo() {
    assertNull(BaseUrl.normalize("https://api.example.com/v1?x=1"))
    assertNull(BaseUrl.normalize("https://api.example.com/v1#frag"))
    assertNull(BaseUrl.normalize("https://user:pass@api.example.com/v1"))
  }

  @Test
  fun summary_isCompleteOnlyWithAllThreeItems() {
    val complete = ByoSettingsSummary("https://api.example.com/v1", "model-a", hasApiKey = true)
    assertEquals(true, complete.isComplete)
    assertEquals(false, complete.copy(model = " ").isComplete)
    assertEquals(false, complete.copy(hasApiKey = false).isComplete)
    assertEquals(false, complete.copy(baseUrl = "http://api.example.com").isComplete)
    assertEquals(false, ByoSettingsSummary.Empty.isComplete)
  }
}
