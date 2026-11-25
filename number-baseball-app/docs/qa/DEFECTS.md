# Defect log

Owner: QA lead. Updated 2026-11-25 (phase 2: all open defects re-verified against the final protocol, code and the server scenarios). Test plan: [`TEST_PLAN.md`](TEST_PLAN.md).

## Format

Every defect has: **ID** (`DEF-NNN`, never reused), **Severity**, **Area**, **Status**, **Found by** (test id, corpus row or document),
**Steps**, **Expected**, **Actual**, and an **Owner** to act. Ambiguities and contradictions in `README.md`, `docs/protocol.md` or
design documents are defects too (spec defects), because two engineers can build incompatible behaviour from them.

| Severity | Meaning |
| --- | --- |
| Blocker | wrong game result, secret leak, account loss/takeover, crash on a main path, policy violation that blocks release |
| Major | feature wrong or unusable in a supported configuration, Play policy risk, spec gap that allows incompatible implementations of a core rule |
| Minor | workaround exists or limited impact; documentation gaps |
| Trivial | cosmetic, wording |

Status: **Open** → **Fixed** (code/doc changed) → **Verified** (QA test passes) → closed; or **Closed (spec)** when a later
document resolved it, **Rejected** with a reason. A confirmed code defect that a QA test catches is listed in
`backend/core/src/test/kotlin/app/numbaseball/core/qa/KnownDefects.kt` (core) or `backend/server/src/test/kotlin/app/numbaseball/server/qa/KnownServerDefects.kt` (server) while open (strict expected failure).

## Summary

| Severity | Open | Fixed / verified | Closed (spec) |
| --- | --- | --- | --- |
| Blocker | 0 | 0 | 0 |
| Major | 1 | 7 | 2 |
| Minor | 16 | 16 | 1 |
| Trivial | 5 | 1 | 1 |

