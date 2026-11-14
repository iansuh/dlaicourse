# 인터넷에 올리기 (Render 기준)

컴퓨터를 몰라도 따라 할 수 있게 클릭 순서대로 적었습니다. 화면 문구는 서비스가 바뀌면 조금 다를 수 있습니다.

## 0. 먼저 알아둘 것

| 단계 | 비용 | 되는 것 |
| --- | --- | --- |
| 무료로 맛보기 | 0원 | 모든 기능이 동작. 단 15분간 접속이 없으면 서버가 잠들고(다시 깨는 데 약 1분), 그때 **계정과 방이 전부 사라짐** |
| 제대로 운영 | 월 약 7.25달러 (Starter 7달러 + 디스크 1GB 0.25달러) | 서버가 항상 켜져 있고 계정·전적이 유지됨 |
| 도메인 (선택) | 연 1~2만 원 | `내이름.onrender.com` 대신 원하는 주소 |

가격은 2026년 11월에 확인한 값입니다. 가입 전에 render.com/pricing 에서 다시 확인하세요.

**개인정보**: 서비스 이름이 그대로 주소가 됩니다(`이름.onrender.com`). 실명이 들어가지 않게 정하세요.

## 1. Render 가입

1. https://render.com 접속 → **Get Started** 클릭
2. **GitHub로 가입** 선택 (게임 코드가 GitHub에 있어서 이게 제일 편합니다)
3. GitHub 화면에서 Render에 권한을 허락

## 2. 게임 서버 만들기

1. Render 대시보드 오른쪽 위 **New +** → **Web Service**
2. 저장소 목록에서 `dlaicourse` 선택 → **Connect**
   - 목록에 없으면 **Configure GitHub App** 을 눌러 이 저장소 접근을 허락합니다.
3. 설정 칸을 아래처럼 채웁니다.

| 칸 | 입력값 |
| --- | --- |
| Name | 원하는 이름 (예: `numbaseball`) → 주소가 `numbaseball.onrender.com` 이 됨 |
| Region | Singapore (한국에서 가장 가까움) |
| Branch | `claude/number-baseball-game-1u4o9o` |
| Root Directory | `number-baseball` |
| Runtime | Node |
| Build Command | 비워 두기 (안 되면 `npm install`) |
| Start Command | `node server.js` |
| Instance Type | 맛보기는 **Free**, 운영은 **Starter** |

4. 맨 아래 **Deploy Web Service** 클릭
5. 로그 창에 `Number Baseball running on ...` 이 보이면 성공. 화면 위쪽의 `https://이름.onrender.com` 을 눌러 접속합니다.

## 3. 계정이 사라지지 않게 하기 (Starter에서만 가능)

1. 서비스 화면 왼쪽 **Settings** → Instance Type 을 **Starter** 로 변경
2. 왼쪽 **Disks** → **Add Disk**
   - Name: `data`
   - Mount Path: `/var/data`
   - Size: `1` GB
3. 왼쪽 **Environment** → **Add Environment Variable**
   - Key: `DATA_DIR` / Value: `/var/data`
4. **Save Changes** → 자동으로 다시 배포됩니다.

이제 서버를 다시 시작해도 계정과 전적이 남습니다.

## 4. 비밀번호 찾기 메일 보내기

메일 발송 서비스 Brevo의 무료 요금제를 쓰는 방법입니다(본인 도메인 없이도 가능).

1. **게임 전용 Gmail을 하나 새로 만듭니다.** 이 주소가 보내는 사람으로 이용자에게 보이므로, 개인 메일을 쓰면 안 됩니다.
2. https://www.brevo.com 가입 → **Senders** 메뉴에서 그 Gmail을 보내는 주소로 등록하고 인증 메일의 링크를 누릅니다.
3. **SMTP & API** → **API Keys** → **Generate a new API key** → 나온 키를 복사합니다(다시 볼 수 없으니 바로 붙여 넣기).
4. Render 서비스의 **Environment** 에 아래 4개를 추가하고 저장합니다.

| Key | Value |
| --- | --- |
| `MAIL_PROVIDER` | `brevo` |
| `MAIL_API_KEY` | 복사한 API 키 |
| `MAIL_FROM` | 게임 전용 Gmail 주소 |
| `SITE_NAME` | 메일에 보일 게임 이름 (예: `숫자야구`) |

5. 게임에서 비밀번호 찾기를 한 번 해 보고 메일이 오는지 확인합니다(스팸함도 확인).

API 키는 비밀번호와 같습니다. 채팅·코드·스크린샷에 올리지 마세요.

## 5. 후원 버튼 켜기

1. GitHub에서 `number-baseball/public/config.js` 파일을 엽니다(브랜치 `claude/number-baseball-game-1u4o9o`).
2. 연필 아이콘(Edit) 클릭 → `donateUrl: ''` 의 따옴표 안에 후원 페이지 주소를 넣습니다.
   예: `donateUrl: 'https://buymeacoffee.com/내필명',`
3. **Commit changes** → Render가 몇 분 안에 자동으로 다시 배포합니다.

## 6. 내 도메인 연결 (선택)

1. 가비아·Cloudflare·Namecheap 등에서 도메인 구입
2. Render 서비스 **Settings** → **Custom Domains** → **Add Custom Domain** → 도메인 입력
3. Render가 알려 주는 DNS 값(CNAME 등)을 도메인 구입처의 DNS 설정에 그대로 입력
4. 몇 분~몇 시간 뒤 연결되고 HTTPS도 자동으로 붙습니다.

## 7. 이후 업데이트

코드가 GitHub 브랜치에 올라가면 Render가 알아서 다시 배포합니다. 배포하는 동안 잠깐 접속이 끊기고, **진행 중인 방은 사라집니다**(계정은 디스크에 있으니 유지). 사람이 적은 시간에 올리세요.

## 문제가 생기면

| 증상 | 확인할 것 |
| --- | --- |
| 배포 실패 | Root Directory 가 `number-baseball` 인지, Branch 가 맞는지 |
| 접속이 1분 가까이 걸림 | Free 요금제가 잠들었다 깨는 중. Starter 로 바꾸면 해결 |
| 재시작하면 계정이 사라짐 | 3단계(디스크 + `DATA_DIR`)를 했는지 |
| 비밀번호 찾기 메일이 안 옴 | Render **Logs** 에 `Mail provider refused` 가 있으면 키·보내는 주소 인증 확인 |
| 온라인 대결 화면이 안 바뀜 | 회사 네트워크가 실시간 연결을 막는 경우가 있음. 휴대폰 데이터로 접속해 비교 |
