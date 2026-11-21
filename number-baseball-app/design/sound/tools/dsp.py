"""Small DSP toolkit for the Number Baseball sound set.

Everything here is synthesis from scratch (oscillators, filtered noise, synthetic reverb) plus an
ITU-R BS.1770-4 loudness meter and a true-peak meter, so the set can be regenerated and checked
without any third-party audio material. Requires numpy and scipy.
"""
from __future__ import annotations

import numpy as np
from scipy import signal

SR = 44100
A4 = 440.0


# ----------------------------------------------------------------------------- pitch helpers
NOTE_INDEX = {"C": -9, "C#": -8, "D": -7, "D#": -6, "E": -5, "F": -4, "F#": -3, "G": -2, "G#": -1,
              "A": 0, "A#": 1, "B": 2}


def hz(note: str) -> float:
    """'A4' -> 440.0, 'F#5' -> 739.99. Equal temperament, A4 = 440 Hz."""
    name, octave = note[:-1], int(note[-1])
    semis = NOTE_INDEX[name] + (octave - 4) * 12
    return A4 * 2 ** (semis / 12)


def midi_hz(m: float) -> float:
    return A4 * 2 ** ((m - 69) / 12)


def n(seconds: float) -> int:
    return int(round(seconds * SR))


def t_axis(seconds: float) -> np.ndarray:
    return np.arange(n(seconds)) / SR


# ----------------------------------------------------------------------------- envelopes
def env_exp(length: int, tau: float, attack: float = 0.0) -> np.ndarray:
    """Exponential decay with time constant tau (s) and an optional raised-cosine attack (s)."""
    t = np.arange(length) / SR
    e = np.exp(-t / max(tau, 1e-5))
    if attack > 0:
        a = n(attack)
        if a > 1:
            e[:a] *= 0.5 - 0.5 * np.cos(np.pi * np.arange(a) / a)
    return e


def env_asr(length: int, attack: float, release: float, curve: float = 1.0) -> np.ndarray:
    """Attack / sustain / release (raised cosine edges) over `length` samples."""
    e = np.ones(length)
    a, r = min(n(attack), length), min(n(release), length)
    if a > 1:
        e[:a] = (0.5 - 0.5 * np.cos(np.pi * np.arange(a) / a)) ** curve
    if r > 1:
        e[length - r:] *= (0.5 + 0.5 * np.cos(np.pi * np.arange(r) / r)) ** curve
    return e


def fade_out(x: np.ndarray, ms: float) -> np.ndarray:
    k = min(n(ms / 1000), x.shape[-1])
    if k > 1:
        x = x.copy()
        x[..., -k:] *= 0.5 + 0.5 * np.cos(np.pi * np.arange(k) / k)
    return x


def fade_in(x: np.ndarray, ms: float) -> np.ndarray:
    k = min(n(ms / 1000), x.shape[-1])
    if k > 1:
        x = x.copy()
        x[..., :k] *= 0.5 - 0.5 * np.cos(np.pi * np.arange(k) / k)
    return x


# ----------------------------------------------------------------------------- oscillators
def osc(freq, length: int, phase: float = 0.0) -> np.ndarray:
    """Sine with constant or per-sample frequency (array)."""
    if np.isscalar(freq):
        return np.sin(2 * np.pi * freq * np.arange(length) / SR + phase)
    inc = 2 * np.pi * np.asarray(freq) / SR
    return np.sin(np.cumsum(inc) - inc[0] + phase)


def partials(f0: float, seconds: float, parts, attack: float = 0.001, rng=None) -> np.ndarray:
    """Sum of decaying sine partials. parts = [(ratio, amp, tau), ...]."""
    L = n(seconds)
    out = np.zeros(L)
    for ratio, amp, tau in parts:
        ph = 0.0 if rng is None else rng.uniform(0, 2 * np.pi)
        f = f0 * ratio
        if f >= SR / 2 * 0.95:
            continue
        out += amp * osc(f, L, ph) * env_exp(L, tau)
    return out * env_exp(L, 1e9, attack)