| ID | Sev | Area | Status | Title | Owner |
| --- | --- | --- | --- | --- | --- |
| DEF-001 | Major | Team | Closed (spec) | Prototype early-decision rule (wins > N/2) misses decisions when draws exist | backend |
| DEF-002 | Major | Timer | Closed (spec) | Double forfeit (both miss twice) undefined in README | backend |
| DEF-003 | Major | Timer | Fixed | Untimed rooms: a connected idle player can stall a duel or lobby forever | product, backend |
| DEF-004 | Minor | Reconnect | Closed (spec) | Disconnect grace vs round timer interplay undefined in README | backend |
| DEF-005 | Minor | Stats | Fixed | A player who left still gets the team win; offline/bot games and stats not stated | product, backend |
| DEF-006 | Minor | Chat | Verified | Text symbols (♪ ★ ♥ © ™ ☺ ↔ ‼) were removed as "emoji" | backend |
| DEF-007 | Minor | Accounts | Fixed | protocol 9.3 nickname rules underspecified; stacked combining marks were accepted | backend |
| DEF-008 | Major | Accounts/Android | Fixed | Secure token storage vs Auto Backup: Keystore-encrypted token cannot be restored | android, tech lead |
| DEF-009 | Major | Deletion | Fixed | Web deletion without the app is in README but not in protocol.md; open design questions | backend |
| DEF-010 | Major | Transfer | Fixed | Transfer code: entropy, per-code attempt counting, old socket, replacing an existing account | backend, android |
| DEF-011 | Major | Protocol/Policy | Fixed | protocol.md lacks the policy-review requirements (terms_required, chat_not_allowed, restriction, retention, review room, pages) | backend |
| DEF-012 | Major | Server | Open | Server restart: in-flight matches, client message and "result is in stats" claim undefined/false | backend |
| DEF-013 | Trivial | Rooms | Closed (spec) | README "Private rooms only for team play" ambiguous | tech lead |
| DEF-014 | Minor | Quick match | Fixed | Queue entry dropped on reconnect contradicts "short reconnects survive an app switch" | backend, android |
| DEF-015 | Minor | Safety | Open | A blocked player can still join my private room by code; no host kick | product, backend |
| DEF-016 | Minor | Computer | Open | Computer levels not in README; Easy may be too strong for beginners | product |
| DEF-017 | Minor | Chat | Fixed | Chat cleaning details missing from protocol 9.1 (mask unit, mark cap, newline handling, bidi list) | backend |
| DEF-018 | Minor | Rooms | Open | No limit on wrong room-code join attempts (private room discovery) | backend |
| DEF-019 | Minor | L10N | Open | Locale fallback (zh-TW/HK, pt-PT, ...) unspecified | android, design |
| DEF-020 | Minor | Ads | Open | Ads details left open: cap persistence, practice/forfeit counting, small-window threshold | android, design |
| DEF-021 | Minor | Protocol | Fixed | `Resync` KDoc says `room_left` when not in a room; protocol 10 says `ack` only | backend |
| DEF-022 | Minor | Reconnect | Fixed | Seat after a disconnect forfeit in a running team match: 8.2 vs 10.3 contradict | backend |
| DEF-023 | Minor | Security | Open | Account tokens never expire and cannot be revoked except by a transfer | backend |
| DEF-024 | Trivial | Docs | Open | README "smaller Play Data safety declaration" outdated after ads | tech lead |
| DEF-025 | Trivial | Protocol | Open | `room_left{inactive}` is sent to a player who is disconnected (unreachable) | backend |
| DEF-026 | Minor | Practice | Open | Who may practise (README: room creator; protocol: client-side, anyone) and in quick-match search | product |
| DEF-027 | Minor | Accounts | Open | Account-creation limits: carrier NAT blocks real users; global cap lets one attacker stop all sign-ups | backend |
| DEF-028 | Minor | Safety | Fixed | Chat restriction scope (phrases too?) and "free text after one finished online match" not decided | product |
| DEF-029 | Trivial | Protocol | Open | ProtocolJson accepts duplicate keys (last wins) and quoted numbers | backend |
| DEF-030 | Minor | Timer | Fixed | Same-instant deadline vs disconnect expiry, and reconnect at exactly 60 s, not in protocol | backend |
| DEF-031 | Minor | Rules | Verified | `Rules.judge` accepted invalid input and returned nonsense | backend |
| DEF-032 | Minor | Timer | Fixed | protocol 8.3 "CHOOSING deadline start + T" reads as immediate when T = 0 | backend |
| DEF-033 | Trivial | Protocol | Fixed | `rid` "any positive number": 0 and negative accepted, behaviour unspecified | backend |
| DEF-034 | Minor | Protocol | Fixed | Error code for room/duel messages without a seat unspecified | backend |
| DEF-035 | Minor | Accounts | Verified | NFKC turned "ㅋㅋ" into conjoining jamo and composed "ㄱㅏ" into "가" | backend |
| DEF-036 | Trivial | Team | Open | Duels ending at the same instant vs early decision not specified | backend |
| DEF-037 | Minor | Chat | Verified | U+2800 braille blank survived chat cleaning (blank lines) | backend |
| DEF-038 | Major | Deletion/Security | Fixed | Code-less web deletion lets anyone delete inactive players; background requests may cancel it | backend, product |
| DEF-039 | Minor | Accounts | Verified | Half-width "ﾊﾞｶﾔﾛｳ" nickname passed the profanity check | backend |
| DEF-040 | Minor | Safety | Fixed | Message reports: "you are (or were) in it" undefined; message-id existence oracle | backend |
| DEF-041 | Major | Policy | Fixed | Audience 13+ vs minimum age 14 not confirmed by the owner; minors in free-text rooms need child-safety measures in force | owner, release lead |
| DEF-042 | Minor | Security | Open | Admin token requirements (length, constant-time compare, rate limit, no logging) unspecified | backend |
| DEF-043 | Minor | Safety | Open | Auto chat restriction can be triggered by 3 free throwaway accounts | backend, product |
| DEF-044 | Minor | Ads/Sound | Open | Ads mute: README "always muted" vs POLICY_REVIEW R11 "follow the sound setting" | tech lead |
| DEF-045 | Minor | Sound | Open | Haptics default unspecified; `design/sound/REPORT.md` (file rules) missing | design, sound |
| DEF-046 | Minor | Age | Open | Age band "on the device only" vs Auto Backup/device transfer; reinstall resets an under-age result | release lead, android |
| DEF-047 | Minor | Age/Ads | Open | Exact SDK settings per age band (child/teen treatment, birth-year rounding at the boundary) not documented | android, release lead |

