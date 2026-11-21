#!/usr/bin/env python3
"""Synthesize the whole Number Baseball sound set and encode it to OGG Vorbis.

    python3 design/sound/tools/generate.py            # all cues
    python3 design/sound/tools/generate.py sfx_win    # only some cues

Writes design/sound/out/<id>.ogg (mono 44.1 kHz effects, stereo 44.1 kHz music loops) and float WAV
masters to the scratch folder given by $SOUND_TMP (default /tmp/nb_sound). Deterministic: every cue
seeds its own random generator from its id, so a rerun gives the same files.
Needs numpy, scipy, soundfile and ffmpeg with libvorbis.
"""
from __future__ import annotations

import os
import subprocess
import sys
import zlib
from pathlib import Path

import numpy as np
import soundfile as sf

sys.path.insert(0, str(Path(__file__).resolve().parent))
import dsp  # noqa: E402
from dsp import SR, hz, n  # noqa: E402
from cues import CUES, CUE_BY_ID  # noqa: E402

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / "out"
TMP = Path(os.environ.get("SOUND_TMP", "/tmp/nb_sound")) / "wav"

SFX_QUALITY = "4"     # libvorbis -q:a for effects (mono)
MUSIC_QUALITY = "0"   # libvorbis -q:a for music (stereo, soft material)
TP_CEILING = -2.0     # dBTP before encoding; leaves room for codec overshoot (deliverable rule: <= -1)


def rng_for(name: str) -> np.random.Generator:
    return np.random.default_rng(zlib.crc32(name.encode()))


def pk(x):
    m = np.max(np.abs(x))
    return x / m if m > 0 else x


def pad_to(x, length):
    return np.pad(x, (0, max(0, length - x.size)))[:length] if x.ndim == 1 else \
        np.pad(x, ((0, 0), (0, max(0, length - x.shape[1]))))[:, :length]


# ============================================================================ building blocks
def exciter(rng, ms=0.8, tau_ms=0.25):
    """Very short noise burst: the 'hit' that drives resonators."""
    L = max(4, n(ms / 1000))
    return dsp.noise(L, rng) * dsp.env_exp(L, tau_ms / 1000)


def resonate(ex, length, modes):
    """Drive a bank of resonators. modes = [(freq, q, gain)]."""
    x = pad_to(ex, length)
    return sum(g * pk(dsp.resonator(x, f, q)) for f, q, g in modes)


def relay_click(rng, bright=1.0, weight=1.0, bounce_ms=4.5, bounce=0.45):
    """Scoreboard lamp relay: armature hit plus one or two contact bounces, small metal body."""
    L = n(0.035)

    def contact(amp, jitter):
        b = bright * jitter
        y = resonate(exciter(rng, 0.7, 0.2), L, [(2400 * b, 9, 1.0), (3900 * b, 11, 0.7), (6100 * b, 14, 0.35)])
        body = dsp.pitch_drop_sine(165 / max(weight, 0.5) * 1.6, 0.035, 0.6, 0.003, 0.006) * 0.35 * weight
        return amp * (y + body)

    out = contact(1.0, 1.0)
    out = dsp.place(out, contact(bounce, 1.04), bounce_ms / 1000)
    out = dsp.place(out, contact(bounce * 0.4, 0.97), bounce_ms * 1.9 / 1000)
    out = dsp.lowpass(out, 9000, 2)
    return dsp.fade_out(out, 4)


def bell(f, seconds, rng, tau=0.16, bright=1.0):
    """Glassy struck bar / small bell (inharmonic partials of a free bar plus a beating pair)."""
    parts = [(1.0, 1.0, tau), (1.0035, 0.25, tau * 1.35), (2.0, 0.10, tau * 0.5),
             (2.76, 0.28 * bright, tau * 0.32), (5.40, 0.10 * bright, tau * 0.13), (8.93, 0.04 * bright, tau * 0.06)]
    x = dsp.partials(f, seconds, parts, attack=0.0008, rng=rng)
    tick = dsp.highpass(pad_to(exciter(rng, 1.2, 0.3), x.size), 3000) * 0.08 * bright
    return x + tick


def mallet(f, seconds, rng, tau=0.075):
    """Rosewood bar (marimba-like 1 : 3.93 : 9.2 modes) with a soft wooden contact."""
    parts = [(1.0, 1.0, tau), (3.93, 0.22, tau * 0.24), (9.2, 0.04, tau * 0.08)]
    x = dsp.partials(f, seconds, parts, attack=0.0015, rng=rng)
    wood = resonate(exciter(rng, 2.0, 0.5), x.size, [(1900, 4, 1.0)]) * dsp.env_exp(x.size, 0.006) * 0.12
    return dsp.lowpass(x + wood, 5000, 2)


