# 11 Settings

Prototype: `prototype/settings.html` (`?scroll=end`, `?ump=0`). Shots: `settings-ko.png`, `settings-ko-end.png`, `settings-en.png`.

**Purpose:** sound and vibration, chat safety, account and legal items. Required items come from README (including "App only": no web pages, no web deletion code) and the release lead (`docs/play/POLICY_REVIEW.md`). No ads on this screen.

## 1. Layout

Top bar 56 dp: `arrow_back` · "설정". On scroll the top bar and the status-bar area take `surface1` with a 1 dp `line` bottom edge (shot `settings-ko-end`). Sections are separated by a section label (40 dp for the first, 48 dp after; `labelSmall` `text3`, baseline 8 dp above the first row); no extra lines above sections. Rows follow DESIGN_SYSTEM 12.14 (64 dp with subtitle, 56 single, icon 24 dp at x = 16 `text2`, text at x = 56, dividers inset 56).

| Section | Row | Control | Default / value | Action |
| --- | --- | --- | --- | --- |
| 게임 | 언어 (`language`) | value + chevron | system language if supported, else English | radio list of 10 languages, each in its own script (한국어, English, 日本語, 简体中文, Español, Français, Deutsch, Português (Brasil), Русский, Tiếng Việt) + "시스템 언어 따르기"; uses per-app language API (`AppCompatDelegate.setApplicationLocales`) |
| 소리와 진동 | 효과음 (`volume_up`), sub "키 입력, 판정, 승패, 알림" | switch | **on** | |
| | 효과음 볼륨 | slider row (DESIGN_SYSTEM 12.14) | 70 | preview cue on release |
| | 무음 모드 따르기 (`do_not_disturb_on`), sub "휴대폰이 무음이나 진동일 때 소리를 꺼요" | switch | **on** | |
| | 배경 음악 (`music_note`), sub "메뉴와 대결 음악 (기본값은 꺼짐)" | switch | **off** | |
| | 음악 볼륨 | slider row, disabled while music is off | 40 | music fades to the new level |
| | 진동 (`mobile_vibrate`), sub "키 입력, 판정, 승패" | switch | **on** | |
| 채팅 | 전체 채팅 끄기 (`notifications_off`), sub "모든 채팅과 빠른 문구를 숨겨요" | switch | off | same setting as the chat sheet switch |
| | 차단한 플레이어 (`block`) | value "2명" + chevron | | list screen (below) |
| 계정 | 닉네임 (`person`) | value "새벽투수#4821" + chevron | | nickname sheet (01 rules) |
| | 다른 기기로 계정 옮기기 (`key`), sub "10분 동안 쓸 수 있는 코드를 만들어요" | chevron | | transfer-code sheet |
| | 계정 삭제 (`delete`, title in `out`), sub "기록과 닉네임이 영구히 지워져요" | chevron | | confirmation dialog |
| 정보 및 지원 | 개인정보 보호 옵션 (`shield`), sub "광고 맞춤 설정 동의를 바꿔요" | chevron | **shown only when UMP reports `privacyOptionsRequirementStatus == REQUIRED`** (`?ump=0` hides it) | UMP privacy options form |
| | 개인정보 처리방침 (`policy`) | chevron | | in-app reader (15) |
| | 이용약관 (`description`) | chevron | | in-app reader (15) |
| | 문의하기 (`send`), second line = the contact email address (`body` `text2`, selectable) | trailing copy icon button 48 dp (`content_copy`, `text2`) | | tap the copy button or long-press the row: copies the address, toast "이메일 주소를 복사했어요" (Android 13+ shows its own clipboard preview; then no toast). No mail app is opened (app only); the address comes from a resource so it never sits in the layout |
| | 오픈소스 라이선스 (`info`) | chevron | | licences screen |
| | 버전 | text only, no icon, text at x = 56, `body` `text3`, value `numTag` `text3` "1.0.0 (1)" | | long-press copies the version |

## 2. Sub-screens and dialogs

**Transfer code sheet:** title "다른 기기로 계정 옮기기"; body "새 기기에서 '코드로 계정 가져오기'에 입력하세요. 코드는 10분 뒤에 사라지고 한 번만 쓸 수 있어요."; code as 6 plates 40 × 52 dp `numCode` 28 sp; countdown `numTimer` `text2` "9:41 남음"; tonal "복사"; when expired: plates `textDisabled`, primary "새 코드 만들기". (The import side lives on the first-run screen as a text button "다른 기기에서 옮겨 오기" under the nickname field.)


**Delete account dialog:** title "계정을 삭제할까요?"; body "기록, 닉네임, 차단 목록이 바로 지워지고 되돌릴 수 없어요. 앱이 없을 때는 문의하기의 이메일로 삭제를 요청할 수 있어요."; actions 취소 · 삭제 (`out`). On confirm: a 3-lamp chase in the button, then the app clears the token and returns to first run with the toast "계정을 삭제했어요".

**Blocked players:** top bar "차단한 플레이어"; rows 56 dp: nickname + tag, trailing text button "차단 해제" (`labelSmall` chalk); unblocking asks nothing and shows the toast "차단을 해제했어요" with "되돌리기". Empty: "차단한 플레이어가 없어요" `caption` `text3`, centred, 48 dp below the top bar.

**Licences:** list of bundled components with licence names (Barlow Condensed: SIL OFL 1.1; Material Symbols: Apache 2.0; plus libraries from the build); each opens its full text.

## 3. Behaviour
- Every switch applies immediately (no save button) and plays `toggle` haptic and cue (the cue obeys the new state: turning effects off plays nothing).
- Changing language recreates the activity and keeps the scroll position.
- With follow-silent-mode on and the phone silent, the effects row shows the subtitle "지금은 무음 모드라 소리가 나지 않아요" in `strike` (so a muted effect is never a mystery).

## 4. Accessibility
Switch rows are one element with role switch: "효과음, 켜짐, 키 입력, 판정, 승패, 알림". Sliders: role slider with "효과음 볼륨 70퍼센트"; adjustable by volume-style accessibility actions in steps of 5.