| DEF-048 | Minor | Stats | Open | A forfeit against the review-room bot is recorded as a human loss (protocol 3.1/8.5: bot games never touch stats) | backend |
| DEF-049 | Minor | Protocol | Open | A rejected duel action bumps `room.version` and re-broadcasts `room_state` ("an error never changes state") | backend |
| DEF-050 | Trivial | Protocol | Open (spec) | Protocol 3.1 says `chat`/`phrase` answer `terms_required`; protocol 5 says `not_in_room` without a seat, which is always the case before terms | backend |

---

## Details

### DEF-001 — Major — Team — Closed (spec)
Found by: reading `../number-baseball/server.js` `checkMatch`; `QaOracleSelfTest.teamVerdictExamplesFromProtocol`.
Steps: 4v4, results A, A, draw, one pending. Expected: decided for A (2 > 0 + 1). Actual (prototype): not decided (needs 3 wins).
Resolution: protocol 8.4 defines `leader > other + unfinished`; core TeamMatch follows it (AC-TEAM-01/03 pass). Do not port the prototype rule.

### DEF-002 — Major — Timer — Closed (spec)
README said only "two missed rounds forfeit". Protocol 8.3 now defines `both_timeout` (draw) and the resolution order.

### DEF-003 — Major — Timer — Open
Found by: protocol 8.3 review; `DuelEngineQaTest.acTime08_noTimerMeansNoDeadlineEver`.
Steps: private room with `roundSeconds = 0`; opponent stays connected and never sets a secret or guesses.
Expected: a product rule ends the stall (e.g. 5 minutes idle → `timeout`, or the other player may claim the win), also for lobbies.
Actual: no deadline exists; the only exit is to forfeit (a loss). Rooms close only when nobody is connected for 10 minutes.
Re-verified 2026-11-25: **Fixed**. `MatchFlowQaTest.acRoom03_roundTimerChoicesAre30_60_120_600AndNothingElse`: online timers are 30/60/120/600 s only; `0` and other values are `invalid_settings`, so no untimed online room exists. (Offline `roundSeconds = 0` is client-only.)

### DEF-004 — Minor — Reconnect — Closed (spec)
Protocol 8.3: missed rounds keep counting while disconnected; whichever forfeit comes first applies.

### DEF-005 — Minor — Stats — Open
Protocol 3.1: "a player who left still gets the team result". Steps: 3v3, leave in round 1 while teammates win. Expected (QA): a
player who left counts a loss (or nothing). Actual: counts a win, which rewards quitting. Also state that vs-computer, practice and
review-room bot games never touch online stats (AC-STAT-02).
Re-verified 2026-11-25: **Fixed**. Leaver gets a loss: `acTeam11_aPlayerWhoLeftGetsALossEvenWhenTheTeamWins`; disconnect forfeit keeps the team result: `acTeam12`; stats once per match: `acStat01..03`. The remaining "bot games never touch stats" part is DEF-048 (server wrong).

### DEF-006 — Minor — Chat — Verified
Found by: `chat_emoji.tsv` E-117..E-140, `ChatFilterCorpusQaTest.acChat03`. ♪ (U+266A) and ★ (U+2605) are not emoji at all
(Extended_Pictographic only); ♥ © ™ ☺ ↔ ‼ are text presentation without U+FE0F. They were removed (chat_empty). Fixed by backend
the same day; test passes. Product should still confirm the definition in protocol 9.1 (README only names ^^ and :) ).

### DEF-007 — Minor — Accounts — Open (code part verified)
Protocol 9.3 does not state: length unit (code points), that invisible letters (Hangul fillers U+3164/U+115F/U+1160/U+FFA0) are
rejected, the compatibility-jamo exception to NFKC (DEF-035), the full reserved list (localized names, look-alikes such as
Cyrillic "Аdmin" — N-307 is EITHER), whether digits-only names ("1234#0420") are fine, mixed-script look-alikes. Code part:
`N-121` ("a" + three combining acutes + "b") was accepted although 9.3 says letters and digits only; fixed, now passes.
Re-verified 2026-11-25: **Fixed**. Protocol 9.4 now states code points, fillers, jamo exception, reserved names, digits. `AccountLifecycleQaTest.acAcc01_accountCreationNicknameRulesOverRest` runs the whole `nicknames.tsv` through `POST /accounts`; core `NicknameCorpusQaTest` green.

