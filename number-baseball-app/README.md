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
