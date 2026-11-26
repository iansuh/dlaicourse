# Release readiness: 숫자야구: 조용한 한판 (Number Baseball: Quiet Duel)

Owner: release lead. Status date: 2026-11-26. Version `0.1.0` (versionCode 1), package `app.numbaseball`, targetSdk 36, minSdk 26, terms version `2026-11-v3`, minimum age 14.

**Verdict.** The code, tests and release documents are ready for a first internal test. Nothing in the repository blocks the release gate (no Blocker or Major defect is open). What is left is (a) the owner's accounts, values and keys (list in Korean below), (b) a manual check on real devices, and (c) one design sign-off round. A signed Play upload cannot be built until the owner supplies the values in section 5.

## 1. What is done

| Area | State |
| --- | --- |
| Game rules, computer players, team rules (`backend/core`) | Done, covered by oracle and differential tests |
| Server (`backend/server`, Ktor + SQLite) | Done: REST + WebSocket protocol (`docs/protocol.md`), terms gate + age confirmation, report/block/mute, auto chat restriction, review room (`REVIEW_ROOM_CODE`), email-based deletion by admin, privacy / terms / delete-account / `app-ads.txt` pages, Dockerfile, `DEPLOY.md` (Render) |
| Android app (`android`) | Done: designed UI in 10 languages, offline first launch, age screen (birth year + month), terms gate, ads (UMP, age treatment, banner rules, interstitial rules), Play Age Signals, Play Billing `remove_ads` with restore, report/block/mute, account transfer, delete account, adaptive layouts, sound and haptics |
| Release documents (`docs/play`) | Policy checklist, policy review, privacy policy, terms (v3), delete-account page, store listing (10 languages), Korean release guide. Re-checked against the code on 2026-11-26 (section 6) |
| CI (`.github/workflows/number-baseball-app.yml`) | Backend tests; Android `verifyRoborazziDebug` (all unit, Robolectric and screenshot tests), lint, debug APK, unsigned release bundle with placeholder IDs |

## 2. Test results (2026-11-26, this container, `--offline` for the regression)

| Run | Result |
| --- | --- |
| `cd backend && ./gradlew --offline test` (forced re-run with `cleanTest`) | BUILD SUCCESSFUL, 271 tests (core 128, server 143), 0 failed, 0 skipped, 2 min 35 s |
| `cd android && ./gradlew --offline :app:testDebugUnitTest --rerun --no-build-cache` | BUILD SUCCESSFUL, 3045 test cases, 0 failed, 0 errors; 729 are skipped on purpose (each screenshot class records only its own shots, `Assume` in `Screenshots.kt`), so 2316 executed; 2 min 37 s |
| `:app:lintDebug` | BUILD SUCCESSFUL: 0 errors, 37 warnings, 5 hints |
| `:app:assembleDebug` | BUILD SUCCESSFUL |
| `:app:verifyRoborazziDebug` | BUILD SUCCESSFUL: the 480 committed reference renders in `android/app/screenshots` all match |
| `:app:assembleRelease` / `:app:bundleRelease` with `NB_ALLOW_PLACEHOLDER_RELEASE=true` (online, not `--offline`) | BUILD SUCCESSFUL in 2 min 38 s: `app-release-unsigned.apk` (6.7 MB, not signed: `apksigner` says DOES NOT VERIFY) and `app-release.aab` (12 MB, unsigned). R8 and resource shrinking ran |
| Same without `NB_ALLOW_PLACEHOLDER_RELEASE` and without the real values | Fails on purpose in `checkReleaseConfig`: "Release build needs NB_SERVER_URL, NB_CONTACT_EMAIL, NB_ADMOB_APP_ID, NB_ADMOB_BANNER_ID, NB_ADMOB_INTERSTITIAL_ID" |
| Same with `--offline` | Fails only because `org.jetbrains.kotlin:compose-group-mapping:2.4.21` is not in the offline Gradle cache of this container (R8 mapping task); it builds as soon as Maven Central is reachable (CI does). Not a project problem |

So a release build works without the owner's key: it needs the five `NB_*` values or the placeholder switch, and it comes out unsigned. With the four `NB_RELEASE_*` signing values and the real IDs it signs; signing with placeholders is refused.

The Maven 429 problem did not appear in this run.

## 3. Defects (`docs/qa/DEFECTS.md`)

Release gate: **no Blocker and no Major is open** (Major: 9 fixed, 2 closed by spec; DEF-051 was the last one).

