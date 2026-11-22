# Number Baseball client–server protocol (v1)

Status: **v1, implemented** (server in `backend/server`, tests in `backend/server/src/test`). The Kotlin source of truth is
`backend/core/src/main/kotlin/app/numbaseball/core/protocol/` (`Common.kt`, `Rest.kt`, `Messages.kt`, `ClientMessageSerializer.kt`).
The app depends on `:core`, so it can use these classes and `ProtocolJson` directly. If this document and the code disagree,
tell the backend engineer; one of them is a bug.

Contents: 1 Conventions · 2 Versioning · 3 REST API · 4 WebSocket connection · 5 Client messages · 6 Server messages ·
7 Room snapshot · 8 State machines · 9 Chat and nicknames · 10 Reconnect, resync, restarts · 11 Error codes · 12 Limits ·
13 Examples · 14 Static pages, admin API, configuration · 15 Data kept by the server · 16 Core library for the app

---

## 1. Conventions

- Transport: HTTPS for REST, WSS for the real-time channel. The base URL is configured in the app.
- Bodies are UTF-8 JSON. Field names are camelCase, enum values are lower snake_case strings (`"playing"`, `"both_solved"`), teams are `"A"` / `"B"`.
- Times are **epoch milliseconds in server time**. Every `room_state` carries `serverTime`; compute `offset = serverTime - localNow`
  on arrival and show countdowns as `deadline - (localNow + offset)`.
- Ids: `playerId` / account `id` is a public 12-character lowercase string (not a secret). `room.id` is opaque.
  A room `code` is 5 characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`; the server upper-cases ASCII letters and ignores spaces and
  dashes (other characters, e.g. full-width letters, never match).
- Secrets and guesses are strings of 3–6 **distinct ASCII digits** `0-9`; `0` may lead (`"0123"`). Full-width, Arabic-Indic and other
  Unicode digits, spaces or signs are invalid. The length must equal the room's `digits`.
- JSON: `ProtocolJson` (strict RFC 8259: no comments, trailing commas, unquoted keys/values, `NaN`, BOM). Unknown keys are ignored,
  defaults and nulls are always encoded. **Clients must ignore unknown fields and unknown message `type`s, and treat unknown error
  codes generically.**
- Display name of a player is `nickname#tag`, e.g. `Mina#0420`. `tag` is always four digits as a string.

## 2. Versioning

- The major version is in the paths: REST under `/api/v1`, WebSocket at `/ws/v1`. `PROTOCOL_VERSION = 1` is also in `welcome` and `GET /api/v1/config`.
- Additive changes (new optional fields, new message types, new error codes, new quick-phrase ids) keep the version.
- A breaking change gets `/api/v2` and `/ws/v2`; the server keeps v1 until old app versions are retired. `/api/v2/...` is `404` today.
- The app sends `X-Client-Version: <versionName>` on REST and WebSocket requests (informational; not logged with personal data).

## 3. REST API

Authenticated endpoints need `Authorization: Bearer <token>` (scheme case-insensitive). Errors always return
`ErrorResponse { "code": String, "message": String, "retryAfterSeconds": Long? }` with the HTTP status shown; `429` also sets
`Retry-After`. Request bodies over 4 KB → `413 message_too_large`. Missing/invalid JSON (also a form-encoded body) → `400 bad_request`.

| Method & path | Auth | Request | Success | Errors |
| --- | --- | --- | --- | --- |
| `GET /api/v1/config` | – | – | `200 ServerInfo` | – |
| `POST /api/v1/accounts` | – | `CreateAccountRequest {nickname}` | `201 AuthResponse {token, account}` | 400 `invalid_nickname` / `nickname_profanity` / `bad_request`, 409 `nickname_unavailable`, 429 `rate_limited` |
| `GET /api/v1/me` | yes (banned ok) | – | `200 AccountDto` | 401 `unauthorized` |
| `PATCH /api/v1/me` | yes | `RenameRequest {nickname}` | `200 AccountDto` | as for create, 429, 403 `banned` |
| `DELETE /api/v1/me` | yes (banned ok) | – | `204` | 401 |
| `PUT /api/v1/me/terms` | yes | `AcceptTermsRequest {version, ageConfirmed}` | `200 AccountDto` | 400 `age_confirmation_required`, 409 `terms_outdated`, 403 |
| `POST /api/v1/me/transfer-code` | yes | – | `200 CodeResponse {code, expiresAt}` | 429, 403 |
| `POST /api/v1/transfer` | – | `RedeemTransferRequest {code}` | `200 AuthResponse` | 400 `invalid_code`, 429 `too_many_attempts` |
| `GET /api/v1/me/blocks` | yes (banned ok) | – | `200 BlockListResponse {blocked: [BlockedPlayer]}` | 401 |
| `PUT /api/v1/me/blocks/{playerId}` | yes (banned ok) | – | `204` (idempotent) | 404 `not_found`, 400 `bad_request` (yourself) |
| `DELETE /api/v1/me/blocks/{playerId}` | yes (banned ok) | – | `204` (idempotent) | – |
| `POST /api/v1/reports` | yes (banned ok) | `ReportRequest` | `201 ReportResponse {reportId}` | 404, 400, 429 |

