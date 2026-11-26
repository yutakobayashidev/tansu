package dev.yutakobayashi.tansu.data.analysis

import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidImageCodecTest {
  private val codec = AndroidImageCodec()

  @Test
  fun probe_readsSizeAndFormat() {
    assertEquals(ImageInfo(30, 20, "image/png"), codec.probe(pngBytes(30, 20)))
  }

  @Test
  fun prepare_keepsImagesUpToTheLimitByteForByte() {
    val original = pngBytes(2048, 1000)
    val copy = original.copyOf()

    val image = AnalysisImagePreparer(codec).prepare(original)!!

    assertSame(original, image.bytes)
    assertArrayEquals(copy, image.bytes)
    assertEquals("image/png", image.mimeType)
  }

  @Test
  fun prepare_downscalesLargeTransparentImageToJpegOnWhite() {
    val original = pngBytes(3000, 1000, transparent = true)
    val copy = original.copyOf()

    val image = AnalysisImagePreparer(codec).prepare(original)!!

    assertEquals("image/jpeg", image.mimeType)
    val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
    assertEquals(2048, bitmap.width)
    assertEquals(683, bitmap.height)
    val pixel = bitmap.getPixel(1024, 341)
    assertEquals(0xFF, Color.alpha(pixel))
    assert(Color.red(pixel) > 0xF0 && Color.green(pixel) > 0xF0 && Color.blue(pixel) > 0xF0) {
      "Transparent pixels must be composited on white, got #${Integer.toHexString(pixel)}"
    }
    assertArrayEquals("The source bytes must not change", copy, original)
  }

  @Test
  fun probe_returnsNullForNonImages() {
    assertEquals(null, codec.probe("not an image".toByteArray()))
  }
}
