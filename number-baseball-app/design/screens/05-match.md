# 05 Match: secret entry, guessing, reveal

Prototype: `prototype/match.html`. Query: `?state=secret|guess|waiting|late|phrases|reconnect`, `?digits=3..6`, `?entry=39`, `?clock=warn|crit`, `?mode=cpu`, `?lang=en|de|ru`. Shots: `match-guess-ko/en/de/ru`, `match-secret-ko`, `match-waiting-ko`, `match-late-ko`, `match-6digits-ko`, `match-phrases-ko`, `match-reconnect-ko`, `match-small-ko/de`.

**Purpose:** the game. Everything needed for deduction on one screen, operable with one thumb, no ads, no clutter.

## 1. Layout: guessing (360 × 800, 4 digits)

| Region | y (dp) | Height | Spec |
| --- | --- | --- | --- |
| Status bar | 0 | 24 | transparent |
| Top bar | 24 | 56 | `close` (leave) · title stack: round "4회" `subtitle` over "빠른 대전 · 4자리" `caption` `text3` · pitch clock (`timer` 18 dp + `numTimer`, min width 38 dp) · `more_vert`; 2 dp clock line at its bottom edge |
| Duel strip | 80 | 52 | `VS` · opponent name + tag over status · quick-phrase button (online only) · "내 숫자" over mini plates (DESIGN_SYSTEM 12.3) |
| Log header | 132 | 28 | 1 dp `line` top; `#` · 내 추측 · 판정 (centred over S and B) · 상대 |
| Log rows | 160 | flexible | latest 64 dp (`surface1`, lamps), older rows 44 dp, newest first, scrolls, 16 dp bottom fade when more rows exist |
| Memo board | — | 32 + 32 + n × 28 + 8 | `surface1`, 1 dp `line` top (DESIGN_SYSTEM 12.8) |
| Entry | — | 12 + 64 + 12 + 172 + 12 | 1 dp `line` top; slots row; keypad 52 + 8 + 52 + 8 + 52 |
| Gesture area | 776 | 24 | navigation inset |

At 360 × 800 with n = 4 the log shows the latest row and two full rows (shot `match-guess-ko`). The log never gets less than the latest row + one row (108 dp); if it would, the memo board collapses to its digit row (shot `match-6digits-ko`).

Column grid of the log: 16 dp padding · 28 round · flexible guess · 44 S · 44 B · 72 opponent · 16 dp padding. n ≥ 5: S and B columns 52 dp and lamp gap 2 dp.

## 2. Phases and states

### 2.1 Secret entry (shot `match-secret-ko`)
- Top bar: title "빠른 대전" (mode) over "4자리"; the pitch clock runs with the same duration as a round.
- Duel strip: opponent status "상대가 숫자를 정하는 중" (unlit lamp) → "숫자를 정했어요" (lit ball lamp). My-secret plates are not shown yet.
- Log area: legend rows "판정 읽는 법" (DESIGN_SYSTEM 12.7): `1S` + lit strike lamp "스트라이크 · 숫자와 자리가 모두 맞음"; `1B` + lit ball lamp "볼 · 숫자는 있지만 자리가 다름"; `OUT` tag "아웃 · 맞는 숫자가 하나도 없음". 44 dp rows.
- Above the slots (24 dp top padding): "비밀 숫자를 정하세요" `title`, "서로 다른 숫자 4개 · 0으로 시작해도 돼요" `caption` `text3`, centred.
- Keypad action row: `⌫` · `shuffle` (fills the remaining slots with random unused digits) · "이 숫자로 정하기" (3 columns). Lock-in disabled until n digits.
- After lock-in: slots lock, the button shows the waiting chase + "상대를 기다리는 중"; when both are ready the screen transitions to guessing (log header fades in, 240 ms).
- Timer expiry in secret entry: a random secret is chosen for the player and a toast says "시간이 지나 숫자를 무작위로 정했어요".

### 2.2 Guessing (shots `match-guess-*`)
- Type n distinct digits. A digit already in the slots disables its key (`used`), so duplicates cannot be entered; a key press when all slots are full plays `key_rejected`.
- Tapping a filled slot clears it and everything to its right.
- "던지기" enabled only when n digits are entered.
- Opponent status: "생각 중" → "던짐" (lit ball lamp) the moment they pitch.

### 2.3 Waiting for the opponent (shot `match-waiting-ko`)
- Slots locked (`surface1`, `text2`). Whole keypad locked (`surface1`, `textDisabled`, marks dimmed). The Pitch key shows the 3-lamp chase + "상대의 투구를 기다리는 중" in `text2`.
- The memo board stays fully interactive while waiting.

### 2.4 Reveal (The Count)
Exactly DESIGN_SYSTEM 8.3 B. The keypad unlocks at `reveal_last`; the slots clear at the same moment (digits fade 150 ms), so the next guess can start while the last lamp is still igniting.

### 2.5 Near the end (shot `match-late-ko`)
Same layout; included because it is the store hero shot: latest row `3S 0B` with three lit strike lamps, memo board shows the deduced positions.

### 2.6 Solve and result hand-off
- I solve: `solve` → latest row flash → result screen (06) per DESIGN_SYSTEM 8.3 C.
- Opponent solves in the same round: draw (both rows reveal, then result).
- Opponent solves first: the log still reveals my last row, then `opponent_solved`, then the result.
- Team match: after my duel ends, the screen goes to the team board (08) instead of the result, until all duels finish.

