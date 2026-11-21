package app.numbaseball.game.offline

import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.Team
import app.numbaseball.core.rules.Rules
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineGameTest {
    private val game = OfflineGame(Dispatchers.Unconfined, Random(7))

    @Test fun playsSimultaneousRoundsUntilSomeoneSolves() = runBlocking {
        val match = game.start(digits = 3, level = ComputerLevel.HARD, secret = "042")
        var snapshot = match.current()
        val candidates = mutableListOf<String>()
        // The player tries every number in order; the hard computer solves 3 digits long before that.
        for (a in '0'..'9') for (b in '0'..'9') for (c in '0'..'9') if (a != b && b != c && a != c) candidates += "$a$b$c"
        for (guess in candidates) {
            snapshot = match.play(guess)
            if (snapshot.result != null) break
        }
        val result = checkNotNull(snapshot.result)
        assertTrue(result.reason == EndReason.SOLVED || result.reason == EndReason.BOTH_SOLVED)
        assertEquals(snapshot.myRounds.size, snapshot.computerRounds.size)
        assertTrue(Rules.isValid(snapshot.computerSecret!!, 3))
        val last = snapshot.computerRounds.last()
        if (result.winner == Team.B) assertEquals(3, last.strikes)
    }

    @Test fun theComputerSecretStaysHiddenUntilTheEnd() = runBlocking {
        val match = game.start(digits = 4, level = ComputerLevel.EASY, secret = "0123")
        val first = match.play("4567")
        if (first.result == null) assertNull(first.computerSecret)
        val forfeited = match.forfeit()
        assertEquals(Team.B, forfeited.result?.winner)
        assertNotNull(forfeited.computerSecret)
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsAnInvalidSecret() {
        runBlocking { game.start(digits = 3, level = ComputerLevel.NORMAL, secret = "112") }
    }
}
