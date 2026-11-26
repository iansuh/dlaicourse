# Review log

Self-review by the design lead. Every round: render all prototype pages with Playwright (360 × 800 at density 2, small window 360 × 320), open every PNG, list concrete problems, fix, re-render. Shots are overwritten in place in [`shots/`](shots/); intermediate rounds were archived outside the repo for before/after comparison.

Rendering setup: Chromium via Playwright; Roboto and Noto Sans KR injected at render time as stand-ins for the Android system faces (not committed); Barlow Condensed loaded from [`fonts/`](fonts/); all colours and sizes from `prototype/css/tokens.css`, generated from [`tokens.json`](tokens.json) by a script that also re-verifies every recorded contrast ratio.

Severity: **B** = blocks sign-off (wrong, broken, unreadable, against a rule); **M** = visibly off (alignment, rhythm, hierarchy); **m** = polish.

---

## Round 1: first complete set (24 shots)

| # | Screen / shot | Problem | Sev | Fix |
| --- | --- | --- | --- | --- |
| 1 | match-guess-ko | History clipped mid-row: row 1 cut through its digits. Memo board took 208 dp vs 130 dp for the log at n = 4 | B | Memo toolbar and header 36 → 32 dp, cells 32 → 28 dp (tokens); log keeps latest + one row or the memo auto-collapses; 16 dp fade on the scrolling log |
| 2 | match-guess-ru | Memo help truncated ("нет → есть → …"), and it is redundant once marks exist | M | Help shows only while the memo is empty; TalkBack hint carries the instruction |
| 3 | match-guess-* | Full diagonal slash through 6 of 10 keypad digits: keypad reads as broken and noisy | M | Keys show a 9 dp corner × instead; the memo board keeps the crossed-out digit (board = notation, keys = controls) |
| 4 | match-guess-* | Used-in-guess keys and excluded keys looked alike | M | Used = `surface1` + `textDisabled`; excluded = `text3` + corner × |
| 5 | match-guess-* | Active slot had both a 2 dp outline and an underscore cursor that read as a typed "_" | m | Outline only |
| 6 | match-waiting-ko | Waiting lamps stacked above the label (grid key) and the live status was in disabled grey | B | Inline flex layout; label `text2` |
| 7 | match-secret-ko | Title duplicated in top bar and body; 260 dp void; lone "무작위" button floating left | M | Top bar shows the mode; legend fills the log area; random moved into the keypad action row |
| 8 | match-6digits-ko | S and B lamp groups merged into one 12-lamp strip; memo pushed the log to one row | B | 52 dp hint columns and 2 dp lamp gaps for n ≥ 5; auto-collapse rule |
| 9 | match-small-* | Memo toggle missing (CSS order bug); opponent status lost entirely | B | Fixed order; opponent status line in the compact top bar |
| 10 | lobby-ko | My seat right-aligned (class name collision with the duel strip) | B | Renamed `.mine` → `.mysecret` |
| 11 | home-ko | 190 dp dead void mid-screen; looked unfinished | M | Mode rows moved down into the thumb zone; hero gets record + recent-form lamps |
| 12 | home-ko | Amber three-dot mark read as a loading/overflow ellipsis, and used strike lamps decoratively (breaks rule 1) | M | Replaced by the brand mark (icon plate + 3 + lamps = "three strikes") |
| 13 | home-de/ru | Quick-match title wrapped against a right-aligned 2-line caption | M | Title and caption stacked, left-aligned |
| 14 | result-win-ko | Win tint started under the top bar: hard band edge at y = 80 dp | M | Tint drawn from the window top, 360 dp fade |
| 15 | result-win-en | Mixed axes: centred hero, left-labelled reveal; "Their number" wrapped | M | Captions centred above the plates |
| 16 | result-* | 150 dp void between stats and buttons | M | Line score (strikes per round, both players), the pitch log condensed |
| 17 | result-cpu-lose-ko | Banner and interstitial notice on the same screen | B | One ad per moment: no banner when an interstitial is due |
| 18 | team-final-ko | "다시 하기" wrong for a room match; banner missing on a result | M | "방으로 돌아가기"; banner added |
| 19 | lobby-ko | Title "방" alone (empty code placeholder) | M | "친구 방" |
| 20 | lobby-en | Copy button 40 dp next to 52 dp plates; "Copy" touching its border | M | Centred plates; two equal 48 dp tonal buttons below |
| 21 | lobby-en | Status sentence truncated | M | Own line under the count |
| 22 | lobby-ko | "이 팀으로" button hung under one column only | m | The empty seat on the other team is the move target |
| 23 | team-board-ko | Tag truncated instead of the name ("Größenwahn#7…") | M | Name ellipsizes, tag never |
| 24 | team-board-ko | Best hint under names had no label | M | "최고" prefix |
| 25 | nickname-ko | Korean orphan "요." on the second line | M | Word-based breaking (`keep-all`; Android `LineBreak.WordBreak.Phrase`) |
| 26 | nickname-ko | Shot without the IME misrepresented first run (field autofocuses) | m | IME placeholder; CTA pinned above it |
| 27 | settings-ko | Full-bleed line under every section header plus inset dividers: double rhythm | m | Sections separated by spacing only |
| 28 | settings-ko | External link drawn with a rotated back arrow | m | Proper glyph (later removed: app only) |
| 29 | settings-ko-end | No scrolled state for the top bar | m | `surface1` + hairline when scrolled |

