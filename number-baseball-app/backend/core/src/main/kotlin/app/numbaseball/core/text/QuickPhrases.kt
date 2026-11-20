package app.numbaseball.core.text

/**
 * Preset chat phrases. Only the ids travel over the wire; the client shows localized text.
 * Ids are stable: never rename or reuse one, only add new ones at the end.
 */
object QuickPhrases {
    val ALL: List<String> = listOf(
        "hello",        // Hi!
        "good_luck",    // Good luck!
        "have_fun",     // Have fun!
        "nice_guess",   // Nice guess!
        "close_one",    // So close!
        "thinking",     // Let me think...
        "oops",         // Oops!
        "well_played",  // Well played!
        "good_game",    // Good game!
        "thanks",       // Thanks!
        "rematch",      // Rematch?
        "bye",          // Bye!
    )
    private val SET = ALL.toHashSet()

    fun isValid(id: String): Boolean = id in SET
}
