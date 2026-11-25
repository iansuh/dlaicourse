# PRIVACY_POLICY.md — 개인정보처리방침 / Privacy Policy

> **운영자 메모 (공개하지 마세요 / do not publish this box)**
>
> - **앱 전용(2026-11-21 결정)**: 방침은 앱의 설정 화면에서 보여 주고, Google이 공개 URL을 요구하므로 서버가 `GET /privacy` 에서 같은 글을 정적 페이지로도 보여 줍니다(웹사이트·폼 없음). 아래 "공개 본문" 두 개(한국어, English)를 씁니다. 같은 페이지에 두 언어를 함께 두거나 `/privacy?lang=en` 으로 나눠도 됩니다. PDF는 안 됩니다(Play 정책).
> - `[대괄호]` 는 공개 전에 반드시 채우세요. **2026-11-21 결정(트레이더 신고)** 으로 운영자 성명·주소·전화·이메일은 Google Play에 공개되므로, 이 방침의 "운영자 정보"에도 같은 값을 넣습니다. 그 밖의 곳에는 개인 이메일·개인 전화번호를 쓰지 말고 게임 전용 이메일·번호를 쓰세요.
> - 앱 이름은 추천안(숫자야구: 조용한 한판 / Number Baseball: Quiet Duel)으로 넣었습니다. 바뀌면 제목과 첫 문단만 고칩니다. 최소 나이 14세는 `POLICY_REVIEW.md` R19의 권고이며, 사장님이 다르게 정하면 9절을 고칩니다.
> - **연락 방법(개인정보 노출 없이)**, 둘 중 하나 이상:
>   1. **게임 전용 이메일**을 새로 만듭니다(예: Gmail에서 `pitchcode.support@gmail.com` 같은 주소, 실명이 들어가지 않게). Play Console의 개발자 이메일·지원 이메일·아동안전 담당 연락처에도 같은 주소를 씁니다. **권장.**
>   2. 앱 안 **문의하기 폼**(설정 → 문의하기): 서버에 문의 내용만 저장하고 답장은 앱 안 알림으로 보냅니다. 이메일을 받지 않으므로 수집 항목이 늘지 않지만 개발이 필요하고, 앱을 지운 사람은 쓸 수 없어 이메일을 대신하지는 못합니다. 만들면 아래 2절 표에 "문의 내용(보관 1년)"을 추가하세요.
> - 이 방침은 `docs/protocol.md`(v1) 기준입니다. 서버가 실제로 그렇게 동작하는지 백엔드 담당이 확인해야 하며, 다르면 방침을 고쳐야 합니다. 특히 확인할 것: 서버 로그에 IP·채팅·토큰을 남기지 않는지, 대결 중 비밀 숫자·추측이 메모리에만 있는지, 처리된 신고 180일 후 삭제, 계정 삭제 시 다른 신고 스냅샷의 닉네임을 "삭제된 플레이어"로 바꾸는지, 호스팅 업체 로그 보관 기간. (이 중 180일 삭제와 닉네임 치환은 아직 프로토콜에 없으므로 `POLICY_REVIEW.md`의 요구사항으로 요청했습니다.)
> - Play Console 데이터 보안(Data safety) 답변(`POLICY_CHECKLIST.md` 5절)과 이 방침은 항상 같아야 합니다.
> - **2026-11-25 변경(광고 제거 인앱 구매 `remove_ads`)**: 2절 표에 "구매 정보" 행, 3절 보유 기간 행, 4절 문장, 7-1절(새 절), 8절 삭제 항목이 추가됐습니다. 앱은 Google Play에서 구매 상태·구매 토큰만 받고 서버로 보내지 않는다는 전제입니다(서버 확인 없음). Android 담당이 (1) 구매 토큰을 우리 서버나 로그로 보내지 않는지, (2) 광고 제거 후 Mobile Ads SDK를 초기화하지 않고 동의 화면도 띄우지 않는지 확인해야 하며, 다르면 7-1절을 고칩니다. 구매 내역은 Data safety의 "금융 정보 → 구매 내역" 행과 맞춥니다.
>
> 채울 값: `[시행일]`, `[운영자 성명]`, `[운영자 주소]`, `[게임 전용 전화번호]`, `[개발자 표시명]`(스토어에 보이는 개발자 이름), `[연락처 이메일]`, `[도메인]`, `[개인정보 보호책임자: 직책 또는 성명]`, `[호스팅 업체명]`, `[호스팅 업체 연락처 또는 개인정보 문의 페이지]`, `[서버 국가]`, `[로그 보관 일수]`.

