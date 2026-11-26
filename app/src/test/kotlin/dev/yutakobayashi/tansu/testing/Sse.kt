package dev.yutakobayashi.tansu.testing

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.mockwebserver.MockResponse

/**
 * Server-Sent Events の応答を組み立てる。
 *
 * BYO の Chat Completions（`stream: true`）と、SIWC で使う Responses API（`/v1/responses`）の
 * 2 形式を作れる。本文を [chunkSize] 文字ずつに分け、受け手が断片を連結して元に戻せるかを確かめる。
 */
object Sse {
  const val DEFAULT_CHUNK_SIZE = 16

  data class Event(val data: String, val event: String? = null)

  /** [events] を `text/event-stream` の本文にした応答。 */
  fun response(events: List<Event>): MockResponse =
    MockResponse()
      .setHeader("Content-Type", "text/event-stream; charset=utf-8")
      .setHeader("Cache-Control", "no-cache")
      .setBody(encode(events))

  /** [events] を SSE の本文にする。`data` 内の改行は複数の `data:` 行に分ける。 */
  fun encode(events: List<Event>): String = buildString {
    for (event in events) {
      event.event?.let { append("event: ").append(it).append('\n') }
      for (line in event.data.split("\r\n", "\n", "\r")) append("data: ").append(line).append('\n')
      append('\n')
    }
  }

  /** SSE の本文を読む。`event` と `data` 以外のフィールドとコメント行は無視する。 */
  fun decode(body: String): List<Event> {
    val events = mutableListOf<Event>()
    var name: String? = null
    val data = mutableListOf<String>()
    for (line in body.split("\r\n", "\n", "\r")) {
      when {
        line.isEmpty() -> {
          if (data.isNotEmpty()) events += Event(data.joinToString("\n"), name)
          name = null
          data.clear()
        }
        line.startsWith(":") -> Unit
        else -> {
          val field = line.substringBefore(':')
          val value = line.substringAfter(':', "").removePrefix(" ")
          when (field) {
            "event" -> name = value
            "data" -> data += value
          }
        }
      }
    }
    if (data.isNotEmpty()) events += Event(data.joinToString("\n"), name)
    return events
  }

  /** Chat Completions のストリーミング応答。`delta.content` の断片を送り、`[DONE]` で終える。 */
  fun chatCompletionChunks(content: String, chunkSize: Int = DEFAULT_CHUNK_SIZE): List<Event> {
    fun chunk(delta: Map<String, String>, finishReason: String?) =
      Event(
        buildJsonObject {
            put("id", "chatcmpl-fixture")
            put("object", "chat.completion.chunk")
            putJsonArray("choices") {
              addJsonObject {
                put("index", 0)
                putJsonObject("delta") { delta.forEach { (key, value) -> put(key, value) } }
                put("finish_reason", finishReason?.let(::JsonPrimitive) ?: JsonNull)
              }
            }
          }
          .toString()
      )
    return buildList {
      add(chunk(mapOf("role" to "assistant", "content" to ""), null))
      content.chunkedByCodePoint(chunkSize).forEach { add(chunk(mapOf("content" to it), null)) }
      add(chunk(emptyMap(), "stop"))
      add(Event("[DONE]"))
    }
  }

  /** Responses API のストリーミング応答。`response.output_text.delta` を送り、`response.completed` で終える。 */
  fun responsesEvents(content: String, chunkSize: Int = DEFAULT_CHUNK_SIZE): List<Event> {
    fun event(type: String, body: JsonObjectBuilder.() -> Unit) =
      Event(
        buildJsonObject {
            put("type", type)
            body()
          }
          .toString(),
        type,
      )
    return buildList {
      add(event("response.created") { putResponse("in_progress") })
      content.chunkedByCodePoint(chunkSize).forEach { delta ->
        add(event("response.output_text.delta") {
          put("item_id", "msg-fixture")
          put("output_index", 0)
          put("content_index", 0)
          put("delta", delta)
        })
      }
      add(event("response.output_text.done") {
        put("item_id", "msg-fixture")
        put("output_index", 0)
        put("content_index", 0)
        put("text", content)
      })
      add(event("response.completed") { putResponse("completed") })
    }
  }

  private fun JsonObjectBuilder.putResponse(status: String) {
    putJsonObject("response") {
      put("id", "resp-fixture")
      put("object", "response")
      put("status", status)
    }
  }

  /** サロゲートペアを分断しないように分ける。 */
  private fun String.chunkedByCodePoint(size: Int): List<String> {
    require(size > 0)
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < length) {
      var end = minOf(start + size, length)
      if (end < length && Character.isHighSurrogate(this[end - 1])) end += 1
      chunks += substring(start, end)
      start = end
    }
    return chunks
  }
}
