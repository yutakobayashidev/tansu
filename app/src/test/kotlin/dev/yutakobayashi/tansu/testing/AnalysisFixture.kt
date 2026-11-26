package dev.yutakobayashi.tansu.testing

import dev.yutakobayashi.tansu.data.analysis.AnalysisClientResult
import dev.yutakobayashi.tansu.data.analysis.ChatCompletionsProtocol
import dev.yutakobayashi.tansu.data.analysis.ReadCard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * 解析の固定応答。モデルが `json_schema` に従って返す本文（`{"cards": [...]}`）を
 * `src/test/resources/fixtures/analysis/` に置く。値はすべて架空。
 *
 * 同じ本文を [FakeAnalysisClient]・[MockAnalysisServer]・[Sse] のどれからでも返せる。
 */
enum class AnalysisFixture(val fileName: String, val expectedCards: Int) {
  /** 日本語の名刺 1 枚（AC3）。 */
  JapaneseSingle("japanese-single.json", 1),

  /** 英語の名刺 1 枚（AC3）。 */
  EnglishSingle("english-single.json", 1),

  /** 3 人分の名刺が写った画像（AC15・AC22）。 */
  ThreePeople("three-people.json", 3),

  /** 名刺が写っていない画像。該当なし（AC18）。 */
  NoCard("no-card.json", 0),

  /**
   * 注意が必要な候補 2 件（AC4・AC20）。
   *
   * 1 件目は `FN` が空文字で確定できない（赤）。2 件目は形式不正の `EMAIL` を持つ（黄）。
   * 空文字や空白だけの値も含む。
   */
  NeedsAttention("needs-attention.json", 2),

  /** カンマ・二重引用符・改行を含む値（AC10）。CSV と vCard のエスケープ確認に使う。 */
  SpecialCharacters("special-characters.json", 1),
  ;

  /** モデルが返す本文そのもの。Chat Completions では `message.content` に入る。 */
  val content: String by lazy {
    val path = "fixtures/analysis/$fileName"
    checkNotNull(AnalysisFixture::class.java.classLoader!!.getResource(path)) { "Missing fixture $path" }
      .readText(Charsets.UTF_8)
  }

  /** 本番と同じ厳格な読み取りで得た名刺の一覧。読めない本文ならここで失敗する。 */
  val cards: List<ReadCard> by lazy {
    checkNotNull(ChatCompletionsProtocol.parseResponse(chatCompletionBody(content))) {
      "Fixture $fileName does not match the analysis schema"
    }
  }

  /** [FakeAnalysisClient] に積む結果。 */
  val result: AnalysisClientResult get() = AnalysisClientResult.Read(cards)

  /** `POST /chat/completions` の非ストリーミング応答本文。 */
  val chatCompletionBody: String get() = chatCompletionBody(content)
}

/** [content] を `choices[0].message.content` に入れた Chat Completions の応答本文を作る。 */
fun chatCompletionBody(content: String, finishReason: String = "stop"): String {
  val escaped = Json.encodeToString(JsonPrimitive(content))
  return """{"id":"chatcmpl-fixture","object":"chat.completion","choices":[""" +
    """{"index":0,"message":{"role":"assistant","content":$escaped},"finish_reason":"$finishReason"}]}"""
}
