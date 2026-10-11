package dev.pulse.core.data

import dev.pulse.core.ai.Lexicon

/** The specificity picker's options. Shared so the picker, parser and analytics agree. */
data class CategoryChoice(val id: String, val label: String, val categories: List<String>)

object CategoryChoices {
    val Food = CategoryChoice("food", "Food", listOf("food"))
    val Fashion = CategoryChoice("fashion", "Fashion", listOf("fashion"))
    val Fitness = CategoryChoice("fitness", "Fitness", listOf("fitness"))
    val Events = CategoryChoice("events", "Events", Lexicon.EVENTS)
    val Shopping = CategoryChoice("shopping", "Shopping", listOf("shopping"))

    /** In display order; "Other" (free text) is rendered after these by the picker. */
    val All = listOf(Food, Fashion, Fitness, Events, Shopping)
}