def pitch_drop_sine(f0: float, seconds: float, drop: float, drop_tau: float, tau: float,
                    attack: float = 0.0005) -> np.ndarray:
    """Sine whose frequency falls from f0*(1+drop) to f0 (membrane / body thump)."""
    L = n(seconds)
    t = np.arange(L) / SR
    f = f0 * (1 + drop * np.exp(-t / drop_tau))
    return osc(f, L) * env_exp(L, tau, attack)


def noise(length: int, rng) -> np.ndarray:
    return rng.standard_normal(length)


# ----------------------------------------------------------------------------- filters
def _sos_apply(sos, x):
    return signal.sosfilt(sos, x, axis=-1)


def lowpass(x, fc, order=2):
    return _sos_apply(signal.butter(order, min(fc, SR * 0.45), "lowpass", fs=SR, output="sos"), x)


def highpass(x, fc, order=2):
    return _sos_apply(signal.butter(order, fc, "highpass", fs=SR, output="sos"), x)


def bandpass(x, lo, hi, order=2):
    return _sos_apply(signal.butter(order, [lo, min(hi, SR * 0.45)], "bandpass", fs=SR, output="sos"), x)


def resonator(x, f, q):
    """Two-pole resonant band-pass (constant 0 dB peak gain)."""
    b, a = signal.iirpeak(f, q, fs=SR)
    return signal.lfilter(b, a, x, axis=-1)


def dc_block(x, fc=25.0):
    return highpass(x, fc, order=2)


# ----------------------------------------------------------------------------- mixing helpers
def place(buf: np.ndarray, x: np.ndarray, at_s: float, gain: float = 1.0) -> np.ndarray:
    """Add x into buf at time at_s (grows buf if needed). Works for mono (1-D) and stereo (2, N)."""
    start = n(at_s)
    end = start + x.shape[-1]
    if end > buf.shape[-1]:
        pad = end - buf.shape[-1]
        buf = np.concatenate([buf, np.zeros(buf.shape[:-1] + (pad,))], axis=-1)
    buf[..., start:end] += gain * x
    return buf


def place_wrap(buf: np.ndarray, x: np.ndarray, start: int, gain: float = 1.0) -> None:
    """Add x into a loop buffer starting at sample `start`, wrapping past the end (seamless loops)."""
    L = buf.shape[-1]
    pos = start % L
    k = 0
    while k < x.shape[-1]:
        take = min(L - pos, x.shape[-1] - k)
        buf[..., pos:pos + take] += gain * x[..., k:k + take]
        k += take
        pos = 0


def pan(x: np.ndarray, p: float) -> np.ndarray:
    """Equal-power pan, p in [-1, 1] -> (2, N)."""
    a = (p + 1) * np.pi / 4
    return np.vstack([np.cos(a) * x, np.sin(a) * x])


def trim_tail(x: np.ndarray, floor_db: float = -66.0, min_s: float = 0.02, fade_ms: float = 8.0):
    """Cut trailing samples below floor (relative to full scale) and fade the end to zero."""
    mag = np.abs(x) if x.ndim == 1 else np.max(np.abs(x), axis=0)
    thr = 10 ** (floor_db / 20) * max(mag.max(), 1e-9)
    idx = np.nonzero(mag > thr)[0]
    end = max(n(min_s), (idx[-1] + 1) if idx.size else 0)
    end = min(end + n(fade_ms / 1000), x.shape[-1])
    return fade_out(x[..., :end], fade_ms)


# ----------------------------------------------------------------------------- reverb
def reverb_ir(seconds: float, rt60: float, seed: int, stereo: bool = False, lp: float = 5000.0,
              predelay_ms: float = 12.0, early: bool = True) -> np.ndarray:
    """Synthetic room: exponentially decaying noise, darker as it decays, plus a few early taps."""
    rng = np.random.default_rng(seed)
    L = n(seconds)
    ch = 2 if stereo else 1
    t = np.arange(L) / SR
    decay = np.exp(-6.9078 * t / rt60)
    out = np.zeros((ch, L))
    for c in range(ch):
        nz = rng.standard_normal(L) * decay
        bright = lowpass(nz, lp, 1)
        dark = lowpass(nz, lp * 0.3, 1)
        mix = np.clip(t / rt60, 0, 1)
        out[c] = bright * (1 - mix) + dark * mix
        if early:
            for d_ms, g in [(7, 0.5), (13, 0.35), (19, 0.3), (29, 0.22), (37, 0.15)]:
                k = n((d_ms + rng.uniform(-2, 2)) / 1000)
                out[c, k] += g * (1 if rng.random() > 0.5 else -1) * 3.0
    pd = n(predelay_ms / 1000)
    out = np.concatenate([np.zeros((ch, pd)), out], axis=1)
    out /= np.sqrt(np.sum(out ** 2) / ch)
    return out if stereo else out[0]


