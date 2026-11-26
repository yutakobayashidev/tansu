package dev.yutakobayashi.tansu.data.analysis

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.yutakobayashi.tansu.data.card.CardRepository
import dev.yutakobayashi.tansu.data.card.SourceImageFiles
import dev.yutakobayashi.tansu.data.card.TansuDatabase
import dev.yutakobayashi.tansu.data.settings.AnalysisCredentials
import dev.yutakobayashi.tansu.domain.AnalysisStatus
import dev.yutakobayashi.tansu.domain.Candidate
import dev.yutakobayashi.tansu.domain.ContentHash
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnalysisRunnerTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val imageDirectory = File(context.filesDir, "source-images")
  private val server = MockWebServer()

  private lateinit var database: TansuDatabase
  private lateinit var repository: CardRepository
  private var credentials: AnalysisCredentials? = null

  @Before
  fun setUp() {
    context.deleteDatabase(DATABASE)
    imageDirectory.deleteRecursively()
    server.start()
    database = TansuDatabase.build(context, DATABASE)
    repository = CardRepository(database, SourceImageFiles(imageDirectory), Dispatchers.IO)
    credentials = AnalysisCredentials(server.url("/v1").toString().trimEnd('/'), "model-a", "sk-secret")
  }

  @After
  fun tearDown() {
    server.shutdown()
    database.close()
    context.deleteDatabase(DATABASE)
    imageDirectory.deleteRecursively()
  }

  private fun runner(client: AnalysisClient = ChatCompletionsAnalysisClient()) =
    AnalysisRunner(repository, { credentials }, client, ioDispatcher = Dispatchers.IO)

  private suspend fun importImage(): ContentHash = repository.import(ByteArrayInputStream(IMAGE)).sourceImage

  @Test
  fun unauthorizedFailsWithNoCardsAndRetrySendsExactlyOneMoreRequest() = runTest {
    // AC7
    val hash = importImage()
    val runner = runner()
    server.enqueue(MockResponse().setResponseCode(401))

    val failed = runner.run(hash)

    assertEquals(AnalysisRunResult.Failed(AnalysisFailure.Authentication), failed)
    assertEquals(AnalysisStatus.Failed, repository.analysis(hash)!!.status)
    assertTrue(repository.cards(hash).isEmpty())
    assertEquals(1, server.requestCount)

    server.enqueue(MockResponse().setResponseCode(401))
    val retried = runner.run(hash)

    assertEquals(AnalysisRunResult.Failed(AnalysisFailure.Authentication), retried)
    assertEquals(AnalysisStatus.Failed, repository.analysis(hash)!!.status)
    assertTrue(repository.cards(hash).isEmpty())
    assertEquals(2, server.requestCount)
  }

  @Test
  fun retryAfterFailureRecordsTheReadResult() = runTest {
    val hash = importImage()
    val runner = runner()
    server.enqueue(MockResponse().setResponseCode(429))
    server.enqueue(MockResponse().setBody(completion(CARD_JSON)))

    assertEquals(AnalysisRunResult.Failed(AnalysisFailure.RateLimited), runner.run(hash))
    assertEquals(1, server.requestCount)

    val result = runner.run(hash)

    val expected = listOf(Candidate.fromRaw(hash, "山田 太郎"))
    assertEquals(AnalysisRunResult.Read(expected), result)
    assertEquals(AnalysisStatus.AwaitingReview(expected), repository.analysis(hash)!!.status)
    assertEquals(2, server.requestCount)
  }

  @Test
  fun sendsTheStoredImageBytesUnmodified() = runTest {
    val hash = importImage()
    server.enqueue(MockResponse().setBody(completion("""{"cards":[]}""")))
    var sent: AnalysisImage? = null
    val recording = AnalysisClient { credentials, image ->
      sent = image
      ChatCompletionsAnalysisClient().analyze(credentials, image)
    }

    val result = runner(recording).run(hash)

    assertEquals(AnalysisRunResult.Read(emptyList()), result)
    assertArrayEquals(IMAGE, sent!!.bytes)
    assertEquals("image/png", sent?.mimeType)
  }

  @Test
  fun notConfiguredSendsNothingAndKeepsTheStatus() = runTest {
    val hash = importImage()
    credentials = null

    assertEquals(AnalysisRunResult.NotConfigured, runner().run(hash))
    assertEquals(AnalysisStatus.NotAnalyzed, repository.analysis(hash)!!.status)
    assertEquals(0, server.requestCount)
  }

  @Test
  fun unreadableImageFailsWithoutSendingARequest() = runTest {
    val hash = repository.import(ByteArrayInputStream("not an image".toByteArray())).sourceImage

    assertEquals(AnalysisRunResult.Failed(AnalysisFailure.UnreadableImage), runner().run(hash))
    assertEquals(AnalysisStatus.Failed, repository.analysis(hash)!!.status)
    assertEquals(0, server.requestCount)
  }

  @Test
  fun cancellationLeavesTheAnalysisFailedInsteadOfAnalyzing() = runTest {
    val hash = importImage()
    val started = CompletableDeferred<Unit>()
    val hanging = AnalysisClient { _, _ ->
      started.complete(Unit)
      awaitCancellation()
    }

    val job = launch(Dispatchers.Default) { runner(hanging).run(hash) }
    started.await()
    job.cancelAndJoin()

    assertEquals(AnalysisStatus.Failed, repository.analysis(hash)!!.status)
  }

  private companion object {
    const val DATABASE = "analysis-runner-test.db"
    val IMAGE = pngBytes(40, 25)

    const val CARD_JSON =
      """{"cards":[{"fn":"山田 太郎","n":null,"org":null,"title":null,"tel":[],"email":[],""" +
        """"adr":null,"url":null,"note":" "}]}"""

    fun completion(content: String): String {
      val escaped = kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(content))
      return """{"choices":[{"message":{"content":$escaped},"finish_reason":"stop"}]}"""
    }
  }
}
