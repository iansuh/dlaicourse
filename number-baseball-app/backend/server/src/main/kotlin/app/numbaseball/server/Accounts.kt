package app.numbaseball.server

import app.numbaseball.core.protocol.AccountDto
import app.numbaseball.core.protocol.AdminAccount
import app.numbaseball.core.protocol.AdminReport
import app.numbaseball.core.protocol.BlockedPlayer
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.ProtocolJson
import app.numbaseball.core.protocol.ReportReason
import app.numbaseball.core.protocol.ReportSnapshot
import app.numbaseball.core.protocol.Stats
import app.numbaseball.core.text.Nicknames
import io.ktor.http.HttpStatusCode
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.ResultSet
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** An error with an HTTP status and a protocol error code. */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
    val retryAfterSeconds: Long? = null,
) : RuntimeException(message)

data class Account(
    val id: String,
    val nickname: String,
    val tag: Int,
    val createdAt: Long,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val termsVersion: String?,
    val termsAcceptedAt: Long?,
    val ageConfirmedAt: Long?,
    val bannedAt: Long?,
    val banReason: String?,
    val chatRestrictedUntil: Long?,
    val chatRestrictReason: String?,
    val isBot: Boolean,
) {
    val tagText: String get() = Nicknames.formatTag(tag)
    val display: String get() = Nicknames.display(nickname, tag)
    val banned: Boolean get() = bannedAt != null
}

enum class MatchOutcome { WIN, LOSS, DRAW }

enum class CodeKind(val key: String, val ttlMillis: Long) {
    TRANSFER("transfer", 10 * 60 * 1000L),
}

/**
 * Accounts, tokens, transfer codes, blocks, terms acceptance, moderation and reports.
 * Thread-safe. Keeps a small in-memory cache so the real-time hub can check blocks, terms and chat restrictions
 * without touching the database.
 */
