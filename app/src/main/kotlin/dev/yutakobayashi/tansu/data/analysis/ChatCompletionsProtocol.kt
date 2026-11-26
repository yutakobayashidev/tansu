package dev.yutakobayashi.tansu.data.analysis

import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** 解析が読み取った名刺 1 件分の項目。値は応答の文字列のままで、空の値もここでは落とさない。 */
@Serializable
data class ReadCard(
  val fn: String?,
  val n: String?,
  val org: String?,
  val title: String?,
  val tel: List<String>,
  val email: List<String>,
  val adr: String?,
  val url: String?,
  val note: String?,
)

/** Chat Completions の `json_schema` 応答形式で受け取る本体。 */
@Serializable
internal data class ReadCards(val cards: List<ReadCard>)

/** Chat Completions の要求本文の組み立てと、応答の読み取り。 */
internal object ChatCompletionsProtocol {
  private const val SCHEMA_NAME = "business_cards"

  private const val INSTRUCTIONS =
    "You read business cards in a photo. Return one element in `cards` for every business card " +
      "visible in the image, including cards that are partly visible. Copy text exactly as printed: " +
      "do not translate, guess, complete or normalize values. Use null for a property that is not " +
      "printed and an empty array when there is no TEL or EMAIL. If no business card is visible, " +
      "return an empty `cards` array. Property names follow vCard 4.0."

  private val responseJson = Json { ignoreUnknownKeys = true }

  /** `json_schema` の厳格モードに合わせ、未知のキーや欠けたキーを応答不正として扱う。 */
  private val payloadJson = Json { ignoreUnknownKeys = false }

  fun requestBody(model: String, image: AnalysisImage): String {
    val dataUrl = "data:${image.mimeType};base64,${Base64.getEncoder().encodeToString(image.bytes)}"
    return buildJsonObject {
        put("model", model)
        putJsonArray("messages") {
          addJsonObject {
            put("role", "system")
            put("content", INSTRUCTIONS)
          }
          addJsonObject {
            put("role", "user")
            putJsonArray("content") {
              addJsonObject {
                put("type", "image_url")
                putJsonObject("image_url") { put("url", dataUrl) }
              }
            }
          }
        }
        putJsonObject("response_format") {
          put("type", "json_schema")
          putJsonObject("json_schema") {
            put("name", SCHEMA_NAME)
            put("strict", true)
            put("schema", cardsSchema())
          }
        }
      }
      .toString()
  }

  /**
   * 応答本文から名刺の一覧を取り出す。期待した形でなければ `null` を返す。
   *
   * 拒否（`refusal`）や、出力の上限で打ち切られた応答（`finish_reason = length`）も期待した形ではない。
   */
  fun parseResponse(body: String): List<ReadCard>? =
    try {
      val choice = responseJson.decodeFromString<ChatCompletion>(body).choices.singleOrNull()
      val content = choice?.message?.content
      if (choice == null || content == null || choice.message.refusal != null || choice.finishReason == "length") {
        null
      } else {
        payloadJson.decodeFromString<ReadCards>(content).cards
      }
    } catch (_: SerializationException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }

  private fun cardsSchema(): JsonObject {
    val nullableString = buildJsonObject { put("type", JsonArray(listOf(JsonPrimitive("string"), JsonPrimitive("null")))) }
    val stringArray = buildJsonObject {
      put("type", "array")
      putJsonObject("items") { put("type", "string") }
    }
    val cardProperties =
      linkedMapOf(
        "fn" to nullableString,
        "n" to nullableString,
        "org" to nullableString,
        "title" to nullableString,
        "tel" to stringArray,
        "email" to stringArray,
        "adr" to nullableString,
        "url" to nullableString,
        "note" to nullableString,
      )
    val card = buildJsonObject {
      put("type", "object")
      put("properties", JsonObject(cardProperties))
      put("required", JsonArray(cardProperties.keys.map(::JsonPrimitive)))
      put("additionalProperties", false)
    }
    return buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("cards") {
          put("type", "array")
          put("items", card)
        }
      }
      put("required", buildJsonArray { add(JsonPrimitive("cards")) })
      put("additionalProperties", false)
    }
  }

  @Serializable
  private data class ChatCompletion(val choices: List<Choice>)

  @Serializable
  private data class Choice(
    val message: Message,
    @SerialName("finish_reason") val finishReason: String? = null,
  )

  @Serializable
  private data class Message(val content: String? = null, val refusal: String? = null)
}
