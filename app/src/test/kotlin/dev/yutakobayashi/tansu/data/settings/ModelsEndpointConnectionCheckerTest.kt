package dev.yutakobayashi.tansu.data.settings

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ModelsEndpointConnectionCheckerTest {
  private lateinit var server: HttpServer
  private val requests = CopyOnWriteArrayList<Pair<String, String?>>()
  private var status = 200

  @Before
  fun setUp() {
    server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") { exchange ->
      requests += exchange.requestURI.path to exchange.requestHeaders.getFirst("Authorization")
      val body = """{"data":[]}""".toByteArray()
      exchange.sendResponseHeaders(status, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }
    server.start()
  }

  @After
  fun tearDown() {
    server.stop(0)
  }

  private fun checker(credentials: AnalysisCredentials?) =
    ModelsEndpointConnectionChecker({ credentials }, Dispatchers.IO, timeoutMillis = 2_000)

  private fun credentials() =
    AnalysisCredentials("http://127.0.0.1:${server.address.port}/v1", "model-a", "sk-secret")

  @Test
  fun check_sendsOneAuthorizedRequestToModels() = runTest {
    assertEquals(ConnectionCheckResult.Success, checker(credentials()).check())
    assertEquals(listOf("/v1/models" to "Bearer sk-secret"), requests.toList())
  }

  @Test
  fun check_mapsUnauthorizedWithoutRetry() = runTest {
    status = 401
    assertEquals(ConnectionCheckResult.Unauthorized(401), checker(credentials()).check())
    assertEquals(1, requests.size)
  }

  @Test
  fun check_mapsOtherHttpErrors() = runTest {
    status = 404
    assertEquals(ConnectionCheckResult.HttpError(404), checker(credentials()).check())
    assertEquals(1, requests.size)
  }

  @Test
  fun check_doesNotFollowRedirects() = runTest {
    status = 302
    assertEquals(ConnectionCheckResult.HttpError(302), checker(credentials()).check())
    assertEquals(1, requests.size)
  }

  @Test
  fun check_reportsNetworkErrorWhenUnreachable() = runTest {
    val port = server.address.port
    server.stop(0)
    val result = checker(AnalysisCredentials("http://127.0.0.1:$port/v1", "m", "k")).check()
    assertEquals(ConnectionCheckResult.NetworkError, result)
  }

  @Test
  fun check_sendsNothingWhenNotConfigured() = runTest {
    assertEquals(ConnectionCheckResult.NotConfigured, checker(null).check())
    assertEquals(0, requests.size)
  }
}
