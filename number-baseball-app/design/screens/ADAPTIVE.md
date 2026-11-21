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
- Secret entry: same entry block; the top bar title reads "비밀 숫자" and the log region shows the 3-line legend at 40 dp rows.
- Result in a small window: hero (lamps + headline 32 sp + sub) and the two rows of slots side by side are replaced by: headline row 56 dp, opponent number as plates 40 × 44, buttons side by side (48 dp). No line score, no banner.
- Lobby: invite code row (plates 40 × 48) with copy and share icon buttons on the same row; teams scroll; practice and chat bar stay pinned.

**Width < 360 dp** (some pop-up windows): margins 12, keypad gap 4, slot width formula still applies (min 44 dp); the opponent column of the log is hidden below 320 dp width.

## 2. Large font (fontScale ≥ 1.5; tested at 2.0)

- `sans` text scales fully; numerals in fixed controls cap at 1.3× (DESIGN_SYSTEM 3.3).
- **Match:** the memo board starts collapsed; when expanded it opens as a bottom sheet with a **transposed grid**: rows are digits 0–9, columns are positions 1..n, every cell 48 × 48 dp (the 48 dp target exception disappears). Log rows grow to 52 dp (latest 72 dp). Duel strip becomes two lines (name row, status + my secret row), 72 dp.
- Top bar titles wrap to 2 lines (bar grows to 72 dp); mode subtitle is hidden.
- Buttons grow in height (padding 16 dp vertical) and wrap to 2 lines instead of truncating.
- Home: record row wraps to 2 lines (W L D on one, win rate on the next); mode rows keep 64 dp minimum and grow.
- Lobby: teams stack vertically (A above B) instead of two columns.
- Settings rows: switch moves below the text when the title would take more than 2 lines.
- Banner ads: unchanged (they do not scale).

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
