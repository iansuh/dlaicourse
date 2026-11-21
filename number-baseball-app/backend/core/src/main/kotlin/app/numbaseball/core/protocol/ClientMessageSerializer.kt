package app.numbaseball.core.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.reflect.KClass

/**
 * Strict codec for [ClientMessage]: `{"type": "<name>", ...fields}`.
 *
 * kotlinx.serialization's tree reader accepts some non-JSON literals (`{"type":ping}`, `NaN`) even when not lenient;
 * this serializer rejects any unquoted literal other than true / false / null / an RFC 8259 number, requires "type" to be
 * a JSON string naming a known message, and then decodes the message fields (unknown keys are ignored).
 */
object ClientMessageSerializer : KSerializer<ClientMessage> {
    private class Kind<T : ClientMessage>(val type: String, val cls: KClass<T>, val serializer: KSerializer<T>)

    private val kinds: List<Kind<*>> = listOf(
        Kind("ping", Ping::class, Ping.serializer()),
        Kind("resync", Resync::class, Resync.serializer()),
        Kind("queue_join", QueueJoin::class, QueueJoin.serializer()),
        Kind("queue_leave", QueueLeave::class, QueueLeave.serializer()),
        Kind("room_create", RoomCreate::class, RoomCreate.serializer()),
        Kind("room_join", RoomJoin::class, RoomJoin.serializer()),
        Kind("room_leave", RoomLeave::class, RoomLeave.serializer()),
        Kind("room_settings", RoomSettingsUpdate::class, RoomSettingsUpdate.serializer()),
        Kind("team_set", TeamSet::class, TeamSet.serializer()),
        Kind("room_reset", RoomReset::class, RoomReset.serializer()),
        Kind("secret", SetSecret::class, SetSecret.serializer()),
        Kind("guess", Guess::class, Guess.serializer()),
        Kind("forfeit", Forfeit::class, Forfeit.serializer()),
        Kind("chat", Chat::class, Chat.serializer()),
        Kind("phrase", Phrase::class, Phrase.serializer()),
    )
    private val byType: Map<String, Kind<*>> = kinds.associateBy { it.type }
    private val byClass: Map<KClass<*>, Kind<*>> = kinds.associateBy { it.cls }

    /** All message type names, for documentation and tests. */
    val types: List<String> get() = kinds.map { it.type }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("app.numbaseball.core.protocol.ClientMessage")

    private val NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

    override fun deserialize(decoder: Decoder): ClientMessage {
        val input = decoder as? JsonDecoder ?: throw SerializationException("ClientMessage can only be decoded from JSON")
        val obj = input.decodeJsonElement() as? JsonObject ?: throw SerializationException("a JSON object is required")
        requireStrict(obj)
        val typeElement = obj["type"] as? JsonPrimitive
        if (typeElement == null || !typeElement.isString) throw SerializationException("\"type\" must be a string")
        val kind = byType[typeElement.content] ?: throw SerializationException("unknown message type")
        return input.json.decodeFromJsonElement(kind.serializer, JsonObject(obj - "type"))
    }

    override fun serialize(encoder: Encoder, value: ClientMessage) {
        val output = encoder as? JsonEncoder ?: throw SerializationException("ClientMessage can only be encoded to JSON")
        @Suppress("UNCHECKED_CAST")
        val kind = byClass[value::class] as? Kind<ClientMessage> ?: throw SerializationException("unregistered message")
        val fields = output.json.encodeToJsonElement(kind.serializer, value).jsonObject
        output.encodeJsonElement(JsonObject(linkedMapOf<String, JsonElement>("type" to JsonPrimitive(kind.type)) + fields))
    }

    /** Iterative walk (deeply nested input must not overflow the stack). */
    private fun requireStrict(root: JsonElement) {
        val stack = ArrayDeque<JsonElement>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            when (val e = stack.removeLast()) {
                is JsonObject -> e.values.forEach { stack.addLast(it) }
                is JsonArray -> e.forEach { stack.addLast(it) }
                is JsonNull -> Unit
                is JsonPrimitive -> if (!e.isString) {
                    val c = e.content
                    if (c != "true" && c != "false" && c != "null" && !NUMBER.matches(c)) {
                        throw SerializationException("invalid JSON literal")
                    }
                }
            }
        }
    }
}
