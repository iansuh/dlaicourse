#!/usr/bin/env python3
"""Build design/sound/audition.html: one self-contained page with every sound embedded (base64).

    python3 design/sound/tools/build_audition.py [--fragment PATH]

Run after generate.py and measure.py (it reads measurements.json for the durations and loudness it
shows). The OGG files are embedded as they are. MP3 copies (made from the OGG with ffmpeg/LAME) are
embedded as a fallback for browsers that cannot decode Ogg Vorbis (older iPhone Safari); the page
says when it uses them. --fragment also writes the page without the <html>/<head>/<body> wrapper
(for hosts that add their own skeleton).
"""
from __future__ import annotations

import base64
import html
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cues import (CUES, GROUPS, SEQUENCES, STRIKE_RATES, BALL_RATES, LISTEN_KO,  # noqa: E402
                  REDUCED_MOTION_LAMP_GAIN)

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "out"
PAGE_LIMIT_KB = 3072

LAMP_COLOR = {  # semantic colour of each cue's indicator lamp (app tokens)
    "sfx_hint_strike": "strike", "sfx_solved": "strike", "sfx_win": "strike", "sfx_timer_warning": "strike",
    "sfx_hint_ball": "ball", "sfx_match_found": "ball", "sfx_opponent_guessed": "ball",
    "sfx_hint_out": "out", "sfx_lose": "out", "sfx_input_invalid": "out", "sfx_error": "out",
    "sfx_time_out": "out", "sfx_timer_warning_last": "out",
}


def mp3_fallback(cue_id: str, music: bool, tmp: Path) -> bytes:
    dst = tmp / f"{cue_id}.mp3"
    cmd = ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(OUT / f"{cue_id}.ogg"),
           "-map_metadata", "-1", "-c:a", "libmp3lame", "-b:a", "64k" if music else "96k",
           "-ac", "2" if music else "1", str(dst)]
    subprocess.run(cmd, check=True)
    return dst.read_bytes()


def chip_label(t, cid, rate):
    if cid.startswith("sfx_keypad_") and cid != "sfx_keypad_delete":
        return "키"
    if cid == "sfx_hint_strike":
        return f"S{min(range(6), key=lambda i: abs(STRIKE_RATES[i] - rate)) + 1}"
    if cid == "sfx_hint_ball":
        return f"B{min(range(6), key=lambda i: abs(BALL_RATES[i] - rate)) + 1}"
    if cid == "sfx_timer_warning":
        return "10초"
    if cid == "sfx_timer_warning_last":
        return f"{10 - int(round(t))}"
    return {"sfx_keypad_delete": "지움", "sfx_guess_sent": "투구", "sfx_hint_out": "OUT",
            "sfx_opponent_guessed": "상대", "sfx_solved": "정답", "sfx_win": "승리", "sfx_lose": "패배",
            "sfx_draw": "무승부", "sfx_time_out": "0초"}.get(cid, cid)


def build():
    meas = json.load(open(ROOT / "measurements.json"))["files"]
    sounds, ogg_total, mp3_total = {}, 0, 0
    with tempfile.TemporaryDirectory() as td:
        for c in CUES:
            music = c["group"] == "music"
            ogg = (OUT / f"{c['id']}.ogg").read_bytes()
            mp3 = mp3_fallback(c["id"], music, Path(td))
            ogg_total += len(ogg)
            mp3_total += len(mp3)
            sounds[c["id"]] = {"ogg": base64.b64encode(ogg).decode(), "mp3": base64.b64encode(mp3).decode()}

    cues = []
    for c in CUES:
        m = meas[c["id"]]
        cues.append(dict(id=c["id"], group=c["group"], ko=c["ko"], en=c["en"], trigger=c["trigger"],
                         listen=c["listen"], listen_ko=LISTEN_KO[c["id"]], character=c["character"],
                         pitch=c["pitch"], ms=round(m["aud_ms"]), lufs=round(m["measured"], 1),
                         metric=m["metric"], kb=round(m["size_kb"], 1), lamp=LAMP_COLOR.get(c["id"], "chalk"),
                         music=c["group"] == "music", samples=m["samples"]))
    seqs = []
    for key, s in SEQUENCES.items():
        ev = [dict(t=t, id=cid, rate=rate, gain=g, label=chip_label(t, cid, rate),
                   lamp=LAMP_COLOR.get(cid, "chalk")) for t, cid, rate, g in s["events"]]
        seqs.append(dict(key=key, ko=s["ko"], en=s["en"], events=ev))

    total_kb = sum(meas[c["id"]]["size_kb"] for c in CUES)
    data = dict(cues=cues, groups=[dict(id=g, ko=k, en=e) for g, k, e in GROUPS], seqs=seqs,
                strikeRates=STRIKE_RATES, ballRates=BALL_RATES, reducedGain=REDUCED_MOTION_LAMP_GAIN,
                totalKb=round(total_kb), nFiles=len(CUES))
    body = TEMPLATE.replace("__DATA__", json.dumps(data, ensure_ascii=False).replace("</", "<\\/"))
    body = body.replace("__SOUNDS__", json.dumps(sounds))
    body = body.replace("__NFILES__", str(len(CUES))).replace("__TOTALKB__", f"{total_kb:.0f}")
    return body, ogg_total, mp3_total


