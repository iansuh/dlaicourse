# 10 Profile and stats

No prototype page; built from existing components (top bar, numerals, line-score table, list rows, ad band). Opens from the Home hero.

**Purpose:** my record in detail and my name. A calm screen: the banner ad is allowed.

## Layout (360 × 800)

| Region | Spec |
| --- | --- |
| Top bar | 56 dp: `arrow_back` · "프로필" `title` |
| Identity | padding 8 / 16 / 20: nickname `headline` + tag `numTag` 22 sp `text3`; 4 dp; text button "닉네임 바꾸기" (`labelSmall` `text2`, 48 dp target; opens the nickname sheet, same rules as 01; tag stays) |
| Record | the Home record row at larger size: `numScore` values for 승 / 패 / 무 with `caption` labels, 1 dp dividers, then win rate `numStat` `text2`; 20 dp below the identity |
| Form | "최근 10경기" + 10 lamps (as Home), 16 dp below |
| By digit count | section label "자리수별 기록" (40 dp header); a line-score style table, 16 dp margins: columns 자리수 (`labelSmall` `text3`) · 승 · 패 · 무 · 최소 회 (fewest rounds to solve, `numLog`); rows 3자리 … 6자리, 40 dp, 1 dp `line` between; values `numLog` chalk, zero in `textDisabled`, `–` when no games |
| By mode | section label "모드별"; three 56 dp rows: 빠른 대전 · 친구 방 · 팀전, value "12승 8패 3무" (`numTag` numbers + `caption` units) right-aligned. vs Computer is listed separately as "컴퓨터 (기록 미포함)" with its own W/L, because computer games do not count in the online record |
| Spacer | flexible |
| Ad band | anchored adaptive banner, rules as Home |

## States
- No games: numerals `0`, win rate `–`, tables show `–`, form row replaced by "첫 경기를 기다리는 중".
- Offline: shows the last cached record with a `caption` `text3` line "오프라인 · 마지막으로 받은 기록" under the identity; nickname change disabled.
- Large font: the by-digit table drops the 최소 회 column; the record row wraps.

## Accessibility
Each table row is read as "4자리, 5승 3패 1무, 최소 5회".
