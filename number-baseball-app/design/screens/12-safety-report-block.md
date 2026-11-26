# 12 Player card, report and block

Design system v2 (Clean White, 2026-11-26). Prototype: `prototype/report.html` (`?state=card|reasons|done|dialog`, `?rec=none|loading|fail`), `prototype/chat.html?state=actions`. Shots: `report-card-ko.png`, `report-card-norecord-ko.png`, `report-reasons-ko.png`, `report-done-ko.png`, `report-dialog-ko.png`, `chat-actions-ko.png`.

Play UGC policy tools: report a message or a player, block a player (hides their chat and avoids matching, both directions), mute all chat (Settings and chat header). Reports are reviewed within 24 hours (release lead).

## 1. Entry points

| From | Gesture | Opens |
| --- | --- | --- |
| Chat message (others) | long-press (500 ms, `LONG_PRESS` haptic) | message action sheet: 복사 · 메시지 신고 · 이 플레이어 차단 |
| Chat name line | tap | player card |
| Lobby seat (others) | long-press | player card |
| Team board row | long-press → choose player | player card |
| Match overflow (online) | 신고 | player card of the opponent |
| Duel result (online) | `flag` icon | player card of the opponent |
| Quick-phrase bubble | long-press | player card |

## 2. Player card (bottom sheet)

| Element | Spec |
| --- | --- |
| Grab handle | 32 × 4 dp |
| Identity | 8 / 16 / 16 dp padding: team badge 24 dp (team rooms only) · nickname `title` + tag `numTag` 18 sp; under it `caption` `text3` context ("B팀 · 온라인 대전 기록"; no team: "온라인 대전 기록"; when no record is shown, only "B팀" or nothing) |
| Record | 0 / 16 / 16: 승 / 패 / 무 values Inter 700 24 sp `ink` + `caption` `text3` labels, 20 dp gaps. **Any player's** overall online record (all modes together; vs-computer games never count) from `GET /api/v1/players/{id}` (protocol 1.1 §3.4), fetched when the card opens, cached for the card's lifetime |
| Actions | 1 dp `line` top; rows: `flag` 신고하기 + chevron (56 dp) · `block` 차단하기 in `danger` with subtitle "채팅이 보이지 않고 다시 매칭되지 않아요" (64 dp) |
| Bottom | 24 dp + navigation inset |

Record states (the sheet height changes by at most 2 dp between them, so nothing jumps):

| State | Record area |
| --- | --- |
| Loading | the three values as "–" in `textDisabled`, labels as normal; no spinner |
| Loaded | values in `ink` (shot `report-card-ko`) |
| Not available (`404 not_found`: deleted, banned or bot account, **or a block in either direction**) | one 48 dp line "기록을 볼 수 없어요" `caption` `text3`; the context line drops "온라인 대전 기록". The same words for every cause, so a block is never revealed (shot `report-card-norecord-ko`) |
| Failed (network, `429 rate_limited`) | "기록을 불러오지 못했어요" `caption` `text3` + text button "다시 시도" at the end of the line |
| My own card | my record (same call) |

The report and block rows work in every record state. Already blocked: the block row reads "차단 해제" in `ink` and the record shows the Not available line.

## 3. Report flow

**Reasons sheet** (`report-reasons-ko`): title "Größenwahn#7731 신고" `title`; "이유를 하나 골라 주세요" `caption` `text3`; 8 radio rows, 48 dp: 욕설이나 괴롭힘 · 혐오 표현 · 성적인 내용 · 개인정보 요구 또는 공개 · 스팸이나 광고 · 부정행위 · 아동 안전 문제 · 기타. Radio rows use the `accent` radio (DESIGN_SYSTEM 12.15). Optional note field 48 dp `surface2` radius 12 "자세한 내용 (선택, 최대 200자)". Evidence line `info` 16 dp + "신고하면 이 방의 최근 대화 20줄이 증거로 함께 저장돼요." `caption` `text3`. Primary "신고 보내기" (`accent`), disabled until a reason is chosen. A message report pre-selects nothing and shows the message as a context line above the reasons.

**Sent** (`report-done-ko`): white sheet with a 48 dp `surface2` circle and `check` in `ball`; "신고했어요" `title`; "보통 24시간 안에 확인해요. 결과는 따로 알려 드리지 않아요." `body` `text2`; secondary 56 dp outlined 1.5 dp `danger`, `danger` text: `block` "이 플레이어도 차단하기" (**block too**); tonal "닫기". Already blocked: only 닫기.

"아동 안전 문제" adds one line under the done text: "긴급한 위험이 있다면 경찰(112)에 바로 신고해 주세요." (localized emergency number per region resource).

## 4. Block flow

**Confirm dialog** (`report-dialog-ko`): title "Größenwahn#7731 님을 차단할까요?"; body "서로의 채팅이 보이지 않고, 빠른 대결에서 다시 만나지 않아요. 설정 > 차단한 플레이어에서 풀 수 있어요."; text buttons 취소 (`accent`) · 차단 (`danger`), `label`.

After blocking: toast "차단했어요" (`inverse`) with action "되돌리기" (`inverseAccent`, 2.8 s). Their past messages disappear from the chat at once; in a running match the duel continues (blocking does not forfeit), but their quick phrases are hidden.

## 5. Rules
- Reporting or blocking never reveals anything to the other player.
- Rate limits: 10 reports per day per account; the 11th shows "오늘은 더 신고할 수 없어요. 내일 다시 시도해 주세요."
- My own card (tapping my name) shows only the identity and record; no actions.
- vs Computer has no card.

## 6. Accessibility
Long-press has an equivalent accessibility action ("메시지 옵션", "플레이어 정보"). The radio list is a single-select group with the reason names as labels.