```text
AccountDto      { id, nickname, tag, stats: Stats, createdAt,
                  termsVersion: String?, termsAcceptedAt: Long?, ageConfirmedAt: Long?, chatRestrictedUntil: Long? }
Stats           { wins: Int, losses: Int, draws: Int }
AuthResponse    { token: String, account: AccountDto }
AcceptTermsRequest { version: String, ageConfirmed: Boolean }
CodeResponse    { code: String, expiresAt: Long }
BlockedPlayer   { id, nickname, tag }
ReportRequest   { targetId: String, reason: ReportReason, roomId: String?, messageId: Long?, note: String? }
ReportReason    "spam" | "harassment" | "hate" | "sexual" | "cheating" | "nickname" | "other"
ServerInfo      { protocol, serverTime, digits: [3,4,5,6], teamSizes: [1,2,3,4,5], roundSeconds: [30,60,120,600],
                  quickRoundSeconds: 60, chatMaxLength: 100, maxMessageBytes: 2048, quickPhrases: [String],
                  termsVersion: String, quickMatchFreeChat: Boolean, minimumAge: Int }
```

### 3.1 Accounts

- **Create** (first launch): the server validates the nickname (§9.4), picks a free random tag and returns a token: 43 characters
  (256 random bits, base64url). **It is shown only once**; the server stores only its SHA-256 hash. Keep it in app storage included in
  Android Auto Backup. A client cannot choose its token (extra fields are ignored). Limit: 10 creations per IP per hour, 300 per hour in total.
- **Rename** keeps the tag if `newNickname#tag` is free, otherwise assigns a new random tag. 5 renames per account per day.
  Rooms you are in show the new name in the next `room_state`.
- **Terms and age** (cannot be skipped): online play and chat need the current Terms of Use. Before the first online action the app shows
  the terms and an "I am at least `ServerInfo.minimumAge` years old" confirmation (no birth date is collected), then sends
  `PUT /me/terms {version: ServerInfo.termsVersion, ageConfirmed: true}`. Without `ageConfirmed: true` → `age_confirmation_required`;
  an old version → `terms_outdated` (reload `/config`). The server stores the version and both times. When `ServerInfo.termsVersion`
  differs from `AccountDto.termsVersion` (the terms changed), the app asks again. Until then `queue_join`, `room_create`, `room_join`,
  `chat` and `phrase` answer `terms_required`. vs-computer play needs no account or terms.
- **Stats**: one result per **match** for every player of that match: the team result (win / loss / team draw → wins / losses / draws + 1),
  except that a player who **left** the room during the match always gets a **loss**. A disconnect forfeit is not "leaving": that player
  gets the team result. Matches that never finished (server restart, §10.3) record nothing. vs-computer games, waiting-room practice
  and the review-room bot (§8.5) never touch stats.
- **Transfer code** (Settings → move to another device): `POST /me/transfer-code` returns a 12-character code shown as `XXXX-XXXX-XXXX`
  from the 32-character alphabet above (60 bits: the first 4 characters identify the code, the last 8 are its secret, stored hashed).
  Case, spaces and dashes are ignored on input. It is valid for **10 minutes**, works once, and a new code replaces the previous one.
  Redeeming it (`POST /transfer`) returns a **new token** and **revokes every older token**. The old device's live connection gets
  `error {code:"session_replaced"}` and close `4000` (do not reconnect); its next REST call gets `401`.
  A code dies after **5 wrong secrets** (from anywhere); an IP gets `429 too_many_attempts` after 10 failed redemptions in 10 minutes.
  Redeeming on a device that already has an account replaces that account on the device: **the app must warn** and suggest making a
  transfer code for the current account first, or it will be orphaned.
- **Delete** (`DELETE /me`, Settings → Delete account; Play requirement): removes the account, tokens, codes, block lists (both ways),
  stats and reports about the player immediately. Reports the player filed lose the reporter id; the player's lines inside other
  reports' snapshots become "deleted player" (sender id, nickname and tag removed). Live rooms: the player leaves (a running duel is
  forfeited), the connection closes with `4004`, and their chat lines turn into "deleted player" (`chat_sender_deleted`).
  Without the app, players email the contact address with their `Nickname#1234` (see `/delete-account`); the operator deletes with the
  admin API (§14.2). There is no web form and no deletion code.
