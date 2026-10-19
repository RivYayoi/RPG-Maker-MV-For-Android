// Extract the RPG Maker MV editor manual's page titles to derive the real
// screen / page inventory of the PC editor. Read-only.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const ROOT = 'C:\\Program Files (x86)\\KADOKAWA\\RPGMV\\Help\\page';

function textOf(html) {
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/\s+/g, ' ')
    .trim();
}

const files = fs.readdirSync(ROOT).filter((f) => f.endsWith('.html')).sort();
const rows = [];
for (const f of files) {
  const html = fs.readFileSync(path.join(ROOT, f), 'utf8');
  const t = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i);
  let title = t ? textOf(t[1]) : '';
  if (!title) {
    const h = html.match(/<h[12][^>]*>([\s\S]*?)<\/h[12]>/i);
    title = h ? textOf(h[1]) : '';
  }
  if (!title) title = textOf(html).slice(0, 60);
  const depth = (f.match(/_/g) || []).length;
  rows.push({ page: f.replace('.html', ''), depth, title });
}

// Group by top-level section (first two path segments) for a readable summary.
const groups = new Map();
for (const r of rows) {
  const seg = r.page.split('_').slice(0, 2).join('_');
  if (!groups.has(seg)) groups.set(seg, []);
  groups.get(seg).push(r);
}

let out = 'TOTAL PAGES: ' + rows.length + '\n\n';
for (const [seg, list] of groups) {
  out += '== ' + seg + ' (' + list.length + ') ==\n';
  for (const r of list) out += '  ' + r.page.padEnd(20) + ' ' + r.title + '\n';
  out += '\n';
}
fs.writeFileSync(`${REPO}/analysis/manual-pages.txt`, out, 'utf8');
console.log('pages:', rows.length, 'sections:', groups.size);
console.log(out.slice(0, 3000));
