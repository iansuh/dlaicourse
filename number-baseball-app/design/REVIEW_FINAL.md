# Final design sign-off: Android renders after fix round 2

Reviewer: design lead. Date: 2026-11-26. Build: `HEAD` (`e8e9102`). The renders were recorded in `25fdd6e`, and no file under `android/app/src` has changed since then, so the renders match the code. 480 Roborazzi renders are in `android/app/screenshots/` and 70 QA locale renders (de / fr / ja / ru / vi) are in `android/app/qa-screenshots/`.

Sources of truth: `DESIGN_SYSTEM.md`, `tokens.json`, `screens/*.md`, and the 59 reference shots in `shots/`. Earlier reports: [`REVIEW_PHASE2.md`](REVIEW_PHASE2.md) and [`REVIEW_PHASE2_ROUND2.md`](REVIEW_PHASE2_ROUND2.md). The engineer's round-2 notes are in `android/README.md`, § "Deviations from the design".

Method, the same as in round 2:
- I paired each reference shot with its render, including the new `home/lobby/match-guess/match-small/result-lose` × `de/ru` pairs.
- I opened each new state and each `-small-*`, `-ko-font200`, de / ru / vi and QA locale render as an image.
- I measured positions with PIL at density 2 (2 px = 1 dp).
- I ran a flat-colour audit over all 550 PNGs. It checked uniform 6 × 6 px blocks against `tokens.json`, with the alpha tokens, disabled opacities and the 64 % scrim composited.
- I computed AA contrast for the colour pairs that are new in this round.
- I read the Compose source only to confirm a mechanism, such as `WordFit` in `design/Text.kt` and `PracticeBand`.

App code was not changed.

## Verdict

**SIGN-OFF WITH LISTED MINORS.** The build can ship. The five minors below can land in a later update.

- Every blocker and major from rounds 1 and 2 is closed.
- At 200 % no state loses content. The reconnect banner leaves the latest row visible, the waiting label and the practice band wrap, and team status shows "재접속 / 0:41".
- The opponent-log sheet and the server-error dialog are built and match the system.
- de / ru / vi render cleanly, with no clipping and no word broken inside the word.
- Every flat colour is a token, and every new text pair meets AA.

Counts for the 24 items open after round 2:
- **21 fixed**
- **1 resolved by a spec update** (D2-51, 11 §2 rewritten to match the build)
- **2 partly fixed, carried as minors** (D2-22, D2-48)

New: 3 minors (M-3, M-4, M-5). Open in total: **0 blocker, 0 major, 5 minor.**

Before release (not design defects, but conditions):
1. **Best-hint 150 ms cross-fade (08 §2).** Round 2 accepted it as a follow-up that "must land before release", and the README still says "Not yet". It is a small motion change, so I keep the condition.
2. **Legal text placeholders.** `legal-ko` shows "[개발자 표시명]" and "시행일 [시행일]". These come from `docs/play/TERMS_OF_USE.md` / `PRIVACY_POLICY.md`, not from the design. `checkReleaseConfig` now refuses a release build while they remain, so the owner must fill them in.

## 1. Round-2 items: status

