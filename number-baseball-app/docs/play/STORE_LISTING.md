# Store listing (10 languages)

Owner: Release lead. Status: updated 2026-11-21 for the owner's decisions ("숫자야구"-style name, 13+ audience, quick match limited to quick phrases).

Limits (Play Console, "Create and set up your app" / store listing help, https://support.google.com/googleplay/android-developer/answer/9859152): app name max 30 characters, short description max 80, full description max 4000. Limits count every character the same way, full-width or half-width. Counts below were computed with a script (Unicode code points, line breaks included in the full description), so they are exact for the text as written. Re-count after any edit.

Metadata rules applied (Metadata policy, https://support.google.com/googleplay/android-developer/answer/9898842; store listing best practices, https://support.google.com/googleplay/android-developer/answer/13393723): no ranking or performance claims ("#1", "best"), no price or promo text, no emoji or repeated special characters, no keyword lists, short description not repeated in the full description, no other brand or trademark used as a keyword.

---

## 1. App name

Owner decision (2026-11-21): a **"숫자야구" (Number Baseball) style name**, localized per language. The earlier brand proposal (Pitchcode) is dropped.

### Conflict check (web search of Google Play, 2026-11-21)

The bare generic names are already used by several Play apps, so we add a distinctive descriptor:

- Taken on Play (avoid as our whole title): "숫자야구게임", "숫자 야구", "숫자 야구 온라인" / "Number BaseBall Online" (two different developers), "Number Baseball" (several), "Number Baseball: Brain Puzzle", "Number Baseball AI Battle", "Number Baseball(Triple Strike)", "인공지능과 숫자야구 게임", "경우의 수 야구". A web-only launch called "Number Strike Baseball: PvP Logic Duel" also exists.
- No Play app found with the descriptors below ("Quiet Duel", "조용한 한판", "Team Duel", "Break", or the localized forms). No app named 数字野球 / 数字棒球 / Béisbol Numérico / Zahlen-Baseball / Числовой бейсбол / Bóng chày số turned up either.
- Generic game names (Number Baseball, Bulls and Cows, 1A2B, Быки и коровы, Picas y Fijas) cannot be owned as trademarks for this game, which is why a descriptor is needed to stand apart. Still avoid "Mastermind", "Superhirn", "Numer0n/ヌメロン", "Hit & Blow" and anything with "Excel". Do not put the words "Baseball Duel" next to each other (a registered mark with that wording turned up in search).
- This was a web search, not a store-wide or trademark search. Before publishing, search Google Play on a phone for the chosen title in each language, and KIPRIS for the Korean title.

### Options per language (characters / 30)

Option 1 in every language is the recommended set ("Number Baseball: a quiet duel"). It matches the product spirit, is the most distinctive, and works as one family across languages.

| Locale | Option 1 (recommended) | Option 2 | Option 3 |
| --- | --- | --- | --- |
| ko | 숫자야구: 조용한 한판 (12)<br>*Number Baseball: a quiet round* | 숫자야구 한판 대결 (10)<br>*Number Baseball, one-round duel* | 숫자야구: 1:1·팀 대결 (14)<br>*Number Baseball: 1v1 and team duels* |
| en-US | Number Baseball: Quiet Duel (27) | Number Baseball: Team Duel (26) | Number Baseball Break (21)<br>*"Break" = a game for your break* |
| ja-JP | 数字野球：静かな対戦 (10)<br>*Number Baseball: quiet match* | 数字野球：数字当てバトル (12)<br>*Number Baseball: number-guessing battle* | 数字野球：1対1・チーム戦 (13)<br>*Number Baseball: 1v1 and team play* |
| zh-CN | 数字棒球：安静对决 (9)<br>*Number Baseball: quiet duel* | 数字棒球：猜数字对战 (10)<br>*Number Baseball: guess-the-number battle* | 数字棒球：组队对战 (9)<br>*Number Baseball: team battle* |
| es-419 | Béisbol Numérico: Duelo Sereno (30)<br>*Number Baseball: serene duel* | Béisbol de Números 1 vs 1 (25)<br>*Baseball of Numbers 1 vs 1* | Béisbol Numérico en Equipo (26)<br>*Number Baseball in teams* |
| fr-FR | Baseball des chiffres : duel (28)<br>*Number Baseball: duel* | Baseball des chiffres 1 vs 1 (28)<br>*Number Baseball 1 vs 1* | Duel de baseball des chiffres (29)<br>*Number Baseball duel* |
| de-DE | Zahlen-Baseball: Leises Duell (29)<br>*Number Baseball: quiet duel* | Zahlen-Baseball: Team-Duell (27)<br>*Number Baseball: team duel* | Zahlen-Baseball 1:1 (19)<br>*Number Baseball 1:1* |
| pt-BR | Beisebol Numérico: Duelo Calmo (30)<br>*Number Baseball: calm duel* | Beisebol dos Números 1x1 (24)<br>*Baseball of Numbers 1x1* | Beisebol Numérico em Equipe (27)<br>*Number Baseball in teams* |
| ru-RU | Числовой бейсбол: тихая дуэль (29)<br>*Number Baseball: quiet duel* | Числовой бейсбол 1 на 1 (23)<br>*Number Baseball 1 on 1* | Числовой бейсбол: команды (25)<br>*Number Baseball: teams* |
| vi | Bóng chày số: Đấu trí yên tĩnh (30)<br>*Number Baseball: quiet battle of wits* | Bóng chày số: Đối kháng 1-1 (27)<br>*Number Baseball: 1-on-1* | Bóng chày số: Đấu đội (21)<br>*Number Baseball: team battle* |

Notes: es-419, pt-BR and vi option 1 are exactly 30 characters; any edit must shorten something. French has no short natural way to say "quiet duel" within 30 characters, so option 1 is "Number Baseball: duel".

### Easy to swap

- The full descriptions below never use the app name (they say "this game" / the game's generic name), so changing the title only changes the **App name** field and the screenshot artwork, not the text.
- In the app, the name lives only in `app_name` string resources per locale (`values/`, `values-ko/`, ... ). The launcher label can be shorter than the store title, for example 숫자야구 / Number Baseball / 数字野球 / 数字棒球 / Béisbol Numérico / Baseball chiffres / Zahlen-Baseball / Beisebol Numérico / Числовой бейсбол / Bóng chày số.
- Web pages and policy documents use the placeholders `[앱 이름]` / `[App name]` filled with the chosen ko/en titles.

### Final titles (recommended set)

| Locale | Title | Chars |
| --- | --- | --- |
| ko | 숫자야구: 조용한 한판 | 12 |
| en-US | Number Baseball: Quiet Duel | 27 |
| ja-JP | 数字野球：静かな対戦 | 10 |
| zh-CN | 数字棒球：安静对决 | 9 |
| es-419 | Béisbol Numérico: Duelo Sereno | 30 |
| fr-FR | Baseball des chiffres : duel | 28 |
| de-DE | Zahlen-Baseball: Leises Duell | 29 |
| pt-BR | Beisebol Numérico: Duelo Calmo | 30 |
| ru-RU | Числовой бейсбол: тихая дуэль | 29 |
| vi | Bóng chày số: Đấu trí yên tĩnh | 30 |

## 2. Locale choices

- **Spanish: es-419 (Latin America).** Most Spanish-speaking Play users are in Latin America (Mexico, Colombia, Argentina and others), the copy uses neutral Latin American Spanish (for example "computadora") that readers in Spain also understand without effort, and Play lists es-419 and es-ES as separate listing languages (Supported languages, https://support.google.com/googleplay/android-developer/table/4419860); adding an es-ES copy later is cheap. I could not confirm whether Play shows an es-419 listing to a device set to es-ES or falls back to the default language / machine translation, so check on a device after publishing.
- In-app Spanish strings in the web prototype use Spain-only words ("ordenador"). The Android engineer should use neutral terms ("la computadora" vs "el ordenador" can be avoided with "la CPU" / "la máquina") or ship `values-es` (neutral) plus `values-b+es+419` if needed.
- **zh-CN**: Google Play is not offered in mainland China; this listing serves Simplified Chinese readers elsewhere (for example Singapore and Malaysia).
- **Default listing language: en-US.** Users whose language has no translation see the default or an automatic machine translation (Translate and localize your app, https://support.google.com/googleplay/android-developer/answer/9844778).
- Distribution is worldwide including the EEA and UK (owner decision 2026-11-21), so fr-FR, de-DE and es-419 all reach their main markets; listing language follows the device language, not the country.

## 3. Copy rules for every language

- The "Contains ads" line is required (honest ads disclosure). The listing does not state a minimum age: the content rating shows on the listing, and the minimum age (14, see `POLICY_REVIEW.md` R19) is enforced by the in-app age screen and the Terms. Do not add "for kids" or child-oriented wording; the target audience is 13+ teens and adults, not children.
- Never write "no ads", "free forever", "safe for kids", rankings or "best".
- Game terms follow the in-app terms from the prototype (`../number-baseball/public/i18n.js`): ja ストライク/ボール/アウト, zh 好球/坏球/出局, es/pt Bola, fr Balle, ru Страйк/Болл.

---

## ko — 한국어 (ko-KR)

**App name** (12/30)
```text
숫자야구: 조용한 한판
```

**Short description** (44/80)
```text
서로 다른 숫자 3~6개, 스트라이크와 볼로 먼저 맞히는 조용한 1:1 숫자야구
```

**Full description** (943/4000)
```text
쉬는 시간 5분, 조용하게 한 판.
상대의 비밀 숫자를 먼저 맞히는 숫자야구로 1:1 대결과 팀 대결을 즐겨 보세요.

■ 규칙은 금방 익힙니다
• 서로 다른 숫자 3~6개로 비밀 숫자를 정합니다. 0이 맨 앞에 와도 됩니다.
• 숫자와 자리가 모두 맞으면 스트라이크, 숫자만 맞고 자리가 다르면 볼, 하나도 없으면 아웃.
• 매 라운드 두 사람이 동시에 추측하므로 선공·후공의 유불리가 없습니다.
• 먼저 전부 스트라이크를 만들면 승리, 같은 라운드에 함께 맞히면 무승부입니다.
• 처음이라면 앱 안의 튜토리얼로 차근차근 배울 수 있습니다.

■ 이렇게 즐겨요
• 컴퓨터 대결: 인터넷 없이 혼자 연습
• 빠른 대결: 자릿수만 고르면 상대를 자동으로 찾아 줍니다
• 비공개 방: 5자리 방 코드로 친구와 1:1 또는 최대 5:5 팀전
• 팀전에서는 팀원이 상대 팀과 1:1로 맞붙고, 더 많이 이긴 팀이 승리합니다
• 방장은 인원이 모일 때까지 컴퓨터와 연습하고, 방이 차면 바로 대결이 시작됩니다

■ 조용하고 가볍게
• 기본은 무음, 한 손으로 조작
• 화면 분할과 팝업 창에서도 플레이
• 잠깐 다른 앱을 보고 와도 짧은 연결 끊김은 이어집니다
• 회원가입 없음: 이메일·비밀번호 없이 닉네임만 정하면 시작

■ 안심하고 대화하세요
• 글자만 쓰는 채팅(이미지·이모지 없음), 욕설 자동 가림
• 메시지·플레이어 신고, 차단, 전체 채팅 끄기
• 빠른 대결에서는 정해진 문구만, 자유 채팅은 방 코드로 모인 친구끼리만

■ 알아 두세요
• 광고가 포함되어 있습니다. 대결 중에는 광고가 나오지 않습니다.
• 온라인 대결과 채팅은 인터넷 연결이 필요합니다.
• 설정에서 언제든 계정을 삭제할 수 있습니다.
• 지원 언어: 한국어, English, 日本語, 简体中文, Español, Français, Deutsch, Português (BR), Русский, Tiếng Việt
```

**Screenshot captions**
1. 비밀 숫자를 먼저 맞히면 승리 (16)
2. 스트라이크·볼로 좁혀 가는 추리 (17)
3. 빠른 대결로 바로 1:1 매칭 (16)
4. 방 코드로 친구와 최대 5:5 팀전 (19)
5. 무음·한 손·화면 분할도 OK (16)
6. 신고·차단·채팅 끄기로 안심 (15)

---

## en-US — English (default)

**App name** (27/30)
```text
Number Baseball: Quiet Duel
```

**Short description** (75/80)
```text
Crack your rival's secret number first. Strikes, balls and quiet 1v1 duels.
```

**Full description** (1733/4000)
```text
A quiet, quick duel for a short break.
Number Baseball, also known as Bulls and Cows, is a number-deduction duel: work out your opponent's secret number before they work out yours.

HOW IT WORKS
• Pick a secret number of 3–6 different digits (a leading 0 is fine).
• Right digit in the right place = Strike. Right digit, wrong place = Ball. Nothing matches = Out.
• Both players guess at the same time every round, so nobody gets a first-move edge.
• Hit all strikes first to win. Both in the same round? It's a draw.
• New to the game? A short in-app tutorial teaches you the rules.

WAYS TO PLAY
• Vs Computer: practise offline, no connection needed
• Quick Match: choose a digit count and get paired automatically
• Private Rooms: share a 5-character code to play a friend 1v1 or team up to 5 vs 5
• In team play, teammates face the other side in 1v1 duels; the team with more wins takes the match
• Waiting for a room to fill? Warm up against the computer; the real match starts automatically

QUIET BY DESIGN
• Silent by default and playable one-handed
• Works in split-screen and pop-up windows
• Switch apps for a moment: short disconnects reconnect
• No sign-up: no email, no password, just pick a nickname

CHAT WITH CARE
• Text-only chat (no images or emoji) with automatic profanity masking
• Report messages or players, block players, or turn chat off entirely
• Quick Match chat uses preset phrases only; free text is for private rooms with friends

GOOD TO KNOW
• Contains ads. Ads never appear during a match.
• Online play and chat need an internet connection.
• You can delete your account at any time in Settings.
• Languages: English, 한국어, 日本語, 简体中文, Español, Français, Deutsch, Português (BR), Русский, Tiếng Việt
```

**Screenshot captions**
1. Crack the secret number first (29)
2. Narrow it down with strikes and balls (37)
3. Quick Match finds you a rival (29)
4. Room codes for 1v1 or up to 5v5 (31)
5. Silent, one-handed, split-screen ready (38)
6. Report, block or mute chat anytime (34)

---

## ja-JP — 日本語

**App name** (10/30)
```text
数字野球：静かな対戦
```

**Short description** (40/80)
```text
相手の秘密の数字を先に当てよう。ストライクとボールで推理する、静かな1対1の対戦
```

**Full description** (874/4000)
```text
ちょっとした休憩に、静かに一局。
数字野球は、相手の秘密の数字を先に当てる数字推理の対戦ゲームです。韓国で親しまれている遊びを、オンラインで気軽に楽しめます。

■ ルールはすぐに覚えられます
・異なる数字3〜6個で秘密の数字を決めます（先頭が0でもOK）。
・数字も位置も合っていればストライク、数字だけ合っていればボール、ひとつも合わなければアウト。
・毎ラウンド二人が同時に推理するので、先攻・後攻の有利不利がありません。
・先に全部ストライクにした方が勝ち。同じラウンドで同時に当てたら引き分けです。
・はじめての方も、アプリ内のチュートリアルで遊び方を学べます。

■ いろいろな遊び方
・コンピューター対戦：オフラインでひとり練習
・クイック対戦：桁数を選ぶだけで自動マッチング
・プライベートルーム：5文字のルームコードで友だちと1対1、または最大5対5のチーム戦
・チーム戦では味方がそれぞれ相手チームと1対1で対戦し、勝ち数の多いチームの勝利
・ルームが埋まるまではコンピューターと練習。そろったら自動で本番スタート

■ 静かに、気軽に
・初期設定は無音、片手で操作できます
・画面分割やポップアップウィンドウにも対応
・少しほかのアプリを見ても、短い切断なら復帰できます
・会員登録なし：メールもパスワードも不要、ニックネームを決めるだけ

■ 安心のチャット
・文字だけのチャット（画像・絵文字なし）、不適切な言葉は自動で伏せ字に
・メッセージやプレイヤーの通報、ブロック、チャットの全オフ
・クイック対戦では定型フレーズのみ。自由なチャットはルームコードで集まった友だち同士だけ

■ ご注意
・広告が含まれます。対戦中に広告は表示されません。
・オンライン対戦とチャットにはインターネット接続が必要です。
・アカウントは設定からいつでも削除できます。
・対応言語：日本語、한국어、English、简体中文、Español、Français、Deutsch、Português（BR）、Русский、Tiếng Việt
```

**Screenshot captions**
1. 相手の秘密の数字を先に当てよう (15)
2. ストライクとボールで絞り込む (14)
3. クイック対戦で自動マッチング (14)
4. ルームコードで最大5対5のチーム戦 (17)
5. 無音・片手・画面分割OK (12)
6. 通報・ブロック・チャットオフで安心 (17)

---

## zh-CN — 简体中文

**App name** (9/30)
```text
数字棒球：安静对决
```

**Short description** (36/80)
```text
抢先猜出对手的秘密数字！靠好球和坏球的提示推理，安静地来一局一对一对决。
```

**Full description** (676/4000)
```text
短暂休息时，安静地来一局。
数字棒球是一款数字推理对战游戏，也叫“猜数字（1A2B）”：抢在对手之前猜出他的秘密数字。

玩法说明
• 用 3～6 个互不相同的数字设定秘密数字（可以以 0 开头）。
• 数字和位置都对 = 好球；数字对但位置不对 = 坏球；一个都不对 = 出局。
• 每回合双方同时猜，没有先手后手之分。
• 先猜出全部好球的一方获胜；同一回合双方都猜中则为平局。
• 第一次玩？应用内的简短教程会带你学会规则。

多种模式
• 人机对战：离线也能练习
• 快速匹配：选好位数，自动为你找到对手
• 私人房间：分享 5 位房间代码，和好友一对一，或组队最多 5 对 5
• 组队模式中，每位队员与对方队员一对一对决，赢得场次多的队伍获胜
• 等待房间满员时可以先和电脑练习，人齐后正式对局自动开始

安静、轻松
• 默认静音，单手即可操作
• 支持分屏和悬浮窗
• 临时切换到其他应用？短暂断线可以自动重连
• 无需注册：不用邮箱和密码，起个昵称就能开始

安心聊天
• 纯文字聊天（不支持图片和表情），不雅词语自动屏蔽
• 可举报消息或玩家、屏蔽玩家，或一键关闭全部聊天
• 快速匹配中只能发送预设短语，自由聊天仅限用房间代码加入的好友之间

温馨提示
• 本应用含广告。对局过程中绝不显示广告。
• 在线对战和聊天需要联网。
• 可随时在“设置”中删除账号。
• 支持语言：简体中文、English、한국어、日本語、Español、Français、Deutsch、Português（BR）、Русский、Tiếng Việt
```

**Screenshot captions**
1. 抢先猜出对手的秘密数字 (11)
2. 好球、坏球，一步步缩小范围 (13)
3. 快速匹配，自动找到对手 (11)
4. 房间代码组队，最多 5 对 5 (15)
5. 静音、单手、分屏都能玩 (11)
6. 举报、屏蔽、关闭聊天，安心对战 (15)

---

## es-419 — Español (Latinoamérica)

**App name** (30/30)
```text
Béisbol Numérico: Duelo Sereno
```

**Short description** (79/80)
```text
Descifra primero el número secreto de tu rival. Strikes, bolas y duelos 1 vs 1.
```

**Full description** (1929/4000)
```text
Un duelo rápido y tranquilo para tu descanso.
El Béisbol Numérico, también conocido como Picas y Fijas o Toros y Vacas, es un duelo de deducción: descifra el número secreto de tu rival antes de que descifre el tuyo.

CÓMO SE JUEGA
• Elige un número secreto de 3 a 6 cifras distintas (puede empezar con 0).
• Cifra correcta en su lugar = Strike. Cifra correcta en otro lugar = Bola. Ninguna coincide = Out.
• En cada ronda ambos adivinan al mismo tiempo, así que nadie tiene ventaja por empezar.
• Gana quien logre todos strikes primero. Si ambos aciertan en la misma ronda, es empate.
• ¿Primera vez? Un tutorial breve dentro de la app te enseña las reglas.

MODOS DE JUEGO
• Contra la computadora: practica sin conexión
• Partida rápida: elige cuántas cifras y te emparejamos automáticamente
• Salas privadas: comparte un código de 5 caracteres para jugar 1 vs 1 con un amigo o por equipos de hasta 5 vs 5
• Por equipos, cada jugador se enfrenta 1 vs 1 a un rival; gana el equipo con más victorias
• Mientras se llena la sala, calienta contra la computadora; la partida real empieza sola

SILENCIOSO POR DISEÑO
• Sin sonido de forma predeterminada y se juega con una mano
• Funciona en pantalla dividida y en ventanas emergentes
• Si cambias de app un momento, las desconexiones breves se recuperan
• Sin registro: ni correo ni contraseña, solo elige un apodo

CHAT CON CUIDADO
• Chat solo de texto (sin imágenes ni emojis) con groserías ocultas automáticamente
• Reporta mensajes o jugadores, bloquea jugadores o desactiva todo el chat
• En la partida rápida solo hay frases predefinidas; el chat libre es para salas privadas con amigos

TEN EN CUENTA
• Contiene anuncios. Nunca aparecen durante una partida.
• El juego en línea y el chat requieren conexión a internet.
• Puedes eliminar tu cuenta cuando quieras desde Ajustes.
• Idiomas: Español, English, 한국어, 日本語, 简体中文, Français, Deutsch, Português (BR), Русский, Tiếng Việt
```

**Screenshot captions**
1. Descifra primero el número secreto (34)
2. Acércate con strikes y bolas (28)
3. Partida rápida con rival automático (35)
4. Código de sala: 1 vs 1 o hasta 5 vs 5 (37)
5. Silencio, una mano y pantalla dividida (38)
6. Reporta, bloquea o silencia el chat (35)

---

## fr-FR — Français

**App name** (28/30)
```text
Baseball des chiffres : duel
```

**Short description** (80/80)
```text
Trouvez avant l'autre son nombre secret. Strikes, balles et duels en 1 contre 1.
```

**Full description** (2095/4000)
```text
Un duel calme et rapide pour votre pause.
Le Baseball des chiffres, aussi appelé Bulls and Cows, est un duel de déduction : trouvez le nombre secret de votre adversaire avant qu'il ne trouve le vôtre.

LES RÈGLES
• Choisissez un nombre secret de 3 à 6 chiffres tous différents (il peut commencer par 0).
• Bon chiffre à la bonne place = Strike. Bon chiffre mal placé = Balle. Aucun chiffre commun = Out.
• À chaque manche, les deux joueurs proposent en même temps : personne n'a l'avantage de commencer.
• Le premier à n'obtenir que des strikes gagne. Si les deux trouvent à la même manche, c'est un match nul.
• Vous découvrez le jeu ? Un court tutoriel intégré vous explique tout.

MODES DE JEU
• Contre l'ordinateur : entraînez-vous hors ligne
• Partie rapide : choisissez le nombre de chiffres, l'adversaire est trouvé automatiquement
• Salons privés : partagez un code de 5 caractères pour affronter un ami en 1 contre 1 ou jouer en équipes jusqu'à 5 contre 5
• En équipe, chaque joueur affronte un adversaire en duel ; l'équipe qui remporte le plus de duels gagne
• En attendant que le salon se remplisse, échauffez-vous contre l'ordinateur ; la vraie partie démarre automatiquement

DISCRET PAR NATURE
• Silencieux par défaut, jouable d'une seule main
• Fonctionne en écran partagé et en fenêtre flottante
• Vous changez d'appli un instant ? Les courtes déconnexions se rattrapent
• Sans inscription : ni e-mail ni mot de passe, juste un pseudo

UNE DISCUSSION SEREINE
• Discussion uniquement textuelle (ni images ni emojis), grossièretés masquées automatiquement
• Signalez un message ou un joueur, bloquez un joueur ou coupez toute la discussion
• En partie rapide, uniquement des phrases prédéfinies ; la discussion libre est réservée aux salons privés entre amis

BON À SAVOIR
• Contient des publicités. Aucune publicité pendant une partie.
• Le jeu en ligne et la discussion nécessitent une connexion Internet.
• Vous pouvez supprimer votre compte à tout moment dans les paramètres.
• Langues : français, English, 한국어, 日本語, 简体中文, Español, Deutsch, Português (BR), Русский, Tiếng Việt
```

**Screenshot captions**
1. Trouvez le nombre secret en premier (35)
2. Affinez avec strikes et balles (30)
3. Partie rapide, adversaire trouvé seul (37)
4. Codes de salon : 1 contre 1 à 5 contre 5 (40)
5. Silencieux, à une main, en écran partagé (40)
6. Signalez, bloquez, coupez la discussion (39)

---

## de-DE — Deutsch

**App name** (29/30)
```text
Zahlen-Baseball: Leises Duell
```

**Short description** (79/80)
```text
Knacke zuerst die Geheimzahl deines Gegners. Strikes, Balls, ruhige 1:1-Duelle.
```

**Full description** (1936/4000)
```text
Ein ruhiges, schnelles Duell für die kurze Pause.
Zahlen-Baseball, auch bekannt als Bulls and Cows, ist ein Logik-Duell: Knacke die Geheimzahl deines Gegners, bevor er deine knackt.

SO GEHT'S
• Wähle eine Geheimzahl aus 3 bis 6 verschiedenen Ziffern (eine 0 vorne ist erlaubt).
• Richtige Ziffer an richtiger Stelle = Strike. Richtige Ziffer an falscher Stelle = Ball. Keine Ziffer stimmt = Out.
• In jeder Runde raten beide gleichzeitig – niemand hat einen Vorteil durch den ersten Zug.
• Wer zuerst nur Strikes hat, gewinnt. Lösen beide in derselben Runde, endet es unentschieden.
• Neu dabei? Ein kurzes Tutorial in der App erklärt die Regeln.

SPIELMODI
• Gegen den Computer: offline üben
• Schnelles Spiel: Ziffernanzahl wählen, ein Gegner wird automatisch gefunden
• Private Räume: Teile einen 5-stelligen Code und spiele 1:1 gegen Freunde oder im Team bis 5 gegen 5
• Im Teamspiel tritt jede Person in einem 1:1-Duell gegen das andere Team an; das Team mit mehr Siegen gewinnt
• Bis der Raum voll ist, übst du gegen den Computer; das echte Spiel startet automatisch

LEISE GEDACHT
• Standardmäßig stumm und mit einer Hand spielbar
• Funktioniert im geteilten Bildschirm und im Pop-up-Fenster
• Kurz die App gewechselt? Kurze Verbindungsabbrüche werden überbrückt
• Keine Registrierung: keine E-Mail, kein Passwort – nur ein Spitzname

ENTSPANNT CHATTEN
• Reiner Text-Chat (keine Bilder, keine Emojis), Schimpfwörter werden automatisch ausgeblendet
• Nachrichten oder Spieler melden, Spieler blockieren oder den Chat ganz ausschalten
• Im schnellen Spiel nur vorgegebene Sätze; freier Chat nur in privaten Räumen mit Freunden

GUT ZU WISSEN
• Enthält Werbung. Während eines Spiels wird nie Werbung gezeigt.
• Online-Spiele und Chat brauchen eine Internetverbindung.
• Du kannst dein Konto jederzeit in den Einstellungen löschen.
• Sprachen: Deutsch, English, 한국어, 日本語, 简体中文, Español, Français, Português (BR), Русский, Tiếng Việt
```

**Screenshot captions**
1. Knacke zuerst die Geheimzahl (28)
2. Mit Strikes und Balls eingrenzen (32)
3. Schnelles Spiel mit Gegnersuche (31)
4. Raumcode: 1:1 oder bis 5 gegen 5 (32)
5. Lautlos, einhändig, mit Splitscreen (35)
6. Melden, blockieren oder Chat aus (32)

---

## pt-BR — Português (Brasil)

**App name** (30/30)
```text
Beisebol Numérico: Duelo Calmo
```

**Short description** (78/80)
```text
Descubra primeiro o número secreto do adversário. Strikes, bolas e duelos 1x1.
```

**Full description** (1916/4000)
```text
Um duelo rápido e tranquilo para a sua pausa.
O Beisebol Numérico, também conhecido como Bulls and Cows, é um duelo de dedução: descubra o número secreto do adversário antes que ele descubra o seu.

COMO FUNCIONA
• Escolha um número secreto com 3 a 6 dígitos diferentes (pode começar com 0).
• Dígito certo na posição certa = Strike. Dígito certo na posição errada = Bola. Nenhum dígito confere = Out.
• A cada rodada, os dois dão o palpite ao mesmo tempo, então ninguém sai na frente por começar.
• Vence quem fizer só strikes primeiro. Se os dois acertarem na mesma rodada, dá empate.
• Primeira vez? Um tutorial rápido no app ensina as regras.

MODOS DE JOGO
• Contra o computador: treine offline
• Partida rápida: escolha a quantidade de dígitos e o adversário é encontrado automaticamente
• Salas privadas: compartilhe um código de 5 caracteres para jogar 1x1 com um amigo ou em equipes de até 5x5
• No modo equipe, cada jogador enfrenta um adversário em duelo 1x1; vence a equipe com mais vitórias
• Enquanto a sala enche, aqueça contra o computador; a partida de verdade começa sozinha

SILENCIOSO DE PROPÓSITO
• Sem som por padrão e jogável com uma mão
• Funciona em tela dividida e em janela flutuante
• Trocou de app por um instante? Quedas rápidas de conexão são recuperadas
• Sem cadastro: sem e-mail e sem senha, é só escolher um apelido

CHAT TRANQUILO
• Chat só de texto (sem imagens nem emojis), com palavrões ocultados automaticamente
• Denuncie mensagens ou jogadores, bloqueie jogadores ou desative o chat inteiro
• Na partida rápida, só frases prontas; o chat livre fica para salas privadas com amigos

BOM SABER
• Contém anúncios. Eles nunca aparecem durante uma partida.
• Partidas online e chat exigem conexão com a internet.
• Você pode excluir sua conta a qualquer momento nas Configurações.
• Idiomas: Português (BR), English, 한국어, 日本語, 简体中文, Español, Français, Deutsch, Русский, Tiếng Việt
```

**Screenshot captions**
1. Descubra primeiro o número secreto (34)
2. Chegue perto com strikes e bolas (32)
3. Partida rápida com adversário automático (40)
4. Código de sala: 1x1 ou até 5x5 (30)
5. Silencioso, uma mão e tela dividida (35)
6. Denuncie, bloqueie ou desligue o chat (37)

---

## ru-RU — Русский

**App name** (29/30)
```text
Числовой бейсбол: тихая дуэль
```

**Short description** (75/80)
```text
Разгадайте секретное число соперника первым. Страйки, боллы и дуэли 1 на 1.
```

**Full description** (1818/4000)
```text
Тихая быстрая дуэль для короткого перерыва.
Числовой бейсбол, он же «Быки и коровы», — логическая дуэль: разгадайте секретное число соперника раньше, чем он разгадает ваше.

ПРАВИЛА
• Загадайте число из 3–6 разных цифр (можно начинать с 0).
• Цифра на своём месте — страйк. Цифра есть, но на другом месте — болл. Ни одного совпадения — аут.
• В каждом раунде оба игрока ходят одновременно, поэтому у первого хода нет преимущества.
• Побеждает тот, кто первым соберёт все страйки. Если оба угадали в одном раунде — ничья.
• Впервые играете? Короткое обучение в приложении объяснит правила.

РЕЖИМЫ
• Против компьютера: тренируйтесь без интернета
• Быстрая игра: выберите количество цифр — соперник найдётся автоматически
• Приватные комнаты: поделитесь кодом из 5 символов и сыграйте с другом 1 на 1 или командами до 5 на 5
• В командной игре каждый проводит дуэль 1 на 1 с игроком другой команды; побеждает команда, у которой больше побед
• Пока комната заполняется, разомнитесь с компьютером — настоящая игра начнётся сама

ТИХО И УДОБНО
• По умолчанию без звука, играть можно одной рукой
• Работает в режиме разделённого экрана и во всплывающем окне
• Ненадолго переключились на другое приложение? Короткие обрывы связи восстанавливаются
• Без регистрации: ни почты, ни пароля — только имя игрока

СПОКОЙНЫЙ ЧАТ
• Только текст (без картинок и эмодзи), нецензурные слова скрываются автоматически
• Жалобы на сообщения и игроков, блокировка игроков, полное отключение чата
• В быстрой игре — только готовые фразы; свободный чат — в приватных комнатах с друзьями

ВАЖНО
• Содержит рекламу. Во время игры реклама не показывается.
• Для онлайн-игр и чата нужен интернет.
• Аккаунт можно удалить в любой момент в настройках.
• Языки: русский, English, 한국어, 日本語, 简体中文, Español, Français, Deutsch, Português (BR), Tiếng Việt
```

**Screenshot captions**
1. Разгадайте секретное число первым (33)
2. Сужайте круг страйками и боллами (32)
3. Быстрая игра: соперник найдётся сам (35)
4. Код комнаты: 1 на 1 или до 5 на 5 (33)
5. Без звука, одной рукой, на пол-экрана (37)
6. Жалоба, блокировка, отключение чата (35)

---

## vi — Tiếng Việt

**App name** (30/30)
```text
Bóng chày số: Đấu trí yên tĩnh
```

**Short description** (77/80)
```text
Đoán trúng số bí mật của đối thủ trước. Strike, Ball và đấu 1 đối 1 yên tĩnh.
```

**Full description** (1783/4000)
```text
Một trận đấu nhanh, yên tĩnh cho giờ nghỉ ngắn.
Bóng chày số, còn gọi là Bulls and Cows, là trò đấu trí suy luận con số: hãy đoán ra số bí mật của đối thủ trước khi họ đoán ra số của bạn.

CÁCH CHƠI
• Chọn một số bí mật gồm 3–6 chữ số khác nhau (có thể bắt đầu bằng 0).
• Đúng số, đúng vị trí = Strike. Đúng số, sai vị trí = Ball. Không trúng số nào = Out.
• Mỗi lượt cả hai cùng đoán một lúc, nên không ai có lợi thế đi trước.
• Ai đạt toàn Strike trước sẽ thắng. Nếu cả hai cùng đoán trúng trong một lượt thì hòa.
• Mới chơi lần đầu? Phần hướng dẫn ngắn trong ứng dụng sẽ giúp bạn nắm luật.

CHẾ ĐỘ CHƠI
• Đấu với máy: luyện tập khi không có mạng
• Ghép trận nhanh: chọn số chữ số, hệ thống tự tìm đối thủ
• Phòng riêng: chia sẻ mã 5 ký tự để đấu 1 đối 1 với bạn bè hoặc đấu đội tối đa 5 đối 5
• Ở chế độ đấu đội, mỗi người đấu 1 đối 1 với một thành viên đội kia; đội thắng nhiều trận hơn sẽ thắng
• Trong lúc chờ phòng đủ người, hãy khởi động với máy; trận thật sẽ tự bắt đầu

YÊN TĨNH TỪ THIẾT KẾ
• Mặc định tắt âm thanh, chơi được bằng một tay
• Hỗ trợ chia đôi màn hình và cửa sổ nổi
• Chuyển sang ứng dụng khác một chút? Mất kết nối ngắn vẫn vào lại được
• Không cần đăng ký: không email, không mật khẩu, chỉ cần đặt biệt danh

TRÒ CHUYỆN AN TÂM
• Chat chỉ có chữ (không hình ảnh, không emoji), từ ngữ thô tục được tự động che
• Báo cáo tin nhắn hoặc người chơi, chặn người chơi, hoặc tắt toàn bộ chat
• Ghép trận nhanh chỉ dùng câu soạn sẵn; chat tự do chỉ trong phòng riêng với bạn bè

LƯU Ý
• Có quảng cáo. Quảng cáo không bao giờ xuất hiện trong trận đấu.
• Chơi trực tuyến và trò chuyện cần kết nối internet.
• Bạn có thể xóa tài khoản bất cứ lúc nào trong phần Cài đặt.
• Ngôn ngữ: Tiếng Việt, English, 한국어, 日本語, 简体中文, Español, Français, Deutsch, Português (BR), Русский
```

**Screenshot captions**
1. Đoán trúng số bí mật trước (26)
2. Thu hẹp đáp án bằng Strike và Ball (34)
3. Ghép trận nhanh, tự tìm đối thủ (31)
4. Mã phòng: 1 đối 1 hoặc tới 5 đối 5 (34)
5. Không tiếng, một tay, chia đôi màn hình (39)
6. Báo cáo, chặn hoặc tắt chat (27)

---

## 4. Screenshot plan (for the design lead)

The six captions above map to these six screens, in this order. Captions should stay short (they are overlay text; under about 40 characters reads well on a phone thumbnail).

| # | Screen | Notes |
| --- | --- | --- |
| 1 | A duel in progress near the end: guess history with S/B/OUT results, one guess away from winning | Hero shot. No ad on screen (ads never appear during a match, and screenshots must match the app). |
| 2 | Close-up of the result chips (e.g. 1S 1B, OUT) with the digit memo pad | Teaches the rule visually. |
| 3 | Quick match: digit-count picker and "finding opponent" state | |
| 4 | Private room lobby with a 5-character room code and two teams | Use fake nicknames only (e.g. `Mint#2048`), never testers' real names. |
| 5 | Split-screen or pop-up window with the game on one side | Shows "quiet, one-handed, split-screen". |
| 6 | Chat with masked word `***`, long-press menu showing Report / Block, and the "Mute all chat" toggle | Must show safety tools, not a toxic conversation. |

Sizes and formats are in `POLICY_CHECKLIST.md` section 14. Make one set per language, or at least localize the overlay captions; the game UI in each screenshot should be in the same language as the caption.
