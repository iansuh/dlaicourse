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
