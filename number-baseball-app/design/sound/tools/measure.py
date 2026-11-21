#!/usr/bin/env python3
"""Objective checks for the sound set (nobody on the team can listen, so everything is measured).

    python3 design/sound/tools/measure.py

Decodes every design/sound/out/*.ogg (what the app will actually play), measures loudness
(BS.1770-4 momentary max, short-term max, integrated), true peak (4x oversampled), duration, DC
offset, clipping, leading silence, end level, small-speaker loss and, for music, the loop seam.
Renders the demo sequences from cues.SEQUENCES the way SoundPool would (rate = resample) to check
summed peaks. Writes design/sound/REPORT.md, design/sound/measurements.json and spectrogram PNGs in
design/sound/spectrograms/. Exit code 1 if any check fails.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

sys.path.insert(0, str(Path(__file__).resolve().parent))
import dsp  # noqa: E402
from dsp import SR  # noqa: E402
from cues import CUES, CUE_BY_ID, SEQUENCES, STRIKE_RATES, BALL_RATES  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "out"
SPEC = ROOT / "spectrograms"

# Rules (SOUND_DESIGN.md "Mixing rules")
TOL_LU = 1.0             # loudness within +/- 1 LU of the cue target
TP_MAX = -1.0            # dBTP
DC_MAX = 0.001           # |mean| as a fraction of full scale (-60 dBFS)
LEAD_MAX_MS = 5.0        # silence before the attack, effects
LEAD_THRESH = 10 ** (-50 / 20)
END_MAX_DB = -50.0       # peak of the last 2 ms (no click when the sound ends)
PHONE_LOSS_WARN = 6.0    # LU lost through a 300 Hz high-pass (small phone speaker)
SET_BUDGET_KB = 1536
SEAM_RATIO_MAX = 2.0     # >6 kHz peak at the seam vs the loop's own 99th percentile of 2 ms block peaks
SEAM_FLOOR_DB = -60.0    # ...or below this absolute level (>= 34 dB under the music's loudness: masked)
AUDIBLE_DB = -60.0       # audible duration = up to the last sample above this (dBFS)

SPECTRO_CUES = ["sfx_keypad_1", "sfx_keypad_delete", "sfx_input_invalid", "sfx_guess_sent", "sfx_hint_strike",
                "sfx_hint_ball", "sfx_hint_out", "sfx_solved", "sfx_win", "sfx_lose", "sfx_draw",
                "sfx_match_found", "sfx_timer_warning", "sfx_timer_warning_last", "sfx_time_out",
                "sfx_chat_message", "sfx_quick_phrase", "sfx_error", "sfx_opponent_guessed"]


def load(cue_id):
    x, sr = sf.read(OUT / f"{cue_id}.ogg", dtype="float64", always_2d=True)
    assert sr == SR, f"{cue_id}: sample rate {sr}"
    x = x.T
    return x[0] if x.shape[0] == 1 else x, x.shape[0]


def mono(x):
    return x if x.ndim == 1 else x.mean(axis=0)


def resample_rate(x, rate):
    """SoundPool-style playback rate: pitch and speed change together."""
    if abs(rate - 1.0) < 1e-6:
        return x
    idx = np.arange(0, x.size - 1, rate)
    return np.interp(idx, np.arange(x.size), x)


def _hf(xc, ctx=8192):
    """>6 kHz content of a loop with circular context so the filter has settled at sample 0."""
    y = np.concatenate([xc[:, -ctx:], xc], axis=1)
    return np.max(np.abs(dsp.highpass(y, 6000, 4)), axis=0)[ctx:]


def _seam_at(xc, j):
    """Seam statistics for a join at sample j of signal xc (2, N): the material before j runs into j."""
    N = xc.shape[1]
    W = 88                                              # 2 ms
    hf = _hf(xc)
    blocks = hf[: (N // W) * W].reshape(-1, W).max(axis=1)
    p99 = float(np.percentile(blocks, 99))
    seam = float(hf[np.r_[j - W:j + W] % N].max())
    d2 = np.max(np.abs(np.diff(np.concatenate([xc, xc[:, :2]], axis=1), 2, axis=1)), axis=0)
    d2b = d2[: (N // 64) * 64].reshape(-1, 64).max(axis=1)
    d2_ratio = float(d2[np.r_[j - 65:j + 63] % N].max() / np.percentile(d2b, 99))
    return dict(hf_peak_db=20 * np.log10(max(seam, 1e-9)), hf_ratio=seam / max(p99, 1e-12), d2_ratio=d2_ratio)


def seam_metrics(x):
    """Click detector at the loop point (end joined to start, i.e. sample 0 of the looped signal).
    A click is a burst of high frequencies: compare the >6 kHz peak within 2 ms of the seam with the
    loop's own 99th percentile of 2 ms block peaks, and with an absolute floor."""
    xc = x if x.ndim == 2 else x[None, :]
    m = _seam_at(xc, 0)
    m["step"] = float(np.max(np.abs(xc[:, 0] - xc[:, -1])))
    m_end = dsp.integrated_loudness(xc[:, -int(0.4 * SR):])
    m_start = dsp.integrated_loudness(xc[:, : int(0.4 * SR)])
    m["level_jump"] = float(m_start - m_end)
    return m


