package dev.yutakobayashi.tansu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisTest {
  @Test
  fun status_hasFiveStates() {
    val states: List<AnalysisStatus> = listOf(
      AnalysisStatus.NotAnalyzed,
      AnalysisStatus.Analyzing,
      AnalysisStatus.AwaitingReview(emptyList()),
      AnalysisStatus.Completed,
      AnalysisStatus.Failed,
    )
    // when が網羅的でなければコンパイルできない
    val labels = states.map {
      when (it) {
        AnalysisStatus.NotAnalyzed -> "未解析"
        AnalysisStatus.Analyzing -> "解析中"
        is AnalysisStatus.AwaitingReview -> "確認待ち"
        AnalysisStatus.Completed -> "完了"
        AnalysisStatus.Failed -> "失敗"
      }
    }
    assertEquals(listOf("未解析", "解析中", "確認待ち", "完了", "失敗"), labels)
  }

  @Test
  fun awaitingReview_withNoCandidatesIsNoCardsFoundNotFailure() {
    val analysis = Analysis(HASH_A, AnalysisStatus.AwaitingReview(emptyList()))
    val status = analysis.status as AnalysisStatus.AwaitingReview
    assertTrue(status.isNoCardsFound)
  }

  @Test
  fun awaitingReview_keepsAllCandidatesIncludingUnconfirmable() {
    val candidates = listOf(
      Candidate.fromRaw(HASH_A, fn = "A"),
      Candidate.fromRaw(HASH_A, fn = ""),
      Candidate.fromRaw(HASH_A, fn = "B"),
    )
    val status = Analysis(HASH_A, AnalysisStatus.AwaitingReview(candidates)).status
    status as AnalysisStatus.AwaitingReview
    assertFalse(status.isNoCardsFound)
    assertEquals(candidates, status.candidates)
  }

  @Test
  fun awaitingReview_rejectsCandidatesFromAnotherImage() {
    val status = AnalysisStatus.AwaitingReview(listOf(Candidate.fromRaw(HASH_B, fn = "A")))
    assertThrows(IllegalArgumentException::class.java) { Analysis(HASH_A, status) }
  }
}
