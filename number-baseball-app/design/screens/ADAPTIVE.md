# Adaptive layouts

Window classes come from Compose `WindowSizeClass`. Height compact < 480 dp, width compact < 600, medium 600–839, expanded ≥ 840 dp. Rules apply to the **window**, not the device: split-screen, pop-up windows and freeform all use them.

Smallest supported window: **320 × 320 dp**; banner ads are hidden below **480 dp** window height (DESIGN_SYSTEM 4.1).

## 1. Small window (height < 480 dp, e.g. 360 × 320 split-screen)

Reference shots: `shots/match-small-ko.png`, `shots/match-small-de.png` (360 × 320, density 2).

**Global**
- Margins 12 dp. No banner ads are requested or shown. Toasts sit 8 dp above the bottom.
- Top bars are 48 dp. Section headers shrink to 32 dp.
- Sheets open full height of the window with the top radius kept.

**Match (the key case)**, top to bottom at 360 × 320:

| Region | Height | Content |
| --- | --- | --- |
| Top bar | 48 | `close` · two lines: round (`subtitle`) over opponent status (6 dp lamp + `caption`, status word only, no name) · `edit_note` memo toggle · pitch clock · `more_vert`; 2 dp clock line at the bottom edge |
| Log | flexible (≥ 88) | No header row. Rows 40 dp, latest 48 dp (no lamps), columns 24 · flex · 40 · 40 · 64 dp, 12 dp side padding, 16 dp bottom fade when it scrolls |
| Entry | 8 + 48 + 8 + 100 + 8 | Slots 48 × 48 dp (`numSlotSm`), gap 8; keypad 2 rows × 6 columns, gap 4 dp, keys 48 dp tall: `0 1 2 3 4 ⌫` / `5 6 7 8 9 Pitch`; the last column is 64 dp wide, Pitch uses `labelSmall` |

- Duel strip, log header and memo board are hidden. The memo toggle swaps the log region for the memo board (toolbar 32 + digit row 32 + rows 28 each; with n ≥ 4 the position rows scroll inside the region); tap again to return to the log.
- My secret is available from `more_vert` > "내 숫자 보기" (shows it in a toast for 2.8 s) because there is no room for the mini plates.
- **Secret entry:** same entry block; the top bar title reads "비밀 숫자" and the log region shows the 3-line legend at 40 dp rows. Keypad rows: `0 1 2 3 4 ⌫` / `5 6 7 8 9 정하기`; the action key reads "정하기" / "Lock in" in `labelSmall`, **one line** (never the long "이 숫자로 정하기"). Random fill: long-press `⌫` keeps "clear all"; random fill is the first item of `more_vert` ("무작위로 채우기" / "Fill randomly"), so the 6-column grid stays 6 columns.
- **Reconnecting:** the connection banner is one line, 40 dp (chase lamps · title · countdown); the explanation line is TalkBack-only. The log keeps ≥ 88 dp.
- **Duel result:** hero (lamps + headline + sub) and the two rows of slots are replaced by, top to bottom: top bar 48 dp (`close` · `flag`; no "광고 제거" link in small windows); headline row 56 dp (12 dp lamps + headline `title`, one line); "상대 숫자" plates 40 × 44 dp centred (the label and my number are hidden; TalkBack still reads "상대 숫자 3 9 1 6" and the sub line); flexible space; buttons side by side, 48 dp each, 8 dp gap, 12 dp margins and bottom padding. No sub line, no line score, no banner, no interstitial.
- **Team result:** top bar 48 dp (`close` · "팀 대전" · `chat`); headline row 56 dp with the score inline: lamps · "승리" (`title`) · "A 2 : 1 B" (team letters `numLabel`, score Barlow 700 28 sp, capped like every `num` style); then the duel rows in a scroll region (rows 56 dp: names one line each side + status column 72 dp; best hints and the "승" tag move into the status column as "승 A" / "2S 1B · 1S 2B"); buttons side by side 48 dp pinned at the bottom. The score is never hidden: it is the result.
- **Team board (live):** score block 56 dp (badges · score · badges, no rule line), rows as in the team result, watching note one line.
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
| Duel strip | 72 | Two lines. Line 1: `VS` (one line, `softWrap = false`) · opponent name + tag (name ellipsizes, tag never) · quick-phrase button 48 dp. Line 2: status (6 dp lamp + `caption`, one line, ellipsis) · flexible · mini plates (capped, 22 × 26 dp). The "내 숫자" label is hidden (TalkBack keeps it on the plates) |
| Log header | 36 | `#`, "내 추측", "판정", "상대", one line each; a label that does not fit wraps to 2 lines (header grows), never ellipsizes |
| Log | flexible, ≥ latest + 1 row | Rows **52 dp** min, latest **72 dp** min. Columns: round 28 dp · guess flexible · S · B · opponent. The guess cell is **never** truncated or wrapped: it is measured first (`softWrap = false`, its intrinsic width) and the other columns take what is left: S/B 40 dp each, opponent column ≥ 56 dp. If the opponent column cannot get 56 dp (6 digits), it is hidden and its header becomes the "상대 기록" button that opens the opponent sheet |
| Memo | 48 | Toolbar only (§2.3): "메모" · "지우기" · `open_in_full` icon button. The inline grid is never shown at large text |
| Entry | 12 + 64 + 12 + keypad | Slots 64 dp min, keys 52 dp min (they grow only if a capped numeral would not fit, which it does at 2.0); Pitch / Lock-in labels `label`, may wrap to 2 lines in a key that grows to 64 dp |