- **Banned** accounts get `403 banned` on everything except `GET /me`, `DELETE /me`, blocks and reports; the WebSocket upgrade is refused with HTTP 403.

### 3.2 Block

Blocking is stored on the server and takes effect immediately, also in a room you are in: the blocked player's chat is not delivered to
you **and yours is not delivered to them**, live and in `chat_history`, and quick match never pairs you. It does not stop a match already
running. The app should also hide lines it already shows from that player.

### 3.3 Report

`roomId` is `room.id`, `messageId` is `ChatMessage.id`. If the room is still alive and you are or were in it, the server stores a snapshot
of the reported message and the last 20 lines of that room as delivered (masked). If the message exists, it must have been sent by
`targetId` (else `400`). `messageId` without `roomId` → `400`; reporting yourself → `400`; unknown target → `404`; `note` ≤ 500 characters.
Limit: 10 reports per account per hour. **When 3 different players report the same player within 24 hours, that player's chat is
restricted for 24 hours** (pending review; `AccountDto.chatRestrictedUntil`, error `chat_restricted`). Moderators can restrict, lift
and ban (§14.2). Reporting does not block; the app should offer "block too".

## 4. WebSocket connection

`GET /ws/v1` with `Authorization: Bearer <token>` (and optionally `X-Client-Version`).

- Missing/unknown token → HTTP `401` (no upgrade). Banned → HTTP `403`. Do not retry on 401/403.
- One live connection per account; a new connection **replaces** the old one (old: `error session_replaced`, close `4000`). The seat moves to the new connection.
- After the upgrade the server sends `welcome` at once. If the account has a seat, `room_state` and `chat_history` follow.
- The server pings every 15 s and drops a connection silent for 30 s. App-level `ping` → `pong` exists for latency/clock checks.
- Text frames only, one JSON object per frame. A message over **2048 UTF-8 bytes** → `error message_too_large` (connection stays open).
  A frame over **16 KB** → close `1009`. Binary frames are ignored. Invalid JSON, unknown `type`, missing or wrongly typed fields →
  `error malformed_message` (connection stays open).
- Rate: about 10 messages per second sustained, bursts of 30. Excess messages are dropped with `error rate_limited` (`retryAfterMs`);
  50 dropped in a row → close `1008`.

| Close code | Meaning | Reconnect? |
| --- | --- | --- |
| 1000 / 1001 | normal / server restarting (§10.3) | yes, with backoff |
| 1008 | flooding | yes, after a delay |
| 1009 | frame too big | yes |
| 4000 | session replaced (newer connection, or account moved by transfer) | **no** |
| 4003 | banned while connected | **no** |
| 4004 | account deleted | **no** |

## 5. Client → server messages

Every message is `{"type": "...", ...fields, "rid": Long?}`. With `rid` the server replies exactly once with `ack {rid}` or
`error {rid, ...}` (state changes still arrive as `room_state`; `ping` is answered by `pong` only). Without `rid` only errors are sent.
An error never changes state.

**Every message that needs a seat answers `not_in_room` when you have none**: `room_leave`, `room_settings`, `team_set`, `room_reset`,
`secret`, `guess`, `forfeit`, `chat`, `phrase`.

| type | Fields | When | Effect / errors (checked in this order) |
| --- | --- | --- | --- |
| `ping` | – | any | `pong {rid, serverTime}` |
| `resync` | – | any | `room_state` + `chat_history` if seated, `queued` if queued, otherwise nothing but the `ack` |
| `queue_join` | `digits` (3–6) | not seated, not queued | `queued {digits, since}`. `invalid_settings`, `already_in_room`, `already_queued`, `terms_required` |
| `queue_leave` | – | queued | `not_queued` |
| `room_create` | `settings` | not seated, not queued | creates a private room, you are host on team A. `invalid_settings`, `already_in_room` (also when queued), `terms_required` |
| `room_join` | `code` | not seated (or seated in that room: state is re-sent) | seats you on the smaller team (A on a tie). `already_in_room` (also when queued), `room_not_found`, `match_in_progress` (playing or finished), `room_full`, `terms_required` |
| `room_leave` | – | seated | lobby/finished: seat freed. Playing: your running duel is forfeited (`forfeit`) and you are marked `left`. You get `room_left {reason:"left"}` |
| `room_settings` | `settings` | host, private, lobby | replaces settings; may start the match. `not_host` (also in quick rooms), `wrong_phase`, `invalid_settings` (also when a team is already larger than the new `teamSize`) |
| `team_set` | `team`, `playerId?` | private, lobby | moves you (or, host only, `playerId`). `not_host` (quick rooms, or moving someone else), `wrong_phase`, `not_found`, `team_full` |
| `room_reset` | – | host, private, finished | back to the lobby with the same settings; players who left are removed; starts again at once if full. `not_host`, `wrong_phase` |
| `secret` | `secret` | your duel is `choosing` | `wrong_phase` (no match yet / other phase), `invalid_secret`, `secret_already_set`, `not_in_duel` |
| `guess` | `guess`, `round` | your duel is `guessing` | `round` must equal `myDuel.round`. `wrong_phase`, `invalid_guess`, `stale_round`, `already_guessed` |
| `forfeit` | – | your duel is running | you lose the duel (`forfeit`). `wrong_phase` |
| `chat` | `text` | seated | §9.1. `chat_not_allowed` (quick room), `chat_empty`, `chat_too_long`, `terms_required`, `chat_restricted`, `chat_rate_limited` |
| `phrase` | `phraseId` | seated | §9.2. `unknown_phrase`, `terms_required`, `chat_restricted`, `chat_rate_limited` |

