package dev.pulse.dsl

import kotlinx.serialization.json.Json

/**
 * The one JSON configuration for the DSL on every platform (mobile, services, AI server
 * golden tests).
 * - `ignoreUnknownKeys`: newer producers can add fields without breaking older clients.
 * - `encodeDefaults = false`: compact payloads, and fewer tokens when used as LLM few-shot examples.
 */
val PulseJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
    coerceInputValues = true
}
