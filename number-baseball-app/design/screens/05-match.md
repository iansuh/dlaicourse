# 05 Match: secret entry, guessing, reveal

Design system v2 (Clean White, 2026-11-26). Prototype: `prototype/match.html`. Query: `?state=secret|guess|waiting|late|phrases|reconnect|memo`, `?digits=3..6`, `?entry=39`, `?clock=warn|crit`, `?mode=cpu`, `?fs=2`, `?lang=en|de|ru`. Shots: `match-guess-ko/en/de/ru`, `match-secret-ko`, `match-waiting-ko`, `match-late-ko`, `match-6digits-ko`, `match-phrases-ko`, `match-reconnect-ko`, `match-memo-ko` (new), `match-small-ko/de`.

**Purpose:** the game. One thumb, no ads, and only what the next guess needs on screen. v2 simplifications: the memo grid lives in a sheet behind "메모" (the keypad shows ruled-out digits), no log header, only the latest row highlighted, opponent hints as small grey text, timer as a pill + thin progress line, "내 숫자" as one line above the slots.

## 1. Layout: guessing (360 × 800, 4 digits)

| Region | y (dp) | Height | Spec |
| --- | --- | --- | --- |
| Status bar | 0 | 24 | transparent, dark icons |
| Top bar | 24 | 56 | `close` (leave) · stack: round "4회" `subtitle` over "빠른 대전 · 4자리" `caption` `text3` · timer pill (DESIGN_SYSTEM 12.2, 4 dp after the stack) · `more_vert` |
| Progress line | 80 | 3 | 16 dp side margins, `line` track, `accent` fill (12.2) |
| Opponent row | 83 | 56 (52 min; 8 dp vertical padding included) | avatar 36 · name + tag over status (dot + caption) · `chat` button (online only) (12.3) |
| Log | 139 | flexible | 8 dp top padding, 16 dp sides; latest row 64 dp (`surface2`, radius 12) then 4 dp, older rows 46 dp with 1 dp `line` bottom border; newest first; 20 dp bottom fade when more rows exist (12.8) |
| Entry head | — | 40 | "내 숫자 0472" left · 메모 button right (12.5) |
| Slots | — | 4 + 64 + 12 | 4 plates 60 × 64 dp, 10 dp gap, centred (12.4) |
| Keypad | — | 52 + 8 + 52 + 8 + 52 | 5 × 2 digits + `⌫` · 던지기 (12.6); 12 dp bottom padding |
| Gesture area | 776 | 24 | navigation inset |

At 360 × 800 the log has room for **six rows** (latest + five) before it scrolls (shot `match-late-ko`); with three rounds it shows them with empty space below (shot `match-guess-ko`). There is no inline memo any more, so the log never collapses.

Log row grid (12.8): 8 dp row padding · round 22 · guess flex · hint chips auto (S chip, 6 dp, B chip; or the 94 dp OUT chip) · opponent hint min 44, right-aligned · 8 dp. Column gap 8 dp. n ≥ 5 (`match-6digits-ko`): chips 40 dp min, OUT 86 dp, guess tracking 0.08 em.

The opponent hint column shows the hint the opponent got on **my** number as plain `numLabel` `text3` ("1S 3B", "OUT"); on the latest row a small "상대" label (`caption` 11 sp `text3`) sits above it. No other column labels.

## 2. Phases and states

### 2.1 Secret entry (shot `match-secret-ko`)
- Top bar: "빠른 대전" (mode; vs Computer "컴퓨터 대전") over "4자리"; the timer runs with the same duration as a round. No 메모 button, no entry head.
- Opponent row status "상대가 숫자를 정하는 중" (`dotOff`) → "숫자를 정했어요" (`ball` dot).
- Log area: the **legend box** (12.8): "판정 읽는 법"; `1S` solid chip "**스트라이크** · 숫자와 자리가 모두 맞음"; `1B` outlined chip "**볼** · 숫자는 있지만 자리가 다름"; `OUT` chip "**아웃** · 맞는 숫자가 하나도 없음".
- Above the slots (16 dp top padding): "비밀 숫자를 정하세요" `title`, "서로 다른 숫자 4개 · 0으로 시작해도 돼요" `caption` `text3`, centred.
- Keypad action row: `⌫` · `shuffle` · "이 숫자로 정하기" (3 columns, `accent`). Disabled (`surface2` / `textDisabled`) until n digits. Used digits are `surface1` / `textDisabled`.
- After lock-in: slots lock, the key shows the waiting chase + "상대를 기다리는 중"; when both are ready the screen changes to guessing (legend fades out, entry head fades in, 240 ms).
- Timer expiry: a random secret is chosen and a toast says "시간이 지나 숫자를 무작위로 정했어요".

