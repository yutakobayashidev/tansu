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
import dev.yutakobayashi.tansu.domain.SourceImage
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
 * 名刺画像の複製は、参照する名刺が 0 件のままレビューを終えた時点で消す。
 * ファイルの書き込みと削除が食い違わないよう、変更はすべて直列に実行する。
 */
class CardRepository(
  private val database: TansuDatabase,
  private val files: SourceImageFiles,
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

  /** `未解析` または `失敗` の解析を `解析中` にする。 */
  suspend fun startAnalysis(sourceImage: ContentHash): Unit = write {
    transition(sourceImage, from = listOf(NOT_ANALYZED, FAILED), to = ANALYZING)
  }

  /** `解析中` の解析に読み取り結果を記録し、`確認待ち` にする。候補が 0 件なら該当なし。 */
  suspend fun recordReadResult(sourceImage: ContentHash, candidates: List<Candidate>): Unit =
    write {
      require(candidates.all { it.sourceImage == sourceImage }) {
        "Candidates must come from the analyzed source image"
      }
      database.withTransaction {
        transition(sourceImage, from = listOf(ANALYZING), to = AWAITING_REVIEW)
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
      if (dao.cardCount(sourceImage.value) == 0) {
        val image = checkNotNull(dao.sourceImage(sourceImage.value)).toDomain()
        dao.deleteSourceImage(sourceImage.value)
        image
      } else {
        null
      }
    }
    if (discarded == null) {
      ReviewOutcome.CardsSaved
    } else {
      files.delete(discarded.copiedFile)
      ReviewOutcome.SourceImageDiscarded
    }
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