HEAD = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
"""

TEMPLATE = r"""<title>Number Baseball Sound Audition</title>
<meta name="claude:viewport-fit-sides" content="cover">
<style>
/* Layout: one scoreboard column (max 640px); sections are bands of the night-game board, rows are plates. */
:root {
  color-scheme: dark light;
  --press: #D3D8CC;
  --bg: #0C1210; --surface1: #131A17; --surface2: #1A231F; --surface3: #222D28; --surface4: #2B3732;
  --line: #2A3530; --outline: #6C7C74;
  --chalk: #ECEFE6; --text2: #A5B1AA; --text3: #909D96;
  --strike: #F5B83D; --ball: #45C98B; --out: #FF6359; --lampOff: #2E3A35; --lampRing: #3B4943;
  --strikeGlow: rgba(245,184,61,.45); --ballGlow: rgba(69,201,139,.45); --outGlow: rgba(255,99,89,.45); --chalkGlow: rgba(236,239,230,.35);
  --font-num: "Barlow Condensed", "Roboto Condensed", "Arial Narrow", sans-serif;
  --font-sans: system-ui, -apple-system, "Apple SD Gothic Neo", "Noto Sans KR", "Malgun Gothic", Roboto, sans-serif;
}
/* Light theme (the game itself is dark only; the page follows the phone setting so it is readable in daylight). */
@media (prefers-color-scheme: light) {
  :root {
    --press: #2A3530;
    --bg: #F4F6F0; --surface1: #FFFFFF; --surface2: #E9EDE5; --surface3: #DCE2D7; --surface4: #CDD5C8;
    --line: #D3DACD; --outline: #6C7C74;
    --chalk: #121A16; --text2: #3F4C45; --text3: #55635B;
    --strike: #C98A00; --ball: #1E9B63; --out: #D93A30; --lampOff: #D3DACD; --lampRing: #B5BFB3;
    --strikeGlow: rgba(201,138,0,.35); --ballGlow: rgba(30,155,99,.35); --outGlow: rgba(217,58,48,.35); --chalkGlow: rgba(18,26,22,.25);
  }
}
* { box-sizing: border-box; }
html, body { background: var(--bg); color: var(--chalk); }
body { margin: 0; font: 15px/1.5 var(--font-sans); -webkit-text-size-adjust: 100%; }
.wrap { max-width: 640px; margin: 0 auto; padding-left: max(16px, env(safe-area-inset-left, 0px));
  padding-right: max(16px, env(safe-area-inset-right, 0px)); padding-block: 24px 48px; display: grid; gap: 28px; }
h1, h2, h3 { margin: 0; text-wrap: balance; }
p { margin: 0; }
.eyebrow { font: 600 13px/1 var(--font-num); letter-spacing: .12em; text-transform: uppercase; color: var(--text3);
  display: flex; align-items: center; gap: 8px; }
.eyebrow .lamps { display: inline-flex; gap: 4px; }
header { display: grid; gap: 12px; }
header h1 { font: 700 30px/1.15 var(--font-sans); }
header .en { color: var(--text2); font-size: 15px; }
.facts { display: flex; flex-wrap: wrap; gap: 6px 14px; font: 500 15px/1.3 var(--font-num); color: var(--text2);
  letter-spacing: .02em; font-variant-numeric: tabular-nums; }
.tips { background: var(--surface1); border: 1px solid var(--line); border-radius: 8px; padding: 12px 14px;
  display: grid; gap: 6px; font-size: 14px; color: var(--text2); }
.tips b { color: var(--chalk); }
.controls { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 16px; }
.status { font: 600 14px/1 var(--font-num); letter-spacing: .06em; text-transform: uppercase; color: var(--text2);
  display: inline-flex; align-items: center; gap: 8px; }
.switch { display: inline-flex; align-items: center; gap: 10px; cursor: pointer; font-size: 14px; color: var(--text2);
  min-height: 44px; }
.switch input { appearance: none; -webkit-appearance: none; width: 44px; height: 26px; border-radius: 13px;
  background: var(--surface3); border: 1px solid var(--outline); position: relative; margin: 0; cursor: pointer; flex: none; }
.switch input::after { content: ""; position: absolute; top: 3px; left: 3px; width: 18px; height: 18px; border-radius: 50%;
  background: var(--text2); transition: transform .15s, background .15s; }
.switch input:checked { background: var(--chalk); border-color: var(--chalk); }
.switch input:checked::after { transform: translateX(18px); background: var(--bg); }
.switch input:focus-visible { outline: 2px solid var(--chalk); outline-offset: 2px; }