def organ(freqs, seconds, rng, attack=0.025, release=0.25, drawbars=None, vib=0.0018, lp=2600.0,
          chorus=True):
    """Muted drawbar organ chord: harmonic stack, slow chorus/vibrato, low-passed. Length = seconds+release."""
    draw = drawbars or [(1, 1.0), (2, 0.55), (3, 0.28), (4, 0.22), (6, 0.08), (8, 0.05)]
    L = n(seconds + release)
    t = np.arange(L) / SR
    out = np.zeros(L)
    for f in np.atleast_1d(freqs):
        for det in ([-3.0, 3.0] if chorus else [0.0]):
            rate = rng.uniform(4.8, 6.2)
            fm = f * 2 ** (det / 1200) * (1 + vib * np.sin(2 * np.pi * rate * t + rng.uniform(0, 6.28)))
            phase = np.cumsum(2 * np.pi * fm / SR)
            for h, a in draw:
                if f * h < SR * 0.4:
                    out += a * np.sin(h * phase + rng.uniform(0, 6.28))
    env = dsp.env_asr(L, attack, release)
    return dsp.lowpass(out * env, lp, 2)


def leather_pop(rng, body_hz=130.0):
    """Ball into a catcher's mitt: slap of leather plus a padded body thump."""
    L = n(0.16)
    slap = resonate(exciter(rng, 2.2, 0.6), L, [(900, 2.2, 1.0), (1500, 3.0, 0.6), (2600, 4.0, 0.25)])
    slap *= dsp.env_exp(L, 0.018)
    body = dsp.pitch_drop_sine(body_hz, 0.16, 0.8, 0.006, 0.04)
    mid = dsp.pitch_drop_sine(body_hz * 3.1, 0.16, 0.5, 0.004, 0.022) * 0.5   # audible on phone speakers
    return dsp.lowpass(0.8 * pk(slap) + body + mid, 7000, 2)


def wood_knock(f, rng, tau=0.035):
    L = n(0.14)
    body = dsp.pitch_drop_sine(f, 0.14, 0.3, 0.004, tau)
    over = dsp.partials(f, 0.14, [(2.71, 0.3, tau * 0.35), (4.6, 0.1, tau * 0.15)], attack=0.0005)
    hit = dsp.lowpass(pad_to(exciter(rng, 1.5, 0.4), L), 2500) * 0.6
    return dsp.lowpass(body + over + pk(hit) * 0.25, 1800, 2)


def tap(rng, f1, f2, fb, body_gain=0.6, length=0.055):
    """Soft key tap: plate modes + small body; dry."""
    L = n(length)
    plate = resonate(exciter(rng, 1.4, 0.4), L, [(f1, 7, 1.0), (f2, 6, 0.55)])
    body = dsp.pitch_drop_sine(fb, length, 0.25, 0.004, 0.016) * body_gain
    thock = dsp.lowpass(pad_to(exciter(rng, 3.0, 0.9), L), 900)
    y = 0.8 * pk(plate) + body + 0.25 * pk(thock)
    y = dsp.highpass(dsp.lowpass(y, 7000, 2), 120, 2)
    return dsp.fade_out(y, 6)


def finish(x, tail_db=-66.0):
    x = dsp.dc_block(x)
    return dsp.trim_tail(x, tail_db)


SMALL_ROOM = None


def small_room():
    global SMALL_ROOM
    if SMALL_ROOM is None:
        SMALL_ROOM = dsp.reverb_ir(1.6, 1.1, seed=7, lp=4500, predelay_ms=14)
    return SMALL_ROOM


# ============================================================================ effects
def keypad(variant):
    f1, f2, fb = {1: (2050, 3450, 410), 2: (2250, 3700, 445), 3: (1900, 3250, 385)}[variant]
    return finish(tap(rng_for(f"keypad{variant}"), f1, f2, fb))


def keypad_delete():
    rng = rng_for("delete")
    a = tap(rng, 1500, 2600, 300, body_gain=0.7, length=0.06)
    b = tap(rng, 1300, 2300, 280, body_gain=0.6, length=0.06)
    out = dsp.place(a * 0.75, b, 0.022)
    return finish(out)


def input_invalid():
    rng = rng_for("invalid")
    out = wood_knock(hz("D4"), rng)
    out = dsp.place(out, wood_knock(hz("B3"), rng) * 0.85, 0.085)
    return finish(out)


def memo_mark():
    rng = rng_for("memo")
    L = n(0.03)
    grain = dsp.noise(L, rng) * (rng.random(L) > 0.55)            # gritty chalk grains
    grain = dsp.bandpass(grain, 2500, 7000, 2) * dsp.env_exp(L, 0.004, attack=0.0006)
    ring = resonate(exciter(rng, 0.8, 0.2), L, [(3200, 10, 1.0)]) * 0.25
    return finish(pk(grain) + ring)


