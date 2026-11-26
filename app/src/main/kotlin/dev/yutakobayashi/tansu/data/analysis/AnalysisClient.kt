package dev.yutakobayashi.tansu.data.analysis

import dev.yutakobayashi.tansu.data.settings.AnalysisCredentials
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** 解析が失敗した理由。 */
enum class AnalysisFailure {
  /** 401 / 403。API キーが誤っているか権限がない。 */
  Authentication,

  /** タイムアウト・名前解決失敗・オフラインなど、応答を受け取れなかった。 */
  Network,

  /** 429。レート制限または利用上限に達した。 */
  RateLimited,

  /** 応答が期待した形でない。スキーマ不一致・パース不能のほか、上記以外の 2xx でない応答を含む。 */
  InvalidResponse,

  /** 名刺画像を画像として読めず、リクエストを送らなかった。 */
  UnreadableImage,
}

sealed interface AnalysisClientResult {
  data class Read(val cards: List<ReadCard>) : AnalysisClientResult

  data class Failed(val failure: AnalysisFailure) : AnalysisClientResult
}

fun interface AnalysisClient {
  suspend fun analyze(credentials: AnalysisCredentials, image: AnalysisImage): AnalysisClientResult
}

/**
 * OpenAI 互換 API の `POST {baseUrl}/chat/completions` で名刺画像を解析する。
 *
 * 1 回の呼び出しで HTTP リクエストを 1 回だけ送る。自動リトライはせず、リダイレクトにも従わない。
 * リダイレクトに従うと別ホストへ API キーを送りうるため、応答不正として扱う。
 */
class ChatCompletionsAnalysisClient(
  baseClient: OkHttpClient = OkHttpClient(),
  connectTimeoutMillis: Long = AnalysisDefaults.CONNECT_TIMEOUT_MILLIS,
  callTimeoutMillis: Long = AnalysisDefaults.CALL_TIMEOUT_MILLIS,
) : AnalysisClient {
  private val client =
    baseClient
      .newBuilder()
      .retryOnConnectionFailure(false)
      .followRedirects(false)
      .followSslRedirects(false)
      .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
      .readTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS)
      .writeTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS)
      .callTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS)
      .addNetworkInterceptor(SingleAttempt)
      .build()

  override suspend fun analyze(
    credentials: AnalysisCredentials,
    image: AnalysisImage,
  ): AnalysisClientResult {
    val request =
      Request.Builder()
        .url("${credentials.baseUrl}/chat/completions")
        .header("Authorization", "Bearer ${credentials.apiKey}")
        .header("Accept", "application/json")
        .tag(AttemptCount::class.java, AttemptCount())
        .post(ChatCompletionsProtocol.requestBody(credentials.model, image).toRequestBody(JSON))
        .build()
    return execute(client.newCall(request))
  }

  private suspend fun execute(call: Call): AnalysisClientResult =
    suspendCancellableCoroutine { continuation ->
      continuation.invokeOnCancellation { call.cancel() }
      call.enqueue(
        object : Callback {
          override fun onFailure(call: Call, e: IOException) {
            continuation.resume(AnalysisClientResult.Failed(AnalysisFailure.Network))
          }

          override fun onResponse(call: Call, response: Response) {
            val result =
              try {
                response.use(::interpret)
              } catch (_: IOException) {
                AnalysisClientResult.Failed(AnalysisFailure.Network)
              }
            continuation.resume(result)
          }
        },
      )
    }

  private fun interpret(response: Response): AnalysisClientResult =
    when (response.code) {
      in 200..299 ->
        ChatCompletionsProtocol.parseResponse(response.body?.string().orEmpty())
          ?.let { AnalysisClientResult.Read(it) }
          ?: AnalysisClientResult.Failed(AnalysisFailure.InvalidResponse)
      401,
      403 -> AnalysisClientResult.Failed(AnalysisFailure.Authentication)
      429 -> AnalysisClientResult.Failed(AnalysisFailure.RateLimited)
      else -> AnalysisClientResult.Failed(AnalysisFailure.InvalidResponse)
    }

  private class AttemptCount {
    var value = 0
  }

  /**
   * 1 回の呼び出しで送るリクエストを 1 回に限る。
   *
   * OkHttp は `retryOnConnectionFailure(false)` でも、503 に `Retry-After: 0` が付くと同じリクエストを
   * 送り直す。`Retry-After` は使わないので取り除き、それでも 2 回目が来たら送らずに失敗させる。
   */
  private object SingleAttempt : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
      val attempts = checkNotNull(chain.request().tag(AttemptCount::class.java))
      synchronized(attempts) {
        attempts.value += 1
        if (attempts.value > 1) throw IOException("Refusing to send the analysis request again")
      }
      return chain.proceed(chain.request()).newBuilder().removeHeader("Retry-After").build()
    }
  }

  private companion object {
    val JSON = "application/json".toMediaType()
  }
}
