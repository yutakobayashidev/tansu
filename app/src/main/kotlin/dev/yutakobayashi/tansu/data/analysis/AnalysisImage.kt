package dev.yutakobayashi.tansu.data.analysis

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/** 解析リクエストに載せる画像。[bytes] は名刺画像そのもの、または縮小した複製。 */
class AnalysisImage(val bytes: ByteArray, val mimeType: String)

/** 画像の寸法と形式。寸法は向きを補正する前の値で、長辺の判定にだけ使う。 */
data class ImageInfo(val width: Int, val height: Int, val mimeType: String)

/** 画像の寸法の読み取りと縮小。 */
interface ImageCodec {
  /** 寸法と形式を返す。画像として読めなければ `null` を返す。 */
  fun probe(bytes: ByteArray): ImageInfo?

  /** 長辺が [maxLongSide] になるよう縮小し、白背景に合成した JPEG を返す。読めなければ `null` を返す。 */
  fun downscale(bytes: ByteArray, maxLongSide: Int, quality: Int): ByteArray?
}

/**
 * 名刺画像から、解析リクエストに載せる画像を作る。
 *
 * 長辺が [maxLongSide] 以下なら再エンコードせず、受け取ったバイト列をそのまま使う。
 * 超える場合だけ縮小した複製を作る。拡大・切り抜き・回転・色変換はしない。名刺画像のファイルは変更しない。
 */
class AnalysisImagePreparer(
  private val codec: ImageCodec = AndroidImageCodec(),
  private val maxLongSide: Int = AnalysisDefaults.MAX_LONG_SIDE_PX,
  private val jpegQuality: Int = AnalysisDefaults.JPEG_QUALITY,
) {
  /** 画像として読めなければ `null` を返す。 */
  fun prepare(bytes: ByteArray): AnalysisImage? {
    val info = codec.probe(bytes) ?: return null
    if (max(info.width, info.height) <= maxLongSide) return AnalysisImage(bytes, info.mimeType)
    val scaled = codec.downscale(bytes, maxLongSide, jpegQuality) ?: return null
    return AnalysisImage(scaled, JPEG_MIME_TYPE)
  }

  private companion object {
    const val JPEG_MIME_TYPE = "image/jpeg"
  }
}

/**
 * Android の画像デコーダを使う [ImageCodec]。
 *
 * 縮小した複製の向きは `ImageDecoder` の既定（EXIF の向きを反映した、表示と同じ向き）に合わせる。
 * 再エンコードで EXIF が落ちるため、向きを画素に反映しておかないと表示と食い違う。
 * `ImageDecoder` が無い API 26・27 では、同じ向きになるよう EXIF の向きを画素に反映する。
 */
class AndroidImageCodec : ImageCodec {
  override fun probe(bytes: ByteArray): ImageInfo? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    val mimeType = options.outMimeType ?: return null
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    return ImageInfo(options.outWidth, options.outHeight, mimeType)
  }

  override fun downscale(bytes: ByteArray, maxLongSide: Int, quality: Int): ByteArray? {
    val decoded =
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          decodeScaled(bytes, maxLongSide)
        } else {
          decodeScaledLegacy(bytes, maxLongSide)
        }
      } catch (_: IOException) {
        null
      } catch (_: IllegalArgumentException) {
        null
      } ?: return null
    val flattened = flattenOnWhite(decoded)
    return ByteArrayOutputStream().use { output ->
      if (!flattened.compress(Bitmap.CompressFormat.JPEG, quality, output)) return null
      output.toByteArray()
    }
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private fun decodeScaled(bytes: ByteArray, maxLongSide: Int): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
      val (width, height) = scaledSize(info.size.width, info.size.height, maxLongSide)
      decoder.setTargetSize(width, height)
      decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

  private fun decodeScaledLegacy(bytes: ByteArray, maxLongSide: Int): Bitmap? {
    val bounds = probe(bytes) ?: return null
    var sampleSize = 1
    while (max(bounds.width, bounds.height) / (sampleSize * 2) >= maxLongSide) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val sampled = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    val (width, height) = scaledSize(sampled.width, sampled.height, maxLongSide)
    val scaled = sampled.scale(width, height)
    val orientation =
      ExifInterface(ByteArrayInputStream(bytes))
        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    val matrix = orientationMatrix(orientation) ?: return scaled
    return Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
  }

  /** 透過を白背景に合成する。JPEG は透過を持てないため、そのままだと透明部分が黒くなる。 */
  private fun flattenOnWhite(source: Bitmap): Bitmap {
    if (!source.hasAlpha()) return source
    val output = createBitmap(source.width, source.height)
    Canvas(output).apply {
      drawColor(Color.WHITE)
      drawBitmap(source, 0f, 0f, null)
    }
    return output
  }

  private fun orientationMatrix(orientation: Int): Matrix? =
    when (orientation) {
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { setScale(-1f, 1f) }
      ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { setRotate(180f) }
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { setScale(1f, -1f) }
      ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply { setRotate(90f); postScale(-1f, 1f) }
      ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { setRotate(90f) }
      ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply { setRotate(-90f); postScale(-1f, 1f) }
      ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { setRotate(-90f) }
      else -> null
    }
}

/** 縦横比を保ち、長辺が [maxLongSide] になる寸法を返す。 */
internal fun scaledSize(width: Int, height: Int, maxLongSide: Int): Pair<Int, Int> {
  val scale = maxLongSide.toDouble() / max(width, height)
  return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
}