def secret_locked():
    rng = rng_for("secret")
    out = relay_click(rng, bright=0.75, weight=1.8, bounce_ms=6, bounce=0.35)
    tone = organ([hz("D3"), hz("A3")], 0.10, rng, attack=0.004, release=0.14, lp=1400, chorus=False)
    tone *= dsp.env_exp(tone.size, 0.09)
    out = dsp.place(out, 0.9 * pk(tone), 0.002)
    out = dsp.place(out, relay_click(rng, bright=1.0, weight=0.9) * 0.45, 0.085)
    return finish(out)


def guess_sent():
    rng = rng_for("guess")
    out = leather_pop(rng)
    air = dsp.bandpass(dsp.noise(n(0.09), rng), 1200, 4500, 2) * dsp.env_exp(n(0.09), 0.025, attack=0.002)
    out = dsp.place(out, 0.18 * pk(air), 0.0)
    out = dsp.place(out, relay_click(rng, bright=1.05, weight=0.7) * 0.32, 0.078)
    return finish(out)


def hint_strike():
    rng = rng_for("strike")
    out = relay_click(rng, bright=1.12, weight=0.8) * 0.5
    out = dsp.place(out, pk(bell(hz("A5"), 0.36, rng, tau=0.16)), 0.004)
    return finish(dsp.fade_out(out, 40))


def hint_ball():
    rng = rng_for("ball")
    out = relay_click(rng, bright=1.0, weight=1.0) * 0.5
    out = dsp.place(out, pk(mallet(hz("D5"), 0.24, rng, tau=0.07)), 0.004)
    return finish(dsp.fade_out(out, 30))


def hint_out():
    rng = rng_for("out")
    out = relay_click(rng, bright=0.72, weight=1.7, bounce_ms=6.5, bounce=0.3) * 0.55
    L = n(0.22)
    body = dsp.pitch_drop_sine(hz("D4"), 0.22, 0.5, 0.008, 0.05)
    body += dsp.partials(hz("D4"), 0.22, [(2.0, 0.45, 0.025), (3.0, 0.2, 0.015)], attack=0.0008)
    sub = dsp.pitch_drop_sine(hz("D3"), 0.22, 0.6, 0.01, 0.06) * 0.3
    leather = dsp.lowpass(pad_to(exciter(rng, 3.0, 1.0), L), 800) * dsp.env_exp(L, 0.012)
    thunk = dsp.lowpass(body + sub + 0.3 * pk(leather), 1500, 2)
    out = dsp.place(out, pk(thunk), 0.003)
    return finish(dsp.fade_out(out, 30))


def opponent_guessed():
    rng = rng_for("opp")
    click = dsp.lowpass(relay_click(rng, bright=0.85, weight=0.6, bounce_ms=5), 3000, 2)
    tone = mallet(hz("A4"), 0.1, rng, tau=0.035) * 0.35
    out = dsp.place(pk(click), pk(tone) * 0.4, 0.003)
    return finish(dsp.fade_out(out, 20))


def solved():
    rng = rng_for("solved")
    L = n(0.12)
    crack = resonate(exciter(rng, 1.2, 0.25), L, [(1150, 12, 1.0), (2350, 14, 0.8), (3650, 16, 0.55), (5200, 10, 0.3)])
    crack = pk(crack) + 0.6 * dsp.pitch_drop_sine(180, 0.12, 1.0, 0.004, 0.03)
    crack = dsp.highpass(crack, 150, 2)
    dry = np.zeros(n(1.5))
    dry = dsp.place(dry, pk(crack) * 0.9, 0.0)
    for i, (note, at) in enumerate([("D6", 0.035), ("F#6", 0.095), ("A6", 0.155), ("D7", 0.215)]):
        dry = dsp.place(dry, pk(bell(hz(note), 1.1, rng, tau=0.42 - i * 0.05, bright=0.8)) * (0.55 - i * 0.06), at)
    pad = organ([hz("D4"), hz("F#4"), hz("A4")], 0.7, rng, attack=0.12, release=0.45, lp=2000)
    dry = dsp.place(dry, pk(pad) * 0.22, 0.05)
    wet = dsp.add_reverb(dry, small_room(), 0.22)
    return finish(dsp.fade_out(wet[: n(1.55)], 250))


