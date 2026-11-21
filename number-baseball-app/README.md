# Number Baseball for Android

A new Android app (Google Play) for the number-guessing duel game. It replaces the web prototype in `../number-baseball/`, which stays only as a reference for rules and wording.

## Product decisions

These are fixed for v1. Change them here first if the team agrees on something else.

### Concept (kept from the web version)
- Two players each pick a secret number of 3–6 **different** digits; 0 may lead.
- Every round both players guess at the same time. Strike = right digit, right place. Ball = right digit, wrong place. Out = nothing matches.
- Whoever gets all strikes first wins. Both in the same round = draw. Draws are a normal result, also for teams.
- Modes: vs Computer (offline), Online 1:1, Team play N vs N (teammates are paired 1:1 against the other team, more duel wins takes the match, ties allowed).
- The room creator can practise against the computer while waiting; the real match starts automatically when the room fills.
- Chat in rooms. **Text only**: no images, no emoji. Swear words are masked.
- 10 languages: ko, en, ja, zh-CN, es, fr, de, pt-BR, ru, vi. A beginner can learn the rules inside the app.
- Spirit: "a quiet quick match in a short break". Silent by default, one-handed, works in split-screen / pop-up windows, short reconnects survive an app switch.

### Sound (owner request 2026-11-21)
- **Sound effects**: keypad, sending a guess, the hint reveal (distinct for strike, ball and out), solving, win / lose / draw, match found, round timer warning, chat message, UI navigation. Short, crisp, ballpark-flavoured, never cartoonish.
- **Music**: one calm menu loop and one low-key match loop, off by default.
- Defaults keep the "quiet quick match" spirit: effects **on**, but muted automatically while the phone is in silent or vibrate mode (setting "Follow silent mode", on by default); music off. Separate volume for effects and music, plus haptics on/off.
- Audio focus: pause music and stop effects when another app takes focus, when the app goes to the background, or during calls. Ads stay muted.
- All audio is made in-house (synthesized) so licensing is clean; OGG Vorbis, loudness-normalized.

### Removed
- Spreadsheet (Excel) disguise mode and the Esc boss key.
- ID + password + email accounts and the password-reset mail flow.

### Accounts (reviewed and changed)
Decision: **no ID, no password, no email.**
- First launch creates a device account automatically. The player only picks a nickname (2–12 letters/digits, profanity-checked). Display form is `Nickname#1234`, so nicknames never collide.
- The server returns a long random account token. The app stores it and includes it in Android Auto Backup, so a new phone signed in to the same Google account restores the player.
- Settings has a **transfer code** (one-time, expires in 10 minutes) to move the account to another device by hand.
- Settings has **Delete account** (Play requirement), and the server hosts a web page for deletion requests without the app.
- Phase 2 option: Google Play Games sign-in as an extra restore path.

Why: one less screen before the first game, no passwords to leak, no email to collect or mail service to run, and a smaller Play Data safety declaration. Play still requires account deletion and chat safety tools, so those are in v1.

### Online rules
- Quick match (automatic pairing by digit count) and private rooms with a 5-character code.
- Team sizes 1 to 5 per side. Private rooms only for team play.
- Round timer: 60 s in quick match; room host picks 30 / 60 / 120 s or none. Two missed rounds in a row forfeit that duel.
- Disconnect: 60 s to come back before the duel is forfeited.
- Records: wins / losses / draws per account.

### Chat safety (Play UGC policy)
Profanity masking, rate limit, report a message or a player, block a player (hides their chat and avoids matching), mute all chat, preset quick phrases.
- **Quick match: quick phrases only.** Free text only in private rooms (people who shared a room code). Server flag `QUICK_MATCH_FREE_CHAT` (default off) can turn free text on later.
- Moderation: admin can restrict a player's chat; automatic 24 h chat restriction after reports from 3 different players within 24 h.

### Terms, deletion and review (from the release lead's policy review, `docs/play/POLICY_REVIEW.md`)
- **Terms gate** before the first online match or chat; cannot be skipped; acceptance stored with the terms version; shown again when the version changes. Server pages `/`, `/privacy`, `/terms`, `/delete-account` built from `docs/play/*.md`.
- **Deletion without the app**: the web page also accepts a request without an in-app code; the account is deleted after 30 days unless it is used again, and the app shows the pending request.
- Reports: resolved reports purged after 180 days; deleted players are anonymised in other reports' snapshots.
- No chat text, tokens, codes or IP addresses in logs or the database (rate-limit IPs in memory only).
- A review room behind `REVIEW_ROOM_CODE` so Play reviewers always find an opponent.
- **Audience (owner decision 2026-11-21): 13 and over.** Restrict Minor Access stays off. Ads use a maximum ad content rating suitable for teens; chat safety above is mandatory, not optional.
- **Distribution (owner decision 2026-11-21): worldwide including EEA/UK, with ads.** The owner accepts declaring as a trader, so legal name, address, phone and email will be shown on the store page.
- **App name (owner decision 2026-11-21): a "숫자야구" (Number Baseball) style name**, localized per language; the release lead proposes the names. Keep the name in resources so it is easy to change.

### Monetization: ads (Google AdMob)
The owner asked for Google ads. On Android that is **AdMob** (AdSense is for websites).
- **Never during a match.** No ad may cover, interrupt or appear next to the board while a duel or the secret entry is on screen, including the online waiting practice game.
- **Banner** (adaptive, anchored) only on calm screens: home, results, profile/stats. Not in the room lobby while players are joining, not in chat.
- **Interstitial** at most after a finished vs-computer match, never after an online match, with a frequency cap (at most one every 3 finished matches and 4 minutes apart), never on app start or exit.
- **Silent.** Ads are muted app-wide (fits "quiet quick match"); no rewarded ads that give an advantage online.
- **Consent.** Google UMP consent form where required (EEA/UK/Switzerland and US states), a "Privacy options" entry in Settings, ads still work (non-personalized) when consent is refused.
- **Store and policy.** "Contains ads" declared, Data safety updated (advertising ID / device identifiers collected and shared with Google for ads), privacy policy updated, `app-ads.txt` served by our server on the developer website domain.
- Test ad unit IDs in debug builds; real IDs only from build configuration, never committed.
- No external donation links (Play payments policy).

## Layout

| Path | What | Owner |
| --- | --- | --- |
| `backend/core` | Pure Kotlin game core: rules, judge, computer AI, match and team engine, protocol messages, nickname and chat filters. Shared by app and server. | Backend engineer |
| `backend/server` | Ktor server: WebSocket protocol, accounts, matchmaking, rooms, chat moderation, deletion page, SQLite. | Backend engineer |
| `android/app` | Android app (Kotlin, Jetpack Compose). | Android engineer |
| `design/` | Design system, screen specs, prototypes, review notes. | Design lead |
| `docs/protocol.md` | Client–server contract. | Backend engineer |
| `docs/qa/` | Test plan, acceptance criteria, defect reports. | QA lead |
| `docs/play/` | Play Store policies, listing, privacy policy, release steps. | Release lead |

## Building

```bash
cd backend && ./gradlew test        # core + server, no Android SDK needed
cd android && ./gradlew :app:testDebugUnitTest   # needs the Android SDK (Android Studio or CI)
```
