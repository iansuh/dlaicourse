# Policy review of the README decisions

Owner: Release lead. Reviewed: 2026-11-21, updated 2026-11-25 for the optional one-time "Remove ads" purchase and the interstitial after online matches (R2, R11, R17, R22, teammate lists), and the same day 2026-11-21 for the owner's decisions (trader / worldwide, audience 13+, "숫자야구"-style name, quick phrases only in quick match), against `../../README.md` and `../protocol.md` v1. Sources and verification notes are in `POLICY_CHECKLIST.md` (official Google pages were confirmed through search extracts; they could not be opened directly from this container).

Severity: **Critical** = blocks launch or breaks an owner requirement; **High** = likely rejection or policy violation if not done; **Medium** = real risk or notable gap; **Low** = good practice.

## 사장님 결정 요약 (Korean summary for the owner)

**이미 정하신 것 (2026-11-21)**
- 광고 + 전 세계 출시(유럽·영국 포함) + **트레이더 신고** (R1 B안). 늦어도 2027년 3월 1일부터 스토어에 **실명, 주소(결제 프로필 주소), 전화번호, 이메일**이 공개됩니다. 공개 이메일은 스팸이 많이 오므로 **게임 전용 이메일**을 그대로 권장합니다. 전화번호도 본인 명의의 게임 전용 번호를 권장합니다.
- 대상 연령 13세 이상, 미성년자 접근 제한 끔 (R3).
- 앱 이름은 "숫자야구" 계열 (R14). 추천: **숫자야구: 조용한 한판 / Number Baseball: Quiet Duel** (`STORE_LISTING.md`).
- 빠른 대결은 정해진 문구만, 자유 채팅은 비공개 방에서만 (R4 해결).
- **(2026-11-25) 광고 제거 1회 구매 `remove_ads`, 4.99달러**, Google Play 결제, 구독 아님, 서버 확인 없음. 전면 광고는 컴퓨터 대결뿐 아니라 **온라인 대결 뒤, 결과 화면으로 가는 길**에도 나옵니다(3판에 1번, 4분 간격). 정책상 가능하지만 R11·R22의 안전장치가 필요합니다.

**광고 제거 때문에 사장님이 하실 일 (R22)**
1. **결제 프로필을 "판매"용으로 설정**합니다(은행 계좌, 세금 정보). 계정이 판매자(merchant) 계정이 됩니다. 주소는 이미 트레이더로 공개되므로 새로 공개되는 것은 없습니다.
2. **한국 개인 개발자가 인앱 상품을 팔려면 사업자등록·통신판매업 신고 정보를 요구받을 수 있습니다.** 확인하지 못했으니 상품을 만들기 전에 Play Console 결제 프로필 화면에서 직접 확인하고, 세무사에게 개인사업자 등록 여부를 물어보세요. 막히면 1.0은 광고만으로 출시하고 업데이트로 `remove_ads`를 넣는 방법이 있습니다.
3. 구매 수수료(처음 연 100만 달러까지 15%로 알고 있음, 확인 필요)와 한국 세금은 사장님 몫입니다.
4. 약관 버전이 `2026-11-v3`로 올라갔습니다(구매 문구 추가). 출시 전이라 영향은 없지만 담당자가 테스트의 `2026-11-v2`를 확인해야 합니다.

**아직 정하실 것**
1. **최소 이용 나이를 만 14세로** (전 세계 공통) 정하는 것을 추천합니다 (R19). 한국 개인정보 보호법은 만 14세 미만 아동의 개인정보를 "동의"로 처리할 때 법정대리인 동의를 요구하고, 개인정보위는 만 14세 미만에게 맞춤형 광고용 행태정보를 쓰지 말라고 권고합니다. 보호자 동의 절차를 만들지 않으려면 14세 미만은 받지 않는 것이 가장 안전합니다. 13세로 두려면 보호자 동의 절차나 한국 이용자만 따로 막는 방법이 필요합니다.
2. 도메인 구입 (연 1~2만 원, R8).
3. 출시 전 KIPRIS·Play 검색으로 앱 이름 최종 확인.

---

## R1. Ads make the owner a "trader": home address and phone become public — **DECIDED (option B, 2026-11-21)**

