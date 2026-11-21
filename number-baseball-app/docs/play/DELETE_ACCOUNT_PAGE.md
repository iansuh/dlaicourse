# DELETE_ACCOUNT_PAGE.md — 계정 삭제 안내 / Delete your account

> **팀 메모 (공개하지 마세요)**
>
> - 서버가 `GET /delete-account` 에서 아래 두 단락을 **글자만 있는 정적 페이지**로 보여 줍니다(폼·코드·로그인 없음, README "App only"). 이 주소를 Play Console 데이터 보안 양식의 "계정 삭제 URL"에 넣습니다.
> - Play 요구사항: 앱 밖에서 삭제를 **요청**할 수 있는 웹 링크가 있어야 하고, 페이지는 동작해야 하며, 스토어와 같은 **앱 이름 또는 개발자 이름**이 있어야 합니다. Google은 그 방법으로 "고객 지원 이메일"도 인정합니다(https://support.google.com/googleplay/android-developer/answer/13327111). 그래서 이메일 안내만 있는 이 페이지로 충족됩니다.
> - 운영자 처리 절차는 `RELEASE_GUIDE.md` 10장에 있습니다. `[대괄호]` 는 공개 전에 채웁니다.

---

## 한국어

# 숫자야구: 조용한 한판 — 계정 삭제

앱: 숫자야구: 조용한 한판 · 개발자: [개발자 표시명]

**앱이 있다면:** 앱의 **설정 → 계정 삭제**를 누르면 즉시 삭제됩니다.

**앱이 없다면:** [연락처 이메일] 로 앱에 표시되던 `닉네임#1234` 를 적어 "계정 삭제 요청"을 보내 주세요. 7일 안에 삭제하고 답장합니다. 다른 사람의 요청을 막기 위해, 요청 후에도 그 계정이 앱에서 계속 쓰이고 있으면 앱에서 직접 삭제해 달라고 안내할 수 있습니다.

**삭제되는 것:** 닉네임, 태그, 계정 번호, 계정 토큰(해시값), 전적, 차단 목록, 기기 이전 코드, 나에 대한 신고와 그 대화 기록. 내가 한 신고에서는 내 계정 번호가 지워집니다.

**잠시 남을 수 있는 것:** 다른 사람에 대한 신고 기록에 들어 있는 내 메시지("삭제된 플레이어"로 표시, 신고 처리 후 최대 180일), 호스팅 업체의 접속 기록(최대 [로그 보관 일수]일), 삭제 요청 이메일(처리 후 30일). 광고 정보는 Google 정책에 따릅니다.

자세한 내용: [개인정보처리방침](/privacy)

---

## English

# Number Baseball: Quiet Duel — Delete your account

App: Number Baseball: Quiet Duel · Developer: [개발자 표시명]

**If you have the app:** open **Settings → Delete account**. Your account is deleted immediately.

**If you don't:** email [연락처 이메일] with the subject "Delete my account" and the `Nickname#1234` the app showed you. We delete it within 7 days and reply. To protect players from requests made by someone else, if the account is still being used in the app after your request, we may ask you to delete it in the app instead.

**What is deleted:** nickname, tag, account ID, account token (hash), game record, block list, transfer code, reports about you and their conversation snapshots. Your account ID is removed from reports you filed.

**What may remain for a limited time:** your messages inside snapshots attached to reports about other players (shown as "deleted player", up to 180 days after the report is resolved), our hosting provider's access logs (up to [로그 보관 일수] days), and your deletion email (30 days after we handle it). Ad data is handled under Google's policies.

More details: [Privacy Policy](/privacy)
