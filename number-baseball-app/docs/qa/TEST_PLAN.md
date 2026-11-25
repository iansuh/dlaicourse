# Number Baseball (Android) — QA Test Plan

Owner: QA lead. Status: **v0.3, phase 1** (2026-11-21). Inputs: `README.md` (product decisions incl. Ads, Sound, Terms/deletion,
owner decisions of 2026-11-21), `docs/protocol.md` v1 draft, `docs/play/POLICY_REVIEW.md`, `:core` code as of today.
Defects: [`DEFECTS.md`](DEFECTS.md). Adversarial data: [`corpora/`](corpora/).

Owner decisions that change testing (2026-11-21): **audience 13 and over** with a **minimum age from build config, default 14**
(POLICY_REVIEW R19, owner to confirm), a neutral **age screen** before any ad or account, age band kept on the device, AdMob
**max ad content rating = the app's IARC rating** (expected Everyone → **G**, from build config; R20), under-16 child/under-age ad
treatment, **Play Age Signals** (R21), **worldwide distribution** (EEA/UK/CH consent rules apply), **localized app name** in
resources ("숫자야구" style per language), **sound** (effects on, music off by default).

---

## 1. Scope and quality bar

In scope: rules engine, computer opponent, offline play, online quick match, private rooms, team play, chat and safety tools,
device accounts (creation, backup restore, transfer, deletion in app and on the web), terms gate, stats, ads and consent, sound,
localization (10 languages), accessibility, window sizes, network resilience, server operations, security, Play policy.
Out of scope for v1: Play Games sign-in, billing, iOS.

**Release gates** (all must hold):
1. No open **Blocker** or **Major** defect; no Blocker/Major id left in `KnownDefects.OPEN`.
2. Every **P1** acceptance criterion passes on the "core" rows of the device matrix (section 9); P2 ≥ 95 % pass with the rest triaged.
3. All non-functional budgets (section 6) met on the reference low-end device.
4. Play pre-launch report: 0 crashes, 0 ANRs, accessibility warnings triaged; closed test with ≥ 12 opted-in testers for 14 consecutive days.
5. Security checks (section 7) and Play policy checks (section 8) signed off; Data safety answers match captured traffic.

## 2. Risk-based strategy

| # | Risk | Likelihood | Impact | Main mitigation |
| --- | --- | --- | --- | --- |
| R1 | Opponent secret leaks before the duel ends (frames, snapshots, logs, errors) | Medium | Blocker | Frame scan in every server scenario (AC-SEC-05), code review of `RoomView` builders |
| R2 | Wrong judging or wrong match/team result (draws, early decision, timeouts, disconnects) | Low | Blocker | Independent oracle + exhaustive/random differential tests (sections 12, AC-RUL/TEAM/TIME) |
| R3 | Timer, miss and disconnect races (same-instant events, reconnect at the boundary) | High | Major | Duel oracle with boundary-hitting random scripts, explicit boundary ACs |
| R4 | Chat filter: profanity gets through (evasions) or innocent text is masked (traps), emoji ban too wide/narrow | High | Major (UGC policy) | 480+ row multilingual corpora, fuzzing, invariants |
| R5 | Account loss: Auto Backup restore fails, transfer/deletion races, token leaks | Medium | Blocker | AC-BAK/XFER/DEL, DEF-008 decision, device tests |
| R6 | Play rejection: terms gate, deletion without app, UGC tools, ads placement, Data safety mismatch, target audience 13+ | Medium | Blocker | Section 8 checklist, traffic capture, review room |
| R7 | Ads during play, accidental clicks, consent mishandled in EEA/UK | Medium | Major (policy + spirit) | Screen inventory test, UMP debug geography, frequency-cap decision table |
| R8 | Short reconnects do not survive an app switch; state lost on process death | High | Major | AC-RECON, process-death charter, backgrounding tests |
| R9 | Small windows / large fonts / long translations break the duel screen | High | Major | Screenshot tests (Roborazzi) per size × font × locale, AC-WIN/FONT/L10N |
| R10 | Server restart loses rooms silently; stats inconsistent | Medium | Major | AC-SRV, DEF-012 |
| R11 | Sound is loud, plays in silent mode or over calls, or ads make noise | Medium | Major (spirit) | AC-SND, audio-focus tests, file checks |
| R12 | Abuse: report brigading, block evasion, room-code brute force, flooding | Medium | Major | Rate-limit and abuse ACs, DEF-018/043 |

Effort follows risk: R1–R4 get exhaustive automated tests; R5–R9 get device tests plus charters; R10–R12 get targeted tests.

## 3. Test levels, location and how to run

