# Number Baseball client–server protocol (v1)

Status: **v1 draft, stable**. The Kotlin source of truth is `backend/core/src/main/kotlin/app/numbaseball/core/protocol/`
(`Common.kt`, `Rest.kt`, `Messages.kt`). The app depends on `:core`, so it can use these classes and `ProtocolJson` directly.
If this document and the code disagree, tell the backend engineer; one of them is a bug.

Contents: 1 Conventions · 2 Versioning · 3 REST API · 4 WebSocket connection · 5 Client messages · 6 Server messages ·
7 Room snapshot · 8 State machines · 9 Chat · 10 Reconnect and resync · 11 Error codes · 12 Limits · 13 Examples · 14 Web pages

---

## 1. Conventions

- Transport: HTTPS for REST, WSS for the real-time channel. Base URL is configured in the app (for example `https://nb.example.com`).
- Bodies are UTF-8 JSON. Field names are camelCase, enum values are lower snake_case strings (`"playing"`, `"both_solved"`), teams are `"A"` / `"B"`.
- Times are **epoch milliseconds in server time** (`Long`). Every `room_state` carries `serverTime`; compute
  `offset = serverTime - localNow` on arrival and show countdowns as `deadline - (localNow + offset)`.
- Ids: `playerId` / account `id` is a public 12-character lowercase string (not a secret). `room.id` is an opaque string.
  Room `code` is 5 characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`; the server upper-cases input and ignores spaces and dashes.
- Secrets and guesses are strings of 3–6 **distinct** ASCII digits; `0` may lead (`"0123"`). Length must equal the room's `digits`.
- JSON configuration (`ProtocolJson`): unknown keys are ignored, defaults and nulls are always encoded.
  **Clients must ignore unknown fields and unknown message `type`s, and treat unknown error codes generically.**
- Display name of a player is `nickname#tag`, for example `Mina#0420`. `tag` is always four digits as a string.

## 2. Versioning

- The major version is in the paths: REST under `/api/v1`, WebSocket at `/ws/v1`. `PROTOCOL_VERSION = 1` is also sent in `welcome` and `GET /api/v1/config`.
- Additive changes (new optional fields, new message types, new error codes, new quick-phrase ids) do **not** change the version.
- A breaking change gets `/api/v2` and `/ws/v2`; the server keeps serving v1 until old app versions are retired.
- The app sends `X-Client-Version: <versionName>` on REST and WebSocket requests (informational, used in logs only).

## 3. REST API

Authenticated endpoints need `Authorization: Bearer <token>`. Errors always return
`ErrorResponse { "code": String, "message": String, "retryAfterSeconds": Long? }` with the HTTP status shown.
Request bodies larger than 4 KB are rejected with `413 message_too_large`. Missing/invalid JSON → `400 bad_request`.

| Method & path | Auth | Request | Success | Errors |
| --- | --- | --- | --- | --- |
| `GET /api/v1/config` | – | – | `200 ServerInfo` | – |
| `POST /api/v1/accounts` | – | `CreateAccountRequest {nickname}` | `201 AuthResponse {token, account}` | 400 `invalid_nickname`, 400 `nickname_profanity`, 409 `nickname_unavailable`, 429 `rate_limited` |
| `GET /api/v1/me` | yes (banned ok) | – | `200 AccountDto` | 401 `unauthorized` |
| `PATCH /api/v1/me` | yes | `RenameRequest {nickname}` | `200 AccountDto` | 400/409 as above, 429 `rate_limited`, 403 `banned` |
| `DELETE /api/v1/me` | yes (banned ok) | – | `204` | 401 |
| `POST /api/v1/me/transfer-code` | yes | – | `200 CodeResponse {code, expiresAt}` | 429, 403 `banned` |
| `POST /api/v1/transfer` | – | `RedeemTransferRequest {code}` | `200 AuthResponse` | 400 `invalid_code`, 429 `too_many_attempts` |
| `POST /api/v1/me/deletion-code` | yes (banned ok) | – | `200 CodeResponse` | 429 |
| `GET /api/v1/me/blocks` | yes | – | `200 BlockListResponse {blocked: [BlockedPlayer]}` | 401 |
| `PUT /api/v1/me/blocks/{playerId}` | yes | – | `204` (idempotent) | 404 `not_found`, 400 `bad_request` (yourself) |
| `DELETE /api/v1/me/blocks/{playerId}` | yes | – | `204` (idempotent) | – |
| `POST /api/v1/reports` | yes | `ReportRequest` | `201 ReportResponse {reportId}` | 404 `not_found`, 400 `bad_request`, 429 |

