package dev.yutakobayashi.tansu.data.card

import dev.yutakobayashi.tansu.domain.ContentHash
import dev.yutakobayashi.tansu.domain.CopiedFileRef
import dev.yutakobayashi.tansu.domain.SourceImage
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * 名刺画像の複製を [directory] に置く。
 *
 * 受け取ったバイト列をそのまま書き、SHA-256 の小文字 16 進をファイル名にする。
 * 同じ内容のファイルが既にあれば書き直さない。呼び出しの直列化は呼び出し側が受け持つ。
 */
class SourceImageFiles(private val directory: File) {
  /** [input] を読み切って複製し、閉じる。 */
  fun copy(input: InputStream): SourceImage {
    ensureDirectory()
    deleteTemporaryFiles()
    val temporary = File.createTempFile(TEMPORARY_PREFIX, TEMPORARY_SUFFIX, directory)
    try {
      val digest = MessageDigest.getInstance("SHA-256")
      DigestInputStream(input, digest).use { source ->
        temporary.outputStream().use { source.copyTo(it) }
      }
      val hash = ContentHash(digest.digest().toHex())
      val target = File(directory, hash.value)
      if (!target.exists() && !temporary.renameTo(target)) {
        throw IOException("Failed to move the copied image into place")
      }
      return SourceImage(hash, CopiedFileRef(target.name))
    } finally {
      temporary.delete()
    }
  }

  fun resolve(ref: CopiedFileRef): File = File(directory, ref.value)

  fun delete(ref: CopiedFileRef) {
    resolve(ref).delete()
  }

  private fun ensureDirectory() {
    if (!directory.isDirectory && !directory.mkdirs()) {
      throw IOException("Failed to create $directory")
    }
  }

  /** 書き込み中にプロセスが落ちて残った一時ファイルを消す。 */
  private fun deleteTemporaryFiles() {
    directory
      .listFiles { file -> file.name.startsWith(TEMPORARY_PREFIX) && file.name.endsWith(TEMPORARY_SUFFIX) }
      ?.forEach(File::delete)
  }

  private companion object {
    const val TEMPORARY_PREFIX = "import-"
    const val TEMPORARY_SUFFIX = ".tmp"

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
  }
}