## Round 2 (28 shots)

| # | Screen / shot | Problem | Sev | Fix |
| --- | --- | --- | --- | --- |
| 1 | match-waiting-ko | Keypad still looked enabled while waiting | B | `locked` keypad state: all keys `surface1` / `textDisabled` |
| 2 | match-secret-ko | Legend was a bordered card (violates "bands, not cards") | M | Legend rendered as the empty state of the pitch log (header + 44 dp rows) |
| 3 | match-small-de | "Kestrel · Geworfen" truncated next to the memo toggle | M | Compact status shows lamp + status word only |
| 4 | home-ko | Overflow at 800 dp: banner pushed off-screen (hero numerals at 56 sp) | B | Record numerals `numStat`, name `title`, tighter hero spacing |
| 5 | result-* | Line-score row labels above the numeral baseline; "컴퓨터 …" truncated; "3:12" unlabelled | M | Baseline alignment; 80 dp label column with a short `cpuShort` string; timer icon + label |
| 6 | team-board-ko | "Watching" note stranded 600 dp below the rows | m | Note directly under the rows |
| 7 | settings-ko-end | Status-bar area did not follow the scrolled top bar | m | Status-bar area takes `surface1` too |

Checked and accepted this round: match-guess (all three rows visible, quiet keypad), lobby (all languages), team board, nickname with IME, result composition.

## Round 3: added policy, safety and store screens (44 shots)

New pages: terms gate, report/block flow, chat (actions, rate limit), quick-match search, reconnecting, quick phrases, settings with all required rows, late-game store hero.

| # | Screen / shot | Problem | Sev | Fix |
| --- | --- | --- | --- | --- |
| 1 | chat-ko | Bubbles stretched to 80 % width regardless of text; a space between name and tag | M | Bubbles hug content; name + tag joined |
| 2 | match-reconnect-ko | Countdown shown twice; Pitch said "waiting for opponent" during my own reconnect; clock kept running visually | B | Countdown only on the right; Pitch disabled "던지기"; clock `paused` state |
| 3 | match-secret-ko | Remaining gap between legend and heading | m | Accepted: separates reference from action; collapses on shorter screens |
| 4 | icon / feature graphic | Plate hinge line crossed the numeral and read as a strike-through, which means "excluded" in our own system | B | Hinge removed from icon, mark and store art |
| 5 | store ko-01 | Caption orphan word ("…맞히면 / 승리") | M | Balanced caption wrapping |
| 6 | store 02 | Crop started mid-row | M | Crop starts at the latest log row |
| 7 | store 05 | Split-screen window cut the keypad's second row | B | Fixed heights for the other-app placeholder |
| 8 | store 06 | Report/Block rows below the fold: the shot did not show the safety tools it promises | B | Re-cropped to show the mute switch and the action sheet together |
| 9 | store en chat | Join message used a different nickname from the bubble | m | Same fake nickname |
| 10 | prototype | Mini plates used a 3 px radius that is not a token | m | `radius.s` |

