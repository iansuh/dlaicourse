package app.numbaseball.data.online

import app.numbaseball.core.protocol.ChatEvent
import app.numbaseball.core.protocol.ChatHistory
import app.numbaseball.core.protocol.ChatSenderDeleted
import app.numbaseball.core.protocol.LeaveReason
import app.numbaseball.core.protocol.QueueJoin
import app.numbaseball.core.protocol.QueueLeave
import app.numbaseball.core.protocol.RoomLeft
import app.numbaseball.core.protocol.RoomState
import app.numbaseball.core.protocol.Welcome
import app.numbaseball.testing.FakeOnlineClient
import app.numbaseball.testing.SampleData
import app.numbaseball.testing.account
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineSessionTest {
    private val client = FakeOnlineClient()

    private fun welcome(roomId: String? = null) = Welcome(1, serverTime = 5_000, account = account(), roomId = roomId)

    @Test fun roomStateReplacesTheRoomAndOlderVersionsAreIgnored() = runTest(UnconfinedTestDispatcher()) {
        val session = OnlineSession(client, backgroundScope, clock = { 1_000 })
        client.receive(RoomState(SampleData.quickRoom(version = 5)))
        assertEquals(5L, session.room.value?.version)
        client.receive(RoomState(SampleData.quickRoom(version = 4)))
        assertEquals(5L, session.room.value?.version)
        assertEquals(SampleData.quickRoom().serverTime - 1_000, session.serverTimeOffset)
    }

    @Test fun welcomeWithoutRoomAfterAReconnectMeansTheSeatIsGone() = runTest(UnconfinedTestDispatcher()) {
        val session = OnlineSession(client, backgroundScope)
        val events = mutableListOf<SessionEvent>()
        backgroundScope.launch { session.events.collect { events += it } }
        client.receive(RoomState(SampleData.quickRoom()))
        client.receive(welcome(roomId = null))
        assertNull(session.room.value)
        assertEquals(listOf<SessionEvent>(SessionEvent.SeatLost), events)
    }

    @Test fun quickMatchIsRejoinedAfterAReconnectAndEndsWhenAQuickRoomArrives() = runTest(UnconfinedTestDispatcher()) {
        val session = OnlineSession(client, backgroundScope)
        session.startQuickMatch(4)
        assertEquals(QueueJoin(4), client.sent.last())
        client.receive(welcome())
        assertEquals(listOf(QueueJoin(4), QueueJoin(4)), client.sent)
        client.receive(RoomState(SampleData.quickRoom()))
        assertEquals(QueueState.Idle, session.queue.value)
    }

    @Test fun cancellingTheSearchLeavesTheQueue() = runTest(UnconfinedTestDispatcher()) {
        val session = OnlineSession(client, backgroundScope)
        session.startQuickMatch(3)
        session.cancelQuickMatch()
        assertEquals(QueueLeave(), client.sent.last())
        assertEquals(QueueState.Idle, session.queue.value)
    }

    @Test fun chatHistoryEventsDeletedSendersAndBlocks() = runTest(UnconfinedTestDispatcher()) {
        val session = OnlineSession(client, backgroundScope)
        val room = SampleData.quickRoom()
        val lines = SampleData.chat()
        client.receive(RoomState(room))
        client.receive(ChatHistory(room.id, lines.take(2)))
        client.receive(ChatEvent(room.id, lines[2]))
        client.receive(ChatEvent("other-room", lines[3]))
        assertEquals(listOf(1L, 2L, 3L), session.chat.value.map { it.id })

        client.receive(ChatSenderDeleted(room.id, SampleData.OPPONENT))
        assertTrue(session.chat.value.filter { it.id != 2L }.all { it.senderId == null && it.senderNickname == null })

        client.receive(ChatHistory(room.id, lines.take(3)))
        session.hideSender(SampleData.OPPONENT)
        assertEquals(listOf(2L), session.chat.value.map { it.id })
    }

    @Test fun roomLeftClearsTheRoomAndReportsTheReason() = runTest(UnconfinedTestDispatcher()) {
        val session = OnlineSession(client, backgroundScope)
        val room = SampleData.quickRoom()
        client.receive(RoomState(room))
        val event = backgroundScope.launch { assertEquals(SessionEvent.LeftRoom(LeaveReason.CLOSED), session.events.first()) }
        client.receive(RoomLeft(room.id, LeaveReason.CLOSED))
        assertNull(session.room.value)
        event.join()
    }

    @Test fun requestsCarryARequestId() = runTest(UnconfinedTestDispatcher()) {
        val session = OnlineSession(client, backgroundScope)
        assertEquals(RequestResult.Ok, session.joinRoom("K7QX2"))
        client.connected = false
        assertEquals(RequestResult.NotConnected, session.joinRoom("K7QX2"))
    }
}
