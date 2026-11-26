package dev.yutakobayashi.tansu.data.settings

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

sealed interface ConnectionCheckResult {
  data object Success : ConnectionCheckResult

  /** 設定が未完了で、リクエストを送らなかった。 */
  data object NotConfigured : ConnectionCheckResult

  /** 401 / 403。API キーが誤っているか権限がない。 */
  data class Unauthorized(val statusCode: Int) : ConnectionCheckResult

  /** 上記以外の 2xx でない応答。 */
  data class HttpError(val statusCode: Int) : ConnectionCheckResult

  /** タイムアウト・名前解決失敗・オフラインなど、応答を受け取れなかった。 */
  data object NetworkError : ConnectionCheckResult
}

fun interface ConnectionChecker {
  suspend fun check(): ConnectionCheckResult
}

/**
 * 保存済みの設定で `GET {baseUrl}/models` を 1 回だけ送り、接続できるかを確かめる。
 *
 * 自動リトライはしない。リダイレクトに従うと別ホストへ API キーを送りうるため、従わずに失敗として扱う。
 */
class ModelsEndpointConnectionChecker(
  private val credentialsProvider: AnalysisCredentialsProvider,
  private val ioDispatcher: CoroutineDispatcher,
  private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) : ConnectionChecker {

  override suspend fun check(): ConnectionCheckResult {
    val credentials = credentialsProvider.credentials() ?: return ConnectionCheckResult.NotConfigured
    return withContext(ioDispatcher) { request(credentials) }
  }

  private fun request(credentials: AnalysisCredentials): ConnectionCheckResult {
    val connection =
      try {
        URL("${credentials.baseUrl}/models").openConnection() as HttpURLConnection
      } catch (_: IOException) {
        return ConnectionCheckResult.NetworkError
      }
    return try {
      connection.requestMethod = "GET"
      connection.instanceFollowRedirects = false
      connection.useCaches = false
      connection.connectTimeout = timeoutMillis
      connection.readTimeout = timeoutMillis
      connection.setRequestProperty("Authorization", "Bearer ${credentials.apiKey}")
      connection.setRequestProperty("Accept", "application/json")
      when (val status = connection.responseCode) {
        in 200..299 -> ConnectionCheckResult.Success
        401,
        403 -> ConnectionCheckResult.Unauthorized(status)
        else -> ConnectionCheckResult.HttpError(status)
      }
    } catch (_: IOException) {
      ConnectionCheckResult.NetworkError
    } finally {
      connection.disconnect()
    }
  }

  companion object {
    const val DEFAULT_TIMEOUT_MILLIS = 15_000
  }
}