Open, all Minor or Trivial, none blocks the release:

| ID | Sev | What | Why it can wait |
| --- | --- | --- | --- |
| DEF-061 | Minor | At 200 % font in a 360 x 320 dp window, 7 baseline entries are still clipped (de `reconnecting_title`, `duel_round`, `home_help`; en/de `search-60s`) | Extreme setting; every control is reachable (0 missing / unreachable); check on a device |
| DEF-016 | Minor | Computer levels are not in the README; Easy may be too strong | Product decision |
| DEF-019 | Minor | Locale fallback (zh-TW/HK, pt-PT) unspecified | Falls back to the closest shipped language |
| DEF-020 | Minor | Ad details open: cap persistence, forfeit counting | Rule is implemented; README gap |
| DEF-023 | Minor | Account tokens never expire | Design risk, no known abuse path |
| DEF-026 | Minor | Who may practise (README vs protocol) | Spec wording |
| DEF-027 | Minor | Account-creation limit: carrier NAT, global cap | Raise `10/IP/h` if real users are blocked after launch |
| DEF-043 | Minor | 3 throwaway accounts can restrict a player's chat | Restriction is temporary (24 h); owner reviews reports daily |
| DEF-045 | Minor | Haptics default not written in README | Doc only |
| DEF-046 | Minor | Age band vs reinstall: a reinstall asks the age again (not backed up) | Written down in `POLICY_CHECKLIST.md` 11.4; accepted limit of a device-only design |
| DEF-047 | Minor | Age-band SDK flags and boundary rule not in README | Rule and flags are in code and `POLICY_REVIEW.md` R20 |
| DEF-024 | Trivial | README says "smaller Data safety declaration" | Doc only |
| DEF-025, DEF-029, DEF-036 | Trivial | `room_left{inactive}` to a disconnected player; duplicate JSON keys accepted; simultaneous-finish wording | No player impact |

Fixed in the last round and re-checked against their tests: DEF-051, 052, 053, 054 to 060, 062, 063 (and DEF-044 by a docs decision). DEF-062 note: the Mobile Ads auto-init provider is removed from the merged debug and release manifests; whether any traffic leaves before the age answer still needs one capture on a device (checklist item 6).

Design: `design/REVIEW_PHASE2_ROUND2.md` still says "Not signed off" (200 % / small-window states D2-37..41, D2-03, D2-34, transfer mode D2-40, de/ru renders, language picker, opponent-log sheet and server-error dialog D2-43). The later fix rounds changed many of those screens and the layout sweeps are clean except the 7 entries above, but no third design review has been written. Ask the design lead for a round-3 verdict before the production release.

## 4. Manual checklist on a real device (Android 10+ phone, plus one small/large screen if possible)

From `docs/qa/TEST_PLAN.md` 14.1 ("Remaining manual") and the DEFECTS notes. Use the release-like build from Play internal testing for items that need Google Play.

1. Offline first launch in real airplane mode: age, nickname, tutorial, full match vs computer, result; kill the app and relaunch offline; go online and check the account is registered (AC-OFF).
2. Banners: real AdView layout, spacing to buttons, 320 dp creative, hidden in small windows (AC-ADS).
3. Interstitial: real load, show, close, back from the ad, process death during the ad; never during a match, secret entry, at start or exit; cap 1 per 3 matches and 4 minutes (AC-ADS).
4. UMP consent form with an EEA debug geography; refusal still shows non-personalized ads; "Privacy options" row only when required (AC-ADS consent).
5. Age screen: under 14 stops the app (no account, no ads); 14-15 / 16-17 / 18+ give CHILD / TEEN / normal treatment (debug proxy); clear-data and reinstall behaviour (DEF-046); Play Age Signals test responses (AC-AGE).
6. Traffic capture (DEF-062): before the age answer, after an under-age answer and after buying `remove_ads`, no request goes to Google ad hosts; data safety answers match the traffic (hosts and fields).
7. Purchase with a license tester on a Play-installed build: price shown equals Play's, pending purchase does not remove ads, ads disappear at once, survive reinstall and a second device, Restore purchase, refund from the Console brings ads back, order shows "acknowledged", delete account keeps the purchase (AC-BILL).
8. Auto Backup restore on a second phone with the same Google account: token comes back, same account (AC-BAK-01/04, DEF-008). The age band must NOT come back.
9. Server restart during a match (Render deploy): players see the restart message, no result recorded.
10. Window and font: split-screen and free-form resize while playing, rotation, 200 % font on Android 14+, TalkBack (AC-WIN, AC-FONT-01, DEF-061).
11. Languages: native-speaker look at the 10 languages (AC-L10N), and a check of the font-dependent items from DEF-054..060 with the real device fonts.
12. Review room: join with `REVIEW_ROOM_CODE` on the deployed server, bot plays and chats, report and block work, no interstitial shown (RELEASE_GUIDE section 6).
13. Two devices: quick match (quick phrases only), private room with free chat, report, block, mute all chat, transfer code to a second phone, delete account.
14. Sound: listen on a phone to `design/sound/audition.html` cues in the game (timer warning: one cue at 10 s, ticks for the last 5 s); haptics.

