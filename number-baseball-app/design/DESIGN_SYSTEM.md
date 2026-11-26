# Night Game: design system

Version 1.0 (phase 1). Owner: design lead. Source of truth for values: [`tokens.json`](tokens.json). This document gives the rules; the prototype in [`prototype/`](prototype/) shows them and is built only from `tokens.css`, which is generated from `tokens.json`. Screen specs are in [`screens/`](screens/), the review history in [`REVIEW_LOG.md`](REVIEW_LOG.md).

Units: dp for size, sp for type. In the prototype 1 px = 1 dp = 1 sp at font scale 1.0. Every number in this file is meant to be measured on a screenshot; "about" is never used for a spec value.

---

## 1. Direction

**The board at a night game.** The app is the outfield scoreboard after sunset: a matte, near-black panel with green undertones, crisp chalk-white numerals, and the only colour on screen comes from the **count lamps**: amber for a strike, green for a ball, red for an out (the real Korean and Japanese ballpark convention). A pitch-by-pitch log reads like a broadcast pitch tracker. Nothing glows except lamps. Nothing moves except what just changed.

Five rules that decide every argument:

1. **Chalk on slate, lamps for meaning.** UI is monochrome (chalk on slate). Amber, green and red appear only where they mean S, B or OUT, or the matching status (warning, success, error), and always with a letter or a word. Team A blue is the single exception and always carries the letter A.
2. **Numbers are the hero.** Every digit in the app (slots, keys, log, timer, scores, codes, tags) is set in the bundled display face with the same plate geometry. Words are set in the system face.
3. **Bands, not cards.** Screens are full-bleed bands separated by 1 dp hairlines and spacing. Only things that float (sheets, dialogs, toasts) are raised. A boxed card is a defect.
4. **One hand, bottom-heavy.** The primary action of every screen sits in the bottom 40 % of the window. The top is for reading.
5. **Quiet by default.** Effects follow the phone's silent mode, music is off, ads never near play and always muted, short motion, no confetti, no mascots, no exclamation-mark copy.

### Why dark only

The app commits to one dark theme and ignores the system light setting:

- The concept is a scoreboard at night. A light theme would turn the lamps into flat dots and remove the only source of colour.
- The core use case is a short break: on a commute, in bed, in a split-screen next to work. A dark, low-glare window is polite in all of them.
- One theme means one contrast audit and half the QA screenshots. All ratios below were computed, not estimated.
- OLED power saving is a minor bonus.

`android:forceDarkAllowed="false"`; status and navigation bars are transparent (edge-to-edge) with light icons; the window background is `bg`.

### Hard no's (from the brief, enforced in review)

No Excel or office motifs (no cell grids with ruled lines, no column letters), no mascots, no rounded or comic fonts, no rainbow or purple-blue gradients, no emoji in UI, no glossy skeuomorphism, no everything-in-cards. The memo board is a dot matrix, not a table, for exactly this reason.

---

## 2. Colour

All values are `#AARRGGBB` in `tokens.json` (Kotlin `Color(0xAARRGGBB)`). Ratios are WCAG 2.x, computed by script (`contrast_checked` in tokens.json).

### 2.1 Tokens

| Token | Hex | Use |
| --- | --- | --- |
| `bg` | `#0C1210` | Window background, behind system bars |
| `surface1` | `#131A17` | Bands on bg: duel strip, memo board, dock, latest log row, scrolled top bar, ad band |
| `surface2` | `#1A231F` | Plates: keys, filled slots, text fields, segmented track, tonal buttons |
| `surface3` | `#222D28` | Raised: sheets, dialogs, toasts, connection banner; pressed `surface2` |
| `surface4` | `#2B3732` | Pressed on `surface3`; other players' chat bubbles |
| `line` | `#2A3530` | 1 dp hairlines (decorative; never the only boundary of a control) |
| `outline` | `#6C7C74` | 1 dp boundary of controls that need one (empty slot, outline button, switch off, radio) |
| `chalk` | `#ECEFE6` | Primary text, icons, primary button fill, selected states, focus ring |
| `chalkPressed` | `#D3D8CC` | Primary button pressed |
| `onChalk` | `#0C1210` | Text and icons on chalk |
| `text2` | `#A5B1AA` | Secondary text |
| `text3` | `#909D96` | Tertiary text (captions, round numbers, placeholders); only on `bg`..`surface3` |
| `textDisabled` | `#56625C` | Disabled text and icons; zero counts (`0S`) |
| `strike` | `#F5B83D` | S lamp and label; warning; win accent |
| `ball` | `#45C98B` | B lamp and label; success, "pitched" status |
| `out` | `#FF6359` | OUT lamp and label; error; destructive |
| `lampOff` / `lampOffRing` | `#2E3A35` / `#3B4943` | Unlit lamp disc and its 1 dp ring; memo "unknown" dot |
| `strikeGlow` `ballGlow` `outGlow` | 35 % of lamp colour | Radial glow behind lit lamps |
| `strikeTint` | 12 % amber | Wash from the window top on a won result (360 dp fade to transparent) |
| `outTint` | 12 % red | Behind an invalid slot |
| `teamA` / `teamATint` | `#6EB0FF` / 12 % | Team A marker (stripe, badge) |
| `teamB` / `teamBTint` | `#ECEFE6` / 8 % | Team B marker ("home whites") |
| `scrim` | 64 % black | Behind sheets and dialogs |
| `shadow` | 48 % black | Shadow of level-3 surfaces |
| `focus` | `#ECEFE6` | 2 dp focus ring, 2 dp outside the control |

### 2.2 Contrast (computed)

| Pair | Ratio | Requirement |
| --- | --- | --- |
| chalk on bg / on surface3 | 16.27 / 12.25 | 4.5 text |
| text2 on bg / on surface3 | 8.53 / 6.42 | 4.5 text |
| text3 on bg / surface1 / surface2 / surface3 | 6.71 / 6.27 / 5.71 / 5.05 | 4.5 text |
| outline on bg / surface1 / surface2 / surface3 | 4.30 / 4.02 / 3.66 / 3.24 | 3.0 UI boundary |
| strike on bg / surface3 | 10.64 / 8.01 | 4.5 text |
| ball on bg / surface3 | 8.99 / 6.77 | 4.5 text |
| out on bg / surface3 | 6.47 / 4.87 | 4.5 text |
| teamA on bg / surface3 | 8.40 / 6.32 | 3.0 UI |
| onChalk on chalk | 16.27 | 4.5 text |
| lit strike / ball / out vs lampOff | 6.66 / 5.63 / 4.05 | 3.0 UI |

