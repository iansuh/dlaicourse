# Design review: redesign v2 "Clean White" (Android renders)

Reviewer: design lead. Date: 2026-11-26. Build: `HEAD` `60316af` ("redesign v2 Clean White across all screens"). The renders and `android/app/src` were last changed in the same commit, so the renders match the code.

What I reviewed:
- 490 Roborazzi renders in `android/app/screenshots/`
- 70 QA locale renders in `android/app/qa-screenshots/` (de / fr / ja / ru / vi)
- the v2 section of `android/README.md` (the engineer's deviations)

Sources of truth: [`REDESIGN_V2.md`](REDESIGN_V2.md), `DESIGN_SYSTEM.md` v2, `tokens.json` 2.0.0, `screens/*.md`, and the 60 light reference shots in `shots/`.

## Method

The same method as [`REVIEW_FINAL.md`](REVIEW_FINAL.md):
1. **Pairs.** I paired all 60 reference shots with their renders side by side (59 pairs; `icon-preview` has no render) and ranked them by pixel difference. I opened every pair, starting with the largest differences.
2. **Other renders.** I opened every `-small-ko` (94), every `-ko-font200` (94), every de / ru / vi render and every QA locale render as contact sheets. Suspect areas I opened at full size.
3. **Measurements.** I measured with PIL at density 2 (2 px = 1 dp): ink row and column profiles, divider runs, sheet tops and handles, and the bounding boxes of fills of each token colour (chips, slots, keys, pills, progress).
4. **Flat-colour audit.** I checked uniform 6 × 6 px blocks in all 560 PNGs against `tokens.json` 2.0.0, with the 40 % ink scrim and the disabled opacities composited.
5. **Code.** I compared `Tokens.kt` with `tokens.json`: all 31 colours match exactly, and every `num` style sets `tnum`. I read the Compose source only to confirm a mechanism (`SheetFrame`, `NameWithTag`, `NbButton`, the chat header, `setDimAmount`).

**Accepted font-fallback offset.** Hangul and Latin text set in the system face sits up to 3 dp lower than in the prototype's face. Examples: the headline 167 vs 164 dp, the summary labels 441 vs 438, the Home name stack +1.5 dp. That offset is accepted when the geometry around the text matches: lines, plates, chips, keys and Inter digits. Anything beyond it is a defect.

App code was not changed.

## Verdict

**NOT SIGNED OFF YET.** Sign-off follows as soon as the two majors (V2-01 and V2-02) are fixed. Both are small and local, and I will check them from the renders alone. The 18 minors can follow in a later update.

The redesign itself is a success and does what the owner asked:
- **It looks like the reference.** The match, Home, results, lobby, settings, report and chat screens are close to pixel-identical with the shots. On `match-guess-ko`, the S chip, progress line, active slot, keys, timer pill and latest row are in exactly the same place and size (±0.5 dp).
- **Colours.** Every flat colour is a token, except the dialog scrim (V2-10). There are no shadows. `Tokens.kt` mirrors `tokens.json` exactly, and the Inter digits are tabular.
- **The screens read as calm.** They are white and use one accent, with no clutter.

Both majors are also in the v1 renders, and I missed them in `REVIEW_FINAL`. I am correcting that here.

**Counts**
- New defects: **2 major, 13 minor** (V2-01 … V2-15).
- Carried from `REVIEW_FINAL`: all five minors are still open, M-1 … M-5 as V2-16 … V2-20. That gives **5 carried minors**.
- **Total open: 0 blocker, 2 major, 18 minor.**

Deviations: 9 decided. **8 accepted** (some with a spec note) and **1 rejected** (own-phrase bubble, now V2-15).

Spec clarifications made in this review are listed in §4.

Conditions before release, carried and still open:
- The best-hint 150 ms cross-fade (08 §2). The README still says "Not yet".
- The `[개발자 표시명]` / `[시행일]` placeholders in the legal texts.

## 1. Defects

Sev: **major** = clipped or changed meaning, or covers the active control. **minor** = more than 1 dp off, a missing state or style, or a small-window or locale-only issue.

Paths below are relative to `android/app/src/main/kotlin/app/numbaseball/ui/`.

| ID | Render(s) | Measured | Expected | Sev | Fix (Compose) |
| --- | --- | --- | --- | --- | --- |
| **V2-01** | `chat-*-ko-font200` (chat, empty, limit, muted, system, actions) | The header label wraps in a 96 dp cap with `maxLines = 2`: "전체 / 채팅", and **"끄기" is cut off**. The switch then reads "전체 채팅 [off]", which suggests the opposite. The first system line also scrolls under the grown header. | The label is never clipped (ADAPTIVE §2.1; 12.16 header). | major | `chat/Chat.kt` `ChatSheet`: at `isLargeFont()`, move "전체 채팅 끄기" + switch to its own full-width 48 dp row under the 48 dp title row. Otherwise drop the 96 dp cap and let `WordFit` shrink it (never `maxLines` without a fit). |
| **V2-02** | `match-toast-ko/en`, `-ko-font200` | The toast (`inverse`, 540–588 dp) **covers the bottom 40 dp of the slots** (516–580), and its bottom is 4 dp above the keypad (592). | Never over the slots (12.17); now: bottom edge 8 dp above the entry head (12.17, clarified). | major | `match/MatchScreen.kt` / `design/Surfaces.kt` `ToastHost`: on the match screen, anchor the toast host to the bottom of the log region (`Modifier.align(BottomCenter)` inside the log box, `padding(bottom = 8.dp)`), not to the keypad. |
| V2-03 | `search-found-small-ko/en` | At 360 × 320 the 48 dp avatar touches the digit tiles (0 dp gap), and "상대를 찾았어요" is **clipped through the glyphs** at the window bottom. | Nothing clipped; compact rule added (ADAPTIVE §1: no avatar / VS, name + headline 16 dp under the tiles). | minor | `quickmatch/QuickMatch.kt` found state: `if (isCompactHeight())`, hide the avatar and the VS line; keep name + headline. |
| V2-04 | `nickname-*` (all langs, all windows) | The privacy link is `text2` and **underlined**. | `caption` with the link `accent` 700, no underline (01 §1 Actions; v2 links). | minor | `firstrun/FirstRun.kt` l. 266: `SpanStyle(color = NbColors.accent, fontWeight = FontWeight.Bold)` without `TextDecoration.Underline`. |
| V2-05 | `nickname-ko`, `nickname-error-ko` | The transfer button adds 48 dp, so the preview caption "번호는 자동으로 붙어요" scrolls under the pinned actions with a **hard cut** and no fade. The transfer button itself is not in the spec or the shots. | The transfer link is now in 01 §1. The body ends in a 20 dp fade when it continues (01 §1 Body scroll, new). | minor | `firstrun/FirstRun.kt`: add the standard 20 dp bottom fade to the scrolling body (the same `fadingEdge` as the pitch log). The transfer button is accepted as built (`text2`, 48 dp target). |
| V2-06 | `team-board-ko`, `team-final-ko`, `report-*` backdrops | Row 2 runs 258–329 dp, with lines at 329 / 401. | 262–333, lines at 333 / 405: **4 dp gap below my row** (shots; 12.12 updated). | minor | `team/TeamBoard.kt` `DuelRow` for `mine`: `padding(bottom = 4.dp)` outside the `surface2` shape. |
| V2-07 | `team-final-ko` (360 × 800 with gesture inset) | The third duel row is **cut in half** behind the fade above the pinned actions (row 3 at 470–520, actions from 533). The last result row of the celebration screen looks unfinished. | All three rows of a 3 v 3 visible (06 §4 Fit, new: the rules caption is hidden on the result, which gains 22 dp). | minor | `team/TeamBoard.kt` final state: don't emit the "3 대 3 · 2승이면 승리" caption when `final`. |
| V2-08 | `home-ko/de/ru` | The record divider is at 234 dp; the "다른 방법으로" label to tiles gap is 12 dp (tiles at 439). The hero then sits 5 dp high (263 vs 268). | Divider 237, tiles 441 (shot); hero follows. | minor | `home/Home.kt` record block: bottom `VSpacer(20.dp)` → 23 dp (the prototype's caption line box), label `VSpacer(10.dp)` → 12 dp. |
| V2-09 | `lobby-ko/en/de/ru` | The rules row is 46 dp between its lines (263–309); seats start 2 dp high (420 vs 422). | 48 dp **between** the lines (07: 48 dp min plus its 1 dp borders; shot 263–311). | minor | `lobby/RoomLobby.kt` rules row: `heightIn(min = 48.dp)` on the content, with the hairlines outside it. |
| V2-10 | every dialog (`match-forfeit/leave-dialog`, `report-dialog`, `settings-delete-dialog`, `server-error-dialog`, …) | The backdrop is `#999999` (40 % **black** window dim). | `#A0A1A3` = `scrim` token (40 % ink) over white, as on every sheet and in the shot `report-dialog-ko`. | minor | `design/Surfaces.kt` l. 321–327: `window.setDimAmount(0f)`, and draw `Box(Modifier.fillMaxSize().background(NbColors.scrim))` behind the dialog card inside the dialog window. |
| V2-11 | `result-innings-ko-font200` | Round **1 is hidden** under the pinned label column (the table starts scrolled to the end), with no left fade: "나 0 2 1 2 4" shows 5 of 6 rounds. | 16 dp left fade whenever the table is scrolled from its start (06 §1.1, ADAPTIVE §2.4). | minor | `results/Results.kt` innings table: apply the 16 dp left fade when `scrollState.value > 0`, not only above 9 rounds. |
| V2-12 | `tutorial-3-*` (ko and all QA locales) | The legend is plain caption text ("S 숫자와 자리가 맞음 · B 숫자만 맞음") about 200 dp below the coached row, just above the slots. | "Under it a 1-line legend **using the same chips**" (01 §2 step 3), directly under the ringed latest row. | minor | `tutorial/Tutorial.kt` step 3: place the legend 8 dp under the latest row: `HintChips` 1S · caption, 1B · caption. |
| V2-13 | `qa-locale-home-ja`, `qa-locale-home-offline-ja` | The mode tile "コンピューター" wraps with **"ー" alone on line 2** (a word break and a kinsoku error). | No word broken inside (DS 11.3 / 14). | minor | `values-ja/strings.xml`: "コンピュータ" (or "CPU対戦"). In `home/Home.kt` `ModeTile`, use `lineBreak = LineBreak.Heading` so CJK never breaks before ー. |
| V2-14 | `result-lose-*`, `result-link-*` (and the large-text variants) | "내 기록" shows "–" in **`ink`**. The fixtures pass no `myWins`, so these renders don't reproduce their shots ("12 S" / "13승"). | Unknown record: "–" in `text3` (12.20 / 06 §1, clarified). Fixtures as in the shots. | minor | `results/Results.kt` `summaryCells`: colour the "–" `text3`. `Screenshots.kt` `resultLose` / `resultLink`: pass `myWins = 12` / `13`. |
| V2-15 | (behaviour, rejected deviation) | No bubble after sending a quick phrase; the sheet just closes. | My phrase as an `accentTint` bubble under the opponent row for 2.8 s (05 §2.8). It needs no protocol: the client knows what it sent. | minor | `match/MatchScreen.kt` phrase flow: on a successful send, push a local `PhraseBubble(mine = true)` into the same bubble slot as received phrases. |
| V2-16 (M-1) | `report-card-norecord-ko` | The handle is at 523 vs 526 dp, and "기록을 볼 수 없어요" at 614.5 vs 609 (header block 3 dp taller, message 5.5 dp lower). | Shot. | minor | `report/Report.kt` no-record branch: drop the extra top spacing above the message (use the 12 dp of the record row's top padding). |
| V2-17 (M-2) | `lobby-reconnect-small-ko` | The reconnect banner leaves only a sliver of the first seat. | Team header + one full seat (ADAPTIVE §1 Lobby). | minor | `lobby/RoomLobby.kt`: in compact height with the banner, put the invite row into the scroll region too. |
| V2-18 (M-3) | (code) | `WordFit` still has no absolute floor (`design/Text.kt` l. 121–131). | 11 dp rendered floor (DS 11.3). | minor | `design/Text.kt` `WordFit`: `minSize = max(max * minScale, 11.sp-in-dp)`. |
| V2-19 (M-4) | `result-lose-de`, `match-small-ru`, `home-de` | de "Du verlierst" / "Start" for Home; ru "Назвать". | The design strings: "Verloren" and "Startseite" (shot `result-lose-de`); ru keeps the pitch metaphor, as `REVIEW_FINAL` M-4 says. | minor | `values-de`, `values-ru` `strings.xml`. |
| V2-20 (M-5) | `qa/layout-baseline.tsv` | 5 ellipsis findings, all at 360 × 320 + 200 % in de (was 7). | 0. | minor | Unchanged from `REVIEW_FINAL`. |

Checked and **not** defects:
- **Chips, slots and keys.** Chip sizes (28 dp, S 44 × 28, OUT 94 × 28), slot plates 60 × 64, keys 59–60 × 52, timer pill 88 × 36 and the 3 dp progress line all measured identical to the shots. So did the latest row (328 × 64, radius 12), the memo plates and the ruled-out strokes.
- **Shared parts.** Dividers (inset 56, to the edge), sheet handles, toggles, the slider and radios match. So do the badge and plates on results and the 광고 제거 line: its 48 dp target ends **23.5–24 dp** above the band on every result render with a banner.
- **Dark system-bar icons.** `SystemBarStyle.light`, `windowLightStatusBar` and `forceDarkAllowed=false` are set.
- **Contrast.** All text pairs in the renders are tokens with AA ratios from `tokens.json`. The checked ones are `text3` on `surface2` 5.25, `text2` on `bg` 7.85, and white on `strike` 5.43. Disabled text is exempt.
- **Targets.** Text buttons, links, 메모, chips and 광고 제거 all keep 48 dp (`NbButton` `heightIn(min = 48.dp)` for text-like kinds; `targetOverflow` on the memo pill; chip row padding).
- **Sheet height.** The memo, innings and phrase sheets are 8–10 dp taller than the shots. The build follows the spec text (56 dp header; bottom 24 dp + inset), and the shots draw 16 dp. I resolved this in the spec in favour of the build (§4).
- **Search tile digits.** On the search screen the tile digits sit 4 dp lower than in the shot. The build is centred in the 48 dp tile; the shot isn't, and Home's tiles match.

## 2. Deviation decisions (README "Redesign v2")

| Spec | Engineer's deviation | Decision |
| --- | --- | --- |
| 12.9 memo sheet, 06 §1.1 innings sheet: `ModalBottomSheet` | Own `SheetFrame`, in-window; no drag-to-dismiss | **Accepted for 1.0.** The reason is right: reveals, sounds, the clock and the live keypad marks must keep running under the sheet. Scrim tap, back and `close` dismiss it. The grab handle still promises drag, so a vertical drag on the handle and header is a 1.1 follow-up (DS 12.17 note). |
| 06 §1 "내 기록 13승" | Cached `/api/v1/me` wins; may lag by the match just played | **Accepted as interim.** Unknown shows "–" in `text3` (V2-14). For 1.1, ask for the player's stats in the protocol's match-end message, so the number the player just earned is never one short. |
| 06 §1 summary units | Digits Inter, unit in the system face at the same size | **Accepted.** This is correct per DS 3.1; 12.20 is reworded to say so. |
| 05 §2.8 own-phrase bubble | Only received phrases show a bubble | **Rejected → V2-15.** It is not a protocol change: the client shows what it sent. Without it, sending gives no feedback but the sheet closing. |
| ADAPTIVE §1 compact Home digit tiles | Numbers only, no "자리" | **Accepted.** ADAPTIVE §1 updated (TalkBack keeps "4자리"). |
| ADAPTIVE §3 medium / expanded windows | v1 layouts (centred 560 dp column, memo as a sheet) | **Accepted as interim for 1.0.** The renders show nothing broken. The two-pane / three-region layouts are the 1.1 target (note in ADAPTIVE §3). |
| 12.3 opponent row tap | The opponent log opens from any row's opponent hint and the menu, not from the row | **Accepted.** 12.8 names the hint; 12.3 now says the row is not a button. |
| `onMemoCollapse`, `MemoState.collapsed` unused | Kept in the API | **Accepted** (engineering hygiene, no UI effect). Remove with the next cleanup. |
| `team-final-ko` frame (gesture inset, no status glyphs) | Test frame with real insets | **Accepted** as a frame difference. It exposed a real fit problem on devices with a gesture bar, now fixed in the spec (06 §4 Fit) and tracked as V2-07. |

The earlier accepted deviations (README "Deviations from the design") stay accepted. The transfer-code entry on the nickname screen is accepted as built and now written into 01 §1.

## 3. Overall impression against the owner's request

The owner asked for simple, refined, uncluttered and white. The phone screens deliver it:
- **Home** shows four numbers, the hero, three tiles and one blue button.
- **The match** has no header clutter, and the chips carry the meaning.
- **The result** gives a verdict, the opponent's number and three numbers.

Settings, terms, legal and report are quiet lists. One accent per screen holds everywhere I looked. Where it still looks unpolished, it is at the edges:
- the dark toast over the slots (V2-02)
- the clipped header label at large text (V2-01)
- half a row on the team result (V2-07)
- the clipped headline in the small search window (V2-03)
- the floating tutorial legend (V2-12)
- hard cuts where a scroll region meets a pinned bar without a fade (V2-05)

### Top 5 polish suggestions

1. **Name ellipsis that hugs the tag.**
   - **Where.** On the team board ("Größe…  #7731": 9 dp between "…" and "#") and in the lobby (6 dp). An ellipsized `Text` in `NameWithTag` keeps the full constraint width, so a hole opens before the tag. The prototype has the same flaw.
   - **Fix.** `design/Foundation.kt` `NameWithTag`: measure the tag first, then give the name exactly the remaining width and draw it with `onTextLayout` → `Modifier.width(layout.getLineRight(0))`. A custom `Layout` with two measurables also works. The gap becomes the specified 1 dp.
2. **Results at large text: show the summary without scrolling.**
   - **Where.** At 200 % the summary and the innings link sit entirely below the fold. Only a faded hairline hints at them, so the result's own numbers are invisible at first.
   - **Fix.** In `results/Results.kt`, at `isLargeFont()`, hide the 64 dp badge (the headline carries the outcome) and drop the 4 dp + 12 dp around it. That brings the first summary row into view at 360 × 800.
3. **Home at large text: keep the three ways in visible.**
   - **Where.** At 200 % the mode tiles are cut to a 30 dp strip of icons above the pinned quick-match block, which reads as broken even though it scrolls.
   - **Fix.** In `home/Home.kt`, use the compact 56 dp tiles (icon left of label) when `isLargeFont()`, as in the small window. Then all three fit, and the region scrolls only when it truly must.
4. **One fade rule for every scroll edge.**
   - **Where.** Three scroll regions meet a pinned bar with a hard cut: the terms gate ("약관 버전" cut under "동의하고 계속", also in the shot), the nickname body (V2-05) and the large-text result.
   - **Fix.** Apply the same 20 dp `bg` fade wherever a scroll region meets a pinned bar. One `Modifier.scrollFade(state)` in `design/Foundation.kt` would cover the terms gate, nickname, results, Home, lobby and settings.
5. **Drag on sheets.**
   - **Where.** Every sheet shows a grab handle, but none follows a finger. On a phone that is the first thing people try, and nothing happening feels unfinished.
   - **Fix.** Add a vertical `draggable` on the `SheetFrame` header that translates the sheet and dismisses past 30 % of its height or on a fling. No window is needed, so the in-window reason for the deviation still holds.

## 4. Spec changes made in this review

Every change resolves an ambiguity or a mismatch between a shot and the spec text, as the design decision stated:

- **DESIGN_SYSTEM 12.17**
  - Sheet header 56 dp.
  - Content ends 24 dp + navigation inset (the shots draw 16).
  - In-window sheets without drag accepted for 1.0.
  - Dialog scrim = the `scrim` token drawn in the dialog window, never the black platform dim.
  - Match toast sits 8 dp above the entry head.
- **12.12:** 4 dp below my row.
- **12.20:** Inter digits + `sans` unit on one baseline; an unknown value is "–" in `text3`.
- **12.3:** the row is not a button; the opponent log opens from the hint and the menu.
- **12.8:** the 11 sp "상대" label is the one documented exception to the 12 sp minimum.
- **01 §1:** transfer link row ("다른 기기에서 옮겨 오기", `text2`, 48 dp target) and a 20 dp fade for the scrolling body.
- **02:** default digit count 3 on first launch, unchanged from v1. The v2 text had silently said 4; the shots show a returning player.
- **05 §2.8:** phrase sheet header 56 dp.
- **06 §1:** summary record lag accepted, "–" `text3`.
- **06 §4 / 08:** team result hides the rules caption, so a 3 v 3 fits with a gesture bar.
- **ADAPTIVE §1:** compact Home digit tiles without "자리"; match toasts at the top of the log in compact windows; compact quick-match found state.
- **ADAPTIVE §3:** v1 tablet layouts accepted as interim for 1.0.
