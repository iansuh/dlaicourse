# Handoff for the next tech-lead session

Written 2026-11-25 by the previous tech-lead session, which became too long to run cheaply. Read this file and `README.md` (all product decisions) before doing anything else. Branch: `claude/number-baseball-game-1u4o9o` (push only there).

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
cd ../android && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```
Network: dl.google.com and gcp.mirrors.maven.org are allowed in the environment (needed for Google Maven and Robolectric jars). GitHub Actions (`.github/workflows/number-baseball-app.yml`) builds and tests on every push to the branch.

## Status by workstream (2026-11-25)

| Area | State | Remaining |
| --- | --- | --- |
| Product decisions | `README.md`, current | Keep it updated when the owner decides something |
| Design (`design/`) | Phase 1 signed off: DESIGN_SYSTEM.md, tokens.json, 15 screen specs + FLOW/ADAPTIVE, prototypes, 48 reference renders in `design/shots/`, fonts, icon, store assets | Phase 2: review the Android Roborazzi renders against `design/shots` (more than 1 dp off or a non-token colour = defect), up to 2 fix rounds |
| Backend (`backend/`) | Complete: protocol (`docs/protocol.md`), core, Ktor server, Dockerfile, `server/DEPLOY.md`. `./gradlew test` green (QA's 40 server scenarios still `@Ignore`) | Fix defects QA finds |
| Android (`android/`) | Phase 1 complete (architecture, data, online client, age/terms gates, ads + UMP + Age Signals, audio/haptics hooks, CI). Phase 2 (designed UI) **in progress**: builds, 194 tests green; screens partly converted to the design | Finish every screen per `design/screens/*.md` and `design/shots`; re-record Roborazzi; `verifyRoborazziDebug`, lint, tests green; wire real sound files |
| Sound (`design/sound/`) | 29 OGG cues in `out/`, generator `tools/`, `REPORT.md`, spectrograms | `SOUND_DESIGN.md`, `audition.html` (owner listens on a phone), `INTEGRATION.md`; timer warning must be one cue at 10 s then ticks for the last 5 s (DESIGN_SYSTEM 8.3/10) |
| QA (`docs/qa/`) | TEST_PLAN.md (220 criteria), corpora, oracle + differential tests (pass), 40 server scenarios written, DEFECTS.md (47 logged: 38 open, many likely fixed since) | Phase 2: update scenarios to the final protocol (terms + `ageConfirmed`, timers 30/60/120/600, `chat_not_allowed`), run the server with `RELAXED_RATE_LIMITS=true TEST_HOOKS=true`, un-ignore, re-verify defects; then Android checks (offline vs-computer from first launch in airplane mode, ads never in matches, age gate, terms gate, small window, font scale 200%, locales) |
| Play release (`docs/play/`) | Policy checklist, review, privacy policy, terms, deletion page, store listing (10 languages), Korean release guide | Fill placeholders once the owner provides contact email/domain |
| Translations | App strings exist in `values/` (en) and `values-ko/` | 8 locales: ja, `b+zh+Hans`, es, fr, de, `pt` (Brazilian), ru, vi. Store listing texts already exist in `docs/play/STORE_LISTING.md` |

## Suggested order
1. Android phase 2 (opus) and sound finishing (sonnet) in parallel.
2. Design review of the new renders (opus), then one Android fix round.
3. QA phase 2 (sonnet) on server and app; route defects to a fix agent.
4. Translations (sonnet), then a final regression run and the release checklist in `docs/play/RELEASE_GUIDE.md`.
5. Show the owner real app screenshots and the sound audition page (publish it as an Artifact) along the way.

## Waiting on the owner
Contact email for the game, a domain (for `app-ads.txt`), deploying the server (Render, `backend/server/DEPLOY.md`), AdMob account and real ad unit IDs, Play Console account (trader declaration, minimum age 14, target audience 13+ groups), KIPRIS check of the name "숫자야구: 조용한 한판", upload key for signing.

## Usage limits
When agents stop with a usage-limit error, note the reset time and resume work after it. An hourly scheduled task bound to this session does the commit + resume check; delete it when the project is finished.
