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
  is `ADS_ALLOWED` (`NbApp.AdsAfterAgeCheck`), so an owner never sees the consent form and the SDK is never initialised;
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

Languages: `values/` (English) and `values-ko/`. Add a language by adding `values-xx/strings.xml`, its tag to
`res/xml/locales_config.xml` and `SupportedLanguages`; `ResourcesTest` checks they agree.

## Deviations from the design (for design sign-off)

Written down per the phase 2 review (`../design/REVIEW_PHASE2.md`); none is accepted until design approves it.

| Spec | What the app does | Why |
| --- | --- | --- |
| 10 Profile: "자리수별 기록" and "모드별" tables (D2-15) | Both sections are laid out as specified but show the empty state (`–`) | `/api/v1/me` returns only total W/L/D and the app keeps no per-digit or per-mode history. Needs per-digit / per-mode stats in the account response (server change). |
| 09 / 12.15: tapping the sender name opens the player card (D2-10) | The player card opens from the long-press sheet ("플레이어 정보 보기") and as a TalkBack action on the bubble | The 20 dp name line cannot get a 48 dp target without overlapping the bubble. |
| 08 reconnect countdown (D2-14) | Counted from the server time the session first saw the player offline (shared by the duel strip and the team board) | The protocol sends no disconnect deadline; exact only if this client was connected when the player dropped. |
| 05 §2.9 "상대 기록 보기" sheet, 12.17 server-error dialog | Not built; errors use toasts | Separate items, not part of review round 1. |
| 01 §2 tutorial: optional team-play card, "tutorial seen" flag | Not built; first run always goes age → nickname → tutorial → Home once, Home's help replays it | Small follow-up. |
| 06 §3 "never in the review room" | The review room is recognised by its bot opponent (`REVIEW_BOT_ID = "reviewbot0001"`, the server's `Hub.BOT_ID`) | The protocol has no review-room flag; a `RoomView` flag from the server would be cleaner. |
| 06 §3 hold "1000 ms since the result was decided" | Counted from the moment the final reveal ends on the match screen (later than the decision); a finger held down for 5 s skips the ad instead of showing it late | The client sees the decision and the reveal end as separate moments; counting from the later one is the safer reading. The 5 s give-up is not in the spec. |
| 11 §2 delete-account dialog | Adds a second paragraph: the remove-ads purchase stays with the Google account and Restore purchase brings it back | Required by POLICY_REVIEW Android item 13; the design text predates the purchase. |
| 11 §1.1 "개인정보 보호 옵션 keeps following UMP" vs POLICY_REVIEW item 13 "keep Privacy options hidden" | Hidden for an owner because UMP never runs; shown after a purchase made later in the same process, until restart | Satisfies both texts; design and release lead should agree on one wording. |
| 08 §1 leave dialog on the live board | Title reuses "방에서 나갈까요?", new body "내 대결은 끝났어요. 지금 나가면 팀 결과를 볼 수 없어요." · 취소 · 나가기 | The design names the dialog but gives no copy. |
| 08 §2 best hint 150 ms cross-fade | Hints update without the cross-fade | Small follow-up. |
| ADAPTIVE §1 small-window team rows | The status column shows the round over the winner ("승 A"), a status word, or both best hints ("2S 1B · 1S 2B"); "진행 중" is dropped there | 56 dp rows have room for two lines in the 72 dp column. |

## CI

`.github/workflows/number-baseball-app.yml` runs on pushes to the feature branch that touch `number-baseball-app/**`
(and on demand): backend tests, then for the app the tests with Roborazzi verify, lint, the debug APK and an unsigned
release bundle, uploading the APK, lint report and test/screenshot reports as artifacts.