def win():
    rng = rng_for("win")
    dry = np.zeros(n(2.4))
    for note, at in [("D5", 0.0), ("F#5", 0.07), ("A5", 0.14)]:
        hit = pk(mallet(hz(note), 0.5, rng, tau=0.12)) * 0.5 + pk(bell(hz(note), 0.5, rng, tau=0.2, bright=0.5)) * 0.35
        dry = dsp.place(dry, hit, at)
    chord = organ([hz("D4"), hz("F#4"), hz("A4"), hz("D5")], 1.15, rng, attack=0.03, release=0.6, lp=2800)
    dry = dsp.place(dry, pk(chord) * 0.5, 0.14)
    pedal = organ([hz("D3")], 1.15, rng, attack=0.03, release=0.6, lp=900, chorus=False)
    dry = dsp.place(dry, pk(pedal) * 0.3, 0.14)
    dry = dsp.place(dry, pk(bell(hz("D6"), 1.4, rng, tau=0.45, bright=0.6)) * 0.4, 0.30)
    wet = dsp.add_reverb(dry, small_room(), 0.25)
    return finish(dsp.fade_out(wet[: n(2.4)], 400))


def lose():
    rng = rng_for("lose")
    dry = np.zeros(n(2.3))
    L = n(0.2)
    thud = dsp.pitch_drop_sine(hz("B3"), 0.2, 0.5, 0.01, 0.045) + 0.4 * dsp.pitch_drop_sine(hz("B2"), 0.2, 0.6, 0.01, 0.06)
    thud += 0.25 * pk(dsp.lowpass(pad_to(exciter(rng, 3, 1.0), L), 700)) * dsp.env_exp(L, 0.015)
    dry = dsp.place(dry, pk(dsp.lowpass(thud, 1200)) * 0.55, 0.0)
    soft = [(1, 1.0), (2, 0.4), (3, 0.15), (4, 0.1)]
    for note, at, dur in [("F#4", 0.02, 0.30), ("D4", 0.27, 0.30)]:
        dry = dsp.place(dry, pk(organ([hz(note)], dur, rng, attack=0.03, release=0.12, drawbars=soft, lp=1700)) * 0.42, at)
    chord = organ([hz("B3"), hz("D4"), hz("F#4")], 0.95, rng, attack=0.05, release=0.6, drawbars=soft, lp=1500)
    dry = dsp.place(dry, pk(chord) * 0.5, 0.52)
    bass = organ([hz("B2")], 0.95, rng, attack=0.05, release=0.6, drawbars=[(1, 1.0), (2, 0.5), (3, 0.2)], lp=800, chorus=False)
    dry = dsp.place(dry, pk(bass) * 0.3, 0.52)
    wet = dsp.add_reverb(dry, small_room(), 0.22)
    return finish(dsp.fade_out(wet[: n(2.3)], 400))


def draw():
    rng = rng_for("draw")
    dry = np.zeros(n(2.0))
    for at in (0.0, 0.09):
        dry = dsp.place(dry, pk(bell(hz("A5"), 0.8, rng, tau=0.22, bright=0.7)) * 0.5, at)
    fifth = organ([hz("D4"), hz("A4")], 0.85, rng, attack=0.06, release=0.5, lp=2200)
    dry = dsp.place(dry, pk(fifth) * 0.45, 0.15)
    pedal = organ([hz("D3")], 0.85, rng, attack=0.06, release=0.5, lp=900, chorus=False)
    dry = dsp.place(dry, pk(pedal) * 0.22, 0.15)
    wet = dsp.add_reverb(dry, small_room(), 0.22)
    return finish(dsp.fade_out(wet[: n(1.9)], 350))


def chime(f, seconds, rng, tau):
    """PA chime: bell and soft organ blended (warmer than the strike bell)."""
    b = pk(bell(f, seconds, rng, tau=tau, bright=0.55))
    o = organ([f], seconds * 0.5, rng, attack=0.004, release=seconds * 0.5, lp=2500, chorus=False)
    o = pad_to(o, b.size) * dsp.env_exp(b.size, tau * 1.2)
    return b * 0.7 + pk(o) * 0.35


def match_found():
    rng = rng_for("match")
    dry = np.zeros(n(1.1))
    for note, at in [("A4", 0.0), ("D5", 0.12)]:
        dry = dsp.place(dry, relay_click(rng, bright=1.0, weight=0.9) * 0.3, at)
        dry = dsp.place(dry, pk(chime(hz(note), 0.9, rng, tau=0.3)) * 0.6, at + 0.003)
    wet = dsp.add_reverb(dry, small_room(), 0.2)
    return finish(dsp.fade_out(wet[: n(1.1)], 200))


def timer_warning():
    rng = rng_for("tick")
    L = n(0.06)
    y = resonate(exciter(rng, 0.5, 0.15), L, [(2600, 18, 1.0), (1200, 8, 0.5), (4300, 12, 0.25)])
    body = dsp.pitch_drop_sine(820, 0.06, 0.2, 0.003, 0.01) * 0.5     # small wooden body, lowers the crest factor
    y = (pk(y) + body) * dsp.env_exp(L, 0.016)
    return finish(dsp.fade_out(dsp.highpass(y, 300), 10))


