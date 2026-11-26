# 07 Private room: create, join, lobby

Design system v2 (Clean White, 2026-11-26). Prototype: `prototype/lobby.html`. Shots: `lobby-ko.png`, `lobby-en.png`, `lobby-de.png`, `lobby-ru.png`.

Private rooms are for friends (1:1 or teams up to 5 vs 5). Free-text chat lives here. **No ads anywhere in this flow.** Terms gate (13) before the first create or join.

## 1. Create room (bottom sheet from Home)

| Element | Spec |
| --- | --- |
| Title row | "방 만들기" `title` · close |
| 자리수 | segmented tiles 48 dp, 3–6 (default last used, first time 4) |
| 인원 | segmented tiles 48 dp: 1:1 · 2:2 · 3:3 · 4:4 · 5:5 (labels Inter 600 16 sp) |
| 제한 시간 | segmented tiles 48 dp: 30초 · 60초 · 120초 · 느긋하게 (labels: number Inter + unit; the fourth reads "느긋하게" with a `caption` `text3` line under the control when selected: "한 회에 10분까지 생각할 수 있어요"); default 60초; there is no untimed option |
| Create | primary 56 dp "방 만들기"; 24 dp bottom + inset |

Creating puts the host in seat A1 and opens the lobby. The rules band shows the timer as "60초" or "느긋하게 (10분)".

## 2. Join with code (full screen)

| Element | Spec |
| --- | --- |
| Top bar | `arrow_back` · "코드로 참가" |
| Prompt | 24 dp top: "친구에게 받은 코드를 입력하세요" `title`, `caption` `text3` "영문 대문자와 숫자 5자리" |
| Code plates | 24 dp below, centred: 5 plates 48 × 60 dp, gap 8, radius 12, `numCode`; empty plate = `bg` + 1.5 dp `outline`; active = 2 dp `accent` inset; filled = `surface2` |
| Input | a hidden text field drives the plates; system keyboard with `KeyboardType.Ascii`, `KeyboardCapitalization.Characters`, autocorrect off; only `A–Z 0–9` accepted (excluding `O I` if the server excludes them, see open questions) |
| Paste | when the clipboard holds a 5-character code, a tonal 48 dp chip "붙여넣기 K7Q2M" appears under the plates |
| Join | primary 56 dp "참가" pinned above the IME, enabled at 5 characters; also triggered by the 5th character |

Errors (toast + plates shake + `dangerTint` fill and 2 dp `danger` inset on all plates): "방을 찾을 수 없어요" (not found), "방이 가득 찼어요" (full), "이미 대결이 시작됐어요" (started), "연결할 수 없어요" (network).

## 3. Lobby layout (360 × 800, 3 vs 3, 4 of 6 present)

| Region | Spec |
| --- | --- |
| Top bar | 56 dp: `arrow_back` (leave room → dialog) · "친구 방" `title` · `more_vert` (방 설정 (host), 게임 방법, 방 나가기) |
| Invite | padding 8 / 16 / 20, centred: "초대 코드" `caption` `text3`; 12 dp; five code plates 48 × 60 dp, gap 8, radius 12, `surface2`, `numCode` `ink`; 16 dp; two equal tonal 48 dp buttons, gap 8: `content_copy` "복사" · `share` "공유" (Android share sheet with the text "숫자야구 방 K7Q2M 에 들어와요: <link>") |
| Room rules row | 48 dp min, 16 dp margins, no fill, 1 dp `line` top and bottom: `tune` 20 dp `text2` · "4자리 · 3 대 3 · 60초" `bodyStrong` · host only: text button "변경" (48 dp target) |
| Fill status | padding 16 / 16 / 12: six 8 dp dots, 6 dp gap (one per seat: `ink` for present players, `dotOff` for open seats) · "4 / 6명" `bodyStrong`; next line "모두 모이면 바로 시작해요" `caption` `text3` (own line; it may wrap) |
| Teams | two columns, 8 dp gutter, 16 dp margins. Column header 36 dp: team badge 24 dp · "A팀" `subtitle` · "2/3" `numLabel` `text3`. Seats 56 dp min, 8 dp gap (DESIGN_SYSTEM 12.11) |
| Spacer | flexible |
| Practice | host only, 16 dp padding: tonal 48 dp (`surface2`) `memory` "기다리는 동안 연습하기" |
| Chat bar | 56 dp min, `bg`, 1 dp `line` top: `chat` `text2` · last message (sender `bodyStrong` `ink` + text `body` `text2`, one line, ellipsis) · unread badge (20 dp min, pill, `accent`, `onAccent` Inter 600 13 sp); tap opens chat (09) |

Seats (12.11): taken seats are `surface2` plates without stripes or borders; mine carries the solid `ink` tag "나"; the host carries the tag "방장" (`bg` fill on the seat). Empty seats are the only bordered element (1.5 dp `line`): on the other team "이 자리로 이동" with `swap_horiz` in `accent` (moves me there); on my team a `dotOff` dot + "빈 자리" in `text3` (does nothing). v2 removed the three v1 border treatments (outline, colour stripe, empty border) in favour of this one.

## 4. Behaviour
- **Auto start:** when all seats are filled, all fill dots turn `ink`, the status line reads "3초 후 시작해요" with a `numTimer` countdown, then every client opens the secret entry (05). Seat changes are locked during the countdown. If someone leaves during it, the countdown cancels.
- Teams must be equal size; the room size defines seats, so starting with uneven teams is impossible by construction.
- **Host leaves:** the next-joined player becomes host (tag moves, toast "방장이 바뀌었어요"). Room settings can only change while the room is not full.
- **Room settings (host):** same sheet as Create; reducing team size is disabled while it would remove seated players.
- **Long-press a seat (not mine):** player card (12).
- **Leave dialog:** "방에서 나갈까요?" / 취소 · 나가기.
- **Practice:** see 05 §2.11.
- After a team or 1:1 match, "방으로 돌아가기" returns everyone who chooses it to the same room and code; players who left free their seats.

## 5. States

| State | Presentation |
| --- | --- |
| Alone in the room | fill dots 1/N `ink`; status line plus a `caption` hint under Invite "코드를 친구에게 보내세요" |
| 1:1 room | teams shown as two single seats labelled "나" / "상대" without team badges |
| 5 vs 5 | 5 seats per column; the spacer disappears and the lobby scrolls between the rules band and the practice button (both stay pinned) |
| Reconnecting | connection banner (`surface2`) under the top bar; seat names `text3` |
| Player offline | seat name `text3` + tag "오프라인"; removed after 60 s |
| Long names (`lobby-de`, `lobby-ru`) | names ellipsize inside seats, tags never truncate; team header "Команда A" fits at 360 dp |

## 6. Accessibility
- The code is read as single characters: "초대 코드 K 7 Q 2 M".
- Seats: "A팀, 1번 자리, 새벽투수 4821, 나, 방장"; empty: "B팀 빈 자리, 이 자리로 이동하려면 두 번 탭".