## 5. 사장님만 하실 수 있는 일 (순서대로)

각 항목의 "넣을 곳"은 프로젝트 안에서 그 값이 들어가는 위치입니다. 자세한 클릭 순서는 `RELEASE_GUIDE.md` 장 번호를 보세요.

1. ✅ **게임 전용 이메일 만들기** — 완료: `numbaseball.help@gmail.com` (문서에 반영됨, 빌드 값 `NB_CONTACT_EMAIL`은 출시 빌드 때 넣음) (Gmail, 2단계 인증). `RELEASE_GUIDE.md` 1장.
   - 넣을 곳: 빌드 값 `NB_CONTACT_EMAIL` (설정 화면의 문의 주소), `docs/play/PRIVACY_POLICY.md` / `TERMS_OF_USE.md` / `DELETE_ACCOUNT_PAGE.md`의 `[연락처 이메일]` (한국어·영어 본문 모두), `RELEASE_GUIDE.md` 6장의 `[연락처 이메일]`, Play Console 개발자 이메일, AdMob.
2. ◐ **게임 전용 전화번호, 운영자 정보 정하기** — 성명 서상현 / Sanghyun Suh, 개발자 표시명 Number Baseball 반영. 주소(집 또는 비상주 사무실 결정 중)와 070 번호 남음. (본인 명의). 한국 개인 계정과 트레이더는 이 번호가 스토어에 공개됩니다.
   - 넣을 곳: `docs/play/PRIVACY_POLICY.md`와 `TERMS_OF_USE.md`의 `[운영자 성명]`, `[운영자 주소]`, `[게임 전용 전화번호]`, `[개발자 표시명]`, `[시행일]` (본문 두 언어 모두). Play Console 결제 프로필과 같은 값이어야 합니다.
3. ✅ **도메인 사기 + 서버 주소 정하기** — 구입: `quietdual.com` (가비아, 네임서버 기본). 문서 반영, 빌드 값 `NB_SERVER_URL`=`https://quietdual.com`은 출시 빌드 때. DNS는 Render 배포 후 연결. (연 1~2만 원, WHOIS 비공개). 서버는 4번에서 만든 Render 주소에 연결합니다.
   - 넣을 곳: 빌드 값 `NB_SERVER_URL` (예: `https://도메인`), 문서의 `[도메인]` (개인정보처리방침 4곳, `RELEASE_GUIDE.md`, 약관), Play Console 개인정보처리방침 URL `https://도메인/privacy`, 계정 삭제 URL `https://도메인/delete-account`, 스토어 웹사이트 칸, AdMob 개인정보 메시지 URL.
4. **서버 배포 (Render)**: `backend/server/DEPLOY.md` 순서대로. Starter 요금제 + 디스크 `/data` 1GB, Docker (`backend/server/Dockerfile`), Health check `/health`.
   - 환경 변수: `ADMIN_TOKEN` (16자 이상 무작위), `REVIEW_ROOM_CODE` (5글자, 예 `PLAYR`), `TRUSTED_PROXY_HOPS=1`, `QUICK_MATCH_FREE_CHAT=false`, `MIN_AGE=14`, 나중에 `APP_ADS_TXT`. `PORT`, `DATA_DIR`, `RELAXED_RATE_LIMITS`, `TEST_HOOKS`는 넣지 않습니다.
   - 확인: `/health`, `/privacy`, `/terms`, `/delete-account`, `/app-ads.txt` 가 열리고 **대괄호 `[ ]`가 하나도 남아 있지 않은지** 직접 봅니다. 서버는 대괄호가 남아 있어도 막지 않습니다.
   - 넣을 곳: `REVIEW_ROOM_CODE` 값은 Play Console "앱 액세스" 안내문(`RELEASE_GUIDE.md` 6장의 `[REVIEW_ROOM_CODE]`)에도 넣습니다. Render가 쓰는 개인정보·약관 글은 `docs/play/*.md`라서, 위 1~3번 대괄호를 채운 뒤 푸시해야 서버 페이지가 바뀝니다. 앱 안의 약관·방침도 빌드할 때 같은 파일에서 만들어지므로 **대괄호를 채운 뒤에 AAB를 빌드**해야 합니다.
