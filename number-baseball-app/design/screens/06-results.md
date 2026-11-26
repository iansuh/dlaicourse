# 06 Results: duel and team

Design system v2 (Clean White, 2026-11-26). Prototype: `prototype/result.html` (`?outcome=win|lose|draw`, `?mode=online|cpu`, `?ad=after` = reached through an interstitial, `?ads=removed`, `?innings=1` = innings sheet open), `prototype/team-board.html?state=final`. Shots: `result-win-ko.png`, `result-win-en.png`, `result-lose-de.png`, `result-cpu-lose-ko.png` (after an interstitial), `result-cpu-win-ko.png`, `result-link-ko.png` / `result-link-en.png` (online, after an interstitial: 광고 제거 line, no banner), `result-innings-ko.png` (new), `team-final-ko.png`.

**Purpose:** a clear verdict, the opponent's number, three summary numbers, then the next game. v2 simplification: headline + opponent number + 3 summary numbers; the per-inning table moved behind "회별 기록 보기"; "광고 제거" moved out of the top bar to a quiet line above the banner; no amber wash.

## 1. Duel result layout (360 × 800, banner loaded)

| Region | Spec |
| --- | --- |
| Background | `bg` for every outcome (no tint) |
| Top bar | 56 dp: `close` (= Home) · empty · online only: `flag` icon button (`text2`) → player card (12) |
| Upper column | centred, 16 dp margins, 8 dp bottom padding; **one vertical scroll region** (20 dp bottom fade when it scrolls): |
| · Badge | 4 dp from the top bar: 64 dp circle; win: `accentTint` + `check` 32 dp `accent`; lose / draw: `surface2` + `sports_baseball` 32 dp `text2`. Decorative |
| · Headline | 12 dp below: `display` 승리 / 패배 / 무승부 |
| · Sub | 4 dp: `body` `text2` "6회 만에 상대 숫자를 맞혔어요" / "상대가 6회에 먼저 맞혔어요" / "6회에 둘 다 맞혔어요" (forfeit and timeout variants in §2) |
| · Opponent number | 16 dp: "상대 숫자" `caption` `text3`; 8 dp; n answer plates 60 × 68 dp, 10 dp gap, `ink` fill, `onInk` `numSlot` digits (width rule of 12.4 for n ≥ 5) |
| · My number | 12 dp: "내 숫자" `caption` `text3` + 6 dp + digits `numTag` 700, tracking 0.12 em, `text2` (one line) |
| · Summary | 16 dp: result summary (12.20; a record that is not cached yet shows "–" in `text3`; review v2 accepts that the cached count may lag by the match just played until the next `/me` refresh): **걸린 회** ("6회") · **시간** ("3:12", match duration) · **내 기록** (my online wins after this match, "13승"; vs Computer: **난이도** "보통", because computer games do not count in the record) |
| · Innings link | 0 dp: link "회별 기록 보기" + `chevron_right` (48 dp target) → innings sheet (§1.1) |
| Actions | pinned, 16 dp margins, 8 dp gap: primary "다시 하기" (56); secondary "홈으로" (56) |
| 광고 제거 line | pinned under the actions: `caption` `text3` "광고 제거" / "Remove ads", centred, 48 dp target; **24 dp clear space below its target** when the banner is present (0 dp when not) |
| Ad band | when ads are on and this screen was **not** reached through an interstitial (DESIGN_SYSTEM 13) |

Measured at 360 × 800 with a 50 dp band: everything fits without scrolling (upper column 444 dp of 453 dp available). Without the band (after an interstitial) the actions sit 75 dp lower and the upper column has room to spare.

### 1.1 Innings sheet ("회별 기록 보기", shot `result-innings-ko`)

Bottom sheet (white, scrim, DESIGN_SYSTEM 12.17): header 56 dp "회별 기록" `title` · `close`; table, 16 dp margins: label column 80 dp (나 / opponent name or "컴퓨터", `caption` `text2`, ellipsis); one 40 dp row per side, 1 dp `line` between; round header numbers `numLabel` `text3`; one column per round with the strike count (Inter 600 18 sp; **0 in `text3` weight 500** (faint-zero fix, AA); the solving count n in `strike` 700); last column "최고" 56 dp right-aligned (`nS`, `strike` when n). Under the table, right-aligned: `timer` 16 dp + "3:12" `numTag` `text3`. More than 9 rounds: the table scrolls horizontally, starting at the end, 16 dp left fade. TalkBack: a table with row and column headers ("6회, 나, 4 스트라이크").

### 1.2 "광고 제거" line rules

Shown on every duel result and the final team result while the player does not own `remove_ads` and Play Billing answered with the product (hidden while billing is unavailable, a purchase is pending, in the review room, and in windows shorter than 480 dp). When hidden or owned, its 48 dp row is removed and only the 24 dp clear space (with the band) or nothing (without) remains. It never moves, blinks, gets a badge, an icon or a colour; there is no dialog, toast or sheet that advertises it, and it never appears on a match, waiting or live-board screen. Tap: launches the Play purchase sheet directly; the result stays underneath. Success: toast "광고를 제거했어요", the band collapses (240 ms) and the line disappears. Cancelled: nothing. Error: toast "구매를 마치지 못했어요. 다시 시도해 주세요". Pending: toast "결제가 끝나면 광고가 사라져요" and the line hides until it resolves. TalkBack: "광고 제거, 구매" (role button). **Ad-safety:** the line is a tappable control, so it must keep 24 dp to the band (AdMob accidental-click guidance; DESIGN_SYSTEM 13); the clear space is measured from the bottom of its 48 dp target.

