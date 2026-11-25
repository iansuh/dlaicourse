# 06 Results: duel and team

Prototype: `prototype/result.html` (`?outcome=win|lose|draw`, `?mode=online|cpu`, `?ad=after` = reached through an interstitial, `?ads=removed`), `prototype/team-board.html?state=final`. Shots: `result-win-ko.png`, `result-win-en.png`, `result-lose-de.png`, `result-cpu-lose-ko.png` (after an interstitial), `result-cpu-win-ko.png`, `result-link-ko.png` / `result-link-en.png` (online, after an interstitial: link, no banner), `team-final-ko.png`.

**Purpose:** a clear verdict, the opponent's number, a quick look back, then the next game. Results are a calm screen, so the banner ad is allowed here (except right after an interstitial) and the quiet "광고 제거" link lives here.

## 1. Duel result layout (360 × 800)

| Region | Spec |
| --- | --- |
| Background | win: `strikeTint` wash from the window top, fading to transparent at 360 dp; lose / draw: plain `bg` |
| Top bar | 56 dp: `close` (= Home) · empty title · **"광고 제거" / "Remove ads" text button** (DESIGN_SYSTEM 12.13 Text: `labelSmall` `text2`, 36 dp visual, 48 dp target, 8 dp side padding; it ends where the `flag` button starts, or 4 dp from the edge when there is no `flag`) · online only: `flag` icon button (`text2`) opening the player card (12) |
| Hero | padding 8 / 16 / 24, centred, 12 dp gaps: n lamps 12 dp, gap 6 (win: all lit strike; lose: all unlit; draw: one chalk lamp); headline `display` (승리 / 패배 / 무승부); sub `body` `text2` ("6회 만에 상대 숫자를 맞혔어요" / "상대가 6회에 먼저 맞혔어요" / "6회에 둘 다 맞혔어요") |
| Reveal | centred column, 16 dp gap: "상대 숫자" `caption` `text3` over slots 52 × 60 dp (digits 32 sp); win: `hit` state (amber plates, `onChalk` digits); then "내 숫자" over smaller plates 40 × 44 dp (`surface1`, `numLog`, `text2`) |
| Line score | 28 dp below, 16 dp margins, 1 dp `line` top, 12 dp padding: header row "회별 스트라이크" `labelSmall` `text3` left, `timer` 16 dp + "3:12" `numTag` `text3` right (match duration); table: label column 80 dp (나 / opponent name, `caption` `text2`, ellipsis), one 32 dp row per side, one column per round with the S count (`numLog`; 0 in `textDisabled`; the solving count n in `strike`), last column "최고" 56 dp right-aligned (`numHint`, `nS`, amber when n). Round header numbers `numLabel` `text3`. Rows separated by 1 dp `line`. More than 9 rounds: the table scrolls horizontally, starting scrolled to the end, 16 dp left fade |
| Scroll | hero → line score is **one vertical scroll region** (16 dp bottom padding, 16 dp bottom fade when it scrolls); actions and band are pinned below it. At 360 × 800 with a 50 dp banner the region is about 10 dp short, so the last line-score row sits under the fade: accepted (phase 2 review) |
| Actions | padding 16 / 16 / 24: primary "다시 하기"; secondary "홈으로"; 8 dp gap |
| Ad band | when ads are on and this screen was **not** reached through an interstitial (DESIGN_SYSTEM 13) |

**"광고 제거" link rules.** Shown on every duel result and on the final team result while the player does not own `remove_ads` and Play Billing answered with the product (hidden while billing is unavailable, while a purchase is pending, in the review room, and in windows shorter than 480 dp where the top bar has no room). It never moves, blinks, gets a badge or changes colour; there is no dialog, toast or sheet that advertises it, and it never appears on any match, waiting or board-while-live screen. Tap: launches the Play purchase sheet directly (Play shows the price and terms); the result stays underneath. Success: toast "광고를 제거했어요", the banner collapses (240 ms) and the link disappears. Cancelled by the user: nothing. Error: toast "구매를 마치지 못했어요. 다시 시도해 주세요". Pending: toast "결제가 끝나면 광고가 사라져요" and the link hides until it resolves. TalkBack: "광고 제거, 구매" (role button).

Line score vs Computer labels the second row "컴퓨터" (`cpuShort` resource).

