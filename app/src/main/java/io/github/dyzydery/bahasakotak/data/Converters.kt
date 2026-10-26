package io.github.dyzydery.bahasakotak.data

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {
    private val stringList = ListSerializer(String.serializer())

    @TypeConverter
    fun fromStringList(value: List<String>): String = Json.encodeToString(stringList, value)

    @TypeConverter
    fun toStringList(value: String): List<String> = Json.decodeFromString(stringList, value)
}
