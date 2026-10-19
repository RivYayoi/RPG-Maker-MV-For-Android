// Phase 1 boundary scanner for the RPG Maker MV editor install tree.
// Read-only. Emits per-file, per-line hits for the four required boundary categories.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

/** The RPG Maker MV installation to scan. Override with MV_INSTALL_DIR. */
const ROOT = process.env.MV_INSTALL_DIR || 'C:\\Program Files (x86)\\KADOKAWA\\RPGMV';
const OUT = path.join(REPO, 'analysis', 'boundary-scan.json');

const CATEGORIES = {
  node_core: [
    /require\(\s*['"](fs|path|child_process|os|net|http|https|url|util|crypto|events|stream|buffer|vm|zlib|worker_threads|electron)['"]\s*\)/g,
    /require\(\s*['"]node:/g,
    /\bprocess\.(cwd|env|argv|platform|execPath|versions)\b/g,
  ],
  nw_api: [
    /\bnw\.(Window|App|Menu|MenuItem|Shell|Clipboard|Notification|Storage|Utils|Extensions|Tray|screenSaver|Window\.get)\b/g,
    /\bwindow\.require\b/g,
    /\bnwrequire\s*\(/g,
    /\bgui\./g,
    /\bprocess\.nw\b/g,
  ],
  path_assumptions: [
    /[A-Za-z]:\\\\?[A-Za-z0-9_\\.\-\/ ]*/g,          // C:\... drive paths
    /AppData/gi,
    /%[A-Z_]+%/g,                                       // %APPDATA% style
    /\b__dirname\b/g,
    /\b__filename\b/g,
    /\bprocess\.cwd\b/g,
    /\/Users\//g,
    /Program Files/gi,
  ],
  ipc_boundary: [
    /\bipcRenderer\b/g,
    /\bipcMain\b/g,
    /postMessage\s*\(/g,
    /require\(\s*['"]electron['"]/g,
    /window\.postMessage/g,
  ],
};

const files = [];
(function walk(dir) {
  let entries;
  try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return; }
  for (const e of entries) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) walk(full);
    else if (e.isFile() && /\.(js|html|json|mjs|cjs)$/i.test(e.name)) files.push(full);
  }
})(ROOT);

const results = {};
const fileSummaries = [];

for (const cat of Object.keys(CATEGORIES)) {
  const hits = [];
  for (const file of files) {
    let text;
    try { text = fs.readFileSync(file, 'utf8'); } catch { continue; }
    const lines = text.split(/\r?\n/);
    let fileCount = 0;
    lines.forEach((line, i) => {
      for (const reSrc of CATEGORIES[cat]) {
        reSrc.lastIndex = 0;
        let m;
        while ((m = reSrc.exec(line)) !== null) {
          hits.push({
            modulePath: path.relative(ROOT, file),
            line: i + 1,
            match: m[0].slice(0, 120),
          });
          fileCount++;
          if (m.index === reSrc.lastIndex) reSrc.lastIndex++;
        }
      }
    });
    if (fileCount) fileSummaries.push({ category: cat, file: path.relative(ROOT, file), hits: fileCount });
  }
  results[cat] = hits;
}

const out = {
  root: ROOT,
  scannedFiles: files.length,
  categories: Object.fromEntries(
    Object.entries(results).map(([k, v]) => [k, { totalHits: v.length, files: new Set(v.map((h) => h.modulePath)).size }]),
  ),
  results,
};

fs.writeFileSync(OUT, JSON.stringify(out, null, 2), 'utf8');

console.log('scanned files:', files.length);
for (const [k, v] of Object.entries(out.categories)) {
  console.log(`${k.padEnd(18)} hits=${String(v.totalHits).padStart(5)}  files=${v.files}`);
}
console.log('\ntop files per category:');
for (const k of Object.keys(CATEGORIES)) {
  const top = fileSummaries.filter((f) => f.category === k).sort((a, b) => b.hits - a.hits).slice(0, 5);
  console.log(`\n[${k}]`);
  for (const t of top) console.log(`   ${String(t.hits).padStart(5)}  ${t.file}`);
}
