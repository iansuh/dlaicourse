# 06 Results: duel and team

Prototype: `prototype/result.html` (`?outcome=win|lose|draw`, `?mode=online|cpu`, `?ad=notdue`), `prototype/team-board.html?state=final`. Shots: `result-win-ko.png`, `result-win-en.png`, `result-lose-de.png`, `result-cpu-lose-ko.png`, `result-cpu-win-ko.png`, `team-final-ko.png`.

**Purpose:** a clear verdict, the opponent's number, a quick look back, then the next game. Results are a calm screen, so the banner ad is allowed here (except when an interstitial is due).

## 1. Duel result layout (360 × 800)

| Region | Spec |
| --- | --- |
| Background | win: `strikeTint` wash from the window top, fading to transparent at 360 dp; lose / draw: plain `bg` |
| Top bar | 56 dp: `close` (= Home) · empty title · online only: `flag` icon button (`text2`) opening the player card (12) |
| Hero | padding 8 / 16 / 24, centred, 12 dp gaps: n lamps 12 dp, gap 6 (win: all lit strike; lose: all unlit; draw: one chalk lamp); headline `display` (승리 / 패배 / 무승부); sub `body` `text2` ("6회 만에 상대 숫자를 맞혔어요" / "상대가 6회에 먼저 맞혔어요" / "6회에 둘 다 맞혔어요") |
| Reveal | centred column, 16 dp gap: "상대 숫자" `caption` `text3` over slots 52 × 60 dp (digits 32 sp); win: `hit` state (amber plates, `onChalk` digits); then "내 숫자" over smaller plates 40 × 44 dp (`surface1`, `numLog`, `text2`) |
| Line score | 28 dp below, 16 dp margins, 1 dp `line` top, 12 dp padding: header row "회별 스트라이크" `labelSmall` `text3` left, `timer` 16 dp + "3:12" `numTag` `text3` right (match duration); table: label column 80 dp (나 / opponent name, `caption` `text2`, ellipsis), one 32 dp row per side, one column per round with the S count (`numLog`; 0 in `textDisabled`; the solving count n in `strike`), last column "최고" 56 dp right-aligned (`numHint`, `nS`, amber when n). Round header numbers `numLabel` `text3`. Rows separated by 1 dp `line`. More than 9 rounds: the table scrolls horizontally, starting scrolled to the end, 16 dp left fade |
| Spacer | flexible, min 16 dp |
| Actions | padding 16 / 16 / 24: primary "다시 하기"; secondary "홈으로"; 8 dp gap |
| Ad band | only when no interstitial is due (DESIGN_SYSTEM 13) |

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

## 3. vs Computer and the interstitial (shot `result-cpu-lose-ko`)

- The result always appears first. Buttons accept input at `result_ready` and never earlier than 1000 ms after the result screen appears.
- When an interstitial is due (cap: one per 3 finished vs-computer matches, ≥ 4 minutes apart): a centred note above the buttons, `info` 16 dp + "다음 화면 전에 짧은 광고가 나와요" `caption` `text3`; **no banner** on this screen.
- Tapping 다시 하기 or 홈으로 opens the interstitial (muted; game music pauses), then continues to the chosen screen. If the ad fails to load, the app continues directly; no spinner screen.
- Back / `close` count as 홈으로 (same rule).
- When no interstitial is due (shot `result-cpu-win-ko`): no note; the banner may show.
- Never after online matches, never on app start or exit.

## 4. Team result (shot `team-final-ko`)

Same screen as the team board (08) in its final state:
- `strikeTint` wash if my team won; top bar `close` · "팀 대전" · `chat`.
- Hero: one 12 dp lamp per duel needed to win the match (`ceil(N / 2)` for odd N; N / 2 + 1 otherwise), lit for each duel my team won (2 of 3 lit in the shot); headline 승리 / 패배 / 무승부 (ties are a normal result: "팀 무승부").
- Score row and duel rows exactly as the board; the winner of each duel has the "승" tag.
- Actions: primary "방으로 돌아가기", secondary "홈으로". Banner allowed (online result, no interstitial).

## 5. Accessibility
- On entry TalkBack reads: headline, sub, then "상대 숫자 3 9 1 6".
- Lamps are decorative. The line score is a table with row and column headers ("6회, 나, 4 스트라이크").
- At fontScale ≥ 1.5 the line score shows only the "최고" column plus the last 3 rounds; the full table is behind "회별 기록 보기".