section { display: grid; gap: 12px; }
.sec-head { display: grid; gap: 4px; border-top: 1px solid var(--line); padding-top: 16px; }
.sec-head h2 { font: 700 20px/1.3 var(--font-sans); }
.sec-head h2 .en { font: 600 15px/1 var(--font-num); letter-spacing: .1em; text-transform: uppercase; color: var(--text3); margin-left: 8px; }
.sec-head p { color: var(--text2); font-size: 14px; }

.lamp { width: 10px; height: 10px; border-radius: 50%; background: var(--lampOff); box-shadow: inset 0 0 0 1px var(--lampRing);
  flex: none; transition: background .06s, box-shadow .18s; }
.lamp.on.strike { background: var(--strike); box-shadow: 0 0 12px 2px var(--strikeGlow); }
.lamp.on.ball { background: var(--ball); box-shadow: 0 0 12px 2px var(--ballGlow); }
.lamp.on.out { background: var(--out); box-shadow: 0 0 12px 2px var(--outGlow); }
.lamp.on.chalk { background: var(--chalk); box-shadow: 0 0 10px 1px var(--chalkGlow); }
.lamp.fixed.strike { background: var(--strike); } .lamp.fixed.ball { background: var(--ball); } .lamp.fixed.out { background: var(--out); }

button { font: inherit; color: inherit; }
.play { width: 48px; height: 48px; border-radius: 50%; border: 1px solid var(--outline); background: var(--surface2);
  display: grid; place-items: center; cursor: pointer; flex: none; position: relative; }
.play:active { background: var(--surface3); }
.play:focus-visible, .key:focus-visible, .ladder button:focus-visible, .btn:focus-visible { outline: 2px solid var(--chalk); outline-offset: 2px; }
.play svg { width: 18px; height: 18px; fill: var(--chalk); margin-left: 2px; }
.play .lamp { position: absolute; top: -2px; right: -2px; width: 12px; height: 12px; }

/* sequences */
.seq { background: var(--surface1); border: 1px solid var(--line); border-radius: 8px; padding: 12px; display: grid;
  grid-template-columns: 48px minmax(0, 1fr); gap: 10px 12px; align-items: start; }
.seq h3 { font: 700 16px/1.35 var(--font-sans); }
.seq .en { font-size: 13px; color: var(--text3); }
.chips { grid-column: 1 / -1; display: flex; flex-wrap: wrap; gap: 6px; }
.chip { display: inline-flex; align-items: center; gap: 6px; padding: 4px 8px 4px 6px; border-radius: 4px; background: var(--surface2);
  font: 600 14px/1 var(--font-num); letter-spacing: .04em; color: var(--text2); font-variant-numeric: tabular-nums; }
.chip .lamp { width: 8px; height: 8px; }
.chip .t { color: var(--text3); font-weight: 500; }
.chip.on { color: var(--chalk); background: var(--surface3); }

/* keypad */
.pad { background: var(--surface1); border: 1px solid var(--line); border-radius: 8px; padding: 12px; display: grid; gap: 10px; }
.pad .readout { font: 500 15px/1.3 var(--font-num); color: var(--text2); letter-spacing: .02em; font-variant-numeric: tabular-nums; min-height: 1.3em; }
.slots { display: flex; gap: 8px; justify-content: center; }
.slot { width: 52px; height: 60px; border-radius: 8px; background: var(--surface2); display: grid; place-items: center;
  font: 600 36px/1 var(--font-num); color: var(--chalk); border: 1px solid transparent; }
.slot.empty { background: transparent; border-color: var(--outline); }
.keys { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 8px; }
.key { height: 52px; border-radius: 8px; border: 0; background: var(--surface2); font: 500 28px/1 var(--font-num);
  color: var(--chalk); cursor: pointer; touch-action: manipulation; }
