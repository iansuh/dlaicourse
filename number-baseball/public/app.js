(() => {
  'use strict';
  const $ = (s) => document.querySelector(s);
  const store = {
    get(k) { try { return localStorage.getItem('nb_' + k); } catch { return null; } },
    set(k, v) { try { localStorage.setItem('nb_' + k, v); } catch { /* storage unavailable */ } },
  };
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  // ---------- state ----------
  let lang = store.get('lang');
  if (!I18N[lang]) lang = Object.keys(I18N).find((l) => (navigator.language || '').toLowerCase().startsWith(l)) || 'en';
  const SKINS = ['light', 'dark', 'pink', 'mint', 'sheet', 'ghost'];
  let skin = store.get('skin') || (store.get('sheet') === '1' ? 'sheet' : '');
  if (!SKINS.includes(skin)) skin = window.matchMedia && matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  const pageTitle = () => (skin === 'sheet' ? 'Book1.xlsx' : skin === 'ghost' ? 'Untitled' : t('title'));
  const form = {
    name: store.get('name') || '', digits: Number(store.get('digits')) || 3, level: store.get('level') || 'normal',
    code: (new URLSearchParams(location.search).get('room') || '').toUpperCase().slice(0, 5),
  };
  form.size = Number(store.get('size')) || 1;
  form.name = '';
  let auth = store.get('auth') || '';   // login token
  let acct = null;                       // signed-in account {name, stats}; null while playing as a guest
  let view = null;      // what is on screen: the vs-computer game if one is running, else the online room
  let ov = null;        // online room view pushed by the server
  let local = null;     // vs-computer game state (also used to practise while waiting in a room)
  let roomList = [], roomsKey = '', notice = '';
  const coarse = window.matchMedia && matchMedia('(pointer: coarse)').matches;
  let sess = null;      // online session {room, pid, token}
  let es = null, esErrors = 0;
  let err = '', copied = false, memo = {};
  const wipe = new Set();

  const t = (k, vars) => {
    let s = (I18N[lang] && I18N[lang][k]) || I18N.en[k] || k;
    if (vars) for (const v in vars) s = s.replace('{' + v + '}', vars[v]);
    return s;
  };
  const validNum = (v, n) => v.length === n && /^\d+$/.test(v) && new Set(v).size === n;
  function judge(secret, guess) {
    let s = 0, hit = 0;
    for (let i = 0; i < guess.length; i++) {
      if (guess[i] === secret[i]) s++;
      if (secret.includes(guess[i])) hit++;
    }
    return { s, b: hit - s };
  }
  function randomNum(n) {
    const d = [...'0123456789'];
    let out = '';
    while (out.length < n) out += d.splice(Math.floor(Math.random() * d.length), 1)[0];
    return out;
  }

  // ---------- vs computer ----------
  const permCache = {};
  function perms(n) {
    if (permCache[n]) return permCache[n];
    const out = [];
    (function rec(cur) {
      if (cur.length === n) { out.push(cur); return; }
      for (let d = 0; d < 10; d++) if (!cur.includes(d)) rec(cur + d);
    })('');
    return (permCache[n] = out);
  }
  function cpuGuess() {
    const hist = local.hist[1], n = local.digits;
    // Easy forgets all but its last two hints; Normal sometimes throws a wild pitch; Hard uses everything.
    if (local.level === 'normal' && hist.length && Math.random() < 0.25) return randomNum(n);
    const used = local.level === 'easy' ? hist.slice(-2) : hist;
    const tried = new Set(hist.map((h) => h.g));
    const ok = perms(n).filter((c) => !tried.has(c) && used.every((h) => { const r = judge(c, h.g); return r.s === h.s && r.b === h.b; }));
    return ok.length ? ok[Math.floor(Math.random() * ok.length)] : randomNum(n);
  }
  function startLocal(digits) {
    local = { digits, level: form.level, secret: null, cpuSecret: randomNum(digits), hist: [[], []], done: false, outcome: null };
    memo = {};
    syncLocal();
  }
  function syncLocal() {
    const L = local, w = L.outcome === 'win' ? 0 : L.outcome === 'lose' ? 1 : L.outcome;
    L.view = {
      local: true, digits: L.digits, phase: L.done ? 'done' : 'play', host: 'me', you: 'me',
      players: [{ id: 'me', name: form.name || t('me'), team: 'A', online: true }, { id: 'cpu', name: t('cpu'), team: 'B', online: true }],
      duels: [{ a: 'me', b: 'cpu', rounds: L.hist[0].length, winner: w, done: L.done }],
      score: { A: +(w === 0), B: +(w === 1) }, result: !L.done ? null : w === 0 ? 'A' : w === 1 ? 'B' : 'draw',
      duel: {
        done: L.done, outcome: L.outcome,
        me: { secret: L.secret, hist: L.hist[0], pending: false },
        opp: { id: 'cpu', name: t('cpu'), hasSecret: true, hist: L.hist[1], pending: false, secret: L.done ? L.cpuSecret : null },
      },
    };
  }
  function localAct(type, value) {
    const L = local;
    if (type === 'secret') {
      L.secret = value;
    } else if (type === 'guess') {
      const cg = cpuGuess();
      const mine = judge(L.cpuSecret, value), theirs = judge(L.secret, cg);
      L.hist[0].push({ g: value, s: mine.s, b: mine.b });
      L.hist[1].push({ g: cg, s: theirs.s, b: theirs.b });
      const a = mine.s === L.digits, b = theirs.s === L.digits;
      if (a || b) { L.done = true; L.outcome = a && b ? 'draw' : a ? 'win' : 'lose'; }
    } else if (type === 'forfeit') {
      L.done = true; L.outcome = 'lose';
    }
    syncLocal();
    render();
  }

  // ---------- online ----------
  async function post(route, body) {
    let res;
    try {
      res = await fetch('api/' + route, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    } catch { throw new Error('errNet'); }
    const ct = res.headers.get('content-type') || '';
    if (!ct.includes('json')) throw new Error('errNet'); // static hosting without the game server
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'errNet');
    return data;
  }
  function connect() {
    if (es) es.close();
    esErrors = 0;
    es = new EventSource(`api/events?room=${sess.room}&pid=${sess.pid}&token=${sess.token}`);
    es.onmessage = (e) => {
      esErrors = 0;
      const was = ov && ov.duel && ov.duel.me.secret;
      ov = JSON.parse(e.data);
      if (local && ov.phase !== 'lobby') { local = null; memo = {}; notice = 'matchFound'; wipe.add('gIn').add('sIn'); }
      if (!local) {
        if (was && ov.duel && !ov.duel.me.secret) memo = {};
        if (ov.phase === 'lobby') memo = {};
      }
      render();
    };
    es.onerror = async () => {
      if (++esErrors < 2 || !sess) return;
      // EventSource retries by itself; only give up when the server says the seat is gone.
      try {
        const r = await fetch(`api/check?room=${sess.room}&pid=${sess.pid}&token=${sess.token}`);
        if (r.status === 404) goHome('errRoom');
      } catch { /* offline – keep retrying */ }
    };
  }
  function setSess(s) {
    sess = s;
    try { s ? sessionStorage.setItem('nb_sess', JSON.stringify(s)) : sessionStorage.removeItem('nb_sess'); } catch { /* ignore */ }
  }
  function goHome(e) {
    if (es) { es.close(); es = null; }
    setSess(null);
    ov = null; local = null; memo = {}; notice = ''; err = e || '';
    history.replaceState(null, '', location.pathname);
    render();
  }
  async function enterRoom(route) {
    try {
      const body = route === 'create' ? { auth, digits: form.digits, size: form.size } : { auth, room: form.code };
      const s = await post(route, body);
      err = '';
      setSess(s);
      history.replaceState(null, '', '?room=' + s.room);
      connect();
    } catch (e) {
      if (e.message === 'errLogin') signOut();
      err = e.message; render();
    }
  }
  function setAccount(data) {
    if (data.auth) { auth = data.auth; store.set('auth', auth); }
    acct = { name: data.name, stats: data.stats };
    form.name = data.name; form.in = true;
  }
  function signOut() {
    auth = ''; store.set('auth', ''); acct = null; form.name = ''; form.in = false; roomList = []; roomsKey = '';
  }
  async function refreshMe() {
    if (!auth) return;
    try {
      const r = await fetch('api/me', { headers: { Authorization: 'Bearer ' + auth } });
      if (r.status === 401) signOut();
      else if (r.ok && (r.headers.get('content-type') || '').includes('json')) setAccount(await r.json());
      else return;
    } catch { return; }
    if (!view) render();
  }
  async function authCall(route) {
    const u = $('#fUser'), p = $('#fPass');
    if (!u || !p) return;
    try {
      const data = await post(route, { username: u.value.trim(), password: p.value });
      err = ''; wipe.add('fUser').add('fPass');
      setAccount(data);
      render(); pollRooms();
    } catch (e) { err = e.message; render(); }
  }
  async function act(type, extra) {
    err = ''; notice = '';
    if (local && ['secret', 'guess', 'forfeit'].includes(type)) return localAct(type, extra && extra.value);
    try { await post('action', { ...sess, type, ...extra }); } catch (e) {
      if (e.message === 'errRoom') return goHome('errRoom');
      err = e.message; render();
    }
  }

  // ---------- rendering ----------
  const hint = (h) => (h.s === 0 && h.b === 0 ? '<span class="out">OUT</span>' : `<span class="${h.s ? 'hit' : ''}">${h.s}S ${h.b}B</span>`);
  const pname = (id) => { const p = view.players.find((x) => x.id === id); return p ? p.name : '?'; };
  const teamName = (k) => t(k === 'A' ? 'teamA' : 'teamB');
  const errLine = () => (err ? `<p class="err">${esc(t(err, { n: view ? view.digits : form.digits }))}</p>` : '');
  const opts = (list, cur) => list.map(([v, label]) => `<option value="${v}"${String(v) === String(cur) ? ' selected' : ''}>${esc(label)}</option>`).join('');
  const digitOpts = (cur) => opts([3, 4, 5, 6].map((n) => [n, n]), cur);

  const sizeOpts = (cur) => opts([1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((n) => [n, `${n} : ${n}`]), cur);
  function loginHtml() {
    return `<section class="card">
      <p class="sub">${esc(t('sub'))}</p>
      <p class="status">${esc(t('loginHint'))}</p>
      <div class="row"><input id="fUser" class="grow" maxlength="12" placeholder="${esc(t('userId'))}" autocomplete="username" autocapitalize="off" spellcheck="false"></div>
      <div class="row"><input id="fPass" class="grow" type="password" maxlength="72" placeholder="${esc(t('password'))}" autocomplete="current-password"></div>
      ${errLine()}
      <div class="row">
        <button class="primary grow" data-act="signin">${esc(t('login'))}</button>
        <button class="grow" data-act="signup">${esc(t('signup'))}</button>
      </div>
      <div class="row between"><button class="link" data-act="guest">${esc(t('guest'))}</button><button class="link" data-act="rules">${esc(t('rules'))}</button></div>
    </section>`;
  }
  function homeHtml() {
    const cpu = `<h3>${esc(t('vsCpu'))}</h3>
      <div class="row">
        ${acct ? '' : `<label>${esc(t('digits'))}<select id="fDigits">${digitOpts(form.digits)}</select></label>`}
        <select id="fLevel" aria-label="${esc(t('level'))}">${opts([['easy', t('easy')], ['normal', t('normal')], ['hard', t('hard')]], form.level)}</select>
        <button class="grow${acct ? '' : ' primary'}" data-act="cpu">${esc(t('start'))}</button>
      </div>
      <div class="row"><button class="link" data-act="rules">${esc(t('rules'))}</button></div>`;
    if (!acct) {
      return `<section class="card">
        <div class="row between"><span class="status">${esc(t('loginHint'))}</span><button class="link" data-act="logout">${esc(t('login'))}</button></div>
        ${cpu}</section>`;
    }
    const list = roomList.length
      ? `<ul class="rooms">${roomList.map((r) => `<li><span class="grow"><b>${esc(r.host)}</b> <small>${esc(t('digits'))} ${r.digits} · ${r.size}:${r.size} · ${r.count}/${r.size * 2}</small></span><button data-act="joinRoom" data-v="${esc(r.code)}">${esc(t('join'))}</button></li>`).join('')}</ul>`
      : `<p class="status">${esc(t('noRooms'))}</p>`;
    return `<section class="card">
      <div class="row between"><span>👤 <b>${esc(acct.name)}</b> <small class="rec">${esc(t('record', acct.stats))}</small></span><button class="link" data-act="logout">${esc(t('logout'))}</button></div>
      <h3>${esc(t('rooms'))}</h3>
      ${list}
      <div class="row">
        <input id="fCode" class="grow code" maxlength="5" placeholder="${esc(t('roomCode'))}" value="${esc(form.code)}" autocomplete="off">
        <button data-act="join">${esc(t('join'))}</button>
      </div>
      ${errLine()}
      <h3>${esc(t('create'))}</h3>
      <div class="row">
        <label>${esc(t('digits'))}<select id="fDigits">${digitOpts(form.digits)}</select></label>
        <label>${esc(t('teamSize'))}<select id="fSize">${sizeOpts(form.size)}</select></label>
        <button class="primary grow" data-act="create">${esc(t('create'))}</button>
      </div>
      ${cpu}
    </section>`;
  }
  function chatHtml() {
    if (!sess || !ov) return '';
    const log = ov.chat.map((m) => `<p><b>${esc(m.n)}</b> ${esc(m.t)}</p>`).join('');
    return `<section class="card chat"><h3>${esc(t('chat'))}</h3><div id="chatLog">${log}</div>
      <div class="row"><input id="cIn" class="grow" maxlength="200" placeholder="${esc(t('chatPh'))}" autocomplete="off"><button data-act="chat">➤</button></div><p class="tiny">${esc(t('chatRule'))}</p></section>`;
  }

  function lobbyHtml() {
    const host = view.host === view.you;
    const me = view.players.find((p) => p.id === view.you) || {};
    const col = (k) => {
      const list = view.players.filter((p) => p.team === k);
      return `<div class="team team${k}"><h3>${esc(teamName(k))} <small>(${list.length})</small></h3>
        <ul>${list.map((p) => `<li class="${p.id === view.you ? 'you' : ''}${p.online ? '' : ' off'}">${esc(p.name)}${p.id === view.host ? ` <small>${esc(t('host'))}</small>` : ''}${p.online ? '' : ` <small>${esc(t('offline'))}</small>`}</li>`).join('')}</ul>
        ${me.team !== k ? `<button data-act="team" data-v="${k}">${esc(t('join'))}</button>` : ''}</div>`;
    };
    return `<section class="card">
      <div class="row between">
        <span>${esc(t('roomCode'))}: <b class="code">${esc(view.code)}</b></span>
        <button data-act="copy">${esc(t(copied ? 'copied' : 'copyLink'))}</button>
      </div>
      <div class="row"><label>${esc(t('digits'))}
        ${host ? `<select id="rDigits">${digitOpts(view.digits)}</select>` : ` <b>${view.digits}</b>`}</label>
        <label>${esc(t('teamSize'))}
        ${host ? `<select id="rSize">${sizeOpts(view.size)}</select>` : ` <b>${view.size} : ${view.size}</b>`}</label></div>
      <p class="tiny">${esc(t('autoStart'))}</p>
      <div class="teams">${col('A')}${col('B')}</div>
      ${errLine()}
      <div class="row">
        ${host ? `<button class="primary grow" data-act="start">${esc(t('start'))}</button>` : `<span class="grow status">${esc(t('waitHost'))}</span>`}
        <button data-act="home">${esc(t('leave'))}</button>
      </div>
      <div class="row"><button class="grow" data-act="practice">🤖 ${esc(t('practice'))}</button></div>
      <div class="row"><button class="link" data-act="rules">${esc(t('rules'))}</button></div>
    </section>`;
  }

  function boardHtml() {
    if (view.duels.length < 2 && view.duel) return '';
    const rows = view.duels.map((d) => {
      const st = !d.done ? (view.phase === 'done' ? '–' : `${esc(t('playing'))} · ${d.rounds}`) : d.winner === 'draw' ? esc(t('draw')) : '';
      return `<tr><td class="${d.winner === 0 ? 'won' : ''}">${esc(pname(d.a))}</td><td class="mid">${st || 'vs'}</td><td class="${d.winner === 1 ? 'won' : ''}">${esc(pname(d.b))}</td></tr>`;
    }).join('');
    return `<table class="board"><thead><tr><th>${esc(teamName('A'))} ${view.score.A}</th><th class="mid">${esc(t('matches'))}</th><th>${view.score.B} ${esc(teamName('B'))}</th></tr></thead><tbody>${rows}</tbody></table>`;
  }

  function duelHtml() {
    const d = view.duel, n = view.digits;
    if (!d) return `<p class="status">${esc(t('spectate'))}</p>`;
    if (!d.me.secret && !d.done) {
      return `<p class="status">${esc(t('setSecret', { n }))}</p>
        <div class="row"><input id="sIn" class="num grow" inputmode="numeric" maxlength="${n}" autocomplete="off" placeholder="${'0123456'.slice(0, n)}">
        <button data-act="rnd">${esc(t('random'))}</button><button class="primary" data-act="secret">${esc(t('ok'))}</button></div>${errLine()}`;
    }
    let top;
    if (d.done) {
      top = `<p class="banner ${d.outcome}">${esc(t(d.outcome))}${d.opp.secret ? ` · ${esc(t('oppSecret'))}: <b class="num">${esc(d.opp.secret)}</b>` : ''}</p>`;
    } else if (view.phase === 'done') {
      top = '';
    } else if (!d.opp.hasSecret) {
      top = `<p class="status">${esc(t('secretWait'))}</p>`;
    } else if (d.me.pending) {
      top = `<p class="status">${esc(t('waitOpp'))}</p>`;
    } else {
      top = `<div class="row"><input id="gIn" class="num grow" inputmode="numeric" maxlength="${n}" autocomplete="off" placeholder="${esc(t('guessPh'))}">
        <button class="primary" data-act="guess">${esc(t('send'))}</button></div>${errLine()}`;
    }
    const len = Math.max(d.me.hist.length, d.opp.hist.length);
    let rows = '';
    for (let i = len - 1; i >= 0; i--) {
      const a = d.me.hist[i], b = d.opp.hist[i];
      rows += `<tr><td>${i + 1}</td><td class="num">${a ? a.g : ''}</td><td>${a ? hint(a) : ''}</td><td class="num">${b ? b.g : ''}</td><td>${b ? hint(b) : ''}</td></tr>`;
    }
    const chips = [...'0123456789'].map((c) => `<button class="chip ${memo[c] || ''}" data-act="memo" data-v="${c}">${c}</button>`).join('');
    return `<div class="row between"><span>${esc(t('mySecret'))}: <b class="num">${esc(d.me.secret || '-')}</b></span><span>vs <b>${esc(d.opp.name)}</b>${d.opp.pending && !d.done ? ' ✓' : ''}</span></div>
      ${top}
      <table class="hist"><thead><tr><th>#</th><th>${esc(t('me'))}</th><th>${esc(t('result'))}</th><th>${esc(t('opp'))}</th><th>${esc(t('result'))}</th></tr></thead><tbody>${rows}</tbody></table>
      <div class="memo" title="${esc(t('memo'))}">${chips}</div><p class="tiny">${esc(t('memo'))}</p>`;
  }

  function gameHtml() {
    const over = view.phase === 'done', host = view.host === view.you, d = view.duel;
    let final = '';
    if (over && view.duels.length > 1) {
      final = `<p class="banner final">${esc(view.result === 'draw' ? t('teamDraw') : t('teamWin', { t: teamName(view.result) }))} (${view.score.A} : ${view.score.B})</p>`;
    }
    const back = esc(t(view.local ? (sess ? 'backLobby' : 'home') : 'leave'));
    let wait = '';
    if (view.local && sess && ov) {
      wait = `<p class="banner wait">⏳ ${esc(t('waitingIn', { c: ov.code, a: ov.players.length, b: ov.size * 2 }))}</p>`;
    } else if (notice) {
      wait = `<p class="banner final">${esc(t(notice))}</p>`;
    }
    let btns = '';
    if (over) {
      btns = (view.local || host ? `<button class="primary grow" data-act="again">${esc(t('again'))}</button>` : `<span class="grow status">${esc(t('waitHost'))}</span>`)
        + `<button data-act="home">${back}</button>`;
    } else {
      btns = (d && !d.done && d.me.secret ? `<button data-act="forfeit">${esc(t('forfeit'))}</button>` : '')
        + `<span class="grow"></span><button class="link" data-act="rules">${esc(t('rules'))}</button>`
        + (view.local || !d || d.done ? `<button data-act="home">${back}</button>` : '');
    }
    return `<section class="card">${wait}${final}${boardHtml()}${duelHtml()}<div class="row">${btns}</div></section>`;
  }

  function render() {
    const app = $('#app');
    view = local ? local.view : ov;
    const keep = {};
    for (const id of ['gIn', 'sIn', 'cIn', 'fUser', 'fPass']) { const el = document.getElementById(id); if (el && !wipe.has(id)) keep[id] = el.value; }
    wipe.clear();
    const focusId = document.activeElement && document.activeElement.id;
    const log = document.getElementById('chatLog');
    const stick = !log || log.scrollHeight - log.scrollTop - log.clientHeight < 30;
    const logTop = log ? log.scrollTop : 0;
    app.innerHTML = !view ? (form.in ? homeHtml() : loginHtml())
      : (view.phase === 'lobby' ? lobbyHtml() : gameHtml()) + chatHtml();
    for (const id in keep) { const el = document.getElementById(id); if (el) el.value = keep[id]; }
    const log2 = document.getElementById('chatLog');
    if (log2) log2.scrollTop = stick ? log2.scrollHeight : logTop;
    // Keep the caret where it was; on desktop also jump to the number box so you can just type.
    const f = (focusId && document.getElementById(focusId))
      || (coarse ? null : document.getElementById('gIn') || document.getElementById('sIn') || (!view ? document.getElementById('fUser') : null));
    if (f && f !== document.activeElement && !$('#rulesDlg').open && $('#boss').hidden) f.focus();
    mirrorFx();
  }
  function mirrorFx() {
    const el = document.getElementById('gIn') || document.getElementById('sIn');
    $('#fxVal').textContent = el ? el.value : '';
  }
  function applyStatic() {
    document.documentElement.lang = lang;
    document.body.className = 'skin-' + skin + (skin === 'sheet' ? ' xl' : '');
    document.title = pageTitle();
    const names = t('skins').split(',');
    $('#skinSel').innerHTML = SKINS.map((k, i) => `<option value="${k}">${esc(names[i] || k)}</option>`).join('');
    $('#skinSel').value = skin;
    $('#brandText').textContent = t('title');
    $('#rulesBtn').title = t('rules');
    $('#rulesTitle').textContent = t('rules');
    $('#rulesList').innerHTML = [1, 2, 3, 4, 5, 6].map((i) => `<li>${esc(t('r' + i))}</li>`).join('');
    $('#rulesClose').textContent = t('ok');
    $('#bossHint').textContent = t('boss');
    $('#xlTabs').innerHTML = t('xlTabs').split(',').map((s, i) => `<span class="${i === 1 ? 'on' : ''}">${esc(s)}</span>`).join('');
    if (local) syncLocal();
    const url = (window.NB_CONFIG && window.NB_CONFIG.donateUrl) || '';
    const d = $('#donate');
    d.hidden = !/^https:\/\//.test(url);
    if (!d.hidden) { d.href = url; d.textContent = '☕ ' + t('donate'); }
  }
  async function pollRooms() {
    if (view || !acct || document.hidden) return;
    let list = [];
    try { const r = await fetch('api/rooms'); if (r.ok) list = (await r.json()).rooms || []; } catch { /* no game server */ }
    const key = JSON.stringify(list);
    if (key === roomsKey) return;
    roomsKey = key; roomList = list;
    if (!view && acct) render();
  }

  // ---------- events ----------
  function submitNum(id, type) {
    const el = document.getElementById(id);
    if (!el) return;
    const v = el.value.trim();
    if (!validNum(v, view.digits)) { err = 'errInvalid'; render(); return; }
    wipe.add(id);
    act(type, { value: v });
  }
  const actions = {
    signin: () => authCall('login'),
    signup: () => authCall('signup'),
    guest: () => { err = ''; acct = null; form.name = ''; form.in = true; render(); },
    logout: () => {
      if (auth) post('logout', { auth }).catch(() => {});
      signOut(); err = ''; render();
    },
    joinRoom: (v) => { form.code = v; enterRoom('join'); },
    practice: () => { err = ''; memo = {}; startLocal(ov.digits); render(); },
    chat: () => {
      const el = $('#cIn');
      // Text only: emoji never leave the browser (the server strips them again and masks swear words).
      const text = el ? el.value.replace(/[\p{Extended_Pictographic}\p{Regional_Indicator}\u200d\ufe0f\u20e3]/gu, '').trim() : '';
      if (el && !text) { wipe.add('cIn'); render(); }
      if (!text) return;
      wipe.add('cIn');
      post('action', { ...sess, type: 'chat', text }).catch(() => {});
    },
    cpu: () => { err = ''; memo = {}; startLocal(form.digits); render(); },
    create: () => enterRoom('create'),
    join: () => { if (form.code.length < 5) { err = 'errRoom'; render(); } else enterRoom('join'); },
    rules: () => $('#rulesDlg').showModal(),
    team: (v) => act('team', { team: v }),
    start: () => act('start'),
    copy: async () => {
      const link = location.origin + location.pathname + '?room=' + view.code;
      try { await navigator.clipboard.writeText(link); } catch { window.prompt('', link); }
      copied = true; render();
      setTimeout(() => { copied = false; if (view && view.phase === 'lobby') render(); }, 1500);
    },
    rnd: () => { const el = $('#sIn'); if (el) { el.value = randomNum(view.digits); mirrorFx(); } },
    secret: () => submitNum('sIn', 'secret'),
    guess: () => submitNum('gIn', 'guess'),
    forfeit: () => act('forfeit'),
    memo: (v) => { memo[v] = memo[v] === 'no' ? 'yes' : memo[v] === 'yes' ? '' : 'no'; render(); },
    again: () => { if (local) { memo = {}; startLocal(local.digits); render(); } else act('lobby'); },
    home: () => {
      if (local && sess) { local = null; memo = {}; wipe.add('gIn').add('sIn'); render(); return; } // back to the waiting room
      if (sess) post('action', { ...sess, type: 'leave' }).catch(() => {});
      goHome(); refreshMe(); pollRooms();
    },
  };
  $('#app').addEventListener('click', (e) => {
    const b = e.target.closest('[data-act]');
    if (b && actions[b.dataset.act]) actions[b.dataset.act](b.dataset.v);
  });
  $('#app').addEventListener('input', (e) => {
    const el = e.target;
    if (el.id === 'gIn' || el.id === 'sIn') { el.value = el.value.replace(/\D/g, ''); mirrorFx(); }
    else if (el.id === 'fCode') { form.code = el.value = el.value.toUpperCase().replace(/[^A-Z0-9]/g, ''); }
  });
  $('#app').addEventListener('change', (e) => {
    const el = e.target;
    if (el.id === 'fDigits') { form.digits = Number(el.value); store.set('digits', el.value); }
    else if (el.id === 'fLevel') { form.level = el.value; store.set('level', el.value); }
    else if (el.id === 'fSize') { form.size = Number(el.value); store.set('size', el.value); }
    else if (el.id === 'rDigits') act('config', { digits: Number(el.value) });
    else if (el.id === 'rSize') act('config', { size: Number(el.value) });
  });
  $('#app').addEventListener('keydown', (e) => {
    if (e.key !== 'Enter') return;
    const id = e.target.id;
    if (id === 'gIn') actions.guess();
    else if (id === 'sIn') actions.secret();
    else if (id === 'fCode') actions.join();
    else if (id === 'fPass' || id === 'fUser') actions.signin();
    else if (id === 'cIn' && !e.isComposing) actions.chat();
  });
  $('#rulesBtn').addEventListener('click', actions.rules);
  $('#skinSel').addEventListener('change', (e) => { skin = e.target.value; store.set('skin', skin); applyStatic(); });
  const sel = $('#langSel');
  sel.innerHTML = Object.entries(LANGS).map(([k, v]) => `<option value="${k}">${v}</option>`).join('');
  sel.value = lang;
  sel.addEventListener('change', () => { lang = sel.value; store.set('lang', lang); applyStatic(); render(); });

  // Boss key: Esc swaps the whole page for a dull spreadsheet, Esc again brings the game back.
  function buildBoss() {
    const heads = ['', 'Region', 'Q1', 'Q2', 'Q3', 'Q4', 'Total', 'YoY %'];
    const regions = ['Seoul', 'Busan', 'Tokyo', 'Osaka', 'Singapore', 'Sydney', 'London', 'Berlin', 'Paris', 'Madrid', 'New York', 'Chicago', 'Toronto', 'São Paulo', 'Dubai', 'Mumbai', 'Hanoi', 'Jakarta'];
    let seed = 7;
    const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
    let rows = '';
    regions.forEach((r, i) => {
      const q = [0, 0, 0, 0].map(() => Math.round(800 + rnd() * 4200));
      rows += `<tr><th>${i + 2}</th><td>${r}</td>${q.map((x) => `<td class="r">${x.toLocaleString('en')}</td>`).join('')}<td class="r">${q.reduce((a, b) => a + b).toLocaleString('en')}</td><td class="r">${(rnd() * 24 - 6).toFixed(1)}</td></tr>`;
    });
    for (let i = regions.length + 2; i < 60; i++) rows += `<tr><th>${i}</th>${'<td></td>'.repeat(7)}</tr>`;
    $('#boss').innerHTML = `<div class="boss-title">▦ Sales_Report_FY.xlsx</div>
      <table><thead><tr><th></th>${'ABCDEFG'.split('').map((c) => `<th>${c}</th>`).join('')}</tr>
      <tr><th>1</th>${heads.slice(1).map((h) => `<td><b>${h}</b></td>`).join('')}</tr></thead><tbody>${rows}</tbody></table>`;
  }
  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape' || $('#rulesDlg').open) return;
    const boss = $('#boss');
    if (!boss.innerHTML) buildBoss();
    boss.hidden = !boss.hidden;
    document.title = boss.hidden ? pageTitle() : 'Sales_Report_FY.xlsx';
    if (boss.hidden) render(); else if (document.activeElement) document.activeElement.blur();
  });

  // ---------- boot ----------
  $('#xlCols').innerHTML = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('').map((c) => `<span>${c}</span>`).join('');
  applyStatic();
  try { sess = JSON.parse(sessionStorage.getItem('nb_sess')); } catch { sess = null; }
  form.in = false;
  refreshMe().then(pollRooms);
  if (sess && sess.room) connect(); else sess = null;
  render();
  pollRooms();
  setInterval(pollRooms, 3000);
})();
