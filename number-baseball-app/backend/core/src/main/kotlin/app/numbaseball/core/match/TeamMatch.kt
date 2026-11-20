package app.numbaseball.core.match

import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.MatchResult
import app.numbaseball.core.protocol.Score
import app.numbaseball.core.protocol.Team
import kotlin.random.Random

/**
 * N paired duels between team A and team B (N = 1 is a plain 1:1 match).
 *
 * A duel win is one point; draws and cancelled duels give none. The match is decided as soon as the leader can no
 * longer be caught (`leader > other + unfinished`); the unfinished duels are then cancelled. Otherwise it ends when
 * every duel has ended, and equal points are a team draw.
 *
 * Pure state machine like [Duel]: pass `now` explicitly; [advance] fires timers of all duels in time order.
 */
class TeamMatch(
    val config: DuelConfig,
    /** (team A player id, team B player id) per duel. */
    val pairs: List<Pair<String, String>>,
    random: Random = Random.Default,
) {
    init {
        require(pairs.isNotEmpty()) { "no duels" }
        val ids = pairs.flatMap { listOf(it.first, it.second) }
        require(ids.toSet().size == ids.size) { "a player can only be in one duel" }
    }

    val duels: List<Duel> = pairs.map { Duel(config, random) }

    var result: MatchResult? = null
        private set
    var endedAt: Long? = null
        private set

    val isFinished: Boolean get() = result != null

    private val index: Map<String, Pair<Int, Team>> = buildMap {
        pairs.forEachIndexed { i, (a, b) -> put(a, i to Team.A); put(b, i to Team.B) }
    }

    /** Duel index and side of [playerId], or null if the player is not in this match. */
    fun locate(playerId: String): Pair<Int, Team>? = index[playerId]

    fun playerAt(duel: Int, team: Team): String = if (team == Team.A) pairs[duel].first else pairs[duel].second

    fun start(now: Long) = duels.forEach { it.start(now) }

    fun score(): Score = Score(
        a = duels.count { it.result?.winner == Team.A },
        b = duels.count { it.result?.winner == Team.B },
    )

    fun setSecret(playerId: String, secret: String, now: Long): ActionResult =
        act(playerId, now) { d, t -> d.setSecret(t, secret, now) }

    fun guess(playerId: String, guess: String, round: Int, now: Long): ActionResult =
        act(playerId, now) { d, t -> d.guess(t, guess, round, now) }

    fun forfeit(playerId: String, now: Long): ActionResult =
        act(playerId, now) { d, t -> d.forfeit(t, now) }

    fun disconnected(playerId: String, now: Long) {
        advance(now)
        val (i, t) = locate(playerId) ?: return
        if (!isFinished) duels[i].disconnected(t, now)
    }

    fun reconnected(playerId: String, now: Long) {
        advance(now)
        val (i, t) = locate(playerId) ?: return
        duels[i].reconnected(t, now)
    }

    fun nextWakeup(): Long? = if (isFinished) null else duels.mapNotNull { it.nextWakeup() }.minOrNull()

    /** Fires every timer up to [now] across all duels in time order. Returns true if anything changed. */
    fun advance(now: Long): Boolean {
        var changed = false
        while (!isFinished) {
            val t = nextWakeup() ?: break
            if (t > now) break
            for (d in duels) if (d.nextWakeup() == t) d.advance(t)
            decide(t)
            changed = true
        }
        return changed
    }

    private inline fun act(playerId: String, now: Long, block: (Duel, Team) -> ActionResult): ActionResult {
        advance(now)
        val (i, team) = locate(playerId) ?: return ActionResult.Rejected(ErrorCodes.NOT_IN_DUEL)
        if (isFinished && !duels[i].isFinished) return ActionResult.Rejected(ErrorCodes.WRONG_PHASE)
        val r = block(duels[i], team)
        decide(now)
        return r
    }

    private fun decide(now: Long) {
        if (isFinished) return
        val s = score()
        val open = duels.count { !it.isFinished }
        val winner = when {
            s.a > s.b + open -> Team.A
            s.b > s.a + open -> Team.B
            else -> null
        }
        if (open == 0) {
            result = MatchResult(winner, decidedEarly = false)
            endedAt = now
        } else if (winner != null) {
            duels.forEach { it.cancel(now) }
            result = MatchResult(winner, decidedEarly = true)
            endedAt = now
        }
    }
}