`RoomSettings { digits: 3..6, teamSize: 1..5, roundSeconds: 30 | 60 | 120 | 600 }` (600 = "relaxed"; there are no untimed online rooms).
An account is in at most one place: the queue **or** one room.

## 6. Server → client messages

| type | Fields | Notes |
| --- | --- | --- |
| `welcome` | `protocol, serverTime, account: AccountDto, roomId: String?` | first message of every connection |
| `pong` | `rid?, serverTime` | answer to `ping` |
| `ack` | `rid` | request with `rid` succeeded |
| `error` | `code, message, rid?, retryAfterMs?` | `message` is English for logs; localize by `code` |
| `queued` | `digits, since` | you are in the quick-match queue |
| `room_state` | `room: RoomView` | full personalised snapshot (§7); replaces everything you hold for the room |
| `room_left` | `roomId, reason` | you no longer have a seat. `left`, `inactive`, `banned`, `account_deleted`, `closed` |
| `chat` | `roomId, message: ChatMessage` | a new line (the sender gets their own line too, masked) |
| `chat_history` | `roomId, messages` | last ≤ 50 lines, oldest first, block-filtered; replaces local chat for that room |
| `chat_sender_deleted` | `roomId, senderId` | show that sender's lines as "deleted player" |

A quick match is found when a `room_state` with `kind = "quick"` arrives while you are queued.

## 7. Room snapshot (`RoomView`)

```text
RoomView {
  id, kind: "quick" | "private", code: String? (private only), phase: "lobby" | "playing" | "finished",
  settings: RoomSettings, hostId: String? (private only), youId,
  players: [PlayerView { id, nickname, tag, team, connected, left, deleted }]   (join order; deleted → nickname/tag "")
  match: MatchView? (null in lobby), version: Long, serverTime: Long
}
MatchView { number (1, 2, ...), duels: [DuelSummary { index, playerA, playerB, phase, round, result? }],
            score: { a, b } (duel wins), result: { winner: "A"|"B"|null, decidedEarly }?, myDuel: MyDuelView? }
DuelResult { winner: "A"|"B"|null (draw or cancelled), reason }
EndReason  "solved" | "both_solved" | "timeout" | "both_timeout" | "disconnect" | "forfeit" | "cancelled"
MyDuelView {
  index, myTeam, opponentId, phase: "choosing"|"guessing"|"finished",
  round (0 while choosing, then 1, 2, ...; after the end the last round played), deadline: Long? (null once finished),
  mySecret?, opponentHasSecret, myPendingGuess?, opponentGuessed,
  myRounds: [RoundEntry] (your guesses), opponentRounds: [RoundEntry] (theirs, against your secret),
  myMisses, opponentMisses (missed rounds in a row; 2 = forfeit), result?,
  opponentSecret? (only once this duel is finished — never earlier, in no message)
}
RoundEntry { round, guess: String? (null = missed), strikes, balls }
```

"Out" is `strikes == 0 && balls == 0` with a non-null guess. Other duels of a team match are visible only as `DuelSummary`.
Every member gets a new `room_state` after every change; `version` strictly increases per room.

## 8. State machines

### 8.1 Quick-match queue

```text
idle --queue_join(d)--> queued(d) --pair found--> quick room (playing)
queued --queue_leave / disconnect--> idle
```

First come, first served among the same digit count, skipping pairs where either blocked the other. The earlier player is team A.
Quick rooms: `teamSize = 1`, `roundSeconds = 60`, **quick phrases only** (§9). The queue entry is dropped when the connection drops; the
app re-sends `queue_join` after reconnecting if the user is still searching. Quick rooms have no reset: players leave and queue again.

### 8.2 Room

```text
room_create / room_join -> LOBBY --both teams have teamSize players (auto start, pairs drawn at random)--> PLAYING --match result--> FINISHED
                             ^------------------------------- room_reset (host, private) ----------------------------------------|
quick rooms start directly in PLAYING.
```

