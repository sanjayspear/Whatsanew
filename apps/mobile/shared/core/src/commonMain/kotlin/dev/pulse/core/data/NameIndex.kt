package dev.pulse.core.data

/**
 * "Is this the name of a real place nearby?" Lets the AI merge keep a name search ("microsoft",
 * "wellsfargo") as a name search instead of letting the model turn it into a category. Process-wide
 * like [SearchDefaults]: the AI ladder is built per platform; the app installs the lookup at start.
 */
object NameIndex {
    var knows: (String) -> Boolean = { false }
}
