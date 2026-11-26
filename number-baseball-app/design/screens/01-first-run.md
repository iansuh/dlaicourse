# 01 First run: age, nickname and tutorial

Design system v2 (Clean White, 2026-11-26). Prototypes: `prototype/age.html` (`?state=error|na`, `?entry=199803`), `prototype/nickname.html` (`?state=error`, `?ime=0`). Shots: `age-ko.png`, `age-en.png`, `age-error-ko.png`, `age-na-ko.png`, `nickname-ko.png`, `nickname-error-ko.png`.

Order on first launch: **age → nickname → tutorial → Home**. A restored account (Auto Backup or transfer code) skips all three.

## 0. Age (birth year and month)

**Purpose:** owner decision: minimum age 14, checked exactly (DEF-051, final sign-off 2026-11-26: year **and month**). The screen is neutral: it never hints at the required age (no number, no "you must be…").

| Element | Spec (v2) |
| --- | --- |
| Body top padding | 32 dp |
| Eyebrow | brand mark 32 × 32 dp + 10 dp + name stack (`app_name_main` `labelSmall` `text2` over `app_name_sub` `caption` `text3`) |
| Title | "태어난 해와 달을 입력해 주세요" `headline`, 24 dp below, max 2 lines |
| Lead | "태어난 해와 달만 확인해요. 이름이나 생일 날짜는 묻지 않아요." `body` `text2`, 8 dp below |
| Plates | 40 dp below, centred: two groups 16 dp apart, each "태어난 해" / "달" `labelSmall` `text3` label 8 dp above its plates; 4 + 2 plates, 6 dp gap inside a group, plate width = min(56, (content width − 16 − 4 × 6) ÷ 6) dp (= 48 dp at 360 dp), 64 dp high, radius 12, slot states (DESIGN_SYSTEM 12.4: empty 1.5 dp `outline`, active 2 dp `accent`, filled `surface2`, error `dangerTint` + 2 dp `danger`) |
| Error line | 12 dp below, 18 dp reserved, `caption` `danger`: "올바른 연도와 달을 입력해 주세요" |
| Keypad | pinned at the bottom: digit keypad (5 × 2, `surface2` keys) with `⌫` and "확인" (4 columns, primary `accent`); **repeated digits are allowed here** (no `used` state) |

- 확인 enabled at 6 digits. Valid: year 1900 to the current year, month 01–12, not in the future. Invalid (shot `age-error-ko`, entry 2091 13): all plates in the error state + the error line; plates clear on the next key.
- Rule: **allowed when the full years as of the current month ≥ 14** (the birth month counts as passed). The threshold lives in remote config with a bundled default, never in copy. Only the resulting band (allowed / not allowed) is stored.
- No back from this screen; no re-entry after "not available" without clearing app data.
- Accessibility: one group "태어난 해와 달, 1 9 9 입력됨"; keys read digits.

### 0.1 Not available (shot `age-na-ko`)
Calm, final, no blame, no number: vertically centred column on `bg`, 32 dp side margins: brand mark 48 × 48 dp at 55 % opacity; 8 dp; "지금은 이용할 수 없어요" `title`; 16 dp; "입력한 정보로는 이 게임을 이용할 수 없어요. 관심 가져 주셔서 고마워요." `body` `text2`; 24 dp; text link "개인정보 처리방침" (`labelSmall` `accent`, underlined, 48 dp target). No buttons, no ads, no sound, no account is created and nothing is sent to the server.


## 1. Nickname

**Purpose:** the only step before the first game. Creates the device account (README Accounts).

**Layout (360 × 800, IME open, 260 dp IME in the shot)**

| Element | Spec |
| --- | --- |
| Status bar | 24 dp, transparent |
| Body top padding | 32 dp |
| Eyebrow | brand mark 32 × 32 dp + 10 dp + "환영해요" `labelSmall` `text2` |
| Title | "닉네임을 정해 주세요" `headline`, 16 dp below the eyebrow, max 2 lines |
| Lead | "가입은 필요 없어요. 이 기기에 계정이 바로 만들어져요." `body` `text2`, 8 dp below |
| Field label | "닉네임" `labelSmall` `text2`, 32 dp below the lead, 8 dp above the field |
| Text field | 56 dp, `surface2`, radius 12, focused 2 dp `accent` inset and `accent` caret, text 18 sp 400, clear button (48 dp, `close` 20 dp `text3`) at the right |
| Helper row | 8 dp below: "2–12자 · 한글, 영문, 숫자" `caption` `text3` left; counter "4 / 12" `numTag` right |
| Preview | 24 dp below, 1 dp `line` on top, 16 dp padding: "다른 사람에게는 이렇게 보여요" `caption` `text3`; nickname `title` + "#····" (Inter `numTag` 20 sp `text3`); "번호는 자동으로 붙어요" `caption` `text3` |
| Actions | pinned above the IME: 16 dp padding; "시작하기" primary 56 dp (`accent`); 12 dp; "닉네임은 다른 플레이어에게 보여요. 개인정보 처리방침" `caption` `text3` centred, the link `accent` 700 |