Types (see `Rest.kt`):

```text
AccountDto      { id: String, nickname: String, tag: String, stats: Stats, createdAt: Long }
Stats           { wins: Int, losses: Int, draws: Int }
AuthResponse    { token: String, account: AccountDto }
CodeResponse    { code: String, expiresAt: Long }
BlockedPlayer   { id: String, nickname: String, tag: String }
ReportRequest   { targetId: String, reason: ReportReason, roomId: String?, messageId: Long?, note: String? }
ReportReason    "spam" | "harassment" | "hate" | "sexual" | "cheating" | "nickname" | "other"
ServerInfo      { protocol, serverTime, digits: [3,4,5,6], teamSizes: [1..5], roundSeconds: [0,30,60,120],
                  quickRoundSeconds: 60, chatMaxLength: 100, maxMessageBytes: 2048, quickPhrases: [String] }
```

### 3.1 Accounts

- **Create** (first launch): the server validates the nickname (section 9.3), picks a free random tag for it and returns a token.
  The token is 43 characters (256 random bits, base64url). **It is shown only once**; the server stores only its SHA-256 hash.
  Keep it in app storage that is included in Android Auto Backup. Limit: 10 creations per IP per hour.
- **Rename** keeps the tag if `newNickname#tag` is free, otherwise assigns a new random tag. Limit 5 renames per account per day.
  Rooms you are in see the new name in the next `room_state`.
- **Stats**: one result per **match** for every player who took part (team result in team play; a player who left still gets
  the team result). Win → wins+1, loss → losses+1, team draw → draws+1.
- **Transfer code** (Settings → move to another device): `POST /me/transfer-code` returns a 12-character code shown as
  `XXXX-XXXX-XXXX` (the app may display it with dashes; the server ignores dashes, spaces and case). It is valid for 10 minutes,
  works once, and a new code replaces the previous one. Redeeming it on the new device (`POST /transfer`) returns a **new token**
  and **revokes every older token** of the account, so the old device is signed out (its next request gets `401`).
  A code dies after 5 wrong attempts; an IP gets `429 too_many_attempts` after 10 failed redemptions in 10 minutes.
- **Delete** (`DELETE /me`): removes the account, tokens, codes, block lists and stats immediately. Live rooms: the player leaves
  (forfeit if in a duel) and their chat lines turn into "deleted player" (`chat_sender_deleted`). Nothing else is kept.
  Reports the player filed lose the reporter id; reports about the player are closed and their snapshots are erased.
- **Deletion code**: `POST /me/deletion-code` returns a 12-character code valid for 24 hours for the web page `/delete-account`
  (section 14). The app shows it in Settings next to "Delete account" for players who prefer the web form.
- **Banned** accounts get `403 banned` on everything except `GET /me`, `DELETE /me` and `POST /me/deletion-code`; the WebSocket upgrade is refused with HTTP 403.

### 3.2 Block

Blocking is stored on the server and takes effect immediately, also in a room you are in:
the blocked player's chat is not delivered to you **and yours is not delivered to them**, both live and in `chat_history`,
and quick match never pairs you. Blocking does not affect a match already running. The app should also hide the blocked
player's lines it already shows.

### 3.3 Report

`roomId` is `room.id`, `messageId` is `ChatMessage.id`. If the room is still alive and you are (or were) in it, the server stores a
snapshot of the reported message and the last 20 messages of that room as delivered (masked). A message report must name the
message's sender as `targetId`, otherwise `400 bad_request`. A player report (no `messageId`) is always accepted. `note` ≤ 500 chars.
Limit: 10 reports per account per hour. Reporting does not block; the app should offer "block too" after a report.

## 4. WebSocket connection

`GET /ws/v1` with headers `Authorization: Bearer <token>` (and optionally `X-Client-Version`).

- Missing/unknown token → HTTP `401` (no upgrade). Banned → HTTP `403`. The app must not retry on 401/403;
  on 401 it should show the "account not found" flow.
- One live connection per account. A new connection **replaces** the old one: the old socket receives
  `error {code:"session_replaced"}` and is closed with code `4000`. The room seat moves to the new connection.
