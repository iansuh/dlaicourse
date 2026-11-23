package app.numbaseball.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation destinations. Arguments are primitives/strings so they survive process death.

@Serializable data object FirstRunRoute

@Serializable data object HomeRoute

@Serializable data object HowToPlayRoute

/** [level] is a ComputerLevel name. [practice] = waiting-room practice; switches to the real match when it starts. */
@Serializable data class ComputerMatchRoute(val digits: Int, val level: String, val practice: Boolean = false, val roundSeconds: Int = 600)

@Serializable data object QuickMatchRoute

@Serializable data object JoinRoute

/** [digits] > 0 creates a new private room with these settings; a [joinCode] joins one; neither = the current room. */
@Serializable data class RoomLobbyRoute(val joinCode: String = "", val digits: Int = 0, val teamSize: Int = 0, val roundSeconds: Int = 0)

@Serializable data object OnlineMatchRoute

/** [mode] is a BoardMode name. */
@Serializable data class TeamBoardRoute(val mode: String)

/** A finished duel (06). Strikes per round are comma separated, `-` for a missed round. */
@Serializable data class ResultsRoute(
    val mode: String,
    val outcome: String,
    val reason: String,
    val rounds: Int,
    val opponentSecret: String,
    val digits: Int,
    val level: String = "",
    val mySecret: String = "",
    val opponentName: String = "",
    val myStrikes: String = "",
    val opponentStrikes: String = "",
    val durationSeconds: Int = 0,
    val roundSeconds: Int = 0,
)

@Serializable data object ChatRoute

/** Player card (12 §2) as a sheet over the current screen. [team] is a Team name or empty. */
@Serializable data class PlayerCardRoute(
    val playerId: String,
    val nickname: String,
    val tag: String,
    val team: String = "",
    val roomId: String = "",
    val quick: Boolean = false,
    val self: Boolean = false,
)

/** Report sheet (12 §3). [messageId] < 0 reports the player rather than a message. */
@Serializable data class ReportRoute(
    val playerId: String,
    val nickname: String,
    val tag: String,
    val roomId: String = "",
    val messageId: Long = -1,
    val messageText: String = "",
)

@Serializable data object ProfileRoute

@Serializable data object SettingsRoute

@Serializable data object BlockedPlayersRoute

@Serializable data object LicencesRoute

/** Index into LICENCES. */
@Serializable data class LicenceTextRoute(val index: Int)

/** [document] is a LegalDocument name: the bundled terms or privacy policy. */
@Serializable data class LegalRoute(val document: String)
