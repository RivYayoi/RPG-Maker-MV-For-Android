// Second pass: turn the REPO placeholders into working template literals
// and give every script a real REPO-root constant.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const HERE = path.dirname(fileURLToPath(import.meta.url));

const HEADER = `const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');`;

const files = fs.readdirSync(HERE).filter((f) => /\.(mjs|js)$/.test(f) && f !== 'make-portable.mjs');

for (const f of files) {
  const p = path.join(HERE, f);
  let s = fs.readFileSync(p, 'utf8');
  if (!s.includes(`${REPO}/`)) continue;

  // single-quoted placeholder -> template literal
  s = s.replace(/'\$\{ROOT\}(\/[^']*)'/g, '`${REPO}$1`');
  s = s.replace(/'\$\{ROOT\}'/g, 'REPO');

  // ensure imports
  if (!/import path from 'node:path'/.test(s)) {
    s = s.replace(/^import fs from 'node:fs';/m, "import fs from 'node:fs';\nimport path from 'node:path';");
  }
  if (!/fileURLToPath/.test(s)) {
    s = s.replace(/^import path from 'node:path';/m, "import path from 'node:path';\nimport { fileURLToPath } from 'node:url';");
  }
  // insert the REPO constant after the last import
  const importEnd = s.lastIndexOf('\nimport ');
  const lineEnd = s.indexOf('\n', importEnd + 1);
  s = s.slice(0, lineEnd + 1) + HEADER + '\n' + s.slice(lineEnd + 1);

  fs.writeFileSync(p, s, 'utf8');
  console.log('  fixed', f);
}
console.log('done');
