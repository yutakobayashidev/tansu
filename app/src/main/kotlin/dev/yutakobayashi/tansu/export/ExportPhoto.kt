package dev.yutakobayashi.tansu.export

import dev.yutakobayashi.tansu.data.analysis.AndroidImageCodec
import dev.yutakobayashi.tansu.data.analysis.ImageCodec
import kotlin.math.max
import kotlin.math.min

/**
 * vCard の `PHOTO` に埋め込む縮小版を作る。保持している名刺画像は変更しない。
 *
 * 長辺が [maxLongSide] を超えれば縮小し、超えなければ寸法を保つ。どちらも JPEG に変換する。
 */
class ExportPhotoRenderer(
  private val codec: ImageCodec = AndroidImageCodec(),
  private val maxLongSide: Int = MAX_LONG_SIDE_PX,
  private val jpegQuality: Int = JPEG_QUALITY,
) {
  /** 画像として読めなければ `null` を返す。 */
  fun render(bytes: ByteArray): ByteArray? {
    val info = codec.probe(bytes) ?: return null
    return codec.downscale(bytes, min(maxLongSide, max(info.width, info.height)), jpegQuality)
  }

  companion object {
    const val MAX_LONG_SIDE_PX = 512
    const val JPEG_QUALITY = 85
  }
}
