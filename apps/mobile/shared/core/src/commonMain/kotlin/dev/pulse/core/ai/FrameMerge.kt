package dev.pulse.core.ai

import dev.pulse.dsl.GeoAnchor
import dev.pulse.dsl.IntentFrame
import dev.pulse.dsl.Task

/**
 * Combines the rules frame (precise but limited) with the LLM frame (broad but can hallucinate).
 *
 * Policy: **the LLM may fill gaps, never contradict.** Every slot the rules understood wins; the
 * LLM only contributes slots the rules left empty. Any place the LLM names must actually appear in
 * what the user said, because small models copy places from their few-shot examples (observed on a
 * Pixel 8a: "what's new near me" came back as "cafés in Koramangala").
 */
object FrameMerge {

    fun merge(rules: IntentFrame, llm: IntentFrame, placeMentioned: (String) -> Boolean): IntentFrame {
        // Commands the rules recognised (theme, layout, act) are never re-interpreted.
        if (rules.task in setOf(Task.CUSTOMIZE_THEME, Task.CUSTOMIZE_LAYOUT, Task.ACT)) return rules
        if (llm.task != Task.DISCOVER && llm.task != Task.REFINE) {
            return if (rules.task == Task.UNKNOWN) llm else rules
        }
        val r = rules.query
        val l = llm.query ?: return rules
        val llmNear = l.near?.takeIf { near ->
            when (val a = near.anchor) {
                is GeoAnchor.Place -> placeMentioned(a.name)
                else -> true
            }
        }
        val merged = (r ?: l.copy(near = null)).copy(
            categories = r?.categories?.ifEmpty { null }
                // A name search stays a name search: the model may not turn "microsoft" into cafés.
                ?: if (r?.text != null && dev.pulse.core.data.NameIndex.knows(r.text!!)) emptyList() else l.categories,
            text = when {
                r?.text == null -> l.text
                // Dish words ("dosa") are firm; other leftovers beside a category are only a name hint.
                r.categories.isNotEmpty() && Lexicon.categories.none { it.second.text == r.text } -> l.text ?: r.text
                else -> r.text
            },
            near = r?.near ?: llmNear,
            time = r?.time ?: l.time,
            freshness = r?.freshness ?: l.freshness,
            priceLevelMax = r?.priceLevelMax ?: l.priceLevelMax,
            openNow = r?.openNow ?: l.openNow,
        )
        val task = if (rules.task == Task.UNKNOWN) llm.task else rules.task
        return rules.copy(task = task, query = merged, confidence = maxOf(rules.confidence, llm.confidence))
    }
}
