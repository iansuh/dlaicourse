/* Prototype runtime (design system v2, Clean White): language, icons, system bars, and builders shared by match screens.
   Query: ?lang=ko|en|de|ru  (+ page-specific params). */
(function () {
  const qs = new URLSearchParams(location.search);
  const lang = qs.get('lang') || 'ko';
  // ?fs=2: font scale. Words scale fully (--fs), numerals cap at 1.3x (--ns, DESIGN_SYSTEM 3.3).
  const fsc = parseFloat(qs.get('fs') || '1');
  document.documentElement.style.setProperty('--fs', fsc);
  document.documentElement.style.setProperty('--ns', Math.min(fsc, 1.3));
  if (fsc >= 1.5) document.documentElement.classList.add('large');
  document.documentElement.lang = lang;
  const S = window.STR[lang] || window.STR.ko;
  const t = (k, p) => {
    let s = S[k] != null ? S[k] : (window.STR.en[k] != null && lang !== 'ko' ? window.STR.en[k] : window.STR.ko[k]);
    if (s == null) return k;
    if (p) for (const a in p) s = s.split('{' + a + '}').join(p[a]);
    return s;
  };
  const icon = (n, cls) => `<svg class="${cls || 'ic'}" viewBox="0 -960 960 960" aria-hidden="true"><path d="${window.ICONS[n] || ''}"/></svg>`;

  // Judge: strikes and balls of guess g against secret s (strings of distinct digits).
  const judge = (s, g) => { let st = 0, b = 0; for (let i = 0; i < g.length; i++) { if (s[i] === g[i]) st++; else if (s.includes(g[i])) b++; } return { s: st, b }; };

  // Hint chips (DESIGN_SYSTEM 12.6): S solid strike chip, B outlined ball chip, zero counts plain text3, 0S 0B = one wide OUT chip.
  const hintCells = (h) => (h.s === 0 && h.b === 0) ? '<span class="hints"><span class="hint h-out">OUT</span></span>'
    : `<span class="hints"><span class="hint ${h.s ? 'h-s' : 'h-zero'}">${h.s}S</span><span class="hint ${h.b ? 'h-b' : 'h-zero'}">${h.b}B</span></span>`;
  const oppText = (h) => (h.s === 0 && h.b === 0) ? 'OUT' : `${h.s}S ${h.b}B`;

  // Pitch log: no header row; newest first; only the latest row is highlighted and labels the opponent column "상대".
  function logHtml(m) {
    let rows = '';
    for (let i = m.mine.length - 1; i >= 0; i--) {
      const g = m.mine[i], h = judge(m.secretOpp, g), og = m.theirs[i], oh = og ? judge(m.secretMine, og) : null;
      const latest = i === m.mine.length - 1;
      rows += `<li class="log-row${latest ? ' latest' : ''}"><span class="r num">${i + 1}</span><span class="g num">${g}</span>${hintCells(h)}<span class="opph num">${latest && oh ? `<small>${t('colOpp')}</small>` : ''}${oh ? oppText(oh) : ''}</span></li>`;
    }
    return `<ol class="log-rows">${rows}</ol>`;
  }

  // Memo sheet grid (05 §2.4): digit status row + n position rows. Cells 40dp, header 44dp.
  function memoHtml(m) {
    const n = m.secretOpp.length, D = '0123456789';
    let g = '<span class="ml h"></span>';
    for (const d of D) { const st = m.memo[d] || ''; g += `<button class="md ${st}" aria-label="${d}">${st === 'o' ? `<b>${d}</b>` : d}</button>`; }
    for (let p = 1; p <= n; p++) {
      g += `<span class="ml">${p}</span>`;
      for (const d of D) {
        const st = m.memo[p + ':' + d] || 'u';
        g += `<button class="mc ${st}${m.memo[d] === 'x' && st !== 'o' ? ' dim' : ''}" aria-label="${p} ${d}">${st === 'o' ? `<b>${d}</b>` : ''}</button>`;
      }
    }
    return `<div class="memo-grid">${g}</div>`;
  }

  function slotsHtml(n, entry, opts = {}) {
    let s = '';
    for (let i = 0; i < n; i++) {
      const d = entry[i];
      const cls = opts.locked ? 'locked' : d != null ? 'filled' : (i === entry.length && !opts.noActive ? 'active' : '');
      s += `<div class="slot ${cls}${opts.cls ? ' ' + opts.cls : ''}">${d != null ? d : ''}</div>`;
    }
    return `<div class="slots">${s}</div>`;
  }

  function keypadHtml(m, opts = {}) {
    const keyCls = (d) => {
      const c = [];
      if (m.entry.includes(d)) c.push('used');
      if (m.memo && m.memo[d] === 'x') c.push('mx');
      if (m.memo && m.memo[d] === 'o') c.push('mo');
      return c.join(' ');
    };
    const full = m.entry.length === m.secretOpp.length;
    const pitchLabel = opts.offline ? t('pitch') : opts.waiting ? `<span class="dots"><span class="dot s accent"></span><span class="dot s"></span><span class="dot s"></span></span>${t('waitingOpp')}` : (opts.label || t('pitch'));
    let k = '';
    for (const d of '01234') k += `<button class="key ${keyCls(d)}">${d}</button>`;
    for (const d of '56789') k += `<button class="key ${keyCls(d)}">${d}</button>`;
    k += `<button class="key fn" aria-label="${t('backspace')}">${icon('backspace')}</button>`;
    if (opts.secret) k += `<button class="key fn" aria-label="${t('random')}">${icon('shuffle')}</button>`;
    k += `<button class="key pitch${opts.secret ? ' span3' : ''}${opts.offline ? ' is-disabled' : opts.waiting ? ' waiting' : full ? '' : ' is-disabled'}">${pitchLabel}</button>`;
    // compact (window height < 480dp): 2 rows x 6 columns
    let c = '';
    for (const d of '01234') c += `<button class="key ${keyCls(d)}">${d}</button>`;
    c += `<button class="key fn" aria-label="${t('backspace')}">${icon('backspace')}</button>`;
    for (const d of '56789') c += `<button class="key ${keyCls(d)}">${d}</button>`;
    c += `<button class="key pitch${full ? '' : ' is-disabled'}">${t('pitch')}</button>`;
    return `<div class="keypad full${opts.waiting || opts.offline ? ' locked' : ''}">${k}</div><div class="keypad compact">${c}</div>`;
  }

  // In-app brand mark v2: 32dp ink plate (radius 8), white "3" (the icon's numeral path), three strike dots = three strikes.
  const mark = () => `<svg class="brandmark" viewBox="0 0 32 32" aria-hidden="true"><rect width="32" height="32" rx="8" fill="#111418"/><g transform="translate(11.2 4.6) scale(0.62) translate(-46.47 -36)"><path fill="#FFFFFF" d="M61.53 54.9Q61.53 56.64 61.38 57.87Q61.06 60.72 59.18 62.36Q57.3 64 54.14 64Q50.86 64 48.8 62.26Q46.74 60.52 46.58 57.63Q46.47 56.56 46.47 55.34Q46.47 54.86 46.94 54.86H51.57Q52.04 54.86 52.04 55.34Q52.04 56.25 52.16 57.04Q52.28 58.07 52.79 58.64Q53.31 59.21 54.14 59.21Q54.93 59.21 55.44 58.66Q55.96 58.11 56.12 57.16Q56.23 55.77 56.23 54.31Q56.23 52.450 56.04 51.38Q55.88 50.47 55.38 49.98Q54.89 49.49 54.14 49.49Q53.55 49.49 52.95 50Q52.71 50.16 52.64 50.16Q52.48 50.16 52.28 49.96L49.91 47.55Q49.75 47.39 49.75 47.19Q49.75 47.03 49.87 46.88L54.65 40.98Q54.73 40.9 54.69 40.82Q54.65 40.75 54.53 40.75H47.42Q47.22 40.75 47.08 40.61Q46.94 40.47 46.94 40.27V36.47Q46.94 36.28 47.08 36.14Q47.22 36 47.42 36H61.02Q61.22 36 61.36 36.14Q61.49 36.28 61.49 36.47V40.43Q61.49 40.750 61.3 41.02L56.87 46.6Q56.83 46.68 56.83 46.76Q56.83 46.84 56.91 46.84Q58.65 46.99 59.85 48.42Q61.06 49.84 61.34 51.98Q61.53 53.48 61.53 54.9Z"/></g><circle cx="11" cy="26" r="1.7" fill="#B54708"/><circle cx="16" cy="26" r="1.7" fill="#B54708"/><circle cx="21" cy="26" r="1.7" fill="#B54708"/></svg>`;

  // Layout fitting used by the match screen: fade the log when it overflows; collapse the memo grid
  // when the log cannot show the latest row plus one full row.
  // Log fade: 20dp bottom mask when rows continue below.
  function fit() {
    document.querySelectorAll('.log-rows, .fit-scroll').forEach((rows) => rows.classList.toggle('fade', rows.scrollHeight > rows.clientHeight + 1));
  }

  function sysbar() {
    document.querySelectorAll('.sysbar').forEach((el) => {
      el.innerHTML = `<span>9:41</span><span style="display:flex;gap:6px;align-items:center">
        <svg width="16" height="12" viewBox="0 0 16 12"><path d="M8 11.5 0.6 3.6C2.6 1.9 5.2 0.9 8 0.9s5.4 1 7.4 2.7z" fill="currentColor"/></svg>
        <svg width="14" height="12" viewBox="0 0 14 12"><path d="M13 0.5v11H1.5z" fill="currentColor"/></svg>
        <svg width="22" height="12" viewBox="0 0 22 12"><rect x="0.5" y="0.5" width="18" height="11" rx="2.5" fill="none" stroke="currentColor" opacity=".5"/><rect x="2" y="2" width="13" height="8" rx="1.2" fill="currentColor"/><rect x="19.6" y="4" width="1.6" height="4" rx=".8" fill="currentColor" opacity=".5"/></svg></span>`;
    });
  }

  function apply(root) {
    (root || document).querySelectorAll('[data-t]').forEach((el) => { el.innerHTML = t(el.dataset.t, el.dataset.p ? JSON.parse(el.dataset.p) : null); });
    (root || document).querySelectorAll('[data-al]').forEach((el) => el.setAttribute('aria-label', t(el.dataset.al)));
    (root || document).querySelectorAll('[data-icon]').forEach((el) => { if (!el.querySelector('svg')) el.insertAdjacentHTML('afterbegin', icon(el.dataset.icon, el.dataset.iconCls)); });
  }

  // "3자리" with the number in the display face. Russian: 2-4 цифры, 5-6 цифр (Android: <plurals>).
  const digitsLabel = (n) => {
    let s = t('digitsN', { n: '#' });
    if (lang === 'ru' && n >= 5) s = s.replace('цифры', 'цифр');
    return s.replace('#', `<b>${n}</b>`);
  };
  // Tabular digits (DESIGN_SYSTEM 3.1, tokens "tnum": true). The CSS `font` shorthand resets font-variant-numeric and
  // font-feature-settings, so a class rule cannot hold it: every element set in the display face gets it inline.
  function tnum(root) {
    (root || document).querySelectorAll('*').forEach((el) => {
      if (/Inter NB/.test(getComputedStyle(el).fontFamily)) el.style.fontVariantNumeric = 'tabular-nums';
    });
  }
  window.P = { mark, fit, digitsLabel, qs, lang, t, icon, judge, logHtml, memoHtml, slotsHtml, keypadHtml, apply, tnum };
  document.addEventListener('DOMContentLoaded', () => { document.title = t('appTitle') + ' · ' + document.title; if (window.build) window.build(qs); apply(); sysbar(); tnum(); fit(); });
  window.addEventListener('resize', fit);
})();