## Round 4: owner decisions (name, age, sound, app only, timers) and final pass (48 shots)

| # | Screen / shot | Problem | Sev | Fix |
| --- | --- | --- | --- | --- |
| 1 | home-* | Brand word in the header after the name decision | B | Localized two-line name (`app_name_main` over `app_name_sub`), system font; store text moved to a replaceable layer (`store/store-text.json`) |
| 2 | home-de | New two-line header made German overflow by 8 dp (banner into the gesture area) | B | Fit rule: upper area is one scroll region; the form row hides first; dock and banner pinned |
| 3 | settings-ko | Divider missing after each volume slider row | M | Divider rule for slider → row |
| 4 | terms-ko | Age point named a number; the owner wants no age hints | M | Neutral "이용 연령" point; the age screen does the check |
| 5 | age-ko | (new) checked: neutral copy, repeats allowed on the keypad, 확인 disabled until 4 digits | — | Accepted |
| 6 | age-na-ko | (new) checked: calm, no number, no retry, no ads | — | Accepted |
| 7 | legal-ko | Contents rows 40 dp (below the 48 dp target) | B | 48 dp rows |
| 8 | settings / home | Web-deletion row and pending-deletion dialog no longer exist (app only) | B | Removed from prototype, shots and specs; Terms and Privacy open the in-app reader; Contact shows the address with a copy button |
| 9 | specs | Timer "none" option and pitch-clock "off" state obsolete | M | 30 / 60 / 120 s / relaxed 10 min everywhere; "off" state removed |
| 10 | sound sync | Sound set (design/sound) has cues for "opponent pitched", "secret locked", "memo" and "error" with no hook in my timeline; its timer demo ticks every second from 10 s | M | Hooks added (DESIGN_SYSTEM 8.3 E) and a hook → file table (section 10); timer plays the warning once at 10 s, then the last-5 ticks |

## Sign-off

I would sign off phase 1 at this state. What I checked last on every shot: alignment to the 16 dp margin and 4 dp grid, one primary action per screen in the bottom 40 %, no truncated text in ko / en / de / ru, every hint carries a letter, no card boxes, no colour outside the lamp rules, contrast table re-verified by script.

Known limitations, on purpose and documented:
- Memo grid cells are 30.4 × 28 dp (below 48 dp): accepted exception with TalkBack and large-font alternatives (DESIGN_SYSTEM 11.1).
- Not prototyped (specified only): vs Computer setup sheet, profile, create-room and join screens, tutorial, transfer-code sheet, blocked list, offline band. They reuse prototyped components.
- Prototype text faces are Roboto / Noto Sans KR stand-ins; Samsung and other OEM faces will differ slightly in width. Specs allow for this (no fixed-width text containers except plates).
- The split-screen store shot uses a neutral grey placeholder for the other app.

## 2026-11-25: Phase 2 review of the Android renders (290 Roborazzi PNGs)

Method: every reference shot paired with its render (reference | render | diff), plus all extra states, the `-small-*` (360 × 320) and `-ko-font200` variants; positions measured with PIL at density 2; every flat colour checked against `tokens.json` (alpha tokens composited). Full report: [`REVIEW_PHASE2.md`](REVIEW_PHASE2.md).

**Verdict: not signed off.** 36 defects: 1 blocker, 18 major, 17 minor.