def timer_warning_last():
    rng = rng_for("ticklast")
    L = n(0.05)
    y = resonate(exciter(rng, 0.5, 0.15), L, [(2900, 18, 1.0), (1400, 8, 0.45), (4800, 12, 0.3)])
    y = pk(y * dsp.env_exp(L, 0.012))
    tone = dsp.partials(hz("B5"), 0.13, [(1.0, 1.0, 0.045), (2.0, 0.15, 0.02)], attack=0.001)
    out = dsp.place(y, pk(tone) * 0.4, 0.002)
    return finish(dsp.fade_out(out, 15))


def time_out():
    rng = rng_for("timeout")
    dry = np.zeros(n(1.0))
    dry = dsp.place(dry, relay_click(rng, bright=0.75, weight=1.6, bounce_ms=6, bounce=0.3) * 0.45, 0.0)
    dry = dsp.place(dry, pk(dsp.lowpass(chime(hz("A4"), 0.6, rng, tau=0.2), 2500)) * 0.6, 0.004)
    dry = dsp.place(dry, pk(dsp.lowpass(chime(hz("D4"), 0.75, rng, tau=0.26), 2200)) * 0.6, 0.17)
    wet = dsp.add_reverb(dry, small_room(), 0.18)
    return finish(dsp.fade_out(wet[: n(0.95)], 180))


def chat_message():
    rng = rng_for("chat")
    out = pk(mallet(hz("E5"), 0.14, rng, tau=0.05)) * 0.8
    out = dsp.place(out, pk(mallet(hz("A5"), 0.16, rng, tau=0.06)), 0.045)
    return finish(dsp.fade_out(out, 30))


def quick_phrase():
    rng = rng_for("phrase")
    L = n(0.03)
    out = np.zeros(n(0.2))
    for at, a in [(0.0, 1.0), (0.014, 0.7), (0.027, 0.5), (0.039, 0.33)]:
        flap = resonate(exciter(rng, 1.0, 0.3), L, [(1800 * rng.uniform(0.95, 1.05), 5, 1.0), (3300, 6, 0.6)])
        flap = dsp.highpass(flap, 600) * dsp.env_exp(L, 0.006)
        out = dsp.place(out, pk(flap) * a * 0.7, at)
    out = dsp.place(out, pk(mallet(hz("D5"), 0.14, rng, tau=0.05)) * 0.55, 0.035)
    return finish(dsp.fade_out(out, 30))


def ui_navigate():
    rng = rng_for("nav")
    return finish(tap(rng, 3000, 4600, 650, body_gain=0.45, length=0.04))


def ui_back():
    rng = rng_for("back")
    return finish(tap(rng, 2200, 3500, 480, body_gain=0.5, length=0.045))


def toggle(on):
    rng = rng_for("toggle_on" if on else "toggle_off")
    low = tap(rng, 1700, 2900, 300, body_gain=0.55, length=0.045)
    high = tap(rng, 3100, 4700, 520, body_gain=0.35, length=0.04)
    first, second = (low, high) if on else (high, low)
    out = dsp.place(first, second * 0.85, 0.018)
    return finish(out)


def error():
    rng = rng_for("error")
    out = np.zeros(n(0.32))
    soft = [(1, 1.0), (2, 0.35), (3, 0.15), (4, 0.08)]
    for note, at in [("B4", 0.0), ("F#4", 0.11)]:
        o = organ([hz(note)], 0.06, rng, attack=0.004, release=0.16, drawbars=soft, lp=2200, chorus=False)
        o = o * dsp.env_exp(o.size, 0.07)
        m = mallet(hz(note), 0.2, rng, tau=0.06)
        out = dsp.place(out, pk(o) * 0.6 + pad_to(pk(m), o.size) * 0.35, at)
    return finish(dsp.fade_out(out, 40))


# ============================================================================ music
def epiano(f, seconds, rng, vel=1.0):
    """Soft FM electric piano (1:1 carrier/modulator, decaying index, faint tine)."""
    L = n(seconds + 0.6)
    t = np.arange(L) / SR
    index = (1.2 * vel) * np.exp(-t / 0.35) + 0.15
    mod = np.sin(2 * np.pi * f * t)
    car = np.sin(2 * np.pi * f * t + index * mod)
    tine = np.sin(2 * np.pi * f * 7.1 * t) * np.exp(-t / 0.02) * 0.06 * vel
    env = dsp.env_exp(L, 0.9, attack=0.004)
    rel = np.ones(L)
    k0 = n(seconds)
    rel[k0:] = np.exp(-(np.arange(L - k0)) / (0.12 * SR))
    return dsp.lowpass((car + tine) * env * rel, 3500, 2) * vel