**Decision.** Personal account, ads, worldwide including EEA/UK, declared **trader**. The owner accepts that legal name, address, phone and email are shown on the store page. The analysis below is kept for the record.

**What follows from B:** declare "trader" at sign-up; the public address is the payments-profile address (it must be the owner's real legal address, not a borrowed one); a verified public phone; the email shown should still be a dedicated game email; provide `/terms` if Play asks for a terms-of-service link; EEA/UK users see the owner's name in "Offered by" from 2027-03-01. If the owner later wants these details off the listing, the route is option D (organization account with a business address), not a non-trader declaration.

**Finding.** Every Play account must declare trader or non-trader (new accounts at sign-up, existing ones by 2027-02-01). Google's trader criteria explicitly include **ad-sponsored apps** (regular remuneration). Traders have **name, address, phone and email** shown on the store listing; for a personal account the address comes from the payments profile, i.e. normally the home address. Display starts **2027-03-01** (third-party summaries say worldwide; EEA/UK users also see the trader name in "Offered by"). Non-traders show only email and country. Google's page says developers who **do not distribute in the EEA or UK may declare non-trader**. False non-trader declarations can get the account terminated. (Trader status declaration, https://support.google.com/googleplay/android-developer/answer/17594752)

This directly conflicts with the owner's requirement that no address or phone be shown to players.

**Options**

| | Ads | EEA + UK distribution | Account / declaration | Shown publicly | Cost / effort |
| --- | --- | --- | --- | --- | --- |
| A | No ads in v1 | Yes | Personal, non-trader | Developer name, email, country, Korean developer phone (R2); legal name possibly (R2) | None; ads can be reconsidered later, but becoming a trader then exposes the same details |
| B | Ads | Yes | Personal, trader | Everything in A **plus legal name, home address, phone, email** (from 2027-03-01) | None, but breaks the owner's requirement |
| **C (recommended)** | Ads | **No** (deselect EU-27, Iceland, Liechtenstein, Norway and the UK in Countries/regions) | Personal, non-trader | Same as A | Loses EEA/UK players (France, Germany, Spain, Italy, Poland...). French/German/Spanish speakers outside the EEA (Switzerland, Canada, Latin America, Africa) are kept. Expected ad revenue is small anyway (`POLICY_CHECKLIST.md` 11.9) |
| D | Ads | Yes | **Organization** account as a registered business (개인사업자) with D-U-N-S, trader | Business name, **business** address and phone, email, D-U-N-S; Korean users also see business registration number and mail-order (통신판매업) number | Business registration, a business address that is not home (for example a shared/virtual office where registration is allowed), D-U-N-S (free, up to 30 days), tax filings; Korean public business records can still reveal the representative's name. Organization accounts skip the 12-tester rule |

**Recommendation at the time:** C. **Owner chose B.**

README: done ("Distribution (owner decision 2026-11-21)").

## R2. Public phone, email and name — **High (actions remain)**

Update 2026-11-21: as a trader, the legal name, address, phone and email are public anyway (R1), so the "legal name may be shown" question no longer matters. The Korean personal-account phone rule below still applies from day one. The actions (dedicated email, dedicated number in the owner's name, brand-style developer name) still stand.

**Finding.** Korean developers with personal accounts must provide and verify a **developer phone number that is displayed on Google Play**, or publishing is blocked (Requirements for specific countries, https://support.google.com/googleplay/android-developer/answer/6223646 ; Verify identity, https://support.google.com/googleplay/android-developer/answer/10841920). The developer email is public for every account. One official extract says personal accounts display the **legal name**, another says non-traders show only email and country **[CONFLICT]**.

**Change / owner actions**
- Create a **dedicated game email** (no real name in the address) and use it as the developer email, support email, privacy contact and child-safety contact.
- Get a **separate phone number** for the game (for example a second SIM/second line). It must receive the verification code by SMS or automated voice call. Whether internet-phone (070) or virtual numbers are accepted is **[UNCONFIRMED]**; test before relying on one.
- Set the **developer name** to a game or studio name (the legal name is shown separately as a trader).
- During sign-up, read the screen that shows which details will be public before submitting.
- Play Billing: since 2026-11-25 the app sells the in-app product `remove_ads`, so the account is a merchant account. The address is public anyway as a trader; see R17 and R22 for what else Play and Korea may require.

## R3. Target audience — **DECIDED: 13 and over, Restrict Minor Access off (2026-11-21)**

The earlier recommendation (18+ only with Restrict Minor Access) was not taken. Consequences for a 13+ audience, all now required:

- Play Console target groups 13–15, 16–17, 18+; nothing under 13, so the Families policy does not apply. Keep the listing and art free of anything aimed at children (Google can treat a child-appealing app as child-directed).
- Minimum age in the app: **14** recommended (R19), enforced by a neutral age screen at the Terms gate.
- Chat safety is mandatory: quick phrases only in quick match (R4), report/block/mute, moderation (R10), child-safety section in the Terms (R6).
- Ads must not exceed the app's content rating, and minors get restricted ad treatment (R20).
- Texas age law and Play Age Signals now matter (R21).

## R4. Quick match + free-text chat looked like "random chat" — **RESOLVED by README (2026-11-21)**

The README now limits quick match to preset quick phrases; free text exists only in private rooms (people who shared a room code). With that, the app has no random free-text chat with strangers and no anonymous chat as a core feature, so the 2026 anonymous/random chat rules (block minors, Child Safety Standards) should not apply (Policy announcement 2026-07-15, https://support.google.com/googleplay/android-developer/answer/17134731).

**Conditions that keep it resolved**
- `QUICK_MATCH_FREE_CHAT` must stay **off** as long as the audience includes minors. Turning it on would recreate a random free-text chat with strangers, and the age-restricted policy would then require blocking minors, which contradicts R3. Add this sentence to the README next to the flag.
- Room codes can be shared publicly, so strangers can still meet in private rooms: keep the child-safety section, report/block/mute, the 3-reporter automatic restriction and daily review.
- Recommended extra for teens: free text unlocks only after the account has finished one online match (anti-abuse; already suggested in R10).

## R5. Account deletion without the app — **DECIDED: by email (owner, 2026-11-21, app only)**

Play requires a web resource where users can request deletion outside the app, and Google accepts "a customer service email" as that resource, next to a link or a form (https://support.google.com/googleplay/android-developer/answer/13327111). So `/delete-account` is a short static page: in-app deletion, or email `Nickname#1234` to the contact address (`DELETE_ACCOUNT_PAGE.md`). No web form, deletion code or 30-day pending state; drop `POST /delete-account`, `/me/deletion-code` and the deletion-code UI from `protocol.md` and the app.

Operator rule (because we cannot verify an email sender): delete within 7 days unless the account was used in the app after the email arrived; in that case reply asking the player to use Settings → Delete account. Keep request emails 30 days after handling (stated in the privacy policy).

## R6. Terms acceptance, Terms page and child-safety text are missing — **High → adopted in README 2026-11-21** (implement)

**Finding.** UGC policy: Terms of Use must exist and users must accept them before posting, without a way to skip (https://support.google.com/googleplay/android-developer/answer/9876937). Child Safety Standards (if a reviewer puts us in scope) need public standards that explicitly prohibit CSAE (https://support.google.com/googleplay/android-developer/answer/14747720). Neither the README nor `protocol.md` has a Terms page or an acceptance record.

**Exact change**
- README "Chat safety": add "Terms of Use acceptance before the first online match or chat (cannot be skipped); public Terms page with a child-safety section."
- Server: `GET /terms` serving `TERMS_OF_USE.md` (anchor `#child-safety`); store `termsVersion` and `termsAcceptedAt` per account; `PUT /api/v1/me/terms {version}`; refuse `chat`/`phrase`/queue/room joins with a new error `terms_required` until the current version is accepted.
- App: terms screen (summary + link + Agree), shown again when the version changes.

## R7. Reviewers need a live server and an opponent — **High → adopted in README 2026-11-21** (implement)

**Finding.** Reviewers must be able to use the app at any time from any location (Prepare your app for review, https://support.google.com/googleplay/android-developer/answer/9859455). A sleeping free-tier server or an empty matchmaking queue makes online features look broken.

**Exact change**
- Hosting: always-on instance with a persistent disk during closed testing, review and production (the prototype deploy notes say Render Free sleeps after 15 minutes and loses data).
- Server: optional **review room**. Env var `REVIEW_ROOM_CODE` (5 characters); when set, joining that private room starts a 1v1 4-digit duel against a server bot that plays slowly and posts one quick phrase and one text line, so the reviewer can test Report and Block. Disabled when unset.
- App access text for Play Console: `RELEASE_GUIDE.md` section 6.

## R8. Hosting domain and developer website — **Medium**

**Finding.** `app-ads.txt` must sit at the root of the developer website listed on Play; AdMob checks that host (one subdomain level) and the root domain (https://support.google.com/admob/answer/9363762). `name.onrender.com` should work because `onrender.com` is on the Public Suffix List, but Google documents only Firebase `*.web.app` explicitly **[UNCONFIRMED for Render]**. A host name also appears publicly in the listing and in privacy/deletion URLs.

**Change**
- Buy a custom domain (WHOIS privacy on), point it at the server, and use it everywhere: developer website, `/privacy`, `/terms`, `/delete-account`, `/app-ads.txt`.
- Never put the owner's real name in the domain, the Render service name, or the GitHub-visible config.
- No landing page (app only). The root `/` may 404 or redirect to `/privacy`. Keep HTTP → HTTPS redirects on the same host.

## R9. Report data retention and snapshot clean-up — **Medium → adopted in README 2026-11-21**

**Finding.** `protocol.md` stores a snapshot of the reported message plus the last 20 room messages (with sender nicknames) and a free-text note, but sets no retention period for resolved reports, and on account deletion only erases reports **about** the deleted player. Their messages remain, with nickname, inside snapshots of reports about other people. Play requires retained data to be disclosed; Korean law requires a stated retention period.

**Exact change (server)**
- Purge resolved reports and their snapshots **180 days after resolution** (daily job).
- On account deletion, replace that player's id and nickname with a "deleted player" marker inside every other snapshot.
- These are already written into `PRIVACY_POLICY.md` and `DELETE_ACCOUNT_PAGE.md`; if the backend chooses different values, update both documents.

## R10. Moderation operations — **Medium → partly adopted** (admin chat restriction and 3-reporter auto restriction are in the README; daily review is the owner's job)

**Finding.** "Timely" action on reports is required and apps that keep failing to handle complaints are removed (UGC policy). Device accounts are free to create, so bans are easy to evade.

**Change**
- Server admin API: add `POST /admin/v1/accounts/{id}/chat-restrict {hours, reason}` (chat restriction, not a full ban).
- Automatic temporary chat restriction (24 h, pending review) when **3 distinct accounts** report the same player within 24 hours.
- New accounts: free text only after one finished online match (also in R4).
- Owner: check open reports **at least once a day**; target action within 24 hours, never more than 3 days (the Terms promise this).
- Report CSAE to the relevant authority and keep the dedicated email monitored (child-safety contact).

## R11. Ads implementation details — **Medium**

The README's ad rules are compliant with the Play Ads policy and AdMob guidance. Add these specifics (README "Monetization"):
- **Updated 2026-11-25.** Interstitial is shown only **after a finished match, vs computer or online, on the way to the results screen**, never the instant the last guess lands, not within 1 second of the result being decided (AdMob advises a pause in tap-heavy games), cap 1 per 3 finished matches and 4 minutes apart (online and computer matches counted together; a team match counts once). Never during a match, secret entry or the waiting practice game, never on app start or exit, never while a rematch or the next team duel is about to start. Because an online match ends without a tap, add: not while the chat box or keyboard is open; skip the ad if the player already left the screen; never in the review room / practice game, so Play reviewers always reach the result screens without an ad; skip it if `remove_ads` is owned. Compliance reasoning: `POLICY_CHECKLIST.md` 11.1.
- Banner keeps clear space from every button (no tappable control directly above or below it) and is hidden when the window is too small (pop-up window, small split-screen) to keep that space; banners never overlap content.
- `setMaxAdContentRating` set from the IARC result, never above it (see R20).
- Muting: Google warns muting reduces video ad eligibility and revenue. Keep ads muted while the game's sound setting is off (default); follow the setting if the player turns sound on.
- Consent: UMP `requestConsentInfoUpdate` on every launch → form if required → initialize Mobile Ads only when `canRequestAds()`; "Privacy options" in Settings when required; European regulations and US state messages published in AdMob in all 10 languages.
- Keep `com.google.android.gms.permission.AD_ID` (merged from the SDK); do not strip it.
- Expect small revenue (`POLICY_CHECKLIST.md` 11.9).

## R12. Data safety, privacy policy and SDKs must stay in sync — **Medium**

The Data safety form must match the privacy policy and include SDK data. Rule for the team: **no new SDK (analytics, crash reporting, Play Games, etc.) without telling the release lead first**, because each changes Data safety and `PRIVACY_POLICY.md`. Server logs must not contain chat text, tokens, codes or IP addresses beyond the in-memory rate limiter; hosting-provider log retention must be filled into the policy.

## R13. Honest listing and leftovers from the web prototype — **Low**

- Never write "no ads" or "ad-free" anywhere (store, onboarding, website).
- Do not port the prototype's donate button or the `donate: 'Support the developer'` strings (Payments policy).
- Screenshots never show ads or real players' nicknames.

## R14. App name — **DECIDED: "숫자야구" style (2026-11-21)**

Recommended set: **숫자야구: 조용한 한판 / Number Baseball: Quiet Duel** and the localized "quiet duel" family, with two alternatives per language (`STORE_LISTING.md` section 1). The bare names "숫자야구", "숫자 야구 온라인", "Number Baseball", "Number BaseBall Online" and "Number Baseball AI Battle" are already used on Play, so a distinctive descriptor is needed to avoid looking like someone else's app (Impersonation, https://support.google.com/googleplay/android-developer/answer/9888374). Never use "Mastermind", "Numer0n/ヌメロン" or "Excel"; no Excel-like visuals. The name lives only in `app_name` resources and the store title, and the descriptions do not repeat it, so it is easy to swap. Owner checks KIPRIS and a Play search on a phone before publishing.

## R15. Auto Backup and deletion — **Low**

Including the token in Auto Backup is fine and is disclosed in the privacy policy. On account deletion the app must clear the token immediately so a later backup cannot restore a deleted account; define `dataExtractionRules` (Android 12+) and `fullBackupContent` so only the token/settings file is backed up.

## R16. Inactive accounts — **Low (optional)**

Not required by Play. Deleting accounts unused for 24 months would reduce stored data. If implemented, add one row to the retention table in `PRIVACY_POLICY.md`.

## R17. Tip jar and other paid features — **Low** (the `remove_ads` purchase is decided: see R22)

A Play Billing tip jar makes no sense next to Remove ads and stays out; external donation links are not allowed. Any further paid item needs the same Billing set-up and the checks in R22.

## R18. Target API and build — **Low (do now)**

Target API 36 (required since 2026-08-31). Check the release bundle for native libraries (16 KB page size, enforcement date 2027-02-01 on the current Android page). Test Android 16 large-screen behaviour.

## R19. Korean children under 14: set the minimum age to 14 — **High**

**Finding.** PIPA Art. 22-2: when consent under the Act is needed to process a child's (under 14) personal information, the legal representative's consent is required and must be confirmed. Our data (nickname#tag, account ID, token hash, records, block list, reports with chat snapshots, IP) should be treated as personal information. Core processing can arguably rest on contract performance (Art. 15(1)4, no consent needed), which would not trigger Art. 22-2, but I found **no official confirmation** of that reading for children. And the ads SDK's behavioural data is the weak point: PIPC's 2024 behavioural-ads policy requires guardian consent for personalized ads combining behavioural data and identifiers for under-14 users and recommends not collecting behavioural data from known under-14 users. The PIPC 2022 children's guideline expects an age check (date of birth or "만 14세 이상" confirmation). Details and sources: `POLICY_CHECKLIST.md` section 18.

Game Industry Promotion Act Art. 12-3 (age verification and parental consent for youth sign-up) very likely does not apply to us (the decree limits it to larger businesses; 2026 amendment summary mentions SMEs with annual revenue of KRW 5 billion or more) **[UNCONFIRMED; check the decree text]**.

**Recommendation:** minimum age **14 worldwide** (not only in Korea: we cannot reliably detect Korean users, and it also keeps a margin above COPPA's 13). Play target groups stay 13–15/16–17/18+.

**Exact README change** (Audience line):
> - **Audience: 14 and over** (Play target groups 13–15, 16–17, 18+; nothing under 13). A neutral age screen on first launch, before any ad or account, stops the app below 14 (Korean PIPA guardian-consent rule). Restrict Minor Access stays off.

If the owner keeps 13 outside Korea, the app must detect Korea (unreliable) or build a legal-guardian consent flow for Korean 13-year-olds; I do not recommend either.

## R20. Ads with a 13+ audience — **High**

- **Ad rating vs app rating.** Play requires ads to be appropriate for the app's content rating; Google's own violation example is a Teen-rated ad in an Everyone-rated app (https://support.google.com/googleplay/android-developer/answer/9859655). The README says "a maximum ad content rating suitable for teens"; that is only right if IARC rates the app Teen/12+. **Change the README line to:** "Max ad content rating = the level matching the IARC rating (G for Everyone/3+, PG for E10+/7+, T for Teen/12+), set from build configuration."
- **Minors and personalized ads.** EEA digital age of consent is 13–16; we cannot collect parental consent. Use AdMob's age-restricted treatment from the age screen: 14–15 → `CHILD` (no personalized ads, no advertising ID sent, UMP does not ask for consent), 16–17 → `TEEN`, 18+ → normal UMP flow. If Play Age Signals reports a minor, use the stricter treatment. Set it before initializing the SDK. Google advises consulting legal counsel on the mapping; this is the conservative choice.
- **Sensitive categories.** Block gambling, dating, alcohol, sexual and get-rich-quick categories in AdMob.
- Revenue effect: non-personalized ads for under-16s and a G rating lower eCPM somewhat; acceptable given expected revenue (`POLICY_CHECKLIST.md` 11.9).

## R21. Texas app-store age law and Play Age Signals — **Medium**

Google says Texas SB 2420 is in effect (injunction stayed by an appeals court); Play Age Signals returns signals for Texas users with accounts created after 2026-05-28; Utah and Louisiana follow in 2027 (https://support.google.com/googleplay/android-developer/answer/16569691). Developers must use the age signals to provide age-appropriate experiences, restrict features tied to "significant changes" for supervised users until a parent approves, and notify Play of significant changes.

**Change:** integrate the Play Age Signals API (latest version) in the app; feed it into the age handling of R19/R20; before any release that changes social features, decide whether it is a significant change and notify Play if so.

## R22. Optional one-time purchase "Remove ads" (`remove_ads`, US$4.99) — **High (set-up and wording) — DECIDED by the owner 2026-11-25**

**Policy fit.** Allowed: digital feature sold only with Google Play Billing, no external payment link, one-time non-consumable, no gameplay advantage, no tip jar. Details and sources: `POLICY_CHECKLIST.md` 12.

**What changes in the documents:** Data safety gets "Financial info → Purchase history" (checklist 5, conservative declaration; payment details are never ours); IARC "digital purchases" = Yes and the listing shows "Offers in-app purchases" (checklist 9, 11.3); privacy policy 7-1 and terms 7 say Google Play processes the payment and refunds, what the app receives (state and token, no payment details, nothing sent to our server), that the purchase stays with the Google account when the game account is deleted and is restored with Restore purchase; delete-account page has the same note; store listing mentions the optional one-time purchase without a price (Metadata policy).

**Owner must decide or do** (also summarized in the Korean box at the top):
1. Payments profile for selling, bank account, tax details; **Korean business registration / 통신판매업 requirement for a personal account is unconfirmed** and could block creating the product.
2. Accept **no server check**: someone with a rooted phone can unlock it for themselves; no online or money loss. Fine for v1.
3. Price: US$4.99 default; Play applies local templates (about ₩6,500 in Korea); review per-country prices once (some countries look odd).
4. Minors: 14–17-year-olds can buy only as Google Play allows (parental approval for supervised accounts). No pressure patterns, no prompt during a match.
5. Terms version bumped to `2026-11-v3`.

**Implementation requirements** are in the Android engineer list (item 13) and the QA list.

## Decisions that are fine as they are

- No ID, password or email: minimal data, no password leaks, smaller Data safety form.
- In-app Delete account (immediate) and server-side deletion of account, tokens, codes, blocks and stats.
- Text-only chat, emoji removal, profanity masking, rate limits, report message/player, block (both directions, also in matching), mute all chat, quick phrases.
- Chat history only in memory (50 lines per room), never on disk.
- No ads during matches, secret entry, the waiting practice game, lobby or chat; interstitial cap stricter than AdMob's minimum; no app-open ads; no rewarded ads.
- Removal of the Excel disguise and the boss key (avoids trademark and deceptive-behaviour problems).
- No external donation links; the only sale is Play Billing `remove_ads`.
- Quick match limited to quick phrases; free text only in private rooms; automatic 24 h chat restriction after 3 reporters; admin chat restriction (README 2026-11-21).
- Terms gate, deletion without the app, 180-day report purge, no chat/IP logging, review room (README 2026-11-21).

---

## Requirements for teammates

### Android engineer (`android/`)
1. `targetSdk 36`; test large-screen orientation/resizability; check the release AAB for `.so` files (16 KB).
2. **Terms gate** before the first online match or chat: summary, link to `/terms`, Agree; cannot be skipped; send `PUT /api/v1/me/terms`; show again when the version changes (R6).
3. Settings must contain: Privacy policy (`/privacy`), Terms (`/terms`), **Privacy options** (only when UMP says required), Mute all chat, **Delete account** (with confirmation), **Contact** (opens email to the dedicated address), app version.
4. Show the Terms and Privacy policy texts inside the app (bundled), linking to the public URLs only as a fallback.
5. Report and block from a long-press on a message and from the player card; offer "Block too" after reporting.
6. Ads: UMP first, then `MobileAds` init only when `canRequestAds()`; `setMaxAdContentRating`; mute tied to the game sound setting; banner only on home / results / profile with clear space from controls and hidden in small windows; interstitial after a finished vs-computer or online match on the way to the results screen (R11), ≥1 s after the result, not while chat/keyboard is open, skipped if the player left the screen, skipped in the review room and practice game, skipped when `remove_ads` is owned, cap 1 per 3 finished matches and 4 minutes (shared by both match types); never during a match, secret entry, waiting practice, lobby, chat, app start or exit; test ad unit IDs in debug; real IDs from build config / CI secrets only; keep the AD_ID permission.
7. Auto Backup: back up only the token/settings; clear the token on deletion; `dataExtractionRules`.
8. No analytics or crash SDK without the release lead; no donate button. (The Play Billing library is allowed: it is the `remove_ads` purchase, R22.)
9. Quick-match chat limited to quick phrases (README); free text in private rooms, ideally only after one finished online match.
10. **Neutral age screen on first launch**, before any ad loads or account is created (no default, no hint): under 14 → explain the app is for 14+ and stop (no account, no ads), no immediate retry on that device; keep the age band on the device only (R19).
11. **Ad age treatment** before `MobileAds` init: 14–15 → `AgeRestrictedTreatment.CHILD`, 16–17 → `TEEN`, 18+ → `UNSPECIFIED` + UMP; max ad content rating from build config matching the IARC rating (R20).
12. **Play Age Signals API**: request on app open; 0–12 → refuse online features; 13–17 → minor ad treatment; respect `significantChangeStatus` for supervised users (R21).
13. **Remove ads (R22):** Play Billing Library (current version), one non-consumable product `remove_ads`; `queryProductDetailsAsync` and show Play's formatted price (never hard-code US$4.99); `queryPurchasesAsync` at start and on resume; grant only on `PURCHASED` (not `PENDING`); **acknowledge** after a purchase and retry at the next start (3-day limit, else Google refunds); no other payment path, no external link; entry points only in Settings and a quiet link on the results screen, no pop-up pitch, never during a match; **Restore purchase** in Settings re-queries Play; when owned: no banners, no interstitials, do not initialise the Mobile Ads SDK and do not show UMP (keep "Privacy options" hidden); when the purchase disappears (refund) ads return; keep the purchase token on the device only: **no logging, no sending to our server** (privacy policy 7-1 says so); Settings text and the delete-account dialog tell the player that deleting the game account keeps the purchase on the Google account and that it can be restored; debug build works with license testers.

### Backend engineer (`backend/`, `docs/protocol.md`)
1. Static pages only: `/privacy`, `/terms` (with `#child-safety`), `/delete-account` (text only), `/app-ads.txt`. Text from the `docs/play/*.md` files, both languages; no landing page, forms, cookies or analytics; HTTPS with same-host HTTP→HTTPS redirect.
2. Terms acceptance: `termsVersion`, `termsAcceptedAt`, `PUT /api/v1/me/terms`, error `terms_required` (R6).
3. Deletion without the app is by email (R5): remove the web deletion code and form; the admin tool must delete an account by `Nickname#tag` and show its last-active time.
4. Reports: purge resolved reports 180 days after resolution; on account deletion replace the player's id/nickname in other snapshots (R9).
5. Moderation: chat-restrict admin endpoint; auto 24 h chat restriction after 3 distinct reporters in 24 h (R10).
6. Logging: no chat text, tokens, codes or IP addresses in logs or the database (rate-limit IPs in memory only); tell the release lead the hosting provider's log retention and whether database backups exist and how long they are kept.
7. Review room behind `REVIEW_ROOM_CODE` (R7). Always-on hosting with persistent disk.
8. Confirm that secrets/guesses are memory-only and only results are stored (the privacy policy says so).
9. Keep `QUICK_MATCH_FREE_CHAT` off while the audience includes minors (R4).
10. Terms acceptance carries an age confirmation: `PUT /api/v1/me/terms {version, ageConfirmed: true}`; store only the boolean with the terms version and time, never the age (R19).

### Design lead (`design/`)
- Icon 512×512, feature graphic 1024×500, six screenshots per `STORE_LISTING.md` section 4 (no ads, fake nicknames), nothing resembling Excel. The app name may still change: keep it out of the icon, or make the wordmark easy to replace.
- Screens: first-launch neutral age screen, Terms gate, Settings items above, deletion-request notice, ad placements with spacing rules, quick-phrase-only chat in quick match.
- Keep art teen/adult-neutral: no mascots or styling aimed at young children (R3).
- Confirm font licenses allow app embedding.

### QA lead (`docs/qa/`)
- Ads never appear during a match, secret entry, waiting practice, lobby or chat; interstitial cap and timing for **both vs-computer and online matches** (shared counter, 4-minute gap, team match counts once); not while the keyboard/chat is open; skipped if the player left; never in the review room; banner hidden in small windows.
- Remove ads: buy with a license tester account (no charge), ads disappear at once without restart, also after reinstall and on a second device with the same Google account; Restore purchase works; pending purchase does not remove ads; refund (Play Console order → refund) brings ads back after the next check; purchase is acknowledged (Play Console order shows it); the Mobile Ads SDK is not initialised and UMP does not show after purchase; account deletion keeps the entitlement (restore after reinstall); price shown equals Play's; no ad request, no purchase token in the proxy traffic to our server or in logs.
- UMP: EEA/UK debug geography shows the form; refusal still shows non-personalized ads; Privacy options entry appears only when required.
- Deletion: in-app; `/delete-account` page loads as plain text with app/developer name and the contact email; admin deletion by `Nickname#tag`.
- Terms gate cannot be bypassed (app and server).
- Report/block/mute flows; auto chat restriction.
- Data safety answers match actual network traffic (proxy the app and check which hosts receive what).
- Age screen on first launch: no ad request before it completes; under-14 answers stop the app and cannot immediately retry; 14–15 / 16–17 / 18+ produce CHILD / TEEN / normal ad treatment (check ad requests in a debug proxy); Play Age Signals test responses.
- Max ad content rating equals the IARC rating.
- Closed test plan: 12+ testers opted in for 14 consecutive days.