| Top issues | Sev |
| --- | --- |
| D2-01 200 % font breaks the match screen: no 1.3× numeral cap and no large-text layout; guess `319620` renders `31962`, name ellipsizes to "…", `OUT`, header, mini plates and memo clipped | B |
| D2-04 / D2-26 / D2-02 / D2-03 Small window and 200 % collapse regions: lobby teams (~20 dp), team result without score or rows, line score, team rows cut to one letter | M |
| D2-13 / D2-15 / D2-16 / D2-34 Undocumented missing features: team best hints, profile tables, interactive tutorial, large-memo sheet | M |
| D2-09 / D2-10 Touch targets 32 dp: memo toolbar (clamped by the 32 dp row), chat chips and the sender name line | M |
| D2-05 / D2-08 Compact slots 56 instead of 48 dp; memo row labels 9 dp left (not centred) | M |

Deviation decisions: font-fallback offset accepted for `sans` glyphs only; tabular digits accepted (shots to be regenerated); team board `close` accepted (my duel finished); disabled send / chips accepted; transfer-code entry accepted but must sit under the field (D2-17); line-score scroll accepted at 1.0, rejected at 200 %; no active slot ring while reconnecting accepted.

Design follow-ups (mine): regenerate the shots with tabular digits; re-shoot `team-board-ko` and `match-reconnect-ko`; update 05 / 09 phrase lists to the protocol IDs, 12.15 masked colour (`text2` on `surface4`), ADAPTIVE §1 (compact secret row, compact team result) and 06 / 11 / 13 for remove ads and the online interstitial.

## 2026-11-25: Design follow-ups after the phase 2 review (59 shots)

Done before the re-review, as promised in [`REVIEW_PHASE2.md`](REVIEW_PHASE2.md). Also covers the owner decisions of 2026-11-25 (remove ads, interstitial after online matches) and protocol 1.1.

