# Clean White: design system v2

Version 2.0 (owner decision 2026-11-26: redesign direction A "Clean white", [`redesign/PROPOSAL.md`](redesign/PROPOSAL.md)). Replaces "Night Game" 1.0. Owner: design lead. Source of truth for values: [`tokens.json`](tokens.json). This document gives the rules; the prototype in [`prototype/`](prototype/) shows them and is built only from `css/tokens.css`, which `prototype/gen-tokens.js` generates from `tokens.json`. Screen specs are in [`screens/`](screens/), reference shots in [`shots/`](shots/), the engineer's change list in [`REDESIGN_V2.md`](REDESIGN_V2.md), the review history in [`REVIEW_LOG.md`](REVIEW_LOG.md).

Units: dp for size, sp for type. In the prototype 1 px = 1 dp = 1 sp at font scale 1.0. Every number in this file is meant to be measured on a screenshot; "about" is never used for a spec value.

---

## 1. Direction

**A quiet sheet of paper with one blue pencil.** White window, near-black text, one blue accent, nothing raised, nothing glowing. The game is the numbers: big tabular digits, three small hint chips, one obvious next action. Everything that is not needed right now (the memo grid, the inning table, mode descriptions) is one tap away instead of on screen.

Six rules that decide every argument:

1. **One accent.** `accent` blue is used for the primary button, the active slot, switches and radios, progress and links. Nothing else is blue except the team A badge (always with the letter A). Hint colours (strike orange, ball teal) appear only on hint chips and the matching warning / status text, always with a letter or a word.
2. **One primary action per screen**, in the bottom 40 % of the window (thumb zone).
3. **Numbers are the hero.** Every digit (slots, keys, log, timer, scores, codes, tags) is set in bundled **Inter** with tabular figures. Words are set in the system face.
4. **Flat.** No shadows, no glows, no gradients (the only gradient is the log fade mask). Grouping comes from spacing, `surface2` fills and 1 dp `line` dividers. Sheets and dialogs are white on a scrim. A shadow is a defect.
5. **Show it when asked.** The memo lives in a sheet behind "메모"; the per-inning table behind "회별 기록 보기"; mode subtitles are gone. A screen never shows two things at the same visual weight.
6. **Quiet by default.** Effects follow the phone's silent mode, music is off, ads never near play and always muted, short motion, no confetti, no mascots, no exclamation-mark copy.

### 1.1 Why light by default

The owner played the dark build and found it cluttered (README "Visual design (owner decision 2026-11-26)"). On white, fewer grey steps are needed, the hint chips read by shape as well as colour, and the screen stays calm at 200 % text and in split screen. All ratios below are computed.

`android:forceDarkAllowed="false"`; status and navigation bars are transparent (edge-to-edge) with **dark** icons (`isAppearanceLightStatusBars = true`, `isAppearanceLightNavigationBars = true`); the window background is `bg`.

### 1.2 Hard no's (enforced in review)

No Excel or office motifs (no ruled cell grids with column letters), no mascots, no rounded or comic fonts, no gradients, no emoji in UI, no glossy or skeuomorphic elements, no shadows, no card stacks. More than one accent-filled control on a screen is a defect.

### 1.3 Dark theme (later)

