# Sound design: 숫자야구: 조용한 한판

Owner of this document: the sound designer. Behaviour rules live in `design/DESIGN_SYSTEM.md` sections 8.3, 9 and 10 and in the README "Sound" section; this file says what the sounds *are*. Android wiring is in [`INTEGRATION.md`](INTEGRATION.md), measurements in [`REPORT.md`](REPORT.md), a listening page in [`audition.html`](audition.html).

## 1. Concept

The game is a quiet duel played on a phone on a bus, in bed or at a table. The sound is the **scoreboard of an empty ballpark at night**: wooden and felt taps, a leather mitt, relay clicks and lamp bulbs, a muted stadium organ, a small PA chime.

- **Quiet.** Every cue is short, dry and sits low in the mix. Nothing is louder than -16 LUFS momentary; keypad and UI sit around -25 to -28 LUFS. Nothing should make a player in a silent room flinch.
- **Crisp.** Each cue starts with a transient (a click, tap or snap) so it lands on the exact frame of the visual, then decays fast. No lead silence (under 5 ms), no tails past the budget.
- **Ballpark-flavoured.** Pitch = mitt pop, solve = bat crack, hint lamps = scoreboard relay + bell (strike) or wooden mallet (ball) or dull thunk (out), win = organ pick-up notes into a major chord.
- **Never cartoonish.** No boing, whistle, sad trombone, buzzer, coin sound, crowd cheer or voice. Losing is wistful, not mocking; errors say "no" politely.
- **Readable without looking.** Strike, ball and out are different in timbre and pitch, not only in loudness: strike is the brightest bell, ball a rounder lower note with no ring, out is dead and low. Consecutive lamps climb a pentatonic step (D major) so three strikes sound like a rising run, not a machine gun.
- **Nothing is sound-only.** Every cue has a visual and, where listed in DESIGN_SYSTEM 9, a haptic. Cues with a haptic share its rhythm (win 0/70/140 ms, draw 0/90 ms, match found 0/120 ms).
- **Licensing is clean.** All audio is synthesized in-house by `tools/generate.py` (numpy/scipy); no samples, no third-party material.

## 2. Cue list

All files are in `out/`, OGG Vorbis, 44.1 kHz; effects mono, music stereo. Loudness is ITU-R BS.1770-4: effects are normalised on the loudest 400 ms window (*M-max*), music on integrated loudness (*I*). Full per-file numbers: `measurements.json`.

