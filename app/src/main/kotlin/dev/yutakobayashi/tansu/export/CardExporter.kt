package dev.yutakobayashi.tansu.export

import dev.yutakobayashi.tansu.data.card.CardRepository
import dev.yutakobayashi.tansu.domain.ContentHash
import dev.yutakobayashi.tansu.domain.SavedCard
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 書き出しの結果。 */
sealed interface ExportResult {
  /** [file] に書き出した。 */
  data class Exported(val file: File, val format: ExportFormat, val count: Int) : ExportResult

  /** 保存済みの名刺が無い。 */
  data object NoCards : ExportResult
}

/**
 * 保存済みの名刺をすべて書き出し、[directory] のファイルにする。
 *
 * ファイル名は形式ごとに固定で、書き出すたびに置き換える。名刺と名刺画像が同じなら、同じバイト列になる。
 */
class CardExporter(
  private val cards: CardRepository,
  private val directory: File,
  private val photos: ExportPhotoRenderer = ExportPhotoRenderer(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  suspend fun export(format: ExportFormat): ExportResult = withContext(ioDispatcher) {
    val saved = cards.savedCards()
    if (saved.isEmpty()) return@withContext ExportResult.NoCards
    val bytes = format.write(saved.map { it.toExportCard(includePhoto = format == ExportFormat.VCard) })
    ExportResult.Exported(writeAtomically(format, bytes), format, saved.size)
  }

  private suspend fun SavedCard.toExportCard(includePhoto: Boolean): ExportCard =
    ExportCard(card, sourceImage, photo = if (includePhoto && embedsPhoto) photo(sourceImage) else null)

  /** 名刺画像を読めなければ `PHOTO` を省き、他の名刺の書き出しは続ける。 */
  private suspend fun photo(sourceImage: ContentHash): ByteArray? {
    val image = cards.sourceImage(sourceImage) ?: return null
    val bytes =
      try {
        cards.file(image).readBytes()
      } catch (_: IOException) {
        return null
      }
    return photos.render(bytes)
  }

  private fun writeAtomically(format: ExportFormat, bytes: ByteArray): File {
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Failed to create $directory")
    val target = File(directory, "$FILE_NAME.${format.extension}")
    val temporary = File.createTempFile(FILE_NAME, ".tmp", directory)
    try {
      temporary.writeBytes(bytes)
      if (!temporary.renameTo(target)) throw IOException("Failed to move the export into place")
    } finally {
      temporary.delete()
    }
    return target
  }

  private companion object {
    const val FILE_NAME = "tansu"
  }
}