5. **AdMob 계정, 앱, 광고 단위 2개 (배너 1, 전면 1), 실제 ID** (`RELEASE_GUIDE.md` 9장). 보상형 광고는 만들지 않습니다. 민감 카테고리 차단, 유럽 규정·미국 주 규정 메시지 게시, 세금 정보.
   - 넣을 곳: 빌드 값 `NB_ADMOB_APP_ID` (`ca-app-pub-…~…`), `NB_ADMOB_BANNER_ID`, `NB_ADMOB_INTERSTITIAL_ID`. 저장소 파일에는 넣지 않습니다. 광고 등급 상한은 6번 등급 결과에 맞춰 `NB_MAX_AD_CONTENT_RATING` (기본 `G`).
   - 개발 중과 디버그 빌드는 Google 테스트 ID만 씁니다. 실제 광고를 직접 누르지 마세요.
6. **Play Console 개발자 계정** (개인, 25달러, 신원 확인, 휴대폰 인증) + **트레이더 신고("트레이더")** (`RELEASE_GUIDE.md` 2장). 앱 만들기(3장), 패키지 이름 `app.numbaseball`은 한 번 올리면 못 바꿉니다.
   - 대상 연령: **13~15, 16~17, 18+** 선택, 13세 미만 제외, **미성년자 접근 제한은 끔**. 앱 안의 최소 나이는 **만 14세** (코드와 서버 기본값이 이미 14: `NB_MINIMUM_AGE` 기본 14, 서버 `MIN_AGE` 기본 14). 13세로 바꾸려면 약관·방침·코드를 함께 바꿔야 합니다(권장하지 않음).
   - 양식: 광고 = 예, 광고 ID = 예, 콘텐츠 등급 설문(사용자 간 상호작용 = 예, 디지털 상품 구매 = 예), 데이터 보안(`POLICY_CHECKLIST.md` 5절, 구매 내역 포함), 계정 삭제 URL, 앱 액세스 안내문(6장).
7. **결제 프로필(판매용)과 `remove_ads` 상품** (`RELEASE_GUIDE.md` 10장). 한국 개인이 인앱 상품을 팔 때 사업자등록·통신판매업 정보를 요구하는지 먼저 확인하고 세무사와 상의하세요. 막히면 1.0은 광고만으로 내고 업데이트로 여는 방법이 있습니다.
   - 상품: 제품 ID **`remove_ads`** (정확히, 코드 상수 `REMOVE_ADS_PRODUCT_ID`), 1회 구매(소모성 아님), 4.99달러, **활성**. 이름·설명은 10-2의 표를 붙여 넣습니다. 앱을 내부 테스트에 한 번 올린 뒤에 만들 수 있는 경우가 많습니다.
   - 넣을 곳: 프로젝트에는 넣을 값이 없습니다(ID가 코드에 고정). 상품이 활성이어야 앱이 가격을 가져옵니다.
8. **라이선스 테스터 등록과 테스트 구매** (`RELEASE_GUIDE.md` 10-3): Play Console 설정 → 라이선스 테스트에 테스트용 Gmail 추가, 같은 계정을 내부 테스트 테스터에도 넣고 **Play 스토어로 설치**해서 구매·복원·환불을 확인합니다 (위 4장 7번).
9. ✅ **앱 이름 확인** — 2026-11-26 사장님 확인: KIPRIS에 '숫자야구'·'조용한 한판' 상표 없음, Play 스토어에 '조용한 한판' 없음('숫자야구' 이름의 게임은 여러 개). 웹 검색에서도 같은 이름 없음.: Play 스토어에서 같은 이름 검색, **KIPRIS**(kipris.or.kr)에서 "숫자야구 조용한 한판" 상표 검색. 이름을 바꾸면 `app_name` 문자열과 `STORE_LISTING.md`의 `[앱 이름]` 한 곳씩만 고치면 됩니다.
10. **업로드 키 만들기** (`RELEASE_GUIDE.md` 4장, `keytool`, 저장소 밖에 보관, 백업 2곳, 비밀번호 관리자). Play 앱 서명을 사용합니다.
    - 넣을 곳: 빌드 값 `NB_RELEASE_STORE_FILE` (파일 경로), `NB_RELEASE_STORE_PASSWORD`, `NB_RELEASE_KEY_ALIAS` (`upload`), `NB_RELEASE_KEY_PASSWORD`. 이 네 값과 2·3·5번의 `NB_*` 값이 모두 있어야 서명된 AAB가 만들어집니다. 지금 GitHub Actions에는 서명 단계가 없으므로, 처음에는 Android 담당이 자기 컴퓨터에서 빌드합니다.