- Clipping check: with 6 digits the latest guess (`numSlotSm` 28 × 1.3 = 36.4 dp, tracking 0.12 em) is about 150 dp wide and fits beside 28 + 40 + 40 dp with the opponent column hidden. QA: `match-6digits-ko-font200` must show all six digits of every guess.
- Legend rows (secret phase): 44 dp min, explanation wraps to 2 lines; `OUT` tag capped.
- Quick-phrase sheet: buttons 48 dp min, labels wrap to 2 lines by 어절.
- Toasts and the connection banner wrap; the banner may take 3 lines and pushes the log, whose minimum is the latest row.

### 2.3 Large memo sheet

Opened by the memo toolbar's `open_in_full` button (and by tapping "메모") whenever fontScale ≥ 1.5 or "Larger memo" is on; replaces the inline memo grid entirely (DESIGN_SYSTEM 11.1 b).

| Part | Spec |
| --- | --- |
| Sheet | Bottom sheet (12.16), full height of the window minus the status bar; drag, scrim tap, back or `close` dismiss it. The match keeps running underneath |
| Header | 56 dp min: "메모" `title` · pitch clock (capped, same states as the top bar, so time is never hidden) · "지우기" text button · `close` |
| Grid | **Transposed**: one column per position 1..n, one row per digit 0–9. Column header row 40 dp: position numbers (`numLabel` capped, `text3`), centred over each column. Row header column 48 dp wide: the digit as a 48 × 48 dp **digit-status button** (same cycle as the normal header row: unknown → out → in; same visuals: struck digit / chalk plate). Cells **48 × 48 dp**, no gaps, 1 dp `line` between rows; cell visuals as the normal memo (4 dp dot / 10 dp × / 24 dp plate, scaled to 32 dp plate here) |
| Width | 48 + n × 48 dp, centred: n = 6 → 336 dp, which fits 360 dp with 12 dp margins; n ≤ 5 keeps 16 dp margins. Below 336 + 24 dp window width the grid scrolls horizontally with the digit column pinned |
| Height | 56 + 40 + 10 × 48 = 576 dp + insets; scrolls vertically (header and position row pinned) when the window is shorter |
| Behaviour | Same rules as the normal memo (setting **in** marks the rest of the row and column out; out digit columns at 35 % opacity, here out digit **rows**). Changes show at once on the keypad marks. The sheet remembers its scroll position per duel |
| TalkBack | Cells as in 11.2 ("자리 2, 숫자 9, 확정. 두 번 탭하면 바꿉니다."), traversal row by row (digit 0 positions 1..n, then digit 1…) |

### 2.4 Results

- Duel result: hero → line score is one vertical scroll region; actions pinned. The line score shows only "최고" plus the last 3 rounds; the full table opens from a text button "회별 기록 보기" under it (06 §5). Buttons grow (padding 16 dp vertical) and wrap to 2 lines. The "광고 제거" link stays in the top bar; if the top bar would need 2 lines for it, the link moves to a centred text button under the line score.
- Team board and team result: rows `heightIn(min = 72.dp)`; each side stacks name (1 line, name ellipsizes) over its best hint or "승" tag; the status column wraps to 2 lines; badge letters capped.

### 2.5 Other screens

- Top bar titles wrap to 2 lines (bar grows to 72 dp); mode subtitle is hidden.
- Buttons grow in height (padding 16 dp vertical) and wrap to 2 lines instead of truncating.
- Home: record row wraps to 2 lines (W L D on one, win rate on the next); mode rows keep 64 dp minimum and grow.
- Lobby: teams stack vertically (A above B) instead of two columns; the whole body is one scroll region (as in §1).
- Settings rows: switch moves below the text when the title would take more than 2 lines; the 구매 rows' price / icon moves below the subtitle in the same way.
- Chat: chips 48 dp target, labels one line (chips scroll); system lines wrap to 3 lines max.

### 2.6 Small window and large text together

Small-window rules (§1) decide the structure; §2.1 decides sizes. Where both cannot fit (320 × 320 at 2.0), the match log keeps the latest row only and scrolls, the memo is the sheet, and nothing else is dropped.

## 3. Tablets and landscape

**Medium width (600–839 dp), any height ≥ 480 dp**
- Content is centred with max width 560 dp on Home, lobby, results, settings, terms; background `bg` fills the rest.
- Match: two panes, 24 dp gutter, 24 dp margins: **left** = duel strip + log + memo (flexible), **right** = slots + keypad, fixed width 360 dp, vertically centred in the lower half. Keys 56 dp tall.

**Expanded width (≥ 840 dp)**
- Match: three regions: left log (min 320 dp), centre memo board (cells 40 × 36 dp, header 40 dp), right entry 360 dp. The opponent's full log opens as a right side panel instead of a sheet.
- Team match: the board is a permanent right panel (360 dp) beside my duel.
- Chat in rooms: permanent right panel 360 dp in the lobby instead of a sheet.
- Dialogs max width 400 dp; sheets become centred dialogs (max width 560 dp) when the window is wider than 640 dp.

**Phone landscape (height < 480 dp, width ≥ 600 dp)**
- Uses the compact rules for height, but two columns: left = log (with header) and memo toggle; right = slots + keypad (5 × 2 + action row, keys 44 dp visual / 48 dp target height) in 360 dp. No banner ads.

**Foldables:** on a half-opened (tabletop) posture with a horizontal hinge, the entry block (slots + keypad) moves to the bottom half and the log/memo to the top half (`WindowLayoutInfo` `FoldingFeature`).

## 4. What QA checks

- 360 × 800, 360 × 640, 360 × 320, 412 × 915, 600 × 960, 840 × 1200, 915 × 412 (landscape), each at fontScale 1.0 and 2.0, in ko, de, ru, vi.
- No clipped text, no overlapped controls, no banner in any window shorter than 480 dp, all targets ≥ 48 dp except the documented memo grid cells at fontScale < 1.5.
