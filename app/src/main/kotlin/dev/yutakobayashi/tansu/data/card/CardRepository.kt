package dev.yutakobayashi.tansu.data.card

import androidx.room.withTransaction
import dev.yutakobayashi.tansu.data.card.StoredAnalysisStatus.ANALYZING
import dev.yutakobayashi.tansu.data.card.StoredAnalysisStatus.AWAITING_REVIEW
import dev.yutakobayashi.tansu.data.card.StoredAnalysisStatus.COMPLETED
import dev.yutakobayashi.tansu.data.card.StoredAnalysisStatus.FAILED
import dev.yutakobayashi.tansu.data.card.StoredAnalysisStatus.NOT_ANALYZED
import dev.yutakobayashi.tansu.domain.Analysis
import dev.yutakobayashi.tansu.domain.Candidate
import dev.yutakobayashi.tansu.domain.Card
import dev.yutakobayashi.tansu.domain.ContentHash
import dev.yutakobayashi.tansu.domain.ImageProgress
import dev.yutakobayashi.tansu.domain.SavedCard
import dev.yutakobayashi.tansu.domain.SourceImage
import dev.yutakobayashi.tansu.domain.Uid
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 名刺画像の取り込みから、解析の状態遷移、名刺の確定までを保存する。
 *
 * 名刺画像の複製は、参照する名刺が 0 件になった時点（レビューを終えたとき、または名刺を削除したとき）で消す。
 * 複製から作った共有用の写しも同時に消す。
 * ファイルの書き込みと削除が食い違わないよう、変更はすべて直列に実行する。
 */