- Host: the creator; if the host leaves, the earliest-joined remaining player becomes host.
- Lobby: players switch team, the host moves players and changes settings. Seats of players disconnected for 60 s are freed (`inactive`).
- Practising against the computer while waiting is client-side only (`:core` `ComputerPlayer` + `Duel`); switch to the match when it starts.
- Playing: each pair plays its own duel (8.3); the match result follows 8.4. Nobody can join (`match_in_progress`). `room_leave` forfeits
  your duel and marks you `left`. Disconnected players keep their seat; the duel engine handles grace and forfeits.
- Finished: everyone sees results and secrets. Seats of players disconnected for 60 s are freed. A room with no remaining player is deleted;
  a room with nobody connected for 10 minutes is closed (connected members get `room_left {closed}`).

### 8.3 Duel

```text
CHOOSING --both secrets set--> GUESSING(round 1) --both guessed or deadline--> resolve --> GUESSING(round n+1) | FINISHED
```

- Timer `T = roundSeconds` (60 in quick match). CHOOSING has deadline `start + T`; a player without a secret then gets a random one
  (visible as `mySecret`) and this **counts as one missed round**.
- GUESSING round n: deadline `roundStart + T`. A round resolves as soon as both have guessed, or at the deadline. A player without a guess
  gets a missed entry (`guess: null`) and `misses + 1`; a guess resets that player's misses to 0. The next round starts at the moment of
  resolution (deadline = resolution time + T).
- Resolution: both solved → draw `both_solved`; one solved → win `solved`; otherwise both at 2 misses → draw `both_timeout`; one at
  2 misses → the other wins `timeout`. Solving beats the opponent's timeout in the same round.
- Disconnect: a player disconnected for **60 s** loses `disconnect` (both at the same instant → draw). A reconnect must arrive **before**
  60 000 ms; at exactly 60 000 ms it is too late. Missed rounds keep counting while disconnected.
- When a round deadline and a disconnect expiry fall on the same millisecond, the round is resolved first (a solve in that round counts).
- `forfeit` or `room_leave` → loss `forfeit`.

### 8.4 Team match

- `teamSize = N` gives N duels (1:1 is N = 1). A duel win is 1 point; draws and cancelled duels give none.
- **Early decision**: as soon as `leaderScore > otherScore + unfinishedDuels`, the match is decided; unfinished duels end with
  `{winner: null, reason: "cancelled"}` (secrets revealed) and `match.result.decidedEarly = true`.
- Otherwise it ends when every duel has ended: more duel wins takes the match; equal → team draw (`winner: null`). Draws are normal.

### 8.5 Review room (Play review)

When the server has `REVIEW_ROOM_CODE` (5 characters), `room_join` with that code (terms required) creates a fresh private room where the
joining player (host, team A) plays a 1:1, 4-digit, 120 s duel against a server bot (`ReviewBot`). The bot chooses its secret and each guess
a few seconds into each phase, and posts one quick phrase and one text line so Report and Block can be tried. `room_reset` starts another
duel. Bot games record no stats for anyone. Disabled when the variable is unset.

## 9. Chat and nicknames

### 9.1 Text chat (private rooms)

Free text is allowed in **private rooms** only. In quick rooms `chat` answers `chat_not_allowed` (use quick phrases); the server flag
`QUICK_MATCH_FREE_CHAT` can allow it (`ServerInfo.quickMatchFreeChat`). The cleaning (`ChatFilter.clean` in `:core`, same on both sides):

1. Emoji are removed: Emoji_Presentation characters, everything in U+1F000–U+1FAFF (incl. flags and skin tones), ZWJ, variation selectors,
   keycap sequences (with their base, e.g. `1️⃣`), tag characters. Text-presentation symbols **without** U+FE0F (♥ © ™ ♪ ★ ☆ ↔ ‼ ✓ ※) and
   emoticons (`^^`, `:)`, `(╯°□°)╯︵ ┻━┻`) stay; a symbol followed by U+FE0F (`❤️`) is removed with it.
2. Control, format (zero-width, bidi), private-use and invisible characters are removed (incl. Hangul fillers U+3164/U+115F/U+1160/U+FFA0,
   braille blank U+2800, U+034F, U+180E, U+17B4/5, U+1D173–U+1D17A); line breaks and tabs become spaces.
3. NFC normalization; at most 2 stacked combining marks per character.
4. Whitespace runs become one space; the text is trimmed.
5. Nothing left → `chat_empty`; more than 100 code points → `chat_too_long`.
6. Listed profanity (ko/en/ja/zh/es/fr/de/pt/ru/vi) is masked with **one `*` per character** (a base character with its marks is one
   character), also when spaced out as single characters (`f u c k`, `시 발`) or with punctuation between letters (`f.u.c.k`), stretched
   (`fuuuck`), with zero-width characters, full-width / circled / mathematical forms, accents, leetspeak (`sh1t`, `$hit`) or look-alike
   letters from other scripts (`fuсk`, `cуka`). Multi-letter fragments are never joined (`push it`, `발표 시 발음` stay clean); digits are
   never masked on their own.

