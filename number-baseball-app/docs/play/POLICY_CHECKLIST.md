# Google Play policy checklist

Owner: Release lead. Checked: 2026-11-20, updated 2026-11-21 for the owner's decisions (trader with worldwide distribution, audience 13+, "숫자야구"-style name, quick match limited to quick phrases). Updated 2026-11-25 for the owner's decisions: one-time in-app product `remove_ads` (US$4.99, Google Play Billing, no server check) and the interstitial now also after online matches (sections 2.2, 5, 9, 10, 11, 12, 17, 19). Applies to: Number Baseball for Android as described in `../../README.md`, including AdMob ads and the Remove ads purchase.

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
| 2026-05 (late) | Texas App Store Accountability Act (SB 2420) in effect after an appeals court stayed the injunction; Play Age Signals returns signals for Texas accounts created after 2026-05-28 | Applies to us now that minors are in the audience (section 19) |
| 2026-12-11 | Play Age Signals API returns responses for all users (per developer docs) | Integrate (sections 10, 19) |
| 2027 (Jan to Mar) | Developer verification expands globally | Automatic for Play apps |
| 2027-02-01 | **Trader status declaration** deadline for existing accounts (new accounts declare at sign-up); 16 KB page size enforcement date on the current Android developer page | Decide before sign-up (section 2.4) |
| 2027-03-01 | Play becomes a marketplace in EEA/UK; **trader contact details shown publicly** | Owner accepted (trader): name, address, phone, email public |
| 2027-05-06 / 2027-07-01 | Utah / Louisiana app-store age laws take effect (both postponed by their legislatures) | Same handling as Texas (section 19) |
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
| Developer name (display name, can differ from legal name) | Yes. Choose a game/studio name rather than the real name (the legal name is shown separately anyway as a trader). |
| Legal name | **[CONFLICT]** The account page extract says personal accounts display "legal name, country and developer email"; the trader page extract says non-traders show "only email address and country". Plan for the legal name possibly being visible; check the profile preview in Play Console. |
| Country | Yes |
| Developer email | Yes. Use a **dedicated game email**, not a personal one. |
| Phone number | Normally no for personal accounts, **but Korea-based personal accounts must provide and verify a developer phone number that is displayed on Google Play** (6223646, 10841920). Publishing is blocked without it. |
| Full address | Shown if the account **monetizes through Play** (paid apps or in-app purchases = "merchant account"), and for **traders** (2.4). AdMob ads alone do not make a merchant account, but they do make the owner a trader. **Since 2026-11-25 the app sells `remove_ads`, so the account is also a merchant account** (needs a Google payments profile set up for selling, and Korea-based sellers of in-app items may have to give business registration / mail-order (통신판매업) details: see section 12). The address is already public as a trader, so nothing new becomes public. |
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
- **Owner decision (2026-11-21): declare as trader** (personal account, ads, worldwide including EEA/UK = `POLICY_REVIEW.md` R1 option B). What becomes public, at the latest from 2027-03-01:
  - **Legal name** (EEA/UK users also see it in "Offered by"), **address** from the Google payments profile, a **verified public phone number**, **email**; no D-U-N-S (personal account).
  - Third-party summaries say the trader details are shown to users **worldwide**, not only in the EEA/UK.
  - The address cannot simply be a made-up or borrowed one: it comes from the payments profile, which must match the verified identity. Whether a personal-account trader can show a different public address is **[UNCONFIRMED]**.
  - A terms-of-service link may be shown for traders (third-party source); use `https://quietdual.com/terms` if Play Console asks.
  - Still use a **dedicated game email**: the public email receives spam and harassment, and it keeps personal mail separate. The phone shown can be a dedicated number as long as it is the owner's and passes verification.

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
| Do you provide a way for users to request that their data is deleted? | Yes (in-app Delete account; without the app by email, explained at `/delete-account`) |
| Account creation method | "Other" / account created automatically by the app, no username/password (pick the closest option the form offers) |
| Delete account URL | `https://quietdual.com/delete-account` |

**Data types**

