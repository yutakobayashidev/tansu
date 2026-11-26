package dev.yutakobayashi.tansu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SourceImageTest {
  @Test
  fun contentHash_acceptsLowercaseSha256Hex() {
    val hex = "0123456789abcdef".repeat(4)
    assertEquals(hex, ContentHash(hex).value)
  }

  @Test
  fun contentHash_rejectsInvalidValues() {
    for (raw in listOf("", "a".repeat(63), "a".repeat(65), "A".repeat(64), "g".repeat(64))) {
      assertThrows(IllegalArgumentException::class.java) { ContentHash(raw) }
    }
  }

  @Test
  fun copiedFileRef_rejectsBlank() {
    for (raw in listOf("", " ")) {
      assertThrows(IllegalArgumentException::class.java) { CopiedFileRef(raw) }
    }
  }

  @Test
  fun sourceImage_holdsHashAndCopiedFile() {
    val image = SourceImage(HASH_A, CopiedFileRef("images/${HASH_A.value}.jpg"))
    assertEquals(HASH_A, image.contentHash)
    assertEquals("images/${HASH_A.value}.jpg", image.copiedFile.value)
  }
}
