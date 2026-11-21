package app.numbaseball.server.qa

import app.numbaseball.core.protocol.ProtocolJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs now (no server needed): the server-side copy of the corpus loader and the size rows are exact. */
class ServerCorpusSmokeQaTest {

    @Test fun framesAndRequestsLoadAndDecode() {
        val frames = QaServerCorpus.load("protocol_frames.tsv")
        assertTrue(frames.size >= 100)
        for (row in frames) QaServerCorpus.decode(row.getValue("frame"))
        val rest = QaServerCorpus.load("rest_requests.tsv")
        assertTrue(rest.size >= 40)
        for (row in rest) if (row.getValue("body") != "-") QaServerCorpus.decode(row.getValue("body"))
    }

    @Test fun sizeRowsHitTheProtocolLimitsExactly() {
        val bytes = QaServerCorpus.load("protocol_frames.tsv").associate {
            it.getValue("id") to QaServerCorpus.decode(it.getValue("frame")).toByteArray(Charsets.UTF_8).size
        }
        assertEquals(2048, bytes["Z-001"]) // protocol 12: 2048 bytes processed
        assertEquals(2049, bytes["Z-002"]) // one byte more: message_too_large
        assertEquals(2049, bytes["Z-003"]) // multi-byte characters: limit is bytes
        assertTrue(bytes.getValue("Z-006") > 16 * 1024) // frame limit: close 1009
        assertTrue(bytes.getValue("T-014") < 2048) // deep nesting stays under the message cap
    }

    @Test fun serverRowsAreValidJsonSoTheServerSeesTheSpecificProblem() {
        for (row in QaServerCorpus.load("protocol_frames.tsv").filter { it["layer"] == "SERVER" }) {
            val frame = QaServerCorpus.decode(row.getValue("frame"))
            assertTrue("${row["id"]} must be valid JSON", runCatching { ProtocolJson.parseToJsonElement(frame) }.isSuccess)
        }
    }
}
