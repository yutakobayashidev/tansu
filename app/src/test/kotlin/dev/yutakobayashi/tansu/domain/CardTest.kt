package dev.yutakobayashi.tansu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTest {
  @Test
  fun card_exposesNoPublicCopy() {
    // UID は保存後に差し替えられないよう、copy を公開しない
    val publicCopies = Card::class.java.methods.filter { it.name.startsWith("copy") }
    assertTrue(publicCopies.isEmpty())
  }

  @Test
  fun of_keepsUidFnAndDetails() {
    val uid = Uid.random()
    val fn = requireNotNull(PropertyValue.of("Taro"))
    val card = Card.of(uid, fn)
    assertEquals(uid, card.uid)
    assertEquals(fn, card.fn)
    assertEquals(CardDetails.EMPTY, card.details)
  }
}
