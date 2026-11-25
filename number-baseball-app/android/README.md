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
Variants: `<name>-ko` / `<name>-en` phone 360 × 800 dp (plus `-de` / `-ru` / `-vi` for home, lobby, match-guess,
match-small, result-lose and settings); `<name>-small-<lang>` split-screen 360 × 320 dp (the match is
`match-small-<lang>` like the shot); `<name>-ko-font200` at 200 % font scale; `window-too-small-*` at 300 × 300 dp. States without a reference shot use the
same scheme (`home-offline-ko`, `profile-ko`, `cpu-setup-ko`, …). The frame reproduces the shots' window: 24 dp status
bar and gesture area as real insets, a 230 dp IME on text-field screens and the shots' sample creative in the ad band
(test only, `LocalAdPreview`). After a UI change run `recordRoborazziDebug`, look at the diff, and commit the new PNGs
with the change; CI verifies them. Diff images from a failed CI run are in the `android-test-reports` artifact.

Ads, billing, audio and haptics are disabled in tests: `TestNumBaseballApp` (set in `src/test/resources/robolectric.properties`)
supplies a fake container with `DisabledAdsController`, `FakeBillingRepository`, `SilentGameAudio` and fakes for network and storage; screens
read `LocalAdsController` / `LocalGameAudio` / `LocalGameHaptics`, whose defaults are the silent no-op versions. The
banner container has the test tag `adBanner`.

## Remove ads (Google Play Billing)

Owner decision 2026-11-25 (README "Remove ads", `docs/play/POLICY_CHECKLIST.md` 12, `POLICY_REVIEW.md` R22 / Android item 13):
one non-consumable in-app product **`remove_ads`** sold only through Google Play Billing (`com.android.billingclient:billing-ktx`
9.1.0, `libs.versions.toml`). The library adds the `com.android.vending.BILLING` permission itself.

How it works (`billing/DefaultBillingRepository.kt`):
- Connects at start with retry (1, 2, 4, 8 s for retryable codes; `enableAutoServiceReconnection` for later drops) and runs
  `queryPurchasesAsync` at start (`MainActivity.onCreate`) and on every resume; "구매 복원" / "Restore purchase" re-queries.
- Grants only on `PURCHASED`; `PENDING` shows the pending row and keeps ads; a purchase that disappears (refund, revocation)
  removes the entitlement and ads return. Unacknowledged purchases are acknowledged at once and again at the next query if
  that failed (3-day rule).
- The price is always `ProductDetails.oneTimePurchaseOfferDetails.formattedPrice`; nothing is hard-coded.
- A cached boolean (`billing` DataStore file) answers at once on an offline start; it is replaced by every successful Play
  answer and kept while Play cannot be reached.
- `entitlement` is `UNKNOWN` until the cache says "owned", Play answered, or 5 s passed. UMP and Mobile Ads start only once it
  is `ADS_ALLOWED` (`NbApp.AdsAfterAgeCheck`), so an owner never sees the consent form and the SDK is never initialised
  (the SDK's auto-init `MobileAdsInitProvider` is removed from the merged manifest and app measurement is delayed, DEF-062;
  `AdsIntegrationQaTest.def062_*` checks the merged manifest);
  banners (`AdBanner`) and interstitials check it too. "Privacy options" follows UMP's `REQUIRED` status: hidden for an owner
  (UMP never runs), still shown if consent was gathered earlier in the same process (consent can still be changed).
- The purchase token stays inside `billing/`: `PlayPurchase.toString()` hides it, nothing in the package logs, and it never
  goes to our server (`RemoveAdsRulesTest.thePurchaseTokenStaysInTheBillingPackage` checks the sources).
- Entry points: Settings "구매" section (11 §1.1) and the quiet top-bar text button on duel and final team results (06 §1),
  hidden while billing is unavailable or pending, in the review room and practice, and in windows lower than 480 dp. No
  pop-ups, never during a match.

