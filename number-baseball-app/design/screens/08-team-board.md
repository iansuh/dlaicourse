# 08 Team match board

Prototype: `prototype/team-board.html` (`?state=final`, `?mine=live` = my duel still running). Shots: `team-board-ko.png` (my duel finished, watching others: `close`), `team-final-ko.png`.

**Purpose:** see every paired duel at a glance like a stadium line score: who leads, who is close, who is reconnecting.

## 1. Access
- During my duel: tap the top-bar title of the match ("팀전 A 1 : 0 B") → the board opens as a full-height sheet; close returns to my duel. Expanded width: permanent right panel (ADAPTIVE.md).
- After my duel ends and others are still playing: the board replaces the match screen (live state below). Back = leave the match dialog.
- When all duels end: final state = team result (06 §4).

## 2. Layout (live, 3 vs 3)

| Region | Spec |
| --- | --- |
| Top bar | 56 dp: leading icon depends on my duel: **running** → `arrow_back` (closes the board sheet, back to my duel; prototype `?mine=live`); **finished, others still playing** → `close` (= leave-the-match dialog; shot `team-board-ko`); **all finished** → team result (06 §4: `close` = Home). Then "팀 대전" · `chat`. No "광고 제거" link here while the match runs (only on the final result) |
| Score | padding 8 / 16 / 20; grid `1fr auto 1fr`, 12 dp gaps: left team badge A + "A팀" `subtitle`; centre score `numScore` (min width 32 dp each) with a colon (Barlow 600 32 sp `text3`), the leading or tied score in chalk, the trailing one in `text3`; right "B팀" + badge B |
| Rule line | "3 대 3 · 2승이면 승리" `caption` `text3`, centred, 16 dp below; for even sizes "4 대 4 · 3승이면 승리, 2 : 2는 무승부" |
| Duel rows | 1 dp `line` top; rows 72 dp (DESIGN_SYSTEM 12.11); my row `surface1` |
| Watching note | when my duel is over: 16 dp padding, 1 dp `line` top, centred `visibility` 20 dp + "대결이 끝났어요. 다른 대결을 보는 중" `caption` `text3`, directly under the last row |

Row status column (88 dp, centred): `R5` (`numLabel` `text3`) over one of:
- "진행 중" with a 6 dp chalk lamp (`caption` `text2`)
- "재접속 대기 0:41" in `strike` (2 lines allowed)
- "무승부" / "기권" (`caption` `text2`)
- nothing when finished (the winner side shows the "승" tag)

Under each name, for **every** duel row (not only mine): "최고" + best hint so far (`numTag`), the `OUT` tag when the best hint is 0S 0B (every guess was out), or the winner tag (lit 6 dp strike lamp + "승" `micro` `strike`). The losing side of a finished duel is in `text3`. Data: `DuelSummary.bestHintA / bestHintB` from every `room_state` (protocol 1.1 §7: most strikes, then most balls, over resolved rounds; never digits). Null (no resolved guess yet) → "최고 –" (dash `text3`), so the row keeps its shape; after an app update from a 1.0 server the fields may be absent: same "–". Updates arrive with each `room_state`; the hint text cross-fades (150 ms), no lamp animation.

## 3. Behaviour
- Rows update live; a finished duel's row plays no animation except the winner tag fading in (150 ms) and the score digit counting up (150 ms).
- Tapping a row other than mine opens a read-only spectator view of that duel (both logs side by side, no digits of either secret until the duel ends). Long-press a row → player card (12) for either player (choose in a 2-item menu).
- Order: rows keep the pairing order from the lobby (A1–B1, A2–B2, …).
- The match ends only when **all** duels have finished, even if the outcome is already decided, so every player gets their own result. The score updates as each duel ends. (If the backend decides to end early once the majority is reached, the board shows the remaining rows as "종료" and nothing else changes.)

## 4. States

| State | Presentation |
| --- | --- |
| 5 vs 5 | 5 rows × 72 dp = 360 dp; the board scrolls under a pinned score block |
| Draw possible (even sizes) | final headline "팀 무승부", hero lamps show the wins of my team out of N / 2 + 1 |
| Forfeit by disconnect | status "기권"; the remaining player's side gets the "승" tag |
| Spectating others while my duel is still on | not possible from the board sheet (it is my duel's overlay); only after my duel ends |

## 5. Accessibility
Each row is one element: "1번 대결, 새벽투수 4821 승리, 민트초코 0912 최고 2스트라이크 1볼, 7회"; null best: "최고 기록 없음". Score: "A팀 1, B팀 0, A팀 앞섬".
