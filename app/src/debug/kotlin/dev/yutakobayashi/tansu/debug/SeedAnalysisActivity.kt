package dev.yutakobayashi.tansu.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import dev.yutakobayashi.tansu.appContainer
import dev.yutakobayashi.tansu.data.folder.FolderListing
import dev.yutakobayashi.tansu.domain.Candidate
import dev.yutakobayashi.tansu.domain.ConfirmationResult
import kotlinx.coroutines.launch

/**
 * 選択中のフォルダの画像 1 枚について、解析の状態を用意する。debug ビルド専用。
 *
 * 解析クライアントを経由せずに `確認待ち` や `完了` を作り、一覧のバッジを E2E で確かめるために使う。
 *
 * ```
 * adb shell am start -W -a android.intent.action.VIEW \
 *   -d 'tansu-debug://seed?name=card-b.jpg&state=completed'
 * ```
 *
 * `state` は `awaiting`（確認待ち）か `completed`（完了）。候補と名刺の中身は架空の固定値。
 */
class SeedAnalysisActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val name = intent.data?.getQueryParameter("name")
    val state = intent.data?.getQueryParameter("state")
    lifecycleScope.launch {
      val result = runCatching { seed(requireNotNull(name) { "name is required" }, state) }
      result.exceptionOrNull()?.let { Log.e(TAG, "Failed to seed $name as $state", it) }
      setResult(if (result.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED)
      finish()
    }
  }

  private suspend fun seed(name: String, state: String?) {
    val container = appContainer
    val listing = container.folderRepository.list()
    check(listing is FolderListing.Listed) { "Folder is not readable: $listing" }
    val image = listing.images.firstOrNull { it.displayName == name } ?: error("$name is not in the folder")
    val cards = container.cardRepository
    val hash = container.folderAccess.open(image.uri).use { cards.import(it) }.sourceImage
    cards.startAnalysis(hash)
    val candidate = Candidate.fromRaw(hash, SAMPLE_FN)
    cards.recordReadResult(hash, listOf(candidate))
    when (state) {
      "awaiting" -> Unit
      "completed" -> {
        val card = (candidate.confirm() as ConfirmationResult.Confirmed).card
        cards.completeReview(hash, listOf(card))
      }
      else -> error("Unknown state: $state")
    }
    Log.i(TAG, "Seeded $name as $state")
  }

  private companion object {
    const val TAG = "TansuSeed"
    const val SAMPLE_FN = "見本 太郎"
  }
}