def seam_ok(m):
    return (m["hf_ratio"] <= SEAM_RATIO_MAX or m["hf_peak_db"] <= SEAM_FLOOR_DB) and abs(m["level_jump"]) <= 3


def seam_selftest(x):
    """Prove the detector catches a real splice: the loop cut at 55 % and rejoined at 80 % of its length."""
    xc = x if x.ndim == 2 else x[None, :]
    N = xc.shape[1]
    a, b = int(N * 0.55), int(N * 0.8)
    bad = np.concatenate([xc[:, :a], xc[:, b:]], axis=1)
    m = _seam_at(bad, a)
    return dict(st_hf_peak_db=m["hf_peak_db"], st_hf_ratio=m["hf_ratio"], st_pass=bool(seam_ok(m | {"level_jump": 0})))


def measure(cue):
    cid = cue["id"]
    x, ch = load(cid)
    music = cue["group"] == "music"
    L = x.shape[-1]
    absx = np.abs(x) if x.ndim == 1 else np.max(np.abs(x), axis=0)
    above = np.nonzero(absx >= LEAD_THRESH)[0]
    lead_ms = (above[0] / SR * 1000) if above.size else float("inf")
    aud = np.nonzero(absx >= 10 ** (AUDIBLE_DB / 20))[0]
    aud_ms = ((aud[-1] + 1) / SR * 1000) if aud.size else 0.0
    r = dict(
        id=cid, group=cue["group"], channels=ch, samples=int(L), dur_ms=L / SR * 1000, aud_ms=aud_ms,
        size_kb=(OUT / f"{cid}.ogg").stat().st_size / 1024,
        m_max=dsp.sliding_max_loudness(x, 0.4), s_max=dsp.sliding_max_loudness(x, 3.0),
        integrated=dsp.integrated_loudness(x), tp=dsp.true_peak_db(x), sp=dsp.sample_peak_db(x),
        dc=float(np.max(np.abs(np.mean(x, axis=-1)))), clipped=int(np.sum(np.abs(x) >= 0.999)),
        lead_ms=lead_ms, end_db=dsp.sample_peak_db(x[..., -int(0.002 * SR):]),
        target=cue["target"],
    )
    r["metric"] = "I" if music else "M-max"
    r["measured"] = r["integrated"] if music else r["m_max"]
    r["dev"] = r["measured"] - cue["target"]
    hp = dsp.highpass(x, 300, 2)
    r["phone_loss"] = (r["m_max"] - dsp.sliding_max_loudness(hp, 0.4)) if not music else \
        (r["integrated"] - dsp.integrated_loudness(hp))
    checks = {
        "loudness": abs(r["dev"]) <= TOL_LU,
        "true_peak": r["tp"] <= TP_MAX,
        "dc": r["dc"] <= DC_MAX,
        "clipping": r["clipped"] == 0,
        "duration": r["aud_ms"] <= cue["max_ms"],
        "format": (ch == 1) if not music else (ch in (1, 2)),
    }
    if not music:
        checks["lead_silence"] = r["lead_ms"] < LEAD_MAX_MS
        checks["end_click"] = r["end_db"] <= END_MAX_DB
    else:
        seam = seam_metrics(x)
        seam.update(seam_selftest(x))
        r["seam"] = seam
        checks["loop_seam"] = seam_ok(seam)
        st = dsp.loudness_curve(x, 3.0, 1.0)
        r["s_range"] = (float(np.min(st)), float(np.max(st)))
    r["checks"] = checks
    r["warn_phone"] = r["phone_loss"] > PHONE_LOSS_WARN and not music
    r["pass"] = all(checks.values())
    return r, x


