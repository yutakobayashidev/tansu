package dev.yutakobayashi.tansu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PropertyValueTest {
  @Test
  fun of_dropsNullEmptyAndBlank() {
    assertNull(PropertyValue.of(null))
    assertNull(PropertyValue.of(""))
    assertNull(PropertyValue.of(" "))
    assertNull(PropertyValue.of("\t\n"))
    assertNull(PropertyValue.of("　"))
  }

  @Test
  fun of_keepsValueWithoutTrimmingOrNormalizing() {
    assertEquals(" 山田 太郎 ", PropertyValue.of(" 山田 太郎 ")?.value)
    assertEquals("０３－１２３４－５６７８", PropertyValue.of("０３－１２３４－５６７８")?.value)
  }

  @Test
  fun listOf_dropsBlankAndKeepsOrderAndDuplicates() {
    val values = PropertyValue.listOf(listOf("03-1", "", null, " ", "090-2", "03-1"))
    assertEquals(listOf("03-1", "090-2", "03-1"), values.map { it.value })
  }
}
