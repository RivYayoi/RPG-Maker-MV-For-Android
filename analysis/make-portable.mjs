// Make every analysis script resolve paths relative to the repository root
// instead of the original author's machine, so the published repo is portable.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..');

const files = fs.readdirSync(HERE).filter((f) => /\.(mjs|js)$/.test(f));
let changed = 0;

for (const f of files) {
  const p = path.join(HERE, f);
  let s = fs.readFileSync(p, 'utf8');
  const before = s;

  // 1. the repo root constant
  s = s.replace(
    /const (?:ROOT|REPO_ROOT|BASE)\s*=\s*['"][^'"]*rmmv-editor-android['"]/g,
    "const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')"
  );
  // 2. inline absolute paths
  s = s.replace(/C:\/Users\/Yayoi\/rmmv-editor-android/g, '${ROOT}');
  s = s.replace(/C:\\Users\\Yayoi\\rmmv-editor-android/g, '${ROOT}');

  if (s !== before) {
    // make sure `path` + `fileURLToPath` are imported when we now use them
    if (s.includes('fileURLToPath') && !/import\s+\{[^}]*fileURLToPath/.test(s)) {
      s = s.replace(/^import fs from 'node:fs';/m,
        "import fs from 'node:fs';\nimport path from 'node:path';\nimport { fileURLToPath } from 'node:url';");
    }
    fs.writeFileSync(p, s, 'utf8');
    console.log('  patched', f);
    changed++;
  }
}
console.log('patched', changed, 'of', files.length, 'scripts');
