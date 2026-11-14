// Builds a single self-contained HTML file with computer mode only (no game server needed).
// Usage: node tools/build-offline.js [output.html]
const fs = require('fs');
const path = require('path');
const pub = path.join(__dirname, '..', 'public');
const read = (f) => fs.readFileSync(path.join(pub, f), 'utf8');
const out = process.argv[2] || path.join(__dirname, '..', 'dist', 'number-baseball-offline.html');

const html = read('index.html');
let body = html.slice(html.indexOf('<body>') + 6, html.indexOf('</body>'));
const config = read('config.js').replace(/offline:\s*false/, 'offline: true');
const inline = (src) => `<script>\n${src.replace(/<\/script/gi, '<\\/script')}\n</script>`;
body = body
  .replace('<script src="config.js"></script>', () => inline(config))
  .replace('<script src="i18n.js"></script>', () => inline(read('i18n.js')))
  .replace('<script src="app.js"></script>', () => inline(read('app.js')));
if (/<script src=/.test(body)) throw new Error('an external script was left in the page');

const page = `<title>Number Baseball</title>\n<style>\n${read('style.css')}\n</style>\n${body.trim()}\n`;
fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, page);
console.log(`wrote ${out} (${Math.round(page.length / 1024)} KB)`);
