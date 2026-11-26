package dev.yutakobayashi.tansu.data.folder

import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.core.net.toUri
import java.io.FileNotFoundException
import java.io.InputStream

/** フォルダが消えた、または読み取り権限を失ったため、列挙も読み出しもできない。 */
class FolderUnavailableException(cause: Throwable? = null) : Exception(cause)

/** フォルダ直下の名刺画像 1 枚。 */
data class FolderDocument(
  val uri: String,
  val displayName: String,
  val lastModified: Long?,
  val size: Long?,
)

/**
 * SAF のフォルダ（tree URI）への操作。テストで差し替えられるよう、Android の API はここに閉じ込める。
 *
 * URI は文字列で受け渡す。
 */
interface FolderAccess {
  /** 読み取り権限を永続化する。 */
  fun persist(treeUri: String)

  /** 永続化した読み取り権限を手放す。既に失っていても例外にしない。 */
  fun release(treeUri: String)

  /** 永続化した読み取り権限がまだ残っているか。 */
  fun hasPersistedPermission(treeUri: String): Boolean

  /** フォルダの表示名。読めなければ [FolderUnavailableException]。 */
  fun folderName(treeUri: String): String

  /** フォルダ直下の画像を列挙する。サブフォルダはたどらない。読めなければ [FolderUnavailableException]。 */
  fun listImages(treeUri: String): List<FolderDocument>

  /** document を開く。呼び出し側が閉じる。 */
  fun open(documentUri: String): InputStream
}

class SafFolderAccess(private val resolver: ContentResolver) : FolderAccess {

  override fun persist(treeUri: String) {
    resolver.takePersistableUriPermission(treeUri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }

  override fun release(treeUri: String) {
    try {
      resolver.releasePersistableUriPermission(treeUri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
      // 既に権限を失っている。手放すものは残っていない。
    }
  }

  override fun hasPersistedPermission(treeUri: String): Boolean {
    val uri = treeUri.toUri()
    return resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
  }

  override fun folderName(treeUri: String): String = unavailableOnFailure {
    val tree = treeUri.toUri()
    val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    resolver.query(folder, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: throw FolderUnavailableException()
  }

  override fun listImages(treeUri: String): List<FolderDocument> = unavailableOnFailure {
    val tree = treeUri.toUri()
    val children =
      DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    val cursor = resolver.query(children, PROJECTION, null, null, null) ?: throw FolderUnavailableException()
    cursor.use {
      buildList {
        while (it.moveToNext()) {
          val mimeType = it.getString(COLUMN_MIME_TYPE) ?: continue
          if (!mimeType.startsWith("image/")) continue
          add(
            FolderDocument(
              uri = DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(COLUMN_DOCUMENT_ID)).toString(),
              displayName = it.getString(COLUMN_DISPLAY_NAME).orEmpty(),
              lastModified = if (it.isNull(COLUMN_LAST_MODIFIED)) null else it.getLong(COLUMN_LAST_MODIFIED),
              size = if (it.isNull(COLUMN_SIZE)) null else it.getLong(COLUMN_SIZE),
            )
          )
        }
      }
    }
  }

  override fun open(documentUri: String): InputStream =
    resolver.openInputStream(documentUri.toUri()) ?: throw FileNotFoundException(documentUri)

  /**
   * 一覧用の縮小画像を作る。provider が縮小画像を返せなければ、画像を間引いて読む。
   *
   * 長辺がおおむね [sizePx] 以上になる大きさで返す。読めなければ `null`。
   */
  fun thumbnail(documentUri: String, sizePx: Int): Bitmap? {
    val uri = documentUri.toUri()
    val fromProvider =
      try {
        DocumentsContract.getDocumentThumbnail(resolver, uri, Point(sizePx, sizePx), null)
      } catch (_: Exception) {
        null
      }
    if (fromProvider != null) return fromProvider
    return try {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
      var sample = 1
      while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
      val options = BitmapFactory.Options().apply { inSampleSize = sample }
      resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    } catch (_: Exception) {
      null
    }
  }

  private inline fun <T> unavailableOnFailure(block: () -> T): T =
    try {
      block()
    } catch (e: SecurityException) {
      throw FolderUnavailableException(e)
    } catch (e: IllegalArgumentException) {
      // provider が document ID を解決できない。フォルダが消えたときに起きる。
      throw FolderUnavailableException(e)
    } catch (e: FileNotFoundException) {
      throw FolderUnavailableException(e)
    } catch (e: IllegalStateException) {
      // provider のプロセスが落ちた、または document が見つからない。
      throw FolderUnavailableException(e)
    }

  private companion object {
    val PROJECTION =
      arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_SIZE,
      )
    const val COLUMN_DOCUMENT_ID = 0
    const val COLUMN_DISPLAY_NAME = 1
    const val COLUMN_MIME_TYPE = 2
    const val COLUMN_LAST_MODIFIED = 3
    const val COLUMN_SIZE = 4
  }
}