## 2. Variants

| Variant | Differences |
| --- | --- |
| Win | tint, lit lamps, amber opponent plates, `result_headline(win)` haptic and cue |
| Lose | no tint, unlit lamps, plain plates, my best S count in the line score in chalk |
| Draw | one chalk lamp blink, headline 무승부, both "최고" cells amber |
| Forfeit (me) | headline 패배, sub "기권했어요" or "시간 초과 2회로 기권했어요" |
| Forfeit (opponent) | headline 승리, sub "상대가 기권했어요" / "상대가 돌아오지 않아 이겼어요"; opponent plates show their number |
| Quick match | 다시 하기 = new quick match with the same digit count (goes to 04); `flag` shown |
| Private room 1:1 | primary "방으로 돌아가기" (07), secondary 홈으로 |
| vs Computer | 다시 하기 = same settings; no `flag`; interstitial rules below |

## 3. The interstitial: vs computer and online (owner decision 2026-11-25; shots `result-cpu-lose-ko`, `result-link-ko/en`)

The interstitial now follows **any finished match, vs computer or online**, and sits on the way **to** the results screen (README; `docs/play/POLICY_CHECKLIST.md` 11.1 and 11.2). This replaces the earlier "result first, ad on leaving, vs computer only" rule and its note "다음 화면 전에 짧은 광고가 나와요", which is removed.

Sequence when an ad is due:
1. The match ends on the match screen as usual: the final row's lamps light (DESIGN_SYSTEM 8.3 C up to the cross-fade, or the opponent-solved / forfeit line). The keypad is already locked.
2. **Hold** on that frame until both are true: at least **1000 ms** since the result was decided, and no pointer is down on any control (wait for the last touch up, then 300 ms). Chat sheet, phrase sheet or IME open → **skip** the ad for this match (it does not count toward the cap); never close them to make room for an ad.
3. If the player already left the screen in that time (back, `close`, leave dialog, app in background, opponent disconnect ended the screen): **skip**, never show it later.
4. Show the interstitial (muted; game music pauses). It covers a cross-fade, so no tap meant for the game can land on it.
5. When it closes (or fails to load: continue directly, no spinner, no placeholder screen), the results screen appears and plays its full hero animation from the start (DESIGN_SYSTEM 8.2 #2). It carries **no banner**, but it does carry the "광고 제거" link.

When is an ad due: cap 1 per 3 finished matches, ≥ 4 minutes since the last interstitial, online and vs computer counted together; a team match counts once and its ad comes only after the **whole** match (before the team result, never after my duel while others still play, never on the live board). Never: the review room and its bot games, the waiting practice game, a match that ended by the server restarting, a cancelled duel, windows shorter than 480 dp, or when the player owns `remove_ads`. Never while a rematch or the next team duel is about to start: the ad is before the results screen, and "다시 하기" / "방으로 돌아가기" on that screen never trigger one.

- Buttons on the results screen accept input at `result_ready` and never earlier than 1000 ms after the screen appears (with or without an ad before it).
- No interstitial due (shot `result-cpu-win-ko`): the result shows directly after the cross-fade, with the banner.
- Never on app start or exit, never during a match or the secret entry.

## 4. Team result (shot `team-final-ko`)

Same screen as the team board (08) in its final state:
- `strikeTint` wash if my team won.
- Hero: one 12 dp lamp per duel needed to win the match (`ceil(N / 2)` for odd N; N / 2 + 1 otherwise), lit for each duel my team won (2 of 3 lit in the shot); headline 승리 / 패배 / 무승부 (ties are a normal result: "팀 무승부").
- Score row and duel rows exactly as the board; the winner of each duel has the "승" tag.
- Top bar `close` · "팀 대전" · "광고 제거" text button (rules in §1) · `chat`.
- Actions: primary "방으로 돌아가기", secondary "홈으로". Banner allowed unless the team result was reached through an interstitial (§3).

## 5. Accessibility
- On entry TalkBack reads: headline, sub, then "상대 숫자 3 9 1 6".
- Lamps are decorative. The line score is a table with row and column headers ("6회, 나, 4 스트라이크").
- At fontScale ≥ 1.5 the line score shows only the "최고" column plus the last 3 rounds; the full table is behind "회별 기록 보기" (ADAPTIVE §2.4).