---

## 공개 본문 — 한국어

# 숫자야구: 조용한 한판 개인정보처리방침

시행일: [시행일]

[운영자 성명](Google Play 개발자 이름: [개발자 표시명], 이하 "운영자")은 숫자야구 게임 앱 **숫자야구: 조용한 한판**(이하 "앱")와 앱이 쓰는 게임 서버 및 웹 페이지(`https://[도메인]`)에서 이용자의 개인정보를 아래와 같이 처리합니다. 앱은 개인 개발자가 운영합니다.

운영자 정보: [운영자 성명] / 주소 [운영자 주소] / 전화 [게임 전용 전화번호] / 이메일 [연락처 이메일] (Google Play의 개발자 정보와 같습니다)

### 1. 한눈에 보기

- 회원가입이 없습니다. **실명, 이메일, 전화번호, 비밀번호, 정확한 위치, 연락처, 사진을 받지 않습니다.**
- 서버에는 닉네임, 무작위로 만든 계정 번호, 전적, 차단 목록, 신고 기록만 저장합니다.
- 채팅은 서버 메모리에서만 전달되고 디스크에 저장하지 않습니다. 단, 신고된 대화는 신고 처리를 위해 일부 저장합니다.
- 앱에는 **Google AdMob 광고**가 있습니다. 광고를 위해 Google이 광고 ID 등 기기 정보를 수집합니다(7절).
- 선택 사항인 **"광고 제거" 1회 구매**는 Google Play가 결제합니다. 운영자는 카드번호 등 결제 정보를 받지 않습니다(7-1절).
- 개인정보를 판매하지 않습니다.
- **만 14세 이상만** 이용할 수 있으며, 만 18세 미만에게는 맞춤형 광고를 하지 않습니다.
- 계정은 앱 설정에서 언제든 삭제할 수 있고, 앱이 없으면 이메일로 요청할 수 있습니다.

### 2. 처리하는 개인정보 항목과 목적

| 구분 | 항목 | 목적 | 수집 방법 |
| --- | --- | --- | --- |
| 계정 | 닉네임, 4자리 태그(예: `#0420`), 계정 번호(무작위 12자), 계정 토큰의 해시값(토큰 원문은 저장하지 않음), 계정 생성 시각 | 계정 식별, 온라인 대결, 이름 표시 | 처음 실행할 때 앱이 자동 생성 |
| 전적 | 승·패·무 횟수 | 기록 표시 | 온라인 대결 결과 |
| 차단 목록 | 내가 차단한 플레이어의 계정 번호 | 차단한 사람의 채팅 숨기기, 매칭 제외 | 이용자가 차단할 때 |
| 신고 | 신고한 사람과 신고된 사람의 계정 번호, 신고 사유, 선택 입력한 메모(500자 이내), 방 번호, 신고된 메시지와 그 방의 직전 메시지 최대 20개(가려진 상태 그대로, 보낸 사람 닉네임 포함), 처리 결과 | 신고 검토, 이용 제한, 악용 방지 | 이용자가 신고할 때 |
| 이용 제한 | 이용 정지 여부와 사유 | 이용약관 위반 대응 | 신고 처리 결과 |
| 임시 코드 | 기기 이전 코드 | 다른 휴대폰으로 계정 이동 | 이용자가 요청할 때 |
| 삭제 요청 이메일 | 보낸 이메일 주소, `닉네임#태그`, 내용 | 앱 없이 계정 삭제 요청 처리 | 이용자가 이메일을 보낼 때 |
| 채팅 | 방 안의 채팅 메시지 | 같은 방 사람에게 전달 | 이용자가 보낼 때 (메모리에서만 처리) |
| 대결 정보 | 비밀 숫자, 추측, 남은 시간 | 판정과 진행 | 대결 중 (메모리에서만 처리, 결과만 전적에 반영) |
| 접속 정보 | IP 주소, 앱 버전, 접속 시각 | 과도한 요청 차단(예: 시간당 계정 생성 횟수 제한), 장애 대응, 보안 | 서버 접속 시 자동 |
| 연령대 | 처음 실행할 때 입력한 출생 연도·월로 앱이 계산한 연령대(만 14세 미만 / 14~15세 / 16~17세 / 18세 이상). 출생 연도·월 자체는 저장하지 않음 | 이용 가능 여부 확인, 미성년자 광고 보호 설정 | **연령대만 휴대폰에 저장**, 서버로 보내지 않음 |
| 약관 동의 | 동의한 약관 버전, 동의 시각, 만 14세 이상 확인 여부 | 이용약관 동의 기록 | 약관 동의 화면 |
| 구매 정보 (Google Play가 처리) | 상품 ID(`remove_ads`), 구매 상태(구매됨/보류), 구매 토큰, 구매 확인(acknowledge) 여부. **카드번호·계좌·결제 수단·실명은 받지 않습니다.** | "광고 제거" 구매 확인, 광고 숨기기, 구매 복원 | Google Play 결제 라이브러리가 앱에 전달 (**휴대폰에서만 확인하며 운영자 서버로 보내지 않음**) |
| 광고 정보 (Google이 수집) | 광고 ID, 앱 세트 ID, IP 주소(대략적인 위치 추정), 광고 상호작용, 진단 정보 | 광고 표시와 측정, 부정 클릭 방지 | Google Mobile Ads SDK가 자동 수집 (7절) |

