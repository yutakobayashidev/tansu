package dev.yutakobayashi.tansu.testing

import dev.yutakobayashi.tansu.data.settings.AnalysisCredentials
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.rules.ExternalResource

/**
 * OpenAI 互換 API の代わりに応答する [MockWebServer]。JUnit の `@get:Rule` で使う。
 *
 * ```
 * @get:Rule val api = MockAnalysisServer()
 * api.enqueue(AnalysisFixture.ThreePeople)
 * client.analyze(api.credentials, image)
 * assertEquals(1, api.requestCount)
 * ```
 */
class MockAnalysisServer : ExternalResource() {
  val server = MockWebServer()

  /** このサーバーを向く設定。base URL は `/v1` で終わり、末尾にスラッシュを付けない。 */
  val credentials: AnalysisCredentials
    get() = AnalysisCredentials(server.url("/v1").toString().trimEnd('/'), MODEL, API_KEY)

  /** これまでに受けたリクエスト数。応答を積んでいない呼び出しも数える。 */
  val requestCount: Int get() = server.requestCount

  override fun before() {
    server.start()
  }

  override fun after() {
    server.shutdown()
  }

  /** [fixture] を Chat Completions の応答として返す。 */
  fun enqueue(fixture: AnalysisFixture): MockAnalysisServer = enqueueCompletion(fixture.content)

  /** [content] を `message.content` に入れた Chat Completions の応答を返す。 */
  fun enqueueCompletion(content: String): MockAnalysisServer =
    enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(chatCompletionBody(content)))

  /** 本文なしで [code] を返す。401・429 などの失敗に使う。 */
  fun enqueueStatus(code: Int): MockAnalysisServer = enqueue(MockResponse().setResponseCode(code))

  /** [fixture] を [Sse.chatCompletionChunks] で分割し、ストリーミング応答として返す。 */
  fun enqueueStream(fixture: AnalysisFixture, chunkSize: Int = Sse.DEFAULT_CHUNK_SIZE): MockAnalysisServer =
    enqueue(Sse.response(Sse.chatCompletionChunks(fixture.content, chunkSize)))

  fun enqueue(response: MockResponse): MockAnalysisServer {
    server.enqueue(response)
    return this
  }

  /** 次のリクエストを返す。リクエストが来ていなければ失敗する。 */
  fun takeRequest(): RecordedRequest =
    checkNotNull(server.takeRequest(1, TimeUnit.SECONDS)) { "No request reached MockAnalysisServer" }

  companion object {
    const val MODEL = "model-fixture"
    const val API_KEY = "sk-fixture"
  }
}