Everybody receives the cleaned text, including the sender. Rate: 5 lines per 10 s per player (text and phrases together) →
`chat_rate_limited` (`retryAfterMs`). A restricted account gets `chat_restricted` (`retryAfterMs` = time left). Delivery: to every
connected member except where a block exists either way. History: last 50 lines per room, kept in memory only while the room exists.
"Mute all chat" is client-side. Chat works in every room phase; there is no team-only chat.

### 9.2 Quick phrases (all rooms)

`phrase {phraseId}` sends a preset line; ids (localize on the client): `hello`, `good_luck`, `have_fun`, `nice_guess`, `close_one`,
`thinking`, `oops`, `well_played`, `good_game`, `thanks`, `rematch`, `bye` (`QuickPhrases.ALL`, `ServerInfo.quickPhrases`). Ids are
case-sensitive; new ids may be added (ignore unknown ones).

### 9.3 Chat message

```text
ChatMessage { id, at, senderId?, senderNickname?, senderTag?, senderTeam?, kind: "text"|"phrase", text?, phraseId? }
```

`senderId == null` means the sender deleted their account: show "deleted player".

### 9.4 Nicknames

After trimming and NFKC normalization (`Nicknames.normalize`): 2–12 code points, each a Unicode letter or digit — Hangul, kana, kanji,
Cyrillic and accented Latin are fine; spaces, symbols, emoji, leftover combining marks and invisible fillers are not → `invalid_nickname`.
Hangul compatibility jamo are kept as typed (`ㅋㅋ`, `ㄱㅏ`; not composed), half-width forms are folded (`ﾐﾅ` → `ミナ`). Listed
profanity anywhere in the name, also glued inside it → `nickname_profanity`. Reserved names (admin, system, moderator, 관리자, ...) →
`invalid_nickname`. Uniqueness is per `nickname#tag`, ignoring case; all 9999 tags of a name taken → `nickname_unavailable`.

## 10. Reconnect, resync, restarts

1. When the socket drops, reconnect with backoff (1 s, 2 s, 4 s, then every 5 s) — except after close 4000/4003/4004 or HTTP 401/403.
2. The seat is kept for **60 s** in every phase; in a running duel the clocks keep going.
3. On the new connection the server sends `welcome` (with `roomId` if the seat still exists), then `room_state` and `chat_history`,
   which replace all local room state. If `roomId` is null the seat is gone: return to the home screen and reload `GET /me` (a finished
   match is in the stats; see 10.5 for restarts).
4. `resync` returns the same snapshot at any time (§5).
5. **Server restart.** Rooms, duels, chat history and the queue live in memory only. On a graceful stop every seated player gets
   `room_left {reason:"closed"}` and every connection is closed with `1001`; on a crash the sockets just drop. After the restart
   `welcome.roomId` is null, **unfinished matches record no result**, and the app should say "The match ended because the server restarted"
   when it had a running match and gets `roomId = null` after a 1001 / connection loss. Deploys should happen at quiet times.

## 11. Error codes

| Code | HTTP | Meaning |
| --- | --- | --- |
| `bad_request` | 400 | invalid body / parameters |
| `malformed_message` | – | WS message is not valid JSON, unknown `type`, missing / wrongly typed field |
| `message_too_large` | 413 | body > 4 KB (REST) or message > 2048 bytes (WS) |
| `rate_limited` | 429 | too many requests / messages (`retryAfterSeconds` / `retryAfterMs`) |
| `not_found` | 404 | unknown path, player, report |
| `internal_error` | 500 | bug; retry later |
| `unsupported_version` | 400 | reserved |
| `unauthorized` | 401 | missing or unknown token |
| `banned` | 403 | account banned |
| `invalid_nickname` / `nickname_profanity` | 400 | §9.4 |
| `nickname_unavailable` | 409 | every tag of that nickname is taken |
| `terms_required` | – | accept the current terms first (§3.1) |
| `terms_outdated` | 409 | `PUT /me/terms` with an old version |
| `age_confirmation_required` | 400 | `PUT /me/terms` without `ageConfirmed: true` |
| `invalid_code` | 400 | transfer code wrong, used or expired |
| `too_many_attempts` | 429 | too many wrong codes from this IP |
| `session_replaced` | – | a newer connection took over (then close 4000) |
| `room_not_found` / `room_full` / `team_full` | – | join / team problems |
| `already_in_room` / `not_in_room` | – | seat state mismatch (queued counts as "in a room" for create/join) |
| `not_host` | – | host-only action (also any host action in a quick room) |
| `invalid_settings` | – | digits / teamSize / roundSeconds out of range, or teams larger than the new size |
| `match_in_progress` | – | room is playing or finished |
| `wrong_phase` | – | not allowed in the current room or duel phase |
| `already_queued` / `not_queued` | – | queue state mismatch |
| `invalid_secret` / `invalid_guess` / `secret_already_set` / `already_guessed` / `stale_round` / `not_in_duel` | – | duel input |
| `chat_empty` / `chat_too_long` / `chat_rate_limited` / `unknown_phrase` | – | §9 |
| `chat_not_allowed` | – | free text in a quick room |
| `chat_restricted` | – | chat restricted by a moderator or the 3-reporter rule |

