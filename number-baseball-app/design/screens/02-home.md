# 02 Home

Design system v2 (Clean White, 2026-11-26). Prototype: `prototype/home.html` (`?ad=none`). Shots: `home-ko.png`, `home-de.png`, `home-ru.png`.

**Purpose:** see my record at a glance, start a quick match with one thumb, reach the other modes. v2 simplification: record = four numbers only (no form dots, no row subtitles), other modes = three tiles, one quick-match block at the bottom.

## Layout (360 × 800, top to bottom)

| Region | Spec |
| --- | --- |
| Status bar | 24 dp, dark icons |
| Top bar | 56 dp, `bg`: brand mark 32 × 32 dp at x = 16; 10 dp; name stack `app_name_main` "숫자야구" `subtitle` 17 sp over `app_name_sub` "조용한 한판" `caption` `text3` (each line ellipsizes; en "Number Baseball" / "Quiet Duel"); `help` and `settings` icon buttons (48 dp, `ink`), last one 4 dp from the edge |
| Body | 16 dp side margins, everything below until the band |
| Me | 16 dp top, 8 dp bottom: avatar 48 dp (initial "새") · 12 dp · nickname `subtitle` 17 sp + tag `numTag` `text3` over "내 기록" `caption` `text3`. The whole row is one button → Profile (10) |
| Record | 12 dp below: four columns spread across the width (4 dp inner padding): 승 · 패 · 무 · 승률; value `numStat` `ink` (승률 shows `52` + `%` at 0.7 em) over label `caption` `text3`; 20 dp bottom padding and a 1 dp `line` under the row |
| Spacer | flexible, min 16 dp |
| Hero | centred: four `?` plates 56 × 64 dp, 8 dp gap (question state, DESIGN_SYSTEM 12.4: `bg`, 1.5 dp `line`, `numSlot` `text3`); 12 dp; link "처음이라면 게임 방법 보기" + `chevron_right` (12.14 Link, 40 dp row, 48 dp target) → tutorial (01). Decorative plates are hidden from TalkBack |
| Spacer | flexible, min 16 dp |
| Other modes | label "다른 방법으로" `labelSmall` `text3` (4 dp left inset, 10 dp below); three mode tiles (12.19), 8 dp gap, 84 dp min: `memory` 컴퓨터와 · `group` 방 만들기 · `login` 코드로 참가 |
| Quick match | 24 dp below the tiles: head row "빠른 대전" `subtitle` left, "60초 · 비슷한 상대" `caption` `text3` right (baseline aligned, 4 dp inset, 10 dp below); digit tiles (12.13, 52 dp, 3자리 … 6자리, default **3** on first launch (unchanged from v1; the shots show a returning player whose last choice was 4), then last used); 12 dp; primary 56 dp "빠른 대전 시작" (no icon) |
| Clear space | 24 dp (`banner_clear_space`) |
| Ad band | anchored adaptive banner (DESIGN_SYSTEM 13): 1 dp `line`, `surface1`, 50–90 dp; collapsed when not loaded |
| Navigation inset | below the band, `bg` |

Removed in v2: the "최근 10경기" form dots, the vertical dividers in the record, mode subtitles ("오프라인 · 쉬움 · 보통 · 어려움" etc.), the `surface1` dock band and its `bolt` icon, the dock subtitle "비슷한 상대와 바로 한 판 · 60초 제한" (now the short `quickMeta` caption).

## Behaviour
- Me row → Profile (10). TalkBack: "내 기록, 새벽투수 4821, 12승 8패 3무, 승률 52퍼센트, 프로필 열기" (the record row is part of the same node).
- 빠른 대전 시작 → Terms gate (13) if needed → Quick match search (04) with the chosen digit count.
- 방 만들기 → create-room sheet (07); 코드로 참가 → join screen (07); 컴퓨터와 → setup sheet (03).
- `help` and the hero link both open the tutorial (01). `settings` opens Settings (11).
- Record numbers count up (150 ms) when returning from a result, no sound.

## States

| State | Presentation |
| --- | --- |
| New player (no games) | record values `0`, win rate `–` (`text3`); everything else unchanged (the hero link is the beginner's way in) |
| Record loading | values `–` in `textDisabled`, no shimmer |
| Offline | 48 dp `surface2` band under the top bar: `cloud_off` 20 dp `text2` + "오프라인 · 컴퓨터 대결만 할 수 있어요" `caption`; 방 만들기 and 코드로 참가 tiles disabled (icon + label `textDisabled`, not tappable, TalkBack "인터넷 연결 필요"); quick-match button disabled (`surface2` / `textDisabled`) with label "연결을 기다리는 중"; digit tiles stay selectable; no banner |
| Maintenance | the offline band variant with `schedule` (14) |
| Long German / Russian | tile labels wrap to 2 lines (tiles grow to fit; all three keep equal height); the quick-match caption may wrap under the title (shots `home-de`, `home-ru`) |
| Content does not fit (windows shorter than about 700 dp, long translations, large text) | the hero (`?` plates + link) hides first; if it still overflows, the region from Me to the tiles scrolls (20 dp bottom fade) above the pinned quick-match block. The quick-match block and the band never scroll |
| Small window (< 480 dp) | ADAPTIVE §1: hero and record labels hidden, tiles 56 dp, no banner |

## Ads
Banner allowed. When the band expands, the quick-match block moves up; taps on moved controls are ignored for 300 ms (`settle_guard`). The 24 dp clear space sits between the primary button and the band.
