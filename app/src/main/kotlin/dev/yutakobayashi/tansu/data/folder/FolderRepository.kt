package dev.yutakobayashi.tansu.data.folder

import dev.yutakobayashi.tansu.data.card.CardRepository
import dev.yutakobayashi.tansu.data.card.TansuDatabase
import dev.yutakobayashi.tansu.domain.ContentHash
import dev.yutakobayashi.tansu.domain.FolderImage
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** フォルダの中身。 */
sealed interface FolderListing {
  /** まだフォルダを選んでいない。 */
  data object NotSelected : FolderListing

  /** 選んだフォルダが消えたか、読み取り権限を失った。選び直す必要がある。 */
  data class Unavailable(val treeUri: String) : FolderListing

  /** フォルダ直下の画像。[images] は [FolderImage.NEWEST_FIRST] の順。 */
  data class Listed(val treeUri: String, val name: String, val images: List<FolderImage>) : FolderListing
}

/**
 * 利用者が SAF で選んだフォルダを 1 つ保持し、その画像を列挙する（ADR 0002）。
 *
 * 画像が取り込み済みかは内容ハッシュで判定する。名刺画像と解析の主キーが内容ハッシュだからである。
 * ハッシュは更新日時とサイズが変わるまで DB に残し、一覧を開くたびに全画像を読み直さない。
 */
class FolderRepository(
  private val store: FolderStore,
  private val access: FolderAccess,
  database: TansuDatabase,
  private val cards: CardRepository,
  private val ioDispatcher: CoroutineDispatcher,
) {
  private val hashes = database.folderImageHashDao()
  private val mutex = Mutex()
  private val _selected = MutableStateFlow(store.read())

  /** 選んだフォルダの tree URI。未選択なら `null`。 */
  val selected: StateFlow<String?> = _selected.asStateFlow()

  /** [treeUri] をフォルダにし、読み取り権限を永続化する。前のフォルダの権限は手放す。 */
  suspend fun select(treeUri: String): Unit = withContext(ioDispatcher) {
    mutex.withLock {
      access.persist(treeUri)
      val previous = store.read()
      store.write(treeUri)
      if (previous != null && previous != treeUri) {
        access.release(previous)
        hashes.clear()
      }
      _selected.value = treeUri
    }
  }

  /** フォルダ直下の画像を列挙する。進み具合はまだ付けない。 */
  suspend fun list(): FolderListing = withContext(ioDispatcher) {
    val treeUri = store.read() ?: return@withContext FolderListing.NotSelected
    if (!access.hasPersistedPermission(treeUri)) return@withContext FolderListing.Unavailable(treeUri)
    try {
      val name = access.folderName(treeUri)
      val images = access.listImages(treeUri).map { it.toFolderImage() }.sortedWith(FolderImage.NEWEST_FIRST)
      FolderListing.Listed(treeUri, name, images)
    } catch (_: FolderUnavailableException) {
      FolderListing.Unavailable(treeUri)
    }
  }

  /**
   * [images] それぞれに進み具合を付けて返す。順序は保つ。
   *
   * 内容を読めなかった画像は進み具合を `null` のまま返す。
   */
  suspend fun withProgress(images: List<FolderImage>): List<FolderImage> = withContext(ioDispatcher) {
    val hashByUri = mutex.withLock { contentHashes(images) }
    val progress = cards.progress(hashByUri.values.toSet())
    images.map { image -> image.copy(progress = hashByUri[image.uri]?.let(progress::getValue)) }
  }

  /** 覚えているハッシュを使い、無いか古い画像だけを読んでハッシュを求める。 */
  private suspend fun contentHashes(images: List<FolderImage>): Map<String, ContentHash> {
    val cached = hashes.all().associateBy { it.documentUri }
    val fresh = mutableListOf<FolderImageHashEntity>()
    val result = buildMap {
      for (image in images) {
        val entry = cached[image.uri]
        if (entry != null && entry.lastModified == image.lastModified && entry.size == image.size) {
          put(image.uri, ContentHash(entry.contentHash))
          continue
        }
        val hash = readHash(image.uri) ?: continue
        put(image.uri, hash)
        fresh += FolderImageHashEntity(image.uri, image.lastModified, image.size, hash.value)
      }
    }
    if (fresh.isNotEmpty()) hashes.upsert(fresh)
    val present = images.mapTo(HashSet()) { it.uri }
    cached.keys.filterNot { it in present }.chunked(QUERY_CHUNK).forEach { hashes.delete(it) }
    return result
  }

  private fun readHash(documentUri: String): ContentHash? =
    try {
      access.open(documentUri).use { ContentHash(it.sha256Hex()) }
    } catch (_: IOException) {
      null
    } catch (_: SecurityException) {
      null
    }

  private fun FolderDocument.toFolderImage() = FolderImage(uri, displayName, lastModified, size)

  private companion object {
    const val QUERY_CHUNK = 500

    fun InputStream.sha256Hex(): String {
      val digest = MessageDigest.getInstance("SHA-256")
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      while (true) {
        val read = read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
      }
      return digest.digest().joinToString("") { "%02x".format(it) }
    }
  }
}
