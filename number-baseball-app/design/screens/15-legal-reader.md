# 15 In-app reader: Terms of Use and Privacy policy

Prototype: `prototype/legal.html`. Shot: `legal-ko.png`.

**Purpose:** owner decision "App only": the Terms and the privacy policy are read inside the app (the server's static `/terms` and `/privacy` pages exist only because Google requires public URLs). Long legal text must be comfortable to read on a phone and in a small window.

## Layout (360 × 800)

| Element | Spec |
| --- | --- |
| Top bar | 56 dp: `arrow_back` · "이용약관" or "개인정보 처리방침" `title`; scrolled state as Settings (`surface1` + 1 dp `line`, status-bar area `surface1`) |
| Meta line | 4 dp below the bar, 16 dp margins: "버전 2026-11-v1 · 시행일 …" `caption` `text3`, version in `numTag` |
| Contents | 16 dp below: 1 dp `line` above and below, 8 dp padding; "목차" `labelSmall` `text3`; one 48 dp row per top-level heading: number (`numLabel` `text3`, 16 dp column) + title `body` `text2`; tap scrolls to the heading (the heading lands 8 dp under the top bar) |
| Body | paragraphs 15 / 24 sp, 400, chalk (one step looser than `body` for long reading), 12 dp between paragraphs; lists indented 20 dp, markers `text3`, 6 dp between items; `b` = 700 |
| Headings | `subtitle` (16 / 22, 700), 28 dp above, 8 dp below; second-level headings `bodyStrong` |
| Column | content max width 560 dp, centred on wide windows; 16 dp margins (12 compact) |
| End | 32 dp bottom padding + navigation inset; last line "마지막 업데이트: …" `caption` `text3` |

## Content source
- Text comes from the release lead's documents (`docs/play/TERMS_OF_USE.md`, `docs/play/PRIVACY_POLICY.md`, public body only, never the team notes) in the app language, bundled as resources so the reader works offline. Same text as the server pages.
- The app name in the text uses the `app_name` resource. Placeholders (`[개발자 표시명]`, `[연락처 이메일]`) are filled before release; QA rejects any build that still shows square brackets.
- The child-safety section has a stable anchor so the Terms gate and reports can deep-link to it.

## Behaviour
- Opens from Settings (이용약관, 개인정보 처리방침), the Terms gate links, the nickname screen's privacy link and the not-available screen's privacy link.
- No web view, no browser, no external links inside the text: email addresses are shown as text with a long-press copy; URLs, if any remain, are plain text.
- Scroll position is kept across rotation; back returns to the caller (the Terms gate keeps its state).
- Text is selectable (long-press) so players can copy a clause.

## Accessibility
- Headings carry `heading()` semantics so TalkBack users can jump between them; the contents list items are buttons ("1. 이용 자격, 버튼").
- At 200 % font scale the body reflows; the contents rows grow; nothing is clipped.
