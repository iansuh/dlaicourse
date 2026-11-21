/* Prototype runtime: language, icons, system bars, and builders shared by match screens.
   Query: ?lang=ko|en|de|ru  (+ page-specific params). */
(function () {
  const qs = new URLSearchParams(location.search);
  const lang = qs.get('lang') || 'ko';
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

  const hintCells = (h, n, latest) => {
    if (h.s === 0 && h.b === 0) {
      return `<span class="ho">${latest ? `<span class="cell-stack"><span class="h-out">OUT</span><span class="lamps isolate"><span class="lamp s on ou"></span></span></span>` : '<span class="h-out">OUT</span>'}</span>`;
    }
    const cell = (v, k, cls, lampCls) => {
      const txt = `<span class="hint ${v ? cls : 'h-zero'}">${v}${k}</span>`;
      if (!latest) return txt;
      let l = ''; for (let i = 0; i < n; i++) l += `<span class="lamp s${i < v ? ' on ' + lampCls : ''}"></span>`;
      return `<span class="cell-stack">${txt}<span class="lamps">${l}</span></span>`;
    };
    return `<span class="hs">${cell(h.s, 'S', 'h-s', 'st')}</span><span class="hb">${cell(h.b, 'B', 'h-b', 'bl')}</span>`;
  };
  const oppCell = (h) => (h.s === 0 && h.b === 0) ? '<span class="h-out" style="height:20px;font-size:12px">OUT</span>'
    : `<span class="${h.s ? '' : 'h-zero'}">${h.s}S</span> <span class="${h.b ? '' : 'h-zero'}">${h.b}B</span>`;

  function logHtml(m) {
    const n = m.secretOpp.length;
    let rows = '';
    for (let i = m.mine.length - 1; i >= 0; i--) {
      const g = m.mine[i], h = judge(m.secretOpp, g), og = m.theirs[i], oh = og ? judge(m.secretMine, og) : null;
      const latest = i === m.mine.length - 1;
      rows += `<li class="log-row${latest ? ' latest' : ''}"><span class="r">${i + 1}</span><span class="g num">${g}</span>${hintCells(h, n, latest)}<span class="op">${oh ? oppCell(oh) : ''}</span></li>`;
    }
    return `<div class="log-head"><span>#</span><span>${t('colGuess')}</span><span class="h-hint">${t('colHint')}</span><span class="h-opp">${t('colOpp')}</span></div><ol class="log-rows">${rows}</ol>`;
  }

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
    const empty = !Object.keys(m.memo).length;
    return `<div class="memo-bar"><span class="sec-label">${t('memo')}</span><span class="memo-help">${empty ? t('memoHelp') : ''}</span><button class="btn-text">${t('memoClear')}</button><button class="iconbtn dim" style="width:36px;height:36px;margin-right:-8px" aria-label="${t('memoHide')}">${icon('keyboard_arrow_down')}</button></div><div class="memo-grid">${g}</div>`;
  }

  function slotsHtml(n, entry, opts = {}) {
    let s = '';
    for (let i = 0; i < n; i++) {
      const d = entry[i];
      const cls = opts.locked ? 'locked' : d != null ? 'filled' : (i === entry.length ? 'active' : '');
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
    const pitchLabel = opts.waiting ? `<span class="waiting-chase"><span class="lamp on ch"></span><span class="lamp"></span><span class="lamp"></span></span>${t('waitingOpp')}` : (opts.label || t('pitch'));
    let k = '';
    for (const d of '01234') k += `<button class="key ${keyCls(d)}">${d}</button>`;
    for (const d of '56789') k += `<button class="key ${keyCls(d)}">${d}</button>`;
    k += `<button class="key fn" aria-label="${t('backspace')}">${icon('backspace')}</button>`;
    if (opts.secret) k += `<button class="key fn" aria-label="${t('random')}">${icon('shuffle')}</button>`;
    k += `<button class="key pitch${opts.secret ? ' span3' : ''}${opts.waiting ? ' waiting' : full ? '' : ' is-disabled'}">${pitchLabel}</button>`;
    // compact (window height < 480dp): 2 rows x 6 columns
    let c = '';
    for (const d of '01234') c += `<button class="key ${keyCls(d)}">${d}</button>`;
    c += `<button class="key fn" aria-label="${t('backspace')}">${icon('backspace')}</button>`;
    for (const d of '56789') c += `<button class="key ${keyCls(d)}">${d}</button>`;
    c += `<button class="key pitch${full ? '' : ' is-disabled'}">${t('pitch')}</button>`;
    return `<div class="keypad full${opts.waiting ? ' locked' : ''}">${k}</div><div class="keypad compact">${c}</div>`;
  }

  // In-app brand mark: the icon's number plate, numeral and three strike lamps (viewBox crops the 108dp icon).
  const mark = () => `<svg class="brandmark" viewBox="33 24 42 63" aria-hidden="true"><rect x="36" y="28" width="36" height="44" rx="6" fill="#1A231F" stroke="#3B4943"/><rect x="40" y="49.5" width="28" height="1" fill="#0C1210" opacity=".55"/><path fill="#ECEFE6" d="M61.53 54.9Q61.53 56.64 61.38 57.87Q61.06 60.72 59.18 62.36Q57.3 64 54.14 64Q50.86 64 48.8 62.26Q46.74 60.52 46.58 57.63Q46.47 56.56 46.47 55.34Q46.47 54.86 46.94 54.86H51.57Q52.04 54.86 52.04 55.34Q52.04 56.25 52.16 57.04Q52.28 58.07 52.79 58.64Q53.31 59.21 54.14 59.21Q54.93 59.21 55.44 58.66Q55.96 58.11 56.12 57.16Q56.23 55.77 56.23 54.31Q56.23 52.45 56.04 51.38Q55.88 50.47 55.38 49.98Q54.89 49.49 54.14 49.49Q53.55 49.49 52.95 50Q52.71 50.16 52.64 50.16Q52.48 50.16 52.28 49.96L49.91 47.55Q49.75 47.39 49.75 47.19Q49.75 47.03 49.87 46.88L54.65 40.98Q54.73 40.9 54.69 40.82Q54.65 40.75 54.53 40.75H47.42Q47.22 40.75 47.08 40.61Q46.94 40.47 46.94 40.27V36.47Q46.94 36.28 47.08 36.14Q47.22 36 47.42 36H61.02Q61.22 36 61.36 36.14Q61.49 36.28 61.49 36.47V40.43Q61.49 40.75 61.3 41.02L56.87 46.6Q56.83 46.68 56.83 46.76Q56.83 46.84 56.91 46.84Q58.65 46.99 59.85 48.42Q61.06 49.84 61.34 51.98Q61.53 53.48 61.53 54.9Z"/><circle cx="44.5" cy="81.5" r="3" fill="#F5B83D"/><circle cx="54" cy="81.5" r="3" fill="#F5B83D"/><circle cx="63.5" cy="81.5" r="3" fill="#F5B83D"/></svg>`;

  // Layout fitting used by the match screen: fade the log when it overflows; collapse the memo grid
  // when the log cannot show the latest row plus one full row.
  function fit() {
    const rows = document.querySelector('.log-rows'), memo = document.querySelector('.memo');
    if (!rows) return;
    const minLog = 64 + 44;
    if (memo && !memo.classList.contains('collapsed') && rows.clientHeight < minLog && getComputedStyle(memo).display !== 'none') {
      memo.classList.add('collapsed');
      const b = memo.querySelector('.memo-bar .iconbtn'); if (b) b.innerHTML = icon('keyboard_arrow_up');
    }
    rows.classList.toggle('fade', rows.scrollHeight > rows.clientHeight + 1);
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
  window.P = { mark, fit, digitsLabel, qs, lang, t, icon, judge, logHtml, memoHtml, slotsHtml, keypadHtml, apply };
  document.addEventListener('DOMContentLoaded', () => { if (window.build) window.build(qs); apply(); sysbar(); fit(); });
  window.addEventListener('resize', fit);
})();
