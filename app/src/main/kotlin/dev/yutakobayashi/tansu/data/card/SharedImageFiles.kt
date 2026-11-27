package dev.yutakobayashi.tansu.data.card

import android.graphics.BitmapFactory
import dev.yutakobayashi.tansu.domain.CopiedFileRef
import java.io.File
import java.io.IOException

/** 共有シートへ渡す名刺画像のファイルと、その MIME タイプ。 */
data class SharedImage(val file: File, val mimeType: String)

/**
 * 名刺画像の複製から、共有シートへ渡すファイルを [directory] に作る。
 *
 * 複製のファイル名は内容ハッシュだけで拡張子が無く、受け取り側が形式を判別できない。
 * そこで拡張子付きの写し（`<内容ハッシュ>.<拡張子>`）を作って渡す。複製そのものは変更しない。
 * 受け取り側が読み終えた時点はアプリから分からないので、写しは複製を消すときに [delete] で一緒に消す。
 * 呼び出しの直列化は呼び出し側が受け持つ。
 */
class SharedImageFiles(private val directory: File) {
  /** [copied] の写しを作る。画像として読めなければ `null` を返す。 */
  fun prepare(copied: File): SharedImage? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(copied.path, bounds)
    val mimeType = bounds.outMimeType ?: return null
    val extension = EXTENSIONS[mimeType] ?: return null
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Failed to create $directory")
    val target = File(directory, "${copied.name}.$extension")
    if (!target.exists() || target.length() != copied.length()) copied.copyTo(target, overwrite = true)
    return SharedImage(target, mimeType)
  }

  /** 複製 [ref] から作った写しをすべて消す。 */
  fun delete(ref: CopiedFileRef) {
    directory.listFiles { file -> file.name.startsWith("${ref.value}.") }?.forEach(File::delete)
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