| text2 on surface4 (masked `***` in others' chat bubbles) | 5.59 | 4.5 text |
| textDisabled on chalk (masked `***` in my own chat bubble) | 5.48 | 4.5 text |

`text3` must never sit on `surface4` (4.39). `textDisabled` is only for disabled controls, zero counts (both exempt) and the masked `***` inside my own chalk bubble, where it passes (5.48).

### 2.3 Colour-blind check

Simulated with Machado 2009 at full severity: under protanopia strike and ball reach **1.01:1** against each other, under deuteranopia 1.32:1. Colour can therefore never be the cue. Rules that make the hints work for everyone:

- Every hint is written as **count + letter** (`2S`, `1B`) or the word `OUT`, in fixed order S then B, in fixed columns.
- Lamp rows are always labelled by their position under the `S` or `B` text; lit vs unlit differ in luminance (≥ 4.05:1), not hue.
- OUT also has a shape: a 1 dp outlined tag.
- Teams carry the letters A and B in badges and sit on fixed sides (A left, B right).
- Win, loss and draw are written as words; the form row (home) uses three shapes: filled amber disc, unlit disc, chalk ring.

---

## 3. Typography

### 3.1 Families

| Role | Family | Weights | Licence | Notes |
| --- | --- | --- | --- | --- |
| `sans`: all words, every language | System default (`FontFamily.Default`): Roboto or the OEM Latin face; Noto Sans CJK or the OEM face for Hangul, kana and Han; the same system face covers Cyrillic and Vietnamese diacritics | **400 and 700 only** | system | Many devices ship CJK in Regular and Bold only, so 500/600 would render inconsistently between scripts. |
| `num`: digits and a few Latin letters | **Barlow Condensed**, bundled: `res/font/barlow_condensed_medium.ttf` (500), `barlow_condensed_semibold.ttf` (600), `barlow_condensed_bold.ttf` (700) | 500, 600, 700 | SIL OFL 1.1 ([licence](fonts/OFL-BarlowCondensed.txt)) | Latin subset (227 code points). **Only** for digits, the letters S B O A R, `OUT`, `VS`, `#` tags and room codes. Never for a translated string. |

Licence check for app embedding: OFL 1.1 explicitly allows bundling and embedding with software. The copyright line declares **no Reserved Font Name**, so our subset conversion (Fontsource Latin subset, WOFF2 to TTF) may keep the name; `OS/2 fsType = 0` (installable embedding, no restriction). The licence text must appear in the in-app Open-source licences screen together with Material Symbols (Apache 2.0).

Barlow's default figures are proportional. Every `num` style sets `fontFeatureSettings = "tnum"` (tokens: `"tnum": true`) so columns of numbers align. The reference shots use tabular digits too (regenerated 2026-11-25 by `prototype/shoot.js`; `proto.js` sets `font-variant-numeric: tabular-nums` on every element in the display face, because the CSS `font` shorthand resets it).

### 3.2 Scale

| Token | Family | Size / line (sp) | Weight | Tracking (em) | Use |
| --- | --- | --- | --- | --- | --- |
| `display` | sans | 40 / 48 | 700 | 0 | Result headline |
| `headline` | sans | 26 / 34 | 700 | 0 | First-run and gate titles |
| `title` | sans | 20 / 28 | 700 | 0 | Top bar, sheet and dialog titles, app name on Home |
| `subtitle` | sans | 16 / 22 | 700 | 0 | Row titles, player names, section titles |
| `body` | sans | 15 / 22 | 400 | 0 | Running text, chat |
| `bodyStrong` | sans | 15 / 22 | 700 | 0 | Emphasis |
| `label` | sans | 16 / 20 | 700 | 0 | Buttons |
| `labelSmall` | sans | 13 / 18 | 700 | 0 | Section labels, column headers, chips, small buttons |
| `caption` | sans | 13 / 18 | 400 | 0 | Helper and status text |
| `micro` | sans | 12 / 16 | 700 | 0 | Tags. Smallest text in the app |
| `numHero` | num | 56 / 60 | 600 | 0 | Reserved (large reveal on tablets) |
| `numScore` | num | 56 / 60 | 700 | 0 | Team score |
| `numSlot` | num | 40 / 44 | 600 | 0 | Slot digit |
| `numCode` | num | 36 / 40 | 600 | 0 | Room code plates |
| `numStat` | num | 32 / 36 | 600 | 0 | Home record |
| `numKey` | num | 30 / 36 | 500 | 0 | Keypad digit |
| `numSlotSm` | num | 28 / 32 | 600 | 0 | Compact slots, latest log guess |
| `numTimer` | num | 24 / 28 | 600 | 0 | Pitch clock, countdowns |
| `numLog` | num | 22 / 28 | 500 | 0.14 | Log guess digits |
| `numHint` | num | 20 / 24 | 600 | 0.02 | `2S` `1B` |
| `numMemo` | num | 18 / 22 | 600 | 0 | Memo digits |
| `numLabel` | num | 14 / 16 | 600 | 0.06 | Round numbers, S/B letters, VS, team letters |
| `numTag` | num | 15 / 20 | 500 | 0.02 | `#1234`, opponent hints, counters |

Rules:

- Letter spacing of `sans` is always 0 (CJK must not be tracked).
- Korean line breaking is by word (어절), never inside a word. Compose: `TextStyle(lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase))` on API 33+; copy is written so the fallback still breaks acceptably. Prototype: `word-break: keep-all`.
- A nickname is shown as name (`sans`) + `#1234` (`numTag`, `text3`) with no space. When space runs out the **name** ellipsizes, the tag never does.
- Line counts: titles max 2 lines, body unlimited, buttons 1 line (ellipsis only as a last resort, see 11.3).

### 3.3 Font scale

- `sans` text scales fully to 200 % (Android 14 non-linear scaling applies automatically).
- **Every `num` style is capped: rendered size = token size × min(fontScale, 1.3), in dp.** This holds for every element set in Barlow, in every screen, with no exceptions: slots and result plates, keypad and age keys, memo digits and cells, mini plates, room and transfer code plates, team badge letters, the `OUT` tag, hint counts, log guesses and round numbers, the opponent column, pitch clock and countdowns, scores, stats, segmented-control numbers, `#tag`s and counters. At 2.0 that is 1.3× (`numLog` 22 → 28.6 dp, `numTag` 15 → 19.5 dp, `numLabel` 14 → 18.2 dp). Reason: these numerals are already large, they sit in fixed grids, and the words around them (which carry the meaning for TalkBack and large-text users) scale fully.
- Compose: compute the size in dp so Android 14 non-linear scaling cannot change it: `val capped = with(LocalDensity.current) { (token.fontSize.value * min(fontScale, 1.3f)).dp.toSp() }` (same for `lineHeight`), wrapped in one helper (`NbType.capped(style)`) that every `num` style goes through. Letter spacing stays in em.
- Controls still use `heightIn(min = token)` / `widthIn(min = token)`, never a fixed `height`, so a capped numeral plus `sans` text next to it never clips.
- At `fontScale ≥ 1.5` screens switch to their large-text layout, described in [`screens/ADAPTIVE.md`](screens/ADAPTIVE.md) §2 (match, memo sheet, results, team board, lobby, settings). Between 1.0 and 1.5 the normal layout must hold with `sans` text grown and numerals capped.

---

## 4. Spacing and layout

- **4 dp grid.** Spacing tokens: 0, 2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64.
- Screen margin 16 dp; 12 dp in compact windows (height < 480 dp). Content max width 560 dp, centred, on wider windows.
- Icon buttons are 48 × 48 dp and sit 4 dp from the screen edge so the 24 dp glyph aligns with the 16 dp margin.
- Top bar 56 dp (48 dp compact). Duel strip 52 dp. List rows 64 dp with subtitle, 56 dp single line; row text starts at x = 56 dp (16 + 24 icon + 16); dividers are inset to x = 56 dp (72 dp on Home rows that use a 40 dp lead plate).
- Section header: 40 to 48 dp tall, label baseline 8 dp above the next row.
- Bottom action zone: primary button 56 dp; 16 dp side margins; 24 dp below the last button when an ad band follows (see 13).
- Window classes (Compose `WindowSizeClass`): width compact < 600, medium 600 to 839, expanded ≥ 840 dp; **height compact < 480 dp** triggers the compact game layout.

### 4.1 Supported window sizes (QA DEF-019 / DEF-020)

| Number | Value | Rule |
| --- | --- | --- |
| **Smallest supported window** | **320 × 320 dp** (width × height) | Every screen is fully usable down to this size (compact layouts, ADAPTIVE.md). Below it the app keeps the game state but shows a full-window notice instead of the screen: brand mark + "창을 조금 더 크게 해 주세요" (`title`) + "게임은 그대로 기다리고 있어요" (`caption` `text3`); online timers keep running, so the reconnect rules still apply. |
| **Ad banner hidden below** | window height **< 480 dp** (or width < 320 dp) | No banner is requested or shown, on any screen. Same threshold as the compact game layout. |
| Compact game layout | window height < 480 dp | ADAPTIVE.md §1 |
| Large-text layout | fontScale ≥ 1.5 | ADAPTIVE.md §2 |
| Two-pane match | window width ≥ 600 dp and height ≥ 480 dp | ADAPTIVE.md §3 |

`android:resizeableActivity="true"`; no `maxAspectRatio`; minimum window size in the manifest `<layout android:minWidth="320dp" android:minHeight="320dp">` (freeform and pop-up windows cannot shrink below it on platforms that honour it).

---

## 5. Shape

| Token | dp | Use |
| --- | --- | --- |
| `none` | 0 | Bands, full-bleed rows |
| `s` | 4 | Tags, chips, team badges, memo plates, mini plates, segment items |
| `m` | 8 | Keys, slots, buttons, text fields, seats, toasts |
| `l` | 16 | Sheet top corners, dialogs |
| `full` | 999 | Lamps, switch, icon-button ripple |

Nothing in the app uses a radius above 16 dp. Pills are not used for buttons.

---

## 6. Elevation

Dark UI shows depth by surface lightness, not shadow.

| Level | Surface | Shadow | Compose |
| --- | --- | --- | --- |
| 0 | `bg` | none | — |
| 1 | `surface1` | none | — |
| 2 | `surface2` | none | — |
| 3 | `surface3` | y 8, blur 24, `shadow` (prototype) | `Modifier.shadow(elevation = 8.dp, shape, ambientColor = Color(0x7A000000), spotColor = Color(0x7A000000))`; colours apply on API 28+, below that the default shadow is fine |

Sheets and dialogs always come with the 64 % `scrim`, which carries most of the separation; QA should judge the edge against the scrim, not the shadow.

---

## 7. Iconography

- **Material Symbols Outlined**, weight 400, grade 0, optical size 24, fill 0 (Apache 2.0). Import only the glyphs listed below as vector drawables (`res/drawable/ic_*.xml`). Do not use the old Material Icons set: its geometry differs.
- Sizes: 24 dp default; 20 dp inside buttons and the room-rules band; 16 dp inline with caption text; 18 dp in the pitch clock.
- Colour: `chalk` for actions, `text2` for leading icons in rows and secondary top-bar actions, `text3` for chevrons and inline info icons, `out` only for destructive rows.
- No filled or coloured icons, no emoji, no illustrations.

| Glyph | Where |
| --- | --- |
| `close` | Leave match, close sheets, dismiss result |
| `arrow_back` | Back in lobby, settings, team board |
| `more_vert` | Match and lobby overflow |
| `settings`, `help` | Home top bar |
| `memory` | vs Computer, practice |
| `group`, `login`, `bolt` | Create room, join with code, quick match |
| `timer` | Pitch clock, rate limit, line-score time |
| `backspace`, `shuffle` | Keypad delete, random secret |
| `edit_note` | Memo toggle in the compact layout |
| `keyboard_arrow_down` / `_up` | Collapse or expand the memo board |
| `chat`, `send` | Chat and quick phrases, send |
| `flag`, `block` | Report, block |
| `content_copy`, `share`, `tune`, `swap_horiz` | Room code copy and share, room rules, move seat |
| `visibility` | Spectating note |
| `language`, `volume_off`, `mobile_vibrate`, `notifications_off`, `person`, `key`, `delete`, `shield`, `policy`, `description`, `info`, `send`, `content_copy`, `volume_up`, `music_note`, `do_not_disturb_on` | Settings rows |
| `check`, `cloud_off`, `schedule` | Report sent, offline band, maintenance |

---

## 8. Motion

### 8.1 Tokens

| Token | ms | | Easing | cubic-bezier |
| --- | --- | --- | --- | --- |
| `tap` | 90 | | `standard` | 0.2, 0, 0, 1 |
| `short` | 150 | | `decelerate` | 0, 0, 0, 1 |
| `medium` | 240 | | `accelerate` | 0.3, 0, 1, 1 |
| `long` | 360 | | `emphasized` | 0.05, 0.7, 0.1, 1 |
| `lamp_ignite` | 180 | | `ignite` | 0.2, 0, 0, 1 |
| `lamp_stagger` | 110 | | `linear` | 0, 0, 1, 1 |
| `slot_fill` | 120 | | | |
| `shake` | 280 | | spring `slot_pop` | damping 0.6, stiffness 800 |
| `result_hero` | 640 | | spring `sheet` | damping 1.0, stiffness 500 |
| `match_found` | 900 | | | |
| `toast_visible` | 2800 | | | |
| `waiting_chase_cycle` | 1200 | | | |
| `reveal_lead_in` | 240 | | | |
| `solve_flash` / `result_delay` | 300 / 400 | | | |
| `result_digit_stagger` / `result_digits_after_headline` | 60 / 320 | | | |
| `result_button_min` / `settle_guard` | 1000 / 300 | | | |
| `timer_pulse` / `phrase_bubble` | 200 / 2800 | | | |
| `music_crossfade` / `chat_sound_min_gap` | 600 / 2000 | | | |

Defaults: enter = `medium` + `decelerate`; exit = `short` + `accelerate`; state change in place = `short` + `standard`. Screen transitions: shared axis X 24 dp + fade, `long`. Nothing loops except the waiting chase and the search sweep.

### 8.2 Signature moments

1. **The Count (hint reveal).** Exact per-step timing is in 8.3 B, which wins over this summary. When both pitches are in, the previous latest row shrinks from 64 to 44 dp (`medium`, `standard`) while the new row slides down 12 dp and fades in (`medium`, `decelerate`). Its guess digits are already chalk. Then the lamps light one by one: S lamps first, then B lamps, each `lamp_ignite` 180 ms (opacity 0 to 1 and glow radius 0 to 1.25 × size, `ignite`), started every `lamp_stagger` 110 ms. The `2S` / `1B` text fades in with the first lamp of its row. OUT: the single red lamp ignites and the `OUT` tag fades in with it. One haptic per lamp (section 9). The keypad unlocks when the last lamp starts, not when it ends.
2. **The Final Strike (win).** Exact timing in 8.3 C. The winning row lights its n strike lamps as above, then the screen cross-fades to the result (`long`). On the result, the hero lamps light left to right (110 ms stagger), then the `display` headline fades up 8 dp (`result_hero` 640 ms, `emphasized`), then the opponent's slots flip to amber one by one (each `slot_fill` 120 ms, 60 ms stagger). Loss: lamps stay unlit, the headline fades in, the opponent's digits fade from `textDisabled` to chalk left to right. Draw: two lamps rows are not used; the headline appears with a single chalk lamp blink. No confetti, no scaling text, no shake.
3. **Match found.** The four `?` plates of the search screen flip to the opponent's name area: the chase lamps stop, three chalk lamps light together, the `VS` and nickname fade in (`match_found` 900 ms total), then the secret-entry screen slides in.
4. **Waiting chase.** While waiting for the opponent, three 6 dp lamps light in sequence (one lit at a time, 400 ms each, `waiting_chase_cycle` 1200 ms), chalk only.

Smaller rules: slot fill = digit scales 0.92 to 1 with `slot_pop`; invalid entry = slot row shakes ±6 dp, 280 ms, 3 cycles; sheets use `sheet` spring; toasts enter from 16 dp below.

### 8.3 Sync timeline (for sound and haptics)

Every step below is an **event hook** the app fires once; the sound (assets in `design/sound/`, owned by the sound designer) and the haptic (section 9) both start on that hook, at the same frame as the visual change. Times are from t = 0 of each sequence. n = digit count.

**A. Keypad and pitch**

| t (ms) | Hook | Visual |
| --- | --- | --- |
| 0 | `key_down(digit)` | key `surface3`; digit enters the active slot, scale 0.92 → 1 (`slot_pop` spring, settles in 120 ms) |
| 0 | `key_rejected` | slot row shakes ±6 dp, 3 cycles over 280 ms |
| 0 | `backspace` | last digit fades out 90 ms |
| 0 | `pitch_submit` | slots lock (`surface1`/`text2`, 150 ms); keypad locks; Pitch becomes the waiting button |
| every 400 | `waiting_tick` (visual only, no sound) | chase lamp moves |

**B. Hint reveal (The Count).** t = 0 is the frame the server result is applied (both pitches in).

| t (ms) | Hook | Visual |
| --- | --- | --- |
| 0 | `round_result` | old latest row shrinks 64 → 44 dp (240 ms, `standard`); new row slides 12 dp down + fades in (240 ms, `decelerate`) |
| 240 + 110 × i, i = 0..S−1 | `lamp(strike, i)` | strike lamp i ignites (180 ms, `ignite`); with i = 0 the `nS` text fades in (150 ms) |
| 240 + 110 × (S + j), j = 0..B−1 | `lamp(ball, j)` | ball lamp j ignites; with j = 0 the `nB` text fades in |
| 240 | `lamp(out)` (only when 0S 0B) | the red lamp ignites and the `OUT` tag fades in (180 ms) |
| 240 + 110 × max(S + B − 1, 0) | `reveal_last` | keypad unlocks (if no one solved) |
| last lamp + 180 | `reveal_end` | sequence complete; TalkBack live region speaks the row |

Examples: 2S 1B → lamps at 240, 350 (S), 460 (B); keypad unlocks at 460; end 640. OUT → lamp at 240, end 420. 0S 0B never plays strike or ball hooks. When S = n (solved) the sequence continues with C.

**C. Solve, win, lose, draw.** t = 0 is `reveal_end` of the solving round.

| t (ms) | Hook | Visual |
| --- | --- | --- |
| 0 | `solve` (I solved) / `opponent_solved` | latest row background flashes `strikeTint` 0 → 1 → 0 over 300 ms (mine only) |
| 400 | `screen_to_result` | cross-fade to the result screen (360 ms, `standard`) |
| 760 + 110 × i, i = 0..n−1 | `result_lamp(i)` (win only) | hero strike lamp i ignites (180 ms) |
| win: 760 + 110 × n; lose/draw: 760 | `result_headline(outcome)` | headline fades up 8 dp (640 ms, `emphasized`) |
| headline + 320 + 60 × i | `reveal_digit(i)` | opponent slot i: win → flips to amber (120 ms); lose/draw → digit fades `textDisabled` → chalk (120 ms) |
| headline + 640 | `result_ready` | buttons accept input (and never earlier than 1000 ms after `screen_to_result`) |

Example 4-digit win: lamps 760, 870, 980, 1090; headline 1200; digits 1520, 1580, 1640, 1700; buttons 1840. Draw: one chalk lamp blinks at 760 (180 ms on, 180 ms off) instead of the lamp row. Team result uses the same timeline with one lamp per duel won.

**D. Pitch clock**

| Moment | Hook | Visual |
| --- | --- | --- |
| remaining = 10.0 s | `timer_warn` | value, icon and line turn `strike` (150 ms cross-fade) |
| remaining = 5, 4, 3, 2, 1 s | `timer_tick(s)` | value and line turn `out` at 5 s; value pulses scale 1.0 → 1.06 → 1.0 (200 ms) on each tick |
| remaining = 0 | `timer_expired` | slots shake once; toast "시간이 지났어요. 이번 회는 넘어가요" |
Clock text updates on whole seconds; the line moves continuously (linear).

**E. Other hooks**

| Hook | When | Visual |
| --- | --- | --- |
| `match_found` | quick match pairs | plates flip, three chalk lamps light together at t = 0, name fades in 0–300 ms, screen changes at 900 ms |
| `chat_message` | a message arrives while the chat sheet is closed (rooms) | unread badge pops (scale 0.6 → 1, 150 ms); at most one sound per 2 s |
| `phrase_received` | quick phrase arrives in quick match | bubble under the duel strip fades in 150 ms, stays 2800 ms, fades out 150 ms |
| `nav_forward` / `nav_back` | screen push / pop | shared axis X 24 dp + fade, 360 ms |
| `sheet_open` / `sheet_close` | any bottom sheet | `sheet` spring |
| `toggle(on/off)` | any switch | thumb slides 150 ms |
| `opponent_pitched` | the opponent's pitch arrives (duel strip status → 던짐) | status lamp ignites (180 ms) |
| `memo_cycle` | a memo cell or digit changes state | mark cross-fades 90 ms |
| `secret_locked` | 이 숫자로 정하기 accepted | slots lock (150 ms) |
| `error_shown` | an error dialog or error toast appears | dialog / toast enter |

### 8.4 Reduced motion

If `Settings.Global.ANIMATOR_DURATION_SCALE == 0` or the user turns on "Remove animations": every transition becomes an instant state change, lamps appear lit at once, the chase shows one static lit lamp plus the text. Hooks still fire in the same order but compressed: all lamp hooks of a row fire 60 ms apart (so sounds stay distinguishable), `result_headline` fires at t = 0 of the result screen, and buttons accept input after 1000 ms.

---

## 9. Haptics

On by default (setting "진동"); also respects the system "Touch feedback" setting. Haptics are independent of sound: they still play in silent mode, and only the 진동 switch turns them off. All through `View.performHapticFeedback` or `VibrationEffect.Composition`; fall back as listed when a primitive is unsupported (`Vibrator.arePrimitivesSupported`). Each row fires on the hook named in 8.3.

| Event (hook) | Effect | Fallback |
| --- | --- | --- |
| Digit key, backspace | `KEYBOARD_TAP` | — |
| Key rejected (duplicate digit, pitch with empty slot) | `REJECT` (API 30+) | `LONG_PRESS` |
| Pitch submitted / secret locked | `CONFIRM` (API 30+) | `VIRTUAL_KEY` |
| Strike lamp lights | `PRIMITIVE_CLICK` scale 0.7 | `CLOCK_TICK` |
| Ball lamp lights | `PRIMITIVE_TICK` scale 0.5 | `CLOCK_TICK` |
| OUT lamp lights | `PRIMITIVE_THUD` scale 0.6 | `LONG_PRESS` |
| I solved (`solve`) | `CLICK` 1.0 | `CONFIRM` |
| Win (`result_headline(win)`) | `CLICK` 1.0, +70 ms `CLICK` 1.0, +70 ms `QUICK_RISE` 0.8 | `CONFIRM` |
| Loss | `THUD` 0.4 | `LONG_PRESS` |
| Draw | `TICK` 0.6, +90 ms `TICK` 0.6 | `CLOCK_TICK` |
| Pitch clock reaches 10 s | `TICK` 0.5 | `CLOCK_TICK` |
| Each of the last 5 s | `TICK` 0.35 | `CLOCK_TICK` |
| Match found | `CLICK` 0.8, +120 ms `CLICK` 1.0 | `CONFIRM` |
| Memo cell or memo digit cycled | `CLOCK_TICK` | — |
| Chat message, quick phrase received | none (sound only, rate-limited) | — |
| Navigation, sheets | none | — |
| Long press (message, player) | `LONG_PRESS` | — |
| Switch toggled | `TOGGLE_ON` / `TOGGLE_OFF` (API 34+) | `CLOCK_TICK` |

Nothing vibrates when the opponent pitches (it would be noise every round) and nothing vibrates while the app is in the background.

---

## 10. Sound

Owner request (README "Sound"): effects and music, made in-house. Assets, mixing and loudness belong to the sound designer in `design/sound/`; this section fixes the behaviour and the sync points.

**Defaults and settings** (Settings > 소리와 진동, see [`screens/11-settings.md`](screens/11-settings.md)):

| Setting | Default | Behaviour |
| --- | --- | --- |
| 효과음 (effects) | on | Plays all effect cues |
| 효과음 볼륨 | 70 / 100 | App-internal gain applied on top of the media volume; slider disabled when effects are off |
| 무음 모드 따르기 (follow silent mode) | on | When the ringer mode is silent or vibrate, effects and music are muted; haptics still play |
| 배경 음악 (music) | off | One calm menu loop (Home, lobby, results, settings) and one low-key match loop (match, secret entry, search) |
| 음악 볼륨 | 40 / 100 | Slider disabled while music is off |
| 진동 (haptics) | on | Section 9 |

**Rules**

- Effects use `AudioAttributes.USAGE_GAME`, music `USAGE_GAME` + `CONTENT_TYPE_MUSIC`; both follow the media volume.
- Audio focus: music requests `AUDIOFOCUS_GAIN`; on loss (other app, call) music pauses and effects stop; on `onStop` (app in background) everything stops; music resumes only when the app is visible again and focus is regained.
- Music crossfades 600 ms between the menu and match loops on `nav_forward` into or out of a match; it ducks −6 dB during the hint reveal and the result sequence (`round_result` to `reveal_end`, `screen_to_result` to `result_ready`).
- Effects never stack: a new cue of the same kind cuts the previous one; lamp cues within one reveal may overlap by design (110 ms apart).
- `chat_message` plays at most once every 2 s and never during the hint reveal; muted while "전체 채팅 끄기" is on.
- Ads are **always muted** (README), regardless of game sound.
- No information is sound-only; everything audible has a visual and, where listed, a haptic.

**Hook → sound file** (files from the sound designer in `design/sound/out/`; the hook defines *when*, the file *what*):

| Hook (8.3) | File | Notes |
| --- | --- | --- |
| `key_down` | `sfx_keypad_1` / `_2` / `_3` | round-robin |
| `backspace` | `sfx_keypad_delete` | |
| `key_rejected` | `sfx_input_invalid` | with the 280 ms shake |
| `pitch_submit` | `sfx_guess_sent` | |
| `secret_locked` | `sfx_secret_locked` | |
| `opponent_pitched` | `sfx_opponent_guessed` | sound only, no haptic; never during a reveal |
| `lamp(strike, i)` / `lamp(ball, j)` / `lamp(out)` | `sfx_hint_strike` / `sfx_hint_ball` / `sfx_hint_out` | playback rate by lamp index, 110 ms apart |
| `solve` | `sfx_solved` | `opponent_solved` plays nothing (the result sting follows) |
| `result_headline(win / lose / draw)` | `sfx_win` / `sfx_lose` / `sfx_draw` | `result_lamp(i)` plays nothing (the sting covers it) |
| `timer_warn` (once at 10 s) | `sfx_timer_warning` | the sound designer's demo ticks every second from 10 s; the app plays it **once** at 10 s and is silent at 9–6 s |
| `timer_tick(5..1)` | `sfx_timer_warning_last` | |
| `timer_expired` | `sfx_time_out` | |
| `match_found` | `sfx_match_found` | |
| `chat_message` / `phrase_received` | `sfx_chat_message` / `sfx_quick_phrase` | max one per 2 s; muted with chat |
| `memo_cycle` | `sfx_memo_mark` | |
| `toggle(on / off)` | `sfx_toggle_on` / `sfx_toggle_off` | |
| `nav_forward` / `nav_back` | `sfx_ui_navigate` / `sfx_ui_back` | not on transitions into or out of a match (the music crossfade covers those) |
| `error_shown` | `sfx_error` | |
| music | `music_menu` (Home, lobby, results, settings, profile) / `music_match` (search, secret entry, match) | 600 ms crossfade |


## 11. Accessibility

### 11.1 Targets

- Minimum touch target 48 × 48 dp everywhere, with these spaced exceptions:
  - **Memo grid cells**: visual cell = (content width − 24) ÷ 10 wide (30.4 dp at 360 dp) × 28 dp; digit header cells 30.4 × 32 dp. This meets WCAG 2.2 target size (minimum, 24 × 24) but not 48 dp. Alternatives that are fully accessible: (a) TalkBack users get each cell as a single focusable item with a spoken state and the "double-tap to change" action; (b) at `fontScale ≥ 1.5` or when "Larger memo" (Settings, phase 2) is on, the memo opens as a bottom sheet with a transposed grid (rows = digits, columns = positions, cells 48 × 48 dp), specified in [`screens/ADAPTIVE.md`](screens/ADAPTIVE.md) §2.3.
  - Keypad keys in the compact window are 48 dp tall but 50.4 dp wide minimum; fine.
- Adjacent targets never overlap; touch areas expand into padding, not into neighbours.

### 11.2 TalkBack

Every control has a content description from string resources. Key patterns (ko / en):

| Element | Spoken text |
| --- | --- |
| Slot | "자리 1, 5" / "Position 1, 5"; empty: "자리 3, 비어 있음, 다음 입력 위치" |
| Digit key | "3"; with memo: "3, 제외로 표시됨" / "3, marked out"; "3, 확정" / "3, marked in"; used: "3, 이미 입력함, 사용할 수 없음" |
| Backspace / random | "한 칸 지우기" / "Delete one digit"; "무작위 숫자" / "Random number" |
| Log row | "6회, 내 추측 3 9 1 2, 3스트라이크 0볼. 상대 판정 3스트라이크 0볼." / "Round 6, my guess 3 9 1 2, 3 strikes 0 balls. Opponent's hint 3 strikes 0 balls." |
| OUT | "아웃, 맞는 숫자 없음" / "Out, no digit matches" |
| Memo cell | "자리 2, 숫자 9, 확정. 두 번 탭하면 바꿉니다." / "Position 2, digit 9, marked in. Double-tap to change." |
| Pitch clock | "남은 시간 42초" / "42 seconds left" (not re-announced every second) |
| Lamps | Decorative (`clearAndSetSemantics {}`); the text carries the meaning |
| Opponent status | "Kestrel 2048, 던짐" / "Kestrel 2048, pitched" |

Live regions: round result (polite, the log row text), pitch clock at 10 s and 5 s (polite), match found (assertive), connection lost and restored (assertive), toast text (polite). Focus order follows reading order top to bottom; after a pitch, focus stays on the keypad; after a result arrives, TalkBack reads it without moving focus.

### 11.3 Text and colour

- Hints are never colour alone (section 2.3). Error states always have text, not only a red outline.
- All text meets 4.5:1, all control boundaries 3:1 (section 2.2).
- At 200 % font scale no text is clipped: titles wrap to 2 lines, buttons grow in height (min 56 dp, padding 16 dp vertical) and wrap to 2 lines rather than ellipsize; the keypad numerals cap at 1.3×.
- **Shrink to fit (added at final sign-off, 2026-11-26).** A UI label (never player content such as names or chat) that would clip, ellipsize or break inside a word may step its size down before it wraps or ellipsizes, in 5 % steps, to a floor of **the larger of** (a) its per-component minimum scale (70 % default; 50–55 % for segment items, column headers, button labels and team badges), (b) **11 dp rendered**, and (c) at font scale ≥ 1.5, **the token's size at 100 %** (a large-text user never sees a label smaller than a default-size user). Below the floor the normal fallbacks apply: wrap by word / 어절, then ellipsis with the full text in TalkBack. `micro` (12 sp) stays the smallest token; shrinking is a fitting step, not a style.
- Keyboard and switch access: a 2 dp chalk focus ring, 2 dp outside every focusable element; Enter on a key types it; Enter on Pitch pitches. Hardware number keys type digits on the match screen.

---

## 12. Components

Each component lists anatomy, size and every state. "Prototype" points to a page that shows it. Compose names are suggestions.

### 12.1 Top bar (`TopBar`)
- 56 dp (48 compact), transparent on `bg`; scrolled: `surface1` with a 1 dp `line` bottom edge, and the status-bar area behind it takes `surface1` too.
- Leading icon button 48 dp at x = 4; title `title` starting at x = 64 (12 dp after the icon button); trailing icon buttons 48 dp, last one at 4 dp from the edge.
- Match variant: two-line title (`subtitle` round, `caption` mode · digits), then pitch clock, then overflow.

### 12.2 Pitch clock (`PitchClock`)
- `timer` icon 18 dp `text3` + `numTimer` value, right-aligned, min width 38 dp, format `m:ss`.
- 2 dp progress line at the bottom edge of the top bar, full width; track `line`, fill `chalk`, shrinks right to left linearly.
- Round timer options (owner decision): **30 s, 60 s, 120 s, or 느긋하게 / Relaxed = 10 min**; there is no untimed option. Quick match always uses 60 s. Relaxed shows `10:00` counting down like the others.
- States: normal (chalk); **warn** ≤ 10 s (value, icon and fill `strike`); **critical** ≤ 5 s (`out`); **paused** (reconnecting, `textDisabled`).

### 12.3 Duel strip (`DuelStrip`)
- 52 dp band, 16 dp margins, 12 dp gaps: `VS` (`numLabel`, `text3`, 20 dp wide) · opponent name + tag (`subtitle` / `numTag`) over status (`caption` `text2` with a 6 dp lamp) · quick-phrase button (online only, 48 dp, `chat` icon `text2`) · my secret ("내 숫자" `caption` `text3` over mini plates 18 × 22 dp, `surface2`, radius 4, Barlow 600 16 sp `text2`, 2 dp apart).
- Opponent status: "생각 중" (unlit lamp) → "던짐" (lit ball lamp) → "상대가 숫자를 정하는 중" (secret phase) → "연결 끊김 0:41" (amber text, unlit lamp).

### 12.4 Digit slots (`DigitSlots`)
- n plates centred, gap 8 dp; width = min(56, (content width − (n − 1) × 8) ÷ n) dp, height 64 dp (48 compact; 60 on result); radius 8; digit `numSlot` (28 sp compact).
- States: **empty** (`bg` fill, 1 dp `outline` inset); **active** (next to fill: 2 dp `chalk` inset, no caret; never shown while the keypad is locked, e.g. reconnecting: entered digits stay filled, the next slot stays empty, shot `match-reconnect-ko`); **filled** (`surface2`, chalk digit); **locked** (after pitching: `surface1`, `text2` digit); **error** (`outTint` fill, 2 dp `out` inset, row shakes; error text appears in a toast); **hit** (result, solved digit: `strike` fill, `onChalk` digit).
- Tapping a filled slot clears it and every slot after it (keeps entry left-to-right; simple and predictable).

### 12.5 Digit keypad (`DigitKeypad`), custom, never the system keyboard
- Grid 5 columns, gap 8 dp, keys 52 dp tall, radius 8, `surface2`; digit `numKey` chalk. Order **0 1 2 3 4 / 5 6 7 8 9** (0 is a normal digit and may lead).
- Action row: guessing: `⌫` (1 column) + **Pitch** (4 columns, `chalk` fill, `onChalk` `label`); secret entry: `⌫` + `shuffle` + **Lock in** (3 columns). Long-press `⌫` clears all.
- Compact window: 2 rows × 6 columns, gap 4 dp, keys 48 dp: `0–4 ⌫` / `5–9 Pitch`; last column 64 dp, Pitch label `labelSmall`.
- Key states: **default**; **pressed** (`surface3`, 90 ms); **used** (digit already in the slots: `surface1`, `textDisabled`, not tappable: this enforces distinct digits before an error can happen); **memo out** (digit `text3` + a 9 dp × in the top-right corner, 8 dp from the right, 11 dp from the top, 1.5 dp strokes `text3`); **memo in** (14 × 3 dp chalk bar, 7 dp above the bottom edge); **locked** (waiting or reconnecting: all keys `surface1` / `textDisabled`, marks `textDisabled`).
- Pitch states: **disabled** until n digits are in (`surface2`, `textDisabled`); **enabled**; **waiting** (`surface2`, `text2`, three-lamp chase + "상대의 투구를 기다리는 중"); **offline** (disabled label "던지기").
- Memo marks on keys mirror the memo board; keys do not change memo state (one place to edit, two places to see).

### 12.6 Hint display (`HintCount`, `CountLamps`)
- Text: `numHint`; S count + `S` in `strike`, B count + `B` in `ball`; a zero count is shown in `textDisabled` (`0S`) so columns stay aligned; 0S 0B is the `OUT` tag (`numLabel` 14 sp, tracking 0.08 em, `out` text, 1 dp `out` inset, radius 4, 22 dp tall, 6 dp side padding) spanning both columns.
- Lamps: disc + radial glow drawn behind (Compose: `drawBehind { drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent)), radius = size × 1.25) }`, no blur). Sizes: `lamp_l` 12 dp (result, gate), `lamp_m` 8 dp (form, legends), `lamp_s` 6 dp (log, status). Gaps 6 / 3 dp; 2 dp for n ≥ 5. Unlit: `lampOff` + 1 dp `lampOffRing`.
- In the latest log row each count has n lamps 6 dp under it (S lamps under `S`, B lamps under `B`; OUT shows one red lamp under the tag).

### 12.7 Pitch log (`PitchLog`)
- Header 28 dp: `#`, "내 추측", "판정" (centred over both hint columns), "상대" (right) in `labelSmall` `text3`.
- Columns: 28 dp round · flexible guess · 44 dp S · 44 dp B · 72 dp opponent (S/B 52 dp when n ≥ 5).
- Row 44 dp, 1 dp `line` top border; round `numLabel` `text3`; guess `numLog` chalk; opponent column `numTag` `text2` showing only the hint the opponent got on my number (their digits are hidden; tap the column header to open "상대 기록", a sheet with their full log).
- Latest row: 64 dp, `surface1`, round in chalk, guess `numSlotSm`, lamps under the counts.
- Newest on top. The list scrolls; when content continues below, a 16 dp fade-out mask sits at the bottom edge (Compose: `drawWithContent` + vertical gradient with `BlendMode.DstIn`).
- Empty (secret phase): the log area shows the legend rows "판정 읽는 법": `1S` + lit S lamp, `1B` + lit B lamp, `OUT` tag, each with a one-line explanation, 44 dp rows.

### 12.8 Memo board (`MemoBoard`)
- `surface1` band, 16 dp margins, 8 dp bottom padding.
- Toolbar 32 dp: "메모" (`labelSmall` `text2`), help text (`caption` `text3`, shown **only while the memo is empty**), "지우기" text button, collapse chevron (36 dp visual, 48 dp target).
- Grid: 24 dp label column + 10 equal columns, digits **0–9**. Header row 32 dp = digit status; n position rows 28 dp each, labelled 1..n (`numLabel` `text3`).
- Digit status cycle on tap: unknown (chalk digit) → **out** (`textDisabled` digit + 22 dp diagonal stroke, 1.5 dp, `text3`) → **in** (24 × 24 dp chalk plate, radius 4, `onChalk` digit) → unknown.
- Position cell cycle: unknown (4 dp `lampOffRing` dot) → **out** (10 dp ×, 1.5 dp strokes, `text3`) → **in** (24 × 24 chalk plate with the digit) → unknown. Setting a cell to **in** also marks every other unknown cell in that row and column as out and sets the digit's status to in (same as the web prototype).
- A column whose digit is out shows its cells at 35 % opacity.
- Collapsed: toolbar + digit row only (chevron up). **Auto-collapse** when the log would show fewer than the latest row + one full row (64 + 44 dp); the user can expand it again (then the log keeps at least the latest row). The user's choice persists per digit count.
- Compact window: hidden; the `edit_note` button in the top bar swaps the log area for the memo board.
- Memo is local only, cleared when a duel ends, never sent to the server.

### 12.9 Player name, tags, badges
- Name: `subtitle` or `bodyStrong`; tag `#1234` `numTag` `text3`, 1 dp gap, tag never truncates.
- Tags (`micro`, 20 dp, radius 4): outline `outline` + `text2` ("방장"), solid chalk ("나").
- Team badge 24 × 24 dp, radius 4, letter Barlow 700 16 sp `onChalk` on `teamA` / `teamB`. Team stripe 3 dp on the outer edge of seats and board rows (A left, B right).

### 12.10 Room seat (`Seat`)
- 56 dp, radius 8, `surface1`, 3 dp team stripe on the left, 13 dp left padding.
- States: **taken** (name + tags); **mine** (+ 1 dp `outline` inset); **empty, own team** (transparent, 1 dp `line`, unlit lamp + "빈 자리", not tappable); **empty, other team** (1 dp `outline`, `swap_horiz` + "이 자리로 이동", tappable = move); **offline player** (name `text3`, tag "오프라인").

### 12.11 Team match board row (`DuelRow`)
- 72 dp; columns: A player (flex) · status 88 dp · B player (flex, right-aligned); 3 dp stripes inset 12 dp top and bottom; my row `surface1`.
- Under each name, for **every** duel (not only mine): "최고" (`caption` `text3`) + best hint (`numTag` `text2`, "2S 1B"; the `OUT` tag at 18 dp when the best is 0S 0B) or, for the winner, a 6 dp lit strike lamp + "승" (`micro`, `strike`). Loser's name and hint in `text3`. Source: `DuelSummary.bestHintA / bestHintB` (protocol 1.1 §7, never digits); my own duel may use `MyDuelView` (same value). While a side's best hint is null (no resolved guess yet, e.g. round 1) show "최고 –" with the dash in `text3`, so every row keeps its two-line shape.
- Status column: round `R7` (`numLabel` `text3`) + one of: "진행 중" with chalk lamp, "무승부", "기권", "재접속 대기 0:41" (`strike`).

### 12.12 Segmented control (digit count)
- 48 dp track `surface2`, radius 8, 4 dp padding and gap; 4 items radius 4; item = number (Barlow 600 20 sp) + unit (`labelSmall`), e.g. "3자리". Selected: chalk fill, `onChalk`. Unselected: `text2`. Plurals come from `<plurals>` (Russian 3/4 цифры, 5/6 цифр).

### 12.13 Buttons
| Variant | Height | Fill / border | Text |
| --- | --- | --- | --- |
| Primary | 56 | `chalk`; pressed `chalkPressed` | `label` `onChalk` |
| Secondary | 48 | transparent, 1 dp `outline` | `label` chalk |
| Tonal | 48 | `surface2`; pressed `surface3` | `label` chalk |
| Small | 36 | as secondary or tonal | `labelSmall` |
| Text | 36 (48 target) | none | `labelSmall` `text2` |
| Destructive | as text or secondary | border `out` when outlined | `out` |
| Disabled (any) | same | `surface2`, no border | `textDisabled` |
Radius 8; icon 20 dp, 8 dp gap; horizontal padding 24 dp (12 small). One primary button per screen.

### 12.14 List row, switch, radio, text field
- Row: see section 4; value text `body` `text2`; chevron 20 dp `text3`. The app opens no external web pages (app only); trailing icon buttons (e.g. copy) are 48 dp, `text2`.
- Long-text reader (Terms, Privacy policy): see [`screens/15-legal-reader.md`](screens/15-legal-reader.md); body 15 / 24 sp, max width 560 dp.
- Switch 52 × 32 dp. Off: `surface2` track, 2 dp `outline` inset, 16 dp `text3` thumb at x = 8. On: `chalk` track, 24 dp `onChalk` thumb at x = 24. Whole row is the target.
- Slider (volume): row 48 dp under its switch row, starts at x = 56 dp; track 4 dp radius 2, inactive `surface4`, active `chalk`; thumb 20 dp chalk disc; value `numTag` `text2` in a 28 dp right-aligned box. Disabled (its switch off): fill, thumb and value `textDisabled`. Steps of 5; TalkBack role slider with percentage; volume keys do not move it. A preview cue plays on release (effects slider) or music fades in at the new level (music slider), both obeying follow-silent-mode.
- Radio 20 dp: 2 dp `outline` ring; selected: 2 dp chalk ring + 10 dp chalk dot. Row 48 dp.
- Text field 56 dp, `surface2`, radius 8, 16 dp padding, text `sans` 400 18 sp; focused 2 dp chalk inset; error 2 dp `out` inset + `caption` `out` message below; counter `numTag` right-aligned below.

### 12.15 Chat
- Sheet (rooms) from y = 136 dp (phone) with `scrim` behind; header 48 dp: "채팅" `title`, "전체 채팅 끄기" + switch, close.
- Message: name line (`micro`, team badge 16 dp + name + `#tag`) over bubble; bubble padding 10 × 12 dp, radius 8, max width 80 %, hugs its text. Others `surface4` / chalk text; mine `chalk` / `onChalk`, right-aligned, time `caption` 12 sp `text3` under it.
- System line (protocol 1.1 `kind: "system"`): centred `caption` `text3`, max 2 lines, no bubble, no name line, not long-pressable, no player card. The client builds the sentence from `systemCode` + `subjectNickname#subjectTag`: `player_joined` "민트초코#0912 님이 들어왔어요" / "민트초코#0912 joined the room"; `player_left` "… 님이 나갔어요" / "… left the room"; subject deleted (`subject*` null): "탈퇴한 플레이어가 들어왔어요 / 나갔어요"; unknown code: "방에 변화가 있어요" / "Something changed in the room". Shot `chat-system-ko`.
- Masked words render as `***`, tracking 0.1 em: in others' bubbles `text2` on `surface4` (5.59 : 1), in my bubble `textDisabled` on chalk (5.48 : 1). Never `text3` on `surface4` (2.2). TalkBack "가려진 말".
- Long-press a message: `held` state (2 dp chalk ring) + action sheet: context line, 복사, 메시지 신고, 이 플레이어 차단 (destructive, with subtitle).
- Quick phrases row: all 12 protocol phrase ids in protocol order (09 §4), 32 dp chips (`labelSmall`, 1 dp `outline`, radius 4, 12 dp padding, 8 dp gap), horizontally scrollable with a 16 dp right fade; 48 dp touch height via vertical padding.
- Composer: 48 dp field `surface2` "메시지 (최대 80자)" + 48 dp chalk send button (disabled `surface2` / `textDisabled` while empty or rate-limited). Rate limit line above: `timer` 16 dp + `caption` `strike` "너무 빨라요. 3초 뒤에 보낼 수 있어요".
- Muted: messages hidden, a centred empty state "채팅을 꺼 두었어요" + text button "켜기".

### 12.16 Sheets, dialogs, toasts, banners
- Bottom sheet: `surface3`, top radius 16, grab handle 32 × 4 dp `outline` 12 dp from the top, `sheet` spring; dismiss by drag, scrim tap or back, except where noted (Terms gate is a full screen, not a sheet).
- Dialog: 24 dp side margins (max width 400 dp), radius 16, padding 24 / 24 / 16, title `title`, body `body` `text2` 12 dp below, actions 24 dp below: text buttons right-aligned, or stacked full-width buttons when one action is primary. Destructive action text in `out`. Alert dialogs (deletion notice) cannot be dismissed by scrim tap.
- Toast: 16 dp side margins, 16 dp above the keypad or bottom bar, min 48 dp, radius 8, `surface3`, `body`; visible 2.8 s; never covers the slots.
- Connection banner: under the top bar, `surface3`, 10 × 16 dp padding, chase lamps + `bodyStrong` title + `caption` `text2` line + countdown `numTimer` `strike` right; pushes content down (never overlays).

### 12.17 Empty, error, offline, reconnecting
| State | Presentation |
| --- | --- |
| Empty log | Legend rows (12.7) |
| Empty room team | Empty seats (12.10) |
| No blocked players | Centred `caption` `text3` "차단한 플레이어가 없어요" 48 dp below the top bar |
| Offline at Home | 48 dp band under the top bar, `surface3`, `cloud_off` + "오프라인 · 컴퓨터 대결만 할 수 있어요"; online rows and quick match are disabled with "인터넷 연결 필요" subtitles; banner ad slot collapsed |
| Reconnecting (in match) | Connection banner with 60 s countdown; clock paused; keypad locked |
| Opponent disconnected | Duel strip status "연결 끊김 0:41" in `strike`; keypad stays usable |
| Server error / unknown | Dialog "문제가 생겼어요" + body + "다시 시도" (primary) + "홈으로" |
| Timeout forfeit | Result screen with "시간 초과 2회로 기권했어요" sub line |

### 12.18 Ad banner slot (`AdBand`)
See section 13.

---

## 13. Ads

Placement rules from the README and the release lead, turned into layout:

- **Where (banner):** Home, duel and team results, Profile. **Never** on match, secret entry, waiting practice, quick-match search, room lobby, chat, Terms gate, Settings, dialogs, or any sheet.
- **Anchored adaptive banner** at the bottom of the window, directly above the navigation-bar inset: `AdBand` = 1 dp `line` top edge + `surface1` band whose height = the ad's height (50 to 90 dp, from `AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(width)`); the creative is centred if narrower than the band; `windowInsets.navigationBars` padding goes **below** the band and is filled with `bg`.
- **Clear space:** no tappable control within 24 dp above the band (the dock and action areas end with 24 dp bottom padding). Nothing scrolls under the band.
- **Loading / no fill:** the band is not in the layout (0 dp, no placeholder, no "AD" box). When an ad loads, the band expands from 0 to its height in `medium` 240 ms (`decelerate`); the content above moves up, and taps on controls that moved are ignored for 300 ms after the expansion ends (settle guard against accidental clicks). If the ad fails or is closed, the band collapses the same way.
- **Small windows:** when the window height is < 480 dp, no banner is requested or shown.
- **One ad per moment:** a result screen reached through an interstitial shows no banner (shots `result-cpu-lose-ko`, `result-link-ko/en`).
- **Interstitial (owner decision 2026-11-25: vs computer and online)**, at the natural break **between the end of a match and its results screen**, never the instant the last guess lands. Full flow and the release lead's safeguards (`docs/play/POLICY_CHECKLIST.md` 11.1) in [`screens/06-results.md`](screens/06-results.md) §3: the final reveal finishes on the match screen, then ≥ 1 s with no touch down and no chat or keyboard open, then the ad (if due and loaded), then the results screen with its full hero animation. Cap: 1 per 3 finished matches (online and vs computer counted together) and ≥ 4 minutes apart; a team match is one match (one ad at its very end, never between duels). Never during a match, secret entry or the waiting practice game, never on app start or exit, never in the review room, never in a window shorter than 480 dp, never when the player owns `remove_ads`. Ads are always muted. Game music pauses while the interstitial is open and resumes after it closes. There is no pre-announcement note any more (the ad comes before the result screen, so a note there would be too late).
- **Remove ads (owner decision 2026-11-25):** one-time Play purchase `remove_ads`. Entry points only in Settings ("구매" section, [`screens/11-settings.md`](screens/11-settings.md)) and as a quiet text button "광고 제거" in the top bar of the duel and team **result** screens ([`screens/06-results.md`](screens/06-results.md) §1). Never a pop-up, dialog, toast, badge or animation that pitches it; never on the match, secret, waiting, search, lobby or chat screens. Once owned: no banner is requested (bands stay at 0 dp), no interstitial, no UMP form, and the result link disappears. The price is always Play's formatted price, never a hard-coded string.
- Banners never appear behind a scrim-less overlay; when a dialog is open on Home the banner stays where it is under the scrim.

---

## 14. Copy and content

### 14.1 Languages and fallback (QA DEF-020)

Supported: ko (primary), en, ja, zh-CN (Simplified), es (Latin American copy), fr, de, pt-BR, ru, vi. The app follows the system language unless the player picks one in Settings > 언어 (per-app language, `AppCompatDelegate.setApplicationLocales`; `res/xml/locales_config.xml` lists the ten).

| Device locale | App language |
| --- | --- |
| Any supported language, any region (ko-KR, en-GB, fr-CA, de-AT, ru-UA, vi-VN, ja-JP …) | that language |
| zh-CN, zh-SG, zh-Hans-* | zh-CN (Simplified) |
| zh-TW, zh-HK, zh-MO, zh-Hant-* | **English** (we do not show Simplified text to Traditional readers; a Traditional translation is a later decision) |
| es-ES and every es-* | es (Latin American copy) |
| pt-PT and every pt-* | **pt-BR** |
| Anything else (it, tr, th, id, …) | **English** |

Android resource folders that make this happen without code: `values` = English (default); `values-ko`, `values-ja`, `values-b+zh+Hans` (not `values-zh`, so Traditional locales fall through to English), `values-es`, `values-fr`, `values-de`, `values-pt` (Brazilian copy, so pt-PT matches it), `values-ru`, `values-vi`. Plurals use `<plurals>` in every language (Russian 3/4 цифры vs 5/6 цифр). The bundled display face only renders digits and Latin letters, so no language depends on it.

### 14.2 Copy rules

- Korean first: short 해요체, no exclamation marks except in quick phrases and the result headline area ("승리" has none). Titles are statements, not questions, except confirmations.
- Numbers always in `num`; units in `sans` next to them ("4자리", "60초").
- Never "Excel", "Mastermind", "Numer0n", "ad-free". Never show real player names in screenshots.
- The app name is a set of resources (owner decision 2026-11-21): `app_name` = "숫자야구: 조용한 한판" / "Number Baseball: Quiet Duel" (full, localized per `docs/play/STORE_LISTING.md`), split for the UI into `app_name_main` ("숫자야구") and `app_name_sub` ("조용한 한판"). Home and the first-launch screens show main (`subtitle`) over sub (`caption` `text3`) next to the mark; the launcher label uses `app_name_main`. No brand word is baked into the icon, artwork or store graphics; store text comes from [`store/store-text.json`](store/store-text.json).
- Minimum age 14 (owner decision), checked once by the neutral birth-year screen (01 §0). The style stays adult-neutral; no copy mentions the age number.

---

## 15. Brand mark and icon

- Mark: a scoreboard number plate (`surface2`, 1 dp `lampOffRing` edge, radius 6 at 108 dp) holding a chalk **3** (Barlow Condensed Bold outline) above three lit strike lamps: "three strikes" = solved. Files in [`icon/`](icon/): adaptive foreground and background (108 dp canvas, all art inside the 66 dp safe circle), monochrome layer for themed icons, composite, and the 512 px store icon in [`store/`](store/).
- In app: the mark appears at 22 × 32 dp in the Home top bar and the first-run eyebrow (inline SVG in the prototype; a vector drawable in the app).

---

## 16. Android implementation notes

- **Fonts:** copy `design/fonts/*.ttf` to `app/src/main/res/font/`. `val Num = FontFamily(Font(R.font.barlow_condensed_medium, FontWeight.Medium), Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold), Font(R.font.barlow_condensed_bold, FontWeight.Bold))`. All `num` text styles: `fontFeatureSettings = "tnum"`. `sans` = `FontFamily.Default` with `FontWeight.Normal` / `FontWeight.Bold` only.
- **Text style mapping:** `TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp, lineHeightStyle = LineHeightStyle(Alignment.Center, Trim.None))`; `platformStyle = PlatformTextStyle(includeFontPadding = false)` so the numbers sit exactly in plates. Tracking in em → `letterSpacing = (tracking).em`.
- **Edge-to-edge:** `enableEdgeToEdge()`; top bar consumes `statusBars` insets; bottom content consumes `navigationBars`; the IME pushes the composer and first-run button (`imePadding()`).
- **Window size:** `currentWindowAdaptiveInfo().windowSizeClass`; compact game layout when `windowHeightSizeClass == COMPACT` (< 480 dp).
- **Lamps:** draw with `Canvas`/`drawBehind` (disc + radial gradient glow, radius 1.25 × diameter); no `blur()` (API 31+ only and expensive).
- **Fades:** list fading edge = `graphicsLayer { compositingStrategy = Offscreen }` + `drawWithContent { drawContent(); drawRect(verticalGradient(...), blendMode = DstIn) }`.
- **Shadows:** level 3 only, `Modifier.shadow(8.dp, shape, ambientColor/spotColor = 0x7A000000)`.
- **Memo board:** `LazyVerticalGrid` is unnecessary; a `Column` of `Row`s with `weight(1f)` cells is enough and keeps exact heights.
- **Haptics:** wrap in one `Haptics` helper that checks the setting, `arePrimitivesSupported` and the fallback table.
- **Review:** the Roborazzi renders will be compared against `design/shots/` at 360 × 800 (density 2) and 360 × 320. Every number in this file and in `screens/` is a defect if off by more than 1 dp (2 px at density 2); colours must match the token hex exactly.
