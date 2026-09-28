// 构建脚本：解析 wordsABCD.md -> assets/words.json
// 运行方式: node tools/gen_words.js
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const MD_FILE = path.join(ROOT, 'wordsABCD.md');
const OUT_FILE = path.join(ROOT, 'app', 'src', 'main', 'assets', 'words.json');

const lines = fs.readFileSync(MD_FILE, 'utf8').split(/\r?\n/);
const words = [];
let skipped = 0;

for (const raw of lines) {
  const line = raw.trim();
  if (!line || line.startsWith('#')) continue;
  // "abandon A. 放弃；抛弃 B. 杂技演员 C. 人口普查 D. 木匠"
  const parts = line.split(/\s+([A-D])\.\s*/);
  if (parts.length < 9) { skipped++; continue; }
  const word = parts[0].trim();
  const map = {};
  for (let i = 1; i + 1 < parts.length; i += 2) map[parts[i]] = parts[i + 1].trim();
  if (!map['A'] || !map['B'] || !map['C'] || !map['D']) { skipped++; continue; }
  words.push({ w: word, c: map['A'].replace(/；$/, ''), d: [map['B'], map['C'], map['D']] });
}

fs.mkdirSync(path.dirname(OUT_FILE), { recursive: true });
fs.writeFileSync(OUT_FILE, JSON.stringify(words), 'utf8');
console.log(`单词 ${words.length} 个, 跳过 ${skipped} 行 -> ${OUT_FILE}`);