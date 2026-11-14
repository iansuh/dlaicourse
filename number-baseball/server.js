// Number Baseball – zero-dependency game server (static files + JSON API + Server-Sent Events).
// Run: node server.js   (PORT env var optional, default 3000)
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = process.env.PORT || 3000;
const PUB = path.join(__dirname, 'public');
const MAX_TEAM = 10;          // players per team
const MAX_ROOMS = 2000;
const LOBBY_GRACE = 30e3;     // offline in lobby -> removed
const PLAY_GRACE = 120e3;     // offline mid-game -> forfeit
const ROOM_TTL = 2 * 3600e3;  // idle rooms are dropped

const rooms = new Map();

// ---------- accounts ----------
// Stored in one JSON file. Passwords are salted scrypt hashes; login tokens are stored hashed.
const { promisify } = require('util');
const { hasBad, mask, stripEmoji } = require('./filter');
const scrypt = promisify(crypto.scrypt);
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, 'data');
const USERS_FILE = path.join(DATA_DIR, 'users.json');
const SESSION_TTL = 30 * 24 * 3600e3;
let users = {};
try { users = JSON.parse(fs.readFileSync(USERS_FILE, 'utf8')); } catch { /* first run */ }
const tokenOwner = new Map(); // sha256(token) -> user key
for (const k in users) for (const h in users[k].sessions) tokenOwner.set(h, k);
let saveTimer = null;
function saveUsers() {
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    try {
      fs.mkdirSync(DATA_DIR, { recursive: true });
      fs.writeFileSync(USERS_FILE + '.tmp', JSON.stringify(users));
      fs.renameSync(USERS_FILE + '.tmp', USERS_FILE);
    } catch (e) { console.error('Could not save accounts:', e.message); }
  }, 300);
}
const sha = (s) => crypto.createHash('sha256').update(s).digest('hex');
const userKey = (name) => name.normalize('NFKC').toLowerCase();
const publicUser = (u) => ({ name: u.name, stats: { w: u.w, l: u.l, d: u.d } });
function openSession(key) {
  const u = users[key], now = Date.now();
  const drop = (h) => { delete u.sessions[h]; tokenOwner.delete(h); };
  for (const h in u.sessions) if (u.sessions[h] < now) drop(h);
  const live = Object.keys(u.sessions).sort((a, b) => u.sessions[a] - u.sessions[b]);
  while (live.length >= 5) drop(live.shift());
  const token = crypto.randomBytes(24).toString('hex');
  u.sessions[sha(token)] = now + SESSION_TTL;
  tokenOwner.set(sha(token), key);
  saveUsers();
  return token;
}
function userByToken(token) {
  if (typeof token !== 'string' || !token) return null;
  const h = sha(token), key = tokenOwner.get(h), u = key && users[key];
  if (!u || !(u.sessions[h] > Date.now())) return null;
  return { key, u, h };
}
// Fixed-window limiter per client address, to slow down password guessing and signup spam.
const hits = new Map();
function limited(req, kind, max, windowMs) {
  const ip = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim() || req.socket.remoteAddress || '?';
  const k = kind + '|' + ip, now = Date.now();
  let e = hits.get(k);
  if (!e || e.reset < now) { e = { n: 0, reset: now + windowMs }; hits.set(k, e); }
  return ++e.n > max;
}
// Recovery code: shown once at signup (and after each reset), stored only as a hash.
// It lets people reset a forgotten password without the site collecting an email address.
async function newRecovery(u) {
  const abc = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  const raw = Array.from(crypto.randomBytes(12), (b) => abc[b % abc.length]).join('');
  u.rsalt = crypto.randomBytes(16).toString('hex');
  u.rhash = (await scrypt(raw, u.rsalt, 32)).toString('hex');
  return raw.match(/.{4}/g).join('-');
}
async function authApi(req, res, route, m) {
  const name = typeof m.username === 'string' ? m.username.trim().normalize('NFKC') : '';
  const pass = typeof m.password === 'string' ? m.password : '';
  if (route === 'signup') {
    if (limited(req, 'signup', 10, 3600e3)) return send(res, 429, { error: 'errRate' });
    if (!/^[\p{L}\p{N}_]{2,12}$/u.test(name)) return send(res, 400, { error: 'errName' });
    if (hasBad(name)) return send(res, 400, { error: 'errBadName' });
    if (pass.length < 6 || pass.length > 72) return send(res, 400, { error: 'errPass' });
    const key = userKey(name);
    if (users[key]) return send(res, 409, { error: 'errTaken' });
    const salt = crypto.randomBytes(16).toString('hex');
    const hash = (await scrypt(pass, salt, 32)).toString('hex');
    if (users[key]) return send(res, 409, { error: 'errTaken' });
    users[key] = { name, salt, hash, created: Date.now(), w: 0, l: 0, d: 0, sessions: {} };
    const recovery = await newRecovery(users[key]);
    return send(res, 200, { auth: openSession(key), recovery, ...publicUser(users[key]) });
  }
  if (route === 'login') {
    if (limited(req, 'login', 10, 60e3)) return send(res, 429, { error: 'errRate' });
    const key = userKey(name), u = users[key];
    // Hash even for unknown names so the response time does not reveal which names exist.
    const got = await scrypt(pass.slice(0, 72), u ? u.salt : '00', 32);
    if (!u || !crypto.timingSafeEqual(got, Buffer.from(u.hash, 'hex'))) return send(res, 401, { error: 'errAuth' });
    return send(res, 200, { auth: openSession(key), ...publicUser(u) });
  }
  if (route === 'reset') {
    if (limited(req, 'reset', 5, 900e3)) return send(res, 429, { error: 'errRate' });
    if (pass.length < 6 || pass.length > 72) return send(res, 400, { error: 'errPass' });
    const key = userKey(name), u = users[key];
    const code = String(m.recovery || '').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 32);
    const got = await scrypt(code, u && u.rsalt ? u.rsalt : '00', 32);
    if (!u || !u.rhash || !crypto.timingSafeEqual(got, Buffer.from(u.rhash, 'hex'))) return send(res, 401, { error: 'errRecovery' });
    u.salt = crypto.randomBytes(16).toString('hex');
    u.hash = (await scrypt(pass, u.salt, 32)).toString('hex');
    for (const h in u.sessions) tokenOwner.delete(h); // sign out everywhere
    u.sessions = {};
    const recovery = await newRecovery(u);             // the old code is used up
    return send(res, 200, { auth: openSession(key), recovery, ...publicUser(u) });
  }
  if (route === 'logout') {
    const s = userByToken(m.auth);
    if (s) { delete s.u.sessions[s.h]; tokenOwner.delete(s.h); saveUsers(); }
    return send(res, 200, { ok: true });
  }
}
function recordResult(room, d) {
  d.p.forEach((pid, i) => {
    const u = users[room.players.get(pid).acct];
    if (!u) return;
    if (d.winner === 'draw') u.d++; else if (d.winner === i) u.w++; else u.l++;
  });
  saveUsers();
}