**Behaviour**
- The field is focused on open (IME up). IME action = Done = Start.
- Allowed: letters (any script) and digits, 2–12 grapheme clusters. Spaces and symbols are stripped as typed with a `REJECT` haptic.
- Start sends the nickname; while waiting the button shows the 3-dot chase (white dots on `accent`) and is disabled (max 10 s, then the error state below).
- On success the server returns `Nickname#1234`; the preview tag animates from `····` to the digits (each digit `slot_fill` 120 ms, 60 ms stagger), then the tutorial opens after 600 ms.

**States**

| State | Presentation |
| --- | --- |
| Empty | Start disabled; helper in `text3` |
| Too short (1) | field 2 dp `danger` inset; helper "2자 이상 입력해 주세요" and counter in `danger`; Start disabled (`surface2` / `textDisabled`) |
| Rejected by profanity filter | helper "사용할 수 없는 닉네임이에요. 다른 이름을 써 주세요" in `danger`; field keeps the text, selected |
| Offline / server error | toast "연결할 수 없어요. 연결을 확인하고 다시 시도해 주세요"; Start enabled again; the app can still go to the tutorial and vs Computer with a local placeholder name (account created later on first online action) |
| Restored by Auto Backup | this screen is skipped entirely |

**Accessibility:** field label "닉네임"; counter announced only at 12/12 ("최대 글자 수"); errors announced as polite live region.

## 2. Interactive tutorial (also "게임 방법")

**Purpose:** a beginner learns the rules inside the app in under 60 s, with the real match components.

**Layout (v2):** the match screen (05) with three changes: the top bar shows "게임 방법" + step "1/4" (`numLabel` `text3`) + text button "건너뛰기"; the opponent row is replaced by a **coach box** (`surface2`, radius 12, 16 dp margins and padding, min 96 dp, `body`, one or two sentences, key words in `bodyStrong`); the 메모 button is hidden until step 4. No timer pill and no progress line.

| Step | Coach text (ko) | Interaction | Result |
| --- | --- | --- | --- |
| 1 | "상대가 서로 다른 숫자 3개를 숨겼어요. 맞혀 볼까요?" | Slots show `? ? ?` plates; "다음" primary in place of Pitch | — |
| 2 | "0, 7, 5를 눌러 보세요." | The next expected key has a 2 dp `accent` inset; other keys work but the coach repeats the hint | Pitch reveals **1S 1B** chips with the real reveal timing (DESIGN_SYSTEM 8.3 B) |
| 3 | "**0**은 숫자와 자리가 맞아서 스트라이크(S), **7**은 숫자만 맞아서 볼(B)이에요. 5는 없어요." | The latest log row (already `surface2`) gets a 2 dp `accent` inset ring; under it a 1-line legend using the same chips; "다음" | Then an automatic second guess `1 2 3` → **OUT**: "하나도 맞지 않으면 아웃이에요." |
| 4 | "숨긴 숫자는 0 4 7. 직접 맞혀 보세요." | The 메모 button appears with a one-time `accent` ring; tapping it opens the memo sheet with one cell pre-marked; the keypad shows the ruled-out stroke on 5; player types 0 4 7 | 3S → the Final Strike sequence → coach "모두 스트라이크면 승리! 같은 회에 둘 다 맞히면 무승부예요." + "시작하기" |

- The hidden number is fixed (0 4 7, the example from the game rules) so the coach text is always correct.
- Skip is always visible; skipping or finishing marks the tutorial seen; "게임 방법" on Home replays it.
- Team-play rule is a final optional card: "팀전에서는 팀원이 상대 팀과 1:1로 맞붙고, 더 많이 이긴 팀이 이겨요." with a 3-row mini board.
- Tutorial results never count in the record.
