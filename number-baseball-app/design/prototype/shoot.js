// Reference-shot renderer: every PNG in ../shots/ (except icon-preview.png) comes from this table.
// Usage (from design/prototype):  node shoot.js [name-filter] [--out=dir]
// Needs Playwright + Chromium, and Roboto + Noto Sans CJK KR installed as system fonts
// (the stand-ins named in tokens.css --font-sans). Density 2: 360 x 800 dp -> 720 x 1600 px.
// Digits in the display face are tabular (DESIGN_SYSTEM 3.1); proto.js enforces it on every Barlow element.
const path = require('path');
const { chromium } = require('playwright');

const PHONE = { width: 360, height: 800 };
const SMALL = { width: 360, height: 320 };
const S = (name, page, query = '', vp = PHONE) => ({ name, page, query, vp });

const SHOTS = [
  S('age-ko', 'age.html'),
  S('age-en', 'age.html', 'lang=en&entry=1998'),
  S('age-error-ko', 'age.html', 'state=error'),
  S('age-na-ko', 'age.html', 'state=na'),
  S('nickname-ko', 'nickname.html'),
  S('nickname-error-ko', 'nickname.html', 'state=error'),
  S('home-ko', 'home.html'),
  S('home-de', 'home.html', 'lang=de'),
  S('home-ru', 'home.html', 'lang=ru'),
  S('search-ko', 'search.html'),
  S('search-found-ko', 'search.html', 'state=found'),
  S('match-guess-ko', 'match.html'),
  S('match-guess-en', 'match.html', 'lang=en'),
  S('match-guess-de', 'match.html', 'lang=de'),
  S('match-guess-ru', 'match.html', 'lang=ru'),
  S('match-secret-ko', 'match.html', 'state=secret'),
  S('match-waiting-ko', 'match.html', 'state=waiting'),
  S('match-late-ko', 'match.html', 'state=late'),
  S('match-phrases-ko', 'match.html', 'state=phrases'),
  S('match-reconnect-ko', 'match.html', 'state=reconnect'),
  S('match-6digits-ko', 'match.html', 'digits=6'),
  S('match-small-ko', 'match.html', '', SMALL),
  S('match-small-de', 'match.html', 'lang=de', SMALL),
  S('result-win-ko', 'result.html'),
  S('result-win-en', 'result.html', 'lang=en'),
  S('result-lose-de', 'result.html', 'lang=de&outcome=lose'),
  S('result-cpu-win-ko', 'result.html', 'mode=cpu'),
  S('result-cpu-lose-ko', 'result.html', 'mode=cpu&outcome=lose&ad=after'),
  S('result-link-ko', 'result.html', 'ad=after'),
  S('result-link-en', 'result.html', 'lang=en&ad=after'),
  S('lobby-ko', 'lobby.html'),
  S('lobby-en', 'lobby.html', 'lang=en'),
  S('lobby-de', 'lobby.html', 'lang=de'),
  S('lobby-ru', 'lobby.html', 'lang=ru'),
  S('team-board-ko', 'team-board.html'),
  S('team-final-ko', 'team-board.html', 'state=final'),
  S('chat-ko', 'chat.html'),
  S('chat-actions-ko', 'chat.html', 'state=actions'),
  S('chat-limit-en', 'chat.html', 'lang=en&state=limit'),
  S('chat-system-ko', 'chat.html', 'state=system'),
  S('terms-ko', 'terms.html'),
  S('terms-en', 'terms.html', 'lang=en'),
  S('legal-ko', 'legal.html'),
  S('report-card-ko', 'report.html', 'state=card'),
  S('report-card-norecord-ko', 'report.html', 'state=card&rec=none'),
  S('report-reasons-ko', 'report.html', 'state=reasons'),
  S('report-done-ko', 'report.html', 'state=done'),
  S('report-dialog-ko', 'report.html', 'state=dialog'),
  S('settings-ko', 'settings.html'),
  S('settings-en', 'settings.html', 'lang=en'),
  S('settings-ko-end', 'settings.html', 'scroll=end'),
  S('settings-ads-ko', 'settings.html', 'scroll=ads'),
  S('settings-ads-en', 'settings.html', 'lang=en&scroll=ads'),
  S('settings-ads-done-ko', 'settings.html', 'scroll=ads&ads=done'),
  S('settings-ads-done-en', 'settings.html', 'lang=en&scroll=ads&ads=done'),
  S('settings-ads-pending-ko', 'settings.html', 'scroll=ads&ads=pending'),
  S('settings-ads-pending-en', 'settings.html', 'lang=en&scroll=ads&ads=pending'),
  S('settings-ads-error-ko', 'settings.html', 'scroll=ads&ads=error'),
];

(async () => {
  const args = process.argv.slice(2);
  const filter = args.find((a) => !a.startsWith('--'));
  const outArg = args.find((a) => a.startsWith('--out='));
  const out = outArg ? path.resolve(outArg.slice(6)) : path.resolve(__dirname, '../shots');
  const browser = await chromium.launch();
  for (const s of SHOTS.filter((x) => !filter || x.name.includes(filter))) {
    const page = await browser.newPage({ viewport: s.vp, deviceScaleFactor: 2 });
    await page.goto('file://' + path.join(__dirname, s.page) + (s.query ? '?' + s.query : ''));
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(250);
    await page.screenshot({ path: path.join(out, s.name + '.png') });
    await page.close();
    console.log(s.name);
  }
  await browser.close();
})();
