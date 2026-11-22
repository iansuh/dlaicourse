package app.numbaseball.ui.common

import androidx.annotation.StringRes
import app.numbaseball.R
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.data.account.ApiResult
import app.numbaseball.data.online.RequestResult

/** Localizes protocol error codes (docs/protocol.md §11). Unknown codes get a generic message. */
@StringRes
fun errorMessage(code: String): Int = when (code) {
    ErrorCodes.RATE_LIMITED -> R.string.error_rate_limited
    ErrorCodes.MESSAGE_TOO_LARGE -> R.string.error_message_too_large
    ErrorCodes.NOT_FOUND -> R.string.error_not_found
    ErrorCodes.UNAUTHORIZED -> R.string.error_unauthorized
    ErrorCodes.BANNED -> R.string.error_banned
    ErrorCodes.INVALID_NICKNAME -> R.string.nickname_error_invalid
    ErrorCodes.NICKNAME_PROFANITY -> R.string.nickname_error_profanity
    ErrorCodes.NICKNAME_UNAVAILABLE -> R.string.nickname_error_unavailable
    ErrorCodes.INVALID_CODE -> R.string.error_invalid_code
    ErrorCodes.TOO_MANY_ATTEMPTS -> R.string.error_too_many_attempts
    ErrorCodes.SESSION_REPLACED -> R.string.error_session_replaced
    ErrorCodes.ROOM_NOT_FOUND -> R.string.error_room_not_found
    ErrorCodes.ROOM_FULL -> R.string.error_room_full
    ErrorCodes.TEAM_FULL -> R.string.error_team_full
    ErrorCodes.ALREADY_IN_ROOM -> R.string.error_already_in_room
    ErrorCodes.NOT_IN_ROOM -> R.string.error_not_in_room
    ErrorCodes.NOT_HOST -> R.string.error_not_host
    ErrorCodes.INVALID_SETTINGS -> R.string.error_invalid_settings
    ErrorCodes.MATCH_IN_PROGRESS -> R.string.error_match_in_progress
    ErrorCodes.WRONG_PHASE, ErrorCodes.NOT_IN_DUEL, ErrorCodes.NOT_QUEUED -> R.string.error_wrong_phase
    ErrorCodes.ALREADY_QUEUED -> R.string.error_already_queued
    ErrorCodes.INVALID_SECRET, ErrorCodes.INVALID_GUESS -> R.string.error_invalid_number
    ErrorCodes.SECRET_ALREADY_SET -> R.string.error_secret_already_set
    ErrorCodes.ALREADY_GUESSED -> R.string.error_already_guessed
    ErrorCodes.STALE_ROUND -> R.string.error_stale_round
    ErrorCodes.CHAT_EMPTY -> R.string.error_chat_empty
    ErrorCodes.CHAT_TOO_LONG -> R.string.error_chat_too_long
    ErrorCodes.CHAT_RATE_LIMITED -> R.string.error_chat_rate_limited
    ErrorCodes.CHAT_NOT_ALLOWED -> R.string.chat_phrases_only
    ErrorCodes.CHAT_RESTRICTED -> R.string.error_chat_restricted
    ErrorCodes.TERMS_REQUIRED, ErrorCodes.TERMS_OUTDATED -> R.string.terms_updated
    ErrorCodes.AGE_CONFIRMATION_REQUIRED -> R.string.error_age_confirmation
    else -> R.string.error_generic
}

/** A user-visible problem: either a protocol error code or "cannot reach the server". */
sealed interface UiError {
    data class Code(val code: String) : UiError
    data object Network : UiError
}

@StringRes
fun UiError.message(): Int = when (this) {
    is UiError.Code -> errorMessage(code)
    UiError.Network -> R.string.error_network
}

fun RequestResult.toUiError(): UiError? = when (this) {
    RequestResult.Ok -> null
    is RequestResult.Failed -> UiError.Code(code)
    RequestResult.NotConnected, RequestResult.TimedOut -> UiError.Network
}

fun ApiResult<*>.toUiError(): UiError? = when (this) {
    is ApiResult.Success -> null
    is ApiResult.Failure -> UiError.Code(code)
    ApiResult.NetworkError -> UiError.Network
}
