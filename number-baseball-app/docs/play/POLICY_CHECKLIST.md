# Google Play policy checklist

Owner: Release lead. Checked: 2026-11-20. Applies to: Pitchcode (Number Baseball for Android) as described in `../../README.md`, including the AdMob ads change.

## How this was checked (read first)

- From this container, `support.google.com`, `android-developers.googleblog.com` and other Google pages **could not be opened directly** (DNS blocked by the session's network allowlist). Every policy statement below was confirmed through web-search extracts of the official page named in the "Source" line, searched on 2026-11-20. I did not read the full pages.
- Items marked **[CONFLICT]** are places where official pages disagree with each other; items marked **[UNCONFIRMED]** are things I could not confirm at all. The owner must check those in Play Console / AdMob before relying on them.
- Third-party sources (blogs) are only used where noted and never as the only basis for a requirement.
- Policies change. Re-check every item marked with a date before each release.

## Key dates

| Date | What | Status for us |
| --- | --- | --- |
| 2026-08-26 | Families, Age-restricted content and Child Safety Standards policies extended to **anonymous chat and random chat apps** | Already in force (section 8) |
| 2026-08-31 | New apps and updates must target **API 36 (Android 16)**; extension requests ran to 2026-11-01 | Already in force: target 36 (section 1) |
| 2026-09-30 | Android developer verification enforced on certified devices in Brazil, Indonesia, Singapore, Thailand | Play apps are registered through Play Console (section 4) |
| 2026-10-29 | Policy announcement (at least 30 days to comply): Local Network / Manage Contacts permission limits, Play Age Signals global, trader status | Mostly not applicable; trader status is critical (section 2.4) |
| 2026-12-11 | Play Age Signals API returns responses for all users (per developer docs) | Optional for us (section 10) |
| 2027 (Jan to Mar) | Developer verification expands globally | Automatic for Play apps |
| 2027-02-01 | **Trader status declaration** deadline for existing accounts (new accounts declare at sign-up); 16 KB page size enforcement date on the current Android developer page | Decide before sign-up (section 2.4) |
| 2027-03-01 | Play becomes a marketplace in EEA/UK; **trader contact details shown publicly** | Critical for the owner's privacy |
| 2027-08-31 (expected) | API 37 for new apps and updates | **[UNCONFIRMED]**: only a third-party blog names this date |

---

## 1. Target API level

Source: Target API level requirements, https://support.google.com/googleplay/android-developer/answer/11926878 and https://developer.android.com/google/play/requirements/target-sdk

- Since **2026-08-31**, new apps and app updates (phone/tablet) must target **Android 16, API 36**. Existing apps must target at least API 35 to stay visible to new users on newer Android versions. The extension option ended 2026-11-01.
- **API 37 / 2027**: Google's page did not list it when searched. Following the yearly pattern, expect API 37 to be required around 2027-08-31, but this is **[UNCONFIRMED]**.
- **Must do (Android):** `targetSdk = 36`, `compileSdk = 36` or newer. Test Android 16 behaviour changes, especially on large screens: apps targeting 36 lose some control over orientation and resizability on large screens (https://developer.android.com/about/versions/16/behavior-changes-16, page not opened from here). This fits the README goal of split-screen and pop-up support, but must be tested.
- **16 KB page size** (https://developer.android.com/guide/practices/page-sizes): applies only if the APK contains native code (our code or an SDK's). Current page gives 2027-02-01 as the date after which non-compliant updates cannot be released; older Google posts said 2025-11-01 **[CONFLICT]**. Must do: check the release AAB's `lib/` folder (APK Analyzer); if any `.so` exists (for example from an ads SDK), use AGP 8.5.1+ and up-to-date SDK versions.

## 2. Developer account, identity, and what is shown publicly

### 2.1 Cost and basics

Sources: Get started with Play Console, https://support.google.com/googleplay/android-developer/answer/6112435 ; Required information, https://support.google.com/googleplay/android-developer/answer/13628312

- **US$25 one-time** registration fee (card in the payer's name). Not refunded if identity details are rejected. Dormant accounts can be closed and the fee forfeited (Developer Program Policy effective 2026-10-29).
- Must be 18+.
- Contact email, contact phone and developer email are each verified by one-time password and must keep working for the life of the account.
- Personal accounts: legal name and address come from the linked **Google payments profile**; identity is verified (government ID if the payments profile is not already verified) — Verify your developer identity, https://support.google.com/googleplay/android-developer/answer/10841920
- New personal accounts must verify access to a **real Android phone** (Android 10+, not rooted) with the Play Console mobile app — https://support.google.com/googleplay/android-developer/answer/14316361

### 2.2 Personal account: what players see

Sources: 13628312 (above); View and manage developer account information, https://support.google.com/googleplay/android-developer/answer/13634081 ; Keeping your developer account information up to date, https://support.google.com/googleplay/android-developer/answer/13634888 ; Requirements for specific countries, https://support.google.com/googleplay/android-developer/answer/6223646

| Item | Shown on Google Play? |
| --- | --- |
| Developer name (display name, can differ from legal name) | Yes. Choose a game brand, e.g. "Pitchcode", never the real name. |
| Legal name | **[CONFLICT]** The account page extract says personal accounts display "legal name, country and developer email"; the trader page extract says non-traders show "only email address and country". Plan for the legal name possibly being visible; check the profile preview in Play Console. |
| Country | Yes |
| Developer email | Yes. Use a **dedicated game email**, not a personal one. |
| Phone number | Normally no for personal accounts, **but Korea-based personal accounts must provide and verify a developer phone number that is displayed on Google Play** (6223646, 10841920). Publishing is blocked without it. |
| Full address | Shown if the account **monetizes through Play** (paid apps or in-app purchases = "merchant account"), and for **traders** (2.4). AdMob ads alone do not make a merchant account, but they do make the owner a trader. |
| Per-app support email/phone/website | Store settings → contact details. Optional phone; email required. Use the dedicated email and our domain. |

### 2.3 Organization account

Sources: Choose a developer account type, https://support.google.com/googleplay/android-developer/answer/13634885 ; 13628312 ; 6223646

- Needs a **D-U-N-S number** (free from Dun & Bradstreet, can take up to 30 days); the legal name and address in the payments profile must match D-U-N-S.
- Organization name, address, phone and email are shown publicly (phone and email verified).
- **Korea-based developers** must also give a business registration number, an e-commerce (mail-order) license number and the issuing agency; these are shown to users who use Google Play in Korean.
- So an organization account (for example as a 개인사업자) **replaces home details with business details**, but the business itself must exist (business registration, possibly mail-order registration, a business address and phone). Korean business registries can reveal the representative's name. It is not a way to stay anonymous; it is a way to show a business address instead of a home address.
- No other account type avoids identity display for Play distribution. The new free "limited distribution" (hobbyist) account in the Android Developer Console allows sharing to up to 20 devices only and is not a Play store listing (https://developer.android.com/developer-verification).

### 2.4 Trader status (EEA/UK consumer law) — affects the owner directly

Sources: Trader status declaration, https://support.google.com/googleplay/android-developer/answer/17594752 ; Policy announcement 2026-10-29, https://support.google.com/googleplay/android-developer/answer/18100766

- **Every** developer account must declare trader or non-trader. Existing accounts by **2027-02-01**; new accounts at creation. Missing it can block updates and remove apps globally. False non-trader declarations can lead to removal or account termination.
- Google's trader criteria include receiving **remuneration on a regular or commercial basis, explicitly including ad-sponsored apps**, plus transaction volume, business setup and legal status.
- **Traders**: store listing shows **name, address, phone number and email**. Personal-account traders must supply a public phone number, and the address (from the payments profile) is shown. From **2027-03-01** trader contact details are displayed to users (third-party summaries say worldwide; EEA/UK users also see the trader name in "Offered by").
- **Non-traders**: only email address and country are shown; EEA/UK users are told consumer-protection rights do not apply.
- Google's page says developers who **do not distribute in the EEA or UK can declare non-trader**.
- **Consequence for us:** with AdMob ads and EEA/UK distribution, the owner is very likely a trader → home address and phone become public. See `POLICY_REVIEW.md` item R1 for the options.

## 3. New personal account testing rule

Source: App testing requirements for new personal developer accounts, https://support.google.com/googleplay/android-developer/answer/14151465

- Personal accounts created after 2023-11-13 must run a **closed test with at least 12 testers who have been opted in continuously for the last 14 days** before applying for production access (lowered from 20 testers in December 2024; still 14 days). A tester who leaves and rejoins restarts their 14 days.
- Then apply for production access on the Dashboard; review usually takes 7 days or less. Google can require more testing if engagement was low, so testers should actually play (vs computer, online match, chat, report, block).
- Organization accounts are not subject to this rule (the rule is for personal accounts).

## 4. Android developer verification (2026 rollout)

Sources: https://developer.android.com/developer-verification ; FAQ https://developer.android.com/developer-verification/guides/faq ; Android Developers Blog post "Android developer verification" (June 2026); timeline https://support.google.com/android-developer-console/answer/16650243

- From **2026-09-30**, certified Android devices in Brazil, Indonesia, Singapore and Thailand only install apps registered by verified developers; global expansion in 2027 (pages differ on exact months **[CONFLICT]**; one distributor page names January 2027 and March 2027).
- Play developers who completed Play Console verification are already verified; Google auto-registered about 99% of Play apps. **Must do:** after creating the app, check its package status on Play Console's "Android developer verification" page and register it if it shows "Not registered".
- This adds no new public information beyond section 2.

## 5. Data safety form (exact answers for our design)

Sources: Provide information for Data safety, https://support.google.com/googleplay/android-developer/answer/10787469 ; Google Mobile Ads SDK disclosure, https://developers.google.com/admob/android/privacy/play-data-disclosure ; User Data policy, https://support.google.com/googleplay/android-developer/answer/10144311

Definitions used: "collected" = sent off the device; "shared" = transferred to a third party; data used only in memory to serve a request and not kept can be treated as ephemeral and not "collected". The form covers data sent by SDKs too.

**General questions**

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | Yes |
| Is all user data encrypted in transit? | Yes (requires HTTPS/WSS only on our server; AdMob uses TLS) |
| Do you provide a way for users to request that their data is deleted? | Yes (in-app Delete account + web page `/delete-account`) |
| Account creation method | "Other" / account created automatically by the app, no username/password (pick the closest option the form offers) |
| Delete account URL | `https://[도메인]/delete-account` |

**Data types**

| Data type (Play category) | What it is in our app | Collected | Shared | Ephemeral | Required? | Purposes |
| --- | --- | --- | --- | --- | --- | --- |
| Personal info → Name | Nickname (Play's "Name" includes nicknames) | Yes | No | No | Required | App functionality, Account management |
| Personal info → User IDs | Random account ID, `#1234` tag, account token (we store only a hash) | Yes | No | No | Required | App functionality, Account management, Fraud prevention/security |
| App activity → Other actions | Win/loss/draw records, block list, reports filed | Yes | No | No | Required | App functionality, Fraud prevention/security/compliance |
| Messages → Other in-app messages | Room chat (relayed, held in memory) and **snapshots of reported messages (stored)** | Yes | No | No (because report snapshots are stored) | Optional (chat can be muted / not used) | App functionality, Fraud prevention/security/compliance |
| Location → Approximate location | IP-derived location used by the **Google Mobile Ads SDK** | Yes | Yes | No | Required | Advertising or marketing, Analytics, Fraud prevention/security |
| App activity → App interactions | Ad interactions (launches, taps, video views) sent by the Ads SDK | Yes | Yes | No | Required | Advertising or marketing, Analytics, Fraud prevention |
| App info and performance → Diagnostics | SDK performance data sent by the Ads SDK | Yes | Yes | No | Required | Analytics, Fraud prevention |
| Device or other IDs | Android advertising ID, app set ID (Ads SDK) | Yes | Yes | No | Required | Advertising or marketing, Analytics, Fraud prevention |

Notes:
- The four Ads SDK rows follow Google's AdMob disclosure page, which says the SDK collects and shares IP address, user product interactions, diagnostic information and device/account identifiers for advertising, analytics and fraud prevention. The search extract cut off before the full table, so **the owner must open that page and match each row and purpose exactly** for the SDK version we ship.
- Our server's own use of IP addresses (rate limiting in memory) is ephemeral and is not a separate data type, as long as the server does **not** write IPs to its own logs or database. Hosting-provider access logs must be described in the privacy policy.
- No crash-reporting or analytics SDK is planned. If one is added (Firebase Crashlytics, Analytics), the form must be updated.
- Keep the form identical to `PRIVACY_POLICY.md`; Play requires them to match.

## 6. Account deletion

Source: Understanding Google Play's app account deletion requirements, https://support.google.com/googleplay/android-developer/answer/13327111

- Apps that let users create an account must offer **(a) an in-app path** to delete the account and associated data and **(b) a web link** where users can request deletion without reinstalling. The web page must work, be easy to find, and **name the app or developer exactly as on the store listing**. The URL goes into the Data safety form.
- Deactivating or freezing is not deletion. Data kept for security, fraud prevention or legal reasons must be disclosed (privacy policy).
- **Our design:** Settings → Delete account (immediate). Web: `/delete-account` page, see `DELETE_ACCOUNT_PAGE.md`. Because we have no email or password, the web flow uses either a transfer code (immediate) or a 30-day pending request that is cancelled if the account is used again (protects players from malicious deletion requests). Exact server requirements are in `POLICY_REVIEW.md` section "Requirements for teammates".

## 7. User Generated Content (chat)

Source: User Generated Content policy, https://support.google.com/googleplay/android-developer/answer/9876937 ; moderation guidance, https://support.google.com/googleplay/android-developer/answer/12923286

| Requirement | Our implementation |
| --- | --- |
| Clear, accessible Terms of Use that users must accept before posting; acceptance cannot be skipped | `TERMS_OF_USE.md` served at `/terms`; app shows a one-time "Agree to Terms" step before the first online match/chat, cannot be skipped; server records the accepted version |
| Terms define objectionable content and behaviour | Chat rules section in Terms |
| In-app reporting of users **and** content | Report message, report player (long-press / profile) |
| In-app blocking of users (required for 1:1 interaction) | Block player (hides their chat, avoids matching) |
| Timely action on reports; apps that keep failing to handle complaints are not allowed | Owner reviews reports at least daily; target first response within 24 h, action within 72 h; auto-restriction after several distinct reports (server) |
| Ongoing moderation proportionate to the UGC | Profanity masking, rate limit, text only, quick phrases, admin tools for chat restriction/suspension/ban |
| No monetizing objectionable UGC | No ads in chat or during matches |

## 8. Anonymous / random chat rules and Child Safety Standards (new in 2026)

Sources: Policy announcement 2026-07-15, https://support.google.com/googleplay/android-developer/answer/17134731 ; Age-Restricted Content and Functionality, https://support.google.com/googleplay/android-developer/answer/16302250 ; Child Safety Standards, https://support.google.com/googleplay/android-developer/answer/14747720 ; Policy deadlines, https://support.google.com/googleplay/android-developer/table/12921780

- Since 2026-08-26, apps whose **core functionality** is anonymous chat (identities deliberately hidden) or random chat (randomly connecting strangers to talk) must block minors (age-restricted policy), may not target children (Families), and must meet the **Child Safety Standards** (published CSAE prohibition, in-app feedback/report mechanism, action on CSAM, compliance with child-safety law including reporting to NCMEC or the regional authority, and a designated contact for Google).
- **Our assessment:** our core functionality is a game, chat is secondary and limited to match rooms, so we should not be in scope. But quick match pairs strangers at random and they can chat freely under nicknames, so a reviewer could see it differently. **Must do anyway (cheap insurance):**
  - Publish a child-safety section (explicit CSAE prohibition) in the Terms at `/terms#child-safety`.
  - Keep in-app reporting and a contact entry in Settings.
  - Name a designated child-safety contact (the dedicated game email) if Play Console asks.
  - Target audience 18+ with Restrict Minor Access (section 10).
- Optional product change that removes most of the risk: free-text chat only in private rooms; quick match gets quick phrases only. See `POLICY_REVIEW.md` R4.

## 9. Content rating (IARC)

Sources: Content ratings, https://support.google.com/googleplay/android-developer/answer/9898843 ; Online Interaction question, https://support.google.com/googleplay/android-developer/answer/7021383 ; Content rating requirements for apps and ads, https://support.google.com/googleplay/android-developer/answer/9859655 ; https://globalratings.com/how-iarc-works/

- Complete the questionnaire before publishing; redo it when features change. Answer honestly; Google warns that inaccurate answers can lead to removal.
- Guidance for our answers: category **Game**; no violence, fear, sexuality, gambling or simulated gambling, drugs, crude humour created by us.
- **Users can interact / exchange content: Yes** (text chat between users). Multiplayer alone would be "No", but chat makes it "Yes". This produces the "Users interact" interactive element; it is shown separately from the age rating.
- Shares user location: No. Digital purchases: No. Unrestricted internet/web browser: No.
- Do not tune answers to get a lower rating. Whatever rating IARC gives, ads must not exceed it: set AdMob's max ad content rating to match (section 11.6).
- Korea: the IARC certificate covers the Korean (GRAC) rating for Play distribution.

## 10. Target audience and Families

Sources: Manage target audience and app content settings, https://support.google.com/googleplay/android-developer/answer/9867159 ; Families policies, https://support.google.com/googleplay/android-developer/answer/9893335 ; Play Age Signals, https://developer.android.com/google/play/age-signals/overview

- Before the Target audience section you must have answered the Ads question, given App access instructions and added a privacy policy.
- **Recommendation: target age group "18 and over" only, and enable Restrict Minor Access.** Restrict Minor Access requires 18+ as the only target group; it stops users Google identifies as minors from finding or downloading the app (existing installs keep working).
- Why: (1) chat with strangers found by random matching is the pattern Google restricted in 2026 (section 8); (2) ads plus minors brings Families Self-Certified Ads SDK and consent rules we do not want; (3) the real audience ("a quick quiet match in a break", originally with an office-disguise mode) is adults; (4) any under-13 group would pull us into the full Families policy, which also no longer allows anonymous chat apps to target children.
- Do not choose more than one age group unless the app is designed for each. Keep store art and wording adult-neutral (no cartoon mascots aimed at kids, no "for kids"); listings that appeal to children while the content is adult-oriented violate the Families policy.
- Play Age Signals API (beta, global) is optional for us while Restrict Minor Access is on. If the owner later drops Restrict Minor Access, use Age Signals to turn off free-text chat for users reported as minors; Age Signals data may only be used for age-appropriate experiences in the receiving app.

## 11. Ads (Google Play policy + AdMob)

### 11.1 Play Ads policy

Sources: Ads policy, https://support.google.com/googleplay/android-developer/answer/9857753 ; Better Ads Experiences, https://support.google.com/googleplay/android-developer/answer/12271244

| Rule | Our design | OK? |
| --- | --- | --- |
| No full-screen ads that appear unexpectedly when the user chose to do something else | Interstitial only after a finished vs-computer match, when leaving the result screen | Yes, if shown at that break only |
| No ads at the beginning of a game level / content segment | Never at match start, never before secret entry | Yes |
| No full-screen video before the splash screen; no ad on app start or exit | README forbids | Yes |
| Full-screen ads must be closeable after 15 s, unless they do not block the user (e.g. after the score screen) | After the result screen; prefer formats closeable within 15 s anyway | Yes |
| Ads must not interfere with app use, mimic UI, or be placed to cause accidental clicks | Banner on calm screens only, away from buttons | Implement spacing |
| Ads only inside the app that serves them; no lock-screen ads | Banner/interstitial inside app | Yes |
| Rewarded ads that give an advantage | None | Yes |

### 11.2 AdMob placement guidance

Sources: Interstitial guidance, https://support.google.com/admob/answer/6066980 ; Disallowed interstitials, https://support.google.com/admob/answer/6201362 ; Discouraged banner implementations, https://support.google.com/admob/answer/6275345 ; Implementation guidance, https://support.google.com/admob/answer/2936217

- Interstitials at natural breaks between stages; never during a focused task; no more than one interstitial per two user actions (our cap: at most one per 3 finished matches and 4 minutes apart, stricter).
- In tap-heavy games, add a short delay before the interstitial so players stop tapping (we add at least 1 s after the result appears before any ad can show).
- Banners must not sit right next to buttons or overlap/float over content. Leave clear space between the banner and the nearest control; hide the banner when the window is too small (pop-up window / split-screen) to keep that space.

### 11.3 "Contains ads" declaration

Source: Prepare your app for review, https://support.google.com/googleplay/android-developer/answer/9859455

- App content → Ads → **Yes, my app contains ads**. The "Contains ads" label then appears on the listing. Google can verify this at any time.

### 11.4 Advertising ID declaration and AD_ID permission

Sources: Advertising ID, https://support.google.com/googleplay/android-developer/answer/6048248 ; AdMob quick start, https://developers.google.com/admob/android/quick-start ; Android 13 behaviour changes, https://developer.android.com/about/versions/13/behavior-changes-13

- Apps targeting Android 13+ that use the advertising ID must declare `com.google.android.gms.permission.AD_ID`; without it the ID is zeros. Google Mobile Ads SDK 20.4.0+ declares it automatically through manifest merge, so do not remove it with `tools:node="remove"`.
- App content → Advertising ID → **Yes, uses advertising ID**; purposes: Advertising or marketing, Analytics, Fraud prevention/security. Must match Data safety.
- Apps solely for children must not request AD_ID (not our case with an 18+ target).

### 11.5 Consent (EEA, UK, Switzerland, US states)

Sources: Google consent management requirements, https://support.google.com/admob/answer/13554116 ; EU user consent policy help, https://www.google.com/about/company/user-consent-policy-help/ ; Disclose to EEA users (UMP), https://developers.google.com/admob/android/privacy/gdpr ; US state regulations messages, https://support.google.com/admob/answer/10862202 ; Restricted data processing, https://support.google.com/admob/answer/14125907

- Google's EU User Consent Policy requires consent for personalized ads in the EEA/UK/Switzerland, collected through a **Google-certified CMP integrated with IAB TCF** (required since 2024-01-16 for EEA/UK and 2024-07-31 for Switzerland). Google's own CMP via the **UMP SDK** qualifies. Without a certified CMP, traffic is only eligible for non-personalized or limited ads.
- US states: create a **US state regulations message** in AdMob Privacy & messaging (UMP shows it); opt-outs are honoured through restricted data processing (RDP) / GPP. New state laws (Indiana, Kentucky, Rhode Island) took effect 2026-01-01 and are covered by Google's message.
- **Must do (Android):** call `requestConsentInfoUpdate` on every launch, `loadAndShowConsentFormIfRequired`, initialize the Mobile Ads SDK and request ads only when `canRequestAds()` is true; show a **"Privacy options"** entry in Settings when `privacyOptionsRequirementStatus == REQUIRED`. Ads still work as non-personalized if consent is refused (matches README).
- **Must do (owner, AdMob UI):** create and publish the European regulations message and US state regulations message in Privacy & messaging, in all 10 languages, with the privacy policy URL.

### 11.6 Ad content rating and sound

Sources: AdMob targeting, https://developers.google.com/admob/android/targeting ; Global settings, https://developers.google.com/admob/android/global-settings ; Content rating requirements for apps and ads, https://support.google.com/googleplay/android-developer/answer/9859655

- Set `RequestConfiguration.setMaxAdContentRating(...)` to the level matching our IARC rating (G for an all-ages rating, PG or T if IARC rates higher). Ads must fit the app's rating.
- `MobileAds.setAppMuted(true)` fits the quiet design, but Google warns muting **reduces video ad eligibility and may lower revenue**, and should mirror a real in-app mute control. Recommendation: keep ads muted when the app's sound setting is off (the default); unmute ads only if the player turns game sound on.

### 11.7 app-ads.txt

Sources: Set up app-ads.txt, https://support.google.com/admob/answer/9363762 ; app-ads.txt FAQ, https://support.google.com/admob/answer/9675354 ; Firebase hosting option, https://support.google.com/admob/answer/9820295 ; crawlability, https://support.google.com/admob/answer/9679128

- The file must be at the **root of the developer website domain listed in the Play store listing** (Store settings → contact details → website), returned with HTTP 200 as plain text, not blocked by robots.txt. Content: `google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0` (with our publisher ID).
- AdMob's crawler builds the URL from the listed website and checks **up to one subdomain level** plus the root domain; `www.` and `m.` are skipped. Redirects within the same root domain are allowed. The app must be **published** before the crawler can verify; allow 24 hours or more.
- Free subdomains: Google documents Firebase's `PROJECT_ID.web.app` as working. `onrender.com` is on the Public Suffix List (I checked the copy bundled with the JDK in this container), so `name.onrender.com` is its own registrable domain and the crawler should check `https://name.onrender.com/app-ads.txt`. Google does not document Render specifically: **[UNCONFIRMED]**.
- **Recommendation: buy a custom domain** (about 10,000–20,000 KRW per year). It keeps the developer website, privacy URL, deletion URL and app-ads.txt stable if we ever move hosting, and keeps the owner's name out of URLs. Register it with WHOIS privacy (most registrars offer it; check that the .com/.app registrar hides the registrant name).

### 11.8 AdMob account for an individual in South Korea

Sources: Sign up for AdMob, https://support.google.com/admob/answer/7356219 ; Age requirements, https://support.google.com/admob/answer/2785008 ; Enter payment details, https://support.google.com/admob/answer/12835021 ; Payment thresholds, https://support.google.com/admob/answer/2772208 ; Address verification (PIN), https://support.google.com/admob/answer/2772302 ; Contracting entity / tax, https://support.google.com/admob/answer/4381375 ; Submit non-US tax info, https://support.google.com/admob/answer/14135099 ; sellers.json, https://support.google.com/admob/answer/9884382

- 18+. Account type (Individual / Organization) and country are chosen at sign-up and **cannot be changed later**.
- Individual payee name = full legal name exactly as on the ID and bank account. A postal address that receives mail is required (the PIN letter goes there).
- Identity verification is requested when earnings reach the verification threshold (USD 10 for USD accounts); 45 days to submit documents. The **PIN** letter comes by international post (can take 2–3 weeks); 4 months to enter it.
- **Payment threshold: USD 100** (varies by currency). Payment by bank transfer to the owner's account.
- **US tax info:** for a non-US individual, AdMob says AdMob revenue is treated as personal-services income, so **AdMob does not accept a W-8BEN from individuals to claim treaty benefits; Form 8233 is the treaty route**. A publisher with no US activities can use the "Foreign Publisher with no US Activities" statement instead (no form). Google may withhold up to 30% if tax info is missing or wrong. Treaty eligibility (Korea–US) needs a tax professional; Google will not decide it.
- **What becomes public:** nothing on the Play listing. In Google's public `sellers.json`, only the seller ID is listed by default; the name (from the payments profile) and domain appear only if the publisher switches to "transparent". **Keep it confidential.** Earlier "transparent" periods can remain visible, so never switch it on.
- Korean income tax on ad revenue (comprehensive income tax filing) applies separately; ask a 세무사. Not researched further here.

### 11.9 Revenue expectations (rough)

- Google publishes no general eCPM numbers; AdMob shows peer benchmarks inside the account (https://support.google.com/admob/answer/14660616).
- Third-party benchmarks (not Google, figures differ between sources): banner eCPM roughly **USD 0.1–1.5**, interstitial roughly **USD 4–15** in top markets (US, Japan, Korea, UK), e.g. Playwire's AdMob benchmark article https://www.playwire.com/blog/admob-ecpm-benchmarks-what-publishers-should-expect and Tenjin's ad-monetization reports https://tenjin.com/tr/blog/ad-mon-gaming-2026/ . Muted ads and our strict caps push toward the low end.
- Illustration only: 100 daily players × (3 banner views + 0.3 interstitials) ≈ 300 × $0.5/1000 + 30 × $5/1000 ≈ **USD 0.3 per day ≈ USD 9 per month**; the USD 100 payout threshold would take about a year. At 1,000 daily players, roughly USD 90 per month, which is in the range of server cost (the prototype deploy guide lists about USD 7.25/month for Render Starter + disk). Treat ads as cost recovery at best, not income.

## 12. Payments policy (donations, tips)

Source: Understanding Google Play's Payments policy, https://support.google.com/googleplay/android-developer/answer/10281818

- Digital goods and services in Play apps must use Play Billing (with program-specific exceptions such as the US external content links program, which has fees and enrollment).
- Play's peer-to-peer exception covers tips where 100% goes **to a creator** and nothing digital is unlocked; a tip to the developer is not that case. **[UNCONFIRMED]** whether a developer "tip jar" without perks can skip Play Billing; assume it cannot.
- Keep the README rule: **no external donation links** (the web prototype's donate button must not come to Android). A Play Billing tip jar would also make the account a merchant account (full address public, section 2.2) and require Korean business registration details for paid items (6223646).

## 13. Impersonation, intellectual property, metadata

Sources: Impersonation, https://support.google.com/googleplay/android-developer/answer/9888374 ; Impersonation FAQ, https://support.google.com/googleplay/android-developer/answer/16341334 ; Metadata, https://support.google.com/googleplay/android-developer/answer/9898842 ; Misrepresentation, https://support.google.com/googleplay/android-developer/answer/9888689

- Title max 30 characters; no emoji, no repeated special characters, ALL CAPS only if part of the brand; no ranking/price/promo text; no keyword stuffing.
- No title or icon so similar to an existing product that users could be misled; developer name must not imply a relationship with another company.
- **Excel**: the spreadsheet disguise was removed (README). Also make sure no screen, icon or screenshot imitates Microsoft Excel (green grid with column letters, ribbon, Excel-like icon, the word "Excel"). Microsoft's marks are not ours to use.
- **Names**: "Mastermind" (and German "Superhirn") and "Numer0n/ヌメロン" are commercial game brands; do not use them anywhere in the listing. Generic folk-game names (Bulls and Cows, Быки и коровы, Picas y Fijas, 1A2B) may appear once as "also known as". See `STORE_LISTING.md`.
- Fonts in `design/fonts`: check their licenses allow embedding in an app (design lead).

## 14. Store assets

Source: Add preview assets, https://support.google.com/googleplay/android-developer/answer/9866151

| Asset | Requirement |
| --- | --- |
| App icon | Required. 512 × 512 px, 32-bit PNG with alpha, max 1024 KB. Follow Play icon specs; no badges/text implying ranking, price or Play programs. |
| Feature graphic | Required. 1024 × 500 px, JPEG or 24-bit PNG without alpha. No store badges, no device frames advised, no third-party trademarks. |
| Phone screenshots | 2–8 per device type; PNG or JPEG, up to 8 MB each, each side 320–3840 px. **Games: at least three** 16:9 landscape (min 1920 × 1080) or 9:16 portrait (min 1080 × 1920) recommended. |
| Tablet / large screen screenshots | Recommended because the app supports resizing; same format rules. |
| Video | Optional (YouTube URL). |

Screenshots must show the real app; never show an ad covering the board (we never do that), never show real testers' nicknames.

## 15. App access instructions for reviewers

Sources: Prepare your app for review, https://support.google.com/googleplay/android-developer/answer/9859455 ; Requirements for providing sign-in details, https://support.google.com/googleplay/android-developer/answer/15748846

- Reviewers need access that works from any location, at any time, for the whole review. If parts of the app depend on our server, **the server must be running and reachable 24/7** during review (no free tier that sleeps).
- We have no login, so credentials are not needed, but online play needs an opponent. Choose "All or some functionality is restricted" and provide instructions (draft in `RELEASE_GUIDE.md` section 9), plus a **review room** on the server: a reserved room code where a test bot joins, plays, and sends one chat message so the reviewer can test Report and Block.
- Never give reviewers a real player's account or data.

## 16. Privacy policy (Play + Korean law)

Sources: User Data policy, https://support.google.com/googleplay/android-developer/answer/10144311 ; Korea PIPA guidance summaries (Korean law firm and PIPC-guideline summaries found by search, e.g. https://datalaw.kr/posts/privacy-policy-checklist-2026/ ; PIPC original guideline not opened)

- Play: link in Play Console **and** inside the app; active, public, non-geofenced URL; **not a PDF**; not editable; clearly titled "Privacy Policy"; must name the app or the developer as on the listing; must include developer info and a privacy contact (or inquiry mechanism), data types collected and shared and with whom, secure handling, retention and deletion; must match the Data safety form.
- Korea (PIPA privacy-policy guideline, revised 2024–2026): items, purposes, retention, third-party provision, processing outsourcing (위탁), **overseas transfer listed separately** (items, country, when/how, recipient and contact, purpose and retention, how to refuse and the effect, legal basis), automatic collection tools such as **advertising IDs** with how to refuse, and the **privacy officer's name or department and contact**.
- Done in `PRIVACY_POLICY.md` (ko + en), served at `/privacy`.

## 17. Other App content declarations

Source: Prepare your app for review, https://support.google.com/googleplay/android-developer/answer/9859455 ; Health declaration, https://support.google.com/googleplay/android-developer/answer/14738291 ; Financial features declaration, https://support.google.com/googleplay/android-developer/answer/13849271

| Declaration | Answer |
| --- | --- |
| Privacy policy | `https://[도메인]/privacy` |
| App access | Restricted functionality + instructions (section 15) |
| Ads | Yes |
| Content rating | IARC questionnaire (section 9) |
| Target audience and content | 18 and over only; Restrict Minor Access on |
| News app | No |
| Health apps | Complete the form: no health features |
| Financial features | None |
| Government app | No |
| Data safety | Section 5 |
| Advertising ID | Yes (section 11.4) |
| Local Network permission (API 37+ targets) | Not applicable (we target 36 and do not use it) |
| Trader status (account level) | Owner decision, see `POLICY_REVIEW.md` R1 |

If the Console shows a declaration not listed here, answer it from the facts in the README; ask the release lead if unsure.