class AccountService(
    private val db: Database,
    private val clock: Clock,
    val currentTermsVersion: String,
    private val random: SecureRandom = SecureRandom(),
) {
    private val cache = ConcurrentHashMap<String, Account>()
    private val blocksCache = ConcurrentHashMap<String, Set<String>>()

    companion object {
        const val TAG_MAX = 9999
        const val REPORT_RETENTION_MILLIS = 180L * 24 * 3600 * 1000
        const val AUTO_RESTRICT_REPORTERS = 3
        const val AUTO_RESTRICT_WINDOW_MILLIS = 24L * 3600 * 1000
        const val AUTO_RESTRICT_MILLIS = 24L * 3600 * 1000
        const val CODE_ATTEMPTS = 5
        private const val ID_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"

        fun sha256(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

        /** Parses `Nickname#1234` (nickname normalized like at sign-up). */
        fun parseHandle(handle: String): Pair<String, Int>? {
            val h = handle.trim()
            val i = h.lastIndexOf('#')
            if (i <= 0 || h.length - i - 1 != 4) return null
            val tag = h.substring(i + 1).takeIf { t -> t.all { it in '0'..'9' } }?.toInt() ?: return null
            if (tag !in 1..TAG_MAX) return null
            return Nicknames.normalize(h.substring(0, i)) to tag
        }

        /** Code input: case, spaces and dashes are ignored. */
        fun normalizeCode(input: String): String =
            input.filter { it != ' ' && it != '-' }.map { if (it in 'a'..'z') it - 32 else it }.joinToString("")
    }

    // ------------------------------------------------------------------ helpers

    private fun randomString(alphabet: String, n: Int) = buildString(n) { repeat(n) { append(alphabet[random.nextInt(alphabet.length)]) } }

    private fun newToken(): String {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun map(rs: ResultSet) = Account(
        id = rs.getString("id"),
        nickname = rs.getString("nickname"),
        tag = rs.getInt("tag"),
        createdAt = rs.getLong("created_at"),
        wins = rs.getInt("wins"),
        losses = rs.getInt("losses"),
        draws = rs.getInt("draws"),
        termsVersion = rs.getString("terms_version"),
        termsAcceptedAt = rs.longOrNull("terms_accepted_at"),
        ageConfirmedAt = rs.longOrNull("age_confirmed_at"),
        bannedAt = rs.longOrNull("banned_at"),
        banReason = rs.getString("ban_reason"),
        chatRestrictedUntil = rs.longOrNull("chat_restricted_until"),
        chatRestrictReason = rs.getString("chat_restrict_reason"),
        isBot = rs.getInt("is_bot") != 0,
    )

    private fun load(c: Connection, id: String): Account? =
        c.query("SELECT * FROM accounts WHERE id = ?", id) { map(it) }.firstOrNull()?.also { cache[id] = it }

    private fun reload(id: String): Account? = db.read { load(it, id) }.also { if (it == null) cache.remove(id) }

    /** Cached account (loaded on first use). */
    fun get(id: String): Account? = cache[id] ?: reload(id)

    fun toDto(a: Account): AccountDto = AccountDto(
        id = a.id,
        nickname = a.nickname,
        tag = a.tagText,
        stats = Stats(a.wins, a.losses, a.draws),
        createdAt = a.createdAt,
        termsVersion = a.termsVersion,
        termsAcceptedAt = a.termsAcceptedAt,
        ageConfirmedAt = a.ageConfirmedAt,
        chatRestrictedUntil = a.chatRestrictedUntil?.takeIf { it > clock.now() },
    )

    fun hasAcceptedTerms(id: String): Boolean = get(id)?.let { it.isBot || it.termsVersion == currentTermsVersion } ?: false

    /** Chat restricted until this time, or null. */
    fun chatRestrictedUntil(id: String): Long? = get(id)?.chatRestrictedUntil?.takeIf { it > clock.now() }

    private fun validNickname(raw: String): String = when (val r = Nicknames.validate(raw)) {
        is Nicknames.Result.Ok -> r.nickname
        is Nicknames.Result.Invalid -> throw ApiException(HttpStatusCode.BadRequest, r.code, "nickname: ${r.reason}")
    }

    private fun freeTag(c: Connection, nickKey: String, preferred: Int? = null, owner: String? = null): Int {
        fun taken(tag: Int) = c.query("SELECT id FROM accounts WHERE nick_key = ? AND tag = ?", nickKey, tag) { it.getString(1) }
            .any { it != owner }
        if (preferred != null && !taken(preferred)) return preferred
        repeat(40) {
            val t = 1 + random.nextInt(TAG_MAX)
            if (!taken(t)) return t
        }
        val used = c.query("SELECT tag FROM accounts WHERE nick_key = ?", nickKey) { it.getInt(1) }.toHashSet()
        val free = (1..TAG_MAX).filter { it !in used }
        if (free.isEmpty()) throw ApiException(HttpStatusCode.Conflict, ErrorCodes.NICKNAME_UNAVAILABLE, "every tag of this nickname is taken")
        return free[random.nextInt(free.size)]
    }

    // ------------------------------------------------------------------ accounts

    fun create(rawNickname: String): Pair<String, Account> {
        val nickname = validNickname(rawNickname)
        val now = clock.now()
        val token = newToken()
        val id = db.tx { c ->
            val tag = freeTag(c, Nicknames.key(nickname))
            var id: String
            do { id = randomString(ID_ALPHABET, 12) } while (c.query("SELECT 1 FROM accounts WHERE id = ?", id) { 1 }.isNotEmpty())
            c.update(
                "INSERT INTO accounts (id, nickname, nick_key, tag, created_at) VALUES (?, ?, ?, ?, ?)",
                id, nickname, Nicknames.key(nickname), tag, now,
            )
            c.update("INSERT INTO tokens (hash, account_id, created_at) VALUES (?, ?, ?)", sha256(token), id, now)
            id
        }
        return token to get(id)!!
    }

    /** A bot account (no token) for the review room. Created once, reused afterwards. */
    fun ensureBot(id: String, nickname: String): Account {
        get(id)?.let { return it }
        db.tx { c ->
            val tag = freeTag(c, Nicknames.key(nickname), preferred = 1)
            c.update(
                "INSERT OR IGNORE INTO accounts (id, nickname, nick_key, tag, created_at, terms_version, is_bot) VALUES (?, ?, ?, ?, ?, ?, 1)",
                id, nickname, Nicknames.key(nickname), tag, clock.now(), currentTermsVersion,
            )
        }
        return reload(id)!!
    }

    /** Resolves a bearer token to its account. */
    fun authenticate(token: String?): Account? {
        if (token.isNullOrBlank() || token.length > 200) return null
        val id = db.read { c -> c.query("SELECT account_id FROM tokens WHERE hash = ?", sha256(token)) { it.getString(1) }.firstOrNull() }
            ?: return null
        return get(id)
    }

    fun rename(id: String, rawNickname: String): Account {
        val nickname = validNickname(rawNickname)
        val current = get(id) ?: throw ApiException(HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED, "unknown account")
        db.tx { c ->
            val key = Nicknames.key(nickname)
            val tag = freeTag(c, key, preferred = current.tag, owner = id)
            c.update("UPDATE accounts SET nickname = ?, nick_key = ?, tag = ? WHERE id = ?", nickname, key, tag, id)
        }
        return reload(id)!!
    }

    /** Stores the accepted terms version together with the minimum-age confirmation (both required). */
    fun acceptTerms(id: String, version: String, ageConfirmed: Boolean): Account {
        if (!ageConfirmed) {
            throw ApiException(HttpStatusCode.BadRequest, ErrorCodes.AGE_CONFIRMATION_REQUIRED, "the minimum age must be confirmed")
        }
        if (version != currentTermsVersion) {
            throw ApiException(HttpStatusCode.Conflict, ErrorCodes.TERMS_OUTDATED, "current terms version is $currentTermsVersion")
        }
        val now = clock.now()
        db.tx { c ->
            c.update("UPDATE accounts SET terms_version = ?, terms_accepted_at = ?, age_confirmed_at = ? WHERE id = ?", version, now, now, id)
        }
        return reload(id)!!
    }

    fun recordResults(results: Map<String, MatchOutcome>) {
        if (results.isEmpty()) return
        db.tx { c ->
            for ((id, outcome) in results) {
                val col = when (outcome) { MatchOutcome.WIN -> "wins"; MatchOutcome.LOSS -> "losses"; MatchOutcome.DRAW -> "draws" }
                c.update("UPDATE accounts SET $col = $col + 1 WHERE id = ? AND is_bot = 0", id)
            }
        }
        results.keys.forEach { reload(it) }
    }

    /**
     * Deletes the account and everything tied to it: tokens, codes, blocks (both ways), reports about it. Reports it filed
     * lose the reporter id, and its messages inside other reports' snapshots become "deleted player".
     */
    fun delete(id: String) {
        db.tx { c ->
            c.update("DELETE FROM reports WHERE target_id = ?", id)
            c.update("UPDATE reports SET reporter_id = NULL WHERE reporter_id = ?", id)
            val withMessages = c.query("SELECT id, snapshot FROM reports WHERE snapshot LIKE ?", "%\"$id\"%") {
                it.getLong(1) to it.getString(2)
            }
            for ((reportId, json) in withMessages) {
                val snap = runCatching { ProtocolJson.decodeFromString(ReportSnapshot.serializer(), json) }.getOrNull() ?: continue
                val anon = { m: app.numbaseball.core.protocol.ChatMessage ->
                    if (m.senderId == id) m.copy(senderId = null, senderNickname = null, senderTag = null) else m
                }
                val cleaned = ReportSnapshot(snap.message?.let(anon), snap.recent.map(anon))
                c.update("UPDATE reports SET snapshot = ? WHERE id = ?", ProtocolJson.encodeToString(ReportSnapshot.serializer(), cleaned), reportId)
            }
            c.update("DELETE FROM accounts WHERE id = ?", id) // tokens, codes, blocks cascade
        }
        cache.remove(id)
        blocksCache.remove(id)
        blocksCache.replaceAll { _, set -> if (id in set) set - id else set }
    }

    // ------------------------------------------------------------------ one-time codes

    /** Issues a new 12-character code (replacing earlier ones of the same kind) and returns it with its expiry. */
    fun issueCode(id: String, kind: CodeKind): Pair<String, Long> {
        val expires = clock.now() + kind.ttlMillis
        val code = db.tx { c ->
            c.update("DELETE FROM codes WHERE kind = ? AND account_id = ?", kind.key, id)
            var lookup: String
            do { lookup = randomString(RoomCodes.ALPHABET, 4) } while (
                c.query("SELECT 1 FROM codes WHERE kind = ? AND lookup = ?", kind.key, lookup) { 1 }.isNotEmpty()
            )
            val secret = randomString(RoomCodes.ALPHABET, 8)
            c.update(
                "INSERT INTO codes (kind, lookup, secret_hash, account_id, expires_at) VALUES (?, ?, ?, ?, ?)",
                kind.key, lookup, sha256(secret), id, expires,
            )
            lookup + secret
        }
        return "${code.substring(0, 4)}-${code.substring(4, 8)}-${code.substring(8)}" to expires
    }

    /** Checks and consumes a code. Returns the account id or null (wrong, expired, used up). */
    fun consumeCode(input: String, kind: CodeKind): String? {
        val code = normalizeCode(input)
        if (code.length != 12 || code.any { it !in RoomCodes.ALPHABET }) return null
        val lookup = code.substring(0, 4)
        val secret = code.substring(4)
        return db.tx { c ->
            val row = c.query(
                "SELECT secret_hash, account_id, expires_at, attempts FROM codes WHERE kind = ? AND lookup = ?", kind.key, lookup,
            ) { Triple(it.getString(1), it.getString(2), it.getLong(3)) to it.getInt(4) }.firstOrNull() ?: return@tx null
            val (hash, accountId, expires) = row.first
            when {
                expires <= clock.now() -> {
                    c.update("DELETE FROM codes WHERE kind = ? AND lookup = ?", kind.key, lookup)
                    null
                }
                !MessageDigest.isEqual(hash.toByteArray(), sha256(secret).toByteArray()) -> {
                    if (row.second + 1 >= CODE_ATTEMPTS) c.update("DELETE FROM codes WHERE kind = ? AND lookup = ?", kind.key, lookup)
                    else c.update("UPDATE codes SET attempts = attempts + 1 WHERE kind = ? AND lookup = ?", kind.key, lookup)
                    null
                }
                else -> {
                    c.update("DELETE FROM codes WHERE kind = ? AND lookup = ?", kind.key, lookup)
                    accountId
                }
            }
        }
    }

    /** Redeems a transfer code: a new token is issued and every older token of the account is revoked. */
    fun redeemTransfer(input: String): Pair<String, Account>? {
        val id = consumeCode(input, CodeKind.TRANSFER) ?: return null
        val token = newToken()
        db.tx { c ->
            c.update("DELETE FROM tokens WHERE account_id = ?", id)
            c.update("INSERT INTO tokens (hash, account_id, created_at) VALUES (?, ?, ?)", sha256(token), id, clock.now())
        }
        return token to (authenticate(token) ?: return null)
    }

    // ------------------------------------------------------------------ lookup by handle (admin, email deletion requests)

    fun findByHandle(handle: String): Account? {
        val (nickname, tag) = parseHandle(handle) ?: return null
        val id = db.read { c ->
            c.query("SELECT id FROM accounts WHERE nick_key = ? AND tag = ? AND is_bot = 0", Nicknames.key(nickname), tag) { it.getString(1) }
                .firstOrNull()
        } ?: return null
        return get(id)
    }

    // ------------------------------------------------------------------ blocks

    fun blockedBy(id: String): Set<String> = blocksCache[id] ?: db.read { c ->
        c.query("SELECT blocked FROM blocks WHERE blocker = ?", id) { it.getString(1) }.toSet()
    }.also { blocksCache[id] = it }

    fun isBlockedEitherWay(a: String, b: String): Boolean = b in blockedBy(a) || a in blockedBy(b)

    fun block(blocker: String, blocked: String) {
        if (blocker == blocked) throw ApiException(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, "you cannot block yourself")
        if (get(blocked) == null) throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "no such player")
        db.tx { c -> c.update("INSERT OR IGNORE INTO blocks (blocker, blocked, created_at) VALUES (?, ?, ?)", blocker, blocked, clock.now()) }
        blocksCache[blocker] = blockedBy(blocker) + blocked
    }

    fun unblock(blocker: String, blocked: String) {
        db.tx { c -> c.update("DELETE FROM blocks WHERE blocker = ? AND blocked = ?", blocker, blocked) }
        blocksCache[blocker] = blockedBy(blocker) - blocked
    }

    fun listBlocks(id: String): List<BlockedPlayer> = blockedBy(id).mapNotNull { get(it) }.sortedBy { it.nickname }
        .map { BlockedPlayer(it.id, it.nickname, it.tagText) }

    // ------------------------------------------------------------------ moderation

    fun setBan(id: String, reason: String?) {
        if (get(id) == null) throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "no such account")
        db.tx { c ->
            if (reason == null) c.update("UPDATE accounts SET banned_at = NULL, ban_reason = NULL WHERE id = ?", id)
            else c.update("UPDATE accounts SET banned_at = ?, ban_reason = ? WHERE id = ?", clock.now(), reason, id)
        }
        reload(id)
    }

    fun restrictChat(id: String, until: Long?, reason: String?) {
        if (get(id) == null) throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "no such account")
        db.tx { c -> c.update("UPDATE accounts SET chat_restricted_until = ?, chat_restrict_reason = ? WHERE id = ?", until, reason, id) }
        reload(id)
    }

    fun adminView(a: Account) = AdminAccount(toDto(a), a.bannedAt, a.banReason, a.chatRestrictReason)

    // ------------------------------------------------------------------ reports

    /**
     * Stores a report. When [AUTO_RESTRICT_REPORTERS] different players reported the same target within 24 hours, the
     * target's chat is restricted for 24 hours (pending review). Returns the report id and whether that happened.
     */
    fun createReport(
        reporter: String, target: Account, reason: ReportReason, note: String?, roomId: String?, messageId: Long?,
        snapshot: ReportSnapshot?,
    ): Pair<Long, Boolean> {
        val now = clock.now()
        var restricted = false
        val reportId = db.tx { c ->
            c.update(
                """INSERT INTO reports (created_at, reporter_id, target_id, target_display, reason, note, room_id, message_id, snapshot)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                now, reporter, target.id, target.display, ProtocolJson.encodeToJsonElement(ReportReason.serializer(), reason).toString().trim('"'),
                note, roomId, messageId, snapshot?.let { ProtocolJson.encodeToString(ReportSnapshot.serializer(), it) },
            )
            val id = c.query("SELECT last_insert_rowid()") { it.getLong(1) }.first()
            val reporters = c.query(
                "SELECT COUNT(DISTINCT reporter_id) FROM reports WHERE target_id = ? AND created_at >= ? AND reporter_id IS NOT NULL",
                target.id, now - AUTO_RESTRICT_WINDOW_MILLIS,
            ) { it.getInt(1) }.first()
            val current = c.query("SELECT chat_restricted_until FROM accounts WHERE id = ?", target.id) { it.longOrNull("chat_restricted_until") }
                .firstOrNull()
            if (reporters >= AUTO_RESTRICT_REPORTERS && (current == null || current < now + AUTO_RESTRICT_MILLIS)) {
                c.update(
                    "UPDATE accounts SET chat_restricted_until = ?, chat_restrict_reason = ? WHERE id = ?",
                    now + AUTO_RESTRICT_MILLIS, "automatic: reports from $reporters players within 24 h (pending review)", target.id,
                )
                restricted = true
            }
            id
        }
        if (restricted) reload(target.id)
        return reportId to restricted
    }

    fun listReports(status: String): List<AdminReport> = db.read { c ->
        val where = when (status) { "open" -> "WHERE status = 'open'"; "resolved" -> "WHERE status = 'resolved'"; else -> "" }
        c.query("SELECT * FROM reports $where ORDER BY id DESC LIMIT 500") { rs ->
            AdminReport(
                id = rs.getLong("id"),
                createdAt = rs.getLong("created_at"),
                reporterId = rs.getString("reporter_id"),
                targetId = rs.getString("target_id"),
                targetDisplay = rs.getString("target_display"),
                reason = ProtocolJson.decodeFromString(ReportReason.serializer(), "\"" + rs.getString("reason") + "\""),
                note = rs.getString("note"),
                roomId = rs.getString("room_id"),
                messageId = rs.longOrNull("message_id"),
                snapshot = rs.getString("snapshot")?.let { runCatching { ProtocolJson.decodeFromString(ReportSnapshot.serializer(), it) }.getOrNull() },
                status = rs.getString("status"),
                resolution = rs.getString("resolution"),
                resolvedAt = rs.longOrNull("resolved_at"),
            )
        }
    }

    fun resolveReport(id: Long, resolution: String) {
        val n = db.tx { c -> c.update("UPDATE reports SET status = 'resolved', resolution = ?, resolved_at = ? WHERE id = ?", resolution, clock.now(), id) }
        if (n == 0) throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "no such report")
    }

    // ------------------------------------------------------------------ maintenance

    /** Deletes resolved reports 180 days after resolution and expired codes. Returns the number of reports purged. */
    fun purge(): Int = db.tx { c ->
        val now = clock.now()
        c.update("DELETE FROM codes WHERE expires_at <= ?", now)
        c.update("DELETE FROM reports WHERE status = 'resolved' AND resolved_at <= ?", now - REPORT_RETENTION_MILLIS)
    }
}