### DEF-008 — Major — Accounts/Android — Open
README: token stored and included in Auto Backup. If the app encrypts it with an Android Keystore key (EncryptedSharedPreferences
or similar), the key is not backed up, so on the new phone the restored ciphertext cannot be decrypted: the player silently gets a
new account (AC-BAK-01/04). Expected: a written decision (plain app-private file covered by backup encryption, or a
backup-specific scheme) and a device test. Owner: Android engineer + tech lead.
Re-verified 2026-11-25: **Fixed**. Decision written in README (plain private DataStore, no Keystore); no Keystore/EncryptedSharedPreferences use in `android/`. The device test AC-BAK-01/04 (Auto Backup restore on a real device) is still a manual item.

### DEF-009 — Major — Deletion — Open
README "Terms, deletion and review" requires a web deletion request without an in-app code (30-day pending, cancelled on use,
shown in the app); protocol 14 only has the code form. Missing: endpoint and fields, identical-response rule, how
`deletionRequestedAt` reaches the app, which requests count as "use" (a background `GET /me` must not silently cancel a
request the player made on purpose), and a test clock hook for 30-day/180-day time travel. Tests waiting:
`AccountLifecycleQaTest.acDel03`.
Re-verified 2026-11-25: **Fixed**. Replaced by the e-mail + admin route: no web form, no deletion code, no code-less request (`acDel02_operatorDeletesByHandleAfterAnEmailRequest`, `acDel03_thereIsNoWebFormNoDeletionCodeAndNoCodelessWebRequest`; corpus rows R-021/R-047/R-048 updated to 404). Test clock exists (`POST /admin/v1/test/clock`, used by many scenarios).

### DEF-010 — Major — Transfer — Open
Protocol 3.1: (a) the code alphabet/entropy is not stated (QA requires ≥ 60 bits); (b) "a code dies after 5 wrong attempts" cannot be
attributed to a code unless codes carry an account part — define how; (c) what the old device's live WebSocket receives when its
token is revoked (close code; must not auto-reconnect); (d) redeeming on a device that already has an account replaces it with no
warning and orphans the old account. Test: `ProtocolSecurityQaTest.acSec10`.
Re-verified 2026-11-25: **Fixed**. Protocol 3.1 defines 60-bit code (4 lookup + 8 secret), per-code attempts, `session_replaced` + close 4000 for the old device, and the app warning. Tests: `acSec10`, `acSec12`, `acSec13`. The app-side warning is an Android item.

### DEF-011 — Major — Protocol/Policy — Open
README and `POLICY_REVIEW.md` require, but protocol.md does not define: `terms_required`, `PUT /api/v1/me/terms`, terms version in
`welcome`/`GET /me`; quick-match free text refused with `chat_not_allowed` and the `QUICK_MATCH_FREE_CHAT` flag (protocol 9.1 even
says "Chat works in every room phase"); chat restriction (admin endpoint, automatic 3-reporter rule, error code shown to the
player); report retention (180 days) and snapshot anonymisation; review room; `/` and `/terms` pages. Server tests for these are
`@Ignore("waiting for protocol.md ...")`.
Re-verified 2026-11-25: **Fixed**. Protocol now defines terms, age confirmation, `chat_not_allowed`, restriction, retention, review room, pages. Tests: `acTerms01/02`, `acChat13/14`, `acSafe05/06`, `acDel04`, `acRev01..04`, `acPages01`. Residual spec nit: DEF-050.

### DEF-012 — Major — Server — Open
Rooms live in memory. Protocol 10.3 tells the app "the result is in the account stats" when `roomId` is null, which is false for a
match interrupted by a restart or crash. Expected: define restart behaviour (close 1001 / `room_left{closed}`, no result recorded,
a dedicated client message), and whether matches are drained before deploys. AC-SRV-01, AC-STAT-03.
Re-verified 2026-11-25: **Open**. Server part still wrong: a graceful stop does NOT send `room_left{closed}` and closes sockets abnormally (1006, not 1001), contradicting protocol 10.5. Known defect: `acSrv01_gracefulRestartTellsPlayersWithRoomLeftClosedAndClose1001`. Verified OK: after a restart `welcome.roomId` is null, interrupted matches record nothing, accounts survive (`acSrv02`).

### DEF-013 — Trivial — Rooms — Closed (spec)
Protocol clarifies: quick match is 1v1; private rooms support team sizes 1–5.

