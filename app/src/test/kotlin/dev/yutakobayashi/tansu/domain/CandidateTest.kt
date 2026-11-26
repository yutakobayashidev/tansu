package dev.yutakobayashi.tansu.domain

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateTest {
  private val uid = Uid(UUID.fromString("00000000-0000-4000-8000-000000000001"))

  @Test
  fun confirm_failsWhenFnIsMissing() {
    val candidate = Candidate.fromRaw(HASH_A, fn = null, CardDetails.fromRaw(org = "Example"))
    assertFalse(candidate.isConfirmable)
    assertEquals(ConfirmationResult.MissingFn, candidate.confirm(uid))
  }

  @Test
  fun confirm_failsWhenFnIsBlank() {
    for (raw in listOf("", " ", "　", "\t\n")) {
      val candidate = Candidate.fromRaw(HASH_A, fn = raw)
      assertNull(candidate.fn)
      assertEquals(ConfirmationResult.MissingFn, candidate.confirm(uid))
    }
  }

  @Test
  fun confirm_createsCardWithFnDetailsAndGivenUid() {
    val details = CardDetails.fromRaw(tel = listOf("03-0000-0000", "090-0000-0000"))
    val candidate = Candidate.fromRaw(HASH_A, fn = "山田 太郎", details)
    assertTrue(candidate.isConfirmable)

    val card = (candidate.confirm(uid) as ConfirmationResult.Confirmed).card
    assertEquals(uid, card.uid)
    assertEquals("山田 太郎", card.fn.value)
    assertEquals(details, card.details)
  }

  @Test
  fun confirm_withoutUidAssignsDistinctUids() {
    val candidate = Candidate.fromRaw(HASH_A, fn = "山田 太郎")
    val first = (candidate.confirm() as ConfirmationResult.Confirmed).card
    val second = (candidate.confirm() as ConfirmationResult.Confirmed).card
    assertTrue(first.uid != second.uid)
  }
}
