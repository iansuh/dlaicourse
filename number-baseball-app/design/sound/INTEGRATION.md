# Sound integration (Android)

For the Android engineer. Behaviour is fixed in `design/DESIGN_SYSTEM.md` section 10 (and 8.3, 9); the sound concept and defaults are in [`SOUND_DESIGN.md`](SOUND_DESIGN.md). This file is how to wire the files in `out/`. Last verified against `android/app/src/main/kotlin/app/numbaseball/audio/` (read only); see "Check against the current code" at the end.

## 1. Files

Copy all 29 files from `design/sound/out/` to `android/app/src/main/res/raw/` **unchanged**. The names already satisfy the resource rules (lowercase letters, digits, underscore; no hyphen, no uppercase), so `sfx_hint_strike.ogg` is `R.raw.sfx_hint_strike`. Do not re-encode (the music seam was verified on these exact files). Total 1008 KB.

## 2. File to event map

| Hook (DESIGN_SYSTEM 8.3) | File(s) | Engine | Notes |
|---|---|---|---|
| `key_down(digit)` | `sfx_keypad_1`, `_2`, `_3` | SoundPool | round-robin, never the same variant twice in a row; random rate +/-3 %, volume 0.85-1.0 |
| `backspace` | `sfx_keypad_delete` | SoundPool | rate +/-2 % |
| `key_rejected` | `sfx_input_invalid` | SoundPool | with the 280 ms shake |
| `pitch_submit` | `sfx_guess_sent` | SoundPool | rate +/-2 % |
| `secret_locked` | `sfx_secret_locked` | SoundPool | |
| `opponent_pitched` | `sfx_opponent_guessed` | SoundPool | no haptic; never during a reveal |
| `lamp(strike, i)` | `sfx_hint_strike` | SoundPool | rate by lamp index, table below; may overlap |
| `lamp(ball, j)` | `sfx_hint_ball` | SoundPool | rate by lamp index; may overlap |
| `lamp(out)` | `sfx_hint_out` | SoundPool | |
| `solve` | `sfx_solved` | SoundPool | `opponent_solved` plays nothing |
| `result_headline(win / lose / draw)` | `sfx_win` / `sfx_lose` / `sfx_draw` | SoundPool | `result_lamp(i)` plays nothing |
| `match_found` | `sfx_match_found` | SoundPool | |
| `timer_warn` (10 s) | `sfx_timer_warning` | SoundPool | once, see section 5 |
| `timer_tick(5..1)` | `sfx_timer_warning_last` | SoundPool | five plays, see section 5 |
| `timer_expired` | `sfx_time_out` | SoundPool | |
| `chat_message` | `sfx_chat_message` | SoundPool | at most 1 per 2 s, not during a reveal, muted with chat |
| `phrase_received` | `sfx_quick_phrase` | SoundPool | same limits |
| `memo_cycle` | `sfx_memo_mark` | SoundPool | rate +/-4 % |
| `toggle(on / off)` | `sfx_toggle_on` / `sfx_toggle_off` | SoundPool | |
| `nav_forward` / `nav_back` | `sfx_ui_navigate` / `sfx_ui_back` | SoundPool | not on transitions into or out of a match (the music crossfade covers them) |
| `error_shown` | `sfx_error` | SoundPool | |
| menu music (Home, lobby, results, settings, profile) | `music_menu` | player | loop |
| match music (search, secret entry, match) | `music_match` | player | loop |

SoundPool priorities (higher wins when streams run out) and per-cue stream caps are in `tools/cues.py` (`priority`, `streams`): result stings and match found 5, solved / timer / time out 4, lamps 3, input rejection / pitch / secret / error 2, keypad / chat / opponent 1, navigation / toggles / memo 0 to 1.

### Lamp playback rates

Use `SoundPool.play(..., rate)` with these values (1-based lamp index within its kind, so the first ball lamp is rate 1.0 even after two strikes). They are pentatonic steps in D major, so a run of lamps climbs instead of repeating:

| Lamp | 1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|
| Strike | 1.0 | 1.1225 | 1.3348 | 1.4983 | 1.6818 | 2.0 |
| Ball | 1.0 | 1.1225 | 1.2599 | 1.4983 | 1.6818 | 2.0 |

