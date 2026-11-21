# 09 Chat and quick phrases

Prototype: `prototype/chat.html` (`?state=actions|limit`), `prototype/match.html?state=phrases`. Shots: `chat-ko.png`, `chat-actions-ko.png`, `chat-limit-en.png`, `match-phrases-ko.png`.

Text only: no images, no emoji (emoji are stripped by the server and the input filter), swear words masked. Chat is never an ad surface.

## 1. Where chat exists

| Context | What the player can send | Entry |
| --- | --- | --- |
| Quick match (strangers) | **Quick phrases only** (6 presets) | `chat` button in the duel strip → phrase sheet (05 §2.8) |
| Private room lobby | Free text + quick phrases | chat bar (07) |
| Private room match (1:1 or team) | Free text + quick phrases | `chat` button in the duel strip; team board top bar |
| vs Computer, tutorial | none | — |

Free text additionally requires: Terms accepted (13), at least one finished online match (new accounts see phrases only, with the composer line "온라인 대결을 한 판 끝내면 채팅을 쓸 수 있어요"), no active chat restriction.

## 2. Chat sheet (rooms), 360 × 800

| Region | Spec |
| --- | --- |
| Sheet | top at y = 136 dp, `surface3`, top radius 16, grab 32 × 4 dp; content behind dimmed by `scrim` |
| Header | 48 dp: "채팅" `title` (16 dp from left) · "전체 채팅 끄기" `labelSmall` `text2` + switch (the same setting as Settings) · `close` |
| Messages | 8 / 16 dp padding, 12 dp between messages, anchored to the bottom (newest last), scroll up for history (in-memory, last 50 lines) |
| Quick phrases | 1 dp `line` top; 8 / 16 dp padding; chips 32 dp (outline, radius 4, `labelSmall`), 8 dp gap, horizontal scroll with a 16 dp right fade |
| Composer | 4 / 4 / 8 / 16 dp padding: field 48 dp `surface2` radius 8, placeholder "메시지 (최대 80자)" `body` `text3`; send 48 × 48 dp chalk, radius 8, `send` icon `onChalk` |
| Inset | IME pushes the whole sheet; navigation inset below the composer |

Message anatomy (DESIGN_SYSTEM 12.15): name line `micro` with team badge 16 dp (team rooms) + nickname + `#tag` (no space); bubble hugs its text, max width 80 %, radius 8, padding 10 × 12, `body`. Others `surface4`; mine `chalk` with `onChalk` text, right-aligned, time under it (`caption` 12 sp `text3`, only on my last message and after 5 min gaps). System lines centred `caption` `text3` ("민트초코#0912 님이 들어왔어요", "방장이 바뀌었어요").

## 3. States

| State | Presentation |
| --- | --- |
| Empty | centred `caption` `text3` "아직 대화가 없어요. 빠른 문구로 인사해 보세요." above the chips |
| Masked word | rendered `***` in `text3`; TalkBack "가려진 말" |
| Rate limited (`chat-limit-en`) | line above the composer: `timer` 16 dp + "너무 빨라요. 3초 뒤에 보낼 수 있어요" `caption` `strike`, countdown updates; send button disabled (`surface2`, `textDisabled`); chips disabled too |
| Too long | counter `numTag` appears at 70/80 in `text3`, at 80/80 in `strike`; input stops at 80 |
| Rejected by server (filter) | my bubble shows `out` 1 dp outline + `caption` `out` "보낼 수 없는 내용이에요" under it |
| Chat restricted (24 h auto restriction) | composer replaced by a 56 dp band `surface2`: `block` 20 dp + "신고가 접수되어 채팅이 24시간 제한됐어요" `caption`; chips disabled |
| Muted (switch on) | messages and chips hidden; centred empty state `notifications_off` 24 dp `text3` + "채팅을 꺼 두었어요" `body` `text2` + text button "켜기"; incoming messages do not raise the unread badge |
| Blocked sender | their messages never render (filtered client-side as well as server-side) |
| Long-press message (`chat-actions-ko`) | message gets a 2 dp chalk ring; action sheet (`surface3`, radius 16): context "선택한 메시지" `caption` `text3` + "Größenwahn#7731 · 그건 좀 *** 아닌가" `bodyStrong`; rows 56 dp: `content_copy` 복사 · `flag` 메시지 신고 · `block` 이 플레이어 차단 (destructive, subtitle "채팅이 보이지 않고 다시 매칭되지 않아요"). My own messages offer only 복사 |
| Tap a name | player card (12) |

## 4. Quick phrases
- Presets (ko): 잘 부탁해요 · 좋은 게임! · 잠깐만요 · 어렵네요 · 한 판 더? · 수고했어요. Localized per language; the server sends phrase IDs, so each player sees phrases in their own language.
- In quick match they appear as transient bubbles (05 §2.8); in rooms they post into the chat like text.
- One phrase per 3 s (separate from the text rate limit).

## 5. Sound and haptics
`chat_message` cue at most once per 2 s while the sheet is closed, never during a hint reveal, never when muted (DESIGN_SYSTEM 10). No haptic for messages.

## 6. Accessibility
- Messages are read as "민트초코 0912: 잘 부탁해요" (mine: "나: …"); the list is a polite live region only while the sheet is open.
- The mute switch is labelled "전체 채팅 끄기" in both places.
- Long-press has an accessibility action "메시지 옵션".