def bass_note(f, seconds, rng, tau=0.55, lp=900):
    L = n(seconds + 0.15)
    t = np.arange(L) / SR
    y = np.sin(2 * np.pi * f * t) + 0.45 * np.sin(4 * np.pi * f * t) + 0.18 * np.sin(6 * np.pi * f * t)
    env = dsp.env_exp(L, tau, attack=0.008)
    k0 = n(seconds)
    env[k0:] *= np.exp(-np.arange(L - k0) / (0.04 * SR))
    thump = dsp.lowpass(pad_to(exciter(rng, 3, 1.5), L), 500) * 0.15
    return dsp.lowpass(y * env + pk(thump) * 0.1, lp, 2)


def brush(rng, level=1.0, length=0.16, tau=0.05):
    L = n(length)
    y = dsp.bandpass(dsp.noise(L, rng), 2500, 8000, 2) * dsp.env_exp(L, tau, attack=0.006)
    return pk(y) * level


def felt_kick(rng):
    y = dsp.pitch_drop_sine(62, 0.3, 1.4, 0.02, 0.11) + 0.3 * dsp.pitch_drop_sine(124, 0.3, 1.0, 0.015, 0.06)
    return pk(y)


def chord_pad(midis, seconds, rng, lp=1800, attack=0.4, release=0.9, gain=1.0):
    """Stereo organ pad: left and right voices with independent chorus for width."""
    soft = [(1, 1.0), (2, 0.42), (3, 0.16), (4, 0.12), (6, 0.03)]
    freqs = [dsp.midi_hz(m) for m in midis]
    l = organ(freqs, seconds, rng, attack=attack, release=release, drawbars=soft, lp=lp, vib=0.0012)
    r = organ(freqs, seconds, rng, attack=attack, release=release, drawbars=soft, lp=lp, vib=0.0012)
    # slow rotor-like movement, opposite phase in the two channels
    t = np.arange(l.size) / SR
    trem = 0.12 * np.sin(2 * np.pi * 0.6 * t + rng.uniform(0, 6.28))
    return np.vstack([l * (1 + trem), r * (1 - trem)]) * gain


def master_loop(dry: np.ndarray, ir: np.ndarray, wet: float, target: float, lp: float) -> np.ndarray:
    """Circular reverb (wet tail folded back to the start) so the loop is seamless, then master."""
    N = dry.shape[1]
    w = dsp.add_reverb(dry, ir, wet)                      # (2, N + len(ir) - 1)
    loop = w[:, :N].copy()
    tail = w[:, N:]
    k = 0
    while k < tail.shape[1]:
        take = min(N, tail.shape[1] - k)
        loop[:, :take] += tail[:, k:k + take]
        k += take
    loop = dsp.lowpass(loop, lp, 2)
    # the low-pass and DC filters are recursive: run them over three periods and keep the last one so
    # the filter state at the seam is the steady state (no seam transient)
    loop3 = np.concatenate([loop, loop, loop], axis=1)
    loop3 = dsp.dc_block(loop3, 30)
    loop = loop3[:, 2 * N:3 * N]
    return dsp.normalize(loop, target, "integrated", tp_ceiling=TP_CEILING)


