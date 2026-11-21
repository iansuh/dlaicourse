# Policy review of the README decisions

Owner: Release lead. Reviewed: 2026-11-21, against `../../README.md` (including "Monetization: ads (Google AdMob)") and `../protocol.md` v1. Sources and verification notes are in `POLICY_CHECKLIST.md` (official Google pages were confirmed through search extracts; they could not be opened directly from this container).

Severity: **Critical** = blocks launch or breaks an owner requirement; **High** = likely rejection or policy violation if not done; **Medium** = real risk or notable gap; **Low** = good practice.

## 사장님 결정 요약 (Korean summary for the owner)

1. **광고를 넣으면 2027년 3월 1일부터 스토어에 집 주소·전화번호·이름이 공개될 가능성이 큽니다** (Google 기준 "트레이더"). 아래 R1에서 A/B/C/D 중 하나를 골라 주세요. 제 추천은 **C: 광고는 넣고, 유럽경제지역(EEA)과 영국에는 출시하지 않고, "트레이더 아님"으로 신고**입니다. 개인 계정으로 가입할 때 바로 신고해야 하므로 가입 전에 정해야 합니다.
2. 한국 개발자의 **개인 계정은 전화번호가 스토어에 공개**됩니다(광고와 무관). 게임 전용 번호를 따로 준비하세요.
3. 개발자 이메일도 공개됩니다. **게임 전용 이메일**을 새로 만드세요. 개발자 표시명은 실명 대신 "Pitchcode" 같은 이름으로 하세요. 실명이 보일 수도 있다는 공식 문서도 있어 가입 화면의 "공개되는 정보" 미리보기를 꼭 확인하세요.
4. 대상 연령은 **만 18세 이상만**, "미성년자 접근 제한"을 켜는 것을 추천합니다(R3).
5. 앱 이름은 **Pitchcode**를 추천합니다. 출시 전에 KIPRIS에서 상표를 검색해 주세요.
6. 도메인을 하나 사는 것을 추천합니다(연 1~2만 원, R8).

---

## R1. Ads make the owner a "trader": home address and phone become public — **Critical**

