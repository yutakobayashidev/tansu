package dev.yutakobayashi.tansu.data.card

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface CardDao {
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertSourceImage(entity: SourceImageEntity)

  @Query("SELECT * FROM source_image WHERE content_hash = :contentHash")
  suspend fun sourceImage(contentHash: String): SourceImageEntity?

  @Query("DELETE FROM source_image WHERE content_hash = :contentHash")
  suspend fun deleteSourceImage(contentHash: String)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAnalysis(entity: AnalysisEntity)

  /** [sourceImages] のうち解析がある名刺画像について、その状態を返す。 */
  @Query("SELECT * FROM analysis WHERE source_image IN (:sourceImages)")
  suspend fun analyses(sourceImages: List<String>): List<AnalysisEntity>

  @Query("SELECT * FROM analysis WHERE source_image = :sourceImage")
  suspend fun analysis(sourceImage: String): AnalysisEntity?

  /** [from] のいずれかの状態にある解析だけを [to] へ移す。移した行数を返す。 */
  @Query(
    "UPDATE analysis SET status = :to WHERE source_image = :sourceImage AND status IN (:from)",
  )
  suspend fun transition(
    sourceImage: String,
    from: List<StoredAnalysisStatus>,
    to: StoredAnalysisStatus,
  ): Int

  @Query("UPDATE analysis SET candidate_count = :count WHERE source_image = :sourceImage")
  suspend fun setCandidateCount(sourceImage: String, count: Int)

  @Insert
  suspend fun insertCandidates(entities: List<CandidateEntity>)

  @Query("SELECT * FROM candidate WHERE source_image = :sourceImage ORDER BY position")
  suspend fun candidates(sourceImage: String): List<CandidateEntity>

  @Query("DELETE FROM candidate WHERE source_image = :sourceImage")
  suspend fun deleteCandidates(sourceImage: String)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertCards(entities: List<CardEntity>)

  @Query("SELECT * FROM card WHERE source_image = :sourceImage ORDER BY rowid")
  suspend fun cards(sourceImage: String): List<CardEntity>

  /** すべての名刺を保存した順に返す。書き出しを繰り返しても同じ順になる。 */
  @Query(
    "SELECT card.*, analysis.candidate_count AS candidate_count FROM card " +
      "LEFT JOIN analysis ON analysis.source_image = card.source_image ORDER BY card.rowid",
  )
  suspend fun savedCards(): List<SavedCardRow>

  @Query("SELECT COUNT(*) FROM card WHERE source_image = :sourceImage")
  suspend fun cardCount(sourceImage: String): Int
}