| File | 한국어 이름 | Trigger (hook, DESIGN_SYSTEM 8.3) | Duration | Loudness | True peak dBTP |
|---|---|---|---:|---:|---:|
| `sfx_keypad_1.ogg` | 숫자 키 (변형 1) | Hook key_down(digit) on the secret / guess keypad. Round-robin with variants 2 and 3. | 75 ms | M-max -25.1 LUFS | -2.8 |
| `sfx_keypad_2.ogg` | 숫자 키 (변형 2) | Round-robin variant of the digit tap. | 75 ms | M-max -25.0 LUFS | -2.9 |
| `sfx_keypad_3.ogg` | 숫자 키 (변형 3) | Round-robin variant of the digit tap. | 75 ms | M-max -25.0 LUFS | -2.9 |
| `sfx_keypad_delete.ogg` | 지우기 | Hook backspace: the last digit is removed. | 102 ms | M-max -25.0 LUFS | -2.0 |
| `sfx_input_invalid.ogg` | 입력 불가 | Hook key_rejected: repeated digit, slots full, Pitch with empty slots. Plays with the 280 ms shake. | 225 ms | M-max -22.0 LUFS | -5.1 |
| `sfx_memo_mark.ogg` | 메모 표시 | Hook memo_cycle: a memo cell or memo digit changes state (x / o / blank). | 50 ms | M-max -28.4 LUFS | -2.8 |
| `sfx_secret_locked.ogg` | 내 숫자 확정 | Hook secret_locked: '이 숫자로 정하기' (lock in) accepts the hidden number. | 238 ms | M-max -21.0 LUFS | -3.1 |
| `sfx_guess_sent.ogg` | 투구 (추측 보내기) | Hook pitch_submit: the Pitch button sends the guess. | 160 ms | M-max -21.0 LUFS | -2.2 |
| `sfx_hint_strike.ogg` | 스트라이크 램프 | Hook lamp(strike, i): each strike lamp, at 240 + 110 x i ms after round_result; playback rate by lamp index. | 324 ms | M-max -19.9 LUFS | -7.8 |
| `sfx_hint_ball.ogg` | 볼 램프 | Hook lamp(ball, j): each ball lamp, after the strike lamps (240 + 110 x (S + j) ms); rate by lamp index. | 244 ms | M-max -20.0 LUFS | -4.8 |
| `sfx_hint_out.ogg` | 아웃 램프 | Hook lamp(out): the single OUT lamp at 240 ms when nothing matches (0S 0B). | 223 ms | M-max -20.0 LUFS | -2.7 |
| `sfx_opponent_guessed.ogg` | 상대 투구 완료 | Hook opponent_pitched: the opponent's pitch arrives while you are still typing (status lamp ignites). Never during a reveal. | 123 ms | M-max -28.1 LUFS | -4.7 |
| `sfx_solved.ogg` | 전부 스트라이크 | Hook solve: your guess is all strikes; fires at reveal_end (last lamp + 180 ms). The result sting follows 760 + 110 x n ms later. | 1136 ms | M-max -16.0 LUFS | -7.7 |
| `sfx_win.ogg` | 승리 | Hook result_headline(win): headline fades up after the hero lamps (760 + 110 x n ms after solve). | 2314 ms | M-max -16.0 LUFS | -5.4 |
| `sfx_lose.ogg` | 패배 | Hook result_headline(lose): 760 ms after opponent_solved (which itself plays nothing). | 2260 ms | M-max -18.0 LUFS | -8.1 |
| `sfx_draw.ogg` | 무승부 | Hook result_headline(draw): 760 ms after solve when both solved in the same round, or a team tie. | 1864 ms | M-max -17.0 LUFS | -7.3 |
| `sfx_match_found.ogg` | 상대 찾음 | Hook match_found: quick match pairs you or the room fills (three lamps light at t = 0, 900 ms animation). | 1083 ms | M-max -17.0 LUFS | -6.1 |
| `sfx_timer_warning.ogg` | 시간 경고 (10초 남음) | Hook timer_warn: once when 10 s remain in the round (clock turns amber). Silent at 9-6 s. | 310 ms | M-max -24.0 LUFS | -2.5 |
| `sfx_timer_warning_last.ogg` | 시간 경고 (5~1초) | Hook timer_tick(s): once per second at 5, 4, 3, 2, 1 s left (clock turns red at 5 s). | 152 ms | M-max -23.2 LUFS | -5.3 |
| `sfx_time_out.ogg` | 시간 초과 | Hook timer_expired: the round clock reaches 0 without a guess (missed round). | 932 ms | M-max -18.0 LUFS | -7.9 |
| `sfx_chat_message.ogg` | 채팅 수신 | Hook chat_message: a message from another player arrives while the chat sheet is closed. At most one per 2 s, never during a hint reveal, muted with chat. | 205 ms | M-max -24.0 LUFS | -8.9 |
| `sfx_quick_phrase.ogg` | 빠른 문구 | Hook phrase_received: a quick phrase arrives in a match (bubble under the duel strip). At most one per 2 s. | 183 ms | M-max -23.0 LUFS | -5.1 |
| `sfx_ui_navigate.ogg` | 버튼, 화면 이동 | Hook nav_forward: screen push (not into or out of a match; the music crossfade covers those). | 60 ms | M-max -26.0 LUFS | -2.4 |
| `sfx_ui_back.ogg` | 뒤로, 닫기 | Hook nav_back: screen pop (not out of a match). | 65 ms | M-max -26.0 LUFS | -2.6 |
| `sfx_toggle_on.ogg` | 스위치 켬 | Hook toggle(on): a switch turns on. | 78 ms | M-max -25.1 LUFS | -2.3 |
| `sfx_toggle_off.ogg` | 스위치 끔 | Hook toggle(off): a switch turns off. | 83 ms | M-max -25.2 LUFS | -2.8 |
| `sfx_error.ogg` | 오류 | Hook error_shown: an error dialog or error toast appears (network error, room not found, report not sent). | 319 ms | M-max -21.0 LUFS | -7.3 |
| `music_menu.ogg` | 메뉴 음악 | Home, lobby, results, settings, profile (when music is on). 600 ms crossfade to / from the match loop. | 48.000 s loop | I -24.0 LUFS | -12.7 |
| `music_match.ogg` | 경기 음악 | Search, secret entry, match (when music is on). Ducks -6 dB during reveals and the result sequence. | 45.714 s loop | I -26.0 LUFS | -14.7 |

Sequences (the busiest moments mixed as the app plays them, with lamp pitch steps) peak at -2.0 dBTP or lower with effects at 100 %: see `REPORT.md`, "Demo sequences".

### Pitch of the lamp cues

`sfx_hint_strike` and `sfx_hint_ball` are played with a **playback rate by lamp index** (1-based, one lamp per strike or ball, in order):

