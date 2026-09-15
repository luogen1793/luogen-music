package com.luogen.music.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull

/** JSON 便捷取值工具 */
fun JsonObject.str(key: String): String = this[key]?.let { e ->
    when (e) {
        is JsonPrimitive -> e.contentOrNull ?: ""
        else -> e.toString()
    }
} ?: ""

fun JsonObject.strOrNull(key: String): String? = this[key]?.let { e ->
    when (e) {
        is JsonPrimitive -> e.contentOrNull
        else -> e.toString()
    }
}

fun JsonObject.lng(key: String): Long = this[key]?.let { e ->
    when (e) {
        is JsonPrimitive -> e.longOrNull
        else -> null
    }
} ?: 0L

fun JsonObject.i(key: String): Int = this[key]?.let { e ->
    when (e) {
        is JsonPrimitive -> e.intOrNull
        else -> null
    }
} ?: 0

fun JsonObject.arr(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())

fun JsonObject.obj(key: String): JsonObject = this[key] as? JsonObject ?: JsonObject(emptyMap())

fun JsonElement?.asObj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

fun JsonElement?.asArr(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())

/** 数组元素字符串提取：用于 ar[].name -> artistNames */
fun JsonArray.stringsOf(key: String): List<String> =
    mapNotNull { (it as? JsonObject)?.str(key) }

fun JsonArray.longsOf(key: String): List<Long> =
    mapNotNull { (it as? JsonObject)?.lng(key) }

fun JsonObject.optArrLong(key: String): Long =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L