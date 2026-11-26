package dev.yutakobayashi.tansu.data.analysis

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** [width] × [height] の PNG を作る。[transparent] なら全面を透明にする。 */
internal fun pngBytes(width: Int, height: Int, transparent: Boolean = false): ByteArray {
  val type = if (transparent) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
  val image = BufferedImage(width, height, type)
  if (!transparent) {
    val graphics = image.createGraphics()
    graphics.color = java.awt.Color(0x33, 0x66, 0x99)
    graphics.fillRect(0, 0, width, height)
    graphics.dispose()
  }
  return ByteArrayOutputStream().use { output ->
    check(ImageIO.write(image, "png", output))
    output.toByteArray()
  }
}
