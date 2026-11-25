# Number Baseball — Android app

Kotlin + Jetpack Compose (Material 3 on the design tokens), single activity, Compose Navigation, ViewModels with
immutable `StateFlow` state and a small manual DI container (`AppContainer`). The game rules, computer AI, duel engine,
protocol messages and text filters come from the shared `:core` module in `../backend` (composite build).

## Open and build

- Android Studio (recent stable) → **Open** → this `android/` folder. JDK 17+ (CI uses 21). The Android SDK needs
  platform 36; Studio installs it when asked. Command line: put `sdk.dir=/path/to/sdk` in `local.properties`
  (git-ignored) or set `ANDROID_HOME`.
- `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- Debug builds talk to `http://10.0.2.2:8080` (the host machine seen from the emulator); override with
  `-PNB_SERVER_URL=https://...`. Debug always uses Google's **test** AdMob IDs.

## Tests

| Command | What |
| --- | --- |
| `./gradlew :app:testDebugUnitTest` | All JVM, Robolectric and Compose UI tests (screenshot tests only render) |
| `./gradlew :app:recordRoborazziDebug` | Same, and writes the screenshots to `app/screenshots/` |
| `./gradlew :app:verifyRoborazziDebug` | Same, and fails if a screenshot differs from `app/screenshots/` (CI) |
| `./gradlew :app:lintDebug` | Android lint (`app/build/reports/lint-results-debug.html`) |

Screenshots (`src/test/.../screenshots/Screenshots.kt`): every screen and state, named after the reference renders in
`../design/shots/` so they can be compared side by side: `match-guess-ko.png` here ↔ `design/shots/match-guess-ko.png`.
Variants: `<name>-ko` / `<name>-en` phone 360 × 800 dp; `<name>-small-<lang>` split-screen 360 × 320 dp (the match is
`match-small-<lang>` like the shot); `<name>-ko-font200` at 200 % font scale. States without a reference shot use the
same scheme (`home-offline-ko`, `profile-ko`, `cpu-setup-ko`, …). The frame reproduces the shots' window: 24 dp status
bar and gesture area as real insets, a 230 dp IME on text-field screens and the shots' sample creative in the ad band
(test only, `LocalAdPreview`). After a UI change run `recordRoborazziDebug`, look at the diff, and commit the new PNGs
with the change; CI verifies them. Diff images from a failed CI run are in the `android-test-reports` artifact.

Ads, audio and haptics are disabled in tests: `TestNumBaseballApp` (set in `src/test/resources/robolectric.properties`)
supplies a fake container with `DisabledAdsController`, `SilentGameAudio` and fakes for network and storage; screens
read `LocalAdsController` / `LocalGameAudio` / `LocalGameHaptics`, whose defaults are the silent no-op versions. The
banner container has the test tag `adBanner`.

## Release builds

`./gradlew :app:bundleRelease` (R8 + resource shrinking) needs these Gradle properties or environment variables — never
commit them:

| Name | Purpose |
| --- | --- |
| `NB_SERVER_URL` | Server base URL, e.g. `https://nb.example.com` |
| `NB_CONTACT_EMAIL` | Contact address shown in Settings |
| `NB_ADMOB_APP_ID`, `NB_ADMOB_BANNER_ID`, `NB_ADMOB_INTERSTITIAL_ID` | Real AdMob IDs |
| `NB_RELEASE_STORE_FILE`, `NB_RELEASE_STORE_PASSWORD`, `NB_RELEASE_KEY_ALIAS`, `NB_RELEASE_KEY_PASSWORD` | Upload key; without them the bundle is unsigned |
| `NB_MINIMUM_AGE` (default 14), `NB_MAX_AD_CONTENT_RATING` (default `G`) | Age gate and the AdMob cap; the cap must not exceed the IARC rating |

A release without the real server URL, contact email or AdMob IDs fails in `checkReleaseConfig`. CI sets
`NB_ALLOW_PLACEHOLDER_RELEASE=true` to build an unsigned check bundle with placeholders; signing such a bundle is refused.

## Structure (`app/src/main/kotlin/app/numbaseball`)

| Package | What |
| --- | --- |
| `data/account` | Device account: token + identity in DataStore (`account` file, the only file in Auto Backup), REST bootstrap, nickname pre-check, terms acceptance |
| `data/age` | First-launch age band (device only, not backed up) and Play Age Signals |
| `data/online` | `WebSocketOnlineClient` (`/ws/v1`: bearer auth, reconnect 1/2/4/5 s, ping, background grace, no reconnect after 401/403/4000/4003/4004), `OnlineSession` (room/chat/queue snapshots, resync after reconnect), `RestApi` (`/api/v1`), block/report |
| `data/settings` | Sound, music, follow silent mode, vibration, chat mute; per-app language via AppCompat |
| `game/offline` | vs-computer and waiting-room practice on the core `Duel` + `ComputerPlayer`, on `Dispatchers.Default` |
| `ads` | UMP consent, Mobile Ads init after consent, age treatment, muted ads, adaptive banner, interstitial frequency cap |
| `audio` | SoundPool effects, looping music, audio focus, silent-mode gating; cue list from the README (files from design/sound) |
| `ui` | Theme from `design/tokens.json`, navigation graph, one package per screen (stateless screen + ViewModel) |

The terms and privacy policy shown in the app are generated at build time from the public sections of
`../docs/play/TERMS_OF_USE.md` and `PRIVACY_POLICY.md` (`generateLegalTexts`), so edit them there.

Languages: `values/` (English) and `values-ko/`. Add a language by adding `values-xx/strings.xml`, its tag to
`res/xml/locales_config.xml` and `SupportedLanguages`; `ResourcesTest` checks they agree.

## Deviations from the design (for design sign-off)

Written down per the phase 2 review (`../design/REVIEW_PHASE2.md`); none is accepted until design approves it.

| Spec | What the app does | Why |
| --- | --- | --- |
| 10 Profile: "자리수별 기록" and "모드별" tables (D2-15) | Both sections are laid out as specified but show the empty state (`–`) | `/api/v1/me` returns only total W/L/D and the app keeps no per-digit or per-mode history. Needs per-digit / per-mode stats in the account response (server change). |
| DESIGN_SYSTEM 3.3 numeral cap | Every `num` (Barlow) text is capped at `size × min(fontScale, 1.3)` in the shared `Text`, not only fixed controls | All numerals sit in fixed plates, columns or tags; capping in one place guarantees no control clips them. |
| 09 / 12.15: tapping the sender name opens the player card (D2-10) | The player card opens from the long-press sheet ("플레이어 정보 보기") and as a TalkBack action on the bubble | The 20 dp name line cannot get a 48 dp target without overlapping the bubble. |
| 08 reconnect countdown (D2-14) | Counted from the server time the session first saw the player offline (shared by the duel strip and the team board) | The protocol sends no disconnect deadline; exact only if this client was connected when the player dropped. |
| 05 §2.9 "상대 기록 보기" sheet, 12.17 server-error dialog | Not built; errors use toasts | Separate items, not part of review round 1. |
| 01 §2 tutorial: optional team-play card, "tutorial seen" flag | Not built; first run always goes age → nickname → tutorial → Home once, Home's help replays it | Small follow-up. |

## CI

`.github/workflows/number-baseball-app.yml` runs on pushes to the feature branch that touch `number-baseball-app/**`
(and on demand): backend tests, then for the app the tests with Roborazzi verify, lint, the debug APK and an unsigned
release bundle, uploading the APK, lint report and test/screenshot reports as artifacts.
