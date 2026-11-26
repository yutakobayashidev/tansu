package dev.yutakobayashi.tansu.data.analysis

import dev.yutakobayashi.tansu.data.card.CardRepository
import dev.yutakobayashi.tansu.data.settings.AnalysisCredentialsProvider
import dev.yutakobayashi.tansu.domain.Candidate
import dev.yutakobayashi.tansu.domain.CardDetails
import dev.yutakobayashi.tansu.domain.ContentHash
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

sealed interface AnalysisRunResult {
  /** 読み取り結果を記録し、解析は `確認待ち` になった。候補が 0 件なら該当なし。 */
  data class Read(val candidates: List<Candidate>) : AnalysisRunResult

  /** 解析は `失敗` になった。 */
  data class Failed(val failure: AnalysisFailure) : AnalysisRunResult

  /** 設定が未完了で、解析を始めなかった。解析の状態は変えない。 */
  data object NotConfigured : AnalysisRunResult
}

/**
 * 名刺画像 1 枚を解析し、結果を [CardRepository] に記録する。
 *
 * 1 回の [run] で HTTP リクエストを高々 1 回だけ送る。自動リトライはしない。
 * 再試行は、利用者の操作で `失敗` の解析に対してもう一度 [run] を呼ぶことで行う。
 */
class AnalysisRunner(
  private val repository: CardRepository,
  private val credentialsProvider: AnalysisCredentialsProvider,
  private val client: AnalysisClient,
  private val imagePreparer: AnalysisImagePreparer = AnalysisImagePreparer(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  /** `未解析` または `失敗` の解析を実行する。 */
  suspend fun run(sourceImage: ContentHash): AnalysisRunResult {
    val credentials = credentialsProvider.credentials() ?: return AnalysisRunResult.NotConfigured
    val image = checkNotNull(repository.sourceImage(sourceImage)) { "Source image $sourceImage is missing" }
    repository.startAnalysis(sourceImage)
    val result =
      try {
        val prepared = withContext(ioDispatcher) { prepare(repository.file(image).readBytesOrNull()) }
        prepared?.let { client.analyze(credentials, it) }
          ?: AnalysisClientResult.Failed(AnalysisFailure.UnreadableImage)
      } catch (e: Throwable) {
        // 中断や想定外の例外で `解析中` のまま残さない。
        withContext(NonCancellable) { repository.recordFailure(sourceImage) }
        throw e
      }
    return when (result) {
      is AnalysisClientResult.Read -> {
        val candidates = result.cards.map { it.toCandidate(sourceImage) }
        repository.recordReadResult(sourceImage, candidates)
        AnalysisRunResult.Read(candidates)
      }
      is AnalysisClientResult.Failed -> {
        repository.recordFailure(sourceImage)
        AnalysisRunResult.Failed(result.failure)
      }
    }
  }

  private fun prepare(bytes: ByteArray?): AnalysisImage? = bytes?.let(imagePreparer::prepare)

  private fun java.io.File.readBytesOrNull(): ByteArray? =
    try {
      readBytes()
    } catch (_: IOException) {
      null
    }
}

private fun ReadCard.toCandidate(sourceImage: ContentHash): Candidate =
  Candidate.fromRaw(
    sourceImage = sourceImage,
    fn = fn,
    details =
      CardDetails.fromRaw(
        n = n,
        org = org,
        title = title,
        tel = tel,
        email = email,
        adr = adr,
        url = url,
        note = note,
      ),
  )
