# 01 First run: age, nickname and tutorial

Prototypes: `prototype/age.html` (`?state=error|na`, `?entry=1998`), `prototype/nickname.html` (`?state=error`, `?ime=0`). Shots: `age-ko.png`, `age-en.png`, `age-error-ko.png`, `age-na-ko.png`, `nickname-ko.png`, `nickname-error-ko.png`.

Order on first launch: **age → nickname → tutorial → Home**. A restored account (Auto Backup or transfer code) skips all three.

## 0. Age (birth year)

**Purpose:** owner decision: minimum age 14. The screen is neutral: it asks only for the birth year and never hints at the required age (no number, no "you must be…").

| Element | Spec |
| --- | --- |
| Body top padding | 32 dp |
| Eyebrow | brand mark 22 × 32 dp + 10 dp + name stack (`app_name_main` `labelSmall` `text2` over `app_name_sub` `caption` `text3`) |
| Title | "태어난 해를 입력해 주세요" `headline`, 24 dp below |
| Lead | "태어난 해만 확인해요. 이름이나 생일은 묻지 않아요." `body` `text2`, 8 dp below |
| Year | 40 dp below, centred: "태어난 해" `labelSmall` `text3`; 12 dp; 4 slots 56 × 64 dp (12.4 states); 12 dp; error line (18 dp reserved, `caption` `out`) |
| Keypad | pinned at the bottom: the digit keypad (5 × 2) with `⌫` and "확인" (4 columns, primary style); **repeated digits are allowed here** (no `used` state), unlike the game |

- 확인 enabled at 4 digits. Valid years: 1900 to the current year. Invalid (shot `age-error-ko`): slots `error` state + "올바른 연도를 입력해 주세요"; slots clear on the next key.
- Rule (year only, so age is known to ±1 year): **allowed when current year − birth year ≥ 14** (the player turns 14 at some point this calendar year). The threshold lives in remote config with a bundled default, never in copy.
- The result (allowed / not allowed) and the year are stored on the device; the screen is not shown again. There is no back from this screen and no way to re-enter the year after a "not available" result without clearing app data.
- Accessibility: the slots are one group "태어난 해, 1 9 9 입력됨"; the keypad keys read digits.

### 0.1 Not available (shot `age-na-ko`)
Calm, final, no blame, no number: vertically centred column, 32 dp side margins: brand mark 33 × 48 dp at 55 % opacity; 8 dp; "지금은 이용할 수 없어요" `title`; 16 dp; "입력한 정보로는 이 게임을 이용할 수 없어요. 관심 가져 주셔서 고마워요." `body` `text2`; 24 dp; text link "개인정보 처리방침". No buttons, no ads, no sound, no account is created and nothing is sent to the server.


## 1. Nickname

**Purpose:** the only step before the first game. Creates the device account (README Accounts).

**Layout (360 × 800, IME open, 260 dp IME in the shot)**

| Element | Spec |
| --- | --- |
| Status bar | 24 dp, transparent |
| Body top padding | 32 dp |
| Eyebrow | brand mark 22 × 32 dp + 10 dp + "환영해요" `labelSmall` `text2` |
| Title | "닉네임을 정해 주세요" `headline`, 16 dp below the eyebrow, max 2 lines |
| Lead | "가입은 필요 없어요. 이 기기에 계정이 바로 만들어져요." `body` `text2`, 8 dp below |
| Field label | "닉네임" `labelSmall` `text2`, 32 dp below the lead, 8 dp above the field |
| Text field | 56 dp, `surface2`, radius 8, focused 2 dp chalk inset, text 18 sp 400, clear button (48 dp, `close` 20 dp `text3`) at the right |
| Helper row | 8 dp below: "2–12자 · 한글, 영문, 숫자" `caption` `text3` left; counter "4 / 12" `numTag` right |
| Preview | 24 dp below, 1 dp `line` on top, 16 dp padding: "다른 사람에게는 이렇게 보여요" `caption` `text3`; nickname `title` + "#····" (`numTag` 20 sp `text3`); "번호는 자동으로 붙어요" `caption` `text3` |
| Actions | pinned above the IME: 16 dp padding; "시작하기" primary 56 dp; 12 dp; "닉네임은 다른 플레이어에게 보여요. 개인정보 처리방침" `caption` `text3` centred, link underlined `text2` |