| Level | What | Where | Run |
| --- | --- | --- | --- |
| Unit / differential (core) | rules, AI, Duel, TeamMatch, filters, protocol JSON vs QA oracle and corpora | `backend/core/src/test/kotlin/app/numbaseball/core/qa/` | `cd backend && ./gradlew test` |
| Server black-box | REST + WebSocket scenarios written against `protocol.md` with JDK clients; they start the real server module in-process on a free port (`QaServer.start`, relaxed limits + `TEST_HOOKS` clock) | `backend/server/src/test/kotlin/app/numbaseball/server/qa/` | `cd backend && ./gradlew :server:test` (all green; open server defects are listed in `KnownServerDefects.OPEN`). Against an external test server: `NB_QA_BASE_URL=http://localhost:8080 NB_QA_ADMIN_TOKEN=... ./gradlew :server:test` (tests needing extra instances are skipped) |
| Android unit / Robolectric / Roborazzi | view models, ads gating logic, sound settings, screenshots per size/font/locale | `android/app/src/test/` (QA cases to be added next to the Android engineer's tests) | locally: `cd android && ./gradlew :app:testDebugUnitTest` (SDK at `/opt/android-sdk`) |
| Instrumented / device | TalkBack, split-screen, audio focus, backup restore, UMP | `android/app/src/androidTest/` + manual | emulator/device; Firebase Test Lab optional |
| Exploratory | charters (section 10) | session notes in `docs/qa/sessions/` | timeboxed |
| Policy / traffic | Data safety vs traffic capture, pre-launch report | manual + checklist | before each release |

3.4 Server black-box tests use `RELAXED_RATE_LIMITS=true` and `TEST_HOOKS=true` (in-process by default). Time-dependent scenarios (round timers 30/60/120/600 s, 60 s reconnect window, 30-minute lobby clean-up, 24 h chat restriction, 10-minute transfer codes, hourly limits) run on their own instance and move the server clock with `POST /admin/v1/test/clock?advanceMs=N` instead of waiting. Strict-limit scenarios start an instance with `relaxed = false`; the privacy scan reads the captured server log and the instance's SQLite file.

## 4. Conventions

- AC ids: `AC-<AREA>-<NN>`; tests carry the id in their name (`acTeam03_...`). Priority **P1** (release-blocking), **P2**, **P3**.
  Automation: **U** core unit, **S** server black-box, **A** Android automated, **M** manual/device, **C** charter.
- Given/When/Then is written compactly: *Given …, when …, then …*.
- **Known-defect policy**: a QA assertion is never weakened. A failure caused by a logged defect is listed in
  `KnownDefects.OPEN` (strict: the test fails on any other failure and fails again when the defect is fixed until the entry is removed).
- Corpus rows are test data with ids (`P-EN-011`, `N-208`, …); a failing row is reported by id.

---

## 5. Acceptance criteria

### 5.1 Rules and judging (RUL)
- **AC-RUL-01** (P1, U) Given any 3-digit secret and guess (all 518 400 pairs), when judged, then strikes/balls equal the QA oracle.
- **AC-RUL-02** (P1, U) Given 300 000 random pairs each for 4, 5, 6 digits, when judged, then results equal the oracle.
- **AC-RUL-03** (P1, U) Given a fixed 3-digit secret, when all 720 guesses are judged, then the answer distribution is exactly 0S0B 210, 0S1B 252, 0S2B 63, 0S3B 2, 1S0B 126, 1S1B 42, 1S2B 3, 2S0B 21, 3S0B 1.
- **AC-RUL-04** (P1, U) Given a guess equal to the secret, then it is "solved"; given no common digit, then it is "out" (0S0B) and the UI shows OUT.
- **AC-RUL-05** (P1, U+S) Given a value with wrong length, a non-ASCII-digit (full-width, Arabic-Indic, Devanagari, mathematical digits), a space, a sign or a repeated digit, when validated as secret or guess, then it is rejected (`invalid_secret` / `invalid_guess` on the server); 0 may lead.
- **AC-RUL-06** (P1, U) Random numbers (computer secrets, timeout secrets) are valid and uniform; 0 leads in about 10 %.
- **AC-RUL-07** (P2, U) Given invalid input, when `Rules.judge` is called, then it throws instead of returning a result (fixed DEF-031).
- **AC-RUL-08** (P1, A) Given any hint shown in the app, then the on-screen S/B/O and the history row match the oracle for that guess (property test over the view model).

### 5.2 Computer opponent (CPU)
- **AC-CPU-01** (P1, U) Hard solves every 3-digit secret in ≤ 7 guesses, mean ≤ 5.3 (measured 5.03 / 7).
- **AC-CPU-02** (P1, U) Per level/digits, mean and worst guesses stay within budget (Hard 4/5/6: ≤ 6.2/7.2/8.2 mean, ≤ 9/11/13 worst; Normal 4/6: ≤ 7.5/10.0; Easy 3/4/6: ≤ 12/20/40). A weaker computer is a regression.
- **AC-CPU-03** (P1, U) Levels are ordered: Easy needs > 1.0 more guesses on average than Normal, Normal > 0.3 more than Hard (3–5 digits).
- **AC-CPU-04** (P1, U) Every computer guess is a valid number and never repeats within a game; every game ends.
- **AC-CPU-05** (P2, U) A seeded game repeats exactly (reproducible bug reports).
- **AC-CPU-06** (P2, U) Given inconsistent answers (buggy referee), the computer keeps making valid new guesses and never throws.
- **AC-CPU-07** (P2, U) Computer secrets are valid, varied, and can start with 0.
- **AC-CPU-08** (P3, M) Given a first-time player on Easy (3 digits), when 10 games are played, then the player wins at least 4 (playtest; measured Easy mean 7–8 guesses may be too strong, DEF-016).

### 5.3 Offline match flow (OFF)
- **AC-OFF-01** (P1, A) Given no network and no account, when the player starts vs-computer, then digits 3–6 and level can be chosen and the game is fully playable.
- **AC-OFF-02** (P1, A) Given secret entry, when the keypad is used, then a digit already used is disabled, the length is enforced, and submit is enabled only for a valid number.
- **AC-OFF-03** (P1, A) Given a guess is submitted, then the computer's move is computed off the main thread (no frame > 32 ms) and both hints appear together (simultaneous rounds).
- **AC-OFF-04** (P1, A) Given both solve in the same round, then the result is a draw; given only one, that side wins; the computer's secret is revealed on the result screen.
- **AC-OFF-05** (P2, A) Given rotation, resize or a theme change mid-game, then secret, history, memo marks and input are kept.
- **AC-OFF-06** (P2, M) Given process death mid-game (Don't keep activities), when the app returns, then the game is restored or a clear "game ended" notice appears; never a crash or a blank board.
- **AC-OFF-07** (P2, A) Forfeit asks for confirmation and counts as a finished match (for the interstitial cap only, not stats).
- **AC-OFF-08** (P2, A) Memo board marks (excluded/confirmed per digit and position) persist through the game and reset on rematch.

### 5.4 Quick match (QM)
- **AC-QM-01** (P1, S) Two players queued with the same digits are paired into a quick room: teamSize 1, 60 s timer, no code, no host, earlier player on team A.
- **AC-QM-02** (P1, S) Players queued with different digits are never paired.
- **AC-QM-03** (P1, S) Blocked pairs are never paired, whichever side blocked.
- **AC-QM-04** (P1, S) A queue entry is dropped when the connection drops; after reconnect the app re-sends `queue_join` if the user is still searching (DEF-014).
- **AC-QM-05** (P1, A) The searching screen shows elapsed time and Cancel; Cancel sends `queue_leave` and returns home; no ad on this screen.
- **AC-QM-06** (P1, S+A) In a quick room free text is refused with `chat_not_allowed` and quick phrases are delivered; with server flag `QUICK_MATCH_FREE_CHAT` on, free text works (README Chat safety).
- **AC-QM-07** (P2, S) `queue_join` while queued → `already_queued`; while in a room → `already_in_room`; `queue_leave` when not queued → `not_queued`.

### 5.5 Private rooms (ROOM)
- **AC-ROOM-01** (P1, S) Creating a room returns a 5-character code from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`; joining accepts lower case, spaces and dashes.
- **AC-ROOM-02** (P1, S) The room starts automatically and immediately when both teams have teamSize players; pairs are drawn at random.
- **AC-ROOM-03** (P1, S) Host-only actions (`room_settings`, moving others, `room_reset`) by others → `not_host`; in the wrong phase → `wrong_phase`.
- **AC-ROOM-04** (P1, S) Host leaves → the earliest remaining joiner becomes host; a room with nobody left is deleted.
- **AC-ROOM-05** (P1, S) Joining a playing or finished room → `match_in_progress`; a full room → `room_full`; unknown code → `room_not_found`.
- **AC-ROOM-06** (P1, S) Settings outside digits 3–6, teamSize 1–5, roundSeconds {0, 30, 60, 120} → `invalid_settings`, including when a team already has more players than the new size.
- **AC-ROOM-07** (P2, S) `room_reset` (host, finished) returns to lobby with the same settings, removes players who left, and restarts at once if teams are full.
- **AC-ROOM-08** (P2, S) Lobby seats of players disconnected > 60 s are freed; a room with no connected player for 10 min is closed and connected players get `room_left{closed}`.
- **AC-ROOM-09** (P2, A) Share/copy code works; the code is shown large and readable without ambiguous characters.
- **AC-ROOM-10** (P2, S) Join attempts with wrong codes are rate-limited per account (room-code brute force, DEF-018).

### 5.6 Team play (TEAM)
- **AC-TEAM-01** (P1, U) For team sizes 1–5, every finish order and every combination of duel results (A, B, draw) gives the oracle's result after each duel (29 160 sequences for 5v5).
- **AC-TEAM-02** (P1, U) 1 win + 1 loss + 1 draw in 3v3 is a team draw; a 1v1 draw is a draw.
- **AC-TEAM-03** (P1, U) As soon as leader > other + unfinished, the match is decided early; unfinished duels end `cancelled` with no winner, `decidedEarly = true` (e.g. 4v4: A, A, draw → decided).
- **AC-TEAM-04** (P1, U) Duels ending at the same instant are evaluated together (2:1 with nothing open is not "early"; no real result is overwritten by `cancelled`) (DEF-036).
- **AC-TEAM-05** (P1, U) After the decision, actions on cancelled duels are refused (`wrong_phase`) and the result never changes.
- **AC-TEAM-06** (P1, S) Cancelled duels reveal both secrets to their two players only; other duels are visible only as summaries (no guesses, no secrets) (AC-SEC-05).
- **AC-TEAM-07** (P1, S) Every participant, including players who left, gets the team result in stats (protocol 3.1; DEF-005 questions this).
- **AC-TEAM-08** (P2, A) The team scoreboard updates live and shows each duel's state; the player's own duel stays primary on small screens.

### 5.7 Practice while waiting and the switch to the real match (PRAC)
- **AC-PRAC-01** (P1, A) Given a player waiting in a private lobby, when Practice is tapped, then a local vs-computer game starts with the room's digit count and sends no game traffic to the server.
- **AC-PRAC-02** (P1, A) Given practice is running, when the room fills (room_state phase playing arrives), then the app switches to the real secret entry within 1 s, shows a clear "Match started" notice, and discards the practice game (including half-typed input).
- **AC-PRAC-03** (P1, A) Practice results never change stats, never count toward the interstitial cap, and never show an ad.
- **AC-PRAC-04** (P2, A) If the room never fills, practice can be played repeatedly; leaving the room ends practice.
- **AC-PRAC-05** (P2, A) A pending interstitial or dialog is cancelled when the real match starts.

### 5.8 Round timer and forfeits (TIME)
- **AC-TIME-01** (P1, U) A round resolves as soon as both have guessed; the next deadline is resolution time + T.
- **AC-TIME-02** (P1, U) Solving beats the opponent's timeout in the same round.
- **AC-TIME-03** (P1, U) Both solving in the same round is a draw (`both_solved`).
- **AC-TIME-04** (P1, U) Two missed rounds in a row lose (`timeout`); both at two misses in the same round → draw (`both_timeout`).
- **AC-TIME-05** (P1, U) A guess resets that player's miss counter.
- **AC-TIME-06** (P1, U) Choosing timeout assigns a random valid secret (shown to its owner) and counts as one missed round.
- **AC-TIME-07** (P1, U) roundSeconds 0 means no deadline in any phase (DEF-032 asks the doc to say so for CHOOSING).
- **AC-TIME-08** (P1, U) Error codes: `invalid_secret`, `secret_already_set`, `invalid_guess`, `stale_round`, `already_guessed`, `wrong_phase` as in protocol 5.
- **AC-TIME-09** (P1, U) Random scripts (12 000 seeded duels, boundary-hitting times) agree with the duel oracle at every step.
- **AC-TIME-10** (P1, S) Server countdowns use server time (`deadline - (now + offset)`); a client with a skewed clock (±5 min) still shows the right remaining time.
- **AC-TIME-11** (P1, A) The timer is visible, announces the last 10 s (sound cue if enabled, haptic if enabled, TalkBack polite announcement), and never blocks input.
- **AC-TIME-12** (P2, M) Untimed rooms: an idle but connected opponent cannot hold the other player forever (DEF-003, needs a product rule).

### 5.9 Disconnect and reconnect (RECON)
- **AC-RECON-01** (P1, U) Reconnecting before 60 s keeps the duel; at exactly 60 s it is lost (`disconnect`); both at the same instant → draw.
- **AC-RECON-02** (P1, U) Missed rounds count while disconnected (30 s timer can forfeit before 60 s).
- **AC-RECON-03** (P1, U) A round deadline that coincides with a disconnect expiry resolves first (DEF-030).
- **AC-RECON-04** (P1, S) After reconnect within 60 s, `welcome.roomId` is set and `room_state` + `chat_history` restore everything (including the pending guess).
- **AC-RECON-05** (P1, S) A newer connection replaces the old one: old socket gets `session_replaced` and close 4000; the app does not auto-reconnect after 4000/4003/4004/401/403.
- **AC-RECON-06** (P1, A) App switch for ≤ 30 s during a duel (home, recent apps, camera, a phone call) returns to the same duel without user action; reconnect time per NFR-05.
- **AC-RECON-07** (P1, A) Backoff 1, 2, 4, then every 5 s; airplane mode for 90 s and back → home screen with the result in stats, no crash, no duplicate sockets.
- **AC-RECON-08** (P2, S) After a disconnect forfeit in a team match the player reconnecting sees what protocol defines (DEF-022) and the team scoreboard.

### 5.10 Chat filter and emoji ban (CHAT)
- **AC-CHAT-01** (P1, U) Every MASK row of `chat_profanity.tsv` (10 languages + mixed: spacing, punctuation, stretching, zero-width, bidi, full-width, math/circled letters, look-alikes, leet, decomposed accents, jamo, QWERTY-typed Korean) is masked and the term is no longer readable.
- **AC-CHAT-02** (P1, U) Every CLEAN row of `chat_false_positives.tsv` (Scunthorpe-type words, words joined across spaces, cross-language traps, all digits) is delivered unchanged.
- **AC-CHAT-03** (P1, U) Emoji (presentation emoji, ZWJ sequences, flags, tags, keycaps, skin tones, VS16, Unicode 14–16 emoji) are removed; text emoticons and text-presentation symbols (^^, :), ♪, ★, ♥, ©, ™) survive (DEF-006 fixed).
- **AC-CHAT-04** (P1, U) Control, format, bidi, zero-width, private-use, unpaired surrogate and blank filler characters (U+3164, U+115F, U+1160, U+FFA0, U+2800) never reach other players; whitespace collapsed and trimmed; text NFC.
- **AC-CHAT-05** (P1, U) Empty after cleaning → `chat_empty`; > 100 code points after cleaning → `chat_too_long` (code points, not UTF-16 units).
- **AC-CHAT-06** (P1, U) Masking replaces with '*' and changes nothing else (one '*' per character; grapheme vs code point to be fixed in the doc, DEF-017); cleaning is idempotent.
- **AC-CHAT-07** (P1, U) 30 000 fuzzed strings never throw and every accepted output keeps the invariants.
- **AC-CHAT-08** (P1, S) The server delivers the same cleaned text to everybody including the sender, equal to `:core` `ChatFilter.clean`.
- **AC-CHAT-09** (P1, S) Rate limit 5 lines per 10 s (text and phrases together) → `chat_rate_limited` with `retryAfterMs`; the app shows a countdown, not an error loop.
- **AC-CHAT-10** (P1, A) The app offers no image or emoji input (keyboard emoji are stripped in the preview; no attachment button); URLs are shown as plain text, never linkified.
- **AC-CHAT-11** (P2, A) The input box shows the remaining characters and the same masking preview as the server.
- **AC-CHAT-12** (P2, S) Chat history is the last 50 lines, block-filtered, memory only (never on disk).

### 5.11 Report, block, mute, quick phrases, moderation (SAFE)
- **AC-SAFE-01** (P1, S) Block hides chat in both directions, live and in history, takes effect immediately, is idempotent; blocking yourself → 400; unknown player → 404.
- **AC-SAFE-02** (P1, S) A message report stores the reported message and the last 20 delivered (masked) lines; the target must be the sender (else 400); a player report is always accepted; note ≤ 500; 10 per hour.
- **AC-SAFE-03** (P1, A) Report and Block are reachable from a long-press on a message and from the player card in ≤ 2 taps; after a report the app offers "Block too".
- **AC-SAFE-04** (P1, A) "Mute all chat" hides all incoming lines (keeps quick-phrase sending), persists across restarts, and is in Settings.
- **AC-SAFE-05** (P1, S) Reports from 3 distinct accounts about the same player within 24 h restrict that player's chat for 24 h; 2 reporters, or 3 reports from one account, do not; the restriction ends after 24 h or when an admin lifts it; the restricted player sees a localized notice (error code to be defined, DEF-011; collusion risk DEF-043).
- **AC-SAFE-06** (P1, S) Admin can chat-restrict (`POST /admin/v1/accounts/{id}/chat-restrict`), ban (live sockets closed with 4003, removed from rooms and queue) and unban.
- **AC-SAFE-07** (P1, S) Resolved reports and their snapshots are purged 180 days after resolution; deleted players are replaced by a "deleted player" marker inside other reports' snapshots.
- **AC-SAFE-08** (P2, A) Quick phrases are localized in all 10 languages; unknown phrase ids from a newer server are ignored.
- **AC-SAFE-09** (P2, A) Blocked players list in Settings with Unblock.

### 5.12 Terms gate (TERMS)
- **AC-TERMS-01** (P1, A) Given the current terms version is not accepted, when the player opens quick match, a room or chat, then the terms screen (summary, link to `/terms`, Agree) appears and cannot be bypassed by Back, Home, recents, rotation, split-screen relaunch, deep link or process death.
- **AC-TERMS-02** (P1, S) The server refuses `queue_join`, `room_create`, `room_join`, `chat` and `phrase` with `terms_required` until `PUT /api/v1/me/terms {version, age confirmation}` stores the current version and time; an old version does not unlock; a request without the age confirmation (the player confirms being at least the minimum age) is refused and stores nothing.
- **AC-TERMS-03** (P1, A+S) When the terms version changes, the gate appears again before the next online action.
- **AC-TERMS-04** (P1, M) `/terms` exists in ko and en, has a `#child-safety` section that prohibits CSAE and names the contact; reachable from Settings and the store listing.
- **AC-TERMS-05** (P1, A) Offline vs-computer and the rules tutorial never require terms acceptance.

### 5.13 Device account and nickname (ACC)
- **AC-ACC-01** (P1, A) First launch online: the only step before online play is choosing a nickname; the account is created in the background; offline play is available immediately.
- **AC-ACC-02** (P1, U+S) Nicknames follow `nicknames.tsv`: NFKC, trim, 2–12 code points, letters/digits only, no invisible fillers, no '#', reserved names rejected (incl. localized ones), profanity anywhere rejected; accepted names are stored normalized and re-validate unchanged.
- **AC-ACC-03** (P1, U) Hangul compatibility jamo are stored as typed ("ㅋㅋ" stays "ㅋㅋ"; "ㄱㅏ" is not composed into "가") (DEF-035).
- **AC-ACC-04** (P1, S) Display is `nickname#tag` with a 4-digit tag 0001–9999; uniqueness ignores case independent of device locale (Turkish i).
- **AC-ACC-05** (P1, S) Rename keeps the tag when free, otherwise assigns a new one; 5 renames per day; rooms show the new name in the next snapshot.
- **AC-ACC-06** (P1, S) Account creation is limited (10 per IP per hour + global cap) and returns `rate_limited` with retry time; the app explains it and keeps offline play (DEF-027).
- **AC-ACC-07** (P2, A) The nickname field validates live with the `:core` rules and shows localized reasons.

### 5.14 Auto Backup restore (BAK)
- **AC-BAK-01** (P1, M) Given a phone with an account and Google backup on, when a new phone signed in to the same Google account restores the app, then the player opens as the same `nickname#tag` with stats.
- **AC-BAK-02** (P1, M) Only the token/settings file is backed up (`dataExtractionRules` for Android 12+, `fullBackupContent` below); device-to-device transfer also carries it.
- **AC-BAK-03** (P1, M) After in-app deletion the token is cleared at once so a later backup cannot restore a deleted account; a restored deleted/revoked token leads to the "account not found" flow and a clean new account.
- **AC-BAK-04** (P1, M) The restored token decrypts on the new device (if the token is encrypted with a Keystore key, restore fails; DEF-008).
- **AC-BAK-05** (P2, M) Old and new phone both running: the newer connection wins (4000 on the other) and the older phone shows "signed in on another device" without reconnect loops.

### 5.15 Transfer code (XFER)
- **AC-XFER-01** (P1, S) The code is 12 characters shown as XXXX-XXXX-XXXX, valid 10 minutes, single use; a new code replaces the previous one; dashes, spaces and case are ignored on redeem.
- **AC-XFER-02** (P1, S) Redeeming returns a new token and revokes every older token: the old device's next request is 401 and its live socket is closed (DEF-010).
- **AC-XFER-03** (P1, S) 5 wrong tries kill a code and 10 failures per IP in 10 minutes → `too_many_attempts` with retry time (DEF-010 on attributing tries to a code).
- **AC-XFER-04** (P1, A) The app shows a countdown, copy button, and expiry; redeeming on a device that already has an account warns that the current account on this device will be replaced.

### 5.16 Account deletion in app and on the web (DEL)
- **AC-DEL-01** (P1, A+S) Settings → Delete account (with confirmation) deletes immediately: account, tokens, codes, blocks, stats; the socket closes with 4004; other room members see "deleted player"; the app clears the token and returns to first launch.
- **AC-DEL-02** (P1, S) Web with an in-app deletion code (24 h): `/delete-account` form requires the code and the confirmation checkbox; a valid code deletes at once; invalid/missing confirmation deletes nothing; IP limits as transfer codes.
- **AC-DEL-03** (P1, S) Web request without a code (`Nickname#1234` + confirm): identical response (status, body, timing within ±100 ms) whether or not the handle exists; IP-rate-limited; the account gets `deletionRequestedAt`.
- **AC-DEL-04** (P1, A+S) The app shows "A deletion request was made on the web" with Cancel and Delete now; Cancel and any deliberate authenticated use cancel the request; with no use for 30 days the account is deleted exactly like DELETE /me (time-travel test; DEF-038 on background requests and griefing).
- **AC-DEL-05** (P1, M) The web page lists the email fallback and what is deleted/retained; works without JavaScript, without cookies, on mobile browsers, in ko and en.
- **AC-DEL-06** (P1, S) Reports filed by the deleted player lose the reporter id; reports about them are closed and their snapshots erased; their lines in other snapshots are anonymised.

### 5.17 Stats (STAT)
- **AC-STAT-01** (P1, S) One result per match per participant: win, loss, or draw (team result in team play).
- **AC-STAT-02** (P1, S) vs-computer, practice and review-room bot games never change online stats (QA proposal, DEF-005).
- **AC-STAT-03** (P1, S) Matches interrupted by a server restart record nothing and the app does not claim a result (DEF-012).
- **AC-STAT-04** (P2, A) Profile shows W/L/D and updates right after a match without restart.

### 5.18 Rules tutorial (LEARN)
- **AC-LEARN-01** (P1, A) A beginner can open "How to play" from home and before the first game, offline, in every language.
- **AC-LEARN-02** (P1, U+A) Every worked example in the tutorial (all languages) matches the oracle judge (no wrong S/B in screenshots or text).
- **AC-LEARN-03** (P2, M) 5 of 5 first-time testers can explain strike/ball/out after the tutorial (usability check per language group ko, ja, en).

### 5.19 Localization (L10N)
- **AC-L10N-01** (P1, A) All strings exist in ko, en, ja, zh-CN, es, fr, de, pt-BR, ru, vi (lint MissingTranslation = error); no hard-coded UI text.
- **AC-L10N-02** (P1, A) Plurals use proper categories (ru one/few/many/other; es/fr/de/pt one/other); placeholders keep order; no concatenated sentences.
- **AC-L10N-03** (P1, A) The app name is localized in resources ("숫자야구" for ko, equivalents per language per owner decision) and fits under launcher icons on a 320 dp screen; never "Mastermind", "Numer0n" or "Excel".
- **AC-L10N-04** (P1, A) Locale fallback: zh-TW/zh-HK, pt-PT, es-419, fr-CA, de-CH, en-GB get a defined language (DEF-019); an unsupported locale (e.g. ar, th) falls back to English with an LTR layout that does not break under an RTL system locale.
- **AC-L10N-05** (P1, A) Game digits and keypad are always ASCII 0–9 regardless of locale numbering systems; timers/counters may use the locale format.
- **AC-L10N-06** (P1, A) Every server error code maps to a localized message; unknown codes show a generic message.
- **AC-L10N-07** (P2, A) Per-app language (Android 13+) and in-app language choice work and survive restart.
- **AC-L10N-08** (P2, M) Native-speaker review of the rules text, quick phrases and store listing in each language.

### 5.20 Accessibility (A11Y)
- **AC-A11Y-01** (P1, A) TalkBack announces every keypad key, the guess, and each hint as words ("two strikes, one ball"), localized; history is navigable row by row.
- **AC-A11Y-02** (P1, A) Touch targets ≥ 48 dp; text contrast ≥ 4.5:1, large text/icons ≥ 3:1, in light and dark themes.
- **AC-A11Y-03** (P1, A) S/B/O are not conveyed by color alone; sound and haptics are never the only signal.
- **AC-A11Y-04** (P1, M) A TalkBack-only player completes a vs-computer game and an online quick match (charter CH-08).
- **AC-A11Y-05** (P2, M) Switch Access and hardware keyboard/D-pad (Chromebook) reach every control; digits typeable on a keyboard.
- **AC-A11Y-06** (P2, A) "Remove animations" is respected; timer announcements are polite, not every second.

### 5.21 Small split-screen and pop-up windows (WIN)
- **AC-WIN-01** (P1, A) The duel screen is fully usable (secret/guess entry, last 3 hints, timer, submit) in a 320 × 400 dp window; the minimum supported size is documented by design (DEF-020 asks for the banner threshold).
- **AC-WIN-02** (P1, A) Resizing between split-screen sizes, pop-up view, landscape, and fold/unfold mid-duel keeps all state and the socket.
- **AC-WIN-03** (P1, A) In split-screen the app stays connected while visible but not focused (multi-resume) and keeps the timer running.
- **AC-WIN-04** (P1, A) Banners are hidden in short windows (see ADS-08); no content overlaps.
- **AC-WIN-05** (P2, M) One-handed use: primary actions are in the lower half on phones.

### 5.22 Large font scale (FONT)
- **AC-FONT-01** (P1, A) At 200 % font (Android 14 non-linear) and largest display size, no clipped hint counts, timer, nickname#tag or buttons in any language; screens scroll instead of clipping.
- **AC-FONT-02** (P2, A) Screenshot tests cover 1.0, 1.5, 2.0 font scale × 3 window sizes × 3 locales (de, ru, ja).

### 5.23 Offline and bad network (NET)
- **AC-NET-01** (P1, A) No network on first launch: vs-computer and tutorial work; online entries explain and retry; account creation happens later automatically.
- **AC-NET-02** (P1, A) Slow 3G (400 kbps, 300 ms RTT, 5 % loss): matches stay playable; no duplicate guesses on retry (use `rid` + `stale_round`).
- **AC-NET-03** (P1, A) Wi-Fi ↔ mobile data switch mid-duel reconnects within NFR-05 without losing the round.
- **AC-NET-04** (P2, M) Captive portal, IPv6-only, VPN/proxy: clear error or normal play, never a hang.
- **AC-NET-05** (P2, A) No reconnect loop drains the battery: backoff respected, no wake locks, socket closed in background after the grace period.

### 5.24 Server restarts and operations (SRV)
- **AC-SRV-01** (P1, S) Graceful restart: connected players get `room_left{closed}` or close 1001, reconnect with backoff, see `welcome.roomId = null` and a friendly "server restarted" message; nothing is recorded for interrupted matches.
- **AC-SRV-02** (P1, S) Crash/kill -9: accounts, stats, blocks, reports and terms acceptance survive (SQLite on persistent disk); no corrupted DB.
- **AC-SRV-03** (P1, S) `/health` is 200 within 1 s of readiness; always-on hosting during review.
- **AC-SRV-04** (P2, S) Clock jumps on the server (NTP step) do not fire or skip deadlines wrongly (monotonic clock for timers).

### 5.25 Ads — Google AdMob (ADS)
Banner screens (whitelist): **home, results, profile/stats**. Every other screen is ad-free.
- **AC-ADS-01** (P1, A) No ad view exists (not even hidden or loading) while secret entry or a duel is on screen — vs computer, quick match, private room, team play.
- **AC-ADS-02** (P1, A) No ad during the waiting practice game, in the room lobby while players join, in chat, on the searching screen, in Settings, tutorial, terms, deletion, transfer or consent screens.
- **AC-ADS-03** (P1, A) A screen inventory test walks every route and asserts that an ad container appears only on the whitelist.
- **AC-ADS-04** (P1, A) The banner is adaptive and anchored, never overlaps content, and has clear space from every tappable control (no button directly above or below; ≥ 8 dp gap).
- **AC-ADS-05** (P1, A) No fill or load error → the banner container collapses to 0 height (no blank box, no layout jump of controls).
- **AC-ADS-06** (P1, A) Offline → no ad requests, no error UI, no delay.
- **AC-ADS-07** (P1, A) On the results screen the banner never covers the outcome or the Rematch/Home buttons.
- **AC-ADS-08** (P1, A) Banners are hidden in short split-screen and pop-up windows (threshold set by design, proposed height < 480 dp or width < 360 dp; DEF-020).
- **AC-ADS-09** (P1, A) An interstitial can appear only when the player leaves a finished vs-computer result screen (Home or Rematch), never earlier than 1 s after the result appeared, never the instant the last guess lands.
- **AC-ADS-10** (P1, A) Frequency cap: at most one interstitial per 3 finished vs-computer matches AND at least 4 minutes after the previous one (decision table below); the counter and timestamp survive process death and restart (QA proposal, DEF-020).
- **AC-ADS-11** (P1, A) Never an interstitial after an online match (quick, private, team), after practice, after the review room, on app start, on resume from background, or on exit; no app-open ads; no rewarded ads.
- **AC-ADS-12** (P1, A) An interstitial is shown only if already loaded; navigation never waits for an ad; closing it (or Back) lands on the screen the player chose.
- **AC-ADS-13** (P1, A) Ads are always muted (`setAppMuted(true)`, volume 0) — README Sound: "Ads stay muted" (DEF-044 notes the conflicting POLICY_REVIEW R11 wording); no audio focus request by ads; verified with `dumpsys audio`.
- **AC-ADS-14** (P1, A) The max ad content rating comes from build configuration and equals the app's IARC rating (G for Everyone/3+, PG for E10+, T for Teen); release check fails if it is higher than the rating recorded in the build config; sensitive categories (gambling, dating, alcohol, sexual, get-rich-quick) are blocked in AdMob (R20).
- **AC-ADS-15** (P1, M) For 18+ users, UMP: `requestConsentInfoUpdate` on every launch; with debug geography EEA (and UK) the consent form appears before any ad request; `MobileAds` is initialized only when `canRequestAds()`. For 14–17 the age treatment of AC-AGE-05 applies first (14–15: no consent form, no personalized ads).
- **AC-ADS-16** (P1, M) Refusing consent: the whole app works; ads are limited/non-personalized; no repeated nagging beyond UMP's own logic.
- **AC-ADS-17** (P1, M) US regulated-state geography: the US state message appears where configured; opt-out honored.
- **AC-ADS-18** (P1, A) Settings shows "Privacy options" only when `privacyOptionsRequirementStatus == REQUIRED`; it opens the UMP privacy options form; changes apply to later requests.
- **AC-ADS-19** (P1, M) Consent messages exist in all 10 languages in AdMob.
- **AC-ADS-20** (P1, A) Debug builds use only Google test ad unit IDs (publisher `ca-app-pub-3940256099942544`); real IDs come only from build configuration/CI secrets.
- **AC-ADS-21** (P1, A) A release build fails at build time when real app/unit IDs are missing or equal to test IDs (Gradle check task).
- **AC-ADS-22** (P1, A) CI scan: no `ca-app-pub-` id other than the test publisher is committed.
- **AC-ADS-23** (P1, A) Automated UI tests run with ads disabled (no ad views, no requests to ad hosts — verified by a test that asserts the no-op ads provider).
- **AC-ADS-24** (P1, S) `GET /app-ads.txt` returns 200 `text/plain` with exactly `APP_ADS_TXT`, unauthenticated; 404 when unset; served on the developer website domain root.
- **AC-ADS-25** (P1, A) `com.google.android.gms.permission.AD_ID` is present in the merged release manifest.
- **AC-ADS-26** (P2, A) Ad SDK initialization does not add more than 100 ms to cold start (NFR-02) and never runs before consent.
- **AC-ADS-27** (P2, A) Rotation/resize while an interstitial is up: no crash, no double show, cap counts once.

Interstitial decision table (vs-computer matches only; "show" also needs a loaded ad):

| Case | Finished since last interstitial | Minutes since last | Leaving result screen? | Expected |
| --- | --- | --- | --- | --- |
| 1 | 3 | ≥ 4 | yes | show |
| 2 | 2 | ≥ 4 | yes | no |
| 3 | 3 | 3:59 | yes | no |
| 4 | 3 | ≥ 4 | result shown < 1 s ago | no (wait until ≥ 1 s) |
| 5 | 5 (online matches in between) | ≥ 4 | yes | show (online matches never count) |
| 6 | 3 incl. a practice game | ≥ 4 | yes | no (practice does not count) |
| 7 | first ever, 3 finished | n/a | yes | show |
| 8 | 3, app restarted in between | ≥ 4 | yes | show; counter persisted |

### 5.26 Sound (SND)
Thresholds marked † are QA defaults until the sound designer's `design/sound/REPORT.md` exists (it does not yet); then REPORT.md wins.
- **AC-SND-01** (P1, A) Fresh install: effects on, music off, "Follow silent mode" on, haptics on (haptics default unspecified, DEF-045).
- **AC-SND-02** (P1, A) With "Follow silent mode" on and the ringer in silent or vibrate, no effect plays; with it off, effects play at the effects volume.
- **AC-SND-03** (P1, A) Effects volume, music volume, haptics toggle and follow-silent persist across restart and app update (and restore with backup settings).
- **AC-SND-04** (P1, M) Audio focus: music pauses and effects stop when another app takes focus, when the app goes to background, and during calls (incoming, outgoing, VoIP); music resumes only in foreground with music enabled.
- **AC-SND-05** (P1, M) Ads never produce sound (see AC-ADS-13).
- **AC-SND-06** (P1, A) Hint reveal cues match the shown counts and order: for "2S 1B" exactly two strike cues then one ball cue, in the same order as the on-screen reveal; "OUT" plays only the out cue; a solve plays the solve cue; cue count equals displayed count for every hint (property test over the view model).
- **AC-SND-07** (P1, A) Repeated keypad taps vary (≥ 3 variants or pitch jitter; never the same variant twice in a row).
- **AC-SND-08** (P1, A) Distinct cues for win, lose, draw, match found, timer warning (timed rounds only), chat message (not for own lines, not for blocked senders, not when chat is muted), navigation.
- **AC-SND-09** (P1, U/A) Every audio file passes the REPORT.md rules (format OGG Vorbis; loudness target ± tolerance; true peak ≤ −1 dBTP†; leading silence ≤ 5 ms† for UI effects; trailing silence ≤ 100 ms†; loop points click-free†; no DC offset†); an automated check runs over `android/app/src/main/res/raw`.
- **AC-SND-10** (P2, M) Keypad cue latency ≤ 50 ms† from touch on the reference device; no clicks or pops.
- **AC-SND-11** (P2, M) TalkBack speech is not masked by effects (effects ducked or suppressed while speaking).
- **AC-SND-12** (P2, A) Haptics off → no vibration anywhere; system "touch feedback" off is respected.

### 5.27 Privacy, logging and data safety (PRIV)
- **AC-PRIV-01** (P1, S) No chat text, tokens, transfer/deletion codes or IP addresses in server logs or the database (rate-limit IPs in memory only) — automated scan with marker strings.
- **AC-PRIV-02** (P1, S) Secrets and guesses are memory only; only results are stored.
- **AC-PRIV-03** (P1, M) Captured traffic of the release build (first launch, consent, offline game, online match, chat, report, ads) reaches only our server and Google ads/UMP hosts; data types sent match the Data safety form and privacy policy (account id, nickname, chat sent to other players, reports, advertising ID / device identifiers shared with Google, IP transiently); no analytics or crash SDK.
- **AC-PRIV-04** (P1, A) No token in logcat, crash text, URLs or WebSocket query strings.

### 5.28 Review room (REV)
- **AC-REV-01** (P1, S) With `REVIEW_ROOM_CODE` set, joining that code starts a 1v1 4-digit duel against a slow server bot that posts one quick phrase and one text line (so Report and Block can be tested); unset → `room_not_found`.
- **AC-REV-02** (P2, S) Bot games do not change stats, never pair real players with the bot by quick match, and the code works 24/7 during review.

### 5.29 Protocol conformance (PROTO)
- **AC-PROTO-01** (P1, U) Every DECODE row of `protocol_frames.tsv` fails to decode with `ProtocolJson` (server answers `malformed_message`).
- **AC-PROTO-02** (P1, U) Every SERVER row decodes, so the server can return the specific error.
- **AC-PROTO-03** (P1, U) TOLERATED rows never crash the decoder (deep nesting, duplicates, quoted numbers).
- **AC-PROTO-04** (P1, U) Message type names and field names equal the tables in protocol 5–7; nulls are encoded explicitly (`opponentSecret: null`).
- **AC-PROTO-05** (P1, U) Clients ignore unknown fields; an unknown server type is skipped without crashing (Android test).
- **AC-PROTO-06** (P1, S) Malformed frames get an error and the connection stays open; 2048 bytes processed, 2049 → `message_too_large`, > 16 KB frame → close 1009, binary frames ignored, a fragmented message > 16 KB is not buffered without limit.
- **AC-PROTO-07** (P1, S) With `rid` the server answers exactly once (`ack` or `error`); `room_state.version` strictly increases; the app ignores a lower version.

### 5.30 Age screen and age-based treatment (AGE) — POLICY_REVIEW R19–R21
- **AC-AGE-01** (P1, A) First launch shows a neutral age screen (birth year; no pre-selected adult year, no hint of the cut-off, no "I am over 18" shortcut) before anything else: until it is answered there is no MobileAds/UMP initialization, no ad request, no account creation and no request to our server (verified with a debug proxy and a fake ads provider).
- **AC-AGE-02** (P1, A) The minimum age comes from build configuration (default 14); builds with 13 and 14 behave accordingly at the boundary (age computed from birth year conservatively: a player who may not have reached the minimum this year is treated as under-age, or the screen asks for the month; rule documented).
- **AC-AGE-03** (P1, A) An under-age answer stops the app with a neutral message (no retry button, no "try another year"); the result persists across restart, process death and app update, so the screen does not simply reappear for another try (reinstall/clear data is the accepted limit, DEF-046).
- **AC-AGE-04** (P1, A+M) The birth year/age band stays on the device: it is never sent to our server, logged, or included in analytics; the only effect outside the device is the ad treatment flags of AC-AGE-05 (traffic capture, AC-PRIV-03); whether it is in Auto Backup is decided in DEF-046.
- **AC-AGE-05** (P1, A+M) Ad treatment is set before the Mobile Ads SDK initializes: 14–15 → child/under-age treatment (no personalized ads, no advertising ID sent, no UMP consent form), 16–17 → teen treatment, 18+ → normal UMP flow; checked on the outgoing ad requests in a debug proxy for each band.
- **AC-AGE-06** (P1, A) Play Age Signals: when it reports a minor (or a supervised user), the stricter of signal and age screen applies; supervised users do not get features Play treats as "significant changes" until approved; when the API is unavailable, errors, times out or returns no signal (non-US, old Play services, offline), the app falls back to the age screen answer without delay or crash (Play test responses for each case).
- **AC-AGE-07** (P1, S) The server's terms acceptance requires the age confirmation (AC-TERMS-02); an account that has not confirmed cannot enter online play or chat.
- **AC-AGE-08** (P2, A) The age screen is localized, accessible (TalkBack, large fonts, small windows) and shown only once per install for an eligible answer.
- **AC-AGE-09** (P2, M) Store listing, Terms and in-app text state the same minimum age as the build config (no "18+" leftovers).

---

## 6. Non-functional budgets

| Id | Metric | Budget | How measured |
| --- | --- | --- | --- |
| NFR-01 | Computer move time (Hard, 6 digits) | worst ≤ 150 ms on the reference low-end device; ≤ 60 ms worst on CI JVM (measured 6.8 ms) | `ComputerPlayerQaTest.nfrCpu01`, Android microbenchmark |
| NFR-02 | Cold start to interactive home | p50 ≤ 1.5 s, p90 ≤ 2.5 s mid-range (Pixel 6a); ≤ 4 s low-end; ads/consent add ≤ 100 ms to first frame | Macrobenchmark `StartupTimingMetric`, 20 runs |
| NFR-03 | Memory | PSS ≤ 150 MB in an online match incl. ad SDK, ≤ 120 MB vs computer; < 10 MB growth after 20 matches | `dumpsys meminfo`, LeakCanary in debug |
| NFR-04 | Size | AAB download ≤ 15 MB (alert at 12 MB); 16 KB page-size compatible native libs | bundletool `get-size total`, `zipalign -P 16` check |
| NFR-05 | WebSocket reconnect | first attempt ≤ 1 s after network returns; state restored ≤ 2 s after socket open on 4G; app switch ≤ 30 s resumes without user action | instrumented test with network toggling |
| NFR-06 | Server latency | round resolution p95 ≤ 150 ms server-side; 500 concurrent duels on 1 vCPU with p95 ≤ 250 ms | load test (k6/Gatling) |
| NFR-07 | Frame time | no frame > 32 ms during guess submit, hint reveal and computer move on mid-range | JankStats / Macrobenchmark `FrameTimingMetric` |
| NFR-08 | Battery/network | lobby idle: no wake locks, WebSocket ping 15 s from server only, no extra polling | Battery Historian 30 min |
| NFR-09 | Sound | keypad cue latency ≤ 50 ms†; effects file sizes ≤ 50 KB each†, music ≤ 1.5 MB each† | REPORT.md, audio check |

## 7. Security checks

| Id | Check | Method |
| --- | --- | --- |
| SEC-01 | Token: 256-bit CSPRNG, base64url 43 chars, stored only as SHA-256 on the server, never logged | AC-SEC test, code review, log scan |
| SEC-02 | Token on Android: app-private storage, in Auto Backup only via the rules file, not on external storage, not in logs; Keystore decision (DEF-008) | review, device test |
| SEC-03 | Transport: HTTPS/WSS only, cleartext disabled (network security config), token in the Authorization header (never in URLs), HSTS on web pages | traffic capture, manifest review |
| SEC-04 | Auth on every endpoint and message: `rest_requests.tsv` (no/garbage/basic/empty/revoked/deleted tokens), WS upgrade 401/403 | `ProtocolSecurityQaTest` |
| SEC-05 | Opponent secret never sent before the duel ends (incl. reconnect snapshots, team summaries, errors) | frame scan in every scenario |
| SEC-06 | Authorization: no acting in other rooms, no reporting messages of rooms never joined (snapshot), host-only actions | scenarios, DEF-040 |
| SEC-07 | Input validation: all corpora; JSON strict (no lenient syntax, no NaN, no comments); size caps; Unicode digits rejected | `ProtocolDecodingQaTest`, corpora |
| SEC-08 | Rate limits: account creation, rename, transfer redemption, deletion page, reports, chat, WS messages (close 1008), room-code join failures | scenarios |
| SEC-09 | Admin API: 404 when `ADMIN_TOKEN` unset; account tokens never work; constant-time compare; minimum token length; not in logs (DEF-042) | scenario + review |
| SEC-10 | Transfer code: entropy ≥ 60 bits, single use, 10 min, old tokens revoked, live sockets closed | scenario, DEF-010 |
| SEC-11 | Web deletion: code-based deletion cannot be brute-forced; request-based deletion cannot be used to delete active players; no enumeration | scenario, DEF-038 |
| SEC-12 | Web pages: output escaping (nicknames, report snapshots on the admin side), no cookies, CSP header, no open redirects | review + ZAP baseline |
| SEC-13 | Android: minimal exported components, no debuggable release, R8 on, no secrets/ad IDs in the APK beyond the public app id | apkanalyzer, MobSF |
| SEC-14 | Dependencies: no known critical CVEs (Ktor, SQLite JDBC, logback, Play services) | OWASP dependency-check in CI |

## 8. Play policy checks

| Id | Policy | Check |
| --- | --- | --- |
| POL-01 | Account deletion | in-app path ≤ 3 taps from Settings; web resource that works without the app and without a code (AC-DEL-03); listed in Data safety |
| POL-02 | UGC | terms gate (AC-TERMS), report message/player, block, mute, filter, timely moderation (admin checks daily), auto restriction |
| POL-03 | Child safety (audience 13+, minimum age 14 by default) | published standards prohibiting CSAE (`/terms#child-safety`), in-app reporting, contact, compliance with local law; quick match phrases only (random-chat rule) |
| POL-04 | Target audience and age | Play Console 13–15, 16–17, 18+; content rating questionnaire (users interact, ads); no Families program; age screen and minimum age (AC-AGE); Play Age Signals for US state laws (R21); "significant change" notification decided before releases that change social features |
| POL-05 | Ads | no ads interfering with play; interstitials only at natural breaks with ≥ 1 s pause; "Contains ads" declared; app-ads.txt; max ad rating = IARC rating (G expected); under-16 treatment; consent (UMP) |
| POL-06 | Data safety | answers match AC-PRIV-03 capture and `PRIVACY_POLICY.md` (incl. AdMob data) |
| POL-07 | Target SDK | targetSdk 36 (required since 2026-08-31); large-screen resizability; 16 KB pages for native libs |
| POL-08 | Payments | no donation links or buttons; no "ad-free" claims |
| POL-09 | Metadata / impersonation | localized name, no "Mastermind/Numer0n/Excel", screenshots without ads or real nicknames |
| POL-10 | Reviewer access | always-on server, review room, app access instructions |
| POL-11 | Permissions | only INTERNET, ACCESS_NETWORK_STATE, AD_ID (merged), POST_NOTIFICATIONS only if notifications exist |

## 9. Device, size and locale matrix

| Row | Device / emulator | API | Window / size | Locale(s) | Purpose |
| --- | --- | --- | --- | --- | --- |
| core-1 | Low-end (2 GB RAM, Android Go class) | 28 | phone 360×640 dp | ko | performance budgets, sound latency |
| core-2 | Pixel 6a | 34 | phone 411×891 | en, de | reference mid-range |
| core-3 | Pixel 9 | 36 | phone + split-screen 50/50 and 1/3 | ja, vi | targetSdk behavior, multi-resume |
| core-4 | Samsung Galaxy A-series (One UI) | 35 | pop-up view ~300×400 dp | ko, ru | pop-up windows, Samsung keyboard emoji |
| core-5 | Pixel Fold / Galaxy Z Fold | 35 | folded ↔ unfolded | fr | posture changes |
| ext-1 | Tablet 10" | 34 | sw720 landscape | zh-CN | large screen |
| ext-2 | Xiaomi (HyperOS) | 34 | phone | es, pt-BR | aggressive background kill, reconnect |
| ext-3 | Chromebook / desktop windowing | 34 | free-form resize, keyboard | en | keyboard and resize |
| ext-4 | Emulator minSdk | min | phone | pseudo en-XA, ar-XB | pseudo-locales, RTL system locale |

Cross-cutting: font scale 0.85 / 1.0 / 1.3 / 1.5 / 2.0; display size small/large; light/dark; TalkBack on; keyboards Gboard (ko, ja flick, zh pinyin, vi Telex), Samsung, SwiftKey; network profiles (Wi-Fi, LTE, 3G-throttled, lossy, offline, IPv6-only); consent geographies (EEA, UK, US state, other).

## 10. Exploratory charters (60–90 min each)

- **CH-01** Duel under a 30 s timer with rapid app switches, split-screen resizing and notification shade to find timer desync or lost guesses.
- **CH-02** Chat abuse with every IME (Korean jamo, Japanese flick, Chinese pinyin, Vietnamese Telex), emoji keyboard, paste, autocorrect, to find filter bypasses and false positives; add findings to the corpora.
- **CH-03** 5v5 team match with mixed disconnects, forfeits, draws and early decision; watch scoreboard, stats and secrets reveal.
- **CH-04** First launch offline and online, nickname edge cases, terms gate escapes (deep links, recents, process death).
- **CH-05** Account continuity: Auto Backup restore, device-to-device transfer, transfer code races on two devices, deletion request then reinstall.
- **CH-06** Ads across all screens and window sizes with test ads; consent in EEA/UK/US-state debug geographies; interstitial cap with clock changes.
- **CH-07** Localization stress: longest languages (de, ru, vi, fr, pt-BR) at 200 % font in split-screen; rules tutorial correctness.
- **CH-08** TalkBack-only player completes vs-computer and quick match.
- **CH-09** Server restarts and deploys mid-match; DB on a full disk; slow DB.
- **CH-10** Griefing: AFK in untimed rooms, join spam, report brigading with fresh accounts, block evasion, room-code guessing.
- **CH-11** Device time and timezone changes during timed rounds and around the interstitial 4-minute cap.
- **CH-12** Process death (Don't keep activities, low-memory kill) during secret entry, duel, practice-to-match switch, interstitial.
- **CH-13** Web pages (`/`, `/privacy`, `/terms`, `/delete-account`) on mobile browsers, no JS, both languages, injection in form fields.
- **CH-14** Sound: silent/vibrate switches, Bluetooth headset connect/disconnect, calls, other media apps, volume keys, TalkBack.
- **CH-15** Beginner usability: someone who never heard of the game learns from the tutorial and plays Easy.

## 11. Test data: corpora

All files in `docs/qa/corpora/` are UTF-8 TSV with a commented header explaining their columns and expectations.
Escapes: `\uXXXX`, `\U{X..X}`, `\n \r \t \0`, `\s` (space), `\\`, `\R[N:TEXT]` (repeat). Invisible, control, bidi and look-alike
characters are always escaped (enforced by `QaCorporaWellFormedTest`).

| File | Rows | Content |
| --- | --- | --- |
| `chat_profanity.tsv` | 245 | evasions per language (ko, en, ja, zh-CN, es, fr, de, pt-BR, ru, vi, mixed): MASK / EITHER |
| `chat_false_positives.tsv` | 124 | innocent traps incl. words joined across spaces, cross-language traps, numbers: CLEAN |
| `chat_emoji.tsv` | 68 | emoji to strip (ZWJ, flags, tags, keycaps, skin tones, VS16, Unicode 14–16) and text symbols to keep |
| `chat_sanitize.tsv` | 45 | controls, bidi, zero-width, fillers, whitespace, length limits, injection strings, NFC |
| `nicknames.tsv` | 125 | valid/invalid nicknames with reasons, normalization results, reserved, profanity |
| `protocol_frames.tsv` | 150 | malformed/hostile WebSocket messages by layer (DECODE/TOLERATED/SERVER/SIZE) and required state |
| `rest_requests.tsv` | 50 | REST bad input and auth cases with expected status and code |

New findings from charters become corpus rows (with the defect id in the note).

## 12. Oracle and differential testing

`QaOracle` (judge via a digit→position table, a second naive judge, validity, all numbers, team verdict) and `QaDuelOracle`
(protocol 8.3 state machine) are written from the README and protocol only. `QaTextOracle` holds the chat invariants, the
"word is gone" skeleton (NFKC, case fold, accents/format removed, look-alikes folded, leet folded or digits dropped, repeats
collapsed) and the structural nickname rule.

Derivation of the 3-digit distribution (fixed secret abc, 720 guesses): 3S 1; 2S0B: 3 positions × 7 non-secret digits = 21;
2S1B impossible; 1S0B: 3 × (7×6) = 126; 1S1B: 3 × 2 × 7 = 42; 1S2B: 3 (swap the other two); 0S0B: 7×6×5 = 210;
0S1B: 3 digits × 2 wrong places × 7×6 = 252; 0S2B: 3 places for the non-secret digit × 7 × 3 derangement pairs = 63;
0S3B: 2 derangements. Sum 720.

Differential coverage: all 518 400 three-digit pairs; 900 000 random 4–6 digit pairs; all strings ≤ 4 over a hostile alphabet for
the validator; 12 000 random duel scripts (> 100 000 steps, times hitting deadlines exactly and 1 ms before); every finish
order × outcome for team sizes 1–5.

## 13. Entry/exit criteria and severities

Entry for a feature's test cycle: AC reviewed, protocol section stable, build green, test data available.
Exit: section 1 gates. Severities (see DEFECTS.md): **Blocker** (wrong result, secret leak, account loss/takeover, crash on a
main path, policy violation that blocks release), **Major** (feature wrong or unusable in a supported configuration, policy
risk, spec gap that allows incompatible implementations of a core rule), **Minor** (workaround exists, limited impact, doc
gap), **Trivial** (cosmetic, wording).

## 14. Traceability and status (2026-11-21)

| Area | Automated tests | Status |
| --- | --- | --- |
| RUL | `JudgeDifferentialQaTest` (8) | pass |
| CPU, NFR-01 | `ComputerPlayerQaTest` (8) | pass |
| TIME, RECON (engine) | `DuelEngineQaTest` (17) | pass |
| TEAM (engine) | `TeamMatchQaTest` (6) | pass |
| CHAT (core) | `ChatFilterCorpusQaTest` (7) | pass (DEF-006, DEF-037 fixed during phase 1) |
| ACC nickname (core) | `NicknameCorpusQaTest` (4) | pass (DEF-007 marks, DEF-035, DEF-039 fixed during phase 1) |
| PROTO (core) | `ProtocolDecodingQaTest` (7) | pass |
| Oracle / corpora | `QaOracleSelfTest` (8), `QaCorporaWellFormedTest` (9) | pass |
| Server: SEC, PROTO, QM, ROOM, TIME, RECON, TEAM, CHAT, SAFE, TERMS, DEL, ACC, PRIV, REV, ADS-24, STAT, SRV | `ProtocolSecurityQaTest` (26), `MatchFlowQaTest` (31), `ChatSafetyQaTest` (15), `AccountLifecycleQaTest` (12) | pass; known server defects (DEF-012, DEF-048, DEF-049) are strict expected failures |
| Server corpus loader | `ServerCorpusSmokeQaTest` (3) | pass |
| Android (AGE, OFF, PRAC, ADS, TERMS, BILL, WIN, FONT, L10N) | `android/app/src/test/kotlin/app/numbaseball/qa/`: `OfflineFirstLaunchQaTest` (1), `AdsIntegrationQaTest` (7), `AdsOnlineQaTest` (5), `AdsLobbyQaTest` (1), `AdsGateQaTest` (14), `AgeGateQaTest` (10), `TermsGateQaTest` (10), `BillingQaTest` (14), `LocaleResourcesQaTest` (8), layout sweeps `LayoutSweeps.kt` (32 classes x 46 screens), `LocaleScreenshotsQaTest` (5 x 14 renders) | pass; open Android defects DEF-051..DEF-063 are strict expected failures (`knownDefect`) or layout baseline entries; see section 14.1 |

### 14.1 Android phase coverage (2026-11-25)

Run: `export ANDROID_HOME=/opt/android-sdk; cd android && ./gradlew --offline :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug`. Harness (`QaInfra.kt`, `QaDriver.kt`): real `MainActivity` / `NbApp` navigation, ViewModels, `InterstitialGate`, banner gate and `DefaultBillingRepository` on fakes (recording ads controller with the real `InterstitialPolicy`, scriptable Play gateway, unreachable account API). Defects are asserted with `knownDefect("DEF-0nn") { correct behaviour }`: it fails when the defect is fixed. Layout sweeps compare to `android/app/src/test/resources/qa/layout-baseline.tsv`.

| Criterion | Automated (A) | Remaining manual (M) |
| --- | --- | --- |
| AC-OFF (offline first launch: age, nickname, tutorial, home, full vs-computer match, result) | `OfflineFirstLaunchQaTest`: network monitor offline, every REST call fails, socket never connects; HARD computer solves; no ad, no socket session, account stays unregistered | real airplane mode, process kill and relaunch offline, retry on reconnect |
| AC-ADS banners | `AdsIntegrationQaTest.bannerOnlyOnCalmScreensAndNeverDuringAMatch`, `offlineHomeHasNoBanner`, `AdsLobbyQaTest` (lobby, chat, practice), `AdsSmallWindowQaTest` | real AdView layout, spacing to buttons, 320 dp creative |
| AC-ADS interstitial (cap 1 per 3 matches and 4 min, hold 1 s, never at start / during match, not with chat or keyboard, review room, practice, small windows, owner) | `AdsIntegrationQaTest` (3rd match under 4 min, 4th due, cap reset, result buttons, no ad at app start), `AdsOnlineQaTest` (online counts, review room, phrase sheet open, repeated snapshot counted once), `AdsGateQaTest` (IME, chat, next match, background, finger down / 5 s give-up, leaving the screen, purchase during the hold, unknown entitlement, ad not loaded, cap edge cases) | real AdMob load / show / close, back from the ad, process death during the ad |
| AC-ADS consent, `remove_ads` | owner and unknown entitlement: no consent call, no SDK start, no banner, no ad; refund starts consent; treatment per age band reaches `gatherConsent` | UMP form in EEA, "Privacy options" row, traffic capture (DEF-062) |
| AC-AGE | `AgeGateQaTest`: build config default 14 / G and wiring, boundaries for 13/14/16/18, under-age block (no account, no consent, no session), survives restart, band-only storage not in backup rules, no year in the terms request; DEF-051, DEF-052 | Play Age Signals, clear-data / reinstall behaviour (DEF-046) |
| AC-TERMS | `TermsGateQaTest`: first-time and changed wording, cannot skip, nothing online behind the gate, version `2026-11-v3` re-prompt after `v2`, `v4` bump, `terms_outdated`, join / create / quick gated, vs-computer free, under-age cannot agree; DEF-053 | chat gate after a version change in a running match |
| AC-BILL (purchase, pending, refund, restore, cached entitlement) | `BillingQaTest` (14): Settings and result link, Play price, ack and cache, cancel / error, pending then cleared, refund, restore (found / none / unreachable), offline cached owner, non-owner timeout, ack retry, other product, token never in logcat / stdout / toString / DataStore / backup | real Play sheet, license tester flows, slow test card, refund from Console, second device |
| AC-WIN, AC-FONT-01 | 360 x 320, 360 x 320 at 200 %, 360 x 800 at 200 % sweeps: no crash on 46 screens / states, every key control present and reachable (0 missing / unreachable); clipped text DEF-055, DEF-056, DEF-061 | split-screen / free-form resize while playing, rotation, Android 14 non-linear scaling on a device, TalkBack |
| AC-L10N-01/02 | `LocaleResourcesQaTest`: 429 strings and 8 plurals in all 10 languages, same placeholders, plural forms, no blanks, loads and formats through Android resources | native-speaker review |
| AC-L10N-04/05 (overflow) | sweeps `phone-*` (10 languages), `narrow-*` (de, fr, ja, ru, vi at 320 dp), renders `qa-locale-*`; findings DEF-054..DEF-060 | device check of font-dependent items (host fonts) |
