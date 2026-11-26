package dev.yutakobayashi.tansu.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageProgressTest {
  @Test
  fun groupsAnalysisStatusesIntoThreeBadges() {
    assertEquals(ImageProgress.Unprocessed, ImageProgress.of(null))
    assertEquals(ImageProgress.Unprocessed, ImageProgress.of(AnalysisStatus.NotAnalyzed))
    assertEquals(ImageProgress.Unprocessed, ImageProgress.of(AnalysisStatus.Analyzing))
    assertEquals(ImageProgress.Unprocessed, ImageProgress.of(AnalysisStatus.Failed))
    assertEquals(ImageProgress.AwaitingReview, ImageProgress.of(AnalysisStatus.AwaitingReview(emptyList())))
    assertEquals(ImageProgress.Completed, ImageProgress.of(AnalysisStatus.Completed))
  }
}