Set-up and testing (owner / release lead; details in `../docs/play/RELEASE_GUIDE.md`):
1. Upload a build with Play Billing to the internal test track, then create the in-app product **`remove_ads`** (one-time,
   US$4.99 with Play's local price templates) in Play Console → Monetize → Products → In-app products, and activate it.
2. Add tester accounts under Play Console → Settings → **License testing**; they buy without being charged and can pick
   "slow test card" outcomes to try `PENDING`, approval and decline.
3. Install the build from the internal track (or a debug build with the same application id signed by any key, with the
   tester account signed in to Play; product details only load for an app known to Play Console). Check: price shown equals
   Play's, purchase removes banners at once and no interstitial follows, restore after reinstall and on a second device,
   refund from the Play Console order brings ads back after the next resume, no UMP form after purchase.
4. Robolectric tests never touch Play: `FakeBillingGateway` / `FakeBillingRepository` (`src/test/.../testing/BillingFakes.kt`).

## Interstitial rule

`ads/InterstitialPolicy.kt` (`InterstitialRules.decide`, unit tested in `InterstitialPolicyTest.kt`): after a finished vs-computer
**or** online match, on the way to the results screen, at most one per 3 finished matches (both kinds counted together, a team
match once) and ≥ 4 minutes apart (process start counts as an ad, so nothing comes right after launch). `InterstitialGate` holds on
the final frame for ≥ 1 s with no finger down (and 300 ms after the last touch up), then shows a loaded ad or continues at once.
Skipped: practice, review room (recognised by the server's bot opponent), cancelled matches, windows < 480 dp, chat sheet,
phrase sheet or keyboard open, app in the background, a rematch / next match already started, `remove_ads` owned; if the
player leaves the screen during the hold the gate is cancelled with the screen and the ad never shows later. The result buttons
never trigger an ad. A result reached through an ad shows no banner (`ResultsRoute.afterAd`, `TeamBoardRoute.afterAd`).

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
| `ads` | UMP consent, Mobile Ads init after consent, age treatment, muted ads, adaptive banner; interstitial rule (`InterstitialRules`, pure) and the gate between a match end and its results (`InterstitialGate`) |
| `billing` | "Remove ads" (`remove_ads`): `BillingRepository` (`DefaultBillingRepository` = all decisions, unit tested), `PlayBillingGateway` (Play Billing Library 9.x), cached entitlement in DataStore (`billing` file, not backed up) |
| `audio` | SoundPool effects, looping music, audio focus, silent-mode gating; cue list from the README (files from design/sound) |
| `ui` | Theme from `design/tokens.json`, navigation graph, one package per screen (stateless screen + ViewModel) |

The terms and privacy policy shown in the app are generated at build time from the public sections of
`../docs/play/TERMS_OF_USE.md` and `PRIVACY_POLICY.md` (`generateLegalTexts`), so edit them there.

Languages: ten (`values/` English, `values-ko/`, `ja`, `b+zh+Hans`, `es`, `fr`, `de`, `pt`, `ru`, `vi`). Add a language by adding `values-xx/strings.xml`, its tag to
`res/xml/locales_config.xml` and `SupportedLanguages`; `ResourcesTest` checks they agree.

## Deviations from the design (for design sign-off)

Updated after design review round 2 (`../design/REVIEW_PHASE2_ROUND2.md`, §3 decisions). Rows marked *accepted* were
approved there; the rest still need design approval.

| Spec | What the app does | Why / status |
| --- | --- | --- |
| 10 Profile: "자리수별 기록" and "모드별" (D2-15) | Both sections (headers included) are hidden until `/api/v1/me` sends per-digit / per-mode records; they render as specified when the data exists (`ProfileUiState.byDigits` / `byMode`) | Interim reduced profile approved in round 2 (a table of dashes was rejected). Needs the server change. |
| 01 §0 age screen: birth year only | Birth **year and month** (four plates + two plates, one keypad); age = full years as of the current month, the birth month counts as passed; only the band is stored | Owner decision 2026-11-25 on DEF-051 (minimum age 14 must hold). Copy updated in all ten languages. Design to update 01 §0 and the `age-*` shots. |
| 09 / 12.15 player card from the name line (D2-10) | From the long-press sheet ("플레이어 정보 보기") and as a TalkBack action | *Accepted* (round 2). |
| 08 reconnect countdown (D2-14) | Counted from the server time the session first saw the player offline | *Accepted as interim* until protocol 1.2 sends `disconnectDeadline`. |
| 08 / ADAPTIVE §1 team status | "재접속 대기" over the countdown on its own line; small windows and large text use the short word (`status_reconnecting_short`: "재접속" / "Away") | D2-03, D2-44; the short string is the one design proposed for 08 §5. |
| 01 §2 tutorial: optional team-play card, "seen" flag | Not built; Home's help replays the tutorial | *Accepted for 1.0*. |
| 06 §3 "never in the review room" | Recognised by the bot opponent (`REVIEW_BOT_ID`) | *Accepted*; a `RoomView` flag is preferred for 1.1. |
| 06 §3 interstitial hold | Counted from the end of the final reveal; a finger held for 5 s skips the ad | *Accepted*. |
| 11 §2 delete-account dialog | Extra paragraph: the remove-ads purchase stays with the Google account | *Accepted*. |
| 11 §1.1 개인정보 보호 옵션 | Hidden for an owner (UMP never runs), shown after a purchase in the same process | *Accepted*. |
| 08 §1 leave dialog on the live board | "방에서 나갈까요?" / "내 대결은 끝났어요. 지금 나가면 팀 결과를 볼 수 없어요." · 취소 · 나가기 | *Accepted*. |
| 08 §2 best hint 150 ms cross-fade | Not yet | *Accepted as a follow-up*; must land before release. |
| ADAPTIVE §1 small-window team rows | Round over the winner, a status word, or both best hints | *Accepted*. |
| 11 §2 transfer plates (D2-51) | 3 groups of 4 `numCode` plates, 28 × 40 dp, narrower when 12 plates do not fit 328 dp (24.5 dp at 360 dp); expired: plates disabled, "코드가 만료됐어요", "새 코드 만들기" | Waiting for design's 11 §2 update; the 28 dp plates only fit from about 380 dp. |
| 12.17 server-error dialog (D2-43) | Built (`ServerErrorDialog`): used for 5xx / unknown codes on the terms gate (다시 시도 = agree again, 홈으로 = close), join, lobby (홈으로 = leave the room) and settings (홈으로 = back). Other errors stay toasts | The catalogue names no screen-specific actions; these are the obvious ones. |
| 05 §2.9 opponent-log sheet (D2-43) | Built (`OpponentLogSheet`): from the duel strip's opponent area, the overflow menu ("상대 기록 보기") and the log header button when the opponent column is hidden (6 digits at large text, log narrower than 320 dp) | — |
| Large text, team final top bar | The "광고 제거" link is hidden at large text (as in small windows) | The title would lose its width; Settings keeps the purchase. |
| Large text / long translations (DEF-054 … 061) | One-line and two-line labels that would clip, ellipsize or break inside a word step their size down (to 70 %, 50–55 % for segment labels, table headers, buttons and the team badge) before anything else (`WordFit` in `design/Text.kt`); the lobby's copy / share buttons stack at large text; list-row values move under the title at large text; small-window opponent status at large text shows the lamp and countdown only (the word is read by TalkBack) | 7 findings remain, all in the 360 × 320 window at 200 % (see `src/test/resources/qa/layout-baseline.tsv`). |
| Lobby practice button (D2-48) | Scrolls with the content at large text and when the region is shorter than 420 dp; small windows keep it in the chat bar | The small window with the reconnect banner still shows only part of a seat before scrolling. |

## CI

`.github/workflows/number-baseball-app.yml` runs on pushes to the feature branch that touch `number-baseball-app/**`
(and on demand): backend tests, then for the app the tests with Roborazzi verify, lint, the debug APK and an unsigned
release bundle, uploading the APK, lint report and test/screenshot reports as artifacts.
