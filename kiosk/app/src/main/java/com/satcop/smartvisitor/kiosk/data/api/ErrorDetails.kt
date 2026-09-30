package com.satcop.smartvisitor.kiosk.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Flattens the API error `details` object: {"activeVisit":{"id":"V-1"}} -> "activeVisit.id" = "V-1". */
object ErrorDetails {
    fun flatten(el: JsonElement?): Map<String, String> {
        val out = linkedMapOf<String, String>()
        fun walk(prefix: String, e: JsonElement?) {
            when (e) {
                is JsonObject -> e.forEach { (k, v) -> walk(if (prefix.isEmpty()) k else "$prefix.$k", v) }
                is JsonArray, null, JsonNull -> Unit
                is JsonPrimitive -> if (prefix.isNotEmpty()) out[prefix] = e.content
            }
        }
        walk("", el)
        return out
    }
}