| ID | Sev (r2) | Status | Evidence |
| --- | --- | --- | --- |
| D2-03 | major | **Fixed** | `team-board-ko-font200` row 3: "재접속" over "0:41", both fully inside the row; badges and best hints intact. Long names ellipsize ("Größenwa…"), which is correct for player content (3.2). |
| D2-09 | minor | **Fixed** | "지우기" ink x 274.5–309 vs 272.5–306.5 dp: 2 dp right, within tolerance (round 2: 6 dp left). Chevron x 328–340 in both. |
| D2-15 | major | **Fixed (interim)** | `profile-*`: the 자리수별 and 모드별 sections are hidden, headers included, until the server sends the data, as decided in round 2. |
| D2-20 | minor | **Fixed** | `result-win-ko`, `result-lose-de`: row separators at 491 / 530 dp in both (were −2 / −3). Header "3:12" at y 433.5 in both (x 2 dp left). Digits are within 0.5 dp. Labels: "나" 507–519.5 vs 506–518, "Kestrel" 545–555.5 vs 546–556.5. The engineer's 4 dp offset approximates baseline alignment to within 1.5 dp, which I accept. |
| D2-22 | minor | **Partly fixed → M-1** | `report-card-ko`: sheet top 496.5 vs 497, dividers 649 / 706 in both (fixed). `chat-actions-ko`: context block 79.5 dp in both (fixed). The extra row is the accepted "플레이어 정보 보기". `report-card-norecord-ko`: the sheet top is still at 510.5 vs 515, so the header block is 4.5 dp taller. |
| D2-30 | major | **Fixed** | `-de / -ru / -vi` renders for home, lobby, match-guess, match-small, result-lose and settings, plus 70 QA locale renders. Positions match the de / ru shots (log, memo, keypad, line score, lobby seats within 1 dp). No clipping. Copy drift is logged as M-4. |
| D2-32 | major | **Fixed** | Closed together with D2-42 and D2-43. |
| D2-34 | major | **Fixed** | `match-memo-sheet-ko(-font200)`: full height (top at the status bar), pitch clock in the header, grid centred (x 60–300 at n = 4), 1 dp `line` between rows, 48 dp rows, IN plates 32 × 32 dp (x 212–244, y 190–222). At 200 % the inline memo is the 48 dp toolbar with `open_in_full` only. The pinned position row was confirmed in code (it cannot be seen in a still). |
| D2-37 | major | **Fixed** | `match-reconnect-ko-font200`: banner on 2 lines (title + countdown) and the latest log row plus 2 more visible. The duel strip yields, as allowed. |
| D2-38 | major | **Fixed** | `match-waiting-ko-font200`: "상대의 투구를 / 기다리는 중" on 2 lines in the 64 dp key, lamps above. |
| D2-39 | major | **Fixed** | `match-practice-ko-font200`: the band grows to 2 lines and "방으로" is whole. Normal windows keep the 40 dp band with a 48 dp target overflowing it (`targetOverflow`). |
| D2-40 | major | **Fixed** | `nickname-transfer-small-en`, `nickname-transfer-error-small-ko`: field visible with the 2 dp chalk outline and cursor; the error line is visible under it. |
| D2-41 | major | **Fixed (residual accepted)** | `tutorial-3-small-ko`: coach band 48 dp (49–97), and the coached row `075 1S 1B` is fully visible (99–146). `tutorial-done-small-en` shows `047 3S 0B`. The log is 47 dp, not the ≥ 88 of ADAPTIVE §1. I accept that for the tutorial in a compact-height window only, because the coach text explains that single row. |
| D2-42 | major | **Fixed** | `settings-language-*`: all 11 rows (system + 10), scrolling in small windows. `settings-ads-*-ko-font200` / `-small-*` show the 구매 section with the price under the subtitle. `settings-transfer-*` shows an active code with "9:12 남음". The expired state has its own render. |
| D2-43 | major | **Fixed** | `match-opponent-log-ko(-font200)`: sheet with `#`, the opponent's guesses and hints, and numerals capped at 1.3×. `server-error-dialog-ko(-font200)`: 12.17 layout, 64 % scrim, actions 홈으로 · 다시 시도. The action mapping per screen in the README is accepted (§3). |
| D2-44 | minor | **Fixed** | `create-room-ko-font200`: "느긋하게" stays on one line, shrunk to about 80 % (ink 17.5 dp high vs 21 dp for "120초"). `team-board-nobest-small-en`: "Away / 0:41" fits the 56 dp row. |
| D2-45 | minor | **Fixed** | Opponent column "1S 3B" ink x 312.5–342.5 vs 313.5–343.5, cap 11 dp (`numTag` 15 sp). |
| D2-46 | minor | **Fixed** | (a) `match-small-ko/de`: guess "3196" y 67.5–83 vs 67–83.5; "5780" 111.5–127 vs 111–127.5. The engineer did not pixel-measure it; I did, and it is within 0.5 dp. (b) `tutorial-*`: top bar ends in a 1 dp hairline (78–79) and the coach band in a 1 dp hairline (175–176). |
| D2-47 | minor | **Fixed** | `window-too-small-*` at 300 × 300 dp, with brand mark, title and caption. |
| D2-48 | minor | **Partly fixed → M-2** | `lobby-reconnect-ko-font200`: team header and one full seat are visible; fixed. `lobby-reconnect-small-ko`: the practice button moved into the chat bar ("연습하기"), but the region still shows only a ~20 dp sliver of the first seat. |
| D2-49 | minor | **Fixed** | The table is hidden (D2-15). `profile-new-*` shows "–" for 승률. |
| D2-50 | minor | **Fixed** | `legal-ko`: contents hairlines at 122 / 405 dp in both. Bullets are discs on the first line. |
| D2-51 | minor | **Resolved by spec** | `settings-transfer-*`: 3 groups of 4 plates measured at 24.5 × 40 dp, 2 dp inside a group, 8 dp between groups, 328 dp total at 360 dp. Digits are 25 dp cap and 14.5–16.5 dp wide, leaving ≥ 4 dp side margin. At 200 % the plates are 24.5 × 48 with a 30.5 dp cap and ≥ 3.5 dp margin. Expired: `surface1` plates with `textDisabled` digits, "코드가 만료됐어요" (`caption` `text3`, 5.05 : 1 on `surface3`) and primary "새 코드 만들기". I rewrote **11 §2** to this geometry: width = min(28, (content − 16 − 18) ÷ 12), floor 22 dp, groups stack below that. The build now matches the spec. |
| D2-52 | minor | **Fixed** | The memo sheet takes the full window height (top at y 24 dp). |