| Data type (Play category) | What it is in our app | Collected | Shared | Ephemeral | Required? | Purposes |
| --- | --- | --- | --- | --- | --- | --- |
| Personal info → Name | Nickname (Play's "Name" includes nicknames) | Yes | No | No | Required | App functionality, Account management |
| Personal info → User IDs | Random account ID, `#1234` tag, account token (we store only a hash) | Yes | No | No | Required | App functionality, Account management, Fraud prevention/security |
| App activity → Other actions | Win/loss/draw records, block list, reports filed | Yes | No | No | Required | App functionality, Fraud prevention/security/compliance |
| App activity → Other user-generated content | Optional free-text note in a report (max 500 characters) | Yes | No | No | Optional | Fraud prevention/security/compliance |
| Messages → Other in-app messages | Room chat (relayed; last 50 lines per room kept in memory only while the room exists) and **report snapshots: the reported message plus the last 20 room messages (stored)** | Yes | No | No (because report snapshots are stored) | Optional (chat can be muted / not used) | App functionality, Fraud prevention/security/compliance |
| Location → Approximate location | IP-derived location used by the **Google Mobile Ads SDK** | Yes | Yes | No | Required | Advertising or marketing, Analytics, Fraud prevention/security |
| App activity → App interactions | Ad interactions (launches, taps, video views) sent by the Ads SDK | Yes | Yes | No | Required | Advertising or marketing, Analytics, Fraud prevention |
| App info and performance → Diagnostics | SDK performance data sent by the Ads SDK | Yes | Yes | No | Required | Analytics, Fraud prevention |
| Device or other IDs | Android advertising ID, app set ID (Ads SDK) | Yes | Yes | No | Required | Advertising or marketing, Analytics, Fraud prevention |
| Financial info → Purchase history | `remove_ads` purchase state, purchase token and acknowledgement, read on the phone through Google Play Billing (decision 2026-11-25: declare it) | Yes (see note) | No | No | Optional (only if the player buys) | App functionality |

Notes:
- The four Ads SDK rows follow Google's AdMob disclosure page, which says the SDK collects and shares IP address, user product interactions, diagnostic information and device/account identifiers for advertising, analytics and fraud prevention. The search extract cut off before the full table, so **the owner must open that page and match each row and purpose exactly** for the SDK version we ship.
- Our server's own use of IP addresses (rate limiting in memory) is ephemeral and is not a separate data type, as long as the server does **not** write IPs to its own logs or database. Hosting-provider access logs must be described in the privacy policy.
- The age band from the neutral age screen (section 10) stays on the device and is not sent to our server, so it is not a collected data type. The ads SDK receives only an age-treatment flag. If the age band is ever sent to the server, add "Personal info → Other info" (or the closest age type the form offers).
- No crash-reporting or analytics SDK is planned. If one is added (Firebase Crashlytics, Analytics), the form must be updated.
- **Purchase history (added 2026-11-25).** Payment details (card, bank, name, billing address) are handled by Google Play and never reach the app or our server, so **do not declare Payment info**. The app does see the purchase state and purchase token from the Play Billing library and, with no server check, **keeps them only on the device and sends them nowhere**. Under Play's definition ("collected" = sent off the device) that alone may not be "collection", and Google's Data safety guidance says data handled only by Google Play Billing need not be declared by the developer **[UNCONFIRMED: I could not open the Data safety FAQ; confirm in Play Console help]**. The README decision is to declare "Financial info → Purchase history" anyway; that is the conservative answer, it costs nothing and it matches the privacy policy. Answer: collected Yes, **shared No** (Google Play is the payment processor acting for the user, not a third party we send data to; if the Console wizard's wording makes you doubt that, the safe fallback is "shared: No" plus the explanation in the privacy policy section 7-1), not ephemeral, optional, purpose App functionality. If a **server check** is ever added (purchase token sent to our server), the row stays but the privacy policy, retention table and `docs/protocol.md` must change, and the token must be handled as a secret.
- When the Remove ads purchase is active the app should not initialise the Mobile Ads SDK, so the ad rows apply only to users who have not bought. The Data safety form still declares them, because the form covers what the app can do.
- Keep the form identical to `PRIVACY_POLICY.md`; Play requires them to match.

## 6. Account deletion

Source: Understanding Google Play's app account deletion requirements, https://support.google.com/googleplay/android-developer/answer/13327111

- Apps that let users create an account must offer **(a) an in-app path** to delete the account and associated data and **(b) a web link** where users can request deletion without reinstalling. The web page must work, be easy to find, and **name the app or developer exactly as on the store listing**. The URL goes into the Data safety form.
- Deactivating or freezing is not deletion. Data kept for security, fraud prevention or legal reasons must be disclosed (privacy policy).
- **Our design (owner decision 2026-11-21, app only):** Settings → Delete account calls `DELETE /api/v1/me` (immediate). Without the app: email the game's contact address with the `Nickname#1234`; the operator deletes with the admin tool within 7 days. `/delete-account` is a short static text page explaining both (`DELETE_ACCOUNT_PAGE.md`); no form, code or web login.
- **Does an email route satisfy Play?** Yes. Google's account-deletion guidance lists, as acceptable web resources for requesting deletion, "an additional link that initiates account deletion, **a customer service email** or a form" (search extract of https://support.google.com/googleplay/android-developer/answer/13327111). Conditions we meet: the URL is entered in the Data safety form, the page works, it names the app/developer as on the listing, and it is easy to find. Requests must actually be handled (operator steps in `RELEASE_GUIDE.md` 11).

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
- **Re-assessment for a 13+ audience (2026-11-21):** our core functionality is a game. The README now limits **quick match to preset quick phrases**; free text exists only in **private rooms** joined with a room code someone shared. So there is no random free-text chat with strangers and no anonymous chat as a core feature: we are **out of scope** of the anonymous/random chat rules, which would otherwise require blocking minors (impossible with a 13+ audience).
- **Hard condition:** the server flag `QUICK_MATCH_FREE_CHAT` must stay **off** while the audience includes minors. Turning it on would make quick match a random free-text chat with strangers and pull the app into the age-restricted policy (block minors).
- Room codes can still be posted publicly (social media), so strangers can meet in private rooms. Keep, as mandatory for a teen audience:
  - A published child-safety section (explicit CSAE prohibition) in the Terms at `/terms#child-safety`.
  - In-app report (message and player), block, mute all chat; a contact entry in Settings.
  - A designated child-safety contact (the dedicated game email) if Play Console asks; act on CSAE reports immediately and report to the relevant authority.
  - Moderation as in section 7 (daily review, automatic chat restriction after 3 reporters).
- The general **Child Endangerment** policy (https://support.google.com/googleplay/android-developer/answer/9878809) applies to every app regardless of audience; the steps above cover it.

## 9. Content rating (IARC)

Sources: Content ratings, https://support.google.com/googleplay/android-developer/answer/9898843 ; Online Interaction question, https://support.google.com/googleplay/android-developer/answer/7021383 ; Content rating requirements for apps and ads, https://support.google.com/googleplay/android-developer/answer/9859655 ; https://globalratings.com/how-iarc-works/

- Complete the questionnaire before publishing; redo it when features change. Answer honestly; Google warns that inaccurate answers can lead to removal.
- Guidance for our answers: category **Game**; no violence, fear, sexuality, gambling or simulated gambling, drugs, crude humour created by us.
- **Users can interact / exchange content: Yes** (text chat between users). Multiplayer alone would be "No", but chat makes it "Yes". This produces the "Users interact" interactive element; it is shown separately from the age rating.
- Shares user location: No. Digital purchases: **Yes** (the optional `remove_ads` in-app purchase; the IARC questionnaire asks about digital purchases and adds the "Offers in-app purchases" interactive element, shown separately from the age rating, and does not change the rating by itself). Unrestricted internet/web browser: No.
- Do not tune answers to get a lower rating. Whatever rating IARC gives, ads must not exceed it: set AdMob's max ad content rating to match (section 11.6).
- The answers do not change with the 13+ target audience: target audience and content rating are separate declarations. Expect the lowest or a low age bracket plus the "Users interact" notice, but the result is IARC's.
- Korea: the IARC certificate covers the Korean (GRAC) rating for Play distribution.

## 10. Target audience and Families

Sources: Manage target audience and app content settings, https://support.google.com/googleplay/android-developer/answer/9867159 ; Families policies, https://support.google.com/googleplay/android-developer/answer/9893335 ; Play Age Signals, https://developer.android.com/google/play/age-signals/overview ; Families Self-Certified Ads SDK program, https://support.google.com/googleplay/android-developer/answer/9900633

**Owner decision (2026-11-21): audience 13 and over, Restrict Minor Access off.** Recommended minimum age in the app: **14** worldwide (section 18, `POLICY_REVIEW.md` R19).

- Play Console target age groups: select **13–15, 16–17 and 18+**. Do not select any group under 13. Play has no 14–15 band; selecting 13–15 is correct for an app designed for 14–15-year-olds and up.
- **Families policy does not apply**: it applies to apps with at least one target age group that includes children (under 13 in Play's model). Google adds two cautions we must respect:
  - The 16–17 band "may be considered to include children in some locales", and developers targeting anyone under 21 must consider whether those users are children under local law. In Korea a child for privacy consent purposes is **under 14** (section 18), which is why the app's minimum age should be 14.
  - If Google finds that the app or listing **appeals to children** (cartoon mascots, "for kids"), it can treat the app as child-directed. Keep art and copy teen/adult-neutral.
- Before the Target audience section you must have answered the Ads question, given App access instructions and added a privacy policy.
- **Neutral age screen (required by our design):** on **first launch, before any ad loads and before the account is created**, ask the age in a neutral way (for example a year-of-birth picker or age bands, with no default and no hint of the "right" answer). Under 14 → the app explains that it is for ages 14 and over and stops (no account, no ads, no online play); it does not allow an immediate retry on that device. Do not offer an offline-with-ads mode to under-14s: that would make us knowingly serve ads to children. Store only the resulting age band **on the device**; it drives the ad treatment (section 11.5) and is not sent to our server.
- **Play Age Signals API** (beta, global from 2026-12-11): integrate it as a second signal. If it reports 0–12, refuse online features; if it reports 13–15 or 16–17, apply the minor ad treatment even if the age screen said 18+. Age Signals data may only be used to provide age-appropriate experiences in the receiving app (policy effective 2026-01-01). Required for Texas users in practice (section 19).
- Restrict Minor Access stays off (it requires an 18+-only audience).
- **In-app purchase and teens (2026-11-25).** The audience includes 14–17-year-olds, so teens can reach the Remove ads purchase. This is allowed: Families policy does not apply (section above), and Play's own controls decide who may buy: a supervised account (Family Link) needs parental approval for purchases, and Google handles payment-method and age checks. The app must not nudge teens harder than adults (no countdowns, no pressure, no pop-up pitches; README: only Settings and a quiet link on the results screen). Minors are never shown a sales prompt during a match. Show the price exactly as Play returns it (`ProductDetails`), never a hard-coded US$4.99. Texas SB 2420 may require age ratings for in-app products (section 19); `remove_ads` does not unlock content, only removes ads.

## 11. Ads (Google Play policy + AdMob)

### 11.1 Play Ads policy

Sources: Ads policy, https://support.google.com/googleplay/android-developer/answer/9857753 ; Better Ads Experiences, https://support.google.com/googleplay/android-developer/answer/12271244

| Rule | Our design | OK? |
| --- | --- | --- |
| No full-screen ads that appear unexpectedly when the user chose to do something else | **Updated 2026-11-25:** interstitial only after a **finished match, vs computer or online**, at the natural break between the match end and the results screen; never during a match, secret entry or the waiting practice game; never on app start or exit; never while a rematch or the next team duel is about to start; cap 1 per 3 finished matches and 4 minutes apart | **Yes, with the safeguards below** |
| No ads at the beginning of a game level / content segment | Never at match start, never before secret entry | Yes |
| No full-screen video before the splash screen; no ad on app start or exit | README forbids | Yes |
| Full-screen ads must be closeable after 15 s, unless they do not block the user (e.g. after the score screen) | After the result screen; prefer formats closeable within 15 s anyway | Yes |
| Ads must not interfere with app use, mimic UI, or be placed to cause accidental clicks | Banner on calm screens only, away from buttons | Implement spacing |
| Ads only inside the app that serves them; no lock-screen ads | Banner/interstitial inside app | Yes |
| Rewarded ads that give an advantage | None | Yes |
| Ads must not be hard to tell from or block a way to pay / no deceptive offers around the purchase | The Remove ads entry is plain: Settings and one quiet link on the results screen, price from Play, no fake close buttons, no sales pop-ups | Yes |

**Safeguards for the interstitial after an online match (new 2026-11-25).** An online match ends by itself (the last guess lands, the opponent leaves, time runs out), not because the player tapped something, so the ad could pop up while the player is still tapping or typing. Google's rule targets full-screen ads that appear unexpectedly when someone chose to do something else; the end of a match is a natural break of the kind AdMob recommends ("between stages"), so the placement is allowed, but it needs these rules in the app: (1) wait at least 1 s after the result is decided, and never show it while the chat box or keyboard is open or the player is mid-tap on a control; (2) the player must not be able to hit an ad's close or install area by a tap meant for the game (the 1 s delay and a full-screen transition do this); (3) if the player has already left the screen (opponent disconnect, back pressed), skip the ad rather than show it later; (4) never show it in the review room or the practice game (the Play reviewers' path): a reviewer must be able to reach the result screens without an interstitial; (5) skip it when the player owns `remove_ads`; (6) the cap counts online and computer matches together. QA must test each of these (`POLICY_REVIEW.md` R11). The results screen shown after the ad still contains the result, so the player does not lose it.

### 11.2 AdMob placement guidance

Sources: Interstitial guidance, https://support.google.com/admob/answer/6066980 ; Disallowed interstitials, https://support.google.com/admob/answer/6201362 ; Discouraged banner implementations, https://support.google.com/admob/answer/6275345 ; Implementation guidance, https://support.google.com/admob/answer/2936217

- Interstitials at natural breaks between stages; never during a focused task; no more than one interstitial per two user actions (our cap, also for online matches since 2026-11-25: at most one per 3 finished matches and 4 minutes apart, stricter; one match is one natural break, so a team match shows at most one ad at its end, not one per duel).
- In tap-heavy games, add a short delay before the interstitial so players stop tapping (we add at least 1 s after the result appears before any ad can show).
- Banners must not sit right next to buttons or overlap/float over content. Leave clear space between the banner and the nearest control; hide the banner when the window is too small (pop-up window / split-screen) to keep that space.

### 11.3 "Contains ads" declaration

Source: Prepare your app for review, https://support.google.com/googleplay/android-developer/answer/9859455

- App content → Ads → **Yes, my app contains ads**. The "Contains ads" label then appears on the listing. Google can verify this at any time. The answer stays **Yes** even though a player can buy Remove ads: the app contains ads for everyone who has not bought. Never write "no ads" or "ad-free" as a feature claim in the listing.
- App content → **In-app purchases** is not a form: it is a label Play derives from the app. Once the app's AAB has Play Billing and the product `remove_ads` is active, the listing shows **"Offers in-app purchases"** automatically (also shown with the price range in the listing details). To check: Play Console → Monetize with Play → Products → In-app products, and the store listing preview.

### 11.4 Advertising ID declaration and AD_ID permission

Sources: Advertising ID, https://support.google.com/googleplay/android-developer/answer/6048248 ; AdMob quick start, https://developers.google.com/admob/android/quick-start ; Android 13 behaviour changes, https://developer.android.com/about/versions/13/behavior-changes-13

- Apps targeting Android 13+ that use the advertising ID must declare `com.google.android.gms.permission.AD_ID`; without it the ID is zeros. Google Mobile Ads SDK 20.4.0+ declares it automatically through manifest merge, so do not remove it with `tools:node="remove"`.
- App content → Advertising ID → **Yes, uses advertising ID**; purposes: Advertising or marketing, Analytics, Fraud prevention/security. Must match Data safety.
- Apps solely for children must not request AD_ID (not our case with an 18+ target).

**Permissions in the merged release manifest (checked 2026-11-26, `processReleaseManifest`; targetSdk 36, minSdk 26).**

| Permission | From | Note |
| --- | --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `VIBRATE` | app | game server, offline state, haptics |
| `com.google.android.gms.permission.AD_ID` | Mobile Ads SDK | kept on purpose (above); matches "Advertising ID = Yes" |
| `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_TOPICS` | Mobile Ads SDK | Privacy Sandbox on Android; no Play form of its own, covered by the ad rows of Data safety |
| `com.android.vending.BILLING` | Play Billing | `remove_ads` |
| `WAKE_LOCK` | androidx.work (via the Ads SDK) | no app feature uses it |
| `app.numbaseball.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX | signature-level, app-internal |

`FOREGROUND_SERVICE` and WorkManager's foreground service are removed on purpose. `MobileAdsInitProvider` is removed (DEF-062); `DELAY_APP_MEASUREMENT_INIT` is true. No location, camera, contacts, storage or notification permissions. Auto Backup: only `datastore/account.preferences_pb` (the token store); the age band lives in a separate `age` store that is not backed up (so a reinstall asks the age again, DEF-046).

### 11.5 Consent (EEA, UK, Switzerland, US states)

Sources: Google consent management requirements, https://support.google.com/admob/answer/13554116 ; EU user consent policy help, https://www.google.com/about/company/user-consent-policy-help/ ; Disclose to EEA users (UMP), https://developers.google.com/admob/android/privacy/gdpr ; US state regulations messages, https://support.google.com/admob/answer/10862202 ; Restricted data processing, https://support.google.com/admob/answer/14125907

- Google's EU User Consent Policy requires consent for personalized ads in the EEA/UK/Switzerland, collected through a **Google-certified CMP integrated with IAB TCF** (required since 2024-01-16 for EEA/UK and 2024-07-31 for Switzerland). Google's own CMP via the **UMP SDK** qualifies. Without a certified CMP, traffic is only eligible for non-personalized or limited ads.
- US states: create a **US state regulations message** in AdMob Privacy & messaging (UMP shows it); opt-outs are honoured through restricted data processing (RDP) / GPP. New state laws (Indiana, Kentucky, Rhode Island) took effect 2026-01-01 and are covered by Google's message.
- **Must do (Android):** call `requestConsentInfoUpdate` on every launch, `loadAndShowConsentFormIfRequired`, initialize the Mobile Ads SDK and request ads only when `canRequestAds()` is true; show a **"Privacy options"** entry in Settings when `privacyOptionsRequirementStatus == REQUIRED`. Ads still work as non-personalized if consent is refused (matches README).
- **Must do (owner, AdMob UI):** create and publish the European regulations message and US state regulations message in Privacy & messaging, in all 10 languages, with the privacy policy URL.
- **Minors (13+ audience).** Sources: AdMob targeting / age-restricted treatment, https://developers.google.com/admob/android/targeting ; Disclose to EEA users, https://developers.google.com/admob/android/privacy/gdpr ; Google ad-serving protections for teens, https://support.google.com/adspolicy/answer/12205906
  - EEA digital age of consent is 13–16 depending on the country; personalized ads for a user under that age need parental consent, which we cannot collect. AdMob's tag for users under the age of consent (now `setAgeRestrictedTreatment(AgeRestrictedTreatment.CHILD)`, replacing the deprecated TFUA) disables personalized ads and remarketing, stops sending the advertising ID and blocks third-party ad vendors; UMP then does not ask that user for consent. The treatment must be set on the ad request configuration **before** initializing the SDK, not only in UMP.
  - `AgeRestrictedTreatment.TEEN` applies Google's teen protections (the exact restrictions are documented on a page I could not read: **[UNCONFIRMED]**). Google's own teen policy for signed-in teens disables ad personalization and restricts sensitive ad categories.
  - **Recommended mapping (conservative, Google says to consult legal counsel):** age band 14–15 → `CHILD` treatment everywhere (we cannot reliably tell whether the user is in an EEA country whose age of consent is 16); 16–17 → `TEEN`; 18+ → `UNSPECIFIED` with the normal UMP flow. If Play Age Signals reports a minor, use the stricter of the two.
  - Korea: PIPC policy (2024-01-31) says personalized ads that combine behavioral data with identifiers for **under-14** users need legal-guardian consent and recommends not collecting behavioral data from known under-14 users; the minimum age 14 avoids this (section 18).

### 11.6 Ad content rating and sound

Sources: AdMob targeting, https://developers.google.com/admob/android/targeting ; Global settings, https://developers.google.com/admob/android/global-settings ; Content rating requirements for apps and ads, https://support.google.com/googleplay/android-developer/answer/9859655

- **Ads must be appropriate for the app's content rating** and not significantly more mature than the app. Google's own violation example is **a Teen-rated ad shown in an Everyone-rated app** (Content rating requirements for apps, games and ads, https://support.google.com/googleplay/android-developer/answer/9859655).
- So "an ad rating suitable for teens" (README) is only correct if IARC rates the app Teen/12+. Rule: set `RequestConfiguration.setMaxAdContentRating(...)` from the IARC result — Everyone / PEGI 3 / 전체이용가 → **G**; Everyone 10+ / PEGI 7 → **PG**; Teen / PEGI 12 / 12세이용가 → **T**. Never MA. Keep the value in build configuration so it can be changed when the rating is known. Expected: G (a number game with text chat usually gets the lowest bracket plus "Users interact").
- In AdMob's blocking controls, also block sensitive categories (gambling, dating, alcohol, sexual content, get-rich-quick) because minors are in the audience.
- `MobileAds.setAppMuted(true)` fits the quiet design, but Google warns muting **reduces video ad eligibility and may lower revenue**, and should mirror a real in-app mute control. Decision (README "Sound", DEF-044): ads are always muted app-wide (`MobileAds.setAppMuted(true)` after SDK start), whatever the game's sound setting; the lower video-ad eligibility is accepted.

### 11.7 app-ads.txt

Sources: Set up app-ads.txt, https://support.google.com/admob/answer/9363762 ; app-ads.txt FAQ, https://support.google.com/admob/answer/9675354 ; Firebase hosting option, https://support.google.com/admob/answer/9820295 ; crawlability, https://support.google.com/admob/answer/9679128

- The file must be at the **root of the developer website domain listed in the Play store listing** (Store settings → contact details → website), returned with HTTP 200 as plain text, not blocked by robots.txt. Content: `google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0` (with our publisher ID).
- AdMob's crawler builds the URL from the listed website and checks **up to one subdomain level** plus the root domain; `www.` and `m.` are skipped. Redirects within the same root domain are allowed. The app must be **published** before the crawler can verify; allow 24 hours or more.
- Free subdomains: Google documents Firebase's `PROJECT_ID.web.app` as working. `onrender.com` is on the Public Suffix List (I checked the copy bundled with the JDK in this container), so `name.onrender.com` is its own registrable domain and the crawler should check `https://name.onrender.com/app-ads.txt`. Google does not document Render specifically: **[UNCONFIRMED]**.
- **Recommendation: buy a custom domain** (about 10,000–20,000 KRW per year). It keeps the developer website, privacy URL, deletion URL and app-ads.txt stable if we ever move hosting, and keeps the owner's name out of URLs. There is no landing page (app only); the root `/` may return 404 or redirect to `/privacy` — AdMob only fetches `/app-ads.txt`. Register it with WHOIS privacy (most registrars offer it; check that the .com/.app registrar hides the registrant name).

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

## 12. Payments policy, in-app purchase "Remove ads" (updated 2026-11-25)

Sources: Understanding Google Play's Payments policy, https://support.google.com/googleplay/android-developer/answer/10281818 ; Play Billing overview, https://developer.android.com/google/play/billing ; Integrate Play Billing, https://developer.android.com/google/play/billing/integrate ; Create an in-app product, https://support.google.com/googleplay/android-developer/answer/1153481 ; Test in-app billing, https://developer.android.com/google/play/billing/test ; Korea requirements, https://support.google.com/googleplay/android-developer/answer/6223646 (none of these pages could be opened from this container; statements are from search extracts and the Play Billing documentation as I know it, marked **[UNCONFIRMED]** where I am not sure)

**Owner decision 2026-11-25:** one-time non-consumable in-app product `remove_ads`, **US$4.99** (Play converts to local prices), through **Google Play Billing**; removes all ads; no subscription; entitlement from Play (`queryPurchasesAsync` at start and on resume), restore in Settings; no server check in v1.

| Rule | Our design | OK? |
| --- | --- | --- |
| Digital goods and features in Play apps must be sold with Google Play Billing | Only Play Billing; no other payment method, no external link or text steering to another way to pay | Yes |
| Do not mislead about the purchase; show the Play price, what it gives and that it is one-time | Name "Remove ads", description "Removes all ads. One-time purchase.", price from `ProductDetails.getOneTimePurchaseOfferDetails().getFormattedPrice()` | Yes, if the app shows Play's price |
| Acknowledge purchases within 3 days or Google refunds them automatically | Acknowledge immediately after a verified `PURCHASED` state (no server: verify on device by checking state, product ID and that it is not pending); retry at the next start if it failed | Implement (README) |
| Pending purchases (cash, slow payment methods) must not grant the item until `PURCHASED` | Treat `PENDING` as "not bought", show a neutral "payment pending" note, re-check on resume | Implement |
| Refunds/revocations must be respected | `queryPurchasesAsync` at start and on resume; if the purchase is gone, ads return | Implement |
| No external donation links, no tip jar | None (README) | Yes |
| Non-consumable that follows the account | Same Google account on a new phone: found by `queryPurchasesAsync`; Settings → **Restore purchase** re-queries | Implement |
| Server-side verification | **Not done in v1 (owner decision).** Risk accepted: a rooted device can fake the state locally and hide ads for itself; there is no server-side loss, the purchase unlocks no online advantage. Google recommends server verification with the Play Developer API for anything that matters; revisit if fraud shows up or if the product ever unlocks online features | Accepted risk |
| Refunds | **Handled entirely by Google Play** (the user asks Google; the developer only sees the purchase disappear). The app, terms and policy say so; no refund form of our own. | Yes |
| Price display, tax | Play shows the local price including tax where required and converts US$4.99 using its price templates; the owner can edit any country's price | Yes |

**What it means for the account (owner must do):**
- The account becomes a **merchant account**. A **Google payments profile** must be set up for selling (legal name, address, **bank account for payouts**, tax information). The address is public already as a trader (section 2.4), so nothing new is shown.
- **Korea [UNCONFIRMED]:** the earlier review (`POLICY_REVIEW.md` R17) found that Korean developers distributing paid apps or in-app purchases must give **business registration (사업자등록) and mail-order business (통신판매업 신고) details** in the payments profile / store listing. Whether a personal account without a business registration can sell an in-app item in Korea, or must register first, I could not confirm: **the owner must check Play Console (Settings → Developer account → Payments profile, and the Korea requirements page) before creating the product**, and ask a 세무사 about registering as an individual business. This may also delay the first release; if it blocks, the fallback is to ship v1 with ads only and add `remove_ads` in an update.
- Income tax / VAT: Play collects and remits VAT/sales tax in many countries; Korean income tax on the sales is the owner's (ask a 세무사). Google's service fee on the first US$1M of yearly sales is 15% as far as I know (**[UNCONFIRMED]**: check the current Google Play service fees page), so US$4.99 nets roughly US$4.2 before tax and Korean income tax.
- **Test purchases:** add license testers (Play Console → Settings → License testing) and use an internal test track; license testers buy without being charged. Details in `RELEASE_GUIDE.md` chapter 10.
- **App review:** the reviewers must be able to see the Remove ads entry; the product must be **active** before the release that contains it, and the app version with Play Billing must be uploaded at least to the internal track so Console accepts the product.

## 13. Impersonation, intellectual property, metadata

Sources: Impersonation, https://support.google.com/googleplay/android-developer/answer/9888374 ; Impersonation FAQ, https://support.google.com/googleplay/android-developer/answer/16341334 ; Metadata, https://support.google.com/googleplay/android-developer/answer/9898842 ; Misrepresentation, https://support.google.com/googleplay/android-developer/answer/9888689

- Title max 30 characters; no emoji, no repeated special characters, ALL CAPS only if part of the brand; no ranking/price/promo text; no keyword stuffing.
- No title or icon so similar to an existing product that users could be misled; developer name must not imply a relationship with another company.
- **Excel**: the spreadsheet disguise was removed (README). Also make sure no screen, icon or screenshot imitates Microsoft Excel (green grid with column letters, ribbon, Excel-like icon, the word "Excel"). Microsoft's marks are not ours to use.
- **Names**: the owner chose a "숫자야구" (Number Baseball) style title. "Number Baseball" itself is generic and used by many Play apps, so the title adds a distinctive descriptor ("Quiet Duel" family) to avoid looking like an existing app (`STORE_LISTING.md` section 1). "Mastermind" (and German "Superhirn") and "Numer0n/ヌメロン" are commercial game brands; do not use them. Generic folk-game names (Bulls and Cows, Быки и коровы, Picas y Fijas, 1A2B) may appear once as "also known as".
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
- We have no login, so credentials are not needed, but online play needs an opponent. Choose "All or some functionality is restricted" and provide instructions (draft in `RELEASE_GUIDE.md` section 6), plus a **review room** on the server: a reserved room code where a test bot joins, plays, and sends one chat message so the reviewer can test Report and Block.
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
| Privacy policy | `https://quietdual.com/privacy` |
| App access | Restricted functionality + instructions (section 15) |
| Ads | Yes (also with Remove ads) |
| Content rating | IARC questionnaire (section 9) |
| Target audience and content | 13–15, 16–17, 18+; Restrict Minor Access off; no child-appealing listing |
| News app | No |
| Health apps | Complete the form: no health features |
| Financial features | None (an in-app purchase is not a financial feature) |
| In-app purchases | No form; Play shows the "Offers in-app purchases" label from the active `remove_ads` product (section 12). Create the product in Monetize with Play → Products → In-app products |
| Government app | No |
| Data safety | Section 5 |
| Advertising ID | Yes (section 11.4) |
| Local Network permission (API 37+ targets) | Not applicable (we target 36 and do not use it) |
| Trader status (account level) | **Trader** (owner decision 2026-11-21) |

If the Console shows a declaration not listed here, answer it from the facts in the README; ask the release lead if unsure.

## 18. Korean law: children under 14, game act, behavioural ads

Sources (Korean statutes and regulator material found by search; law.go.kr pages were not opened directly): PIPA Art. 22-2, https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1029334761 and https://casenote.kr/%EB%B2%95%EB%A0%B9/%EA%B0%9C%EC%9D%B8%EC%A0%95%EB%B3%B4_%EB%B3%B4%ED%98%B8%EB%B2%95/%EC%A0%9C22%EC%A1%B0%EC%9D%982 ; PIPC 아동·청소년 개인정보 보호 가이드라인 (2022-07), https://www.privacy.go.kr/front/bbs/bbsView.do?bbsNo=BBSMSTR_000000000049&bbscttNo=13653 ; summary of under-14 sign-up practice, https://datalaw.kr/posts/under-14-signup-consent/ ; PIPC behavioural-ads policy (2024-01-31), reported at https://m.ddaily.co.kr/page/view/2024013108364903637 ; Game Industry Promotion Act Art. 12-3, https://casenote.kr/%EB%B2%95%EB%A0%B9/%EA%B2%8C%EC%9E%84%EC%82%B0%EC%97%85%EC%A7%84%ED%9D%A5%EC%97%90_%EA%B4%80%ED%95%9C_%EB%B2%95%EB%A5%A0/%EC%A0%9C12%EC%A1%B0%EC%9D%983

**PIPA Art. 22-2 (children under 14).** "When a personal information controller must obtain consent under this Act to process the personal information of a child under 14, it must obtain the consent of the legal representative and confirm it." Penalties for violating Art. 22-2(1) appear in the 2026 amendment.

- **Is our data personal information?** Treat it as yes. A nickname#tag, account ID, token hash and records alone are pseudonymous, but together with IP addresses, chat snapshots (players can type anything about themselves) and the advertising ID collected by the ads SDK, a person can reasonably be identified. Korean law covers information that identifies a person when easily combined with other information.
- **Does it trigger guardian consent?** Art. 22-2 applies only where **consent** is the legal basis. Since the 2023 amendment, processing needed to perform a contract the user asked for does not require consent (Art. 15(1)4). On that reading, nickname, account ID/token, records, block list and reports could be processed for under-14 users without consent, so Art. 22-2 would not be triggered. **I found no official PIPC statement confirming this reading** for children; it is an interpretation.
- Three points make relying on it risky for us:
  1. **Ads**: the ads SDK collects the advertising ID and behavioural data. PIPC's 2024 policy requires **legal-guardian consent** for personalized ads that combine behavioural data with identifiers for under-14 users, and recommends not collecting behavioural data at all from users known to be under 14.
  2. **Contract capacity**: a contract (accepting our Terms) made by a minor without the legal representative's consent can be cancelled under the Civil Act (general rule for minors; not re-checked for this document).
  3. **Regulator practice**: the PIPC guideline asks services to check age (date of birth or a "만 14세 이상입니다" confirmation) and to use high-privacy defaults for children; the common practice is "만 14세 미만 가입 불가" plus an explicit 14+ confirmation.
- **Recommendation: minimum age 14, worldwide.** It removes the need for a guardian-consent flow, keeps behavioural ads away from under-14s, and is simpler than detecting Korean users (device language, SIM and IP are all unreliable, and PIPA covers Korean residents wherever the server is). Outside Korea the cost is losing 13-year-olds only; it also clears COPPA (under 13) with a margin and matches the lowest EEA consent age plus one. If the owner insists on 13 outside Korea, the age screen must then detect Korea, which I do not recommend.

**Game Industry Promotion Act Art. 12-3.** Requires game services to do real-name/age verification at sign-up and obtain **parental consent when a youth (청소년) signs up**. The Enforcement Decree limits this; the 2026 decree amendment summary says it applies to games provided by SMEs in the relevant industries with **annual revenue of KRW 5 billion or more**. An individual developer with ad revenue is far below that, so we are very likely **exempt**. **[UNCONFIRMED]**: check 시행령 제8조의3 on law.go.kr, and re-check if the business grows.

**Overseas transfer** (server abroad, Google ads): disclosed in the privacy policy (section 16); no change for the 13+ audience.

## 19. US state app-store age laws (Texas now; Utah and Louisiana in 2027)

Sources: Changes to Google Play for app store bills in applicable US states, https://support.google.com/googleplay/android-developer/answer/16569691 ; Notify Google Play of significant changes, https://developer.android.com/google/play/age-signals/notify-significant-changes ; Future of Privacy Forum comparison, https://fpf.org/blog/comparing-enacted-app-store-accountability-acts/

- Google's help page says **Texas SB 2420 is in effect** after a federal appeals court stayed the December 2025 injunction; the Play Age Signals API returns signals for eligible Texas users whose accounts were created after 2026-05-28. Utah and Louisiana laws were postponed to 2027-05-06 and 2027-07-01 (third-party source).
- Developer responsibilities per Google: provide age-appropriate experiences using the signals; **restrict content or functionality related to "significant changes"** based on the approval status the API returns for supervised users; decide when a change is significant and **notify Google Play**; Texas may require age ratings for in-app products (we now have one, `remove_ads`: it unlocks no content, only removes ads; list it in the answers the Console asks for, if asked).
- **Must do:** integrate the Play Age Signals API (latest version) and apply the minor handling in sections 10 and 11.5. Before each release that adds or changes social features (for example enabling free text in quick match, adding friends lists or DMs), decide with the release lead whether it is a significant change and notify Play if so.
- Monitor: the Texas appeal is still pending, so its status could change.