| Lamp | 1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|
| Strike rate (A5 B5 D6 E6 F#6 A6) | 1.0 | 1.1225 | 1.3348 | 1.4983 | 1.6818 | 2.0 |
| Ball rate (D5 E5 F#5 A5 B5 D6) | 1.0 | 1.1225 | 1.2599 | 1.4983 | 1.6818 | 2.0 |

With "Remove animations" the lamps fire 60 ms apart (DESIGN_SYSTEM 8.4); play them at **0.7 volume** so overlapping bells stay below 0 dBFS.

### The pitch clock

Per DESIGN_SYSTEM 8.3 D and 10 the clock has exactly **one warning cue and one tick cue**:

- `sfx_timer_warning`: played **once** when 10 s remain (two wooden ticks 110 ms apart plus a faint muted E5). Silence at 9, 8, 7 and 6 s.
- `sfx_timer_warning_last`: a brighter single tick with a faint B5, played once for each of 5, 4, 3, 2, 1 s remaining (5 plays).
- `sfx_time_out`: at 0 s. The demo sequence `timer` in `audition.html` plays exactly this pattern.

The three are deliberately different from each other and from the lamps (wood and clock, not bell), so a player recognises "the clock" at once.

## 3. Mixing rules

- Effects: -16 (win, solved) down to -28 LUFS M-max (memo mark, opponent guessed); larger moments are louder, high-frequency repeated cues are quieter. True peak at or below -1 dBTP (the set's highest is -2.0).
- Music: menu loop -24 LUFS integrated, match loop -26 LUFS, both well under the effects so lamp cues stay readable over the match loop (it sits mostly below 1 kHz).
- No lead silence over 5 ms, last 2 ms at or below -50 dBFS (no click at the end), no DC, no clipping.
- Phone-speaker check: no cue loses more than 6 LU through a 300 Hz high-pass.
- File budget 1536 KB for the whole set; actual 1008 KB.
- The generator seeds every cue from its id and encodes with bit-exact flags: `python3 design/sound/tools/generate.py` regenerates `out/` byte for byte (checked: identical md5 for all 29 files on a rerun). Then `tools/measure.py` rewrites `measurements.json`, `REPORT.md` and `spectrograms/`, and `tools/build_audition.py` rebuilds `audition.html`. Needs numpy, scipy, soundfile, matplotlib (for measure.py) and ffmpeg with libvorbis.

## 4. Default settings

From DESIGN_SYSTEM 10 (Settings > 소리와 진동):

| Setting | Default | Behaviour |
|---|---|---|
| 효과음 (effects) | **on** | Plays all effect cues |
| 효과음 볼륨 | 70 / 100 | App gain on top of media volume; effect files are already normalised, so 100 % = file level |
| 무음 모드 따르기 (follow silent mode) | **on** | Ringer silent or vibrate mutes effects and music; haptics still play |
| 배경 음악 (music) | **off** | Menu loop and match loop; opt-in |
| 음악 볼륨 | 40 / 100 | Disabled while music is off |
| 진동 (haptics) | **on** | Independent of sound, section 9; also respects the system touch-feedback setting |

Effects and music have **separate** volumes and switches. Both follow the system media volume (`USAGE_GAME`).

## 5. Audio focus and lifecycle

- Effects need no focus (`USAGE_GAME`, short, may be ducked or dropped).
- Music requests `AUDIOFOCUS_GAIN`. On focus loss (other app, call, alarm): pause music and stop effects. Resume only when the app is visible again **and** focus is regained.
- `onStop` (app in background, screen off): stop everything, release the music player.
- Menu <-> match music crossfades over 600 ms on `nav_forward` into or out of a match; no navigation effect on those transitions.
- Music ducks by 6 dB from `round_result` to `reveal_end` and from `screen_to_result` to `result_ready`, so lamps and the result sting stay clear.
- A new cue of the same kind cuts the previous one; only lamp cues may overlap (110 ms apart). `chat_message` plays at most once per 2 s, never during a hint reveal, and is muted with chat.
- Ringer silent or vibrate and "무음 모드 따르기" on: no effects and no music; nothing is queued for later.

## 6. Ads are always muted

Every ad (banner, interstitial) is muted app-wide regardless of any sound setting (README, "Silent"). The ad SDK is initialised muted (`MobileAds.setAppMuted(true)`), and the app never plays a game cue over an ad. No rewarded ads.

## 7. Open points

- Nobody has listened to the set on a real phone yet (spectrograms and measurements only); the owner's pass with `audition.html` is the first listening review.
- Fixed pitch steps for lamps need the app to use the rate table in section 2 (`INTEGRATION.md` explains).