## 2. Auto-shrink of labels (`WordFit`): decision

**Accepted, with a floor.** This is better than clipping or ellipsis, and it never applies to names (`autoFit = false` for player content).

What I measured in the renders:
- At 200 %: the smallest shrink I found is the segment label "느긋하게" at about 80 % of its row-mates. Any 50 % step at 2.0 still renders at the token's own 100 % size (13 sp → 13 dp), well above 11.
- At 100 % with long translations: `lobby-ru` copy / share labels at about 90 % (ink 13.5 vs 15 dp, about 14.4 sp). The compact pitch key "Назвать" is at full `labelSmall` (cap 9.5 dp, the same as en / ko).
- No label in any render is below 11 dp.

Why a floor is still needed: the code has none. `fitMinScale` is relative to the token, so at font scale 1.0 a 13 sp column header or caption with `fitMinScale = 0.5f` may legally render at **6.5 dp**. Also, under Android 14's non-linear font scaling, 200 % of a 20 sp title is about 34 sp, so 50 % would be 17 dp, smaller than what a default-size user sees.

I added the rule to **DESIGN_SYSTEM 11.3**. The floor is the larger of:
- the component's minimum scale
- **11 dp rendered**
- at font scale ≥ 1.5, the token's 100 % size

Below the floor the normal fallbacks apply: wrap by word / 어절, then ellipsis with TalkBack text. This is logged as M-3. It is a one-line clamp in `WordFit.getFontSize`.

## 3. README deviations (round-2 additions): decisions

| Deviation | Decision |
| --- | --- |
| 01 §0 age: birth **year and month** (owner decision, DEF-051) | **Accepted.** The renders (`age-*`, 6 plates, error state, 200 %) are clean and neutral. I added an update note to 01 §0. The `age-*` shots are regenerated later; until then the renders are the reference. |
| 12.17 server-error dialog actions per screen (terms: 다시 시도 = agree again; lobby: 홈으로 = leave room; settings: back) | **Accepted.** These are the obvious actions. Other errors stay toasts, as 14 allows. |
| 05 §2.9 opponent sheet also in the overflow menu ("상대 기록 보기") | **Accepted.** It is one more route to the same sheet. |
| Team final at large text hides the "광고 제거" link | **Accepted.** The same rule applies in small windows, and Settings keeps the purchase. |
| Lobby practice button scrolls with content at large text / region < 420 dp | **Accepted**, with the residual in M-2. |
| Team status "재접속 대기 / 0:41" with a short word in small / large (`status_reconnecting_short`) | **Accepted.** This is the string I proposed for 08 §5. |
| Auto-shrink of labels (`WordFit`) | **Accepted with the floor** (§2, M-3). |
| Small-window opponent status at large text: lamp + countdown, word in TalkBack | **Accepted.** |

## 4. Remaining minors (release is OK; fix in the next update)

