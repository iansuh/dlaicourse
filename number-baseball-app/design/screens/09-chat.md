# 09 Chat and quick phrases

Design system v2 (Clean White, 2026-11-26). Prototype: `prototype/chat.html` (`?state=actions|limit|system`), `prototype/match.html?state=phrases`. Shots: `chat-ko.png`, `chat-actions-ko.png`, `chat-limit-en.png`, `chat-system-ko.png` (join / leave / deleted-player lines, masked word in my bubble), `match-phrases-ko.png`.

Text only: no images, no emoji (emoji are stripped by the server and the input filter), swear words masked. Chat is never an ad surface.

## 1. Where chat exists

| Context | What the player can send | Entry |
| --- | --- | --- |
| Quick match (strangers) | **Quick phrases only** (the 6-phrase sheet, 05 §2.8) | `chat` button in the opponent row → phrase sheet (05 §2.8) |
| Private room lobby | Free text + quick phrases | chat bar (07) |
| Private room match (1:1 or team) | Free text + quick phrases | `chat` button in the opponent row; team board top bar |
| vs Computer, tutorial | none | — |

Free text additionally requires: Terms accepted (13), at least one finished online match (new accounts see phrases only, with the composer line "온라인 대결을 한 판 끝내면 채팅을 쓸 수 있어요"), no active chat restriction.

## 2. Chat sheet (rooms), 360 × 800

| Region | Spec |
| --- | --- |
| Sheet | top at y = 136 dp, `surface3` (white), top radius 16, grab 32 × 4 dp `dotOff`; content behind dimmed by the 40 % `scrim`; no shadow |
| Header | 48 dp: "채팅" `title` (16 dp from left) · "전체 채팅 끄기" `labelSmall` `text2` + switch (the same setting as Settings) · `close` |
| Messages | 8 / 16 dp padding, 12 dp between messages, anchored to the bottom (newest last), scroll up for history (in-memory, last 50 lines) |
| Quick phrases | 1 dp `line` top; 8 / 16 dp padding; all 12 phrase ids in protocol order (§4) as chips 32 dp visual / 48 dp target (pill, `surface2`, `labelSmall` `ink`, 12 dp padding), 8 dp gap, horizontal scroll with a 16 dp right fade |
| Composer | 4 / 4 / 8 / 16 dp padding: field 48 dp `surface2` radius 12, placeholder "메시지 (최대 80자)" `body` `text3`; send 48 × 48 dp `accent`, radius 12, `send` icon `onAccent` |
| Inset | IME pushes the whole sheet; navigation inset below the composer |

Message anatomy (DESIGN_SYSTEM 12.16): name line `micro` `text2` with team badge 16 dp (team rooms) + nickname + `#tag` (no space); bubble hugs its text, max width 80 %, radius 12, padding 10 × 12, `body` `ink`. Others `surface2`; mine `accentTint`, right-aligned, time under it (`caption` 12 sp `text3`, only on my last message and after 5 min gaps). System lines: see §2.1.

### 2.1 System lines (protocol 1.1 §9.3, shot `chat-system-ko`)

The server posts `kind: "system"` lines in **private rooms only** (never quick match or the review room). The client writes the sentence; `text`, `phraseId` and `sender*` are null.

| `systemCode` | ko | en |
| --- | --- | --- |
| `player_joined` | "{nickname}#{tag} 님이 들어왔어요" | "{nickname}#{tag} joined the room" |
| `player_left` | "{nickname}#{tag} 님이 나갔어요" | "{nickname}#{tag} left the room" |
| either, `subject*` null (account deleted) | "탈퇴한 플레이어가 들어왔어요" / "탈퇴한 플레이어가 나갔어요" | "A deleted player joined / left the room" |
| unknown code (future) | "방에 변화가 있어요" | "Something changed in the room" |

- Look: centred `caption` `text3`, max 2 lines (the name never ellipsizes inside a sentence: nicknames are at most 12 characters), no bubble, no name line, no time; 12 dp gaps like messages. The handle is plain `caption` (no Inter inside a translated sentence).
- Not long-pressable, no player card, no copy, not reportable (they are not in report snapshots either).
- They come in `chat` events and `chat_history` like messages and count toward the 50 lines. They are never sent about me to me, and are hidden when I and the subject blocked each other (the server does this; the client also drops lines whose `subjectId` is in my block list).
- Muted chat hides them with everything else; they never raise the unread badge or play `chat_message`.
- Lobby chat bar (07): when the newest line is a system line, the preview shows the sentence in `caption` `text3` with no sender.
- TalkBack: the sentence as is, in the polite live region.
- Host changes are **not** chat lines (no protocol code); the 방장 tag moving in the lobby is the only signal. "방장이 바뀌었어요" is removed.

