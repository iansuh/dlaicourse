# Redesign v2 "Clean White": implementation guide

Owner decision 2026-11-26: Direction A "Clean white" ([`redesign/PROPOSAL.md`](redesign/PROPOSAL.md), README "Visual design (owner decision 2026-11-26)"). This file tells the Android engineer what to change. Specs: [`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md) v2, [`tokens.json`](tokens.json) 2.0.0, [`screens/`](screens/); reference shots: [`shots/`](shots/) (all 60 regenerated 2026-11-26 from [`prototype/`](prototype/), `node gen-tokens.js && node shoot.js`).

## 1. Summary

| | v1 Night Game | v2 Clean White |
| --- | --- | --- |
| Theme | dark only | light (dark later, system-driven, same token names) |
| Accent | chalk (white) primary, lamps for meaning | one blue `accent` for primary / selection / progress / links; hint colours only on chips |
| Elevation | surface lightness + level-3 shadow | flat; white sheets on a 40 % ink scrim; dark (`inverse`) toasts |
| Numerals | Barlow Condensed | **Inter** 500 / 600 / 700, tabular (`design/fonts/inter_*.ttf`, OFL 1.1) |
| Radius | 4 / 8 / 16 | 6 / 12 / 16 |
| Hints | coloured text + glowing count lamps | chips: S solid, B outlined, OUT one wide neutral chip, zero = plain `text3` |
| Match | duel strip, log header, inline 10 × n memo board, mini plates | opponent row, no header, **memo in a sheet** behind "메모", ruled-out stroke on keys, "내 숫자" line, timer pill + 3 dp line |
| Home | record + form dots, 3 list rows with subtitles, dock band | 4 numbers, `? ? ? ?` + tutorial link, **3 mode tiles**, quick-match block at the bottom |
| Result | wash, lamps, two plate rows, inning table, 광고 제거 in the top bar | badge, headline, opponent plates, my number line, **3 summary numbers**, "회별 기록 보기" sheet, **광고 제거 line above the banner** |
| Lobby | stripes, outlines, empty borders, band fills | plain `surface2` seats, one border style (empty seats), plain rules row |

Unchanged: every flow, state and policy rule (ads never in matches, interstitial safeguards, remove-ads states, terms gate, age gate (year + month), chat system lines, player card states, team board states incl. null best hints, tutorial, transfer code, window-too-small notice, error dialogs), all motion durations and hooks, sound (DESIGN_SYSTEM 10) and haptics (9), the numeral cap (1.3×) and the shrink floor.

## 2. Token migration

`ui/theme/Tokens.kt` mirrors `tokens.json`. Most names keep their role and only change value; where a v1 token covered two roles in v2 it is split, and **every use of it must be reviewed** (marked ⚠).

### 2.1 Colour

| v1 token | v1 value | v2 | v2 value | Action |
| --- | --- | --- | --- | --- |
| `bg` | `#0C1210` | `bg` | `#FFFFFF` | value |
| `surface1` | `#131A17` | `surface1` | `#F9FAFB` | value; v1 uses as **latest log row, dock, memo band, scrolled top bar, my board row** → v2 `surface2` (latest row, my row) or `bg` (top bar, bands) ⚠ |
| `surface2` | `#1A231F` | `surface2` | `#F3F4F6` | value (keys, slots, fields, tonal) |
| `surface3` | `#222D28` | `surface3` | `#FFFFFF` | value (sheets, dialogs). v1 also used it for toasts, connection banner, offline band, pressed `surface2` → `inverse` (toast), `surface2` (banners), `surface4` (pressed) ⚠ |
| `surface4` | `#2B3732` | `surface4` | `#E5E7EB` | value; now only "pressed on `surface2`". v1 chat bubble / report check circle → `surface2` ⚠ |
| `line` | `#2A3530` | `line` | `#E5E7EB` | value |
| `outline` | `#6C7C74` | `outline` | `#7E8693` | value |
| `chalk` ⚠ | `#ECEFE6` | **`ink`** (text, icons, selected segment, plates) / **`accent`** (primary button, switch on, radio, active slot, focus, progress) | `#111418` / `#2453E6` | **renamed and split**: review all 27 files |
| `chalkPressed` | `#D3D8CC` | **`accentPressed`** | `#1C44C4` | renamed |
| `onChalk` ⚠ | `#0C1210` | **`onAccent`** / **`onInk`** | `#FFFFFF` | renamed and split (text on accent / ink fills) |
| `text2` | `#A5B1AA` | `text2` | `#4B5260` | value |
| `text3` | `#909D96` | `text3` | `#5F6673` | value; **now also zero counts** (`0S`, `0B`, 0 in tables) |
| `textDisabled` | `#56625C` | `textDisabled` | `#B9BEC6` | value; **no longer used for zero counts or masked text** ⚠ |
| `strike` | `#F5B83D` | `strike` | `#B54708` | value (S chip fill with white text, warnings, 승 tag) |
| `ball` | `#45C98B` | `ball` | `#00796B` | value (B chip ring and text, status dot) |
| `out` ⚠ | `#FF6359` | `out` (OUT chip only) / **`danger`** (errors, destructive, timer ≤ 5 s) | `#111418` / `#C4291C` | split: every error / destructive use → `danger` |
| `outTint` | 12 % red | **`dangerTint`** | `#FDECEA` | renamed |
| `strikeTint` | 12 % amber | `strikeTint` | `#FDF0E7` | value; only the solve flash (no win wash, no winning-row wash) |
| `lampOff`, `lampOffRing` | dark greys | **`dotOff`** | `#C9CED6` | merged and renamed |
| `strikeGlow`, `ballGlow`, `outGlow` | 35 % | — | — | **removed** (no glow) |
| `teamA` / `teamATint` | `#6EB0FF` / 12 % | `teamA` / `teamATint` | `#2453E6` / `#EAF0FE` | value; badge letter now white |
| `teamB` / `teamBTint` | `#ECEFE6` / 8 % | `teamB` / `teamBTint` | `#111418` / `#F3F4F6` | value; badge letter now **white** (v1 dark) ⚠ |
| `scrim` | 64 % black | `scrim` | 40 % `#111418` | value |
| `shadow` | 48 % black | — | — | **removed** (flat) |
| `focus` | chalk | `focus` | `#2453E6` | value |
| — | | **`onInk`**, **`accent`**, **`accentPressed`**, **`onAccent`**, **`accentTint`**, **`danger`**, **`dangerTint`**, **`dotOff`**, **`inverse`**, **`onInverse`**, **`inverseAccent`** | | new |

Material scheme (`Theme.kt`): `darkColorScheme` → `lightColorScheme(primary = accent, onPrimary = onAccent, background = bg, surface = bg, onSurface = ink, surfaceVariant = surface2, onSurfaceVariant = text2, surfaceContainer* = surface2 / bg, outline = outline, outlineVariant = line, error = danger, onError = onAccent, scrim = scrim)`; system bar icons dark.

### 2.2 Type (`NbType`)

`sans` styles unchanged. `num` family → Inter; sizes change because Inter is wider:

| Token | v1 | v2 | | Token | v1 | v2 |
| --- | --- | --- | --- | --- | --- | --- |
| `numHero` | 56/60 600 | 48/56 700 | | `numTimer` | 24/28 600 | 18/24 700 |
| `numScore` | 56/60 700 | 48/56 700, −0.01 em | | `numLog` | 22/28 500, 0.14 | 21/28 600, 0.14 |
| `numSlot` | 40/44 600 | 32/40 700 | | `numHint` | 20/24 600, 0.02 | 15/20 700, 0.02 |
| `numCode` | 36/40 600 | 28/36 700 | | `numMemo` | 18/22 600 | 18/24 600 |
| `numStat` | 32/36 600 | 28/32 700, −0.01 em | | `numLabel` | 14/16 600, 0.06 | 13/16 600, 0.02 |
| `numKey` | 30/36 500 | 24/32 600 | | `numTag` | 15/20 500, 0.02 | 15/20 500, 0 |
| `numSlotSm` | 28/32 600 | 26/32 700, 0.12 | | **`numSummary`** (new) | — | 22/30 700 |

### 2.3 Size, layout, radius, elevation

| v1 | v2 | Note |
| --- | --- | --- |
| `radius.s / m` 4 / 8 | 6 / 12 | `l` 16 unchanged |
| `layout.duel_strip_h` 52 | **`opp_row_h`** 52 | renamed |
| `size.button_h_secondary` 48 | 56 | secondary now 56; **new `button_h_tonal` 48** |
| `size.slot_w` 56, `slot_gap` 8 | 60, 10 | width rule min(60, fit), min 44 |
| `size.history_row_h` 44 | 46 | latest 64 unchanged |
| `size.history_header_h` 28 | — | removed (no log header) |
| `size.lamp_l/m/s`, `lamp_gap_*` | **`dot` 8, `dot_s` 6** | lamps removed |
| `size.hint_col_w(_wide)` 44 / 52, `opp_col_w` 72, `round_col_w` 28 | `hint_chip_h` 28, `hint_chip_min_w` 44, `out_chip_min_w` 94, `hint_chip_gap` 6, `opp_col_min_w` 44, `round_col_w` 22 | chips replace fixed hint columns |
| `size.memo_header_h` 32, `memo_cell_h` 28, `memo_toolbar_h` 32 | 44, 40, — | memo is a sheet |
| `size.fade_edge` 16 | 20 | |
| `size.segmented_h` 44 | 48 | tiles; Home `digit_tile_h` 52 |
| — | new: `icon_inline` 18, `plate_result_w/h` 60 / 68, `avatar_l/m` 48 / 36, `timer_pill_h` 36, `progress_h` 3, `memo_button_h` 36, `mode_tile_h` 84, `border` 1.5, `ruled_stroke_l/w` 22 / 2, `layout.banner_clear_space` 24 | |
| `elevation.level3` shadow y 8 blur 24 | none; scrim only; `toast` = `inverse` | remove every `Modifier.shadow` |

Motion and haptic tokens: unchanged (names included).

## 3. Per-screen changes

Paths are relative to `android/app/src/main/kotlin/app/numbaseball/ui/`. "Shots" are the references in `design/shots/`.

| Screen / spec | What changes | Likely files | Shots |
| --- | --- | --- | --- |
| Foundation (DS 2–7, 12) | Token values and renames (§2); light scheme; Inter fonts; radius; no shadows; dark system-bar icons; new `BrandMark` (32 dp ink plate, white 3, three strike dots); avatar; dots replace `Lamp`/`LampRow`/`ChaseLamps` (keep the API name `ChaseLamps` if convenient, draw flat dots) | `theme/Tokens.kt`, `theme/Theme.kt`, `design/Foundation.kt`, `design/Surfaces.kt`, `design/Controls.kt`, `design/Text.kt`, `res/font/*`, `MainActivity` / edge-to-edge setup | all |
| Buttons, tiles, segmented (12.13–12.14) | Primary `accent`; secondary 56 dp `bg` + 1.5 dp `line`; tonal `surface2`; Link variant; segmented = separate tiles with `ink` selection (no track) | `design/Controls.kt` (`NbButton`, `Segmented`, `NumberUnitLabel`, `TextSegmentLabel`) | home, search, settings |
| Switch, slider, radio, field, rows (12.15) | Switch on `accent` / off white + `outline` ring; slider `accent`; radio `accent`; field focus `accent`; dividers on every row | `design/Controls.kt` | settings-*, report-reasons, nickname-* |
| Sheets, dialogs, toasts, banner (12.17) | White sheets, `dotOff` handle, no shadow; dialog actions `accent` / `danger`; toast `inverse`; connection banner full-width `surface2` | `design/Surfaces.kt` (`SheetSurface`, `NbDialog`, `ToastHost`, `ConnectionBanner`, `ServerErrorDialog`), `common/Components.kt` | match-reconnect, report-dialog |
| 01 First run (age, nickname, tutorial) | Age: year + month plates in the v2 slot style (already approved; check it matches 01 §0); accent field focus; links accent; tutorial coach box `surface2`, memo via the sheet at step 4 | `age/AgeGate.kt`, `firstrun/FirstRun.kt`, `tutorial/Tutorial.kt`, `rules/HowToPlay.kt` | age-*, nickname-* |
| 02 Home | Rebuild: Me row (avatar), 4-number record (no dividers, no form dots), `? ? ? ?` hero + tutorial link, 3 mode tiles (no subtitles), quick-match block (head + digit tiles + primary, no dock band, no `bolt`), 24 dp clear space; offline band `surface2`, tiles disabled; fit rule hides the hero first | `home/Home.kt`, `design/Game.kt` (`QuestionPlate`) | home-ko/de/ru |
| 03 vs Computer setup | Tiles, white sheet; computer avatar in the opponent row | `computer/ComputerSetup.kt` | — |
| 04 Quick match search | `?` plates question / sweep style, accent chase dots, found: avatar + VS + name, secondary 56 | `quickmatch/QuickMatch.kt` | search-ko, search-found-ko |
| 05 Match | Replace `DuelStrip` with `OpponentRow`; timer pill + 3 dp progress line under the bar; remove log header; latest row `surface2` radius 12, older rows 46 dp with bottom line; `HintChips` (S solid / B outlined / OUT wide / zero text3) replace `HintColumns` + lamps; opponent hint plain text with "상대" on the latest row; **move the memo into `MemoSheet`** (ModalBottomSheet; reuse `Memo.kt` logic and the cell drawing of `MemoBoard.kt`); entry head (내 숫자 line + 메모 button); keypad ruled-out = one diagonal stroke (was a corner ×); remove `MiniPlates` and the memo auto-collapse logic; waiting chase dots; legend box in secret entry | `match/MatchScreen.kt`, `match/PitchLog.kt`, `match/MemoBoard.kt` → `MemoSheet`, `match/Memo.kt` (unchanged logic), `match/OpponentLog.kt`, `design/Game.kt` (`DigitSlots`, `DigitKeypad`, `DigitKey`, `HintColumns`, `OutTag`, `PitchClock`, `ClockLine`, `MiniPlates`, `crossBars`) | match-* (13 shots incl. new `match-memo-ko`) |
| 06 Results | Remove wash and hero lamps; badge; answer plates `ink` 60 × 68; my number line; `ResultSummary` (걸린 회, 시간, 내 기록 / 난이도); "회별 기록 보기" → innings sheet (the old line-score table moves into it, zeros `text3`); **remove the top-bar 광고 제거 text button**; add the 광고 제거 caption line under the actions with its 24 dp clear space; team final: badge instead of lamps, 광고 제거 line | `results/Results.kt`, `team/TeamBoard.kt` (final state), `navigation/NbApp.kt` (if the link wiring lives there) | result-* (8 incl. new `result-innings-ko`), team-final-ko |
| 07 Lobby, create, join | Seats `surface2` without stripes / outlines; one empty-seat border; rules row without fill; fill dots `ink`; chat bar `bg` with `accent` badge; code plates radius 12; join errors `danger` | `lobby/RoomLobby.kt`, `lobby/CreateRoom.kt`, `lobby/JoinRoom.kt` | lobby-ko/en/de/ru |
| 08 Team board | No stripes; my row `surface2` radius 12; best hints as text (OUT as text); 승 tag white on `strike`; live dot `accent` | `team/TeamBoard.kt` | team-board-ko, team-final-ko |
| 09 Chat | White sheet; others `surface2`, mine `accentTint`; masked `text2` in both; pill chips `surface2`; send `accent`; held ring `accent` | `chat/Chat.kt` | chat-* |
| 10 Profile | Avatar, Home-style record row, form row removed, zero `text3` | `profile/Profile.kt` | — |
| 11 Settings | Scrolled bar = line only; switch / slider colours; licences entry Inter; transfer plates radius 6; destructive `danger` | `settings/Settings.kt` | settings-* (10) |
| 12 Player card, report, block | White sheets, `danger`, `accent` radios; check circle `surface2`; record values Inter `ink` | `report/Report.kt` | report-* (5), chat-actions-ko |
| 13 Terms gate, 15 Legal reader | Accent links without underline; reader body `ink`; scrolled bar line only | `terms/TermsGate.kt`, `terms/TermsGateRoute.kt`, `legal/LegalText.kt` | terms-ko/en, legal-ko |
| 14 Errors, window too small | Banner style; offline band `surface2`; too-small notice with the new mark | `design/Surfaces.kt`, `navigation/NbApp.kt`, `common/ErrorMessages.kt` | match-reconnect-ko |
| ADAPTIVE | Compact match (top bar `edit_note` opens the memo sheet full height), compact Home and result rules, large-text opponent hint under the guess, large memo sheet opened by 메모 | `match/MatchScreen.kt`, `home/Home.kt`, `results/Results.kt` | match-small-ko/de |

## 4. Watch-outs

1. **`chalk` / `onChalk` / `out` are split tokens.** A blind find-and-replace produces blue text or black buttons. Replace `NbColors.chalk` by role: text/icons → `ink`; fills of primary actions and selection indicators → `accent`, except selected segments, answer plates, the "나" tag and memo "in" plates → `ink`. `out` → `danger` everywhere except the OUT chip.
2. **Team B badge letter flips** from dark-on-chalk to white-on-ink.
3. **Zero counts** must use `text3`, not `textDisabled` (AA). Masked chat text is `text2` in both bubbles.
4. **광고 제거 line and the banner:** the line is tappable, so its 48 dp target must end ≥ 24 dp above the band (AdMob accidental clicks). Don't let the line sit flush on the banner; when the line is hidden or owned keep the 24 dp.
5. **Material defaults add shadows and tonal tint:** set `tonalElevation = 0.dp`, `shadowElevation = 0.dp` on `ModalBottomSheet`, `AlertDialog`, `Surface`, `DropdownMenu`; the overflow menu needs `containerColor = surface3` and no shadow, separated by a 1 dp `line` border instead.
6. **Inter tabular figures:** every `num` style keeps `fontFeatureSettings = "tnum"`; Inter's defaults are proportional, so a missing `tnum` shows as jittering timers and misaligned log digits.
7. **Status / navigation bar icons** must switch to dark (`WindowInsetsControllerCompat.isAppearanceLightStatusBars = true`), otherwise the clock disappears on white.
8. **Memo sheet + reveals:** the reveal and its sounds must keep playing under an open memo sheet; the keypad underneath updates its marks live. Don't dismiss the sheet on `round_result`.
9. **The hooks didn't change** (`lamp(strike, i)` etc. keep their names and timing): wire the chip count steps to them so sound and haptics stay in sync.
10. **Icons:** the launcher icon and store art are unchanged (dark plate); only the in-app mark changes. Remove `bolt`; add `sports_baseball`, `open_in_full`, `edit_note` (if not already imported).
11. Delete `res/font/barlow_condensed_*.ttf` only after every `num` style uses Inter; update the licences screen at the same time.
12. Roborazzi baselines: re-record everything; the old dark renders are obsolete.

## 5. Checklist

- [ ] `Tokens.kt` matches `tokens.json` 2.0.0 (colours, type, sizes, radius); `shadow` and glow tokens removed
- [ ] `Theme.kt` uses `lightColorScheme`; dark system-bar icons; `forceDarkAllowed=false`
- [ ] Inter TTFs in `res/font`, `NbFonts.num` uses them, all `num` styles `tnum`; Barlow removed; licences screen lists Inter (OFL 1.1)
- [ ] All `NbColors.chalk` / `onChalk` / `out` uses reviewed by role (watch-out 1)
- [ ] No `Modifier.shadow`, no tonal elevation anywhere
- [ ] Buttons, segmented tiles, switch, slider, radio, field, rows, sheets, dialogs, toasts restyled (DS 12)
- [ ] Hint chips: S solid, B outlined, OUT wide, zero `text3`; chips count up on the `lamp(...)` hooks
- [ ] Match: opponent row, timer pill + line, no log header, latest row highlight, opponent hint text, entry head, 메모 → memo sheet, ruled-out stroke on keys
- [ ] Home: Me row, 4 numbers, hero + link, 3 tiles, quick-match block, fit rule
- [ ] Result: badge, plates, my number, summary, innings sheet, 광고 제거 line with 24 dp clear space; top-bar link removed; team final
- [ ] Lobby / team board: no stripes, one empty-seat border, dots, text best hints, 승 tag
- [ ] Chat, settings, report, terms, legal, search, age, nickname, tutorial restyled
- [ ] Compact (360 × 320) and 200 % layouts per ADAPTIVE v2
- [ ] Every text pair ≥ 4.5 : 1 (spot-check with the values in DS 2.2)
- [ ] Roborazzi re-recorded and compared with all 60 `design/shots/` (± 1 dp, exact token hex)

## 6. Open questions for the owner

- Launcher icon: keep the dark number plate (current) or make a light variant to match the app? (Design recommends keeping it for v1.0 store stability.)
- Dark theme timing (DS 1.3): after release, as the system dark-mode option.
