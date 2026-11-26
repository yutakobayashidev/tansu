package dev.yutakobayashi.tansu.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CardDetailsTest {
  @Test
  fun fromRaw_blankValuesAreNotKept() {
    val details = CardDetails.fromRaw(
      n = "",
      org = " ",
      title = "　",
      tel = listOf("", " "),
      email = listOf("\t"),
      adr = null,
      url = "",
      note = " ",
    )
    assertEquals(CardDetails.EMPTY, details)
  }

  @Test
  fun fromRaw_keepsMultipleTelAndEmail() {
    val details = CardDetails.fromRaw(
      tel = listOf("03-0000-0000", "090-0000-0000"),
      email = listOf("a@example.com", "b@example.com"),
    )
    assertEquals(listOf("03-0000-0000", "090-0000-0000"), details.tel.map { it.value })
    assertEquals(listOf("a@example.com", "b@example.com"), details.email.map { it.value })
  }

  @Test
  fun fromRaw_doesNotFormatOrInferValues() {
    val details = CardDetails.fromRaw(org = " Example Inc ", url = "example.com")
    assertEquals(" Example Inc ", details.org?.value)
    assertEquals("example.com", details.url?.value)
    assertEquals(null, details.n)
    assertEquals(null, details.title)
  }
}