**Behaviour**
- The field is focused on open (IME up). IME action = Done = Start.
- Allowed: letters (any script) and digits, 2–12 grapheme clusters. Spaces and symbols are stripped as typed with a `REJECT` haptic.
- Start sends the nickname; while waiting the button shows a 3-lamp chase and is disabled (max 10 s, then the error state below).
- On success the server returns `Nickname#1234`; the preview tag animates from `····` to the digits (each digit `slot_fill` 120 ms, 60 ms stagger), then the tutorial opens after 600 ms.

**States**

| State | Presentation |
| --- | --- |
| Empty | Start disabled; helper in `text3` |
| Too short (1) | field 2 dp `out` inset; helper "2자 이상 입력해 주세요" in `out`; Start disabled |
| Rejected by profanity filter | helper "사용할 수 없는 닉네임이에요. 다른 이름을 써 주세요" in `out`; field keeps the text, selected |
| Offline / server error | toast "연결할 수 없어요. 연결을 확인하고 다시 시도해 주세요"; Start enabled again; the app can still go to the tutorial and vs Computer with a local placeholder name (account created later on first online action) |
| Restored by Auto Backup | this screen is skipped entirely |

**Accessibility:** field label "닉네임"; counter announced only at 12/12 ("최대 글자 수"); errors announced as polite live region.

## 2. Interactive tutorial (also "게임 방법")

**Purpose:** a beginner learns the rules inside the app in under 60 s, with the real match components.

**Layout:** the match screen (05) with three changes: the top bar shows "게임 방법" + step "1/4" (`numLabel` `text3`) + text button "건너뛰기"; the duel strip is replaced by a **coach band** (surface1, min 96 dp, 16 dp padding, `body`, one or two sentences, key words in `bodyStrong`); the memo board is hidden until step 4. The pitch clock is off.

| Step | Coach text (ko) | Interaction | Result |
| --- | --- | --- | --- |
| 1 | "상대가 서로 다른 숫자 3개를 숨겼어요. 맞혀 볼까요?" | Slots show `? ? ?` plates; "다음" primary in place of Pitch | — |
| 2 | "0, 7, 5를 눌러 보세요." | The next expected key has a 2 dp chalk inset; other keys work but the coach repeats the hint | Pitch reveals **1S 1B** with the real reveal timing (DESIGN_SYSTEM 8.3 B) |
| 3 | "**0**은 숫자와 자리가 맞아서 스트라이크(S), **7**은 숫자만 맞아서 볼(B)이에요. 5는 없어요." | The log row is highlighted; under it a 1-line legend; "다음" | Then an automatic second guess `1 2 3` → **OUT**: "하나도 맞지 않으면 아웃이에요." |
| 4 | "숨긴 숫자는 0 4 7. 직접 맞혀 보세요." | Memo board appears with one cell pre-marked to show how marking works; player types 0 4 7 | 3S → the Final Strike sequence → coach "모두 스트라이크면 승리! 같은 회에 둘 다 맞히면 무승부예요." + "시작하기" |

- The hidden number is fixed (0 4 7, the example from the game rules) so the coach text is always correct.
- Skip is always visible; skipping or finishing marks the tutorial seen; "게임 방법" on Home replays it.
- Team-play rule is a final optional card: "팀전에서는 팀원이 상대 팀과 1:1로 맞붙고, 더 많이 이긴 팀이 이겨요." with a 3-row mini board.
- Tutorial results never count in the record.
