# 11 Settings

Design system v2 (Clean White, 2026-11-26). Prototype: `prototype/settings.html` (`?scroll=end|ads`, `?ads=done|pending|error`, `?ump=0`). Shots: `settings-ko.png`, `settings-ko-end.png`, `settings-en.png`; remove ads: `settings-ads-ko/en` (not purchased), `settings-ads-done-ko/en` (purchased), `settings-ads-pending-ko/en` (payment pending), `settings-ads-error-ko` (Play unavailable).

**Purpose:** sound and vibration, chat safety, account, the "Remove ads" purchase, and legal items. Required items come from README (including "App only": no web pages, no web deletion code) and the release lead (`docs/play/POLICY_REVIEW.md`). No ads on this screen.

## 1. Layout

Top bar 56 dp: `arrow_back` · "설정". On scroll the top bar gets a 1 dp `line` bottom edge; the bar and status-bar area stay `bg` (shot `settings-ko-end`). Sections are separated by a section label (40 dp for the first, 48 dp after; `labelSmall` `text3`, baseline 8 dp above the first row); no extra lines above sections. Rows follow DESIGN_SYSTEM 12.15 (64 dp with subtitle, 56 single, icon 24 dp at x = 16 `text2`, text at x = 56, dividers inset 56 between every row). Switches on = `accent` track, off = white track with a 2 dp `outline` ring; sliders `accent`.

| Section | Row | Control | Default / value | Action |
| --- | --- | --- | --- | --- |
| 게임 | 언어 (`language`) | value + chevron | system language if supported, else English | radio list of 10 languages, each in its own script (한국어, English, 日本語, 简体中文, Español, Français, Deutsch, Português (Brasil), Русский, Tiếng Việt) + "시스템 언어 따르기"; uses per-app language API (`AppCompatDelegate.setApplicationLocales`) |
| 소리와 진동 | 효과음 (`volume_up`), sub "키 입력, 판정, 승패, 알림" | switch | **on** | |
| | 효과음 볼륨 | slider row (DESIGN_SYSTEM 12.15) | 70 | preview cue on release |
| | 무음 모드 따르기 (`do_not_disturb_on`), sub "휴대폰이 무음이나 진동일 때 소리를 꺼요" | switch | **on** | |
| | 배경 음악 (`music_note`), sub "메뉴와 대결 음악" | switch | **on** (owner decision 2026-11-26) | |
| | 음악 볼륨 | slider row, disabled while music is off | 40 | music fades to the new level |
| | 진동 (`mobile_vibrate`), sub "키 입력, 판정, 승패" | switch | **on** | |
| 채팅 | 전체 채팅 끄기 (`notifications_off`), sub "모든 채팅과 빠른 문구를 숨겨요" | switch | off | same setting as the chat sheet switch |
| | 차단한 플레이어 (`block`) | value "2명" + chevron | | list screen (below) |
| 계정 | 닉네임 (`person`) | value "새벽투수#4821" + chevron | | nickname sheet (01 rules) |
| | 다른 기기로 계정 옮기기 (`key`), sub "10분 동안 쓸 수 있는 코드를 만들어요" | chevron | | transfer-code sheet |
| | 계정 삭제 (`delete`, title in `danger`), sub "기록과 닉네임이 영구히 지워져요" | chevron | | confirmation dialog |
| 구매 | 광고 제거 (`ad_off`) | see §1.1 | not purchased | Play purchase sheet |
| | 구매 복원 (`history`) | see §1.1 | | re-queries Play |
| 정보 및 지원 | 개인정보 보호 옵션 (`shield`), sub "광고 맞춤 설정 동의를 바꿔요" | chevron | **shown only when UMP reports `privacyOptionsRequirementStatus == REQUIRED`** (`?ump=0` hides it) | UMP privacy options form |
| | 개인정보 처리방침 (`policy`) | chevron | | in-app reader (15) |
| | 이용약관 (`description`) | chevron | | in-app reader (15) |
| | 문의하기 (`send`), second line = the contact email address (`body` `text2`, selectable) | trailing copy icon button 48 dp (`content_copy`, `text2`) | | tap the copy button or long-press the row: copies the address, toast "이메일 주소를 복사했어요" (Android 13+ shows its own clipboard preview; then no toast). No mail app is opened (app only); the address comes from a resource so it never sits in the layout |
| | 오픈소스 라이선스 (`info`) | chevron | | licences screen |
| | 버전 | text only, no icon, text at x = 56, `body` `text3`, value `numTag` `text3` "1.0.0 (1)" | | long-press copies the version |

### 1.1 구매 section: Remove ads (owner decision 2026-11-25)

Product `remove_ads`, non-consumable, Google Play Billing only (README; `docs/play/POLICY_CHECKLIST.md` 12). The section sits between 계정 and 정보 및 지원 and is always present (also in the review build), so reviewers find it. Rows follow 12.15 (64 dp with subtitle, grows if the subtitle wraps to 2 lines; icon at x = 16 `text2`). Nothing here uses hint colours (except the `strike` warning line), badges or "sale" wording; the price is the only number.