- After the upgrade the server sends `welcome` immediately. If the account has a seat in a room, `room_state` and `chat_history` follow.
- The server sends WebSocket ping frames every 15 s and drops a connection that stays silent for 30 s. App-level `ping` → `pong` is
  available for latency/clock checks.
- Text frames only, one JSON object per frame. Messages > 2048 UTF-8 bytes → `error message_too_large` (connection stays open).
  Frames > 16 KB → close `1009`. Binary frames are ignored.
- Rate limit: about 10 messages per second sustained with bursts of 30. Excess messages are dropped with `error rate_limited`
  (`retryAfterMs` set). After 50 dropped messages in a row the connection is closed with `1008`.

Close codes the app must know:

| Code | Meaning | Reconnect? |
| --- | --- | --- |
| 1000 / 1001 | normal / server restarting | yes (backoff) |
| 1008 | policy violation (flooding) | yes, after a delay |
| 1009 | frame too big | yes |
| 4000 | session replaced by a newer connection | **no** |
| 4003 | account banned while connected | **no** |
| 4004 | account deleted (here or on another device) | **no** |

## 5. Client → server messages

Every message is `{"type": "...", ...fields, "rid": Long?}`. With `rid` the server replies exactly once with `ack {rid}` or
`error {rid, ...}`; state changes still arrive as `room_state`. Without `rid` only errors are sent.

| type | Fields | When | Effect / errors |
| --- | --- | --- | --- |
| `ping` | – | any | `pong {rid, serverTime}` (no ack) |
| `resync` | – | any | sends `room_state` + `chat_history` if you have a seat, `queued` if you are queued (§10) |
| `queue_join` | `digits: Int` (3–6) | not in a room, not queued | `queued {digits, since}`. Errors: `invalid_settings`, `already_in_room`, `already_queued` |
| `queue_leave` | – | queued | leaves the queue. Error: `not_queued` |
| `room_create` | `settings: RoomSettings` | not in a room | creates a private room, you are host on team A; `room_state` follows. Errors: `invalid_settings`, `already_in_room` (also when queued: leave the queue first) |
| `room_join` | `code: String` | not in a room (or already in that same room) | seats you on the smaller team (A on a tie). Errors: `room_not_found`, `room_full`, `match_in_progress`, `already_in_room` |
| `room_leave` | – | in a room | lobby/finished: seat freed. Playing: your duel is forfeited (`forfeit`), your seat is kept as `left`. You get `room_left {reason:"left"}`. Error: `not_in_room` |
| `room_settings` | `settings: RoomSettings` | host, private, lobby | replaces settings; may start the match if teams are now full. Errors: `not_host`, `wrong_phase`, `invalid_settings` (also when a team already has more players than the new `teamSize`) |
| `team_set` | `team`, `playerId?` | private, lobby | moves you (or, host only, `playerId`) to `team`. Errors: `team_full`, `not_host`, `wrong_phase`, `not_found` |
| `room_reset` | – | host, private, finished | back to lobby with the same settings; players who left are removed; starts again at once if teams are full. Errors: `not_host`, `wrong_phase` |
| `secret` | `secret: String` | your duel is `choosing` | Errors: `invalid_secret`, `secret_already_set`, `wrong_phase`, `not_in_duel` |
| `guess` | `guess: String`, `round: Int` | your duel is `guessing` | `round` must equal `myDuel.round`. Errors: `invalid_guess`, `already_guessed`, `stale_round`, `wrong_phase`, `not_in_duel` |
| `forfeit` | – | your duel running | you lose the duel (`forfeit`). Errors: `wrong_phase`, `not_in_duel` |
| `chat` | `text: String` | in a room | see §9. Errors: `chat_empty`, `chat_too_long`, `chat_rate_limited`, `not_in_room` |
| `phrase` | `phraseId: String` | in a room | quick phrase (§9.2). Errors: `unknown_phrase`, `chat_rate_limited`, `not_in_room` |

`RoomSettings { digits: Int (3..6), teamSize: Int (1..5), roundSeconds: Int (0 = none, 30, 60, 120) }`.

An account is in at most one place: the queue **or** one room.

## 6. Server → client messages

