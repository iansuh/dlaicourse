# 14 Connection, offline and error states

Design system v2 (Clean White, 2026-11-26). Prototype: `prototype/match.html?state=reconnect`. Shot: `match-reconnect-ko.png`. Components: DESIGN_SYSTEM 12.17 (connection banner, dialog, toast) and 12.18.

Principles: never block the screen with a spinner; keep what can still work usable (memo, vs Computer); say what happens next and when, with a number.

## 1. Catalogue

| Situation | Where | Presentation | Copy (ko) |
| --- | --- | --- | --- |
| No network at launch | Home | `surface2` offline band under the top bar; 방 만들기 / 코드로 참가 tiles disabled; quick match button "연결을 기다리는 중"; no banner ad | "오프라인 · 컴퓨터 대결만 할 수 있어요" |
| Network back | Home | band collapses (240 ms); toast | "다시 연결됐어요" |
| Lost during quick-match search | Search | back to Home + toast | "연결이 끊겨 찾기를 멈췄어요" |
| Lost in the lobby | Lobby | connection banner (chase dots + countdown 60 s); seat names `text3`; chat send disabled | "연결이 끊겼어요 · 다시 연결하는 중" / "시간 안에 돌아오지 못하면 방에서 나가게 돼요" |
| Lost during a match (mine) | Match | connection banner; clock paused; keypad locked; Pitch "던지기" disabled; memo usable | "연결이 끊겼어요 · 다시 연결하는 중" / "시간 안에 돌아오지 못하면 이 대결은 기권 처리돼요" + `0:48` |
| Back within 60 s | Match | banner collapses; the round state is restored (including a pending reveal, played normally) | toast "다시 연결됐어요" |
| Not back within 60 s | Result | forfeit result | sub "연결이 끊겨 기권 처리됐어요" |
| Opponent disconnected | Match | opponent row status in `strike` with countdown; play continues | "연결 끊김 · 0:41" |
| Opponent did not return | Result | win | sub "상대가 돌아오지 않아 이겼어요" |
| App switched away and back | Match | same as reconnect if the socket dropped; otherwise nothing | — |
| Server error (5xx, unknown) | any | dialog (white, radius 16, scrim) with retry; actions `label` `accent` | title "문제가 생겼어요", body "잠시 후 다시 시도해 주세요.", actions 홈으로 · 다시 시도 |
| Server maintenance | Home | offline band variant with `schedule` icon | "점검 중이에요 · 컴퓨터 대결은 할 수 있어요" (+ end time if the server sends it: "오후 3시까지") |
| App too old (protocol mismatch) | launch | full-screen notice on `bg`, 32 dp mark at top, primary "업데이트" (`accent`) (Play Store), vs Computer still available as a text button | title "새 버전이 필요해요", body "온라인 대결을 하려면 앱을 업데이트해 주세요." |
| Room not found / full / started | Join | plates shake (`dangerTint` + 2 dp `danger`) + toast | "방을 찾을 수 없어요" · "방이 가득 찼어요" · "이미 대결이 시작됐어요" |
| Nickname rejected | First run | field error | "사용할 수 없는 닉네임이에요. 다른 이름을 써 주세요" |
| Chat rejected / restricted / rate-limited | Chat | 09 §3 | — |
| Ad failed to load | Home, results, profile | nothing (band stays collapsed); interstitial skipped silently | — |
| Terms acceptance failed | Terms gate | toast, stay | "연결할 수 없어요. 다시 시도해 주세요." |
| Window smaller than 320 × 320 dp | any | full-window notice on `bg` instead of the screen (DESIGN_SYSTEM 4.1, 12.18): 32 dp mark, `title`, `caption` `text3`; game state and online timers keep running | "창을 조금 더 크게 해 주세요" / "게임은 그대로 기다리고 있어요" |

## 2. Connection banner spec (match, shot `match-reconnect-ko`)
- Placed 8 dp under the progress line (directly under the top bar on screens without one), full width, pushes the opponent row and the rest down; never overlays the keypad.
- `surface2`, no border, padding 10 × 16 dp, 12 dp gaps: chase dots (3 × 8 dp, one `accent`, 4 dp gap) · text column (`bodyStrong` title, `caption` `text2` line, max 2 lines) · countdown `numTimer` `strike` (`m:ss`, whole seconds).
- The log keeps at least the latest row (the memo is a sheet in v2, so nothing collapses).
- Slots keep entered digits but show no active ring while the keypad is locked (DESIGN_SYSTEM 12.4).
- Small window (height < 480 dp): one line, 40 dp: chase dots · title · countdown; the second line moves to TalkBack only (ADAPTIVE §1).
- Live region: assertive on appear ("연결이 끊겼어요"), polite at 10 s left.
- Collapse 240 ms on reconnect.

## 3. Copy rules for errors
- Say what happened and what the player can do, in that order; no error codes in the UI (log them).
- Never blame the player ("잘못 입력했어요" → "올바른 연도를 입력해 주세요").
- Every countdown uses Inter (`numTimer`) and `m:ss`.