| ID | Where | What | Fix |
| --- | --- | --- | --- |
| M-1 (D2-22) | `report-card-norecord-*` (`report/Report.kt`) | Sheet top at 510.5 vs 515 dp: the "기록을 볼 수 없어요" header block is 4.5 dp taller than the shot. | The not-available line box is 48 dp in total, padding included. Drop the extra spacer under it. |
| M-2 (D2-48) | `lobby-reconnect-small-*` (`lobby/RoomLobby.kt`) | 360 × 320 with the reconnect banner: only about 20 dp of the first seat shows before scrolling. | When the banner shows in a compact-height window, collapse the code row (the plates go in the overflow menu, "초대 코드 보기"), or let the settings summary row scroll with the seats. Target: team header + one full 56 dp seat. |
| M-3 | `design/Text.kt` `WordFit` | No absolute floor (see §2). | Clamp each step: `size ≥ max(max × minScale, 11.dp.toSp(), if (fontScale ≥ 1.5) token at 1.0 else 0)`. When the floor is hit, return the floor and let wrap / ellipsis handle the rest. Also consider 4 dp less inner padding on the lobby copy / share buttons, so that ru fits at 100 % as in the shot. |
| M-4 | `values-de`, `values-ru` strings | Copy differs from the design strings (`prototype/js/strings.js`): `col_opp` "Geg." / "Сопер." are abbreviated although "Gegner" / "Соперник" fit (shots `match-guess-de/ru`); `pitch` "Raten" / "Назвать" and the status "Hat geraten" / "Ответил" lose the pitching metaphor ("Werfen" / "Geworfen", "Бросок" / "Бросил"); `memo_clear` "Leeren" vs "Löschen"; `home` "Start" vs "Startseite". | Use the design strings, or send me the reason for each change (a native review). Each one fits at full size. |
| M-5 | 360 × 320 window at 200 % (`src/test/resources/qa/layout-baseline.tsv`) | 7 sweep findings: ellipsis of `reconnecting_title` (de, lobby and match), `duel_round` (de, practice) and `home_help` (de, tutorial 4 / done); clipped height on `search-60s` (de, en). | An edge case combining a split-screen window with the largest font. Ellipsis with TalkBack text is acceptable there. The `search-60s` clipped height is not: let that block scroll. |

Trivial, no action needed: in `tutorial-*-small` the seam under the coach band is 2 dp (hairline + row line); `match-small-*` top bar is 1 dp lower than the shot.

## 5. Regression checks

- **Colour:** the audit over 480 renders and 70 QA renders found no flat non-token colour. The only exceptions are the test ad creative (`#D9DCD4`, `#3A3F3B`).
- **Contrast** (new pairs): `text3` on `surface3` 5.05; `text2` on `surface3` 6.42; `text2` on `surface4` 5.59; `strike` on `surface3` 8.01; `out` on `surface3` 4.87 (the transfer error and dialog texts). All are ≥ 4.5.
- **Targets:**
  - memo toolbar 48 dp (upward extension);
  - practice "방으로" 48 dp over the 40 dp band;
  - compact keypad, the `⌫` and pitch keys ≥ 48 dp;
  - opponent sheet close and server-error dialog actions 48 dp.

  The only sub-48 control is the memo grid cell (30.4 × 28 dp), the accepted exception with the large memo sheet alternative.
- **Ads:** no banner on match, tutorial, lobby, chat, Settings or any 360 × 320 window. The result link is hidden in small windows, at large text and after purchase.
- **de / ru / vi / fr / ja:**
  - none of the renders shows clipping or a word broken inside the word;
  - Vietnamese diacritics are clear of the line boxes;
  - Japanese breaks are acceptable;
  - the home row subtitles wrap to 2 lines in ru / vi ("до 5 / на 5", "tối đa / 5 đấu 5"), which is acceptable for a subtitle.

## 6. Design follow-ups (mine)

- Regenerate the `age-*` shots for year + month and rewrite the 01 §0 table. The note is in place.
- Add `status_reconnecting_short` to 08 §5.
- Carry over from round 2: 09 §2 / 12.15 player card, 06 §3 hold wording, 11 §1.1 privacy row, 11 §2 delete-dialog paragraph, 08 §1 leave copy, 01 §2 team card "later", DESIGN_SYSTEM 11.1 (b) "Larger memo" setting, and the prototype contact row at 64 dp.
- Add a de / ru string table to 05 (pitch metaphor), so that M-4 has a single source of truth.
