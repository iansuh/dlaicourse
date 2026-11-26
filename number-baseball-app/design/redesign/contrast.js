// Contrast + colour-blind check for the redesign palettes. Usage: node contrast.js
const hex = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16) / 255);
const lin = (c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
const L = (h) => { const [r, g, b] = hex(h).map(lin); return 0.2126 * r + 0.7152 * g + 0.0722 * b; };
const cr = (a, b) => { const [x, y] = [L(a), L(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };
// Machado 2009 severity 1.0 (linear RGB)
const M = {
  prot: [[0.152286, 1.052583, -0.204868], [0.114503, 0.786281, 0.099216], [-0.003882, -0.048116, 1.051998]],
  deut: [[0.367322, 0.860646, -0.227968], [0.280085, 0.672501, 0.047413], [-0.011820, 0.042940, 0.968881]],
  trit: [[1.255528, -0.076749, -0.178779], [-0.078411, 0.930809, 0.147602], [0.004733, 0.691367, 0.303900]],
};
const sim = (h, m) => { const c = hex(h).map(lin); return m.map((r) => Math.min(1, Math.max(0, r[0] * c[0] + r[1] * c[1] + r[2] * c[2]))); };
// CIELAB distance (D65) of simulated colours
const lab = (rgb) => {
  const [r, g, b] = rgb;
  const X = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047, Y = 0.2126 * r + 0.7152 * g + 0.0722 * b, Z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
  const f = (t) => (t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116);
  return [116 * f(Y) - 16, 500 * (f(X) - f(Y)), 200 * (f(Y) - f(Z))];
};
const dE = (a, b) => Math.hypot(...a.map((v, i) => v - b[i]));
const P = {
  A: { bg: '#FFFFFF', surface: '#F3F4F6', line: '#E5E7EB', outline: '#7E8693', ink: '#111418', text2: '#4B5260', text3: '#5F6673', accent: '#2453E6', strike: '#B54708', ball: '#00796B', out: '#111418' },
  B: { bg: '#FBF8F3', surface: '#FFFFFF', sunk: '#F2EDE4', line: '#EAE3D8', outline: '#857D71', ink: '#2A2622', text2: '#5E574F', text3: '#6A6258', accent: '#5B4FD8', strike: '#9A4A00', ball: '#066B5D', out: '#A8304A', strikeTint: '#FFEFD9', ballTint: '#DDF3EE', outTint: '#FCE4E8' },
};
for (const [k, p] of Object.entries(P)) {
  console.log(`\n== ${k}`);
  const bgs = Object.entries(p).filter(([n]) => ['bg', 'surface', 'sunk', 'strikeTint', 'ballTint', 'outTint'].includes(n));
  for (const [n, c] of Object.entries(p)) {
    if (['bg', 'surface', 'sunk', 'line', 'strikeTint', 'ballTint', 'outTint'].includes(n)) continue;
    console.log(n.padEnd(8), c, bgs.map(([bn, b]) => `${bn} ${cr(c, b).toFixed(2)}`).join('  '));
  }
  console.log('white on accent', cr('#FFFFFF', p.accent).toFixed(2), ' white on ink', cr('#FFFFFF', p.ink).toFixed(2));
  if (p.strikeTint) for (const h of ['strike', 'ball', 'out']) console.log(`${h} on ${h}Tint`, cr(p[h], p[h + 'Tint']).toFixed(2));
  for (const [a, b] of [['strike', 'ball'], ['strike', 'out'], ['ball', 'out']]) {
    console.log(`${a}/${b} dE: normal ${dE(lab(hex(p[a]).map(lin)), lab(hex(p[b]).map(lin))).toFixed(0)}`,
      Object.entries(M).map(([m, mm]) => `${m} ${dE(lab(sim(p[a], mm)), lab(sim(p[b], mm))).toFixed(0)}`).join(' '));
  }
}