### DEF-014 — Minor — Quick match — Open
Protocol 8.1 drops the queue entry when the socket drops; README promises short reconnects survive an app switch. The player loses
queue position on every app switch. Expected: keep the entry for a short grace (e.g. 15 s) or document the trade-off; the app must
re-queue automatically (AC-QM-04).
Re-verified 2026-11-25: **Fixed**. Trade-off documented in protocol 8.1 (queue entry dropped with the connection; the app re-sends `queue_join`). Behaviour verified by `acQm04`, `acQm06`.

### DEF-015 — Minor — Safety — Open
Block affects chat and quick match only (3.2). A blocked player who knows my room code can join and play against me; hosts cannot
remove players. Expected: refuse `room_join` across a block (either direction) or give hosts a kick in the lobby.
Re-verified 2026-11-25: **Open**. `Hub.roomJoin` has no block check and there is no host kick; protocol 3.2 only covers chat and quick match. No test (the spec does not require it); needs a product decision.

### DEF-016 — Minor — Computer — Open
README names "vs Computer" but not levels. Measured (seeded): Easy needs 7.1–8.0 guesses on 3 digits, Hard 5.0 — Easy may beat most
beginners. Expected: product sets level targets; playtest AC-CPU-08.
Re-verified 2026-11-25: **Open**. README still has no computer levels (protocol 16 lists EASY/NORMAL/HARD); product decision pending.

### DEF-017 — Minor — Chat — Open
Protocol 9.1 should state what the core does: line breaks and tabs become spaces; at most 2 stacked combining marks; bidi controls
and blank fillers are removed; "one `*` per character" means per code point (current core) or per grapheme. QA tests accept both
mask units until decided.
Re-verified 2026-11-25: **Fixed**. Protocol 9.1 now lists the cleaning steps and the mask unit (one `*` per character). `ChatSafetyQaTest.acChat11` (corpora through a real room == core `ChatFilter.clean`), `acChat15`.

### DEF-018 — Minor — Rooms — Open
5 characters of a 32-symbol alphabet = 33.5 M codes. With 1 000 live rooms an attacker at the WS rate limit (10 msg/s) finds a room
about every 55 minutes. Expected: limit failed `room_join` per account/IP (e.g. 20 per 10 minutes) (AC-ROOM-10).
Re-verified 2026-11-25: **Open**. Confirmed in code: failed `room_join` is limited only by the generic WebSocket rate (10 msg/s); no per-account/IP limit. Protocol 12 lists none.

### DEF-019 — Minor — L10N — Open
Only zh-CN is supported; zh-TW/zh-HK, pt-PT, es-419, fr-CA, de-CH and unsupported locales need a defined fallback (AC-L10N-04).
Re-verified 2026-11-25: **Open**. README/protocol still define no locale fallback (zh-TW/HK, pt-PT, ...).

### DEF-020 — Minor — Ads — Open
Still open after README/POLICY_REVIEW R11: does the interstitial cap (1 per 3 finished matches, 4 minutes) persist across restarts
(QA: yes); does a forfeited/abandoned vs-computer game count as "finished"; practice games never count (QA: never); the window size
below which banners hide (QA proposal: height < 480 dp or width < 360 dp); the minimum supported window for the duel screen.
Re-verified 2026-11-25: **Open**. README fixes the cap (1 per 3 finished matches, 4 minutes) but not persistence, forfeit counting or the small-window threshold.

### DEF-021 — Minor — Protocol — Open
`Messages.kt` `Resync` KDoc: "(or `room_left` if not in a room)". Protocol 10: "`resync` when not in a room: the server answers with
`ack` (if `rid`) and nothing else". One of them is wrong.
Re-verified 2026-11-25: **Fixed**. `Messages.kt` KDoc now matches protocol 5 (`ack` only when idle); `acQm05_queueStatesAndResync` verifies it.

### DEF-022 — Minor — Reconnect — Open
8.2: "Disconnected players keep their seat; the duel engine handles grace and forfeits." 10.3: "If `roomId` is null the seat is gone
(duel forfeited ...)". In a team match that continues after my duel was lost by disconnect, do I get `roomId` and the scoreboard
when I come back?
Re-verified 2026-11-25: **Fixed**. Protocol 8.2/10.2 now agree (seat kept while the match runs). `acTeam13_aPlayerWhoLostHisDuelByDisconnectComesBackToARunningMatch`.