def add_reverb(x: np.ndarray, ir: np.ndarray, wet: float) -> np.ndarray:
    """Mono in -> mono out (mono IR) or stereo out (stereo IR). Output is longer by the IR length."""
    if ir.ndim == 1:
        w = signal.fftconvolve(x, ir)
        dry = np.concatenate([x, np.zeros(w.size - x.size)])
        return dry + wet * w
    if x.ndim == 1:
        x = np.vstack([x, x])
    w = np.vstack([signal.fftconvolve(x[0], ir[0]), signal.fftconvolve(x[1], ir[1])])
    dry = np.concatenate([x, np.zeros((2, w.shape[1] - x.shape[1]))], axis=1)
    return dry + wet * w


# ----------------------------------------------------------------------------- loudness (BS.1770-4)
def _k_weighting_sos(fs: int = SR) -> np.ndarray:
    """K-weighting pre-filter + RLB high-pass, same constants as libebur128 (exact for any fs)."""
    f0, G, Q = 1681.974450955533, 3.999843853973347, 0.7071752369554196
    K = np.tan(np.pi * f0 / fs)
    Vh = 10 ** (G / 20)
    Vb = Vh ** 0.4996667741545416
    a0 = 1 + K / Q + K * K
    b1 = [(Vh + Vb * K / Q + K * K) / a0, 2 * (K * K - Vh) / a0, (Vh - Vb * K / Q + K * K) / a0]
    a1 = [1.0, 2 * (K * K - 1) / a0, (1 - K / Q + K * K) / a0]
    f0, Q = 38.13547087602444, 0.5003270373238773
    K = np.tan(np.pi * f0 / fs)
    d = 1 + K / Q + K * K
    b2 = [1.0, -2.0, 1.0]
    a2 = [1.0, 2 * (K * K - 1) / d, (1 - K / Q + K * K) / d]
    return np.array([b1 + a1, b2 + a2])


_KSOS = _k_weighting_sos()


def _as_channels(x: np.ndarray) -> np.ndarray:
    return x[None, :] if x.ndim == 1 else x


def _kpower(x: np.ndarray, pad_before: int, pad_after: int) -> np.ndarray:
    """Per-sample channel-summed K-weighted power, with zero padding on both sides."""
    xc = _as_channels(x)
    xc = np.pad(xc, ((0, 0), (pad_before, pad_after)))
    y = signal.sosfilt(_KSOS, xc, axis=-1)
    return np.sum(y ** 2, axis=0)


def _lufs(ms):
    return -0.691 + 10 * np.log10(np.maximum(ms, 1e-20))


def sliding_max_loudness(x: np.ndarray, window_s: float) -> float:
    """Max loudness over every window position (sample hop). Window 0.4 s = momentary, 3 s = short-term.
    The signal is zero-padded so a sound shorter than the window is measured whole wherever it sits."""
    w = n(window_s)
    p = _kpower(x, w, w)
    c = np.concatenate([[0.0], np.cumsum(p)])
    ms = (c[w:] - c[:-w]) / w
    return float(_lufs(ms.max()))


def loudness_curve(x: np.ndarray, window_s: float, hop_s: float = 0.1) -> np.ndarray:
    w, h = n(window_s), n(hop_s)
    p = _kpower(x, 0, 0)
    c = np.concatenate([[0.0], np.cumsum(p)])
    starts = np.arange(0, max(1, p.size - w + 1), h)
    ms = (c[np.minimum(starts + w, p.size)] - c[starts]) / w
    return _lufs(ms)