### 2.2 Guessing (shots `match-guess-*`)
- Type n distinct digits. A digit already in the slots disables its key (`used`), so duplicates cannot be entered; a key press when all slots are full plays `key_rejected`.
- Tapping a filled slot clears it and everything to its right.
- "던지기" enabled only when n digits are entered.
- Opponent status: "생각 중" (`dotOff`) → "던짐" (`ball` dot) the moment they pitch.
- Keys whose digit is **ruled out** in the memo show the diagonal stroke; **marked in** digits show the short ink bar (12.6). Ruled-out keys stay tappable.

### 2.3 Waiting for the opponent (shot `match-waiting-ko`)
- Slots locked (`surface1`, `text2`). Keypad locked (`surface1`, `textDisabled`, marks `textDisabled`). The pitch key shows the 3-dot chase + "상대의 투구를 기다리는 중" (`labelSmall` 14 sp `text2` on `surface2`).
- The 메모 button and the memo sheet stay fully usable while waiting.

### 2.4 Memo sheet (shot `match-memo-ko`)
- 메모 opens the memo sheet (DESIGN_SYSTEM 12.9) over the match: header "메모" · 지우기 · `close`; help line; digit status row + n position rows. Drag, scrim tap, back or `close` dismiss it.
- The timer keeps running. At normal text size the sheet (12 + 4 + 4 grab, 56 header, 26 help, 44 + n × 40 grid, 24 + inset; 346 dp + inset for n = 4) leaves the top bar and its timer visible; at large text the sheet header carries its own timer (ADAPTIVE §2.3).
- A reveal that arrives while the sheet is open plays normally underneath (sound and haptic as usual); the sheet stays open.
- Every change shows on the keypad marks at the same frame. The memo is local, per duel, cleared at the end.

### 2.5 Reveal (The Count) and near the end (shot `match-late-ko`)
Exactly DESIGN_SYSTEM 8.3 B: the new latest row slides in, the S chip appears and counts up, then the B chip; zero counts appear as plain `text3` text; 0S 0B shows the OUT chip. The keypad unlocks at `reveal_last` and the slots clear (150 ms). `match-late-ko` (store hero shot) shows round 7 with six rows: latest `3S 0B`, ruled-out keys 0 2 4 5 7 8, used 3 9 1.

### 2.6 Solve and result hand-off
- I solve: `solve` → latest row flash → result screen (06) per DESIGN_SYSTEM 8.3 C.
- Opponent solves in the same round: draw (both rows reveal, then result).
- Opponent solves first: the log still reveals my last row, then `opponent_solved`, then the result.
- Team match: after my duel ends, the screen goes to the team board (08) instead of the result, until all duels finish.

### 2.7 Pitch clock
- Options: 30 / 60 / 120 s or 느긋하게 (10 min); always present (no untimed rounds). Pill and progress line: normal (`ink` value, `accent` line), warn ≤ 10 s (`strike`), critical ≤ 5 s (`danger`, value pulses each second), paused while reconnecting (`textDisabled`). Prototype `?clock=warn|crit`.
- Expiry: the round is skipped for me (no guess); toast "시간이 지났어요. 이번 회는 넘어가요". Second consecutive miss forfeits the duel (README): result screen with "시간 초과 2회로 기권했어요".
- First miss also shows a one-time warning toast at the next round start: "한 번 더 시간이 지나면 기권 처리돼요".

