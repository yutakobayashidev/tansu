package dev.yutakobayashi.tansu.testing

import dev.yutakobayashi.tansu.data.analysis.AnalysisClientResult
import dev.yutakobayashi.tansu.data.analysis.AnalysisFailure
import dev.yutakobayashi.tansu.data.analysis.AnalysisImage
import dev.yutakobayashi.tansu.data.settings.AnalysisCredentials
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class FakeAnalysisClientTest {
  private val credentials = AnalysisCredentials("https://example.invalid/v1", "model", "sk-test")
  private val image = AnalysisImage(byteArrayOf(9), "image/jpeg")

  @Test
  fun returnsQueuedResultsInOrderAndRecordsCalls() = runTest {
    val client = FakeAnalysisClient().enqueue(AnalysisFailure.Authentication).enqueue(AnalysisFixture.ThreePeople)

    assertEquals(AnalysisClientResult.Failed(AnalysisFailure.Authentication), client.analyze(credentials, image))
    assertEquals(AnalysisFixture.ThreePeople.result, client.analyze(credentials, image))
    assertEquals(2, client.callCount)
    assertEquals(FakeAnalysisClient.Call(credentials, image), client.calls.first())
  }

  @Test
  fun unexpectedCallFailsTheTestButIsCounted() = runTest {
    val client = FakeAnalysisClient()

    assertThrows(AssertionError::class.java) { kotlinx.coroutines.runBlocking { client.analyze(credentials, image) } }
    assertEquals(1, client.callCount)
  }

  @Test
  fun pendingResponseWaitsUntilCompleted() = runTest {
    val result = CompletableDeferred<AnalysisClientResult>()
    val started = CompletableDeferred<Unit>()
    val client = FakeAnalysisClient().enqueuePending(result, started)

    val call = async { client.analyze(credentials, image) }
    started.await()
    assertFalse(call.isCompleted)

    result.complete(AnalysisFixture.NoCard.result)
    assertEquals(AnalysisClientResult.Read(emptyList()), call.await())
  }
}