Not in v2. When it comes, it follows the **system** dark mode only (no in-app switch), keeps every token **name** of this file and maps them to a dark palette derived from these roles (the v1 "Night Game" values are the starting point: `bg` #0C1210, `surface2` #1A231F, `ink` → chalk #ECEFE6, `accent` lightened to pass 4.5 on the dark `bg`). Components must therefore read colours only from tokens, never from literals, so the dark theme is a palette swap. Hint chips keep their shapes (solid S, outlined B, wide OUT) in dark.

---

## 2. Colour

All values are `#AARRGGBB` in `tokens.json` (Kotlin `Color(0xAARRGGBB)`). Ratios are WCAG 2.x, computed by script (`contrast_checked` in tokens.json).

### 2.1 Tokens

| Token | Hex | Use |
| --- | --- | --- |
| `bg` | `#FFFFFF` | Window, behind system bars; empty slots; B chip fill |
| `surface1` | `#F9FAFB` | Quiet fills: used keys, locked slots, ad band, expired code plates |
| `surface2` | `#F3F4F6` | Plates: keys, filled slots, latest log row, my-duel board row, text fields, segment tiles, timer pill, tonal buttons, OUT chip, seats, others' chat bubbles, connection banner, offline band, avatars |
| `surface3` | `#FFFFFF` | Sheets and dialogs (on `scrim`, no shadow) |
| `surface4` | `#E5E7EB` | Pressed state of anything on `surface2`. Never a resting surface |
| `line` | `#E5E7EB` | 1 dp dividers; 1.5 dp borders of mode tiles, secondary buttons, the memo button and empty seats (decorative) |
| `outline` | `#7E8693` | Boundary of controls that need one: empty slot (1.5 dp), switch off (2 dp), radio ring (2 dp), empty code plate |
| `ink` | `#111418` | Text and icons; selected segment fill; answer plates; solid "나" tag; memo "in" plate; team B |
| `onInk` | `#FFFFFF` | Text on `ink` |
| `accent` | `#2453E6` | Primary button, active slot ring (2 dp), switch on, radio on, timer progress, links, unread badge, focus ring, result win icon |
| `accentPressed` | `#1C44C4` | Primary pressed |
| `onAccent` | `#FFFFFF` | Text on `accent` |
| `accentTint` | `#EAF0FE` | Win badge circle, my chat bubble, team A wash |
| `text2` | `#4B5260` | Secondary text, row values, my number, masked `***` |
| `text3` | `#5F6673` | Captions, section labels, round numbers, opponent hints, placeholders, **zero hint counts** |
| `textDisabled` | `#B9BEC6` | Disabled text and icons only (exempt) |
| `strike` | `#B54708` | S chip fill (white text); warning text (timer ≤ 10 s, rate limit, purchase error, reconnect countdown, "재접속 대기"); 승 tag fill |
| `strikeTint` | `#FDF0E7` | Solve flash behind the latest row |
| `ball` | `#00796B` | B chip ring and text; "던짐" / online status dot; report-sent check |
| `out` | `#111418` | OUT chip text and 1.5 dp ring (on `surface2`) |
| `danger` | `#C4291C` | Errors and destructive actions, invalid slot ring, timer ≤ 5 s |
| `dangerTint` | `#FDECEA` | Invalid slot / code plate fill |
| `dotOff` | `#C9CED6` | Unlit dots (status, chase, fill), memo "unknown" dot, sheet grab handle |
| `teamA` / `teamATint` | `#2453E6` / `#EAF0FE` | Team A badge (white A) |
| `teamB` / `teamBTint` | `#111418` / `#F3F4F6` | Team B badge (white B) |
| `inverse` / `onInverse` / `inverseAccent` | `#1F2329` / `#FFFFFF` / `#A9BEFF` | Toast surface, text, action |
| `scrim` | 40 % `#111418` | Behind sheets and dialogs |
| `focus` | `#2453E6` | 2 dp focus ring, 2 dp outside the control |

### 2.2 Contrast (computed)

| Pair | Ratio | Requirement |
| --- | --- | --- |
| ink on bg / surface2 / surface4 / accentTint | 18.47 / 16.78 / 14.92 / 16.18 | 4.5 text |
| text2 on bg / surface2 / accentTint | 7.85 / 7.13 / 6.88 | 4.5 text |
| text3 on bg / surface1 / surface2 / accentTint / strikeTint | 5.78 / 5.53 / 5.25 / 5.06 / 5.17 | 4.5 text |
| outline on bg / surface2 | 3.67 / 3.34 | 3.0 UI boundary |
| accent on bg / surface2; white on accent | 6.08 / 5.52; 6.08 | 4.5 text |
| white on strike; strike on bg / surface2 | 5.43; 5.43 / 4.93 | 4.5 text |
| ball on bg / surface2 | 5.32 / 4.83 | 4.5 text |
| out (ink) on surface2 | 16.78 | 4.5 text |
| danger on bg / surface2 / dangerTint | 5.70 / 5.18 / 4.99 | 4.5 text |
| white on ink / teamA | 18.47 / 6.08 | 4.5 text |
| onInverse / inverseAccent on inverse | 15.78 / 8.64 | 4.5 text |

**Faint-zero fix.** The proposal drew zero counts (`0S`, `0B`) in `disabled` grey (1.87 : 1). v2 draws them in **`text3`** (5.78 on bg, 5.25 on the latest row's `surface2`), weight 600, no chip. Every text in the app now passes AA; `textDisabled` is used only for disabled controls.

`text3` must never sit on `surface4` (4.67 passes, but pressed states are transient; keep labels on resting surfaces). `strike`/`ball` text on `surface4` is not used.

### 2.3 Colour-blind check

Machado 2009, full severity, CIELAB ΔE of the simulated S fill vs B ring: protan 43, deutan 58, tritan 97 (clearly distinct). Colour is still never the only cue:

- **Letter:** every hint is count + letter (`2S`, `1B`) or the word `OUT`, fixed order S then B.
- **Shape:** S = solid filled chip, B = outlined chip, OUT = one wide neutral chip spanning both columns, zero = plain text without a chip.
- Teams carry the letters A and B in badges and sit on fixed sides (A left, B right).
- Win, loss and draw are words; the result badge also differs in shape (check vs ball icon).

---

## 3. Typography

### 3.1 Families

| Role | Family | Weights | Licence | Notes |
| --- | --- | --- | --- | --- |
| `sans`: all words, every language | System default (`FontFamily.Default`), unchanged from v1 | **400 and 700 only** | system | CJK ships in Regular and Bold on many devices. |
| `num`: digits and a few Latin letters | **Inter 4.000**, bundled: `res/font/inter_medium.ttf` (500), `inter_semibold.ttf` (600), `inter_bold.ttf` (700), from [`fonts/`](fonts/) | 500, 600, 700 | SIL OFL 1.1 ([licence](fonts/OFL-Inter.txt)) | Latin subset (U+0020–007E, U+00A0–00FF, a few dashes and quotes; 309 glyphs, about 53 KB each), CFF converted to TrueType outlines. **Only** for digits, S B, `OUT`, `VS`, `R`, `#` tags, codes, `m:ss`, `%`. Never for a translated string. |

Licence: Inter's OFL declares no Reserved Font Name, so the subset keeps the name; `OS/2 fsType = 0`. The licence text goes into the in-app Open-source licences screen (replacing Barlow Condensed) next to Material Symbols (Apache 2.0). Barlow Condensed is retired; its files were removed from `design/fonts/` (the app keeps its copy until the swap).

Every `num` style sets `fontFeatureSettings = "tnum"` (tabular figures: Inter's default figures are proportional). The shots use tabular digits (`proto.js` sets `font-variant-numeric: tabular-nums` inline on every Inter element because the CSS `font` shorthand resets it).

### 3.2 Scale

| Token | Family | Size / line (sp) | Weight | Tracking (em) | Use |
| --- | --- | --- | --- | --- | --- |
| `display` | sans | 40 / 48 | 700 | 0 | Result headline |
| `headline` | sans | 26 / 34 | 700 | 0 | First-run and gate titles, found opponent |
| `title` | sans | 20 / 28 | 700 | 0 | Top bar, sheet and dialog titles |
| `subtitle` | sans | 16 / 22 | 700 | 0 | Row titles, names, round, app name (17 sp on Home) |
| `body` | sans | 15 / 22 | 400 | 0 | Running text, chat |
| `bodyStrong` | sans | 15 / 22 | 700 | 0 | Emphasis |
| `label` | sans | 16 / 20 | 700 | 0 | Buttons (17 sp on the Pitch key) |
| `labelSmall` | sans | 13 / 18 | 700 | 0 | Section labels, chips, memo button, links (14 sp), mode tiles (14 sp) |
| `caption` | sans | 13 / 18 | 400 | 0 | Helper text, stat labels, status, 광고 제거 |
| `micro` | sans | 12 / 16 | 700 | 0 | Tags. Smallest token |
| `numHero` | num | 48 / 56 | 700 | 0 | Reserved (tablet reveal) |
| `numScore` | num | 48 / 56 | 700 | −0.01 | Team score |
| `numSlot` | num | 32 / 40 | 700 | 0 | Slots, result plates, search plates |
| `numCode` | num | 28 / 36 | 700 | 0 | Room and transfer code plates |
| `numStat` | num | 28 / 32 | 700 | −0.01 | Home / profile record (`%` at 0.7 em) |
| `numKey` | num | 24 / 32 | 600 | 0 | Keypad digit |
| `numSlotSm` | num | 26 / 32 | 700 | 0.12 | Latest log guess; compact slots (tracking 0) |
| `numSummary` | num | 22 / 30 | 700 | 0 | Result summary values, segment numbers |
| `numLog` | num | 21 / 28 | 600 | 0.14 | Older log guesses |
| `numTimer` | num | 18 / 24 | 700 | 0 | Timer pill, countdowns |
| `numMemo` | num | 18 / 24 | 600 | 0 | Memo sheet digits |
| `numHint` | num | 15 / 20 | 700 | 0.02 | Hint chips (OUT 0.08) |
| `numTag` | num | 15 / 20 | 500 | 0 | `#1234`, my number (700, 0.12 em), counters, version |
| `numLabel` | num | 13 / 16 | 600 | 0.02 | Round numbers, opponent hints, VS, R7, memo labels |

Rules (unchanged): `sans` tracking is always 0; Korean breaks by 어절 (`LineBreak.WordBreak.Phrase`, prototype `word-break: keep-all`); a nickname is name (`sans`) + `#1234` (`numTag` `text3`) with no space, and only the name ellipsizes; titles max 2 lines, buttons 1 line (2 at large text).

### 3.3 Font scale

- `sans` scales fully to 200 %.
- **Every `num` style is capped: rendered size = token size × min(fontScale, 1.3), in dp**, everywhere, no exceptions (slots, plates, keys, memo, hint chips including OUT, log, opponent hints, timer, countdowns, scores, stats, segment numbers, codes, tags, badges). Compose: one helper `NbType.capped(style)` computing dp so Android 14 non-linear scaling cannot change it.
- Chips, keys, slots, rows use `heightIn(min = token)`, never a fixed height; hint chip height also scales by min(fontScale, 1.3).
- At `fontScale ≥ 1.5` screens switch to their large-text layout ([`screens/ADAPTIVE.md`](screens/ADAPTIVE.md) §2).
- **Shrink floor** (DESIGN_SYSTEM 11.3, kept from the final sign-off): a UI label may shrink in 5 % steps before wrapping, never below max(component minimum, 11 dp rendered, the 100 % token size at fontScale ≥ 1.5).

---

## 4. Spacing and layout

- **4 dp grid.** Spacing tokens 0, 2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64.
- Screen margin 16 dp; 12 dp in compact windows (height < 480 dp). Content max width 560 dp, centred.
- Icon buttons 48 × 48 dp, 4 dp from the screen edge.
- Top bar 56 dp (48 compact). Opponent row 52 dp (`opp_row_h`). List rows 64 dp with subtitle, 56 single; row text at x = 56; dividers inset to x = 56.
- Gaps: 8 dp between keys, tiles, segment tiles and bottom buttons; 10 dp between slots; 6 dp between hint chips.
- Bottom action zone: primary 56 dp, secondary 56 dp, 8 dp apart, 16 dp side margins. **Banner clear space 24 dp**: nothing tappable (including the 광고 제거 line's 48 dp target) within 24 dp above the ad band.
- Window classes: width compact < 600, medium 600–839, expanded ≥ 840 dp; **height compact < 480 dp** triggers the compact game layout.


### 4.1 Supported window sizes (QA DEF-019 / DEF-020)

| Number | Value | Rule |
| --- | --- | --- |
| **Smallest supported window** | **320 × 320 dp** (width × height) | Every screen is fully usable down to this size (compact layouts, ADAPTIVE.md). Below it the app keeps the game state but shows a full-window notice instead of the screen: brand mark (32 dp) + "창을 조금 더 크게 해 주세요" (`title`) + "게임은 그대로 기다리고 있어요" (`caption` `text3`); online timers keep running, so the reconnect rules still apply. |
| **Ad banner hidden below** | window height **< 480 dp** (or width < 320 dp) | No banner is requested or shown, on any screen. Same threshold as the compact game layout. |
| Compact game layout | window height < 480 dp | ADAPTIVE.md §1 |
| Large-text layout | fontScale ≥ 1.5 | ADAPTIVE.md §2 |
| Two-pane match | window width ≥ 600 dp and height ≥ 480 dp | ADAPTIVE.md §3 |

`android:resizeableActivity="true"`; no `maxAspectRatio`; minimum window size in the manifest `<layout android:minWidth="320dp" android:minHeight="320dp">` (freeform and pop-up windows cannot shrink below it on platforms that honour it).

---

## 5. Shape

| Token | dp | Use |
| --- | --- | --- |
| `none` | 0 | Full-bleed rows and bands |
| `s` | 6 | Hint chips, tags, team badges, memo plates, transfer-code plates |
| `m` | 12 | Keys, slots, plates, buttons, segment tiles, mode tiles, text fields, seats, latest log row, toasts, legend box |
| `l` | 16 | Sheet top corners, dialogs |
| `full` | 999 | Avatars, dots, switch, timer pill, memo button, quick-phrase chips, icon-button ripple |

Nothing uses a radius above 16 dp except pills (`full`), which are reserved for the items listed. Buttons are never pills.

---

## 6. Elevation

**Flat.** There are no shadows in v2.

| Level | Surface | Separation |
| --- | --- | --- |
| 0 | `bg` | — |
| 1 | `surface1` | fill only |
| 2 | `surface2` | fill only |
| 3 (sheets, dialogs) | `surface3` (white) | 40 % `scrim` behind, 16 dp top radius (sheets) / all corners (dialogs), grab handle |
| Toast | `inverse` | the dark fill itself |

Compose: no `Modifier.shadow`, `tonalElevation = 0.dp` and `shadowElevation = 0.dp` on every Material surface (`ModalBottomSheet`, `AlertDialog`, `Surface`).

---

## 7. Iconography

- **Material Symbols Outlined**, weight 400, grade 0, optical size 24, fill 0 (Apache 2.0), as vector drawables.
- Sizes: 24 dp default; 20 dp inside buttons and lists; 18 dp in the timer pill, memo button and links; 16 dp inline with caption text; 32 dp in the result badge.
- Colour: `ink` for actions (top bar, keypad fn keys, mode tiles), `text2` for leading icons in rows and secondary actions (chat, flag), `text3` for chevrons in rows, `accent` inside links, `danger` for destructive rows.

| Glyph | Where |
| --- | --- |
| `close` | Leave match, close sheets, dismiss result |
| `arrow_back` | Back in lobby, settings, team board |
| `more_vert` | Match and lobby overflow |
| `settings`, `help` | Home top bar |
| `memory`, `group`, `login` | Home mode tiles (컴퓨터와, 방 만들기, 코드로 참가); `memory` also practice and the computer avatar |
| `timer` | Timer pill, rate limit, innings sheet time |
| `backspace`, `shuffle` | Keypad delete, random secret |
| `edit_note` | 메모 button (match), memo toggle (compact) |
| `open_in_full` | Large-text memo button |
| `chevron_right` | Links (회별 기록 보기, 처음이라면 게임 방법 보기, terms links), row chevrons |
| `check` / `sports_baseball` | Result badge: win / lose and draw |
| `chat`, `send` | Chat and quick phrases, send |
| `flag`, `block` | Report, block |
| `content_copy`, `share`, `tune`, `swap_horiz` | Room code copy and share, room rules, move seat |
| `visibility` | Watching note |
| Settings rows | `language`, `volume_up`, `do_not_disturb_on`, `music_note`, `mobile_vibrate`, `notifications_off`, `person`, `key`, `delete`, `ad_off`, `history`, `shield`, `policy`, `description`, `send`, `info`, `check_circle`, `hourglass_empty`, `refresh` |
| `cloud_off`, `schedule` | Offline band, maintenance |

`bolt` (old quick-match button) is no longer used.

---

## 8. Motion

### 8.1 Tokens

Unchanged from v1 (names kept so code keeps compiling): `tap` 90, `short` 150, `medium` 240, `long` 360, `lamp_ignite` 180, `lamp_stagger` 110, `slot_fill` 120, `shake` 280, `result_hero` 640, `match_found` 900, `toast_visible` 2800, `waiting_chase_cycle` 1200, `reveal_lead_in` 240, `solve_flash` 300, `result_delay` 400, `result_digit_stagger` 60, `result_digits_after_headline` 320, `result_button_min` 1000, `settle_guard` 300, `timer_pulse` 200, `phrase_bubble` 2800, `music_crossfade` 600, `chat_sound_min_gap` 2000. Easing `standard` (0.2, 0, 0, 1), `decelerate` (0, 0, 0, 1), `accelerate` (0.3, 0, 1, 1), `emphasized` (0.05, 0.7, 0.1, 1), `ignite` (0.2, 0, 0, 1), `linear`. Springs `slot_pop` (damping 0.6, stiffness 800), `sheet` (1.0, 500).

`lamp_ignite` / `lamp_stagger` now time the **hint chip reveal** (there are no lamps in v2); the names stay because the hooks, sounds and haptics are the same.

Defaults: enter = `medium` + `decelerate`; exit = `short` + `accelerate`; state change in place = `short` + `standard`. Screen transitions: shared axis X 24 dp + fade, `long`. Nothing loops except the waiting chase and the search sweep.

### 8.2 Signature moments

1. **The Count (hint reveal).** When both pitches are in, the previous latest row loses its `surface2` fill and shrinks from 64 to 46 dp (`medium`, `standard`) while the new row slides down 12 dp and fades in (`medium`, `decelerate`). Then the chips count up: on the first strike hook the S chip scales 0.8 → 1 and fades in showing `1S`; each further strike hook steps its count (`2S`, `3S`, a 90 ms cross-fade of the number); then the B chip the same way. A zero count fades in as plain `text3` text together with the other chip's first step (or at 240 ms when both are zero, which is the OUT case: the OUT chip scales in on `lamp(out)`). One haptic and one sound per step (sections 9, 10). The keypad unlocks on `reveal_last`.
2. **The Final Strike (win).** The winning row counts up to `nS`, the row flashes `strikeTint` (300 ms), then the screen cross-fades to the result (`long`). On the result the badge scales 0.8 → 1 (`slot_pop`), the headline fades up 8 dp (`result_hero`, `emphasized`), then the opponent's plates reveal left to right (each digit fades in on its ink plate, `slot_fill` 120 ms, 60 ms stagger). Loss and draw: same sequence, neutral badge. No confetti, no scaling text, no shake.
3. **Match found.** The `?` sweep stops, the three dots light accent together, the opponent's avatar, `VS` and name fade in (`match_found` 900 ms total), then the secret-entry screen slides in.
4. **Waiting chase.** Three 6 dp dots, one `accent` at a time (400 ms each, `waiting_chase_cycle` 1200 ms), the others `dotOff`.

Smaller rules: slot fill = digit scales 0.92 → 1 with `slot_pop`; invalid entry = slot row shakes ±6 dp, 280 ms, 3 cycles; sheets use the `sheet` spring; toasts enter from 16 dp below.

### 8.3 Sync timeline (for sound and haptics)

Every step is an **event hook** fired once; sound and haptic start on the hook, at the same frame as the visual change. Times from t = 0 of each sequence. n = digit count. Hook names are unchanged from v1 (`lamp(...)` keeps its name; it now means "hint chip step").

**A. Keypad and pitch**

| t (ms) | Hook | Visual |
| --- | --- | --- |
| 0 | `key_down(digit)` | key `surface4`; digit enters the active slot, scale 0.92 → 1 (`slot_pop`, settles in 120 ms) |
| 0 | `key_rejected` | slot row shakes ±6 dp, 3 cycles over 280 ms |
| 0 | `backspace` | last digit fades out 90 ms |
| 0 | `pitch_submit` | slots lock (`surface1` / `text2`, 150 ms); keypad locks; Pitch becomes the waiting key |
| every 400 | `waiting_tick` (visual only) | chase dot moves |

**B. Hint reveal (The Count).** t = 0 is the frame the server result is applied.

| t (ms) | Hook | Visual |
| --- | --- | --- |
| 0 | `round_result` | old latest row shrinks 64 → 46 dp and loses its fill (240 ms, `standard`); new row slides 12 dp down + fades in (240 ms, `decelerate`); its guess digits are visible at once |
| 240 + 110 × i, i = 0..S−1 | `lamp(strike, i)` | i = 0: S chip scales 0.8 → 1 + fades in as `1S` (180 ms, `ignite`); i ≥ 1: count steps to `(i+1)S` (90 ms cross-fade) |
| 240 + 110 × (S + j), j = 0..B−1 | `lamp(ball, j)` | j = 0: B chip appears as `1B`; j ≥ 1: count steps |
| with the first chip step (or 240 for OUT) | — | a zero count fades in as plain `text3` text (150 ms) |
| 240 | `lamp(out)` (only when 0S 0B) | OUT chip scales 0.8 → 1 + fades in (180 ms) |
| 240 + 110 × max(S + B − 1, 0) | `reveal_last` | keypad unlocks (if no one solved); slots clear (150 ms) |
| last step + 180 | `reveal_end` | sequence complete; TalkBack polite live region speaks the row |

Examples: 2S 1B → steps at 240 (1S), 350 (2S), 460 (1B); keypad unlocks at 460; end 640. OUT → 240, end 420.

**C. Solve, win, lose, draw.** t = 0 is `reveal_end` of the solving round.

| t (ms) | Hook | Visual |
| --- | --- | --- |
| 0 | `solve` / `opponent_solved` | latest row fill flashes `surface2` → `strikeTint` → `surface2` over 300 ms (mine only) |
| 400 | `screen_to_result` | cross-fade to the result screen (360 ms, `standard`) |
| 760 + 110 × i, i = 0..n−1 | `result_lamp(i)` (win only, no sound) | i = 0: badge scales in (`slot_pop`); i ≥ 1: no visual (the hooks keep the v1 timing for the sting) |
| win: 760 + 110 × n; lose/draw: 760 | `result_headline(outcome)` | headline fades up 8 dp (640 ms, `emphasized`); lose/draw: the badge scales in at the same time |
| headline + 320 + 60 × i | `reveal_digit(i)` | opponent plate i: digit fades in on the ink plate (120 ms) |
| headline + 640 | `result_ready` | buttons accept input (never earlier than 1000 ms after `screen_to_result`) |

Example 4-digit win: badge 760; headline 1200; digits 1520, 1580, 1640, 1700; buttons 1840. Team result uses the same timeline.

**D. Pitch clock**

| Moment | Hook | Visual |
| --- | --- | --- |
| remaining = 10.0 s | `timer_warn` | pill value, icon and progress line turn `strike` (150 ms) |
| remaining = 5, 4, 3, 2, 1 s | `timer_tick(s)` | value, icon and line turn `danger` at 5 s; value pulses 1.0 → 1.06 → 1.0 (200 ms) per tick |
| remaining = 0 | `timer_expired` | slots shake once; toast "시간이 지났어요. 이번 회는 넘어가요" |

Clock text updates on whole seconds; the line moves continuously (linear).

**E. Other hooks**

| Hook | When | Visual |
| --- | --- | --- |
| `match_found` | quick match pairs | sweep stops, three accent dots, avatar + name fade in 0–300 ms, screen changes at 900 ms |
| `chat_message` | a message arrives while the chat sheet is closed (rooms) | unread badge pops (scale 0.6 → 1, 150 ms); at most one sound per 2 s |
| `phrase_received` | quick phrase in quick match | bubble under the opponent row fades in 150 ms, stays 2800 ms, fades out 150 ms |
| `nav_forward` / `nav_back` | screen push / pop | shared axis X 24 dp + fade, 360 ms |
| `sheet_open` / `sheet_close` | any bottom sheet (memo, innings, phrases, chat, card) | `sheet` spring |
| `toggle(on/off)` | any switch | thumb slides 150 ms |
| `opponent_pitched` | opponent's pitch arrives | status dot turns `ball` (150 ms), text "던짐" |
| `memo_cycle` | a memo cell or digit changes state | mark cross-fades 90 ms; the keypad mark updates at the same frame |
| `secret_locked` | 이 숫자로 정하기 accepted | slots lock (150 ms) |
| `error_shown` | error dialog or toast | dialog / toast enter |

### 8.4 Reduced motion

If `ANIMATOR_DURATION_SCALE == 0` or "Remove animations" is on: every transition is instant, chips appear with their final counts, the chase shows one static accent dot plus the text. Hooks still fire in order, compressed: all chip-step hooks of a row 60 ms apart, `result_headline` at t = 0 of the result screen, buttons after 1000 ms.

---

## 9. Haptics

On by default (setting "진동"); also respects the system "Touch feedback" setting. Haptics are independent of sound: they still play in silent mode, and only the 진동 switch turns them off. All through `View.performHapticFeedback` or `VibrationEffect.Composition`; fall back as listed when a primitive is unsupported (`Vibrator.arePrimitivesSupported`). Each row fires on the hook named in 8.3.

| Event (hook) | Effect | Fallback |
| --- | --- | --- |
| Digit key, backspace | `KEYBOARD_TAP` | — |
| Key rejected (duplicate digit, pitch with empty slot) | `REJECT` (API 30+) | `LONG_PRESS` |
| Pitch submitted / secret locked | `CONFIRM` (API 30+) | `VIRTUAL_KEY` |
| Strike chip step (`lamp(strike, i)`) | `PRIMITIVE_CLICK` scale 0.7 | `CLOCK_TICK` |
| Ball chip step | `PRIMITIVE_TICK` scale 0.5 | `CLOCK_TICK` |
| OUT chip appears | `PRIMITIVE_THUD` scale 0.6 | `LONG_PRESS` |
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
- Effects never stack: a new cue of the same kind cuts the previous one; chip-step cues within one reveal may overlap by design (110 ms apart).
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
| `lamp(strike, i)` / `lamp(ball, j)` / `lamp(out)` (hint chip steps) | `sfx_hint_strike` / `sfx_hint_ball` / `sfx_hint_out` | playback rate by step index, 110 ms apart |
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

- Minimum touch target **48 × 48 dp** everywhere. The memo is now a sheet: its normal cells are (content width − 24) ÷ 10 = 32.8 dp wide × 40 dp at 360 dp (header 44 dp). That meets WCAG 2.2 target size (24 × 24) but not 48 dp; fully accessible alternatives: (a) TalkBack exposes each cell as one item with a spoken state and "두 번 탭하면 바꿉니다"; (b) at `fontScale ≥ 1.5` (or "Larger memo") the sheet uses the transposed 48 × 48 dp grid (ADAPTIVE §2.3).
- Visual sizes below 48 dp get their target from padding: hint chips are not interactive; the timer pill is not interactive; the memo button (36 dp visual) and 광고 제거 (caption line) have 48 dp targets; quick-phrase chips 32 dp visual / 48 dp target.
- Compact keypad keys 48 dp tall, ≥ 50 dp wide.
- Adjacent targets never overlap.

### 11.2 TalkBack

| Element | Spoken text (ko / en) |
| --- | --- |
| Slot | "자리 1, 5" / "Position 1, 5"; empty: "자리 3, 비어 있음, 다음 입력 위치" |
| Digit key | "3"; memo: "3, 제외로 표시됨" / "3, marked out"; "3, 확정" / "3, marked in"; used: "3, 이미 입력함, 사용할 수 없음" |
| Backspace / random | "한 칸 지우기" / "Delete one digit"; "무작위 숫자" / "Random number" |
| Log row | "6회, 내 추측 3 9 1 2, 3스트라이크 0볼. 상대 판정 3스트라이크 0볼." / "Round 6, my guess 3 9 1 2, 3 strikes 0 balls. Opponent's hint 3 strikes 0 balls." (chips are not separate focus stops) |
| OUT | "아웃, 맞는 숫자 없음" / "Out, no digit matches" |
| 메모 button | "메모 열기" / "Open notes" |
| Memo cell | "자리 2, 숫자 9, 확정. 두 번 탭하면 바꿉니다." |
| Pitch clock | "남은 시간 42초" (not re-announced every second) |
| My number line | "내 숫자 0 4 7 2" |
| Result summary | "걸린 회 6회, 시간 3분 12초, 내 기록 13승" |
| 회별 기록 보기 | role button, "회별 기록 보기" |
| 광고 제거 | "광고 제거, 구매" (role button) |
| Dots, badge icons | decorative (`clearAndSetSemantics {}`); text carries the meaning |
| Opponent status | "Kestrel 2048, 던짐" |

Live regions: round result (polite), pitch clock at 10 s and 5 s (polite), match found (assertive), connection lost and restored (assertive), toast text (polite). Focus stays on the keypad after a pitch.

### 11.3 Text and colour

- Hints are never colour alone (2.3). Errors always have text, not only a `danger` ring.
- **All text meets 4.5 : 1** (2.2), including zero counts (`text3`); control boundaries 3 : 1 (`outline`, `accent`). Exempt: disabled controls, the ad creative, the decorative `line`.
- At 200 % no text is clipped: titles wrap to 2 lines, buttons grow (min 56 dp, 16 dp vertical padding) and wrap to 2 lines; numerals cap at 1.3×.
- **Shrink to fit (kept from the final sign-off 2026-11-26).** A UI label (never player content such as names or chat) that would clip, ellipsize or break inside a word may step its size down in 5 % steps, to a floor of **the larger of** (a) its per-component minimum scale (70 % default; 50–55 % for segment units, mode-tile labels, button labels and team badges), (b) **11 dp rendered**, and (c) at font scale ≥ 1.5, **the token's size at 100 %**. Below the floor: wrap by word / 어절, then ellipsis with the full text in TalkBack. `micro` (12 sp) stays the smallest token.
- Keyboard and switch access: 2 dp `focus` (accent) ring, 2 dp outside every focusable element; Enter on a key types it; Enter on Pitch pitches; hardware digits type on the match screen.

---

## 12. Components

Each component lists anatomy, size and every state. Compose names are suggestions; existing names from `ui/design/` are kept where the role is the same (see [`REDESIGN_V2.md`](REDESIGN_V2.md)).

### 12.1 Top bar (`TopBar`)
- 56 dp (48 compact), `bg`. Scrolled: a 1 dp `line` bottom edge appears (no fill change; status-bar area stays `bg`).
- Leading icon button at x = 4 (`ink`); title `title` at x = 64; trailing icon buttons 48 dp, last one 4 dp from the edge.
- Match variant: two-line stack (`subtitle` round over `caption` `text3` mode · digits), timer pill, `more_vert`; a 3 dp progress line sits **under** the bar with 16 dp side margins (12 compact).
- Home variant: brand mark 32 dp at x = 16, 10 dp, name stack (`app_name_main` `subtitle` 17 sp over `app_name_sub` `caption` `text3`), `help` and `settings` (`ink`).

### 12.2 Pitch clock (`PitchClock`)
- Pill 36 dp tall, `surface2`, radius full, 12 dp side padding: `timer` 18 dp `text2` + 6 dp + value `numTimer` `ink` (min width 34 dp, right-aligned, `m:ss`).
- Progress line: 3 dp, radius 2, track `line`, fill `accent`, shrinks right to left linearly.
- States: normal; **warn** ≤ 10 s (value, icon, fill `strike`); **critical** ≤ 5 s (`danger`, value pulses); **paused** (reconnecting: `textDisabled`).
- Options: 30 / 60 / 120 s or 느긋하게 (10 min); no untimed option; quick match 60 s.

### 12.3 Opponent row (`OpponentRow`, replaces `DuelStrip`)
- 52 dp min, padding 8 / 4 / 8 / 16, gap 10: avatar 36 dp (initial of the nickname `subtitle` 15 sp on `surface2`; computer: `memory` 20 dp) · name + tag (`subtitle` / `numTag`) over status (`caption` `text2` with a 6 dp dot) · quick-phrase / chat button (online only, 48 dp, `chat` `text2`).
- Status: "생각 중" (dot `dotOff`) → "던짐" (dot `ball`) → "상대가 숫자를 정하는 중" (secret phase, `dotOff`) → "연결 끊김 · 0:41" (text `strike`, dot `dotOff`).
- **My number moved** out of this row to the entry head (12.5).

### 12.4 Digit slots (`DigitSlots`)
- n plates centred, gap 10 dp; width = min(60, (content width − (n − 1) × 10) ÷ n) dp, min 44; height 64 dp (48 compact); radius 12; digit `numSlot` (`numSlotSm` tracking 0 compact).
- States: **empty** (`bg`, 1.5 dp `outline` inset); **active** (`bg`, 2 dp `accent` inset; never while the keypad is locked); **filled** (`surface2`, `ink`); **locked** (`surface1`, `text2`); **error** (`dangerTint`, 2 dp `danger` inset, row shakes, message in a toast); **plate** (result / answer: `ink` fill, `onInk` digit, 60 × 68 dp); **question** (search, Home hero: `bg`, 1.5 dp `line`, `?` in `text3`).
- Tapping a filled slot clears it and every slot after it.

### 12.5 Entry head (`EntryHead`) and memo button
- 40 dp row above the slots: left "내 숫자" `caption` `text3` + 6 dp + digits `numTag` 700, tracking 0.12 em, `text2`; right the **메모** button: pill 36 dp visual / 48 dp target, 1.5 dp `line` border, `edit_note` 18 dp + "메모" `labelSmall` `text2`, 10 / 12 dp padding. It opens the memo sheet (12.9). Hidden during secret entry and in compact windows (the top-bar `edit_note` replaces it).

### 12.6 Digit keypad (`DigitKeypad`), custom, never the system keyboard
- 5 columns, gap 8 dp, keys 52 dp min, radius 12, `surface2`, digit `numKey` `ink`. Order **0 1 2 3 4 / 5 6 7 8 9**.
- Action row: guessing `⌫` (1 column) + **던지기** (4 columns, `accent`, `onAccent`, `label` 17 sp); secret entry `⌫` + `shuffle` + **이 숫자로 정하기** (3 columns). Long-press `⌫` clears all. `⌫` / `shuffle` icons `ink`.
- Compact window: 2 rows × 6 columns, gap 4, keys 48 dp: `0–4 ⌫` / `5–9 던지기`; last column 64 dp, label `labelSmall`.
- Key states: **default**; **pressed** (`surface4`, 90 ms); **used** (in the slots: `surface1`, `textDisabled`, not tappable); **ruled out** (memo out: digit `text3` + **one diagonal stroke** 22 × 2 dp, radius 1, `text3`, rotated −45°, centred on the key; still tappable); **marked in** (memo in: 14 × 3 dp `ink` bar 7 dp above the bottom edge); **locked** (waiting / reconnecting: `surface1`, `textDisabled`, marks `textDisabled`).
- Pitch states: **disabled** until n digits (`surface2`, `textDisabled`); **enabled**; **waiting** (`surface2`, `text2` `labelSmall` 14 sp, three 6 dp chase dots + "상대의 투구를 기다리는 중"); **offline** (disabled, label "던지기").
- Keys mirror the memo; they never change it (the memo sheet is the one place to edit).

### 12.7 Hint chips (`HintChips`, replaces `HintCount` + `CountLamps`)
- Chip: height 28 dp × min(fontScale, 1.3), min width 44 dp, 8 dp side padding, radius 6, text `numHint`. Gap 6 dp, right-aligned in the row.
- **S** (count ≥ 1): solid `strike` fill, white `nS`.
- **B** (count ≥ 1): `bg` fill, 2 dp `ball` inset ring, `ball` `nB`.
- **Zero**: no chip, plain `0S` / `0B` in `text3`, weight 600, same box (min width 44 dp) so columns align.
- **OUT** (0S 0B): one chip spanning both columns, min width 94 dp, `surface2` fill, 1.5 dp `out` (ink) inset, `OUT` tracking 0.08 em.
- n ≥ 5 (`wide` log): min width 40 dp, 6 dp padding, OUT 86 dp.
- Opponent hint (log, team board): plain `numLabel` `text3` "2S 1B" / "OUT" — never chips, so my hints always win the eye.

### 12.8 Pitch log (`PitchLog`)
- **No header row.** 16 dp side padding, 8 dp top padding. Columns: round 22 dp (`numLabel` `text3`) · guess (flex, `numLog` `ink`) · hint chips (auto) · opponent hint (min 44 dp, right-aligned); 8 dp column gap; row padding 4 / 8.
- Rows 46 dp min with a 1 dp `line` bottom border. **Latest row**: 64 dp min, `surface2`, radius 12, no border, 4 dp below it, padding 6 / 10 / 6 / 8; guess `numSlotSm`; the opponent hint has a small "상대" label (`caption` 11 sp `text3`) above it — only on the latest row.
- Newest on top; scrolls; 20 dp bottom fade mask when rows continue. At 360 × 800, n = 4, **six rows fit** without scrolling (shot `match-late-ko`).
- Tap the opponent hint of any row, or 더보기 > 상대 기록 보기: opponent's full log sheet.
- Empty (secret phase): the **legend box** — `surface2`, radius 12, 16 dp padding, 8 dp below the opponent row: "판정 읽는 법" `labelSmall` `text3`, then three rows (12 dp gaps, 96 dp key column): `1S` chip · `1B` chip · `OUT` chip, each with its `caption` `text2` explanation (term in `ink` 700).

### 12.9 Memo sheet (`MemoSheet`, replaces the inline `MemoBoard`)
- Opened by the 메모 button (or the compact `edit_note`). Bottom sheet (12.16), match keeps running, the keypad marks update live underneath.
- Header 56 dp: "메모" `title` · "지우기" text button (48 dp target) · `close`. Help line under it, always shown: "누를 때마다 제외 → 확정 → 해제" `caption` `text3`.
- Grid, 16 dp margins: 24 dp label column + 10 equal columns, digits 0–9. Digit status row 44 dp; n position rows 40 dp, labelled 1..n (`numLabel` `text3`), 1 dp `line` above each position row.
- Digit cycle: unknown (`ink` digit, `numMemo`) → **out** (`textDisabled` digit + 22 × 2 dp diagonal stroke `text3`) → **in** (28 × 28 dp `ink` plate, radius 6, `onInk` digit) → unknown.
- Cell cycle: unknown (4 dp `dotOff` dot) → **out** (10 dp ×, 1.5 dp strokes, `text3`) → **in** (28 × 28 `ink` plate with the digit) → unknown. Setting **in** marks the rest of that row and column out and sets the digit in. Columns of out digits at 35 % opacity.
- Bottom 24 dp + navigation inset. Shot `match-memo-ko`.
- Large text: transposed 48 dp sheet (ADAPTIVE §2.3). Memo is local, cleared when the duel ends.

### 12.10 Player name, avatar, tags, badges
- Name `subtitle` or `bodyStrong` + tag `numTag` `text3`, 1 dp gap; the tag never truncates.
- Avatar: circle `surface2`, initial (first grapheme) `subtitle` `ink`; 48 dp (Home, found screen), 36 dp (opponent row). Decorative for TalkBack.
- Tags (`micro`, 20 dp, radius 6, 6 dp padding): default `surface2` + `text2` ("방장", "오프라인"; on a `surface2` seat the fill is `bg`); solid `ink` + `onInk` ("나").
- Team badge 24 dp, radius 6, Inter 700 14 sp white letter on `teamA` / `teamB`. **No team stripes in v2**: team identity comes from the column header badge (lobby) or the fixed A-left / B-right sides (board).

### 12.11 Room seat (`Seat`)
- 56 dp min, radius 12, padding 0 / 10 / 0 / 12.
- **taken**: `surface2`, name + tags; **mine**: same + solid "나" tag (no extra border); **empty, own team**: `bg` + 1.5 dp `line` border, 6 dp `dotOff` dot + "빈 자리" `caption` `text3`, not tappable; **empty, other team**: same border, `swap_horiz` 20 dp + "이 자리로 이동" `labelSmall` `accent`, tappable; **offline player**: name `text3` + tag "오프라인". One border style only.

### 12.12 Team board row (`DuelRow`)
- 72 dp min; columns A player (flex) · status 88 dp · B player (flex, right-aligned); 12 dp side padding; 1 dp `line` bottom border. **My row**: `surface2`, radius 12, no border.
- Under each name, for every duel: "최고" `caption` `text3` + best hint (`numTag` `text2`, "2S 1B" / "OUT" as text) or, for the winner, the **승 tag** (`micro` white on `strike`, 20 dp, radius 6). Loser's name and hint `text3`. Null best: "최고 –".
- Status: `R7` (`numLabel` `text3`) + "진행 중" with a 6 dp `accent` dot (`caption` `text2`) / "무승부" / "기권" / "재접속 대기 0:41" (`strike`).

### 12.13 Segmented control (`Segmented`) = tiles
- No track. Equal tiles, 8 dp gap, 48 dp min (52 dp on Home), radius 12, `surface2`. Number item: number `numSummary` `ink` + unit `labelSmall` `text2` ("4자리"); text item: `labelSmall`.
- **Selected: `ink` fill, `onInk` number and unit.** Plurals from `<plurals>`.

### 12.14 Buttons
| Variant | Height | Fill / border | Text |
| --- | --- | --- | --- |
| Primary | 56 | `accent`; pressed `accentPressed` | `label` `onAccent` |
| Secondary | 56 | `bg`, 1.5 dp `line` inset | `label` `ink` |
| Tonal | 48 | `surface2`; pressed `surface4` | `label` `ink` |
| Small | 36 (48 target) | as tonal | `labelSmall` |
| Text | 36 (48 target) | none | `labelSmall` `text2` (dialog actions: `label` `accent`) |
| Link | 48 target | none | `labelSmall` 14 sp `accent` + `chevron_right` 18 dp |
| Destructive | as text or secondary | 1.5 dp `danger` when outlined | `danger` |
| Disabled (any) | same | `surface2`, no border | `textDisabled` |
Radius 12; icon 20 dp, 8 dp gap; padding 24 dp (12 small). **One primary per screen** (dialogs and sheets count as their own screen).

### 12.15 List row, switch, slider, radio, text field
- Row: 64 / 56 dp; icon 24 dp `text2` at x = 16; text at x = 56; value `body` `text2`; chevron 20 dp `text3`; divider 1 dp `line` inset 56 (every row, consistently).
- Switch 52 × 32 dp. Off: `bg` track, 2 dp `outline` inset, 16 dp `outline` thumb at x = 8. On: `accent` track, 24 dp white thumb at x = 24. Whole row is the target.
- Slider row: 48 dp, from x = 56; track 4 dp `line`, active `accent`, thumb 20 dp `accent`; value `numTag` `text2` in 28 dp; disabled `textDisabled`. Steps of 5.
- Radio 20 dp: 2 dp `outline` ring; selected 2 dp `accent` ring + 10 dp `accent` dot. Row 48 dp.
- Text field 56 dp, `surface2`, radius 12, 16 dp padding, `sans` 18 sp; focused 2 dp `accent` inset, caret `accent`; error 2 dp `danger` inset + `caption` `danger` below; counter `numTag` right.

### 12.16 Chat
- Sheet from y = 136 dp, `surface3` on `scrim`; header 48 dp: "채팅" `title`, "전체 채팅 끄기" + switch, close.
- Message: name line (`micro` `text2`, team badge 16 dp + name + `#tag`) over the bubble; bubble 10 × 12 dp padding, radius 12, max width 80 %. Others: `surface2` / `ink`. **Mine: `accentTint` / `ink`**, right-aligned, time `caption` 12 sp `text3`.
- System line: centred `caption` `text3`, max 2 lines, no bubble (codes and copy unchanged: `player_joined`, `player_left`, deleted subject "탈퇴한 플레이어가 …", unknown "방에 변화가 있어요").
- Masked words `***`, tracking 0.1 em, **`text2` in both bubbles** (7.13 on `surface2`, 6.88 on `accentTint`). TalkBack "가려진 말".
- Long-press: held state (2 dp `accent` ring) + action sheet (복사, 메시지 신고, 이 플레이어 차단 in `danger` with subtitle).
- Quick-phrase chips: 32 dp pills, `surface2`, `labelSmall` `ink`, 12 dp padding, 8 dp gap, horizontal scroll with a 16 dp fade; disabled (rate limit) `surface1` / `textDisabled`.
- Composer: 48 dp field `surface2` radius 12 + 48 dp `accent` send button (disabled `surface2` / `textDisabled`). Rate-limit line: `timer` 16 dp + `caption` `strike`.
- Muted: centred empty state "채팅을 꺼 두었어요" + text button "켜기".

### 12.17 Sheets, dialogs, toasts, banners
- Bottom sheet: `surface3`, top radius 16, grab handle 32 × 4 dp `dotOff` 12 dp from the top, no shadow, `scrim` behind; dismiss by drag, scrim tap or back unless noted.
- Dialog: 24 dp side margins (max 400), radius 16, padding 24 / 24 / 16, `title`, body `body` `text2` 12 dp below, actions 24 dp below (text buttons `label` `accent`, destructive `danger`), right-aligned. Alert dialogs cannot be dismissed by scrim tap.
- Overflow menu (`more_vert`): `surface3`, radius 12, 1 dp `line` border instead of a shadow, rows 48 dp `body` `ink` (destructive `danger`), 8 dp vertical padding.
- Toast: 16 dp margins, 16 dp above the keypad or bottom bar, min 48 dp, radius 12, **`inverse`** fill, `body` `onInverse`, action `labelSmall` `inverseAccent`; 2.8 s; never covers the slots.
- Connection banner: full-width `surface2` band 8 dp under the progress line (under the top bar elsewhere), padding 10 × 16, 12 dp gaps: three 8 dp chase dots · `bodyStrong` title + `caption` `text2` line · countdown `numTimer` `strike`; pushes content down.

### 12.18 Empty, error, offline, reconnecting
| State | Presentation |
| --- | --- |
| Empty log | Legend box (12.8) |
| Empty room team | Empty seats (12.11) |
| No blocked players | centred `caption` `text3` "차단한 플레이어가 없어요" 48 dp below the top bar |
| Offline at Home | 48 dp `surface2` band under the top bar: `cloud_off` 20 dp + "오프라인 · 컴퓨터 대결만 할 수 있어요" `caption`; 방 만들기 / 코드로 참가 tiles disabled (`textDisabled` icon and label); quick-match button disabled, label "연결을 기다리는 중"; no banner |
| Reconnecting (match) | Connection banner, clock paused, keypad locked |
| Opponent disconnected | Opponent row status "연결 끊김 · 0:41" `strike`; keypad usable |
| Server error | Dialog "문제가 생겼어요" / "잠시 후 다시 시도해 주세요." / 홈으로 · 다시 시도 |
| Timeout forfeit | Result with sub "시간 초과 2회로 기권했어요" |
| Window too small (< 320 × 320) | Full-window notice: brand mark 32 dp, "창을 조금 더 크게 해 주세요" `title`, "게임은 그대로 기다리고 있어요" `caption` `text3`, centred on `bg` |

### 12.19 Home mode tile (`ModeTile`)
- 3 equal tiles, 8 dp gap, 84 dp min, radius 12, `bg` + 1.5 dp `line` border; icon 24 dp `ink` over label `labelSmall` 14 sp `ink` (max 2 lines, centred, 6 dp gap). Disabled (offline): icon and label `textDisabled`.

### 12.20 Result summary (`ResultSummary`)
- Full width, 1 dp `line` top and bottom, 14 dp vertical padding, 3 equal columns separated by 1 dp `line`: value `numSummary` `ink` (units in the same run: "6회", "13승") over label `caption` `text3`.

### 12.21 Ad banner slot (`AdBand`) and the 광고 제거 line
See section 13.

---

## 13. Ads

Placement rules from the README and the release lead (unchanged in v2 except where the 광고 제거 entry sits):

- **Where (banner):** Home, duel and team results, Profile. **Never** on match, secret entry, waiting practice, quick-match search, room lobby, chat, Terms gate, Settings, dialogs, or any sheet.
- **Anchored adaptive banner** at the bottom, above the navigation inset: `AdBand` = 1 dp `line` top + `surface1` band whose height = the ad's height (50–90 dp); creative centred; navigation-bar padding goes below the band, filled with `bg`.
- **Clear space:** nothing tappable within **24 dp** above the band — measured from the band to the bottom of the nearest **touch target** (including the 광고 제거 line's 48 dp target). Nothing scrolls under the band.
- **Loading / no fill:** the band is not in the layout (0 dp). When an ad loads, the band expands in `medium` 240 ms (`decelerate`); taps on controls that moved are ignored for 300 ms (`settle_guard`). Fails or closes: collapses the same way.
- **Small windows:** height < 480 dp: no banner requested or shown.
- **One ad per moment:** a result reached through an interstitial shows no banner (shots `result-cpu-lose-ko`, `result-link-ko/en`).
- **Interstitial (owner decision 2026-11-25: vs computer and online)** between the end of a match and its results screen, with all safeguards of [`screens/06-results.md`](screens/06-results.md) §3 (≥ 1 s hold with no touch down, skip when chat / IME open or the player left, 1 per 3 finished matches and ≥ 4 min apart, one per team match, never in the review room, practice, small windows, or with `remove_ads`). Ads are always muted; music pauses.
- **Remove ads (owner decision 2026-11-25):** one-time Play purchase `remove_ads`. Entry points only in Settings ("구매", [`screens/11-settings.md`](screens/11-settings.md) §1.1) and **v2: a quiet "광고 제거" line directly above the banner area on the duel and team result screens** (no longer in the top bar): `caption` `text3`, centred, 48 dp target, ending 24 dp above the band; on a result reached through an interstitial (no band) it sits in the same place with 0 dp below it. Never a pop-up, dialog, toast, badge, colour or animation; never on match, secret, waiting, search, lobby, chat or the live board. Hidden while billing is unavailable, a purchase is pending, in the review room, and in windows < 480 dp. Once owned: no banner, no interstitial, no UMP form, no line (its 24 dp space stays as bottom padding). Price is always Play's formatted price.
- When a dialog is open on Home the banner stays where it is under the scrim.

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

Android resource folders that make this happen without code: `values` = English (default); `values-ko`, `values-ja`, `values-b+zh+Hans` (not `values-zh`, so Traditional locales fall through to English), `values-es`, `values-fr`, `values-de`, `values-pt` (Brazilian copy, so pt-PT matches it), `values-ru`, `values-vi`. Plurals use `<plurals>` in every language (Russian 3/4 цифры vs 5/6 цифр). The bundled Inter subset only renders digits and Latin letters, so no language depends on it.

### 14.2 Copy rules

- Korean first: short 해요체, no exclamation marks except in quick phrases and the result headline area ("승리" has none). Titles are statements, not questions, except confirmations.
- Numbers always in `num`; units in `sans` next to them ("4자리", "60초").
- Never "Excel", "Mastermind", "Numer0n", "ad-free". Never show real player names in screenshots.
- The app name is a set of resources (owner decision 2026-11-21): `app_name` = "숫자야구: 조용한 한판" / "Number Baseball: Quiet Duel" (full, localized per `docs/play/STORE_LISTING.md`), split for the UI into `app_name_main` ("숫자야구") and `app_name_sub` ("조용한 한판"). Home and the first-launch screens show main (`subtitle`) over sub (`caption` `text3`) next to the 32 dp mark; the launcher label uses `app_name_main`. No brand word is baked into the icon, artwork or store graphics; store text comes from [`store/store-text.json`](store/store-text.json).
- Minimum age 14 (owner decision), checked once by the neutral birth-year screen (01 §0). The style stays adult-neutral; no copy mentions the age number.

---


---

## 15. Brand mark and icon

- **In-app mark (v2):** a 32 × 32 dp `ink` plate, radius 8, holding the white **3** from the icon (same outline path) above three 1.7 dp-radius `strike` dots, "three strikes" = solved. Used in the Home top bar, the first-run eyebrow, the not-available screen (48 dp, 55 % opacity) and the window-too-small notice. Prototype: `proto.js` `mark()`; app: one vector drawable `ic_brand_mark.xml`.
- **Launcher icon and store art** ([`icon/`](icon/), [`store/`](store/)) are **unchanged** in v2: the dark number-plate icon still reads well on every launcher and keeps the store listing stable. A light icon variant is a separate decision for the owner (open question in REDESIGN_V2 §6).

---

## 16. Android implementation notes

- **Fonts:** copy `design/fonts/inter_*.ttf` to `app/src/main/res/font/` and delete the three `barlow_condensed_*.ttf`. `val Num = FontFamily(Font(R.font.inter_medium, FontWeight.Medium), Font(R.font.inter_semibold, FontWeight.SemiBold), Font(R.font.inter_bold, FontWeight.Bold))`; every `num` style `fontFeatureSettings = "tnum"`. Replace the Barlow entry in the licences screen with Inter (OFL 1.1, `OFL-Inter.txt`).
- **Theme:** `lightColorScheme(...)` built from `NbColors` (primary = `accent`, onPrimary = `onAccent`, background / surface = `bg`, surfaceContainer = `surface2`, outline = `outline`, outlineVariant = `line`, error = `danger`, scrim = `scrim`); `tonalElevation` and `shadowElevation` 0 everywhere; system bar icons dark.
- **Text style mapping:** unchanged (`includeFontPadding = false`, centred line height, tracking in em).
- **Edge-to-edge, window size:** unchanged (`enableEdgeToEdge()`, `currentWindowAdaptiveInfo()`, compact < 480 dp).
- **Chips:** `Box(Modifier.heightIn(min = 28.dp * capScale).widthIn(min = 44.dp).background(..., RoundedCornerShape(6.dp)).border(...))`; the OUT chip spans both hint slots (`widthIn(min = 94.dp)`).
- **Ruled-out key mark:** `drawWithContent { drawContent(); rotate(-45f) { drawLine(text3, start, end, strokeWidth = 2.dp, cap = Round) } }`, 22 dp long, centred.
- **Fades:** log fade unchanged technique, 20 dp.
- **Shadows:** none. Remove every `Modifier.shadow` and the `shadow` token.
- **Memo:** `MemoBoard` content moves into a `ModalBottomSheet` (`MemoSheet`); its cell logic (`Memo.kt`) is unchanged.
- **Haptics:** unchanged helper.
- **Review:** Roborazzi renders are compared against `design/shots/` (regenerated 2026-11-26 for v2) at 360 × 800 (density 2) and 360 × 320. Every number here and in `screens/` is a defect if off by more than 1 dp; colours must match the token hex exactly.