## 2. Variants

| Variant | Differences |
| --- | --- |
| Win | `accentTint` badge with `check`, `result_headline(win)` haptic and cue |
| Lose | neutral badge (`surface2`, `sports_baseball`) |
| Draw | neutral badge, headline 무승부; in the innings sheet both "최고" cells `strike` |
| Forfeit (me) | headline 패배, sub "기권했어요" or "시간 초과 2회로 기권했어요" |
| Forfeit (opponent) | headline 승리, sub "상대가 기권했어요" / "상대가 돌아오지 않아 이겼어요"; opponent plates show their number; 걸린 회 shows the rounds played |
| Quick match | 다시 하기 = new quick match with the same digit count (goes to 04); `flag` shown |
| Private room 1:1 | primary "방으로 돌아가기" (07), secondary 홈으로 |
| vs Computer | 다시 하기 = same settings; no `flag`; third summary value = 난이도; interstitial rules below |

## 3. The interstitial: vs computer and online (owner decision 2026-11-25; shots `result-cpu-lose-ko`, `result-link-ko/en`)

The interstitial now follows **any finished match, vs computer or online**, and sits on the way **to** the results screen (README; `docs/play/POLICY_CHECKLIST.md` 11.1 and 11.2). This replaces the earlier "result first, ad on leaving, vs computer only" rule and its note "다음 화면 전에 짧은 광고가 나와요", which is removed.

Sequence when an ad is due:
1. The match ends on the match screen as usual: the final row's chips count up (DESIGN_SYSTEM 8.3 C up to the cross-fade, or the opponent-solved / forfeit line). The keypad is already locked.
2. **Hold** on that frame until both are true: at least **1000 ms** since the result was decided, and no pointer is down on any control (wait for the last touch up, then 300 ms). Chat sheet, phrase sheet or IME open → **skip** the ad for this match (it does not count toward the cap); never close them to make room for an ad.
3. If the player already left the screen in that time (back, `close`, leave dialog, app in background, opponent disconnect ended the screen): **skip**, never show it later.
4. Show the interstitial (muted; game music pauses). It covers a cross-fade, so no tap meant for the game can land on it.
5. When it closes (or fails to load: continue directly, no spinner, no placeholder screen), the results screen appears and plays its full hero animation from the start (DESIGN_SYSTEM 8.2 #2). It carries **no banner**, but it does carry the "광고 제거" line (directly under the actions, no clear space needed).

When is an ad due: cap 1 per 3 finished matches, ≥ 4 minutes since the last interstitial, online and vs computer counted together; a team match counts once and its ad comes only after the **whole** match (before the team result, never after my duel while others still play, never on the live board). Never: the review room and its bot games, the waiting practice game, a match that ended by the server restarting, a cancelled duel, windows shorter than 480 dp, or when the player owns `remove_ads`. Never while a rematch or the next team duel is about to start: the ad is before the results screen, and "다시 하기" / "방으로 돌아가기" on that screen never trigger one.

- Buttons on the results screen accept input at `result_ready` and never earlier than 1000 ms after the screen appears (with or without an ad before it).
- No interstitial due (shot `result-cpu-win-ko`): the result shows directly after the cross-fade, with the banner.
- Never on app start or exit, never during a match or the secret entry.

## 4. Team result (shot `team-final-ko`)

Same screen as the team board (08) in its final state:
- `bg`, no wash. Hero: 64 dp badge (win `accentTint` + `check`; lose / draw `surface2` + `sports_baseball`), 12 dp, headline 승리 / 패배 / 무승부 ("팀 무승부" for ties). The v1 lamp row (one lamp per needed win) is gone: the score row says it.
- Score row and duel rows exactly as the board; the winner of each duel has the 승 tag.
- **Fit (review v2):** the rules caption under the score ("3 대 3 · 2승이면 승리") is hidden on the result (the result says it). With that, a 3 v 3 result shows all three duel rows at 360 × 800 with a 24 dp gesture inset and a 50 dp band; 4 v 4 and 5 v 5 rows scroll above the pinned actions (20 dp fade). The shot `team-final-ko` has no gesture area and still shows the caption.
- Top bar `close` · "팀 대전" · `chat` (no 광고 제거 in the top bar any more).
- Actions: primary "방으로 돌아가기", secondary "홈으로"; then the 광고 제거 line (§1.2); banner allowed unless reached through an interstitial (§3).

## 5. Accessibility
- On entry TalkBack reads: headline, sub, then "상대 숫자 3 9 1 6", then the summary ("걸린 회 6회, 시간 3분 12초, 내 기록 13승").
- The badge is decorative. The innings table (sheet) has row and column headers.
- At fontScale ≥ 1.5 the summary stacks to one value per line (label left, value right) and the innings sheet scrolls horizontally (ADAPTIVE §2.4).
