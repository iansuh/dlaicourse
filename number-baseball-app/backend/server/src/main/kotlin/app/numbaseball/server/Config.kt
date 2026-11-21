package app.numbaseball.server

import java.io.File

/** Server configuration, read from environment variables (see DEPLOY.md). */
data class Config(
    val port: Int = 8080,
    /** SQLite database and other persistent files live here (a persistent disk in production). */
    val dataDir: File = File("data"),
    /** Bearer token for /admin/v1; admin API is disabled (404) when null. */
    val adminToken: String? = null,
    /** Content of GET /app-ads.txt; 404 when null. */
    val appAdsTxt: String? = null,
    /** Five-character private-room code that starts a duel against a server bot (for Play review); null = off. */
    val reviewRoomCode: String? = null,
    /** Allow free-text chat in quick-match rooms (default: quick phrases only). */
    val quickMatchFreeChat: Boolean = false,
    /** Override for the Terms of Use version; by default it is read from the bundled terms page. */
    val termsVersion: String? = null,
    /** Redirect plain HTTP (as reported by the proxy's X-Forwarded-Proto) to HTTPS on the same host. */
    val forceHttps: Boolean = true,
    /** Minimum age players confirm with the terms (shown by the app; no birth date is collected). */
    val minimumAge: Int = 14,
    /** Test servers only: multiply REST rate limits by 1000 (QA black-box runs create many accounts). */
    val relaxedRateLimits: Boolean = false,
    /** Test servers only: enables /admin/v1/test/clock (move time forward) and /admin/v1/test/maintenance. */
    val testHooks: Boolean = false,
    /** Database file; default DATA_DIR/numbaseball.db. */
    val dbFile: File? = null,
    /** Optional log file in addition to stdout. */
    val logFile: File? = null,
    /** How often timers (round deadlines, disconnect grace, bot moves) are checked. */
    val tickMillis: Long = 250,
    /** Interval of the clean-up job (pending deletions, report retention, expired codes). */
    val maintenanceMillis: Long = 60 * 60 * 1000L,
    /** Seed for room codes, pairings and bot play; null = secure random (tests pass a seed). */
    val randomSeed: Long? = null,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): Config {
            fun str(name: String) = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            fun bool(name: String, default: Boolean) = str(name)?.lowercase()?.let { it == "true" || it == "1" || it == "yes" } ?: default
            return Config(
                port = str("PORT")?.toIntOrNull() ?: 8080,
                dataDir = File(str("DATA_DIR") ?: "data"),
                adminToken = str("ADMIN_TOKEN")?.takeIf { it.length >= 16 },
                // Keep line breaks of app-ads.txt; Render's env editor accepts multi-line values, "\n" also works.
                appAdsTxt = env["APP_ADS_TXT"]?.replace("\\n", "\n")?.trim()?.takeIf { it.isNotEmpty() }?.let { it + "\n" },
                reviewRoomCode = str("REVIEW_ROOM_CODE")?.uppercase()?.takeIf { RoomCodes.isWellFormed(it) },
                quickMatchFreeChat = bool("QUICK_MATCH_FREE_CHAT", false),
                termsVersion = str("TERMS_VERSION"),
                forceHttps = bool("FORCE_HTTPS", true),
                minimumAge = str("MIN_AGE")?.toIntOrNull()?.takeIf { it in 1..99 } ?: 14,
                relaxedRateLimits = bool("RELAXED_RATE_LIMITS", false),
                testHooks = bool("TEST_HOOKS", false),
                dbFile = str("DB_FILE")?.let { File(it) },
                logFile = str("LOG_FILE")?.let { File(it) },
            )
        }
    }
}

/** Time source; injectable so tests can move time forward. */
fun interface Clock {
    fun now(): Long

    companion object {
        val SYSTEM = Clock { System.currentTimeMillis() }
    }
}

/** A clock that can be moved forward (TEST_HOOKS only). */
class OffsetClock(private val base: Clock) : Clock {
    @Volatile private var offset = 0L
    override fun now(): Long = base.now() + offset
    fun advance(ms: Long) { require(ms >= 0) { "time only moves forward" }; offset += ms }
}

object RoomCodes {
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun isWellFormed(code: String): Boolean = code.length == 5 && code.all { it in ALPHABET }

    /** Upper-cases ASCII letters and drops spaces and dashes; anything else is kept (and will not match). */
    fun normalize(input: String): String =
        input.filter { it != ' ' && it != '-' }.map { if (it in 'a'..'z') it - 32 else it }.joinToString("")
}