def render_sequence(name, cache):
    seq = SEQUENCES[name]
    end = max(t + cache[c].size / SR / r for t, c, r, g in seq["events"]) + 0.05
    out = np.zeros(int(end * SR) + 1)
    for t, c, rate, g in seq["events"]:
        y = resample_rate(cache[c], rate) * g
        out = dsp.place(out, y, t)
    return out


# ----------------------------------------------------------------------------- plots
def spectrogram_png(x, path, title, max_s=None, fmax=16000):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from scipy import signal

    x = mono(x)
    if max_s:
        x = x[: int(max_s * SR)]
    dur = x.size / SR
    nfft = 1024 if dur < 3 else 4096
    hop = 64 if dur < 0.6 else (128 if dur < 3 else 1024)
    xp = np.pad(x, (nfft // 2, nfft // 2))
    f, t, Z = signal.stft(xp, SR, window="hann", nperseg=nfft, noverlap=nfft - hop, boundary=None, padded=True)
    t = t - (nfft / 2) / SR
    S = 20 * np.log10(np.abs(Z) + 1e-9)
    S -= S.max()
    fig = plt.figure(figsize=(6.0, 2.9), dpi=100)
    fig.patch.set_facecolor("#0C1210")
    gs = fig.add_gridspec(2, 1, height_ratios=[1, 3], hspace=0.08)
    ax0 = fig.add_subplot(gs[0])
    ax1 = fig.add_subplot(gs[1], sharex=ax0)
    tt = np.arange(x.size) / SR
    ax0.plot(tt, x, color="#F5B83D", lw=0.6)
    ax0.set_ylim(-1, 1)
    ax0.axhline(10 ** (-1 / 20), color="#FF6359", lw=0.5, ls=":")
    ax0.axhline(-(10 ** (-1 / 20)), color="#FF6359", lw=0.5, ls=":")
    ax0.set_yticks([])
    ax0.set_title(title, color="#ECEFE6", fontsize=9, loc="left")
    ax1.pcolormesh(t, np.maximum(f, 1), S, vmin=-75, vmax=0, cmap="magma", shading="auto")
    ax1.set_yscale("log")
    ax1.set_ylim(80, fmax)
    ax1.set_yticks([100, 300, 1000, 3000, 10000])
    ax1.set_yticklabels(["100", "300", "1k", "3k", "10k"])
    ax1.set_xlim(0, dur)
    ax1.set_xlabel("s", color="#A5B1AA", fontsize=7, labelpad=1)
    for ax in (ax0, ax1):
        ax.set_facecolor("#131A17")
        ax.tick_params(colors="#A5B1AA", labelsize=7)
        for s in ax.spines.values():
            s.set_color("#2A3530")
    plt.setp(ax0.get_xticklabels(), visible=False)
    fig.subplots_adjust(left=0.08, right=0.98, top=0.9, bottom=0.12)
    fig.savefig(path, facecolor=fig.get_facecolor())
    plt.close(fig)


def seam_png(x, path, title):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    xc = x if x.ndim == 2 else x[None, :]
    k = int(0.01 * SR)
    w = np.concatenate([xc[:, -k:], xc[:, :k]], axis=1)
    t = (np.arange(w.shape[1]) - k) / SR * 1000
    fig, ax = plt.subplots(figsize=(6.0, 1.8), dpi=100)
    fig.patch.set_facecolor("#0C1210")
    ax.set_facecolor("#131A17")
    for c, col in zip(w, ["#F5B83D", "#45C98B"]):
        ax.plot(t, c, color=col, lw=0.8)
    ax.axvline(0, color="#FF6359", lw=0.6, ls=":")
    ax.set_title(title, color="#ECEFE6", fontsize=9, loc="left")
    ax.set_xlabel("ms around the loop point (end | start)", color="#A5B1AA", fontsize=7)
    ax.tick_params(colors="#A5B1AA", labelsize=7)
    for s in ax.spines.values():
        s.set_color("#2A3530")
    fig.subplots_adjust(left=0.08, right=0.98, top=0.85, bottom=0.25)
    fig.savefig(path, facecolor=fig.get_facecolor())
    plt.close(fig)


# ----------------------------------------------------------------------------- report
def fmt(v, nd=1):
    return "-inf" if v == -np.inf else f"{v:.{nd}f}"


def write_report(results, seqs, total_kb):
    sfx = [r for r in results if r["group"] != "music"]
    mus = [r for r in results if r["group"] == "music"]
    fails = [r for r in results if not r["pass"]]
    lines = []
    A = lines.append
    A("# Sound set: measurement report")
    A("")
    A("Generated by `tools/measure.py` from the encoded files in `out/` (decoded OGG Vorbis, i.e. what the app plays). "
      "Do not edit by hand; rerun the script after regenerating sounds.")
    A("")
    A("## Summary")
    A("")
    A(f"- Files: {len(results)} ({len(sfx)} effects, {len(mus)} music loops), total **{total_kb:.0f} KB** "
      f"(budget {SET_BUDGET_KB} KB): {'PASS' if total_kb <= SET_BUDGET_KB else 'FAIL'}.")
    A(f"- Checks: **{len(results) - len(fails)} / {len(results)} files pass every rule**"
      + ("." if not fails else ": failing " + ", ".join(r['id'] for r in fails) + "."))
    mm = [r["m_max"] for r in sfx]
    A(f"- Effects loudness (momentary max): {min(mm):.1f} to {max(mm):.1f} LUFS. "
      f"Music integrated: " + ", ".join(f"{r['id']} {r['integrated']:.1f} LUFS" for r in mus) + ".")
    A(f"- Highest true peak in the set: {max(r['tp'] for r in results):.1f} dBTP (rule: <= {TP_MAX:.0f} dBTP).")
    warn = [r["id"] for r in sfx if r["warn_phone"]]
    A(f"- Small-speaker check (loss through a 300 Hz high-pass > {PHONE_LOSS_WARN:.0f} LU): "
      + ("none." if not warn else ", ".join(warn) + " (warning only)."))
    A("")
    A("## Method")
    A("")
    A("- **Loudness**: ITU-R BS.1770-4 K-weighting (libebur128 filter constants, cross-checked against ffmpeg `ebur128`). "
      "*M-max* = highest loudness of any 400 ms window (sample hop, sound zero-padded so a short sound is measured whole); "
      "*S-max* = the same with a 3 s window; *I* = gated integrated loudness (files under 400 ms padded to one block). "
      "Effects are normalised and judged on **M-max**: for sounds of 30 ms to 2.5 s a 3 s short-term window mostly "
      "measures silence, so the 400 ms window is the meaningful 'short-term' figure for one-shots. Music is judged on I.")
    A("- **True peak**: 4x oversampled (polyphase), dBTP. **Clipping**: samples at |x| >= 0.999. "
      "**DC**: |mean| as a fraction of full scale.")
    A("- **Duration**: *audible ms* runs to the last sample above -60 dBFS and is checked against the cue budget; "
      "*file ms* includes the 20 ms of digital silence appended to very short effects (keeps codec noise off the end).")
    A(f"- **Lead silence**: time to the first sample above -50 dBFS (rule for effects: < {LEAD_MAX_MS:.0f} ms). "
      f"**End**: peak of the last 2 ms (rule: <= {END_MAX_DB:.0f} dBFS, so nothing is cut off with a click).")
    A("- **Phone loss**: how much loudness disappears through a 2nd-order 300 Hz high-pass, a rough model of a phone "
      "speaker. Above 6 LU means the cue relies on bass a phone cannot play.")
    A("- **Loop seam** (music): a click is a short burst of high frequencies. The file is looped (end joined to sample 0) "
      "and high-passed at 6 kHz (4th order, with circular context so the filter is settled). The seam passes if the "
      "peak within 2 ms of the join is no higher than 2x the loop's own 99th percentile of 2 ms block peaks (the join is "
      "no rougher than the music itself) **or** lies below -60 dBFS (at least 34 dB under the music's loudness, masked). "
      "Also: loudness of the last vs. the first 400 ms within 3 LU, and, for information, the second-difference ratio "
      "(|x[n+1] - 2x[n] + x[n-1]| at the join vs. its 99th percentile). Self-test: the same loop cut at 55 % and "
      "rejoined at 80 % must fail. The seam is checked on the decoded OGG because the encoder codes the last and first "
      "blocks independently; `generate.py` raises the Vorbis quality until it passes.")
    A("")
    A("## Effects")
    A("")
    A("| id | audible ms | file ms | budget ms | KB | target | M-max | dev | S-max | I | TP dBTP | DC | clip | lead ms | end dBFS | phone loss | result |")
    A("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|")
    for r in sfx:
        bad = [k for k, v in r["checks"].items() if not v]
        res = "PASS" if not bad else "FAIL: " + ", ".join(bad)
        if r["warn_phone"]:
            res += " (phone warn)"
        A(f"| `{r['id']}` | {r['aud_ms']:.0f} | {r['dur_ms']:.0f} | {CUE_BY_ID[r['id']]['max_ms']} | {r['size_kb']:.1f} | {r['target']} | {r['m_max']:.1f} | "
          f"{r['dev']:+.1f} | {fmt(r['s_max'])} | {fmt(r['integrated'])} | {r['tp']:.1f} | {r['dc']:.5f} | "
          f"{r['clipped']} | {r['lead_ms']:.1f} | {fmt(r['end_db'], 0)} | {r['phone_loss']:.1f} | {res} |")
    A("")
    A("## Music loops")
    A("")
    A("| id | channels | length | samples | KB | target I | I | S range | M-max | TP dBTP | DC | clip | result |")
    A("|---|---:|---:|---:|---:|---:|---:|---|---:|---:|---:|---:|---|")
    for r in mus:
        bad = [k for k, v in r["checks"].items() if not v]
        A(f"| `{r['id']}` | {r['channels']} | {r['dur_ms'] / 1000:.3f} s | {r['samples']} | {r['size_kb']:.0f} | "
          f"{r['target']} | {r['integrated']:.1f} | {r['s_range'][0]:.1f} to {r['s_range'][1]:.1f} | {r['m_max']:.1f} | "
          f"{r['tp']:.1f} | {r['dc']:.5f} | {r['clipped']} | {'PASS' if not bad else 'FAIL: ' + ', '.join(bad)} |")
    A("")
    A("Loop points: the whole file is the loop. **Start = sample 0, end = last sample (exclusive end = sample count above)**; "
      "loop with `isLooping = true`. Ogg Vorbis stores the exact sample count (granule position), so the decoded length "
      "equals the source length and no encoder padding has to be skipped.")
    A("")
    A("| id | HF peak at seam dBFS | HF ratio (<= 2, or peak <= -60 dBFS) | 2nd-diff ratio (info) | step | level end -> start (LU) | self-test splice: HF peak / ratio / verdict |")
    A("|---|---:|---:|---:|---:|---:|---|")
    for r in mus:
        s = r["seam"]
        A(f"| `{r['id']}` | {s['hf_peak_db']:.1f} | {s['hf_ratio']:.2f} | {s['d2_ratio']:.2f} | {s['step']:.5f} | "
          f"{s['level_jump']:+.1f} | {s['st_hf_peak_db']:.1f} dBFS / {s['st_hf_ratio']:.1f} / "
          f"{'pass (detector broken!)' if s['st_pass'] else 'fail, as it should'} |")
    A("")
    A("## Demo sequences (SoundPool-style mix)")
    A("")
    A("Overlapping cues add up in the mixer. These renders use the playback rates and gains from `cues.SEQUENCES` "
      "(lamp pitch steps included) to check that the busiest moments stay below 0 dBFS with effects volume at 100 %.")
    A("")
    A("| sequence | length s | true peak dBTP | M-max LUFS | result |")
    A("|---|---:|---:|---:|---|")
    for name, s in seqs.items():
        A(f"| {name} | {s['dur']:.2f} | {s['tp']:.1f} | {s['m_max']:.1f} | {'PASS' if s['tp'] <= -0.5 else 'FAIL'} |")
    A("")
    A("## Spectrograms")
    A("")
    A("Waveform (top, dotted lines at -1 dBFS) and log-frequency spectrogram (bottom, 75 dB range) in `spectrograms/`. "
      "Reviewed visually; notes below.")
    A("")
    for line in VISUAL_NOTES:
        A(line)
    A("")
    (ROOT / "REPORT.md").write_text("\n".join(lines) + "\n")


# Filled in after looking at the PNGs (see SOUND_DESIGN.md "How it was checked").
VISUAL_NOTES = [
    "_Visual review pending._",
]


def main():
    SPEC.mkdir(exist_ok=True)
    results, cache = [], {}
    for cue in CUES:
        r, x = measure(cue)
        results.append(r)
        cache[cue["id"]] = mono(x) if r["group"] != "music" else x
    total_kb = sum(r["size_kb"] for r in results)

    seqs = {}
    for name in SEQUENCES:
        y = render_sequence(name, cache)
        seqs[name] = dict(dur=y.size / SR, tp=dsp.true_peak_db(y), m_max=dsp.sliding_max_loudness(y, 0.4))
        spectrogram_png(y, SPEC / f"seq_{name}.png", f"sequence: {name}")
    for cid in SPECTRO_CUES:
        spectrogram_png(cache[cid], SPEC / f"{cid}.png", cid)
    for r in results:
        if r["group"] == "music":
            spectrogram_png(cache[r["id"]], SPEC / f"{r['id']}.png", f"{r['id']} (whole loop)", fmax=16000)
            seam_png(cache[r["id"]], SPEC / f"{r['id']}_seam.png", f"{r['id']}: 10 ms either side of the loop point")
    # lamp ladder: what 'S S S B B' sounds like at the stepped rates
    lad = np.zeros(1)
    for i in range(3):
        lad = dsp.place(lad, resample_rate(cache["sfx_hint_strike"], STRIKE_RATES[i]), i * 0.11)
    for i in range(2):
        lad = dsp.place(lad, resample_rate(cache["sfx_hint_ball"], BALL_RATES[i]), (3 + i) * 0.11)
    spectrogram_png(lad, SPEC / "seq_lamps_3s2b.png", "lamps 3S 2B at 110 ms stagger (rate steps)")

    write_report(results, seqs, total_kb)
    json.dump(dict(files={r["id"]: {k: v for k, v in r.items() if k != "checks"} | {"checks": r["checks"]}
                          for r in results}, sequences=seqs, total_kb=total_kb),
              open(ROOT / "measurements.json", "w"), indent=1, default=float)
    print(f"{'id':26s} {'ms':>6s} {'M-max':>6s} {'dev':>5s} {'TP':>5s} {'lead':>5s} {'phone':>5s} result")
    for r in results:
        bad = [k for k, v in r["checks"].items() if not v]
        print(f"{r['id']:26s} {r['dur_ms']:6.0f} {r['m_max']:6.1f} {r['dev']:+5.1f} {r['tp']:5.1f} "
              f"{r['lead_ms']:5.1f} {r['phone_loss']:5.1f} {'PASS' if not bad else 'FAIL ' + ','.join(bad)}"
              f"{' phone-warn' if r['warn_phone'] else ''}")
    for r in results:
        if "seam" in r:
            print(r["id"], {k: (round(v, 3) if isinstance(v, float) else v) for k, v in r["seam"].items()})
    for k, s in seqs.items():
        print("seq", k, {kk: round(v, 2) for kk, v in s.items()})
    print(f"total {total_kb:.0f} KB")
    return 0 if all(r["pass"] for r in results) and total_kb <= SET_BUDGET_KB else 1


if __name__ == "__main__":
    sys.exit(main())
