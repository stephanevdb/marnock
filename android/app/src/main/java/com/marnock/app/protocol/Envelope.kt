package com.marnock.app.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID

@Serializable
data class Envelope(
    val type: String,
    val id: String = UUID.randomUUID().toString(),
    val payload: JsonObject = buildJsonObject { }
)

fun jsonString(key: String, value: String) = key to JsonPrimitive(value)
fun jsonLong(key: String, value: Long) = key to JsonPrimitive(value)
fun jsonBool(key: String, value: Boolean) = key to JsonPrimitive(value)
fun jsonInt(key: String, value: Int) = key to JsonPrimitive(value)

fun JsonObject.str(key: String): String =
    (this[key] as? JsonPrimitive)?.content.orEmpty()

fun JsonObject.long(key: String): Long =
    (this[key] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L

fun JsonObject.bool(key: String): Boolean =
    (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false

fun JsonObject.arr(key: String): List<JsonElement> =
    (this[key] as? kotlinx.serialization.json.JsonArray)?.toList().orEmpty()
