# Adaptive layouts

Design system v2 (Clean White, 2026-11-26). Window classes come from Compose `WindowSizeClass`. Height compact < 480 dp, width compact < 600, medium 600–839, expanded ≥ 840 dp. Rules apply to the **window**, not the device: split-screen, pop-up windows and freeform all use them.

Smallest supported window: **320 × 320 dp**; banner ads are hidden below **480 dp** window height (DESIGN_SYSTEM 4.1).

## 1. Small window (height < 480 dp, e.g. 360 × 320 split-screen)

Reference shots: `shots/match-small-ko.png`, `shots/match-small-de.png` (360 × 320, density 2).

**Global**
- Margins 12 dp. No banner ads are requested or shown. Toasts sit 8 dp above the bottom, except on the match screen, where they sit at the top of the log region (8 dp under the progress line) so they never cover the slots or keypad.
- **Quick-match found (review v2):** the avatar and the "VS" line are hidden; name + tag (`title`) and "상대를 찾았어요" sit 16 dp under the digit tiles; nothing is clipped at 360 × 320.
- Top bars are 48 dp. Section headers shrink to 32 dp.
- Sheets open full height of the window with the top radius kept.

**Match (the key case)**, top to bottom at 360 × 320:

| Region | Height | Content |
| --- | --- | --- |
| Top bar | 48 | `close` · two lines: round (`subtitle`) over opponent status (6 dp dot + `caption`, status word only, no name) · `edit_note` (opens the memo sheet) · timer pill · `more_vert`; then the 3 dp progress line, 12 dp margins |
| Log | flexible (≥ 88) | Rows 40 dp, latest 48 dp (`surface2`, radius 12, guess `numLog`, no "상대" label); hint chips 24 dp tall, min width 38 dp, OUT 82 dp; columns 22 · flex · chips · opponent hint ≥ 44; 12 dp side padding, 20 dp bottom fade when it scrolls |
| Entry | 8 + 48 + 8 + 100 + 8 | No entry head. Slots 48 × 48 dp (`numSlotSm`, tracking 0), gap 8; keypad 2 rows × 6 columns, gap 4 dp, keys 48 dp tall: `0 1 2 3 4 ⌫` / `5 6 7 8 9 던지기`; last column 64 dp, label `labelSmall`; ruled-out strokes and in-bars as normal |

