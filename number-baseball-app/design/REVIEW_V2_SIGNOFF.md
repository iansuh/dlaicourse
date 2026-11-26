# Design sign-off: redesign v2 fix round

Reviewer: design lead. Date: 2026-11-26. Build: `71b8fb3` ("redesign v2 review fixes and polish"). I reviewed the 495 renders in `android/app/screenshots/` (including the new `match-phrase-sent-*`), the QA locale renders, and the "Review v2 fixes" section of `android/README.md`, checking each item against [`REVIEW_V2.md`](REVIEW_V2.md). Measurements were taken with PIL at density 2 (2 px = 1 dp). App code was not changed.

## Verdict

**SIGN-OFF WITH MINORS.**
- Both majors are fixed.
- 16 of the 18 minors are fixed.
- V2-18 and V2-20 are accepted as post-release minors.
- The five polish items cause no regressions.
- Two new minors, V3-01 and V3-02, both appear only at large text and both are post-release.

Nothing in this design review blocks release.

The carried release condition still stands: the `[개발자 표시명]` / `[시행일]` placeholders are still in `docs/play/TERMS_OF_USE.md` and `PRIVACY_POLICY.md`. The best-hint cross-fade condition is done.

## Items

| ID | Check (render, measured) | Status |
| --- | --- | --- |
| **V2-01** | `chat-*-ko-font200`: "전체 채팅 끄기" is on its own row under the title, on one line, nothing clipped, and the switch is beside it | Fixed |
| **V2-02** | `match-toast-ko`: toast 416–463.5 dp, entry head below it, slots untouched. `-font200`: the toast is above the entry head. `-small`: the toast is at the top of the log, under the progress line | Fixed |
| V2-03 | `search-found-small-ko/en`: no avatar or VS, name and headline under the tiles, nothing clipped | Fixed |
| V2-04 | `nickname-*`: the link is `accent` bold with no underline | Fixed |
| V2-05 | `nickname-ko`: the preview fades into the pinned actions | Fixed |
| V2-06 | `team-board-ko`: my row 186–258 dp; lines at 333 / 405, the same as the shot | Fixed |
| V2-07 | `team-final-ko`: no rules caption, all three rows visible | Fixed |
| V2-08 | `home-ko`: record divider at 237 dp (shot 237) | Fixed |
| V2-09 | `lobby-ko`: rules row lines at 262 / 311 dp, the same as the shot | Fixed |
| V2-10 | Every dialog backdrop is `#A0A1A3`, the same as the shot `report-dialog-ko` | Fixed |
| V2-11 | `result-innings-ko-font200`: the table starts at round 1 | Fixed |
| V2-12 | `tutorial-3-*`: the S / B chip legend sits directly under the coached row | Fixed |
| V2-13 | `qa-locale-home-ja`: "コンピュータ" fits on one line | Fixed |
| V2-14 | `result-lose/link-*`: fixtures show "12승" / "13승", and the code draws "–" in `text3` | Fixed |
| V2-15 | `match-phrase-sent-*`: an `accentTint` bubble labelled "나" appears under the opponent row | Fixed (see V3-01) |
| V2-16 | `report-card-norecord-ko`: the message and rows below it are within 0.5–4 dp of the shot. The handle is 10 dp higher, which is the sheet height already accepted in §4 of REVIEW_V2 | Fixed |
| V2-17 | `lobby-reconnect-small-ko`: banner, team header and one full seat row are visible | Fixed |
| V2-18 | The `WordFit` 11 dp floor is not enforced. Enforcing it would clip 10 labels in ja / ru / de / en / fr / vi at 100 % in 360 × 320 and narrow windows | **Accepted, post-release (1.0.x).** Clipping is worse than small text, and ko and the standard phone window are not affected. Fix the copy for those 10 strings first (short forms, e.g. ru "Подтвердить" → "ОК"), then enforce the floor |
| V2-19 | de "Verloren" / "Startseite"; ru "Бросок" / "Бросил" / "Соперник" | Fixed |
| V2-20 | 5 ellipsis findings, all de in a 360 × 320 window at 200 % (`layout-baseline.tsv`) | **Accepted, post-release.** This is an extreme combination, and every finding is an ellipsis rather than a clip |
| P1–P5 | P1: names hug the tag ("Größe…#7731"). P2: no badge at large text, first summary row in view. P3: Home at 200 % shows all three tiles, label only. P4: the `bg` fade appears on the terms gate, nickname and results. P5: drag cannot be checked in a render, so I took it as claimed | No regressions. The label-only tiles at large text (no icon, "다른 방법으로" hidden) are accepted as a large-text variant |

## New minors (post-release)

| ID | Render | Measured | Fix |
| --- | --- | --- | --- |
| V3-01 | `match-phrase-sent-ko-font200` | The bubble's top is at a fixed 150 dp, which covers the opponent's status word "던짐" on the opponent row. At 100 % it starts 18 dp below the row's ink, which is correct | Anchor the bubble to the opponent row's measured bottom + 8 dp (05 §2.8), so it moves down when the row grows |
| V3-02 | `chat-ko-font200`, `chat-system-ko-font200` | The scroll list meets the new header row with a hard cut through the glyphs (the "Volna#1187 님이 나갔어요" line). The newest timestamp "9:41" is cut at the phrase-row divider, even with the list at its end | Add `scrollFade` to both edges of the chat list, and add bottom content padding so the last item is fully visible at the end |
