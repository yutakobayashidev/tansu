package dev.yutakobayashi.tansu.testing

import dev.yutakobayashi.tansu.data.analysis.AnalysisClientResult
import dev.yutakobayashi.tansu.data.analysis.AnalysisImage
import dev.yutakobayashi.tansu.data.analysis.ChatCompletionsAnalysisClient
import dev.yutakobayashi.tansu.domain.Candidate
import dev.yutakobayashi.tansu.domain.CardDetails
import dev.yutakobayashi.tansu.domain.HASH_A
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnalysisFixtureTest {
  @get:Rule val api = MockAnalysisServer()

  private val client = ChatCompletionsAnalysisClient(connectTimeoutMillis = 2_000, callTimeoutMillis = 2_000)
  private val image = AnalysisImage(byteArrayOf(1, 2, 3), "image/png")

  @Test
  fun everyFixtureIsReadByTheProductionClientThroughOneRequest() = runTest {
    for ((index, fixture) in AnalysisFixture.entries.withIndex()) {
      api.enqueue(fixture)

      val result = client.analyze(api.credentials, image)

      assertEquals(fixture.name, AnalysisClientResult.Read(fixture.cards), result)
      assertEquals(fixture.name, fixture.expectedCards, fixture.cards.size)
      assertEquals(fixture.name, index + 1, api.requestCount)
    }
  }

  @Test
  fun thereAreSixDistinctFixtures() {
    assertEquals(6, AnalysisFixture.entries.size)
    assertEquals(6, AnalysisFixture.entries.map { it.content }.toSet().size)
  }

  @Test
  fun singleCardFixturesHaveAnFn() {
    for (fixture in listOf(AnalysisFixture.JapaneseSingle, AnalysisFixture.EnglishSingle)) {
      assertTrue(fixture.name, fixture.cards.single().fn!!.isNotBlank())
    }
  }

  @Test
  fun needsAttentionHasAnUnconfirmableCandidateAndAMalformedEmail() {
    val (missingFn, malformedEmail) = AnalysisFixture.NeedsAttention.cards.map(::candidate)

    assertFalse(missingFn.isConfirmable)
    assertEquals(null, missingFn.details.title)
    assertTrue(malformedEmail.isConfirmable)
    assertEquals(listOf("ichiro.suzuki@@example"), malformedEmail.details.email.map { it.value })
    assertEquals(null, malformedEmail.details.note)
  }

  @Test
  fun specialCharactersKeepCommasQuotesAndLineBreaks() {
    val card = AnalysisFixture.SpecialCharacters.cards.single()

    assertEquals("合同会社 \"テスト, 研究所\"\n第二開発部", card.org)
    assertEquals("主任研究員, \"R&D\"", card.title)
    assertEquals("1行目\r\n2行目", card.note)
  }

  private fun candidate(card: dev.yutakobayashi.tansu.data.analysis.ReadCard) =
    Candidate.fromRaw(
      HASH_A,
      card.fn,
      CardDetails.fromRaw(card.n, card.org, card.title, card.tel, card.email, card.adr, card.url, card.note),
    )
}
