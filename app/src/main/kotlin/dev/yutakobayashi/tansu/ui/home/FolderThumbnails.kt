package dev.yutakobayashi.tansu.ui.home

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.yutakobayashi.tansu.data.folder.SafFolderAccess
import dev.yutakobayashi.tansu.domain.FolderImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** 一覧に出す縮小画像を作り、スクロールで読み直さないよう覚えておく。 */
class FolderThumbnails(private val access: SafFolderAccess, private val ioDispatcher: CoroutineDispatcher) {
  private val cache =
    object : LruCache<String, Bitmap>(CACHE_BYTES) {
      override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

  suspend fun load(image: FolderImage, sizePx: Int): ImageBitmap? {
    // 同じ document でも中身が変われば作り直す。
    val key = "${image.uri}#${image.lastModified}#${image.size}#$sizePx"
    cache.get(key)?.let { return it.asImageBitmap() }
    val bitmap = withContext(ioDispatcher) { access.thumbnail(image.uri, sizePx) } ?: return null
    cache.put(key, bitmap)
    return bitmap.asImageBitmap()
  }

  private companion object {
    const val CACHE_BYTES = 16 * 1024 * 1024
  }
}