### DEF-023 — Minor — Security — Open
Tokens have no expiry or rotation and there is no "sign out other devices" except a transfer. A leaked token (backup, rooted phone)
stays valid forever. Expected: document the threat model or rotate on use.
Re-verified 2026-11-25: **Open**. Tokens still never expire; only a transfer revokes. No threat-model note in protocol.

### DEF-024 — Trivial — Docs — Open
README Accounts "Why: … smaller Play Data safety declaration" is outdated now that AdMob collects the advertising ID.
Re-verified 2026-11-25: **Open**. README Accounts "Why:" still says "smaller Play Data safety declaration".

### DEF-025 — Trivial — Protocol — Open
`room_left{reason:"inactive"}` is defined for a player "disconnected longer than the grace period" — a disconnected client cannot
receive it; on reconnect it only sees `welcome.roomId = null`.
Re-verified 2026-11-25: **Open**. Protocol 6 still lists `room_left{inactive}`; it cannot reach a disconnected client.

### DEF-026 — Minor — Practice — Open
README: "The room creator can practise"; protocol 8.2: client-side, so any waiting player can. Also unspecified for the quick-match
searching screen.
Re-verified 2026-11-25: **Open**. README still says "The room creator can practise" while protocol 8.2 says client-side for any waiting player.

### DEF-027 — Minor — Accounts — Open
10 creations per IP per hour blocks real users behind carrier-grade NAT (common on mobile networks); the "global hourly cap" lets
one attacker stop all new sign-ups. Expected: per-device proof or higher per-IP burst with abuse scoring; the app must keep offline
play and retry (AC-ACC-06).
Re-verified 2026-11-25: **Open**. Limits unchanged (10/IP/h, 300/h total; `acSec08` verifies them). Design risk, product decision pending.

### DEF-028 — Minor — Safety — Open
Does a chat restriction also block quick phrases? POLICY_REVIEW R4/R10 add "free text only after one finished online match"; README
does not. Decide and add to protocol (DEF-011).
Re-verified 2026-11-25: **Fixed**. Protocol 9.1/5: a restricted account gets `chat_restricted` for text AND phrases; the "free text after one finished match" idea was not adopted. `acSafe05` asserts phrases are refused too.

### DEF-029 — Trivial — Protocol — Open
`{"type":"ping","type":"queue_leave"}` decodes as `queue_leave`; `"digits":"7"` decodes as 7 (protocol_frames T-004, T-008).
Harmless while both sides use ProtocolJson; document or reject duplicates.
Re-verified 2026-11-25: **Open**. Duplicate keys and quoted numbers are still accepted (tolerated rows T-004/T-007 pass only as "tolerated"); protocol 1 does not mention it.

### DEF-030 — Minor — Timer — Open
The core resolves a round deadline before a disconnect expiry at the same millisecond, and a reconnect at exactly 60 000 ms is
too late. Both are sensible and tested (AC-RECON-01/03) but not in protocol 8.3.
Re-verified 2026-11-25: **Fixed**. Protocol 8.3 now states both rules (round resolves first on the same millisecond; reconnect at exactly 60 000 ms is too late). Core `DuelEngineQaTest` (AC-RECON-01/03); server `acRecon09/10` cover the 60 s window.

### DEF-031 — Minor — Rules — Verified
`Rules.judge("12a","123")`, `("112","123")`, `("123","1 3")` returned results instead of failing. Fixed (now `require`s valid
input); `JudgeDifferentialQaTest.acRul07` passes.

### DEF-032 — Minor — Timer — Open
Protocol 8.3: "CHOOSING has deadline start + T" with T = 0 literally means an immediate deadline (random secrets, a miss). The core
treats 0 as "no timer"; say so in the doc.
Re-verified 2026-11-25: **Fixed**. Online `roundSeconds = 0` is invalid (`acRoom03`); protocol 16 states 0 = no timer offline only.

### DEF-033 — Trivial — Protocol — Open
`rid` is "any positive number" but 0 and negative values are accepted (T-009, T-010). Define.
Re-verified 2026-11-25: **Fixed**. Protocol 5 types `rid` as `Long?` without "positive"; T-009/T-010 pass as tolerated (`acProto10_malformedAndInvalidFramesAnyState`).