| type | Fields | Notes |
| --- | --- | --- |
| `welcome` | `protocol: Int, serverTime: Long, account: AccountDto, roomId: String?` | first message of every connection |
| `pong` | `rid: Long?, serverTime: Long` | answer to `ping` |
| `ack` | `rid: Long` | request with `rid` succeeded |
| `error` | `code: String, message: String, rid: Long?, retryAfterMs: Long?` | `message` is English, for logs only; localize by `code` |
| `queued` | `digits: Int, since: Long` | you are in the quick-match queue |
| `room_state` | `room: RoomView` | full personalised snapshot (§7); replaces everything you hold for the room |
| `room_left` | `roomId: String, reason: LeaveReason` | you no longer have a seat. `reason`: `left`, `inactive`, `banned`, `account_deleted`, `closed` |
| `chat` | `roomId: String, message: ChatMessage` | a new chat line |
| `chat_history` | `roomId: String, messages: [ChatMessage]` | last ≤ 50 lines, oldest first, block-filtered; replaces local chat for that room |
| `chat_sender_deleted` | `roomId: String, senderId: String` | show that sender's lines as "deleted player" |

A quick match is found when `room_state` with `kind = "quick"` arrives while you are queued (there is no separate "matched" message).

## 7. Room snapshot (`RoomView`)

```text
RoomView {
  id: String, kind: "quick" | "private", code: String?   (private only)
  phase: "lobby" | "playing" | "finished"
  settings: RoomSettings, hostId: String?                 (private only)
  youId: String
  players: [PlayerView { id, nickname, tag, team: "A"|"B", connected: Boolean, left: Boolean }]   (join order)
  match: MatchView?                                       (null in lobby)
  version: Long, serverTime: Long
}
MatchView {
  number: Int                                             (1, 2, ... per room)
  duels: [DuelSummary { index, playerA, playerB, phase, round, result: DuelResult? }]
  score: { a: Int, b: Int }                               (duel wins)
  result: MatchResult? { winner: "A"|"B"|null (team draw), decidedEarly: Boolean }
  myDuel: MyDuelView?                                     (your duel; every seated player has one in v1)
}
DuelResult { winner: "A"|"B"|null (draw), reason: EndReason }
EndReason  "solved" | "both_solved" | "timeout" | "both_timeout" | "disconnect" | "forfeit" | "cancelled"
MyDuelView {
  index, myTeam, opponentId, phase: "choosing"|"guessing"|"finished"
  round: Int                     (0 while choosing, then 1, 2, ...; after the end: rounds played)
  deadline: Long?                (server time; null = no timer or finished)
  mySecret: String?, opponentHasSecret: Boolean
  myPendingGuess: String?        (submitted for the current round, not yet resolved)
  opponentGuessed: Boolean       (opponent submitted for the current round)
  myRounds: [RoundEntry]         (your guesses vs. the opponent's secret)
  opponentRounds: [RoundEntry]   (opponent's guesses vs. your secret)
  myMisses: Int, opponentMisses: Int   (missed rounds in a row; 2 = forfeit)
  result: DuelResult?
  opponentSecret: String?        (only after the duel ended — never earlier)
}
RoundEntry { round: Int, guess: String? (null = missed), strikes: Int, balls: Int }
```

"Out" is `strikes == 0 && balls == 0` with a non-null guess. Other duels in a team match are visible only as `DuelSummary`
(no guesses, no secrets). The server sends a new `room_state` to every member after every change (join, leave, connection change,
settings, secret set, guess submitted, round resolved, duel/match end); `version` strictly increases per room.

## 8. State machines

### 8.1 Quick-match queue

```text
idle --queue_join(d)--> queued(d) --pair found--> in quick room (phase playing)
queued --queue_leave / disconnect--> idle
```

Pairing is first-come first-served among players waiting for the same digit count, skipping pairs where either side blocked the
other. The earlier player is team A. Quick rooms: `teamSize = 1`, `roundSeconds = 60`. **The queue entry is dropped when the
connection drops**; after reconnecting the app should send `queue_join` again if the user is still searching.

### 8.2 Room

```text
                 room_create / room_join
                          |
                          v
   +--------------->  LOBBY  ---- both teams have teamSize players ----> PLAYING ---- match result ----> FINISHED
   |   (private only)   ^  (auto start, immediately; pairs drawn at random)                                  |
   |                    +---------------------------- room_reset (host, private) ----------------------------+
   +-- quick rooms are created directly in PLAYING; FINISHED quick rooms only empty out (no reset in v1).
```

