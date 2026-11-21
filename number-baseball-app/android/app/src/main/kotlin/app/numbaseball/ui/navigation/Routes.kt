package app.numbaseball.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation destinations. Arguments are primitives/strings so they survive process death.

@Serializable data object FirstRunRoute

@Serializable data object HomeRoute

@Serializable data object HowToPlayRoute

@Serializable data object ComputerSetupRoute

/** [level] is a ComputerLevel name. [practice] = waiting-room practice; switches to the real match when it starts. */
@Serializable data class ComputerMatchRoute(val digits: Int, val level: String, val practice: Boolean = false)

@Serializable data object QuickMatchRoute

/** Empty [joinCode] creates a new private room. */
@Serializable data class RoomLobbyRoute(val joinCode: String = "")

@Serializable data object OnlineMatchRoute

@Serializable data object TeamBoardRoute

/** See ResultsArgs for the meaning of each field. */
@Serializable data class ResultsRoute(
    val mode: String,
    val outcome: String,
    val reason: String,
    val rounds: Int,
    val opponentSecret: String,
    val digits: Int,
    val level: String = "",
    val teamMatch: Boolean = false,
    val scoreA: Int = 0,
    val scoreB: Int = 0,
)

@Serializable data object ChatRoute

@Serializable data object ProfileRoute

@Serializable data object SettingsRoute

/** [messageId] < 0 reports the player rather than a message. */
@Serializable data class ReportRoute(
    val playerId: String,
    val playerName: String,
    val roomId: String = "",
    val messageId: Long = -1,
)
