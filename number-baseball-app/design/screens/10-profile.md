# 10 Profile and stats

Design system v2 (Clean White, 2026-11-26). No prototype page; built from existing components (top bar, avatar, numerals, table, list rows, ad band). Opens from the Me row on Home.

**Purpose:** my record in detail and my name. A calm screen: the banner ad is allowed.

## Layout (360 × 800)

| Region | Spec |
| --- | --- |
| Top bar | 56 dp: `arrow_back` · "프로필" `title` |
| Identity | padding 8 / 16 / 20: avatar 48 dp · 12 dp · nickname `headline` + tag `numTag` 22 sp (capped) `text3`; 4 dp; link "닉네임 바꾸기" (`labelSmall` `accent`, 48 dp target; opens the nickname sheet, same rules as 01; tag stays) |
| Record | the Home record row (02): four columns 승 · 패 · 무 · 승률, `numStat` `ink` over `caption` `text3`, no dividers, 1 dp `line` under the row; 20 dp below the identity |
| Recent | removed in v2 (the form dots are gone from Home too); the by-mode rows below carry the detail |
| By digit count | section label "자리수별 기록" (40 dp header); a line-score style table, 16 dp margins: columns 자리수 (`labelSmall` `text3`) · 승 · 패 · 무 · 최소 회 (fewest rounds to solve, `numLog`); rows 3자리 … 6자리, 40 dp, 1 dp `line` between; values Inter 600 18 sp `ink`, **zero in `text3`** (AA), `–` in `text3` when no games |
| By mode | section label "모드별"; three 56 dp rows: 빠른 대전 · 친구 방 · 팀전, value "12승 8패 3무" (`numTag` numbers + `caption` units) right-aligned. vs Computer is listed separately as "컴퓨터 (기록 미포함)" with its own W/L, because computer games do not count in the online record |
| Spacer | flexible |
| Ad band | anchored adaptive banner, rules as Home |

## States
- No games: numerals `0`, win rate `–`, tables show `–`; a `caption` `text3` line "첫 경기를 기다리는 중" under the record.
- Offline: shows the last cached record with a `caption` `text3` line "오프라인 · 마지막으로 받은 기록" under the identity; nickname change disabled.
- Large font: the by-digit table drops the 최소 회 column; the record row wraps.

## Accessibility
Each table row is read as "4자리, 5승 3패 1무, 최소 5회".