- Host: the creator; if the host leaves, the player who joined earliest among the remaining becomes host.
- Lobby: players can switch team, the host can move players and change settings. Seats of players disconnected for more than 60 s are freed.
- Practising against the computer while waiting is client-side only (use `:core` `ComputerPlayer` and `Duel`); the server is not involved.
  When the room fills the server starts the match and the app should switch to it.
- Playing: each pair plays an independent duel (8.3); the match result follows 8.4. Nobody can join (`match_in_progress`).
  `room_leave` forfeits your duel and marks you `left`. Disconnected players keep their seat; the duel engine handles grace and forfeits.
- Finished: everyone sees results and both secrets. Players can leave. Seats of players disconnected for more than 60 s are freed.
  A room with nobody left is deleted. A room with no connected player for 10 minutes is closed; a player who comes back later
  simply gets `welcome.roomId = null`. Connected players of a room closed by the server get `room_left {reason:"closed"}`.

### 8.3 Duel

```text
CHOOSING --both secrets set--> GUESSING(round 1) --both guessed or deadline--> resolve --> GUESSING(round n+1) | FINISHED
```

- Timer `T = roundSeconds` (60 in quick match; `0` = no timer). CHOOSING has deadline `start + T`. If it passes, a player without a
  secret gets a random one (shown to them in `mySecret`) and this **counts as one missed round**.
- GUESSING round n: deadline `roundStart + T`. A round resolves as soon as both have guessed, or at the deadline. A player without a
  guess gets a missed entry (`guess: null`) and `misses + 1`; a guess resets that player's `misses` to 0. The next round starts right
  after resolution (deadline = resolution time + T).
- Resolution order: both solved → draw `both_solved`; one solved → that player wins `solved`; otherwise both at 2 misses → draw
  `both_timeout`; one at 2 misses → the other wins `timeout`. Solving beats the opponent's timeout in the same round.
- Disconnect: a player disconnected for 60 s loses `disconnect` (both at the same instant → draw). Reconnecting in time cancels it;
  missed rounds still count, so with a 30 s timer two missed rounds can forfeit before 60 s are over.
- `forfeit` or `room_leave` → loss `forfeit`.
- The opponent's secret is never sent before the duel is FINISHED.

### 8.4 Team match

- `teamSize = N` gives N duels, pairs drawn at random. A duel win is 1 point for that team; draws and cancelled duels give none.
- **Early decision**: as soon as `leaderScore > otherScore + unfinishedDuels`, the match is decided; unfinished duels end with
  `result {winner: null, reason: "cancelled"}` (secrets revealed) and `match.result.decidedEarly = true`.
- Otherwise the match ends when every duel has ended: more duel wins takes the match; equal → team draw (`winner: null`).
  Draws are normal results (for example 1 win, 1 loss, 1 draw in 3v3 is a team draw).
- 1:1 is simply `teamSize = 1`.

## 9. Chat

### 9.1 Text chat

