package dev.yutakobayashi.tansu.ui.detail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.yutakobayashi.tansu.data.analysis.scaledSize
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * アプリ内に複製した名刺画像を、表示用に縮小して読み込む。
 *
 * 複製から読むので、フォルダの原本が消えても表示できる（AC21）。複製のファイルは変更しない。
 */
class CopiedImages(private val ioDispatcher: CoroutineDispatcher) {
  private val cache =
    object : LruCache<String, Bitmap>(CACHE_BYTES) {
      override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

  /** 長辺が [sizePx] 以下になるよう縮小して返す。読めなければ `null` を返す。 */
  suspend fun load(file: File, sizePx: Int): ImageBitmap? {
    // 複製のファイル名は内容ハッシュなので、名前が同じなら中身も同じである。
    val key = "${file.name}#$sizePx"
    cache.get(key)?.let { return it.asImageBitmap() }
    val bitmap = withContext(ioDispatcher) { decode(file, sizePx) } ?: return null
    cache.put(key, bitmap)
    return bitmap.asImageBitmap()
  }

  private fun decode(file: File, sizePx: Int): Bitmap? =
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeScaled(file, sizePx) else decodeSampled(file, sizePx)
    } catch (_: IOException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }

  /** `ImageDecoder` は EXIF の向きを反映するので、フォルダの一覧と同じ向きで表示できる。 */
  @RequiresApi(Build.VERSION_CODES.P)
  private fun decodeScaled(file: File, sizePx: Int): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
      if (max(info.size.width, info.size.height) > sizePx) {
        val (width, height) = scaledSize(info.size.width, info.size.height, sizePx)
        decoder.setTargetSize(width, height)
      }
    }

  /** API 26・27 では 2 の累乗で間引いて読む。向きは補正しない。 */
  private fun decodeSampled(file: File, sizePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
  }

  private companion object {
    const val CACHE_BYTES = 16 * 1024 * 1024
  }
}

/** 共有シートへ渡す名刺画像のファイルと、その MIME タイプ。 */
data class SharedImage(val file: File, val mimeType: String)

/**
 * 名刺画像の複製を、共有シートへ渡すファイルにする。
 *
 * 複製のファイル名は内容ハッシュだけで拡張子が無く、受け取り側が形式を判別できない。
 * そこで [directory] に拡張子付きの写しを作って渡す。複製そのものは変更しない。
 */
class SharedImageFiles(private val directory: File, private val ioDispatcher: CoroutineDispatcher) {
  /** 画像として読めなければ `null` を返す。 */
  suspend fun prepare(copied: File): SharedImage? = withContext(ioDispatcher) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(copied.path, bounds)
    val mimeType = bounds.outMimeType ?: return@withContext null
    val extension = EXTENSIONS[mimeType] ?: return@withContext null
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Failed to create $directory")
    val target = File(directory, "${copied.name}.$extension")
    if (!target.exists() || target.length() != copied.length()) copied.copyTo(target, overwrite = true)
    SharedImage(target, mimeType)
  }

  private companion object {
    val EXTENSIONS =
      mapOf(
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp",
        "image/gif" to "gif",
        "image/heif" to "heic",
        "image/heic" to "heic",
        "image/bmp" to "bmp",
      )
  }
}