**Finding.** Every Play account must declare trader or non-trader (new accounts at sign-up, existing ones by 2027-02-01). Google's trader criteria explicitly include **ad-sponsored apps** (regular remuneration). Traders have **name, address, phone and email** shown on the store listing; for a personal account the address comes from the payments profile, i.e. normally the home address. Display starts **2027-03-01** (third-party summaries say worldwide; EEA/UK users also see the trader name in "Offered by"). Non-traders show only email and country. Google's page says developers who **do not distribute in the EEA or UK may declare non-trader**. False non-trader declarations can get the account terminated. (Trader status declaration, https://support.google.com/googleplay/android-developer/answer/17594752)

This directly conflicts with the owner's requirement that no address or phone be shown to players.

**Options**

| | Ads | EEA + UK distribution | Account / declaration | Shown publicly | Cost / effort |
| --- | --- | --- | --- | --- | --- |
| A | No ads in v1 | Yes | Personal, non-trader | Developer name, email, country, Korean developer phone (R2); legal name possibly (R2) | None; ads can be reconsidered later, but becoming a trader then exposes the same details |
| B | Ads | Yes | Personal, trader | Everything in A **plus legal name, home address, phone, email** (from 2027-03-01) | None, but breaks the owner's requirement |
| **C (recommended)** | Ads | **No** (deselect EU-27, Iceland, Liechtenstein, Norway and the UK in Countries/regions) | Personal, non-trader | Same as A | Loses EEA/UK players (France, Germany, Spain, Italy, Poland...). French/German/Spanish speakers outside the EEA (Switzerland, Canada, Latin America, Africa) are kept. Expected ad revenue is small anyway (`POLICY_CHECKLIST.md` 11.9) |
| D | Ads | Yes | **Organization** account as a registered business (개인사업자) with D-U-N-S, trader | Business name, **business** address and phone, email, D-U-N-S; Korean users also see business registration number and mail-order (통신판매업) number | Business registration, a business address that is not home (for example a shared/virtual office where registration is allowed), D-U-N-S (free, up to 30 days), tax filings; Korean public business records can still reveal the representative's name. Organization accounts skip the 12-tester rule |

**Recommendation:** C for launch if ads stay; D later if EEA/UK demand justifies a business. If the owner does not care much about ad income, A is the simplest.

**Exact README change** (add under "Monetization"):
> - **Publisher identity (decided YYYY-MM-DD):** Personal Play account, declared **non-trader**. Not distributed in the EEA (EU-27, IS, LI, NO) or the UK, because ad-funded apps count as traders and traders must show name, address and phone publicly from 2027-03-01. Revisit with an organization account before adding EEA/UK.

(Replace with the option the owner picks.)

## R2. Korea-based personal accounts must show a phone number; email is public; legal name may be — **High**

**Finding.** Korean developers with personal accounts must provide and verify a **developer phone number that is displayed on Google Play**, or publishing is blocked (Requirements for specific countries, https://support.google.com/googleplay/android-developer/answer/6223646 ; Verify identity, https://support.google.com/googleplay/android-developer/answer/10841920). The developer email is public for every account. One official extract says personal accounts display the **legal name**, another says non-traders show only email and country **[CONFLICT]**.

**Change / owner actions**
- Create a **dedicated game email** (no real name in the address) and use it as the developer email, support email, privacy contact and child-safety contact.
- Get a **separate phone number** for the game (for example a second SIM/second line). It must receive the verification code by SMS or automated voice call. Whether internet-phone (070) or virtual numbers are accepted is **[UNCONFIRMED]**; test before relying on one.
- Set the **developer name** to the brand (e.g. "Pitchcode"), never the real name.
- During sign-up, read the screen that shows which details will be public before submitting.
- Do not add Play Billing (paid app, IAP, tip jar): a merchant account shows the full address (R17).

## R3. Target audience is not decided in the README — **High**

**Finding.** The README says the app is "not designed for children" but sets no age target. With random matching, free-text chat with strangers and ads, the low-risk choice is **18 and over only, with Restrict Minor Access on** (Target audience, https://support.google.com/googleplay/android-developer/answer/9867159). It keeps us out of the Families policy and the 2026 anonymous/random-chat restrictions on minors, and matches the real audience. Trade-off: users Google identifies as minors cannot find or install the app.

**Exact README change** (add a section "Audience"):
> ### Audience
> - Target age group in Play Console: **18 and over only**, with **Restrict Minor Access** enabled. Store art and copy stay adult-neutral (no kid-oriented mascots or wording). Terms of Use state 18+.

## R4. Quick match + free-text chat looks like "random chat" — **Medium**

**Finding.** Since 2026-08-26 apps whose core functionality is random or anonymous chat must block minors and meet the Child Safety Standards (Policy announcement 2026-07-15, https://support.google.com/googleplay/android-developer/answer/17134731). Our core is a game, so we should be out of scope, but quick match pairs strangers at random and lets them chat freely under nicknames. R3 already covers most of the risk.

**Recommended change** (lowers moderation load too):
> - Chat: in **quick match**, only preset quick phrases; free-text chat in **private rooms** (players who shared a room code). Free text unlocks after the account has finished at least one online match.

If the team keeps free text in quick match, keep R3 and the child-safety items in R6 anyway.

## R5. Web deletion only works with a code from the app — **High**

**Finding.** Play requires a web resource where users can **request** deletion, discoverable outside the app (Account deletion, https://support.google.com/googleplay/android-developer/answer/13327111). `protocol.md` §3.1/§14 only accepts a 24-hour deletion code generated in the app. The main web-page user is someone who already uninstalled the app; they cannot get a code, and we have no email or password to verify them.

**Exact change (server, `docs/protocol.md` §14):**
- Add `POST /delete-account/request` with form field `handle` (`Nickname#1234`) and `confirm=yes`. Always answer with the same "request received" page whether or not the handle exists (no account enumeration). Rate limit per IP like transfer codes.
- Mark the account `deletionRequestedAt`. If no authenticated request arrives for **30 days**, delete it exactly as `DELETE /me` does. Any authenticated use cancels the request.
- Expose `deletionRequestedAt` in `AccountDto` (or a `welcome` field) so the app can show "A deletion request was made on the web" with **Cancel** / **Delete now**.
- Keep the email route as a fallback: the page lists `[연락처 이메일]`; the owner deletes via the admin API.
- Page text: `DELETE_ACCOUNT_PAGE.md` (already written with methods A, B and C).

## R6. Terms acceptance, Terms page and child-safety text are missing — **High**

**Finding.** UGC policy: Terms of Use must exist and users must accept them before posting, without a way to skip (https://support.google.com/googleplay/android-developer/answer/9876937). Child Safety Standards (if a reviewer puts us in scope) need public standards that explicitly prohibit CSAE (https://support.google.com/googleplay/android-developer/answer/14747720). Neither the README nor `protocol.md` has a Terms page or an acceptance record.

**Exact change**
- README "Chat safety": add "Terms of Use acceptance before the first online match or chat (cannot be skipped); public Terms page with a child-safety section."
- Server: `GET /terms` serving `TERMS_OF_USE.md` (anchor `#child-safety`); store `termsVersion` and `termsAcceptedAt` per account; `PUT /api/v1/me/terms {version}`; refuse `chat`/`phrase`/queue/room joins with a new error `terms_required` until the current version is accepted.
- App: terms screen (summary + link + Agree), shown again when the version changes.

## R7. Reviewers need a live server and an opponent — **High**

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
- Server: serve `/` as a small landing page (app name, store link, Privacy, Terms, Delete account, contact email). Keep HTTP → HTTPS redirects on the same host.

## R9. Report data retention and snapshot clean-up — **Medium**

**Finding.** `protocol.md` stores a snapshot of the reported message plus the last 20 room messages (with sender nicknames) and a free-text note, but sets no retention period for resolved reports, and on account deletion only erases reports **about** the deleted player. Their messages remain, with nickname, inside snapshots of reports about other people. Play requires retained data to be disclosed; Korean law requires a stated retention period.

**Exact change (server)**
- Purge resolved reports and their snapshots **180 days after resolution** (daily job).
- On account deletion, replace that player's id and nickname with a "deleted player" marker inside every other snapshot.
- These are already written into `PRIVACY_POLICY.md` and `DELETE_ACCOUNT_PAGE.md`; if the backend chooses different values, update both documents.

## R10. Moderation operations — **Medium**

**Finding.** "Timely" action on reports is required and apps that keep failing to handle complaints are removed (UGC policy). Device accounts are free to create, so bans are easy to evade.

**Change**
- Server admin API: add `POST /admin/v1/accounts/{id}/chat-restrict {hours, reason}` (chat restriction, not a full ban).
- Automatic temporary chat restriction (24 h, pending review) when **3 distinct accounts** report the same player within 24 hours.
- New accounts: free text only after one finished online match (also in R4).
- Owner: check open reports **at least once a day**; target action within 24 hours, never more than 3 days (the Terms promise this).
- Report CSAE to the relevant authority and keep the dedicated email monitored (child-safety contact).

## R11. Ads implementation details — **Medium**

The README's ad rules are compliant with the Play Ads policy and AdMob guidance. Add these specifics (README "Monetization"):
- Interstitial is shown only **when the player leaves the vs-computer result screen** (Home or Rematch), never the instant the last guess lands, and not within 1 second of the result appearing (AdMob advises a pause in tap-heavy games).
- Banner keeps clear space from every button (no tappable control directly above or below it) and is hidden when the window is too small (pop-up window, small split-screen) to keep that space; banners never overlap content.
- `setMaxAdContentRating` set to match the IARC rating.
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

## R14. App name — **Low**

"Number Baseball" is generic and many apps use it. Use **Pitchcode** as the brand (`STORE_LISTING.md` section 1). Owner checks KIPRIS for "Pitchcode"/"피치코드" before publishing. Never use "Mastermind", "Numer0n/ヌメロン" or "Excel" anywhere; no Excel-like visuals (Impersonation, https://support.google.com/googleplay/android-developer/answer/9888374).

## R15. Auto Backup and deletion — **Low**

Including the token in Auto Backup is fine and is disclosed in the privacy policy. On account deletion the app must clear the token immediately so a later backup cannot restore a deleted account; define `dataExtractionRules` (Android 12+) and `fullBackupContent` so only the token/settings file is backed up.

## R16. Inactive accounts — **Low (optional)**

Not required by Play. Deleting accounts unused for 24 months would reduce stored data. If implemented, add one row to the retention table in `PRIVACY_POLICY.md`.

## R17. Future tip jar or paid features — **Low**

A Play Billing tip jar or in-app purchase turns the personal account into a merchant account (full address shown) and needs Korean business registration details. External donation links are not allowed. Keep both out unless R1 option D is in place.

## R18. Target API and build — **Low (do now)**

Target API 36 (required since 2026-08-31). Check the release bundle for native libraries (16 KB page size, enforcement date 2027-02-01 on the current Android page). Test Android 16 large-screen behaviour.

## Decisions that are fine as they are

- No ID, password or email: minimal data, no password leaks, smaller Data safety form.
- In-app Delete account (immediate) and server-side deletion of account, tokens, codes, blocks and stats.
- Text-only chat, emoji removal, profanity masking, rate limits, report message/player, block (both directions, also in matching), mute all chat, quick phrases.
- Chat history only in memory (50 lines per room), never on disk.
- No ads during matches, secret entry, the waiting practice game, lobby or chat; interstitial cap stricter than AdMob's minimum; no app-open ads; no rewarded ads.
- Removal of the Excel disguise and the boss key (avoids trademark and deceptive-behaviour problems).
- No external donation links.

---

## Requirements for teammates

### Android engineer (`android/`)
1. `targetSdk 36`; test large-screen orientation/resizability; check the release AAB for `.so` files (16 KB).
2. **Terms gate** before the first online match or chat: summary, link to `/terms`, Agree; cannot be skipped; send `PUT /api/v1/me/terms`; show again when the version changes (R6).
3. Settings must contain: Privacy policy (`/privacy`), Terms (`/terms`), **Privacy options** (only when UMP says required), Mute all chat, **Delete account** (with confirmation) and **Get a code to delete on the web**, **Contact** (opens email to the dedicated address), app version.
4. Show "A deletion request was made on the web" with Cancel / Delete now when the server reports `deletionRequestedAt` (R5).
5. Report and block from a long-press on a message and from the player card; offer "Block too" after reporting.
6. Ads: UMP first, then `MobileAds` init only when `canRequestAds()`; `setMaxAdContentRating`; mute tied to the game sound setting; banner only on home / results / profile with clear space from controls and hidden in small windows; interstitial only when leaving the vs-computer result screen, ≥1 s after the result, cap 1 per 3 finished matches and 4 minutes; never during a match, secret entry, waiting practice, lobby, chat, app start or exit; test ad unit IDs in debug; real IDs from build config / CI secrets only; keep the AD_ID permission.
7. Auto Backup: back up only the token/settings; clear the token on deletion; `dataExtractionRules`.
8. No analytics or crash SDK without the release lead; no donate button.
9. If R4 is accepted: quick-match chat limited to quick phrases; free text in private rooms after one finished online match.

### Backend engineer (`backend/`, `docs/protocol.md`)
1. Web pages: `/` landing, `/privacy`, `/terms` (with `#child-safety`), `/delete-account` (methods A–C), `/app-ads.txt` (already specified). HTML from the `docs/play/*.md` texts, both languages; no cookies, no analytics; HTTPS with same-host HTTP→HTTPS redirect.
2. Terms acceptance: `termsVersion`, `termsAcceptedAt`, `PUT /api/v1/me/terms`, error `terms_required` (R6).
3. Deletion request without the app: `POST /delete-account/request`, 30-day pending, cancelled by any authenticated use, `deletionRequestedAt` exposed to the app, identical responses, IP rate limit (R5).
4. Reports: purge resolved reports 180 days after resolution; on account deletion replace the player's id/nickname in other snapshots (R9).
5. Moderation: chat-restrict admin endpoint; auto 24 h chat restriction after 3 distinct reporters in 24 h (R10).
6. Logging: no chat text, tokens, codes or IP addresses in logs or the database (rate-limit IPs in memory only); tell the release lead the hosting provider's log retention and whether database backups exist and how long they are kept.
7. Review room behind `REVIEW_ROOM_CODE` (R7). Always-on hosting with persistent disk.
8. Confirm that secrets/guesses are memory-only and only results are stored (the privacy policy says so).

### Design lead (`design/`)
- Icon 512×512, feature graphic 1024×500, six screenshots per `STORE_LISTING.md` section 4 (no ads, fake nicknames), adult-neutral style, nothing resembling Excel.
- Screens: Terms gate, Settings items above, deletion-request notice, ad placements with spacing rules, chat in quick match (if R4).
- Confirm font licenses allow app embedding.

### QA lead (`docs/qa/`)
- Ads never appear during a match, secret entry, waiting practice, lobby or chat; interstitial cap and timing; banner hidden in small windows.
- UMP: EEA/UK debug geography shows the form; refusal still shows non-personalized ads; Privacy options entry appears only when required.
- Deletion: in-app, web with code, web request without the app (30-day pending, cancel on use), email fallback.
- Terms gate cannot be bypassed (app and server).
- Report/block/mute flows; auto chat restriction.
- Data safety answers match actual network traffic (proxy the app and check which hosts receive what).
- Closed test plan: 12+ testers opted in for 14 consecutive days.