11. **스토어 등록정보와 그래픽** 올리기 (`STORE_LISTING.md` 10개 언어, `design/store/` 아이콘·그래픽, 스크린샷 6장은 광고와 실제 닉네임이 없는 것으로).
12. **테스트 단계**: 내부 테스트 → 비공개 테스트 **12명 이상 × 14일 연속** → 프로덕션 액세스 신청 → 단계적 출시 (`RELEASE_GUIDE.md` 7장).
13. **AdMob 연결 마무리** (앱이 공개된 뒤): 스토어 연결, `app-ads.txt` 한 줄을 Render 환경 변수 `APP_ADS_TXT`에 넣고 `https://도메인/app-ads.txt` 확인 (9-4).
14. **출시 후 매일**: 신고 목록 확인, 이메일 삭제 요청 처리, Play 정책 메일 확인 (`RELEASE_GUIDE.md` 11장, `DEPLOY.md` 6장).

## 6. Documents checked against the code (2026-11-26)

Matches: manifest permissions (table in `POLICY_CHECKLIST.md` 11.4), `AD_ID` kept, targetSdk 36, 16 KB page size (the only native libraries are two AndroidX ones, 64-bit ELF segments aligned to 0x4000 in the release AAB), Auto Backup limited to the account token file (`ResourcesTest`, `AgeGateQaTest`), age band in a separate non-backed-up store, `ageConfirmed` boolean as the only age data sent, no analytics or crash SDK, terms version served by the server is `2026-11-v3` (read from the bundled terms), public pages `/privacy` `/terms` `/delete-account` `/app-ads.txt` and no `/` page, review room and its bot games (no stats, no interstitial), purchase token never sent to the server, interstitial rules (1 per 3 matches, 4 minutes, vs computer and online, none in practice or review room), banner only on home, results, profile (and the team result), Settings items (privacy, terms, privacy options when required, mute all chat, delete account with the purchase note, contact, restore purchase).

Doc mismatches fixed in this pass:

- `RELEASE_GUIDE.md`: reviewer text now says the age screen asks birth year and month; the signing and AdMob values are the `NB_*` build values (not `UPLOAD_*` / `ADMOB_*` secrets), CI makes only an unsigned check bundle.
- `POLICY_REVIEW.md` (R11, Android items 3, 6, 10) and `POLICY_CHECKLIST.md` 11.6: ads are always muted (README decision, DEF-044); Contact shows the address and long-press copies it (no mail app); age screen asks birth year and month.
- `PRIVACY_POLICY.md` (Korean and English): the age band is computed from the birth year and month typed on the screen; only the band is stored.

Code and process points to decide (not changed, per instructions):

1. `Application.kt` keeps `DEFAULT_TERMS_VERSION = "2026-11-v1"` as a fallback. It is only used if the bundled terms cannot be read, and the pages are bundled, so it is harmless; update it to `2026-11-v3` when someone touches the server.
2. Nothing stops a signed release or the server from shipping the `[대괄호]` placeholders of `docs/play/*.md` (the in-app legal texts and the web pages are generated from them). A check in `generateLegalTexts` for a leftover `[` placeholder when the release is signed would remove the risk.
3. The CI workflow only runs on pushes to branch `claude/number-baseball-game-1u4o9o` and has no signing step; if the branch is renamed or merged to `main`, change the `branches:` list. Heavy jobs fit their limits (backend 30 min, Android 60 min; locally the whole Android test run is under 3 minutes warm).
4. DEF-047 / README: the per-band SDK settings and "always muted" are only in code and the Play docs; README could point to them.