## 12. Limits (summary)

| What | Limit |
| --- | --- |
| Digits / team size / round timer | 3–6 / 1–5 per side / 30, 60, 120 or 600 s (quick: 60 s) |
| Missed rounds in a row / disconnect grace | 2 → forfeit / 60 s |
| Chat | 100 code points, 5 lines per 10 s, 50 lines history per room |
| WS message | 2048 bytes (error), 16 KB frame (close 1009), ~10 msg/s with bursts of 30 |
| Connections | 1 per account (newest wins) |
| Account creation | 10 per IP per hour, 300 per hour in total |
| Rename / transfer codes | 5 per account per day / 5 codes per account per hour |
| Transfer code | 10 min, single use, 5 wrong secrets per code, 10 failures per IP per 10 min |
| Reports | 10 per account per hour, note ≤ 500 characters; 3 reporters in 24 h → 24 h chat restriction |

## 13. Examples

```http
POST /api/v1/accounts                      {"nickname":"Mina"}
201 {"token":"q3V…(43)","account":{"id":"k2m9x4q1ab7p","nickname":"Mina","tag":"0420","stats":{"wins":0,"losses":0,"draws":0},
     "createdAt":1795000000000,"termsVersion":null,"termsAcceptedAt":null,"ageConfirmedAt":null,"chatRestrictedUntil":null}}

PUT /api/v1/me/terms                       {"version":"2026-11-v2","ageConfirmed":true}
200 {... "termsVersion":"2026-11-v2","termsAcceptedAt":1795000005000,"ageConfirmedAt":1795000005000 ...}
```

A 1:1 quick match (client `>`, server `<`):

```text
< {"type":"welcome","protocol":1,"serverTime":1795000010000,"account":{...},"roomId":null}
> {"type":"queue_join","digits":3,"rid":1}
< {"type":"queued","digits":3,"since":1795000010100}
< {"type":"ack","rid":1}
< {"type":"room_state","room":{"id":"r_8f2k…","kind":"quick","code":null,"phase":"playing",
     "settings":{"digits":3,"teamSize":1,"roundSeconds":60},"hostId":null,"youId":"k2m9x4q1ab7p",
     "players":[{"id":"k2m9x4q1ab7p","nickname":"Mina","tag":"0420","team":"A","connected":true,"left":false,"deleted":false},
                {"id":"z8…","nickname":"Leo","tag":"1234","team":"B","connected":true,"left":false,"deleted":false}],
     "match":{"number":1,"duels":[{"index":0,"playerA":"k2m9x4q1ab7p","playerB":"z8…","phase":"choosing","round":0,"result":null}],
              "score":{"a":0,"b":0},"result":null,
              "myDuel":{"index":0,"myTeam":"A","opponentId":"z8…","phase":"choosing","round":0,"deadline":1795000070200,
                        "mySecret":null,"opponentHasSecret":false,"myPendingGuess":null,"opponentGuessed":false,
                        "myRounds":[],"opponentRounds":[],"myMisses":0,"opponentMisses":0,"result":null,"opponentSecret":null}},
     "version":3,"serverTime":1795000010200}}
< {"type":"chat_history","roomId":"r_8f2k…","messages":[]}
> {"type":"secret","secret":"042"}
< {"type":"room_state", ... "myDuel":{"phase":"guessing","round":1,"deadline":1795000075000, ...}}
> {"type":"guess","guess":"123","round":1}
< {"type":"room_state", ... "myPendingGuess":"123" ...}
< {"type":"room_state", ... "round":2,"myRounds":[{"round":1,"guess":"123","strikes":0,"balls":1}] ...}
> {"type":"phrase","phraseId":"nice_guess"}
< {"type":"chat","roomId":"r_8f2k…","message":{"id":1,"kind":"phrase","phraseId":"nice_guess",...}}
< {"type":"room_state", ... "myDuel":{"phase":"finished","result":{"winner":"A","reason":"solved"},"opponentSecret":"719",...},
     "result":{"winner":"A","decidedEarly":false} ...}
> {"type":"room_leave"}
< {"type":"room_left","roomId":"r_8f2k…","reason":"left"}
```

