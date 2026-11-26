# Handoff for the next tech-lead session

Written 2026-11-25 by the previous tech-lead session (status table refreshed 2026-11-26 by the release lead; the "Findings 2026-11-25" section and the sections below it are historical), which became too long to run cheaply. Read this file and `README.md` (all product decisions) before doing anything else. Branch: `claude/number-baseball-game-1u4o9o` (push only there).

## The owner
- Writes in Korean, often from an Android phone. Answer in short, plain Korean. Environment changes (network hosts, setup scripts) can only be approved from a computer.
- Wants: an Android game for Google Play, built by a team; design/UI and testing done by very strict experts; work that resumes on its own after usage limits.
- Cost matters. The owner approved these savings rules (2026-11-23):
  1. **Fresh, focused agents.** Do not resume old agents (their contexts are huge). Spawn a new agent per task with a self-contained brief that points to files, not to past conversation.
  2. **Model per role.** `opus` for Android UI implementation and the design review. `sonnet` for translations, sound finishing, running/updating QA tests, docs.
  3. **At most 2 agents at a time.**
  4. Agents never commit; the tech lead commits and pushes after each report. A stop hook rejects ending a turn with uncommitted changes, so commit before ending every turn.

## Setup in a new container
```bash
bash number-baseball-app/tools/install-android-sdk.sh   # Android SDK at /opt/android-sdk, writes android/local.properties
export ANDROID_HOME=/opt/android-sdk
cd number-baseball-app/backend && ./gradlew test         # core + server
cd ../android && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:verifyRoborazziDebug
```
Network: dl.google.com and gcp.mirrors.maven.org are allowed in the environment (needed for Google Maven and Robolectric jars). GitHub Actions (`.github/workflows/number-baseball-app.yml`) builds and tests on every push to the branch.

## Status by workstream (2026-11-26)

Release status page with numbers, open defects, the device checklist and the owner's to-do list (Korean): `docs/play/RELEASE_READINESS.md`.

| Area | State | Remaining |
| --- | --- | --- |
| Product decisions | `README.md`, current (age screen asks birth year and month; ads always muted; `remove_ads` 4.99 USD) | Keep it updated when the owner decides something; README should point to the per-age-band ad settings (DEF-047) |
| Design (`design/`) | Phase 1 signed off; phase 2 reviews `REVIEW_PHASE2*.md`: round 2 said "Not signed off"; later fix rounds closed most items (layout sweeps clean except 7 entries at 360 x 320 and 200 % font) | One more design review round (round 3) for a sign-off verdict |
| Backend (`backend/`) | Complete. `./gradlew --offline test`: 271 tests green (core 128, server 143), no ignored scenarios, all server defects fixed | Deploy on Render (owner), see `backend/server/DEPLOY.md` |
| Android (`android/`) | Complete. `:app:testDebugUnitTest` 3045 cases, 0 failures (729 skipped by design, screenshot suites); lint 0 errors; `verifyRoborazziDebug` green (480 reference renders); `assembleDebug` ok; unsigned `assembleRelease` / `bundleRelease` ok with `NB_ALLOW_PLACEHOLDER_RELEASE=true` | Signed build needs the owner's `NB_*` values and upload key; device checks (RELEASE_READINESS section 4) |
| Sound (`design/sound/`) | 29 cues integrated (`res/raw`), `SOUND_DESIGN.md`, `INTEGRATION.md`, `audition.html` exist | Owner listens on a phone; device haptics check |
| QA (`docs/qa/`) | DEFECTS.md: no Blocker, no Major open. 15 Minor/Trivial open (DEF-016, 019, 020, 023 to 027, 029, 036, 043, 045 to 047, 061 partly) | Optional: product decisions on the open ones; device checks |
| Play release (`docs/play/`) | Checked against the code on 2026-11-26 and fixed (muting, age screen wording, signing/AdMob value names, permissions table). `RELEASE_READINESS.md` added | Owner fills the `[대괄호]` placeholders (email, name, address, phone, domain, hosting details, dates) before the release build, because the in-app texts and the web pages are generated from `docs/play/*.md` |
| Translations | 10 languages in `res/values*`, `LocaleResourcesQaTest` green (429 strings, 8 plurals) | Native-speaker review |
| CI | `.github/workflows/number-baseball-app.yml`: backend tests, Android verify + lint + debug APK + unsigned release bundle | Runs only on the feature branch; no signing step (add one only if the owner wants Actions to sign) |

