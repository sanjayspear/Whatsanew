package dev.pulse.core.ai

import dev.pulse.dsl.IntentFrame
import dev.pulse.dsl.PulseJson

/**
 * Prompt + output contract for every LLM rung. The model is asked for an [IntentFrame] only
 * (30-60 tokens), never UI, so even a 2B INT4 model answers within the latency budget.
 * Few-shots mirror `packages/ui-dsl/examples`, and the distillation pipeline trains on the same
 * format, so prompt and fine-tune never drift apart.
 */
object LlmPrompt {

    val SYSTEM = """
        You convert a user's request in a city-discovery app into ONE JSON object (an IntentFrame).
        Output JSON only. No prose. Never invent places, phone numbers or events.
        Fields: task (DISCOVER|REFINE|CUSTOMIZE_THEME|CUSTOMIZE_LAYOUT|ACT|UNKNOWN), query{categories[],text,
        near{anchor{type:here|place,name},radiusMeters},time{preset:NOW|TODAY|TONIGHT|TOMORROW|THIS_WEEKEND|NEXT_7_DAYS},
        freshness(NEW|TRENDING),priceLevelMax(1-4),openNow}, theme{mood(DUSK|WARM|CALM|VIVID|NOIR|DAYLIGHT),
        motionScale,saturation,typeScale,blurScale,radiusScale}, layout[{type:move|hide|show,role,position}], confidence.
        Category keys look like food.cafe, food.restaurant.south_indian, arts.theatre, arts.cinema, music.live,
        comedy.standup, fitness.yoga, shopping, gems.
    """.trimIndent()

    private val FEW_SHOTS = listOf(
        "Show me new cafes in Koramangala within 3 km" to
            """{"task":"DISCOVER","query":{"categories":["food.cafe"],"near":{"anchor":{"type":"place","name":"Koramangala"},"radiusMeters":3000},"freshness":"NEW"},"confidence":0.95}""",
        "Change my app theme to something warm and quieter." to
            """{"task":"CUSTOMIZE_THEME","theme":{"mood":"WARM","motionScale":0.5,"saturation":0.75},"confidence":0.9}""",
        "Move the map to the top of the screen." to
            """{"task":"CUSTOMIZE_LAYOUT","layout":[{"type":"move","role":"map","position":0}],"confidence":0.95}""",
    )

    fun request(input: IntentInput, context: IntentContext): GenerationRequest {
        val prompt = buildString {
            FEW_SHOTS.forEach { (u, f) -> append("User: ").append(u).append("\nJSON: ").append(f).append("\n\n") }
            append("Screen sections: ").append(context.visibleRoles.joinToString().ifEmpty { "none" }).append('\n')
            if (input.visionTags.isNotEmpty()) append("Camera sees: ").append(input.visionTags.joinToString()).append('\n')
            append("Language: ").append(context.languageTag).append('\n')
            append("User: ").append(input.text.take(MAX_INPUT_CHARS)).append("\nJSON: ")
        }
        return GenerationRequest(systemInstruction = SYSTEM, prompt = prompt)
    }

    /** Extracts the first balanced JSON object and decodes it; returns null on any malformation. */
    fun parse(raw: String): IntentFrame? {
        val start = raw.indexOf('{').takeIf { it >= 0 } ?: return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val c = raw[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> if (--depth == 0) {
                    return runCatching { PulseJson.decodeFromString(IntentFrame.serializer(), raw.substring(start, i + 1)) }.getOrNull()
                }
            }
        }
        return null
    }

    private const val MAX_INPUT_CHARS = 400
}
