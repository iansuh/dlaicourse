// Basic chat hygiene: profanity masking and emoji removal.
// This is a simple word list, not moderation — edit the lists below to taste.

// English: matched at the start of a word, repeated letters and endings included ("fuuucking").
const EN = ['fuck', 'shit', 'bitch', 'asshole', 'cunt', 'bastard', 'motherfucker', 'nigger', 'nigga', 'faggot', 'whore', 'slut'];
// Other Latin-script words: whole word only, to avoid hitting innocent longer words.
const LATIN = ['puta', 'putas', 'puto', 'mierda', 'joder', 'pendejo', 'cabron', 'cabrón', 'merde', 'putain', 'connard', 'salope',
  'scheisse', 'scheiße', 'arschloch', 'fotze', 'hurensohn', 'porra', 'caralho', 'foda', 'fodase'];
// Korean: matched anywhere, tolerating a couple of separators between syllables ("시 발", "시.발").
const KO = ['시발', '씨발', '씨바', '씨팔', '시팔', '쓰발', '병신', '븅신', '지랄', '좆', '존나', '졸라', '개새끼', '개새', '새끼', '미친놈', '미친년',
  '닥쳐', '꺼져', '엿먹', '느금', '니애미', '니미', '애미', '창녀', '씹', '썅', '염병', '섹스', '김치녀', '한남충'];
// Matched anywhere exactly (initial-consonant slang and other scripts).
const RAW = ['ㅅㅂ', 'ㅆㅂ', 'ㅂㅅ', 'ㅈㄹ', 'ㅈㄴ', 'ㅗ', '死ね', 'くそ', 'クソ', 'ばか', 'バカ', '傻逼', '操你', '他妈的', '妈的', '草泥马',
  'сука', 'блять', 'блядь', 'хуй', 'пизд', 'ебат', 'địt', 'đụ má', 'lồn', 'cặc', 'đm'];

const esc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const PATTERNS = [
  new RegExp(`(?<![a-z])(?:${EN.map((w) => [...w].map((c) => c + '+').join('')).join('|')})[a-z]*`, 'gi'),
  new RegExp(`(?<![a-zà-ÿ])(?:${LATIN.map(esc).join('|')})(?![a-zà-ÿ])`, 'gi'),
  new RegExp(KO.map((w) => [...w].map(esc).join('[^가-힣ㄱ-ㅎa-z0-9]{0,2}')).join('|'), 'g'),
  new RegExp(RAW.map(esc).join('|'), 'gi'),
];

const hasBad = (text) => PATTERNS.some((re) => { re.lastIndex = 0; return re.test(text); });
const mask = (text) => PATTERNS.reduce((t, re) => t.replace(re, (m) => '*'.repeat([...m].length)), text);

// Emoji, flags, skin tones, joiners, keycaps and tag characters. Plain text symbols such as ^^ or :) stay.
const EMOJI = /[\p{Extended_Pictographic}\p{Regional_Indicator}‍️⃣\u{1F3FB}-\u{1F3FF}\u{E0020}-\u{E007F}]/gu;
const stripEmoji = (text) => text.replace(EMOJI, '');

module.exports = { hasBad, mask, stripEmoji };
