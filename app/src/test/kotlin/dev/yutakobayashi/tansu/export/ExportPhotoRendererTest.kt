package dev.yutakobayashi.tansu.export

import android.graphics.BitmapFactory
import dev.yutakobayashi.tansu.data.analysis.pngBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportPhotoRendererTest {
  private val renderer = ExportPhotoRenderer()

  @Test
  fun downscalesLongSideTo512AsJpeg() {
    val original = pngBytes(1600, 1000)
    val copy = original.copyOf()

    val photo = renderer.render(original)!!

    assertEquals("image/jpeg", mimeType(photo))
    val bitmap = BitmapFactory.decodeByteArray(photo, 0, photo.size)
    assertEquals(512, bitmap.width)
    assertEquals(320, bitmap.height)
    assertArrayEquals(copy, original)
  }

  @Test
  fun keepsSmallImagesAtTheirSize() {
    val photo = renderer.render(pngBytes(300, 400))!!

    assertEquals("image/jpeg", mimeType(photo))
    val bitmap = BitmapFactory.decodeByteArray(photo, 0, photo.size)
    assertEquals(300, bitmap.width)
    assertEquals(400, bitmap.height)
  }

  @Test
  fun rendersSameBytesForSameImage() {
    val original = pngBytes(1600, 1000)

    assertArrayEquals(renderer.render(original), renderer.render(original))
  }

  @Test
  fun returnsNullForUnreadableBytes() {
    assertNull(renderer.render("not an image".toByteArray()))
  }

  private fun mimeType(bytes: ByteArray): String? =
    BitmapFactory.Options().apply { inJustDecodeBounds = true }
      .also { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
      .outMimeType
}