(`cues.STRIKE_RATES` / `BALL_RATES`.) SoundPool accepts 0.5 to 2.0, which covers 6 digits. Reduced motion (lamps 60 ms apart): play lamps at volume **0.7**.

## 3. SoundPool for effects, a player for music

**Effects: one `SoundPool`.** All 27 files together are about 158 KB OGG (a few MB decoded), so load them all at app start, `SoundPool.load(context, R.raw.x, 1)`, and wait for `OnLoadCompleteListener` before the first play (cues played earlier are dropped, not queued).

- `SoundPool.Builder().setMaxStreams(6)` (or a few more), `AudioAttributes(USAGE_GAME, CONTENT_TYPE_SONIFICATION)`.
- `play(soundId, vol, vol, priority, loop = 0, rate)`. Volume = effects volume slider (0..1) x the cue's own volume (random 0.85-1.0 only for keypad / memo, see `vol_var` in `cues.py`). Do not add more loudness compensation: the files are normalised (section 6).
- A new cue of the same kind **cuts** the previous instance (`stop(streamId)`); lamp cues are the only cues that may overlap.
- SoundPool is for clips under about 5 s, which is every effect here (longest `sfx_win`, 2.3 s).

**Music: a separate player**, one at a time, `MediaPlayer` (or Media3 `ExoPlayer`) on the raw resource, `isLooping = true`, `AudioAttributes(USAGE_GAME, CONTENT_TYPE_MUSIC)`. Music files are stereo, 368 KB and 482 KB, so stream them, do not put them in SoundPool.

### Loop points

Each music file is **exactly one loop; the whole file is the loop**: start = sample 0, end = last sample.

| File | Length | Samples at 44.1 kHz |
|---|---:|---:|
| `music_menu` | 48.000 s | 2,116,800 |
| `music_match` | 45.714 s | 2,016,000 |

No intro, no outro, no padding to trim. The Ogg container stores the exact sample count, so the decoded length equals the source. The join was checked on the decoded OGG (no click at the seam, level within 3 LU across the join: `REPORT.md`, "Loop seam").

Open: the seam is verified on the decoded file, not on a device. `MediaPlayer.isLooping` is normally gapless for Ogg on API 26+, but some devices add a gap. If a gap is heard, switch to `ExoPlayer` with `REPEAT_MODE_ONE` (or chain two `MediaPlayer`s with `setNextMediaPlayer`). Please listen to one full loop change on a real phone.

Music crossfade between loops: 600 ms (two players, fade one out and the other in; `music_match` starts from its beginning). Duck -6 dB (volume x 0.5) from `round_result` to `reveal_end` and from `screen_to_result` to `result_ready`, ramp 100 ms.

## 4. Audio focus, ducking, lifecycle

- **Music** requests focus on start: `AudioFocusRequest.Builder(AUDIOFOCUS_GAIN)` with the music attributes, `setOnAudioFocusChangeListener`. On `AUDIOFOCUS_LOSS`, `LOSS_TRANSIENT` (calls, alarms) or `LOSS_TRANSIENT_CAN_DUCK`: **pause music and stop effects** (a quiet game does not fight another app; do not duck-and-continue). On `AUDIOFOCUS_GAIN` again: resume music, but only if the app is in the foreground and music is enabled.
- **Effects** do not request focus. They play only while the app is in the foreground and focus is not lost.
- `onStop` / app to background / screen off: stop all effects, pause music, abandon focus. `onStart`: re-request focus, resume if enabled.
- `AudioManager.mode != MODE_NORMAL` (in a call or VoIP call): play nothing.
- If the user turns music off: stop and abandon focus.
- Do not use `setWillPauseWhenDucked`/auto-ducking: this app pauses instead.

### Ads

Ads are **always muted**: `MobileAds.setAppMuted(true)` before `MobileAds.initialize`, and never `setAppVolume` above 0. Game audio is not played during an ad; if an ad takes the screen, treat it like the background for sound (stop effects; music pauses via the focus rules).

## 5. The pitch clock: one 10 s cue and five ticks

DESIGN_SYSTEM 8.3 D. The clock text updates on whole seconds. On each **whole-second change of remaining time**, compare `remainingSeconds` (ceil of remaining ms / 1000) with the last value you handled:

