package app.numbaseball.server

import app.numbaseball.core.protocol.DuelPhase
import app.numbaseball.core.protocol.DuelResult
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.ErrorMessage
import app.numbaseball.core.protocol.Guess
import app.numbaseball.core.protocol.MatchResult
import app.numbaseball.core.protocol.RoomCreate
import app.numbaseball.core.protocol.RoomJoin
import app.numbaseball.core.protocol.RoomPhase
import app.numbaseball.core.protocol.RoomReset
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.protocol.RoomSettingsUpdate
import app.numbaseball.core.protocol.RoomView
import app.numbaseball.core.protocol.Score
import app.numbaseball.core.protocol.SetSecret
import app.numbaseball.core.protocol.Stats
import app.numbaseball.core.protocol.Team
import app.numbaseball.core.protocol.TeamSet
import app.numbaseball.core.protocol.Welcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamMatchFlowTest {
    private val wrong = "980"

    /** Host + 5 players in a 3v3 private room; returns players in join order once the match has started. */
    private suspend fun TestServer.threeVsThree(): Pair<List<Player>, Map<String, String>> {
        val names = listOf("Host", "Bo", "Cy", "Di", "Ed", "Fy")
        val players = names.map { connect(account(it)).also { p -> p.expect<Welcome>() } }
        val host = players[0]
        host.send(RoomCreate(RoomSettings(3, 2, 60)))
        val lobby = host.state { it.phase == RoomPhase.LOBBY }
        assertEquals(host.id, lobby.hostId)
        // The host widens the room to 3v3 while waiting.
        host.send(RoomSettingsUpdate(RoomSettings(3, 3, 60)))
        host.state { it.settings.teamSize == 3 }
        for (p in players.drop(1).take(4)) {
            p.send(RoomJoin(lobby.code!!.lowercase()))
            p.state { s -> s.players.any { it.id == p.id } }
        }
        // Team assignment: alternating; the host moves Ed (team A) to team B and back.
        val ed = players[4]
        val before = host.state { s -> s.players.size == 5 }
        assertEquals(Team.A, before.players.first { it.id == ed.id }.team)
        host.send(TeamSet(Team.B, ed.id))
        host.state { s -> s.players.first { it.id == ed.id }.team == Team.B }
        host.send(TeamSet(Team.A, ed.id))
        host.state { s -> s.players.first { it.id == ed.id }.team == Team.A }
        // A non-host cannot move others.
        players[1].send(TeamSet(Team.A, ed.id, rid = 4))
        assertEquals(ErrorCodes.NOT_HOST, players[1].expect<ErrorMessage> { it.rid == 4L }.code)

        players[5].send(RoomJoin(lobby.code!!))
        val started = host.state { it.phase == RoomPhase.PLAYING }
        assertEquals(3, started.match!!.duels.size)
        val secrets = listOf("012", "123", "234", "345", "456", "567")
        val secretOf = players.mapIndexed { i, p -> p.id to secrets[i] }.toMap()
        players.forEach { it.send(SetSecret(secretOf.getValue(it.id))) }
        players.forEach { p -> p.state { it.match?.myDuel?.phase == DuelPhase.GUESSING } }
        return players to secretOf
    }

    private fun RoomView.pair(i: Int) = match!!.duels[i].let { it.playerA to it.playerB }

    /** Duel i in round [round]: [winner] solves (null = both solve, i.e. a draw). */
    private suspend fun play(players: List<Player>, secretOf: Map<String, String>, room: RoomView, i: Int, winner: Team?, round: Int = 1) {
        val (a, b) = room.pair(i)
        val pa = players.first { it.id == a }
        val pb = players.first { it.id == b }
        pa.send(Guess(if (winner != Team.B) secretOf.getValue(b) else wrong, round))
        pb.send(Guess(if (winner != Team.A) secretOf.getValue(a) else wrong, round))
    }

    @Test fun threeVsThreeWithADuelDrawEndsInATeamDraw() = serverTest {
        val (players, secretOf) = threeVsThree()
        val host = players[0]
        val room = host.lastRoom!!
        play(players, secretOf, room, 0, Team.A)
        host.state { it.match?.duels?.get(0)?.result != null }
        play(players, secretOf, room, 1, null)
        val afterDraw = host.state { it.match?.duels?.get(1)?.result != null }
        assertEquals(DuelResult(null, EndReason.BOTH_SOLVED), afterDraw.match!!.duels[1].result)
        assertNull("1:0 with one duel open is not decided", afterDraw.match!!.result)
        play(players, secretOf, room, 2, Team.B)
        val end = host.state { it.phase == RoomPhase.FINISHED }
        assertEquals(MatchResult(null, decidedEarly = false), end.match!!.result)
        assertEquals(Score(1, 1), end.match!!.score)
        for (p in players) assertEquals(Stats(0, 0, 1), me(p.account.token).stats)
        // Everyone sees the secret of their own opponent now.
        val mine = end.match!!.myDuel!!
        assertEquals(secretOf.getValue(mine.opponentId), mine.opponentSecret)

        // The host starts another match with the same players.
        host.send(RoomReset())
        val again = host.state { it.match?.number == 2 }
        assertEquals(RoomPhase.PLAYING, again.phase)
    }

    @Test fun majorityDecidesEarlyAndCancelsTheLastDuel() = serverTest {
        val (players, secretOf) = threeVsThree()
        val host = players[0]
        val room = host.lastRoom!!
        play(players, secretOf, room, 0, Team.B)
        host.state { it.match?.duels?.get(0)?.result != null }
        play(players, secretOf, room, 1, Team.B)
        val end = host.state { it.phase == RoomPhase.FINISHED }
        assertEquals(MatchResult(Team.B, decidedEarly = true), end.match!!.result)
        assertEquals(DuelResult(null, EndReason.CANCELLED), end.match!!.duels[2].result)
        val (a2, b2) = room.pair(2)
        // Even the cancelled pair's secrets are revealed now, and the cancelled players still get the team result.
        val pa2 = players.first { it.id == a2 }
        assertEquals(secretOf.getValue(b2), pa2.state { it.phase == RoomPhase.FINISHED }.match!!.myDuel!!.opponentSecret)
        assertEquals(Stats(0, 1, 0), me(pa2.account.token).stats)
        assertTrue(me(players.first { it.id == b2 }.account.token).stats.wins == 1)
    }
}