def music_menu():
    rng = rng_for("music_menu")
    beat = 33075                                          # 80 BPM at 44.1 kHz
    bars = 16
    N = beat * 4 * bars                                   # 2 116 800 samples = 48.000 s
    dry = np.zeros((2, N))
    B = lambda bar, b=0.0: int(round((bar * 4 + b) * beat))  # noqa: E731
    sec = beat / SR

    prog = [  # (bass midi, pad midis)
        (38, [54, 57, 61, 64]),   # Dmaj9
        (47, [54, 57, 61, 62]),   # Bm9
        (43, [54, 57, 59, 62]),   # Gmaj9
        (45, [55, 57, 62, 64]),   # A7sus4
    ]
    tail = [(43, [54, 57, 59, 62]), (42, [52, 57, 61, 64]), (40, [54, 55, 59, 62]), (45, [55, 57, 61, 64])]
    chords = prog * 3 + tail
    for bar, (bass, pad) in enumerate(chords):
        dsp.place_wrap(dry, chord_pad(pad, 4 * sec, rng, gain=0.30), B(bar))
        for b, interval, dur, v in [(0, 0, 1.75, 1.0), (2, 7, 1.0, 0.75), (3.5, 12, 0.4, 0.45)]:
            note = bass_note(dsp.midi_hz(bass + interval), dur * sec, rng)
            dsp.place_wrap(dry, dsp.pan(note, 0.0) * 0.42 * v, B(bar, b))

    melody = [  # (bar, beat, midi, beats, velocity)
        (2, 2, 78, 1, 0.6), (2, 3, 76, 1, 0.55), (3, 0, 74, 3.5, 0.6),
        (4, 0, 78, 1.5, 0.8), (4, 1.5, 76, 0.5, 0.65), (4, 2, 74, 2, 0.75),
        (5, 0.5, 71, 1, 0.65), (5, 1.5, 74, 1, 0.7), (5, 2.5, 76, 1.5, 0.75),
        (6, 0, 71, 1, 0.65), (6, 1, 74, 1, 0.7), (6, 2, 78, 2, 0.8), (7, 0, 76, 3.5, 0.7),
        (8, 0, 81, 1.5, 0.85), (8, 1.5, 78, 0.5, 0.65), (8, 2, 76, 1, 0.7), (8, 3, 74, 1, 0.65),
        (9, 0, 73, 1.5, 0.75), (9, 1.5, 71, 0.5, 0.6), (9, 2, 69, 2, 0.7),
        (10, 0, 71, 1, 0.65), (10, 1, 74, 1, 0.7), (10, 2, 79, 1, 0.8), (10, 3, 78, 1, 0.7),
        (11, 0, 76, 2.5, 0.75), (11, 2.5, 74, 1.5, 0.6),
        (12, 0, 78, 2, 0.7), (12, 2, 74, 2, 0.6), (13, 0, 73, 2, 0.65), (13, 2, 69, 2, 0.55),
        (14, 0, 71, 2, 0.6), (14, 2, 66, 2, 0.5), (15, 0, 69, 2, 0.55), (15, 2, 73, 1, 0.5), (15, 3, 76, 1, 0.55),
    ]
    for bar, b, m, beats, v in melody:
        note = epiano(dsp.midi_hz(m), beats * sec * 0.95, rng, vel=v)
        dsp.place_wrap(dry, dsp.pan(note, 0.18) * 0.33, B(bar, b))

    for bar in range(bars):
        dsp.place_wrap(dry, dsp.pan(felt_kick(rng), 0.0) * 0.16, B(bar, 0))
        for b in (1, 3):
            dsp.place_wrap(dry, dsp.pan(brush(rng, 1.0, 0.25, 0.08), 0.3) * 0.035, B(bar, b))
        for e in range(8):                                # swung eighths, very soft
            off = e * 0.5 + (0.08 if e % 2 else 0.0)
            dsp.place_wrap(dry, dsp.pan(brush(rng, 1.0, 0.08, 0.018), -0.35) * (0.016 if e % 2 else 0.022),
                           B(bar, off))
    ir = dsp.reverb_ir(2.8, 2.2, seed=11, stereo=True, lp=4000, predelay_ms=20)
    return master_loop(dry, ir, wet=0.28, target=CUE_BY_ID["music_menu"]["target"], lp=7000)