class CardRepository(
  private val database: TansuDatabase,
  private val files: SourceImageFiles,
  private val sharedImages: SharedImageFiles,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  private val dao = database.cardDao()
  private val mutex = Mutex()

  /**
   * 名刺画像を取り込み、その解析を返す。[input] は読み切って閉じる。
   *
   * 同じ内容の名刺画像が既にあれば、新しい解析を作らず既存の解析を返す。
   */
  suspend fun import(input: InputStream): Analysis = write {
    val image = files.copy(input)
    database.withTransaction {
      dao.insertSourceImage(image.toEntity())
      dao.insertAnalysis(AnalysisEntity(image.contentHash.value, NOT_ANALYZED))
      loadAnalysis(image.contentHash)
    }
  }

  suspend fun analysis(sourceImage: ContentHash): Analysis? = withContext(ioDispatcher) {
    database.withTransaction { loadAnalysisOrNull(sourceImage) }
  }

  /**
   * [sourceImages] それぞれの進み具合を返す。取り込んでいない名刺画像は [ImageProgress.Unprocessed]。
   */
  suspend fun progress(sourceImages: Collection<ContentHash>): Map<ContentHash, ImageProgress> =
    withContext(ioDispatcher) {
      val stored = sourceImages
        .map { it.value }
        .distinct()
        .chunked(QUERY_CHUNK)
        .flatMap { dao.analyses(it) }
        .associate { it.sourceImage to it.status.toProgress() }
      sourceImages.associateWith { stored[it.value] ?: ImageProgress.Unprocessed }
    }

  suspend fun sourceImage(contentHash: ContentHash): SourceImage? = withContext(ioDispatcher) {
    dao.sourceImage(contentHash.value)?.toDomain()
  }

  fun file(image: SourceImage): File = files.resolve(image.copiedFile)

  suspend fun cards(sourceImage: ContentHash): List<Card> = withContext(ioDispatcher) {
    dao.cards(sourceImage.value).map { it.toDomain() }
  }

  /** すべての保存済みの名刺を、保存した順に返す。 */
  suspend fun savedCards(): List<SavedCard> = withContext(ioDispatcher) {
    dao.savedCards().map { it.toDomain() }
  }

  /** `未解析` または `失敗` の解析を `解析中` にする。 */
  suspend fun startAnalysis(sourceImage: ContentHash): Unit = write {
    transition(sourceImage, from = listOf(NOT_ANALYZED, FAILED), to = ANALYZING)
  }

  /**
   * `解析中` の解析に読み取り結果を記録し、`確認待ち` にする。候補が 0 件なら該当なし。
   *
   * 候補の件数も記録する。レビューの後も残り、書き出しで `PHOTO` を埋め込むかを決める。
   */
  suspend fun recordReadResult(sourceImage: ContentHash, candidates: List<Candidate>): Unit =
    write {
      require(candidates.all { it.sourceImage == sourceImage }) {
        "Candidates must come from the analyzed source image"
      }
      database.withTransaction {
        transition(sourceImage, from = listOf(ANALYZING), to = AWAITING_REVIEW)
        dao.setCandidateCount(sourceImage.value, candidates.size)
        dao.insertCandidates(candidates.mapIndexed { index, candidate -> candidate.toEntity(index) })
      }
    }

  /** `解析中` の解析を `失敗` にする。 */
  suspend fun recordFailure(sourceImage: ContentHash): Unit = write {
    transition(sourceImage, from = listOf(ANALYZING), to = FAILED)
  }

  /**
   * `確認待ち` の解析のレビューを終え、確定した [cards] を保存して `完了` にする。
   *
   * 名刺画像を参照する名刺が 0 件なら、名刺画像と解析を消し、複製したファイルも削除する。
   */
  suspend fun completeReview(sourceImage: ContentHash, cards: List<Card>): ReviewOutcome = write {
    val discarded = database.withTransaction {
      transition(sourceImage, from = listOf(AWAITING_REVIEW), to = COMPLETED)
      dao.deleteCandidates(sourceImage.value)
      dao.insertCards(cards.map { it.toEntity(sourceImage) })
      discardIfUnreferenced(sourceImage)
    }
    discarded?.let { deleteFiles(it) }
    if (discarded == null) ReviewOutcome.CardsSaved else ReviewOutcome.SourceImageDiscarded
  }

  /** 名刺が 1 件以上残っている名刺画像と、その名刺。最後に名刺を保存した画像から並べる。 */
  suspend fun savedImages(): List<SavedImage> = withContext(ioDispatcher) {
    database.withTransaction {
      dao.sourceImagesWithCards().map { image ->
        SavedImage(image.toDomain(), dao.cards(image.contentHash).map { it.toDomain() })
      }
    }
  }

  /**
   * [sourceImage] から保存した名刺 [uid] を削除する（ADR 0005）。
   *
   * 名刺画像を参照する名刺が 0 件になったら、名刺画像と解析を消し、アプリ内の複製も削除する。
   * フォルダにある原本は、このクラスからは読み書きしないので残る。
   */
  suspend fun deleteCard(sourceImage: ContentHash, uid: Uid): CardDeletionOutcome = write {
    val discarded = database.withTransaction {
      check(dao.deleteCard(sourceImage.value, uid.toString()) == 1) {
        "Card $uid of $sourceImage does not exist"
      }
      discardIfUnreferenced(sourceImage)
    }
    discarded?.let { deleteFiles(it) }
    if (discarded == null) CardDeletionOutcome.CardsRemain else CardDeletionOutcome.SourceImageDiscarded
  }

  /**
   * 名刺画像 [sourceImage] の複製から、共有シートへ渡すファイルを作る。
   *
   * 名刺画像がもう無いか、画像として読めなければ `null` を返す。名刺の削除と直列に実行するので、
   * 複製を消した後に写しだけが作られることはない。
   */
  suspend fun shareImage(sourceImage: ContentHash): SharedImage? = write {
    dao.sourceImage(sourceImage.value)?.toDomain()?.let { sharedImages.prepare(files.resolve(it.copiedFile)) }
  }

  /**
   * 名刺画像を参照する名刺が 0 件なら、名刺画像の行を消し（解析と候補も連鎖して消える）、消した名刺画像を返す。
   *
   * 複製のファイルは、呼び出し側がトランザクションをコミットしてから消す。巻き戻ったときに、
   * 行が残っているのにファイルだけが無い状態を作らないためである。
   */
  private suspend fun discardIfUnreferenced(sourceImage: ContentHash): SourceImage? {
    if (dao.cardCount(sourceImage.value) != 0) return null
    val image = checkNotNull(dao.sourceImage(sourceImage.value)).toDomain()
    dao.deleteSourceImage(sourceImage.value)
    return image
  }

  /** 消した名刺画像の複製と、そこから作った共有用の写しを削除する。 */
  private fun deleteFiles(image: SourceImage) {
    files.delete(image.copiedFile)
    sharedImages.delete(image.copiedFile)
  }

  private suspend fun <T> write(block: suspend () -> T): T =
    withContext(ioDispatcher) { mutex.withLock { block() } }

  private suspend fun transition(
    sourceImage: ContentHash,
    from: List<StoredAnalysisStatus>,
    to: StoredAnalysisStatus,
  ) {
    val updated = dao.transition(sourceImage.value, from, to)
    check(updated == 1) {
      val current = dao.analysis(sourceImage.value)?.status
      "Analysis of $sourceImage cannot move from $current to $to"
    }
  }

  private suspend fun loadAnalysis(sourceImage: ContentHash): Analysis =
    checkNotNull(loadAnalysisOrNull(sourceImage)) { "Analysis of $sourceImage is missing" }

  private suspend fun loadAnalysisOrNull(sourceImage: ContentHash): Analysis? {
    val entity = dao.analysis(sourceImage.value) ?: return null
    return entity.toDomain(dao.candidates(sourceImage.value))
  }

  private companion object {
    /** SQLite のバインド変数の上限（既定 999）を超えないよう、IN 句を分ける件数。 */
    const val QUERY_CHUNK = 500
  }
}

/** レビューを終えた結果。 */
enum class ReviewOutcome {
  /** 名刺が 1 件以上残り、名刺画像を保持した。 */
  CardsSaved,

  /** 名刺が 0 件になったため、名刺画像の複製を削除した。 */
  SourceImageDiscarded,
}

/** 保存済みの名刺を削除した結果。 */
enum class CardDeletionOutcome {
  /** 同じ名刺画像から保存した名刺が、まだ 1 件以上残っている。 */
  CardsRemain,

  /** 名刺が 0 件になったため、名刺画像の複製を削除した。フォルダの原本は残っている。 */
  SourceImageDiscarded,
}

/** 名刺を 1 件以上保存した名刺画像と、そこから保存した名刺。[cards] は保存した順。 */
data class SavedImage(val image: SourceImage, val cards: List<Card>)
