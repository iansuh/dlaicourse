# 03 vs Computer setup

Design system v2 (Clean White, 2026-11-26). No prototype page (it reuses the segmented tiles and sheet components). Opens from the Home tile 컴퓨터와.

**Purpose:** two choices and go. Works offline, never needs the Terms gate.

## Layout: bottom sheet (`surface3` white, top radius 16, 40 % `scrim`, no shadow)

| Element | Spec |
| --- | --- |
| Grab handle | 32 × 4 dp `dotOff`, 12 dp from top |
| Title row | 48 dp: "컴퓨터와 대결" `title`; close icon button right |
| Digits | label "자리수" `labelSmall` `text3` (8 dp above); segmented tiles 48 dp (DESIGN_SYSTEM 12.13, selected = `ink` fill): 3자리 / 4자리 / 5자리 / 6자리 |
| Level | 20 dp below: label "난이도"; segmented tiles 48 dp: 쉬움 / 보통 / 어려움; under it (8 dp) one `caption` `text3` line that changes with the selection: 쉬움 "가끔 실수하는 상대", 보통 "판정을 꼼꼼히 쓰는 상대", 어려움 "가능한 수를 모두 따지는 상대" |
| Timer | 20 dp below: list row 56 dp "제한 시간" with value "느긋하게 (10분) ›" (opens a 4-option radio sheet: 30초 / 60초 / 120초 / 느긋하게 (10분); default 느긋하게; no untimed option) |
| Start | 24 dp below: primary 56 dp "시작" (`accent`) |
| Bottom | 24 dp + navigation inset |

- Selections persist (last used). Default first time: 3자리, 보통, 느긋하게.
- Start → match secret entry (05) with "컴퓨터" as the opponent: the opponent row shows the `memory` icon 20 dp in the 36 dp avatar, name "컴퓨터", no chat button, no `#tag`; the top-bar caption reads "컴퓨터 · 보통 · 4자리".
- The computer "pitches" after a random 0.6–1.8 s delay so the waiting state is visible but short; at 어려움 the delay is 1.2–2.4 s.
- Practice from a room lobby (07) skips this sheet and uses the room's digit count with 보통.

## States
- Sheet in a small window: opens full height; segmented controls keep 48 dp; the level caption is hidden.
- Large font: tile labels may wrap to 2 lines; tiles grow to 64 dp.