- Opponent row and entry head (my number, 메모 button) are hidden. `edit_note` opens the memo sheet at the full window height (header 48 dp, cells 40 dp; the position rows scroll inside the sheet when n ≥ 4).
- My number is available from `more_vert` > "내 숫자 보기" (toast, 2.8 s) because there is no room for the entry head.
- **Secret entry:** same entry block; the top bar title reads "비밀 숫자" and the log region shows the legend box rows at 40 dp (box padding 12). Keypad rows: `0 1 2 3 4 ⌫` / `5 6 7 8 9 정하기`; the action key reads "정하기" / "Lock in" in `labelSmall`, **one line** (never the long "이 숫자로 정하기"). Random fill: long-press `⌫` keeps "clear all"; random fill is the first item of `more_vert` ("무작위로 채우기" / "Fill randomly"), so the 6-column grid stays 6 columns.
- **Reconnecting:** the connection banner is one line, 40 dp (chase dots · title · countdown); the explanation line is TalkBack-only. The log keeps ≥ 88 dp.
- **Duel result:** top to bottom: top bar 48 dp (`close` · `flag`); headline row 56 dp (badge 32 dp with a 20 dp icon + 12 dp + headline `title`, one line); answer plates 40 × 44 dp (`ink`, `numSlotSm` tracking 0) centred (label, my number, summary and the innings link hidden; TalkBack still reads the sub line, "상대 숫자 3 9 1 6" and the summary); flexible space; buttons side by side, 48 dp each, 8 dp gap, 12 dp margins and bottom padding. No sub line, no 광고 제거 line, no banner, no interstitial.
- **Team result:** top bar 48 dp (`close` · "팀 대전" · `chat`); headline row 56 dp with the score inline: badge 32 dp · "승리" (`title`) · "A 2 : 1 B" (team letters `numLabel`, score Inter 700 28 sp, capped like every `num` style); then the duel rows in a scroll region (rows 56 dp: names one line each side + status column 72 dp; best hints and the 승 tag move into the status column as "승 A" / "2S 1B · 1S 2B"); buttons side by side 48 dp pinned at the bottom. The score is never hidden: it is the result.
- **Team board (live):** score block 56 dp (badges · score · badges, no rule line), rows as in the team result, watching note one line.
- **Home:** top bar 48 dp; Me row without the "내 기록" caption; record values only (labels in TalkBack), one line; hero hidden; mode tiles 56 dp (icon left of the label); quick-match head hidden; digit tiles 48 dp showing the number only (no "자리" unit; TalkBack keeps "4자리"; review v2) and the button 48 dp, side by side when the width allows (tiles 4 × 48 dp + button flex), else stacked; the region above the quick-match block scrolls. No banner.
- **Lobby:** invite code row (plates 40 × 48) with copy and share icon buttons on the same row. Invite row, rules band, fill status and teams are **one** vertical scroll region; only the practice button and the chat bar are pinned below it. The fill-status caption is dropped; teams stack A above B when the region is narrower than 2 × 156 dp. The region always shows at least the team header and one full seat (36 + 56 dp) before scrolling; if the window cannot give it that, the practice button moves into the scroll region too.
- **Settings:** unchanged rows; the 구매 section works the same (Play's purchase sheet is a system window and sizes itself).
- **Interstitial:** never requested or shown in a window shorter than 480 dp (same threshold as the banner); the result simply appears.

**Width < 360 dp** (some pop-up windows): margins 12, keypad gap 4, slot width formula still applies (min 44 dp); the opponent column of the log is hidden below 320 dp width.

## 2. Large font (fontScale ≥ 1.5; tested at 2.0)

Between 1.0 and 1.5 the normal layouts hold. From 1.5 the rules below replace them. Numbers are for 360 × 800 at fontScale 2.0.

### 2.1 What scales

- **`sans` text scales fully** (Android 14 non-linear scaling applies). It wraps (titles max 2 lines, buttons 2 lines, body unlimited); it is never clipped. Ellipsis only for nicknames (the name part, never the tag) and the one-line status texts named below.
- **Every `num` style caps at 1.3×**, everywhere, computed in dp (DESIGN_SYSTEM 3.3). There is no other numeral size rule: slots, keys, memo, plates, badges, `OUT`, hints, log guesses, timers, scores, tags all follow it.
- **Every container uses minimum heights** (`heightIn(min = …)`), never fixed heights. The only fixed sizes are the 48 dp memo-sheet cells and the plates/keys, whose capped numerals always fit (keys: `numKey` 30 × 1.3 = 39 dp line in a 52 dp key).
- Banner ads do not scale.

### 2.2 Match screen (large-text layout)

| Region | Height (min) | Content |
| --- | --- | --- |
| Top bar | 72 | `close` · round title (`subtitle`, up to 2 lines; mode subtitle hidden) · pitch clock (capped `numTimer`, its icon 18 dp) · `more_vert`. Small window + large text: 48 dp, round only, one line, ellipsis |
| Opponent row | 64 | Avatar hidden. Line 1: opponent name + tag (name ellipsizes, tag never) · chat button 48 dp. Line 2: status (6 dp dot + `caption`, one line, ellipsis) |
| Log | flexible, ≥ latest + 1 row | Rows **52 dp** min, latest **72 dp** min. Columns: round 28 dp · guess flexible · hint chips (capped, 28 × 1.3 = 36.4 dp tall). The guess cell is **never** truncated or wrapped (`softWrap = false`, measured first). The opponent hint moves to a **second line under the guess** (`numLabel` capped `text3`; on the latest row prefixed by the "상대" label on the same line), so it never competes for width (redesign shot `redesign/A-match-font200.png`) |
| Entry head | 48 min | "내 숫자" label hidden (digits only, TalkBack keeps the label); the 메모 button shows `open_in_full` + "메모" and opens the large memo sheet (§2.3); if the line cannot hold both, the digits move to TalkBack and `more_vert` > 내 숫자 보기 |
| Entry | 12 + 64 + 12 + keypad | Slots 64 dp min, keys 52 dp min (they grow only if a capped numeral would not fit, which it does at 2.0); Pitch / Lock-in labels `label`, may wrap to 2 lines in a key that grows to 64 dp |

- Clipping check: with 6 digits the latest guess (`numSlotSm` 26 × 1.3 = 33.8 dp, tracking 0.12 em) is about 160 dp wide and fits beside 28 dp + two capped chips (2 × 52 + 6 dp) at 360 dp. QA: `match-6digits-ko-font200` must show all six digits of every guess.
- Legend box rows (secret phase): 44 dp min, explanation wraps to 2 lines; chips capped.
- Quick-phrase sheet: buttons 48 dp min, labels wrap to 2 lines by 어절.
- Toasts and the connection banner wrap; the banner may take 3 lines and pushes the log, whose minimum is the latest row.

### 2.3 Large memo sheet

Opened by the 메모 button whenever fontScale ≥ 1.5 or "Larger memo" is on; replaces the normal memo sheet grid (DESIGN_SYSTEM 11.1 b).

| Part | Spec |
| --- | --- |
| Sheet | Bottom sheet (DESIGN_SYSTEM 12.17), full height of the window minus the status bar; drag, scrim tap, back or `close` dismiss it. The match keeps running underneath |
| Header | 56 dp min: "메모" `title` · pitch clock (capped, same states as the top bar, so time is never hidden) · "지우기" text button · `close` |
| Grid | **Transposed**: one column per position 1..n, one row per digit 0–9. Column header row 40 dp: position numbers (`numLabel` capped, `text3`), centred over each column. Row header column 48 dp wide: the digit as a 48 × 48 dp **digit-status button** (same cycle: unknown → out → in; struck digit / `ink` plate). Cells **48 × 48 dp**, no gaps, 1 dp `line` between rows; cell visuals as the normal memo (4 dp `dotOff` dot / 10 dp × / `ink` plate, 32 dp here) |
| Width | 48 + n × 48 dp, centred: n = 6 → 336 dp, which fits 360 dp with 12 dp margins; n ≤ 5 keeps 16 dp margins. Below 336 + 24 dp window width the grid scrolls horizontally with the digit column pinned |
| Height | 56 + 40 + 10 × 48 = 576 dp + insets; scrolls vertically (header and position row pinned) when the window is shorter |
| Behaviour | Same rules as the normal memo (setting **in** marks the rest of the row and column out; out digit columns at 35 % opacity, here out digit **rows**). Changes show at once on the keypad marks. The sheet remembers its scroll position per duel |
| TalkBack | Cells as in 11.2 ("자리 2, 숫자 9, 확정. 두 번 탭하면 바꿉니다."), traversal row by row (digit 0 positions 1..n, then digit 1…) |

### 2.4 Results

- Duel result: badge → innings link is one vertical scroll region; actions and the 광고 제거 line pinned. The summary stacks to three rows (label left, value right, 48 dp min each); answer plates capped (60 × 68 grows to 60 × 78 min with the 1.3× digit). Buttons grow (padding 16 dp vertical) and wrap to 2 lines. The innings sheet scrolls horizontally with the label column pinned. The 광고 제거 line wraps to 2 lines if needed and keeps its 24 dp clear space.
- Team board and team result: rows `heightIn(min = 72.dp)`; each side stacks name (1 line, name ellipsizes) over its best hint or 승 tag; the status column wraps to 2 lines; badge letters capped.

### 2.5 Other screens

- Top bar titles wrap to 2 lines (bar grows to 72 dp); mode subtitle is hidden.
- Buttons grow in height (padding 16 dp vertical) and wrap to 2 lines instead of truncating.
- Home: record row wraps to 2 lines (W L D on one, win rate on the next); the hero hides; mode tiles keep 84 dp min and grow (labels 2 lines); digit tiles grow to 64 dp.
- Lobby: teams stack vertically (A above B) instead of two columns; the whole body is one scroll region (as in §1).
- Settings rows: switch moves below the text when the title would take more than 2 lines; the 구매 rows' price / icon moves below the subtitle in the same way.
- Chat: chips 48 dp target, labels one line (chips scroll); system lines wrap to 3 lines max.

### 2.6 Small window and large text together

Small-window rules (§1) decide the structure; §2.1 decides sizes. Where both cannot fit (320 × 320 at 2.0), the match log keeps the latest row only and scrolls, the memo is the sheet, and nothing else is dropped.

## 3. Tablets and landscape

*Review v2: for 1.0 the build keeps the v1 tablet layouts (centred 560 dp column, memo as a sheet); accepted as interim, the panes below are the 1.1 target.*

**Medium width (600–839 dp), any height ≥ 480 dp**
- Content is centred with max width 560 dp on Home, lobby, results, settings, terms; background `bg` fills the rest.
- Match: two panes, 24 dp gutter, 24 dp margins: **left** = opponent row + log (flexible), **right** = entry head + slots + keypad, fixed width 360 dp, vertically centred in the lower half. Keys 56 dp tall. The memo stays a sheet (centred dialog above 640 dp, see below).

**Expanded width (≥ 840 dp)**
- Match: three regions: left log (min 320 dp), centre **permanent memo panel** (the memo sheet's content inline, cells 40 × 40 dp, header 44 dp, no 메모 button), right entry 360 dp. The opponent's full log opens as a right side panel instead of a sheet.
- Team match: the board is a permanent right panel (360 dp) beside my duel.
- Chat in rooms: permanent right panel 360 dp in the lobby instead of a sheet.
- Dialogs max width 400 dp; sheets become centred dialogs (max width 560 dp) when the window is wider than 640 dp.

**Phone landscape (height < 480 dp, width ≥ 600 dp)**
- Uses the compact rules for height, but two columns: left = log and the `edit_note` memo button; right = slots + keypad (5 × 2 + action row, keys 44 dp visual / 48 dp target height) in 360 dp. No banner ads.

**Foldables:** on a half-opened (tabletop) posture with a horizontal hinge, the entry block (slots + keypad) moves to the bottom half and the log/memo to the top half (`WindowLayoutInfo` `FoldingFeature`).

## 4. What QA checks

- 360 × 800, 360 × 640, 360 × 320, 412 × 915, 600 × 960, 840 × 1200, 915 × 412 (landscape), each at fontScale 1.0 and 2.0, in ko, de, ru, vi.
- No clipped text, no overlapped controls, no banner in any window shorter than 480 dp, all targets ≥ 48 dp except the documented memo grid cells at fontScale < 1.5.