### 2.8 Quick phrases (shot `match-phrases-ko`)
- Online modes only; the `chat` button in the opponent row opens a sheet (white, scrim): 56 dp header "빠른 문구" `title`, close (DESIGN_SYSTEM 12.17; the shot's header is shorter, 56 dp is correct); note "빠른 대전에서는 정해진 문구만 보낼 수 있어요" `caption` `text3`; a 2 × 3 grid of tonal 48 dp buttons (`surface2`, radius 12), 8 dp gaps, holding exactly these 6 protocol phrase ids in this order (ko / en): `good_luck` 잘 부탁해요 / Good luck · `good_game` 좋은 게임! / Good game! · `thinking` 잠깐만요 / One sec · `close_one` 아까워요 / So close · `rematch` 한 판 더? / Again? · `well_played` 수고했어요 / Well played. ("어렵네요" is gone: it has no protocol id.) Received phrases can be any of the 12 ids (09 §4) and are shown in the receiver's language; unknown ids are ignored.
- Sending closes the sheet and shows my phrase as a bubble under the opponent row for 2.8 s (`accentTint`, left-aligned). Rate limit: one phrase per 3 s; buttons disable during the cooldown.
- Receiving: bubble (opponent name `micro` `text2` + phrase `body`, `surface2`, radius 12, max width 240 dp) appears 8 dp below the opponent row, right-aligned, 2.8 s; it overlays the log, never the slots or keypad. Hidden when chat is muted or the sender is blocked.
- In private rooms the same button opens the full chat (09).

### 2.9 Overflow menu (`more_vert`)
게임 방법 (opens the tutorial in a sheet, match keeps running) · 메모 (same as the button; the only entry in compact windows besides `edit_note`) · 상대 기록 보기 (opponent's full log sheet) · 신고 (opens the player card 12; online only) · 기권 (`danger`).
- Small window only (ADAPTIVE §1): during secret entry the menu starts with 무작위로 채우기 / Fill randomly (the compact keypad has no `shuffle` key); in guessing it adds 내 숫자 보기 (the entry head is hidden there).
- 기권 → dialog "기권할까요?" / "이 대결은 패배로 기록돼요." / 취소 (`accent`) · 기권 (`danger`).
- `close` (leave) in an online match → dialog "대결에서 나갈까요?" / "60초 안에 돌아오지 않으면 기권 처리돼요." / 계속하기 (`accent`) · 나가기 (`danger`). vs Computer: leave without a dialog if no guess has been made; otherwise "나가면 이 판은 기록되지 않아요."

### 2.10 Connection
- My connection lost (shot `match-reconnect-ko`): full-width `surface2` connection banner 8 dp under the progress line (DESIGN_SYSTEM 12.17): chase dots, "연결이 끊겼어요 · 다시 연결하는 중", "시간 안에 돌아오지 못하면 이 대결은 기권 처리돼요", countdown `0:48` in `strike`. Timer pill and line paused (`textDisabled`), keypad locked, Pitch disabled ("던지기"), 메모 usable. Slots keep the digits already entered (`filled`) and show **no active ring** on the next slot while the keypad is locked (phase 2 decision: a ring on a locked keypad suggests input is possible). On reconnect the banner collapses (240 ms) and a toast "다시 연결됐어요" shows.
- Opponent disconnected: opponent row status "연결 끊김 · 0:41" in `strike` with a `dotOff` dot; I can keep pitching; if they do not return, result "상대가 돌아오지 않아 이겼어요".

### 2.11 Practice while waiting (room host)
Same screen vs Computer, plus a 48 dp min `surface2` band under the progress line: "방 K7Q2M · 3/6명 기다리는 중" `caption` + text button "방으로" (`labelSmall` `accent`, 48 dp target). When the room fills, a dialog-free transition: toast "인원이 다 모였어요. 대결을 시작해요", the practice ends without a result and the real secret entry opens. No ads ever.

## 3. Behaviour details
- Hardware keyboard: digits type, Backspace deletes, Enter pitches.
- Screen stays awake during a match (`FLAG_KEEP_SCREEN_ON`), released on the result screen.
- Rotation and resize keep entry, memo (and whether the memo sheet is open) and scroll position.
- The memo is local and cleared when the duel ends.

## 4. Accessibility
- Reading order: top bar → opponent row → log (latest first) → entry head (my number, 메모) → slots → keypad.
- Round result is announced as the log-row sentence (DESIGN_SYSTEM 11.2) after `reveal_end`.
- The keypad uses `Role.Button`; locked keys are `disabled()` in semantics.
- Memo cells: custom action "상태 바꾸기"; at fontScale ≥ 1.5 the transposed 48 dp sheet (ADAPTIVE.md). Hint chips are part of the row's sentence, not separate focus stops.

## 5. Variants by mode

| | Quick match | Private room 1:1 | Team | vs Computer |
| --- | --- | --- | --- | --- |
| Top bar subtitle | 빠른 대전 · 4자리 | 친구 방 · 4자리 | 팀전 A 1 : 0 B (tap opens board) | 컴퓨터 · 보통 · 4자리 |
| Clock | 60 s | host choice (30 / 60 / 120 s / 10 min) | host choice | setup choice (default 10 min) |
| Opponent row button | quick phrases | full chat | full chat (team + room) | none |
| Opponent avatar / tag | initial / yes | initial / yes | initial / yes | `memory` icon / none |
