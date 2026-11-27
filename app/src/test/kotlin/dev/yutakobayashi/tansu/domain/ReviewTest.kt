package dev.yutakobayashi.tansu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewTest {
  private fun candidate(fn: String?, vararg email: String) =
    Candidate.fromRaw(SOURCE_IMAGE, fn, CardDetails.fromRaw(email = email.toList()))

  @Test
  fun attentionIsRedForMissingFnYellowForMalformedEmailAndNoneOtherwise() {
    // AC20
    val reviews =
      CandidateReview.of(
        listOf(
          candidate(null, "a@example.com"),
          candidate("鈴木 一郎", "ok@example.com", "ichiro.suzuki@@example"),
          candidate("山田 太郎", "taro@example.co.jp"),
          // FN が空なら、EMAIL の形式に関わらず赤。
          candidate(" ", "broken"),
        )
      )

    assertEquals(
      listOf(Attention.Blocking, Attention.Warning, Attention.None, Attention.Blocking),
      reviews.map { it.attention },
    )
    assertEquals(setOf(1), reviews[1].malformedEmails)
    assertEquals(setOf(0), reviews[3].malformedEmails)
  }

  @Test
  fun emailFormat() {
    listOf("a@example.com", "taro.yamada@example.co.jp", "a+b@sub.example.jp").forEach {
      assertTrue(it, EmailFormat.isValid(it))
    }
    listOf("ichiro.suzuki@@example", "a@example", "@example.com", "a@", "a b@example.com", "a@example..com", "a@.com")
      .forEach { assertFalse(it, EmailFormat.isValid(it)) }
  }

  @Test
  fun everyConfirmableCandidateIsSelectedByDefault() {
    // AC15
    val review = ReadResultReview.of(listOf(candidate("山田 太郎"), candidate("Jane Doe"), candidate("佐藤 花子")))

    assertEquals(setOf(0, 1, 2), review.selected)
    assertEquals(listOf("山田 太郎", "Jane Doe", "佐藤 花子"), review.confirm().map { it.fn.value })
  }

  @Test
  fun deselectingSecondCandidateSavesTheOtherTwo() {
    // AC16
    val review = ReadResultReview.of(listOf(candidate("山田 太郎"), candidate("Jane Doe"), candidate("佐藤 花子")))

    val cards = review.select(1, include = false).confirm()

    assertEquals(listOf("山田 太郎", "佐藤 花子"), cards.map { it.fn.value })
  }

  @Test
  fun yellowIsSavedAndRedCannotBeSelected() {
    // AC4・AC20: 黄は保存を妨げず、赤は保存対象に入らない。
    val review = ReadResultReview.of(listOf(candidate(null), candidate("鈴木 一郎", "ichiro.suzuki@@example")))

    assertEquals(setOf(1), review.selected)
    assertEquals(review, review.select(0, include = true))
    assertEquals(listOf("鈴木 一郎"), review.confirm().map { it.fn.value })
  }

  @Test
  fun deselectingEverythingConfirmsNothing() {
    // AC17
    val review = ReadResultReview.of(listOf(candidate("山田 太郎"), candidate("Jane Doe")))

    assertTrue(review.select(0, false).select(1, false).confirm().isEmpty())
  }

  @Test
  fun noCandidatesIsNoCardsFound() {
    // AC18
    val review = ReadResultReview.of(emptyList())

    assertTrue(review.isNoCardsFound)
    assertTrue(review.confirm().isEmpty())
  }

  @Test
  fun candidatesSharingFnAreWarnedButKeptApart() {
    // AC27
    val review =
      ReadResultReview.of(listOf(candidate("山田 太郎"), candidate("Jane Doe"), candidate("山田 太郎"), candidate(null)))

    assertEquals(listOf(true, false, true, false), review.candidates.map { it.sharesFn })
    // 統合せず、どちらも保存対象のまま残す。
    assertEquals(setOf(0, 1, 2), review.selected)
    assertEquals(3, review.confirm().size)
  }

  @Test
  fun fnMustMatchExactlyToBeWarned() {
    // 表記の正規化はしないので、空白の違う FN は別の名前として扱う。
    val review = CandidateReview.of(listOf(candidate("山田 太郎"), candidate("山田　太郎"), candidate(null), candidate(null)))

    assertEquals(listOf(false, false, false, false), review.map { it.sharesFn })
  }

  private companion object {
    val SOURCE_IMAGE = ContentHash("a".repeat(64))
  }
}