앱은 계정 토큰을 기기에 저장하고, 안드로이드 자동 백업을 켠 경우 이용자 본인의 Google 계정 백업에 포함됩니다. 이 백업은 Google이 관리하며 운영자는 볼 수 없습니다. 같은 Google 계정으로 새 휴대폰을 설정하면 계정을 되찾는 데 쓰입니다.

### 3. 보유 기간과 파기

| 항목 | 보유 기간 |
| --- | --- |
| 계정, 전적, 차단 목록, 약관 동의 기록 | 계정을 삭제할 때까지. 삭제하면 즉시 지웁니다. |
| 기기 이전 코드 | 10분 또는 사용 즉시 |
| 구매 정보(구매 상태·구매 토큰) | 운영자는 서버에 저장하지 않습니다. 앱이 휴대폰에서 Google Play에 조회한 결과만 쓰며, 앱을 지우면 함께 사라집니다. 구매 기록 자체는 Google이 이용자의 Google 계정에 Google의 정책에 따라 보관합니다. |
| 삭제 요청 이메일 | 처리 완료 후 30일 |
| 채팅 메시지 | 방이 있는 동안 서버 메모리에 최근 50개만. 방이 비거나 10분간 접속자가 없어 닫히면 사라지며 디스크에 저장하지 않습니다. |
| 대결 정보 | 대결이 끝날 때까지 메모리에서만 |
| 신고 기록과 대화 스냅샷 | 처리 완료 후 180일. 신고된 사람이 계정을 삭제하면 그 사람에 대한 신고와 스냅샷은 즉시 지웁니다. 신고한 사람이 계정을 삭제하면 신고 기록에서 그 사람의 계정 번호를 지웁니다. 다른 사람에 대한 신고 스냅샷에 들어 있는 삭제된 계정의 메시지는 닉네임 대신 "삭제된 플레이어"로 바꿔 같은 기간 보관합니다. |
| 이용 정지 기록 | 정지가 풀리거나 계정이 삭제될 때까지 |
| IP 주소(요청 제한용) | 서버 메모리에서 제한 시간(최대 1시간) 동안만. 운영자의 데이터베이스에는 저장하지 않습니다. |
| 서버 접속 기록 | 호스팅 업체의 접속 로그에 IP 주소와 요청 시각이 [로그 보관 일수]일 동안 남을 수 있으며, 그 후 자동 삭제됩니다. |

파기는 데이터베이스에서 복구할 수 없게 삭제하는 방식으로 합니다. 법령에 따라 보관해야 하는 정보는 현재 없습니다.

### 4. 제3자 제공