### 2.7 Pitch clock
- Options: 30 / 60 / 120 s or 느긋하게 (10 min); always present (no untimed rounds). Normal (`chalk`), warn ≤ 10 s (`strike`), critical ≤ 5 s (`out`, value pulses each second), paused while reconnecting (`textDisabled`). Shots via `?clock=warn|crit`.
- Expiry: the round is skipped for me (no guess); toast "시간이 지났어요. 이번 회는 넘어가요". Second consecutive miss forfeits the duel (README): result screen with "시간 초과 2회로 기권했어요".
- First miss also shows a one-time warning toast at the next round start: "한 번 더 시간이 지나면 기권 처리돼요".

### 2.8 Quick phrases (shot `match-phrases-ko`)
- Online modes only; the `chat` button in the duel strip opens a sheet: "빠른 문구" `title`, close; note "빠른 대전에서는 정해진 문구만 보낼 수 있어요" `caption` `text3`; a 2 × 3 grid of tonal 48 dp buttons, 8 dp gaps, holding exactly these 6 protocol phrase ids in this order (ko / en): `good_luck` 잘 부탁해요 / Good luck · `good_game` 좋은 게임! / Good game! · `thinking` 잠깐만요 / One sec · `close_one` 아까워요 / So close · `rematch` 한 판 더? / Again? · `well_played` 수고했어요 / Well played. ("어렵네요" is gone: it has no protocol id.) Received phrases can be any of the 12 ids (09 §4) and are shown in the receiver's language; unknown ids are ignored.
- Sending closes the sheet and shows my phrase as a bubble under the duel strip for 2.8 s. Rate limit: one phrase per 3 s; buttons disable during the cooldown.
- Receiving: bubble (opponent name `micro` + phrase `body`, `surface4`, radius 8, max width 240 dp) appears 8 dp below the duel strip, right-aligned under the opponent's side, 2.8 s; it overlays the log, never the slots or keypad. Hidden when chat is muted or the sender is blocked.
- In private rooms the same button opens the full chat (09).

### 2.9 Overflow menu (`more_vert`)
게임 방법 (opens the tutorial in a sheet, match keeps running) · 상대 기록 보기 (opponent's full log sheet) · 신고 (opens the player card 12; online only) · 기권 (destructive).
- Small window only (ADAPTIVE §1): during secret entry the menu starts with 무작위로 채우기 / Fill randomly (the compact keypad has no `shuffle` key); in guessing it adds 내 숫자 보기.
- 기권 → dialog "기권할까요?" / "이 대결은 패배로 기록돼요." / 취소 · 기권 (`out`).
- `close` (leave) in an online match → dialog "대결에서 나갈까요?" / "60초 안에 돌아오지 않으면 기권 처리돼요." / 계속하기 · 나가기 (`out`). vs Computer: leave without a dialog if no guess has been made; otherwise "나가면 이 판은 기록되지 않아요."

### 2.10 Connection
- My connection lost (shot `match-reconnect-ko`): connection banner under the top bar (DESIGN_SYSTEM 12.16): chase lamps, "연결이 끊겼어요 · 다시 연결하는 중", "시간 안에 돌아오지 못하면 이 대결은 기권 처리돼요", countdown `0:48` in `strike`. Clock paused, keypad locked, Pitch disabled ("던지기"), memo usable. Slots keep the digits already entered (`filled`) and show **no active ring** on the next slot while the keypad is locked (phase 2 decision: a ring on a locked keypad suggests input is possible). On reconnect the banner collapses (240 ms) and a toast "다시 연결됐어요" shows.
- Opponent disconnected: duel strip status "연결 끊김 · 0:41" in `strike` with an unlit lamp; I can keep pitching; if they do not return, result "상대가 돌아오지 않아 이겼어요".

### 2.11 Practice while waiting (room host)
Same screen vs Computer, plus a 40 dp `surface3` band under the top bar: "방 K7Q2M · 3/6명 기다리는 중" `caption` + text button "방으로". When the room fills, a dialog-free transition: toast "인원이 다 모였어요. 대결을 시작해요", the practice ends without a result and the real secret entry opens. No ads ever.

## 3. Behaviour details
- Hardware keyboard: digits type, Backspace deletes, Enter pitches.
- Screen stays awake during a match (`FLAG_KEEP_SCREEN_ON`), released on the result screen.
- Rotation and resize keep entry, memo and scroll position.
- The memo is local and cleared when the duel ends.

## 4. Accessibility
- Reading order: top bar → duel strip → log (latest first) → memo → slots → keypad.
- Round result is announced as the log-row sentence (DESIGN_SYSTEM 11.2) after `reveal_end`.
- The keypad uses `Role.Button`; locked keys are `disabled()` in semantics.
- Memo cells: custom action "상태 바꾸기"; at fontScale ≥ 1.5 the transposed 48 dp sheet (ADAPTIVE.md).

## 5. Variants by mode

| | Quick match | Private room 1:1 | Team | vs Computer |
| --- | --- | --- | --- | --- |
| Top bar subtitle | 빠른 대전 · 4자리 | 친구 방 · 4자리 | 팀전 A 1 : 0 B (tap opens board) | 컴퓨터 · 보통 · 4자리 |
| Clock | 60 s | host choice (30 / 60 / 120 s / 10 min) | host choice | setup choice (default 10 min) |
| Duel strip button | quick phrases | full chat | full chat (team + room) | none |
| Opponent tag | yes | yes | yes | none (`memory` icon) |