| State | 광고 제거 row | 구매 복원 row | Shot |
| --- | --- | --- | --- |
| **Loading** (product details not back yet, < 1 s usually) | title "광고 제거", sub "모든 광고가 사라져요 · 한 번만 결제해요", no trailing value; row not tappable | shown | — |
| **Not purchased** | same title and sub; trailing value = Play's formatted price (`ProductDetails.oneTimePurchaseOfferDetails.formattedPrice`, `body` `text2`, e.g. "₩6,500" / "$4.99"; never hard-coded); whole row tappable, no chevron (it starts a purchase, it does not navigate) | sub "이 Google 계정으로 산 구매를 다시 확인해요"; tappable | `settings-ads-ko/en` |
| **Purchased** | sub "구매함 · 광고가 나오지 않아요" / "Purchased · no ads"; trailing `check_circle` 24 dp `text2`; not tappable, no ripple | hidden | `settings-ads-done-ko/en` |
| **Pending** (`PENDING`: cash, slow payment methods) | sub "결제 대기 중 · 결제가 끝나면 광고가 사라져요"; trailing `hourglass_empty` 24 dp `text2`; not tappable; ads keep showing (not bought until `PURCHASED`); re-checked on every resume | shown (re-checks now) | `settings-ads-pending-ko/en` |
| **Error** (billing unavailable, not signed in to Play, product details failed) | sub "지금은 구매할 수 없어요. 잠시 후 다시 시도해 주세요" in `strike` (same warning colour as the silent-mode subtitle); trailing `refresh` icon button 48 dp `text2` "다시 시도" (also the whole row) reconnects and reloads | shown | `settings-ads-error-ko` |

Flows and feedback (toasts on the `inverse` surface, DESIGN_SYSTEM 12.17):
- Tap 광고 제거 → Play purchase sheet over the app. Success (`PURCHASED`, acknowledged at once; acknowledgement retried at next start if it failed): toast "광고를 제거했어요", the row turns to Purchased, every banner collapses, no further interstitials, the result 광고 제거 line disappears, the UMP form is no longer shown. User cancelled: nothing. Play error: toast "구매를 마치지 못했어요. 다시 시도해 주세요". Result `PENDING`: toast "결제가 끝나면 광고가 사라져요" and the Pending state.
- Tap 구매 복원 → `queryPurchasesAsync`; the row shows the 3-dot chase (`accent`) in place of the subtitle for the call. Found: toast "광고 제거를 복원했어요" → Purchased. Nothing found: toast "이 Google 계정에는 복원할 구매가 없어요". Failed: toast "Google Play에 연결할 수 없어요".
- Refund or revocation (purchase gone at start or resume): back to Not purchased silently; ads return.
- The 개인정보 보호 옵션 row keeps following UMP's `REQUIRED` status (consent can still be changed after purchase).
- TalkBack: "광고 제거, 6,500원, 구매" / "광고 제거, 구매함" / "광고 제거, 결제 대기 중".
- Copy rule: never "ad-free" (14.2); en "Remove ads", "Restore purchase".

## 2. Sub-screens and dialogs

**Transfer code sheet:** title "다른 기기로 계정 옮기기"; body "새 기기에서 '다른 기기에서 옮겨 오기'에 입력하세요. 코드는 10분 뒤에 사라지고 한 번만 쓸 수 있어요." (the label matches the first-run text button). Code (protocol: 12 characters, shown `XXXX-XXXX-XXXX`; the dashes are not drawn, the grouping is) as **3 groups of 4 plates**, centred: 2 dp between plates in a group, 8 dp between groups, plate height 40 dp, radius 6, `surface2`, digit `numCode` (capped at 1.3×, DESIGN_SYSTEM 3.3) `ink`; plate width = min(28, (content width − 2 × 8 − 9 × 2) ÷ 12) dp, floor 22 dp (= 24.5 dp at 360 dp, 28 dp from 382 dp). At font scale ≥ 1.5 the plate height grows to 48 dp; the width rule is unchanged. Below the 22 dp floor (windows narrower than 330 dp) the groups stack, one group of 4 plates at 28 dp per row, 8 dp apart. Countdown under the plates: `numTimer` `text2` "9:41 남음" (TalkBack "9분 41초 남음", polite at 1:00). Tonal "복사" (copies the code with dashes; toast "코드를 복사했어요"). **Expired:** plates `surface1` with `textDisabled` digits, the countdown is replaced by "코드가 만료됐어요" (`caption` `text3`), the tonal button becomes primary "새 코드 만들기" (`accent`). TalkBack reads the code group by group with characters spelled. (The import side lives on the first-run screen as a text button "다른 기기에서 옮겨 오기" under the nickname field.) Renders: `settings-transfer-*`, `settings-transfer-expired-*`.


**Delete account dialog:** title "계정을 삭제할까요?"; body "기록, 닉네임, 차단 목록이 바로 지워지고 되돌릴 수 없어요. 앱이 없을 때는 문의하기의 이메일로 삭제를 요청할 수 있어요."; actions 취소 (`accent`) · 삭제 (`danger`). On confirm: the 3-dot chase in the button, then the app clears the token and returns to first run with the toast "계정을 삭제했어요".

**Blocked players:** top bar "차단한 플레이어"; rows 56 dp: nickname + tag, trailing text button "차단 해제" (`labelSmall` `accent`); unblocking asks nothing and shows the toast "차단을 해제했어요" with "되돌리기". Empty: "차단한 플레이어가 없어요" `caption` `text3`, centred, 48 dp below the top bar.

**Licences:** list of bundled components with licence names (**Inter: SIL OFL 1.1** (`design/fonts/OFL-Inter.txt`; replaces Barlow Condensed); Material Symbols: Apache 2.0; plus libraries from the build); each opens its full text.

## 3. Behaviour
- Every switch applies immediately (no save button) and plays `toggle` haptic and cue (the cue obeys the new state: turning effects off plays nothing).
- Changing language recreates the activity and keeps the scroll position.
- With follow-silent-mode on and the phone silent, the effects row shows the subtitle "지금은 무음 모드라 소리가 나지 않아요" in `strike` (so a muted effect is never a mystery).

## 4. Accessibility
Switch rows are one element with role switch: "효과음, 켜짐, 키 입력, 판정, 승패, 알림". Sliders: role slider with "효과음 볼륨 70퍼센트"; adjustable by volume-style accessibility actions in steps of 5.