```kotlin
// called when the displayed whole second changes; each value is handled once per round
fun onClockSecond(s: Int) {
    when {
        s == 10 -> audio.play(SoundCue.TIMER_WARN)            // sfx_timer_warning, once
        s in 1..5 -> audio.play(SoundCue.TIMER_TICK)          // sfx_timer_warning_last, 5, 4, 3, 2, 1
        s == 0 -> audio.play(SoundCue.TIMER_EXPIRED)          // sfx_time_out
    }                                                         // 9, 8, 7, 6: silence
}
```

Rules:

- Exactly **one** `sfx_timer_warning` at 10 s: not at 9 to 6 s. (`audition.html` "Pitch clock" sequence plays exactly this: warning, 4 s of silence, five ticks, time out.)
- Exactly **five** `sfx_timer_warning_last`, one per second at 5, 4, 3, 2, 1. The same file for all five, rate 1.0, no variation.
- A round shorter than 10 s (never today: shortest is 30 s) plays no warning; a clock that **resumes** below a threshold (app returns from background, reconnect) only plays the cue for the current second if it has not played this round, and never replays missed ones.
- Guard with a per-round set of handled seconds so a recomposition or two close frames never double-play. Reset on a new round.
- The tick cues are **not stackable**; each cuts the previous one. Haptics: `timer_10s` TICK 0.5 at 10 s, `timer_last5` TICK 0.35 at 5 to 1 (DESIGN_SYSTEM 9).
- Take the second from the server-synchronised clock that drives the visual, not from a separate timer, so sound, pulse and text stay together. Off-screen (background): nothing plays.
- Timer cues obey the same gate as other effects (effects on, silent mode, focus).
- Relaxed 10-minute rounds use the same thresholds.

## 6. Loudness and volume

Effects are normalised to **M-max** (loudest 400 ms) targets from -16 to -28 LUFS by importance; music to -24 and -26 LUFS integrated (`REPORT.md`). Consequences for code:

- Apply **only** the user's slider (0..1, linear) to effects and music. Default 0.7 and 0.4. Do not normalise again, do not boost.
- Linear gain is fine; if you want a perceptual slider use `volume = slider^2`.
- Several overlapping cues add up; the demo mixes in `REPORT.md` (win, round with lamps, timer) stay at -2.0 dBTP or lower at slider 1.0, using the SoundPool mix and the rates above.

## 7. Silent-mode detection

"무음 모드 따르기" (default on): mute effects **and** music when the ringer is silent or vibrate. Haptics are not affected.

```kotlin
val am = context.getSystemService(AudioManager::class.java)
val silent = am.ringerMode != AudioManager.RINGER_MODE_NORMAL   // RINGER_MODE_SILENT or RINGER_MODE_VIBRATE
```

- Read `ringerMode` when a cue is about to play (cheap), and also register a receiver for `AudioManager.RINGER_MODE_CHANGED_ACTION` so a running music loop is paused/resumed when the switch is flipped.
- Do Not Disturb that silences media is handled by the system (media stream muted); no extra work.
- It is only about the ringer switch. Media volume 0 is naturally silent; no special case.
- When muted by silent mode, **do not queue** cues to play later and do not show an error.

## 8. Settings keys

Effects on, effects volume 0.7, follow silent mode on, music off, music volume 0.4, haptics on. Music loop choice follows the screen, not a setting.

## 9. Regenerating the files

`python3 design/sound/tools/generate.py` rewrites `out/` deterministically (same bytes every run; needs numpy, scipy, soundfile, ffmpeg with libvorbis). Then `tools/measure.py` and `tools/build_audition.py`. After a change, copy `out/*.ogg` to `res/raw/` again. Do not edit `res/raw/` by hand.

## 10. Check against the current code (read only, not edited)

`android/.../audio/GameAudio.kt` and `AndroidGameAudio.kt` already cover the structure above (SoundPool, `USAGE_GAME`, one looping music player, focus, ringer gate, timer cues). Differences to look at:

1. `lampRate(index)` returns `1 + 0.06 * index` up to 1.5. The designed steps are the table in section 2 (up to 2.0, pentatonic, separate strike and ball). With the linear steps three lamps are 1.0 / 1.06 / 1.12, which sounds like detuning, not a rising run.
2. `SoundCue` priorities differ in places (e.g. `MEMO_MARK` 1, `TOGGLE_*` 1; designed 0). Harmless.
3. Reduced motion lamp volume 0.7 and the keypad/memo random volume and rate variation (see `cues.py`) are not in the enum.