## Findings 2026-11-25 (new session, historical: all fixed by 2026-11-26)
- `backend: ./gradlew test` fails to compile: `server/src/test/.../qa/QaClients.kt` was rewritten (QaInstance/QaServer.start) but the 5 QA test files still use the removed `QaHttp`. QA phase 2 must finish this migration first. `:core:test` is green.
- Android: 194 unit tests green; lint had 1 error (`NbApp.kt:542` LocalContextGetResourceValueCall), assigned to the Android phase 2 agent.
- Maven Central often answers HTTP 429 here; rerunning Gradle works because downloads are cached.

## New owner decision 2026-11-25: Remove ads purchase
README "Remove ads" + updated interstitial rule (now also after online matches). **Implemented** (Play Billing, Settings and result link, restore, interstitial rule; `BillingQaTest` 14 tests). Original brief, kept for the record: an Android agent adds Play Billing + Settings/results entry + interstitial rule change + tests; the release docs (`docs/play/`: Data safety, privacy policy, store listing "In-app purchases", RELEASE_GUIDE product setup in Play Console) get a sonnet docs pass. Owner must create the in-app product `remove_ads` in Play Console. Play docs are updated (2026-11-25; terms now `2026-11-v3`). Billing agent must also meet `docs/play/POLICY_CHECKLIST.md` 11.1 and 12 and POLICY_REVIEW Android item 13: purchase token never logged or sent to our server; after purchase no Mobile Ads init and no UMP form; online interstitial safeguards (not with chat/keyboard open, not if the player left the screen, not in the review room, not when `remove_ads` is owned).

## Suggested order
1. Android phase 2 (opus) and sound finishing (sonnet) in parallel.
2. Design review of the new renders (opus), then one Android fix round.
3. QA phase 2 (sonnet) on server and app; route defects to a fix agent.
4. Translations (sonnet), then a final regression run and the release checklist in `docs/play/RELEASE_GUIDE.md`.
5. Show the owner real app screenshots and the sound audition page (publish it as an Artifact) along the way.

## Waiting on the owner
The numbered Korean list with where each value goes is in `docs/play/RELEASE_READINESS.md` section 5. In short: contact email for the game, a domain (for `app-ads.txt`), deploying the server (Render, `backend/server/DEPLOY.md`), AdMob account and real ad unit IDs, Play Console account (trader declaration, minimum age 14, target audience 13+ groups), KIPRIS check of the name "숫자야구: 조용한 한판", upload key for signing.

## Usage limits
When agents stop with a usage-limit error, note the reset time and resume work after it. An hourly scheduled task bound to this session does the commit + resume check; delete it when the project is finished.

## Next steps (2026-11-26, end of the development pass)
- All development workstreams are done; see `docs/play/RELEASE_READINESS.md` (status, test numbers, open Minor/Trivial defects, manual device checklist, owner list).
- Fixed after the readiness pass: graceful-stop flake (writer flush wait before engine stop, `acSrv01` 6/6) and stale `DEFAULT_TERMS_VERSION`.
- Before the production release: (1) a final design sign-off review (opus) on the round-2 renders (`design/REVIEW_PHASE2_ROUND2.md` still says not signed off; fix round 2 addressed all its items); (2) make the release build fail when `docs/play/*.md` still contain `[대괄호]` placeholders (check in `generateLegalTexts`); (3) the manual on-device checklist once the owner has AdMob, Play Console and license testers.
- The hourly scheduled task was deleted because nothing runs while we wait on the owner. Create a new one when work resumes.

## After the final design review (2026-11-26)
- Design: **signed off with 5 minors** (`design/REVIEW_FINAL.md`). Release condition (best-hint 150 ms cross-fade, 08 §2) is done in `TeamBoard.kt`.
- M-3 (shrink floor, DESIGN_SYSTEM 11.3) is **not applied**: tried in `WordFit` (11 dp floor, and the 100 % size floor at font scale ≥ 1.5); both reintroduce clipping/word breaks in the QA layout sweeps (ru/de/ja/vi at 320 dp, 360×320 and 200 %). Needs layout work per screen; post-release minor. M-1, M-2, M-4 (de/ru wording), M-5 (7 findings) also post-release.
- Release build refuses `[placeholder]` blanks in the public sections of `docs/play/TERMS_OF_USE.md` / `PRIVACY_POLICY.md` (`checkReleaseConfig`; skipped with `NB_ALLOW_PLACEHOLDER_RELEASE=true` for CI).
- Remaining work is waiting on the owner (`docs/play/RELEASE_READINESS.md` §5), then the manual on-device checklist.
