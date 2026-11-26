// Generates css/tokens.css from ../tokens.json (the source of truth). Usage (from design/prototype): node gen-tokens.js
// Naming: color.fooBar -> --c-foo-bar, type.fooBar -> --t-foo-bar (+ -track), space.sN -> --sN, layout.x -> --l-x,
// size.x -> --z-x, radius.x -> --r-x, motion.duration.x -> --d-x, motion.easing.x -> --e-x. 1 dp = 1 px, 1 sp = 1 px at fontScale 1.
const fs = require('fs');
const path = require('path');
const T = JSON.parse(fs.readFileSync(path.join(__dirname, '../tokens.json'), 'utf8'));
const kebab = (s) => s.replace(/([a-z0-9])([A-Z])/g, '$1-$2').replace(/_/g, '-').toLowerCase();
const hex = (v) => {
  const a = parseInt(v.slice(1, 3), 16), rgb = v.slice(3);
  if (a === 255) return '#' + rgb;
  const [r, g, b] = [0, 2, 4].map((i) => parseInt(rgb.slice(i, i + 2), 16));
  return `rgba(${r}, ${g}, ${b}, ${+(a / 255).toFixed(3)})`;
};
const fam = { sans: 'var(--font-sans)', num: 'var(--font-num)' };
const L = ['/* GENERATED from ../../tokens.json by ../gen-tokens.js. Do not edit by hand. 1dp = 1px, 1sp = 1px at fontScale 1.0. */', ':root {'];
for (const [k, v] of Object.entries(T.color)) L.push(`  --c-${kebab(k)}: ${hex(v.value)};`);
L.push('  --font-sans: Roboto, "Noto Sans KR", "Noto Sans CJK KR", "Apple SD Gothic Neo", "Malgun Gothic", system-ui, sans-serif;');
L.push('  --font-num: "Inter NB", Roboto, sans-serif;');
for (const [k, v] of Object.entries(T.type)) {
  // Numerals are capped at 1.3x the token size (DESIGN_SYSTEM 3.3): --ns = min(fontScale, 1.3), --fs = fontScale.
  const s = v.font === 'num' ? 'var(--ns, 1)' : 'var(--fs, 1)';
  L.push(`  --t-${kebab(k)}: ${v.weight} calc(${v.size_sp}px * ${s})/calc(${v.line_sp}px * ${s}) ${fam[v.font]};`);
  L.push(`  --t-${kebab(k)}-track: ${v.tracking_em}em;`);
}
for (const [k, v] of Object.entries(T.space)) L.push(`  --${k}: ${v}px;`);
for (const [k, v] of Object.entries(T.layout)) L.push(`  --l-${kebab(k)}: ${v}px;`);
for (const [k, v] of Object.entries(T.size)) L.push(`  --z-${kebab(k)}: ${v}px;`);
for (const [k, v] of Object.entries(T.radius)) L.push(`  --r-${kebab(k)}: ${v}px;`);
for (const [k, v] of Object.entries(T.motion.duration)) L.push(`  --d-${kebab(k)}: ${v}ms;`);
for (const [k, v] of Object.entries(T.motion.easing)) L.push(`  --e-${kebab(k)}: cubic-bezier(${v.join(', ')});`);
L.push('}');
// The bundled font files are the ones the app ships (design/fonts), loaded under their own family name so a system Inter cannot stand in.
for (const [w, f] of [[500, 'inter_medium'], [600, 'inter_semibold'], [700, 'inter_bold']]) {
  L.push(`@font-face { font-family: "Inter NB"; font-weight: ${w}; src: url("../../fonts/${f}.ttf") format("truetype"); }`);
}
fs.writeFileSync(path.join(__dirname, 'css/tokens.css'), L.join('\n') + '\n');
console.log('css/tokens.css written');