| # | Area | Change | Defects |
| --- | --- | --- | --- |
| 1 | Shot pipeline | Written down as `prototype/shoot.js` (Playwright, density 2). `proto.js` sets tabular digits inline on every Barlow element (the CSS `font` shorthand was resetting `tnum`, so the old log and line-score digits were proportional). All 47 screen shots regenerated | deviation "tabular digits" |
| 2 | Re-shoots | `team-board-ko` with `close` (my duel finished); `match-reconnect-ko` without the active slot ring; result shots no longer overflow (upper area is a scroll region), band at y = 725 dp | deviations, result note |
| 3 | Phrases | 12 protocol ids with ko / en copy in 09 §4; quick-match sheet = 6 fixed ids (아까워요 replaces 어렵네요); room chips = all 12 | D2-24 |
| 4 | Masked text | `text2` on `surface4` (5.59) for others, `textDisabled` on chalk (5.48) for mine; 2.2 and 12.15 no longer contradict | D2-12 |
| 5 | Small window | ADAPTIVE §1: secret row ("정하기", random fill in the menu), reconnect banner one line, duel result, team result with inline score, live board, lobby as one scroll region, no interstitial below 480 dp | D2-04, D2-26, D2-27, D2-35, D2-36 |
| 6 | 200 % font | ADAPTIVE §2 rewritten (2.1–2.6): one numeral rule (every `num` style × min(fontScale, 1.3) in dp, DESIGN_SYSTEM 3.3), a full large-text match table, the large memo sheet in full, results and team board | D2-01, D2-02, D2-03, D2-25, D2-34 |
| 7 | Remove ads | Settings 11 §1.1 (구매 section: loading, not purchased with Play's price, purchased, pending, error, restore, toasts); quiet "광고 제거" text button in the result top bar (06 §1), never a pop-up; DESIGN_SYSTEM 13. Shots `settings-ads-*` (7) and `result-link-ko/en` | D2-31 |
| 8 | Interstitial | Now after vs-computer **and** online matches, on the way to the results screen (06 §3): 1 s hold with no touch down, skip if chat or IME is open or the player left, never in the review room, practice or small windows, one per team match, shared cap; the old "다음 화면 전에 짧은 광고가 나와요" note removed | D2-31 |
| 9 | Protocol 1.1 | Chat system lines (09 §2.1, shot `chat-system-ko`); best hints on every team-board row incl. the null state (08 §2, 12.11); any player's record on the player card with loading / not available / failed states (12 §2, shot `report-card-norecord-ko`) | D2-13, D2-32 |

Self-check on the new shots: no non-token colour, no banner on Settings, the purchase rows use no lamp colour (only `strike` for the error line, as the silent-mode warning does), the result link sits 0 dp from the `flag` button with 48 dp targets that do not overlap, and the result band keeps its 24 dp clear space.

## 2026-11-25: Phase 2 re-review after fix round 1 (450 Roborazzi PNGs)

Build `7d73fc4`. Same method as round 1: reference | render | diff for the 59 regenerated shots (tabular digits), every new state, `-small-*` and `-ko-font200` variant opened, positions measured with PIL at density 2, flat-colour audit over all 450 renders. Full report: [`REVIEW_PHASE2_ROUND2.md`](REVIEW_PHASE2_ROUND2.md).

**Verdict: not signed off.** Round 1: 27 fixed, 1 closed as an accepted deviation (D2-14), 8 open (D2-03, D2-15, D2-30, D2-32, D2-34 major; D2-09, D2-20, D2-22 minor). New: 16 (D2-37 … D2-43 major, D2-44 … D2-52 minor). Open in total: 0 blocker, 12 major, 12 minor.

| Top issues | Sev |
| --- | --- |
| D2-37 / D2-38 / D2-39 / D2-03 200 % still loses content: reconnect banner leaves 0 log rows; waiting label ellipsized; practice band clips "방으로" (and its target is 40 dp); team status "0:41" clipped | M |
| D2-41 / D2-40 Small window: tutorial step 3 hides the row the coach explains; transfer mode scrolls its own field and error out of view | M |
| D2-34 Large memo sheet: no pitch clock in its header, grid not centred, no row lines, position row not pinned; the inline memo at 200 % still shows the digit row | M |
| D2-30 / D2-42 Not reviewable: no de / ru renders; language picker fixture has 2 of 10 languages; 구매 section off-screen in small / 200 % renders; active transfer code never rendered | M |
| D2-43 Opponent-log sheet and server-error dialog still missing (deviation rejected) | M |

Fixed and verified: the 1.3× numeral cap and the large-text match layout (all six digits kept), results / lobby / team result at 200 % and small windows, compact slots, memo labels, chat targets and name line, masked colour, team best hints, the interactive tutorial, the transfer-entry position, home hero, lobby band, slider, scrim, phrases, remove-ads rows and states, the result link. No non-token colour.

Deviation decisions: player card from the long-press sheet accepted; reconnect countdown from the first sighting accepted as interim; empty profile tables rejected (hide the sections until the server sends the data); missing opponent sheet and server-error dialog rejected; tutorial team card, review-room bot id, interstitial hold timing, the delete-dialog purchase paragraph, the privacy-row behaviour, the live-board leave copy, the small-window team status and the deferred cross-fade accepted.

## 2026-11-26: Final sign-off review after fix round 2 (480 renders + 70 QA locale renders)

Build `e8e9102`. The renders were recorded in `25fdd6e`, and the app source has not changed since. Same method as round 2: reference | render pairs (now including de / ru), every `-small-*`, `-ko-font200`, de / ru / vi and QA locale render opened, PIL measurements at density 2, a flat-colour audit over all 550 PNGs, and AA contrast for the new pairs. Full report: [`REVIEW_FINAL.md`](REVIEW_FINAL.md).

**Verdict: SIGN-OFF WITH LISTED MINORS.** Of the 24 items open after round 2: 21 fixed, 1 resolved by a spec update (D2-51), 2 partly fixed and carried as minors (D2-22, D2-48). New: 3 minors. Open in total: 0 blocker, 0 major, 5 minor.

| Remaining minors | |
| --- | --- |
| M-1 | `report-card-norecord` header block 4.5 dp taller than the shot (D2-22 residual) |
| M-2 | 360 × 320 lobby with the reconnect banner shows only a ~20 dp sliver of a seat (D2-48 residual) |
| M-3 | `WordFit` auto-shrink has no absolute floor (13 sp may reach 6.5 dp at 100 %); renders are fine today (smallest ≈ 80–90 %) |
| M-4 | de / ru copy drifts from the design strings ("Geg." / "Сопер." abbreviations that are not needed, "Raten" / "Назвать" lose the pitch metaphor, "Start" for Home) |
| M-5 | 7 sweep findings at 360 × 320 + 200 % (de 6, en 1); the `search-60s` clipped height should scroll |

Spec changes made in this review:
- **11 §2 transfer code:** 3 × 4 plates, width min(28, fit) with a 22 dp floor, 2 / 8 dp gaps, 48 dp high at large text, and the expired state.
- **DESIGN_SYSTEM 11.3 shrink-to-fit rule:** floor = max(component minimum, 11 dp rendered, 100 % token size at font scale ≥ 1.5), then wrap / ellipsis.
- **01 §0:** update note for the approved birth year + month entry.

Deviation decisions: year + month age entry accepted; server-error dialog actions accepted; opponent sheet from the overflow menu accepted; result link hidden at large text accepted; auto-shrink accepted with the floor.

Conditions before release (carried, not design defects): the best-hint 150 ms cross-fade (round-2 condition, still "not yet"), and the owner must fill in the `[개발자 표시명]` / `[시행일]` placeholders in the legal docs (the release build already refuses them).

## 2026-11-26: Design system v2 "Clean White" (owner decision: redesign direction A)

The owner chose Direction A of [`redesign/PROPOSAL.md`](redesign/PROPOSAL.md). Turned it into the full system so the engineer can implement every screen. Engineer's guide with the token migration table, per-screen change list and checklist: [`REDESIGN_V2.md`](REDESIGN_V2.md).

| # | Area | Change |
| --- | --- | --- |
| 1 | Tokens | `tokens.json` 2.0.0, light. Names kept where the role is the same (`bg`, `surface1–4`, `line`, `outline`, `text2/3`, `textDisabled`, `strike`, `ball`, `strikeTint`, `team*`, `scrim`, `focus`, all motion and haptic tokens). Split / renamed: `chalk` → `ink` + `accent`, `onChalk` → `onAccent` + `onInk`, `chalkPressed` → `accentPressed`, `out` → `out` (OUT chip) + `danger`, `outTint` → `dangerTint`, `lampOff(Ring)` → `dotOff`, `duel_strip_h` → `opp_row_h`. Removed: glows, `shadow`, lamp sizes, log header. New: `accentTint`, `inverse*`, `numSummary`, chip / avatar / pill / tile sizes, `banner_clear_space`. `prototype/gen-tokens.js` now generates `tokens.css` |
| 2 | Faint-zero fix | Zero counts in `text3` (5.78 on bg, 5.25 on the latest row) instead of the proposal's 1.87 : 1 grey. All text passes AA; `textDisabled` only for disabled controls |
| 3 | Fonts | Inter 4.000 (OFL 1.1), Latin subset, TrueType, 500 / 600 / 700 in `fonts/` with `OFL-Inter.txt`; Barlow Condensed retired (files removed from `design/fonts/`). Words stay in the system face |
| 4 | DESIGN_SYSTEM.md | Rewritten as v2: direction, colour + contrast, type, spacing, radius 6 / 12 / 16, flat elevation, icons, motion (chip count-up on the unchanged hooks), haptics and sound kept, accessibility (AA, 48 dp, 1.3× numeral cap, shrink floor), components (opponent row, entry head, memo sheet, hint chips, ruled-out keys, tiles, summary, banner line), ads, dark theme note |
| 5 | Screens | 01–15, FLOW, ADAPTIVE updated: memo behind a sheet, inning table behind "회별 기록 보기", Home with 4 numbers / 3 tiles / quick-match block, result with headline + opponent number + 3 summary numbers, 광고 제거 as a quiet line above the banner (24 dp clear space measured from its target). All functional states and policy rules kept |
| 6 | Prototype + shots | `prototype/` rebuilt on v2 (CSS, runtime, all pages); age screen now year + month. **All 58 existing shots regenerated** with the same names, plus 2 new (`match-memo-ko`, `result-innings-ko`): 60 shots |

Self-check: no non-token colour in the prototype except the ad creative and IME placeholders; one accent-filled control per screen; no shadows; the 광고 제거 target ends 24 dp above the band on every result shot with a banner; Home, result and match-late fit 360 × 800 without scrolling; compact match at 360 × 320 keeps two log rows.

Open for the owner: launcher icon stays dark for now (REDESIGN_V2 §6).

## 2026-11-26: Review of the v2 "Clean White" build (490 renders + 70 QA locale renders)

Build `60316af` (renders and app source changed together). Method as in `REVIEW_FINAL`: 59 shot | render pairs (ranked by pixel difference, all opened), every `-small-ko`, `-ko-font200`, de / ru / vi and QA locale render opened as contact sheets, PIL measurements at density 2, flat-colour audit of all 560 PNGs against `tokens.json` 2.0.0 (scrim and disabled opacities composited), `Tokens.kt` checked against `tokens.json` (31 / 31 colours, `tnum` on every `num` style). Full report: [`REVIEW_V2.md`](REVIEW_V2.md).

**Verdict: NOT SIGNED OFF YET.** Sign-off follows as soon as the two majors are fixed. Both are small; they were also present in the v1 renders and I missed them in `REVIEW_FINAL`. The redesign matches the shots closely: chips, slots, keys, the pill, the progress line and the log rows measure ±0.5 dp on `match-guess-ko`. It meets the owner's brief: white, one accent, uncluttered.

| | |
| --- | --- |
| Majors | V2-01 chat header "전체 채팅 끄기" loses "끄기" at 200 % (`maxLines = 2` in 96 dp); V2-02 match toast covers the slots |
| New minors (13) | small-window found screen clipped; nickname privacy link style and missing scroll fade; team-board 4 dp gap below my row; team result 3rd row cut (spec fix: hide the rules caption); Home record / tile offsets (3 / 2 dp); lobby rules row 46 vs 48 dp; dialog scrim black instead of ink; innings table at 200 % hides round 1 without a fade; tutorial legend without chips, far from the row; ja tile breaks before "ー"; unknown record "–" in ink + fixtures; own-phrase bubble missing |
| Carried (5) | M-1 … M-5 still open (V2-16 … V2-20) |
| Total open | 0 blocker, 2 major, 18 minor |

Deviation decisions:
- **Accepted (8):** in-window sheets without drag (1.0); cached record lag; units in the system face; compact digit tiles without "자리"; v1 tablet layouts (interim); opponent log from the hint / menu; unused memo-collapse API; test frame insets.
- **Rejected (1):** no own-phrase bubble (V2-15).

Spec changes:
- **DESIGN_SYSTEM 12.17:** sheet header and bottom; dialog scrim; match toast placement; drag note.
- **DESIGN_SYSTEM 12.12, 12.20, 12.3, 12.8:** small clarifications.
- **01 §1:** transfer link and scroll fade.
- **02:** default digit count 3.
- **05 §2.8:** phrase sheet header 56 dp.
- **06 §1 / §4 and 08:** record "–" and team-result fit.
- **ADAPTIVE §1 / §3:** compact digit tiles, toast and found state; interim tablets.

Top polish suggestions:
1. Ellipsized names that hug the tag.
2. Hide the result badge at large text so the summary shows.
3. Compact mode tiles on Home at large text.
4. One 20 dp fade rule for every scroll edge above a pinned bar.
5. Drag-to-dismiss on sheets.
