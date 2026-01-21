package com.kgapp.encryptionchat.sdk.model

data class RequestData(
    val type: String,
    val pub: String,
    val ts: Long,
    val payload: Map<String, Any?> = emptyMap()
) {
    fun toMap(): Map<String, Any?> {
        return buildMap {
            put("type", type)
            put("pub", pub)
            put("ts", ts)
            payload.forEach { (key, value) ->
                put(key, value)
            }
        }
    }
}