### DEF-034 — Minor — Protocol — Open
For `secret`/`guess`/`forfeit` without a seat the table lists `not_in_duel`; for `room_settings`, `team_set`, `room_reset` without
a seat no error is listed. QA accepts `not_in_duel|not_in_room` (protocol_frames V-008..V-015) until defined.
Re-verified 2026-11-25: **Fixed**. Protocol 5 now lists `not_in_room` for every seat-bound message. `acProto13_seatBoundMessagesWithoutASeatAnswerNotInRoom`; corpus rows V-007..V-015.

### DEF-035 — Minor — Accounts — Verified
NFKC maps Hangul compatibility jamo to conjoining jamo: "ㅋㅋ" was stored as U+110F U+110F, and "ㄱㅏ" was composed into "가" (then
"too short"). Found by N-016 / N-032. Fixed in `Nicknames.normalize`; both pass.

### DEF-036 — Trivial — Team — Open
If two duels end at the same server instant, the core evaluates them together before deciding (AC-TEAM-04, sensible). Protocol 8.4
should say so.
Re-verified 2026-11-25: **Open**. Protocol 8.4 still silent about duels ending at the same instant vs early decision.

### DEF-037 — Minor — Chat — Verified
"⠀⠀" (U+2800 braille blank) was accepted as a chat line that looks empty (S-021, fuzz). Fixed; passes.

### DEF-038 — Major — Deletion/Security — Open
The code-less web request needs only a public handle (`Nickname#1234`, shown in every room). Anyone can file requests for every
player they meet; players who do not open the app for 30 days lose their account and stats to a stranger. Conversely, automatic
background requests from an installed app could cancel a request the owner made on purpose. Expected: require the in-app code when
the app is available, treat code-less requests as needing a contact channel or manual review, cancel only on explicit use, and keep
the identical-response rule (AC-DEL-03/04).
Re-verified 2026-11-25: **Fixed**. The code-less web request no longer exists (`acDel03`).

### DEF-039 — Minor — Accounts — Verified
"ﾊﾞｶﾔﾛｳ" (half-width; NFKC "バカヤロウ") was accepted as a nickname while "ばかやろう"/"バカヤロー" are listed (N-425). Fixed; passes.

### DEF-040 — Minor — Safety — Open
Protocol 3.3: a snapshot is stored "if you are (or were) in it" — how is past membership known after a seat is freed? A report
naming a `roomId`/`messageId` of a room the reporter never joined must not create a snapshot and should not reveal whether the
message exists (`ProtocolSecurityQaTest.acSec06`).
Re-verified 2026-11-25: **Fixed**. Protocol 3.3 clarified; a stranger gets 201 with no snapshot and the same answer for real and unknown message ids. `acSec06_messageReportCannotTargetARoomTheReporterWasNeverIn`; `acSafe02` (wrong sender -> 400).

### DEF-041 — Major — Policy — Open
Owner decided audience 13+; POLICY_REVIEW R19 recommends a minimum age of 14 (Korean PIPA guardian consent) and the README records
it as "owner to confirm" (build config default 14). Until confirmed, Terms, listing and the age screen may disagree (AC-AGE-09).
With 14–17 users, free-text chat with strangers in private rooms needs the child-safety standards in force (published standards
prohibiting CSAE, in-app reporting, contact — AC-TERMS-04, POL-03). R20/R21 (ad rating = IARC rating, under-16 treatment, Play
Age Signals) are now covered by AC-ADS-14 and AC-AGE-01..09. Earlier QA text that said "max ad rating T" is superseded.
Re-verified 2026-11-25: **Fixed**. README records the owner decision: minimum age 14, age confirmation enforced by the server (`acTerms01`, `acTerms02`), `/terms#child-safety` present (`acPages01`). The Android age screen is not covered here.

### DEF-042 — Minor — Security — Open
Admin API (protocol 14) does not require a minimum `ADMIN_TOKEN` length, constant-time comparison, rate limiting, or keeping it
out of logs and URLs.
Re-verified 2026-11-25: **Open**. Reduced scope. Verified: token >= 16 characters (config), constant-time compare, wrong/real admin token never in logs (`acSec11`), 404 without a token (`acSec09`). Still missing: rate limiting of failed admin attempts.

