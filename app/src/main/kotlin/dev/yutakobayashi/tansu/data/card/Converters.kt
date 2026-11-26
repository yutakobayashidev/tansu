package dev.yutakobayashi.tansu.data.card

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

internal class Converters {
  @TypeConverter
  fun fromStringList(values: List<String>): String = Json.encodeToString(values)

  @TypeConverter
  fun toStringList(json: String): List<String> = Json.decodeFromString(json)
}