The server cleans every `chat.text`: removes emoji (including ZWJ sequences, flags, skin tones, keycaps), control and zero-width
characters, collapses whitespace and trims. If nothing is left → `chat_empty`; more than 100 characters (code points) after cleaning →
`chat_too_long`. Listed profanity in ko/en/ja/zh/es/fr/de/pt/ru/vi is replaced with `*` (one per character), also when spaced
out, stretched (`fuuuck`), written with zero-width characters, full-width or look-alike letters. The cleaned text is what everybody
gets, including the sender (the sender's own `chat` event shows the masked text). The app should apply the same `ChatFilter` from
`:core` to the input box preview if it wants to, but the server is authoritative.

- Rate: 5 lines per 10 seconds per player (text and phrases together) → `chat_rate_limited` with `retryAfterMs`.
- Delivery: to every connected room member except where a block exists in either direction. History: the last 50 lines per room,
  kept only while the room exists (never stored on disk).
- "Mute all chat" is a client-side setting; the server keeps delivering and the app hides.
- Chat works in every room phase. There is no team-only chat in v1.

### 9.2 Quick phrases

`phrase {phraseId}` sends a preset line; ids (localize on the client): `hello`, `good_luck`, `have_fun`, `nice_guess`, `close_one`,
`thinking`, `oops`, `well_played`, `good_game`, `thanks`, `rematch`, `bye`. The list is `QuickPhrases.ALL` in `:core` and
`ServerInfo.quickPhrases`; new ids may be added (ignore ids you do not know).

```text
ChatMessage { id: Long, at: Long, senderId: String?, senderNickname: String?, senderTag: String?, senderTeam: "A"|"B"|null,
              kind: "text"|"phrase", text: String?, phraseId: String? }
```

`senderId == null` means the sender deleted their account: show "deleted player".

### 9.3 Nicknames

2–12 characters after NFKC normalization and trimming; every character must be a Unicode letter or digit (Hangul, kana, kanji,
Cyrillic, accented Latin are fine; spaces, symbols and emoji are not) → otherwise `invalid_nickname`. Listed profanity anywhere in
the name → `nickname_profanity`. A few reserved names (admin, system, moderator, ...) → `invalid_nickname`. The same rules are in
`:core` (`Nicknames.validate`) so the app can check before sending. Uniqueness is per `nickname#tag`; comparison ignores case.

## 10. Reconnect and resync

1. When the socket drops, the app reconnects with backoff (1 s, 2 s, 4 s, then every 5 s) — except after close codes 4000/4003/4004 or HTTP 401/403.
2. The seat is kept for **60 s** in every phase. In a running duel the clock keeps going (rounds can be missed).
3. On the new connection the server sends `welcome` (with `roomId` if the seat still exists), then `room_state` and
   `chat_history`. These replace all local room state; nothing needs to be replayed. If `roomId` is null the seat is gone
   (duel forfeited / removed from lobby) and the app returns to the home screen; the result is in the account stats.
4. A client that suspects it missed something can send `resync` at any time.
5. Quick-match queue entries do not survive a reconnect (8.1).

`resync` when not in a room: the server answers with `ack` (if `rid`) and nothing else; if you were queued, `queued` is re-sent.

## 11. Error codes

| Code | HTTP | Meaning |
| --- | --- | --- |
| `bad_request` | 400 | invalid body / parameters |
| `malformed_message` | – | WebSocket message is not valid JSON or has an unknown `type` / missing fields |
| `message_too_large` | 413 | body > 4 KB (REST) or message > 2048 bytes (WS) |
| `rate_limited` | 429 | too many requests / messages; see `retryAfterSeconds` / `retryAfterMs` |
| `not_found` | 404 | unknown path, player or report |
| `internal_error` | 500 | bug; retry later |
| `unsupported_version` | 400 | reserved for future version negotiation |
| `unauthorized` | 401 | missing or unknown token |
| `banned` | 403 | account banned |
| `invalid_nickname` | 400 | length, characters or reserved name |
| `nickname_profanity` | 400 | listed bad word in the name |
| `nickname_unavailable` | 409 | all 9999 tags of that nickname are taken |
| `invalid_code` | 400 | transfer code wrong, used or expired |
| `too_many_attempts` | 429 | too many wrong codes from this IP |
| `session_replaced` | – | a newer connection took over (then close 4000) |
| `room_not_found` | – | no room with that code |
| `room_full` | – | both teams are full |
| `team_full` | – | target team is full |
| `already_in_room` | – | you already have a seat (or are queued) |
| `not_in_room` | – | you have no seat |
| `not_host` | – | host-only action |
| `invalid_settings` | – | digits / teamSize / roundSeconds out of range, or teams larger than the new size |
| `match_in_progress` | – | room is playing or finished; join later |
| `wrong_phase` | – | action not allowed in the current room or duel phase |
| `already_queued` / `not_queued` | – | queue state mismatch |
| `invalid_secret` / `invalid_guess` | – | wrong length, non-digits or repeated digits |
| `secret_already_set` | – | secret can be set once per duel |
| `already_guessed` | – | one guess per round |
| `stale_round` | – | `guess.round` is not the current round |
| `not_in_duel` | – | you have no running duel |
| `chat_empty` / `chat_too_long` / `chat_rate_limited` | – | see §9 |
| `unknown_phrase` | – | unknown quick-phrase id |

## 12. Limits (summary)

| What | Limit |
| --- | --- |
| Digits / team size / timers | 3–6 / 1–5 per side / 0, 30, 60, 120 s (quick: 60 s) |
| Missed rounds in a row | 2 → forfeit |
| Disconnect grace | 60 s |
| Chat | 100 characters, 5 lines / 10 s, 50 lines history per room |
| WS message | 2048 bytes (error), 16 KB frame (close 1009), ~10 msg/s burst 30 |
| Connections | 1 per account (newest wins) |
| Account creation | 10 per IP per hour (and a global hourly cap) |
| Rename | 5 per account per day |
| Transfer code | 10 min, single use, 5 wrong tries per code, 10 failures per IP per 10 min |
| Reports | 10 per account per hour, note ≤ 500 chars |

## 13. Examples

Create an account:

```http
POST /api/v1/accounts
{"nickname":"Mina"}

201 {"token":"q3V...43 chars","account":{"id":"k2m9x4q1ab7p","nickname":"Mina","tag":"0420","stats":{"wins":0,"losses":0,"draws":0},"createdAt":1795000000000}}
```

A 1:1 quick match (client `>`, server `<`):

```text
< {"type":"welcome","protocol":1,"serverTime":1795000000000,"account":{...},"roomId":null}
> {"type":"queue_join","digits":3,"rid":1}
< {"type":"queued","digits":3,"since":1795000000100}
< {"type":"ack","rid":1}
< {"type":"room_state","room":{"id":"r_8f2k...","kind":"quick","code":null,"phase":"playing",
     "settings":{"digits":3,"teamSize":1,"roundSeconds":60},"hostId":null,"youId":"k2m9x4q1ab7p",
     "players":[{"id":"k2m9x4q1ab7p","nickname":"Mina","tag":"0420","team":"A","connected":true,"left":false},
                {"id":"z8...","nickname":"Leo","tag":"1234","team":"B","connected":true,"left":false}],
     "match":{"number":1,"duels":[{"index":0,"playerA":"k2m9x4q1ab7p","playerB":"z8...","phase":"choosing","round":0,"result":null}],
              "score":{"a":0,"b":0},"result":null,
              "myDuel":{"index":0,"myTeam":"A","opponentId":"z8...","phase":"choosing","round":0,"deadline":1795000060200,
                        "mySecret":null,"opponentHasSecret":false,"myPendingGuess":null,"opponentGuessed":false,
                        "myRounds":[],"opponentRounds":[],"myMisses":0,"opponentMisses":0,"result":null,"opponentSecret":null}},
     "version":3,"serverTime":1795000000200}}
> {"type":"secret","secret":"042"}
< {"type":"room_state", ... "myDuel":{... "mySecret":"042" ...}}
< {"type":"room_state", ... "phase":"guessing","round":1,"deadline":1795000065000 ...}
> {"type":"guess","guess":"123","round":1}
< {"type":"room_state", ... "myPendingGuess":"123" ...}
< {"type":"room_state", ... "round":2,"myRounds":[{"round":1,"guess":"123","strikes":0,"balls":1}], ...}
...
< {"type":"room_state", ... "myDuel":{"phase":"finished","result":{"winner":"A","reason":"solved"},"opponentSecret":"719",...},
     "result":{"winner":"A","decidedEarly":false} ...}
> {"type":"room_leave"}
< {"type":"room_left","roomId":"r_8f2k...","reason":"left"}
```

## 14. Web pages and other endpoints

| Path | What |
| --- | --- |
| `GET /health` | `200 {"status":"ok"}` for the host's health check |
| `GET /privacy` | privacy policy (HTML) |
| `GET /delete-account` | explains in-app deletion; form with a **deletion code** (from the app's Settings) and a confirmation checkbox |
| `POST /delete-account` | form fields `code`, `confirm=yes`; deletes the account at once when the code is valid; same IP limits as transfer codes |
| `GET /app-ads.txt` | `text/plain` content of the `APP_ADS_TXT` environment variable; 404 when unset |
| `/admin/v1/...` | moderation API, `Authorization: Bearer <ADMIN_TOKEN>`; disabled (404) when `ADMIN_TOKEN` is unset. See `backend/server/DEPLOY.md` |

Admin API: `GET /admin/v1/reports?status=open|resolved|all` → `AdminReportList`;
`POST /admin/v1/reports/{id}/resolve {resolution}`; `POST /admin/v1/accounts/{id}/ban {reason}` (closes live connections with 4003,
removes the player from rooms and the queue); `POST /admin/v1/accounts/{id}/unban`.
