# 02 Home

Prototype: `prototype/home.html` (`?ad=none`, `?state=deletion`). Shots: `home-ko.png`, `home-de.png`, `home-ru.png`, `home-deletion-ko.png`.

**Purpose:** see my record, start a quick match with one thumb, reach the other modes.

## Layout (360 × 800, top to bottom)

| Region | Spec |
| --- | --- |
| Status bar | 24 dp |
| Header | 56 dp: brand mark 22 × 32 dp at x = 16; 12 dp; name stack: `app_name_main` "숫자야구" in `subtitle` over `app_name_sub` "조용한 한판" in `caption` `text3` (system font, each line ellipsizes; en "Number Baseball" / "Quiet Duel"); `help` and `settings` icon buttons (48 dp, `text2`), last one 4 dp from the edge |
| Hero | padding 12 / 16 / 0: "내 기록" `caption` `text3`; nickname `title` + tag `numTag` 18 sp; record row 16 dp below: three columns `numStat` values (승 패 무) with `caption` `text3` labels, 20 dp padding each side of a 1 dp `line` divider, then win rate (same style, value in `text2`, e.g. `52%`); form row 16 dp below: "최근 10경기" `caption` `text3` + 12 dp + ten 8 dp lamps, 6 dp gap, oldest left: win = lit strike lamp, draw = 1.5 dp chalk ring, loss = unlit |
| Spacer | flexible, min 16 dp |
| Other modes | section label "다른 방법으로 하기" (40 dp header); three 64 dp rows with a 40 × 40 dp `surface2` lead plate (radius 8, icon 24 chalk), title `subtitle`, subtitle `caption` `text3` (max 2 lines), chevron; dividers inset 72 dp: 컴퓨터와 대결 (`memory`, "오프라인 · 쉬움 · 보통 · 어려움"), 방 만들기 (`group`, "친구와 1:1 또는 팀전, 최대 5 대 5"), 코드로 참가 (`login`, "친구에게 받은 5자리 코드") |
| Quick match dock | 8 dp above; `surface1` band, 1 dp `line` top, padding 16 / 16 / 24: "빠른 대전" `subtitle` over "비슷한 상대와 바로 한 판 · 60초 제한" `caption` `text3` (stacked, never side by side); 12 dp; digit segmented control 48 dp (3자리 … 6자리, default 3 on first launch, then last used); 12 dp; primary 56 dp `bolt` + "빠른 대전 시작" |
| Ad band | anchored adaptive banner (DESIGN_SYSTEM 13): 1 dp `line`, `surface1`, 50–90 dp; collapsed when not loaded |
| Navigation inset | below the band, `bg` |

The 24 dp dock bottom padding is the clear space above the banner. Nothing else is tappable within 24 dp of the band.

## Behaviour
- Tapping the hero opens Profile (10). The whole hero is one button (TalkBack: "내 기록, 새벽투수 4821, 12승 8패 3무, 승률 52퍼센트, 프로필 열기").
- Quick match start → Terms gate (13) if needed → Quick match search (04) with the chosen digit count.
- 방 만들기 → create-room sheet (07); 코드로 참가 → join screen (07); 컴퓨터와 대결 → setup sheet (03).
- `help` replays the tutorial (01). `settings` opens Settings (11).
- Record numbers update when returning from a result: changed values count up (150 ms) with no sound.

## States

| State | Presentation |
| --- | --- |
| New player (no games) | record values show `0`, win rate `–`; form row replaced by `caption` `text3` "첫 경기를 기다리는 중" |
| Record loading | numerals show `–` in `textDisabled` (no skeleton shimmer) |
| Offline | 48 dp `surface3` band under the header: `cloud_off` 20 dp + "오프라인 · 컴퓨터 대결만 할 수 있어요" `caption`; 방 만들기 and 코드로 참가 rows disabled (`textDisabled`, subtitle "인터넷 연결 필요"); dock button disabled with label "연결을 기다리는 중"; no banner |
| Deletion requested on the web | alert dialog over Home (shot `home-deletion-ko.png`): `error` 24 dp `strike`; title "웹에서 계정 삭제 요청이 있었어요"; body "11월 18일에 요청됐어요. 그대로 두면 12월 18일에 계정과 기록이 지워져요. 직접 요청한 게 아니라면 취소하세요." (dates from the server); stacked actions: primary "요청 취소하고 계속하기", text button in `out` "지금 삭제하기" (→ same confirmation as Settings > 계정 삭제). Not dismissible by scrim or back |
| Long German / Russian | mode subtitles wrap to 2 lines (rows grow); dock title and caption stay stacked (shots `home-de`, `home-ru`) |
| Content does not fit (long translations, windows shorter than about 760 dp) | the region from the hero to the mode rows is one scroll area; when it overflows, the form row hides first; if it still overflows it scrolls under the pinned dock (16 dp bottom fade). The dock and the ad band never scroll |
| Small window (< 480 dp) | ADAPTIVE.md; no banner |

## Ads
Banner allowed. When the band expands after load, the dock and rows above move up; taps on moved controls are ignored for 300 ms (`settle_guard`).
