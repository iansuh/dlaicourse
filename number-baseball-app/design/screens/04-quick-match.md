# 04 Quick match search

Prototype: `prototype/search.html` (`?state=found`). Shots: `search-ko.png`, `search-found-ko.png`.

**Purpose:** wait calmly for an opponent with the same digit count; change the count without leaving.

## Layout (360 × 800)

| Region | Spec |
| --- | --- |
| Top bar | 56 dp: `close` (cancels the search) · "빠른 대전" `title` |
| Digit picker | 8 dp top padding, 16 dp margins: "자리수 (바꾸면 다시 찾아요)" `labelSmall` `text3`, 8 dp; segmented 48 dp with the chosen count selected |
| Centre block | vertically centred in the remaining space, 20 dp gaps, 24 dp margins: four `?` plates (56 × 64 dp, `surface2`, radius 8, `numSlot`; the sweep lights one plate at a time in chalk, others `textDisabled`, 300 ms per plate); chase lamps (3 × 8 dp); "상대를 찾는 중" `title`; elapsed `numTimer` `text2` (`0:07`); "보통 30초 안에 찾아요. 다른 앱을 봐도 찾기는 계속돼요." `caption` `text3` |
| Actions | 16 / 16 / 24 dp padding: secondary 48 dp "찾기 취소" |

The number of `?` plates equals the chosen digit count (3–6, width rule from DESIGN_SYSTEM 12.4).

## States

| State | Presentation |
| --- | --- |
| Searching | as above; music (if on) switches to the match loop |
| Count changed | the search restarts immediately; elapsed resets to `0:00`; no confirmation |
| 60 s without a match | caption changes to "지금은 상대가 적어요. 조금만 더 기다리거나 컴퓨터와 연습해 보세요." and a tonal 48 dp button "컴퓨터와 대결" appears above Cancel (opens 03 with the same digit count; the search is cancelled) |
| Found (`search-found-ko`) | hook `match_found`: plates flip away; "VS" `numLabel` tracking 0.12 em `text3`; opponent name `headline` + tag `numTag` 22 sp; three 12 dp chalk lamps lit; "상대를 찾았어요" `title`; "곧 비밀 숫자를 정해요" `caption`; Cancel hidden; after 900 ms the secret entry (05) slides in |
| Offline | the screen is not reachable (Home disables the button); losing connection while searching shows the toast "연결이 끊겨 찾기를 멈췄어요" and returns to Home |
| App in background | search continues for up to 60 s; a found match then waits 15 s for the player to return (system notification is out of scope for v1; the match is cancelled without penalty if they do not return) |

## Rules
- Blocked players are never matched (server). 
- No chat here; no ads here.
- Back = cancel search (no dialog).
