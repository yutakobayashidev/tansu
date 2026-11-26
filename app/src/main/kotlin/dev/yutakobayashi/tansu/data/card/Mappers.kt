package dev.yutakobayashi.tansu.data.card

import dev.yutakobayashi.tansu.domain.Analysis
import dev.yutakobayashi.tansu.domain.AnalysisStatus
import dev.yutakobayashi.tansu.domain.Candidate
import dev.yutakobayashi.tansu.domain.Card
import dev.yutakobayashi.tansu.domain.CardDetails
import dev.yutakobayashi.tansu.domain.ContentHash
import dev.yutakobayashi.tansu.domain.CopiedFileRef
import dev.yutakobayashi.tansu.domain.PropertyValue
import dev.yutakobayashi.tansu.domain.SourceImage
import dev.yutakobayashi.tansu.domain.Uid
import java.util.UUID

internal fun SourceImage.toEntity() = SourceImageEntity(contentHash.value, copiedFile.value)

internal fun SourceImageEntity.toDomain() =
  SourceImage(ContentHash(contentHash), CopiedFileRef(copiedFile))

internal fun AnalysisEntity.toDomain(candidates: List<CandidateEntity>): Analysis {
  val hash = ContentHash(sourceImage)
  val status = when (status) {
    StoredAnalysisStatus.NOT_ANALYZED -> AnalysisStatus.NotAnalyzed
    StoredAnalysisStatus.ANALYZING -> AnalysisStatus.Analyzing
    StoredAnalysisStatus.AWAITING_REVIEW ->
      AnalysisStatus.AwaitingReview(candidates.map { it.toDomain() })
    StoredAnalysisStatus.COMPLETED -> AnalysisStatus.Completed
    StoredAnalysisStatus.FAILED -> AnalysisStatus.Failed
  }
  return Analysis(hash, status)
}

internal fun Candidate.toEntity(position: Int) = CandidateEntity(
  sourceImage = sourceImage.value,
  position = position,
  fn = fn?.value,
  details = details.toColumns(),
)

internal fun CandidateEntity.toDomain() = Candidate(
  sourceImage = ContentHash(sourceImage),
  fn = PropertyValue.of(fn),
  details = details.toDomain(),
)

internal fun Card.toEntity(sourceImage: ContentHash) = CardEntity(
  uid = uid.toString(),
  sourceImage = sourceImage.value,
  fn = fn.value,
  details = details.toColumns(),
)

internal fun CardEntity.toDomain(): Card = Card.of(
  uid = Uid(UUID.fromString(uid)),
  fn = requireNotNull(PropertyValue.of(fn)) { "Stored card has an empty FN" },
  details = details.toDomain(),
)

private fun CardDetails.toColumns() = DetailsColumns(
  n = n?.value,
  org = org?.value,
  title = title?.value,
  tel = tel.map { it.value },
  email = email.map { it.value },
  adr = adr?.value,
  url = url?.value,
  note = note?.value,
)

private fun DetailsColumns.toDomain() = CardDetails.fromRaw(
  n = n,
  org = org,
  title = title,
  tel = tel,
  email = email,
  adr = adr,
  url = url,
  note = note,
)
