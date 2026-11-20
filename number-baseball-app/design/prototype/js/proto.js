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
    return `<div class="memo-bar"><span class="sec-label">${t('memo')}</span><span class="memo-help">${t('memoHelp')}</span><button class="btn-text">${t('memoClear')}</button><button class="iconbtn dim" style="width:36px;height:36px;margin-right:-8px" aria-label="${t('memoHide')}">${icon('keyboard_arrow_down')}</button></div><div class="memo-grid">${g}</div>`;
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
    k += `<button class="key pitch${full && !opts.waiting ? '' : ' is-disabled'}">${pitchLabel}</button>`;
    // compact (window height < 480dp): 2 rows x 6 columns
    let c = '';
    for (const d of '01234') c += `<button class="key ${keyCls(d)}">${d}</button>`;
    c += `<button class="key fn" aria-label="${t('backspace')}">${icon('backspace')}</button>`;
    for (const d of '56789') c += `<button class="key ${keyCls(d)}">${d}</button>`;
    c += `<button class="key pitch${full ? '' : ' is-disabled'}">${t('pitch')}</button>`;
    return `<div class="keypad full">${k}</div><div class="keypad compact">${c}</div>`;
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
  window.P = { digitsLabel, qs, lang, t, icon, judge, logHtml, memoHtml, slotsHtml, keypadHtml, apply };
  document.addEventListener('DOMContentLoaded', () => { if (window.build) window.build(qs); apply(); sysbar(); });
})();