### DEF-043 — Minor — Safety — Open
"3 different players within 24 h" can be met by one person with three free device accounts. Expected: count only reporters who
shared a room with the target recently and have at least one finished online match; keep the restriction "pending review".
Re-verified 2026-11-25: **Open**. Characterised by `ChatSafetyQaTest.acSafe07`: three throwaway accounts that never shared a room restrict a player (protocol 3.3 accepts it). Needs a product decision.

### DEF-044 — Minor — Ads/Sound — Open
README (Monetization and Sound): ads muted app-wide / "Ads stay muted". POLICY_REVIEW R11: keep ads muted while sound is off,
follow the setting when the player turns sound on. QA tests README (AC-ADS-13) until the conflict is resolved.
Re-verified 2026-11-25: **Open**. README ("always muted") vs POLICY_REVIEW R11 ("follow the sound setting") still conflict.

### DEF-045 — Minor — Sound — Open
README Sound does not give the haptics default, and `design/sound/REPORT.md` (loudness, peak, silence rules for AC-SND-09) does not
exist yet; QA uses default thresholds marked † in TEST_PLAN 5.26.
Re-verified 2026-11-25: **Open**. `design/sound/REPORT.md` now exists; the haptics default is still unspecified in README.

### DEF-046 — Minor — Age — Open
README: the age band is "kept on the device only". Open: is it part of the Auto Backup / device-to-device settings file (then it
leaves the device, encrypted, and comes back on a new phone — arguably fine), and what happens on reinstall or "clear data" (an
under-age player can answer again; QA accepts this as the limit of a device-only design but it should be written down).
AC-AGE-03/04.
Re-verified 2026-11-25: **Open**. Age band vs Auto Backup / reinstall still undecided in README.

### DEF-047 — Minor — Age/Ads — Open
POLICY_REVIEW R20 names the bands "CHILD" (14–15) and "TEEN" (16–17) but not the exact Google Mobile Ads / UMP calls and request
flags per band, nor how a birth year maps to an age at the minimum-age boundary (a player born in the boundary year may still be
under 14). QA needs both to check outgoing ad requests (AC-AGE-02/05).
Re-verified 2026-11-25: **Open**. Exact SDK flags per age band and boundary-year rule still undocumented.

### DEF-048 — Minor — Stats — Open
Found by: `AccountLifecycleQaTest.acRev03_botGamesRecordNoStatsForAnyone` (known defect).
Steps: new account with terms, `room_join` with the review code, `secret`, `forfeit`; `GET /api/v1/me`.
Expected: stats `0/0/0` (protocol 3.1 and 8.5: "Bot games record no stats for anyone").
Actual: `losses = 1`. `Hub.afterMatchChange` only skips the bot's own seat, not the human's result when the room has a bot (`room.bot != null`). Same would give wins/draws.
Owner: backend (skip `recordResults` when `room.bot != null`).

### DEF-049 — Minor — Protocol — Open
Found by: `ProtocolSecurityQaTest.acProto15_aRejectedDuelActionChangesNothing` (known defect), corpus rows V-301..V-318, V-401..V-408.
Steps: seated in a running duel, send `secret` "12" (or `guess` with a stale round), compare `room.version` from `resync` before and after.
Expected: version unchanged and no `room_state` (protocol 5: "An error never changes state"; corpus note "a following resync shows the same room version").
Actual: version +1 and a `room_state` is broadcast to everyone in the room (`Hub.duelAction` calls `broadcast` before rejecting, "timers may have fired on the way"). Harmless for correctness, but it lets a client spam all room members with snapshots and breaks the stated rule.
Owner: backend (broadcast only if the engine state or timers actually changed).

### DEF-050 — Trivial — Protocol — Open (spec)
Found by: `ChatSafetyQaTest.acTerms01` (asserts `not_in_room`).
Protocol 3.1: "Until then `queue_join`, `room_create`, `room_join`, `chat` and `phrase` answer `terms_required`." Protocol 5: every seat-bound message, `chat` and `phrase` included, answers `not_in_room` without a seat. An account without accepted terms can never get a seat, so `terms_required` for `chat`/`phrase` is unreachable (only after a terms-version change while seated, which needs a restart that drops rooms). The server follows protocol 5. Expected: say so in 3.1.
Owner: backend (doc).
