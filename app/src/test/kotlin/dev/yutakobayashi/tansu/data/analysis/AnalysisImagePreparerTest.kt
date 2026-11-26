package dev.yutakobayashi.tansu.data.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisImagePreparerTest {
  private class FakeCodec(private val info: ImageInfo?) : ImageCodec {
    val downscaleCalls = mutableListOf<Pair<Int, Int>>()

    override fun probe(bytes: ByteArray): ImageInfo? = info

    override fun downscale(bytes: ByteArray, maxLongSide: Int, quality: Int): ByteArray? {
      downscaleCalls += maxLongSide to quality
      return SCALED
    }
  }

  @Test
  fun prepare_sendsImagesUpToTheLimitAsTheOriginalBytes() {
    for ((width, height) in listOf(2048 to 2048, 2048 to 1, 1 to 2048, 640 to 480)) {
      val codec = FakeCodec(ImageInfo(width, height, "image/png"))
      val image = AnalysisImagePreparer(codec).prepare(ORIGINAL)!!

      assertSame(ORIGINAL, image.bytes)
      assertEquals("image/png", image.mimeType)
      assertTrue("Images within the limit must not be re-encoded", codec.downscaleCalls.isEmpty())
    }
  }

  @Test
  fun prepare_downscalesOnlyWhenTheLongSideExceedsTheLimit() {
    for ((width, height) in listOf(2049 to 100, 100 to 2049, 4000 to 3000)) {
      val codec = FakeCodec(ImageInfo(width, height, "image/png"))
      val image = AnalysisImagePreparer(codec).prepare(ORIGINAL)!!

      assertSame(SCALED, image.bytes)
      assertEquals("image/jpeg", image.mimeType)
      assertEquals(listOf(AnalysisDefaults.MAX_LONG_SIDE_PX to AnalysisDefaults.JPEG_QUALITY), codec.downscaleCalls)
    }
  }

  @Test
  fun prepare_returnsNullForUnreadableImages() {
    assertNull(AnalysisImagePreparer(FakeCodec(null)).prepare(ORIGINAL))
  }

  @Test
  fun scaledSize_keepsAspectRatio() {
    assertEquals(2048 to 1536, scaledSize(4000, 3000, 2048))
    assertEquals(683 to 2048, scaledSize(1000, 3000, 2048))
    assertEquals(2048 to 1, scaledSize(100_000, 1, 2048))
  }

  private companion object {
    val ORIGINAL = byteArrayOf(1, 2, 3)
    val SCALED = byteArrayOf(9)
  }
}