def integrated_loudness(x: np.ndarray) -> float:
    """Gated integrated loudness (400 ms blocks, 75 % overlap, -70 LUFS absolute and -10 LU relative
    gates). Files shorter than one block are zero-padded to 400 ms."""
    w, h = n(0.4), n(0.1)
    L = x.shape[-1]
    pad_after = max(0, w - L)
    if L > w:
        pad_after = (h - (L - w) % h) % h
    p = _kpower(x, 0, pad_after)
    c = np.concatenate([[0.0], np.cumsum(p)])
    starts = np.arange(0, p.size - w + 1, h)
    ms = (c[starts + w] - c[starts]) / w
    lk = _lufs(ms)
    g1 = ms[lk > -70]
    if g1.size == 0:
        return -np.inf
    rel = _lufs(g1.mean()) - 10
    g2 = ms[(lk > -70) & (lk > rel)]
    return float(_lufs(g2.mean()))


def true_peak_db(x: np.ndarray, oversample: int = 4) -> float:
    xc = _as_channels(x)
    up = signal.resample_poly(np.pad(xc, ((0, 0), (64, 64))), oversample, 1, axis=-1)
    return float(20 * np.log10(max(np.abs(up).max(), 1e-12)))


def sample_peak_db(x: np.ndarray) -> float:
    return float(20 * np.log10(max(np.abs(x).max(), 1e-12)))


# ----------------------------------------------------------------------------- dynamics / normalisation
def limiter(x: np.ndarray, ceiling_db: float, lookahead_ms: float = 1.5, release_ms: float = 60.0,
            oversample: int = 4) -> np.ndarray:
    """Look-ahead peak limiter on the (oversampled) true-peak envelope. Gentle; only touches peaks."""
    xc = _as_channels(x).astype(float)
    ceiling = 10 ** (ceiling_db / 20)
    up = signal.resample_poly(xc, oversample, 1, axis=-1)
    pk = np.max(np.abs(up), axis=0).reshape(-1, oversample).max(axis=1)[: xc.shape[-1]]
    pk = np.pad(pk, (0, xc.shape[-1] - pk.size))
    need = np.minimum(1.0, ceiling / np.maximum(pk, 1e-12))
    la = max(1, n(lookahead_ms / 1000))
    # running minimum over the look-ahead window (so gain is already down when the peak arrives)
    padded = np.concatenate([need, np.ones(la)])
    win = np.lib.stride_tricks.sliding_window_view(padded, la + 1)[: need.size]
    g = win.min(axis=1)
    # smooth: instant attack (already looked ahead), exponential release
    rel = np.exp(-1.0 / (release_ms / 1000 * SR))
    out_g = np.empty_like(g)
    cur = 1.0
    for i, v in enumerate(g):
        cur = v if v < cur else v + (cur - v) * rel
        out_g[i] = cur
    # short attack smoothing (avoid zipper): symmetric moving average over the look-ahead length
    k = np.ones(la) / la
    out_g = np.minimum(out_g, np.convolve(out_g, k, mode="same"))
    y = xc * out_g
    return y[0] if x.ndim == 1 else y


def normalize(x: np.ndarray, target_lufs: float, metric: str, tp_ceiling: float = -2.0) -> np.ndarray:
    """Gain to target loudness (metric 'momentary' = max 400 ms loudness, 'integrated' = gated I),
    then limit true peaks above tp_ceiling and re-trim the gain (2 passes)."""
    meas = (lambda s: sliding_max_loudness(s, 0.4)) if metric == "momentary" else integrated_loudness
    g0 = target_lufs - meas(x)
    y = x * 10 ** (g0 / 20)
    if true_peak_db(y) <= tp_ceiling:
        return y

    def render(gain_db):
        return limiter(x * 10 ** (gain_db / 20), tp_ceiling - 0.3)

    # loudness after limiting rises monotonically with input gain: bisect for the target
    lo, hi = g0, g0 + 12.0
    for _ in range(18):
        mid = 0.5 * (lo + hi)
        if meas(render(mid)) < target_lufs:
            lo = mid
        else:
            hi = mid
    return render(hi)
