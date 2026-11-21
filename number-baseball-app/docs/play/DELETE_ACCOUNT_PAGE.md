# DELETE_ACCOUNT_PAGE.md — 계정 삭제 페이지 / Delete your account

> **팀 메모 (공개하지 마세요 / do not publish this box)**
>
> - 서버가 `GET /delete-account` 에서 이 내용을 HTML로 보여 줍니다(`docs/protocol.md` 14절). 이 주소를 Play Console 데이터 보안 양식의 "계정 삭제 URL"에 넣습니다.
> - Play 요구사항: 페이지가 실제로 동작해야 하고, 찾기 쉬워야 하며, **스토어에 보이는 앱 이름 또는 개발자 이름**이 페이지에 있어야 합니다(https://support.google.com/googleplay/android-developer/answer/13327111). 제목에 "Pitchcode"와 `[개발자 표시명]`을 그대로 씁니다.
> - 방법 B(삭제 코드)는 현재 프로토콜에 있습니다. **방법 C(앱 없이 요청)는 아직 없습니다.** 앱을 지운 사람이 웹 페이지를 쓰는 가장 흔한 경우인데 코드가 없으면 삭제할 길이 없으므로 추가를 권장합니다(`POLICY_REVIEW.md` R5). 백엔드가 C를 만들지 않으면 C 단락을 지우고, 이메일 요청 문장만 남기세요. 이메일 요청을 받으면 운영자가 관리 API로 처리해야 합니다.
> - 폼은 쿠키·분석 도구 없이, HTTPS로만, IP 기준 요청 제한(전송 코드와 동일)을 둡니다. 이메일 주소를 입력받지 않습니다.
> - `[대괄호]` 값은 공개 전에 채웁니다.

---

## 공개 본문 — 한국어

# Pitchcode 계정 삭제

**앱: Pitchcode (숫자야구) · 개발자: [개발자 표시명]**

Pitchcode 계정과 관련 데이터를 삭제하는 방법을 안내합니다. 삭제하면 되돌릴 수 없습니다.

### 방법 A. 앱에서 바로 삭제 (가장 빠름)

1. Pitchcode 앱을 엽니다.
2. **설정 → 계정 삭제**를 누릅니다.
3. 안내를 확인하고 **삭제**를 누르면 즉시 삭제됩니다.

### 방법 B. 이 페이지에서 삭제 코드로 삭제

1. 앱의 **설정 → 계정 삭제** 옆에 있는 **웹에서 삭제할 삭제 코드 받기**를 누릅니다. 12자리 코드가 나오며 24시간 동안 쓸 수 있습니다.
2. 아래 칸에 코드를 넣고 확인란에 체크한 뒤 **계정 삭제**를 누르면 즉시 삭제됩니다.

[삭제 코드 입력란] [ ] 계정과 전적이 영구히 삭제되며 되돌릴 수 없음을 이해했습니다. [계정 삭제 버튼]

### 방법 C. 앱이 없을 때 삭제 요청

앱을 이미 지웠다면 먼저 앱을 다시 설치해 보세요. 같은 Google 계정을 쓰는 휴대폰이면 자동 백업으로 계정이 돌아올 수 있고, 그러면 방법 A로 바로 삭제할 수 있습니다.

그래도 안 되면 아래에 앱에 표시되던 이름을 `닉네임#1234` 형식으로 넣어 요청하세요.

[닉네임#태그 입력란] [ ] 이 계정이 제 계정이며 삭제를 원합니다. [삭제 요청 버튼]

- 이메일이나 비밀번호가 없는 서비스라 본인 확인을 할 수 없으므로, **요청 후 30일이 지나면 삭제됩니다.**
- 그 30일 동안 그 계정으로 앱을 쓰면 요청은 자동으로 취소되고, 앱에 "웹에서 삭제 요청이 있었습니다"라는 안내가 뜹니다. 다른 사람이 내 계정 삭제를 요청하는 것을 막기 위한 장치입니다.
- 이 방법도 어렵다면 [연락처 이메일]로 `닉네임#태그`와 마지막으로 플레이한 대략의 날짜를 보내 주세요.

### 삭제되는 데이터

- 닉네임, 태그, 계정 번호, 계정 토큰(해시값), 전적(승·패·무), 차단 목록, 기기 이전 코드와 삭제 코드: **즉시 삭제**
- 나에 대한 신고와 신고된 대화 스냅샷: **즉시 삭제**
- 내가 한 신고: 신고 내용은 처리 완료 후 180일까지 남지만, 내 계정 번호는 **즉시 지워져** 누가 신고했는지 알 수 없게 됩니다.
- 진행 중인 방: 방에서 나가게 되며(대결 중이면 기권), 이미 보낸 채팅은 "삭제된 플레이어"로 표시되다가 방이 닫히면 사라집니다. 채팅은 원래 디스크에 저장하지 않습니다.

### 삭제 후에도 남을 수 있는 데이터

- 호스팅 업체의 서버 접속 기록(IP 주소, 요청 시각): 최대 [로그 보관 일수]일 후 자동 삭제
- 다른 사람이 한 신고의 대화 스냅샷에 내 메시지가 들어 있는 경우: 해당 신고 처리 완료 후 180일까지 (닉네임 대신 "삭제된 플레이어"로 표시)
- 광고 관련 정보: Google이 자체 정책에 따라 보관합니다. 휴대폰 설정 → 광고에서 광고 ID를 삭제할 수 있습니다.
- 휴대폰의 앱 데이터와 Google 계정 자동 백업: 앱을 지우면 기기에서 사라지고, 백업은 Google 정책에 따라 정리됩니다.

자세한 내용은 [개인정보처리방침](/privacy)을 보세요. 문의: [연락처 이메일]

---

## Public text — English

# Delete your Pitchcode account

**App: Pitchcode (Number Baseball) · Developer: [개발자 표시명]**

This page explains how to delete your Pitchcode account and its data. Deletion cannot be undone.

### Option A. Delete in the app (fastest)

1. Open the Pitchcode app.
2. Go to **Settings → Delete account**.
3. Read the notice and tap **Delete**. Your account is deleted immediately.

### Option B. Delete here with a deletion code

1. In the app, next to **Settings → Delete account**, tap **Get a code to delete on the web**. You get a 12-character code that works for 24 hours.
2. Enter it below, tick the box and press **Delete account**. Your account is deleted immediately.

[Deletion code field] [ ] I understand my account and record will be permanently deleted and cannot be restored. [Delete account button]

### Option C. Request deletion without the app

If you have uninstalled the app, try reinstalling it first. On a phone that uses the same Google account, Android backup may restore your account, and then you can use Option A.

If that does not work, enter the name the app showed you, in the form `Nickname#1234`.

[Nickname#tag field] [ ] This is my account and I want it deleted. [Request deletion button]

- We have no email or password to confirm who you are, so **the account is deleted 30 days after the request.**
- If the account is used in the app during those 30 days, the request is cancelled automatically and the app shows "A deletion request was made on the web". This stops other people from deleting your account.
- If this does not work for you, email [연락처 이메일] with your `Nickname#tag` and roughly when you last played.

### What is deleted

- Nickname, tag, account ID, account token (hash), game record (wins, losses, draws), block list, transfer and deletion codes: **deleted immediately**
- Reports about you and the conversation snapshots attached to them: **deleted immediately**
- Reports you filed: the report itself stays until 180 days after it is resolved, but your account ID is **removed immediately**, so it no longer shows who filed it.
- Rooms you are in: you leave the room (forfeiting a duel in progress); your earlier chat lines show as "deleted player" until the room closes. Chat is never saved to disk.

### What may remain for a limited time

- Our hosting provider's access logs (IP address, request time): deleted automatically after at most [로그 보관 일수] days
- Your messages inside a conversation snapshot attached to someone else's report: until 180 days after that report is resolved, shown as "deleted player" instead of your nickname
- Ad-related data: kept by Google under its own policies. You can delete your advertising ID in your phone's Settings → Ads.
- App data on your phone and in your Google account backup: removed from the phone when you uninstall; backups are handled under Google's policies.

More details: [Privacy Policy](/privacy). Questions: [연락처 이메일]