def music_match():
    rng = rng_for("music_match")
    beat = 31500                                          # 84 BPM
    bars = 16
    N = beat * 4 * bars                                   # 2 016 000 samples = 45.714 s
    dry = np.zeros((2, N))
    B = lambda bar, b=0.0: int(round((bar * 4 + b) * beat))  # noqa: E731
    sec = beat / SR
    # two bars per chord: Dsus2, Bm(add9), Gmaj7, Asus4 -> A
    chords = [(38, [50, 57, 64]), (35, [50, 54, 61]), (31, [47, 54, 62]), (33, [50, 57, 64])]
    for half in range(2):
        for ci, (root, pad) in enumerate(chords):
            bar0 = half * 8 + ci * 2
            p = pad if not (ci == 3) else pad
            dsp.place_wrap(dry, chord_pad(p, 8 * sec, rng, lp=1100, attack=0.9, release=1.2, gain=0.22), B(bar0))
            if ci == 3:   # resolve the sus4 to A (C#) on the second bar
                dsp.place_wrap(dry, chord_pad([49, 57, 61], 4 * sec, rng, lp=1100, attack=0.6, release=1.0, gain=0.10),
                               B(bar0 + 1))
            for bb in range(2):
                for e, interval, v in [(0, 0, 1.0), (2, 0, 0.55), (3, 12, 0.45), (5, 0, 0.65), (6, 7, 0.55)]:
                    f = dsp.midi_hz(root + 12 + interval)
                    note = bass_note(f, 0.42 * sec, rng, tau=0.11, lp=700)
                    dsp.place_wrap(dry, dsp.pan(note, 0.0) * 0.5 * v, B(bar0 + bb, e * 0.5))
    # low marimba arpeggios (below 500 Hz) in bars 4-7 and 12-15
    arps = {0: [50, 57, 62, 64], 1: [47, 54, 59, 61], 2: [43, 50, 54, 59], 3: [45, 52, 57, 61]}
    for bar in list(range(4, 8)) + list(range(12, 16)):
        notes = arps[(bar // 2) % 4]
        for i, b in enumerate([0, 0.75, 1.5, 2.5]):
            m = mallet(dsp.midi_hz(notes[i]), 0.5, rng, tau=0.16)
            dsp.place_wrap(dry, dsp.pan(m, -0.25 + 0.15 * i) * 0.13, B(bar, b))
    # counter line (soft organ, 290-440 Hz) in the second half
    line = [(8, 69, 2), (8.5, 66, 2), (9, 64, 4), (10, 66, 2), (10.5, 62, 2), (11, 61, 4),
            (12, 62, 2), (12.5, 59, 2), (13, 57, 4), (14, 59, 2), (14.5, 61, 2), (15, 64, 4)]
    soft = [(1, 1.0), (2, 0.3), (3, 0.1)]
    for barpos, m, beats in line:
        o = organ([dsp.midi_hz(m)], beats * sec * 0.95, rng, attack=0.15, release=0.4, drawbars=soft, lp=1200)
        dsp.place_wrap(dry, dsp.pan(o, 0.25) * 0.07, B(int(barpos), (barpos % 1) * 4))
    for bar in range(bars):
        for b in (0, 2):
            dsp.place_wrap(dry, dsp.pan(felt_kick(rng), 0.0) * 0.20, B(bar, b))
        for b in (1, 3):
            dsp.place_wrap(dry, dsp.pan(brush(rng, 1.0, 0.25, 0.07), 0.25) * 0.03, B(bar, b))
        for e in range(8):
            dsp.place_wrap(dry, dsp.pan(brush(rng, 1.0, 0.07, 0.015), -0.3) * (0.012 if e % 2 else 0.018),
                           B(bar, e * 0.5))
    ir = dsp.reverb_ir(2.4, 1.8, seed=13, stereo=True, lp=3000, predelay_ms=18)
    return master_loop(dry, ir, wet=0.22, target=CUE_BY_ID["music_match"]["target"], lp=4500)


# ============================================================================ registry / output
BUILDERS = {
    "sfx_keypad_1": lambda: keypad(1), "sfx_keypad_2": lambda: keypad(2), "sfx_keypad_3": lambda: keypad(3),
    "sfx_keypad_delete": keypad_delete, "sfx_input_invalid": input_invalid, "sfx_memo_mark": memo_mark,
    "sfx_secret_locked": secret_locked, "sfx_guess_sent": guess_sent,
    "sfx_hint_strike": hint_strike, "sfx_hint_ball": hint_ball, "sfx_hint_out": hint_out,
    "sfx_opponent_guessed": opponent_guessed,
    "sfx_solved": solved, "sfx_win": win, "sfx_lose": lose, "sfx_draw": draw,
    "sfx_match_found": match_found, "sfx_timer_warning": timer_warning,
    "sfx_timer_warning_last": timer_warning_last, "sfx_time_out": time_out,
    "sfx_chat_message": chat_message, "sfx_quick_phrase": quick_phrase,
    "sfx_ui_navigate": ui_navigate, "sfx_ui_back": ui_back,
    "sfx_toggle_on": lambda: toggle(True), "sfx_toggle_off": lambda: toggle(False), "sfx_error": error,
    "music_menu": music_menu, "music_match": music_match,
}


def encode(wav: Path, ogg: Path, music: bool):
    cmd = ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(wav), "-map_metadata", "-1",
           "-c:a", "libvorbis", "-q:a", MUSIC_QUALITY if music else SFX_QUALITY, "-ar", str(SR),
           "-ac", "2" if music else "1", "-fflags", "+bitexact", "-flags:a", "+bitexact", str(ogg)]
    subprocess.run(cmd, check=True)


def build(cue_id: str):
    cue = CUE_BY_ID[cue_id]
    music = cue["group"] == "music"
    x = BUILDERS[cue_id]()
    if not music:
        x = dsp.normalize(x, cue["target"], "momentary", tp_ceiling=TP_CEILING)
        x = dsp.fade_out(x, 3)  # guarantee the very last samples reach zero
    TMP.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    wav = TMP / f"{cue_id}.wav"
    sf.write(wav, x.T if x.ndim == 2 else x, SR, subtype="FLOAT")
    encode(wav, OUT / f"{cue_id}.ogg", music)
    dur = x.shape[-1] / SR
    print(f"{cue_id:26s} {dur * 1000:8.0f} ms  M-max {dsp.sliding_max_loudness(x, 0.4):6.1f}  "
          f"I {dsp.integrated_loudness(x):6.1f}  TP {dsp.true_peak_db(x):5.1f}  "
          f"{(OUT / f'{cue_id}.ogg').stat().st_size / 1024:6.1f} KB")


def main(argv):
    ids = argv or [c["id"] for c in CUES]
    unknown = [i for i in ids if i not in BUILDERS]
    if unknown:
        sys.exit(f"unknown cue ids: {unknown}")
    for cue_id in ids:
        build(cue_id)


if __name__ == "__main__":
    main(sys.argv[1:])
