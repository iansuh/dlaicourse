# 13 Terms gate

Design system v2 (Clean White, 2026-11-26). Prototype: `prototype/terms.html`. Shots: `terms-ko.png`, `terms-en.png`.

**Purpose:** Play UGC policy: users accept the Terms before online play or chat, and the step cannot be skipped (release lead R6). Shown before the first online action and again when the Terms version changes. vs Computer and the tutorial never need it.

## Layout (360 × 800), full screen (not a sheet)

| Element | Spec |
| --- | --- |
| Top bar | 56 dp: `close` only (= "나중에": returns to where the user came from; the online action does not happen) |
| Title | 8 dp below the bar: "온라인 대결 전에 약관을 확인해 주세요" `headline`, max 2 lines |
| Lead | 8 dp: "모르는 사람과 대결하고 대화하는 공간이라 몇 가지 약속이 필요해요." `body` `text2` |
| Points | 24 dp below, 1 dp `line` above and between; each 14 dp vertical padding, 40 dp icon column (24 dp icon `text2`), title `bodyStrong`, line `caption` `text3`: `person` "이용 연령" + "이 게임은 정해진 나이 이상만 이용할 수 있어요." (the number comes from the Terms, never shown here) · `forum` "서로 존중하기" + "괴롭힘, 혐오 표현, 개인정보 요구, 스팸은 금지예요." · `shield` "아동 안전은 무관용" + "관련 내용은 즉시 삭제, 영구 정지, 관계 기관 신고." · `flag` "신고와 차단" + "메시지나 플레이어를 길게 눌러요. 보통 24시간 안에 조치해요." |
| Links | 8 dp below: two 48 dp links (DESIGN_SYSTEM 12.14 Link: `labelSmall` 14 sp `accent` + `chevron_right` 18 dp `accent`, no underline): "이용약관 전문 보기", "개인정보 처리방침"; both open the in-app reader (15), never a browser |
| Version | `numTag` `text3` "약관 버전 2026-11-v1" |
| Actions (pinned) | 1 dp `line` top, 12 / 16 / 16 dp padding: primary "동의하고 계속" (`accent`); 8 dp; `caption` `text3` centred "동의하지 않으면 컴퓨터 대결만 할 수 있어요." |

The body scrolls if it does not fit; the actions stay pinned. At 360 × 800 in Korean everything except the version line fits without scrolling.

The age itself is checked once, on the first-launch age screen (01 §0); the gate only restates the rule without naming the number.

## Behaviour
- Agree → `PUT /api/v1/me/terms {version}` → on success continue straight to the action the user started (quick match search, create, join, chat). Failure → toast "연결할 수 없어요. 다시 시도해 주세요." and stay.
- The server refuses online actions with `terms_required` until accepted; the app maps that error to this screen, so the gate cannot be bypassed.
- Version change: shown at the next online action, with the title "약관이 바뀌었어요" and a 1-line summary of what changed above the points (`caption` `strike` "바뀐 점: …").
- Back = close.

## Accessibility
Heading order: title, then each point as a heading-less group; links are announced as buttons ("이용약관 전문 보기, 버튼").