.key:active { background: var(--surface3); }
.key.wide { grid-column: span 4; font: 700 16px/1 var(--font-sans); background: var(--chalk); color: var(--bg); }
.key.wide:active { background: var(--press); }
.key.del { font: 600 15px/1 var(--font-num); letter-spacing: .06em; }
.ladder { display: grid; gap: 8px; }
.ladder .row { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.ladder .row span { font: 600 13px/1 var(--font-num); letter-spacing: .1em; color: var(--text3); width: 52px; text-transform: uppercase; }
.ladder button { min-width: 44px; height: 40px; padding: 0 8px; border-radius: 4px; border: 1px solid var(--line);
  background: var(--surface2); font: 600 16px/1 var(--font-num); letter-spacing: .04em; cursor: pointer;
  display: inline-flex; align-items: center; gap: 6px; }
.ladder button .lamp { width: 8px; height: 8px; }
.btn { min-height: 44px; padding: 0 14px; border-radius: 8px; border: 1px solid var(--outline); background: transparent;
  font: 700 14px/1 var(--font-sans); color: var(--chalk); cursor: pointer; }

/* cue list */
.group { display: grid; gap: 0; background: var(--surface1); border: 1px solid var(--line); border-radius: 8px; }
.group h3 { font: 600 14px/1 var(--font-num); letter-spacing: .12em; text-transform: uppercase; color: var(--text3);
  padding: 14px 12px 6px; }
.group h3 b { font: 700 15px/1 var(--font-sans); letter-spacing: 0; text-transform: none; color: var(--text2); margin-right: 8px; }
.cue { display: grid; grid-template-columns: 48px minmax(0, 1fr); gap: 4px 12px; padding: 12px; border-top: 1px solid var(--line); }
.cue:first-of-type { border-top: 0; }
.cue .name { font: 700 16px/1.35 var(--font-sans); }
.cue .name .en { font-weight: 400; color: var(--text2); font-size: 14px; margin-left: 6px; }
.cue .file { font: 500 14px/1.3 var(--font-num); color: var(--text3); letter-spacing: .02em; overflow-wrap: anywhere; }
.cue .meta { font: 500 14px/1.3 var(--font-num); color: var(--text2); letter-spacing: .02em; font-variant-numeric: tabular-nums; }
.cue .body { grid-column: 2; display: grid; gap: 4px; font-size: 14px; color: var(--text2); min-width: 0; }
.cue .body .ko { color: var(--chalk); }
.cue .body .lbl { font: 600 12px/1 var(--font-num); letter-spacing: .12em; text-transform: uppercase; color: var(--text3); margin-right: 6px; }
.cue .extra { grid-column: 2; display: flex; flex-wrap: wrap; gap: 6px; margin-top: 4px; }

/* music */
.track { background: var(--surface1); border: 1px solid var(--line); border-radius: 8px; padding: 12px; display: grid; gap: 10px; }
.track .top { display: grid; grid-template-columns: 48px minmax(0, 1fr); gap: 12px; align-items: center; }
.track h3 { font: 700 16px/1.35 var(--font-sans); }
.track .meta { font: 500 14px/1.3 var(--font-num); color: var(--text2); letter-spacing: .02em; font-variant-numeric: tabular-nums; }
.bar { position: relative; height: 6px; border-radius: 3px; background: var(--surface3); overflow: hidden; }
.bar i { position: absolute; inset: 0 auto 0 0; width: 0; background: var(--chalk); }
.bar .seam { position: absolute; right: 0; top: 0; bottom: 0; width: 2px; background: var(--strike); }
.track .row { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.pos { font: 600 15px/1 var(--font-num); color: var(--text2); font-variant-numeric: tabular-nums; letter-spacing: .02em; }
.stop svg { margin-left: 0; }
footer { color: var(--text3); font-size: 13px; display: grid; gap: 6px; border-top: 1px solid var(--line); padding-top: 16px; }
footer b { color: var(--text2); }
@media (prefers-reduced-motion: reduce) { .lamp, .switch input::after { transition: none; } }
</style>

<div class="wrap">
  <header>
    <div class="eyebrow"><span class="lamps"><span class="lamp fixed strike"></span><span class="lamp fixed ball"></span><span class="lamp fixed out"></span></span>Night Game · Sound audition</div>
    <h1>숫자야구 사운드 오디션</h1>
    <p class="en">Number Baseball sound set: every effect and both music loops, synthesized in-house.</p>
    <div class="facts"><span>__NFILES__ FILES</span><span>__TOTALKB__ KB</span><span>OGG VORBIS · 44.1 KHZ</span><span>KEY OF D</span></div>
    <div class="tips">
      <p><b>들어 보는 방법.</b> 미디어 볼륨을 50~70 %로 두고, 폰 스피커와 이어폰 둘 다로 들어 보세요. 첫 버튼을 누르면 소리가 준비돼요.</p>
      <p>아이폰은 옆면 무음 스위치가 켜져 있으면 소리가 나지 않아요.</p>
      <p><b>피드백.</b> 파일 이름(예: <span style="font-family:var(--font-num)">sfx_hint_ball</span>)과 느낌을 적어 주세요: 너무 큼 / 너무 작음 / 너무 밝음 / 너무 김 / 다른 소리와 헷갈림 / 게임과 안 어울림.</p>
    </div>
    <div class="controls">
      <span class="status" id="status"><span class="lamp" id="statusLamp"></span><span id="statusText">탭하면 준비</span></span>
      <label class="switch" for="appLevels"><input type="checkbox" id="appLevels"> 앱 기본 볼륨 (효과음 70 %, 음악 40 %)</label>
    </div>
  </header>

  <section aria-labelledby="h-seq">
    <div class="sec-head">
      <h2 id="h-seq">시퀀스 데모<span class="en">Sequences</span></h2>
      <p>실제 게임 타이밍(디자인 시스템 8.3)대로 이어서 재생해요. 칩이 소리와 함께 켜져요. S와 B 옆 숫자는 램프 순서이고, 램프마다 한 음씩 올라가요.</p>
    </div>
    <div id="seqs" style="display:grid;gap:10px"></div>
  </section>

  <section aria-labelledby="h-pad">
    <div class="sec-head">
      <h2 id="h-pad">직접 눌러 보기<span class="en">Try it</span></h2>
      <p>키패드는 앱과 같은 규칙으로 울려요: 세 변형을 번갈아 쓰고(같은 변형 연속 없음), 피치 ±3 %와 볼륨을 조금씩 무작위로 바꿔요. 변화를 끄면 같은 소리만 반복돼요. 빠르게 눌러서 비교해 보세요.</p>
    </div>
    <div class="pad">
      <div class="slots" id="slots"></div>
      <div class="keys" id="keys"></div>
      <div class="controls">
        <label class="switch" for="vary"><input type="checkbox" id="vary" checked> 피치와 변형 바꾸기 (앱 기본)</label>
      </div>
      <div class="readout" id="readout">&nbsp;</div>
    </div>
    <div class="pad ladder" id="ladder">
      <div class="row" id="ladderS"><span>Strike</span></div>
      <div class="row" id="ladderB"><span>Ball</span></div>
      <div class="row" id="ladderO"><span>Out</span></div>
      <p style="color:var(--text2);font-size:14px">램프 순서별 재생 속도(피치): S1 A5 → S6 A6, B1 D5 → B6 D6. 모두 D장조 5음계 안에 있어요.</p>
    </div>
  </section>

  <section aria-labelledby="h-cues">
    <div class="sec-head">
      <h2 id="h-cues">모든 효과음<span class="en">Every cue</span></h2>
      <p>숫자는 실제로 재생되는 파일을 잰 값이에요: 들리는 길이, 라우드니스(효과음은 400 ms 최대값, 음악은 통합), 파일 크기.</p>
    </div>
    <div id="groups" style="display:grid;gap:12px"></div>
  </section>

  <section aria-labelledby="h-music">
    <div class="sec-head">
      <h2 id="h-music">배경 음악<span class="en">Music loops</span></h2>
      <p>앱에서는 기본으로 꺼져 있어요. 반복 재생으로 이음매를 들어 보세요. '이음매 듣기'는 끝나기 4초 전부터 재생해서 곧바로 처음으로 넘어가요.</p>
    </div>
    <div id="music" style="display:grid;gap:10px"></div>
  </section>

  <footer>
    <p><b>만든 방법.</b> 모든 소리는 Python으로 직접 합성했어요(샘플이나 외부 사운드 팩 없음). 측정값과 검사 결과는 REPORT.md, 연동 규칙은 INTEGRATION.md에 있어요.</p>
    <p id="fallbackNote" hidden>이 브라우저는 OGG Vorbis를 재생하지 못해서 같은 믹스의 MP3 사본으로 재생 중이에요. 음악 이음매는 MP3에서는 정확하지 않을 수 있어요.</p>
  </footer>
</div>

<script id="data" type="application/json">__DATA__</script>
<script id="sounds" type="application/json">__SOUNDS__</script>
<script>
(function () {
  "use strict";
  var D = JSON.parse(document.getElementById("data").textContent);
  var S = JSON.parse(document.getElementById("sounds").textContent);
  var CUE = {}; D.cues.forEach(function (c) { CUE[c.id] = c; });
  var ctx = null, buffers = {}, pending = {}, useMp3 = false, ready = false;
  var statusText = document.getElementById("statusText"), statusLamp = document.getElementById("statusLamp");
  var appLevels = document.getElementById("appLevels");

  function setStatus(text, cls) {
    statusText.textContent = text;
    statusLamp.className = "lamp" + (cls ? " on " + cls : "");
  }
  function b64(s) {
    var bin = atob(s), u = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i);
    return u.buffer;
  }
  function decode(ab) {
    return new Promise(function (res, rej) {
      try { var p = ctx.decodeAudioData(ab, res, rej); if (p && p.then) p.then(res, rej); } catch (e) { rej(e); }
    });
  }
  function ensureCtx() {
    if (!ctx) {
      var AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) { setStatus("이 브라우저는 소리를 재생할 수 없어요", "out"); return Promise.reject(new Error("no WebAudio")); }
      ctx = new AC();
      setStatus("준비 중", "chalk");
    }
    return ctx.state === "suspended" ? ctx.resume() : Promise.resolve();
  }
  function getBuffer(id) {
    if (buffers[id]) return Promise.resolve(buffers[id]);
    if (pending[id]) return pending[id];
    pending[id] = decode(b64(S[id][useMp3 ? "mp3" : "ogg"])).catch(function () {
      if (useMp3) throw new Error("decode failed: " + id);
      useMp3 = true;
      document.getElementById("fallbackNote").hidden = false;
      return decode(b64(S[id].mp3));
    }).then(function (b) { buffers[id] = b; return b; });
    return pending[id];
  }
  function warmUp() {
    return ensureCtx().then(function () {
      if (ready) return;
      // decode one effect first to learn whether OGG works, then the rest of the effects
      return getBuffer("sfx_keypad_1").then(function () {
        var ids = D.cues.filter(function (c) { return !c.music; }).map(function (c) { return c.id; });
        return Promise.all(ids.map(getBuffer));
      }).then(function () {
        ready = true;
        setStatus(useMp3 ? "준비됨 · MP3 사본" : "준비됨 · OGG", "ball");
      });
    }).catch(function (e) { setStatus("재생 오류: " + e.message, "out"); throw e; });
  }
  function level(id) {
    if (!appLevels.checked) return 1;
    return CUE[id] && CUE[id].music ? 0.4 : 0.7;
  }
  function play(id, opts) {
    opts = opts || {};
    var buf = buffers[id]; if (!buf) return null;
    var src = ctx.createBufferSource(), g = ctx.createGain();
    src.buffer = buf;
    src.playbackRate.value = opts.rate || 1;
    g.gain.value = (opts.gain == null ? 1 : opts.gain) * level(id);
    src.connect(g); g.connect(ctx.destination);
    if (opts.loop) src.loop = true;
    src.start(opts.when || 0, opts.offset || 0);
    src._gain = g;
    return src;
  }
  function flash(el, ms) {
    el.classList.add("on");
    clearTimeout(el._t);
    el._t = setTimeout(function () { el.classList.remove("on"); }, Math.max(120, ms));
  }
  function playIcon() { return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4.5v15l12-7.5z"/></svg>'; }
  function stopIcon() { return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6h12v12H6z"/></svg>'; }
  function esc(s) { var d = document.createElement("div"); d.textContent = s; return d.innerHTML; }

  // ------------------------------------------------------------------ sequences
  var seqHost = document.getElementById("seqs"), seqTimers = [];
  D.seqs.forEach(function (s) {
    var card = document.createElement("div"); card.className = "seq";
    var chips = s.events.map(function (e, i) {
      return '<span class="chip" data-i="' + i + '"><span class="lamp ' + e.lamp + '"></span>' + esc(e.label) +
        ' <span class="t">' + e.t.toFixed(2) + '</span></span>';
    }).join("");
    card.innerHTML = '<button class="play" aria-label="' + esc(s.ko) + ' 재생">' + playIcon() + '<span class="lamp chalk"></span></button>' +
      '<div><h3>' + esc(s.ko) + '</h3><p class="en">' + esc(s.en) + '</p></div><div class="chips">' + chips + '</div>';
    var btn = card.querySelector(".play"), chipEls = card.querySelectorAll(".chip");
    btn.addEventListener("click", function () {
      warmUp().then(function () {
        seqTimers.forEach(clearTimeout); seqTimers = [];
        document.querySelectorAll(".chip.on, .chip .lamp.on").forEach(function (el) { el.classList.remove("on"); });
        var t0 = ctx.currentTime + 0.08, end = 0;
        s.events.forEach(function (e, i) {
          play(e.id, { when: t0 + e.t, rate: e.rate, gain: e.gain });
          var dur = buffers[e.id].duration / e.rate;
          end = Math.max(end, e.t + dur);
          seqTimers.push(setTimeout(function () {
            var c = chipEls[i]; c.classList.add("on"); c.querySelector(".lamp").classList.add("on");
          }, (e.t + 0.08) * 1000));
        });
        flash(btn.querySelector(".lamp"), (end + 0.1) * 1000);
        seqTimers.push(setTimeout(function () {
          chipEls.forEach(function (c) { c.classList.remove("on"); c.querySelector(".lamp").classList.remove("on"); });
        }, (end + 0.6) * 1000));
      });
    });
    seqHost.appendChild(card);
  });

  // ------------------------------------------------------------------ keypad
  var slotsEl = document.getElementById("slots"), keysEl = document.getElementById("keys"), readout = document.getElementById("readout");
  var vary = document.getElementById("vary"), entry = [], lastVariant = 0;
  function drawSlots() {
    slotsEl.innerHTML = "";
    for (var i = 0; i < 4; i++) {
      var d = document.createElement("div"); d.className = "slot" + (entry[i] == null ? " empty" : "");
      d.textContent = entry[i] == null ? "" : entry[i]; slotsEl.appendChild(d);
    }
  }
  function keyTap(digit) {
    warmUp().then(function () {
      if (entry.length >= 4 || entry.indexOf(digit) >= 0) {
        play("sfx_input_invalid");
        readout.textContent = "sfx_input_invalid · " + (entry.length >= 4 ? "칸이 다 찼어요" : "같은 숫자는 한 번만");
        return;
      }
      var v = 1, rate = 1, gain = 1;
      if (vary.checked) {
        var choices = [1, 2, 3].filter(function (x) { return x !== lastVariant; });
        v = choices[Math.floor(Math.random() * choices.length)];
        rate = 1 + (Math.random() * 2 - 1) * 0.03;
        gain = 0.85 + Math.random() * 0.15;
      }
      lastVariant = v;
      play("sfx_keypad_" + v, { rate: rate, gain: gain });
      entry.push(digit); drawSlots();
      readout.textContent = "sfx_keypad_" + v + " · rate " + rate.toFixed(3) + " · gain " + gain.toFixed(2);
    });
  }
  [1, 2, 3, 4, 5, 6, 7, 8, 9, 0].forEach(function (d) {
    var b = document.createElement("button"); b.className = "key"; b.textContent = d; b.setAttribute("aria-label", "숫자 " + d);
    b.addEventListener("click", function () { keyTap(d); }); keysEl.appendChild(b);
  });
  var del = document.createElement("button"); del.className = "key del"; del.textContent = "DEL"; del.setAttribute("aria-label", "지우기");
  del.addEventListener("click", function () {
    warmUp().then(function () {
      if (!entry.length) { play("sfx_input_invalid"); readout.textContent = "sfx_input_invalid · 지울 숫자가 없어요"; return; }
      play("sfx_keypad_delete", { rate: vary.checked ? 1 + (Math.random() * 2 - 1) * 0.02 : 1 });
      entry.pop(); drawSlots(); readout.textContent = "sfx_keypad_delete";
    });
  });
  keysEl.appendChild(del);
  var pitch = document.createElement("button"); pitch.className = "key wide"; pitch.textContent = "던지기 (Pitch)";
  pitch.addEventListener("click", function () {
    warmUp().then(function () {
      if (entry.length < 4) { play("sfx_input_invalid"); readout.textContent = "sfx_input_invalid · 네 칸을 채워 주세요"; return; }
      play("sfx_guess_sent"); readout.textContent = "sfx_guess_sent";
      entry = []; setTimeout(drawSlots, 300);
    });
  });
  keysEl.appendChild(pitch);
  vary.addEventListener("change", function () { warmUp().then(function () { play(vary.checked ? "sfx_toggle_on" : "sfx_toggle_off"); }); });
  appLevels.addEventListener("change", function () {
    warmUp().then(function () {
      play(appLevels.checked ? "sfx_toggle_on" : "sfx_toggle_off");
      if (musicNow) musicNow.src._gain.gain.value = level(musicNow.id);
    });
  });
  drawSlots();

  // lamp ladder
  function ladderButton(host, label, id, rate, lamp) {
    var b = document.createElement("button");
    b.innerHTML = '<span class="lamp ' + lamp + '"></span>' + label;
    b.setAttribute("aria-label", label + " 램프");
    b.addEventListener("click", function () {
      warmUp().then(function () { play(id, { rate: rate }); flash(b.querySelector(".lamp"), 250); });
    });
    host.appendChild(b);
  }
  D.strikeRates.forEach(function (r, i) { ladderButton(document.getElementById("ladderS"), "S" + (i + 1), "sfx_hint_strike", r, "strike"); });
  D.ballRates.forEach(function (r, i) { ladderButton(document.getElementById("ladderB"), "B" + (i + 1), "sfx_hint_ball", r, "ball"); });
  ladderButton(document.getElementById("ladderO"), "OUT", "sfx_hint_out", 1, "out");

  // ------------------------------------------------------------------ cue list
  var groupsHost = document.getElementById("groups");
  D.groups.forEach(function (g) {
    if (g.id === "music") return;
    var box = document.createElement("div"); box.className = "group";
    box.innerHTML = "<h3><b>" + esc(g.ko) + "</b>" + esc(g.en) + "</h3>";
    D.cues.filter(function (c) { return c.group === g.id; }).forEach(function (c) {
      var row = document.createElement("div"); row.className = "cue";
      row.innerHTML = '<button class="play" aria-label="' + esc(c.ko) + ' 재생">' + playIcon() + '<span class="lamp ' + c.lamp + '"></span></button>' +
        '<div style="min-width:0"><div class="name">' + esc(c.ko) + '<span class="en">' + esc(c.en) + '</span></div>' +
        '<div class="file">' + c.id + '.ogg</div>' +
        '<div class="meta">' + c.ms + ' ms · ' + c.lufs.toFixed(1) + ' LUFS (' + c.metric + ') · ' + c.kb.toFixed(1) + ' KB</div></div>' +
        '<div class="body"><p class="ko">' + esc(c.listen_ko) + '</p><p>' + esc(c.listen) + '</p>' +
        '<p><span class="lbl">When</span>' + esc(c.trigger) + '</p>' +
        '<p><span class="lbl">Sound</span>' + esc(c.character) + ' · ' + esc(c.pitch) + '</p></div>';
      var btn = row.querySelector(".play");
      btn.addEventListener("click", function () {
        warmUp().then(function () { play(c.id); flash(btn.querySelector(".lamp"), c.ms); });
      });
      if (c.id === "sfx_hint_strike" || c.id === "sfx_hint_ball") {
        var ex = document.createElement("div"); ex.className = "extra";
        var rates = c.id === "sfx_hint_strike" ? D.strikeRates : D.ballRates, p = c.id === "sfx_hint_strike" ? "S" : "B";
        rates.slice(0, 4).forEach(function (r, i) { ladderButton(ex, p + (i + 1), c.id, r, c.lamp); });
        row.appendChild(ex);
      }
      box.appendChild(row);
    });
    groupsHost.appendChild(box);
  });

  // ------------------------------------------------------------------ music
  var musicHost = document.getElementById("music"), musicNow = null, raf = 0;
  function stopMusic() {
    if (musicNow) { try { musicNow.src.stop(); } catch (e) {} musicNow.card.querySelector(".play").innerHTML = playIcon() + '<span class="lamp chalk"></span>'; musicNow = null; }
    cancelAnimationFrame(raf);
  }
  function tick() {
    if (!musicNow) return;
    var dur = musicNow.dur, pos = (ctx.currentTime - musicNow.t0 + musicNow.offset) % dur;
    if (pos < 0) pos += dur;
    musicNow.card.querySelector(".bar i").style.width = (pos / dur * 100).toFixed(2) + "%";
    musicNow.card.querySelector(".pos").textContent = pos.toFixed(1) + " / " + dur.toFixed(3) + " s";
    raf = requestAnimationFrame(tick);
  }
  D.cues.filter(function (c) { return c.music; }).forEach(function (c) {
    var card = document.createElement("div"); card.className = "track";
    card.innerHTML = '<div class="top"><button class="play" aria-label="' + esc(c.ko) + ' 반복 재생">' + playIcon() + '<span class="lamp chalk"></span></button>' +
      '<div style="min-width:0"><h3>' + esc(c.ko) + ' <span style="font-weight:400;color:var(--text2);font-size:14px">' + esc(c.en) + '</span></h3>' +
      '<div class="meta">' + c.id + '.ogg · ' + (c.samples / 44100).toFixed(3) + ' s · loop 0 → ' + c.samples + ' · ' + c.lufs.toFixed(1) + ' LUFS · ' + Math.round(c.kb) + ' KB</div></div></div>' +
      '<p style="color:var(--chalk);font-size:14px">' + esc(c.listen_ko) + '</p>' +
      '<p style="color:var(--text2);font-size:14px">' + esc(c.character) + '. ' + esc(c.trigger) + '</p>' +
      '<div class="bar"><i></i><span class="seam"></span></div>' +
      '<div class="row"><button class="btn seamBtn">이음매 듣기 (Hear the seam)</button><span class="pos">0.0 s</span></div>';
    function start(offset) {
      setStatus("음악 불러오는 중", "chalk");
      ensureCtx().then(function () { return warmUp(); }).then(function () { return getBuffer(c.id); }).then(function (buf) {
        stopMusic();
        var src = play(c.id, { loop: true, offset: offset, when: ctx.currentTime + 0.05 });
        musicNow = { id: c.id, src: src, t0: ctx.currentTime + 0.05, offset: offset, dur: buf.duration, card: card };
        card.querySelector(".play").innerHTML = stopIcon() + '<span class="lamp chalk on"></span>';
        setStatus(useMp3 ? "준비됨 · MP3 사본" : "준비됨 · OGG", "ball");
        tick();
      });
    }
    card.querySelector(".play").addEventListener("click", function () {
      if (musicNow && musicNow.id === c.id) { stopMusic(); return; }
      start(0);
    });
    card.querySelector(".seamBtn").addEventListener("click", function () {
      getBuffer && start(Math.max(0, c.samples / 44100 - 4));
    });
    musicHost.appendChild(card);
  });
  document.addEventListener("visibilitychange", function () { if (document.hidden) stopMusic(); });
})();
</script>
"""


def main(argv):
    body, ogg_b, mp3_b = build()
    full = HEAD + body.replace('<title>', '<title>', 1)
    full = full.replace("<title>Number Baseball Sound Audition</title>",
                        "<title>Number Baseball Sound Audition</title>", 1)
    # standalone file: wrap the fragment in a complete document
    page = HEAD + body.split("<div class=\"wrap\">", 1)[0] + "</head>\n<body>\n<div class=\"wrap\">" + \
        body.split("<div class=\"wrap\">", 1)[1] + "</body>\n</html>\n"
    (ROOT / "audition.html").write_text(page, encoding="utf-8")
    kb = len(page.encode()) / 1024
    print(f"audition.html {kb:.0f} KB (OGG {ogg_b / 1024:.0f} KB + MP3 fallback {mp3_b / 1024:.0f} KB, base64)")
    if "--fragment" in argv:
        dst = Path(argv[argv.index("--fragment") + 1])
        dst.write_text(body, encoding="utf-8")
        print(f"fragment -> {dst}")
    if kb > PAGE_LIMIT_KB:
        sys.exit(f"page is {kb:.0f} KB, over the {PAGE_LIMIT_KB} KB limit")


if __name__ == "__main__":
    main(sys.argv[1:])
