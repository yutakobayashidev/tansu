package dev.yutakobayashi.tansu.data.card

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** アプリ内へ複製した名刺画像。主キーは内容ハッシュなので、同じ内容の画像は 1 行にしかならない。 */
@Entity(tableName = "source_image")
internal data class SourceImageEntity(
  @PrimaryKey @ColumnInfo(name = "content_hash") val contentHash: String,
  @ColumnInfo(name = "copied_file") val copiedFile: String,
)

/**
 * 名刺画像 1 枚に対する解析。名刺画像と 1 対 1 で、主キーも内容ハッシュにする。
 *
 * 名刺画像を消すと解析と候補も消える。[candidateCount] は読み取り結果を記録したときの候補の件数で、
 * レビューで候補を消した後も残す。vCard の `PHOTO` を埋め込むかの判定に使う。
 */
@Entity(
  tableName = "analysis",
  foreignKeys = [
    ForeignKey(
      entity = SourceImageEntity::class,
      parentColumns = ["content_hash"],
      childColumns = ["source_image"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
internal data class AnalysisEntity(
  @PrimaryKey @ColumnInfo(name = "source_image") val sourceImage: String,
  val status: StoredAnalysisStatus,
  @ColumnInfo(name = "candidate_count") val candidateCount: Int? = null,
)

internal enum class StoredAnalysisStatus {
  NOT_ANALYZED,
  ANALYZING,
  AWAITING_REVIEW,
  COMPLETED,
  FAILED,
}

/** 確認待ちの候補。解析が返した順序を [position] で保つ。 */
@Entity(
  tableName = "candidate",
  primaryKeys = ["source_image", "position"],
  foreignKeys = [
    ForeignKey(
      entity = AnalysisEntity::class,
      parentColumns = ["source_image"],
      childColumns = ["source_image"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
internal data class CandidateEntity(
  @ColumnInfo(name = "source_image") val sourceImage: String,
  val position: Int,
  val fn: String?,
  @Embedded val details: DetailsColumns,
)

/**
 * 確定した名刺。由来する名刺画像を参照する。
 *
 * 名刺が残っている名刺画像は消せない（`RESTRICT`）。
 */
@Entity(
  tableName = "card",
  foreignKeys = [
    ForeignKey(
      entity = SourceImageEntity::class,
      parentColumns = ["content_hash"],
      childColumns = ["source_image"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [Index("source_image")],
)
internal data class CardEntity(
  @PrimaryKey val uid: String,
  @ColumnInfo(name = "source_image") val sourceImage: String,
  val fn: String,
  @Embedded val details: DetailsColumns,
)

/** `FN` 以外の項目。複数値の `TEL` と `EMAIL` は JSON 配列で 1 列に収める。 */
internal data class DetailsColumns(
  val n: String?,
  val org: String?,
  val title: String?,
  val tel: List<String>,
  val email: List<String>,
  val adr: String?,
  val url: String?,
  val note: String?,
)

/** 保存済みの名刺と、取り込み元の解析が返した候補の件数。 */
internal data class SavedCardRow(
  @Embedded val card: CardEntity,
  @ColumnInfo(name = "candidate_count") val candidateCount: Int?,
)
