package dev.yutakobayashi.tansu.debug

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.yutakobayashi.tansu.MainActivity
import dev.yutakobayashi.tansu.appContainer
import dev.yutakobayashi.tansu.data.analysis.AnalysisClientResult
import dev.yutakobayashi.tansu.data.analysis.ReadCard
import kotlinx.coroutines.launch

/**
 * 解析クライアントを、HTTP を使わず固定の読み取り結果を返す偽物に差し替える。debug ビルド専用。
 *
 * レビュー画面のタップ数（AC19）を E2E で測るために使う。接続先の設定が未完了なら、
 * 架空の値で埋めて解析ボタンを押せるようにする。差し替えはプロセスが終わるまで続くので、
 * 終わったらアプリを開き直す（`MainActivity` を起動する）。Maestro の `launchApp` は
 * プロセスを止めるため、この deep link の後には使わない。
 *
 * ```
 * adb shell am start -W -a android.intent.action.VIEW \
 *   -d 'tansu-debug://fake-analysis?fixture=three-people'
 * ```
 *
 * `fixture` は `three-people`（3 人）、`needs-attention`（`FN` 空と形式不正の `EMAIL`）、
 * `same-fn`（`FN` が一致する 2 人）、`no-card`（該当なし）。値はすべて架空。
 */
class FakeAnalysisActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val fixture = intent.data?.getQueryParameter("fixture")
    lifecycleScope.launch {
      val result = runCatching { install(requireNotNull(FIXTURES[fixture]) { "Unknown fixture: $fixture" }) }
      result.exceptionOrNull()?.let { Log.e(TAG, "Failed to install fake analysis $fixture", it) }
      if (result.isSuccess) {
        Log.i(TAG, "Installed fake analysis $fixture")
        startActivity(
          Intent(this@FakeAnalysisActivity, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
      }
      setResult(if (result.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED)
      finish()
    }
  }

  private suspend fun install(cards: List<ReadCard>) {
    val container = appContainer
    val settings = container.byoSettingsRepository
    if (!settings.summary.value.isComplete) {
      settings.save(baseUrl = "https://api.example.invalid/v1", model = "fake-model", apiKey = "sk-fake")
    }
    container.analysisClient = { _, _ -> AnalysisClientResult.Read(cards) }
  }

  private companion object {
    const val TAG = "TansuFakeAnalysis"

    fun card(fn: String?, org: String? = null, email: List<String> = emptyList()) =
      ReadCard(fn = fn, n = null, org = org, title = null, tel = emptyList(), email = email, adr = null, url = null, note = null)

    val FIXTURES =
      mapOf(
        "three-people" to
          listOf(
            card("山田 太郎", "株式会社サンプル商事", listOf("taro.yamada@example.co.jp")),
            card("Jane Doe", "Example Widgets Inc.", listOf("jane.doe@example.com")),
            card("佐藤 花子", "架空大学"),
          ),
        "needs-attention" to
          listOf(card("", "株式会社サンプル商事"), card("鈴木 一郎", "株式会社サンプル商事", listOf("ichiro.suzuki@@example"))),
        "same-fn" to listOf(card("山田 太郎", "株式会社サンプル商事"), card("山田 太郎", "架空大学")),
        "no-card" to emptyList(),
      )
  }
}
