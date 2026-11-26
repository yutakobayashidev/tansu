package dev.yutakobayashi.tansu.data.analysis

import dev.yutakobayashi.tansu.data.settings.AnalysisCredentials
import dev.yutakobayashi.tansu.testing.Golden
import dev.yutakobayashi.tansu.testing.chatCompletionBody
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ChatCompletionsAnalysisClientTest {
  private lateinit var server: MockWebServer

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  private val client = ChatCompletionsAnalysisClient(connectTimeoutMillis = 2_000, callTimeoutMillis = 2_000)

  private fun credentials(target: MockWebServer = server) =
    AnalysisCredentials(target.url("/v1").toString().trimEnd('/'), "model-a", "sk-secret")

  private suspend fun analyze() = client.analyze(credentials(), AnalysisImage(IMAGE, "image/png"))

  @Test
  fun analyze_sendsOneChatCompletionsRequestWithTheImageBytes() = runTest {
    server.enqueue(MockResponse().setBody(chatCompletionBody(CARDS_JSON)))

    val result = analyze()

    assertEquals(AnalysisClientResult.Read(listOf(EXPECTED_CARD)), result)
    assertEquals(1, server.requestCount)
    val request = server.takeRequest()
    assertEquals("POST", request.method)
    assertEquals("/v1/chat/completions", request.path)
    assertEquals("Bearer sk-secret", request.getHeader("Authorization"))
    val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
    assertEquals("model-a", body["model"]!!.jsonPrimitive.content)
    val format = body["response_format"]!!.jsonObject
    assertEquals("json_schema", format["type"]!!.jsonPrimitive.content)
    assertEquals("true", format["json_schema"]!!.jsonObject["strict"]!!.jsonPrimitive.content)
    val content = body["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray
    val url = content.single().jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content
    assertEquals("data:image/png;base64," + Base64.getEncoder().encodeToString(IMAGE), url)
  }

  @Test
  fun requestBody_matchesTheGolden() {
    // 指示文・スキーマ・画像の埋め込み方を変えたら、ゴールデンの差分で確認する。
    val body = ChatCompletionsProtocol.requestBody("model-a", AnalysisImage(IMAGE, "image/png"))

    Golden.assertMatches("analysis/chat-completions-request.json", prettyJson.encodeToString(Json.parseToJsonElement(body)) + "\n")
  }

  @Test
  fun analyze_treatsEmptyCardsAsARead() = runTest {
    server.enqueue(MockResponse().setBody(chatCompletionBody("""{"cards":[]}""")))
    assertEquals(AnalysisClientResult.Read(emptyList()), analyze())
  }

  @Test
  fun analyze_classifiesHttpFailuresWithoutRetry() = runTest {
    val cases =
      mapOf(
        401 to AnalysisFailure.Authentication,
        403 to AnalysisFailure.Authentication,
        429 to AnalysisFailure.RateLimited,
        400 to AnalysisFailure.InvalidResponse,
        500 to AnalysisFailure.InvalidResponse,
        302 to AnalysisFailure.InvalidResponse,
      )
    for ((status, failure) in cases) {
      MockWebServer().use { other ->
        other.enqueue(MockResponse().setResponseCode(status).setHeader("Location", other.url("/elsewhere")))
        other.enqueue(MockResponse().setBody(chatCompletionBody(CARDS_JSON)))

        val result = client.analyze(credentials(other), AnalysisImage(IMAGE, "image/png"))

        assertEquals("HTTP $status", AnalysisClientResult.Failed(failure), result)
        assertEquals("HTTP $status must be sent once", 1, other.requestCount)
      }
    }
  }

  @Test
  fun analyze_doesNotResendOn503WithRetryAfterZero() = runTest {
    server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
    server.enqueue(MockResponse().setBody(chatCompletionBody(CARDS_JSON)))

    assertEquals(AnalysisClientResult.Failed(AnalysisFailure.InvalidResponse), analyze())
    assertEquals(1, server.requestCount)
  }

  @Test
  fun analyze_reportsMalformedResponsesAsInvalid() = runTest {
    val bodies =
      listOf(
        "not json",
        """{"choices":[]}""",
        chatCompletionBody("not json"),
        chatCompletionBody("""{"cards":[{"fn":"A"}]}"""),
        chatCompletionBody("""{"cards":[], "extra": 1}"""),
        """{"choices":[{"message":{"content":null,"refusal":"I can't"},"finish_reason":"stop"}]}""",
        """{"choices":[{"message":{"content":"{\"cards\":[]}"},"finish_reason":"length"}]}""",
      )
    for (body in bodies) {
      server.enqueue(MockResponse().setBody(body))
      assertEquals(body, AnalysisClientResult.Failed(AnalysisFailure.InvalidResponse), analyze())
    }
    assertEquals(bodies.size, server.requestCount)
  }

  @Test
  fun analyze_reportsTimeoutAsNetworkFailure() = runTest {
    server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

    assertEquals(AnalysisClientResult.Failed(AnalysisFailure.Network), analyze())
    assertEquals(1, server.requestCount)
  }

  @Test
  fun analyze_reportsUnreachableServerAsNetworkFailure() = runTest {
    val credentials = credentials()
    server.shutdown()

    val result = client.analyze(credentials, AnalysisImage(IMAGE, "image/png"))

    assertEquals(AnalysisClientResult.Failed(AnalysisFailure.Network), result)
  }

  private companion object {
    val prettyJson = Json { prettyPrint = true }

    val IMAGE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 1, 2, 3)

    const val CARDS_JSON =
      """{"cards":[{"fn":"山田 太郎","n":"山田;太郎;;;","org":"Example 株式会社","title":null,""" +
        """"tel":["03-0000-0000"],"email":["taro@example.com"],"adr":null,"url":null,"note":null}]}"""

    val EXPECTED_CARD =
      ReadCard(
        fn = "山田 太郎",
        n = "山田;太郎;;;",
        org = "Example 株式会社",
        title = null,
        tel = listOf("03-0000-0000"),
        email = listOf("taro@example.com"),
        adr = null,
        url = null,
        note = null,
      )
  }
}
