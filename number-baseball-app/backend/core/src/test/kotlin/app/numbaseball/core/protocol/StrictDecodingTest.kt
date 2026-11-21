package app.numbaseball.core.protocol

import app.numbaseball.core.text.CorpusTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** DECODE rows of the QA frame corpus must be rejected by [ProtocolJson]; TOLERATED rows must never crash. */
class StrictDecodingTest {
    private fun decode(text: String): ClientMessage = ProtocolJson.decodeFromString(ClientMessage.serializer(), text)

    @Test fun corpusDecodeRowsAreRejected() {
        val f = File(CorpusTestSupport.corpusDir() ?: return, "protocol_frames.tsv")
        if (!f.isFile) return
        val slipped = mutableListOf<String>()
        var header = true
        for (line in f.readLines().filter { it.isNotBlank() && !it.startsWith("#") }) {
            if (header) { header = false; continue }
            val c = line.split('\t')
            val frame = CorpusTestSupport.decode(c[4])
            val decoded = runCatching { decode(frame) }
            if (c[1] == "DECODE" && decoded.isSuccess) slipped += c[0]
            if (c[1] == "TOLERATED") decoded.exceptionOrNull()?.let { assertTrue(c[0], it is IllegalArgumentException) }
        }
        assertTrue("accepted: $slipped", slipped.isEmpty())
    }

    @Test fun strictLiterals() {
        assertEquals(Ping(), decode("""{"type":"ping","x":[1,-2.5e3,true,false,null,"s"]}"""))
        for (bad in listOf("""{"type":ping}""", """{"type":"ping","x":NaN}""", """{"type":"ping","x":01}""", """{"type":"ping","x":Infinity}""")) {
            assertTrue(bad, runCatching { decode(bad) }.isFailure)
        }
        val deep = """{"type":"ping","pad":""" + "[".repeat(900) + "]".repeat(900) + "}"
        assertEquals(Ping(), decode(deep))
    }

    @Test fun encodesTypeFirst() {
        val text = ProtocolJson.encodeToString(ClientMessage.serializer(), Guess("012", 2, 5))
        assertEquals("""{"type":"guess","guess":"012","round":2,"rid":5}""", text)
    }
}