## 14. Static pages, admin API, configuration

### 14.1 Pages (the app has no website)

Plain bilingual (ko + en) pages rendered from `docs/play/*.md` (team memos stripped); no forms, scripts, cookies or external resources.
English comes first unless `Accept-Language` starts with `ko`; `?lang=ko|en` overrides.

| Path | What |
| --- | --- |
| `GET /privacy` | privacy policy |
| `GET /terms` | terms of use; the child-safety section has the anchor `#child-safety` (Korean copy `#child-safety-ko`) |
| `GET /delete-account` | how to delete: in the app, or by email with `Nickname#1234` |
| `GET /app-ads.txt` | `text/plain` content of `APP_ADS_TXT`; 404 when unset |
| `GET /health` | `200 {"status":"ok"}` |
| anything else (incl. `/`) | 404 |

Behind the host's proxy, plain HTTP (`X-Forwarded-Proto: http`) is redirected (301) to HTTPS on the same host.

### 14.2 Admin API

`Authorization: Bearer <ADMIN_TOKEN>`; every path is 404 when `ADMIN_TOKEN` is unset, 401 with another token.

| Method & path | What |
| --- | --- |
| `GET /admin/v1/reports?status=open|resolved|all` | `AdminReportList` (newest first, with snapshots) |
| `POST /admin/v1/reports/{id}/resolve {resolution}` | mark resolved (purged 180 days later) |
| `GET /admin/v1/accounts?handle=Nick%231234` / `GET /admin/v1/accounts/{id}` | `AdminAccount {account, bannedAt, banReason, chatRestrictReason, lastActiveAt}` — check `lastActiveAt` before deleting on an email request |
| `DELETE /admin/v1/accounts?handle=Nick%231234` / `DELETE /admin/v1/accounts/{id}` | delete like `DELETE /me` |
| `POST /admin/v1/accounts/{id}/ban {reason}` / `.../unban` | ban (closes the connection with 4003, removes from room and queue) / lift |
| `POST /admin/v1/accounts/{id}/chat-restrict {hours, reason}` | restrict chat for `hours` (0 lifts) |
| `POST /admin/v1/test/clock?advanceMs=N`, `POST /admin/v1/test/maintenance` | **test servers only** (`TEST_HOOKS=true`): move server time forward / run the clean-up job now |

### 14.3 Configuration (environment)

`PORT`, `DATA_DIR` (SQLite file `numbaseball.db`), `DB_FILE` (override), `ADMIN_TOKEN` (≥ 16 characters), `APP_ADS_TXT`,
`REVIEW_ROOM_CODE`, `QUICK_MATCH_FREE_CHAT` (default false), `MIN_AGE` (default 14), `TERMS_VERSION` (default: read from the bundled terms),
`FORCE_HTTPS` (default true), `LOG_FILE` (optional extra log file). Test servers only: `RELAXED_RATE_LIMITS=true` (REST limits × 1000),
`TEST_HOOKS=true` (§14.2). See `backend/server/DEPLOY.md`.

## 15. Data kept by the server

- Database: account id, nickname, tag, created time, W/L/D, terms version + acceptance and age-confirmation times, last-active time
  (10-minute granularity), ban / chat-restriction state; SHA-256 hashes of tokens and transfer codes; block lists; reports (reason, note,
  target name, snapshot of up to 21 masked chat lines) — resolved reports are deleted 180 days after resolution.
- Memory only: rooms, secrets, guesses, chat history, the queue, rate-limit counters (keyed by IP address or account; never written to disk).
- Logs: method, path and status of requests, errors. Never chat text, tokens, codes, IP addresses or request bodies.

## 16. Core library for the app (`:core`)

| API | Use |
| --- | --- |
| `Rules.isValid / problem / judge / randomNumber` | secret and guess validation, judging |
| `ComputerPlayer(level: EASY/NORMAL/HARD, digits, random)` — `chooseSecret()`, `nextGuess()`, `onResult(guess, strikes, balls)` | offline opponent and waiting-room practice |
| `Duel(DuelConfig.of(digits, roundSeconds), random)` / `TeamMatch` | the same engines the server runs (`roundSeconds = 0` = no timer, offline only) |
| `ChatFilter.clean / sanitize`, `ProfanityFilter.mask / containsProfanity`, `EmojiFilter.strip` | input preview |
| `Nicknames.validate / normalize / display` | nickname screen |
| `QuickPhrases.ALL` | quick-phrase ids |
| `ProtocolJson`, `ClientMessage`, `ServerMessage`, DTOs | wire format |
