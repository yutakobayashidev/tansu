package dev.yutakobayashi.tansu.testing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SseTest {
  @get:Rule val api = MockAnalysisServer()

  @Test
  fun encodeAndDecodeRoundTripMultilineData() {
    val events = listOf(Sse.Event("a\nb", "first"), Sse.Event("c"))

    assertEquals("event: first\ndata: a\ndata: b\n\ndata: c\n\n", Sse.encode(events))
    assertEquals(events, Sse.decode(Sse.encode(events)))
  }

  @Test
  fun decodeIgnoresCommentsAndUnknownFields() {
    assertEquals(listOf(Sse.Event("x", "e")), Sse.decode(": keep-alive\r\nid: 1\r\nevent: e\r\ndata:x\r\n\r\n"))
  }

  @Test
  fun chatCompletionChunksReassembleToTheFixtureOverHttp() {
    for (fixture in AnalysisFixture.entries) {
      api.enqueueStream(fixture, chunkSize = 7)

      val response = OkHttpClient().newCall(Request.Builder().url(api.credentials.baseUrl + "/chat/completions").build()).execute()

      response.use {
        assertTrue(it.header("Content-Type")!!.startsWith("text/event-stream"))
        val events = Sse.decode(it.body!!.string())
        assertEquals("[DONE]", events.last().data)
        val chunks = events.dropLast(1).map { event -> Json.parseToJsonElement(event.data).jsonObject }
        val choices = chunks.map { chunk -> chunk["choices"]!!.jsonArray.single().jsonObject }
        val text = choices.mapNotNull { choice -> choice["delta"]!!.jsonObject["content"]?.jsonPrimitive?.content }
        assertEquals(fixture.name, fixture.content, text.joinToString(""))
        assertEquals("stop", choices.last()["finish_reason"]!!.jsonPrimitive.content)
        assertTrue(choices.dropLast(1).all { choice -> choice["finish_reason"] == JsonNull })
      }
    }
  }

  @Test
  fun responsesEventsReassembleToTheContent() {
    val content = AnalysisFixture.JapaneseSingle.content

    val events = Sse.responsesEvents(content, chunkSize = 5)

    assertEquals("response.created", events.first().event)
    assertEquals("response.completed", events.last().event)
    val deltas =
      events
        .filter { it.event == "response.output_text.delta" }
        .map { Json.parseToJsonElement(it.data).jsonObject["delta"]!!.jsonPrimitive.content }
    assertEquals(content, deltas.joinToString(""))
    events.forEach { assertEquals(it.event, Json.parseToJsonElement(it.data).jsonObject["type"]!!.jsonPrimitive.content) }
  }

  @Test
  fun chunksDoNotSplitSurrogatePairs() {
    val content = "a😀b😀"

    val deltas =
      Sse.chatCompletionChunks(content, chunkSize = 2)
        .dropLast(1)
        .mapNotNull {
          Json.parseToJsonElement(it.data).jsonObject["choices"]!!.jsonArray.single().jsonObject["delta"]!!
            .jsonObject["content"]?.jsonPrimitive?.content
        }
        .filter { it.isNotEmpty() }

    assertEquals(content, deltas.joinToString(""))
    deltas.forEach { assertTrue(it, !Character.isHighSurrogate(it.last())) }
  }
}
