package dev.yutakobayashi.tansu.testing

import dev.yutakobayashi.tansu.data.analysis.AnalysisClient
import dev.yutakobayashi.tansu.data.analysis.AnalysisClientResult
import dev.yutakobayashi.tansu.data.analysis.AnalysisFailure
import dev.yutakobayashi.tansu.data.analysis.AnalysisImage
import dev.yutakobayashi.tansu.data.settings.AnalysisCredentials
import kotlinx.coroutines.CompletableDeferred

/**
 * 積んだ順に固定の結果を返す [AnalysisClient]。HTTP を使わず、呼び出しを記録する。
 *
 * 結果を積まずに呼ぶとテストを失敗させる。リクエスト数を数えるテストで、想定外の呼び出しを見逃さないため。
 */
class FakeAnalysisClient : AnalysisClient {
  data class Call(val credentials: AnalysisCredentials, val image: AnalysisImage)

  private val responses = ArrayDeque<suspend () -> AnalysisClientResult>()
  private val recorded = mutableListOf<Call>()

  /** これまでの呼び出し。古い順。 */
  val calls: List<Call> get() = synchronized(this) { recorded.toList() }

  /** 呼び出し回数。1 回の呼び出しは HTTP リクエスト 1 回に相当する。 */
  val callCount: Int get() = calls.size

  fun enqueue(result: AnalysisClientResult): FakeAnalysisClient = enqueueAnswer { result }

  fun enqueue(fixture: AnalysisFixture): FakeAnalysisClient = enqueue(fixture.result)

  fun enqueue(failure: AnalysisFailure): FakeAnalysisClient = enqueue(AnalysisClientResult.Failed(failure))

  /**
   * [result] が完了するまで応答を止める。`解析中` の状態や中断を観察するときに使う。
   *
   * 呼ばれた時点を待つには [started] を使う。
   */
  fun enqueuePending(
    result: CompletableDeferred<AnalysisClientResult>,
    started: CompletableDeferred<Unit> = CompletableDeferred(),
  ): FakeAnalysisClient = enqueueAnswer {
    started.complete(Unit)
    result.await()
  }

  fun enqueueAnswer(answer: suspend () -> AnalysisClientResult): FakeAnalysisClient {
    synchronized(this) { responses.addLast(answer) }
    return this
  }

  override suspend fun analyze(credentials: AnalysisCredentials, image: AnalysisImage): AnalysisClientResult {
    val answer =
      synchronized(this) {
        recorded += Call(credentials, image)
        responses.removeFirstOrNull()
      } ?: throw AssertionError("FakeAnalysisClient was called $callCount times but no response was queued")
    return answer()
  }
}
