// Renders the redesign PNGs. Usage (from design/redesign): node shoot.js
// Needs Playwright + Chromium and the fonts Noto Sans CJK KR + Inter installed (see design/prototype/shoot.js).
const path = require('path');
const { chromium } = require('playwright');

const PHONE = { width: 360, height: 800 };
const SHOTS = [
  ['A-home', 'dir=A&screen=home'], ['A-match', 'dir=A&screen=match'], ['A-result', 'dir=A&screen=result'],
  ['A-match-font200', 'dir=A&screen=match&fs=2'],
  ['B-home', 'dir=B&screen=home'], ['B-match', 'dir=B&screen=match'], ['B-result', 'dir=B&screen=result'],
];

(async () => {
  const browser = await chromium.launch();
  for (const [name, query] of SHOTS) {
    const page = await browser.newPage({ viewport: PHONE, deviceScaleFactor: 2 });
    await page.goto('file://' + path.join(__dirname, 'proto.html') + '?' + query);
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(250);
    await page.screenshot({ path: path.join(__dirname, name + '.png') });
    await page.close();
    console.log(name);
  }
  // Side-by-side: current app vs A vs B, home and match.
  const cur = path.resolve(__dirname, '../../android/app/screenshots');
  const cell = (src, label) => `<figure><figcaption>${label}</figcaption><img src="file://${src}"></figure>`;
  const html = `<!doctype html><meta charset="utf-8"><style>
    body{margin:0;background:#E9EAEC;font-family:'Noto Sans CJK KR',sans-serif;padding:24px}
    .g{display:grid;grid-template-columns:repeat(3,360px);gap:24px 28px}
    figure{margin:0} img{width:360px;height:800px;display:block;border-radius:20px;box-shadow:0 2px 10px rgba(0,0,0,.15)}
    figcaption{font-size:16px;font-weight:700;color:#111418;margin:0 0 10px 4px}</style>
    <div class="g">
    ${cell(cur + '/home-ko.png', '현재 · 홈')}${cell(__dirname + '/A-home.png', 'A 클린 화이트 · 홈')}${cell(__dirname + '/B-home.png', 'B 소프트 라이트 · 홈')}
    ${cell(cur + '/match-guess-ko.png', '현재 · 대전')}${cell(__dirname + '/A-match.png', 'A 클린 화이트 · 대전')}${cell(__dirname + '/B-match.png', 'B 소프트 라이트 · 대전')}
    </div>`;
  const page = await browser.newPage({ viewport: { width: 24 * 2 + 360 * 3 + 28 * 2, height: 24 * 2 + (800 + 32) * 2 + 24 }, deviceScaleFactor: 1 });
  const tmp = path.join(require('os').tmpdir(), 'redesign-compare.html');
  require('fs').writeFileSync(tmp, html);
  await page.goto('file://' + tmp);
  await page.evaluate(() => Promise.all([...document.images].map((i) => i.decode())));
  await page.screenshot({ path: path.join(__dirname, 'compare.png'), fullPage: true });
  console.log('compare');
  await browser.close();
})();