운영자는 이용자의 개인정보를 다른 회사나 개인에게 판매하거나 제공하지 않습니다. 다만 법령에 따른 수사기관의 적법한 요청이 있으면 그 범위에서 제공할 수 있습니다. 앱 안 광고에 쓰이는 정보는 Google이 앱에서 직접 수집합니다(7절). 앱 안 구매의 결제는 Google Play가 Google의 약관과 개인정보처리방침에 따라 직접 처리하며, 운영자는 그 결제 정보를 받지 않습니다(7-1절).

### 5. 처리 위탁

| 받는 자 | 맡기는 일 |
| --- | --- |
| [호스팅 업체명] | 게임 서버 운영과 데이터 보관 |

### 6. 국외 이전

| 항목 | 서버 호스팅 | 광고 (Google AdMob) |
| --- | --- | --- |
| 이전받는 자 (연락처) | [호스팅 업체명] ([호스팅 업체 연락처 또는 개인정보 문의 페이지]) | Google LLC (https://policies.google.com/privacy 의 문의 경로) |
| 이전 국가 | [서버 국가] | 미국 등 Google이 데이터를 처리하는 국가 |
| 이전 항목 | 2절의 서버 저장 항목과 접속 정보 | 2절의 광고 정보 |
| 이전 시기와 방법 | 온라인 기능을 쓸 때 암호화된 네트워크(HTTPS/WSS)로 전송 | 광고가 표시될 때 SDK가 암호화된 네트워크로 전송 |
| 이용 목적과 보유 기간 | 게임 서비스 제공, 3절의 기간 | 광고 제공·측정·부정 방지, Google의 정책에 따른 기간 |
| 거부 방법과 효과 | 온라인 기능을 쓰지 않으면 이전되지 않습니다(컴퓨터 대결은 인터넷 없이 가능). 거부하면 온라인 대결과 채팅을 쓸 수 없습니다. | 7절의 방법으로 맞춤형 광고를 거부할 수 있습니다. 광고 자체는 무료 앱 운영을 위해 계속 표시됩니다. |

근거: 개인정보 보호법의 국외 이전 규정에 따라 이 처리방침에 공개합니다.

### 7. 광고와 광고 ID

- 앱은 Google AdMob 광고를 보여 줍니다. 대결 중에는 광고가 나오지 않습니다.
- Google Mobile Ads SDK는 광고 ID, 앱 세트 ID, IP 주소, 광고 상호작용, 진단 정보를 수집해 광고 제공, 측정, 부정 클릭 방지에 씁니다. Google이 데이터를 쓰는 방식: https://policies.google.com/technologies/partner-sites
- 유럽경제지역, 영국, 스위스 및 일부 미국 주에서는 앱이 처음에 동의 화면을 보여 줍니다. 앱 **설정 → 개인정보 옵션**에서 언제든 선택을 바꿀 수 있습니다. 동의하지 않아도 앱은 그대로 쓸 수 있고 맞춤형이 아닌 광고가 나옵니다.
- 모든 지역에서 휴대폰 **설정 → 개인정보 보호(또는 Google) → 광고**에서 광고 ID를 재설정하거나 삭제할 수 있습니다(기기마다 메뉴 이름이 조금 다릅니다).
- **만 18세 미만 이용자**: 처음 실행할 때 입력한 출생 연도·월로 계산한 연령대에 따라 앱이 Google에 "연령 제한 처리"를 요청합니다. 만 14~15세는 맞춤형 광고를 하지 않고 광고 ID도 보내지 않으며, 만 16~17세는 Google의 청소년 보호 기준을 적용합니다. Google Play가 알려 주는 연령 정보(Play 연령 신호)도 같은 목적에만 씁니다.
- 운영자는 광고 ID를 직접 받거나 저장하지 않습니다.

### 7-1. 인앱 구매 (광고 제거)

- 앱은 **"광고 제거"**(상품 ID `remove_ads`, 1회 구매, 구독 아님)를 **Google Play 결제**로 판매합니다. 가격은 구매 전에 Google Play에 현지 통화로 표시됩니다.
- **결제는 Google Play가 처리합니다.** 카드번호, 계좌, 결제 수단, 청구 주소, 이름은 Google이 받으며 운영자는 받지 못합니다. 그 정보의 처리는 Google의 약관과 개인정보처리방침(https://policies.google.com/privacy)을 따릅니다.
- **앱이 받는 것은** 구매 상품 ID, 구매 상태(구매됨·보류·취소), 구매 토큰, 구매 확인 여부뿐입니다. 앱은 이를 휴대폰에서 Google Play 결제 라이브러리로 조회해 광고를 숨길지 정합니다. **운영자 서버로 보내거나 서버에 저장하지 않습니다**(서버 확인 없음).
- **구매는 Google 계정에 묶입니다.** 같은 Google 계정으로 새 휴대폰을 쓰거나 앱을 다시 설치하면 앱이 자동으로 구매를 확인하고, 설정의 **구매 복원**으로 직접 다시 확인할 수도 있습니다.
- **환불**은 Google Play의 환불 정책에 따라 Google Play를 통해 요청합니다. 환불되거나 취소된 구매는 앱이 다음에 확인할 때 광고가 다시 나옵니다. 운영자가 결제 내역에 접근할 수는 없습니다.
- 광고를 제거하면 앱은 광고를 불러오지 않고 광고 동의 화면도 더 이상 띄우지 않습니다. 만 18세 미만 이용자의 구매는 Google Play의 가족·보호자 설정에 따라 보호자 승인이 필요할 수 있습니다.

### 8. 이용자의 권리

- **열람**: 앱의 프로필에서 닉네임, 태그, 전적을 볼 수 있습니다. 그 밖의 정보는 [연락처 이메일]로 요청하세요.
- **정정**: 앱 설정에서 닉네임을 바꿀 수 있습니다.
- **삭제**: 앱 **설정 → 계정 삭제**(즉시). 앱이 없으면 [연락처 이메일]로 `닉네임#태그`를 보내 주세요(7일 안에 삭제). 안내: `https://[도메인]/delete-account`
  - **계정을 삭제해도 "광고 제거" 구매는 Google 계정에 그대로 남습니다.** 운영자 서버에는 구매 정보가 없으므로 지울 것이 없고, 환불도 되지 않습니다. 같은 Google 계정으로 앱을 다시 설치하고 설정의 **구매 복원**을 누르면 새 계정에서도 광고가 제거됩니다. 구매 기록 삭제와 환불은 Google Play에서 요청합니다.
- **처리 정지·동의 철회**: 광고는 7절의 방법으로, 그 밖의 처리는 계정 삭제 또는 이메일 요청으로.
- 요청은 [연락처 이메일]로 보내 주세요. 이메일이나 이름을 묻지 않는 서비스이므로, 계정 확인을 위해 앱에 표시되는 `닉네임#태그`를 적어 주세요. 그 계정이 계속 앱에서 쓰이고 있으면 앱에서 직접 처리해 달라고 안내할 수 있습니다. 10일 이내에 답변합니다.

### 9. 아동과 청소년

- 앱은 **만 14세 이상**만 이용할 수 있습니다. 처음 실행할 때 나이를 확인하고, 만 14세 미만이면 계정을 만들지 않고 광고도 불러오지 않습니다. 따라서 만 14세 미만 아동의 개인정보를 수집하지 않으며, 법정대리인 동의 절차도 두지 않습니다.
- 만 14세 미만 아동이 이용 중인 사실을 알게 되면 해당 계정을 즉시 삭제합니다. 보호자는 [연락처 이메일]로 알려 주세요.
- 만 14세 이상 청소년도 이용할 수 있으므로, 빠른 대결에서는 정해진 문구만 쓸 수 있게 하고 신고·차단·채팅 끄기를 제공합니다.

### 10. 안전성 확보 조치

- 앱과 서버 사이의 모든 통신은 HTTPS/WSS로 암호화합니다.
- 계정 토큰은 원문 대신 해시값만 저장합니다. 비밀번호를 쓰지 않으므로 유출될 비밀번호가 없습니다.
- 꼭 필요한 정보만 저장하고, 채팅은 디스크에 저장하지 않습니다.
- 관리 기능은 별도 비밀 키가 있는 운영자만 쓸 수 있습니다.
- 웹 페이지는 쿠키나 방문 분석 도구를 쓰지 않습니다.

### 11. 개인정보 보호책임자와 연락처

- 개인정보 보호책임자: [운영자 성명] (운영자)
- 연락처: [연락처 이메일]

개인정보 침해에 대한 상담이나 신고는 아래 기관에도 할 수 있습니다.

- 개인정보침해신고센터: privacy.kisa.or.kr / 국번 없이 118
- 개인정보분쟁조정위원회: www.kopico.go.kr / 1833-6972
- 대검찰청: www.spo.go.kr / 국번 없이 1301
- 경찰청: ecrm.police.go.kr / 국번 없이 182

### 12. 해외 이용자에 대한 추가 안내

- 유럽경제지역·영국 이용자: 운영자는 게임 제공(계약 이행), 안전한 채팅과 악용 방지(정당한 이익), 맞춤형 광고(동의)를 근거로 처리합니다. 열람, 정정, 삭제, 처리 제한, 반대, 이동을 요청할 권리와 거주국 감독기관에 민원을 제기할 권리가 있습니다.
- 미국 일부 주 이용자: 운영자는 개인정보를 판매하지 않습니다. 맞춤형 광고와 관련한 거부는 앱 **설정 → 개인정보 옵션**에서 할 수 있습니다.

### 13. 방침 변경

내용이 바뀌면 시행 7일 전(이용자에게 불리한 변경은 30일 전)에 이 페이지와 앱 안에서 알립니다.

---

## Public text — English

# Number Baseball: Quiet Duel — Privacy Policy

Effective date: [시행일]

[운영자 성명] (developer name on Google Play: [개발자 표시명]; "we") runs the number-guessing game app **Number Baseball: Quiet Duel** (the "app"), its game server and the web pages at `https://[도메인]`. This policy explains what personal data we process and why. The app is run by an individual developer.

Who we are: [운영자 성명] / address [운영자 주소] / phone [게임 전용 전화번호] / email [연락처 이메일] (the same details as in our Google Play developer information)

### 1. Summary

- There is no sign-up. **We do not ask for your real name, email, phone number, password, precise location, contacts or photos.**
- Our server stores only a nickname, a random account number, your game record, your block list and reports.
- Chat is relayed through server memory and is not saved to disk, except conversations that someone reports.
- The app shows **Google AdMob ads**. Google collects device information such as the advertising ID for ads (section 7).
- An optional one-time **"Remove ads" purchase** is paid through Google Play. We never receive your card or other payment details (section 7-1).
- We do not sell personal data.
- The app is for people **aged 14 and over**, and users under 18 do not get personalized ads.
- You can delete your account at any time in the app, or by email if you no longer have the app.

### 2. What we process and why

| Category | Data | Purpose | How we get it |
| --- | --- | --- | --- |
| Account | Nickname, 4-digit tag (e.g. `#0420`), random 12-character account ID, a hash of your account token (never the token itself), creation time | Identify your account, online play, show your name | Created automatically on first launch |
| Game record | Wins, losses, draws | Show your record | Online match results |
| Block list | Account IDs of players you blocked | Hide their chat, avoid matching you | When you block someone |
| Reports | Account IDs of the reporter and the reported player, reason, optional note (up to 500 characters), room ID, the reported message and up to 20 earlier messages in that room as delivered (masked, with sender nicknames), outcome | Review reports, enforce the Terms, prevent abuse | When someone files a report |
| Enforcement | Whether an account is suspended, and why | Respond to Terms violations | Report outcomes |
| Temporary code | Device transfer code | Move your account to another phone | When you request one |
| Deletion request email | Your email address, `Nickname#tag`, message | Handle deletion requests made without the app | When you email us |
| Chat | Messages in a room | Deliver them to the room | When you send them (memory only) |
| Match data | Secret numbers, guesses, timers | Judge and run the match | During a match (memory only; only the result is saved) |
| Connection data | IP address, app version, time | Rate limiting (e.g. account creations per hour), troubleshooting, security | Automatically when you connect |
| Age band | The age band the app works out from the birth year and month you enter on first launch (under 14 / 14–15 / 16–17 / 18+). The birth year and month themselves are not stored | Decide whether the app can be used; apply ad protections for minors | **Only the band is stored, only on your phone**, never sent to us |
| Terms acceptance | Terms version accepted, time, confirmation of being 14 or older | Record that you accepted the Terms | Terms screen |
| Purchase data (handled by Google Play) | Product ID (`remove_ads`), purchase state (purchased / pending), purchase token, whether the purchase has been acknowledged. **We do not receive card numbers, bank details, payment methods or your real name.** | Confirm the "Remove ads" purchase, hide ads, restore the purchase | Passed to the app by the Google Play Billing library (**checked on your phone only; not sent to our server**) |
| Ad data (collected by Google) | Advertising ID, app set ID, IP address (to estimate approximate location), ad interactions, diagnostics | Serve and measure ads, prevent ad fraud | Google Mobile Ads SDK (section 7) |

The app stores your account token on your device. If Android Auto Backup is on, it is included in your own Google account backup, which Google manages and we cannot see. It lets you restore your account on a new phone set up with the same Google account.

### 3. Retention and deletion

| Data | Kept for |
| --- | --- |
| Account, game record, block list, Terms acceptance | Until you delete your account; deleted immediately when you do |
| Device transfer code | 10 minutes or until used |
| Purchase data (purchase state, purchase token) | We do not store it on our server. The app only uses what it queries from Google Play on your phone, and it disappears when you uninstall the app. The purchase record itself is kept by Google in your Google account under Google's policies. |
| Deletion request email | 30 days after we handle it |
| Chat messages | Only the last 50 per room, in server memory, while the room exists. Gone when the room closes (empty, or 10 minutes with nobody connected). Never written to disk. |
| Match data | In memory until the match ends |
| Reports and conversation snapshots | 180 days after the report is resolved. If the reported player deletes their account, reports about them and their snapshots are erased at once; if the reporter deletes theirs, their account ID is removed from their reports. A deleted player's messages inside snapshots attached to reports about someone else are kept for the same period with the nickname replaced by "deleted player". |
| Suspension records | Until the suspension ends or the account is deleted |
| IP address (rate limiting) | In server memory for the limit window only (at most 1 hour); not stored in our database |
| Server access logs | Our hosting provider's access logs may contain IP addresses and request times for [로그 보관 일수] days, then they are deleted automatically |

Deletion means irreversible removal from our database. No law currently requires us to keep any of this data longer.

### 4. Sharing

We do not sell or give your personal data to other companies or people, except when required by law (for example a valid request from law enforcement). Ad data is collected directly by Google inside the app (section 7). Payment for in-app purchases is processed directly by Google Play under Google's terms and privacy policy; we do not receive that payment data (section 7-1).

### 5. Service providers

| Provider | Service |
| --- | --- |
| [호스팅 업체명] | Runs our game server and stores its data |

### 6. International transfers

| | Server hosting | Ads (Google AdMob) |
| --- | --- | --- |
| Recipient (contact) | [호스팅 업체명] ([호스팅 업체 연락처 또는 개인정보 문의 페이지]) | Google LLC (contact routes at https://policies.google.com/privacy) |
| Country | [서버 국가] | United States and other countries where Google processes data |
| Data | Server-stored data and connection data in section 2 | Ad data in section 2 |
| When and how | When you use online features, over encrypted connections (HTTPS/WSS) | When ads load, over encrypted connections |
| Purpose and retention | Running the game, periods in section 3 | Serving, measuring and protecting ads; Google's retention periods |
| How to refuse, and the effect | Do not use online features (vs Computer works offline); online play and chat will not be available | Refuse personalized ads as described in section 7; ads still appear because they fund the free app |

### 7. Ads and the advertising ID

- The app shows Google AdMob ads. Ads never appear during a match.
- The Google Mobile Ads SDK collects the advertising ID, app set ID, IP address, ad interactions and diagnostics to serve and measure ads and prevent fraud. How Google uses data: https://policies.google.com/technologies/partner-sites
- In the EEA, the UK, Switzerland and some US states, the app shows a consent or choices screen. You can change your choice at any time in **Settings → Privacy options** in the app. If you do not consent, the app still works and shows non-personalized ads.
- Everywhere, you can reset or delete your advertising ID in your phone's **Settings → Privacy (or Google) → Ads** (menu names vary by device).
- **Users under 18**: based on the age band the app works out from the birth year and month you enter on first launch, the app asks Google for age-restricted ad treatment. Users aged 14–15 get no personalized ads and their advertising ID is not sent; users aged 16–17 get Google's teen protections. Age information provided by Google Play (Play Age Signals) is used only for the same purpose.
- We do not receive or store your advertising ID ourselves.

### 7-1. In-app purchase (Remove ads)

- The app sells **"Remove ads"** (product ID `remove_ads`, a one-time purchase, not a subscription) through **Google Play Billing**. The price is shown in your local currency in Google Play before you buy.
- **Google Play processes the payment.** Google, not us, receives your card number, bank details, payment method, billing address and name; Google handles them under its terms and privacy policy (https://policies.google.com/privacy).
- **What the app receives** is only the product ID, the purchase state (purchased, pending, cancelled), the purchase token and whether the purchase is acknowledged. The app queries this on your phone through the Google Play Billing library to decide whether to hide ads. **It is not sent to or stored on our server** (there is no server-side check).
- **The purchase belongs to your Google account.** If you use the same Google account on a new phone or reinstall the app, the app finds the purchase automatically, and you can also check it yourself with **Restore purchase** in Settings.
- **Refunds** are requested through Google Play under Google Play's refund policy. If a purchase is refunded or cancelled, ads come back the next time the app checks. We cannot see your payment history.
- Once ads are removed the app does not load ads and no longer shows the ad consent screen. For users under 18, purchases may need parental approval under Google Play's family settings.

### 8. Your rights

- **Access**: your profile in the app shows your nickname, tag and record. Ask us at [연락처 이메일] for anything else.
- **Correction**: change your nickname in Settings.
- **Deletion**: **Settings → Delete account** in the app (immediate). Without the app, email your `Nickname#tag` to [연락처 이메일] (deleted within 7 days). Instructions: `https://[도메인]/delete-account`
  - **Deleting your account does not remove the "Remove ads" purchase from your Google account.** Our server holds no purchase data, so there is nothing for us to erase, and nothing is refunded. If you reinstall the app with the same Google account and tap **Restore purchase** in Settings, ads are removed for the new account too. Ask Google Play to delete purchase records or to refund.
- **Objection / withdrawing consent**: for ads, see section 7; for everything else, delete your account or email us.
- Send requests to [연락처 이메일]. Because we never ask for your name or email, please include the `Nickname#tag` shown in the app. If that account is still being used in the app, we may ask you to handle it in the app instead. We reply within 10 days.

### 9. Children and teens

- The app is for people **aged 14 and over**. It asks your age on first launch; if you are under 14, it does not create an account and does not load ads. We therefore do not collect personal data from children under 14 and have no parental-consent process.
- If we learn that a child under 14 is using the app, we delete the account immediately. Parents can tell us at [연락처 이메일].
- Because teens aged 14 and over can play, Quick Match chat is limited to preset phrases, and reporting, blocking and turning chat off are always available.

### 10. Security

- All traffic between the app and our server is encrypted (HTTPS/WSS).
- We store only a hash of each account token. There are no passwords to leak.
- We keep only what we need, and chat is not saved to disk.
- Moderation tools need a separate secret key held only by us.
- Our web pages use no cookies and no visitor analytics.

### 11. Privacy contact

- Person responsible for privacy: [운영자 성명] (operator)
- Contact: [연락처 이메일]

### 12. Additional information for some regions

- **EEA and UK**: we process data to provide the game (contract), to keep chat safe and prevent abuse (legitimate interests), and for personalized ads (consent). You have the right to access, correct, delete, restrict, object and port your data, and to complain to your local data protection authority.
- **US states with privacy laws**: we do not sell personal data. You can opt out of ad personalization in **Settings → Privacy options**.
- **Korea**: you can also contact the Personal Information Infringement Report Center (privacy.kisa.or.kr, 118) or the Personal Information Dispute Mediation Committee (www.kopico.go.kr, 1833-6972).

### 13. Changes

We will announce changes on this page and in the app 7 days before they take effect (30 days for changes that reduce your rights).