## 3. States

| State | Presentation |
| --- | --- |
| Empty | centred `caption` `text3` "아직 대화가 없어요. 빠른 문구로 인사해 보세요." above the chips |
| Masked word | rendered `***` (tracking 0.1 em) in `text2` in both bubbles: 7.13 : 1 on `surface2`, 6.88 : 1 on `accentTint` (DESIGN_SYSTEM 2.2, 12.16). TalkBack "가려진 말" |
| Empty field | send button disabled (`surface2`, `textDisabled`); chips stay enabled (shots `chat-ko`, `chat-system-ko`) |
| Rate limited (`chat-limit-en`) | line above the composer: `timer` 16 dp + "너무 빨라요. 3초 뒤에 보낼 수 있어요" `caption` `strike`, countdown updates; send button disabled (`surface2`, `textDisabled`); chips disabled too (`surface1` fill, `textDisabled`) |
| Too long | counter `numTag` appears at 70/80 in `text3`, at 80/80 in `strike`; input stops at 80 |
| Rejected by server (filter) | my bubble gets a 1.5 dp `danger` outline + `caption` `danger` "보낼 수 없는 내용이에요" under it |
| Chat restricted (24 h auto restriction) | composer replaced by a 56 dp band `surface2`: `block` 20 dp + "신고가 접수되어 채팅이 24시간 제한됐어요" `caption`; chips disabled |
| Muted (switch on) | messages and chips hidden; centred empty state `notifications_off` 24 dp `text3` + "채팅을 꺼 두었어요" `body` `text2` + text button "켜기"; incoming messages do not raise the unread badge |
| Blocked sender | their messages never render (filtered client-side as well as server-side) |
| Long-press message (`chat-actions-ko`) | message gets a 2 dp `accent` ring; action sheet (white, radius 16, second scrim over the chat sheet): context "선택한 메시지" `caption` `text3` + "Größenwahn#7731 · 그건 좀 *** 아닌가" `bodyStrong`; rows 56 dp: `content_copy` 복사 · `flag` 메시지 신고 · `block` 이 플레이어 차단 (`danger`, subtitle "채팅이 보이지 않고 다시 매칭되지 않아요"). My own messages offer only 복사 |
| Tap a name | player card (12) |

## 4. Quick phrases
The protocol has 12 phrase ids (`docs/protocol.md` §9.2; `QuickPhrases.ALL`). Every id is localized in every language (all 해요체 in ko, DESIGN_SYSTEM 14.2); the server sends ids, so each player sees phrases in their own language. Unknown ids are ignored (not rendered).

| # | id | ko | en | Quick-match sheet (05 §2.8) |
| --- | --- | --- | --- | --- |
| 1 | `hello` | 안녕하세요 | Hi | |
| 2 | `good_luck` | 잘 부탁해요 | Good luck | 1 |
| 3 | `have_fun` | 재밌게 해요 | Have fun | |
| 4 | `nice_guess` | 좋은 추측이에요 | Nice guess | |
| 5 | `close_one` | 아까워요 | So close | 4 |
| 6 | `thinking` | 잠깐만요 | One sec | 3 |
| 7 | `oops` | 앗, 실수 | Oops | |
| 8 | `well_played` | 수고했어요 | Well played | 6 |
| 9 | `good_game` | 좋은 게임! | Good game! | 2 |
| 10 | `thanks` | 고마워요 | Thanks | |
| 11 | `rematch` | 한 판 더? | Again? | 5 |
| 12 | `bye` | 안녕히 가세요 | Bye | |

- Room chat chips: all 12, in table order (shot `chat-ko` shows the first four).
- Quick-match sheet: the 6 marked ids in the sheet order (one 2 × 3 grid, no scrolling, no "more").
- Bubbles and chat lines from other players: any of the 12.
- In quick match they appear as transient bubbles (05 §2.8); in rooms they post into the chat like text.
- One phrase per 3 s (separate from the text rate limit).

## 5. Sound and haptics
`chat_message` cue at most once per 2 s while the sheet is closed, never during a hint reveal, never when muted (DESIGN_SYSTEM 10). No haptic for messages.

## 6. Accessibility
- Messages are read as "민트초코 0912: 잘 부탁해요" (mine: "나: …"); the list is a polite live region only while the sheet is open.
- The mute switch is labelled "전체 채팅 끄기" in both places.
- Long-press has an accessibility action "메시지 옵션".