const rid = (n) => crypto.randomBytes(n).toString('hex');
function roomCode() {
  const abc = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let c;
  do { c = Array.from(crypto.randomBytes(5), (b) => abc[b % abc.length]).join(''); } while (rooms.has(c));
  return c;
}
const validNum = (v, n) => typeof v === 'string' && v.length === n && /^\d+$/.test(v) && new Set(v).size === n;
function judge(secret, guess) {
  let s = 0, hit = 0;
  for (let i = 0; i < guess.length; i++) {
    if (guess[i] === secret[i]) s++;
    if (secret.includes(guess[i])) hit++;
  }
  return { s, b: hit - s };
}
function shuffle(a) {
  for (let i = a.length - 1; i > 0; i--) {
    const j = crypto.randomInt(i + 1);
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

function teamIds(room, t) {
  return [...room.players.values()].filter((p) => p.team === t && !p.left).map((p) => p.id);
}
function duelOf(room, pid) {
  return room.duels.find((d) => d.p.includes(pid));
}
function scoreOf(room) {
  const sc = { A: 0, B: 0 };
  for (const d of room.duels) { if (d.winner === 0) sc.A++; else if (d.winner === 1) sc.B++; }
  return sc;
}
function checkMatch(room) {
  if (room.phase !== 'play') return;
  const sc = scoreOf(room), n = room.duels.length;
  const allDone = room.duels.every((d) => d.done);
  if (sc.A > n / 2 || sc.B > n / 2 || allDone) {
    room.phase = 'done';
    room.result = sc.A > sc.B ? 'A' : sc.B > sc.A ? 'B' : 'draw';
  }
}
function finishDuel(room, d, winner) {
  if (d.done) return;
  d.done = true;
  d.winner = winner;
  recordResult(room, d);
  checkMatch(room);
}

function viewFor(room, me) {
  const over = room.phase === 'done';
  const v = {
    code: room.code, digits: room.digits, size: room.size, chat: room.chat, phase: room.phase, host: room.host, you: me.id,
    players: [...room.players.values()].map((p) => ({ id: p.id, name: p.name, team: p.team, online: p.online && !p.left })),
    duels: room.duels.map((d) => ({ a: d.p[0], b: d.p[1], rounds: d.hist[0].length, winner: d.winner, done: d.done })),
    score: scoreOf(room), result: room.result, duel: null,
  };
  const d = duelOf(room, me.id);
  if (d && room.phase !== 'lobby') {
    const i = d.p.indexOf(me.id), o = 1 - i, opp = room.players.get(d.p[o]);
    v.duel = {
      done: d.done,
      outcome: !d.done ? null : d.winner === 'draw' ? 'draw' : d.winner === i ? 'win' : 'lose',
      me: { secret: me.secret, hist: d.hist[i], pending: d.pending[i] !== null },
      opp: {
        id: opp.id, name: opp.name, hasSecret: !!opp.secret, hist: d.hist[o],
        pending: d.pending[o] !== null, secret: d.done || over ? opp.secret : null,
      },
    };
  }
  return v;
}
function push(room) {
  room.touched = Date.now();
  for (const p of room.players.values()) {
    if (p.stream) p.stream.write(`data: ${JSON.stringify(viewFor(room, p))}\n\n`);
  }
}

function removePlayer(room, p) {
  clearTimeout(p.timer);
  if (p.stream) { p.stream.end(); p.stream = null; }
  if (room.phase === 'lobby') {
    room.players.delete(p.id);
  } else {
    p.left = true; p.online = false;
    const d = duelOf(room, p.id);
    if (d) finishDuel(room, d, 1 - d.p.indexOf(p.id));
  }
  const alive = [...room.players.values()].filter((x) => !x.left);
  if (!alive.length) { rooms.delete(room.code); return; }
  if (!alive.some((x) => x.id === room.host)) room.host = alive[0].id;
  push(room);
}

function startGame(room) {
  const a = shuffle(teamIds(room, 'A')), b = shuffle(teamIds(room, 'B'));
  if (!a.length || a.length !== b.length) return 'needEven';
  for (const p of room.players.values()) p.secret = null;
  room.duels = a.map((pa, i) => ({ p: [pa, b[i]], hist: [[], []], pending: [null, null], winner: null, done: false }));
  room.phase = 'play';
  room.result = null;
  return null;
}
const isFull = (room) => teamIds(room, 'A').length >= room.size && teamIds(room, 'B').length >= room.size;

function act(room, me, m) {
  const isHost = room.host === me.id;
  switch (m.type) {
    case 'team': {
      if (room.phase !== 'lobby') return 'errStarted';
      if (m.team !== 'A' && m.team !== 'B') return 'errInvalid';
      if (me.team !== m.team && teamIds(room, m.team).length >= room.size) return 'errFull';
      me.team = m.team;
      break;
    }
    case 'config': {
      if (!isHost || room.phase !== 'lobby') return 'errInvalid';
      if (m.digits !== undefined) {
        const n = Number(m.digits);
        if (![3, 4, 5, 6].includes(n)) return 'errInvalid';
        room.digits = n;
      }
      if (m.size !== undefined) {
        const z = Number(m.size);
        if (!Number.isInteger(z) || z < 1 || z > MAX_TEAM) return 'errInvalid';
        if (teamIds(room, 'A').length > z || teamIds(room, 'B').length > z) return 'errFull';
        room.size = z;
      }
      break;
    }
    case 'start': {
      if (!isHost || room.phase !== 'lobby') return 'errInvalid';
      const e = startGame(room);
      if (e) return e;
      break;
    }
    case 'secret': {
      if (room.phase !== 'play' || me.secret || !duelOf(room, me.id)) return 'errInvalid';
      if (!validNum(m.value, room.digits)) return 'errInvalid';
      me.secret = m.value;
      break;
    }
    case 'guess': {
      const d = duelOf(room, me.id);
      if (room.phase !== 'play' || !d || d.done) return 'errInvalid';
      const i = d.p.indexOf(me.id), opp = room.players.get(d.p[1 - i]);
      if (!me.secret || !opp.secret || d.pending[i] !== null) return 'errInvalid';
      if (!validNum(m.value, room.digits)) return 'errInvalid';
      d.pending[i] = m.value;
      if (d.pending[1 - i] !== null) {
        // Both guesses are in: resolve the round simultaneously (no first-move advantage).
        const solved = [0, 1].map((k) => {
          const r = judge(room.players.get(d.p[1 - k]).secret, d.pending[k]);
          d.hist[k].push({ g: d.pending[k], s: r.s, b: r.b });
          return r.s === room.digits;
        });
        d.pending = [null, null];
        if (solved[0] || solved[1]) finishDuel(room, d, solved[0] && solved[1] ? 'draw' : solved[0] ? 0 : 1);
      }
      break;
    }
    case 'forfeit': {
      const d = duelOf(room, me.id);
      if (room.phase !== 'play' || !d || d.done) return 'errInvalid';
      finishDuel(room, d, 1 - d.p.indexOf(me.id));
      break;
    }
    case 'lobby': {
      if (!isHost || room.phase !== 'done') return 'errInvalid';
      for (const p of [...room.players.values()]) if (p.left) room.players.delete(p.id);
      room.phase = 'lobby'; room.duels = []; room.result = null;
      break;
    }
    case 'chat': {
      // Text only: emoji are removed and listed swear words are masked.
      const text = mask(stripEmoji(String(m.text || '')).replace(/[\u0000-\u001f]/g, ' ').trim().slice(0, 200));
      const now = Date.now();
      if (!text || now - (me.lastChat || 0) < 400) return 'errInvalid';
      me.lastChat = now;
      room.chat.push({ id: ++room.chatSeq, n: me.name, t: text });
      if (room.chat.length > 50) room.chat.shift();
      break;
    }
    case 'leave':
      removePlayer(room, me);
      return null;
    default:
      return 'errInvalid';
  }
  push(room);
  return null;
}

function addPlayer(room, acct) {
  const p = { id: rid(4), token: rid(12), name: users[acct].name, acct, team: null, secret: null, stream: null, online: false, left: false, timer: null };
  const a = teamIds(room, 'A').length, b = teamIds(room, 'B').length;
  p.team = a <= b ? (a < room.size ? 'A' : 'B') : (b < room.size ? 'B' : 'A');
  room.players.set(p.id, p);
  // A player who never opens the event stream is cleaned up like one who disconnected.
  p.timer = setTimeout(() => removePlayer(room, p), LOBBY_GRACE);
  return p;
}

function send(res, code, obj) {
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(obj));
}
function readBody(req) {
  return new Promise((resolve) => {
    let s = '';
    req.on('data', (c) => { s += c; if (s.length > 2048) { s = ''; req.destroy(); } });
    req.on('end', () => { try { resolve(JSON.parse(s || '{}')); } catch { resolve({}); } });
    req.on('error', () => resolve({}));
  });
}
function auth(q) {
  const room = rooms.get(String(q.room || '').toUpperCase());
  const p = room && room.players.get(String(q.pid || ''));
  if (!p || p.left || p.token !== q.token) return {};
  return { room, p };
}

async function api(req, res, url) {
  const route = url.pathname.slice(5);
  if (req.method === 'GET' && route === 'events') {
    const { room, p } = auth(Object.fromEntries(url.searchParams));
    if (!room) return send(res, 404, { error: 'errRoom' });
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-store', Connection: 'keep-alive', 'X-Accel-Buffering': 'no' });
    clearTimeout(p.timer);
    if (p.stream) p.stream.end();
    p.stream = res; p.online = true;
    req.on('close', () => {
      if (p.stream !== res) return;
      p.stream = null; p.online = false;
      if (!rooms.has(room.code) || p.left) return;
      p.timer = setTimeout(() => removePlayer(room, p), room.phase === 'play' ? PLAY_GRACE : LOBBY_GRACE);
      push(room);
    });
    return push(room);
  }
  if (req.method === 'GET' && route === 'check') {
    return auth(Object.fromEntries(url.searchParams)).room ? send(res, 200, { ok: true }) : send(res, 404, { error: 'errRoom' });
  }
  if (req.method === 'GET' && route === 'rooms') {
    // Public list of rooms that are waiting for players.
    const list = [];
    for (const room of rooms.values()) {
      if (room.phase !== 'lobby' || isFull(room)) continue;
      const host = room.players.get(room.host);
      list.push({ code: room.code, host: host ? host.name : '', digits: room.digits, size: room.size, count: teamIds(room, 'A').length + teamIds(room, 'B').length, at: room.created });
    }
    list.sort((x, y) => y.at - x.at);
    return send(res, 200, { rooms: list.slice(0, 50) });
  }
  if (req.method === 'GET' && route === 'me') {
    const s = userByToken(String(req.headers.authorization || '').replace(/^Bearer /, ''));
    return s ? send(res, 200, publicUser(s.u)) : send(res, 401, { error: 'errLogin' });
  }
  if (req.method !== 'POST') return send(res, 404, { error: 'errInvalid' });
  const m = await readBody(req);
  if (['signup', 'login', 'logout', 'reset'].includes(route)) return authApi(req, res, route, m);
  // Online rooms are for signed-in accounts only.
  const who = route === 'create' || route === 'join' ? userByToken(m.auth) : null;
  if ((route === 'create' || route === 'join') && !who) return send(res, 401, { error: 'errLogin' });
  if (route === 'create') {
    if (rooms.size >= MAX_ROOMS) return send(res, 503, { error: 'errFull' });
    const digits = [3, 4, 5, 6].includes(Number(m.digits)) ? Number(m.digits) : 3;
    const z = Number(m.size);
    const size = Number.isInteger(z) && z >= 1 && z <= MAX_TEAM ? z : 1;
    const room = { code: roomCode(), digits, size, phase: 'lobby', players: new Map(), host: null, duels: [], result: null, chat: [], chatSeq: 0, created: Date.now(), touched: Date.now() };
    rooms.set(room.code, room);
    const p = addPlayer(room, who.key);
    room.host = p.id;
    return send(res, 200, { room: room.code, pid: p.id, token: p.token });
  }
  if (route === 'join') {
    const room = rooms.get(String(m.room || '').trim().toUpperCase());
    if (!room) return send(res, 404, { error: 'errRoom' });
    // Same account joining again (another tab or device) gets its existing seat back.
    const seat = [...room.players.values()].find((x) => x.acct === who.key && !x.left);
    if (seat) return send(res, 200, { room: room.code, pid: seat.id, token: seat.token });
    if (room.phase === 'play') return send(res, 409, { error: 'errStarted' });
    if (isFull(room)) return send(res, 409, { error: 'errFull' });
    const p = addPlayer(room, who.key);
    // The room starts by itself as soon as every seat is taken.
    if (room.phase === 'lobby' && isFull(room)) startGame(room);
    push(room);
    return send(res, 200, { room: room.code, pid: p.id, token: p.token });
  }
  if (route === 'action') {
    const { room, p } = auth(m);
    if (!room) return send(res, 404, { error: 'errRoom' });
    const err = act(room, p, m);
    return err ? send(res, 400, { error: err }) : send(res, 200, { ok: true });
  }
  send(res, 404, { error: 'errInvalid' });
}

const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.ico': 'image/x-icon' };
function serveStatic(req, res, url) {
  let rel;
  try { rel = decodeURIComponent(url.pathname); } catch { rel = '/'; }
  if (rel.endsWith('/')) rel += 'index.html';
  const file = path.join(PUB, path.normalize(rel));
  if (!file.startsWith(PUB + path.sep)) { res.writeHead(403); return res.end(); }
  fs.readFile(file, (err, data) => {
    if (err) { res.writeHead(404, { 'Content-Type': 'text/plain' }); return res.end('Not found'); }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-cache' });
    res.end(data);
  });
}

http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (url.pathname.startsWith('/api/')) {
    api(req, res, url).catch(() => { if (!res.headersSent) send(res, 500, { error: 'errInvalid' }); });
  } else if (req.method === 'GET' || req.method === 'HEAD') {
    serveStatic(req, res, url);
  } else {
    res.writeHead(405); res.end();
  }
}).listen(PORT, () => console.log(`Number Baseball running on http://localhost:${PORT}`));

// Heartbeat keeps proxies from closing idle streams; also sweep abandoned rooms.
setInterval(() => {
  const now = Date.now();
  for (const room of rooms.values()) {
    if (now - room.touched > ROOM_TTL) {
      for (const p of room.players.values()) { clearTimeout(p.timer); if (p.stream) p.stream.end(); }
      rooms.delete(room.code);
      continue;
    }
    for (const p of room.players.values()) if (p.stream) p.stream.write(': ping\n\n');
  }
}, 20e3);
