package app.numbaseball.server

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * One SQLite connection guarded by a lock (SQLite serializes writers anyway; the game state lives in memory).
 *
 * Stored: accounts (nickname, tag, results, terms acceptance, moderation flags), SHA-256 hashes of tokens and one-time
 * codes, block lists and reports. Never stored: IP addresses, chat text outside report snapshots, secrets or guesses.
 */
class Database(file: File) : AutoCloseable {
    private val lock = ReentrantLock()
    private val conn: Connection

    init {
        file.absoluteFile.parentFile?.mkdirs()
        conn = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
        conn.createStatement().use { st ->
            st.execute("PRAGMA journal_mode=WAL")
            st.execute("PRAGMA synchronous=NORMAL")
            st.execute("PRAGMA busy_timeout=5000")
            st.execute("PRAGMA foreign_keys=ON")
        }
        migrate()
    }

    fun <T> tx(block: (Connection) -> T): T = lock.withLock {
        conn.autoCommit = false
        try {
            val r = block(conn)
            conn.commit()
            r
        } catch (e: Throwable) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    fun <T> read(block: (Connection) -> T): T = lock.withLock { block(conn) }

    override fun close() = lock.withLock { conn.close() }

    private fun migrate() = tx { c ->
        c.createStatement().use { st ->
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS accounts (
                    id TEXT PRIMARY KEY,
                    nickname TEXT NOT NULL,
                    nick_key TEXT NOT NULL,
                    tag INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    wins INTEGER NOT NULL DEFAULT 0,
                    losses INTEGER NOT NULL DEFAULT 0,
                    draws INTEGER NOT NULL DEFAULT 0,
                    terms_version TEXT,
                    terms_accepted_at INTEGER,
                    deletion_requested_at INTEGER,
                    deletion_notice_at INTEGER,
                    banned_at INTEGER,
                    ban_reason TEXT,
                    chat_restricted_until INTEGER,
                    chat_restrict_reason TEXT,
                    is_bot INTEGER NOT NULL DEFAULT 0,
                    UNIQUE (nick_key, tag)
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS tokens (
                    hash TEXT PRIMARY KEY,
                    account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            st.execute("CREATE INDEX IF NOT EXISTS tokens_account ON tokens(account_id)")
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS codes (
                    kind TEXT NOT NULL,
                    lookup TEXT NOT NULL,
                    secret_hash TEXT NOT NULL,
                    account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                    expires_at INTEGER NOT NULL,
                    attempts INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (kind, lookup)
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS blocks (
                    blocker TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                    blocked TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                    created_at INTEGER NOT NULL,
                    PRIMARY KEY (blocker, blocked)
                )
                """.trimIndent(),
            )
            st.execute("CREATE INDEX IF NOT EXISTS blocks_blocked ON blocks(blocked)")
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS reports (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    created_at INTEGER NOT NULL,
                    reporter_id TEXT,
                    target_id TEXT,
                    target_display TEXT,
                    reason TEXT NOT NULL,
                    note TEXT,
                    room_id TEXT,
                    message_id INTEGER,
                    snapshot TEXT,
                    status TEXT NOT NULL DEFAULT 'open',
                    resolution TEXT,
                    resolved_at INTEGER
                )
                """.trimIndent(),
            )
            st.execute("CREATE INDEX IF NOT EXISTS reports_target ON reports(target_id, created_at)")
        }
    }
}

internal fun Connection.update(sql: String, vararg args: Any?): Int = prepareStatement(sql).use { it.bind(args); it.executeUpdate() }

internal fun <T> Connection.query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { ps ->
        ps.bind(args)
        ps.executeQuery().use { rs ->
            val out = ArrayList<T>()
            while (rs.next()) out += map(rs)
            out
        }
    }

internal fun PreparedStatement.bind(args: Array<out Any?>) {
    args.forEachIndexed { i, a ->
        when (a) {
            null -> setObject(i + 1, null)
            is String -> setString(i + 1, a)
            is Int -> setInt(i + 1, a)
            is Long -> setLong(i + 1, a)
            is Boolean -> setInt(i + 1, if (a) 1 else 0)
            else -> error("unsupported parameter ${a::class}")
        }
    }
}

internal fun ResultSet.longOrNull(col: String): Long? = getLong(col).let { if (wasNull()) null else it }
